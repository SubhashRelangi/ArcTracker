package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.backup.*
import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.service.IdentityConfidence
import com.subhashrelangi.arctracker.service.InstrumentType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Focused JVM Unit Tests for Milestone 15: Export / Import / Backup.
 *
 * Verifies:
 * - RFC 4180 CSV export compliance, quotes, newlines, commas, Unicode escaping.
 * - JSON serialization, deserialization, and SHA-256 checksum integrity.
 * - Backup format version enforcement (rejecting unsupported/future versions).
 * - Comprehensive pre-import validation (duplicate IDs, NaN/negative amounts, broken references).
 * - Atomic Merge Import and Full Restore modes.
 * - CRITICAL: Protection of USER_ASSIGNED categories (never overwritten by inferred categories).
 * - Protection of existing user notes and custom edits during merge.
 * - Multi-account identity preservation (same suffix across different banks remains distinct; cards vs bank accounts remain distinct).
 * - Round-trip export/import fidelity across all application entities.
 */
class BackupManagerTest {

    private lateinit var fakeExpenseDao: FakeExpenseDao
    private lateinit var fakeCategoryDao: FakeTransactionCategoryDao
    private lateinit var fakeAccountDao: FakeKnownFinancialAccountDao
    private lateinit var fakeRuleDao: FakeUserCategoryRuleDao
    private lateinit var fakeAliasDao: FakeMerchantAliasDao
    private lateinit var fakeBudgetDao: FakeBudgetDao
    private lateinit var backupManager: BackupManager

    @Before
    fun setUp() {
        fakeExpenseDao = FakeExpenseDao()
        fakeCategoryDao = FakeTransactionCategoryDao()
        fakeAccountDao = FakeKnownFinancialAccountDao()
        fakeRuleDao = FakeUserCategoryRuleDao()
        fakeAliasDao = FakeMerchantAliasDao()
        fakeBudgetDao = FakeBudgetDao()

        backupManager = BackupManager(
            database = null, // In JVM testing, null database executes directly in-memory
            expenseDao = fakeExpenseDao,
            categoryDao = fakeCategoryDao,
            accountDao = fakeAccountDao,
            ruleDao = fakeRuleDao,
            aliasDao = fakeAliasDao,
            budgetDao = fakeBudgetDao
        )
    }

    // ==================================================
    // 1. CSV EXPORT TESTING
    // ==================================================

    @Test
    fun testCsv_emptyList_exportsHeaderOnly() {
        val csv = CsvExporter.generateCsvString(emptyList())
        val lines = csv.trim().lines()
        assertEquals(1, lines.size)
        assertTrue(lines[0].startsWith("Date,Merchant,Amount"))
    }

