package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class NavItem(val title: String, val icon: ImageVector) {
    object Home : NavItem("Home", Icons.Filled.Home)
    object Transactions : NavItem("Transactions", Icons.Filled.List)
    object Insights : NavItem("Insights", Icons.Filled.Info)
    object Settings : NavItem("Settings", Icons.Filled.Settings)
}

@Composable
fun FloatingNavigationBar(
    currentRoute: String,
    onNavigate: (NavItem) -> Unit
) {
    val items = listOf(NavItem.Home, NavItem.Transactions, NavItem.Insights, NavItem.Settings)
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp) // Tighter floating padding
            .shadow(
                elevation = 16.dp, 
                shape = RoundedCornerShape(32.dp), 
                spotColor = Color(0x33673AB7), // Subtle purple shadow
                ambientColor = Color(0x33673AB7)
            ),
        shape = RoundedCornerShape(32.dp),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp), // Reduced height padding
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = currentRoute == item.title
                NavBarItem(
                    item = item,
                    isSelected = isSelected,
                    onClick = { onNavigate(item) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NavBarItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    // Colors from the image inspiration
    val activeColor = Color(0xFF673AB7) // Deep Purple
    val inactiveColor = Color(0xFF9E9E9E) // Gray for unselected
    
    val contentColor = if (isSelected) activeColor else inactiveColor

    Column(
        modifier = modifier
            .padding(horizontal = 2.dp) // Prevents items from touching
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Removes ripple for a clean UI
                onClick = onClick
            )
            .padding(vertical = 6.dp), // Let weight and center alignment handle horizontal spacing
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp)) // Decreased margin between icon and text
        Text(
            text = item.title,
            fontSize = 11.sp, // Slightly smaller text
            color = contentColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
