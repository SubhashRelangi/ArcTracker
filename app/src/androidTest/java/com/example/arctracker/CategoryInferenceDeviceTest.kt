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
 * Milestone 10: Automatic Category Inference Physical Device Tests.
 *
 * Runs on connected physical Samsung Galaxy A34 5G (SM-A346E - Android 16).
 *
 * Verifies:
 * 1. Room v5 -> v6 migration non-destructively:
 *    - Schema migration adds `categorySource` column.
 *    - Existing categorized transactions safely marked as `USER_ASSIGNED`.
 * 2. Automated category inference during transaction persistence:
 *    - High-confidence merchant matching (Swiggy -> Food & Dining, Uber -> Transport).
 *    - Auto-assigned with categorySource = INFERRED.
 * 3. High-precedence protection rules:
 *    - Transfer protection: "Transfer to Amazon" -> Transfer, not Shopping.
 *    - Cash withdrawal protection: "ATM Cash Withdrawal" -> Cash Withdrawal.
 * 4. User override protection:
 *    - Manual assignment sets categorySource = USER_ASSIGNED.
 *    - Re-inference halts with USER_ASSIGNED_PRESERVED and never overwrites user decision.
 *    - Survives database close and reopen.
 * 5. Conflict & Low confidence isolation:
 *    - Ambiguous or conflicting inputs default safely to null category with categorySource = NONE.
 *    - Financial persistence never fails.
 * 6. Category unlinking:
 *    - Manual unlink resets categoryId to null and categorySource to NONE.
 * 7. Active category availability:
 *    - Archived categories are never inferred or auto-assigned.
 */
