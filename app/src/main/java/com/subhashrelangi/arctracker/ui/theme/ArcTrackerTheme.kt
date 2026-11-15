package com.subhashrelangi.arctracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.subhashrelangi.arctracker.settings.AppThemeMode

private val LightColorScheme = lightColorScheme(
    primary = ArcColors.Light.primary,
    onPrimary = Color.White,
    primaryContainer = ArcColors.Light.primaryLight,
    onPrimaryContainer = ArcColors.Light.primaryDark,
    secondary = ArcColors.Light.textSecondary,
    onSecondary = Color.White,
    background = ArcColors.Light.background,
    surface = ArcColors.Light.surface,
    surfaceVariant = ArcColors.Light.surfaceHighlight,
    onBackground = ArcColors.Light.textPrimary,
    onSurface = ArcColors.Light.textPrimary,
    error = ArcColors.Light.danger,
    outline = ArcColors.Light.border
)

private val DarkColorScheme = darkColorScheme(
    primary = ArcColors.Dark.primary,
    onPrimary = Color(0xFF000000),
    primaryContainer = ArcColors.Dark.primaryLight,
    onPrimaryContainer = ArcColors.Dark.primary,
    secondary = ArcColors.Dark.textSecondary,
    onSecondary = Color(0xFF000000),
    background = ArcColors.Dark.background,
    surface = ArcColors.Dark.surface,
    surfaceVariant = ArcColors.Dark.surfaceHighlight,
    onBackground = ArcColors.Dark.textPrimary,
    onSurface = ArcColors.Dark.textPrimary,
    error = ArcColors.Dark.danger,
    outline = ArcColors.Dark.border
)

/**
 * Direct access to universal ArcTracker tokens.
 */
object ArcTheme {
    val colors: ArcColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalArcColors.current

    val shapes: ArcShapes
        get() = ArcShapes

    val spacing: ArcSpacing
        get() = ArcSpacing

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalArcColors.current.isDark
}

/**
 * Universal Theme provider for ArcTracker.
 * Supports SYSTEM, LIGHT, and DARK modes.
 */
@Composable
fun ArcTrackerTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    darkTheme: Boolean = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    },
    content: @Composable () -> Unit
) {
    val arcColorScheme = if (darkTheme) ArcColors.Dark else ArcColors.Light
    val materialScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = arcColorScheme.background.toArgb()
                window.navigationBarColor = arcColorScheme.bottomNavBackground.toArgb()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                    window.isStatusBarContrastEnforced = false
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
                }
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // Adapt density smoothly on compact and medium phone screens (width < 392dp)
    // so that content designed for standard modern screens fits naturally without
    // appearing oversized or zoomed in, while normalizing font scale to keep layouts robust.
    val currentDensity = LocalDensity.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = config.screenWidthDp

    val widthScale = if (screenWidth in 240..391) {
        (screenWidth.toFloat() / 392f).coerceIn(0.85f, 1.0f)
    } else {
        1.0f
    }

    val adaptedFontScale = (1.0f * widthScale).coerceIn(0.85f, 1.0f)
    val adaptedDensity = Density(
        density = currentDensity.density * widthScale,
        fontScale = adaptedFontScale
    )

    CompositionLocalProvider(
        LocalArcColors provides arcColorScheme,
        LocalDensity provides adaptedDensity
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            content = content
        )
    }
}
