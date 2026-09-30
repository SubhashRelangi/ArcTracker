package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * Comprehensive JVM Unit Tests for Milestone 13: Analytics.
 *
 * Verifies all 22 required test cases:
 * 1. Empty database
 * 2. Date range filtering
 * 3. Expense totals
 * 4. Income totals
 * 5. Net calculation
 * 6. Category totals
 * 7. Uncategorized totals
 * 8. Monthly aggregation
 * 9. Merchant aggregation
 * 10. Category-source aggregation
 * 11. Multiple categories
 * 12. Multiple months
 * 13. Same merchant across transactions
 * 14. Self-transfer handling
 * 15. Pending transaction handling according to existing semantics
 * 16. Account aggregation
 * 17. Custom categories
 * 18. Archived/deleted category behavior
 * 19. Large dataset aggregation
 * 20. Boundary dates
 * 21. Invalid/empty custom range handling
 * 22. Analytics does not modify transactions
 */
class AnalyticsManagerTest {

    private lateinit var expenseDao: FakeExpenseDao
    private lateinit var categoryDao: FakeTransactionCategoryDao
    private lateinit var accountDao: FakeKnownFinancialAccountDao
    private lateinit var analyticsManager: AnalyticsManager

    private val baseTimestamp = 1774000000000L // arbitrary fixed epoch (around Mar 2026)

    @Before
    fun setUp() {
        expenseDao = FakeExpenseDao()
        categoryDao = FakeTransactionCategoryDao()
        accountDao = FakeKnownFinancialAccountDao()
        analyticsManager = AnalyticsManager(expenseDao, categoryDao, accountDao)
    }

    // 1. Empty database
    @Test
    fun test1_emptyDatabase() = runBlocking {
        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        assertTrue(report.isEmpty)
        assertEquals(0.0, report.summary.totalExpenses, 0.001)
        assertEquals(0.0, report.summary.totalIncome, 0.001)
        assertEquals(0.0, report.summary.netAmount, 0.001)
        assertEquals(0, report.summary.totalTransactionCount)
        assertTrue(report.categoryBreakdown.isEmpty())
        assertTrue(report.monthlyTrends.isEmpty())
        assertTrue(report.topMerchants.isEmpty())
        assertTrue(report.categorySources.isEmpty())
        assertTrue(report.accountBreakdown.isEmpty())
    }

    // 2. Date range filtering
    @Test
    fun test2_dateRangeFiltering() = runBlocking {
        val t1 = baseTimestamp
        val t2 = baseTimestamp + 100_000L
        val t3 = baseTimestamp + 200_000L

        expenseDao.insert(Expense(id = 1, amount = 100.0, merchant = "M1", dateMillis = t1, type = "Debit"))
        expenseDao.insert(Expense(id = 2, amount = 200.0, merchant = "M2", dateMillis = t2, type = "Debit"))
        expenseDao.insert(Expense(id = 3, amount = 300.0, merchant = "M3", dateMillis = t3, type = "Debit"))

        val report = analyticsManager.getAnalyticsReport(
            dateRange = AnalyticsDateRange.CUSTOM,
            customStartMillis = t1 + 50_000L,
            customEndMillis = t2 + 50_000L
        )

        assertEquals(1, report.summary.totalTransactionCount)
        assertEquals(200.0, report.summary.totalExpenses, 0.001)
        assertEquals("M2", report.topMerchants.first().merchant)
    }

    // 3. Expense totals
    @Test
    fun test3_expenseTotals() = runBlocking {
        expenseDao.insert(Expense(id = 1, amount = 150.50, merchant = "Store A", dateMillis = baseTimestamp, type = "Debit"))
        expenseDao.insert(Expense(id = 2, amount = 49.50, merchant = "Store B", dateMillis = baseTimestamp, type = "Debit"))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        assertEquals(200.0, report.summary.totalExpenses, 0.001)
        assertEquals(2, report.summary.expenseCount)
    }

