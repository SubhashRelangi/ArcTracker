package com.subhashrelangi.arctracker.utils

import org.json.JSONObject
import java.util.regex.Pattern

/**
 * On-Device Deterministic Regex Synthesizer & AI Prompt Assistant.
 *
 * Allows non-technical users to build robust, battle-tested regex rules
 * by simply highlighting key elements (Amount, Merchant/Payee, Account/Card, Type)
 * in an SMS without having to type regex manually.
 *
 * Also provides an offline ChatGPT prompt generator and JSON response parser
 * as a fallback for complex/unusual formats.
 */
object VisualRegexSynthesizer {

    data class SynthesisResult(
        val category: String,
        val pattern: String,
        val suggestedName: String,
        val description: String,
        val extractedPreview: String,
        val isVerified: Boolean
    )

    enum class TargetCategory(val displayName: String) {
        MERCHANT("Merchant / Payee"),
        AMOUNT("Amount"),
        TYPE("Type Debit/Credit"),
        ACCOUNT("Account / Card"),
        OTHERS("Others")
    }

    /**
     * Synthesizes a deterministic regex rule from user's selection within a sample message.
     *
     * @param fullMessage The entire raw SMS/notification text.
     * @param selectedText The portion selected/highlighted by the user.
     * @param explicitCategory The category chosen by user (or null to auto-detect).
     */
    fun synthesize(
        fullMessage: String,
        selectedText: String,
        explicitCategory: String? = null
    ): SynthesisResult? {
        val trimmedMsg = fullMessage.trim()
        val trimmedSel = selectedText.trim()
        if (trimmedMsg.isEmpty() || trimmedSel.isEmpty()) return null

        val startIndex = trimmedMsg.indexOf(trimmedSel, ignoreCase = true)
        if (startIndex == -1) return null

        val category = explicitCategory ?: detectCategory(trimmedSel, trimmedMsg, startIndex)

        return when (category) {
            TargetCategory.AMOUNT.displayName -> synthesizeAmount(trimmedMsg, trimmedSel, startIndex)
            TargetCategory.MERCHANT.displayName -> synthesizeMerchant(trimmedMsg, trimmedSel, startIndex)
            TargetCategory.ACCOUNT.displayName -> synthesizeAccount(trimmedMsg, trimmedSel, startIndex)
            TargetCategory.TYPE.displayName -> synthesizeType(trimmedMsg, trimmedSel)
            else -> synthesizeGeneric(trimmedMsg, trimmedSel, startIndex)
        }
    }

    private fun detectCategory(selected: String, fullMsg: String, index: Int): String {
        val cleanSel = selected.replace("₹", "").replace("Rs.", "").replace("INR", "").trim()
        // Check Amount
        if (cleanSel.matches(Regex("^[0-9,]+(\\.[0-9]{1,2})?$")) || selected.startsWith("₹") || selected.startsWith("Rs")) {
            return TargetCategory.AMOUNT.displayName
        }
        // Check Account / Card (3 to 6 digits, optionally with asterisks / X)
        if (cleanSel.matches(Regex("^[\\*xX•]*[0-9]{3,6}$"))) {
            return TargetCategory.ACCOUNT.displayName
        }
        // Check Type
        val selLower = selected.lowercase()
        if (selLower in listOf("debited", "debit", "paid", "spent", "credited", "credit", "received", "refunded", "withdrawn")) {
            return TargetCategory.TYPE.displayName
        }
        // Default to Merchant
        return TargetCategory.MERCHANT.displayName
    }

