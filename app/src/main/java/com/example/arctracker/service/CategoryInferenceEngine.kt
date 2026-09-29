package com.example.arctracker.service

import com.example.arctracker.data.BuiltInCategories
import com.example.arctracker.data.MerchantAlias
import com.example.arctracker.data.UserCategoryRule
import com.example.arctracker.data.UserRuleMatchType

/**
 * Deterministic, explainable Category Inference Engine (Milestone 10 & 11).
 *
 * Enforces strict architectural boundaries:
 * 1. Safe enrichment only: NEVER affects financial validity of a transaction.
 * 2. 100% local and deterministic: No ML, LLM, or cloud dependencies. Same input -> Same output.
 * 3. User Override Protection: Explicit user category assignments are never overwritten.
 * 4. Transfer, Income, and Cash Withdrawal protections: High-precedence constraints prevent keyword collisions.
 * 5. User Rules Authority: User-defined category rules override built-in merchant rules.
 * 6. Merchant Alias Resolution: Normalizes merchant identity before rule evaluation without directly assigning category.
 * 7. Word-boundary safety: Prevents false substring matches (e.g., "ola" in "colab").
 * 8. Active category filtering: Archived or deleted categories are never inferred or assigned.
 * 9. Explainable evidence: Preserves matched rules and reasons for every decision.
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
        val evidenceList = mutableListOf<CategoryInferenceEvidence>()

        // Step 1: User Override Protection (Critical Invariant)
        if (input.existingCategorySource == CategorySource.USER_ASSIGNED) {
            val catName = input.existingCategoryId?.let { BuiltInCategories.ALL.find { c -> c.id == it }?.name }
            return CategoryInferenceResult.userOverridePreserved(input.existingCategoryId, catName)
        }

        // Step 2: Merchant Alias Resolution (Milestone 11)
        var workingMerchant = input.merchant
        var aliasEvidenceStr: String? = null
        if (!input.merchant.isNullOrBlank() && !input.merchantAliases.isNullOrEmpty()) {
            val normAliasInput = MerchantNormalizer.normalize(input.merchant, stripPrefixes = true)
            val matchedAlias = input.merchantAliases.find { it.isEnabled && it.normalizedAlias == normAliasInput }
            if (matchedAlias != null) {
                workingMerchant = matchedAlias.canonicalMerchant
                aliasEvidenceStr = "Resolved merchant alias '${input.merchant}' -> '$workingMerchant'"
                evidenceList.add(
                    CategoryInferenceEvidence(
                        type = EvidenceType.MERCHANT_ALIAS,
                        description = aliasEvidenceStr,
                        ruleId = matchedAlias.id,
                        scoreContribution = 100.0
                    )
                )
            }
        }

        // Step 3: Normalize Inputs
        val merchantNorm = MerchantNormalizer.normalize(workingMerchant)
        val counterpartyNorm = MerchantNormalizer.normalize(input.counterparty)
        val rawTextNorm = MerchantNormalizer.normalize(input.rawText, stripPrefixes = false)
        val allText = "$merchantNorm $counterpartyNorm $rawTextNorm".trim()

        if (allText.isEmpty()) {
            return CategoryInferenceResult.noMatch("No merchant or transaction text available for category inference")
        }

        // Step 4: Check for Ambiguous Refund/Cashback/Reversal Signals
        val hasReversalSignal = AMBIGUOUS_REVERSAL_KEYWORDS.any { kw ->
            MerchantNormalizer.containsToken(allText, kw)
        }

        // Step 5: High-Precedence Semantic Safety Protections
        // High semantic authority rules (transfer, cash withdrawal, income direction constraints)
        // CANNOT be overridden by user merchant rules (e.g. "Transfer to Amazon" must be Transfer, "ATM Cash Withdrawal" must be Cash Withdrawal)
        val highPrecedenceProtectionRules = sortedRules.filter {
            it.priority >= RulePriority.EXPLICIT_SEMANTIC_KEYWORD &&
                (it.categoryId == "transfer" || it.categoryId == "cash_withdrawal" || it.categoryId == "income")
        }

        val matchedProtectionRules = mutableListOf<CategoryInferenceRule>()
        for (rule in highPrecedenceProtectionRules) {
            if (rule.matches(merchantNorm, counterpartyNorm, rawTextNorm, input.transactionType)) {
                if (input.availableCategoryIds != null && !input.availableCategoryIds.contains(rule.categoryId)) {
                    continue
                }
                if (hasReversalSignal && (rule.categoryId == "income" || rule.categoryId == "shopping") &&
                    rule.matchingType != CategoryMatchingType.EXPLICIT_TRANSACTION_TYPE
                ) {
                    continue
                }
                matchedProtectionRules.add(rule)
            }
        }

        if (matchedProtectionRules.isNotEmpty()) {
            val winningProtection = matchedProtectionRules.maxByOrNull { it.priority }!!
            evidenceList.add(
                CategoryInferenceEvidence(
                    type = when (winningProtection.matchingType) {
                        CategoryMatchingType.EXPLICIT_TRANSACTION_TYPE -> EvidenceType.EXPLICIT_TRANSACTION_TYPE
                        CategoryMatchingType.TRANSACTION_TYPE_AND_KEYWORD -> EvidenceType.EXPLICIT_SEMANTIC_KEYWORD
                        else -> EvidenceType.EXPLICIT_SEMANTIC_KEYWORD
                    },
                    description = "Semantic protection rule '${winningProtection.id}' matched (pattern: '${winningProtection.pattern}', priority: ${winningProtection.priority})",
                    ruleId = winningProtection.id,
                    scoreContribution = winningProtection.priority.toDouble()
                )
            )
            val catName = BuiltInCategories.ALL.find { it.id == winningProtection.categoryId }?.name ?: winningProtection.categoryId
            return CategoryInferenceResult(
                status = CategoryInferenceStatus.HIGH_CONFIDENCE,
                suggestedCategoryId = winningProtection.categoryId,
                suggestedCategoryName = catName,
                confidence = CategoryInferenceConfidence.HIGH,
                confidenceScore = CategoryInferenceConfidence.HIGH.score,
                evidence = evidenceList,
                matchedRules = listOf(winningProtection.id),
                reason = "Inferred '$catName' from semantic protection rule '${winningProtection.id}'",
                resolvedMerchant = workingMerchant,
                aliasEvidence = aliasEvidenceStr
            )
        }

        // Step 6: Evaluate User Category Rules (when no semantic protection rule matched)
        if (!input.userRules.isNullOrEmpty()) {
            val matchedUserRules = mutableListOf<Pair<UserCategoryRule, Int>>()

            for (userRule in input.userRules) {
                if (!userRule.isEnabled) continue

                // Check category availability
                if (input.availableCategoryIds != null && !input.availableCategoryIds.contains(userRule.categoryId)) {
                    evidenceList.add(
                        CategoryInferenceEvidence(
                            type = EvidenceType.NEGATIVE_KEYWORD_EXCLUSION,
                            description = "User rule '${userRule.pattern}' matched ${userRule.categoryId}, but category is archived or inactive",
                            ruleId = userRule.id
                        )
                    )
                    continue
                }

                if (matchesUserRule(userRule, merchantNorm, counterpartyNorm, rawTextNorm)) {
                    val basePriority = when (userRule.matchType) {
                        UserRuleMatchType.MERCHANT_EXACT -> RulePriority.USER_EXACT
                        UserRuleMatchType.MERCHANT_TOKEN -> RulePriority.USER_TOKEN
                        UserRuleMatchType.MERCHANT_CONTAINS -> RulePriority.USER_CONTAINS
                        UserRuleMatchType.TRANSACTION_TEXT_CONTAINS -> RulePriority.USER_TEXT_CONTAINS
                        else -> RulePriority.USER_CONTAINS
                    }
                    val patternBonus = userRule.normalizedPattern.length.coerceAtMost(20)
                    val effectivePriority = basePriority + userRule.priority.coerceIn(0, 49) + patternBonus
                    matchedUserRules.add(userRule to effectivePriority)

                    evidenceList.add(
                        CategoryInferenceEvidence(
                            type = EvidenceType.USER_RULE,
                            description = "Matched user rule '${userRule.name ?: userRule.pattern}' (${userRule.matchType}, priority: $effectivePriority)",
                            ruleId = userRule.id,
                            scoreContribution = effectivePriority.toDouble()
                        )
                    )
                }
            }

            if (matchedUserRules.isNotEmpty()) {
                val userRulesByCategory = matchedUserRules.groupBy { it.first.categoryId }
                val sortedUserCategories = userRulesByCategory.entries.map { (catId, rulesList) ->
                    val maxPrio = rulesList.maxOf { it.second }
                    catId to maxPrio
                }.sortedByDescending { it.second }

                if (sortedUserCategories.size > 1) {
                    val top = sortedUserCategories[0]
                    val second = sortedUserCategories[1]
                    // If equal or near-equal priority (difference < 50 points), treat as CONFLICT
                    if (top.second - second.second < 50) {
                        return CategoryInferenceResult.conflict(
                            evidence = evidenceList,
                            matchedRules = matchedUserRules.map { it.first.id },
                            competingCategoryIds = sortedUserCategories.map { it.first }
                        ).copy(
                            resolvedMerchant = workingMerchant,
                            aliasEvidence = aliasEvidenceStr
                        )
                    }
                }

                val winningCatId = sortedUserCategories.first().first
                val winningRule = userRulesByCategory[winningCatId]!!.maxByOrNull { it.second }!!.first
                val catName = BuiltInCategories.ALL.find { it.id == winningCatId }?.name ?: winningCatId

                return CategoryInferenceResult(
                    status = CategoryInferenceStatus.HIGH_CONFIDENCE,
                    suggestedCategoryId = winningCatId,
                    suggestedCategoryName = catName,
                    confidence = CategoryInferenceConfidence.HIGH,
                    confidenceScore = CategoryInferenceConfidence.HIGH.score,
                    evidence = evidenceList,
                    matchedRules = matchedUserRules.map { it.first.id },
                    reason = "Inferred '$catName' from user rule '${winningRule.name ?: winningRule.pattern}'",
                    resolvedMerchant = workingMerchant,
                    matchedUserRuleId = winningRule.id,
                    aliasEvidence = aliasEvidenceStr
                )
            }
        }

        // Step 7: Evaluate Built-In Rules (Fallback when no user rule matched)
        val matchedRules = mutableListOf<CategoryInferenceRule>()

        for (rule in sortedRules) {
            if (rule.matches(merchantNorm, counterpartyNorm, rawTextNorm, input.transactionType)) {
                // Category Availability Check (Filter Archived / Deleted categories)
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
                reason = "No active category rules matched the transaction text",
                resolvedMerchant = workingMerchant,
                aliasEvidence = aliasEvidenceStr
            )
        }

        // Step 8: Conflict Detection & Deterministic Grouping for Built-In Rules
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
                ).copy(
                    resolvedMerchant = workingMerchant,
                    aliasEvidence = aliasEvidenceStr
                )
            }
        }

        // Winning Category
        val winningCategoryId = sortedCategories.first().first
        val winningRules = matchesByCategory[winningCategoryId] ?: emptyList()
        val topPriority = winningRules.maxOf { it.priority }

        // Step 9: Confidence Rating & Policy
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
            reason = "Inferred '$categoryName' from rule '${winningRules.first().id}' (priority: $topPriority)",
            resolvedMerchant = workingMerchant,
            aliasEvidence = aliasEvidenceStr
        )
    }

    private fun matchesUserRule(
        rule: UserCategoryRule,
        merchantNorm: String,
        counterpartyNorm: String,
        rawTextNorm: String
    ): Boolean {
        val patternNorm = rule.normalizedPattern
        return when (rule.matchType) {
            UserRuleMatchType.MERCHANT_EXACT -> {
                MerchantNormalizer.matchesExact(merchantNorm, patternNorm) ||
                    MerchantNormalizer.matchesExact(counterpartyNorm, patternNorm)
            }
            UserRuleMatchType.MERCHANT_TOKEN -> {
                MerchantNormalizer.containsToken(merchantNorm, patternNorm) ||
                    MerchantNormalizer.containsToken(counterpartyNorm, patternNorm)
            }
            UserRuleMatchType.MERCHANT_CONTAINS -> {
                merchantNorm.contains(patternNorm) || counterpartyNorm.contains(patternNorm)
            }
            UserRuleMatchType.TRANSACTION_TEXT_CONTAINS -> {
                val allText = "$merchantNorm $counterpartyNorm $rawTextNorm".trim()
                allText.contains(patternNorm)
            }
            else -> false
        }
    }

    /**
     * Preview / Test helper for developers and diagnostics.
     */
    fun testInference(
        merchant: String?,
        transactionType: String? = null,
        rawText: String? = null,
        userRules: List<UserCategoryRule>? = null,
        merchantAliases: List<MerchantAlias>? = null
    ): CategoryInferenceResult {
        return inferCategory(
            CategoryInferenceInput(
                merchant = merchant,
                transactionType = transactionType,
                rawText = rawText,
                userRules = userRules,
                merchantAliases = merchantAliases
            )
        )
    }
}
