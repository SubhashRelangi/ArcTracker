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
        Spacer(modifier = Modifier.height(12.dp))
        DatabaseActionsCard()
        Spacer(modifier = Modifier.height(12.dp))
        DatabaseFooterInfo()
    }
}

@Composable
fun DatabaseInfoCard() {
    SettingsCard(title = "Database Info") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(lightPurpleColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "DB",
                    tint = purpleColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                InfoRow("Type", "SQLite (Local)")
                InfoRow("Size", "2.48 MB")
                InfoRow("Version", "1")
            }
        }
    }
}

@Composable
fun InfoRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Text(
            text = key,
            fontSize = 11.sp,
            color = subtitleColor,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = value,
            fontSize = 11.sp,
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
            subtitle = "Clean and optimize for better performance",
            isLast = false,
            onClick = {}
        )
        SettingsRow(
            icon = Icons.Filled.List,
            title = "Rebuild Index",
            subtitle = "Rebuild index for faster queries",
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
            subtitle = "Check for corruption or errors",
            iconTint = greenColor,
            iconBgColor = lightGreenColor,
            isLast = false,
            onClick = {}
        )
        SettingsRow(
            icon = Icons.Filled.Menu,
            title = "View Tables",
            subtitle = "View all tables and records",
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
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "Info",
                tint = purpleColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "All data stored locally on your device.",
                    fontSize = 11.sp,
                    color = textColor
                )
            }
        }
    }
}
