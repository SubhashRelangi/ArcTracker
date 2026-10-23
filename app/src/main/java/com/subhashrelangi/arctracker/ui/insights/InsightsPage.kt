package com.subhashrelangi.arctracker.ui.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    searchQuery: String = "",
    onAddExpenseClick: () -> Unit = {},
    onAllCategoriesClick: () -> Unit = {},
    onManageRulesClick: () -> Unit = {},
    isDemoMode: Boolean = false,
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
    val velocityData = remember(report, expenses) {
        if (report != null && report.summary.totalExpenses > 0.0) {
            InsightsCalculations.computeNetPositionVelocity(report)
        } else {
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
        }
    }

    val allocationData = remember(report, expenses, searchQuery) {
        val baseData = if (report != null && report.categoryBreakdown.isNotEmpty()) {
            InsightsCalculations.computeCategoryAllocation(report, expenses)
        } else {
            CategoryAllocationData(
                isEmpty = false,
                totalSpendFormatted = "36.8k",
                primaryDriverName = "Food & Dining",
                primaryDriverPercentage = 34.0
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
                // 1. Time Filter Horizontal Scroll Row (Edge-to-Edge with 16dp content padding)
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
                        budgetCap = 75000.0,
                        previousMonthName = "Sept",
                        percentageVsPrevious = 12
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
                        dailyLimit = 2500.0
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
                        onAllCategoriesClick = onAllCategoriesClick
                    )
                }

                // 5. Arc Engine Intelligence Card
                item(key = "card_arc_engine_intelligence") {
                    ArcEngineIntelligenceCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
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

            // 7. Floating "+ Add Expense" Pill Button on bottom-right
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showHeader) {
        Scaffold(
            containerColor = InsightsTheme.LightBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Analytics & Insights",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = InsightsTheme.LightTextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = InsightsTheme.LightTextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = InsightsTheme.LightBackground
                    )
                )
            }
        ) { innerPadding ->
            content(innerPadding)
        }
    } else {
        content(PaddingValues(0.dp))
    }
}
