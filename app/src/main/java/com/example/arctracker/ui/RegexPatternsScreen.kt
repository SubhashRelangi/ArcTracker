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
import androidx.compose.material.icons.outlined.*
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
fun RegexPatternsScreen(
    onAddPattern: (String) -> Unit,
    onEditPattern: (RegexRule) -> Unit
) {
    val context = LocalContext.current
    var rules by remember { mutableStateOf(RegexPatternsManager.getRules(context)) }
    var ruleToTest by remember { mutableStateOf<RegexRule?>(null) }
    
    val categories = listOf("Amount", "Name / Merchant", "Type (Debit/Credit)", "Others")
    var selectedCategory by remember { mutableStateOf(categories[0]) }

    val filteredRules = rules.filter { 
        it.category == selectedCategory || (selectedCategory == "Others" && !it.isSystem)
    }.sortedBy { it.priority }

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
                    },
                    onEdit = { onEditPattern(rule) },
                    onTest = { ruleToTest = rule },
                    onDelete = {
                        RegexPatternsManager.deleteRule(context, rule.id)
                        rules = RegexPatternsManager.getRules(context)
                    }
                )
            }
            
            item {
                Button(
                    onClick = { onAddPattern(selectedCategory) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = purpleColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Custom Pattern")
                }
            }
            
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
                            imageVector = Icons.Outlined.Lightbulb,
                            contentDescription = "Tips",
                            tint = purpleColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Tips",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = purpleColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "• Use case-insensitive matching (?i)\n" +
                                       "• Add multiple keywords separated by | (OR)\n" +
                                       "• Test your patterns with real messages\n" +
                                       "• Keep patterns specific to avoid false matches",
                                fontSize = 12.sp,
                                color = subtitleColor,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (ruleToTest != null) {
        TestRuleDialog(rule = ruleToTest!!, onDismiss = { ruleToTest = null })
    }
}

@Composable
fun TestRuleDialog(rule: RegexRule, onDismiss: () -> Unit) {
    val sampleText = when(rule.id) {
        "sys_amount_1" -> "Your A/c no. ****1234 is debited with Rs. 500.00 on 29-08-2025 at GOOGLE PAY."
        "sys_amount_2" -> "Rs. 1,200.00 was debited from your account XX9876 on 01-09-2025. Info: POS transaction."
        "sys_merchant_1" -> "Paid Rs. 500 to Flipkart via UPI. TxnId: 123456"
        "sys_merchant_2" -> "Transaction at Starbucks for Rs. 350.00 is successful."
        "sys_type_debit" -> "Your account is debited by Rs 250."
        "sys_type_credit" -> "Your account is credited with Rs 1,000."
        else -> "Enter test message here to test your pattern: ${rule.name}"
    }
    
    val regex = try { Regex(rule.pattern) } catch(e: Exception) { null }
    val match = regex?.find(sampleText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Test Case Output", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column {
                if (match != null) {
                    Text("Result (1 match)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.padding(bottom = 8.dp))
                    
                    val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
                        append(sampleText.substring(0, match.range.first))
                        withStyle(style = androidx.compose.ui.text.SpanStyle(background = Color(0xFFE8F5E9), color = Color(0xFF2E7D32))) {
                            append(match.value)
                        }
                        append(sampleText.substring(match.range.last + 1))
                    }
                    
                    Text(annotatedString, fontSize = 12.sp, color = textColor, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 12.dp))
                    
                    val extracted = if (match.groups.size > 1) match.groupValues[1] else match.value
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Extracted Data", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.background(Color(0xFFE8F5E9), RoundedCornerShape(16.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(extracted, color = Color(0xFF2E7D32), fontSize = 12.sp)
                        }
                    }
                } else {
                    Text("Result (0 matches)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(sampleText, fontSize = 12.sp, color = textColor)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss, // For now test again just dismisses or could reset state
                colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
            ) {
                Text("Test Again")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, borderColor)
            ) {
                Text("Back", color = textColor)
            }
        },
        containerColor = Color.White
    )
}

@Composable
fun RegexRuleCard(
    rule: RegexRule, 
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

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
                val iconBgColor = when (rule.iconType) {
                    "arrow_downward" -> Color(0xFFFFEBEE)
                    "arrow_upward" -> Color(0xFFE8F5E9)
                    else -> lightPurpleColor
                }
                
                val iconTintColor = when (rule.iconType) {
                    "arrow_downward" -> Color(0xFFD32F2F)
                    "arrow_upward" -> Color(0xFF388E3C)
                    else -> purpleColor
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(iconBgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    when (rule.iconType) {
                        "upi" -> Text("UPI", color = iconTintColor, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 12.sp)
                        "bank" -> Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = iconTintColor, modifier = Modifier.size(20.dp))
                        "wallet" -> Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = iconTintColor, modifier = Modifier.size(20.dp))
                        "storefront" -> Icon(Icons.Filled.Storefront, contentDescription = null, tint = iconTintColor, modifier = Modifier.size(20.dp))
                        "person" -> Icon(Icons.Filled.PersonOutline, contentDescription = null, tint = iconTintColor, modifier = Modifier.size(20.dp))
                        "arrow_downward" -> Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = iconTintColor, modifier = Modifier.size(20.dp))
                        "arrow_upward" -> Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = iconTintColor, modifier = Modifier.size(20.dp))
                        else -> Icon(Icons.Filled.Add, contentDescription = null, tint = iconTintColor, modifier = Modifier.size(20.dp))
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
                
                Box {
                    IconButton(onClick = { expanded = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = textColor, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                expanded = false
                                onEdit()
                            }
                        )
                        if (!rule.isSystem) {
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color(0xFFD32F2F)) },
                                onClick = {
                                    expanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
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
                        .clickable { onTest() }
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
                        .clickable { onEdit() }
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
