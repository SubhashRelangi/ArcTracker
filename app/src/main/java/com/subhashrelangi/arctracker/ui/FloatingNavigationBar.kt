package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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

sealed class NavItem(val route: String, val title: String) {
    object Home : NavItem("Home", "Home")
    object Ledger : NavItem("Transactions", "Ledger")
    object Review : NavItem("Pending", "Review")
    object Insights : NavItem("Insights", "Insights")
    object Settings : NavItem("Settings", "Settings")
}

/**
 * Bottom Navigation Bar styled to match the dark cyberpunk fintech aesthetic in Ui-Designs/HomePage.png exactly.
 */
@Composable
fun FloatingNavigationBar(
    currentRoute: String,
    pendingReviewCount: Int = 3,
    onNavigate: (NavItem) -> Unit
) {
    val items = listOf(
        NavItem.Home,
        NavItem.Ledger,
        NavItem.Review,
        NavItem.Insights,
        NavItem.Settings
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(32.dp),
                spotColor = Color.Black,
                ambientColor = Color.Black
            ),
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFF13161F),
        border = BorderStroke(1.dp, Color(0xFF1F2432))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = currentRoute == item.route || currentRoute == item.title
                BottomBarItem(
                    item = item,
                    isSelected = isSelected,
                    badgeCount = if (item == NavItem.Review && pendingReviewCount > 0) pendingReviewCount else null,
                    onClick = { onNavigate(item) }
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    item: NavItem,
    isSelected: Boolean,
    badgeCount: Int? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val activeColor = Color.White
    val inactiveColor = Color(0xFF94A3B8)
    val contentColor = if (isSelected) activeColor else inactiveColor

    // If selected (like Home in design), the pill background wraps both icon and label
    val pillModifier = if (isSelected) {
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF222734))
            .padding(horizontal = 18.dp, vertical = 6.dp)
    } else {
        Modifier
            .clip(RoundedCornerShape(16.dp))
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
            when (item) {
                NavItem.Home -> GridFourSquaresIcon(
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
                NavItem.Ledger -> ReceiptJaggedIcon(
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
                NavItem.Review -> ReviewInboxTrayIcon(
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
                NavItem.Insights -> ZigzagChartIcon(
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
                NavItem.Settings -> SlidersTuneIcon(
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
            }

            // Amber badge for Review tab (matches crop_bottom_nav.png)
            if (badgeCount != null) {
                Box(
                    modifier = Modifier
                        .offset(x = 10.dp, y = (-7).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFBBF24)),
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
            text = item.title,
            fontSize = 11.sp,
            color = contentColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
