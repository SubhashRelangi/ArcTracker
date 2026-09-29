package com.example.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.arctracker.data.AppDatabase
import com.example.arctracker.data.Expense
import com.example.arctracker.data.KnownFinancialAccount
import com.example.arctracker.data.KnownFinancialAccountRepository
import com.example.arctracker.service.AccountReconciliationManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class UnresolvedFilter {
    ALL,
    WITH_SUFFIX,
    NO_SUFFIX
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnresolvedTransactionsScreen(
    onNavigateBack: () -> Unit,
    manager: AccountReconciliationManager? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val reconciliationManager = remember {
        manager ?: run {
            val db = AppDatabase.getDatabase(context)
            val repo = KnownFinancialAccountRepository(db.knownFinancialAccountDao())
            AccountReconciliationManager(db.expenseDao(), repo, db)
        }
    }

    val unresolvedExpenses by reconciliationManager.getUnresolvedTransactionsFlow().collectAsState(initial = emptyList())

    var currentFilter by remember { mutableStateOf(UnresolvedFilter.ALL) }

    // Multi-Selection State (Parts 13, 21)
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedExpenseIds = remember { mutableStateListOf<Int>() }

    // Dialog states for Single Assignment
    var singleTransactionToAssign by remember { mutableStateOf<Expense?>(null) }
    var singleAccountToLink by remember { mutableStateOf<KnownFinancialAccount?>(null) }
    var showSingleConfirmDialog by remember { mutableStateOf(false) }

    // Dialog states for Bulk Assignment (Parts 14, 15)
    var showBulkAssignPicker by remember { mutableStateOf(false) }
    var bulkTargetAccount by remember { mutableStateOf<KnownFinancialAccount?>(null) }
    var showBulkConfirmDialog by remember { mutableStateOf(false) }

    val allKnownAccounts = remember { reconciliationManager.getAllKnownAccounts() }

    val filteredExpenses = remember(unresolvedExpenses, currentFilter) {
        when (currentFilter) {
            UnresolvedFilter.ALL -> unresolvedExpenses
            UnresolvedFilter.WITH_SUFFIX -> unresolvedExpenses.filter { !it.accountSuffix.isNullOrBlank() }
            UnresolvedFilter.NO_SUFFIX -> unresolvedExpenses.filter { it.accountSuffix.isNullOrBlank() }
        }
    }

    val withSuffixCount = unresolvedExpenses.count { !it.accountSuffix.isNullOrBlank() }
    val noSuffixCount = unresolvedExpenses.count { it.accountSuffix.isNullOrBlank() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isSelectionMode) "${selectedExpenseIds.size} Selected" else "Unresolved Transactions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isSelectionMode) "Select transactions to assign in bulk" else "${unresolvedExpenses.size} transaction(s) pending account link",
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleColor
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) {
                            isSelectionMode = false
                            selectedExpenseIds.clear()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = if (isSelectionMode) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isSelectionMode) "Close Selection" else "Back"
                        )
                    }
                },
                actions = {
                    if (filteredExpenses.isNotEmpty()) {
                        TextButton(onClick = {
                            isSelectionMode = !isSelectionMode
                            if (!isSelectionMode) {
                                selectedExpenseIds.clear()
                            }
                        }) {
                            Text(
                                text = if (isSelectionMode) "Done" else "Select",
                                color = purpleColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            if (isSelectionMode && selectedExpenseIds.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = Color.White
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedExpenseIds.size} selected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = textColor
                        )

                        Button(
                            onClick = { showBulkAssignPicker = true },
                            colors = ButtonDefaults.buttonColors(containerColor = purpleColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Assign Account", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF9F9FB))
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Multi-Selection Controls (Select All / Clear)
            if (isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        selectedExpenseIds.clear()
                        selectedExpenseIds.addAll(filteredExpenses.map { it.id })
                    }) {
                        Text("Select All (${filteredExpenses.size})", color = purpleColor)
                    }

                    TextButton(
                        onClick = { selectedExpenseIds.clear() },
                        enabled = selectedExpenseIds.isNotEmpty()
                    ) {
                        Text("Clear Selection", color = if (selectedExpenseIds.isNotEmpty()) purpleColor else subtitleColor)
                    }
                }
            }

            // Filter Chips: All, With Suffix, No Suffix
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = currentFilter == UnresolvedFilter.ALL,
                    onClick = { currentFilter = UnresolvedFilter.ALL },
                    label = { Text("All (${unresolvedExpenses.size})", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = purpleColor,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = currentFilter == UnresolvedFilter.WITH_SUFFIX,
                    onClick = { currentFilter = UnresolvedFilter.WITH_SUFFIX },
                    label = { Text("With Suffix ($withSuffixCount)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = purpleColor,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = currentFilter == UnresolvedFilter.NO_SUFFIX,
                    onClick = { currentFilter = UnresolvedFilter.NO_SUFFIX },
                    label = { Text("No Suffix ($noSuffixCount)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = purpleColor,
                        selectedLabelColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredExpenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFFE8F5E9), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Clear",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (unresolvedExpenses.isEmpty()) "All transactions are linked to a known account." else "No transactions match this filter.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredExpenses, key = { it.id }) { expense ->
                        val isSelected = selectedExpenseIds.contains(expense.id)
                        UnresolvedTransactionCard(
                            expense = expense,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onToggleSelection = {
                                if (isSelected) {
                                    selectedExpenseIds.remove(expense.id)
                                } else {
                                    selectedExpenseIds.add(expense.id)
                                }
                            },
                            onAssignClick = {
                                singleTransactionToAssign = expense
                            }
                        )
                    }
                }
            }
        }
    }

    // --- Bulk Assign Dialogs (Parts 14, 15, 16) ---
    if (showBulkAssignPicker && !showBulkConfirmDialog) {
        var tempBulkTarget by remember { mutableStateOf<KnownFinancialAccount?>(null) }
        AlertDialog(
            onDismissRequest = { showBulkAssignPicker = false },
            title = {
                Text(
                    text = "Assign ${selectedExpenseIds.size} Transactions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Choose a known account to link all ${selectedExpenseIds.size} selected transactions:",
                        fontSize = 13.sp,
                        color = subtitleColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (allKnownAccounts.isEmpty()) {
                        Text(
                            text = "No known accounts available in registry.",
                            fontSize = 12.sp,
                            color = subtitleColor
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(allKnownAccounts, key = { it.id }) { account ->
                                val isSelected = tempBulkTarget?.id == account.id
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { tempBulkTarget = account },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) lightPurpleColor else Color(0xFFF9F9FB),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) purpleColor else borderColor
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { tempBulkTarget = account },
                                            colors = RadioButtonDefaults.colors(selectedColor = purpleColor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = account.institutionName ?: "Unknown Bank",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = textColor
                                            )
                                            Text(
                                                text = AccountReconciliationManager.formatAccountType(
                                                    account.instrumentType,
                                                    account.accountSuffix
                                                ),
                                                fontSize = 11.sp,
                                                color = subtitleColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempBulkTarget != null) {
                            bulkTargetAccount = tempBulkTarget
                            showBulkConfirmDialog = true
                        }
                    },
                    enabled = tempBulkTarget != null,
                    colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkAssignPicker = false }) {
                    Text("Cancel", color = textColor)
                }
            }
        )
    } else if (showBulkConfirmDialog) {
        val target = bulkTargetAccount
        if (target != null) {
            AlertDialog(
                onDismissRequest = { showBulkConfirmDialog = false },
                title = {
                    Text(
                        text = "Assign ${selectedExpenseIds.size} transactions?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailRow(label = "Selected Transactions", value = "${selectedExpenseIds.size}")
                        DetailRow(label = "Target Account", value = target.institutionName ?: "Unknown")
                        DetailRow(
                            label = "Account Type",
                            value = AccountReconciliationManager.formatAccountType(
                                target.instrumentType,
                                target.accountSuffix
                            )
                        )
                        HorizontalDivider(color = borderColor, thickness = 1.dp)
                        Text(
                            text = "This will link all ${selectedExpenseIds.size} selected transactions to this account. Transaction amounts and other transaction data will not be changed.",
                            fontSize = 12.sp,
                            color = subtitleColor
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val ids = selectedExpenseIds.toList()
                            val tId = target.id
                            scope.launch {
                                val result = reconciliationManager.bulkAssign(ids, tId)
                                if (result.isSuccess) {
                                    snackbarHostState.showSnackbar("Linked ${result.getOrDefault(0)} transactions to ${target.institutionName ?: "account"}")
                                    selectedExpenseIds.clear()
                                    isSelectionMode = false
                                } else {
                                    snackbarHostState.showSnackbar("Bulk assign failed: ${result.exceptionOrNull()?.message}")
                                }
                                showBulkConfirmDialog = false
                                showBulkAssignPicker = false
                                bulkTargetAccount = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                    ) {
                        Text("Assign Account")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBulkConfirmDialog = false }) {
                        Text("Back", color = textColor)
                    }
                }
            )
        }
    }

    // --- Single Transaction Assignment Dialogs ---
    singleTransactionToAssign?.let { expense ->
        val candidateAccounts = remember(expense) {
            reconciliationManager.getCompatibleAccounts(expense)
        }

        var candidateSelected by remember { mutableStateOf<KnownFinancialAccount?>(null) }

        if (!showSingleConfirmDialog) {
            AlertDialog(
                onDismissRequest = { singleTransactionToAssign = null },
                title = {
                    Text(
                        text = "Assign Account",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "Select account for ₹${expense.amount} (${expense.merchant})",
                            fontSize = 13.sp,
                            color = subtitleColor
                        )
                        if (!expense.accountSuffix.isNullOrBlank()) {
                            Text(
                                text = "Evidence: ending in ••••${expense.accountSuffix}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = purpleColor
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        if (candidateAccounts.isEmpty()) {
                            Text(
                                text = "No known accounts available in registry. Scan SMS or wait for live notifications to discover accounts.",
                                fontSize = 12.sp,
                                color = subtitleColor
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(candidateAccounts, key = { it.id }) { account ->
                                    val isSelected = candidateSelected?.id == account.id
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { candidateSelected = account },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) lightPurpleColor else Color(0xFFF9F9FB),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) purpleColor else borderColor
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { candidateSelected = account },
                                                colors = RadioButtonDefaults.colors(selectedColor = purpleColor)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = account.institutionName ?: "Unknown Bank",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = textColor
                                                )
                                                Text(
                                                    text = AccountReconciliationManager.formatAccountType(
                                                        account.instrumentType,
                                                        account.accountSuffix
                                                    ),
                                                    fontSize = 11.sp,
                                                    color = subtitleColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (candidateSelected != null) {
                                singleAccountToLink = candidateSelected
                                showSingleConfirmDialog = true
                            }
                        },
                        enabled = candidateSelected != null,
                        colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                    ) {
                        Text("Continue")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { singleTransactionToAssign = null }) {
                        Text("Cancel", color = textColor)
                    }
                }
            )
        } else {
            val selectedAccount = singleAccountToLink
            if (selectedAccount != null) {
                AlertDialog(
                    onDismissRequest = { showSingleConfirmDialog = false },
                    title = {
                        Text(
                            text = "Link transaction to account?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DetailRow(label = "Amount", value = "₹${expense.amount} (${expense.type})")
                            DetailRow(label = "Merchant", value = expense.merchant)
                            if (!expense.accountSuffix.isNullOrBlank()) {
                                DetailRow(label = "Transaction Suffix", value = "••••${expense.accountSuffix}")
                            }
                            HorizontalDivider(color = borderColor, thickness = 1.dp)
                            DetailRow(label = "Account", value = selectedAccount.institutionName ?: "Unknown")
                            DetailRow(
                                label = "Account Type",
                                value = AccountReconciliationManager.formatAccountType(
                                    selectedAccount.instrumentType,
                                    selectedAccount.accountSuffix
                                )
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val targetExpense = expense
                                val targetAccount = selectedAccount
                                scope.launch {
                                    val result = reconciliationManager.assignAccount(targetExpense.id, targetAccount.id)
                                    if (result.isSuccess) {
                                        snackbarHostState.showSnackbar("Transaction successfully linked to ${targetAccount.institutionName ?: "account"}")
                                    } else {
                                        snackbarHostState.showSnackbar("Failed to link transaction: ${result.exceptionOrNull()?.message}")
                                    }
                                    showSingleConfirmDialog = false
                                    singleTransactionToAssign = null
                                    singleAccountToLink = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                        ) {
                            Text("Link Account")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSingleConfirmDialog = false }) {
                            Text("Back", color = textColor)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun UnresolvedTransactionCard(
    expense: Expense,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onAssignClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isSelectionMode) { onToggleSelection() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) lightPurpleColor else Color.White),
        border = BorderStroke(1.dp, if (isSelected) purpleColor else borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    colors = CheckboxDefaults.colors(checkedColor = purpleColor),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (expense.merchant.isBlank()) "Transaction" else expense.merchant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = textColor
                        )
                        Text(
                            text = dateFormat.format(Date(expense.dateMillis)),
                            fontSize = 11.sp,
                            color = subtitleColor
                        )
                    }

                    Text(
                        text = "₹${expense.amount}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (expense.type.equals("CREDIT", ignoreCase = true)) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (!expense.accountSuffix.isNullOrBlank()) {
                                    "Account ending ••••${expense.accountSuffix}"
                                } else {
                                    "Account information unavailable"
                                },
                                fontSize = 11.sp,
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (!isSelectionMode) {
                        Button(
                            onClick = onAssignClick,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                        ) {
                            Text(
                                text = "Assign Account",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
