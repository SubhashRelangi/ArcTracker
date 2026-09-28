package com.example.arctracker

import com.example.arctracker.service.*
import org.junit.Assert.*
import org.junit.Test

class LiveNotificationMaskedAccountExtractionTest {

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

    // ==================================================
    // 1. MINIMUM POSITIVE CASES
    // ==================================================

    @Test
    fun test01_transferredFrom5XMask() {
        val notif = createNotification("₹100 transferred to Subhash from XXXXX9020")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull("Candidate should not be null", candidate)
        assertEquals(100.0, candidate?.amount)
        assertEquals(TransactionDirection.DEBIT, candidate?.direction)
        assertEquals("9020", candidate?.accountSuffix)
        assertNull("Bank must not be inferred in Milestone 1", candidate?.bank)
        assertEquals("Subhash", candidate?.merchant)
    }

    @Test
    fun test02_transferredFrom4XMask() {
        val notif = createNotification("₹100 transferred to Subhash from XXXX9020")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("9020", candidate?.accountSuffix)
    }

    @Test
    fun test03_transferredFrom3XMask() {
        val notif = createNotification("₹100 transferred to Subhash from XXX9020")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("9020", candidate?.accountSuffix)
    }

    @Test
    fun test04_transferredFrom4AsteriskMask() {
        val notif = createNotification("₹100 transferred to Subhash from ****9020")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("9020", candidate?.accountSuffix)
    }

    @Test
    fun test05_transferredFrom3AsteriskMask() {
        val notif = createNotification("₹100 transferred to Subhash from ***9020")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("9020", candidate?.accountSuffix)
    }

    @Test
    fun test06_transferredFrom4BulletMask() {
        val notif = createNotification("₹100 transferred to Subhash from ••••9020")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("9020", candidate?.accountSuffix)
    }

    @Test
    fun test07_transferredFrom3BulletMask() {
        val notif = createNotification("₹100 transferred to Subhash from •••9020")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("9020", candidate?.accountSuffix)
    }

    @Test
    fun test08_debitedFromExplicitAcMask() {
        val notif = createNotification("₹100 debited from A/c XXXXX9020")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(100.0, candidate?.amount)
        assertEquals(TransactionDirection.DEBIT, candidate?.direction)
        assertEquals("9020", candidate?.accountSuffix)
    }

    @Test
    fun test09_inrPaidUsingAccountXXXX4381() {
        val notif = createNotification("INR 500 paid using account XXXX4381")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(500.0, candidate?.amount)
        assertEquals(TransactionDirection.DEBIT, candidate?.direction)
        assertEquals("4381", candidate?.accountSuffix)
    }

    @Test
    fun test10_rsCreditedToAcAsterisks() {
        val notif = createNotification("Rs.100 credited to A/C ****1065")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(100.0, candidate?.amount)
        assertEquals(TransactionDirection.CREDIT, candidate?.direction)
        assertEquals("1065", candidate?.accountSuffix)
    }

    @Test
    fun test11_deterministicMultipleAccountSuffixes() {
        // Left-to-right deterministic ordering: first valid account is selected
        val notif = createNotification("₹100 transferred from XXXXX9020 to XXXXX4381")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("9020", candidate?.accountSuffix)
    }

    // ==================================================
    // 2. SPACING AND FORMAT VARIATIONS
    // ==================================================

    @Test
    fun test12_spacingBetweenMaskAndDigits() {
        val notif1 = createNotification("₹100 debited from XXXXX 9020")
        val candidate1 = StructuredTransactionExtractor.extract(notif1)
        assertNotNull(candidate1)
        assertEquals("9020", candidate1?.accountSuffix)

        val notif2 = createNotification("₹100 debited from **** 9020")
        val candidate2 = StructuredTransactionExtractor.extract(notif2)
        assertNotNull(candidate2)
        assertEquals("9020", candidate2?.accountSuffix)

        val notif3 = createNotification("₹100 debited from •••• 9020")
        val candidate3 = StructuredTransactionExtractor.extract(notif3)
        assertNotNull(candidate3)
        assertEquals("9020", candidate3?.accountSuffix)
    }

