package com.subhashrelangi.arctracker.ui

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
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.service.*
import com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader
import com.subhashrelangi.arctracker.ui.review.ArcColors
import kotlinx.coroutines.launch

private val emeraldPrimary: Color
    @Composable @ReadOnlyComposable get() = ArcColors.Primary

private val slateTitle: Color
    @Composable @ReadOnlyComposable get() = ArcColors.TextPrimary

private val slateSub: Color
    @Composable @ReadOnlyComposable get() = ArcColors.TextMuted

private val cardBorder: Color
    @Composable @ReadOnlyComposable get() = ArcColors.Border

private val lightBg: Color
    @Composable @ReadOnlyComposable get() = ArcColors.Background

private val cardBg: Color
    @Composable @ReadOnlyComposable get() = ArcColors.CardBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryRulesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToApplyRules: (String?) -> Unit = {},
    ruleManager: CategoryRuleManager? = null,
    categoryManager: CategoryManager? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val db = remember { AppDatabase.getDatabase(context) }
    val rManager = remember {
        ruleManager ?: CategoryRuleManager(db.userCategoryRuleDao(), db.transactionCategoryDao())
    }
    val cManager = remember {
        categoryManager ?: CategoryManager(db.transactionCategoryDao(), db.expenseDao(), db, db.userCategoryRuleDao())
    }

    val rules by rManager.getAllFlow().collectAsState(initial = emptyList())
    val categories by cManager.getAllCategoriesFlow().collectAsState(initial = emptyList())
    val activeCategories = remember(categories) { categories.filter { !it.isArchived } }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Active, 1: Inactive

    val activeRules = remember(rules) { rules.filter { it.isEnabled } }
    val inactiveRules = remember(rules) { rules.filter { !it.isEnabled } }

    var showAddDialog by remember { mutableStateOf(false) }
    var ruleToEdit by remember { mutableStateOf<UserCategoryRule?>(null) }
    var ruleToDelete by remember { mutableStateOf<UserCategoryRule?>(null) }

    // Test / Preview State
    var testMerchant by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<CategoryInferenceResult?>(null) }

    Scaffold(
        containerColor = lightBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = lightBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(lightBg)
            ) {
                ArcTrackerHeader(
                    title = "Category Rules",
                    subtitle = "Automated Categorization Engine",
                    onBackClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(lightBg)
                        .statusBarsPadding(),
                    horizontalPadding = 16.dp,
                    actions = {
                        // Apply Rules to Existing Transactions Button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(cardBg)
                                .border(BorderStroke(1.dp, cardBorder), CircleShape)
                                .clickable { onNavigateToApplyRules(null) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoFixHigh,
                                contentDescription = "Apply Rules",
                                tint = emeraldPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        // Add Rule Button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(cardBg)
                                .border(BorderStroke(1.dp, cardBorder), CircleShape)
                                .clickable { showAddDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add Rule",
                                tint = slateTitle,
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
                .padding(innerPadding)
        ) {
            // Interactive Test & Preview Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(emeraldPrimary, CircleShape)
                            )
                            Text(
                                text = "Rule Tester & Live Preview",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = slateTitle
                            )
                        }

                        Text(
                            text = "${activeRules.size} Active",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = emeraldPrimary
                        )
                    }

                    Text(
                        text = "Enter a merchant name or SMS alert keyword to test category matching accuracy.",
                        fontSize = 12.sp,
                        color = slateSub,
                        lineHeight = 17.sp
                    )

                    // Clear & Attractive Full-Width Input Box
                    OutlinedTextField(
                        value = testMerchant,
                        onValueChange = {
                            testMerchant = it
                            testResult = null
                        },
                        placeholder = { Text("e.g. Swiggy, Netflix, Uber, Shell Fuel...", fontSize = 13.sp, color = slateSub.copy(alpha = 0.7f)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Storefront,
                                contentDescription = null,
                                tint = if (testMerchant.isNotBlank()) emeraldPrimary else slateSub,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (testMerchant.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        testMerchant = ""
                                        testResult = null
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear input",
                                        tint = slateSub,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = slateTitle
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = emeraldPrimary,
                            unfocusedBorderColor = cardBorder,
                            focusedContainerColor = ArcColors.SurfaceHighlight,
                            unfocusedContainerColor = ArcColors.SurfaceHighlight,
                            cursorColor = emeraldPrimary
                        )
                    )

                    // Button Moved Down Below the Input Box
                    Button(
                        onClick = {
                            if (testMerchant.isNotBlank()) {
                                val engine = CategoryInferenceEngine()
                                testResult = engine.inferCategory(
                                    CategoryInferenceInput(
                                        merchant = testMerchant,
                                        availableCategoryIds = activeCategories.map { it.id }.toSet(),
                                        userRules = activeRules
                                    )
                                )
                            }
                        },
                        enabled = testMerchant.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = emeraldPrimary,
                            disabledContainerColor = ArcColors.SurfaceHighlight,
                            contentColor = Color.White,
                            disabledContentColor = Color(0xFF94A3B8)
                        ),
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Test Category Rule",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    testResult?.let { res ->
                        val isHighConfidence = res.status == CategoryInferenceStatus.HIGH_CONFIDENCE
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isHighConfidence) {
                                if (ArcColors.isDark) ArcColors.SuccessContainer else Color(0xFFECFDF5)
                            } else {
                                if (ArcColors.isDark) ArcColors.WarningAmberBg else Color(0xFFFFFBEB)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isHighConfidence) {
                                    if (ArcColors.isDark) ArcColors.SuccessBorder else Color(0xFFA7F3D0)
                                } else {
                                    if (ArcColors.isDark) ArcColors.WarningAmberBorder else Color(0xFFFDE68A)
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val statusColor = if (isHighConfidence) {
                                    if (ArcColors.isDark) Color(0xFF34D399) else emeraldPrimary
                                } else {
                                    if (ArcColors.isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isHighConfidence) Icons.Filled.CheckCircle else Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "INFERENCE: ${res.status}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        color = statusColor
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "\"$testMerchant\"",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = slateTitle
                                    )
                                    Text(
                                        text = "➜",
                                        fontSize = 13.sp,
                                        color = statusColor
                                    )
                                    Text(
                                        text = res.suggestedCategoryName ?: "Uncategorized",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )
                                }

                                Text(
                                    text = res.reason,
                                    fontSize = 12.sp,
                                    color = slateSub
                                )
                            }
                        }
                    }
                }
            }

            // Segmented Tab Pill Selector: Active vs Inactive
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(ArcColors.SurfaceHighlight, RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Active Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == 0) cardBg else Color.Transparent)
                            .then(
                                if (selectedTab == 0) Modifier.border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(10.dp))
                                else Modifier
                            )
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Active (${activeRules.size})",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 0) slateTitle else slateSub
                        )
                    }

                    // Inactive Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == 1) cardBg else Color.Transparent)
                            .then(
                                if (selectedTab == 1) Modifier.border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(10.dp))
                                else Modifier
                            )
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Inactive (${inactiveRules.size})",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == 1) slateTitle else slateSub
                        )
                    }
                }
            }

            val currentList = if (selectedTab == 0) activeRules else inactiveRules

            if (currentList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (ArcColors.isDark) Color(0xFF064E3B) else Color(0xFFECFDF5))
                                .border(1.dp, if (ArcColors.isDark) Color(0xFF059669) else Color(0xFFA7F3D0), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Rule,
                                contentDescription = null,
                                tint = if (ArcColors.isDark) Color(0xFF34D399) else emeraldPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (selectedTab == 0) "No active category rules" else "No inactive rules",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = slateTitle
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap + to teach ArcTracker custom merchant rules",
                            fontSize = 12.5.sp,
                            color = slateSub
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(currentList, key = { it.id }) { rule ->
                        val targetCat = categories.find { it.id == rule.categoryId }
                        val isCatArchived = targetCat?.isArchived == true
                        val isCatMissing = targetCat == null

                        RuleCard(
                            rule = rule,
                            targetCategory = targetCat,
                            isInvalid = isCatArchived || isCatMissing,
                            invalidReason = when {
                                isCatMissing -> "Category was deleted"
                                isCatArchived -> "Category is archived"
                                else -> null
                            },
                            onToggle = { enabled ->
                                scope.launch {
                                    val res = rManager.setRuleEnabled(rule.id, enabled)
                                    if (res.isFailure) {
                                        snackbarHostState.showSnackbar(res.exceptionOrNull()?.message ?: "Failed to update rule")
                                    }
                                }
                            },
                            onEdit = { ruleToEdit = rule },
                            onDelete = { ruleToDelete = rule },
                            onApplyToTransactions = { onNavigateToApplyRules(rule.id) }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Rule Dialog
    if (showAddDialog || ruleToEdit != null) {
        val editingRule = ruleToEdit
        RuleEditorDialog(
            existingRule = editingRule,
            activeCategories = activeCategories,
            onDismiss = {
                showAddDialog = false
                ruleToEdit = null
            },
            onSave = { pattern, categoryId, matchType, priority, name ->
                scope.launch {
                    val result = if (editingRule != null) {
                        rManager.updateRule(
                            id = editingRule.id,
                            pattern = pattern,
                            categoryId = categoryId,
                            matchType = matchType,
                            priority = priority,
                            isEnabled = editingRule.isEnabled,
                            name = name
                        )
                    } else {
                        rManager.createRule(
                            pattern = pattern,
                            categoryId = categoryId,
                            matchType = matchType,
                            priority = priority,
                            name = name
                        )
                    }

                    if (result.isSuccess) {
                        showAddDialog = false
                        ruleToEdit = null
                        snackbarHostState.showSnackbar(if (editingRule != null) "Rule updated" else "Rule created")
                    } else {
                        snackbarHostState.showSnackbar(result.exceptionOrNull()?.message ?: "Operation failed")
                    }
                }
            }
        )
    }

    // Delete Confirmation Dialog
    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = cardBg,
            title = {
                Text(
                    text = "Delete Rule",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = slateTitle
                )
            },
            text = {
                Text(
                    text = "Delete category rule for \"${rule.pattern}\"? Existing transaction categories will not be changed.",
                    fontSize = 13.5.sp,
                    color = slateSub,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = rule.id
                        ruleToDelete = null
                        scope.launch {
                            val res = rManager.deleteRule(id)
                            if (res.isSuccess) {
                                snackbarHostState.showSnackbar("Rule deleted")
                            } else {
                                snackbarHostState.showSnackbar(res.exceptionOrNull()?.message ?: "Failed to delete")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = CircleShape
                ) {
                    Text("Delete Rule", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) {
                    Text("Cancel", color = slateSub, fontSize = 13.sp)
                }
            }
        )
    }
}

@Composable
fun RuleCard(
    rule: UserCategoryRule,
    targetCategory: TransactionCategory?,
    isInvalid: Boolean,
    invalidReason: String?,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onApplyToTransactions: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, if (isInvalid) Color(0xFFEF4444) else cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Avatar Icon
            val catColor = targetCategory?.let { CategoryVisuals.getColor(it.colorKey) } ?: Color.Gray
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(catColor.copy(alpha = 0.12f))
                    .border(1.dp, catColor.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = targetCategory?.let { CategoryVisuals.getIcon(it.iconKey) } ?: Icons.Filled.Category,
                    contentDescription = null,
                    tint = catColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name ?: rule.pattern,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = slateTitle
                )
                if (rule.name != null) {
                    Text(
                        text = "Pattern: ${rule.pattern}",
                        fontSize = 11.5.sp,
                        color = slateSub
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Match Type Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (ArcColors.isDark) Color(0xFF064E3B) else Color(0xFFECFDF5),
                        border = BorderStroke(1.dp, if (ArcColors.isDark) Color(0xFF059669) else Color(0xFFA7F3D0))
                    ) {
                        Text(
                            text = when (rule.matchType) {
                                UserRuleMatchType.MERCHANT_EXACT -> "Exact"
                                UserRuleMatchType.MERCHANT_TOKEN -> "Token"
                                UserRuleMatchType.MERCHANT_CONTAINS -> "Contains"
                                UserRuleMatchType.TRANSACTION_TEXT_CONTAINS -> "Text Contains"
                                else -> rule.matchType
                            },
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (ArcColors.isDark) Color(0xFF34D399) else emeraldPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Text("→", fontSize = 11.sp, color = slateSub)
                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = targetCategory?.name ?: "Unknown Category",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isInvalid) Color(0xFFDC2626) else slateTitle
                    )
                }

                if (isInvalid && invalidReason != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "⚠ $invalidReason",
                        fontSize = 11.sp,
                        color = Color(0xFFDC2626)
                    )
                }
            }

            // Quick Execution Action
            IconButton(
                onClick = onApplyToTransactions,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Apply to transactions",
                    tint = emeraldPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Toggle Switch
            Switch(
                checked = rule.isEnabled && !isInvalid,
                onCheckedChange = onToggle,
                enabled = !isInvalid,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = emeraldPrimary,
                    uncheckedThumbColor = if (ArcColors.isDark) Color(0xFF71717A) else Color(0xFFCBD5E1),
                    uncheckedTrackColor = if (ArcColors.isDark) ArcColors.SurfaceHighlight else Color(0xFFE2E8F0)
                )
            )

            // Edit Button
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Edit",
                    tint = slateSub,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Delete Button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete",
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorDialog(
    existingRule: UserCategoryRule?,
    activeCategories: List<TransactionCategory>,
    onDismiss: () -> Unit,
    onSave: (pattern: String, categoryId: String, matchType: String, priority: Int, name: String?) -> Unit
) {
    var pattern by remember { mutableStateOf(existingRule?.pattern ?: "") }
    var name by remember { mutableStateOf(existingRule?.name ?: "") }
    var selectedMatchType by remember { mutableStateOf(existingRule?.matchType ?: UserRuleMatchType.MERCHANT_EXACT) }
    var selectedCategoryId by remember { mutableStateOf(existingRule?.categoryId ?: (activeCategories.firstOrNull()?.id ?: "")) }
    var priority by remember { mutableIntStateOf(existingRule?.priority ?: 100) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var matchTypeExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = cardBg,
        title = {
            Text(
                text = if (existingRule != null) "Edit Category Rule" else "New Category Rule",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = slateTitle
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = {
                        pattern = it
                        errorMessage = null
                    },
                    label = { Text("Merchant / Keyword Pattern *") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = emeraldPrimary,
                        unfocusedBorderColor = cardBorder,
                        focusedLabelColor = emeraldPrimary
                    )
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rule Label (Optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = emeraldPrimary,
                        unfocusedBorderColor = cardBorder,
                        focusedLabelColor = emeraldPrimary
                    )
                )

                // Match Type Selector
                OutlinedButton(
                    onClick = { matchTypeExpanded = true },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Match: " + when (selectedMatchType) {
                            UserRuleMatchType.MERCHANT_EXACT -> "Exact Merchant"
                            UserRuleMatchType.MERCHANT_TOKEN -> "Merchant Token"
                            UserRuleMatchType.MERCHANT_CONTAINS -> "Merchant Contains"
                            UserRuleMatchType.TRANSACTION_TEXT_CONTAINS -> "Transaction Text Contains"
                            else -> selectedMatchType
                        },
                        fontSize = 13.sp,
                        color = slateTitle
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = slateSub)
                }

                if (matchTypeExpanded) {
                    val matchOptions = UserRuleMatchType.ALL.map { mt ->
                        com.subhashrelangi.arctracker.ui.review.HorizontalOptionItem(
                            id = mt,
                            title = when (mt) {
                                UserRuleMatchType.MERCHANT_EXACT -> "Exact Merchant"
                                UserRuleMatchType.MERCHANT_TOKEN -> "Merchant Token"
                                UserRuleMatchType.MERCHANT_CONTAINS -> "Merchant Contains"
                                UserRuleMatchType.TRANSACTION_TEXT_CONTAINS -> "Text Contains"
                                else -> mt
                            },
                            subtitle = when (mt) {
                                UserRuleMatchType.MERCHANT_EXACT -> "e.g. Swiggy"
                                UserRuleMatchType.MERCHANT_TOKEN -> "e.g. Amazon"
                                UserRuleMatchType.MERCHANT_CONTAINS -> "e.g. Coffee"
                                else -> "SMS Body"
                            }
                        )
                    }
                    com.subhashrelangi.arctracker.ui.review.HorizontalOptionPickerDialog(
                        title = "Select Match Type",
                        subtitle = "Rule evaluation mode",
                        options = matchOptions,
                        selectedId = selectedMatchType,
                        onDismissRequest = { matchTypeExpanded = false },
                        onOptionSelected = { opt ->
                            selectedMatchType = opt.id
                            matchTypeExpanded = false
                        }
                    )
                }

                // Category Selector
                val currentCat = activeCategories.find { it.id == selectedCategoryId }
                OutlinedButton(
                    onClick = { categoryExpanded = true },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (currentCat != null) {
                        Icon(
                            imageVector = CategoryVisuals.getIcon(currentCat.iconKey),
                            contentDescription = null,
                            tint = CategoryVisuals.getColor(currentCat.colorKey),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "Category: ${currentCat?.name ?: "Select Category"}",
                        fontSize = 13.sp,
                        color = slateTitle
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = slateSub)
                }

                if (categoryExpanded) {
                    com.subhashrelangi.arctracker.ui.review.CategoryPickerDialog(
                        categories = activeCategories,
                        currentCategoryId = selectedCategoryId,
                        onCategorySelected = { cat ->
                            selectedCategoryId = cat.id
                            categoryExpanded = false
                        },
                        onDismissRequest = { categoryExpanded = false },
                        pickerStyle = com.subhashrelangi.arctracker.ui.review.CategoryPickerStyle.HORIZONTAL_SNAP
                    )
                }

                // Normalization Preview
                if (pattern.isNotBlank()) {
                    Text(
                        text = "Normalized Pattern: \"${MerchantNormalizer.normalize(pattern, stripPrefixes = false)}\"",
                        fontSize = 11.sp,
                        color = slateSub
                    )
                }

                errorMessage?.let { err ->
                    Text(
                        text = "⚠ $err",
                        fontSize = 12.sp,
                        color = Color(0xFFDC2626)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pattern.trim().isBlank()) {
                        errorMessage = "Pattern cannot be empty"
                        return@Button
                    }
                    if (selectedCategoryId.isBlank()) {
                        errorMessage = "Please select a category"
                        return@Button
                    }
                    onSave(pattern.trim(), selectedCategoryId, selectedMatchType, priority, name.trim().ifBlank { null })
                },
                colors = ButtonDefaults.buttonColors(containerColor = emeraldPrimary),
                shape = CircleShape
            ) {
                Text("Save Rule", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = slateSub, fontSize = 13.sp)
            }
        }
    )
}
