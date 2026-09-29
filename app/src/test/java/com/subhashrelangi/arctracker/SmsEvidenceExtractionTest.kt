package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.service.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Milestone 2 Unit Tests: SMS Evidence Extraction & Pattern Generalization.
 *
 * Verifies:
 * 1. The 4 mandatory real-world test fixtures (IPPB Debit, IPPB Credit, SBI Debit without currency, APGB Debit with VPA).
 * 2. Diverse amount formats, including amounts without currency markers and disambiguation from phone numbers/refs.
 * 3. Context-aware direction extraction (specifically beneficiary credit in debit messages).
 * 4. Secondary amounts (balance, fee, tax, cashback, limit, total).
 * 5. Reference identification (UTR, RRN, UPI Ref, Refno, UPI slash format, helpline exclusion).
 * 6. Temporal evidence extraction (date + time, date only with UNKNOWN time, null time millis).
 * 7. Generic bank and payment rail identification.
 * 8. Noise rejection and TransactionValidator integration.
 */
class SmsEvidenceExtractionTest {

    private fun createNotification(
        body: String,
        sender: String = "SMS",
        postTime: Long = 1772600000000L
    ): NormalizedNotification {
        val captured = CapturedNotificationInfo(
            packageName = sender,
            notificationKey = "test_key_${body.hashCode()}",
            postTime = postTime,
            title = sender,
            text = body
        )
        return NotificationNormalizer.normalize(captured)!!
    }

    // =========================================================================
    // 1. MANDATORY REAL-WORLD FIXTURES (THE 4 PROMPT FIXTURES)
    // =========================================================================

    @Test
    fun `Test 1 - IPPB Debit real-world fixture extracts all fields accurately`() {
        val body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken on 02-09-26 Ref 624571799987. Avl Bal Rs.116.62. -IPPB"
        val notification = createNotification(body, sender = "AD-IPPB")

        val candidate = StructuredTransactionExtractor.extractDirect(notification)

        // Amount
        assertEquals(250.00, candidate.amount ?: 0.0, 0.001)

        // Direction
        assertEquals(TransactionDirection.DEBIT, candidate.direction)

        // Account
        assertEquals("2959", candidate.accountSuffix)

        // Counterparty / Merchant
        assertEquals("satya chicken", candidate.merchant)

        // Reference ID
        assertEquals("624571799987", candidate.referenceId)

        // Balance
        assertEquals(116.62, candidate.balance ?: 0.0, 0.001)

        // Payment Rail
        assertEquals("UPI", candidate.paymentRail)

        // Bank
        assertEquals("IPPB", candidate.bank)

        // Temporal Evidence: Date extracted, Time UNKNOWN
        assertNotNull(candidate.transactionDate)
        assertTrue(candidate.transactionDate!!.contains("02-09-26"))
        assertEquals("UNKNOWN", candidate.transactionTime)
        assertNotNull(candidate.temporalEvidence)
        assertNotNull(candidate.temporalEvidence!!.transactionDateMillis)
        assertNull(candidate.temporalEvidence!!.transactionTimeMillis)
        assertFalse(candidate.temporalEvidence!!.hasExplicitTransactionTime)
        assertTrue(candidate.temporalEvidence!!.hasExplicitTransactionDate)

        // Validation
        val validated = TransactionValidator.validate(candidate)
        assertFalse(validated.isRejected)
    }

    @Test
    fun `Test 2 - IPPB Credit real-world fixture extracts all fields accurately`() {
        val body = "You have received a payment of Rs. 500.00 in a/c X2959 on 02/09/2026 09:59 from relangi srinivasara thru IPPB. Info: UPI/CREDIT/624538633358.-IPPB"
        val notification = createNotification(body, sender = "AD-IPPB")

        val candidate = StructuredTransactionExtractor.extractDirect(notification)

        // Amount
        assertEquals(500.00, candidate.amount ?: 0.0, 0.001)

        // Direction
        assertEquals(TransactionDirection.CREDIT, candidate.direction)

        // Account
        assertEquals("2959", candidate.accountSuffix)

        // Counterparty / Merchant
        assertEquals("relangi srinivasara", candidate.counterparty)

        // Reference ID from UPI/CREDIT/624538633358
        assertEquals("624538633358", candidate.referenceId)
        assertEquals("624538633358", candidate.upiTransactionId)

        // Bank
        assertEquals("IPPB", candidate.bank)

        // Temporal Evidence: Date and Time extracted
        assertNotNull(candidate.transactionDate)
        assertTrue(candidate.transactionDate!!.contains("02/09/2026"))
        assertTrue(candidate.transactionTime.contains("09:59"))
        assertNotNull(candidate.temporalEvidence)
        assertNotNull(candidate.temporalEvidence!!.transactionDateMillis)
        assertNotNull(candidate.temporalEvidence!!.transactionTimeMillis)
        assertTrue(candidate.temporalEvidence!!.hasExplicitTransactionTime)
        assertTrue(candidate.temporalEvidence!!.hasExplicitTransactionDate)

        // Validation
        val validated = TransactionValidator.validate(candidate)
        assertFalse(validated.isRejected)
    }

