package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.R
import com.subhashrelangi.arctracker.data.Expense

data class MonthOption(val key: String, val label: String)

@Composable
fun DashboardCard(
    expenses: List<Expense>,
    selectedMonth: String = "",
    availableMonths: List<MonthOption> = emptyList(),
    onMonthChange: (String) -> Unit = {}
) {

    var dropdownExpanded by remember { mutableStateOf(false) }

    val selectedMonthLabel = availableMonths
        .firstOrNull { it.key == selectedMonth }
        ?.label
        ?: "This Month"

    // Money In / Out are scoped to the selected month
    // (expenses passed in are already filtered to that month).
    val moneyIn = expenses.filter { it.type == "Credit" && !it.isPending }.sumOf { it.amount }
    val moneyOut = expenses.filter { it.type == "Debit" && !it.isPending }.sumOf { it.amount }

    // Colors matching the reference image
    val cardBackground = Color(0xFFF2F2FE) // Matches the 3D wallet background seamlessly

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {

                // Money In tile
                MoneyBox(
                    label = "Money In",
                    amount = moneyIn,
                    labelColor = Color(0xFF2E7D32),
                    amountColor = Color(0xFF1B5E20),
                    boxColor = Color(0xFFE6F4EA),
                    iconColor = Color(0xFF388E3C),
                    iconBg = Color(0xFFC8E6C9),
                    icon = Icons.Filled.KeyboardArrowUp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Money Out tile
                MoneyBox(
                    label = "Money Out",
                    amount = moneyOut,
                    labelColor = Color(0xFFC62828),
                    amountColor = Color(0xFFB71C1C),
                    boxColor = Color(0xFFFDECEC),
                    iconColor = Color(0xFFD32F2F),
                    iconBg = Color(0xFFFFCDD2),
                    icon = Icons.Filled.KeyboardArrowDown
                )
            }
            
            // Period dropdown at the top of the wallet icon
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box {
                    Surface(
                        modifier = Modifier.clickable {
                            dropdownExpanded = true
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFE4DFF8),
                        border = BorderStroke(1.dp, Color(0xFFD1C4E9))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedMonthLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF673AB7)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Select period",
                                tint = Color(0xFF673AB7),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = {
                            dropdownExpanded = false
                        }
                    ) {
                        availableMonths.forEach { month ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = month.label,
                                        fontSize = 13.sp,
                                        fontWeight = if (month.key == selectedMonth) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        },
                                        color = if (month.key == selectedMonth) {
                                            Color(0xFF673AB7)
                                        } else {
                                            Color(0xFF1E1E1E)
                                        }
                                    )
                                },
                                onClick = {
                                    onMonthChange(month.key)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Image(
                    painter = painterResource(id = R.drawable.wallet_3d),
                    contentDescription = "Wallet",
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }
        }
    }
}

@Composable
private fun MoneyBox(
    label: String,
    amount: Double,
    labelColor: Color,
    amountColor: Color,
    boxColor: Color,
    iconColor: Color,
    iconBg: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(boxColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(iconBg, RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = labelColor
            )
            Text(
                text = String.format("₹%,.2f", amount),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
        }
    }
}
