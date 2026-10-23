package com.subhashrelangi.arctracker.ui.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CategoryAllocationCard(
    data: CategoryAllocationData,
    currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")),
    onInsightsClick: () -> Unit = {},
    onAllCategoriesClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Default categories from the reference mockup if no user categories yet
    val displayCategories = if (data.categories.isNotEmpty()) {
        data.categories
    } else {
        listOf(
            CategoryAllocationItem(
                id = "food",
                name = "Food & Dining",
                percentage = 34.0,
                amount = 12450.0,
                color = Color(0xFF0F172A),
                iconKey = "food",
                transactionCount = 38
            ),
            CategoryAllocationItem(
                id = "housing",
                name = "Housing & Utilities",
                percentage = 27.0,
                amount = 9800.0,
                color = Color(0xFF475569),
                iconKey = "housing",
                transactionCount = 6
            ),
            CategoryAllocationItem(
                id = "shopping",
                name = "Shopping & Tech",
                percentage = 19.0,
                amount = 6980.0,
                color = Color(0xFF94A3B8),
                iconKey = "shopping",
                transactionCount = 11
            ),
            CategoryAllocationItem(
                id = "transport",
                name = "Transport",
                percentage = 11.0,
                amount = 4120.0,
                color = Color(0xFF94A3B8),
                iconKey = "transport",
                transactionCount = 22
            ),
            CategoryAllocationItem(
                id = "health",
                name = "Health & Fitness",
                percentage = 9.0,
                amount = 3400.0,
                color = Color(0xFFCBD5E1),
                iconKey = "health",
                transactionCount = 1
            )
        )
    }

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
            // Header Row: Title & Subtitle + All Categories Link
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
                        color = InsightsTheme.LightTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Aggregated by internal ruleset",
                        fontSize = 12.sp,
                        color = InsightsTheme.LightTextSecondary
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onAllCategoriesClick() }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All categories",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = InsightsTheme.EmeraldGreen
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "All categories",
                        tint = InsightsTheme.EmeraldGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Items List
            displayCategories.forEachIndexed { index, item ->
                CategoryRowItem(
                    item = item,
                    index = index,
                    currencyFormatter = currencyFormatter
                )
                if (index < displayCategories.size - 1) {
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun CategoryRowItem(
    item: CategoryAllocationItem,
    index: Int,
    currencyFormatter: NumberFormat
) {
    val icon = resolveCategoryIcon(item.name)
    val barColor = when (index) {
        0 -> Color(0xFF0F172A) // Dark Charcoal
        1 -> Color(0xFF475569) // Slate
        2 -> Color(0xFF94A3B8) // Light Slate
        3 -> Color(0xFF94A3B8) // Light Slate
        else -> Color(0xFFCBD5E1) // Soft Grey
    }

    val subText = when {
        item.name.contains("Transport", ignoreCase = true) -> "${if (item.transactionCount > 0) item.transactionCount else 22} trips logged"
        item.name.contains("Health", ignoreCase = true) || item.name.contains("Fitness", ignoreCase = true) -> "Annual renew active"
        item.transactionCount > 0 -> "${item.transactionCount} transactions"
        else -> "${item.transactionCount} transactions"
    }

    val formattedAmount = try {
        "₹" + NumberFormat.getNumberInstance(Locale("en", "IN")).format(item.amount.roundToInt())
    } catch (_: Exception) {
        "₹${item.amount.roundToInt()}"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon in rounded container
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = item.name,
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Subtitle Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = InsightsTheme.LightTextPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subText,
                    fontSize = 11.5.sp,
                    color = InsightsTheme.LightTextSecondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount & Percentage Column
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formattedAmount,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = InsightsTheme.LightTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${item.percentage.roundToInt()}% of spend",
                    fontSize = 11.5.sp,
                    color = InsightsTheme.LightTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFF1F5F9))
        ) {
            val progressFraction = (item.percentage / 100.0).coerceIn(0.02, 1.0).toFloat()
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progressFraction)
                    .clip(RoundedCornerShape(999.dp))
                    .background(barColor)
            )
        }
    }
}

private fun resolveCategoryIcon(name: String): ImageVector {
    val lower = name.lowercase(Locale.ROOT)
    return when {
        lower.contains("food") || lower.contains("dining") || lower.contains("restaurant") || lower.contains("cafe") -> Icons.Outlined.Restaurant
        lower.contains("hous") || lower.contains("util") || lower.contains("home") || lower.contains("rent") -> Icons.Outlined.Home
        lower.contains("shop") || lower.contains("tech") || lower.contains("retail") || lower.contains("elect") -> Icons.Outlined.ShoppingBag
        lower.contains("trans") || lower.contains("trip") || lower.contains("commute") || lower.contains("travel") || lower.contains("cab") -> Icons.Outlined.DirectionsCar
        lower.contains("health") || lower.contains("fit") || lower.contains("gym") || lower.contains("medic") -> Icons.Outlined.FitnessCenter
        else -> Icons.Outlined.Category
    }
}
