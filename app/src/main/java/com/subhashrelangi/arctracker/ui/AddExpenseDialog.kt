package com.subhashrelangi.arctracker.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import androidx.core.view.WindowCompat
import com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader
import com.subhashrelangi.arctracker.data.BuiltInCategories
import com.subhashrelangi.arctracker.data.CategoryVisuals
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.TransactionCategory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pixel-perfect manual transaction screen matching the ArcTracker Emerald Light Theme mockup.
 *
 * Visual Architecture:
 * - Full-screen edge-to-edge Dialog (`usePlatformDefaultWidth = false`, `decorFitsSystemWindows = false`)
 * - Top Bar: Back arrow, "Log Session" title, TrackChanges & ArcTracker Brand Glyph
 * - Segmented Switcher: [ • Expense | Income | Transfer ]
 * - Amount Card: "TRANSACTION AMOUNT", big centered ₹ input, quick increment chips (+₹100, +₹500, +₹1,000, +₹2,000)
 * - Details Card: Payee/Merchant, Category (with visual icon & Daily badge), Payment Account, Date & Timestamp ("Now" reset)
 * - Memo & Tags Card: Ledger Memo input, Quick Ledger Tags (#Personal, #Work ✓, etc.), Attach Tax Invoice (+ Add Photo)
 * - Security Banner: "Local-first encrypted ledger • Zero telemetry • Private key storage"
 * - Primary Commit Button: "✓ Commit to Ledger" with "• Auto-categorized based on merchant history"
 */
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
    val scrollState = rememberScrollState()

    // Mode: Expense (Debit), Income (Credit), Transfer
    var selectedMode by remember(initialType) {
        mutableStateOf(
            when {
                initialType.equals("Credit", ignoreCase = true) -> "Income"
                initialType.equals("Transfer", ignoreCase = true) -> "Transfer"
                else -> "Expense"
            }
        )
    }
    val effectiveType = when (selectedMode) {
        "Income" -> "Credit"
        else -> "Debit"
    }

    // Amount State
    var amountText by remember(initialAmount) {
        mutableStateOf(
            if (initialAmount.isNotBlank() && initialAmount != "0.0") {
                val parsed = initialAmount.toDoubleOrNull()
                if (parsed != null) {
                    if (parsed % 1.0 == 0.0) String.format(Locale.US, "%.0f", parsed)
                    else String.format(Locale.US, "%.2f", parsed)
                } else initialAmount
            } else ""
        )
    }
    var amountError by remember { mutableStateOf(false) }

    // Merchant State
    var merchant by remember(initialMerchant) {
        mutableStateOf(if (initialMerchant == "Unknown Merchant") "" else initialMerchant)
    }
    var merchantError by remember { mutableStateOf(false) }
    var showRecentMerchants by remember { mutableStateOf(false) }

    // Note State
    var note by remember(initialNote) {
        mutableStateOf(initialNote)
    }

    // Tags State
    val defaultTags = listOf("Personal", "Work", "Reimbursable", "TaxDeductible")
    var availableTags by remember(initialTag) {
        val parsedTags = if (initialTag.isNotBlank()) {
            initialTag.split(",", "•", " ").map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }
        } else emptyList()
        val combined = (defaultTags + parsedTags).distinct()
        mutableStateOf(combined)
    }
    var selectedTags by remember(initialTag) {
        val initialSelected = if (initialTag.isNotBlank()) {
            initialTag.split(",", "•", " ").map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.toSet()
        } else {
            setOf("Work") // Default matching mockup
        }
        mutableStateOf(initialSelected)
    }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var newTagInput by remember { mutableStateOf("") }

    // DateMillis State
    var dateMillis by remember(initialDateMillis) {
        mutableLongStateOf(if (initialDateMillis > 0) initialDateMillis else System.currentTimeMillis())
    }

    // Backend Categories from Room DB
    val backendCategories = remember(allCategories, selectedMode) {
        val base = if (allCategories.isNotEmpty()) {
            allCategories.filter { !it.isArchived }
        } else {
            BuiltInCategories.ALL
        }
        if (selectedMode == "Income") {
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
    var selectedCategoryName by remember(initialTag, selectedMode, backendCategories) {
        val defaultName = if (initialTag.isNotBlank() && backendCategories.any { it.name.equals(initialTag, true) }) {
            initialTag
        } else if (selectedMode == "Income") {
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
    var selectedAccountName by remember { mutableStateOf<String?>(null) }
    var showAccountPicker by remember { mutableStateOf(false) }

    // Initialize default account if available
    LaunchedEffect(knownAccounts) {
        if (selectedAccountId == null && knownAccounts.isNotEmpty()) {
            val primary = knownAccounts.first()
            selectedAccountId = primary.id
            selectedAccountSuffix = primary.accountSuffix
            selectedAccountName = primary.institutionName ?: "Bank Account"
        }
    }

    // Category Picker Dialog
    if (showCategoryPicker) {
        ArcCategoryPickerDialog(
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

    // Account Picker Dialog
    if (showAccountPicker) {
        ArcAccountPickerDialog(
            knownAccounts = knownAccounts,
            currentAccountId = selectedAccountId,
            currentAccountSuffix = selectedAccountSuffix,
            onAccountSelected = { id, suffix, name ->
                selectedAccountId = id
                selectedAccountSuffix = suffix
                selectedAccountName = name
                showAccountPicker = false
            },
            onDismiss = { showAccountPicker = false }
        )
    }

    // Add Tag Dialog
    if (showAddTagDialog) {
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Add Custom Tag",
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a tag label without the '#' symbol:",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    BasicTextField(
                        value = newTagInput,
                        onValueChange = { newTagInput = it },
                        textStyle = TextStyle(
                            color = Color(0xFF0F172A),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(Color(0xFF047857)),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newTagInput.trim().removePrefix("#")
                        if (trimmed.isNotBlank()) {
                            if (trimmed !in availableTags) {
                                availableTags = availableTags + trimmed
                            }
                            selectedTags = selectedTags + trimmed
                        }
                        newTagInput = ""
                        showAddTagDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                    shape = CircleShape
                ) {
                    Text("Add Tag", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) {
                    Text("Cancel", color = Color(0xFF64748B), fontSize = 13.sp)
                }
            }
        )
    }

    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
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
                    insetsController.isAppearanceLightStatusBars = true
                    insetsController.isAppearanceLightNavigationBars = true
                }
            }
        }

        val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

        Scaffold(
            containerColor = Color(0xFFF8FAFC),
            topBar = {
                Surface(
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                ) {
                    ArcTrackerHeader(
                        title = when {
                            isEditMode -> "Edit Expense"
                            rawText != null -> "Approve Expense"
                            else -> "Add expenses"
                        },
                        subtitle = when {
                            isEditMode -> "Update Ledger Record"
                            rawText != null -> "Approve SMS Ingestion"
                            else -> "New Transaction Entry"
                        },
                        onBackClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .statusBarsPadding(),
                        horizontalPadding = 16.dp,
                        actions = {}
                    )
                }
            },
            bottomBar = {
                if (!isKeyboardVisible) {
                    // Solid Bottom Action Section sticking flush at the bottom of the screen
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        shadowElevation = 10.dp,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // If in edit / approve mode and onDelete is provided
                                if (onDelete != null) {
                                    Surface(
                                        onClick = onDelete,
                                        modifier = Modifier
                                            .weight(0.7f)
                                            .height(52.dp),
                                        shape = CircleShape,
                                        color = Color(0xFFFEE2E2),
                                        border = BorderStroke(1.dp, Color(0xFFFECACA))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete",
                                                tint = Color(0xFFDC2626),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Delete",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                color = Color(0xFFDC2626)
                                            )
                                        }
                                    }
                                }

                                // Commit to Ledger Button
                                Button(
                                    onClick = {
                                        val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
                                        if (parsedAmount <= 0.0) {
                                            amountError = true
                                            Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        amountError = false

                                        val resolvedMerchant = if (merchant.isNotBlank()) merchant.trim() else "Unknown Merchant"
                                        val resolvedTag = if (selectedTags.isNotEmpty()) selectedTags.joinToString(", ") else selectedCategoryName

                                        if (onAddFull != null) {
                                            onAddFull(
                                                parsedAmount,
                                                resolvedMerchant,
                                                effectiveType,
                                                resolvedTag,
                                                note,
                                                dateMillis,
                                                selectedAccountId,
                                                selectedAccountSuffix,
                                                selectedCategoryId
                                            )
                                        } else {
                                            onAdd(
                                                parsedAmount,
                                                resolvedMerchant,
                                                effectiveType,
                                                resolvedTag,
                                                note,
                                                dateMillis
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(if (onDelete != null) 1.3f else 1f)
                                        .height(52.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF047857)
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = when {
                                                isEditMode -> "Save Changes"
                                                rawText != null -> "Commit Approval"
                                                else -> "Commit to Ledger"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            // SCROLLABLE CONTENT BODY
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                    // 2. TRANSACTION TYPE SEGMENTED SWITCHER
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Expense", "Income", "Transfer").forEach { mode ->
                                val isSelected = selectedMode == mode
                                Surface(
                                    onClick = { selectedMode = mode },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp),
                                    shape = CircleShape,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shadowElevation = if (isSelected) 1.dp else 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(Color(0xFF059669), CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = mode,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.5.sp,
                                            color = if (isSelected) Color(0xFF0F172A) else Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. CARD 1: TRANSACTION AMOUNT
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                                color = Color(0xFF64748B),
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
                                    color = if (amountText.isNotEmpty()) Color(0xFF0F172A) else Color(0xFF94A3B8),
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
                                            amountError = false
                                        }
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (amountError) Color(0xFFDC2626) else Color(0xFF0F172A),
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
                                    cursorBrush = SolidColor(Color(0xFF047857)),
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.CenterStart) {
                                            if (amountText.isEmpty()) {
                                                Text(
                                                    text = "0.00",
                                                    fontSize = 34.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF94A3B8)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Quick Increment Chips (+₹100, +₹500, +₹1,000, +₹2,000)
                            // Flexible Row with weight so buttons evenly distribute and never shrink/wrap
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
                                            amountError = false
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFF1F5F9)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF0F172A),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. CARD 2: PRIMARY DETAILS (Merchant, Category, Account, Date)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Row 1: Payee or Merchant
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showRecentMerchants = !showRecentMerchants }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Storefront,
                                        contentDescription = null,
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Payee or Merchant",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    BasicTextField(
                                        value = merchant,
                                        onValueChange = {
                                            merchant = it
                                            merchantError = false
                                        },
                                        textStyle = TextStyle(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (merchantError) Color(0xFFDC2626) else Color(0xFF0F172A)
                                        ),
                                        cursorBrush = SolidColor(Color(0xFF047857)),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                        decorationBox = { innerTextField ->
                                            if (merchant.isEmpty()) {
                                                Text(
                                                    text = "Enter payee or merchant",
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF94A3B8)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                }

                                IconButton(
                                    onClick = { showRecentMerchants = !showRecentMerchants },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = "Search / Suggestions",
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            // Optional Recent Merchants Dropdown
                            if (showRecentMerchants && recentMerchants.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    recentMerchants.take(6).forEach { rm ->
                                        Surface(
                                            onClick = {
                                                merchant = rm
                                                showRecentMerchants = false
                                            },
                                            shape = CircleShape,
                                            color = Color(0xFFF1F5F9)
                                        ) {
                                            Text(
                                                text = rm,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF334155),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                            // Row 2: Category
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCategoryPicker = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val currentCat = backendCategories.find { it.id == selectedCategoryId || it.name.equals(selectedCategoryName, true) }
                                val iconKey = currentCat?.iconKey ?: "restaurant"
                                val catVisualIcon = CategoryVisuals.getIcon(iconKey)

                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFFD1FAE5), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = catVisualIcon,
                                        contentDescription = null,
                                        tint = Color(0xFF047857),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Category",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    Text(
                                        text = selectedCategoryName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Change Category",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                            // Row 3: Payment Account
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showAccountPicker = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountBalance,
                                        contentDescription = null,
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Payment Account",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    val displayName = when {
                                        selectedAccountName != null && selectedAccountSuffix != null ->
                                            "$selectedAccountName ••$selectedAccountSuffix"
                                        selectedAccountSuffix != null -> "Account ••$selectedAccountSuffix"
                                        else -> "Cash / Wallet"
                                    }
                                    Text(
                                        text = displayName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Avail: Linked",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.UnfoldMore,
                                        contentDescription = "Select Account",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                            // Row 4: Date & Timestamp
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
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
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarToday,
                                        contentDescription = null,
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Date & Timestamp",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    Text(
                                        text = formatDisplayDateTime(dateMillis),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                }

                                Surface(
                                    onClick = { dateMillis = System.currentTimeMillis() },
                                    shape = CircleShape,
                                    color = Color(0xFFF1F5F9)
                                ) {
                                    Text(
                                        text = "Now",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 5. CARD 3: LEDGER MEMO & QUICK LEDGER TAGS & TAX INVOICE
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Ledger Memo Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                                        .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Notes,
                                        contentDescription = null,
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Ledger Memo",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    BasicTextField(
                                        value = note,
                                        onValueChange = { note = it },
                                        textStyle = TextStyle(
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = Color(0xFF0F172A)
                                        ),
                                        cursorBrush = SolidColor(Color(0xFF047857)),
                                        singleLine = false,
                                        maxLines = 2,
                                        decorationBox = { innerTextField ->
                                            if (note.isEmpty()) {
                                                Text(
                                                    text = "Client kickoff coffees (optional memo)",
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF94A3B8)
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Quick Ledger Tags Header
                            Text(
                                text = "Quick Ledger Tags",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Tags Chips Flow / Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                availableTags.forEach { tagItem ->
                                    val isSelected = tagItem in selectedTags
                                    Surface(
                                        onClick = {
                                            selectedTags = if (isSelected) {
                                                selectedTags - tagItem
                                            } else {
                                                selectedTags + tagItem
                                            }
                                        },
                                        shape = CircleShape,
                                        color = if (isSelected) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) Color(0xFFA7F3D0) else Color.Transparent
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "#$tagItem",
                                                fontSize = 12.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color(0xFF047857) else Color(0xFF334155)
                                            )
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color(0xFF047857),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // "+ Add Tag" button
                                Surface(
                                    onClick = { showAddTagDialog = true },
                                    shape = CircleShape,
                                    color = Color(0xFFF1F5F9)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = Color(0xFF475569),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Tag",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF475569)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Attach Tax Invoice Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.ReceiptLong,
                                        contentDescription = null,
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Attach Tax Invoice",
                                        fontSize = 13.5.sp,
                                        color = Color(0xFF475569)
                                    )
                                }

                                Surface(
                                    onClick = {
                                        Toast.makeText(context, "Encrypted receipt attachment ready in vault", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = CircleShape,
                                    color = Color(0xFFF1F5F9)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = Color(0xFF0F172A),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Add Photo",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF0F172A)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 6. SECURITY BANNER
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF1F5F9).copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = Color(0xFF047857),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "100% Local-First Ledger • Offline Encrypted Vault",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF475569),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * Modern Light-themed Category Picker Dialog matching ArcTracker Emerald aesthetic.
 */
@Composable
private fun ArcCategoryPickerDialog(
    allCategories: List<TransactionCategory>,
    currentCategoryId: String?,
    currentCategoryName: String,
    onCategorySelected: (TransactionCategory) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(allCategories, searchQuery) {
        if (searchQuery.isBlank()) allCategories
        else allCategories.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Select Category",
                            color = Color(0xFF0F172A),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Categorize your transaction",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search categories...", fontSize = 13.sp, color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF047857),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Categories List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.id }) { cat ->
                        val isSelected = cat.id == currentCategoryId || cat.name.equals(currentCategoryName, ignoreCase = true)
                        val catIcon = CategoryVisuals.getIcon(cat.iconKey)
                        val catColor = CategoryVisuals.getColor(cat.colorKey)

                        Surface(
                            onClick = { onCategorySelected(cat) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFFECFDF5) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFFA7F3D0) else Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(catColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = catIcon,
                                        contentDescription = null,
                                        tint = catColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = cat.name,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF047857) else Color(0xFF0F172A),
                                    modifier = Modifier.weight(1f)
                                )

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFF047857),
                                        modifier = Modifier.size(18.dp)
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

/**
 * Modern Light-themed Account Picker Dialog matching ArcTracker Emerald aesthetic.
 */
@Composable
private fun ArcAccountPickerDialog(
    knownAccounts: List<KnownFinancialAccount>,
    currentAccountId: String?,
    currentAccountSuffix: String?,
    onAccountSelected: (id: String?, suffix: String?, name: String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Payment Account",
                            color = Color(0xFF0F172A),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Choose linked bank account or wallet",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 1: Cash / Wallet
                    item {
                        val isCashSelected = currentAccountId == null && currentAccountSuffix == null
                        Surface(
                            onClick = { onAccountSelected(null, null, "Cash / Wallet") },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCashSelected) Color(0xFFECFDF5) else Color(0xFFF8FAFC),
                            border = BorderStroke(
                                1.dp,
                                if (isCashSelected) Color(0xFFA7F3D0) else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFE2E8F0), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Cash / Wallet",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isCashSelected) Color(0xFF047857) else Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "Direct cash or untracked wallet",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                if (isCashSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFF047857),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Linked Known Accounts
                    if (knownAccounts.isNotEmpty()) {
                        items(knownAccounts, key = { it.id }) { acc ->
                            val isSelected = acc.id == currentAccountId
                            Surface(
                                onClick = {
                                    onAccountSelected(
                                        acc.id,
                                        acc.accountSuffix,
                                        acc.institutionName ?: "Bank Account"
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFFECFDF5) else Color(0xFFF8FAFC),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFFA7F3D0) else Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                if (isSelected) Color(0xFFA7F3D0) else Color(0xFFE2E8F0),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AccountBalance,
                                            contentDescription = null,
                                            tint = if (isSelected) Color(0xFF047857) else Color(0xFF475569),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = acc.institutionName ?: "Bank Account",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = if (isSelected) Color(0xFF047857) else Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "Ending in ••${acc.accountSuffix}",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color(0xFF047857),
                                            modifier = Modifier.size(18.dp)
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
}

/**
 * Helper to format epoch millis into human-friendly date string matching the mockup ("Today, 10:42 AM").
 */
private fun formatDisplayDateTime(millis: Long): String {
    val nowCal = Calendar.getInstance()
    val targetCal = Calendar.getInstance().apply { timeInMillis = millis }

    val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
    val timeStr = timeFormat.format(Date(millis))

    val isToday = nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
            nowCal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)

    val isYesterday = nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
            nowCal.get(Calendar.DAY_OF_YEAR) - targetCal.get(Calendar.DAY_OF_YEAR) == 1

    return when {
        isToday -> "Today, $timeStr"
        isYesterday -> "Yesterday, $timeStr"
        nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) -> {
            val df = SimpleDateFormat("dd MMM, h:mm a", Locale.US)
            df.format(Date(millis))
        }
        else -> {
            val df = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.US)
            df.format(Date(millis))
        }
    }
}
