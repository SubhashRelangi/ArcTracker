package com.subhashrelangi.arctracker.ui.review

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Global ArcTracker Fintech Dark Design Tokens.
 * Consistent across ReviewPage and the entire application.
 */
object ArcColors {
    val Background = Color(0xFF090C10)
    val Surface = Color(0xFF13161F)
    val SurfaceElevated = Color(0xFF181C26)
    val SurfaceMuted = Color(0xFF1F2432)
    val SurfaceCard = Color(0xFF13161F)
    val SurfaceCardBorder = Color(0xFF1E2330)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)

    val Debit = Color(0xFFEF4444)
    val Credit = Color(0xFF10B981)
    val Warning = Color(0xFFF59E0B)
    val WarningLight = Color(0xFFFBBF24)
    val WarningContainer = Color(0xFF281D0B)
    val WarningBorder = Color(0xFF45300F)

    val Success = Color(0xFF10B981)
    val SuccessContainer = Color(0xFF0D2218)
    val SuccessBorder = Color(0xFF1B4D35)

    val Danger = Color(0xFFEF4444)
    val DangerContainer = Color(0xFF2C1418)
    val DangerBorder = Color(0xFF4C1D24)

    val Border = Color(0xFF1F2432)
    val BorderSubtle = Color(0xFF181C26)

    val ChipBackground = Color(0xFF181C26)
    val ChipBorder = Color(0xFF252B3A)

    val TelemetryBackground = Color(0xFF0C0E14)
    val TelemetryBorder = Color(0xFF1B202D)
}

object ArcShapes {
    val Card = RoundedCornerShape(16.dp)
    val CardElevated = RoundedCornerShape(18.dp)
    val Pill = RoundedCornerShape(24.dp)
    val Button = RoundedCornerShape(24.dp)
    val ButtonSecondary = RoundedCornerShape(14.dp)
    val Chip = RoundedCornerShape(8.dp)
    val MicroChip = RoundedCornerShape(6.dp)
    val InnerBox = RoundedCornerShape(10.dp)
}

object ArcSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}
