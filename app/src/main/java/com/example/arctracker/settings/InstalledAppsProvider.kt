package com.example.arctracker.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Representation of an application discovered on the current Android device.
 * Contains package identifier, display name, and optional icon representation.
 */
data class InstalledAppInfo(
    val packageName: String,
    val displayName: String,
    val icon: Any? = null
)

/**
 * Contract for discovering installed applications and verifying package presence.
 */
interface InstalledAppsProvider {
    /**
     * Retrieves all launchable/user-facing applications installed on the device.
     */
    fun getInstalledApps(): List<InstalledAppInfo>

    /**
     * Checks whether a specific package is currently installed.
     */
    fun isPackageInstalled(packageName: String): Boolean
}

/**
 * Production implementation of [InstalledAppsProvider] using Android's [PackageManager].
 * Safely handles package visibility restrictions, missing metadata, and OS exceptions.
 */
class DefaultInstalledAppsProvider(private val context: Context) : InstalledAppsProvider {

    override fun getInstalledApps(): List<InstalledAppInfo> {
        val result = mutableMapOf<String, InstalledAppInfo>()
        val pm = try {
            context.packageManager
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to obtain PackageManager", e)
            return emptyList()
        } ?: return emptyList()

        // 1. Query launcher applications
        try {
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }

            for (resolveInfo in resolveInfos) {
                val pkg = resolveInfo.activityInfo?.packageName ?: continue
                if (result.containsKey(pkg)) continue

                val label = try {
                    resolveInfo.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                } catch (e: Throwable) {
                    null
                } ?: pkg

                val icon = try {
                    resolveInfo.loadIcon(pm)
                } catch (e: Throwable) {
                    null
                }

                result[pkg] = InstalledAppInfo(
                    packageName = pkg,
                    displayName = label,
                    icon = icon
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error querying launcher activities", e)
        }

        // 2. Also check built-in catalog apps in case of OEM package visibility filters
        for (catalogApp in AppCatalog.allApps) {
            if (!result.containsKey(catalogApp.packageName)) {
                if (isPackageInstalled(catalogApp.packageName)) {
                    val label = try {
                        val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getApplicationInfo(catalogApp.packageName, PackageManager.ApplicationInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getApplicationInfo(catalogApp.packageName, 0)
                        }
                        pm.getApplicationLabel(appInfo)?.toString()?.takeIf { it.isNotBlank() }
                    } catch (e: Throwable) {
                        null
                    } ?: catalogApp.displayName

                    val icon = try {
                        val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getApplicationInfo(catalogApp.packageName, PackageManager.ApplicationInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getApplicationInfo(catalogApp.packageName, 0)
                        }
                        pm.getApplicationIcon(appInfo)
                    } catch (e: Throwable) {
                        null
                    }

                    result[catalogApp.packageName] = InstalledAppInfo(
                        packageName = catalogApp.packageName,
                        displayName = label,
                        icon = icon
                    )
                }
            }
        }

        return result.values.sortedBy { it.displayName.lowercase() }
    }

    override fun isPackageInstalled(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val pm = try {
            context.packageManager
        } catch (e: Throwable) {
            return false
        } ?: return false

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Throwable) {
            false
        }
    }

    companion object {
        private const val TAG = "InstalledAppsProvider"
    }
}

/**
 * In-memory test implementation of [InstalledAppsProvider] for isolated unit and component tests.
 */
class InMemoryInstalledAppsProvider(
    initialApps: List<InstalledAppInfo> = emptyList()
) : InstalledAppsProvider {

    private val installed = initialApps.toMutableList()

    override fun getInstalledApps(): List<InstalledAppInfo> = synchronized(installed) {
        installed.toList()
    }

    override fun isPackageInstalled(packageName: String): Boolean = synchronized(installed) {
        installed.any { it.packageName.equals(packageName, ignoreCase = true) }
    }

    fun installApp(app: InstalledAppInfo) = synchronized(installed) {
        if (installed.none { it.packageName.equals(app.packageName, ignoreCase = true) }) {
            installed.add(app)
        }
    }

    fun uninstallApp(packageName: String) = synchronized(installed) {
        installed.removeAll { it.packageName.equals(packageName, ignoreCase = true) }
    }

    fun clear() = synchronized(installed) {
        installed.clear()
    }
}
