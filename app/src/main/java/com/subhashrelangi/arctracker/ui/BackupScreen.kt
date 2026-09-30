package com.subhashrelangi.arctracker.ui

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.backup.*
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.security.BackupEncryptionManager
import com.subhashrelangi.arctracker.security.PrivacySettingsManager
import com.subhashrelangi.arctracker.security.SensitiveDataSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    val privacyManager = remember { PrivacySettingsManager.getInstance(context) }
    val backupManager = remember {
        BackupManager(
            database = database,
            expenseDao = database.expenseDao(),
            categoryDao = database.transactionCategoryDao(),
            accountDao = database.knownFinancialAccountDao(),
            ruleDao = database.userCategoryRuleDao(),
            aliasDao = database.merchantAliasDao(),
            budgetDao = database.budgetDao()
        )
    }

    // Apply FLAG_SECURE on sensitive screen
    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        onDispose {
            if (!privacyManager.isScreenSecurityEnabled()) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    var selectedImportMode by remember { mutableStateOf(ImportMode.MERGE) }
    var pendingImportPayload by remember { mutableStateOf<ArcTrackerBackupPayload?>(null) }
    var pendingImportPreview by remember { mutableStateOf<ImportPreview?>(null) }
    var errorDialogMessage by remember { mutableStateOf<String?>(null) }
    var successDialogMessage by remember { mutableStateOf<String?>(null) }

    // Encryption States
    var showExportOptionsDialog by remember { mutableStateOf(false) }
    var exportPassphrase by remember { mutableStateOf("") }
    var pendingEncryptedContent by remember { mutableStateOf<String?>(null) }
    var showImportPassphraseDialog by remember { mutableStateOf(false) }
    var importPassphrase by remember { mutableStateOf("") }

    // SAF Launchers
    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isLoading = true
                loadingMessage = "Exporting transactions to CSV..."
                val result = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            backupManager.exportTransactionsCsv(stream)
                        } ?: Result.failure(IllegalStateException("Could not open destination file"))
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
                isLoading = false
                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    successDialogMessage = "Successfully exported $count transactions to CSV."
                } else {
                    errorDialogMessage = SensitiveDataSanitizer.sanitizeErrorMessage(result.exceptionOrNull())
                }
            }
        }
    }

    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isLoading = true
                loadingMessage = "Creating full application backup..."
                val currentPassphrase = exportPassphrase.trim()
                val result = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            if (currentPassphrase.isNotBlank()) {
                                backupManager.exportEncryptedFullBackup(stream, currentPassphrase.toCharArray())
                            } else {
                                backupManager.exportFullBackup(stream)
                            }
                        } ?: Result.failure(IllegalStateException("Could not open destination file"))
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
                exportPassphrase = ""
                isLoading = false
                if (result.isSuccess) {
                    val metadata = result.getOrNull()
                    val count = metadata?.entityCounts?.transactions ?: 0
                    val encNote = if (currentPassphrase.isNotBlank()) " (Encrypted with AES-256-GCM)" else ""
                    successDialogMessage = "Full backup completed successfully$encNote.\nIncludes $count transactions, categories, accounts, rules, and budgets."
                } else {
                    errorDialogMessage = SensitiveDataSanitizer.sanitizeErrorMessage(result.exceptionOrNull())
                }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isLoading = true
                loadingMessage = "Reading backup file..."
                val readResult = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.bufferedReader().use { it.readText() }
                        } ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                }
                isLoading = false

                if (readResult.isBlank()) {
                    errorDialogMessage = "Unable to read selected file or file is empty."
                    return@launch
                }

                if (BackupEncryptionManager.isEncryptedBackup(readResult)) {
                    pendingEncryptedContent = readResult
                    importPassphrase = ""
                    showImportPassphraseDialog = true
                } else {
                    // Unencrypted M15 backup
                    isLoading = true
                    loadingMessage = "Validating backup..."
                    val previewResult = withContext(Dispatchers.IO) {
                        try {
                            val payload = BackupSerializer.fromJsonString(readResult)
                            backupManager.generateImportPreview(
                                ByteArrayInputStream(readResult.toByteArray()),
                                selectedImportMode
                            ).map { preview -> Pair(payload, preview) }
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    }
                    isLoading = false
                    if (previewResult.isSuccess) {
                        val (payload, preview) = previewResult.getOrThrow()
                        pendingImportPayload = payload
                        pendingImportPreview = preview
                    } else {
                        errorDialogMessage = SensitiveDataSanitizer.sanitizeErrorMessage(previewResult.exceptionOrNull())
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export & Backup", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // EXPORT SECTION
                Text(
                    text = "Export Data",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                ExportActionCard(
                    icon = Icons.Filled.TableChart,
                    title = "Export Transactions (CSV)",
                    description = "Portable spreadsheet format containing your categorized expenses and dates. Compatible with Excel and Google Sheets.",
                    buttonText = "Export CSV",
                    onClick = {
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        csvExportLauncher.launch("ArcTracker_Transactions_$timestamp.csv")
                    }
                )

                ExportActionCard(
                    icon = Icons.Filled.Backup,
                    title = "Full Application Backup",
                    description = "Complete snapshot of all your transactions, custom categories, accounts, rules, and budgets. Supports optional AES-256-GCM encryption.",
                    buttonText = "Create Backup",
                    onClick = {
                        exportPassphrase = ""
                        showExportOptionsDialog = true
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                // IMPORT SECTION
                Text(
                    text = "Import & Restore",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Import Mode",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Merge Mode Option
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedImportMode = ImportMode.MERGE }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedImportMode == ImportMode.MERGE,
                                onClick = { selectedImportMode = ImportMode.MERGE }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Merge Import (Recommended)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Adds new records and strictly protects existing user-assigned categories and manual notes.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Full Restore Option
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedImportMode = ImportMode.FULL_RESTORE }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedImportMode == ImportMode.FULL_RESTORE,
                                onClick = { selectedImportMode = ImportMode.FULL_RESTORE }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Full Restore (Replace)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Replaces the current database with the backup dataset.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                filePickerLauncher.launch(arrayOf("application/json", "*/*"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Backup File")
                        }
                    }
                }

                // PRIVACY & SECURITY SECTION
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Security & Privacy Hardening",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Backups strictly exclude raw SMS texts, passwords, OTPs, and unmasked account numbers. Screen contents are protected from background capture, and imports are fully atomic with rollback on error.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(loadingMessage, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    // Export Options / Passphrase Dialog
    if (showExportOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showExportOptionsDialog = false },
            title = { Text("Backup Protection", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Optionally enter a passphrase to encrypt your backup with AES-256-GCM. Leave blank for standard unencrypted backup.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = exportPassphrase,
                        onValueChange = { exportPassphrase = it },
                        label = { Text("Passphrase (Optional)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportOptionsDialog = false
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        backupExportLauncher.launch("ArcTracker_Backup_$timestamp.json")
                    }
                ) {
                    Text("Proceed")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportOptionsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Import Passphrase Dialog for Encrypted Backup
    if (showImportPassphraseDialog) {
        val encryptedContent = pendingEncryptedContent
        AlertDialog(
            onDismissRequest = {
                showImportPassphraseDialog = false
                pendingEncryptedContent = null
            },
            title = { Text("Encrypted Backup Detected", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This backup is protected with AES-256-GCM encryption. Please enter the passphrase to decrypt and preview its contents.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = importPassphrase,
                        onValueChange = { importPassphrase = it },
                        label = { Text("Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (encryptedContent != null && importPassphrase.isNotBlank()) {
                            showImportPassphraseDialog = false
                            scope.launch {
                                isLoading = true
                                loadingMessage = "Decrypting and validating backup..."
                                val decryptResult = withContext(Dispatchers.IO) {
                                    try {
                                        val decryptedJson = BackupEncryptionManager.decrypt(
                                            encryptedContent,
                                            importPassphrase.toCharArray()
                                        )
                                        val payload = BackupSerializer.fromJsonString(decryptedJson)
                                        backupManager.generateImportPreview(
                                            ByteArrayInputStream(decryptedJson.toByteArray()),
                                            selectedImportMode
                                        ).map { preview -> Pair(payload, preview) }
                                    } catch (e: Exception) {
                                        Result.failure(e)
                                    }
                                }
                                importPassphrase = ""
                                pendingEncryptedContent = null
                                isLoading = false

                                if (decryptResult.isSuccess) {
                                    val (payload, preview) = decryptResult.getOrThrow()
                                    pendingImportPayload = payload
                                    pendingImportPreview = preview
                                } else {
                                    errorDialogMessage = SensitiveDataSanitizer.sanitizeErrorMessage(decryptResult.exceptionOrNull())
                                }
                            }
                        }
                    },
                    enabled = importPassphrase.isNotBlank()
                ) {
                    Text("Decrypt")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportPassphraseDialog = false
                        pendingEncryptedContent = null
                        importPassphrase = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Import Preview Dialog
    val preview = pendingImportPreview
    val payload = pendingImportPayload
    if (preview != null && payload != null) {
        ImportPreviewDialog(
            preview = preview,
            onConfirm = {
                val mode = preview.mode
                pendingImportPreview = null
                pendingImportPayload = null
                scope.launch {
                    isLoading = true
                    loadingMessage = if (mode == ImportMode.FULL_RESTORE) "Restoring database..." else "Merging backup data..."
                    val result = backupManager.performImport(payload, mode)
                    isLoading = false
                    if (result.isSuccess) {
                        val summary = result.getOrThrow()
                        successDialogMessage = if (mode == ImportMode.FULL_RESTORE) {
                            "Restore completed successfully!\nRestored ${summary.importedTransactions} transactions, ${summary.importedCategories} categories, and ${summary.importedBudgets} budgets."
                        } else {
                            "Merge completed successfully!\nImported ${summary.importedTransactions} new transactions (${summary.preservedTransactions} existing protected)."
                        }
                    } else {
                        errorDialogMessage = SensitiveDataSanitizer.sanitizeErrorMessage(result.exceptionOrNull())
                    }
                }
            },
            onDismiss = {
                pendingImportPreview = null
                pendingImportPayload = null
            }
        )
    }

    // Success Dialog
    if (successDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { successDialogMessage = null },
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32)) },
            title = { Text("Success") },
            text = { Text(successDialogMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { successDialogMessage = null }) {
                    Text("OK")
                }
            }
        )
    }

    // Error Dialog
    if (errorDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { errorDialogMessage = null },
            icon = { Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Operation Failed") },
            text = { Text(errorDialogMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { errorDialogMessage = null }) {
                    Text("Dismiss")
                }
            }
        )
    }
}

@Composable
fun ExportActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(buttonText, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ImportPreviewDialog(
    preview: ImportPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmedReplace by remember { mutableStateOf(false) }
    val isRestore = preview.mode == ImportMode.FULL_RESTORE
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(preview.metadata.exportedAt))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isRestore) "Confirm Full Restore" else "Import Preview",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Backup Date: $dateStr • v${preview.metadata.formatVersion}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                PreviewRow("Total Transactions", "${preview.totalTransactions}")
                if (!isRestore) {
                    PreviewRow("  • New to Import", "${preview.newTransactions}")
                    PreviewRow("  • Existing (Protected)", "${preview.existingTransactions}")
                }
                PreviewRow("Categories", "${preview.totalCategories}")
                PreviewRow("Financial Accounts", "${preview.totalAccounts}")
                PreviewRow("Category Rules", "${preview.totalRules}")
                PreviewRow("Merchant Aliases", "${preview.totalAliases}")
                PreviewRow("Budgets", "${preview.totalBudgets}")

                if (isRestore) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Warning: Full restore will replace your current dataset with the data from this backup. Existing records not present in the backup will be lost.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                lineHeight = 15.sp
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Checkbox(
                            checked = confirmedReplace,
                            onCheckedChange = { confirmedReplace = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("I understand and want to replace my current data", fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isRestore || confirmedReplace,
                colors = if (isRestore) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(if (isRestore) "Restore Data" else "Import Data")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
