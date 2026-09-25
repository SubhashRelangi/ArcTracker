package com.example.arctracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arctracker.settings.AppCatalog
import com.example.arctracker.settings.AppCategory
import com.example.arctracker.settings.MonitoringSettingsRepository

data class MockApp(
    val packageName: String,
    val name: String,
    val category: String,
    val subtitle: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportedAppsScreen(
    onNavigateBack: () -> Unit,
    repository: MonitoringSettingsRepository? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsRepo = remember {
        repository ?: MonitoringSettingsRepository.getInstance(context)
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

    var showAddAppDialogForCategory by remember { mutableStateOf<String?>(null) }

    val initialApps = remember {
        AppCatalog.allApps.map { app ->
            val icon = when (app.category) {
                AppCategory.BANKING -> Icons.Default.AccountBalance
                AppCategory.SMS_MESSENGER -> Icons.Default.Sms
                AppCategory.UPI_PAYMENT -> Icons.Default.Payment
            }
            MockApp(
                packageName = app.packageName,
                name = app.displayName,
                category = app.category.displayName,
                subtitle = app.description,
                icon = icon
            )
        }
    }

    var appsList by remember { mutableStateOf(initialApps) }

    val allAvailableMockApps = remember {
        emptyList<MockApp>()
    }

    val groupedApps = appsList.groupBy { it.category }

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

            val categories = listOf("UPI & Payment Apps", "Banking Apps", "SMS & Messenger Apps")

            categories.forEach { categoryName ->
                val appsInCategory = groupedApps[categoryName] ?: emptyList()
                item {
                    MockCategoryAccordion(
                        categoryName = categoryName,
                        apps = appsInCategory,
                        isInitiallyExpanded = categoryName == "UPI & Payment Apps",
                        isEnabled = masterEnabled,
                        enabledPackages = enabledPackages,
                        onToggleApp = onToggleApp,
                        onAddAppClick = { showAddAppDialogForCategory = categoryName },
                        onRemoveApp = { pkg ->
                            appsList = appsList.filter { it.packageName != pkg }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        if (showAddAppDialogForCategory != null) {
            val categoryToAdd = showAddAppDialogForCategory!!
            MockAddAppDialog(
                category = categoryToAdd,
                availableApps = allAvailableMockApps.filter { it.category == categoryToAdd },
                onDismiss = { showAddAppDialogForCategory = null },
                onAppSelected = { selectedApp ->
                    if (appsList.none { it.packageName == selectedApp.packageName }) {
                        appsList = appsList + selectedApp
                    }
                    showAddAppDialogForCategory = null
                }
            )
        }
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
    onAddAppClick: () -> Unit,
    onRemoveApp: (String) -> Unit
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
                    val icon = when (categoryName) {
                        "Banking Apps" -> Icons.Default.AccountBalance
                        "SMS & Messenger Apps" -> Icons.Default.Sms
                        else -> Icons.Default.Payment
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF673AB7),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = categoryName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF1E1E1E),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle",
                    tint = Color(0xFF1E1E1E)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    HorizontalDivider(color = Color(0xFFF5F5F5), thickness = 1.dp)

                    apps.forEachIndexed { index, app ->
                        val isChecked = enabledPackages.contains(app.packageName)
                        MockAppListItem(
                            app = app,
                            isAppChecked = isChecked,
                            onCheckedChange = { checked ->
                                onToggleApp(app.packageName, checked)
                            },
                            onRemoveApp = { onRemoveApp(app.packageName) }
                        )
                        if (index < apps.size - 1) {
                            HorizontalDivider(
                                color = Color(0xFFF5F5F5),
                                thickness = 1.dp,
                                modifier = Modifier.padding(start = 64.dp)
                            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MockAppListItem(
    app: MockApp,
    isAppChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onRemoveApp: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onCheckedChange(!isAppChecked) },
                    onLongClick = { showMenu = true }
                )
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
                    imageVector = app.icon,
                    contentDescription = app.name,
                    tint = Color(0xFF673AB7),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(app.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E1E1E))
                Text(app.subtitle, fontSize = 12.sp, color = Color(0xFF9E9E9E))
            }

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

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(Color.White)
        ) {
            DropdownMenuItem(
                text = { Text("Remove from list", color = Color(0xFFD32F2F)) },
                onClick = {
                    showMenu = false
                    onRemoveApp()
                }
            )
        }
    }
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
                        Text(app.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E1E1E))
                    }
                }
            }
        }
    }
}
