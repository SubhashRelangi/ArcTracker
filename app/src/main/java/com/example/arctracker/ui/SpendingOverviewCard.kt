package com.example.arctracker.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arctracker.data.Expense
import java.text.NumberFormat
import java.util.Locale

data class CategorySpending(val name: String, val amount: Double, val color: Color)

@Composable
fun SpendingOverviewCard(expenses: List<Expense>, periodLabel: String = "This Month") {
    // 1. Filter out pending and non-debit expenses.
    //    (expenses passed in are already scoped to the period selected in Total Balance section)
    val validExpenses = expenses.filter { !it.isPending && it.type == "Debit" }

    // 2. If empty, show empty state
    if (validExpenses.isEmpty()) {
        EmptySpendingAnimation()
        return
    }

    // 3. Group by category tag — category-wise spending, sorted highest first
    val grouped = validExpenses.groupBy { expense ->
        val tag = expense.tag
        if (tag.isNullOrBlank()) "Other" else tag
    }
        .map { (category, list) ->
            category to list.sumOf { it.amount }
        }
        .sortedByDescending { it.second }

    // 4. Map to CategorySpending with colors matching the design palette
    val colorPalette = listOf(
        Color(0xFF8C54FF), // Purple
        Color(0xFFFF66A3), // Pink
        Color(0xFFFF9E3D), // Orange
        Color(0xFF3D8CFF), // Blue
        Color(0xFF4CB050)  // Green
    )
    
    val categories = grouped.mapIndexed { index, pair ->
        CategorySpending(
            name = pair.first.ifBlank { "Unknown" },
            amount = pair.second,
            color = colorPalette[index % colorPalette.size]
        )
    }

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
                    // Static period label — scope is controlled by the dropdown
                    // in the Total Balance section, so no dropdown here.
                    Text(
                        text = periodLabel,
                        color = Color(0xFF673AB7),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
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
                                maxLines = 1,
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

@Composable
fun EmptySpendingAnimation() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF0F0F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "No Data",
                modifier = Modifier
                    .size(48.dp)
                    .scale(scale)
                    .alpha(alpha),
                tint = Color(0xFFD1C4E9)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Spending Yet",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color(0xFF9E9E9E)
            )
            Text(
                text = "Expenses will appear here automatically.",
                fontSize = 12.sp,
                color = Color(0xFFBDBDBD)
            )
        }
    }
}
