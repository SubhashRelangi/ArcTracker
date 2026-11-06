package com.subhashrelangi.arctracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = ArcColors.Primary,
    onPrimary = Color.White,
    primaryContainer = ArcColors.PrimaryLight,
    onPrimaryContainer = ArcColors.PrimaryDark,
    secondary = ArcColors.TextSecondary,
    onSecondary = Color.White,
    background = ArcColors.Background,
    surface = ArcColors.Surface,
    surfaceVariant = ArcColors.SurfaceHighlight,
    onBackground = ArcColors.TextPrimary,
    onSurface = ArcColors.TextPrimary,
    error = ArcColors.Danger,
    outline = ArcColors.Border
)

/**
 * Universal Theme provider for ArcTracker.
 * Defaults to clean, modern ArcTracker Emerald Light aesthetic.
 */
@Composable
fun ArcTrackerTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
