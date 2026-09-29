package com.subhashrelangi.arctracker

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Milestone 5: Expense <-> KnownFinancialAccount Persistence & Safe Reconciliation Device Tests.
 *
 * Runs on connected physical Samsung Galaxy A34 5G (Android 16).
 * Verifies Room v3 -> v4 migration, account persistence, restart survival, and notification stability.
 */
@RunWith(AndroidJUnit4::class)
class ExpenseAccountPersistenceDeviceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var accountRepo: KnownFinancialAccountRepository
    private lateinit var matcher: KnownFinancialAccountMatcher

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = AppDatabase.createInMemoryDatabase(context)
        accountRepo = KnownFinancialAccountRepository(database.knownFinancialAccountDao())
        matcher = KnownFinancialAccountMatcher(accountRepo)
        TransactionPersistenceManager.matcher = matcher
        NotificationPermissionHelper.permissionOverrideForTesting = true
        NotificationReaderService.clearActiveNotifications()
    }

    @After
    fun tearDown() {
        database.close()
        TransactionPersistenceManager.matcher = null
        NotificationPermissionHelper.permissionOverrideForTesting = null
        NotificationReaderService.clearActiveNotifications()
    }

    // ==================================================
    // 1. REAL DEVICE DATABASE MIGRATION (v3 -> v4)
    // ==================================================
    @Test
    fun test01_device_databaseMigration_v3_to_v4() {
        val dbName = "test_device_migration_v3_to_v4.db"
        context.deleteDatabase(dbName)

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create Version 3 Schema: expenses with relationship columns + known_financial_accounts
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
                            `relationshipId` TEXT
                        )
                        """.trimIndent()
                    )
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
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val v3Db = helper.writableDatabase

        // Insert legacy expense row with relationship
        v3Db.execSQL(
            """
            INSERT INTO expenses (id, amount, merchant, dateMillis, type, notificationKey, isPending, source, relationshipType, relationshipId)
            VALUES (1, 150.0, 'Swiggy', 1700000000000, 'Debit', 'notif_1', 0, 'NOTIFICATION', 'SELF_TRANSFER', 'REL_999')
            """.trimIndent()
        )

        // Insert known financial account in v3
        v3Db.execSQL(
            """
            INSERT INTO known_financial_accounts (id, institutionId, institutionName, accountSuffix, instrumentType, confidence, source, createdAt, updatedAt)
            VALUES ('hdfc_bank_account_9020', 'hdfc', 'HDFC Bank', '9020', 'BANK_ACCOUNT', 'HIGH', 'HISTORICAL_SMS', 1000, 1000)
            """.trimIndent()
        )

        // Run MIGRATION_3_4
        AppDatabase.MIGRATION_3_4.migrate(v3Db)

        // Verify columns and legacy data intact
        val cursor = v3Db.query("SELECT id, amount, merchant, relationshipType, relationshipId, accountId, accountSuffix FROM expenses WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(1, cursor.getInt(0))
        assertEquals(150.0, cursor.getDouble(1), 0.001)
        assertEquals("Swiggy", cursor.getString(2))
        assertEquals("SELF_TRANSFER", cursor.getString(3))
        assertEquals("REL_999", cursor.getString(4))
        assertTrue("accountId must be NULL for legacy row", cursor.isNull(5))
        assertTrue("accountSuffix must be NULL for legacy row", cursor.isNull(6))
        cursor.close()

        // Verify known_financial_accounts data intact
        val accCursor = v3Db.query("SELECT id, institutionName FROM known_financial_accounts WHERE id = 'hdfc_bank_account_9020'")
        assertTrue(accCursor.moveToFirst())
        assertEquals("hdfc_bank_account_9020", accCursor.getString(0))
        assertEquals("HDFC Bank", accCursor.getString(1))
        accCursor.close()

        v3Db.close()
        context.deleteDatabase(dbName)
    }

    // ==================================================
    // 2. NEW LIVE NOTIFICATION WITH KNOWN ACCOUNT
    // ==================================================
    @Test
    fun test02_device_newLiveNotification_withKnownAccount_persistsRelationship() = runBlocking {
        accountRepo.upsert(
            KnownFinancialAccount.create("hdfc", "HDFC Bank", "9020", InstrumentType.BANK_ACCOUNT)
        )

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            title = "HDFCBK",
            text = "Rs 100 transferred to Subhash from XXXXX9020 on 28-Sep-26. Ref 123456",
            postTime = System.currentTimeMillis(),
            notificationKey = "key_device_02"
        )

        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, database.expenseDao(), matcher)
        assertEquals(1, results.size)
        val inserted = (results[0] as ExpensePersistenceResult.Inserted).expense

        assertEquals("hdfc_bank_account_9020", inserted.accountId)
        assertEquals("9020", inserted.accountSuffix)

        val inDb = database.expenseDao().getExpenseById(inserted.id)
        assertNotNull(inDb)
        assertEquals("hdfc_bank_account_9020", inDb?.accountId)
        assertEquals("9020", inDb?.accountSuffix)
    }

    // ==================================================
    // 3. NEW NOTIFICATION WITH UNKNOWN SUFFIX
    // ==================================================
    @Test
    fun test03_device_newNotification_withUnknownSuffix_preservesSuffixLeavesAccountIdNull() = runBlocking {
        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            title = "BankAlert",
            text = "INR 350 debited from A/c XXXX7777 at Dominos",
            postTime = System.currentTimeMillis(),
            notificationKey = "key_device_03"
        )

        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, database.expenseDao(), matcher)
        val inserted = (results[0] as ExpensePersistenceResult.Inserted).expense

        assertNull("AccountId must remain null when suffix is not in registry", inserted.accountId)
        assertEquals("7777", inserted.accountSuffix)
    }

    // ==================================================
    // 4. AMBIGUOUS SUFFIX LEAVES ACCOUNTID NULL
    // ==================================================
    @Test
    fun test04_device_ambiguousSuffix_leavesAccountIdNull() = runBlocking {
        accountRepo.upsert(KnownFinancialAccount.create("hdfc", "HDFC Bank", "9020", InstrumentType.BANK_ACCOUNT))
        accountRepo.upsert(KnownFinancialAccount.create("apgb", "APGB Bank", "9020", InstrumentType.BANK_ACCOUNT))

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            title = "Alert",
            text = "Rs 200 debited from A/c XXXXX9020 for payment",
            postTime = System.currentTimeMillis(),
            notificationKey = "key_device_04"
        )

        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, database.expenseDao(), matcher)
        val inserted = (results[0] as ExpensePersistenceResult.Inserted).expense

        assertNull("Must not link account when ambiguous across multiple institutions", inserted.accountId)
        assertEquals("9020", inserted.accountSuffix)
    }

    // ==================================================
    // 5. HISTORICAL SMS SELECTED IMPORT
    // ==================================================
    @Test
    fun test05_device_historicalSmsSelectedImport_accountIdPersists() = runBlocking {
        val identity = FinancialAccountIdentity("hdfc", "HDFC Bank", "9020", null, InstrumentType.BANK_ACCOUNT, IdentityConfidence.HIGH)
        val groupId = AccountIdentityExtractor.generateGroupId(identity)

        val candidate = StructuredTransactionCandidate(
            sourceNotificationKey = "sms_dev_05",
            packageName = "com.google.android.apps.messaging",
            postTime = System.currentTimeMillis(),
            amount = 450.0,
            merchant = "Starbucks",
            direction = TransactionDirection.DEBIT,
            accountSuffix = "9020",
            bank = "HDFC Bank"
        )
        val validated = ValidatedTransactionCandidate(candidate, ValidationState.ACCEPTABLE, EvidenceLevel.STRONG, true)
        val sourceEvent = TransactionSourceEvent(TransactionSourceType.SMS_HISTORY, "sms_dev_05", "HDFCBK", "Body", System.currentTimeMillis())
        val scannedItem = ScannedTransactionItem(validated, sourceEvent, DedupDecision.NEW_TRANSACTION, MatchStrategy.NONE, reason = "", accountIdentity = identity)

        val scanResult = SmsScanResult.Success(
            0L, 2000L, 1, 1, 0, 0, 1, 1, 0, 0, 0, 0, 450.0, 0.0,
            scannedItems = listOf(scannedItem)
        )

        val synchronizer = HistoricalSmsAccountRegistrySynchronizer(accountRepo, database.expenseDao())
        val importManager = HistoricalSmsImportManager(
            InMemorySmsReader(hasPermission = true, records = emptyList()),
            database.expenseDao(),
            TransactionPersistenceManager,
            synchronizer
        )

        val res = importManager.importTransactions(scanResult, setOf(groupId))
        assertTrue(res is SmsImportResult.Success)

        val expenses = database.expenseDao().getAllExpensesList()
        val imported = expenses.find { it.notificationKey == "sms_dev_05" }
        assertNotNull(imported)
        assertEquals("hdfc_bank_account_9020", imported?.accountId)
        assertEquals("9020", imported?.accountSuffix)
    }

    // ==================================================
    // 6. PERSISTENCE SURVIVES PROCESS RESTART
    // ==================================================
    @Test
    fun test06_device_persistenceSurvivesProcessRestart() {
        runBlocking {
            val dbFile = "test_device_restart_survival.db"
            context.deleteDatabase(dbFile)

            // Step 1: Open real database and insert expense with accountId
            var persistentDb = androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, dbFile)
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
                .build()

            val expenseId = persistentDb.expenseDao().insert(
                Expense(
                    amount = 99.0,
                    merchant = "PlayStore",
                    dateMillis = System.currentTimeMillis(),
                    notificationKey = "restart_test_key",
                    accountId = "hdfc_bank_account_9020",
                    accountSuffix = "9020"
                )
            )
            persistentDb.close()

            // Step 2: Reopen database and verify values survived restart
            persistentDb = androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, dbFile)
                .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
                .build()

            val readBack = persistentDb.expenseDao().getExpenseById(expenseId.toInt())
            assertNotNull(readBack)
            assertEquals("hdfc_bank_account_9020", readBack?.accountId)
            assertEquals("9020", readBack?.accountSuffix)
            assertEquals(99.0, readBack?.amount ?: 0.0, 0.001)

            persistentDb.close()
            context.deleteDatabase(dbFile)
        }
    }

    // ==================================================
    // 7. NOTIFICATION TRACKING & SERVICE STABILITY
    // ==================================================
    @Test
    fun test07_device_notificationTrackingAndServiceRemainFunctional() = runBlocking {
        // Verify active notification tracker operates without error
        NotificationReaderService.clearActiveNotifications()
        val count = NotificationReaderService.getAllActiveNotifications().size
        assertEquals(0, count)

        // Verify duplicate notification handling maintains 1 row
        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            title = "Bank",
            text = "Rs 10 paid to Test",
            postTime = 1000L,
            notificationKey = "stable_key"
        )
        TransactionPersistenceManager.processCapturedNotificationAll(captured, database.expenseDao(), matcher)
        TransactionPersistenceManager.processCapturedNotificationAll(captured, database.expenseDao(), matcher)

        assertEquals(1, database.expenseDao().getCount())
    }
}
