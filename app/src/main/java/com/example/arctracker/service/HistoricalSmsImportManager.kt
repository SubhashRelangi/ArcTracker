package com.example.arctracker.service

import android.content.Context
import android.util.Log
import com.example.arctracker.data.AppDatabase
import com.example.arctracker.data.ExpenseDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Progress snapshot emitted during the non-destructive SMS scan phase (Milestone 3B / 4).
 */
data class SmsScanProgress(
    val recordsProcessed: Int,
    val totalEstimated: Int? = null,
    val financialDetected: Int = 0,
    val noiseDetected: Int = 0,
    val duplicatesDetected: Int = 0,
    val isCancelled: Boolean = false
)

/**
 * Progress snapshot emitted during the persistence reconciliation import phase (Milestone 3B / 4).
 */
data class SmsImportProgress(
    val itemsProcessed: Int,
    val totalToImport: Int,
    val insertedCount: Int = 0,
    val updatedCount: Int = 0,
    val duplicatesSkipped: Int = 0,
    val reviewPendingCount: Int = 0,
    val isCancelled: Boolean = false
)

/**
 * Individual transaction item analyzed during the SCAN phase.
 * Holds all structured candidate intelligence needed for the subsequent IMPORT phase.
 */
data class ScannedTransactionItem(
    val candidate: ValidatedTransactionCandidate,
    val sourceEvent: TransactionSourceEvent,
    val plannedDecision: DedupDecision,
    val matchStrategy: MatchStrategy,
    val matchedRecordId: String? = null,
    val reason: String,
    val accountIdentity: FinancialAccountIdentity = FinancialAccountIdentity()
)

/**
 * Result of the non-destructive SCAN phase (Milestone 3B / 4).
 */
sealed class SmsScanResult {
    data class Success(
        val startTimeMillis: Long,
        val endTimeMillis: Long,
        val messagesScanned: Int,
        val financialMessages: Int,
        val nonFinancialMessages: Int,
        val noiseMessages: Int,
        val transactionCandidatesCount: Int,
        val newTransactionsCount: Int,
        val duplicatesCount: Int,
        val correlationsCount: Int,
        val pendingReviewCount: Int,
        val rejectedCount: Int,
        val totalDebitAmount: Double,
        val totalCreditAmount: Double,
        val scannedItems: List<ScannedTransactionItem>,
        val accountGroups: List<FinancialAccountGroup> = emptyList()
    ) : SmsScanResult()

    data object Cancelled : SmsScanResult()
    data object PermissionRequired : SmsScanResult()
    data class Failure(val error: Throwable, val message: String) : SmsScanResult()
}

/**
 * Result of the persistence reconciliation IMPORT phase (Milestone 3B / 4).
 */
sealed class SmsImportResult {
    data class Success(
        val totalProcessed: Int,
        val insertedCount: Int,
        val updatedCount: Int,
        val enrichedCount: Int,
        val duplicatesSkippedCount: Int,
        val reviewPendingCount: Int,
        val rejectedCount: Int,
        val errorCount: Int,
        val totalAmountImported: Double,
        val totalIncomeImported: Double,
        val persistenceResults: List<ExpensePersistenceResult>
    ) : SmsImportResult()

    data object Cancelled : SmsImportResult()
    data class Failure(val error: Throwable, val message: String) : SmsImportResult()
}

/**
 * Observable lifecycle state of the Historical SMS Import Orchestrator.
 */
sealed class HistoricalSmsImportState {
    data object Idle : HistoricalSmsImportState()
    data class Scanning(val progress: SmsScanProgress) : HistoricalSmsImportState()
    data class ScanComplete(val result: SmsScanResult.Success) : HistoricalSmsImportState()
    data object ScanCancelled : HistoricalSmsImportState()
    data object PermissionRequired : HistoricalSmsImportState()
    data class Importing(val progress: SmsImportProgress) : HistoricalSmsImportState()
    data class ImportComplete(val result: SmsImportResult.Success) : HistoricalSmsImportState()
    data object ImportCancelled : HistoricalSmsImportState()
    data class Error(val message: String, val throwable: Throwable? = null) : HistoricalSmsImportState()
}

