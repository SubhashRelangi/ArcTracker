package com.subhashrelangi.arctracker.ui.core

import java.util.Locale

/**
 * Intelligent on-device Search Engine for ArcTracker.
 *
 * Implements prefix-first word matching and piece-to-piece token matching:
 * 1. Single character query (e.g. "a"):
 *    Strict prefix matching — only matches items where at least one word starts with the character.
 *    For example, "a" matches "Amazon", "Axis Bank", "Auto & Transport", but NOT "Swiggy" or "Starbucks".
 * 2. Multi-word / multi-token query (e.g. "Amazon 500", "Food dining", "HDFC Regalia"):
 *    Piece-to-piece matching — every search token (piece) must match against the candidate fields.
 * 3. Relevance scoring:
 *    Calculates match scores to sort exact prefix matches and word starts above generic substring matches.
 */
object SmartSearchMatcher {

    /**
     * Checks if [candidateFields] match the given [searchQuery].
     */
    fun matches(query: String, vararg candidateFields: String?): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true

        val queryTokens = trimmed.lowercase(Locale.ROOT)
            .split("\\s+".toRegex())
            .filter { it.isNotEmpty() }
        if (queryTokens.isEmpty()) return true

        val nonNullFields = candidateFields.filterNotNull().filter { it.isNotBlank() }
        if (nonNullFields.isEmpty()) return false

        // Extract individual words across all candidate fields (split by spaces, punctuation, symbols)
        val allWords = nonNullFields.flatMap { field ->
            field.lowercase(Locale.ROOT)
                .split("[^\\p{L}\\p{Nd}]+".toRegex())
                .filter { it.isNotEmpty() }
        }

        // Single character query: strictly match words that START with this character
        if (trimmed.length == 1) {
            val charStr = trimmed.lowercase(Locale.ROOT)
            return allWords.any { it.startsWith(charStr) }
        }

        // Multi-character / Piece-to-piece query:
        // Every token in the query must match at least one candidate field or word
        return queryTokens.all { token ->
            // Check if any word starts with this token (e.g. "ama" -> "amazon")
            val wordPrefixMatch = allWords.any { it.startsWith(token) }
            if (wordPrefixMatch) return@all true

            // Or if any field contains this token as a substring (e.g. "500" in "₹500.00")
            nonNullFields.any { field ->
                field.lowercase(Locale.ROOT).contains(token)
            }
        }
    }

    /**
     * Calculates a relevance score for sorting matching results.
     * Higher score indicates a better, more relevant match.
     */
    fun score(query: String, primaryField: String?, vararg otherFields: String?): Int {
        val trimmed = query.trim().lowercase(Locale.ROOT)
        if (trimmed.isEmpty()) return 0

        var score = 0
        val p = primaryField?.lowercase(Locale.ROOT)?.trim() ?: ""

        if (p.startsWith(trimmed)) {
            score += 100
        } else if (p.split("[^\\p{L}\\p{Nd}]+".toRegex()).any { it.startsWith(trimmed) }) {
            score += 60
        } else if (p.contains(trimmed)) {
            score += 30
        }

        for (other in otherFields) {
            val o = other?.lowercase(Locale.ROOT)?.trim() ?: continue
            if (o.startsWith(trimmed)) {
                score += 25
            } else if (o.split("[^\\p{L}\\p{Nd}]+".toRegex()).any { it.startsWith(trimmed) }) {
                score += 15
            } else if (o.contains(trimmed)) {
                score += 5
            }
        }

        return score
    }
}
