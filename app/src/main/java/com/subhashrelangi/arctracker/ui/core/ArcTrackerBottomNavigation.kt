package com.subhashrelangi.arctracker.ui.core

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.ui.GridFourSquaresIcon
import com.subhashrelangi.arctracker.ui.ReceiptJaggedIcon
import com.subhashrelangi.arctracker.ui.ReviewInboxTrayIcon
import com.subhashrelangi.arctracker.ui.SlidersTuneIcon
import com.subhashrelangi.arctracker.ui.ZigzagChartIcon
import com.subhashrelangi.arctracker.ui.theme.ArcColors
import com.subhashrelangi.arctracker.ui.theme.ArcShapes
import com.subhashrelangi.arctracker.ui.theme.ArcTypography

/**
 * Universal Floating Bottom Navigation Bar for ArcTracker.
 * Extracted directly from the verified LedgerPage/HomePage implementation.
 *
 * Visual structure:
 * ┌──────────────────────────────────────────────┐
 * │                                              │
 * │  ▦        ▣        ▣³        ↗        ☷     │
 * │ Home     Ledger    Review    Insights Settings│
 * │                                              │
 * └──────────────────────────────────────────────┘
 */
@Composable
fun ArcTrackerBottomNavigation(
    currentDestination: ArcDestination,
    onNavigate: (ArcDestination) -> Unit,
    modifier: Modifier = Modifier,
    reviewBadgeCount: Int = 0
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .shadow(
                elevation = 20.dp,
                shape = ArcShapes.BottomNav,
                spotColor = Color.Black,
                ambientColor = Color.Black
            ),
        shape = ArcShapes.BottomNav,
        color = ArcColors.BottomNavBackground,
        border = BorderStroke(1.dp, ArcColors.BottomNavBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArcDestination.values().forEach { destination ->
                val isSelected = currentDestination == destination
                ArcBottomNavItem(
                    destination = destination,
                    isSelected = isSelected,
                    badgeCount = if (destination == ArcDestination.REVIEW && reviewBadgeCount > 0) reviewBadgeCount else null,
                    onClick = { onNavigate(destination) }
                )
            }
        }
    }
}

@Composable
private fun ArcBottomNavItem(
    destination: ArcDestination,
    isSelected: Boolean,
    badgeCount: Int? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val contentColor = if (isSelected) ArcColors.BottomNavTextActive else ArcColors.BottomNavTextInactive

    val pillModifier = if (isSelected) {
        Modifier
            .clip(ArcShapes.NavItemPill)
            .background(ArcColors.BottomNavPillSelected)
            .padding(horizontal = 18.dp, vertical = 6.dp)
    } else {
        Modifier
            .clip(ArcShapes.NavItemInactive)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    }

    Column(
        modifier = pillModifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (destination) {
                ArcDestination.HOME -> GridFourSquaresIcon(
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
                ArcDestination.LEDGER -> ReceiptJaggedIcon(
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
                ArcDestination.REVIEW -> ReviewInboxTrayIcon(
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
                ArcDestination.INSIGHTS -> ZigzagChartIcon(
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
                ArcDestination.SETTINGS -> SlidersTuneIcon(
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
            }

            // Amber badge for Review tab
            if (badgeCount != null) {
                Box(
                    modifier = Modifier
                        .offset(x = 10.dp, y = (-7).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(ArcColors.BottomNavBadgeAmber),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = Color(0xFF090C10),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = destination.title,
            style = if (isSelected) ArcTypography.NavLabelActive else ArcTypography.NavLabel
        )
    }
}
