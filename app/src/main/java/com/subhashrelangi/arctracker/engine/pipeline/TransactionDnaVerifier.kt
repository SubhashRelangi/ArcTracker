package com.subhashrelangi.arctracker.engine.pipeline

import kotlin.math.max
import kotlin.math.min

/**
 * Pure, deterministic verification bridge (Layer 3.5) that inspects physical banking anchors
 * to evaluate whether a message possesses legitimate financial transaction DNA.
 *
 * Runs between Layer 3 (Noise & Event Gate) and Layer 4 (Structured Extraction).
 * A message must meet the confidence limit before proceeding to structured extraction.
 */
object TransactionDnaVerifier {

    // 1. Financial Instrument Patterns (Account suffix, Card suffix, Digital Wallet, UPI VPA, Bank issuer)
    private val ACCOUNT_PATTERNS = listOf(
        Regex("""\b(?:a/c|acct|acc|account)\s*(?:no\.?|num)?\s*[:.\-]?\s*(?:ending\s+(?:in\s+)?)?([x*]*\d{3,6})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:card|credit card|debit card)\s*(?:ending\s+(?:in\s+)?)?([x*]*\d{3,6})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:using\s+card\s+([x*]*\d{3,6}))\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:from\s+([x*]*\d{3,6}))\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:PhonePe\s+Wallet|Paytm\s+Wallet|Amazon\s+Pay\s+Balance|Mobikwik\s+Wallet|wallet\s+balance)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:vpa|upi\s+id)\s*[:.\-]?\s*([a-zA-Z0-9._-]+@[a-zA-Z0-9]+)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b[a-zA-Z0-9._-]+@(okaxis|okhdfcbank|okicici|oksbi|paytm|ybl|ibl|axl|upi)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:debited\s+from\s+(?:your\s+)?(?:account|a/c)|credited\s+to\s+(?:your\s+)?(?:account|a/c))\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:from\s+(?:your\s+)?(?:account|a/c)|to\s+(?:your\s+)?(?:account|a/c))\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:HDFC\s+Bank|State\s+Bank\s+of\s+India|SBI|ICICI\s+Bank|Axis\s+Bank|Kotak\s+Bank|Punjab\s+National\s+Bank|PNB|Bank\s+of\s+Baroda|BOB|Canara\s+Bank|Union\s+Bank|IndusInd\s+Bank|IDFC\s+FIRST\s+Bank|Federal\s+Bank|RBL\s+Bank)\b""", RegexOption.IGNORE_CASE)
    )

    // 2. Audit Trail Patterns (RRN, UTR, Txn ID, Ref No, Auth Code)
    private val AUDIT_PATTERNS = listOf(
        Regex("""\b(?:upi\s*ref(?:\s*no\.?)?|rrn|ref\s*no\.?)\s*[:.\-]?\s*(\d{6,16})\b""", RegexOption.IGNORE_CASE),
        Regex("""\bUPI/(?:CR|DR)/(\d{6,16})""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:utr(?:\s*no\.?)?|txn\s*(?:id|ref))\s*[:.\-]?\s*([a-zA-Z0-9]{6,25})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:auth\s*code|approval\s*code)\s*[:.\-]?\s*([a-zA-Z0-9]{6,12})\b""", RegexOption.IGNORE_CASE)
    )

    // 3. Post-Transaction Ledger Balance Patterns (Mandatory RBI disclosure for real transactions)
    private val BALANCE_PATTERNS = listOf(
        Regex("""\b(?:avl\s*bal|avail\s*bal|available\s*balance|clear\s*bal|closing\s*bal|bal\s*is|balance\s*is)\s*[:.\-]?\s*(?:rs\.?|inr|₹)?\s*[\d,]+(?:\.\d{1,2})?\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:total\s*bal|updated\s*bal|remaining\s*bal)\s*[:.\-]?\s*(?:rs\.?|inr|₹)?\s*[\d,]+(?:\.\d{1,2})?\b""", RegexOption.IGNORE_CASE),
        Regex("""\bbal\s*[:=\-]\s*(?:rs\.?|inr|₹)?\s*[\d,]+""", RegexOption.IGNORE_CASE)
    )

    // 4. Directional Counterparty ("to <Merchant>", "at <Store>", "from <Person>")
    private val COUNTERPARTY_PATTERNS = listOf(
        Regex("""\b(?:paid|sent|transferred|transfer|payment)(?:\s+(?:(?:rs\.?|inr|₹)\s*)?\d[\d,]*(?:\.\d{1,2})?)?\s+(?:to|at|towards)\s+([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,35})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:payment|purchase|spent|paid|recharge)\s+at\s+([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,35})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:payment|recharge)\s+(?:of\s+.*?\s+)?(?:to|at|for)\s+([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,35})\s+(?:was\s+|is\s+|has been\s+)?(?:successful|completed)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:spent\s+at|purchase\s+(?:at|on))\s+([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,35})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:received|refund\s+of)\s+from\s+([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,35})\b""", RegexOption.IGNORE_CASE),
        Regex("""\btransferred\s+to\s+([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,35})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:paid\s+to|sent\s+to|transferred\s+to|payment\s+to|transfer\s+to)\b""", RegexOption.IGNORE_CASE)
    )

    // 5. Genuine Completed / Terminal Action Verb & Status Patterns
    private val ACTION_PATTERNS = listOf(
        Regex("""\b(?:is\s+|was\s+|has been\s+)?debited\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:is\s+|was\s+|has been\s+)?credited\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(paid|spent|sent|transferred|withdrawn|deposited|refund|refunded|received)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(payment|transaction|recharge|transfer|txn)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(failed|declined|unsuccessful|pending|processing|in progress)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:payment|transaction|recharge|transfer)\s+(?:of\s+.*?\s+)?(?:is\s+|was\s+|has been\s+)?(?:successful|completed|processed|failed|pending)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:payment\s+successful|transaction\s+successful|order\s+payment\s+successful|payment\s+completed)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:reversal\s+of\s+payment|payment\s+reversed|reversed)\b""", RegexOption.IGNORE_CASE)
    )

