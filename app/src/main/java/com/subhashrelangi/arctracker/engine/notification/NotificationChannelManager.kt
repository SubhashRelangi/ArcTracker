package com.subhashrelangi.arctracker.engine.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Centralized manager for creating and maintaining Android Notification Channels in ArcTracker.
 *
 * Guarantees:
 * - 100% on-device local notifications (zero internet telemetry).
 * - Distinct, user-configurable channels so users can control notification types in system settings.
 * - Idempotent channel registration safe to call on every app launch or receiver invocation.
 */
object NotificationChannelManager {

    const val CHANNEL_DAILY_DIGEST = "daily_digests"
    const val CHANNEL_BUDGET_ALERTS = "budget_alerts"
    const val CHANNEL_TRANSACTION_REVIEWS = "transaction_reviews"

    /**
     * Creates all notification channels required by ArcTracker on Android 8.0+ (API 26+).
     */
    fun createAllChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        // 1. Daily Summaries & Reminders
        val dailyChannel = NotificationChannel(
            CHANNEL_DAILY_DIGEST,
            "Daily Summaries & Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Nightly spending digests, zero-spend check-ins, and liquid cash logging prompts."
            enableVibration(true)
        }

        // 2. Budget & Velocity Limit Alerts
        val budgetChannel = NotificationChannel(
            CHANNEL_BUDGET_ALERTS,
            "Budget & Limit Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when spending reaches 80%, exceeds monthly budgets, or crosses daily limits."
            enableVibration(true)
        }

        // 3. Transaction Reviews & Smart Prompts
        val reviewChannel = NotificationChannel(
            CHANNEL_TRANSACTION_REVIEWS,
            "Transaction Review & Smart Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Digest of unconfirmed transactions, ATM withdrawal follow-ups, and high-value spends."
            enableVibration(true)
        }

        manager.createNotificationChannels(listOf(dailyChannel, budgetChannel, reviewChannel))
    }
}
