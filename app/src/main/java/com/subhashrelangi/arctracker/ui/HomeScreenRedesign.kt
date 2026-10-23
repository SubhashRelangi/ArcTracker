package com.subhashrelangi.arctracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.subhashrelangi.arctracker.R
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.service.InstrumentType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==========================================
// Theme Colors matching Light Theme Home Mockup
// ==========================================
private val HomeLightBg = Color(0xFFF8FAFC)
private val TextDarkPrimary = Color(0xFF0F172A)
private val TextDarkSecondary = Color(0xFF475569)
private val TextDarkMuted = Color(0xFF64748B)

private val CardBorderLight = Color(0xFFE2E8F0)
private val CardSurfaceWhite = Color(0xFFFFFFFF)

private val EmeraldAccent = Color(0xFF10B981)
private val EmeraldDarkText = Color(0xFF059669)
private val EmeraldPillBg = Color(0xFFECFDF5)
private val EmeraldPillBorder = Color(0xFFA7F3D0)

private val AmberBannerBg = Color(0xFFFFFBEB)
private val AmberBannerBorder = Color(0xFFFDE68A)
private val AmberTitleText = Color(0xFF78350F)
private val AmberSubtitleText = Color(0xFF92400E)

private val CoralExpense = Color(0xFFF87171)
private val SlateHeroBg = Color(0xFF0B0F19)
private val SlateHeroBorder = Color(0xFF1E293B)

/**
 * Visual styling theme for realistic physical fintech cards in the wallet.
 */
enum class WalletCardTheme {
    GRAPHITE_DARK,    // HDFC Regalia: Matte dark graphite/slate (#161922 to #1E222D)
    ROYAL_PURPLE,     // ICICI Advantage: Royal purple gradient (#3B1A59 to #6B21A8)
    PORCELAIN_WHITE,  // UPI & Digital Wallets: Crisp porcelain white (#FFFFFF)
    EMERALD_SLATE,    // SBI Savings: Emerald green gradient (#064E3B to #047857)
    NAVY_MIDNIGHT     // Midnight Navy (#0F172A to #1E3A8A)
}

/**
 * Data structure representing a tangible wallet card (bank account, credit card, or digital wallet).
 */
data class WalletCardModel(
    val id: String,
    val bankName: String,
    val cardName: String,
    val suffix: String,
    val balanceAmount: Double,
    val balanceFormatted: String,
    val incomeAmount: Double,
    val incomeFormatted: String,
    val expenseAmount: Double,
    val expenseFormatted: String,
    val cardTypeLabel: String,
    val statusText: String? = null,
    val theme: WalletCardTheme,
    val isWalletUpi: Boolean = false
)

private data class CardThemeStyle(
    val bgBrush: Brush,
    val borderColor: Color,
    val contentColor: Color,
    val subtextColor: Color
)

/**
 * Data structure representing sample / display item on the Home Page.
 */
data class HomeTransactionDisplay(
    val id: Int,
    val title: String,
    val subtitle: String,
    val amountText: String,
    val isCredit: Boolean,
    val iconType: String = "general"
)

/**
 * Redesigned HomeScreen component matching the reference mockup exactly.
 *
 * Requirements:
 * 1. Follow header alignment and pure light-theme background (#F8FAFC).
 * 2. Put original data from backend (Room DB).
 * 3. In Demo Mode: show rich dummy data matching the mockup.
 * 4. Interactive Physical Wallet holding realistic cards:
 *    - Tapping a card brings it to the top/front.
 *    - Updates figures (Balance, October In, October Out) to show that specific card's figures.
 *    - Initial state shows whole combined cards figures ("TOTAL LIQUID BALANCE").
 *    - Supports >3 cards with "+N more cards • See all cards" expander.
 * 5. Review Component: only show if reviewCount > 0; if reviewCount == 0, hide completely.
 * 6. Quick Actions: Scan SMS, Budgets, Accounts, Export.
 * 7. Floating Pill Button on bottom-right: "+ Add Expense".
 */
