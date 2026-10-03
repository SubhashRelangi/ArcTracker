package com.subhashrelangi.arctracker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.subhashrelangi.arctracker.R
import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.service.AccountReconciliationManager
import com.subhashrelangi.arctracker.service.InstrumentType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Transaction Details & Evidence Screen for ArcTracker.
 *
 * EXACT VISUAL RECODE matching Ui-Designs/TrasactionDeatilsPage.png:
 * - Top header with Back arrow, "Transaction Details", green status dot, "LOCAL VAULT", green shield squircle, and white circular ArcTracker brand button
 * - Sub-header ID pill "#TXN-YYYYMMDD-0042", "Edit" pill button, and red trash button
 * - Large Transaction Hero Card with category squircle icon, merchant name, secondary description, DEBIT / EXPENSE badge,
 *   prominent settled amount typography in red for debit, timestamp with seconds and IST timezone, and green Reconciled pill
 * - ACCOUNT Section with bank icon, bold "ACCOUNT", green "Matched automatically" badge, nested account card with bank logo,
 *   display name, masked suffix (•• 4821), Reassign Account and Unlink buttons
 * - CATEGORY Section with shapes icon, bold "CATEGORY", Auto-categorized badge, nested card with active category icon,
 *   sub-label ACTIVE CATEGORY, category name, and Change > button
 * - TRANSACTION METADATA Card with UPI Reference and copy action, Balance After, and humanized Source
 * - SOURCE EVIDENCE Section with envelope icon, title, subtitle, and collapsible/expandable "View original message >"
 *   revealing the authentic immutable raw message text from Room
 * - NOTES & TAGS Section with notes icon, bold title, "Edit Note" action, note text body, hashtag chips, and + Add Tag
 * - Anchored floating pill-shaped bottom action bar with "Export Receipt / JSON" and white "Done" button with check icon
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailDialog(
    expense: Expense,
    knownAccounts: List<KnownFinancialAccount> = emptyList(),
    allCategories: List<TransactionCategory> = emptyList(),
    onDismiss: () -> Unit,
    onSaveEdit: (expenseId: Int, merchant: String, category: String?, note: String?) -> Unit,
    onAssignAccount: ((expenseId: Int, targetAccountId: String) -> Unit)? = null,
    onReassignAccount: ((expenseId: Int, newAccountId: String) -> Unit)? = null,
    onUnlinkAccount: ((expenseId: Int) -> Unit)? = null,
    onDelete: ((expense: Expense) -> Unit)? = null
) {
    val context = LocalContext.current

    // Edit Mode State
    var isEditMode by remember { mutableStateOf(false) }
    var editedMerchant by remember(expense) { mutableStateOf(expense.merchant) }
    var editedCategory by remember(expense) { mutableStateOf(expense.tag ?: expense.category ?: "") }
    var editedCategoryId by remember(expense) { mutableStateOf(expense.categoryId) }
    var editedNote by remember(expense) { mutableStateOf(expense.note ?: "") }
    var merchantError by remember { mutableStateOf<String?>(null) }

    // Dialogs State
    var showCategoryPickerDialog by remember { mutableStateOf(false) }
    var showAccountPickerDialog by remember { mutableStateOf(false) }
    var isReassignPicker by remember { mutableStateOf(false) }
    var showUnlinkConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }

    // Expandable Source Evidence State (default collapsed per specification)
    var isEvidenceExpanded by remember { mutableStateOf(false) }

    // Tags State
    var currentTags by remember(expense.note, expense.tag) {
        mutableStateOf(extractTags(expense.note, expense.tag))
    }

    // Linked Account lookup
    val linkedAccount = remember(expense.accountId, knownAccounts) {
        knownAccounts.find { it.id == expense.accountId }
    }

    // Direction & Financial Typography
    val isCredit = expense.type.equals("Credit", ignoreCase = true)
    val sign = if (isCredit) "+" else "-"

    val formattedDateTime = remember(expense.dateMillis) {
        if (expense.dateMillis > 0) {
            val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(expense.dateMillis))
            val timeStr = SimpleDateFormat("h:mm a", Locale.US).format(Date(expense.dateMillis))
            "$dateStr • $timeStr IST"
        } else {
            "-"
        }
    }
    val dateCode = remember(expense.dateMillis) {
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(expense.dateMillis))
    }
    val txnCode = "#TXN-$dateCode-${expense.id.toString().padStart(4, '0')}"

    val transactionTitle = remember(expense.merchant, expense.category, expense.tag) {
        when {
            expense.merchant.isNotBlank() && expense.merchant != "-" -> expense.merchant
            !expense.category.isNullOrBlank() -> expense.category!!
            !expense.tag.isNullOrBlank() -> expense.tag!!
            else -> "Transaction"
        }
    }

    // Extraction of UPI Ref, Balance, Source
    val upiRef = remember(expense.rawText, expense.note, expense.notificationKey) {
        extractUpiRef(expense.rawText, expense.note, expense.notificationKey)
    }

    val postTxnBalance = remember(expense.rawText) {
        extractBalance(expense.rawText) ?: "-"
    }

    val humanizedSource = remember(expense.source) {
        when {
            expense.source.equals("SMS_HISTORY", ignoreCase = true) -> "SMS · Historical Import"
            expense.source.equals("SMS", ignoreCase = true) -> "SMS · Live Ingestion"
            expense.source.equals("NOTIFICATION", ignoreCase = true) -> "Payment Notification"
            expense.source.equals("MANUAL", ignoreCase = true) -> "Manual Entry"
            expense.source.isNotBlank() -> expense.source
            else -> "-"
        }
    }

    // Resolved Category
    val resolvedCategory = remember(expense, allCategories, editedCategoryId, editedCategory) {
        allCategories.find { it.id == editedCategoryId || it.name.equals(editedCategory, ignoreCase = true) }
            ?: BuiltInCategories.findLegacyMapping(editedCategory)
    }
    val categoryIconKey = resolvedCategory?.iconKey ?: "restaurant"
    val categoryDisplayName = resolvedCategory?.name
        ?: editedCategory.ifBlank { expense.category ?: expense.tag ?: "-" }

    // Secondary Description for Hero card
    val secondaryDescription = remember(expense.merchant, categoryDisplayName, expense.source, expense.category, expense.tag) {
        when {
            !expense.category.isNullOrBlank() -> expense.category!!
            !expense.tag.isNullOrBlank() -> expense.tag!!
            categoryDisplayName.isNotBlank() && categoryDisplayName != "-" -> categoryDisplayName
            else -> "-"
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isEditMode) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0D0F14)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. SCROLLABLE SCREEN CONTENT (FULL SCREEN)
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // TOP APP BAR
                    TransactionDetailsHeader(
                        modifier = Modifier.statusBarsPadding(),
                        onBackClick = {
                            if (isEditMode) {
                                editedMerchant = expense.merchant
                                editedCategory = expense.tag ?: expense.category ?: ""
                                editedCategoryId = expense.categoryId
                                editedNote = expense.note ?: ""
                                merchantError = null
                                isEditMode = false
                            } else {
                                onDismiss()
                            }
                        }
                    )

                    // SCROLLABLE MAIN CONTENT
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)
                    ) {
                        // 2a. SUB-HEADER / IDENTIFICATION ROW
                        item {
                            TransactionIdentityRow(
                                txnCode = txnCode,
                                isEditMode = isEditMode,
                                onToggleEdit = {
                                    if (isEditMode) {
                                        editedMerchant = expense.merchant
                                        editedCategory = expense.tag ?: expense.category ?: ""
                                        editedCategoryId = expense.categoryId
                                        editedNote = expense.note ?: ""
                                        merchantError = null
                                        isEditMode = false
                                    } else {
                                        isEditMode = true
                                    }
                                },
                                onDeleteClick = { showDeleteConfirmDialog = true }
                            )
                        }

                        // 2b. TRANSACTION HERO CARD
                        item {
                            TransactionHeroCard(
                                merchant = if (isEditMode) editedMerchant else transactionTitle,
                                onMerchantChange = {
                                    editedMerchant = it
                                    if (it.isNotBlank()) merchantError = null
                                },
                                merchantError = merchantError,
                                secondaryDescription = secondaryDescription,
                                isEditMode = isEditMode,
                                isCredit = isCredit,
                                sign = sign,
                                amount = expense.amount,
                                formattedDateTime = formattedDateTime,
                                categoryIconKey = categoryIconKey,
                                isReconciled = expense.accountId != null,
                                isPending = expense.isPending
                            )
                        }

                        // 2c. ACCOUNT SECTION & CARD
                        item {
                            TransactionAccountSection(
                                linkedAccount = linkedAccount,
                                accountSuffix = expense.accountSuffix,
                                onReassign = {
                                    isReassignPicker = true
                                    showAccountPickerDialog = true
                                },
                                onUnlink = { showUnlinkConfirmDialog = true },
                                onAssign = {
                                    isReassignPicker = false
                                    showAccountPickerDialog = true
                                }
                            )
                        }

                        // 2d. CATEGORY SECTION & CARD
                        item {
                            TransactionCategorySection(
                                categoryName = categoryDisplayName,
                                categoryIconKey = categoryIconKey,
                                isAutoCategorized = expense.categorySource != "USER_ASSIGNED",
                                onChangeCategoryClick = { showCategoryPickerDialog = true }
                            )
                        }

                        // 2e. TRANSACTION METADATA CARD
                        item {
                            TransactionMetadataCard(
                                upiRef = upiRef,
                                balanceAfter = postTxnBalance,
                                source = humanizedSource,
                                onCopyUpiRef = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("UPI Reference", upiRef)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "UPI reference copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        // 2f. SOURCE EVIDENCE SECTION & CARD
                        item {
                            TransactionSourceEvidenceSection(
                                sourceTitle = humanizedSource,
                                rawText = expense.rawText,
                                isExpanded = isEvidenceExpanded,
                                onToggleExpand = { isEvidenceExpanded = !isEvidenceExpanded }
                            )
                        }

                        // 2g. NOTES & TAGS SECTION & CARD
                        item {
                            TransactionNotesSection(
                                note = if (isEditMode) editedNote else (editedNote.ifBlank { expense.note ?: "" }),
                                onNoteChange = { editedNote = it },
                                isEditMode = isEditMode,
                                tags = currentTags,
                                onAddTagClick = { showAddTagDialog = true },
                                onEditNoteClick = { isEditMode = true }
                            )
                        }
                    }
                }

                // 2. UNIVERSAL FLOATING ACTION BAR (Floating pill in exact same position as ArcTrackerBottomNavigationBar)
                val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val bottomOffset = if (navBarBottom > 0.dp) navBarBottom + 26.dp else 38.dp
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = bottomOffset)
                ) {
                    TransactionBottomActions(
                        isEditMode = isEditMode,
                        onExportClick = {
                            exportTransactionReceipt(context, expense, linkedAccount, txnCode, upiRef)
                        },
                        onDoneClick = {
                            if (isEditMode) {
                                val trimmedMerchant = editedMerchant.trim()
                                if (trimmedMerchant.isEmpty()) {
                                    merchantError = "Merchant cannot be empty"
                                    return@TransactionBottomActions
                                }
                                onSaveEdit(
                                    expense.id,
                                    trimmedMerchant,
                                    editedCategory.trim().ifEmpty { null },
                                    editedNote.trim().ifEmpty { null }
                                )
                                isEditMode = false
                            } else {
                                onDismiss()
                            }
                        },
                        onCancelEdit = {
                            editedMerchant = expense.merchant
                            editedCategory = expense.tag ?: expense.category ?: ""
                            editedCategoryId = expense.categoryId
                            editedNote = expense.note ?: ""
                            merchantError = null
                            isEditMode = false
                        }
                    )
                }
            }
        }
    }

    // --- MODAL DIALOGS ---

    // Account Picker Dialog
    if (showAccountPickerDialog) {
        DarkAccountPickerDialog(
            isReassign = isReassignPicker,
            candidateAccounts = remember(knownAccounts, expense) {
                val suffix = expense.accountSuffix
                if (!suffix.isNullOrBlank()) {
                    val matching = knownAccounts.filter { it.accountSuffix == suffix }
                    val others = knownAccounts.filter { it.accountSuffix != suffix }
                    matching + others
                } else {
                    knownAccounts
                }
            },
            currentSuffix = expense.accountSuffix,
            onSelectAccount = { acc ->
                showAccountPickerDialog = false
                if (isReassignPicker) {
                    onReassignAccount?.invoke(expense.id, acc.id)
                } else {
                    onAssignAccount?.invoke(expense.id, acc.id)
                }
            },
            onDismiss = { showAccountPickerDialog = false }
        )
    }

    // Unlink Confirmation Dialog
    if (showUnlinkConfirmDialog) {
        DarkUnlinkConfirmDialog(
            onConfirm = {
                showUnlinkConfirmDialog = false
                onUnlinkAccount?.invoke(expense.id)
            },
            onDismiss = { showUnlinkConfirmDialog = false }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        DarkDeleteConfirmDialog(
            expense = expense,
            sign = sign,
            onConfirm = {
                showDeleteConfirmDialog = false
                onDelete?.invoke(expense)
                onDismiss()
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }

    // Category Picker Dialog
    if (showCategoryPickerDialog) {
        DarkCategoryPickerDialog(
            allCategories = allCategories,
            currentCategoryId = editedCategoryId,
            currentCategoryName = editedCategory,
            onCategorySelected = { cat ->
                editedCategoryId = cat.id
                editedCategory = cat.name
                showCategoryPickerDialog = false
                if (!isEditMode) {
                    onSaveEdit(expense.id, expense.merchant, cat.name, expense.note)
                }
            },
            onDismiss = { showCategoryPickerDialog = false }
        )
    }

    // Add Tag Dialog
    if (showAddTagDialog) {
        DarkAddTagDialog(
            onAddTag = { newTag ->
                val cleanedTag = newTag.trim().removePrefix("#").replace(" ", "")
                if (cleanedTag.isNotEmpty() && !currentTags.contains(cleanedTag)) {
                    val updated = currentTags + cleanedTag
                    currentTags = updated
                    val appendStr = if (editedNote.isBlank()) "#$cleanedTag" else "$editedNote #$cleanedTag"
                    editedNote = appendStr
                    if (!isEditMode) {
                        onSaveEdit(expense.id, expense.merchant, editedCategory.ifBlank { null }, appendStr)
                    }
                }
                showAddTagDialog = false
            },
            onDismiss = { showAddTagDialog = false }
        )
    }
}

// ==========================================
// 1. TOP APP BAR / HEADER
// ==========================================

@Composable
private fun TransactionDetailsHeader(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back Button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Center Title & Status
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "Transaction Details",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFF10B981), CircleShape)
                )
                Text(
                    text = "LOCAL VAULT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = Color(0xFF9CA3AF)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right Security Squircle Control
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF12231A))
                .border(1.dp, Color(0xFF1A3828), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = "Security Vault",
                tint = Color(0xFF10B981),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right Circular Brand Control (White Circle with ArcTracker Brand Mark)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "ArcTracker Brand",
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

// ==========================================
// 2. SUB-HEADER / IDENTIFICATION ROW
// ==========================================

@Composable
private fun TransactionIdentityRow(
    txnCode: String,
    isEditMode: Boolean,
    onToggleEdit: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Txn ID Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF141721))
                .border(1.dp, Color(0xFF22283A), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Color(0xFF10B981), CircleShape)
            )
            Text(
                text = txnCode,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE5E7EB)
            )
        }

        // Right Action Buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Edit Button Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isEditMode) Color(0xFF1B2A20) else Color(0xFF1C222E))
                    .border(
                        1.dp,
                        if (isEditMode) Color(0xFF2A5E3E) else Color(0xFF2A3447),
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onToggleEdit() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = if (isEditMode) Icons.Default.Close else Icons.Default.Edit,
                    contentDescription = if (isEditMode) "Cancel Edit" else "Edit",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (isEditMode) "Cancel" else "Edit",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            // Red Trash Button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2B1417))
                    .border(1.dp, Color(0xFF4C1D24), RoundedCornerShape(10.dp))
                    .clickable { onDeleteClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ==========================================
// 3. TRANSACTION HERO CARD
// ==========================================

@Composable
private fun TransactionHeroCard(
    merchant: String,
    onMerchantChange: (String) -> Unit,
    merchantError: String?,
    secondaryDescription: String,
    isEditMode: Boolean,
    isCredit: Boolean,
    sign: String,
    amount: Double,
    formattedDateTime: String,
    categoryIconKey: String,
    isReconciled: Boolean,
    isPending: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141721)),
        border = BorderStroke(1.dp, Color(0xFF22283A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row: Category Icon + Merchant & Subtitle + Debit/Credit Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Squircle
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1F2535))
                        .border(1.dp, Color(0xFF2D354B), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CategoryVisuals.getIcon(categoryIconKey),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Merchant & Subtitle
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    if (isEditMode) {
                        OutlinedTextField(
                            value = merchant,
                            onValueChange = onMerchantChange,
                            singleLine = true,
                            isError = merchantError != null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF10B981),
                                unfocusedBorderColor = Color(0xFF2A3447),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            placeholder = { Text("Merchant name", color = Color(0xFF6B7280)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (merchantError != null) {
                            Text(
                                text = merchantError,
                                color = Color(0xFFF87171),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            text = merchant,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (secondaryDescription.isNotBlank() && secondaryDescription != "-" && !secondaryDescription.equals(merchant, ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = secondaryDescription,
                                fontSize = 12.sp,
                                color = Color(0xFF8C93A4),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Direction Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isCredit) Color(0xFF0C251C) else Color(0xFF291217))
                        .border(
                            1.dp,
                            if (isCredit) Color(0xFF154231) else Color(0xFF481B24),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isCredit) "CREDIT / INCOME" else "DEBIT / EXPENSE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCredit) Color(0xFF34D399) else Color(0xFFEF4444)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Amount Section
            Text(
                text = "SETTLED TRANSACTION AMOUNT",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color(0xFF6B7280)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "$sign₹${String.format(Locale.US, "%,.2f", amount)}",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCredit) Color(0xFF34D399) else Color(0xFFF87171)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "INR",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8C93A4),
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom row: Clock + Date/Time + Reconciled Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF8C93A4),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = formattedDateTime,
                        fontSize = 12.sp,
                        color = Color(0xFF8C93A4)
                    )
                }

                // Status pill
                if (isPending) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF2B1D08))
                            .border(1.dp, Color(0xFF4D3410), RoundedCornerShape(16.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Pending Review",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B)
                        )
                    }
                } else if (isReconciled) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0C251C))
                            .border(1.dp, Color(0xFF154231), RoundedCornerShape(16.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Reconciled",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1F2433))
                            .border(1.dp, Color(0xFF2B3347), RoundedCornerShape(16.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Unresolved",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. ACCOUNT SECTION & CARD
// ==========================================

@Composable
private fun TransactionAccountSection(
    linkedAccount: KnownFinancialAccount?,
    accountSuffix: String?,
    onReassign: () -> Unit,
    onUnlink: () -> Unit,
    onAssign: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "ACCOUNT",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = Color(0xFFE5E7EB)
                )
            }

            if (linkedAccount != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0C251C))
                        .border(1.dp, Color(0xFF154231), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "Matched automatically",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF222838))
                        .border(1.dp, Color(0xFF2E374D), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Not linked",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF9CA3AF)
                    )
                }
            }
        }

        // Nested Account Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141721)),
            border = BorderStroke(1.dp, Color(0xFF22283A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                val rawSuffix = linkedAccount?.accountSuffix?.ifBlank { null } ?: accountSuffix?.ifBlank { null }
                val cleanDigits = rawSuffix?.filter { it.isDigit() }?.takeLast(4)?.ifEmpty { rawSuffix?.takeLast(4) }
                val maskedSuffix = if (!cleanDigits.isNullOrBlank()) "•• $cleanDigits" else "-"

                if (linkedAccount != null) {
                    // Bank details row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bank logo squircle badge
                        val bankCode = (linkedAccount.institutionId ?: linkedAccount.institutionName)
                            ?.take(4)?.uppercase() ?: "BANK"
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E2536))
                                .border(1.dp, Color(0xFF2A344B), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = bankCode,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = linkedAccount.institutionName ?: linkedAccount.institutionId ?: "-",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val accountTypeLabel = when (linkedAccount.instrumentType) {
                                InstrumentType.CARD -> "Card"
                                InstrumentType.BANK_ACCOUNT -> "Savings Account"
                                else -> "Account"
                            }
                            val subtext = if (maskedSuffix != "-") "$accountTypeLabel $maskedSuffix" else accountTypeLabel
                            Text(
                                text = subtext,
                                fontSize = 13.sp,
                                color = Color(0xFF8C93A4)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Reassign Account
                        Row(
                            modifier = Modifier
                                .weight(1.35f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E2330))
                                .border(1.dp, Color(0xFF2A3447), RoundedCornerShape(10.dp))
                                .clickable { onReassign() },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Reassign Account",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }

                        // Unlink
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1A1D27))
                                .border(1.dp, Color(0xFF2A3447), RoundedCornerShape(10.dp))
                                .clickable { onUnlink() },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LinkOff,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Unlink",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF9CA3AF),
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    // Unlinked State
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Account not linked",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val unlinkedSubtext = if (maskedSuffix != "-") "Account $maskedSuffix" else "-"
                            Text(
                                text = unlinkedSubtext,
                                fontSize = 13.sp,
                                color = Color(0xFF8C93A4)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF10B981))
                                .clickable { onAssign() }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Assign Account",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. CATEGORY SECTION & CARD
