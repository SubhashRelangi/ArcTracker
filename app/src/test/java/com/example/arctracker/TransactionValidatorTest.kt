package com.example.arctracker

import com.example.arctracker.service.*
import org.junit.Assert.*
import org.junit.Test

class TransactionValidatorTest {

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

    // --- Strong Valid Candidates ---

    @Test
    fun test01_strongValidCandidate_paidToRavi() {
        val notif = createNotification("₹500 paid to Ravi")
        val result = TransactionValidator.validate(notif)

        assertEquals(ValidationState.ACCEPTABLE, result.validationState)
        assertTrue(result.evidenceLevel == EvidenceLevel.VERY_STRONG || result.evidenceLevel == EvidenceLevel.STRONG)
        assertEquals(500.0, result.candidate.amount)
        assertEquals("Ravi", result.candidate.merchant)
        assertEquals(TransactionDirection.DEBIT, result.candidate.direction)
        assertTrue(result.isStructurallyValid)
        assertTrue(result.isAcceptable)
        assertFalse(result.isRejected)
    }

    @Test
    fun test02_strongValidCandidate_withUpiRef() {
        val notif = createNotification("₹500 paid to Ravi. UPI Ref: 123456789012")
        val result = TransactionValidator.validate(notif)

        assertEquals(ValidationState.ACCEPTABLE, result.validationState)
        assertEquals(EvidenceLevel.VERY_STRONG, result.evidenceLevel)
        assertEquals("123456789012", result.candidate.upiTransactionId)
        assertTrue(result.isAcceptable)
    }

    @Test
    fun test03_strongValidCandidate_debitedFromAccountNoMerchant() {
        val notif = createNotification("₹500 debited from A/c XX1234")
        val result = TransactionValidator.validate(notif)

        assertEquals(ValidationState.ACCEPTABLE, result.validationState)
        assertNull("Merchant should remain null", result.candidate.merchant)
        assertEquals("1234", result.candidate.accountSuffix)
        assertEquals(TransactionDirection.DEBIT, result.candidate.direction)
        assertTrue(result.isAcceptable)
    }

    // --- Partial Candidates ---

    @Test
    fun test04_partialCandidate_paymentSuccessfulNoAmount() {
        val notif = createNotification("Payment successful")
        val result = TransactionValidator.validate(notif)

        assertEquals(ValidationState.NEEDS_REVIEW, result.validationState)
        assertEquals(TransactionStatus.SUCCESS, result.candidate.status)
        assertNull(result.candidate.amount)
        assertTrue(result.needsReview)
    }

    @Test
    fun test05_paidWithoutMerchant_notRejected() {
        val notif = createNotification("₹500 paid")
        val result = TransactionValidator.validate(notif)

        assertNotEquals(ValidationState.REJECTED, result.validationState)
        assertEquals(500.0, result.candidate.amount)
        assertEquals(TransactionDirection.DEBIT, result.candidate.direction)
        assertNull(result.candidate.merchant)
        assertTrue(result.isAcceptable || result.needsReview)
    }

    @Test
    fun test06_transactionWordWithoutCompletedDirection() {
        val notif = createNotification("₹500 transaction")
        val result = TransactionValidator.validate(notif)

        assertEquals(ValidationState.NEEDS_REVIEW, result.validationState)
        assertEquals(500.0, result.candidate.amount)
        assertTrue(result.needsReview)
    }

    // --- Weak Evidence ---

    @Test
    fun test07_isolatedAmountOnly() {
        val notif = createNotification("₹500")
        val result = TransactionValidator.validate(notif)

        assertEquals(ValidationState.NEEDS_REVIEW, result.validationState)
        assertTrue(result.evidenceLevel == EvidenceLevel.WEAK || result.evidenceLevel == EvidenceLevel.MODERATE)
        assertTrue(result.needsReview)
    }

    @Test
    fun test08_transactionUpdateNoAmount() {
        val notif = createNotification("Transaction update")
        val result = TransactionValidator.validate(notif)

        assertNotEquals(ValidationState.ACCEPTABLE, result.validationState)
        assertTrue(result.needsReview || result.isRejected)
    }

    // --- Noise Candidates ---

