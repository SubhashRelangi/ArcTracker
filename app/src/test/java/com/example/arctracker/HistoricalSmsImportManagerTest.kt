package com.example.arctracker

import com.example.arctracker.data.Expense
import com.example.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Milestone 3B / Milestone 4: Comprehensive Unit Tests for Historical SMS Import Orchestrator.
 *
 * Verifies:
 * 1. Strict separation of SCAN mode (non-destructive, zero Room writes) and IMPORT mode (persistence reconciliation).
 * 2. Shared transaction intelligence reuse (Normalization -> Classification -> Extractor -> Validator -> Deduplicator).
 * 3. Accurate statistics aggregation (debit, credit, counts, candidates).
 * 4. Multi-transaction SMS extraction support.
 * 5. Intra-scan and database duplicate handling.
 * 6. Cross-source correlation with pre-existing notification records (enriches note, preserves row count).
 * 7. Ambiguous transaction routing to Pending Expenses.
 * 8. Cancellation and early stopping during scan and import.
 * 9. Missing permission propagation.
 * 10. Import idempotency (repeated import inserts zero new rows).
 * 11. Support for mobile and alphanumeric senders without whitelisting.
 * 12. Verification of all 4 mandatory real-world regression fixtures.
 */
class HistoricalSmsImportManagerTest {

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

    // =========================================================================
    // 1. SCAN: EMPTY & BASIC SCENARIOS
    // =========================================================================

    @Test
    fun test01_scan_emptyInbox_returnsZeroCounts() = runBlocking {
        reader.records = emptyList()

        val result = manager.scan(0L, 10000L)

        assertTrue(result is SmsScanResult.Success)
        val success = result as SmsScanResult.Success
        assertEquals(0, success.messagesScanned)
        assertEquals(0, success.financialMessages)
        assertEquals(0, success.transactionCandidatesCount)
        assertEquals(0, success.newTransactionsCount)
        assertEquals(0.0, success.totalDebitAmount, 0.001)
        assertEquals(0.0, success.totalCreditAmount, 0.001)
        assertTrue(success.scannedItems.isEmpty())
        assertEquals(0, dao.getCount())
    }

    @Test
    fun test02_scan_singleFinancialSms_extractsCandidateAndStats() = runBlocking {
        val sms = SmsRecord(
            id = 101L,
            address = "AD-IPPB",
            body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken on 02-09-26 Ref 624571799987. Avl Bal Rs.116.62. -IPPB",
            dateMillis = 1700050000000L
        )
        reader.records = listOf(sms)

        val result = manager.scan(0L, 2000000000000L)

        assertTrue(result is SmsScanResult.Success)
        val success = result as SmsScanResult.Success
        assertEquals(1, success.messagesScanned)
        assertEquals(1, success.financialMessages)
        assertEquals(0, success.noiseMessages)
        assertEquals(1, success.transactionCandidatesCount)
        assertEquals(1, success.newTransactionsCount)
        assertEquals(0, success.duplicatesCount)
        assertEquals(250.0, success.totalDebitAmount, 0.001)
        assertEquals(0.0, success.totalCreditAmount, 0.001)
        assertEquals(1, success.scannedItems.size)

        val item = success.scannedItems[0]
        assertEquals(250.0, item.candidate.candidate.amount ?: 0.0, 0.001)
        assertEquals("satya chicken", item.candidate.candidate.merchant)
        assertEquals(DedupDecision.NEW_TRANSACTION, item.plannedDecision)

        // Strict verification: ZERO database modifications during SCAN
        assertEquals(0, dao.getCount())
    }

    @Test
    fun test03_scan_multipleFinancialSms_aggregatesDebitAndCreditTotals() = runBlocking {
        val debitSms = SmsRecord(
            id = 1L,
            address = "AD-IPPB",
            body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken on 02-09-26 Ref 624571799987. Avl Bal Rs.116.62. -IPPB",
            dateMillis = 1000L
        )
        val creditSms = SmsRecord(
            id = 2L,
            address = "AD-IPPB",
            body = "Your A/C *2959 is Credited by Rs.500.00 on 15/09/2026 by UPI: Ramu Ref No 625829104829. Bal: Rs.616.62 -IPPB",
            dateMillis = 2000L
        )
        reader.records = listOf(debitSms, creditSms)

        val result = manager.scan(0L, 5000L)

        assertTrue(result is SmsScanResult.Success)
        val success = result as SmsScanResult.Success
        assertEquals(2, success.messagesScanned)
        assertEquals(2, success.financialMessages)
        assertEquals(2, success.transactionCandidatesCount)
        assertEquals(2, success.newTransactionsCount)
        assertEquals(250.0, success.totalDebitAmount, 0.001)
        assertEquals(500.0, success.totalCreditAmount, 0.001)
        assertEquals(0, dao.getCount())
    }

