package com.subhashrelangi.arctracker.service

import androidx.room.withTransaction
import com.subhashrelangi.arctracker.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Status of an individual transaction during historical rule preview evaluation (Milestone 12).
 */
enum class PreviewChangeStatus {
    /**
     * The transaction will receive a new or updated category upon application.
     */
    WILL_CHANGE,

    /**
     * The inferred category matches the transaction's current category.
     */
    ALREADY_MATCHES,

    /**
     * The transaction was explicitly categorized by the user (categorySource == USER_ASSIGNED).
     * Protected from any automated or historical rule changes.
     */
    PROTECTED_USER_ASSIGNED,

    /**
     * The proposed category is invalid, archived, or deleted.
     */
    INACTIVE_OR_ARCHIVED_CATEGORY,

    /**
     * The existing inference engine could not classify or match this transaction.
     */
    NO_MATCH
}

/**
 * Detailed preview item for a single transaction evaluated against existing rules.
 */
data class HistoricalReassignmentPreviewItem(
    val transactionId: Int,
    val merchant: String,
    val amount: Double,
    val dateMillis: Long,
    val currentCategoryId: String?,
    val currentCategoryName: String?,
    val currentCategorySource: String,
    val proposedCategoryId: String?,
    val proposedCategoryName: String?,
    val proposedCategorySource: String,
    val changeStatus: PreviewChangeStatus,
    val skipReason: String? = null,
    val confidence: CategoryInferenceConfidence? = null,
    val matchedRuleNameOrPattern: String? = null,
    val evidenceDescription: String? = null
)

/**
 * Summary of evaluating existing transactions against category rules.
 */
data class HistoricalRulePreviewResult(
    val items: List<HistoricalReassignmentPreviewItem>,
    val totalEvaluated: Int,
    val willChangeCount: Int,
    val alreadyMatchesCount: Int,
    val protectedCount: Int,
    val cannotChangeCount: Int
) {
    val actionableItems: List<HistoricalReassignmentPreviewItem>
        get() = items.filter { it.changeStatus == PreviewChangeStatus.WILL_CHANGE }
}

/**
 * Result of executing bulk historical rule reassignment.
 */
