package com.subhashrelangi.arctracker.ui.core

/**
 * Universal primary navigation destinations across ArcTracker.
 * Preserves internal routing strings ("Transactions", "Pending")
 * while presenting polished labels ("Ledger", "Review") to users.
 */
enum class ArcDestination(
    val route: String,
    val title: String
) {
    HOME("Home", "Home"),
    LEDGER("Transactions", "Ledger"),
    REVIEW("Pending", "Review"),
    INSIGHTS("Insights", "Insights"),
    SETTINGS("Settings", "Settings");

    companion object {
        fun fromRoute(route: String): ArcDestination {
            return when (route) {
                "Home" -> HOME
                "Transactions", "Ledger" -> LEDGER
                "Pending", "Review" -> REVIEW
                "Insights", "Analytics" -> INSIGHTS
                "Settings" -> SETTINGS
                else -> HOME
            }
        }
    }
}
