package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.TransactionCategory
import com.subhashrelangi.arctracker.data.TransactionRelationshipType
import com.subhashrelangi.arctracker.engine.notification.DailyDigestEvaluator
import com.subhashrelangi.arctracker.engine.notification.DailyNotificationScheduler
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class DailyDigestEvaluatorTest {

    private val categories = listOf(
        TransactionCategory("food", "Food & Dining", "restaurant", "orange"),
        TransactionCategory("groceries", "Groceries", "shopping_cart", "green"),
        TransactionCategory("transport", "Transport", "directions_car", "blue"),
        TransactionCategory("shopping", "Shopping", "shopping_bag", "purple")
    )

    @Test
    fun testEvaluateDailySummary_calculatesOnlyTodaySettledDebits() {
        val now = System.currentTimeMillis()
        val (startOfDay, endOfDay) = DailyDigestEvaluator.calculateDayBounds(now)

        val expenses = listOf(
            // Today's valid debits
            Expense(id = 1, amount = 450.0, merchant = "Swiggy", dateMillis = startOfDay + 10_000, type = "Debit", categoryId = "food"),
            Expense(id = 2, amount = 300.0, merchant = "Uber", dateMillis = startOfDay + 20_000, type = "Debit", categoryId = "transport"),
            Expense(id = 3, amount = 700.0, merchant = "Zepto", dateMillis = startOfDay + 30_000, type = "Debit", categoryId = "groceries"),

            // Today's credit (Income) - MUST be excluded
            Expense(id = 4, amount = 5000.0, merchant = "Salary Bonus", dateMillis = startOfDay + 40_000, type = "Credit"),

            // Today's pending - MUST be excluded
            Expense(id = 5, amount = 200.0, merchant = "Unknown", dateMillis = startOfDay + 50_000, type = "Debit", isPending = true),

            // Today's self-transfer - MUST be excluded
            Expense(id = 6, amount = 1000.0, merchant = "Savings Transfer", dateMillis = startOfDay + 60_000, type = "Debit", relationshipType = TransactionRelationshipType.SELF_TRANSFER),

            // Yesterday's debit - MUST be excluded
            Expense(id = 7, amount = 999.0, merchant = "Amazon", dateMillis = startOfDay - 100_000, type = "Debit", categoryId = "shopping")
        )

        val summary = DailyDigestEvaluator.evaluateDailySummary(
            expenses = expenses,
            categories = categories,
            dailyLimitAmount = 2000.0,
            referenceTimeMillis = now
        )

        // 450 + 300 + 700 = 1450.0
        assertEquals(1450.0, summary.totalSpent, 0.001)
        assertEquals(3, summary.transactionCount)
        assertFalse(summary.isZeroSpend)
        assertFalse(summary.isDailyLimitCrossed)

        assertEquals(3, summary.topCategories.size)
        assertEquals("Groceries", summary.topCategories[0].categoryName)
        assertEquals(700.0, summary.topCategories[0].amount, 0.001)
        assertEquals("Food & Dining", summary.topCategories[1].categoryName)
        assertEquals(450.0, summary.topCategories[1].amount, 0.001)
        assertEquals("Travel & Transport", summary.topCategories[2].categoryName)
        assertEquals(300.0, summary.topCategories[2].amount, 0.001)
    }

    @Test
    fun testEvaluateDailySummary_zeroSpendDay() {
        val now = System.currentTimeMillis()
        val summary = DailyDigestEvaluator.evaluateDailySummary(
            expenses = emptyList(),
            categories = categories,
            dailyLimitAmount = 1000.0,
            referenceTimeMillis = now
        )

        assertEquals(0.0, summary.totalSpent, 0.001)
        assertEquals(0, summary.transactionCount)
        assertTrue(summary.isZeroSpend)
        assertFalse(summary.isDailyLimitCrossed)

        val payload = DailyDigestEvaluator.buildDailyDigestPayload(summary)
        assertTrue(payload.title.contains("Zero-Spend Day"))
        assertEquals("Home", payload.targetDestination)
        assertEquals(1001, payload.notificationId)
    }

    @Test
    fun testBuildDailyDigestPayload_regularSpend() {
        val summary = DailyDigestEvaluator.DailySummaryData(
            totalSpent = 1450.0,
            transactionCount = 3,
            topCategories = listOf(
                DailyDigestEvaluator.CategorySpend("Groceries", 700.0),
                DailyDigestEvaluator.CategorySpend("Food & Dining", 450.0)
            ),
            isZeroSpend = false,
            isDailyLimitCrossed = false,
            dailyLimitAmount = 2000.0
        )

        val payload = DailyDigestEvaluator.buildDailyDigestPayload(summary)
        assertTrue(payload.title.contains("1,450"))
        assertTrue(payload.message.contains("3 transactions"))
        assertTrue(payload.message.contains("Groceries"))
        assertEquals("Transactions", payload.targetDestination)
        assertEquals(1001, payload.notificationId)
    }

    @Test
    fun testBuildDailyDigestPayload_dailyLimitCrossed() {
        val summary = DailyDigestEvaluator.DailySummaryData(
            totalSpent = 2500.0,
            transactionCount = 4,
            topCategories = listOf(
                DailyDigestEvaluator.CategorySpend("Shopping", 2000.0)
            ),
            isZeroSpend = false,
            isDailyLimitCrossed = true,
            dailyLimitAmount = 1500.0
        )

        val payload = DailyDigestEvaluator.buildDailyDigestPayload(summary)
        assertTrue(payload.title.contains("Daily Limit Crossed"))
        assertTrue(payload.title.contains("2,500"))
        assertTrue(payload.title.contains("1,500"))
    }

    @Test
    fun testBuildCashReminderPayload() {
        val payload = DailyDigestEvaluator.buildCashReminderPayload()
        assertTrue(payload.title.contains("cash"))
        assertTrue(payload.openAddExpenseCash)
        assertEquals(1002, payload.notificationId)
    }

    @Test
    fun testBuildInstantDailyLimitAlert_crossingTransition() {
        // Crossing from 1400 (below) to 1600 (above target 1500)
        val alert = DailyDigestEvaluator.buildInstantDailyLimitAlert(
            totalSpentBefore = 1400.0,
            totalSpentNow = 1600.0,
            dailyLimitAmount = 1500.0
        )
        assertNotNull(alert)
        assertTrue(alert!!.title.contains("Daily Limit Reached"))
        assertTrue(alert.message.contains("1,600"))
        assertTrue(alert.message.contains("1,500"))

        // Already above: no repeated alert
        val noAlertAlreadyAbove = DailyDigestEvaluator.buildInstantDailyLimitAlert(
            totalSpentBefore = 1600.0,
            totalSpentNow = 1800.0,
            dailyLimitAmount = 1500.0
        )
        assertNull(noAlertAlreadyAbove)

        // Still below: no alert
        val noAlertStillBelow = DailyDigestEvaluator.buildInstantDailyLimitAlert(
            totalSpentBefore = 800.0,
            totalSpentNow = 1200.0,
            dailyLimitAmount = 1500.0
        )
        assertNull(noAlertStillBelow)
    }

    @Test
    fun testCalculateNextTriggerMillis_futureTimeToday() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val ref = cal.timeInMillis

        // Requesting 21:30 when current time is 10:00 -> must trigger today
        val next = DailyNotificationScheduler.calculateNextTriggerMillis(21, 30, ref)
        assertTrue(next > ref)

        val nextCal = Calendar.getInstance().apply { timeInMillis = next }
        assertEquals(21, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, nextCal.get(Calendar.MINUTE))
        assertEquals(cal.get(Calendar.DAY_OF_YEAR), nextCal.get(Calendar.DAY_OF_YEAR))
    }

    @Test
    fun testCalculateNextTriggerMillis_pastTimeRollsToTomorrow() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val ref = cal.timeInMillis

        // Requesting 21:30 when current time is 22:00 -> must roll to tomorrow
        val next = DailyNotificationScheduler.calculateNextTriggerMillis(21, 30, ref)
        assertTrue(next > ref)

        val nextCal = Calendar.getInstance().apply { timeInMillis = next }
        assertEquals(21, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, nextCal.get(Calendar.MINUTE))
        assertEquals(cal.get(Calendar.DAY_OF_YEAR) + 1, nextCal.get(Calendar.DAY_OF_YEAR))
    }
}
