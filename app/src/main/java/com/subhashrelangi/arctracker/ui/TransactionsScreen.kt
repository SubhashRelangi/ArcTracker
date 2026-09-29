package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.KnownFinancialAccountRepository
import com.subhashrelangi.arctracker.data.TransactionCategories
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    expenses: List<Expense>,
    onExpenseClick: (Expense) -> Unit = {},
    onExpenseLongClick: (Expense) -> Unit = {},
    selectedMonth: String = "",
    availableMonths: List<MonthOption> = emptyList(),
    onMonthChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val accountRepo = remember { KnownFinancialAccountRepository(db.knownFinancialAccountDao()) }
    val reconciliationManager = remember { AccountReconciliationManager(db.expenseDao(), accountRepo, db) }
    val transactionManager = remember { TransactionManager(db.expenseDao()) }
    val categoryManager = remember { CategoryManager(db.transactionCategoryDao(), db.expenseDao(), db) }

    val knownAccounts by accountRepo.getAllFlow().collectAsState(initial = emptyList())
    val allCategories by categoryManager.getAllCategoriesFlow().collectAsState(initial = emptyList())
    val accountsMap = remember(knownAccounts) { knownAccounts.associateBy { it.id } }

    // Search and Filter States
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf(TransactionTypeFilter.ALL) }
    var selectedStatusFilter by remember { mutableStateOf(TransactionStatusFilter.ALL) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var selectedDateRange by remember { mutableStateOf(DateRangeFilter.THIS_MONTH) }
    var selectedSortBy by remember { mutableStateOf(TransactionSortBy.NEWEST_FIRST) }

    // Dropdown visibility states
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showDateRangeMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var showMonthDropdown by remember { mutableStateOf(false) }

    // Detail Dialog State
    var selectedExpenseForDetailId by remember { mutableStateOf<Int?>(null) }
    val activeDetailExpense = remember(selectedExpenseForDetailId, expenses) {
        selectedExpenseForDetailId?.let { id -> expenses.find { it.id == id } }
    }

    val selectedMonthLabel = availableMonths
        .firstOrNull { it.key == selectedMonth }
        ?.label
        ?: "This Month"

    // Calculate month start/end for month-level compatibility
    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance()
    val monthParts = selectedMonth.split("-")
    if (monthParts.size == 2) {
        calendar.set(monthParts[0].toInt(), monthParts[1].toInt() - 1, 1, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    } else {
        calendar.timeInMillis = now
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }
    val monthStartTime = calendar.timeInMillis
    calendar.add(Calendar.MONTH, 1)
    calendar.add(Calendar.MILLISECOND, -1)
    val monthEndTime = calendar.timeInMillis

    // Build composed filter
    val currentFilter = remember(
        searchQuery,
        selectedTypeFilter,
        selectedStatusFilter,
        selectedCategory,
        selectedAccountId,
        selectedDateRange,
        selectedSortBy,
        selectedMonth
    ) {
        TransactionFilter(
            searchQuery = searchQuery,
            type = selectedTypeFilter,
            status = selectedStatusFilter,
            category = selectedCategory,
            accountId = selectedAccountId,
            dateRange = if (selectedMonth.isNotBlank() && selectedDateRange == DateRangeFilter.THIS_MONTH) {
                DateRangeFilter.CUSTOM
            } else {
                selectedDateRange
            },
            customStartDateMillis = if (selectedMonth.isNotBlank() && selectedDateRange == DateRangeFilter.THIS_MONTH) monthStartTime else null,
            customEndDateMillis = if (selectedMonth.isNotBlank() && selectedDateRange == DateRangeFilter.THIS_MONTH) monthEndTime else null,
            sortBy = selectedSortBy
        )
    }

    val filteredExpenses = remember(expenses, currentFilter, accountsMap) {
        transactionManager.filterAndSort(expenses, currentFilter, accountsMap)
    }

    val totalIncome = filteredExpenses.filter { it.type.equals("Credit", ignoreCase = true) }.sumOf { it.amount }
    val totalExpense = filteredExpenses.filter { it.type.equals("Debit", ignoreCase = true) }.sumOf { it.amount }
    val totalBalance = totalIncome - totalExpense
    val incomeCount = filteredExpenses.count { it.type.equals("Credit", ignoreCase = true) }
    val expenseCount = filteredExpenses.count { it.type.equals("Debit", ignoreCase = true) }

    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val dateFormat = SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault())
    val todayStr = dateFormat.format(Date(now))
    calendar.timeInMillis = now
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val yesterdayStr = dateFormat.format(calendar.time)

    val groupedByDate = remember(filteredExpenses) {
        filteredExpenses.groupBy {
            val cal = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }.toSortedMap(compareByDescending { it })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFBF8FF))
    ) {
        // Top Section: Search Bar & Filters
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Search Bar (Parts 14 & 15)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by merchant, notes, account...", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF673AB7)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = Color.Gray)
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFF673AB7),
                    unfocusedBorderColor = Color(0xFFE0E0E0)
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Type Filter Tabs (All / Expenses / Income)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    TransactionTypeFilter.ALL to "All",
                    TransactionTypeFilter.DEBIT to "Expenses",
                    TransactionTypeFilter.CREDIT to "Income"
                ).forEach { (type, label) ->
                    val isSelected = selectedTypeFilter == type
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF673AB7) else Color(0xFFF0EDF5),
                        onClick = { selectedTypeFilter = type }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFF424242),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable Secondary Filter Chips (Category, Account, Date, Status, Sort, Clear)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Filter Chip
                Box {
                    FilterChip(
                        selected = selectedCategory != null,
                        onClick = { showCategoryMenu = true },
                        label = {
                            Text(
                                text = selectedCategory ?: "Category",
                                fontSize = 12.sp
                            )
                        },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    DropdownMenu(
                        expanded = showCategoryMenu,
                        onDismissRequest = { showCategoryMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Categories") },
                            onClick = {
                                selectedCategory = null
                                showCategoryMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Uncategorized") },
                            onClick = {
                                selectedCategory = "Uncategorized"
                                showCategoryMenu = false
                            }
                        )
                        val activeCategories = allCategories.filter { !it.isArchived }
                        activeCategories.forEach { cat ->
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = com.subhashrelangi.arctracker.data.CategoryVisuals.getIcon(cat.iconKey),
                                        contentDescription = null,
                                        tint = com.subhashrelangi.arctracker.data.CategoryVisuals.getColor(cat.colorKey),
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                text = { Text(cat.name) },
                                onClick = {
                                    selectedCategory = cat.name
                                    showCategoryMenu = false
                                }
                            )
                        }
                    }
                }

                // Account Filter Chip
                Box {
                    val accountLabel = remember(selectedAccountId, knownAccounts) {
                        selectedAccountId?.let { id ->
                            knownAccounts.find { it.id == id }?.let {
                                "${it.institutionName ?: "Bank"} ••••${it.accountSuffix}"
                            } ?: "Account"
                        } ?: "Account"
                    }
                    FilterChip(
                        selected = selectedAccountId != null,
                        onClick = { showAccountMenu = true },
                        label = { Text(accountLabel, fontSize = 12.sp) },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    DropdownMenu(
                        expanded = showAccountMenu,
                        onDismissRequest = { showAccountMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Accounts") },
                            onClick = {
                                selectedAccountId = null
                                showAccountMenu = false
                            }
                        )
                        knownAccounts.forEach { acc ->
                            DropdownMenuItem(
                                text = {
                                    Text("${acc.institutionName ?: "Bank"} ••••${acc.accountSuffix}")
                                },
                                onClick = {
                                    selectedAccountId = acc.id
                                    showAccountMenu = false
                                }
                            )
                        }
                    }
                }

                // Date Range Filter Chip
                Box {
                    val dateLabel = when (selectedDateRange) {
                        DateRangeFilter.TODAY -> "Today"
                        DateRangeFilter.THIS_WEEK -> "This Week"
                        DateRangeFilter.THIS_MONTH -> if (selectedMonth.isNotBlank()) selectedMonthLabel else "This Month"
                        DateRangeFilter.CUSTOM -> "Custom Range"
                        DateRangeFilter.ALL -> "All Time"
                    }
                    FilterChip(
                        selected = selectedDateRange != DateRangeFilter.ALL,
                        onClick = { showDateRangeMenu = true },
                        label = { Text(dateLabel, fontSize = 12.sp) },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    DropdownMenu(
                        expanded = showDateRangeMenu,
                        onDismissRequest = { showDateRangeMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Today") },
                            onClick = {
                                selectedDateRange = DateRangeFilter.TODAY
                                showDateRangeMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("This Week") },
                            onClick = {
                                selectedDateRange = DateRangeFilter.THIS_WEEK
                                showDateRangeMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("This Month") },
                            onClick = {
                                selectedDateRange = DateRangeFilter.THIS_MONTH
                                showDateRangeMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("All Time") },
                            onClick = {
                                selectedDateRange = DateRangeFilter.ALL
                                showDateRangeMenu = false
                            }
                        )
                    }
                }

                // Status Filter Chip (Pending / Completed)
                Box {
                    val statusLabel = when (selectedStatusFilter) {
                        TransactionStatusFilter.PENDING -> "Pending"
                        TransactionStatusFilter.COMPLETED -> "Completed"
                        TransactionStatusFilter.ALL -> "Status"
                    }
                    FilterChip(
                        selected = selectedStatusFilter != TransactionStatusFilter.ALL,
                        onClick = { showStatusMenu = true },
                        label = { Text(statusLabel, fontSize = 12.sp) },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    DropdownMenu(
                        expanded = showStatusMenu,
                        onDismissRequest = { showStatusMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Statuses") },
                            onClick = {
                                selectedStatusFilter = TransactionStatusFilter.ALL
                                showStatusMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Completed") },
                            onClick = {
                                selectedStatusFilter = TransactionStatusFilter.COMPLETED
                                showStatusMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Pending Review") },
                            onClick = {
                                selectedStatusFilter = TransactionStatusFilter.PENDING
                                showStatusMenu = false
                            }
                        )
                    }
                }

                // Sort Dropdown Chip
                Box {
                    val sortLabel = when (selectedSortBy) {
                        TransactionSortBy.NEWEST_FIRST -> "Newest"
                        TransactionSortBy.OLDEST_FIRST -> "Oldest"
                        TransactionSortBy.HIGHEST_AMOUNT -> "Highest Amount"
                        TransactionSortBy.LOWEST_AMOUNT -> "Lowest Amount"
                    }
                    FilterChip(
                        selected = selectedSortBy != TransactionSortBy.NEWEST_FIRST,
                        onClick = { showSortMenu = true },
                        label = { Text("Sort: $sortLabel", fontSize = 12.sp) },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Newest First") },
                            onClick = {
                                selectedSortBy = TransactionSortBy.NEWEST_FIRST
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Oldest First") },
                            onClick = {
                                selectedSortBy = TransactionSortBy.OLDEST_FIRST
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Highest Amount") },
                            onClick = {
                                selectedSortBy = TransactionSortBy.HIGHEST_AMOUNT
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Lowest Amount") },
                            onClick = {
                                selectedSortBy = TransactionSortBy.LOWEST_AMOUNT
                                showSortMenu = false
                            }
                        )
                    }
                }

                // Clear Filters (Part 20)
                if (currentFilter.isActive) {
                    AssistChip(
                        onClick = {
                            searchQuery = ""
                            selectedTypeFilter = TransactionTypeFilter.ALL
                            selectedStatusFilter = TransactionStatusFilter.ALL
                            selectedCategory = null
                            selectedAccountId = null
                            selectedDateRange = DateRangeFilter.ALL
                            selectedSortBy = TransactionSortBy.NEWEST_FIRST
                        },
                        label = { Text("Clear Filters", fontSize = 12.sp, color = Color(0xFFD32F2F)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFFFEBEE))
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text("Total Balance", fontSize = 11.sp, color = Color(0xFF757575))
                        Text(
                            currencyFormatter.format(totalBalance).replace("Rs.", "₹"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFF1E1E1E)
                        )
                        Text("${filteredExpenses.size} transactions", fontSize = 10.sp, color = Color(0xFF9E9E9E))
                    }
                    Box(modifier = Modifier.width(1.dp).height(50.dp).background(Color(0xFFEEEEEE)))
                    Row(
                        modifier = Modifier
                            .weight(1.6f)
                            .padding(start = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Income", fontSize = 11.sp, color = Color(0xFF757575))
                            Text(
                                currencyFormatter.format(totalIncome).replace("Rs.", "₹"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF2E7D32)
                            )
                            Text("$incomeCount txns", fontSize = 10.sp, color = Color(0xFF9E9E9E))
                        }
                        Column {
                            Text("Expenses", fontSize = 11.sp, color = Color(0xFF757575))
                            Text(
                                currencyFormatter.format(totalExpense).replace("Rs.", "₹"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFC62828)
                            )
                            Text("$expenseCount txns", fontSize = 10.sp, color = Color(0xFF9E9E9E))
                        }
                    }
                }
            }
        }

        // Empty States (Part 28)
        if (filteredExpenses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (searchQuery.isNotBlank()) Icons.Default.SearchOff else Icons.Default.Receipt,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = when {
                            expenses.isEmpty() -> "No transactions yet."
                            searchQuery.isNotBlank() -> "No transactions match your search."
                            currentFilter.isActive -> "No transactions match the selected filters."
                            else -> "No transactions found."
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF616161)
                    )
                    if (currentFilter.isActive) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                searchQuery = ""
                                selectedTypeFilter = TransactionTypeFilter.ALL
                                selectedStatusFilter = TransactionStatusFilter.ALL
                                selectedCategory = null
                                selectedAccountId = null
                                selectedDateRange = DateRangeFilter.ALL
                                selectedSortBy = TransactionSortBy.NEWEST_FIRST
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Clear Filters")
                        }
                    }
                }
            }
        } else {
            // Transactions List Grouped by Date
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                groupedByDate.forEach { (dateMillis, expensesForDate) ->
                    item {
                        val dateStr = dateFormat.format(Date(dateMillis))
                        val headerText = when (dateStr) {
                            todayStr -> "Today • ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(dateMillis))}"
                            yesterdayStr -> "Yesterday • ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(dateMillis))}"
                            else -> dateStr
                        }
                        val dailyTotal = expensesForDate.sumOf {
                            if (it.type.equals("Debit", ignoreCase = true)) -it.amount else it.amount
                        }
                        val dailyTotalColor = if (dailyTotal >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                headerText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF1E1E1E),
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = dailyTotalColor.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = (if (dailyTotal > 0) "+" else "") + currencyFormatter.format(dailyTotal).replace("Rs.", "₹"),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = dailyTotalColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    items(expensesForDate, key = { it.id }) { expense ->
                        TransactionItemRow(
                            expense = expense,
                            account = expense.accountId?.let { accountsMap[it] },
                            onClick = {
                                selectedExpenseForDetailId = expense.id
                                onExpenseClick(expense)
                            },
                            onLongClick = {
                                selectedExpenseForDetailId = expense.id
                                onExpenseLongClick(expense)
                            }
                        )
                    }
                }
            }
        }
    }

    // Detail Dialog (Parts 2–13)
    activeDetailExpense?.let { detailExpense ->
        TransactionDetailDialog(
            expense = detailExpense,
            knownAccounts = knownAccounts,
            allCategories = allCategories,
            onDismiss = { selectedExpenseForDetailId = null },
            onSaveEdit = { expenseId, merchant, category, note ->
                scope.launch(Dispatchers.IO) {
                    transactionManager.updateTransaction(expenseId, merchant, category, note)
                }
            },
            onAssignAccount = { expenseId, targetAccountId ->
                scope.launch(Dispatchers.IO) {
                    reconciliationManager.assignAccount(expenseId, targetAccountId)
                }
            },
            onReassignAccount = { expenseId, newAccountId ->
                scope.launch(Dispatchers.IO) {
                    reconciliationManager.reassignAccount(expenseId, newAccountId)
                }
            },
            onUnlinkAccount = { expenseId ->
                scope.launch(Dispatchers.IO) {
                    reconciliationManager.unlinkAccount(expenseId)
                }
            },
            onDelete = { expenseToDelete ->
                scope.launch(Dispatchers.IO) {
                    transactionManager.deleteTransaction(expenseToDelete.id)
                }
                selectedExpenseForDetailId = null
            }
        )
    }
}