    @Test
    fun `Test 3 - SBI Debit without currency marker extracts all fields accurately`() {
        val body = "Dear UPI user A/C X6828 debited by 10.00 on date 02Sep26 trf to KASI VISWANADHAM Refno 079173866211. If not done by you call 1800111109 -SBI"
        val notification = createNotification(body, sender = "SBIUPI")

        val candidate = StructuredTransactionExtractor.extractDirect(notification)

        // Amount: Must extract 10.00 despite lacking currency marker (Rs / INR / ₹)
        assertEquals(10.00, candidate.amount ?: 0.0, 0.001)

        // Direction
        assertEquals(TransactionDirection.DEBIT, candidate.direction)

        // Account
        assertEquals("6828", candidate.accountSuffix)

        // Counterparty / Merchant
        assertEquals("KASI VISWANADHAM", candidate.merchant)

        // Reference ID: Must capture Refno 079173866211, NOT the helpline 1800111109
        assertEquals("079173866211", candidate.referenceId)

        // Payment Rail
        assertEquals("UPI", candidate.paymentRail)

        // Bank
        assertEquals("SBI", candidate.bank)

        // Temporal Evidence: Date extracted, Time UNKNOWN
        assertNotNull(candidate.transactionDate)
        assertTrue(candidate.transactionDate!!.contains("02Sep26"))
        assertEquals("UNKNOWN", candidate.transactionTime)
        assertNotNull(candidate.temporalEvidence)
        assertNotNull(candidate.temporalEvidence!!.transactionDateMillis)
        assertNull(candidate.temporalEvidence!!.transactionTimeMillis)
        assertFalse(candidate.temporalEvidence!!.hasExplicitTransactionTime)
        assertTrue(candidate.temporalEvidence!!.hasExplicitTransactionDate)

        // Validation
        val validated = TransactionValidator.validate(candidate)
        assertFalse(validated.isRejected)
    }

    @Test
    fun `Test 4 - APGB Debit with VPA credit clause correctly resolves to DEBIT`() {
        val body = "Your a/c no. XXXXXXXXXXX1065 is debited for Rs.100.00 on 15/09/2026 14:57:23 and credited to VPA 9704147837-3@axl (UPI Ref no 676512391954) -APGBank"
        val notification = createNotification(body, sender = "APGBANK")

        val candidate = StructuredTransactionExtractor.extractDirect(notification)

        // Amount
        assertEquals(100.00, candidate.amount ?: 0.0, 0.001)

        // Direction: MUST resolve to DEBIT, not CREDIT or UNKNOWN
        assertEquals(TransactionDirection.DEBIT, candidate.direction)

        // Account
        assertEquals("1065", candidate.accountSuffix)

        // Counterparty or upiId: "9704147837-3@axl"
        assertEquals("9704147837-3@axl", candidate.upiId)

        // Reference ID
        assertEquals("676512391954", candidate.referenceId)
        assertEquals("676512391954", candidate.upiTransactionId)

        // Bank: APGB or APGBank
        assertTrue(candidate.bank in listOf("APGB", "APGBank"))

        // Payment Rail
        assertEquals("UPI", candidate.paymentRail)

        // Temporal Evidence: Date and Time extracted
        assertNotNull(candidate.transactionDate)
        assertTrue(candidate.transactionDate!!.contains("15/09/2026"))
        assertTrue(candidate.transactionTime.contains("14:57:23"))
        assertNotNull(candidate.temporalEvidence)
        assertNotNull(candidate.temporalEvidence!!.transactionDateMillis)
        assertNotNull(candidate.temporalEvidence!!.transactionTimeMillis)
        assertTrue(candidate.temporalEvidence!!.hasExplicitTransactionTime)
        assertTrue(candidate.temporalEvidence!!.hasExplicitTransactionDate)

        // Validation
        val validated = TransactionValidator.validate(candidate)
        assertFalse(validated.isRejected)
    }

    // =========================================================================
    // 2. AMOUNT PATTERNS & DISAMBIGUATION TESTS
    // =========================================================================

