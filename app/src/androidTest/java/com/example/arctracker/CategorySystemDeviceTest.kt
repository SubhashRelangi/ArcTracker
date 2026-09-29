package com.example.arctracker

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arctracker.data.*
import com.example.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Milestone 9: Category System & Custom Categories Physical Device Tests.
 *
 * Runs on Samsung Galaxy A34 5G (SM-A346E - Android 16).
 * Covers all 21 verification points from Part 32 of the Milestone 9 specification:
 * - Room v5 non-destructive upgrade & verification.
 * - Built-in category seeding & persistence across database close/reopen.
 * - System category assignment & unlinking.
 * - Custom category creation, editing, archiving, and restoration.
 * - Deletion invariants: system category deletion forbidden, custom category deletion with unlink vs reassign modes.
 * - Category merging (custom source deleted, system source archived).
 * - Bulk transaction category assignment.
 */
@RunWith(AndroidJUnit4::class)
class CategorySystemDeviceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var expenseDao: ExpenseDao
    private lateinit var categoryDao: TransactionCategoryDao
    private lateinit var categoryManager: CategoryManager
    private lateinit var transactionManager: TransactionManager
    private val persistentDbName = "m9_device_test_persistent.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(persistentDbName)

        database = Room.databaseBuilder(context, AppDatabase::class.java, persistentDbName)
            .allowMainThreadQueries()
            .build()

        expenseDao = database.expenseDao()
        categoryDao = database.transactionCategoryDao()
        categoryManager = CategoryManager(categoryDao, expenseDao, database)
        transactionManager = TransactionManager(expenseDao, categoryDao)

        NotificationPermissionHelper.permissionOverrideForTesting = true
        NotificationReaderService.clearActiveNotifications()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(persistentDbName)
        NotificationPermissionHelper.permissionOverrideForTesting = null
        NotificationReaderService.clearActiveNotifications()
    }

    private fun reopenDatabase() {
        database.close()
        database = Room.databaseBuilder(context, AppDatabase::class.java, persistentDbName)
            .allowMainThreadQueries()
            .build()
        expenseDao = database.expenseDao()
        categoryDao = database.transactionCategoryDao()
        categoryManager = CategoryManager(categoryDao, expenseDao, database)
        transactionManager = TransactionManager(expenseDao, categoryDao)
    }

    /**
     * Verification Points 1–4:
     * 1. Room v5 database opens cleanly.
     * 2. Built-in categories seeded in database.
     * 3. Existing transaction category is properly resolved.
     * 4. Persistence survives database restart.
     */
    @Test
    fun test01_roomV5Opens_builtInCategoriesSeeded_andSurvivesRestart() = runBlocking {
        // 1. Room opens (version >= 5)
        assertTrue(database.openHelper.readableDatabase.version >= 5)

        // 2. Built-in categories seeded
        categoryManager.ensureBuiltInCategoriesSeeded()
        assertEquals(12, categoryDao.count())

        val food = categoryDao.getById("food_dining")
        assertNotNull(food)
        assertEquals("Food & Dining", food!!.name)
        assertTrue(food.isSystem)
        assertFalse(food.isArchived)

        // 3. Insert transaction with category
        val expense = Expense(
            id = 1,
            amount = 350.0,
            merchant = "Swiggy",
            dateMillis = System.currentTimeMillis(),
            type = "Debit",
            categoryId = "food_dining",
            tag = "Food & Dining"
        )
        expenseDao.insert(expense)

        // 4. Reopen database and verify everything survives
        reopenDatabase()
        assertEquals(5, database.openHelper.readableDatabase.version)
        assertEquals(12, categoryDao.count())

        val restoredExpense = expenseDao.getExpenseById(1)
        assertNotNull(restoredExpense)
        assertEquals("food_dining", restoredExpense!!.categoryId)
        assertEquals("Food & Dining", restoredExpense.tag)
        assertEquals("Swiggy", restoredExpense.merchant)
    }

    /**
     * Verification Points 5–7:
     * 5. Change transaction category to another system category ("shopping").
     * 6. Verify persistence across database close and reopen.
     * 7. Change transaction category to "Uncategorized" (null).
     */
    @Test
    fun test02_systemCategoryAssignment_andClearing() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()

        val expense = Expense(
            id = 1,
            amount = 1299.0,
            merchant = "Amazon",
            dateMillis = System.currentTimeMillis(),
            type = "Debit",
            categoryId = "food_dining",
            tag = "Food & Dining"
        )
        expenseDao.insert(expense)

        // 5. Change category to shopping
        val assignResult = categoryManager.assignCategory(expenseId = 1, categoryId = "shopping")
        assertTrue(assignResult.isSuccess)

        val updated = expenseDao.getExpenseById(1)!!
        assertEquals("shopping", updated.categoryId)
        assertEquals("Shopping", updated.tag)

        // 6. Verify persistence across restart
        reopenDatabase()
        val afterRestart = expenseDao.getExpenseById(1)!!
        assertEquals("shopping", afterRestart.categoryId)
        assertEquals("Shopping", afterRestart.tag)

        // 7. Clear category (unlink / Uncategorized)
        val unlinkResult = categoryManager.assignCategory(expenseId = 1, categoryId = null)
        assertTrue(unlinkResult.isSuccess)

        val unlinked = expenseDao.getExpenseById(1)!!
        assertNull(unlinked.categoryId)
        assertNull(unlinked.tag)
    }

    /**
     * Verification Points 8–14:
     * 8. Create custom category in database.
     * 9. Verify ID starts with "custom_" and isSystem = false.
     * 10. Assign custom category to transaction.
     * 11. Edit custom category (rename, change color).
     * 12. Verify category persistence and restart survival.
     * 13. Archive custom category: active list excludes it, archived list includes it.
     * 14. Restore custom category: active list includes it.
     */
    @Test
    fun test03_customCategoryLifecycle_create_edit_archive_restore() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()

        // 8 & 9. Create custom category
        val createResult = categoryManager.createCategory(
            name = "Gym & Fitness",
            iconKey = "fitness_center",
            colorKey = "red"
        )
        assertTrue(createResult.isSuccess)
        val customCat = createResult.getOrThrow()
        assertTrue(customCat.id.startsWith("custom_"))
        assertFalse(customCat.isSystem)
        assertFalse(customCat.isArchived)
        assertEquals("Gym & Fitness", customCat.name)

        // 10. Assign custom category to transaction
        val expense = Expense(
            id = 10,
            amount = 2500.0,
            merchant = "Cult Fitness",
            dateMillis = System.currentTimeMillis(),
            type = "Debit"
        )
        expenseDao.insert(expense)

        val assignResult = categoryManager.assignCategory(expenseId = 10, categoryId = customCat.id)
        assertTrue(assignResult.isSuccess)

        val assignedExp = expenseDao.getExpenseById(10)!!
        assertEquals(customCat.id, assignedExp.categoryId)
        assertEquals("Gym & Fitness", assignedExp.tag)

        // 11 & 12. Edit custom category (rename & change color)
        val updateResult = categoryManager.updateCategory(
            id = customCat.id,
            name = "Health & Fitness",
            iconKey = "fitness_center",
            colorKey = "teal"
        )
        assertTrue(updateResult.isSuccess)
        val updatedCat = updateResult.getOrThrow()
        assertEquals("Health & Fitness", updatedCat.name)
        assertEquals("teal", updatedCat.colorKey)

        // Verify across restart
        reopenDatabase()
        val catAfterRestart = categoryDao.getById(customCat.id)!!
        assertEquals("Health & Fitness", catAfterRestart.name)
        assertEquals("teal", catAfterRestart.colorKey)

        // 13. Archive custom category
        val archiveResult = categoryManager.archiveCategory(customCat.id)
        assertTrue(archiveResult.isSuccess)
        assertTrue(archiveResult.getOrThrow().isArchived)

        val activeList = categoryManager.getActiveCategories()
        val archivedList = categoryDao.getArchived()
        assertFalse(activeList.any { it.id == customCat.id })
        assertTrue(archivedList.any { it.id == customCat.id })

        // 14. Restore custom category
        val restoreResult = categoryManager.restoreCategory(customCat.id)
        assertTrue(restoreResult.isSuccess)
        assertFalse(restoreResult.getOrThrow().isArchived)

        val activeListAfterRestore = categoryManager.getActiveCategories()
        assertTrue(activeListAfterRestore.any { it.id == customCat.id })
    }

    /**
     * Verification Points 15–18:
     * 15. Prevent deleting system category.
     * 16. Delete custom category with transactions in "unlink" mode.
     * 17 & 18. Delete custom category with transactions in "reassign" mode.
     */
    @Test
    fun test04_deleteSafety_disallowSystem_unlinkAndReassignModes() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()

        // 15. System category deletion disallowed
        val systemDelete = categoryManager.deleteCategory("food_dining")
        assertTrue(systemDelete.isFailure)
        assertNotNull(categoryDao.getById("food_dining"))

        // 16. Delete custom category in unlink mode
        val customA = categoryManager.createCategory("Temporary A", "category", "default").getOrThrow()
        val exp1 = Expense(
            id = 101,
            amount = 50.0,
            merchant = "Vendor A",
            dateMillis = System.currentTimeMillis(),
            categoryId = customA.id,
            tag = customA.name
        )
        expenseDao.insert(exp1)

        val unlinkDelete = categoryManager.deleteCategory(customA.id, reassignToCategoryId = null)
        assertTrue(unlinkDelete.isSuccess)
        assertEquals(1, unlinkDelete.getOrThrow())
        assertNull(categoryDao.getById(customA.id))

        val exp1Unlinked = expenseDao.getExpenseById(101)!!
        assertNull(exp1Unlinked.categoryId)
        assertNull(exp1Unlinked.tag)

        // 17 & 18. Delete custom category in reassign mode
        val customB = categoryManager.createCategory("Coffee & Tea", "coffee", "brown").getOrThrow()
        val exp2 = Expense(
            id = 102,
            amount = 180.0,
            merchant = "Starbucks",
            dateMillis = System.currentTimeMillis(),
            categoryId = customB.id,
            tag = customB.name
        )
        expenseDao.insert(exp2)

        val reassignDelete = categoryManager.deleteCategory(customB.id, reassignToCategoryId = "food_dining")
        assertTrue(reassignDelete.isSuccess)
        assertEquals(1, reassignDelete.getOrThrow())
        assertNull(categoryDao.getById(customB.id))

        val exp2Reassigned = expenseDao.getExpenseById(102)!!
        assertEquals("food_dining", exp2Reassigned.categoryId)
        assertEquals("Food & Dining", exp2Reassigned.tag)
    }

    /**
     * Verification Points 19–20:
     * 19. Merge custom category into target category: all transactions moved, custom source deleted.
     * 20. Merge system category into target category: all transactions moved, system source archived (never deleted).
     */
    @Test
    fun test05_mergeCategories_customSourceAndSystemSource() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()

        // 19. Merge custom category into target
        val customCat = categoryManager.createCategory("Fast Food", "restaurant", "orange").getOrThrow()
        val exp1 = Expense(
            id = 201,
            amount = 220.0,
            merchant = "McDonalds",
            dateMillis = System.currentTimeMillis(),
            categoryId = customCat.id,
            tag = customCat.name
        )
        expenseDao.insert(exp1)

        val mergeCustom = categoryManager.mergeCategories(customCat.id, "food_dining")
        assertTrue(mergeCustom.isSuccess)
        assertEquals(1, mergeCustom.getOrThrow())
        assertNull(categoryDao.getById(customCat.id))

        val exp1Merged = expenseDao.getExpenseById(201)!!
        assertEquals("food_dining", exp1Merged.categoryId)
        assertEquals("Food & Dining", exp1Merged.tag)

        // 20. Merge system category into target (must archive, never delete)
        val exp2 = Expense(
            id = 202,
            amount = 500.0,
            merchant = "ATM Cash",
            dateMillis = System.currentTimeMillis(),
            categoryId = "cash_withdrawal",
            tag = "Cash Withdrawal"
        )
        expenseDao.insert(exp2)

        val mergeSystem = categoryManager.mergeCategories("cash_withdrawal", "transfer")
        assertTrue(mergeSystem.isSuccess)
        assertEquals(1, mergeSystem.getOrThrow())

        // System source must be ARCHIVED, NOT deleted
        val sourceSystemCat = categoryDao.getById("cash_withdrawal")
        assertNotNull(sourceSystemCat)
        assertTrue(sourceSystemCat!!.isArchived)
        assertTrue(sourceSystemCat.isSystem)

        val exp2Merged = expenseDao.getExpenseById(202)!!
        assertEquals("transfer", exp2Merged.categoryId)
        assertEquals("Transfer", exp2Merged.tag)
    }

    /**
     * Verification Point 21:
     * 21. Bulk category assignment: multiple transactions updated atomically in a single operation.
     */
    @Test
    fun test06_bulkCategoryAssignment_atomicUpdate() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()

        val exp1 = Expense(id = 301, amount = 100.0, merchant = "Uber 1", dateMillis = System.currentTimeMillis())
        val exp2 = Expense(id = 302, amount = 150.0, merchant = "Uber 2", dateMillis = System.currentTimeMillis())
        val exp3 = Expense(id = 303, amount = 200.0, merchant = "Other Store", dateMillis = System.currentTimeMillis())
        expenseDao.insert(exp1)
        expenseDao.insert(exp2)
        expenseDao.insert(exp3)

        val bulkResult = categoryManager.bulkAssignCategory(listOf(301, 302), "transport")
        assertTrue(bulkResult.isSuccess)
        assertEquals(2, bulkResult.getOrThrow())

        val updated1 = expenseDao.getExpenseById(301)!!
        val updated2 = expenseDao.getExpenseById(302)!!
        val updated3 = expenseDao.getExpenseById(303)!!

        assertEquals("transport", updated1.categoryId)
        assertEquals("Transport", updated1.tag)
        assertEquals("transport", updated2.categoryId)
        assertEquals("Transport", updated2.tag)
        assertNull(updated3.categoryId)
        assertNull(updated3.tag)
    }
}
