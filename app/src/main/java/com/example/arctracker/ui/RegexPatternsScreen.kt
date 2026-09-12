package com.example.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arctracker.utils.RegexPatternsManager
import com.example.arctracker.utils.RegexRule

@Composable
fun RegexPatternsScreen() {
    val context = LocalContext.current
    var rules by remember { mutableStateOf(RegexPatternsManager.getRules(context)) }
    
    val categories = listOf("Amount", "Name / Merchant", "Type (Debit/Credit)", "Others")
    var selectedCategory by remember { mutableStateOf(categories[0]) }

    val filteredRules = rules.filter { it.category == selectedCategory }.sortedBy { it.priority }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9FB))
    ) {
        // Categories row
        ScrollableTabRow(
            selectedTabIndex = categories.indexOf(selectedCategory),
            containerColor = Color.Transparent,
            contentColor = purpleColor,
            edgePadding = 16.dp,
            indicator = {},
            divider = {}
        ) {
            categories.forEach { category ->
                val isSelected = selectedCategory == category
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp, top = 16.dp, bottom = 16.dp)
                        .background(
                            if (isSelected) purpleColor else lightPurpleColor,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedCategory = category }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = category,
                        color = if (isSelected) Color.White else textColor,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F5FF)),
                    border = BorderStroke(1.dp, Color(0xFFEDE7F6)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Info",
                            tint = purpleColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "These patterns are used to extract transaction details from SMS and notifications. Change only if you know what you are doing.",
                            fontSize = 12.sp,
                            color = subtitleColor,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            items(filteredRules) { rule ->
                RegexRuleCard(
                    rule = rule,
                    onToggle = { isActive ->
                        val updated = rules.map { if (it.id == rule.id) it.copy(isActive = isActive) else it }
                        rules = updated
                        RegexPatternsManager.saveRules(context, updated)
                    }
                )
            }
            
            item {
                Button(
                    onClick = { /* TODO Add custom pattern */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = purpleColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Custom Pattern")
                }
            }
        }
    }
}

@Composable
fun RegexRuleCard(rule: RegexRule, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(lightPurpleColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    when (rule.iconType) {
                        "upi" -> Text("UPI", color = purpleColor, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 12.sp)
                        "bank" -> Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = purpleColor, modifier = Modifier.size(20.dp))
                        "wallet" -> Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = purpleColor, modifier = Modifier.size(20.dp))
                        "storefront" -> Icon(Icons.Filled.Storefront, contentDescription = null, tint = purpleColor, modifier = Modifier.size(20.dp))
                        "person" -> Icon(Icons.Filled.PersonOutline, contentDescription = null, tint = purpleColor, modifier = Modifier.size(20.dp))
                        else -> Icon(Icons.Filled.Add, contentDescription = null, tint = purpleColor, modifier = Modifier.size(20.dp))
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = rule.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)
                    Text(text = rule.description, fontSize = 11.sp, color = subtitleColor)
                }
                
                Switch(
                    checked = rule.isActive,
                    onCheckedChange = onToggle,
                    modifier = Modifier.scale(0.7f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = purpleColor,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFE0E0E0),
                        uncheckedBorderColor = Color.Transparent
                    )
                )
                
                IconButton(onClick = {}, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = textColor, modifier = Modifier.size(20.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Pattern Field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF9F9FB), RoundedCornerShape(8.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rule.pattern,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = textColor,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy",
                    tint = purpleColor,
                    modifier = Modifier.size(16.dp).clickable { /* TODO copy */ }
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Priority Badge
                Box(
                    modifier = Modifier
                        .background(Color(0xFFE8F5E9), RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Priority ${rule.priority}", color = Color(0xFF2E7D32), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Matches Badge
                Box(
                    modifier = Modifier
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("${rule.matches} matches", color = subtitleColor, fontSize = 10.sp)
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Action Buttons
                Row(
                    modifier = Modifier
                        .background(lightPurpleColor, RoundedCornerShape(8.dp))
                        .clickable { }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Test", tint = purpleColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test", color = purpleColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Row(
                    modifier = Modifier
                        .background(lightPurpleColor, RoundedCornerShape(8.dp))
                        .clickable { }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = purpleColor, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", color = purpleColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
