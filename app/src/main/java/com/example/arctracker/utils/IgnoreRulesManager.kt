package com.example.arctracker.utils

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
    private const val RULES_INITIALIZED_KEY = "ignore_rules_initialized"

    private val predefinedNoiseKeywords = listOf(
        "otp", "one time password", "code is", "available balance",
        "balance is", "bal is", "login", "signin", "sign in",
        "welcome", "verify", "blocked", "unblocked", "kyc",
        "limit changed", "statement", "mini statement", "loan offer"
    )

    private val predefinedPromoKeywords = listOf(
        "offer", "% off", "off on", "discount", "win ", "winner", "lucky draw",
        "click here", "apply now", "register now", "redeem", "limited period",
        "subscribe", "guaranteed", "shop now", "buy now", "shop & win",
        "cashback offer", "deal of", "use code", "coupon",
        "personal loan", "pre-approved", "preapproved", "investment plan",
        "fixed deposit rates", "fd rates", "credit card offer"
    )

    fun getRules(context: Context): List<IgnoreRule> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isInitialized = prefs.getBoolean(RULES_INITIALIZED_KEY, false)

        if (!isInitialized) {
            val defaultRules = (predefinedNoiseKeywords + predefinedPromoKeywords)
                .distinct()
                .mapIndexed { index, keyword ->
                    IgnoreRule(
                        id = "sys_$index",
                        type = "Keyword",
                        matchType = "Contains",
                        value = keyword,
                        isSystem = true
                    )
                }
            saveRules(context, defaultRules)
            prefs.edit().putBoolean(RULES_INITIALIZED_KEY, true).apply()
            return defaultRules
        }

        val rulesSet = prefs.getStringSet(RULES_KEY, emptySet()) ?: emptySet()
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
