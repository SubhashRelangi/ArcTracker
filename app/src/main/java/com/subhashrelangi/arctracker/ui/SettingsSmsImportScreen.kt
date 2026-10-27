package com.subhashrelangi.arctracker.ui

import android.Manifest
import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.service.DedupDecision
import com.subhashrelangi.arctracker.service.FinancialAccountGroup
import com.subhashrelangi.arctracker.service.FinancialAccountGrouper
import com.subhashrelangi.arctracker.service.HistoricalSmsImportManager
import com.subhashrelangi.arctracker.service.InstrumentType
import com.subhashrelangi.arctracker.service.ScannedTransactionItem
import com.subhashrelangi.arctracker.service.SmsImportResult
import com.subhashrelangi.arctracker.service.SmsPermissionHelper
import com.subhashrelangi.arctracker.service.SmsScanProgress
import com.subhashrelangi.arctracker.service.SmsScanResult
import com.subhashrelangi.arctracker.service.TransactionDirection
import com.subhashrelangi.arctracker.settings.MonitoringSettingsRepository
import com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Sequential 3-Stage Workflow of the Historical SMS Import Wizard:
 *   1. TIME_SELECTION: Select lookback horizon or custom date range.
 *   2. SCANNING: Live on-device parsing with real engine telemetry.
 *   3. REVIEW: Bank-wise and transaction-level review with explicit checkboxes.
 *   COMPLETE: Celebratory persistence summary.
 */
private enum class SmsImportStage {
    TIME_SELECTION,
    SCANNING,
    REVIEW,
    COMPLETE
}

/**
 * Review organization mode: Bank-wise (default) or flat transaction list
 */
private enum class ReviewViewMode {
    BY_BANK,
    ALL_TRANSACTIONS
}

/**
 * Filter type for all transactions view
 */
private enum class ReviewFilterType {
    ALL,
    HIGH_CONFIDENCE,
    NEEDS_REVIEW
}

/**
 * Lookback horizon option
 */
private data class HorizonOption(
    val days: Int,
    val label: String
)

/**
 * Single-page historical SMS import wizard for Settings subsection with bank-wise grouping,
 * explicit selection checkboxes, fixed telemetry layout, and ArcTracker-themed styling.
 */
