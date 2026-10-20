package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.DeletedTransactionTombstone
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * P2 — Step 4: Historical SMS Import Deletion Integrity Unit Tests.
 *
 * Verifies that:
 * 1. Scenario A: Transactions deleted by the user are never silently resurrected by a later SMS rescan/reimport.
 * 2. Scenario B: Genuinely different SMS transactions with identical amount/merchant are NOT falsely suppressed.
 * 3. Scenario C: Deleting manual transactions does not produce tombstones or suppress SMS imports.
 * 4. Scenario E: Deletion or tombstone registry write failures roll back atomically without partial state.
 * 5. Scenario F: Existing deduplication and live notification pipelines remain completely unaffected.
 */
class HistoricalSmsImportDeletionIntegrityTest {

    private lateinit var dao: FakeExpenseDao
    private lateinit var reader: InMemorySmsReader
    private lateinit var importManager: HistoricalSmsImportManager
    private lateinit var transactionManager: TransactionManager

    private val testTimestamp = 1759000000000L

    @Before
    fun setUp() {
        dao = FakeExpenseDao()
        reader = InMemorySmsReader(hasPermission = true, records = emptyList())
        importManager = HistoricalSmsImportManager(
            smsReader = reader,
            dao = dao,
            persistenceManager = TransactionPersistenceManager
        )
        transactionManager = TransactionManager(dao)
    }

    // =========================================================================
    // SCENARIO A: IMPORT -> DELETE -> RESCAN REMAINS DELETED
    // =========================================================================

    @Test
    fun testA1_importSms_delete_rescan_remainsDeleted() = runBlocking {
        val sms = SmsRecord(
            id = 501L,
            address = "AD-IPPB",
            body = "A/C X2959 Debit Rs.450.00 for UPI to Starbucks on 02-09-26 Ref 624571799987. Avl Bal Rs.116.62. -IPPB",
            dateMillis = testTimestamp
        )
        reader.records = listOf(sms)

        // 1. Initial Scan & Import
        val scan1 = importManager.scan(0L, testTimestamp + 10000L)
        assertTrue(scan1 is SmsScanResult.Success)
        val scanSuccess1 = scan1 as SmsScanResult.Success
        assertEquals(1, scanSuccess1.newTransactionsCount)
        assertEquals(0, scanSuccess1.duplicatesCount)

        val import1 = importManager.importTransactions(scanSuccess1)
        assertTrue(import1 is SmsImportResult.Success)
        assertEquals(1, (import1 as SmsImportResult.Success).insertedCount)

        val expensesAfterImport = dao.getAllExpenses().first()
        assertEquals(1, expensesAfterImport.size)
        val importedExpense = expensesAfterImport[0]
        assertEquals("Starbucks", importedExpense.merchant)
        assertEquals(450.0, importedExpense.amount, 0.001)
        assertEquals("SMS_HISTORY", importedExpense.source)
        assertEquals("sms_501", importedExpense.notificationKey)

        // 2. Explicitly delete the transaction as a user
        val deleteResult = transactionManager.deleteTransaction(importedExpense.id)
        assertTrue(deleteResult.isSuccess)
        assertTrue(dao.getAllExpenses().first().isEmpty())

        // Verify tombstone was created atomically
        assertTrue("Source key sms_501 must be tombstoned", dao.isSourceKeyTombstoned("sms_501"))

        // 3. Rescan the exact same SMS
        val scan2 = importManager.scan(0L, testTimestamp + 10000L)
        assertTrue(scan2 is SmsScanResult.Success)
        val scanSuccess2 = scan2 as SmsScanResult.Success

        // Must NOT be detected as new!
        assertEquals("Must be identified as duplicate", 1, scanSuccess2.duplicatesCount)
        assertEquals("Must NOT be counted as new", 0, scanSuccess2.newTransactionsCount)
        assertEquals("Total debit amount must be 0", 0.0, scanSuccess2.totalDebitAmount, 0.001)

        val item = scanSuccess2.scannedItems[0]
        assertEquals(DedupDecision.DUPLICATE, item.plannedDecision)
        assertEquals("Transaction source was previously deleted by user", item.reason)

        // 4. Re-import must NOT insert any row
        val import2 = importManager.importTransactions(scanSuccess2)
        assertTrue(import2 is SmsImportResult.Success)
        val importSuccess2 = import2 as SmsImportResult.Success
        assertEquals(0, importSuccess2.insertedCount)
        assertEquals(1, importSuccess2.duplicatesSkippedCount)

        // Database remains completely empty!
        assertTrue("Expense must remain deleted in Room database", dao.getAllExpenses().first().isEmpty())
    }

