package com.example.arctracker.ui

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val name: String,
    val icon: Bitmap?,
    val category: String,
    val defaultSubtitle: String
)

val targetPackages = listOf(
    Triple("com.google.android.apps.nbu.paisa.user", "UPI & Payment Apps", "UPI payments, bills, recharges"),
    Triple("com.phonepe.app", "UPI & Payment Apps", "UPI payments, bills, recharges"),
    Triple("net.one97.paytm", "UPI & Payment Apps", "UPI payments, wallet, bills"),
    Triple("in.amazon.mShop.android.shopping", "UPI & Payment Apps", "Shopping, UPI, bills"),
    Triple("in.org.npci.upiapp", "UPI & Payment Apps", "UPI payments"),
    Triple("com.dreamplug.androidapp", "UPI & Payment Apps", "Credit card payments"),
    Triple("com.mobikwik_new", "UPI & Payment Apps", "Wallet, UPI, bills"),
    Triple("sinet.startup.inDriver", "UPI & Payment Apps", "Ride payments"),
    Triple("com.olacabs.customer", "UPI & Payment Apps", "Ride payments"),
    Triple("com.ubercab", "UPI & Payment Apps", "Ride payments"),
    Triple("com.freecharge.android", "UPI & Payment Apps", "Recharges, bills"),

    Triple("com.sbi.SBIAnywhereCorporate", "Banking Apps", "Banking, UPI"),
    Triple("com.sbi.SBIAnywhere", "Banking Apps", "Banking, UPI"),
    Triple("com.snapwork.hdfc", "Banking Apps", "Banking, UPI"),
    Triple("com.csam.icici.bank.imobile", "Banking Apps", "Banking, UPI"),
    Triple("com.axis.mobile", "Banking Apps", "Banking, UPI"),
    Triple("com.msf.kbank.mobile", "Banking Apps", "Banking, UPI"),
    Triple("money.jupiter", "Banking Apps", "Banking, UPI"),

)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportedAppsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE) }

    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var allDeviceApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var showAddAppDialogForCategory by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Load installed apps in background
    LaunchedEffect(refreshTrigger) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val foundAppsMap = mutableMapOf<String, InstalledApp>()
            val allDeviceAppsTemp = mutableListOf<InstalledApp>()
            
            val knownUpi = targetPackages.filter { it.second == "UPI & Payment Apps" }.map { it.first }
            val knownBanks = targetPackages.filter { it.second == "Banking Apps" }.map { it.first }
            
            val manualUpiApps = sharedPrefs.getStringSet("manual_apps_UPI & Payment Apps", emptySet()) ?: emptySet()
            val manualBankingApps = sharedPrefs.getStringSet("manual_apps_Banking Apps", emptySet()) ?: emptySet()
            
            // 1. Scan all installed packages for keywords and manual overrides
            val allPackages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in allPackages) {
                val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                val pkg = appInfo.packageName
                
                val label = pm.getApplicationLabel(appInfo).toString()
                val labelLower = label.lowercase()
                
                val hiddenApps = sharedPrefs.getStringSet("hidden_apps", emptySet()) ?: emptySet()
                if (hiddenApps.contains(pkg)) {
                    continue
                }
                
                var category: String? = null
                var subtitle = ""
                
                if (manualUpiApps.contains(pkg)) {
                    category = "UPI & Payment Apps"
                    subtitle = "Manually added"
                } else if (manualBankingApps.contains(pkg)) {
                    category = "Banking Apps"
                    subtitle = "Manually added"
                } else if (knownUpi.contains(pkg) || labelLower.contains("upi") || labelLower.contains(" pay") || labelLower.endsWith("pay") || labelLower.contains("gpay")) {
                    category = "UPI & Payment Apps"
                    subtitle = targetPackages.find { it.first == pkg }?.third ?: "UPI payments, bills"
                } else if (knownBanks.contains(pkg) || labelLower.contains("bank") || labelLower.contains("sbi") || labelLower.contains("hdfc") || labelLower.contains("icici") || labelLower.contains("ippb") || labelLower.contains("pnb")) {
                    category = "Banking Apps"
                    subtitle = targetPackages.find { it.first == pkg }?.third ?: "Banking, UPI"
                }
                
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (category != null || (!isSystemApp && launchIntent != null)) {
                    try {
                        val iconDrawable = pm.getApplicationIcon(appInfo)
                        val iconBitmap = iconDrawable.toBitmap(width = 120, height = 120)
                        
                        if (category != null) {
                            foundAppsMap[pkg] = InstalledApp(pkg, label, iconBitmap, category, subtitle)
                        }
                        if (!isSystemApp && launchIntent != null) {
                            allDeviceAppsTemp.add(InstalledApp(pkg, label, iconBitmap, "", "Installed App"))
                        }
                    } catch (e: Exception) {
                        // Skip if icon fails
                    }
                }
            }
            
            // 2. Discover any additional apps that handle UPI intents natively
            try {
                val upiIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("upi://pay"))
                val upiActivities = pm.queryIntentActivities(upiIntent, PackageManager.MATCH_DEFAULT_ONLY)
                val hiddenApps = sharedPrefs.getStringSet("hidden_apps", emptySet()) ?: emptySet()
                for (resolveInfo in upiActivities) {
                    val pkg = resolveInfo.activityInfo.packageName
                    if (hiddenApps.contains(pkg)) continue
                    if (!foundAppsMap.containsKey(pkg)) {
                        try {
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            val label = pm.getApplicationLabel(appInfo).toString()
                            val iconDrawable = pm.getApplicationIcon(appInfo)
                            val iconBitmap = iconDrawable.toBitmap(width = 120, height = 120)
                            foundAppsMap[pkg] = InstalledApp(pkg, label, iconBitmap, "UPI & Payment Apps", "UPI payments")
                        } catch (e: Exception) {}
                    }
                }
            } catch (e: Exception) {}
            
            installedApps = foundAppsMap.values.toList().sortedBy { it.name }
            allDeviceApps = allDeviceAppsTemp.sortedBy { it.name }
            isLoading = false
        }
    }

    val groupedApps = installedApps.groupBy { it.category }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFBF8FF)) // Light lavender background like mockup
    ) {
        // Custom Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF1E1E1E))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Supported Apps", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E1E1E))
        }

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
                    var masterEnabled by remember { 
                        mutableStateOf(sharedPrefs.getBoolean("master_app_monitoring", true)) 
                    }
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
                            Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color(0xFF673AB7)) // Mock icon
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Monitoring is enabled", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E1E1E))
                            Text("ArcTracker will read notifications from selected apps only.", fontSize = 12.sp, color = Color(0xFF757575), lineHeight = 16.sp)
                        }
                        Switch(
                            checked = masterEnabled,
                            onCheckedChange = { 
                                masterEnabled = it 
                                sharedPrefs.edit().putBoolean("master_app_monitoring", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF673AB7))
                        )
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF673AB7))
                    }
                }
            } else {
                val categories = listOf("UPI & Payment Apps", "Banking Apps")
                
                categories.forEach { categoryName ->
                    val appsInCategory = groupedApps[categoryName] ?: emptyList()
                        item {
                            CategoryAccordion(
                                categoryName = categoryName,
                                apps = appsInCategory,
                                sharedPrefs = sharedPrefs,
                                isInitiallyExpanded = categoryName == "UPI & Payment Apps",
                                onAddAppClick = { showAddAppDialogForCategory = categoryName },
                                onRemoveApp = { pkg ->
                                    val manualKey = "manual_apps_${categoryName}"
                                    val currentManual = sharedPrefs.getStringSet(manualKey, emptySet()) ?: emptySet()
                                    if (currentManual.contains(pkg)) {
                                        val newManual = currentManual.toMutableSet().apply { remove(pkg) }
                                        sharedPrefs.edit().putStringSet(manualKey, newManual).apply()
                                    } else {
                                        val hiddenApps = sharedPrefs.getStringSet("hidden_apps", emptySet()) ?: emptySet()
                                        val newHidden = hiddenApps.toMutableSet().apply { add(pkg) }
                                        sharedPrefs.edit().putStringSet("hidden_apps", newHidden).apply()
                                    }
                                    refreshTrigger++
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                }
            }
        }
        
        if (showAddAppDialogForCategory != null) {
            AddAppDialog(
                category = showAddAppDialogForCategory!!,
                allDeviceApps = allDeviceApps,
                onDismiss = { showAddAppDialogForCategory = null },
                onAppSelected = { pkg ->
                    val manualKey = "manual_apps_${showAddAppDialogForCategory!!}"
                    val currentManual = sharedPrefs.getStringSet(manualKey, emptySet()) ?: emptySet()
                    val newManual = currentManual.toMutableSet().apply { add(pkg) }
                    sharedPrefs.edit().putStringSet(manualKey, newManual).apply()
                    // Force refresh
                    refreshTrigger++
                    showAddAppDialogForCategory = null
                }
            )
        }
    }
}

