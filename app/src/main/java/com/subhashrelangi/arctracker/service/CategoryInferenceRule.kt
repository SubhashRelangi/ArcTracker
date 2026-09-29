package com.subhashrelangi.arctracker.service

/**
 * Category matching strategies for deterministic evaluation.
 */
enum class CategoryMatchingType {
    EXPLICIT_TRANSACTION_TYPE,
    EXACT_MERCHANT,
    MERCHANT_TOKEN,
    KEYWORD,
    TRANSACTION_TYPE_AND_KEYWORD
}

object RulePriority {
    const val USER_EXACT = 1200
    const val USER_TOKEN = 1150
    const val USER_CONTAINS = 1100
    const val USER_TEXT_CONTAINS = 1050
    const val EXPLICIT_TRANSACTION_TYPE = 1000
    const val EXPLICIT_SEMANTIC_KEYWORD = 900
    const val EXACT_MERCHANT = 800
    const val MERCHANT_TOKEN = 600
    const val KEYWORD = 400
    const val GENERIC = 100
}

/**
 * Deterministic inference rule definition.
 */
data class CategoryInferenceRule(
    val id: String,
    val categoryId: String,
    val matchingType: CategoryMatchingType,
    val pattern: String,
    val priority: Int = RulePriority.MERCHANT_TOKEN,
    val requiredTransactionType: String? = null, // "Debit", "Credit", or null
    val negativeKeywords: List<String> = emptyList(),
    val enabled: Boolean = true
) {
    /**
     * Checks if this rule matches the given normalized merchant, counterparty, and raw text.
     */
    fun matches(
        merchantNorm: String,
        counterpartyNorm: String,
        rawTextNorm: String,
        transactionType: String?
    ): Boolean {
        if (!enabled) return false

        // Check required transaction type constraint
        if (requiredTransactionType != null && transactionType != null) {
            if (!requiredTransactionType.equals(transactionType, ignoreCase = true)) {
                return false
            }
        }

        // Check negative keywords across all texts
        val allText = "$merchantNorm $counterpartyNorm $rawTextNorm".trim()
        for (neg in negativeKeywords) {
            if (MerchantNormalizer.containsToken(allText, neg)) {
                return false
            }
        }

        val patternNorm = MerchantNormalizer.normalize(pattern, stripPrefixes = false)

        return when (matchingType) {
            CategoryMatchingType.EXACT_MERCHANT -> {
                MerchantNormalizer.matchesExact(merchantNorm, patternNorm) ||
                    MerchantNormalizer.matchesExact(counterpartyNorm, patternNorm)
            }
            CategoryMatchingType.MERCHANT_TOKEN -> {
                MerchantNormalizer.containsToken(merchantNorm, patternNorm) ||
                    MerchantNormalizer.containsToken(counterpartyNorm, patternNorm)
            }
            CategoryMatchingType.KEYWORD -> {
                MerchantNormalizer.containsToken(allText, patternNorm)
            }
            CategoryMatchingType.EXPLICIT_TRANSACTION_TYPE,
            CategoryMatchingType.TRANSACTION_TYPE_AND_KEYWORD -> {
                val matchesType = requiredTransactionType == null ||
                    (transactionType != null && requiredTransactionType.equals(transactionType, ignoreCase = true))
                val matchesKeyword = MerchantNormalizer.containsToken(allText, patternNorm)
                matchesType && matchesKeyword
            }
        }
    }
}
