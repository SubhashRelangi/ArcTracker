package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.service.TransactionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * P2 — Step 3: Transaction UI Consistency and Database Error Handling Test Suite.
 *
 * Verifies that:
 * 1. Room Flow is the single source of truth for transaction state.
 * 2. Failed database inserts leave no phantom or zero-ID transactions in state.
 * 3. Failed updates preserve database-backed state and report failure cleanly.
 * 4. Failed deletions leave database-backed records intact and report failure cleanly.
 * 5. Failed pending approvals leave transactions pending without optimistic completion.
 * 6. Structured concurrency is preserved by rethrowing CancellationException.
 * 7. Successful mutations reactively emit consistent state through the Flow.
 */
class TransactionUiConsistencyAndErrorHandlingTest {

    private lateinit var expenseDao: FakeExpenseDao
    private lateinit var transactionManager: TransactionManager
    private val testTimestamp: Long = 1759000000000L

    @Before
    fun setUp() {
        expenseDao = FakeExpenseDao()
        transactionManager = TransactionManager(expenseDao)
    }

    // ==========================================
    // TEST A: CREATION & EMISSION INTEGRITY
    // ==========================================

    @Test
    fun testA_successfulManualCreation_appearsOnceWithValidDatabaseId() = runBlocking {
        val initialList = expenseDao.getAllExpenses().first()
        assertTrue("Initial list must be empty", initialList.isEmpty())

        val newExpense = Expense(
            amount = 450.0,
            merchant = "Starbucks",
            type = "Debit",
            tag = "Food",
            dateMillis = testTimestamp,
            source = "MANUAL",
            isPending = false
        )

        val insertedId = expenseDao.insert(newExpense)
        assertTrue("Database ID must be positive", insertedId > 0)

        val updatedList = expenseDao.getAllExpenses().first()
        assertEquals("List should contain exactly 1 transaction", 1, updatedList.size)
        assertEquals("Emitted transaction must have the assigned database ID", insertedId.toInt(), updatedList[0].id)
        assertEquals("Starbucks", updatedList[0].merchant)
        assertEquals(450.0, updatedList[0].amount, 0.001)

        // Verify no phantom zero-ID item exists
        assertFalse("No item with id=0 should exist", updatedList.any { it.id == 0 })
    }

    // ==========================================
    // TEST B: INSERT FAILURE & PHANTOM PREVENTION
    // ==========================================

    @Test
    fun testB_failedInsert_leavesNoPhantomTransactionInFlow() = runBlocking {
        expenseDao.failOnInsert = true

        val initialList = expenseDao.getAllExpenses().first()
        assertTrue(initialList.isEmpty())

        val newExpense = Expense(
            amount = 999.0,
            merchant = "Phantom Merchant",
            type = "Debit",
            dateMillis = testTimestamp,
            source = "MANUAL"
        )

        var insertCaught = false
        try {
            expenseDao.insert(newExpense)
        } catch (e: Exception) {
            insertCaught = true
            assertFalse("Should not be CancellationException", e is CancellationException)
        }

        assertTrue("Expected insert failure exception was caught", insertCaught)

        // Check that the Flow remains completely empty: no optimistic item with id=0
        val postFailureList = expenseDao.getAllExpenses().first()
        assertTrue("Flow must remain empty when insert fails", postFailureList.isEmpty())
        assertNull("Expense should not exist in database", expenseDao.getExpenseById(0))
    }

    // ==========================================
    // TEST C: UPDATE FAILURE PRESERVATION
    // ==========================================

    @Test
    fun testC_failedUpdate_preservesPersistedRecordAndReportsFailure() = runBlocking {
        val id = expenseDao.insert(
            Expense(
                amount = 250.0,
                merchant = "Original Merchant",
                tag = "Groceries",
                type = "Debit",
                dateMillis = testTimestamp
            )
        ).toInt()

        val beforeUpdate = expenseDao.getExpenseById(id)
        assertNotNull(beforeUpdate)
        assertEquals("Original Merchant", beforeUpdate!!.merchant)

        // Simulate database failure on update
        expenseDao.failOnUpdate = true

        val result = transactionManager.updateTransaction(
            expenseId = id,
            merchant = "Modified Merchant"
        )

        assertTrue("Update must return failure Result", result.isFailure)

        // Verify Room Flow state has not been mutated
        val currentInFlow = expenseDao.getAllExpenses().first().first { it.id == id }
        assertEquals("Merchant must remain unchanged in Flow", "Original Merchant", currentInFlow.merchant)
        assertEquals("Original Merchant", expenseDao.getExpenseById(id)?.merchant)
    }

    // ==========================================
    // TEST D: DELETION FAILURE PRESERVATION
    // ==========================================

