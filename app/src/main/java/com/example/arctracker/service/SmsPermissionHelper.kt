package com.example.arctracker.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.arctracker.settings.MonitoringSettingsRepository
import java.util.Calendar

/**
 * Helper utility for checking Android READ_SMS permission, managing initial setup state,
 * and directing user to App Settings (Milestone 3A / Milestone 5).
 * Follows the pattern established by [NotificationPermissionHelper].
 */
object SmsPermissionHelper {

    /**
     * Optional override flag for unit testing permission state transitions.
     * When null, the actual Android system permission state is checked.
     */
    @Volatile
    var permissionOverrideForTesting: Boolean? = null

    /**
     * Checks whether Android READ_SMS permission is currently granted for ArcTracker.
     */
    fun isSmsPermissionGranted(context: Context): Boolean {
        permissionOverrideForTesting?.let { return it }

        return try {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Safely opens the Android application details settings screen for ArcTracker.
     */
    fun openAppSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Calculates the exact start boundary timestamp for 3 calendar months ago relative to current date/time.
     */
    fun calculateThreeMonthsAgoTimestamp(anchorMillis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = anchorMillis
            add(Calendar.MONTH, -3)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * Checks whether the first-time 3-month historical SMS import has been completed.
     */
    fun isInitialImportCompleted(context: Context): Boolean {
        return MonitoringSettingsRepository.getInstance(context).isInitialSmsImportCompleted()
    }

    /**
     * Sets whether the first-time 3-month historical SMS import has been completed.
     */
    fun setInitialImportCompleted(context: Context, completed: Boolean) {
        MonitoringSettingsRepository.getInstance(context).setInitialSmsImportCompleted(completed)
    }
}
