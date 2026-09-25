package com.example.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val purpleColor = Color(0xFF673AB7)
val lightPurpleColor = Color(0xFFEDE7F6)
val textColor = Color(0xFF1E1E1E)
val subtitleColor = Color(0xFF757575)
val borderColor = Color(0xFFF0F0F0)

@Composable
fun SettingsScreen(onNavigate: (String) -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("ArcTrackerPrefs", android.content.Context.MODE_PRIVATE) }
    
    var autoTracking by remember { 
        mutableStateOf(sharedPrefs.getBoolean("isAutoTrackingEnabled", true)) 
    }
    var smsTracking by remember { 
        mutableStateOf(sharedPrefs.getBoolean("isSmsTrackingEnabled", true)) 
    }
    var notifTracking by remember { 
        mutableStateOf(sharedPrefs.getBoolean("isNotificationTrackingEnabled", true)) 
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9FB))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 100.dp)
    ) {
        AccountsSection()
        Spacer(modifier = Modifier.height(16.dp))
        TrackerStatusSection(
            autoTracking = autoTracking,
            onAutoTrackingChange = { isEnabled ->
                autoTracking = isEnabled
                smsTracking = isEnabled
                notifTracking = isEnabled
                sharedPrefs.edit()
                    .putBoolean("isAutoTrackingEnabled", isEnabled)
                    .putBoolean("isSmsTrackingEnabled", isEnabled)
                    .putBoolean("isNotificationTrackingEnabled", isEnabled)
                    .putBoolean(com.example.arctracker.settings.MonitoringSettingsRepository.KEY_GLOBAL_ENABLED, isEnabled)
                    .apply()
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        DataStorageSection(onNavigate = onNavigate)
        Spacer(modifier = Modifier.height(16.dp))
        TrackingSourcesSection(
            smsTracking = smsTracking,
            notifTracking = notifTracking,
            onSmsTrackingChange = {
                smsTracking = it
                sharedPrefs.edit().putBoolean("isSmsTrackingEnabled", it).apply()
            },
            onNotifTrackingChange = {
                notifTracking = it
                sharedPrefs.edit().putBoolean("isNotificationTrackingEnabled", it).apply()
            },
            onNavigate = onNavigate
        )
        Spacer(modifier = Modifier.height(16.dp))
        DeveloperOptionsSection(onNavigate = onNavigate)
        Spacer(modifier = Modifier.height(16.dp))
        AboutSection()
    }
}

@Composable
fun SettingsCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) {
        Text(
            text = title,
            color = purpleColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            content = content
        )
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isLast: Boolean = false,
    iconTint: Color = purpleColor,
    iconBgColor: Color = lightPurpleColor,
    onClick: (() -> Unit)? = null,
    rightContent: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Go",
            tint = textColor,
            modifier = Modifier.size(16.dp)
        )
    }
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconBgColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = textColor
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = subtitleColor
            )
        }
        
        rightContent()
    }
    
    if (!isLast) {
        HorizontalDivider(
            color = borderColor,
            thickness = 1.dp,
            modifier = Modifier.padding(start = 60.dp)
        )
    }
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isLast: Boolean = false
) {
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        isLast = isLast,
        rightContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
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
    )
}

@Composable
fun AccountsSection() {
    SettingsCard {
        // Profile Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
        
        HorizontalDivider(color = borderColor, thickness = 1.dp)
        
        SettingsRow(
            icon = Icons.Filled.Lock,
            title = "Account & Privacy",
            subtitle = "Manage your account, logout & more",
            isLast = true,
            onClick = {}
        )
    }
}

