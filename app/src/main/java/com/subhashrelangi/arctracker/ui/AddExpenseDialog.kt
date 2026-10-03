package com.subhashrelangi.arctracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
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
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.TransactionCategory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pixel-perfect manual transaction dialog matching the reference design in image.png.
 * Features:
 * - 100% full screen bottom sheet display from start to end (smooth native scrolling, no stuttering).
 * - Clean header: Text starts directly at the left edge without icon, with compact White circular "X" close button.
 * - AMOUNT card with vertical cursor divider and calculator icon.
 * - TRANSACTION TYPE segmented selector with Expense (Debit) and Income (Credit).
 * - MERCHANT / PERSON card without top label, clean placeholder without "e.g." text.
 * - CATEGORY curved pill buttons (like tags) taking minimal space, with White border highlight on selected item and "See all >" in White.
 * - PAID FROM ACCOUNT with White border highlight for selected account ("Cash / Wallet" & bank accounts).
 * - PAYMENT CHANNEL chips with White border highlight on selected.
 * - DATE & TIME picker card with calendar icon.
 * - NOTE (OPTIONAL) card with description icon.
 * - TAGS (OPTIONAL) chips with remove button and "+ Add Tag".
 * - Bottom action buttons matching image1.png: Dark Cancel pill and Solid White Add Expense pill with Black text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    initialAmount: String = "",
    initialMerchant: String = "",
    initialType: String = "Debit",
    initialTag: String = "",
    initialNote: String = "",
    isEditMode: Boolean = false,
    initialDateMillis: Long = System.currentTimeMillis(),
    rawText: String? = null,
    knownAccounts: List<KnownFinancialAccount> = emptyList(),
    allCategories: List<TransactionCategory> = emptyList(),
    recentMerchants: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onAddFull: ((amount: Double, merchant: String, type: String, tag: String, note: String, dateMillis: Long, accountId: String?, accountSuffix: String?, categoryId: String?) -> Unit)? = null,
    onAdd: (Double, String, String, String, String, Long) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    // Direction (Type) State
    var isCredit by remember(initialType) {
        mutableStateOf(initialType.equals("Credit", ignoreCase = true))
    }
    val type = if (isCredit) "Credit" else "Debit"

    // Amount State
    var amountText by remember(initialAmount) {
        mutableStateOf(if (initialAmount.isNotBlank() && initialAmount != "0.0") initialAmount else "")
    }
    var amountError by remember { mutableStateOf(false) }

    // Merchant State
    var merchant by remember(initialMerchant) {
        mutableStateOf(initialMerchant)
    }
    var merchantError by remember { mutableStateOf(false) }

    // Note State
    var note by remember(initialNote) {
        mutableStateOf(initialNote)
    }

    // Tags State
    var tagsList by remember(initialTag) {
        val initialList = if (initialTag.isNotBlank()) {
            initialTag.split(",", "•", " ").map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }
        mutableStateOf(
            if (initialList.isNotEmpty()) initialList else listOf("Food", "Personal", "TaxDeductible")
        )
    }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var newTagInput by remember { mutableStateOf("") }

    // DateMillis State
    var dateMillis by remember(initialDateMillis) {
        mutableLongStateOf(if (initialDateMillis > 0) initialDateMillis else System.currentTimeMillis())
    }

    // Backend Categories from Room DB
    val backendCategories = remember(allCategories, isCredit) {
        val base = if (allCategories.isNotEmpty()) {
            allCategories.filter { !it.isArchived }
        } else {
            BuiltInCategories.ALL
        }
        if (isCredit) {
            val incomeCats = base.filter { 
                it.id.contains("income", ignoreCase = true) || 
                it.name.contains("Income", ignoreCase = true) || 
                it.name.contains("Salary", ignoreCase = true) ||
                it.name.contains("Investment", ignoreCase = true) ||
                it.name.contains("Refund", ignoreCase = true)
            }
            val others = base.filter { it !in incomeCats }
            incomeCats + others
        } else {
            base
        }
    }

    // Category State
    var selectedCategoryName by remember(initialTag, isCredit, backendCategories) {
        val defaultName = if (initialTag.isNotBlank() && backendCategories.any { it.name.equals(initialTag, true) }) {
            initialTag
        } else if (isCredit) {
            backendCategories.firstOrNull { it.name.contains("Income", true) || it.name.contains("Salary", true) }?.name ?: "Income"
        } else {
            backendCategories.firstOrNull { it.name.contains("Food", true) }?.name ?: "Food & Dining"
        }
        mutableStateOf(defaultName)
    }

    var selectedCategoryId by remember(selectedCategoryName, backendCategories) {
        mutableStateOf(backendCategories.find { it.name.equals(selectedCategoryName, true) }?.id)
    }

    var showCategoryPicker by remember { mutableStateOf(false) }

    // Account State
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var selectedAccountSuffix by remember { mutableStateOf<String?>(null) }

    // Payment Channel State
    var selectedChannel by remember {
        mutableStateOf("Cash")
    }

    val paymentChannels = remember {
        listOf("Cash", "UPI", "Debit Card", "Credit Card", "NetBank")
    }

    // Formatted Date String
    val formattedDateStr = remember(dateMillis) {
        val sdf = SimpleDateFormat("dd MMM yyyy • h:mm a", Locale.US)
        sdf.format(Date(dateMillis)) + " IST"
    }

    // Theme Color Tokens matching image.png exactly
    val sheetBg = Color(0xFF090D14)
    val cardBg = Color(0xFF121722)
    val cardBorder = Color(0xFF1E2536)
    val sectionLabelColor = Color(0xFF6B7280)
    val textPrimary = Color.White
    val textSecondary = Color(0xFF9CA3AF)
    val textMuted = Color(0xFF6B7280)
    val accentGreen = Color(0xFF00E676)
    val accentDebit = Color(0xFFEF4444)

    // Category Picker Dialog
    if (showCategoryPicker) {
        DarkCategoryPickerDialog(
            allCategories = backendCategories,
            currentCategoryId = selectedCategoryId,
            currentCategoryName = selectedCategoryName,
            onCategorySelected = { cat ->
                selectedCategoryId = cat.id
                selectedCategoryName = cat.name
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }

    // Custom Tag Dialog
    if (showAddTagDialog) {
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            containerColor = Color(0xFF141A26),
            title = {
                Text("Add Tag", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column {
                    Text("Enter a tag label:", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicTextField(
                        value = newTagInput,
                        onValueChange = { newTagInput = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E2536), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newTagInput.trim().removePrefix("#")
                        if (trimmed.isNotBlank() && trimmed !in tagsList) {
                            tagsList = tagsList + trimmed
                        }
                        newTagInput = ""
                        showAddTagDialog = false
                    }
                ) {
                    Text("Add", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        scrimColor = Color.Black.copy(alpha = 0.70f),
        windowInsets = WindowInsets(0, 0, 0, 0),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 10.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .background(Color(0xFF4B5565), RoundedCornerShape(2.dp))
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 8.dp
    ) {
        // 100% full screen display from start to end (no stuttering)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // 1. TOP HEADER (Text aligned to start without icon, small White circular "X" close button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = when {
                            isEditMode -> "Edit Transaction"
                            rawText != null -> "Approve Transaction"
                            else -> "Add Transaction"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            rawText != null -> "Review detected transaction"
                            else -> "Create a new transaction manually"
                        },
                        fontSize = 12.5.sp,
                        color = Color(0xFF8B949E)
                    )
                }

                // Small White Circle Close Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White, CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 2. AMOUNT SECTION
            Text(
                text = "AMOUNT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = sectionLabelColor,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(14.dp),
                color = cardBg,
                border = BorderStroke(1.dp, if (amountError) accentDebit else cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "₹",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF9CA3AF),
                        modifier = Modifier.padding(end = 12.dp)
                    )

                    // Vertical Divider matching image.png
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(Color(0xFF374151))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (amountText.isEmpty()) {
                            Text(
                                text = "0.00",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF6B7280),
                                fontFamily = FontFamily.Default
                            )
                        }
                        BasicTextField(
                            value = amountText,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() || it == '.' }
                                if (filtered.count { it == '.' } <= 1) {
                                    amountText = filtered
                                    if (amountError && (filtered.toDoubleOrNull() ?: 0.0) > 0.0) {
                                        amountError = false
                                    }
                                }
                            },
                            textStyle = TextStyle(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                fontFamily = FontFamily.Default
                            ),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (amountText.isNotEmpty()) {
                        IconButton(
                            onClick = { amountText = "" },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Calculate,
                            contentDescription = null,
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            if (amountError) {
                Text(
                    text = "Please enter an amount greater than ₹0",
                    color = accentDebit,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                )
            }

            // 3. TRANSACTION TYPE
            Text(
                text = "TRANSACTION TYPE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = sectionLabelColor,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Expense (Debit) Option
                Surface(
                    onClick = { isCredit = false },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (!isCredit) Color(0xFF191115) else cardBg,
                    border = BorderStroke(
                        if (!isCredit) 1.5.dp else 1.dp,
                        if (!isCredit) Color(0xFFEF4444) else cardBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RemoveCircleOutline,
                            contentDescription = null,
                            tint = if (!isCredit) Color(0xFFEF4444) else textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Expense (Debit)",
                            fontSize = 13.5.sp,
                            color = if (!isCredit) Color(0xFFEF4444) else textSecondary,
                            fontWeight = if (!isCredit) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                // Income (Credit) Option
                Surface(
                    onClick = { isCredit = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCredit) Color(0xFF0F1D17) else cardBg,
                    border = BorderStroke(
                        if (isCredit) 1.5.dp else 1.dp,
                        if (isCredit) Color(0xFF10B981) else cardBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddCircleOutline,
                            contentDescription = null,
                            tint = if (isCredit) Color(0xFF10B981) else textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Income (Credit)",
                            fontSize = 13.5.sp,
                            color = if (isCredit) Color(0xFF10B981) else textSecondary,
                            fontWeight = if (isCredit) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // 4. MERCHANT / PERSON CARD (Clean placeholder without "e.g." text)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (recentMerchants.isNotEmpty()) 10.dp else 16.dp),
                shape = RoundedCornerShape(14.dp),
                color = cardBg,
                border = BorderStroke(1.dp, if (merchantError) accentDebit else cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFF1F2433), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PersonOutline,
                            contentDescription = null,
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (merchant.isEmpty()) {
                            Text(
                                text = "Merchant / Person",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF6B7280)
                            )
                        }
                        BasicTextField(
                            value = merchant,
                            onValueChange = {
                                merchant = it
                                if (merchantError && it.isNotBlank()) merchantError = false
                            },
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimary
                            ),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (merchant.isNotEmpty()) {
                        IconButton(
                            onClick = { merchant = "" },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (merchantError) {
                Text(
                    text = "Please enter merchant or person name",
                    color = accentDebit,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }

            // Quick suggestions row using actual previous transactions
            if (recentMerchants.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recentMerchants.take(6).forEach { recentName ->
                        Surface(
                            onClick = {
                                merchant = recentName
                                merchantError = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, cardBorder)
                        ) {
                            Text(
                                text = recentName,
                                fontSize = 12.sp,
                                color = Color(0xFF9CA3AF),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            // 5. CATEGORY (Curved pill buttons like tags taking small vertical space, White border highlight on selected, White "See all >")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CATEGORY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = sectionLabelColor,
                    letterSpacing = 0.8.sp
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showCategoryPicker = true }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "See all",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "See all",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // One-line scrollable curved pill category buttons (like tags)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                backendCategories.forEach { cat ->
                    val isSelected = cat.id == selectedCategoryId || cat.name.equals(selectedCategoryName, ignoreCase = true)
                    Surface(
                        onClick = {
                            selectedCategoryId = cat.id
                            selectedCategoryName = cat.name
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF1E2638) else Color(0xFF161D2B),
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) Color.White else Color(0xFF263248)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = CategoryVisuals.getIcon(cat.iconKey),
                                contentDescription = cat.name,
                                tint = if (isSelected) Color.White else Color(0xFF9CA3AF),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = cat.name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 6. PAID FROM ACCOUNT (White border on selected, Manage > header)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PAID FROM ACCOUNT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = sectionLabelColor,
                    letterSpacing = 0.8.sp
                )

                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Manage",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF9CA3AF)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Manage",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cash / Wallet Card
                val isCashSelected = selectedAccountId == null && (selectedAccountSuffix == "CASH" || selectedAccountSuffix == null)
                Surface(
                    onClick = {
                        selectedAccountId = null
                        selectedAccountSuffix = "CASH"
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = cardBg,
                    border = BorderStroke(
                        if (isCashSelected) 1.5.dp else 1.dp,
                        if (isCashSelected) Color.White else cardBorder
                    ),
                    modifier = Modifier.height(54.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            tint = if (isCashSelected) Color.White else Color(0xFF9CA3AF),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cash / Wallet",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isCashSelected) Color.White else Color(0xFF9CA3AF)
                        )
                    }
                }

                // Known Accounts from DB or defaults if DB empty
                if (knownAccounts.isNotEmpty()) {
                    knownAccounts.forEach { acc ->
                        val isAccSelected = selectedAccountId == acc.id
                        val badgeColor = when {
                            acc.institutionName?.contains("SBI", true) == true -> Color(0xFF0A65C2)
                            acc.institutionName?.contains("HDFC", true) == true -> Color(0xFF003366)
                            acc.institutionName?.contains("ICICI", true) == true -> Color(0xFFB02A30)
                            else -> Color(0xFF1E293B)
                        }
                        Surface(
                            onClick = {
                                selectedAccountId = acc.id
                                selectedAccountSuffix = acc.accountSuffix
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = cardBg,
                            border = BorderStroke(
                                if (isAccSelected) 1.5.dp else 1.dp,
                                if (isAccSelected) Color.White else cardBorder
                            ),
                            modifier = Modifier.height(54.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(badgeColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (acc.institutionName?.take(3) ?: "BNK").uppercase(),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = acc.institutionName ?: "Bank Account",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isAccSelected) Color.White else Color(0xFFE2E8F0)
                                    )
                                    Text(
                                        text = "•••• ${acc.accountSuffix}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF9CA3AF)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Fallback visual demo accounts matching image.png
                    listOf(
                        Triple("sbi", "SBI Savings", "6828"),
                        Triple("hdfc", "HDFC Salary", "4821")
                    ).forEach { (id, name, suffix) ->
                        val isAccSelected = selectedAccountId == id
                        val badgeColor = if (id == "sbi") Color(0xFF0A65C2) else Color(0xFF003366)
                        Surface(
                            onClick = {
                                selectedAccountId = id
                                selectedAccountSuffix = suffix
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = cardBg,
                            border = BorderStroke(
                                if (isAccSelected) 1.5.dp else 1.dp,
                                if (isAccSelected) Color.White else cardBorder
                            ),
                            modifier = Modifier.height(54.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(badgeColor, if (id == "sbi") CircleShape else RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (id == "sbi") "SBI" else "HDFC",
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = name,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isAccSelected) Color.White else Color(0xFFE2E8F0)
                                    )
                                    Text(
                                        text = "•••• $suffix",
                                        fontSize = 11.sp,
                                        color = Color(0xFF9CA3AF)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 7. PAYMENT CHANNEL (White border on selected)
            Text(
                text = "PAYMENT CHANNEL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = sectionLabelColor,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                paymentChannels.forEach { channel ->
                    val isChanSelected = selectedChannel.equals(channel, true)
                    Surface(
                        onClick = { selectedChannel = channel },
                        shape = RoundedCornerShape(10.dp),
                        color = cardBg,
                        border = BorderStroke(
                            if (isChanSelected) 1.5.dp else 1.dp,
                            if (isChanSelected) Color.White else cardBorder
                        )
                    ) {
                        Text(
                            text = channel,
                            fontSize = 13.sp,
                            fontWeight = if (isChanSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isChanSelected) Color.White else Color(0xFF9CA3AF),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
            }

            // 8. DATE & TIME
            Text(
                text = "DATE & TIME",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = sectionLabelColor,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Surface(
                onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
                    DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            cal.set(Calendar.YEAR, y)
                            cal.set(Calendar.MONTH, m)
                            cal.set(Calendar.DAY_OF_MONTH, d)
                            TimePickerDialog(
                                context,
                                { _, h, min ->
                                    cal.set(Calendar.HOUR_OF_DAY, h)
                                    cal.set(Calendar.MINUTE, min)
                                    cal.set(Calendar.SECOND, 0)
                                    dateMillis = cal.timeInMillis
                                },
                                cal.get(Calendar.HOUR_OF_DAY),
                                cal.get(Calendar.MINUTE),
                                false
                            ).show()
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(14.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = formattedDateStr,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 9. NOTE (OPTIONAL)
            Text(
                text = "NOTE (OPTIONAL)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = sectionLabelColor,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(14.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (note.isEmpty()) {
                            Text(
                                text = "Add a note (optional)",
                                fontSize = 14.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                        BasicTextField(
                            value = note,
                            onValueChange = { note = it },
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                color = textPrimary
                            ),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 10. TAGS (OPTIONAL)
            Text(
                text = "TAGS (OPTIONAL)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = sectionLabelColor,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tagsList.forEach { tagItem ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF161D2B),
                        border = BorderStroke(1.dp, Color(0xFF263248))
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#$tagItem",
                                fontSize = 12.sp,
                                color = Color(0xFFE2E8F0)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove tag",
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable {
                                        tagsList = tagsList.filter { it != tagItem }
                                    }
                            )
                        }
                    }
                }

                // "+ Add Tag" Chip
                Surface(
                    onClick = { showAddTagDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF161D2B),
                    border = BorderStroke(1.dp, Color(0xFF263248))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add Tag",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            // 11. BOTTOM ACTION BUTTONS (Matching image1.png: Dark Cancel pill and Solid White Add Expense pill with Black text)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Delete button if in edit/approve mode with onDelete callback
                if (onDelete != null) {
                    Surface(
                        onClick = onDelete,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF261215),
                        border = BorderStroke(1.dp, Color(0xFF4C1D24))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = accentDebit,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Delete",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = accentDebit
                            )
                        }
                    }
                } else {
                    // Cancel Pill Button (matching left button of image1.png)
                    Surface(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF141923),
                        border = BorderStroke(1.dp, Color(0xFF262E40))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = Color.White
                            )
                        }
                    }
                }

                // Solid White Pill Button with Black Text (matching right button "✓ Done" of image1.png)
                Surface(
                    onClick = {
                        val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
                        if (parsedAmount <= 0.0) {
                            amountError = true
                            return@Surface
                        }
                        amountError = false

                        val resolvedMerchant = if (merchant.isNotBlank()) merchant.trim() else "Unknown Merchant"
                        val resolvedTag = if (tagsList.isNotEmpty()) tagsList.joinToString(", ") else selectedCategoryName
                        val finalNote = if (selectedChannel.isNotBlank() && selectedChannel != "UPI") {
                            if (note.isNotBlank()) "$note • $selectedChannel" else selectedChannel
                        } else {
                            note
                        }

                        if (onAddFull != null) {
                            onAddFull(
                                parsedAmount,
                                resolvedMerchant,
                                type,
                                resolvedTag,
                                finalNote,
                                dateMillis,
                                selectedAccountId,
                                selectedAccountSuffix,
                                selectedCategoryId
                            )
                        } else {
                            onAdd(
                                parsedAmount,
                                resolvedMerchant,
                                type,
                                resolvedTag,
                                finalNote,
                                dateMillis
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
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
                            text = when {
                                isEditMode -> "Save"
                                rawText != null -> "Approve"
                                isCredit -> "Add Income"
                                else -> "Add Expense"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}
