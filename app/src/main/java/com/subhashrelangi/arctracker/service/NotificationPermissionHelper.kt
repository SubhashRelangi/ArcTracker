package com.subhashrelangi.arctracker.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Helper utility for checking Android Notification Access permission
 * and directing the user to the system Notification Access settings screen.
 */
object NotificationPermissionHelper {

    /**
     * Optional override flag for unit testing permission state transitions.
     * When null, the actual Android system permission state is checked.
     */
    @Volatile
    var permissionOverrideForTesting: Boolean? = null

    /**
     * Checks whether Android Notification Access permission is currently granted for ArcTracker.
     */
    fun isNotificationAccessGranted(context: Context): Boolean {
        permissionOverrideForTesting?.let { return it }

        return try {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            if (enabledPackages.contains(context.packageName)) {
                return true
            }

            // Fallback check for certain OEMs/custom ROMs via Secure Settings
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false

            val serviceComponentName = ComponentName(context, NotificationReaderService::class.java).flattenToString()
            val shortComponentName = ComponentName(context, NotificationReaderService::class.java).flattenToShortString()

            flat.contains(serviceComponentName) ||
                flat.contains(shortComponentName) ||
                flat.contains(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Creates an Intent to navigate directly to the system Notification Access settings screen.
     */
    fun createNotificationAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Safely opens the Android Notification Access settings screen.
     * Includes fallbacks to Application Details and General Settings to ensure no crash on OEM-modified systems.
     */
    fun openNotificationAccessSettings(context: Context): Boolean {
        return try {
            val intent = createNotificationAccessSettingsIntent()
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(appSettingsIntent)
                true
            } catch (e2: Exception) {
                try {
                    val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    true
                } catch (e3: Exception) {
                    false
                }
            }
        }
    }
}
