package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Milestone 5.2 Test Suite:
 * English-Only Transaction Classification & Promotional Message Filtering.
 * Covers TEST 1 through TEST 10 specified in the user request.
 */
class EnglishOnlyClassificationAndPromoFilteringTest {

    private lateinit var fakeDao: FakeExpenseDao

    @Before
    fun setUp() {
        fakeDao = FakeExpenseDao()
    }

    private fun createNormalized(
        text: String,
        title: String? = null,
        packageName: String = "com.jio.myjio",
        key: String = "test_key_${System.currentTimeMillis()}"
    ): NormalizedNotification {
        val captured = CapturedNotificationInfo(
            packageName = packageName,
            notificationKey = key,
            postTime = System.currentTimeMillis(),
            title = title,
            text = text
        )
        return NotificationNormalizer.normalize(captured)!!
    }

    // =========================================================================
    // TEST 1: English Jio promotional plan
    // =========================================================================
    @Test
    fun test01_englishJioPromotionalPlan_classifiedAsNonFinancialPromoIgnore() {
        val msg = "Recharge plan Rs.949 for 90 days with JioHotstar and 2GB/day. Recharge now."
        val normalized = createNormalized(msg, title = "Jio Plan Offer")

        val classification = FinancialClassifier.classify(normalized)
        assertEquals(FinancialRelevance.NON_FINANCIAL, classification.financialRelevance)
        assertTrue(classification.isNoise)
        assertEquals(NoiseCategory.PROMOTIONAL_OR_OFFER, classification.noiseCategory)

        // Extractor should return empty for non-financial classification
        val candidates = StructuredTransactionExtractor.extractAll(classification)
        assertTrue("No transaction candidate should be extracted for promotional plan", candidates.isEmpty())
    }

    // =========================================================================
    // TEST 2: Exact real Telugu promotional Jio message from user's device
    // =========================================================================
    @Test
    fun test02_exactRealTeluguPromotionalJioMessage_notATransaction_notPending_notReview() = runBlocking {
        val exactTeluguMessage = "మీ Jio నంబర్ 9390545539 ప్లాన్ త్వరలో ముగియనుంది. Rs.949 తో రీచార్జ్ చేసుకోండి మరియు 90 రోజుల పాటు JioHotstar ని పొందండి. అపరిమిత 5G డేటా మరియు వాయిస్ కాల్స్, 84 రోజులకు 2 GB/రోజు డేటా ఆనందించండి. రీచార్జ్ చేయడానికి www.jio.com/r/EqormMs2o పైన క్లిక్ చేయండి. షరతులు  మరియు నిబంధనలు వర్తిస్తాయి ."
        val normalized = createNormalized(exactTeluguMessage, title = "JK-620014-P", packageName = "com.jio.myjio")

        // 1. Classification check
        val classification = FinancialClassifier.classify(normalized)
        assertEquals(FinancialRelevance.NON_FINANCIAL, classification.financialRelevance)
        assertTrue(classification.isNoise)
        assertEquals(NoiseCategory.UNSUPPORTED_LANGUAGE, classification.noiseCategory)

        // 2. Structured Extraction check
        val candidates = StructuredTransactionExtractor.extractAll(classification)
        assertTrue("Telugu promotional message must produce zero candidates", candidates.isEmpty())

        // 3. Direct extraction attempt guard check
        val directCandidate = StructuredTransactionExtractor.extractDirect(normalized, classification)
        assertNull("Amount must be null for unsupported language promo message", directCandidate.amount)
        val validatedDirect = TransactionValidator.validate(directCandidate, classification)
        assertTrue("Direct candidate must be rejected by validator", validatedDirect.isRejected)
        assertEquals(ValidationState.REJECTED, validatedDirect.validationState)

        // 4. End-to-end persistence pipeline check (must not create pending expense, review item, or database record)
        val event = TransactionSourceEvent(
            sourceType = TransactionSourceType.SMS_HISTORY,
            sourceId = "sms_telugu_promo",
            sender = "JK-620014-P",
            rawText = exactTeluguMessage,
            eventTimestamp = System.currentTimeMillis()
        )
        val persistenceResults = TransactionPersistenceManager.processSourceEvent(event, fakeDao)

        // Assert 0 database inserts, 0 pending expenses
        assertEquals(0, fakeDao.getCount())
        assertEquals(0, fakeDao.getPendingExpensesList().size)
        assertTrue("Persistence results must only contain IgnoredNonFinancial",
            persistenceResults.all { it is ExpensePersistenceResult.IgnoredNonFinancial }
        )

        // Alternate Telugu message format from user device
        val altTeluguMessage = "Jio నంబర్ : 9390545539\nప్లాన్ వివరాలు  : Rs949_84D_2GB/D_JioHotstar\nMyJioని ఉపయోగించి రీఛార్జ్ చేసుకోండి మరియు అన్ని రీఛార్జ్ల‌పై జీరో కన్వినియన్స్ ఛార్జీలను ఆస్వాదించండి.\nరీఛార్జ్ చేయడానికి - http://tiny.jio.com/dmyjiorchgpl"
        val altNormalized = createNormalized(altTeluguMessage, title = "JA-JIOINF-S", packageName = "com.jio.myjio")
        val altClassification = FinancialClassifier.classify(altNormalized)
        assertEquals(FinancialRelevance.NON_FINANCIAL, altClassification.financialRelevance)
        assertTrue(altClassification.isNoise)
        assertEquals(0, StructuredTransactionExtractor.extractAll(altClassification).size)
    }

