package com.subhashrelangi.arctracker.utils

import android.content.Context
import org.json.JSONObject
import java.util.UUID

data class RegexRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val category: String, // "Amount", "Name / Merchant", "Type", "Others"
    val pattern: String,
    val isActive: Boolean = true,
    val isSystem: Boolean = false,
    val iconType: String = "custom", // "upi", "bank", "wallet", "custom"
    val priority: Int = 0,
    val matches: Int = 0,
    val testMessage: String? = null
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("description", description)
            put("category", category)
            put("pattern", pattern)
            put("isActive", isActive)
            put("isSystem", isSystem)
            put("iconType", iconType)
            put("priority", priority)
            put("matches", matches)
            put("testMessage", testMessage)
        }.toString()
    }

    companion object {
        fun fromJson(json: String): RegexRule {
            val obj = JSONObject(json)
            return RegexRule(
                id = obj.getString("id"),
                name = obj.getString("name"),
                description = obj.getString("description"),
                category = obj.getString("category"),
                pattern = obj.getString("pattern"),
                isActive = obj.optBoolean("isActive", true),
                isSystem = obj.optBoolean("isSystem", false),
                iconType = obj.optString("iconType", "custom"),
                priority = obj.optInt("priority", 0),
                matches = obj.optInt("matches", 0),
                testMessage = if (obj.has("testMessage") && !obj.isNull("testMessage")) obj.getString("testMessage") else null
            )
        }
    }
}

object RegexPatternsManager {
    private const val PREFS_NAME = "ArcTrackerRegexPrefs"
    private const val RULES_KEY = "regex_rules"
    private const val RULES_INITIALIZED_KEY = "regex_rules_initialized_v3"

    private val defaultRules = listOf(
        RegexRule(
            id = "sys_merchant_upi",
            name = "Standard UPI Merchant Extract",
            description = "Payee String (Normalized)",
            category = "Merchant / Payee",
            pattern = "(?i)(?:to|vpa|paid to)\\s+([a-zA-Z0-9._\\s]+?)(?:\\s+on|\\s+ref|\\.|${'$'})",
            isSystem = true,
            iconType = "upi",
            priority = 1,
            matches = 1249,
            testMessage = "Rs 1,450.00 debited from a/c **4821 on 24-Oct-26 via UPI to ZOMATO. Ref 429104."
        ),
        RegexRule(
            id = "sys_merchant_pos",
            name = "Card Swipe PosTerminal",
            description = "EDC Terminal / Store Code",
            category = "Merchant / Payee",
            pattern = "(?i)(?:at|spent at)\\s+([a-zA-Z0-9._\\s]+?)(?:\\s+using|\\s+card)",
            isSystem = true,
            iconType = "bank",
            priority = 2,
            matches = 482,
            testMessage = "Rs. 382.50 spent at UBER INDIA using HDFC Bank card 4821"
        ),
        RegexRule(
            id = "custom_merchant_quickcommerce",
            name = "Blinkit / QuickCommerce Custom",
            description = "Rapid Grocery Merchant",
            category = "Merchant / Payee",
            pattern = "(?i)(?:blinkit|grofers|zepto)\\s*([a-zA-Z0-9]*)",
            isSystem = false,
            isActive = true,
            iconType = "custom",
            priority = 3,
            matches = 112,
            testMessage = "Paid Rs 340 to Blinkit Grocery Order #9910"
        ),
        RegexRule(
            id = "sys_merchant_store",
            name = "Store & Retail Payee Pattern",
            description = "Direct Storefront Payment",
            category = "Merchant / Payee",
            pattern = "(?i)(?:to|towards)\\s+([A-Za-z0-9 &._-]{2,40})",
            isSystem = true,
            iconType = "storefront",
            priority = 4,
            matches = 654
        ),
        RegexRule(
            id = "sys_amount_1",
            name = "Standard UPI Amount",
            description = "Numeric Currency Anchor",
            category = "Amount",
            pattern = "(?i)(?:rs\\.?|inr|₹|rupees)\\s*([0-9,]+\\.?[0-9]*)",
            isSystem = true,
            iconType = "upi",
            priority = 1,
            matches = 1432,
            testMessage = "Rs 1,450.00 debited from a/c **4821"
        ),
        RegexRule(
            id = "sys_amount_2",
            name = "Bank SMS Amount Pattern",
            description = "Ledger Debit / Credit Value",
            category = "Amount",
            pattern = "(?i)(?:debited|credited|amount:?)\\s*(?:rs\\.?|inr|₹)?\\s*([0-9,]+\\.?[0-9]*)",
            isSystem = true,
            iconType = "bank",
            priority = 2,
            matches = 980
        ),
        RegexRule(
            id = "sys_type_debit",
            name = "Debit Keywords Pattern",
            description = "Outward Cashflow Signal",
            category = "Type Debit/Credit",
            pattern = "(?i)(debited|debit|paid|spent|withdrawn|purchase|sent)",
            isSystem = true,
            iconType = "arrow_downward",
            priority = 1,
            matches = 1120
        ),
        RegexRule(
            id = "sys_type_credit",
            name = "Credit Keywords Pattern",
            description = "Inward Cashflow Signal",
            category = "Type Debit/Credit",
            pattern = "(?i)(credited|credit|received|deposited|refunded|salary|cashback)",
            isSystem = true,
            iconType = "arrow_upward",
            priority = 2,
            matches = 312
        ),
        RegexRule(
            id = "sys_account_suffix",
            name = "Bank Account Masked Suffix",
            description = "Account Suffix Identifier",
            category = "Account / Card",
            pattern = "(?i)(?:a/c|account|acct)\\s*(?:no\\.?)?\\s*[\\*xX]*([0-9]{4})",
            isSystem = true,
            iconType = "bank",
            priority = 1,
            matches = 840
        ),
        RegexRule(
            id = "sys_card_suffix",
            name = "Card Suffix (Last 4 Digits)",
            description = "Card Identifier",
            category = "Account / Card",
            pattern = "(?i)(?:card|ending with|ending in)\\s*[\\*xX]*([0-9]{4})",
            isSystem = true,
            iconType = "bank",
            priority = 2,
            matches = 530
        ),
        RegexRule(
            id = "sys_ref_utr",
            name = "UPI Reference / UTR Number",
            description = "Clearing Reference",
            category = "Others",
            pattern = "(?i)(?:ref|rrn|utr|txn id)\\s*[:\\s]*([0-9a-zA-Z]{6,16})",
            isSystem = true,
            iconType = "custom",
            priority = 1,
            matches = 670
        )
    )

