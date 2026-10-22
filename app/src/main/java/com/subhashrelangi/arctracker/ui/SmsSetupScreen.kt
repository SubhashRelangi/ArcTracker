package com.subhashrelangi.arctracker.ui

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.subhashrelangi.arctracker.BuildConfig
import com.subhashrelangi.arctracker.R
import com.subhashrelangi.arctracker.service.AccountIdentityExtractor
import com.subhashrelangi.arctracker.service.DedupDecision
import com.subhashrelangi.arctracker.service.EvidenceLevel
import com.subhashrelangi.arctracker.service.FinancialAccountGroup
import com.subhashrelangi.arctracker.service.FinancialAccountGrouper
import com.subhashrelangi.arctracker.service.FinancialAccountIdentity
import com.subhashrelangi.arctracker.service.HistoricalSmsImportManager
import com.subhashrelangi.arctracker.service.IdentityConfidence
import com.subhashrelangi.arctracker.service.InstrumentType
import com.subhashrelangi.arctracker.service.MatchStrategy
import com.subhashrelangi.arctracker.service.ScannedTransactionItem
import com.subhashrelangi.arctracker.service.SmsImportResult
import com.subhashrelangi.arctracker.service.SmsPermissionHelper
import com.subhashrelangi.arctracker.service.SmsScanProgress
import com.subhashrelangi.arctracker.service.SmsScanResult
import com.subhashrelangi.arctracker.service.StructuredTransactionCandidate
import com.subhashrelangi.arctracker.service.TransactionDirection
import com.subhashrelangi.arctracker.service.TransactionSourceEvent
import com.subhashrelangi.arctracker.service.TransactionSourceType
import com.subhashrelangi.arctracker.service.ValidatedTransactionCandidate
import com.subhashrelangi.arctracker.service.ValidationState
import com.subhashrelangi.arctracker.settings.MonitoringSettingsRepository
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

// ==========================================
// Light Theme Design Tokens
// ==========================================
private val BackgroundWhite = Color(0xFFFFFFFF)
private val BrandTextPrimary = Color(0xFF0F172A)
private val BrandTextSecondary = Color(0xFF64748B)
private val VersionBadgeBg = Color(0xFFF1F5F9)
private val VersionBadgeText = Color(0xFF64748B)
private val StatusIndicatorGreen = Color(0xFF10B981)

// Step Pill (Emerald)
private val StepPillBg = Color(0xFFECFDF5)
private val StepPillBorder = Color(0xFFA7F3D0)
private val StepPillText = Color(0xFF059669)

// Card Tokens
private val CardBg = Color(0xFFFFFFFF)
private val CardSurface = Color(0xFFF8FAFC)
private val CardBorder = Color(0xFFE2E8F0)
private val CardBorderUnselected = Color(0xFFE2E8F0)
private val CardBorderSelected = Color(0xFF10B981)
private val CardBgSelected = Color(0xFFF8FAFC)

// Estimated Yield & Notice Card
private val YieldCardBg = Color(0xFFF8FAFC)
private val YieldCardBorder = Color(0xFFE2E8F0)

// Buttons
private val PrimaryBtnBg = Color(0xFF0F172A)
private val PrimaryBtnText = Color(0xFFFFFFFF)
private val BackBtnBg = Color(0xFFF8FAFC)
private val BackBtnBorder = Color(0xFFE2E8F0)

