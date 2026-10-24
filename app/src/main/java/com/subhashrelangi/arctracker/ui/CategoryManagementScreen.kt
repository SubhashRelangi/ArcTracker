package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.BuiltInCategories
import com.subhashrelangi.arctracker.data.CategoryVisuals
import com.subhashrelangi.arctracker.data.ExpenseDao
import com.subhashrelangi.arctracker.data.TransactionCategory
import com.subhashrelangi.arctracker.service.CategoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CategoryGlyphOption(
    val key: String,
    val icon: ImageVector
)

data class CategoryToneOption(
    val key: String,
    val label: String,
    val color: Color,
    val checkTint: Color = Color.White
)

private val ALL_CATEGORY_GLYPHS = listOf(
    CategoryGlyphOption("pets", Icons.Filled.Pets),
    CategoryGlyphOption("fitness_center", Icons.Filled.FitnessCenter),
    CategoryGlyphOption("medical_services", Icons.Filled.FavoriteBorder),
    CategoryGlyphOption("coffee", Icons.Filled.LocalCafe),
    CategoryGlyphOption("flight", Icons.Filled.Flight),
    CategoryGlyphOption("school", Icons.Filled.MenuBook),
    CategoryGlyphOption("card_giftcard", Icons.Filled.CardGiftcard),
    CategoryGlyphOption("movie", Icons.Filled.MusicNote),
    CategoryGlyphOption("restaurant", Icons.Default.Restaurant),
    CategoryGlyphOption("shopping_bag", Icons.Default.ShoppingBag),
    CategoryGlyphOption("local_grocery_store", Icons.Default.LocalGroceryStore),
    CategoryGlyphOption("directions_car", Icons.Default.DirectionsCar),
    CategoryGlyphOption("home", Icons.Default.Bolt),
    CategoryGlyphOption("work", Icons.Default.Computer),
    CategoryGlyphOption("receipt", Icons.Default.Receipt),
    CategoryGlyphOption("payments", Icons.Default.Payments),
    CategoryGlyphOption("account_balance_wallet", Icons.Default.AccountBalanceWallet),
    CategoryGlyphOption("savings", Icons.Default.Savings),
    CategoryGlyphOption("swap_horiz", Icons.Default.SwapHoriz),
    CategoryGlyphOption("category", Icons.Default.Category)
)

private val ALL_CATEGORY_TONES = listOf(
    CategoryToneOption("green", "Emerald", Color(0xFF047857), Color.White),
    CategoryToneOption("teal", "Teal / Mint", Color(0xFF0D9488), Color.White),
    CategoryToneOption("blue", "Royal Blue", Color(0xFF2563EB), Color.White),
    CategoryToneOption("indigo", "Indigo", Color(0xFF4F46E5), Color.White),
    CategoryToneOption("purple", "Purple", Color(0xFF7C3AED), Color.White),
    CategoryToneOption("pink", "Rose", Color(0xFFE11D48), Color.White),
    CategoryToneOption("red", "Coral", Color(0xFFDC2626), Color.White),
    CategoryToneOption("orange", "Orange", Color(0xFFEA580C), Color.White),
    CategoryToneOption("amber", "Amber", Color(0xFFD97706), Color.White),
    CategoryToneOption("cyan", "Cyan", Color(0xFF0284C7), Color.White),
    CategoryToneOption("brown", "Brown", Color(0xFF5D4037), Color.White),
    CategoryToneOption("default", "Charcoal", Color(0xFF334155), Color.White)
)