    @Test
    fun testA2_multiSegmentSms_partialDeletion_preservesSurvivingSegment() = runBlocking {
        // One SMS message with two transactions separated by newline
        val multiSms = SmsRecord(
            id = 502L,
            address = "AD-BANK",
            body = "Txn Alert: INR 150.00 debited for Uber on 10-Sep-26 Ref 111111.\nTxn Alert: INR 80.00 debited for Chai Point on 10-Sep-26 Ref 222222.",
            dateMillis = testTimestamp
        )
        reader.records = listOf(multiSms)

        val scan1 = importManager.scan(0L, testTimestamp + 10000L)
        assertTrue(scan1 is SmsScanResult.Success)
        val scanSuccess1 = scan1 as SmsScanResult.Success
        assertEquals(2, scanSuccess1.transactionCandidatesCount)

        val import1 = importManager.importTransactions(scanSuccess1)
        val importRes1 = import1 as SmsImportResult.Success
        assertEquals(2, importRes1.insertedCount + importRes1.reviewPendingCount)

        val expenses = dao.getAllExpenses().first()
        assertEquals(2, expenses.size)

        val chaiExpense = expenses.first { kotlin.math.abs(it.amount - 80.0) < 0.01 }
        val uberExpense = expenses.first { kotlin.math.abs(it.amount - 150.0) < 0.01 }

        // User deletes ONLY the Chai transaction
        val delResult = transactionManager.deleteTransaction(chaiExpense.id)
        assertTrue(delResult.isSuccess)

        assertEquals(1, dao.getAllExpenses().first().size)
        assertTrue("Chai source key must be tombstoned", dao.isSourceKeyTombstoned(chaiExpense.notificationKey))
        assertFalse("Uber source key must NOT be tombstoned", dao.isSourceKeyTombstoned(uberExpense.notificationKey))

        // Rescan
        val scan2 = importManager.scan(0L, testTimestamp + 10000L)
        assertTrue(scan2 is SmsScanResult.Success)
        val scanSuccess2 = scan2 as SmsScanResult.Success

        // Both are duplicates: one via tombstone, one via existing DB row
        assertEquals(0, scanSuccess2.newTransactionsCount)
        assertEquals(2, scanSuccess2.duplicatesCount)

        val import2 = importManager.importTransactions(scanSuccess2)
        val importRes2 = import2 as SmsImportResult.Success
        assertEquals(0, importRes2.insertedCount + importRes2.reviewPendingCount)

        // Surviving Uber expense remains intact
        val finalExpenses = dao.getAllExpenses().first()
        assertEquals(1, finalExpenses.size)
        assertEquals(uberExpense.id, finalExpenses[0].id)
    }

    // =========================================================================
    // SCENARIO B: DIFFERENT TRANSACTION WITH SAME AMOUNT/MERCHANT NOT SUPPRESSED
    // =========================================================================

    @Test
    fun testB_differentSms_sameAmountAndMerchant_isNotSuppressed() = runBlocking {
        // SMS 1
        val sms1 = SmsRecord(
            id = 601L,
            address = "AD-HDFCBK",
            body = "Rs 250.00 spent at SWIGGY on Card 1234 on 19-Oct-26",
            dateMillis = testTimestamp
        )
        // SMS 2 (genuinely different SMS event with identical amount and merchant on different date)
        val sms2 = SmsRecord(
            id = 602L,
            address = "AD-HDFCBK",
            body = "Rs 250.00 spent at SWIGGY on Card 1234 on 20-Oct-26",
            dateMillis = testTimestamp + 86400000L
        )

        reader.records = listOf(sms1)
        val scan1 = importManager.scan(0L, testTimestamp + 100000000L)
        importManager.importTransactions(scan1 as SmsScanResult.Success)

        val exp1 = dao.getAllExpenses().first()[0]
        assertEquals("SWIGGY", exp1.merchant)
        assertEquals(250.0, exp1.amount, 0.001)

        // Delete SMS 1
        transactionManager.deleteTransaction(exp1.id)
        assertTrue("SMS 1 must be tombstoned", dao.isSourceKeyTombstoned("sms_601"))

        // Now scan SMS 2
        reader.records = listOf(sms2)
        val scan2 = importManager.scan(0L, testTimestamp + 100000000L)
        assertTrue(scan2 is SmsScanResult.Success)
        val scanSuccess2 = scan2 as SmsScanResult.Success

        // SMS 2 has key sms_602, so it MUST be recognized as a new transaction!
        assertEquals("Genuinely different transaction must be detected as new", 1, scanSuccess2.newTransactionsCount)
        assertEquals(0, scanSuccess2.duplicatesCount)
        assertEquals(250.0, scanSuccess2.totalDebitAmount, 0.001)

        val import2 = importManager.importTransactions(scanSuccess2)
        assertEquals(1, (import2 as SmsImportResult.Success).insertedCount)

        val finalExpenses = dao.getAllExpenses().first()
        assertEquals(1, finalExpenses.size)
        assertEquals("sms_602", finalExpenses[0].notificationKey)
        assertEquals("SWIGGY", finalExpenses[0].merchant)
        assertEquals(250.0, finalExpenses[0].amount, 0.001)
    }

