package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.window.Dialog
import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.service.AccountReconciliationManager
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var isEditMode by remember { mutableStateOf(false) }

    // Editable state
    var editedMerchant by remember(expense) { mutableStateOf(expense.merchant) }
    var editedCategory by remember(expense) { mutableStateOf(expense.tag ?: expense.category ?: "") }
    var editedCategoryId by remember(expense) { mutableStateOf(expense.categoryId) }
    var editedNote by remember(expense) { mutableStateOf(expense.note ?: "") }
    var merchantError by remember { mutableStateOf<String?>(null) }
    var showCategoryDropdown by remember { mutableStateOf(false) }

    // Account reconciliation dialog state
    var showAccountPickerDialog by remember { mutableStateOf(false) }
    var isReassignPicker by remember { mutableStateOf(false) }
    var showUnlinkConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val isCredit = expense.type.equals("Credit", ignoreCase = true)
    val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val sign = if (isCredit) "+" else "-"
    val amountColor = if (isCredit) Color(0xFF2E7D32) else Color(0xFFC62828)
    val dateTimeFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    val linkedAccount = knownAccounts.find { it.id == expense.accountId }

    Dialog(onDismissRequest = {
        if (!isEditMode) onDismiss()
    }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header: Title & Close / Cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditMode) "Edit Transaction" else "Transaction Detail",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1E1E)
                    )
                    IconButton(
                        onClick = {
                            if (isEditMode) {
                                // Cancel: discard changes
                                editedMerchant = expense.merchant
                                editedCategory = expense.tag ?: expense.category ?: ""
                                editedNote = expense.note ?: ""
                                merchantError = null
                                isEditMode = false
                            } else {
                                onDismiss()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF757575)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Amount and Direction (Read-only System Fields)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isCredit) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Amount",
                                fontSize = 12.sp,
                                color = Color(0xFF616161)
                            )
                            Text(
                                text = "$sign${currencyFormatter.format(expense.amount).replace("Rs.", "₹")}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = amountColor
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCredit) Color(0xFFC8E6C9) else Color(0xFFFFCDD2)
                        ) {
                            Text(
                                text = if (isCredit) "CREDIT" else "DEBIT",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = amountColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Merchant Section
                Text(
                    text = "Merchant",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF757575)
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (isEditMode) {
                    OutlinedTextField(
                        value = editedMerchant,
                        onValueChange = {
                            editedMerchant = it
                            if (it.trim().isNotEmpty()) merchantError = null
                        },
                        isError = merchantError != null,
                        supportingText = merchantError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                } else {
                    Text(
                        text = expense.merchant.ifEmpty { "Unknown Merchant" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E1E1E)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Date & Time (Read-only System Field)
                Text(
                    text = "Date & Time",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF757575)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateTimeFormatter.format(Date(expense.dateMillis)),
                    fontSize = 14.sp,
                    color = Color(0xFF333333)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Category Section
                Text(
                    text = "Category",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF757575)
                )
                Spacer(modifier = Modifier.height(4.dp))
                val resolvedCategory = remember(expense, allCategories) {
                    allCategories.find { it.id == expense.categoryId || it.name.equals(expense.tag, ignoreCase = true) }
                        ?: BuiltInCategories.findLegacyMapping(expense.tag)
                }

                if (isEditMode) {
                    val selectableCategories = remember(allCategories, resolvedCategory) {
                        val active = allCategories.filter { !it.isArchived }
                        if (resolvedCategory != null && resolvedCategory.isArchived && !active.any { it.id == resolvedCategory.id }) {
                            listOf(resolvedCategory) + active
                        } else {
                            active
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = editedCategory.ifBlank { "Uncategorized" },
                            onValueChange = {},
                            readOnly = true,
                            leadingIcon = {
                                val currentSelection = selectableCategories.find { it.id == editedCategoryId || it.name.equals(editedCategory, ignoreCase = true) }
                                if (currentSelection != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(CategoryVisuals.getContainerColor(currentSelection.colorKey), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = CategoryVisuals.getIcon(currentSelection.iconKey),
                                            contentDescription = null,
                                            tint = CategoryVisuals.getColor(currentSelection.colorKey),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else {
                                    Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Gray)
                                }
                            },
                            trailingIcon = {
                                IconButton(onClick = { showCategoryDropdown = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Category")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCategoryDropdown = true },
                            shape = RoundedCornerShape(10.dp)
                        )
                        DropdownMenu(
                            expanded = showCategoryDropdown,
                            onDismissRequest = { showCategoryDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None (Uncategorized)") },
                                leadingIcon = {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    editedCategoryId = null
                                    editedCategory = ""
                                    showCategoryDropdown = false
                                }
                            )
                            selectableCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(cat.name)
                                            if (cat.isArchived) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("(Archived)", fontSize = 10.sp, color = Color.Gray)
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .background(CategoryVisuals.getContainerColor(cat.colorKey), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = CategoryVisuals.getIcon(cat.iconKey),
                                                contentDescription = null,
                                                tint = CategoryVisuals.getColor(cat.colorKey),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        editedCategoryId = cat.id
                                        editedCategory = cat.name
                                        showCategoryDropdown = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    val catName = resolvedCategory?.name ?: (expense.tag ?: expense.category)?.takeIf { it.isNotBlank() } ?: "Uncategorized"
                    val iconKey = resolvedCategory?.iconKey ?: "category"
                    val colorKey = resolvedCategory?.colorKey ?: "default"

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CategoryVisuals.getContainerColor(colorKey),
                        border = BorderStroke(1.dp, CategoryVisuals.getColor(colorKey).copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = CategoryVisuals.getIcon(iconKey),
                                contentDescription = null,
                                tint = CategoryVisuals.getColor(colorKey),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = catName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = CategoryVisuals.getColor(colorKey)
                            )
                            if (resolvedCategory?.isArchived == true) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "(Archived)",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Account Section (with Assign, Change, Unlink)
                Text(
                    text = "Account",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF757575)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF5F5F5)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (linkedAccount != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = linkedAccount.institutionName ?: "Bank Account",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1E1E1E)
                                    )
                                    Text(
                                        text = "${AccountReconciliationManager.formatAccountType(linkedAccount.instrumentType, linkedAccount.accountSuffix)}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF616161)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFE8F5E9)
                                ) {
                                    Text(
                                        text = "Linked",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (!isEditMode && onReassignAccount != null && onUnlinkAccount != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            isReassignPicker = true
                                            showAccountPickerDialog = true
                                        }
                                    ) {
                                        Text("Change Account", fontSize = 12.sp)
                                    }
                                    TextButton(
                                        onClick = { showUnlinkConfirmDialog = true },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD32F2F))
                                    ) {
                                        Text("Unlink", fontSize = 12.sp)
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Account not linked",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = Color(0xFF616161)
                                    )
                                    if (!expense.accountSuffix.isNullOrBlank()) {
                                        Text(
                                            text = "Ending ${AccountReconciliationManager.formatMaskedSuffix(expense.accountSuffix ?: "")}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF757575)
                                        )
                                    }
                                }
                                if (!isEditMode && onAssignAccount != null) {
                                    Button(
                                        onClick = {
                                            isReassignPicker = false
                                            showAccountPickerDialog = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Assign Account", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Notes Section
                Text(
                    text = "Notes",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF757575)
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (isEditMode) {
                    OutlinedTextField(
                        value = editedNote,
                        onValueChange = { editedNote = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp),
                        shape = RoundedCornerShape(10.dp),
                        placeholder = { Text("Add notes here...") }
                    )
                } else {
                    Text(
                        text = expense.note?.takeIf { it.isNotBlank() } ?: "No notes",
                        fontSize = 13.sp,
                        color = if (expense.note.isNullOrBlank()) Color(0xFF9E9E9E) else Color(0xFF333333)
                    )
                }

                // Reference / Relationship badges (Read-only Audit Info)
                val isSelfTransfer = expense.relationshipType == TransactionRelationshipType.SELF_TRANSFER
                val hasRef = expense.notificationKey.isNotBlank()
                if (isSelfTransfer || hasRef || expense.isPending) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "System Information",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF757575)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isSelfTransfer) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFE0F7FA)
                            ) {
                                Text(
                                    text = "Self-Transfer",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF006064),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        if (expense.isPending) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFFF3E0)
                            ) {
                                Text(
                                    text = "Pending Review",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE65100),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    if (hasRef) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Reference: ${expense.notificationKey.take(32)}",
                            fontSize = 11.sp,
                            color = Color(0xFF9E9E9E)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                if (isEditMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                editedMerchant = expense.merchant
                                editedCategory = expense.tag ?: expense.category ?: ""
                                editedNote = expense.note ?: ""
                                merchantError = null
                                isEditMode = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                val trimmed = editedMerchant.trim()
                                if (trimmed.isEmpty()) {
                                    merchantError = "Merchant cannot be empty"
                                    return@Button
                                }
                                onSaveEdit(
                                    expense.id,
                                    trimmed,
                                    editedCategory.trim().ifEmpty { null },
                                    editedNote.trim().ifEmpty { null }
                                )
                                isEditMode = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Save")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (onDelete != null) {
                            OutlinedButton(
                                onClick = { showDeleteConfirmDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
                                border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete")
                            }
                        }
                        Button(
                            onClick = { isEditMode = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit")
                        }
                    }
                }
            }
        }
    }

    // Account Picker Dialog (Assign / Change)
    if (showAccountPickerDialog) {
        AlertDialog(
            onDismissRequest = { showAccountPickerDialog = false },
            title = {
                Text(if (isReassignPicker) "Change Account" else "Assign Account")
            },
            text = {
                val candidateAccounts = remember(knownAccounts, expense) {
                    val suffix = expense.accountSuffix
                    if (!suffix.isNullOrBlank()) {
                        val matching = knownAccounts.filter { it.accountSuffix == suffix }
                        val others = knownAccounts.filter { it.accountSuffix != suffix }
                        matching + others
                    } else {
                        knownAccounts
                    }
                }

                if (candidateAccounts.isEmpty()) {
                    Text("No known financial accounts found. Import SMS or register accounts first.")
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        candidateAccounts.forEach { acc ->
                            val isMatchSuffix = acc.accountSuffix == expense.accountSuffix
                            Card(
                                onClick = {
                                    showAccountPickerDialog = false
                                    if (isReassignPicker) {
                                        onReassignAccount?.invoke(expense.id, acc.id)
                                    } else {
                                        onAssignAccount?.invoke(expense.id, acc.id)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isMatchSuffix) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
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
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = AccountReconciliationManager.formatAccountType(acc.instrumentType, acc.accountSuffix),
                                            fontSize = 12.sp,
                                            color = Color(0xFF616161)
                                        )
                                    }
                                    if (isMatchSuffix) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFC8E6C9)
                                        ) {
                                            Text(
                                                "Matching Suffix",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2E7D32),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                TextButton(onClick = { showAccountPickerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Unlink Confirmation Dialog
    if (showUnlinkConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUnlinkConfirmDialog = false },
            title = { Text("Unlink Account?") },
            text = {
                Text("This will remove the account link for this transaction. The transaction will appear under Unresolved Transactions.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnlinkConfirmDialog = false
                        onUnlinkAccount?.invoke(expense.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Unlink")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog (Part 21)
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete this transaction?") },
            text = {
                Column {
                    Text(
                        text = "$sign${currencyFormatter.format(expense.amount).replace("Rs.", "₹")} ${expense.type.lowercase()}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = amountColor
                    )
                    Text(
                        text = expense.merchant.ifEmpty { "Unknown Merchant" },
                        fontSize = 14.sp,
                        color = Color(0xFF333333)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This removes the transaction from ArcTracker.",
                        fontSize = 13.sp,
                        color = Color(0xFF757575)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete?.invoke(expense)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
