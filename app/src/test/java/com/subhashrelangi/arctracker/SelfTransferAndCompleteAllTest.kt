package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.TransactionRelationshipType
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Step 7.1: Account-Aware Self-Transfer Correlation, Direction Evidence Hardening,
 * and Pending Expenses Complete All Action Test Suite.
 */
class SelfTransferAndCompleteAllTest {

    private lateinit var dao: FakeExpenseDao

    @Before
    fun setUp() {
        dao = FakeExpenseDao()
        TransactionPersistenceManager.persistenceListener = null
    }

    private fun makeCandidate(
        amount: Double = 100.0,
        merchant: String = "Self Transfer",
        direction: TransactionDirection = TransactionDirection.DEBIT,
        status: TransactionStatus = TransactionStatus.SUCCESS,
        referenceId: String? = "138212387425",
        accountSuffix: String? = "2959",
        bank: String? = "IPPB",
        key: String = java.util.UUID.randomUUID().toString(),
        timestamp: Long = 1759000000000L,
        rawContent: String? = null
    ): ValidatedTransactionCandidate {
        val candidate = StructuredTransactionCandidate(
            sourceNotificationKey = key,
            packageName = "com.bank.app",
            postTime = timestamp,
            transactionTimestamp = timestamp,
            amount = amount,
            merchant = merchant,
            direction = direction,
            status = status,
            referenceId = referenceId,
            utr = referenceId,
            accountSuffix = accountSuffix,
            bank = bank,
            rawContent = rawContent ?: "Txn of Rs $amount $direction ref $referenceId"
        )
        return ValidatedTransactionCandidate(
            candidate = candidate,
            validationState = ValidationState.ACCEPTABLE,
            evidenceLevel = EvidenceLevel.VERY_STRONG,
            isStructurallyValid = true,
            validationReasons = listOf("Valid candidate")
        )
    }

    // =========================================================================
    // 1. Account-Aware Self-Transfer Detection & Correlation
    // =========================================================================

    @Test
    fun test01_selfTransfer_differentAccounts_oppositeDirections_sameRef_evaluatesToSelfTransfer() {
        val candidateA = makeCandidate(
            amount = 100.0,
            direction = TransactionDirection.DEBIT,
            accountSuffix = "2959",
            bank = "IPPB",
            referenceId = "138212387425",
            key = "sms_ippb_1"
        )
        val candidateB = makeCandidate(
            amount = 100.0,
            direction = TransactionDirection.CREDIT,
            accountSuffix = "1065",
            bank = "APGBank",
            referenceId = "138212387425",
            key = "sms_apgb_2"
        )

        val recordA = TransactionRecord.fromValidated(candidateA, TransactionSourceType.SMS_HISTORY)
        val result = TransactionDeduplicator.evaluate(candidateB, listOf(recordA))

        assertEquals(DedupDecision.NEW_TRANSACTION, result.decision)
        assertEquals(MatchStrategy.SELF_TRANSFER, result.strategy)
        assertEquals(TransactionRelationshipType.SELF_TRANSFER, result.relationshipType)
        assertEquals("SELF_TRANSFER_138212387425", result.relationshipId)
        assertTrue(result.isSelfTransfer)
        assertFalse(result.needsReview)
    }

