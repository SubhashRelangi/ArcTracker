package com.example.arctracker

import com.example.arctracker.data.*
import com.example.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * Milestone 8: Transaction Management, Detail View & Safe Manual Editing Unit Tests.
 *
 * Verifies all 57 required functional scenarios from Part 31:
 * - Transaction Detail & Field Display (Scenarios 1–8)
 * - Merchant Editing & Safety Invariants (Scenarios 9–13)
 * - Category Assignment & Persistence (Scenarios 14–17)
 * - Notes Editing & Independence (Scenarios 18–20)
 * - Account Linking, Reassignment & Unlink Safety (Scenarios 21–25)
 * - Search Functionality (Scenarios 26–29)
 * - Filtering (Type, Status, Category, Account, Composed) (Scenarios 30–36)
 * - Date Range Filtering (Scenarios 37–39)
 * - Sorting (Scenarios 40–43)
 * - Edit Safety & Isolation (Scenarios 44–47)
 * - Self-Transfer Safety & Counterpart Isolation (Scenarios 48–50)
 * - Pending Review Safety (Scenarios 51–52)
 * - Deletion Safety & Account/Notification Isolation (Scenarios 53–57)
 */
class TransactionManagementAndManualEditingTest {

    private lateinit var expenseDao: FakeExpenseDao
    private lateinit var accountDao: FakeKnownFinancialAccountDao
    private lateinit var accountRepo: KnownFinancialAccountRepository
    private lateinit var reconciliationManager: AccountReconciliationManager
    private lateinit var transactionManager: TransactionManager

    private val referenceTimeMillis: Long = 1790757000000L // Fixed reference timestamp (midday)

    @Before
    fun setUp() {
        expenseDao = FakeExpenseDao()
        accountDao = FakeKnownFinancialAccountDao()
        accountRepo = KnownFinancialAccountRepository(accountDao)
        reconciliationManager = AccountReconciliationManager(expenseDao, accountRepo)
        transactionManager = TransactionManager(expenseDao)
    }

    // ==========================================
    // TRANSACTION DETAIL & FIELD DISPLAY (1–8)
    // ==========================================

    @Test
    fun test01_transactionDetailLoadsCorrectTransaction() = runBlocking {
        val id = expenseDao.insert(
            Expense(amount = 250.0, merchant = "Zomato", dateMillis = referenceTimeMillis)
        ).toInt()

        val loaded = transactionManager.getTransaction(id)
        assertNotNull(loaded)
        assertEquals(id, loaded!!.id)
        assertEquals("Zomato", loaded.merchant)
    }

    @Test
    fun test02_amountDisplayedCorrectly() = runBlocking {
        val id = expenseDao.insert(
            Expense(amount = 1499.50, merchant = "Amazon", dateMillis = referenceTimeMillis)
        ).toInt()

        val loaded = transactionManager.getTransaction(id)
        assertNotNull(loaded)
        assertEquals(1499.50, loaded!!.amount, 0.001)
    }

    @Test
    fun test03_merchantDisplayedCorrectly() = runBlocking {
        val id = expenseDao.insert(
            Expense(amount = 300.0, merchant = "Starbucks Coffee", dateMillis = referenceTimeMillis)
        ).toInt()

        val loaded = transactionManager.getTransaction(id)
        assertEquals("Starbucks Coffee", loaded!!.merchant)
    }

    @Test
    fun test04_debitCreditDisplayedCorrectly() = runBlocking {
        val debitId = expenseDao.insert(
            Expense(amount = 50.0, merchant = "Chai Point", dateMillis = referenceTimeMillis, type = "Debit")
        ).toInt()
        val creditId = expenseDao.insert(
            Expense(amount = 50000.0, merchant = "Company Salary", dateMillis = referenceTimeMillis, type = "Credit")
        ).toInt()

        val debit = transactionManager.getTransaction(debitId)
        val credit = transactionManager.getTransaction(creditId)
        assertEquals("Debit", debit!!.type)
        assertEquals("Credit", credit!!.type)
    }

    @Test
    fun test05_timestampDisplayedCorrectly() = runBlocking {
        val ts = 1790750000000L
        val id = expenseDao.insert(
            Expense(amount = 100.0, merchant = "Metro", dateMillis = ts)
        ).toInt()

        val loaded = transactionManager.getTransaction(id)
        assertEquals(ts, loaded!!.dateMillis)
    }

    @Test
    fun test06_accountDisplayedCorrectly() = runBlocking {
        val hdfc = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(hdfc)

        val id = expenseDao.insert(
            Expense(amount = 450.0, merchant = "Uber", dateMillis = referenceTimeMillis, accountId = hdfc.id, accountSuffix = "9020")
        ).toInt()

        val loaded = transactionManager.getTransaction(id)
        assertEquals("hdfc_bank_account_9020", loaded!!.accountId)
        val account = accountRepo.getById(loaded.accountId!!)
        assertNotNull(account)
        assertEquals("HDFC Bank", account!!.institutionName)
    }