@Composable
fun HomeScreenRedesign(
    expenses: List<Expense> = emptyList(),
    isDemoMode: Boolean = false,
    knownAccounts: List<KnownFinancialAccount> = emptyList(),
    showHeader: Boolean = true,
    onSearchClick: () -> Unit = {},
    onAddExpenseClick: () -> Unit = {},
    onReviewClick: () -> Unit = {},
    onAccountsClick: () -> Unit = {},
    onScanSmsClick: () -> Unit = {},
    onBudgetsClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
    onViewAllClick: () -> Unit = {},
    onExpenseClick: (Expense) -> Unit = {}
) {
    val scrollState = rememberScrollState()

    // Determine current month display string (e.g., "OCTOBER 2026")
    val currentMonthText = remember {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.US)
        sdf.format(Date()).uppercase()
    }

    // Filter pending/review transactions
    val pendingExpenses = expenses.filter { it.isPending }
    val realReviewCount = pendingExpenses.size

    val hasTransactions = expenses.isNotEmpty()

    // Calculate aggregated amounts across all accounts
    val totalIncome = expenses.filter { it.type.equals("Credit", ignoreCase = true) && !it.isPending }.sumOf { it.amount }
    val totalSpent = expenses.filter { !it.type.equals("Credit", ignoreCase = true) && !it.isPending }.sumOf { it.amount }

    // Build realistic physical wallet cards model list
    val walletCards: List<WalletCardModel> = remember(expenses, knownAccounts, isDemoMode) {
        if (isDemoMode) {
            listOf(
                WalletCardModel(
                    id = "hdfc",
                    bankName = "HDFC",
                    cardName = "HDFC Regalia",
                    suffix = "4821",
                    balanceAmount = 78400.0,
                    balanceFormatted = "₹78,400.00",
                    incomeAmount = 48500.0,
                    incomeFormatted = "+₹48,500",
                    expenseAmount = 12450.0,
                    expenseFormatted = "-₹12,450",
                    cardTypeLabel = "CREDIT",
                    statusText = null,
                    theme = WalletCardTheme.GRAPHITE_DARK
                ),
                WalletCardModel(
                    id = "icici",
                    bankName = "ICICI",
                    cardName = "ICICI Advantage",
                    suffix = "1002",
                    balanceAmount = 42350.0,
                    balanceFormatted = "₹42,350.00",
                    incomeAmount = 25000.0,
                    incomeFormatted = "+₹25,000",
                    expenseAmount = 14800.0,
                    expenseFormatted = "-₹14,800",
                    cardTypeLabel = "VISA",
                    statusText = null,
                    theme = WalletCardTheme.ROYAL_PURPLE
                ),
                WalletCardModel(
                    id = "upi",
                    bankName = "UPI",
                    cardName = "UPI & Digital Wallets",
                    suffix = "",
                    balanceAmount = 27500.0,
                    balanceFormatted = "₹27,500.00",
                    incomeAmount = 11500.0,
                    incomeFormatted = "+₹11,500",
                    expenseAmount = 9500.0,
                    expenseFormatted = "-₹9,500",
                    cardTypeLabel = "WALLET",
                    statusText = "Active",
                    theme = WalletCardTheme.PORCELAIN_WHITE,
                    isWalletUpi = true
                ),
                WalletCardModel(
                    id = "sbi",
                    bankName = "SBI",
                    cardName = "SBI Global Savings",
                    suffix = "8820",
                    balanceAmount = 14200.0,
                    balanceFormatted = "₹14,200.00",
                    incomeAmount = 15000.0,
                    incomeFormatted = "+₹15,000",
                    expenseAmount = 800.0,
                    expenseFormatted = "-₹800",
                    cardTypeLabel = "SAVINGS",
                    statusText = "Active",
                    theme = WalletCardTheme.EMERALD_SLATE
                )
            )
        } else if (knownAccounts.isNotEmpty()) {
            val themes = listOf(
                WalletCardTheme.GRAPHITE_DARK,
                WalletCardTheme.ROYAL_PURPLE,
                WalletCardTheme.PORCELAIN_WHITE,
                WalletCardTheme.EMERALD_SLATE,
                WalletCardTheme.NAVY_MIDNIGHT
            )
            knownAccounts.mapIndexed { index, acct ->
                val acctExpenses = expenses.filter {
                    (it.accountId != null && it.accountId == acct.id) ||
                    (!it.accountSuffix.isNullOrBlank() && it.accountSuffix == acct.accountSuffix)
                }
                val inc = acctExpenses.filter { it.type.equals("Credit", true) && !it.isPending }.sumOf { it.amount }
                val exp = acctExpenses.filter { !it.type.equals("Credit", true) && !it.isPending }.sumOf { it.amount }
                val net = inc - exp
                val bName = acct.institutionName ?: acct.institutionId?.uppercase() ?: "Account"
                val label = when (acct.instrumentType) {
                    InstrumentType.CARD -> "CARD"
                    InstrumentType.BANK_ACCOUNT -> "BANK"
                    else -> "ACCOUNT"
                }
                val cardTitle = when (acct.instrumentType) {
                    InstrumentType.CARD -> "$bName Card"
                    InstrumentType.BANK_ACCOUNT -> "$bName Savings"
                    else -> "$bName Account"
                }
                WalletCardModel(
                    id = acct.id,
                    bankName = bName,
                    cardName = cardTitle,
                    suffix = acct.accountSuffix,
                    balanceAmount = net,
                    balanceFormatted = formatInrAmount(net),
                    incomeAmount = inc,
                    incomeFormatted = "+₹" + formatInrShort(inc),
                    expenseAmount = exp,
                    expenseFormatted = "-₹" + formatInrShort(exp),
                    cardTypeLabel = label,
                    statusText = if (acctExpenses.isNotEmpty()) "Active" else null,
                    theme = themes[index % themes.size],
                    isWalletUpi = false
                )
            }
        } else if (hasTransactions) {
            val grouped = expenses.groupBy { it.accountSuffix?.takeIf { s -> s.isNotBlank() } ?: "General" }
            val themes = listOf(
                WalletCardTheme.GRAPHITE_DARK,
                WalletCardTheme.ROYAL_PURPLE,
                WalletCardTheme.PORCELAIN_WHITE,
                WalletCardTheme.EMERALD_SLATE
            )
            grouped.entries.mapIndexed { index, (suffix, list) ->
                val inc = list.filter { it.type.equals("Credit", true) && !it.isPending }.sumOf { it.amount }
                val exp = list.filter { !it.type.equals("Credit", true) && !it.isPending }.sumOf { it.amount }
                val net = inc - exp
                val title = if (suffix == "General") "Primary Account" else "Account ••$suffix"
                WalletCardModel(
                    id = suffix,
                    bankName = if (suffix == "General") "Primary" else "Bank",
                    cardName = title,
                    suffix = if (suffix == "General") "" else suffix,
                    balanceAmount = net,
                    balanceFormatted = formatInrAmount(net),
                    incomeAmount = inc,
                    incomeFormatted = "+₹" + formatInrShort(inc),
                    expenseAmount = exp,
                    expenseFormatted = "-₹" + formatInrShort(exp),
                    cardTypeLabel = "ACCOUNT",
                    statusText = "Active",
                    theme = themes[index % themes.size]
                )
            }
        } else {
            emptyList()
        }
    }

    // State for interactive card selection and expandable deck (>3 cards)
    var selectedCardId by remember { mutableStateOf<String?>(null) }
    var isWalletExpanded by remember { mutableStateOf(false) }

    val activeCard = walletCards.find { it.id == selectedCardId }

    // Final balance figures: Specific Card vs Total Liquid Balance
    val displayBalance: String
    val displayIncome: String
    val displaySpent: String
    val displayReviewCount: Int
    val displayReviewSubtitle: String
    val heroTitle: String

    if (activeCard != null) {
        // Figures updated specifically for the selected card
        heroTitle = "${activeCard.cardName.uppercase()} BALANCE"
        displayBalance = activeCard.balanceFormatted
        displayIncome = activeCard.incomeFormatted
        displaySpent = activeCard.expenseFormatted
        displayReviewCount = if (isDemoMode) 3 else realReviewCount
        displayReviewSubtitle = if (isDemoMode) "Swiggy, Amazon & 1 other" else ""
    } else if (isDemoMode) {
        // Initial whole cards figures matching mockup
        heroTitle = "TOTAL LIQUID BALANCE"
        displayBalance = "₹1,48,250.00"
        displayIncome = "+₹85,000"
        displaySpent = "-₹36,750"
        displayReviewCount = 3
        displayReviewSubtitle = "Swiggy, Amazon & 1 other"
    } else if (hasTransactions) {
        val net = totalIncome - totalSpent
        heroTitle = "TOTAL LIQUID BALANCE"
        displayBalance = formatInrAmount(net)
        displayIncome = "+₹" + formatInrShort(totalIncome)
        displaySpent = "-₹" + formatInrShort(totalSpent)
        displayReviewCount = realReviewCount
        displayReviewSubtitle = if (pendingExpenses.isNotEmpty()) {
            val sample = pendingExpenses.take(2).joinToString(", ") { it.merchant }
            if (pendingExpenses.size > 2) "$sample & ${pendingExpenses.size - 2} other" else sample
        } else ""
    } else {
        // User completed setup but skipped scanning (0 transactions recorded)
        heroTitle = "TOTAL LIQUID BALANCE"
        displayBalance = "₹0.00"
        displayIncome = "+₹0"
        displaySpent = "-₹0"
        displayReviewCount = 0
        displayReviewSubtitle = ""
    }

    // Determine recent activity items (filtered by selected card when active)
    val recentItems: List<HomeTransactionDisplay> = remember(expenses, isDemoMode, hasTransactions, selectedCardId) {
        if (isDemoMode) {
            val allDemo = listOf(
                HomeTransactionDisplay(
                    id = 1,
                    title = "Salary credit",
                    subtitle = "Income • 09:12 • HDFC ••4821",
                    amountText = "+₹48,500",
                    isCredit = true,
                    iconType = "income"
                ),
                HomeTransactionDisplay(
                    id = 2,
                    title = "Blue Tokai Coffee",
                    subtitle = "Dining • Yesterday • HDFC ••4821",
                    amountText = "-₹280",
                    isCredit = false,
                    iconType = "dining"
                ),
                HomeTransactionDisplay(
                    id = 3,
                    title = "Amazon India",
                    subtitle = "Shopping • 18 Oct • ICICI ••1002",
                    amountText = "-₹1,850",
                    isCredit = false,
                    iconType = "shopping"
                ),
                HomeTransactionDisplay(
                    id = 4,
                    title = "Electricity Bill",
                    subtitle = "Utilities • 14 Oct • UPI",
                    amountText = "-₹1,420",
                    isCredit = false,
                    iconType = "utilities"
                )
            )
            when (selectedCardId) {
                "hdfc" -> allDemo.filter { it.subtitle.contains("HDFC") }
                "icici" -> allDemo.filter { it.subtitle.contains("ICICI") }
                "upi" -> allDemo.filter { it.subtitle.contains("UPI") }
                "sbi" -> emptyList()
                else -> allDemo
            }
        } else if (hasTransactions) {
            val filteredExpenses = if (activeCard != null) {
                expenses.filter {
                    (it.accountId != null && it.accountId == activeCard.id) ||
                    (!it.accountSuffix.isNullOrBlank() && it.accountSuffix == activeCard.suffix)
                }
            } else {
                expenses
            }
            val nonPending = filteredExpenses.filter { !it.isPending }.take(6)
            val list = if (nonPending.isNotEmpty()) nonPending else filteredExpenses.take(6)
            list.map { exp ->
                val isCred = exp.type.equals("Credit", ignoreCase = true)
                val timeStr = formatTimestamp(exp.dateMillis)
                val cat = exp.category ?: if (isCred) "Income" else "General"
                val acct = if (!exp.accountSuffix.isNullOrBlank()) "••${exp.accountSuffix}" else "Account"
                HomeTransactionDisplay(
                    id = exp.id,
                    title = exp.merchant.ifBlank { if (isCred) "Credit" else "Expense" },
                    subtitle = "$cat • $timeStr • $acct",
                    amountText = (if (isCred) "+₹" else "-₹") + formatInrShort(exp.amount),
                    isCredit = isCred,
                    iconType = mapCategoryToIconType(cat)
                )
            }
        } else {
            emptyList()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeLightBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (showHeader) Modifier.statusBarsPadding() else Modifier)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = if (showHeader) 10.dp else 4.dp, bottom = 100.dp)
        ) {
            if (showHeader) {
                // ----------------------------------------------------
                // 1. Top App Header: Clean "ArcTracker" + Actions
                // ----------------------------------------------------
                HomeTopHeader(
                    onSearchClick = onSearchClick,
                    onProfileClick = onAccountsClick
                )

                Spacer(modifier = Modifier.height(14.dp))

                // ----------------------------------------------------
                // 2. Subheader: Date Selector + "Vault Encrypted"
                // ----------------------------------------------------
                HomeSubheader(
                    monthText = currentMonthText,
                    onMonthClick = onViewAllClick
                )

                Spacer(modifier = Modifier.height(14.dp))
            }

            // ----------------------------------------------------
            // 3. Interactive Wallet Hero Block:
            //    - Physical Card Deck (HDFC, ICICI, UPI, etc.)
            //    - Tapping a card brings it to the top & filters figures
            //    - Concentric radar background circles
            //    - Total Liquid Balance + Split In / Out
            // ----------------------------------------------------
            HomeDarkHeroCard(
                cards = walletCards,
                selectedCardId = selectedCardId,
                onCardSelect = { id -> selectedCardId = id },
                isWalletExpanded = isWalletExpanded,
                onToggleExpand = { isWalletExpanded = !isWalletExpanded },
                heroTitle = heroTitle,
                balance = displayBalance,
                income = displayIncome,
                spent = displaySpent,
                isDemoMode = isDemoMode,
                onAccountsClick = onAccountsClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ----------------------------------------------------
            // 4. Review Alert Banner:
            //    ONLY shown if user has review transactions (reviewCount > 0).
            //    If reviewCount == 0, completely omitted.
            // ----------------------------------------------------
            if (displayReviewCount > 0) {
                ReviewAlertBanner(
                    reviewCount = displayReviewCount,
                    subtitle = displayReviewSubtitle,
                    onReviewClick = onReviewClick
                )

                Spacer(modifier = Modifier.height(14.dp))
            }

            // ----------------------------------------------------
            // 5. Quick Actions Hub (Unified Single Component Card)
            // ----------------------------------------------------
            HomeQuickActionsHubCard(
                onScanSmsClick = onScanSmsClick,
                onBudgetsClick = onBudgetsClick,
                onAccountsClick = onAccountsClick,
                onExportClick = onExportClick
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ----------------------------------------------------
            // 6. Recent Activity Section
            // ----------------------------------------------------
            HomeRecentActivitySection(
                items = recentItems,
                activeCardName = activeCard?.cardName,
                hasTransactions = hasTransactions,
                isDemoMode = isDemoMode,
                onViewAllClick = onViewAllClick,
                onScanSmsClick = onScanSmsClick,
                onItemClick = { item ->
                    val exp = expenses.firstOrNull { it.id == item.id }
                    if (exp != null) {
                        onExpenseClick(exp)
                    } else {
                        onViewAllClick()
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ----------------------------------------------------
        // 7. Floating Pill Button on bottom-right: "+ Add Expense"
        // ----------------------------------------------------
        Surface(
            onClick = onAddExpenseClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 72.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(999.dp)),
            shape = RoundedCornerShape(999.dp),
            color = TextDarkPrimary,
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Expense",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add Expense",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ==========================================
// 1. Top Header: Clean "ArcTracker" + Actions
// ==========================================
@Composable
private fun HomeTopHeader(
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader(
        title = "ArcTracker",
        showSearch = true,
        showProfile = true,
        onSearchClick = onSearchClick,
        onProfileClick = onProfileClick,
        horizontalPadding = 0.dp,
        verticalPadding = 4.dp,
        modifier = Modifier.fillMaxWidth()
    )
}

// ==========================================
// 2. Subheader: Date Selector + Vault Encrypted
// ==========================================
@Composable
private fun HomeSubheader(
    monthText: String,
    onMonthClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date Selector Pill
        Surface(
            onClick = onMonthClick,
            shape = RoundedCornerShape(20.dp),
            color = CardSurfaceWhite,
            border = BorderStroke(1.dp, CardBorderLight),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarToday,
                    contentDescription = "Calendar",
                    tint = TextDarkMuted,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = monthText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDarkPrimary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Select Month",
                    tint = TextDarkMuted,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // Vault Encrypted Status
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "Vault Encrypted",
                tint = EmeraldAccent,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "Vault Encrypted",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = TextDarkMuted
            )
        }
    }
}

// ==========================================
// 3. Dark Hero Card: Realistic Physical Leather Wallet Pocket (Image 0)
//    & Full-Sized Apple Wallet Cascading Deck (Image 2)
// ==========================================
@Composable
private fun HomeDarkHeroCard(
    cards: List<WalletCardModel>,
    selectedCardId: String?,
    onCardSelect: (String?) -> Unit,
    isWalletExpanded: Boolean,
    onToggleExpand: () -> Unit,
    heroTitle: String,
    balance: String,
    income: String,
    spent: String,
    isDemoMode: Boolean,
    onAccountsClick: () -> Unit
) {
    if (isWalletExpanded) {
        // State 2: Full-Sized Apple Wallet Cascading Deck (Image 2)
        AppleWalletCascadingDeck(
            cards = cards,
            selectedCardId = selectedCardId,
            onCardSelect = onCardSelect,
            onCollapse = onToggleExpand,
            heroTitle = heroTitle,
            balance = balance,
            income = income,
            spent = spent,
            onAccountsClick = onAccountsClick
        )
    } else {
        // State 1: Physical Leather Wallet Pocket (Image 0)
        PhysicalLeatherPocketWallet(
            cards = cards,
            selectedCardId = selectedCardId,
            onCardSelect = onCardSelect,
            onExpand = onToggleExpand,
            heroTitle = heroTitle,
            balance = balance,
            income = income,
            spent = spent,
            isDemoMode = isDemoMode,
            onAccountsClick = onAccountsClick
        )
    }
}

/**
 * State 1: Physical Leather Wallet Pocket matching reference Image 0.
 * Cards are tucked inside the pocket opening at the top and slide UP when tapped.
 * Front leather flap holds the balance and metrics with stitched leather styling.
 */
@Composable
private fun PhysicalLeatherPocketWallet(
    cards: List<WalletCardModel>,
    selectedCardId: String?,
    onCardSelect: (String?) -> Unit,
    onExpand: () -> Unit,
    heroTitle: String,
    balance: String,
    income: String,
    spent: String,
    isDemoMode: Boolean,
    onAccountsClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .drawBehind {
                // Subtle concentric radar rings radiating outward behind the balance section
                val centerX = size.width * 0.88f
                val centerY = size.height * 0.60f
                val radarColor = Color(0xFF1E293B).copy(alpha = 0.45f)
                val stroke = Stroke(width = 1.dp.toPx())
                drawCircle(color = radarColor, radius = 45.dp.toPx(), center = Offset(centerX, centerY), style = stroke)
                drawCircle(color = radarColor, radius = 90.dp.toPx(), center = Offset(centerX, centerY), style = stroke)
                drawCircle(color = radarColor, radius = 140.dp.toPx(), center = Offset(centerX, centerY), style = stroke)
                drawCircle(color = radarColor, radius = 195.dp.toPx(), center = Offset(centerX, centerY), style = stroke)

                // Realistic leather perimeter dashed stitching
                val stitchColor = Color(0xFF334155).copy(alpha = 0.7f)
                val stitchStroke = Stroke(
                    width = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )
                drawRoundRect(
                    color = stitchColor,
                    topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                    size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()),
                    cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
                    style = stitchStroke
                )
            },
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF0B0F19),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, start = 14.dp, end = 14.dp, bottom = 14.dp)
        ) {
            // ----------------------------------------------------
            // A. Pocket Opening: Cards tucked inside the pocket mouth
            // ----------------------------------------------------
            if (cards.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAccountsClick),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF131B2A),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CreditCard,
                                contentDescription = "Cards",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Empty Wallet",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Tap to add accounts or scan SMS alerts",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            } else {
                // Tucked cards container (holds 3 stepped cards peeking out from pocket opening)
                val pocketCards = remember(cards, selectedCardId) {
                    if (cards.size <= 3) {
                        cards
                    } else if (selectedCardId == null) {
                        cards.take(3)
                    } else {
                        val active = cards.find { it.id == selectedCardId }
                        if (active != null) {
                            val others = cards.filter { it.id != selectedCardId }
                            listOf(others[0], others.getOrNull(1) ?: cards[0], active)
                        } else {
                            cards.take(3)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    pocketCards.forEachIndexed { index, card ->
                        key(card.id) {
                            val isSelected = card.id == selectedCardId

                            // Slot offsets inside the pocket sleeve
                            val slot = if (selectedCardId == null) {
                                index // 0 = back, 1 = middle, 2 = front
                            } else {
                                if (isSelected) 2 else if (index == 0) 0 else 1
                            }

                            val (baseY, cardWidthRatio, zIndex) = when (slot) {
                                0 -> Triple(6.dp, 0.92f, 1f)
                                1 -> Triple(36.dp, 0.96f, 2f)
                                else -> Triple(66.dp, 1.0f, if (isSelected) 10f else 3f)
                            }

                            // If selected, card animates sliding UP out of the pocket!
                            val targetY = if (isSelected) baseY - 24.dp else baseY

                            val animatedY by animateDpAsState(
                                targetValue = targetY,
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
                                label = "pocketCardY_${card.id}"
                            )
                            val animatedWidth by animateFloatAsState(
                                targetValue = cardWidthRatio,
                                animationSpec = tween(220),
                                label = "pocketCardWidth_${card.id}"
                            )

                            Box(
                                modifier = Modifier
                                    .offset(y = animatedY)
                                    .fillMaxWidth(animatedWidth)
                                    .align(Alignment.TopCenter)
                                    .zIndex(zIndex)
                            ) {
                                PocketTuckedCard(
                                    card = card,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isSelected) {
                                            onCardSelect(null)
                                        } else {
                                            onCardSelect(card.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ----------------------------------------------------
            // B. Front Leather Pocket Flap (Curved Rim + Balance & Split In/Out)
            // ----------------------------------------------------
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        // Stitching along the top curved rim of the leather flap
                        val stitchColor = Color(0xFF334155).copy(alpha = 0.65f)
                        val stroke = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)
                        )
                        drawLine(
                            color = stitchColor,
                            start = Offset(14.dp.toPx(), 0f),
                            end = Offset(size.width - 14.dp.toPx(), 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                    },
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF111726),
                border = BorderStroke(1.dp, Color(0xFF1E293B).copy(alpha = 0.8f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    // Header Row: Hero Title + See all cards / Reset pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = if (selectedCardId == null) Icons.Outlined.AccountBalanceWallet else Icons.Outlined.CreditCard,
                                contentDescription = "Wallet Icon",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = heroTitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF94A3B8),
                                letterSpacing = 0.8.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (selectedCardId != null) {
                            // Reset to All Cards interactive pill
                            Surface(
                                onClick = { onCardSelect(null) },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Reset to All",
                                        color = Color(0xFFFDE68A),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Reset Selection",
                                        tint = Color(0xFFFDE68A),
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        } else {
                            val accountsCount = if (isDemoMode) 4 else maxOf(1, cards.size)
                            Surface(
                                onClick = onExpand,
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "See all cards ($accountsCount)",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Outlined.NorthEast,
                                        contentDescription = "Expand Cards",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Balance Amount (Split Integer & Decimal) + Trend / Active Pill
                    val (intPart, decPart) = splitInrAmount(balance)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = intPart,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = decPart,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(bottom = 2.5.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF064E3B))
                                .border(BorderStroke(1.dp, Color(0xFF047857)), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (selectedCardId != null) "● Active" else "↗ +4.2%",
                                color = Color(0xFF34D399),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Split Mini-Cards (October In / October Out)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Left: Month In
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0C101A),
                            border = BorderStroke(1.dp, Color(0xFF1E293B))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "October In",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(19.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF064E3B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowDownward,
                                            contentDescription = "Income",
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = income,
                                    color = Color(0xFF34D399),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Right: Month Out
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF0C101A),
                            border = BorderStroke(1.dp, Color(0xFF1E293B))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "October Out",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(19.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E293B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowUpward,
                                            contentDescription = "Expense",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = spent,
                                    color = CoralExpense,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * State 2: Full-Sized Apple Wallet Cascading Deck matching reference Image 2.
 * Renders full realistic credit cards cascading vertically with swipe/scroll capability.
 * Clicking any card selects it, brings it forward, and updates transactions.
 */
@Composable
private fun AppleWalletCascadingDeck(
    cards: List<WalletCardModel>,
    selectedCardId: String?,
    onCardSelect: (String?) -> Unit,
    onCollapse: () -> Unit,
    heroTitle: String,
    balance: String,
    income: String,
    spent: String,
    onAccountsClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp)),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF0B0F19),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: "WALLET CARDS (4)" + "Tuck into wallet ▲"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CreditCard,
                        contentDescription = "Cards Stack",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "WALLET CARDS (${cards.size})",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                Surface(
                    onClick = onCollapse,
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tuck into wallet",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowUp,
                            contentDescription = "Collapse to wallet",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Cascading Cards Stack (Apple Wallet Style - Image 2)
            val deckHeight = if (cards.isEmpty()) {
                120.dp
            } else {
                ((cards.size - 1) * 52 + 168).dp
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(deckHeight)
            ) {
                cards.forEachIndexed { index, card ->
                    key(card.id) {
                        val isSelected = card.id == selectedCardId

                        // Apple Wallet vertical offset: each card starts 52dp below the prior one
                        val baseY = (index * 52).dp
                        // Active selected card lifts forward with animated spring
                        val targetY = if (isSelected) baseY - 12.dp else baseY
                        val zIndex = if (isSelected) 30f else (index + 1).toFloat()

                        val animatedY by animateDpAsState(
                            targetValue = targetY,
                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
                            label = "cascadingCardY_${card.id}"
                        )

                        Box(
                            modifier = Modifier
                                .offset(y = animatedY)
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .zIndex(zIndex)
                        ) {
                            FullRealisticCreditCard(
                                card = card,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        onCardSelect(null)
                                    } else {
                                        onCardSelect(card.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Summary Bar: Active figures + Quick Tuck Back Pill
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF111726),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = heroTitle,
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = balance,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selectedCardId != null) {
                            Surface(
                                onClick = { onCardSelect(null) },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Text(
                                    text = "Reset",
                                    color = Color(0xFFFDE68A),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = onCollapse,
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E293B),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Done",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowUp,
                                    contentDescription = "Done",
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
}

/**
 * Authentic full-sized credit card component (Image 2 style).
 * Height: 160dp, Rounded corners: 18dp.
 * Contains:
 * - Card type/category label (`CREDIT`, `DEBIT`, `SAVINGS`, `WALLET`)
 * - Network / Bank emblem (`VISA`, `Mastercard`, `HDFC`, `ICICI`, `SBI`)
 * - Golden EMV metallic chip with contacts grid
 * - Contactless waves `))))`
 * - Masked card number `•••• •••• •••• 4821`
 * - Cardholder name & balance
 */
@Composable
private fun FullRealisticCreditCard(
    card: WalletCardModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)

    val style = when (card.theme) {
        WalletCardTheme.GRAPHITE_DARK -> {
            CardThemeStyle(
                bgBrush = Brush.linearGradient(
                    listOf(Color(0xFF262C3A), Color(0xFF151922), Color(0xFF0E1118))
                ),
                borderColor = if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155),
                contentColor = Color.White,
                subtextColor = Color(0xFF94A3B8)
            )
        }
        WalletCardTheme.ROYAL_PURPLE -> {
            CardThemeStyle(
                bgBrush = Brush.linearGradient(
                    listOf(Color(0xFF4C1D95), Color(0xFF6D28D9), Color(0xFF3B0764))
                ),
                borderColor = if (isSelected) Color(0xFFFDE68A) else Color(0xFF7C3AED).copy(alpha = 0.6f),
                contentColor = Color.White,
                subtextColor = Color(0xFFDDD6FE)
            )
        }
        WalletCardTheme.PORCELAIN_WHITE -> {
            CardThemeStyle(
                bgBrush = Brush.linearGradient(
                    listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC), Color(0xFFE2E8F0))
                ),
                borderColor = if (isSelected) Color(0xFF2563EB) else Color(0xFFCBD5E1),
                contentColor = TextDarkPrimary,
                subtextColor = Color(0xFF64748B)
            )
        }
        WalletCardTheme.EMERALD_SLATE -> {
            CardThemeStyle(
                bgBrush = Brush.linearGradient(
                    listOf(Color(0xFF065F46), Color(0xFF047857), Color(0xFF022C22))
                ),
                borderColor = if (isSelected) Color(0xFF34D399) else Color(0xFF10B981).copy(alpha = 0.6f),
                contentColor = Color.White,
                subtextColor = Color(0xFFA7F3D0)
            )
        }
        WalletCardTheme.NAVY_MIDNIGHT -> {
            CardThemeStyle(
                bgBrush = Brush.linearGradient(
                    listOf(Color(0xFF1E3A8A), Color(0xFF172554), Color(0xFF0F172A))
                ),
                borderColor = if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155),
                contentColor = Color.White,
                subtextColor = Color(0xFF94A3B8)
            )
        }
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, style.borderColor),
        shadowElevation = if (isSelected) 10.dp else 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(style.bgBrush)
                .drawBehind {
                    // Subtle glossy curved reflection arc across top-right of card
                    val path = Path().apply {
                        moveTo(size.width * 0.45f, 0f)
                        cubicTo(
                            size.width * 0.55f, size.height * 0.4f,
                            size.width * 0.85f, size.height * 0.7f,
                            size.width, size.height * 0.85f
                        )
                        lineTo(size.width, 0f)
                        close()
                    }
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
                        )
                    )
                }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Top Row: Card Type (DEBIT/CREDIT/WALLET) + Network / Bank Emblem
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = card.cardTypeLabel.uppercase(),
                            color = style.contentColor.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )

                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (card.theme == WalletCardTheme.PORCELAIN_WHITE) Color(0xFFEFF6FF)
                                        else Color.White.copy(alpha = 0.22f)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = "● Active",
                                    color = if (card.theme == WalletCardTheme.PORCELAIN_WHITE) Color(0xFF2563EB) else Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Bank or Payment Network Emblem
                    when {
                        card.isWalletUpi -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF2563EB))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "UPI / WALLET",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                        card.cardTypeLabel.equals("VISA", true) || card.id == "icici" -> {
                            Text(
                                text = "VISA",
                                color = style.contentColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                fontStyle = FontStyle.Italic,
                                letterSpacing = 1.sp
                            )
                        }
                        card.id == "hdfc" -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(Color(0xFF004C8F), RoundedCornerShape(2.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("H", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "HDFC BANK",
                                    color = style.contentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = card.bankName.uppercase(),
                                color = style.contentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }

                // 2. Middle Row: Metallic Golden EMV Chip + Contactless Wave Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Golden EMV Chip
                    Box(
                        modifier = Modifier
                            .size(width = 34.dp, height = 25.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFE5C07B), Color(0xFFD19A66), Color(0xFFB8860B))
                                )
                            )
                            .border(BorderStroke(0.8.dp, Color(0xFF8A6212)), RoundedCornerShape(5.dp))
                            .drawBehind {
                                // Chip internal circuit contact lines
                                val stroke = Stroke(width = 0.8.dp.toPx())
                                drawLine(Color(0xFF8A6212), Offset(size.width * 0.5f, 0f), Offset(size.width * 0.5f, size.height), strokeWidth = 0.8.dp.toPx())
                                drawLine(Color(0xFF8A6212), Offset(0f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), strokeWidth = 0.8.dp.toPx())
                            }
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // Contactless Wave Icon (4 curved arcs)
                    Icon(
                        imageVector = Icons.Outlined.Wifi,
                        contentDescription = "Contactless",
                        tint = style.contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // 3. Bottom Row: Masked Number + Cardholder Name on left, Balance on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        val maskedDigits = if (card.suffix.isNotBlank()) {
                            "••••  ••••  ••••  ${card.suffix}"
                        } else {
                            "••••  ••••  ••••  8820"
                        }
                        Text(
                            text = maskedDigits,
                            color = style.contentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = card.cardName.uppercase(),
                            color = style.subtextColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.6.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "BALANCE",
                            color = style.subtextColor,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = card.balanceFormatted,
                            color = style.contentColor,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Realistic card header strip tucked inside the leather pocket (Image 0 style).
 * Shows top portion of card with bank badge, card title, and chip outline.
 */
@Composable
private fun PocketTuckedCard(
    card: WalletCardModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 10.dp, bottomEnd = 10.dp)

    val style = when (card.theme) {
        WalletCardTheme.GRAPHITE_DARK -> {
            CardThemeStyle(
                bgBrush = Brush.horizontalGradient(listOf(Color(0xFF222836), Color(0xFF141822))),
                borderColor = if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155),
                contentColor = Color.White,
                subtextColor = Color(0xFF94A3B8)
            )
        }
        WalletCardTheme.ROYAL_PURPLE -> {
            CardThemeStyle(
                bgBrush = Brush.horizontalGradient(listOf(Color(0xFF5B21B6), Color(0xFF6D28D9))),
                borderColor = if (isSelected) Color(0xFFFDE68A) else Color(0xFF7C3AED).copy(alpha = 0.6f),
                contentColor = Color.White,
                subtextColor = Color(0xFFDDD6FE)
            )
        }
        WalletCardTheme.PORCELAIN_WHITE -> {
            CardThemeStyle(
                bgBrush = Brush.horizontalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF1F5F9))),
                borderColor = if (isSelected) Color(0xFF2563EB) else Color(0xFFCBD5E1),
                contentColor = TextDarkPrimary,
                subtextColor = Color(0xFF64748B)
            )
        }
        WalletCardTheme.EMERALD_SLATE -> {
            CardThemeStyle(
                bgBrush = Brush.horizontalGradient(listOf(Color(0xFF065F46), Color(0xFF047857))),
                borderColor = if (isSelected) Color(0xFF34D399) else Color(0xFF10B981).copy(alpha = 0.6f),
                contentColor = Color.White,
                subtextColor = Color(0xFFA7F3D0)
            )
        }
        WalletCardTheme.NAVY_MIDNIGHT -> {
            CardThemeStyle(
                bgBrush = Brush.horizontalGradient(listOf(Color(0xFF1E3A8A), Color(0xFF0F172A))),
                borderColor = if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155),
                contentColor = Color.White,
                subtextColor = Color(0xFF94A3B8)
            )
        }
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(if (isSelected) 1.8.dp else 1.dp, style.borderColor),
        shadowElevation = if (isSelected) 8.dp else 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(style.bgBrush)
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Bank Badge + Card Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    when {
                        card.isWalletUpi -> {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2563EB)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = "Wallet",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        card.cardTypeLabel.equals("VISA", true) -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "VISA",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = card.bankName.take(1).uppercase(),
                                    color = style.contentColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = card.cardName,
                        color = style.contentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (card.theme == WalletCardTheme.PORCELAIN_WHITE) Color(0xFFEFF6FF)
                                    else Color.White.copy(alpha = 0.2f)
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "Active",
                                color = if (card.theme == WalletCardTheme.PORCELAIN_WHITE) Color(0xFF2563EB) else Color.White,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right: EMV Chip Mini Graphic + Suffix or Balance
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Mini chip indicator
                    Box(
                        modifier = Modifier
                            .size(width = 18.dp, height = 13.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFE5C07B), Color(0xFFB8860B))
                                )
                            )
                    )

                    if (card.suffix.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "••${card.suffix}",
                            color = style.subtextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. Review Alert Banner (Warm Amber)
// ==========================================
@Composable
private fun ReviewAlertBanner(
    reviewCount: Int,
    subtitle: String,
    onReviewClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onReviewClick),
        shape = RoundedCornerShape(16.dp),
        color = AmberBannerBg,
        border = BorderStroke(1.dp, AmberBannerBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Star / Seal Icon Container
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFDE68A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.RateReview,
                    contentDescription = "Review Required",
                    tint = AmberTitleText,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Subtitle Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (reviewCount == 1) "1 Unreviewed Entry" else "$reviewCount Unreviewed Entries",
                    color = AmberTitleText,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = AmberSubtitleText,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // "Review →" Button matching component amber theme
            Surface(
                onClick = onReviewClick,
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFD97706)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Review",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Outlined.ArrowForward,
                        contentDescription = "Go to Review",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// 5. Quick Actions Hub (Unified Single Component Card)
// ==========================================
@Composable
private fun HomeQuickActionsHubCard(
    onScanSmsClick: () -> Unit,
    onBudgetsClick: () -> Unit,
    onAccountsClick: () -> Unit,
    onExportClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CardSurfaceWhite,
        border = BorderStroke(1.dp, CardBorderLight),
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Scan SMS
            QuickActionHubItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Sms,
                iconColor = Color(0xFF2563EB),
                iconBgColor = Color(0xFFEFF6FF),
                title = "Scan SMS",
                subtitle = "Sync alerts",
                onClick = onScanSmsClick
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(Color(0xFFF1F5F9))
            )

            // 2. Budgets
            QuickActionHubItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Savings,
                iconColor = Color(0xFF059669),
                iconBgColor = Color(0xFFECFDF5),
                title = "Budgets",
                subtitle = "Spend caps",
                onClick = onBudgetsClick
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(Color(0xFFF1F5F9))
            )

            // 3. Accounts
            QuickActionHubItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.AccountBalance,
                iconColor = Color(0xFFD97706),
                iconBgColor = Color(0xFFFFFBEB),
                title = "Accounts",
                subtitle = "Bank vaults",
                onClick = onAccountsClick
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(Color(0xFFF1F5F9))
            )

            // 4. Export
            QuickActionHubItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.FileDownload,
                iconColor = Color(0xFF7C3AED),
                iconBgColor = Color(0xFFF5F3FF),
                title = "Export",
                subtitle = "CSV / PDF",
                onClick = onExportClick
            )
        }
    }
}

