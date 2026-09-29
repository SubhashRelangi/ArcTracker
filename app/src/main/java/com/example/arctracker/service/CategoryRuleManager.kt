package com.example.arctracker.service

import com.example.arctracker.data.TransactionCategoryDao
import com.example.arctracker.data.UserCategoryRule
import com.example.arctracker.data.UserCategoryRuleDao
import com.example.arctracker.data.UserRuleMatchType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Domain manager for user-defined category rules (Milestone 11).
 *
 * Responsibilities:
 * - Creates, validates, updates, enables/disables, and deletes user categorization rules.
 * - Enforces category existence and active status invariants (never references archived/deleted categories).
 * - Detects duplicate and conflicting user rules.
 * - In-memory caching for sub-millisecond local inference.
 */
class CategoryRuleManager(
    private val ruleDao: UserCategoryRuleDao,
    private val categoryDao: TransactionCategoryDao
) {
    private val mutex = Mutex()
    private var activeCache: List<UserCategoryRule>? = null

    /**
     * Creates a new user category rule with strict validation.
     */
    suspend fun createRule(
        pattern: String,
        categoryId: String,
        matchType: String = UserRuleMatchType.MERCHANT_EXACT,
        priority: Int = 100,
        name: String? = null
    ): Result<UserCategoryRule> = mutex.withLock {
        val trimmedPattern = pattern.trim()
        if (trimmedPattern.isBlank()) {
            return Result.failure(IllegalArgumentException("Rule pattern cannot be blank"))
        }

        if (categoryId.isBlank()) {
            return Result.failure(IllegalArgumentException("Category ID cannot be blank"))
        }

        if (!UserRuleMatchType.ALL.contains(matchType)) {
            return Result.failure(IllegalArgumentException("Unsupported match type: $matchType"))
        }

        val normPattern = MerchantNormalizer.normalize(trimmedPattern, stripPrefixes = false)
        if (normPattern.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid rule pattern after normalization"))
        }

        // Validate target category existence and active status
        val category = categoryDao.getById(categoryId)
            ?: return Result.failure(IllegalArgumentException("Category does not exist: $categoryId"))

        if (category.isArchived) {
            return Result.failure(IllegalArgumentException("Cannot create rule for an archived category: '${category.name}'"))
        }

        val existingRules = ruleDao.getAll()

        // Duplicate and Conflict detection
        val duplicate = existingRules.find {
            it.matchType == matchType && it.normalizedPattern == normPattern
        }
        if (duplicate != null) {
            return if (duplicate.categoryId == categoryId) {
                Result.failure(IllegalArgumentException("Duplicate: A rule for '$trimmedPattern' ($matchType) pointing to '${category.name}' already exists"))
            } else {
                val conflictingCat = categoryDao.getById(duplicate.categoryId)?.name ?: duplicate.categoryId
                Result.failure(IllegalArgumentException("Conflict: A rule for '$trimmedPattern' ($matchType) already exists pointing to '$conflictingCat'"))
            }
        }

        val newRule = UserCategoryRule(
            id = UUID.randomUUID().toString(),
            name = name?.trim()?.ifBlank { null },
            matchType = matchType,
            pattern = trimmedPattern,
            normalizedPattern = normPattern,
            categoryId = categoryId,
            priority = priority,
            isEnabled = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        ruleDao.insert(newRule)
        invalidateCache()
        Result.success(newRule)
    }

    /**
     * Updates an existing user rule.
     */
    suspend fun updateRule(
        id: String,
        pattern: String,
        categoryId: String,
        matchType: String,
        priority: Int,
        isEnabled: Boolean,
        name: String? = null
    ): Result<UserCategoryRule> = mutex.withLock {
        val existing = ruleDao.getById(id)
            ?: return Result.failure(IllegalArgumentException("Rule not found with id: $id"))

        val trimmedPattern = pattern.trim()
        if (trimmedPattern.isBlank()) {
            return Result.failure(IllegalArgumentException("Rule pattern cannot be blank"))
        }

        if (categoryId.isBlank()) {
            return Result.failure(IllegalArgumentException("Category ID cannot be blank"))
        }

        if (!UserRuleMatchType.ALL.contains(matchType)) {
            return Result.failure(IllegalArgumentException("Unsupported match type: $matchType"))
        }

        val normPattern = MerchantNormalizer.normalize(trimmedPattern, stripPrefixes = false)
        if (normPattern.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid rule pattern after normalization"))
        }

        val category = categoryDao.getById(categoryId)
            ?: return Result.failure(IllegalArgumentException("Category does not exist: $categoryId"))

        if (category.isArchived && isEnabled) {
            return Result.failure(IllegalArgumentException("Cannot assign an archived category to an enabled rule"))
        }

        val otherRules = ruleDao.getAll().filter { it.id != id }

        val duplicate = otherRules.find {
            it.matchType == matchType && it.normalizedPattern == normPattern
        }
        if (duplicate != null) {
            val conflictingCat = categoryDao.getById(duplicate.categoryId)?.name ?: duplicate.categoryId
            return Result.failure(IllegalArgumentException("Conflict: Another rule for '$trimmedPattern' ($matchType) already exists pointing to '$conflictingCat'"))
        }

        val updated = existing.copy(
            name = name?.trim()?.ifBlank { null },
            matchType = matchType,
            pattern = trimmedPattern,
            normalizedPattern = normPattern,
            categoryId = categoryId,
            priority = priority,
            isEnabled = isEnabled,
            updatedAt = System.currentTimeMillis()
        )

        ruleDao.update(updated)
        invalidateCache()
        Result.success(updated)
    }

    /**
     * Toggles rule enabled/disabled.
     */
    suspend fun setRuleEnabled(id: String, enabled: Boolean): Result<Unit> = mutex.withLock {
        val existing = ruleDao.getById(id)
            ?: return Result.failure(IllegalArgumentException("Rule not found with id: $id"))

        if (enabled) {
            val cat = categoryDao.getById(existing.categoryId)
            if (cat == null || cat.isArchived) {
                return Result.failure(IllegalStateException("Cannot enable rule: referenced category is unavailable or archived"))
            }
        }

        val updated = existing.copy(isEnabled = enabled, updatedAt = System.currentTimeMillis())
        ruleDao.update(updated)
        invalidateCache()
        Result.success(Unit)
    }

    /**
     * Deletes a user rule safely.
     */
    suspend fun deleteRule(id: String): Result<Unit> = mutex.withLock {
        ruleDao.deleteById(id)
        invalidateCache()
        Result.success(Unit)
    }

    /**
     * Reactive continuous flow of all user rules.
     */
    fun getAllFlow(): Flow<List<UserCategoryRule>> = ruleDao.getAllFlow()

    /**
     * Continuous flow of active rules.
     */
    fun getActiveRulesFlow(): Flow<List<UserCategoryRule>> = ruleDao.getActiveRulesFlow()

    /**
     * Cached list of active enabled rules for high-speed local inference.
     */
    suspend fun getActiveRules(): List<UserCategoryRule> = mutex.withLock {
        activeCache ?: run {
            val list = ruleDao.getActiveRules()
            activeCache = list
            list
        }
    }

    /**
     * Disables any user rules pointing to the given category (used when a category is archived or deleted).
     */
    suspend fun handleCategoryDeactivated(categoryId: String) = mutex.withLock {
        ruleDao.disableRulesForCategory(categoryId)
        invalidateCache()
    }

    private fun invalidateCache() {
        activeCache = null
    }
}
