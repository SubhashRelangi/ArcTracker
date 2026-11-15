package com.subhashrelangi.arctracker.engine.notification

import com.subhashrelangi.arctracker.data.BuiltInCategories
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.TransactionCategory
import com.subhashrelangi.arctracker.data.TransactionRelationshipType
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/**
 * Pure domain evaluator for Daily Digest and Cash Logging notification content.
 *
 * Guarantees:
 * - 100% testable in pure JVM unit tests (no Android runtime dependency required).
 * - Excludes self-transfers, income credits, and unconfirmed pending transactions.
 * - Deterministic currency formatting with Indian numbering format (₹).
 * - Multi-category aggregation for rich multi-line notification summaries.
 */
object DailyDigestEvaluator {

    data class CategorySpend(
        val categoryName: String,
        val amount: Double
    )

    data class DailySummaryData(
        val totalSpent: Double,
        val transactionCount: Int,
        val topCategories: List<CategorySpend>,
        val isZeroSpend: Boolean,
        val isDailyLimitCrossed: Boolean,
        val dailyLimitAmount: Double?
    )

    data class NotificationPayload(
        val title: String,
        val message: String,
        val bigText: String,
        val channelId: String,
        val notificationId: Int,
        val targetDestination: String = "Transactions",
        val openAddExpenseCash: Boolean = false
    )

    private fun getCurrencyFormatter(): NumberFormat {
        return NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }

    /**
     * Calculates the millisecond boundaries for the day containing [referenceTimeMillis].
     */
    fun calculateDayBounds(referenceTimeMillis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = referenceTimeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfDay = cal.timeInMillis
        return startOfDay to endOfDay
    }

    /**
     * Evaluates today's persisted expenses and computes a [DailySummaryData].
     */
    fun evaluateDailySummary(
        expenses: List<Expense>,
        categories: List<TransactionCategory> = emptyList(),
        dailyLimitAmount: Double? = null,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): DailySummaryData {
        val (startOfDay, endOfDay) = calculateDayBounds(referenceTimeMillis)

        val categoriesMap = (categories + BuiltInCategories.ALL).associateBy { it.id }

        // Filter settled debit transactions strictly occurring today
        val todayDebits = expenses.filter { exp ->
            exp.dateMillis in startOfDay..endOfDay &&
                exp.type.equals("Debit", ignoreCase = true) &&
                !exp.isPending &&
                exp.relationshipType != TransactionRelationshipType.SELF_TRANSFER
        }

        val totalSpent = todayDebits.sumOf { it.amount }
        val isZeroSpend = todayDebits.isEmpty() || totalSpent == 0.0

        val categoryGroups = todayDebits.groupBy { exp ->
            val resolvedId = exp.categoryId?.takeIf { it.isNotBlank() }
                ?: BuiltInCategories.findLegacyMapping(exp.tag)?.id
            val cat = resolvedId?.let { categoriesMap[it] }
            cat?.name ?: exp.tag?.takeIf { it.isNotBlank() } ?: "General"
        }

        val topCategories = categoryGroups.map { (name, list) ->
            CategorySpend(name, list.sumOf { it.amount })
        }.sortedByDescending { it.amount }.take(3)

        val isDailyLimitCrossed = dailyLimitAmount != null && dailyLimitAmount > 0.0 && totalSpent > dailyLimitAmount

        return DailySummaryData(
            totalSpent = totalSpent,
            transactionCount = todayDebits.size,
            topCategories = topCategories,
            isZeroSpend = isZeroSpend,
            isDailyLimitCrossed = isDailyLimitCrossed,
            dailyLimitAmount = dailyLimitAmount
        )
    }