@RunWith(AndroidJUnit4::class)
class CategoryInferenceDeviceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var expenseDao: ExpenseDao
    private lateinit var categoryDao: TransactionCategoryDao
    private lateinit var categoryManager: CategoryManager
    private lateinit var transactionManager: TransactionManager
    private val persistentDbName = "m10_device_test_persistent.db"

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

    // ==================================================
    // 1. ROOM V5 -> V6 NON-DESTRUCTIVE MIGRATION
    // ==================================================
    @Test
    fun test01_device_databaseMigration_v5_to_v6() {
        val dbName = "test_device_migration_v5_to_v6.db"
        context.deleteDatabase(dbName)

        // Step 1: Create v5 SQLite database directly
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Expenses table schema at version 5 (complete schema matching Room v5)
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
                            `categoryId` TEXT
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_notificationKey` ON `expenses` (`notificationKey`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_dateMillis` ON `expenses` (`dateMillis`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_isPending` ON `expenses` (`isPending`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_relationshipId` ON `expenses` (`relationshipId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_accountId` ON `expenses` (`accountId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_accountSuffix` ON `expenses` (`accountSuffix`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_categoryId` ON `expenses` (`categoryId`)")

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
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_categories_isArchived` ON `transaction_categories` (`isArchived`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_categories_sortOrder` ON `transaction_categories` (`sortOrder`)")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `known_financial_accounts` (
                            `id` TEXT PRIMARY KEY NOT NULL,
                            `institutionId` TEXT,
                            `institutionName` TEXT,
                            `accountSuffix` TEXT NOT NULL,
                            `instrumentType` TEXT NOT NULL,
                            `confidence` TEXT NOT NULL,
                            `source` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_financial_accounts_accountSuffix` ON `known_financial_accounts` (`accountSuffix`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_financial_accounts_institutionId` ON `known_financial_accounts` (`institutionId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_financial_accounts_instrumentType` ON `known_financial_accounts` (`instrumentType`)")

                    // Insert two pre-existing transactions in v5
                    db.execSQL(
                        """
                        INSERT INTO expenses (id, amount, merchant, dateMillis, type, notificationKey, isPending, source, categoryId)
                        VALUES (1, 250.0, 'Pre-existing Categorized', 1700000000000, 'Debit', 'notif_1', 0, 'NOTIFICATION', 'food_dining')
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO expenses (id, amount, merchant, dateMillis, type, notificationKey, isPending, source, categoryId)
                        VALUES (2, 100.0, 'Pre-existing Uncategorized', 1700000001000, 'Debit', 'notif_2', 0, 'NOTIFICATION', NULL)
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val v5Db = helper.writableDatabase
        assertEquals(5, v5Db.version)
        v5Db.close()

        // Step 2: Open with Room v6 applying MIGRATION_5_6
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()

        val v6Db = roomDb.openHelper.readableDatabase
        assertEquals(6, v6Db.version)

        // Step 3: Verify existing data preserved and categorySource correctly initialized
        val cursor = v6Db.query("SELECT id, merchant, categoryId, categorySource FROM expenses ORDER BY id ASC")
        assertTrue(cursor.moveToNext())
        assertEquals(1, cursor.getInt(0))
        assertEquals("Pre-existing Categorized", cursor.getString(1))
        assertEquals("food_dining", cursor.getString(2))
        assertEquals("USER_ASSIGNED", cursor.getString(3)) // Preserved from existing category

        assertTrue(cursor.moveToNext())
        assertEquals(2, cursor.getInt(0))
        assertEquals("Pre-existing Uncategorized", cursor.getString(1))
        assertNull(cursor.getString(2))
        assertEquals("NONE", cursor.getString(3)) // Uncategorized gets default NONE

        cursor.close()
        roomDb.close()
        context.deleteDatabase(dbName)
    }

    // ==================================================
    // 2. AUTOMATIC INFERENCE ON PERSISTENCE
    // ==================================================
    @Test
    fun test02_device_automaticInference_onTransactionPersistence() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()

        // Test 1: Swiggy candidate -> inferred food_dining
        val swiggyCandidate = StructuredTransactionCandidate(
            sourceNotificationKey = "notif_swiggy_1",
            packageName = "com.google.android.apps.nbu.paisa.user",
            postTime = System.currentTimeMillis(),
            merchant = "Swiggy",
            amount = 349.0,
            direction = TransactionDirection.DEBIT,
            accountSuffix = "1234",
            rawContent = "Paid Rs 349 at Swiggy using UPI"
        )
        val swiggyExpense = TransactionPersistenceManager.mapToExpense(swiggyCandidate, isPending = false)
        assertEquals("food_dining", swiggyExpense.categoryId)
        assertEquals("INFERRED", swiggyExpense.categorySource)

        val swiggyId = expenseDao.insert(swiggyExpense).toInt()
        val loadedSwiggy = expenseDao.getExpenseById(swiggyId)
        assertNotNull(loadedSwiggy)
        assertEquals("food_dining", loadedSwiggy!!.categoryId)
        assertEquals("INFERRED", loadedSwiggy.categorySource)

        // Test 2: Uber candidate -> inferred transport
        val uberCandidate = StructuredTransactionCandidate(
            sourceNotificationKey = "notif_uber_1",
            packageName = "com.google.android.apps.nbu.paisa.user",
            postTime = System.currentTimeMillis(),
            merchant = "Uber India",
            amount = 189.0,
            direction = TransactionDirection.DEBIT,
            accountSuffix = "1234",
            rawContent = "Paid Rs 189 for Uber ride"
        )
        val uberExpense = TransactionPersistenceManager.mapToExpense(uberCandidate, isPending = false)
        assertEquals("transport", uberExpense.categoryId)
        assertEquals("INFERRED", uberExpense.categorySource)

        // Test 3: Amazon candidate -> inferred shopping
        val amazonCandidate = StructuredTransactionCandidate(
            sourceNotificationKey = "notif_amazon_1",
            packageName = "com.google.android.apps.nbu.paisa.user",
            postTime = System.currentTimeMillis(),
            merchant = "Amazon",
            amount = 1299.0,
            direction = TransactionDirection.DEBIT,
            accountSuffix = "1234",
            rawContent = "Paid Rs 1299 for Amazon order"
        )
        val amazonExpense = TransactionPersistenceManager.mapToExpense(amazonCandidate, isPending = false)
        assertEquals("shopping", amazonExpense.categoryId)
        assertEquals("INFERRED", amazonExpense.categorySource)
    }

    // ==================================================
    // 3. TRANSFER & CASH WITHDRAWAL HIGH PRECEDENCE
    // ==================================================
    @Test
    fun test03_device_transferAndCashWithdrawalProtection() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()

        // Transfer keyword with merchant name: "Transfer to Amazon" must NOT become shopping
        val transferCandidate = StructuredTransactionCandidate(
            sourceNotificationKey = "notif_transfer_1",
            packageName = "com.google.android.apps.nbu.paisa.user",
            postTime = System.currentTimeMillis(),
            merchant = "Transfer to Amazon",
            amount = 5000.0,
            direction = TransactionDirection.DEBIT,
            accountSuffix = "5678",
            rawContent = "UPI transfer of Rs 5000 to Amazon Pay balance"
        )
        val transferExpense = TransactionPersistenceManager.mapToExpense(transferCandidate, isPending = false)
        assertEquals("transfer", transferExpense.categoryId)
        assertEquals("INFERRED", transferExpense.categorySource)

        // Cash withdrawal: "ATM Cash Withdrawal" must become cash_withdrawal
        val cashCandidate = StructuredTransactionCandidate(
            sourceNotificationKey = "notif_cash_1",
            packageName = "com.hdfcbank.mobilebanking",
            postTime = System.currentTimeMillis(),
            merchant = "ATM Cash Withdrawal",
            amount = 2000.0,
            direction = TransactionDirection.DEBIT,
            accountSuffix = "9012",
            rawContent = "Cash dispensed Rs 2000 at HDFC ATM"
        )
        val cashExpense = TransactionPersistenceManager.mapToExpense(cashCandidate, isPending = false)
        assertEquals("cash_withdrawal", cashExpense.categoryId)
        assertEquals("INFERRED", cashExpense.categorySource)
    }

    // ==================================================
    // 4. USER OVERRIDE PROTECTION & RESTART SURVIVAL
    // ==================================================
    @Test
    fun test04_device_userOverrideProtection_survivesRestart() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()

        // Insert inferred expense
        val expense = Expense(
            id = 101,
            merchant = "Swiggy",
            amount = 450.0,
            dateMillis = System.currentTimeMillis(),
            type = "Debit",
            tag = "Food & Dining",
            categoryId = "food_dining",
            categorySource = "INFERRED"
        )
        expenseDao.insert(expense)

        // User explicitly reassigns category to entertainment
        val assignResult = categoryManager.assignCategory(101, "entertainment")
        assertTrue(assignResult.isSuccess)

        val updated = expenseDao.getExpenseById(101)
        assertNotNull(updated)
        assertEquals("entertainment", updated!!.categoryId)
        assertEquals("USER_ASSIGNED", updated.categorySource)

        // Close and reopen database
        reopenDatabase()

        val afterRestart = expenseDao.getExpenseById(101)
        assertNotNull(afterRestart)
        assertEquals("entertainment", afterRestart!!.categoryId)
        assertEquals("USER_ASSIGNED", afterRestart.categorySource)

        // Run category inference on existing transaction
        val engine = CategoryInferenceEngine()
        val inferenceResult = engine.inferCategory(
            CategoryInferenceInput(
                merchant = afterRestart.merchant,
                rawText = afterRestart.rawText,
                existingCategoryId = afterRestart.categoryId,
                existingCategorySource = afterRestart.categorySource,
                availableCategoryIds = BuiltInCategories.ALL.map { it.id }.toSet()
            )
        )

        // Invariant: User override is preserved, never overwritten
        assertEquals(CategoryInferenceStatus.USER_ASSIGNED_PRESERVED, inferenceResult.status)
        assertEquals("entertainment", inferenceResult.suggestedCategoryId)
        assertFalse(inferenceResult.isAutoAssignable)
    }

    // ==================================================
    // 5. CONFLICT & LOW CONFIDENCE ISOLATION
    // ==================================================
    @Test
    fun test05_device_conflictAndLowConfidence_safety() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()

        // Unrecognized merchant
        val unknownCandidate = StructuredTransactionCandidate(
            sourceNotificationKey = "notif_unknown_1",
            packageName = "com.google.android.apps.nbu.paisa.user",
            postTime = System.currentTimeMillis(),
            merchant = "Random Merchant XYZ",
            amount = 150.0,
            direction = TransactionDirection.DEBIT,
            accountSuffix = "1111",
            rawContent = "Paid Rs 150 to Random Merchant XYZ"
        )
        val unknownExpense = TransactionPersistenceManager.mapToExpense(unknownCandidate, isPending = false)
        assertNull(unknownExpense.categoryId)
        assertEquals("NONE", unknownExpense.categorySource)

        // Persistence succeeds with 0 errors
        val insertedId = expenseDao.insert(unknownExpense).toInt()
        assertTrue(insertedId > 0)
        val persisted = expenseDao.getExpenseById(insertedId)
        assertNotNull(persisted)
        assertNull(persisted!!.categoryId)
        assertEquals("NONE", persisted.categorySource)
    }

    // ==================================================
    // 6. CATEGORY UNLINKING RESETS SOURCE TO NONE
    // ==================================================
    @Test
    fun test06_device_unlinkingCategory_setsSourceToNone() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()

        val expense = Expense(
            id = 202,
            merchant = "BookMyShow",
            amount = 600.0,
            dateMillis = System.currentTimeMillis(),
            type = "Debit",
            tag = "Entertainment",
            categoryId = "entertainment",
            categorySource = "USER_ASSIGNED"
        )
        expenseDao.insert(expense)

        // Unlink category
        val unlinkResult = categoryManager.assignCategory(202, null)
        assertTrue(unlinkResult.isSuccess)

        val unlinked = expenseDao.getExpenseById(202)
        assertNotNull(unlinked)
        assertNull(unlinked!!.categoryId)
        assertEquals("NONE", unlinked.categorySource)
    }

    // ==================================================
    // 7. ARCHIVED CATEGORY NEVER INFERRED
    // ==================================================
    @Test
    fun test07_device_archivedCategory_neverInferred() = runBlocking {
        categoryManager.ensureBuiltInCategoriesSeeded()

        // Create a custom category and immediately archive it
        val createResult = categoryManager.createCategory(
            name = "Gym Membership",
            iconKey = "fitness_center",
            colorKey = "teal"
        )
        assertTrue(createResult.isSuccess)
        val customCat = createResult.getOrThrow()

        val archiveResult = categoryManager.archiveCategory(customCat.id)
        assertTrue(archiveResult.isSuccess)

        // Fetch active category IDs
        val activeCategories = categoryManager.getActiveCategories()
        val activeIds = activeCategories.map { it.id }.toSet()
        assertFalse(activeIds.contains(customCat.id))

        // Run inference
        val engine = CategoryInferenceEngine()
        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Gold Gym Membership",
                availableCategoryIds = activeIds
            )
        )

        // Invariant: Archived category is never returned as suggested category
        assertNotEquals(customCat.id, result.suggestedCategoryId)
    }
}
