package com.subhashrelangi.arctracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Universal ArcTracker Fintech Dark Design Tokens.
 * Derived from the verified LedgerPage and HomePage designs.
 * Serves as the single source of truth for the entire application.
 */
object ArcColors {
    // Primary Backgrounds
    val Background = Color(0xFF090C10)
    val Surface = Color(0xFF121620)
    val SurfaceElevated = Color(0xFF161A24)
    val SurfaceHighlight = Color(0xFF1F2432)
    val SurfaceMuted = Color(0xFF1F2432)
    val CardBackground = Color(0xFF131721)
    val SurfaceCard = Color(0xFF131721)

    // Borders
    val Border = Color(0xFF1E2432)
    val BorderSubtle = Color(0xFF262C36)
    val BorderLight = Color(0xFF30363D)
    val SurfaceCardBorder = Color(0xFF1E2330)

    // Typography
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
    val TextMutedLight = Color(0xFF8B949E)

    // Financial indicators - Debits & Credits
    val Debit = Color(0xFFF43F5E) // Bright red / rose
    val DebitBg = Color(0xFF2C1317)
    val DebitBorder = Color(0xFF4A1E24)

    val Credit = Color(0xFF00E676) // Bright emerald green
    val CreditMuted = Color(0xFF10B981)
    val CreditBg = Color(0xFF0C2419)
    val CreditBorder = Color(0xFF134C34)

    // Status - Review & Warning
    val Warning = Color(0xFFF59E0B)
    val WarningLight = Color(0xFFFBBF24)
    val WarningAmber = Color(0xFFFBBF24)
    val WarningAmberDot = Color(0xFFF59E0B)
    val WarningAmberBg = Color(0xFF2B1F09)
    val WarningAmberBorder = Color(0xFF4A3612)
    val WarningContainer = Color(0xFF281D0B)
    val WarningBorder = Color(0xFF45300F)

    // Status - Success & Danger
    val Success = Color(0xFF10B981)
    val SuccessContainer = Color(0xFF0D2218)
    val SuccessBorder = Color(0xFF1B4D35)

    val Danger = Color(0xFFEF4444)
    val DangerContainer = Color(0xFF2C1418)
    val DangerBorder = Color(0xFF4C1D24)

    // Status - Unresolved
    val Unresolved = Color(0xFFF87171)
    val UnresolvedBg = Color(0xFF2C1317)
    val UnresolvedBorder = Color(0xFF4A1E24)

    // Badges & Navigation
    val UpiBadgeBg = Color(0xFF21262D)
    val UpiBadgeText = Color(0xFFC9D1D9)

    val FilterChipBg = Color(0xFF161A24)
    val FilterChipSelectedBg = Color(0xFFFFFFFF)
    val FilterChipSelectedText = Color(0xFF0F141C)

    val ChipBackground = Color(0xFF181C26)
    val ChipBorder = Color(0xFF252B3A)

    val TelemetryBackground = Color(0xFF0C0E14)
    val TelemetryBorder = Color(0xFF1B202D)

    // Navigation Bar Tokens
    val BottomNavBackground = Color(0xFF13161F)
    val BottomNavBorder = Color(0xFF1F2432)
    val BottomNavPillSelected = Color(0xFF262C38)
    val BottomNavTextActive = Color.White
    val BottomNavTextInactive = Color(0xFF94A3B8)
    val BottomNavBadgeAmber = Color(0xFFF59E0B)
}
