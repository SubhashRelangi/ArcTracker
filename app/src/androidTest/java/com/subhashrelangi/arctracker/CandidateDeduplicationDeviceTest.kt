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
import java.util.Calendar

/**
 * Step 10.1 Connected Device Tests executing on physical Samsung Galaxy A34 5G:
 *
 * Verifies real end-to-end device behavior:
 * 1. Multi-candidate notification where Transaction A already exists:
 *    Candidate A is detected as DUPLICATE and NOT inserted.
 *    Candidate B is detected as NEW_TRANSACTION and inserted.
 * 2. Repeated delivery / replay of multi-transaction notification creates zero duplicate rows.
 * 3. Cross-source correlation between SMS and Notification with transaction timestamp and reference matching.
 * 4. Provenance and distinction between transaction timestamp (CONTENT) and notification postTime (ANDROID).
 * 5. Bounded Room queries around transactionTimestamp with safe execution.
 */
@RunWith(AndroidJUnit4::class)
class CandidateDeduplicationDeviceTest {

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

    // 1. Transaction A arrives first -> later notification contains A + B -> A is duplicate, B is inserted
    @Test
    fun test01_device_multiTransaction_withPreExistingTransaction_deduplicatesExistingAndInsertsNew() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        val postTimeMorning = 1758776700000L // 10:35 AM
        val calMorning = Calendar.getInstance().apply {
            timeInMillis = postTimeMorning
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val expectedTimeA = calMorning.timeInMillis

        // 1. Pre-existing transaction A: ₹500 to Amazon at 10:30 AM
        val preExistingExpenseA = Expense(
            id = 1,
            amount = 500.0,
            merchant = "Amazon",
            dateMillis = expectedTimeA,
            type = "Debit",
            notificationKey = "notif_prev_amazon",
            source = "NOTIFICATION"
        )
        dao.insert(preExistingExpenseA)
        assertEquals(1, dao.getCount())

        // 2. Later notification arrives containing Transaction A (₹500 Amazon at 10:30) and Transaction B (₹250 Flipkart at 10:32)
        val multiNotif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "device_gpay_multi_1",
            postTime = postTimeMorning,
            title = "Google Pay",
            text = "10:30 Paid ₹500 to Amazon.\n10:32 Paid ₹250 to Flipkart.",
            category = "payment"
        )

        val results = TransactionPersistenceManager.processCapturedNotificationAll(multiNotif, dao)
        assertEquals("Must extract and process 2 candidates independently", 2, results.size)

        // Candidate A (Amazon) should be detected as DUPLICATE or CORRELATED (Enriched/SkippedDuplicate)
        assertTrue("Candidate A must be detected as duplicate/correlated",
            results[0] is ExpensePersistenceResult.SkippedDuplicate || results[0] is ExpensePersistenceResult.Enriched)

        // Candidate B (Flipkart) must be inserted as NEW_TRANSACTION
        assertTrue("Candidate B must be inserted as new transaction",
            results[1] is ExpensePersistenceResult.Inserted)

        // Room DB must contain exactly 2 rows: original Amazon and newly inserted Flipkart
        assertEquals("Final Room database must contain exactly 2 transactions", 2, dao.getCount())

        val allExpenses = dao.getAllExpensesList()
        val amazonExpense = allExpenses.find { it.merchant == "Amazon" }
        val flipkartExpense = allExpenses.find { it.merchant == "Flipkart" }

        assertNotNull("Existing Amazon expense must remain intact", amazonExpense)
        assertEquals(500.0, amazonExpense!!.amount, 0.001)

        assertNotNull("New Flipkart expense must be persisted", flipkartExpense)
        assertEquals(250.0, flipkartExpense!!.amount, 0.001)

