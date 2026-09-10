package com.example.arctracker.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.arctracker.utils.SmsImporter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsImportScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showPermissionDeniedDialog by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasSmsPermission = isGranted
        if (!isGranted) {
            showPermissionDeniedDialog = true
        }
    }

    val periods = listOf(
        "Last 30 days" to 30L * 24 * 60 * 60 * 1000L,
        "Last 3 months" to 90L * 24 * 60 * 60 * 1000L,
        "Last 6 months" to 180L * 24 * 60 * 60 * 1000L,
        "Last 1 year" to 365L * 24 * 60 * 60 * 1000L,
        "All available" to 0L
    )

    var selectedPeriodIndex by remember { mutableStateOf(0) }
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf(0) }
    var totalMessages by remember { mutableStateOf(0) }
    var importResult by remember { mutableStateOf<SmsImporter.ImportResult?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Previous Transactions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (showPermissionDeniedDialog) {
                AlertDialog(
                    onDismissRequest = { showPermissionDeniedDialog = false },
                    title = { Text("Permission Required") },
                    text = { Text("ArcTracker needs SMS permission to scan your historical messages for transactions. Please grant it in your device settings or try again.") },
                    confirmButton = {
                        TextButton(onClick = { showPermissionDeniedDialog = false }) {
                            Text("OK")
                        }
                    }
                )
            }

            if (importResult != null) {
                // Show Results
                val result = importResult!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Import Complete", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${result.totalFound} transaction messages found")
                        Text("${result.imported} transactions imported", fontWeight = FontWeight.Bold)
                        Text("${result.duplicatesSkipped} duplicates skipped")
                        Text("${result.ignored} unrelated messages ignored")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Total imported spending: ₹${result.totalAmountImported}", color = MaterialTheme.colorScheme.error)
                        Text("Total imported income: ₹${result.totalIncomeImported}", color = MaterialTheme.colorScheme.primary)
                    }
                }

                Button(
                    onClick = { importResult = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import More")
                }

            } else if (isImporting) {
                // Show Progress
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Scanning messages...", style = MaterialTheme.typography.titleMedium)
                    if (totalMessages > 0) {
                        Text("$importProgress / $totalMessages")
                    }
                }
            } else {
                // Show Options
                Text(
                    "Import transactions from bank and payment messages already stored on your phone.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Select Period:", fontWeight = FontWeight.Bold)
                
                periods.forEachIndexed { index, pair ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = (index == selectedPeriodIndex),
                            onClick = { selectedPeriodIndex = index }
                        )
                        Text(
                            text = pair.first,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        if (!hasSmsPermission) {
                            permissionLauncher.launch(Manifest.permission.READ_SMS)
                        } else {
                            val timeToSubtract = periods[selectedPeriodIndex].second
                            val startTime = if (timeToSubtract == 0L) 0L else System.currentTimeMillis() - timeToSubtract
                            
                            isImporting = true
                            importProgress = 0
                            totalMessages = 0
                            
                            scope.launch {
                                val result = SmsImporter.importSms(context, startTime) { processed, total ->
                                    importProgress = processed
                                    totalMessages = total
                                }
                                importResult = result
                                isImporting = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (hasSmsPermission) "Scan Messages" else "Grant SMS Permission")
                }
            }
        }
    }
}
