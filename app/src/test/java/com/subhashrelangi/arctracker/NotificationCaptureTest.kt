package com.subhashrelangi.arctracker

import android.app.Notification
import com.subhashrelangi.arctracker.service.CapturedNotificationInfo
import com.subhashrelangi.arctracker.service.NotificationPermissionHelper
import com.subhashrelangi.arctracker.service.NotificationReaderService
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying Step 2: Complete Notification Capture.
 * Tests all required capture scenarios, extractions, updates, groups, and edge cases.
 */
class NotificationCaptureTest {

    private lateinit var service: NotificationReaderService

    @Before
    fun setUp() {
        NotificationPermissionHelper.permissionOverrideForTesting = true
        NotificationReaderService.isConnected = false
        NotificationReaderService.lastCapturedNotification = null
        NotificationReaderService.notificationListener = null
        NotificationReaderService.clearActiveNotifications()
        service = NotificationReaderService()
    }

    @After
    fun tearDown() {
        NotificationPermissionHelper.permissionOverrideForTesting = null
        NotificationReaderService.isConnected = false
        NotificationReaderService.lastCapturedNotification = null
        NotificationReaderService.notificationListener = null
        NotificationReaderService.clearActiveNotifications()
    }

    // 1. Notification containing only title and text
    @Test
    fun testCapture_titleAndTextOnly() {
        val captured = service.recordCapturedNotification(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "key_1",
            postTime = 1000L,
            title = "Google Pay",
            text = "Payment successful"
        )

        assertNotNull(captured)
        assertEquals("com.google.android.apps.nbu.paisa.user", captured?.packageName)
        assertEquals("key_1", captured?.notificationKey)
        assertEquals(1000L, captured?.postTime)
        assertEquals("Google Pay", captured?.title)
        assertEquals("Payment successful", captured?.text)
        assertNull("bigText should be null", captured?.bigText)
        assertNull("subText should be null", captured?.subText)
        assertNull("summaryText should be null", captured?.summaryText)
        assertNull("infoText should be null", captured?.infoText)
        assertTrue("textLines should be empty", captured?.textLines?.isEmpty() == true)
        assertFalse("isUpdate should be false for first post", captured?.isUpdate == true)
    }

    // 2. Notification containing bigText
    @Test
    fun testCapture_containingBigText() {
        val captured = service.recordCapturedNotification(
            packageName = "com.phonepe.app",
            notificationKey = "key_2",
            postTime = 2000L,
            title = "PhonePe",
            text = "Paid ₹500",
            bigText = "Paid ₹500 to Amazon Pay India. Transaction ID: T123456789"
        )

        assertNotNull(captured)
        assertEquals("PhonePe", captured?.title)
        assertEquals("Paid ₹500", captured?.text)
        assertEquals("Paid ₹500 to Amazon Pay India. Transaction ID: T123456789", captured?.bigText)
    }

    // 3. Notification containing textLines
    @Test
    fun testCapture_containingTextLines() {
        val lines = listOf("Line 1: Paid ₹100", "Line 2: Paid ₹200")
        val captured = service.recordCapturedNotification(
            packageName = "net.one97.paytm",
            notificationKey = "key_3",
            postTime = 3000L,
            title = "Paytm",
            textLines = lines
        )

        assertNotNull(captured)
        assertEquals(2, captured?.textLines?.size)
        assertEquals("Line 1: Paid ₹100", captured?.textLines?.get(0))
        assertEquals("Line 2: Paid ₹200", captured?.textLines?.get(1))
    }

    // 4. Notification containing title + text + bigText + textLines distinctly preserved
    @Test
    fun testCapture_allFieldsDistinctlyPreserved() {
        val lines = listOf("Order placed", "Amount: ₹1,499", "UPI Ref: 987654321")
        val captured = service.recordCapturedNotification(
            packageName = "in.amazon.mShop.android.shopping",
            notificationKey = "key_4",
            postTime = 4000L,
            title = "Amazon Pay",
            text = "Payment of ₹1,499 successful",
            bigText = "Your payment of ₹1,499 to Amazon Seller was successful via UPI Ref 987654321.",
            subText = "UPI",
            summaryText = "amazon@apl",
            infoText = "Verified",
            textLines = lines,
            category = "promo",
            channelId = "transactions_channel"
        )

        assertNotNull(captured)
        // Verify individual fields are preserved separately and not concatenated
        assertEquals("Amazon Pay", captured?.title)
        assertEquals("Payment of ₹1,499 successful", captured?.text)
        assertEquals("Your payment of ₹1,499 to Amazon Seller was successful via UPI Ref 987654321.", captured?.bigText)
        assertEquals("UPI", captured?.subText)
        assertEquals("amazon@apl", captured?.summaryText)
        assertEquals("Verified", captured?.infoText)
        assertEquals(3, captured?.textLines?.size)
        assertEquals("Order placed", captured?.textLines?.get(0))
        assertEquals("Amount: ₹1,499", captured?.textLines?.get(1))
        assertEquals("UPI Ref: 987654321", captured?.textLines?.get(2))
        assertEquals("promo", captured?.category)
        assertEquals("transactions_channel", captured?.channelId)
    }

