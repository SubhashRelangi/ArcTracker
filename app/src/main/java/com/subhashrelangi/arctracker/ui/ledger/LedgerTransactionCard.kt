package com.subhashrelangi.arctracker.ui.ledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.CategoryVisuals
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Production-quality, responsive financial transaction card matching Ui-Designs/Ledgerpage.png.
 * Guarantees zero text overlap, responsive merchant width, stable tabular amount column,
 * and clear visual distinction of review and unresolved states.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerTransactionCard(
    expense: Expense,
    account: KnownFinancialAccount? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isCredit = expense.type.equals("Credit", ignoreCase = true)
    val isUnresolved = expense.accountId.isNullOrBlank() && expense.accountSuffix.isNullOrBlank() && !isCredit && expense.source != "MANUAL"
    val isSalary = isCredit && (expense.merchant.contains("salary", ignoreCase = true) ||
            expense.tag?.contains("salary", ignoreCase = true) == true ||
            expense.note?.contains("salary", ignoreCase = true) == true ||
            expense.merchant.contains("Acme", ignoreCase = true))

    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val formattedAmount = remember(expense.amount) {
        currencyFormatter.format(expense.amount)
            .replace("Rs.", "₹")
            .replace("INR", "₹")
            .trim()
    }
    val sign = if (isCredit) "+" else "-"

    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.US) }
    val timeStr = remember(expense.dateMillis) { timeFormatter.format(Date(expense.dateMillis)) }

    // Resolve Category display
    val categoryName = remember(expense.tag, expense.category, expense.merchant) {
        val cat = expense.tag ?: expense.category
        when {
            !cat.isNullOrBlank() -> cat
            expense.merchant.contains("Swiggy", ignoreCase = true) || expense.merchant.contains("Zomato", ignoreCase = true) -> "Food & Dining"
            expense.merchant.contains("Uber", ignoreCase = true) || expense.merchant.contains("Ola", ignoreCase = true) -> "Transport"
            expense.merchant.contains("Amazon", ignoreCase = true) || expense.merchant.contains("Flipkart", ignoreCase = true) -> "Shopping"
            expense.merchant.contains("Coffee", ignoreCase = true) || expense.merchant.contains("Starbucks", ignoreCase = true) -> "Food & Dining"
            isCredit -> "Income"
            else -> "General"
        }
    }

    // Resolve Icon and Icon Container Colors
    val icon: ImageVector = remember(isUnresolved, isSalary, expense.merchant, categoryName) {
        when {
            isUnresolved -> Icons.Default.Warning
            isSalary -> Icons.Default.AccountBalance
            expense.merchant.contains("Coffee", ignoreCase = true) || expense.merchant.contains("Cafe", ignoreCase = true) -> Icons.Default.LocalCafe
            expense.merchant.contains("Swiggy", ignoreCase = true) || expense.merchant.contains("Zomato", ignoreCase = true) || categoryName.contains("Food", ignoreCase = true) -> Icons.Default.Restaurant
            expense.merchant.contains("Uber", ignoreCase = true) || expense.merchant.contains("Ola", ignoreCase = true) || categoryName.contains("Transport", ignoreCase = true) -> Icons.Default.DirectionsCar
            expense.merchant.contains("Amazon", ignoreCase = true) || expense.merchant.contains("Flipkart", ignoreCase = true) || categoryName.contains("Shopping", ignoreCase = true) -> Icons.Default.ShoppingBag
            else -> CategoryVisuals.getIcon(categoryName)
        }
    }

    val iconBgColor = when {
        isUnresolved -> LedgerColors.UnresolvedBg
        isCredit -> LedgerColors.CreditBg
        else -> Color(0xFF1B202B)
    }

    val iconTintColor = when {
        isUnresolved -> LedgerColors.Unresolved
        isCredit -> LedgerColors.Credit
        expense.isPending -> LedgerColors.WarningAmber
        else -> Color(0xFFCBD5E1)
    }

    // Resolve Account / Metadata string
    val accountPart = remember(account, expense.accountSuffix, isUnresolved) {
        when {
            isUnresolved -> "Unlinked Bank"
            account != null -> "${account.institutionName ?: "Bank"} ••••${account.accountSuffix}"
            !expense.accountSuffix.isNullOrBlank() -> {
                val clean = expense.accountSuffix?.trim() ?: ""
                val prefix = if (expense.merchant.contains("HDFC", true) || expense.rawText?.contains("HDFC", true) == true) "HDFC"
                else if (expense.rawText?.contains("Axis", true) == true) "Axis"
                else if (expense.rawText?.contains("SBI", true) == true) "SBI"
                else if (expense.rawText?.contains("ICICI", true) == true) "ICICI"
                else "Bank"
                "$prefix ••••$clean"
            }
            expense.merchant.contains("HDFC", true) -> "HDFC ••••"
            expense.merchant.contains("Axis", true) -> "Axis ••••"
            else -> null
        }
    }

    val channelPart = remember(expense.source, expense.rawText, isSalary) {
        val raw = expense.rawText ?: ""
        when {
            isSalary -> "NEFT"
            raw.contains("GPay", true) || raw.contains("Google Pay", true) -> "GPay"
            raw.contains("Paytm", true) -> "Paytm Wallet"
            raw.contains("PhonePe", true) -> "PhonePe"
            raw.contains("UPI", true) -> "UPI"
            expense.source.contains("SMS", true) -> "SMS"
            expense.source.contains("NOTIFICATION", true) -> "Notification"
            else -> null
        }
    }

    val cardBorder = when {
        isUnresolved -> BorderStroke(1.dp, LedgerColors.UnresolvedBorder)
        expense.isPending -> BorderStroke(1.dp, LedgerColors.WarningAmberBorder)
        else -> BorderStroke(1.dp, LedgerColors.Border)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(LedgerShapes.Card)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = LedgerShapes.Card,
        color = LedgerColors.CardBackground,
        border = cardBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Icon Container with Indicator Dot
            Box(
                modifier = Modifier.size(44.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTintColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Top-right status dot on the icon circle (e.g. Swiggy review dot, UPI warning dot)
                if (expense.isPending) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(LedgerColors.WarningAmberDot)
                            .border(1.5.dp, LedgerColors.CardBackground, CircleShape)
                    )
                } else if (isUnresolved) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(LedgerColors.Unresolved)
                            .border(1.5.dp, LedgerColors.CardBackground, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. Middle Column: Merchant & Metadata
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Top line: Merchant name + Status badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = expense.merchant.ifEmpty { "Unknown Merchant" },
                        color = LedgerColors.TextPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Optional badge
                    if (expense.isPending) {
                        Spacer(modifier = Modifier.width(6.dp))
                        LedgerStatusChip(type = LedgerBadgeType.REVIEW)
                    } else if (isUnresolved) {
                        Spacer(modifier = Modifier.width(6.dp))
                        LedgerStatusChip(type = LedgerBadgeType.UNRESOLVED)
                    } else if (isSalary) {
                        Spacer(modifier = Modifier.width(6.dp))
                        LedgerStatusChip(type = LedgerBadgeType.SALARY)
                    } else if (expense.source.contains("SMS", true)) {
                        Spacer(modifier = Modifier.width(6.dp))
                        LedgerStatusChip(type = LedgerBadgeType.SMS)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom line: Metadata (Time • Bank • Channel)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = timeStr,
                        color = LedgerColors.TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1
                    )

                    if (accountPart != null) {
                        Text(
                            text = " • ",
                            color = LedgerColors.TextMuted,
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = accountPart,
                            color = if (isUnresolved) LedgerColors.Unresolved else LedgerColors.TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = if (isUnresolved) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (channelPart != null) {
                        Text(
                            text = " • ",
                            color = LedgerColors.TextMuted,
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = channelPart,
                            color = LedgerColors.TextSecondary,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 3. Right Column: Tabular Amount + Category / Status Label
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(min = 90.dp)
            ) {
                // Top line: Amount
                Text(
                    text = "$sign$formattedAmount",
                    color = if (isCredit) LedgerColors.Credit else LedgerColors.Debit,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    textAlign = TextAlign.End
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom line: Category or Status Label
                when {
                    isUnresolved -> {
                        Text(
                            text = "Action Req.",
                            color = LedgerColors.Unresolved,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                    }
                    isSalary -> {
                        Text(
                            text = "Credited",
                            color = LedgerColors.Credit,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                    }
                    else -> {
                        Text(
                            text = categoryName,
                            color = LedgerColors.TextMutedLight,
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
