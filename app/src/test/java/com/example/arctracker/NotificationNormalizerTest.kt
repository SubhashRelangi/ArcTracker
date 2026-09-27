package com.example.arctracker

import com.example.arctracker.service.CapturedNotificationInfo
import com.example.arctracker.service.NotificationNormalizer
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite for Step 3: Notification Normalization.
 * Covers all 21 core requirements, edge cases, whitespace handling, Unicode preservation,
 * duplicate suppression in combined text, and metadata preservation.
 */
class NotificationNormalizerTest {

    // 1. Normal title + text
    @Test
    fun testNormalTitleAndText() {
        val raw = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "key_norm_1",
            postTime = 1000L,
            title = "Google Pay",
            text = "Payment of ₹500 to Flipkart successful"
        )

        val normalized = NotificationNormalizer.normalize(raw)
        assertNotNull(normalized)
        assertEquals("Google Pay", normalized?.normalizedTitle)
        assertEquals("Payment of ₹500 to Flipkart successful", normalized?.normalizedText)
        assertEquals("Google Pay\nPayment of ₹500 to Flipkart successful", normalized?.normalizedCombinedText)
        assertEquals("google pay\npayment of ₹500 to flipkart successful", normalized?.normalizedCombinedTextLower)
    }

    // 2. Repeated whitespace collapsed into single space
    @Test
    fun testRepeatedWhitespace() {
        val raw = CapturedNotificationInfo(
            packageName = "com.phonepe.app",
            notificationKey = "key_norm_2",
            postTime = 2000L,
            title = "PhonePe   App   ",
            text = "Paid     ₹1,200    to    Merchant"
        )

        val normalized = NotificationNormalizer.normalize(raw)
        assertNotNull(normalized)
        assertEquals("PhonePe App", normalized?.normalizedTitle)
        assertEquals("Paid ₹1,200 to Merchant", normalized?.normalizedText)
    }

    // 3. Tabs and line breaks collapsed cleanly
    @Test
    fun testTabsAndLineBreaks() {
        val raw = CapturedNotificationInfo(
            packageName = "net.one97.paytm",
            notificationKey = "key_norm_3",
            postTime = 3000L,
            title = "\tPaytm\t\tMoney\t",
            text = "Payment    successful\n\n\n   to   Ravi   \r\n\r\nRef: 12345\t\t"
        )

        val normalized = NotificationNormalizer.normalize(raw)
        assertNotNull(normalized)
        assertEquals("Paytm Money", normalized?.normalizedTitle)
        assertEquals("Payment successful\nto Ravi\nRef: 12345", normalized?.normalizedText)
    }

    // 4. Null fields handled safely
    @Test
    fun testNullFields_handledSafely() {
        val raw = CapturedNotificationInfo(
            packageName = "com.unknown.bank",
            notificationKey = "key_norm_4",
            postTime = 4000L,
            title = null,
            text = null,
            bigText = null,
            subText = null,
            summaryText = null,
            infoText = null,
            textLines = emptyList()
        )

        val normalized = NotificationNormalizer.normalize(raw)
        assertNotNull(normalized)
        assertNull(normalized?.normalizedTitle)
        assertNull(normalized?.normalizedText)
        assertNull(normalized?.normalizedBigText)
        assertNull(normalized?.normalizedSubText)
        assertNull(normalized?.normalizedSummaryText)
        assertNull(normalized?.normalizedInfoText)
        assertTrue(normalized?.normalizedTextLines?.isEmpty() == true)
        assertEquals("", normalized?.normalizedCombinedText)
        assertEquals("", normalized?.normalizedCombinedTextLower)
    }

    // 5. Empty and whitespace-only fields become null
    @Test
    fun testEmptyFields_becomeNull() {
        val raw = CapturedNotificationInfo(
            packageName = "com.bank.app",
            notificationKey = "key_norm_5",
            postTime = 5000L,
            title = "   ",
            text = "\t\n  \r\n\t",
            bigText = ""
        )

        val normalized = NotificationNormalizer.normalize(raw)
        assertNotNull(normalized)
        assertNull(normalized?.normalizedTitle)
        assertNull(normalized?.normalizedText)
        assertNull(normalized?.normalizedBigText)
        assertEquals("", normalized?.normalizedCombinedText)
    }

    // 6. Unicode characters handled safely (non-breaking spaces and formatting stripped)
    @Test
    fun testUnicodeWhitespace_cleanedSafely() {
        // Contains non-breaking space (\u00A0), zero-width space (\u200B), narrow no-break space (\u202F)
        val textWithUnicode = "Payment\u00A0successful\u200B\u202Fto\u2007Merchant"
        val cleaned = NotificationNormalizer.cleanText(textWithUnicode)
        assertEquals("Payment successful to Merchant", cleaned)
    }

    // 7. ₹ currency symbol strictly preserved
    @Test
    fun testRupeeSymbol_preserved() {
        val raw = CapturedNotificationInfo(
            packageName = "com.phonepe.app",
            notificationKey = "key_norm_7",
            postTime = 7000L,
            title = "PhonePe",
            text = "₹500.00 debited from A/c"
        )

        val normalized = NotificationNormalizer.normalize(raw)
        assertNotNull(normalized)
        assertTrue(normalized!!.normalizedText!!.contains("₹500.00"))
    }

    // 8. Rs / INR text preserved exactly
    @Test
    fun testRsAndInr_preserved() {
        val raw = CapturedNotificationInfo(
            packageName = "com.sbi.SBIAnywhere",
            notificationKey = "key_norm_8",
            postTime = 8000L,
            title = "SBI Alert",
            text = "Rs. 2,450.75 spent on Card. INR balance is Rs 45,000."
        )

        val normalized = NotificationNormalizer.normalize(raw)
        assertNotNull(normalized)
        assertEquals("Rs. 2,450.75 spent on Card. INR balance is Rs 45,000.", normalized?.normalizedText)
    }

    // 9. Numbers and decimal values remain intact
    @Test
    fun testNumbersAndDecimals_remainIntact() {
        val raw = CapturedNotificationInfo(
            packageName = "com.snapwork.hdfc",
            notificationKey = "key_norm_9",
            postTime = 9000L,
            title = "HDFC",
            text = "Txn of INR 1,23,456.78 credited to A/c ending 9876."
        )

        val normalized = NotificationNormalizer.normalize(raw)
        assertNotNull(normalized)
        assertEquals("Txn of INR 1,23,456.78 credited to A/c ending 9876.", normalized?.normalizedText)
    }

    // 10. Transaction/reference-like IDs remain intact
    @Test
    fun testReferenceIds_remainIntact() {
        val raw = CapturedNotificationInfo(
            packageName = "in.org.npci.upiapp",
            notificationKey = "key_norm_10",
            postTime = 10000L,
            title = "BHIM UPI",
            text = "UPI/423123456789/CR/user@okicici/Ref: 9876543210/RRN: 11223344"
        )

        val normalized = NotificationNormalizer.normalize(raw)
        assertNotNull(normalized)
        assertEquals("UPI/423123456789/CR/user@okicici/Ref: 9876543210/RRN: 11223344", normalized?.normalizedText)
    }

    // 11. TextLines normalization
    @Test
    fun testTextLines_normalizedIndependently() {
        val lines = listOf(
            "   ₹500    paid to Amazon  ",
            "\tUPI Ref:   123456789\t",
            "   ", // empty line should be filtered out
            "Balance:   ₹12,000.50   "
        )
        val raw = CapturedNotificationInfo(
            packageName = "in.amazon.mShop.android.shopping",
            notificationKey = "key_norm_11",
            postTime = 11000L,
            title = "Amazon",
            textLines = lines
        )

        val normalized = NotificationNormalizer.normalize(raw)
        assertNotNull(normalized)
        assertEquals(3, normalized?.normalizedTextLines?.size)
        assertEquals("₹500 paid to Amazon", normalized?.normalizedTextLines?.get(0))
        assertEquals("UPI Ref: 123456789", normalized?.normalizedTextLines?.get(1))
        assertEquals("Balance: ₹12,000.50", normalized?.normalizedTextLines?.get(2))
    }

    // 12. TextLines ordering strictly preserved
    @Test
    fun testTextLines_orderPreserved() {
        val lines = listOf("First Line", "Second Line", "Third Line", "Fourth Line")
        val raw = CapturedNotificationInfo(
            packageName = "com.test",
            notificationKey = "key_norm_12",
            postTime = 12000L,
            textLines = lines
        )

        val normalized = NotificationNormalizer.normalize(raw)
        assertEquals(listOf("First Line", "Second Line", "Third Line", "Fourth Line"), normalized?.normalizedTextLines)
    }

    // 13. Duplicate textual fields do not create unnecessary duplicate combined content
    @Test
    fun testDuplicateTextualFields_doNotDuplicateInCombinedText() {
        val raw = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "key_norm_13",
            postTime = 13000L,
            title = "Payment successful",
            text = "Payment successful", // Duplicate of title
            bigText = "Payment successful", // Duplicate of text
            textLines = listOf("Payment successful", "Paid ₹300 to Chai Point")
        )

        val normalized = NotificationNormalizer.normalize(raw)
        assertNotNull(normalized)
        // Combined text should contain "Payment successful" only once, followed by the unique line
        assertEquals("Payment successful\nPaid ₹300 to Chai Point", normalized?.normalizedCombinedText)
    }

    // 14. Different fields remain distinguishable in their individual properties
    @Test
    fun testDifferentFields_remainDistinguishable() {
        val raw = CapturedNotificationInfo(
            packageName = "com.dreamplug.androidapp",
            notificationKey = "key_norm_14",
            postTime = 14000L,
            title = "CRED",
            text = "Credit card bill due",
            bigText = "Bill of ₹15,400 due on 28th Sep for HDFC Credit Card",
            subText = "Alert",
            summaryText = "Card Bills",
            infoText = "Urgent"
        )

        val normalized = NotificationNormalizer.normalize(raw)
        assertNotNull(normalized)
        assertEquals("CRED", normalized?.normalizedTitle)
        assertEquals("Credit card bill due", normalized?.normalizedText)
        assertEquals("Bill of ₹15,400 due on 28th Sep for HDFC Credit Card", normalized?.normalizedBigText)
        assertEquals("Alert", normalized?.normalizedSubText)
        assertEquals("Card Bills", normalized?.normalizedSummaryText)
        assertEquals("Urgent", normalized?.normalizedInfoText)
    }

    // 15. Original raw fields remain completely unchanged
    @Test
    fun testRawFields_remainUnchanged() {
        val raw = CapturedNotificationInfo(
            packageName = "com.test.app",
            notificationKey = "key_norm_15",
            postTime = 15000L,
            title = "  RAW   TITLE   ",
            text = "  RAW\n\n\nTEXT  "
        )

        val normalized = NotificationNormalizer.normalize(raw)
        assertNotNull(normalized)
        // Normalized is cleaned
        assertEquals("RAW TITLE", normalized?.normalizedTitle)
        assertEquals("RAW\nTEXT", normalized?.normalizedText)
        // Raw remains verbatim
        assertEquals("  RAW   TITLE   ", normalized?.raw?.title)
        assertEquals("  RAW\n\n\nTEXT  ", normalized?.raw?.text)
    }

    // 16. Case normalization accessors
    @Test
    fun testCaseNormalization() {
        val raw = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "key_norm_16",
            postTime = 16000L,
            title = "Payment Successful",
            text = "Sent ₹500 TO John",
            bigText = "UPI Ref 998877",
            subText = "UPI Account",
            summaryText = "Transactions",
            infoText = "Verified",
            textLines = listOf("Line A", "Line B")
        )

        val normalized = NotificationNormalizer.normalize(raw)
        assertNotNull(normalized)
        // Original casing preserved in normalized fields
        assertEquals("Payment Successful", normalized?.normalizedTitle)
        assertEquals("Sent ₹500 TO John", normalized?.normalizedText)
        // Lowercase accessors provide lowercased versions
        assertEquals("payment successful", normalized?.normalizedTitleLower)
        assertEquals("sent ₹500 to john", normalized?.normalizedTextLower)
        assertEquals("upi ref 998877", normalized?.normalizedBigTextLower)
        assertEquals("upi account", normalized?.normalizedSubTextLower)
        assertEquals("transactions", normalized?.normalizedSummaryTextLower)
        assertEquals("verified", normalized?.normalizedInfoTextLower)
        assertEquals(listOf("line a", "line b"), normalized?.normalizedTextLinesLower)
        assertTrue(normalized!!.normalizedCombinedTextLower.contains("payment successful"))
        assertTrue(normalized.normalizedCombinedTextLower.contains("sent ₹500 to john"))
    }

    // 17. Group notification metadata preservation
    @Test
    fun testGroupMetadata_preserved() {
        val raw = CapturedNotificationInfo(
            packageName = "com.sbi.SBIAnywhere",
            notificationKey = "key_norm_17",
            postTime = 17000L,
            title = "SBI",
            text = "Txn 1",
            groupKey = "sbi_group",
            isGroup = true,
            isGroupSummary = false
        )

        val normalized = NotificationNormalizer.normalize(raw)
        assertNotNull(normalized)
        assertEquals("sbi_group", normalized?.groupKey)
        assertTrue(normalized?.isGroup == true)
        assertFalse(normalized?.isGroupSummary == true)
    }

    // 18. Group summary metadata preservation
    @Test
    fun testGroupSummaryMetadata_preserved() {
        val raw = CapturedNotificationInfo(
            packageName = "com.sbi.SBIAnywhere",
            notificationKey = "key_norm_18",
            postTime = 18000L,
            title = "SBI",
            text = "5 alerts",
            groupKey = "sbi_group",
            isGroup = true,
            isGroupSummary = true,
            flags = 512
        )

        val normalized = NotificationNormalizer.normalize(raw)
        assertNotNull(normalized)
        assertEquals("sbi_group", normalized?.groupKey)
        assertTrue(normalized?.isGroup == true)
        assertTrue(normalized?.isGroupSummary == true)
        assertEquals(512, normalized?.flags)
    }

    // 19. Notification update metadata preservation
    @Test
    fun testUpdateMetadata_preserved() {
        val raw = CapturedNotificationInfo(
            packageName = "com.phonepe.app",
            notificationKey = "key_norm_19",
            postTime = 19000L,
            title = "PhonePe",
            text = "Payment completed",
            isUpdate = true
        )

        val normalized = NotificationNormalizer.normalize(raw)
        assertNotNull(normalized)
        assertTrue("isUpdate should be preserved as true", normalized?.isUpdate == true)
        assertEquals("key_norm_19", normalized?.notificationKey)
    }

    // 20. Malformed/unexpected input does not crash
    @Test
    fun testMalformedInput_doesNotCrash() {
        val nullResult = NotificationNormalizer.normalize(null)
        assertNull(nullResult)

        val cleanNull = NotificationNormalizer.cleanText(null)
        assertNull(cleanNull)

        val cleanEmpty = NotificationNormalizer.cleanText("")
        assertNull(cleanEmpty)

        val cleanBlank = NotificationNormalizer.cleanText("   \t  \n  ")
        assertNull(cleanBlank)

        val cleanEmptyLines = NotificationNormalizer.cleanTextLines(null)
        assertTrue(cleanEmptyLines.isEmpty())
    }

    // 21. Deterministic output for identical input
    @Test
    fun testDeterministicOutput() {
        val raw = CapturedNotificationInfo(
            packageName = "com.snapwork.hdfc",
            notificationKey = "key_norm_21",
            postTime = 21000L,
            title = "HDFC Bank",
            text = "₹1,500.00 spent at Amazon",
            bigText = "₹1,500.00 spent on Card ending 1234. Avl Lmt: ₹75,000",
            category = "alert",
            channelId = "txn_alerts",
            groupKey = "hdfc_alerts",
            isGroup = true
        )

        val firstRun = NotificationNormalizer.normalize(raw)
        val secondRun = NotificationNormalizer.normalize(raw)

        assertNotNull(firstRun)
        assertNotNull(secondRun)
        assertEquals(firstRun, secondRun)
        assertEquals(firstRun?.normalizedCombinedText, secondRun?.normalizedCombinedText)
        assertEquals(firstRun?.normalizedCombinedTextLower, secondRun?.normalizedCombinedTextLower)
    }

    // Edge Case: Very long notification text
    @Test
    fun testVeryLongNotificationText() {
        val longString = "A".repeat(5000) + "   " + "B".repeat(5000)
        val cleaned = NotificationNormalizer.cleanText(longString)
        assertNotNull(cleaned)
        assertEquals("A".repeat(5000) + " " + "B".repeat(5000), cleaned)
    }
}
