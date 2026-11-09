package com.subhashrelangi.arctracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.BuiltInCategories
import com.subhashrelangi.arctracker.data.CategoryVisuals
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.TransactionCategory
import com.subhashrelangi.arctracker.engine.account.AccountReconciliationManager
import com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader
import com.subhashrelangi.arctracker.ui.theme.ArcColors
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val slateTitle: Color @Composable @ReadOnlyComposable get() = ArcColors.TextPrimary
private val slateSub: Color @Composable @ReadOnlyComposable get() = ArcColors.TextMuted
private val cardBorder: Color @Composable @ReadOnlyComposable get() = ArcColors.Border
private val lightBg: Color @Composable @ReadOnlyComposable get() = ArcColors.Background
private val cardBg: Color @Composable @ReadOnlyComposable get() = ArcColors.CardBackground
private val highlightSurface: Color @Composable @ReadOnlyComposable get() = ArcColors.SurfaceHighlight

/**
 * ArcTracker Edit Transaction Screen ("Log Session").
 *
 * ArcTracker Emerald Light Theme:
 * - Header matches the exact layout of settings subsection pages: circular back button, title directly beside
 *   the back arrow ("Log Session"), and circular security vault icon on the right.
 * - Zero dummy data: dynamically populated with the real transaction details.
 * - Static bottom action section: pinned "Save Changes" and "Delete Transaction" buttons with comfortable
 *   bottom padding (28.dp + navigationBarsPadding) that stay visible while content scrolls.
 * - Real Account & Suffix lookup, real merchant, real note and tags.
 * - Only shows SMS Detection banner and Original SMS Source preview if raw SMS evidence actually exists.
 */
