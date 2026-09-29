package com.example.arctracker.service

import android.content.Context
import android.util.Log
import com.example.arctracker.data.AppDatabase
import com.example.arctracker.data.Expense
import com.example.arctracker.data.ExpenseDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Result of attempting to persist or correlate a transaction candidate in the Room database (Step 8).
 */
sealed class ExpensePersistenceResult {
    open val matchResult: KnownFinancialAccountMatchResult? get() = null
    open val accountEnrichment: AccountEnrichment? get() = matchResult?.accountEnrichment

    data class Inserted(val expense: Expense, val isPending: Boolean, val reason: String, override val matchResult: KnownFinancialAccountMatchResult? = null) : ExpensePersistenceResult()
    data class Updated(val updatedExpense: Expense, val previousExpense: Expense, val changeDescription: String, override val matchResult: KnownFinancialAccountMatchResult? = null) : ExpensePersistenceResult()
    data class SkippedDuplicate(val existingExpense: Expense, val reason: String, override val matchResult: KnownFinancialAccountMatchResult? = null) : ExpensePersistenceResult()
    data class Enriched(val enrichedExpense: Expense, val correlationInfo: String, override val matchResult: KnownFinancialAccountMatchResult? = null) : ExpensePersistenceResult()
    data class ReviewPending(val expense: Expense, val reason: String, override val matchResult: KnownFinancialAccountMatchResult? = null) : ExpensePersistenceResult()
    data class IgnoredNonFinancial(val reason: String) : ExpensePersistenceResult()
    data class Rejected(val reason: String) : ExpensePersistenceResult()
    data class Error(val throwable: Throwable, val message: String) : ExpensePersistenceResult()
}

/**
 * Step 8: Transaction Persistence Manager.
 *
 * Connects the in-memory Steps 1-7 transaction processing pipeline with ArcTracker's
 * Room database and Pending Expenses workflow.
 *
 * Responsibilities:
 * 1. Safe end-to-end execution: Capture -> Normalization -> Classification -> Extraction -> Validation -> Dedup -> Persistence.
 * 2. Strict idempotency: Never inserts duplicate records on repeated processing.
 * 3. Pending integration: Candidates requiring review or in pending status are routed to Pending Expenses (isPending = true).
 * 4. Cross-source correlation: Enriches existing rows (e.g. notification + bank SMS) without creating duplicate entries.
 * 5. Manual expense safety: Protects user-created manual expenses from being overwritten by notifications.
 * 6. Concurrency and exception safety: Guaranteed non-crashing operation with linear execution mutex.
 */
object TransactionPersistenceManager {

    private const val TAG = "TxnPersistenceManager"

    // Mutex ensuring concurrent incoming notifications/SMS do not race to insert duplicate rows
    private val pipelineMutex = Mutex()

    // Optional listener for debugging and testing verification
    var persistenceListener: ((ExpensePersistenceResult) -> Unit)? = null

    // Milestone 4: Optional account matcher for live notification account enrichment
    var matcher: KnownFinancialAccountMatcher? = null

    // Milestone 11: Optional rule and alias managers for user-defined categorization
    var ruleManager: CategoryRuleManager? = null
    var aliasManager: MerchantAliasManager? = null

    /**
     * Complete pipeline entry point for a captured notification using ExpenseDao.
     * Supports one notification containing 0, 1, or N independent transaction candidates.
     */
    suspend fun processCapturedNotification(
        captured: CapturedNotificationInfo,
        dao: ExpenseDao,
        accountMatcher: KnownFinancialAccountMatcher? = matcher
    ): ExpensePersistenceResult {
        val results = processCapturedNotificationAll(captured, dao, accountMatcher)
        return results.firstOrNull { it is ExpensePersistenceResult.Inserted || it is ExpensePersistenceResult.ReviewPending || it is ExpensePersistenceResult.Updated || it is ExpensePersistenceResult.Enriched }
            ?: results.firstOrNull()
            ?: ExpensePersistenceResult.IgnoredNonFinancial("No transactions processed")
    }

