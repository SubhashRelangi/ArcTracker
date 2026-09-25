package com.example.arctracker.service

import com.example.arctracker.utils.IgnoreRule
import kotlin.math.max
import kotlin.math.min

/**
 * Pure, deterministic extractor that produces [StructuredTransactionCandidate] from a classified
 * [NormalizedNotification] (Step 5).
 *
 * Extracts:
 * - Primary transaction amount (differentiating from balances, fees, cashbacks, limits, and taxes)
 * - Merchant / counterparty
 * - UPI ID
 * - Reference numbers (UTR, RRN, UPI Ref, Txn ID)
 * - Account / Card suffix
 * - Transaction status (SUCCESS, FAILED, PENDING, REVERSED, REFUNDED, DECLINED)
 * - Direction (DEBIT, CREDIT, UNKNOWN)
 * - Field-level evidence tracking
 *
 * Does NOT:
 * - Insert into Room database
 * - Create Pending Expenses
 * - Perform confidence scoring or auto-acceptance (Step 6)
 * - Correlate or deduplicate (Step 7)
 */
object StructuredTransactionExtractor {

    // Regex for recognizing monetary values with explicit currency prefix or suffix
    private val CURRENCY_PREFIX_AMOUNT_PATTERN = Regex(
        """(?i)(₹|rs\.?|inr|rupees?)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\b"""
    )

    private val CURRENCY_SUFFIX_AMOUNT_PATTERN = Regex(
        """(?i)\b([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s*(₹|rs\.?|inr|rupees?)\b"""
    )

    // Numbers preceded directly by action verbs (without currency symbols)
    private val ACTION_PRECEDED_AMOUNT_PATTERN = Regex(
        """(?i)\b(?:debited|credited|paid|spent|sent|transferred|withdrawn|deposited|refunded|payment of|txn of|transfer of)\s*(?:by|of|for|is|:)?\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\b"""
    )

    // Context detection patterns for amount categorization
    private val BALANCE_CONTEXT_PATTERN = Regex(
        """(?i)\b(available balance|avl bal|available bal|current balance|account balance|closing balance|ledger balance|remaining balance|remaining amount|clear balance|acc balance|a/c balance|bal\s*[:is\-=]|balance\s*[:is\-=]?)\b"""
    )

    private val LIMIT_CONTEXT_PATTERN = Regex(
        """(?i)\b(transaction limit|daily limit|transfer limit|credit limit|limit is|limit of)\b"""
    )

    private val FEE_CONTEXT_PATTERN = Regex(
        """(?i)\b(transaction fee|service charge|convenience fee|surcharge|fee of|fee|charges?)\b"""
    )

    private val CASHBACK_CONTEXT_PATTERN = Regex(
        """(?i)\b(cashback|reward points?|reward|discount|saved|bonus)\b"""
    )

    private val TAX_CONTEXT_PATTERN = Regex(
        """(?i)\b(gst|cgst|sgst|tax|vat)\b"""
    )

    private val TOTAL_CONTEXT_PATTERN = Regex(
        """(?i)\b(total\s*[:is\-=]?|total amount|total payment|grand total)\b"""
    )

    private val TRANSACTION_ACTION_CONTEXT_PATTERN = Regex(
        """(?i)\b(paid|debited|spent|sent|transferred|credited|received|withdrawn|deposited|refunded|deducted|payment of|txn of|transfer of|purchase|order payment|payment successful|transaction successful)\b"""
    )

    // Status patterns
    private val STATUS_FAILED_PATTERN = Regex(
        """(?i)\b(payment failed|transaction failed|failed|declined|unsuccessful)\b"""
    )

    private val STATUS_PENDING_PATTERN = Regex(
        """(?i)\b(payment pending|transaction pending|pending|in progress|processing)\b"""
    )

    private val STATUS_REFUNDED_PATTERN = Regex(
        """(?i)\b(refund processed|refund received|refund of|refunded)\b"""
    )

    private val STATUS_REVERSED_PATTERN = Regex(
        """(?i)\b(payment reversed|transaction reversed|reversed|reversal)\b"""
    )

