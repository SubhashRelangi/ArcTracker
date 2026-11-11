package com.subhashrelangi.arctracker

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.engine.account.AccountReconciliationManager
import com.subhashrelangi.arctracker.engine.account.InstrumentType
import com.subhashrelangi.arctracker.engine.category.CategoryManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 3: Review & Triage Workspace On-Device Tests.
 *
 * Runs on connected physical Samsung Galaxy A34 5G (`SM-A346E` - Android 16).
 *
 * Verifies on real SQLite:
 * 1. 1-tap "Approve" button: state transition from isPending = true -> false.
 * 2. Category assignment on pending transactions.
 * 3. Unlinked account triage & assignment from Review workspace.
 * 4. Bulk approval of selected pending transactions atomically.
 * 5. Ignore/Dismiss action marking transaction reviewed with audit note.
 * 6. Empty review queue returns zero pending count.
 */
@RunWith(AndroidJUnit4::class)
class ReviewWorkspaceDeviceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var expenseDao: ExpenseDao
    private lateinit var accountRepo: KnownFinancialAccountRepository
    private lateinit var reconciliationManager: AccountReconciliationManager
    private lateinit var categoryManager: CategoryManager
    private val testDbName = "phase3_review_device_test.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDbName)

        database = Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
            .allowMainThreadQueries()
            .build()

        expenseDao = database.expenseDao()
        accountRepo = KnownFinancialAccountRepository(database.knownFinancialAccountDao())
        reconciliationManager = AccountReconciliationManager(expenseDao, accountRepo, database)
        categoryManager = CategoryManager(
            database.transactionCategoryDao(),
            expenseDao,
            database,
            database.userCategoryRuleDao(),
            database.budgetDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(testDbName)
    }

    @Test
    fun test01_singleTransactionApprove_updatesIsPendingAndSurvivesReopen() = runBlocking {
        val now = System.currentTimeMillis()
        val pendingId = expenseDao.insert(
            Expense(
                amount = 420.0,
                merchant = "Swiggy Delivery",
                dateMillis = now,
                isPending = true,
                type = "Debit"
            )
        ).toInt()

        // Verify inserted as pending
        val pendingBefore = expenseDao.getExpenseById(pendingId)
        assertNotNull(pendingBefore)
        assertTrue(pendingBefore!!.isPending)

        // 1-Tap Approve: isPending = false
        val updated = pendingBefore.copy(isPending = false)
        expenseDao.update(updated)

        // Verify state transition
        val approved = expenseDao.getExpenseById(pendingId)
        assertNotNull(approved)
        assertFalse(approved!!.isPending)
        assertEquals(420.0, approved.amount, 0.001)
        assertEquals("Swiggy Delivery", approved.merchant)

        // Reopen database to verify persistence on flash memory
        database.close()
        val reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
            .allowMainThreadQueries()
            .build()
        try {
            val loaded = reopenedDb.expenseDao().getExpenseById(pendingId)
            assertNotNull(loaded)
            assertFalse(loaded!!.isPending)
        } finally {
            reopenedDb.close()
        }
    }

    @Test
    fun test02_categoryAssignmentOnPending_updatesTagAndCategoryId() = runBlocking {
        val now = System.currentTimeMillis()
        val pendingId = expenseDao.insert(
            Expense(
                amount = 280.0,
                merchant = "Blue Tokai",
                dateMillis = now,
                isPending = true,
                type = "Debit"
            )
        ).toInt()

        val item = expenseDao.getExpenseById(pendingId)
        assertNotNull(item)
        assertNull(item!!.categoryId)

        // Assign category "Dining" from CategorySelectionModalDialog
        val assigned = item.copy(
            categoryId = "food_dining",
            tag = "Food & Dining",
            isPending = false
        )
        expenseDao.update(assigned)

        val afterAssign = expenseDao.getExpenseById(pendingId)
        assertNotNull(afterAssign)
        assertEquals("food_dining", afterAssign!!.categoryId)
        assertEquals("Food & Dining", afterAssign.tag)
        assertFalse(afterAssign.isPending)
    }

    @Test
    fun test03_unlinkedAccountAssignmentFromReview_linksAccount() = runBlocking {
        val hdfc = KnownFinancialAccount(
            id = "hdfc_bank_4821",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "4821",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountRepo.upsert(hdfc)

        val unlinkedId = expenseDao.insert(
            Expense(
                amount = 1540.0,
                merchant = "Nature's Basket",
                dateMillis = System.currentTimeMillis(),
                isPending = true,
                type = "Debit",
                accountSuffix = "4821",
                accountId = null
            )
        ).toInt()

        // Assign account using reconciliationManager
        val result = reconciliationManager.assignAccount(unlinkedId, hdfc.id)
        assertTrue(result.isSuccess)

        val linked = expenseDao.getExpenseById(unlinkedId)
        assertNotNull(linked)
        assertEquals(hdfc.id, linked!!.accountId)
        assertEquals("4821", linked.accountSuffix)
    }

    @Test
    fun test04_bulkApprovePendingTransactions_updatesAllAtomically() = runBlocking {
        val now = System.currentTimeMillis()
        val id1 = expenseDao.insert(Expense(amount = 100.0, merchant = "Txn 1", dateMillis = now, isPending = true)).toInt()
        val id2 = expenseDao.insert(Expense(amount = 200.0, merchant = "Txn 2", dateMillis = now, isPending = true)).toInt()
        val id3 = expenseDao.insert(Expense(amount = 300.0, merchant = "Txn 3", dateMillis = now, isPending = true)).toInt()
        val id4 = expenseDao.insert(Expense(amount = 400.0, merchant = "Txn 4", dateMillis = now, isPending = true)).toInt()

        assertEquals(4, expenseDao.getPendingExpensesList().size)

        // Bulk approve id1, id2, id3
        val approvedCount = expenseDao.approvePendingTransactions(listOf(id1, id2, id3))
        assertEquals(3, approvedCount)

        val pendingRemaining = expenseDao.getPendingExpensesList()
        assertEquals(1, pendingRemaining.size)
        assertEquals(id4, pendingRemaining.first().id)
    }

    @Test
    fun test05_ignorePendingTransaction_marksCompletedWithAuditNote() = runBlocking {
        val unlinkedId = expenseDao.insert(
            Expense(
                amount = 500.0,
                merchant = "Unknown Merchant",
                dateMillis = System.currentTimeMillis(),
                isPending = true,
                type = "Debit",
                note = "Original note"
            )
        ).toInt()

        val exp = expenseDao.getExpenseById(unlinkedId)
        assertNotNull(exp)

        // Ignore action from ReviewPage
        val ignored = exp!!.copy(
            isPending = false,
            note = (exp.note ?: "") + " [Ignored unlinked account]"
        )
        expenseDao.update(ignored)

        val afterIgnore = expenseDao.getExpenseById(unlinkedId)
        assertNotNull(afterIgnore)
        assertFalse(afterIgnore!!.isPending)
        assertTrue(afterIgnore.note!!.contains("[Ignored unlinked account]"))
    }

    @Test
    fun test06_emptyReviewQueueState_returnsZeroPendingCount() = runBlocking {
        expenseDao.insert(Expense(amount = 100.0, merchant = "Approved", dateMillis = System.currentTimeMillis(), isPending = false))
        val pending = expenseDao.getPendingExpensesList()
        assertTrue(pending.isEmpty())
        assertEquals(0, pending.size)
    }
}
