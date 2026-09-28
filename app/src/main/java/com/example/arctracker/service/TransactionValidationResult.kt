package com.example.arctracker.service

/**
 * High-level validation decision state for a structured transaction candidate (Step 6).
 */
enum class ValidationState {
    /**
     * Candidate is structurally valid, supported by strong financial evidence,
     * without conflicting signals. Ready for deduplication/correlation (Step 7).
     *
     * NOTE: This does NOT mean automatic insertion into the database.
     */
    ACCEPTABLE,

    /**
     * Candidate contains some financial evidence but is partial, ambiguous,
     * has conflicting direction, failed/pending status, or moderate/weak evidence.
     * Requires user review or additional contextual resolution.
     */
    NEEDS_REVIEW,

    /**
     * Candidate is invalid, classified as noise (OTP, promo, loan, balance inquiry),
     * has non-positive/malformed amount, or contains severe contradictory evidence.
     * Must not be considered a valid financial transaction.
     */
    REJECTED
}

/**
 * Explainable qualitative level of evidence supporting the transaction candidate (Step 6).
 *
 * Distinct from numeric probabilities; represents explicit categorical evidence tiers.
 */
enum class EvidenceLevel {
    /** Multiple independent corroborating signals: financial action, valid amount, direction, and party/ref. */
    VERY_STRONG,

    /** Clear financial action, valid amount, and established direction. */
    STRONG,

    /** Partial transaction evidence (e.g. status without amount, or amount with unknown direction). */
    MODERATE,

    /** Isolated currency or weak keyword without completed action context. */
    WEAK,

    /** Non-financial, noise, or structurally invalid evidence. */
    NONE
}

/**
 * Quality rating for individual field evidence.
 */
enum class FieldEvidenceQuality {
    /** Explicitly labeled field (e.g. "UPI Ref: 12345", "A/c XX5678"). */
    EXPLICIT,

    /** Direct syntactic context (e.g. "₹500 paid", "paid to Amazon"). */
    DIRECT_CONTEXT,

    /** Surrounding financial context without direct binding. */
    INDIRECT_CONTEXT,

    /** Weak or ambiguous match. */
    WEAK,

    /** Field is absent or has no supporting evidence. */
    NONE
}

/**
 * Result of validating a [StructuredTransactionCandidate] against its evidence and Step 4 classification (Step 6).
 *
 * Explains:
 * 1. Structural validity
 * 2. Overall decision state (ACCEPTABLE, NEEDS_REVIEW, REJECTED)
 * 3. Evidence level (VERY_STRONG down to NONE)
 * 4. Human-readable reasons, warnings, and signals
 */
data class ValidatedTransactionCandidate(
    val candidate: StructuredTransactionCandidate,
    val validationState: ValidationState,
    val evidenceLevel: EvidenceLevel,
    val isStructurallyValid: Boolean,
    val validationReasons: List<String> = emptyList(),
    val rejectionReasons: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val supportingSignals: List<String> = emptyList(),
    val contradictingSignals: List<String> = emptyList(),
    val fieldQualities: Map<String, FieldEvidenceQuality> = emptyMap(),
    val heuristicScore: Int = 0,
    val eventAssessment: TransactionEventAssessment? = null
) {
    /**
     * Returns true if the candidate has passed validation as ACCEPTABLE.
     */
    val isAcceptable: Boolean
        get() = validationState == ValidationState.ACCEPTABLE

    /**
     * Returns true if the candidate requires manual review.
     */
    val needsReview: Boolean
        get() = validationState == ValidationState.NEEDS_REVIEW

    /**
     * Returns true if the candidate has been rejected.
     */
    val isRejected: Boolean
        get() = validationState == ValidationState.REJECTED
}
