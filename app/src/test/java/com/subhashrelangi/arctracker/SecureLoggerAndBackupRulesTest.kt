package com.subhashrelangi.arctracker

import android.util.Log
import com.subhashrelangi.arctracker.security.SecureLogger
import com.subhashrelangi.arctracker.security.SensitiveDataSanitizer
import com.subhashrelangi.arctracker.service.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * P0 Security Remediation Unit Tests:
 * 1. Verifies SecureLogger redaction, debug suppression, and safe exception logging.
 * 2. Verifies NotificationReaderService minimal diagnostic logging without sensitive data leaks.
 * 3. Verifies XML backup rules and data extraction rules for complete database exclusions.
 */
class SecureLoggerAndBackupRulesTest {

    private val loggedEntries = mutableListOf<Triple<Int, String, String>>()

    @Before
    fun setUp() {
        loggedEntries.clear()
        SecureLogger.logHandler = { priority, tag, message ->
            loggedEntries.add(Triple(priority, tag, message))
        }
        SecureLogger.isDebugEnabled = true
    }

    @After
    fun tearDown() {
        SecureLogger.logHandler = null
        SecureLogger.isDebugEnabled = true
        NotificationPermissionHelper.permissionOverrideForTesting = null
    }

    // =========================================================================
    // 1. SECURE LOGGER REDACTION & SUPPRESSION TESTS
    // =========================================================================

    @Test
    fun testDebugLoggingSuppressionWhenDisabled() {
        SecureLogger.isDebugEnabled = false

        SecureLogger.d("TestTag", "Debug message with card 4111222233334444")
        SecureLogger.i("TestTag", "Info message with account 123456789012")

        assertTrue("Debug and Info logs must be suppressed when isDebugEnabled=false", loggedEntries.isEmpty())

        // Warning and Error should still log in release
        SecureLogger.w("TestTag", "Warning event")
        SecureLogger.e("TestTag", "Error event")

        assertEquals(2, loggedEntries.size)
        assertEquals(Log.WARN, loggedEntries[0].first)
        assertEquals(Log.ERROR, loggedEntries[1].first)
    }

    @Test
    fun testCardAndAccountRedactionInSecureLogger() {
        SecureLogger.d("TestTag", "Payment via card 4111222233334444 from A/c 98765432109876")

        assertEquals(1, loggedEntries.size)
        val msg = loggedEntries[0].third
        assertFalse("Full card number must not appear in logcat", msg.contains("4111222233334444"))
        assertTrue("Card suffix should be masked safely", msg.contains("•••• •••• •••• 4444"))
        assertFalse("Full account number must not appear in logcat", msg.contains("98765432109876"))
        assertTrue("Account suffix should be masked safely", msg.contains("•••• 9876"))
    }

    @Test
    fun testOtpAndPinRedactionInSecureLogger() {
        SecureLogger.w("TestTag", "Auth alert: OTP is 839201 for login, pin: 1234")

        assertEquals(1, loggedEntries.size)
        val msg = loggedEntries[0].third
        assertFalse("OTP digits must not appear in logcat", msg.contains("839201"))
        assertFalse("PIN digits must not appear in logcat", msg.contains("1234"))
        assertTrue("Placeholder should be present", msg.contains("[REDACTED]"))
    }

    @Test
    fun testUpiVpaRedactionInSecureLogger() {
        SecureLogger.d("TestTag", "Transfer initiated to merchant@okhdfcbank successfully")

        assertEquals(1, loggedEntries.size)
        val msg = loggedEntries[0].third
        assertFalse("VPA handle must not appear in logcat", msg.contains("merchant@okhdfcbank"))
        assertTrue("VPA should be redacted", msg.contains("[REDACTED_VPA]"))
    }