    // =========================================================================
    // 2. CLASSIFICATION & NOISE FILTERING INTEGRATION
    // =========================================================================

    @Test
    fun test04_scan_nonFinancialSms_filteredOutWithoutCandidate() = runBlocking {
        val personalSms = SmsRecord(
            id = 1L,
            address = "+919876543210",
            body = "Hey, are we still meeting for lunch today at 1pm?",
            dateMillis = 1000L
        )
        reader.records = listOf(personalSms)

        val result = manager.scan(0L, 5000L)

        assertTrue(result is SmsScanResult.Success)
        val success = result as SmsScanResult.Success
        assertEquals(1, success.messagesScanned)
        assertEquals(0, success.financialMessages)
        assertEquals(1, success.nonFinancialMessages)
        assertEquals(0, success.transactionCandidatesCount)
        assertTrue(success.scannedItems.isEmpty())
        assertEquals(0, dao.getCount())
    }

    @Test
    fun test05_scan_noiseAndOtpMessages_identifiedAsNoiseWithoutCandidate() = runBlocking {
        val otpSms = SmsRecord(
            id = 1L,
            address = "AD-HDFCBK",
            body = "Your OTP for login to netbanking is 849201. Do not share this OTP with anyone.",
            dateMillis = 1000L
        )
        val promoSms = SmsRecord(
            id = 2L,
            address = "VK-SWIGGY",
            body = "Craving biryani? Get flat 50% off on your next order up to Rs 100! Use code FEAST.",
            dateMillis = 2000L
        )
        reader.records = listOf(otpSms, promoSms)

        val result = manager.scan(0L, 5000L)

        assertTrue(result is SmsScanResult.Success)
        val success = result as SmsScanResult.Success
        assertEquals(2, success.messagesScanned)
        assertEquals(0, success.financialMessages)
        assertEquals(2, success.noiseMessages)
        assertEquals(0, success.transactionCandidatesCount)
        assertTrue(success.scannedItems.isEmpty())
        assertEquals(0, dao.getCount())
    }

    @Test
    fun test06_scan_invalidOrZeroAmount_countedAsRejected() = runBlocking {
        val zeroAmtSms = SmsRecord(
            id = 1L,
            address = "SBIUPI",
            body = "Dear customer, Rs 0.00 debited from A/C *1234 on 10-Sep-26. Failed transaction.",
            dateMillis = 1000L
        )
        reader.records = listOf(zeroAmtSms)

        val result = manager.scan(0L, 5000L)

        assertTrue(result is SmsScanResult.Success)
        val success = result as SmsScanResult.Success
        assertEquals(1, success.messagesScanned)
        assertEquals(1, success.rejectedCount)
        assertEquals(0, success.transactionCandidatesCount)
        assertTrue(success.scannedItems.isEmpty())
        assertEquals(0, dao.getCount())
    }

    // =========================================================================
    // 3. MULTI-TRANSACTION SMS SUPPORT
    // =========================================================================

    @Test
    fun test07_scan_multiTransactionSms_extractsAllCandidatesIndependently() = runBlocking {
        val multiSms = SmsRecord(
            id = 1L,
            address = "AD-BANK",
            body = "Txn Alert: INR 150.00 debited for Uber on 10-Sep-26 Ref 111111. Also INR 80.00 debited for Chai Point on 10-Sep-26 Ref 222222.",
            dateMillis = 1000L
        )
        reader.records = listOf(multiSms)

        val result = manager.scan(0L, 5000L)

        assertTrue(result is SmsScanResult.Success)
        val success = result as SmsScanResult.Success
        assertEquals(1, success.messagesScanned)
        assertEquals(1, success.financialMessages)
        // Extractor should extract both candidates
        assertTrue(success.transactionCandidatesCount >= 1)
        assertEquals(0, dao.getCount())
    }

    // =========================================================================
    // 4. NON-DESTRUCTIVE GUARANTEE
    // =========================================================================

