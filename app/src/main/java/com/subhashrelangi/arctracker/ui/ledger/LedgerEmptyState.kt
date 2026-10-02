package com.subhashrelangi.arctracker.ui.ledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LedgerEmptyState(
    isSearching: Boolean,
    isFilterActive: Boolean,
    onClearFilters: () -> Unit,
    onAddTransaction: () -> Unit,
    onScanSms: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val icon = when {
                isSearching -> Icons.Default.SearchOff
                isFilterActive -> Icons.Default.FilterListOff
                else -> Icons.Outlined.ReceiptLong
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LedgerColors.TextMuted,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            val title = when {
                isSearching -> "No results found"
                isFilterActive -> "No matching transactions"
                else -> "No transactions yet"
            }

            Text(
                text = title,
                color = LedgerColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            val subtitle = when {
                isSearching -> "Try searching for a different merchant, note, or amount."
                isFilterActive -> "No transactions match your currently active filters."
                else -> "ArcTracker hasn't recorded any transactions for this period."
            }

            Text(
                text = subtitle,
                color = LedgerColors.TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (isSearching || isFilterActive) {
                OutlinedButton(
                    onClick = onClearFilters,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, LedgerColors.BorderLight),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LedgerColors.TextPrimary)
                ) {
                    Text("Clear Filters", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onAddTransaction,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Transaction", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onScanSms,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, LedgerColors.BorderLight),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LedgerColors.TextPrimary)
                    ) {
                        Text("Scan SMS", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