    // 4. Income totals
    @Test
    fun test4_incomeTotals() = runBlocking {
        expenseDao.insert(Expense(id = 1, amount = 50000.0, merchant = "Employer", dateMillis = baseTimestamp, type = "Credit"))
        expenseDao.insert(Expense(id = 2, amount = 1200.0, merchant = "Dividend", dateMillis = baseTimestamp, type = "Credit"))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        assertEquals(51200.0, report.summary.totalIncome, 0.001)
        assertEquals(2, report.summary.incomeCount)
    }

    // 5. Net calculation
    @Test
    fun test5_netCalculation() = runBlocking {
        expenseDao.insert(Expense(id = 1, amount = 2000.0, merchant = "Salary", dateMillis = baseTimestamp, type = "Credit"))
        expenseDao.insert(Expense(id = 2, amount = 650.0, merchant = "Groceries", dateMillis = baseTimestamp, type = "Debit"))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        assertEquals(2000.0, report.summary.totalIncome, 0.001)
        assertEquals(650.0, report.summary.totalExpenses, 0.001)
        assertEquals(1350.0, report.summary.netAmount, 0.001)
    }

    // 6. Category totals
    @Test
    fun test6_categoryTotals() = runBlocking {
        expenseDao.insert(Expense(id = 1, amount = 100.0, merchant = "Swiggy", dateMillis = baseTimestamp, type = "Debit", categoryId = "food_dining"))
        expenseDao.insert(Expense(id = 2, amount = 200.0, merchant = "Zomato", dateMillis = baseTimestamp, type = "Debit", categoryId = "food_dining"))
        expenseDao.insert(Expense(id = 3, amount = 100.0, merchant = "Uber", dateMillis = baseTimestamp, type = "Debit", categoryId = "transport"))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        val foodCat = report.categoryBreakdown.find { it.categoryId == "food_dining" }
        assertNotNull(foodCat)
        assertEquals(300.0, foodCat!!.totalAmount, 0.001)
        assertEquals(2, foodCat.transactionCount)
        assertEquals(75.0, foodCat.percentage, 0.001) // 300 / 400 = 75%

        val transCat = report.categoryBreakdown.find { it.categoryId == "transport" }
        assertNotNull(transCat)
        assertEquals(100.0, transCat!!.totalAmount, 0.001)
        assertEquals(1, transCat.transactionCount)
        assertEquals(25.0, transCat.percentage, 0.001) // 100 / 400 = 25%
    }

    // 7. Uncategorized totals
    @Test
    fun test7_uncategorizedTotals() = runBlocking {
        expenseDao.insert(Expense(id = 1, amount = 50.0, merchant = "Vendor A", dateMillis = baseTimestamp, type = "Debit", categoryId = null))
        expenseDao.insert(Expense(id = 2, amount = 150.0, merchant = "Vendor B", dateMillis = baseTimestamp, type = "Debit", categoryId = ""))
        expenseDao.insert(Expense(id = 3, amount = 200.0, merchant = "Supermarket", dateMillis = baseTimestamp, type = "Debit", categoryId = "groceries"))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        assertEquals(2, report.uncategorized.transactionCount)
        assertEquals(200.0, report.uncategorized.totalAmount, 0.001)
        assertEquals(50.0, report.uncategorized.percentage, 0.001) // 200 / 400 = 50%
    }

