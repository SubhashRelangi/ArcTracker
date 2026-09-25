package com.example.arctracker

import com.example.arctracker.service.*
import org.junit.Assert.*
import org.junit.Test

class StructuredTransactionExtractorTest {

    private fun createNotification(
        text: String,
        title: String? = null,
        packageName: String = "com.google.android.apps.nbu.paisa.user",
        textLines: List<String> = emptyList()
    ): NormalizedNotification {
        val captured = CapturedNotificationInfo(
            packageName = packageName,
            notificationKey = "key_${System.currentTimeMillis()}_${text.hashCode()}",
            postTime = 1711360000000L,
            title = title,
            text = text,
            textLines = textLines
        )
        return NotificationNormalizer.normalize(captured)!!
    }

    // --- Basic Amount Extraction ---

    @Test
    fun test01_basicDebitWithPayee() {
        val notif = createNotification("₹500 paid to Ravi")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull("Candidate should not be null", candidate)
        assertEquals(500.0, candidate?.amount)
        assertEquals(TransactionDirection.DEBIT, candidate?.direction)
        assertEquals("Ravi", candidate?.merchant)
        assertNotNull(candidate?.getEvidence("amount"))
        assertNotNull(candidate?.getEvidence("merchant"))
    }

    @Test
    fun test02_debitedByRs() {
        val notif = createNotification("Your account has been debited by Rs. 250")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(250.0, candidate?.amount)
        assertEquals(TransactionDirection.DEBIT, candidate?.direction)
    }

    @Test
    fun test03_creditedWithInrAndComma() {
        val notif = createNotification("INR 1,200 credited to your account")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(1200.0, candidate?.amount)
        assertEquals(TransactionDirection.CREDIT, candidate?.direction)
    }

    @Test
    fun test04_decimalAmountAndSuccessStatus() {
        val notif = createNotification("₹500.50 payment successful")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(500.50, candidate?.amount)
        assertEquals(TransactionStatus.SUCCESS, candidate?.status)
    }

    // --- Multiple Amounts ---

    @Test
    fun test05_paidWithAvailableBalance() {
        val notif = createNotification("₹500 paid to Ravi. Available balance ₹10,000.")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(500.0, candidate?.amount)
        assertNotEquals(10000.0, candidate?.amount)
        assertEquals("Ravi", candidate?.merchant)

        // Check secondary amounts
        val balanceSec = candidate?.secondaryAmounts?.find { it.type == SecondaryAmountType.BALANCE }
        assertNotNull("Available balance must be captured in secondaryAmounts", balanceSec)
        assertEquals(10000.0, balanceSec?.amount)
    }

    @Test
    fun test06_paidWithCashbackAndBalance() {
        val notif = createNotification("Paid ₹1,000. Cashback ₹100. Available balance ₹5,000.")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(1000.0, candidate?.amount)

        val cashbackSec = candidate?.secondaryAmounts?.find { it.type == SecondaryAmountType.CASHBACK }
        assertNotNull(cashbackSec)
        assertEquals(100.0, cashbackSec?.amount)

        val balanceSec = candidate?.secondaryAmounts?.find { it.type == SecondaryAmountType.BALANCE }
        assertNotNull(balanceSec)
        assertEquals(5000.0, balanceSec?.amount)
    }

    @Test
    fun test07_transactionWithFeeAndBalance() {
        val notif = createNotification("₹500 transaction. Fee ₹5. Available balance ₹2,000.")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(500.0, candidate?.amount)

        val feeSec = candidate?.secondaryAmounts?.find { it.type == SecondaryAmountType.FEE }
        assertNotNull(feeSec)
        assertEquals(5.0, feeSec?.amount)
    }