// ==========================================

@Composable
private fun TransactionCategorySection(
    categoryName: String,
    categoryIconKey: String,
    isAutoCategorized: Boolean,
    onChangeCategoryClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Category,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "CATEGORY",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = Color(0xFFE5E7EB)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF191D28))
                    .border(1.dp, Color(0xFF252C3D), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isAutoCategorized) "Auto-categorized" else "User-assigned",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF8C93A4)
                )
            }
        }

        // Nested Category Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141721)),
            border = BorderStroke(1.dp, Color(0xFF22283A))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category icon circle
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF222838))
                        .border(1.dp, Color(0xFF2D354B), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CategoryVisuals.getIcon(categoryIconKey),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ACTIVE CATEGORY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = Color(0xFF6B7280)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = categoryName.ifBlank { "-" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Change > Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1C222E))
                        .border(1.dp, Color(0xFF2A3447), RoundedCornerShape(10.dp))
                        .clickable { onChangeCategoryClick() }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Change",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// 6. TRANSACTION METADATA CARD
// ==========================================

@Composable
private fun TransactionMetadataCard(
    upiRef: String,
    balanceAfter: String,
    source: String,
    onCopyUpiRef: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141721)),
        border = BorderStroke(1.dp, Color(0xFF22283A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Row 1: UPI Reference
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UPI Reference",
                    fontSize = 13.sp,
                    color = Color(0xFF8C93A4)
                )
                if (upiRef != "-") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onCopyUpiRef() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = upiRef,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy UPI reference",
                            tint = Color(0xFF8C93A4),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else {
                    Text(
                        text = "-",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8C93A4)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1B202D), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            // Row 2: Balance After
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Balance After",
                    fontSize = 13.sp,
                    color = Color(0xFF8C93A4)
                )
                Text(
                    text = balanceAfter,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (balanceAfter != "-") Color.White else Color(0xFF8C93A4)
                )
            }

            HorizontalDivider(color = Color(0xFF1B202D), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            // Row 3: Source
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Source",
                    fontSize = 13.sp,
                    color = Color(0xFF8C93A4)
                )
                Text(
                    text = source,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}

