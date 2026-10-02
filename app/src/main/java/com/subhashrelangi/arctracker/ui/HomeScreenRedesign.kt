package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.Expense
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==========================================
// Theme Colors matching Ui-Designs/HomePage.png
// ==========================================
private val HomeBgColor = Color(0xFF090C10)
private val CardSurface = Color(0xFF11141C)
private val CardBorder = Color(0xFF1D222E)

private val EmeraldAccent = Color(0xFF00E676)
private val EmeraldBg = Color(0xFF0C2419)
private val EmeraldBorder = Color(0xFF134C34)

private val AmberAccent = Color(0xFFF59E0B)
private val AmberBright = Color(0xFFFBBF24)
private val AmberBannerBg = Color(0xFF1B160A)
private val AmberBannerBorder = Color(0xFF382B12)
private val AmberBadgeBg = Color(0xFF2B1F09)
private val AmberBadgeBorder = Color(0xFF4A3612)

private val CoralExpense = Color(0xFFF87171)
private val CoralExpenseBg = Color(0xFF2C1317)
private val CoralExpenseBorder = Color(0xFF4A1E24)

private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF94A3B8)
private val TextTertiary = Color(0xFF64748B)

private val MiniCardBg = Color(0xFF161A24)
private val MiniCardBorder = Color(0xFF222837)

/**
 * Redesigned HomeScreen component matching Ui-Designs/HomePage.png exactly.
 */
