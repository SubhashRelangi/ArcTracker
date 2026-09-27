package com.example.arctracker.service

/**
 * Explicit temporal evidence model for financial transactions (Milestone 1).
 *
 * CRITICAL ARCHITECTURAL GUARANTEE:
 * Does NOT assume source arrival timestamp (SMS delivery time or notification postTime)
 * equals the real-world transaction timestamp.
 *
 * Distinguishes:
 * 1. [transactionDateMillis]: Explicit date extracted from message content (e.g. "02-09-26", "15/09/2026").
 * 2. [transactionTimeMillis]: Explicit time extracted from message content (e.g. "09:59", "14:57:23").
 * 3. [sourceEventTimeMillis]: Timestamp when the message arrived on the device (SMS date or notification postTime).
 * 4. [observedTimeMillis]: Timestamp when ArcTracker observed or imported the message.
 *
 * When the transaction time is not explicitly stated in the message body, it remains UNKNOWN rather
 * than fabricating an arbitrary time from the SMS receipt timestamp.
 */
data class TemporalTransactionEvidence(
    val transactionDateMillis: Long? = null,
    val transactionTimeMillis: Long? = null,
    val sourceEventTimeMillis: Long,
    val observedTimeMillis: Long = System.currentTimeMillis(),
    val timestampSource: TimestampSource = TimestampSource.FALLBACK
) {
    /**
     * True if the message body contained an explicit, verifiable transaction time.
     */
    val hasExplicitTransactionTime: Boolean
        get() = transactionTimeMillis != null && timestampSource == TimestampSource.CONTENT

    /**
     * True if the message body contained an explicit transaction date.
     */
    val hasExplicitTransactionDate: Boolean
        get() = transactionDateMillis != null && timestampSource == TimestampSource.CONTENT

    /**
     * Effective transaction timestamp used for persistence and deduplication:
     * - Returns explicit [transactionTimeMillis] if available from content.
     * - Falls back to explicit [transactionDateMillis] if available.
     * - Falls back to [sourceEventTimeMillis] as the best available chronological anchor.
     */
    val effectiveTimestamp: Long
        get() = transactionTimeMillis
            ?: transactionDateMillis
            ?: sourceEventTimeMillis

    companion object {
        /**
         * Creates temporal evidence from extracted content timestamp, anchor, and observation time.
         */
        fun fromExtracted(
            extractedTimestamp: Long?,
            extractedSource: TimestampSource?,
            sourceEventTime: Long,
            observedTime: Long = System.currentTimeMillis()
        ): TemporalTransactionEvidence {
            val isFromContent = extractedTimestamp != null && extractedSource == TimestampSource.CONTENT
            return TemporalTransactionEvidence(
                transactionDateMillis = if (isFromContent) extractedTimestamp else null,
                transactionTimeMillis = if (isFromContent) extractedTimestamp else null,
                sourceEventTimeMillis = sourceEventTime,
                observedTimeMillis = observedTime,
                timestampSource = extractedSource ?: if (sourceEventTime > 0) TimestampSource.SMS_RECEIVE_TIME else TimestampSource.FALLBACK
            )
        }
    }
}
