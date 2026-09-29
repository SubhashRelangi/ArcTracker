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
fun FinancialAccountsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToUnresolved: () -> Unit,
    onViewAccountTransactions: (String) -> Unit = {},
    manager: AccountReconciliationManager? = null
) {
    val context = LocalContext.current
    val reconciliationManager = remember {
        manager ?: run {
            val db = AppDatabase.getDatabase(context)
            val repo = KnownFinancialAccountRepository(db.knownFinancialAccountDao())
            AccountReconciliationManager(db.expenseDao(), repo)
        }
    }

    val accounts by reconciliationManager.getAllKnownAccountsFlow().collectAsState(initial = emptyList())
    val unresolvedExpenses by reconciliationManager.getUnresolvedTransactionsFlow().collectAsState(initial = emptyList())

    var selectedAccountForDetail by remember { mutableStateOf<KnownFinancialAccount?>(null) }

    Scaffold(
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
            }
        )
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
    onViewTransactions: (String) -> Unit
) {
    var linkedCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

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
            }
        },
        confirmButton = {
            Button(
                onClick = { onViewTransactions(account.id) },
                colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
            ) {
                Text("View Transactions")
            }
        },
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
