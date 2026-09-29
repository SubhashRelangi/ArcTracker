package com.subhashrelangi.arctracker.service

import com.subhashrelangi.arctracker.utils.IgnoreRule

/**
 * Pure, deterministic noise detector for normalized notifications (Step 4).
 *
 * Identifies notifications that represent OTPs, promotional marketing, loan offers,
 * security alerts, balance inquiries, statement notices, or hypothetical offer conditions.
 */
object NoiseDetector {

    // 1. OTP / Authentication patterns
    private val OTP_PATTERNS = listOf(
        Regex("""\b(otp|one[\s-]+time[\s-]+password)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(verification[\s-]+code|security[\s-]+code|login[\s-]+code|auth[\s-]+code|v-code)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(is your otp|otp for|code is|secret code)\b""", RegexOption.IGNORE_CASE)
    )

    // 2. Promotional / Marketing patterns
    private val PROMOTIONAL_PATTERNS = listOf(
        Regex("""\b(cashback offer|special offer|limited time offer|exclusive offer|festive offer|mega offer)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(shop now|buy now|order now|claim now|avail now|book now)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(discount|coupon|promo code|promocode|voucher)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(reward points? expiring|cashback reward|scratch card|win rewards?|win scratch cards?)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(flat\s+(?:rs\.?|inr|₹)?\s*\d+\s+off|upto\s+\d+%\s+off|up to\s+\d+%\s+off)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:send money to.*(?:win|reward|cashback|scratch)|pay now (?:to|and)|you can pay|recharge for|recharge with|pay your .* and get)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:save on your next|on your next purchase|invite friends and earn)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(recharge now|recharge today|recharge online|tap to recharge|click to recharge|browse plans)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:special|best|popular|top|exclusive|latest|recommended|hot)?\s*(?:recharge\s+plan|subscription\s+plan)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bplan\s+(?:at|of|for|@)?\s*(?:rs\.?|inr|₹)?\s*\d+\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:validity\s*[:=\-]?|active for|valid for)\s*\d+\s*(?:days|months|hours)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b\d+(?:\.\d+)?\s*(?:gb|mb)\s*/\s*day\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:unlimited\s+5g|unlimited\s+calls|high[\s-]speed data)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:free|complimentary)\s+(?:hotstar|subscription|ott|membership|access)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:enjoy|get)\s+(?:the\s+)?(?:offer|benefits|subscription)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:starting at|starts at|just for)\s+(?:rs\.?|inr|₹)?\s*\d+\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:subscribe now|subscribe today|join now|try now|get premium)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:get|enjoy|subscribe to)\s+(?:[A-Za-z]+\s+)?(?:premium|membership|vip|subscription|plan|pack)\s+(?:for|at|@)?\s*(?:rs\.?|inr|₹)?\s*\d+\b""", RegexOption.IGNORE_CASE),
        Regex("""\bfor\s+\d+\s+(?:months?|days?|years?)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:offer price|plan price|introductory price)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bis\s+one\s+bill\s+away\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:chance\s+to\s+win|win\s+cashback|win\s+rewards?|win\s+scratch\s+cards?|eligible\s+to\s+win)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:get\s+a\s+chance\s+to\s+win)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:use\s+[a-z0-9_]{4,15})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:no\s+extra\s+fees|no\s+fees|zero\s+fees)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bdebit\s+card\s+offers?\b""", RegexOption.IGNORE_CASE)
    )

    // 3. Loan / Credit marketing patterns
    private val LOAN_PATTERNS = listOf(
        Regex("""\b(personal loan|instant loan|pre-approved|pre approved|loan offer|credit card offer|apply now|get instant cash|check eligibility)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(loan up to|loan of up to|credit limit increase)\b""", RegexOption.IGNORE_CASE)
    )

    // 4. Security / Login alert patterns
    private val SECURITY_PATTERNS = listOf(
        Regex("""\b(new login|login successful|logged in|signed in|device login|security alert|password changed|account accessed|unrecognized login|login detected)\b""", RegexOption.IGNORE_CASE)
    )

    // 5. Balance inquiry / Balance only patterns
    private val BALANCE_INQUIRY_PATTERNS = listOf(
        Regex("""\b(available balance|current balance|clear balance|acc balance|a/c balance|bal is|balance is|bal\s*[:=\-]|account balance is|your account balance is)\b""", RegexOption.IGNORE_CASE)
    )

    // Completed transaction action pattern (to differentiate a transaction notification that mentions remaining balance)
    private val COMPLETED_TRANSACTION_ACTION_PATTERN = Regex(
        """\b(debited|debit(?!\s*(?:card|limit|offer|option))|credited|credit(?!\s*(?:card|limit|score|bill|line|facility|offer|option))|paid|spent|sent|transferred|withdrawn|deposited|refunded|transaction|txn)\b""",
        RegexOption.IGNORE_CASE
    )

