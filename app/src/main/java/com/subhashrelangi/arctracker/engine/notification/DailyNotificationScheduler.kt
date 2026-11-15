package com.subhashrelangi.arctracker.engine.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Schedules daily background alarms for Nightly Digests and Cash Check-in prompts.
 *
 * Guarantees:
 * - Uses [AlarmManager.setAndAllowWhileIdle] to fire reliably across Android 7.0 to 15+ without requiring SCHEDULE_EXACT_ALARM permission.
 * - Self-rescheduling: Each alarm automatically schedules the next occurrence for the following day.
 * - Idempotent cancellation and re-scheduling on time or preference changes.
 */
object DailyNotificationScheduler {

    const val ACTION_DAILY_DIGEST = "com.subhashrelangi.arctracker.notification.ACTION_DAILY_DIGEST"
    const val ACTION_CASH_REMINDER = "com.subhashrelangi.arctracker.notification.ACTION_CASH_REMINDER"

    const val REQUEST_CODE_DAILY_DIGEST = 5001
    const val REQUEST_CODE_CASH_REMINDER = 5002

    /**
     * Calculates the next epoch millisecond timestamp for the given hour and minute.
     * If the time has already passed today, rolls over to tomorrow.
     */
    fun calculateNextTriggerMillis(hour: Int, minute: Int, nowMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return cal.timeInMillis
    }

    /**
     * Schedules all enabled daily notifications according to active [NotificationPreferences].
     */
    fun scheduleAll(context: Context) {
        val prefs = NotificationSettingsRepository.getInstance(context).getPreferences()

        if (prefs.isDailyDigestEnabled) {
            scheduleDailyDigest(context, prefs.dailyDigestHour, prefs.dailyDigestMinute)
        } else {
            cancelDailyDigest(context)
        }

        if (prefs.isCashReminderEnabled) {
            scheduleCashReminder(context, prefs.cashReminderHour, prefs.cashReminderMinute)
        } else {
            cancelCashReminder(context)
        }
    }

    fun scheduleDailyDigest(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DailyNotificationReceiver::class.java).apply {
            action = ACTION_DAILY_DIGEST
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE_DAILY_DIGEST, intent, flags)

        val triggerAtMillis = calculateNextTriggerMillis(hour, minute)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancelDailyDigest(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DailyNotificationReceiver::class.java).apply {
            action = ACTION_DAILY_DIGEST
        }
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE_DAILY_DIGEST, intent, flags)
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleCashReminder(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DailyNotificationReceiver::class.java).apply {
            action = ACTION_CASH_REMINDER
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE_CASH_REMINDER, intent, flags)

        val triggerAtMillis = calculateNextTriggerMillis(hour, minute)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancelCashReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DailyNotificationReceiver::class.java).apply {
            action = ACTION_CASH_REMINDER
        }
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE_CASH_REMINDER, intent, flags)
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
