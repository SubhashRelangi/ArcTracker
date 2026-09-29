package com.example.arctracker

import com.example.arctracker.data.AccountSource
import com.example.arctracker.data.KnownFinancialAccount
import com.example.arctracker.data.KnownFinancialAccountRepository
import com.example.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Milestone 3: Historical SMS -> Known Financial Account Synchronization Unit Tests.
 */
class HistoricalSmsAccountRegistrySynchronizationTest {

    private lateinit var fakeDao: FakeKnownFinancialAccountDao
    private lateinit var repository: KnownFinancialAccountRepository
    private lateinit var synchronizer: HistoricalSmsAccountRegistrySynchronizer

    @Before
    fun setUp() {
        fakeDao = FakeKnownFinancialAccountDao()
        repository = KnownFinancialAccountRepository(fakeDao)
        synchronizer = HistoricalSmsAccountRegistrySynchronizer(repository)
    }

    // ==================================================
    // TEST 1 — Known HDFC account creates registry record
    // ==================================================
    @Test
    fun test01_knownHdfcAccountCreatesRegistryRecord() = runBlocking {
        val identity = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            evidenceSource = "bank=BODY_EXPLICIT;suffix=BODY_ACCOUNT"
        )

        val result = synchronizer.synchronize(identity)

        assertNotNull(result)
        assertEquals("hdfc_bank_account_9020", result?.id)
        assertEquals("hdfc", result?.institutionId)
        assertEquals("HDFC Bank", result?.institutionName)
        assertEquals("9020", result?.accountSuffix)
        assertEquals(InstrumentType.BANK_ACCOUNT, result?.instrumentType)
        assertEquals(IdentityConfidence.HIGH, result?.confidence)
        assertEquals(AccountSource.HISTORICAL_SMS, result?.source)

