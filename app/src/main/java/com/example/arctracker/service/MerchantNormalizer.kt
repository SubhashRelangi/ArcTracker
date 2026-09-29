package com.example.arctracker.service

import java.util.Locale

/**
 * Centralized, deterministic merchant and transaction text normalizer (Milestone 10).
 *
 * Responsibilities:
 * - Deterministic lowercase & whitespace normalization.
 * - Safe delimiter and punctuation handling.
 * - Stripping common financial noise prefixes (e.g., "UPI/", "POS ", "VPA/").
 * - Word-boundary-safe tokenization and matching (prevents substring collisions like "ola" in "colab").
 */
object MerchantNormalizer {

    private val PREFIX_PATTERNS = listOf(
        Regex("""^(upi|pos|imps|neft|rtgs|ach|ecs|vpa|e-com|bbps|pg|qr)(\s*[/:\-_]\s*|\s+)""", RegexOption.IGNORE_CASE),
        Regex("""^(to|from|at|paid to|sent to|received from)\s+""", RegexOption.IGNORE_CASE),
        Regex("""^www\.""", RegexOption.IGNORE_CASE)
    )

    private val TRAILING_NOISE = Regex("""[\s*#\-:.]+$""")
    private val LEADING_NOISE = Regex("""^[\s*#\-:.]+""")
    private val MULTI_SPACE = Regex("""\s+""")

    private val DOMAIN_SUFFIX = Regex("""\.(com|co\.in|org|net|in|co|io)$""", RegexOption.IGNORE_CASE)

    /**
     * Normalizes a merchant or entity name into a clean, lowercased string.
     * When [stripPrefixes] is true, strips transactional headers (e.g., "UPI/", "POS ").
     * When false, preserves all semantic words (used for pattern/token matching).
     */
    fun normalize(text: String?, stripPrefixes: Boolean = true): String {
        if (text.isNullOrBlank()) return ""

        var s = text.trim()

        if (stripPrefixes) {
            // Strip known transactional prefix headers
            for (pattern in PREFIX_PATTERNS) {
                s = pattern.replace(s, "")
            }
        }

        // Clean boundary noise
        s = LEADING_NOISE.replace(s, "")
        s = TRAILING_NOISE.replace(s, "")

        // Strip web domain suffix
        s = DOMAIN_SUFFIX.replace(s, "")

        // Normalize separators: replace common symbols with spaces
        s = s.replace(Regex("""[/_\-#*.:]+"""), " ")

        // Collapse whitespace
        s = MULTI_SPACE.replace(s, " ")

        return s.trim().lowercase(Locale.ENGLISH)
    }

    /**
     * Splits normalized text into distinct alphanumeric word tokens.
     */
    fun tokenize(text: String?, stripPrefixes: Boolean = false): List<String> {
        val norm = normalize(text, stripPrefixes = stripPrefixes)
        if (norm.isEmpty()) return emptyList()
        return norm.split(" ").filter { it.isNotBlank() }
    }

    /**
     * Checks if [text] contains [token] as a distinct word boundary token.
     * Prevents false substring matches like "ola" inside "colab" or "gas" inside "vegas".
     */
    fun containsToken(text: String?, token: String): Boolean {
        if (text.isNullOrBlank() || token.isBlank()) return false
        val normText = normalize(text, stripPrefixes = false)
        val normToken = normalize(token, stripPrefixes = false)
        if (normToken.isEmpty()) return false

        // Check if token has multiple words
        if (normToken.contains(" ")) {
            return normText.contains(normToken)
        }

        // Exact match check
        if (normText == normToken) return true

        // Word-boundary token check
        val tokens = tokenize(normText, stripPrefixes = false)
        return tokens.contains(normToken)
    }

    /**
     * Exact equality check on normalized versions of both strings.
     */
    fun matchesExact(text: String?, target: String): Boolean {
        if (text.isNullOrBlank() || target.isBlank()) return false
        return normalize(text, stripPrefixes = false) == normalize(target, stripPrefixes = false)
    }

    /**
     * Checks if [text] starts with [prefix] on word boundary.
     */
    fun startsWithToken(text: String?, prefix: String): Boolean {
        if (text.isNullOrBlank() || prefix.isBlank()) return false
        val normText = normalize(text, stripPrefixes = false)
        val normPrefix = normalize(prefix, stripPrefixes = false)
        return normText.startsWith(normPrefix)
    }
}