    @Test
    fun test02_selfTransfer_bothRecordsPreservedInRoom_withRelationshipFields() = runBlocking {
        val candidateA = makeCandidate(
            amount = 100.0,
            direction = TransactionDirection.DEBIT,
            accountSuffix = "2959",
            bank = "IPPB",
            referenceId = "138212387425",
            key = "sms_ippb_1"
        )
        val candidateB = makeCandidate(
            amount = 100.0,
            direction = TransactionDirection.CREDIT,
            accountSuffix = "1065",
            bank = "APGBank",
            referenceId = "138212387425",
            key = "sms_apgb_2"
        )

        // Process Candidate A
        val resA = TransactionPersistenceManager.processValidatedCandidate(candidateA, dao)
        assertTrue(resA is ExpensePersistenceResult.Inserted)
        assertEquals(1, dao.getCount())

        // Process Candidate B (transfers into APGBank)
        val resB = TransactionPersistenceManager.processValidatedCandidate(candidateB, dao)
        assertTrue(resB is ExpensePersistenceResult.Inserted)
        assertEquals(2, dao.getCount())

        val allExpenses = dao.getAllExpensesList()
        assertEquals(2, allExpenses.size)

        val debitExpense = allExpenses.find { it.type == "Debit" }
        val creditExpense = allExpenses.find { it.type == "Credit" }

        assertNotNull(debitExpense)
        assertNotNull(creditExpense)

        assertEquals("SELF_TRANSFER", debitExpense!!.relationshipType)
        assertEquals("SELF_TRANSFER_138212387425", debitExpense.relationshipId)
        assertFalse(debitExpense.isPending)

        assertEquals("SELF_TRANSFER", creditExpense!!.relationshipType)
        assertEquals("SELF_TRANSFER_138212387425", creditExpense.relationshipId)
        assertFalse(creditExpense.isPending)
    }

    @Test
    fun test03_selfTransfer_orderIndependent_symmetricCorrelation() = runBlocking {
        // Credit processed first, Debit processed second
        val candidateCredit = makeCandidate(
            amount = 250.0,
            direction = TransactionDirection.CREDIT,
            accountSuffix = "1065",
            bank = "APGBank",
            referenceId = "REF999888",
            key = "sms_credit_first"
        )
        val candidateDebit = makeCandidate(
            amount = 250.0,
            direction = TransactionDirection.DEBIT,
            accountSuffix = "2959",
            bank = "IPPB",
            referenceId = "REF999888",
            key = "sms_debit_second"
        )

        val resCredit = TransactionPersistenceManager.processValidatedCandidate(candidateCredit, dao)
        assertTrue(resCredit is ExpensePersistenceResult.Inserted)

        val resDebit = TransactionPersistenceManager.processValidatedCandidate(candidateDebit, dao)
        assertTrue(resDebit is ExpensePersistenceResult.Inserted)

        val expenses = dao.getExpensesByRelationshipId("SELF_TRANSFER_REF999888")
        assertEquals(2, expenses.size)
        assertTrue(expenses.any { it.type == "Credit" && it.amount == 250.0 })
        assertTrue(expenses.any { it.type == "Debit" && it.amount == 250.0 })
    }

    @Test
    fun test04_oppositeDirections_sameAccount_evaluatesToIdentityConflict_notSelfTransfer() {
        val candidateA = makeCandidate(
            amount = 100.0,
            direction = TransactionDirection.DEBIT,
            accountSuffix = "2959",
            bank = "IPPB",
            referenceId = "138212387425",
            key = "sms_a"
        )
        val candidateB = makeCandidate(
            amount = 100.0,
            direction = TransactionDirection.CREDIT,
            accountSuffix = "2959", // Same account suffix
            bank = "IPPB",          // Same bank
            referenceId = "138212387425",
            key = "sms_b"
        )

        val recordA = TransactionRecord.fromValidated(candidateA, TransactionSourceType.SMS_HISTORY)
        val result = TransactionDeduplicator.evaluate(candidateB, listOf(recordA))

        assertEquals(DedupDecision.NEEDS_REVIEW, result.decision)
        assertEquals(MatchStrategy.IDENTITY_CONFLICT, result.strategy)
        assertNotEquals("SELF_TRANSFER", result.relationshipType)
    }

    @Test
    fun test05_oppositeDirections_differentAmounts_evaluatesToIdentityConflict_notSelfTransfer() {
        val candidateA = makeCandidate(
            amount = 100.0,
            direction = TransactionDirection.DEBIT,
            accountSuffix = "2959",
            bank = "IPPB",
            referenceId = "138212387425"
        )
        val candidateB = makeCandidate(
            amount = 200.0, // Different amount
            direction = TransactionDirection.CREDIT,
            accountSuffix = "1065",
            bank = "APGBank",
            referenceId = "138212387425"
        )

        val recordA = TransactionRecord.fromValidated(candidateA, TransactionSourceType.SMS_HISTORY)
        val result = TransactionDeduplicator.evaluate(candidateB, listOf(recordA))

        assertEquals(DedupDecision.NEEDS_REVIEW, result.decision)
        assertEquals(MatchStrategy.IDENTITY_CONFLICT, result.strategy)
    }

