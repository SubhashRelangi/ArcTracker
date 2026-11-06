package com.subhashrelangi.arctracker.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Universal ArcTracker shape tokens matching LedgerPage and HomePage designs.
 * Enforces fully circular / border round pill shapes for all buttons, chips, and floating pills across the app.
 */
object ArcShapes {
    val Card = RoundedCornerShape(18.dp)
    val CardMedium = RoundedCornerShape(16.dp)
    val CardElevated = RoundedCornerShape(18.dp)
    val Pill = CircleShape
    val LargePill = CircleShape
    val SmallPill = CircleShape
    val MicroChip = CircleShape
    val Chip = CircleShape
    val InnerBox = RoundedCornerShape(10.dp)
    val SearchBar = RoundedCornerShape(16.dp)
    val FloatingSummary = CircleShape
    val BottomNav = RoundedCornerShape(32.dp)
    val NavItemPill = CircleShape
    val NavItemInactive = RoundedCornerShape(16.dp)
    val Button = CircleShape
    val ButtonSecondary = CircleShape
}