    /**
     * Generates the primary Nightly Expense Digest notification.
     */
    fun buildDailyDigestPayload(summary: DailySummaryData): NotificationPayload {
        val formatter = getCurrencyFormatter()

        if (summary.isZeroSpend) {
            val title = "🌟 Zero-Spend Day!"
            val msg = "You spent ₹0 today. Great job keeping your spending on track!"
            return NotificationPayload(
                title = title,
                message = msg,
                bigText = "$msg\nConsistent tracking builds financial freedom.",
                channelId = NotificationChannelManager.CHANNEL_DAILY_DIGEST,
                notificationId = 1001,
                targetDestination = "Home"
            )
        }

        val formattedTotal = formatter.format(summary.totalSpent).replace("Rs.", "₹").trim()
        val txCountStr = "${summary.transactionCount} transaction${if (summary.transactionCount == 1) "" else "s"}"

        val breakdownStr = if (summary.topCategories.isNotEmpty()) {
            summary.topCategories.joinToString(", ") { cat ->
                val catAmt = formatter.format(cat.amount).replace("Rs.", "₹").trim()
                "${cat.categoryName}: $catAmt"
            }
        } else ""

        val title = if (summary.isDailyLimitCrossed && summary.dailyLimitAmount != null) {
            val limitStr = formatter.format(summary.dailyLimitAmount).replace("Rs.", "₹").trim()
            "⚠️ Daily Limit Crossed: $formattedTotal (Target: $limitStr)"
        } else {
            "🌙 Today's Spending: $formattedTotal"
        }

        val message = if (breakdownStr.isNotBlank()) {
            "$txCountStr • $breakdownStr"
        } else {
            "$txCountStr today. Tap to review your ledger."
        }

        val bigText = buildString {
            append("Today's Total Outflow: ").append(formattedTotal).append("\n")
            append("Logged across ").append(txCountStr).append(".\n")
            if (summary.topCategories.isNotEmpty()) {
                append("\nTop Categories:\n")
                summary.topCategories.forEach { cat ->
                    val catAmt = formatter.format(cat.amount).replace("Rs.", "₹").trim()
                    append(" • ").append(cat.categoryName).append(": ").append(catAmt).append("\n")
                }
            }
            if (summary.isDailyLimitCrossed && summary.dailyLimitAmount != null) {
                val limitStr = formatter.format(summary.dailyLimitAmount).replace("Rs.", "₹").trim()
                append("\n⚠️ Warning: Exceeded your daily limit of ").append(limitStr).append(".")
            }
        }.trimEnd()

        return NotificationPayload(
            title = title,
            message = message,
            bigText = bigText,
            channelId = if (summary.isDailyLimitCrossed) NotificationChannelManager.CHANNEL_BUDGET_ALERTS else NotificationChannelManager.CHANNEL_DAILY_DIGEST,
            notificationId = 1001,
            targetDestination = "Transactions"
        )
    }

    /**
     * Generates the Liquid Cash Check-in reminder prompt.
     */
    fun buildCashReminderPayload(): NotificationPayload {
        return NotificationPayload(
            title = "💵 Any cash spent today?",
            message = "Paid for tea, groceries, or auto by liquid cash? Tap to log it in seconds.",
            bigText = "Digital apps and UPI track bank cards, but cash purchases slip through unless recorded.\n\nTap to quickly log any cash spending before you call it a day.",
            channelId = NotificationChannelManager.CHANNEL_DAILY_DIGEST,
            notificationId = 1002,
            targetDestination = "Transactions",
            openAddExpenseCash = true
        )
    }

    /**
     * Evaluates instant daily spend limit alert right after a transaction is committed.
     * Returns a [NotificationPayload] only if crossing from below target to above target.
     */
    fun buildInstantDailyLimitAlert(
        totalSpentBefore: Double,
        totalSpentNow: Double,
        dailyLimitAmount: Double
    ): NotificationPayload? {
        if (dailyLimitAmount <= 0.0) return null
        if (totalSpentBefore < dailyLimitAmount && totalSpentNow >= dailyLimitAmount) {
            val formatter = getCurrencyFormatter()
            val formattedNow = formatter.format(totalSpentNow).replace("Rs.", "₹").trim()
            val formattedLimit = formatter.format(dailyLimitAmount).replace("Rs.", "₹").trim()
            val title = "⚠️ Daily Limit Reached!"
            val message = "Today's spending reached $formattedNow, crossing your $formattedLimit daily target."
            return NotificationPayload(
                title = title,
                message = message,
                bigText = "$message\nKeep an eye on non-essential purchases for the rest of today.",
                channelId = NotificationChannelManager.CHANNEL_BUDGET_ALERTS,
                notificationId = 1003,
                targetDestination = "Budgets"
            )
        }
        return null
    }
}
