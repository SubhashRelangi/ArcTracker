package com.subhashrelangi.arctracker.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.subhashrelangi.arctracker.ui.theme.ArcColors
import com.subhashrelangi.arctracker.ui.theme.ArcDimensions

enum class ArcIconButtonVariant {
    /** Dark background with subtle border, e.g., Search button */
    DARK,
    /** Solid white background, e.g., Add transaction button */
    LIGHT,
    /** Transparent with hover/click ripple */
    GHOST
}

/**
 * Universal circular icon button matching LedgerPage header buttons.
 * Dimensions: 40dp circle, centered vector icon.
 */
@Composable
fun ArcIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ArcIconButtonVariant = ArcIconButtonVariant.DARK,
    size: Dp = ArcDimensions.IconButtonSize,
    iconSize: Dp = if (variant == ArcIconButtonVariant.LIGHT) 22.dp else 18.dp,
    enabled: Boolean = true
) {
    val bgModifier = when (variant) {
        ArcIconButtonVariant.DARK -> Modifier
            .background(Color(0xFF161B22), CircleShape)
            .border(1.dp, ArcColors.BorderLight, CircleShape)
        ArcIconButtonVariant.LIGHT -> Modifier
            .background(Color.White, CircleShape)
        ArcIconButtonVariant.GHOST -> Modifier
            .background(Color.Transparent, CircleShape)
    }

    val iconTint = when (variant) {
        ArcIconButtonVariant.DARK -> ArcColors.TextPrimary
        ArcIconButtonVariant.LIGHT -> Color.Black
        ArcIconButtonVariant.GHOST -> ArcColors.TextSecondary
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(bgModifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}
