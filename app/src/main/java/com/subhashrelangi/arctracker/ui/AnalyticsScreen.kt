package com.subhashrelangi.arctracker.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.CategoryVisuals
import com.subhashrelangi.arctracker.service.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit = {},
    analyticsManager: AnalyticsManager? = null
) {
    val context = LocalContext.current
    val manager = remember {
        analyticsManager ?: run {
            val db = AppDatabase.getDatabase(context)
            AnalyticsManager(
                expenseDao = db.expenseDao(),
                categoryDao = db.transactionCategoryDao(),
                accountDao = db.knownFinancialAccountDao()
            )
        }
    }

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
            minimumFractionDigits = 0
        }
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }

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

    Scaffold(
        topBar = {
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
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // Date Range Selector
            // ==========================================
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnalyticsDateRange.values().forEach { range ->
                        val isSelected = selectedRange == range
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedRange = range
                                if (range == AnalyticsDateRange.CUSTOM && customStartMillis == null) {
                                    val now = System.currentTimeMillis()
                                    customStartMillis = now - (30L * 24 * 3600 * 1000)
                                    customEndMillis = now
                                }
                            },
                            label = { Text(range.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // Custom Range Date Pickers
            if (selectedRange == AnalyticsDateRange.CUSTOM) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                                Text("From", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = customStartMillis?.let { dateFormatter.format(Date(it)) } ?: "Select Start",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Column(modifier = Modifier.clickable {
                                showDatePicker(customEndMillis ?: System.currentTimeMillis()) {
                                    customEndMillis = it
                                }
                            }) {
                                Text("To", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = customEndMillis?.let { dateFormatter.format(Date(it)) } ?: "Select End",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            val report = reportState
            if (report == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (report.isEmpty) {
                // ==========================================
                // Empty State
                // ==========================================
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.QueryStats,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Transactions Found",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "There are no recorded transactions within the ${selectedRange.label.lowercase()} timeframe.",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // ==========================================
                // 1. Basic Financial Summary
                // ==========================================
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F5FC))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                text = "Financial Overview",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF673AB7)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Expenses", fontSize = 12.sp, color = Color.Gray)
                                    Text(
                                        text = currencyFormatter.format(report.summary.totalExpenses),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD32F2F)
                                    )
                                    Text("${report.summary.expenseCount} transactions", fontSize = 11.sp, color = Color.Gray)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Income", fontSize = 12.sp, color = Color.Gray)
                                    Text(
                                        text = currencyFormatter.format(report.summary.totalIncome),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                    Text("${report.summary.incomeCount} transactions", fontSize = 11.sp, color = Color.Gray)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = Color(0xFFE0E0E0))
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Net Cash Flow", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                val net = report.summary.netAmount
                                val netColor = if (net >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                val netPrefix = if (net > 0) "+" else ""
                                Text(
                                    text = "$netPrefix${currencyFormatter.format(net)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = netColor
                                )
                            }

                            // Notice for Pending and Self-Transfers
                            if (report.summary.pendingCount > 0 || report.summary.selfTransferCount > 0) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (report.summary.pendingCount > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFFFF3E0),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = "${report.summary.pendingCount} Pending (${currencyFormatter.format(report.summary.pendingAmount)})",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFFE65100),
                                                modifier = Modifier.padding(6.dp)
                                            )
                                        }
                                    }
                                    if (report.summary.selfTransferCount > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFE1F5FE),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = "${report.summary.selfTransferCount} Transfers Excluded",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF0277BD),
                                                modifier = Modifier.padding(6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 2. Category Spending Breakdown
                // ==========================================
                item {
                    Text(
                        text = "Spending by Category",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (report.categoryBreakdown.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "No categorized spending recorded in this period.",
                                modifier = Modifier.padding(16.dp),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    items(report.categoryBreakdown) { cat ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, Color(0xFFF0F0F0))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val icon = CategoryVisuals.getIcon(cat.iconKey)
                                    val color = CategoryVisuals.getColor(cat.colorKey)
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(color.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = cat.categoryName,
                                            tint = color,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = cat.categoryName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            if (cat.isArchived) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("(Archived)", fontSize = 10.sp, color = Color.Gray)
                                            }
                                        }
                                        Text(
                                            text = "${cat.transactionCount} transactions • ${String.format(Locale.US, "%.1f", cat.percentage)}%",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    Text(
                                        text = currencyFormatter.format(cat.totalAmount),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { (cat.percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = CategoryVisuals.getColor(cat.colorKey),
                                    trackColor = Color(0xFFF0F0F0)
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // 3. Uncategorized Transactions
                // ==========================================
                if (report.uncategorized.transactionCount > 0) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                            border = BorderStroke(1.dp, Color(0xFFFFE082))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFFA000))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Uncategorized Transactions",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "${report.uncategorized.transactionCount} transactions (${String.format(Locale.US, "%.1f", report.uncategorized.percentage)}% of spending)",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                Text(
                                    text = currencyFormatter.format(report.uncategorized.totalAmount),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // 4. Monthly Trend Analytics
                // ==========================================
                if (report.monthlyTrends.isNotEmpty()) {
                    item {
                        Text(
                            text = "Monthly Trends",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, Color(0xFFF0F0F0))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                report.monthlyTrends.forEach { month ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(month.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("${month.transactionCount} txns", fontSize = 11.sp, color = Color.Gray)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Expenses", fontSize = 10.sp, color = Color.Gray)
                                                Text(currencyFormatter.format(month.totalExpenses), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFD32F2F))
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Income", fontSize = 10.sp, color = Color.Gray)
                                                Text(currencyFormatter.format(month.totalIncome), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                                            }
                                        }
                                    }
                                    if (month.categoryTrends.isNotEmpty()) {
                                        Text(
                                            text = "Top: " + month.categoryTrends.take(3).joinToString(" • ") { "${it.categoryName} (${currencyFormatter.format(it.amount)})" },
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    if (month != report.monthlyTrends.last()) {
                                        HorizontalDivider(color = Color(0xFFF5F5F5))
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 5. Top Merchants
                // ==========================================
                if (report.topMerchants.isNotEmpty()) {
                    item {
                        Text(
                            text = "Top Merchants",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, Color(0xFFF0F0F0))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                report.topMerchants.take(10).forEachIndexed { index, merchant ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.width(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = merchant.merchant,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "${merchant.transactionCount} transactions • ${String.format(Locale.US, "%.1f", merchant.percentage)}%",
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        Text(
                                            text = currencyFormatter.format(merchant.totalAmount),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    if (index < report.topMerchants.take(10).size - 1) {
                                        HorizontalDivider(color = Color(0xFFF5F5F5))
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 6. Category Source Attribution
                // ==========================================
                if (report.categorySources.isNotEmpty()) {
                    item {
                        Text(
                            text = "Categorization Source",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, Color(0xFFF0F0F0))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                report.categorySources.forEach { src ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(src.displayName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            Text("${src.transactionCount} transactions (${String.format(Locale.US, "%.1f", src.percentage)}%)", fontSize = 11.sp, color = Color.Gray)
                                        }
                                        Text(currencyFormatter.format(src.totalAmount), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    if (src != report.categorySources.last()) {
                                        HorizontalDivider(color = Color(0xFFF5F5F5))
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 7. Automation Effectiveness
                // ==========================================
                if (report.ruleEffectiveness.inferredCount > 0 || report.ruleEffectiveness.userAssignedCount > 0) {
                    item {
                        Text(
                            text = "Automation Effectiveness",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
                            border = BorderStroke(1.dp, Color(0xFFE1BEE7))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Auto-Categorization Rate",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF4A148C)
                                        )
                                        Text(
                                            text = "${report.ruleEffectiveness.inferredCount} of ${report.ruleEffectiveness.inferredCount + report.ruleEffectiveness.userAssignedCount} categorized txns",
                                            fontSize = 11.sp,
                                            color = Color.DarkGray
                                        )
                                    }
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", report.ruleEffectiveness.autoCategorizedPercentage)}%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = Color(0xFF6A1B9A)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { (report.ruleEffectiveness.autoCategorizedPercentage / 100.0).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = Color(0xFF8E24AA),
                                    trackColor = Color(0xFFE1BEE7)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Inferred: ${currencyFormatter.format(report.ruleEffectiveness.inferredAmount)}",
                                        fontSize = 11.sp,
                                        color = Color.DarkGray
                                    )
                                    Text(
                                        text = "Manual: ${currencyFormatter.format(report.ruleEffectiveness.userAssignedAmount)}",
                                        fontSize = 11.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 7. Account Analytics
                // ==========================================
                if (report.accountBreakdown.isNotEmpty()) {
                    item {
                        Text(
                            text = "Activity by Account",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, Color(0xFFF0F0F0))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                report.accountBreakdown.forEach { acc ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(acc.institutionName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                if (!acc.accountSuffix.isNullOrBlank()) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("••${acc.accountSuffix}", fontSize = 11.sp, color = Color.Gray)
                                                }
                                            }
                                            Text("${acc.transactionCount} transactions", fontSize = 11.sp, color = Color.Gray)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(currencyFormatter.format(acc.totalExpenses), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFD32F2F))
                                            if (acc.totalIncome > 0) {
                                                Text("+${currencyFormatter.format(acc.totalIncome)}", fontSize = 11.sp, color = Color(0xFF2E7D32))
                                            }
                                        }
                                    }
                                    if (acc != report.accountBreakdown.last()) {
                                        HorizontalDivider(color = Color(0xFFF5F5F5))
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