@Composable
fun TransactionItemRow(
    expense: Expense,
    account: KnownFinancialAccount? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isCredit = expense.type.equals("Credit", ignoreCase = true)
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val sign = if (isCredit) "+" else "-"
    val amountColor = if (isCredit) Color(0xFF2E7D32) else Color(0xFFC62828)
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val timeStr = timeFormat.format(Date(expense.dateMillis))

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isCredit) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCredit) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = expense.type,
                    tint = amountColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Merchant, Account, Time
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.merchant.ifEmpty { "Unknown" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF1E1E1E),
                    maxLines = 1
                )
                val accountText = when {
                    account != null -> "${account.institutionName ?: "Bank"} ••••${account.accountSuffix}"
                    !expense.accountSuffix.isNullOrBlank() -> "Ending ••••${expense.accountSuffix}"
                    else -> expense.source
                }
                Text(
                    text = "$timeStr • $accountText",
                    fontSize = 11.sp,
                    color = Color(0xFF757575),
                    maxLines = 1
                )
            }

            // Category Chip
            val category = (expense.tag ?: expense.category)?.takeIf { it.isNotBlank() } ?: "General"
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF3E5F5),
                border = BorderStroke(1.dp, Color(0xFFE1BEE7))
            ) {
                Text(
                    text = category,
                    fontSize = 11.sp,
                    color = Color(0xFF673AB7),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Amount
            Text(
                text = "$sign${currencyFormatter.format(expense.amount).replace("Rs.", "₹")}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = amountColor
            )

            // More Options Icon
            IconButton(
                onClick = onLongClick,
                modifier = Modifier.size(28.dp).padding(start = 4.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color(0xFF9E9E9E),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionActionSheet(
    expense: Expense,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isCredit = expense.type.equals("Credit", ignoreCase = true)
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val sign = if (isCredit) "+" else "-"
    val amountColor = if (isCredit) Color(0xFF2E7D32) else Color(0xFFC62828)
    val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFFBF8FF)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = expense.merchant.ifEmpty { "Unknown" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1E1E1E)
                    )
                    Text(
                        text = dateFormat.format(Date(expense.dateMillis)),
                        fontSize = 12.sp,
                        color = Color(0xFF757575)
                    )
                }
                Text(
                    text = "$sign${currencyFormatter.format(expense.amount).replace("Rs.", "₹")}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = amountColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF3EFFF)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color(0xFF673AB7),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Edit",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF673AB7)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFEBEE)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Delete",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFFD32F2F)
                    )
                }
            }
        }
    }
}

