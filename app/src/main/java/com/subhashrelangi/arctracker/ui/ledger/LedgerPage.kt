package com.subhashrelangi.arctracker.ui.ledger

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.subhashrelangi.arctracker.backup.BackupManager
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.KnownFinancialAccountRepository
import com.subhashrelangi.arctracker.service.AccountReconciliationManager
import com.subhashrelangi.arctracker.service.CategoryManager
import com.subhashrelangi.arctracker.service.TransactionManager
import com.subhashrelangi.arctracker.ui.DarkDeleteConfirmDialog
import com.subhashrelangi.arctracker.ui.MonthOption
import com.subhashrelangi.arctracker.ui.TransactionActionSheet
import com.subhashrelangi.arctracker.ui.TransactionDetailDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Production-ready LedgerPage rebuilding the ArcTracker Transactions Screen
 * based on Ui-Designs/Ledgerpage.png.
 *
 * Implements:
 * - Near-black theme & high density financial ledger layout
 * - Search bar with real-time text matching
 * - Horizontal filter chips (All, Debits, Credits, Needs Review)
 * - Period header with dynamic entry counts & Net Flow
 * - Date-grouped transaction timeline (Today, Yesterday, Date with daily totals)
 * - Responsive transaction cards with stable tabular amounts & merchant width
 * - Encrypted Local-First SQLite Database footer
 * - Floating summary action bar with debit/credit totals & real SAF CSV export
 * - Preserves all database access, reconciliation, detail editing, and delete functionality
 */
@Composable
fun LedgerPage(
    expenses: List<Expense>,
    showHeader: Boolean = false,
    searchFocusTrigger: Int = 0,
    onExpenseClick: (Expense) -> Unit = {},
    onExpenseLongClick: (Expense) -> Unit = {},
    onAddTransactionClick: () -> Unit = {},
    onScanSmsClick: () -> Unit = {},
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

    // Detail dialog & Action sheet state
    var selectedExpenseForDetailId by remember { mutableStateOf<Int?>(null) }
    var openDetailInEditMode by remember { mutableStateOf(false) }
    var deleteConfirmExpense by remember { mutableStateOf<Expense?>(null) }
    val activeDetailExpense = remember(selectedExpenseForDetailId, expenses) {
        selectedExpenseForDetailId?.let { id -> expenses.find { it.id == id } }
    }
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

    // Dynamic Filter Calculations
    val filteredExpenses = remember(expenses, searchQuery, selectedFilter, accountsMap) {
        expenses.filter { expense ->
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
    val totalCount = expenses.size
    val debitCount = remember(expenses) { expenses.count { it.type.equals("Debit", ignoreCase = true) } }
    val creditCount = remember(expenses) { expenses.count { it.type.equals("Credit", ignoreCase = true) } }
    val reviewCount = remember(expenses) {
        expenses.count { it.isPending || (it.accountId.isNullOrBlank() && it.accountSuffix.isNullOrBlank() && !it.type.equals("Credit", true) && it.source != "MANUAL") }
    }

    // Financial calculations for period
    val totalCredits = remember(filteredExpenses) {
        filteredExpenses.filter { it.type.equals("Credit", ignoreCase = true) }.sumOf { it.amount }
    }
    val totalDebits = remember(filteredExpenses) {
        filteredExpenses.filter { it.type.equals("Debit", ignoreCase = true) }.sumOf { it.amount }
    }
    val netFlow = totalCredits - totalDebits

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
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (showHeader) {
                // 1. Ledger Top Branding Header
                LedgerHeader(
                    onSearchClick = {
                        searchFocusRequester.requestFocus()
                    },
                    onAddClick = onAddTransactionClick
                )
            }

            // 2. Search Field
            LedgerSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                focusRequester = searchFocusRequester
            )

            // 3. Horizontal Filter Row
            LedgerFilterRow(
                selectedFilter = selectedFilter,
                totalCount = totalCount,
                debitCount = debitCount,
                creditCount = creditCount,
                reviewCount = reviewCount,
                onFilterSelected = { selectedFilter = it }
            )

            // 4. Ledger Summary Row
            LedgerSummary(
                periodLabel = periodLabel,
                entryCount = filteredExpenses.size,
                netFlow = netFlow
            )

            // 5. Transaction Timeline (LazyColumn)
            if (filteredExpenses.isEmpty()) {
                LedgerEmptyState(
                    isSearching = searchQuery.isNotBlank(),
                    isFilterActive = selectedFilter != LedgerFilterMode.ALL,
                    onClearFilters = {
                        searchQuery = ""
                        selectedFilter = LedgerFilterMode.ALL
                    },
                    onAddTransaction = onAddTransactionClick,
                    onScanSms = onScanSmsClick,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 150.dp)
                ) {
                    groupedByDate.forEach { (dateMillis, dayExpenses) ->
                        // Date header item
                        item(key = "header_$dateMillis") {
                            val headerLabel = formatLedgerDateHeader(dateMillis)
                            val dayNet = dayExpenses.sumOf {
                                if (it.type.equals("Debit", ignoreCase = true)) -it.amount else it.amount
                            }

                            LedgerDateHeader(
                                dateLabel = headerLabel,
                                txnCount = dayExpenses.size,
                                dayNet = dayNet
                            )
                        }

                        // Transaction Cards
                        items(dayExpenses, key = { it.id }) { expense ->
                            LedgerTransactionCard(
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

                    // Local-First Encrypted Database Footer
                    item(key = "database_footer") {
                        LedgerDatabaseFooter()
                    }
                }
            }
        }

        // 6. Floating Ledger Summary Action Bar (Pinned above floating bottom nav)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp)
        ) {
            LedgerSummaryActionBar(
                totalDebits = totalDebits,
                totalCredits = totalCredits,
                onExportClick = {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    csvExportLauncher.launch("ArcTracker_Transactions_$timestamp.csv")
                }
            )
        }
    }

    // Detail Dialog Integration (View, Edit, Delete, Reconcile)
    activeDetailExpense?.let { detailExpense ->
        val recentMerchants = remember(expenses) {
            expenses.map { it.merchant }
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
                    transactionManager.deleteTransaction(expenseToDelete.id)
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
                    transactionManager.deleteTransaction(idToDelete)
                }
                Toast.makeText(context, "Transaction deleted", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { deleteConfirmExpense = null }
        )
    }
}

/**
 * Formats a timestamp into date string matching Ui-Designs/Ledgerpage.png:
 * "TODAY • 20 OCT", "YESTERDAY • 19 OCT", "18 OCT 2026"
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

    val targetCal = Calendar.getInstance().apply { timeInMillis = dateMillis }
    val isToday = targetCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
            targetCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
    val isYesterday = targetCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
            targetCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)

    val dayFormat = SimpleDateFormat("dd MMM", Locale.US)
    val fullFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

    return when {
        isToday -> "TODAY • ${dayFormat.format(Date(dateMillis)).uppercase(Locale.US)}"
        isYesterday -> "YESTERDAY • ${dayFormat.format(Date(dateMillis)).uppercase(Locale.US)}"
        else -> fullFormat.format(Date(dateMillis)).uppercase(Locale.US)
    }
}
