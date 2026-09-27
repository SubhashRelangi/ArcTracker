package com.example.arctracker.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.arctracker.service.*
import kotlinx.coroutines.launch

enum class InitialImportDialogStep {
    PROMPT,
    SCANNING,
    IMPORTING,
    COMPLETE,
    PERMISSION_DENIED,
    CANCELLED
}

/**
 * Onboarding dialog for first-time installation (Milestone 5).
 * Requests READ_SMS permission and performs automatic previous 3-month scan and import.
 */
@Composable
fun InitialSmsImportDialog(
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    manager: HistoricalSmsImportManager? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val importManager = manager ?: remember { HistoricalSmsImportManager.create(context) }

    var currentStep by remember { mutableStateOf(InitialImportDialogStep.PROMPT) }
    var scanProgress by remember { mutableStateOf(SmsScanProgress(0)) }
    var scanResult by remember { mutableStateOf<SmsScanResult.Success?>(null) }
    var importProgress by remember { mutableStateOf(SmsImportProgress(0, 0)) }
    var importResult by remember { mutableStateOf<SmsImportResult.Success?>(null) }
    var isCancelled by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            currentStep = InitialImportDialogStep.SCANNING
            scope.launch {
                val threeMonthsAgo = SmsPermissionHelper.calculateThreeMonthsAgoTimestamp()
                val now = System.currentTimeMillis()

                val result = importManager.scan(threeMonthsAgo, now) { progress ->
                    scanProgress = progress
                    !isCancelled
                }

                if (isCancelled || result is SmsScanResult.Cancelled) {
                    currentStep = InitialImportDialogStep.CANCELLED
                } else if (result is SmsScanResult.Success) {
                    scanResult = result
                    if (result.scannedItems.isEmpty()) {
                        // No transactions to import; finish onboarding
                        SmsPermissionHelper.setInitialImportCompleted(context, true)
                        currentStep = InitialImportDialogStep.COMPLETE
                    } else {
                        // Automatically proceed to import for frictionless onboarding
                        currentStep = InitialImportDialogStep.IMPORTING
                        val impRes = importManager.importTransactions(result) { impProg ->
                            importProgress = impProg
                            !isCancelled
                        }
                        if (isCancelled || impRes is SmsImportResult.Cancelled) {
                            currentStep = InitialImportDialogStep.CANCELLED
                        } else if (impRes is SmsImportResult.Success) {
                            importResult = impRes
                            SmsPermissionHelper.setInitialImportCompleted(context, true)
                            currentStep = InitialImportDialogStep.COMPLETE
                        } else {
                            currentStep = InitialImportDialogStep.COMPLETE
                        }
                    }
                } else {
                    currentStep = InitialImportDialogStep.CANCELLED
                }
            }
        } else {
            currentStep = InitialImportDialogStep.PERMISSION_DENIED
        }
    }

    Dialog(
        onDismissRequest = {
            if (currentStep == InitialImportDialogStep.PROMPT || currentStep == InitialImportDialogStep.COMPLETE || currentStep == InitialImportDialogStep.PERMISSION_DENIED || currentStep == InitialImportDialogStep.CANCELLED) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = (currentStep != InitialImportDialogStep.SCANNING && currentStep != InitialImportDialogStep.IMPORTING),
            dismissOnClickOutside = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (currentStep) {
                    InitialImportDialogStep.PROMPT -> {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color(0xFFEDE7F6), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sms,
                                contentDescription = "SMS Setup",
                                tint = Color(0xFF673AB7),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Import Past Transactions",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1E1E),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "ArcTracker can automatically scan the last 3 months of bank and UPI SMS messages to build your expense history.\n\nAll messages are processed 100% locally and offline on your device.",
                            fontSize = 13.sp,
                            color = Color(0xFF757575),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (SmsPermissionHelper.isSmsPermissionGranted(context)) {
                                    currentStep = InitialImportDialogStep.SCANNING
                                    scope.launch {
                                        val threeMonthsAgo = SmsPermissionHelper.calculateThreeMonthsAgoTimestamp()
                                        val now = System.currentTimeMillis()

                                        val result = importManager.scan(threeMonthsAgo, now) { progress ->
                                            scanProgress = progress
                                            !isCancelled
                                        }

                                        if (result is SmsScanResult.Success) {
                                            scanResult = result
                                            if (result.scannedItems.isEmpty()) {
                                                SmsPermissionHelper.setInitialImportCompleted(context, true)
                                                currentStep = InitialImportDialogStep.COMPLETE
                                            } else {
                                                currentStep = InitialImportDialogStep.IMPORTING
                                                val impRes = importManager.importTransactions(result) { impProg ->
                                                    importProgress = impProg
                                                    !isCancelled
                                                }
                                                if (impRes is SmsImportResult.Success) {
                                                    importResult = impRes
                                                    SmsPermissionHelper.setInitialImportCompleted(context, true)
                                                    currentStep = InitialImportDialogStep.COMPLETE
                                                } else {
                                                    currentStep = InitialImportDialogStep.COMPLETE
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    permissionLauncher.launch(Manifest.permission.READ_SMS)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF673AB7),
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = "Scan Previous 3 Months",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Not Now",
                                color = Color(0xFF757575),
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }

                    InitialImportDialogStep.SCANNING -> {
                        CircularProgressIndicator(
                            color = Color(0xFF673AB7),
                            modifier = Modifier.size(48.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Scanning previous 3 months...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1E1E)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${scanProgress.recordsProcessed} messages scanned\n${scanProgress.financialDetected} transactions found",
                            fontSize = 13.sp,
                            color = Color(0xFF757575),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        OutlinedButton(
                            onClick = { isCancelled = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel")
                        }
                    }

                    InitialImportDialogStep.IMPORTING -> {
                        CircularProgressIndicator(
                            color = Color(0xFF673AB7),
                            modifier = Modifier.size(48.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Importing transactions...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1E1E)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${importProgress.itemsProcessed} / ${importProgress.totalToImport} processed",
                            fontSize = 13.sp,
                            color = Color(0xFF757575),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        OutlinedButton(
                            onClick = { isCancelled = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel")
                        }
                    }

                    InitialImportDialogStep.COMPLETE -> {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Complete",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(56.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Setup Complete!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1E1E)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val count = importResult?.insertedCount ?: 0
                        val msg = if (count > 0) {
                            "$count transactions imported from the last 3 months."
                        } else {
                            "No past transaction messages found in the last 3 months."
                        }

                        Text(
                            text = msg,
                            fontSize = 13.sp,
                            color = Color(0xFF757575),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onComplete,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
                        ) {
                            Text("Done", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    InitialImportDialogStep.PERMISSION_DENIED -> {
                        Text(
                            text = "SMS Permission Not Granted",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1E1E)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "ArcTracker could not access your SMS messages. You can import past transactions anytime from Settings -> Data & Storage.",
                            fontSize = 13.sp,
                            color = Color(0xFF757575),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
                        ) {
                            Text("OK")
                        }
                    }

                    InitialImportDialogStep.CANCELLED -> {
                        Text(
                            text = "Import Cancelled",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E1E1E)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Initial import was cancelled. You can scan and import previous transactions anytime from Settings -> Data & Storage.",
                            fontSize = 13.sp,
                            color = Color(0xFF757575),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
                        ) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}
