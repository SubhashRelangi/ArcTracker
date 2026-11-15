package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.engine.notification.NotificationChannelManager
import com.subhashrelangi.arctracker.engine.notification.SmartPromptEvaluator
import org.junit.Assert.*
import org.junit.Test

class SmartPromptEvaluatorTest {

    @Test
    fun testIsAtmWithdrawal_identifiesAtmDebits() {
        val atmExpense1 = Expense(
            id = 1,
            amount = 5000.0,
            merchant = "HDFC ATM WDL",
            dateMillis = 1774000000000L,
            type = "Debit",
            isPending = false
        )
        assertTrue(SmartPromptEvaluator.isAtmWithdrawal(atmExpense1))

        val atmExpense2 = Expense(
            id = 2,
            amount = 2000.0,
            merchant = "Cash withdrawal",
            rawText = "Your a/c has been debited by Rs 2000 at SBI ATM MG ROAD",
            dateMillis = 1774000000000L,
            type = "Debit",
            isPending = false
        )
        assertTrue(SmartPromptEvaluator.isAtmWithdrawal(atmExpense2))

        val atmExpense3 = Expense(
            id = 3,
            amount = 10000.0,
            merchant = "NFS*ATM WDL",
            dateMillis = 1774000000000L,
            type = "Debit",
            isPending = false
        )
        assertTrue(SmartPromptEvaluator.isAtmWithdrawal(atmExpense3))
    }

    @Test
    fun testIsAtmWithdrawal_rejectsNonAtmOrCredits() {
        val shoppingExpense = Expense(
            id = 1,
            amount = 1200.0,
            merchant = "Myntra",
            rawText = "Paid Rs 1200 at Myntra using UPI",
            dateMillis = 1774000000000L,
            type = "Debit",
            isPending = false
        )
        assertFalse(SmartPromptEvaluator.isAtmWithdrawal(shoppingExpense))

        val creditWithAtm = Expense(
            id = 2,
            amount = 5000.0,
            merchant = "ATM Reversal",
            rawText = "Failed ATM transaction refund credited Rs 5000",
            dateMillis = 1774000000000L,
            type = "Credit",
            isPending = false
        )
        assertFalse(SmartPromptEvaluator.isAtmWithdrawal(creditWithAtm))
    }

    @Test
    fun testBuildAtmCashPrompt_formatsPayloadCorrectly() {
        val atmExpense = Expense(
            id = 1,
            amount = 4000.0,
            merchant = "ATM Cash WDL",
            dateMillis = 1774000000000L,
            type = "Debit",
            isPending = false
        )

        val payload = SmartPromptEvaluator.buildAtmCashPrompt(atmExpense)
        assertTrue(payload.title.contains("ATM Cash Withdrawal"))
        assertTrue(payload.message.contains("liquid money"))
        assertTrue(payload.openAddExpenseCash)
        assertEquals("Transactions", payload.targetDestination)
        assertEquals(NotificationChannelManager.CHANNEL_DAILY_DIGEST, payload.channelId)
    }

    @Test
    fun testIsHighValueSpend_thresholdChecks() {
        val highSpend = Expense(
            id = 1,
            amount = 15000.0,
            merchant = "Apple Store",
            dateMillis = 1774000000000L,
            type = "Debit",
            isPending = false
        )
        assertTrue(SmartPromptEvaluator.isHighValueSpend(highSpend, 10000.0))

        val lowSpend = highSpend.copy(amount = 8000.0)
        assertFalse(SmartPromptEvaluator.isHighValueSpend(lowSpend, 10000.0))

        val pendingHighSpend = highSpend.copy(isPending = true)
        assertFalse(SmartPromptEvaluator.isHighValueSpend(pendingHighSpend, 10000.0))

        val creditHighSpend = highSpend.copy(type = "Credit")
        assertFalse(SmartPromptEvaluator.isHighValueSpend(creditHighSpend, 10000.0))
    }

    @Test
    fun testBuildHighValueSpendPrompt_formatsPayload() {
        val expense = Expense(
            id = 1,
            amount = 25000.0,
            merchant = "Reliance Digital",
            dateMillis = 1774000000000L,
            type = "Debit",
            isPending = false
        )

        val payload = SmartPromptEvaluator.buildHighValueSpendPrompt(expense)
        assertTrue(payload.title.contains("Large Purchase"))
        assertTrue(payload.message.contains("Reliance Digital"))
        assertEquals("Transactions", payload.targetDestination)
        assertEquals(NotificationChannelManager.CHANNEL_TRANSACTION_REVIEWS, payload.channelId)
    }

    @Test
    fun testBuildPendingReviewDigest() {
        val zeroPayload = SmartPromptEvaluator.buildPendingReviewDigest(0)
        assertNull(zeroPayload)

        val singlePayload = SmartPromptEvaluator.buildPendingReviewDigest(1)
        assertNotNull(singlePayload)
        assertTrue(singlePayload!!.title.contains("1 Pending Review"))
        assertTrue(singlePayload.message.contains("1 transaction waiting"))
        assertEquals("Review", singlePayload.targetDestination)
        assertEquals(NotificationChannelManager.CHANNEL_TRANSACTION_REVIEWS, singlePayload.channelId)

        val multiPayload = SmartPromptEvaluator.buildPendingReviewDigest(4)
        assertNotNull(multiPayload)
        assertTrue(multiPayload!!.title.contains("4 Pending Review"))
        assertTrue(multiPayload.message.contains("4 transactions waiting"))
    }
}
