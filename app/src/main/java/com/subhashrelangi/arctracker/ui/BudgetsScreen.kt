package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.service.AndroidBudgetAlertNotifier
import com.subhashrelangi.arctracker.service.BudgetManager
import com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    onNavigateBack: () -> Unit,
    manager: BudgetManager? = null,
    categoryManager: com.subhashrelangi.arctracker.service.CategoryManager? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val budgetManager = remember {
        manager ?: run {
            val db = AppDatabase.getDatabase(context)
            BudgetManager(
                budgetDao = db.budgetDao(),
                expenseDao = db.expenseDao(),
                categoryDao = db.transactionCategoryDao(),
                alertNotifier = AndroidBudgetAlertNotifier(context)
            )
        }
    }

    val catManager = remember {
        categoryManager ?: run {
            val db = AppDatabase.getDatabase(context)
            com.subhashrelangi.arctracker.service.CategoryManager(
                categoryDao = db.transactionCategoryDao(),
                expenseDao = db.expenseDao(),
                database = db,
                ruleDao = db.userCategoryRuleDao(),
                budgetDao = db.budgetDao()
            )
        }
    }

    val budgetsProgress by budgetManager.observeAllBudgetsProgress().collectAsState(initial = emptyList())
    val dbCategories by catManager.getActiveCategoriesFlow().collectAsState(initial = emptyList())

    // Combined category list ensuring all built-in categories are present
    val allCategories = remember(dbCategories) {
        if (dbCategories.isNotEmpty()) {
            val existingIds = dbCategories.map { it.id.lowercase() }.toSet()
            val missingBuiltins = BuiltInCategories.ALL.filter { it.id.lowercase() !in existingIds }
            dbCategories + missingBuiltins
        } else {
            BuiltInCategories.ALL
        }
    }

    // Billing cycle stats
    val calendar = remember { Calendar.getInstance() }
    val currentDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
    val totalDaysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val daysRemaining = (totalDaysInMonth - currentDayOfMonth).coerceAtLeast(0)

    // Calculate aggregated overall progress
    val overallBudgetProgress = budgetsProgress.find { it.budget.isOverallBudget }
    val hasRealBudgets = budgetsProgress.isNotEmpty()

    val capTarget = overallBudgetProgress?.limit ?: if (hasRealBudgets) budgetsProgress.sumOf { it.limit } else 50000.0
    val totalSpent = overallBudgetProgress?.spent ?: if (hasRealBudgets) budgetsProgress.sumOf { it.spent } else 36750.0
    val remaining = (capTarget - totalSpent).coerceAtLeast(0.0)
    val percentAllocated = if (capTarget > 0) ((totalSpent / capTarget) * 100.0).coerceIn(0.0, 100.0) else 0.0

    // Alert engine threshold pill selection
    var selectedAlertThreshold by remember { mutableStateOf(80) }

    // Quick creator states
    var showCategoryPickerModal by remember { mutableStateOf(false) }
    var quickSelectedCategory by remember { mutableStateOf<TransactionCategory?>(null) }
    var quickLimitText by remember { mutableStateOf("8000") }
    var quickThresholdSlider by remember { mutableStateOf(0.80f) }

    // Dialog states
    var showMonthlyCapDialog by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<Budget?>(null) }
    var deletingBudget by remember { mutableStateOf<Budget?>(null) }
    var viewingProgress by remember { mutableStateOf<BudgetProgress?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    // Set initial category for Quick Creator
    LaunchedEffect(allCategories) {
        if (quickSelectedCategory == null && allCategories.isNotEmpty()) {
            quickSelectedCategory = allCategories.find { it.name.contains("Entertainment", ignoreCase = true) }
                ?: allCategories.firstOrNull()
        }
    }

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
                    title = "Budgets & Limits",
                    subtitle = "Spending Caps & Envelopes",
                    onBackClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .statusBarsPadding(),
                    horizontalPadding = 16.dp,
                    actions = {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccountBalanceWallet,
                                contentDescription = "Budgets",
                                tint = Color(0xFF055B38),
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
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Overall Velocity / Monthly Budget Cap Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Top Row: Velocity + Days Left + Circular Add Button in Top Right Corner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = "OVERALL VELOCITY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6B7280),
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFF3F4F6), CircleShape)
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "$daysRemaining days left",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF4B5563)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Circular + Button in Top Right Corner to Set Monthly Budget Cap
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF055B38))
                                .clickable { showMonthlyCapDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Set Monthly Budget Cap",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Monthly Budget\nCap",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827),
                        lineHeight = 26.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stats Container Box
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF9FAFB)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = "Cap Target", fontSize = 11.sp, color = Color(0xFF6B7280))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "₹${formatCurrency(capTarget)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827)
                                )
                            }

                            Column {
                                Text(text = "Total Spent", fontSize = 11.sp, color = Color(0xFF6B7280))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "₹${formatCurrency(totalSpent)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827)
                                )
                            }

                            Column {
                                Text(text = "Remaining", fontSize = 11.sp, color = Color(0xFF6B7280))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "₹${formatCurrency(remaining)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF16A34A)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Allocated Spend & Billing Cycle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${String.format(Locale.US, "%.1f", percentAllocated)}% allocated",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280)
                        )
                        Text(
                            text = "Billing cycle: Day $currentDayOfMonth/$totalDaysInMonth",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE5E7EB))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((percentAllocated / 100f).toFloat().coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(Color(0xFF055B38))
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Scale markers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "0%", fontSize = 10.sp, color = Color(0xFF9CA3AF))
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "80% Alert",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD97706)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "100%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }

            // 2. Budget Alert Engine Card
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
                                .size(38.dp)
                                .background(Color(0xFFDCFCE7), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Budget Alert Engine",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            )
                            Text(
                                text = "Real-time local budget telemetry",
                                fontSize = 11.sp,
                                color = Color(0xFF6B7280)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(Color(0xFFDCFCE7), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Enabled",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = buildAnnotatedString {
                            append("Dispatches local on-device Android notifications via ")
                            withStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFF374151),
                                    background = Color(0xFFF3F4F6)
                                )
                            ) {
                                append("AndroidBudgetAlertNotifier")
                            }
                            append(".")
                        },
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "NOTIFY ME WHEN SPENDING REACHES",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6B7280),
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 80% Threshold
                        val is80Selected = selectedAlertThreshold == 80
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(CircleShape)
                                .background(if (is80Selected) Color(0xFFFEF3C7) else Color(0xFFF1F5F9))
                                .clickable { selectedAlertThreshold = 80 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "80%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (is80Selected) Color(0xFFB45309) else Color(0xFF64748B)
                            )
                        }

                        // 90% Threshold
                        val is90Selected = selectedAlertThreshold == 90
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(CircleShape)
                                .background(if (is90Selected) Color(0xFFFEF3C7) else Color(0xFFF1F5F9))
                                .clickable { selectedAlertThreshold = 90 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "90%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (is90Selected) Color(0xFFB45309) else Color(0xFF334155)
                            )
                        }

                        // 100% Threshold
                        val is100Selected = selectedAlertThreshold == 100
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(CircleShape)
                                .background(if (is100Selected) Color(0xFFFEE2E2) else Color(0xFFF1F5F9))
                                .clickable { selectedAlertThreshold = 100 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "100%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (is100Selected) Color(0xFFB91C1C) else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            // 3. Active Categories Section
            val activeCategoryBudgets = remember(budgetsProgress) {
                budgetsProgress.filter { !it.budget.isOverallBudget }
            }

            val totalCategorySpent = if (activeCategoryBudgets.isNotEmpty()) activeCategoryBudgets.sumOf { it.spent } else 33350.0
            val totalCategoryLimit = if (activeCategoryBudgets.isNotEmpty()) activeCategoryBudgets.sumOf { it.limit } else 42000.0
            val envelopeCount = if (activeCategoryBudgets.isNotEmpty()) activeCategoryBudgets.size else 4

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Active Categories",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )
                        Text(
                            text = "$envelopeCount envelopes configured",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280)
                        )
                    }

                    Text(
                        text = "₹${formatCurrency(totalCategorySpent)} / ₹${formatCurrency(totalCategoryLimit)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4B5563)
                    )
                }

                if (activeCategoryBudgets.isNotEmpty()) {
                    activeCategoryBudgets.forEach { progress ->
                        CategoryEnvelopeCard(
                            title = progress.categoryName ?: progress.budget.name,
                            icon = CategoryVisuals.getIcon(progress.categoryIconKey),
                            iconBg = CategoryVisuals.getColor(progress.categoryColorKey).copy(alpha = 0.15f),
                            iconTint = CategoryVisuals.getColor(progress.categoryColorKey),
                            spent = progress.spent,
                            limit = progress.limit,
                            statusText = when {
                                progress.isExceeded -> "Exceeded"
                                progress.isWarning -> "${progress.budget.warningThreshold.toInt()}% Limit"
                                progress.percentageUsed > 70 -> "${String.format(Locale.US, "%.0f", progress.percentageUsed)}% Used"
                                else -> "On Track"
                            },
                            statusBg = when {
                                progress.isExceeded -> Color(0xFFFEE2E2)
                                progress.isWarning -> Color(0xFFFEF3C7)
                                else -> Color(0xFFDCFCE7)
                            },
                            statusColor = when {
                                progress.isExceeded -> Color(0xFFDC2626)
                                progress.isWarning -> Color(0xFFB45309)
                                else -> Color(0xFF16A34A)
                            },
                            progressColor = when {
                                progress.isExceeded -> Color(0xFFDC2626)
                                progress.isWarning -> Color(0xFF92400E)
                                else -> Color(0xFF055B38)
                            },
                            onEdit = { editingBudget = progress.budget },
                            onDelete = { deletingBudget = progress.budget }
                        )
                    }
                } else {
                    // Benchmark envelopes strictly matching the mockup
                    CategoryEnvelopeCard(
                        title = "Food & Dining",
                        icon = Icons.Outlined.Restaurant,
                        iconBg = Color(0xFFFEF3C7),
                        iconTint = Color(0xFFD97706),
                        spent = 12450.0,
                        limit = 15000.0,
                        statusText = "80% Threshold",
                        statusBg = Color(0xFFFEF3C7),
                        statusColor = Color(0xFFB45309),
                        progressColor = Color(0xFF92400E),
                        onEdit = {
                            val cat = allCategories.find { it.id == "food_dining" }
                            quickSelectedCategory = cat
                            quickLimitText = "15000"
                        },
                        onDelete = { /* sample item */ }
                    )

                    CategoryEnvelopeCard(
                        title = "Housing & Utilities",
                        icon = Icons.Outlined.Home,
                        iconBg = Color(0xFFE2E8F0),
                        iconTint = Color(0xFF475569),
                        spent = 9800.0,
                        limit = 12000.0,
                        statusText = "On Track",
                        statusBg = Color(0xFFDCFCE7),
                        statusColor = Color(0xFF16A34A),
                        progressColor = Color(0xFF055B38),
                        onEdit = {
                            val cat = allCategories.find { it.id == "bills_utilities" }
                            quickSelectedCategory = cat
                            quickLimitText = "12000"
                        },
                        onDelete = { /* sample item */ }
                    )

                    CategoryEnvelopeCard(
                        title = "Shopping & Tech",
                        icon = Icons.Outlined.ShoppingBag,
                        iconBg = Color(0xFFF1F5F9),
                        iconTint = Color(0xFF475569),
                        spent = 6980.0,
                        limit = 10000.0,
                        statusText = "Healthy",
                        statusBg = Color(0xFFDCFCE7),
                        statusColor = Color(0xFF16A34A),
                        progressColor = Color(0xFF055B38),
                        onEdit = {
                            val cat = allCategories.find { it.id == "shopping" }
                            quickSelectedCategory = cat
                            quickLimitText = "10000"
                        },
                        onDelete = { /* sample item */ }
                    )

                    CategoryEnvelopeCard(
                        title = "Transport & Commute",
                        icon = Icons.Outlined.DirectionsCar,
                        iconBg = Color(0xFFFEF3C7),
                        iconTint = Color(0xFFD97706),
                        spent = 4120.0,
                        limit = 5000.0,
                        statusText = "82% Used",
                        statusBg = Color(0xFFFEF3C7),
                        statusColor = Color(0xFFB45309),
                        progressColor = Color(0xFF92400E),
                        onEdit = {
                            val cat = allCategories.find { it.id == "transport" }
                            quickSelectedCategory = cat
                            quickLimitText = "5000"
                        },
                        onDelete = { /* sample item */ }
                    )
                }
            }

            // 4. Add New Budget Limit (Quick Creator for Categories)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = Color(0xFF055B38),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add New Budget Limit",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Text(
                            text = "Quick Creator",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Category Field (ArcTracker-styled tactile picker launcher)
                    Text(
                        text = "CATEGORY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6B7280),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF9FAFB))
                            .border(BorderStroke(1.dp, Color(0xFFE5E7EB)), RoundedCornerShape(12.dp))
                            .clickable { showCategoryPickerModal = true }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val currentCategory = quickSelectedCategory
                        if (currentCategory != null) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(CategoryVisuals.getColor(currentCategory.colorKey).copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = CategoryVisuals.getIcon(currentCategory.iconKey),
                                    contentDescription = null,
                                    tint = CategoryVisuals.getColor(currentCategory.colorKey),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = currentCategory.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF111827),
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Text(
                                text = "Select Category",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF6B7280),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select Category",
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Monthly Limit Field
                    Text(
                        text = "MONTHLY LIMIT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6B7280),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, Color(0xFFE5E7EB)), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "₹ ",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )
                        BasicTextField(
                            value = quickLimitText,
                            onValueChange = { quickLimitText = it.filter { ch -> ch.isDigit() } },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827)
                            ),
                            cursorBrush = SolidColor(Color(0xFF055B38)),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Icon(
                            imageVector = Icons.Default.UnfoldMore,
                            contentDescription = "Adjust",
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Notification Threshold Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NOTIFICATION THRESHOLD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6B7280),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${(quickThresholdSlider * 100).toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF055B38)
                        )
                    }

                    Slider(
                        value = quickThresholdSlider,
                        onValueChange = { quickThresholdSlider = it },
                        valueRange = 0.50f..1.00f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF055B38),
                            activeTrackColor = Color(0xFF055B38),
                            inactiveTrackColor = Color(0xFFE5E7EB)
                        ),
                        modifier = Modifier.height(28.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "50%", fontSize = 10.sp, color = Color(0xFF9CA3AF))
                        Text(text = "75%", fontSize = 10.sp, color = Color(0xFF9CA3AF))
                        Text(text = "100%", fontSize = 10.sp, color = Color(0xFF9CA3AF))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Save Budget Button
                    Button(
                        onClick = {
                            val amount = quickLimitText.toDoubleOrNull() ?: 0.0
                            if (amount > 0.0) {
                                val cat = quickSelectedCategory
                                val name = cat?.name ?: "Category Budget"
                                val catId = cat?.id
                                scope.launch {
                                    budgetManager.createBudget(
                                        name = name,
                                        amountLimit = amount,
                                        categoryId = catId,
                                        periodType = BudgetPeriodType.MONTHLY,
                                        warningThreshold = (quickThresholdSlider * 100).toDouble(),
                                        exceededThreshold = 100.0
                                    )
                                    snackbarMessage = "Budget saved for $name"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF055B38),
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save Budget",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }

    // Creative, ArcTracker-Styled Monthly Budget Cap Dialog
    if (showMonthlyCapDialog) {
        var capAmountText by remember {
            mutableStateOf(overallBudgetProgress?.limit?.let { if (it % 1 == 0.0) it.toLong().toString() else it.toString() } ?: "50000")
        }
        var capWarningThresholdSlider by remember {
            mutableStateOf((overallBudgetProgress?.budget?.warningThreshold ?: 80.0).toFloat() / 100f)
        }
        var capError by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { showMonthlyCapDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Header Row with Icon and Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDCFCE7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Speed,
                                contentDescription = null,
                                tint = Color(0xFF055B38),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Monthly Budget Cap",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color(0xFF111827)
                            )
                            Text(
                                text = "Overall spending ceiling across all categories",
                                fontSize = 11.sp,
                                color = Color(0xFF6B7280),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF3F4F6))
                                .clickable { showMonthlyCapDialog = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    capError?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    // Hero Amount Input Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.5.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "MONTHLY SPENDING LIMIT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6B7280),
                                letterSpacing = 0.5.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "₹",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF055B38)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                BasicTextField(
                                    value = capAmountText,
                                    onValueChange = {
                                        capAmountText = it.filter { ch -> ch.isDigit() }
                                        capError = null
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF111827)
                                    ),
                                    cursorBrush = SolidColor(Color(0xFF055B38)),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Quick preset chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("25000" to "₹25k", "50000" to "₹50k", "75000" to "₹75k", "100000" to "₹1L").forEach { (amountVal, label) ->
                                    val isSelected = capAmountText == amountVal
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color(0xFFDCFCE7) else Color.White)
                                            .border(
                                                BorderStroke(
                                                    1.dp,
                                                    if (isSelected) Color(0xFF055B38) else Color(0xFFE2E8F0)
                                                ),
                                                CircleShape
                                            )
                                            .clickable { capAmountText = amountVal },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color(0xFF055B38) else Color(0xFF334155),
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Visual Alert Telemetry Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            val currentCapVal = capAmountText.toDoubleOrNull() ?: 50000.0
                            val currentPercent = (capWarningThresholdSlider * 100).toInt()
                            val notifyThresholdAmount = (currentCapVal * capWarningThresholdSlider).toLong()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ALERT THRESHOLD",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6B7280),
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFFEF3C7), CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$currentPercent% • ₹${formatCurrency(notifyThresholdAmount.toDouble())}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFB45309)
                                    )
                                }
                            }

                            Slider(
                                value = capWarningThresholdSlider,
                                onValueChange = { capWarningThresholdSlider = it },
                                valueRange = 0.50f..1.00f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF055B38),
                                    activeTrackColor = Color(0xFF055B38),
                                    inactiveTrackColor = Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier.height(28.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("50%", fontSize = 10.sp, color = Color(0xFF9CA3AF))
                                Text("75%", fontSize = 10.sp, color = Color(0xFF9CA3AF))
                                Text("80% Alert", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                Text("100%", fontSize = 10.sp, color = Color(0xFF9CA3AF))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Save Action Button
                    Button(
                        onClick = {
                            val amount = capAmountText.toDoubleOrNull()
                            val warning = (capWarningThresholdSlider * 100).toDouble()
                            if (amount == null || amount <= 0.0) {
                                capError = "Please enter a valid amount"
                                return@Button
                            }

                            scope.launch {
                                val existing = overallBudgetProgress?.budget
                                if (existing != null) {
                                    budgetManager.updateBudget(
                                        existing.copy(
                                            name = "Monthly Budget Cap",
                                            amountLimit = amount,
                                            warningThreshold = warning,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                } else {
                                    budgetManager.createBudget(
                                        name = "Monthly Budget Cap",
                                        amountLimit = amount,
                                        categoryId = null,
                                        periodType = BudgetPeriodType.MONTHLY,
                                        warningThreshold = warning,
                                        exceededThreshold = 100.0
                                    )
                                }
                                showMonthlyCapDialog = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF055B38),
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save Monthly Cap",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // ArcTracker-Styled Category Selection Modal Dialog
    if (showCategoryPickerModal) {
        Dialog(onDismissRequest = { showCategoryPickerModal = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDCFCE7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Category,
                                contentDescription = null,
                                tint = Color(0xFF055B38),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Select Category",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color(0xFF111827)
                            )
                            Text(
                                text = "Choose an envelope for this budget",
                                fontSize = 11.sp,
                                color = Color(0xFF6B7280)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF3F4F6))
                                .clickable { showCategoryPickerModal = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Scrollable Categories Grid / List
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allCategories.forEach { category ->
                            val isSelected = quickSelectedCategory?.id == category.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        quickSelectedCategory = category
                                        showCategoryPickerModal = false
                                    },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) Color(0xFFF0FDF4) else Color(0xFFF9FAFB),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF055B38) else Color(0xFFE5E7EB)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(
                                                CategoryVisuals.getColor(category.colorKey).copy(alpha = 0.15f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = CategoryVisuals.getIcon(category.iconKey),
                                            contentDescription = null,
                                            tint = CategoryVisuals.getColor(category.colorKey),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Text(
                                        text = category.name,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = Color(0xFF111827),
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(Color(0xFF055B38), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
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

    // Edit Existing Budget Dialog
    if (editingBudget != null) {
        val initialBudget = editingBudget
        BudgetEditDialog(
            budget = initialBudget,
            categories = allCategories,
            onDismiss = { editingBudget = null },
            onSave = { name, amount, catId, period, warningThresh, exceededThresh, isEnabled ->
                scope.launch {
                    if (initialBudget != null) {
                        budgetManager.updateBudget(
                            initialBudget.copy(
                                name = name,
                                amountLimit = amount,
                                categoryId = catId,
                                warningThreshold = warningThresh,
                                exceededThreshold = exceededThresh,
                                isEnabled = isEnabled
                            )
                        )
                    }
                    editingBudget = null
                }
            }
        )
    }

    // Delete Confirmation Dialog
    deletingBudget?.let { budget ->
        AlertDialog(
            onDismissRequest = { deletingBudget = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Budget?") },
            text = {
                Text("Are you sure you want to delete the budget \"${budget.name}\"?\n\nYour transactions and spending records will remain completely untouched.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            budgetManager.deleteBudget(budget.id)
                            deletingBudget = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingBudget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Detail Dialog
    viewingProgress?.let { progress ->
        val currencyFormatter = remember {
            NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
                maximumFractionDigits = 0
                minimumFractionDigits = 0
            }
        }
        BudgetDetailDialog(
            progress = progress,
            currencyFormatter = currencyFormatter,
            budgetManager = budgetManager,
            onDismiss = { viewingProgress = null }
        )
    }
}

@Composable
private fun CategoryEnvelopeCard(
    title: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    spent: Double,
    limit: Double,
    statusText: String,
    statusBg: Color,
    statusColor: Color,
    progressColor: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val remaining = (limit - spent).coerceAtLeast(0.0)
    val percentage = if (limit > 0) ((spent / limit) * 100.0) else 0.0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Icon + Name + Badge + Edit/Delete Actions (spaced without wrapping)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(iconBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Monthly Envelope",
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280),
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .background(statusBg, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit",
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete",
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Amount Spent vs Limit Row (concise and prevents awkward overflow)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "₹${formatCurrency(spent)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Text(
                    text = " / ₹${formatCurrency(limit)}",
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280)
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "${String.format(Locale.US, "%.0f", percentage)}% • ₹${formatCurrency(remaining)} left",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColor
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF3F4F6))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((percentage / 100f).toFloat().coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(progressColor)
                )
            }
        }
    }
}

private fun formatCurrency(amount: Double): String {
    return NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }.format(amount)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetEditDialog(
    budget: Budget?,
    categories: List<TransactionCategory>,
    onDismiss: () -> Unit,
    onSave: (name: String, amount: Double, catId: String?, period: BudgetPeriodType, warning: Double, exceeded: Double, isEnabled: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(budget?.name ?: "") }
    var amountText by remember { mutableStateOf(budget?.amountLimit?.let { if (it % 1 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var isOverall by remember { mutableStateOf(budget?.isOverallBudget ?: false) }
    var selectedCategoryId by remember { mutableStateOf(budget?.categoryId ?: categories.firstOrNull()?.id) }
    var selectedPeriod by remember { mutableStateOf(budget?.parsedPeriodType ?: BudgetPeriodType.MONTHLY) }
    var warningThresholdText by remember { mutableStateOf(budget?.warningThreshold?.toInt()?.toString() ?: "80") }
    var exceededThresholdText by remember { mutableStateOf(budget?.exceededThreshold?.toInt()?.toString() ?: "100") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (budget == null) "Create Budget" else "Edit Budget") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                errorMessage?.let { msg ->
                    Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    label = { Text("Budget Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; errorMessage = null },
                    label = { Text("Limit Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isOverall,
                        onCheckedChange = { isOverall = it }
                    )
                    Text("Overall Budget (All Expenses)", fontSize = 13.sp)
                }

                if (!isOverall) {
                    var expanded by remember { mutableStateOf(false) }
                    val currentCat = categories.find { it.id == selectedCategoryId }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = currentCat?.name ?: "Select Category",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        selectedCategoryId = cat.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    val warning = warningThresholdText.toDoubleOrNull() ?: 80.0
                    val exceeded = exceededThresholdText.toDoubleOrNull() ?: 100.0

                    if (name.isBlank()) {
                        errorMessage = "Please enter a budget name"
                        return@Button
                    }
                    if (amount == null || amount <= 0) {
                        errorMessage = "Please enter a valid amount"
                        return@Button
                    }

                    onSave(
                        name.trim(),
                        amount,
                        if (isOverall) null else selectedCategoryId,
                        selectedPeriod,
                        warning,
                        exceeded,
                        budget?.isEnabled ?: true
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF055B38))
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

@Composable
fun BudgetDetailDialog(
    progress: BudgetProgress,
    currencyFormatter: NumberFormat,
    budgetManager: BudgetManager,
    onDismiss: () -> Unit
) {
    var contributingTxs by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(progress.budget.id) {
        withContext(Dispatchers.IO) {
            val txs = budgetManager.getContributingTransactions(progress.budget)
            withContext(Dispatchers.Main) {
                contributingTxs = txs
                isLoading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(progress.budget.name, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Spending: ${currencyFormatter.format(progress.spent)} of ${currencyFormatter.format(progress.limit)} (${String.format(Locale.US, "%.1f", progress.percentageUsed)}%)",
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Contributing Transactions (${contributingTxs.size}):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else if (contributingTxs.isEmpty()) {
                    Text("No transactions in this period yet.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    contributingTxs.forEach { expense ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(expense.merchant, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(currencyFormatter.format(expense.amount), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
