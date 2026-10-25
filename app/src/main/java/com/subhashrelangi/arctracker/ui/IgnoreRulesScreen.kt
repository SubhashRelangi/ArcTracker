package com.subhashrelangi.arctracker.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.utils.IgnoreRule
import com.subhashrelangi.arctracker.utils.IgnoreRulesManager
import java.util.UUID

@Composable
fun IgnoreRulesScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE) }

    var isEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("ignore_rules_enabled", true)) }

    var rules by remember {
        val loaded = IgnoreRulesManager.getRules(context)
        if (loaded.size <= 7) {
            // Seed sample rules matching reference mockup if only system defaults exist
            val seeded = listOf(
                IgnoreRule("r_otp", "Keyword", "Contains", "OTP", isSystem = true),
                IgnoreRule("r_cashback", "Keyword", "Contains", "Cashback credited", isSystem = false),
                IgnoreRule("r_airtel", "Sender", "Exact", "VM-AIRTEL", isSystem = false),
                IgnoreRule("r_bal", "Keyword", "Contains", "Balance enquiry", isSystem = true),
                IgnoreRule("r_promo", "Sender", "Starts With", "AX-PROMO*", isSystem = false),
                IgnoreRule("r_loan", "Pattern", "Regex", "loan approval.*pr...", isSystem = false)
            ) + loaded.filter { it.value.lowercase() !in listOf("otp", "cashback offer", "available balance") }
            mutableStateOf(seeded)
        } else {
            mutableStateOf(loaded)
        }
    }

    // Filter type tabs & input
    var selectedTargetType by remember { mutableStateOf("Keyword") } // "Keyword", "Sender ID", "Regex"
    var selectedMatchLogic by remember { mutableStateOf("Contains") } // "Contains", "Exact", "Starts With"
    var inputRuleValue by remember { mutableStateOf("") }

    // Active filters tab selection
    var selectedFilterCategory by remember { mutableStateOf("All") } // "All", "Keywords", "Senders", "Regex"

    // Dialogs
    var deletingRule by remember { mutableStateOf<IgnoreRule?>(null) }

    fun saveRuleList(updated: List<IgnoreRule>) {
        rules = updated
        IgnoreRulesManager.saveRules(context, updated)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = Color.White,
                shadowElevation = 0.5.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onNavigateBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF111827),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Ignore Rules",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )

                            // ACTIVE Badge
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFE6F4EA), CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF137333)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Filter & Noise Suppression",
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280)
                        )
                    }

                    // Shield icon
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = "Protected",
                        tint = Color(0xFF0F9D58),
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Circular Profile Avatar Box
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE5E7EB)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = "Profile",
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Master Noise Filter Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Master Noise Filter",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Switch(
                            checked = isEnabled,
                            onCheckedChange = {
                                isEnabled = it
                                sharedPrefs.edit().putBoolean("ignore_rules_enabled", it).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF055B38),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFE2E8F0),
                                uncheckedBorderColor = Color.Transparent
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Automatically suppresses OTPs, balance queries, promotional blasts, and carrier noise before ledger parsing.",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280),
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stats pill container
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF9FAFB)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF10B981), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${rules.size} Rules Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Icon(
                                imageVector = Icons.Outlined.MarkChatRead,
                                contentDescription = null,
                                tint = Color(0xFF055B38),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF111827))) {
                                        append("42 SMS")
                                    }
                                    append(" silenced this month")
                                },
                                fontSize = 11.sp,
                                color = Color(0xFF4B5563)
                            )
                        }
                    }
                }
            }

            // 2. New Ignore Filter Card (Precision Match)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isEnabled) 1f else 0.5f),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddCircleOutline,
                            contentDescription = null,
                            tint = Color(0xFF055B38),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New Ignore Filter",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            text = "PRECISION MATCH",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6B7280),
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Target Type Label & Segmented Buttons
                    Text(
                        text = "TARGET TYPE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6B7280),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Keyword", "Sender ID", "Regex").forEach { type ->
                            val isSelected = selectedTargetType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0xFFDCFCE7) else Color(0xFFF1F5F9))
                                    .clickable(enabled = isEnabled) {
                                        selectedTargetType = type
                                        if (type == "Regex") {
                                            selectedMatchLogic = "Regex"
                                        } else if (selectedMatchLogic == "Regex") {
                                            selectedMatchLogic = "Contains"
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = type,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF055B38) else Color(0xFF4B5563)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Match Logic Label & Segmented Buttons
                    Text(
                        text = "MATCH LOGIC",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6B7280),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Contains", "Exact", "Starts With").forEach { logic ->
                            val isSelected = selectedMatchLogic == logic
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0xFFDCFCE7) else Color(0xFFF1F5F9))
                                    .clickable(enabled = isEnabled && selectedTargetType != "Regex") {
                                        selectedMatchLogic = logic
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = logic,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF055B38) else Color(0xFF4B5563)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Text Field for Rule Value
                    val placeholderText = when (selectedTargetType) {
                        "Sender ID" -> "e.g. VM-AIRTEL, HDFCBK-PROMO"
                        "Regex" -> "e.g. .*(OTP|verification code).*"
                        else -> "e.g. OTP, cashback offer, reward point"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, Color(0xFFE5E7EB)), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (inputRuleValue.isEmpty()) {
                            Text(
                                text = placeholderText,
                                fontSize = 13.sp,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                        BasicTextField(
                            value = inputRuleValue,
                            onValueChange = { inputRuleValue = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isEnabled,
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF111827)
                            ),
                            cursorBrush = SolidColor(Color(0xFF055B38)),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Add Filter Rule Button
                    Button(
                        onClick = {
                            if (inputRuleValue.isNotBlank()) {
                                val typeToSave = when (selectedTargetType) {
                                    "Sender ID" -> "Sender"
                                    "Regex" -> "Pattern"
                                    else -> "Keyword"
                                }
                                val newRule = IgnoreRule(
                                    id = UUID.randomUUID().toString(),
                                    type = typeToSave,
                                    matchType = selectedMatchLogic,
                                    value = inputRuleValue.trim(),
                                    isSystem = false
                                )
                                saveRuleList(listOf(newRule) + rules)
                                inputRuleValue = ""
                            }
                        },
                        enabled = isEnabled && inputRuleValue.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF055B38),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF055B38).copy(alpha = 0.4f),
                            disabledContentColor = Color.White.copy(alpha = 0.8f)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BookmarkAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Filter Rule",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 3. Active Filters Section
            val keywordRules = rules.filter { it.type.equals("Keyword", ignoreCase = true) }
            val senderRules = rules.filter { it.type.equals("Sender", ignoreCase = true) }
            val regexRules = rules.filter { it.type.equals("Pattern", ignoreCase = true) || it.type.equals("Regex", ignoreCase = true) }

            val filteredRules = when (selectedFilterCategory) {
                "Keywords" -> keywordRules
                "Senders" -> senderRules
                "Regex" -> regexRules
                else -> rules
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active Filters",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "Showing All (${filteredRules.size} sample)",
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280)
                    )
                }

                // Filter Category Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "All" to "All (${rules.size})",
                        "Keywords" to "Keywords (${keywordRules.size})",
                        "Senders" to "Senders (${senderRules.size})",
                        "Regex" to "Regex (${regexRules.size})"
                    ).forEach { (categoryKey, label) ->
                        val isSelected = selectedFilterCategory == categoryKey
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) Color(0xFFDCFCE7) else Color(0xFFF1F5F9))
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF055B38).copy(alpha = 0.2f) else Color.Transparent
                                    ),
                                    CircleShape
                                )
                                .clickable { selectedFilterCategory = categoryKey }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFF055B38) else Color(0xFF4B5563)
                            )
                        }
                    }
                }

                // Rule Items Cards
                filteredRules.forEach { rule ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isEnabled) 1f else 0.5f),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Icon Box
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFFF3F4F6), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                when (rule.type.lowercase()) {
                                    "sender" -> {
                                        Icon(
                                            imageVector = Icons.Outlined.Send,
                                            contentDescription = null,
                                            tint = Color(0xFF055B38),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    "pattern", "regex" -> {
                                        Text(
                                            text = "(.*)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF055B38),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = "ab",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF055B38)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = rule.value,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF111827),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (rule.isSystem) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFF3F4F6), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "SYSTEM RULE",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF6B7280)
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFDCFCE7), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "CUSTOM",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF055B38)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                val matchLabel = if (rule.type.equals("Pattern", ignoreCase = true) || rule.type.equals("Regex", ignoreCase = true)) {
                                    "Type: Pattern • Match: Regex"
                                } else {
                                    "Type: ${rule.type} • Match: ${rule.matchType}"
                                }

                                Text(
                                    text = matchLabel,
                                    fontSize = 11.sp,
                                    color = Color(0xFF6B7280)
                                )
                            }

                            // Delete Action Icon
                            IconButton(
                                onClick = { deletingRule = rule },
                                enabled = isEnabled,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = Color(0xFF6B7280),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Auditor's Best Practice Warning Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isEnabled) 1f else 0.6f),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFFFBEB),
                border = BorderStroke(1.dp, Color(0xFFFDE68A))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lightbulb,
                        contentDescription = null,
                        tint = Color(0xFFB45309),
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Auditor's Best Practice",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = buildAnnotatedString {
                                append("Avoid using generic keywords like ")
                                withStyle(
                                    SpanStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF92400E),
                                        background = Color.White
                                    )
                                ) {
                                    append(" debited ")
                                }
                                append(" or ")
                                withStyle(
                                    SpanStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF92400E),
                                        background = Color.White
                                    )
                                ) {
                                    append(" credited ")
                                }
                                append(". Doing so will silence genuine financial SMS receipts from landing in your ledger.")
                            },
                            fontSize = 11.sp,
                            color = Color(0xFF78350F),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }

    // Delete Confirmation Dialog
    deletingRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { deletingRule = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Ignore Rule?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to remove the rule \"${rule.value}\"? Notifications matching this filter will be parsed into your ledger again.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        saveRuleList(rules.filter { it.id != rule.id })
                        deletingRule = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingRule = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
