package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * Test double capturing alerts for testing.
 */
class FakeBudgetAlertNotifier : BudgetAlertNotifier {
    var warningAlertCount = 0
    var exceededAlertCount = 0
    val warnings = mutableListOf<Pair<Budget, BudgetProgress>>()
    val exceeded = mutableListOf<Pair<Budget, BudgetProgress>>()

    override fun sendWarningAlert(budget: Budget, progress: BudgetProgress): Boolean {
        warningAlertCount++
        warnings.add(budget to progress)
        return true
    }

    override fun sendExceededAlert(budget: Budget, progress: BudgetProgress): Boolean {
        exceededAlertCount++
        exceeded.add(budget to progress)
        return true
    }

    fun clear() {
        warningAlertCount = 0
        exceededAlertCount = 0
        warnings.clear()
        exceeded.clear()
    }
}

/**
 * Focused JVM Unit Tests for Milestone 14: Budgeting & Alerts.
 *
 * Verifies all 32 required test cases:
 * 1. Create budget
 * 2. Edit budget
 * 3. Delete/archive budget
 * 4. Positive amount validation
 * 5. Empty name validation
 * 6. Category budget
 * 7. Overall budget
 * 8. Weekly period
 * 9. Monthly period
 * 10. Yearly period
 * 11. Current period calculation
 * 12. Date boundary handling
 * 13. Expense inclusion
 * 14. Income exclusion
 * 15. Self-transfer exclusion
 * 16. Uncategorized category behavior
 * 17. Remaining amount
 * 18. Percentage calculation
 * 19. Warning threshold
 * 20. Exceeded threshold
 * 21. Alert threshold crossing
 * 22. Duplicate warning suppression
 * 23. Duplicate exceeded-alert suppression
 * 24. New-period alert reset
 * 25. App/repository restart alert-state persistence
 * 26. Multiple budgets
 * 27. Overall + category budget simultaneously
 * 28. Archived/deleted category handling
 * 29. Analytics/transaction data remains unchanged
 * 30. Budget changes do not modify Expense
 * 31. Sequence alert test (Section 34)
 * 32. Reactive flow updates
 */
class BudgetManagerTest {

    private lateinit var budgetDao: FakeBudgetDao
    private lateinit var expenseDao: FakeExpenseDao
    private lateinit var categoryDao: FakeTransactionCategoryDao
    private lateinit var alertNotifier: FakeBudgetAlertNotifier
    private lateinit var budgetManager: BudgetManager

    private val fixedTimestamp = 1774000000000L // Approx March 2026

    @Before
    fun setUp() = runBlocking {
        budgetDao = FakeBudgetDao()
        expenseDao = FakeExpenseDao()
        categoryDao = FakeTransactionCategoryDao()
        alertNotifier = FakeBudgetAlertNotifier()

        categoryDao.insertAll(BuiltInCategories.ALL)

        budgetManager = BudgetManager(
            budgetDao = budgetDao,
            expenseDao = expenseDao,
            categoryDao = categoryDao,
            alertNotifier = alertNotifier
        )
    }

    // 1. Create budget
    @Test
    fun test1_createBudget() = runBlocking {
        val result = budgetManager.createBudget(
            name = "Groceries",
            amountLimit = 15000.0,
            categoryId = BuiltInCategories.FOOD_AND_DINING.id,
            periodType = BudgetPeriodType.MONTHLY
        )
        assertTrue(result.isSuccess)
        val budget = result.getOrNull()
        assertNotNull(budget)
        assertEquals("Groceries", budget!!.name)
        assertEquals(15000.0, budget.amountLimit, 0.001)
        assertEquals(BuiltInCategories.FOOD_AND_DINING.id, budget.categoryId)
        assertTrue(budget.isEnabled)
    }

