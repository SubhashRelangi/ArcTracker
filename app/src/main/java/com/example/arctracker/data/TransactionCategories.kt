package com.example.arctracker.data

/**
 * Standard categories supported for user-facing transaction categorization (Milestone 8).
 *
 * Deterministic, user-controlled categories that satisfy Part 6 requirements.
 */
object TransactionCategories {
    const val FOOD_AND_DINING = "Food & Dining"
    const val SHOPPING = "Shopping"
    const val TRANSPORT = "Transport"
    const val BILLS_AND_UTILITIES = "Bills & Utilities"
    const val ENTERTAINMENT = "Entertainment"
    const val HEALTHCARE = "Healthcare"
    const val TRAVEL = "Travel"
    const val EDUCATION = "Education"
    const val TRANSFER = "Transfer"
    const val CASH_WITHDRAWAL = "Cash Withdrawal"
    const val INCOME = "Income"
    const val OTHER = "Other"

    val ALL: List<String> = listOf(
        FOOD_AND_DINING,
        SHOPPING,
        TRANSPORT,
        BILLS_AND_UTILITIES,
        ENTERTAINMENT,
        HEALTHCARE,
        TRAVEL,
        EDUCATION,
        TRANSFER,
        CASH_WITHDRAWAL,
        INCOME,
        OTHER
    )

    fun isValid(category: String?): Boolean {
        if (category.isNullOrBlank()) return true // unassigned / optional is valid
        return ALL.any { it.equals(category.trim(), ignoreCase = true) }
    }

    /**
     * Resolves matching standard category string preserving exact canonical casing.
     */
    fun canonicalize(category: String?): String? {
        if (category.isNullOrBlank()) return null
        return ALL.find { it.equals(category.trim(), ignoreCase = true) } ?: category.trim()
    }
}
