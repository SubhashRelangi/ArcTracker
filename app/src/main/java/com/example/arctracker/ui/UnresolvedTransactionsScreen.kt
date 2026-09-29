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
            AccountReconciliationManager(db.expenseDao(), repo)
        }
    }

    val unresolvedExpenses by reconciliationManager.getUnresolvedTransactionsFlow().collectAsState(initial = emptyList())

    var currentFilter by remember { mutableStateOf(UnresolvedFilter.ALL) }

    // Dialog states for manual assignment
    var transactionToAssign by remember { mutableStateOf<Expense?>(null) }
    var selectedAccountToLink by remember { mutableStateOf<KnownFinancialAccount?>(null) }
    var showConfirmationDialog by remember { mutableStateOf(false) }

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
                            text = "Unresolved Transactions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${unresolvedExpenses.size} transaction(s) pending account link",
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
                        UnresolvedTransactionCard(
                            expense = expense,
                            onAssignClick = {
                                transactionToAssign = expense
                            }
                        )
                    }
                }
            }
        }
    }

    // Account Selection Dialog
    transactionToAssign?.let { expense ->
        val candidateAccounts = remember(expense) {
            reconciliationManager.getCompatibleAccounts(expense)
        }

        var candidateSelected by remember { mutableStateOf<KnownFinancialAccount?>(null) }

        if (!showConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { transactionToAssign = null },
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
                                selectedAccountToLink = candidateSelected
                                showConfirmationDialog = true
                            }
                        },
                        enabled = candidateSelected != null,
                        colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                    ) {
                        Text("Continue")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { transactionToAssign = null }) {
                        Text("Cancel", color = textColor)
                    }
                }
            )
        } else {
            // Confirmation Dialog
            val selectedAccount = selectedAccountToLink
            if (selectedAccount != null) {
                AlertDialog(
                    onDismissRequest = {
                        showConfirmationDialog = false
                    },
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
                                    showConfirmationDialog = false
                                    transactionToAssign = null
                                    selectedAccountToLink = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                        ) {
                            Text("Link Account")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showConfirmationDialog = false
                            }
                        ) {
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
    onAssignClick: () -> Unit
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
