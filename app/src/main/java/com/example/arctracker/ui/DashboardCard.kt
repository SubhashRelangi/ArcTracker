package com.example.arctracker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arctracker.R
import com.example.arctracker.data.Expense

@Composable
fun DashboardCard(expenses: List<Expense>) {
    val moneyIn = expenses.filter { it.type == "Credit" && !it.isPending }.sumOf { it.amount }
    val moneyOut = expenses.filter { it.type == "Debit" && !it.isPending }.sumOf { it.amount }
    val totalBalance = moneyIn - moneyOut

    // Colors matching the reference image
    val cardBackground = Color(0xFFF2F2FE) // Matches the 3D wallet background seamlessly
    val textColor = Color(0xFF1E1E1E)
    val subtitleColor = Color(0xFF757575)
    val moneyInColor = Color(0xFF388E3C)
    val moneyOutColor = Color(0xFFD32F2F)

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
                Text(
                    text = "Total Balance",
                    color = subtitleColor,
                    fontSize = 14.sp
                )
                Text(
                    text = String.format("₹%,.2f", totalBalance),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    color = textColor
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(
                            text = "Money In",
                            color = moneyInColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = String.format("₹%,.2f", moneyIn),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = textColor
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .width(1.dp)
                            .background(Color(0xFFE0E0E0))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column {
                        Text(
                            text = "Money Out",
                            color = moneyOutColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = String.format("₹%,.2f", moneyOut),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = textColor
                        )
                    }
                }
            }
            
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
