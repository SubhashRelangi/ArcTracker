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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun FinancialAccountsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToUnresolved: () -> Unit,
    onViewAccountTransactions: (String) -> Unit = {},
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

    val accounts by reconciliationManager.getAllKnownAccountsFlow().collectAsState(initial = emptyList())
    val unresolvedExpenses by reconciliationManager.getUnresolvedTransactionsFlow().collectAsState(initial = emptyList())

    var selectedAccountForDetail by remember { mutableStateOf<KnownFinancialAccount?>(null) }
    var accountToDelete by remember { mutableStateOf<KnownFinancialAccount?>(null) }
    var accountToMerge by remember { mutableStateOf<KnownFinancialAccount?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Financial Accounts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Discovered bank accounts & cards",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
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
            // Unresolved Transactions Quick Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToUnresolved() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, borderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(if (unresolvedExpenses.isNotEmpty()) Color(0xFFFFF3E0) else lightPurpleColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (unresolvedExpenses.isNotEmpty()) Icons.Filled.PendingActions else Icons.Filled.CheckCircle,
                            contentDescription = "Unresolved",
                            tint = if (unresolvedExpenses.isNotEmpty()) Color(0xFFE65100) else purpleColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Unresolved Transactions",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = textColor
                        )
                        Text(
                            text = if (unresolvedExpenses.isEmpty()) {
                                "All transactions linked"
                            } else {
                                "${unresolvedExpenses.size} pending account assignment"
                            },
                            fontSize = 12.sp,
                            color = subtitleColor
                        )
                    }

                    if (unresolvedExpenses.isNotEmpty()) {
                        Surface(
                            color = Color(0xFFFF9800),
                            shape = CircleShape,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "${unresolvedExpenses.size}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Go",
                        tint = textColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Known Accounts Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Discovered Accounts (${accounts.size})",
                    color = purpleColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (accounts.isEmpty()) {
                // Empty state
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
                                imageVector = Icons.Filled.AccountBalance,
                                contentDescription = "Accounts",
                                tint = purpleColor,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No financial accounts discovered yet.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Accounts are automatically discovered when importing SMS or processing financial notifications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleColor,
                            lineHeight = 18.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(accounts, key = { it.id }) { account ->
                        KnownAccountCard(
                            account = account,
                            onClick = { selectedAccountForDetail = account }
                        )
                    }
                }
            }
        }
    }

    // Account Detail Dialog
    selectedAccountForDetail?.let { account ->
        AccountDetailDialog(
            account = account,
            manager = reconciliationManager,
            onDismiss = { selectedAccountForDetail = null },
            onViewTransactions = { accountId ->
                selectedAccountForDetail = null
                onViewAccountTransactions(accountId)
            },
            onMergeClick = {
                accountToMerge = account
                selectedAccountForDetail = null
            },
            onDeleteClick = {
                accountToDelete = account
                selectedAccountForDetail = null
            }
        )
    }

    // Delete Account Confirmation Dialog (Parts 3, 4, 28)
    accountToDelete?.let { account ->
        var linkedTxnCount by remember { mutableIntStateOf(0) }
        LaunchedEffect(account.id) {
            linkedTxnCount = reconciliationManager.getLinkedTransactionCount(account.id)
        }

        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = {
                Text(
                    text = "Delete ${account.institutionName ?: "Account"} ••••${account.accountSuffix}?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (linkedTxnCount > 0) {
                        Text(
                            text = "$linkedTxnCount transaction(s) are currently linked to this account.",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = textColor
                        )
                        Text(
                            text = "Deleting the account will unlink these transactions and return them to the Unresolved list. Their transaction data and account suffix will be preserved.",
                            fontSize = 12.sp,
                            color = subtitleColor
                        )
                    } else {
                        Text(
                            text = "No transactions are currently linked to this account.",
                            fontSize = 13.sp,
                            color = textColor
                        )
                        Text(
                            text = "Are you sure you want to delete this account from the registry?",
                            fontSize = 12.sp,
                            color = subtitleColor
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetAccount = account
                        scope.launch {
                            val result = reconciliationManager.deleteAccount(targetAccount.id)
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("Deleted ${targetAccount.institutionName ?: "account"} (unlinked ${result.getOrDefault(0)} transactions)")
                            } else {
                                snackbarHostState.showSnackbar("Failed to delete account: ${result.exceptionOrNull()?.message}")
                            }
                            accountToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Delete Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Cancel", color = textColor)
                }
            }
        )
    }

    // Merge Account Dialog (Parts 5, 6, 7, 8, 29)
    accountToMerge?.let { sourceAccount ->
        var targetSelected by remember { mutableStateOf<KnownFinancialAccount?>(null) }
        var showMergePreview by remember { mutableStateOf(false) }
        var linkedCount by remember { mutableIntStateOf(0) }

        val candidates = remember(sourceAccount) {
            reconciliationManager.getMergeCandidates(sourceAccount)
        }

        LaunchedEffect(sourceAccount.id) {
            linkedCount = reconciliationManager.getLinkedTransactionCount(sourceAccount.id)
        }

        if (!showMergePreview) {
            AlertDialog(
                onDismissRequest = { accountToMerge = null },
                title = {
                    Text(
                        text = "Merge Account",
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
                            text = "Move all transactions from ${sourceAccount.institutionName ?: "Source"} ••••${sourceAccount.accountSuffix} into a target account.",
                            fontSize = 13.sp,
                            color = subtitleColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select Target Account:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = textColor
                        )

                        if (candidates.isEmpty()) {
                            Text(
                                text = "No compatible target accounts found (target must share suffix ••••${sourceAccount.accountSuffix} and instrument type).",
                                fontSize = 12.sp,
                                color = Color(0xFFC62828)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(candidates, key = { it.id }) { candidate ->
                                    val isSelected = targetSelected?.id == candidate.id
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { targetSelected = candidate },
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
                                                onClick = { targetSelected = candidate },
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
                            if (targetSelected != null) {
                                showMergePreview = true
                            }
                        },
                        enabled = targetSelected != null,
                        colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                    ) {
                        Text("Continue")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { accountToMerge = null }) {
                        Text("Cancel", color = textColor)
                    }
                }
            )
        } else {
            // Merge Preview & Confirmation Dialog (Part 8)
            val target = targetSelected
            if (target != null) {
                AlertDialog(
                    onDismissRequest = { showMergePreview = false },
                    title = {
                        Text(
                            text = "Confirm Account Merge",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DetailRow(
                                label = "From",
                                value = "${sourceAccount.institutionName ?: "Account"} (••••${sourceAccount.accountSuffix})"
                            )
                            DetailRow(
                                label = "Into",
                                value = "${target.institutionName ?: "Account"} (••••${target.accountSuffix})",
                                isHighlight = true
                            )
                            DetailRow(
                                label = "Transactions affected",
                                value = "$linkedCount"
                            )
                            HorizontalDivider(color = borderColor, thickness = 1.dp)
                            Text(
                                text = "After merging, these $linkedCount transactions will be linked to the target account. The source account will be removed.",
                                fontSize = 12.sp,
                                color = subtitleColor
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val sId = sourceAccount.id
                                val tId = target.id
                                scope.launch {
                                    val result = reconciliationManager.mergeAccounts(sId, tId)
                                    if (result.isSuccess) {
                                        snackbarHostState.showSnackbar("Successfully merged accounts (${result.getOrDefault(0)} transactions moved)")
                                    } else {
                                        snackbarHostState.showSnackbar("Merge failed: ${result.exceptionOrNull()?.message}")
                                    }
                                    accountToMerge = null
                                    targetSelected = null
                                    showMergePreview = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                        ) {
                            Text("Merge Accounts")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showMergePreview = false }) {
                            Text("Back", color = textColor)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun KnownAccountCard(
    account: KnownFinancialAccount,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(lightPurpleColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AccountBalance,
                    contentDescription = "Bank",
                    tint = purpleColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.institutionName ?: "Unknown Institution",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = AccountReconciliationManager.formatAccountType(account.instrumentType, account.accountSuffix),
                    fontSize = 12.sp,
                    color = subtitleColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFFF0F0F0),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = AccountReconciliationManager.formatAccountSource(account.source),
                            fontSize = 10.sp,
                            color = Color(0xFF555555),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = if (account.confidence.name == "HIGH") Color(0xFFE8F5E9) else Color(0xFFFFF9C4),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = account.confidence.name,
                            fontSize = 10.sp,
                            color = if (account.confidence.name == "HIGH") Color(0xFF2E7D32) else Color(0xFFF57F17),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Details",
                tint = textColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun AccountDetailDialog(
    account: KnownFinancialAccount,
    manager: AccountReconciliationManager,
    onDismiss: () -> Unit,
    onViewTransactions: (String) -> Unit,
    onMergeClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var linkedCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(account.id) {
        linkedCount = manager.getLinkedTransactionCount(account.id)
    }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(lightPurpleColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountBalance,
                        contentDescription = "Account",
                        tint = purpleColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = account.institutionName ?: "Unknown Institution",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = AccountReconciliationManager.formatMaskedSuffix(account.accountSuffix),
                        fontSize = 12.sp,
                        color = subtitleColor
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow(label = "Type", value = AccountReconciliationManager.formatAccountType(account.instrumentType, account.accountSuffix))
                DetailRow(label = "Masked Suffix", value = AccountReconciliationManager.formatMaskedSuffix(account.accountSuffix))
                DetailRow(label = "Confidence", value = account.confidence.name)
                DetailRow(label = "Source", value = AccountReconciliationManager.formatAccountSource(account.source))
                if (account.createdAt > 0) {
                    DetailRow(label = "Discovered", value = dateFormat.format(Date(account.createdAt)))
                }
                if (account.updatedAt > 0) {
                    DetailRow(label = "Last Updated", value = dateFormat.format(Date(account.updatedAt)))
                }
                DetailRow(
                    label = "Linked Transactions",
                    value = "$linkedCount transaction(s)",
                    isHighlight = true
                )

                HorizontalDivider(color = borderColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                // Actions: View Transactions, Merge, Delete
                Button(
                    onClick = { onViewTransactions(account.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                ) {
                    Icon(imageVector = Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Transactions")
                }

                OutlinedButton(
                    onClick = onMergeClick,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, purpleColor)
                ) {
                    Icon(imageVector = Icons.Filled.Merge, contentDescription = null, tint = purpleColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Merge Account", color = purpleColor)
                }

                OutlinedButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Color(0xFFC62828))
                ) {
                    Icon(imageVector = Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Account", color = Color(0xFFC62828))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = textColor)
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = subtitleColor
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium,
            color = if (isHighlight) purpleColor else textColor
        )
    }
}
