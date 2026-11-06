package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.ui.theme.ArcColors
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
 * Styled in ArcTracker Emerald Light theme with circular buttons.
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
    val amountColor = if (isCredit) ArcColors.Credit else ArcColors.TextPrimary
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
        containerColor = ArcColors.CardBackground,
        windowInsets = WindowInsets(0, 0, 0, 0),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(ArcColors.Border)
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
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
                        color = ArcColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = formattedDateTime,
                        fontSize = 12.sp,
                        color = ArcColors.TextMuted
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "$sign$amountFormatted",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = amountColor,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(color = ArcColors.Border, thickness = 1.dp)

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons with strict CircleShape
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Edit Button
                Button(
                    onClick = onEdit,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = ArcColors.Primary),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Edit",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }

                // Delete Button
                OutlinedButton(
                    onClick = onDelete,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, ArcColors.DangerBorder),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = ArcColors.DangerContainer),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = ArcColors.Danger,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ArcColors.Danger
                    )
                }
            }
        }
    }
}
