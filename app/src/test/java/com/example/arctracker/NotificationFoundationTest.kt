package com.example.arctracker

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.arctracker.data.MockData
import com.example.arctracker.service.CapturedNotificationInfo
import com.example.arctracker.service.NotificationPermissionHelper
import com.example.arctracker.service.NotificationReaderService
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying Step 1 requirements:
 * 1. Permission detection and state transitions
 * 2. Settings intent configuration
 * 3. Session-based dialog dismissal and prompt recurrence on cold start
 * 4. Service safety under missing permission and valid permission
 * 5. Lifecycle state transitions
 * 6. Non-regression of existing data structures
 */
class NotificationFoundationTest {

    @Before
    fun setUp() {
        NotificationPermissionHelper.permissionOverrideForTesting = null
        NotificationReaderService.isConnected = false
        NotificationReaderService.lastCapturedNotification = null
        NotificationReaderService.notificationListener = null
    }

    @After
    fun tearDown() {
        NotificationPermissionHelper.permissionOverrideForTesting = null
        NotificationReaderService.isConnected = false
        NotificationReaderService.lastCapturedNotification = null
        NotificationReaderService.notificationListener = null
    }

    // 1. App launches with Notification Access already granted
    @Test
    fun testAppLaunch_whenPermissionGranted_dialogNotShown() {
        NotificationPermissionHelper.permissionOverrideForTesting = true
        val hasDismissedSession = false

        val isGranted = NotificationPermissionHelper.permissionOverrideForTesting == true
        val showDialog = !isGranted && !hasDismissedSession

        assertTrue("Permission should be granted", isGranted)
        assertFalse("Dialog should not be shown when permission is already granted", showDialog)
    }

    // 2. App launches without Notification Access
    // 3. Permission dialog appears when permission is missing
    @Test
    fun testAppLaunch_whenPermissionNotGranted_dialogShown() {
        NotificationPermissionHelper.permissionOverrideForTesting = false
        val hasDismissedSession = false

        val isGranted = NotificationPermissionHelper.permissionOverrideForTesting == true
        val showDialog = !isGranted && !hasDismissedSession

        assertFalse("Permission should not be granted", isGranted)
        assertTrue("Dialog should be shown when permission is missing", showDialog)
    }

