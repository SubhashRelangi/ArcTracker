package com.example.arctracker

import com.example.arctracker.utils.BankSenderFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankSenderFilterTest {

    @Test
    fun testRecognizesBankSender() {
        assertTrue(BankSenderFilter.isBankSender("JD-HDFCBK"))
        assertTrue(BankSenderFilter.isBankSender("VM-SBIIN"))
        assertTrue(BankSenderFilter.isBankSender("AX-ICICIB"))
        assertTrue(BankSenderFilter.isBankSender("HDFCBK")) // sender without operator prefix
    }

    @Test
    fun testRecognizesSegmentedSbiUpiSender() {
        // The user-reported real-world case: SBI UPI sender
        assertTrue(BankSenderFilter.isBankSender("VM-SBIUPI-S"))
        assertEquals("State Bank of India", BankSenderFilter.detectBankName("VM-SBIUPI-S"))
    }

    @Test
    fun testRecognizesMoreRealWorldSenders() {
        assertTrue(BankSenderFilter.isBankSender("VM-SBIIN-S"))
        assertTrue(BankSenderFilter.isBankSender("JD-PNBLD"))
        assertTrue(BankSenderFilter.isBankSender("AD-IPPBL-S"))  // India Post Payments Bank
        assertTrue(BankSenderFilter.isBankSender("XX-IPBM-S"))
        assertTrue(BankSenderFilter.isBankSender("BZ-YESBNK"))
        assertTrue(BankSenderFilter.isBankSender("BX-KOTAKB"))
        assertTrue(BankSenderFilter.isBankSender("AXISBANK"))
    }

    @Test
    fun testRejectsNonBankSenders() {
        assertFalse(BankSenderFilter.isBankSender("JD-SWIGGY"))
        assertFalse(BankSenderFilter.isBankSender("VM-AMZPAY"))
        assertFalse(BankSenderFilter.isBankSender(""))
        assertFalse(BankSenderFilter.isBankSender("   "))
    }

    @Test
    fun testRejectsNumericSenders() {
        // Random promotional / personal numbers must never be imported
        assertFalse(BankSenderFilter.isBankSender("9900123456"))
        assertFalse(BankSenderFilter.isBankSender("+919900123456"))
    }

    @Test
    fun testDetectBankName() {
        assertEquals("HDFC Bank", BankSenderFilter.detectBankName("JD-HDFCBK"))
        assertEquals("State Bank of India", BankSenderFilter.detectBankName("VM-SBIIN"))
        assertEquals("ICICI Bank", BankSenderFilter.detectBankName("AX-ICICIB"))
        assertEquals(null, BankSenderFilter.detectBankName("JD-SWIGGY"))
    }

    @Test
    fun testPromotionalBodies() {
        assertTrue(BankSenderFilter.isPromotional("Get 50% off on all purchases today. Offer valid till midnight!"))
        assertTrue(BankSenderFilter.isPromotional("Apply now and win a lucky draw prize. Use code SAVE100"))
        assertFalse(BankSenderFilter.isPromotional("Your A/c XX1234 is debited by Rs.500 on 05-09-26"))
    }
}