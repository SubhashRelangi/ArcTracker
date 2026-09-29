package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.service.CategoryManager
import com.subhashrelangi.arctracker.service.TransactionFilter
import com.subhashrelangi.arctracker.service.TransactionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * JVM Unit Test Suite for Milestone 9: Category System & Custom Categories.
 *
 * Covers:
 * 1. TransactionCategory entity definition & defaults.
 * 2. BuiltInCategories seeding, stability, IDs, system flag, icon/color keys.
 * 3. Custom category creation, UUID format, defaults.
 * 4. Validation rules: name length, empty/blank, duplicates, case-insensitivity.
 * 5. Visual key validation: icons and colors.
 * 6. Rename, icon/color updates.
 * 7. System category update rules (icon/color mutable, ID & isSystem immutable).
 * 8. Archive and restore lifecycle.
 * 9. Active and archived category queries.
 * 10. Deletion safety invariants (system categories forbidden, reassign vs unlink modes).
 * 11. Category merging (reassigning all expenses, deleting custom source, archiving system source).
 * 12. Single and bulk transaction category assignments.
 * 13. Legacy tag mapping & Expense.category backwards-compatibility getter/setter.
 * 14. ExpenseDao category queries (by categoryId, uncategorized).
 * 15. TransactionManager category filtering (by id, by name, uncategorized).
 * 16. Category reordering.
 */
class CategorySystemAndManagementTest {

    private lateinit var categoryDao: FakeTransactionCategoryDao
    private lateinit var expenseDao: FakeExpenseDao
    private lateinit var categoryManager: CategoryManager
    private lateinit var transactionManager: TransactionManager

    @Before
    fun setUp() {
        categoryDao = FakeTransactionCategoryDao()
        expenseDao = FakeExpenseDao()
        categoryManager = CategoryManager(categoryDao, expenseDao)
        transactionManager = TransactionManager(expenseDao, categoryDao)
    }

    private fun createExpense(
        id: Int = 0,
        amount: Double = 100.0,
        merchant: String = "Test Merchant",
        dateMillis: Long = System.currentTimeMillis(),
        type: String = "Debit",
        categoryId: String? = null,
        tag: String? = null
    ): Expense = Expense(
        id = id,
        amount = amount,
        merchant = merchant,
        dateMillis = dateMillis,
        type = type,
        categoryId = categoryId,
        tag = tag
    )

    // ==========================================
    // 1. Entity Definition & Built-in Categories
    // ==========================================

    @Test
    fun testBuiltInCategoriesCount() {
        assertEquals("Should have exactly 12 built-in categories", 12, BuiltInCategories.ALL.size)
    }

    @Test
    fun testBuiltInCategoriesProperties() {
        BuiltInCategories.ALL.forEach { category ->
            assertTrue("Built-in category ${category.name} must have isSystem = true", category.isSystem)
            assertFalse("Built-in category ${category.name} must not be archived initially", category.isArchived)
            assertTrue("Built-in category ID must not be blank", category.id.isNotBlank())
            assertTrue("Built-in category name must not be blank", category.name.isNotBlank())
            assertTrue("Built-in category icon key must be valid", CategoryVisuals.isValidIconKey(category.iconKey))
            assertTrue("Built-in category color key must be valid", CategoryVisuals.isValidColorKey(category.colorKey))
        }
    }

    @Test
    fun testBuiltInCategoriesSeeding() = runBlocking {
        assertEquals(0, categoryDao.count())
        categoryManager.ensureBuiltInCategoriesSeeded()
        assertEquals(12, categoryDao.count())

        // Re-seeding must be idempotent
        categoryManager.ensureBuiltInCategoriesSeeded()
        assertEquals(12, categoryDao.count())
    }

    @Test
    fun testBuiltInCategoriesIdsAreStable() {
        val expectedIds = listOf(
            "food_dining", "shopping", "transport", "bills_utilities",
            "entertainment", "healthcare", "travel", "education",
            "transfer", "cash_withdrawal", "income", "other"
        )
        val actualIds = BuiltInCategories.ALL.map { it.id }
        assertEquals(expectedIds.sorted(), actualIds.sorted())
    }

