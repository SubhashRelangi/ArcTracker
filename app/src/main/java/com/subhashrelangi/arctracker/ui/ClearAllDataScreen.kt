package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.settings.MonitoringSettingsRepository
import com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ClearAllDataScreen(
    onNavigate: (String) -> Unit,
    onNavigateBack: () -> Unit = {},
    onClearData: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var deleteInput by remember { mutableStateOf("") }

    // Live datastore metrics
    var txCount by remember { mutableStateOf<Int?>(null) }
    var accountCount by remember { mutableStateOf<Int?>(null) }
    var ruleCount by remember { mutableStateOf<Int?>(null) }
    var aliasCount by remember { mutableStateOf<Int?>(null) }
    var budgetCount by remember { mutableStateOf<Int?>(null) }
    var cacheSizeMb by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val txs = db.expenseDao().getCount()
                val accs = db.knownFinancialAccountDao().getCount()
                val rules = db.userCategoryRuleDao().getAll().size
                val aliases = db.merchantAliasDao().getAll().size
                val budgets = db.budgetDao().getCount()
                val dbFile = context.getDatabasePath("arctracker_database")
                val size = if (dbFile.exists()) (dbFile.length().toDouble() / (1024 * 1024)) else 0.0

                withContext(Dispatchers.Main) {
                    txCount = txs
                    accountCount = accs
                    ruleCount = rules
                    aliasCount = aliases
                    budgetCount = budgets
                    cacheSizeMb = size
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    txCount = 0
                    accountCount = 0
                    ruleCount = 0
                    aliasCount = 0
                    budgetCount = 0
                    cacheSizeMb = 0.0
                }
            }
        }
    }

    val isDeleteConfirmed = deleteInput.trim() == "DELETE"

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = Color.White,
                shadowElevation = 0.5.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                ArcTrackerHeader(
                    title = "Clear All Data",
                    subtitle = "Permanent Vault Wipe",
                    onBackClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .statusBarsPadding(),
                    horizontalPadding = 16.dp,
                    actions = {
                        // Circular Shield Status Indicator
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = "Secured Vault",
                                tint = Color(0xFFDC2626),
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
                .background(Color(0xFFF8F9FA))
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Danger Zone Banner Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFEE2E2), RoundedCornerShape(16.dp))
                    .border(BorderStroke(1.dp, Color(0xFFFECACA)), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Danger Zone: Permanent Ledger Wipe",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB91C1C)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "This action irreversibly erases stored financial data from your local hardware. Because ArcTracker operates 100% offline with zero cloud backups, deleted data cannot be recovered.",
                        fontSize = 12.sp,
                        color = Color(0xFF991B1B),
                        lineHeight = 17.sp
                    )
                }
            }

            // 2. Pre-wipe Safety Export Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFDCFCE7), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Pre-wipe Safety Export",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Export encrypted backup (",
                                    fontSize = 11.sp,
                                    color = Color(0xFF6B7280)
                                )
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFF3F4F6), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = ".arcvault",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF374151)
                                    )
                                }
                                Text(
                                    text = ") first.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF6B7280)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { onNavigate("Backup") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF3F4F6),
                            contentColor = Color(0xFF1E293B)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Create Backup First",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 3. Purge Inventory Bullet Points Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFFDC2626), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PURGE INVENTORY (IRREVERSIBLE)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF374151),
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFEE2E2), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "5 Target Datastores",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626)
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        val formattedEntries = if (txCount != null && txCount!! > 0) {
                            NumberFormat.getNumberInstance(Locale.US).format(txCount) + " entries"
                        } else if (txCount != null) {
                            "0 entries"
                        } else {
                            "1,432 entries"
                        }
                        DatastoreBulletPoint(
                            bulletColor = Color(0xFFDC2626),
                            title = "Transaction Records",
                            subtitle = "Expenses, credits & transfers",
                            value = formattedEntries,
                            valueColor = Color(0xFFDC2626)
                        )

                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                        val formattedAccounts = if (accountCount != null && accountCount!! > 0) {
                            "${accountCount} accounts"
                        } else if (accountCount != null) {
                            "0 accounts"
                        } else {
                            "3 accounts"
                        }
                        DatastoreBulletPoint(
                            bulletColor = Color(0xFFDC2626),
                            title = "Discovered Accounts",
                            subtitle = "Masked ledger references",
                            value = formattedAccounts,
                            valueColor = Color(0xFFDC2626)
                        )

                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                        val formattedRules = if (ruleCount != null && aliasCount != null) {
                            "${ruleCount} rules, ${aliasCount} aliases"
                        } else {
                            "14 rules, 28 aliases"
                        }
                        DatastoreBulletPoint(
                            bulletColor = Color(0xFFDC2626),
                            title = "Category Rules & Aliases",
                            subtitle = "Trained parsing regex heuristics",
                            value = formattedRules,
                            valueColor = Color(0xFFDC2626)
                        )

                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                        val formattedBudgets = if (budgetCount != null && budgetCount!! > 0) {
                            "${budgetCount} active limits"
                        } else if (budgetCount != null) {
                            "0 active limits"
                        } else {
                            "4 active limits"
                        }
                        DatastoreBulletPoint(
                            bulletColor = Color(0xFFDC2626),
                            title = "Category Budgets",
                            subtitle = "Monthly ceiling triggers",
                            value = formattedBudgets,
                            valueColor = Color(0xFFDC2626)
                        )

                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                        val formattedCache = if (cacheSizeMb != null && cacheSizeMb!! > 0.0) {
                            String.format(Locale.US, "%.1f MB cache", cacheSizeMb)
                        } else if (cacheSizeMb != null) {
                            "0.0 MB cache"
                        } else {
                            "18.4 MB cache"
                        }
                        DatastoreBulletPoint(
                            bulletColor = Color(0xFFDC2626),
                            title = "SQLite Cache & Intake",
                            subtitle = "Transient SMS / notification intake",
                            value = formattedCache,
                            valueColor = Color(0xFFDC2626)
                        )
                    }
                }
            }

            // 4. Retained Hardware Preferences Bullet Points Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF16A34A), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RETAINED HARDWARE PREFERENCES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF374151),
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Box(
                        modifier = Modifier
                            .background(Color(0xFFDCFCE7), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Zero Overhead Reconfiguration",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A34A)
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        DatastoreBulletPoint(
                            bulletColor = Color(0xFF16A34A),
                            title = "Monitored App Permissions",
                            subtitle = "Package access tokens & listen hooks",
                            value = "Preserved",
                            valueColor = Color(0xFF16A34A)
                        )

                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)

                        DatastoreBulletPoint(
                            bulletColor = Color(0xFF16A34A),
                            title = "Hardware Screen Security",
                            subtitle = "FLAG_SECURE, biometric lock, masking",
                            value = "Preserved",
                            valueColor = Color(0xFF16A34A)
                        )
                    }
                }
            }

            // 5. Confirmation & Circle Format Buttons
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = buildAnnotatedString {
                            append("Type ")
                            withStyle(SpanStyle(color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)) {
                                append("DELETE")
                            }
                            append(" to confirm permanent wipe")
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF111827)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "This confirmation requirement prevents accidental data purging.",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Input Box with Auto-scroll on Focus
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, Color(0xFFE5E7EB)), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        if (deleteInput.isEmpty()) {
                            Text(
                                text = "Type DELETE here...",
                                fontSize = 14.sp,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                        BasicTextField(
                            value = deleteInput,
                            onValueChange = {
                                deleteInput = it
                                scope.launch {
                                    scrollState.animateScrollTo(scrollState.maxValue)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        scope.launch {
                                            delay(250)
                                            scrollState.animateScrollTo(scrollState.maxValue)
                                        }
                                    }
                                },
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF111827)
                            ),
                            cursorBrush = SolidColor(Color(0xFFDC2626)),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Clear All Data Permanently Button (Circle / Stadium Pill Format)
                    Button(
                        onClick = {
                            if (isDeleteConfirmed) {
                                val repo = MonitoringSettingsRepository.getInstance(context)
                                val wasCompleted = repo.isInitialSmsImportCompleted()
                                onClearData()
                                repo.setInitialSmsImportCompleted(wasCompleted)
                            }
                        },
                        enabled = isDeleteConfirmed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFFE89E9E),
                            disabledContentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DisabledByDefault,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Clear All Data Permanently",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Cancel & Return Button (Circle / Stadium Pill Format)
                    Button(
                        onClick = { onNavigateBack() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF3F4F6),
                            contentColor = Color(0xFF1F2937)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = "Cancel & Return to Settings",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 6. Security Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VpnKey,
                        contentDescription = null,
                        tint = Color(0xFF0D9488),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "ARCTRACKER ZERO-TRACE ARCHITECTURE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569),
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Cryptographic AES-GCM keys are purged from the Android Keystore on execution. No forensic residual data persists on device flash storage.",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun DatastoreBulletPoint(
    bulletColor: Color,
    title: String,
    subtitle: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(bulletColor, CircleShape)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827)
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color(0xFF6B7280)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
