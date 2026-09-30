package com.subhashrelangi.arctracker.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.subhashrelangi.arctracker.R
import com.subhashrelangi.arctracker.data.Budget
import com.subhashrelangi.arctracker.data.BudgetProgress
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
 * - Completely decoupled from live transaction listener.
 */
class AndroidBudgetAlertNotifier(
    private val context: Context
) : BudgetAlertNotifier {

    companion object {
        const val CHANNEL_ID = "budget_alerts"
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
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permission != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    override fun sendWarningAlert(budget: Budget, progress: BudgetProgress): Boolean {
        if (!hasNotificationPermission()) return false

        try {
            val title = "Budget Warning: ${budget.name}"
            val text = "You've spent ${currencyFormatter.format(progress.spent)} of ${currencyFormatter.format(progress.limit)} (${String.format(Locale.US, "%.0f", progress.percentageUsed)}%)."

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
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

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
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
