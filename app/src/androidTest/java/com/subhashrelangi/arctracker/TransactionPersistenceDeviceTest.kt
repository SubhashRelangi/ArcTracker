package com.subhashrelangi.arctracker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 8 Device Integration Tests executing on a real connected device with isolated Room SQLite database:
 *
 * 28. Real notification -> complete pipeline -> Room -> exactly 1 persisted transaction.
 * 29. Notification update -> same Room record updated.
 * 30. Notification + SMS correlation -> exactly 1 logical Expense.
 */
@RunWith(AndroidJUnit4::class)
class TransactionPersistenceDeviceTest {

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

    // 28. Device test: Real notification -> complete pipeline -> Room -> verify exactly one persisted transaction
    @Test
    fun test28_device_realNotification_completePipeline_persistsToRoom() = runBlocking {
        val dao = testDb.expenseDao()
        val beforeCount = dao.getCount()
        assertEquals(0, beforeCount)

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "device_test_key_1",
            postTime = System.currentTimeMillis(),
            title = "Google Pay",
            text = "Paid ₹850 to Zomato",
            channelId = "payment_channel"
        )

        val result = TransactionPersistenceManager.processCapturedNotification(captured, dao)

        assertTrue("Expected Inserted result, got $result", result is ExpensePersistenceResult.Inserted)
        val afterCount = dao.getCount()
        assertEquals(1, afterCount)

        val persisted = dao.getAllExpensesList().first()
        assertEquals(850.0, persisted.amount, 0.001)
        assertEquals("Zomato", persisted.merchant)
        assertEquals("Debit", persisted.type)
        assertEquals("Food", persisted.tag)
        assertFalse("Real valid transaction must not be marked as pending", persisted.isPending)
    }

    // 29. Device test: notification update -> same Room record updated
    @Test
    fun test29_device_notificationUpdate_updatesSameRoomRecord() = runBlocking {
        val dao = testDb.expenseDao()

        // Initial pending notification
        val captured1 = CapturedNotificationInfo(
            packageName = "com.phonepe.app",
            notificationKey = "device_update_key",
            postTime = 1700000000000L,
            title = "PhonePe",
            text = "₹1,200 payment processing to BigBasket"
        )
        val res1 = TransactionPersistenceManager.processCapturedNotification(captured1, dao)
        val countAfterFirst = dao.getCount()
        assertEquals(1, countAfterFirst)

        val initialRecord = dao.getAllExpensesList().first()
        assertTrue(initialRecord.isPending)

        // Notification update arrives: same key, now SUCCESS
        val captured2 = CapturedNotificationInfo(
            packageName = "com.phonepe.app",
            notificationKey = "device_update_key",
            postTime = 1700000005000L,
            title = "PhonePe",
            text = "₹1,200 payment successful to BigBasket UTR 987654321012"
        )
        val res2 = TransactionPersistenceManager.processCapturedNotification(captured2, dao)

        val countAfterSecond = dao.getCount()
        assertEquals("Row count must remain 1 after update", 1, countAfterSecond)

        val updatedRecord = dao.getAllExpensesList().first()
        assertEquals("Primary key id must be preserved", initialRecord.id, updatedRecord.id)
        assertFalse("Pending flag must be cleared on success update", updatedRecord.isPending)
        assertTrue("Note must include UTR after update", updatedRecord.note?.contains("987654321012") == true)
    }

    // 30. Device test: notification + SMS correlation -> exactly one logical Expense
    @Test
    fun test30_device_notificationPlusSmsCorrelation_oneLogicalExpense() = runBlocking {
        val dao = testDb.expenseDao()

        // 1. Live notification arrives from Google Pay with UTR 555444333222
        val capturedNotif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "device_gpay_txn_77",
            postTime = 1700000000000L,
            title = "Google Pay",
            text = "Paid ₹2,500 to Reliance Digital. UPI Ref: 555444333222"
        )
        val notifRes = TransactionPersistenceManager.processCapturedNotification(capturedNotif, dao)
        assertTrue(notifRes is ExpensePersistenceResult.Inserted)

        val countBeforeSms = dao.getCount()
        assertEquals(1, countBeforeSms)

        // 2. Bank SMS arrives 10 seconds later from bank with identical UTR 555444333222
        val smsRes = TransactionPersistenceManager.processSms(
            smsId = "device_sms_44",
            sender = "HDFCBK",
            body = "Rs 2500.00 debited from A/c XX8989 on 25-Sep. UTR 555444333222. Avl Bal: Rs 45,000",
            timestamp = 1700000010000L,
            dao = dao
        )

        val countAfterSms = dao.getCount()
        assertEquals("Notification + SMS must correlate to exactly ONE Expense in Room (no duplicate)", 1, countAfterSms)

        val finalExpense = dao.getAllExpensesList().first()
        assertEquals(2500.0, finalExpense.amount, 0.001)
        assertTrue("Enriched note must contain correlation info or account details",
            finalExpense.note?.contains("555444333222") == true)
    }

    // 31. Device test: OnConflictStrategy.ABORT prevents silent overwrite in real Room SQLite
    @Test
    fun test31_device_onConflictAbort_preventsSilentOverwrite() = runBlocking {
        val dao = testDb.expenseDao()
        val original = Expense(
            id = 55,
            amount = 1000.0,
            merchant = "Original Device Store",
            dateMillis = System.currentTimeMillis(),
            type = "Debit",
            note = "Device original note"
        )
        dao.insert(original)
        assertEquals(1, dao.getCount())

        val imposter = Expense(
            id = 55,
            amount = 50.0,
            merchant = "Imposter Store",
            dateMillis = System.currentTimeMillis(),
            type = "Debit",
            note = "Imposter note"
        )

        var caughtConflict = false
        try {
            dao.insert(imposter)
        } catch (e: Exception) {
            caughtConflict = true
        }

        assertTrue("Room SQLite must throw constraint exception on duplicate primary key with ABORT", caughtConflict)
        val persisted = dao.getExpenseById(55)
        assertNotNull(persisted)
        assertEquals("Amount must NOT be replaced", 1000.0, persisted!!.amount, 0.001)
        assertEquals("Merchant must NOT be replaced", "Original Device Store", persisted.merchant)
        assertEquals("Note must NOT be replaced", "Device original note", persisted.note)
    }

    // 32. Device test: manual expense is preserved when matching notification arrives
    @Test
    fun test32_device_manualExpense_protectedFromNotificationModification() = runBlocking {
        val dao = testDb.expenseDao()
        val manual = Expense(
            id = 66,
            amount = 800.0,
            merchant = "Dinner at Bistro",
            dateMillis = 1700000000000L,
            type = "Debit",
            tag = "Food",
            note = "Ref: UTR88776655 | Manual entry",
            source = "MANUAL"
        )
        dao.insert(manual)
        assertEquals(1, dao.getCount())

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "device_manual_test_key",
            postTime = 1700000005000L,
            title = "Google Pay",
            text = "Paid ₹800 to Bistro via UPI. Ref: UTR88776655"
        )

        val result = TransactionPersistenceManager.processCapturedNotification(captured, dao)

        assertEquals("Exactly one row must exist (no duplicate row)", 1, dao.getCount())
        val inDb = dao.getExpenseById(66)
        assertNotNull(inDb)
        assertEquals(800.0, inDb!!.amount, 0.001)
        assertEquals("Dinner at Bistro", inDb.merchant)
        assertEquals("Food", inDb.tag)
        assertEquals("Ref: UTR88776655 | Manual entry", inDb.note)
        assertEquals("MANUAL", inDb.source)
    }
}
