package com.subhashrelangi.arctracker.engine.pipeline

/**
 * Data holder for extracted notification text content (Step 9.4).
 * Kept separate from [CapturedNotificationInfo] so that content extraction
 * is strictly deferred until AFTER the privacy gate passes.
 */
data class NotificationContent(
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val subText: String? = null,
    val summaryText: String? = null,
    val infoText: String? = null,
    val textLines: List<String> = emptyList()
)

/**
 * Data holder for a completely captured notification payload (Step 2).
 * Preserves individual fields separately without concatenation, interpretation,
 * or financial transaction parsing.
 */
data class CapturedNotificationInfo(
    val packageName: String,
    val notificationKey: String,
    val postTime: Long,
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val subText: String? = null,
    val summaryText: String? = null,
    val infoText: String? = null,
    val textLines: List<String> = emptyList(),
    val category: String? = null,
    val channelId: String? = null,
    val groupKey: String? = null,
    val isGroup: Boolean = false,
    val isGroupSummary: Boolean = false,
    val flags: Int = 0,
    val isUpdate: Boolean = false
)
