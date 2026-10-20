package com.subhashrelangi.arctracker

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.DeletedTransactionTombstone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P2 — Step 6: Room Schema and Migration Verification Tests.
 *
 * Verifies on real SQLite / Android framework:
 * 1. Migration 8 -> 9: Creates `deleted_transaction_tombstones` table and `sourceType` index
 *    while preserving existing version 8 data.
 * 2. Room Runtime Schema Validation: Opening a migrated v8 database with Room
 *    validates the v9 schema hash without throwing `IllegalStateException`.
 * 3. Full Sequential Migration Chain (v1 -> v9): Verifies that applying all registered
 *    migrations in sequence successfully migrates early schemas and preserves transaction records.
 * 4. Tombstone DAO contract and SQLite constraints on physical device.
 */
@RunWith(AndroidJUnit4::class)
class RoomMigrationVerificationDeviceTest {

    private lateinit var context: Context
    private val databasesToCleanup = mutableListOf<String>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @After
    fun tearDown() {
        for (dbName in databasesToCleanup) {
            context.deleteDatabase(dbName)
        }
    }

    private fun registerDbForCleanup(name: String): String {
        databasesToCleanup.add(name)
        context.deleteDatabase(name)
        return name
    }

    // =========================================================================
    // 1. MIGRATION 8 -> 9 REAL SQLITE VERIFICATION
    // =========================================================================

