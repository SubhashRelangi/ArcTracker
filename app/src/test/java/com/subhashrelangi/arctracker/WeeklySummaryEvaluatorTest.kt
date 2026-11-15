package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.TransactionCategory
import com.subhashrelangi.arctracker.data.TransactionRelationshipType
import com.subhashrelangi.arctracker.engine.notification.NotificationChannelManager
import com.subhashrelangi.arctracker.engine.notification.WeeklySummaryEvaluator
import org.junit.Assert.*
import org.junit.Test

class WeeklySummaryEvaluatorTest {

    private val now = 1774000000000L // Approx March 2026
    private val oneDay = 24L * 60 * 60 * 1000

    @Test
    fun testCalculateWeekBounds() {
        val (start, end) = WeeklySummaryEvaluator.calculateWeekBounds(now)
        assertEquals(now, end)
        assertTrue(end > start)
        val diffDays = (end - start) / oneDay
        assertTrue(diffDays in 7..8)
    }

    @Test
    fun testEvaluateWeeklySummary_filtersSettledDebitsInWeek() {
        val expenses = listOf(
            // 2 days ago: Food
            Expense(
                id = 1,
                amount = 1200.0,
                merchant = "Swiggy",
                dateMillis = now - (2 * oneDay),
                type = "Debit",
                categoryId = "food",
                isPending = false
            ),
            // 4 days ago: Groceries
            Expense(
                id = 2,
                amount = 2500.0,
                merchant = "Blinkit",
                dateMillis = now - (4 * oneDay),
                type = "Debit",
                categoryId = "groceries",
                isPending = false
            ),
            // 5 days ago: Pending debit -> Must be excluded
            Expense(
                id = 3,
                amount = 400.0,
                merchant = "Uber",
                dateMillis = now - (5 * oneDay),
                type = "Debit",
                categoryId = "transport",
                isPending = true
            ),
            // 3 days ago: Self transfer -> Must be excluded
            Expense(
                id = 4,
                amount = 10000.0,
                merchant = "To HDFC",
                dateMillis = now - (3 * oneDay),
                type = "Debit",
                relationshipType = TransactionRelationshipType.SELF_TRANSFER,
                isPending = false
            ),
            // 1 day ago: Credit -> Must be excluded
            Expense(
                id = 5,
                amount = 50000.0,
                merchant = "Salary",
                dateMillis = now - (1 * oneDay),
                type = "Credit",
                isPending = false
            ),
            // 10 days ago: Outside week window -> Must be excluded
            Expense(
                id = 6,
                amount = 3000.0,
                merchant = "Amazon",
                dateMillis = now - (10 * oneDay),
                type = "Debit",
                categoryId = "shopping",
                isPending = false
            )
        )

        val categories = listOf(
            TransactionCategory(id = "food", name = "Food & Dining"),
            TransactionCategory(id = "groceries", name = "Groceries & Daily")
        )

        val summary = WeeklySummaryEvaluator.evaluateWeeklySummary(expenses, categories, now)
        assertEquals(3700.0, summary.totalSpent, 0.001)
        assertEquals(2, summary.transactionCount)
        assertEquals(2, summary.topCategories.size)
        assertEquals("Groceries & Daily", summary.topCategories[0].categoryName)
        assertEquals(2500.0, summary.topCategories[0].amount, 0.001)
    }

    @Test
    fun testBuildWeeklySummaryPayload() {
        val summary = WeeklySummaryEvaluator.WeeklySummaryData(
            totalSpent = 6800.0,
            transactionCount = 14,
            topCategories = listOf(
                com.subhashrelangi.arctracker.engine.notification.DailyDigestEvaluator.CategorySpend("Food & Dining", 4200.0),
                com.subhashrelangi.arctracker.engine.notification.DailyDigestEvaluator.CategorySpend("Transport", 2600.0)
            )
        )

        val payload = WeeklySummaryEvaluator.buildWeeklySummaryPayload(summary)
        assertTrue(payload.title.contains("Weekly Spend Summary"))
        assertTrue(payload.message.contains("14 transactions this week"))
        assertEquals("Analytics", payload.targetDestination)
        assertEquals(NotificationChannelManager.CHANNEL_DAILY_DIGEST, payload.channelId)
        assertTrue(payload.bigText.contains("Food & Dining"))
    }
}
