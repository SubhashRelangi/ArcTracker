package com.subhashrelangi.arctracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subhashrelangi.arctracker.service.NotificationPermissionHelper
import com.subhashrelangi.arctracker.service.NotificationReaderService
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test executing on a real device/emulator to verify:
 * - title + text
 * - expanded bigText
 * - multiple textLines
 * - grouped notification
 * - updated notification
 */
@RunWith(AndroidJUnit4::class)
class NotificationDeviceCaptureTest {

    private lateinit var context: Context
    private lateinit var service: NotificationReaderService

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        NotificationPermissionHelper.permissionOverrideForTesting = true
        NotificationReaderService.clearActiveNotifications()
        service = NotificationReaderService()
    }

    @After
    fun tearDown() {
        NotificationPermissionHelper.permissionOverrideForTesting = null
        NotificationReaderService.clearActiveNotifications()
    }

    private fun createNotificationBuilder(channelId: String = "test_channel"): Notification.Builder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(channelId, "Test Channel", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(channel)
            Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
    }

    // 1. Device test: title + text
    @Test
    fun testDevice_titleAndText() {
        val notification = createNotificationBuilder()
            .setContentTitle("Device Google Pay")
            .setContentText("Paid ₹350 at Cafe")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        val title = NotificationReaderService.extractTextField(notification.extras, Notification.EXTRA_TITLE)
        val text = NotificationReaderService.extractTextField(notification.extras, Notification.EXTRA_TEXT)

        assertEquals("Device Google Pay", title)
        assertEquals("Paid ₹350 at Cafe", text)

        val captured = service.recordCapturedNotification(
            packageName = context.packageName,
            notificationKey = "device_key_1",
            postTime = System.currentTimeMillis(),
            title = title,
            text = text
        )

        assertNotNull(captured)
        assertEquals("Device Google Pay", captured?.title)
        assertEquals("Paid ₹350 at Cafe", captured?.text)
        assertFalse(captured?.isUpdate == true)
    }

    // 2. Device test: expanded bigText
    @Test
    fun testDevice_expandedBigText() {
        val bigTextStyle = Notification.BigTextStyle()
            .setBigContentTitle("PhonePe Payment Successful")
            .bigText("₹1,200 transferred to John Doe. UPI Transaction ID: 123456789012. Bank Ref: 987654.")

        val notification = createNotificationBuilder()
            .setContentTitle("PhonePe")
            .setContentText("Paid ₹1,200")
            .setStyle(bigTextStyle)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        val title = NotificationReaderService.extractTextField(notification.extras, Notification.EXTRA_TITLE)
        val text = NotificationReaderService.extractTextField(notification.extras, Notification.EXTRA_TEXT)
        val bigText = NotificationReaderService.extractTextField(notification.extras, Notification.EXTRA_BIG_TEXT)

        assertEquals("PhonePe", title)
        assertEquals("₹1,200 transferred to John Doe. UPI Transaction ID: 123456789012. Bank Ref: 987654.", bigText)

        val captured = service.recordCapturedNotification(
            packageName = "com.phonepe.app",
            notificationKey = "device_key_bigtext",
            postTime = System.currentTimeMillis(),
            title = title,
            text = text,
            bigText = bigText
        )

        assertNotNull(captured)
        assertEquals("PhonePe", captured?.title)
        assertEquals("₹1,200 transferred to John Doe. UPI Transaction ID: 123456789012. Bank Ref: 987654.", captured?.bigText)
    }

    // 3. Device test: multiple textLines (InboxStyle)
    @Test
    fun testDevice_multipleTextLines() {
        val inboxStyle = Notification.InboxStyle()
            .addLine("₹500 paid to Swiggy")
            .addLine("₹250 paid to Uber")
            .addLine("₹1,200 paid to Amazon")
            .setSummaryText("3 transactions today")

        val notification = createNotificationBuilder()
            .setContentTitle("Daily Summary")
            .setStyle(inboxStyle)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        val textLines = NotificationReaderService.extractTextLines(notification.extras)
        val summaryText = NotificationReaderService.extractTextField(notification.extras, Notification.EXTRA_SUMMARY_TEXT)

        assertEquals(3, textLines.size)
        assertEquals("₹500 paid to Swiggy", textLines[0])
        assertEquals("₹250 paid to Uber", textLines[1])
        assertEquals("₹1,200 paid to Amazon", textLines[2])
        assertEquals("3 transactions today", summaryText)

        val captured = service.recordCapturedNotification(
            packageName = "com.bank.app",
            notificationKey = "device_key_lines",
            postTime = System.currentTimeMillis(),
            title = "Daily Summary",
            summaryText = summaryText,
            textLines = textLines
        )

        assertNotNull(captured)
        assertEquals(3, captured?.textLines?.size)
        assertEquals("3 transactions today", captured?.summaryText)
    }

    // 4. Device test: grouped notification
    @Test
    fun testDevice_groupedNotification() {
        val groupKey = "banking_alerts_group"
        val childNotification = createNotificationBuilder()
            .setContentTitle("HDFC Bank Alert")
            .setContentText("Rs 5,000 debited from a/c **1234")
            .setGroup(groupKey)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        val capturedChild = service.recordCapturedNotification(
            packageName = "com.snapwork.hdfc",
            notificationKey = "hdfc_child_1",
            postTime = System.currentTimeMillis(),
            title = "HDFC Bank Alert",
            text = "Rs 5,000 debited from a/c **1234",
            groupKey = groupKey,
            isGroup = true,
            isGroupSummary = false
        )

        val summaryNotification = createNotificationBuilder()
            .setContentTitle("HDFC Bank")
            .setContentText("2 alerts")
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        val capturedSummary = service.recordCapturedNotification(
            packageName = "com.snapwork.hdfc",
            notificationKey = "hdfc_summary",
            postTime = System.currentTimeMillis() + 10,
            title = "HDFC Bank",
            text = "2 alerts",
            groupKey = groupKey,
            isGroup = true,
            isGroupSummary = true,
            flags = summaryNotification.flags
        )

        assertNotNull(capturedChild)
        assertEquals(groupKey, capturedChild?.groupKey)
        assertTrue(capturedChild?.isGroup == true)
        assertFalse(capturedChild?.isGroupSummary == true)

        assertNotNull(capturedSummary)
        assertEquals(groupKey, capturedSummary?.groupKey)
        assertTrue(capturedSummary?.isGroup == true)
        assertTrue(capturedSummary?.isGroupSummary == true)
    }

    // 5. Device test: updated notification
    @Test
    fun testDevice_updatedNotification() {
        val key = "device_gpay_payment_101"

        // First state: Pending/Processing
        val initial = service.recordCapturedNotification(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = key,
            postTime = 1000L,
            title = "Google Pay",
            text = "Processing payment of ₹500..."
        )

        assertNotNull(initial)
        assertFalse(initial!!.isUpdate)

        // Updated state: Completed
        val updated = service.recordCapturedNotification(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = key,
            postTime = 1500L,
            title = "Google Pay",
            text = "Payment of ₹500 to Amazon successful!",
            bigText = "Paid ₹500. UPI Ref: 998877665544"
        )

        assertNotNull(updated)
        assertTrue(updated!!.isUpdate)

        val active = NotificationReaderService.getActiveNotification(key)
        assertEquals("Payment of ₹500 to Amazon successful!", active?.text)
        assertEquals("Paid ₹500. UPI Ref: 998877665544", active?.bigText)
        assertEquals(active, NotificationReaderService.lastCapturedNotification)
    }

    // 6. Device test: End-to-end Capture -> Normalization -> Classification -> Extraction Pipeline (Step 5)
    @Test
    fun testDevice_endToEndExtractionPipeline() {
        val bigTextStyle = Notification.BigTextStyle()
            .setBigContentTitle("Payment successful")
            .bigText("₹500 paid to Ravi. UPI Ref: 123456789012. Avl Bal: ₹9,500")

        val notification = createNotificationBuilder()
            .setContentTitle("Payment successful")
            .setContentText("₹500 paid to Ravi")
            .setStyle(bigTextStyle)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        // 1. Capture
        val title = NotificationReaderService.extractTextField(notification.extras, Notification.EXTRA_TITLE)
        val text = NotificationReaderService.extractTextField(notification.extras, Notification.EXTRA_TEXT)
        val bigText = NotificationReaderService.extractTextField(notification.extras, Notification.EXTRA_BIG_TEXT)

        val captured = service.recordCapturedNotification(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "device_key_step5_pipeline",
            postTime = System.currentTimeMillis(),
            title = title,
            text = text,
            bigText = bigText
        )
        assertNotNull(captured)

        // 2. Normalization
        val normalized = com.subhashrelangi.arctracker.service.NotificationNormalizer.normalize(captured)
        assertNotNull(normalized)

        // 3. Financial Classification
        val classification = com.subhashrelangi.arctracker.service.FinancialClassifier.classify(normalized!!)
        assertEquals(com.subhashrelangi.arctracker.service.FinancialRelevance.FINANCIAL, classification.financialRelevance)
        assertFalse(classification.isNoise)

        // 4. Structured Extraction
        val candidate = com.subhashrelangi.arctracker.service.StructuredTransactionExtractor.extract(classification)
        assertNotNull(candidate)
        assertEquals(500.0, candidate?.amount)
        assertEquals("Ravi", candidate?.merchant)
        assertEquals("123456789012", candidate?.upiTransactionId)
        assertEquals(com.subhashrelangi.arctracker.service.TransactionStatus.SUCCESS, candidate?.status)
        assertEquals(com.subhashrelangi.arctracker.service.TransactionDirection.DEBIT, candidate?.direction)

        // Verify balance is separated as secondary amount
        assertNotEquals(9500.0, candidate?.amount)
        val bal = candidate?.secondaryAmounts?.find { it.type == com.subhashrelangi.arctracker.service.SecondaryAmountType.BALANCE }
        assertNotNull(bal)
        assertEquals(9500.0, bal?.amount)
    }

    // 7. Device test: End-to-end Capture -> Normalization -> Classification -> Extraction -> Validation Pipeline (Step 6)
    @Test
    fun testDevice_endToEndValidationPipeline() {
        val bigTextStyle = Notification.BigTextStyle()
            .setBigContentTitle("Payment successful")
            .bigText("₹500 paid to Ravi. UPI Ref: 123456789012. Avl Bal: ₹9,500")

        val notification = createNotificationBuilder()
            .setContentTitle("Payment successful")
            .setContentText("₹500 paid to Ravi")
            .setStyle(bigTextStyle)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        // 1. Capture
        val title = NotificationReaderService.extractTextField(notification.extras, Notification.EXTRA_TITLE)
        val text = NotificationReaderService.extractTextField(notification.extras, Notification.EXTRA_TEXT)
        val bigText = NotificationReaderService.extractTextField(notification.extras, Notification.EXTRA_BIG_TEXT)

        val captured = service.recordCapturedNotification(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "device_key_step6_pipeline",
            postTime = System.currentTimeMillis(),
            title = title,
            text = text,
            bigText = bigText
        )
        assertNotNull(captured)

        // 2. Normalization
        val normalized = com.subhashrelangi.arctracker.service.NotificationNormalizer.normalize(captured)
        assertNotNull(normalized)

        // 3. Financial Classification
        val classification = com.subhashrelangi.arctracker.service.FinancialClassifier.classify(normalized!!)
        assertEquals(com.subhashrelangi.arctracker.service.FinancialRelevance.FINANCIAL, classification.financialRelevance)

        // 4. Structured Extraction
        val candidate = com.subhashrelangi.arctracker.service.StructuredTransactionExtractor.extract(classification)
        assertNotNull(candidate)

        // 5. Validation + Confidence (Step 6)
        val validationResult = com.subhashrelangi.arctracker.service.TransactionValidator.validate(candidate!!, classification)

        assertEquals(com.subhashrelangi.arctracker.service.ValidationState.ACCEPTABLE, validationResult.validationState)
        assertEquals(com.subhashrelangi.arctracker.service.EvidenceLevel.VERY_STRONG, validationResult.evidenceLevel)
        assertTrue(validationResult.isStructurallyValid)
        assertTrue(validationResult.isAcceptable)
        assertFalse(validationResult.isRejected)
        assertFalse(validationResult.needsReview)

        // Verify explainable evidence reasons
        assertTrue(validationResult.validationReasons.isNotEmpty())
        assertTrue(validationResult.supportingSignals.contains("VALID_POSITIVE_AMOUNT"))
        assertTrue(validationResult.supportingSignals.contains("DIRECTION_DEBIT"))
        assertTrue(validationResult.supportingSignals.contains("VALID_MERCHANT_NAME"))
    }

    // 8. Device test: End-to-end Capture -> Normalization -> Classification -> Extraction -> Validation -> Deduplication (Step 7)
    @Test
    fun testDevice_endToEndDeduplicationPipeline() {
        val bigTextStyle = Notification.BigTextStyle()
            .setBigContentTitle("Payment successful")
            .bigText("₹500 paid to Ravi. UPI Ref: 123456789012. Avl Bal: ₹9,500")

        val notification = createNotificationBuilder()
            .setContentTitle("Payment successful")
            .setContentText("₹500 paid to Ravi")
            .setStyle(bigTextStyle)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        // 1. Capture
        val title = NotificationReaderService.extractTextField(notification.extras, Notification.EXTRA_TITLE)
        val text = NotificationReaderService.extractTextField(notification.extras, Notification.EXTRA_TEXT)
        val bigText = NotificationReaderService.extractTextField(notification.extras, Notification.EXTRA_BIG_TEXT)

        val captured = service.recordCapturedNotification(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "device_key_step7_pipeline",
            postTime = System.currentTimeMillis(),
            title = title,
            text = text,
            bigText = bigText
        )
        assertNotNull(captured)

        // 2. Normalization
        val normalized = com.subhashrelangi.arctracker.service.NotificationNormalizer.normalize(captured)
        assertNotNull(normalized)

        // 3. Financial Classification
        val classification = com.subhashrelangi.arctracker.service.FinancialClassifier.classify(normalized!!)
        assertEquals(com.subhashrelangi.arctracker.service.FinancialRelevance.FINANCIAL, classification.financialRelevance)

        // 4. Structured Extraction
        val candidate = com.subhashrelangi.arctracker.service.StructuredTransactionExtractor.extract(classification)
        assertNotNull(candidate)

        // 5. Validation + Confidence (Step 6)
        val validationResult = com.subhashrelangi.arctracker.service.TransactionValidator.validate(candidate!!, classification)
        assertTrue(validationResult.isAcceptable)

        // 6. Deduplication + Cross-Source Correlation (Step 7)
        // Existing bank SMS record received 5 seconds earlier for the same payment
        val existingSms = com.subhashrelangi.arctracker.service.TransactionRecord(
            id = "sms_device_100",
            sourceType = com.subhashrelangi.arctracker.service.TransactionSourceType.SMS_HISTORY,
            amount = 500.0,
            direction = com.subhashrelangi.arctracker.service.TransactionDirection.DEBIT,
            upiTransactionId = "123456789012",
            timestamp = System.currentTimeMillis() - 5000L
        )

        val dedupResult = com.subhashrelangi.arctracker.service.TransactionDeduplicator.evaluate(
            candidate = validationResult,
            existingRecords = listOf(existingSms)
        )

        assertEquals(com.subhashrelangi.arctracker.service.DedupDecision.CORRELATED, dedupResult.decision)
        assertEquals(com.subhashrelangi.arctracker.service.MatchStrategy.EXPLICIT_REFERENCE_ID, dedupResult.strategy)
        assertEquals("sms_device_100", dedupResult.matchedRecordId)
        assertTrue(dedupResult.isCorrelated)
        assertFalse(dedupResult.isNew)
        assertFalse(dedupResult.isDuplicate)
        assertTrue(dedupResult.matchingSignals.any { it.contains("REFERENCE_ID_MATCH") })
    }
}