    /**
     * Processes all transaction candidates found within a captured notification.
     */
    suspend fun processCapturedNotificationAll(
        captured: CapturedNotificationInfo,
        dao: ExpenseDao,
        accountMatcher: KnownFinancialAccountMatcher? = matcher
    ): List<ExpensePersistenceResult> {
        return try {
            // Step 3: Normalization
            val normalized = NotificationNormalizer.normalize(captured)
            if (normalized == null) {
                val res = ExpensePersistenceResult.IgnoredNonFinancial("Notification could not be normalized")
                persistenceListener?.invoke(res)
                return listOf(res)
            }

            // Step 4: Financial Classification + Noise Detection
            val classification = FinancialClassifier.classify(normalized)

            if (classification.financialRelevance == FinancialRelevance.NON_FINANCIAL) {
                Log.d(TAG, "Notification ${captured.notificationKey} ignored: not financially relevant")
                val res = ExpensePersistenceResult.IgnoredNonFinancial("Notification is not financially relevant")
                persistenceListener?.invoke(res)
                return listOf(res)
            }

            if (classification.isNoise) {
                val reason = classification.noiseReasons.joinToString("; ").ifBlank { classification.noiseCategory.name }
                Log.d(TAG, "Notification ${captured.notificationKey} ignored: classified as noise ($reason)")
                val res = ExpensePersistenceResult.IgnoredNonFinancial("Notification classified as noise: $reason")
                persistenceListener?.invoke(res)
                return listOf(res)
            }

            // Step 4.5: ACTUAL TRANSACTION EVENT GATE
            val eventAssessment = ActualTransactionEventGate.assessNotification(normalized, classification)
            if (eventAssessment.actualEvent == ActualEventStatus.FALSE) {
                val reason = eventAssessment.diagnosticReasons.joinToString("; ").ifBlank { eventAssessment.eventType.name }
                Log.d(TAG, "Notification ${captured.notificationKey} ignored: Actual Transaction Event Gate rejected ($reason)")
                val res = ExpensePersistenceResult.IgnoredNonFinancial("No actual transaction event: $reason")
                persistenceListener?.invoke(res)
                return listOf(res)
            }

            // Step 5: Structured Transaction Extraction (Support multi-candidate)
            val candidates = StructuredTransactionExtractor.extractAll(classification)
            if (candidates.isEmpty()) {
                Log.d(TAG, "Notification ${captured.notificationKey} ignored: structured extraction returned empty")
                val res = ExpensePersistenceResult.IgnoredNonFinancial("No structured transaction candidate could be extracted")
                persistenceListener?.invoke(res)
                return listOf(res)
            }

            val results = mutableListOf<ExpensePersistenceResult>()
            val claimedRecordIds = mutableSetOf<String>()
            for (candidate in candidates) {
                val validated = TransactionValidator.validate(candidate, classification)
                if (validated.isRejected) {
                    val reason = validated.rejectionReasons.joinToString("; ").ifBlank { "Validation rejected" }
                    Log.d(TAG, "Notification candidate ${candidate.sourceNotificationKey} rejected by validator: $reason")
                    val res = ExpensePersistenceResult.Rejected("Transaction candidate rejected: $reason")
                    persistenceListener?.invoke(res)
                    results.add(res)
                    continue
                }

                // Invariant Guard: Strictly reject absent or non-positive amount
                val amount = candidate.amount
                if (amount == null || amount <= 0.0 || amount.isNaN() || amount.isInfinite()) {
                    Log.d(TAG, "Notification candidate ${candidate.sourceNotificationKey} rejected: invalid amount ($amount)")
                    val res = ExpensePersistenceResult.Rejected("Transaction amount is absent, non-positive, or non-finite: $amount")
                    persistenceListener?.invoke(res)
                    results.add(res)
                    continue
                }

                // Step 6.5: Known Financial Account Matching & Enrichment (Milestone 4)
                val effectiveMatcher = accountMatcher ?: matcher
                val matchResult = effectiveMatcher?.match(candidate) ?: KnownFinancialAccountMatchResult.NoAccountData
                val enrichedCandidate = if (matchResult is KnownFinancialAccountMatchResult.Matched) {
                    candidate.copy(
                        accountEnrichment = matchResult.enrichment,
                        accountMatchResult = matchResult
                    )
                } else {
                    candidate.copy(
                        accountMatchResult = matchResult
                    )
                }
                val enrichedValidated = validated.copy(candidate = enrichedCandidate)

                val res = processValidatedCandidate(enrichedValidated, dao, claimedRecordIds)
                results.add(res)
            }

            results
        } catch (e: Exception) {
            Log.e(TAG, "Unhandled error in processCapturedNotificationAll", e)
            val res = ExpensePersistenceResult.Error(e, e.message ?: "Pipeline processing error")
            persistenceListener?.invoke(res)
            listOf(res)
        }
    }

