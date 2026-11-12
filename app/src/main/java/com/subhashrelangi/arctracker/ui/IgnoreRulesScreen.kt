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
import com.subhashrelangi.arctracker.ui.theme.ArcColors
import com.subhashrelangi.arctracker.utils.IgnoreRule
import com.subhashrelangi.arctracker.utils.IgnoreRulesManager
import java.util.UUID

private val slateTitle: Color @Composable @ReadOnlyComposable get() = ArcColors.TextPrimary
private val slateSub: Color @Composable @ReadOnlyComposable get() = ArcColors.TextMuted
private val cardBorder: Color @Composable @ReadOnlyComposable get() = ArcColors.Border
private val lightBg: Color @Composable @ReadOnlyComposable get() = ArcColors.Background
private val cardBg: Color @Composable @ReadOnlyComposable get() = ArcColors.CardBackground
private val highlightSurface: Color @Composable @ReadOnlyComposable get() = ArcColors.SurfaceHighlight

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
                color = lightBg,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(lightBg)
            ) {
                // Universal Settings Sub-Page Header
                ArcTrackerHeader(
                    title = "Ignore Rules",
                    subtitle = "Filter & Noise Suppression",
                    onBackClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(lightBg)
                        .statusBarsPadding(),
                    horizontalPadding = 16.dp,
                    actions = {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(cardBg)
                                .border(BorderStroke(1.dp, cardBorder), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = "Security Vault",
                                tint = ArcColors.Primary,
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
                .background(lightBg)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Master Noise Filter Card (ArcTracker styled)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, cardBorder),
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
                        val shieldBg = if (ArcColors.isDark) Color(0xFF142E24) else Color(0xFFECFDF5)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(shieldBg, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = ArcColors.Primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Master Noise Filter",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = slateTitle
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isEnabled) "Active & Suppressing Noise" else "Suppression Engine Paused",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isEnabled) ArcColors.Primary else slateSub
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
                        color = slateSub,
                        lineHeight = 16.sp
                    )

                    // Compact Telemetry Stats Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(highlightSurface, RoundedCornerShape(12.dp))
                            .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val dotColor = if (isEnabled) ArcColors.Primary else slateSub
                            Canvas(modifier = Modifier.size(6.dp)) {
                                drawCircle(color = dotColor)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${rules.size} Active Rules",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = slateTitle
                            )
                        }

                        Text("•", color = slateSub)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.MarkChatRead,
                                contentDescription = null,
                                tint = ArcColors.Primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = slateTitle)) {
                                        append("$silencedCount SMS")
                                    }
                                    withStyle(SpanStyle(color = slateSub)) {
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
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, cardBorder),
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
                            tint = ArcColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Ignore Filter",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = slateTitle
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Box(
                            modifier = Modifier
                                .background(highlightSurface, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Rule Engine",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = slateSub
                            )
                        }
                    }

                    // Target Type Segmented Pills
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "FILTER TARGET",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = slateSub,
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
                                        .background(if (isSelected) ArcColors.Primary else highlightSurface)
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
                                        color = if (isSelected) Color.White else slateSub
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
                                color = slateSub,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Contains", "Exact", "Starts With").forEach { logic ->
                                    val isSelected = selectedMatchLogic == logic
                                    val logicBg = if (isSelected) {
                                        if (ArcColors.isDark) Color(0xFF142E24) else Color(0xFFECFDF5)
                                    } else highlightSurface
                                    val logicBorder = if (isSelected) Color(0xFF10B981) else cardBorder

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .clip(CircleShape)
                                            .background(logicBg)
                                            .border(
                                                BorderStroke(1.dp, logicBorder),
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
                                            color = if (isSelected) ArcColors.Primary else slateSub
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
                            .background(highlightSurface, RoundedCornerShape(14.dp))
                            .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = ArcColors.Primary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (inputRuleValue.isEmpty()) {
                                Text(
                                    text = placeholderText,
                                    fontSize = 13.sp,
                                    color = slateSub
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
                                    color = slateTitle,
                                    fontFamily = if (selectedTargetType == "Regex") FontFamily.Monospace else FontFamily.Default
                                ),
                                cursorBrush = SolidColor(ArcColors.Primary),
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
                                    tint = slateSub,
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
                            containerColor = ArcColors.Primary,
                            contentColor = Color.White,
                            disabledContainerColor = ArcColors.Primary.copy(alpha = 0.4f),
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
                        color = slateTitle
                    )

                    Box(
                        modifier = Modifier
                            .background(highlightSurface, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${filteredRules.size} active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = slateSub
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
                        val chipBg = if (isSelected) {
                            if (ArcColors.isDark) Color(0xFF142E24) else Color(0xFFDCFCE7)
                        } else cardBg

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(chipBg)
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (isSelected) ArcColors.Primary else cardBorder
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
                                color = if (isSelected) ArcColors.Primary else slateSub
                            )
                        }
                    }
                }

                if (filteredRules.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, cardBorder),
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
                                tint = slateSub,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No rules match this category",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = slateTitle
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Add a new rule above to silence specific notifications.",
                                fontSize = 11.sp,
                                color = slateSub
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
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, cardBorder),
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
                                val squircleBg = when {
                                    isSender -> if (ArcColors.isDark) Color(0xFF172554) else Color(0xFFEFF6FF)
                                    isRegex -> if (ArcColors.isDark) Color(0xFF2E1065) else Color(0xFFFAF5FF)
                                    else -> if (ArcColors.isDark) Color(0xFF142E24) else Color(0xFFECFDF5)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(squircleBg, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        isSender -> Icon(
                                            imageVector = Icons.Outlined.Send,
                                            contentDescription = null,
                                            tint = if (ArcColors.isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                                            modifier = Modifier.size(17.dp)
                                        )
                                        isRegex -> Text(
                                            text = "(.*)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (ArcColors.isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED),
                                            fontFamily = FontFamily.Monospace
                                        )
                                        else -> Icon(
                                            imageVector = Icons.Outlined.TextFields,
                                            contentDescription = null,
                                            tint = ArcColors.Primary,
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
                                            color = slateTitle,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontFamily = if (isRegex) FontFamily.Monospace else FontFamily.Default
                                        )

                                        if (rule.isSystem) {
                                            Box(
                                                modifier = Modifier
                                                    .background(highlightSurface, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "SYSTEM",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = slateSub
                                                )
                                            }
                                        } else {
                                            val customBg = if (ArcColors.isDark) Color(0xFF142E24) else Color(0xFFDCFCE7)
                                            Box(
                                                modifier = Modifier
                                                    .background(customBg, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "CUSTOM",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ArcColors.Primary
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
                                        color = slateSub
                                    )
                                }

                                // Soft circular delete button
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(highlightSurface)
                                        .clickable(enabled = isEnabled) { deletingRule = rule },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = slateSub,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Auditor's Best Practice Notice (Amber Callout)
            val calloutBg = if (ArcColors.isDark) ArcColors.WarningAmberBg else Color(0xFFFFFBEB)
            val calloutBorder = if (ArcColors.isDark) ArcColors.WarningAmberBorder else Color(0xFFFEF3C7)
            val calloutTitle = if (ArcColors.isDark) Color(0xFFFBBF24) else Color(0xFF92400E)
            val calloutText = if (ArcColors.isDark) ArcColors.TextSecondary else Color(0xFF78350F)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(calloutBg, RoundedCornerShape(14.dp))
                    .border(1.dp, calloutBorder, RoundedCornerShape(14.dp))
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
                        color = calloutTitle
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildAnnotatedString {
                            append("Avoid using generic keywords like ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = calloutTitle)) {
                                append("debited")
                            }
                            append(" or ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = calloutTitle)) {
                                append("credited")
                            }
                            append(". Doing so will silence genuine financial SMS receipts from landing in your ledger.")
                        },
                        fontSize = 11.sp,
                        color = calloutText,
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
                color = cardBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(ArcColors.DangerContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = ArcColors.Danger,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Delete Ignore Rule?",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = slateTitle
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Are you sure you want to remove this suppression rule?",
                        fontSize = 12.sp,
                        color = slateSub
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Preview Card of the rule being deleted
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = highlightSurface,
                        border = BorderStroke(1.dp, cardBorder)
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
                                    color = slateTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (rule.isSystem) highlightSurface else (if (ArcColors.isDark) Color(0xFF142E24) else Color(0xFFDCFCE7)),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (rule.isSystem) "SYSTEM" else "CUSTOM",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rule.isSystem) slateSub else ArcColors.Primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Type: ${rule.type} • Match: ${rule.matchType}",
                                fontSize = 11.sp,
                                color = slateSub
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
                                containerColor = highlightSurface,
                                contentColor = slateTitle
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
                                containerColor = ArcColors.Danger,
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
            .background(if (checked) ArcColors.Primary else (if (ArcColors.isDark) ArcColors.SurfaceHighlight else Color(0xFFCBD5E1)))
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
                    tint = ArcColors.Primary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