    @Test
    fun test09_otpNotificationRejected() {
        val notif = createNotification("Your OTP for the ₹500 transaction is 123456")
        val result = TransactionValidator.validate(notif)

        assertEquals(ValidationState.REJECTED, result.validationState)
        assertEquals(EvidenceLevel.NONE, result.evidenceLevel)
        assertTrue(result.isRejected)
        assertTrue(result.rejectionReasons.any { it.contains("noise", ignoreCase = true) || it.contains("OTP", ignoreCase = true) })
    }

    @Test
    fun test10_promotionalCashbackRejected() {
        val notif = createNotification("Get ₹500 cashback when you pay")
        val result = TransactionValidator.validate(notif)

        assertEquals(ValidationState.REJECTED, result.validationState)
        assertTrue(result.isRejected)
    }

    @Test
    fun test11_transactionLimitRejected() {
        val notif = createNotification("Transaction limit is ₹50,000")
        val result = TransactionValidator.validate(notif)

        assertEquals(ValidationState.REJECTED, result.validationState)
        assertTrue(result.isRejected)
    }

    @Test
    fun test12_availableBalanceRejected() {
        val notif = createNotification("Available balance ₹10,000")
        val result = TransactionValidator.validate(notif)

        assertEquals(ValidationState.REJECTED, result.validationState)
        assertTrue(result.isRejected)
    }

    // --- Multiple Amounts ---

    @Test
    fun test13_paidWithAvailableBalance() {
        val notif = createNotification("₹500 paid. Available balance ₹10,000.")
        val result = TransactionValidator.validate(notif)

        assertEquals(ValidationState.ACCEPTABLE, result.validationState)
        assertEquals(500.0, result.candidate.amount)
        assertNotEquals(10000.0, result.candidate.amount)
        assertTrue(result.isAcceptable)
    }

    @Test
    fun test14_paidWithCashbackAndBalance() {
        val notif = createNotification("Paid ₹1,000. Cashback ₹100. Avl Bal ₹5,000.")
        val result = TransactionValidator.validate(notif)

        assertEquals(ValidationState.ACCEPTABLE, result.validationState)
        assertEquals(1000.0, result.candidate.amount)
        assertEquals(2, result.candidate.secondaryAmounts.size)
        assertTrue(result.isAcceptable)
    }

    @Test
    fun test15_paidWithGstAndTotal() {
        val notif = createNotification("₹500 paid. GST ₹90. Total ₹590.")
        val result = TransactionValidator.validate(notif)

        assertEquals(ValidationState.ACCEPTABLE, result.validationState)
        assertEquals(500.0, result.candidate.amount)
        assertTrue(result.candidate.secondaryAmounts.any { it.type == SecondaryAmountType.TAX_OR_GST })
        assertTrue(result.candidate.secondaryAmounts.any { it.type == SecondaryAmountType.TOTAL })
    }

    // --- Direction ---

    @Test
    fun test16_directionDebit() {
        val notif = createNotification("₹500 paid to Ravi")
        val result = TransactionValidator.validate(notif)

        assertEquals(TransactionDirection.DEBIT, result.candidate.direction)
        assertTrue(result.supportingSignals.contains("DIRECTION_DEBIT"))
    }

    @Test
    fun test17_directionCredit() {
        val notif = createNotification("₹500 received from Ravi")
        val result = TransactionValidator.validate(notif)

        assertEquals(TransactionDirection.CREDIT, result.candidate.direction)
        assertTrue(result.supportingSignals.contains("DIRECTION_CREDIT"))
    }

    @Test
    fun test18_reversalOfPaymentDirectionUnknownNeedsReview() {
        val notif = createNotification("Reversal of payment to Ravi")
        val result = TransactionValidator.validate(notif)

        assertEquals(TransactionDirection.UNKNOWN, result.candidate.direction)
        assertEquals(TransactionStatus.REVERSED, result.candidate.status)
        assertEquals(ValidationState.NEEDS_REVIEW, result.validationState)
    }

    @Test
    fun test19_conflictingDebitAndCreditDirectionUnknownNeedsReview() {
        val notif = createNotification("Debited ₹500 for credit card bill")
        val result = TransactionValidator.validate(notif)

        assertEquals(TransactionDirection.UNKNOWN, result.candidate.direction)
        assertEquals(ValidationState.NEEDS_REVIEW, result.validationState)
    }

