package com.example.arctracker

import com.example.arctracker.data.Expense
import com.example.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * Step 10.1: Candidate-Level Cross-Event Deduplication with Transaction-Time Correlation Tests.
 *
 * Verifies all 20 test specifications from Section 22:
 * 1. Candidate already exists → DUPLICATE
 * 2. Candidate does not exist → NEW_TRANSACTION
 * 3. Two candidates, first duplicate, second new
 * 4. Two candidates, both new
 * 5. Two candidates, both already exist
 * 6. Same notification processed twice
 * 7. Notification + SMS same transaction
 * 8. Notification + SMS different transactions
 * 9. Explicit UTR match
 * 10. Explicit UTR conflict
 * 11. Same amount but different merchant
 * 12. Same merchant but different amount
 * 13. Same amount/merchant/direction but different transaction time
 * 14. Same transaction time but different transaction ID
 * 15. Notification post time differs from transaction time
 * 16. Missing transaction timestamp falls back to notification postTime
 * 17. Multiple candidates retain independent timestamps
 * 18. Two identical-looking transactions are not blindly merged
 * 19. Manual transaction remains protected
 * 20. Existing bounded DB query behavior remains intact
 */
class CandidateCrossEventDeduplicationTest {

    private lateinit var dao: FakeExpenseDao

    @Before
    fun setUp() {
        dao = FakeExpenseDao()
        TransactionPersistenceManager.persistenceListener = null
    }

    private fun makeCandidate(
        amount: Double = 500.0,
        merchant: String = "Amazon",
        direction: TransactionDirection = TransactionDirection.DEBIT,
        status: TransactionStatus = TransactionStatus.SUCCESS,
        utr: String? = null,
        referenceId: String? = utr,
        accountSuffix: String? = null,
        key: String = "notif_key_1",
        postTime: Long = 1758776700000L, // 10:35 AM
        txnTime: Long = 1758776400000L,  // 10:30 AM
        txnSource: TimestampSource = TimestampSource.CONTENT,
        raw: String = "Paid ₹500 to Amazon at 10:30 AM"
    ): ValidatedTransactionCandidate {
        val candidate = StructuredTransactionCandidate(
            sourceNotificationKey = key,
            packageName = "com.google.android.apps.nbu.paisa.user",
            postTime = postTime,
            transactionTimestamp = txnTime,
            transactionTimestampSource = txnSource,
            amount = amount,
            currency = "INR",
            merchant = merchant,
            direction = direction,
            status = status,
            referenceId = referenceId,
            utr = utr,
            accountSuffix = accountSuffix,
            rawContent = raw
        )
        return ValidatedTransactionCandidate(
            candidate = candidate,
            validationState = ValidationState.ACCEPTABLE,
            evidenceLevel = EvidenceLevel.VERY_STRONG,
            isStructurallyValid = true,
            validationReasons = listOf("Valid candidate")
        )
    }

    // 1. Candidate already exists -> DUPLICATE
    @Test
    fun test01_candidateAlreadyExists_isDuplicate() = runBlocking {
        val existing = Expense(
            id = 1,
            amount = 500.0,
            merchant = "Amazon",
            dateMillis = 1758776400000L,
            type = "Debit",
            notificationKey = "notif_key_1"
        )
        dao.insert(existing)

        val candidate = makeCandidate(key = "notif_key_1", amount = 500.0, merchant = "Amazon")
        val result = TransactionPersistenceManager.processValidatedCandidate(candidate, dao)

        assertTrue("Candidate should be detected as duplicate", result is ExpensePersistenceResult.SkippedDuplicate)
        assertEquals(1, dao.getCount())
    }

    // 2. Candidate does not exist -> NEW_TRANSACTION
    @Test
    fun test02_candidateDoesNotExist_isNewTransaction() = runBlocking {
        assertEquals(0, dao.getCount())
        val candidate = makeCandidate(key = "notif_key_new", amount = 500.0, merchant = "Amazon")
        val result = TransactionPersistenceManager.processValidatedCandidate(candidate, dao)

        assertTrue("New candidate should be inserted", result is ExpensePersistenceResult.Inserted)
        assertEquals(1, dao.getCount())
        val saved = dao.getAllExpensesList().first()
        assertEquals(500.0, saved.amount, 0.001)
        assertEquals("Amazon", saved.merchant)
        assertEquals(candidate.candidate.transactionTimestamp, saved.dateMillis)
    }

