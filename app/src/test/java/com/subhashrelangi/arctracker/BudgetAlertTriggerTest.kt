package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.Budget
import com.subhashrelangi.arctracker.data.BudgetPeriodType
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.TransactionCategory
import com.subhashrelangi.arctracker.data.TransactionRelationshipType
import com.subhashrelangi.arctracker.engine.budget.BudgetManager
import com.subhashrelangi.arctracker.engine.notification.BudgetAlertTrigger
import com.subhashrelangi.arctracker.engine.notification.DailyDigestEvaluator
import com.subhashrelangi.arctracker.engine.notification.NotificationChannelManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class BudgetAlertTriggerTest {

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

        budgetManager = BudgetManager(
            budgetDao = budgetDao,
            expenseDao = expenseDao,
            categoryDao = categoryDao,
            alertNotifier = alertNotifier
        )
    }

    @Test
    fun testIsEligibleForBudgetEvaluation() {
        val validDebit = Expense(
            id = 1,
            amount = 500.0,
            merchant = "Swiggy",
            dateMillis = fixedTimestamp,
            type = "Debit",
            isPending = false
        )
        assertTrue(BudgetAlertTrigger.isEligibleForBudgetEvaluation(validDebit))

        val pendingExpense = validDebit.copy(isPending = true)
        assertFalse(BudgetAlertTrigger.isEligibleForBudgetEvaluation(pendingExpense))

        val creditTransaction = validDebit.copy(type = "Credit")
        assertFalse(BudgetAlertTrigger.isEligibleForBudgetEvaluation(creditTransaction))

        val selfTransfer = validDebit.copy(relationshipType = TransactionRelationshipType.SELF_TRANSFER)
        assertFalse(BudgetAlertTrigger.isEligibleForBudgetEvaluation(selfTransfer))
    }

    @Test
    fun testInstantDailyLimitCrossing_triggersAlertOnTransition() {
        val limit = 2000.0
        val spendBefore = 1800.0
        val spendNow = 2200.0 // Just crossed 2,000

        val payload = DailyDigestEvaluator.buildInstantDailyLimitAlert(
            totalSpentBefore = spendBefore,
            totalSpentNow = spendNow,
            dailyLimitAmount = limit
        )

        assertNotNull(payload)
        assertTrue(payload!!.title.contains("Daily Limit Reached"))
        assertEquals(NotificationChannelManager.CHANNEL_BUDGET_ALERTS, payload.channelId)
        assertEquals("Budgets", payload.targetDestination)
    }

    @Test
    fun testInstantDailyLimitCrossing_suppressesSubsequentTransactions() {
        val limit = 2000.0
        // Spend was already above limit earlier today
        val spendBefore = 2200.0
        val spendNow = 2500.0

        val payload = DailyDigestEvaluator.buildInstantDailyLimitAlert(
            totalSpentBefore = spendBefore,
            totalSpentNow = spendNow,
            dailyLimitAmount = limit
        )

        // Must NOT spam user again on subsequent purchases!
        assertNull(payload)
    }

    @Test
    fun testInstantDailyLimitCrossing_whenUnderLimit_returnsNull() {
        val limit = 2000.0
        val spendBefore = 500.0
        val spendNow = 1000.0

        val payload = DailyDigestEvaluator.buildInstantDailyLimitAlert(
            totalSpentBefore = spendBefore,
            totalSpentNow = spendNow,
            dailyLimitAmount = limit
        )

        assertNull(payload)
    }

    @Test
    fun testOverallBudgetCrossingWarningAndExceededThresholds() = runBlocking {
        val budget = Budget(
            id = UUID.randomUUID().toString(),
            name = "Monthly Overall",
            amountLimit = 10000.0,
            categoryId = null, // Overall budget
            periodType = BudgetPeriodType.MONTHLY.name,
            warningThreshold = 80.0,
            exceededThreshold = 100.0,
            isEnabled = true,
            createdAt = fixedTimestamp,
            updatedAt = fixedTimestamp
        )
        budgetDao.insert(budget)

        // Spend ₹8,500 (85%) -> warning triggered
        val exp1 = Expense(
            id = 1,
            amount = 8500.0,
            merchant = "Croma",
            dateMillis = fixedTimestamp,
            type = "Debit",
            isPending = false
        )
        expenseDao.insert(exp1)

        val progressList1 = budgetManager.evaluateAllBudgets(fixedTimestamp)
        assertEquals(1, progressList1.size)
        assertEquals(1, alertNotifier.warningAlertCount)
        assertEquals(0, alertNotifier.exceededAlertCount)

        // Adding another expense that stays in warning (₹9,000 total) -> NO duplicate warning alert
        val exp2 = Expense(
            id = 2,
            amount = 500.0,
            merchant = "Starbucks",
            dateMillis = fixedTimestamp,
            type = "Debit",
            isPending = false
        )
        expenseDao.insert(exp2)

        budgetManager.evaluateAllBudgets(fixedTimestamp)
        assertEquals("Warning alert must not repeat within same period", 1, alertNotifier.warningAlertCount)
        assertEquals(0, alertNotifier.exceededAlertCount)

        // Spend crossing ₹10,000 (Exceeded) -> Exceeded alert triggered
        val exp3 = Expense(
            id = 3,
            amount = 1500.0, // Total now 10,500 (105%)
            merchant = "Apple Store",
            dateMillis = fixedTimestamp,
            type = "Debit",
            isPending = false
        )
        expenseDao.insert(exp3)

        budgetManager.evaluateAllBudgets(fixedTimestamp)
        assertEquals(1, alertNotifier.exceededAlertCount)
    }

    @Test
    fun testCategoryEnvelopeBudgetAlertTrigger() = runBlocking {
        val catId = "food_dining"
        val category = TransactionCategory(
            id = catId,
            name = "Food & Dining",
            colorKey = "orange",
            iconKey = "restaurant",
            isSystem = true
        )
        categoryDao.upsert(category)

        val foodBudget = Budget(
            id = UUID.randomUUID().toString(),
            name = "Food Envelope",
            amountLimit = 5000.0,
            categoryId = catId,
            periodType = BudgetPeriodType.MONTHLY.name,
            warningThreshold = 80.0,
            exceededThreshold = 100.0,
            isEnabled = true,
            createdAt = fixedTimestamp,
            updatedAt = fixedTimestamp
        )
        budgetDao.insert(foodBudget)

        // Insert non-food transaction -> Should NOT affect food envelope
        val shoppingExp = Expense(
            id = 1,
            amount = 6000.0,
            merchant = "Zara",
            dateMillis = fixedTimestamp,
            type = "Debit",
            categoryId = "shopping",
            isPending = false
        )
        expenseDao.insert(shoppingExp)

        budgetManager.evaluateAllBudgets(fixedTimestamp)
        assertEquals(0, alertNotifier.warningAlertCount)
        assertEquals(0, alertNotifier.exceededAlertCount)

        // Insert Food transaction reaching ₹4,200 (84%) -> Triggers Food envelope warning
        val foodExp = Expense(
            id = 2,
            amount = 4200.0,
            merchant = "Blinkit",
            dateMillis = fixedTimestamp,
            type = "Debit",
            categoryId = catId,
            isPending = false
        )
        expenseDao.insert(foodExp)

        budgetManager.evaluateAllBudgets(fixedTimestamp)
        assertEquals(1, alertNotifier.warningAlertCount)
        assertEquals("Food Envelope", alertNotifier.warnings.first().first.name)
    }
}
