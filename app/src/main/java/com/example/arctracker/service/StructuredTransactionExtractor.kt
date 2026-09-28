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
        """(?i)\b(?:debited|debit|credited|credit|paid|spent|sent|transferred|withdrawn|deposited|refunded|deducted|received|payment of|txn of|transfer of)\s*(?:by|of|for|is|:)?\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\b"""
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
        """(?i)\b(paid|debited|debit(?!\s*(?:card|limit|offer|option))|spent|sent|transferred|credited|credit(?!\s*(?:card|limit|score|bill|line|facility|offer|option))|received|withdrawn|deposited|refunded|deducted|payment of|txn of|transfer of|purchase|order payment|payment successful|transaction successful|recharge(?:d)?\s+(?:was\s+|is\s+|has been\s+)?(?:successful|completed)|recharge of)\b"""
    )

    private val PLAN_OR_OFFER_CONTEXT_PATTERN = Regex(
        """(?i)\b(?:plan\s+(?:of|at|for|@)?|pack\s+(?:of|at|for|@)?|starting\s+at|starts\s+at|special\s+offer|offer\s+price|plan\s+price|recharge\s+with\s+(?:rs\.?|inr|₹)?\s*\d+|recharge\s+plan|subscribe\s+now|get\s+[A-Za-z0-9]+\s+premium|premium\s+for\s+(?:rs\.?|inr|₹)?\s*\d+|for\s+\d+\s+(?:months?|days?|years?)|is\s+one\s+bill\s+away|chance\s+to\s+win|use\s+[a-z0-9_]{4,15}|no\s+extra\s+fees|no\s+fees)\b"""
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
        Regex("""(?i)\b(?:paid|sent|transferred|transfer|trf|payment)(?:\s+(?:(?:rs\.?|inr|₹)\s*)?\d[\d,]*(?:\.\d{1,2})?)?\s+(?:to|at|towards)\s+([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,50})"""),
        Regex("""(?i)\b(?:debited|debit)(?:\s+(?:(?:rs\.?|inr|₹)\s*)?\d[\d,]*(?:\.\d{1,2})?)?\s+(?:for\s+(?:upi\s+)?to|for payment to|towards|to|at)\s+([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,50})"""),
        Regex("""(?i)\b(?:purchase at|purchase on|spent at)\s+([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,50})"""),
        Regex("""(?i)\bpayment(?:\s+of)?(?:\s+(?:(?:rs\.?|inr|₹)\s*)?\d[\d,]*(?:\.\d{1,2})?)?\s+(?:to|at|towards)\s+([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,50})"""),
        Regex("""(?i)\b(?:paid to|sent to|transferred to|trf to)\s+([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,50}?)(?:\s+(?:for|of|is)?\s*(?:rs\.?|inr|₹)\s*\d)"""),
        Regex("""(?i)\btrf\s+(?:to\s+)?([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,50})""")
    )

    private val INCOMING_MERCHANT_PATTERNS = listOf(
        Regex("""(?i)\b(?:received|money received|transfer|trf|credited)(?:\s+(?:(?:rs\.?|inr|₹)\s*)?\d[\d,]*(?:\.\d{1,2})?)?\s+from\s+([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,50})"""),
        Regex("""(?i)\b(?:received\s+(?:a\s+)?payment\s+of|payment\s+of)(?:[^\n!?]*?)\s+from\s+([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,50})"""),
        Regex("""(?i)\b(?:refund of.*(?:received from|from))\s+([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,50})"""),
        Regex("""(?i)\brefund\s+(?:(?:rs\.?|inr|₹)\s*)?\d[\d,]*(?:\.\d{1,2})?\s+from\s+([A-Za-z0-9][A-Za-z0-9 &._\-@']{1,50})""")
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

    private val UPI_SLASH_REF_PATTERN = Regex(
        """(?i)\bUPI/(?:CREDIT|DEBIT)/([a-zA-Z0-9]{6,30})\b"""
    )

    private val UTR_PATTERN = Regex(
        """(?i)\bUTR\s*[:\-#]?\s*([a-zA-Z0-9]{9,30})\b"""
    )

    private val RRN_PATTERN = Regex(
        """(?i)\bRRN\s*[:\-#]?\s*([0-9]{9,16})\b"""
    )

    private val GENERAL_REF_PATTERN = Regex(
        """(?i)\b(?:ref\s*(?:no\.?|num(?:ber)?\.?)?|refno\.?|reference\s*(?:no\.?|num(?:ber)?\.?)?|txn\s*(?:id)?|transaction\s*(?:id)?|recharge\s*(?:id|no\.?|num(?:ber)?\.?|ref(?:erence)?\.?))\s*[:\-#]?\s*([a-zA-Z0-9]{4,30})\b"""
    )

    // Account / Card suffix patterns
    private val ACCOUNT_SUFFIX_PATTERN = Regex(
        """(?i)\b(?:a/c|account|acct)\s*(?:no\.?)?\s*(?:ending\s*(?:with|in)?)?\s*(?:[xX*]*(\d{3,6}))\b"""
    )

    private val CARD_SUFFIX_PATTERN = Regex(
        """(?i)\b(?:card)\s*(?:no\.?)?\s*(?:ending\s*(?:with|in)?)?\s*(?:[xX*]*(\d{3,6}))\b"""
    )

    private val MONTH_MAP = mapOf(
        "jan" to 0, "january" to 0,
        "feb" to 1, "february" to 1,
        "mar" to 2, "march" to 2,
        "apr" to 3, "april" to 3,
        "may" to 4,
        "jun" to 5, "june" to 5,
        "jul" to 6, "july" to 6,
        "aug" to 7, "august" to 7,
        "sep" to 8, "september" to 8,
        "oct" to 9, "october" to 9,
        "nov" to 10, "november" to 10,
        "dec" to 11, "december" to 11
    )

    private val DATE_TIME_PATTERN = Regex(
        """(?i)\b(\d{1,2})[-/ ]([A-Za-z]{3,9}|\d{1,2})(?:[-/ ](\d{2,4}))?\s*(?:at\s+|,\s*|\s+)\s*([01]?[0-9]|2[0-3]):([0-5][0-9])(?::([0-5][0-9]))?\s*(am|pm)?\b"""
    )

    private val TIME_DATE_PATTERN = Regex(
        """(?i)\b([01]?[0-9]|2[0-3]):([0-5][0-9])(?::([0-5][0-9]))?\s*(am|pm)?\s+(?:on|dated)\s+(\d{1,2})[-/ ]([A-Za-z]{3,9}|\d{1,2})(?:[-/ ](\d{2,4}))?\b"""
    )

    private val TIME_ONLY_PATTERN = Regex(
        """(?i)\b(?:at\s+|time\s*[:\-]?\s*)?([01]?[0-9]|2[0-3]):([0-5][0-9])(?::([0-5][0-9]))?\s*(am|pm)?\b"""
    )

    private val DATE_NAMED_MONTH_PATTERN = Regex(
        """(?i)\b(?:on\s+(?:date\s+)?)?(\d{1,2})[-/ ]?([A-Za-z]{3,9})[-/ ]?(\d{2,4})\b"""
    )

    private val DATE_NUMERIC_PATTERN = Regex(
        """(?i)\b(?:on\s+(?:date\s+)?)?(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})\b"""
    )

    // Boundary words that terminate a merchant phrase
    private val MERCHANT_BOUNDARY_PATTERN = Regex(
        """(?i)\b(on|using|via|thru|through|ref|refno|txn|avl|balance|bal|a/c|account|card|upi ref|utr|rrn|info|if not|call|sms|successful|completed|failed|pending)\b"""
    )

    // Phrases that look like merchants but are accounts / methods
    private val INVALID_MERCHANT_PREFIX_PATTERN = Regex(
        """(?i)^(account|a/c|acct|your account|card|vpa|upi id|upi|bank|your bank)\b"""
    )

    /**
     * Extracts ALL structured transaction candidates from a [NotificationClassificationResult] (Step 10).
     * Supports one notification containing 0, 1, or N independent transaction candidates.
     */
    fun extractAll(
        classification: NotificationClassificationResult
    ): List<StructuredTransactionCandidate> {
        if (classification.isNoise || classification.financialRelevance == FinancialRelevance.NON_FINANCIAL) {
            return emptyList()
        }

        val notification = classification.normalized

        // Step 4.5: Actual Transaction Event Gate (Notification-level check)
        val notifAssessment = ActualTransactionEventGate.assessNotification(notification, classification)
        if (notifAssessment.actualEvent == ActualEventStatus.FALSE) {
            return emptyList()
        }

        val segments = segmentNotification(notification)
        val candidates = mutableListOf<StructuredTransactionCandidate>()

        if (segments.size > 1) {
            for ((index, segment) in segments.withIndex()) {
                val segAssessment = ActualTransactionEventGate.assessClause(segment)
                if (segAssessment.actualEvent == ActualEventStatus.FALSE) {
                    continue
                }
                val candidate = extractFromSegment(
                    segmentText = segment,
                    wholeNotification = notification,
                    classification = classification,
                    subIndex = index,
                    totalSegments = segments.size
                )
                if (candidate != null) {
                    candidates.add(candidate.copy(eventAssessment = segAssessment))
                }
            }
        }

        // If segmentation did not produce candidates, evaluate as a single global notification
        if (candidates.isEmpty()) {
            val singleCandidate = extractInternal(notification, classification)
            val candAssessment = ActualTransactionEventGate.assessCandidate(singleCandidate, classification)
            if (candAssessment.actualEvent != ActualEventStatus.FALSE) {
                candidates.add(singleCandidate.copy(eventAssessment = candAssessment))
            }
        }

        return candidates
    }

    /**
     * Extracts all structured transaction candidates directly from a normalized notification.
     */
    fun extractAll(
        notification: NormalizedNotification,
        ignoreRules: List<IgnoreRule> = emptyList()
    ): List<StructuredTransactionCandidate> {
        val classification = FinancialClassifier.classify(notification, ignoreRules)
        return extractAll(classification)
    }

    /**
     * Extracts structured transaction candidate from a [NotificationClassificationResult].
     * Returns the primary/first candidate or null if none.
     */
    fun extract(
        classification: NotificationClassificationResult
    ): StructuredTransactionCandidate? {
        return extractAll(classification).firstOrNull()
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

    /**
     * Segments a notification into individual transaction clauses/lines.
     */
    fun segmentNotification(notification: NormalizedNotification): List<String> {
        val lines = notification.normalizedTextLines.map { it.trim() }.filter { it.isNotBlank() }

        // 1. InboxStyle textLines
        if (lines.size > 1) {
            val candidateLines = lines.filter { line ->
                hasTransactionActionOrAmount(line) && !isPureSummaryLine(line)
            }
            val nonSubsumedLines = candidateLines.filter { line ->
                candidateLines.none { other -> other.length > line.length && other.contains(line, ignoreCase = true) }
            }
            if (nonSubsumedLines.size > 1) {
                return nonSubsumedLines
            }
        }

        // 2. Newline-separated in bigText or combinedText
        val fullText = notification.normalizedCombinedText
        val textByNewlines = fullText.split('\n').map { it.trim() }.filter { it.isNotBlank() }
        if (textByNewlines.size > 1) {
            val candidateLines = textByNewlines.filter { line ->
                hasTransactionActionOrAmount(line) && !isPureSummaryLine(line)
            }
            val nonSubsumedLines = candidateLines.filter { line ->
                candidateLines.none { other -> other.length > line.length && other.contains(line, ignoreCase = true) }
            }
            if (nonSubsumedLines.size > 1) {
                return nonSubsumedLines
            }
        }

        // 3. Numbered entries: e.g. "1. Paid ₹500... 2. Paid ₹250..." or "1) ... 2) ..."
        val numberedPattern = Regex("""(?:\s|^)(?=[0-9]{1,2}[.)]\s*(?:paid|debited|spent|sent|transferred|credited|received|refunded|payment|txn|₹|rs\.?)\b)""", RegexOption.IGNORE_CASE)
        val numberedSplits = fullText.split(numberedPattern).map { it.trim() }.filter { it.isNotBlank() }
        if (numberedSplits.size > 1) {
            val valid = numberedSplits.filter { hasTransactionActionOrAmount(it) && !isPureSummaryLine(it) }
            if (valid.size > 1) {
                return valid
            }
        }

        // 4. Bullet entries: "• Paid ₹500... • Paid ₹250..."
        if (fullText.contains("•") || fullText.contains(" - ")) {
            val bulletSplits = fullText.split(Regex("""(?:\s|^)[•\-]\s*""")).map { it.trim() }.filter { it.isNotBlank() }
            if (bulletSplits.size > 1) {
                val valid = bulletSplits.filter { hasTransactionActionOrAmount(it) && !isPureSummaryLine(it) }
                if (valid.size > 1) {
                    return valid
                }
            }
        }

        // 5. Clause splits (periods, semicolons, and/&)
        val clauseSplits = splitIntoActionClauses(fullText)
        if (clauseSplits.size > 1) {
            return clauseSplits
        }

        return listOf(fullText)
    }

    private fun splitIntoActionClauses(fullText: String): List<String> {
        val clauseRegex = Regex("""(?<=[.!?;])\s+(?=(?:transaction\s*[0-9]*\s*[:\-]?\s*)?(?:(?:₹|rs\.?|inr)\s*[0-9]+.*?\b(?:paid|debited|spent|sent|transferred|credited|received|refunded)\b|(?:paid|debited|spent|sent|transferred|credited|received|refunded|payment of|txn of|transfer of)\b))""", RegexOption.IGNORE_CASE)
        val clauses = fullText.split(clauseRegex).map { it.trim() }.filter { it.isNotBlank() }
        if (clauses.size > 1) {
            val valid = clauses.filter { hasTransactionActionOrAmount(it) && !isPureSummaryLine(it) }
            if (valid.size > 1) {
                return valid
            }
        }

        val conjRegex = Regex("""\s+(?:and|&)\s+(?=(?:transaction\s*[0-9]*\s*[:\-]?\s*)?(?:paid|debited|spent|sent|transferred|credited|received|refunded|payment of|txn of|transfer of|(?:₹|rs\.?|inr)\s*[0-9]+)\b)""", RegexOption.IGNORE_CASE)
        val conjClauses = fullText.split(conjRegex).map { it.trim() }.filter { it.isNotBlank() }
        if (conjClauses.size > 1) {
            val valid = conjClauses.filter { hasTransactionActionOrAmount(it) && !isPureSummaryLine(it) }
            if (valid.size > 1) {
                return valid
            }
        }

        return listOf(fullText)
    }

    private fun hasTransactionActionOrAmount(line: String): Boolean {
        val hasAction = Regex("""(?i)\b(paid|debited|spent|sent|transferred|credited|received|refunded|payment|txn)\b""").containsMatchIn(line)
        val hasAmount = CURRENCY_PREFIX_AMOUNT_PATTERN.containsMatchIn(line) ||
                CURRENCY_SUFFIX_AMOUNT_PATTERN.containsMatchIn(line) ||
                ACTION_PRECEDED_AMOUNT_PATTERN.containsMatchIn(line)
        return hasAction && hasAmount
    }

    private fun isPureSummaryLine(line: String): Boolean {
        val trimmed = line.trim()
        val isTotalOnly = Regex("""(?i)^(?:grand\s+)?total\s*[:\-]?\s*(?:(?:rs\.?|inr|₹)\s*)?[0-9,]+(?:\.[0-9]{1,2})?\s*[.!?]?$""").matches(trimmed)
        val isBalanceOnly = Regex("""(?i)^(?:available\s+balance|avl\s+bal|balance|bal)\s*[:\-]?\s*(?:(?:rs\.?|inr|₹)\s*)?[0-9,]+(?:\.[0-9]{1,2})?\s*[.!?]?$""").matches(trimmed)
        return isTotalOnly || isBalanceOnly
    }

    private fun extractFromSegment(
        segmentText: String,
        wholeNotification: NormalizedNotification,
        classification: NotificationClassificationResult?,
        subIndex: Int,
        totalSegments: Int
    ): StructuredTransactionCandidate? {
        val evidenceMap = mutableMapOf<String, FieldEvidence>()
        val secondaryAmounts = mutableListOf<SecondaryAmount>()

        // 1. Amount Extraction within this segment
        val extractedAmount = extractAmount(segmentText, secondaryAmounts, evidenceMap)
        if (extractedAmount == null || extractedAmount <= 0.0) {
            return null
        }

        // Currency
        val currency = if (segmentText.contains("₹") || segmentText.contains("rs", ignoreCase = true) || segmentText.contains("inr", ignoreCase = true) ||
            wholeNotification.normalizedCombinedText.contains("₹") || wholeNotification.normalizedCombinedText.contains("rs", ignoreCase = true) || wholeNotification.normalizedCombinedText.contains("inr", ignoreCase = true)) {
            "INR"
        } else {
            null
        }

        // 2. Status Extraction
        val status = extractStatus(segmentText, evidenceMap) ?: extractStatus(wholeNotification.normalizedCombinedText, evidenceMap)

        // 3. Direction Extraction
        val direction = extractDirection(segmentText, classification, status, evidenceMap)

        // 4. UPI ID Extraction
        val upiId = extractUpiId(segmentText, evidenceMap) ?: extractUpiId(wholeNotification.normalizedCombinedText, evidenceMap)

        // 5. Merchant / Counterparty Extraction from segment
        val (merchant, counterparty) = extractMerchantAndCounterparty(segmentText, upiId, evidenceMap)

        // 6. Reference IDs (UTR, RRN, UPI Ref, Gen Ref) from segment, falling back to whole text
        val segmentRefs = extractReferences(segmentText, evidenceMap)
        val wholeRefs = if (segmentRefs.refId == null || segmentRefs.utr == null || segmentRefs.rrn == null || segmentRefs.upiTxnId == null) {
            extractReferences(wholeNotification.normalizedCombinedText, evidenceMap)
        } else {
            segmentRefs
        }
        val refId = segmentRefs.refId ?: wholeRefs.refId
        val utr = segmentRefs.utr ?: wholeRefs.utr
        val rrn = segmentRefs.rrn ?: wholeRefs.rrn
        val upiTxnId = segmentRefs.upiTxnId ?: wholeRefs.upiTxnId

        // 7. Account / Card Suffix
        val accountSuffix = extractAccountSuffix(segmentText, evidenceMap) ?: extractAccountSuffix(wholeNotification.normalizedCombinedText, evidenceMap)
        val cardSuffix = extractCardSuffix(segmentText, evidenceMap) ?: extractCardSuffix(wholeNotification.normalizedCombinedText, evidenceMap)

        // 8. Temporal & Bank Details
        val temporal = extractTemporalDetails(segmentText, wholeNotification.postTime, evidenceMap)

        val bank = extractBank(
            text = segmentText,
            sender = wholeNotification.packageName,
            accountSuffix = accountSuffix
        ) ?: extractBank(
            text = wholeNotification.normalizedCombinedText,
            sender = wholeNotification.packageName,
            accountSuffix = accountSuffix
        )

        val paymentRail = extractPaymentRail(segmentText)
            ?: extractPaymentRail(wholeNotification.normalizedCombinedText)

        val sourceKey = if (totalSegments > 1) "${wholeNotification.notificationKey}#$subIndex" else wholeNotification.notificationKey

        return StructuredTransactionCandidate(
            sourceNotificationKey = sourceKey,
            packageName = wholeNotification.packageName,
            postTime = wholeNotification.postTime,
            transactionTimestamp = temporal.timestamp,
            transactionTimestampSource = temporal.source,
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
            isUpdate = wholeNotification.isUpdate,
            groupKey = wholeNotification.groupKey,
            isGroup = wholeNotification.isGroup,
            isGroupSummary = wholeNotification.isGroupSummary,
            rawContent = segmentText,
            bank = bank,
            paymentRail = paymentRail,
            transactionDateString = temporal.dateString,
            transactionTimeString = temporal.timeString,
            temporalEvidence = temporal.temporalEvidence,
            eventAssessment = ActualTransactionEventGate.assessClause(segmentText, extractedAmount)
        )
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

        // 8. Temporal & Bank Details
        val temporal = extractTemporalDetails(text, notification.postTime, evidenceMap)

        val bank = extractBank(
            text = text,
            sender = notification.packageName,
            accountSuffix = accountSuffix
        )

        val paymentRail = extractPaymentRail(text)

        return StructuredTransactionCandidate(
            sourceNotificationKey = notification.notificationKey,
            packageName = notification.packageName,
            postTime = notification.postTime,
            transactionTimestamp = temporal.timestamp,
            transactionTimestampSource = temporal.source,
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
            rawContent = notification.normalizedCombinedText,
            bank = bank,
            paymentRail = paymentRail,
            transactionDateString = temporal.dateString,
            transactionTimeString = temporal.timeString,
            temporalEvidence = temporal.temporalEvidence,
            eventAssessment = ActualTransactionEventGate.assess(text, notification.packageName, extractedAmount)
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
        var isPlanOrOffer: Boolean = false,
        var hasActionVerb: Boolean = false,
        var hasCurrencyMarker: Boolean = false
    )

    private fun extractAmount(
        text: String,
        secondaryAmounts: MutableList<SecondaryAmount>,
        evidenceMap: MutableMap<String, FieldEvidence>
    ): Double? {
        if (LanguagePolicyHelper.shouldRejectAsUnsupportedLanguage(text)) {
            return null
        }

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

        // 3. Check Action-Preceded amounts without currency (e.g. "debited by 250", "debited by 10.00")
        for (match in ACTION_PRECEDED_AMOUNT_PATTERN.findAll(text)) {
            val numStr = match.groups[1]?.value ?: continue
            val clean = numStr.replace(",", "")
            val parsed = clean.toDoubleOrNull() ?: continue
            if (parsed <= 0.0) continue

            // Ensure not a phone number or reference number (10+ digits without decimal point)
            if (clean.length >= 10 && !clean.contains(".")) continue

            // Ensure not inside already matched currency amount or account suffix
            val numStart = match.value.lastIndexOf(numStr) + match.range.first
            if (candidates.any { numStart >= it.startIndex && numStart <= it.endIndex }) continue

            // Ensure text between action verb and number does not contain account/card/ref markers
            val verbToNum = match.value.substring(0, match.value.lastIndexOf(numStr))
            if (verbToNum.contains("a/c", ignoreCase = true) ||
                verbToNum.contains("account", ignoreCase = true) ||
                verbToNum.contains("card", ignoreCase = true) ||
                verbToNum.contains("ref", ignoreCase = true) ||
                verbToNum.contains("vpa", ignoreCase = true) ||
                verbToNum.contains("xx", ignoreCase = true)) {
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
            val isConditionalOffer = Regex("""(?i)\bpay\s+(?:rs\.?|inr|₹)?\s*\d+.*(?:and get|to get|and receive)\b""").containsMatchIn(clause)
            candidate.isPlanOrOffer = (PLAN_OR_OFFER_CONTEXT_PATTERN.containsMatchIn(clause) || isConditionalOffer) && !candidate.hasActionVerb

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
                c.isPlanOrOffer -> secondaryAmounts.add(
                    SecondaryAmount(SecondaryAmountType.OTHER, c.amount, c.rawSnippet, c.clauseSnippet)
                )
            }
        }

        // Filter valid transaction candidates (must not be balance, limit, fee, cashback, tax, or plan price)
        val validCandidates = candidates.filter {
            !it.isBalance && !it.isLimit && !it.isFee && !it.isCashback && !it.isTax && !it.isPlanOrOffer
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

        // 1. Check for beneficiary credit in an account debit message:
        // E.g. "Your a/c ... is debited for Rs.100 ... and credited to VPA 9704147837-3@axl"
        val isAccountDebitedBeneficiaryCredited = Regex(
            """(?i)\b(?:debited|debit)\b.*?\b(?:and\s+)?credited\s+to\s+(?:vpa\b|beneficiary\b|[a-zA-Z0-9.\-_]+@)"""
        ).containsMatchIn(text)

        if (isAccountDebitedBeneficiaryCredited) {
            evidenceMap["direction"] = FieldEvidence(
                fieldName = "direction",
                extractedValue = TransactionDirection.DEBIT.name,
                sourceSnippet = "User account debited with beneficiary credit clause",
                ruleOrPattern = "ACCOUNT_DEBIT_BENEFICIARY_CREDIT"
            )
            return TransactionDirection.DEBIT
        }

        // 2. Check for account credited with sender debited:
        val isAccountCreditedSenderDebited = Regex(
            """(?i)\b(?:credited|credit)\b.*?\b(?:and\s+)?debited\s+from\b"""
        ).containsMatchIn(text)

        if (isAccountCreditedSenderDebited) {
            evidenceMap["direction"] = FieldEvidence(
                fieldName = "direction",
                extractedValue = TransactionDirection.CREDIT.name,
                sourceSnippet = "User account credited with sender debit clause",
                ruleOrPattern = "ACCOUNT_CREDIT_SENDER_DEBIT"
            )
            return TransactionDirection.CREDIT
        }

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

        // Direction from Actual Transaction Event Gate
        val gateAssessment = ActualTransactionEventGate.assessClause(text)
        if (gateAssessment.actualEvent == ActualEventStatus.TRUE && gateAssessment.directionHint != TransactionDirection.UNKNOWN) {
            evidenceMap["direction"] = FieldEvidence(
                fieldName = "direction",
                extractedValue = gateAssessment.directionHint.name,
                sourceSnippet = "Actual Transaction Event Gate: ${gateAssessment.directionHint.name}",
                ruleOrPattern = "ACTUAL_EVENT_GATE_${gateAssessment.directionHint.name}"
            )
            return gateAssessment.directionHint
        }

        val hasDebitExplicit = Regex("""(?i)\b(paid to|sent to|transferred to|debited from|spent at|purchase at|paid at|debited by|debited for|paid using|is debited|was debited|has been debited|debited|debit(?!\s*(?:card|limit|offer|option)))\b""").containsMatchIn(text)
        val hasCreditExplicit = Regex("""(?i)\b(received from|credited to|credited in|credited into|credited with|deposited|refund of .* received|cashback received|cashback credited|is credited|was credited|has been credited|credited|credit(?!\s*(?:card|limit|score|bill|line|facility|offer|option)))\b""").containsMatchIn(text)

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

        if (cleaned.equals("unknown", ignoreCase = true) || cleaned.equals("unknown merchant", ignoreCase = true)) {
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

        if (upiRef == null) {
            val upiSlashMatch = UPI_SLASH_REF_PATTERN.find(text)
            if (upiSlashMatch != null) {
                val slashRef = upiSlashMatch.groups[1]?.value?.trim()
                if (slashRef != null) {
                    upiRef = slashRef
                    evidenceMap["upiTransactionId"] = FieldEvidence(
                        fieldName = "upiTransactionId",
                        extractedValue = slashRef,
                        sourceSnippet = upiSlashMatch.value,
                        ruleOrPattern = "UPI_SLASH_REF_PATTERN"
                    )
                }
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

        val genMatches = GENERAL_REF_PATTERN.findAll(text)
        for (genMatch in genMatches) {
            val candidate = genMatch.groups[1]?.value?.trim()
            // Avoid capturing word "No", trivial noise, status words, or helpline numbers
            if (candidate != null &&
                !candidate.equals("no", ignoreCase = true) &&
                !candidate.equals("successful", ignoreCase = true) &&
                !candidate.equals("completed", ignoreCase = true) &&
                !candidate.equals("failed", ignoreCase = true) &&
                !candidate.equals("pending", ignoreCase = true) &&
                !candidate.startsWith("1800") &&
                !candidate.startsWith("1860") &&
                candidate.length >= 3) {
                generalRef = candidate
                evidenceMap["referenceId"] = FieldEvidence(
                    fieldName = "referenceId",
                    extractedValue = candidate,
                    sourceSnippet = genMatch.value,
                    ruleOrPattern = "GENERAL_REF_PATTERN"
                )
                break
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

    data class ExtractedTemporalDetails(
        val timestamp: Long,
        val source: TimestampSource,
        val dateString: String?,
        val timeString: String?,
        val temporalEvidence: TemporalTransactionEvidence
    )

    fun extractTemporalDetails(
        text: String,
        anchorTime: Long,
        evidenceMap: MutableMap<String, FieldEvidence>? = null
    ): ExtractedTemporalDetails {
        val baseTime = if (anchorTime > 0) anchorTime else System.currentTimeMillis()
        val baseCal = java.util.Calendar.getInstance().apply {
            timeInMillis = baseTime
        }

        // 1. Try Date + Time (e.g. "02/09/2026 09:59", "15/09/2026 14:57:23", "25-Sep-2026 10:30")
        val matchDateTime = DATE_TIME_PATTERN.find(text)
        if (matchDateTime != null) {
            val day = matchDateTime.groupValues[1].toIntOrNull() ?: 1
            val monthStr = matchDateTime.groupValues[2].lowercase()
            val month = MONTH_MAP[monthStr] ?: (monthStr.toIntOrNull()?.minus(1) ?: baseCal.get(java.util.Calendar.MONTH))
            val yearStr = matchDateTime.groupValues[3]
            val year = if (yearStr.isNotBlank()) {
                val y = yearStr.toInt()
                if (y < 100) 2000 + y else y
            } else {
                baseCal.get(java.util.Calendar.YEAR)
            }
            var hour = matchDateTime.groupValues[4].toInt()
            val minute = matchDateTime.groupValues[5].toInt()
            val secondStr = matchDateTime.groupValues[6]
            val second = if (secondStr.isNotBlank()) secondStr.toInt() else 0
            val amPm = matchDateTime.groupValues[7].lowercase()
            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0

            val dateCal = java.util.Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val cal = java.util.Calendar.getInstance().apply {
                set(year, month, day, hour, minute, second)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val ts = cal.timeInMillis
            val dateTs = dateCal.timeInMillis
            val rawDateStr = matchDateTime.groupValues[0].substringBefore(" at ").substringBefore(",").substringBefore(" ").trim()
            val rawTimeStr = String.format("%02d:%02d%s", hour, minute, if (secondStr.isNotBlank()) String.format(":%02d", second) else "")

            evidenceMap?.put("transactionTimestamp", FieldEvidence(
                fieldName = "transactionTimestamp",
                extractedValue = ts.toString(),
                sourceSnippet = matchDateTime.value,
                ruleOrPattern = "DATE_TIME_PATTERN"
            ))

            val temporal = TemporalTransactionEvidence(
                transactionDateMillis = dateTs,
                transactionTimeMillis = ts,
                sourceEventTimeMillis = baseTime,
                timestampSource = TimestampSource.CONTENT
            )

            return ExtractedTemporalDetails(
                timestamp = ts,
                source = TimestampSource.CONTENT,
                dateString = rawDateStr,
                timeString = rawTimeStr,
                temporalEvidence = temporal
            )
        }

        // 2. Try Time + Date (e.g. "10:30 AM on 25-Sep", "10:30 on 25/09/2026")
        val matchTimeDate = TIME_DATE_PATTERN.find(text)
        if (matchTimeDate != null) {
            var hour = matchTimeDate.groupValues[1].toInt()
            val minute = matchTimeDate.groupValues[2].toInt()
            val secondStr = matchTimeDate.groupValues[3]
            val second = if (secondStr.isNotBlank()) secondStr.toInt() else 0
            val amPm = matchTimeDate.groupValues[4].lowercase()
            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0

            val day = matchTimeDate.groupValues[5].toIntOrNull() ?: 1
            val monthStr = matchTimeDate.groupValues[6].lowercase()
            val month = MONTH_MAP[monthStr] ?: (monthStr.toIntOrNull()?.minus(1) ?: baseCal.get(java.util.Calendar.MONTH))
            val yearStr = matchTimeDate.groupValues[7]
            val year = if (yearStr.isNotBlank()) {
                val y = yearStr.toInt()
                if (y < 100) 2000 + y else y
            } else {
                baseCal.get(java.util.Calendar.YEAR)
            }

            val dateCal = java.util.Calendar.getInstance().apply {
                set(year, month, day, 0, 0, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val cal = java.util.Calendar.getInstance().apply {
                set(year, month, day, hour, minute, second)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val ts = cal.timeInMillis
            val dateTs = dateCal.timeInMillis
            val rawTimeStr = String.format("%02d:%02d%s", hour, minute, if (secondStr.isNotBlank()) String.format(":%02d", second) else "")
            val rawDateStr = matchTimeDate.value.substringAfter("on ").substringAfter("dated ").trim()

            evidenceMap?.put("transactionTimestamp", FieldEvidence(
                fieldName = "transactionTimestamp",
                extractedValue = ts.toString(),
                sourceSnippet = matchTimeDate.value,
                ruleOrPattern = "TIME_DATE_PATTERN"
            ))

            val temporal = TemporalTransactionEvidence(
                transactionDateMillis = dateTs,
                transactionTimeMillis = ts,
                sourceEventTimeMillis = baseTime,
                timestampSource = TimestampSource.CONTENT
            )

            return ExtractedTemporalDetails(
                timestamp = ts,
                source = TimestampSource.CONTENT,
                dateString = rawDateStr,
                timeString = rawTimeStr,
                temporalEvidence = temporal
            )
        }

        // 3. Try Date Only (Named Month: e.g. "on date 02Sep26", "on 02-Sep-2026")
        val matchNamedDate = DATE_NAMED_MONTH_PATTERN.find(text)
        if (matchNamedDate != null) {
            val day = matchNamedDate.groupValues[1].toIntOrNull() ?: 1
            val monthStr = matchNamedDate.groupValues[2].lowercase()
            val month = MONTH_MAP[monthStr]
            if (month != null && day in 1..31) {
                val yearStr = matchNamedDate.groupValues[3]
                val year = if (yearStr.isNotBlank()) {
                    val y = yearStr.toInt()
                    if (y < 100) 2000 + y else y
                } else {
                    baseCal.get(java.util.Calendar.YEAR)
                }

                val dateCal = java.util.Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val dateTs = dateCal.timeInMillis
                val rawDateStr = matchNamedDate.value.replace(Regex("""(?i)^on\s+(?:date\s+)?"""), "").trim()

                evidenceMap?.put("transactionTimestamp", FieldEvidence(
                    fieldName = "transactionTimestamp",
                    extractedValue = dateTs.toString(),
                    sourceSnippet = matchNamedDate.value,
                    ruleOrPattern = "DATE_NAMED_MONTH_PATTERN"
                ))

                val temporal = TemporalTransactionEvidence(
                    transactionDateMillis = dateTs,
                    transactionTimeMillis = null, // Explicitly null: message had no time
                    sourceEventTimeMillis = baseTime,
                    timestampSource = TimestampSource.CONTENT
                )

                return ExtractedTemporalDetails(
                    timestamp = dateTs,
                    source = TimestampSource.CONTENT,
                    dateString = rawDateStr,
                    timeString = null, // Will result in transactionTime = "UNKNOWN"
                    temporalEvidence = temporal
                )
            }
        }

        // 4. Try Date Only (Numeric Month: e.g. "on 02-09-26", "15/09/2026")
        val matchNumDate = DATE_NUMERIC_PATTERN.find(text)
        if (matchNumDate != null) {
            val day = matchNumDate.groupValues[1].toIntOrNull() ?: 1
            val monthNum = matchNumDate.groupValues[2].toIntOrNull() ?: 1
            if (day in 1..31 && monthNum in 1..12) {
                val yearStr = matchNumDate.groupValues[3]
                val year = if (yearStr.isNotBlank()) {
                    val y = yearStr.toInt()
                    if (y < 100) 2000 + y else y
                } else {
                    baseCal.get(java.util.Calendar.YEAR)
                }

                val dateCal = java.util.Calendar.getInstance().apply {
                    set(year, monthNum - 1, day, 0, 0, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val dateTs = dateCal.timeInMillis
                val rawDateStr = matchNumDate.value.replace(Regex("""(?i)^on\s+(?:date\s+)?"""), "").trim()

                evidenceMap?.put("transactionTimestamp", FieldEvidence(
                    fieldName = "transactionTimestamp",
                    extractedValue = dateTs.toString(),
                    sourceSnippet = matchNumDate.value,
                    ruleOrPattern = "DATE_NUMERIC_PATTERN"
                ))

                val temporal = TemporalTransactionEvidence(
                    transactionDateMillis = dateTs,
                    transactionTimeMillis = null, // Explicitly null: message had no time
                    sourceEventTimeMillis = baseTime,
                    timestampSource = TimestampSource.CONTENT
                )

                return ExtractedTemporalDetails(
                    timestamp = dateTs,
                    source = TimestampSource.CONTENT,
                    dateString = rawDateStr,
                    timeString = null, // Will result in transactionTime = "UNKNOWN"
                    temporalEvidence = temporal
                )
            }
        }

        // 5. Try Time Only (e.g. "at 10:30 AM", "10:30", "time 14:30")
        val matchTime = TIME_ONLY_PATTERN.find(text)
        if (matchTime != null) {
            var hour = matchTime.groupValues[1].toInt()
            val minute = matchTime.groupValues[2].toInt()
            val secondStr = matchTime.groupValues[3]
            val second = if (secondStr.isNotBlank()) secondStr.toInt() else 0
            val amPm = matchTime.groupValues[4].lowercase()
            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0

            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = baseTime
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, second)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val ts = cal.timeInMillis
            val rawTimeStr = matchTime.value.trim()
            evidenceMap?.put("transactionTimestamp", FieldEvidence(
                fieldName = "transactionTimestamp",
                extractedValue = ts.toString(),
                sourceSnippet = matchTime.value,
                ruleOrPattern = "TIME_ONLY_PATTERN"
            ))

            val temporal = TemporalTransactionEvidence(
                transactionDateMillis = null,
                transactionTimeMillis = ts,
                sourceEventTimeMillis = baseTime,
                timestampSource = TimestampSource.CONTENT
            )

            return ExtractedTemporalDetails(
                timestamp = ts,
                source = TimestampSource.CONTENT,
                dateString = null,
                timeString = rawTimeStr,
                temporalEvidence = temporal
            )
        }

        // 6. Fallback (neither date nor time in content)
        val fallbackSource = if (anchorTime > 0) TimestampSource.NOTIFICATION_POST_TIME else TimestampSource.FALLBACK
        val temporal = TemporalTransactionEvidence(
            transactionDateMillis = null,
            transactionTimeMillis = null,
            sourceEventTimeMillis = baseTime,
            timestampSource = fallbackSource
        )

        return ExtractedTemporalDetails(
            timestamp = baseTime,
            source = fallbackSource,
            dateString = null,
            timeString = null,
            temporalEvidence = temporal
        )
    }

    fun extractTransactionTimestamp(
        text: String,
        postTime: Long,
        evidenceMap: MutableMap<String, FieldEvidence>? = null
    ): Pair<Long, TimestampSource>? {
        val details = extractTemporalDetails(text, postTime, evidenceMap)
        return if (details.source == TimestampSource.CONTENT) {
            Pair(details.timestamp, details.source)
        } else {
            null
        }
    }

    fun extractPaymentRail(text: String): String? {
        val match = Regex("""(?i)\b(UPI|IMPS|NEFT|RTGS|ATM|POS|CARD|NETBANKING)\b""").find(text)
        return match?.value?.uppercase()
    }

    fun extractBank(
        text: String,
        sender: String? = null,
        userAccounts: List<UserAccountContext> = emptyList(),
        accountSuffix: String? = null
    ): String? {
        // 1. User Account Context
        if (accountSuffix != null && userAccounts.isNotEmpty()) {
            val matched = userAccounts.firstOrNull { it.matchesAccountSuffix(accountSuffix) }
            if (!matched?.bankName.isNullOrBlank()) {
                return normalizeBankName(matched?.bankName!!)
            }
        }

        // 2. Trailing hyphen/dash suffix: e.g. "-IPPB", "-SBI", "-APGBank", ".-IPPB"
        val suffixMatch = Regex("""(?i)[-–—]\s*([A-Za-z0-9]{2,15}(?:Bank|BK)?)\s*[.]?\s*$""").find(text)
        if (suffixMatch != null) {
            val raw = suffixMatch.groupValues[1].trim()
            if (isValidBankToken(raw)) return normalizeBankName(raw)
        }

        // 3. "thru <Bank>" e.g. "thru IPPB"
        val thruMatch = Regex("""(?i)\bthru\s+([A-Za-z0-9]{2,15})\b""").find(text)
        if (thruMatch != null) {
            val raw = thruMatch.groupValues[1].trim()
            if (isValidBankToken(raw)) return normalizeBankName(raw)
        }

        // 4. Any hyphenated bank code in text: e.g. "-IPPB", "-SBI", "-APGBank"
        val anyHyphenMatch = Regex("""(?i)[-–—](IPPB|SBI|APGBank|APGB|HDFC|ICICI|AXIS|PNB|BOB|KOTAK|CANARA)\b""").find(text)
        if (anyHyphenMatch != null) {
            return normalizeBankName(anyHyphenMatch.groupValues[1])
        }

        // 5. Sender / Header (e.g. "AD-IPPB", "SBIUPI", "VK-SBI", "APGB", "APGBank")
        if (!sender.isNullOrBlank()) {
            val cleanSender = sender.substringAfter("-").trim()
            for (candidate in listOf("IPPB", "SBI", "APGBank", "APGB", "HDFC", "ICICI", "AXIS", "PNB", "BOB", "KOTAK", "CANARA")) {
                if (cleanSender.contains(candidate, ignoreCase = true)) {
                    return normalizeBankName(candidate)
                }
            }
        }

        return null
    }

    private fun normalizeBankName(raw: String): String {
        return when {
            raw.equals("APGBank", ignoreCase = true) -> "APGBank"
            raw.equals("APGB", ignoreCase = true) -> "APGB"
            else -> raw.uppercase()
        }
    }

    private fun isValidBankToken(token: String): Boolean {
        val lower = token.lowercase()
        val noise = setOf("sms", "otp", "call", "info", "team", "app", "alert", "care", "help")
        return token.length in 2..15 && token !in noise
    }
}
