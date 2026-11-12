package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.AccountSource
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.KnownFinancialAccountRepository
import com.subhashrelangi.arctracker.engine.account.*
import com.subhashrelangi.arctracker.engine.ingestion.HistoricalSmsAccountRegistrySynchronizer
import com.subhashrelangi.arctracker.engine.pipeline.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Robust backend test suite for Digital Wallets (PhonePe, Paytm, Amazon Pay, GPay)
 * and Liquid Cash accounts without numeric suffixes.
 */
class DigitalWalletAndCashBackendTest {

    private lateinit var fakeDao: FakeKnownFinancialAccountDao
    private lateinit var repository: KnownFinancialAccountRepository
    private lateinit var matcher: KnownFinancialAccountMatcher
    private lateinit var synchronizer: HistoricalSmsAccountRegistrySynchronizer

    @Before
    fun setup() {
        fakeDao = FakeKnownFinancialAccountDao()
        repository = KnownFinancialAccountRepository(fakeDao)
        matcher = KnownFinancialAccountMatcher(repository)
        synchronizer = HistoricalSmsAccountRegistrySynchronizer(repository)
    }

    @Test
    fun `WalletProviderRegistry recognizes popular Indian wallet providers`() {
        val phonepe = WalletProviderRegistry.getProviderById("phonepe")
        assertNotNull(phonepe)
        assertEquals("PhonePe Wallet", phonepe!!.canonicalName)
        assertEquals("com.phonepe.app", phonepe.packageName)

        val paytm = WalletProviderRegistry.getProviderById("paytm")
        assertNotNull(paytm)
        assertEquals("Paytm Wallet", paytm!!.canonicalName)

        val amazon = WalletProviderRegistry.getProviderById("amazon_pay")
        assertNotNull(amazon)
        assertEquals("Amazon Pay Balance", amazon!!.canonicalName)
    }

    @Test
    fun `WalletProviderRegistry matches providers from SMS text snippets`() {
        val p1 = WalletProviderRegistry.findProviderFromText("Paid Rs 150 using PhonePe Wallet to Merchant")
        assertEquals("phonepe", p1?.id)

        val p2 = WalletProviderRegistry.findProviderFromText("Debited Rs. 40 from your Paytm Wallet at Store")
        assertEquals("paytm", p2?.id)

        val p3 = WalletProviderRegistry.findProviderFromText("Paid Rs. 299 from your Amazon Pay balance to Uber")
        assertEquals("amazon_pay", p3?.id)

        val p4 = WalletProviderRegistry.findProviderFromText("Debited from HDFC Bank A/c XX1234")
        assertNull(p4) // Not a wallet
    }

    @Test
    fun `KnownFinancialAccount creates canonical IDs for wallets and liquid cash without suffixes`() {
        val wallet = KnownFinancialAccount.create(
            institutionId = "phonepe",
            institutionName = "PhonePe Wallet",
            instrumentType = InstrumentType.WALLET
        )
        assertEquals("phonepe_wallet", wallet.id)
        assertEquals("phonepe", wallet.institutionId)
        assertEquals("", wallet.accountSuffix)
        assertEquals(InstrumentType.WALLET, wallet.instrumentType)

        val cash = KnownFinancialAccount.create(
            instrumentType = InstrumentType.CASH
        )
        assertEquals("cash_liquid", cash.id)
        assertEquals("cash", cash.institutionId)
        assertEquals("Liquid Cash", cash.institutionName)
        assertEquals("", cash.accountSuffix)
        assertEquals(InstrumentType.CASH, cash.instrumentType)
    }

