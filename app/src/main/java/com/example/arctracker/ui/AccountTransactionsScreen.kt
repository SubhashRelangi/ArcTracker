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
            AccountReconciliationManager(db.expenseDao(), repo)
        }
    }

    val account = remember(accountId) { reconciliationManager.getAccountById(accountId) }
    val transactions by reconciliationManager.getLinkedTransactionsFlow(accountId).collectAsState(initial = emptyList())

    // Dialog state for Reassignment
    var expenseToReassign by remember { mutableStateOf<Expense?>(null) }
    var newAccountSelected by remember { mutableStateOf<KnownFinancialAccount?>(null) }
    var showReassignConfirmDialog by remember { mutableStateOf(false) }

    // Dialog state for Unlinking
    var expenseToUnlink by remember { mutableStateOf<Expense?>(null) }

    val allAccounts = remember { reconciliationManager.getAllKnownAccounts() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = account?.institutionName ?: "Account Transactions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = account?.let {
                                "${AccountReconciliationManager.formatAccountType(it.instrumentType, it.accountSuffix)} (${transactions.size} transactions)"
                            } ?: "${transactions.size} transactions",
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleColor
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF9F9FB))
                .padding(innerPadding)
                .padding(16.dp)
        ) {
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
                        LinkedTransactionCard(
                            expense = expense,
                            onChangeAccount = { expenseToReassign = expense },
                            onUnlink = { expenseToUnlink = expense }
                        )
                    }
                }
            }
        }
    }

    // Reassign Picker Dialog
    expenseToReassign?.let { expense ->
        val candidateAccounts = allAccounts.filter { it.id != accountId }

        if (!showReassignConfirmDialog) {
            AlertDialog(
                onDismissRequest = {
                    expenseToReassign = null
                    newAccountSelected = null
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
                                    val isSelected = newAccountSelected?.id == candidate.id
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { newAccountSelected = candidate },
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
                                                onClick = { newAccountSelected = candidate },
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
                            if (newAccountSelected != null) {
                                showReassignConfirmDialog = true
                            }
                        },
                        enabled = newAccountSelected != null,
                        colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                    ) {
                        Text("Continue")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        expenseToReassign = null
                        newAccountSelected = null
                    }) {
                        Text("Cancel", color = textColor)
                    }
                }
            )
        } else {
            // Reassignment Confirmation Dialog
            val targetNewAccount = newAccountSelected
            if (targetNewAccount != null) {
                AlertDialog(
                    onDismissRequest = { showReassignConfirmDialog = false },
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
                                    showReassignConfirmDialog = false
                                    expenseToReassign = null
                                    newAccountSelected = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                        ) {
                            Text("Reassign Account")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showReassignConfirmDialog = false }) {
                            Text("Back", color = textColor)
                        }
                    }
                )
            }
        }
    }

    // Unlink Confirmation Dialog
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
    onChangeAccount: () -> Unit,
    onUnlink: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
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
