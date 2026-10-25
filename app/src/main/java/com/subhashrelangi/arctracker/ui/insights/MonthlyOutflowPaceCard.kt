package com.subhashrelangi.arctracker.ui.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MonthlyOutflowPaceCard(
    totalOutflow: Double = 36750.0,
    budgetCap: Double = 75000.0,
    previousMonthName: String = "Sept",
    percentageVsPrevious: Int = 12,
    weeklyOutflows: List<Double> = emptyList(),
    isDemoMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cal = Calendar.getInstance()
    val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
    val totalDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val daysRemaining = maxOf(0, totalDaysInMonth - dayOfMonth)

    // Calculate utilization & buffer
    val displayOutflow = if (isDemoMode) (if (totalOutflow > 0.0) totalOutflow else 36750.0) else totalOutflow
    val displayCap = if (isDemoMode) (if (budgetCap > 0.0) budgetCap else 75000.0) else (if (budgetCap > 0.0) budgetCap else maxOf(displayOutflow, 50000.0))
    val utilizationFraction = if (displayCap > 0.0) (displayOutflow / displayCap).coerceIn(0.0, 1.0).toFloat() else 0f
    val utilizationPercent = (utilizationFraction * 100).roundToInt()
    val bufferLeft = maxOf(0.0, displayCap - displayOutflow)

    val formattedOutflow = "₹" + NumberFormat.getNumberInstance(Locale("en", "IN")).format(displayOutflow.roundToInt())
    val formattedCap = "₹" + NumberFormat.getNumberInstance(Locale("en", "IN")).format(displayCap.roundToInt())
    val formattedBuffer = "₹" + NumberFormat.getNumberInstance(Locale("en", "IN")).format(bufferLeft.roundToInt())
    val weeklyTarget = "₹" + NumberFormat.getNumberInstance(Locale("en", "IN")).format((displayCap / 8.0).roundToInt() * 2)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = InsightsTheme.LightCardBackground),
        border = BorderStroke(1.dp, InsightsTheme.LightCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Row 1: Monthly Outflow label & Top-right icon button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "MONTHLY OUTFLOW",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InsightsTheme.LightTextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formattedOutflow,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = InsightsTheme.LightTextPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        // Green Trend Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(InsightsTheme.EmeraldGreenBadge)
                                .border(BorderStroke(1.dp, InsightsTheme.EmeraldGreenBadgeBorder), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Decrease",
                                    tint = InsightsTheme.EmeraldGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "$percentageVsPrevious% vs $previousMonthName",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = InsightsTheme.EmeraldGreen
                                )
                            }
                        }
                    }
                }

                // Top-right column icon button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Leaderboard,
                        contentDescription = "Chart",
                        tint = InsightsTheme.LightTextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cap & Buffer row above progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cap: $formattedCap ($utilizationPercent% utilized)",
                    fontSize = 12.sp,
                    color = InsightsTheme.LightTextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$formattedBuffer buffer left",
                    fontSize = 12.sp,
                    color = InsightsTheme.EmeraldGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Progress Bar (Two-tone)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFE2E8F0))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = utilizationFraction)
                        .clip(RoundedCornerShape(999.dp))
                        .background(InsightsTheme.CharcoalDark)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Days status row below progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Day $dayOfMonth of $totalDaysInMonth",
                    fontSize = 11.5.sp,
                    color = InsightsTheme.LightTextSecondary
                )
                Text(
                    text = "$daysRemaining days remaining",
                    fontSize = 11.5.sp,
                    color = InsightsTheme.LightTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Weekly Outflow Pace Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Weekly Outflow Pace",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = InsightsTheme.LightTextPrimary
                )
                Text(
                    text = "Target: $weeklyTarget / wk",
                    fontSize = 12.sp,
                    color = InsightsTheme.LightTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4 Weekly Outflow Bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                if (isDemoMode || weeklyOutflows.isEmpty()) {
                    // W1
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFE2E8F0))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "W1",
                            fontSize = 12.sp,
                            color = InsightsTheme.LightTextSecondary
                        )
                    }

                    // W2 (Ochre/Amber brown)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(InsightsTheme.AmberBrown)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "W2",
                            fontSize = 12.sp,
                            color = InsightsTheme.LightTextSecondary
                        )
                    }

                    // W3
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFE2E8F0))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "W3",
                            fontSize = 12.sp,
                            color = InsightsTheme.LightTextSecondary
                        )
                    }

                    // W4 (Active Emerald Green with label above)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "8.0k",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = InsightsTheme.EmeraldGreen
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(InsightsTheme.EmeraldGreenDark)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "W4",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = InsightsTheme.EmeraldGreen
                        )
                    }
                } else {
                    // Real data weekly bars
                    val maxSpend = maxOf(weeklyOutflows.maxOrNull() ?: 1.0, 1.0)
                    for (i in 0 until 4) {
                        val wSpend = weeklyOutflows.getOrElse(i) { 0.0 }
                        val fraction = (wSpend / maxSpend).coerceIn(0.15, 1.0).toFloat()
                        val isHighest = wSpend == maxSpend && wSpend > 0.0
                        val barColor = when {
                            isHighest -> InsightsTheme.EmeraldGreenDark
                            wSpend > 0.0 -> InsightsTheme.AmberBrown
                            else -> Color(0xFFE2E8F0)
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (wSpend > 0.0) {
                                val labelText = if (wSpend >= 1000.0) {
                                    String.format(Locale.US, "%.1fk", wSpend / 1000.0)
                                } else {
                                    "₹${wSpend.toInt()}"
                                }
                                Text(
                                    text = labelText,
                                    fontSize = 11.sp,
                                    fontWeight = if (isHighest) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isHighest) InsightsTheme.EmeraldGreen else InsightsTheme.LightTextSecondary
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                            } else {
                                Spacer(modifier = Modifier.height(18.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((52 * fraction).dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(barColor)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "W${i + 1}",
                                fontSize = 12.sp,
                                fontWeight = if (isHighest) FontWeight.Bold else FontWeight.Normal,
                                color = if (isHighest) InsightsTheme.EmeraldGreen else InsightsTheme.LightTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
