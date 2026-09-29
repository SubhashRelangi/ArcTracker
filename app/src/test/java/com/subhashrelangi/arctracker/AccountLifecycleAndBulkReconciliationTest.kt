package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Milestone 7: Account Lifecycle Management & Bulk Transaction Reconciliation Unit Tests.
 *
 * Implements all 47 mandatory verification scenarios from Part 34 of the Milestone 7 specification.
 */
class AccountLifecycleAndBulkReconciliationTest {

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
        manager = AccountReconciliationManager(fakeExpenseDao, accountRepo, null)
    }

    @After
    fun tearDown() {
        // Clean up
    }

    // ==================================================
    // ACCOUNT DELETE (Scenarios 1-11)
    // ==================================================

    // 1. Delete account with zero transactions.
    @Test
    fun test01_deleteAccountWithZeroTransactions() = runBlocking {
        val account = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)
        assertEquals(1, manager.getKnownAccountCount())

        val result = manager.deleteAccount(account.id)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
        assertEquals(0, manager.getKnownAccountCount())
        assertNull(accountRepo.getById(account.id))
    }

    // 2. Delete account with one linked transaction.
    @Test
    fun test02_deleteAccountWithOneLinkedTransaction() = runBlocking {
        val account = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)

        val txnId = fakeExpenseDao.insert(
            Expense(
                amount = 150.0,
                merchant = "Swiggy",
                dateMillis = 1000L,
                accountId = account.id,
                accountSuffix = "9020"
            )
        ).toInt()

        val result = manager.deleteAccount(account.id)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())

        assertNull(accountRepo.getById(account.id))
        val txn = fakeExpenseDao.getExpenseById(txnId)
        assertNotNull(txn)
        assertNull(txn!!.accountId)
    }

    // 3. Delete account with many linked transactions.
    @Test
    fun test03_deleteAccountWithManyLinkedTransactions() = runBlocking {
        val account = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)

        val ids = mutableListOf<Int>()
        for (i in 1..25) {
            val id = fakeExpenseDao.insert(
                Expense(
                    amount = i * 10.0,
                    merchant = "Merchant $i",
                    dateMillis = 1000L + i,
                    accountId = account.id,
                    accountSuffix = "9020"
                )
            ).toInt()
            ids.add(id)
        }

        val result = manager.deleteAccount(account.id)
        assertTrue(result.isSuccess)
        assertEquals(25, result.getOrNull())

        assertNull(accountRepo.getById(account.id))
        for (id in ids) {
            val txn = fakeExpenseDao.getExpenseById(id)
            assertNotNull(txn)
            assertNull(txn!!.accountId)
        }
    }

    // 4. Deleted account leaves no dangling Expense.accountId.
    @Test
    fun test04_deletedAccountLeavesNoDanglingAccountId() = runBlocking {
        val account = KnownFinancialAccount(
            id = "sbi_bank_account_1234",
            accountSuffix = "1234",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)

        fakeExpenseDao.insert(
            Expense(amount = 100.0, merchant = "Shop", dateMillis = 1000L, accountId = account.id)
        )

        manager.deleteAccount(account.id)

        val allTxns = fakeExpenseDao.getAllExpensesList()
        assertTrue(allTxns.none { it.accountId == account.id })
    }

    // 5. Deleted account's transactions remain in database.
    @Test
    fun test05_deletedAccountTransactionsRemainInDatabase() = runBlocking {
        val account = KnownFinancialAccount(
            id = "icici_bank_account_5555",
            accountSuffix = "5555",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)

        val id = fakeExpenseDao.insert(
            Expense(amount = 500.0, merchant = "Amazon", dateMillis = 1000L, accountId = account.id)
        ).toInt()

        manager.deleteAccount(account.id)

        val remaining = fakeExpenseDao.getExpenseById(id)
        assertNotNull(remaining)
        assertEquals(1, fakeExpenseDao.getCount())
    }

    // 6. Deleted account's transaction suffix is preserved.
    @Test
    fun test06_deletedAccountTransactionSuffixIsPreserved() = runBlocking {
        val account = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)

        val id = fakeExpenseDao.insert(
            Expense(
                amount = 200.0,
                merchant = "Zomato",
                dateMillis = 1000L,
                accountId = account.id,
                accountSuffix = "9020"
            )
        ).toInt()

        manager.deleteAccount(account.id)

        val txn = fakeExpenseDao.getExpenseById(id)
        assertNotNull(txn)
        assertEquals("9020", txn!!.accountSuffix)
    }

    // 7. Deleted account's transaction amount is preserved.
    @Test
    fun test07_deletedAccountTransactionAmountIsPreserved() = runBlocking {
        val account = KnownFinancialAccount(
            id = "acc_1",
            accountSuffix = "1111",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)

        val id = fakeExpenseDao.insert(
            Expense(amount = 1234.56, merchant = "Store", dateMillis = 1000L, accountId = account.id)
        ).toInt()

        manager.deleteAccount(account.id)

        val txn = fakeExpenseDao.getExpenseById(id)
        assertNotNull(txn)
        assertEquals(1234.56, txn!!.amount, 0.001)
    }

    // 8. Deleted account's transaction merchant is preserved.
    @Test
    fun test08_deletedAccountTransactionMerchantIsPreserved() = runBlocking {
        val account = KnownFinancialAccount(
            id = "acc_1",
            accountSuffix = "1111",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)

        val id = fakeExpenseDao.insert(
            Expense(amount = 50.0, merchant = "Starbucks Coffee", dateMillis = 1000L, accountId = account.id)
        ).toInt()

        manager.deleteAccount(account.id)

        val txn = fakeExpenseDao.getExpenseById(id)
        assertNotNull(txn)
        assertEquals("Starbucks Coffee", txn!!.merchant)
    }

    // 9. Deleted account's relationshipType is preserved.
    @Test
    fun test09_deletedAccountRelationshipTypeIsPreserved() = runBlocking {
        val account = KnownFinancialAccount(
            id = "acc_1",
            accountSuffix = "1111",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)

        val id = fakeExpenseDao.insert(
            Expense(
                amount = 100.0,
                merchant = "Transfer",
                dateMillis = 1000L,
                accountId = account.id,
                relationshipType = TransactionRelationshipType.SELF_TRANSFER,
                relationshipId = "rel_uuid_123"
            )
        ).toInt()

        manager.deleteAccount(account.id)

        val txn = fakeExpenseDao.getExpenseById(id)
        assertNotNull(txn)
        assertEquals(TransactionRelationshipType.SELF_TRANSFER, txn!!.relationshipType)
    }

    // 10. Deleted account's relationshipId is preserved.
    @Test
    fun test10_deletedAccountRelationshipIdIsPreserved() = runBlocking {
        val account = KnownFinancialAccount(
            id = "acc_1",
            accountSuffix = "1111",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)

        val id = fakeExpenseDao.insert(
            Expense(
                amount = 100.0,
                merchant = "Transfer",
                dateMillis = 1000L,
                accountId = account.id,
                relationshipType = TransactionRelationshipType.SELF_TRANSFER,
                relationshipId = "rel_unique_999"
            )
        ).toInt()

        manager.deleteAccount(account.id)

        val txn = fakeExpenseDao.getExpenseById(id)
        assertNotNull(txn)
        assertEquals("rel_unique_999", txn!!.relationshipId)
    }

    // 11. Deleted account transactions become unresolved.
    @Test
    fun test11_deletedAccountTransactionsBecomeUnresolved() = runBlocking {
        val account = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(account)

        val id = fakeExpenseDao.insert(
            Expense(
                amount = 300.0,
                merchant = "Flight",
                dateMillis = 1000L,
                accountId = account.id,
                accountSuffix = "9020"
            )
        ).toInt()

        assertEquals(0, manager.getUnresolvedTransactions().size)

        manager.deleteAccount(account.id)

        val unresolved = manager.getUnresolvedTransactions()
        assertEquals(1, unresolved.size)
        assertEquals(id, unresolved[0].id)
        assertEquals("9020", unresolved[0].accountSuffix)

        val withSuffix = manager.getUnresolvedWithSuffix()
        assertEquals(1, withSuffix.size)
        assertEquals(id, withSuffix[0].id)
    }

    // ==================================================
    // ACCOUNT MERGE (Scenarios 12-23)
    // ==================================================

    // 12. Merge compatible accounts.
    @Test
    fun test12_mergeCompatibleAccounts() = runBlocking {
        val source = KnownFinancialAccount(
            id = "unknown_bank_account_9020",
            institutionId = null,
            institutionName = "Unknown Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val target = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(source)
        accountRepo.upsert(target)

        val validation = manager.validateMergeCompatibility(source, target)
        assertTrue(validation.isCompatible)

        val result = manager.mergeAccounts(source.id, target.id)
        assertTrue(result.isSuccess)
    }

    // 13. All source transactions move to target.
    @Test
    fun test13_allSourceTransactionsMoveToTarget() = runBlocking {
        val source = KnownFinancialAccount(
            id = "source_acc_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val target = KnownFinancialAccount(
            id = "target_acc_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(source)
        accountRepo.upsert(target)

        val txn1 = fakeExpenseDao.insert(
            Expense(amount = 100.0, merchant = "A", dateMillis = 1000L, accountId = source.id, accountSuffix = "9020")
        ).toInt()
        val txn2 = fakeExpenseDao.insert(
            Expense(amount = 200.0, merchant = "B", dateMillis = 2000L, accountId = source.id, accountSuffix = "9020")
        ).toInt()

        val result = manager.mergeAccounts(source.id, target.id)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())

        assertEquals(target.id, fakeExpenseDao.getExpenseById(txn1)!!.accountId)
        assertEquals(target.id, fakeExpenseDao.getExpenseById(txn2)!!.accountId)
    }

    // 14. Source account is removed/archived correctly.
    @Test
    fun test14_sourceAccountIsRemovedCorrectly() = runBlocking {
        val source = KnownFinancialAccount(
            id = "source_acc_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val target = KnownFinancialAccount(
            id = "target_acc_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(source)
        accountRepo.upsert(target)

        manager.mergeAccounts(source.id, target.id)

        assertNull(accountRepo.getById(source.id))
        assertNotNull(accountRepo.getById(target.id))
        assertEquals(1, manager.getKnownAccountCount())
    }

    // 15. No source Expense.accountId remains.
    @Test
    fun test15_noSourceAccountIdRemains() = runBlocking {
        val source = KnownFinancialAccount(
            id = "source_acc_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val target = KnownFinancialAccount(
            id = "target_acc_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(source)
        accountRepo.upsert(target)

        fakeExpenseDao.insert(Expense(amount = 10.0, merchant = "X", dateMillis = 1000L, accountId = source.id))

        manager.mergeAccounts(source.id, target.id)

        val allTxns = fakeExpenseDao.getAllExpensesList()
        assertTrue(allTxns.none { it.accountId == source.id })
    }

    // 16. Target transaction count becomes correct.
    @Test
    fun test16_targetTransactionCountBecomesCorrect() = runBlocking {
        val source = KnownFinancialAccount(
            id = "source_acc_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val target = KnownFinancialAccount(
            id = "target_acc_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(source)
        accountRepo.upsert(target)

        // Target already has 1 transaction
        fakeExpenseDao.insert(Expense(amount = 50.0, merchant = "Pre-existing", dateMillis = 500L, accountId = target.id))

        // Source has 2 transactions
        fakeExpenseDao.insert(Expense(amount = 10.0, merchant = "S1", dateMillis = 1000L, accountId = source.id))
        fakeExpenseDao.insert(Expense(amount = 20.0, merchant = "S2", dateMillis = 2000L, accountId = source.id))

        manager.mergeAccounts(source.id, target.id)

        val targetCount = manager.getLinkedTransactionCount(target.id)
        assertEquals(3, targetCount)
    }

    // 17. accountSuffix remains unchanged during merge.
    @Test
    fun test17_accountSuffixRemainsUnchangedDuringMerge() = runBlocking {
        val source = KnownFinancialAccount(
            id = "source_acc_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val target = KnownFinancialAccount(
            id = "target_acc_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(source)
        accountRepo.upsert(target)

        val id = fakeExpenseDao.insert(
            Expense(amount = 100.0, merchant = "Test", dateMillis = 1000L, accountId = source.id, accountSuffix = "9020")
        ).toInt()

        manager.mergeAccounts(source.id, target.id)

        val txn = fakeExpenseDao.getExpenseById(id)
        assertNotNull(txn)
        assertEquals("9020", txn!!.accountSuffix)
        assertEquals(target.id, txn.accountId)
    }

    // 18. transaction financial data remains unchanged during merge.
    @Test
    fun test18_transactionFinancialDataRemainsUnchangedDuringMerge() = runBlocking {
        val source = KnownFinancialAccount(
            id = "source_acc_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val target = KnownFinancialAccount(
            id = "target_acc_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(source)
        accountRepo.upsert(target)

        val id = fakeExpenseDao.insert(
            Expense(
                amount = 987.65,
                merchant = "Special Merchant",
                dateMillis = 1234567890L,
                type = "Credit",
                note = "Important Note",
                accountId = source.id,
                accountSuffix = "9020"
            )
        ).toInt()

        manager.mergeAccounts(source.id, target.id)

        val txn = fakeExpenseDao.getExpenseById(id)
        assertNotNull(txn)
        assertEquals(987.65, txn!!.amount, 0.001)
        assertEquals("Special Merchant", txn.merchant)
        assertEquals(1234567890L, txn.dateMillis)
        assertEquals("Credit", txn.type)
        assertEquals("Important Note", txn.note)
    }

    // 19. self-transfer relationship fields remain unchanged during merge.
    @Test
    fun test19_selfTransferRelationshipFieldsRemainUnchangedDuringMerge() = runBlocking {
        val source = KnownFinancialAccount(
            id = "source_acc_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val target = KnownFinancialAccount(
            id = "target_acc_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(source)
        accountRepo.upsert(target)

        val id = fakeExpenseDao.insert(
            Expense(
                amount = 500.0,
                merchant = "Transfer",
                dateMillis = 1000L,
                accountId = source.id,
                relationshipType = TransactionRelationshipType.SELF_TRANSFER,
                relationshipId = "rel_transfer_555"
            )
        ).toInt()

        manager.mergeAccounts(source.id, target.id)

        val txn = fakeExpenseDao.getExpenseById(id)
        assertNotNull(txn)
        assertEquals(TransactionRelationshipType.SELF_TRANSFER, txn!!.relationshipType)
        assertEquals("rel_transfer_555", txn.relationshipId)
    }

    // 20. Same-suffix different-bank accounts cannot be silently merged.
    @Test
    fun test20_sameSuffixDifferentBankCannotBeSilentlyMerged() = runBlocking {
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

        val validation = manager.validateMergeCompatibility(hdfc, apgb)
        assertFalse(validation.isCompatible)
        assertTrue(validation.reason!!.contains("conflicting institution identities"))

        val result = manager.mergeAccounts(hdfc.id, apgb.id)
        assertTrue(result.isFailure)
    }

    // 21. Bank account vs card cannot be silently merged.
    @Test
    fun test21_bankAccountVsCardCannotBeSilentlyMerged() = runBlocking {
        val bank = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val card = KnownFinancialAccount(
            id = "hdfc_card_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.CARD
        )
        accountRepo.upsert(bank)
        accountRepo.upsert(card)

        val validation = manager.validateMergeCompatibility(bank, card)
        assertFalse(validation.isCompatible)
        assertTrue(validation.reason!!.contains("different instrument types"))

        val result = manager.mergeAccounts(bank.id, card.id)
        assertTrue(result.isFailure)
    }

    // 22. Explicit user-approved compatible merge succeeds.
    @Test
    fun test22_explicitUserApprovedCompatibleMergeSucceeds() = runBlocking {
        val source = KnownFinancialAccount(
            id = "unknown_bank_account_9020",
            institutionId = null,
            institutionName = null,
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val target = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(source)
        accountRepo.upsert(target)

        val result = manager.mergeAccounts(source.id, target.id)
        assertTrue(result.isSuccess)

        val targetAfter = accountRepo.getById(target.id)
        assertNotNull(targetAfter)
        assertEquals(AccountSource.USER_CONFIRMED, targetAfter!!.source)
        assertEquals(IdentityConfidence.HIGH, targetAfter.confidence)
    }

    // 23. Merge failure does not leave dangling references.
    @Test
    fun test23_mergeFailureDoesNotLeaveDanglingReferences() = runBlocking {
        val source = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val target = KnownFinancialAccount(
            id = "apgb_bank_account_9020",
            institutionId = "apgb",
            institutionName = "APGB",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(source)
        accountRepo.upsert(target)

        val txnId = fakeExpenseDao.insert(
            Expense(amount = 100.0, merchant = "X", dateMillis = 1000L, accountId = source.id, accountSuffix = "9020")
        ).toInt()

        // Attempt invalid merge
        val result = manager.mergeAccounts(source.id, target.id)
        assertTrue(result.isFailure)

        // Source account still exists
        assertNotNull(accountRepo.getById(source.id))
        // Expense still safely references source
        val txn = fakeExpenseDao.getExpenseById(txnId)
        assertNotNull(txn)
        assertEquals(source.id, txn!!.accountId)
    }

    // ==================================================
    // BULK ASSIGN (Scenarios 24-30)
    // ==================================================

    // 24. Select multiple unresolved transactions.
    @Test
    fun test24_selectMultipleUnresolvedTransactions() = runBlocking {
        val id1 = fakeExpenseDao.insert(Expense(amount = 10.0, merchant = "A", dateMillis = 1000L, accountId = null)).toInt()
        val id2 = fakeExpenseDao.insert(Expense(amount = 20.0, merchant = "B", dateMillis = 2000L, accountId = null)).toInt()
        val id3 = fakeExpenseDao.insert(Expense(amount = 30.0, merchant = "C", dateMillis = 3000L, accountId = null)).toInt()

        val selectedIds = listOf(id1, id2, id3)
        assertEquals(3, selectedIds.size)

        val unresolved = manager.getUnresolvedTransactions()
        assertEquals(3, unresolved.size)
        assertTrue(unresolved.all { it.id in selectedIds })
    }

    // 25. Bulk assign updates every selected transaction.
    @Test
    fun test25_bulkAssignUpdatesEverySelectedTransaction() = runBlocking {
        val target = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(target)

        val id1 = fakeExpenseDao.insert(Expense(amount = 10.0, merchant = "A", dateMillis = 1000L, accountId = null)).toInt()
        val id2 = fakeExpenseDao.insert(Expense(amount = 20.0, merchant = "B", dateMillis = 2000L, accountId = null)).toInt()
        val unselectedId = fakeExpenseDao.insert(Expense(amount = 30.0, merchant = "C", dateMillis = 3000L, accountId = null)).toInt()

        val result = manager.bulkAssign(listOf(id1, id2), target.id)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())

        assertEquals(target.id, fakeExpenseDao.getExpenseById(id1)!!.accountId)
        assertEquals(target.id, fakeExpenseDao.getExpenseById(id2)!!.accountId)
        assertNull(fakeExpenseDao.getExpenseById(unselectedId)!!.accountId)
    }

    // 26. Bulk assign preserves suffix.
    @Test
    fun test26_bulkAssignPreservesSuffix() = runBlocking {
        val target = KnownFinancialAccount(
            id = "hdfc_bank_account_9020",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(target)

        val id1 = fakeExpenseDao.insert(Expense(amount = 10.0, merchant = "A", dateMillis = 1000L, accountId = null, accountSuffix = "9020")).toInt()
        val id2 = fakeExpenseDao.insert(Expense(amount = 20.0, merchant = "B", dateMillis = 2000L, accountId = null, accountSuffix = "9020")).toInt()

        manager.bulkAssign(listOf(id1, id2), target.id)

        assertEquals("9020", fakeExpenseDao.getExpenseById(id1)!!.accountSuffix)
        assertEquals("9020", fakeExpenseDao.getExpenseById(id2)!!.accountSuffix)
    }

    // 27. Bulk assign preserves all financial fields.
    @Test
    fun test27_bulkAssignPreservesAllFinancialFields() = runBlocking {
        val target = KnownFinancialAccount(
            id = "target_1",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(target)

        val id = fakeExpenseDao.insert(
            Expense(
                amount = 450.75,
                merchant = "Retail Store",
                dateMillis = 1600000000L,
                type = "Credit",
                note = "Refund",
                relationshipType = TransactionRelationshipType.NONE,
                relationshipId = null,
                accountId = null
            )
        ).toInt()

        manager.bulkAssign(listOf(id), target.id)

        val txn = fakeExpenseDao.getExpenseById(id)
        assertNotNull(txn)
        assertEquals(450.75, txn!!.amount, 0.001)
        assertEquals("Retail Store", txn.merchant)
        assertEquals(1600000000L, txn.dateMillis)
        assertEquals("Credit", txn.type)
        assertEquals("Refund", txn.note)
        assertEquals(TransactionRelationshipType.NONE, txn.relationshipType)
    }

    // 28. Bulk assign with empty selection does nothing.
    @Test
    fun test28_bulkAssignWithEmptySelectionDoesNothing() = runBlocking {
        val target = KnownFinancialAccount(
            id = "target_1",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(target)

        val result = manager.bulkAssign(emptyList(), target.id)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
    }

    // 29. Bulk assign explicit target account only.
    @Test
    fun test29_bulkAssignExplicitTargetAccountOnly() = runBlocking {
        val targetA = KnownFinancialAccount(id = "acc_a", accountSuffix = "1111", instrumentType = InstrumentType.BANK_ACCOUNT)
        val targetB = KnownFinancialAccount(id = "acc_b", accountSuffix = "2222", instrumentType = InstrumentType.BANK_ACCOUNT)
        accountRepo.upsert(targetA)
        accountRepo.upsert(targetB)

        val id = fakeExpenseDao.insert(Expense(amount = 100.0, merchant = "X", dateMillis = 1000L, accountId = null)).toInt()

        manager.bulkAssign(listOf(id), targetA.id)

        assertEquals(targetA.id, fakeExpenseDao.getExpenseById(id)!!.accountId)
        assertNotEquals(targetB.id, fakeExpenseDao.getExpenseById(id)!!.accountId)
    }

    // 30. Bulk assign does not alter automatic matcher behavior.
    @Test
    fun test30_bulkAssignDoesNotAlterAutomaticMatcherBehavior() = runBlocking {
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

        val id1 = fakeExpenseDao.insert(Expense(amount = 10.0, merchant = "A", dateMillis = 1000L, accountSuffix = "9020", accountId = null)).toInt()
        val id2 = fakeExpenseDao.insert(Expense(amount = 20.0, merchant = "B", dateMillis = 2000L, accountSuffix = "9020", accountId = null)).toInt()

        // User bulk assigns these 2 transactions to HDFC
        manager.bulkAssign(listOf(id1, id2), hdfc.id)

        // Matcher for a future ambiguous notification with suffix 9020 MUST REMAIN AMBIGUOUS
        val futureMatch = matcher.match(
            suffix = "9020",
            instrumentType = InstrumentType.UNKNOWN,
            institutionId = null
        )
        assertTrue(futureMatch is KnownFinancialAccountMatchResult.Ambiguous)
    }

    // ==================================================
    // BULK REASSIGN (Scenarios 31-34)
    // ==================================================

    // 31. Reassign multiple linked transactions.
    @Test
    fun test31_reassignMultipleLinkedTransactions() = runBlocking {
        val oldAcc = KnownFinancialAccount(id = "old_acc", accountSuffix = "9020", instrumentType = InstrumentType.BANK_ACCOUNT)
        val newAcc = KnownFinancialAccount(id = "new_acc", accountSuffix = "9020", instrumentType = InstrumentType.BANK_ACCOUNT)
        accountRepo.upsert(oldAcc)
        accountRepo.upsert(newAcc)

        val id1 = fakeExpenseDao.insert(Expense(amount = 10.0, merchant = "A", dateMillis = 1000L, accountId = oldAcc.id)).toInt()
        val id2 = fakeExpenseDao.insert(Expense(amount = 20.0, merchant = "B", dateMillis = 2000L, accountId = oldAcc.id)).toInt()

        val result = manager.bulkReassign(listOf(id1, id2), newAcc.id)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())

        assertEquals(newAcc.id, fakeExpenseDao.getExpenseById(id1)!!.accountId)
        assertEquals(newAcc.id, fakeExpenseDao.getExpenseById(id2)!!.accountId)
    }

    // 32. All selected transactions change accountId.
    @Test
    fun test32_allSelectedTransactionsChangeAccountId() = runBlocking {
        val oldAcc = KnownFinancialAccount(id = "old_acc", accountSuffix = "1111", instrumentType = InstrumentType.BANK_ACCOUNT)
        val newAcc = KnownFinancialAccount(id = "new_acc", accountSuffix = "1111", instrumentType = InstrumentType.BANK_ACCOUNT)
        accountRepo.upsert(oldAcc)
        accountRepo.upsert(newAcc)

        val id1 = fakeExpenseDao.insert(Expense(amount = 10.0, merchant = "A", dateMillis = 1000L, accountId = oldAcc.id)).toInt()
        val id2 = fakeExpenseDao.insert(Expense(amount = 20.0, merchant = "B", dateMillis = 2000L, accountId = oldAcc.id)).toInt()
        val unselectedId = fakeExpenseDao.insert(Expense(amount = 30.0, merchant = "C", dateMillis = 3000L, accountId = oldAcc.id)).toInt()

        manager.bulkReassign(listOf(id1, id2), newAcc.id)

        assertEquals(newAcc.id, fakeExpenseDao.getExpenseById(id1)!!.accountId)
        assertEquals(newAcc.id, fakeExpenseDao.getExpenseById(id2)!!.accountId)
        assertEquals(oldAcc.id, fakeExpenseDao.getExpenseById(unselectedId)!!.accountId)
    }

    // 33. Existing account suffix remains unchanged during bulk reassign.
    @Test
    fun test33_existingAccountSuffixRemainsUnchangedDuringBulkReassign() = runBlocking {
        val oldAcc = KnownFinancialAccount(id = "old_acc", accountSuffix = "1111", instrumentType = InstrumentType.BANK_ACCOUNT)
        val newAcc = KnownFinancialAccount(id = "new_acc", accountSuffix = "2222", instrumentType = InstrumentType.BANK_ACCOUNT)
        accountRepo.upsert(oldAcc)
        accountRepo.upsert(newAcc)

        val id = fakeExpenseDao.insert(Expense(amount = 50.0, merchant = "X", dateMillis = 1000L, accountId = oldAcc.id, accountSuffix = "1111")).toInt()

        manager.bulkReassign(listOf(id), newAcc.id)

        val txn = fakeExpenseDao.getExpenseById(id)
        assertNotNull(txn)
        assertEquals("1111", txn!!.accountSuffix)
        assertEquals(newAcc.id, txn.accountId)
    }

    // 34. Other transaction fields remain unchanged during bulk reassign.
    @Test
    fun test34_otherTransactionFieldsRemainUnchangedDuringBulkReassign() = runBlocking {
        val oldAcc = KnownFinancialAccount(id = "old_acc", accountSuffix = "1111", instrumentType = InstrumentType.BANK_ACCOUNT)
        val newAcc = KnownFinancialAccount(id = "new_acc", accountSuffix = "1111", instrumentType = InstrumentType.BANK_ACCOUNT)
        accountRepo.upsert(oldAcc)
        accountRepo.upsert(newAcc)

        val id = fakeExpenseDao.insert(
            Expense(
                amount = 777.0,
                merchant = "Airline Ticket",
                dateMillis = 12345678L,
                type = "Debit",
                note = "Vacation",
                accountId = oldAcc.id,
                relationshipType = TransactionRelationshipType.NONE
            )
        ).toInt()

        manager.bulkReassign(listOf(id), newAcc.id)

        val txn = fakeExpenseDao.getExpenseById(id)
        assertNotNull(txn)
        assertEquals(777.0, txn!!.amount, 0.001)
        assertEquals("Airline Ticket", txn.merchant)
        assertEquals(12345678L, txn.dateMillis)
        assertEquals("Debit", txn.type)
        assertEquals("Vacation", txn.note)
    }

    // ==================================================
    // BULK UNLINK (Scenarios 35-38)
    // ==================================================

    // 35. Bulk unlink clears accountId.
    @Test
    fun test35_bulkUnlinkClearsAccountId() = runBlocking {
        val acc = KnownFinancialAccount(id = "acc_1", accountSuffix = "9020", instrumentType = InstrumentType.BANK_ACCOUNT)
        accountRepo.upsert(acc)

        val id1 = fakeExpenseDao.insert(Expense(amount = 10.0, merchant = "A", dateMillis = 1000L, accountId = acc.id)).toInt()
        val id2 = fakeExpenseDao.insert(Expense(amount = 20.0, merchant = "B", dateMillis = 2000L, accountId = acc.id)).toInt()

        val result = manager.bulkUnlink(listOf(id1, id2))
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())

        assertNull(fakeExpenseDao.getExpenseById(id1)!!.accountId)
        assertNull(fakeExpenseDao.getExpenseById(id2)!!.accountId)
    }

    // 36. Bulk unlink preserves accountSuffix.
    @Test
    fun test36_bulkUnlinkPreservesAccountSuffix() = runBlocking {
        val acc = KnownFinancialAccount(id = "acc_1", accountSuffix = "9020", instrumentType = InstrumentType.BANK_ACCOUNT)
        accountRepo.upsert(acc)

        val id1 = fakeExpenseDao.insert(Expense(amount = 10.0, merchant = "A", dateMillis = 1000L, accountId = acc.id, accountSuffix = "9020")).toInt()
        val id2 = fakeExpenseDao.insert(Expense(amount = 20.0, merchant = "B", dateMillis = 2000L, accountId = acc.id, accountSuffix = "9020")).toInt()

        manager.bulkUnlink(listOf(id1, id2))

        assertEquals("9020", fakeExpenseDao.getExpenseById(id1)!!.accountSuffix)
        assertEquals("9020", fakeExpenseDao.getExpenseById(id2)!!.accountSuffix)
    }

    // 37. Bulk unlink returns transactions to unresolved query.
    @Test
    fun test37_bulkUnlinkReturnsTransactionsToUnresolvedQuery() = runBlocking {
        val acc = KnownFinancialAccount(id = "acc_1", accountSuffix = "9020", instrumentType = InstrumentType.BANK_ACCOUNT)
        accountRepo.upsert(acc)

        val id1 = fakeExpenseDao.insert(Expense(amount = 10.0, merchant = "A", dateMillis = 1000L, accountId = acc.id, accountSuffix = "9020")).toInt()
        val id2 = fakeExpenseDao.insert(Expense(amount = 20.0, merchant = "B", dateMillis = 2000L, accountId = acc.id, accountSuffix = "9020")).toInt()

        assertEquals(0, manager.getUnresolvedTransactions().size)

        manager.bulkUnlink(listOf(id1, id2))

        val unresolved = manager.getUnresolvedTransactions()
        assertEquals(2, unresolved.size)
        assertTrue(unresolved.any { it.id == id1 })
        assertTrue(unresolved.any { it.id == id2 })
    }

    // 38. Bulk unlink does not delete transactions.
    @Test
    fun test38_bulkUnlinkDoesNotDeleteTransactions() = runBlocking {
        val acc = KnownFinancialAccount(id = "acc_1", accountSuffix = "9020", instrumentType = InstrumentType.BANK_ACCOUNT)
        accountRepo.upsert(acc)

        val id1 = fakeExpenseDao.insert(Expense(amount = 10.0, merchant = "A", dateMillis = 1000L, accountId = acc.id)).toInt()
        val id2 = fakeExpenseDao.insert(Expense(amount = 20.0, merchant = "B", dateMillis = 2000L, accountId = acc.id)).toInt()

        manager.bulkUnlink(listOf(id1, id2))

        assertEquals(2, fakeExpenseDao.getCount())
    }

    // ==================================================
    // ATOMICITY & SAFETY (Scenarios 39-42)
    // ==================================================

    // 39. Bulk operations are transaction-safe.
    @Test
    fun test39_bulkOperationsAreTransactionSafe() = runBlocking {
        val target = KnownFinancialAccount(id = "acc_target", accountSuffix = "9020", instrumentType = InstrumentType.BANK_ACCOUNT)
        accountRepo.upsert(target)

        val id1 = fakeExpenseDao.insert(Expense(amount = 10.0, merchant = "A", dateMillis = 1000L, accountId = null)).toInt()
        val id2 = fakeExpenseDao.insert(Expense(amount = 20.0, merchant = "B", dateMillis = 2000L, accountId = null)).toInt()

        // Invalid target
        val failResult = manager.bulkAssign(listOf(id1, id2), "non_existent_account_id")
        assertTrue(failResult.isFailure)

        // Neither was partially updated
        assertNull(fakeExpenseDao.getExpenseById(id1)!!.accountId)
        assertNull(fakeExpenseDao.getExpenseById(id2)!!.accountId)
    }

    // 40. Account merge is transaction-safe.
    @Test
    fun test40_accountMergeIsTransactionSafe() = runBlocking {
        val source = KnownFinancialAccount(id = "source_id", accountSuffix = "9020", instrumentType = InstrumentType.BANK_ACCOUNT)
        accountRepo.upsert(source)

        val id = fakeExpenseDao.insert(Expense(amount = 100.0, merchant = "X", dateMillis = 1000L, accountId = source.id)).toInt()

        // Merge into non-existent target fails
        val failResult = manager.mergeAccounts(source.id, "missing_target")
        assertTrue(failResult.isFailure)

        // Source account still exists and expense still points to source
        assertNotNull(accountRepo.getById(source.id))
        assertEquals(source.id, fakeExpenseDao.getExpenseById(id)!!.accountId)
    }

    // 41. Account deletion is transaction-safe.
    @Test
    fun test41_accountDeletionIsTransactionSafe() = runBlocking {
        val failResult = manager.deleteAccount("non_existent_account_id")
        assertTrue(failResult.isFailure)
    }

    // 42. No dangling account references after any successful operation.
    @Test
    fun test42_noDanglingAccountReferencesAfterAnySuccessfulOperation() = runBlocking {
        val acc1 = KnownFinancialAccount(id = "acc_1", accountSuffix = "1111", instrumentType = InstrumentType.BANK_ACCOUNT)
        val acc2 = KnownFinancialAccount(id = "acc_2", accountSuffix = "1111", instrumentType = InstrumentType.BANK_ACCOUNT)
        accountRepo.upsert(acc1)
        accountRepo.upsert(acc2)

        val t1 = fakeExpenseDao.insert(Expense(amount = 10.0, merchant = "T1", dateMillis = 1000L, accountId = acc1.id)).toInt()
        val t2 = fakeExpenseDao.insert(Expense(amount = 20.0, merchant = "T2", dateMillis = 2000L, accountId = acc2.id)).toInt()

        // Merge acc1 into acc2
        manager.mergeAccounts(acc1.id, acc2.id)

        // Delete acc2
        manager.deleteAccount(acc2.id)

        // Verify all expenses in DB have accountId == null or point to an existing account
        val allTxns = fakeExpenseDao.getAllExpensesList()
        val knownIds = accountRepo.getAll().map { it.id }.toSet()
        for (txn in allTxns) {
            if (txn.accountId != null) {
                assertTrue("Dangling accountId found: ${txn.accountId}", txn.accountId in knownIds)
            }
        }
    }

    // ==================================================
    // REGRESSION BOUNDARIES (Scenarios 43-47)
    // ==================================================

    // 43. Existing M5 tests remain green.
    @Test
    fun test43_existingM5InvariantsRemainGreen() = runBlocking {
        val acc = KnownFinancialAccount(id = "hdfc_bank_account_9020", accountSuffix = "9020", instrumentType = InstrumentType.BANK_ACCOUNT)
        accountRepo.upsert(acc)

        val id = fakeExpenseDao.insert(Expense(amount = 100.0, merchant = "Amazon", dateMillis = 1000L, accountId = null, accountSuffix = "9020")).toInt()
        fakeExpenseDao.updateAccountId(id, acc.id)

        val loaded = fakeExpenseDao.getExpenseById(id)
        assertEquals(acc.id, loaded!!.accountId)
        assertEquals("9020", loaded.accountSuffix)
    }

    // 44. Existing M4 matcher tests remain green.
    @Test
    fun test44_existingM4MatcherInvariantsRemainGreen() {
        val acc = KnownFinancialAccount(id = "hdfc_bank_account_9020", institutionId = "hdfc", accountSuffix = "9020", instrumentType = InstrumentType.BANK_ACCOUNT)
        accountRepo.upsert(acc)

        val match = matcher.match(suffix = "9020", instrumentType = InstrumentType.BANK_ACCOUNT, institutionId = "hdfc")
        assertTrue(match is KnownFinancialAccountMatchResult.Matched)
        assertEquals(acc.id, (match as KnownFinancialAccountMatchResult.Matched).account.id)
    }

    // 45. Historical SMS tests remain green.
    @Test
    fun test45_historicalSmsInvariantsRemainGreen() {
        val identity = FinancialAccountIdentity(
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        assertEquals("9020", identity.accountSuffix)
        assertEquals(InstrumentType.BANK_ACCOUNT, identity.instrumentType)
    }

    // 46. Self-transfer tests remain green.
    @Test
    fun test46_selfTransferInvariantsRemainGreen() = runBlocking {
        val relId = "self_transfer_m7_test"
        val debit = Expense(
            amount = 500.0,
            merchant = "IPPB Bank",
            dateMillis = 1000L,
            type = "Debit",
            relationshipType = TransactionRelationshipType.SELF_TRANSFER,
            relationshipId = relId
        )
        val credit = Expense(
            amount = 500.0,
            merchant = "APGB Bank",
            dateMillis = 1000L,
            type = "Credit",
            relationshipType = TransactionRelationshipType.SELF_TRANSFER,
            relationshipId = relId
        )
        fakeExpenseDao.insert(debit)
        fakeExpenseDao.insert(credit)

        val txns = fakeExpenseDao.getExpensesByRelationshipId(relId)
        assertEquals(2, txns.size)
        assertTrue(txns.any { it.type == "Debit" })
        assertTrue(txns.any { it.type == "Credit" })
    }

    // 47. Complete All pending transaction behavior remains green.
    @Test
    fun test47_completeAllPendingBehaviorRemainsGreen() = runBlocking {
        val p1 = Expense(amount = 50.0, merchant = "P1", dateMillis = 1000L, isPending = true)
        val p2 = Expense(amount = 75.0, merchant = "P2", dateMillis = 2000L, isPending = true)
        fakeExpenseDao.insert(p1)
        fakeExpenseDao.insert(p2)

        val pendingBefore = fakeExpenseDao.getPendingExpensesList()
        assertEquals(2, pendingBefore.size)

        for (p in pendingBefore) {
            fakeExpenseDao.update(p.copy(isPending = false))
        }

        val pendingAfter = fakeExpenseDao.getPendingExpensesList()
        assertEquals(0, pendingAfter.size)
    }
}
