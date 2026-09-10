package com.example.arctracker

import com.example.arctracker.utils.TransactionValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionValidatorTest {

    @Test
    fun testAcceptsGenuineDebit() {
        val result = TransactionValidator.validate(
            "Your A/c XX1234 is debited by Rs.500 on 05-09-26",
            "SBI"
        )
        assertTrue(result.accepted)
        assertEquals("Debit", result.parsed!!.type)
        assertEquals(500.0, result.parsed.amount, 0.0)
    }

    @Test
    fun testAcceptsGenuineCredit() {
        val result = TransactionValidator.validate(
            "Rs.1,200 credited to your account from JOHN DOE",
            "HDFC Bank"
        )
        assertTrue(result.accepted)
        assertEquals("Credit", result.parsed!!.type)
    }

    @Test
    fun testAcceptsPaymentAppUpiNotification() {
        val result = TransactionValidator.validate(
            "You paid ₹450 to Swiggy via UPI",
            "Google Pay"
        )
        assertTrue(result.accepted)
        assertEquals("Debit", result.parsed!!.type)
    }

    @Test
    fun testRejectsPromotional() {
        val result = TransactionValidator.validate(
            "Get 50% off on all purchases. Offer valid today! Win exciting prizes.",
            "ShopKaro"
        )
        assertFalse(result.accepted)
        assertTrue(result.reason.contains("Promotional"))
    }

    @Test
    fun testRejectsOtp() {
        val result = TransactionValidator.validate(
            "Your OTP for transaction is 123456",
            "SBI"
        )
        assertFalse(result.accepted)
    }

    @Test
    fun testRejectsBalanceInquiry() {
        val result = TransactionValidator.validate(
            "Available balance is Rs.10,000 in your account",
            "HDFC Bank"
        )
        assertFalse(result.accepted)
    }

    @Test
    fun testRejectsNoAmount() {
        val result = TransactionValidator.validate(
            "Your card transaction was successful at a merchant",
            "AXIS"
        )
        assertFalse(result.accepted)
    }

    @Test
    fun testRejectsRandomMessage() {
        val result = TransactionValidator.validate(
            "Hey, are you coming to the party tonight?",
            "Messages"
        )
        assertFalse(result.accepted)
    }

    @Test
    fun testRejectsEmpty() {
        assertFalse(TransactionValidator.validate("", "").accepted)
    }

    @Test
    fun testRefundIsCredit() {
        val result = TransactionValidator.validate(
            "Refund of Rs.299 received in your wallet",
            "PhonePe"
        )
        assertTrue(result.accepted)
        assertEquals("Credit", result.parsed!!.type)
    }
}