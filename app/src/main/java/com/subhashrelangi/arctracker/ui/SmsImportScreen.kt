package com.subhashrelangi.arctracker.ui

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.subhashrelangi.arctracker.R
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.service.*
import com.subhashrelangi.arctracker.settings.MonitoringSettingsRepository
import com.subhashrelangi.arctracker.ui.theme.ArcColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

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

enum class ImportAnimationState {
    IMPORTING,
    SUCCESS,
    FAILURE
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
    onNavigateToLedger: () -> Unit = onImportCompleted,
    onNavigateToReview: () -> Unit = onImportCompleted,
    manager: HistoricalSmsImportManager? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val count = withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            val db = AppDatabase.getDatabase(context)
                            val backupManager = com.subhashrelangi.arctracker.backup.BackupManager(
                                database = db,
                                expenseDao = db.expenseDao(),
                                categoryDao = db.transactionCategoryDao(),
                                accountDao = db.knownFinancialAccountDao(),
                                ruleDao = db.userCategoryRuleDao(),
                                aliasDao = db.merchantAliasDao(),
                                budgetDao = db.budgetDao()
                            )
                            backupManager.exportTransactionsCsv(stream).getOrThrow()
                        } ?: 0
                    }
                    android.widget.Toast.makeText(context, "Exported $count transactions to CSV", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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
    var isNotificationAccessGranted by remember {
        mutableStateOf(NotificationPermissionHelper.isNotificationAccessGranted(context))
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
                isNotificationAccessGranted = NotificationPermissionHelper.isNotificationAccessGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = true) {
        when (currentPhase) {
            SmsImportUiPhase.IMPORT_COMPLETE -> onImportCompleted()
            SmsImportUiPhase.SCAN_PREVIEW -> currentPhase = SmsImportUiPhase.SELECT_RANGE
            SmsImportUiPhase.SCANNING -> {
                isCancelled = true
                currentPhase = SmsImportUiPhase.SELECT_RANGE
            }
            SmsImportUiPhase.IMPORTING -> {
                isCancelled = true
            }
            else -> onNavigateBack()
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
        val isCompletionPhase = currentPhase == SmsImportUiPhase.IMPORT_COMPLETE || currentPhase == SmsImportUiPhase.IMPORTING
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = if (isCompletionPhase) 4.dp else 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        when (currentPhase) {
                            SmsImportUiPhase.SCAN_PREVIEW -> {
                                currentPhase = SmsImportUiPhase.SELECT_RANGE
                            }
                            SmsImportUiPhase.IMPORT_COMPLETE -> {
                                onImportCompleted()
                            }
                            SmsImportUiPhase.IMPORTING -> {
                                // Do not interrupt running persistence
                            }
                            else -> {
                                onNavigateBack()
                            }
                        }
                    },
                    enabled = currentPhase != SmsImportUiPhase.IMPORTING,
                    modifier = Modifier.size(if (isCompletionPhase) 34.dp else 38.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (currentPhase == SmsImportUiPhase.IMPORTING) TextMuted else TextWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = "Sms Import Wizard",
                        color = TextWhite,
                        fontSize = if (isCompletionPhase) 17.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    )
                    if (isCompletionPhase) {
                        Text(
                            text = "ArcTracker Secure Vault",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCompletionPhase) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF161C26))
                            .border(BorderStroke(1.dp, Color(0xFF222C3D)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = "Secure Vault",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Real ArcTracker App Icon from app res folder
                Box(
                    modifier = Modifier
                        .size(if (isCompletionPhase) 32.dp else 34.dp)
                        .clip(CircleShape)
                        .background(if (isCompletionPhase) Color.White else Color.Black)
                        .border(BorderStroke(1.dp, Color(0xFF2D3748)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "ArcTracker App Icon",
                        modifier = Modifier.size(if (isCompletionPhase) 34.dp else 36.dp)
                    )
                }
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
            SmsImportUiPhase.IMPORTING, SmsImportUiPhase.IMPORT_COMPLETE -> {
                SmsImportCompletionContent(
                    animationState = if (currentPhase == SmsImportUiPhase.IMPORTING) ImportAnimationState.IMPORTING else ImportAnimationState.SUCCESS,
                    importResult = importResult,
                    scanResult = scanResult,
                    selectedGroupIds = selectedGroupIds,
                    isRealTimeIngestionActive = isNotificationAccessGranted &&
                        MonitoringSettingsRepository.getInstance(context).getSettings().let {
                            it.isNotificationTrackingEnabled && it.globalEnabled
                        },
                    onOpenNotificationSettings = {
                        NotificationPermissionHelper.openNotificationAccessSettings(context)
                    },
                    onCompleteGoHome = onImportCompleted,
                    onNavigateToReview = onNavigateToReview,
                    onExportCsv = {
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        csvExportLauncher.launch("arctracker_ingestion_audit_$timestamp.csv")
                    },
                    modifier = Modifier.weight(1f)
                )
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

private fun formatIndianNumber(number: Int): String {
    val formatter = java.text.NumberFormat.getNumberInstance(Locale("en", "IN"))
    return formatter.format(number)
}

private fun formatIndianCurrency(amount: Double): String {
    val formatter = java.text.NumberFormat.getNumberInstance(Locale("en", "IN"))
    if (amount % 1.0 == 0.0) {
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 0
    } else {
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
    }
    return formatter.format(amount)
}

// ==========================================
// Animation Components & Particles
// ==========================================
private data class AnimationParticle(
    val angleDeg: Float,
    val distanceDp: Float,
    val sizeDp: Float,
    val color: Color
)

private val SuccessParticles = listOf(
    AnimationParticle(25f, 26f, 3.0f, EmeraldAccent),
    AnimationParticle(70f, 22f, 2.2f, Color.White),
    AnimationParticle(115f, 28f, 2.8f, EmeraldAccent),
    AnimationParticle(165f, 20f, 2.4f, AmberAccent),
    AnimationParticle(210f, 27f, 3.0f, EmeraldAccent),
    AnimationParticle(255f, 23f, 2.0f, Color.White),
    AnimationParticle(295f, 29f, 2.8f, EmeraldAccent),
    AnimationParticle(340f, 21f, 2.2f, Color(0xFF86EFAC))
)

@Composable
fun ImportCompletionAnimation(
    state: ImportAnimationState,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 68.dp
) {
    // Persist completion state across recompositions and scrolling
    var hasPlayedSuccess by rememberSaveable { mutableStateOf(false) }

    // State A: Rotating arc + pulsing glow while IMPORTING
    val infiniteTransition = rememberInfiniteTransition(label = "ImportLoadingTransition")
    val loadingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loadingRotation"
    )
    val loadingGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loadingGlowAlpha"
    )

    // State B: Success fill, pop, checkmark path draw, and particles burst
    val circlePopScale = remember { Animatable(if (hasPlayedSuccess) 1f else 0.85f) }
    val checkmarkDrawProgress = remember { Animatable(if (hasPlayedSuccess) 1f else 0f) }
    val particlesProgress = remember { Animatable(if (hasPlayedSuccess) 1f else 0f) }

    LaunchedEffect(state) {
        if (state == ImportAnimationState.SUCCESS && !hasPlayedSuccess) {
            // Trigger sequence: pop scale, checkmark draw, particles burst
            launch {
                circlePopScale.animateTo(1.08f, tween(160, easing = FastOutSlowInEasing))
                circlePopScale.animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            }
            launch {
                // Particles burst outward and fade smoothly
                particlesProgress.animateTo(1f, tween(480, easing = LinearOutSlowInEasing))
            }
            // Checkmark draws smoothly stroke-by-stroke
            checkmarkDrawProgress.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
            hasPlayedSuccess = true
        }
    }

    val contentDesc = when (state) {
        ImportAnimationState.IMPORTING -> "Importing transactions to local ledger"
        ImportAnimationState.SUCCESS -> "Import completed successfully"
        ImportAnimationState.FAILURE -> "Import failed"
    }

    Box(
        modifier = modifier
            .size(sizeDp)
            .semantics { contentDescription = contentDesc },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val center = Offset(width / 2f, height / 2f)
            val outerRadius = width / 2f
            val coreCircleRadius = width * 0.32f

            when (state) {
                ImportAnimationState.IMPORTING -> {
                    // 1. Subtle pulsing outer glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                EmeraldAccent.copy(alpha = loadingGlowAlpha),
                                EmeraldAccent.copy(alpha = loadingGlowAlpha * 0.3f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = outerRadius
                        ),
                        radius = outerRadius,
                        center = center
                    )

                    // 2. Dark inner core
                    drawCircle(
                        color = Color(0xFF10151E),
                        radius = coreCircleRadius,
                        center = center
                    )

                    // 3. Faint background track
                    drawCircle(
                        color = EmeraldAccent.copy(alpha = 0.15f),
                        radius = coreCircleRadius,
                        center = center,
                        style = Stroke(width = 2.5.dp.toPx())
                    )

                    // 4. Rotating active arc
                    val strokePx = 3.dp.toPx()
                    val arcTopLeft = Offset(center.x - coreCircleRadius, center.y - coreCircleRadius)
                    val arcSize = Size(coreCircleRadius * 2, coreCircleRadius * 2)
                    drawArc(
                        color = EmeraldAccent,
                        startAngle = loadingRotation,
                        sweepAngle = 100f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }

                ImportAnimationState.SUCCESS -> {
                    val pop = circlePopScale.value
                    val currentRadius = coreCircleRadius * pop

                    // 1. Surrounding subtle green glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                EmeraldAccent.copy(alpha = 0.22f),
                                EmeraldAccent.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = outerRadius
                        ),
                        radius = outerRadius,
                        center = center
                    )

                    // 2. Solid green success circle
                    drawCircle(
                        color = EmeraldAccent,
                        radius = currentRadius,
                        center = center
                    )

                    // 3. Subtle fintech particles emitted outward during transition
                    val pProg = particlesProgress.value
                    if (pProg > 0f && pProg < 1f) {
                        val particleFade = (1f - pProg).coerceIn(0f, 1f)
                        SuccessParticles.forEach { particle ->
                            val angleRad = Math.toRadians(particle.angleDeg.toDouble())
                            val travelDist = currentRadius + (particle.distanceDp.dp.toPx() * pProg)
                            val px = center.x + (cos(angleRad) * travelDist).toFloat()
                            val py = center.y + (sin(angleRad) * travelDist).toFloat()
                            val pRadius = (particle.sizeDp.dp.toPx() / 2f) * (1f - 0.25f * pProg)
                            drawCircle(
                                color = particle.color.copy(alpha = particleFade * 0.9f),
                                radius = pRadius,
                                center = Offset(px, py)
                            )
                        }
                    }

                    // 4. Checkmark drawing via Path stroke animation
                    val checkProg = checkmarkDrawProgress.value
                    if (checkProg > 0f) {
                        // Checkmark coordinates relative to center and currentRadius
                        val p1 = Offset(center.x - currentRadius * 0.42f, center.y + currentRadius * 0.02f)
                        val p2 = Offset(center.x - currentRadius * 0.10f, center.y + currentRadius * 0.38f)
                        val p3 = Offset(center.x + currentRadius * 0.44f, center.y - currentRadius * 0.32f)

                        val seg1Len = kotlin.math.hypot(p2.x - p1.x, p2.y - p1.y)
                        val seg2Len = kotlin.math.hypot(p3.x - p2.x, p3.y - p2.y)
                        val totalLen = seg1Len + seg2Len
                        val seg1Fraction = seg1Len / totalLen

                        val checkPath = Path()
                        checkPath.moveTo(p1.x, p1.y)

                        if (checkProg <= seg1Fraction) {
                            val localT = (checkProg / seg1Fraction).coerceIn(0f, 1f)
                            val curX = p1.x + (p2.x - p1.x) * localT
                            val curY = p1.y + (p2.y - p1.y) * localT
                            checkPath.lineTo(curX, curY)
                        } else {
                            checkPath.lineTo(p2.x, p2.y)
                            val localT = ((checkProg - seg1Fraction) / (1f - seg1Fraction)).coerceIn(0f, 1f)
                            val curX = p2.x + (p3.x - p2.x) * localT
                            val curY = p2.y + (p3.y - p2.y) * localT
                            checkPath.lineTo(curX, curY)
                        }

                        drawPath(
                            path = checkPath,
                            color = Color.White,
                            style = Stroke(
                                width = 3.2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                ImportAnimationState.FAILURE -> {
                    // Soft red circle with X
                    drawCircle(
                        color = Color(0xFFEF4444),
                        radius = coreCircleRadius,
                        center = center
                    )
                    val xOffset = coreCircleRadius * 0.35f
                    val strokePx = 3.dp.toPx()
                    drawLine(
                        color = Color.White,
                        start = Offset(center.x - xOffset, center.y - xOffset),
                        end = Offset(center.x + xOffset, center.y + xOffset),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(center.x + xOffset, center.y - xOffset),
                        end = Offset(center.x - xOffset, center.y + xOffset),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

/**
 * Rebuilt SMS Import Completion Screen matching Ui-Designs/ImportCompletePage.png exactly.
 *
 * Content is scrollable for safe fit across all device form factors while the primary CTA
 * is pinned statically at the bottom.
 */
@Composable
private fun SmsImportCompletionContent(
    animationState: ImportAnimationState,
    importResult: SmsImportResult.Success?,
    scanResult: SmsScanResult.Success?,
    selectedGroupIds: Set<String>,
    isRealTimeIngestionActive: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onCompleteGoHome: () -> Unit,
    onNavigateToReview: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isImporting = animationState == ImportAnimationState.IMPORTING

    val insertedCount = importResult?.insertedCount ?: run {
        val groups = scanResult?.accountGroups?.filter { selectedGroupIds.contains(it.groupId) }
        groups?.sumOf { it.transactionCount } ?: 0
    }
    val totalDebits = importResult?.totalAmountImported ?: run {
        val groups = scanResult?.accountGroups?.filter { selectedGroupIds.contains(it.groupId) }
        groups?.sumOf { it.totalDebit } ?: 0.0
    }
    val totalCredits = importResult?.totalIncomeImported ?: run {
        val groups = scanResult?.accountGroups?.filter { selectedGroupIds.contains(it.groupId) }
        groups?.sumOf { it.totalCredit } ?: 0.0
    }
    val netBalance = totalCredits - totalDebits

    val debitCount = remember(importResult, scanResult, selectedGroupIds) {
        if (importResult != null) {
            importResult.persistenceResults.count { res ->
                when (res) {
                    is ExpensePersistenceResult.Inserted -> !res.expense.type.equals("Credit", ignoreCase = true)
                    is ExpensePersistenceResult.ReviewPending -> !res.expense.type.equals("Credit", ignoreCase = true)
                    else -> false
                }
            }.takeIf { it > 0 } ?: (if (totalDebits > 0) insertedCount else 0)
        } else {
            scanResult?.accountGroups
                ?.filter { selectedGroupIds.contains(it.groupId) }
                ?.sumOf { group -> group.transactions.count { it.candidate.candidate.direction != TransactionDirection.CREDIT } }
                ?: 0
        }
    }

    val creditCount = remember(importResult, scanResult, selectedGroupIds) {
        if (importResult != null) {
            importResult.persistenceResults.count { res ->
                when (res) {
                    is ExpensePersistenceResult.Inserted -> res.expense.type.equals("Credit", ignoreCase = true)
                    is ExpensePersistenceResult.ReviewPending -> res.expense.type.equals("Credit", ignoreCase = true)
                    else -> false
                }
            }.takeIf { it > 0 } ?: 0
        } else {
            scanResult?.accountGroups
                ?.filter { selectedGroupIds.contains(it.groupId) }
                ?.sumOf { group -> group.transactions.count { it.candidate.candidate.direction == TransactionDirection.CREDIT } }
                ?: 0
        }
    }

    val messagesScanned = scanResult?.messagesScanned ?: (importResult?.totalProcessed ?: 0)
    val duplicatesFiltered = importResult?.duplicatesSkippedCount ?: (scanResult?.duplicatesCount ?: 0)
    val spamAndOtps = scanResult?.noiseMessages ?: 0
    val reviewCount = importResult?.reviewPendingCount ?: 0

    val reconciledAccounts = remember(scanResult, importResult, selectedGroupIds) {
        val groups = scanResult?.accountGroups ?: emptyList()
        val activeGroups = if (importResult?.selectedGroupIds != null) {
            groups.filter { importResult.selectedGroupIds.contains(it.groupId) }
        } else {
            groups.filter { selectedGroupIds.contains(it.groupId) }
        }
        activeGroups.mapNotNull { group ->
            group.identity.institutionName?.let { name ->
                when {
                    name.contains("HDFC", ignoreCase = true) -> "HDFC"
                    name.contains("SBI", ignoreCase = true) || name.contains("State Bank", ignoreCase = true) -> "SBI"
                    name.contains("Axis", ignoreCase = true) -> "Axis"
                    name.contains("ICICI", ignoreCase = true) -> "ICICI"
                    name.contains("Kotak", ignoreCase = true) -> "Kotak"
                    name.contains("PNB", ignoreCase = true) || name.contains("Punjab National", ignoreCase = true) -> "PNB"
                    name.contains("BOB", ignoreCase = true) || name.contains("Bank of Baroda", ignoreCase = true) -> "BOB"
                    else -> name.split(" ").firstOrNull() ?: name
                }
            } ?: group.identity.institutionId?.uppercase()
              ?: (if (group.groupId != "unidentified_account") group.groupId.uppercase() else null)
        }.distinct()
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // ----------------------------------------------------
        // Scrollable Body Content
        // ----------------------------------------------------
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. HERO AREA with ImportCompletionAnimation
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Success / Loading Animation
                ImportCompletionAnimation(
                    state = animationState,
                    sizeDp = 68.dp
                )

                Spacer(modifier = Modifier.height(2.dp))

                // 100% LOCAL VAULT INITIALIZED Badge
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF082215),
                    border = BorderStroke(1.dp, Color(0xFF164E35))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = EmeraldAccent,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "100% LOCAL VAULT",
                                color = EmeraldAccent,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                lineHeight = 10.sp
                            )
                            Text(
                                text = if (isImporting) "INITIALIZING" else "INITIALIZED",
                                color = EmeraldAccent,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                lineHeight = 10.sp
                            )
                        }
                    }
                }

                // Main Headline
                Text(
                    text = if (isImporting) "Ingesting $insertedCount Transactions..." else "$insertedCount Transactions Ingested",
                    color = TextWhite,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.2).sp
                )

                // Explanatory Subtitle
                Text(
                    text = if (isImporting) {
                        "Parsing, deduplicating, and securely committing selected SMS records to your local encrypted SQLite Room ledger."
                    } else {
                        "Historical SMS data parsed, deduplicated, and securely committed to your local encrypted SQLite Room ledger."
                    },
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }

            // 2. INGESTION AUDIT REPORT Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Layers,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "INGESTION AUDIT",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    lineHeight = 11.sp
                                )
                                Text(
                                    text = "REPORT",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    lineHeight = 11.sp
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = Color(0xFF161C26),
                            border = BorderStroke(1.dp, Color(0xFF222C3D))
                        ) {
                            Text(
                                text = "Room v${AppDatabase.DATABASE_VERSION} • AES-256",
                                color = Color(0xFF64748B),
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // 2-Column Metric Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Column 1
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Column {
                                Text("Messages Scanned", fontSize = 9.5.sp, color = TextMuted)
                                Text(
                                    text = formatIndianNumber(messagesScanned),
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextWhite,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column {
                                Text("Duplicates Filtered", fontSize = 9.5.sp, color = TextMuted)
                                Text(
                                    text = "${formatIndianNumber(duplicatesFiltered)} suppressed",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFCBD5E1),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Column 2
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Column {
                                Text("Financial Entries", fontSize = 9.5.sp, color = TextMuted)
                                Text(
                                    text = "${formatIndianNumber(insertedCount)} records",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldAccent,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column {
                                Text("Spam & OTPs", fontSize = 9.5.sp, color = TextMuted)
                                Text(
                                    text = "${formatIndianNumber(spamAndOtps)} ignored",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF94A3B8),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Reconciled Accounts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Reconciled Accounts (${reconciledAccounts.size})",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val visibleAccounts = reconciledAccounts.take(3)
                            val remainingCount = reconciledAccounts.size - visibleAccounts.size
                            visibleAccounts.forEach { acc ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF1E2433),
                                    border = BorderStroke(1.dp, Color(0xFF2E394F))
                                ) {
                                    Text(
                                        text = acc,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (remainingCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF1E2433),
                                    border = BorderStroke(1.dp, Color(0xFF2E394F))
                                ) {
                                    Text(
                                        text = "+$remainingCount",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. HISTORICAL BALANCE DELTA Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.AccountBalanceWallet,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "HISTORICAL BALANCE",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    lineHeight = 11.sp
                                )
                                Text(
                                    text = "DELTA",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    lineHeight = 11.sp
                                )
                            }
                        }

                        val netPrefix = if (netBalance > 0) "+₹" else if (netBalance < 0) "-₹" else "₹"
                        val netText = "$netPrefix${formatIndianCurrency(Math.abs(netBalance))} net"
                        Text(
                            text = netText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (netBalance >= 0) EmeraldAccent else Color(0xFFF87171)
                        )
                    }

                    // Debits and Credits summary blocks
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Debits
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF241318),
                            border = BorderStroke(1.dp, Color(0xFF451922))
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text("Total Debits tracked", fontSize = 9.5.sp, color = Color(0xFFF87171), maxLines = 1)
                                Text(
                                    text = "-₹${formatIndianCurrency(totalDebits)}",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444),
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                                Text("$debitCount debits", fontSize = 9.5.sp, color = Color(0xFFFCA5A5), maxLines = 1)
                            }
                        }

                        // Credits
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0F261B),
                            border = BorderStroke(1.dp, Color(0xFF1A4732))
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text("Total Credits tracked", fontSize = 9.5.sp, color = Color(0xFF34D399), maxLines = 1)
                                Text(
                                    text = "+₹${formatIndianCurrency(totalCredits)}",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldAccent,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                                Text("$creditCount deposits", fontSize = 9.5.sp, color = Color(0xFF86EFAC), maxLines = 1)
                            }
                        }
                    }

                    // Review / Triage Warning Strip
                    if (reviewCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF2A1C08),
                            border = BorderStroke(1.dp, Color(0xFF4D3410))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = AmberAccent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$reviewCount ${if (reviewCount == 1) "item requires" else "items require"} categorization tria...",
                                        fontSize = 10.sp,
                                        color = Color(0xFFFDE68A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Review",
                                    color = AmberAccent,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onNavigateToReview() }
                                )
                            }
                        }
                    }
                }
            }

            // 4. NEXT ACTIONS Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "NEXT ACTIONS",
                    fontSize = 9.sp,
                    color = Color(0xFF64748B),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                // Review Triage Inbox card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToReview() },
                    shape = RoundedCornerShape(10.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (reviewCount > 0) Color(0xFF281C09) else Color(0xFF0F261B))
                                .border(
                                    BorderStroke(1.dp, if (reviewCount > 0) Color(0xFF4A3412) else Color(0xFF1A4732)),
                                    RoundedCornerShape(7.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (reviewCount > 0) Icons.Outlined.Warning else Icons.Filled.Check,
                                contentDescription = null,
                                tint = if (reviewCount > 0) AmberAccent else EmeraldAccent,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(9.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Review Triage Inbox",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = if (reviewCount > 0) "$reviewCount UPI merchant transactions unmapped" else "All transactions categorized and mapped",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (reviewCount > 0) "Review →" else "Clean ✓",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (reviewCount > 0) AmberAccent else EmeraldAccent
                        )
                    }
                }

                // Real-Time Ingestion card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onOpenNotificationSettings()
                        },
                    shape = RoundedCornerShape(10.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (isRealTimeIngestionActive) Color(0xFF0F261B) else Color(0xFF1A1F2B))
                                .border(
                                    BorderStroke(1.dp, if (isRealTimeIngestionActive) Color(0xFF1A4732) else Color(0xFF2E384D)),
                                    RoundedCornerShape(7.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Sensors,
                                contentDescription = null,
                                tint = if (isRealTimeIngestionActive) EmeraldAccent else Color(0xFF94A3B8),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(9.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Real-Time Ingestion",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                            Text(
                                text = "Local zero-network daemon monitor",
                                fontSize = 10.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isRealTimeIngestionActive) EmeraldAccent else Color(0xFF64748B))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isRealTimeIngestionActive) "Active" else "Inactive",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isRealTimeIngestionActive) EmeraldAccent else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------
        // Pinned Static Bottom Actions Bar
        // ----------------------------------------------------
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // PRIMARY CTA: Complete & Go to Home ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .shadow(
                            elevation = if (isImporting) 0.dp else 4.dp,
                            shape = RoundedCornerShape(12.dp),
                            spotColor = Color(0x66000000)
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !isImporting) { onCompleteGoHome() },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isImporting) Color(0xFF1E2433) else Color.White
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isImporting) "Committing to Ledger..." else "Complete & Go to Home",
                            color = if (isImporting) Color(0xFF64748B) else Color(0xFF090C10),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.1.sp
                        )
                        if (!isImporting) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color(0xFF090C10),
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(16.dp)
                            )
                        }
                    }
                }

                // EXPORT AUDIT LOG (CSV)
                if (!isImporting) {
                    Text(
                        text = "Export Ingestion Audit Log (CSV)",
                        color = Color(0xFF64748B),
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onExportCsv() }
                            .padding(vertical = 2.dp, horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

