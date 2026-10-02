package com.subhashrelangi.arctracker.ui.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Universal ArcTracker Header.
 * Extracted from LedgerPage as the single visual source of truth for all application screens.
 *
 * Visual structure:
 * ┌──────────────────────────────────────────────┐
 * │                                              │
 * │  [ A ]  ArcTracker  [UPI]       [⌕]   [+]   │
 * │         ● Automated • Local-first            │
 * │                                              │
 * └──────────────────────────────────────────────┘
 */
@Composable
fun ArcTrackerHeader(
    modifier: Modifier = Modifier,
    title: String = "ArcTracker",
    showBadge: Boolean = true,
    badgeText: String = "UPI",
    statusText: String = "Automated  •  Local-first",
    showSearch: Boolean = true,
    showAdd: Boolean = true,
    onSearchClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Branding Logo + Title + Subtitle
        ArcTrackerBrand(
            title = title,
            showBadge = showBadge,
            badgeText = badgeText,
            statusText = statusText
        )

        // Right: Configurable Action Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (actions != null) {
                actions()
            } else {
                if (showSearch) {
                    ArcIconButton(
                        icon = Icons.Default.Search,
                        contentDescription = "Search",
                        variant = ArcIconButtonVariant.DARK,
                        onClick = onSearchClick
                    )
                }

                if (showAdd) {
                    ArcIconButton(
                        icon = Icons.Default.Add,
                        contentDescription = "Add Transaction",
                        variant = ArcIconButtonVariant.LIGHT,
                        onClick = onAddClick
                    )
                }
            }
        }
    }
}
