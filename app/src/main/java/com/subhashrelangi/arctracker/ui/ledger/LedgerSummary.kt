package com.subhashrelangi.arctracker.ui.ledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import java.text.NumberFormat
import java.util.Locale

/**
 * Monthly Overview Card matching reference mockup:
 * [ OCTOBER OVERVIEW               ↗ +₹48,250 Net ]
 * [ Total Outflow                  Total Inflow   ]
 * [ ₹36,750                        +₹85,000       ]
 * [ 28 debit events                2 deposits     ]
 * [ ==============================--------------  ]
 */
@Composable
fun LedgerSummary(
    periodLabel: String,
    totalOutflow: Double = 0.0,
    debitCount: Int = 0,
    totalInflow: Double = 0.0,
    creditCount: Int = 0,
    netFlow: Double = 0.0,
    entryCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    fun formatAmt(amount: Double): String {
        return currencyFormatter.format(kotlin.math.abs(amount))
            .replace("Rs.", "₹")
            .replace("INR", "₹")
            .trim()
            .substringBefore(".")
    }

    val overviewTitle = periodLabel.substringBefore(" ").uppercase(Locale.US) + " OVERVIEW"
    val isNetPositive = netFlow >= 0
    val netFormatted = (if (isNetPositive) "+₹" else "-₹") + formatAmt(netFlow)

    val totalActivity = totalOutflow + totalInflow
    val progressRatio = if (totalActivity > 0) {
        (totalOutflow / totalActivity).toFloat().coerceIn(0.08f, 0.95f)
    } else {
        0.5f
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 1. Top Header Row: "OCTOBER OVERVIEW" + "↗ +₹48,250 Net"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = overviewTitle,
                    color = Color(0xFF64748B),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                Text(
                    text = if (isNetPositive) "↗ $netFormatted Net" else "↘ $netFormatted Net",
                    color = if (isNetPositive) Color(0xFF059669) else Color(0xFFEF4444),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Metrics Row: Outflow vs Inflow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Outflow Column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total Outflow",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "₹" + formatAmt(totalOutflow),
                        color = Color(0xFF0F172A),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$debitCount debit events",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }

                // Inflow Column
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Total Inflow",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "+₹" + formatAmt(totalInflow),
                        color = Color(0xFF059669),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$creditCount deposits",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Progress / Split Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFE2E8F0))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressRatio)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF059669))
                )
            }
        }
    }
}
