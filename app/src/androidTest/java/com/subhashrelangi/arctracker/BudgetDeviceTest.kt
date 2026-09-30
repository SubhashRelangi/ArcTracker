package com.subhashrelangi.arctracker

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/**
 * Milestone 14: Budgeting & Alerts Physical Device & Instrumentation Tests.
 *
 * Runs on connected physical Android device (Samsung Galaxy A34 5G).
 *
 * Verifies:
 * - Room SQLite database schema (v8) and MIGRATION_7_8 execution.
 * - Budget CRUD operations on real SQLite.
 * - Category budget progress calculation from persisted Expense records.
 * - Overall budget progress calculation from persisted Expense records.
 * - Exclusion of self-transfers, income (Credit), and out-of-period transactions.
 * - Budget alert state persistence and duplicate alert suppression.
 * - Category lifecycle integration (disabling budgets when category is deleted/archived/merged).
 * - Reactive Flow observation on SQLite table mutations.
 * - READ-ONLY invariant: Budget calculations NEVER mutate persisted Expense records.
 */
@RunWith(AndroidJUnit4::class)
class BudgetDeviceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var expenseDao: ExpenseDao
    private lateinit var categoryDao: TransactionCategoryDao
    private lateinit var budgetDao: BudgetDao
    private lateinit var budgetManager: BudgetManager
    private lateinit var categoryManager: CategoryManager
    private lateinit var testNotifier: RecordingBudgetAlertNotifier

    private val testDbName = "m14_device_test_budgets.db"

    class RecordingBudgetAlertNotifier : BudgetAlertNotifier {
        val notifications = mutableListOf<String>()
        override fun sendWarningAlert(budget: Budget, progress: BudgetProgress): Boolean {
            notifications.add("WARNING:${budget.id}:${progress.spent}/${progress.limit}")
            return true
        }
        override fun sendExceededAlert(budget: Budget, progress: BudgetProgress): Boolean {
            notifications.add("EXCEEDED:${budget.id}:${progress.spent}/${progress.limit}")
            return true
        }
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDbName)

        database = Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
            .allowMainThreadQueries()
            .build()

        expenseDao = database.expenseDao()
        categoryDao = database.transactionCategoryDao()
        budgetDao = database.budgetDao()

        testNotifier = RecordingBudgetAlertNotifier()
        budgetManager = BudgetManager(budgetDao, expenseDao, categoryDao, testNotifier)
        categoryManager = CategoryManager(categoryDao, expenseDao, database, database.userCategoryRuleDao(), budgetDao)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(testDbName)
    }

    // ==================================================
    // 1. ROOM MIGRATION 7 -> 8 VERIFICATION
    // ==================================================

    @Test
    fun testRoomMigration_v7_to_v8_createsTablesAndPreservesData() {
        val migrationDbName = "test_migration_7_8.db"
        context.deleteDatabase(migrationDbName)

        // Step 1: Create v7 schema manually
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(migrationDbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `expenses` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `amount` REAL NOT NULL,
                            `merchant` TEXT NOT NULL,
                            `dateMillis` INTEGER NOT NULL,
                            `type` TEXT NOT NULL,
                            `notificationKey` TEXT NOT NULL,
                            `isPending` INTEGER NOT NULL,
                            `rawText` TEXT,
                            `tag` TEXT,
                            `note` TEXT,
                            `source` TEXT NOT NULL,
                            `relationshipType` TEXT,
                            `relationshipId` TEXT,
                            `accountId` TEXT,
                            `accountSuffix` TEXT,
                            `categoryId` TEXT,
                            `categorySource` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `transaction_categories` (
                            `id` TEXT NOT NULL PRIMARY KEY,
                            `name` TEXT NOT NULL,
                            `iconKey` TEXT NOT NULL,
                            `colorKey` TEXT NOT NULL,
                            `isSystem` INTEGER NOT NULL,
                            `isArchived` INTEGER NOT NULL,
                            `sortOrder` INTEGER NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val openHelper = helperFactory.create(config)
        val v7Db = openHelper.writableDatabase

        // Insert mock data in v7
        v7Db.execSQL("INSERT INTO `expenses` (`id`, `amount`, `merchant`, `dateMillis`, `type`, `notificationKey`, `isPending`, `source`, `categoryId`, `categorySource`) VALUES (1, 500.0, 'Dinner', 1700000000000, 'Debit', '', 0, 'SMS', 'cat_food', 'MANUAL')")
        v7Db.execSQL("INSERT INTO `transaction_categories` (`id`, `name`, `iconKey`, `colorKey`, `isSystem`, `isArchived`, `sortOrder`, `createdAt`, `updatedAt`) VALUES ('cat_food', 'Food & Dining', 'food', 'orange', 1, 0, 1, 1000, 1000)")

        // Step 2: Run MIGRATION_7_8
        AppDatabase.MIGRATION_7_8.migrate(v7Db)

        // Step 3: Verify budgets and budget_alert_states exist
        val budgetsCursor = v7Db.query("SELECT * FROM `budgets`")
        assertNotNull(budgetsCursor)
        budgetsCursor.close()

        val alertStateCursor = v7Db.query("SELECT * FROM `budget_alert_states`")
        assertNotNull(alertStateCursor)
        alertStateCursor.close()

        // Step 4: Verify existing data preserved
        val expensesCursor = v7Db.query("SELECT `merchant`, `amount` FROM `expenses` WHERE `id` = 1")
        assertTrue(expensesCursor.moveToFirst())
        assertEquals("Dinner", expensesCursor.getString(0))
        assertEquals(500.0, expensesCursor.getDouble(1), 0.001)
        expensesCursor.close()

        v7Db.close()
        context.deleteDatabase(migrationDbName)
    }

    // ==================================================
    // 2. BUDGET CRUD ON PHYSICAL SQLITE
    // ==================================================

    @Test
    fun testBudget_crudOperations_physicalDevice() = runBlocking {
        // Create
        val budget = Budget(
            id = "b_test_1",
            name = "Groceries",
            amountLimit = 5000.0,
            categoryId = "cat_groceries",
            periodType = BudgetPeriodType.MONTHLY.name,
            periodAnchor = 1,
            warningThreshold = 80.0,
            exceededThreshold = 100.0,
            isEnabled = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        budgetDao.insert(budget)

        // Read
        val loaded = budgetDao.getById("b_test_1")
        assertNotNull(loaded)
        assertEquals("Groceries", loaded!!.name)
        assertEquals(5000.0, loaded.amountLimit, 0.001)
        assertTrue(loaded.isEnabled)

        // Update
        val updated = loaded.copy(name = "Household & Groceries", amountLimit = 6000.0)
        budgetDao.update(updated)

        val reloaded = budgetDao.getById("b_test_1")
        assertEquals("Household & Groceries", reloaded!!.name)
        assertEquals(6000.0, reloaded.amountLimit, 0.001)

        // Disable
        budgetDao.setBudgetEnabled("b_test_1", false, System.currentTimeMillis())
        val disabled = budgetDao.getById("b_test_1")
        assertFalse(disabled!!.isEnabled)

        // Delete
        budgetDao.deleteById("b_test_1")
        val deleted = budgetDao.getById("b_test_1")
        assertNull(deleted)
    }

    // ==================================================
    // 3. CATEGORY & OVERALL BUDGET PROGRESS CALCULATIONS
    // ==================================================

    @Test
    fun testBudgetProgress_categoryAndOverall_withExclusions() = runBlocking {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val monthStart = cal.timeInMillis

        // Insert expenses in SQLite
        // 1. Food expense (in period, normal expense) -> ₹1,200
        expenseDao.insert(Expense(merchant = "Swiggy", amount = 1200.0, dateMillis = monthStart + 5000, type = "Debit", source = "SMS", categoryId = "cat_food"))
        // 2. Food expense (in period, normal expense) -> ₹800
        expenseDao.insert(Expense(merchant = "Zomato", amount = 800.0, dateMillis = monthStart + 10000, type = "Debit", source = "SMS", categoryId = "cat_food"))
        // 3. Shopping expense (in period) -> ₹3,000
        expenseDao.insert(Expense(merchant = "Amazon", amount = 3000.0, dateMillis = monthStart + 15000, type = "Debit", source = "SMS", categoryId = "cat_shopping"))
        // 4. Self-transfer (MUST BE EXCLUDED) -> ₹10,000
        expenseDao.insert(Expense(merchant = "Transfer to Savings", amount = 10000.0, dateMillis = monthStart + 20000, type = "Debit", source = "SMS", relationshipType = TransactionRelationshipType.SELF_TRANSFER, categoryId = "cat_food"))
        // 5. Income Credit (MUST BE EXCLUDED) -> ₹50,000
        expenseDao.insert(Expense(merchant = "Salary", amount = 50000.0, dateMillis = monthStart + 25000, type = "Credit", source = "SMS", categoryId = "cat_food"))
        // 6. Food expense from LAST month (MUST BE EXCLUDED) -> ₹2,500
        expenseDao.insert(Expense(merchant = "Old Dinner", amount = 2500.0, dateMillis = monthStart - 50000, type = "Debit", source = "SMS", categoryId = "cat_food"))

        // Create Category Budget for Food: ₹3,000 limit
        val foodBudget = Budget(
            id = "b_food",
            name = "Food Budget",
            amountLimit = 3000.0,
            categoryId = "cat_food",
            periodType = BudgetPeriodType.MONTHLY.name,
            periodAnchor = 1
        )
        budgetDao.insert(foodBudget)

        // Create Overall Budget: ₹10,000 limit
        val overallBudget = Budget(
            id = "b_overall",
            name = "Total Monthly Limit",
            amountLimit = 10000.0,
            categoryId = null,
            periodType = BudgetPeriodType.MONTHLY.name,
            periodAnchor = 1
        )
        budgetDao.insert(overallBudget)

        val expenses = expenseDao.getAllExpensesList()
        val categories = categoryDao.getAll()

        // Verify Food Budget Progress
        val foodProgress = budgetManager.calculateBudgetProgress(foodBudget, expenses, categories, now)
        assertEquals(2000.0, foodProgress.spent, 0.001) // 1200 + 800
        assertEquals(1000.0, foodProgress.remaining, 0.001)
        assertEquals(66.67, foodProgress.percentageUsed, 0.1)
        assertEquals(BudgetStatus.UNDER_BUDGET, foodProgress.status)

        // Verify Overall Budget Progress
        val overallProgress = budgetManager.calculateBudgetProgress(overallBudget, expenses, categories, now)
        assertEquals(5000.0, overallProgress.spent, 0.001) // 1200 + 800 + 3000
        assertEquals(5000.0, overallProgress.remaining, 0.001)
        assertEquals(50.0, overallProgress.percentageUsed, 0.1)
        assertEquals(BudgetStatus.UNDER_BUDGET, overallProgress.status)
    }

    // ==================================================
    // 4. ALERT STATE PERSISTENCE & DEDUPLICATION
    // ==================================================

    @Test
    fun testBudgetAlert_stateTransitionsAndDeduplication() = runBlocking {
        val now = System.currentTimeMillis()
        val bounds = BudgetPeriodBounds.calculate(BudgetPeriodType.MONTHLY, 1, now)

        val budget = Budget(
            id = "b_alert_test",
            name = "Alert Test Budget",
            amountLimit = 1000.0,
            categoryId = "cat_test",
            warningThreshold = 80.0,
            exceededThreshold = 100.0
        )
        budgetDao.insert(budget)

        // Step 1: 50% spend -> No alerts
        expenseDao.insert(Expense(merchant = "T1", amount = 500.0, dateMillis = bounds.startMillis + 1000, type = "Debit", source = "SMS", categoryId = "cat_test"))
        budgetManager.evaluateAllBudgets(now)
        assertEquals(0, testNotifier.notifications.size)

        // Step 2: 85% spend (Cross warning) -> 1 WARNING notification
        expenseDao.insert(Expense(merchant = "T2", amount = 350.0, dateMillis = bounds.startMillis + 2000, type = "Debit", source = "SMS", categoryId = "cat_test"))
        budgetManager.evaluateAllBudgets(now)
        assertEquals(1, testNotifier.notifications.size)
        assertTrue(testNotifier.notifications[0].startsWith("WARNING:b_alert_test"))

        // Step 3: Spend more (95%) -> Warning already sent, NOT repeated
        expenseDao.insert(Expense(merchant = "T3", amount = 100.0, dateMillis = bounds.startMillis + 3000, type = "Debit", source = "SMS", categoryId = "cat_test"))
        budgetManager.evaluateAllBudgets(now)
        assertEquals(1, testNotifier.notifications.size) // Unchanged!

        // Step 4: Cross 105% (Cross exceeded) -> 1 EXCEEDED notification
        expenseDao.insert(Expense(merchant = "T4", amount = 100.0, dateMillis = bounds.startMillis + 4000, type = "Debit", source = "SMS", categoryId = "cat_test"))
        budgetManager.evaluateAllBudgets(now)
        assertEquals(2, testNotifier.notifications.size)
        assertTrue(testNotifier.notifications[1].startsWith("EXCEEDED:b_alert_test"))

        // Step 5: Spend more (115%) -> Exceeded already sent, NOT repeated
        expenseDao.insert(Expense(merchant = "T5", amount = 100.0, dateMillis = bounds.startMillis + 5000, type = "Debit", source = "SMS", categoryId = "cat_test"))
        budgetManager.evaluateAllBudgets(now)
        assertEquals(2, testNotifier.notifications.size) // Unchanged!

        // Step 6: Verify persisted alert state entity in SQLite
        val alertState = budgetDao.getAlertState("b_alert_test", bounds.startMillis)
        assertNotNull(alertState)
        assertTrue(alertState!!.warningSent)
        assertTrue(alertState.exceededSent)
    }

    // ==================================================
    // 5. CATEGORY LIFECYCLE INTEGRATION
    // ==================================================

    @Test
    fun testCategoryLifecycle_disablesAssociatedBudgets() = runBlocking {
        // Create custom category
        val category = TransactionCategory(
            id = "cat_custom_entertainment",
            name = "Movies & Fun",
            iconKey = "movie",
            colorKey = "purple",
            isSystem = false,
            isArchived = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        categoryDao.upsert(category)

        // Create budget for this category
        val budget = Budget(
            id = "b_movie",
            name = "Movie Budget",
            amountLimit = 2000.0,
            categoryId = "cat_custom_entertainment",
            isEnabled = true
        )
        budgetDao.insert(budget)
        assertTrue(budgetDao.getById("b_movie")!!.isEnabled)

        // Delete category via CategoryManager
        val deleteResult = categoryManager.deleteCategory("cat_custom_entertainment")
        assertTrue(deleteResult.isSuccess)

        // Verify budget is safely disabled
        val budgetAfterDelete = budgetDao.getById("b_movie")
        assertNotNull(budgetAfterDelete)
        assertFalse(budgetAfterDelete!!.isEnabled)
    }

    // ==================================================
    // 6. REACTIVE FLOW OBSERVATION
    // ==================================================

    @Test
    fun testReactiveFlow_emitsOnExpenseChanges() = runBlocking {
        val now = System.currentTimeMillis()
        val budget = Budget(
            id = "b_flow",
            name = "Dining",
            amountLimit = 2000.0,
            categoryId = "cat_flow_food",
            periodType = BudgetPeriodType.MONTHLY.name,
            periodAnchor = 1
        )
        budgetDao.insert(budget)

        // Observe flow initial state
        val initialList = budgetManager.observeAllBudgetsProgress(now).first()
        assertEquals(1, initialList.size)
        assertEquals(0.0, initialList[0].spent, 0.001)

        // Insert new expense into SQLite
        expenseDao.insert(Expense(merchant = "KFC", amount = 750.0, dateMillis = now, type = "Debit", source = "SMS", categoryId = "cat_flow_food"))

        // Observe flow updated state
        val updatedList = budgetManager.observeAllBudgetsProgress(now).first()
        assertEquals(1, updatedList.size)
        assertEquals(750.0, updatedList[0].spent, 0.001)
        assertEquals(1250.0, updatedList[0].remaining, 0.001)
    }

    // ==================================================
    // 7. READ-ONLY INVARIANT
    // ==================================================

    @Test
    fun testReadOnlyInvariant_budgetsNeverModifyExpenses() = runBlocking {
        val now = System.currentTimeMillis()
        val expenseId = expenseDao.insert(
            Expense(
                merchant = "Original Merchant",
                amount = 450.0,
                dateMillis = now,
                type = "Debit",
                source = "SMS",
                categoryId = "cat_groc",
                categorySource = "RULE",
                relationshipType = null,
                accountId = "acc_1"
            )
        ).toInt()

        val budget = Budget(
            id = "b_readonly_check",
            name = "Test Readonly",
            amountLimit = 100.0, // Limit exceeded!
            categoryId = "cat_groc"
        )
        budgetDao.insert(budget)

        // Run budget calculations & alert checks
        budgetManager.evaluateAllBudgets(now)
        budgetManager.observeAllBudgetsProgress(now).first()

        // Fetch the expense directly from SQLite
        val fetched = expenseDao.getExpenseById(expenseId)
        assertNotNull(fetched)
        assertEquals("Original Merchant", fetched!!.merchant)
        assertEquals(450.0, fetched.amount, 0.001)
        assertEquals(now, fetched.dateMillis)
        assertEquals("Debit", fetched.type)
        assertEquals("SMS", fetched.source)
        assertEquals("cat_groc", fetched.categoryId)
        assertEquals("RULE", fetched.categorySource)
        assertNull(fetched.relationshipType)
        assertEquals("acc_1", fetched.accountId)
    }
}
