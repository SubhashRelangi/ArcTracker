package com.subhashrelangi.arctracker.engine.notification

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Model representing user-configured notification preferences.
 */
data class NotificationPreferences(
    val isNotificationsMasterEnabled: Boolean = true,
    val isDailyDigestEnabled: Boolean = true,
    val dailyDigestHour: Int = 21,       // 9:30 PM default
    val dailyDigestMinute: Int = 30,
    val isWeeklySummaryEnabled: Boolean = true,
    val weeklySummaryDay: Int = 1,       // Calendar.SUNDAY
    val weeklySummaryHour: Int = 20,     // 8:00 PM default
    val weeklySummaryMinute: Int = 0,
    val isCashReminderEnabled: Boolean = true,
    val cashReminderHour: Int = 20,      // 8:30 PM default
    val cashReminderMinute: Int = 30,
    val dailySpendLimit: Double? = null,
    val isBudgetAlertsEnabled: Boolean = true,
    val isReviewReminderEnabled: Boolean = true,
    val isAtmFollowUpEnabled: Boolean = true,
    val isHighValueAlertEnabled: Boolean = true,
    val highValueThreshold: Double = 10000.0
)

/**
 * Thread-safe SharedPreferences repository for ArcTracker notification preferences.
 */
class NotificationSettingsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _preferencesFlow = MutableStateFlow(loadPreferences())
    val preferencesFlow: StateFlow<NotificationPreferences> = _preferencesFlow.asStateFlow()

    fun getPreferences(): NotificationPreferences {
        return _preferencesFlow.value
    }

    fun setMasterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
        updateFlow()
    }

    fun setDailyDigestEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DAILY_DIGEST_ENABLED, enabled).apply()
        updateFlow()
    }

    fun setDailyDigestTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_DAILY_DIGEST_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_DAILY_DIGEST_MINUTE, minute.coerceIn(0, 59))
            .apply()
        updateFlow()
    }

    fun setWeeklySummaryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEEKLY_SUMMARY_ENABLED, enabled).apply()
        updateFlow()
    }

    fun setWeeklySummaryTime(dayOfWeek: Int, hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_WEEKLY_SUMMARY_DAY, dayOfWeek.coerceIn(1, 7))
            .putInt(KEY_WEEKLY_SUMMARY_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_WEEKLY_SUMMARY_MINUTE, minute.coerceIn(0, 59))
            .apply()
        updateFlow()
    }

    fun setCashReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CASH_REMINDER_ENABLED, enabled).apply()
        updateFlow()
    }

    fun setCashReminderTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_CASH_REMINDER_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_CASH_REMINDER_MINUTE, minute.coerceIn(0, 59))
            .apply()
        updateFlow()
    }

    fun setDailySpendLimit(limit: Double?) {
        if (limit != null && limit > 0.0) {
            prefs.edit().putFloat(KEY_DAILY_SPEND_LIMIT, limit.toFloat()).apply()
        } else {
            prefs.edit().remove(KEY_DAILY_SPEND_LIMIT).apply()
        }
        updateFlow()
    }

    fun setBudgetAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BUDGET_ALERTS_ENABLED, enabled).apply()
        updateFlow()
    }

    fun setReviewReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REVIEW_REMINDER_ENABLED, enabled).apply()
        updateFlow()
    }

    fun setAtmFollowUpEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ATM_FOLLOW_UP_ENABLED, enabled).apply()
        updateFlow()
    }

    fun setHighValueAlertEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIGH_VALUE_ALERT_ENABLED, enabled).apply()
        updateFlow()
    }

    fun setHighValueThreshold(threshold: Double) {
        if (threshold > 0.0) {
            prefs.edit().putFloat(KEY_HIGH_VALUE_THRESHOLD, threshold.toFloat()).apply()
            updateFlow()
        }
    }

    /**
     * Enables all notification types simultaneously.
     */
    fun enableAllNotifications() {
        prefs.edit()
            .putBoolean(KEY_MASTER_ENABLED, true)
            .putBoolean(KEY_DAILY_DIGEST_ENABLED, true)
            .putBoolean(KEY_WEEKLY_SUMMARY_ENABLED, true)
            .putBoolean(KEY_CASH_REMINDER_ENABLED, true)
            .putBoolean(KEY_BUDGET_ALERTS_ENABLED, true)
            .putBoolean(KEY_REVIEW_REMINDER_ENABLED, true)
            .putBoolean(KEY_ATM_FOLLOW_UP_ENABLED, true)
            .putBoolean(KEY_HIGH_VALUE_ALERT_ENABLED, true)
            .apply()
        updateFlow()
    }

    /**
     * Disables all notification types simultaneously.
     */
    fun disableAllNotifications() {
        prefs.edit()
            .putBoolean(KEY_MASTER_ENABLED, false)
            .putBoolean(KEY_DAILY_DIGEST_ENABLED, false)
            .putBoolean(KEY_WEEKLY_SUMMARY_ENABLED, false)
            .putBoolean(KEY_CASH_REMINDER_ENABLED, false)
            .putBoolean(KEY_BUDGET_ALERTS_ENABLED, false)
            .putBoolean(KEY_REVIEW_REMINDER_ENABLED, false)
            .putBoolean(KEY_ATM_FOLLOW_UP_ENABLED, false)
            .putBoolean(KEY_HIGH_VALUE_ALERT_ENABLED, false)
            .apply()
        updateFlow()
    }

    private fun loadPreferences(): NotificationPreferences {
        val dailyLimitStored = if (prefs.contains(KEY_DAILY_SPEND_LIMIT)) {
            prefs.getFloat(KEY_DAILY_SPEND_LIMIT, 0f).toDouble().takeIf { it > 0.0 }
        } else null

        return NotificationPreferences(
            isNotificationsMasterEnabled = prefs.getBoolean(KEY_MASTER_ENABLED, true),
            isDailyDigestEnabled = prefs.getBoolean(KEY_DAILY_DIGEST_ENABLED, true),
            dailyDigestHour = prefs.getInt(KEY_DAILY_DIGEST_HOUR, 21),
            dailyDigestMinute = prefs.getInt(KEY_DAILY_DIGEST_MINUTE, 30),
            isWeeklySummaryEnabled = prefs.getBoolean(KEY_WEEKLY_SUMMARY_ENABLED, true),
            weeklySummaryDay = prefs.getInt(KEY_WEEKLY_SUMMARY_DAY, 1),
            weeklySummaryHour = prefs.getInt(KEY_WEEKLY_SUMMARY_HOUR, 20),
            weeklySummaryMinute = prefs.getInt(KEY_WEEKLY_SUMMARY_MINUTE, 0),
            isCashReminderEnabled = prefs.getBoolean(KEY_CASH_REMINDER_ENABLED, true),
            cashReminderHour = prefs.getInt(KEY_CASH_REMINDER_HOUR, 20),
            cashReminderMinute = prefs.getInt(KEY_CASH_REMINDER_MINUTE, 30),
            dailySpendLimit = dailyLimitStored,
            isBudgetAlertsEnabled = prefs.getBoolean(KEY_BUDGET_ALERTS_ENABLED, true),
            isReviewReminderEnabled = prefs.getBoolean(KEY_REVIEW_REMINDER_ENABLED, true),
            isAtmFollowUpEnabled = prefs.getBoolean(KEY_ATM_FOLLOW_UP_ENABLED, true),
            isHighValueAlertEnabled = prefs.getBoolean(KEY_HIGH_VALUE_ALERT_ENABLED, true),
            highValueThreshold = prefs.getFloat(KEY_HIGH_VALUE_THRESHOLD, 10000.0f).toDouble()
        )
    }

    private fun updateFlow() {
        _preferencesFlow.value = loadPreferences()
    }

    companion object {
        private const val PREFS_NAME = "arctracker_notification_prefs"
        private const val KEY_MASTER_ENABLED = "notifications_master_enabled"
        private const val KEY_DAILY_DIGEST_ENABLED = "daily_digest_enabled"
        private const val KEY_DAILY_DIGEST_HOUR = "daily_digest_hour"
        private const val KEY_DAILY_DIGEST_MINUTE = "daily_digest_minute"
        private const val KEY_WEEKLY_SUMMARY_ENABLED = "weekly_summary_enabled"
        private const val KEY_WEEKLY_SUMMARY_DAY = "weekly_summary_day"
        private const val KEY_WEEKLY_SUMMARY_HOUR = "weekly_summary_hour"
        private const val KEY_WEEKLY_SUMMARY_MINUTE = "weekly_summary_minute"
        private const val KEY_CASH_REMINDER_ENABLED = "cash_reminder_enabled"
        private const val KEY_CASH_REMINDER_HOUR = "cash_reminder_hour"
        private const val KEY_CASH_REMINDER_MINUTE = "cash_reminder_minute"
        private const val KEY_DAILY_SPEND_LIMIT = "daily_spend_limit"
        private const val KEY_BUDGET_ALERTS_ENABLED = "budget_alerts_enabled"
        private const val KEY_REVIEW_REMINDER_ENABLED = "review_reminder_enabled"
        private const val KEY_ATM_FOLLOW_UP_ENABLED = "atm_follow_up_enabled"
        private const val KEY_HIGH_VALUE_ALERT_ENABLED = "high_value_alert_enabled"
        private const val KEY_HIGH_VALUE_THRESHOLD = "high_value_threshold"

        @Volatile
        private var instance: NotificationSettingsRepository? = null

        fun getInstance(context: Context): NotificationSettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: NotificationSettingsRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
