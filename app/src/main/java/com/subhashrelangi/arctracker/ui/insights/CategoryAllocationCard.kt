package com.subhashrelangi.arctracker.ui.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CategoryAllocationCard(
    data: CategoryAllocationData,
    currencyFormatter: NumberFormat,
    onInsightsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = InsightsTheme.CardBackground),
        border = BorderStroke(1.dp, InsightsTheme.CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Title & Subtitle + Insights Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Category Allocation",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val bucketCount = if (data.categories.isNotEmpty()) data.categories.size else 0
                    Text(
                        text = "$bucketCount core expenditure buckets",
                        fontSize = 11.sp,
                        color = InsightsTheme.TextSecondary
                    )
                }

                Row(
                    modifier = Modifier.clickable { onInsightsClick() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Insights ›",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = InsightsTheme.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Donut Chart + Primary Driver Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Donut Chart with Center Text
                Box(
                    modifier = Modifier.size(104.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(96.dp)) {
                        val strokeWidth = 14.dp.toPx()
                        if (data.isEmpty || data.categories.isEmpty()) {
                            drawArc(
                                color = Color(0xFF21262D),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth)
                            )
                        } else {
                            var currentAngle = -90f
                            data.categories.forEach { item ->
                                val sweep = (item.percentage / 100.0 * 360.0).toFloat()
                                if (sweep > 0f) {
                                    drawArc(
                                        color = item.color,
                                        startAngle = currentAngle,
                                        sweepAngle = maxOf(2f, sweep - 2f),
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                    currentAngle += sweep
                                }
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = data.periodLabel,
                            fontSize = 11.sp,
                            color = InsightsTheme.TextSecondary
                        )
                        Text(
                            text = data.totalSpendFormatted,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Primary Driver Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PRIMARY DRIVER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = InsightsTheme.WarningAmber,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = data.primaryDriverName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = data.primaryDriverDetails,
                        fontSize = 12.sp,
                        color = InsightsTheme.TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Category Breakdown Rows
            if (data.isEmpty || data.categories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No categorized spending recorded in this period.",
                        fontSize = 12.sp,
                        color = InsightsTheme.TextSecondary
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    data.categories.take(6).forEach { cat ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(cat.color, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = cat.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", cat.percentage)}%",
                                    fontSize = 11.sp,
                                    color = InsightsTheme.TextSecondary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = currencyFormatter.format(cat.amount),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Proportional bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF21262D))
                            ) {
                                val progress = (cat.percentage / 100.0).toFloat().coerceIn(0.01f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(progress)
                                        .background(cat.color)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
