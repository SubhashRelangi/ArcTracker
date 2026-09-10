package com.example.arctracker.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.arctracker.utils.SmsImporter

@Composable
fun FirstTimeSetupDialogs(onComplete: () -> Unit) {
    val context = LocalContext.current
    
    fun isNotificationServiceEnabled(): Boolean {
        val pkgName = context.packageName
        val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(pkgName)
    }

    var hasNotifPermission by remember { mutableStateOf(isNotificationServiceEnabled()) }
    var hasSmsPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
    }

    // 1 = Notif, 2 = SMS, 3 = Import, 4 = Summary
    var currentDialog by remember { mutableStateOf(if (!hasNotifPermission) 1 else if (!hasSmsPermission) 2 else 3) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val newlyEnabled = isNotificationServiceEnabled()
                if (newlyEnabled && !hasNotifPermission && currentDialog == 1) {
                    hasNotifPermission = true
                    currentDialog = if (!hasSmsPermission) 2 else 3
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var importProgress by remember { mutableStateOf(0) }
    var totalMessages by remember { mutableStateOf(0) }
    var importResult by remember { mutableStateOf<SmsImporter.ImportResult?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasSmsPermission = isGranted
        if (isGranted) {
            currentDialog = 3
        } else {
            onComplete()
        }
    }

    if (currentDialog == 1) {
        AlertDialog(
            onDismissRequest = { currentDialog = 2 },
            title = { Text("Automate Expense Tracking", fontWeight = FontWeight.Bold) },
            text = { Text("ArcTracker can automatically log your expenses by reading payment notifications. Would you like to enable Notification Access?\n\nYou can always do this later in Settings.") },
            confirmButton = {
                TextButton(onClick = {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    context.startActivity(intent)
                }) {
                    Text("Enable Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { currentDialog = 2 }) {
                    Text("Not Now", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    } else if (currentDialog == 2) {
        AlertDialog(
            onDismissRequest = { onComplete() },
            title = { Text("Recover Previous Transactions", fontWeight = FontWeight.Bold) },
            text = { Text("ArcTracker can read transaction SMS messages already stored on your phone to recover your past expenses.\n\nWould you like to grant SMS access to import them?") },
            confirmButton = {
                TextButton(onClick = {
                    smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                }) {
                    Text("Allow", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { onComplete() }) {
                    Text("Not Now", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    } else if (currentDialog == 3) {
        LaunchedEffect(Unit) {
            if (!isImporting) {
                isImporting = true
                val timeToSubtract = 30L * 24 * 60 * 60 * 1000L
                val startTime = System.currentTimeMillis() - timeToSubtract
                val result = SmsImporter.importSms(context, startTime) { processed, total ->
                    importProgress = processed
                    totalMessages = total
                }
                importResult = result
                isImporting = false
                currentDialog = 4
            }
        }

        AlertDialog(
            onDismissRequest = { /* Don't dismiss while importing */ },
            title = { Text("Importing Transactions", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Scanning messages...")
                    if (totalMessages > 0) {
                        Text("$importProgress / $totalMessages")
                    }
                }
            },
            confirmButton = {}
        )
    } else if (currentDialog == 4) {
        val result = importResult
        if (result != null) {
            AlertDialog(
                onDismissRequest = { onComplete() },
                title = { Text("Setup Complete", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("${result.imported} previous transactions imported.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${result.duplicatesSkipped} existing transactions skipped.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Your future transactions will now be tracked automatically.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onComplete() }) {
                        Text("Continue", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}
