package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.service.AnalyticsDateRange
import com.subhashrelangi.arctracker.service.AnalyticsReport
import com.subhashrelangi.arctracker.service.AnalyticsSummary
import com.subhashrelangi.arctracker.service.CategoryAnalytics
import com.subhashrelangi.arctracker.service.MerchantAnalytics
import com.subhashrelangi.arctracker.ui.insights.InsightsCalculations
import com.subhashrelangi.arctracker.ui.insights.InsightsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsRebuildTest {

    @Test
    fun testNetPositionVelocityCalculations() {
        val summary = AnalyticsSummary(
            totalExpenses = 58420.0,
            totalIncome = 145000.0,
            netAmount = 86580.0,
            totalTransactionCount = 56,
            expenseCount = 50,
            incomeCount = 6
        )
        val report = AnalyticsReport.empty(AnalyticsDateRange.THIS_MONTH).copy(
            summary = summary
        )

        val velocity = InsightsCalculations.computeNetPositionVelocity(report)

        assertEquals(86580.0, velocity.netAmount, 0.001)
        assertEquals(145000.0, velocity.totalInflow, 0.001)
        assertEquals(58420.0, velocity.totalOutflow, 0.001)
        // Savings rate = (145000 - 58420) / 145000 = 86580 / 145000 = 59.71%
        assertEquals(59.71, velocity.savingsRate, 0.1)
        assertEquals("Balanced", velocity.savingsStatus)
        assertTrue(velocity.isDailyRatePositive)
        assertTrue(velocity.dailyRunRate > 0.0)
    }

    @Test
    fun testCategoryAllocationCalculations() {
        val categories = listOf(
            CategoryAnalytics(
                categoryId = "food",
                categoryName = "Food & Dining",
                totalAmount = 18450.0,
                percentage = 31.6,
                transactionCount = 34
            ),
            CategoryAnalytics(
                categoryId = "shopping",
                categoryName = "Shopping & Retail",
                totalAmount = 14200.0,
                percentage = 24.3,
                transactionCount = 12
            )
        )
        val report = AnalyticsReport.empty(AnalyticsDateRange.THIS_MONTH).copy(
            summary = AnalyticsSummary(totalExpenses = 58420.0, totalTransactionCount = 46),
            categoryBreakdown = categories
        )

        val expenses = listOf(
            Expense(
                id = 1,
                amount = 450.0,
                merchant = "Swiggy",
                dateMillis = System.currentTimeMillis(),
                categoryId = "food",
                rawText = "Paid via UPI",
                source = "NOTIFICATION"
            )
        )

        val allocation = InsightsCalculations.computeCategoryAllocation(report, expenses)

        assertFalse(allocation.isEmpty)
        assertEquals("Food & Dining", allocation.primaryDriverName)
        assertEquals(31.6, allocation.primaryDriverPercentage, 0.01)
        assertEquals(2, allocation.categories.size)
        assertEquals("58.4k", allocation.totalSpendFormatted)
        assertTrue(allocation.primaryDriverDetails.contains("31.6%"))
    }

    @Test
    fun testTopPayeesCalculations() {
        val topMerchants = listOf(
            MerchantAnalytics(merchant = "Amazon India", totalAmount = 11400.0, transactionCount = 4, percentage = 19.5),
            MerchantAnalytics(merchant = "Swiggy", totalAmount = 8250.0, transactionCount = 14, percentage = 14.1),
            MerchantAnalytics(merchant = "Reliance Retail", totalAmount = 6100.0, transactionCount = 3, percentage = 10.4),
            MerchantAnalytics(merchant = "Shell Fuel", totalAmount = 4500.0, transactionCount = 2, percentage = 7.7)
        )
        val report = AnalyticsReport.empty(AnalyticsDateRange.THIS_MONTH).copy(
            topMerchants = topMerchants
        )

        val expenses = listOf(
            Expense(id = 1, amount = 2850.0, merchant = "Amazon India", dateMillis = 1000L, source = "NOTIFICATION", rawText = "UPI transaction"),
            Expense(id = 2, amount = 2033.0, merchant = "Reliance Retail", dateMillis = 2000L, accountId = "HDFC", accountSuffix = "1024")
        )

        val payees = InsightsCalculations.computeTopPayees(report, expenses)

        assertEquals(4, payees.size)
        assertEquals("Amazon India", payees[0].name)
        assertEquals(11400.0, payees[0].amount, 0.001)
        assertEquals(2850.0, payees[0].averageAmount, 0.001)
        assertEquals("shopping", payees[0].iconType)

        assertEquals("Reliance Retail", payees[2].name)
        assertEquals("retail", payees[2].iconType)
        assertEquals("HDFC ••1024", payees[2].paymentBadge)

        assertEquals("Shell Fuel", payees[3].name)
        assertEquals("fuel", payees[3].iconType)
    }

    @Test
    fun testAutomationHealthCalculations() {
        val expenses = listOf(
            Expense(id = 1, amount = 100.0, merchant = "M1", dateMillis = 1000L, source = "NOTIFICATION"),
            Expense(id = 2, amount = 200.0, merchant = "M2", dateMillis = 2000L, source = "NOTIFICATION"),
            Expense(id = 3, amount = 300.0, merchant = "M3", dateMillis = 3000L, source = "SMS"),
            Expense(id = 4, amount = 400.0, merchant = "M4", dateMillis = 4000L, source = "MANUAL")
        )
        val report = AnalyticsReport.empty(AnalyticsDateRange.THIS_MONTH)

        val health = InsightsCalculations.computeAutomationHealth(expenses, report)

        assertEquals(4, health.totalEvents)
        assertEquals(3, health.automatedCount) // 2 notif + 1 sms
        assertEquals(75, health.handsFreePercentage) // 3/4 = 75%
        assertEquals(50, health.notificationPercentage) // 2/4 = 50%
        assertEquals(25, health.smsPercentage) // 1/4 = 25%
        assertEquals(25, health.manualPercentage) // 1/4 = 25%
    }

    @Test
    fun testHashIntegrityCalculations() {
        val expenses = listOf(
            Expense(id = 1, amount = 100.0, merchant = "M1", dateMillis = 1000L, notificationKey = "sha_abc123"),
            Expense(id = 2, amount = 200.0, merchant = "M2", dateMillis = 2000L, relationshipId = "rel_xyz")
        )

        val hashData = InsightsCalculations.computeHashIntegrity(expenses)

        assertEquals(2, hashData.duplicateCount)
        assertEquals(100, hashData.integrityPercentage)
        assertTrue(hashData.description.contains("2 potential duplicate intents"))
        assertTrue(hashData.description.contains("SHA-256 fingerprinting"))
    }

    @Test
    fun testSemanticCategoryColors() {
        assertEquals(InsightsTheme.CategoryFood, InsightsTheme.resolveCategoryColor("Food & Dining"))
        assertEquals(InsightsTheme.CategoryShopping, InsightsTheme.resolveCategoryColor("Shopping & Retail"))
        assertEquals(InsightsTheme.CategoryBills, InsightsTheme.resolveCategoryColor("Bills & Utilities"))
        assertEquals(InsightsTheme.CategoryTransport, InsightsTheme.resolveCategoryColor("Transport & Commute"))
        assertEquals(InsightsTheme.CategoryEntertainment, InsightsTheme.resolveCategoryColor("Entertainment & Subs"))
        assertEquals(InsightsTheme.CategoryHealth, InsightsTheme.resolveCategoryColor("Medical & Health"))
        assertEquals(InsightsTheme.CategoryMisc, InsightsTheme.resolveCategoryColor("Others / Misc"))
    }
}