// ==========================================
// 7. SOURCE EVIDENCE SECTION & CARD
// ==========================================

@Composable
private fun TransactionSourceEvidenceSection(
    sourceTitle: String,
    rawText: String?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Section Header
        Text(
            text = "SOURCE EVIDENCE",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = Color(0xFFE5E7EB),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Nested Evidence Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141721)),
            border = BorderStroke(1.dp, Color(0xFF22283A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Collapsed header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExpand() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF12241B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MailOutline,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sourceTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Extracted from your bank message",
                            fontSize = 11.sp,
                            color = Color(0xFF8C93A4)
                        )
                    }

                    // View original message >
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "View original\nmessage",
                            fontSize = 11.sp,
                            color = Color(0xFF8C93A4),
                            textAlign = TextAlign.End,
                            lineHeight = 14.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFF8C93A4),
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(if (isExpanded) 90f else 0f)
                        )
                    }
                }

                // Expanded Raw Message Container
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        val messageToDisplay = rawText?.takeIf { it.isNotBlank() } ?: "-"

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0B0E14))
                                .border(1.dp, Color(0xFF1A1F2B), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = messageToDisplay,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF9CA3AF),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 8. NOTES & TAGS SECTION & CARD
// ==========================================

@Composable
private fun TransactionNotesSection(
    note: String,
    onNoteChange: (String) -> Unit,
    isEditMode: Boolean,
    tags: List<String>,
    onAddTagClick: () -> Unit,
    onEditNoteClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Article,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "NOTES & TAGS",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = Color(0xFFE5E7EB)
                )
            }

            if (!isEditMode) {
                Text(
                    text = "Edit Note",
                    fontSize = 12.sp,
                    color = Color(0xFF9CA3AF),
                    modifier = Modifier
                        .clickable { onEditNoteClick() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // Note Body
        if (isEditMode) {
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp),
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("Add notes here...", color = Color(0xFF6B7280)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF10B981),
                    unfocusedBorderColor = Color(0xFF22283A),
                    focusedContainerColor = Color(0xFF161924),
                    unfocusedContainerColor = Color(0xFF161924),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF161924))
                    .border(1.dp, Color(0xFF22283A), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = note.ifBlank { "-" },
                    fontSize = 13.sp,
                    color = if (note.isNotBlank()) Color(0xFFE5E7EB) else Color(0xFF8C93A4),
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tags Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tags.forEach { tag ->
                val isTaxOrDeductible = tag.contains("tax", ignoreCase = true) || tag.contains("deductible", ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isTaxOrDeductible) Color(0xFF0F261E) else Color(0xFF191E2A))
                        .border(
                            1.dp,
                            if (isTaxOrDeductible) Color(0xFF1A4535) else Color(0xFF252E40),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "# $tag",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isTaxOrDeductible) Color(0xFF34D399) else Color(0xFF9CA3AF)
                    )
                }
            }

            // + Add Tag
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C222E))
                    .border(1.dp, Color(0xFF2A3447), RoundedCornerShape(16.dp))
                    .clickable { onAddTagClick() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Tag",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "Add Tag",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}

// ==========================================
// 9. FLOATING BOTTOM ACTION BAR
// ==========================================

@Composable
private fun TransactionBottomActions(
    isEditMode: Boolean,
    onExportClick: () -> Unit,
    onDoneClick: () -> Unit,
    onCancelEdit: () -> Unit
) {
    val pillShape = RoundedCornerShape(32.dp)
    val pillBackground = Color(0xFF13161F)
    val glassBorder = BorderStroke(
        width = 1.dp,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0x66FFFFFF), // Top specular reflection
                Color(0x20FFFFFF), // Subtle mid edge
                Color(0x0DFFFFFF)  // Base edge
            )
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .shadow(
                elevation = 20.dp,
                shape = pillShape,
                spotColor = Color(0x99000000),
                ambientColor = Color(0x66000000)
            )
            .clip(pillShape)
            .background(pillBackground)
            .border(glassBorder, pillShape)
    ) {
        // Subtle top glass specular highlight
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.7f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0x00FFFFFF),
                            Color(0x4DFFFFFF),
                            Color(0x00FFFFFF)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isEditMode) {
                // Cancel Button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1E2330))
                        .border(1.dp, Color(0xFF2E384D), RoundedCornerShape(24.dp))
                        .clickable { onCancelEdit() },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Save Changes Button (White Pill)
                Row(
                    modifier = Modifier
                        .weight(1.3f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .clickable { onDoneClick() }
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Save Changes",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                // Export Receipt / JSON (Left)
                Row(
                    modifier = Modifier
                        .weight(1.35f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1E2330))
                        .border(1.dp, Color(0xFF2E384D), RoundedCornerShape(24.dp))
                        .clickable { onExportClick() }
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Export Receipt / JSON",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Done Button (Right - White Pill with Black Text)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .clickable { onDoneClick() }
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Done",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

// ==========================================
// MODAL DIALOG HELPERS
// ==========================================

@Composable
private fun DarkAccountPickerDialog(
    isReassign: Boolean,
    candidateAccounts: List<KnownFinancialAccount>,
    currentSuffix: String?,
    onSelectAccount: (KnownFinancialAccount) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141721),
        title = {
            Text(
                text = if (isReassign) "Reassign Account" else "Assign Account",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            if (candidateAccounts.isEmpty()) {
                Text(
                    text = "No financial accounts registered yet.",
                    color = Color(0xFF8C93A4),
                    fontSize = 13.sp
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    candidateAccounts.forEach { acc ->
                        val isMatchingSuffix = acc.accountSuffix == currentSuffix
                        Card(
                            onClick = { onSelectAccount(acc) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMatchingSuffix) Color(0xFF0F261E) else Color(0xFF1B202E)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isMatchingSuffix) Color(0xFF1B4A37) else Color(0xFF283145)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = acc.institutionName ?: "Bank",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${AccountReconciliationManager.formatAccountType(acc.instrumentType, acc.accountSuffix)} •• ${acc.accountSuffix}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF8C93A4)
                                    )
                                }
                                if (isMatchingSuffix) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF14382A))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Matching Suffix",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF34D399)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8C93A4))
            }
        }
    )
}

