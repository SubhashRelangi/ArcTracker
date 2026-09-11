package com.example.arctracker.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.util.UUID

data class IgnoreRule(
    val id: String,
    val type: String, // "Keyword", "Sender", "Pattern"
    val matchType: String, // "Contains", "Exact match", "Starts with"
    val value: String
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("id", id)
            put("type", type)
            put("matchType", matchType)
            put("value", value)
        }.toString()
    }

    companion object {
        fun fromJson(json: String): IgnoreRule {
            val obj = JSONObject(json)
            return IgnoreRule(
                id = obj.getString("id"),
                type = obj.getString("type"),
                matchType = obj.getString("matchType"),
                value = obj.getString("value")
            )
        }
    }
}

@Composable
fun IgnoreRulesScreen() {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE) }
    
    var isEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("ignore_rules_enabled", true)) }
    
    var rules by remember { 
        mutableStateOf(
            (sharedPrefs.getStringSet("ignore_rules", emptySet()) ?: emptySet())
                .map { IgnoreRule.fromJson(it) }
                .sortedBy { it.value }
        )
    }

    var selectedType by remember { mutableStateOf("Keyword") }
    var selectedMatchType by remember { mutableStateOf("Contains") }
    var inputValue by remember { mutableStateOf("") }

    val types = listOf("Keyword", "Sender", "Pattern")
    val matchTypes = listOf("Contains", "Exact match", "Starts with")

    fun saveRules(newRules: List<IgnoreRule>) {
        rules = newRules
        sharedPrefs.edit().putStringSet("ignore_rules", newRules.map { it.toJson() }.toSet()).apply()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFBF8FF)) // Light lavender background
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Add keywords, senders or patterns to ignore certain notifications. Transactions matching these rules will not be recorded.",
            fontSize = 13.sp,
            color = Color(0xFF757575),
            lineHeight = 18.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Enable/Disable Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFF3E5F5))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp).background(Color(0xFFF3E5F5), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = null, tint = Color(0xFF673AB7))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ignore Filter is enabled", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E1E1E))
                    Text("Notifications matching the rules below will be skipped.", fontSize = 12.sp, color = Color(0xFF757575), lineHeight = 16.sp)
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { 
                        isEnabled = it 
                        sharedPrefs.edit().putBoolean("ignore_rules_enabled", it).apply()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF673AB7))
                )
            }
        }

        // Add Rule Section
        Text("Add Ignore Rule", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E1E1E), modifier = Modifier.padding(bottom = 8.dp))
        
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            types.forEach { type ->
                val selected = selectedType == type
                Surface(
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) Color(0xFFEDE7F6) else Color.White,
                    border = BorderStroke(1.dp, if (selected) Color(0xFFD1C4E9) else Color(0xFFE0E0E0)),
                    onClick = { 
                        selectedType = type 
                        if (type == "Pattern") selectedMatchType = "Contains"
                    }
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        val icon = when(type) {
                            "Keyword" -> Icons.Default.TextFields
                            "Sender" -> Icons.Default.PersonOutline
                            else -> Icons.Default.Code
                        }
                        Icon(icon, contentDescription = null, tint = if (selected) Color(0xFF673AB7) else Color(0xFF757575), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(type, fontSize = 12.sp, color = if (selected) Color(0xFF673AB7) else Color(0xFF757575), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        OutlinedTextField(
            value = inputValue,
            onValueChange = { inputValue = it },
            placeholder = { Text("Enter keyword (e.g. cashback, reward)", fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF673AB7),
                unfocusedBorderColor = Color(0xFFE0E0E0),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )

        if (selectedType != "Pattern") {
            Text("Choose type", fontSize = 13.sp, color = Color(0xFF1E1E1E), modifier = Modifier.padding(bottom = 8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                matchTypes.forEach { matchType ->
                    val selected = selectedMatchType == matchType
                    Surface(
                        modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) Color(0xFFEDE7F6) else Color.White,
                        border = BorderStroke(1.dp, if (selected) Color(0xFFD1C4E9) else Color(0xFFE0E0E0)),
                        onClick = { selectedMatchType = matchType }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(matchType, fontSize = 12.sp, color = if (selected) Color(0xFF673AB7) else Color(0xFF757575), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                if (inputValue.isNotBlank()) {
                    val newRule = IgnoreRule(UUID.randomUUID().toString(), selectedType, selectedMatchType, inputValue.trim())
                    saveRules(rules + newRule)
                    inputValue = ""
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E57C2))
        ) {
            Text("Add Rule", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Ignored Rules (${rules.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E1E1E), modifier = Modifier.padding(bottom = 8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(rules) { rule ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFF3E5F5))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val iconBg = Color(0xFFEDE7F6)
                        val iconColor = Color(0xFF673AB7)
                        Box(
                            modifier = Modifier.size(40.dp).background(iconBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val icon = when(rule.type) {
                                "Keyword" -> Icons.Default.TextFields
                                "Sender" -> Icons.Default.PersonOutline
                                else -> Icons.Default.Code
                            }
                            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.value, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E1E1E))
                            Text("${rule.type} • ${rule.matchType}", fontSize = 12.sp, color = Color(0xFF757575))
                        }
                        
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color(0xFF757575))
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(Color.White)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Delete", color = Color(0xFFD32F2F)) },
                                    onClick = {
                                        showMenu = false
                                        saveRules(rules.filter { it.id != rule.id })
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                // Tips section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F5FF)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFEDE7F6))
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFF673AB7), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Tips", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF673AB7))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• Add common promotional keywords like \"cashback\", \"reward\", \"offer\".", fontSize = 12.sp, color = Color(0xFF757575))
                            Text("• Ignore senders like your bank's marketing numbers.", fontSize = 12.sp, color = Color(0xFF757575))
                            Text("• Use regex for advanced filtering (e.g. .*OTP.*).", fontSize = 12.sp, color = Color(0xFF757575))
                        }
                    }
                }
            }
        }
    }
}
