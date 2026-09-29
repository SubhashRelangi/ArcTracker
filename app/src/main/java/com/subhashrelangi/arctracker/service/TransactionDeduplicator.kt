package com.subhashrelangi.arctracker.service

import kotlin.math.abs

/**
 * Pure, deterministic deduplication and cross-source correlation engine (Step 7).
 *
 * Evaluates [ValidatedTransactionCandidate] against existing transaction records to determine:
 * - NEW_TRANSACTION
 * - DUPLICATE
 * - CORRELATED
 * - UPDATE_EXISTING
 * - NEEDS_REVIEW
 *
 * Does NOT write, update, or delete in Room database (persistence is strictly Step 8).
 */
object TransactionDeduplicator {

    // Time window constants
    /** Live notification updates with the same notificationKey (e.g. processing -> successful). */
    const val NOTIFICATION_UPDATE_WINDOW_MS = 10 * 60 * 1000L // 10 minutes

    /** Cross-source correlation via explicit reference ID (UTR, RRN, UPI Ref) accounting for delayed SMS delivery. */
    const val REFERENCE_ID_CORRELATION_WINDOW_MS = 48 * 60 * 60 * 1000L // 48 hours

    /** Attribute fingerprint correlation (amount + direction + merchant + account/UPI) without explicit ID. */
    const val FINGERPRINT_CORRELATION_WINDOW_MS = 15 * 60 * 1000L // 15 minutes

