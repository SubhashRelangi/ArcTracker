package com.example.arctracker

import com.example.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Milestone 1 Unit Tests: Shared Transaction Engine Foundation & Data Contracts.
 *
 * Verifies:
 * 1. TemporalTransactionEvidence (strict separation of transaction time vs arrival time).
 * 2. UserAccountContext (supporting evidence model for user-configured bank accounts).
 * 3. SmsRecord contract and conversion to TransactionSourceEvent.
 * 4. TransactionSourceEvent boundary abstraction and mobile sender detection.
 * 5. Shared Transaction Engine processing of SMS_HISTORY via TransactionPersistenceManager.
 */
class SharedTransactionEngineFoundationTest {

    // =========================================================================
    // 1. TEMPORAL EVIDENCE TESTS
    // =========================================================================

    @Test
    fun `temporal evidence preserves explicit content timestamp over arrival timestamp`() {
        val arrivalTime = 1772600000000L
        val explicitTxnTime = 1772590000000L

        val evidence = TemporalTransactionEvidence.fromExtracted(
            extractedTimestamp = explicitTxnTime,
            extractedSource = TimestampSource.CONTENT,
            sourceEventTime = arrivalTime,
            observedTime = 1772600050000L
        )

        assertTrue(evidence.hasExplicitTransactionTime)
        assertEquals(explicitTxnTime, evidence.transactionTimeMillis)
        assertEquals(arrivalTime, evidence.sourceEventTimeMillis)
        assertEquals(explicitTxnTime, evidence.effectiveTimestamp)
        assertEquals(TimestampSource.CONTENT, evidence.timestampSource)
    }

    @Test
    fun `temporal evidence keeps missing transaction time as null and does not fabricate from arrival time`() {
        val arrivalTime = 1772600000000L

        val evidence = TemporalTransactionEvidence.fromExtracted(
            extractedTimestamp = null,
            extractedSource = null,
            sourceEventTime = arrivalTime,
            observedTime = 1772600050000L
        )

        assertFalse(evidence.hasExplicitTransactionTime)
        assertFalse(evidence.hasExplicitTransactionDate)
        assertNull(evidence.transactionTimeMillis)
        assertNull(evidence.transactionDateMillis)
        // Effective fallback is the arrival time anchor, but explicit fields remain strictly null
        assertEquals(arrivalTime, evidence.effectiveTimestamp)
        assertEquals(TimestampSource.SMS_RECEIVE_TIME, evidence.timestampSource)
    }

    // =========================================================================
    // 2. USER ACCOUNT CONTEXT TESTS
    // =========================================================================

    @Test
    fun `user account context matches masked and clean account suffixes`() {
        val account = UserAccountContext(
            bankName = "IPPB",
            accountSuffix = "2959",
            accountType = UserAccountType.SAVINGS,
            displayName = "Postal Account",
            aliasKeywords = listOf("India Post", "IPPB")
        )

        assertTrue(account.matchesAccountSuffix("2959"))
        assertTrue(account.matchesAccountSuffix("X2959"))
        assertTrue(account.matchesAccountSuffix("XX2959"))
        assertTrue(account.matchesAccountSuffix("***2959"))
        assertFalse(account.matchesAccountSuffix("6828"))
        assertFalse(account.matchesAccountSuffix(null))
    }

    @Test
    fun `user account context matches bank name and sender aliases`() {
        val account = UserAccountContext(
            bankName = "SBI",
            accountSuffix = "6828",
            accountType = UserAccountType.SAVINGS,
            aliasKeywords = listOf("State Bank of India", "SBIN", "SBIINB")
        )

        assertTrue(account.matchesBank("SBI"))
        assertTrue(account.matchesBank("VK-SBIINB"))
        assertTrue(account.matchesBank("State Bank of India"))
        assertFalse(account.matchesBank("HDFC"))
        assertFalse(account.matchesBank(null))
    }

    @Test
    fun `user account context validates numeric suffix constraints`() {
        assertThrows(IllegalArgumentException::class.java) {
            UserAccountContext(
                bankName = "Test Bank",
                accountSuffix = "ABCD" // Non-digit
            )
        }
    }

    // =========================================================================
    // 3. SMS RECORD & SOURCE EVENT TESTS
    // =========================================================================

    @Test
    fun `sms record converts cleanly to transaction source event`() {
        val sms = SmsRecord(
            id = 101L,
            address = "VK-IPPB",
            body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken on 02-09-26 Ref 624571799987",
            dateMillis = 1772600000000L
        )

        val event = sms.toSourceEvent()

        assertEquals(TransactionSourceType.SMS_HISTORY, event.sourceType)
        assertEquals("sms_101", event.sourceId)
        assertEquals("VK-IPPB", event.sender)
        assertEquals(sms.body, event.rawText)
        assertEquals(1772600000000L, event.eventTimestamp)
        assertEquals("sms", event.category)
        assertFalse(event.isMobileSender)
    }

