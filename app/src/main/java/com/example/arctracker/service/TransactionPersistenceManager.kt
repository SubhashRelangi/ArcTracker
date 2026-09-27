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
    data class Inserted(val expense: Expense, val isPending: Boolean, val reason: String) : ExpensePersistenceResult()
    data class Updated(val updatedExpense: Expense, val previousExpense: Expense, val changeDescription: String) : ExpensePersistenceResult()
    data class SkippedDuplicate(val existingExpense: Expense, val reason: String) : ExpensePersistenceResult()
    data class Enriched(val enrichedExpense: Expense, val correlationInfo: String) : ExpensePersistenceResult()
    data class ReviewPending(val expense: Expense, val reason: String) : ExpensePersistenceResult()
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

    /**
     * Complete pipeline entry point for a captured notification using ExpenseDao.
     */
    /**
     * Complete pipeline entry point for a captured notification using ExpenseDao.
     * Supports one notification containing 0, 1, or N independent transaction candidates.
     */
    suspend fun processCapturedNotification(
        captured: CapturedNotificationInfo,
        dao: ExpenseDao
    ): ExpensePersistenceResult {
        val results = processCapturedNotificationAll(captured, dao)
        return results.firstOrNull { it is ExpensePersistenceResult.Inserted || it is ExpensePersistenceResult.ReviewPending || it is ExpensePersistenceResult.Updated || it is ExpensePersistenceResult.Enriched }
            ?: results.firstOrNull()
            ?: ExpensePersistenceResult.IgnoredNonFinancial("No transactions processed")
    }

    /**
     * Processes all transaction candidates found within a captured notification.
     */
    suspend fun processCapturedNotificationAll(
        captured: CapturedNotificationInfo,
        dao: ExpenseDao
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

            // Step 5: Structured Transaction Extraction (Support multi-candidate)
            val candidates = StructuredTransactionExtractor.extractAll(classification)
            if (candidates.isEmpty()) {
                Log.d(TAG, "Notification ${captured.notificationKey} ignored: structured extraction returned empty")
                val res = ExpensePersistenceResult.IgnoredNonFinancial("No structured transaction candidate could be extracted")
                persistenceListener?.invoke(res)
                return listOf(res)
            }

            val results = mutableListOf<ExpensePersistenceResult>()
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

                val res = processValidatedCandidate(validated, dao)
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
        return processCapturedNotification(captured, database.expenseDao())
    }

    suspend fun processCapturedNotificationAll(
        context: Context,
        captured: CapturedNotificationInfo,
        database: AppDatabase = AppDatabase.getDatabase(context)
    ): List<ExpensePersistenceResult> {
        return processCapturedNotificationAll(captured, database.expenseDao())
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
        val captured = CapturedNotificationInfo(
            packageName = sender,
            notificationKey = "sms_$smsId",
            postTime = timestamp,
            title = sender,
            text = body,
            category = "sms"
        )
        return processCapturedNotification(captured, dao)
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
        dao: ExpenseDao
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
            val existingRecords = targetedExpenses.map { TransactionRecord.fromExpense(it) }

            // Step 7: Deduplication and Cross-Source Correlation
            val dedupResult = TransactionDeduplicator.evaluate(validated, existingRecords)

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

        val candTime = if (validated.candidate.postTime > 0) validated.candidate.postTime else System.currentTimeMillis()
        val windowMs = TransactionDeduplicator.REFERENCE_ID_CORRELATION_WINDOW_MS
        val windowStart = candTime - windowMs
        val windowEnd = candTime + windowMs

        val windowExpenses = dao.getExpensesBetween(windowStart, windowEnd)
        val pendingExpenses = dao.getPendingExpensesList()

        val candidateMap = LinkedHashMap<Int, Expense>()
        for (expense in windowExpenses) {
            candidateMap[expense.id] = expense
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

            when (result.decision) {
                DedupDecision.DUPLICATE -> {
                    // Exact duplicate detected! Zero database writes.
                    val existing = existingByKey
                        ?: (result.matchedRecordId?.let { id ->
                            existingExpenses?.find { it.id.toString() == id || it.notificationKey == id }
                        })
                        ?: (result.matchedRecord?.let { rec ->
                            existingExpenses?.find { it.id.toString() == rec.id || it.notificationKey == rec.id }
                        })
                        ?: mapToExpense(candidate, false)

                    Log.d(TAG, "Candidate duplicate skipped for key=$key, reason=${result.reason}")
                    ExpensePersistenceResult.SkippedDuplicate(existing, result.reason)
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
                        ExpensePersistenceResult.SkippedDuplicate(existing, "Protected manual expense cannot be overwritten")
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

                        val updated = existing.copy(
                            amount = newAmount,
                            merchant = newMerchant,
                            isPending = if (isNowSuccess) false else existing.isPending,
                            note = newNote,
                            rawText = candidate.rawContent ?: existing.rawText
                        )
                        dao.update(updated)
                        Log.d(TAG, "Updated existing transaction id=${updated.id} to isPending=${updated.isPending}")
                        ExpensePersistenceResult.Updated(updated, existing, result.reason)
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
                        ExpensePersistenceResult.SkippedDuplicate(existing, "Protected manual expense preserved without modification")
                    } else {
                        val isNowSuccess = candidate.status == TransactionStatus.SUCCESS
                        val correlationDesc = "Correlated (${result.strategy}): ${result.correlationEvidence ?: result.reason}"
                        val newNote = enrichNote(existing.note, candidate, correlationDesc)

                        // If correlated record confirms a pending transaction, mark it successful
                        val newIsPending = if (isNowSuccess && existing.isPending) false else existing.isPending

                        val enriched = existing.copy(
                            isPending = newIsPending,
                            note = newNote
                        )
                        dao.update(enriched)
                        Log.d(TAG, "Correlated transaction id=${enriched.id}, enriched note. Row count preserved.")
                        ExpensePersistenceResult.Enriched(enriched, correlationDesc)
                    }
                }

                DedupDecision.NEEDS_REVIEW -> {
                    // Conflicting signals or ambiguous candidate -> Routes to Pending Expenses!
                    val amount = candidate.amount
                    if (amount == null || amount <= 0.0 || amount.isNaN() || amount.isInfinite()) {
                        return@withLock ExpensePersistenceResult.Rejected("Cannot insert review transaction with invalid amount: $amount")
                    }
                    if (existingByKey != null) {
                        return@withLock ExpensePersistenceResult.SkippedDuplicate(existingByKey, "Already recorded")
                    }

                    val reviewExpense = mapToExpense(
                        candidate = candidate,
                        isPending = true,
                        extraNote = "Needs Review: ${result.reason}"
                    )
                    val newId = dao.insert(reviewExpense)
                    val saved = reviewExpense.copy(id = newId.toInt())
                    Log.d(TAG, "Inserted pending review expense id=${saved.id}, reason=${result.reason}")
                    ExpensePersistenceResult.ReviewPending(saved, result.reason)
                }

                DedupDecision.NEW_TRANSACTION -> {
                    // Idempotency check: if key already exists, do not duplicate
                    if (existingByKey != null) {
                        Log.d(TAG, "Candidate with key=$key already exists in DB. Skipping duplicate insert.")
                        return@withLock ExpensePersistenceResult.SkippedDuplicate(existingByKey, "Already recorded with key $key")
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

        val expense = mapToExpense(candidate, isPending = isPending)
        val newId = dao.insert(expense)
        val saved = expense.copy(id = newId.toInt())
        Log.d(TAG, "Inserted new transaction id=${saved.id}, isPending=$isPending, amount=${saved.amount}")
        return ExpensePersistenceResult.Inserted(saved, isPending, result.reason)
    }

    /**
     * Maps [StructuredTransactionCandidate] to [Expense] entity.
     */
    fun mapToExpense(
        candidate: StructuredTransactionCandidate,
        isPending: Boolean,
        extraNote: String? = null
    ): Expense {
        val amount = candidate.amount
            ?: throw IllegalArgumentException("Candidate amount cannot be null when mapping to Expense")
        require(amount > 0.0 && !amount.isNaN() && !amount.isInfinite()) {
            "Expense amount must be strictly positive and finite, was $amount"
        }

        val merchant = candidate.merchant?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) && !it.equals("Unknown Merchant", ignoreCase = true) }
            ?: candidate.counterparty?.takeIf { it.isNotBlank() }
            ?: ""

        val dateMillis = if (candidate.postTime > 0) candidate.postTime else System.currentTimeMillis()
        val type = if (candidate.direction == TransactionDirection.CREDIT) "Credit" else "Debit"
        val tag = inferTag(candidate)
        val note = buildNote(candidate, extraNote)
        val source = if (candidate.sourceNotificationKey.startsWith("sms_")) "SMS_HISTORY" else "NOTIFICATION"

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
            source = source
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
