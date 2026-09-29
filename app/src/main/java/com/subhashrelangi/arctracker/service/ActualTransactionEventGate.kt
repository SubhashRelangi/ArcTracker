package com.subhashrelangi.arctracker.service

/**
 * Pure, deterministic semantic gate that assesses whether an actual financial transaction event occurred (Step 4.5).
 *
 * Distinguishes genuine money movement from future actions, promotional offers,
 * and informational statements across all applications and SMS messages.
 */
object ActualTransactionEventGate {

    // 1. Positive Evidence: Completed Debit Actions
    private val COMPLETED_DEBIT_PATTERNS = listOf(
        Regex("""\b(?:paid|sent|transferred)\s+(?:to|at|for)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:debited\s+(?:from|by|for)|spent\s+at|purchase\s+(?:at|on|of)|withdrawn\s+from)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(paid|debited|deducted|spent|withdrawn|charged)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:payment|transaction|txn)\s+(?:at|to|for)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpayment\s+of\s+(?:rs\.?|inr|₹)?\s*[\d,]+(?:\.\d{1,2})?(?:/-)?\s+(?:made|to|debited|sent)\b""", RegexOption.IGNORE_CASE),
        Regex("""(?:rs\.?|inr|₹)\s*[\d,]+(?:\.\d{1,2})?(?:/-)?\s+(?:was\s+|is\s+|has been\s+)?(?:debited|paid|deducted|spent|sent|transferred|charged)\b""", RegexOption.IGNORE_CASE),
        Regex("""(?:rs\.?|inr|₹)\s*[\d,]+(?:\.\d{1,2})?(?:/-)?\s+(?:transaction|txn)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:paid|debited|spent|sent|transferred)\s+(?:rs\.?|inr|₹)\s*[\d,]+""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:recharge\s+of|payment\s+of|transfer\s+of|txn\s+of)\s+(?:rs\.?|inr|₹)?\s*[\d,]+(?:\.\d{1,2})?(?:/-)?\s+(?:was\s+|is\s+|has been\s+)?(?:successful|completed|processed)\b""", RegexOption.IGNORE_CASE),
        Regex("""\border\s+payment\s+successful\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpayment\s+to\s+[A-Za-z0-9 &._-]+\s+(?:was\s+|is\s+)?(?:successful|completed)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:is\s+|was\s+|has been\s+)?debited\s+(?:from|in|by)\s+(?:your\s+)?(?:a/c|acct|acc|account)\b""", RegexOption.IGNORE_CASE)
    )

    // 2. Positive Evidence: Completed Credit Actions
    private val COMPLETED_CREDIT_PATTERNS = listOf(
        Regex("""\b(?:credited\s+(?:to|with|in|into)|deposited\s+(?:in|into|to)|received\s+from)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(credited|deposited)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcredit\b(?!\s*(?:card|score|limit|bill|line|facility|offer|option))""", RegexOption.IGNORE_CASE),
        Regex("""\bUPI/CREDIT/\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:received\s+(?:a\s+)?payment\s+(?:of\s+)?(?:rs\.?|inr|₹)?\s*[\d,]+|payment\s+(?:of\s+)?(?:rs\.?|inr|₹)?\s*[\d,]+.*?\breceived)\b""", RegexOption.IGNORE_CASE),
        Regex("""(?:rs\.?|inr|₹)\s*[\d,]+(?:\.\d{1,2})?(?:/-)?\s+(?:was\s+|is\s+|has been\s+)?(?:credited|deposited|refunded)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:credited|deposited|refunded)\s+(?:with\s+|in\s+|into\s+|to\s+)?(?:rs\.?|inr|₹)\s*[\d,]+""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:refund\s+of|cashback\s+of)\s+(?:rs\.?|inr|₹)?\s*[\d,]+(?:\.\d{1,2})?(?:/-)?\s+(?:credited|received|processed)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:cashback\s+credited|money\s+received|refund\s+received|refund\s+processed|added\s+to\s+wallet)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:received\s+(?:rs\.?|inr|₹)\s*[\d,]+(?:\.\d{1,2})?(?:/-)?\s+from)\b""", RegexOption.IGNORE_CASE),
        Regex("""(?:rs\.?|inr|₹)\s*[\d,]+(?:\.\d{1,2})?(?:/-)?\s+received\s+from\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:is\s+|was\s+|has been\s+)?credited\s+(?:in|into|to|with)\s+(?:your\s+)?(?:a/c|acct|acc|account)\b""", RegexOption.IGNORE_CASE)
    )

