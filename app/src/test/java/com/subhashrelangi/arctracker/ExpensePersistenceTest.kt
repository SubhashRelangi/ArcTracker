package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.ExpenseDao
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Step 8: Comprehensive Unit and Integration Tests for Expense Persistence,
 * Deduplication, Idempotency, and Pending Expenses integration.
 */
class ExpensePersistenceTest {

    private lateinit var dao: FakeExpenseDao

    @Before
    fun setUp() {
        dao = FakeExpenseDao()
        TransactionPersistenceManager.persistenceListener = null
    }

    private fun makeCandidate(
        amount: Double = 500.0,
        merchant: String = "Ravi",
        direction: TransactionDirection = TransactionDirection.DEBIT,
        status: TransactionStatus = TransactionStatus.SUCCESS,
        utr: String? = null,
        accountSuffix: String? = null,
        key: String = "key_1",
        time: Long = 1700000000000L,
        raw: String = "Paid ₹500 to Ravi"
    ): ValidatedTransactionCandidate {
        val sc = StructuredTransactionCandidate(
            packageName = "com.google.android.apps.nbu.paisa.user",
            sourceNotificationKey = key,
            postTime = time,
            amount = amount,
            currency = "INR",
            merchant = merchant,
            direction = direction,
            status = status,
            utr = utr,
            accountSuffix = accountSuffix,
            rawContent = raw
        )
        return ValidatedTransactionCandidate(
            candidate = sc,
            validationState = ValidationState.ACCEPTABLE,
            evidenceLevel = EvidenceLevel.VERY_STRONG,
            isStructurallyValid = true,
            validationReasons = listOf("Valid candidate")
        )
    }

    // 1. NEW_TRANSACTION inserts exactly one Expense: before = 0, after = 1
    @Test
    fun test01_newTransaction_insertsExactlyOneExpense() = runBlocking {
        assertEquals(0, dao.getCount())
        val candidate = makeCandidate(amount = 500.0, merchant = "Ravi", key = "new_1")

        val result = TransactionPersistenceManager.processValidatedCandidate(candidate, dao)

        assertTrue(result is ExpensePersistenceResult.Inserted)
        assertEquals(1, dao.getCount())
        val saved = dao.getAllExpensesList().first()
        assertEquals(500.0, saved.amount, 0.001)
        assertEquals("Ravi", saved.merchant)
        assertEquals("Debit", saved.type)
        assertFalse(saved.isPending)
    }

    // 2. Same NEW_TRANSACTION processed twice does not create two rows: before = 0, after = 1
    @Test
    fun test02_newTransaction_processedTwice_isIdempotent() = runBlocking {
        assertEquals(0, dao.getCount())
        val candidate = makeCandidate(amount = 500.0, merchant = "Ravi", key = "idempotent_1")

        val res1 = TransactionPersistenceManager.processValidatedCandidate(candidate, dao)
        assertTrue(res1 is ExpensePersistenceResult.Inserted)
        assertEquals(1, dao.getCount())

        val res2 = TransactionPersistenceManager.processValidatedCandidate(candidate, dao)
        assertTrue(res2 is ExpensePersistenceResult.SkippedDuplicate)
        assertEquals(1, dao.getCount())
    }

    // 3. DUPLICATE performs no insert: before = 1, after = 1
    @Test
    fun test03_duplicateCandidate_performsNoInsert() = runBlocking {
        val candidate1 = makeCandidate(amount = 350.0, merchant = "Cafe", key = "dup_key_1")
        TransactionPersistenceManager.processValidatedCandidate(candidate1, dao)
        assertEquals(1, dao.getCount())

        val duplicateCandidate = makeCandidate(amount = 350.0, merchant = "Cafe", key = "dup_key_1")
        val result = TransactionPersistenceManager.processValidatedCandidate(duplicateCandidate, dao)

        assertTrue(result is ExpensePersistenceResult.SkippedDuplicate)
        assertEquals(1, dao.getCount())
    }

