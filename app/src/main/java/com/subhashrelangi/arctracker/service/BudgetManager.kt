package com.subhashrelangi.arctracker.service

import com.subhashrelangi.arctracker.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar
import java.util.UUID
import kotlin.math.ceil

/**
 * Production-quality, read-only-over-transactions Budgeting Engine (Milestone 14).
 *
 * Guarantees:
 * - Downstream only: Consumes persisted transactions; NEVER alters or creates expenses.
 * - Calendar-aware recurring periods: Weekly, Monthly, Yearly with custom anchor handling.
 * - Self-transfer safe: Excludes [TransactionRelationshipType.SELF_TRANSFER] from spending.
 * - Strict category isolation: Does NOT infer or guess uncategorized transactions into category budgets.
 * - Alert suppression: State-persisted, transition-based alert delivery preventing spam.
 * - Clean category lifecycle safety: Gracefully detects archived or deleted categories.
 */
class BudgetManager(
    private val budgetDao: BudgetDao,
    private val expenseDao: ExpenseDao,
    private val categoryDao: TransactionCategoryDao? = null,
    private val alertNotifier: BudgetAlertNotifier? = null
) {

    /**
     * Creates a new budget configuration.
     */
    suspend fun createBudget(
        name: String,
        amountLimit: Double,
        categoryId: String? = null,
        periodType: BudgetPeriodType = BudgetPeriodType.MONTHLY,
        periodAnchor: Int = 1,
        warningThreshold: Double = 80.0,
        exceededThreshold: Double = 100.0
    ): Result<Budget> {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Budget name cannot be empty"))
        }
        if (amountLimit <= 0.0 || amountLimit.isNaN() || amountLimit.isInfinite()) {
            return Result.failure(IllegalArgumentException("Budget amount limit must be greater than zero"))
        }
        if (warningThreshold <= 0.0 || warningThreshold > exceededThreshold) {
            return Result.failure(IllegalArgumentException("Warning threshold must be positive and not exceed exceeded threshold"))
        }
        if (exceededThreshold < warningThreshold) {
            return Result.failure(IllegalArgumentException("Exceeded threshold must be greater than or equal to warning threshold"))
        }

        val cleanCategoryId = categoryId?.trim()?.ifBlank { null }
        if (cleanCategoryId != null) {
            val cat = categoryDao?.getById(cleanCategoryId)
            if (cat == null && BuiltInCategories.findLegacyMapping(cleanCategoryId) == null) {
                return Result.failure(IllegalArgumentException("Referenced category does not exist"))
            }
        }

        val budget = Budget(
            id = UUID.randomUUID().toString(),
            name = trimmedName,
            amountLimit = amountLimit,
            categoryId = cleanCategoryId,
            periodType = periodType.name,
            periodAnchor = periodAnchor.coerceIn(1, 31),
            warningThreshold = warningThreshold,
            exceededThreshold = exceededThreshold,
            isEnabled = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        budgetDao.insert(budget)
        return Result.success(budget)
    }

    /**
     * Updates an existing budget configuration.
     */
    suspend fun updateBudget(budget: Budget): Result<Budget> {
        val existing = budgetDao.getById(budget.id)
            ?: return Result.failure(IllegalArgumentException("Budget with id ${budget.id} not found"))

        val trimmedName = budget.name.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Budget name cannot be empty"))
        }
        if (budget.amountLimit <= 0.0 || budget.amountLimit.isNaN() || budget.amountLimit.isInfinite()) {
            return Result.failure(IllegalArgumentException("Budget amount limit must be greater than zero"))
        }
        if (budget.warningThreshold <= 0.0 || budget.warningThreshold > budget.exceededThreshold) {
            return Result.failure(IllegalArgumentException("Warning threshold must be positive and not exceed exceeded threshold"))
        }
        if (budget.exceededThreshold < budget.warningThreshold) {
            return Result.failure(IllegalArgumentException("Exceeded threshold must be greater than or equal to warning threshold"))
        }

        val cleanCategoryId = budget.categoryId?.trim()?.ifBlank { null }
        if (cleanCategoryId != null) {
            val cat = categoryDao?.getById(cleanCategoryId)
            if (cat == null && BuiltInCategories.findLegacyMapping(cleanCategoryId) == null) {
                return Result.failure(IllegalArgumentException("Referenced category does not exist"))
            }
        }

        val updated = budget.copy(
            name = trimmedName,
            categoryId = cleanCategoryId,
            periodAnchor = budget.periodAnchor.coerceIn(1, 31),
            updatedAt = System.currentTimeMillis()
        )

        budgetDao.update(updated)
        return Result.success(updated)
    }

    /**
     * Toggles the active/paused state of a budget.
     */
    suspend fun setBudgetEnabled(id: String, isEnabled: Boolean): Result<Boolean> {
        val count = budgetDao.setBudgetEnabled(id, isEnabled)
        return if (count > 0) Result.success(true)
        else Result.failure(IllegalArgumentException("Budget with id $id not found"))
    }

    /**
     * Deletes a budget and clears its alert history.
     * Guaranteed: Transactions are NEVER deleted or mutated.
     */
    suspend fun deleteBudget(id: String): Result<Boolean> {
        budgetDao.deleteAlertStatesForBudget(id)
        val count = budgetDao.deleteById(id)
        return if (count > 0) Result.success(true)
        else Result.failure(IllegalArgumentException("Budget with id $id not found"))
    }

    /**
     * Evaluates all budgets for the current period and dispatches alerts if thresholds are crossed.
     */
    suspend fun evaluateAllBudgets(referenceTimeMillis: Long = System.currentTimeMillis()): List<BudgetProgress> {
        val budgets = budgetDao.getAll()
        val expenses = expenseDao.getAllExpensesList()
        val categories = categoryDao?.getAll() ?: emptyList()

        return budgets.map { budget ->
            val progress = calculateBudgetProgress(budget, expenses, categories, referenceTimeMillis)
            if (budget.isEnabled) {
                evaluateAndDispatchAlert(budget, progress)
            }
            progress
        }
    }

    /**
     * Calculates deterministic [BudgetProgress] from persisted transactions.
     */
    fun calculateBudgetProgress(
        budget: Budget,
        expenses: List<Expense>,
        categories: List<TransactionCategory>,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): BudgetProgress {
        val bounds = BudgetPeriodBounds.calculate(
            periodType = budget.parsedPeriodType,
            anchor = budget.periodAnchor,
            referenceTimeMillis = referenceTimeMillis
        )

        val categoriesMap = (categories + BuiltInCategories.ALL).associateBy { it.id }
        val category = budget.categoryId?.let { categoriesMap[it] }

        val isCategoryDeleted = budget.categoryId != null && category == null
        val isCategoryArchived = category?.isArchived ?: false

        // Eligible transactions:
        // 1. Must be inside current period
        // 2. Must be Debit (Expense)
        // 3. Must NOT be SELF_TRANSFER
        val inPeriodExpenses = expenses.filter { exp ->
            exp.dateMillis in bounds.startMillis..bounds.endMillis &&
                exp.type.equals("Debit", ignoreCase = true) &&
                exp.relationshipType != TransactionRelationshipType.SELF_TRANSFER
        }

        // Category matching:
        // If overall budget: all in-period expenses
        // If category budget: strictly matching categoryId (no guessing on uncategorized!)
        val eligibleExpenses = if (budget.isOverallBudget) {
            inPeriodExpenses
        } else {
            inPeriodExpenses.filter { exp ->
                val resolvedCatId = exp.categoryId?.takeIf { it.isNotBlank() }
                    ?: BuiltInCategories.findLegacyMapping(exp.tag)?.id
                resolvedCatId == budget.categoryId
            }
        }

        val settledExpenses = eligibleExpenses.filter { !it.isPending }
        val pendingExpenses = eligibleExpenses.filter { it.isPending }

        val spent = settledExpenses.sumOf { it.amount }
        val pendingSpent = pendingExpenses.sumOf { it.amount }
        val remaining = maxOf(0.0, budget.amountLimit - spent)

        val percentageUsed = if (budget.amountLimit > 0.0) {
            (spent / budget.amountLimit) * 100.0
        } else 0.0

        val status = when {
            percentageUsed >= budget.exceededThreshold -> BudgetStatus.EXCEEDED
            percentageUsed >= budget.warningThreshold -> BudgetStatus.WARNING
            else -> BudgetStatus.UNDER_BUDGET
        }

        // Simple, safe projection: average daily spend * remaining days
        val elapsedMillis = maxOf(1L, referenceTimeMillis - bounds.startMillis)
        val elapsedDays = maxOf(1, ceil(elapsedMillis.toDouble() / (24.0 * 60 * 60 * 1000)).toInt())
        val avgDailySpend = spent / elapsedDays.toDouble()
        val projectedSpent = spent + (avgDailySpend * bounds.daysRemaining)

        return BudgetProgress(
            budget = budget,
            categoryName = category?.name ?: if (budget.isOverallBudget) "All Expenses" else (budget.categoryId ?: "Unknown"),
            categoryIconKey = category?.iconKey ?: if (budget.isOverallBudget) "account_balance_wallet" else "category",
            categoryColorKey = category?.colorKey ?: "default",
            limit = budget.amountLimit,
            spent = spent,
            remaining = remaining,
            percentageUsed = percentageUsed,
            status = status,
            currentPeriodStart = bounds.startMillis,
            currentPeriodEnd = bounds.endMillis,
            daysRemaining = bounds.daysRemaining,
            eligibleTransactionCount = settledExpenses.size,
            pendingSpent = pendingSpent,
            projectedSpent = projectedSpent,
            warningThreshold = budget.warningThreshold,
            exceededThreshold = budget.exceededThreshold,
            isCategoryArchived = isCategoryArchived,
            isCategoryDeleted = isCategoryDeleted
        )
    }

    /**
     * Checks alert thresholds and dispatches notifications if crossing a transition boundary.
     * Ensures alerts are strictly transition-based and duplicate alerts are suppressed.
     */
    suspend fun evaluateAndDispatchAlert(budget: Budget, progress: BudgetProgress): Boolean {
        if (!budget.isEnabled) return false

        val periodStart = progress.currentPeriodStart
        val stateId = BudgetAlertState.createId(budget.id, periodStart)
        val existingState = budgetDao.getAlertState(budget.id, periodStart)

        var warningSent = existingState?.warningSent ?: false
        var exceededSent = existingState?.exceededSent ?: false
        var alertTriggered = false

        if (progress.isExceeded) {
            if (!exceededSent) {
                alertNotifier?.sendExceededAlert(budget, progress)
                exceededSent = true
                warningSent = true // Exceeded implies warning threshold was also passed
                alertTriggered = true
            }
        } else if (progress.isWarning) {
            if (!warningSent) {
                alertNotifier?.sendWarningAlert(budget, progress)
                warningSent = true
                alertTriggered = true
            }
        }

        if (alertTriggered || existingState == null) {
            val newState = BudgetAlertState(
                id = stateId,
                budgetId = budget.id,
                periodStart = periodStart,
                warningSent = warningSent,
                exceededSent = exceededSent,
                updatedAt = System.currentTimeMillis()
            )
            budgetDao.upsertAlertState(newState)
        }

        return alertTriggered
    }

    /**
     * Retrieves all settled transactions that contributed to a budget during the current period.
     */
    suspend fun getContributingTransactions(
        budget: Budget,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): List<Expense> {
        val bounds = BudgetPeriodBounds.calculate(
            periodType = budget.parsedPeriodType,
            anchor = budget.periodAnchor,
            referenceTimeMillis = referenceTimeMillis
        )

        val inPeriodExpenses = expenseDao.getExpensesBetween(bounds.startMillis, bounds.endMillis)
            .filter { exp ->
                exp.type.equals("Debit", ignoreCase = true) &&
                    exp.relationshipType != TransactionRelationshipType.SELF_TRANSFER &&
                    !exp.isPending
            }

        return if (budget.isOverallBudget) {
            inPeriodExpenses
        } else {
            inPeriodExpenses.filter { exp ->
                val resolvedCatId = exp.categoryId?.takeIf { it.isNotBlank() }
                    ?: BuiltInCategories.findLegacyMapping(exp.tag)?.id
                resolvedCatId == budget.categoryId
            }
        }
    }

    /**
     * Observes reactive updates of all budgets and their progress.
     */
    fun observeAllBudgetsProgress(
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): Flow<List<BudgetProgress>> {
        val budgetsFlow = budgetDao.getAllFlow()
        val expensesFlow = expenseDao.getAllExpenses()
        val categoriesFlow = categoryDao?.getAllFlow()

        return if (categoriesFlow != null) {
            combine(budgetsFlow, expensesFlow, categoriesFlow) { budgets, expenses, categories ->
                budgets.map { budget ->
                    calculateBudgetProgress(budget, expenses, categories, referenceTimeMillis)
                }
            }
        } else {
            combine(budgetsFlow, expensesFlow) { budgets, expenses ->
                budgets.map { budget ->
                    calculateBudgetProgress(budget, expenses, emptyList(), referenceTimeMillis)
                }
            }
        }
    }
}