    private val STATUS_SUCCESS_PATTERN = Regex(
        """(?i)\b(payment successful|transaction successful|transfer successful|order payment successful|payment completed|successful|completed)\b"""
    )

    // Merchant / Payee patterns (allows optional intervening amount like "Paid ₹500 to Amazon")
    private val OUTGOING_MERCHANT_PATTERNS = listOf(
        Regex("""(?i)\b(?:paid|sent|transferred|transfer|payment)(?:\s+(?:(?:rs\.?|inr|₹)\s*)?\d[\d,]*(?:\.\d{1,2})?)?\s+(?:to|at)\s+([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,50})"""),
        Regex("""(?i)\b(?:purchase at|purchase on)\s+([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,50})""")
    )

    private val INCOMING_MERCHANT_PATTERNS = listOf(
        Regex("""(?i)\b(?:received|money received|transfer|credited)(?:\s+(?:(?:rs\.?|inr|₹)\s*)?\d[\d,]*(?:\.\d{1,2})?)?\s+from\s+([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,50})""")
    )

    private val REVERSAL_MERCHANT_PATTERN = Regex(
        """(?i)\b(?:reversal of payment to)\s+([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,50})"""
    )

    // UPI ID pattern: name@bank (exclude web email domains with dots like .com, .in)
    private val UPI_ID_PATTERN = Regex(
        """\b([a-zA-Z0-9.\-_]{2,50}@[a-zA-Z]{2,30})\b"""
    )

    // Reference patterns
    private val UPI_REF_PATTERN = Regex(
        """(?i)\b(?:upi\s*ref(?:erence)?(?:\s*no\.?)?|upi\s*txn(?:\s*id)?)\s*[:\-#]?\s*([a-zA-Z0-9]{6,30})\b"""
    )

    private val UTR_PATTERN = Regex(
        """(?i)\bUTR\s*[:\-#]?\s*([a-zA-Z0-9]{9,30})\b"""
    )

    private val RRN_PATTERN = Regex(
        """(?i)\bRRN\s*[:\-#]?\s*([0-9]{9,16})\b"""
    )

    private val GENERAL_REF_PATTERN = Regex(
        """(?i)\b(?:ref\s*(?:no\.?|num(?:ber)?\.?)?|reference\s*(?:no\.?|num(?:ber)?\.?)?|txn\s*(?:id)?|transaction\s*(?:id)?)\s*[:\-#]?\s*([a-zA-Z0-9]{4,30})\b"""
    )

    // Account / Card suffix patterns
    private val ACCOUNT_SUFFIX_PATTERN = Regex(
        """(?i)\b(?:a/c|account|acct)\s*(?:no\.?)?\s*(?:ending\s*(?:with|in)?)?\s*(?:[xX*]*(\d{3,6}))\b"""
    )

    private val CARD_SUFFIX_PATTERN = Regex(
        """(?i)\b(?:card)\s*(?:no\.?)?\s*(?:ending\s*(?:with|in)?)?\s*(?:[xX*]*(\d{3,6}))\b"""
    )

    // Boundary words that terminate a merchant phrase
    private val MERCHANT_BOUNDARY_PATTERN = Regex(
        """(?i)\b(on|using|via|ref|txn|avl|balance|a/c|account|card|upi ref|utr|rrn)\b"""
    )

    // Phrases that look like merchants but are accounts / methods
    private val INVALID_MERCHANT_PREFIX_PATTERN = Regex(
        """(?i)^(account|a/c|acct|your account|card|vpa|upi id|upi)\b"""
    )

    /**
     * Extracts structured transaction candidate from a [NotificationClassificationResult].
     * Returns null if the notification is classified as noise or non-financial.
     */
    fun extract(
        classification: NotificationClassificationResult
    ): StructuredTransactionCandidate? {
        if (classification.isNoise || classification.financialRelevance == FinancialRelevance.NON_FINANCIAL) {
            return null
        }
        return extractInternal(classification.normalized, classification)
    }

