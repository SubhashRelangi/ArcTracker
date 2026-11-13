package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.engine.pipeline.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NotificationCreditDirectionTest {

    private fun createAndExtract(title: String, text: String, pkg: String = "com.google.android.apps.messaging"): StructuredTransactionCandidate {
        val captured = CapturedNotificationInfo(
            packageName = pkg,
            notificationKey = "test_key_${System.nanoTime()}",
            postTime = System.currentTimeMillis(),
            title = title,
            text = text
        )
        val normalized = NotificationNormalizer.normalize(captured)!!
        val classification = FinancialClassifier.classify(normalized)
        val candidates = StructuredTransactionExtractor.extractAll(classification)
        assertFalse("Candidates should not be empty for: '$title' / '$text'", candidates.isEmpty())
        return candidates.first()
    }

    @Test
    fun testCreditPatternsResolveToCredit() {
        val testCases = listOf(
            "Bank" to "Your a/c no. XX1234 is credited for Rs.500.00 on 13-Nov-26",
            "Bank" to "Rs 500 credited to your account for payment towards recharge",
            "PhonePe" to "Payment of Rs 500 received from John Doe",
            "Bank" to "Payment successful: Rs 500 credited to your account",
            "UPI" to "Payment received for Rs 500",
            "GPay" to "Received Rs 500 from John Doe",
            "Google Pay" to "John Doe sent you ₹500",
            "Bank" to "Payment of ₹500 is credited in your account",
            "Transaction Alert" to "Rs 500 credited to A/c 1234",
            "Bank" to "A/c *1234 credited by Rs. 500. UPI Ref: payment successful",
            "Bank" to "Payment to your account of Rs 500 is successful",
            "PhonePe" to "Received payment of ₹500 on PhonePe",
            "PhonePe" to "₹500 received from John Doe",
            "Google Pay" to "₹500 received from John",
            "Paytm" to "₹500 received in your Paytm Wallet",
            "Wallet" to "Rs 500 added to your wallet"
        )

        for ((title, body) in testCases) {
            val candidate = createAndExtract(title, body)
            assertEquals("Expected CREDIT for '$title' / '$body'", TransactionDirection.CREDIT, candidate.direction)
            assertEquals(500.0, candidate.amount ?: 0.0, 0.01)
        }
    }

    @Test
    fun testDebitPatternsStrictlyPreservedAsDebit() {
        val testCases = listOf(
            "Bank" to "Your a/c no. XX1234 is debited for Rs.500.00 on 13-Nov-26",
            "UPI" to "Paid Rs 500 to John Doe",
            "Swiggy" to "Payment of Rs 500 to Swiggy is successful",
            "Bank" to "Your a/c is debited for Rs.100 and credited to VPA foo@upi",
            "Bank" to "A/c *1234 debited by Rs 250 on 13-Nov-26",
            "Google Pay" to "You sent ₹500 to Alice"
        )

        for ((title, body) in testCases) {
            val candidate = createAndExtract(title, body)
            assertEquals("Expected DEBIT for '$title' / '$body'", TransactionDirection.DEBIT, candidate.direction)
        }
    }
}
