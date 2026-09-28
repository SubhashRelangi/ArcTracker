package com.example.arctracker

import com.example.arctracker.data.Expense
import com.example.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive verification test suite for:
 * STEP 3: Account / Bank Identity Extraction
 * STEP 4: Stable In-Memory Account Grouping
 */
class AccountIdentityAndGroupingTest {

    private lateinit var dao: FakeExpenseDao
    private lateinit var reader: InMemorySmsReader
    private lateinit var manager: HistoricalSmsImportManager

    @Before
    fun setUp() {
        dao = FakeExpenseDao()
        reader = InMemorySmsReader(hasPermission = true, records = emptyList())
        manager = HistoricalSmsImportManager(
            smsReader = reader,
            dao = dao,
            persistenceManager = TransactionPersistenceManager
        )
    }

    private fun makeSms(
        id: Long,
        body: String,
        address: String = "AD-BANK",
        dateMillis: Long = 1711360000000L
    ) = SmsRecord(
        id = id,
        address = address,
        body = body,
        dateMillis = dateMillis
    )

    // =========================================================================
    // STEP 3 TESTS: Account / Bank Identity Extraction
    // =========================================================================

    @Test
    fun `step3_01 - Explicit bank and account suffix in body extracted with HIGH confidence`() = runBlocking {
        val sms = makeSms(
            id = 1L,
            body = "A/c XX4381 debited by HDFC Bank Rs. 500 on 20-Sep-26 Ref 112233",
            address = "+919876543210" // personal sender, but explicit in body
        )
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.scannedItems.size)

