package com.subhashrelangi.arctracker.ui.ledger

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader

/**
 * Top branding header matching Ui-Designs/Ledgerpage.png.
 * Delegates to the universal ArcTrackerHeader component to ensure
 * application-wide consistency and zero duplication.
 */
@Composable
fun LedgerHeader(
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onAddClick: () -> Unit = {}
) {
    ArcTrackerHeader(
        modifier = modifier,
        title = "ArcTracker",
        showBadge = true,
        badgeText = "UPI",
        statusText = "Automated  •  Local-first",
        showSearch = true,
        showAdd = true,
        onSearchClick = onSearchClick,
        onAddClick = onAddClick
    )
}