    // 2. Edit budget
    @Test
    fun test2_editBudget() = runBlocking {
        val created = budgetManager.createBudget(
            name = "Shopping",
            amountLimit = 5000.0,
            categoryId = BuiltInCategories.SHOPPING.id
        ).getOrThrow()

        val updated = created.copy(
            name = "Retail Shopping",
            amountLimit = 8000.0,
            warningThreshold = 75.0
        )
        val result = budgetManager.updateBudget(updated)
        assertTrue(result.isSuccess)

        val inDb = budgetDao.getById(created.id)
        assertNotNull(inDb)
        assertEquals("Retail Shopping", inDb!!.name)
        assertEquals(8000.0, inDb.amountLimit, 0.001)
        assertEquals(75.0, inDb.warningThreshold, 0.001)
    }

    // 3. Delete/archive budget
    @Test
    fun test3_deleteAndArchiveBudget() = runBlocking {
        val budget = budgetManager.createBudget(
            name = "Temp Budget",
            amountLimit = 2000.0
        ).getOrThrow()

        // Archive / pause
        budgetManager.setBudgetEnabled(budget.id, false)
        val paused = budgetDao.getById(budget.id)
        assertNotNull(paused)
        assertFalse(paused!!.isEnabled)

        // Delete
        val delResult = budgetManager.deleteBudget(budget.id)
        assertTrue(delResult.isSuccess)
        assertNull(budgetDao.getById(budget.id))
    }

    // 4. Positive amount validation
    @Test
    fun test4_positiveAmountValidation() = runBlocking {
        val zeroResult = budgetManager.createBudget("Zero", 0.0)
        assertTrue(zeroResult.isFailure)

        val negResult = budgetManager.createBudget("Negative", -500.0)
        assertTrue(negResult.isFailure)

        val nanResult = budgetManager.createBudget("NaN", Double.NaN)
        assertTrue(nanResult.isFailure)
    }

    // 5. Empty name validation
    @Test
    fun test5_emptyNameValidation() = runBlocking {
        val emptyResult = budgetManager.createBudget("", 5000.0)
        assertTrue(emptyResult.isFailure)

        val blankResult = budgetManager.createBudget("   ", 5000.0)
        assertTrue(blankResult.isFailure)
    }

    // 6. Category budget
    @Test
    fun test6_categoryBudget() = runBlocking {
        val budget = budgetManager.createBudget(
            name = "Dining",
            amountLimit = 5000.0,
            categoryId = BuiltInCategories.FOOD_AND_DINING.id
        ).getOrThrow()

        assertFalse(budget.isOverallBudget)
        assertEquals(BuiltInCategories.FOOD_AND_DINING.id, budget.categoryId)
    }

    // 7. Overall budget
    @Test
    fun test7_overallBudget() = runBlocking {
        val budget = budgetManager.createBudget(
            name = "All Monthly Expenses",
            amountLimit = 40000.0,
            categoryId = null
        ).getOrThrow()

        assertTrue(budget.isOverallBudget)
        assertNull(budget.categoryId)
    }

    // 8. Weekly period
    @Test
    fun test8_weeklyPeriodCalculation() {
        val bounds = BudgetPeriodBounds.calculate(
            periodType = BudgetPeriodType.WEEKLY,
            referenceTimeMillis = fixedTimestamp
        )
        val diffDays = (bounds.endMillis - bounds.startMillis + 1) / (24 * 3600 * 1000)
        assertEquals(7, diffDays)
        assertTrue(bounds.daysRemaining in 1..7)
    }

