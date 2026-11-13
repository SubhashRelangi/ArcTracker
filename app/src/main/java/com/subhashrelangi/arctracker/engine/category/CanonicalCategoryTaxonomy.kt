package com.subhashrelangi.arctracker.engine.category

import com.subhashrelangi.arctracker.data.BuiltInCategories

/**
 * Intelligent Canonical Category Taxonomy for ArcTracker.
 *
 * Solves category fragmentation by harmonizing synonymous domains into single canonical categories.
 * Ensures that categories with identical financial intent (e.g., Travel + Transport, Local Eateries + Food,
 * Digital Tech Subscriptions + Utilities) always "read as one, not two".
 */
object CanonicalCategoryTaxonomy {

    const val CANONICAL_FOOD_DINING = "food_dining"
    const val CANONICAL_SHOPPING = "shopping"
    const val CANONICAL_TRANSPORT = "transport" // Canonical ID representing "Travel & Transport"
    const val CANONICAL_BILLS_UTILITIES = "bills_utilities"
    const val CANONICAL_ENTERTAINMENT = "entertainment"
    const val CANONICAL_HEALTHCARE = "healthcare"
    const val CANONICAL_EDUCATION = "education"
    const val CANONICAL_TRANSFER = "transfer"
    const val CANONICAL_CASH_WITHDRAWAL = "cash_withdrawal"
    const val CANONICAL_INCOME = "income"
    const val CANONICAL_OTHER = "other"

    /**
     * Display names for the canonical categories.
     */
    val CANONICAL_NAMES: Map<String, String> = mapOf(
        CANONICAL_FOOD_DINING to "Food & Dining",
        CANONICAL_SHOPPING to "Shopping",
        CANONICAL_TRANSPORT to "Travel & Transport",
        CANONICAL_BILLS_UTILITIES to "Bills & Utilities",
        CANONICAL_ENTERTAINMENT to "Entertainment",
        CANONICAL_HEALTHCARE to "Healthcare",
        CANONICAL_EDUCATION to "Education",
        CANONICAL_TRANSFER to "Transfer",
        CANONICAL_CASH_WITHDRAWAL to "Cash Withdrawal",
        CANONICAL_INCOME to "Income",
        CANONICAL_OTHER to "Other"
    )

