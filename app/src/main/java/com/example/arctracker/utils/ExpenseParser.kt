package com.example.arctracker.utils

object ExpenseParser {
    data class ParsedExpense(val amount: Double, val merchant: String, val type: String)

    fun parseExpenseData(text: String, title: String): ParsedExpense? {
        val combinedText = "$title. $text"
        val lowerText = combinedText.lowercase()
        val amountRegex = Regex("(?i)(?:rs\\.?|inr|₹|rupees|amount:?)\\s*([0-9,]+\\.?[0-9]*)")
        val amountMatch = amountRegex.find(combinedText)
        
        if (amountMatch != null) {
            val amountStr = amountMatch.groupValues[1].replace(",", "")
            val amount = amountStr.toDoubleOrNull()
            
            if (amount != null) {
                var merchant = "Unknown"
                
                val terminators = "(?:\\.|\\n| on | thru | by |,|;|\\s+Info|\\s+UPI|\\z)"
                val nameChars = "([a-zA-Z0-9\\s@&\\-]+?)"
                
                val merchantPatterns = listOf(
                    Regex("(?i)(?:paid to|sent to|payment to|payment of .*? to)\\s+$nameChars$terminators"),
                    Regex("(?i)(?:received from|from)\\s+(?!a/c|ac\\b|account)$nameChars$terminators"),
                    Regex("(?i)to\\s+(?!a/c|ac\\b|account)$nameChars$terminators"),
                    Regex("(?i)(?:at|spent at)\\s+$nameChars$terminators"),
                    Regex("(?i)(?:upi|inf|info)[/:]\\s*\\d*[/]*([a-zA-Z0-9\\s@&\\-]+?)[/:]")
                )
                
                for (pattern in merchantPatterns) {
                    val match = pattern.find(combinedText)
                    if (match != null) {
                        val extracted = match.groupValues[1].trim()
                        if (extracted.length > 2 && !extracted.equals("a", ignoreCase = true)) {
                            merchant = extracted
                            break
                        }
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
        }
        return null
    }
}
