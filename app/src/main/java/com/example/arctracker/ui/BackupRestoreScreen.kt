package com.example.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BackupRestoreScreen(onNavigate: (String) -> Unit = {}) {
    val context = LocalContext.current
    val sharedPrefs = remember {
        context.getSharedPreferences("ArcTrackerPrefs", android.content.Context.MODE_PRIVATE)
    }

    var wifiOnly by remember {
        mutableStateOf(sharedPrefs.getBoolean("isBackupWifiOnlyEnabled", true))
    }
    var includeAttachments by remember {
        mutableStateOf(sharedPrefs.getBoolean("isBackupAttachmentsEnabled", false))
    }
    var backupFrequency by remember {
        mutableStateOf(sharedPrefs.getString("backupFrequency", "Daily") ?: "Daily")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        DriveAccountSection()
        Spacer(modifier = Modifier.height(16.dp))
        BackupStatusSection(backupFrequency = backupFrequency)
        Spacer(modifier = Modifier.height(16.dp))
        BackupSection(
            wifiOnly = wifiOnly,
            includeAttachments = includeAttachments,
            backupFrequency = backupFrequency,
            onWifiOnlyChange = {
                wifiOnly = it
                sharedPrefs.edit().putBoolean("isBackupWifiOnlyEnabled", it).apply()
            },
            onIncludeAttachmentsChange = {
                includeAttachments = it
                sharedPrefs.edit().putBoolean("isBackupAttachmentsEnabled", it).apply()
            },
            onFrequencyChange = {
                backupFrequency = when (backupFrequency) {
                    "Daily" -> "Weekly"
                    "Weekly" -> "Monthly"
                    else -> "Daily"
                }
                sharedPrefs.edit().putString("backupFrequency", backupFrequency).apply()
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        RestoreSection()
        Spacer(modifier = Modifier.height(16.dp))
        BackupFooterInfo()
    }
}

@Composable
fun DriveAccountSection() {
    SettingsCard(title = "Google Drive Account") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GoogleDriveLogo(modifier = Modifier.size(30.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "subhashrelangi@gmail.com",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = textColor
                )
                Text(
                    text = "15 GB of 15 GB available",
                    fontSize = 11.sp,
                    color = subtitleColor
                )
            }
            OutlinedButton(
                onClick = {},
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = purpleColor
                ),
                border = BorderStroke(1.dp, purpleColor),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Change Account", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun BackupStatusSection(backupFrequency: String) {
    SettingsCard(title = "Backup Status") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(purpleColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDone,
                    contentDescription = "Backup Status",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                InfoRow("Last Backup", "Aug 29, 2025, 20:40")
                InfoRow("Size", "2.48 MB")
                InfoRow("Location", "Google Drive ArcTracker Backups")
                InfoRow("Auto Backup", "Enabled ($backupFrequency)")
            }
        }
    }
}

@Composable
fun BackupSection(
    wifiOnly: Boolean,
    includeAttachments: Boolean,
    backupFrequency: String,
    onWifiOnlyChange: (Boolean) -> Unit,
    onIncludeAttachmentsChange: (Boolean) -> Unit,
    onFrequencyChange: () -> Unit
) {
    SettingsCard(title = "Backup") {
        SettingsRow(
            icon = Icons.Filled.CloudUpload,
            title = "Back Up Now",
            subtitle = "Upload current data to Google Drive",
            isLast = false,
            onClick = {}
        )
        SettingsRow(
            icon = Icons.Filled.Schedule,
            title = "Auto Backup",
            subtitle = "Schedule automatic backups",
            isLast = false,
            onClick = onFrequencyChange,
            rightContent = { FrequencyLabel(backupFrequency) }
        )
        SettingsRow(
            icon = Icons.Filled.CalendarToday,
            title = "Backup Frequency",
            subtitle = "Choose how often to back up",
            isLast = false,
            onClick = onFrequencyChange,
            rightContent = { FrequencyLabel(backupFrequency) }
        )
        SettingsSwitchRow(
            icon = Icons.Filled.Wifi,
            title = "Backup Network",
            subtitle = "Back up using Wi-Fi only",
            checked = wifiOnly,
            onCheckedChange = onWifiOnlyChange,
            isLast = false
        )
        SettingsSwitchRow(
            icon = Icons.Filled.AttachFile,
            title = "Include Attachments (Future)",
            subtitle = "Include files in future backups",
            checked = includeAttachments,
            onCheckedChange = onIncludeAttachmentsChange,
            isLast = true
        )
    }
}

