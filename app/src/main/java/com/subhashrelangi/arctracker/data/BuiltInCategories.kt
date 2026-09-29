package com.subhashrelangi.arctracker.data

/**
 * Built-in System Categories for ArcTracker (Milestone 9).
 *
 * Preserves the 12 standard categories from Milestone 8 with stable system IDs.
 */
object BuiltInCategories {

    val FOOD_AND_DINING = TransactionCategory(
        id = "food_dining",
        name = "Food & Dining",
        iconKey = "restaurant",
        colorKey = "orange",
        isSystem = true,
        isArchived = false,
        sortOrder = 0
    )

    val SHOPPING = TransactionCategory(
        id = "shopping",
        name = "Shopping",
        iconKey = "shopping_bag",
        colorKey = "purple",
        isSystem = true,
        isArchived = false,
        sortOrder = 1
    )

    val TRANSPORT = TransactionCategory(
        id = "transport",
        name = "Transport",
        iconKey = "directions_car",
        colorKey = "blue",
        isSystem = true,
        isArchived = false,
        sortOrder = 2
    )

    val BILLS_AND_UTILITIES = TransactionCategory(
        id = "bills_utilities",
        name = "Bills & Utilities",
        iconKey = "receipt",
        colorKey = "teal",
        isSystem = true,
        isArchived = false,
        sortOrder = 3
    )

    val ENTERTAINMENT = TransactionCategory(
        id = "entertainment",
        name = "Entertainment",
        iconKey = "movie",
        colorKey = "pink",
        isSystem = true,
        isArchived = false,
        sortOrder = 4
    )

    val HEALTHCARE = TransactionCategory(
        id = "healthcare",
        name = "Healthcare",
        iconKey = "medical_services",
        colorKey = "red",
        isSystem = true,
        isArchived = false,
        sortOrder = 5
    )

    val TRAVEL = TransactionCategory(
        id = "travel",
        name = "Travel",
        iconKey = "flight",
        colorKey = "cyan",
        isSystem = true,
        isArchived = false,
        sortOrder = 6
    )

    val EDUCATION = TransactionCategory(
        id = "education",
        name = "Education",
        iconKey = "school",
        colorKey = "amber",
        isSystem = true,
        isArchived = false,
        sortOrder = 7
    )

    val TRANSFER = TransactionCategory(
        id = "transfer",
        name = "Transfer",
        iconKey = "swap_horiz",
        colorKey = "indigo",
        isSystem = true,
        isArchived = false,
        sortOrder = 8
    )

    val CASH_WITHDRAWAL = TransactionCategory(
        id = "cash_withdrawal",
        name = "Cash Withdrawal",
        iconKey = "account_balance_wallet",
        colorKey = "brown",
        isSystem = true,
        isArchived = false,
        sortOrder = 9
    )

    val INCOME = TransactionCategory(
        id = "income",
        name = "Income",
        iconKey = "payments",
        colorKey = "green",
        isSystem = true,
        isArchived = false,
        sortOrder = 10
    )

    val OTHER = TransactionCategory(
        id = "other",
        name = "Other",
        iconKey = "category",
        colorKey = "default",
        isSystem = true,
        isArchived = false,
        sortOrder = 11
    )

    val ALL: List<TransactionCategory> = listOf(
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

    fun isSystemId(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        return ALL.any { it.id == id.trim().lowercase() }
    }

    fun isSystemName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return ALL.any { it.name.equals(name.trim(), ignoreCase = true) }
    }

    /**
     * Resolves a legacy tag/name string to its stable system category.
     */
    fun findLegacyMapping(legacyTagOrName: String?): TransactionCategory? {
        if (legacyTagOrName.isNullOrBlank()) return null
        val normalized = legacyTagOrName.trim().lowercase()
        return when (normalized) {
            "food & dining", "food and dining", "food", "dining" -> FOOD_AND_DINING
            "shopping" -> SHOPPING
            "transport", "transportation" -> TRANSPORT
            "bills & utilities", "bills and utilities", "bills", "utilities" -> BILLS_AND_UTILITIES
            "entertainment" -> ENTERTAINMENT
            "healthcare", "health", "medical" -> HEALTHCARE
            "travel" -> TRAVEL
            "education" -> EDUCATION
            "transfer", "transfers", "self transfer", "self_transfer" -> TRANSFER
            "cash withdrawal", "atm", "cash" -> CASH_WITHDRAWAL
            "income", "salary", "allowance", "refund", "gift" -> INCOME
            "other", "general" -> OTHER
            else -> ALL.find { it.name.equals(normalized, ignoreCase = true) || it.id.equals(normalized, ignoreCase = true) }
        }
    }
}
