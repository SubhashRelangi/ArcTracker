package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.DeletedTransactionTombstone
import com.subhashrelangi.arctracker.data.Expense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * P2 — Step 5: Atomic Batch Pending Approval Unit Tests.
 *
 * Verifies that:
 * 1. Batch approval atomically updates only target transaction IDs to isPending = false.
 * 2. Unselected pending and non-pending transactions remain completely unchanged.
 * 3. All transaction attributes (amount, date, merchant, note, tags, categories, accounts, self-transfers) are strictly preserved.
 * 4. Empty ID lists safely return 0 without executing unnecessary queries.
 * 5. Database write failures roll back cleanly and propagate exceptions without false success.
 * 6. Room Flow immediately reflects the updated state.
 * 7. Historical SMS tombstones remain untouched.
 */
class AtomicBatchPendingApprovalTest {

    private lateinit var dao: FakeExpenseDao
    private val testTimestamp = 1759000000000L

    @Before
    fun setUp() {
        dao = FakeExpenseDao()
    }

    @Test
    fun test01_batchApproval_updatesMultiplePendingTransactionsAtomically() = runBlocking {
        val id1 = dao.insert(Expense(amount = 100.0, merchant = "Merchant 1", dateMillis = testTimestamp, isPending = true)).toInt()
        val id2 = dao.insert(Expense(amount = 200.0, merchant = "Merchant 2", dateMillis = testTimestamp, isPending = true)).toInt()
        val id3 = dao.insert(Expense(amount = 300.0, merchant = "Merchant 3", dateMillis = testTimestamp, isPending = true)).toInt()

        assertEquals(3, dao.getPendingExpensesList().size)

        val updatedCount = dao.approvePendingTransactions(listOf(id1, id2, id3))
        assertEquals("Must return 3 updated rows", 3, updatedCount)

        val all = dao.getAllExpenses().first()
        assertEquals(3, all.size)
        assertTrue("All 3 transactions must now be approved (isPending = false)", all.all { !it.isPending })
        assertTrue("Pending list must now be empty", dao.getPendingExpensesList().isEmpty())
    }

    @Test
    fun test02_nonSelectedTransactions_remainUnchanged() = runBlocking {
        val pendingToApprove = dao.insert(Expense(amount = 50.0, merchant = "Approve Me", dateMillis = testTimestamp, isPending = true)).toInt()
        val pendingToKeep = dao.insert(Expense(amount = 75.0, merchant = "Keep Pending", dateMillis = testTimestamp, isPending = true)).toInt()
        val alreadyCompleted = dao.insert(Expense(amount = 150.0, merchant = "Already Completed", dateMillis = testTimestamp, isPending = false)).toInt()

        val updatedCount = dao.approvePendingTransactions(listOf(pendingToApprove))
        assertEquals(1, updatedCount)

        val approved = dao.getExpenseById(pendingToApprove)!!
        assertFalse("Target record must be approved", approved.isPending)

        val kept = dao.getExpenseById(pendingToKeep)!!
        assertTrue("Non-selected record must remain pending", kept.isPending)

        val completed = dao.getExpenseById(alreadyCompleted)!!
        assertFalse("Already completed record must remain unchanged", completed.isPending)
    }

    @Test
    fun test03_allTransactionFieldsPreserved_duringBatchApproval() = runBlocking {
        val id = dao.insert(
            Expense(
                amount = 1299.50,
                merchant = "Amazon India",
                dateMillis = testTimestamp + 5000L,
                type = "Credit",
                notificationKey = "notif_amazon_123",
                isPending = true,
                rawText = "Raw SMS text for order",
                tag = "Shopping",
                note = "Order #12345",
                source = "NOTIFICATION",
                relationshipType = "COUNTERPART",
                relationshipId = "REL_9999",
                accountId = "acc_hdfc_1234",
                accountSuffix = "1234",
                categoryId = "shopping",
                categorySource = "USER_ASSIGNED"
            )
        ).toInt()

        val updatedCount = dao.approvePendingTransactions(listOf(id))
        assertEquals(1, updatedCount)

        val updated = dao.getExpenseById(id)!!
        assertFalse("isPending must be false", updated.isPending)
        assertEquals(1299.50, updated.amount, 0.001)
        assertEquals("Amazon India", updated.merchant)
        assertEquals(testTimestamp + 5000L, updated.dateMillis)
        assertEquals("Credit", updated.type)
        assertEquals("notif_amazon_123", updated.notificationKey)
        assertEquals("Raw SMS text for order", updated.rawText)
        assertEquals("Shopping", updated.tag)
        assertEquals("Order #12345", updated.note)
        assertEquals("NOTIFICATION", updated.source)
        assertEquals("COUNTERPART", updated.relationshipType)
        assertEquals("REL_9999", updated.relationshipId)
        assertEquals("acc_hdfc_1234", updated.accountId)
        assertEquals("1234", updated.accountSuffix)
        assertEquals("shopping", updated.categoryId)
        assertEquals("USER_ASSIGNED", updated.categorySource)
    }

