package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.engine.pipeline.*
import org.junit.Assert.*
import org.junit.Test

class TransactionOnlyEngineHardeningTest {

    @Test
    fun testAirtelServiceFulfillmentSmsIsDetectedAsNoiseAndRejected() {
        val rawSender = "AD-AIRINF-S"
        val rawBody = "Hi, we have processed Rs. 199.0 for your Airtel Mobile 9502568713. The payment will be updated within 15 minutes. Please keep your order ID 7517823928865849344 for future reference. Track your payment status via https://i.airtel.in/Recharge-Bills"

        val captured = CapturedNotificationInfo(
            packageName = rawSender,
            notificationKey = "airtel_test_key",
            postTime = System.currentTimeMillis(),
            title = rawSender,
            text = rawBody
        )

        val normalized = NotificationNormalizer.normalize(captured)
        assertNotNull(normalized)

        // 1. Noise Detector must flag as SERVICE_STATUS_NOTICE
        val noise = NoiseDetector.detectNoise(normalized!!)
        assertTrue("Carrier service fulfillment SMS must be identified as noise", noise.isNoise)
        assertEquals(NoiseCategory.SERVICE_STATUS_NOTICE, noise.primaryCategory)

        // 2. Financial Classifier must mark as NON_FINANCIAL due to noise
        val classification = FinancialClassifier.classify(normalized)
        assertTrue(classification.isNoise)
        assertEquals(FinancialRelevance.NON_FINANCIAL, classification.financialRelevance)

        // 3. Multi-candidate extractor must return empty candidates for noise
        val candidates = StructuredTransactionExtractor.extractAll(classification)
        assertTrue("Noise messages must produce 0 transaction candidates", candidates.isEmpty())
    }

    @Test
    fun testUrlPathSlugDoesNotHallucinateBankName() {
        val textWithUrl = "Track your payment status via https://i.airtel.in/Recharge-Bills"
        val bank = StructuredTransactionExtractor.extractBank(
            text = textWithUrl,
            sender = "AD-AIRINF-S"
        )
        assertNotEquals("Bank name must never be extracted from URL slug 'Bills'", "BILLS", bank)
        assertNull(bank)
    }

    @Test
    fun testWordReferenceDoesNotExtractBogusErenceId() {
        val text = "Please keep your order ID 7517823928865849344 for future reference."
        val captured = CapturedNotificationInfo(
            packageName = "com.test",
            notificationKey = "test_ref",
            postTime = System.currentTimeMillis(),
            text = text
        )
        val normalized = NotificationNormalizer.normalize(captured)!!
        val classification = NotificationClassificationResult(
            normalized = normalized,
            financialRelevance = FinancialRelevance.FINANCIAL,
            isNoise = false
        )
        val candidates = StructuredTransactionExtractor.extractAll(classification)
        if (candidates.isNotEmpty()) {
            val candidate = candidates.first()
            assertNotEquals("Reference ID must never be 'erence'", "erence", candidate.referenceId)
            assertEquals("7517823928865849344", candidate.referenceId)
        }
    }

    @Test
    fun testRealBankRechargeSmsIsCorrectlyIngested() {
        val bankText = "A/c *4920 debited by INR 199.00 on 19-Oct-26 via UPI to Airtel Prepaid. Avl Bal: INR 12,450.50. UPI Ref: 628912345678"
        val captured = CapturedNotificationInfo(
            packageName = "VK-HDFCBK",
            notificationKey = "bank_test_key",
            postTime = System.currentTimeMillis(),
            title = "HDFC Bank Alert",
            text = bankText
        )
        val normalized = NotificationNormalizer.normalize(captured)!!

        // Must not be noise
        val noise = NoiseDetector.detectNoise(normalized)
        assertFalse("Real bank debit alert must NOT be noise", noise.isNoise)

        // Must be financial
        val classification = FinancialClassifier.classify(normalized)
        assertEquals(FinancialRelevance.FINANCIAL, classification.financialRelevance)
        assertEquals(DirectionHint.DEBIT_HINT, classification.directionHint)

        // Must extract valid candidate
        val candidates = StructuredTransactionExtractor.extractAll(classification)
        assertEquals(1, candidates.size)
        val candidate = candidates.first()
        assertEquals(199.0, candidate.amount)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("4920", candidate.accountSuffix)
        assertEquals("628912345678", candidate.referenceId)

        // Must validate as ACCEPTABLE
        val validated = TransactionValidator.validate(candidate, classification)
        assertTrue("Real bank debit transaction must be acceptable", validated.isAcceptable)
    }
}
