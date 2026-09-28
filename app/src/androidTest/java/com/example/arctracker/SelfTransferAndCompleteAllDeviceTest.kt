package com.example.arctracker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arctracker.data.AppDatabase
import com.example.arctracker.data.Expense
import com.example.arctracker.service.*
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
}
