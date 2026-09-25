package com.example.arctracker.settings

import android.content.Context
import android.content.SharedPreferences

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
     */
    fun getUserAddedApps(): List<SupportedApp>

    /**
     * Adds and persists a user-defined application.
     * Automatically enables the newly added app by default without mutating globalEnabled.
     * Duplicate packages (matching built-in or existing user apps) are rejected.
     *
     * @return true if added successfully, false if duplicate or invalid.
     */
    fun addUserApp(app: SupportedApp): Boolean

    /**
     * Returns all configured apps (built-in catalog + persisted user-added apps).
     * Guaranteed no duplicates by package name.
     */
    fun getAllConfiguredApps(): List<SupportedApp>

    companion object {
        const val PREFS_NAME = "ArcTrackerPrefs"
        const val KEY_GLOBAL_ENABLED = "monitoring_global_enabled"
        const val KEY_ENABLED_PACKAGES = "monitored_packages"
        const val KEY_USER_ADDED_APPS = "user_added_apps"
        const val LEGACY_KEY_AUTO_TRACKING = "isAutoTrackingEnabled"

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
            initialEnabledPackages: Set<String> = AppCatalog.defaultEnabledPackages
        ): MonitoringSettingsRepository {
            return InMemoryMonitoringSettingsRepository(initialGlobalEnabled, initialEnabledPackages)
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

        MonitoringSettings(
            globalEnabled = globalEnabled,
            enabledPackages = enabledPackages.toSet()
        )
    }

    override fun setGlobalEnabled(enabled: Boolean): Unit = synchronized(lock) {
        prefs.edit()
            .putBoolean(MonitoringSettingsRepository.KEY_GLOBAL_ENABLED, enabled)
            .putBoolean(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING, enabled)
            .apply()
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
        rawSet.mapNotNull { SupportedAppSerializer.deserialize(it) }
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

        prefs.edit()
            .putStringSet(MonitoringSettingsRepository.KEY_USER_ADDED_APPS, serializedSet)
            .putStringSet(MonitoringSettingsRepository.KEY_ENABLED_PACKAGES, currentPackages)
            .apply()

        return true
    }

    override fun getAllConfiguredApps(): List<SupportedApp> = synchronized(lock) {
        val builtIn = AppCatalog.allApps
        val userAdded = getUserAddedApps().filter { userApp ->
            builtIn.none { it.packageName.equals(userApp.packageName, ignoreCase = true) }
        }
        builtIn + userAdded
    }

    override fun resetToDefaults(): Unit = synchronized(lock) {
        prefs.edit()
            .putBoolean(MonitoringSettingsRepository.KEY_GLOBAL_ENABLED, true)
            .putBoolean(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING, true)
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
    initialEnabledPackages: Set<String> = AppCatalog.defaultEnabledPackages
) : MonitoringSettingsRepository {

    private val lock = Any()
    private var globalEnabled: Boolean = initialGlobalEnabled
    private val enabledPackages: MutableSet<String> = initialEnabledPackages.toMutableSet()
    private val userAddedApps: MutableList<SupportedApp> = mutableListOf()

    override fun getSettings(): MonitoringSettings = synchronized(lock) {
        MonitoringSettings(
            globalEnabled = globalEnabled,
            enabledPackages = enabledPackages.toSet()
        )
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
        userAddedApps.toList()
    }

    override fun addUserApp(app: SupportedApp): Boolean = synchronized(lock) {
        if (app.packageName.isBlank()) return false
        if (AppCatalog.containsPackage(app.packageName)) return false
        if (userAddedApps.any { it.packageName.equals(app.packageName, ignoreCase = true) }) return false

        userAddedApps.add(app)
        enabledPackages.add(app.packageName)
        return true
    }

    override fun getAllConfiguredApps(): List<SupportedApp> = synchronized(lock) {
        val builtIn = AppCatalog.allApps
        val extra = userAddedApps.filter { userApp ->
            builtIn.none { it.packageName.equals(userApp.packageName, ignoreCase = true) }
        }
        builtIn + extra
    }

    override fun resetToDefaults(): Unit = synchronized(lock) {
        globalEnabled = true
        enabledPackages.clear()
        enabledPackages.addAll(AppCatalog.defaultEnabledPackages)
        userAddedApps.clear()
    }
}
