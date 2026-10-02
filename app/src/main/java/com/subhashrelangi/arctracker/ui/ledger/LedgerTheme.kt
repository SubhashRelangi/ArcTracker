package com.subhashrelangi.arctracker.ui.ledger

import com.subhashrelangi.arctracker.ui.theme.ArcColors
import com.subhashrelangi.arctracker.ui.theme.ArcShapes
import com.subhashrelangi.arctracker.ui.theme.ArcSpacing

/**
 * Design tokens and centralized color palette for ArcTracker LedgerPage.
 * Unified with the universal ArcTracker theme tokens.
 */
object LedgerColors {
    // Backgrounds
    val Background = ArcColors.Background
    val Surface = ArcColors.Surface
    val SurfaceElevated = ArcColors.SurfaceElevated
    val SurfaceHighlight = ArcColors.SurfaceHighlight
    val CardBackground = ArcColors.CardBackground

    // Borders
    val Border = ArcColors.Border
    val BorderSubtle = ArcColors.BorderSubtle
    val BorderLight = ArcColors.BorderLight

    // Typography
    val TextPrimary = ArcColors.TextPrimary
    val TextSecondary = ArcColors.TextSecondary
    val TextMuted = ArcColors.TextMuted
    val TextMutedLight = ArcColors.TextMutedLight

    // Financial indicators
    val Debit = ArcColors.Debit
    val DebitBg = ArcColors.DebitBg
    val DebitBorder = ArcColors.DebitBorder

    val Credit = ArcColors.Credit
    val CreditMuted = ArcColors.CreditMuted
    val CreditBg = ArcColors.CreditBg
    val CreditBorder = ArcColors.CreditBorder

    // Review / Warning
    val WarningAmber = ArcColors.WarningAmber
    val WarningAmberDot = ArcColors.WarningAmberDot
    val WarningAmberBg = ArcColors.WarningAmberBg
    val WarningAmberBorder = ArcColors.WarningAmberBorder

    // Unresolved
    val Unresolved = ArcColors.Unresolved
    val UnresolvedBg = ArcColors.UnresolvedBg
    val UnresolvedBorder = ArcColors.UnresolvedBorder

    // Badges / Tags
    val UpiBadgeBg = ArcColors.UpiBadgeBg
    val UpiBadgeText = ArcColors.UpiBadgeText

    val FilterChipBg = ArcColors.FilterChipBg
    val FilterChipSelectedBg = ArcColors.FilterChipSelectedBg
    val FilterChipSelectedText = ArcColors.FilterChipSelectedText
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