    // --- Status ---

    @Test
    fun test20_statusFailedNeedsReview() {
        val notif = createNotification("Payment of ₹500 failed")
        val result = TransactionValidator.validate(notif)

        assertEquals(TransactionStatus.FAILED, result.candidate.status)
        assertEquals(ValidationState.NEEDS_REVIEW, result.validationState)
        assertFalse(result.isAcceptable)
    }

    @Test
    fun test21_statusPendingNeedsReview() {
        val notif = createNotification("Payment of ₹500 pending")
        val result = TransactionValidator.validate(notif)

        assertEquals(TransactionStatus.PENDING, result.candidate.status)
        assertEquals(ValidationState.NEEDS_REVIEW, result.validationState)
    }

    @Test
    fun test22_statusRefundedAcceptable() {
        val notif = createNotification("Refund of ₹500 received")
        val result = TransactionValidator.validate(notif)

        assertEquals(TransactionStatus.REFUNDED, result.candidate.status)
        assertEquals(TransactionDirection.CREDIT, result.candidate.direction)
        assertEquals(ValidationState.ACCEPTABLE, result.validationState)
    }

    // --- Evidence Integrity ---

    @Test
    fun test23_amountEvidenceIntegrity() {
        val notif = createNotification("₹500 paid to Ravi")
        val result = TransactionValidator.validate(notif)

        assertNotNull(result.candidate.getEvidence("amount"))
        assertEquals(FieldEvidenceQuality.DIRECT_CONTEXT, result.fieldQualities["amount"])
    }

    @Test
    fun test24_merchantEvidenceIntegrity() {
        val notif = createNotification("Paid ₹500 to Amazon India")
        val result = TransactionValidator.validate(notif)

        assertNotNull(result.candidate.getEvidence("merchant"))
        assertEquals(FieldEvidenceQuality.DIRECT_CONTEXT, result.fieldQualities["merchant"])
    }

    @Test
    fun test25_referenceIdEvidenceIntegrity() {
        val notif = createNotification("Transaction successful. UPI Ref No: 123456789012")
        val result = TransactionValidator.validate(notif)

        assertNotNull(result.candidate.getEvidence("upiTransactionId"))
        assertEquals(FieldEvidenceQuality.EXPLICIT, result.fieldQualities["referenceId"])
    }

    @Test
    fun test26_duplicateTitleTextDoesNotArtificiallyInflateScore() {
        val notifSingle = createNotification(
            title = null,
            text = "₹500 paid to Ravi"
        )
        val notifDuplicate = createNotification(
            title = "₹500 paid to Ravi",
            text = "₹500 paid to Ravi",
            textLines = listOf("₹500 paid to Ravi", "₹500 paid to Ravi")
        )

        val resultSingle = TransactionValidator.validate(notifSingle)
        val resultDuplicate = TransactionValidator.validate(notifDuplicate)

        assertEquals(resultSingle.validationState, resultDuplicate.validationState)
        assertEquals(resultSingle.evidenceLevel, resultDuplicate.evidenceLevel)
        assertEquals(resultSingle.heuristicScore, resultDuplicate.heuristicScore)
    }

    // --- Invalid Values ---

    @Test
    fun test27_amountZeroRejected() {
        val candidate = StructuredTransactionCandidate(
            sourceNotificationKey = "key1",
            packageName = "com.google.android.apps.nbu.paisa.user",
            postTime = 1000L,
            amount = 0.0,
            direction = TransactionDirection.DEBIT
        )
        val result = TransactionValidator.validate(candidate)

        assertFalse(result.isStructurallyValid)
        assertEquals(ValidationState.REJECTED, result.validationState)
        assertTrue(result.rejectionReasons.any { it.contains("greater than 0") })
    }

    @Test
    fun test28_amountNegativeRejected() {
        val candidate = StructuredTransactionCandidate(
            sourceNotificationKey = "key2",
            packageName = "com.google.android.apps.nbu.paisa.user",
            postTime = 1000L,
            amount = -50.0,
            direction = TransactionDirection.DEBIT
        )
        val result = TransactionValidator.validate(candidate)

        assertFalse(result.isStructurallyValid)
        assertEquals(ValidationState.REJECTED, result.validationState)
    }

