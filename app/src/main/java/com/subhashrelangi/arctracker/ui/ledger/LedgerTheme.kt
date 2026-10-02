package com.subhashrelangi.arctracker.ui.ledger

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Design tokens and centralized color palette for ArcTracker LedgerPage
 * derived from Ui-Designs/Ledgerpage.png and ArcTracker brand direction.
 */
object LedgerColors {
    // Backgrounds
    val Background = Color(0xFF090C10)
    val Surface = Color(0xFF121620)
    val SurfaceElevated = Color(0xFF161A24)
    val SurfaceHighlight = Color(0xFF1F2432)
    val CardBackground = Color(0xFF131721)

    // Borders
    val Border = Color(0xFF1E2432)
    val BorderSubtle = Color(0xFF262C36)
    val BorderLight = Color(0xFF30363D)

    // Typography
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
    val TextMutedLight = Color(0xFF8B949E)

    // Financial indicators
    val Debit = Color(0xFFF43F5E) // Bright red / rose
    val DebitBg = Color(0xFF2C1317)
    val DebitBorder = Color(0xFF4A1E24)

    val Credit = Color(0xFF00E676) // Bright emerald green
    val CreditMuted = Color(0xFF10B981)
    val CreditBg = Color(0xFF0C2419)
    val CreditBorder = Color(0xFF134C34)

    // Review / Warning
    val WarningAmber = Color(0xFFFBBF24)
    val WarningAmberDot = Color(0xFFF59E0B)
    val WarningAmberBg = Color(0xFF2B1F09)
    val WarningAmberBorder = Color(0xFF4A3612)

    // Unresolved
    val Unresolved = Color(0xFFF87171)
    val UnresolvedBg = Color(0xFF2C1317)
    val UnresolvedBorder = Color(0xFF4A1E24)

    // Badges / Tags
    val UpiBadgeBg = Color(0xFF21262D)
    val UpiBadgeText = Color(0xFFC9D1D9)

    val FilterChipBg = Color(0xFF161A24)
    val FilterChipSelectedBg = Color(0xFFFFFFFF)
    val FilterChipSelectedText = Color(0xFF0F141C)
}

object LedgerShapes {
    val Card = RoundedCornerShape(18.dp)
    val Pill = RoundedCornerShape(20.dp)
    val SmallPill = RoundedCornerShape(6.dp)
    val SearchBar = RoundedCornerShape(16.dp)
    val FloatingSummary = RoundedCornerShape(32.dp)
    val BottomNav = RoundedCornerShape(32.dp)
}

object LedgerSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}