/**
 * Historical SMS Lookback and Permission Step (Step 2 of 2).
 *
 * Implements Ui-Designs/InitialSetUp/SmsImport.png in pure Light Theme.
 * Follows circular button styling for seamless navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsSetupScreen(
    onImportCompleted: () -> Unit,
    onSkipClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    initialStartScanning: Boolean = false,
    initialShowResult: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val settingsRepo = remember {
        MonitoringSettingsRepository.getInstance(context)
    }

    val importManager = remember {
        HistoricalSmsImportManager.create(context)
    }

    val dateFormat = remember {
        SimpleDateFormat("MMM yyyy", Locale.ENGLISH)
    }

    // Status bar styling
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    // Permission state
    var hasSmsPermission by remember {
        mutableStateOf(SmsPermissionHelper.isSmsPermissionGranted(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasSmsPermission = SmsPermissionHelper.isSmsPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Selected period: 0 = 30 days, 1 = 3 months (default/recommended), 2 = 6 months, 3 = Custom range
    var selectedOptionIndex by rememberSaveable { mutableIntStateOf(1) }

    // Date range timestamps
    val defaultThreeMonthsAgo = remember { SmsPermissionHelper.calculateThreeMonthsAgoTimestamp() }
    var startDateMillis by rememberSaveable { mutableLongStateOf(defaultThreeMonthsAgo) }
    var endDateMillis by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }

    // Dedicated Scan Screen execution & animation states (Stage 1 of 3)
    var isScanning by rememberSaveable { mutableStateOf(initialStartScanning) }
    var scanProgressFloat by remember { mutableFloatStateOf(if (initialStartScanning) 0.05f else 0f) }
    var messagesScannedCount by remember { mutableIntStateOf(0) }
    var totalMessagesEstimated by remember { mutableIntStateOf(0) }
    var financialMessagesCount by remember { mutableIntStateOf(0) }
    var scanStatusText by remember { mutableStateOf("Initializing scanner...") }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    // Scan Result & Bank Categorization states (Stage 2 of 3)
    var showScanResult by rememberSaveable { mutableStateOf(initialShowResult) }
    var scanResultSuccess by remember { mutableStateOf<SmsScanResult.Success?>(null) }
    var selectedGroupIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedTransactionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var viewingAccountGroup by remember { mutableStateOf<FinancialAccountGroup?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    // Helper to update date range when option selected
    fun selectOption(index: Int) {
        selectedOptionIndex = index
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        when (index) {
            0 -> {
                cal.timeInMillis = now
                cal.add(Calendar.DAY_OF_YEAR, -30)
                startDateMillis = cal.timeInMillis
                endDateMillis = now
            }
            1 -> {
                cal.timeInMillis = now
                cal.add(Calendar.MONTH, -3)
                startDateMillis = cal.timeInMillis
                endDateMillis = now
            }
            2 -> {
                cal.timeInMillis = now
                cal.add(Calendar.MONTH, -6)
                startDateMillis = cal.timeInMillis
                endDateMillis = now
            }
            // 3: Custom range - retains current custom start and end
        }
    }

    // Helper to run non-destructive SMS scan and transition to Stage 2 of 3
    fun startScan() {
        if (scanJob?.isActive == true) return
        isScanning = true
        showScanResult = false
        scanProgressFloat = 0.05f
        scanStatusText = "Reading SMS inbox..."
        messagesScannedCount = 0
        financialMessagesCount = 0
        totalMessagesEstimated = 0

        scanJob = scope.launch {
            try {
                delay(100)
                if (scanProgressFloat < 0.10f) scanProgressFloat = 0.10f

                val scanResult = withContext(Dispatchers.IO) {
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
                                "${progress.financialDetected} financial transactions found"
                            } else {
                                "Analyzing messages on device..."
                            }
                            try {
                                Thread.sleep(3)
                            } catch (_: InterruptedException) {}
                            true
                        }
                    )
                }

                if (messagesScannedCount == 0 && scanProgressFloat < 0.9f) {
                    scanProgressFloat = 0.5f
                    delay(300)
                }

                scanProgressFloat = 1.0f
                scanStatusText = if (financialMessagesCount > 0) "$financialMessagesCount txns detected" else "Scan complete"
                delay(600)

                val resultSuccess = if (scanResult is SmsScanResult.Success) {
                    val groups = if (scanResult.accountGroups.isNotEmpty()) {
                        scanResult.accountGroups
                    } else if (scanResult.scannedItems.isNotEmpty()) {
                        FinancialAccountGrouper.groupTransactions(scanResult.scannedItems)
                    } else {
                        emptyList()
                    }
                    scanResult.copy(
                        accountGroups = groups,
                        newTransactionsCount = scanResult.newTransactionsCount,
                        duplicatesCount = scanResult.duplicatesCount
                    )
                } else if (scanResult is SmsScanResult.Cancelled) {
                    isScanning = false
                    return@launch
                } else {
                    SmsScanResult.Success(
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
                }

                scanResultSuccess = resultSuccess
                val allItemIds = resultSuccess.scannedItems.map { it.sourceEvent.sourceId }.toSet()
                selectedTransactionIds = allItemIds
                selectedGroupIds = resultSuccess.accountGroups.map { it.groupId }.toSet()
                isScanning = false
                showScanResult = true
            } catch (e: Exception) {
                if (e is CancellationException) {
                    return@launch
                }
                val fallback = SmsScanResult.Success(
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
                scanResultSuccess = fallback
                selectedTransactionIds = emptySet()
                selectedGroupIds = emptySet()
                isScanning = false
                showScanResult = true
            }
        }
    }

    val onCancelScan: () -> Unit = {
        scanJob?.cancel()
        scanJob = null
        isScanning = false
        scanProgressFloat = 0f
    }

    fun importSelectedTransactions() {
        if (isImporting) return
        isImporting = true
        scope.launch {
            try {
                val res = scanResultSuccess
                if (res != null && res.scannedItems.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        importManager.importTransactions(
                            scanResult = res,
                            selectedGroupIds = selectedGroupIds,
                            selectedItemIds = selectedTransactionIds
                        )
                    }
                }
            } catch (_: Exception) {}
            settingsRepo.setSetupInProgress(false)
            settingsRepo.setInitialOnboardingCompleted(true)
            SmsPermissionHelper.setInitialImportCompleted(context, true)
            onImportCompleted()
        }
    }

    LaunchedEffect(initialStartScanning) {
        if (initialStartScanning && scanJob?.isActive != true && !showScanResult) {
            startScan()
        }
    }

    LaunchedEffect(initialShowResult) {
        if (initialShowResult && scanResultSuccess == null && scanJob?.isActive != true) {
            startScan()
        }
    }

    // Permission launcher for SMS
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.READ_SMS] == true ||
                permissions[Manifest.permission.RECEIVE_SMS] == true
        hasSmsPermission = granted
        if (granted) {
            SmsPermissionHelper.setInitialImportCompleted(context, true)
            // Do not scan automatically: user selects options and manually clicks "Scan" or "Continue without scan"
        }
    }

    // Date picker helper for custom range: asks both From date and To date sequentially
    fun showCustomDateRangePicker() {
        val startCal = Calendar.getInstance().apply { timeInMillis = startDateMillis }
        val fromDialog = DatePickerDialog(
            context,
            { _, fromYear, fromMonth, fromDay ->
                val pickedStartCal = Calendar.getInstance().apply {
                    set(fromYear, fromMonth, fromDay, 0, 0, 0)
                }
                val newStart = pickedStartCal.timeInMillis

                // Immediately open picker for "To (End Date)"
                val endCal = Calendar.getInstance().apply { timeInMillis = maxOf(newStart, endDateMillis) }
                val toDialog = DatePickerDialog(
                    context,
                    { _, toYear, toMonth, toDay ->
                        val pickedEndCal = Calendar.getInstance().apply {
                            set(toYear, toMonth, toDay, 23, 59, 59)
                        }
                        val newEnd = maxOf(newStart, pickedEndCal.timeInMillis)
                        startDateMillis = newStart
                        endDateMillis = newEnd
                        selectedOptionIndex = 3
                    },
                    endCal.get(Calendar.YEAR),
                    endCal.get(Calendar.MONTH),
                    endCal.get(Calendar.DAY_OF_MONTH)
                )
                toDialog.setTitle("Select To (End Date)")
                toDialog.show()
            },
            startCal.get(Calendar.YEAR),
            startCal.get(Calendar.MONTH),
            startCal.get(Calendar.DAY_OF_MONTH)
        )
        fromDialog.setTitle("Select From (Start Date)")
        fromDialog.show()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundWhite)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // ----------------------------------------------------
            // 1. App Header (Matches InitialPageScreen exactly)
            // ----------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Title starts first, followed by GitHub version pill
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ArcTracker",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandTextPrimary,
                        letterSpacing = (-0.4).sp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Version Badge Pill (GitHub downloaded version)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(VersionBadgeBg)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = BuildConfig.GITHUB_VERSION.ifEmpty { "v${BuildConfig.VERSION_NAME}" },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VersionBadgeText
                        )
                    }
                }

                // Right: Status Indicator Green Dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(StatusIndicatorGreen)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isScanning) {
                SmsScanningContent(
                    scanProgressFloat = scanProgressFloat,
                    messagesScannedCount = messagesScannedCount,
                    totalMessagesEstimated = totalMessagesEstimated,
                    financialMessagesCount = financialMessagesCount,
                    scanStatusText = scanStatusText,
                    onCancelScan = onCancelScan
                )
            } else if (showScanResult && scanResultSuccess != null) {
                SmsScanResultContent(
                    scanResult = scanResultSuccess!!,
                    selectedGroupIds = selectedGroupIds,
                    selectedTransactionIds = selectedTransactionIds,
                    onToggleGroup = { groupId ->
                        val group = scanResultSuccess?.accountGroups?.find { it.groupId == groupId }
                        val groupItemIds = group?.transactions?.map { it.sourceEvent.sourceId }?.toSet() ?: emptySet()
                        val isCurrentlySelected = selectedGroupIds.contains(groupId) ||
                                (groupItemIds.isNotEmpty() && groupItemIds.any { selectedTransactionIds.contains(it) })

                        if (isCurrentlySelected) {
                            selectedGroupIds = selectedGroupIds - groupId
                            selectedTransactionIds = selectedTransactionIds - groupItemIds
                        } else {
                            selectedGroupIds = selectedGroupIds + groupId
                            selectedTransactionIds = selectedTransactionIds + groupItemIds
                        }
                    },
                    onViewAccountTransactions = { group ->
                        viewingAccountGroup = group
                    },
                    onImportSelected = {
                        importSelectedTransactions()
                    },
                    onChangeDateRange = {
                        showScanResult = false
                        isScanning = false
                    }
                )
            } else {
                // ----------------------------------------------------
                // 2. Stepper & Headline
                // ----------------------------------------------------
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(StepPillBg)
                    .border(BorderStroke(1.dp, StepPillBorder), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(StepPillText)
                    )
                    Text(
                        text = "STEP 2 OF 2",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = StepPillText
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Choose lookback history",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 28.sp,
                letterSpacing = (-0.4).sp,
                color = BrandTextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Scan supported bank SMS messages to build your initial transaction history.",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 19.sp,
                color = BrandTextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

        // ----------------------------------------------------
        // 3. Scrollable Essential Options & Cards
        // ----------------------------------------------------
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Option 0: Last 30 days
            SmsLookbackOptionCard(
                title = "Last 30 days",
                subtitle = "Recent monthly transactions",
                isSelected = selectedOptionIndex == 0,
                badge = null,
                trailingType = OptionTrailingType.RADIO,
                onClick = { selectOption(0) }
            )

            // Option 1: Last 3 months (RECOMMENDED)
            SmsLookbackOptionCard(
                title = "Last 3 months",
                subtitle = "Quarterly ledger setup",
                isSelected = selectedOptionIndex == 1,
                badge = "RECOMMENDED",
                trailingType = OptionTrailingType.RADIO,
                onClick = { selectOption(1) }
            )

            // Option 2: Last 6 months
            SmsLookbackOptionCard(
                title = "Last 6 months",
                subtitle = "Half-year spending history",
                isSelected = selectedOptionIndex == 2,
                badge = null,
                trailingType = OptionTrailingType.RADIO,
                onClick = { selectOption(2) }
            )

            // Option 3: Custom range
            val customRangeSubtitle = if (selectedOptionIndex == 3) {
                "${dateFormat.format(Date(startDateMillis))} – ${dateFormat.format(Date(endDateMillis))}"
            } else {
                "Select custom range"
            }
            SmsLookbackOptionCard(
                title = "Custom range",
                subtitle = customRangeSubtitle,
                isSelected = selectedOptionIndex == 3,
                badge = null,
                trailingType = OptionTrailingType.CALENDAR,
                onClick = {
                    showCustomDateRangePicker()
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ----------------------------------------------------
            // 4. Privacy Assurance Note
            // ----------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = BrandTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Historical scanning is optional. Only financial alerts are parsed; personal chats are never read.",
                    fontSize = 12.sp,
                    color = BrandTextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ----------------------------------------------------
        // 5. Bottom Navigation Actions (Matching LocalFirstSetupScreen exactly)
        // ----------------------------------------------------
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Row: Circular Back Button (Left) + Action Button (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Circular Back Button with Left Arrow
                Surface(
                    modifier = Modifier
                        .size(52.dp)
                        .shadow(
                            elevation = 1.dp,
                            shape = CircleShape,
                            ambientColor = Color(0x08000000),
                            spotColor = Color(0x10000000)
                        )
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(color = Color.Black.copy(alpha = 0.1f)),
                            onClick = onBackClick
                        ),
                    shape = CircleShape,
                    color = BackBtnBg,
                    border = BorderStroke(1.dp, BackBtnBorder)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Previous Screen",
                            tint = BrandTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Right: Primary Next/Enable/Scan Action Button
                val buttonText = when {
                    isScanning -> "Scanning..."
                    !hasSmsPermission -> "Enable Access"
                    else -> "Scan"
                }

                Surface(
                    modifier = Modifier
                        .height(52.dp)
                        .shadow(
                            elevation = 2.dp,
                            shape = RoundedCornerShape(26.dp),
                            ambientColor = Color(0x10000000),
                            spotColor = Color(0x1A000000)
                        )
                        .clip(RoundedCornerShape(26.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(color = Color.White.copy(alpha = 0.2f)),
                            enabled = !isScanning,
                            onClick = {
                                if (!hasSmsPermission) {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_SMS,
                                            Manifest.permission.RECEIVE_SMS
                                        )
                                    )
                                } else {
                                    startScan()
                                }
                            }
                        ),
                    shape = RoundedCornerShape(26.dp),
                    color = PrimaryBtnBg
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .fillMaxHeight(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = buttonText,
                            color = PrimaryBtnText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = PrimaryBtnText,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Context-based Secondary Action: "Continue without access" or "Continue without scan"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(color = Color.Black.copy(alpha = 0.06f)),
                        onClick = {
                            settingsRepo.setSetupInProgress(false)
                            settingsRepo.setInitialOnboardingCompleted(true)
                            SmsPermissionHelper.setInitialImportCompleted(context, true)
                            onSkipClick()
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (!hasSmsPermission) "Continue without access" else "Continue without scan",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = BrandTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
    if (viewingAccountGroup != null) {
        val group = viewingAccountGroup!!
        AccountTransactionsBottomSheet(
            accountGroup = group,
            selectedTransactionIds = selectedTransactionIds,
            onToggleTransaction = { sourceId ->
                selectedTransactionIds = if (selectedTransactionIds.contains(sourceId)) {
                    selectedTransactionIds - sourceId
                } else {
                    selectedTransactionIds + sourceId
                }
                val hasAnyInGroup = group.transactions.any { selectedTransactionIds.contains(it.sourceEvent.sourceId) }
                selectedGroupIds = if (hasAnyInGroup) {
                    selectedGroupIds + group.groupId
                } else {
                    selectedGroupIds - group.groupId
                }
            },
            onToggleAllInGroup = {
                val allInGroup = group.transactions.map { it.sourceEvent.sourceId }.toSet()
                val areAllSelected = allInGroup.all { selectedTransactionIds.contains(it) }
                if (areAllSelected) {
                    selectedTransactionIds = selectedTransactionIds - allInGroup
                    selectedGroupIds = selectedGroupIds - group.groupId
                } else {
                    selectedTransactionIds = selectedTransactionIds + allInGroup
                    selectedGroupIds = selectedGroupIds + group.groupId
                }
            },
            onDismiss = { viewingAccountGroup = null }
        )
    }
}
}

/**
 * Dedicated Scanning Screen matching Ui-Designs/InitialSetUp/SmsImportScan.png in Light Theme.
 *
 * Stage 1 of 3: SMS Scan Phase.
 * - Same Header as previous screens (Title + GitHub version badge pill + Green status dot)
 * - Stage Pill: "AUTOMATED INGESTION • STAGE 1 OF 3" (without icon beside it per instructions)
 * - Perfectly scaled Circular Gauge with dynamic percentage & pulsing "AUDITING" badge
 * - Essential Real-time Scanning Reports Card (Progress bar, Messages checked, Status)
 * - Local isolated sandbox privacy card
 * - Red pill-styled "Cancel Scan" button without bottom text
 */