    // ==========================================
    // 2. Custom Category Creation
    // ==========================================

    @Test
    fun testCreateCustomCategorySuccess() = runBlocking {
        val result = categoryManager.createCategory(
            name = "Groceries",
            iconKey = "local_grocery_store",
            colorKey = "green"
        )

        assertTrue(result.isSuccess)
        val category = result.getOrThrow()

        assertTrue("Custom category ID must start with custom_", category.id.startsWith("custom_"))
        assertEquals("Groceries", category.name)
        assertEquals("local_grocery_store", category.iconKey)
        assertEquals("green", category.colorKey)
        assertFalse("Custom category must have isSystem = false", category.isSystem)
        assertFalse("Custom category must not be archived initially", category.isArchived)
        assertEquals(1, categoryDao.count())
    }

    @Test
    fun testCreateCustomCategoryAutoTrimWhitespace() = runBlocking {
        val result = categoryManager.createCategory(
            name = "   Coffee & Snacks   ",
            iconKey = "coffee",
            colorKey = "brown"
        )

        assertTrue(result.isSuccess)
        val category = result.getOrThrow()
        assertEquals("Coffee & Snacks", category.name)
    }

    @Test
    fun testCreateCustomCategoryFallbackInvalidVisuals() = runBlocking {
        val result = categoryManager.createCategory(
            name = "Hobbies",
            iconKey = "invalid_icon_xyz",
            colorKey = "invalid_color_xyz"
        )

        assertTrue(result.isSuccess)
        val category = result.getOrThrow()
        assertEquals("category", category.iconKey)
        assertEquals("default", category.colorKey)
    }

    // ==========================================
    // 3. Validation Rules
    // ==========================================

    @Test
    fun testValidationEmptyOrBlankName() = runBlocking {
        val emptyResult = categoryManager.createCategory("", "restaurant", "orange")
        assertTrue(emptyResult.isFailure)
        assertTrue(emptyResult.exceptionOrNull()?.message?.contains("empty", ignoreCase = true) == true)

        val blankResult = categoryManager.createCategory("    ", "restaurant", "orange")
        assertTrue(blankResult.isFailure)
        assertTrue(blankResult.exceptionOrNull()?.message?.contains("empty", ignoreCase = true) == true)
    }

