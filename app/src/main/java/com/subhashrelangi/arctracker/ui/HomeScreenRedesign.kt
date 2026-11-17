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
// 3. Dark Hero Card: Authentic Physical Stacked Card Deck (matching Image 1 mockup)
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
    RealisticStackedCardDeck(
        cards = cards,
        selectedCardId = selectedCardId,
        onCardSelect = onCardSelect,
        heroTitle = heroTitle,
        balance = balance,
        income = income,
        spent = spent,
        onAccountsClick = onAccountsClick
    )
}

/**
 * Ultra-sleek Realistic Stacked Card Deck directly inspired by reference Image 1.
 *
 * Visual & Interactive Architecture:
 * - Cards are stacked on top of each other in a tactile, physical card deck.
 * - The active card is ALWAYS in the FRONT (first in visibility, full face, highest zIndex).
 * - Background cards peek out neatly above it with a stepped offset (44dp each),
 *   clearly displaying their Bank branding, masked card number, and available balance.
 * - Tapping ANY background card smoothly animates it to the FRONT of the stack,
 *   while the previous front card smoothly slides back into the deck!
 * - Clean top header bar with "WALLET CARDS (N)" and a "Reset to All" pill when a card is selected.
 * - Zero bottom flaps or overlapping bars: the front card is 100% visible and unobstructed.
 */
