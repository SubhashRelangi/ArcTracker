package com.subhashrelangi.arctracker.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.window.Dialog
import com.subhashrelangi.arctracker.settings.MonitoringSettingsRepository
import com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader
import com.subhashrelangi.arctracker.utils.IgnoreRule
import com.subhashrelangi.arctracker.utils.IgnoreRulesManager
import java.util.UUID

@Composable
fun IgnoreRulesScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE) }
    val settingsRepo = remember { MonitoringSettingsRepository.getInstance(context) }
    val isDemoMode = settingsRepo.isDemoMode()

    var isEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("ignore_rules_enabled", true)) }

    // Load purely real data from backend when not in demo mode; load rich demo dataset in demo mode
    var rules by remember(isDemoMode) {
        val loaded = IgnoreRulesManager.getRules(context)
        if (isDemoMode) {
            val demoDataset = listOf(
                IgnoreRule("demo_otp", "Keyword", "Contains", "OTP", isSystem = true),
                IgnoreRule("demo_cashback", "Keyword", "Contains", "Cashback credited", isSystem = false),
                IgnoreRule("demo_airtel", "Sender", "Exact", "VM-AIRTEL", isSystem = false),
                IgnoreRule("demo_bal", "Keyword", "Contains", "Balance enquiry", isSystem = true),
                IgnoreRule("demo_promo", "Sender", "Starts With", "AX-PROMO*", isSystem = false),
                IgnoreRule("demo_loan", "Pattern", "Regex", "loan approval.*pr...", isSystem = false)
            )
            mutableStateOf(demoDataset)
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

    // Dialog state for attractive deletion
    var deletingRule by remember { mutableStateOf<IgnoreRule?>(null) }

    fun saveRuleList(updated: List<IgnoreRule>) {
        rules = updated
        if (!isDemoMode) {
            IgnoreRulesManager.saveRules(context, updated)
        }
    }

    val silencedCount = if (isDemoMode) 42 else sharedPrefs.getInt("silenced_sms_count", 0)

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
                // Universal Settings Sub-Page Header
                ArcTrackerHeader(
                    title = "Ignore Rules",
                    subtitle = "Filter & Noise Suppression",
                    onBackClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .statusBarsPadding(),
                    horizontalPadding = 16.dp,
                    actions = {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = "Security Vault",
                                tint = Color(0xFF055B38),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Master Noise Filter Card (ArcTracker styled)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color(0xFFECFDF5), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = Color(0xFF055B38),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Master Noise Filter",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isEnabled) "Active & Suppressing Noise" else "Suppression Engine Paused",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isEnabled) Color(0xFF055B38) else Color(0xFF64748B)
                            )
                        }

                        // ArcTracker Custom Switch
                        IgnoreRulesMockSwitch(
                            checked = isEnabled,
                            onCheckedChange = {
                                isEnabled = it
                                sharedPrefs.edit().putBoolean("ignore_rules_enabled", it).apply()
                            }
                        )
                    }

                    Text(
                        text = "Automatically suppresses OTPs, balance queries, promotional blasts, and carrier noise before ledger parsing.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 16.sp
                    )

                    // Compact Telemetry Stats Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(modifier = Modifier.size(6.dp)) {
                                drawCircle(color = if (isEnabled) Color(0xFF10B981) else Color(0xFF94A3B8))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${rules.size} Active Rules",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                        }

                        Text("•", color = Color(0xFFCBD5E1))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.MarkChatRead,
                                contentDescription = null,
                                tint = Color(0xFF055B38),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))) {
                                        append("$silencedCount SMS")
                                    }
                                    withStyle(SpanStyle(color = Color(0xFF64748B))) {
                                        append(" silenced")
                                    }
                                },
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // 2. New Ignore Filter Card (ArcTracker Quick Creator)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isEnabled) 1f else 0.5f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = Color(0xFF055B38),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Ignore Filter",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Box(
                            modifier = Modifier
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Rule Engine",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    // Target Type Segmented Pills
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "FILTER TARGET",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
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
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color(0xFF055B38) else Color(0xFFF1F5F9))
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
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) Color.White else Color(0xFF475569)
                                    )
                                }
                            }
                        }
                    }

                    // Match Logic Segmented Pills (only if not Regex)
                    if (selectedTargetType != "Regex") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "MATCH LOGIC",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
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
                                            .height(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color(0xFFECFDF5) else Color(0xFFF8FAFC))
                                            .border(
                                                BorderStroke(
                                                    1.dp,
                                                    if (isSelected) Color(0xFF10B981) else Color(0xFFE2E8F0)
                                                ),
                                                CircleShape
                                            )
                                            .clickable(enabled = isEnabled) {
                                                selectedMatchLogic = logic
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = logic,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color(0xFF055B38) else Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Search / Rule Value Input Box
                    val placeholderText = when (selectedTargetType) {
                        "Sender ID" -> "e.g. VM-AIRTEL, HDFCBK-PROMO"
                        "Regex" -> "e.g. .*(OTP|verification code).*"
                        else -> "e.g. OTP, cashback offer, reward point"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(14.dp))
                            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = Color(0xFF055B38),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (inputRuleValue.isEmpty()) {
                                Text(
                                    text = placeholderText,
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            BasicTextField(
                                value = inputRuleValue,
                                onValueChange = { inputRuleValue = it },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = isEnabled,
                                textStyle = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF0F172A),
                                    fontFamily = if (selectedTargetType == "Regex") FontFamily.Monospace else FontFamily.Default
                                ),
                                cursorBrush = SolidColor(Color(0xFF055B38)),
                                singleLine = true
                            )
                        }

                        if (inputRuleValue.isNotEmpty()) {
                            IconButton(
                                onClick = { inputRuleValue = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Stadium Pill Add Filter Rule Button
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
                            .height(46.dp),
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
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Ignore Rule",
                            fontSize = 13.sp,
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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Section Title Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active Rules",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )

                    Box(
                        modifier = Modifier
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${filteredRules.size} active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // Filter Category Stadium Chips
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
                                .background(if (isSelected) Color(0xFFDCFCE7) else Color.White)
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF055B38) else Color(0xFFE2E8F0)
                                    ),
                                    CircleShape
                                )
                                .clickable { selectedFilterCategory = categoryKey }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFF055B38) else Color(0xFF475569)
                            )
                        }
                    }
                }

                if (filteredRules.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FilterAltOff,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No rules match this category",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Add a new rule above to silence specific notifications.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                } else {
                    // Elevated ArcTracker Rule Cards
                    filteredRules.forEach { rule ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isEnabled) 1f else 0.5f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Category Icon Squircle
                                val isSender = rule.type.equals("Sender", ignoreCase = true)
                                val isRegex = rule.type.equals("Pattern", ignoreCase = true) || rule.type.equals("Regex", ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            when {
                                                isSender -> Color(0xFFEFF6FF)
                                                isRegex -> Color(0xFFFAF5FF)
                                                else -> Color(0xFFECFDF5)
                                            },
                                            RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        isSender -> Icon(
                                            imageVector = Icons.Outlined.Send,
                                            contentDescription = null,
                                            tint = Color(0xFF2563EB),
                                            modifier = Modifier.size(17.dp)
                                        )
                                        isRegex -> Text(
                                            text = "(.*)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF7C3AED),
                                            fontFamily = FontFamily.Monospace
                                        )
                                        else -> Icon(
                                            imageVector = Icons.Outlined.TextFields,
                                            contentDescription = null,
                                            tint = Color(0xFF055B38),
                                            modifier = Modifier.size(18.dp)
                                        )
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
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontFamily = if (isRegex) FontFamily.Monospace else FontFamily.Default
                                        )

                                        if (rule.isSystem) {
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "SYSTEM",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF64748B)
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

                                    val matchLabel = if (isRegex) {
                                        "Type: Pattern • Match: Regex"
                                    } else {
                                        "Type: ${rule.type} • Match: ${rule.matchType}"
                                    }

                                    Text(
                                        text = matchLabel,
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                // Soft circular delete button
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF8FAFC))
                                        .clickable(enabled = isEnabled) { deletingRule = rule },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Auditor's Best Practice Notice (Amber Callout)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFFBEB), RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFFFEF3C7), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier
                        .size(18.dp)
                        .padding(top = 1.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auditor's Best Practice",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF92400E)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildAnnotatedString {
                            append("Avoid using generic keywords like ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF92400E))) {
                                append("debited")
                            }
                            append(" or ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF92400E))) {
                                append("credited")
                            }
                            append(". Doing so will silence genuine financial SMS receipts from landing in your ledger.")
                        },
                        fontSize = 11.sp,
                        color = Color(0xFF78350F),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ArcTracker Custom Delete Confirmation Dialog
    deletingRule?.let { rule ->
        Dialog(onDismissRequest = { deletingRule = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFFFEE2E2), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Delete Ignore Rule?",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Are you sure you want to remove this suppression rule?",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Preview Card of the rule being deleted
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = rule.value,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (rule.isSystem) Color(0xFFF1F5F9) else Color(0xFFDCFCE7),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (rule.isSystem) "SYSTEM" else "CUSTOM",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rule.isSystem) Color(0xFF64748B) else Color(0xFF055B38)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Type: ${rule.type} • Match: ${rule.matchType}",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action Buttons: Pill stadium format
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { deletingRule = null },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF1F5F9),
                                contentColor = Color(0xFF374151)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = {
                                saveRuleList(rules.filter { it.id != rule.id })
                                deletingRule = null
                            },
                            modifier = Modifier
                                .weight(1.2f)
                                .height(44.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC2626),
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Delete Rule",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom ArcTracker Switch matching the app's clean aesthetic:
 * Forest green background when checked, white thumb containing dark green checkmark.
 */
@Composable
fun IgnoreRulesMockSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        label = "switchThumbOffset"
    )

    Box(
        modifier = modifier
            .width(52.dp)
            .height(30.dp)
            .clip(CircleShape)
            .background(if (checked) Color(0xFF055B38) else Color(0xFFCBD5E1))
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(26.dp)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF055B38),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
