package com.example.arctracker.ui

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.arctracker.service.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class SmsImportUiPhase {
    SELECT_RANGE,
    SCANNING,
    SCAN_PREVIEW,
    IMPORTING,
    IMPORT_COMPLETE,
    ERROR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsImportScreen(
    onNavigateBack: () -> Unit,
    manager: HistoricalSmsImportManager? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val importManager = manager ?: remember { HistoricalSmsImportManager.create(context) }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    var selectedPeriodIndex by remember { mutableStateOf(1) } // Default to "Last 3 months"
    var startDateMillis by remember { mutableStateOf(SmsPermissionHelper.calculateThreeMonthsAgoTimestamp()) }
    var endDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    var currentPhase by remember { mutableStateOf(SmsImportUiPhase.SELECT_RANGE) }
    var scanProgress by remember { mutableStateOf(SmsScanProgress(0)) }
    var scanResult by remember { mutableStateOf<SmsScanResult.Success?>(null) }
    var importProgress by remember { mutableStateOf(SmsImportProgress(0, 0)) }
    var importResult by remember { mutableStateOf<SmsImportResult.Success?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCancelled by remember { mutableStateOf(false) }

    var hasSmsPermission by remember {
        mutableStateOf(SmsPermissionHelper.isSmsPermissionGranted(context))
    }
    var hasRequestedPermissionThisSession by rememberSaveable { mutableStateOf(false) }
    var isPermanentlyDenied by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = SmsPermissionHelper.isSmsPermissionGranted(context)
                hasSmsPermission = granted
                if (granted) {
                    isPermanentlyDenied = false
                    SmsPermissionHelper.setInitialImportCompleted(context, true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isDateRangeValid = startDateMillis <= endDateMillis

    fun triggerScan() {
        SmsPermissionHelper.setInitialImportCompleted(context, true)
        errorMessage = null
        currentPhase = SmsImportUiPhase.SCANNING
        isCancelled = false
        scope.launch {
            val res = importManager.scan(startDateMillis, endDateMillis) { prog ->
                scanProgress = prog
                !isCancelled
            }
            if (isCancelled || res is SmsScanResult.Cancelled) {
                currentPhase = SmsImportUiPhase.SELECT_RANGE
            } else if (res is SmsScanResult.Success) {
                scanResult = res
                currentPhase = SmsImportUiPhase.SCAN_PREVIEW
            } else if (res is SmsScanResult.Failure) {
                errorMessage = res.message
                currentPhase = SmsImportUiPhase.ERROR
            } else if (res is SmsScanResult.PermissionRequired) {
                hasSmsPermission = false
                currentPhase = SmsImportUiPhase.SELECT_RANGE
            }
        }
    }

    // Permission launcher for READ_SMS
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasSmsPermission = isGranted
        if (isGranted) {
            isPermanentlyDenied = false
            SmsPermissionHelper.setInitialImportCompleted(context, true)
            triggerScan()
        } else {
            // User denied permission: stay on SELECT_RANGE, do NOT report terminal error
            currentPhase = SmsImportUiPhase.SELECT_RANGE
            val activity = context.findActivity()
            if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_SMS)) {
                isPermanentlyDenied = true
            }
        }
    }

    fun updatePeriod(index: Int) {
        selectedPeriodIndex = index
        val cal = Calendar.getInstance()
        val now = System.currentTimeMillis()
        endDateMillis = now
        when (index) {
            0 -> { // Last 30 days
                cal.add(Calendar.DAY_OF_YEAR, -30)
                startDateMillis = cal.timeInMillis
            }
            1 -> { // Last 3 months
                startDateMillis = SmsPermissionHelper.calculateThreeMonthsAgoTimestamp(now)
            }
            2 -> { // Last 6 months
                cal.add(Calendar.MONTH, -6)
                startDateMillis = cal.timeInMillis
            }
            3 -> { // Last 1 year
                cal.add(Calendar.YEAR, -1)
                startDateMillis = cal.timeInMillis
            }
            // 4 is custom; user can edit directly
        }
    }

    fun showDatePicker(initialMillis: Long, onDateSelected: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onDateSelected(selectedCal.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        TopAppBar(
            title = { Text("Import Previous Transactions", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White,
                titleContentColor = Color(0xFF1E1E1E)
            )
        )
        HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (currentPhase) {
                SmsImportUiPhase.SELECT_RANGE -> {
                    // Privacy & Offline Notice Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.AccountBalance,
                                contentDescription = null,
                                tint = Color(0xFF673AB7),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "ArcTracker scans your device's SMS inbox for bank, credit card, and UPI transaction messages. All processing is 100% offline and private.",
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = Color(0xFF311B92)
                            )
                        }
                    }

                    // Period Selection Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Select Time Period", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E1E1E))
                            Spacer(modifier = Modifier.height(8.dp))

                            val periodLabels = listOf("Last 30 days", "Last 3 months", "Last 6 months", "Last 1 year", "Custom range")
                            periodLabels.forEachIndexed { index, label ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { updatePeriod(index) }
                                ) {
                                    RadioButton(
                                        selected = (selectedPeriodIndex == index),
                                        onClick = { updatePeriod(index) },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF673AB7))
                                    )
                                    Text(text = label, fontSize = 14.sp, color = Color(0xFF1E1E1E))
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFFEEEEEE))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Date Range Pickers
                            Text("Date Range", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF757575))
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Start Date Button
                                OutlinedButton(
                                    onClick = {
                                        showDatePicker(startDateMillis) { newDate ->
                                            startDateMillis = newDate
                                            selectedPeriodIndex = 4 // Switch to Custom
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(dateFormat.format(Date(startDateMillis)), fontSize = 12.sp)
                                    }
                                }

                                // End Date Button
                                OutlinedButton(
                                    onClick = {
                                        showDatePicker(endDateMillis) { newDate ->
                                            endDateMillis = newDate
                                            selectedPeriodIndex = 4 // Switch to Custom
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(dateFormat.format(Date(endDateMillis)), fontSize = 12.sp)
                                    }
                                }
                            }

                            if (!isDateRangeValid) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Start date must be before or equal to end date",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Informational permission card when READ_SMS is not yet granted
                    if (!hasSmsPermission) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF673AB7),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "SMS Permission",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1E1E1E)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isPermanentlyDenied)
                                            "SMS permission is required to scan previous messages. Please allow SMS permission in App Settings."
                                        else
                                            "SMS permission is required to scan previous messages.",
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = Color(0xFF555555)
                                    )
                                }
                                if (isPermanentlyDenied) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(
                                        onClick = { SmsPermissionHelper.openAppSettings(context) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Settings", color = Color(0xFF673AB7), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Error Message (if any)
                    if (errorMessage != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFD32F2F))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(errorMessage!!, color = Color(0xFFC62828), fontSize = 13.sp)
                            }
                        }
                    }

                    // Scan Messages Button
                    Button(
                        onClick = {
                            errorMessage = null
                            if (!isDateRangeValid) return@Button

                            if (SmsPermissionHelper.isSmsPermissionGranted(context)) {
                                hasSmsPermission = true
                                isPermanentlyDenied = false
                                triggerScan()
                            } else {
                                val activity = context.findActivity()
                                val permanentlyDenied = hasRequestedPermissionThisSession && activity != null &&
                                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_SMS)

                                if (permanentlyDenied) {
                                    isPermanentlyDenied = true
                                    SmsPermissionHelper.openAppSettings(context)
                                } else {
                                    hasRequestedPermissionThisSession = true
                                    permissionLauncher.launch(Manifest.permission.READ_SMS)
                                }
                            }
                        },
                        enabled = isDateRangeValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
                    ) {
                        Text("Scan Messages", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }

                SmsImportUiPhase.SCANNING -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Color(0xFF673AB7))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Scanning SMS messages...", style = MaterialTheme.typography.titleMedium, color = Color(0xFF1E1E1E))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "${scanProgress.recordsProcessed} messages scanned",
                                fontSize = 14.sp,
                                color = Color(0xFF757575)
                            )
                            if (scanProgress.financialDetected > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${scanProgress.financialDetected} financial messages detected",
                                    fontSize = 13.sp,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            OutlinedButton(
                                onClick = {
                                    isCancelled = true
                                    currentPhase = SmsImportUiPhase.SELECT_RANGE
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel Scan")
                            }
                        }
                    }
                }

                SmsImportUiPhase.SCAN_PREVIEW -> {
                    val result = scanResult
                    if (result != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "Scan Complete",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E1E1E)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                ResultRow(Icons.Outlined.Info, "${result.messagesScanned} messages scanned")
                                ResultRow(Icons.Outlined.AccountBalance, "${result.financialMessages} financial messages found", Color(0xFF673AB7))
                                ResultRow(Icons.Filled.CheckCircle, "${result.transactionCandidatesCount} transaction candidates", Color(0xFF4CAF50))
                                ResultRow(Icons.Filled.Add, "${result.newTransactionsCount} new transactions to import", Color(0xFF2E7D32))
                                if (result.duplicatesCount > 0) {
                                    ResultRow(Icons.Filled.ContentCopy, "${result.duplicatesCount} duplicates already in database", Color(0xFF757575))
                                }
                                if (result.correlationsCount > 0) {
                                    ResultRow(Icons.Filled.Refresh, "${result.correlationsCount} matches with existing notifications", Color(0xFF0288D1))
                                }
                                if (result.pendingReviewCount > 0) {
                                    ResultRow(Icons.Filled.Warning, "${result.pendingReviewCount} require review", Color(0xFFF57C00))
                                }

                                val ignoredCount = result.noiseMessages + result.nonFinancialMessages
                                if (ignoredCount > 0) {
                                    ResultRow(Icons.Filled.RemoveCircleOutline, "$ignoredCount non-financial or OTP messages ignored", Color(0xFF9E9E9E))
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = Color(0xFFEEEEEE))
                                Spacer(modifier = Modifier.height(8.dp))

                                if (result.totalDebitAmount > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Total spending detected", fontSize = 14.sp)
                                        Text(
                                            "₹${"%.2f".format(result.totalDebitAmount)}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD32F2F)
                                        )
                                    }
                                }
                                if (result.totalCreditAmount > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Total income detected", fontSize = 14.sp)
                                        Text(
                                            "₹${"%.2f".format(result.totalCreditAmount)}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4CAF50)
                                        )
                                    }
                                }
                            }
                        }

                        // Action Buttons: Import or Change Range
                        Button(
                            onClick = {
                                currentPhase = SmsImportUiPhase.IMPORTING
                                isCancelled = false
                                scope.launch {
                                    val impRes = importManager.importTransactions(result) { prog ->
                                        importProgress = prog
                                        !isCancelled
                                    }
                                    if (isCancelled || impRes is SmsImportResult.Cancelled) {
                                        currentPhase = SmsImportUiPhase.SELECT_RANGE
                                    } else if (impRes is SmsImportResult.Success) {
                                        importResult = impRes
                                        SmsPermissionHelper.setInitialImportCompleted(context, true)
                                        currentPhase = SmsImportUiPhase.IMPORT_COMPLETE
                                    } else if (impRes is SmsImportResult.Failure) {
                                        errorMessage = impRes.message
                                        currentPhase = SmsImportUiPhase.ERROR
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
                        ) {
                            Text("Import Transactions", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }

                        OutlinedButton(
                            onClick = { currentPhase = SmsImportUiPhase.SELECT_RANGE },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Change Date Range")
                        }
                    }
                }

                SmsImportUiPhase.IMPORTING -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Color(0xFF673AB7))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Importing transactions...", style = MaterialTheme.typography.titleMedium, color = Color(0xFF1E1E1E))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "${importProgress.itemsProcessed} / ${importProgress.totalToImport} processed",
                                fontSize = 14.sp,
                                color = Color(0xFF757575)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Imported: ${importProgress.insertedCount} | Duplicates skipped: ${importProgress.duplicatesSkipped}",
                                fontSize = 12.sp,
                                color = Color(0xFF9E9E9E)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            OutlinedButton(
                                onClick = { isCancelled = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel Import")
                            }
                        }
                    }
                }

                SmsImportUiPhase.IMPORT_COMPLETE -> {
                    val result = importResult
                    if (result != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Import Complete",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E1E1E)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                ResultRow(Icons.Filled.CheckCircle, "${result.insertedCount} transactions imported", Color(0xFF4CAF50))
                                if (result.duplicatesSkippedCount > 0) {
                                    ResultRow(Icons.Filled.ContentCopy, "${result.duplicatesSkippedCount} duplicates skipped", Color(0xFF757575))
                                }
                                if (result.enrichedCount > 0) {
                                    ResultRow(Icons.Filled.Refresh, "${result.enrichedCount} existing transactions enriched", Color(0xFF0288D1))
                                }
                                if (result.reviewPendingCount > 0) {
                                    ResultRow(Icons.Filled.Warning, "${result.reviewPendingCount} routed to Pending Expenses for review", Color(0xFFF57C00))
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = Color(0xFFEEEEEE))
                                Spacer(modifier = Modifier.height(8.dp))

                                if (result.totalAmountImported > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Imported spending", fontSize = 14.sp)
                                        Text("₹${"%.2f".format(result.totalAmountImported)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                                    }
                                }
                                if (result.totalIncomeImported > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Imported income", fontSize = 14.sp)
                                        Text("₹${"%.2f".format(result.totalIncomeImported)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
                        ) {
                            Text("Done", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                currentPhase = SmsImportUiPhase.SELECT_RANGE
                                scanResult = null
                                importResult = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Import More")
                        }
                    }
                }

                SmsImportUiPhase.ERROR -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Error", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(errorMessage ?: "An error occurred", color = Color(0xFFB71C1C), fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick = { currentPhase = SmsImportUiPhase.SELECT_RANGE },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
                    ) {
                        Text("Try Again")
                    }
                }
            }
        }
    }
}

@Composable
fun ResultRow(icon: ImageVector, text: String, tint: Color = Color(0xFF757575)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = Color(0xFF1E1E1E))
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