@Composable
private fun SmsScanningContent(
    scanProgressFloat: Float,
    messagesScannedCount: Int,
    totalMessagesEstimated: Int,
    financialMessagesCount: Int,
    scanStatusText: String,
    onCancelScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = scanProgressFloat,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "scanProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val displayPercent = (animatedProgress * 100).toInt().coerceIn(0, 100)

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // ----------------------------------------------------
        // Stage Pill (Removed icon beside AUTOMATED INGESTION per instruction)
        // ----------------------------------------------------
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(StepPillBg)
                .border(BorderStroke(1.dp, StepPillBorder), CircleShape)
                .padding(horizontal = 11.dp, vertical = 3.5.dp)
        ) {
            Text(
                text = "AUTOMATED INGESTION  •  STAGE 1 OF 3",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = StepPillText,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ----------------------------------------------------
        // Central Animated Circular Gauge (Proportionately scaled)
        // ----------------------------------------------------
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(128.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 6.5.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                    val arcSize = Size(diameter, diameter)

                    // Background Track
                    drawArc(
                        color = Color(0xFFF1F5F9),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Foreground Animated Emerald Arc
                    drawArc(
                        color = Color(0xFF10B981),
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$displayPercent%",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandTextPrimary,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Auditing pill with pulsing indicator dot
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(StepPillBg)
                            .border(BorderStroke(1.dp, StepPillBorder), CircleShape)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(StepPillText.copy(alpha = pulseAlpha))
                            )
                            Text(
                                text = if (financialMessagesCount > 0) "$financialMessagesCount FOUND" else "AUDITING",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = StepPillText,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ----------------------------------------------------
        // Headline & Subtitle (Proportional sizing)
        // ----------------------------------------------------
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Scanning messages on this device...",
                fontSize = 18.5.sp,
                fontWeight = FontWeight.Bold,
                color = BrandTextPrimary,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                letterSpacing = (-0.3).sp
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = "Reading SMS inbox and matching verified bank alert signatures.",
                fontSize = 12.5.sp,
                color = BrandTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 17.5.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ----------------------------------------------------
        // Real-Time Scanning Reports Card
        // ----------------------------------------------------
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Progress",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = BrandTextSecondary
                    )
                    Text(
                        text = "$displayPercent%",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandTextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.5.dp)
                        .clip(RoundedCornerShape(3.5.dp)),
                    color = Color(0xFF10B981),
                    trackColor = Color(0xFFE2E8F0)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Checked",
                            fontSize = 11.sp,
                            color = BrandTextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (totalMessagesEstimated > 0) "$messagesScannedCount / $totalMessagesEstimated" else "$messagesScannedCount",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandTextPrimary
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Remaining",
                            fontSize = 11.sp,
                            color = BrandTextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val remaining = if (totalMessagesEstimated > 0) {
                            (totalMessagesEstimated - messagesScannedCount).coerceAtLeast(0)
                        } else 0
                        Text(
                            text = "$remaining",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandTextPrimary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Transactions",
                            fontSize = 11.sp,
                            color = BrandTextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$financialMessagesCount found",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StepPillText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Status",
                        fontSize = 11.sp,
                        color = BrandTextSecondary
                    )
                    Text(
                        text = scanStatusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = StepPillText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ----------------------------------------------------
        // Privacy Sandbox Guarantee
        // ----------------------------------------------------
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "Isolated Sandbox",
                    tint = StepPillText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Parsing occurs locally in an isolated sandbox. Zero data transmission.",
                    fontSize = 11.5.sp,
                    color = BrandTextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ----------------------------------------------------
        // Bottom Action: Soft Rose/Crimson Cancel Scan Button
        // ----------------------------------------------------
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .shadow(
                    elevation = 1.dp,
                    shape = RoundedCornerShape(26.dp),
                    ambientColor = Color(0x0ADC2626),
                    spotColor = Color(0x18DC2626)
                )
                .clip(RoundedCornerShape(26.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(color = Color(0xFFDC2626).copy(alpha = 0.12f)),
                    onClick = onCancelScan
                ),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFFFEF2F2),
            border = BorderStroke(1.dp, Color(0xFFFECACA))
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel Scan",
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cancel Scan",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFDC2626)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

private enum class OptionTrailingType {
    RADIO,
    CALENDAR
}

@Composable
private fun SmsLookbackOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    badge: String?,
    trailingType: OptionTrailingType,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) CardBorderSelected else CardBorderUnselected
    val bgColor = if (isSelected) CardBgSelected else CardBg

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = Color.Black.copy(alpha = 0.04f)),
                onClick = onClick
            ),
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandTextPrimary
                    )

                    if (badge != null) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(StepPillBg)
                                .border(BorderStroke(1.dp, StepPillBorder), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badge,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = StepPillText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    color = BrandTextSecondary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            when (trailingType) {
                OptionTrailingType.RADIO -> {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(CardBorderSelected),
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
                                .size(22.dp)
                                .clip(CircleShape)
                                .border(BorderStroke(1.5.dp, Color(0xFFCBD5E1)), CircleShape)
                        )
                    }
                }
                OptionTrailingType.CALENDAR -> {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = "Custom Date Range",
                        tint = if (isSelected) CardBorderSelected else BrandTextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Scan Result Screen matching Ui-Designs/InitialSetUp/SmsImportScanResult.png in Light Theme.
 *
 * Stage 2 of 3: Scan Result & Account Categorization Phase.
 * - Header matches InitialPageScreen & LocalFirstSetupScreen (ArcTracker + version badge pill + Green status dot)
 * - Stage Pill: "STAGE 2 OF 3" + "Sync Ready" indicator
 * - Overview Card: Total new transactions found, duplicates skipped, detected financial accounts
 * - Detected Accounts Section: Categorized by bank accounts and cards with selection checkboxes
 * - Interactive Bank Cards: Tapping an account opens the scanned transactions bottom sheet
 * - Info Card: Review queue advisory for unidentified senders
 * - Primary Action: "Import Selected Transactions (X) →" (52.dp pill shape)
 * - Secondary Action: "Change Date Range"
 */
@Composable
private fun SmsScanResultContent(
    scanResult: SmsScanResult.Success,
    selectedGroupIds: Set<String>,
    selectedTransactionIds: Set<String>,
    onToggleGroup: (String) -> Unit,
    onViewAccountTransactions: (FinancialAccountGroup) -> Unit,
    onImportSelected: () -> Unit,
    onChangeDateRange: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalSelectedTransactions = if (scanResult.scannedItems.isNotEmpty()) {
        scanResult.scannedItems.count { selectedTransactionIds.contains(it.sourceEvent.sourceId) }
    } else {
        scanResult.accountGroups.filter { selectedGroupIds.contains(it.groupId) }.sumOf { it.transactionCount }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ----------------------------------------------------
        // Stage Indicator & Sync Ready Badge
        // ----------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: STAGE 2 OF 3 pill
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(StepPillBg)
                    .border(BorderStroke(1.dp, StepPillBorder), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(StepPillText)
                    )
                    Text(
                        text = "STAGE 2 OF 3",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = StepPillText
                    )
                }
            }

            // Right: Sync Ready Pill
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFFECFDF5))
                    .border(BorderStroke(1.dp, Color(0xFFA7F3D0)), CircleShape)
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Text(
                        text = "Sync Ready",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF047857)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ----------------------------------------------------
        // Scrollable Central Body
        // ----------------------------------------------------
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Overview Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardBg,
                border = BorderStroke(1.dp, CardBorderUnselected)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    val totalFound = if (scanResult.scannedItems.isNotEmpty()) scanResult.scannedItems.size else scanResult.newTransactionsCount + scanResult.duplicatesCount
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$totalFound",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandTextPrimary,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "transactions found",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandTextPrimary,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${scanResult.newTransactionsCount} new • ${scanResult.duplicatesCount} already imported",
                        fontSize = 13.5.sp,
                        color = BrandTextSecondary
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "From ${scanResult.accountGroups.size} detected financial accounts",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF059669)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section Header: DETECTED ACCOUNTS TO IMPORT
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DETECTED ACCOUNTS TO IMPORT",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "${selectedGroupIds.size} Selected",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF059669)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Detected Accounts Cards
            scanResult.accountGroups.forEach { group ->
                val selectedCountInGroup = if (group.transactions.isNotEmpty()) {
                    group.transactions.count { selectedTransactionIds.contains(it.sourceEvent.sourceId) }
                } else if (selectedGroupIds.contains(group.groupId)) {
                    group.transactionCount
                } else 0

                val isSelected = selectedCountInGroup > 0 || selectedGroupIds.contains(group.groupId)
                val isUnidentified = group.groupId == AccountIdentityExtractor.UNIDENTIFIED_GROUP_ID ||
                        group.groupId.contains("unidentified", ignoreCase = true) ||
                        (group.identity.accountSuffix.isNullOrBlank() && group.identity.cardSuffix.isNullOrBlank())

                AccountGroupCard(
                    group = group,
                    isSelected = isSelected,
                    selectedCountInGroup = selectedCountInGroup,
                    isUnidentified = isUnidentified,
                    onToggle = { onToggleGroup(group.groupId) },
                    onClick = { onViewAccountTransactions(group) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Informational Notice for Review Queue
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF1F5F9),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier
                            .size(18.dp)
                            .padding(top = 1.dp)
                    )
                    Text(
                        text = "Unidentified transactions will be placed into your Review queue for deliberate confirmation.",
                        fontSize = 12.5.sp,
                        color = Color(0xFF475569),
                        lineHeight = 17.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ----------------------------------------------------
        // Fixed Bottom Actions (Circle/Pill Format Matching Onboarding)
        // ----------------------------------------------------
        Spacer(modifier = Modifier.height(8.dp))

        // Primary Action: Import Selected Transactions (X) ->
        val canImport = totalSelectedTransactions > 0
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .shadow(
                    elevation = if (canImport) 2.dp else 0.dp,
                    shape = RoundedCornerShape(26.dp),
                    ambientColor = Color(0x10000000),
                    spotColor = Color(0x1A000000)
                )
                .clip(RoundedCornerShape(26.dp))
                .clickable(
                    enabled = canImport,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(color = Color.White.copy(alpha = 0.2f)),
                    onClick = onImportSelected
                ),
            shape = RoundedCornerShape(26.dp),
            color = if (canImport) PrimaryBtnBg else Color(0xFF94A3B8)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (canImport) {
                        "Import Selected Transactions ($totalSelectedTransactions)"
                    } else {
                        "Select at least 1 transaction"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBtnText
                )

                if (canImport) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Proceed to Import",
                        tint = PrimaryBtnText,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Secondary Action: Change Date Range
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(color = Color.Black.copy(alpha = 0.06f)),
                    onClick = onChangeDateRange
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Change Date Range",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = BrandTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

/**
 * Account Group item card matching the mockup.
 * Entire card is clickable to view transactions, while checkbox toggles inclusion.
 */
@Composable
private fun AccountGroupCard(
    group: FinancialAccountGroup,
    isSelected: Boolean,
    selectedCountInGroup: Int,
    isUnidentified: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFFCBD5E1) else Color(0xFFE2E8F0)
    val bgColor = if (isSelected) Color(0xFFFFFFFF) else Color(0xFFF8FAFC)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = Color.Black.copy(alpha = 0.04f)),
                onClick = onClick
            ),
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading Icon Box
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isUnidentified) Color(0xFFFEF3C7) else Color(0xFFF1F5F9)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val icon = when {
                    isUnidentified -> Icons.Outlined.WarningAmber
                    group.identity.instrumentType == InstrumentType.CARD -> Icons.Outlined.CreditCard
                    else -> Icons.Outlined.AccountBalance
                }
                val iconTint = if (isUnidentified) Color(0xFFD97706) else Color(0xFF334155)

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Account Name & Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val displayName = when {
                        isUnidentified -> "Unidentified Sender / Suffix"
                        !group.identity.accountSuffix.isNullOrBlank() ->
                            "${group.identity.institutionName ?: "Bank"} ••${group.identity.accountSuffix}"
                        !group.identity.cardSuffix.isNullOrBlank() ->
                            "${group.identity.institutionName ?: "Card"} ••${group.identity.cardSuffix}"
                        else -> group.identity.getDisplayName()
                    }

                    Text(
                        text = displayName,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (isUnidentified) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFEF3C7))
                                .border(BorderStroke(0.8.dp, Color(0xFFFDE68A)), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Review",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                val subtitle = when {
                    isUnidentified -> {
                        if (group.transactionCount == selectedCountInGroup || selectedCountInGroup == 0) {
                            "${group.transactionCount} transactions require review"
                        } else {
                            "$selectedCountInGroup of ${group.transactionCount} require review"
                        }
                    }
                    group.identity.instrumentType == InstrumentType.CARD -> {
                        if (group.transactionCount == selectedCountInGroup || selectedCountInGroup == 0) {
                            "Credit Card • ${group.transactionCount} transactions"
                        } else {
                            "Credit Card • $selectedCountInGroup of ${group.transactionCount} selected"
                        }
                    }
                    else -> {
                        if (group.transactionCount == selectedCountInGroup || selectedCountInGroup == 0) {
                            "Savings Account • ${group.transactionCount} transactions"
                        } else {
                            "Savings Account • $selectedCountInGroup of ${group.transactionCount} selected"
                        }
                    }
                }

                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = BrandTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Selection Checkbox (Dedicated Click target)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(color = Color.Black.copy(alpha = 0.1f)),
                        onClick = onToggle
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(CardBorderSelected),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(BorderStroke(1.5.dp, Color(0xFFCBD5E1)), RoundedCornerShape(6.dp))
                    )
                }
            }
        }
    }
}