        // Verify Flipkart dateMillis matches extracted 10:32 AM transaction time
        val calFlipkart = Calendar.getInstance().apply { timeInMillis = flipkartExpense.dateMillis }
        assertEquals(10, calFlipkart.get(Calendar.HOUR_OF_DAY))
        assertEquals(32, calFlipkart.get(Calendar.MINUTE))
    }

    // 2. Same notification delivered/replayed again -> no duplicate rows
    @Test
    fun test02_device_sameMultiTransactionNotificationDeliveredAgain_noDuplicateRowsCreated() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        val multiNotif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "device_replay_key",
            postTime = 1758776700000L,
            title = "Google Pay",
            text = "10:30 Paid ₹500 to Amazon.\n10:32 Paid ₹250 to Flipkart.",
            category = "payment"
        )

        // Run 1: both candidates inserted
        val run1 = TransactionPersistenceManager.processCapturedNotificationAll(multiNotif, dao)
        assertEquals(2, run1.size)
        assertEquals(2, dao.getCount())

        // Run 2: exact replay of same notification
        val run2 = TransactionPersistenceManager.processCapturedNotificationAll(multiNotif, dao)
        assertEquals(2, run2.size)
        assertTrue(run2[0] is ExpensePersistenceResult.SkippedDuplicate)
        assertTrue(run2[1] is ExpensePersistenceResult.SkippedDuplicate)

        // Count must strictly remain 2
        assertEquals("Replay of notification must never create duplicate rows in Room", 2, dao.getCount())
    }

    // 3. Cross-source correlation: SMS arrived first, later notification arrives
    @Test
    fun test03_device_notificationAndSmsCrossSourceCorrelation() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        val postTime = 1758776700000L
        val cal = Calendar.getInstance().apply {
            timeInMillis = postTime
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val txnTime = cal.timeInMillis

        // 1. Bank SMS arrives first
        val smsResult = TransactionPersistenceManager.processSms(
            smsId = "sms_device_101",
            sender = "HDFCBK",
            body = "Debited Rs. 750 at Croma on 25-Sep at 14:00 UTR 998811223344",
            timestamp = postTime,
            dao = dao
        )
        assertTrue("SMS should be inserted", smsResult is ExpensePersistenceResult.Inserted)
        assertEquals(1, dao.getCount())

        // 2. Payment App notification arrives for same transaction
        val notif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "gpay_croma_750",
            postTime = postTime + 15000L, // 15 seconds later
            title = "Google Pay",
            text = "Paid ₹750 to Croma on 25-Sep at 14:00 UTR 998811223344",
            category = "payment"
        )

        val notifResult = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue("Notification should be correlated / duplicate with SMS",
            notifResult is ExpensePersistenceResult.SkippedDuplicate || notifResult is ExpensePersistenceResult.Enriched)

        assertEquals("Cross-source correlation must not create duplicate row in Room", 1, dao.getCount())
    }

    // 4. Timestamp provenance and distinction: postTime differs from transaction time
    @Test
    fun test04_device_timestampProvenanceAndDistinction() = runBlocking {
        val dao = testDb.expenseDao()
        val postTime = 1758776700000L // 10:35 AM

        val notif = CapturedNotificationInfo(
            packageName = "com.swiggy.consumer",
            notificationKey = "swiggy_time_test",
            postTime = postTime,
            title = "Swiggy",
            text = "Paid ₹400 to Swiggy at 10:30 AM",
            category = "payment"
        )

        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue(res is ExpensePersistenceResult.Inserted)
        assertEquals(1, dao.getCount())

        val saved = dao.getAllExpensesList().first()
        val cal = Calendar.getInstance().apply { timeInMillis = saved.dateMillis }

        // Must store transaction time (10:30 AM), NOT postTime (10:35 AM)
        assertEquals(10, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
        assertNotEquals("dateMillis must not equal raw notification postTime when content time exists",
            postTime, saved.dateMillis)
    }

    // 5. Two transactions with same amount and merchant but different hours are not merged
    @Test
    fun test05_device_twoIdenticalAmountAndMerchantDifferingTimes_notMerged() = runBlocking {
        val dao = testDb.expenseDao()
        val basePostTime = 1758776700000L

        val notifMorning = CapturedNotificationInfo(
            packageName = "com.amazon.mShop.android.shopping",
            notificationKey = "amazon_morning",
            postTime = basePostTime,
            title = "Amazon",
            text = "Paid ₹500 to Amazon at 10:30 AM",
            category = "payment"
        )

        val resMorning = TransactionPersistenceManager.processCapturedNotification(notifMorning, dao)
        assertTrue(resMorning is ExpensePersistenceResult.Inserted)
        assertEquals(1, dao.getCount())

        val notifEvening = CapturedNotificationInfo(
            packageName = "com.amazon.mShop.android.shopping",
            notificationKey = "amazon_evening",
            postTime = basePostTime + (6 * 3600 * 1000L),
            title = "Amazon",
            text = "Paid ₹500 to Amazon at 04:30 PM",
            category = "payment"
        )

        val resEvening = TransactionPersistenceManager.processCapturedNotification(notifEvening, dao)
        assertTrue(resEvening is ExpensePersistenceResult.Inserted)

        // Both legitimate payments must be kept in Room
        assertEquals("Both morning and evening transactions must be preserved in Room", 2, dao.getCount())
    }
}
