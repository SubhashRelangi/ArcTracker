package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Step 2: Comprehensive Verification Tests for the Historical SMS Scanning Pipeline.
 *
 * Explicitly covers all 29 requirements outlined in Step 2.15 of the specification:
 * - Reader (1-5)
 * - Classification (6-16)
 * - Extraction (17-22)
 * - Validation (23-24)
 * - Deduplication (25-26)
 * - Scan Behavior (27-29)
 */
class HistoricalSmsScanPipelineVerificationTest {

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
    // SECTION 1: READER (Items 1 - 5)
    // =========================================================================

    @Test
    fun `01 - Reader preserves original Android SMS ID`() = runBlocking {
        val record = makeSms(id = 88492L, body = "A/c *1234 debited by Rs. 500 on 25-Sep-26")
        reader.records = listOf(record)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(1, result.scannedItems.size)
        val item = result.scannedItems[0]
        assertEquals("sms_88492", item.sourceEvent.sourceId)
        assertEquals("sms_88492", item.candidate.candidate.sourceNotificationKey)
    }

    @Test
    fun `02 - Reader strictly respects requested date range`() = runBlocking {
        val smsBefore = makeSms(id = 1L, body = "A/c *1234 debited by Rs. 100", dateMillis = 1000L)
        val smsInside = makeSms(id = 2L, body = "A/c *1234 debited by Rs. 200", dateMillis = 2000L)
        val smsAfter = makeSms(id = 3L, body = "A/c *1234 debited by Rs. 300", dateMillis = 3000L)
        reader.records = listOf(smsBefore, smsInside, smsAfter)

        val result = manager.scan(1500L, 2500L) as SmsScanResult.Success

        assertEquals(1, result.messagesScanned)
        assertEquals(1, result.scannedItems.size)
        assertEquals("sms_2", result.scannedItems[0].sourceEvent.sourceId)
    }

    @Test
    fun `03 - Reader enforces deterministic ordering by date ASC then ID ASC`() = runBlocking {
        val sms1 = makeSms(id = 10L, body = "A/c *1234 debited by Rs. 100", dateMillis = 2000L)
        val sms2 = makeSms(id = 5L, body = "A/c *1234 debited by Rs. 200", dateMillis = 1000L)
        val sms3 = makeSms(id = 2L, body = "A/c *1234 debited by Rs. 300", dateMillis = 1000L)
        // Insert out of order
        reader.records = listOf(sms1, sms2, sms3)

        val result = manager.scan(0L, 5000L) as SmsScanResult.Success

        assertEquals(3, result.messagesScanned)
        // Ordering should be: sms3 (date 1000, id 2), sms2 (date 1000, id 5), sms1 (date 2000, id 10)
        assertEquals("sms_2", result.scannedItems[0].sourceEvent.sourceId)
        assertEquals("sms_5", result.scannedItems[1].sourceEvent.sourceId)
        assertEquals("sms_10", result.scannedItems[2].sourceEvent.sourceId)
    }

