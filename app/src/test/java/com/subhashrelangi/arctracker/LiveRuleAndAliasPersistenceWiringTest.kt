package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Pre-M13 / M11 Live Rule & Merchant Alias Wiring Verification Tests.
 *
 * Verifies that:
 * 1. Live notification persistence correctly receives active CategoryRuleManager and MerchantAliasManager data.
 * 2. Aliases resolve canonical merchant identities for category inference without altering Expense.merchant.
 * 3. User rules take priority over built-in inference rules.
 * 4. Missing/failing rules or aliases never reject or invalidate transactions.
 * 5. Financial, account, and relationship metadata remain completely intact.
 * 6. Historical SMS and M12 HistoricalRuleApplier flows remain fully functional.
 * 7. USER_ASSIGNED categories remain protected from overwrite.
 */
class LiveRuleAndAliasPersistenceWiringTest {

    private lateinit var expenseDao: FakeExpenseDao
    private lateinit var categoryDao: FakeTransactionCategoryDao
    private lateinit var ruleDao: FakeUserCategoryRuleDao
    private lateinit var aliasDao: FakeMerchantAliasDao
    private lateinit var accountDao: FakeKnownFinancialAccountDao

    private lateinit var categoryManager: CategoryManager
    private lateinit var ruleManager: CategoryRuleManager
    private lateinit var aliasManager: MerchantAliasManager
    private lateinit var accountRepo: KnownFinancialAccountRepository
    private lateinit var accountMatcher: KnownFinancialAccountMatcher

    @Before
    fun setUp() = runBlocking {
        expenseDao = FakeExpenseDao()
        categoryDao = FakeTransactionCategoryDao()
        ruleDao = FakeUserCategoryRuleDao()
        aliasDao = FakeMerchantAliasDao()
        accountDao = FakeKnownFinancialAccountDao()

        categoryManager = CategoryManager(categoryDao, expenseDao, ruleDao = ruleDao)
        categoryManager.ensureBuiltInCategoriesSeeded()

        ruleManager = CategoryRuleManager(ruleDao, categoryDao)
        aliasManager = MerchantAliasManager(aliasDao)
        accountRepo = KnownFinancialAccountRepository(accountDao)
        accountMatcher = KnownFinancialAccountMatcher(accountRepo)

        // Wire managers into TransactionPersistenceManager
        TransactionPersistenceManager.ruleManager = ruleManager
        TransactionPersistenceManager.aliasManager = aliasManager
        TransactionPersistenceManager.matcher = accountMatcher
        TransactionPersistenceManager.persistenceListener = null
    }

    @After
    fun tearDown() {
        TransactionPersistenceManager.ruleManager = null
        TransactionPersistenceManager.aliasManager = null
        TransactionPersistenceManager.matcher = null
        TransactionPersistenceManager.persistenceListener = null
    }

    // 1. Live transaction with matching user rule: merchant -> user-defined category
    @Test
    fun test1_liveTransaction_matchingUserRule_persistsUserCategory() = runBlocking {
        // User rule: "Netflix" -> Entertainment (built-in)
        ruleManager.createRule(
            pattern = "Netflix",
            categoryId = "entertainment",
            matchType = UserRuleMatchType.MERCHANT_EXACT
        ).getOrThrow()

        val notif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "live_netflix_1",
            postTime = 1000L,
            title = "Google Pay",
            text = "₹499 paid to Netflix"
        )

        val res = TransactionPersistenceManager.processCapturedNotification(notif, expenseDao)
        assertTrue("Expected Inserted result, got $res", res is ExpensePersistenceResult.Inserted)

