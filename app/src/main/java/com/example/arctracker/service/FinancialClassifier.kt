package com.example.arctracker.service

import com.example.arctracker.utils.IgnoreRule

/**
 * Pure, deterministic classifier that determines the financial relevance of a [NormalizedNotification] (Step 4).
 *
 * Evaluates financial action signals, currency context, and noise signals.
 * Does NOT extract amounts, merchants, or create database transactions.
 */
object FinancialClassifier {

    // Known financial applications (used strictly as supporting context, not primary decision)
    private val KNOWN_FINANCIAL_PACKAGES = setOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app",                       // PhonePe
        "net.one97.paytm",                       // Paytm
        "in.org.npci.upiapp",                    // BHIM UPI
        "com.mobikwik_new",                      // MobiKwik
        "in.amazon.mShop.android.shopping",      // Amazon Pay
        "com.dreamplug.androidapp",              // CRED
        "com.sbi.SBIAnywhere",                   // YONO SBI
        "com.snapwork.hdfc",                     // HDFC Bank
        "com.csam.icici.bank.imobile",           // ICICI Bank
        "com.axis.mobile",                       // Axis Mobile
        "com.msf.kbank.mobile",                  // Kotak 811
        "money.jupiter"                          // Jupiter
    )

    // Debit / Outgoing action indicators
    private val DEBIT_ACTION_PATTERNS = listOf(
        Regex("""\b(paid|debited|debit|deducted|spent|sent|transferred|withdrawn|purchase|purchased|charged|fee|charges?)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(paid to|sent to|transferred to|payment to|payment of|transfer to)\b""", RegexOption.IGNORE_CASE)
    )

    // Credit / Incoming action indicators
    private val CREDIT_ACTION_PATTERNS = listOf(
        Regex("""\b(credited|credit|deposited|received|refunded|refund|cashback received|money received|money added)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(received from|credited to|credited with|added to wallet)\b""", RegexOption.IGNORE_CASE)
    )

    // Explicit transaction confirmation phrases
    private val TRANSACTION_SUCCESS_PATTERNS = listOf(
        Regex("""\b(payment successful|transaction successful|transfer successful|order payment successful|payment completed)\b""", RegexOption.IGNORE_CASE)
    )

    // Currency and financial context indicators
    private val CURRENCY_PATTERNS = listOf(
        Regex("""[₹$€£]"""),
        Regex("""\b(rs\.?|inr|rupees?)\b""", RegexOption.IGNORE_CASE),
        Regex("""(?:rs\.?|inr|₹)\s*\d+""", RegexOption.IGNORE_CASE)
    )

    // General / Weak financial keywords (insufficient on their own without action or currency)
    private val WEAK_FINANCIAL_KEYWORD_PATTERNS = listOf(
        Regex("""\b(transaction|payment|upi|transfer|account|a/c)\b""", RegexOption.IGNORE_CASE)
    )

    /**
     * Classifies a normalized notification for financial relevance and noise.
     *
     * @param notification The normalized notification to evaluate.
     * @param ignoreRules Optional custom or system ignore rules.
     * @return [NotificationClassificationResult] containing the explainable classification decision.
     */
    fun classify(
        notification: NormalizedNotification,
        ignoreRules: List<IgnoreRule> = emptyList()
    ): NotificationClassificationResult {
        val text = notification.normalizedCombinedText
        val packageName = notification.packageName

        // 1. Evaluate Noise first
        val noiseResult = NoiseDetector.detectNoise(notification, ignoreRules)

        // 2. Extract Financial Signals
        val matchedFinancialSignals = mutableListOf<String>()
        val classificationReasons = mutableListOf<String>()

        val hasDebitAction = DEBIT_ACTION_PATTERNS.any { it.containsMatchIn(text) }
        val hasCreditAction = CREDIT_ACTION_PATTERNS.any { it.containsMatchIn(text) }
        val hasTransactionSuccess = TRANSACTION_SUCCESS_PATTERNS.any { it.containsMatchIn(text) }
        val hasCurrencyContext = CURRENCY_PATTERNS.any { it.containsMatchIn(text) }
        val hasWeakFinancialKeyword = WEAK_FINANCIAL_KEYWORD_PATTERNS.any { it.containsMatchIn(text) }
        val isKnownFinancialApp = KNOWN_FINANCIAL_PACKAGES.contains(packageName)

        if (hasDebitAction) {
            matchedFinancialSignals.add("DEBIT_ACTION_INDICATOR")
            classificationReasons.add("Contains outgoing/debit action language")
        }
        if (hasCreditAction) {
            matchedFinancialSignals.add("CREDIT_ACTION_INDICATOR")
            classificationReasons.add("Contains incoming/credit action language")
        }
        if (hasTransactionSuccess) {
            matchedFinancialSignals.add("TRANSACTION_SUCCESS_CONFIRMATION")
            classificationReasons.add("Contains transaction success confirmation phrase")
        }
        if (hasCurrencyContext) {
            matchedFinancialSignals.add("CURRENCY_CONTEXT")
            classificationReasons.add("Contains currency symbol or monetary denomination")
        }
        if (hasWeakFinancialKeyword) {
            matchedFinancialSignals.add("FINANCIAL_KEYWORD")
        }
        if (isKnownFinancialApp) {
            matchedFinancialSignals.add("KNOWN_FINANCIAL_APP")
            classificationReasons.add("Supporting evidence: delivered by recognized financial package '$packageName'")
        }

        // 3. Determine Direction Hint
        val directionHint = when {
            hasDebitAction && !hasCreditAction -> DirectionHint.DEBIT_HINT
            hasCreditAction && !hasDebitAction -> DirectionHint.CREDIT_HINT
            else -> DirectionHint.UNKNOWN
        }

        // 4. Determine Financial Relevance
        val relevance: FinancialRelevance
        if (noiseResult.isNoise) {
            // Overridden by noise context (OTP, Promotional, Loan, etc.)
            relevance = FinancialRelevance.NON_FINANCIAL
            classificationReasons.add("Classified as NON_FINANCIAL due to primary noise signal: ${noiseResult.primaryCategory}")
        } else {
            val hasCompletedAction = hasDebitAction || hasCreditAction || hasTransactionSuccess

            when {
                // Clear financial transaction: completed action with currency, OR confirmed transaction success
                (hasCompletedAction && hasCurrencyContext) || hasTransactionSuccess -> {
                    relevance = FinancialRelevance.FINANCIAL
                    classificationReasons.add("Classified as FINANCIAL: verified completed transaction action with financial context")
                }
                // Completed action without explicit currency (e.g. "Money received", "Paid to Ravi")
                hasCompletedAction && (hasDebitAction || hasCreditAction) -> {
                    relevance = FinancialRelevance.FINANCIAL
                    classificationReasons.add("Classified as FINANCIAL: completed transaction action verb present")
                }
                // Ambiguous / Weak signals (e.g. "Transaction update", "Your payment", "₹500", "A new notification from your bank")
                hasWeakFinancialKeyword || hasCurrencyContext || isKnownFinancialApp -> {
                    relevance = FinancialRelevance.UNCERTAIN
                    classificationReasons.add("Classified as UNCERTAIN: isolated financial context or keyword without completed transaction action")
                }
                // No financial signals
                else -> {
                    relevance = FinancialRelevance.NON_FINANCIAL
                    classificationReasons.add("Classified as NON_FINANCIAL: no financial signals detected")
                }
            }
        }

        return NotificationClassificationResult(
            normalized = notification,
            financialRelevance = relevance,
            isNoise = noiseResult.isNoise,
            noiseCategory = noiseResult.primaryCategory,
            directionHint = if (relevance == FinancialRelevance.FINANCIAL) directionHint else DirectionHint.UNKNOWN,
            matchedFinancialSignals = matchedFinancialSignals,
            matchedNoiseSignals = noiseResult.matchedSignals,
            classificationReasons = classificationReasons,
            noiseReasons = noiseResult.reasons
        )
    }
}
