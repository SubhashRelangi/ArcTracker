package com.subhashrelangi.arctracker.engine.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

        if (!prefs.isDailyDigestEnabled || !NotificationDeliveryHelper.hasNotificationPermission(context)) {
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
                NotificationDeliveryHelper.deliverNotification(context, payload)

                // Dispatch Pending Review Digest if unreviewed transactions exist
                if (prefs.isReviewReminderEnabled) {
                    val pendingCount = db.expenseDao().getPendingExpensesList().size
                    if (pendingCount > 0) {
                        val reviewPayload = SmartPromptEvaluator.buildPendingReviewDigest(pendingCount)
                        if (reviewPayload != null) {
                            NotificationDeliveryHelper.deliverNotification(context, reviewPayload)
                        }
                    }
                }
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

        if (!prefs.isCashReminderEnabled || !NotificationDeliveryHelper.hasNotificationPermission(context)) {
            return
        }

        val payload = DailyDigestEvaluator.buildCashReminderPayload()
        NotificationDeliveryHelper.deliverNotification(context, payload)
    }
}
