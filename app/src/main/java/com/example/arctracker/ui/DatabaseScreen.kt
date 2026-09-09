package com.example.arctracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DatabaseScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9FB))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 100.dp)
    ) {
        DatabaseInfoCard()
        Spacer(modifier = Modifier.height(16.dp))
        DatabaseActionsCard()
        Spacer(modifier = Modifier.height(16.dp))
        DatabaseFooterInfo()
    }
}

@Composable
fun DatabaseInfoCard() {
    SettingsCard(title = "Database Info") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(lightPurpleColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "DB",
                    tint = purpleColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                InfoRow("Type", "SQLite (Local)")
                InfoRow("Size", "2.48 MB")
                InfoRow("Version", "1")
                InfoRow("Path", "/data/user/0/com.arctracker/\ndatabases/arctracker.db")
                InfoRow("Created On", "Aug 20, 2025, 09:12")
                InfoRow("Last Modified", "Aug 29, 2025, 20:54")
            }
        }
    }
}

@Composable
fun InfoRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Text(
            text = key,
            fontSize = 13.sp,
            color = subtitleColor,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = textColor,
            modifier = Modifier.weight(0.65f)
        )
    }
}

@Composable
fun DatabaseActionsCard() {
    val greenColor = Color(0xFF4CAF50)
    val lightGreenColor = Color(0xFFE8F5E9)
    val blueColor = Color(0xFF2196F3)
    val lightBlueColor = Color(0xFFE3F2FD)

    SettingsCard(title = "Database Actions") {
        SettingsRow(
            icon = Icons.Filled.Build,
            title = "Optimize Database",
            subtitle = "Clean and optimize database for better performance",
            isLast = false,
            onClick = {}
        )
        SettingsRow(
            icon = Icons.Filled.List,
            title = "Rebuild Index",
            subtitle = "Rebuild database index for faster queries",
            iconTint = greenColor,
            iconBgColor = lightGreenColor,
            isLast = false,
            onClick = {}
        )
        SettingsRow(
            icon = Icons.Filled.Share,
            title = "Export Database",
            subtitle = "Export database file (.db) to storage",
            iconTint = blueColor,
            iconBgColor = lightBlueColor,
            isLast = false,
            onClick = {}
        )
        SettingsRow(
            icon = Icons.Filled.CheckCircle,
            title = "Check Integrity",
            subtitle = "Check database for any corruption or errors",
            iconTint = greenColor,
            iconBgColor = lightGreenColor,
            isLast = false,
            onClick = {}
        )
        SettingsRow(
            icon = Icons.Filled.Menu,
            title = "View Tables",
            subtitle = "View all database tables and records",
            iconTint = blueColor,
            iconBgColor = lightBlueColor,
            isLast = true,
            onClick = {}
        )
    }
}

@Composable
fun DatabaseFooterInfo() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = lightPurpleColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "Info",
                tint = purpleColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "All data is stored locally on your device.",
                    fontSize = 13.sp,
                    color = textColor
                )
                Text(
                    text = "No data leaves your device.",
                    fontSize = 13.sp,
                    color = textColor
                )
            }
        }
    }
}
