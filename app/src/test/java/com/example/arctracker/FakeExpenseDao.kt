package com.example.arctracker

import com.example.arctracker.data.Expense
import com.example.arctracker.data.ExpenseDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory thread-safe implementation of [ExpenseDao] for unit testing.
 */
open class FakeExpenseDao : ExpenseDao {

    private val lock = Any()
    private val idCounter = AtomicInteger(1)
    private val expenses = mutableListOf<Expense>()
    private val expensesFlow = MutableStateFlow<List<Expense>>(emptyList())

    private fun notifyFlow() {
        expensesFlow.value = expenses.toList()
    }

    override suspend fun insert(expense: Expense): Long = synchronized(lock) {
        val assignedId = if (expense.id <= 0) {
            idCounter.getAndIncrement()
        } else {
            val existing = expenses.find { it.id == expense.id }
            if (existing != null) {
                throw IllegalStateException("UNIQUE constraint failed: expenses.id ${expense.id} already exists. Use update() instead.")
            }
            if (expense.id >= idCounter.get()) {
                idCounter.set(expense.id + 1)
            }
            expense.id
        }
        val copy = expense.copy(id = assignedId)
        expenses.add(0, copy)
        notifyFlow()
        assignedId.toLong()
    }

    override suspend fun insertAll(expenses: List<Expense>): List<Long> {
        return expenses.map { insert(it) }
    }

    override suspend fun update(expense: Expense): Int = synchronized(lock) {
        val idx = expenses.indexOfFirst { it.id == expense.id }
        if (idx >= 0) {
            expenses[idx] = expense
            notifyFlow()
            1
        } else {
            0
        }
    }

    override suspend fun delete(expense: Expense): Int = synchronized(lock) {
        val removed = expenses.removeIf { it.id == expense.id }
        if (removed) notifyFlow()
        if (removed) 1 else 0
    }

    override fun getAllExpenses(): Flow<List<Expense>> {
        return expensesFlow.asStateFlow()
    }

    override suspend fun getAllExpensesList(): List<Expense> = synchronized(lock) {
        expenses.sortedByDescending { it.dateMillis }.toList()
    }

    override fun getPendingExpenses(): Flow<List<Expense>> {
        return expensesFlow.map { list -> list.filter { it.isPending }.sortedByDescending { it.dateMillis } }
    }

    override suspend fun getPendingExpensesList(): List<Expense> = synchronized(lock) {
        expenses.filter { it.isPending }.sortedByDescending { it.dateMillis }
    }

    override suspend fun getExpenseById(id: Int): Expense? = synchronized(lock) {
        expenses.find { it.id == id }
    }

    override suspend fun getExpenseByNotificationKey(key: String): Expense? = synchronized(lock) {
        if (key.isBlank()) null else expenses.find { it.notificationKey == key }
    }

    override suspend fun findByNotificationKey(key: String): List<Expense> = synchronized(lock) {
        if (key.isBlank()) emptyList() else expenses.filter { it.notificationKey == key }
    }

    override suspend fun getExpensesBetween(startMillis: Long, endMillis: Long): List<Expense> = synchronized(lock) {
        expenses.filter { it.dateMillis in startMillis..endMillis }.sortedByDescending { it.dateMillis }
    }

    override suspend fun getCount(): Int = synchronized(lock) {
        expenses.size
    }

    override suspend fun clearAll(): Int = synchronized(lock) {
        val count = expenses.size
        expenses.clear()
        notifyFlow()
        count
    }
}
