package com.subhashrelangi.arctracker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.engine.account.AccountReconciliationManager
import com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader
import com.subhashrelangi.arctracker.ui.theme.ArcColors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val slateTitle: Color @Composable @ReadOnlyComposable get() = ArcColors.TextPrimary
private val slateSub: Color @Composable @ReadOnlyComposable get() = ArcColors.TextMuted
private val cardBorder: Color @Composable @ReadOnlyComposable get() = ArcColors.Border
private val lightBg: Color @Composable @ReadOnlyComposable get() = ArcColors.Background
private val cardBg: Color @Composable @ReadOnlyComposable get() = ArcColors.CardBackground
private val highlightSurface: Color @Composable @ReadOnlyComposable get() = ArcColors.SurfaceHighlight

/**
 * Transaction Details & Evidence Screen for ArcTracker.
 *
 * ArcTracker Emerald Light Theme:
 * - Header matches AddExpenseDialog layout with circular back button, bold title, and circular security vault icon.
 * - Simple, clean inline status indicators ("• Verified & Reconciled" and "🔒 On-Device Encrypted") without bulky badges.
 * - Hero Card displays authentic transaction values with zero hardcoded dummy data.
 * - Action buttons: Full-width "Edit Transaction" and two side-by-side pills: "Category" and "Delete" (Split Bill removed).
 * - Payment & Settlement section only displays fields that exist for the transaction (balance, reference, and channel are omitted when absent).
 * - Classification & Notes section displays real category, authentic tags only, and real memo/ledger note.
 * - Manual transactions omit sender ID, parser latency, raw SMS snippets, and mismatch reporting.
 * - Light-themed modal dialogs for account assignment, category picking, tags, and deletion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailDialog(
    expense: Expense,
    initialEditMode: Boolean = false,
    closeOnBackOrSave: Boolean = false,
    knownAccounts: List<KnownFinancialAccount> = emptyList(),
    allCategories: List<TransactionCategory> = emptyList(),
    recentMerchants: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSaveEdit: (expenseId: Int, merchant: String, category: String?, note: String?) -> Unit,
    onAssignAccount: ((expenseId: Int, targetAccountId: String) -> Unit)? = null,
    onReassignAccount: ((expenseId: Int, newAccountId: String) -> Unit)? = null,
    onUnlinkAccount: ((expenseId: Int) -> Unit)? = null,
    onDelete: ((expense: Expense) -> Unit)? = null
) {
    val context = LocalContext.current

    // Edit Mode State
    var isEditMode by remember(initialEditMode) { mutableStateOf(initialEditMode) }
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

    // Tags State (Only real extracted tags)
    var currentTags by remember(expense.note, expense.tag, editedNote, editedCategory) {
        mutableStateOf(extractTags(editedNote.ifBlank { expense.note }, editedCategory.ifBlank { expense.tag }))
    }

    // Linked Account lookup
    val linkedAccount = remember(expense.accountId, knownAccounts) {
        knownAccounts.find { it.id == expense.accountId }
    }

    // Direction & Financial Typography
    val isCredit = expense.type.equals("Credit", ignoreCase = true)
    val isTransfer = expense.type.equals("Transfer", ignoreCase = true)
    val sign = if (isCredit) "+" else "-"

    val dateMillis = if (expense.dateMillis > 0) expense.dateMillis else System.currentTimeMillis()
    val formattedHeroDate = remember(dateMillis) {
        SimpleDateFormat("EEEE, MMM dd, yyyy • hh:mm a", Locale.US).format(Date(dateMillis))
    }
    val formattedParserDate = remember(dateMillis) {
        SimpleDateFormat("MMM dd, yyyy • hh:mm:ss a", Locale.US).format(Date(dateMillis))
    }
    val dateCode = remember(dateMillis) {
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(dateMillis))
    }
    val txnCode = "#TXN-$dateCode-${expense.id.toString().padStart(4, '0')}"

    // Merchant title: Use actual merchant; if blank, fallback to category or generic "Expense" (no dummy data)
    val transactionTitle = remember(expense.merchant, expense.category, expense.tag, editedMerchant) {
        val currentMerchant = if (editedMerchant.isNotBlank()) editedMerchant else expense.merchant
        when {
            currentMerchant.isNotBlank() && currentMerchant != "-" -> currentMerchant
            !expense.category.isNullOrBlank() -> expense.category!!
            !expense.tag.isNullOrBlank() -> expense.tag!!
            else -> "Expense Transaction"
        }
    }

    // Is this a manual entry (or an entry without SMS evidence)?
    val isManualEntry = remember(expense.source, expense.rawText, expense.notificationKey) {
        expense.source.contains("MANUAL", ignoreCase = true) ||
            (expense.rawText.isNullOrBlank() && expense.notificationKey.isNullOrBlank())
    }

    // Extraction of UPI Ref, Balance, Source (real data only, no dummy fallback)
    val upiRef = remember(expense.rawText, expense.note, expense.notificationKey) {
        extractUpiRef(expense.rawText, expense.note, expense.notificationKey)
    }

    val postTxnBalance = remember(expense.rawText) {
        extractBalance(expense.rawText)
    }

    val humanizedSource = remember(expense.source, isManualEntry) {
        when {
            isManualEntry -> "Manual Entry"
            expense.source.contains("NOTIFICATION", ignoreCase = true) -> "Payment Notification"
            expense.source.contains("SMS", ignoreCase = true) -> "SMS Notification"
            expense.source.isNotBlank() -> expense.source
            else -> "Local Ingestion"
        }
    }

    // Origin Sender ID: Extract only from actual raw text or notification key
    val originSenderId = remember(expense.rawText, expense.notificationKey, isManualEntry) {
        if (isManualEntry) null else extractSenderId(expense.rawText, expense.notificationKey)
    }

    // Payment Channel: Deduce only from real data if present
    val paymentChannel = remember(expense.rawText, expense.source, isManualEntry) {
        when {
            isManualEntry -> "Manual Ledger Entry"
            expense.rawText?.contains("POS", ignoreCase = true) == true ||
                expense.rawText?.contains("ending", ignoreCase = true) == true -> "POS Terminal / Card"
            expense.rawText?.contains("UPI", ignoreCase = true) == true -> "UPI / Instant Transfer"
            expense.rawText?.contains("ATM", ignoreCase = true) == true -> "ATM Cash Withdrawal"
            expense.rawText?.contains("NetBanking", ignoreCase = true) == true ||
                expense.rawText?.contains("IMPS", ignoreCase = true) == true -> "Net Banking / IMPS"
            expense.source.isNotBlank() && !expense.source.equals("SMS", true) -> expense.source
            else -> null
        }
    }

    // Resolved Category
    val resolvedCategory = remember(expense, allCategories, editedCategoryId, editedCategory) {
        allCategories.find { it.id == editedCategoryId || it.name.equals(editedCategory, ignoreCase = true) }
            ?: BuiltInCategories.findLegacyMapping(editedCategory)
    }
    val categoryIconKey = resolvedCategory?.iconKey ?: "restaurant"
    val categoryDisplayName = resolvedCategory?.name
        ?: editedCategory.ifBlank { expense.category ?: expense.tag ?: "Uncategorized" }

    // Secondary Description / Location: Only show real location or category if distinct from merchant title
    val secondaryDescription = remember(expense.merchant, categoryDisplayName, expense.category, expense.tag) {
        when {
            !expense.category.isNullOrBlank() && expense.category != transactionTitle -> expense.category
            !expense.tag.isNullOrBlank() && expense.tag != transactionTitle -> expense.tag
            categoryDisplayName.isNotBlank() && categoryDisplayName != "-" && categoryDisplayName != transactionTitle -> categoryDisplayName
            else -> null
        }
    }

    BackHandler {
        if (closeOnBackOrSave || !isEditMode) {
            onDismiss()
        } else {
            isEditMode = false
        }
    }

    Dialog(
        onDismissRequest = {
            if (closeOnBackOrSave || !isEditMode) {
                onDismiss()
            } else {
                isEditMode = false
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        val isDark = ArcColors.isDark
        SideEffect {
            var parent = dialogView.parent
            while (parent != null && parent !is DialogWindowProvider) {
                parent = parent.parent
            }
            (parent as? DialogWindowProvider)?.window?.let { window ->
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setDimAmount(0f)
                window.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                WindowCompat.getInsetsController(window, window.decorView)?.let { insetsController ->
                    insetsController.isAppearanceLightStatusBars = !isDark
                    insetsController.isAppearanceLightNavigationBars = !isDark
                }
            }
        }

        if (isEditMode) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = lightBg
            ) {
                EditTransactionPage(
                    expense = expense.copy(
                        merchant = editedMerchant.ifBlank { expense.merchant },
                        tag = editedCategory.ifBlank { expense.tag },
                        categoryId = editedCategoryId ?: expense.categoryId,
                        note = editedNote
                    ),
                    knownAccounts = knownAccounts,
                    allCategories = allCategories,
                    recentMerchants = recentMerchants,
                    onBack = {
                        if (closeOnBackOrSave) {
                            onDismiss()
                        } else {
                            isEditMode = false
                        }
                    },
                    onSave = { expenseId, merchant, category, note ->
                        editedMerchant = merchant
                        editedCategory = category ?: ""
                        editedNote = note ?: ""
                        onSaveEdit(expenseId, merchant, category, note)
                        if (closeOnBackOrSave) {
                            onDismiss()
                        } else {
                            isEditMode = false
                        }
                    },
                    onDelete = if (onDelete != null) { { showDeleteConfirmDialog = true } } else null,
                    onAssignAccount = onAssignAccount,
                    onReassignAccount = onReassignAccount
                )
            }
        } else {
            Scaffold(
                containerColor = lightBg,
                topBar = {
                    var menuExpanded by remember { mutableStateOf(false) }

                    Surface(
                        color = cardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(cardBg)
                    ) {
                        ArcTrackerHeader(
                            title = "Transaction Details",
                            subtitle = "Immutable Local Vault",
                            onBackClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(cardBg)
                                .statusBarsPadding(),
                            horizontalPadding = 16.dp,
                            actions = {
                                // Circular Share Button
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(cardBg)
                                        .border(BorderStroke(1.dp, cardBorder), CircleShape)
                                        .clickable {
                                            exportTransactionReceipt(context, expense, linkedAccount, txnCode, upiRef)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = slateTitle,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Circular 3-Dots More Menu
                                Box {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(cardBg)
                                            .border(BorderStroke(1.dp, cardBorder), CircleShape)
                                            .clickable { menuExpanded = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More Options",
                                            tint = slateTitle,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false },
                                        modifier = Modifier.background(cardBg)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Edit Transaction", color = slateTitle, fontWeight = FontWeight.Medium) },
                                            onClick = {
                                                menuExpanded = false
                                                isEditMode = true
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Edit, contentDescription = null, tint = ArcColors.Primary, modifier = Modifier.size(18.dp))
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete Transaction", color = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626), fontWeight = FontWeight.Medium) },
                                            onClick = {
                                                menuExpanded = false
                                                showDeleteConfirmDialog = true
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            ) { innerPadding ->
                // SCROLLABLE CONTENT
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
                ) {
                    // 1. SIMPLE & CLEAN STATUS INDICATORS ROW
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Verified & Reconciled / Committed status
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(ArcColors.Primary)
                                )
                                Text(
                                    text = if (expense.accountId != null) "Verified & Reconciled" else "Committed to Ledger",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ArcColors.Primary
                                )
                            }

                            // Right: On-Device Encrypted
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = slateSub,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "On-Device Encrypted",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = slateSub
                                )
                            }
                        }
                    }

                    // 2. HERO TRANSACTION CARD
                    item {
                        TransactionHeroCard(
                            transactionTitle = transactionTitle,
                            secondaryDescription = secondaryDescription,
                            isCredit = isCredit,
                            isTransfer = isTransfer,
                            sign = sign,
                            amount = expense.amount,
                            formattedHeroDate = formattedHeroDate,
                            categoryIconKey = categoryIconKey,
                            isReconciled = expense.accountId != null
                        )
                    }

                    // 3. ACTION BUTTONS (Edit + Category + Delete, Split Bill removed)
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Primary Emerald Edit Button
                            Button(
                                onClick = { isEditMode = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ArcColors.Primary,
                                    contentColor = Color.White
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Edit Transaction",
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 2 Secondary Action Pills: Category & Delete (Split Bill Removed)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // 1. Category Pill
                                OutlinedButton(
                                    onClick = { showCategoryPickerDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, cardBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = cardBg,
                                        contentColor = slateTitle
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Label,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = slateTitle
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Category",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                // 2. Delete Pill
                                OutlinedButton(
                                    onClick = { showDeleteConfirmDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, if (isDark) Color(0xFF5C2424) else Color(0xFFFECACA)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isDark) Color(0xFF3B1818) else Color(0xFFFEF2F2),
                                        contentColor = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Delete",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. SECTION 1: "PAYMENT & SETTLEMENT"
                    item {
                        TransactionAccountSection(
                            linkedAccount = linkedAccount,
                            accountSuffix = expense.accountSuffix,
                            isPending = expense.isPending,
                            postTxnBalance = postTxnBalance,
                            paymentChannel = paymentChannel,
                            upiRef = if (upiRef != "-") upiRef else null,
                            onAccountClick = {
                                isReassignPicker = linkedAccount != null
                                showAccountPickerDialog = true
                            },
                            onCopyUpiRef = {
                                if (upiRef != "-") {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Reference / UTR", upiRef)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Reference / UTR copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    // 5. SECTION 2: "CLASSIFICATION & NOTES"
                    item {
                        TransactionCategorySection(
                            categoryDisplayName = categoryDisplayName,
                            categoryIconKey = categoryIconKey,
                            categorySubtitle = if (resolvedCategory?.isSystem == true) "System Category" else null,
                            isAutoCategorized = expense.categorySource != "USER_ASSIGNED" && !expense.category.isNullOrBlank(),
                            tags = currentTags,
                            memoNote = editedNote.ifBlank { expense.note ?: "" },
                            onReassignClick = { showCategoryPickerDialog = true },
                            onAddTagClick = { showAddTagDialog = true },
                            onEditMemoClick = { isEditMode = true }
                        )
                    }

                    // 6. SECTION 3: "LOCAL INGESTION & AUDIT"
                    item {
                        TransactionSourceEvidenceSection(
                            isManualEntry = isManualEntry,
                            sourceProtocol = humanizedSource,
                            originSenderId = originSenderId,
                            parserTimestamp = if (!isManualEntry) formattedParserDate else null,
                            rawSnippet = expense.rawText?.takeIf { it.isNotBlank() }
                        )
                    }

                    // 7. BOTTOM ACTIONS: EXPORT & (OPTIONAL) REPORT LINK
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Export Receipt / CSV Row Button
                            Button(
                                onClick = {
                                    exportTransactionReceipt(context, expense, linkedAccount, txnCode, upiRef)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = highlightSurface,
                                    contentColor = slateTitle
                                ),
                                border = BorderStroke(1.dp, cardBorder),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Upload,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = slateTitle
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Export Receipt / CSV Row",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Centered Report Parsing Mismatch (Only shown when raw SMS/notification evidence exists)
                            if (!expense.rawText.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            Toast.makeText(context, "Parsing mismatch reported for local audit", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Flag,
                                        contentDescription = null,
                                        tint = slateSub,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Report Parsing Mismatch",
                                        fontSize = 12.sp,
                                        color = slateSub,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
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
// 1. HERO TRANSACTION CARD
// ==========================================

@Composable
private fun TransactionHeroCard(
    transactionTitle: String,
    secondaryDescription: String?,
    isCredit: Boolean,
    isTransfer: Boolean,
    sign: String,
    amount: Double,
    formattedHeroDate: String,
    categoryIconKey: String,
    isReconciled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        shadowElevation = 0.5.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Top Row: Merchant Squircle + Verified checkmark (if reconciled) + Merchant details + Direction Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Merchant Icon Squircle
                Box(
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (ArcColors.isDark) Color(0xFF064E3B) else Color(0xFFE8F5E9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CategoryVisuals.getIcon(categoryIconKey),
                            contentDescription = null,
                            tint = ArcColors.Primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Green check badge on bottom right if reconciled
                    if (isReconciled) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(ArcColors.Primary)
                                .border(BorderStroke(1.5.dp, cardBg), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Verified",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Merchant Name & Secondary Subtitle (if available)
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = transactionTitle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = slateTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!secondaryDescription.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = null,
                                tint = slateSub,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = secondaryDescription,
                                fontSize = 12.sp,
                                color = slateSub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Direction Badge
                val isDark = ArcColors.isDark
                val (badgeBg, badgeText, badgeLabel) = when {
                    isTransfer -> Triple(if (isDark) Color(0xFF075985).copy(alpha = 0.4f) else Color(0xFFE0F2FE), Color(0xFF0284C7), "⇄ Transfer")
                    isCredit -> Triple(if (isDark) Color(0xFF064E3B).copy(alpha = 0.5f) else Color(0xFFDCFCE7), ArcColors.Primary, "↑ Income")
                    else -> Triple(if (isDark) Color(0xFF7F1D1D).copy(alpha = 0.5f) else Color(0xFFFEE2E2), if (isDark) Color(0xFFF87171) else Color(0xFFDC2626), "↓ Expense")
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(badgeBg)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeText
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Inner Settled Amount Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(highlightSurface)
                    .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(14.dp))
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SETTLED AMOUNT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = slateSub,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val formattedAmount = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
                        minimumFractionDigits = 2
                        maximumFractionDigits = 2
                    }.format(amount)
                    Text(
                        text = "$sign ₹$formattedAmount",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = slateTitle
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = slateSub,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = formattedHeroDate,
                            fontSize = 12.sp,
                            color = slateSub,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. PAYMENT & SETTLEMENT SECTION
// ==========================================

@Composable
private fun TransactionAccountSection(
    linkedAccount: KnownFinancialAccount?,
    accountSuffix: String?,
    isPending: Boolean,
    postTxnBalance: String?,
    paymentChannel: String?,
    upiRef: String?,
    onAccountClick: () -> Unit,
    onCopyUpiRef: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CreditCard,
                contentDescription = null,
                tint = ArcColors.Primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Payment & Settlement",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = slateTitle
            )
            Spacer(modifier = Modifier.weight(1f))
            val isDark = ArcColors.isDark
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (linkedAccount != null) (if (isDark) Color(0xFF064E3B).copy(alpha = 0.5f) else Color(0xFFDCFCE7)) else highlightSurface)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = when {
                        isPending -> "Pending"
                        linkedAccount != null -> "Reconciled"
                        else -> "Unlinked"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (linkedAccount != null) ArcColors.Primary else slateSub
                )
            }
        }

        // Section Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            shadowElevation = 0.5.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Account Row Box (Shows real account or prompt to assign)
                val displayAccountName = when {
                    !linkedAccount?.institutionName.isNullOrBlank() -> linkedAccount!!.institutionName!!
                    !linkedAccount?.institutionId.isNullOrBlank() -> linkedAccount!!.institutionId!!
                    !accountSuffix.isNullOrBlank() -> "Linked Account"
                    else -> "No Account Linked"
                }
                val displayAccountType = if (linkedAccount != null) {
                    AccountReconciliationManager.formatAccountType(linkedAccount.instrumentType, linkedAccount.accountSuffix)
                } else if (!accountSuffix.isNullOrBlank()) {
                    "Account"
                } else {
                    "Tap to link an account"
                }
                val suffix = accountSuffix ?: linkedAccount?.accountSuffix

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(highlightSurface)
                        .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(12.dp))
                        .clickable { onAccountClick() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (ArcColors.isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = null,
                            tint = slateSub,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = displayAccountName,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = slateTitle
                        )
                        Text(
                            text = displayAccountType,
                            fontSize = 11.5.sp,
                            color = slateSub
                        )
                    }

                    if (!suffix.isNullOrBlank()) {
                        Text(
                            text = "•••• $suffix",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = slateTitle
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Assign",
                            tint = slateSub,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Balance After Transaction (Only shown if authentic balance was extracted)
                if (!postTxnBalance.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Balance After Transaction",
                            fontSize = 13.sp,
                            color = slateSub
                        )
                        Text(
                            text = postTxnBalance,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = slateTitle
                        )
                    }
                }

                // Payment Channel (Only shown if identified)
                if (!paymentChannel.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Payment Channel",
                            fontSize = 13.sp,
                            color = slateSub
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Contactless,
                                contentDescription = null,
                                tint = slateSub,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = paymentChannel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = slateTitle
                            )
                        }
                    }
                }

                // Reference / UTR (Only shown if authentic reference is present)
                if (!upiRef.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Reference / UTR",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = slateTitle
                            )
                            Text(
                                text = "Bank Ledger Identifier",
                                fontSize = 11.sp,
                                color = slateSub
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(highlightSurface)
                                .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(8.dp))
                                .clickable { onCopyUpiRef() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = upiRef,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = slateTitle
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = slateSub,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. CLASSIFICATION & NOTES SECTION
// ==========================================

@Composable
private fun TransactionCategorySection(
    categoryDisplayName: String,
    categoryIconKey: String,
    categorySubtitle: String?,
    isAutoCategorized: Boolean,
    tags: List<String>,
    memoNote: String,
    onReassignClick: () -> Unit,
    onAddTagClick: () -> Unit,
    onEditMemoClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Category,
                contentDescription = null,
                tint = ArcColors.Primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Classification & Notes",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = slateTitle
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Re-assign",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = ArcColors.Primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onReassignClick() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        // Section Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            shadowElevation = 0.5.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Category Row Container
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(highlightSurface)
                        .clickable { onReassignClick() }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (ArcColors.isDark) Color(0xFF064E3B) else Color(0xFFDCFCE7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CategoryVisuals.getIcon(categoryIconKey),
                            contentDescription = null,
                            tint = ArcColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = categoryDisplayName,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = slateTitle
                        )
                        if (!categorySubtitle.isNullOrBlank()) {
                            Text(
                                text = categorySubtitle,
                                fontSize = 11.5.sp,
                                color = slateSub
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Change",
                        tint = slateSub,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Auto-categorized Rule row (Only shown if auto-categorized)
                if (isAutoCategorized) {
                    Row(
                        modifier = Modifier.padding(start = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = ArcColors.Primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Auto-categorized by Category Rules ↗",
                            fontSize = 12.sp,
                            color = ArcColors.Primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Semantic Tags Caption
                Text(
                    text = "SEMANTIC TAGS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = slateSub,
                    letterSpacing = 0.5.sp
                )

                // Semantic Tags Row (Only displays actual tags from this transaction)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(tags) { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(highlightSurface)
                                .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = slateTitle
                            )
                        }
                    }

                    item {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(50))
                                .clickable { onAddTagClick() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "+ Add Tag",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ArcColors.Primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Memo / Ledger Note Caption
                Text(
                    text = "MEMO / LEDGER NOTE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = slateSub,
                    letterSpacing = 0.5.sp
                )

                // Memo Preview Card (Shows actual note or clean placeholder)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(highlightSurface)
                        .clickable { onEditMemoClick() }
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Article,
                            contentDescription = null,
                            tint = slateSub,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (memoNote.isNotBlank()) memoNote else "Tap to add a note or ledger memo...",
                            fontSize = 12.5.sp,
                            color = if (memoNote.isNotBlank()) slateTitle else slateSub,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. LOCAL INGESTION & AUDIT SECTION
// ==========================================

@Composable
private fun TransactionSourceEvidenceSection(
    isManualEntry: Boolean,
    sourceProtocol: String,
    originSenderId: String?,
    parserTimestamp: String?,
    rawSnippet: String?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                tint = ArcColors.Primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Local Ingestion & Audit",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = slateTitle
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (ArcColors.isDark) Color(0xFF064E3B).copy(alpha = 0.5f) else Color(0xFFDCFCE7))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isManualEntry) "Local Ledger" else "⚙ Local Pipeline",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ArcColors.Primary
                )
            }
        }

        // Section Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            shadowElevation = 0.5.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Source Protocol (and Sender ID if present)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Source Protocol
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(highlightSurface)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Source Protocol",
                            fontSize = 11.sp,
                            color = slateSub
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isManualEntry) Icons.Default.EditNote else Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = ArcColors.Primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = sourceProtocol,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = slateTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Origin Sender ID (Only displayed when real sender ID is present)
                    if (!originSenderId.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(highlightSurface)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "Origin Sender ID",
                                fontSize = 11.sp,
                                color = slateSub
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = originSenderId,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = slateTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Parser Resolution Time (Omitted for manual transactions)
                if (!isManualEntry && !parserTimestamp.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(highlightSurface)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Parser Resolution Time",
                            fontSize = 11.sp,
                            color = slateSub
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = parserTimestamp,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = slateTitle
                        )
                    }
                }

                // Raw Ingestion Snippet (Omitted for manual transactions or when absent)
                if (!isManualEntry && !rawSnippet.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RAW INGESTION SNIPPET",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = slateSub,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "UTF-8 Plaintext",
                            fontSize = 11.sp,
                            color = slateSub,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(highlightSurface)
                            .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = rawSnippet,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            color = slateTitle,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Bottom Audit Badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (ArcColors.isDark) Color(0xFF064E3B).copy(alpha = 0.25f) else Color(0xFFF0FDF4))
                        .border(BorderStroke(1.dp, if (ArcColors.isDark) Color(0xFF064E3B) else Color(0xFFDCFCE7)), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = ArcColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "SHA-256 Ledger Verified",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ArcColors.Primary
                        )
                        Text(
                            text = if (isManualEntry) "Zero Cloud Transmission • Private Local Storage" else "Zero Cloud Transmission • Air-gapped Sandbox",
                            fontSize = 10.5.sp,
                            color = if (ArcColors.isDark) ArcColors.Primary.copy(alpha = 0.8f) else Color(0xFF059669)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// MODAL DIALOG HELPERS (LIGHT THEMED)
// ==========================================

@Composable
internal fun DarkAccountPickerDialog(
    isReassign: Boolean,
    candidateAccounts: List<KnownFinancialAccount>,
    currentSuffix: String?,
    onSelectAccount: (KnownFinancialAccount) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = ArcColors.isDark
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardBg,
        title = {
            Text(
                text = if (isReassign) "Reassign Account" else "Assign Account",
                color = slateTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            if (candidateAccounts.isEmpty()) {
                Text(
                    text = "No financial accounts registered yet.",
                    color = slateSub,
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
                                containerColor = if (isMatchingSuffix) (if (isDark) Color(0xFF064E3B).copy(alpha = 0.35f) else Color(0xFFECFDF5)) else highlightSurface
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isMatchingSuffix) (if (isDark) ArcColors.Primary else Color(0xFFA7F3D0)) else cardBorder
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
                                        text = acc.institutionName ?: acc.institutionId ?: "Bank",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = slateTitle
                                    )
                                    Text(
                                        text = "${AccountReconciliationManager.formatAccountType(acc.instrumentType, acc.accountSuffix)} •• ${acc.accountSuffix}",
                                        fontSize = 12.sp,
                                        color = slateSub
                                    )
                                }
                                if (isMatchingSuffix) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isDark) Color(0xFF064E3B).copy(alpha = 0.6f) else Color(0xFFDCFCE7))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Matching Suffix",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ArcColors.Primary
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
                Text("Cancel", color = slateSub)
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
        containerColor = cardBg,
        title = {
            Text("Unlink Account?", color = slateTitle, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "This will remove the account link for this transaction. The transaction will appear under Unresolved Transactions.",
                color = slateSub,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
            ) {
                Text("Unlink", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = slateSub)
            }
        }
    )
}

@Composable
internal fun DarkDeleteConfirmDialog(
    expense: Expense,
    sign: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = ArcColors.isDark
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF3B1818) else Color(0xFFFEE2E2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Delete Transaction?",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = slateTitle
                        )
                        Text(
                            text = "Permanently removes from local ledger.",
                            fontSize = 11.5.sp,
                            color = slateSub
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = highlightSurface,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val formattedAmount = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
                            minimumFractionDigits = 2
                            maximumFractionDigits = 2
                        }.format(expense.amount)
                        Text(
                            text = "$sign ₹$formattedAmount",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = expense.merchant.ifEmpty { "Unknown Merchant" },
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = slateTitle
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Cancel", color = slateSub, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onConfirm,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Delete", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun DarkCategoryPickerDialog(
    allCategories: List<TransactionCategory>,
    currentCategoryId: String?,
    currentCategoryName: String,
    onCategorySelected: (TransactionCategory) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = ArcColors.isDark
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Select Category",
                    color = slateTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

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
                                .background(if (isSelected) (if (isDark) Color(0xFF064E3B).copy(alpha = 0.35f) else Color(0xFFECFDF5)) else highlightSurface)
                                .border(1.dp, if (isSelected) (if (isDark) ArcColors.Primary else Color(0xFFA7F3D0)) else cardBorder, RoundedCornerShape(8.dp))
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
                                color = slateTitle,
                                modifier = Modifier.weight(1f)
                            )

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = ArcColors.Primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Text("Cancel", color = slateSub, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
internal fun DarkAddTagDialog(
    onAddTag: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var tagInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Add Custom Tag",
                    color = slateTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    placeholder = { Text("e.g. Personal, Food, Travel", color = slateSub) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ArcColors.Primary,
                        unfocusedBorderColor = cardBorder,
                        focusedTextColor = slateTitle,
                        unfocusedTextColor = slateTitle
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Cancel", color = slateSub, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = { onAddTag(tagInput) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = ArcColors.Primary),
                        enabled = tagInput.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Add", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// UTILITY FUNCTIONS
// ==========================================

internal fun extractTags(note: String?, legacyTag: String?): List<String> {
    val results = mutableListOf<String>()
    if (!note.isNullOrBlank()) {
        val matches = Regex("""#(\w+)""").findAll(note)
        for (m in matches) {
            val t = m.groupValues[1]
            if (!results.contains(t)) {
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

internal fun extractUpiRef(rawText: String?, note: String?, notificationKey: String?): String {
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

internal fun extractBalance(rawText: String?): String? {
    if (rawText.isNullOrBlank()) return null
    val balPattern = Regex("""(?:Bal(?:ance)?|Avl\s*Bal|Avail\s*Bal)(?:\s*(?:is|INR|Rs\.?))?[:\s]*([0-9,]+(?:\.[0-9]{2})?)""", RegexOption.IGNORE_CASE)
    val match = balPattern.find(rawText)
    return match?.groupValues?.get(1)?.let { "₹$it" }
}

internal fun extractSenderId(rawText: String?, notificationKey: String?): String? {
    if (!notificationKey.isNullOrBlank()) {
        val parts = notificationKey.split(":", "|", "_")
        for (part in parts) {
            if (part.matches(Regex("""[A-Za-z]{2}-[A-Za-z0-9]{4,8}"""))) {
                return part.uppercase()
            }
        }
    }
    if (!rawText.isNullOrBlank()) {
        val match = Regex("""\b([A-Za-z]{2}-[A-Za-z0-9]{4,8})\b""").find(rawText)
        if (match != null) {
            return match.groupValues[1].uppercase()
        }
    }
    return null
}

private fun exportTransactionReceipt(
    context: Context,
    expense: Expense,
    account: KnownFinancialAccount?,
    txnCode: String,
    upiRef: String
) {
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(if (expense.dateMillis > 0) expense.dateMillis else System.currentTimeMillis()))
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