/**
 * Milestone 3B / Milestone 4: Historical SMS Import Orchestrator.
 *
 * Coordinates:
 *   SmsReader
 *       ↓
 *   SmsRecord
 *       ↓
 *   TransactionSourceEvent
 *       ↓
 *   Shared Transaction Engine (Normalization -> Classification -> Noise -> Extractor -> Validator -> Deduplicator)
 *       ↓
 *   SmsScanResult (Pure analysis, ZERO Room writes)
 *       ↓
 *   Persistence Reconciliation (TransactionPersistenceManager -> ExpenseDao -> Room)
 *
 * CRITICAL ARCHITECTURAL GUARANTEES:
 * 1. STRICT SEPARATION: scan() is 100% non-destructive. It does not insert, update, or delete Room records.
 * 2. SINGLE SOURCE OF TRUTH: Uses the existing shared transaction intelligence without duplicating parsers.
 * 3. NO SENDER WHITELISTING: Unknown/mobile senders flow through classification rather than being rejected.
 * 4. MULTI-TRANSACTION SUPPORT: One SMS containing multiple candidates is fully extracted and analyzed.
 * 5. PERSISTENCE RECONCILIATION: importTransactions() determines NEW, DUPLICATE, CORRELATED, UPDATE_EXISTING,
 *    and NEEDS_REVIEW idempotently via TransactionPersistenceManager.
 * 6. STREAMING & CANCELLATION: Supports callback early stopping and resource safety without leaking cursors.
 */
