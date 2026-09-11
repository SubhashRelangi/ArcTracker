package com.example.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arctracker.data.Expense
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(expenses: List<Expense>, onExpenseClick: (Expense) -> Unit) {
    var selectedTabIndex by remember { mutableStateOf(0) } // 0 = All, 1 = Expenses, 2 = Income
    var selectedTimePeriod by remember { mutableStateOf("This Month") }
    var showTimePeriodDropdown by remember { mutableStateOf(false) }
    
    val timePeriods = listOf("This Month", "3 Months", "6 Months", "1 Year", "Custom")
    
    // Calculate date ranges
    val calendar = Calendar.getInstance()
    val now = calendar.timeInMillis
    val startTime = when (selectedTimePeriod) {
        "This Month" -> {
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.timeInMillis
        }
        "3 Months" -> {
            calendar.add(Calendar.MONTH, -3)
            calendar.timeInMillis
        }
        "6 Months" -> {
            calendar.add(Calendar.MONTH, -6)
            calendar.timeInMillis
        }
        "1 Year" -> {
            calendar.add(Calendar.YEAR, -1)
            calendar.timeInMillis
        }
        else -> 0L // Custom could be implemented later with DatePickerDialog
    }

    // Filter expenses
    val filteredExpenses = expenses.filter { expense ->
        val inTimeRange = expense.dateMillis >= startTime && expense.dateMillis <= now
        val typeMatch = when (selectedTabIndex) {
            1 -> expense.type == "Debit"
            2 -> expense.type == "Credit"
            else -> true
        }
        inTimeRange && typeMatch
    }

    val totalIncome = filteredExpenses.filter { it.type == "Credit" }.sumOf { it.amount }
    val totalExpense = filteredExpenses.filter { it.type == "Debit" }.sumOf { it.amount }
    val totalBalance = totalIncome - totalExpense
    val incomeCount = filteredExpenses.count { it.type == "Credit" }
    val expenseCount = filteredExpenses.count { it.type == "Debit" }

    // Grouping by Date
    val dateFormat = SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault())
    val todayStr = dateFormat.format(Date())
    calendar.timeInMillis = now
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val yesterdayStr = dateFormat.format(calendar.time)

    val groupedExpenses = filteredExpenses.groupBy { 
        val dateStr = dateFormat.format(Date(it.dateMillis))
        when (dateStr) {
            todayStr -> "Today • ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it.dateMillis))}"
            yesterdayStr -> "Yesterday • ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it.dateMillis))}"
            else -> dateStr
        }
    }.toSortedMap(compareByDescending { 
        // Need a reliable way to sort, falling back to string parsing if needed
        // Since we know the groups map to dates, we can sort them by the max timestamp in each group
        it 
    }) // Hack: to properly sort we should group by a truncated timestamp.
    
    // Better grouping for sorting:
    val groupedByDate = filteredExpenses.groupBy {
        val cal = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }.toSortedMap(compareByDescending { it })

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFBF8FF)) // Very light purple/gray from image
    ) {
        // Custom Header
        Column(modifier = Modifier.padding(16.dp)) {


            // Filter Row (Chips)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tabs
                listOf("All", "Expenses", "Income").forEachIndexed { index, title ->
                    val isSelected = selectedTabIndex == index
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF673AB7) else Color(0xFFF0EDF5),
                        onClick = { selectedTabIndex = index }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = title,
                                color = if (isSelected) Color.White else Color(0xFF424242),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Time Period Dropdown
                Box(modifier = Modifier.weight(1.2f)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF0EDF5),
                        onClick = { showTimePeriodDropdown = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(selectedTimePeriod, fontSize = 13.sp, color = Color(0xFF424242), fontWeight = FontWeight.Medium)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF424242))
                        }
                    }
                    DropdownMenu(
                        expanded = showTimePeriodDropdown,
                        onDismissRequest = { showTimePeriodDropdown = false }
                    ) {
                        timePeriods.forEach { period ->
                            DropdownMenuItem(
                                text = { Text(period) },
                                onClick = {
                                    selectedTimePeriod = period
                                    showTimePeriodDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp) // Subtle shadow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Total Balance
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text("Total Balance", fontSize = 12.sp, color = Color(0xFF757575))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                currencyFormatter.format(totalBalance).replace("Rs.", "₹"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = Color(0xFF1E1E1E)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.VisibilityOff, contentDescription = "Hide", tint = Color(0xFF9E9E9E), modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(12.dp))
                            Text("+12% from last month", fontSize = 10.sp, color = Color(0xFF757575))
                        }
                    }
                    
                    // Divider
                    Box(modifier = Modifier.width(1.dp).height(60.dp).background(Color(0xFFEEEEEE)))
                    
                    // Income / Expenses
                    Row(
                        modifier = Modifier
                            .weight(1.5f)
                            .padding(start = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Income", fontSize = 12.sp, color = Color(0xFF757575))
                            Text(
                                currencyFormatter.format(totalIncome).replace("Rs.", "₹"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF4CAF50)
                            )
                            Text("$incomeCount transactions", fontSize = 10.sp, color = Color(0xFF9E9E9E))
                        }
                        Column {
                            Text("Expenses", fontSize = 12.sp, color = Color(0xFF757575))
                            Text(
                                currencyFormatter.format(totalExpense).replace("Rs.", "₹"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFFD32F2F)
                            )
                            Text("$expenseCount transactions", fontSize = 10.sp, color = Color(0xFF9E9E9E))
                        }
                    }
                }
            }
        }

        // Transactions List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp) // padding for FAB/BottomNav
        ) {
            groupedByDate.forEach { (dateMillis, expensesForDate) ->
                item {
                    val dateStr = dateFormat.format(Date(dateMillis))
                    val headerText = when (dateStr) {
                        todayStr -> "Today • ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(dateMillis))}"
                        yesterdayStr -> "Yesterday • ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(dateMillis))}"
                        else -> dateStr
                    }
                    val dailyTotal = expensesForDate.sumOf { if (it.type == "Debit") -it.amount else it.amount }
                    val dailyTotalColor = if (dailyTotal >= 0) Color(0xFF4CAF50) else Color(0xFFD32F2F)
                    val sign = if (dailyTotal > 0) "+" else ""
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            headerText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1E1E1E),
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF5F5F5)
                        ) {
                            Text(
                                "${expensesForDate.size} transactions",
                                fontSize = 11.sp,
                                color = Color(0xFF757575),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "$sign${currencyFormatter.format(dailyTotal).replace("Rs.", "₹")}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = dailyTotalColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color(0xFF757575), modifier = Modifier.size(16.dp))
                    }
                }

                items(expensesForDate) { expense ->
                    TransactionItemRow(expense, onClick = { onExpenseClick(expense) })
                }
            }
        }
    }
}

