package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.ExpenseDao
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

    override fun getExpensesBetweenFlow(startMillis: Long, endMillis: Long): Flow<List<Expense>> {
        return expensesFlow.map { list ->
            list.filter { it.dateMillis in startMillis..endMillis }.sortedByDescending { it.dateMillis }
        }
    }

    override suspend fun getTotalAmountBetween(startMillis: Long, endMillis: Long, type: String): Double = synchronized(lock) {
        expenses.filter {
            it.dateMillis in startMillis..endMillis &&
                it.type.equals(type, ignoreCase = true) &&
                !it.isPending &&
                (it.relationshipType == null || it.relationshipType != com.subhashrelangi.arctracker.data.TransactionRelationshipType.SELF_TRANSFER)
        }.sumOf { it.amount }
    }

    override suspend fun getExpensesByRelationshipId(relationshipId: String): List<Expense> = synchronized(lock) {
        if (relationshipId.isBlank()) emptyList() else expenses.filter { it.relationshipId == relationshipId }
    }

    override suspend fun updateAccountId(id: Int, accountId: String?): Int = synchronized(lock) {
        val idx = expenses.indexOfFirst { it.id == id }
        if (idx >= 0) {
            expenses[idx] = expenses[idx].copy(accountId = accountId)
            notifyFlow()
            1
        } else {
            0
        }
    }

    override suspend fun updateAccountMetadata(id: Int, accountId: String?, accountSuffix: String?): Int = synchronized(lock) {
        val idx = expenses.indexOfFirst { it.id == id }
        if (idx >= 0) {
            expenses[idx] = expenses[idx].copy(accountId = accountId, accountSuffix = accountSuffix)
            notifyFlow()
            1
        } else {
            0
        }
    }

    override suspend fun reassignAccountId(oldAccountId: String, newAccountId: String): Int = synchronized(lock) {
        var count = 0
        for (i in 0 until expenses.size) {
            if (expenses[i].accountId == oldAccountId) {
                expenses[i] = expenses[i].copy(accountId = newAccountId)
                count++
            }
        }
        if (count > 0) notifyFlow()
        count
    }

    override suspend fun clearAccountIdForAccount(accountId: String): Int = synchronized(lock) {
        var count = 0
        for (i in 0 until expenses.size) {
            if (expenses[i].accountId == accountId) {
                expenses[i] = expenses[i].copy(accountId = null)
                count++
            }
        }
        if (count > 0) notifyFlow()
        count
    }

    override suspend fun bulkUpdateAccountId(expenseIds: List<Int>, accountId: String?): Int = synchronized(lock) {
        var count = 0
        val idSet = expenseIds.toSet()
        for (i in 0 until expenses.size) {
            if (expenses[i].id in idSet) {
                expenses[i] = expenses[i].copy(accountId = accountId)
                count++
            }
        }
        if (count > 0) notifyFlow()
        count
    }

    override suspend fun countByAccountId(accountId: String): Int = synchronized(lock) {
        expenses.count { it.accountId == accountId }
    }

    override suspend fun getExpensesWithoutAccount(): List<Expense> = synchronized(lock) {
        expenses.filter { it.accountId == null }.sortedByDescending { it.dateMillis }
    }

    override fun getExpensesWithoutAccountFlow(): Flow<List<Expense>> {
        return expensesFlow.map { list -> list.filter { it.accountId == null }.sortedByDescending { it.dateMillis } }
    }

    override suspend fun getExpensesByAccountId(accountId: String): List<Expense> = synchronized(lock) {
        expenses.filter { it.accountId == accountId }.sortedByDescending { it.dateMillis }
    }

    override fun getExpensesByAccountIdFlow(accountId: String): Flow<List<Expense>> {
        return expensesFlow.map { list -> list.filter { it.accountId == accountId }.sortedByDescending { it.dateMillis } }
    }

    override suspend fun getExpensesByAccountSuffix(suffix: String): List<Expense> = synchronized(lock) {
        expenses.filter { it.accountSuffix == suffix }.sortedByDescending { it.dateMillis }
    }

    override suspend fun getExpensesByCategory(category: String): List<Expense> = synchronized(lock) {
        expenses.filter { it.tag == category }.sortedByDescending { it.dateMillis }
    }

    override fun getExpensesByCategoryFlow(category: String): Flow<List<Expense>> {
        return expensesFlow.map { list -> list.filter { it.tag == category }.sortedByDescending { it.dateMillis } }
    }

    override suspend fun searchExpensesList(query: String): List<Expense> = synchronized(lock) {
        if (query.isBlank()) return@synchronized expenses.sortedByDescending { it.dateMillis }.toList()
        val q = query.trim().lowercase()
        expenses.filter {
            it.merchant.lowercase().contains(q) || (it.note?.lowercase()?.contains(q) == true)
        }.sortedByDescending { it.dateMillis }
    }

    override fun searchExpensesFlow(query: String): Flow<List<Expense>> {
        if (query.isBlank()) return getAllExpenses()
        val q = query.trim().lowercase()
        return expensesFlow.map { list ->
            list.filter {
                it.merchant.lowercase().contains(q) || (it.note?.lowercase()?.contains(q) == true)
            }.sortedByDescending { it.dateMillis }
        }
    }

    override suspend fun updateCategoryMetadata(id: Int, categoryId: String?, categoryName: String?, categorySource: String): Int = synchronized(lock) {
        val idx = expenses.indexOfFirst { it.id == id }
        if (idx >= 0) {
            expenses[idx] = expenses[idx].copy(categoryId = categoryId, tag = categoryName, categorySource = categorySource)
            notifyFlow()
            1
        } else {
            0
        }
    }

    override suspend fun updateCategoryMetadataIfNotUserAssigned(id: Int, categoryId: String?, categoryName: String?, categorySource: String): Int = synchronized(lock) {
        val idx = expenses.indexOfFirst { it.id == id }
        if (idx >= 0 && expenses[idx].categorySource != com.subhashrelangi.arctracker.service.CategorySource.USER_ASSIGNED) {
            expenses[idx] = expenses[idx].copy(categoryId = categoryId, tag = categoryName, categorySource = categorySource)
            notifyFlow()
            1
        } else {
            0
        }
    }

    override suspend fun reassignCategoryId(oldCategoryId: String, newCategoryId: String, newCategoryName: String, categorySource: String): Int = synchronized(lock) {
        var count = 0
        for (i in 0 until expenses.size) {
            if (expenses[i].categoryId == oldCategoryId) {
                expenses[i] = expenses[i].copy(categoryId = newCategoryId, tag = newCategoryName, categorySource = categorySource)
                count++
            }
        }
        if (count > 0) notifyFlow()
        count
    }

    override suspend fun clearCategoryId(categoryId: String): Int = synchronized(lock) {
        var count = 0
        for (i in 0 until expenses.size) {
            if (expenses[i].categoryId == categoryId) {
                expenses[i] = expenses[i].copy(categoryId = null, tag = null, categorySource = "NONE")
                count++
            }
        }
        if (count > 0) notifyFlow()
        count
    }

    override suspend fun bulkUpdateCategoryId(expenseIds: List<Int>, categoryId: String?, categoryName: String?, categorySource: String): Int = synchronized(lock) {
        val idSet = expenseIds.toSet()
        var count = 0
        for (i in 0 until expenses.size) {
            if (expenses[i].id in idSet) {
                expenses[i] = expenses[i].copy(categoryId = categoryId, tag = categoryName, categorySource = categorySource)
                count++
            }
        }
        if (count > 0) notifyFlow()
        count
    }

    override suspend fun countByCategoryId(categoryId: String): Int = synchronized(lock) {
        expenses.count { it.categoryId == categoryId }
    }

    override suspend fun getExpensesByCategoryId(categoryId: String): List<Expense> = synchronized(lock) {
        expenses.filter { it.categoryId == categoryId }.sortedByDescending { it.dateMillis }
    }

    override fun getExpensesByCategoryIdFlow(categoryId: String): Flow<List<Expense>> {
        return expensesFlow.map { list -> list.filter { it.categoryId == categoryId }.sortedByDescending { it.dateMillis } }
    }

    override suspend fun getUncategorizedExpenses(): List<Expense> = synchronized(lock) {
        expenses.filter { it.categoryId.isNullOrBlank() }.sortedByDescending { it.dateMillis }
    }

    override fun getUncategorizedExpensesFlow(): Flow<List<Expense>> {
        return expensesFlow.map { list -> list.filter { it.categoryId.isNullOrBlank() }.sortedByDescending { it.dateMillis } }
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