    @Test
    fun test13_explicitAccountSpacingAndPunctuation() {
        val notif1 = createNotification("₹100 debited from A/C **** 9020")
        assertEquals("9020", StructuredTransactionExtractor.extract(notif1)?.accountSuffix)

        val notif2 = createNotification("₹100 debited from A/c: XXXXX9020")
        assertEquals("9020", StructuredTransactionExtractor.extract(notif2)?.accountSuffix)

        val notif3 = createNotification("₹100 debited from account XXXX9020")
        assertEquals("9020", StructuredTransactionExtractor.extract(notif3)?.accountSuffix)

        val notif4 = createNotification("₹100 debited from using ****9020")
        assertEquals("9020", StructuredTransactionExtractor.extract(notif4)?.accountSuffix)
    }

    // ==================================================
    // 3. PRECEDENCE: EXPLICIT VS MASKED
    // ==================================================

    @Test
    fun test14_precedenceExplicitOverMasked() {
        // In "₹500 paid using account ending 4381 to XXXXX9020", Priority 1 explicit account takes precedence
        val notif = createNotification("₹500 paid using account ending 4381 to XXXXX9020")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("4381", candidate?.accountSuffix)
    }

    // ==================================================
    // 4. NEGATIVE TESTS: MUST NOT PRODUCE ACCOUNT SUFFIX
    // ==================================================

    @Test
    fun test15_negativePromotionalCashback() {
        val notif = createNotification("Congratulations! Get ₹5,000 cashback!")
        val candidate = StructuredTransactionExtractor.extract(notif)
        assertNull("Promotional notification must not produce candidate", candidate)

        val direct = StructuredTransactionExtractor.extractDirect(notif)
        assertNull("Direct extraction must not produce account suffix", direct.accountSuffix)
    }

    @Test
    fun test16_negativeOtpNotification() {
        val notif = createNotification("OTP 9020")
        val direct = StructuredTransactionExtractor.extractDirect(notif)
        assertNull("OTP must not be treated as account suffix", direct.accountSuffix)
    }

    @Test
    fun test17_negativeReferenceNotification() {
        val notif = createNotification("Reference 9020")
        val direct = StructuredTransactionExtractor.extractDirect(notif)
        assertNull("Reference ID must not be treated as account suffix", direct.accountSuffix)
    }

    @Test
    fun test18_negativeUtrNotification() {
        val notif = createNotification("UTR 9020")
        val direct = StructuredTransactionExtractor.extractDirect(notif)
        assertNull("UTR must not be treated as account suffix", direct.accountSuffix)
    }

    @Test
    fun test19_negativeTransactionIdNotification() {
        val notif = createNotification("Transaction ID 9020")
        val direct = StructuredTransactionExtractor.extractDirect(notif)
        assertNull("Transaction ID must not be treated as account suffix", direct.accountSuffix)
    }

    @Test
    fun test20_negativeAmountsMustNotBeAccountSuffix() {
        val notif1 = createNotification("₹9020 paid")
        val candidate1 = StructuredTransactionExtractor.extract(notif1)
        assertNotNull(candidate1)
        assertEquals(9020.0, candidate1?.amount)
        assertNull("Amount ₹9020 must not be extracted as account suffix", candidate1?.accountSuffix)

        val notif2 = createNotification("Rs 9020 credited")
        val candidate2 = StructuredTransactionExtractor.extract(notif2)
        assertNotNull(candidate2)
        assertEquals(9020.0, candidate2?.amount)
        assertNull("Amount Rs 9020 must not be extracted as account suffix", candidate2?.accountSuffix)

        val notif3 = createNotification("INR 9020 paid")
        val candidate3 = StructuredTransactionExtractor.extract(notif3)
        assertNotNull(candidate3)
        assertEquals(9020.0, candidate3?.amount)
        assertNull("Amount INR 9020 must not be extracted as account suffix", candidate3?.accountSuffix)
    }

    @Test
    fun test21_negativeMarketingOffer() {
        val notif = createNotification("Pay ₹9020 and get cashback")
        val candidate = StructuredTransactionExtractor.extract(notif)
        assertNull("Offer must not produce candidate", candidate)

        val direct = StructuredTransactionExtractor.extractDirect(notif)
        assertNull("Offer amount must not be account suffix", direct.accountSuffix)
    }

