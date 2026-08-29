package com.example.arctracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.arctracker.data.AppDatabase
import com.example.arctracker.data.Expense
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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

    fun isNotificationServiceEnabled(): Boolean {
        val pkgName = context.packageName
        val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(pkgName)
    }

    var hasPermission by remember { mutableStateOf(isNotificationServiceEnabled()) }
    
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermission = isNotificationServiceEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ArcTracker MVP") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        bottomBar = {
            com.example.arctracker.ui.FloatingNavigationBar(
                currentRoute = currentRoute,
                onNavigate = { currentRoute = it.title }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Expense")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (currentRoute != "Home") {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("$currentRoute Screen Coming Soon!", style = MaterialTheme.typography.titleLarge)
                }
            } else {
                if (!hasPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Notification Access Required",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "To automatically track expenses, please enable Notification Access.",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Enable Now", color = MaterialTheme.colorScheme.onError)
                        }
                    }
                }
            }

            // Using refreshTrigger just to satisfy the compose compiler that we are observing it
            val trigger = refreshTrigger 
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    com.example.arctracker.ui.DashboardCard(expenses = expenses)
                    
                    com.example.arctracker.ui.SpendingOverviewCard(expenses = expenses)
                    
                    Text(
                        text = "Recent Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    )
                }
                
                if (expenses.isEmpty()) {
                    item {
                        Text(
                            text = "No expenses yet. Waiting for notifications...",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(expenses) { expense ->
                        ExpenseItem(expense, onClick = {
                            if (expense.isPending) {
                                showApproveDialog = expense
                            }
                        })
                    }
                }
            }
            }
        }

        if (showAddDialog) {
            AddExpenseDialog(
                initialAmount = "",
                initialMerchant = "",
                onDismiss = { showAddDialog = false },
                onAdd = { amount, merchant, type ->
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        dao.insertExpense(
                            Expense(
                                amount,
                                merchant,
                                System.currentTimeMillis(),
                                type,
                                UUID.randomUUID().toString(),
                                false,
                                "Manual Entry"
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
                rawText = pendingExpense.rawText,
                onDismiss = { showApproveDialog = null },
                onAdd = { amount, merchant, type ->
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        pendingExpense.amount = amount
                        pendingExpense.merchant = merchant
                        pendingExpense.type = type
                        pendingExpense.isPending = false
                        dao.updateExpense(pendingExpense)
                        showApproveDialog = null
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseItem(expense: Expense, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val dateString = dateFormat.format(Date(expense.dateMillis))
    
    val cardColor = if (expense.isPending) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (expense.isPending) "Needs Review" else expense.merchant, 
                        fontWeight = FontWeight.Bold, 
                        style = MaterialTheme.typography.titleMedium,
                        color = if (expense.isPending) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    Text(text = dateString, style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    val isCredit = expense.type == "Credit"
                    val sign = if (isCredit) "+" else "-"
                    val amountColor = if (expense.isPending) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else if (isCredit) {
                        androidx.compose.ui.graphics.Color(0xFF388E3C) // Green for Credit
                    } else {
                        androidx.compose.ui.graphics.Color(0xFFD32F2F) // Red for Debit
                    }
                    
                    Text(
                        text = "$sign₹${expense.amount}",
                        fontWeight = FontWeight.Bold,
                        color = amountColor
                    )
                    Text(text = expense.type, style = MaterialTheme.typography.bodySmall)
                }
            }
            
            if (expense.isPending && expense.rawText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Raw Msg: ${expense.rawText}", 
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tap to approve and categorize", 
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun AddExpenseDialog(
    initialAmount: String,
    initialMerchant: String,
    rawText: String? = null,
    onDismiss: () -> Unit, 
    onAdd: (Double, String, String) -> Unit
) {
    var amount by remember { mutableStateOf(initialAmount) }
    var merchant by remember { mutableStateOf(initialMerchant) }
    var type by remember { mutableStateOf("Debit") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rawText != null) "Approve Expense" else "Add Manual Expense") },
        text = {
            Column {
                if (rawText != null) {
                    Text("Original Message:", style = MaterialTheme.typography.labelMedium)
                    Text(rawText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp))
                }
                
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    RadioButton(
                        selected = type == "Debit",
                        onClick = { type = "Debit" }
                    )
                    Text("Debit", modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 12.dp))
                    
                    RadioButton(
                        selected = type == "Credit",
                        onClick = { type = "Credit" }
                    )
                    Text("Credit", modifier = Modifier.padding(start = 8.dp, top = 12.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (amt > 0 && merchant.isNotBlank()) {
                    onAdd(amt, merchant, type)
                }
            }) {
                Text(if (rawText != null) "Approve" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}