    /**
     * Map of synonyms, aliases, and sub-domains to canonical category IDs.
     */
    private val SYNONYMS: Map<String, String> = mapOf(
        // ==========================================
        // 1. Travel & Transport Domain (All mobility & journeys)
        // ==========================================
        "travel" to CANONICAL_TRANSPORT,
        "transport" to CANONICAL_TRANSPORT,
        "travel_transport" to CANONICAL_TRANSPORT,
        "transportation" to CANONICAL_TRANSPORT,
        "transit" to CANONICAL_TRANSPORT,
        "commute" to CANONICAL_TRANSPORT,
        "cab" to CANONICAL_TRANSPORT,
        "cabs" to CANONICAL_TRANSPORT,
        "flight" to CANONICAL_TRANSPORT,
        "flights" to CANONICAL_TRANSPORT,
        "airline" to CANONICAL_TRANSPORT,
        "airlines" to CANONICAL_TRANSPORT,
        "hotel" to CANONICAL_TRANSPORT,
        "hotels" to CANONICAL_TRANSPORT,
        "resort" to CANONICAL_TRANSPORT,
        "fuel" to CANONICAL_TRANSPORT,
        "petrol" to CANONICAL_TRANSPORT,
        "diesel" to CANONICAL_TRANSPORT,
        "toll" to CANONICAL_TRANSPORT,
        "fastag" to CANONICAL_TRANSPORT,
        "parking" to CANONICAL_TRANSPORT,

        // ==========================================
        // 2. Food & Dining Domain (All sustenance, eateries & quick groceries)
        // ==========================================
        "food" to CANONICAL_FOOD_DINING,
        "dining" to CANONICAL_FOOD_DINING,
        "food_dining" to CANONICAL_FOOD_DINING,
        "food & dining" to CANONICAL_FOOD_DINING,
        "food and dining" to CANONICAL_FOOD_DINING,
        "restaurant" to CANONICAL_FOOD_DINING,
        "restaurants" to CANONICAL_FOOD_DINING,
        "cafe" to CANONICAL_FOOD_DINING,
        "eatery" to CANONICAL_FOOD_DINING,
        "dhaba" to CANONICAL_FOOD_DINING,
        "bakery" to CANONICAL_FOOD_DINING,
        "bakes" to CANONICAL_FOOD_DINING,
        "groceries" to CANONICAL_FOOD_DINING,
        "grocery" to CANONICAL_FOOD_DINING,
        "kirana" to CANONICAL_FOOD_DINING,
        "supermarket" to CANONICAL_FOOD_DINING,

        // ==========================================
        // 3. Shopping Domain (Physical retail, fashion & tech hardware)
        // ==========================================
        "shopping" to CANONICAL_SHOPPING,
        "retail" to CANONICAL_SHOPPING,
        "electronics" to CANONICAL_SHOPPING,
        "gadgets" to CANONICAL_SHOPPING,
        "fashion" to CANONICAL_SHOPPING,
        "apparel" to CANONICAL_SHOPPING,
        "clothing" to CANONICAL_SHOPPING,

        // ==========================================
        // 4. Bills & Utilities Domain (Recurring services, telecom & digital tech subscriptions)
        // ==========================================
        "bills" to CANONICAL_BILLS_UTILITIES,
        "utilities" to CANONICAL_BILLS_UTILITIES,
        "bills_utilities" to CANONICAL_BILLS_UTILITIES,
        "bills & utilities" to CANONICAL_BILLS_UTILITIES,
        "bills and utilities" to CANONICAL_BILLS_UTILITIES,
        "recharge" to CANONICAL_BILLS_UTILITIES,
        "mobile recharge" to CANONICAL_BILLS_UTILITIES,
        "broadband" to CANONICAL_BILLS_UTILITIES,
        "wifi" to CANONICAL_BILLS_UTILITIES,
        "electricity" to CANONICAL_BILLS_UTILITIES,
        "water" to CANONICAL_BILLS_UTILITIES,
        "gas" to CANONICAL_BILLS_UTILITIES,
        "dth" to CANONICAL_BILLS_UTILITIES,
        "tech_subscription" to CANONICAL_BILLS_UTILITIES,
        "subscription" to CANONICAL_BILLS_UTILITIES,
        "software" to CANONICAL_BILLS_UTILITIES,
        "cloud" to CANONICAL_BILLS_UTILITIES,

        // ==========================================
        // 5. Entertainment Domain
        // ==========================================
        "entertainment" to CANONICAL_ENTERTAINMENT,
        "movies" to CANONICAL_ENTERTAINMENT,
        "cinema" to CANONICAL_ENTERTAINMENT,
        "streaming" to CANONICAL_ENTERTAINMENT,
        "gaming" to CANONICAL_ENTERTAINMENT,

        // ==========================================
        // 6. Healthcare Domain
        // ==========================================
        "healthcare" to CANONICAL_HEALTHCARE,
        "health" to CANONICAL_HEALTHCARE,
        "medical" to CANONICAL_HEALTHCARE,
        "pharmacy" to CANONICAL_HEALTHCARE,
        "medicines" to CANONICAL_HEALTHCARE,
        "hospital" to CANONICAL_HEALTHCARE,
        "doctor" to CANONICAL_HEALTHCARE,

        // ==========================================
        // 7. Education Domain
        // ==========================================
        "education" to CANONICAL_EDUCATION,
        "school" to CANONICAL_EDUCATION,
        "college" to CANONICAL_EDUCATION,
        "tuition" to CANONICAL_EDUCATION,
        "course" to CANONICAL_EDUCATION,

        // ==========================================
        // 8. Financial Domains
        // ==========================================
        "transfer" to CANONICAL_TRANSFER,
        "transfers" to CANONICAL_TRANSFER,
        "self_transfer" to CANONICAL_TRANSFER,
        "cash_withdrawal" to CANONICAL_CASH_WITHDRAWAL,
        "cash" to CANONICAL_CASH_WITHDRAWAL,
        "atm" to CANONICAL_CASH_WITHDRAWAL,
        "income" to CANONICAL_INCOME,
        "salary" to CANONICAL_INCOME,
        "payroll" to CANONICAL_INCOME
    )

    /**
     * Resolves any category ID, synonym, or legacy string to its canonical category ID.
     */
    fun canonicalize(categoryIdOrSynonym: String?): String {
        if (categoryIdOrSynonym.isNullOrBlank()) return CANONICAL_OTHER
        val normalized = categoryIdOrSynonym.trim().lowercase()
        return SYNONYMS[normalized] ?: normalized
    }

    /**
     * Returns the standardized display name for a canonical category ID.
     */
    fun getCanonicalCategoryName(canonicalId: String): String {
        val canonical = canonicalize(canonicalId)
        return CANONICAL_NAMES[canonical]
            ?: BuiltInCategories.ALL.find { it.id == canonical }?.name
            ?: canonical.replace('_', ' ').split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    /**
     * Checks if two category IDs belong to the exact same canonical financial domain.
     * E.g., isSameCanonicalDomain("travel", "transport") == true
     */
    fun isSameCanonicalDomain(catA: String?, catB: String?): Boolean {
        if (catA == null || catB == null) return false
        return canonicalize(catA) == canonicalize(catB)
    }
}
