package com.subhashrelangi.arctracker.engine.category

import java.util.Locale

/**
 * Centralized, deterministic merchant and transaction text normalizer (Milestone 10 & Phase 11).
 *
 * Responsibilities:
 * - Deterministic lowercase & whitespace normalization.
 * - Safe delimiter and punctuation handling.
 * - Stripping common financial noise prefixes (e.g., "UPI/", "POS ", "VPA/").
 * - Word-boundary-safe tokenization and matching (prevents substring collisions like "ola" in "colab").
 * - Compound word and suffix decomposition for Indian UPI narratives, VPAs, and single-case merchant handles.
 */
object MerchantNormalizer {

    private val PREFIX_PATTERNS = listOf(
        Regex("""^(upi|pos|imps|neft|rtgs|ach|ecs|vpa|e-com|ecom|bbps|pg|qr)(\s*[/:\-_*]\s*|\s+)""", RegexOption.IGNORE_CASE),
        Regex("""^(to|from|at|paid to|sent to|received from)\s+""", RegexOption.IGNORE_CASE),
        Regex("""^www\.""", RegexOption.IGNORE_CASE)
    )

    private val GATEWAY_TRANSPORT_PREFIXES = listOf(
        Regex("""^(razorpay|paytm|phonepe|googlepay|gpay|bharatpe|pinelabs)(\s*[*#_/\-:]\s*|\s+)""", RegexOption.IGNORE_CASE)
    )

    private val TRAILING_NOISE = Regex("""[\s*#\-:.]+$""")
    private val LEADING_NOISE = Regex("""^[\s*#\-:.]+""")
    private val MULTI_SPACE = Regex("""\s+""")

    private val DOMAIN_SUFFIX = Regex("""\.(com|co\.in|org|net|in|co|io)$""", RegexOption.IGNORE_CASE)

    private val CAMEL_CASE_SPLIT = Regex("""(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])""")
    private val LEGAL_SUFFIXES = setOf("pvt", "ltd", "private", "limited", "llp", "inc", "corp")

    /**
     * Verified multi-character domain stems for conservative morphological decomposition.
     * Short words (bus, gas, cab, lab, tea) are intentionally excluded to prevent substring collisions.
     */
    private val COMPOUND_DOMAIN_STEMS = listOf(
        // Transit & Travel Stems (Longest first)
        "apsrtconline", "apsrtc", "tgsrtc", "tsrtc", "ksrtc", "msrtc", "upsrtc", "bmtc", "irctc",
        "travels", "travel", "transit", "cabs", "motors", "airline", "airways",
        // Food & Dining Stems
        "supermarket", "restaurant", "bhojanalaya", "canteen", "bakery", "bakers", "tiffins",
        "tiffin", "biryani", "sweets", "bakes", "dhaba", "juices", "juice", "foods", "food",
        "hotel", "cafe", "mess",
        // Healthcare Stems
        "diagnostics", "diagnostic", "pharmacy", "medicals", "medical", "hospital",
        "clinic", "pharma",
        // Shopping Stems
        "jewellers", "textiles", "garments", "stores", "kirana", "silks", "mart",
        // Entertainment Stems
        "multiplex", "cinemas", "cinema",
        // Fuel Stems
        "petrol", "fuel"
    )

    /**
     * Normalizes a merchant or entity name into a clean, lowercased string.
     * When [stripPrefixes] is true, strips transactional headers (e.g., "UPI/", "POS ").
     * When false, preserves all semantic words (used for pattern/token matching).
     */
    fun normalize(text: String?, stripPrefixes: Boolean = true): String {
        if (text.isNullOrBlank()) return ""

        var s = text.trim()

        if (stripPrefixes) {
            // Strip verified gateway transport prefixes
            for (pattern in GATEWAY_TRANSPORT_PREFIXES) {
                val replaced = pattern.replace(s, "")
                if (replaced.isNotBlank()) {
                    s = replaced
                    break
                }
            }

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
     * Decomposes a merchant narrative, VPA handle, or concatenated single-case string
     * into clean, normalized semantic tokens for domain matching.
     */
    fun decomposeMerchantTokens(rawMerchant: String?): List<String> {
        if (rawMerchant.isNullOrBlank()) return emptyList()

        var s = rawMerchant.trim()

        // 1. If input is a UPI VPA (contains '@'), extract semantic handle
        if (s.contains("@")) {
            val handle = s.substringBefore("@")
            // Strip common trailing payment app suffix in handle (e.g., .paytm, .rzp, .ibz, .ybl, .axl, .upi)
            s = handle.replace(Regex("""\.(paytm|rzp|ybl|axl|ibl|upi|okaxis|okhdfcbank|okicici|oksbi)$""", RegexOption.IGNORE_CASE), "")
        }

        // 2. Strip transport gateway prefixes if followed by a merchant string
        for (pattern in GATEWAY_TRANSPORT_PREFIXES) {
            val stripped = pattern.replace(s, "")
            if (stripped.isNotBlank()) {
                s = stripped
                break
            }
        }
        for (pattern in PREFIX_PATTERNS) {
            val stripped = pattern.replace(s, "")
            if (stripped.isNotBlank()) {
                s = stripped
                break
            }
        }

        // 3. Strip trailing numeric transaction ID/noise (e.g., "-123", " 12345", "_4321")
        s = s.replace(Regex("""[-_/\s*#]+\d{2,15}$"""), "")

        // 4. Split CamelCase transitions (e.g. "RNRKFoods" -> "RNRK Foods", "SriBalajiSweets" -> "Sri Balaji Sweets")
        s = CAMEL_CASE_SPLIT.replace(s, " ")

        // 5. Replace punctuation separators with spaces
        s = s.replace(Regex("""[/_\-#*.:]+"""), " ")
        s = MULTI_SPACE.replace(s, " ").trim().lowercase(Locale.ENGLISH)

        if (s.isEmpty()) return emptyList()

        // 6. Tokenize and filter legal noise
        val baseTokens = s.split(" ").filter { it.isNotBlank() && !LEGAL_SUFFIXES.contains(it) }

        // 7. Morphological stem decomposition for concatenated tokens (e.g. "rnrkfoods", "sribalajisweets", "apsrtconline")
        val decomposed = mutableListOf<String>()
        for (token in baseTokens) {
            var matchedStem: String? = null
            for (stem in COMPOUND_DOMAIN_STEMS) {
                if (token.length > stem.length) {
                    if (token.endsWith(stem) && token.length - stem.length >= 2) {
                        matchedStem = stem
                        val prefix = token.substring(0, token.length - stem.length)
                        decomposed.add(prefix)
                        decomposed.add(stem)
                        break
                    } else if (token.startsWith(stem) && token.length - stem.length >= 2) {
                        matchedStem = stem
                        val suffix = token.substring(stem.length)
                        decomposed.add(stem)
                        decomposed.add(suffix)
                        break
                    }
                }
            }
            if (matchedStem == null) {
                decomposed.add(token)
            }
        }

        return decomposed.filter { it.isNotBlank() }
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
     * Checks if [text] contains [token] as a distinct word boundary token or decomposed domain token.
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
        if (tokens.contains(normToken)) return true

        // Decomposed token check
        val decomposed = decomposeMerchantTokens(text)
        return decomposed.contains(normToken)
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