@Composable
fun EditTransactionPage(
    expense: Expense,
    knownAccounts: List<KnownFinancialAccount>,
    allCategories: List<TransactionCategory> = emptyList(),
    recentMerchants: List<String> = emptyList(),
    onBack: () -> Unit,
    onSave: (expenseId: Int, merchant: String, category: String?, note: String?) -> Unit,
    onDelete: (() -> Unit)? = null,
    onAssignAccount: ((expenseId: Int, accountId: String) -> Unit)? = null,
    onReassignAccount: ((expenseId: Int, accountId: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // 1. Transaction Direction (Credit / Debit / Transfer)
    var isCredit by remember(expense.type) {
        mutableStateOf(expense.type.equals("Credit", ignoreCase = true))
    }
    var isTransfer by remember(expense.type) {
        mutableStateOf(expense.type.equals("Transfer", ignoreCase = true))
    }

    // 2. Amount State
    var currentAmount by remember(expense.amount) {
        mutableDoubleStateOf(expense.amount)
    }
    var amountText by remember(expense.amount) {
        mutableStateOf(
            if (expense.amount > 0.0) {
                if (expense.amount % 1.0 == 0.0) {
                    String.format(Locale.US, "%.0f", expense.amount)
                } else {
                    String.format(Locale.US, "%.2f", expense.amount)
                }
            } else ""
        )
    }

    // 3. Merchant State (Starts with real merchant, no fake default)
    var editedMerchant by remember(expense.merchant) {
        mutableStateOf(expense.merchant)
    }
    var merchantError by remember { mutableStateOf<String?>(null) }

    // 4. Categories Setup
    val defaultDebitCategories = remember {
        listOf(
            TransactionCategory("groceries", "Groceries", "shopping_basket", "green", isSystem = true, sortOrder = 0),
            TransactionCategory("food_dining", "Food & Dining", "restaurant", "orange", isSystem = true, sortOrder = 1),
            TransactionCategory("shopping", "Shopping", "shopping_bag", "purple", isSystem = true, sortOrder = 2),
            TransactionCategory("transport", "Transport", "directions_car", "blue", isSystem = true, sortOrder = 3),
            TransactionCategory("bills_utilities", "Bills & Utilities", "receipt", "teal", isSystem = true, sortOrder = 4),
            TransactionCategory("household", "Household", "home", "green", isSystem = true, sortOrder = 5),
            TransactionCategory("entertainment", "Entertainment", "movie", "pink", isSystem = true, sortOrder = 6),
            TransactionCategory("healthcare", "Healthcare", "medical_services", "red", isSystem = true, sortOrder = 7),
            TransactionCategory("other", "Other", "category", "default", isSystem = true, sortOrder = 8)
        )
    }

    val defaultCreditCategories = remember {
        listOf(
            TransactionCategory("salary", "Salary", "payments", "green", isSystem = true, sortOrder = 0),
            TransactionCategory("refund", "Refund", "receipt", "teal", isSystem = true, sortOrder = 1),
            TransactionCategory("cashback", "Cashback", "savings", "orange", isSystem = true, sortOrder = 2),
            TransactionCategory("investment", "Investment", "savings", "purple", isSystem = true, sortOrder = 3),
            TransactionCategory("gift", "Gift & Allowance", "card_giftcard", "pink", isSystem = true, sortOrder = 4),
            TransactionCategory("income", "Income", "payments", "green", isSystem = true, sortOrder = 5),
            TransactionCategory("other_credit", "Other", "category", "default", isSystem = true, sortOrder = 6)
        )
    }

    val activeCategoryOptions = remember(isCredit, allCategories) {
        if (isCredit) {
            val customCredit = allCategories.filter { !it.isSystem && (it.name.contains("Income", true) || it.name.contains("Salary", true) || it.name.contains("Refund", true)) }
            defaultCreditCategories + customCredit
        } else {
            val customDebit = allCategories.filter { !it.isSystem }
            defaultDebitCategories + customDebit
        }
    }

    var editedCategory by remember(expense.tag, expense.category, isCredit) {
        val initialName = expense.tag ?: expense.category ?: (if (isCredit) "Income" else "Other")
        mutableStateOf(initialName)
    }
    var editedCategoryId by remember(expense.categoryId) {
        mutableStateOf(expense.categoryId)
    }

    // 5. Chronology State
    var currentDateMillis by remember(expense.dateMillis) {
        mutableLongStateOf(if (expense.dateMillis > 0) expense.dateMillis else System.currentTimeMillis())
    }

    // 6. Notes and Tags State (Strictly real data only)
    var noteText by remember(expense.note) {
        mutableStateOf(expense.note ?: "")
    }
    var currentTags by remember(expense.note, expense.tag) {
        mutableStateOf(extractTags(expense.note, expense.tag))
    }

    // 7. Source Evidence Expansion
    var isEvidenceExpanded by remember { mutableStateOf(false) }

    // Dialog flags
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAmountEditDialog by remember { mutableStateOf(false) }

    // Linked Account lookup
    val linkedAccount = remember(expense.accountId, knownAccounts) {
        knownAccounts.find { it.id == expense.accountId?.toString() || it.id == expense.accountId }
    }

    // Formatted Date
    val formattedDateTime = remember(currentDateMillis) {
        SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.US).format(Date(currentDateMillis))
    }

    // Resolved Category Visuals
    val resolvedCategory = remember(activeCategoryOptions, editedCategoryId, editedCategory) {
        activeCategoryOptions.find { it.id == editedCategoryId || it.name.equals(editedCategory, ignoreCase = true) }
            ?: BuiltInCategories.findLegacyMapping(editedCategory)
            ?: activeCategoryOptions.firstOrNull()
    }
    val categoryDisplayName = resolvedCategory?.name ?: editedCategory.ifBlank { if (isCredit) "Income" else "Other" }
    val categoryIconKey = resolvedCategory?.iconKey ?: (if (isCredit) "payments" else "category")
    val categorySubtitle = remember(categoryDisplayName, isCredit) {
        getCategorySubtitle(categoryDisplayName, isCredit)
    }

    // Account Display (strictly authentic)
    val displayAccountName = when {
        !linkedAccount?.institutionName.isNullOrBlank() -> linkedAccount!!.institutionName!!
        !linkedAccount?.institutionId.isNullOrBlank() -> linkedAccount!!.institutionId!!
        !expense.accountSuffix.isNullOrBlank() -> "Linked Account"
        else -> "No Account Linked"
    }
    val displayAccountType = if (linkedAccount != null) {
        AccountReconciliationManager.formatAccountType(linkedAccount.instrumentType, linkedAccount.accountSuffix)
    } else if (!expense.accountSuffix.isNullOrBlank()) {
        "Account"
    } else {
        "Tap to assign an account"
    }
    val suffix = expense.accountSuffix ?: linkedAccount?.accountSuffix

    // Sign
    val sign = if (isCredit) "+" else "-"

    // Keyboard detection for smooth layout adjustment
    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // Scaffold Container
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = lightBg,
        topBar = {
            // Header matching settings subsection pages: circular back button + title directly beside it + right circular vault icon
            Surface(
                color = cardBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cardBg)
            ) {
                ArcTrackerHeader(
                    title = "Log Session",
                    subtitle = "Local Ledger Record",
                    onBackClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardBg)
                        .statusBarsPadding(),
                    horizontalPadding = 16.dp,
                    actions = {
                        // Circular @ Mention button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(cardBg)
                                .border(BorderStroke(1.dp, cardBorder), CircleShape)
                            .clickable {
                                Toast.makeText(context, "Mention payee or category", Toast.LENGTH_SHORT).show()
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AlternateEmail,
                                contentDescription = "Mention",
                                tint = slateTitle,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Circular A Font Formatting button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(cardBg)
                                .border(BorderStroke(1.dp, cardBorder), CircleShape)
                            .clickable {
                                Toast.makeText(context, "Typography & formatting", Toast.LENGTH_SHORT).show()
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TextFields,
                                contentDescription = "Font formatting",
                                tint = slateTitle,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            // Static Bottom Action Section docking flush at bottom of screen
            if (!isKeyboardVisible) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = cardBg,
                    shadowElevation = 10.dp,
                    border = BorderStroke(1.dp, cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Primary Save Changes Button
                        Button(
                            onClick = {
                                val trimmed = editedMerchant.trim()
                                if (trimmed.isEmpty()) {
                                    merchantError = "Merchant cannot be empty"
                                    return@Button
                                }
                                val finalNote = if (currentTags.isNotEmpty()) {
                                    val tagString = currentTags.joinToString(" ") { "#$it" }
                                    if (noteText.contains("#")) noteText.trim() else "$noteText $tagString".trim()
                                } else {
                                    noteText.trim()
                                }
                                onSave(expense.id, trimmed, categoryDisplayName.ifBlank { null }, finalNote.ifEmpty { null })
                            },
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
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Save Changes",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Secondary Delete Transaction Button
                        if (onDelete != null) {
                            Surface(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = CircleShape,
                                color = if (ArcColors.isDark) Color(0xFF3B1818) else Color(0xFFFEF2F2),
                                border = BorderStroke(1.dp, if (ArcColors.isDark) Color(0xFF5C2424) else Color(0xFFFECACA))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = if (ArcColors.isDark) Color(0xFFF87171) else Color(0xFFDC2626),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Delete Transaction",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (ArcColors.isDark) Color(0xFFF87171) else Color(0xFFDC2626)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp)
        ) {
            // 1. TOP SOURCE INGESTION BANNER (Only displayed if authentic SMS/push notification exists)
            if (!expense.rawText.isNullOrBlank() || !expense.notificationKey.isNullOrBlank()) {
                item {
                    val bannerAccountTitle = when {
                        !linkedAccount?.institutionName.isNullOrBlank() -> linkedAccount!!.institutionName!!
                        !expense.accountSuffix.isNullOrBlank() -> "Account ••${expense.accountSuffix}"
                        else -> "SMS Transaction"
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        shadowElevation = 0.5.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Chat/SMS bubble squircle
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (ArcColors.isDark) Color(0xFF064E3B) else Color(0xFFE8F5E9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = ArcColors.Primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = bannerAccountTitle,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = slateTitle
                                    )
                                    if (linkedAccount != null) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Verified",
                                            tint = ArcColors.Primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (expense.source.contains("NOTIFICATION", ignoreCase = true)) "Detected via App Notification" else "Detected via SMS Alert",
                                    fontSize = 11.5.sp,
                                    color = slateSub
                                )
                            }

                            // Right: Auto-Parsed Pill Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (ArcColors.isDark) Color(0xFF064E3B).copy(alpha = 0.6f) else Color(0xFFDCFCE7))
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Auto-Parsed",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ArcColors.Primary
                                )
                            }
                        }
                    }
                }
            }

            // 2. TRANSACTION TYPE SEGMENTED SWITCHER
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = highlightSurface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Expense Tab
                        val isExpenseSelected = !isCredit && !isTransfer
                        Surface(
                            onClick = {
                                isCredit = false
                                isTransfer = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isExpenseSelected) cardBg else Color.Transparent,
                            shadowElevation = if (isExpenseSelected) 1.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "↓ Expense",
                                    fontSize = 13.sp,
                                    fontWeight = if (isExpenseSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isExpenseSelected) slateTitle else slateSub
                                )
                            }
                        }

                        // Income Tab
                        val isIncomeSelected = isCredit && !isTransfer
                        Surface(
                            onClick = {
                                isCredit = true
                                isTransfer = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isIncomeSelected) cardBg else Color.Transparent,
                            shadowElevation = if (isIncomeSelected) 1.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "↑ Income",
                                    fontSize = 13.sp,
                                    fontWeight = if (isIncomeSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isIncomeSelected) ArcColors.Primary else slateSub
                                )
                            }
                        }

                        // Transfer Tab
                        Surface(
                            onClick = {
                                isTransfer = true
                                isCredit = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isTransfer) cardBg else Color.Transparent,
                            shadowElevation = if (isTransfer) 1.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⇄ Transfer",
                                    fontSize = 13.sp,
                                    fontWeight = if (isTransfer) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isTransfer) Color(0xFF0284C7) else slateSub
                                )
                            }
                        }
                    }
                }
            }

            // 3. CARD 1: TRANSACTION AMOUNT (Exactly like AddExpenseDialog)
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "TRANSACTION AMOUNT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = slateSub,
                            letterSpacing = 0.8.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Start-aligned Amount Input with tight symbol spacing
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(
                                text = "₹",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (amountText.isNotEmpty()) slateTitle else slateSub,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            BasicTextField(
                                value = amountText,
                                onValueChange = { input ->
                                    // Allow digits and up to one decimal point
                                    val clean = input.filter { it.isDigit() || it == '.' }
                                    if (clean.count { it == '.' } <= 1) {
                                        amountText = clean
                                        currentAmount = clean.toDoubleOrNull() ?: 0.0
                                    }
                                },
                                textStyle = TextStyle(
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = slateTitle,
                                    textAlign = TextAlign.Start
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() }
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(ArcColors.Primary),
                                decorationBox = { innerTextField ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (amountText.isEmpty()) {
                                            Text(
                                                text = "0.00",
                                                fontSize = 34.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = slateSub
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick Increment Chips (+₹100, +₹500, +₹1,000, +₹2,000)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(100.0 to "+₹100", 500.0 to "+₹500", 1000.0 to "+₹1,000", 2000.0 to "+₹2,000").forEach { (inc, label) ->
                                Surface(
                                    onClick = {
                                        val current = amountText.toDoubleOrNull() ?: 0.0
                                        val newVal = current + inc
                                        amountText = if (newVal % 1.0 == 0.0) {
                                            String.format(Locale.US, "%.0f", newVal)
                                        } else {
                                            String.format(Locale.US, "%.2f", newVal)
                                        }
                                        currentAmount = newVal
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = highlightSurface
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = slateTitle,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. MERCHANT / PAYEE
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "MERCHANT / PAYEE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = slateSub,
                        letterSpacing = 0.5.sp
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = cardBg,
                        border = BorderStroke(
                            1.dp,
                            if (merchantError != null) Color(0xFFEF4444) else cardBorder
                        ),
                        shadowElevation = 0.5.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mint squircle storefront icon
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (ArcColors.isDark) Color(0xFF064E3B) else Color(0xFFE8F5E9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = ArcColors.Primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Merchant Text Field
                            Box(modifier = Modifier.weight(1f)) {
                                if (editedMerchant.isEmpty()) {
                                    Text(
                                        text = "Enter payee or merchant",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = slateSub
                                    )
                                }
                                BasicTextField(
                                    value = editedMerchant,
                                    onValueChange = {
                                        editedMerchant = it
                                        if (it.isNotBlank()) merchantError = null
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = slateTitle
                                    ),
                                    cursorBrush = SolidColor(ArcColors.Primary),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Clear button
                            if (editedMerchant.isNotEmpty()) {
                                IconButton(
                                    onClick = { editedMerchant = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "Clear",
                                        tint = slateSub,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. CATEGORY
            item {
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
                            text = "CATEGORY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = slateSub,
                            letterSpacing = 0.5.sp
                        )

                        Text(
                            text = "All Categories >",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ArcColors.Primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { showCategoryPicker = true }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        shadowElevation = 0.5.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Active Category Selection Card
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(highlightSurface)
                                    .clickable { showCategoryPicker = true }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Deep Emerald Category Squircle
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ArcColors.Primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = CategoryVisuals.getIcon(categoryIconKey),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = categoryDisplayName,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = slateTitle
                                    )
                                    Text(
                                        text = categorySubtitle,
                                        fontSize = 11.5.sp,
                                        color = slateSub
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.UnfoldMore,
                                    contentDescription = "Select",
                                    tint = slateSub,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Quick Category Suggestions Row
                            val quickChips = if (isCredit) {
                                listOf(
                                    Triple("Salary", "payments", "Salary"),
                                    Triple("Refund", "receipt", "Refund"),
                                    Triple("Cashback", "savings", "Cashback")
                                )
                            } else {
                                listOf(
                                    Triple("Food & Dining", "restaurant", "Food & Dining"),
                                    Triple("Household", "home", "Household"),
                                    Triple("Utilities", "bolt", "Bills & Utilities")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickChips.forEach { (label, iconKey, targetCategory) ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(highlightSurface)
                                            .clickable {
                                                editedCategory = targetCategory
                                                editedCategoryId = null
                                            }
                                            .padding(vertical = 8.dp, horizontal = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = CategoryVisuals.getIcon(iconKey),
                                                contentDescription = null,
                                                tint = slateTitle,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = label,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = slateTitle,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. PAYMENT ACCOUNT
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "PAYMENT ACCOUNT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = slateSub,
                        letterSpacing = 0.5.sp
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        shadowElevation = 0.5.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAccountPicker = true }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Card Squircle
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(highlightSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreditCard,
                                    contentDescription = null,
                                    tint = slateSub,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayAccountName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = slateTitle
                                )
                                Text(
                                    text = if (!suffix.isNullOrBlank()) "Ending ••$suffix" else displayAccountType,
                                    fontSize = 11.5.sp,
                                    color = slateSub
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Change Account",
                                tint = slateSub,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 7. DATE & TIME
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    shadowElevation = 0.5.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cal = Calendar.getInstance().apply {
                                    timeInMillis = currentDateMillis
                                }
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        val timeCal = Calendar.getInstance().apply {
                                            timeInMillis = currentDateMillis
                                        }
                                        TimePickerDialog(
                                            context,
                                            { _, hour, minute ->
                                                cal.set(Calendar.YEAR, year)
                                                cal.set(Calendar.MONTH, month)
                                                cal.set(Calendar.DAY_OF_MONTH, day)
                                                cal.set(Calendar.HOUR_OF_DAY, hour)
                                                cal.set(Calendar.MINUTE, minute)
                                                currentDateMillis = cal.timeInMillis
                                            },
                                            timeCal.get(Calendar.HOUR_OF_DAY),
                                            timeCal.get(Calendar.MINUTE),
                                            false
                                        ).show()
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Calendar Squircle
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(highlightSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = slateSub,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DATE & TIME",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = slateSub,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = formattedDateTime,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = slateTitle
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.EditCalendar,
                            contentDescription = "Edit Date",
                            tint = slateSub,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 8. ORIGINAL SMS SOURCE (COLLAPSIBLE - Strictly only shown when authentic SMS raw text exists)
            if (!expense.rawText.isNullOrBlank()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        shadowElevation = 0.5.dp
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isEvidenceExpanded = !isEvidenceExpanded },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.MailOutline,
                                        contentDescription = null,
                                        tint = slateTitle,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Original SMS Source",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = slateTitle
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isEvidenceExpanded) "Hide preview" else "Show preview",
                                        fontSize = 12.sp,
                                        color = slateSub
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (isEvidenceExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = slateSub,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            if (isEvidenceExpanded) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(highlightSurface)
                                        .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = expense.rawText!!,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.5.sp,
                                        color = slateTitle,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 9. NOTES & TAGS (Strictly authentic data only)
            item {
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
                            text = "NOTES & TAGS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = slateSub,
                            letterSpacing = 0.5.sp
                        )

                        Text(
                            text = "Optional",
                            fontSize = 12.sp,
                            color = slateSub
                        )
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        shadowElevation = 0.5.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Note Input
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (noteText.isEmpty()) {
                                    Text(
                                        text = "Optional note or ledger memo...",
                                        fontSize = 14.sp,
                                        color = slateSub
                                    )
                                }
                                BasicTextField(
                                    value = noteText,
                                    onValueChange = { noteText = it },
                                    textStyle = TextStyle(
                                        fontSize = 14.sp,
                                        color = slateTitle
                                    ),
                                    cursorBrush = SolidColor(ArcColors.Primary),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Tags Row with remove 'x' buttons and '+ Add Tag'
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(currentTags) { tag ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(highlightSurface)
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "#$tag",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = slateTitle
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove tag",
                                                tint = slateSub,
                                                modifier = Modifier
                                                    .size(13.dp)
                                                    .clickable {
                                                        currentTags = currentTags.filter { it != tag }
                                                        noteText = noteText.replace("#$tag", "").trim()
                                                    }
                                            )
                                        }
                                    }
                                }

                                item {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(50))
                                            .clickable { showAddTagDialog = true }
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
                        }
                    }
                }
            }
        }
    }

    // --- MODAL DIALOGS ---

    // Amount Edit Dialog
    if (showAmountEditDialog) {
        DarkAmountInputDialog(
            initialAmount = currentAmount,
            onConfirm = {
                currentAmount = it
                amountText = if (it % 1.0 == 0.0) String.format(Locale.US, "%.0f", it) else String.format(Locale.US, "%.2f", it)
                showAmountEditDialog = false
            },
            onDismiss = { showAmountEditDialog = false }
        )
    }

    // Category Picker Dialog
    if (showCategoryPicker) {
        DarkCategoryPickerDialog(
            allCategories = activeCategoryOptions,
            currentCategoryId = editedCategoryId,
            currentCategoryName = categoryDisplayName,
            onCategorySelected = { cat ->
                editedCategoryId = cat.id
                editedCategory = cat.name
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }

    // Account Picker Dialog
    if (showAccountPicker) {
        DarkAccountPickerDialog(
            isReassign = linkedAccount != null,
            candidateAccounts = knownAccounts,
            currentSuffix = expense.accountSuffix,
            onSelectAccount = { acc ->
                showAccountPicker = false
                if (linkedAccount != null) {
                    onReassignAccount?.invoke(expense.id, acc.id)
                } else {
                    onAssignAccount?.invoke(expense.id, acc.id)
                }
            },
            onDismiss = { showAccountPicker = false }
        )
    }

    // Add Tag Dialog
    if (showAddTagDialog) {
        DarkAddTagDialog(
            onAddTag = { newTag ->
                val clean = newTag.trim().removePrefix("#").replace(" ", "")
                if (clean.isNotEmpty() && !currentTags.contains(clean)) {
                    currentTags = currentTags + clean
                    noteText = if (noteText.isBlank()) "#$clean" else "$noteText #$clean"
                }
                showAddTagDialog = false
            },
            onDismiss = { showAddTagDialog = false }
        )
    }

    // Delete Confirm Dialog
    if (showDeleteConfirm) {
        DarkDeleteConfirmDialog(
            expense = expense.copy(amount = currentAmount),
            sign = sign,
            onConfirm = {
                showDeleteConfirm = false
                onDelete?.invoke()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

// ==========================================
// AMOUNT INPUT DIALOG (LIGHT THEMED)
// ==========================================

@Composable
private fun DarkAmountInputDialog(
    initialAmount: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var amountInput by remember { mutableStateOf(String.format(Locale.US, "%.2f", initialAmount)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cardBg,
        title = {
            Text("Edit Amount", color = slateTitle, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("₹ ", color = ArcColors.Primary, fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ArcColors.Primary,
                        unfocusedBorderColor = cardBorder,
                        focusedTextColor = slateTitle,
                        unfocusedTextColor = slateTitle
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = amountInput.toDoubleOrNull()
                    if (parsed != null && parsed >= 0.0) {
                        onConfirm(parsed)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ArcColors.Primary)
            ) {
                Text("Set", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = slateSub)
            }
        }
    )
}

// ==========================================
// CATEGORY SUBTITLE HELPER
// ==========================================

private fun getCategorySubtitle(categoryName: String, isCredit: Boolean): String {
    return when (categoryName.lowercase().trim()) {
        "groceries" -> "Essential Food & Supplies"
        "food & dining", "food", "dining" -> "Online Delivery & Dine-Out"
        "shopping" -> "Retail, Clothes & Electronics"
        "transport" -> "Cabs, Fuel & Transit"
        "bills & utilities", "bills", "utilities" -> "Electricity, Water & Recharges"
        "household" -> "Home Maintenance & Living"
        "entertainment" -> "Movies, Games & Subscriptions"
        "healthcare", "health" -> "Doctor, Pharmacy & Wellness"
        "travel" -> "Flights, Hotels & Holidays"
        "education" -> "Tuition, Books & Courses"
        "transfer" -> if (isCredit) "Inbound Peer or Bank Transfer" else "Outbound Peer or Bank Transfer"
        "cash withdrawal", "cash" -> "ATM & Cash Disbursal"
        "salary" -> "Monthly Payroll & Wages"
        "refund" -> "Purchase Returns & Reversals"
        "cashback" -> "Rewards & Promotional Credits"
        "investment" -> "Dividends, Stocks & Mutual Funds"
        "interest" -> "Savings & Term Deposit Yield"
        "gift & allowance", "gift" -> "Personal Gifts & Pocket Money"
        "income" -> "General Inbound Revenue"
        else -> if (isCredit) "Inbound Credit Receipt" else "General Outbound Expense"
    }
}
