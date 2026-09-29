package com.subhashrelangi.arctracker.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Authoritative repository interface for managing ArcTracker's monitoring settings.
 * Single source of truth for global monitoring status, enabled package sets, and user-added apps.
 */
interface MonitoringSettingsRepository {

    /**
     * Retrieves the current snapshot of monitoring settings.
     */
    fun getSettings(): MonitoringSettings

    /**
     * Sets the global master monitoring switch.
     */
    fun setGlobalEnabled(enabled: Boolean)

    /**
     * Enables or disables monitoring for a specific application package.
     */
    fun setAppEnabled(packageName: String, enabled: Boolean)

    /**
     * Sets the entire enabled package set at once.
     */
    fun setEnabledPackages(packages: Set<String>)

    /**
     * Returns whether the given package is enabled in settings.
     */
    fun isAppEnabled(packageName: String): Boolean = getSettings().isAppEnabled(packageName)

    /**
     * Returns whether monitoring is currently active for the given package
     * (both global switch and package switch must be enabled).
     */
    fun isMonitoringActive(packageName: String): Boolean = getSettings().isMonitoringActive(packageName)

    /**
     * Resets monitoring settings back to factory defaults.
     */
    fun resetToDefaults()

    /**
     * Retrieves the full catalog of built-in supported applications.
     */
    fun getCatalog(): List<SupportedApp> = AppCatalog.allApps

    /**
     * Retrieves all user-added applications configured in settings.
     * Guaranteed deterministic deduplication by package name.
     */
    fun getUserAddedApps(): List<SupportedApp> = emptyList()

    /**
     * Adds and persists a user-defined application.
     * Automatically enables the newly added app by default without mutating globalEnabled.
     * Duplicate packages (matching built-in or existing user apps) are rejected.
     *
     * @return true if added successfully, false if duplicate or invalid.
     */
    fun addUserApp(app: SupportedApp): Boolean = false

    /**
     * Updates an existing user-added application definition (e.g. changing its category).
     * Built-in AppCatalog applications cannot be modified.
     * Package name remains immutable.
     * Enabled package state and global monitoring remain untouched.
     *
     * @return true if updated successfully, false if app not found or built-in.
     */
    fun updateUserApp(app: SupportedApp): Boolean = false

    /**
     * Removes an existing user-added application from ArcTracker's configuration.
     * Built-in AppCatalog applications cannot be removed.
     * Removes all user-added definitions matching [packageName] (case-insensitive).
     * Removes [packageName] from enabledPackages set.
     * Preserves globalEnabled and all other applications.
     *
     * @return true if successfully removed and persisted, false if package is blank, built-in, or not found.
     */
    fun removeUserApp(packageName: String): Boolean = false

    /**
     * Returns all configured apps (built-in catalog + persisted user-added apps).
     * Guaranteed no duplicates by package name.
     */
    fun getAllConfiguredApps(): List<SupportedApp> = getCatalog()

    /**
     * Reconciles monitoring activity by verifying that global monitoring is ON,
     * the package switch is ON, and the package is physically installed on the device.
     */
    fun isPackageEffectivelyMonitored(packageName: String, isInstalled: Boolean): Boolean {
        return isMonitoringActive(packageName) && isInstalled
    }

    /**
     * Returns the set of packages that are both enabled for monitoring and currently installed.
     */
    fun getEffectiveMonitoredPackages(installedPackages: Set<String>): Set<String> {
        return getSettings().enabledPackages.intersect(installedPackages)
    }

    /**
     * Returns whether notification tracking is enabled.
     */
    fun isNotificationTrackingEnabled(): Boolean = getSettings().isNotificationTrackingEnabled

    /**
     * Sets whether notification tracking is enabled.
     */
    fun setNotificationTrackingEnabled(enabled: Boolean) {}

    /**
     * Returns whether SMS tracking is enabled.
     */
    fun isSmsTrackingEnabled(): Boolean = true

    /**
     * Sets whether SMS tracking is enabled.
     */
    fun setSmsTrackingEnabled(enabled: Boolean) {}

    /**
     * Returns whether the initial historical SMS import has been completed.
     */
    fun isInitialSmsImportCompleted(): Boolean = false

    /**
     * Sets whether the initial historical SMS import has been completed.
     */
    fun setInitialSmsImportCompleted(completed: Boolean) {}