    @Test
    fun `AccountIdentityExtractor extracts digital wallet identity without requiring numeric suffix`() {
        val candidate = ValidatedTransactionCandidate(
            candidate = StructuredTransactionCandidate(
                sourceNotificationKey = "sms_test_wallet_1",
                packageName = "com.google.android.apps.messaging",
                postTime = System.currentTimeMillis(),
                amount = 150.0,
                direction = TransactionDirection.DEBIT
            ),
            validationState = ValidationState.ACCEPTABLE,
            evidenceLevel = EvidenceLevel.STRONG,
            isStructurallyValid = true,
            rejectionReasons = emptyList()
        )

        val identity = AccountIdentityExtractor.extractIdentity(
            candidate = candidate,
            rawBody = "Paid Rs 150 to Starbucks using PhonePe Wallet",
            sender = "VM-PHONEPE"
        )

        assertEquals(InstrumentType.WALLET, identity.instrumentType)
        assertEquals("phonepe", identity.institutionId)
        assertEquals("PhonePe Wallet", identity.institutionName)
        assertNull(identity.accountSuffix)
        assertNull(identity.cardSuffix)
        assertEquals(IdentityConfidence.HIGH, identity.confidence)
        assertTrue(identity.isComplete)
        assertEquals("PhonePe Wallet", identity.getDisplayName())

        val groupId = AccountIdentityExtractor.generateGroupId(identity)
        assertEquals("phonepe_wallet", groupId)
    }

    @Test
    fun `HistoricalSmsAccountRegistrySynchronizer synchronizes digital wallet and cash accounts`() = runBlocking {
        val walletIdentity = FinancialAccountIdentity(
            institutionId = "paytm",
            institutionName = "Paytm Wallet",
            accountSuffix = null,
            cardSuffix = null,
            instrumentType = InstrumentType.WALLET,
            confidence = IdentityConfidence.HIGH
        )

        val syncedWallet = synchronizer.synchronize(walletIdentity)
        assertNotNull(syncedWallet)
        assertEquals("paytm_wallet", syncedWallet!!.id)
        assertEquals(InstrumentType.WALLET, syncedWallet.instrumentType)
        assertEquals(1, repository.getCount())

        val cashIdentity = FinancialAccountIdentity(
            institutionId = "cash",
            institutionName = "Liquid Cash",
            accountSuffix = null,
            cardSuffix = null,
            instrumentType = InstrumentType.CASH,
            confidence = IdentityConfidence.HIGH
        )

        val syncedCash = synchronizer.synchronize(cashIdentity)
        assertNotNull(syncedCash)
        assertEquals("cash_liquid", syncedCash!!.id)
        assertEquals(InstrumentType.CASH, syncedCash.instrumentType)
        assertEquals(2, repository.getCount())
    }

    @Test
    fun `KnownFinancialAccountMatcher resolves wallet and cash accounts without suffix`() {
        // Register PhonePe Wallet and Liquid Cash
        val wallet = WalletProviderRegistry.createWalletAccount("phonepe")
        repository.upsert(wallet)

        val cash = WalletProviderRegistry.createLiquidCashAccount()
        repository.upsert(cash)

        // Test matching wallet candidate
        val walletCandidate = StructuredTransactionCandidate(
            sourceNotificationKey = "notif_wallet_1",
            packageName = "com.phonepe.app",
            postTime = System.currentTimeMillis(),
            rawContent = "Paid Rs 250 using PhonePe Wallet at Grocery Store"
        )

        val walletMatch = matcher.match(walletCandidate)
        assertTrue("Expected Matched result for PhonePe wallet", walletMatch is KnownFinancialAccountMatchResult.Matched)
        val matchedWallet = (walletMatch as KnownFinancialAccountMatchResult.Matched).account
        assertEquals("phonepe_wallet", matchedWallet.id)
        assertEquals(InstrumentType.WALLET, matchedWallet.instrumentType)

        // Test matching cash instrument directly
        val cashMatch = matcher.match(suffix = null, instrumentType = InstrumentType.CASH)
        assertTrue("Expected Matched result for Liquid Cash", cashMatch is KnownFinancialAccountMatchResult.Matched)
        val matchedCash = (cashMatch as KnownFinancialAccountMatchResult.Matched).account
        assertEquals("cash_liquid", matchedCash.id)
        assertEquals(InstrumentType.CASH, matchedCash.instrumentType)
    }
}
