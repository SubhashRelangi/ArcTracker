package com.example.arctracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arctracker.settings.*

data class MockApp(
    val packageName: String,
    val name: String,
    val category: String,
    val subtitle: String,
    val icon: ImageVector
)

/**
 * Resolves an icon for the given category with safe fallback.
 */
fun getCategoryIcon(category: AppCategory?): ImageVector {
    return when (category) {
        AppCategory.BANKING -> Icons.Default.AccountBalance
        AppCategory.SMS_MESSENGER -> Icons.Default.Sms
        AppCategory.UPI_PAYMENT -> Icons.Default.Payment
        null -> Icons.Default.Payment
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

    fun refreshState() {
        try {
            val settings = settingsRepo.getSettings()
            masterEnabled = settings.globalEnabled
            enabledPackages = settings.enabledPackages
            configuredApps = settingsRepo.getAllConfiguredApps()
            installedApps = appsProvider.getInstalledApps()
        } catch (e: Exception) {
            android.util.Log.e("SupportedAppsScreen", "Error refreshing monitoring state", e)
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

    var showAddAppDialogForCategory by remember { mutableStateOf<AppCategory?>(null) }
    var appToCategorize by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var selectedCategory by remember { mutableStateOf(AppCategory.UPI_PAYMENT) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Authoritative category order: UPI & Payment Apps, Banking Apps, SMS & Messenger Apps
    val orderedCategories = remember {
        listOf(
            AppCategory.UPI_PAYMENT,
            AppCategory.BANKING,
            AppCategory.SMS_MESSENGER
        )
    }

    // Active Supported Apps: Configured apps that are currently installed on this device
    val installedPackageSet = remember(installedApps) {
        installedApps.map { it.packageName }.toSet()
    }

    val activeSupportedApps = remember(configuredApps, installedPackageSet) {
        // If installedApps is populated, strictly filter to installed packages.
        // If provider returned empty (e.g. preview or unpopulated test), fall back safely to configured apps.
        if (installedPackageSet.isNotEmpty()) {
            configuredApps.filter { installedPackageSet.contains(it.packageName) }
        } else {
            configuredApps
        }
    }

    val groupedActiveApps = remember(activeSupportedApps) {
        activeSupportedApps.groupBy { it.category }
    }

    // Addable Apps: Currently installed apps that are not yet configured as Supported Apps
    val configuredPackageSet = remember(configuredApps) {
        configuredApps.map { it.packageName }.toSet()
    }

    val addableApps = remember(installedApps, configuredPackageSet) {
        installedApps.filter { !configuredPackageSet.contains(it.packageName) }
    }

    fun confirmAdd(app: InstalledAppInfo, category: AppCategory) {
        // 1. Verify the package is still installed
        if (!appsProvider.isPackageInstalled(app.packageName)) {
            errorMessage = "Application is no longer installed on this device."
            refreshState()
            return
        }

        // 2. Verify it is not already supported
        val currentConfigured = settingsRepo.getAllConfiguredApps()
        if (currentConfigured.any { it.packageName.equals(app.packageName, ignoreCase = true) }) {
            errorMessage = "Application is already configured as a supported app."
            refreshState()
            return
        }

        // 3. Persist the user-added app definition (and enables package by default)
        val newSupportedApp = SupportedApp(
            packageName = app.packageName,
            displayName = app.displayName,
            description = "User added app",
            category = category,
            defaultEnabled = true
        )

        val success = try {
            settingsRepo.addUserApp(newSupportedApp)
        } catch (e: Exception) {
            android.util.Log.e("SupportedAppsScreen", "Error persisting user added app", e)
            false
        }

        if (!success) {
            errorMessage = "Failed to add application."
            return
        }

        // 4. Close category selection dialog and refresh screen state
        appToCategorize = null
        showAddAppDialogForCategory = null
        errorMessage = null
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

            // SUPPORTED APPS Section
            orderedCategories.forEach { category ->
                val appsInCategory = groupedActiveApps[category] ?: emptyList()
                item(key = category.id) {
                    CategoryAccordion(
                        category = category,
                        apps = appsInCategory,
                        isInitiallyExpanded = (category == AppCategory.UPI_PAYMENT),
                        isEnabled = masterEnabled,
                        enabledPackages = enabledPackages,
                        onToggleApp = onToggleApp,
                        onAddAppClick = {
                            showAddAppDialogForCategory = category
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // ADD APPS Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Add Apps",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1E1E1E),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                Text(
                    text = "Apps currently installed on your phone but not yet configured as Supported Apps.",
                    fontSize = 12.sp,
                    color = Color(0xFF757575),
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFF3E5F5))
                ) {
                    if (addableApps.isEmpty()) {
                        Text(
                            text = "No additional installed apps found to add.",
                            fontSize = 13.sp,
                            color = Color(0xFF9E9E9E),
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        Column {
                            addableApps.forEachIndexed { index, app ->
                                AddAppListItem(
                                    app = app,
                                    onAddClick = {
                                        appToCategorize = app
                                        selectedCategory = AppCategory.UPI_PAYMENT
                                        errorMessage = null
                                    }
                                )
                                if (index < addableApps.size - 1) {
                                    HorizontalDivider(
                                        color = Color(0xFFF5F5F5),
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(start = 64.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category Selection Dialog for Add Apps flow
        if (appToCategorize != null) {
            val app = appToCategorize!!
            AlertDialog(
                onDismissRequest = {
                    appToCategorize = null
                    errorMessage = null
                },
                title = {
                    Text(
                        "Add Application",
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
                            "Select category",
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

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(errorMessage!!, color = Color(0xFFD32F2F), fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { confirmAdd(app, selectedCategory) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            appToCategorize = null
                            errorMessage = null
                        }
                    ) {
                        Text("Cancel", color = Color(0xFF757575))
                    }
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Quick add dialog when "+ Add App" inside a category accordion is clicked
        if (showAddAppDialogForCategory != null) {
            val targetCategory = showAddAppDialogForCategory!!
            val mockAppItems = addableApps.map {
                MockApp(
                    packageName = it.packageName,
                    name = it.displayName,
                    category = targetCategory.displayName,
                    subtitle = it.packageName,
                    icon = Icons.Default.Apps
                )
            }
            MockAddAppDialog(
                category = targetCategory.displayName,
                availableApps = mockAppItems,
                onDismiss = { showAddAppDialogForCategory = null },
                onAppSelected = { selectedMockApp ->
                    val installedApp = addableApps.find { it.packageName == selectedMockApp.packageName }
                    if (installedApp != null) {
                        confirmAdd(installedApp, targetCategory)
                    } else {
                        showAddAppDialogForCategory = null
                    }
                }
            )
        }
    }
}

@Composable
fun AddAppListItem(
    app: InstalledAppInfo,
    onAddClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFFEDE7F6), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = app.displayName,
                tint = Color(0xFF673AB7),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF1E1E1E),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                fontSize = 12.sp,
                color = Color(0xFF9E9E9E),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onAddClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text("Add", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun CategoryAccordion(
    category: AppCategory,
    apps: List<SupportedApp>,
    isInitiallyExpanded: Boolean = (category == AppCategory.UPI_PAYMENT),
    isEnabled: Boolean = true,
    enabledPackages: Set<String>,
    onToggleApp: (String, Boolean) -> Unit,
    onAddAppClick: () -> Unit = {}
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
                            SupportedAppListItem(
                                app = app,
                                isAppChecked = isChecked,
                                onCheckedChange = { checked ->
                                    onToggleApp(app.packageName, checked)
                                }
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
                            .clickable { onAddAppClick() }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add App",
                            tint = Color(0xFF673AB7),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add App", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF673AB7))
                    }
                }
            }
        }
    }
}

@Composable
fun SupportedAppListItem(
    app: SupportedApp,
    isAppChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isAppChecked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFFEDE7F6), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getCategoryIcon(app.category),
                contentDescription = app.displayName,
                tint = Color(0xFF673AB7),
                modifier = Modifier.size(20.dp)
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

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = isAppChecked,
            onCheckedChange = onCheckedChange,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MockAddAppDialog(
    category: String,
    availableApps: List<MockApp>,
    onDismiss: () -> Unit,
    onAppSelected: (MockApp) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(searchQuery, availableApps) {
        if (searchQuery.isBlank()) availableApps
        else availableApps.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "Add to $category",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF1E1E1E),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                placeholder = { Text("Search apps...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF673AB7),
                    unfocusedBorderColor = Color(0xFFE0E0E0)
                )
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
            ) {
                items(filteredApps) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAppSelected(app) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFEDE7F6), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(app.icon, contentDescription = null, tint = Color(0xFF673AB7), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E1E1E))
                            Text(app.packageName, fontSize = 12.sp, color = Color(0xFF757575))
                        }
                    }
                }
            }
        }
    }
}