        val inDb = repository.getById("hdfc_bank_account_9020")
        assertNotNull(inDb)
    }

    // ==================================================
    // TEST 2 — Same account from multiple SMS messages
    // ==================================================
    @Test
    fun test02_sameAccountFromMultipleSmsMessages_resultsInOneRecord() = runBlocking {
        val identity1 = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.MEDIUM
        )
        val identity2 = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH
        )
        val identity3 = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.MEDIUM
        )

        synchronizer.synchronize(identity1)
        synchronizer.synchronize(identity2)
        synchronizer.synchronize(identity3)

        assertEquals(1, repository.getCount())
        val saved = repository.getById("hdfc_bank_account_9020")
        assertNotNull(saved)
        assertEquals(IdentityConfidence.HIGH, saved?.confidence)
    }

    // ==================================================
    // TEST 3 — Same suffix different banks
    // ==================================================
    @Test
    fun test03_sameSuffixDifferentBanks_remainSeparate() = runBlocking {
        val hdfc = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH
        )
        val apgb = FinancialAccountIdentity(
            institutionId = "apgb",
            institutionName = "APG Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH
        )

        synchronizer.synchronize(hdfc)
        synchronizer.synchronize(apgb)

        assertEquals(2, repository.getCount())
        assertNotNull(repository.getById("hdfc_bank_account_9020"))
        assertNotNull(repository.getById("apgb_bank_account_9020"))
    }

    // ==================================================
    // TEST 4 — Same bank different accounts
    // ==================================================
    @Test
    fun test04_sameBankDifferentAccounts_remainSeparate() = runBlocking {
        val acc1 = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val acc2 = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "4381",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )

        synchronizer.synchronize(acc1)
        synchronizer.synchronize(acc2)

        assertEquals(2, repository.getCount())
        assertNotNull(repository.getById("hdfc_bank_account_9020"))
        assertNotNull(repository.getById("hdfc_bank_account_4381"))
    }

    // ==================================================
    // TEST 5 — Card/account distinction
    // ==================================================
    @Test
    fun test05_cardAndAccountDoNotCollide() = runBlocking {
        val bankAccount = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val card = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            cardSuffix = "9020",
            instrumentType = InstrumentType.CARD
        )

        synchronizer.synchronize(bankAccount)
        synchronizer.synchronize(card)

        assertEquals(2, repository.getCount())
        assertNotNull(repository.getById("hdfc_bank_account_9020"))
        assertNotNull(repository.getById("hdfc_card_9020"))
    }

    // ==================================================
    // TEST 6 — Unknown institution
    // ==================================================
    @Test
    fun test06_unknownInstitutionWithValidSuffixCreatesUnknownAccount() = runBlocking {
        val unknownAcc = FinancialAccountIdentity(
            institutionId = null,
            institutionName = null,
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.LOW
        )

        val result = synchronizer.synchronize(unknownAcc)

        assertNotNull(result)
        assertEquals("unknown_bank_account_9020", result?.id)
        assertNull(result?.institutionId)
        assertNull(result?.institutionName)
        assertEquals("9020", result?.accountSuffix)
    }

    // ==================================================
    // TEST 7 — No suffix
    // ==================================================
    @Test
    fun test07_identityWithoutSuffixDoesNotCreateRegistryRecord() = runBlocking {
        val noSuffixIdentity = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = null,
            cardSuffix = null,
            instrumentType = InstrumentType.BANK_ACCOUNT
        )

        val result = synchronizer.synchronize(noSuffixIdentity)

        assertNull("Should not synchronize identity without account suffix", result)
        assertEquals(0, repository.getCount())
    }

    // ==================================================
    // TEST 8 — No financial transaction (promotional noise)
    // ==================================================
    @Test
    fun test08_promotionalNoiseSmsDoesNotReachSynchronization() = runBlocking {
        val fakeSmsReader = InMemorySmsReader(
            records = listOf(
                SmsRecord(
                    id = 1L,
                    address = "PROMO",
                    body = "Congratulations! Get ₹5,000 cashback! XXXXX9020",
                    dateMillis = 1711360000000L
                )
            )
        )
        val fakeExpenseDao = FakeExpenseDao()
        val manager = HistoricalSmsImportManager(
            smsReader = fakeSmsReader,
            dao = fakeExpenseDao,
            synchronizer = synchronizer
        )

        val scanResult = manager.scan(0L, System.currentTimeMillis())
        assertTrue(scanResult is SmsScanResult.Success)
        assertEquals(0, (scanResult as SmsScanResult.Success).scannedItems.size)

        manager.importTransactions(scanResult)
        assertEquals(0, repository.getCount())
    }

    // ==================================================
    // TEST 9 — Unknown -> HDFC reconciliation
    // ==================================================
    @Test
    fun test09_unknownToHdfcReconciliation() = runBlocking {
        // Step 1: Provisional unknown account is in the registry
        val unknownIdentity = FinancialAccountIdentity(
            institutionId = null,
            institutionName = null,
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.LOW
        )
        synchronizer.synchronize(unknownIdentity)
        assertNotNull(repository.getById("unknown_bank_account_9020"))

        // Step 2: Definitive HDFC identity arrives
        val hdfcIdentity = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH
        )
        synchronizer.synchronize(hdfcIdentity)

        // Step 3: Verified outcome
        assertEquals(1, repository.getCount())
        assertNotNull("hdfc_bank_account_9020 must exist", repository.getById("hdfc_bank_account_9020"))
        assertNull("unknown_bank_account_9020 must be reconciled and deleted", repository.getById("unknown_bank_account_9020"))
    }

    // ==================================================
    // TEST 10 — Unknown suffix ambiguity
    // ==================================================
    @Test
    fun test10_unknownSuffixAmbiguityPreservesBothRecords() = runBlocking {
        // Existing in registry: unknown_bank_account_9020
        repository.upsert(
            KnownFinancialAccount(
                id = "unknown_bank_account_9020",
                institutionId = null,
                institutionName = null,
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT,
                confidence = IdentityConfidence.LOW,
                source = AccountSource.HISTORICAL_SMS
            )
        )
        // Existing in registry: apgb_bank_account_9020
        repository.upsert(
            KnownFinancialAccount(
                id = "apgb_bank_account_9020",
                institutionId = "apgb",
                institutionName = "APG Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT,
                confidence = IdentityConfidence.HIGH,
                source = AccountSource.HISTORICAL_SMS
            )
        )

        // Incoming: HDFC BANK_ACCOUNT 9020
        synchronizer.synchronize(
            FinancialAccountIdentity(
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                accountSuffix = "9020",
                instrumentType = InstrumentType.BANK_ACCOUNT
            )
        )

        // Suffix 9020 is ambiguous across APGB and HDFC: unknown record must NOT be arbitrarily deleted
        assertEquals(3, repository.getCount())
        assertNotNull(repository.getById("unknown_bank_account_9020"))
        assertNotNull(repository.getById("apgb_bank_account_9020"))
        assertNotNull(repository.getById("hdfc_bank_account_9020"))

        // Also verify batch synchronization preserves unknown when both exist in batch
        val freshRepo = KnownFinancialAccountRepository(FakeKnownFinancialAccountDao())
        val freshSynchronizer = HistoricalSmsAccountRegistrySynchronizer(freshRepo)
        freshSynchronizer.synchronizeAll(
            listOf(
                FinancialAccountIdentity(institutionId = null, accountSuffix = "9020"),
                FinancialAccountIdentity(institutionId = "apgb", institutionName = "APG Bank", accountSuffix = "9020"),
                FinancialAccountIdentity(institutionId = "hdfc", institutionName = "HDFC Bank", accountSuffix = "9020")
            )
        )
        assertEquals(3, freshRepo.getCount())
        assertNotNull(freshRepo.getById("unknown_bank_account_9020"))
        assertNotNull(freshRepo.getById("apgb_bank_account_9020"))
        assertNotNull(freshRepo.getById("hdfc_bank_account_9020"))
    }

    // ==================================================
    // TEST 11 — Existing known account upgrades metadata
    // ==================================================
    @Test
    fun test11_existingKnownAccountUpgradesMetadata() = runBlocking {
        val initial = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.MEDIUM
        )
        synchronizer.synchronize(initial)

        val upgraded = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH
        )
        synchronizer.synchronize(upgraded)

        assertEquals(1, repository.getCount())
        val saved = repository.getById("hdfc_bank_account_9020")
        assertEquals(IdentityConfidence.HIGH, saved?.confidence)
    }

    // ==================================================
    // TEST 12 — Historical source
    // ==================================================
    @Test
    fun test12_allSynchronizedAccountsHaveHistoricalSmsSource() = runBlocking {
        val identity = FinancialAccountIdentity(
            institutionId = "sbi",
            institutionName = "State Bank of India",
            accountSuffix = "1065",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH
        )
        val result = synchronizer.synchronize(identity)

        assertEquals(AccountSource.HISTORICAL_SMS, result?.source)
    }

    // ==================================================
    // TEST 13 — Selected account behavior
    // ==================================================
    @Test
    fun test13_selectedAccountImportSynchronizesOnlySelectedAccounts() = runBlocking {
        val fakeSmsReader = InMemorySmsReader(
            records = listOf(
                SmsRecord(
                    id = 1L,
                    address = "AD-HDFCBK",
                    body = "Rs.500 debited from A/c XX9020 to Ravi. UPI Ref: 1111",
                    dateMillis = 1711360000000L
                ),
                SmsRecord(
                    id = 2L,
                    address = "AD-HDFCBK",
                    body = "Rs.250 debited from A/c XX4381 to Store. UPI Ref: 2222",
                    dateMillis = 1711360000000L
                ),
                SmsRecord(
                    id = 3L,
                    address = "VK-APGBANK",
                    body = "Rs.100 credited to A/c XX1065 from Raju. UPI Ref: 3333",
                    dateMillis = 1711360000000L
                )
            )
        )
        val fakeExpenseDao = FakeExpenseDao()
        val manager = HistoricalSmsImportManager(
            smsReader = fakeSmsReader,
            dao = fakeExpenseDao,
            synchronizer = synchronizer
        )

        val scanResult = manager.scan(0L, System.currentTimeMillis()) as SmsScanResult.Success
        assertEquals(3, scanResult.accountGroups.size)

        // Select ONLY HDFC 9020
        val selectedGroupIds = setOf("hdfc_bank_account_9020")
        manager.importTransactions(scanResult, selectedGroupIds)

        // Only HDFC 9020 should exist in the registry
        assertEquals(1, repository.getCount())
        assertNotNull(repository.getById("hdfc_bank_account_9020"))
        assertNull(repository.getById("hdfc_bank_account_4381"))
        assertNull(repository.getById("apgb_bank_account_1065"))
    }

    // ==================================================
    // TEST 14 — Scan is non-destructive
    // ==================================================
    @Test
    fun test14_scanIsNonDestructiveAndRegistryChangesOccurOnlyOnImport() = runBlocking {
        val fakeSmsReader = InMemorySmsReader(
            records = listOf(
                SmsRecord(
                    id = 1L,
                    address = "AD-HDFCBK",
                    body = "Rs.500 debited from A/c XX9020 to Ravi. UPI Ref: 1111",
                    dateMillis = 1711360000000L
                )
            )
        )
        val fakeExpenseDao = FakeExpenseDao()
        val manager = HistoricalSmsImportManager(
            smsReader = fakeSmsReader,
            dao = fakeExpenseDao,
            synchronizer = synchronizer
        )

        // 1. Scan phase: ZERO registry writes
        val scanResult = manager.scan(0L, System.currentTimeMillis()) as SmsScanResult.Success
        assertEquals(0, repository.getCount())
        assertEquals(1, scanResult.accountGroups.size)

        // 2. Import phase: Selected accounts are synchronized
        manager.importTransactions(scanResult)
        assertEquals(1, repository.getCount())
        assertNotNull(repository.getById("hdfc_bank_account_9020"))
    }
}
