package com.subhashrelangi.arctracker.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Universal ArcTracker typography hierarchy.
 * Combines high-density financial typography with tabular monospace numerals.
 */
object ArcTypography {
    val BrandTitle = TextStyle(
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        color = ArcColors.Light.textPrimary,
        letterSpacing = (-0.2).sp
    )

    val BrandSubtitle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.Monospace,
        color = ArcColors.Light.textSecondary,
        letterSpacing = 0.3.sp
    )

    val BadgeText = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = ArcColors.Light.upiBadgeText
    )

    val NavLabel = TextStyle(
        fontSize = 11.sp,
        color = ArcColors.Light.bottomNavTextInactive
    )

    val NavLabelActive = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = ArcColors.Light.bottomNavTextActive
    )

    val FinancialMonospace = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )

    val SecurityFooter = TextStyle(
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = ArcColors.Light.textMuted,
        letterSpacing = 0.4.sp
    )
}