    // Strong confirmed completed transaction pattern (to prevent true recharge/payment confirmations from being flagged as promo offers)
    private val CONFIRMED_COMPLETED_TRANSACTION_PATTERN = Regex(
        """\b(debited|credited|paid|spent|sent|transferred|withdrawn|deposited|refunded|recharge(?:d)?\s+(?:was\s+|is\s+|has been\s+)?(?:successful|completed|processed)|payment\s+(?:was\s+|is\s+|has been\s+)?(?:successful|completed|processed)|transaction\s+(?:was\s+|is\s+|has been\s+)?(?:successful|completed|processed)|recharge of\s+(?:rs\.?|inr|₹)?\s*[\d,]+(?:\.\d{1,2})?\s+(?:was\s+|is\s+|has been\s+)?(?:successful|completed|processed))\b""",
        RegexOption.IGNORE_CASE
    )

    // 6. Informational statement patterns
    private val INFORMATIONAL_PATTERNS = listOf(
        Regex("""\b(statement generated|statement available|monthly statement|e-statement|account summary|transaction history available|bill reminder|payment reminder|reminder\s*[:\-]|due date is|due date|bill is generated|bill due|amount due|payment due|due on|pay by|pay before|upcoming payment)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:credit\s+card\s+payment\s+options|payment\s+options?\s+available|options?\s+available)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:credit\s+limit|debit\s+limit|transaction\s+limit|daily\s+limit)\b""", RegexOption.IGNORE_CASE)
    )

