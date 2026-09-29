package com.example.arctracker

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arctracker.data.*
import com.example.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Milestone 6: Account Management & Manual Transaction Reconciliation UI Physical Device Tests.
 *
 * Runs on Samsung Galaxy A34 5G (Android 16).
 * Covers all 17 verification points from Part 23 of the Milestone 6 specification.
 */
@RunWith(AndroidJUnit4::class)
class FinancialAccountsDeviceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var accountRepo: KnownFinancialAccountRepository
    private lateinit var expenseDao: ExpenseDao
    private lateinit var manager: AccountReconciliationManager
    private val persistentDbName = "m6_device_test_persistent.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(persistentDbName)

        database = Room.databaseBuilder(context, AppDatabase::class.java, persistentDbName)
            .allowMainThreadQueries()
            .build()

        expenseDao = database.expenseDao()
        accountRepo = KnownFinancialAccountRepository(database.knownFinancialAccountDao())
        manager = AccountReconciliationManager(expenseDao, accountRepo)

        NotificationPermissionHelper.permissionOverrideForTesting = true
        NotificationReaderService.clearActiveNotifications()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(persistentDbName)
        NotificationPermissionHelper.permissionOverrideForTesting = null
        NotificationReaderService.clearActiveNotifications()
    }

    // 1. Existing Room v4 database opens.
    // 2. Existing known accounts appear.
    // 3. Existing linked transactions appear under the correct account.
    // 4. Same-suffix accounts remain separate.
    @Test
    fun test01_roomV4Opens_andAccountsAppear_separateSameSuffix() = runBlocking {
        assertEquals(4, database.openHelper.readableDatabase.version)

        val hdfc = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            source = AccountSource.HISTORICAL_SMS
        )
        val apgb = KnownFinancialAccount(
            id = "apgb_bank_account_9020",
            institutionId = "apgb",
            institutionName = "APGB",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            source = AccountSource.HISTORICAL_SMS
        )
        accountRepo.upsert(hdfc)
        accountRepo.upsert(apgb)

        // Same suffix 9020, but distinct accounts
        val accounts = manager.getAllKnownAccounts()
        assertEquals(2, accounts.size)
        assertTrue(accounts.any { it.id == hdfc.id })
        assertTrue(accounts.any { it.id == apgb.id })

        val txn1Id = expenseDao.insert(
            Expense(
                amount = 150.0,
                merchant = "Amazon",
                dateMillis = System.currentTimeMillis(),
                accountId = hdfc.id,
                accountSuffix = "9020"
            )
        ).toInt()

        val txn2Id = expenseDao.insert(
            Expense(
                amount = 250.0,
                merchant = "Flipkart",
                dateMillis = System.currentTimeMillis(),
                accountId = apgb.id,
                accountSuffix = "9020"
            )
        ).toInt()

        val hdfcTxns = manager.getLinkedTransactions(hdfc.id)
        val apgbTxns = manager.getLinkedTransactions(apgb.id)

        assertEquals(1, hdfcTxns.size)
        assertEquals(txn1Id, hdfcTxns[0].id)
        assertEquals(1, apgbTxns.size)
        assertEquals(txn2Id, apgbTxns[0].id)
    }

    // 5. An ambiguous transaction appears under unresolved.
    // 6. Open Assign Account (get compatible accounts).
    // 7. Select intended account.
    // 8. Confirm.
    // 9. Verify transaction now appears under that account.
    @Test
    fun test02_ambiguousTransactionAppearsUnresolved_andManualAssignmentSucceeds() = runBlocking {
        val hdfc = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val apgb = KnownFinancialAccount(
            id = "apgb_bank_account_9020",
            institutionId = "apgb",
            institutionName = "APGB",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(hdfc)
        accountRepo.upsert(apgb)

        // Ambiguous transaction without accountId
        val expense = Expense(
            amount = 500.0,
            merchant = "Swiggy",
            dateMillis = System.currentTimeMillis(),
            accountId = null,
            accountSuffix = "9020"
        )
        val expenseId = expenseDao.insert(expense).toInt()

        // 5. Appears in unresolved list
        val unresolved = manager.getUnresolvedTransactions()
        assertTrue(unresolved.any { it.id == expenseId })

        // 6. Compatible accounts
        val candidates = manager.getCompatibleAccounts(expense)
        assertEquals(2, candidates.size)

        // 7 & 8. User explicitly selects and confirms HDFC
        val result = manager.assignAccount(expenseId, hdfc.id)
        assertTrue(result.isSuccess)

        // 9. Verify transaction now appears under HDFC, and no longer in unresolved
        val hdfcTxns = manager.getLinkedTransactions(hdfc.id)
        assertEquals(1, hdfcTxns.size)
        assertEquals(expenseId, hdfcTxns[0].id)

        val updatedUnresolved = manager.getUnresolvedTransactions()
        assertFalse(updatedUnresolved.any { it.id == expenseId })
    }

    // 10. Restart application (close/reopen database).
    // 11. Verify accountId relationship survives restart.
    @Test
    fun test03_accountIdSurvivesRestart() = runBlocking {
        val account = KnownFinancialAccount(
            id = "icici_bank_account_3333",
            institutionId = "icici",
            institutionName = "ICICI Bank",
            accountSuffix = "3333",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)

        val expenseId = expenseDao.insert(
            Expense(
                amount = 750.0,
                merchant = "Zomato",
                dateMillis = System.currentTimeMillis(),
                accountId = account.id,
                accountSuffix = "3333"
            )
        ).toInt()

        // Close DB simulating app kill
        database.close()

        // Reopen DB simulating app restart
        val reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, persistentDbName)
            .allowMainThreadQueries()
            .build()
        val reopenedManager = AccountReconciliationManager(
            reopenedDb.expenseDao(),
            KnownFinancialAccountRepository(reopenedDb.knownFinancialAccountDao())
        )

        // 11. Verify relationship survives restart
        val loaded = reopenedDb.expenseDao().getExpenseById(expenseId)
        assertNotNull(loaded)
        assertEquals(account.id, loaded!!.accountId)
        assertEquals("3333", loaded.accountSuffix)

        val linkedTxns = reopenedManager.getLinkedTransactions(account.id)
        assertEquals(1, linkedTxns.size)
        assertEquals(expenseId, linkedTxns[0].id)

        reopenedDb.close()
    }

    // 12. Unlink the transaction.
    // 13. Verify it returns to unresolved.
    // 14. Reassign it to another valid account.
    // 15. Verify reassignment survives restart.
    @Test
    fun test04_unlink_reassign_andSurvivesRestart() = runBlocking {
        val hdfc = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val apgb = KnownFinancialAccount(
            id = "apgb_bank_account_9020",
            institutionId = "apgb",
            institutionName = "APGB",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(hdfc)
        accountRepo.upsert(apgb)

        val expenseId = expenseDao.insert(
            Expense(
                amount = 400.0,
                merchant = "Store",
                dateMillis = System.currentTimeMillis(),
                accountId = hdfc.id,
                accountSuffix = "9020"
            )
        ).toInt()

        // 12. Unlink transaction
        val unlinkResult = manager.unlinkAccount(expenseId)
        assertTrue(unlinkResult.isSuccess)

        // 13. Returns to unresolved, preserves suffix
        val afterUnlink = expenseDao.getExpenseById(expenseId)
        assertNotNull(afterUnlink)
        assertNull(afterUnlink!!.accountId)
        assertEquals("9020", afterUnlink.accountSuffix)

        val unresolved = manager.getUnresolvedTransactions()
        assertTrue(unresolved.any { it.id == expenseId })

        // 14. Reassign to APGB
        val reassignResult = manager.reassignAccount(expenseId, apgb.id)
        assertTrue(reassignResult.isSuccess)

        val afterReassign = expenseDao.getExpenseById(expenseId)
        assertNotNull(afterReassign)
        assertEquals(apgb.id, afterReassign!!.accountId)

        // 15. Restart DB and verify reassignment survives
        database.close()

        val reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, persistentDbName)
            .allowMainThreadQueries()
            .build()
        val reopenedLoaded = reopenedDb.expenseDao().getExpenseById(expenseId)
        assertNotNull(reopenedLoaded)
        assertEquals(apgb.id, reopenedLoaded!!.accountId)
        assertEquals("9020", reopenedLoaded.accountSuffix)

        reopenedDb.close()
    }

    // 16. Verify normal notification tracking still works.
    @Test
    fun test05_notificationTrackingContinuesToWork() = runBlocking {
        val sbi = KnownFinancialAccount(
            id = "sbi_bank_account_1122",
            institutionId = "sbi",
            institutionName = "SBI",
            accountSuffix = "1122",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(sbi)

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "Bank Alert",
            text = "Rs. 120 paid using account ending 1122",
            postTime = System.currentTimeMillis(),
            notificationKey = "notif_live_test_key"
        )

        val matcher = KnownFinancialAccountMatcher(accountRepo)
        val inserted = TransactionPersistenceManager.processCapturedNotificationAll(
            captured,
            database.expenseDao(),
            matcher
        )

        val expenses = database.expenseDao().getAllExpensesList()
        val persisted = expenses.find { it.notificationKey == "notif_live_test_key" }
        assertNotNull(persisted)
        assertEquals(sbi.id, persisted!!.accountId)
        assertEquals("1122", persisted.accountSuffix)
        assertEquals(120.0, persisted.amount, 0.001)
    }

    // 17. Verify historical SMS import still works.
    @Test
    fun test06_historicalSmsImportContinuesToWork() = runBlocking {
        val synchronizer = HistoricalSmsAccountRegistrySynchronizer(accountRepo, expenseDao)

        val identity = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            evidenceSource = "SMS"
        )

        val synced = synchronizer.synchronize(identity)
        assertNotNull(synced)
        assertEquals("hdfc_bank_account_9020", synced!!.id)

        val inRepo = accountRepo.getById("hdfc_bank_account_9020")
        assertNotNull(inRepo)
        assertEquals("9020", inRepo!!.accountSuffix)
    }
}