    @Test
    fun testTransactionReferenceRedactionInSecureLogger() {
        SecureLogger.d("TestTag", "Transaction completed with UTR: 987654321012 and Ref: TXN99887766")

        assertEquals(1, loggedEntries.size)
        val msg = loggedEntries[0].third
        assertFalse("UTR digits must not appear in logcat", msg.contains("987654321012"))
        assertFalse("Ref code must not appear in logcat", msg.contains("TXN99887766"))
        assertTrue("UTR redacted", msg.contains("UTR [REDACTED]"))
        assertTrue("Ref redacted", msg.contains("Ref [REDACTED]"))
    }

    @Test
    fun testCurrencyAndAmountRedactionInSecureLogger() {
        SecureLogger.d("AmountTag", "Paid ₹4,500.00 at Store. New balance: Rs. 15,250. Also charged USD 99.50 and EUR 50")

        assertEquals(1, loggedEntries.size)
        val msg = loggedEntries[0].third
        assertFalse("Raw rupee amount must not appear in logcat", msg.contains("4,500.00"))
        assertFalse("Raw balance must not appear in logcat", msg.contains("15,250"))
        assertFalse("Raw USD amount must not appear in logcat", msg.contains("99.50"))
        assertFalse("Raw EUR amount must not appear in logcat", msg.contains("50"))
        assertTrue("Redacted amount placeholder present", msg.contains("[REDACTED_AMOUNT]"))
    }

    @Test
    fun testDiagnosticEventManagerSanitizesReasonPayloads() {
        val event = DiagnosticEventManager.logEvent(
            category = DiagnosticCategory.DUPLICATE_SKIPPED,
            packageName = "com.bank.app",
            reason = "Duplicate of ₹1,500.00 with card 4111222233334444 and OTP 123456"
        )

        assertNotNull(event.reason)
        assertFalse("DiagnosticEvent reason must not contain raw amount", event.reason!!.contains("1,500.00"))
        assertFalse("DiagnosticEvent reason must not contain card number", event.reason!!.contains("4111222233334444"))
        assertFalse("DiagnosticEvent reason must not contain raw OTP", event.reason!!.contains("123456"))
        assertTrue("DiagnosticEvent reason should contain masked card suffix", event.reason!!.contains("•••• 4444"))
    }

    @Test
    fun testExceptionSanitizationInSecureLogger() {
        val sqliteException = Exception("android.database.sqlite.SQLiteConstraintException: UNIQUE constraint failed: expenses.id at query SELECT * FROM expenses")
        SecureLogger.e("DbTag", "Insertion failed", sqliteException)

        assertEquals(1, loggedEntries.size)
        val msg = loggedEntries[0].third
        assertFalse("SQL query details must not leak in logcat", msg.contains("SELECT * FROM expenses"))
        assertFalse("Table name must not leak in logcat", msg.contains("expenses.id"))
        assertTrue("Database operation failure safely messaged", msg.contains("Database operation failed safely"))
        assertTrue("Exception class name preserved for debugging", msg.contains("[Exception]"))
    }

    // =========================================================================
    // 2. NOTIFICATION CAPTURE LOGGING LEAK ELIMINATION TESTS
    // =========================================================================

