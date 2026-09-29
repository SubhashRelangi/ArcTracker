package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.utils.RegexPatternsManager
import com.subhashrelangi.arctracker.utils.RegexRule
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRegexPatternScreen(
    ruleId: String?,
    defaultCategory: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    // Determine if we are editing an existing rule or creating a new one
    val initialRule = remember(ruleId) {
        if (ruleId.isNullOrEmpty()) {
            RegexRule(
                id = UUID.randomUUID().toString(),
                name = "",
                description = "",
                category = defaultCategory,
                pattern = "",
                isSystem = false,
                iconType = "custom",
                priority = 99
            )
        } else {
            RegexPatternsManager.getRuleById(context, ruleId) ?: return@remember null
        }
    }
    
    if (initialRule == null) {
        onBack()
        return
    }

    var name by remember { mutableStateOf(initialRule.name) }
    var description by remember { mutableStateOf(initialRule.description) }
    var pattern by remember { mutableStateOf(initialRule.pattern) }
    var testMessage by remember { mutableStateOf(initialRule.testMessage ?: "") }
    var showTestDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F9FB))
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, borderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconBgColor = when (initialRule.iconType) {
                        "arrow_downward" -> Color(0xFFFFEBEE)
                        "arrow_upward" -> Color(0xFFE8F5E9)
                        else -> lightPurpleColor
                    }
                    val iconTintColor = when (initialRule.iconType) {
                        "arrow_downward" -> Color(0xFFD32F2F)
                        "arrow_upward" -> Color(0xFF388E3C)
                        else -> purpleColor
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(iconBgColor, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        when (initialRule.iconType) {
                            "upi" -> Text("UPI", color = iconTintColor, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 14.sp)
                            "bank" -> Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = iconTintColor, modifier = Modifier.size(24.dp))
                            "wallet" -> Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = iconTintColor, modifier = Modifier.size(24.dp))
                            "storefront" -> Icon(Icons.Filled.Storefront, contentDescription = null, tint = iconTintColor, modifier = Modifier.size(24.dp))
                            "person" -> Icon(Icons.Filled.PersonOutline, contentDescription = null, tint = iconTintColor, modifier = Modifier.size(24.dp))
                            "arrow_downward" -> Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = iconTintColor, modifier = Modifier.size(24.dp))
                            "arrow_upward" -> Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = iconTintColor, modifier = Modifier.size(24.dp))
                            else -> Icon(Icons.Filled.Add, contentDescription = null, tint = iconTintColor, modifier = Modifier.size(24.dp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text("Pattern Type", fontSize = 11.sp, color = subtitleColor, fontWeight = FontWeight.Medium)
                        Text(if (name.isEmpty()) "Custom Pattern" else name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)
                        if (description.isNotEmpty()) {
                            Text(description, fontSize = 11.sp, color = subtitleColor)
                        }
                    }
                }
            }

            // Pattern Name
            Text("Pattern Name", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor, modifier = Modifier.padding(bottom = 4.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                enabled = !initialRule.isSystem,
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = borderColor,
                    unfocusedContainerColor = if (initialRule.isSystem) Color(0xFFF5F5F5) else Color.White,
                    focusedContainerColor = Color.White
                )
            )

            // Description
            Text("Description", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor, modifier = Modifier.padding(bottom = 4.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                enabled = !initialRule.isSystem,
                minLines = 2,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = borderColor,
                    unfocusedContainerColor = if (initialRule.isSystem) Color(0xFFF5F5F5) else Color.White,
                    focusedContainerColor = Color.White
                )
            )

            // Regex Pattern
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Regex Pattern", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor)
                Text("Cheat Sheet", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = purpleColor, modifier = Modifier.clickable { /* TODO */ })
            }
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = borderColor,
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(pattern))
                            android.widget.Toast.makeText(context, "Pattern copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy",
                            tint = purpleColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )

            // Test Pattern Section
            Text("Test Pattern", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.padding(bottom = 8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, borderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Enter a sample message", fontSize = 11.sp, color = subtitleColor, modifier = Modifier.padding(bottom = 4.dp))
                    OutlinedTextField(
                        value = testMessage,
                        onValueChange = { testMessage = it },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = borderColor,
                            focusedBorderColor = purpleColor,
                            unfocusedContainerColor = Color.White,
                            focusedContainerColor = Color.White
                        )
                    )
                    Text("${testMessage.length}/500", fontSize = 10.sp, color = subtitleColor, modifier = Modifier.align(Alignment.End).padding(top = 4.dp, bottom = 12.dp))
                    
                    Button(
                        onClick = { showTestDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = purpleColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Test", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Pattern")
                    }
                }
            }
        }
        
        if (showTestDialog) {
            val tempRule = initialRule.copy(
                name = name,
                pattern = pattern,
                testMessage = testMessage.ifEmpty { null }
            )
            TestRuleDialog(rule = tempRule, onDismiss = { showTestDialog = false })
        }
        
        // Bottom Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (!initialRule.isSystem && !ruleId.isNullOrEmpty()) {
                OutlinedButton(
                    onClick = {
                        RegexPatternsManager.deleteRule(context, initialRule.id)
                        onBack()
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF5350)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, borderColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor)
            ) {
                Text("Cancel", fontWeight = FontWeight.Medium)
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Button(
                onClick = {
                    val newRule = initialRule.copy(
                        name = name,
                        description = description,
                        pattern = pattern,
                        testMessage = testMessage.ifEmpty { null }
                    )
                    if (ruleId.isNullOrEmpty()) {
                        RegexPatternsManager.addRule(context, newRule)
                    } else {
                        RegexPatternsManager.updateRule(context, newRule)
                    }
                    onBack()
                },
                modifier = Modifier.weight(1.5f).height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
            ) {
                Text("Save Changes", fontWeight = FontWeight.Medium)
            }
        }
    }
}
