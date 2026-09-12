package com.example.arctracker.utils

import android.content.Context

object ExpenseParser {
    data class ParsedExpense(val amount: Double, val merchant: String, val type: String)

    fun parseExpenseData(context: Context, text: String, title: String): ParsedExpense? {
        val combinedText = "$title. $text"
        val lowerText = combinedText.lowercase()
        
        val rules = RegexPatternsManager.getRules(context).filter { it.isActive }
        val amountRules = rules.filter { it.category == "Amount" }.sortedBy { it.priority }
        val merchantRules = rules.filter { it.category == "Name / Merchant" }.sortedBy { it.priority }
        
        var amount: Double? = null
        for (rule in amountRules) {
            try {
                val amountRegex = Regex(rule.pattern)
                val match = amountRegex.find(combinedText)
                if (match != null && match.groups.size > 1) {
                    val amountStr = match.groupValues[1].replace(",", "")
                    amount = amountStr.toDoubleOrNull()
                    if (amount != null) break
                }
            } catch (e: Exception) {
                // Ignore invalid regex
            }
        }
        
        // Fallback amount regex if rules didn't match anything
        if (amount == null) {
            val fallbackRegex = Regex("(?i)(?:rs\\.?|inr|₹|rupees|amount:?)\\s*([0-9,]+\\.?[0-9]*)")
            val match = fallbackRegex.find(combinedText)
            if (match != null) {
                val amountStr = match.groupValues[1].replace(",", "")
                amount = amountStr.toDoubleOrNull()
            }
        }
        
        if (amount != null) {
            var merchant = "Unknown"
            
            for (rule in merchantRules) {
                try {
                    val pattern = Regex(rule.pattern)
                    val match = pattern.find(combinedText)
                    if (match != null && match.groups.size > 1) {
                        val extracted = match.groupValues[1].trim()
                        if (extracted.length > 2 && !extracted.equals("a", ignoreCase = true)) {
                            merchant = extracted
                            break
                        }
                    }
                } catch (e: Exception) {
                    // Ignore invalid regex
                }
            }
            
            if (merchant == "Unknown" && title.isNotBlank() && title.lowercase() != "messages" && !title.contains("new message")) {
                val cleanTitle = title.replace(Regex("(?i)(?:rs\\.?|inr|₹|rupees)\\s*[0-9,]+\\.?[0-9]*"), "").trim()
                merchant = if (cleanTitle.isNotBlank() && !cleanTitle.contains("paid", ignoreCase = true)) {
                    cleanTitle
                } else {
                    title
                }
            }
            
            val type = if (lowerText.contains("received") || lowerText.contains("credited")) "Credit" else "Debit"
            
            return ParsedExpense(amount, merchant, type)
        }
        return null
    }
}
