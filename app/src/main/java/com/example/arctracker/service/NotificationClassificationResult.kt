package com.example.arctracker.service

/**
 * State indicating whether a notification is financially relevant (Step 4).
 */
enum class FinancialRelevance {
    /** Clear evidence of a completed financial transaction. */
    FINANCIAL,

    /** Clear evidence of non-transactional content, noise, OTP, promotional, or non-financial. */
    NON_FINANCIAL,

    /** Ambiguous content containing isolated financial words without sufficient transaction context. */
    UNCERTAIN
}

/**
 * Specific noise category when a notification is identified as noise/non-transactional.
 */
enum class NoiseCategory {
    NONE,
    OTP_OR_AUTHENTICATION,
    PROMOTIONAL_OR_OFFER,
    LOAN_OR_CREDIT_MARKETING,
    SECURITY_OR_LOGIN,
    BALANCE_INQUIRY,
    INFORMATIONAL_STATEMENT,
    HYPOTHETICAL_OR_OFFER_TERMS,
    IGNORE_RULE_MATCH,
    OTHER_NOISE
}

/**
 * Direction hint for financial analysis.
 * This is ONLY a hint and does not represent a definitive transaction direction.
 */
enum class DirectionHint {
    DEBIT_HINT,
    CREDIT_HINT,
    UNKNOWN
}

/**
 * Intermediate result of noise detection.
 */
data class NoiseDetectionResult(
    val isNoise: Boolean,
    val primaryCategory: NoiseCategory = NoiseCategory.NONE,
    val matchedSignals: List<String> = emptyList(),
    val reasons: List<String> = emptyList()
)

/**
 * Complete, explainable classification result for a normalized notification (Step 4).
 *
 * Answers:
 * 1. Does this notification contain evidence of financial relevance?
 * 2. Does this notification contain strong evidence of noise?
 *
 * Does NOT extract amounts, merchants, or create database records.
 */
data class NotificationClassificationResult(
    val normalized: NormalizedNotification,
    val financialRelevance: FinancialRelevance,
    val isNoise: Boolean,
    val noiseCategory: NoiseCategory = NoiseCategory.NONE,
    val directionHint: DirectionHint = DirectionHint.UNKNOWN,
    val matchedFinancialSignals: List<String> = emptyList(),
    val matchedNoiseSignals: List<String> = emptyList(),
    val classificationReasons: List<String> = emptyList(),
    val noiseReasons: List<String> = emptyList()
)