@Composable
private fun DarkUnlinkConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141721),
        title = {
            Text("Unlink Account?", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "This will remove the account link for this transaction. The transaction will appear under Unresolved Transactions.",
                color = Color(0xFF8C93A4),
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Text("Unlink", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8C93A4))
            }
        }
    )
}

@Composable
private fun DarkDeleteConfirmDialog(
    expense: Expense,
    sign: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141721),
        title = {
            Text("Delete this transaction?", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "$sign₹${String.format(Locale.US, "%,.2f", expense.amount)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFFF87171)
                )
                Text(
                    text = expense.merchant.ifEmpty { "Unknown Merchant" },
                    fontSize = 14.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This removes the transaction permanently from your ArcTracker ledger.",
                    fontSize = 12.sp,
                    color = Color(0xFF8C93A4)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8C93A4))
            }
        }
    )
}

@Composable
private fun DarkCategoryPickerDialog(
    allCategories: List<TransactionCategory>,
    currentCategoryId: String?,
    currentCategoryName: String,
    onCategorySelected: (TransactionCategory) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141721),
        title = {
            Text("Select Category", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            val categories = remember(allCategories) {
                if (allCategories.isNotEmpty()) {
                    allCategories.filter { !it.isArchived }
                } else {
                    BuiltInCategories.ALL
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = cat.id == currentCategoryId || cat.name.equals(currentCategoryName, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF1E2638) else Color(0xFF1B202E))
                            .border(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF262E40), RoundedCornerShape(8.dp))
                            .clickable { onCategorySelected(cat) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(CategoryVisuals.getContainerColor(cat.colorKey).copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CategoryVisuals.getIcon(cat.iconKey),
                                contentDescription = null,
                                tint = CategoryVisuals.getColor(cat.colorKey),
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = cat.name,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8C93A4))
            }
        }
    )
}

