package com.subhashrelangi.arctracker.engine.notification

import com.subhashrelangi.arctracker.data.BuiltInCategories
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.TransactionCategory
import com.subhashrelangi.arctracker.data.TransactionRelationshipType
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/**
 * Pure evaluator for Weekly Spending Summaries.
 * Decoupled from Android Framework for fast JVM testing.
 */
object WeeklySummaryEvaluator {

    const val NOTIFICATION_ID_WEEKLY_SUMMARY = 1007

    data class WeeklySummaryData(
        val totalSpent: Double,
        val transactionCount: Int,
        val topCategories: List<DailyDigestEvaluator.CategorySpend>
    )

    private fun getCurrencyFormatter(): NumberFormat {
        return NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }

    /**
     * Calculates epoch millisecond bounds for the past 7 days up to reference time.
     */
    fun calculateWeekBounds(referenceTimeMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = referenceTimeMillis
            add(Calendar.DAY_OF_YEAR, -7)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startMillis = cal.timeInMillis
        return Pair(startMillis, referenceTimeMillis)
    }

    /**
     * Evaluates total outflow, transaction count, and top 3 spending categories over the past 7 days.
     */
    fun evaluateWeeklySummary(
        expenses: List<Expense>,
        categories: List<TransactionCategory> = emptyList(),
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): WeeklySummaryData {
        val (startMillis, endMillis) = calculateWeekBounds(referenceTimeMillis)
        val categoriesMap = (categories + BuiltInCategories.ALL).associateBy { it.id }

        val weekDebits = expenses.filter { exp ->
            exp.dateMillis in startMillis..endMillis &&
                exp.type.equals("Debit", ignoreCase = true) &&
                !exp.isPending &&
                exp.relationshipType != TransactionRelationshipType.SELF_TRANSFER
        }

        val totalSpent = weekDebits.sumOf { it.amount }
        val categoryGroups = weekDebits.groupBy { exp ->
            val resolvedId = exp.categoryId?.takeIf { it.isNotBlank() }
                ?: BuiltInCategories.findLegacyMapping(exp.tag)?.id
            val cat = resolvedId?.let { categoriesMap[it] }
            cat?.name ?: exp.tag?.takeIf { it.isNotBlank() } ?: "General"
        }

        val topCategories = categoryGroups.map { (name, list) ->
            DailyDigestEvaluator.CategorySpend(name, list.sumOf { it.amount })
        }.sortedByDescending { it.amount }.take(3)

        return WeeklySummaryData(
            totalSpent = totalSpent,
            transactionCount = weekDebits.size,
            topCategories = topCategories
        )
    }

    /**
     * Builds notification payload summarizing weekly spending.
     */
    fun buildWeeklySummaryPayload(summary: WeeklySummaryData): DailyDigestEvaluator.NotificationPayload {
        val formatter = getCurrencyFormatter()
        val formattedTotal = formatter.format(summary.totalSpent).replace("Rs.", "₹").trim()
        val txCountStr = "${summary.transactionCount} transaction${if (summary.transactionCount == 1) "" else "s"}"

        val breakdownStr = if (summary.topCategories.isNotEmpty()) {
            summary.topCategories.joinToString(", ") { cat ->
                val catAmt = formatter.format(cat.amount).replace("Rs.", "₹").trim()
                "${cat.categoryName}: $catAmt"
            }
        } else ""

        val title = "📊 Weekly Spend Summary: $formattedTotal"
        val message = if (breakdownStr.isNotBlank()) {
            "$txCountStr this week • $breakdownStr"
        } else {
            "Total outflow of $formattedTotal across $txCountStr this week."
        }

        val bigText = buildString {
            append("Weekly Financial Summary:\n")
            append(" • Total Outflow: ").append(formattedTotal).append("\n")
            append(" • Logged across: ").append(txCountStr).append("\n")
            if (summary.topCategories.isNotEmpty()) {
                append("\nTop Spending Categories:\n")
                summary.topCategories.forEach { cat ->
                    val catAmt = formatter.format(cat.amount).replace("Rs.", "₹").trim()
                    append(" • ").append(cat.categoryName).append(": ").append(catAmt).append("\n")
                }
            }
            append("\nTap to view analytics, category trends, and spending velocity.")
        }.trimEnd()

        return DailyDigestEvaluator.NotificationPayload(
            title = title,
            message = message,
            bigText = bigText,
            channelId = NotificationChannelManager.CHANNEL_DAILY_DIGEST,
            notificationId = NOTIFICATION_ID_WEEKLY_SUMMARY,
            targetDestination = "Analytics"
        )
    }
}
