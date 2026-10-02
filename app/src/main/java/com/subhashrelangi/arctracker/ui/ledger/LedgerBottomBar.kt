package com.subhashrelangi.arctracker.ui.ledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

import com.subhashrelangi.arctracker.ui.core.ArcTrackerSecurityFooter

/**
 * Encrypted Local-First SQLite Database footer indicator matching Ui-Designs/Ledgerpage.png.
 * Delegates to the universal ArcTrackerSecurityFooter component.
 */
@Composable
fun LedgerDatabaseFooter(
    modifier: Modifier = Modifier
) {
    ArcTrackerSecurityFooter(modifier = modifier)
}

/**
 * Floating Ledger Summary Action Bar matching Ui-Designs/Ledgerpage.png:
 * [ ↓ -₹5,989  |  ↑ +₹1,45,000  |  ⤓ Export ]
 */
@Composable
fun LedgerSummaryActionBar(
    totalDebits: Double,
    totalCredits: Double,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val debitFormatted = currencyFormatter.format(totalDebits)
        .replace("Rs.", "₹")
        .replace("INR", "₹")
        .trim()
        .substringBefore(".")
    val creditFormatted = currencyFormatter.format(totalCredits)
        .replace("Rs.", "₹")
        .replace("INR", "₹")
        .trim()
        .substringBefore(".")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .shadow(
                elevation = 16.dp,
                shape = LedgerShapes.FloatingSummary,
                spotColor = Color.Black,
                ambientColor = Color.Black
            ),
        shape = LedgerShapes.FloatingSummary,
        color = Color(0xFF131720),
        border = BorderStroke(1.dp, LedgerColors.BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Debit Total
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowDownward,
                    contentDescription = "Debits",
                    tint = LedgerColors.Debit,
                    modifier = Modifier.size(15.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = "-$debitFormatted",
                    color = LedgerColors.Debit,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Divider 1
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(Color(0xFF262D3D))
            )

            // 2. Credit Total
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = "Credits",
                    tint = LedgerColors.Credit,
                    modifier = Modifier.size(15.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = "+$creditFormatted",
                    color = LedgerColors.Credit,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Divider 2
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(Color(0xFF262D3D))
            )

            // 3. Export Action
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onExportClick)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = "Export CSV",
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(15.dp)
                )

                Spacer(modifier = Modifier.width(5.dp))

                Text(
                    text = "Export",
                    color = Color(0xFFE2E8F0),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