@Composable
fun TrackerStatusSection(
    autoTracking: Boolean,
    onAutoTrackingChange: (Boolean) -> Unit
) {
    SettingsCard(title = "Tracker Status") {
        SettingsSwitchRow(
            icon = Icons.Filled.CheckCircle,
            title = "Auto Tracking",
            subtitle = "Actively listening to notifications & SMS",
            checked = autoTracking,
            onCheckedChange = onAutoTrackingChange,
            isLast = false
        )
        
        SettingsRow(
            icon = Icons.AutoMirrored.Filled.List,
            title = "Tracking Stats",
            subtitle = "Today: 8 transactions • This month: 126",
            isLast = true,
            onClick = {}
        )
    }
}

@Composable
fun DataStorageSection(onNavigate: (String) -> Unit = {}) {
    SettingsCard(title = "Data & Storage") {
        SettingsRow(
            icon = Icons.Filled.Info,
            title = "Database",
            subtitle = "Local • SQLite",
            isLast = false,
            onClick = { onNavigate("Database") },
            rightContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("2.48 MB", fontSize = 11.sp, color = subtitleColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Go", tint = textColor, modifier = Modifier.size(16.dp))
                }
            }
        )
        SettingsRow(
            icon = Icons.Filled.DateRange,
            title = "Import Previous Transactions",
            subtitle = "Import from existing SMS messages",
            isLast = false,
            onClick = { onNavigate("SmsImport") }
        )
        SettingsRow(
            icon = Icons.Filled.Share,
            title = "Backup & Restore",
            subtitle = "Export or import your data",
            isLast = false,
            onClick = { onNavigate("BackupRestore") }
        )
        SettingsRow(
            icon = Icons.Filled.Delete,
            title = "Clear All Data",
            subtitle = "Delete all transactions permanently",
            isLast = true,
            onClick = { onNavigate("ClearAllData") }
        )
    }
}

@Composable
fun TrackingSourcesSection(
    smsTracking: Boolean,
    notifTracking: Boolean,
    onSmsTrackingChange: (Boolean) -> Unit,
    onNotifTrackingChange: (Boolean) -> Unit,
    onNavigate: (String) -> Unit
) {
    SettingsCard(title = "Tracking Sources") {
        SettingsSwitchRow(
            icon = Icons.Filled.Email,
            title = "SMS Tracking",
            subtitle = "Read and parse SMS messages",
            checked = smsTracking,
            onCheckedChange = onSmsTrackingChange,
            isLast = false
        )
        SettingsSwitchRow(
            icon = Icons.Filled.Notifications,
            title = "Notification Tracking",
            subtitle = "Listen to UPI & banking app notifications",
            checked = notifTracking,
            onCheckedChange = onNotifTrackingChange,
            isLast = false
        )
        SettingsRow(
            icon = Icons.AutoMirrored.Filled.List,
            title = "Supported Apps",
            subtitle = "Manage apps to include or exclude",
            isLast = false,
            onClick = { onNavigate("SupportedApps") }
        )
        SettingsRow(
            icon = Icons.Filled.Warning, // Filter or Warning icon
            title = "Ignore Rules",
            subtitle = "Keywords, senders or patterns to ignore",
            isLast = true,
            onClick = { onNavigate("IgnoreRules") }
        )
    }
}

@Composable
fun DeveloperOptionsSection(onNavigate: (String) -> Unit) {
    SettingsCard(title = "Developer Settings") {
        SettingsRow(
            icon = Icons.Filled.Code,
            title = "Developer options",
            subtitle = "Advanced tools for debugging and customization",
            isLast = true,
            onClick = { onNavigate("DeveloperOptions") }
        )
    }
}

@Composable
fun AboutSection() {
    SettingsCard(title = "About") {
        SettingsRow(
            icon = Icons.Filled.Info,
            title = "About ArcTracker",
            subtitle = "Version 1.0.0 • Offline • 100% Private",
            isLast = false,
            onClick = {}
        )
        SettingsRow(
            icon = Icons.Filled.Favorite,
            title = "Rate Us",
            subtitle = "If you love the app, please rate it",
            isLast = true,
            onClick = {}
        )
    }
}
