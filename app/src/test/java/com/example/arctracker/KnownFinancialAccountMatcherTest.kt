package com.example.arctracker

import com.example.arctracker.data.AccountSource
import com.example.arctracker.data.KnownFinancialAccount
import com.example.arctracker.data.KnownFinancialAccountRepository
import com.example.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Milestone 4: Live Notification -> Known Financial Account Matching & Enrichment Unit Tests.
 *
 * Verifies all 14 mandatory matching scenarios and pipeline integration cases from Tasks 15 and 16.
 */
class KnownFinancialAccountMatcherTest {

    private lateinit var fakeAccountDao: FakeKnownFinancialAccountDao
    private lateinit var repository: KnownFinancialAccountRepository
    private lateinit var matcher: KnownFinancialAccountMatcher
    private lateinit var fakeExpenseDao: FakeExpenseDao

    @Before
    fun setUp() {
        fakeAccountDao = FakeKnownFinancialAccountDao()
        repository = KnownFinancialAccountRepository(fakeAccountDao)
        matcher = KnownFinancialAccountMatcher(repository)
        fakeExpenseDao = FakeExpenseDao()
        TransactionPersistenceManager.matcher = matcher
    }

    @After
    fun tearDown() {
        TransactionPersistenceManager.matcher = null
    }

