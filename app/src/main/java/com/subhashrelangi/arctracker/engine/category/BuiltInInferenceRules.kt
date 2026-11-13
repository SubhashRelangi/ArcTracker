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
        )
    )
}