    @Test
    fun test04_emptyIdList_safelyReturnsZeroWithoutModifyingData() = runBlocking {
        dao.insert(Expense(amount = 40.0, merchant = "Chai", dateMillis = testTimestamp, isPending = true))

        val count = dao.approvePendingTransactions(emptyList())
        assertEquals(0, count)
        assertEquals(1, dao.getPendingExpensesList().size)
    }

    @Test
    fun test05_nonExistentIds_handledGracefully() = runBlocking {
        val existingId = dao.insert(Expense(amount = 99.0, merchant = "Snacks", dateMillis = testTimestamp, isPending = true)).toInt()

        val count = dao.approvePendingTransactions(listOf(existingId, 99999, 88888))
        assertEquals("Should update only the existing record", 1, count)
        assertFalse(dao.getExpenseById(existingId)!!.isPending)
    }

    @Test
    fun test06_databaseFailure_rollsBackAndPropagatesException() = runBlocking {
        val id1 = dao.insert(Expense(amount = 10.0, merchant = "T1", dateMillis = testTimestamp, isPending = true)).toInt()
        val id2 = dao.insert(Expense(amount = 20.0, merchant = "T2", dateMillis = testTimestamp, isPending = true)).toInt()

        dao.failOnBatchUpdate = true

        var exceptionThrown = false
        try {
            dao.approvePendingTransactions(listOf(id1, id2))
        } catch (_: Exception) {
            exceptionThrown = true
        }

        assertTrue("Database failure must propagate exception", exceptionThrown)

        // Verify state is completely preserved in Room Flow
        val pendingAfter = dao.getPendingExpensesList()
        assertEquals(2, pendingAfter.size)
        assertTrue("All records must remain pending on rollback", pendingAfter.all { it.isPending })
    }

    @Test
    fun test07_roomFlow_reactivelyEmitsBatchUpdate() = runBlocking {
        val id1 = dao.insert(Expense(amount = 15.0, merchant = "Milk", dateMillis = testTimestamp, isPending = true)).toInt()
        val id2 = dao.insert(Expense(amount = 25.0, merchant = "Bread", dateMillis = testTimestamp, isPending = true)).toInt()

        val initialFlow = dao.getAllExpenses().first()
        assertEquals(2, initialFlow.size)
        assertTrue(initialFlow.all { it.isPending })

        dao.approvePendingTransactions(listOf(id1, id2))

        val updatedFlow = dao.getAllExpenses().first()
        assertEquals(2, updatedFlow.size)
        assertTrue("Flow must reactively reflect isPending = false", updatedFlow.none { it.isPending })
    }

    @Test
    fun test08_selfTransferAndTombstones_remainIntactAcrossBatchApproval() = runBlocking {
        // Record a tombstone
        dao.insertTombstone(
            DeletedTransactionTombstone(
                sourceKey = "sms_tombstone_test",
                sourceType = "SMS_HISTORY"
            )
        )

        // Self-transfer pair in pending review
        val relId = "SELF_TRANSFER_BATCH_123"
        val debitId = dao.insert(
            Expense(
                amount = 5000.0,
                merchant = "Self Transfer Debit",
                type = "Debit",
                dateMillis = testTimestamp,
                isPending = true,
                relationshipType = "SELF_TRANSFER",
                relationshipId = relId
            )
        ).toInt()
        val creditId = dao.insert(
            Expense(
                amount = 5000.0,
                merchant = "Self Transfer Credit",
                type = "Credit",
                dateMillis = testTimestamp,
                isPending = true,
                relationshipType = "SELF_TRANSFER",
                relationshipId = relId
            )
        ).toInt()

        val updatedCount = dao.approvePendingTransactions(listOf(debitId, creditId))
        assertEquals(2, updatedCount)

        val debit = dao.getExpenseById(debitId)!!
        val credit = dao.getExpenseById(creditId)!!

        assertFalse(debit.isPending)
        assertFalse(credit.isPending)
        assertEquals("SELF_TRANSFER", debit.relationshipType)
        assertEquals("SELF_TRANSFER", credit.relationshipType)
        assertEquals(relId, debit.relationshipId)
        assertEquals(relId, credit.relationshipId)

        // Tombstone must remain intact
        assertTrue("Tombstone must not be affected by batch approvals", dao.isSourceKeyTombstoned("sms_tombstone_test"))
    }
}