    @Test
    fun test29_amountNaNRejected() {
        val candidate = StructuredTransactionCandidate(
            sourceNotificationKey = "key3",
            packageName = "com.google.android.apps.nbu.paisa.user",
            postTime = 1000L,
            amount = Double.NaN,
            direction = TransactionDirection.DEBIT
        )
        val result = TransactionValidator.validate(candidate)

        assertFalse(result.isStructurallyValid)
        assertEquals(ValidationState.REJECTED, result.validationState)
    }

    @Test
    fun test30_amountInfinityRejected() {
        val candidate = StructuredTransactionCandidate(
            sourceNotificationKey = "key4",
            packageName = "com.google.android.apps.nbu.paisa.user",
            postTime = 1000L,
            amount = Double.POSITIVE_INFINITY,
            direction = TransactionDirection.DEBIT
        )
        val result = TransactionValidator.validate(candidate)

        assertFalse(result.isStructurallyValid)
        assertEquals(ValidationState.REJECTED, result.validationState)
    }

    // --- Classification Conflicts ---

    @Test
    fun test31_nonFinancialWithExtractedAmountRejected() {
        val notif = createNotification("Personal loan of ₹50,000 approved")
        val candidate = StructuredTransactionCandidate(
            sourceNotificationKey = notif.notificationKey,
            packageName = notif.packageName,
            postTime = notif.postTime,
            amount = 50000.0,
            direction = TransactionDirection.DEBIT
        )
        val classification = FinancialClassifier.classify(notif)
        assertEquals(FinancialRelevance.NON_FINANCIAL, classification.financialRelevance)

        val result = TransactionValidator.validate(candidate, classification)
        assertEquals(ValidationState.REJECTED, result.validationState)
    }

    @Test
    fun test32_uncertainClassificationNeedsReview() {
        val notif = createNotification("A transaction of ₹500 was initiated")
        val result = TransactionValidator.validate(notif)

        // Uncertain classification should not be immediately accepted
        assertNotEquals(ValidationState.ACCEPTABLE, result.validationState)
        assertEquals(ValidationState.NEEDS_REVIEW, result.validationState)
    }

    @Test
    fun test33_knownPaymentPackagePromotionalRejected() {
        val notif = createNotification(
            text = "Special offer! Get 50% off on your next purchase",
            packageName = "com.google.android.apps.nbu.paisa.user"
        )
        val result = TransactionValidator.validate(notif)

        assertEquals(ValidationState.REJECTED, result.validationState)
    }

    @Test
    fun test34_unknownPackageStrongTransactionAcceptable() {
        val notif = createNotification(
            text = "₹750 paid to Ravi. Ref No: ABC123",
            packageName = "com.unknown.financial.app"
        )
        val result = TransactionValidator.validate(notif)

        assertEquals(ValidationState.ACCEPTABLE, result.validationState)
        assertEquals(750.0, result.candidate.amount)
        assertEquals("Ravi", result.candidate.merchant)
        assertTrue(result.isAcceptable)
    }

    // --- Missing Optional Fields ---

    @Test
    fun test35_strongTransactionNoMerchantAcceptable() {
        val notif = createNotification("₹1,200 debited from your account")
        val result = TransactionValidator.validate(notif)

        assertEquals(ValidationState.ACCEPTABLE, result.validationState)
        assertNull(result.candidate.merchant)
        assertEquals(1200.0, result.candidate.amount)
        assertTrue(result.isAcceptable)
    }

    @Test
    fun test36_strongTransactionNoReferenceIdAcceptable() {
        val notif = createNotification("₹350 paid to Cafe Coffee Day")
        val result = TransactionValidator.validate(notif)

        assertEquals(ValidationState.ACCEPTABLE, result.validationState)
        assertNull(result.candidate.referenceId)
        assertEquals("Cafe Coffee Day", result.candidate.merchant)
        assertEquals(350.0, result.candidate.amount)
        assertTrue(result.isAcceptable)
    }
}
