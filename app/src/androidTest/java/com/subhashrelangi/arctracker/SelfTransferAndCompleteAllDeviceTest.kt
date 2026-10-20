package com.subhashrelangi.arctracker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 7.1: Physical Device Integration Tests for Self-Transfer Correlation,
 * Direction Hardening, and Complete All Action executing on connected Samsung Galaxy A34 5G.
 */
@RunWith(AndroidJUnit4::class)
class SelfTransferAndCompleteAllDeviceTest {

    private lateinit var context: Context
    private lateinit var testDb: AppDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testDb = AppDatabase.createInMemoryDatabase(context)
        NotificationPermissionHelper.permissionOverrideForTesting = true
        NotificationReaderService.clearActiveNotifications()
    }

    @After
    fun tearDown() {
        testDb.close()
        NotificationPermissionHelper.permissionOverrideForTesting = null
        NotificationReaderService.clearActiveNotifications()
    }

    @Test
    fun testDevice_selfTransfer_bothRecordsPreservedInRoom_withRelationshipFields() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        val debitCandidate = StructuredTransactionCandidate(
            sourceNotificationKey = "device_sms_ippb_debit",
            packageName = "com.google.android.apps.messaging",
            postTime = System.currentTimeMillis(),
            transactionTimestamp = System.currentTimeMillis(),
            amount = 100.0,
            merchant = "IPPB Transfer",
            direction = TransactionDirection.DEBIT,
            status = TransactionStatus.SUCCESS,
            referenceId = "138212387425",
            utr = "138212387425",
            accountSuffix = "2959",
            bank = "IPPB",
            rawContent = "Rs.100.00 debited from A/c XX2959. UPI Ref: 138212387425."
        )
        val validDebit = ValidatedTransactionCandidate(
            candidate = debitCandidate,
            validationState = ValidationState.ACCEPTABLE,
            evidenceLevel = EvidenceLevel.VERY_STRONG,
            isStructurallyValid = true,
            validationReasons = listOf("Valid debit")
        )

        val creditCandidate = StructuredTransactionCandidate(
            sourceNotificationKey = "device_sms_apgb_credit",
            packageName = "com.google.android.apps.messaging",
            postTime = System.currentTimeMillis() + 2000L,
            transactionTimestamp = System.currentTimeMillis() + 2000L,
            amount = 100.0,
            merchant = "APGBank Transfer",
            direction = TransactionDirection.CREDIT,
            status = TransactionStatus.SUCCESS,
            referenceId = "138212387425",
            utr = "138212387425",
            accountSuffix = "1065",
            bank = "APGBank",
            rawContent = "Rs.100/- is credited in your A/c XXXX1065. UPI Ref: 138212387425."
        )
        val validCredit = ValidatedTransactionCandidate(
            candidate = creditCandidate,
            validationState = ValidationState.ACCEPTABLE,
            evidenceLevel = EvidenceLevel.VERY_STRONG,
            isStructurallyValid = true,
            validationReasons = listOf("Valid credit")
        )

        // Process Debit
        val resA = TransactionPersistenceManager.processValidatedCandidate(validDebit, dao)
        assertTrue(resA is ExpensePersistenceResult.Inserted)
        assertEquals(1, dao.getCount())

        // Process Credit (Counterpart)
        val resB = TransactionPersistenceManager.processValidatedCandidate(validCredit, dao)
        assertTrue(resB is ExpensePersistenceResult.Inserted)
        assertEquals(2, dao.getCount())

        val allExpenses = dao.getAllExpensesList()
        assertEquals(2, allExpenses.size)

        val debitExp = allExpenses.find { it.type == "Debit" }
        val creditExp = allExpenses.find { it.type == "Credit" }

        assertNotNull(debitExp)
        assertNotNull(creditExp)

        assertEquals("SELF_TRANSFER", debitExp!!.relationshipType)
        assertEquals("SELF_TRANSFER_138212387425", debitExp.relationshipId)
        assertFalse(debitExp.isPending)

        assertEquals("SELF_TRANSFER", creditExp!!.relationshipType)
        assertEquals("SELF_TRANSFER_138212387425", creditExp.relationshipId)
        assertFalse(creditExp.isPending)

        val relList = dao.getExpensesByRelationshipId("SELF_TRANSFER_138212387425")
        assertEquals(2, relList.size)
    }

    @Test
    fun testDevice_directionHardening_creditedInAccount_deviceExecution() = runBlocking {
        val dao = testDb.expenseDao()

        val text = "Rs.100/- is credited in your A/c XXXX1065 on 28-Sep-26. UPI Ref: 138212387425."
        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.messaging",
            notificationKey = "device_test_credited_${System.currentTimeMillis()}",
            postTime = System.currentTimeMillis(),
            title = "VK-APGBANK",
            text = text
        )

        val res = TransactionPersistenceManager.processCapturedNotification(captured, dao)
        assertTrue(res is ExpensePersistenceResult.Inserted)
        val inserted = (res as ExpensePersistenceResult.Inserted).expense

        assertEquals("Credit", inserted.type)
        assertEquals(100.0, inserted.amount, 0.01)
        assertFalse(inserted.isPending)
    }

    @Test
    fun testDevice_completeAll_approvesAllPendingExpensesOnDevice() = runBlocking {
        val dao = testDb.expenseDao()

        dao.insert(Expense(id = 1, amount = 100.0, merchant = "Pending 1", dateMillis = 1000L, isPending = true))
        dao.insert(Expense(id = 2, amount = 200.0, merchant = "Pending 2", dateMillis = 2000L, isPending = true))
        dao.insert(Expense(id = 3, amount = 300.0, merchant = "Normal", dateMillis = 3000L, isPending = false))

        assertEquals(2, dao.getPendingExpensesList().size)
        assertEquals(3, dao.getCount())

        // Execute Complete All logic
        val pendingList = dao.getPendingExpensesList()
        var completedCount = 0
        for (item in pendingList) {
            val updated = item.copy(isPending = false)
            val rows = dao.update(updated)
            if (rows > 0) completedCount++
        }

        assertEquals(2, completedCount)
        assertEquals(0, dao.getPendingExpensesList().size)
        val all = dao.getAllExpensesList()
        assertEquals(3, all.size)
        assertTrue(all.all { !it.isPending })
    }

    @Test
    fun testDevice_atomicSelfTransfer_counterpartMissing_rollsBackInRealSqlite() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        // Insert initial valid expense
        val initialId = dao.insert(
            Expense(
                id = 0,
                amount = 500.0,
                merchant = "Existing Merchant",
                dateMillis = System.currentTimeMillis(),
                type = "Debit",
                notificationKey = "existing_device_key_1"
            )
        )
        assertEquals(1, dao.getCount())

        // Attempt insertSelfTransferPair with a missing counterpart key
        val candidate = Expense(
            id = 0,
            amount = 500.0,
            merchant = "Transfer Credit",
            dateMillis = System.currentTimeMillis(),
            type = "Credit",
            notificationKey = "new_transfer_candidate_key",
            relationshipType = "SELF_TRANSFER",
            relationshipId = "SELF_TRANSFER_12345"
        )

        var thrown = false
        try {
            dao.insertSelfTransferPair(
                newExpense = candidate,
                counterpartRecordId = "completely_missing_counterpart_key",
                relationshipType = "SELF_TRANSFER",
                relationshipId = "SELF_TRANSFER_12345"
            )
        } catch (e: IllegalStateException) {
            thrown = true
            assertTrue(e.message?.contains("Self-transfer counterpart record not found") == true)
        }

        assertTrue("Expected IllegalStateException from insertSelfTransferPair", thrown)

        // Verify SQLite transaction rollback: count must still be exactly 1!
        assertEquals(1, dao.getCount())
        assertNull(dao.getExpenseByNotificationKey("new_transfer_candidate_key"))

        val surviving = dao.getExpenseById(initialId.toInt())
        assertNotNull(surviving)
        assertNull(surviving!!.relationshipType)
        assertNull(surviving.relationshipId)
    }

    @Test
    fun testDevice_atomicSelfTransfer_success_bothPersistedAndLinkedAtomically() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        val debitCand = StructuredTransactionCandidate(
            sourceNotificationKey = "device_atomic_debit",
            packageName = "com.google.android.apps.messaging",
            postTime = System.currentTimeMillis(),
            transactionTimestamp = System.currentTimeMillis(),
            amount = 750.0,
            merchant = "IPPB Transfer",
            direction = TransactionDirection.DEBIT,
            status = TransactionStatus.SUCCESS,
            referenceId = "UPI77889900",
            utr = "UPI77889900",
            accountSuffix = "2959",
            bank = "IPPB",
            rawContent = "Rs.750.00 debited from A/c XX2959. UPI Ref: UPI77889900."
        )
        val validDebit = ValidatedTransactionCandidate(
            candidate = debitCand,
            validationState = ValidationState.ACCEPTABLE,
            evidenceLevel = EvidenceLevel.VERY_STRONG,
            isStructurallyValid = true,
            validationReasons = listOf("Valid debit")
        )

        val creditCand = StructuredTransactionCandidate(
            sourceNotificationKey = "device_atomic_credit",
            packageName = "com.google.android.apps.messaging",
            postTime = System.currentTimeMillis() + 1000L,
            transactionTimestamp = System.currentTimeMillis() + 1000L,
            amount = 750.0,
            merchant = "APGBank Transfer",
            direction = TransactionDirection.CREDIT,
            status = TransactionStatus.SUCCESS,
            referenceId = "UPI77889900",
            utr = "UPI77889900",
            accountSuffix = "1065",
            bank = "APGBank",
            rawContent = "Rs.750/- is credited in your A/c XXXX1065. UPI Ref: UPI77889900."
        )
        val validCredit = ValidatedTransactionCandidate(
            candidate = creditCand,
            validationState = ValidationState.ACCEPTABLE,
            evidenceLevel = EvidenceLevel.VERY_STRONG,
            isStructurallyValid = true,
            validationReasons = listOf("Valid credit")
        )

        val resA = TransactionPersistenceManager.processValidatedCandidate(validDebit, dao)
        assertTrue(resA is ExpensePersistenceResult.Inserted)
        assertEquals(1, dao.getCount())

        val resB = TransactionPersistenceManager.processValidatedCandidate(validCredit, dao)
        assertTrue(resB is ExpensePersistenceResult.Inserted)
        assertEquals(2, dao.getCount())

        val all = dao.getAllExpensesList()
        val debit = all.find { it.type == "Debit" }
        val credit = all.find { it.type == "Credit" }

        assertNotNull(debit)
        assertNotNull(credit)
        assertEquals("SELF_TRANSFER", debit!!.relationshipType)
        assertEquals("SELF_TRANSFER_UPI77889900", debit.relationshipId)
        assertEquals("SELF_TRANSFER", credit!!.relationshipType)
        assertEquals("SELF_TRANSFER_UPI77889900", credit.relationshipId)
        assertEquals(750.0, debit.amount, 0.001)
        assertEquals(750.0, credit.amount, 0.001)
        assertEquals("2959", debit.accountSuffix)
        assertEquals("1065", credit.accountSuffix)
    }
}
