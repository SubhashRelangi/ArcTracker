package com.subhashrelangi.arctracker.engine.notification

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.subhashrelangi.arctracker.MainActivity
import com.subhashrelangi.arctracker.R
import com.subhashrelangi.arctracker.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles incoming scheduled daily alarms, evaluates financial state, and posts local notifications.
 */
class DailyNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_NAVIGATE_TO = "extra_navigate_to"
        const val EXTRA_OPEN_ADD_EXPENSE_CASH = "extra_open_add_expense_cash"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // Ensure notification channels exist before posting
        NotificationChannelManager.createAllChannels(context)

        when (action) {
            DailyNotificationScheduler.ACTION_DAILY_DIGEST -> handleDailyDigest(context)
            DailyNotificationScheduler.ACTION_CASH_REMINDER -> handleCashReminder(context)
        }
    }

    private fun handleDailyDigest(context: Context) {
        val repo = NotificationSettingsRepository.getInstance(context)
        val prefs = repo.getPreferences()

        // Reschedule for tomorrow
        if (prefs.isDailyDigestEnabled) {
            DailyNotificationScheduler.scheduleDailyDigest(context, prefs.dailyDigestHour, prefs.dailyDigestMinute)
        }

        if (!prefs.isDailyDigestEnabled || !hasNotificationPermission(context)) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val expenses = db.expenseDao().getAllExpensesList()
                val categories = db.transactionCategoryDao().getAll()

                val summary = DailyDigestEvaluator.evaluateDailySummary(
                    expenses = expenses,
                    categories = categories,
                    dailyLimitAmount = prefs.dailySpendLimit
                )

                val payload = DailyDigestEvaluator.buildDailyDigestPayload(summary)
                deliverNotification(context, payload)
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleCashReminder(context: Context) {
        val repo = NotificationSettingsRepository.getInstance(context)
        val prefs = repo.getPreferences()

        // Reschedule for tomorrow
        if (prefs.isCashReminderEnabled) {
            DailyNotificationScheduler.scheduleCashReminder(context, prefs.cashReminderHour, prefs.cashReminderMinute)
        }

        if (!prefs.isCashReminderEnabled || !hasNotificationPermission(context)) {
            return
        }

        val payload = DailyDigestEvaluator.buildCashReminderPayload()
        deliverNotification(context, payload)
    }

    private fun deliverNotification(context: Context, payload: DailyDigestEvaluator.NotificationPayload) {
        if (!hasNotificationPermission(context)) return

        try {
            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NAVIGATE_TO, payload.targetDestination)
                if (payload.openAddExpenseCash) {
                    putExtra(EXTRA_OPEN_ADD_EXPENSE_CASH, true)
                }
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                payload.notificationId,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, payload.channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(payload.title)
                .setContentText(payload.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(payload.bigText))
                .setPriority(
                    if (payload.channelId == NotificationChannelManager.CHANNEL_BUDGET_ALERTS)
                        NotificationCompat.PRIORITY_HIGH
                    else
                        NotificationCompat.PRIORITY_DEFAULT
                )
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(payload.notificationId, notification)
        } catch (_: Exception) {
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            return permission == PackageManager.PERMISSION_GRANTED
        }
        return true
    }
}
