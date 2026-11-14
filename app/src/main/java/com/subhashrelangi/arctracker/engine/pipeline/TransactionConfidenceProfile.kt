package com.subhashrelangi.arctracker.engine.pipeline

/**
 * Types of hard banking and financial anchors that constitute legitimate transaction DNA.
 */
enum class AnchorType {
    /** Financial instrument: Bank account suffix, Card suffix, Wallet name, or UPI VPA */
    ACCOUNT_INSTRUMENT,

    /** Audit trail: 12-digit UPI RRN, UTR number, Bank Txn ID, or Auth Code */
    AUDIT_TRAIL,

    /** Ledger balance: Available balance, remaining balance, or updated account balance */
    LEDGER_BALANCE,

    /** Directional counterparty: "to <Merchant>", "transferred to <Person>", "spent at <Store>" */
    DIRECTIONAL_PARTY,

    /** Completed action verb: "debited", "credited", "paid Rs. X", "payment successful" */
    COMPLETED_ACTION_VERB
}

/**
 * Verification verdict produced by [TransactionDnaVerifier].
 */
enum class VerificationVerdict {
    /** High confidence: 2+ anchors or 1 strong anchor with verified action */
    CONFIRMED_FINANCIAL,

    /** Sufficient confidence: Meets minimum anchor threshold to proceed to extraction/review */
    PROBABLE_FINANCIAL,

    /** Unverified: Zero or insufficient physical financial anchors; dropped as promotional/isolated text */
    UNVERIFIED_PROMOTIONAL
}

/**
 * Comprehensive financial confidence profile measuring physical banking anchors.
 */
data class TransactionConfidenceProfile(
    val score: Int,
    val verdict: VerificationVerdict,
    val matchedAnchors: Set<AnchorType> = emptySet(),
    val anchorDetails: Map<AnchorType, String> = emptyMap(),
    val diagnosticNotes: List<String> = emptyList(),
    val isAnchored: Boolean = false
) {
    /**
     * Whether this notification meets the confidence limit to proceed to the next layer in the pipeline.
     */
    val meetsExtractionThreshold: Boolean
        get() = verdict != VerificationVerdict.UNVERIFIED_PROMOTIONAL && score >= MIN_CONFIDENCE_THRESHOLD

    companion object {
        const val MIN_CONFIDENCE_THRESHOLD = 25
    }
}
