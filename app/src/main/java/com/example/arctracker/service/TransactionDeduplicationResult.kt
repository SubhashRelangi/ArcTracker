package com.example.arctracker.service

/**
 * Deduplication / correlation decision for a transaction candidate (Step 7).
 */
enum class DedupDecision {
    /** Candidate is a distinct new transaction not matching any known record. */
    NEW_TRANSACTION,

    /** Candidate is an exact duplicate of an already processed source event. */
    DUPLICATE,

    /** Candidate correlates with a separate record from another source (or notification) representing the same transaction. */
    CORRELATED,

    /** Candidate provides a status, reference, or content update to an existing transaction. */
    UPDATE_EXISTING,

    /** Ambiguous candidate or identity conflict (e.g. matching reference but differing amounts) requiring review. */
    NEEDS_REVIEW
}

/**
 * Strategy or rule that determined the deduplication decision (Step 7).
 */
enum class MatchStrategy {
    /** Matched via exact stable source event ID (notificationKey or SMS record ID). */
    SOURCE_EVENT_ID,

    /** Matched as an update to an existing notificationKey. */
    NOTIFICATION_UPDATE,

    /** Matched via explicit strong reference (UTR, RRN, UPI Ref, Txn ID). */
    EXPLICIT_REFERENCE_ID,

    /** Matched via strong multi-attribute fingerprint (amount + direction + merchant + account/UPI + time). */
    STRONG_FINGERPRINT,

    /** Matched as an inter-account self-transfer (opposite directions, matching reference & amount, different accounts). */
    SELF_TRANSFER,

    /** Identified a conflict between candidates (e.g. same reference ID with different amounts or directions). */
    IDENTITY_CONFLICT,

    /** Candidate is invalid/rejected or non-actionable from Step 6. */
    REJECTED_INPUT,

    /** No matching existing transaction found. */
    NONE
}

/**
 * Source type of a transaction.
 */
enum class TransactionSourceType {
    NOTIFICATION,
    SMS_HISTORY,
    MANUAL,
    UNKNOWN
}

/**
 * Common abstraction representing an existing transaction for correlation/deduplication comparisons.
 * Can represent a past [ValidatedTransactionCandidate] or an existing database/memory expense record.
 */
