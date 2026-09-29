package com.subhashrelangi.arctracker.service

/**
 * High-confidence curated built-in category inference rules (Milestone 10).
 *
 * Rules are evaluated deterministically in descending priority order.
 * Explicit transaction type constraints and negative exclusion keywords prevent false collisions.
 */
object BuiltInInferenceRules {

    val ALL_RULES: List<CategoryInferenceRule> = listOf(

        // ==========================================
        // 1. TRANSFER PROTECTION (Highest Precedence)
        // ==========================================
        CategoryInferenceRule(
            id = "rule_transfer_explicit_1",
            categoryId = "transfer",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "self transfer",
            priority = RulePriority.EXPLICIT_TRANSACTION_TYPE
        ),
        CategoryInferenceRule(
            id = "rule_transfer_explicit_2",
            categoryId = "transfer",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "fund transfer",
            priority = RulePriority.EXPLICIT_TRANSACTION_TYPE
        ),
        CategoryInferenceRule(
            id = "rule_transfer_explicit_3",
            categoryId = "transfer",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "upi transfer",
            priority = RulePriority.EXPLICIT_TRANSACTION_TYPE
        ),
        CategoryInferenceRule(
            id = "rule_transfer_explicit_4",
            categoryId = "transfer",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "account transfer",
            priority = RulePriority.EXPLICIT_TRANSACTION_TYPE
        ),
        CategoryInferenceRule(
            id = "rule_transfer_explicit_5",
            categoryId = "transfer",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "transfer to",
            priority = RulePriority.EXPLICIT_SEMANTIC_KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_transfer_explicit_6",
            categoryId = "transfer",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "transfer",
            priority = RulePriority.EXPLICIT_SEMANTIC_KEYWORD
        ),

        // ==========================================
        // 2. CASH WITHDRAWAL PROTECTION
        // ==========================================
        CategoryInferenceRule(
            id = "rule_cash_withdrawal_1",
            categoryId = "cash_withdrawal",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "atm cash withdrawal",
            priority = RulePriority.EXPLICIT_TRANSACTION_TYPE
        ),
        CategoryInferenceRule(
            id = "rule_cash_withdrawal_2",
            categoryId = "cash_withdrawal",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "cash withdrawal",
            priority = RulePriority.EXPLICIT_TRANSACTION_TYPE
        ),
        CategoryInferenceRule(
            id = "rule_cash_withdrawal_3",
            categoryId = "cash_withdrawal",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "cash dispensed",
            priority = RulePriority.EXPLICIT_SEMANTIC_KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_cash_withdrawal_4",
            categoryId = "cash_withdrawal",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "atm withdrawal",
            priority = RulePriority.EXPLICIT_SEMANTIC_KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_cash_withdrawal_5",
            categoryId = "cash_withdrawal",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "atm",
            priority = RulePriority.MERCHANT_TOKEN,
            negativeKeywords = listOf("fee", "charges", "annual")
        ),

        // ==========================================
        // 3. INCOME PROTECTION (Credit + Explicit Keyword)
        // ==========================================
        CategoryInferenceRule(
            id = "rule_income_salary",
            categoryId = "income",
            matchingType = CategoryMatchingType.TRANSACTION_TYPE_AND_KEYWORD,
            pattern = "salary",
            requiredTransactionType = "Credit",
            priority = RulePriority.EXPLICIT_TRANSACTION_TYPE
        ),
        CategoryInferenceRule(
            id = "rule_income_payroll",
            categoryId = "income",
            matchingType = CategoryMatchingType.TRANSACTION_TYPE_AND_KEYWORD,
            pattern = "payroll",
            requiredTransactionType = "Credit",
            priority = RulePriority.EXPLICIT_TRANSACTION_TYPE
        ),
        CategoryInferenceRule(
            id = "rule_income_stipend",
            categoryId = "income",
            matchingType = CategoryMatchingType.TRANSACTION_TYPE_AND_KEYWORD,
            pattern = "stipend",
            requiredTransactionType = "Credit",
            priority = RulePriority.EXPLICIT_TRANSACTION_TYPE
        ),
        CategoryInferenceRule(
            id = "rule_income_dividend",
            categoryId = "income",
            matchingType = CategoryMatchingType.TRANSACTION_TYPE_AND_KEYWORD,
            pattern = "dividend",
            requiredTransactionType = "Credit",
            priority = RulePriority.EXPLICIT_SEMANTIC_KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_income_interest",
            categoryId = "income",
            matchingType = CategoryMatchingType.TRANSACTION_TYPE_AND_KEYWORD,
            pattern = "interest credit",
            requiredTransactionType = "Credit",
            priority = RulePriority.EXPLICIT_SEMANTIC_KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_income_pension",
            categoryId = "income",
            matchingType = CategoryMatchingType.TRANSACTION_TYPE_AND_KEYWORD,
            pattern = "pension",
            requiredTransactionType = "Credit",
            priority = RulePriority.EXPLICIT_SEMANTIC_KEYWORD
        ),

        // ==========================================
        // 4. FOOD & DINING
        // ==========================================
        CategoryInferenceRule(
            id = "rule_food_swiggy",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "swiggy",
            priority = RulePriority.EXACT_MERCHANT,
            negativeKeywords = listOf("transfer", "salary", "refund")
        ),
        CategoryInferenceRule(
            id = "rule_food_zomato",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "zomato",
            priority = RulePriority.EXACT_MERCHANT,
            negativeKeywords = listOf("transfer", "salary", "refund")
        ),
        CategoryInferenceRule(
            id = "rule_food_uber_eats",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "uber eats",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_food_dominos",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "dominos",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_food_mcdonald",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "mcdonald",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_food_kfc",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "kfc",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_food_subway",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "subway",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_food_starbucks",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "starbucks",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_food_burger_king",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "burger king",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_food_pizza_hut",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "pizza hut",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_food_cafe",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "cafe",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_food_restaurant",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "restaurant",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_food_bakery",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "bakery",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_food_dine",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "dine",
            priority = RulePriority.KEYWORD
        ),

        // ==========================================
        // 5. SHOPPING
        // ==========================================
        CategoryInferenceRule(
            id = "rule_shop_amazon",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "amazon",
            priority = RulePriority.EXACT_MERCHANT,
            negativeKeywords = listOf("salary", "payroll", "transfer", "refund", "cashback")
        ),
        CategoryInferenceRule(
            id = "rule_shop_flipkart",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "flipkart",
            priority = RulePriority.EXACT_MERCHANT,
            negativeKeywords = listOf("salary", "payroll", "transfer", "refund", "cashback")
        ),
        CategoryInferenceRule(
            id = "rule_shop_myntra",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "myntra",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_shop_ajio",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "ajio",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_shop_meesho",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "meesho",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_shop_nykaa",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "nykaa",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_shop_decathlon",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "decathlon",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_shop_ikea",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "ikea",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_shop_dmart",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "dmart",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_shop_blinkit",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "blinkit",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_shop_instamart",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "instamart",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_shop_zepto",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "zepto",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_shop_bigbasket",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "bigbasket",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_shop_supermarket",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "supermarket",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_shop_retail",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "retail",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_shop_shopping",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "shopping",
            priority = RulePriority.KEYWORD
        ),

        // ==========================================
        // 6. TRANSPORT
        // ==========================================
        CategoryInferenceRule(
            id = "rule_trans_uber",
            categoryId = "transport",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "uber",
            priority = RulePriority.EXACT_MERCHANT,
            negativeKeywords = listOf("eats", "food")
        ),
        CategoryInferenceRule(
            id = "rule_trans_ola",
            categoryId = "transport",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "ola",
            priority = RulePriority.EXACT_MERCHANT,
            negativeKeywords = listOf("colab", "money")
        ),
        CategoryInferenceRule(
            id = "rule_trans_rapido",
            categoryId = "transport",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "rapido",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_trans_metro",
            categoryId = "transport",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "metro",
            priority = RulePriority.MERCHANT_TOKEN
        ),
        CategoryInferenceRule(
            id = "rule_trans_irctc",
            categoryId = "transport",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "irctc",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_trans_petrol",
            categoryId = "transport",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "petrol",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_trans_fuel",
            categoryId = "transport",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "fuel",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_trans_diesel",
            categoryId = "transport",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "diesel",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_trans_fastag",
            categoryId = "transport",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "fastag",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_trans_toll",
            categoryId = "transport",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "toll",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_trans_parking",
            categoryId = "transport",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "parking",
            priority = RulePriority.KEYWORD
        ),

        // ==========================================
        // 7. ENTERTAINMENT
        // ==========================================
        CategoryInferenceRule(
            id = "rule_ent_netflix",
            categoryId = "entertainment",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "netflix",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_ent_spotify",
            categoryId = "entertainment",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "spotify",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_ent_youtube",
            categoryId = "entertainment",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "youtube",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_ent_prime_video",
            categoryId = "entertainment",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "prime video",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_ent_hotstar",
            categoryId = "entertainment",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "hotstar",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_ent_bookmyshow",
            categoryId = "entertainment",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "bookmyshow",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_ent_pvr",
            categoryId = "entertainment",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "pvr",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_ent_inox",
            categoryId = "entertainment",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "inox",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_ent_cinema",
            categoryId = "entertainment",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "cinema",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_ent_movie",
            categoryId = "entertainment",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "movie",
            priority = RulePriority.KEYWORD
        ),

        // ==========================================
        // 8. HEALTHCARE
        // ==========================================
        CategoryInferenceRule(
            id = "rule_health_hospital",
            categoryId = "healthcare",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "hospital",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_health_pharmacy",
            categoryId = "healthcare",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "pharmacy",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_health_apollo",
            categoryId = "healthcare",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "apollo",
            priority = RulePriority.EXACT_MERCHANT,
            negativeKeywords = listOf("tyres", "tires")
        ),
        CategoryInferenceRule(
            id = "rule_health_medplus",
            categoryId = "healthcare",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "medplus",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_health_1mg",
            categoryId = "healthcare",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "1mg",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_health_netmeds",
            categoryId = "healthcare",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "netmeds",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_health_practo",
            categoryId = "healthcare",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "practo",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_health_clinic",
            categoryId = "healthcare",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "clinic",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_health_doctor",
            categoryId = "healthcare",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "doctor",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_health_dental",
            categoryId = "healthcare",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "dental",
            priority = RulePriority.KEYWORD
        ),

        // ==========================================
        // 9. TRAVEL
        // ==========================================
        CategoryInferenceRule(
            id = "rule_travel_makemytrip",
            categoryId = "travel",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "makemytrip",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_travel_goibibo",
            categoryId = "travel",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "goibibo",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_travel_booking",
            categoryId = "travel",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "booking.com",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_travel_airbnb",
            categoryId = "travel",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "airbnb",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_travel_cleartrip",
            categoryId = "travel",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "cleartrip",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_travel_airline",
            categoryId = "travel",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "airline",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_travel_flight",
            categoryId = "travel",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "flight",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_travel_hotel",
            categoryId = "travel",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "hotel",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_travel_resort",
            categoryId = "travel",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "resort",
            priority = RulePriority.KEYWORD
        ),

        // ==========================================
        // 10. EDUCATION
        // ==========================================
        CategoryInferenceRule(
            id = "rule_edu_udemy",
            categoryId = "education",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "udemy",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_edu_coursera",
            categoryId = "education",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "coursera",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_edu_college",
            categoryId = "education",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "college",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_edu_university",
            categoryId = "education",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "university",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_edu_school",
            categoryId = "education",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "school",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_edu_tuition",
            categoryId = "education",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "tuition",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_edu_education",
            categoryId = "education",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "education",
            priority = RulePriority.KEYWORD
        ),

        // ==========================================
        // 11. BILLS & UTILITIES
        // ==========================================
        CategoryInferenceRule(
            id = "rule_bill_electricity",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "electricity",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_bill_water",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "water bill",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_bill_gas",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "gas bill",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_bill_broadband",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "broadband",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_bill_internet",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "internet",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_bill_recharge",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "recharge",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_bill_dth",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "dth",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_bill_utility",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "utility",
            priority = RulePriority.KEYWORD
        )
    )
}
