package com.subhashrelangi.arctracker.settings

/**
 * Supported appearance themes for ArcTracker.
 * Allows seamless switching between System Default, Light, and Dark themes.
 */
enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    val displayName: String
        get() = when (this) {
            SYSTEM -> "System Default"
            LIGHT -> "Light Theme"
            DARK -> "Dark Theme"
        }

    val description: String
        get() = when (this) {
            SYSTEM -> "Follows device system settings"
            LIGHT -> "Clean Emerald Light aesthetic"
            DARK -> "Industrial Slate Dark aesthetic"
        }

    companion object {
        fun fromString(value: String?): AppThemeMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
        }
    }
}