data class BulkReassignmentResult(
    val successCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

/**
 * Historical Rule Application & Bulk Re-Categorization Service (Milestone 12).
 *
 * Responsibilities:
 * 1. Evaluates existing transactions against the existing CategoryInferenceEngine, user rules, and aliases.
 * 2. Produces a non-destructive, explainable preview.
 * 3. Enforces immutable USER_ASSIGNED category protection.
 * 4. Strictly validates category lifecycle (never assigns archived or deleted categories).
 * 5. Executes transactional bulk updates modifying only category-related metadata.
 * 6. Preserves all financial and account metadata.
 * 7. Never performs background automatic re-categorization (strictly user-triggered).
 */
class HistoricalRuleApplier(
    private val expenseDao: ExpenseDao,
    private val categoryDao: TransactionCategoryDao,
    private val ruleDao: UserCategoryRuleDao,
    private val aliasDao: MerchantAliasDao,
    private val database: AppDatabase? = null,
    private val inferenceEngine: CategoryInferenceEngine = CategoryInferenceEngine()
) {

    private suspend inline fun <T> runTransaction(crossinline block: suspend () -> T): T {
        return if (database != null) {
            database.withTransaction { block() }
        } else {
            block()
        }
    }

    /**
     * Evaluates existing transactions against active user rules, merchant aliases, and built-in rules.
     *
     * Invariants:
     * - USER_ASSIGNED transactions are strictly protected and marked as PROTECTED_USER_ASSIGNED.
     * - Only active categories can be proposed (archived/deleted categories are strictly excluded).
     * - Identical category matches are marked as ALREADY_MATCHES.
     * - Deterministic: identical input produces identical preview.
     * - Cancellation safe: checks coroutine cancellation between evaluations.
     * - Zero writes to database.
     *
     * @param specificRuleId Optional ID of a specific user rule to apply. If null, all active rules are evaluated.
     * @param onProgress Optional progress callback (evaluatedCount, totalCount).
     */
    suspend fun generatePreview(
        specificRuleId: String? = null,
        onProgress: ((Int, Int) -> Unit)? = null
    ): HistoricalRulePreviewResult {
        // Load active categories and build lookup maps
        val allCategories = categoryDao.getAll()
        val activeCategories = allCategories.filter { !it.isArchived }
        val activeCategoryIds = activeCategories.map { it.id }.toSet()
        val categoryNameMap = allCategories.associate { it.id to it.name }

        // Load active user rules
        val activeRules = if (specificRuleId != null) {
            val rule = ruleDao.getById(specificRuleId)
            if (rule != null && rule.isEnabled && activeCategoryIds.contains(rule.categoryId)) {
                listOf(rule)
            } else {
                emptyList()
            }
        } else {
            ruleDao.getActiveRules().filter { activeCategoryIds.contains(it.categoryId) }
        }

        // Load active merchant aliases
        val activeAliases = aliasDao.getActiveAliases()

        // Load all expenses
        val expenses = expenseDao.getAllExpensesList()
        val total = expenses.size
        val previewItems = ArrayList<HistoricalReassignmentPreviewItem>(total)

        var willChange = 0
        var alreadyMatches = 0
        var protected = 0
        var cannotChange = 0

        for ((index, expense) in expenses.withIndex()) {
            coroutineContext.ensureActive()

            val currentCatId = expense.categoryId?.takeIf { it.isNotBlank() }
            val currentCatName = currentCatId?.let { categoryNameMap[it] } ?: expense.tag?.takeIf { it.isNotBlank() }

            // 1. Mandatory USER_ASSIGNED Protection Check
            if (expense.categorySource == CategorySource.USER_ASSIGNED) {
                protected++
                previewItems.add(
                    HistoricalReassignmentPreviewItem(
                        transactionId = expense.id,
                        merchant = expense.merchant,
                        amount = expense.amount,
                        dateMillis = expense.dateMillis,
                        currentCategoryId = currentCatId,
                        currentCategoryName = currentCatName,
                        currentCategorySource = expense.categorySource,
                        proposedCategoryId = currentCatId,
                        proposedCategoryName = currentCatName,
                        proposedCategorySource = expense.categorySource,
                        changeStatus = PreviewChangeStatus.PROTECTED_USER_ASSIGNED,
                        skipReason = "Protected by user manual assignment",
                        confidence = CategoryInferenceConfidence.HIGH,
                        matchedRuleNameOrPattern = null,
                        evidenceDescription = "User-assigned category is explicitly protected"
                    )
                )
                onProgress?.invoke(index + 1, total)
                continue
            }

            // 2. Evaluate using EXISTING CategoryInferenceEngine
            val input = CategoryInferenceInput(
                merchant = expense.merchant,
                counterparty = null,
                rawText = expense.rawText,
                transactionType = expense.type,
                amount = expense.amount,
                existingCategoryId = currentCatId,
                existingCategorySource = expense.categorySource,
                availableCategoryIds = activeCategoryIds,
                userRules = activeRules,
                merchantAliases = activeAliases
            )

            val inferenceResult = inferenceEngine.inferCategory(input)

            val proposedCatId = if (inferenceResult.isAutoAssignable) inferenceResult.suggestedCategoryId else null
            val proposedCatName = proposedCatId?.let { categoryNameMap[it] ?: inferenceResult.suggestedCategoryName }

            val evidenceDesc = inferenceResult.evidence.firstOrNull()?.description
            val matchedRuleStr = inferenceResult.matchedUserRuleId?.let { ruleId ->
                activeRules.find { it.id == ruleId }?.let { it.name ?: it.pattern }
            }

            // 3. Determine Preview Change Status
            if (proposedCatId == null) {
                cannotChange++
                previewItems.add(
                    HistoricalReassignmentPreviewItem(
                        transactionId = expense.id,
                        merchant = expense.merchant,
                        amount = expense.amount,
                        dateMillis = expense.dateMillis,
                        currentCategoryId = currentCatId,
                        currentCategoryName = currentCatName,
                        currentCategorySource = expense.categorySource,
                        proposedCategoryId = null,
                        proposedCategoryName = null,
                        proposedCategorySource = CategorySource.NONE,
                        changeStatus = PreviewChangeStatus.NO_MATCH,
                        skipReason = "No matching category rule",
                        confidence = inferenceResult.confidence,
                        matchedRuleNameOrPattern = null,
                        evidenceDescription = evidenceDesc
                    )
                )
            } else if (!activeCategoryIds.contains(proposedCatId)) {
                cannotChange++
                previewItems.add(
                    HistoricalReassignmentPreviewItem(
                        transactionId = expense.id,
                        merchant = expense.merchant,
                        amount = expense.amount,
                        dateMillis = expense.dateMillis,
                        currentCategoryId = currentCatId,
                        currentCategoryName = currentCatName,
                        currentCategorySource = expense.categorySource,
                        proposedCategoryId = proposedCatId,
                        proposedCategoryName = proposedCatName,
                        proposedCategorySource = CategorySource.NONE,
                        changeStatus = PreviewChangeStatus.INACTIVE_OR_ARCHIVED_CATEGORY,
                        skipReason = "Proposed category is inactive or archived",
                        confidence = inferenceResult.confidence,
                        matchedRuleNameOrPattern = matchedRuleStr,
                        evidenceDescription = evidenceDesc
                    )
                )
            } else if (proposedCatId == currentCatId) {
                alreadyMatches++
                previewItems.add(
                    HistoricalReassignmentPreviewItem(
                        transactionId = expense.id,
                        merchant = expense.merchant,
                        amount = expense.amount,
                        dateMillis = expense.dateMillis,
                        currentCategoryId = currentCatId,
                        currentCategoryName = currentCatName,
                        currentCategorySource = expense.categorySource,
                        proposedCategoryId = proposedCatId,
                        proposedCategoryName = proposedCatName,
                        proposedCategorySource = CategorySource.INFERRED,
                        changeStatus = PreviewChangeStatus.ALREADY_MATCHES,
                        skipReason = "Already matches proposed category",
                        confidence = inferenceResult.confidence,
                        matchedRuleNameOrPattern = matchedRuleStr,
                        evidenceDescription = evidenceDesc
                    )
                )
            } else {
                willChange++
                previewItems.add(
                    HistoricalReassignmentPreviewItem(
                        transactionId = expense.id,
                        merchant = expense.merchant,
                        amount = expense.amount,
                        dateMillis = expense.dateMillis,
                        currentCategoryId = currentCatId,
                        currentCategoryName = currentCatName,
                        currentCategorySource = expense.categorySource,
                        proposedCategoryId = proposedCatId,
                        proposedCategoryName = proposedCatName,
                        proposedCategorySource = CategorySource.INFERRED,
                        changeStatus = PreviewChangeStatus.WILL_CHANGE,
                        skipReason = null,
                        confidence = inferenceResult.confidence,
                        matchedRuleNameOrPattern = matchedRuleStr,
                        evidenceDescription = evidenceDesc
                    )
                )
            }

            onProgress?.invoke(index + 1, total)
        }

        return HistoricalRulePreviewResult(
            items = previewItems,
            totalEvaluated = total,
            willChangeCount = willChange,
            alreadyMatchesCount = alreadyMatches,
            protectedCount = protected,
            cannotChangeCount = cannotChange
        )
    }

    /**
     * Atomically executes bulk updates for the selected preview items.
     *
     * Invariants:
     * - Zero selection: no database write, returns early.
     * - USER_ASSIGNED protection: re-verifies every transaction at write time. If any transaction has become
     *   USER_ASSIGNED since preview, it is strictly skipped.
     * - Stale data safety: re-verifies existence of transaction. If missing, skipped.
     * - Category validity: re-verifies target category is still active.
     * - Preserves all non-category transaction fields.
     * - Atomic Room transaction: rollback on failure, leaving zero half-updated state.
     */
    suspend fun applyReassignments(
        selectedItems: List<HistoricalReassignmentPreviewItem>
    ): BulkReassignmentResult {
        if (selectedItems.isEmpty()) {
            return BulkReassignmentResult(
                successCount = 0,
                skippedCount = 0,
                failedCount = 0,
                isSuccess = true
            )
        }

        // Get currently active category IDs for validation
        val activeCategoryIds = categoryDao.getActive().map { it.id }.toSet()
        val allCategoryNames = categoryDao.getAll().associate { it.id to it.name }

        var successCount = 0
        var skippedCount = 0

        try {
            runTransaction {
                for (item in selectedItems) {
                    coroutineContext.ensureActive()

                    // Guard: item proposed category must be valid and active
                    val targetCatId = item.proposedCategoryId
                    if (targetCatId == null || !activeCategoryIds.contains(targetCatId)) {
                        skippedCount++
                        continue
                    }

                    // Re-fetch fresh transaction state from database
                    val freshExpense = expenseDao.getExpenseById(item.transactionId)
                    if (freshExpense == null) {
                        skippedCount++
                        continue
                    }

                    // Mandatory Concurrency/Stale Protection: NEVER overwrite USER_ASSIGNED
                    if (freshExpense.categorySource == CategorySource.USER_ASSIGNED) {
                        skippedCount++
                        continue
                    }

                    val catName = item.proposedCategoryName
                        ?: allCategoryNames[targetCatId]
                        ?: BuiltInCategories.ALL.find { it.id == targetCatId }?.name
                        ?: targetCatId

                    // Update ONLY category metadata, strictly protecting all financial fields and USER_ASSIGNED status
                    val updated = expenseDao.updateCategoryMetadataIfNotUserAssigned(
                        id = item.transactionId,
                        categoryId = targetCatId,
                        categoryName = catName,
                        categorySource = CategorySource.INFERRED
                    )

                    if (updated > 0) {
                        successCount++
                    } else {
                        skippedCount++
                    }
                }
            }

            return BulkReassignmentResult(
                successCount = successCount,
                skippedCount = skippedCount,
                failedCount = 0,
                isSuccess = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return BulkReassignmentResult(
                successCount = 0,
                skippedCount = 0,
                failedCount = selectedItems.size,
                isSuccess = false,
                errorMessage = e.message ?: "Database transaction failed during bulk re-categorization"
            )
        }
    }
}
