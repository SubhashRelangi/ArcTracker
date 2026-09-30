package com.subhashrelangi.arctracker

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subhashrelangi.arctracker.backup.*
import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.service.IdentityConfidence
import com.subhashrelangi.arctracker.service.InstrumentType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Milestone 15: Export / Import / Backup Physical Device & Instrumentation Tests.
 *
 * Runs on connected physical Android device (Samsung Galaxy A34 5G, Android 16).
 *
 * Verifies:
 * - Real Room SQLite database operations and transactions.
 * - Streaming CSV export with masked suffixes and RFC 4180 escaping.
 * - Full JSON backup generation with self-validation and SHA-256 checksums.
 * - Safe pre-import validation preventing corruption from malformed/future-version files.
 * - Strict atomicity and complete rollback on transaction failure.
 * - MERGE mode protection of USER_ASSIGNED categories and manual notes.
 * - Multi-account identity preservation (same suffix across banks; cards vs bank accounts).
 * - FULL RESTORE mode cleanly replacing datasets while preserving system categories.
 */
@RunWith(AndroidJUnit4::class)
class BackupDeviceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var expenseDao: ExpenseDao
    private lateinit var categoryDao: TransactionCategoryDao
    private lateinit var accountDao: KnownFinancialAccountDao
    private lateinit var ruleDao: UserCategoryRuleDao
    private lateinit var aliasDao: MerchantAliasDao
    private lateinit var budgetDao: BudgetDao
    private lateinit var backupManager: BackupManager

    private val testDbName = "m15_device_test_backup.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDbName)

        database = Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
            .allowMainThreadQueries()
            .build()

        expenseDao = database.expenseDao()
        categoryDao = database.transactionCategoryDao()
        accountDao = database.knownFinancialAccountDao()
        ruleDao = database.userCategoryRuleDao()
        aliasDao = database.merchantAliasDao()
        budgetDao = database.budgetDao()

        backupManager = BackupManager(
            database = database,
            expenseDao = expenseDao,
            categoryDao = categoryDao,
            accountDao = accountDao,
            ruleDao = ruleDao,
            aliasDao = aliasDao,
            budgetDao = budgetDao
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(testDbName)
    }

    // ==================================================
    // 1. CSV EXPORT ON REAL SQLITE
    // ==================================================

    @Test
    fun testDevice_csvExport_generatesValidCsvFromSqlite() = runBlocking {
        // Insert sample records in SQLite
        accountDao.insert(
            KnownFinancialAccount(
                id = "hdfc_acc_9020",
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT,
                confidence = IdentityConfidence.HIGH
            )
        )
        categoryDao.upsert(
            TransactionCategory(
                id = "cat_groceries",
                name = "Groceries",
                isSystem = false
            )
        )
        expenseDao.insert(
            Expense(
                id = 1,
                merchant = "Supermarket, Downtown",
                amount = 1250.75,
                dateMillis = 1711360000000L,
                type = "Debit",
                categoryId = "cat_groceries",
                categorySource = "USER_ASSIGNED",
                accountId = "hdfc_acc_9020",
                accountSuffix = "9020",
                note = "Weekly groceries, bought fruits"
            )
        )

        val output = ByteArrayOutputStream()
        val result = backupManager.exportTransactionsCsv(output)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        val csvString = output.toString("UTF-8")
        assertTrue(csvString.contains("Date,Merchant,Amount"))
        assertTrue(csvString.contains("\"Supermarket, Downtown\""))
        assertTrue(csvString.contains("1250.75"))
        assertTrue(csvString.contains("Groceries"))
        assertTrue(csvString.contains("USER_ASSIGNED"))
        assertTrue(csvString.contains("•••• 9020"))
        assertTrue(csvString.contains("\"Weekly groceries, bought fruits\""))
    }

    // ==================================================
    // 2. FULL BACKUP EXPORT & SELF-VALIDATION
    // ==================================================

    @Test
    fun testDevice_fullBackupExport_producesSelfValidatingJson() = runBlocking {
        // Populate complete entities
        categoryDao.upsert(TransactionCategory(id = "cat_dine", name = "Dining", isSystem = false))
        accountDao.insert(KnownFinancialAccount(id = "sbi_1234", accountSuffix = "1234", instrumentType = InstrumentType.BANK_ACCOUNT))
        ruleDao.insert(UserCategoryRule(id = "r_swiggy", pattern = "Swiggy", normalizedPattern = "swiggy", categoryId = "cat_dine"))
        aliasDao.insert(MerchantAlias(id = "a_swiggy", alias = "SWIGGY BANGALORE", canonicalMerchant = "Swiggy", normalizedAlias = "swiggy bangalore"))
        budgetDao.insert(Budget(id = "b_dine", name = "Dining Budget", amountLimit = 5000.0, categoryId = "cat_dine"))
        expenseDao.insert(Expense(id = 10, merchant = "Swiggy", amount = 450.0, dateMillis = 1700000000000L, categoryId = "cat_dine"))

        val output = ByteArrayOutputStream()
        val exportResult = backupManager.exportFullBackup(output)
        assertTrue(exportResult.isSuccess)

        val metadata = exportResult.getOrThrow()
        assertEquals(CURRENT_BACKUP_FORMAT_VERSION, metadata.formatVersion)
        assertEquals(1, metadata.entityCounts.transactions)
        assertEquals(1, metadata.entityCounts.categories)
        assertEquals(1, metadata.entityCounts.accounts)
        assertEquals(1, metadata.entityCounts.rules)
        assertEquals(1, metadata.entityCounts.aliases)
        assertEquals(1, metadata.entityCounts.budgets)
        assertTrue(metadata.checksum.isNotBlank())
    }

    // ==================================================
    // 3. ATOMIC MERGE & USER_ASSIGNED CATEGORY PROTECTION
    // ==================================================

    @Test
    fun testDevice_mergeImport_strictlyProtectsUserAssignedCategory() = runBlocking {
        // User manually categorized in SQLite
        expenseDao.insert(
            Expense(
                id = 42,
                merchant = "Apple Store",
                amount = 999.0,
                dateMillis = 1700000000000L,
                categoryId = "electronics",
                categorySource = "USER_ASSIGNED",
                note = "Purchased accessories"
            )
        )

        // Incoming backup has same transaction but with category "other" and categorySource = "INFERRED"
        val payload = ArcTrackerBackupPayload(
            metadata = BackupMetadata(),
            transactions = listOf(
                ExpenseBackupDto(
                    id = 42,
                    merchant = "Apple Store",
                    amount = 999.0,
                    dateMillis = 1700000000000L,
                    categoryId = "other",
                    categorySource = "INFERRED"
                )
            )
        )

        val importResult = backupManager.performImport(payload, ImportMode.MERGE)
        assertTrue(importResult.isSuccess)

        // Verify in SQLite: USER_ASSIGNED category was preserved!
        val preserved = expenseDao.getExpenseById(42)
        assertNotNull(preserved)
        assertEquals("electronics", preserved!!.categoryId)
        assertEquals("USER_ASSIGNED", preserved.categorySource)
        assertEquals("Purchased accessories", preserved.note)
    }

    // ==================================================
    // 4. MULTI-ACCOUNT IDENTITY PRESERVATION
    // ==================================================

    @Test
    fun testDevice_accountIdentityPreservation_separateInstitutionsAndCards() = runBlocking {
        val hdfcBank = AccountBackupDto(
            id = "hdfc_BANK_ACCOUNT_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = "BANK_ACCOUNT",
            confidence = "HIGH",
            source = "HISTORICAL_SMS"
        )
        val apgbBank = AccountBackupDto(
            id = "apgb_BANK_ACCOUNT_9020",
            institutionId = "apgb",
            institutionName = "APG Bank",
            accountSuffix = "9020",
            instrumentType = "BANK_ACCOUNT",
            confidence = "HIGH",
            source = "HISTORICAL_SMS"
        )
        val hdfcCard = AccountBackupDto(
            id = "hdfc_CARD_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = "CARD",
            confidence = "HIGH",
            source = "HISTORICAL_SMS"
        )

        val payload = ArcTrackerBackupPayload(
            metadata = BackupMetadata(),
            accounts = listOf(hdfcBank, apgbBank, hdfcCard)
        )

        val result = backupManager.performImport(payload, ImportMode.MERGE)
        assertTrue(result.isSuccess)

        val accounts = accountDao.getAll()
        assertEquals(3, accounts.size)

        val acc1 = accountDao.getById("hdfc_BANK_ACCOUNT_9020")
        val acc2 = accountDao.getById("apgb_BANK_ACCOUNT_9020")
        val acc3 = accountDao.getById("hdfc_CARD_9020")

        assertNotNull(acc1)
        assertNotNull(acc2)
        assertNotNull(acc3)

        assertEquals("HDFC Bank", acc1!!.institutionName)
        assertEquals(InstrumentType.BANK_ACCOUNT, acc1.instrumentType)

        assertEquals("APG Bank", acc2!!.institutionName)
        assertEquals(InstrumentType.BANK_ACCOUNT, acc2.instrumentType)

        assertEquals("HDFC Bank", acc3!!.institutionName)
        assertEquals(InstrumentType.CARD, acc3.instrumentType)
    }

    // ==================================================
    // 5. ATOMIC FULL RESTORE
    // ==================================================

    @Test
    fun testDevice_fullRestore_completelyReplacesDatasetInSqlite() = runBlocking {
        // Pre-populate old data in SQLite
        expenseDao.insert(Expense(id = 1, merchant = "Old Expense", amount = 100.0, dateMillis = 1000L))
        categoryDao.upsert(TransactionCategory(id = "old_cat", name = "Old Cat", isSystem = false))
        budgetDao.insert(Budget(id = "old_b", name = "Old Budget", amountLimit = 1000.0))

        assertEquals(1, expenseDao.getCount())

        // Backup to restore
        val payload = ArcTrackerBackupPayload(
            metadata = BackupMetadata(),
            categories = listOf(
                CategoryBackupDto(id = "restored_cat", name = "Restored Cat", isSystem = false)
            ),
            transactions = listOf(
                ExpenseBackupDto(id = 200, merchant = "Restored Expense", amount = 500.0, dateMillis = 2000L, categoryId = "restored_cat")
            ),
            budgets = listOf(
                BudgetBackupDto(id = "restored_b", name = "Restored Budget", amountLimit = 2500.0, categoryId = "restored_cat")
            )
        )

        val result = backupManager.performImport(payload, ImportMode.FULL_RESTORE)
        assertTrue(result.isSuccess)

        // Verify old records removed
        assertNull(expenseDao.getExpenseById(1))
        assertNull(categoryDao.getById("old_cat"))
        assertNull(budgetDao.getById("old_b"))

        // Verify restored records present
        assertEquals(1, expenseDao.getCount())
        assertNotNull(expenseDao.getExpenseById(200))
        assertNotNull(categoryDao.getById("restored_cat"))
        assertNotNull(budgetDao.getById("restored_b"))
    }

    // ==================================================
    // 6. ATOMIC ROLLBACK ON INVALID BACKUP
    // ==================================================

    @Test
    fun testDevice_invalidBackup_rejectedWithoutModifyingSqlite() = runBlocking {
        expenseDao.insert(Expense(id = 1, merchant = "Keep Me Safe", amount = 100.0, dateMillis = 1000L))

        // Invalid payload: unsupported version 999
        val invalidPayload = ArcTrackerBackupPayload(
            metadata = BackupMetadata(formatVersion = 999),
            transactions = listOf(
                ExpenseBackupDto(id = 2, merchant = "Intruder", amount = 50.0, dateMillis = 2000L)
            )
        )

        val result = backupManager.performImport(invalidPayload, ImportMode.FULL_RESTORE)
        assertTrue(result.isFailure)

        // Verify SQLite database remained completely unchanged!
        assertEquals(1, expenseDao.getCount())
        assertNotNull(expenseDao.getExpenseById(1))
        assertEquals("Keep Me Safe", expenseDao.getExpenseById(1)!!.merchant)
    }

    // ==================================================
    // 7. IMPORT PREVIEW GENERATION
    // ==================================================

    @Test
    fun testDevice_importPreview_doesNotMutateDatabase() = runBlocking {
        expenseDao.insert(Expense(id = 10, merchant = "Existing Tx", amount = 200.0, dateMillis = 1000L))

        val payload = ArcTrackerBackupPayload(
            metadata = BackupMetadata(),
            transactions = listOf(
                ExpenseBackupDto(id = 10, merchant = "Existing Tx", amount = 200.0, dateMillis = 1000L),
                ExpenseBackupDto(id = 20, merchant = "New Tx", amount = 300.0, dateMillis = 2000L)
            ),
            categories = listOf(CategoryBackupDto(id = "cat_pv", name = "Preview Cat"))
        )

        val json = BackupSerializer.toJsonString(payload)
        val previewResult = backupManager.generateImportPreview(ByteArrayInputStream(json.toByteArray()))
        assertTrue(previewResult.isSuccess)

        val preview = previewResult.getOrThrow()
        assertEquals(2, preview.totalTransactions)
        assertEquals(1, preview.existingTransactions)
        assertEquals(1, preview.newTransactions)
        assertEquals(1, preview.newCategories)

        // Verify zero SQLite mutations
        assertEquals(1, expenseDao.getCount())
        assertNull(categoryDao.getById("cat_pv"))
    }
}
