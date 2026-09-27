package com.example.arctracker

import com.example.arctracker.service.NotificationContent
import com.example.arctracker.service.NotificationPermissionHelper
import com.example.arctracker.service.NotificationReaderService
import com.example.arctracker.settings.AppCatalog
import com.example.arctracker.settings.AppCategory
import com.example.arctracker.settings.InMemoryMonitoringSettingsRepository
import com.example.arctracker.settings.MonitoringSettingsRepository
import com.example.arctracker.settings.SupportedApp
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Step 9.11: Unit tests for Tracking Controls, Permission Integration & Settings UX.
 * Verifies the 3-level control hierarchy:
 * Level 1: Automatic Tracking (Master Switch)
 * Level 2: SMS Messages & App Notifications (Tracking Sources)
 * Level 3: Monitored Apps (Individual App Monitoring)
 * Along with Permission Integration, State Preservation, and Privacy Gate enforcement.
 */
class TrackingControlsAndPermissionIntegrationTest {

    private lateinit var service: NotificationReaderService
    private lateinit var repository: InMemoryMonitoringSettingsRepository
    private val gpay = "com.google.android.apps.nbu.paisa.user"
    private val phonepe = "com.phonepe.app"
    private val hdfc = "com.snapwork.hdfc"

    @Before
    fun setUp() {
        NotificationPermissionHelper.permissionOverrideForTesting = true
        NotificationReaderService.isConnected = false
        NotificationReaderService.lastCapturedNotification = null
        NotificationReaderService.notificationListener = null
        NotificationReaderService.clearActiveNotifications()

        repository = InMemoryMonitoringSettingsRepository(
            initialGlobalEnabled = true,
            initialEnabledPackages = setOf(gpay, hdfc),
            initialNotificationTrackingEnabled = true,
            initialSmsTrackingEnabled = true
        )
        NotificationReaderService.settingsRepositoryOverride = repository
        service = NotificationReaderService()
    }

    @After
    fun tearDown() {
        NotificationPermissionHelper.permissionOverrideForTesting = null
        NotificationReaderService.settingsRepositoryOverride = null
        NotificationReaderService.lastCapturedNotification = null
        NotificationReaderService.notificationListener = null
        NotificationReaderService.clearActiveNotifications()
    }

    @Test
    fun test01_automaticTrackingOn_allowsOperations() {
        repository.setGlobalEnabled(true)
        repository.setNotificationTrackingEnabled(true)
        repository.setSmsTrackingEnabled(true)

        val settings = repository.getSettings()
        assertTrue(settings.globalEnabled)
        assertTrue(settings.isNotificationTrackingEnabled)
        assertTrue(repository.isSmsTrackingEnabled())
        assertTrue(settings.isMonitoringActive(gpay))
    }

    @Test
    fun test02_automaticTrackingOff_masterRuntimeGate_blocksNotificationsBeforeContentExtraction() {
        repository.setGlobalEnabled(false)

        var contentExtracted = false
        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "test_key_master_off",
            postTime = System.currentTimeMillis()
        ) {
            contentExtracted = true
            NotificationContent(title = "Test", text = "Paid 50")
        }

