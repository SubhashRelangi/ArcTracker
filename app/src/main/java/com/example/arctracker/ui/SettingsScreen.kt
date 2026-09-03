package com.example.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9FB)) // A subtle off-white minimal background
            .padding(16.dp)
    ) {
        AccountsSection()
        Spacer(modifier = Modifier.height(16.dp))
        TrackerStatusSection()
    }
}

@Composable
fun AccountsSection() {
    val purpleColor = Color(0xFF673AB7)
    val lightPurpleColor = Color(0xFFEDE7F6)
    val textColor = Color(0xFF1E1E1E)
    val subtitleColor = Color(0xFF757575)
    val borderColor = Color(0xFFF0F0F0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Profile Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { /* Handle click */ },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile Avatar
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(lightPurpleColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Profile",
                        tint = purpleColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Profile",
                        color = purpleColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Subhash Relangi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = textColor
                    )
                    Text(
                        text = "subhashrelangi@gmail.com",
                        fontSize = 11.sp,
                        color = subtitleColor
                    )
                }
                
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Go",
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                color = borderColor,
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // Account & Privacy Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { /* Handle click */ },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(lightPurpleColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock, 
                        contentDescription = "Account & Privacy",
                        tint = purpleColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Account & Privacy",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = textColor
                    )
                    Text(
                        text = "Manage your account, logout & more",
                        fontSize = 11.sp,
                        color = subtitleColor
                    )
                }
                
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Go",
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun TrackerStatusSection() {
    val purpleColor = Color(0xFF673AB7)
    val lightPurpleColor = Color(0xFFEDE7F6)
    val textColor = Color(0xFF1E1E1E)
    val subtitleColor = Color(0xFF757575)
    val borderColor = Color(0xFFF0F0F0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Tracker Status",
                color = purpleColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Item 1: Auto Tracking
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon Background
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(lightPurpleColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle, // Placeholder for shield
                        contentDescription = "Auto Tracking",
                        tint = purpleColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto Tracking",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = textColor
                    )
                    Text(
                        text = "Actively listening to notifications & SMS",
                        fontSize = 11.sp,
                        color = subtitleColor
                    )
                }
                
                var isTrackingEnabled by remember { mutableStateOf(true) }
                Switch(
                    checked = isTrackingEnabled,
                    onCheckedChange = { isTrackingEnabled = it },
                    modifier = Modifier.scale(0.7f).offset(x = 8.dp),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = purpleColor,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFE0E0E0),
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            HorizontalDivider(
                color = borderColor,
                thickness = 1.dp,
                modifier = Modifier.padding(start = 44.dp) // Aligns with text
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Item 2: Tracking Stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { /* Handle click */ },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(lightPurpleColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List, // Placeholder for library/bar chart
                        contentDescription = "Tracking Stats",
                        tint = purpleColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tracking Stats",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = textColor
                    )
                    Text(
                        text = "Today: 8 transactions • This month: 126", // Real stats can be hooked up later
                        fontSize = 11.sp,
                        color = subtitleColor
                    )
                }
                
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Go",
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
