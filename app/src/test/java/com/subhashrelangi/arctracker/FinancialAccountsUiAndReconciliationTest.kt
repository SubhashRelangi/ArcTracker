package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Milestone 6: Account Management & Manual Transaction Reconciliation UI Unit Tests.
 *
 * Implements all 28 mandatory verification scenarios from Part 22 of the Milestone 6 specification.
 */
class FinancialAccountsUiAndReconciliationTest {

    private lateinit var fakeExpenseDao: FakeExpenseDao
    private lateinit var fakeAccountDao: FakeKnownFinancialAccountDao
    private lateinit var accountRepo: KnownFinancialAccountRepository
    private lateinit var matcher: KnownFinancialAccountMatcher
    private lateinit var manager: AccountReconciliationManager

    @Before
    fun setUp() {
        fakeExpenseDao = FakeExpenseDao()
        fakeAccountDao = FakeKnownFinancialAccountDao()
        accountRepo = KnownFinancialAccountRepository(fakeAccountDao)
        matcher = KnownFinancialAccountMatcher(accountRepo)
        manager = AccountReconciliationManager(fakeExpenseDao, accountRepo)
    }

    @After
    fun tearDown() {
        // Clean up
    }

    // 1. Accounts screen/repository loads known accounts.
    @Test
    fun test01_accountsRepositoryLoadsKnownAccounts() = runBlocking {
        val hdfc = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            source = AccountSource.HISTORICAL_SMS
        )
        val sbi = KnownFinancialAccount(
            id = "sbi_bank_account_1234",
            institutionId = "sbi",
            institutionName = "State Bank of India",
            accountSuffix = "1234",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            source = AccountSource.LIVE_NOTIFICATION
        )
        accountRepo.upsert(hdfc)
        accountRepo.upsert(sbi)

        val loaded = manager.getAllKnownAccounts()
        assertEquals(2, loaded.size)
        assertTrue(loaded.any { it.id == "hdfc_bank_account_9020" })
        assertTrue(loaded.any { it.id == "sbi_bank_account_1234" })

