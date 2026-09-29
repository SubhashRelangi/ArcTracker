package com.example.arctracker.service

import com.example.arctracker.data.*
import java.util.Calendar

enum class TransactionTypeFilter { ALL, DEBIT, CREDIT }
enum class TransactionStatusFilter { ALL, PENDING, COMPLETED }
enum class DateRangeFilter { ALL, TODAY, THIS_WEEK, THIS_MONTH, CUSTOM }
enum class TransactionSortBy { NEWEST_FIRST, OLDEST_FIRST, HIGHEST_AMOUNT, LOWEST_AMOUNT }

data class TransactionFilter(
    val searchQuery: String = "",
    val type: TransactionTypeFilter = TransactionTypeFilter.ALL,
    val status: TransactionStatusFilter = TransactionStatusFilter.ALL,
    val category: String? = null,
    val accountId: String? = null,
    val dateRange: DateRangeFilter = DateRangeFilter.ALL,
    val customStartDateMillis: Long? = null,
    val customEndDateMillis: Long? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    val sortBy: TransactionSortBy = TransactionSortBy.NEWEST_FIRST
) {
    val isActive: Boolean
        get() = searchQuery.isNotBlank() ||
                type != TransactionTypeFilter.ALL ||
                status != TransactionStatusFilter.ALL ||
                !category.isNullOrBlank() ||
                !accountId.isNullOrBlank() ||
                dateRange != DateRangeFilter.ALL ||
                minAmount != null ||
                maxAmount != null
}

/**
 * Transaction management and editing layer for Milestones 8 & 9.
 *
 * Provides safe manual editing, deterministic validation, search, filtering, and sorting.
 * Enforces strict boundaries preserving transaction identity fields.
 */
