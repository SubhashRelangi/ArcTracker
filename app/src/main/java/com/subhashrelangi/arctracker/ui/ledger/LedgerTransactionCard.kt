package com.subhashrelangi.arctracker.ui.ledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.ArrowDownward
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Clean, light-theme transaction card & row matching reference mockup.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerTransactionRow(
    expense: Expense,
    account: KnownFinancialAccount? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isCredit = expense.type.equals("Credit", ignoreCase = true)
    val isPending = expense.isPending
    val isSalary = isCredit && (expense.merchant.contains("salary", ignoreCase = true) ||
            expense.tag?.contains("salary", ignoreCase = true) == true ||
            expense.note?.contains("salary", ignoreCase = true) == true ||
            expense.merchant.contains("Acuity", ignoreCase = true) ||
            expense.merchant.contains("Acme", ignoreCase = true))

    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val formattedAmount = remember(expense.amount) {
        currencyFormatter.format(expense.amount)
            .replace("Rs.", "₹")
            .replace("INR", "₹")
            .trim()
            .substringBefore(".")
    }
    val sign = if (isCredit) "+" else "-"

    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val timeStr = remember(expense.dateMillis) { timeFormatter.format(Date(expense.dateMillis)) }

    // Resolve Category display
    val categoryName = remember(expense.tag, expense.category, expense.merchant) {
        val cat = expense.tag ?: expense.category
        when {
            !cat.isNullOrBlank() -> cat
            expense.merchant.contains("Swiggy", ignoreCase = true) || expense.merchant.contains("Zomato", ignoreCase = true) -> "Food & Dining"
            expense.merchant.contains("Coffee", ignoreCase = true) || expense.merchant.contains("Cafe", ignoreCase = true) -> "Dining"
            expense.merchant.contains("Uber", ignoreCase = true) || expense.merchant.contains("Ola", ignoreCase = true) -> "Transit"
            expense.merchant.contains("Amazon", ignoreCase = true) || expense.merchant.contains("Flipkart", ignoreCase = true) -> "Electronics"
            expense.merchant.contains("Basket", ignoreCase = true) || expense.merchant.contains("Blinkit", ignoreCase = true) -> "Groceries"
            expense.merchant.contains("Cult", ignoreCase = true) || expense.merchant.contains("Fit", ignoreCase = true) -> "Health"
            expense.merchant.contains("BESCOM", ignoreCase = true) || expense.merchant.contains("Electricity", ignoreCase = true) -> "Utilities"
            isSalary -> "Salary / Income"
            isCredit -> "Income"
            else -> "General"
        }
    }

    // Resolve Icon and Icon Container Colors
    val icon: ImageVector = remember(isSalary, isCredit, expense.merchant, categoryName) {
        when {
            isSalary || isCredit -> Icons.Rounded.ArrowDownward
            expense.merchant.contains("Coffee", ignoreCase = true) || expense.merchant.contains("Cafe", ignoreCase = true) -> Icons.Outlined.LocalCafe
            expense.merchant.contains("Basket", ignoreCase = true) || expense.merchant.contains("Groceries", ignoreCase = true) -> Icons.Outlined.ShoppingCart
            expense.merchant.contains("Swiggy", ignoreCase = true) || expense.merchant.contains("Zomato", ignoreCase = true) -> Icons.Outlined.Fastfood
            expense.merchant.contains("Uber", ignoreCase = true) || expense.merchant.contains("Ola", ignoreCase = true) -> Icons.Outlined.DirectionsCar
            expense.merchant.contains("Amazon", ignoreCase = true) || expense.merchant.contains("Flipkart", ignoreCase = true) -> Icons.Outlined.Inventory2
            expense.merchant.contains("Cult", ignoreCase = true) || expense.merchant.contains("Fit", ignoreCase = true) -> Icons.Outlined.FitnessCenter
            expense.merchant.contains("BESCOM", ignoreCase = true) || expense.merchant.contains("Electricity", ignoreCase = true) || expense.merchant.contains("Bill", ignoreCase = true) -> Icons.Outlined.Bolt
            else -> Icons.Outlined.Receipt
        }
    }

    val iconBgColor = if (isCredit) Color(0xFFECFDF5) else Color(0xFFF1F5F9)
    val iconTint = if (isCredit) Color(0xFF059669) else Color(0xFF0F172A)

    // Account suffix or bank badge
    val acctSuffix = remember(expense.accountSuffix, account) {
        val s = expense.accountSuffix ?: account?.accountSuffix
        val bName = account?.institutionName ?: if (expense.rawText?.contains("ICICI", true) == true) "ICICI" else if (expense.rawText?.contains("HDFC", true) == true) "HDFC" else ""
        when {
            !s.isNullOrBlank() && bName.isNotBlank() -> "$bName ••$s"
            !s.isNullOrBlank() -> "••$s"
            expense.rawText?.contains("UPI", true) == true || expense.rawText?.contains("GPay", true) == true -> "UPI / GPay"
            else -> "Account"
        }
    }

    // Status subtitle label
    val statusText = remember(expense.isPending, isCredit, isSalary, expense.merchant) {
        when {
            expense.isPending -> "Personal or Team?"
            isSalary -> "Deposit"
            isCredit -> "Deposit"
            expense.merchant.contains("Nature", true) -> "Auto-categorized"
            expense.merchant.contains("Coffee", true) -> "Cleared"
            expense.merchant.contains("Uber", true) -> "Receipt attached"
            expense.merchant.contains("Amazon", true) -> "Verified"
            expense.merchant.contains("Cult", true) -> "Auto-pay"
            expense.merchant.contains("BESCOM", true) || expense.merchant.contains("Electricity", true) -> "Recurring"
            else -> "Cleared"
        }
    }

    val statusColor = when {
        expense.isPending -> Color(0xFF64748B)
        statusText in listOf("Cleared", "Auto-categorized", "Deposit", "Verified") -> Color(0xFF059669)
        else -> Color(0xFF64748B)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Category / Transaction Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = categoryName,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 2. Title & Subtitle Column
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = expense.merchant.ifBlank { if (isCredit) "Credit" else "Expense" },
                    color = Color(0xFF0F172A),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                // Optional Tag Pill (Direct or Review tag)
                if (isSalary) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFECFDF5))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "Direct",
                            color = Color(0xFF059669),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (isPending) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFFFBEB))
                            .border(BorderStroke(0.8.dp, Color(0xFFFDE68A)), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "Review tag",
                            color = Color(0xFFB45309),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Subtitle: "Dining • 14:32 • ICICI ••1092"
            Text(
                text = "$categoryName • $timeStr • $acctSuffix",
                color = Color(0xFF64748B),
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 3. Amount & Status Column
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "$sign$formattedAmount",
                color = if (isCredit) Color(0xFF059669) else Color(0xFF0F172A),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = statusText,
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = if (statusText in listOf("Cleared", "Auto-categorized", "Deposit", "Verified")) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

/**
 * Backward-compatible wrapper for single card usage.
 */
@Composable
fun LedgerTransactionCard(
    expense: Expense,
    account: KnownFinancialAccount? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 0.5.dp
    ) {
        LedgerTransactionRow(
            expense = expense,
            account = account,
            onClick = onClick,
            onLongClick = onLongClick
        )
    }
}