    private fun synthesizeAmount(fullMsg: String, selected: String, index: Int): SynthesisResult {
        // Strip any leading currency symbol from user selection for name
        val digitsOnly = selected.replace(Regex("[^0-9.,]"), "").trim()
        val textBefore = fullMsg.substring(0, index).trim().lowercase()

        val pattern = when {
            textBefore.contains("debited") -> "(?i)(?:debited\\s*(?:by|of)?|amount:?)\\s*(?:rs\\.?|inr|₹)?\\s*([0-9,]+\\.?[0-9]*)"
            textBefore.contains("spent") -> "(?i)(?:spent|paid)\\s*(?:rs\\.?|inr|₹)?\\s*([0-9,]+\\.?[0-9]*)"
            textBefore.contains("credited") -> "(?i)(?:credited\\s*(?:with|by)?|received)\\s*(?:rs\\.?|inr|₹)?\\s*([0-9,]+\\.?[0-9]*)"
            else -> "(?i)(?:rs\\.?|inr|₹|rupees)\\s*([0-9,]+\\.?[0-9]*)"
        }

        val testResult = testPattern(pattern, fullMsg)
        return SynthesisResult(
            category = TargetCategory.AMOUNT.displayName,
            pattern = pattern,
            suggestedName = "Amount Parser (₹$digitsOnly)",
            description = "Extracts numeric transaction amount with optional currency symbols",
            extractedPreview = testResult ?: digitsOnly,
            isVerified = testResult != null
        )
    }

    private fun synthesizeMerchant(fullMsg: String, selected: String, index: Int): SynthesisResult {
        val prefixContext = fullMsg.substring(maxOf(0, index - 25), index).trim()
        val wordsBefore = prefixContext.split("\\s+".toRegex()).takeLast(3)

        // Identify anchor keyword right before merchant
        val anchor = when {
            wordsBefore.any { it.equals("to", ignoreCase = true) } -> "to"
            wordsBefore.any { it.equals("at", ignoreCase = true) } -> "at"
            wordsBefore.any { it.equals("towards", ignoreCase = true) } -> "towards"
            wordsBefore.any { it.equals("for", ignoreCase = true) } -> "for"
            wordsBefore.any { it.equals("info", ignoreCase = true) } -> "info"
            wordsBefore.any { it.equals("vpa", ignoreCase = true) } -> "vpa"
            else -> "to"
        }

        val pattern = when (anchor) {
            "at" -> "(?i)(?:at|spent at)\\s+([A-Za-z0-9 &._-]{2,40})"
            "towards" -> "(?i)(?:towards|to)\\s+([A-Za-z0-9 &._-]{2,40})"
            "for" -> "(?i)(?:for|towards)\\s+([A-Za-z0-9 &._-]{2,40})"
            "info" -> "(?i)(?:info|ref)\\s+([A-Za-z0-9 &._-]{2,40})"
            else -> "(?i)(?:to|vpa|paid to)\\s+([A-Za-z0-9 &._-]{2,40})"
        }

        val testResult = testPattern(pattern, fullMsg)
        return SynthesisResult(
            category = TargetCategory.MERCHANT.displayName,
            pattern = pattern,
            suggestedName = "Payee Pattern ($selected)",
            description = "Extracts payee or merchant following '$anchor' keyword",
            extractedPreview = testResult ?: selected,
            isVerified = testResult != null
        )
    }

    private fun synthesizeAccount(fullMsg: String, selected: String, index: Int): SynthesisResult {
        val digitsOnly = selected.replace(Regex("[^0-9]"), "").takeLast(4)
        val prefixContext = fullMsg.substring(maxOf(0, index - 25), index).lowercase()

        val pattern = when {
            prefixContext.contains("card") -> "(?i)(?:card|ending with|ending in)\\s*[\\*xX•]*([0-9]{4})"
            else -> "(?i)(?:a/c|account|acct)\\s*(?:no\\.?)?\\s*[\\*xX•]*([0-9]{4})"
        }

        val testResult = testPattern(pattern, fullMsg)
        return SynthesisResult(
            category = TargetCategory.ACCOUNT.displayName,
            pattern = pattern,
            suggestedName = "Account Suffix Parser (**$digitsOnly)",
            description = "Extracts 4-digit bank account or card identifier",
            extractedPreview = testResult ?: digitsOnly,
            isVerified = testResult != null
        )
    }

    private fun synthesizeType(fullMsg: String, selected: String): SynthesisResult {
        val selLower = selected.lowercase()
        val isCredit = selLower in listOf("credited", "credit", "received", "deposited", "refunded", "salary")
        val pattern = if (isCredit) {
            "(?i)(credited|credit|received|deposited|refunded|salary|cashback)"
        } else {
            "(?i)(debited|debit|paid|spent|withdrawn|purchase|sent)"
        }

        val testResult = testPattern(pattern, fullMsg)
        return SynthesisResult(
            category = TargetCategory.TYPE.displayName,
            pattern = pattern,
            suggestedName = if (isCredit) "Credit Signal Pattern" else "Debit Signal Pattern",
            description = if (isCredit) "Identifies incoming deposits and refunds" else "Identifies outgoing expenses and withdrawals",
            extractedPreview = testResult ?: selected,
            isVerified = testResult != null
        )
    }

