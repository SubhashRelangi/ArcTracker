package com.example.arctracker.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Authoritative repository interface for managing ArcTracker's monitoring settings.
 * Single source of truth for global monitoring status and enabled package sets.
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
     * Retrieves the full catalog of supported applications.
     */
    fun getCatalog(): List<SupportedApp> = AppCatalog.allApps

    companion object {
        const val PREFS_NAME = "ArcTrackerPrefs"
        const val KEY_GLOBAL_ENABLED = "monitoring_global_enabled"
        const val KEY_ENABLED_PACKAGES = "monitored_packages"
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
 * SharedPreferences-backed production implementation of [MonitoringSettingsRepository].
 * Preserves existing settings and provides backward compatibility with legacy preferences.
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

    override fun resetToDefaults(): Unit = synchronized(lock) {
        prefs.edit()
            .putBoolean(MonitoringSettingsRepository.KEY_GLOBAL_ENABLED, true)
            .putBoolean(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING, true)
            .putStringSet(MonitoringSettingsRepository.KEY_ENABLED_PACKAGES, AppCatalog.defaultEnabledPackages)
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

    override fun resetToDefaults(): Unit = synchronized(lock) {
        globalEnabled = true
        enabledPackages.clear()
        enabledPackages.addAll(AppCatalog.defaultEnabledPackages)
    }
}