    companion object {
        const val PREFS_NAME = "ArcTrackerPrefs"
        const val KEY_GLOBAL_ENABLED = "monitoring_global_enabled"
        const val KEY_ENABLED_PACKAGES = "monitored_packages"
        const val KEY_USER_ADDED_APPS = "user_added_apps"
        const val LEGACY_KEY_AUTO_TRACKING = "isAutoTrackingEnabled"
        const val KEY_NOTIFICATION_TRACKING_ENABLED = "isNotificationTrackingEnabled"
        const val KEY_SMS_TRACKING_ENABLED = "isSmsTrackingEnabled"
        const val KEY_INITIAL_SMS_IMPORT_COMPLETED = "initial_sms_import_completed"

        @Volatile
        private var INSTANCE: MonitoringSettingsRepository? = null

        /**
         * Returns the singleton production repository backed by SharedPreferences.
         */
        fun getInstance(context: Context): MonitoringSettingsRepository {
            return INSTANCE ?: synchronized(this) {
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val instance = SharedPreferencesMonitoringSettingsRepository(prefs)
                INSTANCE = instance
                instance
            }
        }

        /**
         * Creates a repository backed by a specific [SharedPreferences] instance.
         */
        fun create(sharedPreferences: SharedPreferences): MonitoringSettingsRepository {
            return SharedPreferencesMonitoringSettingsRepository(sharedPreferences)
        }

        /**
         * Creates an isolated in-memory repository for unit and component testing.
         */
        fun createInMemory(
            initialGlobalEnabled: Boolean = true,
            initialEnabledPackages: Set<String> = AppCatalog.defaultEnabledPackages,
            initialNotificationTrackingEnabled: Boolean = true,
            initialSmsTrackingEnabled: Boolean = true
        ): MonitoringSettingsRepository {
            return InMemoryMonitoringSettingsRepository(
                initialGlobalEnabled,
                initialEnabledPackages,
                initialNotificationTrackingEnabled,
                initialSmsTrackingEnabled
            )
        }
    }
}

/**
 * Pure Kotlin serializer for [SupportedApp] persistence in SharedPreferences.
 * Safe across local JVM unit tests and real Android devices with zero external dependencies.
 */
object SupportedAppSerializer {
    fun serialize(app: SupportedApp): String {
        fun escape(s: String) = s.replace("\\", "\\\\").replace("|", "\\|")
        return "${escape(app.packageName)}|${escape(app.displayName)}|${escape(app.description)}|${escape(app.category.id)}"
    }

    fun deserialize(raw: String): SupportedApp? {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        var escaping = false
        for (c in raw) {
            if (escaping) {
                sb.append(c)
                escaping = false
            } else if (c == '\\') {
                escaping = true
            } else if (c == '|') {
                parts.add(sb.toString())
                sb.setLength(0)
            } else {
                sb.append(c)
            }
        }
        parts.add(sb.toString())
        if (parts.size < 4) return null
        val pkg = parts[0].trim()
        if (pkg.isBlank()) return null
        val name = parts[1].trim().ifBlank { pkg }
        val desc = parts[2].trim()
        val catId = parts[3].trim()
        val cat = AppCategory.fromId(catId) ?: AppCategory.UPI_PAYMENT
        return SupportedApp(
            packageName = pkg,
            displayName = name,
            description = desc,
            category = cat,
            defaultEnabled = true
        )
    }
}

/**
 * SharedPreferences-backed production implementation of [MonitoringSettingsRepository].
 * Preserves existing settings, provides backward compatibility, and persists user-added apps.
 */