        val identity = result.scannedItems[0].accountIdentity
        assertEquals("hdfc", identity.institutionId)
        assertEquals("HDFC Bank", identity.institutionName)
        assertEquals("4381", identity.accountSuffix)
        assertNull(identity.cardSuffix)
        assertEquals(InstrumentType.BANK_ACCOUNT, identity.instrumentType)
        assertEquals(IdentityConfidence.HIGH, identity.confidence)
        assertEquals("HDFC Bank ••••4381", identity.getDisplayName())
    }

    @Test
    fun `step3_02 - Bank sender and account suffix extracted with HIGH confidence`() = runBlocking {
        val sms = makeSms(
            id = 2L,
            body = "A/c XX4381 debited Rs. 500 on 20-Sep-26 to Store",
            address = "AD-HDFCBK" // Bank sender
        )
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.scannedItems.size)

        val identity = result.scannedItems[0].accountIdentity
        assertEquals("hdfc", identity.institutionId)
        assertEquals("HDFC Bank", identity.institutionName)
        assertEquals("4381", identity.accountSuffix)
        assertEquals(InstrumentType.BANK_ACCOUNT, identity.instrumentType)
        assertEquals(IdentityConfidence.HIGH, identity.confidence)
    }

    @Test
    fun `step3_03 - Account suffix without explicit bank extracted with MEDIUM confidence`() = runBlocking {
        val sms = makeSms(
            id = 3L,
            body = "A/c XX4381 debited Rs. 500 on 20-Sep-26 to Store",
            address = "+919876543210" // unknown sender, no bank in body
        )
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.scannedItems.size)

        val identity = result.scannedItems[0].accountIdentity
        assertNull(identity.institutionName)
        assertEquals("4381", identity.accountSuffix)
        assertEquals(InstrumentType.BANK_ACCOUNT, identity.instrumentType)
        assertEquals(IdentityConfidence.MEDIUM, identity.confidence)
        assertEquals("Unknown Bank ••••4381", identity.getDisplayName())
    }

    @Test
    fun `step3_04 - Bank without account suffix extracted with MEDIUM confidence`() = runBlocking {
        val sms = makeSms(
            id = 4L,
            body = "Debited by HDFC Bank Rs. 500 on 20-Sep-26 to Store",
            address = "AD-HDFCBK"
        )
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.scannedItems.size)

        val identity = result.scannedItems[0].accountIdentity
        assertEquals("HDFC Bank", identity.institutionName)
        assertNull(identity.accountSuffix)
        assertNull(identity.cardSuffix)
        assertEquals(InstrumentType.UNKNOWN, identity.instrumentType)
        assertEquals(IdentityConfidence.MEDIUM, identity.confidence)
        assertEquals("HDFC Bank (Account: Unknown)", identity.getDisplayName())
    }

    @Test
    fun `step3_05 - Unknown bank and unknown account yields UNKNOWN confidence`() = runBlocking {
        val sms = makeSms(
            id = 5L,
            body = "Debited Rs. 500 on 20-Sep-26 to Cafe Ref 998811",
            address = "+919876543210"
        )
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.scannedItems.size)

        val identity = result.scannedItems[0].accountIdentity
        assertNull(identity.institutionName)
        assertNull(identity.accountSuffix)
        assertNull(identity.cardSuffix)
        assertEquals(InstrumentType.UNKNOWN, identity.instrumentType)
        assertEquals(IdentityConfidence.UNKNOWN, identity.confidence)
        assertEquals("Unidentified Account", identity.getDisplayName())
    }

    @Test
    fun `step3_06 - Same bank with different account suffixes yields distinct identities`() = runBlocking {
        val sms1 = makeSms(6L, "A/c XX4381 debited by HDFC Bank Rs. 100 on 20-Sep-26", "AD-HDFCBK")
        val sms2 = makeSms(7L, "A/c XX7724 debited by HDFC Bank Rs. 200 on 20-Sep-26", "AD-HDFCBK")
        reader.records = listOf(sms1, sms2)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(2, result.scannedItems.size)

        val id1 = result.scannedItems[0].accountIdentity
        val id2 = result.scannedItems[1].accountIdentity

        assertEquals("HDFC Bank", id1.institutionName)
        assertEquals("4381", id1.accountSuffix)

        assertEquals("HDFC Bank", id2.institutionName)
        assertEquals("7724", id2.accountSuffix)

        assertNotEquals(id1.accountSuffix, id2.accountSuffix)
    }

    @Test
    fun `step3_07 - Different banks with same account suffix yields distinct identities`() = runBlocking {
        val sms1 = makeSms(8L, "A/c XX4381 debited by HDFC Bank Rs. 100 on 20-Sep-26", "AD-HDFCBK")
        val sms2 = makeSms(9L, "A/c XX4381 debited by ICICI Bank Rs. 200 on 20-Sep-26", "AD-ICICIB")
        reader.records = listOf(sms1, sms2)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(2, result.scannedItems.size)

        val id1 = result.scannedItems[0].accountIdentity
        val id2 = result.scannedItems[1].accountIdentity

        assertEquals("hdfc", id1.institutionId)
        assertEquals("icici", id2.institutionId)
        assertEquals("4381", id1.accountSuffix)
        assertEquals("4381", id2.accountSuffix)

        assertNotEquals(id1.institutionId, id2.institutionId)
    }

    @Test
    fun `step3_08 - Card suffix is not treated as account suffix`() = runBlocking {
        val sms = makeSms(
            id = 10L,
            body = "Card ending 1234 charged Rs. 500 on 20-Sep-26 at Amazon",
            address = "AD-HDFCBK"
        )
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.scannedItems.size)

        val identity = result.scannedItems[0].accountIdentity
        assertEquals("HDFC Bank", identity.institutionName)
        assertEquals(InstrumentType.CARD, identity.instrumentType)
        assertEquals("1234", identity.cardSuffix)
        assertNull(identity.accountSuffix)
        assertEquals("HDFC Bank Card ••••1234", identity.getDisplayName())
    }

    @Test
    fun `step3_09 - Full account number is safely reduced to trailing suffix`() = runBlocking {
        val sms = makeSms(
            id = 11L,
            body = "Account no. 5010023456789 debited by HDFC Bank Rs. 500 on 20-Sep-26",
            address = "AD-HDFCBK"
        )
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.scannedItems.size)

        val identity = result.scannedItems[0].accountIdentity
        assertEquals("6789", identity.accountSuffix)
        assertFalse(identity.accountSuffix!!.contains("50100234"))
    }

    @Test
    fun `step3_10 - Missing account identity does not invalidate transaction`() = runBlocking {
        val sms = makeSms(
            id = 12L,
            body = "Debited Rs. 750 on 20-Sep-26 to Zomato Ref 334455",
            address = "+919876543210"
        )
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.transactionCandidatesCount)
        assertEquals(1, result.scannedItems.size)

        val item = result.scannedItems[0]
        assertEquals(750.0, item.candidate.candidate.amount ?: 0.0, 0.001)
        assertEquals(ValidationState.ACCEPTABLE, item.candidate.validationState)
        assertEquals(IdentityConfidence.UNKNOWN, item.accountIdentity.confidence)
    }

    @Test
    fun `step3_11 - Missing bank identity does not invalidate transaction`() = runBlocking {
        val sms = makeSms(
            id = 13L,
            body = "A/c XX9988 debited Rs. 400 on 20-Sep-26 to Swiggy Ref 112233",
            address = "+919876543210"
        )
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.transactionCandidatesCount)
        assertEquals(1, result.scannedItems.size)

        val item = result.scannedItems[0]
        assertNull(item.accountIdentity.institutionName)
        assertEquals("9988", item.accountIdentity.accountSuffix)
        assertEquals(ValidationState.ACCEPTABLE, item.candidate.validationState)
    }

    @Test
    fun `step3_12 - Transaction confidence remains unchanged by account identity`() = runBlocking {
        val sms = makeSms(
            id = 14L,
            body = "Debited Rs. 500 on 20-Sep-26 to Store Ref 998811",
            address = "+919876543210"
        )
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        val item = result.scannedItems[0]

        // Transaction confidence is STRONG/VERY_STRONG even when account identity confidence is UNKNOWN
        assertTrue(
            item.candidate.evidenceLevel == EvidenceLevel.STRONG ||
                    item.candidate.evidenceLevel == EvidenceLevel.VERY_STRONG
        )
        assertEquals(IdentityConfidence.UNKNOWN, item.accountIdentity.confidence)
    }

    @Test
    fun `step3_13 - SMS source identity remains deterministic sms_id`() = runBlocking {
        val sms = makeSms(15L, "A/c XX4381 debited by HDFC Bank Rs. 500 on 20-Sep-26")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals("sms_15", result.scannedItems[0].sourceEvent.sourceId)
    }

    @Test
    fun `step3_14 - Existing deduplication remains completely unchanged`() = runBlocking {
        val smsA = makeSms(16L, "A/c XX4381 debited by HDFC Bank Rs. 500 on 20-Sep-26 Ref 998811", dateMillis = 1000L)
        val smsB = makeSms(16L, "A/c XX4381 debited by HDFC Bank Rs. 500 on 20-Sep-26 Ref 998811", dateMillis = 1000L)
        reader.records = listOf(smsA, smsB)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.newTransactionsCount)
        assertEquals(1, result.duplicatesCount)
        assertEquals(DedupDecision.NEW_TRANSACTION, result.scannedItems[0].plannedDecision)
        assertEquals(DedupDecision.DUPLICATE, result.scannedItems[1].plannedDecision)
    }

    // =========================================================================
    // STEP 4 TESTS: Stable In-Memory Account Grouping
    // =========================================================================

    @Test
    fun `step4_01 - One bank and account produces exactly one group`() = runBlocking {
        val sms1 = makeSms(101L, "A/c XX4381 debited by HDFC Bank Rs. 100 on 20-Sep-26 to Store1", "AD-HDFCBK")
        val sms2 = makeSms(102L, "A/c XX4381 debited by HDFC Bank Rs. 200 on 21-Sep-26 to Store2", "AD-HDFCBK")
        val sms3 = makeSms(103L, "A/c XX4381 debited by HDFC Bank Rs. 300 on 22-Sep-26 to Store3", "AD-HDFCBK")
        reader.records = listOf(sms1, sms2, sms3)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.accountGroups.size)

        val group = result.accountGroups[0]
        assertEquals("hdfc_bank_account_4381", group.groupId)
        assertEquals(3, group.transactionCount)
        assertEquals(600.0, group.totalDebit, 0.001)
        assertEquals(0.0, group.totalCredit, 0.001)
    }

    @Test
    fun `step4_02 - Three different accounts produce three distinct groups`() = runBlocking {
        val sms1 = makeSms(201L, "A/c XX4381 debited by HDFC Bank Rs. 100 on 20-Sep-26", "AD-HDFCBK")
        val sms2 = makeSms(202L, "SBI A/c XX7724 credited INR 200 on 21-Sep-26", "AD-SBIINB")
        val sms3 = makeSms(203L, "A/c XX9123 debited by ICICI Bank Rs. 300 on 22-Sep-26", "AD-ICICIB")
        reader.records = listOf(sms1, sms2, sms3)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(3, result.accountGroups.size)

        val groupIds = result.accountGroups.map { it.groupId }.toSet()
        assertTrue(groupIds.contains("hdfc_bank_account_4381"))
        assertTrue(groupIds.contains("sbi_bank_account_7724"))
        assertTrue(groupIds.contains("icici_bank_account_9123"))
    }

    @Test
    fun `step4_03 - Same bank with two accounts produces two distinct groups`() = runBlocking {
        val sms1 = makeSms(301L, "A/c XX4381 debited by HDFC Bank Rs. 100 on 20-Sep-26", "AD-HDFCBK")
        val sms2 = makeSms(302L, "A/c XX7724 debited by HDFC Bank Rs. 200 on 21-Sep-26", "AD-HDFCBK")
        reader.records = listOf(sms1, sms2)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(2, result.accountGroups.size)

        val groupIds = result.accountGroups.map { it.groupId }
        assertTrue(groupIds.contains("hdfc_bank_account_4381"))
        assertTrue(groupIds.contains("hdfc_bank_account_7724"))
    }

    @Test
    fun `step4_04 - Two banks with same suffix produce two distinct groups`() = runBlocking {
        val sms1 = makeSms(401L, "A/c XX4381 debited by HDFC Bank Rs. 100 on 20-Sep-26", "AD-HDFCBK")
        val sms2 = makeSms(402L, "A/c XX4381 debited by ICICI Bank Rs. 200 on 21-Sep-26", "AD-ICICIB")
        reader.records = listOf(sms1, sms2)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(2, result.accountGroups.size)

        val groupIds = result.accountGroups.map { it.groupId }
        assertTrue(groupIds.contains("hdfc_bank_account_4381"))
        assertTrue(groupIds.contains("icici_bank_account_4381"))
    }

    @Test
    fun `step4_05 - Bank known and account unknown produces dedicated bank unknown group`() = runBlocking {
        val sms = makeSms(501L, "Debited by HDFC Bank Rs. 500 on 20-Sep-26 to Store", "AD-HDFCBK")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.accountGroups.size)

        val group = result.accountGroups[0]
        assertEquals("hdfc_unknown_unknown", group.groupId)
        assertEquals("HDFC Bank (Account: Unknown)", group.identity.getDisplayName())
    }

    @Test
    fun `step4_06 - Bank unknown and account known produces dedicated account suffix group`() = runBlocking {
        val sms = makeSms(601L, "A/c XX4381 debited Rs. 500 on 20-Sep-26 to Store", "+919876543210")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.accountGroups.size)

        val group = result.accountGroups[0]
        assertEquals("unknown_bank_account_4381", group.groupId)
        assertEquals("Unknown Bank ••••4381", group.identity.getDisplayName())
    }

    @Test
    fun `step4_07 - Both bank and account unknown produce unidentified account group`() = runBlocking {
        val sms = makeSms(701L, "Debited Rs. 500 on 20-Sep-26 to Store Ref 998811", "+919876543210")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.accountGroups.size)

        val group = result.accountGroups[0]
        assertEquals("unidentified_account", group.groupId)
        assertEquals("Unidentified Account", group.identity.getDisplayName())
    }

    @Test
    fun `step4_08 - Card identity remains separate from bank-account identity`() = runBlocking {
        val smsCard = makeSms(801L, "Card ending 1234 charged Rs. 500 on 20-Sep-26 at Amazon", "AD-HDFCBK")
        val smsAccount = makeSms(802L, "A/c XX1234 debited by HDFC Bank Rs. 300 on 20-Sep-26", "AD-HDFCBK")
        reader.records = listOf(smsCard, smsAccount)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(2, result.accountGroups.size)

        val groupIds = result.accountGroups.map { it.groupId }
        assertTrue(groupIds.contains("hdfc_card_1234"))
        assertTrue(groupIds.contains("hdfc_bank_account_1234"))
    }

    @Test
    fun `step4_09 - Correct transaction count per group`() = runBlocking {
        val smsList = (1..5).map { id ->
            makeSms(
                id = id.toLong(),
                body = "A/c XX4381 debited by HDFC Bank Rs. 100 on 20-Sep-26 Ref 9900$id",
                address = "AD-HDFCBK"
            )
        }
        reader.records = smsList

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.accountGroups.size)
        assertEquals(5, result.accountGroups[0].transactionCount)
    }

    @Test
    fun `step4_10 - Correct debit total per group`() = runBlocking {
        val sms1 = makeSms(901L, "A/c XX4381 debited by HDFC Bank Rs. 150.00 on 20-Sep-26", "AD-HDFCBK")
        val sms2 = makeSms(902L, "A/c XX4381 debited by HDFC Bank Rs. 350.00 on 21-Sep-26", "AD-HDFCBK")
        reader.records = listOf(sms1, sms2)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.accountGroups.size)
        assertEquals(500.0, result.accountGroups[0].totalDebit, 0.001)
        assertEquals(0.0, result.accountGroups[0].totalCredit, 0.001)
    }

    @Test
    fun `step4_11 - Correct credit total per group`() = runBlocking {
        val sms1 = makeSms(1001L, "A/c XX4381 credited by HDFC Bank INR 1,000.00 on 20-Sep-26", "AD-HDFCBK")
        val sms2 = makeSms(1002L, "A/c XX4381 credited by HDFC Bank INR 2,500.00 on 21-Sep-26", "AD-HDFCBK")
        reader.records = listOf(sms1, sms2)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.accountGroups.size)
        assertEquals(0.0, result.accountGroups[0].totalDebit, 0.001)
        assertEquals(3500.0, result.accountGroups[0].totalCredit, 0.001)
    }

    @Test
    fun `step4_12 - Repeated scan produces identical group IDs and metrics`() = runBlocking {
        val sms1 = makeSms(1101L, "A/c XX4381 debited by HDFC Bank Rs. 100 on 20-Sep-26", "AD-HDFCBK")
        val sms2 = makeSms(1102L, "SBI A/c XX7724 credited INR 200 on 21-Sep-26", "AD-SBIINB")
        reader.records = listOf(sms1, sms2)

        val scan1 = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        val scan2 = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(scan1.accountGroups.size, scan2.accountGroups.size)
        for (i in scan1.accountGroups.indices) {
            val g1 = scan1.accountGroups[i]
            val g2 = scan2.accountGroups[i]
            assertEquals(g1.groupId, g2.groupId)
            assertEquals(g1.transactionCount, g2.transactionCount)
            assertEquals(g1.totalDebit, g2.totalDebit, 0.001)
            assertEquals(g1.totalCredit, g2.totalCredit, 0.001)
        }
    }

    @Test
    fun `step4_13 - Rejected messages never enter account groups`() = runBlocking {
        val promoSms = makeSms(1201L, "Flat 50% OFF on your next order! Use code SAVE50.", "AD-PROMO")
        val validSms = makeSms(1202L, "A/c XX4381 debited by HDFC Bank Rs. 500 on 20-Sep-26", "AD-HDFCBK")
        reader.records = listOf(promoSms, validSms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.accountGroups.size)
        assertEquals(1, result.accountGroups[0].transactionCount)
        assertEquals("hdfc_bank_account_4381", result.accountGroups[0].groupId)
    }

    @Test
    fun `step4_14 - Duplicate transactions do not inflate group counts or totals`() = runBlocking {
        val sms1 = makeSms(1301L, "A/c XX4381 debited by HDFC Bank Rs. 500 on 20-Sep-26 Ref 998811", "AD-HDFCBK", dateMillis = 1000L)
        val sms2 = makeSms(1301L, "A/c XX4381 debited by HDFC Bank Rs. 500 on 20-Sep-26 Ref 998811", "AD-HDFCBK", dateMillis = 1000L) // Exact duplicate
        reader.records = listOf(sms1, sms2)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.duplicatesCount)
        assertEquals(1, result.accountGroups.size)

        val group = result.accountGroups[0]
        assertEquals(1, group.transactionCount)
        assertEquals(500.0, group.totalDebit, 0.001)
    }

    @Test
    fun `step4_15 - Grouping performs strictly zero Room database writes`() = runBlocking {
        val initialCount = dao.getCount()
        assertEquals(0, initialCount)

        val sms1 = makeSms(1401L, "A/c XX4381 debited by HDFC Bank Rs. 500 on 20-Sep-26", "AD-HDFCBK")
        val sms2 = makeSms(1402L, "SBI A/c XX7724 credited INR 1,500 on 20-Sep-26", "AD-SBIINB")
        reader.records = listOf(sms1, sms2)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(2, result.accountGroups.size)

        // Strict non-destructive check: Room DB row count remains strictly 0
        assertEquals(0, dao.getCount())
        assertTrue(dao.getAllExpensesList().isEmpty())
    }

    @Test
    fun `step4_16 - Existing SmsScanResult behavior remains fully compatible`() = runBlocking {
        val sms = makeSms(1501L, "A/c XX4381 debited by HDFC Bank Rs. 250 on 20-Sep-26 to Mart", "AD-HDFCBK")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, result.messagesScanned)
        assertEquals(1, result.financialMessages)
        assertEquals(0, result.nonFinancialMessages)
        assertEquals(0, result.noiseMessages)
        assertEquals(1, result.transactionCandidatesCount)
        assertEquals(1, result.newTransactionsCount)
        assertEquals(0, result.duplicatesCount)
        assertEquals(250.0, result.totalDebitAmount, 0.001)
        assertEquals(1, result.scannedItems.size)
        assertEquals(1, result.accountGroups.size)
    }
}