    /**
     * Convenience method to classify and extract in a single call.
     */
    fun extract(
        notification: NormalizedNotification,
        ignoreRules: List<IgnoreRule> = emptyList()
    ): StructuredTransactionCandidate? {
        val classification = FinancialClassifier.classify(notification, ignoreRules)
        return extract(classification)
    }

    /**
     * Direct extraction without classification gate (useful for testing extraction logic directly).
     */
    fun extractDirect(
        notification: NormalizedNotification,
        classification: NotificationClassificationResult? = null
    ): StructuredTransactionCandidate {
        return extractInternal(notification, classification)
    }

    private fun extractInternal(
        notification: NormalizedNotification,
        classification: NotificationClassificationResult?
    ): StructuredTransactionCandidate {
        val text = notification.normalizedCombinedText
        val evidenceMap = mutableMapOf<String, FieldEvidence>()
        val secondaryAmounts = mutableListOf<SecondaryAmount>()

        // 1. Amount Extraction & Disambiguation
        val extractedAmount = extractAmount(text, secondaryAmounts, evidenceMap)

        // Currency
        val currency = if (extractedAmount != null || secondaryAmounts.isNotEmpty()) {
            if (text.contains("₹") || text.contains("rs", ignoreCase = true) || text.contains("inr", ignoreCase = true)) {
                "INR"
            } else {
                null
            }
        } else {
            null
        }

        // 2. Status Extraction
        val status = extractStatus(text, evidenceMap)

        // 3. Direction Extraction
        val direction = extractDirection(text, classification, status, evidenceMap)

        // 4. UPI ID Extraction
        val upiId = extractUpiId(text, evidenceMap)

        // 5. Merchant / Counterparty Extraction
        val (merchant, counterparty) = extractMerchantAndCounterparty(text, upiId, evidenceMap)

        // 6. Reference IDs (UTR, RRN, UPI Ref, Gen Ref)
        val (refId, utr, rrn, upiTxnId) = extractReferences(text, evidenceMap)

        // 7. Account / Card Suffix
        val accountSuffix = extractAccountSuffix(text, evidenceMap)
        val cardSuffix = extractCardSuffix(text, evidenceMap)

        return StructuredTransactionCandidate(
            sourceNotificationKey = notification.notificationKey,
            packageName = notification.packageName,
            postTime = notification.postTime,
            amount = extractedAmount,
            currency = currency,
            merchant = merchant,
            counterparty = counterparty,
            direction = direction,
            status = status,
            referenceId = refId,
            utr = utr,
            rrn = rrn,
            upiTransactionId = upiTxnId,
            upiId = upiId,
            accountSuffix = accountSuffix,
            cardSuffix = cardSuffix,
            secondaryAmounts = secondaryAmounts,
            evidence = evidenceMap,
            isUpdate = notification.isUpdate,
            groupKey = notification.groupKey,
            isGroup = notification.isGroup,
            isGroupSummary = notification.isGroupSummary,
            rawContent = notification.normalizedCombinedText
        )
    }

    private data class ParsedAmountCandidate(
        val amount: Double,
        val rawSnippet: String,
        val startIndex: Int,
        val endIndex: Int,
        val clauseSnippet: String,
        var isBalance: Boolean = false,
        var isLimit: Boolean = false,
        var isFee: Boolean = false,
        var isCashback: Boolean = false,
        var isTax: Boolean = false,
        var isTotal: Boolean = false,
        var hasActionVerb: Boolean = false,
        var hasCurrencyMarker: Boolean = false
    )

