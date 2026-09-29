package com.example.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.example.arctracker.data.CategoryVisuals
import com.example.arctracker.data.ExpenseDao
import com.example.arctracker.data.TransactionCategory
import com.example.arctracker.service.CategoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onNavigateBack: () -> Unit,
    manager: CategoryManager? = null,
    expenseDao: ExpenseDao? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val categoryManager = remember {
        manager ?: run {
            val db = AppDatabase.getDatabase(context)
            CategoryManager(db.transactionCategoryDao(), db.expenseDao(), db)
        }
    }

    val dao = remember {
        expenseDao ?: AppDatabase.getDatabase(context).expenseDao()
    }

    // Ensure built-in categories exist
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            categoryManager.ensureBuiltInCategoriesSeeded()
        }
    }

    val allCategories by categoryManager.getAllCategoriesFlow().collectAsState(initial = emptyList())
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Active, 1: Archived

    // Transaction counts per category
    var categoryCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(allCategories) {
        withContext(Dispatchers.IO) {
            val counts = mutableMapOf<String, Int>()
            allCategories.forEach { cat ->
                counts[cat.id] = dao.countByCategoryId(cat.id)
            }
            categoryCounts = counts
        }
    }

    // Dialog States
    var showAddDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<TransactionCategory?>(null) }
    var categoryToDelete by remember { mutableStateOf<TransactionCategory?>(null) }
    var categoryToMerge by remember { mutableStateOf<TransactionCategory?>(null) }
    var categoryToArchive by remember { mutableStateOf<TransactionCategory?>(null) }

    val activeCategories = remember(allCategories) { allCategories.filter { !it.isArchived } }
    val archivedCategories = remember(allCategories) { allCategories.filter { it.isArchived } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Categories",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Manage system & custom categories",
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleColor
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add Category",
                            tint = purpleColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF9F9FB))
                .padding(innerPadding)
        ) {
            // Tabs: Active vs Archived
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = purpleColor
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Active (${activeCategories.size})", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Archived (${archivedCategories.size})", fontWeight = FontWeight.SemiBold) }
                )
            }

            val displayedList = if (selectedTab == 0) activeCategories else archivedCategories

            if (displayedList.isEmpty()) {
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
                                .background(lightPurpleColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (selectedTab == 0) Icons.Filled.Category else Icons.Filled.Archive,
                                contentDescription = null,
                                tint = purpleColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedTab == 0) "No active categories" else "No archived categories",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayedList, key = { it.id }) { category ->
                        val count = categoryCounts[category.id] ?: 0
                        CategoryRowCard(
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
                            onMerge = { categoryToMerge = category },
                            onDelete = { categoryToDelete = category }
                        )
                    }
                }
            }
        }
    }

    // Add Category Dialog
    if (showAddDialog) {
        AddCategoryDialog(
            existingCategories = allCategories,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, iconKey, colorKey ->
                scope.launch {
                    val result = categoryManager.createCategory(name, iconKey, colorKey)
                    if (result.isSuccess) {
                        snackbarHostState.showSnackbar("Category \"$name\" created")
                        showAddDialog = false
                    } else {
                        snackbarHostState.showSnackbar(result.exceptionOrNull()?.message ?: "Error creating category")
                    }
                }
            }
        )
    }

    // Edit Category Dialog
    categoryToEdit?.let { category ->
        EditCategoryDialog(
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
                    color = subtitleColor
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
                    colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                ) {
                    Text("Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToArchive = null }) {
                    Text("Cancel", color = textColor)
                }
            }
        )
    }

    // Delete Category Dialog (Custom categories only)
    categoryToDelete?.let { category ->
        val count = categoryCounts[category.id] ?: 0
        var reassignTargetId by remember { mutableStateOf<String?>(null) }
        var deleteMode by remember { mutableIntStateOf(if (count > 0) 0 else 1) } // 0: Reassign, 1: Unlink
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
                            Text("What would you like to do with these transactions?", fontSize = 12.sp, color = subtitleColor)

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = deleteMode == 0,
                                    onClick = { deleteMode = 0 },
                                    colors = RadioButtonDefaults.colors(selectedColor = purpleColor)
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
                                    colors = RadioButtonDefaults.colors(selectedColor = purpleColor)
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) {
                        Text("Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Cancel", color = textColor)
                }
            }
        )
    }

    // Merge Categories Dialog
    categoryToMerge?.let { sourceCategory ->
        val count = categoryCounts[sourceCategory.id] ?: 0
        var targetCategory by remember { mutableStateOf<TransactionCategory?>(null) }
        val targetCandidates = remember(activeCategories, sourceCategory) {
            activeCategories.filter { it.id != sourceCategory.id }
        }

        AlertDialog(
            onDismissRequest = { categoryToMerge = null },
            title = { Text("Merge \"${sourceCategory.name}\"", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Move all $count transaction(s) from \"${sourceCategory.name}\" into a target category.",
                        fontSize = 13.sp,
                        color = subtitleColor
                    )
                    Text(
                        text = if (sourceCategory.isSystem) {
                            "Note: Since \"${sourceCategory.name}\" is a system category, it will be archived after merging."
                        } else {
                            "Note: \"${sourceCategory.name}\" will be deleted after merging."
                        },
                        fontSize = 11.sp,
                        color = purpleColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Select Target Category:", fontWeight = FontWeight.Bold, fontSize = 12.sp)

                    var dropdownExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(targetCategory?.name ?: "Choose Target Category", fontSize = 13.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            targetCandidates.forEach { candidate ->
                                DropdownMenuItem(
                                    text = { Text(candidate.name) },
                                    onClick = {
                                        targetCategory = candidate
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = targetCategory
                        if (target != null) {
                            val srcId = sourceCategory.id
                            val tgtId = target.id
                            categoryToMerge = null
                            scope.launch {
                                val res = categoryManager.mergeCategories(srcId, tgtId)
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Merged \"${sourceCategory.name}\" into \"${target.name}\"")
                                } else {
                                    snackbarHostState.showSnackbar(res.exceptionOrNull()?.message ?: "Merge failed")
                                }
                            }
                        }
                    },
                    enabled = targetCategory != null,
                    colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
                ) {
                    Text("Merge")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToMerge = null }) {
                    Text("Cancel", color = textColor)
                }
            }
        )
    }
}

