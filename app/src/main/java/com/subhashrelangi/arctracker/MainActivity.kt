package com.subhashrelangi.arctracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.service.NotificationPermissionHelper
import com.subhashrelangi.arctracker.service.SmsPermissionHelper
import com.subhashrelangi.arctracker.ui.InitialSmsImportDialog
import com.subhashrelangi.arctracker.ui.NotificationPermissionDialog
import com.subhashrelangi.arctracker.utils.RegexPatternsManager
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val privacyManager = com.subhashrelangi.arctracker.security.PrivacySettingsManager.getInstance(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                privacyManager.screenSecurityFlow.collect { enabled ->
                    if (enabled) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ExpenseScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen() {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val settingsRepo = remember {
        com.subhashrelangi.arctracker.settings.MonitoringSettingsRepository.getInstance(context)
    }

    var isNotificationAccessGranted by remember {
        mutableStateOf(NotificationPermissionHelper.isNotificationAccessGranted(context))
    }

    var hasDismissedNotificationPermissionDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var pendingHomeNotificationGrant by rememberSaveable {
        mutableStateOf(false)
    }

    var hasDismissedInitialSmsImportDialog by rememberSaveable {
        mutableStateOf(false)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = NotificationPermissionHelper.isNotificationAccessGranted(context)
                if (isNotificationAccessGranted != granted) {
                    isNotificationAccessGranted = granted
                }
                if (pendingHomeNotificationGrant) {
                    pendingHomeNotificationGrant = false
                    if (granted) {
                        settingsRepo.setGlobalEnabled(true)
                        settingsRepo.setNotificationTrackingEnabled(true)
                    } else {
                        settingsRepo.setGlobalEnabled(false)
                        settingsRepo.setNotificationTrackingEnabled(false)
                    }
                }
                if (SmsPermissionHelper.isSmsPermissionGranted(context)) {
                    SmsPermissionHelper.setInitialImportCompleted(context, true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val database = remember { com.subhashrelangi.arctracker.data.AppDatabase.getDatabase(context) }
    val expenseDao = remember { database.expenseDao() }
    val dbExpenses by expenseDao.getAllExpenses().collectAsState(initial = emptyList())

    var expenses by remember {
        mutableStateOf(emptyList<Expense>())
    }

    LaunchedEffect(dbExpenses) {
        expenses = dbExpenses
    }

    var refreshTrigger by remember {
        mutableStateOf(0)
    }

    var showAddDialog by remember {
        mutableStateOf(false)
    }

    var showApproveDialog by remember {
        mutableStateOf<Expense?>(null)
    }

    var showCompleteAllDialog by remember {
        mutableStateOf(false)
    }

    var actionSheetExpense by remember {
        mutableStateOf<Expense?>(null)
    }

    var editExpense by remember {
        mutableStateOf<Expense?>(null)
    }

    var deleteConfirmExpense by remember {
        mutableStateOf<Expense?>(null)
    }

    // ---- Home page month selection (drives Money In/Out + Spending chart) ----
    val keyFormat = remember { SimpleDateFormat("yyyy-MM", Locale.US) }
    val currentMonthKey = remember { keyFormat.format(Date()) }
    val currentYear = remember {
        java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    }

    var selectedMonth by remember {
        mutableStateOf(currentMonthKey)
    }

    // Build the month list: current month back to the month of the oldest transaction
    val availableMonths = remember(expenses) {
        val cal = java.util.Calendar.getInstance()
        val oldest = expenses.minOfOrNull { it.dateMillis }
        if (oldest != null) {
            cal.timeInMillis = oldest
        }
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)

        val keyFormatLocal = SimpleDateFormat("yyyy-MM", Locale.US)
        val monthFormat = SimpleDateFormat("MMMM", Locale.ENGLISH)
        val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)

        val months = mutableListOf<com.subhashrelangi.arctracker.ui.MonthOption>()
        var guard = 0

        while (guard < 600) {

            val key = keyFormatLocal.format(cal.time)

            months.add(
                com.subhashrelangi.arctracker.ui.MonthOption(
                    key = key,
                    label = if (cal.get(java.util.Calendar.YEAR) == currentYear) {
                        monthFormat.format(cal.time)
                    } else {
                        monthYearFormat.format(cal.time)
                    }
                )
            )

            if (key == currentMonthKey) break

            cal.add(java.util.Calendar.MONTH, 1)
            guard++
        }

        months.asReversed()
    }

    val selectedMonthLabel = availableMonths
        .firstOrNull { it.key == selectedMonth }
        ?.label
        ?: "This Month"

    // Transactions scoped to the selected calendar month
    val periodExpenses = remember(selectedMonth, expenses) {
        val cal = java.util.Calendar.getInstance()
        val parts = selectedMonth.split("-")
        cal.set(parts[0].toInt(), parts[1].toInt() - 1, 1, 0, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(java.util.Calendar.MONTH, 1)
        cal.add(java.util.Calendar.MILLISECOND, -1)
        val end = cal.timeInMillis

        expenses.filter { it.dateMillis in start..end }
    }

    val isOnboardingCompleted = remember {
        settingsRepo.isInitialOnboardingCompleted()
    }

    var backStack by rememberSaveable {
        mutableStateOf(if (isOnboardingCompleted) listOf("Home") else listOf("InitialOnboarding"))
    }
    val currentRoute = backStack.lastOrNull() ?: "Home"

    fun navigateTo(route: String) {
        if (route == "Home") {
            backStack = listOf("Home")
        } else if (route == "InitialOnboarding") {
            backStack = listOf("InitialOnboarding")
        } else if (route == "Transactions" || route == "Settings" || route == "Insights" || route == "Analytics" || route == "Pending" || route == "Review") {
            backStack = listOf("Home", route)
        } else {
            if (backStack.lastOrNull() != route) {
                backStack = backStack + route
            }
        }
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack = backStack.dropLast(1)
        }
    }

    BackHandler(enabled = backStack.size > 1) {
        navigateBack()
    }

    var editingRegexRuleId by remember {
        mutableStateOf<String?>(null)
    }
    
    var editingRegexRuleCategory by remember {
        mutableStateOf("Amount")
    }

    var viewingAccountId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var applyingRuleId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var isSearching by remember {
        mutableStateOf(false)
    }

    var ledgerSearchFocusTrigger by remember {
        mutableStateOf(0)
    }

    var searchQuery by remember {
        mutableStateOf("")
    }

    val displayedExpenses =
        if (searchQuery.isNotBlank()) {
            expenses.filter {
                it.merchant.contains(
                    searchQuery,
                    ignoreCase = true
                )
            }
        } else {
            expenses
        }

    Scaffold(
        containerColor = if (currentRoute in listOf("Home", "InitialOnboarding", "Transactions", "Pending", "Review", "Settings", "Insights", "Analytics")) com.subhashrelangi.arctracker.ui.theme.ArcColors.Background else MaterialTheme.colorScheme.background,

        topBar = {
            if (currentRoute in listOf("Home", "Transactions", "Pending", "Review", "Insights", "Settings")) {
                com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader(
                    modifier = Modifier.statusBarsPadding(),
                    title = "ArcTracker",
                    showBadge = true,
                    badgeText = "UPI",
                    statusText = "Automated  •  Local-first",
                    showSearch = true,
                    showAdd = true,
                    onSearchClick = {
                        if (currentRoute == "Transactions") {
                            ledgerSearchFocusTrigger++
                        } else {
                            isSearching = !isSearching
                            if (!isSearching) searchQuery = ""
                        }
                    },
                    onAddClick = {
                        showAddDialog = true
                    }
                )
            } else if (
                currentRoute != "SmsImport" &&
                currentRoute != "InitialOnboarding" &&
                currentRoute != "FinancialAccounts" &&
                currentRoute != "UnresolvedTransactions" &&
                currentRoute != "AccountTransactions" &&
                currentRoute != "CategoryManagement" &&
                currentRoute != "CategoryRules" &&
                currentRoute != "MerchantAliases" &&
                currentRoute != "ApplyCategoryRules" &&
                currentRoute != "Analytics" &&
                currentRoute != "Budgets" &&
                currentRoute != "Backup"
            ) {
                val title = when (currentRoute) {
                    "Database" -> "Database"
                    "ClearAllData" -> "Clear All Data"
                    "BackupRestore" -> "Backup & Restore"
                    "IgnoreRules" -> "Ignore Rules"
                    "DeveloperOptions" -> "Developer Options"
                    "RegexPatterns" -> "Regex Patterns"
                    "EditRegexPattern" -> if (editingRegexRuleId == null) "Add Pattern" else "Edit Pattern"
                    "SupportedApps" -> "Monitored Apps"
                    else -> "ArcTracker"
                }

                val subtitle = when (currentRoute) {
                    "SupportedApps" -> "Choose which apps can be monitored"
                    "IgnoreRules" -> "Keywords, senders or patterns to ignore"
                    "DeveloperOptions" -> "Advanced tools for debugging and customization."
                    "RegexPatterns" -> "Define how amounts, names and transaction details are extracted."
                    "EditRegexPattern" -> null
                    else -> null
                }

                val onBackClick: (() -> Unit)? = if (backStack.size > 1) {
                    { navigateBack() }
                } else {
                    null
                }

                com.subhashrelangi.arctracker.ui.ArcTrackerHeader(
                    title = title,
                    subtitle = subtitle,
                    onBackClick = onBackClick,
                    actions = {
                        if (currentRoute == "RegexPatterns") {
                            IconButton(onClick = { /* TODO */ }) {
                                Icon(Icons.Outlined.HelpOutline, contentDescription = "Help", tint = Color(0xFF3F51B5))
                            }
                        } else if (currentRoute == "EditRegexPattern") {
                            if (editingRegexRuleId != null) {
                                val context = LocalContext.current
                                TextButton(onClick = {
                                    RegexPatternsManager.resetSystemRule(context, editingRegexRuleId!!)
                                    navigateBack()
                                }) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Reset", tint = Color(0xFF3F51B5), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reset", color = Color(0xFF3F51B5), fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                )
            }
        },

        bottomBar = {

            if (
                currentRoute != "InitialOnboarding" &&
                currentRoute != "ClearAllData" &&
                currentRoute != "SmsImport" &&
                currentRoute != "BackupRestore" &&
                currentRoute != "SupportedApps" &&
                currentRoute != "CategoryManagement" &&
                currentRoute != "FinancialAccounts" &&
                currentRoute != "UnresolvedTransactions" &&
                currentRoute != "AccountTransactions" &&
                currentRoute != "CategoryRules" &&
                currentRoute != "MerchantAliases" &&
                currentRoute != "ApplyCategoryRules" &&
                currentRoute != "Analytics" &&
                currentRoute != "Budgets" &&
                currentRoute != "Backup"
            ) {

                val pendingCount = expenses.count { it.isPending }
                val destination = com.subhashrelangi.arctracker.ui.core.ArcDestination.fromRoute(currentRoute)
                com.subhashrelangi.arctracker.ui.core.ArcTrackerBottomNavigation(
                    currentDestination = destination,
                    reviewBadgeCount = pendingCount,
                    onNavigate = { dest ->
                        navigateTo(dest.route)
                    }
                )
            }
        },

        floatingActionButton = {}

    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            AnimatedVisibility(
                visible = isSearching && currentRoute != "InitialOnboarding" && currentRoute != "Transactions"
            ) {

                OutlinedTextField(

                    value = searchQuery,

                    onValueChange = {
                        searchQuery = it
                    },

                    placeholder = {
                        Text(
                            "Search transactions by name..."
                        )
                    },

                    singleLine = true,

                    shape =
                        RoundedCornerShape(24.dp),

                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        ),

                    textStyle =
                        MaterialTheme
                            .typography
                            .bodyMedium,

                    leadingIcon = {

                        Icon(
                            imageVector =
                                Icons.Filled.Search,
                            contentDescription =
                                "Search Icon",
                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .primary
                        )
                    }
                )
            }

            if (currentRoute == "Transactions") {

                com.subhashrelangi.arctracker.ui.ledger.LedgerPage(
                    expenses = expenses,
                    showHeader = false,
                    searchFocusTrigger = ledgerSearchFocusTrigger,
                    onExpenseClick = { expense ->
                        if (expense.isPending) {
                            showApproveDialog = expense
                        } else {
                            actionSheetExpense = expense
                        }
                    },
                    onExpenseLongClick = { expense ->
                        actionSheetExpense = expense
                    },
                    onAddTransactionClick = {
                        showAddDialog = true
                    },
                    onScanSmsClick = {
                        navigateTo("SmsImport")
                    },
                    selectedMonth = selectedMonth,
                    availableMonths = availableMonths,
                    onMonthChange = {
                        selectedMonth = it
                    }
                )

            } else if (currentRoute == "Settings") {

                val calToday = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val startOfDay = calToday.timeInMillis

                val calMonth = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val startOfMonth = calMonth.timeInMillis

                val todayTxnCount = expenses.count { it.dateMillis >= startOfDay && !it.isPending }
                val thisMonthTxnCount = expenses.count { it.dateMillis >= startOfMonth && !it.isPending }

                com.subhashrelangi.arctracker.ui.SettingsScreen(
                    onNavigate = {
                        navigateTo(it)
                    },
                    todayCount = todayTxnCount,
                    thisMonthCount = thisMonthTxnCount
                )

            } else if (currentRoute == "ClearAllData") {

                com.subhashrelangi.arctracker.ui.ClearAllDataScreen(
                    onNavigate = {
                        navigateTo(it)
                    },
                    onClearData = {
                        val repo = com.subhashrelangi.arctracker.settings.MonitoringSettingsRepository.getInstance(context)
                        val wasCompleted = repo.isInitialSmsImportCompleted()
                        expenses = emptyList()
                        scope.launch(Dispatchers.IO) {
                            try {
                                expenseDao.clearAll()
                            } catch (_: Exception) {}
                        }
                        repo.setInitialSmsImportCompleted(wasCompleted)
                        navigateBack()
                    }
                )

            } else if (currentRoute == "SmsImport") {

                com.subhashrelangi.arctracker.ui.SmsImportScreen(
                    onNavigateBack = {
                        navigateBack()
                    }
                )

            } else if (currentRoute == "BackupRestore") {

                com.subhashrelangi.arctracker.ui.BackupRestoreScreen(
                    onNavigate = {
                        navigateTo(it)
                    }
                )

            } else if (currentRoute == "SupportedApps") {

                com.subhashrelangi.arctracker.ui.SupportedAppsScreen(
                    onNavigateBack = {
                        navigateBack()
                    }
                )

            } else if (currentRoute == "Database") {

                com.subhashrelangi.arctracker.ui.DatabaseScreen()

            } else if (currentRoute == "IgnoreRules") {

                com.subhashrelangi.arctracker.ui.IgnoreRulesScreen()

            } else if (currentRoute == "DeveloperOptions") {
                com.subhashrelangi.arctracker.ui.DeveloperOptionsScreen(
                    onNavigate = { navigateTo(it) }
                )
            } else if (currentRoute == "RegexPatterns") {
                com.subhashrelangi.arctracker.ui.RegexPatternsScreen(
                    onAddPattern = { category ->
                        editingRegexRuleId = null
                        editingRegexRuleCategory = category
                        navigateTo("EditRegexPattern")
                    },
                    onEditPattern = { rule ->
                        editingRegexRuleId = rule.id
                        editingRegexRuleCategory = rule.category
                        navigateTo("EditRegexPattern")
                    }
                )
            } else if (currentRoute == "EditRegexPattern") {
                com.subhashrelangi.arctracker.ui.EditRegexPatternScreen(
                    ruleId = editingRegexRuleId,
                    defaultCategory = editingRegexRuleCategory,
                    onBack = { navigateBack() }
                )
            } else if (currentRoute == "FinancialAccounts") {
                com.subhashrelangi.arctracker.ui.FinancialAccountsScreen(
                    onNavigateBack = { navigateBack() },
                    onNavigateToUnresolved = { navigateTo("UnresolvedTransactions") },
                    onViewAccountTransactions = { accId ->
                        viewingAccountId = accId
                        navigateTo("AccountTransactions")
                    }
                )
            } else if (currentRoute == "UnresolvedTransactions") {
                com.subhashrelangi.arctracker.ui.UnresolvedTransactionsScreen(
                    onNavigateBack = { navigateBack() }
                )
            } else if (currentRoute == "AccountTransactions") {
                com.subhashrelangi.arctracker.ui.AccountTransactionsScreen(
                    accountId = viewingAccountId ?: "",
                    onNavigateBack = { navigateBack() }
                )
            } else if (currentRoute == "CategoryManagement") {
                com.subhashrelangi.arctracker.ui.CategoryManagementScreen(
                    onNavigateBack = { navigateBack() },
                    onNavigateToRules = { navigateTo("CategoryRules") },
                    onNavigateToAliases = { navigateTo("MerchantAliases") },
                    onNavigateToApplyRules = {
                        applyingRuleId = null
                        navigateTo("ApplyCategoryRules")
                    }
                )
            } else if (currentRoute == "CategoryRules") {
                com.subhashrelangi.arctracker.ui.CategoryRulesScreen(
                    onNavigateBack = { navigateBack() },
                    onNavigateToApplyRules = { ruleId ->
                        applyingRuleId = ruleId
                        navigateTo("ApplyCategoryRules")
                    }
                )
            } else if (currentRoute == "MerchantAliases") {
                com.subhashrelangi.arctracker.ui.MerchantAliasesScreen(
                    onNavigateBack = { navigateBack() }
                )
            } else if (currentRoute == "ApplyCategoryRules") {
                com.subhashrelangi.arctracker.ui.HistoricalRuleApplicationScreen(
                    initialRuleId = applyingRuleId,
                    onNavigateBack = { navigateBack() }
                )
            } else if (currentRoute == "Analytics" || currentRoute == "Insights") {
                com.subhashrelangi.arctracker.ui.AnalyticsScreen(
                    onNavigateBack = { navigateBack() },
                    showInternalHeader = (currentRoute == "Analytics")
                )
            } else if (currentRoute == "Budgets") {
                com.subhashrelangi.arctracker.ui.BudgetsScreen(
                    onNavigateBack = { navigateBack() }
                )
            } else if (currentRoute == "Backup") {
                com.subhashrelangi.arctracker.ui.BackupScreen(
                    onNavigateBack = { navigateBack() }
                )
            } else if (currentRoute == "Pending" || currentRoute == "Review") {

                com.subhashrelangi.arctracker.ui.review.ReviewPage(
                    expenses = displayedExpenses,
                    showHeader = false,
                    onSearchClick = {
                        isSearching = !isSearching
                    },
                    onAddExpenseClick = {
                        showAddDialog = true
                    },
                    onViewLedger = {
                        navigateTo("Transactions")
                    },
                    onEditExpense = {
                        editExpense = it
                    }
                )

            } else if (currentRoute == "InitialOnboarding") {

                com.subhashrelangi.arctracker.ui.InitialPageScreen(
                    onGetStartedClick = {
                        settingsRepo.setInitialOnboardingCompleted(true)
                        navigateTo("Home")
                    },
                    onExploreDemoClick = {
                        settingsRepo.setInitialOnboardingCompleted(true)
                        navigateTo("Home")
                        Toast.makeText(context, "Demo mode: sample ledger loaded", Toast.LENGTH_SHORT).show()
                    }
                )

            } else if (currentRoute != "Home") {

                Box(
                    modifier =
                        Modifier.fillMaxSize(),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text =
                            "$currentRoute Screen Coming Soon!",
                        style =
                            MaterialTheme
                                .typography
                                .titleLarge
                    )
                }

            } else {

                // Keep refreshTrigger observed for manual refresh.
                val trigger = refreshTrigger

                com.subhashrelangi.arctracker.ui.HomeScreenRedesign(
                    expenses = displayedExpenses,
                    showHeader = false,
                    onSearchClick = {
                        isSearching = !isSearching
                    },
                    onAddExpenseClick = {
                        showAddDialog = true
                    },
                    onReviewClick = {
                        navigateTo("Pending")
                    },
                    onAccountsClick = {
                        navigateTo("FinancialAccounts")
                    },
                    onScanSmsClick = {
                        navigateTo("SmsImport")
                    },
                    onAddCashClick = {
                        showAddDialog = true
                    },
                    onExportClick = {
                        navigateTo("Backup")
                    },
                    onViewAllClick = {
                        navigateTo("Transactions")
                    },
                    onExpenseClick = { expense ->
                        if (expense.isPending) {
                            showApproveDialog = expense
                        } else {
                            actionSheetExpense = expense
                        }
                    }
                )
            }
        }

        if (showAddDialog) {

            AddExpenseDialog(

                initialAmount = "",

                initialMerchant = "",

                initialType = "Debit",

                initialNote = "",

                initialDateMillis =
                    System.currentTimeMillis(),

                onDismiss = {
                    showAddDialog = false
                },

                onAdd = {
                        amount,
                        merchant,
                        type,
                        tag,
                        note,
                        dateMillis ->

                    val newExpense = Expense(
                        id = 0,
                        amount = amount,
                        merchant = merchant,
                        dateMillis = dateMillis,
                        type = type,
                        notificationKey = UUID.randomUUID().toString(),
                        isPending = false,
                        rawText = "Manual Entry",
                        tag = tag,
                        note = note,
                        source = "MANUAL"
                    )
                    expenses = listOf(newExpense) + expenses
                    scope.launch(Dispatchers.IO) {
                        try {
                            val id = expenseDao.insert(newExpense)
                            newExpense.id = id.toInt()
                        } catch (_: Exception) {}
                    }
                    showAddDialog = false
                }
            )
        }

        showApproveDialog?.let { pendingExpense ->

            AddExpenseDialog(

                initialAmount =
                    if (pendingExpense.amount > 0) {
                        pendingExpense.amount
                            .toString()
                    } else {
                        ""
                    },

                initialMerchant =
                    if (
                        pendingExpense.merchant.isNotBlank()
                    ) {
                        pendingExpense.merchant
                    } else {
                        "Unknown Merchant"
                    },

                initialType =
                    pendingExpense.type ?: "Debit",

                initialNote =
                    pendingExpense.note ?: "",

                initialDateMillis =
                    pendingExpense.dateMillis,

                rawText =
                    pendingExpense.rawText,

                onDismiss = {
                    showApproveDialog = null
                },

                onDelete = {
                    expenses = expenses.filter { it.id != pendingExpense.id }
                    scope.launch(Dispatchers.IO) {
                        try {
                            expenseDao.delete(pendingExpense)
                        } catch (_: Exception) {}
                    }
                    showApproveDialog = null
                },

                onAdd = {
                        amount,
                        merchant,
                        type,
                        tag,
                        note,
                        dateMillis ->

                    val updated = pendingExpense.copy(
                        amount = amount,
                        merchant = merchant,
                        type = type,
                        tag = tag,
                        note = note,
                        dateMillis = dateMillis,
                        isPending = false
                    )
                    expenses = expenses.map {
                        if (it.id == pendingExpense.id) updated else it
                    }
                    scope.launch(Dispatchers.IO) {
                        try {
                            expenseDao.update(updated)
                        } catch (_: Exception) {}
                    }
                    showApproveDialog = null
                }
            )
        }

        if (showCompleteAllDialog) {
            val pendingExpenses = expenses.filter { it.isPending }
            AlertDialog(
                onDismissRequest = { showCompleteAllDialog = false },
                title = {
                    Text("Complete all pending transactions?")
                },
                text = {
                    Text("This will approve all pending transactions currently waiting for review.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showCompleteAllDialog = false
                            val toComplete = expenses.filter { it.isPending }
                            scope.launch {
                                var successCount = 0
                                var failCount = 0
                                val updatedList = expenses.toMutableList()
                                for (item in toComplete) {
                                    try {
                                        val updated = item.copy(isPending = false)
                                        val count = withContext(Dispatchers.IO) {
                                            expenseDao.update(updated)
                                        }
                                        if (count > 0) {
                                            val idx = updatedList.indexOfFirst { it.id == item.id }
                                            if (idx != -1) {
                                                updatedList[idx] = updated
                                            }
                                            successCount++
                                        } else {
                                            failCount++
                                        }
                                    } catch (e: Exception) {
                                        failCount++
                                    }
                                }
                                expenses = updatedList
                                refreshTrigger++
                                val msg = if (failCount == 0) {
                                    "Completed all $successCount pending transactions"
                                } else {
                                    "Completed $successCount transactions ($failCount failed)"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Complete All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCompleteAllDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        actionSheetExpense?.let { sheetExpense ->

            com.subhashrelangi.arctracker.ui.TransactionActionSheet(

                expense = sheetExpense,

                onDismiss = {
                    actionSheetExpense = null
                },

                onEdit = {
                    editExpense = sheetExpense
                    actionSheetExpense = null
                },

                onDelete = {
                    deleteConfirmExpense = sheetExpense
                    actionSheetExpense = null
                }
            )
        }

        editExpense?.let { expenseToEdit ->

            AddExpenseDialog(

                initialAmount =
                    if (expenseToEdit.amount > 0) {
                        expenseToEdit.amount.toString()
                    } else {
                        ""
                    },

                initialMerchant =
                    if (
                        expenseToEdit.merchant !=
                        "Unknown Merchant"
                    ) {
                        expenseToEdit.merchant
                    } else {
                        ""
                    },

                initialType =
                    expenseToEdit.type ?: "Debit",

                initialTag =
                    expenseToEdit.tag ?: "",

                initialNote =
                    expenseToEdit.note ?: "",

                initialDateMillis =
                    expenseToEdit.dateMillis,

                isEditMode = true,

                onDismiss = {
                    editExpense = null
                },

                onAdd = {
                        amount,
                        merchant,
                        type,
                        tag,
                        note,
                        dateMillis ->

                    val updated = expenseToEdit.copy(
                        amount = amount,
                        merchant = merchant,
                        type = type,
                        tag = tag,
                        note = note,
                        dateMillis = dateMillis
                    )
                    expenses = expenses.map {
                        if (it.id == expenseToEdit.id) updated else it
                    }
                    scope.launch(Dispatchers.IO) {
                        try {
                            expenseDao.update(updated)
                        } catch (_: Exception) {}
                    }
                    editExpense = null
                }
            )
        }

        deleteConfirmExpense?.let { expenseToDelete ->

            AlertDialog(

                onDismissRequest = {
                    deleteConfirmExpense = null
                },

                title = {
                    Text(
                        text = "Delete Transaction?",
                        fontWeight = FontWeight.Bold
                    )
                },

                text = {
                    Text(
                        text =
                            "This will permanently delete the transaction of ₹${expenseToDelete.amount} to ${expenseToDelete.merchant}. " +
                                    "This action cannot be undone."
                    )
                },

                confirmButton = {
                    TextButton(
                        onClick = {
                            expenses = expenses.filter { it.id != expenseToDelete.id }
                            scope.launch(Dispatchers.IO) {
                                try {
                                    expenseDao.delete(expenseToDelete)
                                } catch (_: Exception) {}
                            }
                            deleteConfirmExpense = null
                        }
                    ) {
                        Text(
                            text = "Delete",
                            color = Color(0xFFD32F2F),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },

                dismissButton = {
                    TextButton(
                        onClick = {
                            deleteConfirmExpense = null
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (currentRoute != "InitialOnboarding") {
            if (!isNotificationAccessGranted && !hasDismissedNotificationPermissionDialog) {
                NotificationPermissionDialog(
                    onGrantClick = {
                        pendingHomeNotificationGrant = true
                        NotificationPermissionHelper.openNotificationAccessSettings(context)
                    },
                    onDismiss = {
                        hasDismissedNotificationPermissionDialog = true
                        settingsRepo.setGlobalEnabled(false)
                        settingsRepo.setNotificationTrackingEnabled(false)
                    }
                )
            } else if (!hasDismissedInitialSmsImportDialog && !SmsPermissionHelper.isInitialImportCompleted(context)) {
                InitialSmsImportDialog(
                    onDismiss = {
                        hasDismissedInitialSmsImportDialog = true
                        SmsPermissionHelper.setInitialImportCompleted(context, true)
                    },
                    onNavigateToImport = {
                        hasDismissedInitialSmsImportDialog = true
                        SmsPermissionHelper.setInitialImportCompleted(context, true)
                        navigateTo("SmsImport")
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExpenseItemRow(
    expense: Expense,
    isLast: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {

    val dateFormat =
        SimpleDateFormat(
            "MMM dd, HH:mm",
            Locale.getDefault()
        )

    val dateString =
        dateFormat.format(
            Date(expense.dateMillis)
        )

    val isCredit =
        expense.type == "Credit"

    val iconBgColor =
        if (isCredit) {
            Color(0xFFE8F5E9)
        } else {
            Color(0xFFFFEBEE)
        }

    val iconColor =
        if (isCredit) {
            Color(0xFF4CAF50)
        } else {
            Color(0xFFF44336)
        }

    val icon =
        if (isCredit) {
            Icons.Filled.KeyboardArrowUp
        } else {
            Icons.Filled.KeyboardArrowDown
        }

    val amountColor =
        if (isCredit) {
            Color(0xFF4CAF50)
        } else {
            Color(0xFFD32F2F)
        }

    val sign =
        if (isCredit) {
            "+"
        } else {
            "-"
        }

    Surface(

        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),

        color =
            Color.Transparent

    ) {

        Column {

            Row(

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    ),

                verticalAlignment =
                    Alignment.CenterVertically

            ) {

                Box(

                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            iconBgColor,
                            CircleShape
                        ),

                    contentAlignment =
                        Alignment.Center

                ) {

                    Icon(
                        imageVector = icon,
                        contentDescription =
                            expense.type,
                        tint = iconColor,
                        modifier =
                            Modifier.size(24.dp)
                    )
                }

                Spacer(
                    modifier =
                        Modifier.width(16.dp)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text =
                            expense.merchant.ifBlank { "Unknown" },
                        fontWeight =
                            FontWeight.Bold,
                        fontSize = 15.sp,
                        color =
                            Color(0xFF1E1E1E)
                    )

                    Text(

                        text =
                            dateString +
                                    if (
                                        expense.tag != null
                                    ) {
                                        " • ${expense.tag}"
                                    } else {
                                        ""
                                    } +
                                    if (
                                        !expense.note
                                            .isNullOrBlank()
                                    ) {
                                        " • ${expense.note}"
                                    } else {
                                        ""
                                    },

                        fontSize = 13.sp,

                        color =
                            Color(0xFF757575)
                    )
                }

                Column(
                    horizontalAlignment =
                        Alignment.End
                ) {

                    Text(
                        text =
                            "$sign₹${expense.amount}",
                        fontWeight =
                            FontWeight.Bold,
                        fontSize = 15.sp,
                        color =
                            amountColor
                    )

                    Text(
                        text =
                            expense.type,
                        fontSize = 13.sp,
                        color =
                            Color(0xFF757575)
                    )
                }
            }

            if (!isLast) {

                HorizontalDivider(

                    modifier =
                        Modifier.padding(
                            start = 76.dp,
                            end = 16.dp
                        ),

                    color =
                        Color(0xFFF5F5F5),

                    thickness = 1.dp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(

    initialAmount: String,

    initialMerchant: String,

    initialType: String = "Debit",

    initialTag: String = "",

    initialNote: String = "",

    isEditMode: Boolean = false,

    initialDateMillis: Long =
        System.currentTimeMillis(),

    rawText: String? = null,

    onDismiss: () -> Unit,

    onDelete: (() -> Unit)? = null,

    onAdd: (
        Double,
        String,
        String,
        String,
        String,
        Long
    ) -> Unit

) {

    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )

    var amount by remember {
        mutableStateOf(initialAmount)
    }

    var merchant by remember {
        mutableStateOf(initialMerchant)
    }

    var note by remember {
        mutableStateOf(initialNote)
    }

    var type by remember {
        mutableStateOf(initialType)
    }

    var tag by remember {

        mutableStateOf(
            if (initialTag.isNotBlank()) {
                initialTag
            } else if (initialType == "Credit") {
                "Salary"
            } else {
                "Food"
            }
        )
    }

    var dateMillis by remember {
        mutableStateOf(initialDateMillis)
    }

    var showDatePicker by remember {
        mutableStateOf(false)
    }

    val primaryPurple =
        Color(0xFF7859C1)

    val lightPurple =
        Color(0xFFF3EFFF)

    val bgGray =
        Color(0xFFF9F9FB)

    val textDark =
        Color(0xFF1E1E1E)

    var amountError by remember {
        mutableStateOf(false)
    }

    var isAnimating by remember {
        mutableStateOf(false)
    }

    val scale by animateFloatAsState(

        targetValue =
            if (isAnimating) {
                0.95f
            } else {
                1f
            },

        animationSpec =
            spring(
                dampingRatio =
                    Spring.DampingRatioMediumBouncy
            ),

        finishedListener = {

            if (isAnimating) {

                val amt =
                    amount.toDoubleOrNull()
                        ?: 0.0

                if (
                    amt > 0 &&
                    merchant.isNotBlank()
                ) {

                    onAdd(
                        amt,
                        merchant,
                        type,
                        tag,
                        note,
                        dateMillis
                    )

                } else {

                    isAnimating = false
                }
            }
        }
    )

    ModalBottomSheet(

        onDismissRequest = onDismiss,

        sheetState = sheetState,

        containerColor =
            Color(0xFFFBF8FF),

        dragHandle = {
            BottomSheetDefaults.DragHandle()
        }

    ) {

        Column(

            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .imePadding()
                .verticalScroll(
                    rememberScrollState()
                )

        ) {

            Row(

                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.CenterVertically

            ) {

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Box(

                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                lightPurple,
                                CircleShape
                            ),

                        contentAlignment =
                            Alignment.Center

                    ) {

                        Icon(
                            imageVector =
                                Icons.Rounded.Add,
                            contentDescription = null,
                            tint =
                                primaryPurple,
                            modifier =
                                Modifier.size(24.dp)
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.width(12.dp)
                    )

                    Column {

                        Text(
                            text =
                                if (isEditMode) {
                                    "Edit Expense"
                                } else if (rawText != null) {
                                    "Approve Expense"
                                } else {
                                    "Add Expense"
                                },
                            fontWeight =
                                FontWeight.Bold,
                            fontSize = 18.sp,
                            color = textDark
                        )

                        Text(
                            text =
                                if (rawText != null) {
                                    "Review detected transaction"
                                } else {
                                    "Add a transaction manually"
                                },
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                IconButton(

                    onClick = onDismiss,

                    modifier = Modifier
                        .background(
                            bgGray,
                            CircleShape
                        )
                        .size(36.dp)

                ) {

                    Icon(
                        imageVector =
                            Icons.Rounded.Close,
                        contentDescription =
                            "Close",
                        tint =
                            textDark,
                        modifier =
                            Modifier.size(20.dp)
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            OutlinedTextField(

                value = amount,

                onValueChange = {
                    amount = it
                    if (amountError && (it.toDoubleOrNull() ?: 0.0) > 0.0) {
                        amountError = false
                    }
                },

                isError = amountError,

                supportingText = if (amountError) {
                    { Text("Please enter a valid amount greater than 0", color = Color(0xFFD32F2F), fontSize = 11.sp) }
                } else null,

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(12.dp),

                textStyle =
                    TextStyle(
                        fontSize = 24.sp,
                        color = textDark
                    ),

                colors =
                    OutlinedTextFieldDefaults.colors(

                        unfocusedBorderColor =
                            Color(0xFFEBEBEB),

                        focusedBorderColor =
                            primaryPurple,

                        unfocusedContainerColor =
                            Color.White,

                        focusedContainerColor =
                            Color.White
                    ),

                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Number
                    ),

                leadingIcon = {

                    Text(
                        text = "₹",
                        fontSize = 20.sp,
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            textDark,
                        modifier =
                            Modifier.padding(
                                start = 12.dp
                            )
                    )
                },

                trailingIcon = {

                    Box(

                        modifier = Modifier
                            .padding(end = 8.dp)
                            .background(
                                bgGray,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(6.dp)

                    ) {

                        Icon(
                            imageVector =
                                Icons.Outlined.Calculate,
                            contentDescription = null,
                            tint =
                                textDark,
                            modifier =
                                Modifier.size(20.dp)
                        )
                    }
                }
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            OutlinedTextField(

                value = merchant,

                onValueChange = {
                    merchant = it
                },

                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(12.dp),

                colors =
                    OutlinedTextFieldDefaults.colors(

                        unfocusedBorderColor =
                            Color(0xFFEBEBEB),

                        focusedBorderColor =
                            primaryPurple,

                        unfocusedContainerColor =
                            Color.White,

                        focusedContainerColor =
                            Color.White
                    ),

                placeholder = {

                    Column(
                        modifier =
                            Modifier.padding(top = 2.dp)
                    ) {

                        Text(
                            text =
                                "Merchant / Person",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )

                        Text(
                            text =
                                "e.g. Zomato, Amazon, Mom",
                            fontSize = 10.sp,
                            color =
                                Color.LightGray
                        )
                    }
                },

                leadingIcon = {

                    Icon(
                        imageVector =
                            Icons.Outlined.Person,
                        contentDescription = null,
                        tint = textDark
                    )
                }
            )

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            Row(

                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.CenterVertically

            ) {

                Text(
                    text = "Category",
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 14.sp,
                    color = textDark
                )

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        text = "See all",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    Icon(
                        imageVector =
                            Icons.Rounded
                                .KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier =
                            Modifier.size(16.dp)
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Row(

                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween

            ) {

                val categories =
                    if (type == "Debit") {

                        listOf(

                            "Food" to
                                    Icons.Outlined.Fastfood,

                            "Travel" to
                                    Icons.Outlined.DirectionsCar,

                            "Bills" to
                                    Icons.Outlined.Receipt,

                            "Shopping" to
                                    Icons.Outlined.ShoppingBag,

                            "Other" to
                                    Icons.Outlined.MoreHoriz

                        )

                    } else {

                        listOf(

                            "Salary" to
                                    Icons.Outlined.Payments,

                            "Allowance" to
                                    Icons.Outlined.Savings,

                            "Refund" to
                                    Icons.Outlined.Replay,

                            "Gift" to
                                    Icons.Outlined.CardGiftcard,

                            "Other" to
                                    Icons.Outlined.MoreHoriz

                        )
                    }

                categories.forEach {
                        (catName, iconRes) ->

                    val isSelected =
                        tag == catName

                    Column(

                        horizontalAlignment =
                            Alignment.CenterHorizontally,

                        modifier = Modifier
                            .weight(1f)
                            .padding(
                                horizontal = 4.dp
                            )
                            .background(
                                if (isSelected) {
                                    lightPurple
                                } else {
                                    bgGray
                                },
                                RoundedCornerShape(
                                    10.dp
                                )
                            )
                            .clickable {
                                tag = catName
                            }
                            .padding(
                                vertical = 8.dp
                            )

                    ) {

                        Icon(

                            imageVector =
                                iconRes,

                            contentDescription =
                                catName,

                            tint =
                                if (isSelected) {
                                    primaryPurple
                                } else {
                                    textDark
                                },

                            modifier =
                                Modifier.size(20.dp)
                        )

                        Spacer(
                            modifier =
                                Modifier.height(4.dp)
                        )

                        Text(

                            text = catName,

                            fontSize = 10.sp,

                            color =
                                if (isSelected) {
                                    primaryPurple
                                } else {
                                    textDark
                                },

                            fontWeight =
                                if (isSelected) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                }
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            Text(
                text = "Type",
                fontWeight =
                    FontWeight.Bold,
                fontSize = 14.sp,
                color = textDark
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                val isDebit =
                    type == "Debit"

                Box(

                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isDebit) {
                                lightPurple
                            } else {
                                Color.White
                            },
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.dp,
                            if (isDebit) {
                                primaryPurple
                            } else {
                                Color(0xFFEBEBEB)
                            },
                            RoundedCornerShape(10.dp)
                        )
                        .clickable(
                            enabled =
                                onDelete == null
                        ) {

                            type = "Debit"
                            tag = "Food"
                        }
                        .padding(
                            vertical = 10.dp
                        ),

                    contentAlignment =
                        Alignment.Center

                ) {

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Icon(
                            imageVector =
                                Icons.Outlined
                                    .RemoveCircleOutline,
                            contentDescription =
                                null,
                            tint =
                                if (isDebit) {
                                    primaryPurple
                                } else {
                                    textDark
                                },
                            modifier =
                                Modifier.size(18.dp)
                        )

                        Spacer(
                            modifier =
                                Modifier.width(6.dp)
                        )

                        Text(
                            text =
                                "Expense (Debit)",
                            fontSize = 13.sp,
                            color =
                                if (isDebit) {
                                    primaryPurple
                                } else {
                                    textDark
                                },
                            fontWeight =
                                if (isDebit) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                }
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.width(12.dp)
                )

                Box(

                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (!isDebit) {
                                lightPurple
                            } else {
                                Color.White
                            },
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.dp,
                            if (!isDebit) {
                                primaryPurple
                            } else {
                                Color(0xFFEBEBEB)
                            },
                            RoundedCornerShape(10.dp)
                        )
                        .clickable(
                            enabled =
                                onDelete == null
                        ) {

                            type = "Credit"
                            tag = "Salary"
                        }
                        .padding(
                            vertical = 10.dp
                        ),

                    contentAlignment =
                        Alignment.Center

                ) {

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Icon(
                            imageVector =
                                Icons.Outlined
                                    .AddCircleOutline,
                            contentDescription =
                                null,
                            tint =
                                if (!isDebit) {
                                    primaryPurple
                                } else {
                                    textDark
                                },
                            modifier =
                                Modifier.size(18.dp)
                        )

                        Spacer(
                            modifier =
                                Modifier.width(6.dp)
                        )

                        Text(
                            text =
                                "Income (Credit)",
                            fontSize = 13.sp,
                            color =
                                if (!isDebit) {
                                    primaryPurple
                                } else {
                                    textDark
                                },
                            fontWeight =
                                if (!isDebit) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                }
                        )
                    }
                }
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            val sdf =
                SimpleDateFormat(
                    "MMM dd, yyyy",
                    Locale.getDefault()
                )

            val dateStr =
                sdf.format(
                    Date(dateMillis)
                )

            Box(

                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled =
                            onDelete == null
                    ) {
                        showDatePicker = true
                    }

            ) {

                OutlinedTextField(

                    value = dateStr,

                    onValueChange = {},

                    readOnly = true,

                    enabled = false,

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),

                    shape =
                        RoundedCornerShape(10.dp),

                    colors =
                        OutlinedTextFieldDefaults
                            .colors(

                                disabledBorderColor =
                                    Color(0xFFEBEBEB),

                                disabledContainerColor =
                                    Color.White,

                                disabledTextColor =
                                    textDark,

                                disabledLeadingIconColor =
                                    textDark,

                                disabledTrailingIconColor =
                                    textDark
                            ),

                    textStyle =
                        TextStyle(
                            fontSize = 14.sp
                        ),

                    leadingIcon = {

                        Icon(
                            imageVector =
                                Icons.Outlined
                                    .CalendarToday,
                            contentDescription =
                                null,
                            tint =
                                textDark,
                            modifier =
                                Modifier.size(20.dp)
                        )
                    },

                    trailingIcon = {

                        if (
                            onDelete == null
                        ) {

                            Icon(
                                imageVector =
                                    Icons.Rounded
                                        .KeyboardArrowDown,
                                contentDescription =
                                    null,
                                tint =
                                    textDark,
                                modifier =
                                    Modifier.size(20.dp)
                            )
                        }
                    }
                )
            }

            if (showDatePicker) {

                val datePickerState =
                    rememberDatePickerState(
                        initialSelectedDateMillis =
                            dateMillis
                    )

                DatePickerDialog(

                    onDismissRequest = {
                        showDatePicker = false
                    },

                    confirmButton = {

                        TextButton(

                            onClick = {

                                datePickerState
                                    .selectedDateMillis
                                    ?.let {
                                        dateMillis = it
                                    }

                                showDatePicker = false
                            }

                        ) {

                            Text("OK")
                        }
                    },

                    dismissButton = {

                        TextButton(

                            onClick = {
                                showDatePicker = false
                            }

                        ) {

                            Text("Cancel")
                        }
                    }

                ) {

                    DatePicker(
                        state =
                            datePickerState
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            if (rawText != null) {

                Text(

                    text =
                        "Source: $rawText",

                    fontSize = 12.sp,

                    color =
                        Color.Gray,

                    modifier =
                        Modifier.padding(
                            bottom = 8.dp
                        )
                )
            }

            OutlinedTextField(

                value = note,

                onValueChange = {
                    note = it
                },

                readOnly = false,

                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),

                shape =
                    RoundedCornerShape(10.dp),

                colors =
                    OutlinedTextFieldDefaults
                        .colors(

                            unfocusedBorderColor =
                                Color(0xFFEBEBEB),

                            unfocusedContainerColor =
                                Color.White,

                            focusedContainerColor =
                                Color.White
                        ),

                textStyle =
                    TextStyle(
                        fontSize = 14.sp
                    ),

                placeholder = {

                    Text(
                        text =
                            "Add a note (optional)",
                        color =
                            Color.Gray,
                        fontSize = 14.sp
                    )
                },

                leadingIcon = {

                    Icon(
                        imageVector =
                            Icons.Outlined
                                .Description,
                        contentDescription =
                            null,
                        tint =
                            textDark,
                        modifier =
                            Modifier.size(20.dp)
                    )
                }
            )

            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                if (onDelete != null) {

                    Button(

                        onClick = {
                            onDelete()
                        },

                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),

                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor =
                                        Color(0xFFFFEBEE),
                                    contentColor =
                                        Color(0xFFD32F2F)
                                ),

                        shape =
                            RoundedCornerShape(
                                10.dp
                            )

                    ) {

                        Text(
                            text = "Delete",
                            fontWeight =
                                FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.width(12.dp)
                    )

                    Button(

                        onClick = {

                            val amt =
                                amount
                                    .toDoubleOrNull()
                                    ?: 0.0

                            if (amt <= 0.0) {
                                amountError = true
                            } else {
                                amountError = false
                                val resolvedMerchant = if (merchant.isNotBlank()) merchant.trim() else "Unknown Merchant"
                                onAdd(
                                    amt,
                                    resolvedMerchant,
                                    type,
                                    tag,
                                    note,
                                    dateMillis
                                )
                            }
                        },

                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),

                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor =
                                        primaryPurple,
                                    contentColor =
                                        Color.White
                                ),

                        shape =
                            RoundedCornerShape(
                                10.dp
                            )

                    ) {

                        Text(
                            text = "Complete",
                            fontWeight =
                                FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                } else {

                    Button(

                        onClick = onDismiss,

                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),

                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor =
                                        lightPurple,
                                    contentColor =
                                        primaryPurple
                                ),

                        shape =
                            RoundedCornerShape(
                                10.dp
                            )

                    ) {

                        Text(
                            text = "Cancel",
                            fontWeight =
                                FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.width(12.dp)
                    )

                    Button(

                        onClick = {

                            val amt =
                                amount
                                    .toDoubleOrNull()
                                    ?: 0.0

                            if (amt <= 0.0) {
                                amountError = true
                            } else {
                                amountError = false
                                val resolvedMerchant = if (merchant.isNotBlank()) merchant.trim() else "Unknown Merchant"
                                onAdd(
                                    amt,
                                    resolvedMerchant,
                                    type,
                                    tag,
                                    note,
                                    dateMillis
                                )
                            }
                        },

                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),

                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor =
                                        primaryPurple,
                                    contentColor =
                                        Color.White
                                ),

                        shape =
                            RoundedCornerShape(
                                10.dp
                            )

                    ) {

                        Text(

                            text =
                                if (isEditMode) {
                                    "Change"
                                } else {
                                    "Add Expense"
                                },
                            fontWeight =
                                FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}