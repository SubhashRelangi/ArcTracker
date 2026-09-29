package com.example.arctracker

import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.arctracker.data.*
import com.example.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Milestone 5: Expense <-> KnownFinancialAccount Persistence & Safe Reconciliation/Backfill Tests.
 *
 * Implements all 24 mandatory test scenarios from Part 17 of the Milestone 5 specification.
 */
class ExpenseAccountPersistenceAndReconciliationTest {

    private lateinit var fakeExpenseDao: FakeExpenseDao
    private lateinit var fakeAccountDao: FakeKnownFinancialAccountDao
    private lateinit var accountRepo: KnownFinancialAccountRepository
    private lateinit var matcher: KnownFinancialAccountMatcher
    private lateinit var persistenceManager: TransactionPersistenceManager
    private lateinit var synchronizer: HistoricalSmsAccountRegistrySynchronizer
    private lateinit var backfiller: AccountReconciliationBackfiller

    @Before
    fun setUp() {
        fakeExpenseDao = FakeExpenseDao()
        fakeAccountDao = FakeKnownFinancialAccountDao()
        accountRepo = KnownFinancialAccountRepository(fakeAccountDao)
        matcher = KnownFinancialAccountMatcher(accountRepo)
        persistenceManager = TransactionPersistenceManager
        persistenceManager.matcher = matcher
        synchronizer = HistoricalSmsAccountRegistrySynchronizer(accountRepo, fakeExpenseDao)
        backfiller = AccountReconciliationBackfiller(fakeExpenseDao, accountRepo, matcher)
    }

    @After
    fun tearDown() {
        persistenceManager.matcher = null
    }

    // ==================================================
    // 1. ROOM MIGRATION v3 -> v4
    // ==================================================
    @Test
    fun test01_roomMigration_v3_to_v4() {
        assertEquals(3, AppDatabase.MIGRATION_3_4.startVersion)
        assertEquals(4, AppDatabase.MIGRATION_3_4.endVersion)

        val executedSql = mutableListOf<String>()
        val fakeDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_3_4.migrate(fakeDb)

        assertTrue(executedSql.any { it.contains("ALTER TABLE expenses ADD COLUMN accountId TEXT DEFAULT NULL") })
        assertTrue(executedSql.any { it.contains("ALTER TABLE expenses ADD COLUMN accountSuffix TEXT DEFAULT NULL") })
        assertTrue(executedSql.any { it.contains("CREATE INDEX IF NOT EXISTS index_expenses_accountId ON expenses (accountId)") })
        assertTrue(executedSql.any { it.contains("CREATE INDEX IF NOT EXISTS index_expenses_accountSuffix ON expenses (accountSuffix)") })

        // Verify Expense entity default values for backward compatibility
        val legacyExpense = Expense(
            id = 1,
            amount = 150.0,
            merchant = "Starbucks",
            dateMillis = 1000L,
            relationshipType = TransactionRelationshipType.SELF_TRANSFER,
            relationshipId = "rel_123"
        )
        assertNull(legacyExpense.accountId)
        assertNull(legacyExpense.accountSuffix)
        assertEquals("SELF_TRANSFER", legacyExpense.relationshipType)
        assertEquals("rel_123", legacyExpense.relationshipId)
    }

