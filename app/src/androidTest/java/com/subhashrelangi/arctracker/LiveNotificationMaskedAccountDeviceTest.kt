package com.subhashrelangi.arctracker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Physical Device Integration Tests for Milestone 1: Live Notification Masked Account Suffix Extraction
 * executing on connected Samsung Galaxy A34 5G.
 */
@RunWith(AndroidJUnit4::class)
class LiveNotificationMaskedAccountDeviceTest {

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

    /**
     * Case A: "₹100 transferred to Subhash from XXXXX9020"
     * Expected:
     * - Transaction is detected exactly as before
     * - accountSuffix is extracted as 9020
     * - Bank is null (bank identity deferred to future milestone)
     */
    @Test
    fun testDevice_caseA_maskedAccountNotification() = runBlocking {
        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "device_test_case_a_${System.currentTimeMillis()}",
            postTime = System.currentTimeMillis(),
            title = "Google Pay",
            text = "₹100 transferred to Subhash from XXXXX9020"
        )

        val normalized = NotificationNormalizer.normalize(captured)!!
        val classification = FinancialClassifier.classify(normalized)
        val candidates = StructuredTransactionExtractor.extractAll(classification)

        assertEquals(1, candidates.size)
        val candidate = candidates.first()
        assertEquals(100.0, candidate.amount)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("9020", candidate.accountSuffix)
        assertNull("Bank must not be inferred in Milestone 1", candidate.bank)
        assertEquals("Subhash", candidate.merchant)

        // Verify end-to-end persistence flow
        val dao = testDb.expenseDao()
        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)
        assertEquals(1, results.size)
        assertTrue(results[0] is ExpensePersistenceResult.Inserted)
        assertEquals(1, dao.getCount())
    }

    /**
     * Case B: Notification without account information
     * Expected:
     * - Transaction still detected exactly as before
     * - accountSuffix = null
     */
    @Test
    fun testDevice_caseB_notificationWithoutAccountInfo() = runBlocking {
        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "device_test_case_b_${System.currentTimeMillis()}",
            postTime = System.currentTimeMillis(),
            title = "Google Pay",
            text = "Paid ₹500 to Subhash"
        )

        val normalized = NotificationNormalizer.normalize(captured)!!
        val classification = FinancialClassifier.classify(normalized)
        val candidates = StructuredTransactionExtractor.extractAll(classification)

        assertEquals(1, candidates.size)
        val candidate = candidates.first()
        assertEquals(500.0, candidate.amount)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertNull("Account suffix must be null when absent", candidate.accountSuffix)
        assertEquals("Subhash", candidate.merchant)

        // Verify end-to-end persistence flow
        val dao = testDb.expenseDao()
        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)
        assertEquals(1, results.size)
        assertTrue(results[0] is ExpensePersistenceResult.Inserted)
        assertEquals(1, dao.getCount())
    }

    /**
     * Case C: Promotional notification containing a masked suffix
     * Example: "Congratulations! Get ₹5,000 cashback! XXXXX9020"
     * Expected:
     * - Existing noise/classification behavior remains unchanged
     * - It must NOT become a transaction merely because 9020 exists
     * - Zero Room database entries created
     */
    @Test
    fun testDevice_caseC_promotionalNotificationWithMaskedDigits() = runBlocking {
        val captured = CapturedNotificationInfo(
            packageName = "net.one97.paytm",
            notificationKey = "device_test_case_c_${System.currentTimeMillis()}",
            postTime = System.currentTimeMillis(),
            title = "Paytm Offer",
            text = "Congratulations! Get ₹5,000 cashback! XXXXX9020"
        )

        val normalized = NotificationNormalizer.normalize(captured)!!
        val classification = FinancialClassifier.classify(normalized)

        // Must be rejected at classification/noise/actual-event level
        val candidates = StructuredTransactionExtractor.extractAll(classification)
        assertTrue("Promotional notification must not produce candidates", candidates.isEmpty())

        // Verify end-to-end persistence flow
        val dao = testDb.expenseDao()
        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)
        assertEquals(1, results.size)
        assertTrue(
            "Expected IgnoredNonFinancial but got ${results[0]}",
            results[0] is ExpensePersistenceResult.IgnoredNonFinancial
        )
        assertEquals(0, dao.getCount())
    }
}
