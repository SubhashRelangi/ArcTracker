package com.example.arctracker.service

/**
 * Types of financial instruments identified from SMS messages (Step 3).
 */
enum class InstrumentType {
    BANK_ACCOUNT,
    CARD,
    UNKNOWN
}

/**
 * Qualitative confidence level for account/institution identification (Step 3).
 *
 * Explicitly separate from transaction confidence.
 */
enum class IdentityConfidence {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN
}

/**
 * Represents the extracted bank/institution and account/instrument identity (Step 3).
 *
 * This is non-destructive metadata attached to a validated transaction candidate.
 * It is NEVER a strict requirement for transaction validity: unknown bank and/or unknown
 * account transactions remain fully valid. Full account numbers are never retained.
 */
data class FinancialAccountIdentity(
    val institutionId: String? = null,
    val institutionName: String? = null,
    val accountSuffix: String? = null,
    val cardSuffix: String? = null,
    val instrumentType: InstrumentType = InstrumentType.UNKNOWN,
    val confidence: IdentityConfidence = IdentityConfidence.UNKNOWN,
    val evidenceSource: String? = null
) {
    val isComplete: Boolean
        get() = !institutionName.isNullOrBlank() && (!accountSuffix.isNullOrBlank() || !cardSuffix.isNullOrBlank())

    val isPartiallyIdentified: Boolean
        get() = !institutionName.isNullOrBlank() || !accountSuffix.isNullOrBlank() || !cardSuffix.isNullOrBlank()

    /**
     * User-facing display label.
     *
     * Examples:
     * - "HDFC Bank ••••4381"
     * - "HDFC Bank Card ••••1234"
     * - "HDFC Bank (Account: Unknown)"
     * - "Unknown Bank ••••4381"
     * - "Unidentified Account"
     */
    fun getDisplayName(): String {
        val inst = institutionName
        val suffix = when {
            !accountSuffix.isNullOrBlank() -> "••••$accountSuffix"
            !cardSuffix.isNullOrBlank() -> "Card ••••$cardSuffix"
            else -> null
        }

        return when {
            inst != null && suffix != null -> "$inst $suffix"
            inst != null -> "$inst (Account: Unknown)"
            suffix != null -> "Unknown Bank $suffix"
            else -> "Unidentified Account"
        }
    }
}

/**
 * In-memory representation of a stable group of transactions belonging to a single
 * financial account or instrument (Step 4).
 *
 * Created only during the scan phase; strictly in-memory with ZERO Room database persistence.
 */
data class FinancialAccountGroup(
    val groupId: String,
    val identity: FinancialAccountIdentity,
    val transactions: List<ScannedTransactionItem>,
    val transactionCount: Int,
    val totalDebit: Double,
    val totalCredit: Double
)
