package com.subhashrelangi.arctracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.service.*
import com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val emeraldPrimary = Color(0xFF047857)
private val emeraldLight = Color(0xFFECFDF5)
private val emeraldBorder = Color(0xFFA7F3D0)
private val slateTitle = Color(0xFF0F172A)
private val slateSub = Color(0xFF64748B)
private val cardBorder = Color(0xFFE2E8F0)
private val lightBg = Color(0xFFF8FAFC)

/**
 * Historical Rule Application & Bulk Re-Categorization Screen (Milestone 12).
 *
 * Provides a user-driven, non-destructive workflow to preview and selectively apply
 * category rules and merchant aliases to pre-existing transactions in ArcTracker Emerald Light style.
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
        containerColor = lightBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                ArcTrackerHeader(
                    title = "Apply Category Rules",
                    subtitle = if (initialRuleId != null) "Re-categorize by selected rule" else "Historical re-categorization",
                    onBackClick = onNavigateBack
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(lightBg)
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
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, cardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(emeraldLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoFixHigh,
                                    contentDescription = null,
                                    tint = emeraldPrimary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = "Historical Rule Application",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = slateTitle
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Evaluate your existing transactions against active user rules, merchant aliases, and built-in rules.\n\nYou will be able to review all changes before anything is modified. Manual categories are strictly protected.",
                                fontSize = 13.sp,
                                color = slateSub,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 19.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { runPreview() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = emeraldPrimary)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Preview Changes", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                    CircularProgressIndicator(color = emeraldPrimary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Evaluating transactions against rules...",
                        fontSize = 13.5.sp,
                        color = slateSub
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

                    // Tab selector (Segmented Pills matching CategoryRulesScreen)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            0 to "Changes (${currentPreview.willChangeCount})",
                            1 to "Protected (${currentPreview.protectedCount})",
                            2 to "Matches (${currentPreview.alreadyMatchesCount})",
                            3 to "No Match (${currentPreview.cannotChangeCount})"
                        ).forEach { (index, title) ->
                            val isTabSelected = selectedTab == index
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (isTabSelected) emeraldPrimary else Color(0xFFF1F5F9))
                                    .border(
                                        BorderStroke(1.dp, if (isTabSelected) emeraldPrimary else cardBorder),
                                        RoundedCornerShape(999.dp)
                                    )
                                    .clickable { selectedTab = index }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isTabSelected) Color.White else slateSub
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = cardBorder, thickness = 1.dp)

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
                                        border = BorderStroke(1.dp, cardBorder),
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
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = emeraldPrimary,
                                                        uncheckedColor = Color(0xFF94A3B8)
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${selectedTransactionIds.size} of ${actionable.size} selected",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = slateTitle
                                                )
                                            }

                                            TextButton(
                                                onClick = { selectedTransactionIds = emptySet() },
                                                enabled = selectedTransactionIds.isNotEmpty()
                                            ) {
                                                Text(
                                                    text = "Clear All",
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (selectedTransactionIds.isNotEmpty()) emeraldPrimary else Color(0xFF94A3B8)
                                                )
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
                                        border = BorderStroke(1.dp, cardBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(modifier = Modifier.padding(16.dp)) {
                                            Button(
                                                onClick = { showConfirmDialog = true },
                                                enabled = selectedTransactionIds.isNotEmpty() && !isApplying,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = emeraldPrimary,
                                                    disabledContainerColor = Color(0xFFE2E8F0)
                                                )
                                            ) {
                                                if (isApplying) {
                                                    CircularProgressIndicator(
                                                        color = Color.White,
                                                        modifier = Modifier.size(18.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Applying Updates...", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Filled.DoneAll,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = if (selectedTransactionIds.isNotEmpty()) Color.White else Color(0xFF94A3B8)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "Apply Selected (${selectedTransactionIds.size})",
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (selectedTransactionIds.isNotEmpty()) Color.White else Color(0xFF94A3B8)
                                                    )
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
                                    icon = Icons.AutoMirrored.Filled.HelpOutline,
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
                    shape = RoundedCornerShape(20.dp),
                    containerColor = Color.White,
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEF3C7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "Apply Category Changes?",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = slateTitle
                        )
                    },
                    text = {
                        Text(
                            text = "You are about to re-categorize $selectedCount selected transaction(s).\n\nOnly category-related metadata will be updated. Amounts, merchants, dates, accounts, and manual categories remain completely untouched.",
                            fontSize = 13.sp,
                            color = slateSub,
                            lineHeight = 18.sp
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
                            colors = ButtonDefaults.buttonColors(containerColor = emeraldPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Apply $selectedCount Changes", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { showConfirmDialog = false },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Text("Cancel", color = slateSub, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }

            // Step 5: Result Dialog
            applyResult?.let { res ->
                AlertDialog(
                    onDismissRequest = { applyResult = null },
                    shape = RoundedCornerShape(20.dp),
                    containerColor = Color.White,
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (res.isSuccess) emeraldLight else Color(0xFFFEE2E2)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (res.isSuccess) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = if (res.isSuccess) emeraldPrimary else Color(0xFFDC2626),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    },
                    title = {
                        Text(
                            text = if (res.isSuccess) "Re-Categorization Complete" else "Operation Incomplete",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = slateTitle
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Successfully updated: ${res.successCount}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = slateTitle
                            )
                            if (res.skippedCount > 0) {
                                Text(
                                    text = "Skipped (protected or inactive): ${res.skippedCount}",
                                    fontSize = 12.5.sp,
                                    color = slateSub
                                )
                            }
                            if (res.failedCount > 0) {
                                Text(
                                    text = "Failed: ${res.failedCount}",
                                    fontSize = 12.5.sp,
                                    color = Color(0xFFDC2626)
                                )
                            }
                            if (res.errorMessage != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Error: ${res.errorMessage}",
                                    color = Color(0xFFDC2626),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { applyResult = null },
                            colors = ButtonDefaults.buttonColors(containerColor = emeraldPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold)
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
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Evaluation Summary",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = slateTitle
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFF1F5F9))
                            .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(999.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${preview.totalEvaluated} total",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = slateSub
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF1F5F9))
                        .clickable(onClick = onRefresh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Re-evaluate",
                        tint = slateTitle,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip(
                    label = "Will Change",
                    count = preview.willChangeCount,
                    textColor = emeraldPrimary,
                    bgColor = emeraldLight,
                    borderColor = emeraldBorder,
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    label = "Protected",
                    count = preview.protectedCount,
                    textColor = Color(0xFFB45309),
                    bgColor = Color(0xFFFEF3C7),
                    borderColor = Color(0xFFFDE68A),
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    label = "Matches",
                    count = preview.alreadyMatchesCount,
                    textColor = slateTitle,
                    bgColor = Color(0xFFF1F5F9),
                    borderColor = cardBorder,
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
    textColor: Color,
    bgColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = slateSub
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
    val formattedAmount = remember(item.amount) {
        NumberFormat.getNumberInstance(Locale("en", "IN")).format(item.amount.toLong())
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelection() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFF0FDF4) else Color.White
        ),
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) emeraldPrimary else cardBorder
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() },
                colors = CheckboxDefaults.colors(
                    checkedColor = emeraldPrimary,
                    uncheckedColor = Color(0xFF94A3B8)
                )
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.merchant.ifBlank { "Unknown Merchant" },
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = slateTitle,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "₹$formattedAmount",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = slateTitle
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = formattedDate,
                    fontSize = 11.5.sp,
                    color = slateSub
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Current Category -> Proposed Category Flow
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryPill(
                        name = item.currentCategoryName ?: "Uncategorized",
                        backgroundColor = Color(0xFFF1F5F9),
                        borderColor = cardBorder,
                        textColor = Color(0xFF475569)
                    )

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "to",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(13.dp)
                    )

                    CategoryPill(
                        name = item.proposedCategoryName ?: "Unknown",
                        backgroundColor = emeraldLight,
                        borderColor = emeraldBorder,
                        textColor = emeraldPrimary
                    )
                }

                if (!item.matchedRuleNameOrPattern.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Rule: ${item.matchedRuleNameOrPattern}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = emeraldPrimary
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
    val formattedAmount = remember(item.amount) {
        NumberFormat.getNumberInstance(Locale("en", "IN")).format(item.amount.toLong())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.merchant.ifBlank { "Unknown Merchant" },
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = slateTitle
                )
                Text(
                    text = "₹$formattedAmount",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = slateTitle
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = formattedDate,
                fontSize = 11.5.sp,
                color = slateSub
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategoryPill(
                    name = item.currentCategoryName ?: "Uncategorized",
                    backgroundColor = Color(0xFFFFFBEB),
                    borderColor = Color(0xFFFDE68A),
                    textColor = Color(0xFFB45309)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFEF3C7))
                        .border(BorderStroke(1.dp, Color(0xFFFDE68A)), RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "User Manual Assignment (Protected)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFB45309)
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
    val formattedAmount = remember(item.amount) {
        NumberFormat.getNumberInstance(Locale("en", "IN")).format(item.amount.toLong())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.merchant.ifBlank { "Unknown Merchant" },
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = slateTitle
                )
                Text(
                    text = "₹$formattedAmount",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = slateTitle
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = formattedDate,
                fontSize = 11.5.sp,
                color = slateSub
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategoryPill(
                    name = item.currentCategoryName ?: "Uncategorized",
                    backgroundColor = emeraldLight,
                    borderColor = emeraldBorder,
                    textColor = emeraldPrimary
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1F5F9))
                        .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Already matches rule result",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = slateSub
                    )
                }
            }
        }
    }
}

@Composable
private fun UnmatchedTransactionCard(item: HistoricalReassignmentPreviewItem) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val formattedDate = remember(item.dateMillis) { dateFormat.format(Date(item.dateMillis)) }
    val formattedAmount = remember(item.amount) {
        NumberFormat.getNumberInstance(Locale("en", "IN")).format(item.amount.toLong())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.merchant.ifBlank { "Unknown Merchant" },
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = slateTitle
                )
                Text(
                    text = "₹$formattedAmount",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = slateTitle
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = formattedDate,
                fontSize = 11.5.sp,
                color = slateSub
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategoryPill(
                    name = item.currentCategoryName ?: "Uncategorized",
                    backgroundColor = Color(0xFFF1F5F9),
                    borderColor = cardBorder,
                    textColor = Color(0xFF475569)
                )

                Text(
                    text = item.skipReason ?: "No rule matched",
                    fontSize = 11.sp,
                    color = slateSub
                )
            }
        }
    }
}

@Composable
private fun CategoryPill(
    name: String,
    backgroundColor: Color,
    borderColor: Color = cardBorder,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
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
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            fontSize = 13.sp,
            color = slateSub,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 19.sp
        )
    }
}