@Composable
private fun DarkAddTagDialog(
    onAddTag: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tagInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141721),
        title = {
            Text("Add Tag", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = { Text("e.g. Personal, Food, Travel", color = Color(0xFF6B7280)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF283145),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAddTag(tagInput) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                enabled = tagInput.isNotBlank()
            ) {
                Text("Add", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8C93A4))
            }
        }
    )
}

// ==========================================
// UTILITY FUNCTIONS
// ==========================================

private fun extractTags(note: String?, legacyTag: String?): List<String> {
    val results = mutableListOf<String>()
    if (!note.isNullOrBlank()) {
        val matches = Regex("""#([A-Za-z0-9_-]+)""").findAll(note)
        for (m in matches) {
            val t = m.groupValues[1]
            if (t.isNotBlank() && !results.contains(t)) {
                results.add(t)
            }
        }
    }
    if (!legacyTag.isNullOrBlank()) {
        val clean = legacyTag.removePrefix("#").trim()
        if (clean.isNotBlank() && !results.contains(clean)) {
            results.add(clean)
        }
    }
    return results
}

private fun extractUpiRef(rawText: String?, note: String?, notificationKey: String?): String {
    // 1. Search in note
    if (!note.isNullOrBlank()) {
        val notePattern = Regex("""(?:Ref|UTR|UPI)[:\s]+(\d{8,16})""", RegexOption.IGNORE_CASE).find(note)
        if (notePattern != null) {
            return notePattern.groupValues[1]
        }
    }
    // 2. Search in rawText
    if (!rawText.isNullOrBlank()) {
        val upiPattern = Regex("""(?:UPI(?:\s*Ref(?:erence)?(?:\s*No)?|\/|\s*txn\s*id|\s*id)?[:\s]+|Ref(?:\s*No)?[:\s]+|UTR[:\s]+)(\d{8,16})""", RegexOption.IGNORE_CASE)
        val match = upiPattern.find(rawText)
        if (match != null) {
            return match.groupValues[1]
        }
        val digitRun = Regex("""\b(\d{12})\b""").find(rawText)
        if (digitRun != null) {
            return digitRun.groupValues[1]
        }
    }
    // 3. Search in notificationKey
    if (!notificationKey.isNullOrBlank()) {
        val digits = notificationKey.filter { it.isDigit() }
        if (digits.length >= 10) {
            return digits.takeLast(12)
        }
    }
    // Return "-" if not available
    return "-"
}

