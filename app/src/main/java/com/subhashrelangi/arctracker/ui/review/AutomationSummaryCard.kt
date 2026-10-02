package com.subhashrelangi.arctracker.ui.review

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom summary card communicating local sandboxed automation metrics.
 */
@Composable
fun AutomationSummaryCard(
    autoCategorizedCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0D111A),
        border = BorderStroke(1.dp, Color(0xFF181F2F)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "✦",
                    color = Color(0xFF10B981),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$autoCategorizedCount transactions auto-categorized this month",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = "SMS parser uses rule-based heuristic regex engines. All matching runs strictly in local sandboxed memory.",
                color = Color(0xFF64748B),
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}