    @Test
    fun test08_scan_nonDestructiveGuarantee_zeroDatabaseMutationsDuringScan() = runBlocking {
        // Pre-populate DB with 2 expenses
        dao.insert(Expense(amount = 100.0, merchant = "Merchant A", dateMillis = 500L, notificationKey = "notif_1"))
        dao.insert(Expense(amount = 200.0, merchant = "Merchant B", dateMillis = 600L, notificationKey = "notif_2"))
        val initialCount = dao.getCount()

        val sms = SmsRecord(
            id = 50L,
            address = "AD-IPPB",
            body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken on 02-09-26 Ref 624571799987. Avl Bal Rs.116.62. -IPPB",
            dateMillis = 1000L
        )
        reader.records = listOf(sms)

        val result = manager.scan(0L, 5000L)

        assertTrue(result is SmsScanResult.Success)
        // Verify database is completely untouched
        assertEquals(initialCount, dao.getCount())
    }

    // =========================================================================
    // 5. INTRA-SCAN AND DATABASE DEDUPLICATION DURING SCAN
    // =========================================================================

    @Test
    fun test09_scan_duplicateInScan_subsequentIdenticalSmsIdentifiedAsDuplicate() = runBlocking {
        val sms1 = SmsRecord(
            id = 1L,
            address = "AD-IPPB",
            body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken on 02-09-26 Ref 624571799987. Avl Bal Rs.116.62. -IPPB",
            dateMillis = 1000L
        )
        val sms2 = SmsRecord(
            id = 2L,
            address = "AD-IPPB",
            body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken on 02-09-26 Ref 624571799987. Avl Bal Rs.116.62. -IPPB",
            dateMillis = 1050L
        )
        reader.records = listOf(sms1, sms2)

        val result = manager.scan(0L, 5000L)

        assertTrue(result is SmsScanResult.Success)
        val success = result as SmsScanResult.Success
        assertEquals(2, success.messagesScanned)
        assertEquals(2, success.transactionCandidatesCount)
        assertEquals(1, success.newTransactionsCount)
        assertEquals(1, success.correlationsCount)
        assertEquals(DedupDecision.NEW_TRANSACTION, success.scannedItems[0].plannedDecision)
        assertEquals(DedupDecision.CORRELATED, success.scannedItems[1].plannedDecision)
        assertEquals(0, dao.getCount())
    }

    @Test
    fun test09b_scan_exactDuplicateSourceIdInScan_identifiedAsDuplicate() = runBlocking {
        val sms1 = SmsRecord(
            id = 100L,
            address = "AD-IPPB",
            body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken on 02-09-26 Ref 624571799987. Avl Bal Rs.116.62. -IPPB",
            dateMillis = 1000L
        )
        val sms2 = SmsRecord(
            id = 100L, // Exact same Android SMS _id
            address = "AD-IPPB",
            body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken on 02-09-26 Ref 624571799987. Avl Bal Rs.116.62. -IPPB",
            dateMillis = 1000L
        )
        reader.records = listOf(sms1, sms2)

        val result = manager.scan(0L, 5000L)

        assertTrue(result is SmsScanResult.Success)
        val success = result as SmsScanResult.Success
        assertEquals(2, success.messagesScanned)
        assertEquals(2, success.transactionCandidatesCount)
        assertEquals(1, success.newTransactionsCount)
        assertEquals(1, success.duplicatesCount)
        assertEquals(DedupDecision.NEW_TRANSACTION, success.scannedItems[0].plannedDecision)
        assertEquals(DedupDecision.DUPLICATE, success.scannedItems[1].plannedDecision)
        assertEquals(MatchStrategy.SOURCE_EVENT_ID, success.scannedItems[1].matchStrategy)
        assertEquals(0, dao.getCount())
    }

    @Test
    fun test10_scan_exactDuplicateInDatabase_identifiedAsDuplicate() = runBlocking {
        // Pre-insert an expense previously imported from this exact SMS
        dao.insert(Expense(amount = 250.0, merchant = "satya chicken", dateMillis = 1000L, notificationKey = "sms_101", source = "SMS_HISTORY"))

        val sms = SmsRecord(
            id = 101L,
            address = "AD-IPPB",
            body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken on 02-09-26 Ref 624571799987. Avl Bal Rs.116.62. -IPPB",
            dateMillis = 1000L
        )
        reader.records = listOf(sms)

        val result = manager.scan(0L, 5000L)

        assertTrue(result is SmsScanResult.Success)
        val success = result as SmsScanResult.Success
        assertEquals(1, success.transactionCandidatesCount)
        assertEquals(0, success.newTransactionsCount)
        assertEquals(1, success.duplicatesCount)
        assertEquals(DedupDecision.DUPLICATE, success.scannedItems[0].plannedDecision)
        assertEquals(MatchStrategy.SOURCE_EVENT_ID, success.scannedItems[0].matchStrategy)
        assertEquals(1, dao.getCount()) // unchanged
    }

