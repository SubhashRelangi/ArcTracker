package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.engine.pipeline.AnchorType
import com.subhashrelangi.arctracker.engine.pipeline.TransactionDnaVerifier
import com.subhashrelangi.arctracker.engine.pipeline.VerificationVerdict
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite for Layer 3.5: [TransactionDnaVerifier].
 * Verifies that physical banking anchors are correctly scored and promotional spam is blocked.
 */
class TransactionDnaVerifierTest {

    @Test
    fun test01_promotionalPrizeMoney_scoresZero_isUnverifiedPromotional() {
        val text = "Order now and win 33333 prize money! Flat Rs. 100 off on your meal when paid using PhonePe"
        val profile = TransactionDnaVerifier.verify(text)

        assertEquals(0, profile.score)
        assertEquals(VerificationVerdict.UNVERIFIED_PROMOTIONAL, profile.verdict)
        assertFalse("Must not meet extraction threshold", profile.meetsExtractionThreshold)
        assertFalse(profile.matchedAnchors.contains(AnchorType.ACCOUNT_INSTRUMENT))
        assertFalse(profile.matchedAnchors.contains(AnchorType.AUDIT_TRAIL))
        assertFalse(profile.matchedAnchors.contains(AnchorType.LEDGER_BALANCE))
    }

    @Test
    fun test02_promotionalBlinkitOffer_scoresZero_isUnverifiedPromotional() {
        val text = "Blinkit: Rs. 50 discount on your order! Sent with love"
        val profile = TransactionDnaVerifier.verify(text)

        assertEquals(0, profile.score)
        assertEquals(VerificationVerdict.UNVERIFIED_PROMOTIONAL, profile.verdict)
        assertFalse(profile.meetsExtractionThreshold)
    }

    @Test
    fun test03_promotionalPhonePeSip_scoresZero_isUnverifiedPromotional() {
        val text = "PhonePe: Start a SIP for Rs 10 today! Amount transferred automatically from your bank"
        val profile = TransactionDnaVerifier.verify(text)

        assertEquals(0, profile.score)
        assertEquals(VerificationVerdict.UNVERIFIED_PROMOTIONAL, profile.verdict)
        assertFalse(profile.meetsExtractionThreshold)
    }

    @Test
    fun test04_genuinePhonePeWalletTransaction_meetsConfirmedFinancial() {
        val text = "Paid Rs. 150 to Swiggy using PhonePe Wallet"
        val profile = TransactionDnaVerifier.verify(text)

        assertTrue("Score must be >= 50", profile.score >= 50)
        assertEquals(VerificationVerdict.CONFIRMED_FINANCIAL, profile.verdict)
        assertTrue(profile.meetsExtractionThreshold)
        assertTrue(profile.matchedAnchors.contains(AnchorType.ACCOUNT_INSTRUMENT))
        assertTrue(profile.matchedAnchors.contains(AnchorType.DIRECTIONAL_PARTY))
        assertTrue(profile.matchedAnchors.contains(AnchorType.COMPLETED_ACTION_VERB))
    }

    @Test
    fun test05_genuineP2PTransfer_meetsConfirmedFinancial() {
        val text = "₹100 transferred to Subhash from XXXXX9020"
        val profile = TransactionDnaVerifier.verify(text)

        assertTrue("Score must be >= 50", profile.score >= 50)
        assertEquals(VerificationVerdict.CONFIRMED_FINANCIAL, profile.verdict)
        assertTrue(profile.meetsExtractionThreshold)
        assertTrue(profile.matchedAnchors.contains(AnchorType.ACCOUNT_INSTRUMENT))
        assertTrue(profile.matchedAnchors.contains(AnchorType.DIRECTIONAL_PARTY))
    }