    // 9. Monthly period
    @Test
    fun test9_monthlyPeriodCalculation() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.FEBRUARY, 10, 12, 0) }
        val febTime = cal.timeInMillis

        val bounds = BudgetPeriodBounds.calculate(
            periodType = BudgetPeriodType.MONTHLY,
            referenceTimeMillis = febTime
        )

        cal.timeInMillis = bounds.startMillis
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.FEBRUARY, cal.get(Calendar.MONTH))

        cal.timeInMillis = bounds.endMillis
        assertEquals(28, cal.get(Calendar.DAY_OF_MONTH)) // 2026 is non-leap year
    }

    // 10. Yearly period
    @Test
    fun test10_yearlyPeriodCalculation() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.JULY, 15, 12, 0) }
        val bounds = BudgetPeriodBounds.calculate(
            periodType = BudgetPeriodType.YEARLY,
            referenceTimeMillis = cal.timeInMillis
        )

        cal.timeInMillis = bounds.startMillis
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH))
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))

        cal.timeInMillis = bounds.endMillis
        assertEquals(Calendar.DECEMBER, cal.get(Calendar.MONTH))
        assertEquals(31, cal.get(Calendar.DAY_OF_MONTH))
    }

    // 11. Current period calculation with custom anchor (e.g. 15th of month)
    @Test
    fun test11_customPeriodAnchorHandling() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 20, 12, 0) }
        val bounds = BudgetPeriodBounds.calculate(
            periodType = BudgetPeriodType.MONTHLY,
            anchor = 15,
            referenceTimeMillis = cal.timeInMillis
        )

        cal.timeInMillis = bounds.startMillis
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MARCH, cal.get(Calendar.MONTH))

        cal.timeInMillis = bounds.endMillis + 1 // Start of next period
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.APRIL, cal.get(Calendar.MONTH))
    }

    // 12. Date boundary handling
    @Test
    fun test12_dateBoundaryHandling() = runBlocking {
        val budget = budgetManager.createBudget(
            name = "Test Bounds",
            amountLimit = 1000.0,
            periodType = BudgetPeriodType.MONTHLY
        ).getOrThrow()

        val bounds = BudgetPeriodBounds.calculate(BudgetPeriodType.MONTHLY, referenceTimeMillis = fixedTimestamp)

        // Inside boundary
        expenseDao.insert(Expense(amount = 100.0, merchant = "Start", dateMillis = bounds.startMillis, type = "Debit"))
        expenseDao.insert(Expense(amount = 200.0, merchant = "End", dateMillis = bounds.endMillis, type = "Debit"))
        // Outside boundary
        expenseDao.insert(Expense(amount = 300.0, merchant = "Before", dateMillis = bounds.startMillis - 1, type = "Debit"))
        expenseDao.insert(Expense(amount = 400.0, merchant = "After", dateMillis = bounds.endMillis + 1, type = "Debit"))

        val expenses = expenseDao.getAllExpensesList()
        val progress = budgetManager.calculateBudgetProgress(budget, expenses, emptyList(), fixedTimestamp)

        assertEquals(300.0, progress.spent, 0.001)
        assertEquals(2, progress.eligibleTransactionCount)
    }

    // 13. Expense inclusion
    @Test
    fun test13_expenseInclusion() = runBlocking {
        val budget = budgetManager.createBudget(
            name = "Groceries",
            amountLimit = 5000.0,
            categoryId = BuiltInCategories.FOOD_AND_DINING.id
        ).getOrThrow()

        expenseDao.insert(Expense(amount = 450.0, merchant = "Store", dateMillis = fixedTimestamp, type = "Debit", categoryId = BuiltInCategories.FOOD_AND_DINING.id))

        val progress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), fixedTimestamp)
        assertEquals(450.0, progress.spent, 0.001)
        assertEquals(1, progress.eligibleTransactionCount)
    }

    // 14. Income exclusion
    @Test
    fun test14_incomeExclusion() = runBlocking {
        val budget = budgetManager.createBudget(
            name = "Overall",
            amountLimit = 10000.0
        ).getOrThrow()

        expenseDao.insert(Expense(amount = 50000.0, merchant = "Salary", dateMillis = fixedTimestamp, type = "Credit"))
        expenseDao.insert(Expense(amount = 1000.0, merchant = "Dinner", dateMillis = fixedTimestamp, type = "Debit"))

        val progress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), fixedTimestamp)
        // Income must NOT be included or offset spending
        assertEquals(1000.0, progress.spent, 0.001)
        assertEquals(1, progress.eligibleTransactionCount)
    }

    // 15. Self-transfer exclusion
    @Test
    fun test15_selfTransferExclusion() = runBlocking {
        val budget = budgetManager.createBudget(
            name = "Overall",
            amountLimit = 20000.0
        ).getOrThrow()

        expenseDao.insert(Expense(amount = 500.0, merchant = "Coffee", dateMillis = fixedTimestamp, type = "Debit"))
        expenseDao.insert(Expense(
            amount = 15000.0,
            merchant = "Transfer Leg",
            dateMillis = fixedTimestamp,
            type = "Debit",
            relationshipType = TransactionRelationshipType.SELF_TRANSFER
        ))

        val progress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), fixedTimestamp)
        assertEquals(500.0, progress.spent, 0.001)
        assertEquals(1, progress.eligibleTransactionCount)
    }

    // 16. Uncategorized category behavior
    @Test
    fun test16_uncategorizedCategoryBehavior() = runBlocking {
        val categoryBudget = budgetManager.createBudget(
            name = "Food Only",
            amountLimit = 5000.0,
            categoryId = BuiltInCategories.FOOD_AND_DINING.id
        ).getOrThrow()

        val overallBudget = budgetManager.createBudget(
            name = "Overall",
            amountLimit = 10000.0,
            categoryId = null
        ).getOrThrow()

        // Uncategorized transaction
        expenseDao.insert(Expense(amount = 600.0, merchant = "Mystery Store", dateMillis = fixedTimestamp, type = "Debit", categoryId = null))

        val expenses = expenseDao.getAllExpensesList()

        // Category budget must NOT include uncategorized transaction
        val catProgress = budgetManager.calculateBudgetProgress(categoryBudget, expenses, emptyList(), fixedTimestamp)
        assertEquals(0.0, catProgress.spent, 0.001)

        // Overall budget DOES include uncategorized expense
        val overallProgress = budgetManager.calculateBudgetProgress(overallBudget, expenses, emptyList(), fixedTimestamp)
        assertEquals(600.0, overallProgress.spent, 0.001)
    }

    // 17. Remaining amount
    @Test
    fun test17_remainingAmountCalculation() = runBlocking {
        val budget = budgetManager.createBudget(name = "B1", amountLimit = 10000.0).getOrThrow()
        expenseDao.insert(Expense(amount = 3200.0, merchant = "M1", dateMillis = fixedTimestamp, type = "Debit"))

        val progress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), fixedTimestamp)
        assertEquals(6800.0, progress.remaining, 0.001)
    }

    // 18. Percentage calculation
    @Test
    fun test18_percentageCalculation() = runBlocking {
        val budget = budgetManager.createBudget(name = "B2", amountLimit = 5000.0).getOrThrow()
        expenseDao.insert(Expense(amount = 2500.0, merchant = "M1", dateMillis = fixedTimestamp, type = "Debit"))

        val progress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), fixedTimestamp)
        assertEquals(50.0, progress.percentageUsed, 0.001)
    }

    // 19. Warning threshold
    @Test
    fun test19_warningThresholdStatus() = runBlocking {
        val budget = budgetManager.createBudget(
            name = "Warning Test",
            amountLimit = 10000.0,
            warningThreshold = 80.0,
            exceededThreshold = 100.0
        ).getOrThrow()

        expenseDao.insert(Expense(amount = 8000.0, merchant = "M1", dateMillis = fixedTimestamp, type = "Debit"))
        val progress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), fixedTimestamp)
        assertEquals(BudgetStatus.WARNING, progress.status)
        assertTrue(progress.isWarning)
    }

    // 20. Exceeded threshold
    @Test
    fun test20_exceededThresholdStatus() = runBlocking {
        val budget = budgetManager.createBudget(
            name = "Exceeded Test",
            amountLimit = 10000.0,
            warningThreshold = 80.0,
            exceededThreshold = 100.0
        ).getOrThrow()

        expenseDao.insert(Expense(amount = 10050.0, merchant = "M1", dateMillis = fixedTimestamp, type = "Debit"))
        val progress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), fixedTimestamp)
        assertEquals(BudgetStatus.EXCEEDED, progress.status)
        assertTrue(progress.isExceeded)
        assertEquals(0.0, progress.remaining, 0.001)
    }

    // 21. Alert threshold crossing triggers alert
    @Test
    fun test21_alertThresholdCrossingTriggersAlert() = runBlocking {
        val budget = budgetManager.createBudget(
            name = "Alert Cross",
            amountLimit = 10000.0,
            warningThreshold = 80.0
        ).getOrThrow()

        expenseDao.insert(Expense(amount = 8500.0, merchant = "Spend", dateMillis = fixedTimestamp, type = "Debit"))
        val progress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), fixedTimestamp)

        val triggered = budgetManager.evaluateAndDispatchAlert(budget, progress)
        assertTrue(triggered)
        assertEquals(1, alertNotifier.warningAlertCount)
    }

    // 22. Duplicate warning suppression
    @Test
    fun test22_duplicateWarningSuppression() = runBlocking {
        val budget = budgetManager.createBudget(
            name = "Suppression",
            amountLimit = 10000.0,
            warningThreshold = 80.0
        ).getOrThrow()

        expenseDao.insert(Expense(amount = 8500.0, merchant = "Spend 1", dateMillis = fixedTimestamp, type = "Debit"))
        var progress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), fixedTimestamp)

        // First crossing -> triggers alert
        val firstAlert = budgetManager.evaluateAndDispatchAlert(budget, progress)
        assertTrue(firstAlert)
        assertEquals(1, alertNotifier.warningAlertCount)

        // Subsequent spend still in warning range -> must NOT send duplicate alert
        expenseDao.insert(Expense(amount = 200.0, merchant = "Spend 2", dateMillis = fixedTimestamp, type = "Debit"))
        progress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), fixedTimestamp)
        val secondAlert = budgetManager.evaluateAndDispatchAlert(budget, progress)
        assertFalse(secondAlert)
        assertEquals(1, alertNotifier.warningAlertCount)
    }

    // 23. Duplicate exceeded-alert suppression
    @Test
    fun test23_duplicateExceededAlertSuppression() = runBlocking {
        val budget = budgetManager.createBudget(
            name = "Exceeded Suppression",
            amountLimit = 5000.0,
            warningThreshold = 80.0,
            exceededThreshold = 100.0
        ).getOrThrow()

        expenseDao.insert(Expense(amount = 5500.0, merchant = "Over spend 1", dateMillis = fixedTimestamp, type = "Debit"))
        var progress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), fixedTimestamp)

        val firstAlert = budgetManager.evaluateAndDispatchAlert(budget, progress)
        assertTrue(firstAlert)
        assertEquals(1, alertNotifier.exceededAlertCount)

        // Additional spend after already exceeded -> must NOT send duplicate alert
        expenseDao.insert(Expense(amount = 500.0, merchant = "Over spend 2", dateMillis = fixedTimestamp, type = "Debit"))
        progress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), fixedTimestamp)
        val secondAlert = budgetManager.evaluateAndDispatchAlert(budget, progress)
        assertFalse(secondAlert)
        assertEquals(1, alertNotifier.exceededAlertCount)
    }

    // 24. New-period alert reset
    @Test
    fun test24_newPeriodAlertReset() = runBlocking {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 15, 12, 0) }
        val janTime = cal.timeInMillis
        cal.set(2026, Calendar.FEBRUARY, 15, 12, 0)
        val febTime = cal.timeInMillis

        val budget = budgetManager.createBudget(
            name = "Period Reset",
            amountLimit = 10000.0,
            warningThreshold = 80.0
        ).getOrThrow()

        // January: spend 8500 -> trigger warning
        expenseDao.insert(Expense(amount = 8500.0, merchant = "Jan Spend", dateMillis = janTime, type = "Debit"))
        val janProgress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), janTime)
        assertTrue(budgetManager.evaluateAndDispatchAlert(budget, janProgress))
        assertEquals(1, alertNotifier.warningAlertCount)

        // February: spend 8500 -> triggers warning again because new period started!
        expenseDao.insert(Expense(amount = 8500.0, merchant = "Feb Spend", dateMillis = febTime, type = "Debit"))
        val febProgress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), febTime)
        assertTrue(budgetManager.evaluateAndDispatchAlert(budget, febProgress))
        assertEquals(2, alertNotifier.warningAlertCount)
    }

    // 25. App/repository restart alert-state persistence
    @Test
    fun test25_alertStatePersistenceSurvivesManagerRecreation() = runBlocking {
        val budget = budgetManager.createBudget(
            name = "Persistence Test",
            amountLimit = 10000.0,
            warningThreshold = 80.0
        ).getOrThrow()

        expenseDao.insert(Expense(amount = 8500.0, merchant = "Spend", dateMillis = fixedTimestamp, type = "Debit"))
        val progress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), fixedTimestamp)
        assertTrue(budgetManager.evaluateAndDispatchAlert(budget, progress))
        assertEquals(1, alertNotifier.warningAlertCount)

        // Simulate app restart / recreating BudgetManager with same DAO and notifier
        val newManager = BudgetManager(
            budgetDao = budgetDao,
            expenseDao = expenseDao,
            categoryDao = categoryDao,
            alertNotifier = alertNotifier
        )

        // Recalculating must NOT re-alert
        val progressAfterRestart = newManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), fixedTimestamp)
        val reTriggered = newManager.evaluateAndDispatchAlert(budget, progressAfterRestart)
        assertFalse("Alert should not re-trigger after manager restart", reTriggered)
        assertEquals(1, alertNotifier.warningAlertCount)
    }

    // 26. Multiple budgets
    @Test
    fun test26_multipleBudgetsIndependentTracking() = runBlocking {
        val foodBudget = budgetManager.createBudget("Food", 5000.0, categoryId = BuiltInCategories.FOOD_AND_DINING.id).getOrThrow()
        val travelBudget = budgetManager.createBudget("Travel", 10000.0, categoryId = BuiltInCategories.TRAVEL.id).getOrThrow()

        expenseDao.insert(Expense(amount = 4000.0, merchant = "Airline", dateMillis = fixedTimestamp, type = "Debit", categoryId = BuiltInCategories.TRAVEL.id))
        expenseDao.insert(Expense(amount = 4500.0, merchant = "Restaurant", dateMillis = fixedTimestamp, type = "Debit", categoryId = BuiltInCategories.FOOD_AND_DINING.id))

        val expenses = expenseDao.getAllExpensesList()
        val foodProgress = budgetManager.calculateBudgetProgress(foodBudget, expenses, emptyList(), fixedTimestamp)
        val travelProgress = budgetManager.calculateBudgetProgress(travelBudget, expenses, emptyList(), fixedTimestamp)

        assertEquals(4500.0, foodProgress.spent, 0.001)
        assertEquals(90.0, foodProgress.percentageUsed, 0.001)

        assertEquals(4000.0, travelProgress.spent, 0.001)
        assertEquals(40.0, travelProgress.percentageUsed, 0.001)
    }

    // 27. Overall + category budget simultaneously
    @Test
    fun test27_overallAndCategoryBudgetSimultaneous() = runBlocking {
        val catBudget = budgetManager.createBudget("Food", 5000.0, categoryId = BuiltInCategories.FOOD_AND_DINING.id).getOrThrow()
        val overallBudget = budgetManager.createBudget("Overall", 30000.0, categoryId = null).getOrThrow()

        // 1. Food expense contributes to both
        expenseDao.insert(Expense(amount = 2000.0, merchant = "Supermarket", dateMillis = fixedTimestamp, type = "Debit", categoryId = BuiltInCategories.FOOD_AND_DINING.id))
        // 2. Shopping expense contributes ONLY to overall
        expenseDao.insert(Expense(amount = 3000.0, merchant = "Mall", dateMillis = fixedTimestamp, type = "Debit", categoryId = BuiltInCategories.SHOPPING.id))

        val expenses = expenseDao.getAllExpensesList()
        val catProgress = budgetManager.calculateBudgetProgress(catBudget, expenses, emptyList(), fixedTimestamp)
        val overallProgress = budgetManager.calculateBudgetProgress(overallBudget, expenses, emptyList(), fixedTimestamp)

        assertEquals(2000.0, catProgress.spent, 0.001)
        assertEquals(5000.0, overallProgress.spent, 0.001)
    }

    // 28. Archived/deleted category handling
    @Test
    fun test28_archivedDeletedCategoryHandling() = runBlocking {
        val customCat = TransactionCategory(
            id = "custom_fitness",
            name = "Fitness",
            isArchived = true
        )
        categoryDao.upsert(customCat)

        val budget = budgetManager.createBudget("Gym", 2000.0, categoryId = "custom_fitness").getOrThrow()

        val progress = budgetManager.calculateBudgetProgress(
            budget = budget,
            expenses = emptyList(),
            categories = listOf(customCat),
            referenceTimeMillis = fixedTimestamp
        )

        assertTrue(progress.isCategoryArchived)
        assertFalse(progress.isCategoryDeleted)

        // If category is not in list (deleted)
        val progressDeleted = budgetManager.calculateBudgetProgress(
            budget = budget,
            expenses = emptyList(),
            categories = emptyList(),
            referenceTimeMillis = fixedTimestamp
        )
        assertTrue(progressDeleted.isCategoryDeleted)
    }

    // 29. Analytics and transaction data remain unchanged
    @Test
    fun test29_analyticsAndTransactionsRemainUnchanged() = runBlocking {
        val originalExpense = Expense(
            id = 77,
            amount = 1250.0,
            merchant = "Immutable Store",
            dateMillis = fixedTimestamp,
            type = "Debit",
            notificationKey = "k77",
            categoryId = BuiltInCategories.SHOPPING.id,
            categorySource = CategorySource.USER_ASSIGNED
        )
        expenseDao.insert(originalExpense)

        val budget = budgetManager.createBudget("Shopping", 2000.0, categoryId = BuiltInCategories.SHOPPING.id).getOrThrow()

        // Perform all budget actions: progress, alerts, edit, disable
        budgetManager.evaluateAllBudgets(fixedTimestamp)
        budgetManager.updateBudget(budget.copy(amountLimit = 2500.0))
        budgetManager.setBudgetEnabled(budget.id, false)
        budgetManager.deleteBudget(budget.id)

        // Verify expense remains 100% byte-for-byte untouched
        val reloaded = expenseDao.getExpenseById(77)
        assertNotNull(reloaded)
        assertEquals(originalExpense.id, reloaded!!.id)
        assertEquals(originalExpense.amount, reloaded.amount, 0.0001)
        assertEquals(originalExpense.merchant, reloaded.merchant)
        assertEquals(originalExpense.categoryId, reloaded.categoryId)
        assertEquals(originalExpense.categorySource, reloaded.categorySource)
    }

    // 30. Budget changes do not modify Expense
    @Test
    fun test30_budgetChangesNeverMutateExpense() = runBlocking {
        val initialCount = expenseDao.getCount()
        budgetManager.createBudget("New", 1000.0)
        budgetManager.createBudget("Another", 2000.0)
        assertEquals(initialCount, expenseDao.getCount())
    }

    // 31. Sequence alert test (Section 34 exact sequence)
    @Test
    fun test31_exactAlertTransitionSequence() = runBlocking {
        // Budget = ₹10,000, Warning = 80%, Exceeded = 100%
        val budget = budgetManager.createBudget(
            name = "Sequence Test",
            amountLimit = 10000.0,
            warningThreshold = 80.0,
            exceededThreshold = 100.0
        ).getOrThrow()

        // Initial: ₹7,500 -> no warning
        expenseDao.insert(Expense(id = 1, amount = 7500.0, merchant = "M1", dateMillis = fixedTimestamp, type = "Debit"))
        var progress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), fixedTimestamp)
        var alert = budgetManager.evaluateAndDispatchAlert(budget, progress)
        assertFalse(alert)
        assertEquals(0, alertNotifier.warningAlertCount)
        assertEquals(0, alertNotifier.exceededAlertCount)

        // Add ₹600: total = ₹8,100 -> warning exactly once
        expenseDao.insert(Expense(id = 2, amount = 600.0, merchant = "M2", dateMillis = fixedTimestamp, type = "Debit"))
        progress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), fixedTimestamp)
        alert = budgetManager.evaluateAndDispatchAlert(budget, progress)
        assertTrue(alert)
        assertEquals(1, alertNotifier.warningAlertCount)
        assertEquals(0, alertNotifier.exceededAlertCount)

        // Add ₹100: total = ₹8,200 -> no second warning
        expenseDao.insert(Expense(id = 3, amount = 100.0, merchant = "M3", dateMillis = fixedTimestamp, type = "Debit"))
        progress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), fixedTimestamp)
        alert = budgetManager.evaluateAndDispatchAlert(budget, progress)
        assertFalse(alert)
        assertEquals(1, alertNotifier.warningAlertCount)
        assertEquals(0, alertNotifier.exceededAlertCount)

        // Add ₹2,000: total = ₹10,200 -> exceeded alert exactly once
        expenseDao.insert(Expense(id = 4, amount = 2000.0, merchant = "M4", dateMillis = fixedTimestamp, type = "Debit"))
        progress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), fixedTimestamp)
        alert = budgetManager.evaluateAndDispatchAlert(budget, progress)
        assertTrue(alert)
        assertEquals(1, alertNotifier.warningAlertCount)
        assertEquals(1, alertNotifier.exceededAlertCount)

        // Add another ₹500: total = ₹10,700 -> no duplicate exceeded alert
        expenseDao.insert(Expense(id = 5, amount = 500.0, merchant = "M5", dateMillis = fixedTimestamp, type = "Debit"))
        progress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), fixedTimestamp)
        alert = budgetManager.evaluateAndDispatchAlert(budget, progress)
        assertFalse(alert)
        assertEquals(1, alertNotifier.warningAlertCount)
        assertEquals(1, alertNotifier.exceededAlertCount)

        // Next budget period: -> alert state resets
        val nextPeriodTime = progress.currentPeriodEnd + 1000L
        expenseDao.insert(Expense(id = 6, amount = 8500.0, merchant = "Next Period Spend", dateMillis = nextPeriodTime, type = "Debit"))
        val nextProgress = budgetManager.calculateBudgetProgress(budget, expenseDao.getAllExpensesList(), emptyList(), nextPeriodTime)
        alert = budgetManager.evaluateAndDispatchAlert(budget, nextProgress)
        assertTrue("Alert should trigger in new period", alert)
        assertEquals(2, alertNotifier.warningAlertCount)
    }

    // 32. Reactive flow updates
    @Test
    fun test32_reactiveFlowUpdates() = runBlocking {
        val flow = budgetManager.observeAllBudgetsProgress(fixedTimestamp)
        val initial = flow.first()
        assertTrue(initial.isEmpty())

        budgetManager.createBudget("Reactive Budget", 5000.0)
        val afterCreate = flow.first()
        assertEquals(1, afterCreate.size)
        assertEquals(0.0, afterCreate[0].spent, 0.001)

        expenseDao.insert(Expense(amount = 1200.0, merchant = "Store", dateMillis = fixedTimestamp, type = "Debit"))
        val afterExpense = flow.first()
        assertEquals(1200.0, afterExpense[0].spent, 0.001)
    }
}