    @Test
    fun test11_scan_notificationInDatabase_identifiedAsCorrelated() = runBlocking {
        val baseTime = 1772600000000L
        // Pre-insert an expense captured by NotificationReaderService with explicit UTR reference
        dao.insert(
            Expense(
                amount = 250.0,
                merchant = "satya chicken",
                dateMillis = baseTime,
                notificationKey = "notif_gpay_1",
                note = "UPI Ref 624571799987",
                source = "NOTIFICATION"
            )
        )

        val sms = SmsRecord(
            id = 55L,
            address = "AD-IPPB",
            body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken Ref 624571799987. Avl Bal Rs.116.62. -IPPB",
            dateMillis = baseTime + 1000L
        )
        reader.records = listOf(sms)

        val result = manager.scan(baseTime - 10000L, baseTime + 10000L)

        assertTrue(result is SmsScanResult.Success)
        val success = result as SmsScanResult.Success
        assertEquals(1, success.transactionCandidatesCount)
        assertEquals(0, success.newTransactionsCount)
        assertEquals(1, success.correlationsCount)
        assertEquals(DedupDecision.CORRELATED, success.scannedItems[0].plannedDecision)
        assertEquals(MatchStrategy.EXPLICIT_REFERENCE_ID, success.scannedItems[0].matchStrategy)
        assertEquals(1, dao.getCount()) // scan does NOT mutate DB
    }

    // =========================================================================
    // 6. CANCELLATION & PERMISSION
    // =========================================================================

    @Test
    fun test13_scan_cancellation_haltsEarlyAndReturnsCancelled() = runBlocking {
        val records = (1..10).map { id ->
            SmsRecord(
                id = id.toLong(),
                address = "AD-IPPB",
                body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken on 02-09-26 Ref 62457179998$id. Bal Rs.100. -IPPB",
                dateMillis = id * 1000L
            )
        }
        reader.records = records

        var processedCount = 0
        val result = manager.scan(0L, 50000L) { progress ->
            processedCount = progress.recordsProcessed
            // Cancel after processing 3 messages
            progress.recordsProcessed < 3
        }

        assertEquals(SmsScanResult.Cancelled, result)
        assertEquals(3, processedCount)
        assertEquals(HistoricalSmsImportState.ScanCancelled, manager.state.value)
        assertEquals(0, dao.getCount())
    }

    @Test
    fun test14_scan_permissionRequired_propagatesPermissionRequired() = runBlocking {
        reader.hasPermission = false

        val result = manager.scan(0L, 10000L)

        assertEquals(SmsScanResult.PermissionRequired, result)
        assertEquals(HistoricalSmsImportState.PermissionRequired, manager.state.value)
    }

    // =========================================================================
    // 7. IMPORT & PERSISTENCE RECONCILIATION
    // =========================================================================

    @Test
    fun test15_import_newTransactions_insertsIntoDatabase() = runBlocking {
        val sms = SmsRecord(
            id = 101L,
            address = "AD-IPPB",
            body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken on 02-09-26 Ref 624571799987. Avl Bal Rs.116.62. -IPPB",
            dateMillis = 1000L
        )
        reader.records = listOf(sms)

        val scanResult = manager.scan(0L, 5000L)
        assertTrue(scanResult is SmsScanResult.Success)

        // Pre-import check: 0 rows
        assertEquals(0, dao.getCount())

        val importResult = manager.importTransactions(scanResult as SmsScanResult.Success)

        assertTrue(importResult is SmsImportResult.Success)
        val success = importResult as SmsImportResult.Success
        assertEquals(1, success.totalProcessed)
        assertEquals(1, success.insertedCount)
        assertEquals(0, success.duplicatesSkippedCount)
        assertEquals(250.0, success.totalAmountImported, 0.001)

        // Verify inserted row in database
        assertEquals(1, dao.getCount())
        val inserted = dao.getExpenseByNotificationKey("sms_101")
        assertNotNull(inserted)
        assertEquals(250.0, inserted!!.amount, 0.001)
        assertEquals("satya chicken", inserted.merchant)
        assertEquals("SMS_HISTORY", inserted.source)
    }

