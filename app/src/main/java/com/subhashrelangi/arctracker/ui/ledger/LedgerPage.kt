package com.subhashrelangi.arctracker.ui.ledger

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.KnownFinancialAccountRepository
import com.subhashrelangi.arctracker.security.SecureLogger
import com.subhashrelangi.arctracker.service.AccountReconciliationManager
import com.subhashrelangi.arctracker.backup.BackupManager
import com.subhashrelangi.arctracker.service.CategoryManager
import com.subhashrelangi.arctracker.ui.MonthOption
import com.subhashrelangi.arctracker.service.TransactionManager
import com.subhashrelangi.arctracker.ui.DarkDeleteConfirmDialog
import com.subhashrelangi.arctracker.ui.TransactionActionSheet
import com.subhashrelangi.arctracker.ui.TransactionDetailDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pure light-theme Ledger Page matching the reference mockup:
 * - App Header: Brand icon circle (A) + "ArcTracker" + Profile circle button
 * - Ledger Title: "Ledger" + "• Local Vault" + Tune & Export action buttons
 * - Subtitle: "Encrypted on-device SQLite ledger • October 2026"
 * - Search bar with real-time text matching
 * - Horizontal filter chips: [ All ] [ Expenses ] [ Income ] [ Needs Review ]
 * - October Overview Card with Outflow/Inflow totals, counts, net flow, and progress split bar
 * - Date-grouped transaction cards (Today, Yesterday, Earlier this week, Date)
 * - Grouped card containers with rounded corners and row dividers
 * - Local-First Encrypted Database Privacy Footer
 * - Floating pill "+ Add Expense" action button on bottom-right
 * - Fully compatible with the static light-theme bottom navigation bar
 */
