package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.AccountSource
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.KnownFinancialAccountRepository
import com.subhashrelangi.arctracker.service.IdentityConfidence
import com.subhashrelangi.arctracker.service.InstrumentType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Milestone 2: Comprehensive Registry Unit Tests for [KnownFinancialAccount] and [KnownFinancialAccountRepository].
 */
class KnownFinancialAccountRegistryTest {

    private lateinit var dao: FakeKnownFinancialAccountDao
    private lateinit var repository: KnownFinancialAccountRepository

    @Before
    fun setUp() {
        dao = FakeKnownFinancialAccountDao()
        repository = KnownFinancialAccountRepository(dao)
    }

    // ==================================================
    // TEST 1 — Insert known account
    // ==================================================
    @Test
    fun test01_insertKnownAccount() {
        val account = KnownFinancialAccount.create(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            source = AccountSource.HISTORICAL_SMS
        )

        repository.upsert(account)

        assertEquals(1, repository.getCount())
        val retrieved = repository.getById("hdfc_bank_account_9020")
        assertNotNull(retrieved)
        assertEquals("hdfc", retrieved?.institutionId)
        assertEquals("HDFC Bank", retrieved?.institutionName)
        assertEquals("9020", retrieved?.accountSuffix)
        assertEquals(InstrumentType.BANK_ACCOUNT, retrieved?.instrumentType)
        assertEquals(IdentityConfidence.HIGH, retrieved?.confidence)
        assertEquals(AccountSource.HISTORICAL_SMS, retrieved?.source)
    }

    // ==================================================
    // TEST 2 — Same account upsert
    // ==================================================
    @Test
    fun test02_sameAccountUpsert_upgradesConfidenceAndPreservesIdAndCreatedAt() {
        val initialTime = 1711000000000L
        val account1 = KnownFinancialAccount.create(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.MEDIUM,
            source = AccountSource.HISTORICAL_SMS,
            createdAt = initialTime,
            updatedAt = initialTime
        )
        repository.upsert(account1)

        val updateTime = initialTime + 50000L
        val account2 = KnownFinancialAccount.create(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            source = AccountSource.USER_CONFIRMED,
            createdAt = updateTime,
            updatedAt = updateTime
        )
        repository.upsert(account2)

        // Exactly one record
        assertEquals(1, repository.getCount())
        val saved = repository.getById("hdfc_bank_account_9020")
        assertNotNull(saved)
        assertEquals("hdfc_bank_account_9020", saved?.id)
        assertEquals(initialTime, saved?.createdAt)
        assertTrue("updatedAt must be greater than createdAt", saved!!.updatedAt >= updateTime)
        assertEquals(IdentityConfidence.HIGH, saved.confidence)
        assertEquals(AccountSource.USER_CONFIRMED, saved.source)
    }

