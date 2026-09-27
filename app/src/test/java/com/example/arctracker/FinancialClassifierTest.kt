package com.example.arctracker

import com.example.arctracker.service.*
import com.example.arctracker.utils.IgnoreRule
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite for Step 4: Financial Classification + Noise Detection.
 * Tests all 20 required scenarios from specification, plus IgnoreRule integration,
 * direction hints, conflict precedence, and explainable signals.
 */
class FinancialClassifierTest {

    private fun normalize(
        text: String,
        title: String? = null,
        packageName: String = "com.unknown.sender",
        groupKey: String? = null,
        isGroup: Boolean = false,
        isGroupSummary: Boolean = false,
        isUpdate: Boolean = false
    ): NormalizedNotification {
        val raw = CapturedNotificationInfo(
            packageName = packageName,
            notificationKey = "test_key",
            postTime = 1000L,
            title = title,
            text = text,
            groupKey = groupKey,
            isGroup = isGroup,
            isGroupSummary = isGroupSummary,
            isUpdate = isUpdate
        )
        return NotificationNormalizer.normalize(raw)!!
    }

    // 1. Clear financial candidate: "₹500 paid to Ravi"
    @Test
    fun testFinancial_paidToRavi() {
        val norm = normalize("₹500 paid to Ravi")
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.FINANCIAL, result.financialRelevance)
        assertFalse("Must not be noise", result.isNoise)
        assertEquals(DirectionHint.DEBIT_HINT, result.directionHint)
        assertTrue(result.matchedFinancialSignals.contains("DEBIT_ACTION_INDICATOR"))
        assertTrue(result.matchedFinancialSignals.contains("CURRENCY_CONTEXT"))
    }

    // 2. Clear financial candidate: "Your account has been debited by Rs. 250"
    @Test
    fun testFinancial_accountDebited() {
        val norm = normalize("Your account has been debited by Rs. 250")
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.FINANCIAL, result.financialRelevance)
        assertFalse("Must not be noise", result.isNoise)
        assertEquals(DirectionHint.DEBIT_HINT, result.directionHint)
        assertTrue(result.matchedFinancialSignals.contains("DEBIT_ACTION_INDICATOR"))
    }

    // 3. Clear financial candidate: "INR 1,200 credited to your account"
    @Test
    fun testFinancial_accountCredited() {
        val norm = normalize("INR 1,200 credited to your account")
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.FINANCIAL, result.financialRelevance)
        assertFalse("Must not be noise", result.isNoise)
        assertEquals(DirectionHint.CREDIT_HINT, result.directionHint)
        assertTrue(result.matchedFinancialSignals.contains("CREDIT_ACTION_INDICATOR"))
    }

    // 4. Clear financial candidate: "Payment successful"
    @Test
    fun testFinancial_paymentSuccessful() {
        val norm = normalize("Payment successful")
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.FINANCIAL, result.financialRelevance)
        assertFalse("Must not be noise", result.isNoise)
        assertTrue(result.matchedFinancialSignals.contains("TRANSACTION_SUCCESS_CONFIRMATION"))
    }

    // 5. Clear noise: "Your OTP is 123456"
    @Test
    fun testNoise_otp() {
        val norm = normalize("Your OTP is 123456")
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.NON_FINANCIAL, result.financialRelevance)
        assertTrue("Must be noise", result.isNoise)
        assertEquals(NoiseCategory.OTP_OR_AUTHENTICATION, result.noiseCategory)
        assertTrue(result.noiseReasons.any { it.contains("OTP", ignoreCase = true) })
    }

    // 6. Clear noise: "Get ₹500 cashback. Shop now!"
    @Test
    fun testNoise_promotionalCashbackOffer() {
        val norm = normalize("Get ₹500 cashback. Shop now!")
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.NON_FINANCIAL, result.financialRelevance)
        assertTrue("Must be noise", result.isNoise)
        assertTrue(
            result.noiseCategory == NoiseCategory.PROMOTIONAL_OR_OFFER ||
            result.noiseCategory == NoiseCategory.HYPOTHETICAL_OR_OFFER_TERMS
        )
    }

    // 7. Clear noise: "Personal loan up to ₹5 lakh. Apply now."
    @Test
    fun testNoise_personalLoanMarketing() {
        val norm = normalize("Personal loan up to ₹5 lakh. Apply now.")
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.NON_FINANCIAL, result.financialRelevance)
        assertTrue("Must be noise", result.isNoise)
        assertEquals(NoiseCategory.LOAN_OR_CREDIT_MARKETING, result.noiseCategory)
    }

    // 8. Clear noise: "New login detected on your account"
    @Test
    fun testNoise_newLoginAlert() {
        val norm = normalize("New login detected on your account")
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.NON_FINANCIAL, result.financialRelevance)
        assertTrue("Must be noise", result.isNoise)
        assertEquals(NoiseCategory.SECURITY_OR_LOGIN, result.noiseCategory)
    }

    // 9. Clear noise / informational: "Your available balance is ₹5,000"
    @Test
    fun testNoise_availableBalanceOnly() {
        val norm = normalize("Your available balance is ₹5,000")
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.NON_FINANCIAL, result.financialRelevance)
        assertTrue("Must be noise", result.isNoise)
        assertEquals(NoiseCategory.BALANCE_INQUIRY, result.noiseCategory)
    }

    // 10. Ambiguous: "Transaction update"
    @Test
    fun testAmbiguous_transactionUpdate() {
        val norm = normalize("Transaction update")
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.UNCERTAIN, result.financialRelevance)
        assertFalse("Not noise", result.isNoise)
        assertEquals(DirectionHint.UNKNOWN, result.directionHint)
    }

    // 11. Ambiguous: "Your payment"
    @Test
    fun testAmbiguous_yourPayment() {
        val norm = normalize("Your payment")
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.UNCERTAIN, result.financialRelevance)
        assertFalse("Not noise", result.isNoise)
        assertEquals(DirectionHint.UNKNOWN, result.directionHint)
    }

    // 12. Ambiguous: "₹500"
    @Test
    fun testAmbiguous_currencyOnly() {
        val norm = normalize("₹500")
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.UNCERTAIN, result.financialRelevance)
        assertFalse("Not noise", result.isNoise)
    }

    // 13. Conflicting context: "Your OTP for the ₹500 transaction is 123456"
    @Test
    fun testConflict_otpForTransaction_mustNotBeFinancial() {
        val norm = normalize("Your OTP for the ₹500 transaction is 123456")
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.NON_FINANCIAL, result.financialRelevance)
        assertTrue("Must be marked as noise", result.isNoise)
        assertEquals(NoiseCategory.OTP_OR_AUTHENTICATION, result.noiseCategory)
    }

    // 14. Conflicting context: "Get ₹500 cashback when you pay using your card"
    @Test
    fun testConflict_promotionalWhenYouPay_mustNotBeFinancial() {
        val norm = normalize("Get ₹500 cashback when you pay using your card")
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.NON_FINANCIAL, result.financialRelevance)
        assertTrue("Must be marked as noise", result.isNoise)
    }

    // 15. Conflicting context: "Transaction limit is ₹50,000"
    @Test
    fun testConflict_transactionLimit_mustNotBeFinancial() {
        val norm = normalize("Transaction limit is ₹50,000")
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.NON_FINANCIAL, result.financialRelevance)
        assertTrue("Must be marked as noise", result.isNoise)
        assertEquals(NoiseCategory.HYPOTHETICAL_OR_OFFER_TERMS, result.noiseCategory)
    }

    // 16. Conflicting context: "Pay ₹500 and get ₹100 cashback"
    @Test
    fun testConflict_payAndGetOffer_mustNotBeFinancial() {
        val norm = normalize("Pay ₹500 and get ₹100 cashback")
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.NON_FINANCIAL, result.financialRelevance)
        assertTrue("Must be marked as noise", result.isNoise)
    }

    // 17. Unknown source + clearly transactional content -> FINANCIAL
    @Test
    fun testUnknownPackage_withTransactionalContent_isFinancial() {
        val norm = normalize(
            text = "₹750 debited for order #4321",
            packageName = "com.some.unknown.app"
        )
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.FINANCIAL, result.financialRelevance)
        assertFalse(result.isNoise)
        assertEquals(DirectionHint.DEBIT_HINT, result.directionHint)
    }

    // 18. Known payment package + promotional content -> NOISE / NON_FINANCIAL
    @Test
    fun testKnownPaymentApp_withPromotionalContent_isNoise() {
        val norm = normalize(
            text = "Get flat ₹100 discount on your next recharge! Apply now.",
            packageName = "com.google.android.apps.nbu.paisa.user"
        )
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.NON_FINANCIAL, result.financialRelevance)
        assertTrue(result.isNoise)
        assertEquals(NoiseCategory.PROMOTIONAL_OR_OFFER, result.noiseCategory)
    }

    // 19. Group notification metadata preserved
    @Test
    fun testGroupNotificationMetadata_preserved() {
        val norm = normalize(
            text = "₹1,000 credited",
            groupKey = "bank_group_1",
            isGroup = true,
            isGroupSummary = false
        )
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.FINANCIAL, result.financialRelevance)
        assertEquals("bank_group_1", result.normalized.groupKey)
        assertTrue(result.normalized.isGroup)
        assertFalse(result.normalized.isGroupSummary)
    }

    // 20. Update metadata preserved
    @Test
    fun testUpdateMetadata_preserved() {
        val norm = normalize(
            text = "₹250 debited",
            isUpdate = true
        )
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.FINANCIAL, result.financialRelevance)
        assertTrue("isUpdate must be preserved", result.normalized.isUpdate)
    }

    // 21. IgnoreRule integration: matching keyword rule
    @Test
    fun testIgnoreRule_matchingKeyword() {
        val norm = normalize("Account statement for September is ready")
        val rules = listOf(
            IgnoreRule("rule_1", "Keyword", "Contains", "statement", isSystem = true)
        )
        val result = FinancialClassifier.classify(norm, rules)

        assertEquals(FinancialRelevance.NON_FINANCIAL, result.financialRelevance)
        assertTrue(result.isNoise)
        assertTrue(result.matchedNoiseSignals.any { it.contains("IGNORE_RULE_rule_1") })
    }

    // 22. Transaction with balance remaining is classified as FINANCIAL (not balance inquiry noise)
    @Test
    fun testTransactionWithBalanceMention_isFinancial() {
        val norm = normalize("₹500 debited from A/c **1234. Avl Bal: ₹4,500")
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.FINANCIAL, result.financialRelevance)
        assertFalse(result.isNoise)
        assertEquals(DirectionHint.DEBIT_HINT, result.directionHint)
    }

    // 23. Completely unrelated notification -> NON_FINANCIAL with no noise
    @Test
    fun testUnrelatedNotification_isNonFinancial() {
        val norm = normalize("Meeting scheduled for tomorrow at 10 AM")
        val result = FinancialClassifier.classify(norm)

        assertEquals(FinancialRelevance.NON_FINANCIAL, result.financialRelevance)
        assertFalse(result.isNoise)
        assertEquals(DirectionHint.UNKNOWN, result.directionHint)
    }
}
