package com.example.arctracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class MockImportResult(
    val totalFound: Int,
    val bankMessagesFound: Int,
    val imported: Int,
    val duplicatesSkipped: Int,
    val promotionalSkipped: Int,
    val nonBankSkipped: Int,
    val ignored: Int,
    val totalAmountImported: Double,
    val totalIncomeImported: Double,
    val banksDetected: List<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsImportScreen(onNavigateBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    val periods = listOf(
        "Last 30 days",
        "Last 3 months",
        "Last 6 months",
        "Last 1 year",
        "All available"
    )

    var selectedPeriodIndex by remember { mutableStateOf(0) }
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf(0) }
    val totalMessages = 184
    var importResult by remember { mutableStateOf<MockImportResult?>(null) }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (importResult != null) {
                ImportResultCard(result = importResult!!)
                Button(
                    onClick = { importResult = null },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
                ) {
                    Text("Import More")
                }
            } else if (isImporting) {
                ImportProgressCard(importProgress, totalMessages)
            } else {
                // Bank-only import info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFEDE7F6)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.AccountBalance,
                            contentDescription = null,
                            tint = Color(0xFF673AB7),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Only messages from recognized bank senders (e.g. JD-HDFCBK) are scanned. Promotional, OTP and random messages are skipped automatically.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFF311B92)
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Select Period", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E1E1E))
                        Spacer(modifier = Modifier.height(8.dp))
                        periods.forEachIndexed { index, periodLabel ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = (index == selectedPeriodIndex),
                                    onClick = { selectedPeriodIndex = index },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF673AB7))
                                )
                                Text(text = periodLabel, fontSize = 14.sp, color = Color(0xFF1E1E1E))
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        isImporting = true
                        importProgress = 0

                        scope.launch {
                            for (step in 1..totalMessages) {
                                if (step % 20 == 0 || step == totalMessages) {
                                    importProgress = step
                                    delay(60)
                                }
                            }
                            importResult = MockImportResult(
                                totalFound = 184,
                                bankMessagesFound = 46,
                                imported = 42,
                                duplicatesSkipped = 4,
                                promotionalSkipped = 112,
                                nonBankSkipped = 0,
                                ignored = 26,
                                totalAmountImported = 16840.0,
                                totalIncomeImported = 75000.0,
                                banksDetected = listOf("HDFC Bank", "State Bank of India", "ICICI Bank")
                            )
                            isImporting = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7))
                ) {
                    Text("Scan Bank Messages")
                }
            }
        }
    }
}

@Composable
fun ImportResultCard(result: MockImportResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Import Complete",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E1E1E)
            )
            Spacer(modifier = Modifier.height(4.dp))
            ResultRow(Icons.Outlined.Info, "${result.totalFound} messages scanned")
            ResultRow(Icons.Outlined.AccountBalance, "${result.bankMessagesFound} bank messages found")
            ResultRow(Icons.Filled.CheckCircle, "${result.imported} transactions imported", Color(0xFF4CAF50))
            ResultRow(Icons.Filled.ContentCopy, "${result.duplicatesSkipped} duplicates skipped")
            ResultRow(Icons.Filled.Campaign, "${result.promotionalSkipped} promotional messages skipped")
            ResultRow(Icons.Filled.RemoveCircleOutline, "${result.nonBankSkipped} non-bank senders skipped")
            ResultRow(Icons.Outlined.Info, "${result.ignored} unparsable messages ignored")

            if (result.banksDetected.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Banks detected: ${result.banksDetected.joinToString(", ")}",
                    fontSize = 13.sp,
                    color = Color(0xFF757575)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Imported spending", fontSize = 14.sp)
                Text(
                    "₹${"%.2f".format(result.totalAmountImported)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD32F2F)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Imported income", fontSize = 14.sp)
                Text(
                    "₹${"%.2f".format(result.totalIncomeImported)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
fun ImportProgressCard(processed: Int, total: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = Color(0xFF673AB7))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Scanning messages...", style = MaterialTheme.typography.titleMedium, color = Color(0xFF1E1E1E))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "$processed / ${if (total > 0) total.toString() else "?"}",
                fontSize = 14.sp,
                color = Color(0xFF757575)
            )
        }
    }
}

@Composable
fun ResultRow(icon: ImageVector, text: String, tint: Color = Color(0xFF757575)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, color = Color(0xFF1E1E1E))
    }
}
