package com.subhashrelangi.arctracker.ui

import com.subhashrelangi.arctracker.ui.core.ArcFloatingAddExpenseButton

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.subhashrelangi.arctracker.engine.account.InstrumentType
import com.subhashrelangi.arctracker.ui.theme.ArcColors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==========================================
// Theme Colors matching Universal ArcColors (Dynamic Light/Dark)
// ==========================================
private val HomeLightBg: Color @Composable @ReadOnlyComposable get() = ArcColors.Background
private val TextDarkPrimary: Color @Composable @ReadOnlyComposable get() = ArcColors.TextPrimary
private val TextDarkSecondary: Color @Composable @ReadOnlyComposable get() = ArcColors.TextSecondary
private val TextDarkMuted: Color @Composable @ReadOnlyComposable get() = ArcColors.TextMuted

private val CardBorderLight: Color @Composable @ReadOnlyComposable get() = ArcColors.Border
private val CardSurfaceWhite: Color @Composable @ReadOnlyComposable get() = ArcColors.CardBackground

private val EmeraldAccent: Color @Composable @ReadOnlyComposable get() = ArcColors.Primary
private val EmeraldDarkText: Color @Composable @ReadOnlyComposable get() = ArcColors.PrimaryDark
private val EmeraldPillBg: Color @Composable @ReadOnlyComposable get() = ArcColors.PrimaryLight
private val EmeraldPillBorder: Color @Composable @ReadOnlyComposable get() = ArcColors.PrimaryBorder

private val AmberBannerBg: Color @Composable @ReadOnlyComposable get() = ArcColors.WarningAmberBg
private val AmberBannerBorder: Color @Composable @ReadOnlyComposable get() = ArcColors.WarningAmberBorder
private val AmberTitleText: Color @Composable @ReadOnlyComposable get() = ArcColors.WarningAmber
private val AmberSubtitleText: Color @Composable @ReadOnlyComposable get() = ArcColors.TextSecondary

