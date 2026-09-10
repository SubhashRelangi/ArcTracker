package com.example.arctracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arctracker.data.AppDatabase
import com.example.arctracker.data.Expense
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    val dao = database.expenseDao()
    
    // Using Flow automatically refreshes when the DB changes, but we'll add a refresh button as requested
    // to manually trigger a re-composition or force a UI update if the user wants it.
    var refreshTrigger by remember { mutableStateOf(0) }
    val expenses by dao.getAllExpenses().collectAsState(initial = emptyList())
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showApproveDialog by remember { mutableStateOf<Expense?>(null) }
    var currentRoute by remember { mutableStateOf("Home") }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val displayedExpenses = if (searchQuery.isNotBlank()) {
        expenses.filter { it.merchant.contains(searchQuery, ignoreCase = true) }
    } else {
        expenses
    }

    fun isNotificationServiceEnabled(): Boolean {
        val pkgName = context.packageName
        val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(pkgName)
    }

    var hasPermission by remember { mutableStateOf(isNotificationServiceEnabled()) }
    
    val sharedPrefs = remember { context.getSharedPreferences("ArcTrackerPrefs", android.content.Context.MODE_PRIVATE) }
    val isInitialSetupCompleted = sharedPrefs.getBoolean("isInitialSetupCompleted", false)
    
    val isExistingUser = remember {
        val dbExists = context.getDatabasePath("arctracker_database").exists()
        val hasPrefs = sharedPrefs.contains("isAutoTrackingEnabled")
        dbExists || hasPrefs
    }
    
    var localSetupCompleted by remember { mutableStateOf(isInitialSetupCompleted) }

    LaunchedEffect(isInitialSetupCompleted) {
        if (!isInitialSetupCompleted && isExistingUser) {
            // Silently mark as complete for existing users
            sharedPrefs.edit().putBoolean("isInitialSetupCompleted", true).apply()
            localSetupCompleted = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (currentRoute) {
                        "Database" -> Text("Database")
                        "ClearAllData" -> Text("Clear All Data")
                        "SmsImport" -> Text("Import SMS")
                        "Pending" -> Text("Pending Expenses")
                        else -> Text("ArcTracker")
                    }
                },
                    navigationIcon = {
                        when (currentRoute) {
                            "Database", "ClearAllData", "SmsImport" -> {
                                IconButton(onClick = { currentRoute = "Settings" }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            }
                            "Pending" -> {
                                IconButton(onClick = { currentRoute = "Home" }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    actions = {
                        if (currentRoute == "Home") {
                            IconButton(onClick = { refreshTrigger++ }) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "Refresh"
                                )
                            }

                            IconButton(onClick = {
                                isSearching = !isSearching
                                if (!isSearching) {
                                    searchQuery = ""
                                }
                            }) {
                                Icon(
                                    imageVector = if (isSearching) {
                                        Icons.Filled.Close
                                    } else {
                                        Icons.Filled.Search
                                    },
                                    contentDescription = if (isSearching) {
                                        "Close Search"
                                    } else {
                                        "Search"
                                    }
                                )
                            }

                            IconButton(onClick = { currentRoute = "Pending" }) {
                                BadgedBox(
                                    badge = {
                                        val pendingCount = expenses.count { it.isPending }
                                        if (pendingCount > 0) {
                                            Badge {
                                                Text(pendingCount.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.PendingActions,
                                        contentDescription = "Pending Expenses"
                                    )
                                }
                            }
                        }
                    }
                )
        },
        bottomBar = {
            if (currentRoute != "ClearAllData" && currentRoute != "Pending" && currentRoute != "SmsImport") {
                com.example.arctracker.ui.FloatingNavigationBar(
                    currentRoute = currentRoute,
                    onNavigate = { currentRoute = it.title }
                )
            }
        },
        floatingActionButton = {
            if (currentRoute == "Home") {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Expense")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            
            AnimatedVisibility(visible = isSearching) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search transactions by name...") },
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), // Added border radius
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = "Search Icon", tint = MaterialTheme.colorScheme.primary)
                    }
                )
            }

            if (currentRoute == "Settings") {
                com.example.arctracker.ui.SettingsScreen(onNavigate = { currentRoute = it })
            } else if (currentRoute == "ClearAllData") {
                // ClearAllData is full screen, so we don't need padding from Scaffold here if we were using it, 
                // but since it's inside the Column in Scaffold, we'll just show it.
                // To remove the "ArcTracker" top bar and nav bar, we need to handle it in Scaffold.
                com.example.arctracker.ui.ClearAllDataScreen(onNavigate = { currentRoute = it })
            } else if (currentRoute == "SmsImport") {
                com.example.arctracker.ui.SmsImportScreen(onNavigateBack = { currentRoute = "Settings" })
            } else if (currentRoute == "Database") {
                com.example.arctracker.ui.DatabaseScreen()
            } else if (currentRoute == "Pending") {
                val pendingExpenses = expenses.filter { it.isPending }
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    if (pendingExpenses.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No pending expenses!", color = Color.Gray)
                            }
                        }
                    } else {
                        items(pendingExpenses) { expense ->
                            ExpenseItemRow(
                                expense = expense,
                                isLast = false,
                                onClick = { showApproveDialog = expense }
                            )
                        }
                    }
                }
            } else if (currentRoute != "Home") {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("$currentRoute Screen Coming Soon!", style = MaterialTheme.typography.titleLarge)
                }
            } else {

            // Using refreshTrigger just to satisfy the compose compiler that we are observing it
            val trigger = refreshTrigger 
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    com.example.arctracker.ui.DashboardCard(expenses = expenses)
                    
                    com.example.arctracker.ui.SpendingOverviewCard(expenses = expenses)
                    // Recent Transactions Section
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFF0F0F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column {
                            // Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Transactions",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = androidx.compose.ui.graphics.Color(0xFF1E1E1E)
                                )
                                Text(
                                    text = "View All",
                                    fontSize = 14.sp,
                                    color = androidx.compose.ui.graphics.Color(0xFF673AB7),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (displayedExpenses.isEmpty()) {
                                Text(
                                    text = if (isSearching) "No matching transactions found." else "No expenses yet.",
                                    modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
                                    color = androidx.compose.ui.graphics.Color(0xFF757575)
                                )
                            } else {
                                // Display up to 5 items in this card for the dashboard
                                displayedExpenses.take(5).forEachIndexed { index, expense ->
                                    val isLast = index == minOf(displayedExpenses.size, 5) - 1
                                    ExpenseItemRow(
                                        expense = expense,
                                        isLast = isLast,
                                        onClick = {
                                            if (expense.isPending) {
                                                showApproveDialog = expense
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }

        if (showAddDialog) {
            AddExpenseDialog(
                initialAmount = "",
                initialMerchant = "",
                initialType = "Debit",
                initialNote = "",
                initialDateMillis = System.currentTimeMillis(),
                onDismiss = { showAddDialog = false },
                onAdd = { amount, merchant, type, tag, note, dateMillis ->
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        dao.insertExpense(
                            Expense(
                                amount,
                                merchant,
                                dateMillis,
                                type,
                                UUID.randomUUID().toString(),
                                false,
                                "Manual Entry",
                                tag,
                                note,
                                "MANUAL"
                            )
                        )
                        showAddDialog = false
                    }
                }
            )
        }
        
        showApproveDialog?.let { pendingExpense ->
            AddExpenseDialog(
                initialAmount = if (pendingExpense.amount > 0) pendingExpense.amount.toString() else "",
                initialMerchant = if (pendingExpense.merchant != "Unknown Merchant") pendingExpense.merchant else "",
                initialType = pendingExpense.type ?: "Debit",
                initialNote = pendingExpense.note ?: "",
                initialDateMillis = pendingExpense.dateMillis,
                rawText = pendingExpense.rawText,
                onDismiss = { showApproveDialog = null },
                onDelete = {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        dao.deleteExpense(pendingExpense)
                        showApproveDialog = null
                    }
                },
                onAdd = { amount, merchant, type, tag, note, dateMillis ->
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        pendingExpense.amount = amount
                        pendingExpense.merchant = merchant
                        pendingExpense.type = type
                        pendingExpense.isPending = false
                        pendingExpense.tag = tag
                        pendingExpense.note = note
                        dao.updateExpense(pendingExpense)
                        showApproveDialog = null
                    }
                }
            )
        }
        
        if (!localSetupCompleted && !isExistingUser) {
            com.example.arctracker.ui.FirstTimeSetupDialogs(onComplete = {
                sharedPrefs.edit().putBoolean("isInitialSetupCompleted", true).apply()
                localSetupCompleted = true
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseItemRow(expense: Expense, isLast: Boolean, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateString = dateFormat.format(Date(expense.dateMillis))
    
    val isCredit = expense.type == "Credit"
    val isPending = expense.isPending

    val iconBgColor = if (isCredit) androidx.compose.ui.graphics.Color(0xFFE8F5E9) else androidx.compose.ui.graphics.Color(0xFFFFEBEE)
    val iconColor = if (isCredit) androidx.compose.ui.graphics.Color(0xFF4CAF50) else androidx.compose.ui.graphics.Color(0xFFF44336)
    val icon = if (isCredit) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown

    val amountColor = if (isCredit) androidx.compose.ui.graphics.Color(0xFF4CAF50) else androidx.compose.ui.graphics.Color(0xFFD32F2F)
    val sign = if (isCredit) "+" else "-"

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                // Circular Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(iconBgColor, androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = expense.type,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Name and Date
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = expense.merchant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = androidx.compose.ui.graphics.Color(0xFF1E1E1E)
                    )
                    Text(
                        text = "$dateString" + (if (expense.tag != null) " • ${expense.tag}" else "") + (if (!expense.note.isNullOrBlank()) " • ${expense.note}" else ""),
                        fontSize = 13.sp,
                        color = androidx.compose.ui.graphics.Color(0xFF757575)
                    )
                }
                
                // Amount and Type
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text(
                        text = "$sign₹${expense.amount}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = amountColor
                    )
                    Text(
                        text = expense.type,
                        fontSize = 13.sp,
                        color = androidx.compose.ui.graphics.Color(0xFF757575)
                    )
                }
            }
            
            if (!isLast) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 76.dp, end = 16.dp),
                    color = androidx.compose.ui.graphics.Color(0xFFF5F5F5),
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
    initialNote: String = "",
    initialDateMillis: Long = System.currentTimeMillis(),
    rawText: String? = null,
    onDismiss: () -> Unit, 
    onDelete: (() -> Unit)? = null,
    onAdd: (Double, String, String, String, String, Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amount by remember { mutableStateOf(initialAmount) }
    var merchant by remember { mutableStateOf(initialMerchant) }
    var note by remember { mutableStateOf(initialNote) }
    var type by remember { mutableStateOf(initialType) }
    var tag by remember { mutableStateOf(if (initialType == "Credit") "Salary" else "Food") }
    var dateMillis by remember { mutableStateOf(initialDateMillis) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    val primaryPurple = Color(0xFF7859C1)
    val lightPurple = Color(0xFFF3EFFF)
    val bgGray = Color(0xFFF9F9FB)
    val textDark = Color(0xFF1E1E1E)

    var isAnimating by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isAnimating) 0.95f else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy),
        finishedListener = {
            if (isAnimating) {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (amt > 0 && merchant.isNotBlank()) {
                    onAdd(amt, merchant, type, tag, note, dateMillis)
                } else {
                    isAnimating = false // Reset if invalid
                }
            }
        }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFFFBF8FF),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(lightPurple, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = primaryPurple, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(if (rawText != null) "Approve Expense" else "Add Expense", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textDark)
                        Text(if (rawText != null) "Review detected transaction" else "Add a transaction manually", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(bgGray, shape = CircleShape).size(36.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = textDark, modifier = Modifier.size(20.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Amount Input
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                readOnly = onDelete != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                textStyle = TextStyle(fontSize = 24.sp, color = textDark),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFFEBEBEB),
                    focusedBorderColor = primaryPurple,
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                leadingIcon = { Text("₹", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textDark, modifier = Modifier.padding(start = 12.dp)) },
                trailingIcon = { 
                    Box(modifier = Modifier.padding(end = 8.dp).background(bgGray, shape = RoundedCornerShape(8.dp)).padding(6.dp)) {
                        Icon(Icons.Outlined.Calculate, contentDescription = null, tint = textDark, modifier = Modifier.size(20.dp))
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Merchant Input
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                readOnly = onDelete != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFFEBEBEB),
                    focusedBorderColor = primaryPurple,
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                ),
                placeholder = {
                    Column(modifier = Modifier.padding(top = 2.dp)) {
                        Text("Merchant / Person", fontSize = 14.sp, color = Color.Gray)
                        Text("e.g. Zomato, Amazon, Mom", fontSize = 10.sp, color = Color.LightGray)
                    }
                },
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = textDark) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Category Section
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Category", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textDark)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("See all", fontSize = 12.sp, color = Color.Gray)
                    Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val categories = if (type == "Debit") {
                    listOf(
                        "Food" to Icons.Outlined.Fastfood,
                        "Travel" to Icons.Outlined.DirectionsCar,
                        "Bills" to Icons.Outlined.Receipt,
                        "Shopping" to Icons.Outlined.ShoppingBag,
                        "Other" to Icons.Outlined.MoreHoriz
                    )
                } else {
                    listOf(
                        "Salary" to Icons.Outlined.Payments,
                        "Allowance" to Icons.Outlined.Savings,
                        "Refund" to Icons.Outlined.Replay,
                        "Gift" to Icons.Outlined.CardGiftcard,
                        "Other" to Icons.Outlined.MoreHoriz
                    )
                }
                categories.forEach { (catName, iconRes) ->
                    val isSelected = tag == catName
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .background(if (isSelected) lightPurple else bgGray, shape = RoundedCornerShape(10.dp))
                            .clickable { tag = catName }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(iconRes, contentDescription = catName, tint = if (isSelected) primaryPurple else textDark, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(catName, fontSize = 10.sp, color = if (isSelected) primaryPurple else textDark, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Type Section
            Text("Type", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textDark)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                val isDebit = type == "Debit"
                Box(modifier = Modifier
                    .weight(1f)
                    .background(if (isDebit) lightPurple else Color.White, shape = RoundedCornerShape(10.dp))
                    .border(1.dp, if (isDebit) primaryPurple else Color(0xFFEBEBEB), RoundedCornerShape(10.dp))
                    .clickable(enabled = onDelete == null) { 
                        type = "Debit"
                        tag = "Food"
                    }
                    .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = null, tint = if (isDebit) primaryPurple else textDark, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Expense (Debit)", fontSize = 13.sp, color = if (isDebit) primaryPurple else textDark, fontWeight = if (isDebit) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier
                    .weight(1f)
                    .background(if (!isDebit) lightPurple else Color.White, shape = RoundedCornerShape(10.dp))
                    .border(1.dp, if (!isDebit) primaryPurple else Color(0xFFEBEBEB), RoundedCornerShape(10.dp))
                    .clickable(enabled = onDelete == null) { 
                        type = "Credit"
                        tag = "Salary"
                    }
                    .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, tint = if (!isDebit) primaryPurple else textDark, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Income (Credit)", fontSize = 13.sp, color = if (!isDebit) primaryPurple else textDark, fontWeight = if (!isDebit) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Date input
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            val dateStr = sdf.format(java.util.Date(dateMillis))
            Box(modifier = Modifier.fillMaxWidth().clickable(enabled = onDelete == null) { showDatePicker = true }) {
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = Color(0xFFEBEBEB),
                        disabledContainerColor = Color.White,
                        disabledTextColor = textDark,
                        disabledLeadingIconColor = textDark,
                        disabledTrailingIconColor = textDark
                    ),
                    textStyle = TextStyle(fontSize = 14.sp),
                    leadingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = textDark, modifier = Modifier.size(20.dp)) },
                    trailingIcon = { 
                        if (onDelete == null) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = textDark, modifier = Modifier.size(20.dp))
                        }
                    }
                )
            }

            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { dateMillis = it }
                            showDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Raw Text and Note input
            if (rawText != null) {
                Text(
                    text = "Source: $rawText",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it }, 
                readOnly = false,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFFEBEBEB),
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                ),
                textStyle = TextStyle(fontSize = 14.sp),
                placeholder = { Text("Add a note (optional)", color = Color.Gray, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null, tint = textDark, modifier = Modifier.size(20.dp)) }
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Buttons
            Row(modifier = Modifier.fillMaxWidth()) {
                if (onDelete != null) {
                    Button(
                        onClick = { onDelete() },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE), contentColor = Color(0xFFD32F2F)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val amt = amount.toDoubleOrNull() ?: 0.0
                            if (amt > 0 && merchant.isNotBlank()) {
                                isAnimating = true
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryPurple, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Complete", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = lightPurple, contentColor = primaryPurple),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val amt = amount.toDoubleOrNull() ?: 0.0
                            if (amt > 0 && merchant.isNotBlank()) {
                                isAnimating = true
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryPurple, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Add Expense", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}