    fun getRules(context: Context): List<RegexRule> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isInitialized = prefs.getBoolean(RULES_INITIALIZED_KEY, false)

        if (!isInitialized) {
            saveRules(context, defaultRules)
            prefs.edit().putBoolean(RULES_INITIALIZED_KEY, true).apply()
            return defaultRules
        }

        val rulesSet = prefs.getStringSet(RULES_KEY, emptySet()) ?: emptySet()
        val loaded = rulesSet.mapNotNull {
            try {
                RegexRule.fromJson(it)
            } catch (e: Exception) {
                null
            }
        }.toMutableList()

        // Ensure newly defined system rules are present
        val loadedIds = loaded.map { it.id }.toSet()
        var updated = false
        for (defaultRule in defaultRules) {
            if (defaultRule.id !in loadedIds) {
                loaded.add(defaultRule)
                updated = true
            }
        }
        if (updated) {
            saveRules(context, loaded)
        }

        return loaded.sortedBy { it.priority }
    }

    fun saveRules(context: Context, rules: List<RegexRule>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(RULES_KEY, rules.map { it.toJson() }.toSet()).apply()
    }
    
    fun getRuleById(context: Context, id: String): RegexRule? {
        return getRules(context).find { it.id == id }
    }
    
    fun addRule(context: Context, rule: RegexRule) {
        val rules = getRules(context).toMutableList()
        rules.add(rule)
        saveRules(context, rules)
    }
    
    fun updateRule(context: Context, rule: RegexRule) {
        val rules = getRules(context).toMutableList()
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index != -1) {
            rules[index] = rule
            saveRules(context, rules)
        }
    }
    
    fun deleteRule(context: Context, id: String) {
        val rules = getRules(context).toMutableList()
        rules.removeAll { it.id == id }
        saveRules(context, rules)
    }
    
    fun resetSystemRule(context: Context, id: String) {
        val defaultRule = defaultRules.find { it.id == id } ?: return
        updateRule(context, defaultRule)
    }
}
