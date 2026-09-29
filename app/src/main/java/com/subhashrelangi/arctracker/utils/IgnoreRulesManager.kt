package com.subhashrelangi.arctracker.utils

import android.content.Context
import org.json.JSONObject

data class IgnoreRule(
    val id: String,
    val type: String, // "Keyword", "Sender", "Pattern"
    val matchType: String, // "Contains", "Exact match", "Starts with"
    val value: String,
    val isSystem: Boolean = false
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("id", id)
            put("type", type)
            put("matchType", matchType)
            put("value", value)
            put("isSystem", isSystem)
        }.toString()
    }

    companion object {
        fun fromJson(json: String): IgnoreRule {
            val obj = JSONObject(json)
            return IgnoreRule(
                id = obj.getString("id"),
                type = obj.getString("type"),
                matchType = obj.getString("matchType"),
                value = obj.getString("value"),
                isSystem = obj.optBoolean("isSystem", false)
            )
        }
    }
}

object IgnoreRulesManager {
    private const val PREFS_NAME = "ArcTrackerPrefs"
    private const val RULES_KEY = "ignore_rules"

    private val defaultRules = listOf(
        IgnoreRule("sys_1", "Keyword", "Contains", "otp", true),
        IgnoreRule("sys_2", "Keyword", "Contains", "one time password", true),
        IgnoreRule("sys_3", "Keyword", "Contains", "available balance", true),
        IgnoreRule("sys_4", "Keyword", "Contains", "cashback offer", true),
        IgnoreRule("sys_5", "Keyword", "Contains", "pre-approved", true),
        IgnoreRule("sys_6", "Sender", "Contains", "JD-HDFCBK", false),
        IgnoreRule("sys_7", "Keyword", "Contains", "discount", false)
    )

    fun getRules(context: Context): List<IgnoreRule> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rulesSet = prefs.getStringSet(RULES_KEY, null)
        if (rulesSet == null) {
            saveRules(context, defaultRules)
            return defaultRules
        }
        return rulesSet.mapNotNull {
            try {
                IgnoreRule.fromJson(it)
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.value }
    }

    fun saveRules(context: Context, rules: List<IgnoreRule>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(RULES_KEY, rules.map { it.toJson() }.toSet()).apply()
    }
}