    @Test
    fun testNotificationCaptureLoggingDoesNotExposeRawPayload() {
        NotificationPermissionHelper.permissionOverrideForTesting = true
        val service = NotificationReaderService()

        val captured = service.recordCapturedNotification(
            packageName = "com.bank.app",
            notificationKey = "bank_key_123",
            postTime = 1720000000000L,
            title = "HDFC Bank Alert",
            text = "Rs. 4,500.00 debited from A/C ending 9876 at Starbucks via UPI",
            bigText = "Rs. 4,500.00 debited from A/C ending 9876 at Starbucks via UPI. Avl Bal: Rs. 50,000.00",
            subText = "Bal: Rs. 50,000",
            summaryText = "Transaction Alert",
            infoText = "INR 4500",
            textLines = listOf("Line 1: Rs. 4,500 debited", "Line 2: Bal 50,000"),
            category = "msg",
            channelId = "tx_channel"
        )

        assertNotNull("Notification should be captured", captured)
        // Check what got logged
        val captureLogs = loggedEntries.filter { it.second == "NotificationReader" }
        assertTrue("Diagnostic log must be emitted", captureLogs.isNotEmpty())

        val loggedMsg = captureLogs.first().third

        // Strict assertions: raw text, amounts, balances, and merchant names must NEVER appear in the log!
        assertFalse("Raw notification text must not be logged", loggedMsg.contains("debited from A/C"))
        assertFalse("Financial amount must not be logged", loggedMsg.contains("4,500"))
        assertFalse("Account balance must not be logged", loggedMsg.contains("50,000"))
        assertFalse("Merchant name must not be logged", loggedMsg.contains("Starbucks"))
        assertFalse("Raw text lines must not be logged", loggedMsg.contains("Line 1"))

        // Metadata assertions: structured diagnostic presence flags must be present
        assertTrue("Package must be present", loggedMsg.contains("package=com.bank.app"))
        assertTrue("Key must be present", loggedMsg.contains("key=bank_key_123"))
        assertTrue("hasTitle boolean flag present", loggedMsg.contains("hasTitle=true"))
        assertTrue("hasText boolean flag present", loggedMsg.contains("hasText=true"))
        assertTrue("hasBigText boolean flag present", loggedMsg.contains("hasBigText=true"))
        assertTrue("textLinesCount present", loggedMsg.contains("textLinesCount=2"))
    }

    // =========================================================================
    // 3. BACKUP RULES & DATA EXTRACTION RULES VERIFICATION
    // =========================================================================

    @Test
    fun testLegacyBackupRulesXmlExcludesDatabase() {
        val file = File("src/main/res/xml/backup_rules.xml").let {
            if (it.exists()) it else File("app/src/main/res/xml/backup_rules.xml")
        }
        assertTrue("backup_rules.xml must exist", file.exists())

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = doc.documentElement
        assertEquals("full-backup-content", root.tagName)

        val excludes = root.getElementsByTagName("exclude")
        val excludedDomains = mutableListOf<Pair<String, String>>()
        for (i in 0 until excludes.length) {
            val el = excludes.item(i) as Element
            excludedDomains.add(Pair(el.getAttribute("domain"), el.getAttribute("path")))
        }

        assertTrue("Must exclude database domain '.'",
            excludedDomains.any { it.first == "database" && it.second == "." })
        assertTrue("Must exclude arctracker_database specifically",
            excludedDomains.any { it.first == "database" && it.second == "arctracker_database" })
        assertTrue("Must exclude root/databases directory",
            excludedDomains.any { it.first == "root" && it.second == "databases" })
        assertTrue("Must exclude file domain '.'",
            excludedDomains.any { it.first == "file" && it.second == "." })
        assertTrue("Must exclude external domain '.'",
            excludedDomains.any { it.first == "external" && it.second == "." })

        val includes = root.getElementsByTagName("include")
        val includedDomains = mutableListOf<Pair<String, String>>()
        for (i in 0 until includes.length) {
            val el = includes.item(i) as Element
            includedDomains.add(Pair(el.getAttribute("domain"), el.getAttribute("path")))
        }
        assertTrue("Must include sharedpref for preferences",
            includedDomains.any { it.first == "sharedpref" && it.second == "." })
    }