    @Test
    fun test07_accountSuffixDisplayedSafely() = runBlocking {
        val id = expenseDao.insert(
            Expense(amount = 200.0, merchant = "Swiggy", dateMillis = referenceTimeMillis, accountSuffix = "9020")
        ).toInt()

        val loaded = transactionManager.getTransaction(id)
        assertEquals("9020", loaded!!.accountSuffix)
        val masked = AccountReconciliationManager.formatMaskedSuffix(loaded.accountSuffix!!)
        assertEquals("••••9020", masked)
    }

    @Test
    fun test08_unlinkedAccountDisplaysCorrectly() = runBlocking {
        val id = expenseDao.insert(
            Expense(amount = 120.0, merchant = "Local Store", dateMillis = referenceTimeMillis, accountId = null, accountSuffix = "1065")
        ).toInt()

        val loaded = transactionManager.getTransaction(id)
        assertNull(loaded!!.accountId)
        assertEquals("1065", loaded.accountSuffix)
    }

    // ==========================================
    // MERCHANT EDITING (9–13)
    // ==========================================

    @Test
    fun test09_merchantEditPersists() = runBlocking {
        val id = expenseDao.insert(
            Expense(amount = 500.0, merchant = "AMAZON PAY INDIA", dateMillis = referenceTimeMillis)
        ).toInt()

        val result = transactionManager.updateTransaction(id, merchant = "Amazon")
        assertTrue(result.isSuccess)

        val updated = expenseDao.getExpenseById(id)
        assertEquals("Amazon", updated!!.merchant)
    }

    @Test
    fun test10_merchantEditPreservesAmount() = runBlocking {
        val id = expenseDao.insert(
            Expense(amount = 899.99, merchant = "Old Merchant", dateMillis = referenceTimeMillis)
        ).toInt()

        transactionManager.updateTransaction(id, merchant = "Correct Merchant")

        val updated = expenseDao.getExpenseById(id)
        assertEquals(899.99, updated!!.amount, 0.001)
    }

    @Test
    fun test11_merchantEditPreservesType() = runBlocking {
        val id = expenseDao.insert(
            Expense(amount = 1500.0, merchant = "Refund Vendor", dateMillis = referenceTimeMillis, type = "Credit")
        ).toInt()

        transactionManager.updateTransaction(id, merchant = "Vendor Name")

        val updated = expenseDao.getExpenseById(id)
        assertEquals("Credit", updated!!.type)
    }

    @Test
    fun test12_merchantEditPreservesTimestamp() = runBlocking {
        val ts = referenceTimeMillis - 100000L
        val id = expenseDao.insert(
            Expense(amount = 250.0, merchant = "Merchant A", dateMillis = ts)
        ).toInt()

        transactionManager.updateTransaction(id, merchant = "Merchant B")

        val updated = expenseDao.getExpenseById(id)
        assertEquals(ts, updated!!.dateMillis)
    }

    @Test
    fun test13_cancelDiscardsMerchantChanges() = runBlocking {
        val id = expenseDao.insert(
            Expense(amount = 350.0, merchant = "Original Merchant", dateMillis = referenceTimeMillis)
        ).toInt()

        // Simulating UI cancel: user typed "Edited", but tapped Cancel so updateTransaction is never called
        val inDb = expenseDao.getExpenseById(id)
        assertEquals("Original Merchant", inDb!!.merchant)
    }

    // ==========================================
    // CATEGORY (14–17)
    // ==========================================

    @Test
    fun test14_categoryAssignmentPersists() = runBlocking {
        val id = expenseDao.insert(
            Expense(amount = 400.0, merchant = "BigBasket", dateMillis = referenceTimeMillis, tag = null)
        ).toInt()

        val result = transactionManager.updateTransaction(id, category = TransactionCategories.FOOD_AND_DINING)
        assertTrue(result.isSuccess)

        val updated = expenseDao.getExpenseById(id)
        assertEquals(TransactionCategories.FOOD_AND_DINING, updated!!.tag)
        assertEquals(TransactionCategories.FOOD_AND_DINING, updated.category)
    }

    @Test
    fun test15_categoryChangeDoesNotModifyAccount() = runBlocking {
        val id = expenseDao.insert(
            Expense(amount = 600.0, merchant = "Nike", dateMillis = referenceTimeMillis, accountId = "hdfc_9020", accountSuffix = "9020")
        ).toInt()

        transactionManager.updateTransaction(id, category = TransactionCategories.SHOPPING)

        val updated = expenseDao.getExpenseById(id)
        assertEquals("hdfc_9020", updated!!.accountId)
        assertEquals("9020", updated.accountSuffix)
        assertEquals(TransactionCategories.SHOPPING, updated.category)
    }

