package com.example.arctracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.example.arctracker.data.AppDatabase
import com.example.arctracker.service.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Historical Rule Application & Bulk Re-Categorization Screen (Milestone 12).
 *
 * Provides a user-driven, non-destructive workflow to preview and selectively apply
 * category rules and merchant aliases to pre-existing transactions.
 *
 * Invariants:
 * - USER_ASSIGNED transactions are strictly protected and cannot be selected/modified.
 * - Non-category fields (amount, merchant, date, accounts, etc.) are strictly preserved.
 * - Selection is based purely on unique transaction IDs.
 * - Execution is atomic and transactional.
 * - Never runs automatically in the background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricalRuleApplicationScreen(
    initialRuleId: String? = null,
    onNavigateBack: () -> Unit,
    customApplier: HistoricalRuleApplier? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val applier = remember {
        customApplier ?: run {
            val db = AppDatabase.getDatabase(context)
            HistoricalRuleApplier(
                expenseDao = db.expenseDao(),
                categoryDao = db.transactionCategoryDao(),
                ruleDao = db.userCategoryRuleDao(),
                aliasDao = db.merchantAliasDao(),
                database = db
            )
        }
    }

    var isEvaluating by remember { mutableStateOf(false) }
    var isApplying by remember { mutableStateOf(false) }
    var previewResult by remember { mutableStateOf<HistoricalRulePreviewResult?>(null) }
    var selectedTransactionIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var applyResult by remember { mutableStateOf<BulkReassignmentResult?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Changes, 1: Protected, 2: Already Matches, 3: No Match

    val purpleColor = Color(0xFF6750A4)
    val subtitleColor = Color(0xFF757575)
    val successColor = Color(0xFF2E7D32)
    val amberColor = Color(0xFFF57F17)

    fun runPreview() {
        isEvaluating = true
        applyResult = null
        scope.launch {
            try {
                val res = applier.generatePreview(specificRuleId = initialRuleId)
                previewResult = res
                // Default: pre-select all actionable items that will change
                selectedTransactionIds = res.actionableItems.map { it.transactionId }.toSet()
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Failed to evaluate rules: ${e.message}")
            } finally {
                isEvaluating = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Apply Category Rules",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (initialRuleId != null) "Re-categorize by selected rule" else "Historical re-categorization",
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleColor
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8F9FA))
        ) {
            val currentPreview = previewResult

            if (currentPreview == null && !isEvaluating) {
                // Step 1: Initial Prompt / Explainer Card
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color(0xFFEADDFF), RoundedCornerShape(32.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoFixHigh,
                                    contentDescription = null,
                                    tint = purpleColor,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Historical Rule Application",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Evaluate your existing transactions against active user rules, merchant aliases, and built-in rules.\n\nYou will be able to review all changes before anything is modified. Manual categories are strictly protected.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = subtitleColor,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { runPreview() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Preview Changes", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else if (isEvaluating) {
                // Evaluating progress indicator
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = purpleColor)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Evaluating transactions against rules...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = subtitleColor
                    )
                }
            } else if (currentPreview != null) {
                // Step 2 & 3: Preview and Selection
                Column(modifier = Modifier.fillMaxSize()) {
                    // Summary metric cards
                    SummaryHeader(
                        preview = currentPreview,
                        onRefresh = { runPreview() }
                    )

                    // Tab selector
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.White,
                        contentColor = purpleColor
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Changes (${currentPreview.willChangeCount})") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Protected (${currentPreview.protectedCount})") }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("Matches (${currentPreview.alreadyMatchesCount})") }
                        )
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            text = { Text("No Match (${currentPreview.cannotChangeCount})") }
                        )
                    }

                    // Content depending on selected tab
                    when (selectedTab) {
                        0 -> {
                            // Actionable Changes Tab
                            val actionable = currentPreview.actionableItems
                            if (actionable.isEmpty()) {
                                EmptyTabState(
                                    icon = Icons.Filled.CheckCircleOutline,
                                    message = "No transactions require category changes.\nAll transactions either match current rules or are protected."
                                )
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // Selection control bar
                                    Surface(
                                        color = Color.White,
                                        tonalElevation = 1.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.clickable {
                                                    selectedTransactionIds = if (selectedTransactionIds.size == actionable.size) {
                                                        emptySet()
                                                    } else {
                                                        actionable.map { it.transactionId }.toSet()
                                                    }
                                                }
                                            ) {
                                                Checkbox(
                                                    checked = selectedTransactionIds.size == actionable.size && actionable.isNotEmpty(),
                                                    onCheckedChange = { checked ->
                                                        selectedTransactionIds = if (checked) {
                                                            actionable.map { it.transactionId }.toSet()
                                                        } else {
                                                            emptySet()
                                                        }
                                                    },
                                                    colors = CheckboxDefaults.colors(checkedColor = purpleColor)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${selectedTransactionIds.size} of ${actionable.size} selected",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }

                                            TextButton(
                                                onClick = { selectedTransactionIds = emptySet() },
                                                enabled = selectedTransactionIds.isNotEmpty()
                                            ) {
                                                Text("Clear All")
                                            }
                                        }
                                    }

                                    // List of actionable transactions
                                    LazyColumn(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        contentPadding = PaddingValues(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(actionable, key = { it.transactionId }) { item ->
                                            val isSelected = selectedTransactionIds.contains(item.transactionId)
                                            PreviewTransactionCard(
                                                item = item,
                                                isSelected = isSelected,
                                                onToggleSelection = {
                                                    selectedTransactionIds = if (isSelected) {
                                                        selectedTransactionIds - item.transactionId
                                                    } else {
                                                        selectedTransactionIds + item.transactionId
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    // Bottom Apply Button
                                    Surface(
                                        color = Color.White,
                                        shadowElevation = 8.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(modifier = Modifier.padding(16.dp)) {
                                            Button(
                                                onClick = { showConfirmDialog = true },
                                                enabled = selectedTransactionIds.isNotEmpty() && !isApplying,
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                                            ) {
                                                if (isApplying) {
                                                    CircularProgressIndicator(
                                                        color = Color.White,
                                                        modifier = Modifier.size(20.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Applying Updates...")
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Filled.DoneAll,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Apply Selected (${selectedTransactionIds.size})")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            // Protected Transactions Tab
                            val protectedItems = currentPreview.items.filter { it.changeStatus == PreviewChangeStatus.PROTECTED_USER_ASSIGNED }
                            if (protectedItems.isEmpty()) {
                                EmptyTabState(
                                    icon = Icons.Filled.Shield,
                                    message = "No transactions with user-assigned categories found."
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(protectedItems, key = { it.transactionId }) { item ->
                                        ProtectedTransactionCard(item = item)
                                    }
                                }
                            }
                        }
                        2 -> {
                            // Already Matching Tab
                            val matchingItems = currentPreview.items.filter { it.changeStatus == PreviewChangeStatus.ALREADY_MATCHES }
                            if (matchingItems.isEmpty()) {
                                EmptyTabState(
                                    icon = Icons.Filled.Check,
                                    message = "No transactions already matched."
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(matchingItems, key = { it.transactionId }) { item ->
                                        MatchingTransactionCard(item = item)
                                    }
                                }
                            }
                        }
                        3 -> {
                            // No Match / Inactive Tab
                            val unmatchable = currentPreview.items.filter {
                                it.changeStatus == PreviewChangeStatus.NO_MATCH ||
                                it.changeStatus == PreviewChangeStatus.INACTIVE_OR_ARCHIVED_CATEGORY
                            }
                            if (unmatchable.isEmpty()) {
                                EmptyTabState(
                                    icon = Icons.Filled.HelpOutline,
                                    message = "No unclassifiable transactions found."
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(unmatchable, key = { it.transactionId }) { item ->
                                        UnmatchedTransactionCard(item = item)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Step 4: Confirmation Dialog
            if (showConfirmDialog) {
                val selectedCount = selectedTransactionIds.size
                AlertDialog(
                    onDismissRequest = { showConfirmDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = amberColor,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "Apply Category Changes?",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "You are about to re-categorize $selectedCount selected transaction(s).\n\nOnly category-related metadata will be updated. Amounts, merchants, dates, accounts, and manual categories remain completely untouched."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showConfirmDialog = false
                                isApplying = true
                                scope.launch {
                                    val itemsToApply = currentPreview?.actionableItems?.filter {
                                        it.transactionId in selectedTransactionIds
                                    } ?: emptyList()

                                    val res = applier.applyReassignments(itemsToApply)
                                    applyResult = res
                                    isApplying = false
                                    // Refresh preview after apply
                                    runPreview()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                        ) {
                            Text("Apply $selectedCount Changes")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Step 5: Result Dialog / Banner
            applyResult?.let { res ->
                AlertDialog(
                    onDismissRequest = { applyResult = null },
                    icon = {
                        Icon(
                            imageVector = if (res.isSuccess) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = if (res.isSuccess) successColor else Color.Red,
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = {
                        Text(
                            text = if (res.isSuccess) "Re-Categorization Complete" else "Operation Incomplete",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column {
                            Text("Successfully updated: ${res.successCount}")
                            if (res.skippedCount > 0) {
                                Text("Skipped (protected or inactive): ${res.skippedCount}")
                            }
                            if (res.failedCount > 0) {
                                Text("Failed: ${res.failedCount}", color = Color.Red)
                            }
                            if (res.errorMessage != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Error: ${res.errorMessage}", color = Color.Red, fontSize = 12.sp)
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { applyResult = null },
                            colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                        ) {
                            Text("Done")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SummaryHeader(
    preview: HistoricalRulePreviewResult,
    onRefresh: () -> Unit
) {
    Surface(
        color = Color.White,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Evaluation Summary (${preview.totalEvaluated} total)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Re-evaluate",
                        tint = Color(0xFF6750A4)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip(
                    label = "Will Change",
                    count = preview.willChangeCount,
                    color = Color(0xFF7C4DFF),
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    label = "Protected",
                    count = preview.protectedCount,
                    color = Color(0xFFF57F17),
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    label = "Matches",
                    count = preview.alreadyMatchesCount,
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricChip(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.DarkGray
            )
        }
    }
}

@Composable
private fun PreviewTransactionCard(
    item: HistoricalReassignmentPreviewItem,
    isSelected: Boolean,
    onToggleSelection: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val formattedDate = remember(item.dateMillis) { dateFormat.format(Date(item.dateMillis)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelection() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() },
                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF6750A4))
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.merchant.ifBlank { "Unknown Merchant" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "₹${"%.2f".format(item.amount)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E88E5)
                    )
                }

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Current Category -> Proposed Category Flow
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryPill(
                        name = item.currentCategoryName ?: "Uncategorized",
                        backgroundColor = Color(0xFFEEEEEE),
                        textColor = Color.DarkGray
                    )

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "to",
                        tint = Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )

                    CategoryPill(
                        name = item.proposedCategoryName ?: "Unknown",
                        backgroundColor = Color(0xFFEADDFF),
                        textColor = Color(0xFF6750A4)
                    )
                }

                if (!item.matchedRuleNameOrPattern.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Rule: ${item.matchedRuleNameOrPattern}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6750A4)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtectedTransactionCard(item: HistoricalReassignmentPreviewItem) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val formattedDate = remember(item.dateMillis) { dateFormat.format(Date(item.dateMillis)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.merchant.ifBlank { "Unknown Merchant" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "₹${"%.2f".format(item.amount)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategoryPill(
                    name = item.currentCategoryName ?: "Uncategorized",
                    backgroundColor = Color(0xFFFFF3E0),
                    textColor = Color(0xFFE65100)
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFFFF8E1)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = Color(0xFFF57F17),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "User Manual Assignment (Protected)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFF57F17)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchingTransactionCard(item: HistoricalReassignmentPreviewItem) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val formattedDate = remember(item.dateMillis) { dateFormat.format(Date(item.dateMillis)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.merchant.ifBlank { "Unknown Merchant" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "₹${"%.2f".format(item.amount)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategoryPill(
                    name = item.currentCategoryName ?: "Uncategorized",
                    backgroundColor = Color(0xFFE8F5E9),
                    textColor = Color(0xFF2E7D32)
                )

                Text(
                    text = "Already matches rule result",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32)
                )
            }
        }
    }
}

@Composable
private fun UnmatchedTransactionCard(item: HistoricalReassignmentPreviewItem) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val formattedDate = remember(item.dateMillis) { dateFormat.format(Date(item.dateMillis)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.merchant.ifBlank { "Unknown Merchant" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "₹${"%.2f".format(item.amount)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategoryPill(
                    name = item.currentCategoryName ?: "Uncategorized",
                    backgroundColor = Color(0xFFEEEEEE),
                    textColor = Color.DarkGray
                )

                Text(
                    text = item.skipReason ?: "No rule matched",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun CategoryPill(
    name: String,
    backgroundColor: Color,
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyTabState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.LightGray,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}
