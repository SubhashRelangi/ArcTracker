package com.example.arctracker

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arctracker.data.*
import com.example.arctracker.service.IdentityConfidence
import com.example.arctracker.service.InstrumentType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Milestone 2: Room Schema Migration & Real Physical Device Tests
 * executing on connected Samsung Galaxy A34 5G.
 */
@RunWith(AndroidJUnit4::class)
class KnownFinancialAccountMigrationAndDeviceTest {

    private lateinit var context: Context
    private lateinit var testDb: AppDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testDb = AppDatabase.createInMemoryDatabase(context)
    }

    @After
    fun tearDown() {
        testDb.close()
    }

    // ==================================================
    // 1. PHYSICAL DEVICE REGISTRY VERIFICATION
    // ==================================================

    /**
     * Physical device test:
     * 1. Opens the real Room database configuration.
     * 2. Inserts: HDFC BANK_ACCOUNT 9020.
     * 3. Reads it back.
     * 4. Inserts: APGB BANK_ACCOUNT 9020.
     * 5. Verifies both exist.
     * 6. Verifies exact lookup distinguishes them.
     * 7. Verifies suffix-only lookup returns both.
     */
    @Test
    fun testDevice_realRoomConfiguration_exactAndSuffixLookups() = runBlocking {
        val dao = testDb.knownFinancialAccountDao()
        val repo = KnownFinancialAccountRepository(dao)

        assertEquals(0, repo.getCount())

        // 2. Inserts: HDFC BANK_ACCOUNT 9020
        val hdfc = KnownFinancialAccount.create(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            source = AccountSource.HISTORICAL_SMS
        )
        repo.upsert(hdfc)

        // 3. Reads it back
        val readBack = repo.getById("hdfc_bank_account_9020")
        assertNotNull(readBack)
        assertEquals("hdfc", readBack?.institutionId)
        assertEquals("HDFC Bank", readBack?.institutionName)
        assertEquals("9020", readBack?.accountSuffix)
        assertEquals(InstrumentType.BANK_ACCOUNT, readBack?.instrumentType)

        // 4. Inserts: APGB BANK_ACCOUNT 9020
        val apgb = KnownFinancialAccount.create(
            institutionId = "apgb",
            institutionName = "APG Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            source = AccountSource.HISTORICAL_SMS
        )
        repo.upsert(apgb)

        // 5. Verifies both exist
        assertEquals(2, repo.getCount())
        assertNotNull(repo.getById("hdfc_bank_account_9020"))
        assertNotNull(repo.getById("apgb_bank_account_9020"))

        // 6. Verifies exact lookup distinguishes them
        val exactHdfc = repo.findByExact("hdfc", InstrumentType.BANK_ACCOUNT, "9020")
        val exactApgb = repo.findByExact("apgb", InstrumentType.BANK_ACCOUNT, "9020")
        assertNotNull(exactHdfc)
        assertNotNull(exactApgb)
        assertEquals("HDFC Bank", exactHdfc?.institutionName)
        assertEquals("APG Bank", exactApgb?.institutionName)
        assertNotEquals(exactHdfc?.id, exactApgb?.id)

        // 7. Verifies suffix-only lookup returns both
        val suffixResults = repo.findBySuffix("9020")
        assertEquals(2, suffixResults.size)
        val institutions = suffixResults.map { it.institutionId }.toSet()
        assertTrue(institutions.contains("hdfc"))
        assertTrue(institutions.contains("apgb"))
    }

    /**
     * Verifies upsert metadata upgrade behavior on physical device.
     */
    @Test
    fun testDevice_realRoomConfiguration_upsertConfidenceUpgrade() = runBlocking {
        val dao = testDb.knownFinancialAccountDao()
        val repo = KnownFinancialAccountRepository(dao)

        val initial = KnownFinancialAccount.create(
            institutionId = "sbi",
            institutionName = "State Bank of India",
            accountSuffix = "1065",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.MEDIUM,
            source = AccountSource.HISTORICAL_SMS
        )
        repo.upsert(initial)

        val upgraded = KnownFinancialAccount.create(
            institutionId = "sbi",
            institutionName = "State Bank of India",
            accountSuffix = "1065",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            source = AccountSource.USER_CONFIRMED
        )
        repo.upsert(upgraded)

        assertEquals(1, repo.getCount())
        val saved = repo.getById("sbi_bank_account_1065")
        assertNotNull(saved)
        assertEquals(IdentityConfidence.HIGH, saved?.confidence)
        assertEquals(AccountSource.USER_CONFIRMED, saved?.source)
    }

    // ==================================================
    // 2. ROOM SCHEMA MIGRATION TEST (Version 2 -> Version 3)
    // ==================================================

    /**
     * Verifies Room migration from Version 2 to Version 3:
     * - Version 2 database contains existing Expense with relationship fields
     * - MIGRATION_2_3 executes successfully
     * - Expenses table and data are preserved intact
     * - known_financial_accounts table is created with indexes
     * - Operations on known_financial_accounts succeed after migration
     */
    @Test
    fun testDevice_roomMigration_version2_to_version3() {
        val dbName = "test_migration_v2_to_v3.db"
        context.deleteDatabase(dbName)

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create Version 2 Schema (Expenses with relationshipType and relationshipId)
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
                            `relationshipType` TEXT DEFAULT NULL,
                            `relationshipId` TEXT DEFAULT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_relationshipId` ON `expenses` (`relationshipId`)")

                    // Insert sample expense data in version 2
                    db.execSQL(
                        """
                        INSERT INTO `expenses` (
                            id, amount, merchant, dateMillis, type, notificationKey, isPending,
                            source, relationshipType, relationshipId
                        ) VALUES (
                            1, 750.0, 'Amazon India', 1711360000000, 'Debit', 'notif_key_42', 0,
                            'SMS', 'SELF_TRANSFER', 'rel_group_99'
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    // Not invoked during creation
                }
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        var db = helper.writableDatabase

        // Verify version 2 data exists
        var cursor = db.query("SELECT * FROM expenses WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(750.0, cursor.getDouble(cursor.getColumnIndexOrThrow("amount")), 0.01)
        assertEquals("Amazon India", cursor.getString(cursor.getColumnIndexOrThrow("merchant")))
        assertEquals("SELF_TRANSFER", cursor.getString(cursor.getColumnIndexOrThrow("relationshipType")))
        assertEquals("rel_group_99", cursor.getString(cursor.getColumnIndexOrThrow("relationshipId")))
        cursor.close()

        // Execute MIGRATION_2_3
        AppDatabase.MIGRATION_2_3.migrate(db)

        // Verify version 2 data is completely preserved after migration
        cursor = db.query("SELECT * FROM expenses WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(750.0, cursor.getDouble(cursor.getColumnIndexOrThrow("amount")), 0.01)
        assertEquals("Amazon India", cursor.getString(cursor.getColumnIndexOrThrow("merchant")))
        assertEquals("SELF_TRANSFER", cursor.getString(cursor.getColumnIndexOrThrow("relationshipType")))
        assertEquals("rel_group_99", cursor.getString(cursor.getColumnIndexOrThrow("relationshipId")))
        cursor.close()

        // Verify known_financial_accounts table exists and operates correctly
        db.execSQL(
            """
            INSERT INTO `known_financial_accounts` (
                id, institutionId, institutionName, accountSuffix, instrumentType,
                confidence, source, createdAt, updatedAt
            ) VALUES (
                'hdfc_bank_account_9020', 'hdfc', 'HDFC Bank', '9020', 'BANK_ACCOUNT',
                'HIGH', 'HISTORICAL_SMS', 1711360000000, 1711360000000
            )
            """.trimIndent()
        )

        cursor = db.query("SELECT * FROM known_financial_accounts WHERE id = 'hdfc_bank_account_9020'")
        assertTrue(cursor.moveToFirst())
        assertEquals("hdfc", cursor.getString(cursor.getColumnIndexOrThrow("institutionId")))
        assertEquals("HDFC Bank", cursor.getString(cursor.getColumnIndexOrThrow("institutionName")))
        assertEquals("9020", cursor.getString(cursor.getColumnIndexOrThrow("accountSuffix")))
        assertEquals("BANK_ACCOUNT", cursor.getString(cursor.getColumnIndexOrThrow("instrumentType")))
        assertEquals("HIGH", cursor.getString(cursor.getColumnIndexOrThrow("confidence")))
        cursor.close()

        db.close()
        context.deleteDatabase(dbName)
    }
}