    // =========================================================================
    // SCENARIO C: MANUAL TRANSACTION DELETION DOES NOT SUPPRESS SMS IMPORTS
    // =========================================================================

    @Test
    fun testC_deletingManualTransaction_doesNotCreateTombstoneOrSuppressSms() = runBlocking {
        // 1. User creates a manual transaction
        val manualExpense = Expense(
            amount = 350.0,
            merchant = "Blue Tokai",
            type = "Debit",
            dateMillis = testTimestamp,
            source = "MANUAL",
            notificationKey = ""
        )
        val manualId = dao.insert(manualExpense).toInt()
        assertEquals(1, dao.getAllExpenses().first().size)

        // 2. User deletes manual transaction
        val delResult = transactionManager.deleteTransaction(manualId)
        assertTrue(delResult.isSuccess)
        assertTrue(dao.getAllExpenses().first().isEmpty())

        // 3. Confirm NO tombstone was created
        val allTombstones = dao.getAllTombstonedSmsSourceKeys()
        assertTrue("No tombstones should be created for manual transactions", allTombstones.isEmpty())

        // 4. An SMS for Blue Tokai arrives and is imported
        val sms = SmsRecord(
            id = 701L,
            address = "AD-HDFCBK",
            body = "Rs 350.00 spent at BLUE TOKAI on Card 1234 on 20-Oct-26",
            dateMillis = testTimestamp
        )
        reader.records = listOf(sms)

        val scan = importManager.scan(0L, testTimestamp + 10000L)
        assertTrue(scan is SmsScanResult.Success)
        val scanSuccess = scan as SmsScanResult.Success
        assertEquals("SMS must be detected as new transaction", 1, scanSuccess.newTransactionsCount)

        val importRes = importManager.importTransactions(scanSuccess)
        assertEquals(1, (importRes as SmsImportResult.Success).insertedCount)

        val currentList = dao.getAllExpenses().first()
        assertEquals(1, currentList.size)
        assertEquals("BLUE TOKAI", currentList[0].merchant)
        assertEquals(350.0, currentList[0].amount, 0.001)
        assertEquals("sms_701", currentList[0].notificationKey)
    }

    // =========================================================================
    // SCENARIO E: ATOMIC ROLLBACK ON DELETION OR TOMBSTONE WRITE FAILURE
    // =========================================================================

    @Test
    fun testE1_tombstoneWriteFailure_rollsBackTransactionDeletion() = runBlocking {
        val exp = Expense(
            amount = 120.0,
            merchant = "Tea Stall",
            type = "Debit",
            dateMillis = testTimestamp,
            source = "SMS_HISTORY",
            notificationKey = "sms_801"
        )
        val id = dao.insert(exp).toInt()
        assertEquals(1, dao.getAllExpenses().first().size)

        // Simulate tombstone database failure
        dao.failOnInsertTombstone = true

        val delResult = transactionManager.deleteTransaction(id)
        assertTrue("Delete must fail when tombstone insertion fails", delResult.isFailure)

        // Verify transaction was NOT deleted
        val currentExpenses = dao.getAllExpenses().first()
        assertEquals("Expense must roll back and remain in database", 1, currentExpenses.size)
        assertEquals(id, currentExpenses[0].id)
        assertFalse("Tombstone must not exist", dao.isSourceKeyTombstoned("sms_801"))
    }