    /**
     * Complete pipeline entry point for a captured notification using Context/Database.
     */
    suspend fun processCapturedNotification(
        context: Context,
        captured: CapturedNotificationInfo,
        database: AppDatabase = AppDatabase.getDatabase(context)
    ): ExpensePersistenceResult {
        if (matcher == null) {
            val accountRepo = com.example.arctracker.data.KnownFinancialAccountRepository(database.knownFinancialAccountDao())
            matcher = KnownFinancialAccountMatcher(accountRepo)
        }
        return processCapturedNotification(captured, database.expenseDao(), matcher)
    }

    suspend fun processCapturedNotificationAll(
        context: Context,
        captured: CapturedNotificationInfo,
        database: AppDatabase = AppDatabase.getDatabase(context)
    ): List<ExpensePersistenceResult> {
        if (matcher == null) {
            val accountRepo = com.example.arctracker.data.KnownFinancialAccountRepository(database.knownFinancialAccountDao())
            matcher = KnownFinancialAccountMatcher(accountRepo)
        }
        return processCapturedNotificationAll(captured, database.expenseDao(), matcher)
    }

    /**
     * Unified pipeline entry point for any [TransactionSourceEvent] (Notification or SMS_HISTORY)
     * using [ExpenseDao] (Milestone 1).
     */
    suspend fun processSourceEvent(
        event: TransactionSourceEvent,
        dao: ExpenseDao
    ): List<ExpensePersistenceResult> {
        val captured = event.toCapturedNotificationInfo()
        return processCapturedNotificationAll(captured, dao)
    }

    suspend fun processSourceEvent(
        context: Context,
        event: TransactionSourceEvent,
        database: AppDatabase = AppDatabase.getDatabase(context)
    ): List<ExpensePersistenceResult> {
        return processSourceEvent(event, database.expenseDao())
    }

    /**
     * Pipeline entry point for historical SMS messages using ExpenseDao.
     */
    suspend fun processSms(
        smsId: String,
        sender: String,
        body: String,
        timestamp: Long,
        dao: ExpenseDao
    ): ExpensePersistenceResult {
        val event = TransactionSourceEvent(
            sourceType = TransactionSourceType.SMS_HISTORY,
            sourceId = "sms_$smsId",
            sender = sender,
            rawText = body,
            eventTimestamp = timestamp,
            title = sender,
            category = "sms"
        )
        val results = processSourceEvent(event, dao)
        return results.firstOrNull { it is ExpensePersistenceResult.Inserted || it is ExpensePersistenceResult.ReviewPending || it is ExpensePersistenceResult.Updated || it is ExpensePersistenceResult.Enriched }
            ?: results.firstOrNull()
            ?: ExpensePersistenceResult.IgnoredNonFinancial("No transactions processed")
    }

    /**
     * Pipeline entry point for historical SMS messages using Context/Database.
     */
    suspend fun processSms(
        context: Context,
        smsId: String,
        sender: String,
        body: String,
        timestamp: Long,
        database: AppDatabase = AppDatabase.getDatabase(context)
    ): ExpensePersistenceResult {
        return processSms(smsId, sender, body, timestamp, database.expenseDao())
    }

