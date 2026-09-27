package com.example.arctracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.arctracker.settings.*

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
 * Renders a real application icon loaded from PackageManager, or falls back to a generic app icon.
 * Never uses the category icon as an application icon fallback.
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
                icon.toBitmap(width = 96, height = 96).asImageBitmap()
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
            modifier = modifier
        )
    } else {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = contentDescription,
            tint = Color(0xFF673AB7),
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportedAppsScreen(
    onNavigateBack: () -> Unit,
    repository: MonitoringSettingsRepository? = null,
    installedAppsProvider: InstalledAppsProvider? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
                android.util.Log.e("SupportedAppsScreen", "Error loading monitoring settings", e)
                true
            }
        )
    }
    var enabledPackages by remember {
        mutableStateOf(
            try {
                settingsRepo.getSettings().enabledPackages
            } catch (e: Exception) {
                android.util.Log.e("SupportedAppsScreen", "Error loading enabled packages", e)
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
            val freshInstalled = appsProvider.getInstalledApps()
            installedApps = freshInstalled
            hasLoadedInstalledApps = true
        } catch (e: Exception) {
            android.util.Log.e("SupportedAppsScreen", "Error refreshing monitoring state; preserving previous state", e)
        }
    }

    // Refresh state on initial launch
    LaunchedEffect(Unit) {
        refreshState()
    }

    // Refresh state when screen resumes (detects installs/uninstalls)
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
                android.util.Log.e("SupportedAppsScreen", "Error saving app monitoring setting for $packageName", e)
                enabledPackages = previous
            }
        }
    }

    // State for Add App dialog inside a specific category
    var categoryForAdd by remember { mutableStateOf<AppCategory?>(null) }

    // State for Edit App dialog
    var appToEdit by remember { mutableStateOf<SupportedApp?>(null) }

    // State for Remove App confirmation dialog
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

    // Active Supported Apps: Configured apps that are currently installed on this device
    val activeSupportedApps = remember(configuredApps, installedInfoMap, hasLoadedInstalledApps) {
        if (hasLoadedInstalledApps) {
            val seen = mutableSetOf<String>()
            configuredApps.mapNotNull { configuredApp ->
                val lowerPkg = configuredApp.packageName.lowercase()
                val installedInfo = installedInfoMap[lowerPkg] ?: return@mapNotNull null
                if (!seen.add(lowerPkg)) return@mapNotNull null
                val freshName = installedInfo.displayName.takeIf { it.isNotBlank() } ?: configuredApp.displayName
                configuredApp.copy(displayName = freshName)
            }
        } else {
            configuredApps.distinctBy { it.packageName.lowercase() }
        }
    }

    val groupedActiveApps = remember(activeSupportedApps) {
        activeSupportedApps.groupBy { it.category }
    }

    // Addable Apps: Currently installed apps that are not yet configured as Supported Apps (excluding ArcTracker)
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
            categoryForAdd = null
            return
        }

        val currentConfigured = settingsRepo.getAllConfiguredApps()
        if (currentConfigured.any { it.packageName.equals(app.packageName, ignoreCase = true) }) {
            refreshState()
            categoryForAdd = null
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
            android.util.Log.e("SupportedAppsScreen", "Error persisting user added app", e)
        }

        categoryForAdd = null
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
            android.util.Log.e("SupportedAppsScreen", "Error updating user app category", e)
        }

        appToEdit = null
        refreshState()
    }

    fun confirmRemove(app: SupportedApp) {
        try {
            val success = settingsRepo.removeUserApp(app.packageName)
            if (!success) {
                android.util.Log.e("SupportedAppsScreen", "Failed to remove user app ${app.packageName}")
            }
        } catch (e: Exception) {
            android.util.Log.e("SupportedAppsScreen", "Error removing user app ${app.packageName}", e)
        }
        appToRemove = null
        refreshState()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFBF8FF))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Text(
                    "Select the apps you want ArcTracker to monitor.\nTransactions from these apps will be automatically tracked.",
                    fontSize = 13.sp,
                    color = Color(0xFF757575),
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 24.dp, start = 4.dp, end = 4.dp)
                )

                // Master Switch Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFF3E5F5))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFFF3E5F5), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Payment, contentDescription = null, tint = Color(0xFF673AB7))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Monitoring is enabled", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E1E1E))
                            Text("ArcTracker will read notifications from selected apps only.", fontSize = 12.sp, color = Color(0xFF757575), lineHeight = 16.sp)
                        }
                        Switch(
                            checked = masterEnabled,
                            onCheckedChange = { isChecked ->
                                masterEnabled = isChecked
                                try {
                                    settingsRepo.setGlobalEnabled(isChecked)
                                } catch (e: Exception) {
                                    android.util.Log.e("SupportedAppsScreen", "Error saving global monitoring setting", e)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF673AB7))
                        )
                    }
                }
            }

            // SUPPORTED APPS Categories (each containing its own + Add App action)
            orderedCategories.forEach { category ->
                val appsInCategory = groupedActiveApps[category] ?: emptyList()
                item(key = category.id) {
                    CategoryAccordion(
                        category = category,
                        apps = appsInCategory,
                        installedInfoMap = installedInfoMap,
                        isInitiallyExpanded = (category == AppCategory.UPI_PAYMENT),
                        isEnabled = masterEnabled && isNotificationTrackingEnabled,
                        enabledPackages = enabledPackages,
                        onToggleApp = onToggleApp,
                        onAddAppClick = {
                            categoryForAdd = category
                        },
                        onEditAppClick = { app ->
                            appToEdit = app
                        },
                        onRemoveAppClick = { app ->
                            appToRemove = app
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Category-Specific Add App Selector Dialog
        if (categoryForAdd != null) {
            val targetCategory = categoryForAdd!!
            AddAppSelectorDialog(
                category = targetCategory,
                availableApps = addableApps,
                onDismiss = { categoryForAdd = null },
                onConfirm = { selectedApp ->
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
                        fontSize = 18.sp,
                        color = Color(0xFF1E1E1E)
                    )
                },
                text = {
                    Text(
                        text = "This will remove the app from your Supported Apps configuration.\n\nThe app will not be monitored until you add it again.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFF49454F)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { confirmRemove(targetApp) }
                    ) {
                        Text("Remove", color = Color(0xFFBA1A1A), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { appToRemove = null }
                    ) {
                        Text("Cancel", color = Color(0xFF673AB7))
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
fun AddAppSelectorDialog(
    category: AppCategory,
    availableApps: List<InstalledAppInfo>,
    onDismiss: () -> Unit,
    onConfirm: (InstalledAppInfo) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<InstalledAppInfo?>(null) }

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
                "Add to ${category.displayName}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF1E1E1E)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF673AB7),
                        unfocusedBorderColor = Color(0xFFE0E0E0)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

                if (filteredApps.isEmpty()) {
                    Text(
                        text = if (searchQuery.isBlank()) "No available installed apps to add." else "No matching apps found.",
                        fontSize = 13.sp,
                        color = Color(0xFF9E9E9E),
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isSelected = selectedApp?.packageName == app.packageName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedApp = app }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFEDE7F6), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppIconView(
                                        icon = app.icon,
                                        contentDescription = app.displayName,
                                        modifier = Modifier.size(24.dp),
                                        fallbackIcon = Icons.Default.Apps
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.displayName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1E1E1E),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        fontSize = 12.sp,
                                        color = Color(0xFF757575),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedApp = app },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF673AB7))
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
                    selectedApp?.let { onConfirm(it) }
                },
                enabled = selectedApp != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF673AB7),
                    disabledContainerColor = Color(0xFFE0E0E0)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Add", color = if (selectedApp != null) Color.White else Color(0xFF9E9E9E))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF757575))
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}

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
                "Edit Application",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF1E1E1E)
            )
        },
        text = {
            Column {
                Text(
                    app.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1E1E1E)
                )
                Text(
                    app.packageName,
                    fontSize = 12.sp,
                    color = Color(0xFF757575)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Current category",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF1E1E1E)
                )
                Spacer(modifier = Modifier.height(8.dp))

                AppCategory.values().forEach { cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCategory = cat }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedCategory == cat),
                            onClick = { selectedCategory = cat },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF673AB7))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(cat.displayName, fontSize = 14.sp, color = Color(0xFF1E1E1E))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedCategory) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF757575))
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun CategoryAccordion(
    category: AppCategory,
    apps: List<SupportedApp>,
    installedInfoMap: Map<String, InstalledAppInfo> = emptyMap(),
    isInitiallyExpanded: Boolean = (category == AppCategory.UPI_PAYMENT),
    isEnabled: Boolean = true,
    enabledPackages: Set<String>,
    onToggleApp: (String, Boolean) -> Unit,
    onAddAppClick: () -> Unit = {},
    onEditAppClick: (SupportedApp) -> Unit = {},
    onRemoveAppClick: (SupportedApp) -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(isInitiallyExpanded) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isEnabled) 1f else 0.7f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF3E5F5))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFFF3E5F5), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(category),
                        contentDescription = null,
                        tint = Color(0xFF673AB7),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = category.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF1E1E1E),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse ${category.displayName}" else "Expand ${category.displayName}",
                    tint = Color(0xFF1E1E1E)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    HorizontalDivider(color = Color(0xFFF5F5F5), thickness = 1.dp)

                    if (apps.isEmpty()) {
                        Text(
                            text = "No apps configured in this category.",
                            fontSize = 13.sp,
                            color = Color(0xFF9E9E9E),
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        apps.forEachIndexed { index, app ->
                            val isChecked = enabledPackages.contains(app.packageName)
                            val isUserAdded = !AppCatalog.containsPackage(app.packageName)
                            val appIcon = installedInfoMap[app.packageName.lowercase()]?.icon

                            SupportedAppListItem(
                                app = app,
                                icon = appIcon,
                                isUserAdded = isUserAdded,
                                isAppChecked = isChecked,
                                isRowEnabled = isEnabled,
                                onCheckedChange = { checked ->
                                    onToggleApp(app.packageName, checked)
                                },
                                onEditClick = if (isUserAdded) {
                                    { onEditAppClick(app) }
                                } else null,
                                onRemoveClick = if (isUserAdded) {
                                    { onRemoveAppClick(app) }
                                } else null
                            )
                            if (index < apps.size - 1) {
                                HorizontalDivider(
                                    color = Color(0xFFF5F5F5),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(start = 64.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF5F5F5), thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isEnabled) { onAddAppClick() }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add App",
                            tint = if (isEnabled) Color(0xFF673AB7) else Color(0xFF9E9E9E),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "+ Add App",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isEnabled) Color(0xFF673AB7) else Color(0xFF9E9E9E)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SupportedAppListItem(
    app: SupportedApp,
    icon: Any? = null,
    isUserAdded: Boolean = false,
    isAppChecked: Boolean = true,
    isRowEnabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit = {},
    onEditClick: (() -> Unit)? = null,
    onRemoveClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isRowEnabled) { onCheckedChange(!isAppChecked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFFEDE7F6), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AppIconView(
                icon = icon,
                contentDescription = app.displayName,
                modifier = Modifier.size(24.dp),
                fallbackIcon = Icons.Default.Apps
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = app.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF1E1E1E),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.description,
                fontSize = 12.sp,
                color = Color(0xFF9E9E9E),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isUserAdded) {
            if (onEditClick != null) {
                IconButton(
                    onClick = onEditClick,
                    enabled = isRowEnabled,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit ${app.displayName}",
                        tint = if (isRowEnabled) Color(0xFF757575) else Color(0xFFBDBDBD),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (onRemoveClick != null) {
                IconButton(
                    onClick = onRemoveClick,
                    enabled = isRowEnabled,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove ${app.displayName}",
                        tint = if (isRowEnabled) Color(0xFFBA1A1A) else Color(0xFFE57373),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }

        Switch(
            checked = isAppChecked,
            onCheckedChange = if (isRowEnabled) onCheckedChange else null,
            enabled = isRowEnabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Color(0xFF673AB7),
                uncheckedTrackColor = Color(0xFF9E9E9E)
            ),
            modifier = Modifier.scale(0.85f)
        )
    }
}

@Composable
fun MockCategoryAccordion(
    categoryName: String,
    apps: List<MockApp>,
    isInitiallyExpanded: Boolean,
    isEnabled: Boolean = true,
    enabledPackages: Set<String>,
    onToggleApp: (String, Boolean) -> Unit,
    onAddAppClick: () -> Unit = {},
    onRemoveApp: (String) -> Unit = {}
) {
    val category = AppCategory.fromDisplayName(categoryName) ?: AppCategory.UPI_PAYMENT
    val supportedApps = apps.map {
        SupportedApp(
            packageName = it.packageName,
            displayName = it.name,
            description = it.subtitle,
            category = category
        )
    }
    CategoryAccordion(
        category = category,
        apps = supportedApps,
        isInitiallyExpanded = isInitiallyExpanded,
        isEnabled = isEnabled,
        enabledPackages = enabledPackages,
        onToggleApp = onToggleApp,
        onAddAppClick = onAddAppClick
    )
}

@Composable
fun MockAppListItem(
    app: MockApp,
    isAppChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onRemoveApp: () -> Unit = {}
) {
    val supportedApp = SupportedApp(
        packageName = app.packageName,
        displayName = app.name,
        description = app.subtitle,
        category = AppCategory.fromDisplayName(app.category) ?: AppCategory.UPI_PAYMENT
    )
    SupportedAppListItem(
        app = supportedApp,
        isAppChecked = isAppChecked,
        onCheckedChange = onCheckedChange
    )
}