@Composable
fun CategoryRowCard(
    category: TransactionCategory,
    transactionCount: Int,
    onEdit: () -> Unit,
    onArchiveToggle: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visual Icon inside colored circle
            val catColor = CategoryVisuals.getColor(category.colorKey)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(catColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CategoryVisuals.getIcon(category.iconKey),
                    contentDescription = category.name,
                    tint = catColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = if (category.isSystem) Color(0xFFEDE7F6) else Color(0xFFE3F2FD),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (category.isSystem) "System" else "Custom",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (category.isSystem) purpleColor else Color(0xFF1976D2),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    if (category.isArchived) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Archived",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$transactionCount transactions",
                    fontSize = 11.sp,
                    color = subtitleColor
                )
            }

            // Action Icons
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = Color.Gray)
                }
                IconButton(onClick = onMerge, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Merge, contentDescription = "Merge", modifier = Modifier.size(16.dp), tint = Color.Gray)
                }
                IconButton(onClick = onArchiveToggle, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (category.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                        contentDescription = if (category.isArchived) "Restore" else "Archive",
                        modifier = Modifier.size(16.dp),
                        tint = Color.Gray
                    )
                }
                if (!category.isSystem) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = Color(0xFFC62828))
                    }
                }
            }
        }
    }
}

