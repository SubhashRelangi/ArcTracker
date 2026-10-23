package com.subhashrelangi.arctracker.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.R
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
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
 * 4. If user completed setup but skipped scanning (0 transactions):
 *    show clean empty state caption: "No transactions recorded yet. ArcTracker will automatically capture banking alerts and SMS notifications locally, or tap Scan SMS to import history."
 * 5. Review Component: only show if reviewCount > 0; if reviewCount == 0, hide completely.
 * 6. Quick Actions: Scan SMS, Add Cash, Export, Audit.
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

    // Calculate aggregated amounts
    val totalIncome = expenses.filter { it.type.equals("Credit", ignoreCase = true) && !it.isPending }.sumOf { it.amount }
    val totalSpent = expenses.filter { !it.type.equals("Credit", ignoreCase = true) && !it.isPending }.sumOf { it.amount }

    // Final balance values: Demo Mode vs Real Data vs Empty State
    val displayBalance: String
    val displayIncome: String
    val displaySpent: String
    val displayReviewCount: Int
    val displayReviewSubtitle: String

    if (isDemoMode) {
        displayBalance = "₹1,48,250.00"
        displayIncome = "+₹85,000"
        displaySpent = "-₹36,750"
        displayReviewCount = 3
        displayReviewSubtitle = "Swiggy, Amazon & 1 other"
    } else if (hasTransactions) {
        val net = totalIncome - totalSpent
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
        displayBalance = "₹0.00"
        displayIncome = "+₹0"
        displaySpent = "-₹0"
        displayReviewCount = 0
        displayReviewSubtitle = ""
    }

    // Determine recent activity items
    val recentItems: List<HomeTransactionDisplay> = remember(expenses, isDemoMode, hasTransactions) {
        if (isDemoMode) {
            listOf(
                HomeTransactionDisplay(
                    id = 1,
                    title = "Salary credit",
                    subtitle = "Income • 09:12 • HDFC ••4521",
                    amountText = "+₹48,500",
                    isCredit = true,
                    iconType = "income"
                ),
                HomeTransactionDisplay(
                    id = 2,
                    title = "Blue Tokai Coffee",
                    subtitle = "Dining • Yesterday • HDFC ••4521",
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
        } else if (hasTransactions) {
            val nonPending = expenses.filter { !it.isPending }.take(6)
            val list = if (nonPending.isNotEmpty()) nonPending else expenses.take(6)
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
            // 3. Dark Hero Block:
            //    - Overlapping Stacked Cards Deck (HDFC, ICICI, UPI)
            //    - Total Liquid Balance + Trend Pill
            //    - Split Mini-Cards for October In / October Out
            // ----------------------------------------------------
            HomeDarkHeroCard(
                balance = displayBalance,
                income = displayIncome,
                spent = displaySpent,
                knownAccounts = knownAccounts,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Clean Brand Title (No circle logo, No 100% local badge)
        Text(
            text = "ArcTracker",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextDarkPrimary,
            letterSpacing = (-0.5).sp
        )

        // Right: Search & Profile Circle Action Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(CardSurfaceWhite)
                    .border(BorderStroke(1.dp, CardBorderLight), CircleShape)
                    .clickable(onClick = onSearchClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = TextDarkPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Profile / Accounts Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(CardSurfaceWhite)
                    .border(BorderStroke(1.dp, CardBorderLight), CircleShape)
                    .clickable(onClick = onProfileClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = "Profile & Accounts",
                    tint = TextDarkPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
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
// 3. Dark Hero Card: Stacked Deck + Total Liquid Balance + Split In/Out
// ==========================================
@Composable
private fun HomeDarkHeroCard(
    balance: String,
    income: String,
    spent: String,
    knownAccounts: List<KnownFinancialAccount>,
    isDemoMode: Boolean,
    onAccountsClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = SlateHeroBg,
        border = BorderStroke(1.dp, SlateHeroBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // ----------------------------------------------------
            // A. Overlapping Stack of Wallet Cards (Deck Preview)
            // ----------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAccountsClick
                    )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Back Card (HDFC Regalia)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .align(Alignment.CenterHorizontally),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0F172A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("H", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "HDFC Regalia",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "₹78,400",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Middle Card (ICICI Advantage)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.96f)
                            .align(Alignment.CenterHorizontally),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF3730A3),
                        border = BorderStroke(1.dp, Color(0xFF4F46E5))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "VISA",
                                    color = Color(0xFFC7D2FE),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ICICI Advantage",
                                    color = Color(0xFFE0E7FF),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "•• 1002",
                                    color = Color(0xFFA5B4FC),
                                    fontSize = 10.5.sp
                                )
                            }
                            Text(
                                text = "₹42,350",
                                color = Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Front Card (UPI & Digital Wallets)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = CardSurfaceWhite,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEFF6FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountBalanceWallet,
                                        contentDescription = "Wallet",
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "UPI & Digital Wallets",
                                        color = TextDarkPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.dp)
                                                .clip(CircleShape)
                                                .background(EmeraldAccent)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Active",
                                            color = EmeraldDarkText,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "₹27,500",
                                color = TextDarkPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ----------------------------------------------------
            // B. TOTAL LIQUID BALANCE Header Row
            // ----------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = "Liquid Balance",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TOTAL LIQUID BALANCE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.8.sp
                    )
                }

                val accountsCount = if (isDemoMode) 4 else maxOf(1, knownAccounts.size)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .clickable(onClick = onAccountsClick)
                ) {
                    Text(
                        text = "$accountsCount Accounts •",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Balance Amount + Trend Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = balance,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF064E3B))
                        .border(BorderStroke(1.dp, Color(0xFF047857)), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "↗ +4.2%",
                        color = Color(0xFF34D399),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ----------------------------------------------------
            // C. Split Mini-Cards (October In / October Out)
            // ----------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Left: Month In
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF161F30),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
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
                            Text(
                                text = "↗",
                                color = Color(0xFF34D399),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
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
                    color = Color(0xFF161F30),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
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
                            Text(
                                text = "↘",
                                color = CoralExpense,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
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
                text = "Recent Activity",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextDarkPrimary
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
            if (items.isEmpty() && !isDemoMode) {
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
