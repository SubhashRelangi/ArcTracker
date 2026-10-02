package com.subhashrelangi.arctracker.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

/**
 * Period summary row matching Ui-Designs/Ledgerpage.png:
 * ● October 2026 • 42 entries            Net Flow: -₹58,420
 */
@Composable
fun LedgerSummary(
    periodLabel: String,
    entryCount: Int,
    netFlow: Double,
    modifier: Modifier = Modifier
) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val isPositive = netFlow >= 0
    val netFormatted = currencyFormatter.format(kotlin.math.abs(netFlow))
        .replace("Rs.", "₹")
        .replace("INR", "₹")
        .trim()
        .substringBefore(".") // In the screenshot: -₹58,420 (no decimals in net flow summary)

    val sign = if (isPositive) "+" else "-"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Period & entry count
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(LedgerColors.TextMuted, CircleShape)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "$periodLabel • $entryCount entries",
                color = LedgerColors.TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.2.sp
            )
        }

        // Right: Net Flow
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Net Flow: ",
                color = LedgerColors.TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = "$sign$netFormatted",
                color = if (isPositive) LedgerColors.Credit else LedgerColors.Debit,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