    @Test
    fun testDataExtractionRulesXmlExcludesDatabaseFromCloudAndDeviceTransfer() {
        val file = File("src/main/res/xml/data_extraction_rules.xml").let {
            if (it.exists()) it else File("app/src/main/res/xml/data_extraction_rules.xml")
        }
        assertTrue("data_extraction_rules.xml must exist", file.exists())

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = doc.documentElement
        assertEquals("data-extraction-rules", root.tagName)

        // 1. Verify cloud-backup exclusions
        val cloudNodes = root.getElementsByTagName("cloud-backup")
        assertEquals(1, cloudNodes.length)
        val cloudElement = cloudNodes.item(0) as Element

        val cloudExcludes = cloudElement.getElementsByTagName("exclude")
        val cloudExcludedList = mutableListOf<Pair<String, String>>()
        for (i in 0 until cloudExcludes.length) {
            val el = cloudExcludes.item(i) as Element
            cloudExcludedList.add(Pair(el.getAttribute("domain"), el.getAttribute("path")))
        }
        assertTrue("Cloud backup must exclude database domain '.'",
            cloudExcludedList.any { it.first == "database" && it.second == "." })
        assertTrue("Cloud backup must exclude arctracker_database",
            cloudExcludedList.any { it.first == "database" && it.second == "arctracker_database" })
        assertTrue("Cloud backup must exclude root/databases",
            cloudExcludedList.any { it.first == "root" && it.second == "databases" })
        assertTrue("Cloud backup must exclude file domain '.'",
            cloudExcludedList.any { it.first == "file" && it.second == "." })
        assertTrue("Cloud backup must exclude external domain '.'",
            cloudExcludedList.any { it.first == "external" && it.second == "." })

        // 2. Verify device-transfer exclusions
        val deviceNodes = root.getElementsByTagName("device-transfer")
        assertEquals(1, deviceNodes.length)
        val deviceElement = deviceNodes.item(0) as Element

        val deviceExcludes = deviceElement.getElementsByTagName("exclude")
        val deviceExcludedList = mutableListOf<Pair<String, String>>()
        for (i in 0 until deviceExcludes.length) {
            val el = deviceExcludes.item(i) as Element
            deviceExcludedList.add(Pair(el.getAttribute("domain"), el.getAttribute("path")))
        }
        assertTrue("Device transfer must exclude database domain '.'",
            deviceExcludedList.any { it.first == "database" && it.second == "." })
        assertTrue("Device transfer must exclude arctracker_database",
            deviceExcludedList.any { it.first == "database" && it.second == "arctracker_database" })
        assertTrue("Device transfer must exclude root/databases",
            deviceExcludedList.any { it.first == "root" && it.second == "databases" })
        assertTrue("Device transfer must exclude file domain '.'",
            deviceExcludedList.any { it.first == "file" && it.second == "." })
        assertTrue("Device transfer must exclude external domain '.'",
            deviceExcludedList.any { it.first == "external" && it.second == "." })
    }

    @Test
    fun testTransactionDeduplicatorEmitsNoRawAmountsOrReferencesInReasons() {
        val captured1 = CapturedNotificationInfo(
            packageName = "com.bank.app",
            notificationKey = "key_1",
            postTime = 1000000L,
            text = "Debited ₹500. UTR: ABC123456789"
        )
        val normalized1 = NotificationNormalizer.normalize(captured1)!!
        val classification1 = FinancialClassifier.classify(normalized1)
        val candidate1 = StructuredTransactionExtractor.extractDirect(normalized1, classification1)
        val validated1 = TransactionValidator.validate(candidate1, classification1)

        val captured2 = CapturedNotificationInfo(
            packageName = "com.bank.app",
            notificationKey = "key_2",
            postTime = 1000000L,
            text = "Debited ₹5,000. UTR: ABC123456789"
        )
        val normalized2 = NotificationNormalizer.normalize(captured2)!!
        val classification2 = FinancialClassifier.classify(normalized2)
        val candidate2 = StructuredTransactionExtractor.extractDirect(normalized2, classification2)
        val validated2 = TransactionValidator.validate(candidate2, classification2)

        val existing = listOf(TransactionRecord.fromValidated(validated1))
        val result = TransactionDeduplicator.evaluate(validated2, existing)

        assertNotNull(result.reason)
        assertFalse("Reason must not expose raw amounts", result.reason.contains("500") || result.reason.contains("5000"))
        assertFalse("Reason must not expose raw reference IDs", result.reason.contains("ABC123456789"))
        assertFalse("Conflicting signals must not expose raw amounts", result.conflictingSignals.any { it.contains("500") || it.contains("5000") })
    }
}
