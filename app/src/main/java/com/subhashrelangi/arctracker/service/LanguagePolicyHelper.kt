package com.subhashrelangi.arctracker.service

import java.lang.Character.UnicodeScript

/**
 * Pure, deterministic utility supporting English-Only Transaction Classification and
 * Non-English/Unsupported Message policy (Milestone 5.2).
 *
 * Enforces:
 * 1. English semantic classification for supported messages.
 * 2. Conservative non-financial rejection of unsupported non-English messages unless
 *    they establish strong, unambiguous supported English completed-transaction evidence.
 * 3. Support for mixed-language messages that contain clear English financial transaction markers.
 */
object LanguagePolicyHelper {

    private val UNSUPPORTED_INDIC_SCRIPTS = setOf(
        UnicodeScript.TELUGU,
        UnicodeScript.DEVANAGARI,
        UnicodeScript.TAMIL,
        UnicodeScript.KANNADA,
        UnicodeScript.MALAYALAM,
        UnicodeScript.BENGALI,
        UnicodeScript.GUJARATI,
        UnicodeScript.GURMUKHI,
        UnicodeScript.ORIYA
    )

    /**
     * Checks if the text contains non-English / unsupported scripts (specifically Indic scripts).
     */
    fun containsUnsupportedScript(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            // Fast check for common Indic block range: 0x0900..0x0D7F
            if (codePoint in 0x0900..0x0D7F) {
                return true
            }
            val script = try {
                UnicodeScript.of(codePoint)
            } catch (e: Exception) {
                UnicodeScript.UNKNOWN
            }
            if (script in UNSUPPORTED_INDIC_SCRIPTS) {
                return true
            }
            i += Character.charCount(codePoint)
        }
        return false
    }

    // Strong, unambiguous supported English completed-transaction patterns
    private val STRONG_ENGLISH_FINANCIAL_EVIDENCE_PATTERNS = listOf(
        // Explicit debit action verbs
        Regex("""(?i)\b(debited|debited from|debited by|debited for|spent at|withdrawn from|deducted from)\b"""),
        // Explicit credit action verbs
        Regex("""(?i)\b(credited|credited to|credited with|deposited in|deposited into|cashback received)\b"""),
        // Confirmed transaction/payment/recharge success phrases
        Regex("""(?i)\b(?:payment|transaction|transfer|recharge|order payment)\s+(?:was\s+|is\s+|has been\s+)?(?:successful|completed|processed)\b"""),
        Regex("""(?i)\b(?:recharge of|payment of)\s+(?:rs\.?|inr|₹)?\s*[\d,]+(?:\.\d{1,2})?\s+(?:was\s+|is\s+|has been\s+)?(?:successful|completed|processed)\b"""),
        // Paid to/at/towards/for
        Regex("""(?i)\b(?:paid|transferred|sent)\s+(?:to|towards|at|for)\s+[A-Za-z0-9]"""),
        // Received from
        Regex("""(?i)\b(?:received)\s+(?:from)\s+[A-Za-z0-9]"""),
        // Amount charged
        Regex("""(?i)\b(?:amount charged|charged to)\b"""),
        // Money sent / received
        Regex("""(?i)\b(?:money sent|money received)\b"""),
        // Account action with reference
        Regex("""(?i)\b(?:a/c|acct|account)\s+[x*0-9]+\s+(?:debited|credited|paid)\b"""),
        Regex("""(?i)\b(?:debited|credited)\s+.*?\b(?:upi\s*ref|rrn|utr)\b""")
    )

    /**
     * Checks if the message establishes strong, unambiguous supported English completed-transaction evidence.
     */
    fun hasStrongEnglishFinancialEvidence(text: String): Boolean {
        return STRONG_ENGLISH_FINANCIAL_EVIDENCE_PATTERNS.any { it.containsMatchIn(text) }
    }

    /**
     * Evaluates whether an unsupported-language message should be rejected from transaction processing.
     * Returns true if the message contains unsupported non-English scripts AND lacks strong English financial evidence.
     */
    fun shouldRejectAsUnsupportedLanguage(text: String): Boolean {
        if (!containsUnsupportedScript(text)) {
            return false
        }
        // If it contains unsupported script, it MUST have strong English financial evidence to proceed
        return !hasStrongEnglishFinancialEvidence(text)
    }
}
