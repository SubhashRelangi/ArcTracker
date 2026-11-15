package com.subhashrelangi.arctracker.engine.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-registers scheduled alarms and notification channels on device reboot or app upgrade.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            NotificationChannelManager.createAllChannels(context)
            DailyNotificationScheduler.scheduleAll(context)
        }
    }
}
