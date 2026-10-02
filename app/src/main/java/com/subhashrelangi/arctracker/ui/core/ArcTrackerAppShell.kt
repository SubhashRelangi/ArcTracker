package com.subhashrelangi.arctracker.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.subhashrelangi.arctracker.ui.theme.ArcColors

/**
 * Universal ArcTracker Application Root Shell.
 * Establishes the uniform chrome across Home, Ledger, Review, Insights, and Settings.
 *
 * Structure:
 * ┌──────────────────────────────────────────────┐
 * │ ArcTrackerHeader (Status-safe top area)     │
 * ├──────────────────────────────────────────────┤
 * │ Screen Content (LazyColumn / ScrollView)     │
 * │                                              │
 * ├──────────────────────────────────────────────┤
 * │ Floating Action Bar (e.g., Ledger Summary)  │
 * │ ArcTrackerBottomNavigation (Nav-safe bar)    │
 * └──────────────────────────────────────────────┘
 */
@Composable
fun ArcTrackerAppShell(
    currentDestination: ArcDestination,
    onNavigate: (ArcDestination) -> Unit,
    modifier: Modifier = Modifier,
    reviewBadgeCount: Int = 0,
    showHeader: Boolean = true,
    showBottomBar: Boolean = true,
    header: @Composable () -> Unit = {
        ArcTrackerHeader()
    },
    floatingBottomBar: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val bottomContentPadding = if (floatingBottomBar != null) 140.dp else 90.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ArcColors.Background)
    ) {
        // Main Screen Vertical Layout: Top Header + Content
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (showHeader) {
                Box(modifier = Modifier.statusBarsPadding()) {
                    header()
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                content(PaddingValues(bottom = bottomContentPadding))
            }
        }

        // Bottom Overlays: Floating Action Bar + Universal Bottom Navigation
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (floatingBottomBar != null) {
                floatingBottomBar()
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (showBottomBar) {
                ArcTrackerBottomNavigation(
                    currentDestination = currentDestination,
                    onNavigate = onNavigate,
                    reviewBadgeCount = reviewBadgeCount
                )
            }
        }
    }
}