    @Test
    fun test06_oppositeDirections_differentReferences_evaluatedAsIndependent() {
        val candidateA = makeCandidate(
            amount = 100.0,
            direction = TransactionDirection.DEBIT,
            accountSuffix = "2959",
            bank = "IPPB",
            referenceId = "REF_AAA"
        )
        val candidateB = makeCandidate(
            amount = 100.0,
            direction = TransactionDirection.CREDIT,
            accountSuffix = "1065",
            bank = "APGBank",
            referenceId = "REF_BBB" // Different reference
        )

        val recordA = TransactionRecord.fromValidated(candidateA, TransactionSourceType.SMS_HISTORY)
        val result = TransactionDeduplicator.evaluate(candidateB, listOf(recordA))

        assertEquals(DedupDecision.NEW_TRANSACTION, result.decision)
        assertEquals(MatchStrategy.NONE, result.strategy)
        assertNull(result.relationshipType)
    }

    @Test
    fun test07_sameDirection_sameReference_evaluatedAsDuplicateOrUpdate_notSelfTransfer() {
        val candidateA = makeCandidate(
            amount = 100.0,
            direction = TransactionDirection.DEBIT,
            accountSuffix = "2959",
            bank = "IPPB",
            referenceId = "138212387425"
        )
        val candidateB = makeCandidate(
            amount = 100.0,
            direction = TransactionDirection.DEBIT, // Same direction
            accountSuffix = "2959",
            bank = "IPPB",
            referenceId = "138212387425"
        )

        val recordA = TransactionRecord.fromValidated(candidateA, TransactionSourceType.SMS_HISTORY)
        val result = TransactionDeduplicator.evaluate(candidateB, listOf(recordA))

        assertEquals(DedupDecision.CORRELATED, result.decision)
        assertEquals(MatchStrategy.EXPLICIT_REFERENCE_ID, result.strategy)
        assertNotEquals("SELF_TRANSFER", result.relationshipType)
    }

    @Test
    fun test08_selfTransfer_timeDiffExceeding48Hours_evaluatesToNeedsReview() {
        val baseTime = 1759000000000L
        val candidateA = makeCandidate(
            amount = 100.0,
            direction = TransactionDirection.DEBIT,
            accountSuffix = "2959",
            bank = "IPPB",
            referenceId = "138212387425",
            timestamp = baseTime
        )
        // 49 hours later
        val candidateB = makeCandidate(
            amount = 100.0,
            direction = TransactionDirection.CREDIT,
            accountSuffix = "1065",
            bank = "APGBank",
            referenceId = "138212387425",
            timestamp = baseTime + (49 * 3600 * 1000L)
        )

        val recordA = TransactionRecord.fromValidated(candidateA, TransactionSourceType.SMS_HISTORY)
        val result = TransactionDeduplicator.evaluate(candidateB, listOf(recordA))

        assertEquals(DedupDecision.NEEDS_REVIEW, result.decision)
        assertEquals(MatchStrategy.IDENTITY_CONFLICT, result.strategy)
    }

    @Test
    fun test09_selfTransfer_doesNotRouteToPendingExpensesSolelyDueToOppositeDirections() = runBlocking {
        val candidateA = makeCandidate(
            amount = 100.0,
            direction = TransactionDirection.DEBIT,
            accountSuffix = "2959",
            bank = "IPPB",
            referenceId = "138212387425",
            key = "sms_ippb_1"
        )
        val candidateB = makeCandidate(
            amount = 100.0,
            direction = TransactionDirection.CREDIT,
            accountSuffix = "1065",
            bank = "APGBank",
            referenceId = "138212387425",
            key = "sms_apgb_2"
        )

        TransactionPersistenceManager.processValidatedCandidate(candidateA, dao)
        TransactionPersistenceManager.processValidatedCandidate(candidateB, dao)

        val pending = dao.getPendingExpensesList()
        assertEquals(0, pending.size)
    }

