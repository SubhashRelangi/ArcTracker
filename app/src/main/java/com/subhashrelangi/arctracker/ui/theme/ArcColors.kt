package com.subhashrelangi.arctracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Universal ArcTracker Industrial-Grade Design Tokens & Global Color Palette.
 * Serves as the single source of truth for the entire application.
 *
 * Theme Architecture: ArcTracker Emerald Light Aesthetic
 * - Canvas Backgrounds: Clean, breathable light slate (#F8FAFC) & pure white (#FFFFFF)
 * - Brand Accents: Emerald Green (#047857), Mint (#ECFDF5), Emerald Border (#A7F3D0)
 * - Typography: Deep slate primary (#0F172A), medium slate (#475569), muted slate (#64748B)
 * - Semantic Indicators: Credit (#047857), Debit (#0F172A / #DC2626), Warning (#D97706)
 * - Geometry Convention: All buttons, pills & icon touch targets use CircleShape
 */
object ArcColors {
    // Brand Emerald Accents
    val Primary = Color(0xFF047857)
    val PrimaryDark = Color(0xFF065F46)
    val PrimaryLight = Color(0xFFECFDF5)
    val PrimaryBorder = Color(0xFFA7F3D0)
    val EmeraldPrimary = Color(0xFF047857)

    // Primary Canvas & Surface Backgrounds
    val Background = Color(0xFFF8FAFC)
    val LightBg = Color(0xFFF8FAFC)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceElevated = Color(0xFFFFFFFF)
    val SurfaceHighlight = Color(0xFFF1F5F9)
    val SurfaceMuted = Color(0xFFF1F5F9)
    val CardBackground = Color(0xFFFFFFFF)
    val SurfaceCard = Color(0xFFFFFFFF)

    // Borders & Dividers
    val Border = Color(0xFFE2E8F0)
    val BorderSubtle = Color(0xFFF1F5F9)
    val BorderLight = Color(0xFFE2E8F0)
    val SurfaceCardBorder = Color(0xFFE2E8F0)
    val CardBorder = Color(0xFFE2E8F0)

    // Typography
    val TextPrimary = Color(0xFF0F172A)
    val TextSecondary = Color(0xFF475569)
    val TextMuted = Color(0xFF64748B)
    val TextMutedLight = Color(0xFF94A3B8)
    val SlateTitle = Color(0xFF0F172A)
    val SlateSub = Color(0xFF64748B)

    // Financial Indicators - Debits & Credits
    val Debit = Color(0xFF0F172A)
    val DebitDanger = Color(0xFFDC2626)
    val DebitBg = Color(0xFFFEF2F2)
    val DebitBorder = Color(0xFFFECACA)

    val Credit = Color(0xFF047857)
    val CreditMuted = Color(0xFF10B981)
    val CreditBg = Color(0xFFECFDF5)
    val CreditBorder = Color(0xFFA7F3D0)

    // Status - Review & Warning
    val Warning = Color(0xFFD97706)
    val WarningLight = Color(0xFFF59E0B)
    val WarningAmber = Color(0xFFD97706)
    val WarningAmberDot = Color(0xFFF59E0B)
    val WarningAmberBg = Color(0xFFFFFBEB)
    val WarningAmberBorder = Color(0xFFFDE68A)
    val WarningContainer = Color(0xFFFFFBEB)
    val WarningBorder = Color(0xFFFDE68A)

    // Status - Success & Danger
    val Success = Color(0xFF047857)
    val SuccessContainer = Color(0xFFECFDF5)
    val SuccessBorder = Color(0xFFA7F3D0)

    val Danger = Color(0xFFDC2626)
    val DangerContainer = Color(0xFFFEF2F2)
    val DangerBorder = Color(0xFFFECACA)

    // Status - Unresolved
    val Unresolved = Color(0xFFD97706)
    val UnresolvedBg = Color(0xFFFFFBEB)
    val UnresolvedBorder = Color(0xFFFDE68A)

    // Badges & Filter Chips
    val UpiBadgeBg = Color(0xFFEFF6FF)
    val UpiBadgeText = Color(0xFF2563EB)

    val FilterChipBg = Color(0xFFF1F5F9)
    val FilterChipSelectedBg = Color(0xFF0F172A)
    val FilterChipSelectedText = Color(0xFFFFFFFF)

    val ChipBackground = Color(0xFFF1F5F9)
    val ChipBorder = Color(0xFFE2E8F0)

    val TelemetryBackground = Color(0xFFF8FAFC)
    val TelemetryBorder = Color(0xFFE2E8F0)

    // Navigation Bar Tokens
    val BottomNavBackground = Color(0xFFFFFFFF)
    val BottomNavBorder = Color(0xFFE2E8F0)
    val BottomNavPillSelected = Color(0xFF0F172A)
    val BottomNavTextActive = Color(0xFF047857)
    val BottomNavTextInactive = Color(0xFF64748B)
    val BottomNavBadgeAmber = Color(0xFFD97706)
}