@Composable
fun AddCategoryDialog(
    existingCategories: List<TransactionCategory>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, iconKey: String, colorKey: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(CategoryVisuals.AVAILABLE_ICON_KEYS.first()) }
    var selectedColor by remember { mutableStateOf(CategoryVisuals.AVAILABLE_COLOR_KEYS.first()) }

    val nameValidation = remember(name, existingCategories) {
        val trimmed = name.trim()
        when {
            trimmed.isEmpty() -> "Name cannot be empty"
            trimmed.length > 30 -> "Name must be 30 characters or fewer"
            existingCategories.any { it.name.equals(trimmed, ignoreCase = true) } -> "A category with this name already exists"
            else -> null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Category", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    isError = name.isNotBlank() && nameValidation != null,
                    supportingText = {
                        if (name.isNotBlank() && nameValidation != null) {
                            Text(nameValidation, color = Color(0xFFC62828))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Icon", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.height(100.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(CategoryVisuals.AVAILABLE_ICON_KEYS) { iconKey ->
                        val isSelected = selectedIcon == iconKey
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) lightPurpleColor else Color(0xFFF5F5F5),
                            border = if (isSelected) BorderStroke(2.dp, purpleColor) else null,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { selectedIcon = iconKey }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = CategoryVisuals.getIcon(iconKey),
                                    contentDescription = iconKey,
                                    tint = if (isSelected) purpleColor else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Text("Color", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.height(80.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(CategoryVisuals.AVAILABLE_COLOR_KEYS) { colorKey ->
                        val isSelected = selectedColor == colorKey
                        val swatchColor = CategoryVisuals.getColor(colorKey)
                        Surface(
                            shape = CircleShape,
                            color = swatchColor,
                            border = if (isSelected) BorderStroke(2.dp, Color.Black) else null,
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { selectedColor = colorKey }
                        ) {
                            if (isSelected) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), selectedIcon, selectedColor) },
                enabled = nameValidation == null,
                colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textColor)
            }
        }
    )
}

@Composable
fun EditCategoryDialog(
    category: TransactionCategory,
    existingCategories: List<TransactionCategory>,
    onDismiss: () -> Unit,
    onConfirm: (newName: String, newIcon: String, newColor: String) -> Unit
) {
    var name by remember { mutableStateOf(category.name) }
    var selectedIcon by remember { mutableStateOf(category.iconKey) }
    var selectedColor by remember { mutableStateOf(category.colorKey) }

    val nameValidation = remember(name, existingCategories, category) {
        val trimmed = name.trim()
        when {
            trimmed.isEmpty() -> "Name cannot be empty"
            trimmed.length > 30 -> "Name must be 30 characters or fewer"
            existingCategories.any { it.id != category.id && it.name.equals(trimmed, ignoreCase = true) } -> "A category with this name already exists"
            else -> null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (category.isSystem) "Customize System Category" else "Edit Category",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!category.isSystem) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Category Name") },
                        isError = nameValidation != null,
                        supportingText = {
                            if (nameValidation != null) {
                                Text(nameValidation, color = Color(0xFFC62828))
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "Category: ${category.name} (Built-in)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = textColor
                    )
                }

                Text("Icon", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.height(100.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(CategoryVisuals.AVAILABLE_ICON_KEYS) { iconKey ->
                        val isSelected = selectedIcon == iconKey
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) lightPurpleColor else Color(0xFFF5F5F5),
                            border = if (isSelected) BorderStroke(2.dp, purpleColor) else null,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { selectedIcon = iconKey }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = CategoryVisuals.getIcon(iconKey),
                                    contentDescription = iconKey,
                                    tint = if (isSelected) purpleColor else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Text("Color", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.height(80.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(CategoryVisuals.AVAILABLE_COLOR_KEYS) { colorKey ->
                        val isSelected = selectedColor == colorKey
                        val swatchColor = CategoryVisuals.getColor(colorKey)
                        Surface(
                            shape = CircleShape,
                            color = swatchColor,
                            border = if (isSelected) BorderStroke(2.dp, Color.Black) else null,
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { selectedColor = colorKey }
                        ) {
                            if (isSelected) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), selectedIcon, selectedColor) },
                enabled = category.isSystem || nameValidation == null,
                colors = ButtonDefaults.buttonColors(containerColor = purpleColor)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textColor)
            }
        }
    )
}