    // 5. Notification with missing extras (null values)
    @Test
    fun testCapture_missingExtras_safelyHandled() {
        val captured = service.recordCapturedNotification(
            packageName = "com.unknown.app",
            notificationKey = "key_5",
            postTime = 5000L,
            title = null,
            text = null,
            bigText = null,
            subText = null
        )

        assertNotNull(captured)
        assertNull(captured?.title)
        assertNull(captured?.text)
        assertNull(captured?.bigText)
        assertTrue(captured?.textLines?.isEmpty() == true)
    }

    // 6. Extraction helpers with null values
    @Test
    fun testExtraction_nullValues_doesNotCrash() {
        val extractedText = NotificationReaderService.extractTextFromValue(null)
        assertNull(extractedText)

        val extractedLines = NotificationReaderService.extractTextLinesFromValue(null)
        assertTrue(extractedLines.isEmpty())

        val extractedFromEmpty = NotificationReaderService.extractTextLinesFromValue(emptyArray<CharSequence>())
        assertTrue(extractedFromEmpty.isEmpty())
    }

    // 7. Notification with multiple text lines preserving order
    @Test
    fun testExtraction_multipleTextLines_preservesOrder() {
        val rawLines = arrayOf<CharSequence>(
            "Payment successful",
            "₹500 paid to Amazon",
            "UPI Ref: 123456789",
            "Balance: ₹10,000"
        )

        val result = NotificationReaderService.extractTextLinesFromValue(rawLines)
        assertEquals(4, result.size)
        assertEquals("Payment successful", result[0])
        assertEquals("₹500 paid to Amazon", result[1])
        assertEquals("UPI Ref: 123456789", result[2])
        assertEquals("Balance: ₹10,000", result[3])
    }

    // 8. Notification with group information
    @Test
    fun testCapture_groupInformation() {
        val captured = service.recordCapturedNotification(
            packageName = "com.sbi.SBIAnywhere",
            notificationKey = "sbi_child_1",
            postTime = 6000L,
            title = "SBI Alert",
            text = "Txn of ₹2,500 done",
            groupKey = "sbi_alerts_group",
            isGroup = true,
            isGroupSummary = false
        )

        assertNotNull(captured)
        assertEquals("sbi_alerts_group", captured?.groupKey)
        assertTrue("isGroup should be true", captured?.isGroup == true)
        assertFalse("isGroupSummary should be false for child", captured?.isGroupSummary == true)
    }

    // 9. Group summary notification
    @Test
    fun testCapture_groupSummaryNotification() {
        val captured = service.recordCapturedNotification(
            packageName = "com.sbi.SBIAnywhere",
            notificationKey = "sbi_summary",
            postTime = 6050L,
            title = "SBI",
            text = "3 new transactions",
            groupKey = "sbi_alerts_group",
            isGroup = true,
            isGroupSummary = true,
            flags = Notification.FLAG_GROUP_SUMMARY
        )

        assertNotNull(captured)
        assertEquals("sbi_alerts_group", captured?.groupKey)
        assertTrue("isGroup should be true", captured?.isGroup == true)
        assertTrue("isGroupSummary should be true", captured?.isGroupSummary == true)
        assertEquals(Notification.FLAG_GROUP_SUMMARY, captured?.flags)
    }

    // 10. Same notification key received twice distinguishes new from update
    @Test
    fun testUpdate_sameKeyReceivedTwice_distinguishesNewFromUpdate() {
        val key = "unique_notif_key_10"

        val firstPost = service.recordCapturedNotification(
            packageName = "com.phonepe.app",
            notificationKey = key,
            postTime = 7000L,
            title = "PhonePe",
            text = "Payment processing ₹500"
        )

        assertNotNull(firstPost)
        assertFalse("First post must have isUpdate = false", firstPost!!.isUpdate)

        val secondPost = service.recordCapturedNotification(
            packageName = "com.phonepe.app",
            notificationKey = key,
            postTime = 7500L,
            title = "PhonePe",
            text = "Payment successful ₹500 to Amazon"
        )

        assertNotNull(secondPost)
        assertTrue("Second post with same key must have isUpdate = true", secondPost!!.isUpdate)
    }

