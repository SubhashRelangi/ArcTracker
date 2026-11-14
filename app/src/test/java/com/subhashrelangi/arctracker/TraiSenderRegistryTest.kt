package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.engine.pipeline.SenderTier
import com.subhashrelangi.arctracker.engine.pipeline.TraiChannelSuffix
import com.subhashrelangi.arctracker.engine.pipeline.TraiSenderRegistry
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite for Indian TRAI DLT Header & Sender Authority Registry.
 */
class TraiSenderRegistryTest {

    @Test
    fun testAirtelCarrierServiceHeader_classifiedAsTier3Carrier() {
        val info = TraiSenderRegistry.classify("AD-AIRINF-S")
        assertEquals("AD-AIRINF-S", info.rawSender)
        assertEquals("AD", info.operatorCircle)
        assertEquals("AIRINF", info.entityCode)
        assertEquals(TraiChannelSuffix.SERVICE, info.channelSuffix)
        assertEquals(SenderTier.TIER_3_MERCHANT_OR_CARRIER, info.senderTier)
        assertFalse(info.isPromotional)
        assertFalse(info.isPersonalNumber)
        assertTrue(info.isTier3MerchantOrCarrier)
    }

    @Test
    fun testHdfcBankTransactionalHeader_classifiedAsTier1Bank() {
        val info = TraiSenderRegistry.classify("VK-HDFCBK-T")
        assertEquals("VK", info.operatorCircle)
        assertEquals("HDFCBK", info.entityCode)
        assertEquals(TraiChannelSuffix.TRANSACTIONAL, info.channelSuffix)
        assertEquals(SenderTier.TIER_1_BANK, info.senderTier)
        assertFalse(info.isPromotional)
        assertTrue(info.isTier1Bank)
    }

    @Test
    fun testSbiTransactionalHeader_classifiedAsTier1Bank() {
        val info = TraiSenderRegistry.classify("BZ-SBINB-T")
        assertEquals("BZ", info.operatorCircle)
        assertEquals("SBINB", info.entityCode)
        assertEquals(TraiChannelSuffix.TRANSACTIONAL, info.channelSuffix)
        assertEquals(SenderTier.TIER_1_BANK, info.senderTier)
        assertTrue(info.isTier1Bank)
    }

    @Test
    fun testPromotionalHeader_hardDropEnforced() {
        val swiggyPromo = TraiSenderRegistry.classify("CP-SWIGGY-P")
        assertEquals("SWIGGY", swiggyPromo.entityCode)
        assertEquals(TraiChannelSuffix.PROMOTIONAL, swiggyPromo.channelSuffix)
        assertTrue(swiggyPromo.isPromotional)
        assertTrue(swiggyPromo.isHardDrop)
        assertEquals(SenderTier.TIER_3_MERCHANT_OR_CARRIER, swiggyPromo.senderTier)

        val flipkartPromo = TraiSenderRegistry.classify("AD-FLIPKT-P")
        assertTrue(flipkartPromo.isPromotional)
        assertTrue(flipkartPromo.isHardDrop)

        // Even if sent from a bank entity, -P channel means marketing promo
        val bankPromo = TraiSenderRegistry.classify("VK-HDFCBK-P")
        assertTrue(bankPromo.isPromotional)
        assertTrue(bankPromo.isHardDrop)
    }

    @Test
    fun testAirtelAndPaytmPaymentsBanks_classifiedAsTier1Bank() {
        val airtelBank = TraiSenderRegistry.classify("AD-AIRTELPB-T")
        assertEquals(SenderTier.TIER_1_BANK, airtelBank.senderTier)
        assertTrue(airtelBank.isTier1Bank)

        val paytmBank = TraiSenderRegistry.classify("VM-PAYTMB-T")
        assertEquals(SenderTier.TIER_1_BANK, paytmBank.senderTier)
        assertTrue(paytmBank.isTier1Bank)
    }

    @Test
    fun testPersonalMobileNumbers_rejectedAsPersonal() {
        val withPlus91 = TraiSenderRegistry.classify("+919876543210")
        assertTrue(withPlus91.isPersonalNumber)
        assertEquals(SenderTier.UNKNOWN, withPlus91.senderTier)
        assertTrue(withPlus91.isHardDrop)

        val tenDigits = TraiSenderRegistry.classify("9876543210")
        assertTrue(tenDigits.isPersonalNumber)
        assertTrue(tenDigits.isHardDrop)

        val withZero = TraiSenderRegistry.classify("09876543210")
        assertTrue(withZero.isPersonalNumber)
        assertTrue(withZero.isHardDrop)
    }

    @Test
    fun testFintechAppPackages_classifiedAsTier2Fintech() {
        val gpay = TraiSenderRegistry.classify("com.google.android.apps.nbu.paisa.user")
        assertEquals(SenderTier.TIER_2_FINTECH, gpay.senderTier)
        assertFalse(gpay.isPersonalNumber)
        assertTrue(gpay.isTier2Fintech)

        val phonepe = TraiSenderRegistry.classify("com.phonepe.app")
        assertEquals(SenderTier.TIER_2_FINTECH, phonepe.senderTier)
        assertTrue(phonepe.isTier2Fintech)

        val paytm = TraiSenderRegistry.classify("net.one97.paytm")
        assertEquals(SenderTier.TIER_2_FINTECH, paytm.senderTier)
        assertTrue(paytm.isTier2Fintech)
    }

    @Test
    fun testHeaderWithoutOperatorPrefix_classifiedCorrectly() {
        val hdfc = TraiSenderRegistry.classify("HDFCBK")
        assertEquals(SenderTier.TIER_1_BANK, hdfc.senderTier)
        assertNull(hdfc.operatorCircle)

        val airtel = TraiSenderRegistry.classify("AIRINF-S")
        assertEquals(SenderTier.TIER_3_MERCHANT_OR_CARRIER, airtel.senderTier)
        assertEquals(TraiChannelSuffix.SERVICE, airtel.channelSuffix)
    }

    @Test
    fun testNullAndEmptySenders_handledSafely() {
        val nullInfo = TraiSenderRegistry.classify(null)
        assertEquals(SenderTier.UNKNOWN, nullInfo.senderTier)
        assertFalse(nullInfo.isHardDrop)

        val blankInfo = TraiSenderRegistry.classify("   ")
        assertEquals(SenderTier.UNKNOWN, blankInfo.senderTier)
        assertFalse(blankInfo.isHardDrop)
    }
}
