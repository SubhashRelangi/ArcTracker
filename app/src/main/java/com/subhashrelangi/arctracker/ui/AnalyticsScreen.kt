package com.subhashrelangi.arctracker.ui

import androidx.compose.runtime.Composable
import com.subhashrelangi.arctracker.service.AnalyticsManager
import com.subhashrelangi.arctracker.ui.insights.InsightsPage

/**
 * Backward-compatible entrypoint delegating to the production [InsightsPage].
 */
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit = {},
    analyticsManager: AnalyticsManager? = null,
    showInternalHeader: Boolean = true,
    searchQuery: String = ""
) {
    InsightsPage(
        analyticsManager = analyticsManager,
        showHeader = showInternalHeader,
        onNavigateBack = onNavigateBack,
        searchQuery = searchQuery
    )
}