    private fun extractAmount(
        text: String,
        secondaryAmounts: MutableList<SecondaryAmount>,
        evidenceMap: MutableMap<String, FieldEvidence>
    ): Double? {
        val candidates = mutableListOf<ParsedAmountCandidate>()

        // Helper to extract clause containing a range (split by periods and newlines)
        fun findClause(start: Int, end: Int): String {
            // Find closest sentence boundary before start
            var clauseStart = 0
            for (i in (start - 1) downTo 0) {
                if (text[i] == '\n' || text[i] == '.' || text[i] == ';') {
                    clauseStart = i + 1
                    break
                }
            }
            // Find closest sentence boundary after end
            var clauseEnd = text.length
            for (i in end until text.length) {
                if (text[i] == '\n' || text[i] == '.' || text[i] == ';') {
                    clauseEnd = i
                    break
                }
            }
            return text.substring(clauseStart, clauseEnd).trim()
        }

        // 1. Check Currency-Prefixed amounts (e.g. ₹500, Rs. 1,200)
        for (match in CURRENCY_PREFIX_AMOUNT_PATTERN.findAll(text)) {
            val numStr = match.groups[2]?.value ?: continue
            val clean = numStr.replace(",", "")
            val parsed = clean.toDoubleOrNull() ?: continue
            val clause = findClause(match.range.first, match.range.last)

            candidates.add(
                ParsedAmountCandidate(
                    amount = parsed,
                    rawSnippet = match.value.trim(),
                    startIndex = match.range.first,
                    endIndex = match.range.last,
                    clauseSnippet = clause,
                    hasCurrencyMarker = true
                )
            )
        }

        // 2. Check Currency-Suffixed amounts (e.g. 500 INR, 500 Rs)
        for (match in CURRENCY_SUFFIX_AMOUNT_PATTERN.findAll(text)) {
            // Avoid duplicate range
            if (candidates.any { it.startIndex == match.range.first }) continue
            val numStr = match.groups[1]?.value ?: continue
            val clean = numStr.replace(",", "")
            val parsed = clean.toDoubleOrNull() ?: continue
            val clause = findClause(match.range.first, match.range.last)

            candidates.add(
                ParsedAmountCandidate(
                    amount = parsed,
                    rawSnippet = match.value.trim(),
                    startIndex = match.range.first,
                    endIndex = match.range.last,
                    clauseSnippet = clause,
                    hasCurrencyMarker = true
                )
            )
        }

        // 3. Check Action-Preceded amounts without currency (e.g. "debited by 250")
        for (match in ACTION_PRECEDED_AMOUNT_PATTERN.findAll(text)) {
            val numStr = match.groups[1]?.value ?: continue
            val clean = numStr.replace(",", "")
            val parsed = clean.toDoubleOrNull() ?: continue

            // Ensure not inside already matched currency amount or account suffix
            val numStart = match.value.lastIndexOf(numStr) + match.range.first
            if (candidates.any { numStart >= it.startIndex && numStart <= it.endIndex }) continue

            // Ensure not preceded by account/card/ref markers
            val preContext = text.substring(max(0, match.range.first - 15), match.range.first)
            if (preContext.contains("a/c", ignoreCase = true) ||
                preContext.contains("account", ignoreCase = true) ||
                preContext.contains("card", ignoreCase = true) ||
                preContext.contains("ref", ignoreCase = true) ||
                preContext.contains("xx", ignoreCase = true)) {
                continue
            }

            val clause = findClause(match.range.first, match.range.last)
            candidates.add(
                ParsedAmountCandidate(
                    amount = parsed,
                    rawSnippet = match.value.trim(),
                    startIndex = match.range.first,
                    endIndex = match.range.last,
                    clauseSnippet = clause,
                    hasCurrencyMarker = false
                )
            )
        }

        // Categorize each candidate using its isolated clause and immediate local context
        for (candidate in candidates) {
            val clause = candidate.clauseSnippet

            candidate.isBalance = BALANCE_CONTEXT_PATTERN.containsMatchIn(clause)
            candidate.isLimit = LIMIT_CONTEXT_PATTERN.containsMatchIn(clause)
            candidate.isFee = FEE_CONTEXT_PATTERN.containsMatchIn(clause)
            candidate.isCashback = CASHBACK_CONTEXT_PATTERN.containsMatchIn(clause)
            candidate.isTax = TAX_CONTEXT_PATTERN.containsMatchIn(clause)
            candidate.isTotal = TOTAL_CONTEXT_PATTERN.containsMatchIn(clause)

            candidate.hasActionVerb = TRANSACTION_ACTION_CONTEXT_PATTERN.containsMatchIn(clause)

            // If an action verb exists in the clause and it's not explicitly labeled balance/fee/tax/cashback/limit
            if (candidate.hasActionVerb && !candidate.isBalance && !candidate.isFee && !candidate.isTax && !candidate.isCashback && !candidate.isLimit) {
                // e.g. "₹500 paid" in "₹500 paid. GST ₹90. Total ₹590."
            }
        }

        // Record secondary amounts
        for (c in candidates) {
            when {
                c.isBalance -> secondaryAmounts.add(
                    SecondaryAmount(SecondaryAmountType.BALANCE, c.amount, c.rawSnippet, c.clauseSnippet)
                )
                c.isLimit -> secondaryAmounts.add(
                    SecondaryAmount(SecondaryAmountType.LIMIT, c.amount, c.rawSnippet, c.clauseSnippet)
                )
                c.isFee -> secondaryAmounts.add(
                    SecondaryAmount(SecondaryAmountType.FEE, c.amount, c.rawSnippet, c.clauseSnippet)
                )
                c.isCashback -> secondaryAmounts.add(
                    SecondaryAmount(SecondaryAmountType.CASHBACK, c.amount, c.rawSnippet, c.clauseSnippet)
                )
                c.isTax -> secondaryAmounts.add(
                    SecondaryAmount(SecondaryAmountType.TAX_OR_GST, c.amount, c.rawSnippet, c.clauseSnippet)
                )
                c.isTotal -> secondaryAmounts.add(
                    SecondaryAmount(SecondaryAmountType.TOTAL, c.amount, c.rawSnippet, c.clauseSnippet)
                )
            }
        }

        // Filter valid transaction candidates (must not be balance, limit, fee, cashback, or tax)
        val validCandidates = candidates.filter {
            !it.isBalance && !it.isLimit && !it.isFee && !it.isCashback && !it.isTax
        }

        if (validCandidates.isEmpty()) {
            return null
        }

        // Deterministic Ranking:
        // 1. Direct transaction action verb takes highest priority (e.g. "₹500 paid" > "Total ₹590")
        // 2. Total amount candidate (if no direct action verb exists)
        // 3. Generic currency candidate
        val selected = validCandidates.firstOrNull { it.hasActionVerb && !it.isTotal }
            ?: validCandidates.firstOrNull { it.hasActionVerb }
            ?: validCandidates.firstOrNull { it.isTotal }
            ?: validCandidates.firstOrNull { it.hasCurrencyMarker }
            ?: validCandidates.firstOrNull()

        if (selected != null) {
            evidenceMap["amount"] = FieldEvidence(
                fieldName = "amount",
                extractedValue = selected.amount.toString(),
                sourceSnippet = selected.clauseSnippet,
                ruleOrPattern = if (selected.hasActionVerb) "ACTION_VERB_ASSOCIATION" else "CURRENCY_MARKER"
            )
            return selected.amount
        }

        return null
    }

