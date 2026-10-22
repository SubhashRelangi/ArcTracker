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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.subhashrelangi.arctracker.BuildConfig
import com.subhashrelangi.arctracker.R
import com.subhashrelangi.arctracker.service.HistoricalSmsImportManager
import com.subhashrelangi.arctracker.service.SmsImportResult
import com.subhashrelangi.arctracker.service.SmsPermissionHelper
import com.subhashrelangi.arctracker.service.SmsScanProgress
import com.subhashrelangi.arctracker.service.SmsScanResult
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
@Composable
fun SmsSetupScreen(
    onImportCompleted: () -> Unit,
    onSkipClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    initialStartScanning: Boolean = false
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

    // Dedicated Scan Screen execution & animation states
    var isScanning by rememberSaveable { mutableStateOf(initialStartScanning) }
    var scanProgressFloat by remember { mutableFloatStateOf(if (initialStartScanning) 0.05f else 0f) }
    var messagesScannedCount by remember { mutableIntStateOf(0) }
    var totalMessagesEstimated by remember { mutableIntStateOf(400) }
    var financialMessagesCount by remember { mutableIntStateOf(0) }
    var scanStatusText by remember { mutableStateOf("Initializing scanner...") }
    var scanJob by remember { mutableStateOf<Job?>(null) }

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

    // Helper function to execute scan & auto-import
    fun startScanAndImport() {
        if (scanJob?.isActive == true) return
        isScanning = true
        scanProgressFloat = 0.05f
        scanStatusText = "Reading SMS inbox..."
        messagesScannedCount = 0
        financialMessagesCount = 0
        totalMessagesEstimated = 400

        scanJob = scope.launch {
            try {
                delay(200)
                if (scanProgressFloat < 0.15f) scanProgressFloat = 0.15f

                val importResult = withContext(Dispatchers.IO) {
                    importManager.scanAndImport(
                        startTimeMillis = startDateMillis,
                        endTimeMillis = endDateMillis,
                        scanProgress = { progress: SmsScanProgress ->
                            messagesScannedCount = progress.recordsProcessed
                            val est = progress.totalEstimated?.takeIf { it > 0 } ?: maxOf(400, progress.recordsProcessed + 40)
                            totalMessagesEstimated = est
                            val ratio = (progress.recordsProcessed.toFloat() / est).coerceIn(0.15f, 0.75f)
                            scanProgressFloat = ratio
                            financialMessagesCount = progress.financialDetected
                            scanStatusText = if (progress.financialDetected > 0) {
                                "${progress.financialDetected} txns detected"
                            } else {
                                "Scanning messages..."
                            }
                            true
                        },
                        importProgress = { progress ->
                            val ratio = if (progress.totalToImport > 0) {
                                0.75f + 0.25f * (progress.itemsProcessed.toFloat() / progress.totalToImport)
                            } else {
                                0.95f
                            }
                            scanProgressFloat = ratio.coerceIn(0.75f, 1.0f)
                            scanStatusText = "Importing ${progress.itemsProcessed} of ${progress.totalToImport}..."
                            true
                        }
                    )
                }

                // If scan completed with zero messages (e.g. fresh environment), give a smooth visual ramp
                if (messagesScannedCount == 0 && scanProgressFloat < 0.9f) {
                    scanProgressFloat = 0.5f
                    delay(300)
                }

                // Smoothly finish at 100%
                scanProgressFloat = 1.0f
                scanStatusText = if (financialMessagesCount > 0) "$financialMessagesCount txns imported" else "Scan complete"
                delay(800)

                settingsRepo.setSetupInProgress(false)
                settingsRepo.setInitialOnboardingCompleted(true)
                SmsPermissionHelper.setInitialImportCompleted(context, true)
                onImportCompleted()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    return@launch
                }
                settingsRepo.setSetupInProgress(false)
                settingsRepo.setInitialOnboardingCompleted(true)
                SmsPermissionHelper.setInitialImportCompleted(context, true)
                onImportCompleted()
            } finally {
                isScanning = false
            }
        }
    }

    val onCancelScan: () -> Unit = {
        scanJob?.cancel()
        scanJob = null
        isScanning = false
        scanProgressFloat = 0f
    }

    LaunchedEffect(initialStartScanning) {
        if (initialStartScanning && scanJob?.isActive != true) {
            startScanAndImport()
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
                                    startScanAndImport()
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
                                text = "AUDITING",
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
                            text = "Messages checked",
                            fontSize = 11.sp,
                            color = BrandTextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (totalMessagesEstimated > 0) "$messagesScannedCount of $totalMessagesEstimated" else "$messagesScannedCount",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandTextPrimary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Status",
                            fontSize = 11.sp,
                            color = BrandTextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = scanStatusText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StepPillText
                        )
                    }
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
