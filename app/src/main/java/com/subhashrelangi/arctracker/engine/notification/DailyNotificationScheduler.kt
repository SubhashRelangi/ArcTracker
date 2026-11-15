package com.subhashrelangi.arctracker.engine.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Schedules daily and weekly background alarms for Digests, Summaries, and Cash prompts.
 *
 * Guarantees:
 * - Uses [AlarmManager.setAndAllowWhileIdle] to fire reliably across Android 7.0 to 15+ without requiring SCHEDULE_EXACT_ALARM permission.
 * - Self-rescheduling: Each alarm automatically schedules the next occurrence.
 * - Idempotent cancellation and re-scheduling on time or preference changes.
 */
object DailyNotificationScheduler {

    const val ACTION_DAILY_DIGEST = "com.subhashrelangi.arctracker.notification.ACTION_DAILY_DIGEST"
    const val ACTION_CASH_REMINDER = "com.subhashrelangi.arctracker.notification.ACTION_CASH_REMINDER"
    const val ACTION_WEEKLY_SUMMARY = "com.subhashrelangi.arctracker.notification.ACTION_WEEKLY_SUMMARY"

    const val REQUEST_CODE_DAILY_DIGEST = 5001
    const val REQUEST_CODE_CASH_REMINDER = 5002
    const val REQUEST_CODE_WEEKLY_SUMMARY = 5003

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
     * Calculates the next epoch millisecond timestamp for a recurring weekly alarm.
     */
    fun calculateNextWeeklyTriggerMillis(
        dayOfWeek: Int = Calendar.SUNDAY,
        hour: Int = 20,
        minute: Int = 0,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.DAY_OF_WEEK, dayOfWeek.coerceIn(1, 7))
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
        return cal.timeInMillis
    }

    /**
     * Schedules all enabled notifications according to active [NotificationPreferences].
     */
    fun scheduleAll(context: Context) {
        val prefs = NotificationSettingsRepository.getInstance(context).getPreferences()

        if (!prefs.isNotificationsMasterEnabled) {
            cancelDailyDigest(context)
            cancelCashReminder(context)
            cancelWeeklySummary(context)
            return
        }

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

        if (prefs.isWeeklySummaryEnabled) {
            scheduleWeeklySummary(context, prefs.weeklySummaryDay, prefs.weeklySummaryHour, prefs.weeklySummaryMinute)
        } else {
            cancelWeeklySummary(context)
        }
    }

    fun scheduleDailyDigest(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DailyNotificationReceiver::class.java).apply {
            action = ACTION_DAILY_DIGEST
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
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
        val flags = PendingIntent.FLAG_NO_CREATE or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
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
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
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
        val flags = PendingIntent.FLAG_NO_CREATE or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE_CASH_REMINDER, intent, flags)
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleWeeklySummary(
        context: Context,
        dayOfWeek: Int = Calendar.SUNDAY,
        hour: Int = 20,
        minute: Int = 0
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DailyNotificationReceiver::class.java).apply {
            action = ACTION_WEEKLY_SUMMARY
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE_WEEKLY_SUMMARY, intent, flags)

        val triggerAtMillis = calculateNextWeeklyTriggerMillis(dayOfWeek, hour, minute)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancelWeeklySummary(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DailyNotificationReceiver::class.java).apply {
            action = ACTION_WEEKLY_SUMMARY
        }
        val flags = PendingIntent.FLAG_NO_CREATE or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE_WEEKLY_SUMMARY, intent, flags)
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
