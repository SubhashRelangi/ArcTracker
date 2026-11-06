package com.subhashrelangi.arctracker.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.subhashrelangi.arctracker.BuildConfig

object AppVersion {

    /**
     * Resolves the exact version string of the app installed on the device (e.g. "v2.4").
     */
    fun getFormattedVersion(context: Context): String {
        val rawVersion = getRawVersion(context)
        return if (rawVersion.startsWith("v", ignoreCase = true)) {
            rawVersion
        } else {
            "v$rawVersion"
        }
    }

    /**
     * Resolves the raw version name without leading 'v' (e.g. "2.4").
     */
    fun getRawVersion(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            BuildConfig.VERSION_NAME
        }
    }
}
