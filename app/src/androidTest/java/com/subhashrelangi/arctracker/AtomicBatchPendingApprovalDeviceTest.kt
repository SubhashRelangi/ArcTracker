package com.subhashrelangi.arctracker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.Expense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P2 — Step 5: Real Room SQLite / Device Tests for Atomic Batch Pending Approval.
 *
 * Verifies on real SQLite / Room:
 * 1. Single parameterized query updates multiple pending transactions atomically.
 * 2. Unselected transactions remain unchanged in real SQLite.
 * 3. Empty ID selection safely returns 0 without executing bad queries.
 * 4. All transaction columns and self-transfer relationships remain 100% intact.
 */
@RunWith(AndroidJUnit4::class)
class AtomicBatchPendingApprovalDeviceTest {

    private lateinit var context: Context
    private lateinit var testDb: AppDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testDb = AppDatabase.createInMemoryDatabase(context)
    }

    @After
    fun tearDown() {
        testDb.close()
    }

    @Test
    fun testRealSqlite_batchApproval_updatesPendingStatusAtomically() = runBlocking {
        val dao = testDb.expenseDao()
        val now = System.currentTimeMillis()

        val id1 = dao.insert(Expense(amount = 250.0, merchant = "Swiggy", dateMillis = now, isPending = true)).toInt()
        val id2 = dao.insert(Expense(amount = 450.0, merchant = "Zomato", dateMillis = now, isPending = true)).toInt()
        val id3 = dao.insert(Expense(amount = 650.0, merchant = "Uber", dateMillis = now, isPending = true)).toInt()

        assertEquals(3, dao.getPendingExpensesList().size)

        // Approve id1 and id2 only
        val updatedCount = dao.approvePendingTransactions(listOf(id1, id2))
        assertEquals("Real SQLite must update exactly 2 rows", 2, updatedCount)

        // Verify id1 and id2 are approved
        val e1 = dao.getExpenseById(id1)!!
        val e2 = dao.getExpenseById(id2)!!
        val e3 = dao.getExpenseById(id3)!!

        assertFalse("id1 must be approved", e1.isPending)
        assertFalse("id2 must be approved", e2.isPending)
        assertTrue("id3 must remain pending", e3.isPending)

        // Verify pending list in SQLite now contains only id3
        val remainingPending = dao.getPendingExpensesList()
        assertEquals(1, remainingPending.size)
        assertEquals(id3, remainingPending[0].id)
    }

    @Test
    fun testRealSqlite_emptyList_safelyReturnsZero() = runBlocking {
        val dao = testDb.expenseDao()
        dao.insert(Expense(amount = 100.0, merchant = "Store", dateMillis = System.currentTimeMillis(), isPending = true))

        val count = dao.approvePendingTransactions(emptyList())
        assertEquals(0, count)
        assertEquals(1, dao.getPendingExpensesList().size)
    }

    @Test
    fun testRealSqlite_preservesAllColumnsAndSelfTransfers() = runBlocking {
        val dao = testDb.expenseDao()
        val now = System.currentTimeMillis()
        val relId = "SELF_TRANSFER_DEVICE_SQLITE_1"

        val debitId = dao.insert(
            Expense(
                amount = 2000.0,
                merchant = "Debit Self",
                type = "Debit",
                dateMillis = now,
                notificationKey = "sms_device_test_1",
                isPending = true,
                rawText = "Raw transfer text",
                tag = "Transfer",
                note = "Personal transfer",
                source = "SMS_HISTORY",
                relationshipType = "SELF_TRANSFER",
                relationshipId = relId,
                accountId = "acc_1",
                accountSuffix = "1111",
                categoryId = "transfer",
                categorySource = "USER_ASSIGNED"
            )
        ).toInt()

        val updatedCount = dao.approvePendingTransactions(listOf(debitId))
        assertEquals(1, updatedCount)

        val updated = dao.getExpenseById(debitId)!!
        assertFalse(updated.isPending)
        assertEquals(2000.0, updated.amount, 0.001)
        assertEquals("Debit Self", updated.merchant)
        assertEquals("Debit", updated.type)
        assertEquals(now, updated.dateMillis)
        assertEquals("sms_device_test_1", updated.notificationKey)
        assertEquals("Raw transfer text", updated.rawText)
        assertEquals("Transfer", updated.tag)
        assertEquals("Personal transfer", updated.note)
        assertEquals("SMS_HISTORY", updated.source)
        assertEquals("SELF_TRANSFER", updated.relationshipType)
        assertEquals(relId, updated.relationshipId)
        assertEquals("acc_1", updated.accountId)
        assertEquals("1111", updated.accountSuffix)
        assertEquals("transfer", updated.categoryId)
        assertEquals("USER_ASSIGNED", updated.categorySource)
    }
}
