package com.subhashrelangi.arctracker.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import com.subhashrelangi.arctracker.security.SecureLogger
import com.subhashrelangi.arctracker.service.NotificationReaderService
import com.subhashrelangi.arctracker.settings.*
import com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MockApp(
    val packageName: String,
    val name: String,
    val category: String,
    val subtitle: String,
    val icon: ImageVector
)

/**
 * Resolves an icon for the category header with safe fallback.
 */
fun getCategoryIcon(category: AppCategory?): ImageVector {
    return when (category) {
        AppCategory.BANKING -> Icons.Default.AccountBalance
        AppCategory.SMS_MESSENGER -> Icons.Default.Sms
        AppCategory.UPI_PAYMENT -> Icons.Default.Payment
        null -> Icons.Default.Payment
    }
}

/**
 * Resolves a vector icon fallback based on package name heuristics.
 */
fun getAppVectorFallback(packageName: String, category: AppCategory): ImageVector {
    val lower = packageName.lowercase()
    return when {
        lower.contains("message") || lower.contains("sms") -> Icons.Filled.ChatBubble
        lower.contains("hdfc") || lower.contains("sbi") || lower.contains("bank") || lower.contains("kotak") || lower.contains("axis") -> Icons.Filled.AccountBalance
        lower.contains("icici") || lower.contains("imobile") -> Icons.Filled.Domain
        lower.contains("cred") -> Icons.Filled.CreditCard
        lower.contains("paytm") -> Icons.Filled.AccountBalanceWallet
        lower.contains("phonepe") -> Icons.Filled.AccountBalanceWallet
        lower.contains("gpay") || lower.contains("paisa") -> Icons.Filled.Payment
        category == AppCategory.BANKING -> Icons.Filled.AccountBalance
        category == AppCategory.SMS_MESSENGER -> Icons.Filled.ChatBubble
        else -> Icons.Filled.Payment
    }
}

/**
 * Heuristically detects app category for automatic scanning and classification.
 */
fun detectAppCategory(packageName: String, displayName: String): AppCategory? {
    val text = (packageName + " " + displayName).lowercase()
    return when {
        // SMS & Messaging Apps
        text.contains("sms") || text.contains("messaging") || text.contains("message") ||
            text.contains("mms") || text.contains("truecaller") -> AppCategory.SMS_MESSENGER

        // Banking Apps
        text.contains("bank") || text.contains("sbi") || text.contains("hdfc") ||
            text.contains("icici") || text.contains("axis") || text.contains("kotak") ||
            text.contains("pnb") || text.contains("bob") || text.contains("canara") ||
            text.contains("union") || text.contains("idfc") || text.contains("indusind") ||
            text.contains("federal") || text.contains("rbl") || text.contains("yes") ||
            text.contains("aubank") || text.contains("baroda") || text.contains("yono") ||
            text.contains("snapwork") || text.contains("imobile") || text.contains("lotusintouch") ||
            text.contains("dbs") || text.contains("hsbc") || text.contains("citi") ||
            text.contains("centralbank") || text.contains("mahabank") || text.contains("iob") ||
            text.contains("uco") || text.contains("indianbank") || text.contains("karnatakabank") ||
            text.contains("southindianbank") || text.contains("equitas") || text.contains("ujjivan") ||
            text.contains("bandhan") || text.contains("sc.breeze") -> AppCategory.BANKING

        // UPI, Payment & Fintech Apps
        text.contains("upi") || text.contains("pay") || text.contains("paisa") ||
            text.contains("wallet") || text.contains("cred") || text.contains("bhim") ||
            text.contains("mobikwik") || text.contains("bharatpe") || text.contains("navi") ||
            text.contains("slice") || text.contains("onecard") || text.contains("jupiter") ||
            text.contains("fampay") || text.contains("freecharge") || text.contains("groww") ||
            text.contains("zerodha") || text.contains("finance") || text.contains("lending") ||
            text.contains("money") || text.contains("angelone") || text.contains("upstox") ||
            text.contains("kuvera") || text.contains("etmoney") || text.contains("coin") ||
            text.contains("indmoney") || text.contains("smallcase") || text.contains("bajaj") ||
            text.contains("muthoot") || text.contains("kreditbee") || text.contains("moneyview") ||
            text.contains("cashe") || text.contains("stashfin") || text.contains("kissht") ||
            text.contains("fibe") || text.contains("ring") || text.contains("postpe") ||
            text.contains("payzapp") -> AppCategory.UPI_PAYMENT

        else -> null
    }
}