    // 7. Hypothetical / Conditional / Offer terms patterns
    private val HYPOTHETICAL_PATTERNS = listOf(
        Regex("""\b(when you pay|if you pay|on your next|on next transaction|use your card to get|use code)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpay\s+(?:rs\.?|inr|₹)?\s*\d+.*(?:and get|to get|and receive)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bget\s+(?:rs\.?|inr|₹)?\s*\d+.*(?:cashback|off|discount)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(transaction limit is|daily limit is|transfer limit is)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(win up to|earn up to|get up to)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpay\s+your\b.*?\b(?:with\s+no\s+extra\s+fees|and\s+get|to\s+get|to\s+win|and\s+win)\b""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("""\b(?:get|win|earn)\s+(?:a\s+chance\s+to\s+win\s+)?(?:rs\.?|inr|₹)?\s*[\d,]+.*?\b(?:cashback|reward|bonus|discount|off)\b""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    )

    /**
     * Evaluates a normalized notification for noise signals.
     *
     * @param notification The normalized notification to inspect.
     * @param ignoreRules Optional user or system ignore rules.
     * @return [NoiseDetectionResult] detailing whether noise was detected, category, and reasons.
     */
    fun detectNoise(
        notification: NormalizedNotification,
        ignoreRules: List<IgnoreRule> = emptyList()
    ): NoiseDetectionResult {
        val text = notification.normalizedCombinedText
        val packageName = notification.packageName
        if (text.isBlank()) {
            return NoiseDetectionResult(
                isNoise = false,
                primaryCategory = NoiseCategory.NONE
            )
        }

        val matchedSignals = mutableListOf<String>()
        val reasons = mutableListOf<String>()
        var primaryCategory = NoiseCategory.NONE

        // 0. Check Unsupported Language Policy (Milestone 5.2)
        if (LanguagePolicyHelper.shouldRejectAsUnsupportedLanguage(text)) {
            matchedSignals.add("UNSUPPORTED_NON_ENGLISH_LANGUAGE")
            reasons.add("Contains unsupported non-English script without strong English completed-transaction evidence")
            return NoiseDetectionResult(
                isNoise = true,
                primaryCategory = NoiseCategory.UNSUPPORTED_LANGUAGE,
                matchedSignals = matchedSignals,
                reasons = reasons
            )
        }

        // 1. Check custom & system IgnoreRules from IgnoreRulesManager
        for (rule in ignoreRules) {
            val isMatch = when (rule.type.lowercase()) {
                "sender" -> {
                    when (rule.matchType.lowercase()) {
                        "contains" -> packageName.contains(rule.value, ignoreCase = true) || text.contains(rule.value, ignoreCase = true)
                        "exact match" -> packageName.equals(rule.value, ignoreCase = true)
                        "starts with" -> packageName.startsWith(rule.value, ignoreCase = true)
                        else -> packageName.contains(rule.value, ignoreCase = true)
                    }
                }
                "keyword" -> {
                    when (rule.matchType.lowercase()) {
                        "contains" -> text.contains(rule.value, ignoreCase = true)
                        "exact match" -> text.equals(rule.value, ignoreCase = true)
                        "starts with" -> text.startsWith(rule.value, ignoreCase = true)
                        else -> text.contains(rule.value, ignoreCase = true)
                    }
                }
                "pattern" -> {
                    try {
                        Regex(rule.value, RegexOption.IGNORE_CASE).containsMatchIn(text)
                    } catch (e: Exception) {
                        false
                    }
                }
                else -> text.contains(rule.value, ignoreCase = true)
            }

            if (isMatch) {
                matchedSignals.add("IGNORE_RULE_${rule.id} (${rule.value})")
                reasons.add("Matched ignore rule: [${rule.type}] '${rule.value}'")
                if (primaryCategory == NoiseCategory.NONE) {
                    primaryCategory = NoiseCategory.IGNORE_RULE_MATCH
                }
            }
        }

        // 2. Check OTP / Authentication
        for (pattern in OTP_PATTERNS) {
            if (pattern.containsMatchIn(text)) {
                matchedSignals.add("OTP_OR_AUTHENTICATION")
                reasons.add("Contains OTP or verification code pattern")
                if (primaryCategory == NoiseCategory.NONE || primaryCategory == NoiseCategory.IGNORE_RULE_MATCH) {
                    primaryCategory = NoiseCategory.OTP_OR_AUTHENTICATION
                }
                break
            }
        }

        val hasCompletedAction = COMPLETED_TRANSACTION_ACTION_PATTERN.containsMatchIn(text)
        val hasConfirmedTransaction = CONFIRMED_COMPLETED_TRANSACTION_PATTERN.containsMatchIn(text)

        // 3. Check Promotional / Offers
        for (pattern in PROMOTIONAL_PATTERNS) {
            if (pattern.containsMatchIn(text) && !hasConfirmedTransaction) {
                matchedSignals.add("PROMOTIONAL_OR_OFFER")
                reasons.add("Contains promotional marketing or telecom plan offer pattern")
                if (primaryCategory == NoiseCategory.NONE) {
                    primaryCategory = NoiseCategory.PROMOTIONAL_OR_OFFER
                }
                break
            }
        }

        // 4. Check Loan / Credit Marketing
        for (pattern in LOAN_PATTERNS) {
            if (pattern.containsMatchIn(text)) {
                matchedSignals.add("LOAN_OR_CREDIT_MARKETING")
                reasons.add("Contains loan or credit marketing pattern")
                if (primaryCategory == NoiseCategory.NONE) {
                    primaryCategory = NoiseCategory.LOAN_OR_CREDIT_MARKETING
                }
                break
            }
        }

        // 5. Check Security / Login Alerts
        for (pattern in SECURITY_PATTERNS) {
            if (pattern.containsMatchIn(text)) {
                matchedSignals.add("SECURITY_OR_LOGIN")
                reasons.add("Contains login or security alert pattern")
                if (primaryCategory == NoiseCategory.NONE) {
                    primaryCategory = NoiseCategory.SECURITY_OR_LOGIN
                }
                break
            }
        }

        // 6. Check Hypothetical / Conditional Offer Terms
        for (pattern in HYPOTHETICAL_PATTERNS) {
            if (pattern.containsMatchIn(text) && !hasConfirmedTransaction) {
                matchedSignals.add("HYPOTHETICAL_OR_OFFER_TERMS")
                reasons.add("Contains hypothetical or conditional offer terms")
                if (primaryCategory == NoiseCategory.NONE) {
                    primaryCategory = NoiseCategory.HYPOTHETICAL_OR_OFFER_TERMS
                }
                break
            }
        }

        // 7. Check Informational Statements
        for (pattern in INFORMATIONAL_PATTERNS) {
            if (pattern.containsMatchIn(text)) {
                matchedSignals.add("INFORMATIONAL_STATEMENT")
                reasons.add("Contains statement or informational notice pattern")
                if (primaryCategory == NoiseCategory.NONE) {
                    primaryCategory = NoiseCategory.INFORMATIONAL_STATEMENT
                }
                break
            }
        }

        // 8. Check Balance-Only notifications (if no debit/credit action is mentioned)
        val hasBalanceMention = BALANCE_INQUIRY_PATTERNS.any { it.containsMatchIn(text) }
        if (hasBalanceMention && !hasCompletedAction) {
            matchedSignals.add("BALANCE_INQUIRY")
            reasons.add("Contains balance inquiry without transaction action")
            if (primaryCategory == NoiseCategory.NONE) {
                primaryCategory = NoiseCategory.BALANCE_INQUIRY
            }
        }

        val isNoise = matchedSignals.isNotEmpty()
        return NoiseDetectionResult(
            isNoise = isNoise,
            primaryCategory = primaryCategory,
            matchedSignals = matchedSignals,
            reasons = reasons
        )
    }
}
