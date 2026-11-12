package com.subhashrelangi.arctracker.ui.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.subhashrelangi.arctracker.ui.theme.LocalArcColors
import com.subhashrelangi.arctracker.ui.theme.ArcColors as ThemeArcColors
import com.subhashrelangi.arctracker.ui.theme.ArcShapes as ThemeArcShapes
import com.subhashrelangi.arctracker.ui.theme.ArcSpacing as ThemeArcSpacing

/**
 * Global ArcTracker Fintech Design Tokens.
 * Dynamically unified with universal theme tokens in com.subhashrelangi.arctracker.ui.theme.
 */
object ArcColors {
    val Light = ThemeArcColors.Light
    val Dark = ThemeArcColors.Dark

    val isDark: Boolean @Composable @ReadOnlyComposable get() = LocalArcColors.current.isDark

    val Primary: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.primary
    val PrimaryDark: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.primaryDark
    val PrimaryLight: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.primaryLight
    val PrimaryBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.primaryBorder

    val Background: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.surface
    val SurfaceElevated: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.surfaceElevated
    val SurfaceHighlight: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.surfaceHighlight
    val SurfaceMuted: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.surfaceMuted
    val CardBackground: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.cardBackground
    val SurfaceCard: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.surfaceCard
    val SurfaceCardBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.surfaceCardBorder

    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.textSecondary
    val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.textMuted

    val Debit: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.debit
    val Credit: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.credit
    val Warning: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.warning
    val WarningLight: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.warningLight
    val WarningAmber: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.warningAmber
    val WarningAmberDot: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.warningAmberDot
    val WarningAmberBg: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.warningAmberBg
    val WarningAmberBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.warningAmberBorder
    val WarningContainer: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.warningContainer
    val WarningBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.warningBorder

    val Success: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.success
    val SuccessContainer: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.successContainer
    val SuccessBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.successBorder

    val Danger: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.danger
    val DangerContainer: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.dangerContainer
    val DangerBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.dangerBorder

    val Border: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.border
    val BorderSubtle: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.borderSubtle

    val ChipBackground: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.chipBackground
    val ChipBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.chipBorder

    val TelemetryBackground: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.telemetryBackground
    val TelemetryBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.telemetryBorder
}

object ArcShapes {
    val Card = ThemeArcShapes.CardMedium
    val CardElevated = ThemeArcShapes.CardElevated
    val Pill = ThemeArcShapes.LargePill
    val Button = ThemeArcShapes.Button
    val ButtonSecondary = ThemeArcShapes.ButtonSecondary
    val Chip = ThemeArcShapes.Chip
    val MicroChip = ThemeArcShapes.MicroChip
    val InnerBox = ThemeArcShapes.InnerBox
}

object ArcSpacing {
    val xs = ThemeArcSpacing.xs
    val sm = ThemeArcSpacing.sm
    val md = ThemeArcSpacing.md
    val lg = ThemeArcSpacing.lg
    val xl = ThemeArcSpacing.xl
    val xxl = ThemeArcSpacing.xxl
}
