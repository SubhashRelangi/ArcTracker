package com.example.arctracker.service

import androidx.room.withTransaction
import com.example.arctracker.data.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Domain and Management Layer for Transaction Categories (Milestone 9).
 *
 * Responsibilities:
 * - Centralized validation for category creation, renaming, and modification.
 * - Enforcing system category safety rules (built-in categories cannot be deleted).
 * - Safe category archiving and restoration.
 * - Transactional reassignment and category merging.
 * - Safe single and bulk transaction category assignment.
 */
class CategoryManager(
    private val categoryDao: TransactionCategoryDao,
    private val expenseDao: ExpenseDao,
    private val database: AppDatabase? = null,
    var ruleDao: UserCategoryRuleDao? = null
) {

    private suspend inline fun <T> runTransaction(crossinline block: suspend () -> T): T {
        return if (database != null) {
            database.withTransaction { block() }
        } else {
            block()
        }
    }

    suspend fun ensureBuiltInCategoriesSeeded() {
        if (categoryDao.count() == 0) {
            categoryDao.insertAll(BuiltInCategories.ALL)
        }
    }

    fun getAllCategoriesFlow(): Flow<List<TransactionCategory>> = categoryDao.getAllFlow()

    suspend fun getAllCategories(): List<TransactionCategory> = categoryDao.getAll()

    fun getActiveCategoriesFlow(): Flow<List<TransactionCategory>> = categoryDao.getActiveFlow()

    suspend fun getActiveCategories(): List<TransactionCategory> = categoryDao.getActive()

    fun getArchivedCategoriesFlow(): Flow<List<TransactionCategory>> = categoryDao.getArchivedFlow()

    suspend fun getArchivedCategories(): List<TransactionCategory> = categoryDao.getArchived()

    suspend fun getCategory(id: String): TransactionCategory? = categoryDao.getById(id)

    suspend fun countTransactionsForCategory(categoryId: String): Int =
        expenseDao.countByCategoryId(categoryId)

    /**
     * Validates a category name for creation or editing.
     */
    fun validateCategoryName(name: String?, excludeCategoryId: String? = null, existingCategories: List<TransactionCategory>): Result<String> {
        if (name == null || name.trim().isEmpty()) {
            return Result.failure(IllegalArgumentException("Category name cannot be empty"))
        }
        val trimmed = name.trim()
        if (trimmed.length > 30) {
            return Result.failure(IllegalArgumentException("Category name cannot exceed 30 characters"))
        }

        val duplicate = existingCategories.find { cat ->
            cat.id != excludeCategoryId && cat.name.trim().equals(trimmed, ignoreCase = true)
        }
        if (duplicate != null) {
            return Result.failure(IllegalArgumentException("A category named '$trimmed' already exists"))
        }

        return Result.success(trimmed)
    }

    /**
     * Creates a new custom category.
     */
    suspend fun createCategory(
        name: String,
        iconKey: String = "category",
        colorKey: String = "default"
    ): Result<TransactionCategory> {
        val existing = categoryDao.getAll()
        val nameValidation = validateCategoryName(name, existingCategories = existing)
        if (nameValidation.isFailure) {
            return Result.failure(nameValidation.exceptionOrNull()!!)
        }
        val validatedName = nameValidation.getOrThrow()

        val validIcon = if (CategoryVisuals.isValidIconKey(iconKey)) iconKey else "category"
        val validColor = if (CategoryVisuals.isValidColorKey(colorKey)) colorKey else "default"

        val maxOrder = categoryDao.getMaxSortOrder() ?: 11
        val newCategory = TransactionCategory(
            id = "custom_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            name = validatedName,
            iconKey = validIcon,
            colorKey = validColor,
            isSystem = false,
            isArchived = false,
            sortOrder = maxOrder + 1,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        categoryDao.upsert(newCategory)
        return Result.success(newCategory)
    }

    /**
     * Updates an existing category (name, icon, color).
     * System categories can be renamed and have their icons/colors updated,
     * but their [isSystem] and [id] remain immutable.
     */
    suspend fun updateCategory(
        id: String,
        name: String,
        iconKey: String,
        colorKey: String
    ): Result<TransactionCategory> = runTransaction {
        val existing = categoryDao.getById(id)
            ?: return@runTransaction Result.failure(IllegalArgumentException("Category with id $id not found"))

        val allCategories = categoryDao.getAll()
        val nameValidation = validateCategoryName(name, excludeCategoryId = id, existingCategories = allCategories)
        if (nameValidation.isFailure) {
            return@runTransaction Result.failure(nameValidation.exceptionOrNull()!!)
        }
        val validatedName = nameValidation.getOrThrow()

        val validIcon = if (CategoryVisuals.isValidIconKey(iconKey)) iconKey else existing.iconKey
        val validColor = if (CategoryVisuals.isValidColorKey(colorKey)) colorKey else existing.colorKey

        val updated = existing.copy(
            name = validatedName,
            iconKey = validIcon,
            colorKey = validColor,
            updatedAt = System.currentTimeMillis()
        )

        categoryDao.update(updated)

        // Keep legacy tag field in sync across referenced expenses
        expenseDao.reassignCategoryId(oldCategoryId = id, newCategoryId = id, newCategoryName = validatedName)

        Result.success(updated)
    }

    /**
     * Archives a category.
     * Historical transactions retain this category, but it will no longer
     * appear in standard selection pickers for new transactions.
     */
    suspend fun archiveCategory(id: String): Result<TransactionCategory> {
        val existing = categoryDao.getById(id)
            ?: return Result.failure(IllegalArgumentException("Category with id $id not found"))

        val updated = existing.copy(
            isArchived = true,
            updatedAt = System.currentTimeMillis()
        )
        categoryDao.update(updated)
        ruleDao?.disableRulesForCategory(id)
        return Result.success(updated)
    }

    /**
     * Restores an archived category to active status.
     */
    suspend fun restoreCategory(id: String): Result<TransactionCategory> {
        val existing = categoryDao.getById(id)
            ?: return Result.failure(IllegalArgumentException("Category with id $id not found"))

        val updated = existing.copy(
            isArchived = false,
            updatedAt = System.currentTimeMillis()
        )
        categoryDao.update(updated)
        return Result.success(updated)
    }

    /**
     * Safely deletes a custom category.
     * System categories CANNOT be deleted.
     *
     * Invariants:
     * - Disallows dangling category IDs: referencing transactions are either
     *   reassigned to [reassignToCategoryId] or unlinked to Uncategorized.
     * - Disallows dangling rules: referencing rules are either reassigned or disabled.
     * - Fully transactional execution.
     */
    suspend fun deleteCategory(
        id: String,
        reassignToCategoryId: String? = null
    ): Result<Int> = runTransaction {
        val category = categoryDao.getById(id)
            ?: return@runTransaction Result.failure(IllegalArgumentException("Category with id $id not found"))

        if (category.isSystem) {
            return@runTransaction Result.failure(IllegalStateException("Built-in system categories cannot be deleted"))
        }

        if (reassignToCategoryId == id) {
            return@runTransaction Result.failure(IllegalArgumentException("Cannot reassign category to itself"))
        }

        val unlinkedOrReassignedCount: Int
        if (reassignToCategoryId != null) {
            val targetCategory = categoryDao.getById(reassignToCategoryId)
                ?: return@runTransaction Result.failure(IllegalArgumentException("Target category $reassignToCategoryId not found"))
            unlinkedOrReassignedCount = expenseDao.reassignCategoryId(
                oldCategoryId = id,
                newCategoryId = targetCategory.id,
                newCategoryName = targetCategory.name
            )
            ruleDao?.getRulesForCategory(id)?.forEach { rule ->
                ruleDao?.update(rule.copy(categoryId = targetCategory.id, updatedAt = System.currentTimeMillis()))
            }
        } else {
            unlinkedOrReassignedCount = expenseDao.clearCategoryId(id)
            ruleDao?.disableRulesForCategory(id)
        }

        categoryDao.deleteById(id)
        Result.success(unlinkedOrReassignedCount)
    }

    /**
     * Merges a source category into a target category.
     *
     * Invariants:
     * - Reassigns all transactions referencing source -> target.
     * - Reassigns all user rules referencing source -> target.
     * - If source is custom: deletes source category.
     * - If source is system: archives source category (system categories are never deleted).
     * - Fully transactional execution.
     */
    suspend fun mergeCategories(sourceId: String, targetId: String): Result<Int> = runTransaction {
        if (sourceId == targetId) {
            return@runTransaction Result.failure(IllegalArgumentException("Cannot merge a category into itself"))
        }
        val source = categoryDao.getById(sourceId)
            ?: return@runTransaction Result.failure(IllegalArgumentException("Source category not found"))
        val target = categoryDao.getById(targetId)
            ?: return@runTransaction Result.failure(IllegalArgumentException("Target category not found"))

        val reassignedCount = expenseDao.reassignCategoryId(
            oldCategoryId = source.id,
            newCategoryId = target.id,
            newCategoryName = target.name
        )

        ruleDao?.getRulesForCategory(source.id)?.forEach { rule ->
            ruleDao?.update(rule.copy(categoryId = target.id, updatedAt = System.currentTimeMillis()))
        }

        if (source.isSystem) {
            categoryDao.update(source.copy(isArchived = true, updatedAt = System.currentTimeMillis()))
        } else {
            categoryDao.deleteById(source.id)
        }

        Result.success(reassignedCount)
    }

    /**
     * Assigns or unlinks category on a single transaction.
     *
     * Invariants:
     * - Strictly updates categoryId and tag.
     * - Preserves amount, type, date, merchant, account, suffix, relationshipType, relationshipId, isPending.
     */
    suspend fun assignCategory(expenseId: Int, categoryId: String?): Result<Expense> = runTransaction {
        val expense = expenseDao.getExpenseById(expenseId)
            ?: return@runTransaction Result.failure(IllegalArgumentException("Expense with id $expenseId not found"))

        if (categoryId.isNullOrBlank()) {
            expenseDao.updateCategoryMetadata(expenseId, null, null, CategorySource.NONE)
            return@runTransaction Result.success(expense.copy(categoryId = null, tag = null, categorySource = CategorySource.NONE))
        }

        val category = categoryDao.getById(categoryId)
            ?: BuiltInCategories.findLegacyMapping(categoryId)
            ?: return@runTransaction Result.failure(IllegalArgumentException("Category $categoryId not found"))

        expenseDao.updateCategoryMetadata(expenseId, category.id, category.name, CategorySource.USER_ASSIGNED)
        Result.success(expense.copy(categoryId = category.id, tag = category.name, categorySource = CategorySource.USER_ASSIGNED))
    }

    /**
     * Atomically bulk-assigns or unlinks category across multiple transactions.
     */
    suspend fun bulkAssignCategory(expenseIds: List<Int>, categoryId: String?): Result<Int> = runTransaction {
        if (expenseIds.isEmpty()) return@runTransaction Result.success(0)

        if (categoryId.isNullOrBlank()) {
            val count = expenseDao.bulkUpdateCategoryId(expenseIds, null, null, CategorySource.NONE)
            return@runTransaction Result.success(count)
        }

        val category = categoryDao.getById(categoryId)
            ?: BuiltInCategories.findLegacyMapping(categoryId)
            ?: return@runTransaction Result.failure(IllegalArgumentException("Category $categoryId not found"))

        val count = expenseDao.bulkUpdateCategoryId(expenseIds, category.id, category.name, CategorySource.USER_ASSIGNED)
        Result.success(count)
    }

    /**
     * Reorders categories according to the specified list of IDs.
     */
    suspend fun reorderCategories(orderedIds: List<String>): Result<Unit> = runTransaction {
        orderedIds.forEachIndexed { index, id ->
            val cat = categoryDao.getById(id)
            if (cat != null) {
                categoryDao.update(cat.copy(sortOrder = index, updatedAt = System.currentTimeMillis()))
            }
        }
        Result.success(Unit)
    }
}