    @Test
    fun test06_genuineBankDebitSms_hasAllAnchors_scoresNearMax() {
        val text = "Your A/c XX1234 is debited for INR 2,500.00 on 10-Nov-26. UPI Ref 412345678901. Avl Bal INR 14,250.00."
        val profile = TransactionDnaVerifier.verify(text)

        assertTrue("Full bank SMS should score >= 90", profile.score >= 90)
        assertEquals(VerificationVerdict.CONFIRMED_FINANCIAL, profile.verdict)
        assertTrue(profile.meetsExtractionThreshold)
        assertTrue(profile.matchedAnchors.contains(AnchorType.ACCOUNT_INSTRUMENT))
        assertTrue(profile.matchedAnchors.contains(AnchorType.AUDIT_TRAIL))
        assertTrue(profile.matchedAnchors.contains(AnchorType.LEDGER_BALANCE))
        assertTrue(profile.matchedAnchors.contains(AnchorType.COMPLETED_ACTION_VERB))
    }

    @Test
    fun test07_cardSuffixTransaction_meetsConfirmedFinancial() {
        val text = "Paid using card XX1234"
        val profile = TransactionDnaVerifier.verify(text)

        assertTrue("Card transaction should meet extraction threshold", profile.score >= 50)
        assertEquals(VerificationVerdict.CONFIRMED_FINANCIAL, profile.verdict)
        assertTrue(profile.meetsExtractionThreshold)
        assertTrue(profile.matchedAnchors.contains(AnchorType.ACCOUNT_INSTRUMENT))
        assertTrue(profile.matchedAnchors.contains(AnchorType.COMPLETED_ACTION_VERB))
    }

    @Test
    fun test08_isolatedCurrencyPaymentSuccessful_meetsProbableThreshold() {
        val text = "₹500.50 payment successful"
        val profile = TransactionDnaVerifier.verify(text)

        assertTrue(profile.score >= 25)
        assertTrue(profile.meetsExtractionThreshold)
        assertTrue(profile.matchedAnchors.contains(AnchorType.COMPLETED_ACTION_VERB))
    }

    @Test
    fun test09_gpayLivePush_profileB_trustedPackageAnchor_confirmedFinancial() {
        val text = "Paid ₹150 to Chai Point"
        val gpayPkg = "com.google.android.apps.nbu.paisa.user"
        val profile = TransactionDnaVerifier.verify(text, packageName = gpayPkg)

        assertTrue("Profile B verified fintech should score >= 50", profile.score >= 50)
        assertEquals(VerificationVerdict.CONFIRMED_FINANCIAL, profile.verdict)
        assertTrue(profile.isAnchored)
        assertTrue(profile.matchedAnchors.contains(AnchorType.ACCOUNT_INSTRUMENT))
        assertTrue(profile.matchedAnchors.contains(AnchorType.COMPLETED_ACTION_VERB))
        assertTrue(profile.matchedAnchors.contains(AnchorType.DIRECTIONAL_PARTY))
    }

    @Test
    fun test10_smsUnanchored_profileA_scoreCappedAt35_notConfirmed() {
        val text = "Hi, we have processed Rs. 199.0 for your mobile. The payment will be updated within 15 minutes."
        val smsSender = "AD-AIRINF-S"
        val profile = TransactionDnaVerifier.verify(text, packageName = smsSender)

        assertTrue("Unanchored SMS must be capped at <= 35 pts", profile.score <= 35)
        assertFalse(profile.isAnchored)
        assertNotEquals(VerificationVerdict.CONFIRMED_FINANCIAL, profile.verdict)
        assertFalse(profile.matchedAnchors.contains(AnchorType.ACCOUNT_INSTRUMENT))
        assertFalse(profile.matchedAnchors.contains(AnchorType.COMPLETED_ACTION_VERB))
    }

    @Test
    fun test11_genericNounAlone_doesNotScoreActionVerb() {
        val text = "Track your payment status and recharge online for order reference"
        val profile = TransactionDnaVerifier.verify(text)

        assertFalse("Standalone nouns must not score action verb", profile.matchedAnchors.contains(AnchorType.COMPLETED_ACTION_VERB))
    }
}