    // 6. Currency / Monetary presence pattern
    private val CURRENCY_AMOUNT_PATTERN = Regex(
        """(?:₹|rs\.?|inr|rupees?)\s*[\d,]+(?:\.\d{1,2})?|[\d,]+(?:\.\d{1,2})?\s*(?:₹|rs\.?|inr|rupees?)""",
        RegexOption.IGNORE_CASE
    )

    // 7. Promotional Language Patterns (Trigger penalty if no explicit account/card anchor is present)
    private val PROMOTIONAL_DISQUALIFIERS = listOf(
        Regex("""\b(?:win\s+(?:rs\.?|inr|₹)?\s*[\d,]+\s*(?:prize\s+money|cash\s+prize|jackpot)|prize\s+money|cash\s+prize|bumper\s+prize|lucky\s+draw)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:when\s+paid\s+(?:using|with|via)|if\s+paid\s+(?:using|with|via)|on\s+orders?\s+paid\s+(?:using|with|via)|orders?\s+paid\s+with)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:start\s+(?:a\s+)?sip|sip\s+(?:for|of)?\s*(?:rs\.?|inr|₹)?\s*[\d,]+|invest\s+in\s+gold|pre-approved\s+loan)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:sent\s+with\s+love|discount\s+on\s+your\s+order|flat\s+(?:rs\.?|inr|₹)?\s*[\d,]+\s*off)\b""", RegexOption.IGNORE_CASE)
    )

    /**
     * Verifies the presence of physical banking anchors in the text.
     *
     * @param text The normalized combined notification text.
     * @return [TransactionConfidenceProfile] detailing the score, matched anchors, and extraction verdict.
     */
    fun verify(text: String): TransactionConfidenceProfile {
        if (text.isBlank()) {
            return TransactionConfidenceProfile(
                score = 0,
                verdict = VerificationVerdict.UNVERIFIED_PROMOTIONAL,
                diagnosticNotes = listOf("Empty content")
            )
        }

        val matchedAnchors = mutableSetOf<AnchorType>()
        val anchorDetails = mutableMapOf<AnchorType, String>()
        val notes = mutableListOf<String>()
        var score = 0

        // 1. Check Instrument Anchor (Weight: 35 pts)
        for (pattern in ACCOUNT_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                matchedAnchors.add(AnchorType.ACCOUNT_INSTRUMENT)
                anchorDetails[AnchorType.ACCOUNT_INSTRUMENT] = match.value
                score += 35
                notes.add("Found Instrument Anchor: '${match.value}'")
                break
            }
        }

        // 2. Check Audit Trail (Weight: 30 pts)
        for (pattern in AUDIT_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                matchedAnchors.add(AnchorType.AUDIT_TRAIL)
                anchorDetails[AnchorType.AUDIT_TRAIL] = match.value
                score += 30
                notes.add("Found Audit RRN/UTR: '${match.value}'")
                break
            }
        }

        // 3. Check Ledger Balance (Weight: 20 pts)
        for (pattern in BALANCE_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                matchedAnchors.add(AnchorType.LEDGER_BALANCE)
                anchorDetails[AnchorType.LEDGER_BALANCE] = match.value
                score += 20
                notes.add("Found Ledger Balance: '${match.value}'")
                break
            }
        }

        // 4. Check Directional Counterparty (Weight: 20 pts)
        for (pattern in COUNTERPARTY_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                matchedAnchors.add(AnchorType.DIRECTIONAL_PARTY)
                anchorDetails[AnchorType.DIRECTIONAL_PARTY] = match.value
                score += 20
                notes.add("Found Counterparty: '${match.value}'")
                break
            }
        }

        // 5. Check Action / Status Verb (Weight: 30 pts)
        for (pattern in ACTION_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                matchedAnchors.add(AnchorType.COMPLETED_ACTION_VERB)
                anchorDetails[AnchorType.COMPLETED_ACTION_VERB] = match.value
                score += 30
                notes.add("Found Action Verb/Status: '${match.value}'")
                break
            }
        }

        // 6. Check Currency / Monetary context (Weight: 20 pts)
        val currencyMatch = CURRENCY_AMOUNT_PATTERN.find(text)
        if (currencyMatch != null) {
            score += 20
            notes.add("Found Monetary Context: '${currencyMatch.value}'")
        }

        // 7. Apply Promotional Penalty if marketing language present without explicit instrument
        val hasPromotionalLanguage = PROMOTIONAL_DISQUALIFIERS.any { it.containsMatchIn(text) }
        val hasExplicitInstrument = matchedAnchors.contains(AnchorType.ACCOUNT_INSTRUMENT)
        if (hasPromotionalLanguage && !hasExplicitInstrument) {
            score -= 50
            notes.add("Applied promotional penalty (-50 pts): promotional language without explicit bank instrument")
        }

        val finalScore = max(0, min(100, score))
        val verdict = when {
            finalScore >= 50 -> VerificationVerdict.CONFIRMED_FINANCIAL
            finalScore >= TransactionConfidenceProfile.MIN_CONFIDENCE_THRESHOLD -> VerificationVerdict.PROBABLE_FINANCIAL
            else -> VerificationVerdict.UNVERIFIED_PROMOTIONAL
        }

        return TransactionConfidenceProfile(
            score = finalScore,
            verdict = verdict,
            matchedAnchors = matchedAnchors,
            anchorDetails = anchorDetails,
            diagnosticNotes = notes
        )
    }
}