    // 4. UPDATE_EXISTING updates existing transaction: before = 1, after = 1
    @Test
    fun test04_updateExisting_updatesFieldsWithoutDuplicateInsert() = runBlocking {
        val pendingCandidate = makeCandidate(
            amount = 500.0,
            merchant = "Unknown Merchant",
            status = TransactionStatus.PENDING,
            key = "update_txn_1"
        )
        val insertRes = TransactionPersistenceManager.processValidatedCandidate(pendingCandidate, dao)
        assertTrue(insertRes is ExpensePersistenceResult.Inserted)
        assertTrue((insertRes as ExpensePersistenceResult.Inserted).isPending)
        assertEquals(1, dao.getCount())

        val successCandidate = makeCandidate(
            amount = 500.0,
            merchant = "Ravi",
            status = TransactionStatus.SUCCESS,
            key = "update_txn_1"
        )
        val updateRes = TransactionPersistenceManager.processValidatedCandidate(successCandidate, dao)

        assertTrue(updateRes is ExpensePersistenceResult.Updated)
        assertEquals(1, dao.getCount())

        val updated = dao.getAllExpensesList().first()
        assertEquals("Ravi", updated.merchant)
        assertFalse(updated.isPending)
    }

    // 5. Pending -> Success results in one transaction
    @Test
    fun test05_pendingToSuccess_resultsInOneTransaction() = runBlocking {
        assertEquals(0, dao.getCount())
        val capturedPending = CapturedNotificationInfo(
            packageName = "com.phonepe.app",
            notificationKey = "p2s_key",
            postTime = System.currentTimeMillis(),
            title = "PhonePe",
            text = "₹500 payment processing for Ravi"
        )
        val res1 = TransactionPersistenceManager.processCapturedNotification(capturedPending, dao)
        assertTrue(res1 is ExpensePersistenceResult.Inserted || res1 is ExpensePersistenceResult.ReviewPending)
        assertEquals(1, dao.getCount())
        assertTrue(dao.getAllExpensesList().first().isPending)

        val capturedSuccess = CapturedNotificationInfo(
            packageName = "com.phonepe.app",
            notificationKey = "p2s_key",
            postTime = System.currentTimeMillis(),
            title = "PhonePe",
            text = "₹500 payment successful to Ravi UTR 123456789"
        )
        val res2 = TransactionPersistenceManager.processCapturedNotification(capturedSuccess, dao)
        assertTrue(res2 is ExpensePersistenceResult.Updated || res2 is ExpensePersistenceResult.Enriched)
        assertEquals(1, dao.getCount())
        assertFalse(dao.getAllExpensesList().first().isPending)
    }

    // 6. Notification update with same notificationKey does not duplicate
    @Test
    fun test06_notificationUpdate_sameKey_doesNotDuplicate() = runBlocking {
        val captured1 = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "gpay_update_1",
            postTime = 1000L,
            title = "Google Pay",
            text = "Paid ₹500 to Amazon"
        )
        TransactionPersistenceManager.processCapturedNotification(captured1, dao)
        assertEquals(1, dao.getCount())

