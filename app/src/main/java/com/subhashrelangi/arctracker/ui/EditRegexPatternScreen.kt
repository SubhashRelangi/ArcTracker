package com.subhashrelangi.arctracker.ui

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
import com.subhashrelangi.arctracker.utils.RegexPatternsManager
import com.subhashrelangi.arctracker.utils.RegexRule
import java.util.UUID

@Composable
fun EditRegexPatternScreen(
    ruleId: String?,
    defaultCategory: String,
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
            initialRule.testMessage ?: "Rs 1,450.00 debited from a/c **4821 on 24-Oct-26 via UPI to ZOMATO. Ref 429104."
        )
    }

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
                color = Color.White,
                shadowElevation = 0.5.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                ArcTrackerHeader(
                    title = if (ruleId.isNullOrEmpty()) "Add Regex Pattern" else "Edit Regex Pattern",
                    subtitle = if (ruleId.isNullOrEmpty()) "Deterministic Parser Rule" else if (initialRule.isSystem) "System Parser Signature" else "Custom User Signature",
                    onBackClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .statusBarsPadding(),
                    horizontalPadding = 16.dp,
                    actions = {
                        if (initialRule.isSystem && !ruleId.isNullOrEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
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
                                        tint = Color(0xFF0F172A),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Reset",
                                        color = Color(0xFF0F172A),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        } else if (!initialRule.isSystem && !ruleId.isNullOrEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEE2E2),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                modifier = Modifier.clickable { showDeleteConfirmDialog = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Delete",
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Delete",
                                        color = Color(0xFFDC2626),
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
                                    .background(Color.White)
                                    .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Shield,
                                    contentDescription = "Local Security",
                                    tint = Color(0xFF047857),
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
                .background(Color(0xFFF8FAFC))
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
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                            .background(Color(0xFFF0FDF4)),
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
                            tint = Color(0xFF047857),
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
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (description.isBlank()) "Deterministic local extraction" else description,
                            fontSize = 11.5.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (initialRule.isSystem) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Text(
                                text = "System Rule",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFDCFCE7),
                            border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                        ) {
                            Text(
                                text = "Custom Rule",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534),
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
                    color = Color(0xFF475569),
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
                            color = if (isSelected) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                            border = if (isSelected) null else BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                                color = if (isSelected) Color.White else Color(0xFF475569),
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
                    color = Color(0xFF475569),
                    letterSpacing = 0.5.sp
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (initialRule.isSystem) Color(0xFFF1F5F9) else Color.White,
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
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
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            BasicTextField(
                                value = name,
                                onValueChange = { if (!initialRule.isSystem) name = it },
                                textStyle = TextStyle(
                                    fontSize = 13.5.sp,
                                    color = if (initialRule.isSystem) Color(0xFF64748B) else Color(0xFF0F172A),
                                    fontWeight = FontWeight.Medium
                                ),
                                singleLine = true,
                                enabled = !initialRule.isSystem,
                                cursorBrush = SolidColor(Color(0xFF047857)),
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
                                    tint = Color(0xFF94A3B8),
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
                    color = Color(0xFF475569),
                    letterSpacing = 0.5.sp
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (initialRule.isSystem) Color(0xFFF1F5F9) else Color.White,
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
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
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            BasicTextField(
                                value = description,
                                onValueChange = { if (!initialRule.isSystem) description = it },
                                textStyle = TextStyle(
                                    fontSize = 13.5.sp,
                                    color = if (initialRule.isSystem) Color(0xFF64748B) else Color(0xFF0F172A),
                                    fontWeight = FontWeight.Normal
                                ),
                                enabled = !initialRule.isSystem,
                                cursorBrush = SolidColor(Color(0xFF047857)),
                                modifier = Modifier.fillMaxWidth()
                            )
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
                        color = Color(0xFF475569),
                        letterSpacing = 0.5.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                        modifier = Modifier.clickable { showCheatSheetDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HelpOutline,
                                contentDescription = null,
                                tint = Color(0xFF047857),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Syntax Helper",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF047857)
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(
                        1.dp,
                        if (regexSyntaxError != null) Color(0xFFFCA5A5) else Color(0xFFCBD5E1)
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
                                        color = Color(0xFF94A3B8),
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
                                        color = Color(0xFF047857),
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 18.sp
                                    ),
                                    cursorBrush = SolidColor(Color(0xFF047857)),
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
                                        tint = Color(0xFF047857),
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
                                        .background(Color(0xFFDC2626))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Syntax Error: $regexSyntaxError",
                                    fontSize = 11.sp,
                                    color = Color(0xFFDC2626),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else if (pattern.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Valid Regex Expression ✓",
                                    fontSize = 11.sp,
                                    color = Color(0xFF166534),
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
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "+",
                                    fontSize = 11.sp,
                                    color = Color(0xFF047857),
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
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                                tint = Color(0xFF047857),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Live Pattern Sandbox",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF0FDF4),
                            border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                            modifier = Modifier.clickable {
                                testMessage = "Rs 1,450.00 debited from a/c **4821 on 24-Oct-26 via UPI to ZOMATO. Ref 429104."
                                hasEvaluated = false
                            }
                        ) {
                            Text(
                                text = "Insert Sample UPI",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF047857),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Test message input field
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        BasicTextField(
                            value = testMessage,
                            onValueChange = {
                                testMessage = it
                                hasEvaluated = false
                            },
                            textStyle = TextStyle(
                                fontSize = 12.5.sp,
                                color = Color(0xFF0F172A),
                                lineHeight = 17.sp
                            ),
                            cursorBrush = SolidColor(Color(0xFF047857)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp)
                                .padding(10.dp)
                        )
                    }

                    // Run Test Button
                    Button(
                        onClick = { runLiveTest() },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
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
                                color = Color(0xFFF0FDF4),
                                border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
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
                                                .background(Color(0xFF10B981))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Pattern Matched Successfully ✓",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF166534)
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
                                                    background = Color(0xFFBBF7D0),
                                                    color = Color(0xFF166534),
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
                                            color = Color(0xFF0F172A),
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
                                            color = Color(0xFF475569)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFDCFCE7),
                                            border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                                        ) {
                                            Text(
                                                text = testExtractedToken ?: "",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF166534),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFEF2F2),
                                border = BorderStroke(1.dp, Color(0xFFFECACA)),
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
                                            .background(Color(0xFFDC2626))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "No Match Found ✕  (Check regex anchors and sample text)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626)
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
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
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
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
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

    // Syntax Cheat Sheet Dialog
    if (showCheatSheetDialog) {
        Dialog(onDismissRequest = { showCheatSheetDialog = false }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Regex Syntax Guide",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        IconButton(
                            onClick = { showCheatSheetDialog = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

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
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = token,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF047857)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { showCheatSheetDialog = false },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Got it", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        Dialog(onDismissRequest = { showDeleteConfirmDialog = false }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEE2E2)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Delete Pattern",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "This custom rule will be permanently deleted.",
                                fontSize = 11.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDeleteConfirmDialog = false },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            Text("Cancel", color = Color(0xFF475569), fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                showDeleteConfirmDialog = false
                                RegexPatternsManager.deleteRule(context, initialRule.id)
                                Toast.makeText(context, "Regex pattern deleted", Toast.LENGTH_SHORT).show()
                                onBack()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