    @Test
    fun test08_paidWithGstAndTotal() {
        // "₹500 paid. GST ₹90. Total ₹590."
        // Rule: direct action verb 'paid' attached to ₹500 takes precedence over Total
        val notif = createNotification("₹500 paid. GST ₹90. Total ₹590.")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(500.0, candidate?.amount)

        val taxSec = candidate?.secondaryAmounts?.find { it.type == SecondaryAmountType.TAX_OR_GST }
        assertNotNull(taxSec)
        assertEquals(90.0, taxSec?.amount)

        val totalSec = candidate?.secondaryAmounts?.find { it.type == SecondaryAmountType.TOTAL }
        assertNotNull(totalSec)
        assertEquals(590.0, totalSec?.amount)
    }

    // --- Merchant Extraction ---

    @Test
    fun test09_merchantAmazonIndia() {
        val notif = createNotification("Paid ₹500 to Amazon India.")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(500.0, candidate?.amount)
        assertEquals("Amazon India", candidate?.merchant)
    }

    @Test
    fun test10_merchantSwiggy() {
        val notif = createNotification("Payment at SWIGGY")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("SWIGGY", candidate?.merchant)
    }

    @Test
    fun test11_sentToUpiId() {
        val notif = createNotification("₹500 sent to ravi@upi")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(500.0, candidate?.amount)
        assertEquals("ravi@upi", candidate?.upiId)
        assertNull("Counterparty should not blindly become entire UPI ID", candidate?.counterparty)
        assertNull("Merchant should not be UPI ID", candidate?.merchant)
    }

    // --- Reference Extraction ---

    @Test
    fun test12_upiRefNumber() {
        val notif = createNotification("Payment successful. UPI Ref No: 123456789012")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("123456789012", candidate?.upiTransactionId)
        assertEquals("123456789012", candidate?.referenceId)
    }

    @Test
    fun test13_utrNumber() {
        val notif = createNotification("Debited ₹1000. UTR: ABC123456789")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("ABC123456789", candidate?.utr)
    }

    @Test
    fun test14_rrnNumber() {
        val notif = createNotification("Transaction successful. RRN 123456789012")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("123456789012", candidate?.rrn)
    }

    // --- Account / Card Suffix ---

    @Test
    fun test15_cardSuffix() {
        val notif = createNotification("Paid using card XX1234")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("1234", candidate?.cardSuffix)
        assertEquals(TransactionDirection.DEBIT, candidate?.direction)
    }

    @Test
    fun test16_accountSuffix() {
        val notif = createNotification("A/c XX5678 debited ₹500")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("5678", candidate?.accountSuffix)
        assertEquals(500.0, candidate?.amount)
        assertEquals(TransactionDirection.DEBIT, candidate?.direction)
    }

    // --- Status ---

    @Test
    fun test17_statusFailed() {
        val notif = createNotification("Payment of ₹500 failed")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(500.0, candidate?.amount)
        assertEquals(TransactionStatus.FAILED, candidate?.status)
    }

    @Test
    fun test18_statusPending() {
        val notif = createNotification("Payment of ₹500 pending")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(500.0, candidate?.amount)
        assertEquals(TransactionStatus.PENDING, candidate?.status)
    }

    @Test
    fun test19_statusRefunded() {
        val notif = createNotification("Refund of ₹500 received")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(500.0, candidate?.amount)
        assertEquals(TransactionStatus.REFUNDED, candidate?.status)
        assertEquals(TransactionDirection.CREDIT, candidate?.direction)
    }

    @Test
    fun test20_statusReversed() {
        val notif = createNotification("Payment of ₹500 reversed")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(500.0, candidate?.amount)
        assertEquals(TransactionStatus.REVERSED, candidate?.status)
    }

    // --- Noise Protection ---

    @Test
    fun test21_otpNotificationDoesNotProduceCandidate() {
        val notif = createNotification("Your OTP for the ₹500 transaction is 123456")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNull("Noise OTP notification must not produce a financial candidate", candidate)
    }

    @Test
    fun test22_promotionalCashbackDoesNotExtractCompletedAmount() {
        val notif = createNotification("Get ₹500 cashback when you pay using your card")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNull("Promotional notification must not produce a candidate", candidate)
    }