class SharedPreferencesMonitoringSettingsRepository(
    private val prefs: SharedPreferences
) : MonitoringSettingsRepository {

    private val lock = Any()

    override fun getSettings(): MonitoringSettings = synchronized(lock) {
        val globalEnabled = if (prefs.contains(MonitoringSettingsRepository.KEY_GLOBAL_ENABLED)) {
            prefs.getBoolean(MonitoringSettingsRepository.KEY_GLOBAL_ENABLED, true)
        } else if (prefs.contains(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING)) {
            // Respect existing installation's auto-tracking preference
            prefs.getBoolean(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING, true)
        } else {
            true
        }

        val enabledPackages = if (prefs.contains(MonitoringSettingsRepository.KEY_ENABLED_PACKAGES)) {
            prefs.getStringSet(MonitoringSettingsRepository.KEY_ENABLED_PACKAGES, emptySet())
                ?: emptySet()
        } else {
            AppCatalog.defaultEnabledPackages
        }

        val isNotificationTrackingEnabled = prefs.getBoolean(
            MonitoringSettingsRepository.KEY_NOTIFICATION_TRACKING_ENABLED,
            true
        )

        MonitoringSettings(
            globalEnabled = globalEnabled,
            enabledPackages = enabledPackages.toSet(),
            isNotificationTrackingEnabled = isNotificationTrackingEnabled
        )
    }

    override fun isNotificationTrackingEnabled(): Boolean = synchronized(lock) {
        prefs.getBoolean(MonitoringSettingsRepository.KEY_NOTIFICATION_TRACKING_ENABLED, true)
    }

    override fun setNotificationTrackingEnabled(enabled: Boolean): Unit = synchronized(lock) {
        prefs.edit()
            .putBoolean(MonitoringSettingsRepository.KEY_NOTIFICATION_TRACKING_ENABLED, enabled)
            .commit()
    }

    override fun isSmsTrackingEnabled(): Boolean = synchronized(lock) {
        prefs.getBoolean(MonitoringSettingsRepository.KEY_SMS_TRACKING_ENABLED, true)
    }

    override fun setSmsTrackingEnabled(enabled: Boolean): Unit = synchronized(lock) {
        prefs.edit()
            .putBoolean(MonitoringSettingsRepository.KEY_SMS_TRACKING_ENABLED, enabled)
            .commit()
    }

    override fun setGlobalEnabled(enabled: Boolean): Unit = synchronized(lock) {
        prefs.edit()
            .putBoolean(MonitoringSettingsRepository.KEY_GLOBAL_ENABLED, enabled)
            .putBoolean(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING, enabled)
            .commit()
    }

    override fun setAppEnabled(packageName: String, enabled: Boolean): Unit = synchronized(lock) {
        val currentPackages = getSettings().enabledPackages.toMutableSet()
        if (enabled) {
            currentPackages.add(packageName)
        } else {
            currentPackages.remove(packageName)
        }
        prefs.edit()
            .putStringSet(MonitoringSettingsRepository.KEY_ENABLED_PACKAGES, currentPackages)
            .apply()
    }

    override fun setEnabledPackages(packages: Set<String>): Unit = synchronized(lock) {
        prefs.edit()
            .putStringSet(MonitoringSettingsRepository.KEY_ENABLED_PACKAGES, packages.toSet())
            .apply()
    }

    override fun getUserAddedApps(): List<SupportedApp> = synchronized(lock) {
        val rawSet = prefs.getStringSet(MonitoringSettingsRepository.KEY_USER_ADDED_APPS, emptySet()) ?: emptySet()
        val list = rawSet.mapNotNull { SupportedAppSerializer.deserialize(it) }
        val seen = mutableSetOf<String>()
        val deduplicated = mutableListOf<SupportedApp>()
        for (app in list) {
            val lowerPkg = app.packageName.lowercase()
            if (seen.add(lowerPkg)) {
                deduplicated.add(app)
            } else {
                try {
                    Log.w("MonitoringSettingsRepo", "Duplicate user-added app found for ${app.packageName}; ignoring duplicate")
                } catch (e: Throwable) {}
            }
        }
        deduplicated
    }

    override fun addUserApp(app: SupportedApp): Boolean = synchronized(lock) {
        if (app.packageName.isBlank()) return false

        // Duplicate check against built-in AppCatalog
        if (AppCatalog.containsPackage(app.packageName)) {
            return false
        }

        // Duplicate check against existing user-added apps
        val existingUserApps = getUserAddedApps().toMutableList()
        if (existingUserApps.any { it.packageName.equals(app.packageName, ignoreCase = true) }) {
            return false
        }

        existingUserApps.add(app)
        val serializedSet = existingUserApps.map { SupportedAppSerializer.serialize(it) }.toSet()

        // Enable newly added app by default in enabledPackages without touching globalEnabled
        val currentPackages = getSettings().enabledPackages.toMutableSet()
        currentPackages.add(app.packageName)

        return try {
            prefs.edit()
                .putStringSet(MonitoringSettingsRepository.KEY_USER_ADDED_APPS, serializedSet)
                .putStringSet(MonitoringSettingsRepository.KEY_ENABLED_PACKAGES, currentPackages)
                .commit()
        } catch (e: Throwable) {
            false
        }
    }

    override fun updateUserApp(app: SupportedApp): Boolean = synchronized(lock) {
        if (app.packageName.isBlank()) return false
        // Built-in apps cannot be modified
        if (AppCatalog.containsPackage(app.packageName)) return false

        val existingUserApps = getUserAddedApps().toMutableList()
        val index = existingUserApps.indexOfFirst { it.packageName.equals(app.packageName, ignoreCase = true) }
        if (index == -1) return false

        existingUserApps[index] = app
        val serializedSet = existingUserApps.map { SupportedAppSerializer.serialize(it) }.toSet()

        prefs.edit()
            .putStringSet(MonitoringSettingsRepository.KEY_USER_ADDED_APPS, serializedSet)
            .apply()

        return true
    }

    override fun removeUserApp(packageName: String): Boolean = synchronized(lock) {
        if (packageName.isBlank()) return false
        // Built-in catalog apps must NOT be removed
        if (AppCatalog.containsPackage(packageName)) return false

        val rawSet = prefs.getStringSet(MonitoringSettingsRepository.KEY_USER_ADDED_APPS, emptySet()) ?: emptySet()
        val allUserApps = rawSet.mapNotNull { SupportedAppSerializer.deserialize(it) }
        val matchingApps = allUserApps.filter { it.packageName.equals(packageName, ignoreCase = true) }
        if (matchingApps.isEmpty()) {
            return false
        }

        // Filter out all instances of this package (handles duplicate/corrupt data safety)
        val remainingUserApps = allUserApps.filterNot { it.packageName.equals(packageName, ignoreCase = true) }
        val serializedSet = remainingUserApps.map { SupportedAppSerializer.serialize(it) }.toSet()

        // Clean up from enabledPackages (remove all case-insensitive matches)
        val currentPackages = getSettings().enabledPackages.toMutableSet()
        currentPackages.removeAll { it.equals(packageName, ignoreCase = true) }

        return try {
            prefs.edit()
                .putStringSet(MonitoringSettingsRepository.KEY_USER_ADDED_APPS, serializedSet)
                .putStringSet(MonitoringSettingsRepository.KEY_ENABLED_PACKAGES, currentPackages)
                .commit()
        } catch (e: Throwable) {
            false
        }
    }

    override fun getAllConfiguredApps(): List<SupportedApp> = synchronized(lock) {
        val builtIn = AppCatalog.allApps
        val seen = mutableSetOf<String>()
        val result = mutableListOf<SupportedApp>()

        // Built-in catalog definitions take precedence
        for (app in builtIn) {
            if (seen.add(app.packageName.lowercase())) {
                result.add(app)
            }
        }
        // Add user-added definitions if not duplicate
        for (userApp in getUserAddedApps()) {
            if (seen.add(userApp.packageName.lowercase())) {
                result.add(userApp)
            }
        }
        result
    }

    override fun isInitialSmsImportCompleted(): Boolean = synchronized(lock) {
        prefs.getBoolean(MonitoringSettingsRepository.KEY_INITIAL_SMS_IMPORT_COMPLETED, false)
    }

    override fun setInitialSmsImportCompleted(completed: Boolean): Unit = synchronized(lock) {
        prefs.edit()
            .putBoolean(MonitoringSettingsRepository.KEY_INITIAL_SMS_IMPORT_COMPLETED, completed)
            .commit()
    }

    override fun resetToDefaults(): Unit = synchronized(lock) {
        val wasInitialCompleted = prefs.getBoolean(MonitoringSettingsRepository.KEY_INITIAL_SMS_IMPORT_COMPLETED, false)
        prefs.edit()
            .putBoolean(MonitoringSettingsRepository.KEY_GLOBAL_ENABLED, true)
            .putBoolean(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING, true)
            .putBoolean(MonitoringSettingsRepository.KEY_NOTIFICATION_TRACKING_ENABLED, true)
            .putBoolean(MonitoringSettingsRepository.KEY_SMS_TRACKING_ENABLED, true)
            .putBoolean(MonitoringSettingsRepository.KEY_INITIAL_SMS_IMPORT_COMPLETED, wasInitialCompleted)
            .putStringSet(MonitoringSettingsRepository.KEY_ENABLED_PACKAGES, AppCatalog.defaultEnabledPackages)
            .remove(MonitoringSettingsRepository.KEY_USER_ADDED_APPS)
            .apply()
    }
}

