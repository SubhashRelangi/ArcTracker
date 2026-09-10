package com.example.arctracker.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.arctracker.utils.BankSenderFilter
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                ImportResultCard(result = importResult!!)
                Button(
                    onClick = { importResult = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import More")
                }
            } else if (isImporting) {
                ImportProgressCard(importProgress, totalMessages)
            } else {
                // Bank-only import info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Only messages from recognized bank senders (e.g. JD-HDFCBK) are scanned. Promotional, OTP and random messages are skipped automatically.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                if (!hasSmsPermission) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "SMS permission is required to scan your inbox.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Select Period", fontWeight = FontWeight.Bold)
                        periods.forEachIndexed { index, pair ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = (index == selectedPeriodIndex),
                                    onClick = { selectedPeriodIndex = index }
                                )
                                Text(text = pair.first, fontSize = 14.sp)
                            }
                        }
                    }
                }

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
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(if (hasSmsPermission) "Scan Bank Messages" else "Grant SMS Permission")
                }
            }
        }
    }
}

@Composable
fun ImportResultCard(result: SmsImporter.ImportResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Import Complete",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            ResultRow(Icons.Outlined.Info, "${result.totalFound} messages scanned")
            ResultRow(Icons.Outlined.AccountBalance, "${result.bankMessagesFound} bank messages found")
            ResultRow(Icons.Filled.CheckCircle, "${result.imported} transactions imported", MaterialTheme.colorScheme.primary)
            ResultRow(Icons.Filled.ContentCopy, "${result.duplicatesSkipped} duplicates skipped")
            ResultRow(Icons.Filled.Campaign, "${result.promotionalSkipped} promotional messages skipped")
            ResultRow(Icons.Filled.RemoveCircleOutline, "${result.nonBankSkipped} non-bank senders skipped")
            ResultRow(Icons.Outlined.Info, "${result.ignored} unparsable messages ignored")

            if (result.banksDetected.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Banks detected: ${result.banksDetected.joinToString(", ")}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Imported spending", fontSize = 14.sp)
                Text(
                    "₹${"%.2f".format(result.totalAmountImported)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Imported income", fontSize = 14.sp)
                Text(
                    "₹${"%.2f".format(result.totalIncomeImported)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ImportProgressCard(processed: Int, total: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Scanning messages...", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "$processed / ${if (total > 0) total.toString() else "?"}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ResultRow(icon: ImageVector, text: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 14.sp)
    }
}
