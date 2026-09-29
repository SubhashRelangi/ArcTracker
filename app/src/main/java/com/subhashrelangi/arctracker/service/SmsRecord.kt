package com.subhashrelangi.arctracker.service

/**
 * Immutable data contract representing a raw SMS record read from Android's Telephony provider (Milestone 1).
 *
 * Corresponds to records queried from [android.provider.Telephony.Sms.Inbox] via ContentResolver.
 */
data class SmsRecord(
    val id: Long,
    val address: String,
    val body: String,
    val dateMillis: Long,
    val type: Int = 1, // Telephony.Sms.MESSAGE_TYPE_INBOX
    val read: Boolean = true,
    val subscriptionId: Int? = null
) {
    /**
     * Converts this raw [SmsRecord] into a normalized [TransactionSourceEvent]
     * for processing by the shared transaction engine.
     */
    fun toSourceEvent(): TransactionSourceEvent {
        return TransactionSourceEvent(
            sourceType = TransactionSourceType.SMS_HISTORY,
            sourceId = "sms_$id",
            sender = address,
            rawText = body,
            eventTimestamp = dateMillis,
            title = address,
            category = "sms"
        )
    }
}
