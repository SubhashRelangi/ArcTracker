package com.subhashrelangi.arctracker.ui.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import java.util.Locale

@Composable
fun NetPositionVelocityCard(
    data: NetPositionVelocityData,
    currencyFormatter: NumberFormat,
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
            // Header Row: NET POSITION VELOCITY + Trend Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NET POSITION VELOCITY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = InsightsTheme.TextSecondary,
                    letterSpacing = 0.8.sp
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(InsightsTheme.PositiveGreenContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = "Trend",
                        tint = InsightsTheme.PositiveGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Primary Net Position Value
            val netPrefix = if (data.netAmount > 0.0) "+" else ""
            val netColor = if (data.netAmount >= 0.0) InsightsTheme.PositiveGreen else InsightsTheme.NegativeRed
            Text(
                text = "$netPrefix${currencyFormatter.format(data.netAmount)}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = netColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Savings rate & status
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${String.format(Locale.US, "%.1f", data.savingsRate)}% Savings Rate",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = InsightsTheme.PositiveGreen
                )
                Text(
                    text = "  •  ",
                    fontSize = 12.sp,
                    color = InsightsTheme.TextSecondary
                )
                Text(
                    text = data.savingsStatus,
                    fontSize = 12.sp,
                    color = InsightsTheme.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Inflow / Outflow Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(InsightsTheme.PositiveGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Total Inflow",
                        fontSize = 13.sp,
                        color = Color(0xFFC9D1D9)
                    )
                }
                Text(
                    text = currencyFormatter.format(data.totalInflow),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(InsightsTheme.NegativeRed, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Outflow (Spend)",
                        fontSize = 13.sp,
                        color = Color(0xFFC9D1D9)
                    )
                }
                Text(
                    text = currencyFormatter.format(data.totalOutflow),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Split Progress / Ratio Bar
            val totalFlow = data.totalInflow + data.totalOutflow
            val inflowWeight = if (totalFlow > 0.0) {
                (data.totalInflow / totalFlow).toFloat().coerceIn(0.08f, 0.92f)
            } else {
                0.5f
            }
            val outflowWeight = 1f - inflowWeight

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .weight(inflowWeight)
                        .fillMaxHeight()
                        .background(InsightsTheme.PositiveGreen)
                )
                Box(
                    modifier = Modifier
                        .weight(outflowWeight)
                        .fillMaxHeight()
                        .background(InsightsTheme.NegativeRed)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metric Cards: Average Daily Run Rate & Predicted Month End
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Average Daily Run Rate
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = InsightsTheme.SubCardBackground),
                    border = BorderStroke(1.dp, InsightsTheme.SubCardBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Average Daily Run\nRate",
                            fontSize = 11.sp,
                            color = InsightsTheme.TextSecondary,
                            lineHeight = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = currencyFormatter.format(data.dailyRunRate.toInt()),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = " / day",
                                fontSize = 11.sp,
                                color = InsightsTheme.TextSecondary,
                                modifier = Modifier.padding(bottom = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = data.dailyRunRateChangeText,
                            fontSize = 11.sp,
                            color = InsightsTheme.PositiveGreenSubtle,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Predicted Month End
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = InsightsTheme.SubCardBackground),
                    border = BorderStroke(1.dp, InsightsTheme.SubCardBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Predicted Month\nEnd",
                            fontSize = 11.sp,
                            color = InsightsTheme.TextSecondary,
                            lineHeight = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = currencyFormatter.format(data.predictedMonthEnd.toInt()),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = " est.",
                                fontSize = 11.sp,
                                color = InsightsTheme.TextSecondary,
                                modifier = Modifier.padding(bottom = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🟡 ", fontSize = 9.sp)
                            Text(
                                text = data.targetStatusText,
                                fontSize = 11.sp,
                                color = InsightsTheme.WarningAmber,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