    @Test
    fun testCsv_singleTransaction_formatsExpectedColumns() {
        val expense = Expense(
            id = 1,
            merchant = "Starbucks",
            amount = 350.0,
            dateMillis = 1711360000000L,
            type = "Debit",
            tag = "Food & Dining",
            categoryId = "food_dining",
            categorySource = "USER_ASSIGNED",
            accountId = "hdfc_acc_1",
            accountSuffix = "9020",
            note = "Coffee with friends"
        )
        val csv = CsvExporter.generateCsvString(listOf(expense))
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("Starbucks"))
        assertTrue(lines[1].contains("350.00"))
        assertTrue(lines[1].contains("Debit"))
        assertTrue(lines[1].contains("•••• 9020"))
        assertTrue(lines[1].contains("Coffee with friends"))
    }

    @Test
    fun testCsv_specialCharacters_quotesCommasAndNewlinesEscaped() {
        val expense = Expense(
            id = 2,
            merchant = "Bookstore, \"Main St\"",
            amount = 120.50,
            dateMillis = 1711360000000L,
            type = "Debit",
            note = "Line 1\nLine 2 with, comma and \"quotes\""
        )
        val csv = CsvExporter.generateCsvString(listOf(expense))
        assertTrue(csv.contains("\"Bookstore, \"\"Main St\"\"\""))
        assertTrue(csv.contains("\"Line 1\nLine 2 with, comma and \"\"quotes\"\"\""))
    }

    @Test
    fun testCsv_selfTransferAndPending_representedAccurately() {
        val expense = Expense(
            id = 3,
            merchant = "Transfer to Savings",
            amount = 5000.0,
            dateMillis = 1711360000000L,
            type = "Debit",
            isPending = true,
            relationshipType = TransactionRelationshipType.SELF_TRANSFER
        )
        val csv = CsvExporter.generateCsvString(listOf(expense))
        assertTrue(csv.contains(",Yes,Yes,")) // isPending=Yes, isSelfTransfer=Yes
    }

    @Test
    fun testCsv_unicodeCharacters_handledCleanly() {
        val expense = Expense(
            id = 4,
            merchant = "Café Müller München ₹",
            amount = 25.0,
            dateMillis = 1711360000000L,
            note = "€uro and ₹upee symbols: ☕"
        )
        val csv = CsvExporter.generateCsvString(listOf(expense))
        assertTrue(csv.contains("Café Müller München ₹"))
        assertTrue(csv.contains("€uro and ₹upee symbols: ☕"))
    }

    // ==================================================
    // 2. SERIALIZATION, INTEGRITY & VERSIONING
    // ==================================================

    @Test
    fun testBackup_serializationRoundTrip_matchesOriginal() {
        val payload = ArcTrackerBackupPayload(
            metadata = BackupMetadata(
                formatVersion = 1,
                appVersion = "1.0.0",
                databaseSchemaVersion = 8
            ),
            categories = listOf(
                CategoryBackupDto(id = "cat_test", name = "Test Category")
            ),
            accounts = listOf(
                AccountBackupDto(id = "acc_test", accountSuffix = "1234", instrumentType = "BANK_ACCOUNT", confidence = "HIGH", source = "HISTORICAL_SMS")
            ),
            transactions = listOf(
                ExpenseBackupDto(id = 10, amount = 100.0, merchant = "Merchant A", dateMillis = 1700000000000L)
            )
        )

        val json = BackupSerializer.toJsonString(payload)
        val deserialized = BackupSerializer.fromJsonString(json)

        assertEquals(1, deserialized.categories.size)
        assertEquals("Test Category", deserialized.categories[0].name)
        assertEquals(1, deserialized.accounts.size)
        assertEquals("1234", deserialized.accounts[0].accountSuffix)
        assertEquals(1, deserialized.transactions.size)
        assertEquals("Merchant A", deserialized.transactions[0].merchant)
        assertNotNull(deserialized.metadata.checksum)
    }

    @Test
    fun testBackup_futureUnsupportedVersion_rejected() {
        val payload = ArcTrackerBackupPayload(
            metadata = BackupMetadata(
                formatVersion = 999, // Future unsupported version
                appVersion = "99.0"
            )
        )
        val validation = BackupValidator.validate(payload)
        assertTrue(validation is ValidationResult.Failure)
        val errorMsg = (validation as ValidationResult.Failure).errors.joinToString()
        assertTrue(errorMsg.contains("Unsupported backup format version: 999"))
    }

    @Test
    fun testBackup_invalidNegativeVersion_rejected() {
        val payload = ArcTrackerBackupPayload(
            metadata = BackupMetadata(
                formatVersion = 0
            )
        )
        val validation = BackupValidator.validate(payload)
        assertTrue(validation is ValidationResult.Failure)
    }

    // ==================================================
    // 3. PRE-IMPORT VALIDATION TESTING
    // ==================================================

    @Test
    fun testValidation_missingMerchantOrNegativeAmount_rejected() {
        val payload = ArcTrackerBackupPayload(
            metadata = BackupMetadata(),
            transactions = listOf(
                ExpenseBackupDto(id = 1, amount = -50.0, merchant = "Store", dateMillis = 1700000000000L),
                ExpenseBackupDto(id = 2, amount = 100.0, merchant = "  ", dateMillis = 1700000000000L),
                ExpenseBackupDto(id = 3, amount = Double.NaN, merchant = "Store", dateMillis = 1700000000000L)
            )
        )
        val validation = BackupValidator.validate(payload)
        assertTrue(validation is ValidationResult.Failure)
        val errors = (validation as ValidationResult.Failure).errors
        assertEquals(3, errors.size)
    }

    @Test
    fun testValidation_duplicateIdsWithinBackup_rejected() {
        val payload = ArcTrackerBackupPayload(
            metadata = BackupMetadata(),
            categories = listOf(
                CategoryBackupDto(id = "cat_dup", name = "Category 1"),
                CategoryBackupDto(id = "cat_dup", name = "Category 2")
            ),
            transactions = listOf(
                ExpenseBackupDto(id = 101, amount = 10.0, merchant = "M1", dateMillis = 1000L),
                ExpenseBackupDto(id = 101, amount = 20.0, merchant = "M2", dateMillis = 2000L)
            )
        )
        val validation = BackupValidator.validate(payload)
        assertTrue(validation is ValidationResult.Failure)
        val errors = (validation as ValidationResult.Failure).errors
        assertTrue(errors.any { it.contains("Duplicate category id") })
        assertTrue(errors.any { it.contains("Duplicate transaction id") })
    }

    @Test
    fun testValidation_brokenCategoryReference_rejected() {
        val payload = ArcTrackerBackupPayload(
            metadata = BackupMetadata(),
            transactions = listOf(
                ExpenseBackupDto(id = 1, amount = 50.0, merchant = "Store", dateMillis = 1000L, categoryId = "cat_ghost_non_existent")
            )
        )
        val validation = BackupValidator.validate(payload)
        assertTrue(validation is ValidationResult.Failure)
        val errors = (validation as ValidationResult.Failure).errors
        assertTrue(errors.any { it.contains("references non-existent category 'cat_ghost_non_existent'") })
    }

    @Test
    fun testValidation_brokenBudgetCategoryReference_rejected() {
        val payload = ArcTrackerBackupPayload(
            metadata = BackupMetadata(),
            budgets = listOf(
                BudgetBackupDto(id = "b1", name = "Budget 1", amountLimit = 5000.0, categoryId = "cat_unknown_missing")
            )
        )
        val validation = BackupValidator.validate(payload)
        assertTrue(validation is ValidationResult.Failure)
        val errors = (validation as ValidationResult.Failure).errors
        assertTrue(errors.any { it.contains("references non-existent category 'cat_unknown_missing'") })
    }

    // ==================================================
    // 4. IMPORT PREVIEW
    // ==================================================

    @Test
    fun testImportPreview_calculatesAccurateCounts_withoutMutatingDb() = runBlocking {
        // Pre-populate database with 1 transaction
        fakeExpenseDao.insert(Expense(id = 1, merchant = "Existing Expense", amount = 100.0, dateMillis = 1000L))

        val payload = ArcTrackerBackupPayload(
            metadata = BackupMetadata(),
            transactions = listOf(
                ExpenseBackupDto(id = 1, amount = 100.0, merchant = "Existing Expense", dateMillis = 1000L),
                ExpenseBackupDto(id = 2, amount = 200.0, merchant = "New Expense", dateMillis = 2000L)
            ),
            categories = listOf(CategoryBackupDto(id = "cat_new", name = "New Cat")),
            accounts = listOf(AccountBackupDto(id = "acc_new", accountSuffix = "9020", instrumentType = "BANK_ACCOUNT", confidence = "HIGH", source = "OTHER"))
        )

        val json = BackupSerializer.toJsonString(payload)
        val previewResult = backupManager.generateImportPreview(ByteArrayInputStream(json.toByteArray()))
        assertTrue(previewResult.isSuccess)

        val preview = previewResult.getOrThrow()
        assertEquals(2, preview.totalTransactions)
        assertEquals(1, preview.existingTransactions)
        assertEquals(1, preview.newTransactions)
        assertEquals(1, preview.newCategories)
        assertEquals(1, preview.newAccounts)

        // Verify DB was NOT mutated during preview
        assertEquals(1, fakeExpenseDao.getCount())
        assertEquals(0, fakeCategoryDao.count())
    }

    // ==================================================
    // 5. USER_ASSIGNED CATEGORY PROTECTION (SECTION 21)
    // ==================================================

    @Test
    fun testMergeImport_userAssignedCategory_strictlyProtected() = runBlocking {
        // Step 1: User manually assigned category "food_dining" to transaction 1
        fakeExpenseDao.insert(
            Expense(
                id = 1,
                merchant = "Grocery Mart",
                amount = 500.0,
                dateMillis = 1700000000000L,
                categoryId = "food_dining",
                categorySource = "USER_ASSIGNED",
                note = "User manual note"
            )
        )

        // Step 2: An imported backup has transaction 1 categorized as "other" with categorySource = "INFERRED"
        val payload = ArcTrackerBackupPayload(
            metadata = BackupMetadata(),
            transactions = listOf(
                ExpenseBackupDto(
                    id = 1,
                    merchant = "Grocery Mart",
                    amount = 500.0,
                    dateMillis = 1700000000000L,
                    categoryId = "other",
                    categorySource = "INFERRED"
                )
            )
        )

        val result = backupManager.performImport(payload, ImportMode.MERGE)
        assertTrue(result.isSuccess)

        // Step 3: Verify the existing USER_ASSIGNED category was NOT overwritten!
        val preserved = fakeExpenseDao.getExpenseById(1)
        assertNotNull(preserved)
        assertEquals("food_dining", preserved!!.categoryId)
        assertEquals("USER_ASSIGNED", preserved.categorySource)
        assertEquals("User manual note", preserved.note)
    }

    // ==================================================
    // 6. ACCOUNT IDENTITY PRESERVATION
    // ==================================================

    @Test
    fun testAccountIdentity_sameSuffixDifferentInstitutions_remainDistinct() = runBlocking {
        val hdfc = AccountBackupDto(
            id = "hdfc_BANK_ACCOUNT_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = "BANK_ACCOUNT",
            confidence = "HIGH",
            source = "HISTORICAL_SMS"
        )
        val apgb = AccountBackupDto(
            id = "apgb_BANK_ACCOUNT_9020",
            institutionId = "apgb",
            institutionName = "APG Bank",
            accountSuffix = "9020",
            instrumentType = "BANK_ACCOUNT",
            confidence = "HIGH",
            source = "HISTORICAL_SMS"
        )
        val card = AccountBackupDto(
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
            accounts = listOf(hdfc, apgb, card)
        )

        val result = backupManager.performImport(payload, ImportMode.MERGE)
        assertTrue(result.isSuccess)

        val accounts = fakeAccountDao.getAll()
        assertEquals(3, accounts.size)

        assertNotNull(fakeAccountDao.getById("hdfc_BANK_ACCOUNT_9020"))
        assertNotNull(fakeAccountDao.getById("apgb_BANK_ACCOUNT_9020"))
        assertNotNull(fakeAccountDao.getById("hdfc_CARD_9020"))
    }

    // ==================================================
    // 7. FULL RESTORE OPERATION
    // ==================================================

    @Test
    fun testFullRestore_completelyReplacesDataset_preservesSystemCategories() = runBlocking {
        // Pre-populate system category and custom data in DB
        fakeCategoryDao.upsert(TransactionCategory(id = "food_dining", name = "Food & Dining", isSystem = true))
        fakeCategoryDao.upsert(TransactionCategory(id = "old_custom", name = "Old Custom", isSystem = false))
        fakeExpenseDao.insert(Expense(id = 99, merchant = "Old Expense", amount = 10.0, dateMillis = 1000L))

        // Backup has new data
        val payload = ArcTrackerBackupPayload(
            metadata = BackupMetadata(),
            categories = listOf(
                CategoryBackupDto(id = "new_custom", name = "New Custom", isSystem = false)
            ),
            transactions = listOf(
                ExpenseBackupDto(id = 201, merchant = "Restored Expense", amount = 250.0, dateMillis = 5000L)
            ),
            budgets = listOf(
                BudgetBackupDto(id = "b_restored", name = "Restored Budget", amountLimit = 1500.0)
            )
        )

        val result = backupManager.performImport(payload, ImportMode.FULL_RESTORE)
        assertTrue(result.isSuccess)

        // Old custom category and old expense are gone
        assertNull(fakeCategoryDao.getById("old_custom"))
        assertNull(fakeExpenseDao.getExpenseById(99))

        // System category preserved, new custom category restored
        assertNotNull(fakeCategoryDao.getById("food_dining"))
        assertNotNull(fakeCategoryDao.getById("new_custom"))

        // Restored expense and budget exist
        assertNotNull(fakeExpenseDao.getExpenseById(201))
        assertNotNull(fakeBudgetDao.getById("b_restored"))
    }

    // ==================================================
    // 8. COMPLETE ROUND-TRIP FIDELITY TEST
    // ==================================================

    @Test
    fun testRoundTrip_completeRealisticDataset_perfectFidelity() = runBlocking {
        // Populate DB with realistic dataset
        fakeAccountDao.insert(KnownFinancialAccount(id = "sbi_acc_1065", institutionId = "sbi", institutionName = "SBI", accountSuffix = "1065", instrumentType = InstrumentType.BANK_ACCOUNT, confidence = IdentityConfidence.HIGH))
        fakeCategoryDao.upsert(TransactionCategory(id = "cat_tech", name = "Electronics", isSystem = false))
        fakeRuleDao.insert(UserCategoryRule(id = "r_amazon", pattern = "Amazon", normalizedPattern = "amazon", categoryId = "cat_tech"))
        fakeAliasDao.insert(MerchantAlias(id = "a_amzn", alias = "AMZN MKTP", canonicalMerchant = "Amazon", normalizedAlias = "amzn mktp"))
        fakeBudgetDao.insert(Budget(id = "b_monthly_tech", name = "Tech Budget", amountLimit = 15000.0, categoryId = "cat_tech"))

        // Normal expense, pending expense, self-transfer, income
        fakeExpenseDao.insert(Expense(id = 1, merchant = "Amazon", amount = 3499.0, dateMillis = 1710000000000L, type = "Debit", categoryId = "cat_tech", categorySource = "RULE", accountId = "sbi_acc_1065", accountSuffix = "1065"))
        fakeExpenseDao.insert(Expense(id = 2, merchant = "Uber", amount = 420.0, dateMillis = 1710005000000L, type = "Debit", isPending = true))
        fakeExpenseDao.insert(Expense(id = 3, merchant = "Transfer to Wallet", amount = 1000.0, dateMillis = 1710010000000L, type = "Debit", relationshipType = TransactionRelationshipType.SELF_TRANSFER))
        fakeExpenseDao.insert(Expense(id = 4, merchant = "Salary Deposit", amount = 75000.0, dateMillis = 1710015000000L, type = "Credit"))

        // Step 1: Export to full backup stream
        val exportBuffer = ByteArrayOutputStream()
        val exportResult = backupManager.exportFullBackup(exportBuffer)
        assertTrue(exportResult.isSuccess)

        // Step 2: Clear database
        fakeExpenseDao.clearAll()
        fakeCategoryDao.deleteCustomCategories()
        fakeAccountDao.deleteAll()
        fakeRuleDao.deleteAll()
        fakeAliasDao.deleteAll()
        fakeBudgetDao.clearAll()

        assertEquals(0, fakeExpenseDao.getCount())
        assertEquals(0, fakeAccountDao.getCount())

        // Step 3: Read back and Restore
        val importPayload = BackupSerializer.readPayload(ByteArrayInputStream(exportBuffer.toByteArray()))
        val restoreResult = backupManager.performImport(importPayload, ImportMode.FULL_RESTORE)
        assertTrue(restoreResult.isSuccess)

        // Step 4: Verify 100% fidelity
        assertEquals(4, fakeExpenseDao.getCount())
        assertEquals(1, fakeAccountDao.getCount())
        assertEquals(1, fakeRuleDao.count())
        assertEquals(1, fakeAliasDao.count())
        assertEquals(1, fakeBudgetDao.getCount())

        val restoredTx1 = fakeExpenseDao.getExpenseById(1)
        assertNotNull(restoredTx1)
        assertEquals("Amazon", restoredTx1!!.merchant)
        assertEquals(3499.0, restoredTx1.amount, 0.001)
        assertEquals("cat_tech", restoredTx1.categoryId)
        assertEquals("RULE", restoredTx1.categorySource)
        assertEquals("sbi_acc_1065", restoredTx1.accountId)

        val restoredTx3 = fakeExpenseDao.getExpenseById(3)
        assertNotNull(restoredTx3)
        assertEquals(TransactionRelationshipType.SELF_TRANSFER, restoredTx3!!.relationshipType)

        val restoredTx4 = fakeExpenseDao.getExpenseById(4)
        assertNotNull(restoredTx4)
        assertEquals("Credit", restoredTx4!!.type)
    }
}