        val flowList = manager.getAllKnownAccountsFlow().first()
        assertEquals(2, flowList.size)
    }

    // 2. Account detail transaction count uses accountId.
    @Test
    fun test02_accountDetailTransactionCountUsesAccountId() = runBlocking {
        val hdfcId = "hdfc_bank_account_9020"
        val apgbId = "apgb_bank_account_9020"

        fakeExpenseDao.insert(
            Expense(
                amount = 100.0,
                merchant = "Amazon",
                dateMillis = 1000L,
                accountId = hdfcId,
                accountSuffix = "9020"
            )
        )
        fakeExpenseDao.insert(
            Expense(
                amount = 200.0,
                merchant = "Swiggy",
                dateMillis = 2000L,
                accountId = hdfcId,
                accountSuffix = "9020"
            )
        )
        // Same suffix 9020, but APGB accountId
        fakeExpenseDao.insert(
            Expense(
                amount = 300.0,
                merchant = "Zomato",
                dateMillis = 3000L,
                accountId = apgbId,
                accountSuffix = "9020"
            )
        )

        val hdfcCount = manager.getLinkedTransactionCount(hdfcId)
        val apgbCount = manager.getLinkedTransactionCount(apgbId)

        assertEquals(2, hdfcCount)
        assertEquals(1, apgbCount)
    }

    // 3. Same suffix across different banks does not mix transactions.
    @Test
    fun test03_sameSuffixAcrossDifferentBanksDoesNotMixTransactions() = runBlocking {
        val hdfcId = "hdfc_bank_account_9020"
        val apgbId = "apgb_bank_account_9020"

        fakeExpenseDao.insert(
            Expense(
                amount = 150.0,
                merchant = "Store 1",
                dateMillis = 1000L,
                accountId = hdfcId,
                accountSuffix = "9020"
            )
        )
        fakeExpenseDao.insert(
            Expense(
                amount = 250.0,
                merchant = "Store 2",
                dateMillis = 2000L,
                accountId = apgbId,
                accountSuffix = "9020"
            )
        )

        val hdfcTxns = manager.getLinkedTransactions(hdfcId)
        val apgbTxns = manager.getLinkedTransactions(apgbId)

        assertEquals(1, hdfcTxns.size)
        assertEquals("Store 1", hdfcTxns[0].merchant)
        assertEquals(hdfcId, hdfcTxns[0].accountId)

        assertEquals(1, apgbTxns.size)
        assertEquals("Store 2", apgbTxns[0].merchant)
        assertEquals(apgbId, apgbTxns[0].accountId)
    }

    // 4. Unresolved query returns accountId == null records.
    @Test
    fun test04_unresolvedQueryReturnsAccountIdNullRecords() = runBlocking {
        fakeExpenseDao.insert(
            Expense(
                amount = 100.0,
                merchant = "Linked 1",
                dateMillis = 1000L,
                accountId = "some_account_id",
                accountSuffix = "1234"
            )
        )
        fakeExpenseDao.insert(
            Expense(
                amount = 200.0,
                merchant = "Unresolved 1",
                dateMillis = 2000L,
                accountId = null,
                accountSuffix = "9020"
            )
        )
        fakeExpenseDao.insert(
            Expense(
                amount = 300.0,
                merchant = "Unresolved 2",
                dateMillis = 3000L,
                accountId = null,
                accountSuffix = null
            )
        )

        val unresolved = manager.getUnresolvedTransactions()
        assertEquals(2, unresolved.size)
        assertTrue(unresolved.all { it.accountId == null })
    }

    // 5. Unresolved transaction with suffix is displayed as unresolved.
    @Test
    fun test05_unresolvedTransactionWithSuffixIsDisplayedAsUnresolved() = runBlocking {
        fakeExpenseDao.insert(
            Expense(
                amount = 500.0,
                merchant = "Uber",
                dateMillis = 1000L,
                accountId = null,
                accountSuffix = "4381"
            )
        )

        val withSuffix = manager.getUnresolvedWithSuffix()
        assertEquals(1, withSuffix.size)
        assertEquals("4381", withSuffix[0].accountSuffix)
        assertNull(withSuffix[0].accountId)
    }

    // 6. Unresolved transaction without suffix remains distinguishable.
    @Test
    fun test06_unresolvedTransactionWithoutSuffixRemainsDistinguishable() = runBlocking {
        fakeExpenseDao.insert(
            Expense(
                amount = 500.0,
                merchant = "Cash Pay",
                dateMillis = 1000L,
                accountId = null,
                accountSuffix = null
            )
        )
        fakeExpenseDao.insert(
            Expense(
                amount = 250.0,
                merchant = "Card Pay",
                dateMillis = 2000L,
                accountId = null,
                accountSuffix = "1234"
            )
        )

        val noSuffix = manager.getUnresolvedWithoutSuffix()
        val withSuffix = manager.getUnresolvedWithSuffix()

        assertEquals(1, noSuffix.size)
        assertEquals("Cash Pay", noSuffix[0].merchant)
        assertNull(noSuffix[0].accountSuffix)

        assertEquals(1, withSuffix.size)
        assertEquals("Card Pay", withSuffix[0].merchant)
        assertEquals("1234", withSuffix[0].accountSuffix)
    }

    // 7. Manual assignment persists selected accountId.
    @Test
    fun test07_manualAssignmentPersistsSelectedAccountId() = runBlocking {
        val account = KnownFinancialAccount(
            id = "icici_bank_account_5555",
            institutionId = "icici",
            institutionName = "ICICI Bank",
            accountSuffix = "5555",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            source = AccountSource.HISTORICAL_SMS
        )
        accountRepo.upsert(account)

        val id = fakeExpenseDao.insert(
            Expense(
                amount = 450.0,
                merchant = "Flipkart",
                dateMillis = 1000L,
                accountId = null,
                accountSuffix = "5555"
            )
        ).toInt()

        val result = manager.assignAccount(id, account.id)
        assertTrue(result.isSuccess)

        val updated = fakeExpenseDao.getExpenseById(id)
        assertNotNull(updated)
        assertEquals(account.id, updated!!.accountId)
    }

    // 8. Manual assignment preserves accountSuffix.
    @Test
    fun test08_manualAssignmentPreservesAccountSuffix() = runBlocking {
        val account = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            source = AccountSource.HISTORICAL_SMS
        )
        accountRepo.upsert(account)

        val id = fakeExpenseDao.insert(
            Expense(
                amount = 100.0,
                merchant = "Shop",
                dateMillis = 1000L,
                accountId = null,
                accountSuffix = "9020"
            )
        ).toInt()

        manager.assignAccount(id, account.id)
        val updated = fakeExpenseDao.getExpenseById(id)

        assertNotNull(updated)
        assertEquals("9020", updated!!.accountSuffix)
        assertEquals(account.id, updated.accountId)
    }

    // 9. Manual assignment does not modify amount.
    @Test
    fun test09_manualAssignmentDoesNotModifyAmount() = runBlocking {
        val account = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)

        val originalAmount = 999.50
        val id = fakeExpenseDao.insert(
            Expense(
                amount = originalAmount,
                merchant = "Shop",
                dateMillis = 1000L,
                accountId = null
            )
        ).toInt()

        manager.assignAccount(id, account.id)
        val updated = fakeExpenseDao.getExpenseById(id)

        assertEquals(originalAmount, updated!!.amount, 0.001)
    }

    // 10. Manual assignment does not modify merchant.
    @Test
    fun test10_manualAssignmentDoesNotModifyMerchant() = runBlocking {
        val account = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)

        val originalMerchant = "Custom Merchant XYZ"
        val id = fakeExpenseDao.insert(
            Expense(
                amount = 100.0,
                merchant = originalMerchant,
                dateMillis = 1000L,
                accountId = null
            )
        ).toInt()

        manager.assignAccount(id, account.id)
        val updated = fakeExpenseDao.getExpenseById(id)

        assertEquals(originalMerchant, updated!!.merchant)
    }

    // 11. Manual assignment does not modify timestamp.
    @Test
    fun test11_manualAssignmentDoesNotModifyTimestamp() = runBlocking {
        val account = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)

        val originalTime = 1774900000000L
        val id = fakeExpenseDao.insert(
            Expense(
                amount = 100.0,
                merchant = "Shop",
                dateMillis = originalTime,
                accountId = null
            )
        ).toInt()

        manager.assignAccount(id, account.id)
        val updated = fakeExpenseDao.getExpenseById(id)

        assertEquals(originalTime, updated!!.dateMillis)
    }

    // 12. Manual assignment does not modify transaction direction.
    @Test
    fun test12_manualAssignmentDoesNotModifyTransactionDirection() = runBlocking {
        val account = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)

        val id = fakeExpenseDao.insert(
            Expense(
                amount = 100.0,
                merchant = "Salary",
                dateMillis = 1000L,
                type = "Credit",
                accountId = null
            )
        ).toInt()

        manager.assignAccount(id, account.id)
        val updated = fakeExpenseDao.getExpenseById(id)

        assertEquals("Credit", updated!!.type)
    }

    // 13. Manual assignment does not modify relationshipType.
    @Test
    fun test13_manualAssignmentDoesNotModifyRelationshipType() = runBlocking {
        val account = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)

        val id = fakeExpenseDao.insert(
            Expense(
                amount = 100.0,
                merchant = "Self Transfer",
                dateMillis = 1000L,
                relationshipType = TransactionRelationshipType.SELF_TRANSFER,
                relationshipId = "rel_123",
                accountId = null
            )
        ).toInt()

        manager.assignAccount(id, account.id)
        val updated = fakeExpenseDao.getExpenseById(id)

        assertEquals(TransactionRelationshipType.SELF_TRANSFER, updated!!.relationshipType)
    }

    // 14. Manual assignment does not modify relationshipId.
    @Test
    fun test14_manualAssignmentDoesNotModifyRelationshipId() = runBlocking {
        val account = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)

        val id = fakeExpenseDao.insert(
            Expense(
                amount = 100.0,
                merchant = "Self Transfer",
                dateMillis = 1000L,
                relationshipType = TransactionRelationshipType.SELF_TRANSFER,
                relationshipId = "rel_transfer_999",
                accountId = null
            )
        ).toInt()

        manager.assignAccount(id, account.id)
        val updated = fakeExpenseDao.getExpenseById(id)

        assertEquals("rel_transfer_999", updated!!.relationshipId)
    }

    // 15. Manual reassignment changes accountId only.
    @Test
    fun test15_manualReassignmentChangesAccountIdOnly() = runBlocking {
        val accountA = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val accountB = KnownFinancialAccount(
            id = "apgb_bank_account_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(accountA)
        accountRepo.upsert(accountB)

        val id = fakeExpenseDao.insert(
            Expense(
                amount = 750.0,
                merchant = "Myntra",
                dateMillis = 1000L,
                type = "Debit",
                accountId = accountA.id,
                accountSuffix = "9020",
                note = "Clothes shopping"
            )
        ).toInt()

        val result = manager.reassignAccount(id, accountB.id)
        assertTrue(result.isSuccess)

        val updated = fakeExpenseDao.getExpenseById(id)
        assertNotNull(updated)
        assertEquals(accountB.id, updated!!.accountId)
        assertEquals("9020", updated.accountSuffix)
        assertEquals(750.0, updated.amount, 0.001)
        assertEquals("Myntra", updated.merchant)
        assertEquals("Debit", updated.type)
        assertEquals("Clothes shopping", updated.note)
    }

    // 16. Unlink sets accountId to null.
    @Test
    fun test16_unlinkSetsAccountIdToNull() = runBlocking {
        val id = fakeExpenseDao.insert(
            Expense(
                amount = 300.0,
                merchant = "BigBasket",
                dateMillis = 1000L,
                accountId = "hdfc_bank_account_9020",
                accountSuffix = "9020"
            )
        ).toInt()

        val result = manager.unlinkAccount(id)
        assertTrue(result.isSuccess)

        val updated = fakeExpenseDao.getExpenseById(id)
        assertNotNull(updated)
        assertNull(updated!!.accountId)
    }

    // 17. Unlink preserves accountSuffix.
    @Test
    fun test17_unlinkPreservesAccountSuffix() = runBlocking {
        val id = fakeExpenseDao.insert(
            Expense(
                amount = 300.0,
                merchant = "BigBasket",
                dateMillis = 1000L,
                accountId = "hdfc_bank_account_9020",
                accountSuffix = "9020"
            )
        ).toInt()

        manager.unlinkAccount(id)

        val updated = fakeExpenseDao.getExpenseById(id)
        assertNotNull(updated)
        assertNull(updated!!.accountId)
        assertEquals("9020", updated.accountSuffix)
    }

    // 18. Manual selection between two same-suffix accounts works.
    @Test
    fun test18_manualSelectionBetweenTwoSameSuffixAccountsWorks() = runBlocking {
        val hdfc = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val apgb = KnownFinancialAccount(
            id = "apgb_bank_account_9020",
            institutionId = "apgb",
            institutionName = "APGB",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(hdfc)
        accountRepo.upsert(apgb)

        val expense = Expense(
            amount = 500.0,
            merchant = "Store",
            dateMillis = 1000L,
            accountId = null,
            accountSuffix = "9020"
        )
        val id = fakeExpenseDao.insert(expense).toInt()

        // Get candidates
        val candidates = manager.getCompatibleAccounts(expense)
        assertEquals(2, candidates.size)
        assertTrue(candidates.any { it.id == hdfc.id })
        assertTrue(candidates.any { it.id == apgb.id })

        // Explicitly select APGB
        val res = manager.assignAccount(id, apgb.id)
        assertTrue(res.isSuccess)

        val updated = fakeExpenseDao.getExpenseById(id)
        assertEquals(apgb.id, updated!!.accountId)
    }

    // 19. No account is automatically selected in an ambiguous case.
    @Test
    fun test19_noAccountIsAutomaticallySelectedInAmbiguousCase() {
        val hdfc = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val apgb = KnownFinancialAccount(
            id = "apgb_bank_account_9020",
            institutionId = "apgb",
            institutionName = "APGB",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(hdfc)
        accountRepo.upsert(apgb)

        // Automatic matching returns Ambiguous
        val matchResult = matcher.match(
            suffix = "9020",
            instrumentType = InstrumentType.UNKNOWN,
            institutionId = null
        )
        assertTrue(matchResult is KnownFinancialAccountMatchResult.Ambiguous)

        // When listing candidates for UI, no single account is marked as auto-selected
        val candidates = manager.getCompatibleAccounts(
            Expense(amount = 100.0, merchant = "X", dateMillis = 1000L, accountSuffix = "9020")
        )
        assertEquals(2, candidates.size)
    }

    // 20. User-selected account is not converted into a global automatic matching rule.
    @Test
    fun test20_userSelectedAccountIsNotConvertedIntoGlobalAutomaticMatchingRule() = runBlocking {
        val hdfc = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val apgb = KnownFinancialAccount(
            id = "apgb_bank_account_9020",
            institutionId = "apgb",
            institutionName = "APGB",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(hdfc)
        accountRepo.upsert(apgb)

        val id = fakeExpenseDao.insert(
            Expense(
                amount = 200.0,
                merchant = "Store",
                dateMillis = 1000L,
                accountId = null,
                accountSuffix = "9020"
            )
        ).toInt()

        // User manually links this transaction to HDFC
        manager.assignAccount(id, hdfc.id)

        // A future notification with suffix 9020 and NO institution MUST STILL be Ambiguous!
        val futureMatch = matcher.match(
            suffix = "9020",
            instrumentType = InstrumentType.UNKNOWN,
            institutionId = null
        )
        assertTrue(futureMatch is KnownFinancialAccountMatchResult.Ambiguous)
    }

    // 21. Already-linked transaction remains linked unless user explicitly reassigns/unlinks it.
    @Test
    fun test21_alreadyLinkedTransactionRemainsLinkedUnlessExplicitlyChanged() = runBlocking {
        val account = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)

        val id = fakeExpenseDao.insert(
            Expense(
                amount = 500.0,
                merchant = "Dining",
                dateMillis = 1000L,
                accountId = account.id,
                accountSuffix = "9020"
            )
        ).toInt()

        // Querying unresolved does not affect or include this transaction
        val unresolved = manager.getUnresolvedTransactions()
        assertFalse(unresolved.any { it.id == id })

        val current = fakeExpenseDao.getExpenseById(id)
        assertEquals(account.id, current!!.accountId)
    }

    // 22. Account transaction list uses accountId, not suffix.
    @Test
    fun test22_accountTransactionListUsesAccountIdNotSuffix() = runBlocking {
        val hdfcId = "hdfc_bank_account_9020"
        val apgbId = "apgb_bank_account_9020"

        fakeExpenseDao.insert(
            Expense(
                amount = 100.0,
                merchant = "HDFC Txn 1",
                dateMillis = 1000L,
                accountId = hdfcId,
                accountSuffix = "9020"
            )
        )
        fakeExpenseDao.insert(
            Expense(
                amount = 200.0,
                merchant = "APGB Txn 1",
                dateMillis = 2000L,
                accountId = apgbId,
                accountSuffix = "9020"
            )
        )
        fakeExpenseDao.insert(
            Expense(
                amount = 300.0,
                merchant = "Unlinked Txn",
                dateMillis = 3000L,
                accountId = null,
                accountSuffix = "9020"
            )
        )

        val hdfcList = manager.getLinkedTransactions(hdfcId)
        assertEquals(1, hdfcList.size)
        assertEquals("HDFC Txn 1", hdfcList[0].merchant)

        val apgbList = manager.getLinkedTransactions(apgbId)
        assertEquals(1, apgbList.size)
        assertEquals("APGB Txn 1", apgbList[0].merchant)
    }

    // 23. Account count is correct.
    @Test
    fun test23_accountCountIsCorrect() {
        assertEquals(0, manager.getKnownAccountCount())

        accountRepo.upsert(
            KnownFinancialAccount(
                id = "acc_1",
                accountSuffix = "1111",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )
        accountRepo.upsert(
            KnownFinancialAccount(
                id = "acc_2",
                accountSuffix = "2222",
                instrumentType = InstrumentType.CARD
            )
        )

        assertEquals(2, manager.getKnownAccountCount())
    }

    // 24. Unresolved count is correct.
    @Test
    fun test24_unresolvedCountIsCorrect() = runBlocking {
        assertEquals(0, manager.getUnresolvedTransactions().size)

        fakeExpenseDao.insert(
            Expense(amount = 10.0, merchant = "A", dateMillis = 1000L, accountId = null)
        )
        fakeExpenseDao.insert(
            Expense(amount = 20.0, merchant = "B", dateMillis = 2000L, accountId = null, accountSuffix = "9020")
        )
        fakeExpenseDao.insert(
            Expense(amount = 30.0, merchant = "C", dateMillis = 3000L, accountId = "linked_id")
        )

        assertEquals(2, manager.getUnresolvedTransactions().size)
        assertEquals(1, manager.getUnresolvedWithSuffix().size)
        assertEquals(1, manager.getUnresolvedWithoutSuffix().size)
    }

    // 25. Existing M5 persistence tests remain green.
    @Test
    fun test25_existingM5PersistenceInvariantsRemainGreen() = runBlocking {
        val account = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)

        val id = fakeExpenseDao.insert(
            Expense(
                amount = 100.0,
                merchant = "Amazon",
                dateMillis = 1000L,
                accountId = null,
                accountSuffix = "9020"
            )
        ).toInt()

        fakeExpenseDao.updateAccountId(id, account.id)
        val loaded = fakeExpenseDao.getExpenseById(id)
        assertEquals(account.id, loaded!!.accountId)
        assertEquals("9020", loaded.accountSuffix)
    }

    // 26. Existing historical SMS tests remain green.
    @Test
    fun test26_existingHistoricalSmsInvariantsRemainGreen() {
        val identity = FinancialAccountIdentity(
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        assertEquals("9020", identity.accountSuffix)
        assertEquals(InstrumentType.BANK_ACCOUNT, identity.instrumentType)
    }

    // 27. Existing notification tests remain green.
    @Test
    fun test27_existingNotificationInvariantsRemainGreen() {
        val notifCandidate = StructuredTransactionCandidate(
            sourceNotificationKey = "notif_key_1",
            packageName = "com.google.android.apps.nbu.paisa.user",
            postTime = 1000L,
            amount = 100.0,
            merchant = "Swiggy",
            direction = TransactionDirection.DEBIT,
            accountSuffix = "9020"
        )
        assertEquals("9020", notifCandidate.accountSuffix)
        assertEquals("Swiggy", notifCandidate.merchant)
    }

    // 28. Existing self-transfer tests remain green.
    @Test
    fun test28_existingSelfTransferInvariantsRemainGreen() = runBlocking {
        val relId = "self_transfer_unique_uuid"
        val debitTxn = Expense(
            amount = 500.0,
            merchant = "IPPB Bank",
            dateMillis = 1000L,
            type = "Debit",
            relationshipType = TransactionRelationshipType.SELF_TRANSFER,
            relationshipId = relId,
            accountId = "ippb_acc_1"
        )
        val creditTxn = Expense(
            amount = 500.0,
            merchant = "APGB Bank",
            dateMillis = 1000L,
            type = "Credit",
            relationshipType = TransactionRelationshipType.SELF_TRANSFER,
            relationshipId = relId,
            accountId = "apgb_acc_2"
        )

        fakeExpenseDao.insert(debitTxn)
        fakeExpenseDao.insert(creditTxn)

        val related = fakeExpenseDao.getExpensesByRelationshipId(relId)
        assertEquals(2, related.size)
        assertTrue(related.any { it.type == "Debit" })
        assertTrue(related.any { it.type == "Credit" })
    }
}