@Composable
private fun QuickActionHubItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconColor: Color,
    iconBgColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = title,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextDarkPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(1.dp))

        Text(
            text = subtitle,
            fontSize = 10.sp,
            color = TextDarkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ==========================================
// 6. Recent Activity Section
// ==========================================
@Composable
private fun HomeRecentActivitySection(
    items: List<HomeTransactionDisplay>,
    activeCardName: String? = null,
    hasTransactions: Boolean,
    isDemoMode: Boolean,
    onViewAllClick: () -> Unit,
    onScanSmsClick: () -> Unit,
    onItemClick: (HomeTransactionDisplay) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (activeCardName != null) "Recent • $activeCardName" else "Recent Activity",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextDarkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            Row(
                modifier = Modifier.clickable(onClick = onViewAllClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "View all",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2563EB)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    imageVector = Icons.Outlined.ArrowForward,
                    contentDescription = "View all transactions",
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Activity Card Container
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = CardSurfaceWhite,
            border = BorderStroke(1.dp, CardBorderLight),
            shadowElevation = 0.5.dp
        ) {
            if (items.isEmpty() && activeCardName != null) {
                // Specific card selected has 0 transactions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CreditCard,
                            contentDescription = "No transactions",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "No recent transactions",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Transactions for $activeCardName will appear here once detected.",
                        fontSize = 12.sp,
                        color = TextDarkMuted,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (items.isEmpty() && !isDemoMode) {
                // ----------------------------------------------------
                // Clean Empty State Caption for users who completed setup
                // but skipped scanning (0 transactions recorded)
                // ----------------------------------------------------
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ReceiptLong,
                            contentDescription = "No transactions",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "No transactions recorded yet",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "ArcTracker will automatically capture banking alerts and SMS notifications locally, or tap Scan SMS to import history.",
                        fontSize = 12.sp,
                        color = TextDarkMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = onScanSmsClick,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, CardBorderLight),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextDarkPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Sms,
                            contentDescription = "Scan SMS",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Scan SMS",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                // List of transactions
                Column(modifier = Modifier.fillMaxWidth()) {
                    items.forEachIndexed { index, item ->
                        RecentTransactionItem(
                            item = item,
                            onClick = { onItemClick(item) }
                        )
                        if (index < items.size - 1) {
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

@Composable
private fun RecentTransactionItem(
    item: HomeTransactionDisplay,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category Icon Circle
        val (iconVector, iconTint, iconBg) = when (item.iconType) {
            "income" -> Triple(Icons.AutoMirrored.Rounded.TrendingUp, EmeraldDarkText, EmeraldPillBg)
            "dining" -> Triple(Icons.Outlined.Restaurant, Color(0xFFEA580C), Color(0xFFFFF7ED))
            "shopping" -> Triple(Icons.Outlined.ShoppingBag, Color(0xFF2563EB), Color(0xFFEFF6FF))
            "utilities" -> Triple(Icons.Outlined.Bolt, Color(0xFFDC2626), Color(0xFFFEF2F2))
            else -> Triple(Icons.Outlined.CreditCard, Color(0xFF475569), Color(0xFFF1F5F9))
        }

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = item.title,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDarkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.subtitle,
                fontSize = 11.5.sp,
                color = TextDarkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Amount
        Text(
            text = item.amountText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (item.isCredit) EmeraldDarkText else TextDarkPrimary
        )
    }
}

// ==========================================
// Formatting Utilities
// ==========================================
private fun splitInrAmount(amountText: String): Pair<String, String> {
    val dotIndex = amountText.lastIndexOf('.')
    return if (dotIndex != -1) {
        amountText.substring(0, dotIndex) to amountText.substring(dotIndex)
    } else {
        amountText to ".00"
    }
}

private fun formatInrShortWithSymbol(amount: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale("en", "IN"))
    formatter.maximumFractionDigits = 0
    formatter.minimumFractionDigits = 0
    return "₹" + formatter.format(amount)
}

private fun formatInrAmount(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return formatter.format(amount)
}

private fun formatInrShort(amount: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale("en", "IN"))
    formatter.maximumFractionDigits = 0
    return formatter.format(amount)
}

private fun formatTimestamp(dateMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - dateMillis
    val oneDay = 24 * 60 * 60 * 1000L
    return when {
        diff < 60 * 60 * 1000L -> "${maxOf(1, diff / (60 * 1000L))}m ago"
        diff < oneDay -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(dateMillis))
        diff < 2 * oneDay -> "Yesterday"
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(dateMillis))
    }
}

private fun mapCategoryToIconType(category: String): String {
    val lower = category.lowercase(Locale.ROOT)
    return when {
        lower.contains("salary") || lower.contains("income") || lower.contains("deposit") -> "income"
        lower.contains("food") || lower.contains("dining") || lower.contains("coffee") || lower.contains("restaurant") -> "dining"
        lower.contains("shop") || lower.contains("amazon") || lower.contains("flipkart") || lower.contains("store") -> "shopping"
        lower.contains("bill") || lower.contains("util") || lower.contains("electric") || lower.contains("recharge") -> "utilities"
        else -> "general"
    }
}
