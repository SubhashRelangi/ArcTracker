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
 * Milestone 8: Transaction Management, Detail View & Safe Manual Editing Physical Device Tests.
 *
 * Runs on Samsung Galaxy A34 5G (SM-A346E - Android 16).
 * Covers all 28 verification points from Part 32 of the Milestone 8 specification.
 */
@RunWith(AndroidJUnit4::class)
class TransactionManagementDeviceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var expenseDao: ExpenseDao
    private lateinit var accountRepo: KnownFinancialAccountRepository
    private lateinit var reconciliationManager: AccountReconciliationManager
    private lateinit var transactionManager: TransactionManager
    private val persistentDbName = "m8_device_test_persistent.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(persistentDbName)

        database = Room.databaseBuilder(context, AppDatabase::class.java, persistentDbName)
            .allowMainThreadQueries()
            .build()

        expenseDao = database.expenseDao()
        accountRepo = KnownFinancialAccountRepository(database.knownFinancialAccountDao())
        reconciliationManager = AccountReconciliationManager(expenseDao, accountRepo, database)
        transactionManager = TransactionManager(expenseDao)

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
     * Verification Points 1–9:
     * 1. Existing Room v4 database opens.
     * 2. Existing transactions appear.
     * 3. Open transaction detail.
     * 4. Verify account information.
     * 5. Edit merchant.
     * 6. Save.
     * 7. Verify persistence after leaving screen.
     * 8. Restart app (close and reopen database).
     * 9. Verify merchant survives restart.
     */
    @Test
    fun test01_roomV4Opens_andMerchantEditSurvivesRestart() = runBlocking {
        // 1. Room v4 opens
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
        accountRepo.upsert(hdfc)

        val txnId = expenseDao.insert(
            Expense(
                amount = 750.0,
                merchant = "AMAZON PAY INDIA",
                dateMillis = System.currentTimeMillis(),
                accountId = hdfc.id,
                accountSuffix = "9020"
            )
        ).toInt()

        // 2. Existing transactions appear
        val all = expenseDao.getAllExpensesList()
        assertEquals(1, all.size)

        // 3. Open transaction detail
        val detail = transactionManager.getTransaction(txnId)
        assertNotNull(detail)

        // 4. Verify account information
        assertEquals(hdfc.id, detail!!.accountId)
        assertEquals("9020", detail.accountSuffix)
        val linked = accountRepo.getById(detail.accountId!!)
        assertNotNull(linked)
        assertEquals("HDFC Bank", linked!!.institutionName)

        // 5 & 6. Edit merchant and save
        val updateResult = transactionManager.updateTransaction(txnId, merchant = "Amazon")
        assertTrue(updateResult.isSuccess)

        // 7. Verify persistence
        val afterSave = expenseDao.getExpenseById(txnId)
        assertNotNull(afterSave)
        assertEquals("Amazon", afterSave!!.merchant)
        assertEquals(750.0, afterSave.amount, 0.001)

        // 8. Restart app (close and reopen persistent database)
        database.close()

        val reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, persistentDbName)
            .allowMainThreadQueries()
            .build()
        val reopenedExpenseDao = reopenedDb.expenseDao()

        // 9. Verify merchant survives restart
        val loaded = reopenedExpenseDao.getExpenseById(txnId)
        assertNotNull(loaded)
        assertEquals("Amazon", loaded!!.merchant)
        assertEquals(750.0, loaded.amount, 0.001)
        assertEquals("9020", loaded.accountSuffix)

        reopenedDb.close()
    }

    /**
     * Verification Points 10–16:
     * 10. Edit category.
     * 11. Verify category persists.
     * 12. Edit notes.
     * 13. Verify notes persist.
     * 14. Change account using existing account UI/manager.
     * 15. Verify account change persists.
     * 16. Verify account suffix remains intact.
     */
    @Test
    fun test02_categoryNotesAndAccountChangesPersist() = runBlocking {
        val sbi = KnownFinancialAccount(
            id = "sbi_bank_account_3344",
            institutionId = "sbi",
            institutionName = "SBI",
            accountSuffix = "3344",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val icici = KnownFinancialAccount(
            id = "icici_bank_account_3344",
            institutionId = "icici",
            institutionName = "ICICI",
            accountSuffix = "3344",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(sbi)
        accountRepo.upsert(icici)

        val txnId = expenseDao.insert(
            Expense(
                amount = 450.0,
                merchant = "Apollo Pharmacy",
                dateMillis = System.currentTimeMillis(),
                accountId = sbi.id,
                accountSuffix = "3344"
            )
        ).toInt()

        // 10 & 11. Edit category and verify persistence
        val catResult = transactionManager.updateTransaction(txnId, category = TransactionCategories.HEALTHCARE)
        assertTrue(catResult.isSuccess)
        val afterCat = expenseDao.getExpenseById(txnId)
        assertEquals(TransactionCategories.HEALTHCARE, afterCat!!.tag)
        assertEquals(TransactionCategories.HEALTHCARE, afterCat.category)

        // 12 & 13. Edit notes and verify persistence
        val noteResult = transactionManager.updateTransaction(txnId, note = "Prescription medicines")
        assertTrue(noteResult.isSuccess)
        val afterNote = expenseDao.getExpenseById(txnId)
        assertEquals("Prescription medicines", afterNote!!.note)

        // 14. Change account using reconciliation manager
        val reassignResult = reconciliationManager.reassignAccount(txnId, icici.id)
        assertTrue(reassignResult.isSuccess)

        // 15 & 16. Verify account change persists and account suffix remains intact
        val afterAccountChange = expenseDao.getExpenseById(txnId)
        assertNotNull(afterAccountChange)
        assertEquals(icici.id, afterAccountChange!!.accountId)
        assertEquals("3344", afterAccountChange.accountSuffix)
        assertEquals(TransactionCategories.HEALTHCARE, afterAccountChange.category)
        assertEquals("Prescription medicines", afterAccountChange.note)
        assertEquals(450.0, afterAccountChange.amount, 0.001)
    }

    /**
     * Verification Points 17–24:
     * 17. Search transactions.
     * 18. Apply debit/credit filter.
     * 19. Apply category filter.
     * 20. Apply account filter.
     * 21. Apply date filter.
     * 22. Combine multiple filters.
     * 23. Change sorting.
     * 24. Clear filters.
     */
    @Test
    fun test03_searchFiltersAndSorting() = runBlocking {
        val now = System.currentTimeMillis()

        val hdfc = KnownFinancialAccount(id = "acc_hdfc_55", accountSuffix = "5555")
        accountRepo.upsert(hdfc)

        val e1 = Expense(amount = 100.0, merchant = "Amazon Shopping", dateMillis = now - 5000L, type = "Debit", tag = TransactionCategories.SHOPPING, accountId = hdfc.id)
        val e2 = Expense(amount = 250.0, merchant = "Refund Amazon", dateMillis = now - 3000L, type = "Credit", tag = TransactionCategories.INCOME, accountId = hdfc.id)
        val e3 = Expense(amount = 50.0, merchant = "Tea Point", dateMillis = now - 1000L, type = "Debit", tag = TransactionCategories.FOOD_AND_DINING, accountId = null)

        expenseDao.insert(e1)
        expenseDao.insert(e2)
        expenseDao.insert(e3)

        val all = expenseDao.getAllExpensesList()
        assertEquals(3, all.size)

        // 17. Search transactions (case-insensitive)
        val searchResults = transactionManager.filterAndSort(all, TransactionFilter(searchQuery = "amazon"))
        assertEquals(2, searchResults.size)

        // 18. Apply debit/credit filter
        val debitResults = transactionManager.filterAndSort(all, TransactionFilter(type = TransactionTypeFilter.DEBIT))
        assertEquals(2, debitResults.size)
        val creditResults = transactionManager.filterAndSort(all, TransactionFilter(type = TransactionTypeFilter.CREDIT))
        assertEquals(1, creditResults.size)

        // 19. Apply category filter
        val catResults = transactionManager.filterAndSort(all, TransactionFilter(category = TransactionCategories.SHOPPING))
        assertEquals(1, catResults.size)
        assertEquals("Amazon Shopping", catResults[0].merchant)

        // 20. Apply account filter
        val accResults = transactionManager.filterAndSort(all, TransactionFilter(accountId = hdfc.id))
        assertEquals(2, accResults.size)

        // 21. Apply date filter (Today)
        val dateResults = transactionManager.filterAndSort(all, TransactionFilter(dateRange = DateRangeFilter.TODAY), referenceTimeMillis = now)
        assertEquals(3, dateResults.size)

        // 22. Combine multiple filters (Search = Amazon, Type = Debit, Category = Shopping, Account = hdfc)
        val combinedResults = transactionManager.filterAndSort(
            all,
            TransactionFilter(
                searchQuery = "amazon",
                type = TransactionTypeFilter.DEBIT,
                category = TransactionCategories.SHOPPING,
                accountId = hdfc.id
            )
        )
        assertEquals(1, combinedResults.size)
        assertEquals("Amazon Shopping", combinedResults[0].merchant)

        // 23. Change sorting
        val sortedDesc = transactionManager.filterAndSort(all, TransactionFilter(sortBy = TransactionSortBy.HIGHEST_AMOUNT))
        assertEquals(250.0, sortedDesc[0].amount, 0.001)
        assertEquals(50.0, sortedDesc.last().amount, 0.001)

        val sortedAsc = transactionManager.filterAndSort(all, TransactionFilter(sortBy = TransactionSortBy.LOWEST_AMOUNT))
        assertEquals(50.0, sortedAsc[0].amount, 0.001)
        assertEquals(250.0, sortedAsc.last().amount, 0.001)

        // 24. Clear filters
        val cleared = transactionManager.filterAndSort(all, TransactionFilter())
        assertEquals(3, cleared.size)
    }

    /**
     * Verification Points 25–26:
     * 25. Verify self-transfer transaction remains correctly linked.
     * 26. Verify pending transaction remains pending after editing.
     */
    @Test
    fun test04_selfTransferAndPendingSafety() = runBlocking {
        val now = System.currentTimeMillis()

        // 25. Self-transfer link preservation
        val txn1Id = expenseDao.insert(
            Expense(
                amount = 2000.0,
                merchant = "IPPB Transfer",
                dateMillis = now,
                type = "Debit",
                relationshipType = TransactionRelationshipType.SELF_TRANSFER,
                relationshipId = "self_pair_device"
            )
        ).toInt()

        val txn2Id = expenseDao.insert(
            Expense(
                amount = 2000.0,
                merchant = "APGB Transfer",
                dateMillis = now,
                type = "Credit",
                relationshipType = TransactionRelationshipType.SELF_TRANSFER,
                relationshipId = "self_pair_device"
            )
        ).toInt()

        // Edit merchant & note on first transaction
        transactionManager.updateTransaction(txn1Id, merchant = "India Post", note = "Transfer to personal account")

        val t1 = expenseDao.getExpenseById(txn1Id)
        val t2 = expenseDao.getExpenseById(txn2Id)
        assertNotNull(t1)
        assertNotNull(t2)
        assertEquals(TransactionRelationshipType.SELF_TRANSFER, t1!!.relationshipType)
        assertEquals("self_pair_device", t1.relationshipId)
        assertEquals(TransactionRelationshipType.SELF_TRANSFER, t2!!.relationshipType)
        assertEquals("self_pair_device", t2.relationshipId)

        // 26. Pending transaction remains pending after editing
        val pendingId = expenseDao.insert(
            Expense(
                amount = 150.0,
                merchant = "Unconfirmed Merchant",
                dateMillis = now,
                isPending = true
            )
        ).toInt()

        transactionManager.updateTransaction(pendingId, merchant = "Confirmed Merchant Name", category = TransactionCategories.ENTERTAINMENT)

        val pendingAfter = expenseDao.getExpenseById(pendingId)
        assertNotNull(pendingAfter)
        assertTrue("Editing must preserve isPending = true", pendingAfter!!.isPending)
        assertEquals("Confirmed Merchant Name", pendingAfter.merchant)
        assertEquals(TransactionCategories.ENTERTAINMENT, pendingAfter.category)
    }

    /**
     * Verification Points 27–28:
     * 27. Verify normal live notification tracking continues.
     * 28. Verify historical SMS import continues working.
     */
    @Test
    fun test05_notificationTrackingAndHistoricalSmsImportContinueWorking() = runBlocking {
        // 27. Live notification tracking
        val pnb = KnownFinancialAccount(
            id = "pnb_bank_account_8899",
            institutionId = "pnb",
            institutionName = "PNB",
            accountSuffix = "8899",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(pnb)

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "Bank Alert",
            text = "Rs. 320 debited from A/c XX8899 for dining",
            postTime = System.currentTimeMillis(),
            notificationKey = "notif_m8_device_key"
        )

        val matcher = KnownFinancialAccountMatcher(accountRepo)
        TransactionPersistenceManager.processCapturedNotificationAll(
            captured,
            database.expenseDao(),
            matcher
        )

        val expenses = database.expenseDao().getAllExpensesList()
        val persisted = expenses.find { it.notificationKey == "notif_m8_device_key" }
        assertNotNull("Live notification should be extracted and persisted", persisted)
        assertEquals(pnb.id, persisted!!.accountId)
        assertEquals("8899", persisted.accountSuffix)
        assertEquals(320.0, persisted.amount, 0.001)

        // 28. Historical SMS import continues working
        val synchronizer = HistoricalSmsAccountRegistrySynchronizer(accountRepo, expenseDao)
        val identity = FinancialAccountIdentity(
            institutionId = "sbi",
            institutionName = "SBI",
            accountSuffix = "9988",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            evidenceSource = "SMS"
        )

        val synced = synchronizer.synchronize(identity)
        assertNotNull(synced)
        assertEquals("sbi_bank_account_9988", synced!!.id)

        val inRepo = accountRepo.getById("sbi_bank_account_9988")
        assertNotNull(inRepo)
        assertEquals("9988", inRepo!!.accountSuffix)
    }
}