    // ==================================================
    // TEST 3 — Same suffix, different banks
    // ==================================================
    @Test
    fun test03_sameSuffixDifferentBanks_remainSeparate() {
        val hdfc = KnownFinancialAccount.create(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val apgb = KnownFinancialAccount.create(
            institutionId = "apgb",
            institutionName = "APG Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )

        repository.upsert(hdfc)
        repository.upsert(apgb)

        assertEquals(2, repository.getCount())
        assertNotNull(repository.getById("hdfc_bank_account_9020"))
        assertNotNull(repository.getById("apgb_bank_account_9020"))
        assertNotEquals(hdfc.id, apgb.id)
    }

    // ==================================================
    // TEST 4 — Same bank, different accounts
    // ==================================================
    @Test
    fun test04_sameBankDifferentAccounts_remainSeparate() {
        val acc1 = KnownFinancialAccount.create(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val acc2 = KnownFinancialAccount.create(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "4381",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )

        repository.upsert(acc1)
        repository.upsert(acc2)

        assertEquals(2, repository.getCount())
        assertNotNull(repository.getById("hdfc_bank_account_9020"))
        assertNotNull(repository.getById("hdfc_bank_account_4381"))
        assertNotEquals(acc1.id, acc2.id)
    }

    // ==================================================
    // TEST 5 — Same bank/suffix, different instrument
    // ==================================================
    @Test
    fun test05_sameBankAndSuffixDifferentInstrument_remainSeparate() {
        val bankAccount = KnownFinancialAccount.create(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val card = KnownFinancialAccount.create(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.CARD
        )

        repository.upsert(bankAccount)
        repository.upsert(card)

        assertEquals(2, repository.getCount())
        assertNotNull(repository.getById("hdfc_bank_account_9020"))
        assertNotNull(repository.getById("hdfc_card_9020"))
        assertNotEquals(bankAccount.id, card.id)
    }

    // ==================================================
    // TEST 6 — Unknown institution
    // ==================================================
    @Test
    fun test06_unknownInstitutionStoredSuccessfully() {
        val unknownAcc = KnownFinancialAccount.create(
            institutionId = null,
            institutionName = null,
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.LOW,
            source = AccountSource.LIVE_NOTIFICATION
        )

        repository.upsert(unknownAcc)

        assertEquals(1, repository.getCount())
        val saved = repository.getById("unknown_bank_account_9020")
        assertNotNull(saved)
        assertNull(saved?.institutionId)
        assertNull(saved?.institutionName)
        assertEquals("9020", saved?.accountSuffix)
        assertEquals(InstrumentType.BANK_ACCOUNT, saved?.instrumentType)
    }

    // ==================================================
    // TEST 7 — Unknown and known institution coexist
    // ==================================================
    @Test
    fun test07_unknownAndKnownInstitutionCoexist() {
        val unknownAcc = KnownFinancialAccount.create(
            institutionId = null,
            institutionName = null,
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val hdfcAcc = KnownFinancialAccount.create(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )

        repository.upsert(unknownAcc)
        repository.upsert(hdfcAcc)

        assertEquals(2, repository.getCount())
        assertNotNull(repository.getById("unknown_bank_account_9020"))
        assertNotNull(repository.getById("hdfc_bank_account_9020"))
    }

    // ==================================================
    // TEST 8 — Suffix-only lookup returns multiple accounts
    // ==================================================
    @Test
    fun test08_suffixOnlyLookupReturnsAllMatchingAccounts() {
        val hdfc = KnownFinancialAccount.create(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val apgb = KnownFinancialAccount.create(
            institutionId = "apgb",
            institutionName = "APG Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        repository.upsert(hdfc)
        repository.upsert(apgb)

        val results = repository.findBySuffix("9020")

        assertEquals(2, results.size)
        val institutionIds = results.map { it.institutionId }.toSet()
        assertTrue(institutionIds.contains("hdfc"))
        assertTrue(institutionIds.contains("apgb"))
    }

    // ==================================================
    // TEST 9 — Exact lookup
    // ==================================================
    @Test
    fun test09_exactLookupReturnsOnlyMatchingAccount() {
        val hdfc = KnownFinancialAccount.create(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val apgb = KnownFinancialAccount.create(
            institutionId = "apgb",
            institutionName = "APG Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        repository.upsert(hdfc)
        repository.upsert(apgb)

        val exactMatch = repository.findByExact(
            institutionId = "hdfc",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            suffix = "9020"
        )

        assertNotNull(exactMatch)
        assertEquals("hdfc_bank_account_9020", exactMatch?.id)
        assertEquals("HDFC Bank", exactMatch?.institutionName)
    }

    // ==================================================
    // TEST 10 — Card/account distinction
    // ==================================================
    @Test
    fun test10_cardAndAccountDoNotCollide() {
        val bankAccount = KnownFinancialAccount.create(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val card = KnownFinancialAccount.create(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.CARD
        )

        repository.upsert(bankAccount)
        repository.upsert(card)

        val bankResult = repository.findByExact("hdfc", InstrumentType.BANK_ACCOUNT, "9020")
        val cardResult = repository.findByExact("hdfc", InstrumentType.CARD, "9020")

        assertNotNull(bankResult)
        assertNotNull(cardResult)
        assertEquals(InstrumentType.BANK_ACCOUNT, bankResult?.instrumentType)
        assertEquals(InstrumentType.CARD, cardResult?.instrumentType)
        assertNotEquals(bankResult?.id, cardResult?.id)
    }

    // ==================================================
    // TEST 11 — Null metadata update does not erase existing identity
    // ==================================================
    @Test
    fun test11_nullMetadataUpdatePreservesExistingIdentity() {
        val existingHdfc = KnownFinancialAccount.create(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            source = AccountSource.USER_CONFIRMED
        )
        repository.upsert(existingHdfc)

        // Attempting to upsert an unknown record creates a distinct canonical record
        val unknownIncoming = KnownFinancialAccount.create(
            institutionId = null,
            institutionName = null,
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.UNKNOWN,
            source = AccountSource.LIVE_NOTIFICATION
        )
        repository.upsert(unknownIncoming)

        // Verify the original HDFC identity was untouched
        val retrievedHdfc = repository.getById("hdfc_bank_account_9020")
        assertNotNull(retrievedHdfc)
        assertEquals("hdfc", retrievedHdfc?.institutionId)
        assertEquals("HDFC Bank", retrievedHdfc?.institutionName)
        assertEquals(IdentityConfidence.HIGH, retrievedHdfc?.confidence)
        assertEquals(AccountSource.USER_CONFIRMED, retrievedHdfc?.source)

        // Verify unknown account is separately maintained
        val retrievedUnknown = repository.getById("unknown_bank_account_9020")
        assertNotNull(retrievedUnknown)
    }

    // ==================================================
    // TEST 12 — Stable ID
    // ==================================================
    @Test
    fun test12_stableIdProducedAcrossSeparateOperations() {
        val id1 = KnownFinancialAccount.generateId("HDFC ", InstrumentType.BANK_ACCOUNT, "9020")
        val id2 = KnownFinancialAccount.generateId("hdfc", InstrumentType.BANK_ACCOUNT, "XXXXX9020")
        val id3 = KnownFinancialAccount.generateId(" Hdfc", InstrumentType.BANK_ACCOUNT, "••••9020")

        assertEquals("hdfc_bank_account_9020", id1)
        assertEquals("hdfc_bank_account_9020", id2)
        assertEquals("hdfc_bank_account_9020", id3)
    }

    // ==================================================
    // TEST 13 — Full account numbers are never stored
    // ==================================================
    @Test
    fun test13_fullAccountNumbersAreNeverStored() {
        val fullAccountNumber = "123456789020"
        val maskedFullNumber = "XXXXXXXXXXXX9020"

        val acc1 = KnownFinancialAccount.create(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = fullAccountNumber,
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val acc2 = KnownFinancialAccount.create(
            institutionId = "sbi",
            institutionName = "State Bank of India",
            accountSuffix = maskedFullNumber,
            instrumentType = InstrumentType.BANK_ACCOUNT
        )

        assertEquals("9020", acc1.accountSuffix)
        assertEquals("hdfc_bank_account_9020", acc1.id)
        assertFalse(acc1.accountSuffix.contains("12345678"))

        assertEquals("9020", acc2.accountSuffix)
        assertEquals("sbi_bank_account_9020", acc2.id)
        assertFalse(acc2.accountSuffix.contains("X"))
    }
}
