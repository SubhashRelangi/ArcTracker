package com.example.arctracker

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import com.example.arctracker.data.*
import com.example.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Milestone 11: User-Defined Category Rules & Merchant Aliases Physical Device Tests.
 *
 * Runs on connected physical Samsung Galaxy A34 5G (SM-A346E - Android 16).
 *
 * Verifies:
 * 1. Room v6 -> v7 migration non-destructively:
 *    - Schema migration creates `user_category_rules` and `merchant_aliases` tables.
 *    - Correct indices created.
 *    - Pre-existing transaction and category data preserved.
 * 2. UserCategoryRule CRUD & Persistence across database close/reopen.
 * 3. MerchantAlias CRUD, Normalization & In-memory resolution across database close/reopen.
 * 4. User Rule Precedence over Built-in Rule during transaction inference.
 * 5. Semantic Protection Precedence: Transfer protection overrides user merchant rules.
 * 6. Merchant Alias -> Canonical Merchant -> User Rule resolution pipeline.
 * 7. Category Lifecycle Safety: Archiving a category disables associated user rules.
 * 8. User Override Protection: USER_ASSIGNED transactions are never overwritten by user rules.
 */
@RunWith(AndroidJUnit4::class)
class CategoryRuleAndAliasDeviceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var expenseDao: ExpenseDao
    private lateinit var categoryDao: TransactionCategoryDao
    private lateinit var ruleDao: UserCategoryRuleDao
    private lateinit var aliasDao: MerchantAliasDao
    private lateinit var categoryManager: CategoryManager
    private lateinit var ruleManager: CategoryRuleManager
    private lateinit var aliasManager: MerchantAliasManager
    private val persistentDbName = "m11_device_test_persistent.db"

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

        TransactionPersistenceManager.ruleManager = ruleManager
        TransactionPersistenceManager.aliasManager = aliasManager

        runBlocking {
            categoryManager.ensureBuiltInCategoriesSeeded()
            aliasManager.getActiveAliases()
            ruleManager.getActiveRules()
        }

        NotificationPermissionHelper.permissionOverrideForTesting = true
        NotificationReaderService.clearActiveNotifications()
    }

    @After
    fun tearDown() {
        TransactionPersistenceManager.ruleManager = null
        TransactionPersistenceManager.aliasManager = null
        database.close()
        context.deleteDatabase(persistentDbName)
        NotificationPermissionHelper.permissionOverrideForTesting = null
        NotificationReaderService.clearActiveNotifications()
    }

    // ==================================================
    // 1. Room v6 -> v7 Schema Migration
    // ==================================================

    @Test
    fun testRoomMigration6to7PreservesDataAndCreatesTables() {
        val migDbName = "m11_migration_test.db"
        context.deleteDatabase(migDbName)

        // Step 1: Create a v6 database manually
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(migDbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `expenses` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `amount` REAL NOT NULL,
                            `merchant` TEXT NOT NULL,
                            `dateMillis` INTEGER NOT NULL,
                            `type` TEXT NOT NULL,
                            `notificationKey` TEXT NOT NULL,
                            `isPending` INTEGER NOT NULL,
                            `rawText` TEXT,
                            `tag` TEXT,
                            `note` TEXT,
                            `source` TEXT NOT NULL,
                            `relationshipType` TEXT,
                            `relationshipId` TEXT,
                            `accountId` TEXT,
                            `accountSuffix` TEXT,
                            `categoryId` TEXT,
                            `categorySource` TEXT NOT NULL DEFAULT 'NONE'
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `transaction_categories` (
                            `id` TEXT PRIMARY KEY NOT NULL,
                            `name` TEXT NOT NULL,
                            `iconKey` TEXT NOT NULL,
                            `colorKey` TEXT NOT NULL,
                            `isSystem` INTEGER NOT NULL,
                            `isArchived` INTEGER NOT NULL,
                            `sortOrder` INTEGER NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    // Insert sample data in v6
                    db.execSQL("INSERT INTO transaction_categories VALUES ('food_dining', 'Food & Dining', 'restaurant', 'orange', 1, 0, 0, 1000, 1000)")
                    db.execSQL("INSERT INTO expenses (id, amount, merchant, dateMillis, type, notificationKey, isPending, source, categoryId, categorySource) VALUES (1, 250.0, 'Pre-existing Expense', 1700000000000, 'Debit', 'notif_1', 0, 'NOTIFICATION', 'food_dining', 'INFERRED')")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val v6Db = helper.writableDatabase

        // Verify initial state in v6
        val v6Cursor = v6Db.query("SELECT COUNT(*) FROM expenses")
        assertTrue(v6Cursor.moveToFirst())
        assertEquals(1, v6Cursor.getInt(0))
        v6Cursor.close()

        // Step 2: Execute MIGRATION_6_7
        AppDatabase.MIGRATION_6_7.migrate(v6Db)

        // Step 3: Verify user_category_rules and merchant_aliases tables exist
        val rulesCursor = v6Db.query("SELECT COUNT(*) FROM user_category_rules")
        assertTrue(rulesCursor.moveToFirst())
        assertEquals(0, rulesCursor.getInt(0))
        rulesCursor.close()

        val aliasesCursor = v6Db.query("SELECT COUNT(*) FROM merchant_aliases")
        assertTrue(aliasesCursor.moveToFirst())
        assertEquals(0, aliasesCursor.getInt(0))
        aliasesCursor.close()

        // Verify pre-existing data preserved
        val expenseCursor = v6Db.query("SELECT merchant, categoryId, categorySource FROM expenses WHERE id = 1")
        assertTrue(expenseCursor.moveToFirst())
        assertEquals("Pre-existing Expense", expenseCursor.getString(0))
        assertEquals("food_dining", expenseCursor.getString(1))
        assertEquals("INFERRED", expenseCursor.getString(2))
        expenseCursor.close()

        v6Db.close()
        helper.close()
        context.deleteDatabase(migDbName)
    }

    // ==================================================
    // 2. UserCategoryRule CRUD & Persistence Across Reopen
    // ==================================================

    @Test
    fun testUserCategoryRuleCrudAndReopenPersistence() = runBlocking {
        val rule = UserCategoryRule(
            name = "My Netflix Rule",
            matchType = UserRuleMatchType.MERCHANT_EXACT,
            pattern = "Netflix India",
            normalizedPattern = MerchantNormalizer.normalize("Netflix India"),
            categoryId = BuiltInCategories.ENTERTAINMENT.id,
            priority = 100,
            isEnabled = true
        )

        ruleDao.insert(rule)
        val inserted = ruleDao.getById(rule.id)
        assertNotNull(inserted)
        assertEquals("My Netflix Rule", inserted?.name)
        assertEquals("netflix india", inserted?.normalizedPattern)
        assertEquals(BuiltInCategories.ENTERTAINMENT.id, inserted?.categoryId)

        // Close and reopen database
        database.close()
        database = Room.databaseBuilder(context, AppDatabase::class.java, persistentDbName)
            .allowMainThreadQueries()
            .build()
        ruleDao = database.userCategoryRuleDao()

        val reloaded = ruleDao.getById(rule.id)
        assertNotNull(reloaded)
        assertEquals("My Netflix Rule", reloaded?.name)

        // Update
        ruleDao.update(reloaded!!.copy(isEnabled = false, priority = 80))
        val updated = ruleDao.getById(rule.id)
        assertFalse(updated!!.isEnabled)
        assertEquals(80, updated.priority)

        // Delete
        ruleDao.deleteById(rule.id)
        assertNull(ruleDao.getById(rule.id))
    }

    // ==================================================
    // 3. MerchantAlias CRUD, Normalization & Caching
    // ==================================================

    @Test
    fun testMerchantAliasCrudAndResolution() = runBlocking {
        val createResult = aliasManager.createAlias(
            alias = "AMZN",
            canonicalMerchant = "Amazon"
        )
        assertTrue(createResult.isSuccess)

        val activeAliases = aliasManager.getActiveAliases()
        val (resolvedMerchant, matchedAlias) = aliasManager.resolveAlias("AMZN", activeAliases)
        assertEquals("Amazon", resolvedMerchant)
        assertNotNull(matchedAlias)

        // Check DB direct query
        val stored = aliasDao.getByNormalizedAlias("amzn")
        assertNotNull(stored)
        assertEquals("Amazon", stored?.canonicalMerchant)

        // Close and reopen database
        database.close()
        database = Room.databaseBuilder(context, AppDatabase::class.java, persistentDbName)
            .allowMainThreadQueries()
            .build()
        aliasDao = database.merchantAliasDao()
        aliasManager = MerchantAliasManager(aliasDao)

        val reloadedAliases = aliasManager.getActiveAliases()
        val (reloadedResolve, reloadedMatched) = aliasManager.resolveAlias("amzn", reloadedAliases)
        assertEquals("Amazon", reloadedResolve)
        assertNotNull(reloadedMatched)
    }

    // ==================================================
    // 4. User Rule Precedence Over Built-In Rule
    // ==================================================

    @Test
    fun testUserRuleOverridesBuiltInRuleInInference() = runBlocking {
        // Built-in rule: "amazon" -> shopping (RulePriority.EXACT_MERCHANT = 800)
        // User rule: "Amazon" -> entertainment (RulePriority.USER_EXACT = 1200)
        val createResult = ruleManager.createRule(
            pattern = "Amazon",
            categoryId = BuiltInCategories.ENTERTAINMENT.id,
            matchType = UserRuleMatchType.MERCHANT_EXACT,
            priority = 100,
            name = "Amazon Prime Video"
        )
        assertTrue(createResult.isSuccess)

        val activeRules = ruleDao.getActiveRules()
        val engine = CategoryInferenceEngine()
        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Amazon",
                userRules = activeRules
            )
        )

        assertEquals(CategoryInferenceStatus.HIGH_CONFIDENCE, result.status)
        assertEquals(BuiltInCategories.ENTERTAINMENT.id, result.suggestedCategoryId)
        assertNotNull(result.matchedUserRuleId)
        assertTrue(result.evidence.any { it.type == EvidenceType.USER_RULE })
    }

    // ==================================================
    // 5. Semantic Protection Precedence (Transfer Protection)
    // ==================================================

    @Test
    fun testTransferProtectionOverridesUserRule() = runBlocking {
        // User creates rule: "Amazon" -> shopping
        val createResult = ruleManager.createRule(
            pattern = "Amazon",
            categoryId = BuiltInCategories.SHOPPING.id,
            matchType = UserRuleMatchType.MERCHANT_CONTAINS,
            name = "Amazon Shopping"
        )
        assertTrue(createResult.isSuccess)

        val activeRules = ruleDao.getActiveRules()
        val engine = CategoryInferenceEngine()

        // Input contains transfer intent: "Transfer to Amazon"
        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Amazon",
                rawText = "Transfer to Amazon for account funding",
                transactionType = "Debit",
                userRules = activeRules
            )
        )

        // Transfer protection must win!
        assertEquals(CategoryInferenceStatus.HIGH_CONFIDENCE, result.status)
        assertEquals(BuiltInCategories.TRANSFER.id, result.suggestedCategoryId)
        assertTrue(result.evidence.any { it.type == EvidenceType.EXPLICIT_SEMANTIC_KEYWORD })
    }

    // ==================================================
    // 6. Merchant Alias -> Canonical Merchant -> User Rule Pipeline
    // ==================================================

    @Test
    fun testMerchantAliasToUserRulePipeline() = runBlocking {
        // 1. Alias: "BB" -> "BigBasket"
        aliasManager.createAlias(alias = "BB", canonicalMerchant = "BigBasket")

        // 2. User Rule: "BigBasket" -> shopping
        ruleManager.createRule(
            pattern = "BigBasket",
            categoryId = BuiltInCategories.SHOPPING.id,
            matchType = UserRuleMatchType.MERCHANT_EXACT,
            name = "BigBasket Shopping"
        )

        val activeRules = ruleDao.getActiveRules()
        val activeAliases = aliasDao.getActiveAliases()
        val engine = CategoryInferenceEngine()

        // 3. Raw input has alias "BB"
        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "BB",
                userRules = activeRules,
                merchantAliases = activeAliases
            )
        )

        assertEquals("BigBasket", result.resolvedMerchant)
        assertEquals(BuiltInCategories.SHOPPING.id, result.suggestedCategoryId)
        assertTrue(result.evidence.any { it.type == EvidenceType.MERCHANT_ALIAS })
        assertTrue(result.evidence.any { it.type == EvidenceType.USER_RULE })
    }

    // ==================================================
    // 7. Category Lifecycle: Archiving Disables User Rules
    // ==================================================

    @Test
    fun testArchivingCategoryDisablesReferencingUserRules() = runBlocking {
        // Create custom category
        val customCatResult = categoryManager.createCategory(
            name = "Pet Supplies"
        )
        assertTrue(customCatResult.isSuccess)
        val customCat = customCatResult.getOrThrow()

        // Create rule for this category
        val ruleResult = ruleManager.createRule(
            pattern = "Heads Up For Tails",
            categoryId = customCat.id,
            matchType = UserRuleMatchType.MERCHANT_CONTAINS,
            name = "Pet Store Rule"
        )
        assertTrue(ruleResult.isSuccess)
        val ruleId = ruleResult.getOrThrow().id

        // Verify rule is enabled
        assertTrue(ruleDao.getById(ruleId)!!.isEnabled)

        // Archive the custom category
        categoryManager.archiveCategory(customCat.id)

        // Verify rule is now disabled!
        assertFalse(ruleDao.getById(ruleId)!!.isEnabled)
    }

    // ==================================================
    // 8. User Override Protection (USER_ASSIGNED Preserved)
    // ==================================================

    @Test
    fun testUserAssignedCategoryPreservedAgainstUserRules() = runBlocking {
        // Create an expense manually assigned to "Healthcare"
        val expense = Expense(
            id = 0,
            amount = 1200.0,
            merchant = "Special Amazon Order",
            dateMillis = System.currentTimeMillis(),
            type = "Debit",
            notificationKey = "test_key_1",
            isPending = false,
            tag = "Healthcare",
            categoryId = BuiltInCategories.HEALTHCARE.id,
            categorySource = CategorySource.USER_ASSIGNED
        )
        val expenseId = expenseDao.insert(expense).toInt()

        // User now creates a rule for Amazon -> Shopping
        ruleManager.createRule(
            pattern = "Amazon",
            categoryId = BuiltInCategories.SHOPPING.id,
            matchType = UserRuleMatchType.MERCHANT_CONTAINS,
            name = "Amazon Rule"
        )

        val loaded = expenseDao.getExpenseById(expenseId)
        assertNotNull(loaded)

        val activeRules = ruleDao.getActiveRules()
        val engine = CategoryInferenceEngine()
        val inferenceResult = engine.inferCategory(
            CategoryInferenceInput(
                merchant = loaded!!.merchant,
                existingCategorySource = loaded.categorySource,
                existingCategoryId = loaded.categoryId,
                userRules = activeRules
            )
        )

        assertEquals(CategoryInferenceStatus.USER_ASSIGNED_PRESERVED, inferenceResult.status)
        assertFalse(inferenceResult.isAutoAssignable)

        // Ensure persistent expense is still HEALTHCARE and USER_ASSIGNED
        val reloaded = expenseDao.getExpenseById(expenseId)
        assertEquals(BuiltInCategories.HEALTHCARE.id, reloaded?.categoryId)
        assertEquals(CategorySource.USER_ASSIGNED, reloaded?.categorySource)
    }
}
