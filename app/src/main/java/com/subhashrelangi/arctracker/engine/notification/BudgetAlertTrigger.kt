package com.subhashrelangi.arctracker.engine.notification

import android.content.Context
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.TransactionRelationshipType
import com.subhashrelangi.arctracker.engine.budget.AndroidBudgetAlertNotifier
import com.subhashrelangi.arctracker.engine.budget.BudgetAlertNotifier
import com.subhashrelangi.arctracker.engine.budget.BudgetManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real-time event trigger that reacts when financial transactions are committed to the database.
 * Evaluates budget envelope limits and instant daily spending thresholds.
 *
 * Guarantees:
 * - Thread-safe execution on background dispatcher.
 * - Non-blocking: failures or permission blocks never abort the transaction insert.
 * - Ignores non-debit, pending, and self-transfer transactions.
 * - Transition-based spam suppression (at most 1 instant daily limit alert per day, and
 *   transition-only alerts for budget warning/exceeded).
 */
object BudgetAlertTrigger {

    const val PREFS_ALERTS_NAME = "arctracker_notification_alerts"
    const val KEY_LAST_DAILY_LIMIT_ALERT_DATE = "last_daily_limit_alert_date"

    /**
     * Entry point invoked immediately after an expense is inserted or confirmed in the database.
     */
    suspend fun onTransactionCommitted(
        context: Context,
        expense: Expense,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        customNotifier: BudgetAlertNotifier? = null
    ) = withContext(dispatcher) {
        if (!isEligibleForBudgetEvaluation(expense)) return@withContext

        try {
            val db = AppDatabase.getDatabase(context)
            val settingsRepo = NotificationSettingsRepository.getInstance(context)
            val prefs = settingsRepo.getPreferences()

            val notifier = customNotifier ?: AndroidBudgetAlertNotifier(context)
            val budgetManager = BudgetManager(
                budgetDao = db.budgetDao(),
                expenseDao = db.expenseDao(),
                categoryDao = db.transactionCategoryDao(),
                alertNotifier = notifier
            )

            // 1. Evaluate Budget Envelopes & Overall Budgets
            if (prefs.isBudgetAlertsEnabled) {
                budgetManager.evaluateAllBudgets()
            }

            // 2. Evaluate Instant Daily Spend Limit
            val dailyLimit = prefs.dailySpendLimit
            if (dailyLimit != null && dailyLimit > 0.0) {
                checkInstantDailyLimit(context, db, expense, dailyLimit)
            }

            // 3. Evaluate ATM Cash Withdrawal Prompt
            if (prefs.isAtmFollowUpEnabled && SmartPromptEvaluator.isAtmWithdrawal(expense)) {
                val atmPayload = SmartPromptEvaluator.buildAtmCashPrompt(expense)
                NotificationDeliveryHelper.deliverNotification(context, atmPayload)
            }

            // 4. Evaluate High-Value Transaction Alert
            if (prefs.isHighValueAlertEnabled && SmartPromptEvaluator.isHighValueSpend(expense, prefs.highValueThreshold)) {
                val highValuePayload = SmartPromptEvaluator.buildHighValueSpendPrompt(expense)
                NotificationDeliveryHelper.deliverNotification(context, highValuePayload)
            }
        } catch (_: Exception) {
            // Defensive: notification failures must never crash or roll back transactions
        }
    }

    /**
     * Determines whether a transaction contributes to spending limits.
     * Only settled (non-pending) Debit transactions that are not internal self-transfers qualify.
     */
    fun isEligibleForBudgetEvaluation(expense: Expense): Boolean {
        if (expense.isPending) return false
        if (!expense.type.equals("Debit", ignoreCase = true)) return false
        if (expense.relationshipType == TransactionRelationshipType.SELF_TRANSFER) return false
        return true
    }

    /**
     * Checks if the committed transaction caused today's total spending to cross the daily limit.
     */
    private suspend fun checkInstantDailyLimit(
        context: Context,
        db: AppDatabase,
        newExpense: Expense,
        dailyLimit: Double
    ) {
        val todayExpenses = db.expenseDao().getAllExpensesList()
        val (startOfDay, endOfDay) = DailyDigestEvaluator.calculateDayBounds(System.currentTimeMillis())

        val settledTodayDebits = todayExpenses.filter { exp ->
            exp.dateMillis in startOfDay..endOfDay &&
                exp.type.equals("Debit", ignoreCase = true) &&
                !exp.isPending &&
                exp.relationshipType != TransactionRelationshipType.SELF_TRANSFER
        }

        val totalSpentNow = settledTodayDebits.sumOf { it.amount }
        // The newExpense is already inside db, so total before was totalNow - newExpense.amount
        val totalSpentBefore = totalSpentNow - newExpense.amount

        val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val sp = context.getSharedPreferences(PREFS_ALERTS_NAME, Context.MODE_PRIVATE)
        val lastSentDate = sp.getString(KEY_LAST_DAILY_LIMIT_ALERT_DATE, null)

        if (lastSentDate != todayDateStr) {
            val alertPayload = DailyDigestEvaluator.buildInstantDailyLimitAlert(
                totalSpentBefore = totalSpentBefore,
                totalSpentNow = totalSpentNow,
                dailyLimitAmount = dailyLimit
            )
            if (alertPayload != null) {
                val delivered = NotificationDeliveryHelper.deliverNotification(context, alertPayload)
                if (delivered) {
                    sp.edit().putString(KEY_LAST_DAILY_LIMIT_ALERT_DATE, todayDateStr).apply()
                }
            }
        }
    }
}
