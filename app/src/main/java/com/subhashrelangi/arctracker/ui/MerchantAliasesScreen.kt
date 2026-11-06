package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
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
import com.subhashrelangi.arctracker.data.MerchantAlias
import com.subhashrelangi.arctracker.service.MerchantAliasManager
import com.subhashrelangi.arctracker.service.MerchantNormalizer
import com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader
import kotlinx.coroutines.launch

private val emeraldPrimary = Color(0xFF047857)
private val slateTitle = Color(0xFF0F172A)
private val slateSub = Color(0xFF64748B)
private val cardBorder = Color(0xFFE2E8F0)
private val lightBg = Color(0xFFF8FAFC)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantAliasesScreen(
    onNavigateBack: () -> Unit,
    aliasManager: MerchantAliasManager? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val db = remember { AppDatabase.getDatabase(context) }
    val aManager = remember {
        aliasManager ?: MerchantAliasManager(db.merchantAliasDao())
    }

    val aliases by aManager.getAllFlow().collectAsState(initial = emptyList())
    val activeAliases = remember(aliases) { aliases.filter { it.isEnabled } }

    var showAddDialog by remember { mutableStateOf(false) }
    var aliasToEdit by remember { mutableStateOf<MerchantAlias?>(null) }
    var aliasToDelete by remember { mutableStateOf<MerchantAlias?>(null) }

    // Test preview state
    var testMerchantInput by remember { mutableStateOf("") }
    var resolvedPreview by remember { mutableStateOf<Pair<String?, MerchantAlias?>?>(null) }

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
                    title = "Merchant Aliases",
                    subtitle = "Canonical Payee Normalization",
                    onBackClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .statusBarsPadding(),
                    horizontalPadding = 16.dp,
                    actions = {
                        // Add Alias Button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(BorderStroke(1.dp, cardBorder), CircleShape)
                                .clickable { showAddDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add Alias",
                                tint = slateTitle,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tester & Live Resolver Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header Row with Live Indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(emeraldPrimary, CircleShape)
                            )
                            Text(
                                text = "Alias Tester & Live Resolver",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = slateTitle
                            )
                        }

                        Text(
                            text = "${activeAliases.size} Active",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = emeraldPrimary
                        )
                    }

                    Text(
                        text = "Enter any raw merchant or transaction description below to preview real-time canonical resolution.",
                        fontSize = 12.sp,
                        color = slateSub,
                        lineHeight = 17.sp
                    )

                    // Clear & Attractive Full-Width Input Box
                    OutlinedTextField(
                        value = testMerchantInput,
                        onValueChange = {
                            testMerchantInput = it
                            resolvedPreview = null
                        },
                        placeholder = {
                            Text(
                                "e.g. AMZN Mktp, SWIGGY BANGALORE, UBER TRIP...",
                                fontSize = 13.sp,
                                color = slateSub.copy(alpha = 0.7f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Storefront,
                                contentDescription = null,
                                tint = if (testMerchantInput.isNotBlank()) emeraldPrimary else slateSub,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (testMerchantInput.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        testMerchantInput = ""
                                        resolvedPreview = null
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear input",
                                        tint = slateSub,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = slateTitle
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = emeraldPrimary,
                            unfocusedBorderColor = cardBorder,
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                            cursorColor = emeraldPrimary
                        )
                    )

                    // Button Moved Down Below the Input Box
                    Button(
                        onClick = {
                            if (testMerchantInput.isNotBlank()) {
                                resolvedPreview = aManager.resolveAlias(testMerchantInput, activeAliases)
                            }
                        },
                        enabled = testMerchantInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = emeraldPrimary,
                            disabledContainerColor = Color(0xFFF1F5F9),
                            contentColor = Color.White,
                            disabledContentColor = Color(0xFF94A3B8)
                        ),
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Resolve Merchant Alias",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Resolution Result Card
                    resolvedPreview?.let { (canonical, matched) ->
                        val isMatched = matched != null
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isMatched) Color(0xFFECFDF5) else Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, if (isMatched) Color(0xFFA7F3D0) else cardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isMatched) Icons.Filled.CheckCircle else Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = if (isMatched) emeraldPrimary else slateSub,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (isMatched) "ALIAS MATCH IDENTIFIED" else "DEFAULT CANONICAL NORMALIZATION",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        color = if (isMatched) emeraldPrimary else slateSub
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "\"$testMerchantInput\"",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = slateTitle
                                    )
                                    Text(
                                        text = "➜",
                                        fontSize = 13.sp,
                                        color = if (isMatched) emeraldPrimary else slateSub
                                    )
                                    Text(
                                        text = "\"${canonical ?: testMerchantInput}\"",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMatched) emeraldPrimary else slateTitle
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (aliases.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFECFDF5))
                                .border(1.dp, Color(0xFFA7F3D0), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storefront,
                                contentDescription = null,
                                tint = emeraldPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No merchant aliases configured",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = slateTitle
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap + to normalize variations (e.g. 'AMZN' → 'Amazon')",
                            fontSize = 12.5.sp,
                            color = slateSub
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(aliases, key = { it.id }) { alias ->
                        AliasCard(
                            alias = alias,
                            onToggle = { enabled ->
                                scope.launch {
                                    val res = aManager.setAliasEnabled(alias.id, enabled)
                                    if (res.isFailure) {
                                        snackbarHostState.showSnackbar(res.exceptionOrNull()?.message ?: "Failed to update alias")
                                    }
                                }
                            },
                            onEdit = { aliasToEdit = alias },
                            onDelete = { aliasToDelete = alias }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showAddDialog || aliasToEdit != null) {
        val editing = aliasToEdit
        AliasEditorDialog(
            existing = editing,
            onDismiss = {
                showAddDialog = false
                aliasToEdit = null
            },
            onSave = { aliasStr, canonicalStr ->
                scope.launch {
                    val result = if (editing != null) {
                        aManager.updateAlias(
                            id = editing.id,
                            alias = aliasStr,
                            canonicalMerchant = canonicalStr,
                            isEnabled = editing.isEnabled
                        )
                    } else {
                        aManager.createAlias(
                            alias = aliasStr,
                            canonicalMerchant = canonicalStr
                        )
                    }

                    if (result.isSuccess) {
                        showAddDialog = false
                        aliasToEdit = null
                        snackbarHostState.showSnackbar(if (editing != null) "Alias updated" else "Alias created")
                    } else {
                        snackbarHostState.showSnackbar(result.exceptionOrNull()?.message ?: "Operation failed")
                    }
                }
            }
        )
    }

    // Delete Confirmation Dialog
    aliasToDelete?.let { alias ->
        AlertDialog(
            onDismissRequest = { aliasToDelete = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            title = {
                Text(
                    text = "Delete Alias",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = slateTitle
                )
            },
            text = {
                Text(
                    text = "Delete alias \"${alias.alias}\" → \"${alias.canonicalMerchant}\"? Existing transactions will remain unchanged.",
                    fontSize = 13.5.sp,
                    color = slateSub,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = alias.id
                        aliasToDelete = null
                        scope.launch {
                            val res = aManager.deleteAlias(id)
                            if (res.isSuccess) {
                                snackbarHostState.showSnackbar("Alias deleted")
                            } else {
                                snackbarHostState.showSnackbar(res.exceptionOrNull()?.message ?: "Failed to delete")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = CircleShape
                ) {
                    Text("Delete Alias", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { aliasToDelete = null }) {
                    Text("Cancel", color = slateSub, fontSize = 13.sp)
                }
            }
        )
    }
}

@Composable
fun AliasCard(
    alias: MerchantAlias,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFECFDF5))
                    .border(1.dp, Color(0xFFA7F3D0), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Storefront,
                    contentDescription = null,
                    tint = emeraldPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alias.alias,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    color = slateTitle
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Resolves to: ", fontSize = 11.5.sp, color = slateSub)
                    Text(
                        text = alias.canonicalMerchant,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = emeraldPrimary
                    )
                }
            }

            // Toggle Switch
            Switch(
                checked = alias.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = emeraldPrimary,
                    uncheckedThumbColor = Color(0xFF94A3B8),
                    uncheckedTrackColor = Color(0xFFE2E8F0)
                )
            )

            // Edit Button
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Edit",
                    tint = slateSub,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Delete Button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete",
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun AliasEditorDialog(
    existing: MerchantAlias?,
    onDismiss: () -> Unit,
    onSave: (alias: String, canonical: String) -> Unit
) {
    var aliasText by remember { mutableStateOf(existing?.alias ?: "") }
    var canonicalText by remember { mutableStateOf(existing?.canonicalMerchant ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White,
        title = {
            Text(
                text = if (existing != null) "Edit Merchant Alias" else "New Merchant Alias",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = slateTitle
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = aliasText,
                    onValueChange = {
                        aliasText = it
                        errorMessage = null
                    },
                    label = { Text("Alias / Raw Variation *") },
                    placeholder = { Text("e.g. AMZN Mktp, SWIGGY BLR") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = emeraldPrimary,
                        unfocusedBorderColor = cardBorder,
                        focusedLabelColor = emeraldPrimary
                    )
                )

                OutlinedTextField(
                    value = canonicalText,
                    onValueChange = {
                        canonicalText = it
                        errorMessage = null
                    },
                    label = { Text("Canonical Merchant Name *") },
                    placeholder = { Text("e.g. Amazon, Swiggy") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = emeraldPrimary,
                        unfocusedBorderColor = cardBorder,
                        focusedLabelColor = emeraldPrimary
                    )
                )

                if (aliasText.isNotBlank() && canonicalText.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFECFDF5),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                    ) {
                        Text(
                            text = "Preview: \"${aliasText.trim()}\" → \"${canonicalText.trim()}\"",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = emeraldPrimary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                errorMessage?.let { err ->
                    Text(
                        text = "⚠ $err",
                        fontSize = 12.sp,
                        color = Color(0xFFDC2626)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val a = aliasText.trim()
                    val c = canonicalText.trim()
                    if (a.isBlank()) {
                        errorMessage = "Alias cannot be blank"
                        return@Button
                    }
                    if (c.isBlank()) {
                        errorMessage = "Canonical merchant name cannot be blank"
                        return@Button
                    }
                    val normA = MerchantNormalizer.normalize(a, stripPrefixes = true)
                    val normC = MerchantNormalizer.normalize(c, stripPrefixes = true)
                    if (normA == normC) {
                        errorMessage = "An alias cannot point to itself"
                        return@Button
                    }
                    onSave(a, c)
                },
                colors = ButtonDefaults.buttonColors(containerColor = emeraldPrimary),
                shape = CircleShape
            ) {
                Text("Save Alias", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = slateSub, fontSize = 13.sp)
            }
        }
    )
}
