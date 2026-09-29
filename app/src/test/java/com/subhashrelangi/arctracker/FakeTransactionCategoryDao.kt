package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.TransactionCategory
import com.subhashrelangi.arctracker.data.TransactionCategoryDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory thread-safe fake for [TransactionCategoryDao].
 */
class FakeTransactionCategoryDao : TransactionCategoryDao {

    private val lock = Any()
    private val categories = mutableListOf<TransactionCategory>()
    private val categoriesFlow = MutableStateFlow<List<TransactionCategory>>(emptyList())

    private fun notifyFlow() {
        categoriesFlow.value = categories.sortedWith(compareBy<TransactionCategory> { it.sortOrder }.thenBy { it.createdAt }).toList()
    }

    override suspend fun upsert(category: TransactionCategory): Long = synchronized(lock) {
        val idx = categories.indexOfFirst { it.id == category.id }
        if (idx >= 0) {
            categories[idx] = category
        } else {
            categories.add(category)
        }
        notifyFlow()
        1L
    }

    override suspend fun insertAll(categories: List<TransactionCategory>): List<Long> = synchronized(lock) {
        val result = mutableListOf<Long>()
        for (cat in categories) {
            if (this.categories.none { it.id == cat.id }) {
                this.categories.add(cat)
                result.add(1L)
            } else {
                result.add(-1L)
            }
        }
        notifyFlow()
        result
    }

    override suspend fun update(category: TransactionCategory): Int = synchronized(lock) {
        val idx = categories.indexOfFirst { it.id == category.id }
        if (idx >= 0) {
            categories[idx] = category
            notifyFlow()
            1
        } else {
            0
        }
    }

    override suspend fun delete(category: TransactionCategory): Int = synchronized(lock) {
        val removed = categories.removeIf { it.id == category.id }
        if (removed) notifyFlow()
        if (removed) 1 else 0
    }

    override suspend fun deleteById(id: String): Int = synchronized(lock) {
        val removed = categories.removeIf { it.id == id }
        if (removed) notifyFlow()
        if (removed) 1 else 0
    }

    override suspend fun getById(id: String): TransactionCategory? = synchronized(lock) {
        categories.find { it.id == id }
    }

    override fun getAllFlow(): Flow<List<TransactionCategory>> {
        return categoriesFlow.asStateFlow()
    }

    override suspend fun getAll(): List<TransactionCategory> = synchronized(lock) {
        categories.sortedWith(compareBy<TransactionCategory> { it.sortOrder }.thenBy { it.createdAt }).toList()
    }

    override fun getActiveFlow(): Flow<List<TransactionCategory>> {
        return categoriesFlow.map { list -> list.filter { !it.isArchived } }
    }

    override suspend fun getActive(): List<TransactionCategory> = synchronized(lock) {
        categories.filter { !it.isArchived }.sortedWith(compareBy<TransactionCategory> { it.sortOrder }.thenBy { it.createdAt }).toList()
    }

    override fun getArchivedFlow(): Flow<List<TransactionCategory>> {
        return categoriesFlow.map { list -> list.filter { it.isArchived } }
    }

    override suspend fun getArchived(): List<TransactionCategory> = synchronized(lock) {
        categories.filter { it.isArchived }.sortedWith(compareBy<TransactionCategory> { it.sortOrder }.thenBy { it.createdAt }).toList()
    }

    override suspend fun findByName(name: String): TransactionCategory? = synchronized(lock) {
        val trimmed = name.trim().lowercase()
        categories.find { it.name.trim().lowercase() == trimmed }
    }

    override suspend fun count(): Int = synchronized(lock) {
        categories.size
    }

    override suspend fun getMaxSortOrder(): Int? = synchronized(lock) {
        categories.maxOfOrNull { it.sortOrder }
    }
}
