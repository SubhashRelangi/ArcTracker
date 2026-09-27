package com.example.arctracker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arctracker.data.AppDatabase
import com.example.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 10 Connected Device Tests executing on physical Samsung Galaxy A34 5G:
 *
 * Verifies real end-to-end device behavior:
 * 1. Multi-transaction extraction and persistence into device Room SQLite database.
 * 2. Strict prevention of ₹0.0 transactions from reaching Room.
 * 3. Accurate rejection of balance inquiries and promotional notifications.
 * 4. Correct extraction of transaction amount when secondary amounts (GST, Total) exist.
 * 5. Full trace through capture -> normalize -> classify -> segment -> extract -> validate -> deduplicate -> persistence.
 */
@RunWith(AndroidJUnit4::class)
class NotificationAccuracyDeviceTest {

    private lateinit var context: Context
    private lateinit var testDb: AppDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testDb = AppDatabase.createInMemoryDatabase(context)
        NotificationPermissionHelper.permissionOverrideForTesting = true
        NotificationReaderService.clearActiveNotifications()
    }

    @After
    fun tearDown() {
        testDb.close()
        NotificationPermissionHelper.permissionOverrideForTesting = null
        NotificationReaderService.clearActiveNotifications()
    }

    // 1. Multi-transaction notification creates multiple independent records in Room
    @Test
    fun test01_device_multiTransaction_persistsMultipleRecords() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        val multiNotif = CapturedNotificationInfo(
            packageName = "com.phonepe.app",
            notificationKey = "device_phonepe_multi_1",
            postTime = System.currentTimeMillis(),
            title = "PhonePe",
            text = "Paid ₹500 to Amazon. Paid ₹250 to Flipkart."
        )

        val results = TransactionPersistenceManager.processCapturedNotificationAll(multiNotif, dao)
        assertEquals("Must extract and process 2 candidates", 2, results.size)
        assertEquals("Room must contain 2 distinct transactions", 2, dao.getCount())

        val expenses = dao.getAllExpensesList()
        val amazonExp = expenses.find { it.merchant == "Amazon" }
        val flipkartExp = expenses.find { it.merchant == "Flipkart" }

        assertNotNull("Amazon transaction must exist", amazonExp)
        assertEquals(500.0, amazonExp!!.amount, 0.001)

        assertNotNull("Flipkart transaction must exist", flipkartExp)
        assertEquals(250.0, flipkartExp!!.amount, 0.001)
    }

    // 2. Zero amount strictly never creates ₹0.0 in Room
    @Test
    fun test02_device_zeroAmount_neverReachesRoom() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        val zeroNotif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "device_zero_1",
            postTime = System.currentTimeMillis(),
            title = "Google Pay",
            text = "Paid ₹0.00 to Merchant"
        )

        val res = TransactionPersistenceManager.processCapturedNotification(zeroNotif, dao)
        assertTrue("Zero amount must be rejected", res is ExpensePersistenceResult.Rejected || res is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals("Zero amount must NEVER reach Room on device", 0, dao.getCount())
    }

    // 3. Balance-only inquiry never creates transaction in Room
    @Test
    fun test03_device_balanceInquiry_neverReachesRoom() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        val balanceNotif = CapturedNotificationInfo(
            packageName = "com.hdfc.bank",
            notificationKey = "device_bal_1",
            postTime = System.currentTimeMillis(),
            title = "HDFC Bank",
            text = "Available balance in A/c XX5678 is ₹25,400.50"
        )

        val res = TransactionPersistenceManager.processCapturedNotification(balanceNotif, dao)
        assertTrue(res is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, dao.getCount())
    }

    // 4. Marketing offer with payment words never reaches Room
    @Test
    fun test04_device_promotionalOffer_neverReachesRoom() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        val promoNotif = CapturedNotificationInfo(
            packageName = "com.phonepe.app",
            notificationKey = "device_promo_1",
            postTime = System.currentTimeMillis(),
            title = "PhonePe",
            text = "Pay using PhonePe and get flat ₹50 cashback on your next purchase! Claim now."
        )

        val res = TransactionPersistenceManager.processCapturedNotification(promoNotif, dao)
        assertTrue(res is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, dao.getCount())
    }

    // 5. Transaction with secondary GST and Total extracts transaction amount accurately
    @Test
    fun test05_device_transactionWithGstAndTotal_extractsTransactionAmount() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        val gstNotif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "device_gst_1",
            postTime = System.currentTimeMillis(),
            title = "Google Pay",
            text = "Paid ₹800 to Blue Tokai. GST ₹40. Total ₹840."
        )

        val res = TransactionPersistenceManager.processCapturedNotification(gstNotif, dao)
        assertTrue(res is ExpensePersistenceResult.Inserted)
        assertEquals(1, dao.getCount())

        val saved = dao.getAllExpensesList().first()
        assertEquals("Transaction amount must be 800 (not 840 total)", 800.0, saved.amount, 0.001)
        assertEquals("Blue Tokai", saved.merchant)
    }

    // 6. End-to-end pipeline trace: capture -> normalize -> classify -> segment -> extract -> validate -> dedup -> persist
    @Test
    fun test06_device_fullPipelineTrace() = runBlocking {
        val dao = testDb.expenseDao()

        // 1. Capture
        val raw = CapturedNotificationInfo(
            packageName = "com.phonepe.app",
            notificationKey = "trace_key_1",
            postTime = 1700000000000L,
            title = "PhonePe",
            text = "Paid ₹1,250 to Croma. UPI Ref: 998877665544"
        )
        assertNotNull(raw)

        // 2. Normalize
        val normalized = NotificationNormalizer.normalize(raw)
        assertNotNull(normalized)
        assertEquals("PhonePe", normalized!!.normalizedTitle)

        // 3. Classify
        val classification = FinancialClassifier.classify(normalized)
        assertEquals(FinancialRelevance.FINANCIAL, classification.financialRelevance)
        assertFalse(classification.isNoise)

        // 4. Segment
        val segments = StructuredTransactionExtractor.segmentNotification(normalized)
        assertEquals(1, segments.size)

        // 5. Extract
        val candidates = StructuredTransactionExtractor.extractAll(classification)
        assertEquals(1, candidates.size)
        val candidate = candidates[0]
        assertEquals(1250.0, candidate.amount)
        assertEquals("Croma", candidate.merchant)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("998877665544", candidate.referenceId)

        // 6. Validate
        val validated = TransactionValidator.validate(candidate, classification)
        assertTrue(validated.isAcceptable)
        assertFalse(validated.isRejected)

        // 7. Deduplicate
        val dedupResult = TransactionDeduplicator.evaluate(validated, emptyList())
        assertEquals(DedupDecision.NEW_TRANSACTION, dedupResult.decision)

        // 8. Persist
        val persistResult = TransactionPersistenceManager.processValidatedCandidate(validated, dao)
        assertTrue(persistResult is ExpensePersistenceResult.Inserted)
        assertEquals(1, dao.getCount())

        val saved = dao.getAllExpensesList().first()
        assertEquals(1250.0, saved.amount, 0.001)
        assertEquals("Croma", saved.merchant)
    }
}