    // 3. Two candidates, first duplicate, second new
    @Test
    fun test03_twoCandidates_firstDuplicate_secondNew() = runBlocking {
        // Pre-insert Amazon ₹500 at 10:30
        val existing = Expense(
            id = 1,
            amount = 500.0,
            merchant = "Amazon",
            dateMillis = 1758776400000L, // 10:30
            type = "Debit",
            notificationKey = "test_notif#0"
        )
        dao.insert(existing)
        assertEquals(1, dao.getCount())

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "test_notif",
            postTime = 1758776700000L,
            title = "Google Pay",
            text = "10:30 Paid ₹500 to Amazon.\n10:32 Paid ₹250 to Flipkart.",
            category = "payment"
        )

        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)
        assertEquals(2, results.size)

        // First candidate should be skipped as duplicate
        assertTrue("Candidate 0 should be duplicate", results[0] is ExpensePersistenceResult.SkippedDuplicate)
        // Second candidate should be inserted
        assertTrue("Candidate 1 should be inserted as new", results[1] is ExpensePersistenceResult.Inserted)

        // Only 1 additional row inserted, total DB count = 2
        assertEquals(2, dao.getCount())
        val all = dao.getAllExpensesList()
        assertTrue(all.any { it.merchant == "Amazon" && it.amount == 500.0 })
        assertTrue(all.any { it.merchant == "Flipkart" && it.amount == 250.0 })
    }

    // 4. Two candidates, both new
    @Test
    fun test04_twoCandidates_bothNew() = runBlocking {
        assertEquals(0, dao.getCount())
        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "notif_both_new",
            postTime = 1758776700000L,
            title = "Google Pay",
            text = "10:30 Paid ₹500 to Amazon.\n10:32 Paid ₹250 to Flipkart.",
            category = "payment"
        )

        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)
        assertEquals(2, results.size)
        assertTrue(results[0] is ExpensePersistenceResult.Inserted)
        assertTrue(results[1] is ExpensePersistenceResult.Inserted)
        assertEquals(2, dao.getCount())
    }

    // 5. Two candidates, both already exist
    @Test
    fun test05_twoCandidates_bothAlreadyExist() = runBlocking {
        dao.insert(Expense(id = 1, amount = 500.0, merchant = "Amazon", dateMillis = 1758776400000L, type = "Debit", notificationKey = "notif_both_exist#0"))
        dao.insert(Expense(id = 2, amount = 250.0, merchant = "Flipkart", dateMillis = 1758776520000L, type = "Debit", notificationKey = "notif_both_exist#1"))
        assertEquals(2, dao.getCount())

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "notif_both_exist",
            postTime = 1758776700000L,
            title = "Google Pay",
            text = "10:30 Paid ₹500 to Amazon.\n10:32 Paid ₹250 to Flipkart.",
            category = "payment"
        )

        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)
        assertEquals(2, results.size)
        assertTrue(results[0] is ExpensePersistenceResult.SkippedDuplicate)
        assertTrue(results[1] is ExpensePersistenceResult.SkippedDuplicate)
        assertEquals(2, dao.getCount())
    }

    // 6. Same notification processed twice
    @Test
    fun test06_sameNotificationProcessedTwice() = runBlocking {
        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "notif_idempotency",
            postTime = 1758776700000L,
            title = "Google Pay",
            text = "10:30 Paid ₹500 to Amazon.\n10:32 Paid ₹250 to Flipkart.",
            category = "payment"
        )

        val firstRun = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)
        assertEquals(2, firstRun.size)
        assertEquals(2, dao.getCount())

        val secondRun = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)
        assertEquals(2, secondRun.size)
        assertTrue(secondRun[0] is ExpensePersistenceResult.SkippedDuplicate)
        assertTrue(secondRun[1] is ExpensePersistenceResult.SkippedDuplicate)
        assertEquals("Database count must not change on duplicate notification replay", 2, dao.getCount())
    }

    // 7. Notification + SMS same transaction
    @Test
    fun test07_notificationAndSms_sameTransaction() = runBlocking {
        // SMS was received first: ₹500 Amazon with UTR
        val smsExpense = Expense(
            id = 1,
            amount = 500.0,
            merchant = "Amazon",
            dateMillis = 1758776400000L,
            type = "Debit",
            note = "Ref: UTR123456789012",
            source = "SMS_HISTORY"
        )
        dao.insert(smsExpense)
        assertEquals(1, dao.getCount())

        // Later notification arrives for same transaction with matching UTR
        val notifCandidate = makeCandidate(
            amount = 500.0,
            merchant = "Amazon",
            utr = "UTR123456789012",
            key = "notif_amazon_pay",
            txnTime = 1758776400000L
        )

        val result = TransactionPersistenceManager.processValidatedCandidate(notifCandidate, dao)
        assertTrue("Notification should be correlated / duplicate with SMS",
            result is ExpensePersistenceResult.SkippedDuplicate || result is ExpensePersistenceResult.Enriched)
        assertEquals("No second row should be inserted", 1, dao.getCount())
    }

    // 8. Notification + SMS different transactions
    @Test
    fun test08_notificationAndSms_differentTransactions() = runBlocking {
        val smsExpense = Expense(
            id = 1,
            amount = 500.0,
            merchant = "Amazon",
            dateMillis = 1758776400000L,
            type = "Debit",
            note = "Ref: UTR111111111111",
            source = "SMS_HISTORY"
        )
        dao.insert(smsExpense)

        val notifCandidate = makeCandidate(
            amount = 500.0,
            merchant = "Swiggy",
            utr = "UTR222222222222",
            key = "notif_swiggy_pay",
            txnTime = 1758776400000L
        )

        val result = TransactionPersistenceManager.processValidatedCandidate(notifCandidate, dao)
        assertTrue("Different transactions should insert a new row", result is ExpensePersistenceResult.Inserted)
        assertEquals(2, dao.getCount())
    }

    // 9. Explicit UTR match
    @Test
    fun test09_explicitUtrMatch() {
        val candidate = makeCandidate(amount = 1200.0, merchant = "Merchant A", utr = "UTR998877665544")
        val existingRecord = TransactionRecord(
            id = "exist_1",
            amount = 1200.0,
            merchant = "Merchant A",
            direction = TransactionDirection.DEBIT,
            utr = "UTR998877665544",
            referenceId = "UTR998877665544",
            timestamp = candidate.candidate.transactionTimestamp
        )

        val result = TransactionDeduplicator.evaluate(candidate, listOf(existingRecord))
        assertEquals(DedupDecision.CORRELATED, result.decision)
        assertEquals(MatchStrategy.EXPLICIT_REFERENCE_ID, result.strategy)
    }

    // 10. Explicit UTR conflict
    @Test
    fun test10_explicitUtrConflict() {
        val candidate = makeCandidate(amount = 500.0, merchant = "Merchant A", utr = "UTR_CONFLICT_123")
        val existingRecord = TransactionRecord(
            id = "exist_conflict",
            amount = 1500.0, // Differing amount!
            merchant = "Merchant A",
            direction = TransactionDirection.DEBIT,
            utr = "UTR_CONFLICT_123",
            referenceId = "UTR_CONFLICT_123",
            timestamp = candidate.candidate.transactionTimestamp
        )

        val result = TransactionDeduplicator.evaluate(candidate, listOf(existingRecord))
        assertEquals(DedupDecision.NEEDS_REVIEW, result.decision)
        assertEquals(MatchStrategy.IDENTITY_CONFLICT, result.strategy)
    }

    // 11. Same amount but different merchant
    @Test
    fun test11_sameAmountDifferentMerchant() {
        val candidate = makeCandidate(amount = 500.0, merchant = "Swiggy", utr = null)
        val existingRecord = TransactionRecord(
            id = "exist_zomato",
            amount = 500.0,
            merchant = "Zomato",
            direction = TransactionDirection.DEBIT,
            timestamp = candidate.candidate.transactionTimestamp
        )

        val result = TransactionDeduplicator.evaluate(candidate, listOf(existingRecord))
        assertEquals(DedupDecision.NEW_TRANSACTION, result.decision)
    }

    // 12. Same merchant but different amount
    @Test
    fun test12_sameMerchantDifferentAmount() {
        val candidate = makeCandidate(amount = 500.0, merchant = "Amazon", utr = null)
        val existingRecord = TransactionRecord(
            id = "exist_amazon_250",
            amount = 250.0,
            merchant = "Amazon",
            direction = TransactionDirection.DEBIT,
            timestamp = candidate.candidate.transactionTimestamp
        )

        val result = TransactionDeduplicator.evaluate(candidate, listOf(existingRecord))
        assertEquals(DedupDecision.NEW_TRANSACTION, result.decision)
    }

    // 13. Same amount/merchant/direction but different transaction time
    @Test
    fun test13_sameAmountMerchantDirection_differentTransactionTime() {
        val baseTime = 1758776400000L // 10:30 AM
        val candidate = makeCandidate(
            amount = 500.0,
            merchant = "Amazon",
            direction = TransactionDirection.DEBIT,
            txnTime = baseTime + (6 * 3600 * 1000L) // 16:30 (6 hours later)
        )
        val existingRecord = TransactionRecord(
            id = "exist_morning",
            amount = 500.0,
            merchant = "Amazon",
            direction = TransactionDirection.DEBIT,
            timestamp = baseTime
        )

        val result = TransactionDeduplicator.evaluate(candidate, listOf(existingRecord))
        assertEquals("Transactions separated by hours must not be merged as duplicates",
            DedupDecision.NEW_TRANSACTION, result.decision)
    }

    // 14. Same transaction time but different transaction ID
    @Test
    fun test14_sameTransactionTime_differentTransactionId() {
        val time = 1758776400000L
        val candidate = makeCandidate(
            amount = 500.0,
            merchant = "Amazon",
            utr = "UTR_ALPHA_111",
            txnTime = time
        )
        val existingRecord = TransactionRecord(
            id = "exist_beta",
            amount = 500.0,
            merchant = "Amazon",
            direction = TransactionDirection.DEBIT,
            utr = "UTR_BETA_222",
            referenceId = "UTR_BETA_222",
            timestamp = time
        )

        val result = TransactionDeduplicator.evaluate(candidate, listOf(existingRecord))
        assertEquals("Distinct reference IDs mean different transactions",
            DedupDecision.NEW_TRANSACTION, result.decision)
    }

    // 15. Notification post time differs from transaction time
    @Test
    fun test15_notificationPostTimeDiffersFromTransactionTime() {
        val postTime = 1758776700000L // 10:35 AM
        val text = "Paid ₹500 to Amazon at 10:30 AM"

        val pair = StructuredTransactionExtractor.extractTransactionTimestamp(text, postTime)
        assertNotNull(pair)
        val (txnTimestamp, source) = pair!!
        assertEquals(TimestampSource.CONTENT, source)

        val cal = Calendar.getInstance().apply { timeInMillis = txnTimestamp }
        assertEquals(10, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
        assertNotEquals(postTime, txnTimestamp)
    }

    // 16. Missing transaction timestamp falls back to notification postTime
    @Test
    fun test16_missingTransactionTimestamp_fallsBackToNotificationPostTime() {
        val postTime = 1758776700000L
        val text = "Paid ₹500 to Amazon"

        val pair = StructuredTransactionExtractor.extractTransactionTimestamp(text, postTime)
        assertNull("No time in text should return null for content timestamp", pair)

        val captured = CapturedNotificationInfo(
            packageName = "com.amazon",
            notificationKey = "key_fallback",
            postTime = postTime,
            title = "Amazon",
            text = text,
            category = "payment"
        )
        val normalized = NotificationNormalizer.normalize(captured)!!
        val classification = FinancialClassifier.classify(normalized)
        val candidate = StructuredTransactionExtractor.extractAll(classification).first()

        assertEquals(postTime, candidate.transactionTimestamp)
        assertEquals(TimestampSource.NOTIFICATION_POST_TIME, candidate.transactionTimestampSource)
    }

    // 17. Multiple candidates retain independent timestamps
    @Test
    fun test17_multipleCandidatesRetainIndependentTimestamps() {
        val text = "10:30 Paid ₹500 to Amazon.\n10:32 Paid ₹250 to Flipkart."
        val postTime = 1758776700000L // 10:35 AM

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "multi_time_key",
            postTime = postTime,
            title = "Google Pay",
            text = text,
            category = "payment"
        )
        val normalized = NotificationNormalizer.normalize(captured)!!
        val classification = FinancialClassifier.classify(normalized)
        val candidates = StructuredTransactionExtractor.extractAll(classification)

        assertEquals(2, candidates.size)
        val c0 = candidates[0]
        val c1 = candidates[1]

        val cal0 = Calendar.getInstance().apply { timeInMillis = c0.transactionTimestamp }
        val cal1 = Calendar.getInstance().apply { timeInMillis = c1.transactionTimestamp }

        assertEquals(10, cal0.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal0.get(Calendar.MINUTE))

        assertEquals(10, cal1.get(Calendar.HOUR_OF_DAY))
        assertEquals(32, cal1.get(Calendar.MINUTE))

        assertNotEquals(c0.transactionTimestamp, c1.transactionTimestamp)
    }

    // 18. Two identical-looking transactions are not blindly merged
    @Test
    fun test18_twoIdenticalLookingTransactions_notBlindlyMerged() {
        val baseTime = 1758776400000L // 10:30 AM
        val existingRecord = TransactionRecord(
            id = "exist_amazon",
            amount = 500.0,
            merchant = "Amazon",
            direction = TransactionDirection.DEBIT,
            timestamp = baseTime,
            timestampSource = TimestampSource.CONTENT
        )

        // Candidate arrives 60s later with explicit content time and no card/UPI
        val candidate = makeCandidate(
            amount = 500.0,
            merchant = "Amazon",
            direction = TransactionDirection.DEBIT,
            utr = null,
            accountSuffix = null,
            txnTime = baseTime + 60_000L, // 10:31 AM
            txnSource = TimestampSource.CONTENT
        )

        val result = TransactionDeduplicator.evaluate(candidate, listOf(existingRecord))
        assertEquals("Identical looking transactions with differing explicit times and no instrument must route to review",
            DedupDecision.NEEDS_REVIEW, result.decision)
    }

    // 19. Manual transaction remains protected
    @Test
    fun test19_manualTransactionRemainsProtected() = runBlocking {
        val manualExpense = Expense(
            id = 42,
            amount = 500.0,
            merchant = "Amazon",
            dateMillis = 1758776400000L,
            type = "Debit",
            note = "My personal manual Amazon purchase",
            source = "MANUAL"
        )
        dao.insert(manualExpense)

        // Notification arrives for ₹500 Amazon without reference ID
        val notifCandidate = makeCandidate(
            amount = 500.0,
            merchant = "Amazon",
            utr = null,
            key = "notif_manual_protect",
            txnTime = 1758776400000L
        )

        val result = TransactionPersistenceManager.processValidatedCandidate(notifCandidate, dao)
        assertTrue("Automatic candidate must NOT overwrite manual transaction",
            result is ExpensePersistenceResult.Inserted)

        // Both manual and new automatic expense should exist
        assertEquals(2, dao.getCount())
        val manualInDb = dao.getExpenseById(42)
        assertNotNull(manualInDb)
        assertEquals("Manual note must remain untouched", "My personal manual Amazon purchase", manualInDb?.note)
        assertEquals("Source must remain MANUAL", "MANUAL", manualInDb?.source)
    }

    // 20. Existing bounded DB query behavior remains intact
    @Test
    fun test20_boundedDbQueryBehaviorIntact() = runBlocking {
        val now = 1758776400000L
        val fiveDaysAgo = now - (5 * 24 * 3600 * 1000L)

        // Old expense outside 48h window
        val oldExpense = Expense(
            id = 99,
            amount = 500.0,
            merchant = "Old Amazon",
            dateMillis = fiveDaysAgo,
            type = "Debit"
        )
        dao.insert(oldExpense)

        val candidate = makeCandidate(amount = 500.0, merchant = "Amazon", txnTime = now)
        val targeted = TransactionPersistenceManager.findTargetedCandidates(candidate, dao)

        assertTrue("Old expense outside 48h correlation window must not be loaded",
            targeted.none { it.id == 99 })
    }
}
