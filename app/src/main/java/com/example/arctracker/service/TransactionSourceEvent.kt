package com.example.arctracker.service

/**
 * Normalized source event boundary abstraction (Milestone 1).
 *
 * Serves as the unified ingestion contract for the Shared Transaction Engine,
 * bridging both real-time Notification events and historical SMS messages.
 *
 * CRITICAL ARCHITECTURAL GUARANTEES:
 * 1. Mobile-number senders are categorized via [isMobileSender] as source-risk evidence,
 *    and NEVER automatically rejected without evaluating financial context.
 * 2. Source arrival timestamp [eventTimestamp] is strictly preserved as arrival metadata,
 *    and NEVER conflated with the real-world transaction timestamp.
 * 3. Both Notification and SMS sources produce [TransactionSourceEvent], flowing through
 *    the exact same shared transaction extraction, validation, and deduplication engine.
 */
data class TransactionSourceEvent(
    val sourceType: TransactionSourceType,
    val sourceId: String,
    val sender: String,
    val rawText: String,
    val eventTimestamp: Long,
    val title: String? = null,
    val category: String? = null,
    val channelOrThreadId: String? = null,
    val isUpdate: Boolean = false
) {
    /**
     * Checks if the sender represents a numeric or mobile phone number.
     * Used as source-risk evidence; never a rejection gate on its own.
     */
    val isMobileSender: Boolean
        get() {
            val clean = sender.trim()
            if (clean.startsWith("+")) return true
            val digitsCount = clean.count { it.isDigit() }
            return digitsCount >= 10 && digitsCount.toDouble() / clean.length > 0.7
        }

    /**
     * Adapts this source event into [CapturedNotificationInfo] to provide 100% backward
     * compatibility with existing notification pipeline components.
     */
    fun toCapturedNotificationInfo(): CapturedNotificationInfo {
        return CapturedNotificationInfo(
            packageName = sender,
            notificationKey = sourceId,
            postTime = eventTimestamp,
            title = title ?: sender,
            text = rawText,
            category = category ?: if (sourceType == TransactionSourceType.SMS_HISTORY) "sms" else null,
            channelId = channelOrThreadId,
            isUpdate = isUpdate
        )
    }

    companion object {
        /**
         * Creates a [TransactionSourceEvent] from an existing [CapturedNotificationInfo].
         */
        fun fromCapturedNotification(captured: CapturedNotificationInfo): TransactionSourceEvent {
            return TransactionSourceEvent(
                sourceType = if (captured.category == "sms" || captured.notificationKey.startsWith("sms_")) {
                    TransactionSourceType.SMS_HISTORY
                } else {
                    TransactionSourceType.NOTIFICATION
                },
                sourceId = captured.notificationKey,
                sender = captured.packageName,
                rawText = buildString {
                    captured.title?.let { append(it).append("\n") }
                    captured.text?.let { append(it).append("\n") }
                    captured.bigText?.let { append(it).append("\n") }
                    captured.subText?.let { append(it).append("\n") }
                    captured.summaryText?.let { append(it).append("\n") }
                    captured.infoText?.let { append(it).append("\n") }
                    if (captured.textLines.isNotEmpty()) {
                        append(captured.textLines.joinToString("\n"))
                    }
                }.trim(),
                eventTimestamp = captured.postTime,
                title = captured.title,
                category = captured.category,
                channelOrThreadId = captured.channelId,
                isUpdate = captured.isUpdate
            )
        }
    }
}