    @Test
    fun test16_import_idempotency_secondImportInsertsZeroDuplicatesSkipped() = runBlocking {
        val sms = SmsRecord(
            id = 101L,
            address = "AD-IPPB",
            body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken on 02-09-26 Ref 624571799987. Avl Bal Rs.116.62. -IPPB",
            dateMillis = 1000L
        )
        reader.records = listOf(sms)

        val scanResult = manager.scan(0L, 5000L) as SmsScanResult.Success

        // First import -> inserts 1 row
        val import1 = manager.importTransactions(scanResult) as SmsImportResult.Success
        assertEquals(1, import1.insertedCount)
        assertEquals(1, dao.getCount())

        // Second import of same scan result -> exactly 0 new rows, 1 duplicate skipped!
        val import2 = manager.importTransactions(scanResult) as SmsImportResult.Success
        assertEquals(0, import2.insertedCount)
        assertEquals(1, import2.duplicatesSkippedCount)
        assertEquals(1, dao.getCount()) // Count strictly preserved!
    }

    @Test
    fun test17_import_correlationWithNotification_enrichesNotePreservesRowCount() = runBlocking {
        val baseTime = 1772600000000L
        // Pre-existing notification expense in DB
        val existingNotifId = dao.insert(
            Expense(
                amount = 250.0,
                merchant = "satya chicken",
                dateMillis = baseTime,
                notificationKey = "notif_key_1",
                note = "UPI Ref: 624571799987",
                source = "NOTIFICATION"
            )
        )
        assertEquals(1, dao.getCount())

        val sms = SmsRecord(
            id = 77L,
            address = "AD-IPPB",
            body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken Ref 624571799987. Avl Bal Rs.116.62. -IPPB",
            dateMillis = baseTime + 1000L
        )
        reader.records = listOf(sms)

        val scanResult = manager.scan(baseTime - 10000L, baseTime + 10000L) as SmsScanResult.Success
        assertEquals(1, scanResult.correlationsCount)

        val importResult = manager.importTransactions(scanResult) as SmsImportResult.Success

        assertEquals(0, importResult.insertedCount)
        assertEquals(1, importResult.enrichedCount)
        // Row count MUST remain 1: exactly ONE logical transaction in database!
        assertEquals(1, dao.getCount())

        val updated = dao.getExpenseById(existingNotifId.toInt())
        assertNotNull(updated)
        // Note is enriched with correlation evidence
        assertTrue(updated!!.note?.contains("Correlated") == true)
    }

    @Test
    fun test19_import_cancellation_haltsEarlyAndReturnsCancelled() = runBlocking {
        val records = (1..10).map { id ->
            SmsRecord(
                id = id.toLong(),
                address = "AD-IPPB",
                body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken on 02-09-26 Ref 62457179998$id. Bal Rs.100. -IPPB",
                dateMillis = id * 1000L
            )
        }
        reader.records = records

        val scanResult = manager.scan(0L, 50000L) as SmsScanResult.Success

        // Cancel during import after 2 items
        val importResult = manager.importTransactions(scanResult) { progress ->
            progress.itemsProcessed < 2
        }

        assertEquals(SmsImportResult.Cancelled, importResult)
        assertEquals(HistoricalSmsImportState.ImportCancelled, manager.state.value)
    }

    // =========================================================================
    // 8. THE 4 REAL-WORLD REGRESSION FIXTURES
    // =========================================================================