    @Test
    fun `transaction source event identifies mobile numbers as source-risk evidence`() {
        val mobileEvent = TransactionSourceEvent(
            sourceType = TransactionSourceType.SMS_HISTORY,
            sourceId = "sms_102",
            sender = "+919876543210",
            rawText = "Received Rs 500",
            eventTimestamp = 1772600000000L
        )

        assertTrue(mobileEvent.isMobileSender)

        val bankHeaderEvent = TransactionSourceEvent(
            sourceType = TransactionSourceType.SMS_HISTORY,
            sourceId = "sms_103",
            sender = "AD-HDFCBK",
            rawText = "Debited Rs 100",
            eventTimestamp = 1772600000000L
        )

        assertFalse(bankHeaderEvent.isMobileSender)
    }

    @Test
    fun `transaction source event provides bidirectional compatibility with captured notification info`() {
        val originalNotification = CapturedNotificationInfo(
            packageName = "com.phonepe.app",
            notificationKey = "notif_phonepe_123",
            postTime = 1772600000000L,
            title = "Paid to Swiggy",
            text = "₹350 paid successfully",
            category = "payment"
        )

        val event = TransactionSourceEvent.fromCapturedNotification(originalNotification)
        assertEquals(TransactionSourceType.NOTIFICATION, event.sourceType)
        assertEquals("notif_phonepe_123", event.sourceId)
        assertEquals("com.phonepe.app", event.sender)

        val roundTrip = event.toCapturedNotificationInfo()
        assertEquals(originalNotification.notificationKey, roundTrip.notificationKey)
        assertEquals(originalNotification.packageName, roundTrip.packageName)
        assertEquals(originalNotification.postTime, roundTrip.postTime)
    }

    // =========================================================================
    // 4. SHARED TRANSACTION ENGINE EXECUTION TESTS
    // =========================================================================

    @Test
    fun `processSourceEvent parses and persists real-world IPPB SMS transaction`() = runBlocking {
        val fakeDao = FakeExpenseDao()

        val ippbSms = TransactionSourceEvent(
            sourceType = TransactionSourceType.SMS_HISTORY,
            sourceId = "sms_9001",
            sender = "IPPB",
            rawText = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken on 02-09-26 Ref 624571799987. Avl Bal Rs.116.62.",
            eventTimestamp = 1772600000000L
        )

        val results = TransactionPersistenceManager.processSourceEvent(ippbSms, fakeDao)
        assertEquals(1, results.size)
        val firstResult = results.first()
        assertTrue("Result should be Inserted, but was $firstResult", firstResult is ExpensePersistenceResult.Inserted)

        val inserted = (firstResult as ExpensePersistenceResult.Inserted).expense
        assertEquals(250.0, inserted.amount, 0.001)
        assertEquals("Debit", inserted.type)
        assertEquals("SMS_HISTORY", inserted.source)
        assertEquals("sms_9001", inserted.notificationKey)

        // Verify in fake database
        val allInDb = fakeDao.getAllExpensesList()
        assertEquals(1, allInDb.size)
        assertEquals("SMS_HISTORY", allInDb.first().source)
        assertEquals(250.0, allInDb.first().amount, 0.001)
    }

    @Test
    fun `processSourceEvent enforces strict idempotency for repeated SMS source events`() = runBlocking {
        val fakeDao = FakeExpenseDao()

        val sbiSms = TransactionSourceEvent(
            sourceType = TransactionSourceType.SMS_HISTORY,
            sourceId = "sms_9002",
            sender = "SBI",
            rawText = "Dear UPI user A/C X6828 debited by Rs.10.00 on date 02Sep26 trf to KASI VISWANADHAM Refno 079173866211",
            eventTimestamp = 1772600000000L
        )

        // First ingestion
        val firstRun = TransactionPersistenceManager.processSourceEvent(sbiSms, fakeDao)
        assertTrue("Expected Inserted, but was ${firstRun.first()}", firstRun.first() is ExpensePersistenceResult.Inserted)
        assertEquals(1, fakeDao.getAllExpensesList().size)

        // Duplicate ingestion of the same source event
        val secondRun = TransactionPersistenceManager.processSourceEvent(sbiSms, fakeDao)
        assertTrue("Expected duplicate skip, but was ${secondRun.first()}", secondRun.first() is ExpensePersistenceResult.SkippedDuplicate)
        assertEquals(1, fakeDao.getAllExpensesList().size) // Row count strictly preserved
    }
}
