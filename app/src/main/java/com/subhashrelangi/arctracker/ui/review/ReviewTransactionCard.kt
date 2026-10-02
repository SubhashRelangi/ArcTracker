package com.subhashrelangi.arctracker.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.CategoryVisuals
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.TransactionCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Standard Verified Transaction Review Card matching Ui-Designs/ReviewPage.png.
 */
@Composable
fun ReviewTransactionCard(
    expense: Expense,
    accountLabel: String?,
    categories: List<TransactionCategory>,
    onApprove: (Expense) -> Unit,
    onEdit: (Expense) -> Unit,
    onDelete: (Expense) -> Unit,
    onCategoryChange: (Expense, TransactionCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCategoryPicker by remember { mutableStateOf(false) }

    val formattedTime = remember(expense.dateMillis) {
        val date = Date(expense.dateMillis)
        val cal = java.util.Calendar.getInstance()
        val todayYear = cal.get(java.util.Calendar.YEAR)
        val todayDay = cal.get(java.util.Calendar.DAY_OF_YEAR)
        cal.time = date
        val isToday = cal.get(java.util.Calendar.YEAR) == todayYear && cal.get(java.util.Calendar.DAY_OF_YEAR) == todayDay

        val timeFormat = SimpleDateFormat("h:mm a", Locale.US).format(date)
        if (isToday) {
            "Today, $timeFormat"
        } else {
            val dateFormat = SimpleDateFormat("dd MMM, h:mm a", Locale.US).format(date)
            dateFormat
        }
    }

    val sourceDisplay = remember(expense.source, expense.rawText) {
        when {
            expense.source.contains("NOTIFICATION", ignoreCase = true) || expense.rawText?.contains("Google Pay", ignoreCase = true) == true -> "UPI/GPay"
            expense.source.contains("SMS", ignoreCase = true) -> "SMS Alert"
            else -> "UPI/Bank"
        }
    }

    val sourceAppLabel = remember(expense.source, expense.rawText) {
        when {
            expense.rawText?.contains("Google Pay", ignoreCase = true) == true || expense.source.contains("GPAY", ignoreCase = true) -> "Google Pay App"
            expense.rawText?.contains("PhonePe", ignoreCase = true) == true -> "PhonePe App"
            expense.rawText?.contains("Paytm", ignoreCase = true) == true -> "Paytm App"
            expense.source.contains("SMS", ignoreCase = true) -> "SMS Service"
            else -> "Payment App"
        }
    }

    val currentCategory = remember(expense.categoryId, expense.tag, categories) {
        categories.find { it.id == expense.categoryId || it.name.equals(expense.tag, ignoreCase = true) }
            ?: categories.firstOrNull { it.id == "food_dining" }
    }

    val categoryVisualIcon = currentCategory?.let { CategoryVisuals.getIcon(it.iconKey) } ?: Icons.Default.Restaurant
    val categoryName = currentCategory?.name ?: expense.tag ?: "Food & Dining"

    Surface(
        shape = ArcShapes.Card,
        color = ArcColors.SurfaceCard,
        border = BorderStroke(1.dp, ArcColors.SurfaceCardBorder),
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Row 1: Header (Icon + Merchant + Date) & (Amount + Match Confidence)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Left: Merchant details
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF28171A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = categoryVisualIcon,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = expense.merchant.ifBlank { "Unknown Merchant" },
                            color = ArcColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$formattedTime • $sourceDisplay",
                            color = ArcColors.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right: Amount and Match Confidence
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.widthIn(min = 90.dp)
                ) {
                    val isCredit = expense.type.equals("Credit", ignoreCase = true)
                    val sign = if (isCredit) "+" else "-"
                    val amountColor = if (isCredit) ArcColors.Credit else ArcColors.Debit

                    Text(
                        text = "$sign₹${String.format(Locale.US, "%,.2f", expense.amount)}",
                        color = amountColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Match Confidence Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "94% Match",
                            color = Color(0xFF10B981),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Row 2: Source Chips (Source App, Account, Mode)
            ReviewMetadataChips(
                sourceText = sourceAppLabel,
                accountLabel = accountLabel ?: if (!expense.accountSuffix.isNullOrBlank()) "Acc •• ${expense.accountSuffix}" else null,
                modeText = "P2M"
            )

            // Row 3: Suggested Category + Auto-rule ready
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Suggested:",
                        color = ArcColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1B202D),
                        border = BorderStroke(1.dp, Color(0xFF283044)),
                        modifier = Modifier.clickable { showCategoryPicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = categoryVisualIcon,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = categoryName,
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Change Category",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Auto-rule\nready",
                    color = ArcColors.TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.Medium
                )
            }

            // Row 4: Raw Capture Telemetry (Expandable)
            ReviewTelemetrySection(
                expense = expense,
                title = "Raw Capture Telemetry",
                icon = Icons.Default.Code
            )

            // Row 5: Action Buttons [ ✓ Approve ] [ ✎ ] [ 🗑 ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary Approve Button (Dominant White Pill)
                Button(
                    onClick = { onApprove(expense) },
                    shape = ArcShapes.Button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Approve",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                    }
                }

                // Edit Button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1B202D),
                    border = BorderStroke(1.dp, Color(0xFF283044)),
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { onEdit(expense) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Transaction",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Delete Button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF281418),
                    border = BorderStroke(1.dp, Color(0xFF4C1D24)),
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { onDelete(expense) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Transaction",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showCategoryPicker) {
        CategoryPickerDialog(
            categories = categories,
            currentCategoryId = expense.categoryId,
            onCategorySelected = { selectedCategory ->
                onCategoryChange(expense, selectedCategory)
            },
            onDismissRequest = { showCategoryPicker = false }
        )
    }
}
