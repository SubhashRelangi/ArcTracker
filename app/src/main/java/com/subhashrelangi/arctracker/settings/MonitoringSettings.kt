package com.subhashrelangi.arctracker.settings

/**
 * Model representing the user's active monitoring settings configuration.
 *
 * @property globalEnabled Master switch: whether background monitoring is globally enabled.
 * @property enabledPackages The set of package identifiers explicitly enabled for monitoring.
 */
data class MonitoringSettings(
    val globalEnabled: Boolean = true,
    val enabledPackages: Set<String> = emptySet(),
    val isNotificationTrackingEnabled: Boolean = true
) {
    /**
     * Returns true if the given package is enabled in the selected package set.
     */
    fun isAppEnabled(packageName: String): Boolean {
        return enabledPackages.contains(packageName)
    }

    /**
     * Returns true if the global master switch, notification source switch, and the package-specific switch are enabled.
     */
    fun isMonitoringActive(packageName: String): Boolean {
        return globalEnabled && isNotificationTrackingEnabled && isAppEnabled(packageName)
    }
}
