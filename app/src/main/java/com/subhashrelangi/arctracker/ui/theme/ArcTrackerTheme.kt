package com.subhashrelangi.arctracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ArcColors.Credit,
    onPrimary = ArcColors.Background,
    secondary = ArcColors.TextSecondary,
    background = ArcColors.Background,
    surface = ArcColors.Surface,
    surfaceVariant = ArcColors.SurfaceElevated,
    onBackground = ArcColors.TextPrimary,
    onSurface = ArcColors.TextPrimary,
    error = ArcColors.Debit
)

/**
 * Universal Theme provider for ArcTracker.
 */
@Composable
fun ArcTrackerTheme(
    darkTheme: Boolean = true, // ArcTracker defaults to fintech dark aesthetic
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
