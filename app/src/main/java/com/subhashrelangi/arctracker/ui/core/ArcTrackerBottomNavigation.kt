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
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
    reviewBadgeCount: Int = 0,
    isStatic: Boolean = true,
    isLightMode: Boolean = true
) {
    if (isStatic) {
        // Static docked bottom navigation bar matching light theme
        val navBackground = if (isLightMode) Color.White else ArcColors.Background
        val topBorder = BorderStroke(1.dp, if (isLightMode) Color(0xFFE2E8F0) else Color(0xFF1E2432))

        Surface(
            modifier = modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (isLightMode) 8.dp else 12.dp,
                    spotColor = if (isLightMode) Color(0x1A000000) else Color(0x80000000),
                    ambientColor = if (isLightMode) Color(0x0A000000) else Color(0x40000000)
                ),
            color = navBackground,
            border = topBorder
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(58.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArcDestination.values().forEach { destination ->
                    val isSelected = currentDestination == destination
                    ArcBottomNavItem(
                        destination = destination,
                        isSelected = isSelected,
                        isLightMode = isLightMode,
                        badgeCount = if (destination == ArcDestination.REVIEW && reviewBadgeCount > 0) reviewBadgeCount else null,
                        onClick = { onNavigate(destination) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    } else {
        // Floating pill capsule option
        val navShape = ArcShapes.BottomNav
        val navBackground = if (isLightMode) Color.White else ArcColors.Background
        val glassBorder = if (isLightMode) {
            BorderStroke(1.dp, Color(0xFFE2E8F0))
        } else {
            BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x66FFFFFF),
                        Color(0x20FFFFFF),
                        Color(0x0DFFFFFF)
                    )
                )
            )
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .height(62.dp)
                .shadow(
                    elevation = if (isLightMode) 12.dp else 20.dp,
                    shape = navShape,
                    spotColor = if (isLightMode) Color(0x1A000000) else Color(0x99000000),
                    ambientColor = if (isLightMode) Color(0x0D000000) else Color(0x66000000)
                )
                .clip(navShape)
                .background(navBackground)
                .border(glassBorder, navShape)
        ) {
            if (!isLightMode) {
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
            }

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
                        isLightMode = isLightMode,
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
}

/**
 * Backward-compatible alias for ArcTrackerBottomNavigationBar.
 */
@Composable
fun ArcTrackerBottomNavigation(
    currentDestination: ArcDestination,
    onNavigate: (ArcDestination) -> Unit,
    modifier: Modifier = Modifier,
    reviewBadgeCount: Int = 0,
    isStatic: Boolean = true,
    isLightMode: Boolean = true
) {
    ArcTrackerBottomNavigationBar(
        currentDestination = currentDestination,
        onNavigate = onNavigate,
        modifier = modifier,
        reviewBadgeCount = reviewBadgeCount,
        isStatic = isStatic,
        isLightMode = isLightMode
    )
}

@Composable
private fun ArcBottomNavItem(
    destination: ArcDestination,
    isSelected: Boolean,
    isLightMode: Boolean = false,
    modifier: Modifier = Modifier,
    badgeCount: Int? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor = if (isLightMode) {
        if (isSelected) Color(0xFF0F172A) else Color(0xFF94A3B8)
    } else {
        if (isSelected) Color.White else Color(0xFF94A3B8)
    }

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
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                val iconVector = when (destination) {
                    ArcDestination.HOME -> Icons.Rounded.Home
                    ArcDestination.LEDGER -> Icons.Rounded.ReceiptLong
                    ArcDestination.REVIEW -> Icons.Rounded.RateReview
                    ArcDestination.INSIGHTS -> Icons.Rounded.BarChart
                    ArcDestination.SETTINGS -> Icons.Rounded.Settings
                }

                Icon(
                    imageVector = iconVector,
                    contentDescription = destination.title,
                    modifier = Modifier.size(23.dp),
                    tint = contentColor
                )

                // Amber notification badge on Review icon (upper-right overlay)
                if (badgeCount != null && badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-4).dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD97706)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                            color = Color.White,
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

            Spacer(modifier = Modifier.height(3.dp))

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