    /**
     * Processes a validated candidate through deduplication and persists the result.
     */
    suspend fun processValidatedCandidate(
        validated: ValidatedTransactionCandidate,
        dao: ExpenseDao,
        claimedRecordIds: MutableSet<String>? = null
    ): ExpensePersistenceResult {
        if (validated.isRejected) {
            val reason = validated.rejectionReasons.joinToString("; ").ifBlank { "Validation rejected" }
            val res = ExpensePersistenceResult.Rejected("Transaction candidate rejected: $reason")
            persistenceListener?.invoke(res)
            return res
        }

        val amount = validated.candidate.amount
        if (amount == null || amount <= 0.0 || amount.isNaN() || amount.isInfinite()) {
            val res = ExpensePersistenceResult.Rejected("Transaction amount is absent, non-positive, or non-finite: $amount")
            persistenceListener?.invoke(res)
            return res
        }
        return try {
            // Targeted query lookup: fetch only relevant correlation/dedup candidates instead of full table
            val targetedExpenses = findTargetedCandidates(validated, dao)
            val existingRecords = targetedExpenses
                .filter { expense ->
                    claimedRecordIds == null || (!claimedRecordIds.contains(expense.id.toString()) && !claimedRecordIds.contains(expense.notificationKey))
                }
                .map { TransactionRecord.fromExpense(it) }

            // Step 7: Deduplication and Cross-Source Correlation
            val dedupResult = TransactionDeduplicator.evaluate(validated, existingRecords)
            if (dedupResult.matchedRecordId != null) {
                claimedRecordIds?.add(dedupResult.matchedRecordId)
            }

            // Step 8: Room Database Persistence based on deduplication decision
            val result = processDedupResult(dedupResult, dao, targetedExpenses)
            persistenceListener?.invoke(result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting validated transaction candidate", e)
            val res = ExpensePersistenceResult.Error(e, e.message ?: "Database persistence error")
            persistenceListener?.invoke(res)
            res
        }
    }

    /**
     * Performs targeted lookup of relevant past candidates rather than loading the entire table.
     * Order of lookup:
     * 1. Exact notificationKey / source identity
     * 2. Time-window candidates within 48 hours (max window for reference or fingerprint correlation)
     * 3. Any active pending expenses
     */
    suspend fun findTargetedCandidates(
        validated: ValidatedTransactionCandidate,
        dao: ExpenseDao
    ): List<Expense> {
        val key = validated.candidate.sourceNotificationKey
        if (key.isNotBlank()) {
            val exactKeyMatch = dao.getExpenseByNotificationKey(key)
            if (exactKeyMatch != null) {
                return listOf(exactKeyMatch)
            }
        }

        val candTime = if (validated.candidate.transactionTimestamp > 0) {
            validated.candidate.transactionTimestamp
        } else if (validated.candidate.postTime > 0) {
            validated.candidate.postTime
        } else {
            System.currentTimeMillis()
        }
        val windowMs = TransactionDeduplicator.REFERENCE_ID_CORRELATION_WINDOW_MS
        val windowStart = candTime - windowMs
        val windowEnd = candTime + windowMs

        val windowExpenses = dao.getExpensesBetween(windowStart, windowEnd)
        val pendingExpenses = dao.getPendingExpensesList()

        val candidateMap = LinkedHashMap<Int, Expense>()
        for (expense in windowExpenses) {
            candidateMap[expense.id] = expense
        }
        val postTime = validated.candidate.postTime
        if (postTime > 0 && Math.abs(postTime - candTime) > windowMs) {
            for (expense in dao.getExpensesBetween(postTime - windowMs, postTime + windowMs)) {
                candidateMap[expense.id] = expense
            }
        }
        for (expense in pendingExpenses) {
            candidateMap[expense.id] = expense
        }
        return candidateMap.values.toList()
    }

    suspend fun processValidatedCandidate(
        validated: ValidatedTransactionCandidate,
        database: AppDatabase
    ): ExpensePersistenceResult {
        return processValidatedCandidate(validated, database.expenseDao())
    }

    suspend fun processDedupResult(
        result: TransactionDeduplicationResult,
        database: AppDatabase,
        existingExpenses: List<Expense>? = null
    ): ExpensePersistenceResult {
        return processDedupResult(result, database.expenseDao(), existingExpenses)
    }

    /**
     * Executes the persistence operation according to the deduplication decision.
     * Guaranteed thread-safe via mutex locking.
     */
    suspend fun processDedupResult(
        result: TransactionDeduplicationResult,
        dao: ExpenseDao,
        existingExpenses: List<Expense>? = null
    ): ExpensePersistenceResult = pipelineMutex.withLock {
        try {
            val key = result.candidate.candidate.sourceNotificationKey
            val existingByKey = if (key.isNotBlank()) {
                dao.getExpenseByNotificationKey(key)
            } else {
                null
            }

            val candidate = result.candidate.candidate
            val candidateAccountId = candidate.accountEnrichment?.accountId
                ?: (candidate.accountMatchResult as? KnownFinancialAccountMatchResult.Matched)?.account?.id
            val rawSuffix = candidate.accountSuffix ?: candidate.cardSuffix
            val candidateAccountSuffix = rawSuffix?.let { AccountIdentityExtractor.safeSuffix(it) }
                ?: (candidate.accountMatchResult as? KnownFinancialAccountMatchResult.Matched)?.account?.accountSuffix

            when (result.decision) {
                DedupDecision.DUPLICATE -> {
                    // Exact duplicate detected!
                    val existing = existingByKey
                        ?: (result.matchedRecordId?.let { id ->
                            existingExpenses?.find { it.id.toString() == id || it.notificationKey == id }
                        })
                        ?: (result.matchedRecord?.let { rec ->
                            existingExpenses?.find { it.id.toString() == rec.id || it.notificationKey == rec.id }
                        })
                        ?: mapToExpense(candidate, false)

                    val resolvedExisting = if (existing.id > 0 && existing.accountId == null && candidateAccountId != null) {
                        val newSuffix = existing.accountSuffix ?: candidateAccountSuffix
                        val enriched = existing.copy(
                            accountId = candidateAccountId,
                            accountSuffix = newSuffix
                        )
                        dao.updateAccountMetadata(existing.id, candidateAccountId, newSuffix)
                        enriched
                    } else {
                        existing
                    }

                    Log.d(TAG, "Candidate duplicate skipped for key=$key, reason=${result.reason}")
                    ExpensePersistenceResult.SkippedDuplicate(resolvedExisting, result.reason, matchResult = candidate.accountMatchResult)
                }

                DedupDecision.UPDATE_EXISTING -> {
                    // An update to an existing transaction (e.g. status changed from PENDING to SUCCESS)
                    val existing = existingByKey
                        ?: (result.matchedRecordId?.let { id ->
                            existingExpenses?.find { it.id.toString() == id || it.notificationKey == id }
                        })
                        ?: (result.matchedRecord?.let { rec ->
                            existingExpenses?.find { it.id.toString() == rec.id || it.notificationKey == rec.id }
                        })

                    if (existing == null) {
                        // Fallback: insert as new if previous record not located
                        insertNewCandidate(candidate, result, dao)
                    } else if (existing.source == "MANUAL") {
                        // Protect user's manual expense: never overwrite manual expense
                        Log.i(TAG, "Protecting manual expense id=${existing.id}; skipping overwrite by notification")
                        ExpensePersistenceResult.SkippedDuplicate(existing, "Protected manual expense cannot be overwritten", matchResult = candidate.accountMatchResult)
                    } else {
                        val isNowSuccess = candidate.status == TransactionStatus.SUCCESS
                        val newAmount = if (existing.amount <= 0.0 && (candidate.amount ?: 0.0) > 0.0) {
                            candidate.amount!!
                        } else {
                            existing.amount
                        }
                        val newMerchant = if ((existing.merchant.isBlank() || existing.merchant == "Unknown Merchant") && !candidate.merchant.isNullOrBlank()) {
                            candidate.merchant!!
                        } else {
                            existing.merchant
                        }
                        val newNote = enrichNote(existing.note, candidate, "Status: ${candidate.status ?: "SUCCESS"}")

                        val newAccountId = existing.accountId ?: candidateAccountId
                        val newAccountSuffix = existing.accountSuffix ?: candidateAccountSuffix

                        val updated = existing.copy(
                            amount = newAmount,
                            merchant = newMerchant,
                            isPending = if (isNowSuccess) false else existing.isPending,
                            note = newNote,
                            rawText = candidate.rawContent ?: existing.rawText,
                            accountId = newAccountId,
                            accountSuffix = newAccountSuffix
                        )
                        dao.update(updated)
                        Log.d(TAG, "Updated existing transaction id=${updated.id} to isPending=${updated.isPending}")
                        ExpensePersistenceResult.Updated(updated, existing, result.reason, matchResult = candidate.accountMatchResult)
                    }
                }

                DedupDecision.CORRELATED -> {
                    // Cross-source correlation! (e.g. Bank SMS confirms earlier notification)
                    // Exactly ONE logical Expense is maintained. No duplicate row inserted!
                    val existing = existingByKey
                        ?: (result.matchedRecordId?.let { id ->
                            existingExpenses?.find { it.id.toString() == id || it.notificationKey == id }
                        })
                        ?: (result.matchedRecord?.let { rec ->
                            existingExpenses?.find { it.id.toString() == rec.id || it.notificationKey == rec.id }
                        })

                    if (existing == null) {
                        insertNewCandidate(candidate, result, dao)
                    } else if (existing.source == "MANUAL") {
                        // Protect user's manual expense: never overwrite or modify manual expense fields
                        Log.i(TAG, "Protecting manual expense id=${existing.id}; preserving all user manual fields")
                        ExpensePersistenceResult.SkippedDuplicate(existing, "Protected manual expense preserved without modification", matchResult = candidate.accountMatchResult)
                    } else {
                        val isNowSuccess = candidate.status == TransactionStatus.SUCCESS
                        val correlationDesc = "Correlated (${result.strategy}): ${result.correlationEvidence ?: result.reason}"
                        val newNote = enrichNote(existing.note, candidate, correlationDesc)

                        // If correlated record confirms a pending transaction, mark it successful
                        val newIsPending = if (isNowSuccess && existing.isPending) false else existing.isPending

                        val newAccountId = existing.accountId ?: candidateAccountId
                        val newAccountSuffix = existing.accountSuffix ?: candidateAccountSuffix

                        val enriched = existing.copy(
                            isPending = newIsPending,
                            note = newNote,
                            accountId = newAccountId,
                            accountSuffix = newAccountSuffix
                        )
                        dao.update(enriched)
                        Log.d(TAG, "Correlated transaction id=${enriched.id}, enriched note. Row count preserved.")
                        ExpensePersistenceResult.Enriched(enriched, correlationDesc, matchResult = candidate.accountMatchResult)
                    }
                }

                DedupDecision.NEEDS_REVIEW -> {
                    // Conflicting signals or ambiguous candidate -> Routes to Pending Expenses!
                    val amount = candidate.amount
                    if (amount == null || amount <= 0.0 || amount.isNaN() || amount.isInfinite()) {
                        return@withLock ExpensePersistenceResult.Rejected("Cannot insert review transaction with invalid amount: $amount")
                    }
                    if (existingByKey != null) {
                        return@withLock ExpensePersistenceResult.SkippedDuplicate(existingByKey, "Already recorded", matchResult = candidate.accountMatchResult)
                    }

                    val reviewExpense = mapToExpense(
                        candidate = candidate,
                        isPending = true,
                        extraNote = "Needs Review: ${result.reason}"
                    )
                    val newId = dao.insert(reviewExpense)
                    val saved = reviewExpense.copy(id = newId.toInt())
                    Log.d(TAG, "Inserted pending review expense id=${saved.id}, reason=${result.reason}")
                    ExpensePersistenceResult.ReviewPending(saved, result.reason, matchResult = candidate.accountMatchResult)
                }

                DedupDecision.NEW_TRANSACTION -> {
                    // Idempotency check: if key already exists, do not duplicate
                    if (existingByKey != null) {
                        Log.d(TAG, "Candidate with key=$key already exists in DB. Skipping duplicate insert.")
                        return@withLock ExpensePersistenceResult.SkippedDuplicate(existingByKey, "Already recorded with key $key", matchResult = candidate.accountMatchResult)
                    }

                    insertNewCandidate(candidate, result, dao)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Database failure safely handled in processDedupResult", e)
            ExpensePersistenceResult.Error(e, e.message ?: "Database write failure")
        }
    }

    private suspend fun insertNewCandidate(
        candidate: StructuredTransactionCandidate,
        result: TransactionDeduplicationResult,
        dao: ExpenseDao
    ): ExpensePersistenceResult {
        val amount = candidate.amount
        if (amount == null || amount <= 0.0 || amount.isNaN() || amount.isInfinite()) {
            return ExpensePersistenceResult.Rejected("Cannot insert transaction with invalid amount: $amount")
        }

        val isPending = result.candidate.needsReview ||
            candidate.status == TransactionStatus.PENDING

        val expense = mapToExpense(
            candidate = candidate,
            isPending = isPending,
            relationshipType = result.relationshipType,
            relationshipId = result.relationshipId
        )
        val newId = dao.insert(expense)
        val saved = expense.copy(id = newId.toInt())

        // If this is a self-transfer, also update the existing matched counterpart in Room
        if (result.relationshipType != null && result.matchedRecordId != null) {
            val existing = dao.getExpenseByNotificationKey(result.matchedRecordId)
                ?: result.matchedRecordId.toIntOrNull()?.let { dao.getExpenseById(it) }
            if (existing != null && (existing.relationshipType == null || existing.relationshipId == null)) {
                val updatedExisting = existing.copy(
                    relationshipType = result.relationshipType,
                    relationshipId = result.relationshipId
                )
                dao.update(updatedExisting)
                Log.d(TAG, "Updated existing counterpart transaction id=${existing.id} with relationship ${result.relationshipType}")
            }
        }

        Log.d(TAG, "Inserted new transaction id=${saved.id}, isPending=$isPending, amount=${saved.amount}")
        return ExpensePersistenceResult.Inserted(saved, isPending, result.reason, matchResult = candidate.accountMatchResult)
    }

    /**
     * Maps [StructuredTransactionCandidate] to [Expense] entity.
     */
    fun mapToExpense(
        candidate: StructuredTransactionCandidate,
        isPending: Boolean,
        extraNote: String? = null,
        relationshipType: String? = null,
        relationshipId: String? = null,
        userRules: List<com.example.arctracker.data.UserCategoryRule>? = null,
        merchantAliases: List<com.example.arctracker.data.MerchantAlias>? = null
    ): Expense {
        val amount = candidate.amount
            ?: throw IllegalArgumentException("Candidate amount cannot be null when mapping to Expense")
        require(amount > 0.0 && !amount.isNaN() && !amount.isInfinite()) {
            "Expense amount must be strictly positive and finite, was $amount"
        }

        val merchant = candidate.merchant?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) && !it.equals("Unknown Merchant", ignoreCase = true) }
            ?: candidate.counterparty?.takeIf { it.isNotBlank() }
            ?: ""

        val dateMillis = if (candidate.transactionTimestamp > 0) {
            candidate.transactionTimestamp
        } else if (candidate.postTime > 0) {
            candidate.postTime
        } else {
            System.currentTimeMillis()
        }
        val type = if (candidate.direction == TransactionDirection.CREDIT) "Credit" else "Debit"

        // Milestone 10 & 11: Category Inference with User Rules & Aliases
        val effectiveRules = userRules ?: kotlinx.coroutines.runBlocking {
            try { ruleManager?.getActiveRules() } catch (e: Exception) { null }
        }
        val effectiveAliases = merchantAliases ?: kotlinx.coroutines.runBlocking {
            try { aliasManager?.getActiveAliases() } catch (e: Exception) { null }
        }

        val inferenceResult = try {
            CategoryInferenceEngine().inferCategory(
                CategoryInferenceInput(
                    merchant = merchant,
                    counterparty = candidate.counterparty,
                    rawText = candidate.rawContent,
                    transactionType = type,
                    amount = amount,
                    userRules = effectiveRules,
                    merchantAliases = effectiveAliases
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Category inference failure safely handled", e)
            CategoryInferenceResult.noMatch("Inference failure safely handled: ${e.message}")
        }

        val categoryId = if (inferenceResult.isAutoAssignable) inferenceResult.suggestedCategoryId else null
        val tag = if (inferenceResult.isAutoAssignable) inferenceResult.suggestedCategoryName else inferTag(candidate)
        val categorySource = if (inferenceResult.isAutoAssignable) CategorySource.INFERRED else CategorySource.NONE

        val note = buildNote(candidate, extraNote)
        val source = if (candidate.sourceNotificationKey.startsWith("sms_")) "SMS_HISTORY" else "NOTIFICATION"

        val accountId = candidate.accountEnrichment?.accountId
            ?: (candidate.accountMatchResult as? KnownFinancialAccountMatchResult.Matched)?.account?.id
        val rawSuffix = candidate.accountSuffix ?: candidate.cardSuffix
        val accountSuffix = rawSuffix?.let { AccountIdentityExtractor.safeSuffix(it) }
            ?: (candidate.accountMatchResult as? KnownFinancialAccountMatchResult.Matched)?.account?.accountSuffix

        return Expense(
            id = 0,
            amount = amount,
            merchant = merchant,
            dateMillis = dateMillis,
            type = type,
            notificationKey = candidate.sourceNotificationKey,
            isPending = isPending,
            rawText = candidate.rawContent,
            tag = tag,
            note = note,
            source = source,
            relationshipType = relationshipType,
            relationshipId = relationshipId,
            accountId = accountId,
            accountSuffix = accountSuffix,
            categoryId = categoryId,
            categorySource = categorySource
        )
    }

    /**
     * Incurs reasonable expense category tag based on merchant name.
     */
    fun inferTag(candidate: StructuredTransactionCandidate): String? {
        val name = "${candidate.merchant ?: ""} ${candidate.counterparty ?: ""} ${candidate.rawContent ?: ""}".lowercase()
        return when {
            name.contains("zomato") || name.contains("swiggy") || name.contains("starbucks") ||
                name.contains("cafe") || name.contains("restaurant") || name.contains("food") -> "Food"
            name.contains("amazon") || name.contains("flipkart") || name.contains("myntra") ||
                name.contains("store") || name.contains("mart") -> "Shopping"
            name.contains("uber") || name.contains("ola") || name.contains("metro") ||
                name.contains("travel") || name.contains("rail") || name.contains("flight") -> "Travel"
            name.contains("bill") || name.contains("electricity") || name.contains("recharge") ||
                name.contains("bescom") || name.contains("airtel") || name.contains("jio") -> "Bills"
            name.contains("salary") || name.contains("payroll") || name.contains("corp") -> "Salary"
            else -> null
        }
    }

    /**
     * Builds structured note containing references, account suffixes, and UTR.
     */
    fun buildNote(candidate: StructuredTransactionCandidate, extraNote: String? = null): String {
        val parts = mutableListOf<String>()
        candidate.utr?.let { parts.add("UTR: $it") }
        candidate.referenceId?.let { if (it != candidate.utr) parts.add("Ref: $it") }
        candidate.accountSuffix?.let { parts.add("A/c: XX$it") }
        candidate.cardSuffix?.let { parts.add("Card: XX$it") }
        candidate.upiId?.let { parts.add("UPI: $it") }
        extraNote?.let { parts.add(it) }
        return parts.joinToString(" | ")
    }

    /**
     * Enriches an existing note without duplicating tokens.
     */
    fun enrichNote(currentNote: String?, candidate: StructuredTransactionCandidate, enrichment: String): String {
        val base = currentNote ?: ""
        val addition = buildNote(candidate, enrichment)
        return when {
            base.isBlank() -> addition
            addition.isBlank() -> base
            base.contains(addition) -> base
            else -> "$base | $addition"
        }
    }
}