    // =========================================================================
    // TEST 3: English completed Jio recharge
    // =========================================================================
    @Test
    fun test03_englishCompletedJioRecharge_classifiedAsFinancialDebitSuccess() {
        val msg = "Your recharge of Rs.949 was successful. Recharge ID: JIO12345."
        val normalized = createNormalized(msg, title = "Recharge Successful", packageName = "com.jio.myjio")

        val classification = FinancialClassifier.classify(normalized)
        assertEquals(FinancialRelevance.FINANCIAL, classification.financialRelevance)
        assertFalse(classification.isNoise)
        assertEquals(DirectionHint.DEBIT_HINT, classification.directionHint)

        val candidate = StructuredTransactionExtractor.extractDirect(normalized, classification)
        assertNotNull(candidate)
        assertEquals(949.0, candidate!!.amount)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals(TransactionStatus.SUCCESS, candidate.status)
        assertEquals("JIO12345", candidate.referenceId)

        val validated = TransactionValidator.validate(candidate, classification)
        assertEquals(ValidationState.ACCEPTABLE, validated.validationState)
    }

    // =========================================================================
    // TEST 4: English bank debit
    // =========================================================================
    @Test
    fun test04_englishBankDebit_classifiedAsFinancialDebit() {
        val msg = "A/C XXXX debited Rs.949 for Jio. UPI Ref 987654321."
        val normalized = createNormalized(msg, title = "Bank Alert", packageName = "com.sbi.SBIAnywhere")

        val classification = FinancialClassifier.classify(normalized)
        assertEquals(FinancialRelevance.FINANCIAL, classification.financialRelevance)
        assertFalse(classification.isNoise)
        assertEquals(DirectionHint.DEBIT_HINT, classification.directionHint)

        val candidate = StructuredTransactionExtractor.extractDirect(normalized, classification)
        assertNotNull(candidate)
        assertEquals(949.0, candidate!!.amount)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("987654321", candidate.upiTransactionId ?: candidate.referenceId)
    }

    // =========================================================================
    // TEST 5: Spotify promotional
    // =========================================================================
    @Test
    fun test05_spotifyPromotional_classifiedAsNonFinancialPromoIgnore() {
        val msg = "Get Spotify Premium for Rs.799 for 12 months. Subscribe now."
        val normalized = createNormalized(msg, title = "Special Offer", packageName = "com.spotify.music")

        val classification = FinancialClassifier.classify(normalized)
        assertEquals(FinancialRelevance.NON_FINANCIAL, classification.financialRelevance)
        assertTrue(classification.isNoise)
        assertEquals(NoiseCategory.PROMOTIONAL_OR_OFFER, classification.noiseCategory)

        val candidates = StructuredTransactionExtractor.extractAll(classification)
        assertTrue("Spotify promo must produce zero candidates", candidates.isEmpty())
    }