private val CoralExpense: Color @Composable @ReadOnlyComposable get() = ArcColors.DebitDanger
private val SlateHeroBg: Color @Composable @ReadOnlyComposable get() = if (!ArcColors.isDark) Color(0xFF0B0F19) else ArcColors.SurfaceElevated
private val SlateHeroBorder: Color @Composable @ReadOnlyComposable get() = if (!ArcColors.isDark) Color(0xFF1E293B) else ArcColors.Border

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
    onExpenseClick: (Expense) -> Unit = {},
    onExpenseLongClick: (Expense) -> Unit = {}
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
                    InstrumentType.WALLET -> "WALLET"
                    InstrumentType.CASH -> "CASH"
                    else -> "ACCOUNT"
                }
                val cardTitle = when (acct.instrumentType) {
                    InstrumentType.CARD -> "$bName Card"
                    InstrumentType.BANK_ACCOUNT -> "$bName Savings"
                    InstrumentType.WALLET -> bName
                    InstrumentType.CASH -> bName
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
                    isWalletUpi = acct.instrumentType == InstrumentType.WALLET || bName.contains("UPI", ignoreCase = true)
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
                },
                onItemLongClick = { item ->
                    val exp = expenses.firstOrNull { it.id == item.id }
                    if (exp != null) {
                        onExpenseLongClick(exp)
                    }
                }
            )

            // Dynamic bottom clearance so content is never covered by bottom nav bar and floating action button
            Spacer(modifier = Modifier.navigationBarsPadding().height(104.dp))
        }

        // ----------------------------------------------------
        // 7. Floating Pill Button on bottom-right: "+ Add Expense"
        // ----------------------------------------------------
        ArcFloatingAddExpenseButton(
            onClick = onAddExpenseClick,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsRepo = remember { com.subhashrelangi.arctracker.settings.MonitoringSettingsRepository.getInstance(context) }
    val profileName by settingsRepo.getProfileNameFlow().collectAsState()
    val profileInitials = remember(profileName) {
        val parts = profileName.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts[0].first().uppercase()}${parts[1].first().uppercase()}"
            parts.size == 1 && parts[0].isNotEmpty() -> "${parts[0].first().uppercase()}"
            else -> "PV"
        }
    }

    com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader(
        title = "ArcTracker",
        showSearch = true,
        showProfile = true,
        profileInitials = profileInitials,
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
    val radarColor = if (ArcColors.isDark) ArcColors.Border.copy(alpha = 0.35f) else Color(0xFF1E293B).copy(alpha = 0.45f)
    val stitchColor = if (ArcColors.isDark) ArcColors.BorderSubtle else Color(0xFF334155).copy(alpha = 0.7f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .drawBehind {
                // Subtle concentric radar rings radiating outward behind the balance section
                val centerX = size.width * 0.88f
                val centerY = size.height * 0.60f
                val stroke = Stroke(width = 1.dp.toPx())
                drawCircle(color = radarColor, radius = 45.dp.toPx(), center = Offset(centerX, centerY), style = stroke)
                drawCircle(color = radarColor, radius = 90.dp.toPx(), center = Offset(centerX, centerY), style = stroke)
                drawCircle(color = radarColor, radius = 140.dp.toPx(), center = Offset(centerX, centerY), style = stroke)
                drawCircle(color = radarColor, radius = 195.dp.toPx(), center = Offset(centerX, centerY), style = stroke)

                // Realistic leather perimeter dashed stitching
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
        color = if (ArcColors.isDark) ArcColors.CardBackground else Color(0xFF0B0F19),
        border = BorderStroke(1.dp, if (ArcColors.isDark) ArcColors.Border else Color(0xFF1E293B))
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
                    color = if (ArcColors.isDark) ArcColors.SurfaceHighlight else Color(0xFF131B2A),
                    border = BorderStroke(1.dp, if (ArcColors.isDark) ArcColors.BorderSubtle else Color(0xFF1E293B))
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
                                .background(if (ArcColors.isDark) ArcColors.SurfaceElevated else Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CreditCard,
                                contentDescription = "Cards",
                                tint = if (ArcColors.isDark) ArcColors.TextSecondary else Color(0xFF94A3B8),
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
                                else -> Triple(66.dp, 1.0f, if (isSelected) 25f else 3f)
                            }

                            // If selected, card animates sliding UP out of the pocket and scales up!
                            val targetY = if (isSelected) baseY - 28.dp else if (selectedCardId != null) baseY + 4.dp else baseY
                            val targetScale = if (isSelected) 1.03f else 1.0f

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
                            val animatedScale by animateFloatAsState(
                                targetValue = targetScale,
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
                                label = "pocketCardScale_${card.id}"
                            )

                            Box(
                                modifier = Modifier
                                    .offset(y = animatedY)
                                    .fillMaxWidth(animatedWidth)
                                    .graphicsLayer {
                                        scaleX = animatedScale
                                        scaleY = animatedScale
                                    }
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
            val flapStitchColor = if (ArcColors.isDark) ArcColors.BorderSubtle else Color(0xFF334155).copy(alpha = 0.65f)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        // Stitching along the top curved rim of the leather flap
                        val stroke = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)
                        )
                        drawLine(
                            color = flapStitchColor,
                            start = Offset(14.dp.toPx(), 0f),
                            end = Offset(size.width - 14.dp.toPx(), 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                    },
                shape = RoundedCornerShape(20.dp),
                color = if (ArcColors.isDark) ArcColors.SurfaceElevated else Color(0xFF111726),
                border = BorderStroke(1.dp, if (ArcColors.isDark) ArcColors.Border else Color(0xFF1E293B).copy(alpha = 0.8f))
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
                                tint = if (ArcColors.isDark) ArcColors.TextSecondary else Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = heroTitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (ArcColors.isDark) ArcColors.TextSecondary else Color(0xFF94A3B8),
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
                                color = if (ArcColors.isDark) ArcColors.SurfaceHighlight else Color(0xFF1E293B),
                                border = BorderStroke(1.dp, if (ArcColors.isDark) ArcColors.BorderSubtle else Color(0xFF334155))
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
                                color = if (ArcColors.isDark) ArcColors.SurfaceHighlight else Color(0xFF1E293B),
                                border = BorderStroke(1.dp, if (ArcColors.isDark) ArcColors.BorderSubtle else Color(0xFF334155))
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
                            color = if (ArcColors.isDark) ArcColors.CardBackground else Color(0xFF0C101A),
                            border = BorderStroke(1.dp, if (ArcColors.isDark) ArcColors.Border else Color(0xFF1E293B))
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
                                        color = if (ArcColors.isDark) ArcColors.TextSecondary else Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(19.dp)
                                            .clip(CircleShape)
                                            .background(if (ArcColors.isDark) Color(0xFF064E3B) else Color(0xFF064E3B)),
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
                            color = if (ArcColors.isDark) ArcColors.CardBackground else Color(0xFF0C101A),
                            border = BorderStroke(1.dp, if (ArcColors.isDark) ArcColors.Border else Color(0xFF1E293B))
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
                                        color = if (ArcColors.isDark) ArcColors.TextSecondary else Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(19.dp)
                                            .clip(CircleShape)
                                            .background(if (ArcColors.isDark) ArcColors.SurfaceHighlight else Color(0xFF1E293B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowUpward,
                                            contentDescription = "Expense",
                                            tint = if (ArcColors.isDark) ArcColors.TextSecondary else Color(0xFF94A3B8),
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

// ==========================================
// Card Components: EMV Chip, NFC Waves, Network Logos & Authentic Physical Cards
// ==========================================

/**
 * Visual metallic EMV smart chip with golden micro-circuit contact etching.
 */
@Composable
private fun EmvChip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 38.dp, height = 28.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFDF79),
                        Color(0xFFFFD54F),
                        Color(0xFFD4AF37),
                        Color(0xFFA67C1E),
                        Color(0xFFFFE082)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(80f, 80f)
                )
            )
            .border(BorderStroke(0.8.dp, Color(0xFF7A540D).copy(alpha = 0.85f)), RoundedCornerShape(5.dp))
            .drawBehind {
                val chipW = size.width
                val chipH = size.height
                val grooveColor = Color(0xFF6B4708).copy(alpha = 0.85f)
                val strokeW = 0.9.dp.toPx()
                val padRadius = 3.dp.toPx()

                // Center rounded rectangular contact island
                val centerRectW = chipW * 0.36f
                val centerRectH = chipH * 0.44f
                val centerLeft = (chipW - centerRectW) / 2f
                val centerTop = (chipH - centerRectH) / 2f
                drawRoundRect(
                    color = grooveColor,
                    topLeft = Offset(centerLeft, centerTop),
                    size = Size(centerRectW, centerRectH),
                    cornerRadius = CornerRadius(padRadius, padRadius),
                    style = Stroke(width = strokeW)
                )

                // Left & right horizontal grooves connecting to outer edge
                drawLine(grooveColor, Offset(0f, chipH * 0.34f), Offset(centerLeft, chipH * 0.34f), strokeW)
                drawLine(grooveColor, Offset(0f, chipH * 0.66f), Offset(centerLeft, chipH * 0.66f), strokeW)
                drawLine(grooveColor, Offset(centerLeft + centerRectW, chipH * 0.34f), Offset(chipW, chipH * 0.34f), strokeW)
                drawLine(grooveColor, Offset(centerLeft + centerRectW, chipH * 0.66f), Offset(chipW, chipH * 0.66f), strokeW)

                // Top & bottom vertical grooves
                drawLine(grooveColor, Offset(chipW * 0.5f, 0f), Offset(chipW * 0.5f, centerTop), strokeW)
                drawLine(grooveColor, Offset(chipW * 0.5f, centerTop + centerRectH), Offset(chipW * 0.5f, chipH), strokeW)

                // Specular highlight on top contact edge
                drawLine(
                    Color.White.copy(alpha = 0.45f),
                    Offset(centerLeft + padRadius, centerTop + 0.8.dp.toPx()),
                    Offset(centerLeft + centerRectW - padRadius, centerTop + 0.8.dp.toPx()),
                    strokeWidth = 0.8.dp.toPx()
                )
            }
    )
}