// In-memory cache to ensure instant frame-1 rendering when navigating between Rules/Aliases and Categories
private var cachedCategoriesState: List<TransactionCategory> = BuiltInCategories.ALL
private var cachedCategoryCountsState: Map<String, Int> = emptyMap()
private var cachedRulesCountState: Int = 14
private var cachedAliasesCountState: Int = 28

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRules: () -> Unit = {},
    onNavigateToAliases: () -> Unit = {},
    onNavigateToApplyRules: () -> Unit = {},
    manager: CategoryManager? = null,
    expenseDao: ExpenseDao? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val categoryManager = remember {
        manager ?: run {
            val db = AppDatabase.getDatabase(context)
            CategoryManager(db.transactionCategoryDao(), db.expenseDao(), db)
        }
    }

    val dao = remember {
        expenseDao ?: AppDatabase.getDatabase(context).expenseDao()
    }

    val ruleDao = remember { AppDatabase.getDatabase(context).userCategoryRuleDao() }
    val aliasDao = remember { AppDatabase.getDatabase(context).merchantAliasDao() }

    val activeRules by ruleDao.getActiveRulesFlow().collectAsState(initial = emptyList())
    val activeAliases by aliasDao.getActiveAliasesFlow().collectAsState(initial = emptyList())

    val rulesCount = remember(activeRules) {
        if (activeRules.isNotEmpty()) {
            cachedRulesCountState = activeRules.size
            activeRules.size
        } else {
            cachedRulesCountState
        }
    }

    val aliasesCount = remember(activeAliases) {
        if (activeAliases.isNotEmpty()) {
            cachedAliasesCountState = activeAliases.size
            activeAliases.size
        } else {
            cachedAliasesCountState
        }
    }

    // Ensure built-in categories exist
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            categoryManager.ensureBuiltInCategoriesSeeded()
        }
    }

    // Instant frame-1 state initialized from in-memory cache
    val allCategories by categoryManager.getAllCategoriesFlow().collectAsState(initial = cachedCategoriesState)
    LaunchedEffect(allCategories) {
        if (allCategories.isNotEmpty()) {
            cachedCategoriesState = allCategories
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Active, 1: Archived

    // Transaction counts per category initialized instantly with cached counts
    var categoryCounts by remember { mutableStateOf(cachedCategoryCountsState) }
    LaunchedEffect(allCategories) {
        withContext(Dispatchers.IO) {
            val counts = mutableMapOf<String, Int>()
            allCategories.forEach { cat ->
                counts[cat.id] = dao.countByCategoryId(cat.id)
            }
            categoryCounts = counts
            cachedCategoryCountsState = counts
        }
    }

    // Inline Add Category Form state
    var newCategoryName by remember { mutableStateOf("") }
    var selectedGlyph by remember { mutableStateOf(ALL_CATEGORY_GLYPHS.first()) }
    var selectedTone by remember { mutableStateOf(ALL_CATEGORY_TONES.first()) }
    var isMoreIconsExpanded by remember { mutableStateOf(false) }

    // Dialog & Bottom Sheet States
    var categoryToEdit by remember { mutableStateOf<TransactionCategory?>(null) }
    var categoryToDelete by remember { mutableStateOf<TransactionCategory?>(null) }
    var categoryToArchive by remember { mutableStateOf<TransactionCategory?>(null) }

    val activeCategories = remember(allCategories) { allCategories.filter { !it.isArchived } }
    val archivedCategories = remember(allCategories) { allCategories.filter { it.isArchived } }
    val displayedList = if (selectedTab == 0) activeCategories else archivedCategories

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = Color.White,
                shadowElevation = 0.5.dp,
                modifier = Modifier.fillMaxWidth().background(Color.White)
            ) {
                com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader(
                    title = "Categories",
                    subtitle = "Taxonomy & Custom Tags",
                    onBackClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .statusBarsPadding(),
                    horizontalPadding = 16.dp,
                    actions = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Auto-apply rules button
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), CircleShape)
                                    .clickable(onClick = onNavigateToApplyRules),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoFixHigh,
                                    contentDescription = "Apply Rules",
                                    tint = Color(0xFF0F172A),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // 2. Circular Add (+) Button
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F172A))
                                    .clickable {
                                        scope.launch {
                                            listState.animateScrollToItem(4)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Add Category",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Rules & Aliases Navigation Cards Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Rules Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(14.dp))
                            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(14.dp))
                            .clickable(onClick = onNavigateToRules)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFE2E8F0), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = null,
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Rules",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "$rulesCount active",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Aliases Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(14.dp))
                            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(14.dp))
                            .clickable(onClick = onNavigateToAliases)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFE2E8F0), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Storefront,
                                    contentDescription = null,
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Aliases",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "$aliasesCount mapped",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Segmented Tab Pill Selector: Active vs Archived
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Active Tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedTab == 0) Color.White else Color.Transparent)
                                .then(
                                    if (selectedTab == 0) Modifier.border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(10.dp))
                                    else Modifier
                                )
                                .clickable { selectedTab = 0 }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Active",
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == 0) Color(0xFF0F172A) else Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${activeCategories.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedTab == 0) Color(0xFF0F172A) else Color(0xFF64748B)
                                    )
                                }
                            }
                        }

                        // Archived Tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selectedTab == 1) Color.White else Color.Transparent)
                                .then(
                                    if (selectedTab == 1) Modifier.border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(10.dp))
                                    else Modifier
                                )
                                .clickable { selectedTab = 1 }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Archived",
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == 1) Color(0xFF0F172A) else Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${archivedCategories.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedTab == 1) Color(0xFF0F172A) else Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Grouped Category Section in One Card with Dividers
            item {
                if (displayedList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(16.dp))
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedTab == 0) "No active categories" else "No archived categories",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            displayedList.forEachIndexed { index, category ->
                                val count = categoryCounts[category.id] ?: 0
                                CategoryMockRow(
                                    category = category,
                                    transactionCount = count,
                                    onEdit = { categoryToEdit = category },
                                    onArchiveToggle = {
                                        if (category.isArchived) {
                                            scope.launch {
                                                val res = categoryManager.restoreCategory(category.id)
                                                if (res.isSuccess) {
                                                    snackbarHostState.showSnackbar("Restored \"${category.name}\"")
                                                }
                                            }
                                        } else {
                                            categoryToArchive = category
                                        }
                                    },
                                    onDelete = { categoryToDelete = category }
                                )

                                // Subtle divider between items inside the section
                                if (index < displayedList.size - 1) {
                                    HorizontalDivider(
                                        color = Color(0xFFF1F5F9),
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(horizontal = 14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Inline "Add New Category" Section at bottom
            item {
                Spacer(modifier = Modifier.height(6.dp))
                AddNewCategoryInlineCard(
                    categoryName = newCategoryName,
                    onCategoryNameChange = { newCategoryName = it },
                    selectedGlyph = selectedGlyph,
                    onGlyphSelected = { selectedGlyph = it },
                    selectedTone = selectedTone,
                    onToneSelected = { selectedTone = it },
                    isMoreIconsExpanded = isMoreIconsExpanded,
                    onToggleMoreIcons = { isMoreIconsExpanded = !isMoreIconsExpanded },
                    onCreateCategory = {
                        val trimmed = newCategoryName.trim()
                        if (trimmed.isEmpty()) {
                            scope.launch { snackbarHostState.showSnackbar("Category name cannot be empty") }
                            return@AddNewCategoryInlineCard
                        }
                        if (allCategories.any { it.name.equals(trimmed, ignoreCase = true) }) {
                            scope.launch { snackbarHostState.showSnackbar("A category with this name already exists") }
                            return@AddNewCategoryInlineCard
                        }
                        scope.launch {
                            val res = categoryManager.createCategory(
                                name = trimmed,
                                iconKey = selectedGlyph.key,
                                colorKey = selectedTone.key
                            )
                            if (res.isSuccess) {
                                snackbarHostState.showSnackbar("Created category \"$trimmed\"")
                                newCategoryName = ""
                            } else {
                                snackbarHostState.showSnackbar(res.exceptionOrNull()?.message ?: "Failed to create category")
                            }
                        }
                    }
                )
            }
        }
    }

    // Modern Edit Category Modal Bottom Sheet (opens at ~70% initially, expands to 100% on scroll, solid bottom coverage)
    categoryToEdit?.let { category ->
        EditCategoryBottomSheet(
            category = category,
            existingCategories = allCategories,
            onDismiss = { categoryToEdit = null },
            onConfirm = { newName, newIcon, newColor ->
                scope.launch {
                    val result = categoryManager.updateCategory(
                        id = category.id,
                        name = if (!category.isSystem) newName else category.name,
                        iconKey = newIcon,
                        colorKey = newColor
                    )
                    if (result.isSuccess) {
                        snackbarHostState.showSnackbar("Updated \"${category.name}\"")
                        categoryToEdit = null
                    } else {
                        snackbarHostState.showSnackbar(result.exceptionOrNull()?.message ?: "Error updating category")
                    }
                }
            }
        )
    }

    // Archive Confirmation Dialog
    categoryToArchive?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryToArchive = null },
            title = { Text("Archive \"${category.name}\"?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Archived categories are hidden from the transaction dropdown, but existing transactions will retain this category.",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = category
                        categoryToArchive = null
                        scope.launch {
                            val res = categoryManager.archiveCategory(target.id)
                            if (res.isSuccess) {
                                snackbarHostState.showSnackbar("Archived \"${target.name}\"")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857))
                ) {
                    Text("Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToArchive = null }) {
                    Text("Cancel", color = Color(0xFF0F172A))
                }
            }
        )
    }

    // Delete Category Dialog (Custom categories only)
    categoryToDelete?.let { category ->
        val count = categoryCounts[category.id] ?: 0
        var reassignTargetId by remember { mutableStateOf<String?>(null) }
        var deleteMode by remember { mutableIntStateOf(if (count > 0) 0 else 1) }
        val targetOptions = remember(activeCategories, category) {
            activeCategories.filter { it.id != category.id }
        }

        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete \"${category.name}\"?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (category.isSystem) {
                        Text("System categories cannot be deleted. You can archive this category instead.", color = Color(0xFFC62828))
                    } else {
                        Text(
                            text = if (count > 0) {
                                "$count transaction(s) are currently assigned to this category."
                            } else {
                                "No transactions are currently assigned to this category."
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        if (count > 0) {
                            Text("What would you like to do with these transactions?", fontSize = 12.sp, color = Color(0xFF64748B))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = deleteMode == 0,
                                    onClick = { deleteMode = 0 },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF047857))
                                )
                                Text("Reassign to another category", fontSize = 12.sp)
                            }

                            if (deleteMode == 0 && targetOptions.isNotEmpty()) {
                                var dropdownExpanded by remember { mutableStateOf(false) }
                                val selectedTargetName = targetOptions.find { it.id == reassignTargetId }?.name ?: "Select Target Category"

                                Box(modifier = Modifier.padding(start = 32.dp)) {
                                    OutlinedButton(onClick = { dropdownExpanded = true }) {
                                        Text(selectedTargetName, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    DropdownMenu(
                                        expanded = dropdownExpanded,
                                        onDismissRequest = { dropdownExpanded = false }
                                    ) {
                                        targetOptions.forEach { opt ->
                                            DropdownMenuItem(
                                                text = { Text(opt.name) },
                                                onClick = {
                                                    reassignTargetId = opt.id
                                                    dropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = deleteMode == 1,
                                    onClick = { deleteMode = 1 },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF047857))
                                )
                                Text("Leave Uncategorized", fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!category.isSystem) {
                    Button(
                        onClick = {
                            val targetReassign = if (deleteMode == 0) reassignTargetId else null
                            val catId = category.id
                            categoryToDelete = null
                            scope.launch {
                                val res = categoryManager.deleteCategory(catId, targetReassign)
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Deleted \"${category.name}\"")
                                } else {
                                    snackbarHostState.showSnackbar(res.exceptionOrNull()?.message ?: "Delete failed")
                                }
                            }
                        },
                        enabled = count == 0 || deleteMode == 1 || (deleteMode == 0 && reassignTargetId != null),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) {
                        Text("Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Cancel", color = Color(0xFF0F172A))
                }
            }
        )
    }
}