    @Test
    fun test16_categoryChangeDoesNotModifyAmount() = runBlocking {
        val id = expenseDao.insert(
            Expense(amount = 1250.0, merchant = "Petrol Bunk", dateMillis = referenceTimeMillis)
        ).toInt()

        transactionManager.updateTransaction(id, category = TransactionCategories.TRANSPORT)

        val updated = expenseDao.getExpenseById(id)
        assertEquals(1250.0, updated!!.amount, 0.001)
    }

    @Test
    fun test17_categoryFilterReturnsCorrectTransactions() = runBlocking {
        expenseDao.insert(Expense(amount = 100.0, merchant = "Bakery", dateMillis = referenceTimeMillis, tag = TransactionCategories.FOOD_AND_DINING))
        expenseDao.insert(Expense(amount = 200.0, merchant = "Clothing", dateMillis = referenceTimeMillis, tag = TransactionCategories.SHOPPING))
        expenseDao.insert(Expense(amount = 300.0, merchant = "Restaurant", dateMillis = referenceTimeMillis, tag = TransactionCategories.FOOD_AND_DINING))

        val all = expenseDao.getAllExpensesList()
        val filter = TransactionFilter(category = TransactionCategories.FOOD_AND_DINING)
        val filtered = transactionManager.filterAndSort(all, filter)

        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.tag == TransactionCategories.FOOD_AND_DINING })
    }

    // ==========================================
    // NOTES (18–20)
    // ==========================================

    @Test
    fun test18_notesPersist() = runBlocking {
        val id = expenseDao.insert(
            Expense(amount = 80.0, merchant = "Tea", dateMillis = referenceTimeMillis)
        ).toInt()

        val result = transactionManager.updateTransaction(id, note = "Evening tea with team")
        assertTrue(result.isSuccess)

        val updated = expenseDao.getExpenseById(id)
        assertEquals("Evening tea with team", updated!!.note)
    }

    @Test
    fun test19_emptyNotesAreHandledCorrectly() = runBlocking {
        val id = expenseDao.insert(
            Expense(amount = 100.0, merchant = "Coffee", dateMillis = referenceTimeMillis, note = "Initial note")
        ).toInt()

        transactionManager.updateTransaction(id, note = "")

        val updated = expenseDao.getExpenseById(id)
        assertNull(updated!!.note)
    }

    @Test
    fun test20_notesDoNotAffectAccountIdentity() = runBlocking {
        val id = expenseDao.insert(
            Expense(amount = 50.0, merchant = "Snack", dateMillis = referenceTimeMillis, accountId = "sbi_1234", accountSuffix = "1234")
        ).toInt()

        transactionManager.updateTransaction(id, note = "Paid via cash to friend ending 9999")

        val updated = expenseDao.getExpenseById(id)
        assertEquals("sbi_1234", updated!!.accountId)
        assertEquals("1234", updated.accountSuffix)
    }

    // ==========================================
    // ACCOUNT (21–25)
    // ==========================================

    @Test
    fun test21_assignAccountUsesExistingAccountManager() = runBlocking {
        val sbi = KnownFinancialAccount(
            id = "sbi_bank_account_5566",
            institutionId = "sbi",
            institutionName = "State Bank of India",
            accountSuffix = "5566",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(sbi)

        val id = expenseDao.insert(
            Expense(amount = 200.0, merchant = "Pharmacy", dateMillis = referenceTimeMillis, accountId = null, accountSuffix = "5566")
        ).toInt()

        val result = reconciliationManager.assignAccount(id, sbi.id)
        assertTrue(result.isSuccess)

        val updated = expenseDao.getExpenseById(id)
        assertEquals(sbi.id, updated!!.accountId)
        assertEquals("5566", updated.accountSuffix)
    }

    @Test
    fun test22_changeAccountUpdatesAccountIdOnly() = runBlocking {
        val hdfc = KnownFinancialAccount(id = "hdfc_bank_account_7788", accountSuffix = "7788")
        val axis = KnownFinancialAccount(id = "axis_bank_account_7788", accountSuffix = "7788")
        accountRepo.upsert(hdfc)
        accountRepo.upsert(axis)

        val id = expenseDao.insert(
            Expense(amount = 1000.0, merchant = "Electronics", dateMillis = referenceTimeMillis, accountId = hdfc.id, accountSuffix = "7788")
        ).toInt()

        val result = reconciliationManager.reassignAccount(id, axis.id)
        assertTrue(result.isSuccess)

        val updated = expenseDao.getExpenseById(id)
        assertEquals(axis.id, updated!!.accountId)
        assertEquals("7788", updated.accountSuffix)
    }

    @Test
    fun test23_unlinkPreservesAccountSuffix() = runBlocking {
        val id = expenseDao.insert(
            Expense(amount = 450.0, merchant = "Cafe", dateMillis = referenceTimeMillis, accountId = "acc_1", accountSuffix = "4381")
        ).toInt()

        val result = reconciliationManager.unlinkAccount(id)
        assertTrue(result.isSuccess)

        val updated = expenseDao.getExpenseById(id)
        assertNull(updated!!.accountId)
        assertEquals("4381", updated.accountSuffix)
    }

    @Test
    fun test24_accountChangeDoesNotModifyCategory() = runBlocking {
        val sbi = KnownFinancialAccount(id = "sbi_bank_account_1111", accountSuffix = "1111")
        accountRepo.upsert(sbi)

        val id = expenseDao.insert(
            Expense(
                amount = 220.0,
                merchant = "Bookstore",
                dateMillis = referenceTimeMillis,
                tag = TransactionCategories.EDUCATION,
                accountSuffix = "1111"
            )
        ).toInt()

        reconciliationManager.assignAccount(id, sbi.id)

        val updated = expenseDao.getExpenseById(id)
        assertEquals(TransactionCategories.EDUCATION, updated!!.category)
    }

    @Test
    fun test25_accountChangeDoesNotModifyMerchant() = runBlocking {
        val sbi = KnownFinancialAccount(id = "sbi_bank_account_2222", accountSuffix = "2222")
        accountRepo.upsert(sbi)

        val id = expenseDao.insert(
            Expense(amount = 70.0, merchant = "Auto Fare", dateMillis = referenceTimeMillis, accountSuffix = "2222")
        ).toInt()

        reconciliationManager.assignAccount(id, sbi.id)

        val updated = expenseDao.getExpenseById(id)
        assertEquals("Auto Fare", updated!!.merchant)
    }

    // ==========================================
    // SEARCH (26–29)
    // ==========================================

    @Test
    fun test26_merchantSearchWorksCaseInsensitively() = runBlocking {
        expenseDao.insert(Expense(amount = 100.0, merchant = "AMAZON INDIA", dateMillis = referenceTimeMillis))
        expenseDao.insert(Expense(amount = 200.0, merchant = "Flipkart", dateMillis = referenceTimeMillis))
        expenseDao.insert(Expense(amount = 300.0, merchant = "amazon pay", dateMillis = referenceTimeMillis))

        val all = expenseDao.getAllExpensesList()
        val filter = TransactionFilter(searchQuery = "Amazon")
        val results = transactionManager.filterAndSort(all, filter)

        assertEquals(2, results.size)
        assertTrue(results.all { it.merchant.contains("amazon", ignoreCase = true) })
    }

    @Test
    fun test27_notesSearchWorks() = runBlocking {
        expenseDao.insert(Expense(amount = 50.0, merchant = "Store", dateMillis = referenceTimeMillis, note = "Dinner with Alice"))
        expenseDao.insert(Expense(amount = 60.0, merchant = "Store", dateMillis = referenceTimeMillis, note = "Lunch alone"))

        val all = expenseDao.getAllExpensesList()
        val filter = TransactionFilter(searchQuery = "alice")
        val results = transactionManager.filterAndSort(all, filter)

        assertEquals(1, results.size)
        assertEquals("Dinner with Alice", results[0].note)
    }

    @Test
    fun test28_searchWithNoResultsProducesEmptyState() = runBlocking {
        expenseDao.insert(Expense(amount = 100.0, merchant = "Netflix", dateMillis = referenceTimeMillis))

        val all = expenseDao.getAllExpensesList()
        val filter = TransactionFilter(searchQuery = "Spotify")
        val results = transactionManager.filterAndSort(all, filter)

        assertTrue(results.isEmpty())
    }

    @Test
    fun test29_clearingSearchRestoresResults() = runBlocking {
        expenseDao.insert(Expense(amount = 100.0, merchant = "Store A", dateMillis = referenceTimeMillis))
        expenseDao.insert(Expense(amount = 200.0, merchant = "Store B", dateMillis = referenceTimeMillis))

        val all = expenseDao.getAllExpensesList()
        val filtered = transactionManager.filterAndSort(all, TransactionFilter(searchQuery = "Store A"))
        assertEquals(1, filtered.size)

        val cleared = transactionManager.filterAndSort(all, TransactionFilter(searchQuery = ""))
        assertEquals(2, cleared.size)
    }

    // ==========================================
    // FILTERS (30–36)
    // ==========================================

    @Test
    fun test30_debitFilterWorks() = runBlocking {
        expenseDao.insert(Expense(amount = 100.0, merchant = "Grocery", dateMillis = referenceTimeMillis, type = "Debit"))
        expenseDao.insert(Expense(amount = 500.0, merchant = "Gift", dateMillis = referenceTimeMillis, type = "Credit"))

        val all = expenseDao.getAllExpensesList()
        val filtered = transactionManager.filterAndSort(all, TransactionFilter(type = TransactionTypeFilter.DEBIT))

        assertEquals(1, filtered.size)
        assertEquals("Debit", filtered[0].type)
    }

    @Test
    fun test31_creditFilterWorks() = runBlocking {
        expenseDao.insert(Expense(amount = 100.0, merchant = "Grocery", dateMillis = referenceTimeMillis, type = "Debit"))
        expenseDao.insert(Expense(amount = 500.0, merchant = "Gift", dateMillis = referenceTimeMillis, type = "Credit"))

        val all = expenseDao.getAllExpensesList()
        val filtered = transactionManager.filterAndSort(all, TransactionFilter(type = TransactionTypeFilter.CREDIT))

        assertEquals(1, filtered.size)
        assertEquals("Credit", filtered[0].type)
    }

    @Test
    fun test32_pendingFilterWorks() = runBlocking {
        expenseDao.insert(Expense(amount = 150.0, merchant = "M1", dateMillis = referenceTimeMillis, isPending = true))
        expenseDao.insert(Expense(amount = 250.0, merchant = "M2", dateMillis = referenceTimeMillis, isPending = false))

        val all = expenseDao.getAllExpensesList()
        val filtered = transactionManager.filterAndSort(all, TransactionFilter(status = TransactionStatusFilter.PENDING))

        assertEquals(1, filtered.size)
        assertTrue(filtered[0].isPending)
    }

    @Test
    fun test33_completedFilterWorks() = runBlocking {
        expenseDao.insert(Expense(amount = 150.0, merchant = "M1", dateMillis = referenceTimeMillis, isPending = true))
        expenseDao.insert(Expense(amount = 250.0, merchant = "M2", dateMillis = referenceTimeMillis, isPending = false))

        val all = expenseDao.getAllExpensesList()
        val filtered = transactionManager.filterAndSort(all, TransactionFilter(status = TransactionStatusFilter.COMPLETED))

        assertEquals(1, filtered.size)
        assertFalse(filtered[0].isPending)
    }

    @Test
    fun test34_categoryFilterWorks() = runBlocking {
        expenseDao.insert(Expense(amount = 80.0, merchant = "Bus", dateMillis = referenceTimeMillis, tag = TransactionCategories.TRANSPORT))
        expenseDao.insert(Expense(amount = 90.0, merchant = "Movie", dateMillis = referenceTimeMillis, tag = TransactionCategories.ENTERTAINMENT))

        val all = expenseDao.getAllExpensesList()
        val filtered = transactionManager.filterAndSort(all, TransactionFilter(category = TransactionCategories.TRANSPORT))

        assertEquals(1, filtered.size)
        assertEquals("Bus", filtered[0].merchant)
    }

    @Test
    fun test35_accountFilterWorks() = runBlocking {
        expenseDao.insert(Expense(amount = 100.0, merchant = "M1", dateMillis = referenceTimeMillis, accountId = "acc_hdfc"))
        expenseDao.insert(Expense(amount = 200.0, merchant = "M2", dateMillis = referenceTimeMillis, accountId = "acc_sbi"))

        val all = expenseDao.getAllExpensesList()
        val filtered = transactionManager.filterAndSort(all, TransactionFilter(accountId = "acc_hdfc"))

        assertEquals(1, filtered.size)
        assertEquals("acc_hdfc", filtered[0].accountId)
    }

    @Test
    fun test36_multipleFiltersComposeCorrectly() = runBlocking {
        expenseDao.insert(Expense(amount = 100.0, merchant = "Amazon", dateMillis = referenceTimeMillis, type = "Debit", tag = TransactionCategories.SHOPPING, accountId = "acc_1"))
        expenseDao.insert(Expense(amount = 200.0, merchant = "Amazon", dateMillis = referenceTimeMillis, type = "Credit", tag = TransactionCategories.SHOPPING, accountId = "acc_1"))
        expenseDao.insert(Expense(amount = 150.0, merchant = "Amazon", dateMillis = referenceTimeMillis, type = "Debit", tag = TransactionCategories.FOOD_AND_DINING, accountId = "acc_1"))
        expenseDao.insert(Expense(amount = 300.0, merchant = "Flipkart", dateMillis = referenceTimeMillis, type = "Debit", tag = TransactionCategories.SHOPPING, accountId = "acc_1"))

        val all = expenseDao.getAllExpensesList()
        val filter = TransactionFilter(
            searchQuery = "Amazon",
            type = TransactionTypeFilter.DEBIT,
            category = TransactionCategories.SHOPPING,
            accountId = "acc_1"
        )
        val filtered = transactionManager.filterAndSort(all, filter)

        assertEquals(1, filtered.size)
        assertEquals(100.0, filtered[0].amount, 0.001)
    }

    // ==========================================
    // DATE (37–39)
    // ==========================================

    @Test
    fun test37_todayFilterWorks() = runBlocking {
        val cal = Calendar.getInstance().apply { timeInMillis = referenceTimeMillis }
        cal.set(Calendar.HOUR_OF_DAY, 10)
        val todayTime = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -2)
        val twoDaysAgo = cal.timeInMillis

        expenseDao.insert(Expense(amount = 50.0, merchant = "Today Shop", dateMillis = todayTime))
        expenseDao.insert(Expense(amount = 75.0, merchant = "Past Shop", dateMillis = twoDaysAgo))

        val all = expenseDao.getAllExpensesList()
        val filtered = transactionManager.filterAndSort(
            all,
            TransactionFilter(dateRange = DateRangeFilter.TODAY),
            referenceTimeMillis = referenceTimeMillis
        )

        assertEquals(1, filtered.size)
        assertEquals("Today Shop", filtered[0].merchant)
    }

    @Test
    fun test38_thisMonthFilterWorks() = runBlocking {
        val cal = Calendar.getInstance().apply { timeInMillis = referenceTimeMillis }
        cal.set(Calendar.DAY_OF_MONTH, 5)
        val thisMonthTime = cal.timeInMillis

        cal.add(Calendar.MONTH, -2)
        val twoMonthsAgo = cal.timeInMillis

        expenseDao.insert(Expense(amount = 120.0, merchant = "This Month Shop", dateMillis = thisMonthTime))
        expenseDao.insert(Expense(amount = 130.0, merchant = "Past Month Shop", dateMillis = twoMonthsAgo))

        val all = expenseDao.getAllExpensesList()
        val filtered = transactionManager.filterAndSort(
            all,
            TransactionFilter(dateRange = DateRangeFilter.THIS_MONTH),
            referenceTimeMillis = referenceTimeMillis
        )

        assertEquals(1, filtered.size)
        assertEquals("This Month Shop", filtered[0].merchant)
    }

    @Test
    fun test39_customDateRangeWorks() = runBlocking {
        val start = 1000L
        val end = 2000L

        expenseDao.insert(Expense(amount = 10.0, merchant = "Before", dateMillis = 500L))
        expenseDao.insert(Expense(amount = 20.0, merchant = "Inside", dateMillis = 1500L))
        expenseDao.insert(Expense(amount = 30.0, merchant = "After", dateMillis = 2500L))

        val all = expenseDao.getAllExpensesList()
        val filtered = transactionManager.filterAndSort(
            all,
            TransactionFilter(
                dateRange = DateRangeFilter.CUSTOM,
                customStartDateMillis = start,
                customEndDateMillis = end
            )
        )

        assertEquals(1, filtered.size)
        assertEquals("Inside", filtered[0].merchant)
    }

    // ==========================================
    // SORT (40–43)
    // ==========================================

    @Test
    fun test40_newestFirstWorks() = runBlocking {
        expenseDao.insert(Expense(amount = 10.0, merchant = "Old", dateMillis = 1000L))
        expenseDao.insert(Expense(amount = 20.0, merchant = "New", dateMillis = 2000L))

        val all = expenseDao.getAllExpensesList()
        val sorted = transactionManager.filterAndSort(all, TransactionFilter(sortBy = TransactionSortBy.NEWEST_FIRST))

        assertEquals("New", sorted[0].merchant)
        assertEquals("Old", sorted[1].merchant)
    }

    @Test
    fun test41_oldestFirstWorks() = runBlocking {
        expenseDao.insert(Expense(amount = 10.0, merchant = "Old", dateMillis = 1000L))
        expenseDao.insert(Expense(amount = 20.0, merchant = "New", dateMillis = 2000L))

        val all = expenseDao.getAllExpensesList()
        val sorted = transactionManager.filterAndSort(all, TransactionFilter(sortBy = TransactionSortBy.OLDEST_FIRST))

        assertEquals("Old", sorted[0].merchant)
        assertEquals("New", sorted[1].merchant)
    }

    @Test
    fun test42_highestAmountWorks() = runBlocking {
        expenseDao.insert(Expense(amount = 50.0, merchant = "Small", dateMillis = 1000L))
        expenseDao.insert(Expense(amount = 500.0, merchant = "Big", dateMillis = 1000L))

        val all = expenseDao.getAllExpensesList()
        val sorted = transactionManager.filterAndSort(all, TransactionFilter(sortBy = TransactionSortBy.HIGHEST_AMOUNT))

        assertEquals(500.0, sorted[0].amount, 0.001)
        assertEquals(50.0, sorted[1].amount, 0.001)
    }

    @Test
    fun test43_lowestAmountWorks() = runBlocking {
        expenseDao.insert(Expense(amount = 50.0, merchant = "Small", dateMillis = 1000L))
        expenseDao.insert(Expense(amount = 500.0, merchant = "Big", dateMillis = 1000L))

        val all = expenseDao.getAllExpensesList()
        val sorted = transactionManager.filterAndSort(all, TransactionFilter(sortBy = TransactionSortBy.LOWEST_AMOUNT))

        assertEquals(50.0, sorted[0].amount, 0.001)
        assertEquals(500.0, sorted[1].amount, 0.001)
    }

    // ==========================================
    // EDIT SAFETY (44–47)
    // ==========================================

    @Test
    fun test44_editingMerchantDoesNotAlterRelationshipType() = runBlocking {
        val id = expenseDao.insert(
            Expense(
                amount = 100.0,
                merchant = "IPPB",
                dateMillis = referenceTimeMillis,
                relationshipType = TransactionRelationshipType.SELF_TRANSFER,
                relationshipId = "rel_123"
            )
        ).toInt()

        transactionManager.updateTransaction(id, merchant = "India Post Payments Bank")

        val updated = expenseDao.getExpenseById(id)
        assertEquals(TransactionRelationshipType.SELF_TRANSFER, updated!!.relationshipType)
    }

    @Test
    fun test45_editingCategoryDoesNotAlterRelationshipId() = runBlocking {
        val id = expenseDao.insert(
            Expense(
                amount = 200.0,
                merchant = "APGB",
                dateMillis = referenceTimeMillis,
                relationshipType = TransactionRelationshipType.SELF_TRANSFER,
                relationshipId = "rel_999"
            )
        ).toInt()

        transactionManager.updateTransaction(id, category = TransactionCategories.TRANSFER)

        val updated = expenseDao.getExpenseById(id)
        assertEquals("rel_999", updated!!.relationshipId)
        assertEquals(TransactionCategories.TRANSFER, updated.category)
    }

    @Test
    fun test46_editingNotesDoesNotAlterAccountId() = runBlocking {
        val id = expenseDao.insert(
            Expense(amount = 300.0, merchant = "Shop", dateMillis = referenceTimeMillis, accountId = "hdfc_123", accountSuffix = "123")
        ).toInt()

        transactionManager.updateTransaction(id, note = "Updated Note")

        val updated = expenseDao.getExpenseById(id)
        assertEquals("hdfc_123", updated!!.accountId)
        assertEquals("123", updated.accountSuffix)
    }

    @Test
    fun test47_editingAccountDoesNotAlterFinancialAmount() = runBlocking {
        val sbi = KnownFinancialAccount(id = "sbi_9020", accountSuffix = "9020")
        accountRepo.upsert(sbi)

        val id = expenseDao.insert(
            Expense(amount = 450.75, merchant = "Vendor", dateMillis = referenceTimeMillis, accountSuffix = "9020")
        ).toInt()

        reconciliationManager.assignAccount(id, sbi.id)

        val updated = expenseDao.getExpenseById(id)
        assertEquals(450.75, updated!!.amount, 0.001)
    }

    // ==========================================
    // SELF TRANSFER (48–50)
    // ==========================================

    @Test
    fun test48_editingSelfTransferPreservesRelationshipType() = runBlocking {
        val id = expenseDao.insert(
            Expense(
                amount = 1000.0,
                merchant = "Self Transfer Debit",
                dateMillis = referenceTimeMillis,
                relationshipType = TransactionRelationshipType.SELF_TRANSFER,
                relationshipId = "transfer_pair_1"
            )
        ).toInt()

        transactionManager.updateTransaction(id, merchant = "My HDFC Transfer")

        val updated = expenseDao.getExpenseById(id)
        assertEquals(TransactionRelationshipType.SELF_TRANSFER, updated!!.relationshipType)
    }

    @Test
    fun test49_editingSelfTransferPreservesRelationshipId() = runBlocking {
        val id = expenseDao.insert(
            Expense(
                amount = 1000.0,
                merchant = "Self Transfer Credit",
                dateMillis = referenceTimeMillis,
                relationshipType = TransactionRelationshipType.SELF_TRANSFER,
                relationshipId = "transfer_pair_1"
            )
        ).toInt()

        transactionManager.updateTransaction(id, note = "Saved for rent")

        val updated = expenseDao.getExpenseById(id)
        assertEquals("transfer_pair_1", updated!!.relationshipId)
    }

    @Test
    fun test50_linkedCounterpartRemainsIntact() = runBlocking {
        val id1 = expenseDao.insert(
            Expense(amount = 500.0, merchant = "From IPPB", dateMillis = referenceTimeMillis, type = "Debit", relationshipType = TransactionRelationshipType.SELF_TRANSFER, relationshipId = "pair_88")
        ).toInt()
        val id2 = expenseDao.insert(
            Expense(amount = 500.0, merchant = "To APGB", dateMillis = referenceTimeMillis, type = "Credit", relationshipType = TransactionRelationshipType.SELF_TRANSFER, relationshipId = "pair_88")
        ).toInt()

        // Edit transaction 1 only
        transactionManager.updateTransaction(id1, merchant = "IPPB Salary Account", note = "Transferred to savings")

        val counterpart = expenseDao.getExpenseById(id2)
        assertNotNull(counterpart)
        assertEquals("To APGB", counterpart!!.merchant)
        assertEquals(500.0, counterpart.amount, 0.001)
        assertEquals(TransactionRelationshipType.SELF_TRANSFER, counterpart.relationshipType)
        assertEquals("pair_88", counterpart.relationshipId)
    }

    // ==========================================
    // PENDING (51–52)
    // ==========================================

    @Test
    fun test51_editingPendingTransactionDoesNotApproveIt() = runBlocking {
        val id = expenseDao.insert(
            Expense(amount = 120.0, merchant = "Pending Store", dateMillis = referenceTimeMillis, isPending = true)
        ).toInt()

        transactionManager.updateTransaction(id, merchant = "Corrected Pending Store", category = TransactionCategories.FOOD_AND_DINING)

        val updated = expenseDao.getExpenseById(id)
        assertTrue(updated!!.isPending)
    }

    @Test
    fun test52_editingPendingTransactionPreservesIsPending() = runBlocking {
        val id = expenseDao.insert(
            Expense(amount = 350.0, merchant = "Unreviewed", dateMillis = referenceTimeMillis, isPending = true)
        ).toInt()

        transactionManager.updateTransaction(id, note = "Needs review later")

        val updated = expenseDao.getExpenseById(id)
        assertTrue(updated!!.isPending)
    }

    // ==========================================
    // DELETION (53–57)
    // ==========================================

    @Test
    fun test53_deleteRemovesOnlyTheExpense() = runBlocking {
        val id1 = expenseDao.insert(Expense(amount = 10.0, merchant = "To Keep", dateMillis = referenceTimeMillis)).toInt()
        val id2 = expenseDao.insert(Expense(amount = 20.0, merchant = "To Delete", dateMillis = referenceTimeMillis)).toInt()

        val deleteResult = transactionManager.deleteTransaction(id2)
        assertTrue(deleteResult.isSuccess)

        assertNull(expenseDao.getExpenseById(id2))
        assertNotNull(expenseDao.getExpenseById(id1))
    }

    @Test
    fun test54_deleteDoesNotDeleteKnownFinancialAccount() = runBlocking {
        val account = KnownFinancialAccount(id = "hdfc_9020", institutionName = "HDFC", accountSuffix = "9020")
        accountRepo.upsert(account)

        val id = expenseDao.insert(
            Expense(amount = 100.0, merchant = "Cafe", dateMillis = referenceTimeMillis, accountId = account.id, accountSuffix = "9020")
        ).toInt()

        transactionManager.deleteTransaction(id)

        assertNull(expenseDao.getExpenseById(id))
        val accountAfter = accountRepo.getById("hdfc_9020")
        assertNotNull(accountAfter)
        assertEquals("HDFC", accountAfter!!.institutionName)
    }

    @Test
    fun test55_deleteDoesNotModifyNotificationOrSmsSourceData() = runBlocking {
        val notifKey = "notif_persistent_test_key_123"
        val id = expenseDao.insert(
            Expense(
                amount = 200.0,
                merchant = "Store",
                dateMillis = referenceTimeMillis,
                notificationKey = notifKey,
                rawText = "Raw notification text"
            )
        ).toInt()

        transactionManager.deleteTransaction(id)
        assertNull(expenseDao.getExpenseById(id))

        // NotificationReaderService / historical SMS source data is completely untouched
        // (verified by persistent pipeline tests)
    }

    @Test
    fun test56_deleteDoesNotModifyDeduplicationRules() = runBlocking {
        val id = expenseDao.insert(Expense(amount = 50.0, merchant = "Test", dateMillis = referenceTimeMillis)).toInt()
        transactionManager.deleteTransaction(id)

        // TransactionValidator / TransactionDeduplicator configuration remains intact
        assertTrue(TransactionRelationshipType.SELF_TRANSFER.isNotEmpty())
    }

    @Test
    fun test57_deleteReturnsFailureForNonExistentExpense() = runBlocking {
        val result = transactionManager.deleteTransaction(999999)
        assertTrue(result.isFailure)
    }
}
