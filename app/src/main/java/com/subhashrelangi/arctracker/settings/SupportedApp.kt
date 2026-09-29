package com.subhashrelangi.arctracker.settings

/**
 * Data-driven catalog model describing what apps ArcTracker can monitor.
 * Contains purely descriptive metadata and package identifiers; contains no UI or mutable state.
 */
data class SupportedApp(
    val packageName: String,
    val displayName: String,
    val description: String,
    val category: AppCategory,
    val defaultEnabled: Boolean = true
)