@Composable
fun LedgerPage(
    expenses: List<Expense>,
    isDemoMode: Boolean = false,
    showHeader: Boolean = true,
    searchFocusTrigger: Int = 0,
    onExpenseClick: (Expense) -> Unit = {},
    onExpenseLongClick: (Expense) -> Unit = {},
    onAddTransactionClick: () -> Unit = {},
    onScanSmsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    selectedMonth: String = "",
    availableMonths: List<MonthOption> = emptyList(),
    onMonthChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Data layer services
    val db = remember { AppDatabase.getDatabase(context) }
    val accountRepo = remember { KnownFinancialAccountRepository(db.knownFinancialAccountDao()) }
    val reconciliationManager = remember { AccountReconciliationManager(db.expenseDao(), accountRepo, db) }
    val transactionManager = remember { TransactionManager(db.expenseDao()) }
    val categoryManager = remember { CategoryManager(db.transactionCategoryDao(), db.expenseDao(), db) }
    val backupManager = remember {
        BackupManager(
            database = db,
            expenseDao = db.expenseDao(),
            categoryDao = db.transactionCategoryDao(),
            accountDao = db.knownFinancialAccountDao(),
            ruleDao = db.userCategoryRuleDao(),
            aliasDao = db.merchantAliasDao(),
            budgetDao = db.budgetDao()
        )
    }

    val knownAccounts by accountRepo.getAllFlow().collectAsState(initial = emptyList())
    val allCategories by categoryManager.getAllCategoriesFlow().collectAsState(initial = emptyList())
    val accountsMap = remember(knownAccounts) { knownAccounts.associateBy { it.id } }

    // Search and filter states
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(LedgerFilterMode.ALL) }
    val searchFocusRequester = remember { FocusRequester() }

    // Month filter dialog state
    var showMonthDialog by remember { mutableStateOf(false) }

    // Detail dialog & Action sheet state
    var selectedExpenseForDetailId by remember { mutableStateOf<Int?>(null) }
    var openDetailInEditMode by remember { mutableStateOf(false) }
    var deleteConfirmExpense by remember { mutableStateOf<Expense?>(null) }
    var sheetExpense by remember { mutableStateOf<Expense?>(null) }

    // Real CSV Export via Android Storage Access Framework (SAF)
    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    var count = 0
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            count = backupManager.exportTransactionsCsv(stream).getOrThrow()
                        }
                    }
                    Toast.makeText(context, "Successfully exported $count transactions to CSV", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Determine actual transactions source: Demo Mode vs Real DB
    val sourceExpenses = remember(expenses, isDemoMode) {
        if (isDemoMode) {
            val cal = Calendar.getInstance()
            val todayTime1 = (cal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 14); set(Calendar.MINUTE, 32) }.timeInMillis
            val todayTime2 = (cal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 11); set(Calendar.MINUTE, 15) }.timeInMillis

            val yCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
            val yTime1 = (yCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 12) }.timeInMillis
            val yTime2 = (yCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 45) }.timeInMillis
            val yTime3 = (yCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 30) }.timeInMillis

            val weekCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -4) }
            val wTime1 = (weekCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 17); set(Calendar.MINUTE, 5) }.timeInMillis
            val wTime2 = (weekCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 10); set(Calendar.MINUTE, 0) }.timeInMillis

            val olderCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -6) }
            val oTime1 = (olderCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0) }.timeInMillis

            listOf(
                Expense(id = 101, amount = 280.0, merchant = "Blue Tokai Coffee", dateMillis = todayTime1, type = "Debit", categoryId = "Dining", accountSuffix = "1092", rawText = "ICICI"),
                Expense(id = 102, amount = 1540.0, merchant = "Nature's Basket", dateMillis = todayTime2, type = "Debit", categoryId = "Groceries", accountSuffix = "4821", rawText = "HDFC"),
                Expense(id = 103, amount = 48500.0, merchant = "Acuity Corp Salary", dateMillis = yTime1, type = "Credit", categoryId = "Salary / Income", accountSuffix = "4821", rawText = "HDFC"),
                Expense(id = 104, amount = 420.0, merchant = "Swiggy Delivery", dateMillis = yTime2, type = "Debit", categoryId = "Food & Dining", isPending = true, rawText = "UPI / GPay"),
                Expense(id = 105, amount = 1770.0, merchant = "Uber Premier", dateMillis = yTime3, type = "Debit", categoryId = "Transit", accountSuffix = "1092", rawText = "ICICI"),
                Expense(id = 106, amount = 3490.0, merchant = "Amazon India", dateMillis = wTime1, type = "Debit", categoryId = "Electronics", accountSuffix = "1092", rawText = "ICICI"),
                Expense(id = 107, amount = 1400.0, merchant = "Cult.fit Subscription", dateMillis = wTime2, type = "Debit", categoryId = "Health", accountSuffix = "4821", rawText = "HDFC"),
                Expense(id = 108, amount = 1850.0, merchant = "BESCOM Electricity Bill", dateMillis = oTime1, type = "Debit", categoryId = "Utilities", accountSuffix = "4821", rawText = "HDFC")
            )
        } else {
            expenses
        }
    }

    val activeDetailExpense = remember(selectedExpenseForDetailId, sourceExpenses) {
        selectedExpenseForDetailId?.let { id -> sourceExpenses.find { it.id == id } }
    }

    // Dynamic Filter Calculations
    val filteredExpenses = remember(sourceExpenses, searchQuery, selectedFilter, accountsMap) {
        sourceExpenses.filter { expense ->
            // Search Query filter
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim().lowercase(Locale.ROOT)
                val account = expense.accountId?.let { accountsMap[it] }
                val bankName = account?.institutionName ?: ""
                val suffix = expense.accountSuffix ?: account?.accountSuffix ?: ""
                val amountStr = String.format(Locale.US, "%.2f", expense.amount)

                expense.merchant.lowercase(Locale.ROOT).contains(q) ||
                        (expense.note?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                        (expense.tag?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                        (expense.category?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                        (expense.rawText?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                        bankName.lowercase(Locale.ROOT).contains(q) ||
                        suffix.contains(q) ||
                        amountStr.contains(q)
            }

            if (!matchesSearch) return@filter false

            // Status / Type filter
            when (selectedFilter) {
                LedgerFilterMode.ALL -> true
                LedgerFilterMode.DEBITS -> expense.type.equals("Debit", ignoreCase = true)
                LedgerFilterMode.CREDITS -> expense.type.equals("Credit", ignoreCase = true)
                LedgerFilterMode.NEEDS_REVIEW -> expense.isPending || (expense.accountId.isNullOrBlank() && expense.accountSuffix.isNullOrBlank() && !expense.type.equals("Credit", true) && expense.source != "MANUAL")
            }
        }
    }

    // Counts for filter chips
    val totalCount = sourceExpenses.size
    val debitCount = remember(sourceExpenses) { sourceExpenses.count { it.type.equals("Debit", ignoreCase = true) } }
    val creditCount = remember(sourceExpenses) { sourceExpenses.count { it.type.equals("Credit", ignoreCase = true) } }
    val reviewCount = remember(sourceExpenses) {
        sourceExpenses.count { it.isPending || (it.accountId.isNullOrBlank() && it.accountSuffix.isNullOrBlank() && !it.type.equals("Credit", true) && it.source != "MANUAL") }
    }

    // Financial calculations for period
    val totalCredits: Double
    val totalDebits: Double
    val displayDebitCount: Int
    val displayCreditCount: Int
    val netFlow: Double

    if (isDemoMode) {
        // Values exactly matching the reference mockup
        totalDebits = 36750.0
        displayDebitCount = 28
        totalCredits = 85000.0
        displayCreditCount = 2
        netFlow = 48250.0
    } else {
        totalCredits = filteredExpenses.filter { it.type.equals("Credit", ignoreCase = true) && !it.isPending }.sumOf { it.amount }
        totalDebits = filteredExpenses.filter { !it.type.equals("Credit", ignoreCase = true) && !it.isPending }.sumOf { it.amount }
        displayDebitCount = debitCount
        displayCreditCount = creditCount
        netFlow = totalCredits - totalDebits
    }

    // Formatted Month Label
    val periodLabel = remember(selectedMonth) {
        if (selectedMonth.isNotBlank()) {
            try {
                val parts = selectedMonth.split("-")
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, parts[0].toInt())
                    set(Calendar.MONTH, parts[1].toInt() - 1)
                }
                SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
            } catch (_: Exception) {
                SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
            }
        } else {
            SimpleDateFormat("MMMM yyyy", Locale.US).format(Date())
        }
    }

    // Grouping by Date
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

    LaunchedEffect(searchFocusTrigger) {
        if (searchFocusTrigger > 0) {
            searchFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LedgerColors.Background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(if (showHeader) Modifier.statusBarsPadding() else Modifier),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // 1. Top Header: Logo + ArcTracker + Profile & "Ledger • Local Vault" Title
            if (showHeader) {
                item(key = "header_section") {
                    LedgerHeader(
                        monthSubtitle = periodLabel,
                        onProfileClick = onProfileClick,
                        onFilterClick = {
                            if (availableMonths.isNotEmpty()) {
                                showMonthDialog = true
                            }
                        },
                        onExportClick = {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            csvExportLauncher.launch("ArcTracker_Transactions_$timestamp.csv")
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // 2. Search Field
            item(key = "search_field") {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    LedgerSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        focusRequester = searchFocusRequester
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 3. Horizontal Filter Row: [ All ] [ Expenses ] [ Income ] [ Needs Review ]
            item(key = "filter_row") {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    LedgerFilterRow(
                        selectedFilter = selectedFilter,
                        totalCount = totalCount,
                        debitCount = debitCount,
                        creditCount = creditCount,
                        reviewCount = reviewCount,
                        onFilterSelected = { selectedFilter = it }
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 4. Monthly Overview Card (Outflow, Inflow, Net, Progress Bar)
            item(key = "monthly_overview") {
                LedgerSummary(
                    periodLabel = periodLabel,
                    totalOutflow = totalDebits,
                    debitCount = displayDebitCount,
                    totalInflow = totalCredits,
                    creditCount = displayCreditCount,
                    netFlow = netFlow,
                    entryCount = filteredExpenses.size
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 5. Date-Grouped Transaction Sections
            if (filteredExpenses.isEmpty()) {
                item(key = "empty_state") {
                    LedgerEmptyState(
                        isSearching = searchQuery.isNotBlank(),
                        isFilterActive = selectedFilter != LedgerFilterMode.ALL,
                        onClearFilters = {
                            searchQuery = ""
                            selectedFilter = LedgerFilterMode.ALL
                        },
                        onAddTransaction = onAddTransactionClick,
                        onScanSms = onScanSmsClick
                    )
                }
            } else {
                groupedByDate.forEach { (dateMillis, dayExpenses) ->
                    // A. Date Header with Net Amount
                    item(key = "date_header_$dateMillis") {
                        val headerLabel = formatLedgerDateHeader(dateMillis)
                        val dayNet = if (isDemoMode) {
                            when {
                                headerLabel.startsWith("Today") -> -1820.0
                                headerLabel.startsWith("Yesterday") -> 46310.0
                                headerLabel.contains("20 Oct") -> -4890.0
                                else -> -1850.0
                            }
                        } else {
                            dayExpenses.sumOf {
                                if (it.type.equals("Debit", ignoreCase = true)) -it.amount else it.amount
                            }
                        }

                        LedgerDateHeader(
                            dateLabel = headerLabel,
                            txnCount = dayExpenses.size,
                            dayNet = dayNet
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // B. Grouped White Card Container for the day's transactions
                    item(key = "group_card_$dateMillis") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            shadowElevation = 0.5.dp
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                dayExpenses.forEachIndexed { index, expense ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            color = Color(0xFFF1F5F9),
                                            thickness = 1.dp,
                                            modifier = Modifier.padding(horizontal = 14.dp)
                                        )
                                    }

                                    LedgerTransactionRow(
                                        expense = expense,
                                        account = expense.accountId?.let { accountsMap[it] },
                                        onClick = {
                                            onExpenseClick(expense)
                                            openDetailInEditMode = false
                                            selectedExpenseForDetailId = expense.id
                                        },
                                        onLongClick = {
                                            sheetExpense = expense
                                            onExpenseLongClick(expense)
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // 6. Local-First Encrypted Database Privacy Footer
                item(key = "privacy_footer") {
                    val recordCount = if (isDemoMode) 32 else sourceExpenses.size
                    LedgerDatabaseFooter(recordCount = recordCount)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // 7. Floating Pill Button on bottom-right: "+ Add Expense"
        Surface(
            onClick = onAddTransactionClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 72.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(999.dp)),
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFF0F172A),
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

    // Month Selector Dialog
    if (showMonthDialog && availableMonths.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showMonthDialog = false },
            title = {
                Text(
                    text = "Select Month",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column {
                    for (monthOpt in availableMonths) {
                        val isSelected = monthOpt.key == selectedMonth
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    onMonthChange(monthOpt.key)
                                    showMonthDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = monthOpt.label,
                                        color = if (isSelected) Color(0xFF059669) else Color(0xFF0F172A),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    if (isSelected) {
                                        Text(
                                            text = "● Active",
                                            color = Color(0xFF059669),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMonthDialog = false }) {
                    Text("Close", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Detail Dialog Integration (View, Edit, Delete, Reconcile)
    activeDetailExpense?.let { detailExpense ->
        val recentMerchants = remember(sourceExpenses) {
            sourceExpenses.map { it.merchant }
                .filter { it.isNotBlank() && it != "-" }
                .distinct()
                .take(8)
        }
        TransactionDetailDialog(
            expense = detailExpense,
            initialEditMode = openDetailInEditMode,
            knownAccounts = knownAccounts,
            allCategories = allCategories,
            recentMerchants = recentMerchants,
            onDismiss = {
                selectedExpenseForDetailId = null
                openDetailInEditMode = false
            },
            onSaveEdit = { expenseId, merchant, category, note ->
                scope.launch(Dispatchers.IO) {
                    transactionManager.updateTransaction(expenseId, merchant, category, note)
                }
                openDetailInEditMode = false
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
                    try {
                        val res = transactionManager.deleteTransaction(expenseToDelete.id)
                        if (res.isFailure) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Failed to delete transaction", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        SecureLogger.e("LedgerPage", "Failed to delete transaction id=${expenseToDelete.id}", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to delete transaction", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                selectedExpenseForDetailId = null
                openDetailInEditMode = false
            }
        )
    }

    // Action Sheet on long-click
    sheetExpense?.let { currentSheetExpense ->
        TransactionActionSheet(
            expense = currentSheetExpense,
            onDismiss = { sheetExpense = null },
            onEdit = {
                sheetExpense = null
                openDetailInEditMode = true
                selectedExpenseForDetailId = currentSheetExpense.id
            },
            onDelete = {
                sheetExpense = null
                deleteConfirmExpense = currentSheetExpense
            }
        )
    }

    // Quick Delete Confirm Dialog from Action Sheet
    deleteConfirmExpense?.let { expenseToDelete ->
        val isCredit = expenseToDelete.type.equals("Credit", ignoreCase = true)
        val sign = if (isCredit) "+" else "-"
        DarkDeleteConfirmDialog(
            expense = expenseToDelete,
            sign = sign,
            onConfirm = {
                val idToDelete = expenseToDelete.id
                deleteConfirmExpense = null
                scope.launch(Dispatchers.IO) {
                    try {
                        val res = transactionManager.deleteTransaction(idToDelete)
                        withContext(Dispatchers.Main) {
                            if (res.isSuccess) {
                                Toast.makeText(context, "Transaction deleted", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to delete transaction", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        SecureLogger.e("LedgerPage", "Failed to delete transaction id=$idToDelete", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to delete transaction", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onDismiss = { deleteConfirmExpense = null }
        )
    }
}

/**
 * Formats a timestamp into date string matching reference mockup:
 * "Today • 24 Oct", "Yesterday • 23 Oct", "Earlier this week • 20 Oct", "18 Oct 2026"
 */
private fun formatLedgerDateHeader(dateMillis: Long): String {
    val todayCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val yesterdayCal = (todayCal.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }
    val weekAgoCal = (todayCal.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -7)
    }

    val targetCal = Calendar.getInstance().apply { timeInMillis = dateMillis }
    val isToday = targetCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
            targetCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
    val isYesterday = targetCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
            targetCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)
    val isEarlierThisWeek = targetCal.after(weekAgoCal) && !isToday && !isYesterday

    val dayFormat = SimpleDateFormat("dd MMM", Locale.US)
    val fullFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

    return when {
        isToday -> "Today • ${dayFormat.format(Date(dateMillis))}"
        isYesterday -> "Yesterday • ${dayFormat.format(Date(dateMillis))}"
        isEarlierThisWeek -> "Earlier this week • ${dayFormat.format(Date(dateMillis))}"
        else -> fullFormat.format(Date(dateMillis))
    }
}
