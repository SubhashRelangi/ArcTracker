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
    private const val RULES_INITIALIZED_KEY = "regex_rules_initialized"

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
            name = "Paid To Pattern",
            description = "Matches merchants in outgoing transfers",
            category = "Name / Merchant",
            pattern = "(?i)(?:paid to|sent to|payment to|payment of .*? to)\\s+([a-zA-Z0-9\\s@&\\-]+?)(?:\\.|\\n| on | thru | by |,|;|\\s+Info|\\s+UPI|\\z)",
            isSystem = true,
            iconType = "upi",
            priority = 1
        ),
        RegexRule(
            id = "sys_merchant_2",
            name = "Received From Pattern",
            description = "Matches senders in incoming transfers",
            category = "Name / Merchant",
            pattern = "(?i)(?:received from|from)\\s+(?!a/c|ac\\b|account)([a-zA-Z0-9\\s@&\\-]+?)(?:\\.|\\n| on | thru | by |,|;|\\s+Info|\\s+UPI|\\z)",
            isSystem = true,
            iconType = "bank",
            priority = 2
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
}
