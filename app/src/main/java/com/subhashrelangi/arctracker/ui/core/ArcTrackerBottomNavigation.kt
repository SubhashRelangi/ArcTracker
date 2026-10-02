package com.subhashrelangi.arctracker.ui.core

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.ui.GridFourSquaresIcon
import com.subhashrelangi.arctracker.ui.ReceiptJaggedIcon
import com.subhashrelangi.arctracker.ui.ReviewInboxTrayIcon
import com.subhashrelangi.arctracker.ui.ZigzagChartIcon
import com.subhashrelangi.arctracker.ui.theme.ArcColors
import com.subhashrelangi.arctracker.ui.theme.ArcShapes

/**
 * Universal Floating Glassmorphic Bottom Navigation Bar for ArcTracker.
 *
 * Glass theme aesthetic:
 * - Translucent frosted dark glass surface with top-lit specular refraction
 * - Floating pill capsule hovering over scrolling content
 * - 5 equal-weight responsive columns
 * - White active icon & label, muted gray inactive icon & label (no background pill)
 * - Compact spacing between icon and label
 * - Crisp gear symbol for Settings
 * - Amber review badge on Review icon
 */
@Composable
fun ArcTrackerBottomNavigationBar(
    currentDestination: ArcDestination,
    onNavigate: (ArcDestination) -> Unit,
    modifier: Modifier = Modifier,
    reviewBadgeCount: Int = 0
) {
    val navShape = ArcShapes.BottomNav

    // Navbar background matching home screen background color (ArcColors.Background)
    val navBackground = ArcColors.Background

    // Frosted glass edge with top specular highlight
    val glassBorder = BorderStroke(
        width = 1.dp,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0x66FFFFFF), // Top specular reflection
                Color(0x20FFFFFF), // Subtle mid edge
                Color(0x0DFFFFFF)  // Base edge
            )
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .height(62.dp)
            .shadow(
                elevation = 20.dp,
                shape = navShape,
                spotColor = Color(0x99000000),
                ambientColor = Color(0x66000000)
            )
            .clip(navShape)
            .background(navBackground)
            .border(glassBorder, navShape)
    ) {
        // Subtle top glass specular highlight
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.7f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0x00FFFFFF),
                            Color(0x4DFFFFFF),
                            Color(0x00FFFFFF)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArcDestination.values().forEach { destination ->
                val isSelected = currentDestination == destination
                ArcBottomNavItem(
                    destination = destination,
                    isSelected = isSelected,
                    badgeCount = if (destination == ArcDestination.REVIEW && reviewBadgeCount > 0) reviewBadgeCount else null,
                    onClick = { onNavigate(destination) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

/**
 * Backward-compatible alias for ArcTrackerBottomNavigationBar.
 */
@Composable
fun ArcTrackerBottomNavigation(
    currentDestination: ArcDestination,
    onNavigate: (ArcDestination) -> Unit,
    modifier: Modifier = Modifier,
    reviewBadgeCount: Int = 0
) {
    ArcTrackerBottomNavigationBar(
        currentDestination = currentDestination,
        onNavigate = onNavigate,
        modifier = modifier,
        reviewBadgeCount = reviewBadgeCount
    )
}

@Composable
private fun ArcBottomNavItem(
    destination: ArcDestination,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    badgeCount: Int? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor = if (isSelected) Color.White else Color(0xFF94A3B8)

    Box(
        modifier = modifier
            .clip(ArcShapes.BottomNav)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(20.dp),
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
                    ArcDestination.SETTINGS -> Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(20.dp),
                        tint = contentColor
                    )
                }

                // Amber notification badge on Review icon (upper-right overlay)
                if (badgeCount != null && badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-5).dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(ArcColors.BottomNavBadgeAmber),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                            color = Color(0xFF090C10),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = destination.title,
                color = contentColor,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center
            )
        }
    }
}
