package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DeveloperOptionsScreen(onNavigate: (String) -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9FB))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 100.dp)
    ) {
        // Top Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F5FF)),
            border = BorderStroke(1.dp, Color(0xFFEDE7F6)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(purpleColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "These options are intended for advanced users. Changing values may affect tracking accuracy. Use only if you know what you are doing.",
                    fontSize = 12.sp,
                    color = Color(0xFF5E5E5E),
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Options List
        SettingsCard {
            SettingsRow(
                icon = Icons.Filled.Smartphone,
                title = "Initial Welcome Screen",
                subtitle = "Preview redesigned on-device onboarding page",
                onClick = { onNavigate("InitialOnboarding") }
            )
            SettingsRow(
                icon = Icons.Filled.Search,
                title = "Regex Patterns",
                subtitle = "View & edit amount, name, type patterns",
                onClick = { onNavigate("RegexPatterns") }
            )
            SettingsRow(
                icon = Icons.Filled.Science,
                title = "Test Parser",
                subtitle = "Test parsing on custom message",
                isLast = false,
                onClick = {}
            )
            SettingsRow(
                icon = Icons.Filled.Description,
                title = "Logs",
                subtitle = "View tracking and parsing logs",
                isLast = false,
                onClick = {}
            )
            SettingsRow(
                icon = Icons.Filled.Settings,
                title = "Advanced Settings",
                subtitle = "Database, performance & memory options",
                isLast = false,
                onClick = {}
            )
            SettingsRow(
                icon = Icons.Filled.Science,
                title = "Experimental Features",
                subtitle = "Try new features (beta)",
                isLast = false,
                onClick = {}
            )
            SettingsRow(
                icon = Icons.Filled.DeveloperMode,
                title = "Developer UI",
                subtitle = "Enable debugging tools and developer UI",
                isLast = false,
                onClick = {}
            )
            SettingsRow(
                icon = Icons.Filled.Save,
                title = "Export / Import Config",
                subtitle = "Backup or restore developer settings",
                isLast = false,
                onClick = {}
            )
            SettingsRow(
                icon = Icons.Filled.Refresh,
                title = "Reset to Defaults",
                subtitle = "Restore all developer settings",
                isLast = true,
                onClick = {}
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F5FF)),
            border = BorderStroke(1.dp, Color(0xFFEDE7F6)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Need help?",
                    tint = Color(0xFF3F51B5), // A bit more blueish purple like the image
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Need help?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF3F51B5)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "These tools are for debugging and customization. Incorrect changes may affect the app's behavior.",
                        fontSize = 12.sp,
                        color = Color(0xFF5E5E5E),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