        val saved = expenseDao.getAllExpensesList().first()
        assertEquals(499.0, saved.amount, 0.001)
        assertEquals("Netflix", saved.merchant)
        assertEquals("entertainment", saved.categoryId)
        assertEquals(CategorySource.INFERRED, saved.categorySource)
    }

    // 2. Live transaction without user-rule match: falls back to built-in inference
    @Test
    fun test2_liveTransaction_noUserRuleMatch_fallsBackToBuiltInInference() = runBlocking {
        val notif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "live_swiggy_1",
            postTime = 1000L,
            title = "Google Pay",
            text = "₹350 paid to Swiggy"
        )

        val res = TransactionPersistenceManager.processCapturedNotification(notif, expenseDao)
        assertTrue(res is ExpensePersistenceResult.Inserted)

        val saved = expenseDao.getAllExpensesList().first()
        assertEquals(350.0, saved.amount, 0.001)
        assertEquals("Swiggy", saved.merchant)
        assertEquals("food_dining", saved.categoryId)
        assertEquals(CategorySource.INFERRED, saved.categorySource)
    }

    // 3. Live transaction with matching merchant alias: alias affects inference
    @Test
    fun test3_liveTransaction_matchingMerchantAlias_affectsInference() = runBlocking {
        // Alias: "AMZN Mktp" -> "Amazon"
        aliasManager.createAlias(
            alias = "AMZN Mktp",
            canonicalMerchant = "Amazon"
        ).getOrThrow()

        // User rule: "Amazon" -> Shopping
        ruleManager.createRule(
            pattern = "Amazon",
            categoryId = "shopping",
            matchType = UserRuleMatchType.MERCHANT_EXACT
        ).getOrThrow()

        val notif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "live_amzn_alias_1",
            postTime = 1000L,
            title = "Google Pay",
            text = "₹1299 paid to AMZN Mktp"
        )

        val res = TransactionPersistenceManager.processCapturedNotification(notif, expenseDao)
        assertTrue(res is ExpensePersistenceResult.Inserted)

        val saved = expenseDao.getAllExpensesList().first()
        assertEquals(1299.0, saved.amount, 0.001)
        assertEquals("shopping", saved.categoryId)
        assertEquals(CategorySource.INFERRED, saved.categorySource)
    }

    // 4. Merchant alias does NOT overwrite Expense.merchant
    @Test
    fun test4_merchantAlias_doesNotOverwrite_persistedExpenseMerchant() = runBlocking {
        aliasManager.createAlias(
            alias = "AMZN Mktp",
            canonicalMerchant = "Amazon"
        ).getOrThrow()

        val notif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "live_amzn_merchant_protect",
            postTime = 1000L,
            title = "Google Pay",
            text = "₹1299 paid to AMZN Mktp"
        )

        val res = TransactionPersistenceManager.processCapturedNotification(notif, expenseDao)
        assertTrue(res is ExpensePersistenceResult.Inserted)

        val saved = expenseDao.getAllExpensesList().first()
        assertEquals("AMZN Mktp", saved.merchant)
        assertNotEquals("Amazon", saved.merchant)
    }

    // 5. User rule does NOT modify amount, merchant, date, type, accountId, accountSuffix, etc.
    @Test
    fun test5_userRule_doesNotModify_financialOrAccountFields() = runBlocking {
        // Register known account with suffix 9020
        val account = KnownFinancialAccount.create(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH
        )
        accountDao.insert(account)

        ruleManager.createRule(
            pattern = "Starbucks Coffee",
            categoryId = "food_dining",
            matchType = UserRuleMatchType.MERCHANT_EXACT
        ).getOrThrow()

        val notif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "live_starbucks_hdfc",
            postTime = 1700000000000L,
            title = "Google Pay",
            text = "₹450 paid to Starbucks Coffee from XXXXX9020"
        )

        val res = TransactionPersistenceManager.processCapturedNotification(notif, expenseDao)
        assertTrue(res is ExpensePersistenceResult.Inserted)

        val saved = expenseDao.getAllExpensesList().first()
        assertEquals(450.0, saved.amount, 0.001)
        assertEquals("Starbucks Coffee", saved.merchant)
        assertEquals("Debit", saved.type)
        assertEquals(account.id, saved.accountId)
        assertEquals("9020", saved.accountSuffix)
        assertEquals("food_dining", saved.categoryId)
        assertEquals(CategorySource.INFERRED, saved.categorySource)
        assertFalse(saved.isPending)
    }

    // 6. User rule failure does not reject the transaction
    @Test
    fun test6_userRuleFailure_doesNotRejectTransaction() = runBlocking {
        // Rule pointing to non-existent category
        val brokenRule = UserCategoryRule(
            id = "broken_rule_1",
            pattern = "Blinkit",
            normalizedPattern = "blinkit",
            categoryId = "non_existent_category",
            matchType = UserRuleMatchType.MERCHANT_EXACT
        )
        ruleDao.insert(brokenRule)

        val notif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "live_blinkit_1",
            postTime = 1000L,
            title = "Google Pay",
            text = "₹250 paid to Blinkit"
        )

        val res = TransactionPersistenceManager.processCapturedNotification(notif, expenseDao)
        assertTrue(res is ExpensePersistenceResult.Inserted)

        val saved = expenseDao.getAllExpensesList().first()
        assertEquals(250.0, saved.amount, 0.001)
        assertEquals("Blinkit", saved.merchant)
    }

    // 7. Alias failure does not reject the transaction
    @Test
    fun test7_aliasFailure_doesNotRejectTransaction() = runBlocking {
        // Disable alias manager and test that processing continues safely
        TransactionPersistenceManager.aliasManager = null

        val notif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "live_no_alias_mgr",
            postTime = 1000L,
            title = "Google Pay",
            text = "₹100 paid to Chai Point"
        )

        val res = TransactionPersistenceManager.processCapturedNotification(notif, expenseDao)
        assertTrue(res is ExpensePersistenceResult.Inserted)

        val saved = expenseDao.getAllExpensesList().first()
        assertEquals(100.0, saved.amount, 0.001)
        assertEquals("Chai Point", saved.merchant)
    }

    // 8. No user rules: transaction still persists
    @Test
    fun test8_noUserRules_transactionStillPersists() = runBlocking {
        TransactionPersistenceManager.ruleManager = null

        val notif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "live_no_rules_mgr",
            postTime = 1000L,
            title = "Google Pay",
            text = "₹60 paid to Uber"
        )

        val res = TransactionPersistenceManager.processCapturedNotification(notif, expenseDao)
        assertTrue(res is ExpensePersistenceResult.Inserted)

        val saved = expenseDao.getAllExpensesList().first()
        assertEquals(60.0, saved.amount, 0.001)
        assertEquals("Uber", saved.merchant)
        assertEquals("transport", saved.categoryId)
    }

    // 9. No aliases: transaction still persists
    @Test
    fun test9_noAliases_transactionStillPersists() = runBlocking {
        TransactionPersistenceManager.aliasManager = null

        val notif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "live_no_aliases_mgr_2",
            postTime = 1000L,
            title = "Google Pay",
            text = "₹1500 paid to BigBasket"
        )

        val res = TransactionPersistenceManager.processCapturedNotification(notif, expenseDao)
        assertTrue(res is ExpensePersistenceResult.Inserted)

        val saved = expenseDao.getAllExpensesList().first()
        assertEquals(1500.0, saved.amount, 0.001)
        assertEquals("BigBasket", saved.merchant)
    }

    // 10. Historical SMS persistence still works
    @Test
    fun test10_historicalSmsPersistence_stillWorks() = runBlocking {
        ruleManager.createRule(
            pattern = "Swiggy",
            categoryId = "food_dining",
            matchType = UserRuleMatchType.MERCHANT_CONTAINS
        ).getOrThrow()

        val res = TransactionPersistenceManager.processSms(
            smsId = "sms_101",
            sender = "SBI",
            body = "Paid ₹450 to Swiggy",
            timestamp = 1700000000000L,
            dao = expenseDao
        )

        assertTrue(res is ExpensePersistenceResult.Inserted)
        val saved = expenseDao.getAllExpensesList().first()
        assertEquals(450.0, saved.amount, 0.001)
        assertEquals("Swiggy", saved.merchant)
        assertEquals("food_dining", saved.categoryId)
    }

    // 11. M12 HistoricalRuleApplier still works
    @Test
    fun test11_m12_historicalRuleApplier_stillWorks() = runBlocking {
        expenseDao.insert(
            Expense(
                id = 1,
                merchant = "Netflix",
                amount = 499.0,
                dateMillis = 1000L,
                type = "Debit",
                notificationKey = "k1",
                categoryId = null,
                categorySource = CategorySource.NONE
            )
        )

        ruleManager.createRule(
            pattern = "Netflix",
            categoryId = "entertainment",
            matchType = UserRuleMatchType.MERCHANT_EXACT
        ).getOrThrow()

        val applier = HistoricalRuleApplier(
            expenseDao = expenseDao,
            categoryDao = categoryDao,
            ruleDao = ruleDao,
            aliasDao = aliasDao,
            inferenceEngine = CategoryInferenceEngine()
        )

        val preview = applier.generatePreview()
        assertEquals(1, preview.willChangeCount)
        assertEquals(1, preview.actionableItems.size)

        val result = applier.applyReassignments(preview.actionableItems)
        assertTrue(result.isSuccess)
        assertEquals(1, result.successCount)

        val updated = expenseDao.getExpenseById(1)
        assertEquals("entertainment", updated?.categoryId)
        assertEquals(CategorySource.INFERRED, updated?.categorySource)
    }

    // 12. USER_ASSIGNED category remains protected
    @Test
    fun test12_userAssignedCategory_remainsProtected() = runBlocking {
        // Pre-existing expense with USER_ASSIGNED category
        val manualExpense = Expense(
            id = 50,
            merchant = "Netflix",
            amount = 499.0,
            dateMillis = 1000L,
            type = "Debit",
            notificationKey = "k_user_assigned_netflix",
            categoryId = "shopping", // Deliberately user-assigned as Shopping
            categorySource = CategorySource.USER_ASSIGNED
        )
        expenseDao.insert(manualExpense)

        // User rule: Netflix -> Entertainment
        ruleManager.createRule(
            pattern = "Netflix",
            categoryId = "entertainment",
            matchType = UserRuleMatchType.MERCHANT_EXACT
        ).getOrThrow()

        // Incoming duplicate/correlated notification
        val notif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "k_user_assigned_netflix",
            postTime = 1000L,
            title = "Google Pay",
            text = "₹499 paid to Netflix"
        )

        val res = TransactionPersistenceManager.processCapturedNotification(notif, expenseDao)
        assertTrue(res is ExpensePersistenceResult.SkippedDuplicate)

        val inDb = expenseDao.getExpenseById(50)
        assertEquals("shopping", inDb?.categoryId)
        assertEquals(CategorySource.USER_ASSIGNED, inDb?.categorySource)
    }

    // 13. Live-specific regression test (Section 16 scenario)
    @Test
    fun test13_liveSpecificRegression_netflixAndAmazon() = runBlocking {
        // Scenario A:
        // Input: Merchant: Netflix
        // User rule: merchant contains "netflix" -> Entertainment
        // Expected: live transaction persists, categoryId = Entertainment, categorySource = INFERRED
        ruleManager.createRule(
            pattern = "netflix",
            categoryId = "entertainment",
            matchType = UserRuleMatchType.MERCHANT_CONTAINS
        ).getOrThrow()

        val netflixNotif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "live_reg_netflix",
            postTime = 1000L,
            title = "Google Pay",
            text = "₹499 paid to Netflix"
        )

        val resA = TransactionPersistenceManager.processCapturedNotification(netflixNotif, expenseDao)
        assertTrue("Expected Inserted, got $resA", resA is ExpensePersistenceResult.Inserted)

        val savedA = expenseDao.getExpenseByNotificationKey("live_reg_netflix")
        assertNotNull(savedA)
        assertEquals(499.0, savedA!!.amount, 0.001)
        assertEquals("entertainment", savedA.categoryId)
        assertEquals(CategorySource.INFERRED, savedA.categorySource)

        // Scenario B:
        // Alias: "AMZN Mktp" -> "Amazon"
        // User rule: merchant contains "amazon" -> Shopping
        // Expected: persisted Expense.merchant: "AMZN Mktp", inferred category: Shopping
        aliasManager.createAlias(
            alias = "AMZN Mktp",
            canonicalMerchant = "Amazon"
        ).getOrThrow()

        ruleManager.createRule(
            pattern = "amazon",
            categoryId = "shopping",
            matchType = UserRuleMatchType.MERCHANT_CONTAINS
        ).getOrThrow()

        val amazonNotif = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "live_reg_amazon",
            postTime = 2000L,
            title = "Google Pay",
            text = "₹1299 paid to AMZN Mktp"
        )

        val resB = TransactionPersistenceManager.processCapturedNotification(amazonNotif, expenseDao)
        assertTrue("Expected Inserted, got $resB", resB is ExpensePersistenceResult.Inserted)

        val savedB = expenseDao.getExpenseByNotificationKey("live_reg_amazon")
        assertNotNull(savedB)
        assertEquals(1299.0, savedB!!.amount, 0.001)
        assertEquals("AMZN Mktp", savedB.merchant)
        assertEquals("shopping", savedB.categoryId)
        assertEquals(CategorySource.INFERRED, savedB.categorySource)
    }
}
