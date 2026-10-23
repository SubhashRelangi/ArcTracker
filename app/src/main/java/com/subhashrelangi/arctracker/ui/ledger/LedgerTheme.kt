package com.subhashrelangi.arctracker.ui.ledger

import androidx.compose.ui.graphics.Color
import com.subhashrelangi.arctracker.ui.theme.ArcShapes
import com.subhashrelangi.arctracker.ui.theme.ArcSpacing

/**
 * Design tokens and centralized color palette for ArcTracker LedgerPage.
 * Pure light-theme aesthetic exactly matching the reference mockup.
 */
object LedgerColors {
    // Backgrounds
    val Background = Color(0xFFF8FAFC)       // Crisp light slate background
    val Surface = Color(0xFFFFFFFF)          // Pure white card surfaces
    val SurfaceElevated = Color(0xFFFFFFFF)
    val SurfaceHighlight = Color(0xFFF1F5F9)
    val CardBackground = Color(0xFFFFFFFF)

    // Search and Input
    val SearchBg = Color(0xFFF1F5F9)
    val SearchBorder = Color(0xFFE2E8F0)

    // Borders
    val Border = Color(0xFFE2E8F0)
    val BorderSubtle = Color(0xFFF1F5F9)
    val BorderLight = Color(0xFFE2E8F0)

    // Typography
    val TextPrimary = Color(0xFF0F172A)      // Deep dark slate / near black
    val TextSecondary = Color(0xFF475569)    // Medium slate
    val TextMuted = Color(0xFF64748B)        // Muted slate
    val TextMutedLight = Color(0xFF94A3B8)   // Light muted caption slate

    // Financial indicators
    val Debit = Color(0xFF0F172A)           // Outflows in mockup are dark bold
    val DebitMuted = Color(0xFFEF4444)      // Soft red accent if needed
    val DebitBg = Color(0xFFF1F5F9)
    val DebitBorder = Color(0xFFE2E8F0)

    val Credit = Color(0xFF059669)          // Vibrant emerald green for inflows
    val CreditMuted = Color(0xFF10B981)
    val CreditBg = Color(0xFFECFDF5)
    val CreditBorder = Color(0xFFA7F3D0)

    // Review / Warning
    val WarningAmber = Color(0xFFD97706)
    val WarningAmberDot = Color(0xFFF59E0B)
    val WarningAmberBg = Color(0xFFFFFBEB)
    val WarningAmberBorder = Color(0xFFFDE68A)

    // Unresolved
    val Unresolved = Color(0xFFD97706)
    val UnresolvedBg = Color(0xFFFFFBEB)
    val UnresolvedBorder = Color(0xFFFDE68A)

    // Badges / Tags
    val UpiBadgeBg = Color(0xFFEFF6FF)
    val UpiBadgeText = Color(0xFF2563EB)

    // Filter Chips
    val FilterChipBg = Color(0xFFF1F5F9)
    val FilterChipSelectedBg = Color(0xFF0F172A)
    val FilterChipSelectedText = Color(0xFFFFFFFF)
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
