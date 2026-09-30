package com.subhashrelangi.arctracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Calendar
import java.util.UUID
import kotlin.math.ceil

/**
 * Supported budget recurrence periods.
 */
enum class BudgetPeriodType {
    WEEKLY,
    MONTHLY,
    YEARLY;

    companion object {
        fun fromString(value: String): BudgetPeriodType {
            return try {
                valueOf(value.uppercase())
            } catch (e: Exception) {
                MONTHLY
            }
        }
    }
}

/**
 * Deterministic status of a budget based on configured thresholds.
 */
enum class BudgetStatus {
    UNDER_BUDGET,
    WARNING,
    EXCEEDED
}

/**
 * Persistent Budget Entity for ArcTracker (Milestone 14).
 *
 * Guarantees:
 * - Deterministic, immutable identity (UUID).
 * - Optional category association: null categoryId indicates an OVERALL spending budget.
 * - Calendar-aware recurrence periods (Weekly, Monthly, Yearly).
 * - Configurable warning & exceeded alert thresholds (percentages e.g. 80.0%, 100.0%).
 * - Zero mutation of persisted transactions.
 */
@Entity(
    tableName = "budgets",
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["isEnabled"])
    ]
)
data class Budget(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amountLimit: Double,
    val categoryId: String? = null,
    val periodType: String = BudgetPeriodType.MONTHLY.name,
    val periodAnchor: Int = 1,
    val warningThreshold: Double = 80.0,
    val exceededThreshold: Double = 100.0,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isOverallBudget: Boolean
        get() = categoryId.isNullOrBlank()

    val parsedPeriodType: BudgetPeriodType
        get() = BudgetPeriodType.fromString(periodType)
}

/**
 * Persistent record of alerts delivered for a specific budget and period.
 * Ensures alerts are strictly transition-based and never spammed on app restart or recalculation.
 */
@Entity(
    tableName = "budget_alert_states",
    indices = [
        Index(value = ["budgetId"]),
        Index(value = ["periodStart"])
    ]
)
data class BudgetAlertState(
    @PrimaryKey
    val id: String, // Format: "${budgetId}_${periodStart}"
    val budgetId: String,
    val periodStart: Long,
    val warningSent: Boolean = false,
    val exceededSent: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun createId(budgetId: String, periodStart: Long): String {
            return "${budgetId}_${periodStart}"
        }
    }
}

/**
 * Period boundaries and remaining duration calculation result.
 */
data class BudgetPeriodBounds(
    val startMillis: Long,
    val endMillis: Long,
    val daysRemaining: Int
) {
    companion object {
        fun calculate(
            periodType: BudgetPeriodType,
            anchor: Int = 1,
            referenceTimeMillis: Long = System.currentTimeMillis(),
            firstDayOfWeek: Int = Calendar.getInstance().firstDayOfWeek
        ): BudgetPeriodBounds {
            val cal = Calendar.getInstance().apply {
                timeInMillis = referenceTimeMillis
            }

            val (startMillis, endMillis) = when (periodType) {
                BudgetPeriodType.WEEKLY -> {
                    cal.set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val start = cal.timeInMillis
                    cal.add(Calendar.DAY_OF_YEAR, 7)
                    cal.add(Calendar.MILLISECOND, -1)
                    val end = cal.timeInMillis
                    Pair(start, end)
                }

                BudgetPeriodType.MONTHLY -> {
                    val safeAnchor = anchor.coerceIn(1, 31)
                    if (safeAnchor == 1) {
                        cal.set(Calendar.DAY_OF_MONTH, 1)
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        val start = cal.timeInMillis
                        cal.add(Calendar.MONTH, 1)
                        cal.add(Calendar.MILLISECOND, -1)
                        val end = cal.timeInMillis
                        Pair(start, end)
                    } else {
                        // Custom anchor day (e.g. 15th)
                        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
                        val maxDayCurrentMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val clampedAnchorCurrent = minOf(safeAnchor, maxDayCurrentMonth)

                        if (currentDay >= clampedAnchorCurrent) {
                            // Current period started on anchor day of this month
                            cal.set(Calendar.DAY_OF_MONTH, clampedAnchorCurrent)
                            cal.set(Calendar.HOUR_OF_DAY, 0)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            val start = cal.timeInMillis

                            cal.add(Calendar.MONTH, 1)
                            val maxDayNextMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                            val clampedAnchorNext = minOf(safeAnchor, maxDayNextMonth)
                            cal.set(Calendar.DAY_OF_MONTH, clampedAnchorNext)
                            cal.add(Calendar.MILLISECOND, -1)
                            val end = cal.timeInMillis
                            Pair(start, end)
                        } else {
                            // Current period started on anchor day of previous month
                            cal.add(Calendar.MONTH, -1)
                            val maxDayPrevMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                            val clampedAnchorPrev = minOf(safeAnchor, maxDayPrevMonth)
                            cal.set(Calendar.DAY_OF_MONTH, clampedAnchorPrev)
                            cal.set(Calendar.HOUR_OF_DAY, 0)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            val start = cal.timeInMillis

                            cal.add(Calendar.MONTH, 1)
                            val maxDayCurrent = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                            val clampedAnchorNow = minOf(safeAnchor, maxDayCurrent)
                            cal.set(Calendar.DAY_OF_MONTH, clampedAnchorNow)
                            cal.add(Calendar.MILLISECOND, -1)
                            val end = cal.timeInMillis
                            Pair(start, end)
                        }
                    }
                }

                BudgetPeriodType.YEARLY -> {
                    cal.set(Calendar.MONTH, Calendar.JANUARY)
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val start = cal.timeInMillis
                    cal.add(Calendar.YEAR, 1)
                    cal.add(Calendar.MILLISECOND, -1)
                    val end = cal.timeInMillis
                    Pair(start, end)
                }
            }

            val remainingMs = maxOf(0L, endMillis - referenceTimeMillis)
            val daysRemaining = ceil(remainingMs.toDouble() / (24.0 * 60 * 60 * 1000)).toInt()

            return BudgetPeriodBounds(
                startMillis = startMillis,
                endMillis = endMillis,
                daysRemaining = daysRemaining
            )
        }
    }
}

/**
 * Immutable domain model representing current budget progress and status.
 */
data class BudgetProgress(
    val budget: Budget,
    val categoryName: String? = null,
    val categoryIconKey: String = "category",
    val categoryColorKey: String = "default",
    val limit: Double,
    val spent: Double,
    val remaining: Double,
    val percentageUsed: Double,
    val status: BudgetStatus,
    val currentPeriodStart: Long,
    val currentPeriodEnd: Long,
    val daysRemaining: Int,
    val eligibleTransactionCount: Int,
    val pendingSpent: Double = 0.0,
    val projectedSpent: Double = 0.0,
    val warningThreshold: Double,
    val exceededThreshold: Double,
    val isCategoryArchived: Boolean = false,
    val isCategoryDeleted: Boolean = false
) {
    val isExceeded: Boolean
        get() = status == BudgetStatus.EXCEEDED

    val isWarning: Boolean
        get() = status == BudgetStatus.WARNING

    val isUnderBudget: Boolean
        get() = status == BudgetStatus.UNDER_BUDGET
}
