package com.subhashrelangi.arctracker.engine.budget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.subhashrelangi.arctracker.MainActivity
import com.subhashrelangi.arctracker.R
import com.subhashrelangi.arctracker.data.Budget
import com.subhashrelangi.arctracker.data.BudgetProgress
import com.subhashrelangi.arctracker.engine.notification.NotificationChannelManager
import com.subhashrelangi.arctracker.engine.notification.NotificationDeliveryHelper
import java.text.NumberFormat
import java.util.Locale

/**
 * Interface abstraction for delivering budget alert notifications.
 * Allows pure, zero-dependency testing in JVM environments.
 */
interface BudgetAlertNotifier {
    fun sendWarningAlert(budget: Budget, progress: BudgetProgress): Boolean
    fun sendExceededAlert(budget: Budget, progress: BudgetProgress): Boolean
}

/**
 * Android system notification delivery implementation for Budget Alerts.
 *
 * Guarantees:
 * - Creates and manages dedicated "budget_alerts" notification channel.
 * - Safely handles missing POST_NOTIFICATIONS permission without crashing.
 * - Respects system notifications disabled state.
 * - Deep links directly to the Budgets dashboard upon tap.
 * - Completely decoupled from live transaction listener.
 */
class AndroidBudgetAlertNotifier(
    private val context: Context
) : BudgetAlertNotifier {

    companion object {
        const val CHANNEL_ID = NotificationChannelManager.CHANNEL_BUDGET_ALERTS
        const val CHANNEL_NAME = "Budget Alerts"
        const val CHANNEL_DESC = "Notifications when your spending approaches or exceeds your configured budget limits."
    }

    private val currencyFormatter by lazy {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }

    init {
        NotificationChannelManager.createAllChannels(context)
    }

    private fun hasNotificationPermission(): Boolean {
        return NotificationDeliveryHelper.hasNotificationPermission(context)
    }

    override fun sendWarningAlert(budget: Budget, progress: BudgetProgress): Boolean {
        if (!hasNotificationPermission()) return false

        try {
            val title = "Budget Warning: ${budget.name}"
            val text = "You've spent ${currencyFormatter.format(progress.spent)} of ${currencyFormatter.format(progress.limit)} (${String.format(Locale.US, "%.0f", progress.percentageUsed)}%)."

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_NAVIGATE_TO, "Budgets")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                budget.id.hashCode(),
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(
                budget.id.hashCode(),
                notification
            )
            return true
        } catch (e: Exception) {
            return false
        }
    }

    override fun sendExceededAlert(budget: Budget, progress: BudgetProgress): Boolean {
        if (!hasNotificationPermission()) return false

        try {
            val title = "Budget Exceeded: ${budget.name}"
            val text = "Alert! You've exceeded your budget of ${currencyFormatter.format(progress.limit)} (Spent: ${currencyFormatter.format(progress.spent)})."

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_NAVIGATE_TO, "Budgets")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                budget.id.hashCode() + 1,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            NotificationManagerCompat.from(context).notify(
                budget.id.hashCode() + 1,
                notification
            )
            return true
        } catch (e: Exception) {
            return false
        }
    }
}
