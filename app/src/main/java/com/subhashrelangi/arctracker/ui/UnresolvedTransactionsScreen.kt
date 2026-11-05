package com.subhashrelangi.arctracker.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.KnownFinancialAccountRepository
import com.subhashrelangi.arctracker.service.AccountReconciliationManager
import com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
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

enum class UnresolvedFilter {
    ALL,
    WITH_SUFFIX,
    NO_SUFFIX
}

@Composable
fun UnresolvedTransactionsScreen(
    onNavigateBack: () -> Unit,
    manager: AccountReconciliationManager? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val db = remember { AppDatabase.getDatabase(context) }
    val reconciliationManager = remember {
        manager ?: run {
            val repo = KnownFinancialAccountRepository(db.knownFinancialAccountDao())
            AccountReconciliationManager(db.expenseDao(), repo, db)
        }
    }

    val unresolvedExpenses by reconciliationManager.getUnresolvedTransactionsFlow().collectAsState(initial = emptyList())
    val allKnownAccounts by reconciliationManager.getAllKnownAccountsFlow().collectAsState(initial = emptyList())

    var currentFilter by remember { mutableStateOf(UnresolvedFilter.ALL) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedExpenseIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Dialog state for Single Assignment
    var singleTransactionToAssign by remember { mutableStateOf<Expense?>(null) }

    // Dialog state for Bulk Assignment
    var showBulkAssignDialog by remember { mutableStateOf(false) }

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
                    title = if (isSelectionMode) "${selectedExpenseIds.size} Selected" else "Unresolved Transactions",
                    subtitle = if (isSelectionMode) "Select transactions to bulk assign" else "Pending Account Reconciliation",
                    onBackClick = {
                        if (isSelectionMode) {
                            isSelectionMode = false
                            selectedExpenseIds = emptySet()
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
                        if (unresolvedExpenses.isNotEmpty()) {
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
                                        if (!isSelectionMode) selectedExpenseIds = emptySet()
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
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { selectedExpenseIds = emptySet() },
                            border = BorderStroke(1.dp, cardBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Text(
                                "Clear",
                                color = slateSub,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            )
                        }

                        Button(
                            onClick = { showBulkAssignDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = emeraldPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(2f)
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Bulk Assign (${selectedExpenseIds.size})",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = if (isSelectionMode && selectedExpenseIds.isNotEmpty()) 16.dp else 24.dp
                )
            ) {
                // 1. Triage Tag & Filter Controls
                item(key = "unresolved_header_controls") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Sub-header Row: Vault Triage Tag & Active Status Pill
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = emeraldPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "LOCAL-FIRST VAULT TRIAGE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569),
                                    letterSpacing = 0.6.sp
                                )
                            }

                            // Active Status Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(emeraldLight)
                                    .border(BorderStroke(1.dp, emeraldBorder), RoundedCornerShape(999.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(emeraldPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Active",
                                        color = emeraldPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Filter Pills (Segmented style)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ArcUnresolvedFilterPill(
                                text = "All (${unresolvedExpenses.size})",
                                isSelected = currentFilter == UnresolvedFilter.ALL,
                                onClick = { currentFilter = UnresolvedFilter.ALL }
                            )
                            ArcUnresolvedFilterPill(
                                text = "With Suffix ($withSuffixCount)",
                                isSelected = currentFilter == UnresolvedFilter.WITH_SUFFIX,
                                onClick = { currentFilter = UnresolvedFilter.WITH_SUFFIX }
                            )
                            ArcUnresolvedFilterPill(
                                text = "No Suffix ($noSuffixCount)",
                                isSelected = currentFilter == UnresolvedFilter.NO_SUFFIX,
                                onClick = { currentFilter = UnresolvedFilter.NO_SUFFIX }
                            )
                        }

                        // Multi-Selection Controller Bar (When in Selection Mode)
                        if (isSelectionMode) {
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            selectedExpenseIds = if (selectedExpenseIds.size == filteredExpenses.size) {
                                                emptySet()
                                            } else {
                                                filteredExpenses.map { it.id }.toSet()
                                            }
                                        }
                                    ) {
                                        Checkbox(
                                            checked = selectedExpenseIds.size == filteredExpenses.size && filteredExpenses.isNotEmpty(),
                                            onCheckedChange = { checked ->
                                                selectedExpenseIds = if (checked) {
                                                    filteredExpenses.map { it.id }.toSet()
                                                } else {
                                                    emptySet()
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = emeraldPrimary,
                                                uncheckedColor = Color(0xFF94A3B8)
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${selectedExpenseIds.size} of ${filteredExpenses.size} selected",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = slateTitle
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            selectedExpenseIds = if (selectedExpenseIds.size == filteredExpenses.size) {
                                                emptySet()
                                            } else {
                                                filteredExpenses.map { it.id }.toSet()
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = if (selectedExpenseIds.size == filteredExpenses.size && filteredExpenses.isNotEmpty()) "Deselect All" else "Select All",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = emeraldPrimary
                                        )
                                    }
                                }
                            }
                        } else {
                            // Vault Overview Info Card (Normal Mode)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(emeraldLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Sensors,
                                            contentDescription = null,
                                            tint = emeraldPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${filteredExpenses.size} unlinked transactions",
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = slateTitle
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFFF1F5F9))
                                                    .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Pending Match",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = slateSub
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "Detected from SMS & system notifications waiting for local vault verification.",
                                            fontSize = 11.5.sp,
                                            color = slateSub,
                                            lineHeight = 15.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Empty State
                if (filteredExpenses.isEmpty()) {
                    item(key = "empty_state") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(emeraldLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = emeraldPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (unresolvedExpenses.isEmpty()) "All Transactions Reconciled" else "No Transactions in this Filter",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = slateTitle
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (unresolvedExpenses.isEmpty()) {
                                        "Every transaction in your vault is linked to a known financial account."
                                    } else {
                                        "Try switching filter chips above to see other unlinked records."
                                    },
                                    fontSize = 12.5.sp,
                                    color = slateSub,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                } else {
                    // 3. Transactions List
                    items(filteredExpenses, key = { it.id }) { expense ->
                        val isSelected = expense.id in selectedExpenseIds
                        UnresolvedTransactionCard(
                            expense = expense,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onToggleSelection = {
                                selectedExpenseIds = if (isSelected) {
                                    selectedExpenseIds - expense.id
                                } else {
                                    selectedExpenseIds + expense.id
                                }
                            },
                            onAssignClick = {
                                singleTransactionToAssign = expense
                            },
                            onIgnoreClick = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        db.expenseDao().delete(expense)
                                        withContext(Dispatchers.Main) {
                                            snackbarHostState.showSnackbar("Transaction ignored and removed.")
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 4. Single Item Account Assignment Dialog
    singleTransactionToAssign?.let { expense ->
        val candidateAccounts = remember(expense, allKnownAccounts) {
            val s = expense.accountSuffix?.trim()
            if (!s.isNullOrBlank()) {
                val matched = allKnownAccounts.filter { it.accountSuffix.equals(s, ignoreCase = true) }
                if (matched.isNotEmpty()) matched else allKnownAccounts
            } else allKnownAccounts
        }

        ArcAccountAssignmentDialog(
            title = "Assign Account",
            subtitle = "For ₹${formatUnresolvedAmount(expense.amount)} (${if (expense.merchant.isNotBlank()) expense.merchant else "Transaction"})",
            detectedSuffix = expense.accountSuffix,
            availableAccounts = candidateAccounts,
            onDismiss = { singleTransactionToAssign = null },
            onConfirm = { account ->
                scope.launch {
                    val result = reconciliationManager.assignAccount(expense.id, account.id)
                    if (result.isSuccess) {
                        snackbarHostState.showSnackbar("Linked to ${account.institutionName ?: "account"}")
                    } else {
                        snackbarHostState.showSnackbar("Failed: ${result.exceptionOrNull()?.message}")
                    }
                    singleTransactionToAssign = null
                }
            }
        )
    }

    // 5. Bulk Assignment Dialog
    if (showBulkAssignDialog) {
        ArcAccountAssignmentDialog(
            title = "Bulk Assign Accounts",
            subtitle = "Assign ${selectedExpenseIds.size} transactions to account",
            detectedSuffix = null,
            availableAccounts = allKnownAccounts,
            onDismiss = { showBulkAssignDialog = false },
            onConfirm = { account ->
                val ids = selectedExpenseIds.toList()
                scope.launch {
                    val result = reconciliationManager.bulkAssign(ids, account.id)
                    if (result.isSuccess) {
                        snackbarHostState.showSnackbar("Linked ${result.getOrDefault(0)} transactions to ${account.institutionName ?: "account"}")
                        selectedExpenseIds = emptySet()
                        isSelectionMode = false
                    } else {
                        snackbarHostState.showSnackbar("Bulk assign failed: ${result.exceptionOrNull()?.message}")
                    }
                    showBulkAssignDialog = false
                }
            }
        )
    }
}

// ====================================================
// Sub-Components
// ====================================================

@Composable
private fun ArcUnresolvedFilterPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (isSelected) emeraldPrimary else Color(0xFFF1F5F9))
            .border(
                BorderStroke(1.dp, if (isSelected) emeraldPrimary else cardBorder),
                RoundedCornerShape(999.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else slateSub
        )
    }
}

@Composable
fun UnresolvedTransactionCard(
    expense: Expense,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onAssignClick: () -> Unit,
    onIgnoreClick: () -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val isCredit = expense.type.equals("CREDIT", ignoreCase = true)
    val cardIcon = getUnresolvedCardIcon(expense.merchant)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0xFFF0FDF4) else Color.White,
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) emeraldPrimary else cardBorder
        ),
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isSelectionMode) onToggleSelection()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Checkbox (if selection mode) + Icon + Merchant & Date + Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelection() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = emeraldPrimary,
                            uncheckedColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCredit) emeraldLight else Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = cardIcon,
                        contentDescription = expense.merchant,
                        tint = if (isCredit) emeraldPrimary else slateTitle,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (expense.merchant.isNotBlank()) expense.merchant else "Transaction",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = slateTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateFormat.format(Date(expense.dateMillis)),
                        fontSize = 12.sp,
                        color = slateSub
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (isCredit) "+" else "-"}₹${formatUnresolvedAmount(expense.amount)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCredit) emeraldPrimary else slateTitle
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = if (isCredit) "Credit" else "Debit",
                        fontSize = 11.sp,
                        color = slateSub
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata Badges (Suffix / No Suffix + SMS/Push Parse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!expense.accountSuffix.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF1F5F9))
                            .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CreditCard,
                                contentDescription = null,
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "••${expense.accountSuffix}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF334155),
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFEF3C7))
                            .border(BorderStroke(1.dp, Color(0xFFFDE68A)), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "No Suffix Detected",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                maxLines = 1
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (expense.source.contains("SMS", ignoreCase = true)) Icons.AutoMirrored.Outlined.Message else Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = slateSub,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (expense.source.contains("SMS", ignoreCase = true)) "SMS Parse" else "Push Parse",
                            fontSize = 11.sp,
                            color = slateSub,
                            maxLines = 1
                        )
                    }
                }
            }

            // Raw Text Snippet Box
            if (!expense.rawText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val rawClean = expense.rawText?.trim().orEmpty()
                    Text(
                        text = "\"${rawClean.take(110)}${if (rawClean.length > 110) "..." else ""}\"",
                        fontSize = 11.5.sp,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF475569),
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            // Action Buttons: Ignore & Assign Account (only in normal mode)
            if (!isSelectionMode) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onIgnoreClick,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, cardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFF8FAFC),
                            contentColor = Color(0xFF475569)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VisibilityOff,
                            contentDescription = null,
                            tint = slateSub,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ignore",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )
                    }

                    Button(
                        onClick = onAssignClick,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = emeraldLight,
                            contentColor = emeraldPrimary
                        ),
                        border = BorderStroke(1.dp, emeraldBorder),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                            tint = emeraldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Assign Account",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = emeraldPrimary
                        )
                    }
                }
            }
        }
    }
}

