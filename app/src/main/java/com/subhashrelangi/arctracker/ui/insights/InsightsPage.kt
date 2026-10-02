package com.subhashrelangi.arctracker.ui.insights

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.service.AnalyticsDateRange
import com.subhashrelangi.arctracker.service.AnalyticsManager
import com.subhashrelangi.arctracker.service.AnalyticsReport
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsPage(
    analyticsManager: AnalyticsManager? = null,
    showHeader: Boolean = false,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }

    val manager = remember {
        analyticsManager ?: AnalyticsManager(
            expenseDao = database.expenseDao(),
            categoryDao = database.transactionCategoryDao(),
            accountDao = database.knownFinancialAccountDao()
        )
    }

    val expenses by database.expenseDao().getAllExpenses().collectAsState(initial = emptyList())

    var selectedRange by remember { mutableStateOf(AnalyticsDateRange.THIS_MONTH) }
    var customStartMillis by remember { mutableStateOf<Long?>(null) }
    var customEndMillis by remember { mutableStateOf<Long?>(null) }

    val reportState by produceState<AnalyticsReport?>(
        initialValue = null,
        key1 = selectedRange,
        key2 = customStartMillis,
        key3 = customEndMillis
    ) {
        manager.getAnalyticsReportFlow(
            dateRange = selectedRange,
            customStartMillis = customStartMillis,
            customEndMillis = customEndMillis
        ).collect { value = it }
    }

    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 2
        }
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    val currentMonthYear = remember { SimpleDateFormat("MMM yyyy", Locale.US).format(Date()) }

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

    val report = reportState
    val velocityData = remember(report, expenses) {
        if (report != null) {
            InsightsCalculations.computeNetPositionVelocity(report)
        } else {
            NetPositionVelocityData()
        }
    }

    val allocationData = remember(report, expenses) {
        if (report != null) {
            InsightsCalculations.computeCategoryAllocation(report, expenses)
        } else {
            CategoryAllocationData()
        }
    }

    val topPayees = remember(report, expenses) {
        if (report != null) {
            InsightsCalculations.computeTopPayees(report, expenses)
        } else {
            emptyList()
        }
    }

    val automationHealth = remember(report, expenses) {
        if (report != null) {
            InsightsCalculations.computeAutomationHealth(expenses, report)
        } else {
            AutomationHealthData()
        }
    }

    val hashIntegrity = remember(expenses) {
        InsightsCalculations.computeHashIntegrity(expenses)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (showHeader) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Analytics & Insights",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // Top Status Bar: Live Intelligence & SHA-256 On-Device
            // ==========================================
            item(key = "insights_status_banner") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(InsightsTheme.PositiveGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE LEDGER INTELLIGENCE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = InsightsTheme.PositiveGreenSubtle,
                            letterSpacing = 0.8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = InsightsTheme.WarningAmber,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SHA-256 On-Device",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InsightsTheme.WarningAmber,
                            letterSpacing = 0.4.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // ==========================================
            // Date Range Selector (Horizontally scrollable, intrinsic width)
            // ==========================================
            item(key = "insights_date_range_selector") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnalyticsDateRange.values().forEach { range ->
                        val isSelected = selectedRange == range
                        val labelText = if (range == AnalyticsDateRange.THIS_MONTH) {
                            "This Month ($currentMonthYear)"
                        } else {
                            range.label
                        }

                        Surface(
                            onClick = {
                                selectedRange = range
                                if (range == AnalyticsDateRange.CUSTOM && customStartMillis == null) {
                                    val now = System.currentTimeMillis()
                                    customStartMillis = now - (30L * 24 * 3600 * 1000)
                                    customEndMillis = now
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) Color.White else InsightsTheme.CardBackground,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) Color.White else InsightsTheme.CardBorder
                            ),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                    .wrapContentWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = labelText,
                                    color = if (isSelected) Color(0xFF0D1117) else InsightsTheme.TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // Custom Range Date Pickers (when CUSTOM selected)
            // ==========================================
            if (selectedRange == AnalyticsDateRange.CUSTOM) {
                item(key = "insights_custom_range") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = InsightsTheme.CardBackground),
                        border = BorderStroke(1.dp, InsightsTheme.CardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.clickable {
                                showDatePicker(customStartMillis ?: System.currentTimeMillis()) {
                                    customStartMillis = it
                                }
                            }) {
                                Text("From", fontSize = 11.sp, color = InsightsTheme.TextSecondary)
                                Text(
                                    text = customStartMillis?.let { dateFormatter.format(Date(it)) } ?: "Select Start",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = InsightsTheme.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Column(modifier = Modifier.clickable {
                                showDatePicker(customEndMillis ?: System.currentTimeMillis()) {
                                    customEndMillis = it
                                }
                            }) {
                                Text("To", fontSize = 11.sp, color = InsightsTheme.TextSecondary)
                                Text(
                                    text = customEndMillis?.let { dateFormatter.format(Date(it)) } ?: "Select End",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 1. Net Position Velocity Card
            // ==========================================
            item(key = "card_net_position_velocity") {
                NetPositionVelocityCard(
                    data = velocityData,
                    currencyFormatter = currencyFormatter
                )
            }

            // ==========================================
            // 2. Category Allocation Card
            // ==========================================
            item(key = "card_category_allocation") {
                CategoryAllocationCard(
                    data = allocationData,
                    currencyFormatter = currencyFormatter
                )
            }

            // ==========================================
            // 3. Top Payees & Merchants Card
            // ==========================================
            item(key = "card_top_payees") {
                TopPayeesCard(
                    items = topPayees,
                    currencyFormatter = currencyFormatter
                )
            }

            // ==========================================
            // 4. Automation Health Card
            // ==========================================
            item(key = "card_automation_health") {
                AutomationHealthCard(
                    data = automationHealth
                )
            }

            // ==========================================
            // 5. Zero Hash Collisions Card
            // ==========================================
            item(key = "card_zero_hash_collisions") {
                ZeroHashCollisionsCard(
                    data = hashIntegrity
                )
            }
        }
    }
}
