package com.subhashrelangi.arctracker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subhashrelangi.arctracker.service.NotificationContent
import com.subhashrelangi.arctracker.service.NotificationPermissionHelper
import com.subhashrelangi.arctracker.service.NotificationReaderService
import com.subhashrelangi.arctracker.settings.AppCatalog
import com.subhashrelangi.arctracker.settings.MonitoringSettingsRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 9.11 Connected Android Device Test executing on Samsung Galaxy A34 5G (SM-A346E - 16).
 * Verifies real SharedPreferences persistence, device-level privacy gate enforcement,
 * and 3-level tracking control hierarchy on physical hardware.
 */
@RunWith(AndroidJUnit4::class)
class TrackingControlsDeviceTest {

    private lateinit var context: Context
    private lateinit var repository: MonitoringSettingsRepository
    private lateinit var service: NotificationReaderService
    private val gpay = "com.google.android.apps.nbu.paisa.user"
    private val phonepe = "com.phonepe.app"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        NotificationPermissionHelper.permissionOverrideForTesting = true
        NotificationReaderService.clearActiveNotifications()
        NotificationReaderService.lastCapturedNotification = null
        NotificationReaderService.notificationListener = null

        val prefs = context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        repository = MonitoringSettingsRepository.getInstance(context)
        NotificationReaderService.settingsRepositoryOverride = repository
        service = NotificationReaderService()
    }

    @After
    fun tearDown() {
        NotificationPermissionHelper.permissionOverrideForTesting = null
        NotificationReaderService.settingsRepositoryOverride = null
        NotificationReaderService.clearActiveNotifications()
        NotificationReaderService.lastCapturedNotification = null
        NotificationReaderService.notificationListener = null

        val prefs = context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun testRealDevice_defaultHierarchyStates() {
        val settings = repository.getSettings()
        assertTrue("Automatic Tracking must default to true on device", settings.globalEnabled)
        assertTrue("App Notifications must default to true on device", settings.isNotificationTrackingEnabled)
        assertTrue("SMS Messages must default to true on device", repository.isSmsTrackingEnabled())
        assertTrue("GPay must be in default enabled packages on device", settings.isAppEnabled(gpay))
    }

    @Test
    fun testRealDevice_masterOff_preservesStoredSources_andBlocksNotifications() {
        repository.setSmsTrackingEnabled(true)
        repository.setNotificationTrackingEnabled(true)
        repository.setAppEnabled(gpay, true)

        // Turn Master OFF
        repository.setGlobalEnabled(false)

        val prefs = context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE)
        assertFalse(prefs.getBoolean(MonitoringSettingsRepository.KEY_GLOBAL_ENABLED, true))
        assertFalse(prefs.getBoolean(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING, true))
        assertTrue(prefs.getBoolean(MonitoringSettingsRepository.KEY_SMS_TRACKING_ENABLED, true))
        assertTrue(prefs.getBoolean(MonitoringSettingsRepository.KEY_NOTIFICATION_TRACKING_ENABLED, true))

        var contentExtracted = false
        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "device_key_master_off",
            postTime = System.currentTimeMillis()
        ) {
            contentExtracted = true
            NotificationContent("Secret Title", "Secret Body")
        }

        assertFalse("Notification ingress must be blocked when master is OFF", result)
        assertFalse("Content extractor MUST NOT be invoked when master is OFF", contentExtracted)
    }

    @Test
    fun testRealDevice_masterOn_restoresOperations() {
        repository.setGlobalEnabled(false)
        assertFalse(repository.getSettings().globalEnabled)

        // Turn Master ON
        repository.setGlobalEnabled(true)
        assertTrue(repository.getSettings().globalEnabled)
        assertTrue(repository.isNotificationTrackingEnabled())
        assertTrue(repository.isSmsTrackingEnabled())
        assertTrue(repository.getSettings().isMonitoringActive(gpay))

        var contentExtracted = false
        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "device_key_master_on",
            postTime = System.currentTimeMillis()
        ) {
            contentExtracted = true
            NotificationContent("Test Title", "Paid Rs 100")
        }

        assertTrue(result)
        assertTrue(contentExtracted)
    }

    @Test
    fun testRealDevice_smsOnly_notificationSourceOff_blocksNotificationIngress() {
        repository.setGlobalEnabled(true)
        repository.setSmsTrackingEnabled(true)
        repository.setNotificationTrackingEnabled(false)

        assertTrue(repository.isSmsTrackingEnabled())
        assertFalse(repository.isNotificationTrackingEnabled())

        var contentExtracted = false
        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "device_sms_only",
            postTime = System.currentTimeMillis()
        ) {
            contentExtracted = true
            NotificationContent("Payment", "Amount 500")
        }

        assertFalse("Notification ingress must fail when notification tracking is disabled", result)
        assertFalse("Content must NOT be extracted when notification tracking is disabled", contentExtracted)
        assertTrue("Individual app preference remains intact", repository.isAppEnabled(gpay))
    }

    @Test
    fun testRealDevice_notificationOnly_smsSourceOff_allowsNotifications() {
        repository.setGlobalEnabled(true)
        repository.setSmsTrackingEnabled(false)
        repository.setNotificationTrackingEnabled(true)

        assertFalse(repository.isSmsTrackingEnabled())
        assertTrue(repository.isNotificationTrackingEnabled())

        var contentExtracted = false
        val result = service.processNotification(
            packageName = gpay,
            notificationKey = "device_notif_only",
            postTime = System.currentTimeMillis()
        ) {
            contentExtracted = true
            NotificationContent("Payment", "Received Rs 250")
        }

        assertTrue(result)
        assertTrue(contentExtracted)
    }

    @Test
    fun testRealDevice_preferencesPersistAcrossReload() {
        repository.setGlobalEnabled(true)
        repository.setSmsTrackingEnabled(false)
        repository.setNotificationTrackingEnabled(true)
        repository.setAppEnabled(phonepe, false)

        // Simulate reload from disk
        val reloaded = MonitoringSettingsRepository.getInstance(context)
        assertTrue(reloaded.getSettings().globalEnabled)
        assertFalse(reloaded.isSmsTrackingEnabled())
        assertTrue(reloaded.isNotificationTrackingEnabled())
        assertFalse(reloaded.isAppEnabled(phonepe))
        assertTrue(reloaded.isAppEnabled(gpay))
    }

    @Test
    fun testRealDevice_resetToDefaults_restoresAllHierarchyLevels() {
        repository.setGlobalEnabled(false)
        repository.setSmsTrackingEnabled(false)
        repository.setNotificationTrackingEnabled(false)

        repository.resetToDefaults()

        val settings = repository.getSettings()
        assertTrue(settings.globalEnabled)
        assertTrue(settings.isNotificationTrackingEnabled)
        assertTrue(repository.isSmsTrackingEnabled())
        assertEquals(AppCatalog.defaultEnabledPackages, settings.enabledPackages)
    }

    @Test
    fun testRealDevice_statePreservationMatrix_CaseA_through_CaseE() {
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

        // CASE B: Master ON -> restores previously selected states
        repository.setGlobalEnabled(true)
        assertTrue(repository.isSmsTrackingEnabled())
        assertTrue(repository.isNotificationTrackingEnabled())
        assertTrue(repository.getSettings().isMonitoringActive(gpay))
        assertFalse(repository.getSettings().isMonitoringActive(phonepe))

        // CASE C: Master ON, SMS OFF, Notif ON
        repository.setSmsTrackingEnabled(false)
        assertFalse(repository.isSmsTrackingEnabled())
        assertTrue(repository.isNotificationTrackingEnabled())

        // CASE D: Master ON, SMS ON, Notif OFF -> Monitored apps preserved
        repository.setSmsTrackingEnabled(true)
        repository.setNotificationTrackingEnabled(false)
        assertTrue(repository.isSmsTrackingEnabled())
        assertFalse(repository.isNotificationTrackingEnabled())
        assertTrue(repository.isAppEnabled(gpay))

        // CASE E: Master OFF, Notif OFF, GPay ON, PhonePe OFF -> Master ON
        repository.setGlobalEnabled(false)
        repository.setGlobalEnabled(true)
        assertFalse(repository.isNotificationTrackingEnabled())
        assertTrue(repository.isAppEnabled(gpay))
        assertFalse(repository.isAppEnabled(phonepe))
    }
}