@Composable
fun HomeScreenRedesign(
    expenses: List<Expense> = emptyList(),
    onSearchClick: () -> Unit = {},
    onAddExpenseClick: () -> Unit = {},
    onReviewClick: () -> Unit = {},
    onAccountsClick: () -> Unit = {},
    onScanSmsClick: () -> Unit = {},
    onAddCashClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
    onViewAllClick: () -> Unit = {},
    onExpenseClick: (Expense) -> Unit = {}
) {
    val scrollState = rememberScrollState()

    // Aggregate real expenses or use reference figures when list is empty
    val pendingExpenses = expenses.filter { it.isPending }
    val reviewCount = if (pendingExpenses.isNotEmpty()) pendingExpenses.size else 3

    val totalSpent = expenses.filter { !it.type.equals("Credit", ignoreCase = true) && !it.isPending }.sumOf { it.amount }
    val totalIncome = expenses.filter { it.type.equals("Credit", ignoreCase = true) && !it.isPending }.sumOf { it.amount }

    val displayBalance = if (expenses.isNotEmpty()) {
        val bal = totalIncome - totalSpent
        formatInr(bal)
    } else {
        "₹2,84,350.00"
    }

    val displayIncome = if (totalIncome > 0) formatInr(totalIncome) else "₹1,45,000"
    val displaySpent = if (totalSpent > 0) formatInr(totalSpent) else "₹58,420"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 90.dp)
        ) {
            // ----------------------------------------------------
            // 1. App Header: Logo + "ArcTracker" + Badges + Search/Add
            // ----------------------------------------------------
            HomeTopHeader(
                onSearchClick = onSearchClick,
                onAddClick = onAddExpenseClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ----------------------------------------------------
            // 2. Review Alert Banner: "3 Transactions Require Review"
            // ----------------------------------------------------
            ReviewAlertBanner(
                reviewCount = reviewCount,
                firstPending = pendingExpenses.firstOrNull(),
                onReviewAllClick = onReviewClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ----------------------------------------------------
            // 3. "TOTAL LIQUID POSITION" Card
            // ----------------------------------------------------
            TotalLiquidPositionCard(
                balance = displayBalance,
                income = displayIncome,
                spent = displaySpent,
                onAccountsClick = onAccountsClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ----------------------------------------------------
            // 4. "Monthly Spend Velocity" Card
            // ----------------------------------------------------
            MonthlySpendVelocityCard(
                spentText = displaySpent
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ----------------------------------------------------
            // 5. Quick Action 4-Grid Buttons (with circular icon containers)
            // ----------------------------------------------------
            QuickActionsGrid(
                reviewCount = reviewCount,
                onScanSmsClick = onScanSmsClick,
                onReviewClick = onReviewClick,
                onAddCashClick = onAddCashClick,
                onExportClick = onExportClick
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ----------------------------------------------------
            // 6. "Recent Activity" Live Ledger
            // ----------------------------------------------------
            RecentActivitySection(
                expenses = expenses,
                onViewAllClick = onViewAllClick,
                onExpenseClick = onExpenseClick
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ----------------------------------------------------
            // 7. Security Guarantee Pill (Bottom Notice)
            // ----------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF091710))
                        .border(BorderStroke(1.dp, Color(0xFF123B25)), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = "Shield",
                            tint = EmeraldAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ALL SMS PARSED ON-DEVICE  •  ZERO CLOUD TRACKING",
                            color = TextTertiary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ==========================================
// Section 1: Header
// ==========================================
@Composable
private fun HomeTopHeader(
    onSearchClick: () -> Unit,
    onAddClick: () -> Unit
) {
    com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader(
        modifier = Modifier.fillMaxWidth(),
        onSearchClick = onSearchClick,
        onAddClick = onAddClick
    )
}

// ==========================================
// Section 2: Review Alert Banner
// ==========================================
@Composable
private fun ReviewAlertBanner(
    reviewCount: Int,
    firstPending: Expense?,
    onReviewAllClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = AmberBannerBg,
        border = BorderStroke(1.dp, AmberBannerBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(AmberBright)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$reviewCount Transactions Require Review",
                        color = AmberBright,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.clickable(onClick = onReviewAllClick),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Review All →",
                        color = AmberBright,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Ticket Row for first pending item
            val merchantName = firstPending?.merchant ?: "Swiggy"
            val amountText = if (firstPending != null) "₹${"%,.2f".format(firstPending.amount)}" else "₹480.00"

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onReviewAllClick),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF131007),
                border = BorderStroke(1.dp, Color(0xFF2C220D))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                        contentDescription = "Receipt",
                        tint = AmberAccent,
                        modifier = Modifier.size(15.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = merchantName,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = amountText,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // "PENDING CATEGORY" Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AmberBadgeBg)
                            .border(BorderStroke(1.dp, AmberBadgeBorder), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PENDING CATEGORY",
                            color = AmberBright,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "GPay UPI",
                        color = TextTertiary,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// ==========================================
// Section 3: "TOTAL LIQUID POSITION" Card
// ==========================================
@Composable
private fun TotalLiquidPositionCard(
    balance: String,
    income: String,
    spent: String,
    onAccountsClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Wallet Icon + TOTAL LIQUID POSITION + 4 Accounts ⌵
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WalletPositionIcon(
                        modifier = Modifier.size(15.dp),
                        tint = TextSecondary
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "TOTAL LIQUID POSITION",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                // Accounts Pill Dropdown
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onAccountsClick),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF181D28),
                    border = BorderStroke(1.dp, Color(0xFF252D3E))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "4 Accounts",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Dropdown",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Balance Text
            Text(
                text = balance,
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Trend Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                    contentDescription = "Trend Up",
                    tint = EmeraldAccent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "+4.2%",
                    color = EmeraldAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "vs last month",
                    color = TextTertiary,
                    fontSize = 11.5.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Split Mini Cards: Oct Income / Oct Spent
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Income Mini Card
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MiniCardBg,
                    border = BorderStroke(1.dp, MiniCardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowDownward,
                                contentDescription = "Income",
                                tint = EmeraldAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Oct Income",
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = income,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Spent Mini Card
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MiniCardBg,
                    border = BorderStroke(1.dp, MiniCardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = "Spent",
                                tint = CoralExpense,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Oct Spent",
                                color = TextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = spent,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// Section 4: "Monthly Spend Velocity" Card
// ==========================================
@Composable
private fun MonthlySpendVelocityCard(
    spentText: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Title + 73%
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monthly Spend Velocity",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "73%",
                    color = TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Sub-row: Days left + Cap
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "12 days left in October",
                    color = TextSecondary,
                    fontSize = 11.5.sp
                )

                Text(
                    text = "of ₹80,000 cap",
                    color = TextTertiary,
                    fontSize = 11.5.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Custom High-Contrast Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF202636))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.73f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer Row: spent + safe buffer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$spentText spent",
                    color = TextSecondary,
                    fontSize = 11.sp
                )

                Text(
                    text = "₹21,580 safe buffer",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ==========================================
// Section 5: Quick Actions 4-Grid (with exact circular icon containers)
// ==========================================
@Composable
private fun QuickActionsGrid(
    reviewCount: Int,
    onScanSmsClick: () -> Unit,
    onReviewClick: () -> Unit,
    onAddCashClick: () -> Unit,
    onExportClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Tile 1: Scan SMS (Chat bubble with dots inside circular container)
        QuickActionTile(
            iconComposable = {
                ChatBubbleDotsIcon(modifier = Modifier.size(20.dp), tint = Color(0xFFCBD5E1))
            },
            title = "Scan SMS",
            subtitle = "2h ago",
            onClick = onScanSmsClick,
            modifier = Modifier.weight(1f)
        )

        // Tile 2: Review (Yellow document with checkmark, inside dark amber circle, with amber dot)
        QuickActionTile(
            iconComposable = {
                ReviewDocumentIcon(modifier = Modifier.size(20.dp), tint = AmberBright)
            },
            iconCircleBg = Color(0xFF291F0B),
            hasTopRightAmberDot = true,
            title = "Review",
            subtitle = "$reviewCount items",
            subtitleColor = AmberBright,
            onClick = onReviewClick,
            modifier = Modifier.weight(1f)
        )

        // Tile 3: Add Cash (Square with pen)
        QuickActionTile(
            iconComposable = {
                AddCashEditIcon(modifier = Modifier.size(20.dp), tint = Color(0xFFCBD5E1))
            },
            title = "Add Cash",
            subtitle = "Manual",
            onClick = onAddCashClick,
            modifier = Modifier.weight(1f)
        )

        // Tile 4: Export (Open tray with upward arrow)
        QuickActionTile(
            iconComposable = {
                ExportTrayIcon(modifier = Modifier.size(20.dp), tint = Color(0xFFCBD5E1))
            },
            title = "Export",
            subtitle = "Sheets/CSV",
            onClick = onExportClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActionTile(
    iconComposable: @Composable () -> Unit,
    iconCircleBg: Color = Color(0xFF191D28),
    hasTopRightAmberDot: Boolean = false,
    title: String,
    subtitle: String,
    subtitleColor: Color = TextTertiary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Circular Icon Container (matches crop_quick_actions.png)
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconCircleBg),
                    contentAlignment = Alignment.Center
                ) {
                    iconComposable()
                }

                if (hasTopRightAmberDot) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 1.dp, y = (-1).dp)
                            .clip(CircleShape)
                            .background(AmberBright)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==========================================
// Section 6: Recent Activity Section
// ==========================================
@Composable
private fun RecentActivitySection(
    expenses: List<Expense>,
    onViewAllClick: () -> Unit,
    onExpenseClick: (Expense) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Activity",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(8.dp))

                // "Live Ledger" Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF181C26))
                        .border(BorderStroke(1.dp, Color(0xFF262D3D)), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Live Ledger",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Text(
                text = "View All",
                color = TextSecondary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onViewAllClick)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // If user has expenses in DB, show top recent ones, else show exact design sample rows
        if (expenses.isNotEmpty()) {
            expenses.take(5).forEach { expense ->
                RecentTransactionCard(
                    expense = expense,
                    onClick = { onExpenseClick(expense) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
            // Exact Sample Ledger rows from HomePage.png design
            SampleTransactionRow(
                icon = Icons.Outlined.Restaurant,
                iconTint = CoralExpense,
                iconBg = CoralExpenseBg,
                iconBorder = CoralExpenseBorder,
                hasAmberDot = true,
                title = "Swiggy",
                badgeText = "Unverified",
                subtitle = "Today, 1:42 PM  •  HDFC  ••  4...",
                amount = "-₹480.00",
                amountColor = CoralExpense,
                categoryText = "Tag Category",
                categoryColor = AmberBright
            )
            Spacer(modifier = Modifier.height(8.dp))

            SampleTransactionRow(
                icon = Icons.Outlined.BusinessCenter,
                iconTint = EmeraldAccent,
                iconBg = EmeraldBg,
                iconBorder = EmeraldBorder,
                hasCheck = true,
                title = "Acme Corp Tech",
                subtitle = "Yesterday  •  Salary  •...",
                amount = "+₹1,45,000.00",
                amountColor = EmeraldAccent,
                categoryText = "NEFT Inflow",
                categoryColor = TextTertiary
            )
            Spacer(modifier = Modifier.height(8.dp))

            SampleTransactionRow(
                icon = Icons.Outlined.ShoppingBag,
                iconTint = Color(0xFFCBD5E1),
                iconBg = Color(0xFF181C26),
                iconBorder = Color(0xFF242A38),
                title = "Amazon India",
                subtitle = "Yesterday  •  Shopping  •  H...",
                amount = "-₹3,249.00",
                amountColor = Color(0xFFF1F5F9),
                categoryText = "UPI Card Ingest",
                categoryColor = TextTertiary
            )
            Spacer(modifier = Modifier.height(8.dp))

            SampleTransactionRow(
                icon = Icons.Outlined.LocalCafe,
                iconTint = Color(0xFFCBD5E1),
                iconBg = Color(0xFF181C26),
                iconBorder = Color(0xFF242A38),
                title = "Blue Tokai Coffee",
                subtitle = "18 Oct  •  GPay  •  Axis  ••  3...",
                amount = "-₹260.00",
                amountColor = Color(0xFFF1F5F9),
                categoryText = "Coffee / Food",
                categoryColor = TextTertiary
            )
            Spacer(modifier = Modifier.height(8.dp))

            SampleTransactionRow(
                icon = Icons.Outlined.Router,
                iconTint = Color(0xFFCBD5E1),
                iconBg = Color(0xFF181C26),
                iconBorder = Color(0xFF242A38),
                title = "Airtel Broadband",
                subtitle = "17 Oct  •  Auto-debit Mand...",
                amount = "-₹1,179.00",
                amountColor = Color(0xFFF1F5F9),
                categoryText = "Bills & Utility",
                categoryColor = TextTertiary
            )
        }
    }
}

@Composable
private fun RecentTransactionCard(
    expense: Expense,
    onClick: () -> Unit
) {
    val isCredit = expense.type.equals("Credit", ignoreCase = true)
    val isDebit = !isCredit
    val amountPrefix = if (isDebit) "-₹" else "+₹"
    val amountColor = if (isDebit) {
        if (expense.isPending) CoralExpense else Color(0xFFF1F5F9)
    } else {
        EmeraldAccent
    }

    val tagText = expense.tag ?: ""
    val icon = when {
        tagText.contains("Food", ignoreCase = true) -> Icons.Outlined.Restaurant
        tagText.contains("Salary", ignoreCase = true) || isCredit -> Icons.Outlined.BusinessCenter
        tagText.contains("Shopping", ignoreCase = true) -> Icons.Outlined.ShoppingBag
        tagText.contains("Coffee", ignoreCase = true) -> Icons.Outlined.LocalCafe
        tagText.contains("Bill", ignoreCase = true) -> Icons.Outlined.Router
        else -> Icons.AutoMirrored.Outlined.ReceiptLong
    }

    val iconBg = if (isCredit) EmeraldBg else if (expense.isPending) CoralExpenseBg else Color(0xFF181C26)
    val iconTint = if (isCredit) EmeraldAccent else if (expense.isPending) CoralExpense else Color(0xFFCBD5E1)
    val iconBorder = if (isCredit) EmeraldBorder else if (expense.isPending) CoralExpenseBorder else Color(0xFF242A38)

    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val dateText = dateFormat.format(Date(expense.dateMillis))
    val accountMaskText = expense.accountSuffix?.let { "•• $it" } ?: "UPI"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular Icon with optional overlapping amber dot (matches crop_recent.png)
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconBg)
                        .border(BorderStroke(1.dp, iconBorder), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(19.dp)
                    )
                }

                if (expense.isPending) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 1.dp, y = (-1).dp)
                            .clip(CircleShape)
                            .background(AmberBright)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Merchant & Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = expense.merchant,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (expense.isPending) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AmberBadgeBg)
                                .border(BorderStroke(1.dp, AmberBadgeBorder), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "Unverified",
                                color = AmberBright,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (isCredit) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Verified",
                            tint = EmeraldAccent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "$dateText  •  $accountMaskText",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount & Tag
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$amountPrefix${"%,.2f".format(expense.amount)}",
                    color = amountColor,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(2.dp))

                if (expense.isPending) {
                    Text(
                        text = "Tag Category",
                        color = AmberBright,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = if (tagText.isNotBlank()) tagText else "Ledger",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SampleTransactionRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    iconBorder: Color,
    hasAmberDot: Boolean = false,
    hasCheck: Boolean = false,
    title: String,
    badgeText: String? = null,
    subtitle: String,
    amount: String,
    amountColor: Color,
    categoryText: String,
    categoryColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular Icon with optional top-right amber dot (matches crop_recent.png)
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconBg)
                        .border(BorderStroke(1.dp, iconBorder), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(19.dp)
                    )
                }

                if (hasAmberDot) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 1.dp, y = (-1).dp)
                            .clip(CircleShape)
                            .background(AmberBright)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Merchant & Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (badgeText != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AmberBadgeBg)
                                .border(BorderStroke(1.dp, AmberBadgeBorder), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = badgeText,
                                color = AmberBright,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (hasCheck) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Verified",
                            tint = EmeraldAccent,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount & Tag
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = amount,
                    color = amountColor,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = categoryText,
                    color = categoryColor,
                    fontSize = 11.sp,
                    fontWeight = if (categoryColor == AmberBright) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

private fun formatInr(amount: Double): String {
    return "₹${"%,.2f".format(amount)}"
}

@Preview(showBackground = true, backgroundColor = 0xFF090C10)
@Composable
fun HomeScreenRedesignPreview() {
    MaterialTheme {
        HomeScreenRedesign()
    }
}