@Composable
fun TransactionItemRow(expense: Expense, onClick: () -> Unit) {
    val isCredit = expense.type == "Credit"
    val iconBgColor = if (isCredit) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    val iconColor = if (isCredit) Color(0xFF4CAF50) else Color(0xFFF44336)
    val icon = if (isCredit) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown
    val amountColor = if (isCredit) Color(0xFF4CAF50) else Color(0xFFD32F2F)
    val sign = if (isCredit) "+" else "-"
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeStr = timeFormat.format(Date(expense.dateMillis))

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconBgColor, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = expense.type,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.merchant.ifEmpty { "Unknown" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF1E1E1E),
                    maxLines = 1
                )
                Text(
                    text = "$timeStr • ${expense.source}",
                    fontSize = 12.sp,
                    color = Color(0xFF757575)
                )
            }
            
            // Category Chip (Using "tag" or just default)
            val category = expense.tag?.takeIf { it.isNotBlank() } ?: "General"
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF3E5F5), // Light purple
                border = BorderStroke(1.dp, Color(0xFFE1BEE7))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(category, fontSize = 11.sp, color = Color(0xFF673AB7), fontWeight = FontWeight.Medium)
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Amount
            Text(
                text = "$sign${currencyFormatter.format(expense.amount).replace("Rs.", "₹")}",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = amountColor
            )
            
            // More Vert icon (using three dots)
            IconButton(onClick = { /* TODO */ }, modifier = Modifier.size(24.dp).padding(start = 8.dp)) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color(0xFF9E9E9E),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
