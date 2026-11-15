package com.subhashrelangi.arctracker.engine.notification

import com.subhashrelangi.arctracker.data.Expense
import java.text.NumberFormat
import java.util.Locale

/**
 * Pure evaluator for contextual smart prompts (ATM cash follow-up, high-value alerts, review digests).
 * Decoupled from Android Framework for fast JVM testing.
 */
object SmartPromptEvaluator {

    const val NOTIFICATION_ID_ATM_PROMPT = 1004
    const val NOTIFICATION_ID_HIGH_VALUE = 1005
    const val NOTIFICATION_ID_REVIEW_DIGEST = 1006

    private val ATM_REGEX = Regex(
        """\b(?:ATM|ATW|WDL-ATM|ATM-WDL|CASH\s+WDL|CASH\s+WITHDRAWAL|NFS\*ATM|ATM/CASH|ATM\s+DEBIT)\b""",
        RegexOption.IGNORE_CASE
    )

    private fun getCurrencyFormatter(): NumberFormat {
        return NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }
    }

    /**
     * Determines whether an expense represents an ATM cash withdrawal.
     */
    fun isAtmWithdrawal(expense: Expense): Boolean {
        if (!expense.type.equals("Debit", ignoreCase = true)) return false
        val merchantMatches = ATM_REGEX.containsMatchIn(expense.merchant)
        val rawMatches = expense.rawText?.let { ATM_REGEX.containsMatchIn(it) } ?: false
        val tagMatches = expense.tag?.let { ATM_REGEX.containsMatchIn(it) } ?: false
        val noteMatches = expense.note?.let { ATM_REGEX.containsMatchIn(it) } ?: false
        return merchantMatches || rawMatches || tagMatches || noteMatches
    }

    /**
     * Generates an ATM follow-up reminder payload prompting user to track liquid cash.
     */
    fun buildAtmCashPrompt(expense: Expense): DailyDigestEvaluator.NotificationPayload {
        val formatter = getCurrencyFormatter()
        val formattedAmt = formatter.format(expense.amount).replace("Rs.", "₹").trim()
        val title = "🏧 ATM Cash Withdrawal: $formattedAmt"
        val message = "Withdrew cash? Tap to track where you spend this liquid money."
        val bigText = "ATM withdrawals reduce your bank balance, but tracking how you spend that cash keeps your ledger accurate.\n\nTap to log a cash expense in seconds."

        return DailyDigestEvaluator.NotificationPayload(
            title = title,
            message = message,
            bigText = bigText,
            channelId = NotificationChannelManager.CHANNEL_DAILY_DIGEST,
            notificationId = NOTIFICATION_ID_ATM_PROMPT,
            targetDestination = "Transactions",
            openAddExpenseCash = true
        )
    }

    /**
     * Determines whether an expense exceeds the high-value transaction threshold.
     */
    fun isHighValueSpend(expense: Expense, threshold: Double): Boolean {
        if (threshold <= 0.0) return false
        if (expense.isPending) return false
        if (!expense.type.equals("Debit", ignoreCase = true)) return false
        return expense.amount >= threshold
    }

    /**
     * Generates a high-value transaction alert payload.
     */
    fun buildHighValueSpendPrompt(expense: Expense): DailyDigestEvaluator.NotificationPayload {
        val formatter = getCurrencyFormatter()
        val formattedAmt = formatter.format(expense.amount).replace("Rs.", "₹").trim()
        val merchant = expense.merchant.ifBlank { "Unknown Merchant" }
        val title = "🏷️ Large Purchase: $formattedAmt"
        val message = "Debit logged at $merchant. Tap to attach an invoice or note."
        val bigText = "A high-value purchase of $formattedAmt was logged at $merchant.\n\nTap to review transaction details, categorize, or attach a memo."

        return DailyDigestEvaluator.NotificationPayload(
            title = title,
            message = message,
            bigText = bigText,
            channelId = NotificationChannelManager.CHANNEL_TRANSACTION_REVIEWS,
            notificationId = NOTIFICATION_ID_HIGH_VALUE,
            targetDestination = "Transactions"
        )
    }

    /**
     * Generates a digest payload reminding the user to clear the review queue.
     */
    fun buildPendingReviewDigest(pendingCount: Int): DailyDigestEvaluator.NotificationPayload? {
        if (pendingCount <= 0) return null
        val txWord = if (pendingCount == 1) "transaction" else "transactions"
        val title = "📬 $pendingCount Pending Review"
        val message = "You have $pendingCount $txWord waiting for confirmation."
        val bigText = "$pendingCount incoming $txWord require your verification.\n\nTap to approve merchant categories, link accounts, or dismiss."

        return DailyDigestEvaluator.NotificationPayload(
            title = title,
            message = message,
            bigText = bigText,
            channelId = NotificationChannelManager.CHANNEL_TRANSACTION_REVIEWS,
            notificationId = NOTIFICATION_ID_REVIEW_DIGEST,
            targetDestination = "Review"
        )
    }
}
