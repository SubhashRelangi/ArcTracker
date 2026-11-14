package com.subhashrelangi.arctracker.engine.category

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

        // Small Merchants, Local Eateries & Food Descriptors
        CategoryInferenceRule(
            id = "rule_food_bhavan",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "bhavan",
            priority = RulePriority.MERCHANT_TOKEN
        ),
        CategoryInferenceRule(
            id = "rule_food_sagar",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "sagar",
            priority = RulePriority.MERCHANT_TOKEN
        ),
        CategoryInferenceRule(
            id = "rule_food_dhaba",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "dhaba",
            priority = RulePriority.MERCHANT_TOKEN
        ),
        CategoryInferenceRule(
            id = "rule_food_canteen",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "canteen",
            priority = RulePriority.MERCHANT_TOKEN
        ),
        CategoryInferenceRule(
            id = "rule_food_mess",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "mess",
            priority = RulePriority.MERCHANT_TOKEN
        ),
        CategoryInferenceRule(
            id = "rule_food_tiffin",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "tiffin",
            priority = RulePriority.MERCHANT_TOKEN
        ),
        CategoryInferenceRule(
            id = "rule_food_kitchen",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "kitchen",
            priority = RulePriority.MERCHANT_TOKEN
        ),
        CategoryInferenceRule(
            id = "rule_food_bhojanalaya",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "bhojanalaya",
            priority = RulePriority.MERCHANT_TOKEN
        ),
        CategoryInferenceRule(
            id = "rule_food_biryani",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "biryani",
            priority = RulePriority.MERCHANT_TOKEN
        ),
        CategoryInferenceRule(
            id = "rule_food_shawarma",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "shawarma",
            priority = RulePriority.MERCHANT_TOKEN
        ),
        CategoryInferenceRule(
            id = "rule_food_chaat",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "chaat",
            priority = RulePriority.MERCHANT_TOKEN
        ),
        CategoryInferenceRule(
            id = "rule_food_sweets",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "sweets",
            priority = RulePriority.MERCHANT_TOKEN
        ),
        CategoryInferenceRule(
            id = "rule_food_mithai",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "mithai",
            priority = RulePriority.MERCHANT_TOKEN
        ),
        CategoryInferenceRule(
            id = "rule_food_chai_point",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "chai point",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_food_tea_stall",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "tea stall",
            priority = RulePriority.MERCHANT_TOKEN
        ),
        CategoryInferenceRule(
            id = "rule_food_chai",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "chai",
            priority = RulePriority.MERCHANT_TOKEN
        ),
        CategoryInferenceRule(
            id = "rule_food_coffee",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "coffee",
            priority = RulePriority.MERCHANT_TOKEN
        ),
        CategoryInferenceRule(
            id = "rule_food_juice",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "juice",
            priority = RulePriority.MERCHANT_TOKEN
        ),
        CategoryInferenceRule(
            id = "rule_food_bakes",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "bakes",
            priority = RulePriority.MERCHANT_TOKEN
        ),
        CategoryInferenceRule(
            id = "rule_food_cake",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "cake",
            priority = RulePriority.MERCHANT_TOKEN
        ),

        // Quick Commerce, Groceries & Daily Needs (Rolls into Food & Sustenance)
        CategoryInferenceRule(
            id = "rule_food_blinkit",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "blinkit",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_food_zepto",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "zepto",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_food_instamart",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "instamart",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_food_bigbasket",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "bigbasket",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_food_bbdaily",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "bbdaily",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_food_country_delight",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "country delight",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_food_dairy",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "dairy",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_food_milk",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "milk",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_food_amul",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "amul",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_food_mother_dairy",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "mother dairy",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_food_kirana",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "kirana",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_food_grocery",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "grocery",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_food_groceries",
            categoryId = "food_dining",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "groceries",
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
        // Tech Hardware, Gadgets & Electronics Retail
        CategoryInferenceRule(
            id = "rule_shop_croma",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "croma",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_shop_reliance_digital",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "reliance digital",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_shop_apple_store",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "apple store",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_shop_vijay_sales",
            categoryId = "shopping",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "vijay sales",
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
        // 9. TRAVEL & TRANSPORT (Harmonized Journey Domain)
        // ==========================================
        CategoryInferenceRule(
            id = "rule_travel_makemytrip",
            categoryId = "transport",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "makemytrip",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_travel_goibibo",
            categoryId = "transport",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "goibibo",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_travel_booking",
            categoryId = "transport",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "booking.com",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_travel_airbnb",
            categoryId = "transport",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "airbnb",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_travel_cleartrip",
            categoryId = "transport",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "cleartrip",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_trans_redbus",
            categoryId = "transport",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "redbus",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_trans_abhibus",
            categoryId = "transport",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "abhibus",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_trans_chalo",
            categoryId = "transport",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "chalo",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_trans_indigo",
            categoryId = "transport",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "indigo",
            priority = RulePriority.EXACT_MERCHANT,
            negativeKeywords = listOf("paints")
        ),
        CategoryInferenceRule(
            id = "rule_trans_air_india",
            categoryId = "transport",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "air india",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_trans_spicejet",
            categoryId = "transport",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "spicejet",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_trans_vistara",
            categoryId = "transport",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "vistara",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_trans_akasa",
            categoryId = "transport",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "akasa",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_travel_airline",
            categoryId = "transport",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "airline",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_travel_flight",
            categoryId = "transport",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "flight",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_travel_hotel",
            categoryId = "transport",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "hotel",
            priority = RulePriority.KEYWORD
        ),
        CategoryInferenceRule(
            id = "rule_travel_resort",
            categoryId = "transport",
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
        ),

        // Telecom, Mobile Recharges & Connectivity
        CategoryInferenceRule(
            id = "rule_bill_airtel",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "airtel",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_bill_jio",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "jio",
            priority = RulePriority.EXACT_MERCHANT,
            negativeKeywords = listOf("cinema", "mart")
        ),
        CategoryInferenceRule(
            id = "rule_bill_vi",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "vodafone idea",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_bill_bsnl",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "bsnl",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_bill_act",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "act fibernet",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_bill_wifi",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.KEYWORD,
            pattern = "wifi",
            priority = RulePriority.KEYWORD
        ),

        // Tech Subscriptions, Cloud Services & Developer Tools
        CategoryInferenceRule(
            id = "rule_bill_openai",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "openai",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_bill_chatgpt",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "chatgpt",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_bill_claude",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "claude",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_bill_google_play",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "google play",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_bill_google_one",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "google one",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_bill_apple_bill",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "apple.com/bill",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_bill_icloud",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "icloud",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_bill_microsoft",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "microsoft",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_bill_github",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "github",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_bill_aws",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "aws",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_bill_hostinger",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "hostinger",
            priority = RulePriority.EXACT_MERCHANT
        ),
        CategoryInferenceRule(
            id = "rule_bill_godaddy",
            categoryId = "bills_utilities",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "godaddy",
            priority = RulePriority.EXACT_MERCHANT
        ),

        // ==========================================
        // 9. UNIVERSAL INDIAN DOMAIN VOCABULARY (Phase 12)
        // ==========================================

        // --- Food, Eateries & Daily Groceries (food_dining) ---
        CategoryInferenceRule("rule_food_foods_stem", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "foods", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_food_stem", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "food", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_sweets_stem", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "sweets", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_mithai", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "mithai", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_namkeen", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "namkeen", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_tiffins_stem", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "tiffins", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_bakes_stem", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "bakes", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_bakers", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "bakers", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_biryani_stem", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "biryani", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_pulao", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "pulao", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_mandi", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "mandi", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_shawarma", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "shawarma", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_bhojanalaya", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "bhojanalaya", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_pizzeria", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "pizzeria", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_icecream", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "icecream", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_cream_stone", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "cream stone", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_juice_center", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "juice", RulePriority.MERCHANT_TOKEN),
        CategoryInferenceRule("rule_food_fastfood", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "fastfood", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_caterers", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "caterers", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_udupi", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "udupi", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_chai_point", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "chai point", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_tea_stall", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "tea", RulePriority.MERCHANT_TOKEN),
        CategoryInferenceRule("rule_food_coffee_day", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "coffee", RulePriority.MERCHANT_TOKEN),
        CategoryInferenceRule("rule_food_restro", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "restro", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_kirana", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "kirana", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_supermarket_stem", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "supermarket", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_hypermarket_stem", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "hypermarket", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_provisions", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "provisions", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_provision", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "provision", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_dairy", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "dairy", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_ratnadeep", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "ratnadeep", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_dmart", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "dmart", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_d_mart", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "d-mart", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_reliance_smart", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "reliance smart", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_jiomart", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "jiomart", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_vijetha", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "vijetha", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_spencers", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "spencers", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_food_heritage_fresh", "food_dining", CategoryMatchingType.MERCHANT_TOKEN, "heritage fresh", RulePriority.EXACT_MERCHANT),

        // --- State RTCs, Railways, Transit & Travel (transport) ---
        CategoryInferenceRule("rule_trans_apsrtc", "transport", CategoryMatchingType.MERCHANT_TOKEN, "apsrtc", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_tgsrtc", "transport", CategoryMatchingType.MERCHANT_TOKEN, "tgsrtc", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_tsrtc", "transport", CategoryMatchingType.MERCHANT_TOKEN, "tsrtc", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_ksrtc", "transport", CategoryMatchingType.MERCHANT_TOKEN, "ksrtc", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_msrtc", "transport", CategoryMatchingType.MERCHANT_TOKEN, "msrtc", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_upsrtc", "transport", CategoryMatchingType.MERCHANT_TOKEN, "upsrtc", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_gsrtc", "transport", CategoryMatchingType.MERCHANT_TOKEN, "gsrtc", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_rsrtc", "transport", CategoryMatchingType.MERCHANT_TOKEN, "rsrtc", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_tnstc", "transport", CategoryMatchingType.MERCHANT_TOKEN, "tnstc", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_setc", "transport", CategoryMatchingType.MERCHANT_TOKEN, "setc", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_kerala_rtc", "transport", CategoryMatchingType.MERCHANT_TOKEN, "kerala rtc", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_wbtc", "transport", CategoryMatchingType.MERCHANT_TOKEN, "wbtc", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_bmtc", "transport", CategoryMatchingType.MERCHANT_TOKEN, "bmtc", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_dtc", "transport", CategoryMatchingType.MERCHANT_TOKEN, "dtc", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_best_bus", "transport", CategoryMatchingType.MERCHANT_TOKEN, "best bus", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_mtc", "transport", CategoryMatchingType.MERCHANT_TOKEN, "mtc", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_rtc", "transport", CategoryMatchingType.MERCHANT_TOKEN, "rtc", RulePriority.MERCHANT_TOKEN),
        CategoryInferenceRule("rule_trans_travels_stem", "transport", CategoryMatchingType.MERCHANT_TOKEN, "travels", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_orange_travels", "transport", CategoryMatchingType.MERCHANT_TOKEN, "orange travels", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_vrl_travels", "transport", CategoryMatchingType.MERCHANT_TOKEN, "vrl travels", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_srs_travels", "transport", CategoryMatchingType.MERCHANT_TOKEN, "srs travels", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_kaveri_travels", "transport", CategoryMatchingType.MERCHANT_TOKEN, "kaveri travels", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_kpn_travels", "transport", CategoryMatchingType.MERCHANT_TOKEN, "kpn travels", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_morning_star", "transport", CategoryMatchingType.MERCHANT_TOKEN, "morning star", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_zingbus", "transport", CategoryMatchingType.MERCHANT_TOKEN, "zingbus", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_intracity", "transport", CategoryMatchingType.MERCHANT_TOKEN, "intracity", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_bus_pass", "transport", CategoryMatchingType.MERCHANT_TOKEN, "bus pass", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_indian_railways", "transport", CategoryMatchingType.MERCHANT_TOKEN, "indian railways", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_railways", "transport", CategoryMatchingType.MERCHANT_TOKEN, "railways", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_railway", "transport", CategoryMatchingType.MERCHANT_TOKEN, "railway", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_utsonmobile", "transport", CategoryMatchingType.MERCHANT_TOKEN, "utsonmobile", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_uts", "transport", CategoryMatchingType.MERCHANT_TOKEN, "uts", RulePriority.MERCHANT_TOKEN),
        CategoryInferenceRule("rule_trans_railyatri", "transport", CategoryMatchingType.MERCHANT_TOKEN, "railyatri", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_confirmtkt", "transport", CategoryMatchingType.MERCHANT_TOKEN, "confirmtkt", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_ixigo", "transport", CategoryMatchingType.MERCHANT_TOKEN, "ixigo", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_dmrc", "transport", CategoryMatchingType.MERCHANT_TOKEN, "dmrc", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_hmrl", "transport", CategoryMatchingType.MERCHANT_TOKEN, "hmrl", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_bmrc", "transport", CategoryMatchingType.MERCHANT_TOKEN, "bmrc", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_airways", "transport", CategoryMatchingType.MERCHANT_TOKEN, "airways", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_airlines", "transport", CategoryMatchingType.MERCHANT_TOKEN, "airlines", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_aviation", "transport", CategoryMatchingType.MERCHANT_TOKEN, "aviation", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_airport", "transport", CategoryMatchingType.MERCHANT_TOKEN, "airport", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_digiyatra", "transport", CategoryMatchingType.MERCHANT_TOKEN, "digiyatra", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_namma_yatri", "transport", CategoryMatchingType.MERCHANT_TOKEN, "namma yatri", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_mana_yatri", "transport", CategoryMatchingType.MERCHANT_TOKEN, "mana yatri", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_blusmart", "transport", CategoryMatchingType.MERCHANT_TOKEN, "blusmart", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_nhai", "transport", CategoryMatchingType.MERCHANT_TOKEN, "nhai", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_toll_plaza", "transport", CategoryMatchingType.MERCHANT_TOKEN, "toll plaza", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_hpcl", "transport", CategoryMatchingType.MERCHANT_TOKEN, "hpcl", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_bpcl", "transport", CategoryMatchingType.MERCHANT_TOKEN, "bpcl", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_iocl", "transport", CategoryMatchingType.MERCHANT_TOKEN, "iocl", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_indian_oil", "transport", CategoryMatchingType.MERCHANT_TOKEN, "indian oil", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_bharat_petroleum", "transport", CategoryMatchingType.MERCHANT_TOKEN, "bharat petroleum", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_hindustan_petroleum", "transport", CategoryMatchingType.MERCHANT_TOKEN, "hindustan petroleum", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_nayara", "transport", CategoryMatchingType.MERCHANT_TOKEN, "nayara", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_jio_bp", "transport", CategoryMatchingType.MERCHANT_TOKEN, "jio-bp", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_filling_station", "transport", CategoryMatchingType.MERCHANT_TOKEN, "filling station", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_trans_fuel_station", "transport", CategoryMatchingType.MERCHANT_TOKEN, "fuel station", RulePriority.EXACT_MERCHANT),

        // --- Healthcare & Medical (healthcare) ---
        CategoryInferenceRule("rule_health_apollo_pharmacy", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "apollo pharmacy", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_medplus", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "medplus", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_netmeds", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "netmeds", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_pharmeasy", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "pharmeasy", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_1mg", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "1mg", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_pharma_stem", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "pharma", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_pharmacy_stem", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "pharmacy", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_medicals_stem", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "medicals", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_medical_stem", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "medical", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_chemist", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "chemist", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_druggist", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "druggist", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_medicines", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "medicines", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_hospital_stem", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "hospital", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_hospitals", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "hospitals", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_clinic_stem", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "clinic", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_polyclinic", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "polyclinic", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_nursing_home", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "nursing home", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_diagnostics_stem", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "diagnostics", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_diagnostic_stem", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "diagnostic", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_pathology", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "pathology", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_pathlabs", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "pathlabs", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_dental", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "dental", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_dentist", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "dentist", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_eye_care", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "eye care", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_opticals", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "opticals", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_ayurveda", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "ayurveda", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_ayurvedic", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "ayurvedic", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_homeo", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "homeo", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_health_homeopathy", "healthcare", CategoryMatchingType.MERCHANT_TOKEN, "homeopathy", RulePriority.EXACT_MERCHANT),

        // --- Bills, Utilities & Power (bills_utilities) ---
        CategoryInferenceRule("rule_bill_apepdcl", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "apepdcl", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_apspdcl", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "apspdcl", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_apcpdcl", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "apcpdcl", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_tsspdcl", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "tsspdcl", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_tgspdcl", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "tgspdcl", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_tsnpdcl", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "tsnpdcl", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_bescom", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "bescom", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_msedcl", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "msedcl", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_tneb", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "tneb", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_tangedco", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "tangedco", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_uppcl", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "uppcl", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_electricity", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "electricity", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_discom", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "discom", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_water_board", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "water board", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_gas_agency", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "gas agency", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_indane", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "indane", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_hp_gas", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "hp gas", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_bharat_gas", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "bharat gas", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_act_fibernet", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "act fibernet", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_jiofiber", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "jiofiber", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_airtel_xstream", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "airtel xstream", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_hathway", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "hathway", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_excitel", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "excitel", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_fibernet", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "fibernet", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_cable_tv", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "cable tv", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_tataplay", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "tataplay", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_bill_bharat_billpay", "bills_utilities", CategoryMatchingType.MERCHANT_TOKEN, "bharat billpay", RulePriority.EXACT_MERCHANT),

        // --- Shopping, Apparel & Retail (shopping) ---
        CategoryInferenceRule("rule_shop_zudio", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "zudio", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_westside", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "westside", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_pantaloons", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "pantaloons", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_max_fashion", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "max fashion", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_trends", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "trends", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_reliance_digital", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "reliance digital", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_croma", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "croma", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_vijay_sales", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "vijay sales", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_bajaj_electronics", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "bajaj electronics", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_pai_international", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "pai international", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_lot_mobiles", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "lot mobiles", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_big_c", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "big c", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_sangeetha", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "sangeetha", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_poorvika", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "poorvika", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_garments_stem", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "garments", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_silks_stem", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "silks", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_sarees", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "sarees", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_textiles_stem", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "textiles", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_jewellers_stem", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "jewellers", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_jewellery", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "jewellery", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_gold", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "gold", RulePriority.MERCHANT_TOKEN),
        CategoryInferenceRule("rule_shop_footwear", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "footwear", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_electronics", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "electronics", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_decathlon", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "decathlon", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_shop_ikea", "shopping", CategoryMatchingType.MERCHANT_TOKEN, "ikea", RulePriority.EXACT_MERCHANT),

        // --- Entertainment & Cinemas (entertainment) ---
        CategoryInferenceRule("rule_ent_cinemas_stem", "entertainment", CategoryMatchingType.MERCHANT_TOKEN, "cinemas", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_ent_cinema", "entertainment", CategoryMatchingType.MERCHANT_TOKEN, "cinema", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_ent_multiplex_stem", "entertainment", CategoryMatchingType.MERCHANT_TOKEN, "multiplex", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_ent_talkies", "entertainment", CategoryMatchingType.MERCHANT_TOKEN, "talkies", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_ent_cinepolis", "entertainment", CategoryMatchingType.MERCHANT_TOKEN, "cinepolis", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_ent_asian_cinemas", "entertainment", CategoryMatchingType.MERCHANT_TOKEN, "asian cinemas", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_ent_paytm_insider", "entertainment", CategoryMatchingType.MERCHANT_TOKEN, "paytm insider", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_ent_jiocinema", "entertainment", CategoryMatchingType.MERCHANT_TOKEN, "jiocinema", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_ent_sonyliv", "entertainment", CategoryMatchingType.MERCHANT_TOKEN, "sonyliv", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_ent_zee5", "entertainment", CategoryMatchingType.MERCHANT_TOKEN, "zee5", RulePriority.EXACT_MERCHANT),
        CategoryInferenceRule("rule_ent_aha", "entertainment", CategoryMatchingType.MERCHANT_TOKEN, "aha", RulePriority.EXACT_MERCHANT)
    )
}