    private fun extractStatus(
        text: String,
        evidenceMap: MutableMap<String, FieldEvidence>
    ): TransactionStatus? {
        val status = when {
            STATUS_FAILED_PATTERN.containsMatchIn(text) -> TransactionStatus.FAILED
            STATUS_PENDING_PATTERN.containsMatchIn(text) -> TransactionStatus.PENDING
            STATUS_REVERSED_PATTERN.containsMatchIn(text) -> TransactionStatus.REVERSED
            STATUS_REFUNDED_PATTERN.containsMatchIn(text) -> TransactionStatus.REFUNDED
            STATUS_SUCCESS_PATTERN.containsMatchIn(text) -> TransactionStatus.SUCCESS
            else -> null
        }

        if (status != null) {
            val snippet = when (status) {
                TransactionStatus.FAILED -> STATUS_FAILED_PATTERN.find(text)?.value ?: ""
                TransactionStatus.PENDING -> STATUS_PENDING_PATTERN.find(text)?.value ?: ""
                TransactionStatus.REVERSED -> STATUS_REVERSED_PATTERN.find(text)?.value ?: ""
                TransactionStatus.REFUNDED -> STATUS_REFUNDED_PATTERN.find(text)?.value ?: ""
                TransactionStatus.SUCCESS -> STATUS_SUCCESS_PATTERN.find(text)?.value ?: ""
                else -> ""
            }
            evidenceMap["status"] = FieldEvidence(
                fieldName = "status",
                extractedValue = status.name,
                sourceSnippet = snippet,
                ruleOrPattern = "EXPLICIT_STATUS_PATTERN"
            )
        }

        return status
    }