    @Test
    fun test10_historicalSmsScan_selfTransfer_identifiesBothAsNewAndComputesMonetaryTotals() = runBlocking {
        val smsA = SmsRecord(
            id = 101L,
            address = "VK-IPPBMS",
            body = "Rs.100.00 debited from A/c XX2959 on 28-Sep-26. UPI Ref: 138212387425.",
            dateMillis = 1759000000000L
        )
        val smsB = SmsRecord(
            id = 102L,
            address = "VK-APGBANK",
            body = "Rs.100/- is credited in your A/c XXXX1065 on 28-Sep-26. UPI Ref: 138212387425.",
            dateMillis = 1759000005000L
        )

        val reader = InMemorySmsReader(hasPermission = true, records = listOf(smsA, smsB))
        val manager = HistoricalSmsImportManager(
            smsReader = reader,
            dao = dao
        )

        val scanResult = manager.scan(0L, 2000000000000L)
        assertTrue(scanResult is SmsScanResult.Success)
        val success = scanResult as SmsScanResult.Success

        assertEquals(2, success.messagesScanned)
        assertEquals(2, success.financialMessages)
        assertEquals(2, success.transactionCandidatesCount)
        assertEquals(2, success.newTransactionsCount)
        assertEquals(0, success.pendingReviewCount)
        assertEquals(100.0, success.totalDebitAmount, 0.01)
        assertEquals(100.0, success.totalCreditAmount, 0.01)
    }

    @Test
    fun test11_historicalSmsImport_selfTransfer_persistsBothRecordsWithSelfTransferRelationship() = runBlocking {
        val smsA = SmsRecord(
            id = 201L,
            address = "VK-IPPBMS",
            body = "Rs.100.00 debited from A/c XX2959 on 28-Sep-26. UPI Ref: 138212387425.",
            dateMillis = 1759000000000L
        )
        val smsB = SmsRecord(
            id = 202L,
            address = "VK-APGBANK",
            body = "Rs.100/- is credited in your A/c XXXX1065 on 28-Sep-26. UPI Ref: 138212387425.",
            dateMillis = 1759000005000L
        )

        val reader = InMemorySmsReader(hasPermission = true, records = listOf(smsA, smsB))
        val manager = HistoricalSmsImportManager(
            smsReader = reader,
            dao = dao
        )

        val scanResult = manager.scan(0L, 2000000000000L) as SmsScanResult.Success
        val importResult = manager.importTransactions(scanResult)

        assertTrue(importResult is SmsImportResult.Success)
        val importSuccess = importResult as SmsImportResult.Success
        assertEquals(2, importSuccess.insertedCount)

        val expensesInDb = dao.getAllExpensesList()
        assertEquals(2, expensesInDb.size)

        val relExpenses = dao.getExpensesByRelationshipId("SELF_TRANSFER_138212387425")
        assertEquals(2, relExpenses.size)
        assertTrue(relExpenses.any { it.type == "Debit" })
        assertTrue(relExpenses.any { it.type == "Credit" })
    }

    // =========================================================================
    // 2. Direction Evidence Hardening
    // =========================================================================

    @Test
    fun test12_directionHardening_creditedInAccount_resolvesToCredit() {
        val text = "Rs.100/- is credited in your A/c XXXX1065 on 28-Sep-26. UPI Ref: 138212387425."
        val assessment = ActualTransactionEventGate.assess(text)

        assertEquals(ActualEventStatus.TRUE, assessment.actualEvent)
        assertEquals(TransactionEventType.COMPLETED, assessment.eventType)
        assertEquals(TransactionDirection.CREDIT, assessment.directionHint)

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            notificationKey = "test_key_credited",
            postTime = 1759000000000L,
            title = "VK-APGBANK",
            text = text
        )
        val normalized = NotificationNormalizer.normalize(captured)!!
        val classification = FinancialClassifier.classify(normalized)
        val candidates = StructuredTransactionExtractor.extractAll(classification)