data class TransactionRecord(
    val id: String,
    val sourceNotificationKey: String? = null,
    val sourceType: TransactionSourceType = TransactionSourceType.NOTIFICATION,
    val amount: Double? = null,
    val currency: String? = null,
    val merchant: String? = null,
    val counterparty: String? = null,
    val direction: TransactionDirection = TransactionDirection.UNKNOWN,
    val status: TransactionStatus? = null,
    val referenceId: String? = null,
    val utr: String? = null,
    val rrn: String? = null,
    val upiTransactionId: String? = null,
    val upiId: String? = null,
    val accountSuffix: String? = null,
    val cardSuffix: String? = null,
    val bank: String? = null,
    val timestamp: Long = 0L,
    val transactionTimestamp: Long = timestamp,
    val timestampSource: TimestampSource = TimestampSource.NOTIFICATION_POST_TIME,
    val rawText: String? = null,
    val relationshipType: String? = null,
    val relationshipId: String? = null
) {
    companion object {
        fun fromValidated(
            validated: ValidatedTransactionCandidate,
            sourceType: TransactionSourceType = TransactionSourceType.NOTIFICATION
        ): TransactionRecord {
            val c = validated.candidate
            val inferredBank = c.bank ?: AccountIdentityExtractor.extractBankFromText(c.rawContent)
            return TransactionRecord(
                id = c.sourceNotificationKey,
                sourceNotificationKey = c.sourceNotificationKey,
                sourceType = sourceType,
                amount = c.amount,
                currency = c.currency,
                merchant = c.merchant,
                counterparty = c.counterparty,
                direction = c.direction,
                status = c.status,
                referenceId = c.referenceId,
                utr = c.utr,
                rrn = c.rrn,
                upiTransactionId = c.upiTransactionId,
                upiId = c.upiId,
                accountSuffix = c.accountSuffix,
                cardSuffix = c.cardSuffix,
                bank = inferredBank,
                timestamp = c.transactionTimestamp,
                transactionTimestamp = c.transactionTimestamp,
                timestampSource = c.transactionTimestampSource
            )
        }

        fun fromExpense(expense: com.example.arctracker.data.Expense): TransactionRecord {
            val srcType = when (expense.source) {
                "NOTIFICATION" -> TransactionSourceType.NOTIFICATION
                "SMS_HISTORY" -> TransactionSourceType.SMS_HISTORY
                "MANUAL" -> TransactionSourceType.MANUAL
                else -> TransactionSourceType.UNKNOWN
            }
            val textToSearch = "${expense.note ?: ""} ${expense.rawText ?: ""}"

            // Extract UTR/Ref if present in note, rawText, or relationshipId
            val utrMatch = Regex("""(?i)\b(?:utr|rrn|upi\s*ref(?:erence)?|upi\s*txn(?:\s*id)?|ref(?:\s*no|\s*id|\s*num)?|reference(?:\s*no|\s*id)?|txn(?:\s*id)?)\s*[:\-#]?\s*([A-Za-z0-9]{4,32})\b""").find(textToSearch)
            val extractedUtr = utrMatch?.groupValues?.get(1)
                ?: expense.relationshipId?.removePrefix("SELF_TRANSFER_")

            // Extract account suffix if present (supporting colons, e.g. A/c: XX1065)
            val accMatch = Regex("""(?i)\b(?:a/c|acct|acc|card|account)\s*(?:no\.?|:)?\s*[*xX]{0,4}(\d{4})\b""").find(textToSearch)
            val extractedAcc = accMatch?.groupValues?.get(1)

            // Extract UPI ID if present
            val upiMatch = Regex("""([a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64})""").find(textToSearch)
            val extractedUpi = upiMatch?.groupValues?.get(1)

            // Extract Bank from note or text
            val bankMatch = Regex("""(?i)\bBank:\s*([A-Za-z0-9 ]+?)(?:\s*\||$)""").find(expense.note ?: "")
            val extractedBank = bankMatch?.groupValues?.get(1)?.trim()
                ?: AccountIdentityExtractor.extractBankFromText(expense.note, expense.rawText)

            val recId = if (expense.notificationKey.isNotBlank()) expense.notificationKey else expense.id.toString()

            return TransactionRecord(
                id = recId,
                sourceNotificationKey = expense.notificationKey.takeIf { it.isNotBlank() },
                sourceType = srcType,
                amount = expense.amount,
                currency = "INR",
                merchant = expense.merchant,
                counterparty = null,
                direction = if (expense.type.equals("Credit", ignoreCase = true)) TransactionDirection.CREDIT else TransactionDirection.DEBIT,
                status = if (expense.isPending) TransactionStatus.PENDING else TransactionStatus.SUCCESS,
                referenceId = extractedUtr,
                utr = extractedUtr,
                rrn = null,
                upiTransactionId = null,
                upiId = extractedUpi,
                accountSuffix = extractedAcc,
                cardSuffix = null,
                bank = extractedBank,
                timestamp = expense.dateMillis,
                transactionTimestamp = expense.dateMillis,
                timestampSource = TimestampSource.NOTIFICATION_POST_TIME,
                rawText = expense.rawText,
                relationshipType = expense.relationshipType,
                relationshipId = expense.relationshipId
            )
        }
    }
}

/**
 * Complete, explainable deduplication and correlation result (Step 7).
 *
 * Explains:
 * 1. The decision (NEW_TRANSACTION, DUPLICATE, CORRELATED, UPDATE_EXISTING, NEEDS_REVIEW)
 * 2. Match strategy used
 * 3. Matched record identifier and data
 * 4. Matching signals and conflicting signals
 * 5. Time difference
 * 6. Human-readable explanation
 */
data class TransactionDeduplicationResult(
    val candidate: ValidatedTransactionCandidate,
    val decision: DedupDecision,
    val strategy: MatchStrategy,
    val matchedRecordId: String? = null,
    val matchedRecord: TransactionRecord? = null,
    val matchingSignals: List<String> = emptyList(),
    val conflictingSignals: List<String> = emptyList(),
    val correlationEvidence: String? = null,
    val timeDifferenceMillis: Long? = null,
    val reason: String,
    val relationshipType: String? = null,
    val relationshipId: String? = null
) {
    val isNew: Boolean
        get() = decision == DedupDecision.NEW_TRANSACTION

    val isDuplicate: Boolean
        get() = decision == DedupDecision.DUPLICATE

    val isCorrelated: Boolean
        get() = decision == DedupDecision.CORRELATED

    val isUpdate: Boolean
        get() = decision == DedupDecision.UPDATE_EXISTING

    val needsReview: Boolean
        get() = decision == DedupDecision.NEEDS_REVIEW

    val isSelfTransfer: Boolean
        get() = strategy == MatchStrategy.SELF_TRANSFER || relationshipType == "SELF_TRANSFER"
}