/**
 * Bottom sheet displaying the transactions scanned for a specific bank account or instrument.
 * Users can individually select/deselect each transaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountTransactionsBottomSheet(
    accountGroup: FinancialAccountGroup,
    selectedTransactionIds: Set<String>,
    onToggleTransaction: (String) -> Unit,
    onToggleAllInGroup: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    val selectedCount = accountGroup.transactions.count { selectedTransactionIds.contains(it.sourceEvent.sourceId) }
    val allSelected = selectedCount == accountGroup.transactions.size && accountGroup.transactions.isNotEmpty()

    val selectedItems = accountGroup.transactions.filter { selectedTransactionIds.contains(it.sourceEvent.sourceId) }
    val totalDebit = selectedItems
        .filter { it.candidate.candidate.direction == TransactionDirection.DEBIT }
        .sumOf { it.candidate.candidate.amount ?: 0.0 }
    val totalCredit = selectedItems
        .filter { it.candidate.candidate.direction == TransactionDirection.CREDIT }
        .sumOf { it.candidate.candidate.amount ?: 0.0 }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header: Account name & Dismiss X
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = accountGroup.identity.getDisplayName(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$selectedCount of ${accountGroup.transactions.size} transactions selected",
                        fontSize = 12.5.sp,
                        color = if (selectedCount > 0) Color(0xFF059669) else BrandTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF1F5F9))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = BrandTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Summary Totals Row (Calculated for selected items)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF8FAFC))
                    .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Selected Debits",
                        fontSize = 11.5.sp,
                        color = BrandTextSecondary
                    )
                    Text(
                        text = "₹${String.format(Locale.getDefault(), "%,.2f", totalDebit)}",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626)
                    )
                }

                if (totalCredit > 0 || accountGroup.totalCredit > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Selected Credits",
                            fontSize = 11.5.sp,
                            color = BrandTextSecondary
                        )
                        Text(
                            text = "₹${String.format(Locale.getDefault(), "%,.2f", totalCredit)}",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Subheader with Select All / Deselect All toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SCANNED TRANSACTIONS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = if (allSelected) "Deselect All" else "Select All",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (allSelected) Color(0xFFDC2626) else Color(0xFF059669),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onToggleAllInGroup)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable list of scanned transactions with checkboxes
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(accountGroup.transactions) { item ->
                    val candidate = item.candidate.candidate
                    val isCredit = candidate.direction == TransactionDirection.CREDIT
                    val amount = candidate.amount ?: 0.0
                    val merchant = candidate.merchant ?: candidate.counterparty ?: "Transaction"
                    val timestamp = candidate.transactionTimestamp
                    val isItemChecked = selectedTransactionIds.contains(item.sourceEvent.sourceId)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onToggleTransaction(item.sourceEvent.sourceId)
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isItemChecked) Color(0xFFFFFFFF) else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, if (isItemChecked) Color(0xFFCBD5E1) else Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = merchant,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = dateFormat.format(Date(timestamp)),
                                        fontSize = 11.5.sp,
                                        color = BrandTextSecondary
                                    )
                                    if (item.plannedDecision == DedupDecision.DUPLICATE) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFFF1F5F9))
                                                .border(BorderStroke(0.8.dp, Color(0xFFCBD5E1)), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "Imported",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    }
                                }

                                if (item.sourceEvent.rawText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.sourceEvent.rawText,
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF64748B),
                                        lineHeight = 14.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isCredit) "+₹${String.format(Locale.getDefault(), "%,.2f", amount)}"
                                    else "-₹${String.format(Locale.getDefault(), "%,.2f", amount)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCredit) Color(0xFF059669) else BrandTextPrimary
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Item selection checkbox
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            if (isItemChecked) CardBorderSelected else Color.Transparent
                                        )
                                        .border(
                                            BorderStroke(
                                                1.5.dp,
                                                if (isItemChecked) CardBorderSelected else Color(0xFFCBD5E1)
                                            ),
                                            RoundedCornerShape(5.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isItemChecked) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Done Button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = onDismiss),
                shape = RoundedCornerShape(24.dp),
                color = PrimaryBtnBg
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Done ($selectedCount selected)",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryBtnText
                    )
                }
            }
        }
    }
}

/**
 * Creates high-fidelity sample account groups matching the mockup for zero-state or demo evaluation.
 */