    @Test
    fun testValidationNameExceeds30Chars() = runBlocking {
        val longName = "A".repeat(31)
        val result = categoryManager.createCategory(longName, "restaurant", "orange")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("30 characters", ignoreCase = true) == true)
    }

    @Test
    fun testValidationExactDuplicateNameRejected() = runBlocking {
        categoryManager.createCategory("Fitness", "fitness_center", "teal")
        val duplicateResult = categoryManager.createCategory("Fitness", "fitness_center", "teal")

        assertTrue(duplicateResult.isFailure)
        assertTrue(duplicateResult.exceptionOrNull()?.message?.contains("already exists", ignoreCase = true) == true)
    }

    @Test
    fun testValidationCaseInsensitiveDuplicateRejected() = runBlocking {
        categoryManager.createCategory("Pets & Animals", "pets", "amber")
        val duplicateResult = categoryManager.createCategory("pets & animals", "pets", "amber")

        assertTrue(duplicateResult.isFailure)
        assertTrue(duplicateResult.exceptionOrNull()?.message?.contains("already exists", ignoreCase = true) == true)
    }

    @Test
    fun testValidationDuplicateWithBuiltInCategoriesRejected() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()
        val duplicateResult = categoryManager.createCategory("shopping", "shopping_bag", "purple")

        assertTrue(duplicateResult.isFailure)
        assertTrue(duplicateResult.exceptionOrNull()?.message?.contains("already exists", ignoreCase = true) == true)
    }

    // ==========================================
    // 4. Update & Rename Categories
    // ==========================================

    @Test
    fun testUpdateCustomCategoryNameAndVisuals() = runBlocking {
        val created = categoryManager.createCategory("Books", "school", "blue").getOrThrow()

        val updatedResult = categoryManager.updateCategory(
            id = created.id,
            name = "Books & Media",
            iconKey = "movie",
            colorKey = "purple"
        )

        assertTrue(updatedResult.isSuccess)
        val updated = updatedResult.getOrThrow()
        assertEquals("Books & Media", updated.name)
        assertEquals("movie", updated.iconKey)
        assertEquals("purple", updated.colorKey)
        assertEquals(created.id, updated.id)
    }

    @Test
    fun testUpdateCategoryPreserveSameName() = runBlocking {
        val created = categoryManager.createCategory("Tech", "work", "cyan").getOrThrow()

        // Updating visuals while keeping the same name should succeed (not treated as duplicate)
        val updatedResult = categoryManager.updateCategory(
            id = created.id,
            name = "Tech",
            iconKey = "work",
            colorKey = "indigo"
        )

        assertTrue(updatedResult.isSuccess)
        assertEquals("indigo", updatedResult.getOrThrow().colorKey)
    }

    @Test
    fun testUpdateCategoryRenameToExistingNameFails() = runBlocking {
        val cat1 = categoryManager.createCategory("Music", "movie", "pink").getOrThrow()
        val cat2 = categoryManager.createCategory("Podcasts", "movie", "red").getOrThrow()

        val renameResult = categoryManager.updateCategory(
            id = cat2.id,
            name = "Music",
            iconKey = cat2.iconKey,
            colorKey = cat2.colorKey
        )

        assertTrue(renameResult.isFailure)
        assertTrue(renameResult.exceptionOrNull()?.message?.contains("already exists", ignoreCase = true) == true)
    }

    @Test
    fun testUpdateSystemCategoryVisualsPreservesIdAndSystemFlag() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()
        val foodCat = categoryDao.getById("food_dining")!!

        val updateResult = categoryManager.updateCategory(
            id = foodCat.id,
            name = foodCat.name,
            iconKey = "restaurant",
            colorKey = "red"
        )

        assertTrue(updateResult.isSuccess)
        val updated = updateResult.getOrThrow()
        assertEquals("food_dining", updated.id)
        assertTrue(updated.isSystem)
        assertEquals("red", updated.colorKey)
    }

    // ==========================================
    // 5. Archive & Restore
    // ==========================================

    @Test
    fun testArchiveAndRestoreCategory() = runBlocking {
        val created = categoryManager.createCategory("Gardening", "home", "green").getOrThrow()
        assertFalse(created.isArchived)

        // Archive
        val archiveResult = categoryManager.archiveCategory(created.id)
        assertTrue(archiveResult.isSuccess)
        val archived = archiveResult.getOrThrow()
        assertTrue(archived.isArchived)

        // Verify query splits
        val activeList = categoryManager.getActiveCategories()
        val archivedList = categoryDao.getArchived()
        assertFalse(activeList.any { it.id == created.id })
        assertTrue(archivedList.any { it.id == created.id })

        // Restore
        val restoreResult = categoryManager.restoreCategory(created.id)
        assertTrue(restoreResult.isSuccess)
        val restored = restoreResult.getOrThrow()
        assertFalse(restored.isArchived)

        val activeAfterRestore = categoryManager.getActiveCategories()
        assertTrue(activeAfterRestore.any { it.id == created.id })
    }

    @Test
    fun testArchiveNonExistentCategoryFails() = runBlocking {
        val result = categoryManager.archiveCategory("non_existent_id")
        assertTrue(result.isFailure)
    }

    // ==========================================
    // 6. Delete Safety & Invariants
    // ==========================================

    @Test
    fun testDeleteSystemCategoryDisallowed() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()
        val result = categoryManager.deleteCategory("food_dining")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("built-in", ignoreCase = true) == true)
        assertNotNull(categoryDao.getById("food_dining"))
    }

    @Test
    fun testDeleteCustomCategoryWithNoTransactions() = runBlocking {
        val created = categoryManager.createCategory("Temp", "category", "default").getOrThrow()
        val deleteResult = categoryManager.deleteCategory(created.id)

        assertTrue(deleteResult.isSuccess)
        assertEquals(0, deleteResult.getOrThrow())
        assertNull(categoryDao.getById(created.id))
    }

    @Test
    fun testDeleteCustomCategoryWithUnlinkMode() = runBlocking {
        val created = categoryManager.createCategory("Subscriptions", "receipt", "purple").getOrThrow()

        // Insert expenses with this category
        val exp1 = createExpense(id = 1, amount = 199.0, type = "Debit", categoryId = created.id, tag = created.name)
        val exp2 = createExpense(id = 2, amount = 499.0, type = "Debit", categoryId = created.id, tag = created.name)
        expenseDao.insert(exp1)
        expenseDao.insert(exp2)

        // Delete with reassignToCategoryId = null (unlink mode)
        val deleteResult = categoryManager.deleteCategory(created.id, reassignToCategoryId = null)
        assertTrue(deleteResult.isSuccess)
        assertEquals(2, deleteResult.getOrThrow())

        // Category deleted
        assertNull(categoryDao.getById(created.id))

        // Expenses unlinked (categoryId = null, tag = null)
        val updatedExp1 = expenseDao.getExpenseById(1)!!
        val updatedExp2 = expenseDao.getExpenseById(2)!!
        assertNull(updatedExp1.categoryId)
        assertNull(updatedExp1.tag)
        assertNull(updatedExp2.categoryId)
        assertNull(updatedExp2.tag)
    }

    @Test
    fun testDeleteCustomCategoryWithReassignMode() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()
        val targetCat = categoryDao.getById("entertainment")!!
        val created = categoryManager.createCategory("Movies", "movie", "pink").getOrThrow()

        val exp1 = createExpense(id = 1, amount = 300.0, type = "Debit", categoryId = created.id, tag = created.name)
        expenseDao.insert(exp1)

        val deleteResult = categoryManager.deleteCategory(created.id, reassignToCategoryId = targetCat.id)
        assertTrue(deleteResult.isSuccess)
        assertEquals(1, deleteResult.getOrThrow())

        // Category deleted
        assertNull(categoryDao.getById(created.id))

        // Expense reassigned to target category
        val updatedExp1 = expenseDao.getExpenseById(1)!!
        assertEquals("entertainment", updatedExp1.categoryId)
        assertEquals(targetCat.name, updatedExp1.tag)
    }

    @Test
    fun testDeleteCustomCategoryCannotReassignToSelf() = runBlocking {
        val created = categoryManager.createCategory("SelfTest", "category", "default").getOrThrow()
        val result = categoryManager.deleteCategory(created.id, reassignToCategoryId = created.id)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("itself", ignoreCase = true) == true)
        assertNotNull(categoryDao.getById(created.id))
    }

    @Test
    fun testDeleteCustomCategoryCannotReassignToNonExistentTarget() = runBlocking {
        val created = categoryManager.createCategory("InvalidTargetTest", "category", "default").getOrThrow()
        val result = categoryManager.deleteCategory(created.id, reassignToCategoryId = "ghost_target")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not found", ignoreCase = true) == true)
        assertNotNull(categoryDao.getById(created.id))
    }

    // ==========================================
    // 7. Merge Categories
    // ==========================================

    @Test
    fun testMergeCustomCategoryIntoTarget() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()
        val targetCat = categoryDao.getById("food_dining")!!
        val sourceCat = categoryManager.createCategory("Fast Food", "restaurant", "orange").getOrThrow()

        val exp1 = createExpense(id = 1, amount = 150.0, type = "Debit", categoryId = sourceCat.id, tag = sourceCat.name)
        val exp2 = createExpense(id = 2, amount = 250.0, type = "Debit", categoryId = sourceCat.id, tag = sourceCat.name)
        expenseDao.insert(exp1)
        expenseDao.insert(exp2)

        val mergeResult = categoryManager.mergeCategories(sourceCat.id, targetCat.id)
        assertTrue(mergeResult.isSuccess)
        assertEquals(2, mergeResult.getOrThrow())

        // Source custom category was deleted
        assertNull(categoryDao.getById(sourceCat.id))

        // All expenses moved to target
        val updated1 = expenseDao.getExpenseById(1)!!
        val updated2 = expenseDao.getExpenseById(2)!!
        assertEquals(targetCat.id, updated1.categoryId)
        assertEquals(targetCat.name, updated1.tag)
        assertEquals(targetCat.id, updated2.categoryId)
        assertEquals(targetCat.name, updated2.tag)
    }

    @Test
    fun testMergeSystemCategoryIntoTargetArchivesSource() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()
        val sourceCat = categoryDao.getById("cash_withdrawal")!!
        val targetCat = categoryDao.getById("transfer")!!

        val exp = createExpense(id = 10, amount = 1000.0, type = "Debit", categoryId = sourceCat.id, tag = sourceCat.name)
        expenseDao.insert(exp)

        val mergeResult = categoryManager.mergeCategories(sourceCat.id, targetCat.id)
        assertTrue(mergeResult.isSuccess)
        assertEquals(1, mergeResult.getOrThrow())

        // System source category MUST NOT be deleted; it must be ARCHIVED!
        val preservedSource = categoryDao.getById(sourceCat.id)
        assertNotNull(preservedSource)
        assertTrue("Source system category must be archived after merge", preservedSource!!.isArchived)

        // Expense moved to target
        val updatedExp = expenseDao.getExpenseById(10)!!
        assertEquals(targetCat.id, updatedExp.categoryId)
        assertEquals(targetCat.name, updatedExp.tag)
    }

    @Test
    fun testMergeCannotMergeIntoSelf() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()
        val result = categoryManager.mergeCategories("food_dining", "food_dining")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("itself", ignoreCase = true) == true)
    }

    @Test
    fun testMergeInvalidSourceOrTargetFails() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()
        val res1 = categoryManager.mergeCategories("invalid_source", "food_dining")
        assertTrue(res1.isFailure)

        val res2 = categoryManager.mergeCategories("food_dining", "invalid_target")
        assertTrue(res2.isFailure)
    }

    // ==========================================
    // 8. Transaction Category Assignment
    // ==========================================

    @Test
    fun testAssignCategorySuccess() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()
        val exp = createExpense(id = 1, amount = 100.0, type = "Debit", merchant = "Store")
        expenseDao.insert(exp)

        val assignResult = categoryManager.assignCategory(expenseId = 1, categoryId = "shopping")
        assertTrue(assignResult.isSuccess)

        val updated = expenseDao.getExpenseById(1)!!
        assertEquals("shopping", updated.categoryId)
        assertEquals("Shopping", updated.tag)
    }

    @Test
    fun testAssignCategoryUnlink() = runBlocking {
        val exp = createExpense(id = 2, amount = 100.0, type = "Debit", categoryId = "shopping", tag = "Shopping")
        expenseDao.insert(exp)

        val unlinkResult = categoryManager.assignCategory(expenseId = 2, categoryId = null)
        assertTrue(unlinkResult.isSuccess)

        val updated = expenseDao.getExpenseById(2)!!
        assertNull(updated.categoryId)
        assertNull(updated.tag)
    }

    @Test
    fun testBulkAssignCategory() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()
        expenseDao.insert(createExpense(id = 1, amount = 10.0, type = "Debit"))
        expenseDao.insert(createExpense(id = 2, amount = 20.0, type = "Debit"))
        expenseDao.insert(createExpense(id = 3, amount = 30.0, type = "Debit"))

        val bulkResult = categoryManager.bulkAssignCategory(listOf(1, 2), "transport")
        assertTrue(bulkResult.isSuccess)
        assertEquals(2, bulkResult.getOrThrow())

        val exp1 = expenseDao.getExpenseById(1)!!
        val exp2 = expenseDao.getExpenseById(2)!!
        val exp3 = expenseDao.getExpenseById(3)!!

        assertEquals("transport", exp1.categoryId)
        assertEquals("Transport", exp1.tag)
        assertEquals("transport", exp2.categoryId)
        assertEquals("Transport", exp2.tag)
        assertNull(exp3.categoryId)
    }

    // ==========================================
    // 9. Legacy Tag Mapping & Backward Compatibility
    // ==========================================

    @Test
    fun testLegacyMappingFindExactAndAliases() {
        assertEquals("food_dining", BuiltInCategories.findLegacyMapping("Food & Dining")?.id)
        assertEquals("food_dining", BuiltInCategories.findLegacyMapping("food")?.id)
        assertEquals("food_dining", BuiltInCategories.findLegacyMapping("dining")?.id)
        assertEquals("shopping", BuiltInCategories.findLegacyMapping("Shopping")?.id)
        assertEquals("bills_utilities", BuiltInCategories.findLegacyMapping("Bills & Utilities")?.id)
        assertEquals("bills_utilities", BuiltInCategories.findLegacyMapping("bills")?.id)
        assertEquals("transfer", BuiltInCategories.findLegacyMapping("Transfers")?.id)
        assertEquals("income", BuiltInCategories.findLegacyMapping("Salary")?.id)
        assertNull(BuiltInCategories.findLegacyMapping(null))
        assertNull(BuiltInCategories.findLegacyMapping(""))
        assertNull(BuiltInCategories.findLegacyMapping("UnknownCategory123"))
    }

    @Test
    fun testExpenseCategoryGetterSetterSync() {
        val exp = createExpense(id = 1, amount = 50.0, type = "Debit")

        // Setter updates both tag and categoryId
        exp.category = "Shopping"
        assertEquals("Shopping", exp.tag)
        assertEquals("shopping", exp.categoryId)
        assertEquals("Shopping", exp.category)

        // Setting null clears both
        exp.category = null
        assertNull(exp.tag)
        assertNull(exp.categoryId)
        assertNull(exp.category)
    }

    // ==========================================
    // 10. ExpenseDao Category Queries
    // ==========================================

    @Test
    fun testExpenseDaoCategoryQueries() = runBlocking {
        expenseDao.insert(createExpense(id = 1, amount = 10.0, type = "Debit", categoryId = "cat_a", tag = "Cat A"))
        expenseDao.insert(createExpense(id = 2, amount = 20.0, type = "Debit", categoryId = "cat_a", tag = "Cat A"))
        expenseDao.insert(createExpense(id = 3, amount = 30.0, type = "Debit", categoryId = null, tag = null))

        assertEquals(2, expenseDao.countByCategoryId("cat_a"))
        assertEquals(0, expenseDao.countByCategoryId("cat_b"))

        val catAExpenses = expenseDao.getExpensesByCategoryId("cat_a")
        assertEquals(2, catAExpenses.size)

        val uncategorized = expenseDao.getUncategorizedExpenses()
        assertEquals(1, uncategorized.size)
        assertEquals(3, uncategorized.first().id)
    }

    // ==========================================
    // 11. TransactionManager Category Filtering
    // ==========================================

    @Test
    fun testTransactionManagerFilterByCategory() {
        val exp1 = createExpense(id = 1, amount = 100.0, type = "Debit", categoryId = "food_dining", tag = "Food & Dining")
        val exp2 = createExpense(id = 2, amount = 200.0, type = "Debit", categoryId = "shopping", tag = "Shopping")
        val exp3 = createExpense(id = 3, amount = 50.0, type = "Debit", categoryId = null, tag = null)
        val all = listOf(exp1, exp2, exp3)

        // Filter by category ID
        val filterById = TransactionFilter(category = "food_dining")
        val resultById = transactionManager.filterAndSort(all, filterById)
        assertEquals(1, resultById.size)
        assertEquals(1, resultById.first().id)

        // Filter by category name
        val filterByName = TransactionFilter(category = "Shopping")
        val resultByName = transactionManager.filterAndSort(all, filterByName)
        assertEquals(1, resultByName.size)
        assertEquals(2, resultByName.first().id)

        // Filter by Uncategorized
        val filterUncategorized = TransactionFilter(category = "Uncategorized")
        val resultUncategorized = transactionManager.filterAndSort(all, filterUncategorized)
        assertEquals(1, resultUncategorized.size)
        assertEquals(3, resultUncategorized.first().id)
    }

    // ==========================================
    // 12. Category Reordering
    // ==========================================

    @Test
    fun testReorderCategories() = runBlocking {
        val cat1 = categoryManager.createCategory("Cat 1", "category", "default").getOrThrow()
        val cat2 = categoryManager.createCategory("Cat 2", "category", "default").getOrThrow()

        // Reorder
        val reorderResult = categoryManager.reorderCategories(listOf(cat2.id, cat1.id))
        assertTrue(reorderResult.isSuccess)

        val sorted = categoryManager.getAllCategories()
        assertEquals(cat2.id, sorted[0].id)
        assertEquals(cat1.id, sorted[1].id)
    }

    // ==========================================
    // 13. Additional Edge Cases & Verifications
    // ==========================================

    @Test
    fun testVisualKeysValidityChecks() {
        CategoryVisuals.AVAILABLE_ICON_KEYS.forEach { key ->
            assertTrue("Key $key must be recognized as valid icon", CategoryVisuals.isValidIconKey(key))
            assertNotNull(CategoryVisuals.getIcon(key))
        }
        assertFalse(CategoryVisuals.isValidIconKey("unknown_bogus_icon"))
        assertFalse(CategoryVisuals.isValidIconKey(null))
        assertFalse(CategoryVisuals.isValidIconKey(""))

        CategoryVisuals.AVAILABLE_COLOR_KEYS.forEach { key ->
            assertTrue("Key $key must be recognized as valid color", CategoryVisuals.isValidColorKey(key))
            assertNotNull(CategoryVisuals.getColor(key))
        }
        assertFalse(CategoryVisuals.isValidColorKey("unknown_bogus_color"))
        assertFalse(CategoryVisuals.isValidColorKey(null))
        assertFalse(CategoryVisuals.isValidColorKey(""))
    }

    @Test
    fun testIsSystemIdAndIsSystemName() {
        assertTrue(BuiltInCategories.isSystemId("food_dining"))
        assertTrue(BuiltInCategories.isSystemId("shopping"))
        assertFalse(BuiltInCategories.isSystemId("custom_12345"))
        assertFalse(BuiltInCategories.isSystemId(null))

        assertTrue(BuiltInCategories.isSystemName("Food & Dining"))
        assertTrue(BuiltInCategories.isSystemName("food & dining"))
        assertTrue(BuiltInCategories.isSystemName("Shopping"))
        assertFalse(BuiltInCategories.isSystemName("Custom Category"))
        assertFalse(BuiltInCategories.isSystemName(null))
    }

    @Test
    fun testTransactionManagerUpdateTransactionCategory() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()
        val exp = createExpense(id = 1, amount = 100.0, type = "Debit", categoryId = null, tag = null)
        expenseDao.insert(exp)

        val updateResult = transactionManager.updateTransaction(
            expenseId = 1,
            merchant = "Fresh Mart",
            category = "Food & Dining",
            note = "Groceries"
        )
        assertTrue(updateResult.isSuccess)
        val updated = updateResult.getOrThrow()
        assertEquals("food_dining", updated.categoryId)
        assertEquals("Food & Dining", updated.tag)
        assertEquals("Fresh Mart", updated.merchant)
        assertEquals("Groceries", updated.note)
    }

    @Test
    fun testTransactionManagerUpdateTransactionCategoryToUncategorized() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()
        val exp = createExpense(id = 2, amount = 50.0, type = "Debit", categoryId = "shopping", tag = "Shopping")
        expenseDao.insert(exp)

        val updateResult = transactionManager.updateTransaction(
            expenseId = 2,
            merchant = "Store",
            category = "Uncategorized",
            note = null
        )

        assertTrue(updateResult.isSuccess)
        val updated = updateResult.getOrThrow()
        assertNull(updated.categoryId)
        assertNull(updated.tag)
    }

    @Test
    fun testMergeWithZeroExpenses() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()
        val cat1 = categoryManager.createCategory("Empty 1", "category", "default").getOrThrow()
        val cat2 = categoryManager.createCategory("Empty 2", "category", "default").getOrThrow()

        val mergeResult = categoryManager.mergeCategories(cat1.id, cat2.id)
        assertTrue(mergeResult.isSuccess)
        assertEquals(0, mergeResult.getOrThrow())
        assertNull(categoryDao.getById(cat1.id))
        assertNotNull(categoryDao.getById(cat2.id))
    }

    @Test
    fun testMultipleCustomCategoriesWithSameVisuals() = runBlocking {
        val cat1 = categoryManager.createCategory("Dinner Out", "restaurant", "orange").getOrThrow()
        val cat2 = categoryManager.createCategory("Lunch Snacks", "restaurant", "orange").getOrThrow()

        assertNotEquals(cat1.id, cat2.id)
        assertEquals(cat1.iconKey, cat2.iconKey)
        assertEquals(cat1.colorKey, cat2.colorKey)
        assertNotNull(categoryDao.getById(cat1.id))
        assertNotNull(categoryDao.getById(cat2.id))
    }

    @Test
    fun testExpenseCategorySetterCustomName() {
        val exp = createExpense(id = 1, amount = 50.0, type = "Debit")
        exp.category = "Freelance"
        assertEquals("Freelance", exp.tag)
        assertEquals("Freelance", exp.category)
    }

    @Test
    fun testCategorySortOrderIncremental() = runBlocking {
        val cat1 = categoryManager.createCategory("First Cat", "category", "default").getOrThrow()
        val cat2 = categoryManager.createCategory("Second Cat", "category", "default").getOrThrow()
        assertTrue("Second category sort order should be greater than first", cat2.sortOrder > cat1.sortOrder)
    }

    @Test
    fun testCategoryCountTransactionsViaManager() = runBlocking {
        val cat = categoryManager.createCategory("Travels", "flight", "cyan").getOrThrow()
        assertEquals(0, categoryManager.countTransactionsForCategory(cat.id))

        expenseDao.insert(createExpense(id = 1, amount = 500.0, type = "Debit", categoryId = cat.id, tag = cat.name))
        assertEquals(1, categoryManager.countTransactionsForCategory(cat.id))
    }

    @Test
    fun testBulkAssignNonExistentCategoryFails() = runBlocking {
        val exp = createExpense(id = 1, amount = 10.0, type = "Debit")
        expenseDao.insert(exp)

        val result = categoryManager.bulkAssignCategory(listOf(1), "non_existent_category")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not found", ignoreCase = true) == true)
    }

    @Test
    fun testArchiveCategoryPreservesSortOrderAndVisuals() = runBlocking {
        val created = categoryManager.createCategory("Gym", "fitness_center", "red").getOrThrow()
        val initialOrder = created.sortOrder

        val archived = categoryManager.archiveCategory(created.id).getOrThrow()
        assertTrue(archived.isArchived)
        assertEquals("fitness_center", archived.iconKey)
        assertEquals("red", archived.colorKey)
        assertEquals(initialOrder, archived.sortOrder)
    }

    @Test
    fun testGetAllFlowAndGetActiveFlowReactivity() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()
        val activeFlowFirst = categoryManager.getActiveCategoriesFlow().first()
        assertEquals(12, activeFlowFirst.size)

        // Add 1 custom category
        val custom = categoryManager.createCategory("Crypto", "savings", "green").getOrThrow()
        val activeFlowSecond = categoryManager.getActiveCategoriesFlow().first()
        assertEquals(13, activeFlowSecond.size)

        // Archive it
        categoryManager.archiveCategory(custom.id)
        val activeFlowThird = categoryManager.getActiveCategoriesFlow().first()
        assertEquals(12, activeFlowThird.size)
        val archivedFlow = categoryDao.getArchivedFlow().first()
        assertEquals(1, archivedFlow.size)
    }
}