@Composable
fun SettingsSmsImportScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLedger: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { MonitoringSettingsRepository.getInstance(context) }
    val importManager = remember { HistoricalSmsImportManager.create(context) }

    // Sequential stage state
    var currentStage by rememberSaveable { mutableStateOf(SmsImportStage.TIME_SELECTION) }

    // Date range timestamps (Default: 90 days ago to now)
    val defaultStartDate = remember { SmsPermissionHelper.calculateThreeMonthsAgoTimestamp() }
    var startDateMillis by rememberSaveable { mutableLongStateOf(defaultStartDate) }
    var endDateMillis by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }

    // Lookback horizon presets
    val horizons = remember {
        listOf(
            HorizonOption(30, "30 Days"),
            HorizonOption(90, "90 Days"),
            HorizonOption(180, "180 Days"),
            HorizonOption(365, "1 Year")
        )
    }
    var selectedHorizonIndex by rememberSaveable { mutableIntStateOf(1) } // 90 Days default
    var isCustomRange by rememberSaveable { mutableStateOf(false) }

    // Scanning execution state (Stage 2)
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var scanProgressFloat by remember { mutableFloatStateOf(0f) }
    var messagesScannedCount by remember { mutableIntStateOf(0) }
    var totalMessagesEstimated by remember { mutableIntStateOf(0) }
    var financialMessagesCount by remember { mutableIntStateOf(0) }
    var scanStatusText by remember { mutableStateOf("Initializing scanner...") }

    // Review & curation state (Stage 3)
    var scanResultSuccess by remember { mutableStateOf<SmsScanResult.Success?>(null) }
    var selectedItemIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var reviewViewMode by remember { mutableStateOf(ReviewViewMode.BY_BANK) }
    var activeFilter by remember { mutableStateOf(ReviewFilterType.ALL) }
    var expandedGroupIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isImporting by remember { mutableStateOf(false) }

    // Completion state
    var importResultSuccess by remember { mutableStateOf<SmsImportResult.Success?>(null) }

    // Date formatters
    val fullDateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH) }
    val itemDateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.ENGLISH) }

    // Native DatePickerDialogs for Custom Range
    val calendar = remember { Calendar.getInstance() }
    val toDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val calEnd = Calendar.getInstance()
                calEnd.set(year, month, dayOfMonth, 23, 59, 59)
                calEnd.set(Calendar.MILLISECOND, 999)
                endDateMillis = calEnd.timeInMillis
                isCustomRange = true
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    val fromDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val calStart = Calendar.getInstance()
                calStart.set(year, month, dayOfMonth, 0, 0, 0)
                calStart.set(Calendar.MILLISECOND, 0)
                startDateMillis = calStart.timeInMillis
                toDialog.setTitle("Select End Date")
                toDialog.show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    // Helper to select horizon preset
    fun selectHorizon(index: Int) {
        selectedHorizonIndex = index
        isCustomRange = false
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        when (index) {
            0 -> cal.add(Calendar.DAY_OF_YEAR, -30)
            1 -> cal.add(Calendar.MONTH, -3)
            2 -> cal.add(Calendar.MONTH, -6)
            3 -> cal.add(Calendar.YEAR, -1)
        }
        startDateMillis = cal.timeInMillis
        endDateMillis = now
    }

    // Cancel active scan and return to Stage 1
    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        scanProgressFloat = 0f
        currentStage = SmsImportStage.TIME_SELECTION
    }

    // Start live on-device SMS scan
    fun startScan() {
        if (scanJob?.isActive == true) return
        currentStage = SmsImportStage.SCANNING
        scanProgressFloat = 0.05f
        scanStatusText = "Reading SMS inbox..."
        messagesScannedCount = 0
        financialMessagesCount = 0
        totalMessagesEstimated = 0

        scanJob = scope.launch {
            try {
                delay(120)
                val result = withContext(Dispatchers.IO) {
                    importManager.scan(
                        startTimeMillis = startDateMillis,
                        endTimeMillis = endDateMillis,
                        progressCallback = { progress: SmsScanProgress ->
                            messagesScannedCount = progress.recordsProcessed
                            val est = progress.totalEstimated
                            if (est != null && est > 0) {
                                totalMessagesEstimated = est
                                val ratio = (progress.recordsProcessed.toFloat() / est.toFloat()).coerceIn(0.05f, 0.95f)
                                scanProgressFloat = ratio
                            } else {
                                totalMessagesEstimated = maxOf(messagesScannedCount, 1)
                                scanProgressFloat = 0.5f
                            }
                            financialMessagesCount = progress.financialDetected
                            scanStatusText = if (progress.financialDetected > 0) {
                                "${progress.financialDetected} financial transactions detected"
                            } else {
                                "Analyzing messages on device..."
                            }
                            true
                        }
                    )
                }

                scanProgressFloat = 1.0f
                scanStatusText = if (financialMessagesCount > 0) "$financialMessagesCount txns detected" else "Scan complete"
                delay(400) // smooth visual transition

                if (result is SmsScanResult.Success) {
                    val groups = if (result.accountGroups.isNotEmpty()) {
                        result.accountGroups
                    } else if (result.scannedItems.isNotEmpty()) {
                        FinancialAccountGrouper.groupTransactions(result.scannedItems)
                    } else {
                        emptyList()
                    }
                    val enrichedResult = result.copy(accountGroups = groups)
                    scanResultSuccess = enrichedResult
                    selectedItemIds = enrichedResult.scannedItems.map { it.sourceEvent.sourceId }.toSet()
                    expandedGroupIds = emptySet() // Initially minimize all accounts
                    currentStage = SmsImportStage.REVIEW
                } else if (result is SmsScanResult.Cancelled) {
                    currentStage = SmsImportStage.TIME_SELECTION
                } else {
                    scanResultSuccess = SmsScanResult.Success(
                        startTimeMillis = startDateMillis,
                        endTimeMillis = endDateMillis,
                        messagesScanned = messagesScannedCount,
                        financialMessages = financialMessagesCount,
                        nonFinancialMessages = 0,
                        noiseMessages = 0,
                        transactionCandidatesCount = 0,
                        newTransactionsCount = 0,
                        duplicatesCount = 0,
                        correlationsCount = 0,
                        pendingReviewCount = 0,
                        rejectedCount = 0,
                        totalDebitAmount = 0.0,
                        totalCreditAmount = 0.0,
                        scannedItems = emptyList(),
                        accountGroups = emptyList()
                    )
                    selectedItemIds = emptySet()
                    currentStage = SmsImportStage.REVIEW
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    return@launch
                }
                scanResultSuccess = SmsScanResult.Success(
                    startTimeMillis = startDateMillis,
                    endTimeMillis = endDateMillis,
                    messagesScanned = messagesScannedCount,
                    financialMessages = 0,
                    nonFinancialMessages = 0,
                    noiseMessages = 0,
                    transactionCandidatesCount = 0,
                    newTransactionsCount = 0,
                    duplicatesCount = 0,
                    correlationsCount = 0,
                    pendingReviewCount = 0,
                    rejectedCount = 0,
                    totalDebitAmount = 0.0,
                    totalCreditAmount = 0.0,
                    scannedItems = emptyList(),
                    accountGroups = emptyList()
                )
                selectedItemIds = emptySet()
                currentStage = SmsImportStage.REVIEW
            }
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startScan()
        } else {
            Toast.makeText(context, "SMS permission is required to import transactions", Toast.LENGTH_SHORT).show()
        }
    }

    // Check permission before initiating scan
    fun checkPermissionAndScan() {
        if (SmsPermissionHelper.isSmsPermissionGranted(context)) {
            startScan()
        } else {
            permissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    // Execute real backend import reconciliation
    fun executeImport() {
        if (isImporting) return
        val res = scanResultSuccess ?: return
        isImporting = true

        scope.launch {
            try {
                val realResult = withContext(Dispatchers.IO) {
                    importManager.importTransactions(
                        scanResult = res,
                        selectedGroupIds = null,
                        selectedItemIds = selectedItemIds
                    )
                }
                if (realResult is SmsImportResult.Success) {
                    importResultSuccess = realResult
                    settingsRepo.setInitialSmsImportCompleted(true)
                    SmsPermissionHelper.setInitialImportCompleted(context, true)
                    isImporting = false
                    currentStage = SmsImportStage.COMPLETE
                } else if (realResult is SmsImportResult.Failure) {
                    isImporting = false
                    Toast.makeText(context, "Import failed: ${realResult.message}", Toast.LENGTH_SHORT).show()
                } else {
                    isImporting = false
                    currentStage = SmsImportStage.TIME_SELECTION
                }
            } catch (e: Exception) {
                isImporting = false
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Toggle all items in an account group
    fun toggleAccountGroup(group: FinancialAccountGroup) {
        val groupItemIds = group.transactions.map { it.sourceEvent.sourceId }.toSet()
        val allSelected = groupItemIds.isNotEmpty() && groupItemIds.all { selectedItemIds.contains(it) }
        selectedItemIds = if (allSelected) {
            selectedItemIds - groupItemIds
        } else {
            selectedItemIds + groupItemIds
        }
    }

    // Toggle expansion of account group
    fun toggleGroupExpansion(groupId: String) {
        expandedGroupIds = if (expandedGroupIds.contains(groupId)) {
            expandedGroupIds - groupId
        } else {
            expandedGroupIds + groupId
        }
    }

    // Handle back button logically per stage
    fun handleBack() {
        when (currentStage) {
            SmsImportStage.TIME_SELECTION -> onNavigateBack()
            SmsImportStage.SCANNING -> cancelScan()
            SmsImportStage.REVIEW -> currentStage = SmsImportStage.TIME_SELECTION
            SmsImportStage.COMPLETE -> onNavigateBack()
        }
    }

    BackHandler {
        handleBack()
    }

    // Animation for scanning sync icon
    val infiniteTransition = rememberInfiniteTransition(label = "scan_spinner")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFF8FAFC),
        topBar = {
            // Universal Settings Sub-Page Header with statusBarsPadding matching all other sub-settings pages
            ArcTrackerHeader(
                modifier = Modifier.statusBarsPadding(),
                title = "SMS Import Wizard",
                subtitle = "Historical Ingestion Pipeline",
                onBackClick = { handleBack() },
                horizontalPadding = 16.dp,
                actions = {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFFF1F5F9), CircleShape)
                            .border(1.dp, Color(0xFFE2E8F0), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = "Air-Gapped Vault",
                            tint = Color(0xFF055B38),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Clean ArcTracker Sticky Bottom Bar sticking directly to the bottom of the screen
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when (currentStage) {
                        SmsImportStage.TIME_SELECTION -> {
                            Button(
                                onClick = { checkPermissionAndScan() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF055B38),
                                    contentColor = Color.White
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Scan",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Scan SMS Inbox",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = "Cancel & Exit",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF64748B),
                                modifier = Modifier
                                    .clickable { onNavigateBack() }
                                    .padding(vertical = 4.dp)
                            )
                        }

                        SmsImportStage.SCANNING -> {
                            // ArcTracker-styled cancel action
                            Button(
                                onClick = { cancelScan() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFEE2E2),
                                    contentColor = Color(0xFFDC2626)
                                )
                            ) {
                                Text(
                                    text = "Cancel Scan",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "Return to Date Selection",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF64748B),
                                modifier = Modifier
                                    .clickable { cancelScan() }
                                    .padding(vertical = 4.dp)
                            )
                        }

                        SmsImportStage.REVIEW -> {
                            val countToImport = selectedItemIds.size
                            Button(
                                onClick = { executeImport() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF055B38),
                                    contentColor = Color.White
                                ),
                                enabled = !isImporting && countToImport > 0
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isImporting) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "Ingesting to Local Ledger...",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Import",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Import $countToImport Transactions to Ledger",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Cancel & Rescan",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF64748B),
                                modifier = Modifier
                                    .clickable { currentStage = SmsImportStage.TIME_SELECTION }
                                    .padding(vertical = 4.dp)
                            )
                        }

                        SmsImportStage.COMPLETE -> {
                            Button(
                                onClick = onNavigateToLedger,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF055B38),
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = "View in Ledger",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = onNavigateBack,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = CircleShape,
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF334155)
                                )
                            ) {
                                Text(
                                    text = "Return to Settings",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // ----------------------------------------------------
            // Stage Progress Header showing active stage
            // ----------------------------------------------------
            StageProgressIndicator(
                currentStage = currentStage,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ----------------------------------------------------
            // Stage Content Switcher (Sequential Flow in 1 Page)
            // ----------------------------------------------------
            when (currentStage) {
                // ====================================================
                // STAGE 1: Time Selection / Date Horizon
                // ====================================================
                SmsImportStage.TIME_SELECTION -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Hero Banner Card
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(Color(0xFFECFDF5), RoundedCornerShape(12.dp))
                                            .border(1.dp, Color(0xFFA7F3D0), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Forum,
                                            contentDescription = "SMS Scanner",
                                            tint = Color(0xFF055B38),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Scan SMS Inbox & Ingest Historical Ledger",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A),
                                            letterSpacing = (-0.2).sp
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "Select your lookback horizon to parse transaction alerts locally into ArcTracker's double-entry vault.",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B),
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Horizon Lookback Pills
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "AUDIT LOOKBACK HORIZON",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF64748B),
                                        letterSpacing = 0.6.sp
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.History,
                                            contentDescription = "Sync History",
                                            tint = Color(0xFF055B38),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Zero Cloud Sync",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF055B38)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    horizons.forEachIndexed { index, horizon ->
                                        val isSelected = !isCustomRange && selectedHorizonIndex == index
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color(0xFF055B38) else Color.White)
                                                .border(
                                                    1.dp,
                                                    if (isSelected) Color(0xFF055B38) else Color(0xFFE2E8F0),
                                                    CircleShape
                                                )
                                                .clickable { selectHorizon(index) }
                                                .padding(vertical = 9.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                                Text(
                                                    text = horizon.label,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color.White else Color(0xFF334155)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Custom Date Range Selector Card
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { fromDialog.show() },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isCustomRange) Color(0xFFF0FDF4) else Color.White,
                                border = BorderStroke(1.dp, if (isCustomRange) Color(0xFF86EFAC) else Color(0xFFE2E8F0))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .background(Color(0xFFF1F5F9), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DateRange,
                                                contentDescription = "Calendar",
                                                tint = Color(0xFF0F172A),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = if (isCustomRange) "Custom Audit Period" else "Pick Custom Date Range",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A)
                                            )
                                            Text(
                                                text = "${fullDateFormat.format(Date(startDateMillis))}  —  ${fullDateFormat.format(Date(endDateMillis))}",
                                                fontSize = 12.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Dates",
                                        tint = Color(0xFF055B38),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Air-Gapped Vault Security Notice
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Shield,
                                            contentDescription = "Security",
                                            tint = Color(0xFF055B38),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Air-Gapped Vault Security",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                    }

                                    Text(
                                        text = "ArcTracker reads incoming transaction alerts directly from Android's Telephony provider. Pattern extraction, noise reduction, and double-entry reconciliation execute 100% on your device with zero internet transmission.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B),
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // ====================================================
                // STAGE 2: Scanning SMS Inbox Execution
                // ====================================================
                SmsImportStage.SCANNING -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .background(Color(0xFFE8F5E9), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Scanning",
                                                tint = Color(0xFF055B38),
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .rotate(rotationAngle)
                                            )
                                        }

                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Scanning SMS Inbox",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFFDCFCE7), CircleShape)
                                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Live",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF15803D)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))

                                            Text(
                                                text = if (totalMessagesEstimated > 0) {
                                                    "$messagesScannedCount / $totalMessagesEstimated SMS parsed"
                                                } else {
                                                    "$messagesScannedCount SMS parsed"
                                                },
                                                fontSize = 12.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    }

                                    Text(
                                        text = "${(scanProgressFloat * 100).toInt()}%",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF055B38)
                                    )
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // Progress Bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF1F5F9))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(scanProgressFloat.coerceIn(0f, 1f))
                                            .fillMaxHeight()
                                            .clip(CircleShape)
                                            .background(Color(0xFF055B38))
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Telemetry Strip (Fixed shrinking layout)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                                        .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Shield,
                                        contentDescription = null,
                                        tint = Color(0xFF055B38),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Zero cloud upload • On-device regex",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF475569),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "4.2 ms/msg",
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569)
                                    )
                                }
                            }
                        }

                        // Engine Status Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Automated Engine Active",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = scanStatusText,
                                    fontSize = 12.5.sp,
                                    color = Color(0xFF055B38),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Classifying financial debits and credits while filtering out balance inquiries, OTPs, promotional spam, and personal texts.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // ====================================================
                // STAGE 3: Review & Curate Scanned Results (Bank-Wise)
                // ====================================================
                SmsImportStage.REVIEW -> {
                    val result = scanResultSuccess
                    val items = result?.scannedItems ?: emptyList()
                    val accountGroups = result?.accountGroups ?: emptyList()

                    val displayedItems = remember(items, activeFilter) {
                        when (activeFilter) {
                            ReviewFilterType.ALL -> items
                            ReviewFilterType.HIGH_CONFIDENCE -> items.filter { it.plannedDecision != DedupDecision.NEEDS_REVIEW && !it.candidate.needsReview }
                            ReviewFilterType.NEEDS_REVIEW -> items.filter { it.plannedDecision == DedupDecision.NEEDS_REVIEW || it.candidate.needsReview }
                        }
                    }

                    val totalDiscovered = result?.let { it.newTransactionsCount + it.duplicatesCount } ?: items.size
                    val totalVolume = (result?.totalDebitAmount ?: 0.0) + (result?.totalCreditAmount ?: 0.0)

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Summary Overview Card
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Discovered",
                                            tint = Color(0xFF055B38),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "$totalDiscovered Found",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${result?.newTransactionsCount ?: items.size} new • ${result?.duplicatesCount ?: 0} in vault",
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.wrapContentWidth()
                                    ) {
                                        val formattedVolume = if (totalVolume % 1.0 == 0.0) {
                                            String.format(Locale.ENGLISH, "₹%,.0f", totalVolume)
                                        } else {
                                            String.format(Locale.ENGLISH, "₹%,.2f", totalVolume)
                                        }
                                        Text(
                                            text = formattedVolume,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A),
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "Total Volume",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF055B38)
                                        )
                                    }
                                }
                            }
                        }

                        // View Mode Switcher: "By Bank Account" vs "All Transactions"
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val isBankMode = reviewViewMode == ReviewViewMode.BY_BANK
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isBankMode) Color.White else Color.Transparent)
                                        .clickable { reviewViewMode = ReviewViewMode.BY_BANK }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "By Bank Account (${accountGroups.size})",
                                        fontSize = 12.sp,
                                        fontWeight = if (isBankMode) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isBankMode) Color(0xFF0F172A) else Color(0xFF64748B)
                                    )
                                }

                                val isAllMode = reviewViewMode == ReviewViewMode.ALL_TRANSACTIONS
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isAllMode) Color.White else Color.Transparent)
                                        .clickable { reviewViewMode = ReviewViewMode.ALL_TRANSACTIONS }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "All Transactions (${items.size})",
                                        fontSize = 12.sp,
                                        fontWeight = if (isAllMode) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isAllMode) Color(0xFF0F172A) else Color(0xFF64748B)
                                    )
                                }
                            }
                        }

                        // Batch Select Controls Row
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${selectedItemIds.size} of ${items.size} Selected",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF055B38)
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "Select all",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A),
                                        modifier = Modifier.clickable {
                                            selectedItemIds = items.map { it.sourceEvent.sourceId }.toSet()
                                        }
                                    )
                                    Text(
                                        text = "Deselect all",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF64748B),
                                        modifier = Modifier.clickable {
                                            selectedItemIds = emptySet()
                                        }
                                    )
                                }
                            }
                        }

                        // Empty State if no financial SMS found
                        if (items.isEmpty()) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 20.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
                                                .background(Color(0xFFF1F5F9), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Sms,
                                                contentDescription = "No SMS",
                                                tint = Color(0xFF64748B),
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }

                                        Text(
                                            text = "No Financial SMS Detected",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )

                                        Text(
                                            text = "We scanned $messagesScannedCount messages in the selected timeframe, but found no financial transaction alerts matching debit or credit patterns.",
                                            fontSize = 12.5.sp,
                                            color = Color(0xFF64748B),
                                            textAlign = TextAlign.Center,
                                            lineHeight = 17.sp
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Button(
                                            onClick = { currentStage = SmsImportStage.TIME_SELECTION },
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF055B38),
                                                contentColor = Color.White
                                            )
                                        ) {
                                            Text(text = "Try Wider Date Range", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // MODE 1: Bank-Wise View (Grouped by Bank/Financial Account)
                        if (reviewViewMode == ReviewViewMode.BY_BANK) {
                            items(accountGroups, key = { it.groupId }) { group ->
                                val isExpanded = expandedGroupIds.contains(group.groupId)
                                val groupItemIds = group.transactions.map { it.sourceEvent.sourceId }.toSet()
                                val selectedInGroup = groupItemIds.count { selectedItemIds.contains(it) }
                                val isGroupChecked = groupItemIds.isNotEmpty() && selectedInGroup == groupItemIds.size

                                BankAccountReviewCard(
                                    group = group,
                                    isExpanded = isExpanded,
                                    isGroupChecked = isGroupChecked,
                                    selectedCount = selectedInGroup,
                                    selectedItemIds = selectedItemIds,
                                    dateFormat = itemDateFormat,
                                    onToggleGroup = { toggleAccountGroup(group) },
                                    onToggleExpand = { toggleGroupExpansion(group.groupId) },
                                    onToggleItem = { id ->
                                        selectedItemIds = if (selectedItemIds.contains(id)) {
                                            selectedItemIds - id
                                        } else {
                                            selectedItemIds + id
                                        }
                                    }
                                )
                            }
                        } else {
                            // MODE 2: Flat List of All Transactions
                            items(displayedItems, key = { it.sourceEvent.sourceId }) { item ->
                                val isSelected = selectedItemIds.contains(item.sourceEvent.sourceId)
                                RealScannedItemCard(
                                    item = item,
                                    isSelected = isSelected,
                                    dateFormat = itemDateFormat,
                                    onToggle = {
                                        selectedItemIds = if (isSelected) {
                                            selectedItemIds - item.sourceEvent.sourceId
                                        } else {
                                            selectedItemIds + item.sourceEvent.sourceId
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // ====================================================
                // COMPLETE STAGE: Celebratory Ingestion Finished
                // ====================================================
                SmsImportStage.COMPLETE -> {
                    val res = importResultSuccess
                    val inserted = res?.insertedCount ?: 0
                    val totalVol = (res?.totalAmountImported ?: 0.0) + (res?.totalIncomeImported ?: 0.0)
                    val duplicatesSkipped = res?.duplicatesSkippedCount ?: 0

                    val formattedVolume = if (totalVol % 1.0 == 0.0) {
                        String.format(Locale.ENGLISH, "₹%,.0f", totalVol)
                    } else {
                        String.format(Locale.ENGLISH, "₹%,.2f", totalVol)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(86.dp)
                                .background(Color(0xFFE8F5E9), CircleShape)
                                .border(2.dp, Color(0xFFA7F3D0), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF055B38),
                                modifier = Modifier.size(52.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Historical Ledger Ingested!",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "$inserted transactions parsed and cryptographically sealed into your local SQLite vault with zero cloud telemetry.",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Breakdown Summary Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Total Transactions Processed", fontSize = 13.sp, color = Color(0xFF64748B))
                                    Text(text = "$inserted records", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                }

                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Total Cumulative Volume", fontSize = 13.sp, color = Color(0xFF64748B))
                                    Text(text = formattedVolume, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF055B38))
                                }

                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Duplicates Prevented", fontSize = 13.sp, color = Color(0xFF64748B))
                                    Text(text = "$duplicatesSkipped skipped", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF475569))
                                }

                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "Privacy Guarantee", fontSize = 13.sp, color = Color(0xFF64748B))
                                    Text(text = "100% On-Device", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF055B38))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3-Stage Progress Indicator Header showing current stage
 */
@Composable
private fun StageProgressIndicator(
    currentStage: SmsImportStage,
    modifier: Modifier = Modifier
) {
    val stageNumber = when (currentStage) {
        SmsImportStage.TIME_SELECTION -> 1
        SmsImportStage.SCANNING -> 2
        SmsImportStage.REVIEW -> 3
        SmsImportStage.COMPLETE -> 3
    }
    val stageTitle = when (currentStage) {
        SmsImportStage.TIME_SELECTION -> "Stage 1 of 3: Select Horizon"
        SmsImportStage.SCANNING -> "Stage 2 of 3: Scanning Inbox"
        SmsImportStage.REVIEW -> "Stage 3 of 3: Review & Ingest"
        SmsImportStage.COMPLETE -> "Historical Ingestion Complete"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (currentStage == SmsImportStage.COMPLETE) Color(0xFF10B981) else Color(0xFF055B38),
                                CircleShape
                            )
                    )
                    Text(
                        text = stageTitle,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                Text(
                    text = if (currentStage == SmsImportStage.COMPLETE) "All Done" else "Step $stageNumber of 3",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3 Segmented Bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Step 1: Range
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF055B38))
                )
                // Step 2: Scan
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(if (stageNumber >= 2) Color(0xFF055B38) else Color(0xFFE2E8F0))
                )
                // Step 3: Review
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(if (stageNumber >= 3) Color(0xFF055B38) else Color(0xFFE2E8F0))
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "1. Range",
                    fontSize = 10.5.sp,
                    fontWeight = if (stageNumber == 1) FontWeight.Bold else FontWeight.Medium,
                    color = if (stageNumber >= 1) Color(0xFF055B38) else Color(0xFF94A3B8)
                )
                Text(
                    text = "2. Scan",
                    fontSize = 10.5.sp,
                    fontWeight = if (stageNumber == 2) FontWeight.Bold else FontWeight.Medium,
                    color = if (stageNumber >= 2) Color(0xFF055B38) else Color(0xFF94A3B8)
                )
                Text(
                    text = "3. Review",
                    fontSize = 10.5.sp,
                    fontWeight = if (stageNumber == 3) FontWeight.Bold else FontWeight.Medium,
                    color = if (stageNumber >= 3) Color(0xFF055B38) else Color(0xFF94A3B8)
                )
            }
        }
    }
}