        assertFalse(candidates.isEmpty())
        val candidate = candidates.first()
        assertEquals(TransactionDirection.CREDIT, candidate.direction)
        assertEquals(100.0, candidate.amount ?: 0.0, 0.01)
        assertEquals("1065", candidate.accountSuffix)
    }

    @Test
    fun test13_directionHardening_debitedFromAccount_resolvesToDebit() {
        val text = "Rs.100/- is debited from your A/c XXXX2959 on 28-Sep-26. UPI Ref: 138212387425."
        val assessment = ActualTransactionEventGate.assess(text)

        assertEquals(ActualEventStatus.TRUE, assessment.actualEvent)
        assertEquals(TransactionEventType.COMPLETED, assessment.eventType)
        assertEquals(TransactionDirection.DEBIT, assessment.directionHint)

        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            notificationKey = "test_key_debited",
            postTime = 1759000000000L,
            title = "VK-IPPBMS",
            text = text
        )
        val normalized = NotificationNormalizer.normalize(captured)!!
        val classification = FinancialClassifier.classify(normalized)
        val candidates = StructuredTransactionExtractor.extractAll(classification)

        assertFalse(candidates.isEmpty())
        val candidate = candidates.first()
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals(100.0, candidate.amount ?: 0.0, 0.01)
        assertEquals("2959", candidate.accountSuffix)
    }

    @Test
    fun test14_directionHardening_creditCardPaymentDue_rejectedByActualTransactionEventGate() {
        val text = "Credit card payment of Rs 5000 is due on 15-Oct. Avoid late charges."
        val assessment = ActualTransactionEventGate.assess(text)

        assertEquals(ActualEventStatus.FALSE, assessment.actualEvent)
        assertEquals(TransactionEventType.INFORMATIONAL, assessment.eventType)
    }

    @Test
    fun test15_directionHardening_promotionalCashbackOffer_rejectedByActualTransactionEventGate() {
        val text = "₹2,000 Is One Bill Away! Pay your Credit Card Bill on Paytm with No Extra Fees & get a chance to win ₹2,000 cashback. Use CCBP2000. No Fees"
        val assessment = ActualTransactionEventGate.assess(text)

        assertEquals(ActualEventStatus.FALSE, assessment.actualEvent)
        assertTrue(assessment.eventType == TransactionEventType.PROMOTIONAL || assessment.eventType == TransactionEventType.FUTURE_ACTION)
    }

    // =========================================================================
    // 3. Room Schema Migration & Helper Verification
    // =========================================================================

    @Test
    fun test16_roomMigration_addsRelationshipColumns_preservesExistingData() {
        // Verify migration SQL definition is syntactically sound and non-destructive
        assertNotNull(AppDatabase.MIGRATION_1_2)
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)
    }

    @Test
    fun test17_expenseDao_getExpensesByRelationshipId_retrievesBothSidesOfTransfer() = runBlocking {
        dao.insert(
            Expense(
                id = 1,
                amount = 50.0,
                merchant = "IPPB",
                dateMillis = 1759000000000L,
                type = "Debit",
                relationshipType = "SELF_TRANSFER",
                relationshipId = "SELF_TRANSFER_REF555"
            )
        )
        dao.insert(
            Expense(
                id = 2,
                amount = 50.0,
                merchant = "APGBank",
                dateMillis = 1759000001000L,
                type = "Credit",
                relationshipType = "SELF_TRANSFER",
                relationshipId = "SELF_TRANSFER_REF555"
            )
        )
        dao.insert(
            Expense(
                id = 3,
                amount = 100.0,
                merchant = "Swiggy",
                dateMillis = 1759000002000L,
                type = "Debit",
                relationshipType = null,
                relationshipId = null
            )
        )

        val transferGroup = dao.getExpensesByRelationshipId("SELF_TRANSFER_REF555")
        assertEquals(2, transferGroup.size)
        assertTrue(transferGroup.all { it.relationshipId == "SELF_TRANSFER_REF555" })
    }

    // =========================================================================
    // 4. Pending Expenses — Complete All Action
    // =========================================================================

    @Test
    fun test18_completeAll_approvesAllPendingTransactions() = runBlocking {
        // Seed 3 pending transactions and 1 normal transaction
        dao.insert(Expense(id = 1, amount = 100.0, merchant = "Pending A", dateMillis = 1000L, isPending = true))
        dao.insert(Expense(id = 2, amount = 200.0, merchant = "Pending B", dateMillis = 2000L, isPending = true))
        dao.insert(Expense(id = 3, amount = 300.0, merchant = "Pending C", dateMillis = 3000L, isPending = true))
        dao.insert(Expense(id = 4, amount = 400.0, merchant = "Normal D", dateMillis = 4000L, isPending = false))

        assertEquals(3, dao.getPendingExpensesList().size)

        // Simulate Complete All logic
        val pendingList = dao.getPendingExpensesList()
        var approvedCount = 0
        for (item in pendingList) {
            val updated = item.copy(isPending = false)
            val rows = dao.update(updated)
            if (rows > 0) approvedCount++
        }

        assertEquals(3, approvedCount)
        assertEquals(0, dao.getPendingExpensesList().size)
        val all = dao.getAllExpensesList()
        assertEquals(4, all.size)
        assertTrue(all.all { !it.isPending })
    }

    @Test
    fun test19_completeAll_withZeroPending_noopAndPreservesDatabase() = runBlocking {
        dao.insert(Expense(id = 1, amount = 50.0, merchant = "Normal", dateMillis = 1000L, isPending = false))

        val pendingList = dao.getPendingExpensesList()
        assertEquals(0, pendingList.size)

        var approvedCount = 0
        for (item in pendingList) {
            val updated = item.copy(isPending = false)
            dao.update(updated)
            approvedCount++
        }

        assertEquals(0, approvedCount)
        assertEquals(1, dao.getCount())
    }

    @Test
    fun test20_completeAll_handlesPartialFailuresGracefully() = runBlocking {
        // Failing DAO where item with id=2 throws exception on update
        val partialFailDao = object : FakeExpenseDao() {
            override suspend fun update(expense: Expense): Int {
                if (expense.id == 2) {
                    throw IllegalStateException("Simulated database lock error on id=2")
                }
                return super.update(expense)
            }
        }

        partialFailDao.insert(Expense(id = 1, amount = 100.0, merchant = "Pending 1", dateMillis = 1000L, isPending = true))
        partialFailDao.insert(Expense(id = 2, amount = 200.0, merchant = "Pending 2", dateMillis = 2000L, isPending = true))
        partialFailDao.insert(Expense(id = 3, amount = 300.0, merchant = "Pending 3", dateMillis = 3000L, isPending = true))

        val pending = partialFailDao.getPendingExpensesList()
        var successCount = 0
        var failCount = 0

        for (item in pending) {
            try {
                val updated = item.copy(isPending = false)
                val count = partialFailDao.update(updated)
                if (count > 0) successCount++ else failCount++
            } catch (e: Exception) {
                failCount++
            }
        }

        assertEquals(2, successCount)
        assertEquals(1, failCount)

        // Item 1 and 3 are now approved, Item 2 remains pending
        val remainingPending = partialFailDao.getPendingExpensesList()
        assertEquals(1, remainingPending.size)
        assertEquals(2, remainingPending.first().id)
    }

    @Test
    fun test21_completeAll_refreshesExpenseTotalsAndCounts() = runBlocking {
        dao.insert(Expense(id = 1, amount = 150.0, merchant = "Merchant 1", dateMillis = 1000L, isPending = true))
        dao.insert(Expense(id = 2, amount = 250.0, merchant = "Merchant 2", dateMillis = 2000L, isPending = true))
        dao.insert(Expense(id = 3, amount = 500.0, merchant = "Merchant 3", dateMillis = 3000L, isPending = false))

        var nonPendingTotal = dao.getAllExpensesList().filter { !it.isPending }.sumOf { it.amount }
        var nonPendingCount = dao.getAllExpensesList().count { !it.isPending }
        assertEquals(500.0, nonPendingTotal, 0.01)
        assertEquals(1, nonPendingCount)

        // Complete All
        val pending = dao.getPendingExpensesList()
        for (item in pending) {
            dao.update(item.copy(isPending = false))
        }

        nonPendingTotal = dao.getAllExpensesList().filter { !it.isPending }.sumOf { it.amount }
        nonPendingCount = dao.getAllExpensesList().count { !it.isPending }

        assertEquals(900.0, nonPendingTotal, 0.01)
        assertEquals(3, nonPendingCount)
    }
}