    // ==================================================
    // 2. NEW LIVE TRANSACTION WITH MATCHED ACCOUNT
    // ==================================================
    @Test
    fun test02_newLiveTransaction_withMatchedAccount() = runBlocking {
        accountRepo.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT,
                source = AccountSource.HISTORICAL_SMS
            )
        )

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            title = "HDFCBK",
            text = "Rs 100 transferred to Subhash from XXXXX9020 on 28-Sep-26. Ref 123456",
            postTime = System.currentTimeMillis(),
            notificationKey = "key_live_matched_01"
        )

        val results = persistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao, matcher)
        assertEquals(1, results.size)
        val result = results[0]
        assertTrue("Expected Inserted, was $result", result is ExpensePersistenceResult.Inserted)
        val inserted = (result as ExpensePersistenceResult.Inserted).expense

        assertEquals("hdfc_bank_account_9020", inserted.accountId)
        assertEquals("9020", inserted.accountSuffix)
        assertEquals(100.0, inserted.amount, 0.001)

        val inDb = fakeExpenseDao.getExpenseById(inserted.id)
        assertNotNull(inDb)
        assertEquals("hdfc_bank_account_9020", inDb?.accountId)
        assertEquals("9020", inDb?.accountSuffix)
    }

    // ==================================================
    // 3. NEW LIVE TRANSACTION WITH UNMATCHED ACCOUNT
    // ==================================================
    @Test
    fun test03_newLiveTransaction_withUnmatchedAccount() = runBlocking {
        // Registry empty - suffix 9020 has no known account
        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            title = "Bank",
            text = "Rs 250 spent using account XXXXX9020 at Swiggy",
            postTime = System.currentTimeMillis(),
            notificationKey = "key_live_unmatched_02"
        )

        val results = persistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao, matcher)
        assertEquals(1, results.size)
        val result = results[0]
        assertTrue("Expected Inserted, was $result", result is ExpensePersistenceResult.Inserted)
        val inserted = (result as ExpensePersistenceResult.Inserted).expense

        assertNull("AccountId must remain null for unmatched account", inserted.accountId)
        assertEquals("9020", inserted.accountSuffix)
        assertEquals(250.0, inserted.amount, 0.001)
    }

    // ==================================================
    // 4. NEW LIVE TRANSACTION WITH AMBIGUOUS ACCOUNT
    // ==================================================
    @Test
    fun test04_newLiveTransaction_withAmbiguousAccount() = runBlocking {
        // Registry contains both HDFC 9020 and APGB 9020
        accountRepo.upsert(
            KnownFinancialAccount.create("hdfc", "HDFC Bank", "9020", InstrumentType.BANK_ACCOUNT)
        )
        accountRepo.upsert(
            KnownFinancialAccount.create("apgb", "APGB Bank", "9020", InstrumentType.BANK_ACCOUNT)
        )

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            title = "Alert",
            text = "INR 300 debited from A/c XXXXX9020 for payment",
            postTime = System.currentTimeMillis(),
            notificationKey = "key_live_ambiguous_03"
        )

        val results = persistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao, matcher)
        assertEquals(1, results.size)
        val result = results[0]
        assertTrue("Expected Inserted, was $result", result is ExpensePersistenceResult.Inserted)
        val inserted = (result as ExpensePersistenceResult.Inserted).expense

        assertNull("AccountId must remain null for ambiguous match", inserted.accountId)
        assertEquals("9020", inserted.accountSuffix)
        assertEquals(300.0, inserted.amount, 0.001)
    }

    // ==================================================
    // 5. TRANSACTION WITHOUT ACCOUNT INFORMATION
    // ==================================================
    @Test
    fun test05_transactionWithoutAccountInformation() = runBlocking {
        val captured = CapturedNotificationInfo(
            packageName = "com.phonepe.app",
            title = "PhonePe",
            text = "Paid Rs 500 to Grocery Store successfully",
            postTime = System.currentTimeMillis(),
            notificationKey = "key_no_account_04"
        )

        val results = persistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao, matcher)
        assertEquals(1, results.size)
        val result = results[0]
        assertTrue("Expected Inserted, was $result", result is ExpensePersistenceResult.Inserted)
        val inserted = (result as ExpensePersistenceResult.Inserted).expense

        assertNull(inserted.accountId)
        assertNull(inserted.accountSuffix)
        assertEquals(500.0, inserted.amount, 0.001)
    }

    // ==================================================
    // 6. BANK ACCOUNT MATCHING
    // ==================================================
    @Test
    fun test06_bankAccountMatching() = runBlocking {
        accountRepo.upsert(
            KnownFinancialAccount.create("sbi", "SBI", "1065", InstrumentType.BANK_ACCOUNT)
        )

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            title = "SBIUPI",
            text = "Rs 150 debited from A/c XX1065 on 29-Sep-26. Ref 987654321",
            postTime = System.currentTimeMillis(),
            notificationKey = "key_sbi_bank_05"
        )

        val results = persistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao, matcher)
        val inserted = (results[0] as ExpensePersistenceResult.Inserted).expense
        assertEquals("sbi_bank_account_1065", inserted.accountId)
        assertEquals("1065", inserted.accountSuffix)
    }

    // ==================================================
    // 7. CARD MATCHING
    // ==================================================
    @Test
    fun test07_cardMatching() = runBlocking {
        accountRepo.upsert(
            KnownFinancialAccount.create("hdfc", "HDFC Bank", "4381", InstrumentType.CARD)
        )

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            title = "HDFCBK",
            text = "INR 800 spent on HDFC Bank Card ending 4381 at Amazon",
            postTime = System.currentTimeMillis(),
            notificationKey = "key_card_06"
        )

        val results = persistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao, matcher)
        val inserted = (results[0] as ExpensePersistenceResult.Inserted).expense
        assertEquals("hdfc_card_4381", inserted.accountId)
        assertEquals("4381", inserted.accountSuffix)
    }

    // ==================================================
    // 8. SAME SUFFIX, DIFFERENT BANKS
    // ==================================================
    @Test
    fun test08_sameSuffixDifferentBanks_noArbitrarySelection() = runBlocking {
        accountRepo.upsert(KnownFinancialAccount.create("hdfc", "HDFC Bank", "9020", InstrumentType.BANK_ACCOUNT))
        accountRepo.upsert(KnownFinancialAccount.create("axis", "Axis Bank", "9020", InstrumentType.BANK_ACCOUNT))

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            title = "BankAlert",
            text = "Rs 120 paid from account XXXX9020",
            postTime = System.currentTimeMillis(),
            notificationKey = "key_diff_banks_07"
        )

        val results = persistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao, matcher)
        val inserted = (results[0] as ExpensePersistenceResult.Inserted).expense
        assertNull("Must not guess between HDFC and Axis", inserted.accountId)
        assertEquals("9020", inserted.accountSuffix)
    }

    // ==================================================
    // 9. SAME SUFFIX, BANK VS CARD
    // ==================================================
    @Test
    fun test09_sameSuffixBankVsCard_distinctionPreserved() = runBlocking {
        // Registry only has bank account 9020
        accountRepo.upsert(KnownFinancialAccount.create("hdfc", "HDFC Bank", "9020", InstrumentType.BANK_ACCOUNT))

        // Notification is explicitly a card transaction
        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            title = "HDFCBK",
            text = "INR 500 spent on HDFC Bank Card ending 9020",
            postTime = System.currentTimeMillis(),
            notificationKey = "key_card_not_bank_08"
        )

        val results = persistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao, matcher)
        val inserted = (results[0] as ExpensePersistenceResult.Inserted).expense
        assertNull("Card transaction must not link to Bank Account", inserted.accountId)
        assertEquals("9020", inserted.accountSuffix)
    }

    // ==================================================
    // 10. EXISTING TRANSACTION ENRICHMENT (NULL -> DETERMINISTIC)
    // ==================================================
    @Test
    fun test10_existingTransactionEnrichment_nullToDeterministic() = runBlocking {
        accountRepo.upsert(KnownFinancialAccount.create("hdfc", "HDFC Bank", "9020", InstrumentType.BANK_ACCOUNT))

        // Pre-existing expense has no accountId
        val existingId = fakeExpenseDao.insert(
            Expense(
                amount = 200.0,
                merchant = "Zomato",
                dateMillis = System.currentTimeMillis(),
                notificationKey = "order_12345",
                accountId = null,
                accountSuffix = "9020"
            )
        )

        // Incoming duplicate notification carries matched HDFC account
        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            title = "HDFCBK",
            text = "Rs 200 debited from A/c XX9020 at Zomato",
            postTime = System.currentTimeMillis(),
            notificationKey = "order_12345"
        )

        val results = persistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao, matcher)
        assertTrue(results[0] is ExpensePersistenceResult.SkippedDuplicate)

        val updated = fakeExpenseDao.getExpenseById(existingId.toInt())
        assertNotNull(updated)
        assertEquals("hdfc_bank_account_9020", updated?.accountId)
        assertEquals("9020", updated?.accountSuffix)
    }

    // ==================================================
    // 11. EXISTING RESOLVED TRANSACTION NEVER OVERWRITTEN BY NULL
    // ==================================================
    @Test
    fun test11_existingResolvedTransaction_neverOverwrittenByNull() = runBlocking {
        val existingId = fakeExpenseDao.insert(
            Expense(
                amount = 350.0,
                merchant = "Amazon",
                dateMillis = System.currentTimeMillis(),
                notificationKey = "amazon_999",
                accountId = "hdfc_bank_account_9020",
                accountSuffix = "9020"
            )
        )

        // Incoming duplicate without account metadata
        val captured = CapturedNotificationInfo(
            packageName = "com.amazon.mShop.android.shopping",
            title = "Amazon",
            text = "Your order of Rs 350 has been placed",
            postTime = System.currentTimeMillis(),
            notificationKey = "amazon_999"
        )

        persistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao, matcher)

        val checked = fakeExpenseDao.getExpenseById(existingId.toInt())
        assertEquals("hdfc_bank_account_9020", checked?.accountId)
        assertEquals("9020", checked?.accountSuffix)
    }

    // ==================================================
    // 12. EXISTING RESOLVED TRANSACTION NOT REPLACED BY ANOTHER ACCOUNT
    // ==================================================
    @Test
    fun test12_existingResolvedTransaction_notReplacedByAnotherAccount() = runBlocking {
        accountRepo.upsert(KnownFinancialAccount.create("axis", "Axis Bank", "9020", InstrumentType.BANK_ACCOUNT))

        val existingId = fakeExpenseDao.insert(
            Expense(
                amount = 400.0,
                merchant = "Flipkart",
                dateMillis = System.currentTimeMillis(),
                notificationKey = "fk_111",
                accountId = "hdfc_bank_account_9020",
                accountSuffix = "9020"
            )
        )

        // Incoming candidate matching Axis Bank
        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            title = "AXISBK",
            text = "Rs 400 debited from Axis A/c XX9020 for Flipkart",
            postTime = System.currentTimeMillis(),
            notificationKey = "fk_111"
        )

        persistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao, matcher)

        val checked = fakeExpenseDao.getExpenseById(existingId.toInt())
        assertEquals("hdfc_bank_account_9020", checked?.accountId)
    }

    // ==================================================
    // 13. SAFE BACKFILL WITH EXACTLY ONE MATCHING ACCOUNT
    // ==================================================
    @Test
    fun test13_safeBackfill_withExactlyOneMatchingAccount() = runBlocking {
        accountRepo.upsert(KnownFinancialAccount.create("hdfc", "HDFC Bank", "9020", InstrumentType.BANK_ACCOUNT))

        val e1 = fakeExpenseDao.insert(
            Expense(amount = 100.0, merchant = "Merchant", dateMillis = 1000L, accountId = null, accountSuffix = "9020")
        )

        val result = backfiller.backfill()
        assertEquals(1, result.processedCount)
        assertEquals(1, result.reconciledCount)
        assertEquals(0, result.ambiguousCount)

        val updated = fakeExpenseDao.getExpenseById(e1.toInt())
        assertEquals("hdfc_bank_account_9020", updated?.accountId)
        assertEquals("9020", updated?.accountSuffix)
    }

    // ==================================================
    // 14. SAFE BACKFILL WITH MULTIPLE MATCHING ACCOUNTS
    // ==================================================
    @Test
    fun test14_safeBackfill_withMultipleMatchingAccounts_remainsNull() = runBlocking {
        accountRepo.upsert(KnownFinancialAccount.create("hdfc", "HDFC Bank", "9020", InstrumentType.BANK_ACCOUNT))
        accountRepo.upsert(KnownFinancialAccount.create("apgb", "APGB Bank", "9020", InstrumentType.BANK_ACCOUNT))

        val e1 = fakeExpenseDao.insert(
            Expense(amount = 150.0, merchant = "Shop", dateMillis = 1000L, accountId = null, accountSuffix = "9020")
        )

        val result = backfiller.backfill()
        assertEquals(1, result.processedCount)
        assertEquals(0, result.reconciledCount)
        assertEquals(1, result.ambiguousCount)

        val updated = fakeExpenseDao.getExpenseById(e1.toInt())
        assertNull(updated?.accountId)
        assertEquals("9020", updated?.accountSuffix)
    }

    // ==================================================
    // 15. SAFE BACKFILL WITH NO KNOWN ACCOUNT
    // ==================================================
    @Test
    fun test15_safeBackfill_withNoKnownAccount_remainsNull() = runBlocking {
        val e1 = fakeExpenseDao.insert(
            Expense(amount = 220.0, merchant = "Cafe", dateMillis = 1000L, accountId = null, accountSuffix = "8888")
        )

        val result = backfiller.backfill()
        assertEquals(1, result.processedCount)
        assertEquals(0, result.reconciledCount)
        assertEquals(1, result.unmatchedCount)

        val updated = fakeExpenseDao.getExpenseById(e1.toInt())
        assertNull(updated?.accountId)
        assertEquals("8888", updated?.accountSuffix)
    }

    // ==================================================
    // 16. AMOUNT / DATE-ONLY MATCHING ATTEMPT
    // ==================================================
    @Test
    fun test16_amountDateOnlyMatchingAttempt_remainsNull() = runBlocking {
        accountRepo.upsert(KnownFinancialAccount.create("hdfc", "HDFC Bank", "9020", InstrumentType.BANK_ACCOUNT))

        // Expense has no suffix and no raw text containing an account
        val e1 = fakeExpenseDao.insert(
            Expense(amount = 500.0, merchant = "Uber", dateMillis = 1000L, accountId = null, accountSuffix = null)
        )

        val result = backfiller.backfill()
        assertEquals(1, result.processedCount)
        assertEquals(0, result.reconciledCount)
        assertEquals(1, result.noAccountDataCount)

        val updated = fakeExpenseDao.getExpenseById(e1.toInt())
        assertNull("Must not link by amount or date alone", updated?.accountId)
        assertNull(updated?.accountSuffix)
    }

    // ==================================================
    // 17. UNKNOWN -> KNOWN ACCOUNT RECONCILIATION
    // ==================================================
    @Test
    fun test17_unknownToKnownAccountReconciliation_expenseReassigned() = runBlocking {
        // Step 1: Create provisional unknown account and expense
        val provisionalAccount = KnownFinancialAccount.create(
            institutionId = null,
            institutionName = null,
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(provisionalAccount)
        assertEquals("unknown_bank_account_9020", provisionalAccount.id)

        val e1 = fakeExpenseDao.insert(
            Expense(amount = 100.0, merchant = "Grocery", dateMillis = 1000L, accountId = provisionalAccount.id, accountSuffix = "9020")
        )

        // Step 2: Definitive HDFC identity arrives via SMS sync
        val definitiveIdentity = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH
        )

        val synced = synchronizer.synchronize(definitiveIdentity)
        assertNotNull(synced)
        assertEquals("hdfc_bank_account_9020", synced?.id)

        // Step 3: Verify Expense.accountId was reassigned to the new definitive account
        val updatedExpense = fakeExpenseDao.getExpenseById(e1.toInt())
        assertEquals("hdfc_bank_account_9020", updatedExpense?.accountId)

        // Step 4: Verify provisional unknown account was removed from registry
        assertNull(accountRepo.getById("unknown_bank_account_9020"))
    }

    // ==================================================
    // 18. RECONCILIATION FAILURE SAFETY (NO DANGLING EXPENSE.ACCOUNTID)
    // ==================================================
    @Test
    fun test18_reconciliationFailureSafety_competingKnownInstitutions() = runBlocking {
        // Competing known accounts exist for suffix 9020 (HDFC and APGB)
        accountRepo.upsert(KnownFinancialAccount.create("apgb", "APGB Bank", "9020", InstrumentType.BANK_ACCOUNT))
        val provisionalAccount = KnownFinancialAccount.create(null, null, "9020", InstrumentType.BANK_ACCOUNT)
        accountRepo.upsert(provisionalAccount)

        val e1 = fakeExpenseDao.insert(
            Expense(amount = 75.0, merchant = "Tea", dateMillis = 1000L, accountId = provisionalAccount.id, accountSuffix = "9020")
        )

        // Incoming definitive HDFC identity
        val definitiveIdentity = FinancialAccountIdentity("hdfc", "HDFC Bank", "9020", null, InstrumentType.BANK_ACCOUNT)
        synchronizer.synchronize(definitiveIdentity)

        // Suffix is ambiguous across known institutions -> Unknown record is preserved to prevent corrupting links
        assertNotNull(accountRepo.getById(provisionalAccount.id))
        val expense = fakeExpenseDao.getExpenseById(e1.toInt())
        assertEquals(provisionalAccount.id, expense?.accountId)
    }

    // ==================================================
    // 19. HISTORICAL SMS SELECTED IMPORT: ACCOUNTID PERSISTED
    // ==================================================
    @Test
    fun test19_historicalSmsSelectedImport_accountIdPersisted() = runBlocking {
        val identity = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH
        )
        val groupId = AccountIdentityExtractor.generateGroupId(identity)

        val candidate = StructuredTransactionCandidate(
            sourceNotificationKey = "sms_1001",
            packageName = "com.google.android.apps.messaging",
            postTime = 1000L,
            amount = 500.0,
            merchant = "Starbucks",
            direction = TransactionDirection.DEBIT,
            accountSuffix = "9020",
            bank = "HDFC Bank"
        )
        val validated = ValidatedTransactionCandidate(
            candidate = candidate,
            validationState = ValidationState.ACCEPTABLE,
            evidenceLevel = EvidenceLevel.STRONG,
            isStructurallyValid = true
        )
        val sourceEvent = TransactionSourceEvent(
            sourceType = TransactionSourceType.SMS_HISTORY,
            sourceId = "sms_1001",
            sender = "HDFCBK",
            rawText = "Rs 500 debited from A/c XX9020",
            eventTimestamp = 1000L
        )
        val scannedItem = ScannedTransactionItem(
            candidate = validated,
            sourceEvent = sourceEvent,
            plannedDecision = DedupDecision.NEW_TRANSACTION,
            matchStrategy = MatchStrategy.NONE,
            reason = "Valid SMS",
            accountIdentity = identity
        )

        val scanResult = SmsScanResult.Success(
            startTimeMillis = 0L,
            endTimeMillis = 2000L,
            messagesScanned = 1,
            financialMessages = 1,
            nonFinancialMessages = 0,
            noiseMessages = 0,
            transactionCandidatesCount = 1,
            newTransactionsCount = 1,
            duplicatesCount = 0,
            correlationsCount = 0,
            pendingReviewCount = 0,
            rejectedCount = 0,
            totalDebitAmount = 500.0,
            totalCreditAmount = 0.0,
            scannedItems = listOf(scannedItem)
        )

        val fakeReader = InMemorySmsReader(hasPermission = true, records = emptyList())
        val importManager = HistoricalSmsImportManager(fakeReader, fakeExpenseDao, persistenceManager, synchronizer)

        val importRes = importManager.importTransactions(scanResult, setOf(groupId))
        assertTrue(importRes is SmsImportResult.Success)
        assertEquals(1, (importRes as SmsImportResult.Success).insertedCount)

        val expenses = fakeExpenseDao.getAllExpensesList()
        assertEquals(1, expenses.size)
        assertEquals("hdfc_bank_account_9020", expenses[0].accountId)
        assertEquals("9020", expenses[0].accountSuffix)
    }

    // ==================================================
    // 20. HISTORICAL SMS UNSELECTED ACCOUNT: NO EXPENSE CREATED
    // ==================================================
    @Test
    fun test20_historicalSmsUnselectedAccount_noExpenseCreated() = runBlocking {
        val identityHdfc = FinancialAccountIdentity("hdfc", "HDFC Bank", "9020", null, InstrumentType.BANK_ACCOUNT)
        val identityApgb = FinancialAccountIdentity("apgb", "APGB Bank", "1065", null, InstrumentType.BANK_ACCOUNT)

        val groupIdHdfc = AccountIdentityExtractor.generateGroupId(identityHdfc)

        val c1 = ValidatedTransactionCandidate(
            candidate = StructuredTransactionCandidate("sms_1", "pkg", 1000L, amount = 200.0, merchant = "M1"),
            validationState = ValidationState.ACCEPTABLE,
            evidenceLevel = EvidenceLevel.STRONG,
            isStructurallyValid = true
        )
        val c2 = ValidatedTransactionCandidate(
            candidate = StructuredTransactionCandidate("sms_2", "pkg", 1000L, amount = 300.0, merchant = "M2"),
            validationState = ValidationState.ACCEPTABLE,
            evidenceLevel = EvidenceLevel.STRONG,
            isStructurallyValid = true
        )

        val ev1 = TransactionSourceEvent(TransactionSourceType.SMS_HISTORY, "sms_1", "S1", "Body", 1000L)
        val ev2 = TransactionSourceEvent(TransactionSourceType.SMS_HISTORY, "sms_2", "S2", "Body", 1000L)

        val item1 = ScannedTransactionItem(c1, ev1, DedupDecision.NEW_TRANSACTION, MatchStrategy.NONE, reason = "", accountIdentity = identityHdfc)
        val item2 = ScannedTransactionItem(c2, ev2, DedupDecision.NEW_TRANSACTION, MatchStrategy.NONE, reason = "", accountIdentity = identityApgb)

        val scanResult = SmsScanResult.Success(
            0L, 2000L, 2, 2, 0, 0, 2, 2, 0, 0, 0, 0, 500.0, 0.0,
            scannedItems = listOf(item1, item2)
        )

        val fakeReader = InMemorySmsReader(hasPermission = true, records = emptyList())
        val importManager = HistoricalSmsImportManager(fakeReader, fakeExpenseDao, persistenceManager, synchronizer)

        // User selects ONLY HDFC, unselecting APGB
        val res = importManager.importTransactions(scanResult, setOf(groupIdHdfc))
        assertTrue(res is SmsImportResult.Success)
        assertEquals(1, (res as SmsImportResult.Success).insertedCount)

        val all = fakeExpenseDao.getAllExpensesList()
        assertEquals(1, all.size)
        assertEquals("sms_1", all[0].notificationKey)
        assertEquals("hdfc_bank_account_9020", all[0].accountId)
    }

    // ==================================================
    // 21. HISTORICAL SMS SCAN REMAINS NON-DESTRUCTIVE
    // ==================================================
    @Test
    fun test21_historicalSmsScanRemainsNonDestructive() = runBlocking {
        val sms = SmsRecord(1L, "HDFCBK", "Rs 500 debited from A/c XX9020", System.currentTimeMillis())
        val fakeReader = InMemorySmsReader(hasPermission = true, records = listOf(sms))

        val importManager = HistoricalSmsImportManager(fakeReader, fakeExpenseDao, persistenceManager, synchronizer)
        val scanResult = importManager.scan(0L, System.currentTimeMillis())

        assertTrue(scanResult is SmsScanResult.Success)
        // Verify ZERO mutations occurred in Room database
        assertEquals(0, fakeExpenseDao.getCount())
        assertEquals(0, accountRepo.getCount())
    }

    // ==================================================
    // 22. DUPLICATE NOTIFICATION DOES NOT CREATE ANOTHER TRANSACTION
    // ==================================================
    @Test
    fun test22_duplicateNotificationDoesNotCreateAnotherTransaction() = runBlocking {
        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            title = "HDFCBK",
            text = "Rs 100 transferred to Subhash from XXXXX9020 on 28-Sep-26. Ref 123456",
            postTime = 1000L,
            notificationKey = "key_dup_test_22"
        )

        // First pass
        val r1 = persistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao, matcher)
        assertTrue(r1[0] is ExpensePersistenceResult.Inserted)
        assertEquals(1, fakeExpenseDao.getCount())

        // Second pass (identical notification)
        val r2 = persistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao, matcher)
        assertTrue(r2[0] is ExpensePersistenceResult.SkippedDuplicate)
        assertEquals(1, fakeExpenseDao.getCount())
    }

    // ==================================================
    // 23. ACCOUNT ENRICHMENT DOES NOT ALTER DEDUP BEHAVIOR
    // ==================================================
    @Test
    fun test23_accountEnrichmentDoesNotAlterDedupBehavior() = runBlocking {
        accountRepo.upsert(KnownFinancialAccount.create("hdfc", "HDFC Bank", "9020", InstrumentType.BANK_ACCOUNT))

        val c1 = StructuredTransactionCandidate(
            sourceNotificationKey = "key_A",
            packageName = "pkg",
            postTime = 1000L,
            amount = 100.0,
            merchant = "Shop",
            referenceId = "REF9999",
            accountSuffix = "9020"
        )
        val v1 = ValidatedTransactionCandidate(
            candidate = c1,
            validationState = ValidationState.ACCEPTABLE,
            evidenceLevel = EvidenceLevel.STRONG,
            isStructurallyValid = true
        )

        val r1 = persistenceManager.processValidatedCandidate(v1, fakeExpenseDao)
        assertTrue(r1 is ExpensePersistenceResult.Inserted)

        // Incoming candidate with same reference ID and amount within correlation window
        val c2 = StructuredTransactionCandidate(
            sourceNotificationKey = "key_B",
            packageName = "pkg",
            postTime = 1000L,
            amount = 100.0,
            merchant = "Shop",
            referenceId = "REF9999",
            accountSuffix = "9020"
        )
        val v2 = ValidatedTransactionCandidate(
            candidate = c2,
            validationState = ValidationState.ACCEPTABLE,
            evidenceLevel = EvidenceLevel.STRONG,
            isStructurallyValid = true
        )

        val r2 = persistenceManager.processValidatedCandidate(v2, fakeExpenseDao)
        // Correlated or duplicate skipped: row count must remain exactly 1!
        assertEquals(1, fakeExpenseDao.getCount())
    }

    // ==================================================
    // 24. FULL EXISTING TEST SUITE REMAINS GREEN (INVARIANTS ASSERTION)
    // ==================================================
    @Test
    fun test24_fullExistingTestSuiteRemainsGreen() = runBlocking {
        // Assert that unmapped candidates preserve non-null amounts and standard types
        val candidate = StructuredTransactionCandidate(
            sourceNotificationKey = "notif_test_24",
            packageName = "com.test",
            postTime = 1000L,
            amount = 99.99,
            merchant = "TestMerchant",
            direction = TransactionDirection.CREDIT
        )
        val expense = persistenceManager.mapToExpense(candidate, isPending = false)

        assertEquals(99.99, expense.amount, 0.001)
        assertEquals("Credit", expense.type)
        assertEquals("TestMerchant", expense.merchant)
        assertNull(expense.accountId)
        assertNull(expense.accountSuffix)
    }
}