    // 3. Positive Evidence: Status Confirmations
    private val STATUS_SUCCESS_PATTERNS = listOf(
        Regex("""\b(?:payment|transaction|transfer|recharge|bill payment)\s+(?:is\s+|was\s+|has been\s+)?(?:successful|completed|processed)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:payment|transaction|transfer)\s+successful\s*[:\-]?\s*(?:rs\.?|inr|₹)?\s*[\d,]+""", RegexOption.IGNORE_CASE)
    )

    private val STATUS_FAILED_PATTERNS = listOf(
        Regex("""\b(?:payment|transaction|transfer)\s+(?:failed|declined|unsuccessful)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:payment|transaction)\s+failed\s*[:\-]?\s*(?:rs\.?|inr|₹)?\s*[\d,]+""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:failed|declined|unsuccessful)\b""", RegexOption.IGNORE_CASE)
    )

    private val STATUS_PENDING_PATTERNS = listOf(
        Regex("""\b(?:payment|transaction|transfer)\s+(?:pending|under process|in process)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:payment|transaction)\s+pending\s*[:\-]?\s*(?:rs\.?|inr|₹)?\s*[\d,]+""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:pending|under process|in process)\b""", RegexOption.IGNORE_CASE)
    )

    private val STATUS_REVERSED_PATTERNS = listOf(
        Regex("""\b(?:payment|transaction|transfer)\s+(?:reversed|rolled back)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:payment|transaction)\s+reversed\s*[:\-]?\s*(?:rs\.?|inr|₹)?\s*[\d,]+""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:reversal|reversed)\b""", RegexOption.IGNORE_CASE)
    )