/**
 * NFC / Contactless payment indicator featuring 4 radiating curved wave arcs.
 */
@Composable
private fun ContactlessWaves(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
        val center = Offset(w * 0.15f, h * 0.5f)

        // 4 concentric arcs radiating to the right
        val radii = listOf(w * 0.22f, w * 0.42f, w * 0.62f, w * 0.82f)
        val sweepAngle = 70f
        val startAngle = -35f

        radii.forEachIndexed { index, r ->
            val alpha = 0.40f + (index * 0.18f)
            drawArc(
                color = tint.copy(alpha = alpha.coerceAtMost(1f)),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2, r * 2),
                style = stroke
            )
        }
    }
}

/**
 * Detects appropriate card network based on model data.
 */
private fun detectCardNetwork(card: WalletCardModel): String {
    return when {
        card.isWalletUpi || card.bankName.contains("UPI", ignoreCase = true) || card.cardTypeLabel.equals("WALLET", ignoreCase = true) -> "UPI"
        card.cardTypeLabel.contains("VISA", ignoreCase = true) || card.cardName.contains("Advantage", ignoreCase = true) || card.id.contains("icici", ignoreCase = true) -> "VISA"
        card.cardTypeLabel.contains("Mastercard", ignoreCase = true) || card.cardName.contains("Regalia", ignoreCase = true) || card.id.contains("hdfc", ignoreCase = true) -> "MASTERCARD"
        card.cardTypeLabel.contains("RUPAY", ignoreCase = true) || card.cardName.contains("RuPay", ignoreCase = true) || card.cardName.contains("Global", ignoreCase = true) || card.id.contains("sbi", ignoreCase = true) -> "RUPAY"
        else -> card.cardTypeLabel.ifBlank { "CARD" }
    }
}