    /**
     * Evaluates a validated transaction candidate against existing transaction records.
     *
     * @param candidate The validated candidate from Step 6.
     * @param existingRecords In-memory collection of previously captured/processed transactions.
     * @return [TransactionDeduplicationResult] with explainable decision, strategy, and signals.
     */
    fun evaluate(
        candidate: ValidatedTransactionCandidate,
        existingRecords: List<TransactionRecord>
    ): TransactionDeduplicationResult {

        // ----------------------------------------------------
        // 0. Check Step 6 Validation State
        // ----------------------------------------------------
        if (candidate.isRejected) {
            return TransactionDeduplicationResult(
                candidate = candidate,
                decision = DedupDecision.NEEDS_REVIEW,
                strategy = MatchStrategy.REJECTED_INPUT,
                reason = "Candidate was rejected during Step 6 validation; excluded from active deduplication"
            )
        }

        val candStruct = candidate.candidate
        val notifKey = candStruct.sourceNotificationKey

        // ----------------------------------------------------
        // 1. Level 1 — Source Identity (notificationKey)
        // ----------------------------------------------------
        if (notifKey.isNotBlank()) {
            val sameSourceMatch = existingRecords.firstOrNull {
                it.sourceNotificationKey != null && it.sourceNotificationKey == notifKey
            }

            if (sameSourceMatch != null) {
                val timeDiff = abs(candStruct.transactionTimestamp - sameSourceMatch.transactionTimestamp)

                // Check if this represents an update to the existing event
                val isStatusUpdate = candStruct.status != null &&
                        candStruct.status != sameSourceMatch.status &&
                        sameSourceMatch.status == TransactionStatus.PENDING

                val hasNewerReference = candStruct.referenceId != null && sameSourceMatch.referenceId == null
                val hasNewerMerchant = candStruct.merchant != null && sameSourceMatch.merchant == null

                val isExplicitUpdate = candStruct.isUpdate || isStatusUpdate || hasNewerReference || hasNewerMerchant

                return if (isExplicitUpdate && timeDiff <= NOTIFICATION_UPDATE_WINDOW_MS) {
                    TransactionDeduplicationResult(
                        candidate = candidate,
                        decision = DedupDecision.UPDATE_EXISTING,
                        strategy = MatchStrategy.NOTIFICATION_UPDATE,
                        matchedRecordId = sameSourceMatch.id,
                        matchedRecord = sameSourceMatch,
                        matchingSignals = listOf("SAME_NOTIFICATION_KEY", "PAYLOAD_UPDATE"),
                        timeDifferenceMillis = timeDiff,
                        reason = "Updated payload received for existing notificationKey '$notifKey'"
                    )
                } else {
                    TransactionDeduplicationResult(
                        candidate = candidate,
                        decision = DedupDecision.DUPLICATE,
                        strategy = MatchStrategy.SOURCE_EVENT_ID,
                        matchedRecordId = sameSourceMatch.id,
                        matchedRecord = sameSourceMatch,
                        matchingSignals = listOf("EXACT_NOTIFICATION_KEY"),
                        timeDifferenceMillis = timeDiff,
                        reason = "Identical source event already processed for notificationKey '$notifKey'"
                    )
                }
            }
        }

        // ----------------------------------------------------
        // 2. Level 2 — Explicit Transaction Identity (UTR, RRN, UPI Ref)
        // ----------------------------------------------------
        val candidateRefs = extractReferenceSet(candStruct)

        if (candidateRefs.isNotEmpty()) {
            for (existing in existingRecords) {
                val existingRefs = extractReferenceSet(existing)
                val commonRefs = candidateRefs.intersect(existingRefs)

                if (commonRefs.isNotEmpty()) {
                    val matchedRef = commonRefs.first()
                    val timeDiff = abs(candStruct.transactionTimestamp - existing.transactionTimestamp)
                    val arrivalDiff = if (candStruct.postTime > 0 && existing.timestamp > 0) abs(candStruct.postTime - existing.timestamp) else timeDiff
                    val effectiveTimeDiff = minOf(timeDiff, arrivalDiff)

                    // Conflict Check 1: Incompatible Amounts
                    val amountsDiffer = candStruct.amount != null && existing.amount != null && abs(candStruct.amount - existing.amount) >= 0.01
                    if (amountsDiffer) {
                        return TransactionDeduplicationResult(
                            candidate = candidate,
                            decision = DedupDecision.NEEDS_REVIEW,
                            strategy = MatchStrategy.IDENTITY_CONFLICT,
                            matchedRecordId = existing.id,
                            matchedRecord = existing,
                            matchingSignals = listOf("REFERENCE_ID_MATCH: $matchedRef"),
                            conflictingSignals = listOf("AMOUNT_MISMATCH: candidate=${candStruct.amount}, existing=${existing.amount}"),
                            timeDifferenceMillis = effectiveTimeDiff,
                            reason = "Matching reference ID ($matchedRef) but conflicting amounts: ${candStruct.amount} vs ${existing.amount}"
                        )
                    }

                    // Check Direction Relationship: Opposite directions could be a self-transfer across different accounts!
                    val isOppositeDirections = (candStruct.direction == TransactionDirection.DEBIT && existing.direction == TransactionDirection.CREDIT) ||
                            (candStruct.direction == TransactionDirection.CREDIT && existing.direction == TransactionDirection.DEBIT)

                    if (isOppositeDirections) {
                        val candAccount = candStruct.accountSuffix
                        val candBank = candStruct.bank ?: AccountIdentityExtractor.extractBankFromText(candStruct.rawContent)
                        val existingAccount = existing.accountSuffix
                        val existingBank = existing.bank ?: AccountIdentityExtractor.extractBankFromText(existing.rawText)

                        val isDifferentAccount = (candAccount != null && existingAccount != null && candAccount != existingAccount) ||
                                (candBank != null && existingBank != null && !candBank.equals(existingBank, ignoreCase = true))

                        if (isDifferentAccount && effectiveTimeDiff <= REFERENCE_ID_CORRELATION_WINDOW_MS) {
                            val relId = "SELF_TRANSFER_$matchedRef"
                            return TransactionDeduplicationResult(
                                candidate = candidate,
                                decision = DedupDecision.NEW_TRANSACTION,
                                strategy = MatchStrategy.SELF_TRANSFER,
                                matchedRecordId = existing.id,
                                matchedRecord = existing,
                                matchingSignals = listOf(
                                    "REFERENCE_ID_MATCH: $matchedRef",
                                    "AMOUNT_MATCH: ${candStruct.amount}",
                                    "SELF_TRANSFER_CORRELATED: ${candStruct.direction} vs ${existing.direction}"
                                ),
                                conflictingSignals = emptyList(),
                                timeDifferenceMillis = effectiveTimeDiff,
                                reason = "Self-transfer identified across accounts (${candBank ?: "A/c $candAccount"} <-> ${existingBank ?: "A/c $existingAccount"}) with reference $matchedRef",
                                relationshipType = "SELF_TRANSFER",
                                relationshipId = relId
                            )
                        } else {
                            // Opposite directions on same account or without account differentiation -> Conflict / Needs Review
                            return TransactionDeduplicationResult(
                                candidate = candidate,
                                decision = DedupDecision.NEEDS_REVIEW,
                                strategy = MatchStrategy.IDENTITY_CONFLICT,
                                matchedRecordId = existing.id,
                                matchedRecord = existing,
                                matchingSignals = listOf("REFERENCE_ID_MATCH: $matchedRef"),
                                conflictingSignals = listOf("DIRECTION_MISMATCH: candidate=${candStruct.direction}, existing=${existing.direction}"),
                                timeDifferenceMillis = effectiveTimeDiff,
                                reason = "Matching reference ID ($matchedRef) but conflicting directions: ${candStruct.direction} vs ${existing.direction}"
                            )
                        }
                    }

                    // Time window validation for reference matching
                    if (effectiveTimeDiff > REFERENCE_ID_CORRELATION_WINDOW_MS) {
                        return TransactionDeduplicationResult(
                            candidate = candidate,
                            decision = DedupDecision.NEEDS_REVIEW,
                            strategy = MatchStrategy.IDENTITY_CONFLICT,
                            matchedRecordId = existing.id,
                            matchedRecord = existing,
                            matchingSignals = listOf("REFERENCE_ID_MATCH: $matchedRef"),
                            conflictingSignals = listOf("TIME_WINDOW_EXCEEDED: ${effectiveTimeDiff / (1000 * 3600)}h > 48h"),
                            timeDifferenceMillis = effectiveTimeDiff,
                            reason = "Matching reference ID ($matchedRef) but time difference exceeds 48-hour window"
                        )
                    }

                    // Status update correlation (e.g. PENDING -> SUCCESS)
                    val isStatusUpdate = existing.status == TransactionStatus.PENDING &&
                            (candStruct.status == TransactionStatus.SUCCESS || candStruct.status == null)

                    val decision = if (isStatusUpdate) DedupDecision.UPDATE_EXISTING else DedupDecision.CORRELATED
                    val signals = mutableListOf("REFERENCE_ID_MATCH: $matchedRef")
                    if (candStruct.amount != null && existing.amount != null) signals.add("AMOUNT_MATCH: ${candStruct.amount}")
                    if (candStruct.direction == existing.direction) signals.add("DIRECTION_MATCH: ${candStruct.direction}")

                    return TransactionDeduplicationResult(
                        candidate = candidate,
                        decision = decision,
                        strategy = MatchStrategy.EXPLICIT_REFERENCE_ID,
                        matchedRecordId = existing.id,
                        matchedRecord = existing,
                        matchingSignals = signals,
                        correlationEvidence = "Common explicit reference ID '$matchedRef' across sources (${candStruct.packageName} <-> ${existing.sourceType})",
                        timeDifferenceMillis = effectiveTimeDiff,
                        reason = "Strong reference identifier ($matchedRef) matches existing record with compatible context"
                    )
                }
            }
        }

        // ----------------------------------------------------
        // 3. Level 3 — Strong Multi-Attribute Fingerprint
        // ----------------------------------------------------
        // Disallow matching on weak subsets alone (Rule 20):
        // - amount alone
        // - amount + direction alone
        // - amount + timestamp alone
        // - amount + merchant alone
        if (candStruct.amount != null && candStruct.direction != TransactionDirection.UNKNOWN) {
            for (existing in existingRecords) {
                // Rule 14: Never automatically merge manual transactions without explicit reference ID
                if (existing.sourceType == TransactionSourceType.MANUAL) {
                    continue
                }

                // Sibling transactions from the same multi-transaction notification are independent
                val existKey = existing.sourceNotificationKey
                if (!existKey.isNullOrBlank() && notifKey.isNotBlank()) {
                    val existParent = existKey.substringBefore("#")
                    val candParent = notifKey.substringBefore("#")
                    if (existParent == candParent && existKey != notifKey) {
                        continue
                    }
                }

                // Check amount match
                if (existing.amount == null || candStruct.amount != existing.amount) {
                    continue
                }

                // Check direction match
                if (existing.direction == TransactionDirection.UNKNOWN || candStruct.direction != existing.direction) {
                    continue
                }

                // Check time compatibility
                val timeDiff = abs(candStruct.transactionTimestamp - existing.transactionTimestamp)
                if (timeDiff > FINGERPRINT_CORRELATION_WINDOW_MS) {
                    continue
                }

                // Check for explicit conflicting attributes
                val candAccount = candStruct.accountSuffix
                val existAccount = existing.accountSuffix
                val candCard = candStruct.cardSuffix
                val existCard = existing.cardSuffix

                val candInstrument = candAccount ?: candCard
                val existInstrument = existAccount ?: existCard

                val hasAccountConflict = (candAccount != null && existAccount != null && candAccount != existAccount) ||
                        (candCard != null && existCard != null && candCard != existCard) ||
                        (candInstrument != null && existInstrument != null && candInstrument != existInstrument)

                val candUpi = candStruct.upiId
                val existUpi = existing.upiId
                val hasUpiConflict = candUpi != null && existUpi != null && candUpi.lowercase() != existUpi.lowercase()

                val candMerchantNorm = cleanMerchant(candStruct.merchant)
                val existMerchantNorm = cleanMerchant(existing.merchant)
                val hasMerchantConflict = candMerchantNorm != null && existMerchantNorm != null && candMerchantNorm != existMerchantNorm

                // If explicit attributes conflict, do not correlate
                if (hasAccountConflict) {
                    return TransactionDeduplicationResult(
                        candidate = candidate,
                        decision = DedupDecision.NEEDS_REVIEW,
                        strategy = MatchStrategy.IDENTITY_CONFLICT,
                        matchedRecordId = existing.id,
                        matchedRecord = existing,
                        conflictingSignals = listOf("ACCOUNT_SUFFIX_MISMATCH: $candInstrument vs $existInstrument"),
                        timeDifferenceMillis = timeDiff,
                        reason = "Matching amount and direction but conflicting account/card suffixes: $candInstrument vs $existInstrument"
                    )
                }

                val candRefs = extractReferenceSet(candStruct)
                val existRefs = extractReferenceSet(existing)
                val hasRefConflict = candRefs.isNotEmpty() && existRefs.isNotEmpty() && candRefs.intersect(existRefs).isEmpty()

                if (hasUpiConflict || hasMerchantConflict || hasRefConflict) {
                    // Conflicting party or explicit transaction reference -> separate transaction
                    continue
                }

                // Corroborating attributes
                val sameMerchant = candMerchantNorm != null && existMerchantNorm != null && candMerchantNorm == existMerchantNorm
                val sameInstrument = candInstrument != null && existInstrument != null && candInstrument == existInstrument
                val sameUpiId = candUpi != null && existUpi != null && candUpi.lowercase() == existUpi.lowercase()

                // Evaluate Fingerprint Strength:
                // Must have Amount + Direction + Time PLUS at least one verified entity/account identifier (Account/Card, UPI ID, or Merchant)
                val hasExplicitContentTimeDiff = (candStruct.transactionTimestampSource == TimestampSource.CONTENT || existing.timestampSource == TimestampSource.CONTENT) && timeDiff > 0L

                if (sameMerchant && !sameInstrument && !sameUpiId && hasExplicitContentTimeDiff && timeDiff <= 60_000L) {
                    // Close time difference with merchant match but missing instrument / UPI corroboration
                    return TransactionDeduplicationResult(
                        candidate = candidate,
                        decision = DedupDecision.NEEDS_REVIEW,
                        strategy = MatchStrategy.STRONG_FINGERPRINT,
                        matchedRecordId = existing.id,
                        matchedRecord = existing,
                        matchingSignals = listOf(
                            "AMOUNT_MATCH: ${candStruct.amount}",
                            "DIRECTION_MATCH: ${candStruct.direction}",
                            "MERCHANT_MATCH: $candMerchantNorm",
                            "CLOSE_TIME: ${timeDiff / 1000}s"
                        ),
                        conflictingSignals = listOf("INSUFFICIENT_IDENTITY_EVIDENCE: lacking instrument suffix or UPI ID with differing timestamps"),
                        timeDifferenceMillis = timeDiff,
                        reason = "Matching amount, direction, and merchant with close timestamps (${timeDiff / 1000}s) but lacking corroborating instrument or reference identity"
                    )
                }

                val isStrongFingerprint = when {
                    sameMerchant && (sameInstrument || sameUpiId) -> true
                    sameInstrument && (candStruct.merchant == null || existing.merchant == null) -> true
                    sameUpiId -> true
                    sameMerchant && timeDiff == 0L -> true
                    sameMerchant && !hasExplicitContentTimeDiff && timeDiff <= 60_000L -> true
                    else -> false
                }

                if (isStrongFingerprint) {
                    val matchingSignals = mutableListOf(
                        "AMOUNT_MATCH: ${candStruct.amount}",
                        "DIRECTION_MATCH: ${candStruct.direction}",
                        "TIME_COMPATIBLE: ${timeDiff / 1000}s <= ${FINGERPRINT_CORRELATION_WINDOW_MS / 1000}s"
                    )
                    if (sameMerchant) matchingSignals.add("MERCHANT_MATCH: $candMerchantNorm")
                    if (sameInstrument) matchingSignals.add("INSTRUMENT_SUFFIX_MATCH: $candInstrument")
                    if (sameUpiId) matchingSignals.add("UPI_ID_MATCH: $candUpi")

                    return TransactionDeduplicationResult(
                        candidate = candidate,
                        decision = DedupDecision.CORRELATED,
                        strategy = MatchStrategy.STRONG_FINGERPRINT,
                        matchedRecordId = existing.id,
                        matchedRecord = existing,
                        matchingSignals = matchingSignals,
                        correlationEvidence = "Multi-attribute fingerprint match across sources (${candStruct.packageName} <-> ${existing.sourceType})",
                        timeDifferenceMillis = timeDiff,
                        reason = "Strong multi-attribute fingerprint match with compatible timing"
                    )
                }
            }
        }

        // ----------------------------------------------------
        // 4. Default — New Transaction
        // ----------------------------------------------------
        return TransactionDeduplicationResult(
            candidate = candidate,
            decision = DedupDecision.NEW_TRANSACTION,
            strategy = MatchStrategy.NONE,
            reason = "No matching existing transaction or duplicate source event found"
        )
    }

