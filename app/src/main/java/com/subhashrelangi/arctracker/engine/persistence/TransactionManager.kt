package com.subhashrelangi.arctracker.engine.persistence

import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.engine.category.CategorySource
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
     * - Merchant is trimmed; cannot be blank when updated.
     * - Amount cannot be negative.
     * - Timestamp (dateMillis) is strictly preserved if not provided.
     * - Category resolves to stable categoryId and canonical category name.
     * - Note can be empty string or null.
     * - System-generated audit trail (notificationKey, rawText, source, relationshipId) is preserved.
     */
    suspend fun updateTransaction(
        expenseId: Int,
        merchant: String? = null,
        category: String? = null,
        note: String? = null,
        amount: Double? = null,
        dateMillis: Long? = null,
        type: String? = null,
        categoryId: String? = null,
        accountId: String? = null,
        accountSuffix: String? = null
    ): Result<Expense> {
        return try {
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

            val newAmount = if (amount != null) {
                if (amount < 0.0) {
                    return Result.failure(IllegalArgumentException("Amount cannot be negative"))
                }
                amount
            } else {
                existing.amount
            }

            val newDateMillis = if (dateMillis != null && dateMillis > 0L) {
                dateMillis
            } else {
                existing.dateMillis
            }

            val newType = if (type != null) {
                when {
                    type.equals("Credit", ignoreCase = true) || type.equals("Income", ignoreCase = true) -> "Credit"
                    type.equals("Transfer", ignoreCase = true) -> "Transfer"
                    else -> "Debit"
                }
            } else {
                existing.type
            }

            val (resolvedCategoryId, resolvedCategoryName, resolvedCategorySource) = if (categoryId != null && categoryId.isNotBlank()) {
                val cat = categoryDao?.getById(categoryId.trim())
                    ?: BuiltInCategories.findLegacyMapping(categoryId.trim())
                if (cat != null) {
                    Triple(cat.id, cat.name, CategorySource.USER_ASSIGNED)
                } else {
                    Triple(categoryId.trim(), category?.trim() ?: categoryId.trim(), CategorySource.USER_ASSIGNED)
                }
            } else if (category != null) {
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

            val newAccountId = if (accountId != null) {
                accountId.trim().ifEmpty { null }
            } else {
                existing.accountId
            }

            val newAccountSuffix = if (accountSuffix != null) {
                accountSuffix.trim().ifEmpty { null }
            } else {
                existing.accountSuffix
            }

            val updated = existing.copy(
                merchant = newMerchant,
                amount = newAmount,
                dateMillis = newDateMillis,
                type = newType,
                categoryId = resolvedCategoryId,
                tag = resolvedCategoryName,
                categorySource = resolvedCategorySource,
                note = newNote,
                accountId = newAccountId,
                accountSuffix = newAccountSuffix
            )

            val rows = expenseDao.update(updated)
            if (rows <= 0) {
                Result.failure(IllegalStateException("Failed to update expense $expenseId in database"))
            } else {
                Result.success(updated)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTransaction(expense: Expense): Result<Expense> = updateTransaction(
        expenseId = expense.id,
        merchant = expense.merchant,
        category = expense.tag ?: expense.category,
        note = expense.note,
        amount = expense.amount,
        dateMillis = expense.dateMillis,
        type = expense.type,
        categoryId = expense.categoryId,
        accountId = expense.accountId,
        accountSuffix = expense.accountSuffix
    )

    /**
     * Deletes a single transaction upon explicit user confirmation.
     *
     * Invariants:
     * - Safely disassociates any paired self-transfer counterpart so the surviving record
     *   is correctly reflected in spending/income aggregations.
     * - If the transaction is not a self-transfer, deletes only the target record.
     * - Never deletes or modifies [KnownFinancialAccount] records.
     * - Never modifies notification history or deduplication memory.
     * - Executes atomically; rolls back completely on database write failure.
     */
    suspend fun deleteTransaction(expenseId: Int): Result<Boolean> {
        return try {
            val existing = expenseDao.getExpenseById(expenseId)
                ?: return Result.failure(IllegalArgumentException("Expense with id $expenseId not found"))

            val rows = expenseDao.deleteSelfTransferAware(expenseId)
            if (rows > 0) {
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("Failed to delete expense $expenseId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
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

        // 1. Search Query (Merchant, Note, Account Suffix/Name, Category Name, Reference, Amount)
        if (filter.searchQuery.isNotBlank()) {
            result = result.filter { expense ->
                val categoryName = expense.categoryId?.let { categories[it]?.name }
                val acc = expense.accountId?.let { accounts[it] }
                com.subhashrelangi.arctracker.ui.core.SmartSearchMatcher.matches(
                    filter.searchQuery,
                    expense.merchant,
                    expense.category,
                    categoryName,
                    expense.tag,
                    expense.note,
                    expense.rawText,
                    expense.accountSuffix,
                    acc?.institutionName,
                    acc?.accountSuffix,
                    expense.amount.toString(),
                    expense.type
                )
            }.sortedByDescending {
                com.subhashrelangi.arctracker.ui.core.SmartSearchMatcher.score(filter.searchQuery, it.merchant, it.category, it.note)
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