class HistoricalSmsImportManager(
    private val smsReader: SmsReader,
    private val dao: ExpenseDao,
    private val persistenceManager: TransactionPersistenceManager = TransactionPersistenceManager
) {

    private val _state = MutableStateFlow<HistoricalSmsImportState>(HistoricalSmsImportState.Idle)
    val state: StateFlow<HistoricalSmsImportState> = _state.asStateFlow()

    private val executionMutex = Mutex()

    /**
     * Executes a non-destructive SCAN of historical SMS within [startTimeMillis..endTimeMillis].
     *
     * Analyzes all messages through the Shared Transaction Engine, evaluates deduplication/correlation
     * against existing database records, and returns structured statistics and candidates.
     * ZERO database mutations take place during this call.
     *
     * @param startTimeMillis Start boundary timestamp (inclusive).
     * @param endTimeMillis End boundary timestamp (inclusive).
     * @param progressCallback Optional callback returning false to cancel the scan.
     */
    suspend fun scan(
        startTimeMillis: Long,
        endTimeMillis: Long,
        progressCallback: ((SmsScanProgress) -> Boolean)? = null
    ): SmsScanResult = executionMutex.withLock {
        Log.i(TAG, "Starting non-destructive SMS scan for range [$startTimeMillis..$endTimeMillis]")

        var isCancelled = false
        var messagesScanned = 0
        var financialMessages = 0
        var nonFinancialMessages = 0
        var noiseMessages = 0
        var transactionCandidatesCount = 0
        var newTransactionsCount = 0
        var duplicatesCount = 0
        var correlationsCount = 0
        var pendingReviewCount = 0
        var rejectedCount = 0
        var totalDebitAmount = 0.0
        var totalCreditAmount = 0.0

        val scannedItems = mutableListOf<ScannedTransactionItem>()
        val claimedRecordIds = mutableSetOf<String>()
        val inScanSeenRecords = mutableListOf<TransactionRecord>()

        val readResult = smsReader.readSms(startTimeMillis, endTimeMillis) { smsRecord ->
            messagesScanned++

            // 1. Convert to normalized TransactionSourceEvent
            val event = smsRecord.toSourceEvent()
            val captured = event.toCapturedNotificationInfo()

            // 2. Normalization
            val normalized = NotificationNormalizer.normalize(captured)
            if (normalized == null) {
                nonFinancialMessages++
                val cont = dispatchScanProgress(messagesScanned, financialMessages, noiseMessages, duplicatesCount, progressCallback)
                if (!cont) isCancelled = true
                return@readSms cont
            }

            // 3. Financial Classification & Noise Detection
            val classification = FinancialClassifier.classify(normalized)
            if (classification.isNoise) {
                noiseMessages++
                val cont = dispatchScanProgress(messagesScanned, financialMessages, noiseMessages, duplicatesCount, progressCallback)
                if (!cont) isCancelled = true
                return@readSms cont
            }

            if (classification.financialRelevance == FinancialRelevance.NON_FINANCIAL) {
                nonFinancialMessages++
                val cont = dispatchScanProgress(messagesScanned, financialMessages, noiseMessages, duplicatesCount, progressCallback)
                if (!cont) isCancelled = true
                return@readSms cont
            }

            // 4. Structured Multi-Candidate Extraction
            val candidates = StructuredTransactionExtractor.extractAll(classification)
            if (candidates.isEmpty()) {
                nonFinancialMessages++
                val cont = dispatchScanProgress(messagesScanned, financialMessages, noiseMessages, duplicatesCount, progressCallback)
                if (!cont) isCancelled = true
                return@readSms cont
            }

            // Financially relevant message detected with valid candidates
            financialMessages++

            // 5. Validation and Non-Destructive Deduplication Evaluation for Each Candidate
            for (candidate in candidates) {
                val validated = TransactionValidator.validate(candidate, classification)
                val amount = candidate.amount

                if (validated.isRejected || amount == null || amount <= 0.0 || amount.isNaN() || amount.isInfinite()) {
                    rejectedCount++
                    continue
                }

                transactionCandidatesCount++

                // Non-destructive targeted candidate lookup from database
                val targetedExpenses = kotlinx.coroutines.runBlocking {
                    persistenceManager.findTargetedCandidates(validated, dao)
                }
                val existingRecordsFromDb = targetedExpenses
                    .filter { exp ->
                        !claimedRecordIds.contains(exp.id.toString()) && !claimedRecordIds.contains(exp.notificationKey)
                    }
                    .map { TransactionRecord.fromExpense(it) }

                // Combine DB records with prior in-scan candidates to prevent intra-scan duplicates
                val candidatePool = existingRecordsFromDb + inScanSeenRecords

                val dedupResult = TransactionDeduplicator.evaluate(validated, candidatePool)
                if (dedupResult.matchedRecordId != null) {
                    claimedRecordIds.add(dedupResult.matchedRecordId)
                }

                if (dedupResult.isNew) {
                    newTransactionsCount++
                    inScanSeenRecords.add(TransactionRecord.fromValidated(validated, TransactionSourceType.SMS_HISTORY))
                } else if (dedupResult.isDuplicate) {
                    duplicatesCount++
                } else if (dedupResult.isCorrelated) {
                    correlationsCount++
                } else if (dedupResult.needsReview) {
                    pendingReviewCount++
                } else if (dedupResult.isUpdate) {
                    // Update to existing transaction
                    correlationsCount++
                }

                // Sum planned monetary totals for actionable candidates
                if (dedupResult.isNew || dedupResult.isCorrelated || dedupResult.needsReview || dedupResult.isUpdate) {
                    if (candidate.direction == TransactionDirection.CREDIT) {
                        totalCreditAmount += amount
                    } else {
                        totalDebitAmount += amount
                    }
                }

                // Step 3: Account Identity Extraction (strictly after validation and deduplication)
                val accountIdentity = AccountIdentityExtractor.extractIdentity(
                    candidate = validated,
                    rawBody = smsRecord.body,
                    sender = smsRecord.address
                )

                scannedItems.add(
                    ScannedTransactionItem(
                        candidate = validated,
                        sourceEvent = event,
                        plannedDecision = dedupResult.decision,
                        matchStrategy = dedupResult.strategy,
                        matchedRecordId = dedupResult.matchedRecordId,
                        reason = dedupResult.reason,
                        accountIdentity = accountIdentity
                    )
                )
            }

            val cont = dispatchScanProgress(messagesScanned, financialMessages, noiseMessages, duplicatesCount, progressCallback)
            if (!cont) isCancelled = true
            cont
        }

        // Handle reader outcomes
        when (readResult) {
            is SmsReadResult.PermissionRequired -> {
                Log.w(TAG, "Scan failed: READ_SMS permission required")
                _state.value = HistoricalSmsImportState.PermissionRequired
                SmsScanResult.PermissionRequired
            }

            is SmsReadResult.Failure -> {
                Log.e(TAG, "Scan failed: ${readResult.message}", readResult.error)
                _state.value = HistoricalSmsImportState.Error(readResult.message, readResult.error)
                SmsScanResult.Failure(readResult.error, readResult.message)
            }

            is SmsReadResult.Success -> {
                if (isCancelled) {
                    Log.i(TAG, "Scan cancelled by caller after $messagesScanned messages")
                    _state.value = HistoricalSmsImportState.ScanCancelled
                    SmsScanResult.Cancelled
                } else {
                    // Step 4: Stable In-Memory Account Grouping (strictly ZERO Room writes)
                    val accountGroups = FinancialAccountGrouper.groupTransactions(scannedItems)

                    val result = SmsScanResult.Success(
                        startTimeMillis = startTimeMillis,
                        endTimeMillis = endTimeMillis,
                        messagesScanned = messagesScanned,
                        financialMessages = financialMessages,
                        nonFinancialMessages = nonFinancialMessages,
                        noiseMessages = noiseMessages,
                        transactionCandidatesCount = transactionCandidatesCount,
                        newTransactionsCount = newTransactionsCount,
                        duplicatesCount = duplicatesCount,
                        correlationsCount = correlationsCount,
                        pendingReviewCount = pendingReviewCount,
                        rejectedCount = rejectedCount,
                        totalDebitAmount = totalDebitAmount,
                        totalCreditAmount = totalCreditAmount,
                        scannedItems = scannedItems,
                        accountGroups = accountGroups
                    )
                    Log.i(TAG, "Scan completed successfully: $messagesScanned scanned, $financialMessages financial, $transactionCandidatesCount candidates")
                    _state.value = HistoricalSmsImportState.ScanComplete(result)
                    result
                }
            }
        }
    }

    /**
     * Executes persistence reconciliation for the candidates produced during [scan].
     *
     * Processes candidates idempotently through [TransactionPersistenceManager] and persists
     * to Room via [ExpenseDao].
     *
     * @param scanResult The successful scan result containing analyzed candidates.
     * @param progressCallback Optional callback returning false to cancel the import.
     */
    suspend fun importTransactions(
        scanResult: SmsScanResult.Success,
        progressCallback: ((SmsImportProgress) -> Boolean)? = null
    ): SmsImportResult = executionMutex.withLock {
        Log.i(TAG, "Starting persistence reconciliation for ${scanResult.scannedItems.size} scanned candidates")

        var insertedCount = 0
        var updatedCount = 0
        var enrichedCount = 0
        var duplicatesSkippedCount = 0
        var reviewPendingCount = 0
        var rejectedCount = 0
        var errorCount = 0
        var totalAmountImported = 0.0
        var totalIncomeImported = 0.0

        val persistenceResults = mutableListOf<ExpensePersistenceResult>()
        val claimedRecordIds = mutableSetOf<String>()
        val items = scanResult.scannedItems
        var isCancelled = false

        for ((index, item) in items.withIndex()) {
            if (progressCallback != null) {
                val progress = SmsImportProgress(
                    itemsProcessed = index,
                    totalToImport = items.size,
                    insertedCount = insertedCount,
                    updatedCount = updatedCount,
                    duplicatesSkipped = duplicatesSkippedCount,
                    reviewPendingCount = reviewPendingCount,
                    isCancelled = false
                )
                _state.value = HistoricalSmsImportState.Importing(progress)
                if (!progressCallback(progress)) {
                    isCancelled = true
                    break
                }
            }

            val result = persistenceManager.processValidatedCandidate(
                validated = item.candidate,
                dao = dao,
                claimedRecordIds = claimedRecordIds
            )
            persistenceResults.add(result)

            when (result) {
                is ExpensePersistenceResult.Inserted -> {
                    insertedCount++
                    val amt = result.expense.amount
                    if (result.expense.type.equals("Credit", ignoreCase = true)) {
                        totalIncomeImported += amt
                    } else {
                        totalAmountImported += amt
                    }
                }

                is ExpensePersistenceResult.Updated -> {
                    updatedCount++
                }

                is ExpensePersistenceResult.Enriched -> {
                    enrichedCount++
                }

                is ExpensePersistenceResult.SkippedDuplicate -> {
                    duplicatesSkippedCount++
                }

                is ExpensePersistenceResult.ReviewPending -> {
                    reviewPendingCount++
                    val amt = result.expense.amount
                    if (result.expense.type.equals("Credit", ignoreCase = true)) {
                        totalIncomeImported += amt
                    } else {
                        totalAmountImported += amt
                    }
                }

                is ExpensePersistenceResult.Rejected -> {
                    rejectedCount++
                }

                is ExpensePersistenceResult.Error -> {
                    errorCount++
                }

                is ExpensePersistenceResult.IgnoredNonFinancial -> {
                    // Non-financial item ignored during persistence
                }
            }
        }

        if (isCancelled) {
            Log.i(TAG, "Import cancelled by caller after processing $insertedCount inserts")
            _state.value = HistoricalSmsImportState.ImportCancelled
            return SmsImportResult.Cancelled
        }

        val importResult = SmsImportResult.Success(
            totalProcessed = persistenceResults.size,
            insertedCount = insertedCount,
            updatedCount = updatedCount,
            enrichedCount = enrichedCount,
            duplicatesSkippedCount = duplicatesSkippedCount,
            reviewPendingCount = reviewPendingCount,
            rejectedCount = rejectedCount,
            errorCount = errorCount,
            totalAmountImported = totalAmountImported,
            totalIncomeImported = totalIncomeImported,
            persistenceResults = persistenceResults
        )

        Log.i(TAG, "Import complete: $insertedCount inserted, $enrichedCount enriched, $duplicatesSkippedCount duplicates skipped, $reviewPendingCount review pending")
        _state.value = HistoricalSmsImportState.ImportComplete(importResult)
        return importResult
    }

    /**
     * Convenience method combining non-destructive SCAN followed immediately by IMPORT.
     */
    suspend fun scanAndImport(
        startTimeMillis: Long,
        endTimeMillis: Long,
        scanProgress: ((SmsScanProgress) -> Boolean)? = null,
        importProgress: ((SmsImportProgress) -> Boolean)? = null
    ): SmsImportResult {
        return when (val scanRes = scan(startTimeMillis, endTimeMillis, scanProgress)) {
            is SmsScanResult.Success -> importTransactions(scanRes, importProgress)
            is SmsScanResult.Cancelled -> SmsImportResult.Cancelled
            is SmsScanResult.PermissionRequired -> SmsImportResult.Failure(
                SecurityException("READ_SMS permission required"),
                "SMS permission not granted"
            )
            is SmsScanResult.Failure -> SmsImportResult.Failure(scanRes.error, scanRes.message)
        }
    }

    /**
     * Resets the internal observable state back to Idle.
     */
    fun resetState() {
        _state.value = HistoricalSmsImportState.Idle
    }

    private fun dispatchScanProgress(
        processed: Int,
        financial: Int,
        noise: Int,
        duplicates: Int,
        callback: ((SmsScanProgress) -> Boolean)?
    ): Boolean {
        val progress = SmsScanProgress(
            recordsProcessed = processed,
            financialDetected = financial,
            noiseDetected = noise,
            duplicatesDetected = duplicates,
            isCancelled = false
        )
        _state.value = HistoricalSmsImportState.Scanning(progress)
        return callback?.invoke(progress) ?: true
    }

    companion object {
        private const val TAG = "ArcTracker:SmsImport"

        fun create(
            context: Context,
            smsReader: SmsReader = SmsReader.create(context),
            database: AppDatabase = AppDatabase.getDatabase(context)
        ): HistoricalSmsImportManager {
            return HistoricalSmsImportManager(
                smsReader = smsReader,
                dao = database.expenseDao(),
                persistenceManager = TransactionPersistenceManager
            )
        }
    }
}