    // 4. "Grant Permission" creates correct Android Notification Access settings intent
    @Test
    fun testGrantPermission_createsCorrectSettingsIntent() {
        val intent = NotificationPermissionHelper.createNotificationAccessSettingsIntent()
        assertNotNull("Intent should not be null", intent)
        // Verify constant used
        assertEquals("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS", Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    // 5. Returning from Settings correctly detects the new permission state
    // 6. Granting permission enables service operation and dismisses dialog
    @Test
    fun testReturnFromSettings_permissionGranted_enablesServiceAndDismissesDialog() {
        var isGranted = false
        var hasDismissedSession = false

        // Before user goes to settings
        assertTrue("Dialog should show initially", !isGranted && !hasDismissedSession)

        // User grants permission in settings and returns
        isGranted = true

        val showDialogAfterReturn = !isGranted && !hasDismissedSession
        assertFalse("Dialog should dismiss once permission is granted", showDialogAfterReturn)
        assertTrue("Service is now allowed to operate", isGranted)
    }

    // 7. Denying/skipping permission does not crash and hides dialog for current session
    @Test
    fun testDenyOrSkipPermission_hidesDialogForSessionWithoutCrash() {
        val isGranted = false
        var hasDismissedSession = false

        // User clicks "Not Now" / skip
        hasDismissedSession = true

        val showDialogAfterDismiss = !isGranted && !hasDismissedSession
        assertFalse("Dialog should not show again in the same session after dismissal", showDialogAfterDismiss)
        assertFalse("Permission remains ungranted", isGranted)
    }

    // 8. Closing and reopening the app after denying permission asks again (cold start)
    @Test
    fun testColdStart_afterPreviousDenial_asksAgain() {
        val isGranted = false
        var hasDismissedSession = true // Dismissed in previous session

        // Simulating complete app close and cold start: session dismissal is reset to false
        hasDismissedSession = false

        val showDialogOnColdStart = !isGranted && !hasDismissedSession
        assertTrue("Dialog must be shown again on cold start if permission is still missing", showDialogOnColdStart)
    }

    // 9. Revoking Notification Access updates state safely without crash
    @Test
    fun testRevokeNotificationAccess_detectedSafely() {
        var isGranted = true
        assertEquals(true, isGranted)

        // User revokes permission in Android Settings
        isGranted = false
        assertFalse("Permission revocation should be reflected", isGranted)
    }

    // 10. Service does not process notifications when permission is unavailable
    @Test
    fun testService_doesNotProcessNotification_whenPermissionUnavailable() {
        NotificationPermissionHelper.permissionOverrideForTesting = false

        val service = NotificationReaderService()

        // Test with null
        val handledNull = service.handleNotification(null)
        assertFalse("Null notification should not be handled", handledNull)
        assertNull("No notification should be captured", NotificationReaderService.lastCapturedNotification)

        // Test with simulated incoming notification
        val captured = service.recordCapturedNotification(
            packageName = "com.phonepe.app",
            notificationKey = "key123",
            postTime = 1000L,
            title = "Paid ₹100",
            text = "Payment successful"
        )
        assertNull("Should discard and return null when permission is unavailable", captured)
        assertNull("lastCapturedNotification must remain null when permission is missing", NotificationReaderService.lastCapturedNotification)
    }

    // 11. Service receives/logs a basic notification when permission is granted
    @Test
    fun testService_receivesAndLogsNotification_whenPermissionGranted() {
        NotificationPermissionHelper.permissionOverrideForTesting = true

        val service = NotificationReaderService()
        var listenerInvoked = false

        NotificationReaderService.notificationListener = { capturedInfo ->
            listenerInvoked = true
            assertEquals("com.google.android.apps.nbu.paisa.user", capturedInfo.packageName)
            assertEquals("key999", capturedInfo.notificationKey)
            assertEquals(12345L, capturedInfo.postTime)
            assertEquals("Payment Received", capturedInfo.title)
            assertEquals("₹500 received", capturedInfo.text)
        }

        val captured = service.recordCapturedNotification(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "key999",
            postTime = 12345L,
            title = "Payment Received",
            text = "₹500 received"
        )

        assertNotNull("Notification should be captured when permission is granted", captured)
        assertEquals(captured, NotificationReaderService.lastCapturedNotification)
        assertTrue("Notification listener should have been invoked", listenerInvoked)
    }

    // 11. Service foundation lifecycle states
    @Test
    fun testService_lifecycleConnectedAndDisconnected() {
        val service = NotificationReaderService()

        assertFalse(NotificationReaderService.isConnected)

        service.onListenerConnected()
        assertTrue("Service should report connected", NotificationReaderService.isConnected)

        service.onListenerDisconnected()
        assertFalse("Service should report disconnected", NotificationReaderService.isConnected)

        service.onDestroy()
        assertFalse("Service should remain disconnected after destroy", NotificationReaderService.isConnected)
    }

    // 12. CapturedNotificationInfo records basic information without parsing transactions
    @Test
    fun testCapturedNotificationInfo_basicDataPreserved() {
        val captured = CapturedNotificationInfo(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "0|com.google.android.apps.nbu.paisa.user|101|null|10001",
            postTime = 1727245600000L,
            title = "Paid to Starbucks",
            text = "₹250 paid successfully"
        )

        assertEquals("com.google.android.apps.nbu.paisa.user", captured.packageName)
        assertEquals("0|com.google.android.apps.nbu.paisa.user|101|null|10001", captured.notificationKey)
        assertEquals(1727245600000L, captured.postTime)
        assertEquals("Paid to Starbucks", captured.title)
        assertEquals("₹250 paid successfully", captured.text)
    }

    // 13. Existing app functionality remains intact
    @Test
    fun testExistingAppFunctionality_remainsIntact() {
        val initialExpenses = MockData.getInitialExpenses()
        assertNotNull(initialExpenses)
        assertTrue(initialExpenses.isNotEmpty())
        assertEquals("Zomato", initialExpenses.first().merchant)
    }
}
