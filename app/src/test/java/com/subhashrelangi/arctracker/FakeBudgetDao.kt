package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.Budget
import com.subhashrelangi.arctracker.data.BudgetAlertState
import com.subhashrelangi.arctracker.data.BudgetDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory test implementation of [BudgetDao].
 */
class FakeBudgetDao : BudgetDao {

    private val lock = Any()
    private val budgets = mutableListOf<Budget>()
    private val alertStates = mutableMapOf<String, BudgetAlertState>()

    private val budgetsFlow = MutableStateFlow<List<Budget>>(emptyList())

    private fun notifyFlow() {
        budgetsFlow.value = synchronized(lock) { budgets.toList() }
    }

    override suspend fun insert(budget: Budget): Long = synchronized(lock) {
        budgets.removeAll { it.id == budget.id }
        budgets.add(budget)
        notifyFlow()
        1L
    }

    override suspend fun insertAll(budgets: List<Budget>): List<Long> = synchronized(lock) {
        val ids = budgets.map { it.id }.toSet()
        this.budgets.removeAll { it.id in ids }
        this.budgets.addAll(budgets)
        notifyFlow()
        budgets.map { 1L }
    }

    override suspend fun update(budget: Budget): Int = synchronized(lock) {
        val idx = budgets.indexOfFirst { it.id == budget.id }
        if (idx >= 0) {
            budgets[idx] = budget
            notifyFlow()
            1
        } else 0
    }

    override suspend fun delete(budget: Budget): Int = synchronized(lock) {
        val removed = budgets.removeAll { it.id == budget.id }
        if (removed) {
            notifyFlow()
            1
        } else 0
    }

    override suspend fun deleteById(id: String): Int = synchronized(lock) {
        val removed = budgets.removeAll { it.id == id }
        if (removed) {
            notifyFlow()
            1
        } else 0
    }

    override suspend fun getById(id: String): Budget? = synchronized(lock) {
        budgets.find { it.id == id }
    }

    override fun observeById(id: String): Flow<Budget?> {
        return budgetsFlow.map { list -> list.find { it.id == id } }
    }

    override suspend fun getAll(): List<Budget> = synchronized(lock) {
        budgets.sortedByDescending { it.createdAt }
    }

    override fun getAllFlow(): Flow<List<Budget>> {
        return budgetsFlow.map { list -> list.sortedByDescending { it.createdAt } }
    }

    override suspend fun getActive(): List<Budget> = synchronized(lock) {
        budgets.filter { it.isEnabled }.sortedByDescending { it.createdAt }
    }

    override fun getActiveFlow(): Flow<List<Budget>> {
        return budgetsFlow.map { list -> list.filter { it.isEnabled }.sortedByDescending { it.createdAt } }
    }

    override suspend fun getByCategory(categoryId: String): List<Budget> = synchronized(lock) {
        budgets.filter { it.categoryId == categoryId }
    }

    override suspend fun disableBudgetsForCategory(categoryId: String, timestamp: Long): Int = synchronized(lock) {
        var count = 0
        for (i in budgets.indices) {
            if (budgets[i].categoryId == categoryId && budgets[i].isEnabled) {
                budgets[i] = budgets[i].copy(isEnabled = false, updatedAt = timestamp)
                count++
            }
        }
        if (count > 0) notifyFlow()
        count
    }

    override suspend fun setBudgetEnabled(id: String, isEnabled: Boolean, timestamp: Long): Int = synchronized(lock) {
        val idx = budgets.indexOfFirst { it.id == id }
        if (idx >= 0) {
            budgets[idx] = budgets[idx].copy(isEnabled = isEnabled, updatedAt = timestamp)
            notifyFlow()
            1
        } else 0
    }

    override suspend fun getCount(): Int = synchronized(lock) {
        budgets.size
    }

    override suspend fun clearAll(): Int = synchronized(lock) {
        val count = budgets.size
        budgets.clear()
        notifyFlow()
        count
    }

    override suspend fun upsertAlertState(state: BudgetAlertState): Long = synchronized(lock) {
        alertStates[state.id] = state
        1L
    }

    override suspend fun getAlertState(budgetId: String, periodStart: Long): BudgetAlertState? = synchronized(lock) {
        val key = BudgetAlertState.createId(budgetId, periodStart)
        alertStates[key]
    }

    override suspend fun getAlertStateById(id: String): BudgetAlertState? = synchronized(lock) {
        alertStates[id]
    }

    override suspend fun deleteAlertStatesForBudget(budgetId: String): Int = synchronized(lock) {
        val keysToRemove = alertStates.keys.filter { it.startsWith("${budgetId}_") }
        keysToRemove.forEach { alertStates.remove(it) }
        keysToRemove.size
    }

    override suspend fun clearAllAlertStates(): Int = synchronized(lock) {
        val count = alertStates.size
        alertStates.clear()
        count
    }
}