// Account Selection Dialog for Single / Bulk Assignment
@Composable
private fun ArcAccountAssignmentDialog(
    title: String,
    subtitle: String,
    detectedSuffix: String?,
    availableAccounts: List<KnownFinancialAccount>,
    onDismiss: () -> Unit,
    onConfirm: (KnownFinancialAccount) -> Unit
) {
    var selectedAccount by remember {
        val matching = if (!detectedSuffix.isNullOrBlank()) {
            availableAccounts.firstOrNull { it.accountSuffix.equals(detectedSuffix, ignoreCase = true) }
        } else null
        mutableStateOf(matching ?: availableAccounts.firstOrNull())
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            border = BorderStroke(1.dp, cardBorder),
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = slateTitle
                        )
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = slateSub
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = slateSub,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Accounts List
                if (availableAccounts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No registered accounts found in local registry.",
                            fontSize = 12.5.sp,
                            color = slateSub
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableAccounts, key = { it.id }) { account ->
                            val isSelected = selectedAccount?.id == account.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFFF0FDF4) else Color(0xFFF8FAFC),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) emeraldPrimary else cardBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedAccount = account }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) emeraldLight else Color(0xFFE2E8F0)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.AccountBalance,
                                                contentDescription = null,
                                                tint = if (isSelected) emeraldPrimary else Color(0xFF475569),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column {
                                            Text(
                                                text = account.institutionName ?: "Unknown Bank",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                color = if (isSelected) emeraldPrimary else slateTitle
                                            )
                                            Text(
                                                text = "Account ending in ••••${account.accountSuffix}",
                                                fontSize = 11.5.sp,
                                                color = slateSub
                                            )
                                        }
                                    }

                                    // Selection Ring
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) emeraldPrimary else Color.White)
                                            .border(
                                                width = if (isSelected) 0.dp else 1.5.dp,
                                                color = if (isSelected) emeraldPrimary else Color(0xFFCBD5E1),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions: Cancel & Confirm Link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Text("Cancel", color = slateSub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            selectedAccount?.let { onConfirm(it) }
                        },
                        enabled = selectedAccount != null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.3f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = emeraldPrimary,
                            disabledContainerColor = Color(0xFFCBD5E1)
                        )
                    ) {
                        Text("Confirm Link", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// Helpers
private fun formatUnresolvedAmount(amount: Double): String {
    return NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(amount)
}

private fun getUnresolvedCardIcon(merchant: String): ImageVector {
    val m = merchant.lowercase(Locale.ROOT)
    return when {
        m.contains("swiggy") || m.contains("zomato") || m.contains("food") || m.contains("dining") -> Icons.Outlined.Restaurant
        m.contains("amazon") || m.contains("flipkart") || m.contains("shop") || m.contains("store") -> Icons.Outlined.ShoppingBag
        m.contains("kirana") || m.contains("mart") || m.contains("grocery") -> Icons.Outlined.Storefront
        m.contains("uber") || m.contains("ola") || m.contains("ride") || m.contains("transit") -> Icons.Outlined.DirectionsCar
        m.contains("card") -> Icons.Outlined.CreditCard
        else -> Icons.AutoMirrored.Outlined.ReceiptLong
    }
}
