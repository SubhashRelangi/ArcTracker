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
 * Milestone 12: Historical Rule Application & Bulk Re-Categorization Physical Device Tests.
 *
 * Runs on connected physical Samsung Galaxy A34 5G (SM-A346E - Android 16).
 *
 * Verifies:
 * 1. Preview generation with real Room database entities.
 * 2. USER_ASSIGNED strict protection at SQLite and application layer.
 * 3. Atomic bulk update and cold-restart persistence across database close/reopen.
 * 4. Strict preservation of all financial fields, account associations, and notes.
 * 5. Concurrency / stale preview protection: late USER_ASSIGNED transition is never overwritten.
 * 6. Category lifecycle safety: archived categories are never assigned historically.
 * 7. Merchant alias resolution pipeline during historical rule application.
 */
@RunWith(AndroidJUnit4::class)
class HistoricalRuleApplierDeviceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var expenseDao: ExpenseDao
    private lateinit var categoryDao: TransactionCategoryDao
    private lateinit var ruleDao: UserCategoryRuleDao
    private lateinit var aliasDao: MerchantAliasDao
    private lateinit var categoryManager: CategoryManager
    private lateinit var ruleManager: CategoryRuleManager
    private lateinit var aliasManager: MerchantAliasManager
    private lateinit var applier: HistoricalRuleApplier
    private val persistentDbName = "m12_device_test_persistent.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(persistentDbName)

        database = Room.databaseBuilder(context, AppDatabase::class.java, persistentDbName)
            .allowMainThreadQueries()
            .build()

        expenseDao = database.expenseDao()
        categoryDao = database.transactionCategoryDao()
        ruleDao = database.userCategoryRuleDao()
        aliasDao = database.merchantAliasDao()

        categoryManager = CategoryManager(categoryDao, expenseDao, database, ruleDao)
        ruleManager = CategoryRuleManager(ruleDao, categoryDao)
        aliasManager = MerchantAliasManager(aliasDao)
        applier = HistoricalRuleApplier(
            expenseDao = expenseDao,
            categoryDao = categoryDao,
            ruleDao = ruleDao,
            aliasDao = aliasDao,
            database = database
        )

        runBlocking {
            categoryManager.ensureBuiltInCategoriesSeeded()
            ruleManager.getActiveRules()
            aliasManager.getActiveAliases()
        }

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

    // ==================================================
    // 1. Preview Generation on Real Room Database
    // ==================================================

    @Test
    fun test01_device_previewGenerationOnRealRoomDb() = runBlocking {
        // User rule: Swiggy -> Food & Dining
        ruleDao.insert(
            UserCategoryRule(
                matchType = UserRuleMatchType.MERCHANT_EXACT,
                pattern = "Swiggy",
                normalizedPattern = "swiggy",
                categoryId = BuiltInCategories.FOOD_AND_DINING.id
            )
        )

        val id1 = expenseDao.insert(
            Expense(
                id = 0,
                merchant = "Swiggy",
                amount = 450.0,
                dateMillis = System.currentTimeMillis(),
                type = "Debit",
                notificationKey = "swiggy_1",
                categoryId = null,
                categorySource = CategorySource.NONE
            )
        ).toInt()

        val id2 = expenseDao.insert(
            Expense(
                id = 0,
                merchant = "Doctor Visit",
                amount = 800.0,
                dateMillis = System.currentTimeMillis(),
                type = "Debit",
                notificationKey = "doc_1",
                categoryId = BuiltInCategories.HEALTHCARE.id,
                categorySource = CategorySource.USER_ASSIGNED
            )
        ).toInt()

        val preview = applier.generatePreview()
        assertEquals(2, preview.totalEvaluated)
        assertEquals(1, preview.willChangeCount)
        assertEquals(1, preview.protectedCount)

        val willChangeItem = preview.actionableItems.first { it.transactionId == id1 }
        assertEquals(BuiltInCategories.FOOD_AND_DINING.id, willChangeItem.proposedCategoryId)

        val protectedItem = preview.items.first { it.transactionId == id2 }
        assertEquals(PreviewChangeStatus.PROTECTED_USER_ASSIGNED, protectedItem.changeStatus)
    }

    // ==================================================
    // 2. Strict USER_ASSIGNED Protection on Device
    // ==================================================

    @Test
    fun test02_device_userAssignedStrictProtection() = runBlocking {
        val expenseId = expenseDao.insert(
            Expense(
                id = 0,
                merchant = "Amazon",
                amount = 2999.0,
                dateMillis = System.currentTimeMillis(),
                type = "Debit",
                notificationKey = "amzn_protected",
                categoryId = BuiltInCategories.ENTERTAINMENT.id,
                categorySource = CategorySource.USER_ASSIGNED
            )
        ).toInt()

        // Create a user rule that would otherwise match Amazon -> Shopping
        ruleDao.insert(
            UserCategoryRule(
                matchType = UserRuleMatchType.MERCHANT_EXACT,
                pattern = "Amazon",
                normalizedPattern = "amazon",
                categoryId = BuiltInCategories.SHOPPING.id
            )
        )

        val preview = applier.generatePreview()
        assertEquals(1, preview.protectedCount)
        assertTrue(preview.actionableItems.none { it.transactionId == expenseId })

        // Direct apply attempt must fail/skip
        val fakePreviewItem = HistoricalReassignmentPreviewItem(
            transactionId = expenseId,
            merchant = "Amazon",
            amount = 2999.0,
            dateMillis = System.currentTimeMillis(),
            currentCategoryId = BuiltInCategories.ENTERTAINMENT.id,
            currentCategoryName = "Entertainment",
            currentCategorySource = CategorySource.USER_ASSIGNED,
            proposedCategoryId = BuiltInCategories.SHOPPING.id,
            proposedCategoryName = "Shopping",
            proposedCategorySource = CategorySource.INFERRED,
            changeStatus = PreviewChangeStatus.WILL_CHANGE
        )

        val applyResult = applier.applyReassignments(listOf(fakePreviewItem))
        assertEquals(0, applyResult.successCount)
        assertEquals(1, applyResult.skippedCount)

        // Verify database is completely unchanged
        val stored = expenseDao.getExpenseById(expenseId)
        assertEquals(BuiltInCategories.ENTERTAINMENT.id, stored?.categoryId)
        assertEquals(CategorySource.USER_ASSIGNED, stored?.categorySource)
    }

    // ==================================================
    // 3. Atomic Bulk Update & Cold Reopen Persistence
    // ==================================================

    @Test
    fun test03_device_atomicBulkUpdateAndReopenPersistence() = runBlocking {
        ruleDao.insert(
            UserCategoryRule(
                matchType = UserRuleMatchType.MERCHANT_EXACT,
                pattern = "Uber",
                normalizedPattern = "uber",
                categoryId = BuiltInCategories.TRANSPORT.id
            )
        )

        val id1 = expenseDao.insert(
            Expense(id = 0, merchant = "Uber", amount = 180.0, dateMillis = 1000L, type = "Debit", notificationKey = "u1", categoryId = null, categorySource = CategorySource.NONE)
        ).toInt()
        val id2 = expenseDao.insert(
            Expense(id = 0, merchant = "Uber", amount = 220.0, dateMillis = 2000L, type = "Debit", notificationKey = "u2", categoryId = null, categorySource = CategorySource.NONE)
        ).toInt()

        val preview = applier.generatePreview()
        assertEquals(2, preview.actionableItems.size)

        // Apply only id1
        val selected = preview.actionableItems.filter { it.transactionId == id1 }
        val result = applier.applyReassignments(selected)
        assertTrue(result.isSuccess)
        assertEquals(1, result.successCount)

        // Close and reopen database (Cold Restart)
        database.close()
        database = Room.databaseBuilder(context, AppDatabase::class.java, persistentDbName)
            .allowMainThreadQueries()
            .build()
        expenseDao = database.expenseDao()

        val reloaded1 = expenseDao.getExpenseById(id1)
        assertNotNull(reloaded1)
        assertEquals(BuiltInCategories.TRANSPORT.id, reloaded1!!.categoryId)
        assertEquals(CategorySource.INFERRED, reloaded1.categorySource)

        val reloaded2 = expenseDao.getExpenseById(id2)
        assertNotNull(reloaded2)
        assertNull(reloaded2!!.categoryId)
        assertEquals(CategorySource.NONE, reloaded2.categorySource)
    }

    // ==================================================
    // 4. Preservation of All Financial & Non-Category Fields
    // ==================================================

    @Test
    fun test04_device_allFinancialFieldsPreserved() = runBlocking {
        val original = Expense(
            id = 0,
            amount = 4599.50,
            merchant = "Croma Electronics",
            dateMillis = 1711000000000L,
            type = "Debit",
            notificationKey = "croma_txn_001",
            isPending = false,
            rawText = "Debited INR 4599.50 for Croma using HDFC Bank xx9020",
            tag = "Legacy Tag",
            note = "Important warranty invoice #998",
            source = "NOTIFICATION",
            relationshipType = "NONE",
            relationshipId = "rel_998",
            accountId = "hdfc_bank_9020",
            accountSuffix = "9020",
            categoryId = null,
            categorySource = CategorySource.NONE
        )
        val expenseId = expenseDao.insert(original).toInt()

        ruleDao.insert(
            UserCategoryRule(
                matchType = UserRuleMatchType.MERCHANT_CONTAINS,
                pattern = "Croma",
                normalizedPattern = "croma",
                categoryId = BuiltInCategories.SHOPPING.id
            )
        )

        val preview = applier.generatePreview()
        val result = applier.applyReassignments(preview.actionableItems)
        assertEquals(1, result.successCount)

        val updated = expenseDao.getExpenseById(expenseId)
        assertNotNull(updated)

        // Strictly verify every single non-category property
        assertEquals(4599.50, updated!!.amount, 0.001)
        assertEquals("Croma Electronics", updated.merchant)
        assertEquals(1711000000000L, updated.dateMillis)
        assertEquals("Debit", updated.type)
        assertEquals("croma_txn_001", updated.notificationKey)
        assertFalse(updated.isPending)
        assertEquals("Debited INR 4599.50 for Croma using HDFC Bank xx9020", updated.rawText)
        assertEquals("Important warranty invoice #998", updated.note)
        assertEquals("NOTIFICATION", updated.source)
        assertEquals("NONE", updated.relationshipType)
        assertEquals("rel_998", updated.relationshipId)
        assertEquals("hdfc_bank_9020", updated.accountId)
        assertEquals("9020", updated.accountSuffix)

        // Only category fields changed
        assertEquals(BuiltInCategories.SHOPPING.id, updated.categoryId)
        assertEquals(CategorySource.INFERRED, updated.categorySource)
        assertEquals("Shopping", updated.tag)
    }

    // ==================================================
    // 5. Concurrency / Stale Preview Protection
    // ==================================================

    @Test
    fun test05_device_stalePreviewUserAssignedProtection() = runBlocking {
        val expenseId = expenseDao.insert(
            Expense(
                id = 0,
                merchant = "Apollo Pharmacy",
                amount = 650.0,
                dateMillis = System.currentTimeMillis(),
                type = "Debit",
                notificationKey = "apollo_1",
                categoryId = null,
                categorySource = CategorySource.NONE
            )
        ).toInt()

        ruleDao.insert(
            UserCategoryRule(
                matchType = UserRuleMatchType.MERCHANT_CONTAINS,
                pattern = "Apollo",
                normalizedPattern = "apollo",
                categoryId = BuiltInCategories.HEALTHCARE.id
            )
        )

        // Step 1: Preview generated
        val preview = applier.generatePreview()
        val item = preview.actionableItems.first { it.transactionId == expenseId }

        // Step 2: In the meantime, user manually assigns category to OTHER in detail view
        expenseDao.updateCategoryMetadata(
            id = expenseId,
            categoryId = BuiltInCategories.OTHER.id,
            categoryName = "Other",
            categorySource = CategorySource.USER_ASSIGNED
        )

        // Step 3: Stale apply executed
        val result = applier.applyReassignments(listOf(item))
        assertEquals(0, result.successCount)
        assertEquals(1, result.skippedCount)

        // Verify manual choice was preserved
        val current = expenseDao.getExpenseById(expenseId)
        assertEquals(BuiltInCategories.OTHER.id, current?.categoryId)
        assertEquals(CategorySource.USER_ASSIGNED, current?.categorySource)
    }

    // ==================================================
    // 6. Category Lifecycle Safety (Archived Category)
    // ==================================================

    @Test
    fun test06_device_archivedCategoryLifecycleSafety() = runBlocking {
        val customCat = categoryManager.createCategory("Temporary Category").getOrThrow()
        val rule = ruleDao.insert(
            UserCategoryRule(
                matchType = UserRuleMatchType.MERCHANT_EXACT,
                pattern = "TempMerchant",
                normalizedPattern = "tempmerchant",
                categoryId = customCat.id
            )
        )

        val expenseId = expenseDao.insert(
            Expense(
                id = 0,
                merchant = "TempMerchant",
                amount = 100.0,
                dateMillis = System.currentTimeMillis(),
                type = "Debit",
                notificationKey = "temp_1",
                categoryId = null,
                categorySource = CategorySource.NONE
            )
        ).toInt()

        // Archive category
        categoryManager.archiveCategory(customCat.id)

        // Evaluate preview
        val preview = applier.generatePreview()
        assertEquals(0, preview.willChangeCount)
        assertTrue(preview.actionableItems.isEmpty())

        // Try to apply directly
        val badItem = HistoricalReassignmentPreviewItem(
            transactionId = expenseId,
            merchant = "TempMerchant",
            amount = 100.0,
            dateMillis = System.currentTimeMillis(),
            currentCategoryId = null,
            currentCategoryName = null,
            currentCategorySource = CategorySource.NONE,
            proposedCategoryId = customCat.id,
            proposedCategoryName = "Temporary Category",
            proposedCategorySource = CategorySource.INFERRED,
            changeStatus = PreviewChangeStatus.INACTIVE_OR_ARCHIVED_CATEGORY
        )

        val result = applier.applyReassignments(listOf(badItem))
        assertEquals(0, result.successCount)
        assertEquals(1, result.skippedCount)

        // Expense remains uncategorized
        val reloaded = expenseDao.getExpenseById(expenseId)
        assertNull(reloaded?.categoryId)
    }

    // ==================================================
    // 7. Merchant Alias Resolution Pipeline
    // ==================================================

    @Test
    fun test07_device_merchantAliasToUserRuleHistoricalApplication() = runBlocking {
        // Alias: "AMZN IN" -> "Amazon"
        aliasManager.createAlias(alias = "AMZN IN", canonicalMerchant = "Amazon")

        // User Rule: "Amazon" -> Shopping
        ruleManager.createRule(
            pattern = "Amazon",
            categoryId = BuiltInCategories.SHOPPING.id,
            matchType = UserRuleMatchType.MERCHANT_EXACT
        )

        // Historical expense with raw alias
        val expenseId = expenseDao.insert(
            Expense(
                id = 0,
                merchant = "AMZN IN",
                amount = 1599.0,
                dateMillis = System.currentTimeMillis(),
                type = "Debit",
                notificationKey = "alias_test_1",
                categoryId = null,
                categorySource = CategorySource.NONE
            )
        ).toInt()

        val preview = applier.generatePreview()
        assertEquals(1, preview.willChangeCount)
        val item = preview.actionableItems.first { it.transactionId == expenseId }
        assertEquals(BuiltInCategories.SHOPPING.id, item.proposedCategoryId)

        val result = applier.applyReassignments(listOf(item))
        assertTrue(result.isSuccess)
        assertEquals(1, result.successCount)

        val finalExpense = expenseDao.getExpenseById(expenseId)
        assertEquals(BuiltInCategories.SHOPPING.id, finalExpense?.categoryId)
        assertEquals(CategorySource.INFERRED, finalExpense?.categorySource)
    }
}