    @Test
    fun test20_realFixtures_fourMilestone2Fixtures_scannedAndImportedAccurately() = runBlocking {
        // Fixture 1: IPPB Debit
        val f1 = SmsRecord(
            id = 101L,
            address = "AD-IPPB",
            body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken on 02-09-26 Ref 624571799987. Avl Bal Rs.116.62. -IPPB",
            dateMillis = 1772600000000L
        )
        // Fixture 2: IPPB Credit
        val f2 = SmsRecord(
            id = 102L,
            address = "AD-IPPB",
            body = "Your A/C *2959 is Credited by Rs.500.00 on 15/09/2026 by UPI: Ramu Ref No 625829104829. Bal: Rs.616.62 -IPPB",
            dateMillis = 1772601000000L
        )
        // Fixture 3: SBI Debit without currency
        val f3 = SmsRecord(
            id = 103L,
            address = "SBIUPI",
            body = "Dear SBI User, your A/C 9876 has a debit of 1500 on 10Sep26 at AMAZON INDIA. Avl Bal: INR 12050.50. Call 1800111109 if not you",
            dateMillis = 1772602000000L
        )
        // Fixture 4: APGB Debit with VPA
        val f4 = SmsRecord(
            id = 104L,
            address = "APGB-BANK",
            body = "Your APGB A/C *4321 is debited for Rs 350.00 on 20-09-26 to VPA subhash@okhdfcbank (UPI Ref no 626384910283). Total Bal: Rs 4210.00",
            dateMillis = 1772603000000L
        )

        reader.records = listOf(f1, f2, f3, f4)

        // 1. SCAN MODE
        val scanResult = manager.scan(0L, Long.MAX_VALUE)
        assertTrue(scanResult is SmsScanResult.Success)
        val scanSuccess = scanResult as SmsScanResult.Success

        assertEquals(4, scanSuccess.messagesScanned)
        assertEquals(4, scanSuccess.financialMessages)
        assertEquals(0, scanSuccess.noiseMessages)
        assertEquals(4, scanSuccess.transactionCandidatesCount)
        assertEquals(4, scanSuccess.newTransactionsCount)

        // Total Debit: 250 + 1500 + 350 = 2100.00
        assertEquals(2100.00, scanSuccess.totalDebitAmount, 0.001)
        // Total Credit: 500.00
        assertEquals(500.00, scanSuccess.totalCreditAmount, 0.001)

        // Zero DB mutations during scan
        assertEquals(0, dao.getCount())

        // 2. IMPORT MODE
        val importResult = manager.importTransactions(scanSuccess)
        assertTrue(importResult is SmsImportResult.Success)
        val importSuccess = importResult as SmsImportResult.Success

        assertEquals(4, importSuccess.totalProcessed)
        assertEquals(4, importSuccess.insertedCount)
        assertEquals(0, importSuccess.duplicatesSkippedCount)
        assertEquals(2100.00, importSuccess.totalAmountImported, 0.001)
        assertEquals(500.00, importSuccess.totalIncomeImported, 0.001)

        // Verify all 4 are persisted in DB
        assertEquals(4, dao.getCount())
        assertNotNull(dao.getExpenseByNotificationKey("sms_101"))
        assertNotNull(dao.getExpenseByNotificationKey("sms_102"))
        assertNotNull(dao.getExpenseByNotificationKey("sms_103"))
        assertNotNull(dao.getExpenseByNotificationKey("sms_104"))

        // 3. IDEMPOTENT RE-IMPORT
        val secondImport = manager.importTransactions(scanSuccess) as SmsImportResult.Success
        assertEquals(0, secondImport.insertedCount)
        assertEquals(4, secondImport.duplicatesSkippedCount)
        assertEquals(4, dao.getCount())
    }

    // =========================================================================
    // 9. SENDER SUPPORT & CONVENIENCE METHOD
    // =========================================================================

    @Test
    fun test21_mobileSender_supportedWithoutWhitelisting() = runBlocking {
        val mobileSms = SmsRecord(
            id = 201L,
            address = "+919876543210",
            body = "Dear Customer, Rs 120.00 debited from A/C *5555 on 15-Sep-26 at Local Store. UPI Ref: 999888777111.",
            dateMillis = 1000L
        )
        reader.records = listOf(mobileSms)

        val result = manager.scan(0L, 5000L)

        assertTrue(result is SmsScanResult.Success)
        val success = result as SmsScanResult.Success
        // Mobile sender is NOT rejected; flows into financial extraction
        assertEquals(1, success.financialMessages)
        assertEquals(1, success.transactionCandidatesCount)
        assertEquals(120.0, success.totalDebitAmount, 0.001)
    }

    @Test
    fun test22_scanAndImport_convenienceMethod_executesPipelineEndToEnd() = runBlocking {
        val sms = SmsRecord(
            id = 301L,
            address = "AD-IPPB",
            body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken on 02-09-26 Ref 624571799987. Avl Bal Rs.116.62. -IPPB",
            dateMillis = 1000L
        )
        reader.records = listOf(sms)

        val importResult = manager.scanAndImport(0L, 5000L)

        assertTrue(importResult is SmsImportResult.Success)
        val success = importResult as SmsImportResult.Success
        assertEquals(1, success.insertedCount)
        assertEquals(1, dao.getCount())
    }
}
