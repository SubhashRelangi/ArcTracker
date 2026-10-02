package com.subhashrelangi.arctracker.ui

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.subhashrelangi.arctracker.R
import com.subhashrelangi.arctracker.service.*
import com.subhashrelangi.arctracker.ui.theme.ArcColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ==========================================
// Color Tokens matching Ui-Designs/SmsScanWizard.png
// ==========================================
private val WizardBgColor = ArcColors.Background // #090C10
private val CardSurface = Color(0xFF12151D)
private val CardBorder = Color(0xFF222836)
private val SelectedCardSurface = Color(0xFF181E2B)
private val SelectedCardBorder = Color(0xFF333F54)

private val EmeraldAccent = Color(0xFF00E676)
private val EmeraldPillBg = Color(0xFF0D251A)
private val EmeraldPillBorder = Color(0xFF164E35)

private val AmberAccent = Color(0xFFF59E0B)
private val AmberPillBg = Color(0xFF2E200B)
private val AmberPillBorder = Color(0xFF533912)

private val SubBoxBg = Color(0xFF161A24)
private val SubBoxBorder = Color(0xFF262C3D)

private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF64748B)

enum class SmsImportUiPhase {
    SELECT_RANGE,
    SCANNING,
    SCAN_PREVIEW,
    IMPORTING,
    IMPORT_COMPLETE,
    ERROR
}