    private fun extractDirection(
        text: String,
        classification: NotificationClassificationResult?,
        status: TransactionStatus?,
        evidenceMap: MutableMap<String, FieldEvidence>
    ): TransactionDirection {
        // Reversals are ambiguous regarding overall ledger direction unless explicitly clarified
        if (status == TransactionStatus.REVERSED || text.contains("reversal", ignoreCase = true)) {
            evidenceMap["direction"] = FieldEvidence(
                fieldName = "direction",
                extractedValue = TransactionDirection.UNKNOWN.name,
                sourceSnippet = "Contains reversal keyword; direction left unresolved",
                ruleOrPattern = "REVERSAL_AMBIGUITY"
            )
            return TransactionDirection.UNKNOWN
        }

        val hasDebitExplicit = Regex("""(?i)\b(paid to|sent to|transferred to|debited from|spent at|purchase at|paid at|debited by|debited for|paid using|debited)\b""").containsMatchIn(text)
        val hasCreditExplicit = Regex("""(?i)\b(received from|credited to|credited with|deposited|refund of .* received|cashback received|credited)\b""").containsMatchIn(text)

        // General debit and credit terms (including "credit card" vs "debited")
        val hasDebitWord = Regex("""(?i)\b(debited|debit|paid|spent|withdrawn)\b""").containsMatchIn(text)
        val hasCreditWord = Regex("""(?i)\b(credited|credit|deposited|received|refunded)\b""").containsMatchIn(text)

        // Ambiguous when both debit and credit words are present
        if (hasDebitWord && hasCreditWord) {
            evidenceMap["direction"] = FieldEvidence(
                fieldName = "direction",
                extractedValue = TransactionDirection.UNKNOWN.name,
                sourceSnippet = "Contains conflicting debit and credit signals",
                ruleOrPattern = "CONFLICTING_DIRECTION"
            )
            return TransactionDirection.UNKNOWN
        }

        if (hasDebitExplicit) {
            evidenceMap["direction"] = FieldEvidence(
                fieldName = "direction",
                extractedValue = TransactionDirection.DEBIT.name,
                sourceSnippet = "Explicit debit action found",
                ruleOrPattern = "EXPLICIT_DEBIT_PATTERN"
            )
            return TransactionDirection.DEBIT
        }

        if (hasCreditExplicit) {
            evidenceMap["direction"] = FieldEvidence(
                fieldName = "direction",
                extractedValue = TransactionDirection.CREDIT.name,
                sourceSnippet = "Explicit credit action found",
                ruleOrPattern = "EXPLICIT_CREDIT_PATTERN"
            )
            return TransactionDirection.CREDIT
        }

        // Fall back to Step 4 DirectionHint if available
        if (classification != null) {
            when (classification.directionHint) {
                DirectionHint.DEBIT_HINT -> {
                    evidenceMap["direction"] = FieldEvidence(
                        fieldName = "direction",
                        extractedValue = TransactionDirection.DEBIT.name,
                        sourceSnippet = "Inherited from Step 4 debit hint",
                        ruleOrPattern = "STEP4_DEBIT_HINT"
                    )
                    return TransactionDirection.DEBIT
                }
                DirectionHint.CREDIT_HINT -> {
                    evidenceMap["direction"] = FieldEvidence(
                        fieldName = "direction",
                        extractedValue = TransactionDirection.CREDIT.name,
                        sourceSnippet = "Inherited from Step 4 credit hint",
                        ruleOrPattern = "STEP4_CREDIT_HINT"
                    )
                    return TransactionDirection.CREDIT
                }
                DirectionHint.UNKNOWN -> {}
            }
        }

        return TransactionDirection.UNKNOWN
    }

