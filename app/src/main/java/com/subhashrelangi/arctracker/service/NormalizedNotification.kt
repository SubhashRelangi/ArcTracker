package com.subhashrelangi.arctracker.service

/**
 * Normalized representation of a captured notification (Step 3).
 *
 * Cleans and organizes notification text without financial classification or interpretation.
 * Retains complete reference to the original [CapturedNotificationInfo] for full raw data access.
 */
data class NormalizedNotification(
    val raw: CapturedNotificationInfo,
    val packageName: String = raw.packageName,
    val notificationKey: String = raw.notificationKey,
    val postTime: Long = raw.postTime,
    val category: String? = raw.category,
    val channelId: String? = raw.channelId,
    val groupKey: String? = raw.groupKey,
    val isGroup: Boolean = raw.isGroup,
    val isGroupSummary: Boolean = raw.isGroupSummary,
    val flags: Int = raw.flags,
    val isUpdate: Boolean = raw.isUpdate,
    val normalizedTitle: String? = null,
    val normalizedText: String? = null,
    val normalizedBigText: String? = null,
    val normalizedSubText: String? = null,
    val normalizedSummaryText: String? = null,
    val normalizedInfoText: String? = null,
    val normalizedTextLines: List<String> = emptyList(),
    val normalizedCombinedText: String = "",
    val normalizedCombinedTextLower: String = ""
) {
    val normalizedTitleLower: String?
        get() = normalizedTitle?.lowercase()

    val normalizedTextLower: String?
        get() = normalizedText?.lowercase()

    val normalizedBigTextLower: String?
        get() = normalizedBigText?.lowercase()

    val normalizedSubTextLower: String?
        get() = normalizedSubText?.lowercase()

    val normalizedSummaryTextLower: String?
        get() = normalizedSummaryText?.lowercase()

    val normalizedInfoTextLower: String?
        get() = normalizedInfoText?.lowercase()

    val normalizedTextLinesLower: List<String>
        get() = normalizedTextLines.map { it.lowercase() }
}
