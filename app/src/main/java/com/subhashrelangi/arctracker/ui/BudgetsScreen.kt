package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.service.AndroidBudgetAlertNotifier
import com.subhashrelangi.arctracker.service.BudgetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    onNavigateBack: () -> Unit,
    manager: BudgetManager? = null,
    categoryManager: com.subhashrelangi.arctracker.service.CategoryManager? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val budgetManager = remember {
        manager ?: run {
            val db = AppDatabase.getDatabase(context)
            BudgetManager(
                budgetDao = db.budgetDao(),
                expenseDao = db.expenseDao(),
                categoryDao = db.transactionCategoryDao(),
                alertNotifier = AndroidBudgetAlertNotifier(context)
            )
        }
    }

    val catManager = remember {
        categoryManager ?: run {
            val db = AppDatabase.getDatabase(context)
            com.subhashrelangi.arctracker.service.CategoryManager(
                categoryDao = db.transactionCategoryDao(),
                expenseDao = db.expenseDao(),
                database = db,
                ruleDao = db.userCategoryRuleDao(),
                budgetDao = db.budgetDao()
            )
        }
    }

    val budgetsProgress by budgetManager.observeAllBudgetsProgress().collectAsState(initial = emptyList())
    val allCategories by catManager.getActiveCategoriesFlow().collectAsState(initial = emptyList())

    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }

    // Dialog states
    var showAddDialog by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<Budget?>(null) }
    var deletingBudget by remember { mutableStateOf<Budget?>(null) }
    var viewingProgress by remember { mutableStateOf<BudgetProgress?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Budgets & Limits",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Track and control recurring spending",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Budget")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Budget", tint = Color.White)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (budgetsProgress.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Budgets Configured",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Set up weekly, monthly, or yearly spending limits for specific categories or your overall expenses.",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { showAddDialog = true }) {
                                Text("Create Budget")
                            }
                        }
                    }
                }
            } else {
                items(budgetsProgress, key = { it.budget.id }) { progress ->
                    BudgetCard(
                        progress = progress,
                        currencyFormatter = currencyFormatter,
                        onClick = { viewingProgress = progress },
                        onEdit = { editingBudget = progress.budget },
                        onDelete = { deletingBudget = progress.budget },
                        onToggleEnabled = {
                            scope.launch {
                                budgetManager.setBudgetEnabled(progress.budget.id, !progress.budget.isEnabled)
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Add / Edit Budget Dialog
    if (showAddDialog || editingBudget != null) {
        val initialBudget = editingBudget
        BudgetEditDialog(
            budget = initialBudget,
            categories = allCategories,
            onDismiss = {
                showAddDialog = false
                editingBudget = null
            },
            onSave = { name, amount, catId, period, warningThresh, exceededThresh, isEnabled ->
                scope.launch {
                    if (initialBudget == null) {
                        budgetManager.createBudget(
                            name = name,
                            amountLimit = amount,
                            categoryId = catId,
                            periodType = period,
                            warningThreshold = warningThresh,
                            exceededThreshold = exceededThresh
                        )
                    } else {
                        budgetManager.updateBudget(
                            initialBudget.copy(
                                name = name,
                                amountLimit = amount,
                                categoryId = catId,
                                warningThreshold = warningThresh,
                                exceededThreshold = exceededThresh,
                                isEnabled = isEnabled
                            )
                        )
                    }
                    showAddDialog = false
                    editingBudget = null
                }
            }
        )
    }

    // Delete Confirmation Dialog
    deletingBudget?.let { budget ->
        AlertDialog(
            onDismissRequest = { deletingBudget = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Budget?") },
            text = {
                Text("Are you sure you want to delete the budget \"${budget.name}\"?\n\nYour transactions and spending records will remain completely untouched.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            budgetManager.deleteBudget(budget.id)
                            deletingBudget = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingBudget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Detail Dialog
    viewingProgress?.let { progress ->
        BudgetDetailDialog(
            progress = progress,
            currencyFormatter = currencyFormatter,
            budgetManager = budgetManager,
            onDismiss = { viewingProgress = null }
        )
    }
}

@Composable
fun BudgetCard(
    progress: BudgetProgress,
    currencyFormatter: NumberFormat,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    val budget = progress.budget
    val isEnabled = budget.isEnabled

    val statusColor = when {
        !isEnabled -> Color.Gray
        progress.isExceeded -> Color(0xFFD32F2F)
        progress.isWarning -> Color(0xFFF57C00)
        else -> Color(0xFF2E7D32)
    }

    val statusText = when {
        !isEnabled -> "Paused"
        progress.isExceeded -> "Exceeded"
        progress.isWarning -> "Warning"
        else -> "On Track"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (progress.isExceeded && isEnabled) Color(0xFFFFCDD2) else Color(0xFFE0E0E0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = if (budget.isOverallBudget) Icons.Filled.AccountBalanceWallet else CategoryVisuals.getIcon(progress.categoryIconKey)
                val iconBg = if (budget.isOverallBudget) Color(0xFFEDE7F6) else CategoryVisuals.getColor(progress.categoryColorKey).copy(alpha = 0.15f)
                val iconTint = if (budget.isOverallBudget) Color(0xFF673AB7) else CategoryVisuals.getColor(progress.categoryColorKey)

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(iconBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = budget.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${budget.parsedPeriodType.name.lowercase().replaceFirstChar { it.uppercase() }} • ${progress.categoryName}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Amount and Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "${currencyFormatter.format(progress.spent)} / ${currencyFormatter.format(progress.limit)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "${String.format(Locale.US, "%.0f", progress.percentageUsed)}%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = statusColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { (progress.percentageUsed / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = statusColor,
                trackColor = Color(0xFFF0F0F0)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Footer info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val remainingText = if (progress.isExceeded) {
                    "Over by ${currencyFormatter.format(progress.spent - progress.limit)}"
                } else {
                    "Remaining: ${currencyFormatter.format(progress.remaining)}"
                }
                Text(
                    text = remainingText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (progress.isExceeded) Color(0xFFD32F2F) else Color.DarkGray
                )
                Text(
                    text = "Ends in ${progress.daysRemaining} days",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            // Warning if category archived or deleted
            if (progress.isCategoryArchived || progress.isCategoryDeleted) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFF3E0),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val warningMsg = if (progress.isCategoryDeleted) "Category deleted. Budget paused." else "Category archived."
                    Text(
                        text = "⚠️ $warningMsg",
                        fontSize = 11.sp,
                        color = Color(0xFFE65100),
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }

            // Quick Actions Row
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFF5F5F5))
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onToggleEnabled) {
                    Text(if (isEnabled) "Pause" else "Resume", fontSize = 12.sp)
                }
                TextButton(onClick = onEdit) {
                    Text("Edit", fontSize = 12.sp)
                }
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete", fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetEditDialog(
    budget: Budget?,
    categories: List<TransactionCategory>,
    onDismiss: () -> Unit,
    onSave: (name: String, amount: Double, catId: String?, period: BudgetPeriodType, warning: Double, exceeded: Double, isEnabled: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(budget?.name ?: "") }
    var amountText by remember { mutableStateOf(budget?.amountLimit?.let { if (it % 1 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var isOverall by remember { mutableStateOf(budget?.isOverallBudget ?: false) }
    var selectedCategoryId by remember { mutableStateOf(budget?.categoryId ?: categories.firstOrNull()?.id) }
    var selectedPeriod by remember { mutableStateOf(budget?.parsedPeriodType ?: BudgetPeriodType.MONTHLY) }
    var warningThresholdText by remember { mutableStateOf(budget?.warningThreshold?.toInt()?.toString() ?: "80") }
    var exceededThresholdText by remember { mutableStateOf(budget?.exceededThreshold?.toInt()?.toString() ?: "100") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (budget == null) "Create Budget" else "Edit Budget") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                errorMessage?.let { msg ->
                    Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("Budget Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; errorMessage = null },
                    label = { Text("Limit Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Overall vs Category Toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isOverall,
                        onCheckedChange = { isOverall = it }
                    )
                    Text("Overall Budget (All Expenses)", fontSize = 13.sp)
                }

                // Category selector
                if (!isOverall) {
                    var expanded by remember { mutableStateOf(false) }
                    val currentCat = categories.find { it.id == selectedCategoryId }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = currentCat?.name ?: "Select Category",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        selectedCategoryId = cat.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Period Selector
                var periodExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = periodExpanded,
                    onExpandedChange = { periodExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedPeriod.name.lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Period") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = periodExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = periodExpanded,
                        onDismissRequest = { periodExpanded = false }
                    ) {
                        BudgetPeriodType.values().forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    selectedPeriod = p
                                    periodExpanded = false
                                }
                            )
                        }
                    }
                }

                // Warning & Exceeded Thresholds
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = warningThresholdText,
                        onValueChange = { warningThresholdText = it },
                        label = { Text("Warning %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = exceededThresholdText,
                        onValueChange = { exceededThresholdText = it },
                        label = { Text("Exceeded %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    val warning = warningThresholdText.toDoubleOrNull() ?: 80.0
                    val exceeded = exceededThresholdText.toDoubleOrNull() ?: 100.0

                    if (name.isBlank()) {
                        errorMessage = "Please enter a budget name"
                        return@Button
                    }
                    if (amount == null || amount <= 0.0) {
                        errorMessage = "Please enter a valid positive limit"
                        return@Button
                    }
                    if (!isOverall && selectedCategoryId == null) {
                        errorMessage = "Please choose a category"
                        return@Button
                    }
                    if (warning <= 0 || warning > exceeded) {
                        errorMessage = "Warning threshold must be > 0 and <= exceeded threshold"
                        return@Button
                    }

                    onSave(
                        name,
                        amount,
                        if (isOverall) null else selectedCategoryId,
                        selectedPeriod,
                        warning,
                        exceeded,
                        budget?.isEnabled ?: true
                    )
                }
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

@Composable
fun BudgetDetailDialog(
    progress: BudgetProgress,
    currencyFormatter: NumberFormat,
    budgetManager: BudgetManager,
    onDismiss: () -> Unit
) {
    var contributingTransactions by remember { mutableStateOf<List<Expense>>(emptyList()) }
    val dateFormatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.US) }

    LaunchedEffect(progress.budget.id) {
        withContext(Dispatchers.IO) {
            contributingTransactions = budgetManager.getContributingTransactions(progress.budget)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(progress.budget.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    text = "${progress.categoryName} • ${progress.budget.parsedPeriodType.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Limit:", fontSize = 12.sp, color = Color.Gray)
                            Text(currencyFormatter.format(progress.limit), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Spent:", fontSize = 12.sp, color = Color.Gray)
                            Text(currencyFormatter.format(progress.spent), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Remaining:", fontSize = 12.sp, color = Color.Gray)
                            Text(currencyFormatter.format(progress.remaining), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Used:", fontSize = 12.sp, color = Color.Gray)
                            Text("${String.format(Locale.US, "%.1f", progress.percentageUsed)}%", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        if (progress.projectedSpent > progress.spent) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Projected:", fontSize = 11.sp, color = Color.Gray)
                                Text("~${currencyFormatter.format(progress.projectedSpent)} (est.)", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                Text(
                    text = "Contributing Transactions (${contributingTransactions.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                if (contributingTransactions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("No spending in this period yet", fontSize = 12.sp, color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(contributingTransactions) { exp ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(exp.merchant, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text(dateFormatter.format(Date(exp.dateMillis)), fontSize = 10.sp, color = Color.Gray)
                                }
                                Text(
                                    text = currencyFormatter.format(exp.amount),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFFD32F2F)
                                )
                            }
                            HorizontalDivider(color = Color(0xFFF5F5F5))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
