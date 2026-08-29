package com.example.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

data class CategorySpending(val name: String, val amount: Double, val color: Color)

@Composable
fun SpendingOverviewCard() {
    // Mock data matching the reference image perfectly
    val categories = listOf(
        CategorySpending("Food & Dining", 1450.0, Color(0xFF8C54FF)), // Purple
        CategorySpending("Shopping", 1120.0, Color(0xFFFF66A3)), // Pink
        CategorySpending("Transport", 780.0, Color(0xFFFF9E3D)), // Orange
        CategorySpending("Bills & Utilities", 430.0, Color(0xFF3D8CFF)), // Blue
        CategorySpending("Others", 220.0, Color(0xFF4CB050)) // Green
    )
    val total = categories.sumOf { it.amount }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF0F0F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Spending Overview",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF1E1E1E)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "This Month",
                        color = Color(0xFF757575),
                        fontSize = 11.sp
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Dropdown",
                        tint = Color(0xFF757575),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chart & Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Donut Chart
                Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(84.dp)) {
                        var startAngle = -90f
                        val strokeWidth = 18.dp.toPx()
                        
                        categories.forEach { category ->
                            val sweepAngle = (category.amount / total).toFloat() * 360f
                            drawArc(
                                color = category.color,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle - 3f, // Leaves a small visual gap between segments
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            startAngle += sweepAngle
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Legend
                Column(modifier = Modifier.weight(1f)) {
                    val format = NumberFormat.getNumberInstance(Locale("en", "IN"))
                    
                    categories.forEach { category ->
                        val percentage = Math.round((category.amount / total) * 100).toInt()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Dot
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(category.color, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            
                            // Name
                            Text(
                                text = category.name,
                                fontSize = 11.sp,
                                color = Color(0xFF1E1E1E),
                                modifier = Modifier.weight(1f)
                            )
                            
                            // Amount
                            Text(
                                text = "₹${format.format(category.amount.toInt())}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1E1E1E)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            // Percentage
                            Text(
                                text = "$percentage%",
                                fontSize = 11.sp,
                                color = Color(0xFF757575),
                                modifier = Modifier.width(28.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }
    }
}
