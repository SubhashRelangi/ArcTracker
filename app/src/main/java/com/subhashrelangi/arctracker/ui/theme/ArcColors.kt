package com.subhashrelangi.arctracker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Universal ArcTracker Design Tokens & Color Palette.
 * Compatible format supporting seamless dynamic switching between Light and Dark themes.
 */
@Immutable
data class ArcColorScheme(
    // Brand Accents
    val primary: Color,
    val primaryDark: Color,
    val primaryLight: Color,
    val primaryBorder: Color,
    val emeraldPrimary: Color,

    // Canvas & Surface
    val background: Color,
    val lightBg: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHighlight: Color,
    val surfaceMuted: Color,
    val cardBackground: Color,
    val surfaceCard: Color,

    // Borders & Dividers
    val border: Color,
    val borderSubtle: Color,
    val borderLight: Color,
    val surfaceCardBorder: Color,
    val cardBorder: Color,

    // Typography
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textMutedLight: Color,
    val slateTitle: Color,
    val slateSub: Color,

    // Debits & Credits
    val debit: Color,
    val debitDanger: Color,
    val debitBg: Color,
    val debitBorder: Color,
    val credit: Color,
    val creditMuted: Color,
    val creditBg: Color,
    val creditBorder: Color,

    // Warning & Review
    val warning: Color,
    val warningLight: Color,
    val warningAmber: Color,
    val warningAmberDot: Color,
    val warningAmberBg: Color,
    val warningAmberBorder: Color,
    val warningContainer: Color,
    val warningBorder: Color,

    // Success & Danger
    val success: Color,
    val successContainer: Color,
    val successBorder: Color,
    val danger: Color,
    val dangerContainer: Color,
    val dangerBorder: Color,

    // Unresolved
    val unresolved: Color,
    val unresolvedBg: Color,
    val unresolvedBorder: Color,

    // Badges & Chips
    val upiBadgeBg: Color,
    val upiBadgeText: Color,
    val filterChipBg: Color,
    val filterChipSelectedBg: Color,
    val filterChipSelectedText: Color,
    val chipBackground: Color,
    val chipBorder: Color,
    val telemetryBackground: Color,
    val telemetryBorder: Color,

    // Navigation Bar
    val bottomNavBackground: Color,
    val bottomNavBorder: Color,
    val bottomNavPillSelected: Color,
    val bottomNavTextActive: Color,
    val bottomNavTextInactive: Color,
    val bottomNavBadgeAmber: Color,

    val isDark: Boolean
)

val ArcLightColorScheme = ArcColorScheme(
    primary = Color(0xFF047857),
    primaryDark = Color(0xFF065F46),
    primaryLight = Color(0xFFECFDF5),
    primaryBorder = Color(0xFFA7F3D0),
    emeraldPrimary = Color(0xFF047857),

    background = Color(0xFFF8FAFC),
    lightBg = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    surfaceHighlight = Color(0xFFF1F5F9),
    surfaceMuted = Color(0xFFF1F5F9),
    cardBackground = Color(0xFFFFFFFF),
    surfaceCard = Color(0xFFFFFFFF),

    border = Color(0xFFE2E8F0),
    borderSubtle = Color(0xFFF1F5F9),
    borderLight = Color(0xFFE2E8F0),
    surfaceCardBorder = Color(0xFFE2E8F0),
    cardBorder = Color(0xFFE2E8F0),

    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF64748B),
    textMutedLight = Color(0xFF94A3B8),
    slateTitle = Color(0xFF0F172A),
    slateSub = Color(0xFF64748B),

    debit = Color(0xFF0F172A),
    debitDanger = Color(0xFFDC2626),
    debitBg = Color(0xFFFEF2F2),
    debitBorder = Color(0xFFFECACA),

    credit = Color(0xFF047857),
    creditMuted = Color(0xFF10B981),
    creditBg = Color(0xFFECFDF5),
    creditBorder = Color(0xFFA7F3D0),

    warning = Color(0xFFD97706),
    warningLight = Color(0xFFF59E0B),
    warningAmber = Color(0xFFD97706),
    warningAmberDot = Color(0xFFF59E0B),
    warningAmberBg = Color(0xFFFFFBEB),
    warningAmberBorder = Color(0xFFFDE68A),
    warningContainer = Color(0xFFFFFBEB),
    warningBorder = Color(0xFFFDE68A),

    success = Color(0xFF047857),
    successContainer = Color(0xFFECFDF5),
    successBorder = Color(0xFFA7F3D0),

    danger = Color(0xFFDC2626),
    dangerContainer = Color(0xFFFEF2F2),
    dangerBorder = Color(0xFFFECACA),

    unresolved = Color(0xFFD97706),
    unresolvedBg = Color(0xFFFFFBEB),
    unresolvedBorder = Color(0xFFFDE68A),

    upiBadgeBg = Color(0xFFEFF6FF),
    upiBadgeText = Color(0xFF2563EB),

    filterChipBg = Color(0xFFF1F5F9),
    filterChipSelectedBg = Color(0xFF0F172A),
    filterChipSelectedText = Color(0xFFFFFFFF),

    chipBackground = Color(0xFFF1F5F9),
    chipBorder = Color(0xFFE2E8F0),

    telemetryBackground = Color(0xFFF8FAFC),
    telemetryBorder = Color(0xFFE2E8F0),

    bottomNavBackground = Color(0xFFFFFFFF),
    bottomNavBorder = Color(0xFFE2E8F0),
    bottomNavPillSelected = Color(0xFF0F172A),
    bottomNavTextActive = Color(0xFF047857),
    bottomNavTextInactive = Color(0xFF64748B),
    bottomNavBadgeAmber = Color(0xFFD97706),

    isDark = false
)

