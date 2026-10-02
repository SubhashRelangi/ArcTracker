package com.subhashrelangi.arctracker.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.subhashrelangi.arctracker.data.CategoryVisuals
import com.subhashrelangi.arctracker.data.TransactionCategory

/**
 * Modal dialog for selecting a transaction category.
 */
@Composable
fun CategoryPickerDialog(
    categories: List<TransactionCategory>,
    currentCategoryId: String?,
    onCategorySelected: (TransactionCategory) -> Unit,
    onDismissRequest: () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF131620),
            border = BorderStroke(1.dp, Color(0xFF22283A)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Category",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories, key = { it.id }) { cat ->
                        val isSelected = cat.id == currentCategoryId
                        val catColor = CategoryVisuals.getColor(cat.colorKey)
                        val catIcon = CategoryVisuals.getIcon(cat.iconKey)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF1F2637) else Color(0xFF161A26),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF3B82F6) else Color(0xFF202636)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCategorySelected(cat)
                                    onDismissRequest()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(catColor.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = catIcon,
                                        contentDescription = null,
                                        tint = catColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = cat.name,
                                    color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF3B82F6))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
