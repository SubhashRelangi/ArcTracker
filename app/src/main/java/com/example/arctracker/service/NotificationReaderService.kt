package com.example.arctracker.service

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.launch
import java.util.Collections

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

        @Volatile
        var lastCapturedNotification: CapturedNotificationInfo? = null
            internal set

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
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.d(TAG, "NotificationReaderService connected.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        Log.d(TAG, "NotificationReaderService disconnected.")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        isConnected = false
        Log.d(TAG, "NotificationReaderService destroyed.")
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
            Log.w(TAG, "Notification received but Notification Access permission is not granted. Discarding.")
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

            // Diagnostic logging of complete captured structure
            Log.d(
                TAG,
                "Notification captured [isUpdate=$isUpdate]: " +
                    "package=$packageName, key=$notificationKey, postTime=$postTime, " +
                    "title=$title, text=$text, bigText=$bigText, subText=$subText, " +
                    "summaryText=$summaryText, infoText=$infoText, textLines=$textLines, " +
                    "category=$category, channelId=$channelId, groupKey=$groupKey, " +
                    "isGroup=$isGroup, isGroupSummary=$isGroupSummary, flags=$flags"
            )

            if (notificationKey.isNotEmpty()) {
                activeNotificationsMap[notificationKey] = captured
            }

            lastCapturedNotification = captured
            notificationListener?.invoke(captured)

            // Step 8: Trigger end-to-end transaction pipeline safely on background IO thread
            serviceScope.launch {
                try {
                    TransactionPersistenceManager.processCapturedNotification(applicationContext, captured)
                } catch (e: Exception) {
                    Log.e(TAG, "Safe catch: pipeline failure in NotificationReaderService", e)
                }
            }

            captured
        } catch (e: Exception) {
            Log.e(TAG, "Error recording captured notification in NotificationReaderService", e)
            null
        }
    }

    /**
     * Handles an incoming notification safely with independent permission verification.
     * Extracts all available fields from [StatusBarNotification] and its extras.
     *
     * @return true if notification was captured and logged; false if ignored or permission missing.
     */
    fun handleNotification(sbn: StatusBarNotification?): Boolean {
        if (sbn == null) {
            return false
        }

        // Independently verify that Notification Access permission is currently granted before any processing
        if (!NotificationPermissionHelper.isNotificationAccessGranted(this)) {
            Log.w(TAG, "Notification received but Notification Access permission is not granted. Discarding.")
            return false
        }

        return try {
            val packageName = sbn.packageName ?: ""
            val notificationKey = sbn.key ?: ""
            val postTime = sbn.postTime
            val notification = sbn.notification
            val extras = notification?.extras

            val title = extractTextField(extras, Notification.EXTRA_TITLE)
                ?: extractTextField(extras, "android.title.big")
            val text = extractTextField(extras, Notification.EXTRA_TEXT)
            val bigText = extractTextField(extras, Notification.EXTRA_BIG_TEXT)
            val subText = extractTextField(extras, Notification.EXTRA_SUB_TEXT)
            val summaryText = extractTextField(extras, Notification.EXTRA_SUMMARY_TEXT)
            val infoText = extractTextField(extras, Notification.EXTRA_INFO_TEXT)
            val textLines = extractTextLines(extras)

            val category = notification?.category
            val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notification?.channelId
            } else {
                null
            }

            val groupKey = sbn.groupKey ?: notification?.group
            val isGroup = sbn.isGroup || !groupKey.isNullOrBlank()
            val flags = notification?.flags ?: 0
            val isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0

            recordCapturedNotification(
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
                flags = flags
            ) != null
        } catch (e: Exception) {
            Log.e(TAG, "Error handling notification in NotificationReaderService", e)
            false
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        handleNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        val key = sbn.key ?: ""
        Log.d(TAG, "Notification removed: key=$key, package=${sbn.packageName}")
        if (key.isNotEmpty()) {
            activeNotificationsMap.remove(key)
        }
    }
}
