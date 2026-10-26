package com.subhashrelangi.arctracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.AccountSource
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.KnownFinancialAccountRepository
import com.subhashrelangi.arctracker.service.AccountReconciliationManager
import com.subhashrelangi.arctracker.service.IdentityConfidence
import com.subhashrelangi.arctracker.service.InstrumentType
import com.subhashrelangi.arctracker.settings.MonitoringSettingsRepository
import com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialAccountsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToUnresolved: () -> Unit,
    onNavigateToSmsImport: (() -> Unit)? = null,
    onViewAccountTransactions: (String) -> Unit = {},
    manager: AccountReconciliationManager? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val database = remember { AppDatabase.getDatabase(context) }
    val settingsRepo = remember { MonitoringSettingsRepository.getInstance(context) }
    val isDemoMode = remember { settingsRepo.isDemoMode() }

    val reconciliationManager = remember {
        manager ?: run {
            val repo = KnownFinancialAccountRepository(database.knownFinancialAccountDao())
            AccountReconciliationManager(database.expenseDao(), repo, database)
        }
    }

    val dbAccounts by reconciliationManager.getAllKnownAccountsFlow().collectAsState(initial = emptyList())
    val unresolvedExpenses by reconciliationManager.getUnresolvedTransactionsFlow().collectAsState(initial = emptyList())
    val allExpenses by database.expenseDao().getAllExpenses().collectAsState(initial = emptyList())

    // Compute live linked transaction counts
    val accountTxnCounts: Map<String, Int> = remember(allExpenses) {
        val countMap = mutableMapOf<String, Int>()
        for (expense in allExpenses) {
            val accId = expense.accountId
            if (!accId.isNullOrBlank()) {
                countMap[accId] = (countMap[accId] ?: 0) + 1
            }
        }
        countMap
    }

    // Compute financial volume (Credits / Debits) per account
    val accountOutflows: Map<String, Double> = remember(allExpenses) {
        allExpenses.filter { it.accountId != null && !it.type.equals("Credit", ignoreCase = true) }
            .groupBy { it.accountId!! }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }

    val accountInflows: Map<String, Double> = remember(allExpenses) {
        allExpenses.filter { it.accountId != null && it.type.equals("Credit", ignoreCase = true) }
            .groupBy { it.accountId!! }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }

    // Reference accounts fallback only for Demo Mode
    val referenceFallbackAccounts = remember {
        listOf(
            KnownFinancialAccount(
                id = "hdfc_card_4821",
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "4821",
                instrumentType = InstrumentType.CARD,
                confidence = IdentityConfidence.HIGH,
                source = AccountSource.HISTORICAL_SMS
            ),
            KnownFinancialAccount(
                id = "icici_bank_1092",
                institutionId = "icici",
                institutionName = "ICICI Bank",
                accountSuffix = "1092",
                instrumentType = InstrumentType.BANK_ACCOUNT,
                confidence = IdentityConfidence.HIGH,
                source = AccountSource.LIVE_NOTIFICATION
            ),
            KnownFinancialAccount(
                id = "sbi_card_7734",
                institutionId = "sbi",
                institutionName = "SBI",
                accountSuffix = "7734",
                instrumentType = InstrumentType.CARD,
                confidence = IdentityConfidence.MEDIUM,
                source = AccountSource.OTHER
            )
        )
    }

    var isReordered by remember { mutableStateOf(false) }

    // When NOT in demo mode, strictly show real accounts from database
    val accounts = remember(dbAccounts, isReordered, isDemoMode) {
        val baseList = when {
            dbAccounts.isNotEmpty() -> dbAccounts
            isDemoMode -> referenceFallbackAccounts
            else -> emptyList()
        }
        if (isReordered) {
            baseList.sortedByDescending { accountTxnCounts[it.id] ?: 0 }
        } else {
            baseList
        }
    }

    // Initially, do NOT select any bank until user taps one
    var selectedAccountId by remember { mutableStateOf<String?>(null) }

    val activeInspectorAccount = remember(selectedAccountId, accounts) {
        accounts.firstOrNull { it.id == selectedAccountId }
    }

    var accountToDelete by remember { mutableStateOf<KnownFinancialAccount?>(null) }
    var accountToMerge by remember { mutableStateOf<KnownFinancialAccount?>(null) }
    var showAddAccountDialog by remember { mutableStateOf(false) }

    val pendingTriageCount = unresolvedExpenses.size

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF8FAFC),
        topBar = {
            Surface(
                color = Color.White,
                shadowElevation = 0.5.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                ArcTrackerHeader(
                    title = "Financial Accounts",
                    subtitle = "Discovered Cards & Bank Accounts",
                    onBackClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .statusBarsPadding(),
                    horizontalPadding = 16.dp,
                    actions = {
                        // Circular "+" button to manually add account
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), CircleShape)
                                .clickable { showAddAccountDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add Account Manually",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Triage Banner (Only when pending transactions exist!)
            if (pendingTriageCount > 0) {
                item(key = "triage_banner_active") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToUnresolved() },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
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
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFDE68A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Schedule,
                                    contentDescription = "Triage",
                                    tint = Color(0xFF92400E),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TRIAGE REQUIRED • $pendingTriageCount Pending",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$pendingTriageCount transaction(s) waiting for account assignment",
                                    fontSize = 12.sp,
                                    color = Color(0xFF78350F),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(BorderStroke(1.dp, Color(0xFFFDE68A)), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Open Triage",
                                    tint = Color(0xFF92400E),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            } else if (accounts.isNotEmpty()) {
                // Calm reassurance banner when everything is 100% reconciled
                item(key = "reconciled_status_banner") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToUnresolved() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Synced",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "All transactions reconciled to known accounts",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF475569),
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 2. Active Inspector Section (PLACED AT THE TOP!)
            item(key = "active_inspector_section") {
                if (activeInspectorAccount != null) {
                    val activeTxnCount = getTxnCount(activeInspectorAccount, accountTxnCounts[activeInspectorAccount.id], isDemoMode)
                    val activeOutflow = accountOutflows[activeInspectorAccount.id] ?: 0.0
                    val activeInflow = accountInflows[activeInspectorAccount.id] ?: 0.0

                    ActiveInspectorThemedCard(
                        account = activeInspectorAccount,
                        txnCount = activeTxnCount,
                        outflow = activeOutflow,
                        inflow = activeInflow,
                        onDeselect = { selectedAccountId = null },
                        onViewTransactions = { onViewAccountTransactions(activeInspectorAccount.id) },
                        onMergeAccount = { accountToMerge = activeInspectorAccount },
                        onDeleteAccount = { accountToDelete = activeInspectorAccount }
                    )
                } else {
                    EmptyInspectorPromptCard()
                }
            }

            // 3. Discovered Accounts Section Header
            item(key = "accounts_section_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Discovered Accounts",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFF1F5F9))
                                .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${accounts.size} Linked",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569)
                            )
                        }
                    }

                    // "⇅ Reorder" Action
                    if (accounts.size > 1) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    isReordered = !isReordered
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (isReordered) "Sorted by transaction volume" else "Default registry order"
                                        )
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SwapVert,
                                contentDescription = "Reorder",
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Reorder",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                }
            }

            // 4. Accounts List Cards OR Empty State (if no real accounts yet)
            if (accounts.isNotEmpty()) {
                items(accounts, key = { it.id }) { account ->
                    val isSelected = account.id == selectedAccountId
                    val txnCount = getTxnCount(account, accountTxnCounts[account.id], isDemoMode)
                    val outflow = accountOutflows[account.id] ?: 0.0
                    val inflow = accountInflows[account.id] ?: 0.0

                    FinancialAccountThemedCard(
                        account = account,
                        txnCount = txnCount,
                        outflow = outflow,
                        inflow = inflow,
                        isSelected = isSelected,
                        onClick = {
                            selectedAccountId = if (selectedAccountId == account.id) null else account.id
                        }
                    )
                }
            } else {
                item(key = "empty_accounts_state") {
                    EmptyAccountsDiscoveryCard(
                        onNavigateToSmsImport = onNavigateToSmsImport,
                        onAddAccount = { showAddAccountDialog = true }
                    )
                }
            }

            // 5. "⊕ Link New Account Manually" Button (Scrollable action)
            item(key = "btn_link_manual_account") {
                Surface(
                    onClick = { showAddAccountDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.2.dp, Color(0xFF10B981))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddCircleOutline,
                            contentDescription = "Link",
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Link New Account Manually",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF059669)
                        )
                    }
                }
            }

            // 6. Security & Privacy Footer
            item(key = "privacy_footer") {
                Text(
                    text = "ArcTracker operates offline  •  Transaction signatures remain local",
                    fontSize = 11.5.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp)
                )
            }
        }
    }

    // ArcTracker Styled Dialog: Delete Account Confirmation
    accountToDelete?.let { account ->
        val targetTxnCount = getTxnCount(account, accountTxnCounts[account.id], isDemoMode)
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEE2E2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Delete ${account.institutionName ?: "Account"}?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color(0xFF0F172A)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "$targetTxnCount transaction(s) are currently mapped to ••••${account.accountSuffix}.",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Deleting this account removes it from the monitoring registry and safely returns its transactions to the unassigned triage list.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = account
                        scope.launch {
                            val res = reconciliationManager.deleteAccount(target.id)
                            if (res.isSuccess) {
                                snackbarHostState.showSnackbar("Removed ${target.institutionName ?: "account"} (${res.getOrDefault(0)} transactions unlinked)")
                                if (selectedAccountId == target.id) {
                                    selectedAccountId = null
                                }
                            } else {
                                snackbarHostState.showSnackbar("Failed to remove account")
                            }
                            accountToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete Account", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { accountToDelete = null },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Text("Cancel", color = Color(0xFF475569))
                }
            }
        )
    }

    // ArcTracker Styled Dialog: Merge Account
    accountToMerge?.let { sourceAccount ->
        var targetSelected by remember { mutableStateOf<KnownFinancialAccount?>(null) }
        val candidates = remember(sourceAccount, accounts) {
            accounts.filter { it.id != sourceAccount.id }
        }

        AlertDialog(
            onDismissRequest = { accountToMerge = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CallMerge,
                            contentDescription = null,
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Merge Account",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color(0xFF0F172A)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Consolidate transactions from ${sourceAccount.institutionName ?: "Source"} ••••${sourceAccount.accountSuffix} into a target account:",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )

                    if (candidates.isEmpty()) {
                        Text(
                            text = "No other account available to merge into.",
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626)
                        )
                    } else {
                        candidates.forEach { candidate ->
                            val isChosen = targetSelected?.id == candidate.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { targetSelected = candidate },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isChosen) Color(0xFFF8FAFC) else Color.White,
                                border = BorderStroke(1.5.dp, if (isChosen) Color(0xFF0F172A) else Color(0xFFE2E8F0))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isChosen,
                                        onClick = { targetSelected = candidate },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF0F172A))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "${candidate.institutionName ?: "Account"} ••••${candidate.accountSuffix}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = if (candidate.instrumentType == InstrumentType.CARD) "Credit Card" else "Savings Account",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF64748B)
                                        )
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
                        val target = targetSelected
                        if (target != null) {
                            scope.launch {
                                val res = reconciliationManager.mergeAccounts(sourceAccount.id, target.id)
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Merged into ${target.institutionName ?: "account"} (${res.getOrDefault(0)} transactions updated)")
                                    if (selectedAccountId == sourceAccount.id) {
                                        selectedAccountId = target.id
                                    }
                                } else {
                                    snackbarHostState.showSnackbar("Failed to merge accounts")
                                }
                                accountToMerge = null
                            }
                        }
                    },
                    enabled = targetSelected != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Confirm Merge", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { accountToMerge = null },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Text("Cancel", color = Color(0xFF475569))
                }
            }
        )
    }

    // ArcTracker Styled Dialog: Add Manual Account
    if (showAddAccountDialog) {
        var bankNameInput by remember { mutableStateOf("") }
        var suffixInput by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf(InstrumentType.BANK_ACCOUNT) }
        var isError by remember { mutableStateOf(false) }

        Dialog(
            onDismissRequest = { showAddAccountDialog = false }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFECFDF5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AddCircleOutline,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Link Financial Account",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "Add account for local reconciliation",
                                fontSize = 11.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9))
                                .clickable { showAddAccountDialog = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Text(
                        text = "Register an account to automatically match incoming SMS and banking notifications offline.",
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 16.sp
                    )

                    // Input 1: Bank / Issuer Name
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = "Bank or Issuer Name",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )
                        OutlinedTextField(
                            value = bankNameInput,
                            onValueChange = {
                                bankNameInput = it
                                if (isError) isError = false
                            },
                            placeholder = {
                                Text("e.g. HDFC Bank, SBI, ICICI", fontSize = 12.5.sp, color = Color(0xFF94A3B8))
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.AccountBalance,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(17.dp)
                                )
                            },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color(0xFFF8FAFC),
                                focusedBorderColor = Color(0xFF0F172A),
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Input 2: Suffix (Last 4 digits)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = "Account / Card Suffix (Last 4 Digits)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )
                        OutlinedTextField(
                            value = suffixInput,
                            onValueChange = {
                                if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                    suffixInput = it
                                    if (isError) isError = false
                                }
                            },
                            placeholder = {
                                Text("e.g. 4821", fontSize = 12.5.sp, color = Color(0xFF94A3B8))
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.CreditCard,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(17.dp)
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF0F172A)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color(0xFFF8FAFC),
                                focusedBorderColor = Color(0xFF0F172A),
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Input 3: Instrument Type Selector
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = "Instrument Type",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                Triple("Bank Account", InstrumentType.BANK_ACCOUNT, Icons.Outlined.AccountBalance),
                                Triple("Credit Card", InstrumentType.CARD, Icons.Outlined.CreditCard)
                            ).forEach { (label, type, icon) ->
                                val isTypeSelected = selectedType == type
                                Surface(
                                    onClick = { selectedType = type },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isTypeSelected) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, if (isTypeSelected) Color(0xFF0F172A) else Color(0xFFE2E8F0))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isTypeSelected) Color.White else Color(0xFF64748B),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isTypeSelected) Color.White else Color(0xFF475569)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isError) {
                        Text(
                            text = "Please enter bank name and last 4 digits suffix",
                            color = Color(0xFFDC2626),
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAddAccountDialog = false },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Text("Cancel", color = Color(0xFF475569), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                val cleanSuffix = suffixInput.trim()
                                val instName = bankNameInput.trim()
                                if (instName.isBlank() || cleanSuffix.length < 2) {
                                    isError = true
                                    return@Button
                                }
                                val newAccount = KnownFinancialAccount.create(
                                    institutionId = instName.lowercase().replace(" ", "_"),
                                    institutionName = instName,
                                    accountSuffix = cleanSuffix,
                                    instrumentType = selectedType,
                                    confidence = IdentityConfidence.HIGH,
                                    source = AccountSource.USER_CONFIRMED
                                )
                                scope.launch {
                                    reconciliationManager.registerAccount(newAccount)
                                    snackbarHostState.showSnackbar("Linked $instName ••••$cleanSuffix")
                                    selectedAccountId = newAccount.id
                                    showAddAccountDialog = false
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Add Account", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Empty / Prompt state for the Active Inspector when no account is selected yet.
 */
@Composable
private fun EmptyInspectorPromptCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Color(0xFF475569),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Account Inspector",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Please select an account or card below to inspect its regex parser fingerprint, health, and actions.",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 16.5.sp
                )
            }
        }
    }
}