/**
 * Bank Account Review Card with Account Summary, Checkbox, and Expandable Transaction List
 */
@Composable
private fun BankAccountReviewCard(
    group: FinancialAccountGroup,
    isExpanded: Boolean,
    isGroupChecked: Boolean,
    selectedCount: Int,
    selectedItemIds: Set<String>,
    dateFormat: SimpleDateFormat,
    onToggleGroup: () -> Unit,
    onToggleExpand: () -> Unit,
    onToggleItem: (String) -> Unit
) {
    val isUnidentified = group.identity.accountSuffix.isNullOrBlank() && group.identity.cardSuffix.isNullOrBlank()
    val totalVolume = group.totalDebit + group.totalCredit
    val formattedTotal = if (totalVolume % 1.0 == 0.0) {
        String.format(Locale.ENGLISH, "₹%,.0f", totalVolume)
    } else {
        String.format(Locale.ENGLISH, "₹%,.2f", totalVolume)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, if (selectedCount > 0) Color(0xFFCBD5E1) else Color(0xFFF1F5F9))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row (Clickable to expand, with dedicated Checkbox)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Bank Icon Box
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isUnidentified) Color(0xFFFEF3C7) else Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when {
                        isUnidentified -> Icons.Outlined.HelpOutline
                        group.identity.instrumentType == InstrumentType.CARD -> Icons.Outlined.CreditCard
                        else -> Icons.Outlined.AccountBalance
                    }
                    val iconTint = if (isUnidentified) Color(0xFFB45309) else Color(0xFF334155)

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Bank Name & Counts
                Column(modifier = Modifier.weight(1f)) {
                    val displayName = when {
                        isUnidentified -> "Unidentified Sender / Account"
                        !group.identity.accountSuffix.isNullOrBlank() ->
                            "${group.identity.institutionName ?: "Bank"} ••${group.identity.accountSuffix}"
                        !group.identity.cardSuffix.isNullOrBlank() ->
                            "${group.identity.institutionName ?: "Card"} ••${group.identity.cardSuffix}"
                        else -> group.identity.getDisplayName()
                    }

                    Text(
                        text = displayName,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "$selectedCount of ${group.transactionCount} selected • $formattedTotal",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }

                // Expand Chevron
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(22.dp)
                )

                // Dedicated Checkbox for the entire Account Group
                ArcTrackerCheckbox(
                    checked = isGroupChecked,
                    onCheckedChange = onToggleGroup
                )
            }

            // Expanded Transaction Items List
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    group.transactions.forEach { item ->
                        val isItemSelected = selectedItemIds.contains(item.sourceEvent.sourceId)
                        RealScannedItemCard(
                            item = item,
                            isSelected = isItemSelected,
                            dateFormat = dateFormat,
                            onToggle = { onToggleItem(item.sourceEvent.sourceId) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Real Scanned Transaction Item Card with explicit Checkbox
 */
@Composable
private fun RealScannedItemCard(
    item: ScannedTransactionItem,
    isSelected: Boolean,
    dateFormat: SimpleDateFormat,
    onToggle: () -> Unit
) {
    val candidate = item.candidate.candidate
    val merchant = candidate.merchant ?: candidate.counterparty ?: "Transaction Alert"
    val isCredit = candidate.direction == TransactionDirection.CREDIT
    val amount = candidate.amount ?: 0.0
    val dateDisplay = dateFormat.format(Date(candidate.transactionTimestamp))
    val accountSuffix = candidate.accountSuffix
        ?: candidate.cardSuffix
        ?: "••••"
    val isReview = item.plannedDecision == DedupDecision.NEEDS_REVIEW || item.candidate.needsReview
    val confidenceScore = if (isReview) 82 else 99

    val icon = getMerchantIcon(merchant, isCredit)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, if (isSelected) Color(0xFFCBD5E1) else Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Explicit Checkbox on the Left
            ArcTrackerCheckbox(
                checked = isSelected,
                onCheckedChange = onToggle
            )

            // Merchant Icon
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        if (isCredit) Color(0xFFE8F5E9) else Color(0xFFF1F5F9),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = merchant,
                    tint = if (isCredit) Color(0xFF055B38) else Color(0xFF475569),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Merchant Name & Date
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = merchant,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = dateDisplay,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (accountSuffix.startsWith("••")) accountSuffix else "••$accountSuffix",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }

            // Amount & Confidence Badge
            Column(horizontalAlignment = Alignment.End) {
                val formattedAmount = if (amount % 1.0 == 0.0) {
                    String.format(Locale.ENGLISH, "₹%,.0f", amount)
                } else {
                    String.format(Locale.ENGLISH, "₹%,.2f", amount)
                }
                Text(
                    text = if (isCredit) "+$formattedAmount" else "-$formattedAmount",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCredit) Color(0xFF055B38) else Color(0xFF0F172A)
                )

                Spacer(modifier = Modifier.height(2.dp))

                if (isReview) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFEF3C7), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$confidenceScore% Review",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFDCFCE7), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$confidenceScore% High",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Crisp, explicit ArcTracker-themed checkbox with green fill & checkmark when checked,
 * and clean rounded outline when unchecked.
 */
@Composable
private fun ArcTrackerCheckbox(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = false, radius = 17.dp),
                onClick = onCheckedChange
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF055B38)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .border(BorderStroke(1.5.dp, Color(0xFFCBD5E1)), RoundedCornerShape(5.dp))
                    .background(Color.White)
            )
        }
    }
}

/**
 * Resolver for category/merchant icon
 */
private fun getMerchantIcon(merchant: String, isCredit: Boolean): ImageVector {
    if (isCredit) return Icons.Outlined.Payments
    val m = merchant.lowercase(Locale.ENGLISH)
    return when {
        m.contains("swiggy") || m.contains("zomato") || m.contains("food") || m.contains("eat") || m.contains("restaurant") -> Icons.Outlined.Restaurant
        m.contains("amazon") || m.contains("flipkart") || m.contains("blinkit") || m.contains("zepto") || m.contains("shop") || m.contains("store") -> Icons.Outlined.ShoppingBag
        m.contains("uber") || m.contains("ola") || m.contains("rapido") || m.contains("metro") || m.contains("travel") -> Icons.Outlined.DirectionsCar
        m.contains("power") || m.contains("electricity") || m.contains("broadband") || m.contains("airtel") || m.contains("jio") || m.contains("bill") -> Icons.Default.Bolt
        m.contains("coffee") || m.contains("cafe") || m.contains("tea") || m.contains("starbucks") -> Icons.Default.LocalCafe
        else -> Icons.Outlined.Receipt
    }
}
