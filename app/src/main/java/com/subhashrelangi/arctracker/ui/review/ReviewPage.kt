package com.subhashrelangi.arctracker.ui.review

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.subhashrelangi.arctracker.data.AccountSource
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.KnownFinancialAccountRepository
import com.subhashrelangi.arctracker.service.AccountReconciliationManager
import com.subhashrelangi.arctracker.service.CategoryManager
import com.subhashrelangi.arctracker.service.IdentityConfidence
import com.subhashrelangi.arctracker.service.InstrumentType
import com.subhashrelangi.arctracker.service.MerchantAliasManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Filter tabs for Review workspace
private enum class ReviewFilterTab {
    ALL,
    NEEDS_ACCOUNT,
    NEEDS_CATEGORY
}

// Data model for Unlinked Account triage items
private data class UnlinkedAccountItem(
    val id: Int,
    val merchant: String,
    val amount: Double,
    val isCredit: Boolean,
    val dateInfo: String,
    val accountSuffix: String?,
    val suggestedBank: String?,
    val source: String,
    val rawText: String?,
    val expense: Expense? = null
)

// Data model for Next in Line fallback/supplementary items
private data class PendingReviewItem(
    val id: Int,
    val merchant: String,
    val amount: Double,
    val accountInfo: String,
    val dateInfo: String,
    val icon: ImageVector,
    val badge: String? = null,
    val ambiguityLabel: String? = null,
    val patternTitle: String? = null,
    val patternDescription: String? = null,
    val suggestedCategories: List<String> = emptyList(),
    val hintText: String? = null,
    val hintIcon: ImageVector? = null,
    val quickActions: List<String> = emptyList(),
    val acceptLabel: String? = null,
    val defaultTag: String? = null
)

// Data model for Resolved items
private data class ResolvedReviewItem(
    val id: Int,
    val merchant: String,
    val amount: Double,
    val subtitle: String,
    val icon: ImageVector,
    val isVaulted: Boolean = true
)

/**
 * Dedicated Production Review Workspace matching reference mockup.
 */
