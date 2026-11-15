package com.subhashrelangi.arctracker.engine.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.subhashrelangi.arctracker.MainActivity
import com.subhashrelangi.arctracker.R

/**
 * Centralized helper for posting local on-device notifications with deep-linking intents.
 */
object NotificationDeliveryHelper {

    fun hasNotificationPermission(context: Context): Boolean {
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

    fun deliverNotification(
        context: Context,
        payload: DailyDigestEvaluator.NotificationPayload
    ): Boolean {
        if (!hasNotificationPermission(context)) return false

        try {
            NotificationChannelManager.createAllChannels(context)

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (payload.targetDestination != null) {
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO, payload.targetDestination)
                }
                if (payload.openAddExpenseCash) {
                    putExtra(MainActivity.EXTRA_OPEN_ADD_EXPENSE_CASH, true)
                }
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                payload.notificationId,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
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
            return true
        } catch (_: Exception) {
            return false
        }
    }
}
