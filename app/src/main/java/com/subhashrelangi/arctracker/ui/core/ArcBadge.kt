package com.subhashrelangi.arctracker.ui.core

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.ui.theme.ArcColors
import com.subhashrelangi.arctracker.ui.theme.ArcShapes
import com.subhashrelangi.arctracker.ui.theme.ArcTypography

/**
 * Universal UPI pill badge matching LedgerPage header.
 */
@Composable
fun ArcUpiBadge(
    modifier: Modifier = Modifier,
    text: String = "UPI"
) {
    Surface(
        modifier = modifier,
        shape = ArcShapes.SmallPill,
        color = ArcColors.UpiBadgeBg
    ) {
        Text(
            text = text,
            style = ArcTypography.BadgeText,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Live indicator dot (green, amber, or custom color).
 */
@Composable
fun ArcStatusDot(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF22C55E),
    size: Dp = 6.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color, CircleShape)
    )
}

/**
 * Pill count badge, e.g., "1 txns" or "42".
 */
@Composable
fun ArcCountBadge(
    countText: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF1E2432),
    textColor: Color = ArcColors.TextSecondary
) {
    Box(
        modifier = modifier
            .background(backgroundColor, ArcShapes.SmallPill)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = countText,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
