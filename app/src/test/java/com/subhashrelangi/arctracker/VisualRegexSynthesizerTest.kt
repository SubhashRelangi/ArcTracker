package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.utils.VisualRegexSynthesizer
import org.junit.Assert.*
import org.junit.Test

class VisualRegexSynthesizerTest {

    @Test
    fun testSynthesizeMerchantFromUpiSms() {
        val sms = "Rs 1,450.00 debited from a/c **4821 on 24-Oct-26 via UPI to ZOMATO. Ref 429104."
        val result = VisualRegexSynthesizer.synthesize(
            fullMessage = sms,
            selectedText = "ZOMATO"
        )

        assertNotNull("Synthesizer should produce a result for ZOMATO", result)
        assertEquals("Merchant / Payee", result!!.category)
        assertTrue("Pattern should match sample message", result.isVerified)
        assertTrue("Extracted preview should contain ZOMATO", result.extractedPreview.contains("ZOMATO"))
    }

    @Test
    fun testSynthesizeAmountFromBankSms() {
        val sms = "Rs. 4,500.00 debited from A/C ending 9876 at Starbucks via UPI. Avl Bal: Rs. 50,000.00"
        val result = VisualRegexSynthesizer.synthesize(
            fullMessage = sms,
            selectedText = "4,500.00"
        )

        assertNotNull("Synthesizer should produce a result for amount", result)
        assertEquals("Amount", result!!.category)
        assertTrue("Pattern should match sample message", result.isVerified)
        assertEquals("4,500.00", result.extractedPreview)
    }

    @Test
    fun testSynthesizeAccountSuffix() {
        val sms = "Dear Customer, INR 750.00 debited from A/C *5678 on 25-Nov-23 to Swiggy."
        val result = VisualRegexSynthesizer.synthesize(
            fullMessage = sms,
            selectedText = "*5678"
        )

        assertNotNull("Synthesizer should produce a result for account suffix", result)
        assertEquals("Account / Card", result!!.category)
        assertTrue("Pattern should match sample message", result.isVerified)
        assertEquals("5678", result.extractedPreview)
    }

    @Test
    fun testSynthesizeDebitTypeSignal() {
        val sms = "Rs.100.00 debited from A/c XX2959 on 28-Sep-26. UPI Ref: 138212387425."
        val result = VisualRegexSynthesizer.synthesize(
            fullMessage = sms,
            selectedText = "debited"
        )

        assertNotNull("Synthesizer should produce a result for debit keyword", result)
        assertEquals("Type Debit/Credit", result!!.category)
        assertTrue("Pattern should match sample message", result.isVerified)
        assertEquals("debited", result.extractedPreview)
    }

    @Test
    fun testBuildAndParseAiPrompt() {
        val sms = "Paid Rs 340 to Blinkit Grocery Order #9910"
        val prompt = VisualRegexSynthesizer.buildAiPrompt(sms, "Merchant / Payee")
        assertTrue("Prompt should include sample message", prompt.contains("Blinkit"))
        assertTrue("Prompt should specify JSON format", prompt.contains("```json"))

        val mockAiResponse = """
            Here is your regex pattern:
            ```json
            {
              "name": "Blinkit QuickCommerce",
              "category": "Merchant / Payee",
              "pattern": "(?i)(?:to|paid to)\\s+([A-Za-z0-9 &._-]{2,40})",
              "description": "Extracts grocery merchant name"
            }
            ```
        """.trimIndent()

        val parsed = VisualRegexSynthesizer.parseAiResponse(mockAiResponse)
        assertNotNull("AI parser should extract json rule", parsed)
        assertEquals("Blinkit QuickCommerce", parsed!!.suggestedName)
        assertEquals("Merchant / Payee", parsed.category)
        assertEquals("(?i)(?:to|paid to)\\s+([A-Za-z0-9 &._-]{2,40})", parsed.pattern)
    }
}