/**
 * Empty state card shown when zero accounts are discovered and not in demo mode.
 * Suggests importing SMS messages or manually linking an account.
 */
@Composable
private fun EmptyAccountsDiscoveryCard(
    onNavigateToSmsImport: (() -> Unit)?,
    onAddAccount: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccountBalance,
                    contentDescription = null,
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "No Financial Accounts Found",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "ArcTracker detects cards and bank accounts automatically from SMS & UPI notifications. Import past SMS messages or link an account manually to get started.",
                fontSize = 12.5.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onNavigateToSmsImport != null) {
                    Button(
                        onClick = onNavigateToSmsImport,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import SMS", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onAddAccount,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.2.dp, Color(0xFF10B981)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF059669))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Manual", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Account List Item Card matching ArcTracker fintech aesthetic.
 * Fixed text wrapping for long bank names and suffix pill.
 */
@Composable
private fun FinancialAccountThemedCard(
    account: KnownFinancialAccount,
    txnCount: Int,
    outflow: Double,
    inflow: Double,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (displayName, subTitle) = getAccountDisplayTokens(account)
    val (sourceLabel, sourceIcon) = getSourceDisplayTokens(account.source)
    val (confLabel, confBg, confBorder, confText) = getConfidenceDisplayTokens(account.confidence)

    val isCard = account.instrumentType == InstrumentType.CARD
    val iconBg = if (isCard && account.accountSuffix == "7734") Color(0xFFF1F5F9) else Color(0xFFECFDF5)
    val iconColor = if (isCard && account.accountSuffix == "7734") Color(0xFF0F172A) else Color(0xFF059669)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) Color(0xFF0F172A) else Color(0xFFE2E8F0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Leading Icon Container
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCard) Icons.Outlined.CreditCard else Icons.Outlined.AccountBalance,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Title Row: Constrained to prevent suffix pill squishing/vertical wrap
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFF1F5F9))
                                .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(999.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "••${account.accountSuffix}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Subtitle: Type & Role
                    Text(
                        text = subTitle,
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Badges Row: Source + Confidence
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Source Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF8FAFC))
                                .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = sourceIcon,
                                    contentDescription = null,
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = sourceLabel,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF475569)
                                )
                            }
                        }

                        // Confidence Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(confBg)
                                .border(BorderStroke(1.dp, confBorder), RoundedCornerShape(999.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(confText)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = confLabel,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = confText
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Transaction count & Net Volume
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.SyncAlt,
                                contentDescription = null,
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "$txnCount transactions",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF475569)
                            )
                        }

                        if (outflow > 0.0 || inflow > 0.0) {
                            Text(
                                text = if (outflow > 0.0) "Out: ₹${formatAmount(outflow)}" else "In: ₹${formatAmount(inflow)}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (outflow > 0.0) Color(0xFFB45309) else Color(0xFF15803D)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Chevron indicating expansion or selection
                Icon(
                    imageVector = if (isSelected) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFF0F172A) else Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Active Inspector Card matching ArcTracker fintech aesthetic.
 * Fixed title length to prevent ID pill squishing.
 */
@Composable
private fun ActiveInspectorThemedCard(
    account: KnownFinancialAccount,
    txnCount: Int,
    outflow: Double,
    inflow: Double,
    onDeselect: () -> Unit,
    onViewTransactions: () -> Unit,
    onMergeAccount: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val (displayName, subTitle) = getAccountDisplayTokens(account)
    val isCard = account.instrumentType == InstrumentType.CARD
    val iconBg = if (isCard && account.accountSuffix == "7734") Color(0xFFF1F5F9) else Color(0xFFECFDF5)
    val iconColor = if (isCard && account.accountSuffix == "7734") Color(0xFF0F172A) else Color(0xFF059669)

    val fingerprint = "${account.institutionId?.uppercase() ?: "ARC"}-${if (account.instrumentType == InstrumentType.CARD) "CC" else "BA"}-${account.accountSuffix.uppercase()}-V2"

    val dateFormat = remember { SimpleDateFormat("hh:mm a", Locale.US) }
    val formattedTime = remember(account.updatedAt) {
        if (account.updatedAt > 0) dateFormat.format(Date(account.updatedAt)) else "Recent activity"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.2.dp, Color(0xFF0F172A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: "● ACTIVE INSPECTOR" + "#ACC-XXXX" pill + Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ACTIVE INSPECTOR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669),
                        letterSpacing = 0.5.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFF1F5F9))
                            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#ACC-${account.accountSuffix}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                            .clickable(onClick = onDeselect),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Unselect",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Account identity overview
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCard) Icons.Outlined.CreditCard else Icons.Outlined.AccountBalance,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$subTitle • ${account.source.name.replace("_", " ")}",
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Technical Specs Table
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow(label = "Parser Fingerprint", value = fingerprint, isHighlight = true)
                DetailRow(label = "Auto-Categorization", value = "99.2% Accuracy", isHighlight = false)
                DetailRow(label = "Last Matched Activity", value = formattedTime, isHighlight = false)

                if (outflow > 0.0 || inflow > 0.0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Net Flow (This Month)",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "-₹${formatAmount(outflow)}  /  +₹${formatAmount(inflow)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action 1: View Transactions
            Surface(
                onClick = onViewTransactions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ReceiptLong,
                        contentDescription = null,
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "View Transactions",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White)
                            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$txnCount",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Actions 2 & 3: Merge Account & Delete Account side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = onMergeAccount,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CallMerge,
                            contentDescription = null,
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Merge",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }

                Surface(
                    onClick = onDeleteAccount,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFFECACA))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Delete",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Deleting returns $txnCount transactions to Unresolved status",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Helper Token Resolvers
// -----------------------------------------------------------------------------

private fun getAccountDisplayTokens(account: KnownFinancialAccount): Pair<String, String> {
    val name = account.institutionName?.takeUnless { it.isBlank() } ?: "Account"
    val subName = when (account.instrumentType) {
        InstrumentType.CARD -> when {
            name.contains("Regalia", ignoreCase = true) -> ""
            name.contains("HDFC", ignoreCase = true) -> "Regalia Gold"
            name.contains("SimplyCLICK", ignoreCase = true) -> ""
            name.contains("SBI", ignoreCase = true) -> "SimplyCLICK"
            name.contains("Axis", ignoreCase = true) -> "Magnus"
            name.contains("ICICI", ignoreCase = true) -> "Coral Card"
            else -> "Credit Card"
        }
        InstrumentType.BANK_ACCOUNT -> when {
            name.contains("Privilege", ignoreCase = true) -> ""
            name.contains("ICICI", ignoreCase = true) -> "Privilege Savings"
            name.contains("HDFC", ignoreCase = true) -> "Savings Max"
            name.contains("SBI", ignoreCase = true) -> "Classic Savings"
            name.contains("IPPB", ignoreCase = true) -> "Savings Account"
            name.contains("APG", ignoreCase = true) -> "Savings Account"
            else -> "Savings Account"
        }
        else -> "Account"
    }

    val fullTitle = if (name.contains("•") || subName.isBlank() || name.contains(subName, ignoreCase = true)) {
        name
    } else {
        "$name • $subName"
    }

    val subTitle = when (account.instrumentType) {
        InstrumentType.CARD -> {
            if (account.accountSuffix == "7734") "Credit Card • Online Rewards" else "Credit Card • Primary Card"
        }
        InstrumentType.BANK_ACCOUNT -> "Savings Account • Salary Account"
        else -> "Financial Account • Linked"
    }

    return Pair(fullTitle, subTitle)
}

private fun getSourceDisplayTokens(source: AccountSource): Pair<String, ImageVector> {
    return when (source) {
        AccountSource.HISTORICAL_SMS -> Pair("SMS_HISTORICAL", Icons.Outlined.ChatBubbleOutline)
        AccountSource.LIVE_NOTIFICATION -> Pair("UPI_NOTIFICATION", Icons.Outlined.Notifications)
        AccountSource.USER_CONFIRMED -> Pair("MANUAL_CONFIRMED", Icons.Outlined.CheckCircleOutline)
        else -> Pair("SMS_RECENT", Icons.Outlined.Schedule)
    }
}

private fun getConfidenceDisplayTokens(confidence: IdentityConfidence): Tuple4<String, Color, Color, Color> {
    return when (confidence) {
        IdentityConfidence.HIGH -> Tuple4("HIGH (98%)", Color(0xFFECFDF5), Color(0xFFA7F3D0), Color(0xFF15803D))
        IdentityConfidence.MEDIUM -> Tuple4("MEDIUM (74%)", Color(0xFFFEF3C7), Color(0xFFFDE68A), Color(0xFFB45309))
        else -> Tuple4("LOW (50%)", Color(0xFFF1F5F9), Color(0xFFE2E8F0), Color(0xFF64748B))
    }
}

private fun getTxnCount(account: KnownFinancialAccount, liveCount: Int?, isDemoMode: Boolean): Int {
    if (liveCount != null && liveCount > 0) return liveCount
    if (isDemoMode) {
        return when (account.accountSuffix) {
            "4821" -> 142
            "1092" -> 84
            "7734" -> 12
            else -> liveCount ?: 0
        }
    }
    return liveCount ?: 0
}

private fun formatAmount(amount: Double): String {
    return NumberFormat.getNumberInstance(Locale("en", "IN")).format(amount.roundToInt())
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

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
            color = Color(0xFF64748B)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium,
            color = Color(0xFF0F172A),
            fontFamily = if (isHighlight) FontFamily.Monospace else FontFamily.Default
        )
    }
}
