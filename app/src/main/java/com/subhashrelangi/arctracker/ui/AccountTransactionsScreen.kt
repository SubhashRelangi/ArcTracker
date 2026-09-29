package com.subhashrelangi.arctracker.ui

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
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.KnownFinancialAccountRepository
import com.subhashrelangi.arctracker.service.AccountReconciliationManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountTransactionsScreen(
    accountId: String,
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

    val account = remember(accountId) { reconciliationManager.getAccountById(accountId) }
    val transactions by reconciliationManager.getLinkedTransactionsFlow(accountId).collectAsState(initial = emptyList())

    // Multi-Selection State (Parts 17, 18)
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedExpenseIds = remember { mutableStateListOf<Int>() }

    // Dialog state for Single Reassignment
    var expenseToReassign by remember { mutableStateOf<Expense?>(null) }
    var singleNewAccountSelected by remember { mutableStateOf<KnownFinancialAccount?>(null) }
    var showSingleReassignConfirmDialog by remember { mutableStateOf(false) }

    // Dialog state for Single Unlink
    var expenseToUnlink by remember { mutableStateOf<Expense?>(null) }

    // Dialog state for Bulk Reassignment
    var showBulkReassignPicker by remember { mutableStateOf(false) }
    var bulkNewAccountSelected by remember { mutableStateOf<KnownFinancialAccount?>(null) }
    var showBulkReassignConfirmDialog by remember { mutableStateOf(false) }

    // Dialog state for Bulk Unlink
    var showBulkUnlinkConfirmDialog by remember { mutableStateOf(false) }

    val allAccounts = remember { reconciliationManager.getAllKnownAccounts() }
    val otherAccounts = remember(allAccounts, accountId) { allAccounts.filter { it.id != accountId } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isSelectionMode) "${selectedExpenseIds.size} Selected" else (account?.institutionName ?: "Account Transactions"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isSelectionMode) "Select transactions to reassign or unlink" else {
                                account?.let {
                                    "${AccountReconciliationManager.formatAccountType(it.instrumentType, it.accountSuffix)} (${transactions.size} transactions)"
                                } ?: "${transactions.size} transactions"
                            },
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
                    if (transactions.isNotEmpty()) {
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
                        OutlinedButton(
                            onClick = { showBulkUnlinkConfirmDialog = true },
                            border = BorderStroke(1.dp, Color(0xFFC62828)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Unlink (${selectedExpenseIds.size})", color = Color(0xFFC62828), fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { showBulkReassignPicker = true },
                            colors = ButtonDefaults.buttonColors(containerColor = purpleColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Change Account", fontWeight = FontWeight.Bold)
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
            // Multi-Selection Controls
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
                        selectedExpenseIds.addAll(transactions.map { it.id })
                    }) {
                        Text("Select All (${transactions.size})", color = purpleColor)
                    }

                    TextButton(
                        onClick = { selectedExpenseIds.clear() },
                        enabled = selectedExpenseIds.isNotEmpty()
                    ) {
                        Text("Clear Selection", color = if (selectedExpenseIds.isNotEmpty()) purpleColor else subtitleColor)
                    }
                }
            }

            if (transactions.isEmpty()) {
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
                                .background(lightPurpleColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ReceiptLong,
                                contentDescription = "No Transactions",
                                tint = purpleColor,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No transactions linked to this account.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Transactions linked manually or matched from notifications will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleColor
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(transactions, key = { it.id }) { expense ->
                        val isSelected = selectedExpenseIds.contains(expense.id)
                        LinkedTransactionCard(
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
                            onChangeAccount = { expenseToReassign = expense },
                            onUnlink = { expenseToUnlink = expense }
                        )
                    }
                }
            }
        }
    }

    // --- Bulk Reassign Dialogs (Part 17) ---
    if (showBulkReassignPicker && !showBulkReassignConfirmDialog) {
        var tempBulkTarget by remember { mutableStateOf<KnownFinancialAccount?>(null) }
        AlertDialog(
            onDismissRequest = { showBulkReassignPicker = false },
            title = {
                Text(
                    text = "Reassign ${selectedExpenseIds.size} Transactions",
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
                        text = "Choose target account for ${selectedExpenseIds.size} selected transactions:",
                        fontSize = 13.sp,
                        color = subtitleColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (otherAccounts.isEmpty()) {
                        Text(
                            text = "No other known accounts available.",
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
                            items(otherAccounts, key = { it.id }) { candidate ->
                                val isSelected = tempBulkTarget?.id == candidate.id
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { tempBulkTarget = candidate },
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
                                            onClick = { tempBulkTarget = candidate },
                                            colors = RadioButtonDefaults.colors(selectedColor = purpleColor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = candidate.institutionName ?: "Unknown Bank",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = textColor
                                            )
                                            Text(
                                                text = AccountReconciliationManager.formatAccountType(
                                                    candidate.instrumentType,
                                                    candidate.accountSuffix
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
                            bulkNewAccountSelected = tempBulkTarget
                            showBulkReassignConfirmDialog = true
                        }
                    },
                    enabled = tempBulkTarget != null,
                    colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkReassignPicker = false }) {
                    Text("Cancel", color = textColor)
                }
            }
        )
    } else if (showBulkReassignConfirmDialog) {
        val targetNew = bulkNewAccountSelected
        if (targetNew != null) {
            AlertDialog(
                onDismissRequest = { showBulkReassignConfirmDialog = false },
                title = {
                    Text(
                        text = "Reassign ${selectedExpenseIds.size} transactions?",
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
                        DetailRow(
                            label = "From",
                            value = "${account?.institutionName ?: "Account"} (••••${account?.accountSuffix})"
                        )
                        DetailRow(
                            label = "To",
                            value = "${targetNew.institutionName ?: "Account"} (••••${targetNew.accountSuffix})",
                            isHighlight = true
                        )
                        HorizontalDivider(color = borderColor, thickness = 1.dp)
                        Text(
                            text = "This will update the account link for all ${selectedExpenseIds.size} transactions. Other transaction data will be preserved.",
                            fontSize = 12.sp,
                            color = subtitleColor
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val ids = selectedExpenseIds.toList()
                            val destId = targetNew.id
                            scope.launch {
                                val result = reconciliationManager.bulkReassign(ids, destId)
                                if (result.isSuccess) {
                                    snackbarHostState.showSnackbar("Reassigned ${result.getOrDefault(0)} transactions to ${targetNew.institutionName ?: "account"}")
                                    selectedExpenseIds.clear()
                                    isSelectionMode = false
                                } else {
                                    snackbarHostState.showSnackbar("Bulk reassignment failed: ${result.exceptionOrNull()?.message}")
                                }
                                showBulkReassignConfirmDialog = false
                                showBulkReassignPicker = false
                                bulkNewAccountSelected = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                    ) {
                        Text("Reassign")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBulkReassignConfirmDialog = false }) {
                        Text("Back", color = textColor)
                    }
                }
            )
        }
    }

    // --- Bulk Unlink Dialog (Part 18) ---
    if (showBulkUnlinkConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBulkUnlinkConfirmDialog = false },
            title = {
                Text(
                    text = "Unlink ${selectedExpenseIds.size} transactions?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "These ${selectedExpenseIds.size} transactions will return to the unresolved list.\n\nTheir account suffix information and financial data will be preserved.",
                    fontSize = 13.sp,
                    color = textColor
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ids = selectedExpenseIds.toList()
                        scope.launch {
                            val result = reconciliationManager.bulkUnlink(ids)
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("Unlinked ${result.getOrDefault(0)} transactions")
                                selectedExpenseIds.clear()
                                isSelectionMode = false
                            } else {
                                snackbarHostState.showSnackbar("Failed to unlink: ${result.exceptionOrNull()?.message}")
                            }
                            showBulkUnlinkConfirmDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Unlink")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkUnlinkConfirmDialog = false }) {
                    Text("Cancel", color = textColor)
                }
            }
        )
    }

    // --- Single Reassign Picker Dialog ---
    expenseToReassign?.let { expense ->
        val candidateAccounts = otherAccounts

        if (!showSingleReassignConfirmDialog) {
            AlertDialog(
                onDismissRequest = {
                    expenseToReassign = null
                    singleNewAccountSelected = null
                },
                title = {
                    Text(
                        text = "Change Account",
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
                            text = "Currently linked to: ${account?.institutionName ?: "Unknown"} (••••${account?.accountSuffix})",
                            fontSize = 12.sp,
                            color = subtitleColor
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (candidateAccounts.isEmpty()) {
                            Text(
                                text = "No other known accounts available.",
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
                                items(candidateAccounts, key = { it.id }) { candidate ->
                                    val isSelected = singleNewAccountSelected?.id == candidate.id
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { singleNewAccountSelected = candidate },
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
                                                onClick = { singleNewAccountSelected = candidate },
                                                colors = RadioButtonDefaults.colors(selectedColor = purpleColor)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = candidate.institutionName ?: "Unknown Bank",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = textColor
                                                )
                                                Text(
                                                    text = AccountReconciliationManager.formatAccountType(
                                                        candidate.instrumentType,
                                                        candidate.accountSuffix
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
                            if (singleNewAccountSelected != null) {
                                showSingleReassignConfirmDialog = true
                            }
                        },
                        enabled = singleNewAccountSelected != null,
                        colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                    ) {
                        Text("Continue")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        expenseToReassign = null
                        singleNewAccountSelected = null
                    }) {
                        Text("Cancel", color = textColor)
                    }
                }
            )
        } else {
            val targetNewAccount = singleNewAccountSelected
            if (targetNewAccount != null) {
                AlertDialog(
                    onDismissRequest = { showSingleReassignConfirmDialog = false },
                    title = {
                        Text(
                            text = "Confirm Account Change",
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
                            DetailRow(label = "Transaction", value = "₹${expense.amount} (${expense.merchant})")
                            DetailRow(
                                label = "From",
                                value = "${account?.institutionName ?: "Account"} (••••${account?.accountSuffix})"
                            )
                            DetailRow(
                                label = "To",
                                value = "${targetNewAccount.institutionName ?: "Account"} (••••${targetNewAccount.accountSuffix})",
                                isHighlight = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val targetExpense = expense
                                val destAccount = targetNewAccount
                                scope.launch {
                                    val result = reconciliationManager.reassignAccount(targetExpense.id, destAccount.id)
                                    if (result.isSuccess) {
                                        snackbarHostState.showSnackbar("Reassigned to ${destAccount.institutionName ?: "account"}")
                                    } else {
                                        snackbarHostState.showSnackbar("Reassignment failed: ${result.exceptionOrNull()?.message}")
                                    }
                                    showSingleReassignConfirmDialog = false
                                    expenseToReassign = null
                                    singleNewAccountSelected = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                        ) {
                            Text("Reassign Account")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSingleReassignConfirmDialog = false }) {
                            Text("Back", color = textColor)
                        }
                    }
                )
            }
        }
    }

    // --- Single Unlink Confirmation Dialog ---
    expenseToUnlink?.let { expense ->
        AlertDialog(
            onDismissRequest = { expenseToUnlink = null },
            title = {
                Text(
                    text = "Unlink Account?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Unlink this transaction (₹${expense.amount} - ${expense.merchant}) from ${account?.institutionName ?: "this account"} ••••${account?.accountSuffix}?\n\nThe transaction will return to the Unresolved list with its original suffix preserved.",
                    fontSize = 13.sp,
                    color = textColor
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetExpense = expense
                        scope.launch {
                            val result = reconciliationManager.unlinkAccount(targetExpense.id)
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("Transaction unlinked")
                            } else {
                                snackbarHostState.showSnackbar("Failed to unlink: ${result.exceptionOrNull()?.message}")
                            }
                            expenseToUnlink = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Unlink Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToUnlink = null }) {
                    Text("Cancel", color = textColor)
                }
            }
        )
    }
}

@Composable
fun LinkedTransactionCard(
    expense: Expense,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onChangeAccount: () -> Unit,
    onUnlink: () -> Unit
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

                if (!isSelectionMode) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onUnlink,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                        ) {
                            Text(
                                text = "Unlink",
                                fontSize = 11.sp,
                                color = Color(0xFFC62828),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = onChangeAccount,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                        ) {
                            Text(
                                text = "Change Account",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
