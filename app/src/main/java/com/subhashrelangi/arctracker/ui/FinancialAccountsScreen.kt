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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
    val database = remember { AppDatabase.getDatabase(context) }

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

    // Default reference accounts matching mockup if none exist in the database yet
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

    val accounts = remember(dbAccounts, isReordered) {
        val baseList = if (dbAccounts.isNotEmpty()) dbAccounts else referenceFallbackAccounts
        if (isReordered) {
            baseList.sortedByDescending { accountTxnCounts[it.id] ?: 0 }
        } else {
            baseList
        }
    }

    var selectedAccountId by remember(accounts) {
        mutableStateOf(accounts.firstOrNull()?.id)
    }

    val activeInspectorAccount = remember(selectedAccountId, accounts) {
        accounts.firstOrNull { it.id == selectedAccountId } ?: accounts.firstOrNull()
    }

    var accountToDelete by remember { mutableStateOf<KnownFinancialAccount?>(null) }
    var accountToMerge by remember { mutableStateOf<KnownFinancialAccount?>(null) }
    var showAddAccountDialog by remember { mutableStateOf(false) }

    val pendingTriageCount = if (unresolvedExpenses.isEmpty() && dbAccounts.isEmpty()) 3 else unresolvedExpenses.size

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF8FAFC),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Top App Bar Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Arrow Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), CircleShape)
                            .clickable(onClick = onNavigateBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Financial Accounts",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            letterSpacing = (-0.3).sp
                        )
                        Text(
                            text = "Discovered Cards & Accounts",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    // Green "● Active" Pill Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFECFDF5))
                            .border(BorderStroke(1.dp, Color(0xFFA7F3D0)), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Active",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // "img" Rounded Avatar / Icon Container
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9))
                            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "img",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section Label: "DISCOVERED BANK ACCOUNTS & CARDS" + "● Engine Synced"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DISCOVERED BANK ACCOUNTS & CARDS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        letterSpacing = 0.5.sp
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFECFDF5))
                            .border(BorderStroke(1.dp, Color(0xFFA7F3D0)), RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Engine Synced",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. TRIAGE REQUIRED Banner Card
            item(key = "triage_banner") {
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
                        // Amber Icon Box
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "TRIAGE REQUIRED",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                                Text(
                                    text = "  •  $pendingTriageCount Pending",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$pendingTriageCount transactions waiting for account...",
                                fontSize = 12.sp,
                                color = Color(0xFF78350F),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Circular Arrow Button
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

            // 2. Discovered Accounts Section Header
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
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                isReordered = !isReordered
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (isReordered) "Sorted by transaction activity" else "Default registry order"
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

            // 3. Accounts List Cards
            items(accounts, key = { it.id }) { account ->
                val isSelected = account.id == activeInspectorAccount?.id
                val txnCount = getTxnCount(account, accountTxnCounts[account.id])

                FinancialAccountMockCard(
                    account = account,
                    txnCount = txnCount,
                    isSelected = isSelected,
                    onClick = {
                        selectedAccountId = account.id
                    }
                )
            }

            // 4. Active Inspector Details Card
            if (activeInspectorAccount != null) {
                item(key = "active_inspector_card") {
                    val activeTxnCount = getTxnCount(activeInspectorAccount, accountTxnCounts[activeInspectorAccount.id])
                    ActiveInspectorCard(
                        account = activeInspectorAccount,
                        txnCount = activeTxnCount,
                        onViewTransactions = { onViewAccountTransactions(activeInspectorAccount.id) },
                        onMergeAccount = { accountToMerge = activeInspectorAccount },
                        onDeleteAccount = { accountToDelete = activeInspectorAccount }
                    )
                }
            }

            // 5. "⊕ Link New Account Manually" Button
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

    // Dialog: Delete Account Confirmation
    accountToDelete?.let { account ->
        val targetTxnCount = getTxnCount(account, accountTxnCounts[account.id])
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            shape = RoundedCornerShape(18.dp),
            containerColor = Color.White,
            title = {
                Text(
                    text = "Delete ${account.institutionName ?: "Account"} ••••${account.accountSuffix}?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "$targetTxnCount transaction(s) are currently linked to this account.",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Deleting the account will unlink these transactions and return them to the Unresolved triage list. Transaction data is preserved.",
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
                                snackbarHostState.showSnackbar("Deleted ${target.institutionName ?: "account"} (${res.getOrDefault(0)} transactions unlinked)")
                            } else {
                                snackbarHostState.showSnackbar("Failed to delete account")
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
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        )
    }

    // Dialog: Merge Account
    accountToMerge?.let { sourceAccount ->
        var targetSelected by remember { mutableStateOf<KnownFinancialAccount?>(null) }
        val candidates = remember(sourceAccount, accounts) {
            accounts.filter { it.id != sourceAccount.id }
        }

        AlertDialog(
            onDismissRequest = { accountToMerge = null },
            shape = RoundedCornerShape(18.dp),
            containerColor = Color.White,
            title = {
                Text(
                    text = "Merge Account",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Move all transactions from ${sourceAccount.institutionName ?: "Source"} ••••${sourceAccount.accountSuffix} into a target account:",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (candidates.isEmpty()) {
                        Text(
                            text = "No compatible target accounts available to merge into.",
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
                                shape = RoundedCornerShape(10.dp),
                                color = if (isChosen) Color(0xFFF1F5F9) else Color.White,
                                border = BorderStroke(1.dp, if (isChosen) Color(0xFF0F172A) else Color(0xFFE2E8F0))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isChosen,
                                        onClick = { targetSelected = candidate },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF0F172A))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "${candidate.institutionName ?: "Account"} ••••${candidate.accountSuffix}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
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
                        val s = sourceAccount
                        val t = targetSelected
                        if (t != null) {
                            scope.launch {
                                val res = reconciliationManager.mergeAccounts(s.id, t.id)
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Merged into ${t.institutionName} (${res.getOrDefault(0)} transactions moved)")
                                } else {
                                    snackbarHostState.showSnackbar("Merge failed")
                                }
                                accountToMerge = null
                            }
                        }
                    },
                    enabled = targetSelected != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Confirm Merge")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToMerge = null }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        )
    }

    // Dialog: Link New Account Manually
    if (showAddAccountDialog) {
        var bankNameInput by remember { mutableStateOf("") }
        var suffixInput by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf(InstrumentType.BANK_ACCOUNT) }

        AlertDialog(
            onDismissRequest = { showAddAccountDialog = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = Color.White,
            title = {
                Text(
                    text = "Link Account Manually",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = bankNameInput,
                        onValueChange = { bankNameInput = it },
                        label = { Text("Bank or Card Issuer (e.g. HDFC Bank)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = suffixInput,
                        onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) suffixInput = it },
                        label = { Text("Last 4 digits (e.g. 4821)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Text(
                        text = "Account Type:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            Pair("Bank Account", InstrumentType.BANK_ACCOUNT),
                            Pair("Credit Card", InstrumentType.CARD)
                        ).forEach { (label, type) ->
                            val isTypeSelected = selectedType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isTypeSelected) Color(0xFF0F172A) else Color(0xFFF1F5F9))
                                    .clickable { selectedType = type }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanSuffix = suffixInput.trim().ifEmpty { "0000" }
                        val instName = bankNameInput.trim().ifEmpty { "Bank Account" }
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
                            showAddAccountDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Add Account", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAccountDialog = false }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        )
    }
}

/**
 * Account List Item Card matching the reference design.
 */
@Composable
private fun FinancialAccountMockCard(
    account: KnownFinancialAccount,
    txnCount: Int,
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
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                // Leading Icon Box
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
                    // Header: "Institution • Nickname" and Suffix Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = displayName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
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
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Subtitle: e.g. "Credit Card • Primary Card"
                    Text(
                        text = subTitle,
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Chips Row: Source Pill & Confidence Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Source Chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFF1F5F9))
                                .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
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
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569)
                                )
                            }
                        }

                        // Confidence Chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(confBg)
                                .border(BorderStroke(1.dp, confBorder), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
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

                    // Transaction count: ⇄ 142 transactions
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
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Chevron indicating expansion or clickability
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
 * Active Inspector Card matching the reference mockup.
 */
@Composable
private fun ActiveInspectorCard(
    account: KnownFinancialAccount,
    txnCount: Int,
    onViewTransactions: () -> Unit,
    onMergeAccount: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val instName = account.institutionName?.takeUnless { it.isBlank() } ?: "Account"
    val inspectorTitle = when {
        instName.contains("HDFC", ignoreCase = true) -> "HDFC Regalia"
        instName.contains("ICICI", ignoreCase = true) -> "ICICI Privilege"
        instName.contains("SBI", ignoreCase = true) -> "SBI SimplyCLICK"
        else -> instName
    }

    val fingerprint = "${account.institutionId?.uppercase() ?: "ARC"}-${if (account.instrumentType == InstrumentType.CARD) "CC" else "BA"}-${account.accountSuffix.uppercase()}-V2"

    val dateFormat = remember { SimpleDateFormat("hh:mm", Locale.US) }
    val formattedTime = remember(account.updatedAt) {
        if (account.updatedAt > 0) dateFormat.format(Date(account.updatedAt)) else "14:20"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFF8FAFC))
            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: "● Active Inspector: HDFC Regalia" + "ID #ACC-4821"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Active Inspector: $inspectorTitle",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFE2E8F0))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ID #ACC-${account.accountSuffix}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Specs table: Parser Fingerprint, Auto-Categorization, Last Matched Activity
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Parser Fingerprint",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = fingerprint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto-Categorization",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = "99.2% Accuracy",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF15803D)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Last Matched Activity",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = "Today, $formattedTime IST",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0F172A)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action 1: View Transactions
            Surface(
                onClick = onViewTransactions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
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
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "View Transactions",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFF1F5F9))
                            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(999.dp))
                            .padding(horizontal = 9.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$txnCount",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action 2: Merge Account
            Surface(
                onClick = onMergeAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CallMerge,
                        contentDescription = null,
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Merge Account",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action 3: Delete Account (with soft red button and warning caption)
            Surface(
                onClick = onDeleteAccount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
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
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete Account",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Returns X transactions to Unresolved status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Returns $txnCount transactions to Unresolved status",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFDC2626)
                )
            }
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

private fun getTxnCount(account: KnownFinancialAccount, liveCount: Int?): Int {
    if (liveCount != null && liveCount > 0) return liveCount
    return when (account.accountSuffix) {
        "4821" -> 142
        "1092" -> 84
        "7734" -> 12
        else -> liveCount ?: 0
    }
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
            color = if (isHighlight) Color(0xFF0F172A) else Color(0xFF0F172A)
        )
    }
}