@Composable
private fun RealisticStackedCardDeck(
    cards: List<WalletCardModel>,
    selectedCardId: String?,
    onCardSelect: (String?) -> Unit,
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
        color = if (ArcColors.isDark) ArcColors.CardBackground else Color(0xFF0C101A),
        border = BorderStroke(1.dp, if (ArcColors.isDark) ArcColors.Border else Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            // 1. Header Row: Hero Title + "Reset to All" / "All Accounts"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (selectedCardId == null) Icons.Outlined.AccountBalanceWallet else Icons.Outlined.CreditCard,
                        contentDescription = "Wallet Cards",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = heroTitle,
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (selectedCardId != null) {
                    Surface(
                        onClick = { onCardSelect(null) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (ArcColors.isDark) ArcColors.SurfaceHighlight else Color(0xFF1E293B),
                        border = BorderStroke(1.dp, if (ArcColors.isDark) ArcColors.BorderSubtle else Color(0xFF334155))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Reset to All",
                                color = Color(0xFFFDE68A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Reset Selection",
                                tint = Color(0xFFFDE68A),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                } else if (cards.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (ArcColors.isDark) ArcColors.SurfaceHighlight else Color(0xFF1E293B))
                            .padding(horizontal = 9.dp, vertical = 4.5.dp)
                    ) {
                        Text(
                            text = "All Accounts (${cards.size})",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Balance Amount (Split Integer & Decimal) + Trend / Active Badge
            val (intPart, decPart) = splitInrAmount(balance)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = intPart,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = decPart,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .padding(bottom = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedCardId != null) Color(0xFF1E3A8A) else Color(0xFF064E3B))
                        .border(
                            BorderStroke(
                                1.dp,
                                if (selectedCardId != null) Color(0xFF2563EB) else Color(0xFF047857)
                            ),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = if (selectedCardId != null) "● Card Focus" else "↗ +4.2%",
                        color = if (selectedCardId != null) Color(0xFF60A5FA) else Color(0xFF34D399),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Split Mini-Cards (Month In / Month Out - Total Expenses)
            val monthLabel = remember {
                val sdf = SimpleDateFormat("MMMM", Locale.US)
                sdf.format(Date())
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Left: Month In
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = if (ArcColors.isDark) ArcColors.SurfaceElevated else Color(0xFF131A29),
                    border = BorderStroke(1.dp, if (ArcColors.isDark) ArcColors.BorderSubtle else Color(0xFF1E293B))
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
                                text = "$monthLabel In",
                                color = if (ArcColors.isDark) ArcColors.TextSecondary else Color(0xFF94A3B8),
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

                // Right: Month Out (Total Expenses)
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = if (ArcColors.isDark) ArcColors.SurfaceElevated else Color(0xFF131A29),
                    border = BorderStroke(1.dp, if (ArcColors.isDark) ArcColors.BorderSubtle else Color(0xFF1E293B))
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
                                text = "$monthLabel Out",
                                color = if (ArcColors.isDark) ArcColors.TextSecondary else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Box(
                                modifier = Modifier
                                    .size(19.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF450A0A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = "Expense",
                                    tint = CoralExpense,
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

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Card Deck
            if (cards.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAccountsClick),
                    shape = RoundedCornerShape(18.dp),
                    color = if (ArcColors.isDark) ArcColors.SurfaceHighlight else Color(0xFF131B2A),
                    border = BorderStroke(1.dp, if (ArcColors.isDark) ArcColors.BorderSubtle else Color(0xFF1E293B))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
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
                                fontSize = 13.5.sp,
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
                // Determine active card (front of the deck)
                val activeCard = cards.find { it.id == selectedCardId } ?: cards.first()
                val otherCards = cards.filter { it.id != activeCard.id }
                // The deck stack from back to front:
                // otherCards reversed so first in list is right behind activeCard, and last in list is at back
                val orderedCards = remember(cards, activeCard.id) {
                    otherCards.reversed() + listOf(activeCard)
                }

                val cardStep = if (cards.size > 3) 40.dp else 44.dp
                val deckHeight = ((orderedCards.size - 1) * cardStep.value).dp + 218.dp

                val animatedDeckHeight by animateDpAsState(
                    targetValue = deckHeight,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                    label = "deckHeight"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(animatedDeckHeight)
                ) {
                    orderedCards.forEachIndexed { index, card ->
                        key(card.id) {
                            val isFront = card.id == activeCard.id
                            val targetY = (index * cardStep.value).dp
                            val targetScale = if (isFront) 1.0f else (0.93f + (index * 0.025f).coerceAtMost(0.06f))
                            val targetZ = if (isFront) 50f else (index + 1).toFloat()

                            val animatedY by animateDpAsState(
                                targetValue = targetY,
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                                label = "cardY_${card.id}"
                            )
                            val animatedScale by animateFloatAsState(
                                targetValue = targetScale,
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                                label = "cardScale_${card.id}"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = animatedY)
                                    .graphicsLayer {
                                        scaleX = animatedScale
                                        scaleY = animatedScale
                                    }
                                    .zIndex(targetZ)
                            ) {
                                FullRealisticCreditCard(
                                    card = card,
                                    isSelected = isFront && selectedCardId != null,
                                    isFront = isFront,
                                    onClick = {
                                        if (isFront) {
                                            // Clicking front card toggles selection / resets to all
                                            if (selectedCardId != null) {
                                                onCardSelect(null)
                                            } else {
                                                onCardSelect(card.id)
                                            }
                                        } else {
                                            // Clicking any background card brings it to the front!
                                            onCardSelect(card.id)
                                        }
                                    }
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
 * Authentic full-sized physical credit/debit card component.
 * Aspect ratio: standard ISO/IEC 7810 ID-1 (85.60 mm × 53.98 mm ≈ 1.586:1).
 * Features:
 * - Bank name prominently at top-left
 * - Top tab shows masked suffix and balance when in background stack
 * - EMV chip visual with etched contact circuits
 * - NFC / Contactless payment indicator
 * - Masked card number (•••• •••• •••• XXXX) in embossed monospace styling
 * - Cardholder name & Valid Thru expiry date (12/29)
 * - Card type / payment network label (VISA/MASTERCARD/RUPAY/UPI)
 * - Premium gradient backgrounds per theme with metallic sheen and glossy reflection
 * - Expanded details (income & expense) when front card
 */
@Composable
private fun FullRealisticCreditCard(
    card: WalletCardModel,
    isSelected: Boolean,
    isFront: Boolean = true,
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
        shadowElevation = if (isSelected) 14.dp else if (isFront) 6.dp else 2.dp
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
                // 1. Top Row: Bank Name on left, Peeking info (suffix + balance) when background, or Active badge + NFC Waves when front
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.1.sp
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
                        if (!isFront) {
                            // Peeking tab details: suffix & balance right in the visible top strip!
                            val last4 = if (card.suffix.isNotBlank()) card.suffix.trim().takeLast(4) else "4821"
                            Text(
                                text = "•••• $last4",
                                color = style.subtextColor,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = card.balanceFormatted,
                                color = style.contentColor,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else if (isSelected) {
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

                // 4. Expanded Details: Income & Expense (when front card)
                if (isFront) {
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
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = card.incomeFormatted,
                                color = Color(0xFF34D399),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Out: ",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 10.sp,
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