/**
 * Payment network badge: VISA, MASTERCARD, RUPAY, UPI.
 */
@Composable
private fun CardNetworkLogo(
    network: String,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    when (network.uppercase()) {
        "VISA" -> {
            Text(
                text = "VISA",
                color = contentColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = 1.2.sp,
                modifier = modifier
            )
        }
        "MASTERCARD" -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier
            ) {
                Box(modifier = Modifier.size(width = 32.dp, height = 20.dp)) {
                    // Left circle: Mastercard Red
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEB001B))
                    )
                    // Right circle: Mastercard Orange/Yellow
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .offset(x = 12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF79E1B).copy(alpha = 0.92f))
                    )
                }
            }
        }
        "RUPAY" -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.92f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Ru",
                    color = Color(0xFF003366),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Pay",
                    color = Color(0xFFF37021),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic
                )
            }
        }
        "UPI" -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF0F9D58), Color(0xFF2563EB))
                        )
                    )
                    .padding(horizontal = 7.dp, vertical = 2.5.dp)
            ) {
                Text(
                    text = "UPI",
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
            }
        }
        else -> {
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(4.dp))
                    .border(BorderStroke(0.8.dp, contentColor.copy(alpha = 0.6f)), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = network.uppercase(),
                    color = contentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

/**
 * State 2: Full-Sized Apple Wallet Cascading Deck matching reference Image 2.
 * Renders full realistic credit cards cascading vertically with bring-to-front animation.
 * When user taps a card:
 * - Selected card animates (scale up + translate to top/front) with spring-based transition
 * - Other cards shift and cascade behind it in a neat stack below
 * - Selected card shows expanded details (balance, income, spent)
 * - Tapping again or clicking reset returns to the full cascade fan
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
        color = if (ArcColors.isDark) ArcColors.CardBackground else Color(0xFF0B0F19),
        border = BorderStroke(1.dp, if (ArcColors.isDark) ArcColors.Border else Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: "WALLET CARDS (N)" + "Tuck into wallet ▲"
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
                    color = if (ArcColors.isDark) ArcColors.SurfaceHighlight else Color(0xFF1E293B),
                    border = BorderStroke(1.dp, if (ArcColors.isDark) ArcColors.BorderSubtle else Color(0xFF334155))
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

            // Dynamic Bring-To-Front Cascading Cards Stack
            if (cards.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAccountsClick),
                    shape = RoundedCornerShape(16.dp),
                    color = if (ArcColors.isDark) ArcColors.SurfaceHighlight else Color(0xFF131B2A),
                    border = BorderStroke(1.dp, if (ArcColors.isDark) ArcColors.BorderSubtle else Color(0xFF1E293B))
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
                                .background(if (ArcColors.isDark) ArcColors.SurfaceElevated else Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CreditCard,
                                contentDescription = "Cards",
                                tint = if (ArcColors.isDark) ArcColors.TextSecondary else Color(0xFF94A3B8),
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
                val selectedIndex = cards.indexOfFirst { it.id == selectedCardId }
                val targetDeckHeight = remember(cards.size, selectedCardId) {
                    if (selectedCardId == null) {
                        ((cards.size - 1) * 56 + 220).dp
                    } else {
                        (220 + (cards.size - 1) * 48 + 12).dp
                    }
                }

                val animatedDeckHeight by animateDpAsState(
                    targetValue = targetDeckHeight,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                    label = "cascadingDeckHeight"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(animatedDeckHeight)
                ) {
                    cards.forEachIndexed { index, card ->
                        key(card.id) {
                            val isSelected = card.id == selectedCardId

                            // Bring-to-front physics calculation:
                            val (targetY, targetScale, zIndex) = if (selectedCardId == null) {
                                // Default cascade fan: each card steps down by 56dp
                                Triple((index * 56).dp, 1.0f, (index + 1).toFloat())
                            } else if (isSelected) {
                                // Selected card animates to front/top of the deck!
                                Triple(0.dp, 1.02f, 100f)
                            } else {
                                // Other cards shift and cascade behind it in a stack below
                                val nonSelectedIndex = if (index < selectedIndex) index else index - 1
                                val yOffset = (212 + nonSelectedIndex * 48).dp
                                Triple(yOffset, 0.97f, (nonSelectedIndex + 1).toFloat())
                            }

                            val animatedY by animateDpAsState(
                                targetValue = targetY,
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                                label = "cascadingCardY_${card.id}"
                            )
                            val animatedScale by animateFloatAsState(
                                targetValue = targetScale,
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                                label = "cascadingCardScale_${card.id}"
                            )

                            Box(
                                modifier = Modifier
                                    .offset(y = animatedY)
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = animatedScale
                                        scaleY = animatedScale
                                    }
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
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Summary Bar: Active figures + Quick Reset / Tuck Back Pill
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (ArcColors.isDark) ArcColors.SurfaceElevated else Color(0xFF111726),
                border = BorderStroke(1.dp, if (ArcColors.isDark) ArcColors.Border else Color(0xFF1E293B))
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
                            color = if (ArcColors.isDark) ArcColors.TextSecondary else Color(0xFF94A3B8),
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
                                color = if (ArcColors.isDark) ArcColors.SurfaceHighlight else Color(0xFF1E293B),
                                border = BorderStroke(1.dp, if (ArcColors.isDark) ArcColors.BorderSubtle else Color(0xFF334155))
                            ) {
                                Text(
                                    text = "Reset to All",
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
                            color = if (ArcColors.isDark) ArcColors.SurfaceHighlight else Color(0xFF1E293B),
                            border = BorderStroke(1.dp, if (ArcColors.isDark) ArcColors.BorderSubtle else Color(0xFF334155))
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
 * Authentic full-sized physical credit/debit card component.
 * Aspect ratio: standard ISO/IEC 7810 ID-1 (85.60 mm × 53.98 mm ≈ 1.586:1).
 * Features:
 * - Bank name prominently at top-left
 * - EMV chip visual with etched contact circuits
 * - NFC / Contactless payment indicator
 * - Masked card number (•••• •••• •••• XXXX) in embossed monospace styling
 * - Cardholder name & Valid Thru expiry date
 * - Card type / payment network label (VISA/MASTERCARD/RUPAY/UPI)
 * - Premium gradient backgrounds per theme with metallic sheen and glossy reflection
 * - Expanded details (balance, income, spent) when selected
 */
@Composable
private fun FullRealisticCreditCard(
    card: WalletCardModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)

    val style = when (card.theme) {
        WalletCardTheme.GRAPHITE_DARK -> {
            CardThemeStyle(
                bgBrush = Brush.linearGradient(
                    colors = listOf(Color(0xFF2A2E3D), Color(0xFF181B24), Color(0xFF0F1117)),
                    start = Offset(0f, 0f),
                    end = Offset(450f, 450f)
                ),
                borderColor = if (isSelected) Color(0xFF38BDF8) else Color(0xFF475569).copy(alpha = 0.65f),
                contentColor = Color.White,
                subtextColor = Color(0xFF94A3B8)
            )
        }
        WalletCardTheme.ROYAL_PURPLE -> {
            CardThemeStyle(
                bgBrush = Brush.linearGradient(
                    colors = listOf(Color(0xFF5B168C), Color(0xFF3B0764), Color(0xFF1E0338)),
                    start = Offset(0f, 0f),
                    end = Offset(450f, 450f)
                ),
                borderColor = if (isSelected) Color(0xFFFDE68A) else Color(0xFF8B5CF6).copy(alpha = 0.65f),
                contentColor = Color.White,
                subtextColor = Color(0xFFE9D5FF)
            )
        }
        WalletCardTheme.PORCELAIN_WHITE -> {
            if (ArcColors.isDark) {
                CardThemeStyle(
                    bgBrush = Brush.linearGradient(
                        colors = listOf(Color(0xFF242C3D), Color(0xFF141926), Color(0xFF090D15)),
                        start = Offset(0f, 0f),
                        end = Offset(450f, 450f)
                    ),
                    borderColor = if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155),
                    contentColor = Color(0xFFF8FAFC),
                    subtextColor = Color(0xFF94A3B8)
                )
            } else {
                CardThemeStyle(
                    bgBrush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC), Color(0xFFE2E8F0)),
                        start = Offset(0f, 0f),
                        end = Offset(450f, 450f)
                    ),
                    borderColor = if (isSelected) Color(0xFF2563EB) else Color(0xFFCBD5E1),
                    contentColor = TextDarkPrimary,
                    subtextColor = Color(0xFF64748B)
                )
            }
        }
        WalletCardTheme.EMERALD_SLATE -> {
            CardThemeStyle(
                bgBrush = Brush.linearGradient(
                    colors = listOf(Color(0xFF065F46), Color(0xFF047857), Color(0xFF022C22)),
                    start = Offset(0f, 0f),
                    end = Offset(450f, 450f)
                ),
                borderColor = if (isSelected) Color(0xFF34D399) else Color(0xFF059669).copy(alpha = 0.7f),
                contentColor = Color.White,
                subtextColor = Color(0xFFA7F3D0)
            )
        }
        WalletCardTheme.NAVY_MIDNIGHT -> {
            CardThemeStyle(
                bgBrush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1E3A8A), Color(0xFF172554), Color(0xFF0A0F1D)),
                    start = Offset(0f, 0f),
                    end = Offset(450f, 450f)
                ),
                borderColor = if (isSelected) Color(0xFF38BDF8) else Color(0xFF2563EB).copy(alpha = 0.65f),
                contentColor = Color.White,
                subtextColor = Color(0xFF93C5FD)
            )
        }
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.586f),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, style.borderColor),
        shadowElevation = if (isSelected) 14.dp else 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(style.bgBrush)
                .drawBehind {
                    // Subtle glossy reflection arc across top-right of card
                    val path = Path().apply {
                        moveTo(size.width * 0.38f, 0f)
                        cubicTo(
                            size.width * 0.48f, size.height * 0.35f,
                            size.width * 0.78f, size.height * 0.7f,
                            size.width, size.height * 0.85f
                        )
                        lineTo(size.width, 0f)
                        close()
                    }
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.09f), Color.Transparent)
                        )
                    )

                    // Top rim subtle specular highlight
                    drawLine(
                        color = Color.White.copy(alpha = 0.22f),
                        start = Offset(16.dp.toPx(), 1.dp.toPx()),
                        end = Offset(size.width - 16.dp.toPx(), 1.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Top Row: Bank Name prominently at top-left, Active Badge + NFC Waves at top-right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = card.bankName.uppercase(),
                            color = style.contentColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${card.cardTypeLabel.uppercase()}",
                            color = style.subtextColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (card.theme == WalletCardTheme.PORCELAIN_WHITE && !ArcColors.isDark) {
                                            Color(0xFFEFF6FF)
                                        } else {
                                            Color.White.copy(alpha = 0.22f)
                                        }
                                    )
                                    .border(
                                        BorderStroke(
                                            0.8.dp,
                                            if (card.theme == WalletCardTheme.PORCELAIN_WHITE && !ArcColors.isDark) {
                                                Color(0xFF2563EB)
                                            } else {
                                                Color.White.copy(alpha = 0.45f)
                                            }
                                        ),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "● Active",
                                    color = if (card.theme == WalletCardTheme.PORCELAIN_WHITE && !ArcColors.isDark) Color(0xFF2563EB) else Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        // NFC Contactless Waves
                        ContactlessWaves(tint = style.contentColor.copy(alpha = 0.85f))
                    }
                }

                // 2. Upper-Middle: EMV Chip on left + Card Balance on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EmvChip()

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "AVAILABLE BALANCE",
                            color = style.subtextColor,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = card.balanceFormatted,
                            color = style.contentColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 3. Middle: Masked Card Number
                val maskedNumber = remember(card.suffix) {
                    val s = card.suffix.trim()
                    val last4 = if (s.isNotBlank()) s.takeLast(4) else "4821"
                    "••••  ••••  ••••  $last4"
                }
                Text(
                    text = maskedNumber,
                    color = style.contentColor,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.4.sp
                )

                // 4. Expanded Details: Income & Expense (when selected)
                if (isSelected) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.28f),
                        border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "In: ",
                                color = style.subtextColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = card.incomeFormatted,
                                color = Color(0xFF34D399),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Out: ",
                                color = style.subtextColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = card.expenseFormatted,
                                color = Color(0xFFF87171),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 5. Bottom Row: Cardholder Name & Valid Thru on left, Network Logo on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column {
                            Text(
                                text = "CARDHOLDER",
                                color = style.subtextColor,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = card.cardName.uppercase(),
                                color = style.contentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "VALID THRU",
                                color = style.subtextColor,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = "12/29",
                                color = style.contentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Card Payment Network at bottom-right
                    val network = detectCardNetwork(card)
                    CardNetworkLogo(network = network, contentColor = style.contentColor)
                }
            }
        }
    }
}

