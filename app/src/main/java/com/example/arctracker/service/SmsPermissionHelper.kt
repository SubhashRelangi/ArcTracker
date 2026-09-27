package com.example.arctracker.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Helper utility for checking Android READ_SMS permission (Milestone 3A).
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
}
