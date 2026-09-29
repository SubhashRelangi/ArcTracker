package com.example.arctracker.service

import com.example.arctracker.data.BuiltInCategories

/**
 * Deterministic, explainable Category Inference Engine (Milestone 10).
 *
 * Enforces strict architectural boundaries:
 * 1. Safe enrichment only: NEVER affects financial validity of a transaction.
 * 2. 100% local and deterministic: No ML, LLM, or cloud dependencies. Same input -> Same output.
 * 3. User Override Protection: Explicit user category assignments are never overwritten.
 * 4. Transfer, Income, and Cash Withdrawal protections: High-precedence constraints prevent keyword collisions.
 * 5. Word-boundary safety: Prevents false substring matches (e.g., "ola" in "colab").
 * 6. Active category filtering: Archived or deleted categories are never inferred or assigned.
 * 7. Explainable evidence: Preserves matched rules and reasons for every decision.
 */
class CategoryInferenceEngine(
    private val rules: List<CategoryInferenceRule> = BuiltInInferenceRules.ALL_RULES
) {

    private val sortedRules = rules.sortedByDescending { it.priority }

    // Ambiguous refund / reversal / cashback keywords that must not blindly become Income or Shopping
    private val AMBIGUOUS_REVERSAL_KEYWORDS = listOf(
        "refund", "refunded", "reversal", "reversed", "cashback", "cash back", "reward", "rewards"
    )

    /**
     * Infers category for a transaction candidate.
     */
    fun inferCategory(input: CategoryInferenceInput): CategoryInferenceResult {
        // Step 1: User Override Protection (Critical Invariant)
        if (input.existingCategorySource == CategorySource.USER_ASSIGNED) {
            val catName = input.existingCategoryId?.let { BuiltInCategories.ALL.find { c -> c.id == it }?.name }
            return CategoryInferenceResult.userOverridePreserved(input.existingCategoryId, catName)
        }

        // Step 2: Normalize Inputs
        val merchantNorm = MerchantNormalizer.normalize(input.merchant)
        val counterpartyNorm = MerchantNormalizer.normalize(input.counterparty)
        val rawTextNorm = MerchantNormalizer.normalize(input.rawText)
        val allText = "$merchantNorm $counterpartyNorm $rawTextNorm".trim()

        if (allText.isEmpty()) {
            return CategoryInferenceResult.noMatch("No merchant or transaction text available for category inference")
        }

        // Step 3: Check for Ambiguous Refund/Cashback/Reversal Signals
        val hasReversalSignal = AMBIGUOUS_REVERSAL_KEYWORDS.any { kw ->
            MerchantNormalizer.containsToken(allText, kw)
        }

        // Step 4: Evaluate Rules in Priority Order
        val matchedRules = mutableListOf<CategoryInferenceRule>()
        val evidenceList = mutableListOf<CategoryInferenceEvidence>()

        for (rule in sortedRules) {
            if (rule.matches(merchantNorm, counterpartyNorm, rawTextNorm, input.transactionType)) {
                // Step 5: Category Availability Check (Filter Archived / Deleted categories)
                if (input.availableCategoryIds != null && !input.availableCategoryIds.contains(rule.categoryId)) {
                    evidenceList.add(
                        CategoryInferenceEvidence(
                            type = EvidenceType.NEGATIVE_KEYWORD_EXCLUSION,
                            description = "Rule ${rule.id} matched ${rule.categoryId}, but category is archived or inactive in database",
                            ruleId = rule.id
                        )
                    )
                    continue
                }

                // If transaction contains reversal/cashback and rule is Income/Shopping without explicit context, avoid false match
                if (hasReversalSignal && (rule.categoryId == "income" || rule.categoryId == "shopping") &&
                    rule.matchingType != CategoryMatchingType.EXPLICIT_TRANSACTION_TYPE
                ) {
                    evidenceList.add(
                        CategoryInferenceEvidence(
                            type = EvidenceType.NEGATIVE_KEYWORD_EXCLUSION,
                            description = "Excluded rule ${rule.id} (${rule.categoryId}) due to ambiguous reversal/cashback context",
                            ruleId = rule.id
                        )
                    )
                    continue
                }

                matchedRules.add(rule)
                evidenceList.add(
                    CategoryInferenceEvidence(
                        type = when (rule.matchingType) {
                            CategoryMatchingType.EXPLICIT_TRANSACTION_TYPE -> EvidenceType.EXPLICIT_TRANSACTION_TYPE
                            CategoryMatchingType.TRANSACTION_TYPE_AND_KEYWORD -> EvidenceType.EXPLICIT_SEMANTIC_KEYWORD
                            CategoryMatchingType.EXACT_MERCHANT -> EvidenceType.EXACT_MERCHANT
                            CategoryMatchingType.MERCHANT_TOKEN -> EvidenceType.MERCHANT_TOKEN
                            CategoryMatchingType.KEYWORD -> EvidenceType.KEYWORD_CONTAINED
                        },
                        description = "Matched rule '${rule.id}' (pattern: '${rule.pattern}', priority: ${rule.priority})",
                        ruleId = rule.id,
                        scoreContribution = rule.priority.toDouble()
                    )
                )
            }
        }

        if (matchedRules.isEmpty()) {
            return CategoryInferenceResult(
                status = CategoryInferenceStatus.NO_MATCH,
                suggestedCategoryId = null,
                suggestedCategoryName = null,
                confidence = CategoryInferenceConfidence.NONE,
                confidenceScore = 0.0,
                evidence = evidenceList,
                reason = "No active category rules matched the transaction text"
            )
        }

        // Step 6: Conflict Detection & Deterministic Grouping
        val matchesByCategory = matchedRules.groupBy { it.categoryId }
        val sortedCategories = matchesByCategory.entries.map { (catId, rulesForCat) ->
            val maxPriority = rulesForCat.maxOf { it.priority }
            catId to maxPriority
        }.sortedByDescending { it.second }

        if (sortedCategories.size > 1) {
            val topCategory = sortedCategories[0]
            val secondCategory = sortedCategories[1]

            // If top category priority is not strictly higher by at least 100 points, treat as CONFLICT
            if (topCategory.second - secondCategory.second < 100) {
                return CategoryInferenceResult.conflict(
                    evidence = evidenceList,
                    matchedRules = matchedRules.map { it.id },
                    competingCategoryIds = sortedCategories.map { it.first }
                )
            }
        }

        // Winning Category
        val winningCategoryId = sortedCategories.first().first
        val winningRules = matchesByCategory[winningCategoryId] ?: emptyList()
        val topPriority = winningRules.maxOf { it.priority }

        // Step 7: Confidence Rating & Policy
        val confidence = when {
            topPriority >= RulePriority.EXACT_MERCHANT -> CategoryInferenceConfidence.HIGH
            topPriority >= RulePriority.MERCHANT_TOKEN -> CategoryInferenceConfidence.HIGH
            topPriority >= RulePriority.KEYWORD -> CategoryInferenceConfidence.MEDIUM
            else -> CategoryInferenceConfidence.LOW
        }

        val status = when (confidence) {
            CategoryInferenceConfidence.HIGH -> CategoryInferenceStatus.HIGH_CONFIDENCE
            CategoryInferenceConfidence.MEDIUM -> CategoryInferenceStatus.SUGGESTED
            CategoryInferenceConfidence.LOW -> CategoryInferenceStatus.LOW_CONFIDENCE
            CategoryInferenceConfidence.NONE -> CategoryInferenceStatus.NO_MATCH
        }

        val categoryName = BuiltInCategories.ALL.find { it.id == winningCategoryId }?.name ?: winningCategoryId

        return CategoryInferenceResult(
            status = status,
            suggestedCategoryId = winningCategoryId,
            suggestedCategoryName = categoryName,
            confidence = confidence,
            confidenceScore = confidence.score,
            evidence = evidenceList,
            matchedRules = matchedRules.map { it.id },
            reason = "Inferred '$categoryName' from rule '${winningRules.first().id}' (priority: $topPriority)"
        )
    }

    /**
     * Preview / Test helper for developers and diagnostics.
     */
    fun testInference(
        merchant: String?,
        transactionType: String? = null,
        rawText: String? = null
    ): CategoryInferenceResult {
        return inferCategory(
            CategoryInferenceInput(
                merchant = merchant,
                transactionType = transactionType,
                rawText = rawText
            )
        )
    }
}