    @Test
    fun testE2_expenseDeleteFailure_rollsBackTombstoneInsertion() = runBlocking {
        val exp = Expense(
            amount = 120.0,
            merchant = "Tea Stall",
            type = "Debit",
            dateMillis = testTimestamp,
            source = "SMS_HISTORY",
            notificationKey = "sms_802"
        )
        val id = dao.insert(exp).toInt()
        assertEquals(1, dao.getAllExpenses().first().size)

        // Simulate expense delete failure
        dao.failOnDelete = true

        val delResult = transactionManager.deleteTransaction(id)
        assertTrue("Delete must fail when delete fails", delResult.isFailure)

        // Verify state is completely intact
        val currentExpenses = dao.getAllExpenses().first()
        assertEquals(1, currentExpenses.size)
        assertEquals(id, currentExpenses[0].id)
        assertFalse("Tombstone must roll back", dao.isSourceKeyTombstoned("sms_802"))
    }

    // =========================================================================
    // SCENARIO F: REGRESSION VERIFICATION (SELF-TRANSFER, NOTIFICATIONS, DIRECT API)
    // =========================================================================

    @Test
    fun testF1_selfTransferSmsDeletion_tombstonesDeletedLegAndClearsCounterpart() = runBlocking {
        val transferId = "SELF_TRANSFER_123456"

        // Insert self transfer pair where debit leg originated from SMS
        val debitSmsLeg = Expense(
            amount = 1000.0,
            merchant = "Self Transfer Debit",
            type = "Debit",
            dateMillis = testTimestamp,
            source = "SMS_HISTORY",
            notificationKey = "sms_901",
            relationshipType = "SELF_TRANSFER",
            relationshipId = transferId
        )
        val creditLeg = Expense(
            amount = 1000.0,
            merchant = "Self Transfer Credit",
            type = "Credit",
            dateMillis = testTimestamp,
            source = "NOTIFICATION",
            notificationKey = "notif_credit_901",
            relationshipType = "SELF_TRANSFER",
            relationshipId = transferId
        )

        val debitId = dao.insert(debitSmsLeg).toInt()
        val creditId = dao.insert(creditLeg).toInt()

        assertEquals(2, dao.getAllExpenses().first().size)

        // User deletes the SMS debit leg
        val delResult = transactionManager.deleteTransaction(debitId)
        assertTrue(delResult.isSuccess)

        // Verify:
        // 1. Debit leg deleted
        val remaining = dao.getAllExpenses().first()
        assertEquals(1, remaining.size)
        assertEquals(creditId, remaining[0].id)

        // 2. Credit leg counterpart relationship fields cleared
        assertNull(remaining[0].relationshipType)
        assertNull(remaining[0].relationshipId)

        // 3. Tombstone created for sms_901
        assertTrue(dao.isSourceKeyTombstoned("sms_901"))

        // 4. Rescan SMS 901 does not resurrect
        val sms = SmsRecord(
            id = 901L,
            address = "AD-HDFCBK",
            body = "Rs 1000.00 debited from A/C 1234 to Self Account on 20-Oct-26",
            dateMillis = testTimestamp
        )
        reader.records = listOf(sms)

        val scan = importManager.scan(0L, testTimestamp + 10000L)
        val scanSuccess = scan as SmsScanResult.Success
        assertEquals(0, scanSuccess.newTransactionsCount)
        assertEquals(1, scanSuccess.duplicatesCount)
    }

    @Test
    fun testF2_directProcessCandidate_respectsTombstones() = runBlocking {
        // Record tombstone directly
        dao.insertTombstone(
            DeletedTransactionTombstone(
                sourceKey = "sms_999",
                sourceType = "SMS_HISTORY"
            )
        )

        val candidate = StructuredTransactionCandidate(
            sourceNotificationKey = "sms_999",
            packageName = "com.google.android.apps.messaging",
            postTime = testTimestamp,
            transactionTimestamp = testTimestamp,
            amount = 750.0,
            merchant = "Amazon",
            direction = TransactionDirection.DEBIT,
            status = TransactionStatus.SUCCESS,
            rawContent = "Rs 750 spent on Amazon"
        )
        val validated = ValidatedTransactionCandidate(
            candidate = candidate,
            validationState = ValidationState.ACCEPTABLE,
            evidenceLevel = EvidenceLevel.STRONG,
            isStructurallyValid = true,
            validationReasons = listOf("Valid")
        )

        val result = TransactionPersistenceManager.processValidatedCandidate(validated, dao)

        assertTrue("Direct candidate persistence must skip tombstoned source as duplicate", result is ExpensePersistenceResult.SkippedDuplicate)
        assertEquals("Transaction source was previously deleted by user", (result as ExpensePersistenceResult.SkippedDuplicate).reason)
        assertTrue("No rows inserted into DAO", dao.getAllExpenses().first().isEmpty())
    }
}