@Composable
fun ReviewPage(
    expenses: List<Expense>,
    showHeader: Boolean = false,
    isDemoMode: Boolean = false,
    isSetupCompleted: Boolean = false,
    searchQuery: String = "",
    onSearchClick: () -> Unit = {},
    onAddExpenseClick: () -> Unit = {},
    onViewLedger: () -> Unit = {},
    onStartSetup: () -> Unit = {},
    onEditExpense: (Expense) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val database = remember { AppDatabase.getDatabase(context) }
    val expenseDao = remember { database.expenseDao() }
    val knownAccountDao = remember { database.knownFinancialAccountDao() }
    val categoryDao = remember { database.transactionCategoryDao() }
    val aliasDao = remember { database.merchantAliasDao() }

    val reconciliationManager = remember {
        val repo = KnownFinancialAccountRepository(knownAccountDao)
        AccountReconciliationManager(expenseDao, repo, database)
    }
    val aliasManager = remember { MerchantAliasManager(aliasDao) }
    val categoryManager = remember { CategoryManager(categoryDao, expenseDao, database) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                categoryManager.ensureBuiltInCategoriesSeeded()
            } catch (_: Exception) {}
        }
    }

    // Filter DB pending transactions
    val dbPendingExpenses = remember(expenses) {
        expenses.filter { it.isPending }
    }

    val dbUnresolvedExpenses by reconciliationManager.getUnresolvedTransactionsFlow().collectAsState(initial = emptyList())
    val dbKnownAccounts by knownAccountDao.getAllFlow().collectAsState(initial = emptyList())

    val fallbackDemoAccounts = remember {
        listOf(
            KnownFinancialAccount(
                id = "demo_hdfc_4082",
                accountSuffix = "4082",
                institutionName = "HDFC Bank",
                instrumentType = InstrumentType.BANK_ACCOUNT,
                source = AccountSource.USER_CONFIRMED,
                confidence = IdentityConfidence.HIGH
            ),
            KnownFinancialAccount(
                id = "demo_icici_1092",
                accountSuffix = "1092",
                institutionName = "ICICI Bank",
                instrumentType = InstrumentType.BANK_ACCOUNT,
                source = AccountSource.USER_CONFIRMED,
                confidence = IdentityConfidence.HIGH
            ),
            KnownFinancialAccount(
                id = "demo_axis_8840",
                accountSuffix = "8840",
                institutionName = "Axis Bank",
                instrumentType = InstrumentType.CARD,
                source = AccountSource.USER_CONFIRMED,
                confidence = IdentityConfidence.HIGH
            )
        )
    }

    val effectiveKnownAccounts = remember(dbKnownAccounts, isDemoMode) {
        if (dbKnownAccounts.isNotEmpty()) dbKnownAccounts else fallbackDemoAccounts
    }

    val demoUnlinkedState = remember {
        mutableStateListOf(
            UnlinkedAccountItem(
                id = 9101,
                merchant = "Swiggy Delivery",
                amount = 420.0,
                isCredit = false,
                dateInfo = "Yesterday, 20:45",
                accountSuffix = "4082",
                suggestedBank = "HDFC",
                source = "SMS Parse",
                rawText = "Paid Rs 420 via UPI to Swiggy on 19-Oct via HDFC Bank xx4082. Ref 928172"
            ),
            UnlinkedAccountItem(
                id = 9102,
                merchant = "Amazon India",
                amount = 3490.0,
                isCredit = false,
                dateInfo = "18 Oct, 14:10",
                accountSuffix = "1092",
                suggestedBank = "ICICI",
                source = "Push Notification",
                rawText = "Acct XX1092 debited for INR 3,490.00 on 18-Oct via NetBanking to Amazon India."
            ),
            UnlinkedAccountItem(
                id = 9103,
                merchant = "Local Kirana Store",
                amount = 180.0,
                isCredit = false,
                dateInfo = "17 Oct, 11:30",
                accountSuffix = null,
                suggestedBank = null,
                source = "SMS Parse",
                rawText = "UPI txn to Sharma General Stores Rs 180 on 17-Oct successful. Ref 382910."
            )
        )
    }

    val unlinkedAccountItems: List<UnlinkedAccountItem> = remember(isDemoMode, isSetupCompleted, dbUnresolvedExpenses, demoUnlinkedState.toList()) {
        when {
            isDemoMode -> demoUnlinkedState.toList()
            !isSetupCompleted -> emptyList()
            dbUnresolvedExpenses.isNotEmpty() -> {
                dbUnresolvedExpenses.map { exp ->
                    val isCr = exp.type.equals("CREDIT", ignoreCase = true)
                    val dateFmt = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(exp.dateMillis))
                    val inferredBank = when {
                        exp.rawText?.contains("HDFC", ignoreCase = true) == true -> "HDFC"
                        exp.rawText?.contains("ICICI", ignoreCase = true) == true -> "ICICI"
                        exp.rawText?.contains("SBI", ignoreCase = true) == true -> "SBI"
                        exp.rawText?.contains("Axis", ignoreCase = true) == true -> "Axis"
                        else -> null
                    }
                    val src = if (exp.rawText?.contains("SMS", ignoreCase = true) == true || exp.source.contains("SMS", ignoreCase = true)) "SMS Parse" else "Push Notification"

                    UnlinkedAccountItem(
                        id = exp.id,
                        merchant = exp.merchant.ifBlank { "Unidentified Transaction" },
                        amount = exp.amount,
                        isCredit = isCr,
                        dateInfo = dateFmt,
                        accountSuffix = exp.accountSuffix?.takeIf { it.isNotBlank() },
                        suggestedBank = inferredBank,
                        source = src,
                        rawText = exp.rawText,
                        expense = exp
                    )
                }
            }
            else -> emptyList()
        }
    }

    var currentTab by remember { mutableStateOf(ReviewFilterTab.ALL) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedAccountExpenseIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var singleItemToAssign by remember { mutableStateOf<UnlinkedAccountItem?>(null) }
    var showBulkAssignDialog by remember { mutableStateOf(false) }

    // Live search query filtering for unlinked items
    val filteredUnlinkedItems = remember(unlinkedAccountItems, searchQuery) {
        if (searchQuery.isBlank()) unlinkedAccountItems
        else {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            unlinkedAccountItems.filter {
                it.merchant.lowercase(Locale.ROOT).contains(q) ||
                it.amount.toString().contains(q) ||
                (it.accountSuffix?.contains(q) == true) ||
                (it.rawText?.lowercase(Locale.ROOT)?.contains(q) == true)
            }
        }
    }

    // Baseline mock items used for demo mode
    val baselinePending = remember {
        listOf(
            PendingReviewItem(
                id = 901,
                merchant = "Swiggy Delivery",
                amount = 420.0,
                accountInfo = "HDFC ••4082",
                dateInfo = "Yesterday • 20:45",
                icon = Icons.Outlined.Restaurant,
                badge = "UPI",
                ambiguityLabel = "High Ambiguity",
                patternTitle = "Local Pattern Detected",
                patternDescription = "Detected past 8 PM on a weekday. Is this personal evening food or tax-deductible team dinner?",
                suggestedCategories = listOf("Food & Dining", "Team Meals", "Personal Outflow", "Tax Deductible"),
                defaultTag = "#reimbursable"
            ),
            PendingReviewItem(
                id = 902,
                merchant = "Amazon India",
                amount = 3490.0,
                accountInfo = "ICICI ••1092",
                dateInfo = "Today, 14:10",
                icon = Icons.Outlined.ShoppingBag,
                ambiguityLabel = "Card spend",
                hintText = "Sub-category: Electronics vs ...",
                hintIcon = Icons.Outlined.HelpOutline,
                quickActions = listOf("Office", "Gadgets")
            ),
            PendingReviewItem(
                id = 903,
                merchant = "Uber Premier",
                amount = 1770.0,
                accountInfo = "ICICI ••1092",
                dateInfo = "02 Nov",
                icon = Icons.Outlined.DirectionsCar,
                ambiguityLabel = "Airport ride",
                hintText = "Flag as Tax Deductible Commute?",
                hintIcon = Icons.Outlined.LocalOffer,
                acceptLabel = "Accept"
            )
        )
    }

    // Determine initial queue items according to setup state
    // Case 1: Without setup and not demo mode -> empty
    // Case 2: Demo mode -> baseline mock items
    // Case 3: Setup completed -> real DB pending items (if none, empty)
    var queueItems by remember(dbPendingExpenses, isDemoMode, isSetupCompleted) {
        when {
            isDemoMode -> mutableStateOf(baselinePending)
            !isSetupCompleted -> mutableStateOf(emptyList<PendingReviewItem>())
            dbPendingExpenses.isNotEmpty() -> {
                mutableStateOf(
                    dbPendingExpenses.mapIndexed { idx, exp ->
                        val isDining = exp.merchant.contains("Swiggy", ignoreCase = true) ||
                            exp.merchant.contains("Zomato", ignoreCase = true) ||
                            exp.merchant.contains("Coffee", ignoreCase = true) ||
                            exp.tag?.contains("Food", ignoreCase = true) == true

                        val isShopping = exp.merchant.contains("Amazon", ignoreCase = true) ||
                            exp.merchant.contains("Flipkart", ignoreCase = true) ||
                            exp.tag?.contains("Shop", ignoreCase = true) == true

                        val isTransit = exp.merchant.contains("Uber", ignoreCase = true) ||
                            exp.merchant.contains("Ola", ignoreCase = true) ||
                            exp.tag?.contains("Transit", ignoreCase = true) == true

                        val icon = when {
                            isDining -> Icons.Outlined.Restaurant
                            isShopping -> Icons.Outlined.ShoppingBag
                            isTransit -> Icons.Outlined.DirectionsCar
                            else -> Icons.Outlined.ReceiptLong
                        }

                        val dateFmt = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(exp.dateMillis))

                        PendingReviewItem(
                            id = exp.id,
                            merchant = exp.merchant,
                            amount = exp.amount,
                            accountInfo = if (!exp.accountSuffix.isNullOrBlank()) "Account ••${exp.accountSuffix}" else "Local Vault",
                            dateInfo = dateFmt,
                            icon = icon,
                            badge = if (exp.rawText?.contains("UPI", ignoreCase = true) == true) "UPI" else null,
                            ambiguityLabel = if (exp.tag.isNullOrBlank()) "Needs Review" else null,
                            patternTitle = if (!exp.note.isNullOrBlank()) "Local Pattern Detected" else null,
                            patternDescription = exp.note ?: "Detected new unconfirmed transaction waiting for review.",
                            suggestedCategories = listOf("Food & Dining", "Shopping & Tech", "Transport", "Housing & Utilities"),
                            hintText = if (isTransit) "Flag as Tax Deductible Commute?" else if (isShopping) "Sub-category: Electronics vs ..." else null,
                            hintIcon = if (isTransit) Icons.Outlined.LocalOffer else if (isShopping) Icons.Outlined.HelpOutline else null,
                            quickActions = if (isShopping) listOf("Office", "Gadgets") else emptyList(),
                            acceptLabel = if (isTransit) "Accept" else null,
                            defaultTag = exp.tag ?: "#verified"
                        )
                    }
                )
            }
            else -> mutableStateOf(emptyList<PendingReviewItem>())
        }
    }

    // Recently resolved items list
    var resolvedItems by remember(isDemoMode, isSetupCompleted) {
        if (isDemoMode) {
            mutableStateOf(
                listOf(
                    ResolvedReviewItem(
                        id = 801,
                        merchant = "Blue Tokai Coffee",
                        amount = 280.0,
                        subtitle = "Tagged: Food & Dining • Rule applied",
                        icon = Icons.Outlined.LocalCafe
                    ),
                    ResolvedReviewItem(
                        id = 802,
                        merchant = "Airtel Broadband",
                        amount = 1178.0,
                        subtitle = "Tagged: Utilities • Auto-matched",
                        icon = Icons.Outlined.Wifi
                    )
                )
            )
        } else {
            mutableStateOf(emptyList<ResolvedReviewItem>())
        }
    }

    // Track total sessions items so progress bar calculates accurately
    val initialTotalCount = remember(isDemoMode, isSetupCompleted, dbPendingExpenses.size, unlinkedAccountItems.size) {
        if (isDemoMode) 8 // 3 unlinked + 3 pending + 2 resolved = 8 total
        else if (!isSetupCompleted) 0
        else maxOf(dbPendingExpenses.size + unlinkedAccountItems.size, 1)
    }

    fun assignAccountToItem(item: UnlinkedAccountItem, account: KnownFinancialAccount) {
        if (isDemoMode) {
            demoUnlinkedState.removeAll { it.id == item.id }
            resolvedItems = listOf(
                ResolvedReviewItem(
                    id = item.id,
                    merchant = item.merchant,
                    amount = item.amount,
                    subtitle = "Linked: ${account.institutionName} ••${account.accountSuffix}",
                    icon = Icons.Outlined.CheckCircle
                )
            ) + resolvedItems
            Toast.makeText(context, "Linked to ${account.institutionName} ••${account.accountSuffix}", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch(Dispatchers.IO) {
                try {
                    reconciliationManager.assignAccount(item.id, account.id)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Linked to ${account.institutionName} ••${account.accountSuffix}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to link account: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun ignoreItem(item: UnlinkedAccountItem) {
        if (isDemoMode) {
            demoUnlinkedState.removeAll { it.id == item.id }
            Toast.makeText(context, "Transaction ignored", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch(Dispatchers.IO) {
                val exp = item.expense ?: expenseDao.getExpenseById(item.id)
                if (exp != null) {
                    expenseDao.update(exp.copy(isPending = false, note = (exp.note ?: "") + " [Ignored unlinked account]"))
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Transaction ignored", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun bulkAssignAccounts(account: KnownFinancialAccount) {
        val idsToAssign = selectedAccountExpenseIds.toList()
        if (isDemoMode) {
            demoUnlinkedState.removeAll { it.id in idsToAssign }
            selectedAccountExpenseIds = emptySet()
            isSelectionMode = false
            showBulkAssignDialog = false
            Toast.makeText(context, "${idsToAssign.size} transactions linked to ${account.institutionName}", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch(Dispatchers.IO) {
                for (id in idsToAssign) {
                    try {
                        reconciliationManager.assignAccount(id, account.id)
                    } catch (_: Exception) {}
                }
                withContext(Dispatchers.Main) {
                    selectedAccountExpenseIds = emptySet()
                    isSelectionMode = false
                    showBulkAssignDialog = false
                    Toast.makeText(context, "${idsToAssign.size} transactions linked to ${account.institutionName}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var showResolved by remember { mutableStateOf(true) }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var attachTagEnabled by remember { mutableStateOf(true) }
    var showRuleLogsDialog by remember { mutableStateOf(false) }

    // Live search query filtering
    val filteredQueue = remember(queueItems, searchQuery) {
        if (searchQuery.isBlank()) queueItems
        else {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            queueItems.filter {
                it.merchant.lowercase(Locale.ROOT).contains(q) ||
                it.amount.toString().contains(q) ||
                it.accountInfo.lowercase(Locale.ROOT).contains(q) ||
                it.dateInfo.lowercase(Locale.ROOT).contains(q)
            }
        }
    }

    // Active item is the first in the filtered list
    val activeItem = filteredQueue.firstOrNull()
    val nextInLineItems = remember(filteredQueue) {
        if (filteredQueue.size > 1) filteredQueue.drop(1) else emptyList()
    }

    val pendingCount = queueItems.size + filteredUnlinkedItems.size
    val resolvedCount = resolvedItems.size
    val totalCount = maxOf(pendingCount + resolvedCount, initialTotalCount)
    val targetProgressFraction = if (totalCount > 0) resolvedCount.toFloat() / totalCount.toFloat() else 0f
    val animatedProgress by animateFloatAsState(targetValue = targetProgressFraction.coerceIn(0f, 1f), label = "review_progress")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(if (showHeader) Modifier.statusBarsPadding() else Modifier),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = if (showHeader) 8.dp else 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Progress / Rule Engine Card (shown when there are reviews or in demo mode)
            if (isDemoMode || (isSetupCompleted && (pendingCount > 0 || resolvedCount > 0))) {
                item(key = "progress_card") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Top row: Resolved count & percentage
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF047857),
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$resolvedCount of $totalCount resolved today",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                }
                                Text(
                                    text = "${(targetProgressFraction * 100).toInt()}%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF64748B)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Linear progress bar
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF047857),
                                trackColor = Color(0xFFF1F5F9)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Bottom row: Rule engine count & View rule logs
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Tune,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Rule engine: ${maxOf(1, totalCount)} suggested",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                Text(
                                    text = "View rule logs",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF047857),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { showRuleLogsDialog = true }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Filter Tabs (All, Needs Account, Needs Category)
            item(key = "review_filter_tabs") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val totalTriageCount = filteredUnlinkedItems.size + filteredQueue.size
                    ReviewTabPill(
                        text = "All ($totalTriageCount)",
                        isSelected = currentTab == ReviewFilterTab.ALL,
                        onClick = { currentTab = ReviewFilterTab.ALL }
                    )
                    ReviewTabPill(
                        text = "Needs Account (${filteredUnlinkedItems.size})",
                        isSelected = currentTab == ReviewFilterTab.NEEDS_ACCOUNT,
                        alertCount = if (filteredUnlinkedItems.isNotEmpty()) filteredUnlinkedItems.size else null,
                        onClick = { currentTab = ReviewFilterTab.NEEDS_ACCOUNT }
                    )
                    ReviewTabPill(
                        text = "Needs Category (${filteredQueue.size})",
                        isSelected = currentTab == ReviewFilterTab.NEEDS_CATEGORY,
                        onClick = { currentTab = ReviewFilterTab.NEEDS_CATEGORY }
                    )
                }
            }

            // UNRESOLVED ACCOUNTS Section (Action Needed: Link Bank Account)
            if (currentTab != ReviewFilterTab.NEEDS_CATEGORY && filteredUnlinkedItems.isNotEmpty()) {
                item(key = "unresolved_section_header") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFFD97706), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ACTION NEEDED: LINK BANK ACCOUNT",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309),
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFFEF3C7)
                                ) {
                                    Text(
                                        text = "${filteredUnlinkedItems.size} unlinked",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB45309),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = if (isSelectionMode) "Done" else "Select",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF055B38),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        isSelectionMode = !isSelectionMode
                                        if (!isSelectionMode) selectedAccountExpenseIds = emptySet()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Triage Sub-Banner (matching mockup uploaded_media_1793084168021.png)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(0xFFECFDF5), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Link,
                                        contentDescription = null,
                                        tint = Color(0xFF055B38),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "${filteredUnlinkedItems.size} unlinked transactions",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "Detected from SMS & notifications waiting for local account reconciliation.",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }
                }

                items(filteredUnlinkedItems, key = { "unresolved_${it.id}" }) { item ->
                    UnlinkedAccountCard(
                        item = item,
                        isSelectionMode = isSelectionMode,
                        isSelected = item.id in selectedAccountExpenseIds,
                        onToggleSelect = {
                            selectedAccountExpenseIds = if (item.id in selectedAccountExpenseIds) {
                                selectedAccountExpenseIds - item.id
                            } else {
                                selectedAccountExpenseIds + item.id
                            }
                        },
                        availableAccounts = effectiveKnownAccounts,
                        onAssignClick = { singleItemToAssign = item },
                        onQuickAssignAccount = { account ->
                            assignAccountToItem(item, account)
                        },
                        onIgnoreClick = {
                            ignoreItem(item)
                        }
                    )
                }
            }

            // 2. Empty State (When no setup has been completed OR setup completed but no reviews)
            val isEntireQueueEmpty = activeItem == null && nextInLineItems.isEmpty() && filteredUnlinkedItems.isEmpty()
            if (isEntireQueueEmpty) {
                item(key = "empty_state") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFECFDF5))
                                    .border(1.5.dp, Color(0xFFA7F3D0), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (!isSetupCompleted && !isDemoMode) Icons.Outlined.ReceiptLong else Icons.Outlined.CheckCircle,
                                    contentDescription = "No Reviews",
                                    tint = Color(0xFF047857),
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = if (!isSetupCompleted && !isDemoMode) "No Transactions to Review" else "All Caught Up",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (!isSetupCompleted && !isDemoMode) {
                                    "Setup has not been completed. Sync your SMS or banking notifications to automatically detect transactions."
                                } else {
                                    "There are no unconfirmed transactions waiting for review. All items are confirmed and synced to your private vault."
                                },
                                fontSize = 13.sp,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center,
                                lineHeight = 19.sp
                            )

                            Spacer(modifier = Modifier.height(22.dp))

                            Button(
                                onClick = {
                                    if (!isSetupCompleted && !isDemoMode) onStartSetup()
                                    else onViewLedger()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = if (!isSetupCompleted && !isDemoMode) "Start Setup" else "View Ledger",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // 3. ACTIVE REVIEW Section
            if (currentTab != ReviewFilterTab.NEEDS_ACCOUNT && activeItem != null) {
                item(key = "active_review_section") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Section Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF047857), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ACTIVE REVIEW",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569),
                                    letterSpacing = 0.8.sp
                                )
                            }

                            Text(
                                text = "Item 1 of $pendingCount",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Active Review Main Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                // Merchant Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFF1F5F9)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = activeItem.icon,
                                            contentDescription = activeItem.merchant,
                                            tint = Color(0xFF0F172A),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = activeItem.merchant,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A)
                                            )
                                            if (activeItem.badge != null) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFF1F5F9)
                                                ) {
                                                    Text(
                                                        text = activeItem.badge,
                                                        color = Color(0xFF475569),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(3.dp))

                                        Text(
                                            text = "${activeItem.dateInfo} • ${activeItem.accountInfo}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "- ₹${formatAmount(activeItem.amount)}",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        if (activeItem.ambiguityLabel != null) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = activeItem.ambiguityLabel,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB45309)
                                            )
                                        }
                                    }
                                }

                                // Local Pattern Detected Banner
                                if (activeItem.patternTitle != null) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFFFFBEB),
                                        border = BorderStroke(1.dp, Color(0xFFFEF08A)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Lightbulb,
                                                contentDescription = null,
                                                tint = Color(0xFFB45309),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = activeItem.patternTitle,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF92400E)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = activeItem.patternDescription ?: "",
                                                    fontSize = 11.5.sp,
                                                    color = Color(0xFF78350F),
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // CHOOSE CATEGORY Section
                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "CHOOSE CATEGORY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    letterSpacing = 0.8.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val categories = if (activeItem.suggestedCategories.isNotEmpty()) {
                                    activeItem.suggestedCategories
                                } else {
                                    listOf("Food & Dining", "Team Meals", "Personal Outflow", "Tax Deductible")
                                }

                                // 2x2 Category Grid
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CategoryOptionChip(
                                            text = categories.getOrNull(0) ?: "Food & Dining",
                                            icon = if (selectedCategoryIndex == 0) Icons.Outlined.CheckCircle else Icons.Outlined.Restaurant,
                                            isSelected = selectedCategoryIndex == 0,
                                            modifier = Modifier.weight(1f),
                                            onClick = { selectedCategoryIndex = 0 }
                                        )
                                        CategoryOptionChip(
                                            text = categories.getOrNull(1) ?: "Team Meals",
                                            icon = Icons.Outlined.Groups,
                                            isSelected = selectedCategoryIndex == 1,
                                            modifier = Modifier.weight(1f),
                                            onClick = { selectedCategoryIndex = 1 }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CategoryOptionChip(
                                            text = categories.getOrNull(2) ?: "Personal Outflow",
                                            icon = Icons.Outlined.Person,
                                            isSelected = selectedCategoryIndex == 2,
                                            modifier = Modifier.weight(1f),
                                            onClick = { selectedCategoryIndex = 2 }
                                        )
                                        CategoryOptionChip(
                                            text = categories.getOrNull(3) ?: "Tax Deductible",
                                            icon = Icons.Outlined.ReceiptLong,
                                            isSelected = selectedCategoryIndex == 3,
                                            modifier = Modifier.weight(1f),
                                            onClick = { selectedCategoryIndex = 3 }
                                        )
                                    }
                                }

                                // Remember Rule & Tag
                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Remember auto-categorization rule for ${activeItem.merchant.split(" ").firstOrNull() ?: activeItem.merchant}",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF1E293B)
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Attach tag",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (attachTagEnabled) Color(0xFFF1F5F9) else Color(0xFFF8FAFC),
                                        border = BorderStroke(1.dp, if (attachTagEnabled) Color(0xFFCBD5E1) else Color(0xFFE2E8F0)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { attachTagEnabled = !attachTagEnabled }
                                    ) {
                                        Text(
                                            text = activeItem.defaultTag ?: "#reimbursable",
                                            color = if (attachTagEnabled) Color(0xFF334155) else Color(0xFF94A3B8),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                // Action Buttons: Confirm & Decline / Skip
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Confirm Category (Primary)
                                    Button(
                                        onClick = {
                                            val resolvedCat = categories.getOrNull(selectedCategoryIndex) ?: "Food & Dining"
                                            val newlyResolved = ResolvedReviewItem(
                                                id = activeItem.id,
                                                merchant = activeItem.merchant,
                                                amount = activeItem.amount,
                                                subtitle = "Tagged: $resolvedCat • Rule applied",
                                                icon = activeItem.icon
                                            )
                                            resolvedItems = listOf(newlyResolved) + resolvedItems
                                            queueItems = queueItems.filter { it.id != activeItem.id }

                                            // Persist to DB if item exists in DB
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    try {
                                                        val dbExp = dbPendingExpenses.find { it.id == activeItem.id }
                                                        if (dbExp != null) {
                                                            expenseDao.update(
                                                                dbExp.copy(
                                                                    categoryId = resolvedCat,
                                                                    tag = if (attachTagEnabled) activeItem.defaultTag else dbExp.tag,
                                                                    isPending = false
                                                                )
                                                            )
                                                        }
                                                    } catch (_: Exception) {}
                                                }
                                            }

                                            Toast.makeText(context, "Confirmed category: $resolvedCat", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Confirm Category",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = Color.White
                                        )
                                    }

                                    // Decline / Dismiss Button (Increments progress & removes)
                                    Button(
                                        onClick = {
                                            val newlyResolved = ResolvedReviewItem(
                                                id = activeItem.id,
                                                merchant = activeItem.merchant,
                                                amount = activeItem.amount,
                                                subtitle = "Declined • Dismissed",
                                                icon = activeItem.icon
                                            )
                                            resolvedItems = listOf(newlyResolved) + resolvedItems
                                            queueItems = queueItems.filter { it.id != activeItem.id }

                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    try {
                                                        val dbExp = dbPendingExpenses.find { it.id == activeItem.id }
                                                        if (dbExp != null) {
                                                            expenseDao.update(dbExp.copy(isPending = false))
                                                        }
                                                    } catch (_: Exception) {}
                                                }
                                            }

                                            Toast.makeText(context, "Declined ${activeItem.merchant}", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFEE2E2),
                                            contentColor = Color(0xFF991B1B)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(46.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp)
                                    ) {
                                        Text(
                                            text = "Decline",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF991B1B)
                                        )
                                    }

                                    // Skip Button (Cycles to next item without resolving)
                                    Button(
                                        onClick = {
                                            queueItems = queueItems.drop(1) + activeItem
                                            Toast.makeText(context, "Skipped to next item", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFF1F5F9),
                                            contentColor = Color(0xFF1E293B)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(46.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp)
                                    ) {
                                        Text(
                                            text = "Skip",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF1E293B)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. NEXT IN LINE Section
            if (currentTab != ReviewFilterTab.NEEDS_ACCOUNT && nextInLineItems.isNotEmpty()) {
                item(key = "next_in_line_section") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NEXT IN LINE",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569),
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Tap to expand",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            nextInLineItems.forEach { nextItem ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            // Promote to active item
                                            queueItems = listOf(nextItem) + queueItems.filter { it.id != nextItem.id }
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp)
                                    ) {
                                        // Header Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFFF1F5F9)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = nextItem.icon,
                                                    contentDescription = nextItem.merchant,
                                                    tint = Color(0xFF0F172A),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = nextItem.merchant,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${nextItem.accountInfo} • ${nextItem.dateInfo}",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "- ₹${formatAmount(nextItem.amount)}",
                                                    fontSize = 15.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A)
                                                )
                                                if (nextItem.ambiguityLabel != null) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = nextItem.ambiguityLabel,
                                                        fontSize = 11.5.sp,
                                                        color = Color(0xFF64748B)
                                                    )
                                                }
                                            }
                                        }

                                        // Bottom Action Row if hints exist
                                        if (nextItem.hintText != null) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (nextItem.hintIcon != null) {
                                                        Icon(
                                                            imageVector = nextItem.hintIcon,
                                                            contentDescription = null,
                                                            tint = if (nextItem.acceptLabel != null) Color(0xFF047857) else Color(0xFFF59E0B),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                    }
                                                    Text(
                                                        text = nextItem.hintText,
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF475569),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                // Action Pills / Button
                                                if (nextItem.quickActions.isNotEmpty()) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        nextItem.quickActions.forEach { action ->
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = Color(0xFFF1F5F9),
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .clickable {
                                                                        val resolved = ResolvedReviewItem(
                                                                            id = nextItem.id,
                                                                            merchant = nextItem.merchant,
                                                                            amount = nextItem.amount,
                                                                            subtitle = "Tagged: $action • Auto-matched",
                                                                            icon = nextItem.icon
                                                                        )
                                                                        resolvedItems = listOf(resolved) + resolvedItems
                                                                        queueItems = queueItems.filter { it.id != nextItem.id }

                                                                        scope.launch {
                                                                            withContext(Dispatchers.IO) {
                                                                                try {
                                                                                    val dbExp = dbPendingExpenses.find { it.id == nextItem.id }
                                                                                    if (dbExp != null) {
                                                                                        expenseDao.update(dbExp.copy(categoryId = action, tag = action, isPending = false))
                                                                                    }
                                                                                } catch (_: Exception) {}
                                                                            }
                                                                        }
                                                                        Toast.makeText(context, "Assigned to $action", Toast.LENGTH_SHORT).show()
                                                                    }
                                                            ) {
                                                                Text(
                                                                    text = action,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFF0F172A),
                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                } else if (nextItem.acceptLabel != null) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = Color(0xFFECFDF5),
                                                        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable {
                                                                val resolved = ResolvedReviewItem(
                                                                    id = nextItem.id,
                                                                    merchant = nextItem.merchant,
                                                                    amount = nextItem.amount,
                                                                    subtitle = "Tagged: Tax Deductible Commute • Accepted",
                                                                    icon = nextItem.icon
                                                                )
                                                                resolvedItems = listOf(resolved) + resolvedItems
                                                                queueItems = queueItems.filter { it.id != nextItem.id }

                                                                scope.launch {
                                                                    withContext(Dispatchers.IO) {
                                                                        try {
                                                                            val dbExp = dbPendingExpenses.find { it.id == nextItem.id }
                                                                            if (dbExp != null) {
                                                                                expenseDao.update(dbExp.copy(isPending = false))
                                                                            }
                                                                        } catch (_: Exception) {}
                                                                    }
                                                                }
                                                                Toast.makeText(context, "Accepted Tax Deductible Commute", Toast.LENGTH_SHORT).show()
                                                            }
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = "✓ ${nextItem.acceptLabel}",
                                                                fontSize = 11.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF047857)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Recently Resolved Today Section
            if (resolvedItems.isNotEmpty()) {
                item(key = "recently_resolved_section") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF047857),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Recently Resolved Today (${resolvedItems.size} items)",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }

                            Text(
                                text = if (showResolved) "Hide" else "Show",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF047857),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { showResolved = !showResolved }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }

                        if (showResolved) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    resolvedItems.take(5).forEachIndexed { idx, item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFFECFDF5)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = item.merchant,
                                                    tint = Color(0xFF047857),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.merchant,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = item.subtitle,
                                                    fontSize = 11.5.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "- ₹${formatAmount(item.amount)}",
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "✓ Vaulted",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF047857)
                                                )
                                            }
                                        }

                                        if (idx < resolvedItems.take(5).size - 1) {
                                            HorizontalDivider(
                                                color = Color(0xFFF1F5F9),
                                                thickness = 1.dp,
                                                modifier = Modifier.padding(horizontal = 14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. Zero Telemetry Local Storage SQLite Card
            item(key = "zero_telemetry_banner") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Sync,
                                contentDescription = null,
                                tint = Color(0xFF047857),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Zero Telemetry Local Storage",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SQLITE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF047857)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "All vendor matches & rule engines run purely on-device. SMS text never leaves this phone.",
                                fontSize = 11.5.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Floating Stadium Multi-Select Bar (matches mockup uploaded_media_1793084168021.png)
        if (isSelectionMode && selectedAccountExpenseIds.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp, start = 20.dp, end = 20.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF0F172A),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${selectedAccountExpenseIds.size} Selected",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    TextButton(
                        onClick = { selectedAccountExpenseIds = emptySet() }
                    ) {
                        Text("Clear Selection", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }

                    Button(
                        onClick = { showBulkAssignDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF055B38)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Bulk Assign",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 7. Universal Black Floating Action Button (+ Add Expense)
        if (!isSelectionMode || selectedAccountExpenseIds.isEmpty()) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 20.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .clickable { onAddExpenseClick() },
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Expense",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add Expense",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
            }
        }
        }
    }

    // Single Item Account Assignment Dialog
    singleItemToAssign?.let { item ->
        AccountSelectionDialog(
            title = "Assign Account",
            subtitle = "For ₹${formatAmount(item.amount)} (${item.merchant})",
            detectedSuffix = item.accountSuffix,
            availableAccounts = effectiveKnownAccounts,
            onDismiss = { singleItemToAssign = null },
            onConfirm = { account ->
                assignAccountToItem(item, account)
                singleItemToAssign = null
            }
        )
    }

    // Bulk Assignment Dialog
    if (showBulkAssignDialog) {
        AccountSelectionDialog(
            title = "Bulk Assign Accounts",
            subtitle = "Assign ${selectedAccountExpenseIds.size} transactions to account",
            detectedSuffix = null,
            availableAccounts = effectiveKnownAccounts,
            onDismiss = { showBulkAssignDialog = false },
            onConfirm = { account ->
                bulkAssignAccounts(account)
            }
        )
    }

    // Rule Logs Dialog
    if (showRuleLogsDialog) {
        AlertDialog(
            onDismissRequest = { showRuleLogsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = Color(0xFF047857),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Rule Engine Logs",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Real-time on-device heuristics & pattern matching:",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("• Swiggy Delivery -> Food & Dining (Confidence 98%)", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF334155))
                            Text("• Amazon India -> Shopping & Tech (Card Regex v2)", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF334155))
                            Text("• Uber Premier -> Commute & Transport (Airport Ride)", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF334155))
                            Text("• Blue Tokai -> Food & Dining (Vault Rule #12 Applied)", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF047857))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showRuleLogsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                ) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun CategoryOptionChip(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFFECFDF5) else Color(0xFFF1F5F9),
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) Color(0xFF047857) else Color(0xFFE2E8F0)
        ),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = if (isSelected) Color(0xFF047857) else Color(0xFF475569),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color(0xFF065F46) else Color(0xFF1E293B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatAmount(amount: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale("en", "IN"))
    formatter.maximumFractionDigits = 2
    formatter.minimumFractionDigits = 2
    return formatter.format(amount)
}


