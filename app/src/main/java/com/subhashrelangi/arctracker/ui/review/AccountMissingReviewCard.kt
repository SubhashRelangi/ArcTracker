package com.subhashrelangi.arctracker.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.TransactionCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Account Missing Review Card with dynamic account assignment grid matching Ui-Designs/ReviewPage.png.
 */
@Composable
fun AccountMissingReviewCard(
    expense: Expense,
    knownAccounts: List<KnownFinancialAccount>,
    categories: List<TransactionCategory>,
    onAssignAndApprove: (Expense, KnownFinancialAccount) -> Unit,
    onDismissItem: (Expense) -> Unit,
    onAddNewAccount: (KnownFinancialAccount) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedAccount by remember { mutableStateOf<KnownFinancialAccount?>(null) }
    var showNewAccountDialog by remember { mutableStateOf(false) }

    // Pre-select if account matches suffix
    LaunchedEffect(expense, knownAccounts) {
        if (selectedAccount == null && !expense.accountSuffix.isNullOrBlank()) {
            selectedAccount = knownAccounts.find { it.accountSuffix == expense.accountSuffix }
        }
        if (selectedAccount == null && knownAccounts.isNotEmpty()) {
            selectedAccount = knownAccounts.first()
        }
    }

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

    val sourceDisplay = remember(expense.source) {
        if (expense.source.contains("SMS", ignoreCase = true)) "SMS Alert" else "Notification"
    }

    val currentCategory = remember(expense.categoryId, expense.tag, categories) {
        categories.find { it.id == expense.categoryId || it.name.equals(expense.tag, ignoreCase = true) }
            ?: categories.firstOrNull { it.id == "groceries" }
    }
    val categoryVisualIcon = currentCategory?.let { CategoryVisuals.getIcon(it.iconKey) } ?: Icons.Default.ShoppingCart
    val categoryName = currentCategory?.name ?: expense.tag ?: "Groceries & Daily Needs"

    Surface(
        shape = ArcShapes.Card,
        color = ArcColors.SurfaceCard,
        border = BorderStroke(1.dp, ArcColors.SurfaceCardBorder),
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Warning Banner at top
            Surface(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = ArcColors.WarningContainer,
                border = BorderStroke(1.dp, ArcColors.WarningBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = null,
                        tint = ArcColors.Warning,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Account Missing: Select source account",
                        color = ArcColors.Warning,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Main Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Row 1: Header (Icon + Merchant + Date) & (Amount + Needs Account)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Left
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF281E12)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = expense.merchant.ifBlank { "Local Store" },
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

                    // Right
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.widthIn(min = 90.dp)
                    ) {
                        Text(
                            text = "-₹${String.format(Locale.US, "%,.2f", expense.amount)}",
                            color = ArcColors.Debit,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(ArcColors.Warning, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Needs Account",
                                color = ArcColors.Warning,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Row 2: Prompt
                Text(
                    text = "Which account debited this?",
                    color = ArcColors.TextSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )

                // Row 3: Account Options 2x2 Grid
                val displayAccounts = knownAccounts.take(3)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // First row of 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (displayAccounts.isNotEmpty()) {
                            val acc = displayAccounts[0]
                            val isSelected = selectedAccount?.id == acc.id
                            AccountChoicePill(
                                label = "${acc.institutionName ?: acc.institutionId?.uppercase() ?: "Bank"} •• ${acc.accountSuffix}",
                                isSelected = isSelected,
                                onClick = { selectedAccount = acc },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (displayAccounts.size > 1) {
                            val acc = displayAccounts[1]
                            val isSelected = selectedAccount?.id == acc.id
                            AccountChoicePill(
                                label = "${acc.institutionName ?: acc.institutionId?.uppercase() ?: "Bank"} •• ${acc.accountSuffix}",
                                isSelected = isSelected,
                                onClick = { selectedAccount = acc },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            // If fewer than 2 accounts, put + New Wallet here
                            NewWalletButton(
                                onClick = { showNewAccountDialog = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Second row of 2 (if we had at least 2 accounts above)
                    if (displayAccounts.size >= 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (displayAccounts.size > 2) {
                                val acc = displayAccounts[2]
                                val isSelected = selectedAccount?.id == acc.id
                                AccountChoicePill(
                                    label = "${acc.institutionName ?: acc.institutionId?.uppercase() ?: "Bank"} •• ${acc.accountSuffix}",
                                    isSelected = isSelected,
                                    onClick = { selectedAccount = acc },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }

                            // 4th slot: + New Wallet
                            NewWalletButton(
                                onClick = { showNewAccountDialog = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Row 4: Category Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Category:",
                        color = ArcColors.TextSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = categoryVisualIcon,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = categoryName,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Row 5: SMS Source String (Expandable)
                ReviewTelemetrySection(
                    expense = expense,
                    title = "SMS Source String",
                    icon = Icons.Default.ChatBubbleOutline
                )

                // Row 6: Action Buttons [ ✓ Assign & Approve ] [ ✕ ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            selectedAccount?.let { acc ->
                                onAssignAndApprove(expense, acc)
                            }
                        },
                        enabled = selectedAccount != null,
                        shape = ArcShapes.Button,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                            disabledContainerColor = Color.White.copy(alpha = 0.4f),
                            disabledContentColor = Color.Black.copy(alpha = 0.5f)
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
                                imageVector = Icons.Default.CheckCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Assign & Approve",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.Black
                            )
                        }
                    }

                    // Cancel / dismiss button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1B202D),
                        border = BorderStroke(1.dp, Color(0xFF283044)),
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { onDismissItem(expense) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewAccountDialog) {
        NewAccountDialog(
            onAccountCreated = { newAcc ->
                onAddNewAccount(newAcc)
                selectedAccount = newAcc
            },
            onDismissRequest = { showNewAccountDialog = false }
        )
    }
}

@Composable
private fun AccountChoicePill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFF1D2638) else Color(0xFF161A26),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFF3B82F6) else Color(0xFF242A3A)
        ),
        modifier = modifier
            .height(38.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .border(
                        1.5.dp,
                        if (isSelected) Color(0xFF3B82F6) else Color(0xFF64748B),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6))
                    )
                }
            }
        }
    }
}

@Composable
private fun NewWalletButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF1C2230),
        border = BorderStroke(1.dp, Color(0xFF283146)),
        modifier = modifier
            .height(38.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+ New Wallet",
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
