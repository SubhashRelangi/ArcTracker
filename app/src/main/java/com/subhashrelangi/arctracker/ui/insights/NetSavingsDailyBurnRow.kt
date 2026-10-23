package com.subhashrelangi.arctracker.ui.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Shield
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
import kotlin.math.roundToInt

@Composable
fun NetSavingsDailyBurnRow(
    netSavings: Double = 48250.0,
    savingsRatePercent: Double = 56.7,
    dailyBurn: Double = 1531.0,
    dailyLimit: Double = 2500.0,
    modifier: Modifier = Modifier
) {
    val displaySavings = if (netSavings != 0.0) netSavings else 48250.0
    val displaySavingsRate = if (savingsRatePercent > 0.0) savingsRatePercent else 56.7
    val displayDailyBurn = if (dailyBurn > 0.0) dailyBurn else 1531.0

    val formattedSavings = (if (displaySavings >= 0) "+₹" else "-₹") +
            NumberFormat.getNumberInstance(Locale("en", "IN")).format(Math.abs(displaySavings.roundToInt()))
    val formattedDailyBurn = "₹" + NumberFormat.getNumberInstance(Locale("en", "IN")).format(displayDailyBurn.roundToInt())
    val formattedDailyLimit = "₹" + NumberFormat.getNumberInstance(Locale("en", "IN")).format(dailyLimit.roundToInt())

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card 1: Net Savings
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = InsightsTheme.LightCardBackground),
            border = BorderStroke(1.dp, InsightsTheme.LightCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Net Savings",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = InsightsTheme.LightTextSecondary
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDCFCE7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = "Savings",
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = formattedSavings,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = InsightsTheme.EmeraldGreen
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(InsightsTheme.EmeraldGreenBadge)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f%%", displaySavingsRate),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = InsightsTheme.EmeraldGreen
                        )
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "saved",
                        fontSize = 11.5.sp,
                        color = InsightsTheme.LightTextSecondary
                    )
                }
            }
        }

        // Card 2: Daily Burn
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = InsightsTheme.LightCardBackground),
            border = BorderStroke(1.dp, InsightsTheme.LightCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daily Burn",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = InsightsTheme.LightTextSecondary
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocalFireDepartment,
                            contentDescription = "Daily Burn",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = formattedDailyBurn,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = InsightsTheme.LightTextPrimary
                    )
                    Text(
                        text = "/d",
                        fontSize = 13.sp,
                        color = InsightsTheme.LightTextSecondary,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(bottom = 2.dp, start = 1.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Pace < $formattedDailyLimit limit",
                    fontSize = 11.5.sp,
                    color = InsightsTheme.LightTextSecondary
                )
            }
        }
    }
}
