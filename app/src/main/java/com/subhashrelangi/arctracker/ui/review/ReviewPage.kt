package com.subhashrelangi.arctracker.ui.review

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.text.TextStyle
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
import com.subhashrelangi.arctracker.data.TransactionCategory
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
    val allDbCategories by categoryManager.getActiveCategoriesFlow().collectAsState(initial = emptyList())

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
    var localHandledUnlinkedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var activeUnlinkedId by remember { mutableStateOf<Int?>(null) }

    // Live search query filtering for unlinked items
    val filteredUnlinkedItems = remember(unlinkedAccountItems, localHandledUnlinkedIds, searchQuery) {
        val remaining = unlinkedAccountItems.filter { it.id !in localHandledUnlinkedIds }
        if (searchQuery.isBlank()) remaining
        else {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            remaining.filter {
                it.merchant.lowercase(Locale.ROOT).contains(q) ||
                it.amount.toString().contains(q) ||
                (it.accountSuffix?.contains(q) == true) ||
                (it.rawText?.lowercase(Locale.ROOT)?.contains(q) == true)
            }
        }
    }

    val activeUnlinkedItem = filteredUnlinkedItems.find { it.id == activeUnlinkedId } ?: filteredUnlinkedItems.firstOrNull()
    val nextUnlinkedItems = filteredUnlinkedItems.filter { it.id != activeUnlinkedItem?.id }

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
                val categoryReady = dbPendingExpenses.filter { it.accountId != null || !it.accountSuffix.isNullOrBlank() }
                mutableStateOf(
                    categoryReady.mapIndexed { idx, exp ->
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

    fun pushItemToCategoryQueue(item: UnlinkedAccountItem, account: KnownFinancialAccount) {
        val catItem = PendingReviewItem(
            id = item.id,
            merchant = item.merchant,
            amount = item.amount,
            accountInfo = "${account.institutionName ?: "Bank"} ••${account.accountSuffix}",
            dateInfo = item.dateInfo,
            icon = Icons.Outlined.ReceiptLong,
            badge = if (item.source.contains("SMS", ignoreCase = true)) "SMS" else "Push",
            ambiguityLabel = "Needs Review",
            patternTitle = "Account Linked: ${account.institutionName ?: "Bank"}",
            patternDescription = "Local bank account reconciled (••${account.accountSuffix}). Select category to complete review.",
            suggestedCategories = listOf("Food & Dining", "Shopping & Tech", "Transport", "Housing & Utilities"),
            defaultTag = "#verified"
        )
        queueItems = queueItems + catItem
    }

    fun assignAccountToItem(item: UnlinkedAccountItem, account: KnownFinancialAccount) {
        localHandledUnlinkedIds = localHandledUnlinkedIds + item.id
        if (isDemoMode) {
            demoUnlinkedState.removeAll { it.id == item.id }
        } else {
            scope.launch(Dispatchers.IO) {
                try {
                    reconciliationManager.assignAccount(item.id, account.id)
                } catch (_: Exception) {}
            }
        }

        // Immediately push to category review queue
        pushItemToCategoryQueue(item, account)

        val remainingCount = filteredUnlinkedItems.count { it.id != item.id && it.id !in localHandledUnlinkedIds }
        if (remainingCount == 0) {
            if (currentTab == ReviewFilterTab.NEEDS_ACCOUNT) {
                currentTab = ReviewFilterTab.NEEDS_CATEGORY
            }
            Toast.makeText(context, "All accounts linked! Pushed to Category Review.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Linked to ${account.institutionName} ••${account.accountSuffix}! Pushed to Category Review.", Toast.LENGTH_SHORT).show()
        }
    }

    fun ignoreItem(item: UnlinkedAccountItem) {
        localHandledUnlinkedIds = localHandledUnlinkedIds + item.id
        if (isDemoMode) {
            demoUnlinkedState.removeAll { it.id == item.id }
        } else {
            scope.launch(Dispatchers.IO) {
                try {
                    val exp = item.expense ?: expenseDao.getExpenseById(item.id)
                    if (exp != null) {
                        expenseDao.update(exp.copy(isPending = false, note = (exp.note ?: "") + " [Ignored unlinked account]"))
                    }
                } catch (_: Exception) {}
            }
        }
        resolvedItems = listOf(
            ResolvedReviewItem(
                id = item.id,
                merchant = item.merchant,
                amount = item.amount,
                subtitle = "Ignored unlinked account",
                icon = Icons.Outlined.VisibilityOff
            )
        ) + resolvedItems

        val remainingCount = filteredUnlinkedItems.count { it.id != item.id && it.id !in localHandledUnlinkedIds }
        if (remainingCount == 0 && currentTab == ReviewFilterTab.NEEDS_ACCOUNT) {
            currentTab = ReviewFilterTab.NEEDS_CATEGORY
        }
        Toast.makeText(context, "Transaction ignored", Toast.LENGTH_SHORT).show()
    }

    fun bulkAssignAccounts(account: KnownFinancialAccount) {
        val idsToAssign = selectedAccountExpenseIds.toList()
        localHandledUnlinkedIds = localHandledUnlinkedIds + idsToAssign
        if (isDemoMode) {
            demoUnlinkedState.removeAll { it.id in idsToAssign }
        } else {
            scope.launch(Dispatchers.IO) {
                for (id in idsToAssign) {
                    try {
                        reconciliationManager.assignAccount(id, account.id)
                    } catch (_: Exception) {}
                }
            }
        }

        // Push all assigned to category queue
        filteredUnlinkedItems.filter { it.id in idsToAssign }.forEach { item ->
            pushItemToCategoryQueue(item, account)
        }

        selectedAccountExpenseIds = emptySet()
        isSelectionMode = false
        showBulkAssignDialog = false

        val remainingCount = filteredUnlinkedItems.count { it.id !in idsToAssign && it.id !in localHandledUnlinkedIds }
        if (remainingCount == 0 && currentTab == ReviewFilterTab.NEEDS_ACCOUNT) {
            currentTab = ReviewFilterTab.NEEDS_CATEGORY
        }
        Toast.makeText(context, "${idsToAssign.size} transactions linked! Pushed to Category Review.", Toast.LENGTH_SHORT).show()
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

    val defaultCategoryNames = remember {
        listOf(
            "Food & Dining", "Shopping & Tech", "Transport & Commute",
            "Bills & Utilities", "Entertainment", "Healthcare & Medical",
            "Groceries & Essentials", "Travel & Vacation", "Housing & Rent",
            "Education", "Personal Care", "Investments", "Miscellaneous"
        )
    }
    var showCategorySelectionDialog by remember { mutableStateOf(false) }
    var selectedCategoryName by remember(activeItem?.id) {
        mutableStateOf(activeItem?.suggestedCategories?.firstOrNull() ?: allDbCategories.firstOrNull()?.name ?: defaultCategoryNames.first())
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
                val totalTriageCount = filteredUnlinkedItems.size + filteredQueue.size
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(Color(0xFFD97706), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ACTION NEEDED: LINK BANK ACCOUNT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309),
                                    letterSpacing = 0.6.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFFEF3C7)
                                ) {
                                    Text(
                                        text = "${filteredUnlinkedItems.size} unlinked",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB45309),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        maxLines = 1
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val currentIdx = filteredUnlinkedItems.indexOfFirst { it.id == activeUnlinkedItem?.id }.let { if (it >= 0) it + 1 else 1 }
                                Text(
                                    text = "Item $currentIdx of ${filteredUnlinkedItems.size}",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelectionMode) Color(0xFFECFDF5) else Color(0xFFF1F5F9),
                                    border = BorderStroke(1.dp, if (isSelectionMode) Color(0xFFA7F3D0) else Color(0xFFE2E8F0)),
                                    modifier = Modifier.clickable {
                                        isSelectionMode = !isSelectionMode
                                        if (!isSelectionMode) selectedAccountExpenseIds = emptySet()
                                    }
                                ) {
                                    Text(
                                        text = if (isSelectionMode) "Done" else "Select",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelectionMode) Color(0xFF047857) else Color(0xFF475569),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // Triage Sub-Banner or Top Bulk Action Card
                        if (isSelectionMode) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF0FDF4),
                                border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                                    .background(Color(0xFF047857), CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${selectedAccountExpenseIds.size} of ${filteredUnlinkedItems.size} Selected",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A)
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (selectedAccountExpenseIds.size == filteredUnlinkedItems.size) "Deselect All" else "Select All",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF047857),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        selectedAccountExpenseIds = if (selectedAccountExpenseIds.size == filteredUnlinkedItems.size) {
                                                            emptySet()
                                                        } else {
                                                            filteredUnlinkedItems.map { it.id }.toSet()
                                                        }
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )

                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFF1F5F9),
                                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                                modifier = Modifier.clickable {
                                                    isSelectionMode = false
                                                    selectedAccountExpenseIds = emptySet()
                                                }
                                            ) {
                                                Text(
                                                    text = "Cancel",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF64748B),
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = { showBulkAssignDialog = true },
                                        enabled = selectedAccountExpenseIds.isNotEmpty(),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF055B38),
                                            disabledContainerColor = Color(0xFFCBD5E1)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(vertical = 10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (selectedAccountExpenseIds.isNotEmpty()) {
                                                "Bulk Assign (${selectedAccountExpenseIds.size} Selected)"
                                            } else {
                                                "Select Transactions to Bulk Assign"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        } else {
                            // Triage Sub-Banner
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
                }

                // ONE BIG ACTIVE UNLINKED CARD
                if (activeUnlinkedItem != null) {
                    item(key = "active_unlinked_card_${activeUnlinkedItem.id}") {
                        ActiveUnlinkedAccountCard(
                            item = activeUnlinkedItem,
                            isSelectionMode = isSelectionMode,
                            isSelected = activeUnlinkedItem.id in selectedAccountExpenseIds,
                            onToggleSelect = {
                                selectedAccountExpenseIds = if (activeUnlinkedItem.id in selectedAccountExpenseIds) {
                                    selectedAccountExpenseIds - activeUnlinkedItem.id
                                } else {
                                    selectedAccountExpenseIds + activeUnlinkedItem.id
                                }
                            },
                            availableAccounts = effectiveKnownAccounts,
                            canSkip = nextUnlinkedItems.isNotEmpty(),
                            onAssignClick = { singleItemToAssign = activeUnlinkedItem },
                            onQuickAssignAccount = { account ->
                                assignAccountToItem(activeUnlinkedItem, account)
                            },
                            onIgnoreClick = {
                                ignoreItem(activeUnlinkedItem)
                            },
                            onSkipClick = {
                                activeUnlinkedId = nextUnlinkedItems.firstOrNull()?.id
                            }
                        )
                    }
                }

                // UNLINKED QUEUE (NEXT IN LINE FOR UNLINKED ITEMS)
                if (nextUnlinkedItems.isNotEmpty()) {
                    item(key = "unlinked_queue_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NEXT IN QUEUE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569),
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = if (isSelectionMode) "Select to bulk assign" else "Tap to review",
                                fontSize = 11.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    items(nextUnlinkedItems, key = { "unresolved_queue_${it.id}" }) { nextItem ->
                        UnlinkedQueueCard(
                            item = nextItem,
                            isSelectionMode = isSelectionMode,
                            isSelected = nextItem.id in selectedAccountExpenseIds,
                            onToggleSelect = {
                                selectedAccountExpenseIds = if (nextItem.id in selectedAccountExpenseIds) {
                                    selectedAccountExpenseIds - nextItem.id
                                } else {
                                    selectedAccountExpenseIds + nextItem.id
                                }
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    selectedAccountExpenseIds = if (nextItem.id in selectedAccountExpenseIds) {
                                        selectedAccountExpenseIds - nextItem.id
                                    } else {
                                        selectedAccountExpenseIds + nextItem.id
                                    }
                                } else {
                                    activeUnlinkedId = nextItem.id
                                }
                            }
                        )
                    }
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
                                // 1. Merchant Row (Title + Avatar on left, Amount on right)
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
                                                color = Color(0xFF0F172A),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
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
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text(
                                        text = "- ₹${formatAmount(activeItem.amount)}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                }

                                // 2. Clean Metadata Badges Row (Date + Account Badge + Needs Review Status Pill)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFF8FAFC),
                                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                    ) {
                                        Text(
                                            text = activeItem.dateInfo,
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF64748B),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            maxLines = 1
                                        )
                                    }

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
                                                imageVector = Icons.Outlined.AccountBalance,
                                                contentDescription = null,
                                                tint = Color(0xFF475569),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = activeItem.accountInfo,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF334155),
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    if (activeItem.ambiguityLabel != null) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFFEF3C7),
                                            border = BorderStroke(1.dp, Color(0xFFFDE68A))
                                        ) {
                                            Text(
                                                text = activeItem.ambiguityLabel,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB45309),
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                // 3. Local Pattern Detected Banner
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

                                // 4. CHOOSE CATEGORY Section with Pop-up Dialog & Suggestions
                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "CHOOSE CATEGORY",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF64748B),
                                        letterSpacing = 0.8.sp
                                    )

                                    val count = if (allDbCategories.isNotEmpty()) allDbCategories.size else defaultCategoryNames.size
                                    Text(
                                        text = "Browse all ($count)",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF047857),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { showCategorySelectionDialog = true }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Main Category Card (Opens Pop-up Modal)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { showCategorySelectionDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFECFDF5)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = getCategoryIcon(selectedCategoryName),
                                                    contentDescription = null,
                                                    tint = Color(0xFF047857),
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column {
                                                Text(
                                                    text = selectedCategoryName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF0F172A)
                                                )
                                                Text(
                                                    text = "Tap to choose or add category",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFF1F5F9),
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Change",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF047857)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Dropdown",
                                                    tint = Color(0xFF047857),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Quick Suggestion Pills (Horizontally Scrollable)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val quickOptions = (activeItem.suggestedCategories + listOf("Food & Dining", "Shopping & Tech", "Transport & Commute", "Bills & Utilities")).distinct()
                                    quickOptions.take(5).forEach { catName ->
                                        val isSelected = catName.equals(selectedCategoryName, ignoreCase = true)
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = if (isSelected) Color(0xFFECFDF5) else Color.White,
                                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF047857) else Color(0xFFE2E8F0)),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .clickable { selectedCategoryName = catName }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color(0xFF047857),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                }
                                                Text(
                                                    text = catName,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color(0xFF047857) else Color(0xFF334155),
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }

                                // 5. Remember Rule & Tag
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

                                // 6. Action Buttons: Confirm & Decline / Skip
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Confirm Category (Primary)
                                    Button(
                                        onClick = {
                                            val resolvedCat = selectedCategoryName
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
                                            .height(46.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Confirm",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            softWrap = false
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
                                                    color = Color(0xFF0F172A),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Text(
                                                text = "- ₹${formatAmount(nextItem.amount)}",
                                                fontSize = 15.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Subtitle Row: Account + Date + Ambiguity Badge
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = nextItem.accountInfo,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF475569)
                                            )
                                            Text(
                                                text = "•",
                                                fontSize = 11.5.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                            Text(
                                                text = nextItem.dateInfo,
                                                fontSize = 11.5.sp,
                                                color = Color(0xFF64748B)
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                            if (nextItem.ambiguityLabel != null) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFFEF3C7)
                                                ) {
                                                    Text(
                                                        text = nextItem.ambiguityLabel,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFB45309),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

        // Floating Stadium Multi-Select Bar (cleared above bottom navigation bar)
        if (isSelectionMode && selectedAccountExpenseIds.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp, start = 20.dp, end = 20.dp),
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

        // 7. Universal Black Floating Action Button (+ Add Expense, cleared above bottom nav)
        if (!isSelectionMode || selectedAccountExpenseIds.isEmpty()) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 90.dp, end = 20.dp)
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

    // Category Selection Modal Dialog (Pop-up with Search & Add New Category)
    if (showCategorySelectionDialog) {
        CategorySelectionModalDialog(
            categories = allDbCategories,
            selectedCategoryName = selectedCategoryName,
            onCategorySelected = { catName ->
                selectedCategoryName = catName
                showCategorySelectionDialog = false
            },
            onAddNewCategory = { newName ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val res = categoryManager.createCategory(newName)
                        withContext(Dispatchers.Main) {
                            if (res.isSuccess) {
                                selectedCategoryName = newName
                                showCategorySelectionDialog = false
                                Toast.makeText(context, "Added category: $newName", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Failed to add category", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            },
            onDismiss = { showCategorySelectionDialog = false }
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
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                fontSize = 12.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Color(0xFF475569),
                maxLines = 1,
                softWrap = false
            )
            if (alertCount != null && alertCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(Color(0xFFD97706), CircleShape)
                )
            }
        }
    }
}

// ONE BIG ACTIVE CARD for unlinked account triage
@Composable
private fun ActiveUnlinkedAccountCard(
    item: UnlinkedAccountItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    availableAccounts: List<KnownFinancialAccount>,
    canSkip: Boolean,
    onAssignClick: () -> Unit,
    onQuickAssignAccount: (KnownFinancialAccount) -> Unit,
    onIgnoreClick: () -> Unit,
    onSkipClick: () -> Unit,
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
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Avatar + Merchant & Date + Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
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
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = cardIcon,
                        contentDescription = item.merchant,
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.merchant,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = item.dateInfo,
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (item.isCredit) "+" else "-"}₹${formatAmount(item.amount)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isCredit) Color(0xFF047857) else Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (item.isCredit) "Credit" else "Debit",
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Evidence Badges Row
            Spacer(modifier = Modifier.height(12.dp))
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
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CreditCard,
                                contentDescription = null,
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(13.dp)
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
                        color = Color(0xFFFFFBEB),
                        border = BorderStroke(1.dp, Color(0xFFFEF3C7))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "No Suffix Detected",
                                fontSize = 11.5.sp,
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (item.source.contains("SMS", ignoreCase = true)) Icons.Outlined.Message else Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = item.source,
                            fontSize = 11.5.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            // Raw Notification Snippet Box (Italic monospace)
            if (!item.rawText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
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

            // Compatible 1-Tap Quick Link Chip
            if (compatibleAccount != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFECFDF5),
                    border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onQuickAssignAccount(compatibleAccount) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF047857),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "1-Tap Link to ${compatibleAccount.institutionName ?: "Bank"} (••${compatibleAccount.accountSuffix})",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF047857)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color(0xFF047857),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Action Buttons Row: Assign Account (Primary) + Ignore (Secondary) + Skip (Tertiary)
            if (!isSelectionMode) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAssignClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Assign Account",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Button(
                        onClick = onIgnoreClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF1F5F9),
                            contentColor = Color(0xFF475569)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(46.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Text(
                            text = "Ignore",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color(0xFF475569),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    if (canSkip) {
                        Button(
                            onClick = onSkipClick,
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
                                color = Color(0xFF1E293B),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

// COMPACT QUEUE CARD for Next in Line unlinked transactions
@Composable
private fun UnlinkedQueueCard(
    item: UnlinkedAccountItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) Color(0xFF055B38) else Color(0xFFE2E8F0)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ReceiptLong,
                    contentDescription = null,
                    tint = Color(0xFF334155),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.merchant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.dateInfo,
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B)
                    )
                    if (!item.accountSuffix.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ••${item.accountSuffix}",
                            fontSize = 11.5.sp,
                            color = Color(0xFF047857),
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• No Suffix",
                            fontSize = 11.sp,
                            color = Color(0xFFB45309),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (item.isCredit) "+" else "-"}₹${formatAmount(item.amount)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = if (item.isCredit) Color(0xFF047857) else Color(0xFF0F172A)
                )
                Text(
                    text = if (item.isCredit) "Credit" else "Debit",
                    fontSize = 10.5.sp,
                    color = Color(0xFF64748B)
                )
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

private fun getCategoryIcon(categoryName: String): ImageVector {
    val nameLower = categoryName.lowercase(Locale.ROOT)
    return when {
        nameLower.contains("food") || nameLower.contains("dining") || nameLower.contains("restaurant") || nameLower.contains("meal") -> Icons.Outlined.Restaurant
        nameLower.contains("shop") || nameLower.contains("store") || nameLower.contains("tech") -> Icons.Outlined.ShoppingBag
        nameLower.contains("transport") || nameLower.contains("commute") || nameLower.contains("cab") || nameLower.contains("ride") -> Icons.Outlined.DirectionsCar
        nameLower.contains("bill") || nameLower.contains("utilit") -> Icons.Outlined.Receipt
        nameLower.contains("entertainment") || nameLower.contains("movie") -> Icons.Outlined.Movie
        nameLower.contains("health") || nameLower.contains("medical") -> Icons.Outlined.MedicalServices
        nameLower.contains("travel") || nameLower.contains("flight") -> Icons.Outlined.Flight
        nameLower.contains("education") || nameLower.contains("school") -> Icons.Outlined.School
        nameLower.contains("grocer") -> Icons.Outlined.LocalGroceryStore
        nameLower.contains("house") || nameLower.contains("rent") -> Icons.Outlined.Home
        nameLower.contains("invest") || nameLower.contains("saving") -> Icons.Outlined.TrendingUp
        else -> Icons.Outlined.Category
    }
}

@Composable
private fun CategorySelectionModalDialog(
    categories: List<TransactionCategory>,
    selectedCategoryName: String,
    onCategorySelected: (String) -> Unit,
    onAddNewCategory: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isAddingNew by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    val effectiveCategories = remember(categories) {
        if (categories.isNotEmpty()) {
            categories
        } else {
            listOf(
                "Food & Dining", "Shopping & Tech", "Transport & Commute",
                "Bills & Utilities", "Entertainment", "Healthcare & Medical",
                "Groceries & Essentials", "Travel & Vacation", "Housing & Rent",
                "Education", "Personal Care", "Investments", "Miscellaneous"
            ).mapIndexed { idx, name ->
                TransactionCategory(
                    id = "default_$idx",
                    name = name,
                    iconKey = "category",
                    colorKey = "default",
                    isSystem = true,
                    isArchived = false,
                    sortOrder = idx,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }
        }
    }

    val filteredList = remember(effectiveCategories, searchQuery) {
        if (searchQuery.isBlank()) effectiveCategories
        else effectiveCategories.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shadowElevation = 12.dp,
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
                            text = "Select Category",
                            color = Color(0xFF0F172A),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Choose or create a category",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Bar
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 13.5.sp,
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.Normal
                            ),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search categories...",
                                        fontSize = 13.5.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Category List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredList, key = { it.id }) { cat ->
                        val isSelected = cat.name.equals(selectedCategoryName, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFFECFDF5) else Color(0xFFF8FAFC),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFFA7F3D0) else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCategorySelected(cat.name)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color(0xFFD1FAE5) else Color(0xFFE2E8F0)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = getCategoryIcon(cat.name),
                                            contentDescription = null,
                                            tint = if (isSelected) Color(0xFF047857) else Color(0xFF475569),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Text(
                                        text = cat.name,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFF047857) else Color(0xFF0F172A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFF047857),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (filteredList.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No category matching \"$searchQuery\"",
                                    fontSize = 12.5.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                HorizontalDivider(color = Color(0xFFE2E8F0))

                Spacer(modifier = Modifier.height(10.dp))

                // + Add Category Section
                if (!isAddingNew) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isAddingNew = true
                                if (searchQuery.isNotBlank() && filteredList.isEmpty()) {
                                    newCategoryName = searchQuery.trim()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFF047857),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (searchQuery.isNotBlank() && filteredList.isEmpty()) {
                                    "Create \"${searchQuery.trim()}\" Category"
                                } else {
                                    "+ Add New Category"
                                },
                                color = Color(0xFF047857),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFF047857)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = newCategoryName,
                                    onValueChange = { newCategoryName = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 13.5.sp,
                                        color = Color(0xFF0F172A),
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { innerTextField ->
                                        if (newCategoryName.isEmpty()) {
                                            Text(
                                                text = "Enter category name...",
                                                fontSize = 12.5.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isAddingNew = false
                                    newCategoryName = ""
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                            ) {
                                Text("Cancel", color = Color(0xFF475569), fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    val trimmed = newCategoryName.trim()
                                    if (trimmed.isNotEmpty()) {
                                        onAddNewCategory(trimmed)
                                    }
                                },
                                enabled = newCategoryName.isNotBlank(),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1.4f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF047857),
                                    disabledContainerColor = Color(0xFFCBD5E1)
                                )
                            ) {
                                Text("Save & Select", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
