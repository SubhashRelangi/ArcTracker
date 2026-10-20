package com.subhashrelangi.arctracker.security

import java.util.regex.Pattern

/**
 * Production-quality Sensitive Data Sanitizer (Milestone 16: Security & Privacy Hardening).
 *
 * Guarantees:
 * - Detects and masks unmasked 15-16 digit card numbers, keeping only the last 4 digits.
 * - Detects and masks long bank account numbers, keeping only the trailing 4 digits.
 * - Redacts OTP (One-Time Password) and PIN codes.
 * - Sanitizes exception messages so database schemas or internal payloads are not exposed.
 */
object SensitiveDataSanitizer {

    // Regex for 15-16 digit card numbers (with optional spaces or hyphens)
    private val CARD_PATTERN = Pattern.compile(
        "\\b(?:\\d{4}[ -]?){3}\\d{4}\\b"
    )

    // Regex for bank account numbers (8 to 18 consecutive digits)
    private val ACCOUNT_NUMBER_PATTERN = Pattern.compile(
        "\\b(?<![\\d.])(\\d{4,14})(\\d{4})\\b"
    )

    // Regex for OTP codes (e.g., "OTP is 123456", "verification code 9482")
    private val OTP_PATTERN = Pattern.compile(
        "(?i)\\b(otp|code|pin|passcode|secret)\\s*(?:is|:|=)?\\s*([0-9]{4,8})\\b"
    )

    // Regex for UPI IDs / VPAs (e.g., user@okhdfcbank, merchant@upi)
    private val UPI_PATTERN = Pattern.compile(
        "(?i)\\b([a-zA-Z0-9.\\-_]{2,64})@([a-zA-Z]{2,32})\\b"
    )

    // Regex for transaction reference / UTR numbers (e.g., "Ref: 123456789012", "UTR: 9876543210")
    private val REF_PATTERN = Pattern.compile(
        "(?i)\\b(ref(?:erence)?(?:\\s*no)?|utr|txn(?:\\s*id)?)\\s*(?:is|:|=)?\\s*([a-zA-Z0-9]{8,25})\\b"
    )

    // Regex for currency amounts (e.g. ₹1,500.00, Rs. 500, INR 1250.50, $45.00, USD 45)
    private val CURRENCY_AMOUNT_PATTERN = Pattern.compile(
        "(?i)(?:₹|rs\\.?|inr|usd|eur|gbp|\\$|€|£)\\s*[\\d,]+(?:\\.\\d{1,2})?|\\b[\\d,]+(?:\\.\\d{1,2})?\\s*(?:inr|rs\\.?|usd|eur|gbp)\\b"
    )

    // Regex for explicit amount and balance key-value pairs (e.g., "amount=500.00", "bal: 50000")
    private val EXPLICIT_AMOUNT_PATTERN = Pattern.compile(
        "(?i)\\b(amount|amt|balance|bal)\\s*[:=]\\s*([\\d,]+(?:\\.\\d{1,2})?)\\b"
    )

    /**
     * Sanitizes a string by redacting cards, long account numbers, OTPs, UPI handles, reference numbers, and monetary amounts.
     */
    fun sanitize(input: String?): String {
        if (input.isNullOrBlank()) return ""

        var result = input

        // 1. Redact OTPs
        val otpMatcher = OTP_PATTERN.matcher(result)
        result = otpMatcher.replaceAll("$1 [REDACTED]")

        // 2. Redact Transaction References / UTRs
        val refMatcher = REF_PATTERN.matcher(result)
        result = refMatcher.replaceAll("$1 [REDACTED]")

        // 3. Redact UPI / VPA handles
        val upiMatcher = UPI_PATTERN.matcher(result)
        result = upiMatcher.replaceAll("[REDACTED_VPA]")

        // 4. Redact currency amounts and explicit balance/amount values
        val currencyMatcher = CURRENCY_AMOUNT_PATTERN.matcher(result)
        result = currencyMatcher.replaceAll("[REDACTED_AMOUNT]")

        val explicitAmountMatcher = EXPLICIT_AMOUNT_PATTERN.matcher(result)
        result = explicitAmountMatcher.replaceAll("$1 [REDACTED_AMOUNT]")

        // 5. Mask 15-16 digit cards: keep only last 4 digits
        val cardMatcher = CARD_PATTERN.matcher(result)
        val sbCard = StringBuffer()
        while (cardMatcher.find()) {
            val matched = cardMatcher.group()
            val cleanDigits = matched.filter { it.isDigit() }
            val masked = "•••• •••• •••• " + cleanDigits.takeLast(4)
            cardMatcher.appendReplacement(sbCard, MatcherQuote(masked))
        }
        cardMatcher.appendTail(sbCard)
        result = sbCard.toString()

        // 6. Mask bank account numbers (8-18 consecutive digits): keep only last 4 digits
        val accountMatcher = ACCOUNT_NUMBER_PATTERN.matcher(result)
        val sbAccount = StringBuffer()
        while (accountMatcher.find()) {
            val matched = accountMatcher.group()
            val cleanDigits = matched.filter { it.isDigit() }
            val masked = "•••• " + cleanDigits.takeLast(4)
            accountMatcher.appendReplacement(sbAccount, MatcherQuote(masked))
        }
        accountMatcher.appendTail(sbAccount)
        result = sbAccount.toString()

        return result
    }

    /**
     * Ensures an account suffix is strictly represented as safe trailing digits (max 4 digits).
     */
    fun maskSuffix(suffix: String?): String {
        if (suffix.isNullOrBlank()) return ""
        val digitsOnly = suffix.filter { it.isDigit() }
        val last4 = digitsOnly.takeLast(4)
        return if (last4.isNotEmpty()) "•••• $last4" else ""
    }

    fun maskAccountSuffix(suffix: String?): String = maskSuffix(suffix)

    /**
     * Sanitizes user-facing error messages to avoid leaking SQL statements or file paths.
     */
    fun sanitizeErrorMessage(throwable: Throwable?): String {
        if (throwable == null) return "An unknown error occurred."
        val msg = throwable.message ?: return throwable.javaClass.simpleName

        // Don't show raw SQLite errors, constraints, or table dumps
        if (msg.contains("SQLite", ignoreCase = true) ||
            msg.contains("table", ignoreCase = true) ||
            msg.contains("constraint", ignoreCase = true) ||
            msg.contains("syntax error", ignoreCase = true)) {
            return "Database operation failed safely."
        }

        // Don't expose internal device storage paths
        if (msg.contains("/data/user", ignoreCase = true) ||
            msg.contains("/data/data", ignoreCase = true) ||
            msg.contains(".db", ignoreCase = true)) {
            return "Storage operation failed safely."
        }

        return sanitize(msg)
    }

    private fun MatcherQuote(s: String): String {
        return java.util.regex.Matcher.quoteReplacement(s)
    }
}
