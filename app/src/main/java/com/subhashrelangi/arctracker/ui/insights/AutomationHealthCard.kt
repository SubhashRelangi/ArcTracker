package com.subhashrelangi.arctracker.ui.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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

@Composable
fun AutomationHealthCard(
    data: AutomationHealthData,
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
            // Header Row: Title & Subtitle + % Hands-Free Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = InsightsTheme.PositiveGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Automation Health",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Local daemon ingestion diagnostics",
                        fontSize = 11.sp,
                        color = InsightsTheme.TextSecondary
                    )
                }

                Surface(
                    color = InsightsTheme.PositiveGreenContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${data.handsFreePercentage}% Hands-Free",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InsightsTheme.PositiveGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sub-card with stacked progress bar and 3-column breakdown
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = InsightsTheme.SubCardBackground),
                border = BorderStroke(1.dp, InsightsTheme.SubCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // Row: Automated Ingest vs Manual Entry ... 54 / 56 events
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Automated Ingest vs Manual Entry",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFC9D1D9)
                        )
                        Text(
                            text = "${data.automatedCount} / ${data.totalEvents} events",
                            fontSize = 11.sp,
                            color = InsightsTheme.TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stacked Segmented Horizontal Bar
                    val total = data.notificationPercentage + data.smsPercentage + data.manualPercentage
                    val nWeight = if (total > 0) (data.notificationPercentage.toFloat() / total).coerceAtLeast(0.02f) else 0.33f
                    val sWeight = if (total > 0) (data.smsPercentage.toFloat() / total).coerceAtLeast(0.02f) else 0.33f
                    val mWeight = if (total > 0) (data.manualPercentage.toFloat() / total).coerceAtLeast(0.02f) else 0.34f

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF21262D))
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(nWeight)
                                .fillMaxHeight()
                                .background(InsightsTheme.PositiveGreen)
                        )
                        Box(
                            modifier = Modifier
                                .weight(sWeight)
                                .fillMaxHeight()
                                .background(InsightsTheme.PrimaryBlue)
                        )
                        Box(
                            modifier = Modifier
                                .weight(mWeight)
                                .fillMaxHeight()
                                .background(Color(0xFF6E7681))
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3-Column Breakdown Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Col 1: Notification
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(InsightsTheme.PositiveGreen, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Notification",
                                    fontSize = 11.sp,
                                    color = Color(0xFFC9D1D9)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${data.notificationPercentage}%",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = data.notificationSources,
                                fontSize = 10.sp,
                                color = InsightsTheme.TextSecondary,
                                lineHeight = 13.sp
                            )
                        }

                        // Col 2: SMS Parser
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(InsightsTheme.PrimaryBlue, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SMS Parser",
                                    fontSize = 11.sp,
                                    color = Color(0xFFC9D1D9)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${data.smsPercentage}%",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = data.smsSources,
                                fontSize = 10.sp,
                                color = InsightsTheme.TextSecondary,
                                lineHeight = 13.sp
                            )
                        }

                        // Col 3: Manual
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF6E7681), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Manual",
                                    fontSize = 11.sp,
                                    color = Color(0xFFC9D1D9)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${data.manualPercentage}%",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = data.manualSources,
                                fontSize = 10.sp,
                                color = InsightsTheme.TextSecondary,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