/**
 * Resolves the primary forest emerald accent color matching the design mockup (#046941).
 */
fun getCategoryAccentColor(category: AppCategory?): Color = Color(0xFF046941)

/**
 * Resolves soft background and crisp text colors for category pill badges.
 */
fun getCategoryBadgeColors(category: AppCategory?): Pair<Color, Color> {
    return Pair(Color(0xFFF1F5F9), Color(0xFF64748B))
}

/**
 * Resolves the unified forest emerald toggle accent color (#046941) for application toggles.
 */
fun getAppAccentColor(packageName: String, category: AppCategory?): Color = Color(0xFF046941)

/**
 * Renders an application icon that fills the entire container without inner spacing.
 */
@Composable
fun AppIconView(
    icon: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector = Icons.Default.Apps
) {
    val bitmap = remember(icon) {
        if (icon is android.graphics.drawable.Drawable) {
            try {
                icon.toBitmap(width = 128, height = 128).asImageBitmap()
            } catch (e: Throwable) {
                null
            }
        } else if (icon is ImageBitmap) {
            icon
        } else {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = contentDescription,
                tint = Color(0xFF0F172A),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportedAppsScreen(
    onNavigateBack: () -> Unit,
    repository: MonitoringSettingsRepository? = null,
    installedAppsProvider: InstalledAppsProvider? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settingsRepo = remember {
        repository ?: MonitoringSettingsRepository.getInstance(context)
    }
    val appsProvider = remember {
        installedAppsProvider ?: DefaultInstalledAppsProvider(context)
    }

    var masterEnabled by remember {
        mutableStateOf(
            try {
                settingsRepo.getSettings().globalEnabled
            } catch (e: Exception) {
                SecureLogger.e("SupportedAppsScreen", "Error loading monitoring settings", e)
                true
            }
        )
    }
    var enabledPackages by remember {
        mutableStateOf(
            try {
                settingsRepo.getSettings().enabledPackages
            } catch (e: Exception) {
                SecureLogger.e("SupportedAppsScreen", "Error loading enabled packages", e)
                AppCatalog.defaultEnabledPackages
            }
        )
    }

    var isNotificationTrackingEnabled by remember {
        mutableStateOf(
            try {
                settingsRepo.isNotificationTrackingEnabled()
            } catch (e: Exception) {
                true
            }
        )
    }

    var configuredApps by remember {
        mutableStateOf(
            try {
                settingsRepo.getAllConfiguredApps()
            } catch (e: Exception) {
                AppCatalog.allApps
            }
        )
    }

    var installedApps by remember {
        mutableStateOf<List<InstalledAppInfo>>(emptyList())
    }
    var hasLoadedInstalledApps by remember { mutableStateOf(false) }

    fun refreshState() {
        try {
            val settings = settingsRepo.getSettings()
            masterEnabled = settings.globalEnabled
            isNotificationTrackingEnabled = settingsRepo.isNotificationTrackingEnabled()
            enabledPackages = settings.enabledPackages
            configuredApps = settingsRepo.getAllConfiguredApps()
        } catch (e: Exception) {
            SecureLogger.e("SupportedAppsScreen", "Error refreshing monitoring state; preserving previous state", e)
        }
    }

    // Refresh state immediately and asynchronously load installed apps in the background.
    // On initial first run, perform one-time detection of financial apps on the phone and never scan again.
    LaunchedEffect(Unit) {
        refreshState()
        withContext(Dispatchers.IO) {
            try {
                val freshInstalled = appsProvider.getInstalledApps()
                val prefs = context.getSharedPreferences(MonitoringSettingsRepository.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                val isInitialDetectionDone = prefs.getBoolean("initial_app_detection_completed", false)

                if (!isInitialDetectionDone) {
                    val currentConfigured = settingsRepo.getAllConfiguredApps()
                    val configuredPkgSet = currentConfigured.map { it.packageName.lowercase() }.toSet()
                    val currentPkg = context.packageName.lowercase()

                    freshInstalled.forEach { installed ->
                        val lower = installed.packageName.lowercase()
                        if (lower != currentPkg && !configuredPkgSet.contains(lower)) {
                            val detectedCategory = detectAppCategory(installed.packageName, installed.displayName)
                            if (detectedCategory != null) {
                                val newApp = SupportedApp(
                                    packageName = installed.packageName,
                                    displayName = installed.displayName,
                                    description = "Detected ${detectedCategory.displayName}",
                                    category = detectedCategory,
                                    defaultEnabled = true
                                )
                                settingsRepo.addUserApp(newApp)
                            }
                        }
                    }
                    prefs.edit().putBoolean("initial_app_detection_completed", true).apply()
                }

                withContext(Dispatchers.Main) {
                    installedApps = freshInstalled
                    hasLoadedInstalledApps = true
                    if (!isInitialDetectionDone) {
                        refreshState()
                    }
                }
            } catch (e: Exception) {
                SecureLogger.e("SupportedAppsScreen", "Error loading installed apps in background", e)
            }
        }
    }

    // Refresh state when screen resumes
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val onToggleApp: (String, Boolean) -> Unit = remember(settingsRepo) {
        { packageName, isChecked ->
            val previous = enabledPackages
            enabledPackages = if (isChecked) {
                enabledPackages + packageName
            } else {
                enabledPackages - packageName
            }
            try {
                settingsRepo.setAppEnabled(packageName, isChecked)
            } catch (e: Exception) {
                SecureLogger.e("SupportedAppsScreen", "Error saving app monitoring setting for $packageName", e)
                enabledPackages = previous
            }
        }
    }

    // Dialog & UI States
    var showAddCustomAppDialog by remember { mutableStateOf(false) }
    var appToEdit by remember { mutableStateOf<SupportedApp?>(null) }
    var appToRemove by remember { mutableStateOf<SupportedApp?>(null) }

    // Authoritative category order: UPI & Payment Apps, Banking Apps, SMS & Messenger Apps
    val orderedCategories = remember {
        listOf(
            AppCategory.UPI_PAYMENT,
            AppCategory.BANKING,
            AppCategory.SMS_MESSENGER
        )
    }

    // Installed packages map: packageName (lowercase) -> InstalledAppInfo
    val installedInfoMap = remember(installedApps) {
        installedApps.associateBy { it.packageName.lowercase() }
    }

    // Active Supported Apps: STRICTLY SHOW ONLY INSTALLED APPS (configured INTERSECT installed)
    val activeSupportedApps = remember(configuredApps, installedInfoMap, hasLoadedInstalledApps) {
        val seen = mutableSetOf<String>()
        configuredApps.mapNotNull { configuredApp ->
            val lowerPkg = configuredApp.packageName.lowercase()
            val installed = installedInfoMap[lowerPkg]
                ?: if (appsProvider.isPackageInstalled(configuredApp.packageName)) {
                    InstalledAppInfo(configuredApp.packageName, configuredApp.displayName)
                } else null
                ?: return@mapNotNull null

            if (!seen.add(lowerPkg)) return@mapNotNull null
            val freshName = installed.displayName.takeIf { it.isNotBlank() } ?: configuredApp.displayName
            configuredApp.copy(displayName = freshName)
        }
    }

    val groupedActiveApps = remember(activeSupportedApps) {
        activeSupportedApps.groupBy { it.category }
    }

    // Identify user-added custom apps
    val userAddedApps = remember(configuredApps) {
        val builtInPackages = AppCatalog.allApps.map { it.packageName.lowercase() }.toSet()
        configuredApps.filter { !builtInPackages.contains(it.packageName.lowercase()) }
    }

    // Installed apps that can be added (excluding ArcTracker and already configured apps)
    val configuredPackageSet = remember(configuredApps) {
        configuredApps.map { it.packageName.lowercase() }.toSet()
    }
    val currentPackageName = context.packageName.lowercase()
    val addableApps = remember(installedApps, configuredPackageSet, currentPackageName) {
        installedApps.filter {
            val lower = it.packageName.lowercase()
            lower != currentPackageName && !configuredPackageSet.contains(lower)
        }.distinctBy { it.packageName.lowercase() }
    }

    fun confirmAdd(app: InstalledAppInfo, category: AppCategory) {
        if (!appsProvider.isPackageInstalled(app.packageName)) {
            refreshState()
            showAddCustomAppDialog = false
            return
        }

        val currentConfigured = settingsRepo.getAllConfiguredApps()
        if (currentConfigured.any { it.packageName.equals(app.packageName, ignoreCase = true) }) {
            refreshState()
            showAddCustomAppDialog = false
            return
        }

        val newSupportedApp = SupportedApp(
            packageName = app.packageName,
            displayName = app.displayName,
            description = "User added app",
            category = category,
            defaultEnabled = true
        )

        try {
            settingsRepo.addUserApp(newSupportedApp)
        } catch (e: Exception) {
            SecureLogger.e("SupportedAppsScreen", "Error persisting user added app", e)
        }

        showAddCustomAppDialog = false
        refreshState()
    }

    fun confirmEdit(app: SupportedApp, newCategory: AppCategory) {
        if (!appsProvider.isPackageInstalled(app.packageName)) {
            refreshState()
            appToEdit = null
            return
        }

        val updated = app.copy(category = newCategory)
        try {
            settingsRepo.updateUserApp(updated)
        } catch (e: Exception) {
            SecureLogger.e("SupportedAppsScreen", "Error updating user app category", e)
        }

        appToEdit = null
        refreshState()
    }

    fun confirmRemove(app: SupportedApp) {
        try {
            val success = settingsRepo.removeUserApp(app.packageName)
            if (!success) {
                SecureLogger.w("SupportedAppsScreen", "Failed to remove user app ${app.packageName}")
            }
        } catch (e: Exception) {
            SecureLogger.e("SupportedAppsScreen", "Error removing user app ${app.packageName}", e)
        }
        appToRemove = null
        refreshState()
    }

    // Toggle all apps in a given category ON or OFF
    val onToggleAllCategory: (AppCategory, Boolean) -> Unit = remember(settingsRepo, enabledPackages, groupedActiveApps) {
        { category, isChecked ->
            val categoryApps = groupedActiveApps[category] ?: emptyList()
            val categoryPackages = categoryApps.map { it.packageName }
            val newSet = if (isChecked) {
                enabledPackages + categoryPackages
            } else {
                enabledPackages - categoryPackages.toSet()
            }
            enabledPackages = newSet
            try {
                categoryPackages.forEach { pkg ->
                    settingsRepo.setAppEnabled(pkg, isChecked)
                }
            } catch (e: Exception) {
                SecureLogger.e("SupportedAppsScreen", "Error toggling all apps for category $category", e)
            }
        }
    }

    val monitoredCount = remember(enabledPackages, masterEnabled) {
        if (masterEnabled) enabledPackages.size else 0
    }

    val isListenerConnected = remember(context) {
        try {
            NotificationReaderService.isConnected ||
                NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = Color.White,
                shadowElevation = 0.5.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                ArcTrackerHeader(
                    title = "Monitored Apps",
                    subtitle = "App Notification Listeners",
                    onBackClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .statusBarsPadding(),
                    horizontalPadding = 16.dp,
                    actions = {
                        // Circular + Button to Add Custom App
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F172A))
                                .clickable { showAddCustomAppDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add Custom App",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Page Subtitle / Hint
            item {
                Text(
                    text = "Choose which apps can be monitored",
                    fontSize = 13.5.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(start = 2.dp)
                )
            }

            // Notification Listener Banner / Status Card (matches reference mockup styling)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                SecureLogger.e("SupportedAppsScreen", "Error opening notification listener settings", e)
                            }
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE9F4EE)),
                    border = BorderStroke(1.dp, Color(0xFFD8ECE1)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (isListenerConnected) Color(0xFF046941) else Color(0xFF94A3B8), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (isListenerConnected) Color(0xFF046941) else Color(0xFF64748B),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isListenerConnected) "Notification Listener: Connected" else "Notification Listener: Enable Permission",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF046941),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFD8ECE1))
                                .border(BorderStroke(1.dp, Color(0xFFC7E2D3)), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Sandboxed",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF046941)
                            )
                        }
                    }
                }
            }

            // Master App Monitoring Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Master App Monitoring",
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(if (masterEnabled) Color(0xFF046941) else Color(0xFF94A3B8), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (masterEnabled) "Active" else "Disabled",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (masterEnabled) Color(0xFF046941) else Color(0xFF64748B)
                                    )
                                    Text(
                                        text = " • ",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    Text(
                                        text = if (masterEnabled) "$monitoredCount apps monitored locally" else "Monitoring paused",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            Switch(
                                checked = masterEnabled,
                                onCheckedChange = { isChecked ->
                                    masterEnabled = isChecked
                                    try {
                                        settingsRepo.setGlobalEnabled(isChecked)
                                    } catch (e: Exception) {
                                        SecureLogger.e("SupportedAppsScreen", "Error saving global monitoring setting", e)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = Color(0xFF046941),
                                    checkedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFCBD5E1),
                                    uncheckedThumbColor = Color.White
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Zero notification data leaves your device sandbox.",
                                fontSize = 11.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            // Categories (UPI & Payment Apps, Banking Apps, SMS & Messaging Apps)
            orderedCategories.forEach { category ->
                val appsInCategory = groupedActiveApps[category] ?: emptyList()
                val isAllEnabled = appsInCategory.isNotEmpty() && appsInCategory.all { enabledPackages.contains(it.packageName) }

                item(key = category.id) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Section Header: Title + Toggle All Switch (numbers removed)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = category.displayName,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "Toggle All",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = isAllEnabled,
                                onCheckedChange = { isChecked ->
                                    onToggleAllCategory(category, isChecked)
                                },
                                enabled = masterEnabled,
                                modifier = Modifier.scale(0.8f),
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = Color(0xFF046941),
                                    checkedThumbColor = Color.White,
                                    uncheckedTrackColor = Color(0xFFCBD5E1),
                                    uncheckedThumbColor = Color.White,
                                    disabledCheckedTrackColor = Color(0xFF046941).copy(alpha = 0.4f),
                                    disabledUncheckedTrackColor = Color(0xFFCBD5E1).copy(alpha = 0.4f)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // App List Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (appsInCategory.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No apps installed in this category",
                                            fontSize = 12.5.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                } else {
                                    appsInCategory.forEachIndexed { index, app ->
                                        val isUserApp = userAddedApps.any { it.packageName.equals(app.packageName, ignoreCase = true) }
                                        MonitoredAppRow(
                                            app = app,
                                            installedInfo = installedInfoMap[app.packageName.lowercase()],
                                            isEnabled = enabledPackages.contains(app.packageName),
                                            masterEnabled = masterEnabled,
                                            onToggle = { isChecked ->
                                                onToggleApp(app.packageName, isChecked)
                                            },
                                            onEdit = if (isUserApp) { { appToEdit = app } } else null,
                                            onRemove = if (isUserApp) { { appToRemove = app } } else null,
                                            switchAccentColor = Color(0xFF046941)
                                        )

                                        if (index < appsInCategory.size - 1) {
                                            HorizontalDivider(
                                                color = Color(0xFFF1F5F9),
                                                thickness = 1.dp,
                                                modifier = Modifier.padding(horizontal = 14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Custom Apps Section
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Custom Apps",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // + Add Custom App Card (matching app global slate palette)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAddCustomAppDialog = true
                            },
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
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color(0xFFE9F4EE), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Add Custom App",
                                    tint = Color(0xFF046941),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Add Custom App",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Add any installed banking or fintech app to monitor",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Custom App Dialog
    if (showAddCustomAppDialog) {
        AddAppSelectorDialog(
            category = AppCategory.UPI_PAYMENT,
            availableApps = addableApps,
            onDismiss = {
                showAddCustomAppDialog = false
            },
            onConfirm = { selectedApp, targetCategory ->
                confirmAdd(selectedApp, targetCategory)
            }
        )
    }

    // Edit User-Added App Dialog
    if (appToEdit != null) {
        val targetApp = appToEdit!!
        EditUserAppDialog(
            app = targetApp,
            onDismiss = { appToEdit = null },
            onConfirm = { newCategory ->
                confirmEdit(targetApp, newCategory)
            }
        )
    }

    // Remove User-Added App Confirmation Dialog
    if (appToRemove != null) {
        val targetApp = appToRemove!!
        AlertDialog(
            onDismissRequest = { appToRemove = null },
            title = {
                Text(
                    text = "Remove ${targetApp.displayName}?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Text(
                    text = "This will remove the app from your Monitored Apps configuration. It will not be monitored until you add it again.",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { confirmRemove(targetApp) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { appToRemove = null }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * Single App Row inside category card matching mockup styling.
 * Icon fills the entire container box with zero inner space.
 */
@Composable
private fun MonitoredAppRow(
    app: SupportedApp,
    installedInfo: InstalledAppInfo?,
    isEnabled: Boolean,
    masterEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    switchAccentColor: Color = Color(0xFF046941)
) {
    val fallbackIcon = remember(app.packageName, app.category) {
        getAppVectorFallback(app.packageName, app.category)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Icon Box - fully clipped with zero inner space
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            AppIconView(
                icon = installedInfo?.icon,
                contentDescription = app.displayName,
                modifier = Modifier.fillMaxSize(),
                fallbackIcon = fallbackIcon
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // App Details: Name & Package Name
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.displayName,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = app.packageName,
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Actions if user-added app
        if (onEdit != null || onRemove != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onEdit != null) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit Category",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                if (onRemove != null) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Remove App",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Toggle Switch with clean white thumb
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            enabled = masterEnabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = switchAccentColor,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFCBD5E1),
                uncheckedThumbColor = Color.White,
                disabledCheckedTrackColor = switchAccentColor.copy(alpha = 0.4f),
                disabledUncheckedTrackColor = Color(0xFFCBD5E1).copy(alpha = 0.4f)
            )
        )
    }
}

/**
 * Add App Selector Dialog allowing user to select installed app and designate its category.
 */
@Composable
fun AddAppSelectorDialog(
    category: AppCategory,
    availableApps: List<InstalledAppInfo>,
    onDismiss: () -> Unit,
    onConfirm: (InstalledAppInfo, AppCategory) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var selectedCategory by remember { mutableStateOf(category) }

    val filteredApps = remember(searchQuery, availableApps) {
        if (searchQuery.isBlank()) availableApps
        else availableApps.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Add Custom App",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color(0xFF0F172A)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Category Selector Pills
                Text(
                    text = "Assign Category",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppCategory.values().forEach { cat ->
                        val isCatSelected = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCatSelected) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                                .clickable { selectedCategory = cat }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (cat) {
                                    AppCategory.UPI_PAYMENT -> "UPI"
                                    AppCategory.BANKING -> "Banking"
                                    AppCategory.SMS_MESSENGER -> "SMS"
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCatSelected) Color.White else Color(0xFF64748B)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps...", fontSize = 13.sp) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF0F172A),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "No available installed apps to add." else "No matching apps found.",
                            fontSize = 12.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isSelected = selectedApp?.packageName == app.packageName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0xFFF1F5F9) else Color.Transparent)
                                    .clickable { selectedApp = app }
                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE2E8F0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppIconView(
                                        icon = app.icon,
                                        contentDescription = app.displayName,
                                        modifier = Modifier.fillMaxSize(),
                                        fallbackIcon = Icons.Default.Apps
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.displayName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        color = Color(0xFF0F172A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedApp = app },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF0F172A))
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedApp?.let { onConfirm(it, selectedCategory) }
                },
                enabled = selectedApp != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0F172A),
                    disabledContainerColor = Color(0xFFCBD5E1)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Add App", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF64748B))
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * Edit User-Added App Category Dialog.
 */
@Composable
fun EditUserAppDialog(
    app: SupportedApp,
    onDismiss: () -> Unit,
    onConfirm: (AppCategory) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(app.category) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Edit App Category",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color(0xFF0F172A)
            )
        },
        text = {
            Column {
                Text(
                    text = app.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = app.packageName,
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Select Category",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(8.dp))

                AppCategory.values().forEach { cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedCategory == cat) Color(0xFFF1F5F9) else Color.Transparent)
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedCategory == cat),
                            onClick = { selectedCategory = cat },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF0F172A))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(cat.displayName, fontSize = 13.5.sp, color = Color(0xFF0F172A))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedCategory) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF64748B))
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}
