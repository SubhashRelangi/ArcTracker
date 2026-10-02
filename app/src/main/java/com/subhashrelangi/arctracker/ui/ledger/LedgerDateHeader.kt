package com.subhashrelangi.arctracker.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

/**
 * Compact date section header matching Ui-Designs/Ledgerpage.png:
 * TODAY • 20 OCT      [ 2 txns ]             Day Net: -₹740.00
 */
@Composable
fun LedgerDateHeader(
    dateLabel: String,
    txnCount: Int,
    dayNet: Double,
    modifier: Modifier = Modifier
) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val isPositive = dayNet >= 0
    val formattedAmount = currencyFormatter.format(kotlin.math.abs(dayNet))
        .replace("Rs.", "₹")
        .replace("INR", "₹")
        .trim()
    val sign = if (isPositive) "+" else "-"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Date label and Txn Count pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = dateLabel.uppercase(Locale.US),
                color = LedgerColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF1A1F2B)
            ) {
                Text(
                    text = "$txnCount txns",
                    color = LedgerColors.TextSecondary,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right: Day Net
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Day Net: ",
                color = LedgerColors.TextSecondary,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = "$sign$formattedAmount",
                color = if (isPositive) LedgerColors.Credit else LedgerColors.Debit,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
