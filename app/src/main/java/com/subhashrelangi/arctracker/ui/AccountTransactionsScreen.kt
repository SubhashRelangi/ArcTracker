package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.KnownFinancialAccountRepository
import com.subhashrelangi.arctracker.service.AccountReconciliationManager
import com.subhashrelangi.arctracker.service.InstrumentType
import com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val emeraldPrimary = Color(0xFF047857)
private val emeraldLight = Color(0xFFECFDF5)
private val emeraldBorder = Color(0xFFA7F3D0)
private val slateTitle = Color(0xFF0F172A)
private val slateSub = Color(0xFF64748B)
private val cardBorder = Color(0xFFE2E8F0)
private val lightBg = Color(0xFFF8FAFC)

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

    val allAccounts by reconciliationManager.getAllKnownAccountsFlow().collectAsState(initial = emptyList())
    val account = remember(allAccounts, accountId) { allAccounts.firstOrNull { it.id == accountId } }
    val transactions by reconciliationManager.getLinkedTransactionsFlow(accountId).collectAsState(initial = emptyList())

    // Multi-Selection State
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

    val otherAccounts = remember(allAccounts, accountId) { allAccounts.filter { it.id != accountId } }

    Scaffold(
        containerColor = lightBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                ArcTrackerHeader(
                    title = if (isSelectionMode) "${selectedExpenseIds.size} Selected" else (account?.institutionName ?: "Account Ledger"),
                    subtitle = if (isSelectionMode) "Select transactions to reassign or unlink" else {
                        account?.let {
                            "${AccountReconciliationManager.formatAccountType(it.instrumentType, it.accountSuffix)} • ${transactions.size} records"
                        } ?: "${transactions.size} transactions"
                    },
                    onBackClick = {
                        if (isSelectionMode) {
                            isSelectionMode = false
                            selectedExpenseIds.clear()
                        } else {
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .statusBarsPadding(),
                    horizontalPadding = 16.dp,
                    actions = {
                        if (transactions.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelectionMode) emeraldPrimary else Color.White)
                                    .border(
                                        BorderStroke(1.dp, if (isSelectionMode) emeraldPrimary else cardBorder),
                                        CircleShape
                                    )
                                    .clickable {
                                        isSelectionMode = !isSelectionMode
                                        if (!isSelectionMode) {
                                            selectedExpenseIds.clear()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelectionMode) Icons.Filled.Check else Icons.Outlined.Checklist,
                                    contentDescription = if (isSelectionMode) "Done" else "Select Multiple",
                                    tint = if (isSelectionMode) Color.White else slateTitle,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (isSelectionMode && selectedExpenseIds.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showBulkUnlinkConfirmDialog = true },
                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFFEF2F2)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Unlink (${selectedExpenseIds.size})",
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }

                        Button(
                            onClick = { showBulkReassignPicker = true },
                            colors = ButtonDefaults.buttonColors(containerColor = emeraldPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SwapHoriz,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Reassign",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Account Summary Hero Card (shown in normal mode)
            if (!isSelectionMode && account != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(emeraldLight)
                                .border(1.dp, emeraldBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (account.instrumentType == InstrumentType.CARD) {
                                    Icons.Outlined.CreditCard
                                } else {
                                    Icons.Outlined.AccountBalance
                                },
                                contentDescription = null,
                                tint = emeraldPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = account.institutionName ?: "Linked Account",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = slateTitle
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = AccountReconciliationManager.formatAccountType(account.instrumentType, account.accountSuffix),
                                fontSize = 12.sp,
                                color = slateSub
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = emeraldLight,
                            border = BorderStroke(1.dp, emeraldBorder)
                        ) {
                            Text(
                                text = "${transactions.size} Txns",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = emeraldPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Multi-Selection Controls Pill Bar
            if (isSelectionMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                selectedExpenseIds.clear()
                                selectedExpenseIds.addAll(transactions.map { it.id })
                            }
                        ) {
                            Text(
                                "Select All (${transactions.size})",
                                color = emeraldPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }

                        TextButton(
                            onClick = { selectedExpenseIds.clear() },
                            enabled = selectedExpenseIds.isNotEmpty()
                        ) {
                            Text(
                                "Clear (${selectedExpenseIds.size})",
                                color = if (selectedExpenseIds.isNotEmpty()) Color(0xFFDC2626) else slateSub,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                }
            }

            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(emeraldLight)
                                .border(1.dp, emeraldBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                                contentDescription = "No Transactions",
                                tint = emeraldPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No transactions linked to this account",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = slateTitle
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Transactions linked manually or ingested from notifications will appear here.",
                            fontSize = 12.5.sp,
                            color = slateSub,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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

    // --- Bulk Reassign Dialogs ---
    if (showBulkReassignPicker && !showBulkReassignConfirmDialog) {
        var tempBulkTarget by remember { mutableStateOf<KnownFinancialAccount?>(null) }
        AlertDialog(
            onDismissRequest = { showBulkReassignPicker = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            title = {
                Text(
                    text = "Reassign ${selectedExpenseIds.size} Transactions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = slateTitle
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Choose target account for ${selectedExpenseIds.size} selected transactions:",
                        fontSize = 13.sp,
                        color = slateSub
                    )

                    if (otherAccounts.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = "No other registered financial accounts found.",
                                fontSize = 12.sp,
                                color = slateSub,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(otherAccounts, key = { it.id }) { candidate ->
                                val isSelected = tempBulkTarget?.id == candidate.id
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { tempBulkTarget = candidate },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) emeraldLight else Color(0xFFF8FAFC),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) emeraldPrimary else cardBorder
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { tempBulkTarget = candidate },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = emeraldPrimary,
                                                unselectedColor = slateSub
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = candidate.institutionName ?: "Unknown Bank",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                color = slateTitle
                                            )
                                            Text(
                                                text = AccountReconciliationManager.formatAccountType(
                                                    candidate.instrumentType,
                                                    candidate.accountSuffix
                                                ),
                                                fontSize = 11.5.sp,
                                                color = slateSub
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
                    colors = ButtonDefaults.buttonColors(containerColor = emeraldPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Continue", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkReassignPicker = false }) {
                    Text("Cancel", color = slateSub, fontSize = 13.sp)
                }
            }
        )
    } else if (showBulkReassignConfirmDialog) {
        val targetNew = bulkNewAccountSelected
        if (targetNew != null) {
            AlertDialog(
                onDismissRequest = { showBulkReassignConfirmDialog = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = Color.White,
                title = {
                    Text(
                        text = "Confirm Bulk Reassignment",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = slateTitle
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailRow(label = "Selected Transactions", value = "${selectedExpenseIds.size}")
                        DetailRow(
                            label = "Current Account",
                            value = "${account?.institutionName ?: "Account"} (••••${account?.accountSuffix})"
                        )
                        DetailRow(
                            label = "New Target Account",
                            value = "${targetNew.institutionName ?: "Account"} (••••${targetNew.accountSuffix})",
                            isHighlight = true
                        )
                        HorizontalDivider(color = cardBorder, thickness = 1.dp)
                        Text(
                            text = "This will atomically update the account link for all ${selectedExpenseIds.size} transactions. Other ledger data is preserved.",
                            fontSize = 12.sp,
                            color = slateSub
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
                        colors = ButtonDefaults.buttonColors(containerColor = emeraldPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Confirm Reassignment", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBulkReassignConfirmDialog = false }) {
                        Text("Back", color = slateSub, fontSize = 13.sp)
                    }
                }
            )
        }
    }

    // --- Bulk Unlink Dialog ---
    if (showBulkUnlinkConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showBulkUnlinkConfirmDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            title = {
                Text(
                    text = "Unlink ${selectedExpenseIds.size} Transactions?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = slateTitle
                )
            },
            text = {
                Text(
                    text = "These ${selectedExpenseIds.size} transactions will return to the Unresolved Ledger list.\n\nTheir account suffix hints and transaction amounts will remain intact.",
                    fontSize = 13.5.sp,
                    color = slateSub,
                    lineHeight = 19.sp
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Unlink Records", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkUnlinkConfirmDialog = false }) {
                    Text("Cancel", color = slateSub, fontSize = 13.sp)
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
                shape = RoundedCornerShape(20.dp),
                containerColor = Color.White,
                title = {
                    Text(
                        text = "Change Account Link",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = slateTitle
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Currently linked: ${account?.institutionName ?: "Unknown"} (••••${account?.accountSuffix})",
                            fontSize = 12.sp,
                            color = slateSub
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        if (candidateAccounts.isEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF1F5F9)
                            ) {
                                Text(
                                    text = "No other registered accounts found.",
                                    fontSize = 12.sp,
                                    color = slateSub,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
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
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) emeraldLight else Color(0xFFF8FAFC),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) emeraldPrimary else cardBorder
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { singleNewAccountSelected = candidate },
                                                colors = RadioButtonDefaults.colors(
                                                    selectedColor = emeraldPrimary,
                                                    unselectedColor = slateSub
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = candidate.institutionName ?: "Unknown Bank",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.5.sp,
                                                    color = slateTitle
                                                )
                                                Text(
                                                    text = AccountReconciliationManager.formatAccountType(
                                                        candidate.instrumentType,
                                                        candidate.accountSuffix
                                                    ),
                                                    fontSize = 11.5.sp,
                                                    color = slateSub
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
                        colors = ButtonDefaults.buttonColors(containerColor = emeraldPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Continue", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        expenseToReassign = null
                        singleNewAccountSelected = null
                    }) {
                        Text("Cancel", color = slateSub, fontSize = 13.sp)
                    }
                }
            )
        } else {
            val targetNewAccount = singleNewAccountSelected
            if (targetNewAccount != null) {
                AlertDialog(
                    onDismissRequest = { showSingleReassignConfirmDialog = false },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = Color.White,
                    title = {
                        Text(
                            text = "Confirm Account Change",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = slateTitle
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
                            colors = ButtonDefaults.buttonColors(containerColor = emeraldPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Reassign Account", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSingleReassignConfirmDialog = false }) {
                            Text("Back", color = slateSub, fontSize = 13.sp)
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
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            title = {
                Text(
                    text = "Unlink Account?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = slateTitle
                )
            },
            text = {
                Text(
                    text = "Unlink this transaction (₹${expense.amount} - ${expense.merchant}) from ${account?.institutionName ?: "this account"} ••••${account?.accountSuffix}?\n\nThe transaction will return to the Unresolved list with its original suffix preserved.",
                    fontSize = 13.5.sp,
                    color = slateSub,
                    lineHeight = 19.sp
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Unlink Account", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToUnlink = null }) {
                    Text("Cancel", color = slateSub, fontSize = 13.sp)
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
    val isCredit = expense.type.equals("CREDIT", ignoreCase = true)
    val isSalary = isCredit && (expense.merchant.contains("salary", ignoreCase = true) ||
            expense.tag?.contains("salary", ignoreCase = true) == true ||
            expense.note?.contains("salary", ignoreCase = true) == true)

    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    val timeStr = remember(expense.dateMillis) { timeFormatter.format(Date(expense.dateMillis)) }
    val dateStr = remember(expense.dateMillis) { dateFormatter.format(Date(expense.dateMillis)) }

    // Resolve Category display exactly matching ArcTracker Ledger
    val categoryName = remember(expense.tag, expense.category, expense.merchant) {
        val cat = expense.tag ?: expense.category
        when {
            !cat.isNullOrBlank() -> cat
            expense.merchant.contains("Swiggy", ignoreCase = true) || expense.merchant.contains("Zomato", ignoreCase = true) -> "Food & Dining"
            expense.merchant.contains("Coffee", ignoreCase = true) || expense.merchant.contains("Cafe", ignoreCase = true) -> "Dining"
            expense.merchant.contains("Uber", ignoreCase = true) || expense.merchant.contains("Ola", ignoreCase = true) -> "Transit"
            expense.merchant.contains("Amazon", ignoreCase = true) || expense.merchant.contains("Flipkart", ignoreCase = true) -> "Shopping"
            expense.merchant.contains("Basket", ignoreCase = true) || expense.merchant.contains("Blinkit", ignoreCase = true) -> "Groceries"
            expense.merchant.contains("Cult", ignoreCase = true) || expense.merchant.contains("Fit", ignoreCase = true) -> "Health"
            expense.merchant.contains("BESCOM", ignoreCase = true) || expense.merchant.contains("Electricity", ignoreCase = true) -> "Utilities"
            isSalary -> "Salary"
            isCredit -> "Income"
            else -> "General"
        }
    }

    // Resolve Icon matching ArcTracker styling
    val icon = remember(isSalary, isCredit, expense.merchant, categoryName) {
        when {
            isSalary || isCredit -> Icons.Rounded.ArrowDownward
            expense.merchant.contains("Coffee", ignoreCase = true) || expense.merchant.contains("Cafe", ignoreCase = true) -> Icons.Outlined.LocalCafe
            expense.merchant.contains("Basket", ignoreCase = true) || expense.merchant.contains("Groceries", ignoreCase = true) -> Icons.Outlined.ShoppingCart
            expense.merchant.contains("Swiggy", ignoreCase = true) || expense.merchant.contains("Zomato", ignoreCase = true) -> Icons.Outlined.Fastfood
            expense.merchant.contains("Uber", ignoreCase = true) || expense.merchant.contains("Ola", ignoreCase = true) -> Icons.Outlined.DirectionsCar
            expense.merchant.contains("Amazon", ignoreCase = true) || expense.merchant.contains("Flipkart", ignoreCase = true) -> Icons.Outlined.Inventory2
            expense.merchant.contains("Cult", ignoreCase = true) || expense.merchant.contains("Fit", ignoreCase = true) -> Icons.Outlined.FitnessCenter
            expense.merchant.contains("BESCOM", ignoreCase = true) || expense.merchant.contains("Electricity", ignoreCase = true) -> Icons.Outlined.Bolt
            else -> Icons.AutoMirrored.Outlined.ReceiptLong
        }
    }

    val iconBgColor = if (isCredit) Color(0xFFECFDF5) else Color(0xFFF1F5F9)
    val iconTint = if (isCredit) Color(0xFF047857) else Color(0xFF0F172A)

    val formattedAmount = remember(expense.amount) {
        if (expense.amount % 1.0 == 0.0) {
            String.format(Locale.US, "%,d", expense.amount.toLong())
        } else {
            String.format(Locale.US, "%,.2f", expense.amount)
        }
    }
    val sign = if (isCredit) "+" else "-"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isSelectionMode) { onToggleSelection() },
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) emeraldLight else Color.White,
        border = BorderStroke(1.dp, if (isSelected) emeraldPrimary else cardBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Main Transaction Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelection() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = emeraldPrimary,
                            uncheckedColor = slateSub
                        ),
                        modifier = Modifier.padding(end = 10.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(iconBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = categoryName,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = expense.merchant.ifBlank { if (isCredit) "Income Entry" else "Expense Entry" },
                        color = slateTitle,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$categoryName • $dateStr • $timeStr",
                        color = slateSub,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$sign₹$formattedAmount",
                        color = if (isCredit) emeraldPrimary else slateTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isCredit) emeraldLight else Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = if (isCredit) "Deposit" else "Linked",
                            color = if (isCredit) emeraldPrimary else slateSub,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                }
            }

            // Sleek Action Footer Bar (Normal Mode)
            if (!isSelectionMode) {
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reassign Action Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(emeraldLight)
                            .border(BorderStroke(1.dp, emeraldBorder), RoundedCornerShape(8.dp))
                            .clickable { onChangeAccount() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SwapHoriz,
                                contentDescription = null,
                                tint = emeraldPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Reassign",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = emeraldPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Unlink Action Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFEF2F2))
                            .border(BorderStroke(1.dp, Color(0xFFFECACA)), RoundedCornerShape(8.dp))
                            .clickable { onUnlink() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Unlink",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }
                }
            }
        }
    }
}
