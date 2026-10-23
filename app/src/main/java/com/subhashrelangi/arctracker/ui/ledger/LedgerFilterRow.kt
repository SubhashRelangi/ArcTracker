package com.subhashrelangi.arctracker.ui.ledger

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Filter Row matching reference mockup:
 * [ All ] [ Expenses ] [ Income ] [ Needs Review ]
 */
@Composable
fun LedgerFilterRow(
    selectedFilter: LedgerFilterMode,
    totalCount: Int = 0,
    debitCount: Int = 0,
    creditCount: Int = 0,
    reviewCount: Int = 0,
    onFilterSelected: (LedgerFilterMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = listOf(
        LedgerFilterMode.ALL to "All",
        LedgerFilterMode.DEBITS to "Expenses",
        LedgerFilterMode.CREDITS to "Income",
        LedgerFilterMode.NEEDS_REVIEW to "Needs Review"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        filters.forEach { (mode, label) ->
            val isSelected = selectedFilter == mode

            Surface(
                onClick = { onFilterSelected(mode) },
                shape = RoundedCornerShape(999.dp),
                color = if (isSelected) LedgerColors.FilterChipSelectedBg else LedgerColors.FilterChipBg,
                shadowElevation = if (isSelected) 1.dp else 0.dp
            ) {
                Text(
                    text = label,
                    color = if (isSelected) LedgerColors.FilterChipSelectedText else LedgerColors.TextSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