    private fun extractUpiId(
        text: String,
        evidenceMap: MutableMap<String, FieldEvidence>
    ): String? {
        val matches = UPI_ID_PATTERN.findAll(text)
        for (match in matches) {
            val handle = match.value
            val domain = handle.substringAfter("@")
            // Must not contain a dot (avoid standard web emails like example@domain.com)
            if (!domain.contains(".")) {
                evidenceMap["upiId"] = FieldEvidence(
                    fieldName = "upiId",
                    extractedValue = handle,
                    sourceSnippet = match.value,
                    ruleOrPattern = "UPI_ID_PATTERN"
                )
                return handle
            }
        }
        return null
    }

    private fun extractMerchantAndCounterparty(
        text: String,
        upiId: String?,
        evidenceMap: MutableMap<String, FieldEvidence>
    ): Pair<String?, String?> {
        var rawCandidate: String? = null
        var matchedPatternName: String? = null
        var isCounterparty = false

        // 1. Check Reversal
        val reversalMatch = REVERSAL_MERCHANT_PATTERN.find(text)
        if (reversalMatch != null) {
            rawCandidate = reversalMatch.groups[1]?.value
            matchedPatternName = "REVERSAL_MERCHANT_PATTERN"
        }

        // 2. Check Incoming (Counterparty / Sender)
        if (rawCandidate == null) {
            for (pattern in INCOMING_MERCHANT_PATTERNS) {
                val match = pattern.find(text)
                if (match != null) {
                    rawCandidate = match.groups[1]?.value
                    matchedPatternName = "INCOMING_MERCHANT_PATTERN"
                    isCounterparty = true
                    break
                }
            }
        }

        // 3. Check Outgoing (Payee / Merchant)
        if (rawCandidate == null) {
            for (pattern in OUTGOING_MERCHANT_PATTERNS) {
                val match = pattern.find(text)
                if (match != null) {
                    rawCandidate = match.groups[1]?.value
                    matchedPatternName = "OUTGOING_MERCHANT_PATTERN"
                    break
                }
            }
        }

        if (rawCandidate == null) {
            return Pair(null, null)
        }

        // Clean candidate
        var cleaned = rawCandidate.trim()

        // Stop at sentence boundary (period followed by whitespace or end of string)
        val periodMatch = Regex("""\.(?:\s+|$)""").find(cleaned)
        if (periodMatch != null) {
            cleaned = cleaned.substring(0, periodMatch.range.first).trim()
        }

        // Stop before newline or boundary words (on, using, via, ref, avl, bal, etc.)
        if (cleaned.contains("\n")) {
            cleaned = cleaned.substringBefore("\n").trim()
        }

        val boundaryMatch = MERCHANT_BOUNDARY_PATTERN.find(cleaned)
        if (boundaryMatch != null && boundaryMatch.range.first > 0) {
            cleaned = cleaned.substring(0, boundaryMatch.range.first).trim()
        }

        // Remove trailing punctuation
        cleaned = cleaned.trimEnd('.', ',', ';', ':', '!', '-', '\'', '"')

        // If candidate matches UPI ID or starts with UPI ID, do NOT treat as merchant name
        if (cleaned.contains("@")) {
            return Pair(null, null)
        }

        // Reject if it starts with invalid prefix like "account", "card", "vpa"
        if (INVALID_MERCHANT_PREFIX_PATTERN.containsMatchIn(cleaned)) {
            return Pair(null, null)
        }

        if (cleaned.length < 2) {
            return Pair(null, null)
        }

        val fieldKey = if (isCounterparty) "counterparty" else "merchant"
        evidenceMap[fieldKey] = FieldEvidence(
            fieldName = fieldKey,
            extractedValue = cleaned,
            sourceSnippet = "$rawCandidate -> $cleaned",
            ruleOrPattern = matchedPatternName
        )

        return if (isCounterparty) {
            Pair(cleaned, cleaned)
        } else {
            Pair(cleaned, null)
        }
    }