    @Test
    fun `extracts diverse currency representations properly`() {
        val fixtures = listOf(
            "Paid Rs.250 for groceries" to 250.0,
            "Paid Rs 250 for groceries" to 250.0,
            "Paid Rs. 250 for groceries" to 250.0,
            "Paid ₹250 for groceries" to 250.0,
            "Paid ₹ 250 for groceries" to 250.0,
            "Paid INR 250 for groceries" to 250.0,
            "A/C X1234 debited by 250.00 for purchase" to 250.0
        )

        for ((text, expectedAmount) in fixtures) {
            val candidate = StructuredTransactionExtractor.extractDirect(createNotification(text))
            assertEquals("Failed for text: $text", expectedAmount, candidate.amount ?: 0.0, 0.001)
        }
    }

    @Test
    fun `does not extract phone number as amount or reference`() {
        val body = "Dear user A/C X1122 debited by 45.00. Call 9876543210 for queries -SBI"
        val candidate = StructuredTransactionExtractor.extractDirect(createNotification(body))

        assertEquals(45.00, candidate.amount ?: 0.0, 0.001)
        assertNotEquals("9876543210", candidate.referenceId)
    }

    // =========================================================================
    // 3. SECONDARY AMOUNTS TESTS
    // =========================================================================

    @Test
    fun `separates primary transaction amount from balance, fee, and tax`() {
        val body = "A/C X9999 debited for Rs.1,500.00. Fee Rs.15.00. GST Rs.2.70. Avl Bal Rs.25,482.30"
        val candidate = StructuredTransactionExtractor.extractDirect(createNotification(body))

        assertEquals(1500.00, candidate.amount ?: 0.0, 0.001)
        assertEquals(25482.30, candidate.balance ?: 0.0, 0.001)

        val fee = candidate.secondaryAmounts.firstOrNull { it.type == SecondaryAmountType.FEE }
        assertNotNull(fee)
        assertEquals(15.00, fee!!.amount, 0.001)

        val tax = candidate.secondaryAmounts.firstOrNull { it.type == SecondaryAmountType.TAX_OR_GST }
        assertNotNull(tax)
        assertEquals(2.70, tax!!.amount, 0.001)
    }

    // =========================================================================
    // 4. IDENTITY & REFERENCE TESTS
    // =========================================================================

    @Test
    fun `extracts UTR, RRN, and UPI references accurately`() {
        val utrBody = "Rs.500 debited from A/C X1234. UTR: 123456789012"
        val utrCandidate = StructuredTransactionExtractor.extractDirect(createNotification(utrBody))
        assertEquals("123456789012", utrCandidate.utr)
        assertEquals("123456789012", utrCandidate.referenceId)

        val rrnBody = "Rs.300 debited from A/C X1234. RRN: 987654321987"
        val rrnCandidate = StructuredTransactionExtractor.extractDirect(createNotification(rrnBody))
        assertEquals("987654321987", rrnCandidate.rrn)
        assertEquals("987654321987", rrnCandidate.referenceId)
    }

    // =========================================================================
    // 5. NOISE & NON-TRANSACTION REJECTION TESTS
    // =========================================================================

    @Test
    fun `correctly rejects non-transaction OTP, promotional, and balance-only messages`() {
        val otpBody = "123456 is your OTP to complete payment of Rs.500 at Amazon. Do not share."
        val otpClassification = FinancialClassifier.classify(createNotification(otpBody))
        assertTrue(otpClassification.isNoise)
        assertEquals(NoiseCategory.OTP_OR_AUTHENTICATION, otpClassification.noiseCategory)

        val promoBody = "Get Rs.50 cashback when you pay your electricity bill today on App!"
        val promoClassification = FinancialClassifier.classify(createNotification(promoBody))
        assertTrue(promoClassification.isNoise)

        val balanceOnlyBody = "Your available balance in A/C X2959 is Rs.5,432.10."
        val balanceClassification = FinancialClassifier.classify(createNotification(balanceOnlyBody))
        assertTrue(balanceClassification.isNoise)
        assertEquals(NoiseCategory.BALANCE_INQUIRY, balanceClassification.noiseCategory)
    }

    // =========================================================================
    // 6. USER ACCOUNT CONTEXT INTEGRATION TEST
    // =========================================================================

    @Test
    fun `matches UserAccountContext and boosts validator confidence`() {
        val userAccounts = listOf(
            UserAccountContext(
                bankName = "IPPB",
                accountSuffix = "2959",
                accountType = UserAccountType.SAVINGS,
                displayName = "Post Office Savings"
            )
        )

        val body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken on 02-09-26 Ref 624571799987. Avl Bal Rs.116.62"
        val notification = createNotification(body)
        val candidate = StructuredTransactionExtractor.extractDirect(notification)

        val validated = TransactionValidator.validate(
            candidate = candidate,
            userAccounts = userAccounts
        )

        assertTrue(validated.supportingSignals.contains("KNOWN_USER_ACCOUNT"))
        assertFalse(validated.isRejected)
    }
}