class TransactionManager(
    private val expenseDao: ExpenseDao,
    private val categoryDao: TransactionCategoryDao? = null
) {

    suspend fun getTransaction(id: Int): Expense? = expenseDao.getExpenseById(id)

    /**
     * Safely updates user-editable fields of an existing [Expense].
     *
     * Invariants:
     * - Only user-editable fields (merchant, category/tag, note) are updated.
     * - Merchant is trimmed; cannot be blank.
     * - Category resolves to stable categoryId and canonical category name.
     * - Note can be empty string or null.
     * - System-generated fields (amount, type, dateMillis, notificationKey, rawText,
     *   source, isPending, relationshipType, relationshipId, accountId, accountSuffix)
     *   are strictly preserved.
     */
    suspend fun updateTransaction(
        expenseId: Int,
        merchant: String? = null,
        category: String? = null,
        note: String? = null
    ): Result<Expense> {
        val existing = expenseDao.getExpenseById(expenseId)
            ?: return Result.failure(IllegalArgumentException("Expense with id $expenseId not found"))

        val newMerchant = if (merchant != null) {
            val trimmed = merchant.trim()
            if (trimmed.isEmpty()) {
                return Result.failure(IllegalArgumentException("Merchant name cannot be empty"))
            }
            trimmed
        } else {
            existing.merchant
        }

        val (resolvedCategoryId, resolvedCategoryName, resolvedCategorySource) = if (category != null) {
            val trimmed = category.trim()
            if (trimmed.isEmpty() || trimmed.equals("uncategorized", ignoreCase = true)) {
                Triple(null, null, CategorySource.NONE)
            } else {
                val cat = categoryDao?.getById(trimmed)
                    ?: categoryDao?.findByName(trimmed)
                    ?: BuiltInCategories.findLegacyMapping(trimmed)
                if (cat != null) {
                    Triple(cat.id, cat.name, CategorySource.USER_ASSIGNED)
                } else {
                    Triple(trimmed, trimmed, CategorySource.USER_ASSIGNED)
                }
            }
        } else {
            Triple(existing.categoryId, existing.tag, existing.categorySource)
        }

        val newNote = if (note != null) {
            note.trim().ifEmpty { null }
        } else {
            existing.note
        }

        val updated = existing.copy(
            merchant = newMerchant,
            categoryId = resolvedCategoryId,
            tag = resolvedCategoryName,
            categorySource = resolvedCategorySource,
            note = newNote
        )

        val rows = expenseDao.update(updated)
        if (rows <= 0) {
            return Result.failure(IllegalStateException("Failed to update expense $expenseId in database"))
        }

        return Result.success(updated)
    }

    /**
     * Deletes a single transaction upon explicit user confirmation.
     *
     * Invariants:
     * - Removes only the [Expense] record from the database.
     * - Never deletes or modifies [KnownFinancialAccount] records.
     * - Never modifies notification history or deduplication memory.
     */
    suspend fun deleteTransaction(expenseId: Int): Result<Boolean> {
        val existing = expenseDao.getExpenseById(expenseId)
            ?: return Result.failure(IllegalArgumentException("Expense with id $expenseId not found"))

        val rows = expenseDao.delete(existing)
        return if (rows > 0) {
            Result.success(true)
        } else {
            Result.failure(IllegalStateException("Failed to delete expense $expenseId"))
        }
    }

    /**
     * Filters and sorts a list of transactions based on [TransactionFilter].
     */
    fun filterAndSort(
        expenses: List<Expense>,
        filter: TransactionFilter,
        accounts: Map<String, KnownFinancialAccount> = emptyMap(),
        categories: Map<String, TransactionCategory> = emptyMap(),
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): List<Expense> {
        var result = expenses

        // 1. Search Query (Merchant, Note, Account Suffix/Name, Category Name, Reference)
        if (filter.searchQuery.isNotBlank()) {
            val q = filter.searchQuery.trim().lowercase()
            result = result.filter { expense ->
                val merchantMatch = expense.merchant.lowercase().contains(q)
                val noteMatch = expense.note?.lowercase()?.contains(q) == true
                val suffixMatch = expense.accountSuffix?.lowercase()?.contains(q) == true
                val refMatch = expense.notificationKey.lowercase().contains(q)
                val catMatch = expense.tag?.lowercase()?.contains(q) == true ||
                        (expense.categoryId?.let { categories[it]?.name?.lowercase()?.contains(q) } == true)
                val accountMatch = expense.accountId?.let { accId ->
                    val acc = accounts[accId]
                    acc?.institutionName?.lowercase()?.contains(q) == true ||
                            acc?.accountSuffix?.contains(q) == true
                } ?: false

                merchantMatch || noteMatch || suffixMatch || refMatch || catMatch || accountMatch
            }
        }

        // 2. Type Filter (Debit vs Credit)
        when (filter.type) {
            TransactionTypeFilter.DEBIT -> {
                result = result.filter { it.type.equals("Debit", ignoreCase = true) }
            }
            TransactionTypeFilter.CREDIT -> {
                result = result.filter { it.type.equals("Credit", ignoreCase = true) }
            }
            TransactionTypeFilter.ALL -> {}
        }

        // 3. Status Filter (Pending vs Completed)
        when (filter.status) {
            TransactionStatusFilter.PENDING -> {
                result = result.filter { it.isPending }
            }
            TransactionStatusFilter.COMPLETED -> {
                result = result.filter { !it.isPending }
            }
            TransactionStatusFilter.ALL -> {}
        }

        // 4. Category Filter
        if (!filter.category.isNullOrBlank()) {
            val cat = filter.category.trim()
            if (cat.equals("uncategorized", ignoreCase = true) || cat.equals("None", ignoreCase = true)) {
                result = result.filter { it.categoryId.isNullOrBlank() && it.tag.isNullOrBlank() }
            } else {
                result = result.filter { expense ->
                    val idMatch = expense.categoryId?.equals(cat, ignoreCase = true) == true
                    val tagMatch = expense.tag?.equals(cat, ignoreCase = true) == true
                    val nameMatch = expense.categoryId?.let { cid ->
                        categories[cid]?.name?.equals(cat, ignoreCase = true)
                    } == true
                    idMatch || tagMatch || nameMatch
                }
            }
        }

        // 5. Account Filter
        if (!filter.accountId.isNullOrBlank()) {
            result = result.filter { it.accountId == filter.accountId }
        }

        // 6. Date Range Filter
        when (filter.dateRange) {
            DateRangeFilter.TODAY -> {
                val startOfDay = Calendar.getInstance().apply {
                    timeInMillis = referenceTimeMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val endOfDay = Calendar.getInstance().apply {
                    timeInMillis = referenceTimeMillis
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                result = result.filter { it.dateMillis in startOfDay..endOfDay }
            }
            DateRangeFilter.THIS_WEEK -> {
                val startOfWeek = Calendar.getInstance().apply {
                    timeInMillis = referenceTimeMillis
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val endOfWeek = Calendar.getInstance().apply {
                    timeInMillis = startOfWeek
                    add(Calendar.DAY_OF_YEAR, 7)
                    add(Calendar.MILLISECOND, -1)
                }.timeInMillis
                result = result.filter { it.dateMillis in startOfWeek..endOfWeek }
            }
            DateRangeFilter.THIS_MONTH -> {
                val startOfMonth = Calendar.getInstance().apply {
                    timeInMillis = referenceTimeMillis
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val endOfMonth = Calendar.getInstance().apply {
                    timeInMillis = startOfMonth
                    add(Calendar.MONTH, 1)
                    add(Calendar.MILLISECOND, -1)
                }.timeInMillis
                result = result.filter { it.dateMillis in startOfMonth..endOfMonth }
            }
            DateRangeFilter.CUSTOM -> {
                val start = filter.customStartDateMillis ?: 0L
                val end = filter.customEndDateMillis ?: Long.MAX_VALUE
                result = result.filter { it.dateMillis in start..end }
            }
            DateRangeFilter.ALL -> {}
        }

        // 7. Amount Min / Max Filter
        if (filter.minAmount != null) {
            result = result.filter { it.amount >= filter.minAmount }
        }
        if (filter.maxAmount != null) {
            result = result.filter { it.amount <= filter.maxAmount }
        }

        // 8. Sorting
        result = when (filter.sortBy) {
            TransactionSortBy.NEWEST_FIRST -> result.sortedByDescending { it.dateMillis }
            TransactionSortBy.OLDEST_FIRST -> result.sortedBy { it.dateMillis }
            TransactionSortBy.HIGHEST_AMOUNT -> result.sortedByDescending { it.amount }
            TransactionSortBy.LOWEST_AMOUNT -> result.sortedBy { it.amount }
        }

        return result
    }
}
