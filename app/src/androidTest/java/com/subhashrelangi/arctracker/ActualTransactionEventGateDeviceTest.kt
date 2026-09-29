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
 * Physical Device Integration Tests for Actual Transaction Event Gate (Step 4.5)
 * executing on connected Samsung Galaxy A34 5G with isolated Room database.
 */
@RunWith(AndroidJUnit4::class)
class ActualTransactionEventGateDeviceTest {

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
     * Case J on Device: The real-world Paytm credit card bill promotion.
     * Must produce:
     * - IgnoredNonFinancial persistence result
     * - 0 Room database inserts
     * - 0 Pending expenses
     */
    @Test
    fun testDevice_paytmProductionFalsePositive_zeroRoomRows() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        val promoText = "₹2,000 Is One Bill Away! 🚀\nPay your Credit Card Bill on Paytm with No Extra Fees & get a chance to win ₹2,000 cashback. Use CCBP2000.\nNo Fees"
        val captured = CapturedNotificationInfo(
            packageName = "net.one97.paytm",
            notificationKey = "device_test_paytm_promo_${System.currentTimeMillis()}",
            postTime = System.currentTimeMillis(),
            title = "Paytm",
            text = promoText
        )

        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)

        assertEquals(1, results.size)
        assertTrue(
            "Expected IgnoredNonFinancial but got ${results[0]}",
            results[0] is ExpensePersistenceResult.IgnoredNonFinancial
        )

        // Strict verification: Zero Room entries created
        assertEquals(0, dao.getCount())
        assertEquals(0, dao.getPendingExpensesList().size)
        assertTrue(dao.getAllExpensesList().isEmpty())
    }

    /**
     * Genuine transaction from unknown third-party application on Device.
     * Content evidence is primary: Must be accepted and persisted to Room.
     */
    @Test
    fun testDevice_genuineDebitFromUnknownApp_persistedToRoom() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        val text = "₹350 spent at Starbucks"
        val captured = CapturedNotificationInfo(
            packageName = "com.thirdparty.coffeeapp",
            notificationKey = "device_test_unknown_app_${System.currentTimeMillis()}",
            postTime = System.currentTimeMillis(),
            title = "Coffee Rewards",
            text = text
        )

        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)

        assertEquals(1, results.size)
        assertTrue(
            "Expected Inserted but got ${results[0]}",
            results[0] is ExpensePersistenceResult.Inserted
        )

        // Exactly one Room database record
        assertEquals(1, dao.getCount())
        val saved = dao.getAllExpensesList().first()
        assertEquals(350.0, saved.amount, 0.001)
        assertEquals("DEBIT", saved.type.uppercase())
        assertEquals("Starbucks", saved.merchant)
        assertFalse(saved.isPending)
    }

    /**
     * Mixed notification: 1 valid completed debit + 1 promotional future offer.
     * Gate isolates the valid clause and filters the promo clause.
     */
    @Test
    fun testDevice_multiClauseNotification_persistsOnlyValidClause() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        val text = "₹500 paid to Swiggy. Pay another ₹500 to win ₹100 cashback."
        val captured = CapturedNotificationInfo(
            packageName = "in.swiggy.android",
            notificationKey = "device_test_multiclause_${System.currentTimeMillis()}",
            postTime = System.currentTimeMillis(),
            title = "Swiggy",
            text = text
        )

        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)

        // Exactly 1 inserted expense corresponding to the real debit
        val inserted = results.filterIsInstance<ExpensePersistenceResult.Inserted>()
        assertEquals(1, inserted.size)

        assertEquals(1, dao.getCount())
        val saved = dao.getAllExpensesList().first()
        assertEquals(500.0, saved.amount, 0.001)
        assertEquals("DEBIT", saved.type.uppercase())
        assertEquals("Swiggy", saved.merchant)
    }
}