    // 8. Monthly aggregation
    @Test
    fun test8_monthlyAggregation() = runBlocking {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 15, 12, 0)
        }
        val janTime = cal.timeInMillis
        cal.set(2026, Calendar.FEBRUARY, 15, 12, 0)
        val febTime = cal.timeInMillis

        expenseDao.insert(Expense(id = 1, amount = 100.0, merchant = "Jan Buy", dateMillis = janTime, type = "Debit"))
        expenseDao.insert(Expense(id = 2, amount = 500.0, merchant = "Jan Income", dateMillis = janTime, type = "Credit"))
        expenseDao.insert(Expense(id = 3, amount = 250.0, merchant = "Feb Buy", dateMillis = febTime, type = "Debit"))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        assertEquals(2, report.monthlyTrends.size)

        val jan = report.monthlyTrends.find { it.yearMonth == "2026-01" }
        assertNotNull(jan)
        assertEquals(100.0, jan!!.totalExpenses, 0.001)
        assertEquals(500.0, jan.totalIncome, 0.001)
        assertEquals(400.0, jan.netAmount, 0.001)

        val feb = report.monthlyTrends.find { it.yearMonth == "2026-02" }
        assertNotNull(feb)
        assertEquals(250.0, feb!!.totalExpenses, 0.001)
        assertEquals(0.0, feb.totalIncome, 0.001)
        assertEquals(-250.0, feb.netAmount, 0.001)
    }

    // 9. Merchant aggregation
    @Test
    fun test9_merchantAggregation() = runBlocking {
        expenseDao.insert(Expense(id = 1, amount = 500.0, merchant = "Amazon", dateMillis = baseTimestamp, type = "Debit"))
        expenseDao.insert(Expense(id = 2, amount = 300.0, merchant = "Amazon", dateMillis = baseTimestamp, type = "Debit"))
        expenseDao.insert(Expense(id = 3, amount = 200.0, merchant = "Swiggy", dateMillis = baseTimestamp, type = "Debit"))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        assertEquals(2, report.topMerchants.size)
        val top = report.topMerchants[0]
        assertEquals("Amazon", top.merchant)
        assertEquals(800.0, top.totalAmount, 0.001)
        assertEquals(2, top.transactionCount)
        assertEquals(80.0, top.percentage, 0.001) // 800 / 1000 = 80%

        val second = report.topMerchants[1]
        assertEquals("Swiggy", second.merchant)
        assertEquals(200.0, second.totalAmount, 0.001)
        assertEquals(1, second.transactionCount)
        assertEquals(20.0, second.percentage, 0.001)
    }

    // 10. Category-source aggregation
    @Test
    fun test10_categorySourceAggregation() = runBlocking {
        expenseDao.insert(Expense(id = 1, amount = 100.0, merchant = "M1", dateMillis = baseTimestamp, type = "Debit", categorySource = CategorySource.USER_ASSIGNED))
        expenseDao.insert(Expense(id = 2, amount = 200.0, merchant = "M2", dateMillis = baseTimestamp, type = "Debit", categorySource = CategorySource.INFERRED))
        expenseDao.insert(Expense(id = 3, amount = 300.0, merchant = "M3", dateMillis = baseTimestamp, type = "Debit", categorySource = CategorySource.NONE))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        val userAssigned = report.categorySources.find { it.source == CategorySource.USER_ASSIGNED }
        assertNotNull(userAssigned)
        assertEquals(1, userAssigned!!.transactionCount)
        assertEquals(100.0, userAssigned.totalAmount, 0.001)

        val inferred = report.categorySources.find { it.source == CategorySource.INFERRED }
        assertNotNull(inferred)
        assertEquals(1, inferred!!.transactionCount)
        assertEquals(200.0, inferred.totalAmount, 0.001)

        val none = report.categorySources.find { it.source == CategorySource.NONE }
        assertNotNull(none)
        assertEquals(1, none!!.transactionCount)
        assertEquals(300.0, none.totalAmount, 0.001)
    }

    // 11. Multiple categories
    @Test
    fun test11_multipleCategories() = runBlocking {
        expenseDao.insert(Expense(id = 1, amount = 100.0, merchant = "Doc", dateMillis = baseTimestamp, type = "Debit", categoryId = "healthcare"))
        expenseDao.insert(Expense(id = 2, amount = 200.0, merchant = "Cinema", dateMillis = baseTimestamp, type = "Debit", categoryId = "entertainment"))
        expenseDao.insert(Expense(id = 3, amount = 300.0, merchant = "Flight", dateMillis = baseTimestamp, type = "Debit", categoryId = "travel"))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        assertEquals(3, report.categoryBreakdown.size)
        // Verify ordered by amount descending
        assertEquals("travel", report.categoryBreakdown[0].categoryId)
        assertEquals("entertainment", report.categoryBreakdown[1].categoryId)
        assertEquals("healthcare", report.categoryBreakdown[2].categoryId)
    }

    // 12. Multiple months
    @Test
    fun test12_multipleMonths() = runBlocking {
        val cal = Calendar.getInstance()
        for (month in 0..5) {
            cal.set(2026, month, 10, 10, 0)
            expenseDao.insert(
                Expense(
                    id = month + 1,
                    amount = (month + 1) * 100.0,
                    merchant = "Month-$month",
                    dateMillis = cal.timeInMillis,
                    type = "Debit",
                    categoryId = "groceries"
                )
            )
        }

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        assertEquals(6, report.monthlyTrends.size)
        // Verify ascending chronological order
        assertEquals("2026-01", report.monthlyTrends[0].yearMonth)
        assertEquals("2026-06", report.monthlyTrends[5].yearMonth)
        // Verify category trends inside month
        assertEquals(1, report.monthlyTrends[0].categoryTrends.size)
        assertEquals("groceries", report.monthlyTrends[0].categoryTrends[0].categoryId)
    }

    // 13. Same merchant across transactions
    @Test
    fun test13_sameMerchantAcrossTransactions() = runBlocking {
        expenseDao.insert(Expense(id = 1, amount = 120.0, merchant = " Starbucks ", dateMillis = baseTimestamp, type = "Debit"))
        expenseDao.insert(Expense(id = 2, amount = 230.0, merchant = "Starbucks", dateMillis = baseTimestamp + 1000, type = "Debit"))
        expenseDao.insert(Expense(id = 3, amount = 150.0, merchant = "Starbucks ", dateMillis = baseTimestamp + 2000, type = "Debit"))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        assertEquals(1, report.topMerchants.size)
        assertEquals("Starbucks", report.topMerchants[0].merchant)
        assertEquals(500.0, report.topMerchants[0].totalAmount, 0.001)
        assertEquals(3, report.topMerchants[0].transactionCount)
    }

    // 14. Self-transfer handling
    @Test
    fun test14_selfTransferHandling() = runBlocking {
        // Normal spending
        expenseDao.insert(Expense(id = 1, amount = 500.0, merchant = "Shopper", dateMillis = baseTimestamp, type = "Debit", categoryId = "shopping"))
        // Self-transfer debit & credit legs
        expenseDao.insert(Expense(
            id = 2,
            amount = 10000.0,
            merchant = "Self Transfer",
            dateMillis = baseTimestamp,
            type = "Debit",
            relationshipType = TransactionRelationshipType.SELF_TRANSFER,
            relationshipId = "transfer_101"
        ))
        expenseDao.insert(Expense(
            id = 3,
            amount = 10000.0,
            merchant = "Self Transfer",
            dateMillis = baseTimestamp,
            type = "Credit",
            relationshipType = TransactionRelationshipType.SELF_TRANSFER,
            relationshipId = "transfer_101"
        ))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        // Self-transfers must NOT inflate total expenses, income, net, or merchants
        assertEquals(500.0, report.summary.totalExpenses, 0.001)
        assertEquals(0.0, report.summary.totalIncome, 0.001)
        assertEquals(-500.0, report.summary.netAmount, 0.001)
        assertEquals(2, report.summary.selfTransferCount)
        assertEquals(10000.0, report.summary.selfTransferAmount, 0.001)

        // Category breakdown must not contain self-transfers
        assertEquals(1, report.categoryBreakdown.size)
        assertEquals("shopping", report.categoryBreakdown[0].categoryId)
        assertEquals(500.0, report.categoryBreakdown[0].totalAmount, 0.001)

        // Merchant ranking must not contain self-transfers
        assertEquals(1, report.topMerchants.size)
        assertEquals("Shopper", report.topMerchants[0].merchant)
    }

    // 15. Pending transaction handling according to existing semantics
    @Test
    fun test15_pendingTransactionHandling() = runBlocking {
        expenseDao.insert(Expense(id = 1, amount = 300.0, merchant = "Settled Debit", dateMillis = baseTimestamp, type = "Debit", isPending = false))
        expenseDao.insert(Expense(id = 2, amount = 700.0, merchant = "Pending Debit", dateMillis = baseTimestamp, type = "Debit", isPending = true))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        // Settled totals exclude pending
        assertEquals(300.0, report.summary.totalExpenses, 0.001)
        assertEquals(1, report.summary.expenseCount)
        // Pending reported separately
        assertEquals(1, report.summary.pendingCount)
        assertEquals(700.0, report.summary.pendingAmount, 0.001)
    }

    // 16. Account aggregation
    @Test
    fun test16_accountAggregation() = runBlocking {
        val account = KnownFinancialAccount(
            id = "hdfc_bank_1234",
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "1234",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        accountDao.insert(account)

        expenseDao.insert(Expense(id = 1, amount = 400.0, merchant = "M1", dateMillis = baseTimestamp, type = "Debit", accountId = "hdfc_bank_1234", accountSuffix = "1234"))
        expenseDao.insert(Expense(id = 2, amount = 1000.0, merchant = "M2", dateMillis = baseTimestamp, type = "Credit", accountId = "hdfc_bank_1234", accountSuffix = "1234"))
        expenseDao.insert(Expense(id = 3, amount = 150.0, merchant = "M3", dateMillis = baseTimestamp, type = "Debit", accountId = null, accountSuffix = null))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        assertEquals(2, report.accountBreakdown.size)

        val hdfcAcc = report.accountBreakdown.find { it.accountId == "hdfc_bank_1234" }
        assertNotNull(hdfcAcc)
        assertEquals("HDFC Bank", hdfcAcc!!.institutionName)
        assertEquals("1234", hdfcAcc.accountSuffix)
        assertEquals(400.0, hdfcAcc.totalExpenses, 0.001)
        assertEquals(1000.0, hdfcAcc.totalIncome, 0.001)
        assertEquals(600.0, hdfcAcc.netAmount, 0.001)

        val unresAcc = report.accountBreakdown.find { it.accountId == null }
        assertNotNull(unresAcc)
        assertEquals("Unresolved Account", unresAcc!!.institutionName)
        assertEquals(150.0, unresAcc.totalExpenses, 0.001)
    }

    // 17. Custom categories
    @Test
    fun test17_customCategories() = runBlocking {
        val customCat = TransactionCategory(
            id = "custom_fitness",
            name = "Fitness & Gym",
            iconKey = "fitness_center",
            colorKey = "teal",
            isSystem = false
        )
        categoryDao.upsert(customCat)

        expenseDao.insert(Expense(id = 1, amount = 2500.0, merchant = "Gold's Gym", dateMillis = baseTimestamp, type = "Debit", categoryId = "custom_fitness"))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        val fit = report.categoryBreakdown.find { it.categoryId == "custom_fitness" }
        assertNotNull(fit)
        assertEquals("Fitness & Gym", fit!!.categoryName)
        assertEquals("fitness_center", fit.iconKey)
        assertEquals("teal", fit.colorKey)
        assertFalse(fit.isSystem)
    }

    // 18. Archived/deleted category behavior
    @Test
    fun test18_archivedDeletedCategoryBehavior() = runBlocking {
        // Archived category
        val archivedCat = TransactionCategory(
            id = "archived_hobbies",
            name = "Old Hobbies",
            iconKey = "palette",
            colorKey = "purple",
            isArchived = true
        )
        categoryDao.upsert(archivedCat)
        expenseDao.insert(Expense(id = 1, amount = 120.0, merchant = "Art Store", dateMillis = baseTimestamp, type = "Debit", categoryId = "archived_hobbies"))

        // Category deleted from registry but id/tag still persisted on expense
        expenseDao.insert(Expense(id = 2, amount = 80.0, merchant = "Book Store", dateMillis = baseTimestamp, type = "Debit", categoryId = "deleted_books", tag = "Books"))

        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        val arc = report.categoryBreakdown.find { it.categoryId == "archived_hobbies" }
        assertNotNull(arc)
        assertTrue(arc!!.isArchived)
        assertEquals("Old Hobbies", arc.categoryName)

        val del = report.categoryBreakdown.find { it.categoryId == "deleted_books" }
        assertNotNull(del)
        assertEquals("Books", del!!.categoryName)
    }

    // 19. Large dataset aggregation
    @Test
    fun test19_largeDatasetAggregation() = runBlocking {
        val largeList = (1..5000).map { i ->
            Expense(
                id = i,
                amount = 10.0,
                merchant = "Merchant_${i % 20}",
                dateMillis = baseTimestamp + i * 1000L,
                type = if (i % 5 == 0) "Credit" else "Debit",
                categoryId = if (i % 2 == 0) "food_dining" else "shopping"
            )
        }
        expenseDao.insertAll(largeList)

        val start = System.currentTimeMillis()
        val report = analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        val elapsed = System.currentTimeMillis() - start

        assertEquals(5000, report.summary.totalTransactionCount)
        assertEquals(4000, report.summary.expenseCount)
        assertEquals(1000, report.summary.incomeCount)
        assertEquals(40000.0, report.summary.totalExpenses, 0.001)
        assertEquals(10000.0, report.summary.totalIncome, 0.001)
        assertTrue("Large aggregation should be fast (< 2000ms), took ${elapsed}ms", elapsed < 2000)
    }

    // 20. Boundary dates
    @Test
    fun test20_boundaryDates() = runBlocking {
        val startBound = 10000L
        val endBound = 20000L

        // Exactly on boundaries
        expenseDao.insert(Expense(id = 1, amount = 10.0, merchant = "Start", dateMillis = startBound, type = "Debit"))
        expenseDao.insert(Expense(id = 2, amount = 20.0, merchant = "End", dateMillis = endBound, type = "Debit"))
        // Outside boundaries
        expenseDao.insert(Expense(id = 3, amount = 30.0, merchant = "Before", dateMillis = startBound - 1L, type = "Debit"))
        expenseDao.insert(Expense(id = 4, amount = 40.0, merchant = "After", dateMillis = endBound + 1L, type = "Debit"))

        val report = analyticsManager.getAnalyticsReport(
            dateRange = AnalyticsDateRange.CUSTOM,
            customStartMillis = startBound,
            customEndMillis = endBound
        )

        assertEquals(2, report.summary.totalTransactionCount)
        assertEquals(30.0, report.summary.totalExpenses, 0.001)
    }

    // 21. Invalid/empty custom range handling
    @Test
    fun test21_invalidEmptyCustomRangeHandling() = runBlocking {
        expenseDao.insert(Expense(id = 1, amount = 100.0, merchant = "M1", dateMillis = 15000L, type = "Debit"))

        // Inverted range (start > end) should normalize safely without crashing
        val reportInverted = analyticsManager.getAnalyticsReport(
            dateRange = AnalyticsDateRange.CUSTOM,
            customStartMillis = 20000L,
            customEndMillis = 10000L
        )
        assertEquals(1, reportInverted.summary.totalTransactionCount)
        assertEquals(100.0, reportInverted.summary.totalExpenses, 0.001)

        // Single instant range (start == end)
        val reportSame = analyticsManager.getAnalyticsReport(
            dateRange = AnalyticsDateRange.CUSTOM,
            customStartMillis = 15000L,
            customEndMillis = 15000L
        )
        assertEquals(1, reportSame.summary.totalTransactionCount)

        // Null bounds should default safely
        val reportNull = analyticsManager.getAnalyticsReport(
            dateRange = AnalyticsDateRange.CUSTOM,
            customStartMillis = null,
            customEndMillis = null
        )
        assertEquals(1, reportNull.summary.totalTransactionCount)
    }

    // 22. Analytics does not modify transactions (CRITICAL READ-ONLY INVARIANT)
    @Test
    fun test22_analyticsDoesNotModifyTransactions() = runBlocking {
        val original = Expense(
            id = 42,
            amount = 999.99,
            merchant = "Apple Store",
            dateMillis = baseTimestamp,
            type = "Debit",
            notificationKey = "notif_key_42",
            isPending = false,
            rawText = "Raw notification text",
            tag = "Tech",
            note = "User note",
            source = "NOTIFICATION",
            relationshipType = "NONE",
            relationshipId = null,
            accountId = "acc_1",
            accountSuffix = "4321",
            categoryId = "shopping",
            categorySource = CategorySource.USER_ASSIGNED
        )
        expenseDao.insert(original)

        // Run analytics calculations across all date ranges
        analyticsManager.getAnalyticsReport(AnalyticsDateRange.ALL_TIME)
        analyticsManager.getAnalyticsReport(AnalyticsDateRange.TODAY)
        analyticsManager.getAnalyticsReport(AnalyticsDateRange.THIS_WEEK)
        analyticsManager.getAnalyticsReport(AnalyticsDateRange.THIS_MONTH)
        analyticsManager.getAnalyticsReport(AnalyticsDateRange.CUSTOM, customStartMillis = 0L, customEndMillis = Long.MAX_VALUE)

        // Fetch the record again from the database
        val afterAnalytics = expenseDao.getExpenseById(42)
        assertNotNull(afterAnalytics)

        // Verify every single field remains 100% untouched
        assertEquals(original.id, afterAnalytics!!.id)
        assertEquals(original.amount, afterAnalytics.amount, 0.0001)
        assertEquals(original.merchant, afterAnalytics.merchant)
        assertEquals(original.dateMillis, afterAnalytics.dateMillis)
        assertEquals(original.type, afterAnalytics.type)
        assertEquals(original.notificationKey, afterAnalytics.notificationKey)
        assertEquals(original.isPending, afterAnalytics.isPending)
        assertEquals(original.rawText, afterAnalytics.rawText)
        assertEquals(original.tag, afterAnalytics.tag)
        assertEquals(original.note, afterAnalytics.note)
        assertEquals(original.source, afterAnalytics.source)
        assertEquals(original.relationshipType, afterAnalytics.relationshipType)
        assertEquals(original.relationshipId, afterAnalytics.relationshipId)
        assertEquals(original.accountId, afterAnalytics.accountId)
        assertEquals(original.accountSuffix, afterAnalytics.accountSuffix)
        assertEquals(original.categoryId, afterAnalytics.categoryId)
        assertEquals(original.categorySource, afterAnalytics.categorySource)
    }

    // Reactive Flow test
    @Test
    fun testReactiveFlowUpdatesOnNewTransaction() = runBlocking {
        val flow = analyticsManager.getAnalyticsReportFlow(AnalyticsDateRange.ALL_TIME)
        val initial = flow.first()
        assertTrue(initial.isEmpty)

        // Insert new expense
        expenseDao.insert(Expense(id = 1, amount = 50.0, merchant = "Store", dateMillis = baseTimestamp, type = "Debit"))
        val updated = flow.first()
        assertEquals(1, updated.summary.totalTransactionCount)
        assertEquals(50.0, updated.summary.totalExpenses, 0.001)
    }
}