    // =========================================================================
    // TEST 6: Spotify completed payment
    // =========================================================================
    @Test
    fun test06_spotifyCompletedPayment_classifiedAsFinancialDebit() {
        val msg = "Your Spotify Premium payment of Rs.799 was successful."
        val normalized = createNormalized(msg, title = "Payment Successful", packageName = "com.spotify.music")

        val classification = FinancialClassifier.classify(normalized)
        assertEquals(FinancialRelevance.FINANCIAL, classification.financialRelevance)
        assertFalse(classification.isNoise)
        assertEquals(DirectionHint.DEBIT_HINT, classification.directionHint)

        val candidate = StructuredTransactionExtractor.extractDirect(normalized, classification)
        assertNotNull(candidate)
        assertEquals(799.0, candidate!!.amount)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals(TransactionStatus.SUCCESS, candidate.status)
    }

    // =========================================================================
    // TEST 7: English informational balance
    // =========================================================================
    @Test
    fun test07_englishInformationalBalance_nonTransaction_doesNotCreateExpense() = runBlocking {
        val msg = "Your account balance is Rs.949."
        val normalized = createNormalized(msg, title = "Balance Alert", packageName = "com.sbi.SBIAnywhere")

        val classification = FinancialClassifier.classify(normalized)
        assertEquals(FinancialRelevance.NON_FINANCIAL, classification.financialRelevance)
        assertTrue(classification.isNoise)
        assertEquals(NoiseCategory.BALANCE_INQUIRY, classification.noiseCategory)

        val event = TransactionSourceEvent(
            sourceType = TransactionSourceType.NOTIFICATION,
            sourceId = "balance_notice_1",
            sender = "SBI",
            rawText = msg,
            eventTimestamp = System.currentTimeMillis()
        )
        val results = TransactionPersistenceManager.processSourceEvent(event, fakeDao)
        assertEquals(0, fakeDao.getCount())
        assertTrue(results.all { it is ExpensePersistenceResult.IgnoredNonFinancial })
    }

    // =========================================================================
    // TEST 8: English transaction limit
    // =========================================================================
    @Test
    fun test08_englishTransactionLimit_nonTransaction() = runBlocking {
        val msg = "Your transaction limit is Rs.50,000."
        val normalized = createNormalized(msg, title = "Limit Alert", packageName = "com.sbi.SBIAnywhere")

        val classification = FinancialClassifier.classify(normalized)
        assertEquals(FinancialRelevance.NON_FINANCIAL, classification.financialRelevance)
        assertTrue(classification.isNoise)
        assertEquals(NoiseCategory.HYPOTHETICAL_OR_OFFER_TERMS, classification.noiseCategory)

        val event = TransactionSourceEvent(
            sourceType = TransactionSourceType.NOTIFICATION,
            sourceId = "limit_notice_1",
            sender = "SBI",
            rawText = msg,
            eventTimestamp = System.currentTimeMillis()
        )
        val results = TransactionPersistenceManager.processSourceEvent(event, fakeDao)
        assertEquals(0, fakeDao.getCount())
    }

