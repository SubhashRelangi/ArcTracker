package com.subhashrelangi.arctracker

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

/**
 * Milestone 13: Analytics Physical Device & Instrumentation Tests.
 *
 * Runs on connected physical Android device (Samsung Galaxy A34 5G).
 *
 * Verifies:
 * - Analytics calculations against real Room SQLite database.
 * - Empty state verification.
 * - Date range switching (Today, This Week, This Month, All Time, Custom Range).
 * - Expense, income, net totals calculation.
 * - Category breakdown with built-in and custom categories.
 * - Monthly trend aggregation.
 * - Top merchant aggregation and ranking.
 * - Uncategorized transactions handling.
 * - Self-transfer safety (exclusion from financial metrics).
 * - Account activity breakdown.
 * - Reactive Flow stream observation.
 * - Invariant: Analytics does NOT modify persisted transaction records in SQLite.
 */
@RunWith(AndroidJUnit4::class)
class AnalyticsDeviceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var expenseDao: ExpenseDao
    private lateinit var categoryDao: TransactionCategoryDao
    private lateinit var accountDao: KnownFinancialAccountDao
    private lateinit var analyticsManager: AnalyticsManager

    private val testDbName = "m13_device_test_analytics.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(testDbName)

        database = Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
            .allowMainThreadQueries()
            .build()

        expenseDao = database.expenseDao()
        categoryDao = database.transactionCategoryDao()
        accountDao = database.knownFinancialAccountDao()

        analyticsManager = AnalyticsManager(
            expenseDao = expenseDao,
            categoryDao = categoryDao,
            accountDao = accountDao
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(testDbName)
    }

    // 1. Empty State
    @Test
    fun testEmptyStateOnDevice() = runBlocking {
        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.THIS_MONTH)
        assertTrue(report.isEmpty)
        assertEquals(0, report.summary.totalTransactionCount)
        assertEquals(0.0, report.summary.totalExpenses, 0.001)
        assertEquals(0.0, report.summary.totalIncome, 0.001)
        assertEquals(0.0, report.summary.netAmount, 0.001)
        assertTrue(report.categoryBreakdown.isEmpty())
        assertTrue(report.monthlyTrends.isEmpty())
        assertTrue(report.topMerchants.isEmpty())
    }

    // 2. Date Range Changes
    @Test
    fun testDateRangeChangesOnDevice() = runBlocking {
        val now = System.currentTimeMillis()
        val oneDayMillis = 24L * 60 * 60 * 1000

        // Today transaction
        expenseDao.insert(Expense(amount = 100.0, merchant = "Today Store", dateMillis = now, type = "Debit"))
        // Last week transaction
        expenseDao.insert(Expense(amount = 200.0, merchant = "Past Store", dateMillis = now - (10 * oneDayMillis), type = "Debit"))

        val todayReport = analyticsManager.getAnalyticsReport(AnalyticsDateRange.TODAY, referenceTimeMillis = now)
        assertEquals(1, todayReport.summary.totalTransactionCount)
        assertEquals(100.0, todayReport.summary.totalExpenses, 0.001)

        val allTimeReport = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        assertEquals(2, allTimeReport.summary.totalTransactionCount)
        assertEquals(300.0, allTimeReport.summary.totalExpenses, 0.001)
    }

    // 3. Totals and Net Calculation
    @Test
    fun testTotalsAndNetCalculationOnDevice() = runBlocking {
        val now = System.currentTimeMillis()
        expenseDao.insert(Expense(amount = 5000.0, merchant = "Salary", dateMillis = now, type = "Credit"))
        expenseDao.insert(Expense(amount = 1200.0, merchant = "Rent", dateMillis = now, type = "Debit"))
        expenseDao.insert(Expense(amount = 300.0, merchant = "Food", dateMillis = now, type = "Debit"))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        assertEquals(5000.0, report.summary.totalIncome, 0.001)
        assertEquals(1500.0, report.summary.totalExpenses, 0.001)
        assertEquals(3500.0, report.summary.netAmount, 0.001)
        assertEquals(3, report.summary.totalTransactionCount)
        assertEquals(2, report.summary.expenseCount)
        assertEquals(1, report.summary.incomeCount)
    }

    // 4. Category Breakdown with Built-in & Custom Categories
    @Test
    fun testCategoryBreakdownOnDevice() = runBlocking {
        val now = System.currentTimeMillis()

        // Seed custom category
        val custom = TransactionCategory(
            id = "custom_gadgets",
            name = "Tech Gadgets",
            iconKey = "devices",
            colorKey = "blue",
            isSystem = false
        )
        categoryDao.upsert(custom)

        expenseDao.insert(Expense(amount = 1000.0, merchant = "Electronics Hub", dateMillis = now, type = "Debit", categoryId = "custom_gadgets"))
        expenseDao.insert(Expense(amount = 500.0, merchant = "Chai Point", dateMillis = now, type = "Debit", categoryId = "food_dining"))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        assertEquals(2, report.categoryBreakdown.size)

        val customItem = report.categoryBreakdown.find { it.categoryId == "custom_gadgets" }
        assertNotNull(customItem)
        assertEquals("Tech Gadgets", customItem!!.categoryName)
        assertEquals(1000.0, customItem.totalAmount, 0.001)
        assertEquals(66.67, customItem.percentage, 0.1)

        val foodItem = report.categoryBreakdown.find { it.categoryId == "food_dining" }
        assertNotNull(foodItem)
        assertEquals(500.0, foodItem!!.totalAmount, 0.001)
        assertEquals(33.33, foodItem.percentage, 0.1)
    }

    // 5. Monthly Trend Breakdown
    @Test
    fun testMonthlyTrendsOnDevice() = runBlocking {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 10, 12, 0) }
        val marchTime = cal.timeInMillis
        cal.set(2026, Calendar.APRIL, 10, 12, 0)
        val aprilTime = cal.timeInMillis

        expenseDao.insert(Expense(amount = 800.0, merchant = "March Spend", dateMillis = marchTime, type = "Debit", categoryId = "shopping"))
        expenseDao.insert(Expense(amount = 400.0, merchant = "April Spend", dateMillis = aprilTime, type = "Debit", categoryId = "entertainment"))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        assertEquals(2, report.monthlyTrends.size)

        val march = report.monthlyTrends.find { it.yearMonth == "2026-03" }
        assertNotNull(march)
        assertEquals(800.0, march!!.totalExpenses, 0.001)
        assertEquals(1, march.categoryTrends.size)
        assertEquals("shopping", march.categoryTrends[0].categoryId)

        val april = report.monthlyTrends.find { it.yearMonth == "2026-04" }
        assertNotNull(april)
        assertEquals(400.0, april!!.totalExpenses, 0.001)
    }

    // 6. Top Merchants
    @Test
    fun testTopMerchantsOnDevice() = runBlocking {
        val now = System.currentTimeMillis()
        expenseDao.insert(Expense(amount = 2500.0, merchant = "Amazon", dateMillis = now, type = "Debit"))
        expenseDao.insert(Expense(amount = 1500.0, merchant = "Amazon", dateMillis = now + 100, type = "Debit"))
        expenseDao.insert(Expense(amount = 800.0, merchant = "Uber", dateMillis = now + 200, type = "Debit"))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        assertEquals(2, report.topMerchants.size)
        assertEquals("Amazon", report.topMerchants[0].merchant)
        assertEquals(4000.0, report.topMerchants[0].totalAmount, 0.001)
        assertEquals(2, report.topMerchants[0].transactionCount)
    }

    // 7. Uncategorized Transactions
    @Test
    fun testUncategorizedSectionOnDevice() = runBlocking {
        val now = System.currentTimeMillis()
        expenseDao.insert(Expense(amount = 300.0, merchant = "Unknown Shop", dateMillis = now, type = "Debit", categoryId = null))
        expenseDao.insert(Expense(amount = 700.0, merchant = "Known Shop", dateMillis = now, type = "Debit", categoryId = "groceries"))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        assertEquals(1, report.uncategorized.transactionCount)
        assertEquals(300.0, report.uncategorized.totalAmount, 0.001)
        assertEquals(30.0, report.uncategorized.percentage, 0.001)
    }

    // 8. Self-Transfer Exclusion
    @Test
    fun testSelfTransferExclusionOnDevice() = runBlocking {
        val now = System.currentTimeMillis()
        expenseDao.insert(Expense(amount = 500.0, merchant = "Coffee", dateMillis = now, type = "Debit"))
        expenseDao.insert(
            Expense(
                amount = 20000.0,
                merchant = "Transfer Leg",
                dateMillis = now,
                type = "Debit",
                relationshipType = TransactionRelationshipType.SELF_TRANSFER,
                relationshipId = "self_xfer_1"
            )
        )

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        assertEquals(500.0, report.summary.totalExpenses, 0.001)
        assertEquals(1, report.summary.selfTransferCount)
        assertEquals(20000.0, report.summary.selfTransferAmount, 0.001)
    }

    // 9. Account Breakdown
    @Test
    fun testAccountBreakdownOnDevice() = runBlocking {
        val now = System.currentTimeMillis()
        val account = KnownFinancialAccount(
            id = "sbi_bank_9999",
            institutionId = "sbi",
            institutionName = "State Bank of India",
            accountSuffix = "9999",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountDao.insert(account)

        expenseDao.insert(Expense(amount = 450.0, merchant = "Store", dateMillis = now, type = "Debit", accountId = "sbi_bank_9999", accountSuffix = "9999"))
        expenseDao.insert(Expense(amount = 150.0, merchant = "Cash", dateMillis = now, type = "Debit", accountId = null))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        assertEquals(2, report.accountBreakdown.size)

        val sbi = report.accountBreakdown.find { it.accountId == "sbi_bank_9999" }
        assertNotNull(sbi)
        assertEquals("State Bank of India", sbi!!.institutionName)
        assertEquals(450.0, sbi.totalExpenses, 0.001)
    }

    // 10. Reactive Flow Updates
    @Test
    fun testReactiveFlowOnDevice() = runBlocking {
        val now = System.currentTimeMillis()
        val flow = analyticsManager.getAnalyticsReportFlow(AnalyticsDateRange.ALL_TIME)
        val initial = flow.first()
        assertTrue(initial.isEmpty)

        expenseDao.insert(Expense(amount = 750.0, merchant = "New Expense", dateMillis = now, type = "Debit"))
        val updated = flow.first()
        assertEquals(1, updated.summary.totalTransactionCount)
        assertEquals(750.0, updated.summary.totalExpenses, 0.001)
    }

    // 11. CRITICAL INVARIANT: Analytics Never Alters Transaction Data
    @Test
    fun testAnalyticsNeverAltersPersistedTransactionsOnDevice() = runBlocking {
        val now = System.currentTimeMillis()
        val id = expenseDao.insert(
            Expense(
                amount = 1234.56,
                merchant = "Unchanged Merchant",
                dateMillis = now,
                type = "Debit",
                notificationKey = "device_immutable_key",
                isPending = false,
                rawText = "Raw device text",
                tag = "Original Tag",
                note = "Original Note",
                source = "NOTIFICATION",
                relationshipType = "NONE",
                relationshipId = null,
                accountId = "device_acc_1",
                accountSuffix = "8888",
                categoryId = "groceries",
                categorySource = CategorySource.USER_ASSIGNED
            )
        ).toInt()

        val before = expenseDao.getExpenseById(id)
        assertNotNull(before)

        // Run analytics across all queries
        analyticsManager.getAnalyticsReport(AnalyticsDateRange.TODAY)
        analyticsManager.getAnalyticsReport(AnalyticsDateRange.THIS_WEEK)
        analyticsManager.getAnalyticsReport(AnalyticsDateRange.THIS_MONTH)
        analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        analyticsManager.getAnalyticsReport(AnalyticsDateRange.CUSTOM, customStartMillis = now - 1000, customEndMillis = now + 1000)

        val after = expenseDao.getExpenseById(id)
        assertNotNull(after)

        assertEquals(before!!.id, after!!.id)
        assertEquals(before.amount, after.amount, 0.0001)
        assertEquals(before.merchant, after.merchant)
        assertEquals(before.dateMillis, after.dateMillis)
        assertEquals(before.type, after.type)
        assertEquals(before.notificationKey, after.notificationKey)
        assertEquals(before.isPending, after.isPending)
        assertEquals(before.rawText, after.rawText)
        assertEquals(before.tag, after.tag)
        assertEquals(before.note, after.note)
        assertEquals(before.source, after.source)
        assertEquals(before.relationshipType, after.relationshipType)
        assertEquals(before.relationshipId, after.relationshipId)
        assertEquals(before.accountId, after.accountId)
        assertEquals(before.accountSuffix, after.accountSuffix)
        assertEquals(before.categoryId, after.categoryId)
        assertEquals(before.categorySource, after.categorySource)
    }
}