    @Test
    fun testMigration_8_to_9_createsTombstoneTableAndIndexAndPreservesData() {
        val dbName = registerDbForCleanup("test_migration_8_to_9_sqlite.db")

        // 1. Create a real v8 SQLite database
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create v8 expenses table
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
                            `categorySource` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_notificationKey` ON `expenses` (`notificationKey`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_dateMillis` ON `expenses` (`dateMillis`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_isPending` ON `expenses` (`isPending`)")

                    // Create v8 budgets table
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `budgets` (
                            `id` TEXT NOT NULL PRIMARY KEY,
                            `name` TEXT NOT NULL,
                            `amountLimit` REAL NOT NULL,
                            `categoryId` TEXT,
                            `periodType` TEXT NOT NULL,
                            `periodAnchor` INTEGER NOT NULL,
                            `warningThreshold` REAL NOT NULL,
                            `exceededThreshold` REAL NOT NULL,
                            `isEnabled` INTEGER NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )

                    // Seed pre-existing data in v8
                    db.execSQL(
                        """
                        INSERT INTO `expenses` (
                            `id`, `amount`, `merchant`, `dateMillis`, `type`, `notificationKey`,
                            `isPending`, `source`, `categorySource`
                        ) VALUES (
                            42, 450.50, 'Trader Joes', 1711000000000, 'Debit', 'sms_tj_001',
                            0, 'SMS', 'NONE'
                        )
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        INSERT INTO `budgets` (
                            `id`, `name`, `amountLimit`, `periodType`, `periodAnchor`,
                            `warningThreshold`, `exceededThreshold`, `isEnabled`, `createdAt`, `updatedAt`
                        ) VALUES (
                            'budget_groceries', 'Groceries', 5000.0, 'MONTHLY', 1,
                            80.0, 100.0, 1, 1711000000000, 1711000000000
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = helperFactory.create(config)
        val v8Db = helper.writableDatabase

        // Verify tombstone table does NOT exist prior to migration
        val checkTableBefore = v8Db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='deleted_transaction_tombstones'")
        assertFalse("deleted_transaction_tombstones should not exist before migration", checkTableBefore.moveToFirst())
        checkTableBefore.close()

        // 2. Execute MIGRATION_8_9
        AppDatabase.MIGRATION_8_9.migrate(v8Db)

        // 3. Verify deleted_transaction_tombstones table exists
        val checkTableAfter = v8Db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='deleted_transaction_tombstones'")
        assertTrue("deleted_transaction_tombstones table must exist after migration", checkTableAfter.moveToFirst())
        checkTableAfter.close()

        // Verify column structure of deleted_transaction_tombstones
        val columnsCursor = v8Db.query("PRAGMA table_info(deleted_transaction_tombstones)")
        val columns = mutableMapOf<String, String>()
        val primaryKeys = mutableListOf<String>()
        val notNulls = mutableMapOf<String, Boolean>()
        while (columnsCursor.moveToNext()) {
            val name = columnsCursor.getString(columnsCursor.getColumnIndexOrThrow("name"))
            val type = columnsCursor.getString(columnsCursor.getColumnIndexOrThrow("type"))
            val notNull = columnsCursor.getInt(columnsCursor.getColumnIndexOrThrow("notnull")) == 1
            val pk = columnsCursor.getInt(columnsCursor.getColumnIndexOrThrow("pk")) > 0
            columns[name] = type.uppercase()
            notNulls[name] = notNull
            if (pk) primaryKeys.add(name)
        }
        columnsCursor.close()

        assertEquals("Expected 3 columns in tombstone table", 3, columns.size)
        assertEquals("TEXT", columns["sourceKey"])
        assertEquals("TEXT", columns["sourceType"])
        assertEquals("INTEGER", columns["deletedAtMillis"])
        assertTrue("sourceKey must be NOT NULL", notNulls["sourceKey"] == true)
        assertTrue("sourceType must be NOT NULL", notNulls["sourceType"] == true)
        assertTrue("deletedAtMillis must be NOT NULL", notNulls["deletedAtMillis"] == true)
        assertEquals(listOf("sourceKey"), primaryKeys)

        // Verify index on sourceType
        val indexCursor = v8Db.query("PRAGMA index_list(deleted_transaction_tombstones)")
        var foundSourceTypeIndex = false
        while (indexCursor.moveToNext()) {
            val indexName = indexCursor.getString(indexCursor.getColumnIndexOrThrow("name"))
            if (indexName == "index_deleted_transaction_tombstones_sourceType") {
                foundSourceTypeIndex = true
            }
        }
        indexCursor.close()
        assertTrue("index_deleted_transaction_tombstones_sourceType must exist", foundSourceTypeIndex)

        // 4. Verify existing v8 data was preserved intact
        val expenseCursor = v8Db.query("SELECT amount, merchant, notificationKey FROM expenses WHERE id = 42")
        assertTrue("Existing expense row 42 must be preserved", expenseCursor.moveToFirst())
        assertEquals(450.50, expenseCursor.getDouble(expenseCursor.getColumnIndexOrThrow("amount")), 0.001)
        assertEquals("Trader Joes", expenseCursor.getString(expenseCursor.getColumnIndexOrThrow("merchant")))
        assertEquals("sms_tj_001", expenseCursor.getString(expenseCursor.getColumnIndexOrThrow("notificationKey")))
        expenseCursor.close()

        val budgetCursor = v8Db.query("SELECT name, amountLimit FROM budgets WHERE id = 'budget_groceries'")
        assertTrue("Existing budget row must be preserved", budgetCursor.moveToFirst())
        assertEquals("Groceries", budgetCursor.getString(budgetCursor.getColumnIndexOrThrow("name")))
        assertEquals(5000.0, budgetCursor.getDouble(budgetCursor.getColumnIndexOrThrow("amountLimit")), 0.001)
        budgetCursor.close()

        // 5. Verify direct operations on tombstone table work in SQLite
        v8Db.execSQL(
            """
            INSERT INTO `deleted_transaction_tombstones` (`sourceKey`, `sourceType`, `deletedAtMillis`)
            VALUES ('sms_key_101', 'SMS_HISTORY', 1711000500000)
            """.trimIndent()
        )
        val tombstoneCursor = v8Db.query("SELECT * FROM deleted_transaction_tombstones WHERE sourceKey = 'sms_key_101'")
        assertTrue(tombstoneCursor.moveToFirst())
        assertEquals("SMS_HISTORY", tombstoneCursor.getString(tombstoneCursor.getColumnIndexOrThrow("sourceType")))
        assertEquals(1711000500000L, tombstoneCursor.getLong(tombstoneCursor.getColumnIndexOrThrow("deletedAtMillis")))
        tombstoneCursor.close()

        v8Db.close()
        helper.close()
    }

    // =========================================================================
    // 2. ROOM RUNTIME SCHEMA HASH VALIDATION VIA DATABASEBUILDER
    // =========================================================================

    @Test
    fun testMigration_8_to_9_withRoomDatabaseBuilder_passesSchemaValidation() = runBlocking {
        val dbName = registerDbForCleanup("test_v8_to_v9_room_validation.db")

        // 1. Create a real v8 database containing ALL tables and indices as expected by Room v8
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // 1. expenses
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
                            `categorySource` TEXT NOT NULL
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

                    // 2. known_financial_accounts
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `known_financial_accounts` (
                            `id` TEXT NOT NULL,
                            `institutionId` TEXT,
                            `institutionName` TEXT,
                            `accountSuffix` TEXT NOT NULL,
                            `instrumentType` TEXT NOT NULL,
                            `confidence` TEXT NOT NULL,
                            `source` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_financial_accounts_accountSuffix` ON `known_financial_accounts` (`accountSuffix`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_financial_accounts_institutionId` ON `known_financial_accounts` (`institutionId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_financial_accounts_instrumentType` ON `known_financial_accounts` (`instrumentType`)")

                    // 3. transaction_categories
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `transaction_categories` (
                            `id` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `iconKey` TEXT NOT NULL,
                            `colorKey` TEXT NOT NULL,
                            `isSystem` INTEGER NOT NULL,
                            `isArchived` INTEGER NOT NULL,
                            `sortOrder` INTEGER NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_categories_isArchived` ON `transaction_categories` (`isArchived`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_categories_sortOrder` ON `transaction_categories` (`sortOrder`)")

                    // 4. user_category_rules
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `user_category_rules` (
                            `id` TEXT NOT NULL,
                            `name` TEXT,
                            `matchType` TEXT NOT NULL,
                            `pattern` TEXT NOT NULL,
                            `normalizedPattern` TEXT NOT NULL,
                            `categoryId` TEXT NOT NULL,
                            `priority` INTEGER NOT NULL,
                            `isEnabled` INTEGER NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_category_rules_normalizedPattern` ON `user_category_rules` (`normalizedPattern`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_category_rules_categoryId` ON `user_category_rules` (`categoryId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_category_rules_isEnabled` ON `user_category_rules` (`isEnabled`)")

                    // 5. merchant_aliases
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `merchant_aliases` (
                            `id` TEXT NOT NULL,
                            `alias` TEXT NOT NULL,
                            `canonicalMerchant` TEXT NOT NULL,
                            `normalizedAlias` TEXT NOT NULL,
                            `isEnabled` INTEGER NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_merchant_aliases_normalizedAlias` ON `merchant_aliases` (`normalizedAlias`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_merchant_aliases_isEnabled` ON `merchant_aliases` (`isEnabled`)")

                    // 6. budgets
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `budgets` (
                            `id` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `amountLimit` REAL NOT NULL,
                            `categoryId` TEXT,
                            `periodType` TEXT NOT NULL,
                            `periodAnchor` INTEGER NOT NULL,
                            `warningThreshold` REAL NOT NULL,
                            `exceededThreshold` REAL NOT NULL,
                            `isEnabled` INTEGER NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_isEnabled` ON `budgets` (`isEnabled`)")

                    // 7. budget_alert_states
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `budget_alert_states` (
                            `id` TEXT NOT NULL,
                            `budgetId` TEXT NOT NULL,
                            `periodStart` INTEGER NOT NULL,
                            `warningSent` INTEGER NOT NULL,
                            `exceededSent` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_alert_states_budgetId` ON `budget_alert_states` (`budgetId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_alert_states_periodStart` ON `budget_alert_states` (`periodStart`)")

                    // Insert pre-existing v8 row
                    db.execSQL(
                        """
                        INSERT INTO `expenses` (
                            `id`, `amount`, `merchant`, `dateMillis`, `type`, `notificationKey`,
                            `isPending`, `source`, `categorySource`
                        ) VALUES (
                            100, 199.99, 'Test Store', 1711200000000, 'Debit', 'sms_ts_100',
                            0, 'SMS', 'USER_ASSIGNED'
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = helperFactory.create(config)
        val initialDb = helper.writableDatabase
        assertEquals("Database should be at version 8", 8, initialDb.version)
        initialDb.close()
        helper.close()

        // 2. Open via Room DatabaseBuilder adding MIGRATION_8_9
        // This triggers Room's migration execution AND onValidateSchema verification!
        val roomDb = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName
        )
        .addMigrations(AppDatabase.MIGRATION_8_9)
        .build()

        // 3. Verify Room operations succeed without schema mismatch
        val expenseDao = roomDb.expenseDao()

        // Existing expense row must be accessible
        val expenses = expenseDao.getAllExpensesList()
        assertEquals(1, expenses.size)
        assertEquals(100, expenses[0].id)
        assertEquals(199.99, expenses[0].amount, 0.001)
        assertEquals("Test Store", expenses[0].merchant)

        // Tombstone DAO methods must succeed
        val notTombstoned = expenseDao.isSourceKeyTombstoned("sms_ts_100")
        assertFalse("Key should not be tombstoned initially", notTombstoned)

        expenseDao.insertTombstone(
            DeletedTransactionTombstone(
                sourceKey = "sms_ts_100",
                sourceType = "SMS_HISTORY",
                deletedAtMillis = 1711200500000L
            )
        )

        val isTombstoned = expenseDao.isSourceKeyTombstoned("sms_ts_100")
        assertTrue("Key should be reported as tombstoned after insertion", isTombstoned)

        val tombstonedList = expenseDao.getAllTombstonedSmsSourceKeys()
        assertTrue("Tombstoned list should contain key", tombstonedList.contains("sms_ts_100"))

        roomDb.close()
    }

    // =========================================================================
    // 3. FULL SEQUENTIAL MIGRATION CHAIN (v1 -> v9) VERIFICATION
    // =========================================================================

    @Test
    fun testSequentialMigrationChain_1_to_9_preservesData() {
        val dbName = registerDbForCleanup("test_migration_chain_1_to_9.db")

        // 1. Create a real v1 SQLite database with initial schema
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
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
                            `source` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_notificationKey` ON `expenses` (`notificationKey`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_dateMillis` ON `expenses` (`dateMillis`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_isPending` ON `expenses` (`isPending`)")

                    // Insert an original transaction with tag 'Food'
                    db.execSQL(
                        """
                        INSERT INTO `expenses` (
                            `id`, `amount`, `merchant`, `dateMillis`, `type`, `notificationKey`,
                            `isPending`, `rawText`, `tag`, `note`, `source`
                        ) VALUES (
                            1, 1250.0, 'Swiggy', 1600000000000, 'Debit', 'sms_chain_1',
                            0, 'Paid Rs.1250 on Swiggy', 'Food', 'Dinner with family', 'SMS'
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = helperFactory.create(config)
        val db = helper.writableDatabase

        // Verify initial v1 record
        var cursor = db.query("SELECT amount, merchant, tag, note FROM expenses WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(1250.0, cursor.getDouble(cursor.getColumnIndexOrThrow("amount")), 0.01)
        assertEquals("Swiggy", cursor.getString(cursor.getColumnIndexOrThrow("merchant")))
        assertEquals("Food", cursor.getString(cursor.getColumnIndexOrThrow("tag")))
        cursor.close()

        // 2. Sequentially run all migrations 1 -> 9
        AppDatabase.MIGRATION_1_2.migrate(db)
        AppDatabase.MIGRATION_2_3.migrate(db)
        AppDatabase.MIGRATION_3_4.migrate(db)
        AppDatabase.MIGRATION_4_5.migrate(db)
        AppDatabase.MIGRATION_5_6.migrate(db)
        AppDatabase.MIGRATION_6_7.migrate(db)
        AppDatabase.MIGRATION_7_8.migrate(db)
        AppDatabase.MIGRATION_8_9.migrate(db)

        // 3. Verify original transaction survived and was properly transformed:
        // - amount, merchant, note intact
        // - tag 'Food' migrated to categoryId 'food_dining' by MIGRATION_4_5
        // - categorySource backfilled to 'USER_ASSIGNED' by MIGRATION_5_6
        cursor = db.query("SELECT amount, merchant, categoryId, categorySource, note FROM expenses WHERE id = 1")
        assertTrue("Transaction must survive entire migration chain", cursor.moveToFirst())
        assertEquals(1250.0, cursor.getDouble(cursor.getColumnIndexOrThrow("amount")), 0.01)
        assertEquals("Swiggy", cursor.getString(cursor.getColumnIndexOrThrow("merchant")))
        assertEquals("food_dining", cursor.getString(cursor.getColumnIndexOrThrow("categoryId")))
        assertEquals("USER_ASSIGNED", cursor.getString(cursor.getColumnIndexOrThrow("categorySource")))
        assertEquals("Dinner with family", cursor.getString(cursor.getColumnIndexOrThrow("note")))
        cursor.close()

        // 4. Verify all 8 tables now exist and are queryable
        val expectedTables = listOf(
            "expenses",
            "known_financial_accounts",
            "transaction_categories",
            "user_category_rules",
            "merchant_aliases",
            "budgets",
            "budget_alert_states",
            "deleted_transaction_tombstones"
        )
        for (table in expectedTables) {
            val tableCheck = db.query("SELECT COUNT(*) FROM `$table`")
            assertTrue("Table $table must exist and be queryable", tableCheck.moveToFirst())
            tableCheck.close()
        }

        // 5. Verify tombstone table operates correctly on the migrated database
        db.execSQL(
            """
            INSERT INTO `deleted_transaction_tombstones` (`sourceKey`, `sourceType`, `deletedAtMillis`)
            VALUES ('sms_chain_1', 'SMS_HISTORY', 1600000500000)
            """.trimIndent()
        )
        val tombCheck = db.query("SELECT sourceType FROM deleted_transaction_tombstones WHERE sourceKey = 'sms_chain_1'")
        assertTrue(tombCheck.moveToFirst())
        assertEquals("SMS_HISTORY", tombCheck.getString(0))
        tombCheck.close()

        db.close()
        helper.close()
    }
}
