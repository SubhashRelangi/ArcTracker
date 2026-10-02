package com.subhashrelangi.arctracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.subhashrelangi.arctracker.ui.core.ArcDestination
import com.subhashrelangi.arctracker.ui.core.ArcTrackerBottomNavigation

sealed class NavItem(val route: String, val title: String) {
    object Home : NavItem("Home", "Home")
    object Ledger : NavItem("Transactions", "Ledger")
    object Review : NavItem("Pending", "Review")
    object Insights : NavItem("Insights", "Insights")
    object Settings : NavItem("Settings", "Settings")
}

/**
 * Bottom Navigation Bar styled to match the dark cyberpunk fintech aesthetic.
 * Delegates to the universal ArcTrackerBottomNavigation component.
 */
@Composable
fun FloatingNavigationBar(
    currentRoute: String,
    pendingReviewCount: Int = 3,
    onNavigate: (NavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val destination = ArcDestination.fromRoute(currentRoute)

    ArcTrackerBottomNavigation(
        currentDestination = destination,
        onNavigate = { dest ->
            val navItem = when (dest) {
                ArcDestination.HOME -> NavItem.Home
                ArcDestination.LEDGER -> NavItem.Ledger
                ArcDestination.REVIEW -> NavItem.Review
                ArcDestination.INSIGHTS -> NavItem.Insights
                ArcDestination.SETTINGS -> NavItem.Settings
            }
            onNavigate(navItem)
        },
        reviewBadgeCount = pendingReviewCount,
        modifier = modifier
    )
}