/**
 * Historical SMS Import Wizard screen matching Ui-Designs/SmsScanWizard.png exactly.
 *
 * Connected directly to HistoricalSmsImportManager for zero-cloud on-device scanning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsImportScreen(
    onNavigateBack: () -> Unit,
    onImportCompleted: () -> Unit = onNavigateBack,
    manager: HistoricalSmsImportManager? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val importManager = manager ?: remember { HistoricalSmsImportManager.create(context) }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH) }

    var selectedPeriodIndex by rememberSaveable { mutableStateOf(1) } // Default: "Last 3 months" (Recommended)
    var startDateMillis by rememberSaveable { mutableStateOf(SmsPermissionHelper.calculateThreeMonthsAgoTimestamp()) }
    var endDateMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }

    var currentPhase by remember { mutableStateOf(SmsImportUiPhase.SELECT_RANGE) }
    var scanProgress by remember { mutableStateOf(SmsScanProgress(0)) }
    var scanResult by remember { mutableStateOf<SmsScanResult.Success?>(null) }
    var importProgress by remember { mutableStateOf(SmsImportProgress(0, 0)) }
    var importResult by remember { mutableStateOf<SmsImportResult.Success?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isCancelled by remember { mutableStateOf(false) }

    var selectedGroupIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedGroupIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var scanDurationSeconds by remember { mutableStateOf("0.38s") }

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

    val isDateRangeValid = selectedPeriodIndex != -1 && startDateMillis <= endDateMillis

    val rangeDays = remember(startDateMillis, endDateMillis) {
        val diff = endDateMillis - startDateMillis
        maxOf(1, (diff / (1000L * 60 * 60 * 24)).toInt())
    }

    val windowLabel = when (selectedPeriodIndex) {
        0 -> "30 Days Window"
        1 -> "3 Months Window"
        2 -> "6 Months Window"
        3 -> "1 Year Window"
        4 -> "Custom Window"
        else -> ""
    }

    fun togglePeriod(index: Int) {
        if (selectedPeriodIndex == index) {
            selectedPeriodIndex = -1
        } else {
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
                // 4: Custom range keeps existing custom dates
            }
        }
    }

    fun showDatePicker(initialMillis: Long, maxMillis: Long? = System.currentTimeMillis(), onDateSelected: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
        val dialog = DatePickerDialog(
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
        )
        if (maxMillis != null) {
            dialog.datePicker.maxDate = maxMillis
        }
        dialog.show()
    }

    fun triggerScan() {
        SmsPermissionHelper.setInitialImportCompleted(context, true)
        errorMessage = null
        currentPhase = SmsImportUiPhase.SCANNING
        isCancelled = false
        val scanStartTime = System.currentTimeMillis()
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                importManager.scan(startDateMillis, endDateMillis) { prog ->
                    scanProgress = prog
                    !isCancelled
                }
            }
            val elapsed = maxOf(0.12, (System.currentTimeMillis() - scanStartTime) / 1000.0)
            scanDurationSeconds = "%.2fs".format(Locale.ENGLISH, elapsed)
            if (isCancelled || res is SmsScanResult.Cancelled) {
                currentPhase = SmsImportUiPhase.SELECT_RANGE
            } else if (res is SmsScanResult.Success) {
                scanResult = res
                selectedGroupIds = res.accountGroups
                    .filter { it.groupId != "unidentified_account" }
                    .map { it.groupId }
                    .toSet()
                    .ifEmpty { res.accountGroups.map { it.groupId }.toSet() }
                expandedGroupIds = emptySet()
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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasSmsPermission = isGranted
        if (isGranted) {
            isPermanentlyDenied = false
            SmsPermissionHelper.setInitialImportCompleted(context, true)
            triggerScan()
        } else {
            currentPhase = SmsImportUiPhase.SELECT_RANGE
            val activity = context.findActivity()
            if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_SMS)) {
                isPermanentlyDenied = true
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WizardBgColor)
            .statusBarsPadding()
    ) {
        // ----------------------------------------------------
        // 1. Static Wizard Header matching reference
        // ----------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (currentPhase == SmsImportUiPhase.SCAN_PREVIEW) {
                            currentPhase = SmsImportUiPhase.SELECT_RANGE
                        } else {
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Sms Import Wizard",
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                )
            }

            // Real ArcTracker App Icon from app res folder
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(BorderStroke(1.dp, Color(0xFF2D3748)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "ArcTracker App Icon",
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // ----------------------------------------------------
        // Scrollable Body Content
        // ----------------------------------------------------
        when (currentPhase) {
            SmsImportUiPhase.SELECT_RANGE -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // ----------------------------------------------------
                        // Select Time Period Section
                        // ----------------------------------------------------
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Select Time Period",
                                    color = TextWhite,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "Historical transaction breadth for deterministic sync",
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                        }

                        // Period Options (1 to 5) with left checkboxes
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 1. Last 30 days
                            PeriodOptionCard(
                                title = "Last 30 days",
                                subtitle = "Recent active statement • ~120 msgs",
                                trailingIcon = Icons.Outlined.Schedule,
                                isSelected = selectedPeriodIndex == 0,
                                onClick = { togglePeriod(0) }
                            )

                            // 2. Last 3 months (Recommended)
                            PeriodOptionCard(
                                title = "Last 3 months",
                                subtitle = "Full balance reconciliation",
                                trailingIcon = Icons.Outlined.CalendarMonth,
                                badgeText = "Recommended",
                                isSelected = selectedPeriodIndex == 1,
                                onClick = { togglePeriod(1) }
                            )

                            // 3. Last 6 months
                            PeriodOptionCard(
                                title = "Last 6 months",
                                subtitle = "Comprehensive financial quarter",
                                trailingIcon = Icons.Outlined.GridView,
                                isSelected = selectedPeriodIndex == 2,
                                onClick = { togglePeriod(2) }
                            )

                            // 4. Last 1 year
                            PeriodOptionCard(
                                title = "Last 1 year",
                                subtitle = "Annual tax & ledger construction",
                                trailingIcon = Icons.Outlined.Description,
                                isSelected = selectedPeriodIndex == 3,
                                onClick = { togglePeriod(3) }
                            )

                            // 5. Custom range
                            PeriodOptionCard(
                                title = "Custom range",
                                subtitle = "Select custom start and end date",
                                trailingIcon = Icons.Outlined.CalendarMonth,
                                isSelected = selectedPeriodIndex == 4,
                                onClick = { togglePeriod(4) }
                            )
                        }

                        // Custom Date Pickers (visible if custom range is selected)
                        if (selectedPeriodIndex == 4) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = SubBoxBg,
                                border = BorderStroke(1.dp, SubBoxBorder)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Choose Custom Date Range",
                                        color = TextWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    showDatePicker(startDateMillis) { newDate ->
                                                        startDateMillis = newDate
                                                    }
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF1E2433),
                                            border = BorderStroke(1.dp, Color(0xFF2E394F))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(dateFormat.format(Date(startDateMillis)), color = TextWhite, fontSize = 11.5.sp)
                                                Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                                            }
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    showDatePicker(endDateMillis) { newDate ->
                                                        endDateMillis = newDate
                                                    }
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF1E2433),
                                            border = BorderStroke(1.dp, Color(0xFF2E394F))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(dateFormat.format(Date(endDateMillis)), color = TextWhite, fontSize = 11.5.sp)
                                                Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                                            }
                                        }
                                    }
                                    if (!isDateRangeValid) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Start date must be before or equal to end date",
                                            color = Color(0xFFEF4444),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        // ----------------------------------------------------
                        // 4. Active Ingest Window Card
                        // ----------------------------------------------------
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(11.dp),
                            color = CardSurface,
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "ACTIVE INGEST WINDOW",
                                        color = TextSecondary,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )

                                    Text(
                                        text = if (selectedPeriodIndex == -1) "No Window Selected" else "$rangeDays Days Range",
                                        color = if (selectedPeriodIndex == -1) TextMuted else EmeraldAccent,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(5.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // FROM Box
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (selectedPeriodIndex == 4) {
                                                    showDatePicker(startDateMillis) { startDateMillis = it }
                                                }
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = SubBoxBg,
                                        border = BorderStroke(1.dp, SubBoxBorder)
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "FROM",
                                                    color = Color(0xFF64748B),
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "IST 00:00",
                                                    color = Color(0xFF64748B),
                                                    fontSize = 8.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = dateFormat.format(Date(startDateMillis)),
                                                    color = TextWhite,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Icon(
                                                    imageVector = Icons.Outlined.CalendarToday,
                                                    contentDescription = null,
                                                    tint = Color(0xFF64748B),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }

                                    // TO Box
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (selectedPeriodIndex == 4) {
                                                    showDatePicker(endDateMillis) { endDateMillis = it }
                                                }
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = SubBoxBg,
                                        border = BorderStroke(1.dp, SubBoxBorder)
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "TO",
                                                    color = Color(0xFF64748B),
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Live Sync",
                                                    color = EmeraldAccent,
                                                    fontSize = 8.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = dateFormat.format(Date(endDateMillis)),
                                                    color = TextWhite,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Icon(
                                                    imageVector = Icons.Outlined.CalendarToday,
                                                    contentDescription = null,
                                                    tint = Color(0xFF64748B),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ----------------------------------------------------
                        // 5. SMS Permission Status Card
                        // ----------------------------------------------------
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(11.dp),
                            color = CardSurface,
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (hasSmsPermission) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = if (hasSmsPermission) EmeraldAccent else AmberAccent,
                                        modifier = Modifier.size(15.dp)
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Text(
                                        text = if (hasSmsPermission) "Granted (READ_SMS)" else "Permission Required (READ_SMS)",
                                        color = TextWhite,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.width(5.dp))

                                    Text(
                                        text = "• Android Process Recovery Active",
                                        color = Color(0xFF64748B),
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = "Strict OTP & Spam suppression active. No personal messages or OTP tokens are ever stored or retained.",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp
                                )

                                if (!hasSmsPermission) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                if (isPermanentlyDenied) {
                                                    SmsPermissionHelper.openAppSettings(context)
                                                } else {
                                                    hasRequestedPermissionThisSession = true
                                                    permissionLauncher.launch(Manifest.permission.READ_SMS)
                                                }
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF222938),
                                        border = BorderStroke(1.dp, Color(0xFF3B4863))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (isPermanentlyDenied) "Open App Settings" else "Grant SMS Permission",
                                                color = TextWhite,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ----------------------------------------------------
                    // Bottom Docked: Scan Button & Regex Engine Footer
                    // ----------------------------------------------------
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Error display if any
                        if (errorMessage != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF2B1215),
                                border = BorderStroke(1.dp, Color(0xFF5C1D24))
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(errorMessage!!, color = Color(0xFFFCA5A5), fontSize = 11.5.sp)
                                }
                            }
                        }

                        // 6. Primary Scan Action Button
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .shadow(
                                    elevation = if (isDateRangeValid) 8.dp else 0.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    spotColor = Color(0x66000000),
                                    ambientColor = Color(0x33000000)
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    enabled = isDateRangeValid,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(color = Color.Black.copy(alpha = 0.2f))
                                ) {
                                    errorMessage = null
                                    if (!isDateRangeValid) return@clickable

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
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDateRangeValid) Color.White else Color(0xFF1E232E),
                            border = if (isDateRangeValid) null else BorderStroke(1.dp, Color(0xFF2E3747))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (selectedPeriodIndex == -1) "Select Time Period" else "Scan Messages ($windowLabel)",
                                    color = if (isDateRangeValid) Color(0xFF090C10) else Color(0xFF6B7280),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.1.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = if (isDateRangeValid) Color(0xFF090C10) else Color(0xFF6B7280),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 7. Deterministic Regex Engine Footer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldAccent)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Deterministic Regex Engine v4.2 Local • ~460 msg/s",
                                color = Color(0xFF64748B),
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
            SmsImportUiPhase.SCAN_PREVIEW -> {
                val result = scanResult
                if (result != null) {
                    val groups = result.accountGroups
                    val selectedGroups = groups.filter { selectedGroupIds.contains(it.groupId) }
                    val selectedTxnCount = selectedGroups.sumOf { it.transactionCount }
                    val selectedDebitTotal = selectedGroups.sumOf { it.totalDebit }
                    val selectedCreditTotal = selectedGroups.sumOf { it.totalCredit }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Step 2 of 2 Badge
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF111722),
                                border = BorderStroke(1.dp, Color(0xFF1E2838))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(EmeraldAccent)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Step 2 of 2: Ingestion Audit & Ac...",
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 11.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Stage Ready",
                                        color = EmeraldAccent,
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 2. Deterministic Scan Diagnostics Card
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = CardSurface,
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Diagnostics Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.CheckCircle,
                                                contentDescription = null,
                                                tint = EmeraldAccent,
                                                modifier = Modifier.size(17.dp)
                                            )
                                            Spacer(modifier = Modifier.width(7.dp))
                                            Text(
                                                text = "Deterministic Scan Diagnostics",
                                                color = TextWhite,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Complete",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(1.dp))

                                    // 5 Diagnostic Rows matching ScanResult.png
                                    DiagnosticRow(
                                        icon = Icons.Outlined.Info,
                                        iconTint = Color(0xFF64748B),
                                        label = "${result.messagesScanned} messages scanned",
                                        tag = "Total window"
                                    )

                                    DiagnosticRow(
                                        icon = Icons.Outlined.AccountBalance,
                                        iconTint = Color(0xFF3B82F6),
                                        label = "${result.financialMessages} financial messages found",
                                        tag = "Pattern matched"
                                    )

                                    DiagnosticRow(
                                        icon = Icons.Outlined.CheckCircle,
                                        iconTint = EmeraldAccent,
                                        label = "${result.transactionCandidatesCount} transaction candidates",
                                        tag = "Schema valid"
                                    )

                                    DiagnosticRow(
                                        icon = Icons.Outlined.AddCircleOutline,
                                        iconTint = EmeraldAccent,
                                        label = "${result.newTransactionsCount} new transactions available",
                                        labelColor = EmeraldAccent,
                                        tag = if (result.duplicatesCount == 0) "Zero dups" else "${result.duplicatesCount} dups",
                                        tagColor = EmeraldAccent,
                                        tagBold = true
                                    )

                                    val noiseOrOtp = result.noiseMessages + result.nonFinancialMessages
                                    DiagnosticRow(
                                        icon = Icons.Outlined.RemoveCircleOutline,
                                        iconTint = Color(0xFF64748B),
                                        label = "$noiseOrOtp non-financial / OTP ignored",
                                        labelColor = Color(0xFF94A3B8),
                                        tag = "Suppressed"
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    // Diagnostic Footer
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Memory,
                                                contentDescription = null,
                                                tint = Color(0xFF64748B),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "Local in-memory evaluation • $scanDurationSeconds execution",
                                                color = Color(0xFF64748B),
                                                fontSize = 9.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "SHA-256",
                                            color = Color(0xFF64748B),
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // 3. Select Accounts to Import Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Select accounts to",
                                        color = TextWhite,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "import",
                                        color = TextWhite,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF1E2430),
                                        border = BorderStroke(1.dp, Color(0xFF2E3A4E)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                selectedGroupIds = groups.map { it.groupId }.toSet()
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("Select", color = TextWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                            Text("All", color = TextWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF1E2430),
                                        border = BorderStroke(1.dp, Color(0xFF2E3A4E)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                selectedGroupIds = emptySet()
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("Clear", color = Color(0xFF64748B), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                            Text("All", color = Color(0xFF64748B), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // Subtitle Row with Amber Dot
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(AmberAccent)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${selectedGroups.size} of ${groups.size} accounts selected • $selectedTxnCount transactions",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // 4. Discovered Account Cards
                            if (groups.isEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF10151E),
                                    border = BorderStroke(1.dp, Color(0xFF1E2838))
                                ) {
                                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                                        Text("No accounts found in this period", color = TextSecondary, fontSize = 12.sp)
                                    }
                                }
                            } else {
                                groups.forEach { group ->
                                    val isSelected = selectedGroupIds.contains(group.groupId)
                                    val isExpanded = expandedGroupIds.contains(group.groupId)
                                    val isUnidentified = group.groupId == "unidentified_account"

                                    if (isUnidentified) {
                                        // Unidentified Account Card (Amber Warning)
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFF221706),
                                            border = BorderStroke(1.dp, Color(0xFF684712))
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Square checkbox
                                                    Box(
                                                        modifier = Modifier
                                                            .size(20.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(if (isSelected) Color(0xFF3B2607) else Color(0xFF191104))
                                                            .border(BorderStroke(1.dp, if (isSelected) AmberAccent else Color(0xFF5E3C0B)), RoundedCornerShape(4.dp))
                                                            .clickable {
                                                                selectedGroupIds = if (isSelected) selectedGroupIds - group.groupId else selectedGroupIds + group.groupId
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isSelected) {
                                                            Icon(Icons.Filled.Check, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(14.dp))
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.width(10.dp))

                                                    Column(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable {
                                                                selectedGroupIds = if (isSelected) selectedGroupIds - group.groupId else selectedGroupIds + group.groupId
                                                            }
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Filled.Warning, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(15.dp))
                                                            Spacer(modifier = Modifier.width(5.dp))
                                                            Text(
                                                                text = "Unidentified Account",
                                                                color = AmberAccent,
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "REVIEW REQUIRED • Ambiguous Suffix",
                                                            color = Color(0xFFD97706),
                                                            fontSize = 10.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.clickable {
                                                            expandedGroupIds = if (isExpanded) expandedGroupIds - group.groupId else expandedGroupIds + group.groupId
                                                        }
                                                    ) {
                                                        Surface(
                                                            color = Color(0xFF130E05),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = "${group.transactionCount} txn",
                                                                color = AmberAccent,
                                                                fontSize = 11.sp,
                                                                fontFamily = FontFamily.Monospace,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Icon(
                                                            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.ChevronRight,
                                                            contentDescription = null,
                                                            tint = Color(0xFFD97706),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }

                                                // Amber explanatory inner box
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 8.dp),
                                                    color = Color(0xFF140D04),
                                                    border = BorderStroke(1.dp, Color(0xFF38250A)),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "Could not confidently link bank suffix. Tap to inspect raw evidence before importing.",
                                                        color = Color(0xFFCBD5E1),
                                                        fontSize = 11.sp,
                                                        lineHeight = 15.sp,
                                                        modifier = Modifier.padding(8.dp)
                                                    )
                                                }

                                                if (group.totalDebit > 0 || group.totalCredit > 0) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text("Debit ", color = Color(0xFF94A3B8), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                                            Text("-₹${formatCurrency(group.totalDebit)}", color = Color(0xFFEF4444), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                                        }
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text("Credit ", color = Color(0xFF94A3B8), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                                            val creditText = if (group.totalCredit > 0) "+₹${formatCurrency(group.totalCredit)}" else "₹0.00"
                                                            val creditColor = if (group.totalCredit > 0) EmeraldAccent else Color(0xFF64748B)
                                                            Text(creditText, color = creditColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }

                                                if (isExpanded && group.transactions.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    HorizontalDivider(color = Color(0xFF38250A))
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    group.transactions.forEach { txnItem ->
                                                        val cand = txnItem.candidate.candidate
                                                        val isCredit = cand.direction == TransactionDirection.CREDIT
                                                        val party = cand.merchant?.takeIf { it.isNotBlank() }
                                                            ?: cand.counterparty?.takeIf { it.isNotBlank() }
                                                            ?: "Transaction"
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 3.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(party, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextWhite, maxLines = 1)
                                                                cand.transactionDateString?.let {
                                                                    Text(it, fontSize = 10.sp, color = TextMuted)
                                                                }
                                                            }
                                                            Text(
                                                                (if (isCredit) "+₹" else "-₹") + formatCurrency(cand.amount ?: 0.0),
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isCredit) EmeraldAccent else Color(0xFFF87171)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // Standard Identified Account Card
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFF10151E),
                                            border = BorderStroke(1.dp, Color(0xFF1E2838))
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Square checkbox
                                                    Box(
                                                        modifier = Modifier
                                                            .size(20.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(if (isSelected) Color(0xFF0F261E) else Color(0xFF151B26))
                                                            .border(BorderStroke(1.dp, if (isSelected) EmeraldAccent else Color(0xFF2D3B4F)), RoundedCornerShape(4.dp))
                                                            .clickable {
                                                                selectedGroupIds = if (isSelected) selectedGroupIds - group.groupId else selectedGroupIds + group.groupId
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isSelected) {
                                                            Icon(Icons.Filled.Check, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(14.dp))
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.width(10.dp))

                                                    Column(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable {
                                                                selectedGroupIds = if (isSelected) selectedGroupIds - group.groupId else selectedGroupIds + group.groupId
                                                            }
                                                    ) {
                                                        Text(
                                                            text = group.identity.getDisplayName(),
                                                            color = TextWhite,
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        val subLabel = when {
                                                            group.identity.instrumentType == InstrumentType.CARD -> "Auto-Matched • Credit Card"
                                                            group.identity.institutionName == null -> "Merchant Direct Debit"
                                                            group.groupId.contains("salary", ignoreCase = true) -> "Auto-Matched • Salary Account"
                                                            group.groupId.contains("upi", ignoreCase = true) -> "Auto-Matched • Primary UPI"
                                                            else -> "Auto-Matched • Savings"
                                                        }
                                                        val subColor = if (subLabel.startsWith("Auto-Matched")) EmeraldAccent else Color(0xFF94A3B8)
                                                        Text(
                                                            text = subLabel,
                                                            color = subColor,
                                                            fontSize = 10.5.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.clickable {
                                                            expandedGroupIds = if (isExpanded) expandedGroupIds - group.groupId else expandedGroupIds + group.groupId
                                                        }
                                                    ) {
                                                        Surface(
                                                            color = Color(0xFF1A2230),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = "${group.transactionCount} txns",
                                                                color = Color(0xFFCBD5E1),
                                                                fontSize = 11.sp,
                                                                fontFamily = FontFamily.Monospace,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Icon(
                                                            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.ChevronRight,
                                                            contentDescription = null,
                                                            tint = Color(0xFF64748B),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }

                                                // Bottom Row: Debit / Credit
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("Debit ", color = Color(0xFF64748B), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                                        Text("-₹${formatCurrency(group.totalDebit)}", color = Color(0xFFEF4444), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("Credit ", color = Color(0xFF64748B), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                                        val creditText = if (group.totalCredit > 0) "+₹${formatCurrency(group.totalCredit)}" else "₹0.00"
                                                        val creditColor = if (group.totalCredit > 0) EmeraldAccent else Color(0xFF64748B)
                                                        Text(creditText, color = creditColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                                    }
                                                }

                                                if (isExpanded && group.transactions.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    HorizontalDivider(color = Color(0xFF1E2838))
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    group.transactions.forEach { txnItem ->
                                                        val cand = txnItem.candidate.candidate
                                                        val isCredit = cand.direction == TransactionDirection.CREDIT
                                                        val party = cand.merchant?.takeIf { it.isNotBlank() }
                                                            ?: cand.counterparty?.takeIf { it.isNotBlank() }
                                                            ?: "Transaction"
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 3.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(party, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextWhite, maxLines = 1)
                                                                cand.transactionDateString?.let {
                                                                    Text(it, fontSize = 10.sp, color = TextMuted)
                                                                }
                                                            }
                                                            Text(
                                                                (if (isCredit) "+₹" else "-₹") + formatCurrency(cand.amount ?: 0.0),
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isCredit) EmeraldAccent else Color(0xFFF87171)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 5. Staging Ingest Summary Card
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF10151E),
                                border = BorderStroke(1.dp, Color(0xFF1E2838))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Top Row: Icon + Title + Counts
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Outlined.AccountBalanceWallet,
                                                contentDescription = null,
                                                tint = Color(0xFFCBD5E1),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("Staging Ingest", color = TextWhite, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                                Text("Summary", color = TextWhite, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Accounts: ${selectedGroups.size} |", color = Color(0xFF94A3B8), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                            Text("Transactions: $selectedTxnCount", color = Color(0xFF94A3B8), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Spending & Income Blocks Side-by-Side
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Selected Spending Box
                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            color = Color(0xFF241014),
                                            border = BorderStroke(1.dp, Color(0xFF4C1D24)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                                Text("Selected Spending", color = Color(0xFFEF4444), fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("-₹${formatCurrency(selectedDebitTotal)}", color = Color(0xFFEF4444), fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        // Selected Income Box
                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            color = Color(0xFF0A2018),
                                            border = BorderStroke(1.dp, Color(0xFF124330)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                                Text("Selected Income", color = EmeraldAccent, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("+₹${formatCurrency(selectedCreditTotal)}", color = EmeraldAccent, fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Net Ledger Impact Row
                                    val netBalance = selectedCreditTotal - selectedDebitTotal
                                    val netFormatted = (if (netBalance >= 0) "+₹" else "-₹") + formatCurrency(Math.abs(netBalance))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Net Ledger", color = Color(0xFF94A3B8), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                            Text("Impact", color = Color(0xFF94A3B8), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "$netFormatted net balance",
                                                color = if (netBalance >= 0) EmeraldAccent else Color(0xFFEF4444),
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "adjustment",
                                                color = if (netBalance >= 0) EmeraldAccent else Color(0xFFEF4444),
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Action Controls Docked at Screen End
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .shadow(
                                        elevation = if (selectedGroupIds.isNotEmpty()) 8.dp else 0.dp,
                                        shape = RoundedCornerShape(12.dp),
                                        spotColor = Color(0x66000000),
                                        ambientColor = Color(0x33000000)
                                    )
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(
                                        enabled = selectedGroupIds.isNotEmpty(),
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = rememberRipple(color = Color.Black.copy(alpha = 0.2f))
                                    ) {
                                        currentPhase = SmsImportUiPhase.IMPORTING
                                        isCancelled = false
                                        scope.launch {
                                            val impRes = withContext(Dispatchers.IO) {
                                                importManager.importTransactions(
                                                    scanResult = result,
                                                    selectedGroupIds = selectedGroupIds
                                                ) { prog ->
                                                    importProgress = prog
                                                    !isCancelled
                                                }
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
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedGroupIds.isNotEmpty()) Color.White else Color(0xFF1E232E),
                                border = if (selectedGroupIds.isNotEmpty()) null else BorderStroke(1.dp, Color(0xFF2E3747))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Import $selectedTxnCount Transactions to Ledger",
                                        color = if (selectedGroupIds.isNotEmpty()) Color(0xFF090C10) else Color(0xFF6B7280),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.1.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = if (selectedGroupIds.isNotEmpty()) Color(0xFF090C10) else Color(0xFF6B7280),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { currentPhase = SmsImportUiPhase.SELECT_RANGE }
                                    .padding(vertical = 4.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarToday,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Change Date Range",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (currentPhase) {
                        SmsImportUiPhase.SCANNING -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = EmeraldAccent,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = "Scanning SMS Messages...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${scanProgress.recordsProcessed} messages processed locally",
                                fontSize = 13.5.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                            if (scanProgress.financialDetected > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${scanProgress.financialDetected} financial transactions detected",
                                    fontSize = 13.sp,
                                    color = EmeraldAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        isCancelled = true
                                        currentPhase = SmsImportUiPhase.SELECT_RANGE
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E2433),
                                border = BorderStroke(1.dp, Color(0xFF2E394F))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("Cancel Scan", color = TextSecondary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                SmsImportUiPhase.IMPORTING -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = EmeraldAccent, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(18.dp))
                            Text("Importing Transactions...", style = MaterialTheme.typography.titleMedium, color = TextWhite)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "${importProgress.itemsProcessed} / ${importProgress.totalToImport} processed",
                                fontSize = 14.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Imported: ${importProgress.insertedCount} | Duplicates skipped: ${importProgress.duplicatesSkipped}",
                                fontSize = 12.sp,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { isCancelled = true },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E2433),
                                border = BorderStroke(1.dp, Color(0xFF2E394F))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("Cancel Import", color = TextSecondary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                SmsImportUiPhase.IMPORT_COMPLETE -> {
                    val result = importResult
                    if (result != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = CardSurface,
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldAccent,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        "Import Complete",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                DarkResultRow(Icons.Filled.CheckCircle, "${result.insertedCount} transactions imported", EmeraldAccent)
                                if (result.duplicatesSkippedCount > 0) {
                                    DarkResultRow(Icons.Filled.ContentCopy, "${result.duplicatesSkippedCount} duplicates skipped", TextMuted)
                                }
                                if (result.enrichedCount > 0) {
                                    DarkResultRow(Icons.Filled.Refresh, "${result.enrichedCount} existing transactions enriched", Color(0xFF60A5FA))
                                }
                                if (result.reviewPendingCount > 0) {
                                    DarkResultRow(Icons.Filled.Warning, "${result.reviewPendingCount} routed to Pending Expenses for review", AmberAccent)
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = Color(0xFF222836))
                                Spacer(modifier = Modifier.height(8.dp))

                                if (result.totalAmountImported > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Imported spending", fontSize = 13.5.sp, color = TextSecondary)
                                        Text("₹${"%.2f".format(result.totalAmountImported)}", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                                    }
                                }
                                if (result.totalIncomeImported > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Imported income", fontSize = 13.5.sp, color = TextSecondary)
                                        Text("₹${"%.2f".format(result.totalIncomeImported)}", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = EmeraldAccent)
                                    }
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onImportCompleted() },
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Done", color = Color(0xFF090C10), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    currentPhase = SmsImportUiPhase.SELECT_RANGE
                                    scanResult = null
                                    importResult = null
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF141722),
                            border = BorderStroke(1.dp, Color(0xFF222836))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Import More", color = TextSecondary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                SmsImportUiPhase.ERROR -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF2B1215),
                        border = BorderStroke(1.dp, Color(0xFF5C1D24))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Import Error", fontWeight = FontWeight.Bold, color = Color(0xFFF87171), fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(errorMessage ?: "An error occurred", color = Color(0xFFFCA5A5), fontSize = 13.sp)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { currentPhase = SmsImportUiPhase.SELECT_RANGE },
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Try Again", color = Color(0xFF090C10), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
                else -> {}
            }
        }
    }
}
}
}

// -----------------------------------------------------------------------------
// Sub-components
// -----------------------------------------------------------------------------

@Composable
private fun PeriodOptionCard(
    title: String,
    subtitle: String,
    trailingIcon: ImageVector,
    badgeText: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        color = if (isSelected) SelectedCardSurface else CardSurface,
        border = BorderStroke(1.dp, if (isSelected) SelectedCardBorder else CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left checkbox indicator (checked = white filled with black checkmark, unchecked = dark box)
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.5.dp))
                    .background(if (isSelected) Color.White else Color(0xFF161A22))
                    .border(
                        BorderStroke(1.dp, if (isSelected) Color.White else Color(0xFF2E3747)),
                        RoundedCornerShape(4.5.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Checked",
                        tint = Color.Black,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(11.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = TextWhite,
                        fontSize = 13.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                    )

                    if (badgeText != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AmberPillBg)
                                .border(BorderStroke(1.dp, AmberPillBorder), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = badgeText,
                                color = AmberAccent,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(1.dp))

                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }

            // Trailing icon (always displayed, no checkmark on the right)
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = if (isSelected) TextWhite else Color(0xFF4A5568),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun TechnicalPill(
    text: String,
    isHighlight: Boolean
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isHighlight) EmeraldPillBg else Color(0xFF191D26))
            .border(
                BorderStroke(1.dp, if (isHighlight) EmeraldPillBorder else Color(0xFF282F3E)),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = if (isHighlight) EmeraldAccent else TextSecondary,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DarkResultRow(
    icon: ImageVector,
    text: String,
    tint: Color = TextSecondary
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, fontSize = 12.5.sp, color = TextWhite)
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

@Composable
private fun DiagnosticRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    labelColor: Color = Color(0xFFCBD5E1),
    tag: String,
    tagColor: Color = Color(0xFF64748B),
    tagBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = labelColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = tag,
            color = tagColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (tagBold) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun formatCurrency(amount: Double): String {
    val formatter = java.text.NumberFormat.getNumberInstance(Locale("en", "IN"))
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return formatter.format(amount)
}

