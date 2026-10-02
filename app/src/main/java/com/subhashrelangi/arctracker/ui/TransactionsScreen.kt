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
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val sign = if (isCredit) "+" else "-"
    val amountColor = if (isCredit) Color(0xFF2E7D32) else Color(0xFFC62828)
    val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFFBF8FF)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = expense.merchant.ifEmpty { "Unknown" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1E1E1E)
                    )
                    Text(
                        text = dateFormat.format(Date(expense.dateMillis)),
                        fontSize = 12.sp,
                        color = Color(0xFF757575)
                    )
                }
                Text(
                    text = "$sign${currencyFormatter.format(expense.amount).replace("Rs.", "₹")}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = amountColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF3EFFF)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color(0xFF673AB7),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Edit",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF673AB7)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFEBEE)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Delete",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFFD32F2F)
                    )
                }
            }
        }
    }
}

