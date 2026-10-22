package com.subhashrelangi.arctracker.ui

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.Dispatchers
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
    modifier: Modifier = Modifier
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

    // Scan/Import execution state
    var isScanning by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf("Scanning SMS messages...") }

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
        if (isScanning) return
        isScanning = true
        scanMessage = "Scanning transaction SMS..."

        scope.launch {
            try {
                val importResult = withContext(Dispatchers.IO) {
                    importManager.scanAndImport(
                        startTimeMillis = startDateMillis,
                        endTimeMillis = endDateMillis,
                        scanProgress = { progress: SmsScanProgress ->
                            if (progress.recordsProcessed > 0) {
                                scanMessage = "Scanned ${progress.recordsProcessed} messages..."
                            }
                            true
                        },
                        importProgress = { progress ->
                            if (progress.itemsProcessed > 0) {
                                scanMessage = "Importing ${progress.itemsProcessed} of ${progress.totalToImport}..."
                            }
                            true
                        }
                    )
                }

                settingsRepo.setSetupInProgress(false)
                settingsRepo.setInitialOnboardingCompleted(true)
                SmsPermissionHelper.setInitialImportCompleted(context, true)
                onImportCompleted()
            } catch (_: Exception) {
                // In case of error, still proceed to dashboard
                settingsRepo.setSetupInProgress(false)
                settingsRepo.setInitialOnboardingCompleted(true)
                SmsPermissionHelper.setInitialImportCompleted(context, true)
                onImportCompleted()
            } finally {
                isScanning = false
            }
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

            // Scanning indicator if currently processing
            if (isScanning) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = StepPillText
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = scanMessage,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = StepPillText
                    )
                }
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