@Composable
private fun ReviewTabPill(
    text: String,
    isSelected: Boolean,
    alertCount: Int? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color(0xFF0F172A) else Color.White,
        border = BorderStroke(1.dp, if (isSelected) Color(0xFF0F172A) else Color(0xFFE2E8F0)),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Color(0xFF475569)
            )
            if (alertCount != null && alertCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(Color(0xFFF59E0B), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun UnlinkedAccountCard(
    item: UnlinkedAccountItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    availableAccounts: List<KnownFinancialAccount>,
    onAssignClick: () -> Unit,
    onQuickAssignAccount: (KnownFinancialAccount) -> Unit,
    onIgnoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val merchantLower = item.merchant.lowercase(Locale.ROOT)
    val cardIcon = when {
        merchantLower.contains("swiggy") || merchantLower.contains("zomato") || merchantLower.contains("food") -> Icons.Outlined.Restaurant
        merchantLower.contains("amazon") || merchantLower.contains("flipkart") || merchantLower.contains("shop") -> Icons.Outlined.ShoppingBag
        merchantLower.contains("kirana") || merchantLower.contains("store") || merchantLower.contains("mart") -> Icons.Outlined.Storefront
        merchantLower.contains("uber") || merchantLower.contains("ola") || merchantLower.contains("transit") -> Icons.Outlined.DirectionsCar
        else -> Icons.Outlined.ReceiptLong
    }

    val compatibleAccount = remember(item.accountSuffix, availableAccounts) {
        val s = item.accountSuffix?.trim()
        if (!s.isNullOrBlank()) {
            availableAccounts.firstOrNull { it.accountSuffix.equals(s, ignoreCase = true) }
        } else null
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) Color(0xFF055B38) else Color(0xFFE2E8F0)
        ),
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .then(if (isSelectionMode) Modifier.clickable { onToggleSelect() } else Modifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Checkbox (optional) + Icon + Merchant/Date + Amount/Debit
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF055B38),
                            uncheckedColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = cardIcon,
                        contentDescription = null,
                        tint = Color(0xFF334155),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.merchant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.dateInfo,
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (item.isCredit) "+" else "-"}₹${formatAmount(item.amount)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        color = if (item.isCredit) Color(0xFF16A34A) else Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (item.isCredit) "Credit" else "Debit",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Suffix Pill & Source Badge Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!item.accountSuffix.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CreditCard,
                                contentDescription = null,
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "••${item.accountSuffix}${item.suggestedBank?.let { " ($it)" } ?: ""}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF334155)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFEF3C7)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                color = Color(0xFFB45309)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (item.source.contains("SMS", ignoreCase = true)) Icons.Outlined.Chat else Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = item.source,
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            // Raw Notification Snippet Box (Italic monospace)
            if (!item.rawText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "\"${item.rawText.trim()}\"",
                        fontSize = 11.5.sp,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF475569),
                        modifier = Modifier.padding(10.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Action Row: Ignore + Compatible Chip / Assign Account Button
            if (!isSelectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onIgnoreClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VisibilityOff,
                            contentDescription = "Ignore",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Ignore",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (compatibleAccount != null) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onQuickAssignAccount(compatibleAccount) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Link,
                                    contentDescription = null,
                                    tint = Color(0xFF055B38),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Link ${compatibleAccount.institutionName} ••${compatibleAccount.accountSuffix}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF055B38)
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onAssignClick,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color(0xFF055B38)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF055B38)
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Link,
                                contentDescription = null,
                                tint = Color(0xFF055B38),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Assign Account",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF055B38)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountSelectionDialog(
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
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Evidence banner if suffix detected
                if (!detectedSuffix.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFECFDF5),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF055B38),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Detected SMS Account: Ending in ••••$detectedSuffix",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF065F46)
                            )
                        }
                    }
                }

                // Account Selection List
                Text(
                    text = "SELECT BANK ACCOUNT OR CARD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.8.sp
                )

                if (availableAccounts.isEmpty()) {
                    Text(
                        text = "No linked accounts registered. Please link or discover accounts via SMS import.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableAccounts.forEach { acc ->
                            val isSelected = selectedAccount?.id == acc.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFFECFDF5) else Color(0xFFF8FAFC),
                                border = BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) Color(0xFF055B38) else Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedAccount = acc }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color(0xFFA7F3D0) else Color(0xFFE2E8F0)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (acc.instrumentType == InstrumentType.CARD) Icons.Outlined.CreditCard else Icons.Outlined.AccountBalance,
                                                contentDescription = null,
                                                tint = if (isSelected) Color(0xFF055B38) else Color(0xFF475569),
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column {
                                            Text(
                                                text = acc.institutionName ?: "Bank Account",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                color = Color(0xFF0F172A)
                                            )
                                            Text(
                                                text = "Ending in ••${acc.accountSuffix}",
                                                fontSize = 11.5.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color(0xFF055B38),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Text("Cancel", color = Color(0xFF475569), fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            selectedAccount?.let { onConfirm(it) }
                        },
                        enabled = selectedAccount != null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.3f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF055B38),
                            disabledContainerColor = Color(0xFF94A3B8)
                        )
                    ) {
                        Text("Confirm Link", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
