package com.subhashrelangi.arctracker.ui.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.Expense
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Expandable technical telemetry and raw capture evidence section.
 */
@Composable
fun ReviewTelemetrySection(
    expense: Expense,
    title: String = "Raw Capture Telemetry",
    icon: ImageVector = Icons.Default.Code,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "rotation")

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ArcColors.TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    color = ArcColors.TextSecondary,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = ArcColors.TextMuted,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ArcColors.TelemetryBackground,
                border = BorderStroke(1.dp, ArcColors.TelemetryBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.US)
                    val formattedDate = dateFormat.format(Date(expense.dateMillis))

                    TelemetryItem(label = "PAYLOAD", value = expense.rawText ?: "(No raw payload available)")
                    TelemetryItem(label = "SOURCE", value = expense.source)
                    if (!expense.notificationKey.isNullOrBlank()) {
                        TelemetryItem(label = "DEDUP_HASH", value = expense.notificationKey.take(16) + "...")
                    }
                    TelemetryItem(label = "TIMESTAMP", value = formattedDate)
                    TelemetryItem(label = "TYPE / FLOW", value = "${expense.type} (${expense.relationshipType ?: "STANDARD"})")
                    if (!expense.categorySource.isNullOrBlank() && expense.categorySource != "NONE") {
                        TelemetryItem(label = "RULE_SOURCE", value = expense.categorySource)
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryItem(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Color(0xFF64748B),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Text(
            text = value,
            color = Color(0xFFCBD5E1),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 15.sp
        )
    }
}