    // =========================================================================
    // TEST 9: English cashback
    // =========================================================================
    @Test
    fun test09_englishCashbackOffer_promotionalContext_doesNotCreateExpense() = runBlocking {
        val msg = "Pay Rs.949 and get Rs.100 cashback."
        val normalized = createNormalized(msg, title = "Cashback Offer", packageName = "com.phonepe.app")

        val classification = FinancialClassifier.classify(normalized)
        assertEquals(FinancialRelevance.NON_FINANCIAL, classification.financialRelevance)
        assertTrue(classification.isNoise)
        assertTrue(
            classification.noiseCategory == NoiseCategory.PROMOTIONAL_OR_OFFER ||
            classification.noiseCategory == NoiseCategory.HYPOTHETICAL_OR_OFFER_TERMS
        )

        val candidates = StructuredTransactionExtractor.extractAll(classification)
        assertTrue("Cashback offer must produce zero candidates", candidates.isEmpty())

        val event = TransactionSourceEvent(
            sourceType = TransactionSourceType.NOTIFICATION,
            sourceId = "cashback_offer_1",
            sender = "PhonePe",
            rawText = msg,
            eventTimestamp = System.currentTimeMillis()
        )
        TransactionPersistenceManager.processSourceEvent(event, fakeDao)
        assertEquals(0, fakeDao.getCount())
    }

    // =========================================================================
    // TEST 10: Mixed-language genuine financial message
    // =========================================================================
    @Test
    fun test10_mixedLanguageGenuineFinancialMessage_withStrongEvidence_classifiedAsFinancialDebit() {
        val msg = "మీ ఖాతా A/C XXXX debited Rs.949 UPI Ref 987654321."
        val normalized = createNormalized(msg, title = "SBI Alert", packageName = "com.sbi.SBIAnywhere")

        val classification = FinancialClassifier.classify(normalized)
        assertEquals(FinancialRelevance.FINANCIAL, classification.financialRelevance)
        assertFalse(classification.isNoise)
        assertEquals(DirectionHint.DEBIT_HINT, classification.directionHint)

        val candidate = StructuredTransactionExtractor.extractDirect(normalized, classification)
        assertNotNull(candidate)
        assertEquals(949.0, candidate!!.amount)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("987654321", candidate.upiTransactionId ?: candidate.referenceId)
    }

    // =========================================================================
    // Additional Language Policy Unit Tests
    // =========================================================================
    @Test
    fun test11_languagePolicyHelper_identifiesUnsupportedScriptsAccurately() {
        // Telugu
        assertTrue(LanguagePolicyHelper.containsUnsupportedScript("మీ Jio నంబర్"))
        // Hindi / Devanagari
        assertTrue(LanguagePolicyHelper.containsUnsupportedScript("आपका खाता"))
        // Tamil
        assertTrue(LanguagePolicyHelper.containsUnsupportedScript("உங்கள் கணக்கு"))
        // Kannada
        assertTrue(LanguagePolicyHelper.containsUnsupportedScript("ನಿಮ್ಮ ಖಾತೆ"))

        // Pure English / ASCII / Currency
        assertFalse(LanguagePolicyHelper.containsUnsupportedScript("Rs. 949 debited from A/C XX1234"))
        assertFalse(LanguagePolicyHelper.containsUnsupportedScript("₹500 paid to Swiggy"))
        assertFalse(LanguagePolicyHelper.containsUnsupportedScript("Special Offer! 50% discount now."))
    }

    @Test
    fun test12_languagePolicyHelper_rejectsUnsupportedWithoutEvidence_acceptsWithEvidence() {
        // Telugu promotional without completed evidence -> REJECT
        val teluguPromo = "Rs.949 తో రీచార్జ్ చేసుకోండి మరియు 90 రోజుల పాటు JioHotstar ని పొందండి"
        assertTrue(LanguagePolicyHelper.shouldRejectAsUnsupportedLanguage(teluguPromo))

        // Telugu text WITH strong English debit verb and ref -> ACCEPT
        val teluguBank = "మీ బ్యాంక్ A/C debited Rs.949 UPI Ref 123456"
        assertFalse(LanguagePolicyHelper.shouldRejectAsUnsupportedLanguage(teluguBank))

        // Hindi text WITH strong English credit verb -> ACCEPT
        val hindiBank = "आपके खाते में credited Rs.1500 Ref 987654"
        assertFalse(LanguagePolicyHelper.shouldRejectAsUnsupportedLanguage(hindiBank))
    }
}
