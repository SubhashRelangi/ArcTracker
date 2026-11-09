package com.subhashrelangi.arctracker.ui.ledger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.subhashrelangi.arctracker.ui.theme.ArcColors
import com.subhashrelangi.arctracker.ui.theme.ArcShapes
import com.subhashrelangi.arctracker.ui.theme.ArcSpacing
import com.subhashrelangi.arctracker.ui.theme.LocalArcColors

/**
 * Design tokens and centralized color palette for ArcTracker LedgerPage.
 * Dynamically resolves to Light or Dark theme via Universal ArcColors.
 */
object LedgerColors {
    val Light = ArcColors.Light
    val Dark = ArcColors.Dark

    // Backgrounds
    val Background: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.surface
    val SurfaceElevated: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.surfaceElevated
    val SurfaceHighlight: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.surfaceHighlight
    val CardBackground: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.cardBackground

    // Search and Input
    val SearchBg: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.surfaceHighlight
    val SearchBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.border

    // Borders
    val Border: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.border
    val BorderSubtle: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.borderSubtle
    val BorderLight: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.borderLight

    // Typography
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.textSecondary
    val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.textMuted
    val TextMutedLight: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.textMutedLight

    // Financial indicators
    val Debit: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.debit
    val DebitMuted: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.debitDanger
    val DebitBg: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.debitBg
    val DebitBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.debitBorder

    val Credit: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.credit
    val CreditMuted: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.creditMuted
    val CreditBg: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.creditBg
    val CreditBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.creditBorder

    // Review / Warning
    val WarningAmber: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.warningAmber
    val WarningAmberDot: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.warningAmberDot
    val WarningAmberBg: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.warningAmberBg
    val WarningAmberBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.warningAmberBorder

    // Unresolved
    val Unresolved: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.unresolved
    val UnresolvedBg: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.unresolvedBg
    val UnresolvedBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.unresolvedBorder

    // Badges / Tags
    val UpiBadgeBg: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.upiBadgeBg
    val UpiBadgeText: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.upiBadgeText

    // Filter Chips
    val FilterChipBg: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.filterChipBg
    val FilterChipSelectedBg: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.filterChipSelectedBg
    val FilterChipSelectedText: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.filterChipSelectedText
}

object LedgerShapes {
    val Card = ArcShapes.Card
    val Pill = ArcShapes.Pill
    val SmallPill = ArcShapes.SmallPill
    val SearchBar = ArcShapes.SearchBar
    val FloatingSummary = ArcShapes.FloatingSummary
    val BottomNav = ArcShapes.BottomNav
}

object LedgerSpacing {
    val xxs = ArcSpacing.xxs
    val xs = ArcSpacing.xs
    val sm = ArcSpacing.sm
    val md = ArcSpacing.md
    val lg = ArcSpacing.lg
    val xl = ArcSpacing.xl
    val xxl = ArcSpacing.xxl
}