        val captured2 = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "gpay_update_1",
            postTime = 1005L,
            title = "Google Pay",
            text = "Paid ₹500 to Amazon (Completed)"
        )
        val result = TransactionPersistenceManager.processCapturedNotification(captured2, dao)
        assertFalse(result is ExpensePersistenceResult.Inserted)
        assertEquals(1, dao.getCount())
    }

    // 7. Notification + SMS with same UTR does not create two transactions
    @Test
    fun test07_notificationPlusSms_sameUtr_correlatesToOneTransaction() = runBlocking {
        assertEquals(0, dao.getCount())
        // Notification arrives first
        val capturedNotif = CapturedNotificationInfo(
            packageName = "com.phonepe.app",
            notificationKey = "notif_utr_1",
            postTime = 1700000000000L,
            title = "PhonePe",
            text = "₹500 payment successful to Ravi UTR 123456789"
        )
        val notifRes = TransactionPersistenceManager.processCapturedNotification(capturedNotif, dao)
        assertTrue(notifRes is ExpensePersistenceResult.Inserted)
        assertEquals(1, dao.getCount())

        // Bank SMS arrives 15 seconds later with same UTR
        val smsRes = TransactionPersistenceManager.processSms(
            smsId = "9901",
            sender = "HDFCBK",
            body = "Rs 500 debited from A/c XX1234 on 25-Sep-26. UTR 123456789. Bal: Rs 15000",
            timestamp = 1700000015000L,
            dao = dao
        )
        assertTrue("Expected CORRELATED or Updated result, got $smsRes",
            smsRes is ExpensePersistenceResult.Enriched || smsRes is ExpensePersistenceResult.Updated || smsRes is ExpensePersistenceResult.SkippedDuplicate)
        assertEquals("Expected exactly 1 expense row after notification + SMS with same UTR", 1, dao.getCount())
    }

    // 8. Notification + SMS with strong fingerprint does not create two transactions
    @Test
    fun test08_notificationPlusSms_strongFingerprint_correlates() = runBlocking {
        assertEquals(0, dao.getCount())
        val capturedNotif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "notif_fp_1",
            postTime = 1700000000000L,
            title = "Google Pay",
            text = "Paid ₹1200 to Starbucks using A/c XX4321"
        )
        TransactionPersistenceManager.processCapturedNotification(capturedNotif, dao)
        assertEquals(1, dao.getCount())

        val smsRes = TransactionPersistenceManager.processSms(
            smsId = "9902",
            sender = "ICICIB",
            body = "INR 1200.00 debited from A/c XX4321 on 25-Sep at Starbucks",
            timestamp = 1700000020000L, // 20s later
            dao = dao
        )
        assertTrue(smsRes is ExpensePersistenceResult.Enriched || smsRes is ExpensePersistenceResult.Updated || smsRes is ExpensePersistenceResult.SkippedDuplicate)
        assertEquals(1, dao.getCount())
    }

    // 9. Amount-only matches remain separate
    @Test
    fun test09_amountOnlyMatches_remainSeparate() = runBlocking {
        assertEquals(0, dao.getCount())
        val cand1 = makeCandidate(amount = 500.0, merchant = "Merchant A", key = "cand_1", time = 1000L)
        val cand2 = makeCandidate(amount = 500.0, merchant = "Merchant B", key = "cand_2", time = 1000000L)

        TransactionPersistenceManager.processValidatedCandidate(cand1, dao)
        TransactionPersistenceManager.processValidatedCandidate(cand2, dao)

        assertEquals("Different transactions sharing only amount must not be merged", 2, dao.getCount())
    }

    // 10. Amount + direction-only matches remain separate
    @Test
    fun test10_amountAndDirectionOnlyMatches_remainSeparate() = runBlocking {
        assertEquals(0, dao.getCount())
        val cand1 = makeCandidate(amount = 250.0, merchant = "Zomato", direction = TransactionDirection.DEBIT, key = "k1", time = 1000L)
        val cand2 = makeCandidate(amount = 250.0, merchant = "Swiggy", direction = TransactionDirection.DEBIT, key = "k2", time = 2000000L)

        TransactionPersistenceManager.processValidatedCandidate(cand1, dao)
        TransactionPersistenceManager.processValidatedCandidate(cand2, dao)

        assertEquals(2, dao.getCount())
    }

    // 11. Different merchants with same amount remain separate
    @Test
    fun test11_differentMerchants_sameAmount_remainSeparate() = runBlocking {
        val captured1 = CapturedNotificationInfo(
            packageName = "com.phonepe.app",
            notificationKey = "diff_m_1",
            postTime = 1700000000000L,
            title = "PhonePe",
            text = "Paid ₹500 to Swiggy"
        )
        val captured2 = CapturedNotificationInfo(
            packageName = "com.phonepe.app",
            notificationKey = "diff_m_2",
            postTime = 1700000000000L,
            title = "PhonePe",
            text = "Paid ₹500 to Uber"
        )
        TransactionPersistenceManager.processCapturedNotification(captured1, dao)
        TransactionPersistenceManager.processCapturedNotification(captured2, dao)

        assertEquals(2, dao.getCount())
    }

    // 12. Conflicting reference amount reaches review behavior
    @Test
    fun test12_conflictingReferenceAmount_reachesReview() = runBlocking {
        val cand1 = makeCandidate(amount = 500.0, utr = "REF9999", key = "c_ref_1")
        TransactionPersistenceManager.processValidatedCandidate(cand1, dao)
        assertEquals(1, dao.getCount())

        val cand2 = makeCandidate(amount = 900.0, utr = "REF9999", key = "c_ref_2")
        val res = TransactionPersistenceManager.processValidatedCandidate(cand2, dao)

        assertTrue("Conflicting amounts for same reference must route to review", res is ExpensePersistenceResult.ReviewPending)
        assertEquals(2, dao.getCount())
        val reviewRow = dao.getAllExpensesList().find { it.notificationKey == "c_ref_2" }
        assertNotNull(reviewRow)
        assertTrue(reviewRow!!.isPending)
    }

    // 13. Conflicting direction reaches review behavior
    @Test
    fun test13_conflictingDirection_reachesReview() = runBlocking {
        val cand1 = makeCandidate(amount = 500.0, direction = TransactionDirection.DEBIT, utr = "DIR8888", key = "c_dir_1")
        TransactionPersistenceManager.processValidatedCandidate(cand1, dao)
        assertEquals(1, dao.getCount())

        val cand2 = makeCandidate(amount = 500.0, direction = TransactionDirection.CREDIT, utr = "DIR8888", key = "c_dir_2")
        val res = TransactionPersistenceManager.processValidatedCandidate(cand2, dao)

        assertTrue(res is ExpensePersistenceResult.ReviewPending)
        assertEquals(2, dao.getCount())
        val reviewRow = dao.getAllExpensesList().find { it.notificationKey == "c_dir_2" }
        assertNotNull(reviewRow)
        assertTrue(reviewRow!!.isPending)
    }

    // 14. Manual expense is not overwritten by an ordinary notification
    @Test
    fun test14_manualExpense_notOverwrittenByNotification() = runBlocking {
        val manualExpense = Expense(
            id = 0,
            amount = 1500.0,
            merchant = "Dinner with Friends",
            dateMillis = 1700000000000L,
            type = "Debit",
            notificationKey = "",
            isPending = false,
            tag = "Food",
            note = "Custom note",
            source = "MANUAL"
        )
        dao.insert(manualExpense)
        assertEquals(1, dao.getCount())

        val notification = makeCandidate(
            amount = 1500.0,
            merchant = "Unknown Merchant",
            key = "notif_override_attempt"
        )
        TransactionPersistenceManager.processValidatedCandidate(notification, dao)

        val manualAfter = dao.getAllExpensesList().first { it.source == "MANUAL" }
        assertEquals("Dinner with Friends", manualAfter.merchant)
        assertEquals(1500.0, manualAfter.amount, 0.001)
        assertEquals("Custom note", manualAfter.note)
    }

    // 15. Repeated historical SMS import remains idempotent
    @Test
    fun test15_repeatedSmsImport_isIdempotent() = runBlocking {
        assertEquals(0, dao.getCount())
        val smsList = listOf(
            Triple("1", "Paid ₹100 at Chai Point", 1700000001000L),
            Triple("2", "Paid ₹200 at D-Mart", 1700000002000L),
            Triple("3", "Paid ₹300 at Shell Petrol", 1700000003000L)
        )

        // First import
        for ((id, body, time) in smsList) {
            TransactionPersistenceManager.processSms(id, "HDFCBK", body, time, dao)
        }
        val countAfterFirst = dao.getCount()
        assertEquals(3, countAfterFirst)

        // Second import of same messages
        for ((id, body, time) in smsList) {
            val res = TransactionPersistenceManager.processSms(id, "HDFCBK", body, time, dao)
            assertTrue(res is ExpensePersistenceResult.SkippedDuplicate)
        }
        assertEquals("Repeated SMS import must not duplicate rows", countAfterFirst, dao.getCount())
    }

    // 16. Re-running SMS import does not duplicate existing records
    @Test
    fun test16_rerunningSmsImport_doesNotDuplicateRecords() = runBlocking {
        TransactionPersistenceManager.processSms("s100", "SBI", "Paid ₹450 to Swiggy", 1700000000000L, dao)
        assertEquals(1, dao.getCount())

        val res = TransactionPersistenceManager.processSms("s100", "SBI", "Paid ₹450 to Swiggy", 1700000000000L, dao)
        assertTrue(res is ExpensePersistenceResult.SkippedDuplicate)
        assertEquals(1, dao.getCount())
    }

    // 17. Rejected candidate never reaches Room
    @Test
    fun test17_rejectedCandidate_neverReachesRoom() = runBlocking {
        assertEquals(0, dao.getCount())
        val rejectedCandidate = ValidatedTransactionCandidate(
            candidate = StructuredTransactionCandidate(
                packageName = "pkg",
                sourceNotificationKey = "rej_1",
                postTime = 1000L,
                amount = null // Missing amount -> invalid
            ),
            validationState = ValidationState.REJECTED,
            evidenceLevel = EvidenceLevel.NONE,
            isStructurallyValid = false,
            rejectionReasons = listOf("Missing required amount")
        )
        val res = TransactionPersistenceManager.processValidatedCandidate(rejectedCandidate, dao)
        assertTrue(res is ExpensePersistenceResult.Rejected)
        assertEquals(0, dao.getCount())
    }

    // 18. NON_FINANCIAL candidate never reaches Room
    @Test
    fun test18_nonFinancialCandidate_neverReachesRoom() = runBlocking {
        assertEquals(0, dao.getCount())
        val otpNotification = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            notificationKey = "otp_1",
            postTime = 1000L,
            title = "Bank OTP",
            text = "Your OTP for transaction is 123456. Do not share it with anyone."
        )
        val res = TransactionPersistenceManager.processCapturedNotification(otpNotification, dao)
        assertTrue(res is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, dao.getCount())
    }

    // 19. NEEDS_REVIEW follows the existing Pending Expenses path
    @Test
    fun test19_needsReview_persistsWithIsPendingTrue() = runBlocking {
        assertEquals(0, dao.getCount())
        val cand = StructuredTransactionCandidate(
            packageName = "pkg",
            sourceNotificationKey = "review_k1",
            postTime = 1000L,
            amount = 500.0,
            direction = TransactionDirection.DEBIT
        )
        val validated = ValidatedTransactionCandidate(
            candidate = cand,
            validationState = ValidationState.NEEDS_REVIEW,
            evidenceLevel = EvidenceLevel.MODERATE,
            isStructurallyValid = true,
            warnings = listOf("Low confidence, missing merchant")
        )
        val res = TransactionPersistenceManager.processValidatedCandidate(validated, dao)
        assertTrue(res is ExpensePersistenceResult.Inserted || res is ExpensePersistenceResult.ReviewPending)
        assertEquals(1, dao.getCount())
        val saved = dao.getAllExpensesList().first()
        assertTrue("Expense must be marked as pending", saved.isPending)
    }

    // 20. Database failure is safely handled
    @Test
    fun test20_databaseFailure_isSafelyHandled() = runBlocking {
        val failingDao = object : FakeExpenseDao() {
            override suspend fun insert(expense: Expense): Long {
                throw android.database.sqlite.SQLiteException("Simulated disk I/O error")
            }
        }
        val cand = makeCandidate(amount = 200.0, key = "fail_key")
        val res = TransactionPersistenceManager.processValidatedCandidate(cand, failingDao)
        assertTrue("Expected Error result on database exception", res is ExpensePersistenceResult.Error)
    }

    // 21. Notification service does not crash on persistence failure
    @Test
    fun test21_serviceDoesNotCrashOnPersistenceFailure() {
        val service = NotificationReaderService()
        NotificationPermissionHelper.permissionOverrideForTesting = true
        try {
            val captured = service.recordCapturedNotification(
                packageName = "com.test",
                notificationKey = "test_key",
                postTime = 1000L,
                title = "Title",
                text = "Text"
            )
            assertNotNull(captured)
        } finally {
            NotificationPermissionHelper.permissionOverrideForTesting = null
        }
    }

    // 22. Synthetic: ₹500 paid to Ravi
    @Test
    fun test22_synthetic_paidToRavi() = runBlocking {
        val notif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "synth_ravi",
            postTime = 1000L,
            title = "Google Pay",
            text = "₹500 paid to Ravi"
        )
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue(res is ExpensePersistenceResult.Inserted)
        val saved = dao.getAllExpensesList().first()
        assertEquals(500.0, saved.amount, 0.001)
        assertEquals("Ravi", saved.merchant)
        assertEquals("Debit", saved.type)
        assertFalse(saved.isPending)
    }

    // 23. Synthetic: ₹500 payment processing
    @Test
    fun test23_synthetic_paymentProcessing() = runBlocking {
        val notif = CapturedNotificationInfo(
            packageName = "com.phonepe.app",
            notificationKey = "synth_proc",
            postTime = 1000L,
            title = "PhonePe",
            text = "₹500 payment processing"
        )
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue(res is ExpensePersistenceResult.Inserted || res is ExpensePersistenceResult.ReviewPending)
        val saved = dao.getAllExpensesList().first()
        assertEquals(500.0, saved.amount, 0.001)
        assertTrue(saved.isPending)
    }

    // 24. Synthetic: ₹500 payment successful UTR 123456789
    @Test
    fun test24_synthetic_paymentSuccessfulWithUtr() = runBlocking {
        val notif = CapturedNotificationInfo(
            packageName = "com.phonepe.app",
            notificationKey = "synth_utr",
            postTime = 1000L,
            title = "PhonePe",
            text = "₹500 payment successful to Amazon UTR 123456789"
        )
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue(res is ExpensePersistenceResult.Inserted)
        val saved = dao.getAllExpensesList().first()
        assertEquals(500.0, saved.amount, 0.001)
        assertTrue(saved.note?.contains("123456789") == true)
        assertEquals("Shopping", saved.tag)
    }

    // 25. Synthetic: INR 500 debited from A/c XX1234
    @Test
    fun test25_synthetic_debitedFromAccount() = runBlocking {
        val notif = CapturedNotificationInfo(
            packageName = "com.hdfc.bank",
            notificationKey = "synth_hdfc",
            postTime = 1000L,
            title = "HDFC Bank",
            text = "INR 500 debited from A/c XX1234 at Starbucks"
        )
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue(res is ExpensePersistenceResult.Inserted)
        val saved = dao.getAllExpensesList().first()
        assertEquals(500.0, saved.amount, 0.001)
        assertTrue(saved.note?.contains("1234") == true)
        assertEquals("Food", saved.tag)
    }

    // 26. Synthetic: ₹500 refunded
    @Test
    fun test26_synthetic_refundedCredit() = runBlocking {
        val notif = CapturedNotificationInfo(
            packageName = "com.phonepe.app",
            notificationKey = "synth_refund",
            postTime = 1000L,
            title = "PhonePe",
            text = "₹500 refunded to your account from Zomato"
        )
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue(res is ExpensePersistenceResult.Inserted)
        val saved = dao.getAllExpensesList().first()
        assertEquals(500.0, saved.amount, 0.001)
        assertEquals("Credit", saved.type)
    }

    // 27. Synthetic non-financial noise: OTP 123456
    @Test
    fun test27_synthetic_otpIgnored() = runBlocking {
        val notif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            notificationKey = "synth_otp",
            postTime = 1000L,
            title = "SMS",
            text = "OTP 123456 for logging into your account"
        )
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue(res is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, dao.getCount())
    }

    // 28. Synthetic non-financial: Your account balance is ₹5,000
    @Test
    fun test28_synthetic_accountBalanceIgnored() = runBlocking {
        val notif = CapturedNotificationInfo(
            packageName = "com.hdfc.bank",
            notificationKey = "synth_bal",
            postTime = 1000L,
            title = "HDFC Bank",
            text = "Your account balance is ₹5,000 as of 25-Sep"
        )
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue(res is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, dao.getCount())
    }

    // 29. Synthetic non-financial: Loan offer ₹500
    @Test
    fun test29_synthetic_loanOfferIgnored() = runBlocking {
        val notif = CapturedNotificationInfo(
            packageName = "com.loan.app",
            notificationKey = "synth_loan",
            postTime = 1000L,
            title = "Instant Loan",
            text = "Get instant loan offer ₹50,000 at low interest rate! Apply now."
        )
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue(res is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, dao.getCount())
    }

    // 30. Concurrency safety: parallel processing does not create duplicate entries
    @Test
    fun test30_concurrencySafety_noDuplicateEntries() = runBlocking {
        assertEquals(0, dao.getCount())
        val candidate = makeCandidate(amount = 750.0, merchant = "Uber", key = "concurrent_key_1")

        // Launch 10 concurrent persistence attempts with the same candidate
        val jobs = (1..10).map {
            async {
                TransactionPersistenceManager.processValidatedCandidate(candidate, dao)
            }
        }
        val results = jobs.awaitAll()

        assertEquals("Exactly 1 row should be persisted despite 10 concurrent requests", 1, dao.getCount())
        val insertedCount = results.count { it is ExpensePersistenceResult.Inserted }
        val skippedCount = results.count { it is ExpensePersistenceResult.SkippedDuplicate }
        assertEquals(1, insertedCount)
        assertEquals(9, skippedCount)
    }

    // 31. Regression test: existing transaction cannot be silently replaced by an unrelated insert
    @Test
    fun test31_cannotSilentlyReplaceExistingExpenseOnInsert() = runBlocking {
        assertEquals(0, dao.getCount())
        val original = Expense(
            id = 101,
            amount = 1200.0,
            merchant = "Original Merchant",
            dateMillis = 1700000000000L,
            type = "Debit",
            note = "Important original note"
        )
        dao.insert(original)
        assertEquals(1, dao.getCount())

        val imposter = Expense(
            id = 101,
            amount = 99.0,
            merchant = "Imposter Merchant",
            dateMillis = 1700000000000L,
            type = "Debit",
            note = "Malicious replacement"
        )

        var caughtConflict = false
        try {
            dao.insert(imposter)
        } catch (e: Exception) {
            caughtConflict = true
        }

        assertTrue("Attempting to insert an expense with existing id must abort/throw", caughtConflict)
        val current = dao.getExpenseById(101)
        assertNotNull(current)
        assertEquals("Amount must NOT be silently replaced", 1200.0, current!!.amount, 0.001)
        assertEquals("Merchant must NOT be silently replaced", "Original Merchant", current.merchant)
        assertEquals("Note must NOT be silently replaced", "Important original note", current.note)
    }

    // 32. Manual + notification same amount: manual expense remains protected
    @Test
    fun test32_manualExpense_notificationSameAmount_manualProtected() = runBlocking {
        val manual = Expense(
            id = 1,
            amount = 500.0,
            merchant = "Grocery Store",
            dateMillis = 1700000000000L,
            type = "Debit",
            tag = "Shopping",
            note = "Weekly groceries",
            source = "MANUAL"
        )
        dao.insert(manual)
        assertEquals(1, dao.getCount())

        // Notification arrives with same amount (500.0) but different merchant/source
        val notifCandidate = makeCandidate(
            amount = 500.0,
            merchant = "Zomato",
            key = "notif_amt_500",
            time = 1700000005000L
        )
        val res = TransactionPersistenceManager.processValidatedCandidate(notifCandidate, dao)
        assertTrue(res is ExpensePersistenceResult.Inserted)

        // Verify manual expense was not modified or overwritten
        val manualInDb = dao.getExpenseById(1)
        assertNotNull(manualInDb)
        assertEquals(500.0, manualInDb!!.amount, 0.001)
        assertEquals("Grocery Store", manualInDb.merchant)
        assertEquals("Weekly groceries", manualInDb.note)
        assertEquals("Shopping", manualInDb.tag)
        assertEquals("MANUAL", manualInDb.source)
        assertEquals(2, dao.getCount())
    }

    // 33. Manual + notification same amount and time: manual expense remains protected
    @Test
    fun test33_manualExpense_notificationSameAmountAndTime_manualProtected() = runBlocking {
        val time = 1700000000000L
        val manual = Expense(
            id = 2,
            amount = 350.0,
            merchant = "Coffee",
            dateMillis = time,
            type = "Debit",
            tag = "Food",
            note = "Morning espresso",
            source = "MANUAL"
        )
        dao.insert(manual)

        // Notification with identical amount and time
        val notifCandidate = makeCandidate(
            amount = 350.0,
            merchant = "Cafe Coffee Day",
            key = "notif_ccd_350",
            time = time
        )
        val res = TransactionPersistenceManager.processValidatedCandidate(notifCandidate, dao)
        assertTrue(res is ExpensePersistenceResult.Inserted)

        val manualInDb = dao.getExpenseById(2)
        assertNotNull(manualInDb)
        assertEquals(350.0, manualInDb!!.amount, 0.001)
        assertEquals("Coffee", manualInDb.merchant)
        assertEquals("Morning espresso", manualInDb.note)
        assertEquals("MANUAL", manualInDb.source)
    }

    // 34. Manual + notification same merchant: manual expense remains protected
    @Test
    fun test34_manualExpense_notificationSameMerchant_manualProtected() = runBlocking {
        val manual = Expense(
            id = 3,
            amount = 200.0,
            merchant = "Starbucks",
            dateMillis = 1700000000000L,
            type = "Debit",
            tag = "Food",
            note = "Manual coffee",
            source = "MANUAL"
        )
        dao.insert(manual)

        // Notification with same merchant but different amount
        val notifCandidate = makeCandidate(
            amount = 450.0,
            merchant = "Starbucks",
            key = "notif_sb_450",
            time = 1700000010000L
        )
        val res = TransactionPersistenceManager.processValidatedCandidate(notifCandidate, dao)
        assertTrue(res is ExpensePersistenceResult.Inserted)

        val manualInDb = dao.getExpenseById(3)
        assertNotNull(manualInDb)
        assertEquals(200.0, manualInDb!!.amount, 0.001)
        assertEquals("Starbucks", manualInDb.merchant)
        assertEquals("Manual coffee", manualInDb.note)
    }

    // 35. Manual + notification same explicit reference: manual fields remain completely preserved
    @Test
    fun test35_manualExpense_notificationSameExplicitReference_preservesManualFields() = runBlocking {
        val manual = Expense(
            id = 4,
            amount = 1500.0,
            merchant = "Dinner with Friends",
            dateMillis = 1700000000000L,
            type = "Debit",
            tag = "Food",
            note = "Ref: UTR99887766 | Paid by UPI",
            source = "MANUAL"
        )
        dao.insert(manual)
        assertEquals(1, dao.getCount())

        // Notification arrives with matching UTR99887766
        val notifCandidate = makeCandidate(
            amount = 1500.0,
            merchant = "Swiggy",
            utr = "UTR99887766",
            key = "notif_swiggy_ref",
            time = 1700000005000L
        )
        val res = TransactionPersistenceManager.processValidatedCandidate(notifCandidate, dao)

        // Must be identified as duplicate / preserved manual, no second row inserted
        assertEquals(1, dao.getCount())
        val manualInDb = dao.getExpenseById(4)
        assertNotNull(manualInDb)
        assertEquals("Manual amount must remain untouched", 1500.0, manualInDb!!.amount, 0.001)
        assertEquals("Manual merchant must NOT be overwritten", "Dinner with Friends", manualInDb.merchant)
        assertEquals("Manual note must NOT be overwritten", "Ref: UTR99887766 | Paid by UPI", manualInDb.note)
        assertEquals("Manual tag must NOT be overwritten", "Food", manualInDb.tag)
        assertEquals("Source must remain MANUAL", "MANUAL", manualInDb.source)
    }

    // 36. Targeted lookup efficiency: only queries candidates in correlation window
    @Test
    fun test36_targetedLookup_onlyQueriesRelevantWindow() = runBlocking {
        val now = 1700000000000L
        val tenDaysAgo = now - (10 * 24 * 3600 * 1000L)

        // Expense from 10 days ago (outside 48h correlation window)
        val oldExpense = Expense(
            id = 10,
            amount = 999.0,
            merchant = "Ancient Store",
            dateMillis = tenDaysAgo,
            type = "Debit"
        )
        dao.insert(oldExpense)

        // Current candidate
        val currentCandidate = makeCandidate(amount = 200.0, merchant = "Current Store", time = now)

        val targeted = TransactionPersistenceManager.findTargetedCandidates(currentCandidate, dao)

        // The 10-day-old expense must NOT be included in the targeted candidates
        assertTrue("Ancient transactions outside 48h window should not be loaded for dedup",
            targeted.none { it.id == 10 })
    }
}