private fun extractBalance(rawText: String?): String? {
    if (rawText.isNullOrBlank()) return null
    val balPattern = Regex("""(?:Bal(?:ance)?|Avl\s*Bal|Avail\s*Bal)(?:\s*(?:is|INR|Rs\.?))?[:\s]*([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE)
    val match = balPattern.find(rawText)
    return match?.groupValues?.get(1)?.let { "₹$it" }
}

private fun exportTransactionReceipt(
    context: Context,
    expense: Expense,
    account: KnownFinancialAccount?,
    txnCode: String,
    upiRef: String
) {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(expense.dateMillis))
    val merchantVal = expense.merchant.ifBlank { "-" }
    val accountVal = when {
        account != null -> "${account.institutionName ?: account.institutionId ?: "Account"} (${account.accountSuffix})"
        !expense.accountSuffix.isNullOrBlank() -> "Account (${expense.accountSuffix})"
        else -> "-"
    }
    val categoryVal = expense.category ?: expense.tag ?: "-"
    val noteVal = expense.note?.ifBlank { "-" } ?: "-"
    val statusVal = if (expense.isPending) "Pending Review" else if (expense.accountId != null) "Reconciled" else "Unresolved"
    val json = """
{
  "transactionId": "$txnCode",
  "merchant": "$merchantVal",
  "amount": ${expense.amount},
  "currency": "INR",
  "type": "${expense.type}",
  "timestamp": "$dateStr",
  "account": "$accountVal",
  "category": "$categoryVal",
  "upiRef": "$upiRef",
  "note": "$noteVal",
  "status": "$statusVal"
}
    """.trimIndent()

    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, json)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export Transaction JSON")
        context.startActivity(shareIntent)
    } catch (_: Exception) {
        Toast.makeText(context, "Exporting receipt...", Toast.LENGTH_SHORT).show()
    }
}
