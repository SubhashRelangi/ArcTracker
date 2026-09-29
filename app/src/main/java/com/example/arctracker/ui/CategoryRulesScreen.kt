package com.example.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arctracker.data.*
import com.example.arctracker.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val primaryColor = Color(0xFF6750A4)
private val cardBorderColor = Color(0xFFE0E0E0)
private val textSubColor = Color(0xFF757575)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryRulesScreen(
    onNavigateBack: () -> Unit,
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Category Rules",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "User-defined merchant categorization rules",
                            style = MaterialTheme.typography.bodySmall,
                            color = textSubColor
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = primaryColor,
                contentColor = Color.White
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Rule")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Interactive Test & Preview Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, cardBorderColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Rule Tester & Preview",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = testMerchant,
                            onValueChange = {
                                testMerchant = it
                                testResult = null
                            },
                            placeholder = { Text("Enter merchant name to test...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
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
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Text("Test", fontSize = 12.sp)
                        }
                    }

                    testResult?.let { res ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = if (res.status == CategoryInferenceStatus.HIGH_CONFIDENCE) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Inferred: ${res.suggestedCategoryName ?: "Uncategorized"} (${res.status})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (res.status == CategoryInferenceStatus.HIGH_CONFIDENCE) Color(0xFF2E7D32) else Color(0xFFE65100)
                                )
                                Text(
                                    text = res.reason,
                                    fontSize = 11.sp,
                                    color = Color.DarkGray
                                )
                            }
                        }
                    }
                }
            }

            // Tabs for Active vs Inactive
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Active (${activeRules.size})", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Inactive (${inactiveRules.size})", fontWeight = FontWeight.SemiBold) }
                )
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
                        Icon(
                            imageVector = Icons.Filled.Rule,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedTab == 0) "No active category rules" else "No inactive rules",
                            fontWeight = FontWeight.Medium,
                            color = textSubColor
                        )
                        Text(
                            text = "Tap + to teach ArcTracker custom merchant rules",
                            fontSize = 12.sp,
                            color = textSubColor
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            onDelete = { ruleToDelete = rule }
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
            title = { Text("Delete Rule", fontWeight = FontWeight.Bold) },
            text = {
                Text("Delete category rule for \"${rule.pattern}\"? Existing transaction categories will not be changed.")
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) {
                    Text("Cancel")
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
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isInvalid) Color(0xFFE57373) else cardBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            val catColor = targetCategory?.let { CategoryVisuals.getColor(it.colorKey) } ?: Color.Gray
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(catColor.copy(alpha = 0.15f), CircleShape),
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
                    fontSize = 14.sp
                )
                if (rule.name != null) {
                    Text(
                        text = "Pattern: ${rule.pattern}",
                        fontSize = 11.sp,
                        color = textSubColor
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Match Type Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFEDE7F6)
                    ) {
                        Text(
                            text = when (rule.matchType) {
                                UserRuleMatchType.MERCHANT_EXACT -> "Exact"
                                UserRuleMatchType.MERCHANT_TOKEN -> "Token"
                                UserRuleMatchType.MERCHANT_CONTAINS -> "Contains"
                                UserRuleMatchType.TRANSACTION_TEXT_CONTAINS -> "Text Contains"
                                else -> rule.matchType
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Text("→", fontSize = 11.sp, color = textSubColor)
                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = targetCategory?.name ?: "Unknown Category",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isInvalid) Color(0xFFC62828) else Color.Unspecified
                    )
                }

                if (isInvalid && invalidReason != null) {
                    Text(
                        text = "⚠ $invalidReason",
                        fontSize = 11.sp,
                        color = Color(0xFFC62828)
                    )
                }
            }

            // Actions
            Switch(
                checked = rule.isEnabled && !isInvalid,
                onCheckedChange = onToggle,
                enabled = !isInvalid
            )

            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFD32F2F), modifier = Modifier.size(20.dp))
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
        title = {
            Text(if (existingRule != null) "Edit Category Rule" else "Add Category Rule", fontWeight = FontWeight.Bold)
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
                    label = { Text("Merchant / Pattern *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rule Label (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Match Type Dropdown
                Box {
                    OutlinedButton(
                        onClick = { matchTypeExpanded = true },
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
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = matchTypeExpanded,
                        onDismissRequest = { matchTypeExpanded = false }
                    ) {
                        UserRuleMatchType.ALL.forEach { mt ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (mt) {
                                            UserRuleMatchType.MERCHANT_EXACT -> "Exact Merchant (e.g. 'Swiggy')"
                                            UserRuleMatchType.MERCHANT_TOKEN -> "Merchant Token (e.g. 'Amazon')"
                                            UserRuleMatchType.MERCHANT_CONTAINS -> "Merchant Contains (e.g. 'Coffee')"
                                            UserRuleMatchType.TRANSACTION_TEXT_CONTAINS -> "Transaction Text Contains"
                                            else -> mt
                                        }
                                    )
                                },
                                onClick = {
                                    selectedMatchType = mt
                                    matchTypeExpanded = false
                                }
                            )
                        }
                    }
                }

                // Category Dropdown
                Box {
                    val currentCat = activeCategories.find { it.id == selectedCategoryId }
                    OutlinedButton(
                        onClick = { categoryExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Category: ${currentCat?.name ?: "Select Category"}",
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        activeCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = CategoryVisuals.getIcon(cat.iconKey),
                                            contentDescription = null,
                                            tint = CategoryVisuals.getColor(cat.colorKey),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(cat.name)
                                    }
                                },
                                onClick = {
                                    selectedCategoryId = cat.id
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Normalization Preview
                if (pattern.isNotBlank()) {
                    Text(
                        text = "Normalized Pattern: \"${MerchantNormalizer.normalize(pattern, stripPrefixes = false)}\"",
                        fontSize = 11.sp,
                        color = textSubColor
                    )
                }

                errorMessage?.let { err ->
                    Text(
                        text = "⚠ $err",
                        fontSize = 12.sp,
                        color = Color(0xFFC62828)
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
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