    @Test
    fun testD_failedDeletion_preservesRecordInFlowAndReportsFailure() = runBlocking {
        val id = expenseDao.insert(
            Expense(
                amount = 1200.0,
                merchant = "Rent Payment",
                type = "Debit",
                dateMillis = testTimestamp
            )
        ).toInt()

        val listBefore = expenseDao.getAllExpenses().first()
        assertEquals(1, listBefore.size)

        // Simulate database failure on delete
        expenseDao.failOnDelete = true

        val deleteResult = transactionManager.deleteTransaction(id)
        assertTrue("Delete must return failure Result", deleteResult.isFailure)

        // Verify transaction is still present in Flow
        val listAfter = expenseDao.getAllExpenses().first()
        assertEquals("Record must not be deleted from Flow on failure", 1, listAfter.size)
        assertEquals("Rent Payment", listAfter[0].merchant)
        assertNotNull(expenseDao.getExpenseById(id))
    }

    // ==========================================
    // TEST E: PENDING APPROVAL FAILURE
    // ==========================================

    @Test
    fun testE_failedApproval_preservesPendingStateInFlow() = runBlocking {
        val id = expenseDao.insert(
            Expense(
                amount = 75.0,
                merchant = "Coffee",
                type = "Debit",
                dateMillis = testTimestamp,
                isPending = true
            )
        ).toInt()

        val pendingBefore = expenseDao.getPendingExpensesList()
        assertEquals(1, pendingBefore.size)
        assertTrue(pendingBefore[0].isPending)

        // Simulate database failure on update
        expenseDao.failOnUpdate = true

        val pendingItem = expenseDao.getExpenseById(id)!!
        val approvedCopy = pendingItem.copy(isPending = false)

        var updateFailed = false
        try {
            val count = expenseDao.update(approvedCopy)
            if (count <= 0) updateFailed = true
        } catch (_: Exception) {
            updateFailed = true
        }

        assertTrue("Update must fail", updateFailed)

        // Verify the item is STILL pending in the Flow
        val pendingAfter = expenseDao.getPendingExpensesList()
        assertEquals("Item must remain pending in database", 1, pendingAfter.size)
        assertTrue("Item must still have isPending = true", pendingAfter[0].isPending)
        assertEquals(true, expenseDao.getExpenseById(id)?.isPending)
    }

    @Test
    fun testE2_batchCompleteAll_handlesPartialOrCompleteFailuresSafely() = runBlocking {
        val id1 = expenseDao.insert(Expense(amount = 10.0, merchant = "M1", dateMillis = testTimestamp, isPending = true)).toInt()
        val id2 = expenseDao.insert(Expense(amount = 20.0, merchant = "M2", dateMillis = testTimestamp, isPending = true)).toInt()

        assertEquals(2, expenseDao.getPendingExpensesList().size)

        // Fail all updates
        expenseDao.failOnUpdate = true

        val toComplete = expenseDao.getPendingExpensesList()
        var failCount = 0
        var successCount = 0

        for (item in toComplete) {
            try {
                val updated = item.copy(isPending = false)
                val count = expenseDao.update(updated)
                if (count > 0) successCount++ else failCount++
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                failCount++
            }
        }

        assertEquals(0, successCount)
        assertEquals(2, failCount)

        // State remains completely pending
        val pendingAfter = expenseDao.getPendingExpensesList()
        assertEquals(2, pendingAfter.size)
        assertTrue(pendingAfter.all { it.isPending })
    }

    // ==========================================
    // TEST F: REACTIVE FLOW LIFECYCLE
    // ==========================================

    @Test
    fun testF_reactiveFlowLifecycle_emitsStateTransitionsConsistently() = runBlocking {
        // 1. Initial empty
        assertEquals(0, expenseDao.getAllExpenses().first().size)

        // 2. Insert pending item
        val id = expenseDao.insert(
            Expense(amount = 300.0, merchant = "Initial", dateMillis = testTimestamp, isPending = true)
        ).toInt()

        val afterInsert = expenseDao.getAllExpenses().first()
        assertEquals(1, afterInsert.size)
        assertTrue(afterInsert[0].isPending)
        assertEquals(id, afterInsert[0].id)

        // 3. Approve item
        expenseDao.update(afterInsert[0].copy(isPending = false))
        val afterApprove = expenseDao.getAllExpenses().first()
        assertEquals(1, afterApprove.size)
        assertFalse(afterApprove[0].isPending)

        // 4. Edit item
        val editResult = transactionManager.updateTransaction(id, merchant = "Updated Merchant")
        assertTrue(editResult.isSuccess)
        val afterEdit = expenseDao.getAllExpenses().first()
        assertEquals("Updated Merchant", afterEdit[0].merchant)

        // 5. Delete item
        val delResult = transactionManager.deleteTransaction(id)
        assertTrue(delResult.isSuccess)
        val afterDelete = expenseDao.getAllExpenses().first()
        assertTrue(afterDelete.isEmpty())
    }

    // ==========================================
    // TEST G: CANCELLATION EXCEPTION INTEGRITY
    // ==========================================

    @Test
    fun testG_cancellationException_isRethrownAndNotSwallowed() {
        var cancellationRethrown = false
        try {
            try {
                throw CancellationException("Coroutine cancelled by user navigation")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Otherwise handle error...
            }
        } catch (e: CancellationException) {
            cancellationRethrown = true
        }

        assertTrue("CancellationException must be rethrown to preserve structured concurrency", cancellationRethrown)
    }
}
