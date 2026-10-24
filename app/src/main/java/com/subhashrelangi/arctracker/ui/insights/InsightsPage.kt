package com.subhashrelangi.arctracker.ui.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.AppDatabase
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
    searchQuery: String = "",
    onAddExpenseClick: () -> Unit = {},
    onAllCategoriesClick: () -> Unit = {},
    onManageRulesClick: () -> Unit = {},
    isDemoMode: Boolean = false,
    isSetupCompleted: Boolean = false,
    onStartSetup: () -> Unit = {},
    onViewLedger: () -> Unit = {},
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
    val hasAnyTransactions = remember(expenses) { expenses.any { !it.isPending } }

    // Filter pills list
    val currentMonthShort = remember { SimpleDateFormat("MMM", Locale.US).format(Date()) }
    val timeFilterPills = remember(currentMonthShort) {
        listOf(
            "This Month ($currentMonthShort)",
            "Last 30 Days",
            "Quarter 3",
            "Year 2026",
            "All Time"
        )
    }
    var selectedPill by remember { mutableStateOf("This Month ($currentMonthShort)") }

    val selectedDateRange = remember(selectedPill) {
        when {
            selectedPill.startsWith("This Month") -> AnalyticsDateRange.THIS_MONTH
            selectedPill.contains("30 Days") -> AnalyticsDateRange.LAST_MONTH
            selectedPill.contains("Quarter") -> AnalyticsDateRange.LAST_3_MONTHS
            else -> AnalyticsDateRange.ALL_TIME
        }
    }

    val reportState by produceState<AnalyticsReport?>(
        initialValue = null,
        key1 = selectedDateRange
    ) {
        manager.getAnalyticsReportFlow(dateRange = selectedDateRange).collect { value = it }
    }

    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }

    val report = reportState
    val velocityData = remember(report, expenses, isDemoMode) {
        if (isDemoMode) {
            NetPositionVelocityData(
                netAmount = 48250.0,
                totalInflow = 85000.0,
                totalOutflow = 36750.0,
                savingsRate = 56.7,
                savingsStatus = "Balanced",
                dailyRunRate = 1531.0,
                dailyRunRateChangeText = "↓ 12% vs Sept",
                isDailyRatePositive = true,
                predictedMonthEnd = 47461.0,
                targetStatusText = "Under ₹75k Target",
                isTargetAchieved = true
            )
        } else if (report != null) {
            InsightsCalculations.computeNetPositionVelocity(report)
        } else {
            NetPositionVelocityData(
                netAmount = 0.0,
                totalInflow = 0.0,
                totalOutflow = 0.0,
                savingsRate = 0.0,
                savingsStatus = "Neutral",
                dailyRunRate = 0.0,
                dailyRunRateChangeText = "",
                isDailyRatePositive = true,
                predictedMonthEnd = 0.0,
                targetStatusText = "On Track",
                isTargetAchieved = true
            )
        }
    }

    val allocationData = remember(report, expenses, searchQuery, isDemoMode) {
        val baseData = if (isDemoMode) {
            CategoryAllocationData(
                isEmpty = false,
                totalSpendFormatted = "36.8k",
                primaryDriverName = "Food & Dining",
                primaryDriverPercentage = 34.0
            )
        } else if (report != null && report.categoryBreakdown.isNotEmpty()) {
            InsightsCalculations.computeCategoryAllocation(report, expenses)
        } else {
            CategoryAllocationData(
                isEmpty = true,
                totalSpendFormatted = "0",
                primaryDriverName = "No Spending Yet",
                primaryDriverPercentage = 0.0
            )
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            val filtered = baseData.categories.filter { it.name.lowercase(Locale.ROOT).contains(q) }
            baseData.copy(categories = filtered)
        } else {
            baseData
        }
    }

    // Weekly Outflow calculation for real data
    val weeklyOutflows = remember(expenses, report, isDemoMode) {
        if (isDemoMode || report == null) {
            emptyList()
        } else {
            val cal = Calendar.getInstance()
            val weekly = DoubleArray(4) { 0.0 }
            val inRangeDebits = expenses.filter {
                it.type.equals("Debit", ignoreCase = true) && !it.isPending && it.dateMillis in report.startDateMillis..report.endDateMillis
            }
            for (exp in inRangeDebits) {
                cal.timeInMillis = exp.dateMillis
                val day = cal.get(Calendar.DAY_OF_MONTH)
                val weekIndex = when {
                    day <= 7 -> 0
                    day <= 14 -> 1
                    day <= 21 -> 2
                    else -> 3
                }
                weekly[weekIndex] += exp.amount
            }
            weekly.toList()
        }
    }

    val content: @Composable (PaddingValues) -> Unit = { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Condition A: User has NOT completed setup and is NOT in demo mode -> Show empty setup page
                if (!isSetupCompleted && !isDemoMode) {
                    item(key = "empty_setup_state") {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF1F5F9))
                                        .border(1.5.dp, Color(0xFFCBD5E1), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Leaderboard,
                                        contentDescription = "No Insights",
                                        tint = Color(0xFF0F172A),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Text(
                                    text = "No Insights Available",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Setup has not been completed yet. Connect your SMS or bank notifications to see real-time outflow velocity, category breakdowns, and spending limits.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 19.sp
                                )

                                Spacer(modifier = Modifier.height(22.dp))

                                Button(
                                    onClick = onStartSetup,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 11.dp)
                                ) {
                                    Text(
                                        text = "Start Setup",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    )
                                }
                            }
                        }
                    }

                    // Privacy Footer
                    item(key = "card_zero_telemetry_footer") {
                        ZeroTelemetryFooter(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                // Condition B: User completed setup but has no transactions yet -> Show empty transactions state
                else if (isSetupCompleted && !hasAnyTransactions && !isDemoMode) {
                    item(key = "empty_transactions_state") {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFECFDF5))
                                        .border(1.5.dp, Color(0xFFA7F3D0), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Leaderboard,
                                        contentDescription = "No Transactions",
                                        tint = Color(0xFF047857),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Text(
                                    text = "No Transactions in this Period",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "There are no recorded expenses or income for this timeframe. Add an expense or import banking alerts to see on-device spending analytics.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 19.sp
                                )

                                Spacer(modifier = Modifier.height(22.dp))

                                Button(
                                    onClick = onAddExpenseClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 11.dp)
                                ) {
                                    Text(
                                        text = "Add Expense",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    )
                                }
                            }
                        }
                    }

                    // Privacy Footer
                    item(key = "card_zero_telemetry_footer") {
                        ZeroTelemetryFooter(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                // Condition C: Demo Mode OR Real Data with Transactions -> Show full rich Insights dashboard
                else {
                    // 1. Time Filter Horizontal Scroll Row
                    item(key = "time_filter_row") {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(timeFilterPills) { pillLabel ->
                                val isSelected = selectedPill == pillLabel
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (isSelected) InsightsTheme.CharcoalDark else Color.White)
                                        .border(
                                            BorderStroke(1.dp, if (isSelected) InsightsTheme.CharcoalDark else Color(0xFFE2E8F0)),
                                            RoundedCornerShape(999.dp)
                                        )
                                        .clickable { selectedPill = pillLabel }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = pillLabel,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFF334155)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Main Outflow & Weekly Pace Card
                    item(key = "card_monthly_outflow_pace") {
                        MonthlyOutflowPaceCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            totalOutflow = velocityData.totalOutflow,
                            budgetCap = if (isDemoMode) 75000.0 else maxOf(velocityData.predictedMonthEnd, 50000.0),
                            previousMonthName = "Sept",
                            percentageVsPrevious = 12,
                            weeklyOutflows = weeklyOutflows,
                            isDemoMode = isDemoMode
                        )
                    }

                    // 3. Net Savings & Daily Burn Row
                    item(key = "card_net_savings_daily_burn") {
                        NetSavingsDailyBurnRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            netSavings = velocityData.netAmount,
                            savingsRatePercent = velocityData.savingsRate,
                            dailyBurn = velocityData.dailyRunRate,
                            dailyLimit = 2500.0,
                            isDemoMode = isDemoMode
                        )
                    }

                    // 4. Category Allocation Card
                    item(key = "card_category_allocation") {
                        CategoryAllocationCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            data = allocationData,
                            currencyFormatter = currencyFormatter,
                            isDemoMode = isDemoMode,
                            onAllCategoriesClick = onAllCategoriesClick
                        )
                    }

                    // 5. Arc Engine Intelligence Card
                    item(key = "card_arc_engine_intelligence") {
                        ArcEngineIntelligenceCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            isDemoMode = isDemoMode,
                            topCategoryName = allocationData.primaryDriverName,
                            topCategoryPercent = allocationData.primaryDriverPercentage.toInt(),
                            transactionCount = expenses.count { !it.isPending },
                            onManageRulesClick = onManageRulesClick
                        )
                    }

                    // 6. Zero-Telemetry Environment Privacy Footer
                    item(key = "card_zero_telemetry_footer") {
                        ZeroTelemetryFooter(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // Floating "+ Add Expense" Pill Button on bottom-right
            Surface(
                onClick = onAddExpenseClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 72.dp)
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(999.dp)),
                shape = RoundedCornerShape(999.dp),
                color = InsightsTheme.CharcoalDark,
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Expense",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Expense",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }
    }

    if (showHeader) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Analytics & Insights",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = InsightsTheme.LightTextPrimary
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = InsightsTheme.LightBackground)
                )
            },
            containerColor = InsightsTheme.LightBackground,
            content = content
        )
    } else {
        content(PaddingValues(0.dp))
    }
}