    private fun synthesizeGeneric(fullMsg: String, selected: String, index: Int): SynthesisResult {
        val escaped = Pattern.quote(selected)
        val pattern = "(?i)($escaped)"
        val testResult = testPattern(pattern, fullMsg)
        return SynthesisResult(
            category = TargetCategory.OTHERS.displayName,
            pattern = pattern,
            suggestedName = "Custom Token ($selected)",
            description = "Matches specific keyword pattern",
            extractedPreview = testResult ?: selected,
            isVerified = testResult != null
        )
    }

    private fun testPattern(patternStr: String, sample: String): String? {
        return try {
            val reg = Regex(patternStr, RegexOption.IGNORE_CASE)
            val match = reg.find(sample) ?: return null
            if (match.groups.size > 1 && match.groups[1] != null) {
                match.groupValues[1].trim()
            } else {
                match.value.trim()
            }
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // AI Fallback Prompt Builder & JSON Response Parser
    // =========================================================================

    /**
     * Formats an optimal prompt for external AI (ChatGPT, Claude, Gemini)
     * adhering strictly to ArcTracker's deterministic regex engine standards.
     */
    fun buildAiPrompt(sampleMessage: String, targetCategory: String = "Merchant / Payee"): String {
        return """
I need an Android Java-compatible Regular Expression (java.util.regex) to extract transaction details for my ArcTracker local budget app.

Sample Banking SMS:
"${sampleMessage.trim()}"

Target to Extract: $targetCategory

Instructions:
1. Make the expression case-insensitive using (?i).
2. Group 1 must capture the target value:
   - For "Amount": capture digits in group 1 (e.g. 1,450.00).
   - For "Merchant / Payee": capture store / payee name in group 1.
   - For "Account / Card": capture last 4 digits in group 1.
   - For "Type Debit/Credit": match debit/credit keyword.
3. Return ONLY a single JSON code block formatted exactly like this:

```json
{
  "name": "Short Name (e.g. HDFC UPI Payee)",
  "category": "$targetCategory",
  "pattern": "PASTE_REGEX_PATTERN_HERE",
  "description": "Short explanation of anchor keywords used"
}
```
""".trimIndent()
    }

    /**
     * Parses the AI's response text and extracts the JSON block or regex pattern.
     */
    fun parseAiResponse(response: String): SynthesisResult? {
        val trimmed = response.trim()
        if (trimmed.isEmpty()) return null

        val pattern = extractJsonField(trimmed, "pattern")
        val name = extractJsonField(trimmed, "name")
        val category = extractJsonField(trimmed, "category") ?: "Merchant / Payee"
        val desc = extractJsonField(trimmed, "description") ?: "Generated via AI Assistant"

        if (!pattern.isNullOrBlank()) {
            return SynthesisResult(
                category = category,
                pattern = pattern,
                suggestedName = if (!name.isNullOrBlank()) name else "Custom AI Pattern",
                description = desc,
                extractedPreview = "Pattern Extracted",
                isVerified = true
            )
        }

        // Fallback: If AI just returned a bare regex starting with (?i) or containing regex symbols
        if (trimmed.startsWith("(?i)") || trimmed.contains("([") || trimmed.contains("(?:")) {
            val barePattern = trimmed.substringBefore("\n").trim().removeSurrounding("`")
            return SynthesisResult(
                category = "Merchant / Payee",
                pattern = barePattern,
                suggestedName = "Imported AI Pattern",
                description = "Regex pattern imported from AI",
                extractedPreview = "Imported",
                isVerified = true
            )
        }

        return null
    }

    private fun extractJsonField(text: String, fieldName: String): String? {
        val regex = Regex("\"$fieldName\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"")
        val raw = regex.find(text)?.groupValues?.get(1)?.trim() ?: return null
        return raw.replace("\\\\", "\\").replace("\\\"", "\"")
    }
}