/**
 * Pure in-memory implementation of [MonitoringSettingsRepository] for tests without disk I/O.
 */
class InMemoryMonitoringSettingsRepository(
    initialGlobalEnabled: Boolean = true,
    initialEnabledPackages: Set<String> = AppCatalog.defaultEnabledPackages,
    initialNotificationTrackingEnabled: Boolean = true,
    initialSmsTrackingEnabled: Boolean = true
) : MonitoringSettingsRepository {

    private val lock = Any()
    private var globalEnabled: Boolean = initialGlobalEnabled
    private var isNotificationTracking: Boolean = initialNotificationTrackingEnabled
    private var isSmsTracking: Boolean = initialSmsTrackingEnabled
    private val enabledPackages: MutableSet<String> = initialEnabledPackages.toMutableSet()
    private val userAddedApps: MutableList<SupportedApp> = mutableListOf()

    override fun getSettings(): MonitoringSettings = synchronized(lock) {
        MonitoringSettings(
            globalEnabled = globalEnabled,
            enabledPackages = enabledPackages.toSet(),
            isNotificationTrackingEnabled = isNotificationTracking
        )
    }

    override fun isNotificationTrackingEnabled(): Boolean = synchronized(lock) {
        isNotificationTracking
    }

    override fun setNotificationTrackingEnabled(enabled: Boolean): Unit = synchronized(lock) {
        isNotificationTracking = enabled
    }

    override fun isSmsTrackingEnabled(): Boolean = synchronized(lock) {
        isSmsTracking
    }

    override fun setSmsTrackingEnabled(enabled: Boolean): Unit = synchronized(lock) {
        isSmsTracking = enabled
    }

    override fun setGlobalEnabled(enabled: Boolean): Unit = synchronized(lock) {
        globalEnabled = enabled
    }

    override fun setAppEnabled(packageName: String, enabled: Boolean): Unit = synchronized(lock) {
        if (enabled) {
            enabledPackages.add(packageName)
        } else {
            enabledPackages.remove(packageName)
        }
    }

    override fun setEnabledPackages(packages: Set<String>): Unit = synchronized(lock) {
        enabledPackages.clear()
        enabledPackages.addAll(packages)
    }

    override fun getUserAddedApps(): List<SupportedApp> = synchronized(lock) {
        val seen = mutableSetOf<String>()
        val deduplicated = mutableListOf<SupportedApp>()
        for (app in userAddedApps) {
            if (seen.add(app.packageName.lowercase())) {
                deduplicated.add(app)
            }
        }
        deduplicated
    }

    override fun addUserApp(app: SupportedApp): Boolean = synchronized(lock) {
        if (app.packageName.isBlank()) return false
        if (AppCatalog.containsPackage(app.packageName)) return false
        if (userAddedApps.any { it.packageName.equals(app.packageName, ignoreCase = true) }) return false

        userAddedApps.add(app)
        enabledPackages.add(app.packageName)
        return true
    }

    override fun updateUserApp(app: SupportedApp): Boolean = synchronized(lock) {
        if (app.packageName.isBlank()) return false
        if (AppCatalog.containsPackage(app.packageName)) return false

        val index = userAddedApps.indexOfFirst { it.packageName.equals(app.packageName, ignoreCase = true) }
        if (index == -1) return false

        userAddedApps[index] = app
        return true
    }

    override fun removeUserApp(packageName: String): Boolean = synchronized(lock) {
        if (packageName.isBlank()) return false
        if (AppCatalog.containsPackage(packageName)) return false

        val matching = userAddedApps.filter { it.packageName.equals(packageName, ignoreCase = true) }
        if (matching.isEmpty()) return false

        userAddedApps.removeAll { it.packageName.equals(packageName, ignoreCase = true) }
        enabledPackages.removeAll { it.equals(packageName, ignoreCase = true) }
        return true
    }

    override fun getAllConfiguredApps(): List<SupportedApp> = synchronized(lock) {
        val builtIn = AppCatalog.allApps
        val seen = mutableSetOf<String>()
        val result = mutableListOf<SupportedApp>()

        for (app in builtIn) {
            if (seen.add(app.packageName.lowercase())) {
                result.add(app)
            }
        }
        for (userApp in getUserAddedApps()) {
            if (seen.add(userApp.packageName.lowercase())) {
                result.add(userApp)
            }
        }
        result
    }

    private var initialSmsImportCompleted: Boolean = false

    override fun isInitialSmsImportCompleted(): Boolean = synchronized(lock) {
        initialSmsImportCompleted
    }

    override fun setInitialSmsImportCompleted(completed: Boolean): Unit = synchronized(lock) {
        initialSmsImportCompleted = completed
    }

    override fun resetToDefaults(): Unit = synchronized(lock) {
        globalEnabled = true
        isNotificationTracking = true
        isSmsTracking = true
        // initialSmsImportCompleted is preserved as onboarding state is first-time only
        enabledPackages.clear()
        enabledPackages.addAll(AppCatalog.defaultEnabledPackages)
        userAddedApps.clear()
    }
}
