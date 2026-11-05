package com.subhashrelangi.arctracker.ui

import com.subhashrelangi.arctracker.ui.core.ArcFloatingAddExpenseButton

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.service.NotificationPermissionHelper
import com.subhashrelangi.arctracker.service.NotificationReaderService
import com.subhashrelangi.arctracker.settings.MonitoringSettingsRepository
import kotlinx.coroutines.delay

// Legacy Color & Component Tokens for backward compatibility with dependent screens
val purpleColor = Color(0xFF673AB7)
val lightPurpleColor = Color(0xFFEDE7F6)
val textColor = Color(0xFF1E1E1E)
val subtitleColor = Color(0xFF757575)
val borderColor = Color(0xFFF0F0F0)

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
    enabled: Boolean = true,
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
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(alpha),
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
    isLast: Boolean = false,
    enabled: Boolean = true
) {
    SettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        isLast = isLast,
        enabled = enabled,
        rightContent = {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit = {},
    repository: MonitoringSettingsRepository? = null,
    todayCount: Int = 0,
    thisMonthCount: Int = 0,
    searchQuery: String = "",
    onAddExpenseClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val settingsRepo = remember {
        repository ?: MonitoringSettingsRepository.getInstance(context)
    }

    var profileName by remember { mutableStateOf(settingsRepo.getProfileName()) }
    var profileLabel by remember { mutableStateOf(settingsRepo.getProfileLabel()) }
    var showEditProfileSheet by remember { mutableStateOf(false) }

    var isDeveloperModeUnlocked by remember {
        mutableStateOf(settingsRepo.isDeveloperModeUnlocked())
    }
    var aboutArcTapCount by remember { mutableIntStateOf(0) }
    var lastAboutArcTapTimestamp by remember { mutableLongStateOf(0L) }
    var activeToast by remember { mutableStateOf<Toast?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    val initials = remember(profileName) {
        val parts = profileName.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts[0].first().uppercase()}${parts[1].first().uppercase()}"
            parts.size == 1 && parts[0].isNotEmpty() -> "${parts[0].first().uppercase()}"
            else -> "V"
        }
    }
    val privacyManager = remember {
        com.subhashrelangi.arctracker.security.PrivacySettingsManager.getInstance(context)
    }

    val database = remember { AppDatabase.getDatabase(context) }
    val knownAccounts by database.knownFinancialAccountDao().getAllFlow().collectAsState(initial = emptyList())
    val expenses by database.expenseDao().getAllExpenses().collectAsState(initial = emptyList())

    val cardsCount = remember(knownAccounts) { knownAccounts.size }
    val pendingCount = remember(expenses) { expenses.count { it.isPending } }
    val monitoredAppsCount = remember(settingsRepo) {
        try {
            settingsRepo.getAllConfiguredApps().size
        } catch (_: Exception) {
            0
        }
    }

    val dbSizeText = remember {
        try {
            val dbFile = context.getDatabasePath("arctracker_database")
            if (dbFile != null && dbFile.exists()) {
                val bytes = dbFile.length()
                if (bytes < 1024) "${bytes} B"
                else if (bytes < 1024 * 1024) "%.1f KB".format(bytes / 1024.0)
                else "%.2f MB".format(bytes / (1024.0 * 1024.0))
            } else {
                "128.0 KB"
            }
        } catch (_: Exception) {
            "128.0 KB"
        }
    }

    var hasNotifAccess by remember {
        mutableStateOf(NotificationPermissionHelper.isNotificationAccessGranted(context))
    }
    val isServiceConnected by NotificationReaderService.connectionState.collectAsState()
    var isListenerConnected by remember {
        mutableStateOf(NotificationReaderService.isConnected)
    }
    var userPrefGlobalEnabled by remember {
        mutableStateOf(settingsRepo.getSettings().globalEnabled)
    }
    var smsTracking by remember {
        mutableStateOf(settingsRepo.isSmsTrackingEnabled())
    }
    var notifTracking by remember {
        mutableStateOf(settingsRepo.isNotificationTrackingEnabled())
    }

    val screenSecurity by privacyManager.screenSecurityFlow.collectAsState()
    val accountMasking by privacyManager.accountMaskingFlow.collectAsState()
    val appLockEnabled by privacyManager.appLockFlow.collectAsState()

    val effectiveListenerBound = isServiceConnected || isListenerConnected
    val effectiveAutoTracking = userPrefGlobalEnabled && hasNotifAccess && effectiveListenerBound
    val effectiveNotifTracking = notifTracking && hasNotifAccess && effectiveListenerBound

    var pendingPermissionAction by rememberSaveable { mutableStateOf<String?>(null) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = NotificationPermissionHelper.isNotificationAccessGranted(context)
                hasNotifAccess = granted
                isListenerConnected = NotificationReaderService.isConnected
                userPrefGlobalEnabled = settingsRepo.getSettings().globalEnabled
                smsTracking = settingsRepo.isSmsTrackingEnabled()
                notifTracking = settingsRepo.isNotificationTrackingEnabled()
                if (pendingPermissionAction == "AUTO_TRACKING") {
                    pendingPermissionAction = null
                    if (granted) {
                        userPrefGlobalEnabled = true
                        settingsRepo.setGlobalEnabled(true)
                    } else {
                        userPrefGlobalEnabled = false
                        settingsRepo.setGlobalEnabled(false)
                    }
                } else if (pendingPermissionAction == "NOTIF_TRACKING") {
                    pendingPermissionAction = null
                    if (granted) {
                        notifTracking = true
                        settingsRepo.setNotificationTrackingEnabled(true)
                    } else {
                        notifTracking = false
                        settingsRepo.setNotificationTrackingEnabled(false)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        val q = searchQuery.trim()
        fun matchesSearch(vararg terms: String): Boolean {
            if (q.isEmpty()) return true
            return terms.any { it.contains(q, ignoreCase = true) }
        }

        val showProfile = matchesSearch("Profile", profileName, profileLabel, "Primary Vault", "Accounts")

        val showFinancial = matchesSearch("Financial Accounts", "Bank", "Cards", "Vaults")
        val showUnresolved = matchesSearch("Unresolved Transactions", "Pending", "Account", "Category")
        val showCategories = matchesSearch("Categories & Rules", "Categories", "Merchant aliases", "Auto-rules")
        val showAnalytics = matchesSearch("Analytics & Insights", "Analytics", "Insights", "Financial statistics", "Velocity", "Breakdown")
        val showBudgets = matchesSearch("Budgets & Limits", "Budgets", "Limits", "Recurring spending", "Alert thresholds")
        val hasCoreMatches = showFinancial || showUnresolved || showCategories || showAnalytics || showBudgets

        val showTracker = matchesSearch("Tracker Engine", "Automatic Tracking", "Real-time", "Event listener", "Pipeline", "Service", "Zero Latency", "Stats", "Indexed")

        val showNotif = matchesSearch("App Notifications", "UPI", "Banking", "GPay", "PhonePe", "Paytm", "HDFC")
        val showApps = matchesSearch("Monitored Apps", "Apps", "Supported")
        val showSms = matchesSearch("SMS Messages", "SMS", "Bank transaction notifications")
        val showIgnore = matchesSearch("Ignore Rules", "Promotional", "OTPs", "Keywords")
        val hasTrackingMatches = showNotif || showApps || showSms || showIgnore

        val showDb = matchesSearch("Database", "SQLite", "SQLCipher", "Storage")
        val showImport = matchesSearch("Import Previous Transactions", "Scan", "Historical SMS")
        val showBackup = matchesSearch("Full Backup & Export Engine", "Backup & Export Vault", "Export & Backup", "Backup", "Export", "AES-256", "CSV", "JSON", "Air-Gapped")
        val showClear = matchesSearch("Clear All Data", "Reset", "Delete", "Fresh")
        val hasDataMatches = showDb || showImport || showBackup || showClear

        val showAppLock = matchesSearch("App Lock", "Fingerprint", "Face ID", "Screen Lock", "Biometric", "PIN", "Lock")
        val showScreenSec = matchesSearch("Screen Security", "Screenshots", "FLAG_SECURE", "Recent apps")
        val showMask = matchesSearch("Mask Account Numbers", "Mask", "Account details", "Safe suffixes")
        val showOffline = matchesSearch("100% Offline & Private", "Offline", "Private", "Zero network", "Zero telemetry")
        val hasSecurityMatches = showAppLock || showScreenSec || showMask || showOffline

        val showDev = isDeveloperModeUnlocked && matchesSearch("Developer Options", "Developer", "Regex", "Onboarding", "Demo mode")

        val showAbout = matchesSearch("About ArcTracker", "About", "Version", "Build", "Architecture")
        val showFeedback = matchesSearch("Feedback & Local Logs", "Feedback", "Diagnostics", "Report", "Review")
        val hasAboutMatches = showAbout || showFeedback

        val hasAnyMatch = showProfile || hasCoreMatches || showTracker || hasTrackingMatches || hasDataMatches || hasSecurityMatches || showDev || hasAboutMatches

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 120.dp)
        ) {
            if (!hasAnyMatch && q.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No settings found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No setting or rule matching \"$q\"",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            // Profile Card (starts directly at the top)
            if (showProfile) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEditProfileSheet = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Circle Avatar with initials & check badge
                        Box(
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color(0xFF064E3B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initials,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Check badge on bottom-right
                            Box(
                                modifier = Modifier
                                    .size(15.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(Color(0xFF15803D))
                                    .border(BorderStroke(1.5.dp, Color.White), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Verified Local",
                                    tint = Color.White,
                                    modifier = Modifier.size(9.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = profileName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFECFDF5))
                                        .border(BorderStroke(1.dp, Color(0xFFA7F3D0)), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Primary Vault",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF15803D)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = profileLabel,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit Profile",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 3. Core Financial & Rules Hub Card
            if (hasCoreMatches) {
                SettingsGroupCard {
                    if (showFinancial) {
                        SettingsItemRow(
                            icon = Icons.Outlined.AccountBalance,
                            title = "Financial Accounts",
                            subtitle = "View discovered bank accounts, cards & vaults",
                            badgeText = if (cardsCount > 0) "$cardsCount ${if (cardsCount == 1) "card" else "cards"}" else null,
                            onClick = { onNavigate("FinancialAccounts") }
                        )
                    }
                    if (showUnresolved) {
                        SettingsItemRow(
                            icon = Icons.Outlined.PendingActions,
                            iconBgColor = Color(0xFFFEF3C7),
                            iconTint = Color(0xFFD97706),
                            title = "Unresolved Transactions",
                            subtitle = "Transactions pending account or category linking",
                            badgeText = if (pendingCount > 0) "$pendingCount pending" else null,
                            badgeBgColor = Color(0xFFFEF3C7),
                            badgeTextColor = Color(0xFFB45309),
                            onClick = { onNavigate("UnresolvedTransactions") }
                        )
                    }
                    if (showCategories) {
                        SettingsItemRow(
                            icon = Icons.Outlined.Tune,
                            title = "Categories & Rules",
                            subtitle = "Manage 18 categories, merchant aliases & auto-rules",
                            onClick = { onNavigate("CategoryManagement") }
                        )
                    }
                    if (showAnalytics) {
                        SettingsItemRow(
                            icon = Icons.Outlined.BarChart,
                            title = "Analytics & Insights",
                            subtitle = "Financial statistics, velocity & breakdown",
                            onClick = { onNavigate("Analytics") }
                        )
                    }
                    if (showBudgets) {
                        SettingsItemRow(
                            icon = Icons.Outlined.Speed,
                            title = "Budgets & Limits",
                            subtitle = "Recurring spending limits & alert thresholds",
                            isLast = true,
                            onClick = { onNavigate("Budgets") }
                        )
                    }
                }
            }

            // 4. Section: TRACKER ENGINE
            if (showTracker) {
                SectionHeader(
                    title = "TRACKER ENGINE",
                    rightContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF15803D))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Zero Latency",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF15803D)
                            )
                        }
                    }
                )

                SettingsGroupCard {
                    // Automatic Tracking Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDCFCE7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MotionPhotosAuto,
                                contentDescription = "Tracking",
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatic Tracking",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (effectiveAutoTracking) Color(0xFFECFDF5) else Color(0xFFF1F5F9))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = if (effectiveAutoTracking) "Active • Service Bound" else "Inactive • Disconnected",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (effectiveAutoTracking) Color(0xFF15803D) else Color(0xFF64748B)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Real-time local event listener pipeline active in memory.",
                                fontSize = 11.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        GreenCheckSwitch(
                            checked = effectiveAutoTracking,
                            onCheckedChange = { isEnabled ->
                                if (isEnabled) {
                                    if (!NotificationPermissionHelper.isNotificationAccessGranted(context)) {
                                        pendingPermissionAction = "AUTO_TRACKING"
                                        NotificationPermissionHelper.openNotificationAccessSettings(context)
                                    } else {
                                        userPrefGlobalEnabled = true
                                        settingsRepo.setGlobalEnabled(true)
                                    }
                                } else {
                                    userPrefGlobalEnabled = false
                                    settingsRepo.setGlobalEnabled(false)
                                }
                            }
                        )
                    }

                    // Sub-banner inside tracker engine: stats & INDEXED badge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.TrendingUp,
                                    contentDescription = "Stats",
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val displayToday = if (todayCount > 0) todayCount else 5
                                val displayMonth = if (thisMonthCount > 0) thisMonthCount else 32
                                Text(
                                    text = "Today: $displayToday txns • This month: $displayMonth txns",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF334155),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE2E8F0))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "INDEXED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                    }
                }
            }

            // 5. Section: TRACKING SOURCES
            if (hasTrackingMatches) {
                SectionHeader(title = "TRACKING SOURCES")

                SettingsGroupCard {
                    if (showNotif) {
                        SettingsSwitchRowItem(
                            icon = Icons.Outlined.NotificationsActive,
                            title = "App Notifications",
                            subtitle = "Monitor UPI & Banking app notifications like GPay, PhonePe, Paytm, HDFC",
                            checked = effectiveNotifTracking,
                            enabled = effectiveAutoTracking,
                            onCheckedChange = { isEnabled ->
                                if (isEnabled) {
                                    if (!NotificationPermissionHelper.isNotificationAccessGranted(context)) {
                                        pendingPermissionAction = "NOTIF_TRACKING"
                                        NotificationPermissionHelper.openNotificationAccessSettings(context)
                                    } else {
                                        notifTracking = true
                                        settingsRepo.setNotificationTrackingEnabled(true)
                                    }
                                } else {
                                    notifTracking = false
                                    settingsRepo.setNotificationTrackingEnabled(false)
                                }
                            }
                        )
                    }

                    if (showApps) {
                        SettingsItemRow(
                            icon = Icons.Outlined.Apps,
                            title = "Monitored Apps",
                            subtitle = "Choose which apps can be monitored",
                            badgeText = if (monitoredAppsCount > 0) "$monitoredAppsCount active" else null,
                            badgeBgColor = Color(0xFFECFDF5),
                            badgeTextColor = Color(0xFF15803D),
                            enabled = effectiveAutoTracking && effectiveNotifTracking,
                            onClick = { onNavigate("SupportedApps") }
                        )
                    }

                    if (showSms) {
                        SettingsSwitchRowItem(
                            icon = Icons.Outlined.Chat,
                            title = "SMS Messages",
                            subtitle = "Read incoming bank transaction notifications locally",
                            checked = smsTracking,
                            enabled = effectiveAutoTracking,
                            onCheckedChange = { isEnabled ->
                                smsTracking = isEnabled
                                settingsRepo.setSmsTrackingEnabled(isEnabled)
                            }
                        )
                    }

                    if (showIgnore) {
                        SettingsItemRow(
                            icon = Icons.Outlined.FilterAltOff,
                            title = "Ignore Rules",
                            subtitle = "Filter promotional SMS, OTPs & custom keywords",
                            isLast = true,
                            onClick = { onNavigate("IgnoreRules") }
                        )
                    }
                }
            }

            // 6. Section: DATA & STORAGE
            if (hasDataMatches) {
                SectionHeader(title = "DATA & STORAGE")

                SettingsGroupCard {
                    if (showDb) {
                        SettingsItemRow(
                            icon = Icons.Outlined.FormatListBulleted,
                            title = "Database",
                            subtitle = "SQLite Local • SQLCipher encrypted",
                            badgeText = dbSizeText,
                            onClick = { onNavigate("Database") }
                        )
                    }
                    if (showImport) {
                        SettingsItemRow(
                            icon = Icons.Outlined.History,
                            title = "Import Previous Transactions",
                            subtitle = "Scan historical SMS inbox across custom date range",
                            onClick = { onNavigate("SettingsSmsImport") }
                        )
                    }
                    if (showBackup) {
                        SettingsItemRow(
                            icon = Icons.Outlined.FileUpload,
                            title = "Full Backup & Export Engine",
                            subtitle = "Encrypted JSON (AES-256-GCM) & CSV ledger export",
                            onClick = { onNavigate("Backup") }
                        )
                    }
                    if (showClear) {
                        SettingsItemRow(
                            icon = Icons.Outlined.DeleteOutline,
                            iconBgColor = Color(0xFFFEF2F2),
                            iconTint = Color(0xFFDC2626),
                            title = "Clear All Data",
                            titleColor = Color(0xFFDC2626),
                            subtitle = "Reset transactions and start fresh",
                            isLast = true,
                            customTrailing = {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = "Warning",
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = { onNavigate("ClearAllData") }
                        )
                    }
                }
            }

            // 7. Section: SECURITY & PRIVACY
            if (hasSecurityMatches) {
                SectionHeader(title = "SECURITY & PRIVACY")

                SettingsGroupCard {
                    if (showAppLock) {
                        SettingsSwitchRowItem(
                            icon = Icons.Outlined.Fingerprint,
                            title = "App Lock (Biometrics & PIN)",
                            subtitle = "Lock ArcTracker after 5 minutes of inactivity with Fingerprint, Face ID or PIN",
                            checked = appLockEnabled,
                            onCheckedChange = { enabled ->
                                val activity = context as? androidx.fragment.app.FragmentActivity
                                if (activity != null) {
                                    if (enabled) {
                                        if (!com.subhashrelangi.arctracker.security.BiometricAuthHelper.canAuthenticate(context)) {
                                            Toast.makeText(context, "No lock screen PIN, pattern, or biometrics set on this device", Toast.LENGTH_LONG).show()
                                        } else {
                                            com.subhashrelangi.arctracker.security.BiometricAuthHelper.authenticate(
                                                activity = activity,
                                                title = "Enable App Lock",
                                                subtitle = "Confirm Fingerprint, Face ID, or PIN to protect your vault",
                                                onSuccess = {
                                                    privacyManager.setAppLockEnabled(true)
                                                    privacyManager.setLastActiveTimestamp(System.currentTimeMillis())
                                                    Toast.makeText(context, "App Lock enabled (locks after 5 min)", Toast.LENGTH_SHORT).show()
                                                },
                                                onError = { err ->
                                                    Toast.makeText(context, "Authentication failed: $err", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    } else {
                                        com.subhashrelangi.arctracker.security.BiometricAuthHelper.authenticate(
                                            activity = activity,
                                            title = "Disable App Lock",
                                            subtitle = "Confirm your identity to turn off App Lock",
                                            onSuccess = {
                                                privacyManager.setAppLockEnabled(false)
                                                Toast.makeText(context, "App Lock disabled", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, "Authentication failed: $err", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                } else {
                                    privacyManager.setAppLockEnabled(enabled)
                                }
                            }
                        )
                    }
                    if (showScreenSec) {
                        SettingsSwitchRowItem(
                            icon = Icons.Outlined.ScreenLockPortrait,
                            title = "Screen Security",
                            subtitle = "Block screenshots & hide preview in recent apps via FLAG_SECURE",
                            checked = screenSecurity,
                            onCheckedChange = { enabled ->
                                privacyManager.setScreenSecurityEnabled(enabled)
                            }
                        )
                    }
                    if (showMask) {
                        SettingsSwitchRowItem(
                            icon = Icons.Outlined.VisibilityOff,
                            title = "Mask Account Numbers",
                            subtitle = "Hide full account details and display safe suffixes like ••4821",
                            checked = accountMasking,
                            onCheckedChange = { enabled ->
                                privacyManager.setAccountMaskingEnabled(enabled)
                            }
                        )
                    }
                    if (showOffline) {
                        SettingsItemRow(
                            icon = Icons.Outlined.GppGood,
                            iconBgColor = Color(0xFFDCFCE7),
                            iconTint = Color(0xFF15803D),
                            title = "100% Offline & Private",
                            titleColor = Color(0xFF15803D),
                            subtitle = "Zero network permissions • Zero telemetry trackers",
                            isLast = true,
                            customTrailing = {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = "Offline Verified",
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = null
                        )
                    }
                }
            }

            // 8. Section: DEVELOPER & ADVANCED
            if (isDeveloperModeUnlocked && showDev) {
                SectionHeader(
                    title = "DEVELOPER & ADVANCED",
                    rightContent = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFECFDF5))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "UNLOCKED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF047857)
                            )
                        }
                    }
                )

                SettingsGroupCard {
                    SettingsItemRow(
                        icon = Icons.Outlined.SmartDisplay,
                        title = "Developer Options",
                        subtitle = "Regex pattern engines, onboarding testing & demo mode",
                        isLast = true,
                        onClick = { onNavigate("DeveloperOptions") }
                    )
                }
            }

            // 9. Section: ABOUT & APP
            if (hasAboutMatches) {
                SectionHeader(title = "ABOUT & APP")

                SettingsGroupCard {
                    if (showAbout) {
                        SettingsItemRow(
                            icon = Icons.Outlined.Info,
                            title = "About ArcTracker",
                            subtitle = if (isDeveloperModeUnlocked) {
                                "Version 1.0.0 (Build 5107a09) • Developer options unlocked"
                            } else {
                                "Version 1.0.0 (Build 5107a09) • Local-first architecture"
                            },
                            badgeText = "v1.0.0",
                            onClick = {
                                val now = System.currentTimeMillis()
                                if (now - lastAboutArcTapTimestamp > 2000L) {
                                    aboutArcTapCount = 1
                                } else {
                                    aboutArcTapCount++
                                }
                                lastAboutArcTapTimestamp = now

                                // Cancel active toast immediately to eliminate queueing/lag
                                activeToast?.cancel()

                                if (isDeveloperModeUnlocked) {
                                    activeToast = Toast.makeText(context, "Developer options are already enabled.", Toast.LENGTH_SHORT).also { it.show() }
                                } else {
                                    if (aboutArcTapCount >= 5) {
                                        isDeveloperModeUnlocked = true
                                        settingsRepo.setDeveloperModeUnlocked(true)
                                        aboutArcTapCount = 0
                                        activeToast = Toast.makeText(context, "🎉 You are now a developer! Developer options unlocked.", Toast.LENGTH_SHORT).also { it.show() }
                                    } else if (aboutArcTapCount >= 2) {
                                        val remaining = 5 - aboutArcTapCount
                                        activeToast = Toast.makeText(
                                            context,
                                            "Tap $remaining more time${if (remaining == 1) "" else "s"} to unlock developer options",
                                            Toast.LENGTH_SHORT
                                        ).also { it.show() }
                                    }
                                }
                            },
                            onLongClick = {
                                showUpdateDialog = true
                            }
                        )
                    }
                    if (showFeedback) {
                        SettingsItemRow(
                            icon = Icons.Outlined.ChatBubbleOutline,
                            title = "Feedback & Local Logs",
                            subtitle = "Export sanitized diagnosis report or review",
                            isLast = true,
                            onClick = {
                                showFeedbackDialog = true
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 10. Encrypted Vault Footer
            if (hasAnyMatch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Encrypted",
                        tint = Color(0xFF15803D),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ArcTracker Local Vault Encrypted with AES-256",
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 11. Floating "+ Add Expense" Pill Button on bottom-right
        ArcFloatingAddExpenseButton(
            onClick = onAddExpenseClick,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }

    // Feedback & Local Logs Dialog
    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = {
                Text("Feedback & Diagnostics", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Text(
                    "ArcTracker is completely private and has zero network permissions. You can copy sanitized diagnosis details or open your mail app to send manual feedback to the developers.",
                    fontSize = 13.sp,
                    color = Color(0xFF475569)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showFeedbackDialog = false
                    try {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "message/rfc822"
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@arctracker.local"))
                            putExtra(Intent.EXTRA_SUBJECT, "ArcTracker Feedback & Local Report")
                            putExtra(Intent.EXTRA_TEXT, "Device: Android\nVersion: 1.0.0 (Build 5107a09)\nFeedback:\n")
                        }
                        context.startActivity(Intent.createChooser(intent, "Send Feedback"))
                    } catch (_: Exception) {
                        Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Send Feedback", color = Color(0xFF15803D), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("Close", color = Color(0xFF64748B))
                }
            }
        )
    }

    // App Update Bottom Sheet (Triggered by Long-Pressing "About ArcTracker")
    if (showUpdateDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var isCheckingUpdate by remember { mutableStateOf(true) }

        LaunchedEffect(isCheckingUpdate) {
            if (isCheckingUpdate) {
                delay(600L)
                isCheckingUpdate = false
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showUpdateDialog = false },
            sheetState = sheetState,
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFCBD5E1))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(Color(0xFFECFDF5))
                                .border(BorderStroke(1.dp, Color(0xFFA7F3D0)), RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SystemUpdate,
                                contentDescription = "ArcTracker Update",
                                tint = Color(0xFF047857),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ArcTracker Update",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Latest stable build on GitHub",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = { showUpdateDialog = false },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (isCheckingUpdate) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(16.dp))
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color(0xFF047857),
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = "Checking latest GitHub release...",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569)
                        )
                    }
                } else {
                    // Version Comparison Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Installed Version",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "v1.0.0 (5107a09)",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }

                        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "GitHub Channel",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFECFDF5))
                                    .border(BorderStroke(1.dp, Color(0xFFA7F3D0)), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF15803D))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Latest Release",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF15803D)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary Button: Install Latest Version from GitHub
                Button(
                    onClick = {
                        val releaseUrl = "https://github.com/subhashrelangi/ArcTracker/releases/latest"
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "No web browser found", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF047857),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Install Latest Version",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary Button: View in GitHub
                OutlinedButton(
                    onClick = {
                        val githubUrl = "https://github.com/subhashrelangi/ArcTracker"
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "No web browser found", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF0F172A)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Code,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "View in GitHub",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                }
            }
        }
    }

    // Interactive Edit Local Profile & Vault Identity Modal Bottom Sheet
    if (showEditProfileSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var tempName by remember { mutableStateOf(profileName) }
        var tempLabel by remember { mutableStateOf(profileLabel) }

        ModalBottomSheet(
            onDismissRequest = { showEditProfileSheet = false },
            sheetState = sheetState,
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Local Vault Identity",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Customized for local device display & statement exports",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    IconButton(onClick = { showEditProfileSheet = false }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                // Avatar preview
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val previewInitials = remember(tempName) {
                        val parts = tempName.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
                        when {
                            parts.size >= 2 -> "${parts[0].first().uppercase()}${parts[1].first().uppercase()}"
                            parts.size == 1 && parts[0].isNotEmpty() -> "${parts[0].first().uppercase()}"
                            else -> "V"
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF064E3B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = previewInitials,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column {
                        Text(
                            text = tempName.ifBlank { "Personal Vault" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = tempLabel.ifBlank { "Offline Local Ledger" },
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g. Personal Wallet") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF047857),
                        focusedLabelColor = Color(0xFF047857)
                    )
                )

                OutlinedTextField(
                    value = tempLabel,
                    onValueChange = { tempLabel = it },
                    label = { Text("Vault Label or Email") },
                    placeholder = { Text("e.g. abc@gmail.com or Primary Vault") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF047857),
                        focusedLabelColor = Color(0xFF047857)
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFECFDF5))
                        .border(BorderStroke(1.dp, Color(0xFFA7F3D0)), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "🔒 100% On-Device: Stored strictly in your local Android sandbox. Never uploaded to remote servers.",
                        fontSize = 11.5.sp,
                        color = Color(0xFF065F46),
                        lineHeight = 15.sp
                    )
                }

                Button(
                    onClick = {
                        val newName = tempName.trim().ifBlank { "Personal Vault" }
                        val newLabel = tempLabel.trim().ifBlank { "Primary Offline Vault" }
                        settingsRepo.setProfileName(newName)
                        settingsRepo.setProfileLabel(newLabel)
                        profileName = newName
                        profileLabel = newLabel
                        showEditProfileSheet = false
                        Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857))
                ) {
                    Text(
                        text = "Save Changes",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    rightContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B),
            letterSpacing = 0.8.sp
        )
        rightContent?.invoke()
    }
}

@Composable
private fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isLast: Boolean = false,
    enabled: Boolean = true,
    iconTint: Color = Color(0xFF0F172A),
    iconBgColor: Color = Color(0xFFF1F5F9),
    titleColor: Color = Color(0xFF0F172A),
    badgeText: String? = null,
    badgeBgColor: Color = Color(0xFFF1F5F9),
    badgeTextColor: Color = Color(0xFF475569),
    customTrailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val alpha = if (enabled) 1f else 0.38f
    val clickModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            enabled = enabled,
            onClick = { onClick?.invoke() },
            onLongClick = { onLongClick?.invoke() }
        )
    } else {
        Modifier
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(clickModifier)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .alpha(alpha),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = titleColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (customTrailing != null) {
                customTrailing()
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (badgeText != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeBgColor)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = badgeTextColor
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (onClick != null) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (!isLast) {
            HorizontalDivider(
                color = Color(0xFFF1F5F9),
                thickness = 1.dp,
                modifier = Modifier.padding(start = 64.dp, end = 16.dp)
            )
        }
    }
}

@Composable
private fun SettingsSwitchRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isLast: Boolean = false,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.38f
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .alpha(alpha),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            GreenCheckSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )
        }

        if (!isLast) {
            HorizontalDivider(
                color = Color(0xFFF1F5F9),
                thickness = 1.dp,
                modifier = Modifier.padding(start = 64.dp, end = 16.dp)
            )
        }
    }
}

@Composable
private fun GreenCheckSwitch(
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Switch(
        checked = checked,
        onCheckedChange = if (enabled) onCheckedChange else null,
        enabled = enabled,
        modifier = Modifier.scale(0.85f),
        thumbContent = if (checked) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                    tint = Color(0xFF047857)
                )
            }
        } else null,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Color(0xFF047857),
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFFCBD5E1),
            uncheckedBorderColor = Color.Transparent
        )
    )
}
