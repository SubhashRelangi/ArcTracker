package com.subhashrelangi.arctracker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subhashrelangi.arctracker.data.AccountSource
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.KnownFinancialAccountRepository
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Milestone 4: Live Notification -> Known Financial Account Matching Real Physical Device Tests
 * Executing on connected physical Samsung Galaxy A34 5G (Android 16).
 */
@RunWith(AndroidJUnit4::class)
class KnownFinancialAccountMatcherDeviceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var accountRepo: KnownFinancialAccountRepository
    private lateinit var matcher: KnownFinancialAccountMatcher

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = AppDatabase.createInMemoryDatabase(context)
        accountRepo = KnownFinancialAccountRepository(database.knownFinancialAccountDao())
        matcher = KnownFinancialAccountMatcher(accountRepo)
        TransactionPersistenceManager.matcher = matcher
        NotificationPermissionHelper.permissionOverrideForTesting = true
        NotificationReaderService.clearActiveNotifications()
    }

    @After
    fun tearDown() {
        database.close()
        TransactionPersistenceManager.matcher = null
        NotificationPermissionHelper.permissionOverrideForTesting = null
        NotificationReaderService.clearActiveNotifications()
    }

    // ==================================================
    // 1. Known account matching
    // ==================================================
    @Test
    fun test01_device_knownAccountMatchingAndEnrichment() = runBlocking {
        accountRepo.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT,
                source = AccountSource.HISTORICAL_SMS
            )
        )

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "device_m4_01_${System.currentTimeMillis()}",
            postTime = System.currentTimeMillis(),
            title = "Google Pay",
            text = "₹100 transferred to Subhash from XXXXX9020"
        )

        val dao = database.expenseDao()
        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)

        assertEquals(1, results.size)
        assertTrue(results[0] is ExpensePersistenceResult.Inserted)
        val res = results[0] as ExpensePersistenceResult.Inserted

        // Verify transaction is recorded in Room
        assertEquals(1, dao.getCount())
        assertEquals(100.0, res.expense.amount, 0.001)

        // Verify match result and enrichment
        assertTrue(res.matchResult is KnownFinancialAccountMatchResult.Matched)
        assertEquals("hdfc_bank_account_9020", res.accountEnrichment?.accountId)
        assertEquals("hdfc", res.accountEnrichment?.institutionId)
        assertEquals("HDFC Bank", res.accountEnrichment?.institutionName)
        assertEquals(InstrumentType.BANK_ACCOUNT, res.accountEnrichment?.instrumentType)
    }

    // ==================================================
    // 2. Unknown account still produces transaction
    // ==================================================
    @Test
    fun test02_device_unknownAccountProducesTransactionNormally() = runBlocking {
        // Registry is intentionally empty
        assertEquals(0, accountRepo.getCount())

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "device_m4_02_${System.currentTimeMillis()}",
            postTime = System.currentTimeMillis(),
            title = "Google Pay",
            text = "₹250 debited for groceries from XXXXX9020"
        )

        val dao = database.expenseDao()
        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)

        assertEquals(1, results.size)
        assertTrue(results[0] is ExpensePersistenceResult.Inserted)
        val res = results[0] as ExpensePersistenceResult.Inserted

        // Expense saved in Room
        assertEquals(1, dao.getCount())
        assertEquals(250.0, res.expense.amount, 0.001)

        // Match result is Unmatched, no enrichment
        assertEquals(KnownFinancialAccountMatchResult.Unmatched, res.matchResult)
        assertNull(res.accountEnrichment)

        // Zero records created in registry
        assertEquals(0, accountRepo.getCount())
    }

    // ==================================================
    // 3. Same suffix / different banks produces ambiguity
    // ==================================================
    @Test
    fun test03_device_sameSuffixDifferentBanksProducesAmbiguity() = runBlocking {
        accountRepo.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )
        accountRepo.upsert(
            KnownFinancialAccount.create(
                institutionId = "apgb",
                institutionName = "APGBank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "device_m4_03_${System.currentTimeMillis()}",
            postTime = System.currentTimeMillis(),
            title = "Google Pay",
            text = "₹100 transferred to Subhash from XXXXX9020"
        )

        val dao = database.expenseDao()
        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)

        assertEquals(1, results.size)
        assertTrue(results[0] is ExpensePersistenceResult.Inserted)
        val res = results[0] as ExpensePersistenceResult.Inserted

        // Valid transaction preserved
        assertEquals(1, dao.getCount())

        // Ambiguity preserved without guessing
        assertTrue(res.matchResult is KnownFinancialAccountMatchResult.Ambiguous)
        assertNull(res.accountEnrichment)
        val candidates = (res.matchResult as KnownFinancialAccountMatchResult.Ambiguous).candidates
        assertEquals(2, candidates.size)
    }

    // ==================================================
    // 4. Promotional notification remains rejected
    // ==================================================
    @Test
    fun test04_device_promotionalNotificationRemainsRejected() = runBlocking {
        accountRepo.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )

        val captured = CapturedNotificationInfo(
            packageName = "net.one97.paytm",
            notificationKey = "device_m4_04_${System.currentTimeMillis()}",
            postTime = System.currentTimeMillis(),
            title = "Paytm Cashback",
            text = "Congratulations! Get ₹5,000 cashback! XXXXX9020"
        )

        val dao = database.expenseDao()
        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)

        assertEquals(1, results.size)
        assertTrue(results[0] is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, dao.getCount())
    }

    // ==================================================
    // 5. Card / account distinction
    // ==================================================
    @Test
    fun test05_device_cardAndAccountDistinction() = runBlocking {
        accountRepo.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )
        accountRepo.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.CARD
            )
        )

        val cardNotification = CapturedNotificationInfo(
            packageName = "com.hdfc.bank",
            notificationKey = "device_m4_05_${System.currentTimeMillis()}",
            postTime = System.currentTimeMillis(),
            title = "HDFC Bank Alert",
            text = "Paid ₹750 using card ****9020"
        )

        val dao = database.expenseDao()
        val results = TransactionPersistenceManager.processCapturedNotificationAll(cardNotification, dao)

        assertEquals(1, results.size)
        assertTrue(results[0] is ExpensePersistenceResult.Inserted)
        val res = results[0] as ExpensePersistenceResult.Inserted

        assertEquals(1, dao.getCount())
        assertEquals(750.0, res.expense.amount, 0.001)

        // Must match CARD, not BANK_ACCOUNT
        assertTrue(res.matchResult is KnownFinancialAccountMatchResult.Matched)
        assertEquals("hdfc_card_9020", res.accountEnrichment?.accountId)
        assertEquals(InstrumentType.CARD, res.accountEnrichment?.instrumentType)
    }

    // ==================================================
    // 6. Existing notification tracking remains functional
    // ==================================================
    @Test
    fun test06_device_existingNotificationTrackingRemainsFunctional() = runBlocking {
        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "device_m4_06_${System.currentTimeMillis()}",
            postTime = System.currentTimeMillis(),
            title = "Google Pay",
            text = "Paid ₹300 to Store"
        )

        val dao = database.expenseDao()
        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, dao)

        assertEquals(1, results.size)
        assertTrue(results[0] is ExpensePersistenceResult.Inserted)
        val res = results[0] as ExpensePersistenceResult.Inserted

        assertEquals(1, dao.getCount())
        assertEquals(300.0, res.expense.amount, 0.001)
        assertEquals(KnownFinancialAccountMatchResult.NoAccountData, res.matchResult)
    }
}