    // 4. Negative Evidence: Future / Instructional / Imperative Actions
    private val FUTURE_ACTION_PATTERNS = listOf(
        Regex("""\b(?:pay\s+your|pay\s+the|pay\s+bill|pay\s+bills|pay\s+any|pay\s+today|pay\s+online|pay\s+using)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:pay\s+another\s+(?:rs\.?|inr|₹)?\s*[\d,]+)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:pay\b(?!\s+(?:to|at|for)\b)|can pay|pay now|tap to pay|click to pay|how to pay|to pay|will pay|should pay|please pay)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bpay\b.*?\b(?:and\s+get|to\s+get|to\s+win|and\s+win|to\s+receive|and\s+receive)\b""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        Regex("""\b(?:when\s+you\s+pay|if\s+you\s+pay|on\s+paying|after\s+paying)\b""", RegexOption.IGNORE_CASE),
        Regex("""\blearn\s+how\s+to\s+make\s+a(?:\s+.*?)?\s+payment\b""", RegexOption.IGNORE_CASE),
        Regex("""\bis\s+one\s+bill\s+away\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:recharge\s+now|recharge\s+today|recharge\s+online|tap\s+to\s+recharge|click\s+to\s+recharge)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:subscribe\s+now|subscribe\s+today|buy\s+now|order\s+now)\b""", RegexOption.IGNORE_CASE)
    )

    // 5. Negative Evidence: Promotional / Marketing / Contests / Reward Offers
    private val PROMOTIONAL_PATTERNS = listOf(
        Regex("""\b(?:cashback\s+offer|special\s+offer|limited\s+time\s+offer|exclusive\s+offer|festive\s+offer|card\s+offer|offers?\s+available)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:chance\s+to\s+win|stand\s+a\s+chance|win\s+rewards?|win\s+scratch\s+cards?|eligible\s+to\s+win|win\s+cashback)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:coupon|promo\s+code|promocode|voucher|use\s+code|use\s+[a-z0-9_]{4,15})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:no\s+extra\s+fees|no\s+fees|zero\s+fees|zero\s+convenience\s+fee)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:eligible\s+for|check\s+eligibility)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:get|win|earn)\s+(?:a\s+chance\s+to\s+win\s+)?(?:rs\.?|inr|₹)?\s*[\d,]+.*?\b(?:cashback|reward|bonus|discount|off)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:cashback\s+on\s+your\s+next|on\s+your\s+next\s+purchase|on\s+your\s+next\s+payment|on\s+next\s+transaction|next\s+time)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:get\s+(?:rs\.?|inr|₹)?\s*[\d,]+\s+cashback)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:win\s+up\s+to|earn\s+up\s+to|get\s+up\s+to)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:reward\s+points?\s+expiring|scratch\s+card)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:save\s+on\s+your\s+next|invite\s+friends\s+and\s+earn)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bdebit\s+card\s+offers?\b""", RegexOption.IGNORE_CASE)
    )

    // 6. Negative Evidence: Informational / Balance / Limit Statements
    private val INFORMATIONAL_PATTERNS = listOf(
        Regex("""\b(?:available\s+balance|current\s+balance|clear\s+balance|acc\s+balance|a/c\s+balance|bal\s+is|balance\s+is|bal\s*[:=\-]|account\s+balance\s+is|your\s+account\s+balance\s+is)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:credit\s+limit|debit\s+limit|transaction\s+limit|daily\s+limit|card\s+limit|transfer\s+limit)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:limit\s+is|limit\s+of)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:minimum\s+amount\s+due|bill\s+amount|amount\s+due|statement\s+total|bill\s+due|payment\s+due|due\s+date|total\s+due|due\s+on)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:statement\s+generated|statement\s+available|monthly\s+statement|e-statement|account\s+summary|bill\s+reminder|payment\s+reminder|reminder\b|reminder\s*[:\-])""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:credit\s+card\s+payment\s+options|payment\s+options?\s+available|options?\s+available)\b""", RegexOption.IGNORE_CASE)
    )

    // Currency Detection
    private val CURRENCY_PATTERN = Regex("""[₹$€£]|(?:rs\.?|inr|rupees?)\b""", RegexOption.IGNORE_CASE)

    /**
     * Assesses a normalized notification for actual financial transaction event truth (Step 4.5).
     */
    fun assessNotification(
        notification: NormalizedNotification,
        classification: NotificationClassificationResult? = null
    ): TransactionEventAssessment {
        val text = notification.normalizedCombinedText
        return assess(text, notification.packageName)
    }

    /**
     * Assesses an individual clause / segment for actual transaction event truth.
     */
    fun assessClause(
        clauseText: String,
        candidateAmount: Double? = null
    ): TransactionEventAssessment {
        return assess(clauseText, candidateAmount = candidateAmount)
    }

    /**
     * Assesses an extracted candidate along with its evidence.
     */
    fun assessCandidate(
        candidate: StructuredTransactionCandidate,
        classification: NotificationClassificationResult? = null
    ): TransactionEventAssessment {
        val snippet = candidate.getEvidence("amount")?.sourceSnippet
            ?: candidate.rawContent
            ?: ""
        return assess(snippet, candidate.packageName, candidate.amount)
    }

    /**
     * Core evidence-based assessment engine.
     */
    fun assess(
        text: String,
        packageName: String? = null,
        candidateAmount: Double? = null
    ): TransactionEventAssessment {
        val positiveEvidence = mutableListOf<String>()
        val negativeEvidence = mutableListOf<String>()
        val diagnosticReasons = mutableListOf<String>()

        val hasCompletedDebit = COMPLETED_DEBIT_PATTERNS.any { it.containsMatchIn(text) }
        val hasCompletedCredit = COMPLETED_CREDIT_PATTERNS.any { it.containsMatchIn(text) }
        val isSuccess = STATUS_SUCCESS_PATTERNS.any { it.containsMatchIn(text) }
        val isFailed = STATUS_FAILED_PATTERNS.any { it.containsMatchIn(text) }
        val isPending = STATUS_PENDING_PATTERNS.any { it.containsMatchIn(text) }
        val isReversed = STATUS_REVERSED_PATTERNS.any { it.containsMatchIn(text) }
        val hasStatus = isSuccess || isFailed || isPending || isReversed

        val hasFutureAction = FUTURE_ACTION_PATTERNS.any { it.containsMatchIn(text) }
        val hasPromotional = PROMOTIONAL_PATTERNS.any { it.containsMatchIn(text) }
        val hasInformational = INFORMATIONAL_PATTERNS.any { it.containsMatchIn(text) }

        if (hasCompletedDebit) positiveEvidence.add("COMPLETED_DEBIT_ACTION")
        if (hasCompletedCredit) positiveEvidence.add("COMPLETED_CREDIT_ACTION")
        if (isSuccess) positiveEvidence.add("STATUS_SUCCESS")
        if (isFailed) positiveEvidence.add("STATUS_FAILED")
        if (isPending) positiveEvidence.add("STATUS_PENDING")
        if (isReversed) positiveEvidence.add("STATUS_REVERSED")

        if (hasFutureAction) negativeEvidence.add("FUTURE_ACTION")
        if (hasPromotional) negativeEvidence.add("PROMOTIONAL_OFFER")
        if (hasInformational) negativeEvidence.add("INFORMATIONAL_STATEMENT")

        val hasPositiveSignal = hasCompletedDebit || hasCompletedCredit || hasStatus
        val hasCurrency = CURRENCY_PATTERN.containsMatchIn(text) || candidateAmount != null

        // 1. Clear False: No completed action, but future, promotional, or informational patterns present
        if (!hasPositiveSignal && (hasFutureAction || hasPromotional || hasInformational)) {
            val eventType = when {
                hasFutureAction -> TransactionEventType.FUTURE_ACTION
                hasPromotional -> TransactionEventType.PROMOTIONAL
                else -> TransactionEventType.INFORMATIONAL
            }
            val reason = when (eventType) {
                TransactionEventType.FUTURE_ACTION -> "Future/instructional payment command without completed transaction evidence"
                TransactionEventType.PROMOTIONAL -> "Future/promotional payment language without completed transaction evidence"
                else -> "Informational balance/limit/bill statement without completed transaction event"
            }
            diagnosticReasons.add(reason)
            return TransactionEventAssessment(
                actualEvent = ActualEventStatus.FALSE,
                eventType = eventType,
                directionHint = TransactionDirection.UNKNOWN,
                isActionAssociatedWithAmount = false,
                positiveEvidence = positiveEvidence,
                negativeEvidence = negativeEvidence,
                diagnosticReasons = diagnosticReasons
            )
        }

        // 2. Clear False: No positive action and no currency
        if (!hasPositiveSignal && !hasCurrency) {
            diagnosticReasons.add("No financial transaction signals or monetary amounts detected")
            return TransactionEventAssessment(
                actualEvent = ActualEventStatus.FALSE,
                eventType = TransactionEventType.UNKNOWN,
                directionHint = TransactionDirection.UNKNOWN,
                isActionAssociatedWithAmount = false,
                positiveEvidence = positiveEvidence,
                negativeEvidence = negativeEvidence,
                diagnosticReasons = diagnosticReasons
            )
        }

        // 3. Ambiguous / Uncertain: Currency present but no completed transaction action
        if (!hasPositiveSignal && hasCurrency) {
            diagnosticReasons.add("Isolated currency amount without completed transaction action verb or confirmed status")
            return TransactionEventAssessment(
                actualEvent = ActualEventStatus.UNCERTAIN,
                eventType = TransactionEventType.UNKNOWN,
                directionHint = TransactionDirection.UNKNOWN,
                isActionAssociatedWithAmount = false,
                positiveEvidence = positiveEvidence,
                negativeEvidence = negativeEvidence,
                diagnosticReasons = diagnosticReasons
            )
        }

        // 4. Positive Event Present: Determine Direction and Status
        val eventType = when {
            isFailed -> TransactionEventType.FAILED
            isPending -> TransactionEventType.PENDING
            isReversed -> TransactionEventType.REVERSED
            hasCompletedCredit -> TransactionEventType.COMPLETED
            else -> TransactionEventType.COMPLETED
        }

        val hasDebitWord = Regex("""(?i)\b(debited|debit(?!\s*(?:card|limit|offer|option))|paid|spent|withdrawn)\b""").containsMatchIn(text)
        val hasCreditWord = Regex("""(?i)\b(credited|credit(?!\s*(?:card|score|limit|bill|line|facility|offer|option))|deposited|received|refunded)\b""").containsMatchIn(text)

        val directionHint = when {
            hasDebitWord && hasCreditWord -> TransactionDirection.UNKNOWN
            isReversed -> TransactionDirection.UNKNOWN
            hasCompletedCredit && !hasCompletedDebit -> TransactionDirection.CREDIT
            hasCompletedDebit && !hasCompletedCredit -> TransactionDirection.DEBIT
            hasCreditWord && !hasDebitWord -> TransactionDirection.CREDIT
            hasDebitWord && !hasCreditWord -> TransactionDirection.DEBIT
            isSuccess && !hasCompletedCredit && !hasCreditWord -> TransactionDirection.DEBIT
            isFailed -> TransactionDirection.DEBIT
            isPending -> TransactionDirection.DEBIT
            else -> TransactionDirection.UNKNOWN
        }

        diagnosticReasons.add("Verified actual transaction event ($eventType) with direction $directionHint")

        return TransactionEventAssessment(
            actualEvent = ActualEventStatus.TRUE,
            eventType = eventType,
            directionHint = directionHint,
            isActionAssociatedWithAmount = true,
            positiveEvidence = positiveEvidence,
            negativeEvidence = negativeEvidence,
            diagnosticReasons = diagnosticReasons
        )
    }
}