    private data class ExtractedReferences(
        val refId: String?,
        val utr: String?,
        val rrn: String?,
        val upiTxnId: String?
    )

    private fun extractReferences(
        text: String,
        evidenceMap: MutableMap<String, FieldEvidence>
    ): ExtractedReferences {
        var upiRef: String? = null
        var utr: String? = null
        var rrn: String? = null
        var generalRef: String? = null

        val upiMatch = UPI_REF_PATTERN.find(text)
        if (upiMatch != null) {
            upiRef = upiMatch.groups[1]?.value?.trim()
            if (upiRef != null) {
                evidenceMap["upiTransactionId"] = FieldEvidence(
                    fieldName = "upiTransactionId",
                    extractedValue = upiRef,
                    sourceSnippet = upiMatch.value,
                    ruleOrPattern = "UPI_REF_PATTERN"
                )
            }
        }

        val utrMatch = UTR_PATTERN.find(text)
        if (utrMatch != null) {
            utr = utrMatch.groups[1]?.value?.trim()
            if (utr != null) {
                evidenceMap["utr"] = FieldEvidence(
                    fieldName = "utr",
                    extractedValue = utr,
                    sourceSnippet = utrMatch.value,
                    ruleOrPattern = "UTR_PATTERN"
                )
            }
        }

        val rrnMatch = RRN_PATTERN.find(text)
        if (rrnMatch != null) {
            rrn = rrnMatch.groups[1]?.value?.trim()
            if (rrn != null) {
                evidenceMap["rrn"] = FieldEvidence(
                    fieldName = "rrn",
                    extractedValue = rrn,
                    sourceSnippet = rrnMatch.value,
                    ruleOrPattern = "RRN_PATTERN"
                )
            }
        }

        val genMatch = GENERAL_REF_PATTERN.find(text)
        if (genMatch != null) {
            val candidate = genMatch.groups[1]?.value?.trim()
            // Avoid capturing word "No" or trivial noise
            if (candidate != null && !candidate.equals("no", ignoreCase = true) && candidate.length >= 3) {
                generalRef = candidate
                evidenceMap["referenceId"] = FieldEvidence(
                    fieldName = "referenceId",
                    extractedValue = candidate,
                    sourceSnippet = genMatch.value,
                    ruleOrPattern = "GENERAL_REF_PATTERN"
                )
            }
        }

        // If referenceId is null, but upiRef, utr, or rrn was extracted, populate referenceId as primary reference
        val primaryRef = generalRef ?: upiRef ?: utr ?: rrn

        return ExtractedReferences(
            refId = primaryRef,
            utr = utr,
            rrn = rrn,
            upiTxnId = upiRef
        )
    }

    private fun extractAccountSuffix(
        text: String,
        evidenceMap: MutableMap<String, FieldEvidence>
    ): String? {
        val match = ACCOUNT_SUFFIX_PATTERN.find(text) ?: return null
        val suffix = match.groups[1]?.value ?: return null
        evidenceMap["accountSuffix"] = FieldEvidence(
            fieldName = "accountSuffix",
            extractedValue = suffix,
            sourceSnippet = match.value,
            ruleOrPattern = "ACCOUNT_SUFFIX_PATTERN"
        )
        return suffix
    }

    private fun extractCardSuffix(
        text: String,
        evidenceMap: MutableMap<String, FieldEvidence>
    ): String? {
        val match = CARD_SUFFIX_PATTERN.find(text) ?: return null
        val suffix = match.groups[1]?.value ?: return null
        evidenceMap["cardSuffix"] = FieldEvidence(
            fieldName = "cardSuffix",
            extractedValue = suffix,
            sourceSnippet = match.value,
            ruleOrPattern = "CARD_SUFFIX_PATTERN"
        )
        return suffix
    }
}
