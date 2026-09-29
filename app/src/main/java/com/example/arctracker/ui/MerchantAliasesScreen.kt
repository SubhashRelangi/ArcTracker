package com.example.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.arctracker.data.MerchantAlias
import com.example.arctracker.service.MerchantAliasManager
import com.example.arctracker.service.MerchantNormalizer
import kotlinx.coroutines.launch

private val primaryColor = Color(0xFF6750A4)
private val cardBorderColor = Color(0xFFE0E0E0)
private val textSubColor = Color(0xFF757575)

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Merchant Aliases",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Normalize merchant variations to canonical names",
                            style = MaterialTheme.typography.bodySmall,
                            color = textSubColor
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = primaryColor,
                contentColor = Color.White
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Alias")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tester Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, cardBorderColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Alias Tester & Resolver", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = testMerchantInput,
                            onValueChange = {
                                testMerchantInput = it
                                resolvedPreview = null
                            },
                            placeholder = { Text("e.g. AMZN Mktp...", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (testMerchantInput.isNotBlank()) {
                                    resolvedPreview = aManager.resolveAlias(testMerchantInput, activeAliases)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Text("Resolve", fontSize = 12.sp)
                        }
                    }

                    resolvedPreview?.let { (canonical, matched) ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = if (matched != null) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (matched != null) Icons.Filled.CheckCircle else Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = if (matched != null) Color(0xFF2E7D32) else Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (matched != null) {
                                        "Resolved: \"$testMerchantInput\" → \"$canonical\""
                                    } else {
                                        "No alias matched. Will use: \"$testMerchantInput\""
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
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
                        Icon(
                            imageVector = Icons.Filled.Storefront,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No merchant aliases created", fontWeight = FontWeight.Medium, color = textSubColor)
                        Text(
                            text = "Tap + to normalize merchant variations (e.g. 'AMZN' → 'Amazon')",
                            fontSize = 12.sp,
                            color = textSubColor
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
            title = { Text("Delete Alias", fontWeight = FontWeight.Bold) },
            text = {
                Text("Delete alias \"${alias.alias}\" → \"${alias.canonicalMerchant}\"? Existing transactions will remain unchanged.")
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { aliasToDelete = null }) {
                    Text("Cancel")
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, cardBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(primaryColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Storefront,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alias.alias,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Resolves to: ", fontSize = 11.sp, color = textSubColor)
                    Text(
                        text = alias.canonicalMerchant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor
                    )
                }
            }

            Switch(
                checked = alias.isEnabled,
                onCheckedChange = onToggle
            )

            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFD32F2F), modifier = Modifier.size(20.dp))
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
        title = {
            Text(if (existing != null) "Edit Merchant Alias" else "Add Merchant Alias", fontWeight = FontWeight.Bold)
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
                    placeholder = { Text("e.g. AMZN Mktp") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = canonicalText,
                    onValueChange = {
                        canonicalText = it
                        errorMessage = null
                    },
                    label = { Text("Canonical Merchant Name *") },
                    placeholder = { Text("e.g. Amazon") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (aliasText.isNotBlank() && canonicalText.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEDE7F6)
                    ) {
                        Text(
                            text = "Preview: \"${aliasText.trim()}\" → \"${canonicalText.trim()}\"",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryColor,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                errorMessage?.let { err ->
                    Text(
                        text = "⚠ $err",
                        fontSize = 12.sp,
                        color = Color(0xFFC62828)
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
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
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
