package com.subhashrelangi.arctracker.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class LedgerFilterMode {
    ALL,
    DEBITS,
    CREDITS,
    NEEDS_REVIEW
}

/**
 * Filter Row matching Ui-Designs/Ledgerpage.png:
 * [ All 42 ] [ • Debits ] [ • Credits ] [ • Needs Review 3 ]
 */
@Composable
fun LedgerFilterRow(
    selectedFilter: LedgerFilterMode,
    totalCount: Int,
    debitCount: Int,
    creditCount: Int,
    reviewCount: Int,
    onFilterSelected: (LedgerFilterMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. All Chip
        val isAllSelected = selectedFilter == LedgerFilterMode.ALL
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isAllSelected) Color.White else LedgerColors.FilterChipBg,
            border = if (isAllSelected) null else androidx.compose.foundation.BorderStroke(1.dp, LedgerColors.BorderSubtle),
            modifier = Modifier
                .height(34.dp)
                .clickable { onFilterSelected(LedgerFilterMode.ALL) }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "All",
                    color = if (isAllSelected) LedgerColors.FilterChipSelectedText else LedgerColors.TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isAllSelected) Color(0xFFE2E8F0) else Color(0xFF222837))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = totalCount.toString(),
                        color = if (isAllSelected) Color(0xFF0F141C) else LedgerColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. Debits Chip
        val isDebitSelected = selectedFilter == LedgerFilterMode.DEBITS
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isDebitSelected) Color(0xFF2C1317) else LedgerColors.FilterChipBg,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isDebitSelected) LedgerColors.Debit else LedgerColors.BorderSubtle
            ),
            modifier = Modifier
                .height(34.dp)
                .clickable { onFilterSelected(LedgerFilterMode.DEBITS) }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(LedgerColors.Debit, CircleShape)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "Debits",
                    color = if (isDebitSelected) LedgerColors.Debit else LedgerColors.TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = if (isDebitSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }

        // 3. Credits Chip
        val isCreditSelected = selectedFilter == LedgerFilterMode.CREDITS
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isCreditSelected) Color(0xFF0C2419) else LedgerColors.FilterChipBg,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isCreditSelected) LedgerColors.CreditMuted else LedgerColors.BorderSubtle
            ),
            modifier = Modifier
                .height(34.dp)
                .clickable { onFilterSelected(LedgerFilterMode.CREDITS) }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(LedgerColors.CreditMuted, CircleShape)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "Credits",
                    color = if (isCreditSelected) LedgerColors.CreditMuted else LedgerColors.TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = if (isCreditSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }

        // 4. Needs Review Chip
        val isReviewSelected = selectedFilter == LedgerFilterMode.NEEDS_REVIEW
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isReviewSelected) Color(0xFF2B1F09) else LedgerColors.FilterChipBg,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isReviewSelected) LedgerColors.WarningAmber else LedgerColors.BorderSubtle
            ),
            modifier = Modifier
                .height(34.dp)
                .clickable { onFilterSelected(LedgerFilterMode.NEEDS_REVIEW) }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(LedgerColors.WarningAmberDot, CircleShape)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "Needs Review",
                    color = if (isReviewSelected) LedgerColors.WarningAmber else LedgerColors.TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = if (isReviewSelected) FontWeight.Bold else FontWeight.Medium
                )

                if (reviewCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF382B12))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = reviewCount.toString(),
                            color = LedgerColors.WarningAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
