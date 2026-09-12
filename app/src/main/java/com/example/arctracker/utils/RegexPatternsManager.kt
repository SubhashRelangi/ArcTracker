package com.example.arctracker.utils

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
    val matches: Int = 0
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
                matches = obj.optInt("matches", 0)
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
            id = "sys_amount_1",
            name = "UPI Amount Pattern",
            description = "Matches amounts from UPI apps (e.g. ₹500, Rs. 1,200)",
            category = "Amount",
            pattern = "(?i)(?:rs\\.?|inr|₹|rupees)\\s*([0-9,]+\\.?[0-9]*)",
            isSystem = true,
            iconType = "upi",
            priority = 1
        ),
        RegexRule(
            id = "sys_amount_2",
            name = "Bank SMS Amount Pattern",
            description = "Matches amounts from bank transaction SMS",
            category = "Amount",
            pattern = "(?i)(?:debited|credited|amount:?)\\s*(?:rs\\.?|inr|₹)?\\s*([0-9,]+\\.?[0-9]*)",
            isSystem = true,
            iconType = "bank",
            priority = 2
        ),
        RegexRule(
            id = "sys_merchant_1",
            name = "UPI Merchant Pattern",
            description = "Extracts merchant name from UPI notifications",
            category = "Name / Merchant",
            pattern = "(?i)(?:to|at|@)\\s+([A-Za-z0-9 &._-]{2,50})",
            isSystem = true,
            iconType = "storefront",
            priority = 1
        ),
        RegexRule(
            id = "sys_merchant_2",
            name = "Bank Merchant Pattern",
            description = "Extracts merchant name from bank SMS",
            category = "Name / Merchant",
            pattern = "(?i)(?:at|in|on)\\s+([A-Za-z0-9 &._-]{2,50})",
            isSystem = true,
            iconType = "bank",
            priority = 2
        ),
        RegexRule(
            id = "sys_merchant_3",
            name = "Sender as Merchant (Fallback)",
            description = "Uses sender name when merchant is not found",
            category = "Name / Merchant",
            pattern = "(?i)^([A-Za-z0-9 &._-]{2,50})",
            isSystem = true,
            iconType = "person",
            priority = 3
        ),
        RegexRule(
            id = "custom_merchant_1",
            name = "Custom Merchant Pattern",
            description = "Add pattern for a specific app or message format",
            category = "Name / Merchant",
            pattern = "(?i)merchant[:\\s]+([A-Za-z0-9 &._-]{2,50})",
            isActive = false,
            isSystem = false,
            iconType = "custom",
            priority = 4
        ),
        RegexRule(
            id = "sys_type_debit",
            name = "Debit Keywords Pattern",
            description = "Detects debit transactions",
            category = "Type (Debit/Credit)",
            pattern = "(?i)(debited|debit|paid|spent|withdrawn|purchase|sent)",
            isSystem = true,
            iconType = "arrow_downward",
            priority = 1
        ),
        RegexRule(
            id = "sys_type_credit",
            name = "Credit Keywords Pattern",
            description = "Detects credit transactions",
            category = "Type (Debit/Credit)",
            pattern = "(?i)(credited|credit|received|deposited|refunded|salary|cashback)",
            isSystem = true,
            iconType = "arrow_upward",
            priority = 2
        ),
        RegexRule(
            id = "custom_type_1",
            name = "Custom Type Pattern",
            description = "Add custom pattern for transaction type",
            category = "Type (Debit/Credit)",
            pattern = "(?i)(refund|reversal)",
            isActive = false,
            isSystem = false,
            iconType = "custom",
            priority = 3
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
        return rulesSet.mapNotNull {
            try {
                RegexRule.fromJson(it)
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.priority }
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