private fun createSampleItem(
    id: String,
    sender: String,
    rawText: String,
    merchant: String,
    amount: Double,
    direction: TransactionDirection,
    timeOffsetHours: Long,
    accountIdentity: FinancialAccountIdentity,
    isReview: Boolean = false
): ScannedTransactionItem {
    val timestamp = System.currentTimeMillis() - (timeOffsetHours * 3600_000L)
    val structured = StructuredTransactionCandidate(
        sourceNotificationKey = "sms_$id",
        packageName = "com.google.android.apps.messaging",
        postTime = timestamp,
        transactionTimestamp = timestamp,
        amount = amount,
        currency = "INR",
        merchant = merchant,
        counterparty = merchant,
        direction = direction,
        accountSuffix = accountIdentity.accountSuffix,
        cardSuffix = accountIdentity.cardSuffix
    )
    val validated = ValidatedTransactionCandidate(
        candidate = structured,
        validationState = if (isReview) ValidationState.NEEDS_REVIEW else ValidationState.ACCEPTABLE,
        evidenceLevel = if (isReview) EvidenceLevel.MODERATE else EvidenceLevel.VERY_STRONG,
        isStructurallyValid = true
    )
    val sourceEvent = TransactionSourceEvent(
        sourceType = TransactionSourceType.SMS_HISTORY,
        sourceId = id,
        sender = sender,
        rawText = rawText,
        eventTimestamp = timestamp
    )
    return ScannedTransactionItem(
        candidate = validated,
        sourceEvent = sourceEvent,
        plannedDecision = if (isReview) DedupDecision.NEEDS_REVIEW else DedupDecision.NEW_TRANSACTION,
        matchStrategy = MatchStrategy.NONE,
        reason = if (isReview) "Account suffix unconfirmed" else "Valid transaction",
        accountIdentity = accountIdentity
    )
}

