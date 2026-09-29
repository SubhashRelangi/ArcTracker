package com.example.arctracker.service

/**
 * Origin or authority of a transaction's category assignment.
 * Ensures user manual edits are NEVER silently overwritten by automated inference.
 */
object CategorySource {
    const val NONE = "NONE"
    const val INFERRED = "INFERRED"
    const val USER_ASSIGNED = "USER_ASSIGNED"
    const val SYSTEM_ASSIGNED = "SYSTEM_ASSIGNED"
}

/**
 * Outcome status of the deterministic inference process.
 */
enum class CategoryInferenceStatus {
    NO_MATCH,
    LOW_CONFIDENCE,
    SUGGESTED,
    HIGH_CONFIDENCE,
    CONFLICT,
    USER_ASSIGNED_PRESERVED
}

/**
 * Categorical confidence rating.
 */
enum class CategoryInferenceConfidence(val score: Double) {
    NONE(0.0),
    LOW(0.3),
    MEDIUM(0.6),
    HIGH(0.9)
}

/**
 * Specific nature of an evidence item backing an inference decision.
 */
enum class EvidenceType {
    EXPLICIT_TRANSACTION_TYPE,
    EXPLICIT_SEMANTIC_KEYWORD,
    EXACT_MERCHANT,
    MERCHANT_TOKEN,
    KEYWORD_CONTAINED,
    NEGATIVE_KEYWORD_EXCLUSION,
    CONFLICT_DETECTED,
    USER_OVERRIDE_PROTECTION,
    USER_RULE,
    MERCHANT_ALIAS,
    BUILT_IN_RULE
}

/**
 * An individual piece of explainable evidence.
 */
data class CategoryInferenceEvidence(
    val type: EvidenceType,
    val description: String,
    val ruleId: String? = null,
    val scoreContribution: Double = 0.0
)

/**
 * Input parameters passed into [CategoryInferenceEngine].
 */
data class CategoryInferenceInput(
    val merchant: String? = null,
    val counterparty: String? = null,
    val rawText: String? = null,
    val transactionType: String? = null, // "Debit", "Credit"
    val amount: Double? = null,
    val existingCategoryId: String? = null,
    val existingCategorySource: String? = null,
    val availableCategoryIds: Set<String>? = null, // Valid active category IDs from DB
    val userRules: List<com.example.arctracker.data.UserCategoryRule>? = null,
    val merchantAliases: List<com.example.arctracker.data.MerchantAlias>? = null
)

/**
 * Comprehensive, explainable output from [CategoryInferenceEngine].
 */
data class CategoryInferenceResult(
    val status: CategoryInferenceStatus,
    val suggestedCategoryId: String?,
    val suggestedCategoryName: String?,
    val confidence: CategoryInferenceConfidence,
    val confidenceScore: Double,
    val evidence: List<CategoryInferenceEvidence> = emptyList(),
    val matchedRules: List<String> = emptyList(),
    val reason: String,
    val resolvedMerchant: String? = null,
    val matchedUserRuleId: String? = null,
    val aliasEvidence: String? = null
) {
    val isAutoAssignable: Boolean
        get() = status == CategoryInferenceStatus.HIGH_CONFIDENCE && !suggestedCategoryId.isNullOrBlank()

    companion object {
        fun noMatch(reason: String = "No matching rules or evidence found"): CategoryInferenceResult =
            CategoryInferenceResult(
                status = CategoryInferenceStatus.NO_MATCH,
                suggestedCategoryId = null,
                suggestedCategoryName = null,
                confidence = CategoryInferenceConfidence.NONE,
                confidenceScore = 0.0,
                reason = reason
            )

        fun conflict(
            evidence: List<CategoryInferenceEvidence>,
            matchedRules: List<String>,
            competingCategoryIds: List<String>
        ): CategoryInferenceResult =
            CategoryInferenceResult(
                status = CategoryInferenceStatus.CONFLICT,
                suggestedCategoryId = null,
                suggestedCategoryName = null,
                confidence = CategoryInferenceConfidence.LOW,
                confidenceScore = 0.3,
                evidence = evidence,
                matchedRules = matchedRules,
                reason = "Ambiguous or conflicting category evidence: ${competingCategoryIds.joinToString(", ")}"
            )

        fun userOverridePreserved(existingCategoryId: String?, existingTag: String?): CategoryInferenceResult =
            CategoryInferenceResult(
                status = CategoryInferenceStatus.USER_ASSIGNED_PRESERVED,
                suggestedCategoryId = existingCategoryId,
                suggestedCategoryName = existingTag,
                confidence = CategoryInferenceConfidence.HIGH,
                confidenceScore = 1.0,
                evidence = listOf(
                    CategoryInferenceEvidence(
                        type = EvidenceType.USER_OVERRIDE_PROTECTION,
                        description = "User manually assigned category $existingTag ($existingCategoryId); override protected",
                        scoreContribution = 1.0
                    )
                ),
                reason = "Explicit user category assignment preserved"
            )
    }
}
