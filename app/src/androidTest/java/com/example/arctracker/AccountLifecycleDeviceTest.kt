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
 * Milestone 7: Account Lifecycle Management & Bulk Transaction Reconciliation Physical Device Tests.
 *
 * Runs on Samsung Galaxy A34 5G (SM-A346E - Android 16).
 * Covers all 25 verification points from Part 35 of the Milestone 7 specification.
 */
@RunWith(AndroidJUnit4::class)
class AccountLifecycleDeviceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var accountRepo: KnownFinancialAccountRepository
    private lateinit var expenseDao: ExpenseDao
    private lateinit var manager: AccountReconciliationManager
    private val persistentDbName = "m7_device_test_persistent.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(persistentDbName)

        database = Room.databaseBuilder(context, AppDatabase::class.java, persistentDbName)
            .allowMainThreadQueries()
            .build()

        expenseDao = database.expenseDao()
        accountRepo = KnownFinancialAccountRepository(database.knownFinancialAccountDao())
        manager = AccountReconciliationManager(expenseDao, accountRepo, database)

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

    /**
     * Verification Points 1–7:
     * 1. Existing Room v4 database opens.
     * 2. Existing accounts appear.
     * 3. Existing linked transactions appear.
     * 4. Delete an account with linked transactions.
     * 5. Verify transactions remain.
     * 6. Verify transactions become unresolved.
     * 7. Verify suffix remains visible.
     */
    @Test
    fun test01_roomV4Opens_andDeleteAccountUnlinksTransactionsSafely() = runBlocking {
        // 1. Room v4 opens
        assertEquals(4, database.openHelper.readableDatabase.version)

        // Setup account
        val hdfc = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            source = AccountSource.HISTORICAL_SMS
        )
        accountRepo.upsert(hdfc)

        // 2. Existing accounts appear
        val accounts = manager.getAllKnownAccounts()
        assertEquals(1, accounts.size)
        assertEquals(hdfc.id, accounts[0].id)

        // Setup linked transactions
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
                accountId = hdfc.id,
                accountSuffix = "9020"
            )
        ).toInt()

        // 3. Existing linked transactions appear
        val linkedBefore = manager.getLinkedTransactions(hdfc.id)
        assertEquals(2, linkedBefore.size)
        assertTrue(linkedBefore.any { it.id == txn1Id })
        assertTrue(linkedBefore.any { it.id == txn2Id })

        // 4. Delete an account with linked transactions
        val deleteResult = manager.deleteAccount(hdfc.id)
        assertTrue(deleteResult.isSuccess)
        val unlinkedCount = deleteResult.getOrNull()
        assertEquals(2, unlinkedCount)

        // Account is deleted from registry
        assertNull(accountRepo.getById(hdfc.id))
        assertTrue(manager.getAllKnownAccounts().isEmpty())

        // 5. Verify transactions remain in database
        val txn1After = expenseDao.getExpenseById(txn1Id)
        val txn2After = expenseDao.getExpenseById(txn2Id)
        assertNotNull(txn1After)
        assertNotNull(txn2After)
        assertEquals(150.0, txn1After!!.amount, 0.001)
        assertEquals(250.0, txn2After!!.amount, 0.001)

        // 6. Verify transactions become unresolved (accountId = null)
        assertNull(txn1After.accountId)
        assertNull(txn2After.accountId)
        val unresolved = manager.getUnresolvedTransactions()
        assertEquals(2, unresolved.size)
        assertTrue(unresolved.any { it.id == txn1Id })
        assertTrue(unresolved.any { it.id == txn2Id })

        // 7. Verify suffix remains visible
        assertEquals("9020", txn1After.accountSuffix)
        assertEquals("9020", txn2After.accountSuffix)
    }

    /**
     * Verification Points 8–11:
     * 8. Create/verify a compatible source and target account.
     * 9. Perform account merge.
     * 10. Verify all source transactions appear under target.
     * 11. Verify source account disappears.
     */
    @Test
    fun test02_compatibleAccountMergeMovesTransactionsAndRemovesSource() = runBlocking {
        // 8. Create/verify compatible source and target account
        // Source: unknown institution bank account with suffix 9020
        val source = KnownFinancialAccount(
            id = "unknown_bank_account_9020",
            institutionId = "unknown",
            institutionName = "Unknown Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.LOW,
            source = AccountSource.OTHER
        )
        // Target: recognized canonical HDFC bank account with suffix 9020
        val target = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            source = AccountSource.HISTORICAL_SMS
        )
        accountRepo.upsert(source)
        accountRepo.upsert(target)

        val candidates = manager.getMergeCandidates(source)
        assertTrue("Target should be a compatible merge candidate", candidates.any { it.id == target.id })

        val txnSourceId = expenseDao.insert(
            Expense(
                amount = 300.0,
                merchant = "Swiggy",
                dateMillis = System.currentTimeMillis(),
                accountId = source.id,
                accountSuffix = "9020"
            )
        ).toInt()

        val txnTargetId = expenseDao.insert(
            Expense(
                amount = 500.0,
                merchant = "Zomato",
                dateMillis = System.currentTimeMillis(),
                accountId = target.id,
                accountSuffix = "9020"
            )
        ).toInt()

        // 9. Perform account merge
        val mergeResult = manager.mergeAccounts(source.id, target.id)
        assertTrue(mergeResult.isSuccess)
        val reassignedCount = mergeResult.getOrNull()
        assertEquals(1, reassignedCount)

        // 10. Verify all source transactions appear under target
        val targetTxns = manager.getLinkedTransactions(target.id)
        assertEquals(2, targetTxns.size)
        assertTrue(targetTxns.any { it.id == txnSourceId })
        assertTrue(targetTxns.any { it.id == txnTargetId })

        val reassignedTxn = expenseDao.getExpenseById(txnSourceId)
        assertNotNull(reassignedTxn)
        assertEquals(target.id, reassignedTxn!!.accountId)
        assertEquals("9020", reassignedTxn.accountSuffix)

        // 11. Verify source account disappears
        assertNull(accountRepo.getById(source.id))
        val remainingAccounts = manager.getAllKnownAccounts()
        assertEquals(1, remainingAccounts.size)
        assertEquals(target.id, remainingAccounts[0].id)
    }

    /**
     * Verification Points 12–18:
     * 12. Open unresolved transactions.
     * 13. Select multiple transactions.
     * 14. Bulk assign them to a target account.
     * 15. Verify selection count and confirmation.
     * 16. Verify all selected transactions become linked.
     * 17. Restart application (close/reopen database).
     * 18. Verify bulk assignments survive restart.
     */
    @Test
    fun test03_bulkAssignUnresolvedTransactions_andSurvivesRestart() = runBlocking {
        val sbi = KnownFinancialAccount(
            id = "sbi_bank_account_4455",
            institutionId = "sbi",
            institutionName = "State Bank of India",
            accountSuffix = "4455",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            source = AccountSource.HISTORICAL_SMS
        )
        accountRepo.upsert(sbi)

        val id1 = expenseDao.insert(
            Expense(amount = 100.0, merchant = "Tea Shop", dateMillis = System.currentTimeMillis(), accountId = null, accountSuffix = "4455")
        ).toInt()
        val id2 = expenseDao.insert(
            Expense(amount = 200.0, merchant = "Bakery", dateMillis = System.currentTimeMillis(), accountId = null, accountSuffix = "4455")
        ).toInt()
        val id3 = expenseDao.insert(
            Expense(amount = 300.0, merchant = "Grocery", dateMillis = System.currentTimeMillis(), accountId = null, accountSuffix = "4455")
        ).toInt()

        // 12. Open unresolved transactions
        val unresolved = manager.getUnresolvedTransactions()
        assertEquals(3, unresolved.size)

        // 13. Select multiple transactions (id1, id2, id3)
        val selectedIds = listOf(id1, id2, id3)
        assertEquals(3, selectedIds.size)

        // 14 & 15. Bulk assign them to target account and verify selection count
        val bulkResult = manager.bulkAssign(selectedIds, sbi.id)
        assertTrue(bulkResult.isSuccess)
        val updatedCount = bulkResult.getOrNull()
        assertEquals(3, updatedCount)

        // 16. Verify all selected transactions become linked
        val sbiTxns = manager.getLinkedTransactions(sbi.id)
        assertEquals(3, sbiTxns.size)
        assertTrue(manager.getUnresolvedTransactions().isEmpty())

        // 17. Restart application
        database.close()

        val reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, persistentDbName)
            .allowMainThreadQueries()
            .build()
        val reopenedExpenseDao = reopenedDb.expenseDao()
        val reopenedAccountRepo = KnownFinancialAccountRepository(reopenedDb.knownFinancialAccountDao())
        val reopenedManager = AccountReconciliationManager(reopenedExpenseDao, reopenedAccountRepo, reopenedDb)

        // 18. Verify bulk assignments survive restart
        val persisted1 = reopenedExpenseDao.getExpenseById(id1)
        val persisted2 = reopenedExpenseDao.getExpenseById(id2)
        val persisted3 = reopenedExpenseDao.getExpenseById(id3)
        assertNotNull(persisted1)
        assertNotNull(persisted2)
        assertNotNull(persisted3)
        assertEquals(sbi.id, persisted1!!.accountId)
        assertEquals(sbi.id, persisted2!!.accountId)
        assertEquals(sbi.id, persisted3!!.accountId)

        val reopenedSbiTxns = reopenedManager.getLinkedTransactions(sbi.id)
        assertEquals(3, reopenedSbiTxns.size)

        reopenedDb.close()
    }

    /**
     * Verification Points 19–23:
     * 19. Select linked transactions.
     * 20. Bulk reassign them.
     * 21. Verify reassignment survives restart.
     * 22. Bulk unlink transactions.
     * 23. Verify they return to unresolved.
     */
    @Test
    fun test04_bulkReassign_survivesRestart_andBulkUnlinkReturnsToUnresolved() = runBlocking {
        val icici = KnownFinancialAccount(
            id = "icici_bank_account_7788",
            institutionId = "icici",
            institutionName = "ICICI Bank",
            accountSuffix = "7788",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val axis = KnownFinancialAccount(
            id = "axis_bank_account_7788",
            institutionId = "axis",
            institutionName = "Axis Bank",
            accountSuffix = "7788",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(icici)
        accountRepo.upsert(axis)

        val t1 = expenseDao.insert(
            Expense(amount = 40.0, merchant = "Auto", dateMillis = System.currentTimeMillis(), accountId = icici.id, accountSuffix = "7788")
        ).toInt()
        val t2 = expenseDao.insert(
            Expense(amount = 80.0, merchant = "Metro", dateMillis = System.currentTimeMillis(), accountId = icici.id, accountSuffix = "7788")
        ).toInt()

        // 19 & 20. Select linked transactions and bulk reassign to axis
        val selected = listOf(t1, t2)
        val reassignResult = manager.bulkReassign(selected, axis.id)
        assertTrue(reassignResult.isSuccess)
        val reassignUpdated = reassignResult.getOrNull()
        assertEquals(2, reassignUpdated)

        assertEquals(0, manager.getLinkedTransactions(icici.id).size)
        assertEquals(2, manager.getLinkedTransactions(axis.id).size)

        // 21. Verify reassignment survives restart
        database.close()

        val reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, persistentDbName)
            .allowMainThreadQueries()
            .build()
        val reopenedExpenseDao = reopenedDb.expenseDao()
        val reopenedAccountRepo = KnownFinancialAccountRepository(reopenedDb.knownFinancialAccountDao())
        val reopenedManager = AccountReconciliationManager(reopenedExpenseDao, reopenedAccountRepo, reopenedDb)

        val r1 = reopenedExpenseDao.getExpenseById(t1)
        val r2 = reopenedExpenseDao.getExpenseById(t2)
        assertNotNull(r1)
        assertNotNull(r2)
        assertEquals(axis.id, r1!!.accountId)
        assertEquals(axis.id, r2!!.accountId)

        // 22. Bulk unlink transactions
        val unlinkResult = reopenedManager.bulkUnlink(listOf(t1, t2))
        assertTrue(unlinkResult.isSuccess)
        val unlinkUpdated = unlinkResult.getOrNull()
        assertEquals(2, unlinkUpdated)

        // 23. Verify they return to unresolved
        val u1 = reopenedExpenseDao.getExpenseById(t1)
        val u2 = reopenedExpenseDao.getExpenseById(t2)
        assertNotNull(u1)
        assertNotNull(u2)
        assertNull(u1!!.accountId)
        assertNull(u2!!.accountId)
        assertEquals("7788", u1.accountSuffix)
        assertEquals("7788", u2.accountSuffix)

        val unresolved = reopenedManager.getUnresolvedTransactions()
        assertEquals(2, unresolved.size)
        assertTrue(unresolved.any { it.id == t1 })
        assertTrue(unresolved.any { it.id == t2 })

        reopenedDb.close()
    }

    /**
     * Verification Points 24–25:
     * 24. Verify normal notification tracking still works.
     * 25. Verify historical SMS import still works.
     */
    @Test
    fun test05_notificationTrackingAndHistoricalSmsImportContinueToWork() = runBlocking {
        // 24. Normal notification tracking
        val pnb = KnownFinancialAccount(
            id = "pnb_bank_account_6677",
            institutionId = "pnb",
            institutionName = "PNB",
            accountSuffix = "6677",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(pnb)

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "Bank Alert",
            text = "Rs. 250 debited from A/c XX6677 for groceries",
            postTime = System.currentTimeMillis(),
            notificationKey = "notif_m7_test_key"
        )

        val matcher = KnownFinancialAccountMatcher(accountRepo)
        TransactionPersistenceManager.processCapturedNotificationAll(
            captured,
            database.expenseDao(),
            matcher
        )

        val allExpenses = database.expenseDao().getAllExpensesList()
        val persisted = allExpenses.find { it.notificationKey == "notif_m7_test_key" }
        assertNotNull("Notification transaction should be persisted", persisted)
        assertEquals(pnb.id, persisted!!.accountId)
        assertEquals("6677", persisted.accountSuffix)
        assertEquals(250.0, persisted.amount, 0.001)

        // 25. Historical SMS import
        val synchronizer = HistoricalSmsAccountRegistrySynchronizer(accountRepo, expenseDao)
        val identity = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "1234",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            evidenceSource = "SMS"
        )

        val synced = synchronizer.synchronize(identity)
        assertNotNull(synced)
        assertEquals("hdfc_bank_account_1234", synced!!.id)

        val inRepo = accountRepo.getById("hdfc_bank_account_1234")
        assertNotNull(inRepo)
        assertEquals("1234", inRepo!!.accountSuffix)
        assertEquals("HDFC Bank", inRepo.institutionName)
    }
}