internal fun createSampleAccountGroups(): List<FinancialAccountGroup> {
    val hdfcIdentity = FinancialAccountIdentity(
        institutionId = "hdfc",
        institutionName = "HDFC Bank",
        accountSuffix = "4092",
        instrumentType = InstrumentType.BANK_ACCOUNT,
        confidence = IdentityConfidence.HIGH
    )
    val iciciIdentity = FinancialAccountIdentity(
        institutionId = "icici",
        institutionName = "ICICI Coral Card",
        cardSuffix = "8114",
        instrumentType = InstrumentType.CARD,
        confidence = IdentityConfidence.HIGH
    )
    val reviewIdentity = FinancialAccountIdentity(
        institutionId = null,
        institutionName = "Unidentified Sender / Suffix",
        instrumentType = InstrumentType.UNKNOWN,
        confidence = IdentityConfidence.UNKNOWN
    )

    val hdfcTxns = listOf(
        createSampleItem("1", "HDFCBK", "Rs.420.00 debited from A/C **4092 to Swiggy on 12-Oct-25. UPI:987654321", "Swiggy", 420.0, TransactionDirection.DEBIT, 4, hdfcIdentity),
        createSampleItem("2", "HDFCBK", "Rs.1,899.00 debited from A/C **4092 for Amazon India purchase on 11-Oct-25", "Amazon India", 1899.0, TransactionDirection.DEBIT, 28, hdfcIdentity),
        createSampleItem("3", "HDFCBK", "Rs.45,000.00 credited to A/C **4092 by Monthly Salary Deposit", "Salary Deposit", 45000.0, TransactionDirection.CREDIT, 54, hdfcIdentity),
        createSampleItem("4", "HDFCBK", "Rs.240.00 debited from A/C **4092 to Uber India on 09-Oct-25", "Uber India", 240.0, TransactionDirection.DEBIT, 80, hdfcIdentity),
        createSampleItem("5", "HDFCBK", "Rs.380.00 debited from A/C **4092 to Starbucks Coffee", "Starbucks Coffee", 380.0, TransactionDirection.DEBIT, 102, hdfcIdentity)
    )

    val iciciTxns = listOf(
        createSampleItem("6", "ICICIB", "Spent Rs.6,450.00 on ICICI Coral Card 8114 at MakeMyTrip on 12-Oct-25", "MakeMyTrip", 6450.0, TransactionDirection.DEBIT, 8, iciciIdentity),
        createSampleItem("7", "ICICIB", "Spent Rs.649.00 on ICICI Coral Card 8114 for Netflix on 10-Oct-25", "Netflix Subscription", 649.0, TransactionDirection.DEBIT, 42, iciciIdentity),
        createSampleItem("8", "ICICIB", "Spent Rs.2,100.00 on ICICI Coral Card 8114 at Shell Fuel on 08-Oct-25", "Shell Fuel Station", 2100.0, TransactionDirection.DEBIT, 90, iciciIdentity),
        createSampleItem("9", "ICICIB", "Spent Rs.350.00 on ICICI Coral Card 8114 at Zomato on 06-Oct-25", "Zomato", 350.0, TransactionDirection.DEBIT, 120, iciciIdentity)
    )

    val reviewTxns = listOf(
        createSampleItem("10", "PAYTM", "Sent Rs.150.00 from Wallet / VPA", "VP-PAYTM txn", 150.0, TransactionDirection.DEBIT, 12, reviewIdentity, isReview = true),
        createSampleItem("11", "IMPS", "IMPS txn Rs.2,000.00 received", "IMPS Transfer", 2000.0, TransactionDirection.CREDIT, 36, reviewIdentity, isReview = true),
        createSampleItem("12", "BK-ALERT", "Rs.500.00 payment completed to Merchant", "Unknown merchant", 500.0, TransactionDirection.DEBIT, 60, reviewIdentity, isReview = true)
    )

    return listOf(
        FinancialAccountGroup(
            groupId = "hdfc_4092",
            identity = hdfcIdentity,
            transactions = hdfcTxns,
            transactionCount = 84,
            totalDebit = 18450.0,
            totalCredit = 45000.0
        ),
        FinancialAccountGroup(
            groupId = "icici_8114",
            identity = iciciIdentity,
            transactions = iciciTxns,
            transactionCount = 44,
            totalDebit = 28370.0,
            totalCredit = 0.0
        ),
        FinancialAccountGroup(
            groupId = "unidentified_review",
            identity = reviewIdentity,
            transactions = reviewTxns,
            transactionCount = 14,
            totalDebit = 2650.0,
            totalCredit = 2000.0
        )
    )
}
