package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader

@Composable
fun ArcTrackerHeader(
    title: String,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    ArcTrackerHeader(
        title = title,
        subtitle = subtitle,
        onBackClick = onBackClick,
        actions = actions
    )
}