@Composable
fun FrequencyLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = subtitleColor
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Change",
            tint = textColor,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun RestoreSection() {
    SettingsCard(title = "Restore") {
        SettingsRow(
            icon = Icons.Filled.CloudDownload,
            title = "Restore from Google Drive",
            subtitle = "Download and restore your data",
            isLast = false,
            onClick = {}
        )
        SettingsRow(
            icon = Icons.Filled.History,
            title = "View Backup History",
            subtitle = "See all your previous backups",
            isLast = true,
            onClick = {}
        )
    }
}

@Composable
fun BackupFooterInfo() {
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
                    text = "Backups are saved to your Google Drive.",
                    fontSize = 11.sp,
                    color = textColor
                )
                Text(
                    text = "Your data is safe and can be restored anytime.",
                    fontSize = 11.sp,
                    color = textColor
                )
            }
        }
    }
}

/**
 * Simplified vector rendering of the Google Drive triangle logo
 * (official geometry, viewBox 0 0 87.3 78, straight-line approximation).
 */
@Composable
fun GoogleDriveLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun px(v: Float) = v / 87.3f * w
        fun py(v: Float) = v / 78f * h

        fun drivePath(points: List<Pair<Float, Float>>): Path {
            val path = Path()
            path.moveTo(px(points[0].first), py(points[0].second))
            points.drop(1).forEach { path.lineTo(px(it.first), py(it.second)) }
            path.close()
            return path
        }

        // Bottom-left corner cap (blue)
        drawPath(
            path = drivePath(listOf(6.6f to 66.85f, 13.75f to 76.8f, 27.5f to 53f, 0f to 53f)),
            color = Color(0xFF0066DA),
            style = Fill
        )
        // Left arm (green)
        drawPath(
            path = drivePath(
                listOf(
                    43.65f to 25f, 29.9f to 1.2f, 26.6f to 4.5f,
                    1.2f to 48.5f, 0f to 53f, 27.5f to 53f
                )
            ),
            color = Color(0xFF00AC47),
            style = Fill
        )
        // Bottom-right corner cap (red)
        drawPath(
            path = drivePath(
                listOf(
                    73.55f to 76.8f, 76.85f to 73.5f, 78.45f to 70.75f,
                    86.1f to 57.5f, 87.3f to 53f, 59.8f to 53f, 65.65f to 64.5f
                )
            ),
            color = Color(0xFFEA4335),
            style = Fill
        )
        // Top apex (dark green)
        drawPath(
            path = drivePath(listOf(43.65f to 25f, 57.4f to 1.2f, 52.9f to 0f, 34.4f to 0f, 29.9f to 1.2f)),
            color = Color(0xFF00832D),
            style = Fill
        )
        // Bottom band (blue)
        drawPath(
            path = drivePath(
                listOf(
                    59.8f to 53f, 27.5f to 53f, 13.75f to 76.8f,
                    18.25f to 78f, 69.05f to 78f, 73.55f to 76.8f
                )
            ),
            color = Color(0xFF2684FC),
            style = Fill
        )
        // Right arm (yellow)
        drawPath(
            path = drivePath(
                listOf(
                    73.4f to 26.5f, 60.7f to 4.5f, 57.4f to 1.2f,
                    43.65f to 25f, 57.4f to 48.8f, 84.85f to 48.8f, 83.65f to 44.3f
                )
            ),
            color = Color(0xFFFFBA00),
            style = Fill
        )
    }
}