    // ==================================================
    // TEST 1 — Exact single account
    // ==================================================
    @Test
    fun test01_exactSingleAccount() {
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT,
                source = AccountSource.HISTORICAL_SMS
            )
        )

        val result = matcher.match(suffix = "9020")

        assertTrue("Expected Matched result, got $result", result is KnownFinancialAccountMatchResult.Matched)
        val matched = (result as KnownFinancialAccountMatchResult.Matched).account
        assertEquals("hdfc_bank_account_9020", matched.id)
        assertEquals("hdfc", matched.institutionId)
        assertEquals("HDFC Bank", matched.institutionName)
        assertEquals("9020", matched.accountSuffix)
        assertEquals(InstrumentType.BANK_ACCOUNT, matched.instrumentType)
    }

    // ==================================================
    // TEST 2 — No known account
    // ==================================================
    @Test
    fun test02_noKnownAccount() {
        // Registry is empty
        val result = matcher.match(suffix = "9020")
        assertEquals(KnownFinancialAccountMatchResult.Unmatched, result)
        assertNull(result.matchedAccount)
        assertNull(result.accountEnrichment)
    }

    // ==================================================
    // TEST 3 — No suffix
    // ==================================================
    @Test
    fun test03_noSuffix() {
        val resultNull = matcher.match(suffix = null)
        assertEquals(KnownFinancialAccountMatchResult.NoAccountData, resultNull)

        val resultBlank = matcher.match(suffix = "   ")
        assertEquals(KnownFinancialAccountMatchResult.NoAccountData, resultBlank)
    }

    // ==================================================
    // TEST 4 — Same suffix, two banks
    // ==================================================
    @Test
    fun test04_sameSuffixTwoBanksProducesAmbiguity() {
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "apgb",
                institutionName = "APGBank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )

        val result = matcher.match(suffix = "9020")

        assertTrue("Expected Ambiguous result, got $result", result is KnownFinancialAccountMatchResult.Ambiguous)
        val candidates = (result as KnownFinancialAccountMatchResult.Ambiguous).candidates
        assertEquals(2, candidates.size)
        assertTrue(candidates.any { it.institutionId == "hdfc" })
        assertTrue(candidates.any { it.institutionId == "apgb" })
        assertNull("Ambiguous match must never return a single matched account", result.matchedAccount)
    }

    // ==================================================
    // TEST 5 — Same bank, different suffix
    // ==================================================
    @Test
    fun test05_sameBankDifferentSuffix() {
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "4381",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )

        val result = matcher.match(suffix = "4381")

        assertTrue(result is KnownFinancialAccountMatchResult.Matched)
        val matched = (result as KnownFinancialAccountMatchResult.Matched).account
        assertEquals("hdfc_bank_account_4381", matched.id)
        assertEquals("4381", matched.accountSuffix)
    }

    // ==================================================
    // TEST 6 — Bank account vs card
    // ==================================================
    @Test
    fun test06_bankAccountVsCard() {
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.CARD
            )
        )

        val result = matcher.match(suffix = "9020", instrumentType = InstrumentType.BANK_ACCOUNT)

        assertTrue(result is KnownFinancialAccountMatchResult.Matched)
        val matched = (result as KnownFinancialAccountMatchResult.Matched).account
        assertEquals(InstrumentType.BANK_ACCOUNT, matched.instrumentType)
        assertEquals("hdfc_bank_account_9020", matched.id)
    }

    // ==================================================
    // TEST 7 — Explicit card
    // ==================================================
    @Test
    fun test07_explicitCard() {
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.CARD
            )
        )

        val result = matcher.match(suffix = "9020", instrumentType = InstrumentType.CARD)

        assertTrue(result is KnownFinancialAccountMatchResult.Matched)
        val matched = (result as KnownFinancialAccountMatchResult.Matched).account
        assertEquals(InstrumentType.CARD, matched.instrumentType)
        assertEquals("hdfc_card_9020", matched.id)

        // If card was requested, but ONLY bank account existed:
        val freshRepo = KnownFinancialAccountRepository(FakeKnownFinancialAccountDao())
        freshRepo.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "1234",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )
        val freshMatcher = KnownFinancialAccountMatcher(freshRepo)
        val cardOnlyResult = freshMatcher.match(suffix = "1234", instrumentType = InstrumentType.CARD)
        assertEquals("Must NOT match bank account when card was explicitly requested", KnownFinancialAccountMatchResult.Unmatched, cardOnlyResult)
    }

    // ==================================================
    // TEST 8 — Unknown instrument
    // ==================================================
    @Test
    fun test08_unknownInstrumentProducesAmbiguityWhenMultipleTypesExist() {
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.CARD
            )
        )

        val result = matcher.match(suffix = "9020", instrumentType = InstrumentType.UNKNOWN)

        assertTrue("Unknown instrument with both card and account must be AMBIGUOUS", result is KnownFinancialAccountMatchResult.Ambiguous)
        val candidates = (result as KnownFinancialAccountMatchResult.Ambiguous).candidates
        assertEquals(2, candidates.size)
    }

    // ==================================================
    // TEST 9 — Explicit institution
    // ==================================================
    @Test
    fun test09_explicitInstitutionResolvesAmbiguity() {
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "apgb",
                institutionName = "APGBank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )

        val result = matcher.match(
            suffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            institutionId = "hdfc"
        )

        assertTrue(result is KnownFinancialAccountMatchResult.Matched)
        val matched = (result as KnownFinancialAccountMatchResult.Matched).account
        assertEquals("hdfc", matched.institutionId)
        assertEquals("hdfc_bank_account_9020", matched.id)
    }

    // ==================================================
    // TEST 10 — Institution mismatch
    // ==================================================
    @Test
    fun test10_institutionMismatchYieldsUnmatched() {
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )

        val result = matcher.match(
            suffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            institutionId = "apgb"
        )

        assertEquals("Must NOT fall back to suffix matching on institution conflict", KnownFinancialAccountMatchResult.Unmatched, result)
    }

    // ==================================================
    // TEST 11 — Promotional notification
    // ==================================================
    @Test
    fun test11_promotionalNotificationRejectedWithoutAccountEnrichment() = runBlocking {
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )

        val captured = CapturedNotificationInfo(
            packageName = "net.one97.paytm",
            notificationKey = "promo_test_11",
            postTime = System.currentTimeMillis(),
            title = "Paytm Cashback",
            text = "Congratulations! Get ₹5,000 cashback! XXXXX9020"
        )

        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao)
        assertEquals(1, results.size)
        assertTrue(results[0] is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, fakeExpenseDao.getCount())
    }

    // ==================================================
    // TEST 12 — Valid notification with unknown registry
    // ==================================================
    @Test
    fun test12_validNotificationWithUnknownRegistry() = runBlocking {
        // Registry is empty
        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "valid_test_12",
            postTime = System.currentTimeMillis(),
            title = "Google Pay",
            text = "₹100 transferred to Subhash from XXXXX9020"
        )

        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao)
        assertEquals(1, results.size)
        assertTrue(results[0] is ExpensePersistenceResult.Inserted)
        val inserted = results[0] as ExpensePersistenceResult.Inserted

        // Transaction is persisted
        assertEquals(1, fakeExpenseDao.getCount())
        assertEquals(100.0, inserted.expense.amount, 0.001)

        // Match result is Unmatched
        assertEquals(KnownFinancialAccountMatchResult.Unmatched, inserted.matchResult)
        assertNull(inserted.accountEnrichment)

        // ZERO records created in known accounts registry
        assertEquals(0, repository.getCount())
    }

    // ==================================================
    // TEST 13 — Valid notification with known account
    // ==================================================
    @Test
    fun test13_validNotificationWithKnownAccount() = runBlocking {
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "valid_test_13",
            postTime = System.currentTimeMillis(),
            title = "Google Pay",
            text = "₹100 transferred to Subhash from XXXXX9020"
        )

        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao)
        assertEquals(1, results.size)
        assertTrue(results[0] is ExpensePersistenceResult.Inserted)
        val inserted = results[0] as ExpensePersistenceResult.Inserted

        // Transaction is persisted normally
        assertEquals(1, fakeExpenseDao.getCount())
        assertEquals(100.0, inserted.expense.amount, 0.001)

        // Matched account is enriched
        assertTrue(inserted.matchResult is KnownFinancialAccountMatchResult.Matched)
        val enrichment = inserted.accountEnrichment
        assertNotNull(enrichment)
        assertEquals("hdfc_bank_account_9020", enrichment?.accountId)
        assertEquals("hdfc", enrichment?.institutionId)
        assertEquals("HDFC Bank", enrichment?.institutionName)
        assertEquals(InstrumentType.BANK_ACCOUNT, enrichment?.instrumentType)
    }

    // ==================================================
    // TEST 14 — Same suffix multiple institutions determinism
    // ==================================================
    @Test
    fun test14_sameSuffixMultipleInstitutionsDeterministicAmbiguity() {
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "apgb",
                institutionName = "APGBank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )

        // Multiple calls must always return Ambiguous and never pick a random bank
        for (i in 1..20) {
            val result = matcher.match(suffix = "9020")
            assertTrue("Iteration $i must be Ambiguous", result is KnownFinancialAccountMatchResult.Ambiguous)
            assertNull("Iteration $i must not guess an account", result.matchedAccount)
        }
    }

    // ==================================================
    // TASK 16 — END-TO-END NOTIFICATION CASES
    // ==================================================

    @Test
    fun test16_caseA_singleKnownAccountMatchesAndEnriches() = runBlocking {
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "case_a_${System.currentTimeMillis()}",
            postTime = System.currentTimeMillis(),
            title = "Google Pay",
            text = "₹100 transferred to Subhash from XXXXX9020"
        )

        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao)
        assertEquals(1, results.size)
        val res = results[0] as ExpensePersistenceResult.Inserted
        assertEquals(100.0, res.expense.amount, 0.001)
        assertEquals("Debit", res.expense.type)
        assertTrue(res.matchResult is KnownFinancialAccountMatchResult.Matched)
        assertEquals("hdfc_bank_account_9020", res.accountEnrichment?.accountId)
    }

    @Test
    fun test16_caseB_emptyRegistryTransactionPersistedNormally() = runBlocking {
        // Registry empty
        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "case_b_${System.currentTimeMillis()}",
            postTime = System.currentTimeMillis(),
            title = "Google Pay",
            text = "₹100 transferred to Subhash from XXXXX9020"
        )

        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao)
        assertEquals(1, results.size)
        val res = results[0] as ExpensePersistenceResult.Inserted
        assertEquals(100.0, res.expense.amount, 0.001)
        assertEquals(KnownFinancialAccountMatchResult.Unmatched, res.matchResult)
        assertEquals(1, fakeExpenseDao.getCount())
    }

    @Test
    fun test16_caseC_multipleBanksAmbiguousNoBankSelected() = runBlocking {
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "apgb",
                institutionName = "APGBank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "case_c_${System.currentTimeMillis()}",
            postTime = System.currentTimeMillis(),
            title = "Google Pay",
            text = "₹100 transferred to Subhash from XXXXX9020"
        )

        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao)
        assertEquals(1, results.size)
        val res = results[0] as ExpensePersistenceResult.Inserted
        assertEquals(100.0, res.expense.amount, 0.001)
        assertTrue(res.matchResult is KnownFinancialAccountMatchResult.Ambiguous)
        assertNull(res.accountEnrichment)
        assertEquals(1, fakeExpenseDao.getCount())
    }

    @Test
    fun test16_caseD_promotionalNotificationRejected() = runBlocking {
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )

        val captured = CapturedNotificationInfo(
            packageName = "net.one97.paytm",
            notificationKey = "case_d_${System.currentTimeMillis()}",
            postTime = System.currentTimeMillis(),
            title = "Paytm Offer",
            text = "Congratulations! Get ₹5,000 cashback! XXXXX9020"
        )

        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao)
        assertEquals(1, results.size)
        assertTrue(results[0] is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, fakeExpenseDao.getCount())
    }

    @Test
    fun test16_caseE_explicitCardDoesNotMatchBankAccount() = runBlocking {
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )
        repository.upsert(
            KnownFinancialAccount.create(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.CARD
            )
        )

        val captured = CapturedNotificationInfo(
            packageName = "com.hdfc.bank",
            notificationKey = "case_e_${System.currentTimeMillis()}",
            postTime = System.currentTimeMillis(),
            title = "HDFC Bank Alert",
            text = "Paid ₹500 using card ****9020"
        )

        val results = TransactionPersistenceManager.processCapturedNotificationAll(captured, fakeExpenseDao)
        assertEquals(1, results.size)
        val res = results[0] as ExpensePersistenceResult.Inserted
        assertEquals(500.0, res.expense.amount, 0.001)
        assertTrue(res.matchResult is KnownFinancialAccountMatchResult.Matched)
        assertEquals("hdfc_card_9020", res.accountEnrichment?.accountId)
        assertEquals(InstrumentType.CARD, res.accountEnrichment?.instrumentType)
    }
}
