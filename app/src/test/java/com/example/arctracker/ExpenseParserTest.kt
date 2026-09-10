package com.example.arctracker

import com.example.arctracker.utils.ExpenseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseParserTest {

    @Test
    fun testDebit() {
        val result = ExpenseParser.parseExpenseData("Your A/c XX1234 is debited by Rs.500", "Bank")
        assertNotNull(result)
        assertEquals(500.0, result!!.amount, 0.0)
        assertEquals("Debit", result.type)
    }

    @Test
    fun testCredit() {
        val result = ExpenseParser.parseExpenseData("Rs.500 credited to A/c XX1234", "Bank")
        assertNotNull(result)
        assertEquals(500.0, result!!.amount, 0.0)
        assertEquals("Credit", result.type)
    }

    @Test
    fun testUPI() {
        val result = ExpenseParser.parseExpenseData("UPI transaction of Rs.450 to swiggy@upi", "Bank")
        assertNotNull(result)
        assertEquals(450.0, result!!.amount, 0.0)
        assertEquals("swiggy@upi", result.merchant)
        assertEquals("Debit", result.type)
    }

    @Test
    fun testOTP() {
        // Will actually be ignored in SmsImporter by the isIgnore check, but parser should still just parse or not find amount
        // Wait, "Your OTP for transaction is 123456" doesn't have an amount indicator (like Rs or INR) unless it says Rs.
        val result = ExpenseParser.parseExpenseData("Your OTP for transaction is 123456", "Bank")
        assertNull(result)
    }

    @Test
    fun testBalance() {
        // "Available balance is Rs.10,000"
        // Wait, parser might pick up Rs.10,000. Let's see if we should ignore balance in parser?
        // SmsImporter ignores balance, but let's test parser behavior
        val result = ExpenseParser.parseExpenseData("Available balance is Rs.10,000", "Bank")
        assertNotNull(result) // Parser currently finds Rs. 10,000
    }
}
