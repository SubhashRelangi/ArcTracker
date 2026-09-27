package com.example.arctracker.service

/**
 * Account types supported for user-configured bank account context (Milestone 1).
 */
enum class UserAccountType {
    SAVINGS,
    CURRENT,
    CREDIT_CARD,
    WALLET,
    SALARY,
    OTHER
}

/**
 * User-configured bank account context representation (Milestone 1).
 *
 * CRITICAL ARCHITECTURAL GUARANTEE:
 * User-configured bank and account information is used strictly as SUPPORTING EVIDENCE for:
 * - Bank identification
 * - Account identification
 * - Sender classification
 * - Transaction ownership
 * - Confidence scoring
 * - Cross-source correlation
 * - Deduplication
 *
 * It is NEVER a strict requirement or gate: unknown banks and unknown accounts MUST remain
 * fully detectable if the message itself contains strong financial evidence.
 * Full bank account numbers are never stored; only account suffixes are kept.
 */
data class UserAccountContext(
    val id: String = java.util.UUID.randomUUID().toString(),
    val bankName: String,
    val accountSuffix: String,
    val accountType: UserAccountType = UserAccountType.SAVINGS,
    val displayName: String? = null,
    val isPrimary: Boolean = false,
    val aliasKeywords: List<String> = emptyList()
) {
    init {
        require(bankName.isNotBlank()) { "Bank name cannot be blank" }
        require(accountSuffix.isNotBlank() && accountSuffix.all { it.isDigit() }) {
            "Account suffix must consist of digits only, was: '$accountSuffix'"
        }
    }

    /**
     * Checks if an extracted account or card suffix matches this user account context.
     */
    fun matchesAccountSuffix(suffix: String?): Boolean {
        if (suffix.isNullOrBlank()) return false
        val cleanSuffix = suffix.trim().trimStart('x', 'X', '*')
        return cleanSuffix.endsWith(accountSuffix) || accountSuffix.endsWith(cleanSuffix)
    }

    /**
     * Checks if a candidate bank name or sender header matches this user account's bank.
     */
    fun matchesBank(bankOrSender: String?): Boolean {
        if (bankOrSender.isNullOrBlank()) return false
        val candidate = bankOrSender.trim()
        if (candidate.contains(bankName, ignoreCase = true) || bankName.contains(candidate, ignoreCase = true)) {
            return true
        }
        return aliasKeywords.any { alias -> candidate.contains(alias, ignoreCase = true) }
    }
}
