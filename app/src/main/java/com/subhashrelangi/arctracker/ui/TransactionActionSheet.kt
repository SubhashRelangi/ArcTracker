package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.Expense
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Representation of a month option used for date-based filtering.
 */
data class MonthOption(val key: String, val label: String)

/**
 * Bottom action sheet displaying quick details and actions (Edit / Delete) for a tapped transaction.
 */
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
        windowInsets = WindowInsets(0, 0, 0, 0),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(Color(0xFF333D52), RoundedCornerShape(2.dp))
            )
        },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            // Header summary row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (expense.merchant.isNotBlank() && expense.merchant != "-") expense.merchant else (expense.category ?: "Transaction"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formattedDateTime,
                        fontSize = 11.5.sp,
                        color = Color(0xFF8E9BAE)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "$sign$amountFormatted",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = amountColor,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF222938))
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Small and clean quick action buttons side-by-side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Small & clean Edit Button
                Surface(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1A1F2C),
                    border = BorderStroke(1.dp, Color(0xFF283245))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Edit",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }

                // Small & clean Delete Button
                Surface(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF241418),
                    border = BorderStroke(1.dp, Color(0xFF4A1E24))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Delete",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            }
        }
    }
}
