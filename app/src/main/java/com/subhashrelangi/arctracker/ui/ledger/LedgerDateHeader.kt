package com.subhashrelangi.arctracker.ui.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

/**
 * Clean date section header matching reference mockup:
 * Today • 24 Oct                                       -₹1,820
 * Yesterday • 23 Oct                                   +₹46,310
 */
@Composable
fun LedgerDateHeader(
    dateLabel: String,
    txnCount: Int = 0,
    dayNet: Double = 0.0,
    modifier: Modifier = Modifier
) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val isPositive = dayNet >= 0
    val formattedAmount = currencyFormatter.format(kotlin.math.abs(dayNet))
        .replace("Rs.", "₹")
        .replace("INR", "₹")
        .trim()
        .substringBefore(".")
    val sign = if (isPositive) "+" else "-"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Clean Date label (e.g., "Today • 24 Oct")
        Text(
            text = dateLabel,
            color = Color(0xFF0F172A),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp
        )

        // Right: Day Net (e.g., "-₹1,820" or "+₹46,310")
        Text(
            text = "$sign$formattedAmount",
            color = if (isPositive) Color(0xFF059669) else Color(0xFF0F172A),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
