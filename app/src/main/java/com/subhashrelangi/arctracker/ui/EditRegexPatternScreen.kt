package com.subhashrelangi.arctracker.ui

import com.subhashrelangi.arctracker.ui.core.ArcConfirmationBottomSheet
import com.subhashrelangi.arctracker.ui.core.ArcModalBottomSheet
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
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
import com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader
import com.subhashrelangi.arctracker.ui.theme.ArcColors
import com.subhashrelangi.arctracker.utils.RegexPatternsManager
import com.subhashrelangi.arctracker.utils.RegexRule
import java.util.UUID

private val slateTitle: Color @Composable @ReadOnlyComposable get() = ArcColors.TextPrimary
private val slateSub: Color @Composable @ReadOnlyComposable get() = ArcColors.TextMuted
private val cardBorder: Color @Composable @ReadOnlyComposable get() = ArcColors.Border
private val lightBg: Color @Composable @ReadOnlyComposable get() = ArcColors.Background
private val cardBg: Color @Composable @ReadOnlyComposable get() = ArcColors.CardBackground
private val highlightSurface: Color @Composable @ReadOnlyComposable get() = ArcColors.SurfaceHighlight

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditRegexPatternScreen(
    ruleId: String?,
    defaultCategory: String,
    initialTestMessage: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Determine if we are editing an existing rule or creating a new one
    val initialRule = remember(ruleId) {
        if (ruleId.isNullOrEmpty()) {
            RegexRule(
                id = UUID.randomUUID().toString(),
                name = "",
                description = "",
                category = if (defaultCategory.isNotBlank() && defaultCategory != "All") defaultCategory else "Merchant / Payee",
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
    var category by remember { mutableStateOf(initialRule.category) }
    var pattern by remember { mutableStateOf(initialRule.pattern) }
    var testMessage by remember {
        mutableStateOf(
            initialTestMessage?.takeIf { it.isNotBlank() }
                ?: initialRule.testMessage
                ?: "Rs 1,450.00 debited from a/c **4821 on 24-Oct-26 via UPI to ZOMATO. Ref 429104."
        )
    }

    var selectedHighlightWord by remember { mutableStateOf<String?>(null) }
    var showAiPromptDialog by remember { mutableStateOf(false) }
    var aiPastedText by remember { mutableStateOf("") }
    var lastSynthesizedSuccess by remember { mutableStateOf<String?>(null) }

    var showCheatSheetDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Live test state inside the screen
    var hasEvaluated by remember { mutableStateOf(false) }
    var testMatched by remember { mutableStateOf<Boolean?>(null) }
    var testExtractedToken by remember { mutableStateOf<String?>(null) }
    var testMatchRange by remember { mutableStateOf<IntRange?>(null) }

    // Real-time regex syntax check
    val regexSyntaxError by remember(pattern) {
        derivedStateOf {
            if (pattern.isBlank()) null
            else {
                try {
                    Regex(pattern, RegexOption.IGNORE_CASE)
                    null
                } catch (e: Exception) {
                    e.message ?: "Invalid regex pattern"
                }
            }
        }
    }

    fun runLiveTest() {
        hasEvaluated = true
        if (pattern.isBlank() || testMessage.isBlank()) {
            testMatched = false
            testExtractedToken = null
            testMatchRange = null
            return
        }

        try {
            val reg = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = reg.find(testMessage)
            if (match != null) {
                testMatched = true
                testMatchRange = match.range
                testExtractedToken = if (match.groups.size > 1) {
                    match.groupValues[1]
                } else {
                    match.value
                }
            } else {
                testMatched = false
                testMatchRange = null
                testExtractedToken = null
            }
        } catch (_: Exception) {
            testMatched = false
            testMatchRange = null
            testExtractedToken = null
        }
    }

    val categoryList = listOf("Merchant / Payee", "Amount", "Type Debit/Credit", "Account / Card", "Others")

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = cardBg,
                shadowElevation = 0.5.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardBg)
            ) {
                ArcTrackerHeader(
                    title = if (ruleId.isNullOrEmpty()) "Add Regex Pattern" else "Edit Regex Pattern",
                    subtitle = if (ruleId.isNullOrEmpty()) "Deterministic Parser Rule" else if (initialRule.isSystem) "System Parser Signature" else "Custom User Signature",
                    onBackClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardBg)
                        .statusBarsPadding(),
                    horizontalPadding = 16.dp,
                    actions = {
                        if (initialRule.isSystem && !ruleId.isNullOrEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = highlightSurface,
                                border = BorderStroke(1.dp, cardBorder),
                                modifier = Modifier.clickable {
                                    RegexPatternsManager.resetSystemRule(context, initialRule.id)
                                    Toast.makeText(context, "System rule reset to default", Toast.LENGTH_SHORT).show()
                                    onBack()
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = "Reset",
                                        tint = slateTitle,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Reset",
                                        color = slateTitle,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        } else if (!initialRule.isSystem && !ruleId.isNullOrEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (ArcColors.isDark) Color(0xFF7F1D1D).copy(alpha = 0.5f) else Color(0xFFFEE2E2),
                                border = BorderStroke(1.dp, if (ArcColors.isDark) Color(0xFF991B1B) else Color(0xFFFCA5A5)),
                                modifier = Modifier.clickable { showDeleteConfirmDialog = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Delete",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Delete",
                                        color = Color(0xFFEF4444),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        } else {
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
                                    contentDescription = "Local Security",
                                    tint = ArcColors.Primary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
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
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Rule Identity Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (ArcColors.isDark) Color(0xFF064E3B) else Color(0xFFF0FDF4)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val iconVector: ImageVector = when {
                            category.contains("Merchant", ignoreCase = true) -> Icons.Outlined.Storefront
                            category.contains("Amount", ignoreCase = true) -> Icons.Outlined.Payments
                            category.contains("Type", ignoreCase = true) -> Icons.Outlined.SwapVert
                            category.contains("Account", ignoreCase = true) || category.contains("Card", ignoreCase = true) -> Icons.Outlined.CreditCard
                            else -> Icons.Outlined.Code
                        }
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = ArcColors.Primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (name.isBlank()) "New Regex Rule" else name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = slateTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (description.isBlank()) "Deterministic local extraction" else description,
                            fontSize = 11.5.sp,
                            color = slateSub,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (initialRule.isSystem) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = highlightSurface,
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Text(
                                text = "System Rule",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = slateSub,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (ArcColors.isDark) Color(0xFF064E3B).copy(alpha = 0.5f) else Color(0xFFDCFCE7),
                            border = BorderStroke(1.dp, if (ArcColors.isDark) Color(0xFF065F46) else Color(0xFFBBF7D0))
                        ) {
                            Text(
                                text = "Custom Rule",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (ArcColors.isDark) Color(0xFF34D399) else Color(0xFF166534),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // 2. Category Selector Pills
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "TARGET CATEGORY",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = slateSub,
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    categoryList.forEach { cat ->
                        val isSelected = category.equals(cat, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) ArcColors.Primary else highlightSurface,
                            border = if (isSelected) null else BorderStroke(1.dp, cardBorder),
                            modifier = Modifier.clickable {
                                if (!initialRule.isSystem) {
                                    category = cat
                                }
                            }
                        ) {
                            Text(
                                text = cat,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isSelected) Color.White else slateSub,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            // 3. Pattern Name Input
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "PATTERN NAME",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = slateSub,
                    letterSpacing = 0.5.sp
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (initialRule.isSystem) highlightSurface else cardBg,
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (name.isEmpty()) {
                                Text(
                                    text = "e.g. Swiggy / Zomato UPI Payee",
                                    fontSize = 13.5.sp,
                                    color = slateSub
                                )
                            }
                            BasicTextField(
                                value = name,
                                onValueChange = { if (!initialRule.isSystem) name = it },
                                textStyle = TextStyle(
                                    fontSize = 13.5.sp,
                                    color = if (initialRule.isSystem) slateSub else slateTitle,
                                    fontWeight = FontWeight.Medium
                                ),
                                singleLine = true,
                                enabled = !initialRule.isSystem,
                                cursorBrush = SolidColor(ArcColors.Primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (name.isNotEmpty() && !initialRule.isSystem) {
                            IconButton(
                                onClick = { name = "" },
                                modifier = Modifier.size(20.dp)
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
                }
            }

            // 4. Target Description Input
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "TARGET TOKEN DESCRIPTION",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = slateSub,
                    letterSpacing = 0.5.sp
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (initialRule.isSystem) highlightSurface else cardBg,
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (description.isEmpty()) {
                                Text(
                                    text = "e.g. Extracts merchant name following 'to' or 'towards'",
                                    fontSize = 13.5.sp,
                                    color = slateSub
                                )
                            }
                            BasicTextField(
                                value = description,
                                onValueChange = { if (!initialRule.isSystem) description = it },
                                textStyle = TextStyle(
                                    fontSize = 13.5.sp,
                                    color = if (initialRule.isSystem) slateSub else slateTitle,
                                    fontWeight = FontWeight.Normal
                                ),
                                enabled = !initialRule.isSystem,
                                cursorBrush = SolidColor(ArcColors.Primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // 4.5. Quick Highlight Pattern Builder Card (No-AI Builder + AI Assistant)
            if (!initialRule.isSystem) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (ArcColors.isDark) Color(0xFF064E3B) else Color(0xFFECFDF5)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = "Highlight Builder",
                                        tint = ArcColors.Primary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Quick Highlight Builder",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = slateTitle
                                    )
                                    Text(
                                        text = "Tap target word in SMS to auto-create regex",
                                        fontSize = 11.sp,
                                        color = slateSub
                                    )
                                }
                            }

                            // Circular Ask AI button
                            Button(
                                onClick = { showAiPromptDialog = true },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (ArcColors.isDark) highlightSurface else Color(0xFF0F172A)
                                ),
                                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "Ask AI",
                                    tint = ArcColors.Primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Ask AI",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (ArcColors.isDark) slateTitle else Color.White
                                )
                            }
                        }

                        // Sample SMS Word Chips in FlowRow
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = highlightSurface,
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "SAMPLE SMS (TAP TARGET TOKEN):",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = slateSub,
                                    letterSpacing = 0.5.sp
                                )

                                val sampleTokens = remember(testMessage) {
                                    testMessage.split(Regex("\\s+")).filter { it.isNotBlank() }
                                }

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    sampleTokens.forEach { rawToken ->
                                        val cleanToken = rawToken.trimEnd('.', ',', ':', ';', '!', '?')
                                        val isSelected = selectedHighlightWord.equals(cleanToken, ignoreCase = true) ||
                                                (selectedHighlightWord != null && rawToken.contains(selectedHighlightWord!!, ignoreCase = true))

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) ArcColors.Primary else cardBg,
                                            border = if (isSelected) null else BorderStroke(1.dp, cardBorder),
                                            modifier = Modifier.clickable {
                                                selectedHighlightWord = cleanToken
                                                val synth = com.subhashrelangi.arctracker.utils.VisualRegexSynthesizer.synthesize(
                                                    fullMessage = testMessage,
                                                    selectedText = cleanToken,
                                                    explicitCategory = null
                                                )
                                                if (synth != null) {
                                                    pattern = synth.pattern
                                                    category = synth.category
                                                    if (name.isBlank() || name.startsWith("Amount Parser") || name.startsWith("Payee Pattern") || name.startsWith("Account Suffix") || name.startsWith("Custom Token") || name.contains("Signal Pattern") || name.startsWith("Custom")) {
                                                        name = synth.suggestedName
                                                    }
                                                    if (description.isBlank() || description.startsWith("Extracts") || description.startsWith("Identifies") || description.startsWith("Matches")) {
                                                        description = synth.description
                                                    }
                                                    lastSynthesizedSuccess = "Extracted: \"${synth.extractedPreview}\" (${synth.category})"
                                                    runLiveTest()
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = rawToken,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else slateTitle,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Category Override Quick Pills
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                text = "INTERPRET SELECTION AS:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = slateSub,
                                letterSpacing = 0.5.sp
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val quickCategories = listOf(
                                    "Merchant / Payee",
                                    "Amount",
                                    "Account / Card",
                                    "Type Debit/Credit",
                                    "Others"
                                )
                                quickCategories.forEach { cat ->
                                    val isCurrentCat = category.equals(cat, ignoreCase = true)
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isCurrentCat) ArcColors.Primary else highlightSurface,
                                        border = if (isCurrentCat) null else BorderStroke(1.dp, cardBorder),
                                        modifier = Modifier.clickable {
                                            category = cat
                                            val targetWord = selectedHighlightWord
                                            if (!targetWord.isNullOrBlank()) {
                                                val synth = com.subhashrelangi.arctracker.utils.VisualRegexSynthesizer.synthesize(
                                                    fullMessage = testMessage,
                                                    selectedText = targetWord,
                                                    explicitCategory = cat
                                                )
                                                if (synth != null) {
                                                    pattern = synth.pattern
                                                    category = synth.category
                                                    lastSynthesizedSuccess = "Extracted: \"${synth.extractedPreview}\" (${synth.category})"
                                                    runLiveTest()
                                                }
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = cat,
                                            fontSize = 11.sp,
                                            fontWeight = if (isCurrentCat) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isCurrentCat) Color.White else slateSub,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Verification Success Banner
                        if (lastSynthesizedSuccess != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (ArcColors.isDark) Color(0xFF064E3B).copy(alpha = 0.5f) else Color(0xFFECFDF5),
                                border = BorderStroke(1.dp, if (ArcColors.isDark) Color(0xFF065F46) else Color(0xFFA7F3D0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Success",
                                        tint = ArcColors.Primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Generated & Verified ✓ $lastSynthesizedSuccess",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (ArcColors.isDark) Color(0xFF34D399) else Color(0xFF065F46)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Regex Pattern Expression Card
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "REGEX EXPRESSION (JAVA / KOTLIN)",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = slateSub,
                        letterSpacing = 0.5.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (ArcColors.isDark) Color(0xFF064E3B).copy(alpha = 0.5f) else Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, if (ArcColors.isDark) Color(0xFF065F46) else Color(0xFFBBF7D0)),
                        modifier = Modifier.clickable { showCheatSheetDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HelpOutline,
                                contentDescription = null,
                                tint = ArcColors.Primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Syntax Helper",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ArcColors.Primary
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = cardBg,
                    border = BorderStroke(
                        1.dp,
                        if (regexSyntaxError != null) Color(0xFFEF4444) else cardBorder
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (pattern.isEmpty()) {
                                    Text(
                                        text = "(?i)(?:to|towards)\\s+([A-Za-z0-9 &._-]{2,40})",
                                        fontSize = 12.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = slateSub,
                                        lineHeight = 18.sp
                                    )
                                }
                                BasicTextField(
                                    value = pattern,
                                    onValueChange = {
                                        pattern = it
                                        hasEvaluated = false
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 12.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = ArcColors.Primary,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 18.sp
                                    ),
                                    cursorBrush = SolidColor(ArcColors.Primary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                )
                            }

                            if (pattern.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(pattern))
                                        Toast.makeText(context, "Regex copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = ArcColors.Primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Syntax Status Banner
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (regexSyntaxError != null) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Syntax Error: $regexSyntaxError",
                                    fontSize = 11.sp,
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else if (pattern.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(ArcColors.Primary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Valid Regex Expression ✓",
                                    fontSize = 11.sp,
                                    color = ArcColors.Primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Quick Token Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val quickTokens = listOf(
                        "(?i)" to "Case insensitive",
                        "(?:to|at)" to "Non-capturing",
                        "([0-9,]+\\.?[0-9]*)" to "Amount",
                        "([0-9]{4})" to "Account 4 digits",
                        "([A-Za-z0-9 &._-]{2,30})" to "Payee"
                    )
                    quickTokens.forEach { (token, label) ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = highlightSurface,
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier.clickable {
                                pattern += token
                                hasEvaluated = false
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = token,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = slateTitle
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "+",
                                    fontSize = 11.sp,
                                    color = ArcColors.Primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 6. Interactive Live Test Sandbox Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, cardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Terminal,
                                contentDescription = null,
                                tint = ArcColors.Primary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Live Pattern Sandbox",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = slateTitle
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (ArcColors.isDark) Color(0xFF064E3B).copy(alpha = 0.5f) else Color(0xFFF0FDF4),
                            border = BorderStroke(1.dp, if (ArcColors.isDark) Color(0xFF065F46) else Color(0xFFBBF7D0)),
                            modifier = Modifier.clickable {
                                testMessage = "Rs 1,450.00 debited from a/c **4821 on 24-Oct-26 via UPI to ZOMATO. Ref 429104."
                                hasEvaluated = false
                            }
                        ) {
                            Text(
                                text = "Insert Sample UPI",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ArcColors.Primary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Test message input field
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = highlightSurface,
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        BasicTextField(
                            value = testMessage,
                            onValueChange = {
                                testMessage = it
                                hasEvaluated = false
                            },
                            textStyle = TextStyle(
                                fontSize = 12.5.sp,
                                color = slateTitle,
                                lineHeight = 17.sp
                            ),
                            cursorBrush = SolidColor(ArcColors.Primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp)
                                .padding(10.dp)
                        )
                    }

                    // Run Test Button
                    Button(
                        onClick = { runLiveTest() },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = ArcColors.Primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Test",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Test Pattern Against Payload",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = Color.White
                        )
                    }

                    // Live Test Result
                    if (hasEvaluated) {
                        if (testMatched == true && testExtractedToken != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (ArcColors.isDark) Color(0xFF064E3B).copy(alpha = 0.5f) else Color(0xFFF0FDF4),
                                border = BorderStroke(1.dp, if (ArcColors.isDark) Color(0xFF065F46) else Color(0xFFBBF7D0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(ArcColors.Primary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Pattern Matched Successfully ✓",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (ArcColors.isDark) Color(0xFF34D399) else Color(0xFF166534)
                                        )
                                    }

                                    // Render highlighted text
                                    testMatchRange?.let { range ->
                                        val annotated = buildAnnotatedString {
                                            if (range.first > 0) {
                                                append(testMessage.take(range.first))
                                            }
                                            withStyle(
                                                style = SpanStyle(
                                                    background = if (ArcColors.isDark) Color(0xFF065F46) else Color(0xFFBBF7D0),
                                                    color = if (ArcColors.isDark) Color(0xFFF8FAFC) else Color(0xFF166534),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            ) {
                                                append(testMessage.substring(range.first, (range.last + 1).coerceAtMost(testMessage.length)))
                                            }
                                            if (range.last + 1 < testMessage.length) {
                                                append(testMessage.substring(range.last + 1))
                                            }
                                        }
                                        Text(
                                            text = annotated,
                                            fontSize = 11.5.sp,
                                            color = slateTitle,
                                            lineHeight = 16.sp
                                        )
                                    }

                                    // Extracted Token Pill
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text(
                                            text = "Extracted Token:",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = slateSub
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (ArcColors.isDark) Color(0xFF064E3B) else Color(0xFFDCFCE7),
                                            border = BorderStroke(1.dp, if (ArcColors.isDark) Color(0xFF065F46) else Color(0xFFA7F3D0))
                                        ) {
                                            Text(
                                                text = testExtractedToken ?: "",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (ArcColors.isDark) Color(0xFF34D399) else Color(0xFF166534),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (ArcColors.isDark) Color(0xFF7F1D1D).copy(alpha = 0.5f) else Color(0xFFFEF2F2),
                                border = BorderStroke(1.dp, if (ArcColors.isDark) Color(0xFF991B1B) else Color(0xFFFECACA)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEF4444))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "No Match Found ✕  (Check regex anchors and sample text)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 7. Bottom Actions: Cancel & Save Changes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onBack,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = slateSub
                    )
                }

                Button(
                    onClick = {
                        val validPattern = pattern.trim()
                        if (validPattern.isBlank()) {
                            Toast.makeText(context, "Please enter a valid regex pattern", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (regexSyntaxError != null) {
                            Toast.makeText(context, "Invalid regex syntax: $regexSyntaxError", Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        val ruleToSave = initialRule.copy(
                            name = name.ifBlank { "Custom Pattern" },
                            description = description.ifBlank { "Deterministic token extractor" },
                            category = category,
                            pattern = validPattern,
                            testMessage = testMessage.ifBlank { null }
                        )

                        if (ruleId.isNullOrEmpty()) {
                            val wasNew = RegexPatternsManager.addRule(context, ruleToSave)
                            if (wasNew) {
                                Toast.makeText(context, "New regex pattern added", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Existing matching pattern updated", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            RegexPatternsManager.updateRule(context, ruleToSave)
                            Toast.makeText(context, "Regex pattern updated", Toast.LENGTH_SHORT).show()
                        }
                        onBack()
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = ArcColors.Primary),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (ruleId.isNullOrEmpty()) "Save Pattern" else "Save Changes",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }

    // AI Regex Assistant Dialog
    if (showAiPromptDialog) {
        ArcModalBottomSheet(
            onDismissRequest = { showAiPromptDialog = false },
            title = "Ask AI (ChatGPT / Claude)",
            subtitle = "Generate regex with AI, 100% private & offline",
            icon = Icons.Rounded.AutoAwesome,
            iconColor = ArcColors.Primary,
            maxHeight = 560.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Step 1: Copy AI Prompt
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "STEP 1: COPY PROMPT FOR CHATGPT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = slateSub,
                            letterSpacing = 0.5.sp
                        )

                        val generatedPrompt = remember(testMessage, category) {
                            com.subhashrelangi.arctracker.utils.VisualRegexSynthesizer.buildAiPrompt(testMessage, category)
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = highlightSurface,
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = generatedPrompt,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = slateTitle,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Button(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(generatedPrompt))
                                Toast.makeText(context, "Prompt copied to clipboard! Paste it into ChatGPT.", Toast.LENGTH_SHORT).show()
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = ArcColors.Primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Prompt to Clipboard", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Step 2: Paste AI Response
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "STEP 2: PASTE AI RESPONSE HERE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = slateSub,
                            letterSpacing = 0.5.sp
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = highlightSurface,
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Box(modifier = Modifier.padding(10.dp)) {
                                if (aiPastedText.isEmpty()) {
                                    Text(
                                        text = "Paste ChatGPT's JSON response or regex pattern here...",
                                        fontSize = 12.sp,
                                        color = slateSub
                                    )
                                }
                                BasicTextField(
                                    value = aiPastedText,
                                    onValueChange = { aiPastedText = it },
                                    textStyle = TextStyle(
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = slateTitle
                                    ),
                                    cursorBrush = SolidColor(ArcColors.Primary),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // Dialog Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAiPromptDialog = false },
                            shape = CircleShape,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Text("Cancel", color = slateSub, fontSize = 12.5.sp)
                        }

                        Button(
                            onClick = {
                                val parsed = com.subhashrelangi.arctracker.utils.VisualRegexSynthesizer.parseAiResponse(aiPastedText)
                                if (parsed != null) {
                                    pattern = parsed.pattern
                                    category = parsed.category
                                    name = parsed.suggestedName
                                    description = parsed.description
                                    lastSynthesizedSuccess = "Imported from AI: \"${parsed.pattern}\""
                                    runLiveTest()
                                    showAiPromptDialog = false
                                    Toast.makeText(context, "AI Regex pattern applied successfully!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Could not extract pattern. Paste the full JSON or regex.", Toast.LENGTH_LONG).show()
                                }
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = ArcColors.Primary),
                            modifier = Modifier.weight(1.4f)
                        ) {
                            Text("Apply Pattern", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
        }
    }

    // Syntax Cheat Sheet Bottom Sheet
    if (showCheatSheetDialog) {
        ArcModalBottomSheet(
            onDismissRequest = { showCheatSheetDialog = false },
            title = "Regex Syntax Guide",
            maxHeight = 480.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val cheatItems = listOf(
                    "(?i)" to "Enables case-insensitive matching across entire regex.",
                    "(?:to|at|paid to)" to "Non-capturing group matching any alternative word.",
                    "([0-9,]+\\.?[0-9]*)" to "Captures numbers, commas, and decimal amounts.",
                    "([A-Za-z0-9 &._-]{2,30})" to "Captures alphanumeric merchant or recipient name.",
                    "([0-9]{4})" to "Captures 4-digit bank account or card suffix."
                )

                cheatItems.forEach { (token, desc) ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = highlightSurface,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = token,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = ArcColors.Primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = desc,
                                fontSize = 11.5.sp,
                                color = slateSub
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = { showCheatSheetDialog = false },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ArcColors.Primary),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text(
                        "Got it",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Delete Confirmation Bottom Sheet
    if (showDeleteConfirmDialog) {
        ArcConfirmationBottomSheet(
            title = "Delete Pattern",
            message = "This custom rule will be permanently deleted.",
            confirmText = "Delete",
            cancelText = "Cancel",
            icon = Icons.Outlined.Delete,
            iconColor = Color(0xFFEF4444),
            isDestructive = true,
            onConfirm = {
                showDeleteConfirmDialog = false
                RegexPatternsManager.deleteRule(context, initialRule.id)
                Toast.makeText(context, "Regex pattern deleted", Toast.LENGTH_SHORT).show()
                onBack()
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }
}