    @Test
    fun test22_negativeRewardText() {
        val notif = createNotification("Your reward is 9020")
        val direct = StructuredTransactionExtractor.extractDirect(notif)
        assertNull("Reward must not be account suffix", direct.accountSuffix)
    }

    @Test
    fun test23_negativeBareFourDigits() {
        val notif = createNotification("Payment processed: 9020")
        val direct = StructuredTransactionExtractor.extractDirect(notif)
        assertNull("Bare 4-digit number must not be account suffix", direct.accountSuffix)
    }

    // ==================================================
    // 5. CARD SUFFIX PRESERVATION
    // ==================================================

    @Test
    fun test24_cardSuffixPreservedAndAccountSuffixNull() {
        val notif = createNotification("Paid ₹500 using card XX1234")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(500.0, candidate?.amount)
        assertEquals("1234", candidate?.cardSuffix)
        assertNull("Account suffix must remain null when instrument is a card", candidate?.accountSuffix)
    }

    @Test
    fun test25_cardAsteriskSuffixPreserved() {
        val notif = createNotification("Paid ₹500 using card ****1234")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("1234", candidate?.cardSuffix)
        assertNull(candidate?.accountSuffix)
    }

    @Test
    fun test26_cardBulletSuffixPreserved() {
        val notif = createNotification("Paid ₹500 using card ••••1234")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("1234", candidate?.cardSuffix)
        assertNull(candidate?.accountSuffix)
    }

    // ==================================================
    // 6. REGRESSION TESTS FOR EXISTING ACCOUNT PATTERNS
    // ==================================================

    @Test
    fun test27_existingAcXXPattern() {
        val notif = createNotification("₹250 debited from A/c XX4381")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(250.0, candidate?.amount)
        assertEquals("4381", candidate?.accountSuffix)
    }

    @Test
    fun test28_existingAcXXXXPattern() {
        val notif = createNotification("₹250 debited from A/C XXXX4381")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("4381", candidate?.accountSuffix)
    }

    @Test
    fun test29_existingAccountEndingPattern() {
        val notif = createNotification("₹250 debited from account ending 4381")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("4381", candidate?.accountSuffix)
    }

    @Test
    fun test30_existingAcctPattern() {
        val notif = createNotification("₹250 debited from acct 4381")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals("4381", candidate?.accountSuffix)
    }

    @Test
    fun test31_missingAccountYieldsNullSuffixWithoutRejectingTransaction() {
        val notif = createNotification("Paid ₹500 to Subhash")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull("Missing account suffix must NOT reject a valid transaction", candidate)
        assertEquals(500.0, candidate?.amount)
        assertEquals("Subhash", candidate?.merchant)
        assertNull(candidate?.accountSuffix)
    }

    // ==================================================
    // 7. REALISTIC NOTIFICATION TESTS
    // ==================================================

    @Test
    fun test32_realisticUpiNotification1() {
        val notif = createNotification("₹500 paid to Rahul using UPI. Account ****4381")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(500.0, candidate?.amount)
        assertEquals(TransactionDirection.DEBIT, candidate?.direction)
        assertEquals("Rahul", candidate?.merchant)
        assertEquals("4381", candidate?.accountSuffix)
    }

    @Test
    fun test33_realisticUpiNotification2() {
        val notif = createNotification("₹250 debited from A/c XX1065")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(250.0, candidate?.amount)
        assertEquals(TransactionDirection.DEBIT, candidate?.direction)
        assertEquals("1065", candidate?.accountSuffix)
    }

    @Test
    fun test34_realisticUpiNotification3() {
        val notif = createNotification("₹1000 received in account XXXX9020")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(1000.0, candidate?.amount)
        assertEquals(TransactionDirection.CREDIT, candidate?.direction)
        assertEquals("9020", candidate?.accountSuffix)
    }

    // ==================================================
    // 8. PROMOTIONAL NOTIFICATION WITH MASKED DIGITS
    // ==================================================

    @Test
    fun test35_promotionalNotificationWithMaskedDigitsRemainsRejected() {
        val notif = createNotification("Congratulations! Get ₹5,000 cashback! XXXXX9020")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNull("Promotional notification must NOT become a transaction even if it has a masked account", candidate)
    }
}