    @Test
    fun `04 - Reader processes messages from unknown and mobile senders without hard gate`() = runBlocking {
        val unknownSenderSms = makeSms(
            id = 42L,
            address = "+919876543210",
            body = "Your A/c XX4381 has been debited by Rs.500. Txn ID ABC123."
        )
        reader.records = listOf(unknownSenderSms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(1, result.messagesScanned)
        assertEquals(1, result.transactionCandidatesCount)
        assertEquals(500.0, result.scannedItems[0].candidate.candidate.amount ?: 0.0, 0.001)
        assertEquals(TransactionDirection.DEBIT, result.scannedItems[0].candidate.candidate.direction)
    }

    @Test
    fun `05 - Reader handles missing or null optional fields safely`() {
        val recordWithNulls = SmsRecord(
            id = 99L,
            address = "UNKNOWN",
            body = "A/c *1234 debited by Rs. 150",
            dateMillis = 1000L,
            type = 1,
            read = true,
            subscriptionId = null
        )
        val event = recordWithNulls.toSourceEvent()
        val captured = event.toCapturedNotificationInfo()
        val normalized = NotificationNormalizer.normalize(captured)

        assertNotNull(normalized)
        assertEquals("UNKNOWN", normalized?.packageName)
        assertEquals("sms_99", normalized?.notificationKey)
    }

    // =========================================================================
    // SECTION 2: CLASSIFICATION (Items 6 - 16)
    // =========================================================================

    @Test
    fun `06 - Real debit message is correctly detected as debit transaction`() = runBlocking {
        val sms = makeSms(1L, "A/c *1234 debited by Rs. 750.00 on 20-Sep-26 towards Amazon")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(1, result.transactionCandidatesCount)
        assertEquals(TransactionDirection.DEBIT, result.scannedItems[0].candidate.candidate.direction)
        assertEquals(750.0, result.scannedItems[0].candidate.candidate.amount ?: 0.0, 0.001)
    }

    @Test
    fun `07 - Real credit message is correctly detected as credit transaction`() = runBlocking {
        val sms = makeSms(2L, "Rs. 2,000 received from John into A/c *5678 on 21-Sep-26")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(1, result.transactionCandidatesCount)
        assertEquals(TransactionDirection.CREDIT, result.scannedItems[0].candidate.candidate.direction)
        assertEquals(2000.0, result.scannedItems[0].candidate.candidate.amount ?: 0.0, 0.001)
    }

    @Test
    fun `08 - Refund and reversal messages follow established credit or reversal semantics`() = runBlocking {
        val refundSms = makeSms(3L, "Refund of ₹399 processed for your order")
        val reversalSms = makeSms(4L, "Payment of ₹500 reversed for txn 882910")
        reader.records = listOf(refundSms, reversalSms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(2, result.transactionCandidatesCount)
        assertEquals(TransactionDirection.CREDIT, result.scannedItems[0].candidate.candidate.direction)
        assertEquals(399.0, result.scannedItems[0].candidate.candidate.amount ?: 0.0, 0.001)

        assertEquals(TransactionStatus.REVERSED, result.scannedItems[1].candidate.candidate.status)
        assertEquals(500.0, result.scannedItems[1].candidate.candidate.amount ?: 0.0, 0.001)
    }

    @Test
    fun `09 - Promotional message is rejected and yields zero transaction candidates`() = runBlocking {
        val sms = makeSms(5L, "Get ₹5,000 cashback today! Recharge your mobile now on MyJio app.")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(0, result.transactionCandidatesCount)
        assertTrue(result.scannedItems.isEmpty())
        assertTrue(result.noiseMessages >= 1 || result.nonFinancialMessages >= 1)
    }

    @Test
    fun `10 - Cashback advertisement is rejected and yields zero transaction candidates`() = runBlocking {
        val sms = makeSms(6L, "Spend ₹2,000 and get ₹500 cashback on your next credit card payment.")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(0, result.transactionCandidatesCount)
        assertTrue(result.scannedItems.isEmpty())
    }

    @Test
    fun `11 - Loan advertisement is rejected and yields zero transaction candidates`() = runBlocking {
        val sms = makeSms(7L, "Pre-approved loan of ₹50,000 ready for instant disbursal! Click here to claim.")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(0, result.transactionCandidatesCount)
        assertTrue(result.scannedItems.isEmpty())
        assertEquals(1, result.noiseMessages)
    }

    @Test
    fun `12 - OTP message is rejected and yields zero transaction candidates`() = runBlocking {
        val sms = makeSms(8L, "Your OTP is 123456 for net banking login. Valid for 10 minutes.")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(0, result.transactionCandidatesCount)
        assertTrue(result.scannedItems.isEmpty())
        assertEquals(1, result.noiseMessages)
    }

    @Test
    fun `13 - Balance-only inquiry message is rejected and yields zero transaction candidates`() = runBlocking {
        val sms = makeSms(9L, "Available balance is ₹25,000 in your account ending *1234 as of 25-Sep.")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(0, result.transactionCandidatesCount)
        assertTrue(result.scannedItems.isEmpty())
    }

    @Test
    fun `14 - Security and login alert is rejected and yields zero transaction candidates`() = runBlocking {
        val sms = makeSms(10L, "Login detected on your account from Chrome on Windows at 10:15 AM.")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(0, result.transactionCandidatesCount)
        assertTrue(result.scannedItems.isEmpty())
    }

    @Test
    fun `15 - Informational statement is rejected and yields zero transaction candidates`() = runBlocking {
        val sms = makeSms(11L, "Your monthly statement is ready for viewing. Download your e-statement now.")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(0, result.transactionCandidatesCount)
        assertTrue(result.scannedItems.isEmpty())
    }

    @Test
    fun `16 - Hypothetical payment language is rejected and yields zero transaction candidates`() = runBlocking {
        val sms = makeSms(12L, "If you pay ₹500 before Friday, avoid late payment penalties on your card.")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(0, result.transactionCandidatesCount)
        assertTrue(result.scannedItems.isEmpty())
    }

    // =========================================================================
    // SECTION 3: EXTRACTION (Items 17 - 22)
    // =========================================================================

    @Test
    fun `17 - Extracts rupee symbol amount format`() = runBlocking {
        val sms = makeSms(13L, "₹500 debited from A/c *1234 for Swiggy order")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(1, result.scannedItems.size)
        assertEquals(500.0, result.scannedItems[0].candidate.candidate.amount ?: 0.0, 0.001)
    }

    @Test
    fun `18 - Extracts Rs without dot amount format`() = runBlocking {
        val sms = makeSms(14L, "Rs 500 debited from A/c *1234 for Zomato")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(1, result.scannedItems.size)
        assertEquals(500.0, result.scannedItems[0].candidate.candidate.amount ?: 0.0, 0.001)
    }

    @Test
    fun `19 - Extracts INR amount format`() = runBlocking {
        val sms = makeSms(15L, "INR 500 debited from A/c *1234 for Uber")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(1, result.scannedItems.size)
        assertEquals(500.0, result.scannedItems[0].candidate.candidate.amount ?: 0.0, 0.001)
    }

    @Test
    fun `20 - Extracts Indian comma formatted amount with decimals`() = runBlocking {
        val sms = makeSms(16L, "A/c *1234 debited by ₹1,250.00 on 20-Sep-26 towards BigBasket")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(1, result.scannedItems.size)
        assertEquals(1250.0, result.scannedItems[0].candidate.candidate.amount ?: 0.0, 0.001)
    }

    @Test
    fun `21 - Extracts transaction reference ID when present in SMS`() = runBlocking {
        val sms = makeSms(17L, "Paid Rs 500 to Swiggy on 20-Sep-26. UPI Ref: 624571799987.")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(1, result.scannedItems.size)
        assertEquals("624571799987", result.scannedItems[0].candidate.candidate.referenceId)
    }

    @Test
    fun `22 - Extracts merchant or payee counterparty when available`() = runBlocking {
        val sms = makeSms(18L, "A/c *2959 debited Rs. 350 for UPI to satya chicken on 02-09-26")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(1, result.scannedItems.size)
        assertEquals("satya chicken", result.scannedItems[0].candidate.candidate.merchant)
    }

    // =========================================================================
    // SECTION 4: VALIDATION (Items 23 - 24)
    // =========================================================================

    @Test
    fun `23 - Invalid or zero amount is counted as rejected and excluded from candidates`() = runBlocking {
        val sms = makeSms(19L, "Dear customer, Rs 0.00 debited from A/c *1234 on 10-Sep-26.")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(1, result.rejectedCount)
        assertEquals(0, result.transactionCandidatesCount)
        assertTrue(result.scannedItems.isEmpty())
    }

    @Test
    fun `24 - Candidate rejected by validation is excluded from final scanned candidates`() = runBlocking {
        // Pure greeting with no financial signals
        val sms = makeSms(20L, "Good morning, thank you for being a valued customer!")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(0, result.transactionCandidatesCount)
        assertTrue(result.scannedItems.isEmpty())
    }

    // =========================================================================
    // SECTION 5: DEDUPLICATION (Items 25 - 26)
    // =========================================================================

    @Test
    fun `25 - Same SMS scanned twice within single scan session is flagged as duplicate`() = runBlocking {
        val smsA = makeSms(21L, "A/c *1234 debited by Rs. 500 on 25-Sep-26 Ref 998811", dateMillis = 1000L)
        val smsB = makeSms(21L, "A/c *1234 debited by Rs. 500 on 25-Sep-26 Ref 998811", dateMillis = 1000L)
        reader.records = listOf(smsA, smsB)

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(2, result.messagesScanned)
        assertEquals(1, result.newTransactionsCount)
        assertEquals(1, result.duplicatesCount)
        // First is NEW_TRANSACTION, second is DUPLICATE
        assertEquals(DedupDecision.NEW_TRANSACTION, result.scannedItems[0].plannedDecision)
        assertEquals(DedupDecision.DUPLICATE, result.scannedItems[1].plannedDecision)
    }

    @Test
    fun `26 - Deterministic identity sms_id ensures identical source key across repeated scans`() = runBlocking {
        val sms = makeSms(777L, "A/c *1234 debited by Rs. 500 on 25-Sep-26")
        reader.records = listOf(sms)

        val scan1 = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        val scan2 = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        val key1 = scan1.scannedItems[0].sourceEvent.sourceId
        val key2 = scan2.scannedItems[0].sourceEvent.sourceId

        assertEquals("sms_777", key1)
        assertEquals("sms_777", key2)
        assertEquals(key1, key2)
    }

    // =========================================================================
    // SECTION 6: SCAN BEHAVIOR & ZERO WRITES (Items 27 - 29)
    // =========================================================================

    @Test
    fun `27 - Scan produces valid candidates with ZERO database writes`() = runBlocking {
        val initialCount = dao.getCount()
        assertEquals(0, initialCount)

        val sms = makeSms(23L, "A/c *1234 debited by Rs. 500 on 25-Sep-26 towards Swiggy")
        reader.records = listOf(sms)

        val result = manager.scan(0L, Long.MAX_VALUE)

        assertTrue(result is SmsScanResult.Success)
        val success = result as SmsScanResult.Success
        assertEquals(1, success.transactionCandidatesCount)

        // Strict non-destructive assertion: exactly 0 records in Room
        assertEquals(0, dao.getCount())
        assertTrue(dao.getAllExpensesList().isEmpty())
        assertTrue(dao.getPendingExpensesList().isEmpty())
    }

    @Test
    fun `28 - Pre-existing database records remain completely unmodified during scan`() = runBlocking {
        dao.insert(Expense(amount = 100.0, merchant = "Old Expense", dateMillis = 500L, notificationKey = "notif_prev"))
        assertEquals(1, dao.getCount())

        val sms = makeSms(24L, "A/c *1234 debited by Rs. 250 on 25-Sep-26")
        reader.records = listOf(sms)

        manager.scan(0L, Long.MAX_VALUE)

        // Database count remains strictly 1
        assertEquals(1, dao.getCount())
        val existing = dao.getAllExpensesList().first()
        assertEquals("Old Expense", existing.merchant)
        assertEquals(100.0, existing.amount, 0.001)
    }

    @Test
    fun `29 - Only the explicit import operation persists transactions into ExpenseDao`() = runBlocking {
        val sms = makeSms(25L, "A/C *1234 Debit Rs. 400.00 for UPI to Groceries on 25-Sep-26 Ref 123456789012")
        reader.records = listOf(sms)

        // 1. Scan phase: zero writes
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(0, dao.getCount())

        // 2. Explicit import phase: writes to Room
        val importResult = manager.importTransactions(scanResult)
        assertTrue(importResult is SmsImportResult.Success)
        val successImport = importResult as SmsImportResult.Success
        assertEquals(1, successImport.insertedCount)

        // 3. Exactly one record in Room
        assertEquals(1, dao.getCount())
        val saved = dao.getAllExpensesList().first()
        assertEquals(400.0, saved.amount, 0.001)
        assertEquals("Groceries", saved.merchant)
        assertEquals("sms_25", saved.notificationKey)
        assertEquals("SMS_HISTORY", saved.source)
    }
}