/**
 * Realistic card header strip tucked inside the leather pocket (Image 0 style).
 * Shows top portion of card with bank badge, card title, mini chip, suffix, and color theme.
 */
@Composable
private fun PocketTuckedCard(
    card: WalletCardModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 8.dp, bottomEnd = 8.dp)

    val style = when (card.theme) {
        WalletCardTheme.GRAPHITE_DARK -> {
            CardThemeStyle(
                bgBrush = Brush.horizontalGradient(listOf(Color(0xFF252A36), Color(0xFF141822))),
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
            if (ArcColors.isDark) {
                CardThemeStyle(
                    bgBrush = Brush.horizontalGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A))),
                    borderColor = if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155),
                    contentColor = Color(0xFFF8FAFC),
                    subtextColor = Color(0xFF94A3B8)
                )
            } else {
                CardThemeStyle(
                    bgBrush = Brush.horizontalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF1F5F9))),
                    borderColor = if (isSelected) Color(0xFF2563EB) else Color(0xFFCBD5E1),
                    contentColor = TextDarkPrimary,
                    subtextColor = Color(0xFF64748B)
                )
            }
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
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, style.borderColor),
        shadowElevation = if (isSelected) 10.dp else 3.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(style.bgBrush)
                .drawBehind {
                    // Top rim specular highlight
                    drawLine(
                        color = Color.White.copy(alpha = 0.22f),
                        start = Offset(14.dp.toPx(), 1.dp.toPx()),
                        end = Offset(size.width - 14.dp.toPx(), 1.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
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
                        text = card.bankName.uppercase(),
                        color = style.contentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = card.cardName,
                        color = style.subtextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (card.theme == WalletCardTheme.PORCELAIN_WHITE && !ArcColors.isDark) {
                                        Color(0xFFEFF6FF)
                                    } else {
                                        Color.White.copy(alpha = 0.22f)
                                    }
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "Active",
                                color = if (card.theme == WalletCardTheme.PORCELAIN_WHITE && !ArcColors.isDark) Color(0xFF38BDF8) else Color.White,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right: EMV Chip Mini Graphic + Suffix
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 18.dp, height = 13.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFFDF79), Color(0xFFB8860B))
                                )
                            )
                            .border(BorderStroke(0.5.dp, Color(0xFF7A540D)), RoundedCornerShape(2.dp))
                    )

                    if (card.suffix.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "••${card.suffix}",
                            color = style.subtextColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
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
                iconColor = if (ArcColors.current.isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                iconBgColor = if (ArcColors.current.isDark) Color(0xFF1E293B) else Color(0xFFEFF6FF),
                title = "Scan SMS",
                subtitle = "Sync alerts",
                onClick = onScanSmsClick
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(CardBorderLight)
            )

            // 2. Budgets
            QuickActionHubItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Savings,
                iconColor = if (ArcColors.current.isDark) Color(0xFF34D399) else Color(0xFF059669),
                iconBgColor = if (ArcColors.current.isDark) Color(0xFF064E3B) else Color(0xFFECFDF5),
                title = "Budgets",
                subtitle = "Spend caps",
                onClick = onBudgetsClick
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(CardBorderLight)
            )

            // 3. Accounts
            QuickActionHubItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.AccountBalance,
                iconColor = if (ArcColors.current.isDark) Color(0xFFFBBF24) else Color(0xFFD97706),
                iconBgColor = if (ArcColors.current.isDark) Color(0xFF2B1F09) else Color(0xFFFFFBEB),
                title = "Accounts",
                subtitle = "Bank vaults",
                onClick = onAccountsClick
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(CardBorderLight)
            )

            // 4. Export
            QuickActionHubItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.FileDownload,
                iconColor = if (ArcColors.current.isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED),
                iconBgColor = if (ArcColors.current.isDark) Color(0xFF2E1A47) else Color(0xFFF5F3FF),
                title = "Export",
                subtitle = "Vault Backup",
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
    onItemClick: (HomeTransactionDisplay) -> Unit,
    onItemLongClick: (HomeTransactionDisplay) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Activity",
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
                    color = EmeraldAccent
                )
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    imageVector = Icons.Outlined.ArrowForward,
                    contentDescription = "View all transactions",
                    tint = EmeraldAccent,
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
                            .background(ArcColors.SurfaceHighlight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CreditCard,
                            contentDescription = "No transactions",
                            tint = ArcColors.TextMuted,
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
                            .background(ArcColors.SurfaceHighlight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ReceiptLong,
                            contentDescription = "No transactions",
                            tint = ArcColors.TextMuted,
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
                        shape = CircleShape,
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
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongClick(item) }
                        )
                        if (index < items.size - 1) {
                            HorizontalDivider(
                                color = CardBorderLight,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentTransactionItem(
    item: HomeTransactionDisplay,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category Icon Circle
        val isDark = ArcColors.current.isDark
        val (iconVector, iconTint, iconBg) = when (item.iconType) {
            "income" -> Triple(Icons.AutoMirrored.Rounded.TrendingUp, EmeraldAccent, EmeraldPillBg)
            "dining" -> Triple(Icons.Outlined.Restaurant, if (isDark) Color(0xFFFB923C) else Color(0xFFEA580C), if (isDark) Color(0xFF2C1608) else Color(0xFFFFF7ED))
            "shopping" -> Triple(Icons.Outlined.ShoppingBag, if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB), if (isDark) Color(0xFF1E293B) else Color(0xFFEFF6FF))
            "utilities" -> Triple(Icons.Outlined.Bolt, if (isDark) Color(0xFFF87171) else Color(0xFFDC2626), if (isDark) Color(0xFF2C1418) else Color(0xFFFEF2F2))
            else -> Triple(Icons.Outlined.CreditCard, TextDarkSecondary, ArcColors.SurfaceHighlight)
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