/**
 * Individual row inside the unified Category Section.
 * Sleek, proportionate font sizes and clean spacing with divider support.
 */
@Composable
private fun CategoryMockRow(
    category: TransactionCategory,
    transactionCount: Int,
    onEdit: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val nameLower = category.name.lowercase()
    val (containerBg, iconTint, iconVector) = remember(category) {
        when {
            nameLower.contains("food") || nameLower.contains("dining") -> Triple(Color(0xFFFEF3C7), Color(0xFFD97706), Icons.Default.Restaurant)
            nameLower.contains("hous") || nameLower.contains("util") -> Triple(Color(0xFFF1F5F9), Color(0xFF64748B), Icons.Default.Bolt)
            nameLower.contains("shop") || nameLower.contains("gadget") -> Triple(Color(0xFFE2E8F0), Color(0xFF475569), Icons.Default.ShoppingBag)
            nameLower.contains("travel") || nameLower.contains("commute") -> Triple(Color(0xFFD1FAE5), Color(0xFF059669), Icons.Default.DirectionsCar)
            nameLower.contains("freelance") || nameLower.contains("saas") -> Triple(Color(0xFFE2E8F0), Color(0xFF475569), Icons.Default.Computer)
            else -> Triple(
                CategoryVisuals.getContainerColor(category.colorKey),
                CategoryVisuals.getColor(category.colorKey),
                CategoryVisuals.getIcon(category.iconKey)
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category Icon box
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(containerBg, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = category.name,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Center details: Sleek proportionate font size
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = category.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(
                            color = if (category.isSystem) Color(0xFFF1F5F9) else Color(0xFFECFDF5),
                            shape = RoundedCornerShape(5.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = if (category.isSystem) "System" else "Custom",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (category.isSystem) Color(0xFF475569) else Color(0xFF047857)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$transactionCount transactions logged",
                fontSize = 11.5.sp,
                color = Color(0xFF64748B)
            )
        }

        // Action icons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Edit Icon Button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF8FAFC))
                    .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), CircleShape)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit Category",
                    tint = Color(0xFF475569),
                    modifier = Modifier.size(15.dp)
                )
            }

            if (category.isSystem) {
                // Archive Toggle Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF8FAFC))
                        .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), CircleShape)
                        .clickable(onClick = onArchiveToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Archive,
                        contentDescription = "Archive",
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(15.dp)
                    )
                }
            } else {
                // Delete Button for Custom Categories
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEE2E2))
                        .border(BorderStroke(1.dp, Color(0xFFFECACA)), CircleShape)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/**
 * Inline "Add New Category" Section at the bottom of the list.
 * Clean, centered icon grid without text labels, full color palette,
 * and a circle/pill format button dynamically colored to match the selected tone.
 */
