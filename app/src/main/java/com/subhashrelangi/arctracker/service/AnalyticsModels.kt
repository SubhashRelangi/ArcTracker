package com.subhashrelangi.arctracker.service

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Supported date range options for Analytics (Milestone 13).
 */
enum class AnalyticsDateRange(val label: String) {
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    LAST_3_MONTHS("Last 3 Months"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    ALL_TIME("All Time"),
    CUSTOM("Custom Range");

    companion object {
        fun calculateBounds(
            range: AnalyticsDateRange,
            referenceTimeMillis: Long = System.currentTimeMillis(),
            customStartMillis: Long? = null,
            customEndMillis: Long? = null,
            firstDayOfWeek: Int = Calendar.getInstance().firstDayOfWeek
        ): Pair<Long, Long> {
            return when (range) {
                THIS_MONTH -> {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = referenceTimeMillis
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val start = cal.timeInMillis
                    cal.add(Calendar.MONTH, 1)
                    cal.add(Calendar.MILLISECOND, -1)
                    val end = cal.timeInMillis
                    Pair(start, end)
                }
                LAST_MONTH -> {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = referenceTimeMillis
                        add(Calendar.MONTH, -1)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val start = cal.timeInMillis
                    cal.add(Calendar.MONTH, 1)
                    cal.add(Calendar.MILLISECOND, -1)
                    val end = cal.timeInMillis
                    Pair(start, end)
                }
                LAST_3_MONTHS -> {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = referenceTimeMillis
                        add(Calendar.MONTH, -2)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val start = cal.timeInMillis
                    val endCal = Calendar.getInstance().apply {
                        timeInMillis = referenceTimeMillis
                        set(Calendar.DAY_OF_MONTH, 1)
                        add(Calendar.MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        add(Calendar.MILLISECOND, -1)
                    }
                    Pair(start, endCal.timeInMillis)
                }
                TODAY -> {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = referenceTimeMillis
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val start = cal.timeInMillis
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    val end = cal.timeInMillis
                    Pair(start, end)
                }
                THIS_WEEK -> {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = referenceTimeMillis
                        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val start = cal.timeInMillis
                    cal.add(Calendar.DAY_OF_YEAR, 7)
                    cal.add(Calendar.MILLISECOND, -1)
                    val end = cal.timeInMillis
                    Pair(start, end)
                }
                ALL_TIME -> {
                    Pair(0L, Long.MAX_VALUE)
                }
                CUSTOM -> {
                    var s = customStartMillis ?: 0L
                    var e = customEndMillis ?: Long.MAX_VALUE
                    if (s > e) {
                        val tmp = s
                        s = e
                        e = tmp
                    }
                    // If end of custom range is specified and on midnight (e.g. from datepicker),
                    // expand to 23:59:59.999 so transactions on the end day are fully included.
                    if (e != Long.MAX_VALUE && e > 0L) {
                        val cal = Calendar.getInstance().apply {
                            timeInMillis = e
                            if (get(Calendar.HOUR_OF_DAY) == 0 && get(Calendar.MINUTE) == 0 && get(Calendar.SECOND) == 0) {
                                set(Calendar.HOUR_OF_DAY, 23)
                                set(Calendar.MINUTE, 59)
                                set(Calendar.SECOND, 59)
                                set(Calendar.MILLISECOND, 999)
                            }
                        }
                        e = maxOf(e, cal.timeInMillis)
                    }
                    Pair(s, e)
                }
            }
        }
    }
}

/**
 * Basic high-level financial summary for the selected period.
 */
data class AnalyticsSummary(
    val totalExpenses: Double = 0.0,
    val totalIncome: Double = 0.0,
    val netAmount: Double = 0.0,
    val totalTransactionCount: Int = 0,
    val expenseCount: Int = 0,
    val incomeCount: Int = 0,
    val pendingCount: Int = 0,
    val pendingAmount: Double = 0.0,
    val selfTransferCount: Int = 0,
    val selfTransferAmount: Double = 0.0
)

/**
 * Category-level aggregated analytics.
 */
data class CategoryAnalytics(
    val categoryId: String?,
    val categoryName: String,
    val iconKey: String = "category",
    val colorKey: String = "default",
    val totalAmount: Double = 0.0,
    val transactionCount: Int = 0,
    val percentage: Double = 0.0,
    val isSystem: Boolean = false,
    val isArchived: Boolean = false
)

/**
 * Analytics for transactions lacking a category.
 */
data class UncategorizedAnalytics(
    val transactionCount: Int = 0,
    val totalAmount: Double = 0.0,
    val percentage: Double = 0.0
)

/**
 * Category trend breakdown within a specific month.
 */
data class MonthlyCategoryTrend(
    val categoryId: String?,
    val categoryName: String,
    val amount: Double = 0.0,
    val transactionCount: Int = 0
)

/**
 * Monthly trend metrics.
 */
data class MonthlyTrendAnalytics(
    val yearMonth: String,       // e.g. "2026-09"
    val label: String,           // e.g. "Sep 2026"
    val startMillis: Long,
    val totalExpenses: Double = 0.0,
    val totalIncome: Double = 0.0,
    val netAmount: Double = 0.0,
    val transactionCount: Int = 0,
    val categoryTrends: List<MonthlyCategoryTrend> = emptyList()
)

/**
 * Top merchant ranking metrics.
 */
data class MerchantAnalytics(
    val merchant: String,
    val totalAmount: Double = 0.0,
    val transactionCount: Int = 0,
    val percentage: Double = 0.0
)

/**
 * Breakdown by canonical [CategorySource] values (USER_ASSIGNED, INFERRED, SYSTEM_ASSIGNED, NONE).
 */
data class CategorySourceAnalytics(
    val source: String,          // Matches CategorySource.USER_ASSIGNED, INFERRED, SYSTEM_ASSIGNED, or NONE
    val displayName: String,
    val transactionCount: Int = 0,
    val totalAmount: Double = 0.0,
    val percentage: Double = 0.0
)

/**
 * Aggregate metrics regarding automated categorization effectiveness.
 */
data class RuleAnalytics(
    val inferredCount: Int = 0,
    val inferredAmount: Double = 0.0,
    val userAssignedCount: Int = 0,
    val userAssignedAmount: Double = 0.0,
    val uncategorizedCount: Int = 0,
    val uncategorizedAmount: Double = 0.0,
    val autoCategorizedPercentage: Double = 0.0
)

/**
 * Account-level aggregated metrics.
 */
data class AccountAnalytics(
    val accountId: String?,
    val institutionName: String,
    val accountSuffix: String?,
    val instrumentType: String?,
    val totalExpenses: Double = 0.0,
    val totalIncome: Double = 0.0,
    val netAmount: Double = 0.0,
    val transactionCount: Int = 0
)

/**
 * Complete immutable analytics report for a selected timeframe.
 */
data class AnalyticsReport(
    val dateRange: AnalyticsDateRange,
    val startDateMillis: Long,
    val endDateMillis: Long,
    val summary: AnalyticsSummary,
    val categoryBreakdown: List<CategoryAnalytics>,
    val uncategorized: UncategorizedAnalytics,
    val monthlyTrends: List<MonthlyTrendAnalytics>,
    val topMerchants: List<MerchantAnalytics>,
    val categorySources: List<CategorySourceAnalytics>,
    val ruleEffectiveness: RuleAnalytics,
    val accountBreakdown: List<AccountAnalytics>
) {
    val isEmpty: Boolean
        get() = summary.totalTransactionCount == 0

    companion object {
        fun empty(
            dateRange: AnalyticsDateRange = AnalyticsDateRange.THIS_MONTH,
            startDateMillis: Long = 0L,
            endDateMillis: Long = Long.MAX_VALUE
        ): AnalyticsReport {
            return AnalyticsReport(
                dateRange = dateRange,
                startDateMillis = startDateMillis,
                endDateMillis = endDateMillis,
                summary = AnalyticsSummary(),
                categoryBreakdown = emptyList(),
                uncategorized = UncategorizedAnalytics(),
                monthlyTrends = emptyList(),
                topMerchants = emptyList(),
                categorySources = emptyList(),
                ruleEffectiveness = RuleAnalytics(),
                accountBreakdown = emptyList()
            )
        }
    }
}