    // 11. Updated notification with changed content provides latest complete payload
    @Test
    fun testUpdate_makesLatestPayloadAvailable() {
        val key = "gpay_txn_status"

        service.recordCapturedNotification(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = key,
            postTime = 8000L,
            title = "Google Pay",
            text = "Initiating ₹1,000 transfer...",
            bigText = "Connecting to bank..."
        )

        assertEquals("Initiating ₹1,000 transfer...", NotificationReaderService.getActiveNotification(key)?.text)

        // Updated notification received later with same key
        val updated = service.recordCapturedNotification(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = key,
            postTime = 8200L,
            title = "Google Pay",
            text = "Paid ₹1,000 to John Doe",
            bigText = "Payment completed. UPI Ref: 1122334455"
        )

        assertTrue("Should be marked as update", updated?.isUpdate == true)
        // Latest complete payload is available
        val active = NotificationReaderService.getActiveNotification(key)
        assertNotNull(active)
        assertEquals("Paid ₹1,000 to John Doe", active?.text)
        assertEquals("Payment completed. UPI Ref: 1122334455", active?.bigText)
        assertEquals(active, NotificationReaderService.lastCapturedNotification)
    }

    // 12. Notification with unexpected/unsupported extra types handled safely
    @Test
    fun testExtraction_unexpectedExtraTypes_doesNotCrash() {
        // Number passed to text field
        val numResult = NotificationReaderService.extractTextFromValue(123456)
        assertEquals("123456", numResult)

        // CharArray passed
        val charArrayResult = NotificationReaderService.extractTextFromValue(charArrayOf('U', 'P', 'I'))
        assertEquals("UPI", charArrayResult)

        // List of CharSequence passed to text field
        val listResult = NotificationReaderService.extractTextFromValue(listOf<CharSequence>("First line", "Second line"))
        assertEquals("First line", listResult)

        // Unexpected arbitrary object
        val objResult = NotificationReaderService.extractTextFromValue(Any())
        assertNull("Arbitrary unsupported object should return null safely", objResult)

        // Unexpected object in text lines
        val linesResult = NotificationReaderService.extractTextLinesFromValue(listOf(123, 456))
        assertEquals(2, linesResult.size)
        assertEquals("123", linesResult[0])
        assertEquals("456", linesResult[1])
    }

    // 13. Notification Access permission unavailable
    @Test
    fun testCapture_permissionUnavailable_discardsWithoutCrash() {
        NotificationPermissionHelper.permissionOverrideForTesting = false

        val captured = service.recordCapturedNotification(
            packageName = "com.dreamplug.androidapp",
            notificationKey = "cred_key",
            postTime = 9000L,
            title = "CRED",
            text = "Bill paid"
        )

        assertNull("Must return null when permission is unavailable", captured)
        assertNull("lastCapturedNotification must remain null", NotificationReaderService.lastCapturedNotification)
        assertNull("Active notification cache must not contain entry", NotificationReaderService.getActiveNotification("cred_key"))
    }

    // 14. Normal notification with permission granted
    @Test
    fun testCapture_normalNotificationWithPermission_succeedsAndNotifiesListener() {
        NotificationPermissionHelper.permissionOverrideForTesting = true
        var callbackReceived: CapturedNotificationInfo? = null

        NotificationReaderService.notificationListener = { info ->
            callbackReceived = info
        }

        val captured = service.recordCapturedNotification(
            packageName = "com.snapwork.hdfc",
            notificationKey = "hdfc_01",
            postTime = 9500L,
            title = "HDFC Bank",
            text = "Salary credited ₹85,000",
            category = "financial"
        )

        assertNotNull(captured)
        assertEquals(captured, callbackReceived)
        assertEquals(captured, NotificationReaderService.lastCapturedNotification)
        assertEquals("HDFC Bank", captured?.title)
        assertEquals("financial", captured?.category)
    }

    // 15. Listener lifecycle remains safe
    @Test
    fun testLifecycle_remainsSafe() {
        assertFalse(NotificationReaderService.isConnected)

        service.onListenerConnected()
        assertTrue(NotificationReaderService.isConnected)

        service.onListenerDisconnected()
        assertFalse(NotificationReaderService.isConnected)

        service.onDestroy()
        assertFalse(NotificationReaderService.isConnected)
    }
}