@Composable
fun CategoryAccordion(
    categoryName: String,
    apps: List<InstalledApp>,
    sharedPrefs: android.content.SharedPreferences,
    isInitiallyExpanded: Boolean,
    onAddAppClick: () -> Unit,
    onRemoveApp: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(isInitiallyExpanded) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF3E5F5))
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Icon Mock
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFFF3E5F5), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
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
                        AppListItem(
                            app = app,
                            sharedPrefs = sharedPrefs,
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppListItem(
    app: InstalledApp,
    sharedPrefs: android.content.SharedPreferences,
    onRemoveApp: () -> Unit
) {
    val prefKey = "app_enabled_${app.packageName}"
    var isEnabled by remember { mutableStateOf(sharedPrefs.getBoolean(prefKey, true)) }
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .androidx.compose.foundation.combinedClickable(
                    onClick = {
                        isEnabled = !isEnabled
                        sharedPrefs.edit().putBoolean(prefKey, isEnabled).apply()
                    },
                    onLongClick = {
                        showMenu = true
                    }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (app.icon != null) {
                Image(
                    bitmap = app.icon.asImageBitmap(),
                    contentDescription = app.name,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFEEEEEE), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(app.name.take(1), fontWeight = FontWeight.Bold, color = Color(0xFF9E9E9E))
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(app.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E1E1E))
                Text(app.defaultSubtitle, fontSize = 12.sp, color = Color(0xFF9E9E9E))
            }
            
            Switch(
                checked = isEnabled,
                onCheckedChange = { 
                    isEnabled = it
                    sharedPrefs.edit().putBoolean(prefKey, it).apply()
                },
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
                text = { Text("Remove from list") },
                onClick = {
                    showMenu = false
                    onRemoveApp()
                },
                colors = MenuItemDefaults.colors(textColor = Color(0xFFD32F2F))
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppDialog(
    category: String,
    allDeviceApps: List<InstalledApp>,
    onDismiss: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredApps = remember(searchQuery, allDeviceApps) {
        if (searchQuery.isBlank()) allDeviceApps
        else allDeviceApps.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "Add to ",
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
                            .clickable { onAppSelected(app.packageName) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (app.icon != null) {
                            Image(
                                bitmap = app.icon.asImageBitmap(),
                                contentDescription = app.name,
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(app.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E1E1E))
                    }
                }
            }
        }
    }
}
