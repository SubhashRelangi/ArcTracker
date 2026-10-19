package com.subhashrelangi.arctracker.service

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.subhashrelangi.arctracker.security.SecureLogger
import com.subhashrelangi.arctracker.settings.MonitoringSettingsRepository
import kotlinx.coroutines.launch
import java.util.Collections

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

/**
 * Complete Notification Capture Service (Step 2).
 *
 * Captures all available notification content safely and reliably into [CapturedNotificationInfo].
 * Does not parse transactions, classify data, or alter persistent databases.
 */
class NotificationReaderService : NotificationListenerService() {

    private val serviceJob = kotlinx.coroutines.SupervisorJob()
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + serviceJob)

    companion object {
        private const val TAG = "NotificationReader"
        private const val MAX_ACTIVE_NOTIFICATIONS = 500

        @Volatile
        var isConnected: Boolean = false
            internal set

        private val _connectionState = kotlinx.coroutines.flow.MutableStateFlow(false)
        val connectionState: kotlinx.coroutines.flow.StateFlow<Boolean> = _connectionState

        fun isServiceRunningAndConnected(): Boolean = isConnected

        @Volatile
        var lastCapturedNotification: CapturedNotificationInfo? = null
            internal set

        /**
         * Optional settings repository override for testing.
         * When null, production repository from applicationContext is used.
         */
        @Volatile
        var settingsRepositoryOverride: MonitoringSettingsRepository? = null

        var notificationListener: ((CapturedNotificationInfo) -> Unit)? = null

        // In-memory cache of currently tracked notifications keyed by notificationKey.
        // Uses access-order LRU eviction to prevent unbounded memory growth.
        private val activeNotificationsMap: MutableMap<String, CapturedNotificationInfo> =
            Collections.synchronizedMap(
                object : LinkedHashMap<String, CapturedNotificationInfo>(100, 0.75f, true) {
                    override fun removeEldestEntry(
                        eldest: MutableMap.MutableEntry<String, CapturedNotificationInfo>?
                    ): Boolean {
                        return size > MAX_ACTIVE_NOTIFICATIONS
                    }
                }
            )

        /**
         * Returns the latest captured notification for the given key, if available.
         */
        fun getActiveNotification(key: String): CapturedNotificationInfo? {
            return activeNotificationsMap[key]
        }

        /**
         * Returns all currently active captured notifications.
         */
        fun getAllActiveNotifications(): List<CapturedNotificationInfo> {
            return synchronized(activeNotificationsMap) {
                activeNotificationsMap.values.toList()
            }
        }

        /**
         * Clears active notifications cache (used for testing and teardown).
         */
        fun clearActiveNotifications() {
            activeNotificationsMap.clear()
        }

        /**
         * Safely extracts a text field from [Bundle] extras.
         * Handles CharSequence, String, Array/List of CharSequence, nested Bundle, and numbers.
         * Never throws on unexpected payload types.
         */
        @Suppress("DEPRECATION")
        fun extractTextField(bundle: Bundle?, key: String): String? {
            if (bundle == null) return null
            return try {
                val value = bundle.get(key)
                extractTextFromValue(value)
            } catch (e: Throwable) {
                null
            }
        }

        /**
         * Converts raw extra value to String safely.
         */
        fun extractTextFromValue(value: Any?): String? {
            return when (value) {
                null -> null
                is CharSequence -> value.toString()
                is Array<*> -> value.filterIsInstance<CharSequence>().firstOrNull()?.toString()
                is Collection<*> -> value.filterIsInstance<CharSequence>().firstOrNull()?.toString()
                is Number -> value.toString()
                is CharArray -> String(value)
                is Bundle -> {
                    // Check standard nested text keys
                    extractTextField(value, Notification.EXTRA_TEXT)
                        ?: extractTextField(value, Notification.EXTRA_TITLE)
                        ?: extractTextField(value, "text")
                }
                else -> null
            }
        }

        /**
         * Safely extracts text lines (e.g. from InboxStyle EXTRA_TEXT_LINES).
         * Preserves every line in order and safely converts each CharSequence to String.
         * Never crashes on unexpected types or missing keys.
         */
        @Suppress("DEPRECATION")
        fun extractTextLines(bundle: Bundle?): List<String> {
            if (bundle == null) return emptyList()
            return try {
                extractTextLinesFromValue(bundle.get(Notification.EXTRA_TEXT_LINES))
            } catch (e: Throwable) {
                emptyList()
            }
        }

        /**
         * Converts raw EXTRA_TEXT_LINES payload to a list of strings safely preserving order.
         */
        fun extractTextLinesFromValue(raw: Any?): List<String> {
            return when (raw) {
                null -> emptyList()
                is Array<*> -> {
                    val lines = mutableListOf<String>()
                    for (item in raw) {
                        if (item != null) {
                            lines.add(item.toString())
                        }
                    }
                    lines
                }
                is Collection<*> -> {
                    val lines = mutableListOf<String>()
                    for (item in raw) {
                        if (item != null) {
                            lines.add(item.toString())
                        }
                    }
                    lines
                }
                is CharSequence -> listOf(raw.toString())
                else -> emptyList()
            }
        }

        /**
         * Safely extracts message lines from raw value (e.g., Array<Bundle> or Array<CharSequence>).
         */
        @Suppress("DEPRECATION")
        fun extractMessagingLinesFromValue(rawMessages: Any?): List<String> {
            if (rawMessages == null) return emptyList()
            val lines = mutableListOf<String>()
            fun processItem(item: Any?) {
                when (item) {
                    is Bundle -> {
                        val msgText = item.getCharSequence("text")?.toString()
                            ?: item.getString("text")
                            ?: extractTextField(item, Notification.EXTRA_TEXT)
                        if (!msgText.isNullOrBlank()) {
                            lines.add(msgText)
                        }
                    }
                    is CharSequence -> {
                        if (item.isNotBlank()) lines.add(item.toString())
                    }
                }
            }

            when (rawMessages) {
                is Array<*> -> rawMessages.forEach { processItem(it) }
                is Collection<*> -> rawMessages.forEach { processItem(it) }
                is CharSequence -> if (rawMessages.isNotBlank()) lines.add(rawMessages.toString())
            }
            return lines
        }

        /**
         * Safely extracts message lines from MessagingStyle notifications (EXTRA_MESSAGES).
         */
        @Suppress("DEPRECATION")
        fun extractMessagingLines(bundle: Bundle?): List<String> {
            if (bundle == null) return emptyList()
            val rawMessages = try {
                bundle.get(Notification.EXTRA_MESSAGES) ?: bundle.get("android.messages")
            } catch (_: Throwable) {
                null
            } ?: return emptyList()
            return extractMessagingLinesFromValue(rawMessages)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        _connectionState.value = true
        DiagnosticEventManager.logEvent(DiagnosticCategory.LISTENER_CONNECTED)
        SecureLogger.d(TAG, "NotificationReaderService connected.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        _connectionState.value = false
        DiagnosticEventManager.logEvent(DiagnosticCategory.LISTENER_DISCONNECTED)
        SecureLogger.d(TAG, "NotificationReaderService disconnected.")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        isConnected = false
        _connectionState.value = false
        DiagnosticEventManager.logEvent(DiagnosticCategory.LISTENER_DISCONNECTED)
        SecureLogger.d(TAG, "NotificationReaderService destroyed.")
    }

    /**
     * Records and logs a captured notification if permission is valid.
     * Detects if the notificationKey has already been seen to distinguish new notifications
     * from updates, and stores the latest complete payload in [activeNotificationsMap].
     *
     * @return [CapturedNotificationInfo] if permission is granted, or null if permission is unavailable.
     */
    fun recordCapturedNotification(
        packageName: String,
        notificationKey: String,
        postTime: Long,
        title: String? = null,
        text: String? = null,
        bigText: String? = null,
        subText: String? = null,
        summaryText: String? = null,
        infoText: String? = null,
        textLines: List<String> = emptyList(),
        category: String? = null,
        channelId: String? = null,
        groupKey: String? = null,
        isGroup: Boolean = false,
        isGroupSummary: Boolean = false,
        flags: Int = 0
    ): CapturedNotificationInfo? {
        // Independently verify that Notification Access permission is currently granted before any processing
        if (!NotificationPermissionHelper.isNotificationAccessGranted(this)) {
            SecureLogger.w(TAG, "Notification received but Notification Access permission is not granted. Discarding.")
            return null
        }

        return try {
            // Detect if this notificationKey has already been recorded
            val isUpdate = if (notificationKey.isNotEmpty()) {
                activeNotificationsMap.containsKey(notificationKey)
            } else {
                false
            }

            val captured = CapturedNotificationInfo(
                packageName = packageName,
                notificationKey = notificationKey,
                postTime = postTime,
                title = title,
                text = text,
                bigText = bigText,
                subText = subText,
                summaryText = summaryText,
                infoText = infoText,
                textLines = textLines,
                category = category,
                channelId = channelId,
                groupKey = groupKey,
                isGroup = isGroup,
                isGroupSummary = isGroupSummary,
                flags = flags,
                isUpdate = isUpdate
            )

            // Diagnostic logging of minimal, non-sensitive notification metadata (no raw financial payload)
            SecureLogger.d(
                TAG,
                "Notification captured [isUpdate=$isUpdate]: " +
                    "package=$packageName, key=$notificationKey, postTime=$postTime, " +
                    "hasTitle=${!title.isNullOrEmpty()}, hasText=${!text.isNullOrEmpty()}, " +
                    "hasBigText=${!bigText.isNullOrEmpty()}, hasSubText=${!subText.isNullOrEmpty()}, " +
                    "textLinesCount=${textLines.size}, category=$category, channelId=$channelId, " +
                    "groupKey=$groupKey, isGroup=$isGroup, isGroupSummary=$isGroupSummary, flags=$flags"
            )

            if (notificationKey.isNotEmpty()) {
                activeNotificationsMap[notificationKey] = captured
            }

            lastCapturedNotification = captured
            notificationListener?.invoke(captured)

            // Step 8: Trigger end-to-end transaction pipeline safely on background IO thread
            if (!isGroupSummary) {
                serviceScope.launch {
                    try {
                        TransactionPersistenceManager.processCapturedNotification(applicationContext, captured)
                    } catch (e: Exception) {
                        SecureLogger.e(TAG, "Safe catch: pipeline failure in NotificationReaderService", e)
                    }
                }
            } else {
                DiagnosticEventManager.logEvent(
                    DiagnosticCategory.NOTIFICATION_IGNORED,
                    packageName = packageName,
                    reason = "group_summary"
                )
                SecureLogger.d(TAG, "Notification skipped from transaction ingestion: group summary container")
            }

            captured
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Error recording captured notification in NotificationReaderService", e)
            null
        }
    }

    fun getSettingsRepository(): MonitoringSettingsRepository {
        settingsRepositoryOverride?.let { return it }
        val appContext = try {
            applicationContext ?: this
        } catch (_: Exception) {
            this
        }
        return MonitoringSettingsRepository.getInstance(appContext)
    }

    /**
     * Extracts content from [Bundle] extras safely into a [NotificationContent] structure.
     */
    fun extractContentFromExtras(extras: Bundle?): NotificationContent {
        if (extras == null) return NotificationContent()
        val title = extractTextField(extras, Notification.EXTRA_TITLE)
            ?: extractTextField(extras, "android.title.big")
        val text = extractTextField(extras, Notification.EXTRA_TEXT)
        val bigText = extractTextField(extras, Notification.EXTRA_BIG_TEXT)
        val subText = extractTextField(extras, Notification.EXTRA_SUB_TEXT)
        val summaryText = extractTextField(extras, Notification.EXTRA_SUMMARY_TEXT)
        val infoText = extractTextField(extras, Notification.EXTRA_INFO_TEXT)
        val textLines = extractTextLines(extras)
        val messagingLines = extractMessagingLines(extras)

        val combinedTextLines = if (textLines.isNotEmpty() && messagingLines.isNotEmpty()) {
            (textLines + messagingLines).distinct()
        } else if (messagingLines.isNotEmpty()) {
            messagingLines
        } else {
            textLines
        }

        val resolvedText = if (text.isNullOrBlank() && messagingLines.isNotEmpty()) {
            messagingLines.lastOrNull()
        } else {
            text
        }

        return NotificationContent(
            title = title,
            text = resolvedText,
            bigText = bigText,
            subText = subText,
            summaryText = summaryText,
            infoText = infoText,
            textLines = combinedTextLines
        )
    }

    /**
     * Processes an incoming notification with Step 9.4 Privacy Gate enforcement.
     * Checks global and package monitoring settings BEFORE invoking [contentExtractor].
     * If the notification is blocked, [contentExtractor] is NEVER invoked, ensuring zero content access.
     *
     * @return true if allowed, captured, and processed; false if blocked by privacy gate or permission missing.
     */
    fun processNotification(
        packageName: String,
        notificationKey: String,
        postTime: Long,
        channelId: String? = null,
        category: String? = null,
        groupKey: String? = null,
        isGroup: Boolean = false,
        isGroupSummary: Boolean = false,
        flags: Int = 0,
        contentExtractor: () -> NotificationContent
    ): Boolean {
        // Step 1: Independently verify Notification Access permission
        if (!NotificationPermissionHelper.isNotificationAccessGranted(this)) {
            SecureLogger.w(TAG, "Notification received but Notification Access permission is not granted. Discarding.")
            return false
        }

        if (packageName.isEmpty()) {
            return false
        }

        // Step 2: Privacy Gate - Query settings repository (FAIL-CLOSED)
        val settings = try {
            getSettingsRepository().getSettings()
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Privacy gate: failed to read monitoring settings. Failing closed (blocking notification).", e)
            return false
        }

        // Step 3: Global monitoring check - MUST return before content extraction
        if (!settings.globalEnabled) {
            DiagnosticEventManager.logEvent(
                DiagnosticCategory.NOTIFICATION_IGNORED,
                packageName = packageName,
                reason = "global_disabled"
            )
            SecureLogger.d(TAG, "Notification ignored: global monitoring is disabled")
            return false
        }

        // App Notifications source check - MUST return before content extraction
        if (!settings.isNotificationTrackingEnabled) {
            DiagnosticEventManager.logEvent(
                DiagnosticCategory.NOTIFICATION_IGNORED,
                packageName = packageName,
                reason = "notification_tracking_disabled"
            )
            SecureLogger.d(TAG, "Notification ignored: notification tracking is disabled")
            return false
        }

        // Step 4: Package monitoring check - MUST return before content extraction
        if (!settings.isAppEnabled(packageName)) {
            DiagnosticEventManager.logEvent(
                DiagnosticCategory.SOURCE_NOT_SUPPORTED,
                packageName = packageName,
                reason = "app_not_monitored"
            )
            SecureLogger.d(TAG, "Notification ignored: package is not enabled for monitoring")
            return false
        }

        // Step 5: PRIVACY GATE PASSED - Now and only now extract notification content
        val content = try {
            contentExtractor()
        } catch (e: Exception) {
            DiagnosticEventManager.logEvent(
                DiagnosticCategory.PARSE_FAILED,
                packageName = packageName,
                reason = "content_extraction_failed"
            )
            SecureLogger.e(TAG, "Error extracting notification content in NotificationReaderService", e)
            return false
        }

        if (content.title.isNullOrBlank() && content.text.isNullOrBlank() &&
            content.bigText.isNullOrBlank() && content.textLines.isEmpty()
        ) {
            DiagnosticEventManager.logEvent(
                DiagnosticCategory.CONTENT_UNAVAILABLE,
                packageName = packageName,
                reason = "all_content_fields_empty"
            )
        }

        // Step 6: Record captured notification and trigger transaction pipeline
        return recordCapturedNotification(
            packageName = packageName,
            notificationKey = notificationKey,
            postTime = postTime,
            title = content.title,
            text = content.text,
            bigText = content.bigText,
            subText = content.subText,
            summaryText = content.summaryText,
            infoText = content.infoText,
            textLines = content.textLines,
            category = category,
            channelId = channelId,
            groupKey = groupKey,
            isGroup = isGroup,
            isGroupSummary = isGroupSummary,
            flags = flags
        ) != null
    }

    /**
     * Handles an incoming notification safely with independent permission and privacy gate verification.
     * Extracts all available fields from [StatusBarNotification] extras ONLY IF the privacy gate permits processing.
     *
     * @return true if notification was captured and logged; false if ignored, blocked by privacy gate, or permission missing.
     */
    fun handleNotification(sbn: StatusBarNotification?): Boolean {
        if (sbn == null) {
            return false
        }

        DiagnosticEventManager.logEvent(
            DiagnosticCategory.NOTIFICATION_RECEIVED,
            packageName = sbn.packageName
        )

        val notification = sbn.notification
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification?.channelId
        } else {
            null
        }

        val groupKey = sbn.groupKey ?: notification?.group
        val isGroup = sbn.isGroup || !groupKey.isNullOrBlank()
        val flags = notification?.flags ?: 0
        val isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0

        return processNotification(
            packageName = sbn.packageName ?: "",
            notificationKey = sbn.key ?: "",
            postTime = sbn.postTime,
            channelId = channelId,
            category = notification?.category,
            groupKey = groupKey,
            isGroup = isGroup,
            isGroupSummary = isGroupSummary,
            flags = flags,
            contentExtractor = {
                extractContentFromExtras(notification?.extras)
            }
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        handleNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        val key = sbn.key ?: ""
        SecureLogger.d(TAG, "Notification removed: key=$key, package=${sbn.packageName}")
        if (key.isNotEmpty()) {
            activeNotificationsMap.remove(key)
        }
    }
}