@Composable
private fun AddNewCategoryInlineCard(
    categoryName: String,
    onCategoryNameChange: (String) -> Unit,
    selectedGlyph: CategoryGlyphOption,
    onGlyphSelected: (CategoryGlyphOption) -> Unit,
    selectedTone: CategoryToneOption,
    onToneSelected: (CategoryToneOption) -> Unit,
    isMoreIconsExpanded: Boolean,
    onToggleMoreIcons: () -> Unit,
    onCreateCategory: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row: Green dot + Add New Category + Custom Node label
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(selectedTone.color, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add New Category",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Custom Node",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Field: Category Name
            Text(
                text = "Category Name",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF475569)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                    .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (categoryName.isEmpty()) {
                    Text(
                        text = "e.g. Pet Care",
                        fontSize = 13.5.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                BasicTextField(
                    value = categoryName,
                    onValueChange = onCategoryNameChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 13.5.sp,
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(selectedTone.color),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section: Icon Glyph with Centered Icon Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Icon Glyph",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF475569)
                )
                Spacer(modifier = Modifier.weight(1f))

                // Interactive toggle for more icons
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFECFDF5))
                        .border(BorderStroke(1.dp, Color(0xFFA7F3D0)), RoundedCornerShape(10.dp))
                        .clickable(onClick = onToggleMoreIcons)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isMoreIconsExpanded) "▲ Less" else "▼ More Icons (${ALL_CATEGORY_GLYPHS.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF047857)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Centered icons without text labels - 4 columns
            val displayedGlyphs = if (isMoreIconsExpanded) ALL_CATEGORY_GLYPHS else ALL_CATEGORY_GLYPHS.take(8)
            val glyphChunks = displayedGlyphs.chunked(4)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                glyphChunks.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { item ->
                            val isSelected = item.key == selectedGlyph.key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) selectedTone.color.copy(alpha = 0.12f) else Color(0xFFF1F5F9))
                                    .border(
                                        BorderStroke(
                                            if (isSelected) 1.5.dp else 1.dp,
                                            if (isSelected) selectedTone.color else Color.Transparent
                                        ),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onGlyphSelected(item) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) selectedTone.color else Color(0xFF64748B),
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section: Theme Tone with interactive palette
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Theme Tone",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF475569)
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(selectedTone.color, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedTone.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = selectedTone.color
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 12-Color Selector in horizontal scrollable row with generous swatches
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ALL_CATEGORY_TONES.forEach { tone ->
                    val isSelected = tone.key == selectedTone.key
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(tone.color)
                            .then(
                                if (isSelected) Modifier.border(BorderStroke(2.5.dp, Color.White), CircleShape)
                                else Modifier
                            )
                            .clickable { onToneSelected(tone) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = tone.label,
                                tint = tone.checkTint,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Circle / Pill Format Button dynamically colored to match selected category tone
            Button(
                onClick = onCreateCategory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = selectedTone.color)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color.White.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Create Category",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Modern Edit Category Modal Bottom Sheet:
 * - Opens initially at ~70% screen height, and expands smoothly to 100% on scroll/drag.
 * - Sheet container surface sits solidly at the bottom with zero gap (no category components visible underneath).
 * - Internal scroll is enabled only once the sheet is fully expanded to 100%, allowing smooth expansion first.
 * - 8 centered icons by default + "More Icons (20)" toggle.
 * - Colors in ONE line scrollable.
 * - Dynamic pill format Save button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCategoryBottomSheet(
    category: TransactionCategory,
    existingCategories: List<TransactionCategory>,
    onDismiss: () -> Unit,
    onConfirm: (newName: String, newIcon: String, newColor: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var name by remember { mutableStateOf(category.name) }
    var selectedIcon by remember { mutableStateOf(category.iconKey) }
    var selectedColor by remember { mutableStateOf(category.colorKey) }
    var isMoreIconsExpanded by remember { mutableStateOf(false) }

    val nameValidation = remember(name, existingCategories, category) {
        val trimmed = name.trim()
        when {
            trimmed.isEmpty() -> "Name cannot be empty"
            trimmed.length > 30 -> "Name must be 30 characters or fewer"
            existingCategories.any { it.id != category.id && it.name.equals(trimmed, ignoreCase = true) } -> "A category with this name already exists"
            else -> null
        }
    }

    val activeTone = remember(selectedColor) {
        ALL_CATEGORY_TONES.find { it.key.equals(selectedColor, ignoreCase = true) }
            ?: ALL_CATEGORY_TONES.first()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        // Internal scroll is only enabled once the sheet expands to 100%, ensuring drag gestures expand the sheet first!
        val isExpanded = sheetState.currentValue == SheetValue.Expanded

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(scrollState, enabled = isExpanded)
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 28.dp)
        ) {
            // Header: Title, Subtitle, and Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Visual Icon Preview
                val previewBg = CategoryVisuals.getContainerColor(selectedColor)
                val previewTint = CategoryVisuals.getColor(selectedColor)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(previewBg, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CategoryVisuals.getIcon(selectedIcon),
                        contentDescription = null,
                        tint = previewTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (category.isSystem) "Customize System Category" else "Edit Category",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = if (category.isSystem) "Customize icon and theme color" else "Edit category name, icon and color tone",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(0xFFF1F5F9), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Name Textbox (or Locked notification for System Category)
            if (!category.isSystem) {
                Text(
                    text = "Category Name",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    isError = nameValidation != null,
                    supportingText = {
                        if (nameValidation != null) {
                            Text(nameValidation, color = Color(0xFFDC2626), fontSize = 11.sp)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC),
                        focusedBorderColor = activeTone.color,
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Category Name (Locked)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = category.name,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "System category names are locked to preserve regex rules, but you can fully customize icon & color.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Icon Glyph Section: 8 icons by default + More Icons button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Icon Glyph",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.weight(1f))

                // Interactive More Icons button (clicking smoothly expands bottom sheet to 100%)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFECFDF5))
                        .border(BorderStroke(1.dp, Color(0xFFA7F3D0)), RoundedCornerShape(10.dp))
                        .clickable {
                            isMoreIconsExpanded = !isMoreIconsExpanded
                            if (isMoreIconsExpanded) {
                                scope.launch { sheetState.expand() }
                            }
                        }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isMoreIconsExpanded) "▲ Less" else "▼ More Icons (${ALL_CATEGORY_GLYPHS.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF047857)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Centered icons without text labels: 8 icons by default, or all 20 when expanded
            val displayedGlyphs = if (isMoreIconsExpanded) ALL_CATEGORY_GLYPHS else ALL_CATEGORY_GLYPHS.take(8)
            val iconChunks = displayedGlyphs.chunked(4)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                iconChunks.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { item ->
                            val isSelected = item.key.equals(selectedIcon, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) activeTone.color.copy(alpha = 0.12f) else Color(0xFFF8FAFC))
                                    .border(
                                        BorderStroke(
                                            if (isSelected) 1.5.dp else 1.dp,
                                            if (isSelected) activeTone.color else Color(0xFFE2E8F0)
                                        ),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedIcon = item.key },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) activeTone.color else Color(0xFF64748B),
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Theme Tone Section: Colors in ONE LINE SCROLLABLE
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Theme Tone",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(activeTone.color, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = activeTone.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeTone.color
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Colors in ONE horizontal scrollable line
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ALL_CATEGORY_TONES.forEach { tone ->
                    val isSelected = tone.key.equals(selectedColor, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(tone.color)
                            .then(
                                if (isSelected) Modifier.border(BorderStroke(2.5.dp, Color.White), CircleShape)
                                else Modifier
                            )
                            .clickable { selectedColor = tone.key },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = tone.label,
                                tint = tone.checkTint,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Dynamic Circle / Pill Format Button for Save Changes
            Button(
                onClick = { onConfirm(name.trim(), selectedIcon, selectedColor) },
                enabled = category.isSystem || nameValidation == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeTone.color,
                    disabledContainerColor = Color(0xFFCBD5E1)
                )
            ) {
                Text(
                    text = "Save Changes",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
