package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.KnownFinancialAccountRepository
import com.subhashrelangi.arctracker.data.TransactionCategories
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TransactionsScreen(
    expenses: List<Expense>,
    onExpenseClick: (Expense) -> Unit = {},
    onExpenseLongClick: (Expense) -> Unit = {},
    selectedMonth: String = "",
    availableMonths: List<MonthOption> = emptyList(),
    onMonthChange: (String) -> Unit = {}
) {
    com.subhashrelangi.arctracker.ui.ledger.LedgerPage(
        expenses = expenses,
        onExpenseClick = onExpenseClick,
        onExpenseLongClick = onExpenseLongClick,
        selectedMonth = selectedMonth,
        availableMonths = availableMonths,
        onMonthChange = onMonthChange
    )
}

@Composable
fun TransactionItemRow(
    expense: Expense,
    account: KnownFinancialAccount? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isCredit = expense.type.equals("Credit", ignoreCase = true)
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val sign = if (isCredit) "+" else "-"
    val amountColor = if (isCredit) Color(0xFF2E7D32) else Color(0xFFC62828)
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val timeStr = timeFormat.format(Date(expense.dateMillis))

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isCredit) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCredit) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = expense.type,
                    tint = amountColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Merchant, Account, Time
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.merchant.ifEmpty { "Unknown" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF1E1E1E),
                    maxLines = 1
                )
                val accountText = when {
                    account != null -> "${account.institutionName ?: "Bank"} ••••${account.accountSuffix}"
                    !expense.accountSuffix.isNullOrBlank() -> "Ending ••••${expense.accountSuffix}"
                    else -> expense.source
                }
                Text(
                    text = "$timeStr • $accountText",
                    fontSize = 11.sp,
                    color = Color(0xFF757575),
                    maxLines = 1
                )
            }

            // Category Chip
            val category = (expense.tag ?: expense.category)?.takeIf { it.isNotBlank() } ?: "General"
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF3E5F5),
                border = BorderStroke(1.dp, Color(0xFFE1BEE7))
            ) {
                Text(
                    text = category,
                    fontSize = 11.sp,
                    color = Color(0xFF673AB7),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Amount
            Text(
                text = "$sign${currencyFormatter.format(expense.amount).replace("Rs.", "₹")}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = amountColor
            )

            // More Options Icon
            IconButton(
                onClick = onLongClick,
                modifier = Modifier.size(28.dp).padding(start = 4.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color(0xFF9E9E9E),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionActionSheet(
    expense: Expense,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isCredit = expense.type.equals("Credit", ignoreCase = true)
    val sign = if (isCredit) "+" else "-"
    val amountColor = if (isCredit) Color(0xFF10B981) else Color(0xFFEF4444)
    val formattedDateTime = remember(expense.dateMillis) {
        if (expense.dateMillis > 0) {
            val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(expense.dateMillis))
            val timeStr = SimpleDateFormat("h:mm a", Locale.US).format(Date(expense.dateMillis))
            "$dateStr • $timeStr IST"
        } else {
            "-"
        }
    }
    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = if (expense.amount % 1.0 == 0.0) 0 else 2
        }
    }
    val amountFormatted = remember(expense.amount) {
        val formatted = currencyFormatter.format(expense.amount).replace("Rs.", "₹").replace("INR", "₹").trim()
        if (!formatted.startsWith("₹")) "₹$formatted" else formatted
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141722),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color(0xFF333D52), RoundedCornerShape(2.dp))
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Header summary row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (expense.merchant.isNotBlank() && expense.merchant != "-") expense.merchant else (expense.category ?: "Transaction"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = formattedDateTime,
                        fontSize = 12.sp,
                        color = Color(0xFF8E9BAE)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "$sign$amountFormatted",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = amountColor,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF222938))
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "QUICK ACTIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 1. Quick Edit Option
            Surface(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1A1F2C),
                border = BorderStroke(1.dp, Color(0xFF283245))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF242E42), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Edit Transaction",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Modify amount, merchant, category, or note",
                            fontSize = 12.sp,
                            color = Color(0xFF8E9BAE)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Quick Delete Option
            Surface(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF241418),
                border = BorderStroke(1.dp, Color(0xFF4A1E24))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF381A20), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Delete Transaction",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color(0xFFEF4444)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Permanently remove from encrypted vault",
                            fontSize = 12.sp,
                            color = Color(0xFFA5787E)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF7F2D38),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