    @Test
    fun test23_transactionLimitDoesNotExtractAmount() {
        val notif = createNotification("Transaction limit is ₹50,000")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNull("Non-financial limit notice must not produce a transaction candidate", candidate)

        // Also test direct extraction to ensure limit context excludes amount
        val direct = StructuredTransactionExtractor.extractDirect(notif)
        assertNull("Limit value must not be selected as transaction amount", direct.amount)
    }

    @Test
    fun test24_balanceInquiryDoesNotExtractAmount() {
        val notif = createNotification("Available balance ₹10,000")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNull("Balance inquiry must not produce candidate", candidate)

        // Also test direct extraction
        val direct = StructuredTransactionExtractor.extractDirect(notif)
        assertNull("Balance must not be extracted as transaction amount", direct.amount)
    }

    // --- Partial Extraction ---

    @Test
    fun test25_partialExtractionSuccessOnly() {
        val notif = createNotification("Payment successful")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(TransactionStatus.SUCCESS, candidate?.status)
        assertNull(candidate?.amount)
        assertNull(candidate?.merchant)
    }

    @Test
    fun test26_partialExtractionAmountOnly() {
        val notif = createNotification("₹500 paid")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(500.0, candidate?.amount)
        assertEquals(TransactionDirection.DEBIT, candidate?.direction)
        assertNull(candidate?.merchant)
    }

    // --- Ambiguous / Conflicting ---

    @Test
    fun test27_receivedFromCounterparty() {
        val notif = createNotification("₹500 transaction received from Ravi")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(500.0, candidate?.amount)
        assertEquals(TransactionDirection.CREDIT, candidate?.direction)
        assertEquals("Ravi", candidate?.counterparty)
    }

    @Test
    fun test28_reversalOfPaymentDirectionUnknown() {
        val notif = createNotification("Reversal of payment to Ravi")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(TransactionDirection.UNKNOWN, candidate?.direction)
        assertEquals(TransactionStatus.REVERSED, candidate?.status)
        assertEquals("Ravi", candidate?.merchant)
    }

    @Test
    fun test29_conflictingDebitAndCreditDirectionUnknown() {
        val notif = createNotification("Debited ₹500 for credit card bill")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(TransactionDirection.UNKNOWN, candidate?.direction)
        assertEquals(500.0, candidate?.amount)
    }

    // --- Unknown Source ---

    @Test
    fun test30_unknownPackageExtractsCorrectly() {
        val notif = createNotification(
            text = "₹750 paid to Ravi. Ref No: ABC123",
            packageName = "com.arbitrary.unknown.app"
        )
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("com.arbitrary.unknown.app", candidate?.packageName)
        assertEquals(750.0, candidate?.amount)
        assertEquals("Ravi", candidate?.merchant)
        assertEquals("ABC123", candidate?.referenceId)
        assertEquals(TransactionDirection.DEBIT, candidate?.direction)
    }

    // --- Multi-line Notification Test ---

    @Test
    fun test31_multiLineNotification() {
        val notif = createNotification(
            title = "Payment successful",
            text = "₹500 paid to Ravi",
            textLines = listOf(
                "₹500 paid to Ravi",
                "UPI Ref: 123456789012",
                "Avl Bal: ₹9,500"
            )
        )
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(500.0, candidate?.amount)
        assertEquals("Ravi", candidate?.merchant)
        assertEquals("123456789012", candidate?.upiTransactionId)
        assertEquals(TransactionStatus.SUCCESS, candidate?.status)

        // Balance excluded from amount and captured in secondary
        assertNotEquals(9500.0, candidate?.amount)
        val bal = candidate?.secondaryAmounts?.find { it.type == SecondaryAmountType.BALANCE }
        assertNotNull(bal)
        assertEquals(9500.0, bal?.amount)
    }
}