    /**
     * Extracts all candidate reference strings for reference-based matching.
     */
    private fun extractReferenceSet(c: StructuredTransactionCandidate): Set<String> {
        val set = mutableSetOf<String>()
        c.referenceId?.trim()?.let { if (it.length >= 4) set.add(it) }
        c.upiTransactionId?.trim()?.let { if (it.length >= 4) set.add(it) }
        c.utr?.trim()?.let { if (it.length >= 4) set.add(it) }
        c.rrn?.trim()?.let { if (it.length >= 4) set.add(it) }
        return set
    }

    /**
     * Extracts all reference strings from a [TransactionRecord].
     */
    private fun extractReferenceSet(r: TransactionRecord): Set<String> {
        val set = mutableSetOf<String>()
        r.referenceId?.trim()?.let { if (it.length >= 4) set.add(it) }
        r.upiTransactionId?.trim()?.let { if (it.length >= 4) set.add(it) }
        r.utr?.trim()?.let { if (it.length >= 4) set.add(it) }
        r.rrn?.trim()?.let { if (it.length >= 4) set.add(it) }
        return set
    }

    /**
     * Normalizes a merchant string for deterministic comparison.
     * Trims whitespace, lowercases, and collapses internal spaces.
     */
    fun cleanMerchant(merchant: String?): String? {
        if (merchant.isNullOrBlank()) return null
        return merchant.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}