        assertFalse("Notification processing must be blocked when master is OFF", result)
        assertFalse("Content extractor MUST NOT be invoked when master is OFF", contentExtracted)
    }

    @Test
    fun test03_automaticTrackingOff_preservesSmsState() {
        repository.setSmsTrackingEnabled(true)
        repository.setGlobalEnabled(false)

        assertFalse(repository.getSettings().globalEnabled)
        assertTrue("SMS state must be preserved when master is toggled OFF", repository.isSmsTrackingEnabled())

        repository.setGlobalEnabled(true)
        assertTrue("SMS state remains ON after master is toggled back ON", repository.isSmsTrackingEnabled())
    }

    @Test
    fun test04_automaticTrackingOff_preservesNotificationState() {
        repository.setNotificationTrackingEnabled(true)
        repository.setGlobalEnabled(false)

        assertFalse(repository.getSettings().globalEnabled)
        assertTrue("Notification tracking state must be preserved when master is toggled OFF", repository.isNotificationTrackingEnabled())

        repository.setGlobalEnabled(true)
        assertTrue("Notification tracking state remains ON after master is toggled back ON", repository.isNotificationTrackingEnabled())
    }

    @Test
    fun test05_automaticTrackingOff_preservesMonitoredAppsState() {
        val originalPackages = setOf(gpay, hdfc)
        repository.setEnabledPackages(originalPackages)

        repository.setGlobalEnabled(false)
        assertEquals("Monitored apps set must NOT be cleared when master is OFF", originalPackages, repository.getSettings().enabledPackages)

        repository.setGlobalEnabled(true)
        assertEquals("Monitored apps set remains identical when master is turned back ON", originalPackages, repository.getSettings().enabledPackages)
    }

    @Test
    fun test06_smsMessagesOn_automaticTrackingOn_allowsSms() {
        repository.setGlobalEnabled(true)
        repository.setSmsTrackingEnabled(true)

        assertTrue(repository.getSettings().globalEnabled)
        assertTrue(repository.isSmsTrackingEnabled())
    }

    @Test
    fun test07_smsMessagesOff_automaticTrackingOn_stopsSms_withoutAffectingNotifications() {
        repository.setGlobalEnabled(true)
        repository.setSmsTrackingEnabled(false)
        repository.setNotificationTrackingEnabled(true)

        assertFalse(repository.isSmsTrackingEnabled())
        assertTrue("Notification tracking must remain ON when SMS is toggled OFF", repository.isNotificationTrackingEnabled())
        assertTrue("Monitored app gpay must remain active when SMS is toggled OFF", repository.getSettings().isMonitoringActive(gpay))

        var contentExtracted = false
        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "test_key_sms_off",
            postTime = System.currentTimeMillis()
        ) {
            contentExtracted = true
            NotificationContent(title = "Payment", text = "Paid Rs 100")
        }

        assertTrue("Notification processing operates normally when only SMS is OFF", result)
        assertTrue("Content must be extracted for allowed notification", contentExtracted)
    }

    @Test
    fun test08_appNotificationsOn_automaticTrackingOn_allowsNotificationIngress() {
        repository.setGlobalEnabled(true)
        repository.setNotificationTrackingEnabled(true)

        var contentExtracted = false
        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "test_key_notif_on",
            postTime = System.currentTimeMillis()
        ) {
            contentExtracted = true
            NotificationContent(title = "Payment", text = "Received 200")
        }

        assertTrue(result)
        assertTrue(contentExtracted)
    }

    @Test
    fun test09_appNotificationsOff_automaticTrackingOn_blocksNotificationsBeforeContentExtraction() {
        repository.setGlobalEnabled(true)
        repository.setNotificationTrackingEnabled(false)

        var contentExtracted = false
        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "test_key_notif_off",
            postTime = System.currentTimeMillis()
        ) {
            contentExtracted = true
            NotificationContent(title = "Payment", text = "Paid 50")
        }

        assertFalse("Notification processing must be blocked when App Notifications is OFF", result)
        assertFalse("Content extractor MUST NOT be invoked when App Notifications is OFF", contentExtracted)
    }

    @Test
    fun test10_appNotificationsOff_preservesIndividualAppSelections() {
        val originalPackages = setOf(gpay, hdfc)
        repository.setEnabledPackages(originalPackages)

        repository.setNotificationTrackingEnabled(false)
        assertEquals("App selections must remain intact when App Notifications is turned OFF", originalPackages, repository.getSettings().enabledPackages)
    }

    @Test
    fun test11_appNotificationsToggledBackOn_restoresAppStates() {
        repository.setEnabledPackages(setOf(gpay))
        repository.setNotificationTrackingEnabled(false)
        assertFalse(repository.getSettings().isMonitoringActive(gpay))

        repository.setNotificationTrackingEnabled(true)
        assertTrue(repository.getSettings().isMonitoringActive(gpay))
        assertFalse(repository.getSettings().isMonitoringActive(phonepe))
    }

    @Test
    fun test12_permissionMissing_blocksAppNotifications_failsClosed() {
        NotificationPermissionHelper.permissionOverrideForTesting = false

        var contentExtracted = false
        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "test_key_perm_missing",
            postTime = System.currentTimeMillis()
        ) {
            contentExtracted = true
            NotificationContent(title = "Paid", text = "Rs 50")
        }

        assertFalse("Notification processing must be blocked when permission is missing", result)
        assertFalse("Content extractor MUST NOT be invoked when permission is missing", contentExtracted)
    }

    @Test
    fun test13_permissionMissing_failsClosedRegardlessOfSettings() {
        NotificationPermissionHelper.permissionOverrideForTesting = false
        repository.setGlobalEnabled(true)
        repository.setNotificationTrackingEnabled(true)
        repository.setAppEnabled(gpay, true)

        var contentExtracted = false
        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "test_perm_fail_closed",
            postTime = System.currentTimeMillis()
        ) {
            contentExtracted = true
            NotificationContent(title = "Title", text = "Text")
        }

        assertFalse(result)
        assertFalse(contentExtracted)
    }

    @Test
    fun test14_permissionGranted_allowsProcessing() {
        NotificationPermissionHelper.permissionOverrideForTesting = true
        repository.setGlobalEnabled(true)
        repository.setNotificationTrackingEnabled(true)
        repository.setAppEnabled(gpay, true)

        var contentExtracted = false
        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "test_perm_granted",
            postTime = System.currentTimeMillis()
        ) {
            contentExtracted = true
            NotificationContent(title = "Title", text = "Text")
        }

        assertTrue(result)
        assertTrue(contentExtracted)
    }

    @Test
    fun test15_permissionRevoked_failsClosed() {
        // Initially granted
        NotificationPermissionHelper.permissionOverrideForTesting = true
        assertTrue(service.processNotification(gpay, "k1", 100L) { NotificationContent("T", "M") })

        // User revokes outside app
        NotificationPermissionHelper.permissionOverrideForTesting = false

        var contentExtracted = false
        val result = service.processNotification(gpay, "k2", 200L) {
            contentExtracted = true
            NotificationContent("T2", "M2")
        }

        assertFalse(result)
        assertFalse(contentExtracted)
    }

    @Test
    fun test16_smsSource_doesNotAffectNotificationSource() {
        repository.setGlobalEnabled(true)
        repository.setNotificationTrackingEnabled(true)

        // Toggle SMS OFF
        repository.setSmsTrackingEnabled(false)
        assertTrue("Notification tracking must remain true when SMS tracking is toggled OFF", repository.isNotificationTrackingEnabled())
        assertTrue("App monitoring must remain active when SMS is toggled OFF", repository.getSettings().isMonitoringActive(gpay))

        // Toggle SMS ON
        repository.setSmsTrackingEnabled(true)
        assertTrue(repository.isNotificationTrackingEnabled())
        assertTrue(repository.getSettings().isMonitoringActive(gpay))
    }

    @Test
    fun test17_notificationSource_doesNotAffectSmsSource() {
        repository.setGlobalEnabled(true)
        repository.setSmsTrackingEnabled(true)

        // Toggle Notification OFF
        repository.setNotificationTrackingEnabled(false)
        assertTrue("SMS tracking must remain true when notification tracking is toggled OFF", repository.isSmsTrackingEnabled())

        // Toggle Notification ON
        repository.setNotificationTrackingEnabled(true)
        assertTrue(repository.isSmsTrackingEnabled())
    }

    @Test
    fun test18_legacyAutoTracking_syncsWithGlobalEnabled() {
        repository.setGlobalEnabled(false)
        assertFalse(repository.getSettings().globalEnabled)

        repository.setGlobalEnabled(true)
        assertTrue(repository.getSettings().globalEnabled)
    }

    @Test
    fun test19_userAddedApps_preservedAcrossHierarchyToggles() {
        val userApp = SupportedApp(
            packageName = "com.custom.fintech",
            displayName = "Custom Fintech",
            description = "User added app",
            category = AppCategory.UPI_PAYMENT,
            defaultEnabled = true
        )
        val added = repository.addUserApp(userApp)
        assertTrue(added)
        assertTrue(repository.getUserAddedApps().any { it.packageName == "com.custom.fintech" })

        // Master OFF
        repository.setGlobalEnabled(false)
        assertTrue(repository.getUserAddedApps().any { it.packageName == "com.custom.fintech" })

        // Notification source OFF
        repository.setNotificationTrackingEnabled(false)
        assertTrue(repository.getUserAddedApps().any { it.packageName == "com.custom.fintech" })

        // Restore Master & Notification
        repository.setGlobalEnabled(true)
        repository.setNotificationTrackingEnabled(true)
        assertTrue(repository.getUserAddedApps().any { it.packageName == "com.custom.fintech" })
        assertTrue(repository.getSettings().isMonitoringActive("com.custom.fintech"))
    }

    @Test
    fun test20_resetToDefaults_restoresAllTrackingSourcesToOn() {
        repository.setGlobalEnabled(false)
        repository.setNotificationTrackingEnabled(false)
        repository.setSmsTrackingEnabled(false)

        repository.resetToDefaults()

        val settings = repository.getSettings()
        assertTrue("Master tracking must default to true after reset", settings.globalEnabled)
        assertTrue("Notification tracking must default to true after reset", settings.isNotificationTrackingEnabled)
        assertTrue("SMS tracking must default to true after reset", repository.isSmsTrackingEnabled())
        assertEquals("Enabled packages must restore to catalog defaults", AppCatalog.defaultEnabledPackages, settings.enabledPackages)
    }

    @Test
    fun test21_privacyGate_zeroContentExtractionWhenNotificationTrackingDisabled() {
        repository.setGlobalEnabled(true)
        repository.setNotificationTrackingEnabled(false)

        var accessed = false
        val allowed = service.processNotification(gpay, "k", 1L) {
            accessed = true
            NotificationContent("Secret Title", "Secret Body")
        }

        assertFalse(allowed)
        assertFalse(accessed)
    }

    @Test
    fun test22_privacyGate_zeroContentExtractionWhenMasterDisabled() {
        repository.setGlobalEnabled(false)
        repository.setNotificationTrackingEnabled(true)

        var accessed = false
        val allowed = service.processNotification(gpay, "k", 1L) {
            accessed = true
            NotificationContent("Secret Title", "Secret Body")
        }

        assertFalse(allowed)
        assertFalse(accessed)
    }

    @Test
    fun test23_privacyGate_zeroContentExtractionWhenPackageDisabled() {
        repository.setGlobalEnabled(true)
        repository.setNotificationTrackingEnabled(true)
        repository.setAppEnabled(phonepe, false)

        var accessed = false
        val allowed = service.processNotification(phonepe, "k", 1L) {
            accessed = true
            NotificationContent("Secret Title", "Secret Body")
        }

        assertFalse(allowed)
        assertFalse(accessed)
    }

    @Test
    fun test24_statePreservationMatrix_CaseA_through_CaseE() {
        // CASE A: Master OFF, SMS ON, Notif ON, GPay ON, PhonePe OFF
        repository.setSmsTrackingEnabled(true)
        repository.setNotificationTrackingEnabled(true)
        repository.setAppEnabled(gpay, true)
        repository.setAppEnabled(phonepe, false)
        repository.setGlobalEnabled(false)

        assertFalse(repository.getSettings().globalEnabled)
        assertTrue(repository.isSmsTrackingEnabled())
        assertTrue(repository.isNotificationTrackingEnabled())
        assertTrue(repository.isAppEnabled(gpay))
        assertFalse(repository.isAppEnabled(phonepe))
        assertFalse(repository.getSettings().isMonitoringActive(gpay))

        // CASE B: Turn Master ON -> previously selected states restored
        repository.setGlobalEnabled(true)
        assertTrue(repository.isSmsTrackingEnabled())
        assertTrue(repository.isNotificationTrackingEnabled())
        assertTrue(repository.getSettings().isMonitoringActive(gpay))
        assertFalse(repository.getSettings().isMonitoringActive(phonepe))

        // CASE C: Master ON, SMS OFF, Notif ON
        repository.setSmsTrackingEnabled(false)
        assertFalse(repository.isSmsTrackingEnabled())
        assertTrue(repository.isNotificationTrackingEnabled())
        assertTrue(repository.getSettings().isMonitoringActive(gpay))

        // CASE D: Master ON, SMS ON, Notif OFF
        repository.setSmsTrackingEnabled(true)
        repository.setNotificationTrackingEnabled(false)
        assertTrue(repository.isSmsTrackingEnabled())
        assertFalse(repository.isNotificationTrackingEnabled())
        assertFalse(repository.getSettings().isMonitoringActive(gpay))
        assertTrue("GPay preference preserved even when Notif source is OFF", repository.isAppEnabled(gpay))

        // CASE E: Master OFF, Notif OFF, GPay ON, PhonePe OFF -> Turn Master ON
        repository.setGlobalEnabled(false)
        repository.setGlobalEnabled(true)
        assertFalse(repository.isNotificationTrackingEnabled())
        assertTrue(repository.isAppEnabled(gpay))
        assertFalse(repository.isAppEnabled(phonepe))
    }
}