val ArcDarkColorScheme = ArcColorScheme(
    primary = Color(0xFF10B981),
    primaryDark = Color(0xFF059669),
    primaryLight = Color(0xFF064E3B),
    primaryBorder = Color(0xFF047857),
    emeraldPrimary = Color(0xFF10B981),

    background = Color(0xFF090D16),
    lightBg = Color(0xFF090D16),
    surface = Color(0xFF131722),
    surfaceElevated = Color(0xFF181D2A),
    surfaceHighlight = Color(0xFF1E2433),
    surfaceMuted = Color(0xFF1E2433),
    cardBackground = Color(0xFF131722),
    surfaceCard = Color(0xFF131722),

    border = Color(0xFF1F2636),
    borderSubtle = Color(0xFF262E40),
    borderLight = Color(0xFF1F2636),
    surfaceCardBorder = Color(0xFF1F2636),
    cardBorder = Color(0xFF1F2636),

    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    textMutedLight = Color(0xFF475569),
    slateTitle = Color(0xFFF8FAFC),
    slateSub = Color(0xFF94A3B8),

    debit = Color(0xFFF8FAFC),
    debitDanger = Color(0xFFF87171),
    debitBg = Color(0xFF2C1418),
    debitBorder = Color(0xFF4C1D24),

    credit = Color(0xFF10B981),
    creditMuted = Color(0xFF34D399),
    creditBg = Color(0xFF09291B),
    creditBorder = Color(0xFF125238),

    warning = Color(0xFFF59E0B),
    warningLight = Color(0xFFFBBF24),
    warningAmber = Color(0xFFF59E0B),
    warningAmberDot = Color(0xFFFBBF24),
    warningAmberBg = Color(0xFF2B1F09),
    warningAmberBorder = Color(0xFF4A3612),
    warningContainer = Color(0xFF281D0B),
    warningBorder = Color(0xFF45300F),

    success = Color(0xFF10B981),
    successContainer = Color(0xFF09291B),
    successBorder = Color(0xFF125238),

    danger = Color(0xFFEF4444),
    dangerContainer = Color(0xFF2C1418),
    dangerBorder = Color(0xFF4C1D24),

    unresolved = Color(0xFFF87171),
    unresolvedBg = Color(0xFF2C1418),
    unresolvedBorder = Color(0xFF4C1D24),

    upiBadgeBg = Color(0xFF1E293B),
    upiBadgeText = Color(0xFF60A5FA),

    filterChipBg = Color(0xFF1E2433),
    filterChipSelectedBg = Color(0xFFF8FAFC),
    filterChipSelectedText = Color(0xFF090D16),

    chipBackground = Color(0xFF1E2433),
    chipBorder = Color(0xFF1F2636),

    telemetryBackground = Color(0xFF0E131F),
    telemetryBorder = Color(0xFF1F2636),

    bottomNavBackground = Color(0xFF131722),
    bottomNavBorder = Color(0xFF1F2636),
    bottomNavPillSelected = Color(0xFF1E2433),
    bottomNavTextActive = Color(0xFF10B981),
    bottomNavTextInactive = Color(0xFF64748B),
    bottomNavBadgeAmber = Color(0xFFF59E0B),

    isDark = true
)

val LocalArcColors = staticCompositionLocalOf { ArcLightColorScheme }

object ArcColors {
    val Light: ArcColorScheme = ArcLightColorScheme
    val Dark: ArcColorScheme = ArcDarkColorScheme

    val current: ArcColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalArcColors.current

    val isDark: Boolean @Composable @ReadOnlyComposable get() = LocalArcColors.current.isDark

    // Dynamic Composable Color Token Accessors
    val Primary: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.primary
    val PrimaryDark: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.primaryDark
    val PrimaryLight: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.primaryLight
    val PrimaryBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.primaryBorder
    val EmeraldPrimary: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.emeraldPrimary

    val Background: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.background
    val LightBg: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.lightBg
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.surface
    val SurfaceElevated: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.surfaceElevated
    val SurfaceHighlight: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.surfaceHighlight
    val SurfaceMuted: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.surfaceMuted
    val CardBackground: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.cardBackground
    val SurfaceCard: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.surfaceCard

    val Border: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.border
    val BorderSubtle: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.borderSubtle
    val BorderLight: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.borderLight
    val SurfaceCardBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.surfaceCardBorder
    val CardBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.cardBorder

    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.textSecondary
    val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.textMuted
    val TextMutedLight: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.textMutedLight
    val SlateTitle: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.slateTitle
    val SlateSub: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.slateSub

    val Debit: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.debit
    val DebitDanger: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.debitDanger
    val DebitBg: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.debitBg
    val DebitBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.debitBorder

    val Credit: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.credit
    val CreditMuted: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.creditMuted
    val CreditBg: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.creditBg
    val CreditBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.creditBorder

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

    val Unresolved: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.unresolved
    val UnresolvedBg: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.unresolvedBg
    val UnresolvedBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.unresolvedBorder

    val UpiBadgeBg: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.upiBadgeBg
    val UpiBadgeText: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.upiBadgeText

    val FilterChipBg: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.filterChipBg
    val FilterChipSelectedBg: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.filterChipSelectedBg
    val FilterChipSelectedText: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.filterChipSelectedText

    val ChipBackground: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.chipBackground
    val ChipBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.chipBorder

    val TelemetryBackground: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.telemetryBackground
    val TelemetryBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.telemetryBorder

    val BottomNavBackground: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.bottomNavBackground
    val BottomNavBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.bottomNavBorder
    val BottomNavPillSelected: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.bottomNavPillSelected
    val BottomNavTextActive: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.bottomNavTextActive
    val BottomNavTextInactive: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.bottomNavTextInactive
    val BottomNavBadgeAmber: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.bottomNavBadgeAmber
}
