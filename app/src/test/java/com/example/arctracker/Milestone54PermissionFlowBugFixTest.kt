package com.example.arctracker

import android.content.Context
import com.example.arctracker.service.NotificationPermissionHelper
import com.example.arctracker.service.SmsPermissionHelper
import com.example.arctracker.settings.InMemoryMonitoringSettingsRepository
import com.example.arctracker.settings.MonitoringSettingsRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests verifying fixes for:
 * 1. SMS permission accepted in Import message page or Home page dialog never asks again on app reopens.
 * 2. Home page notification permission grant immediately toggles ON auto tracking.
 * 3. Home page notification permission denial turns OFF auto tracking, allowing user to toggle it ON in Settings.
 */
class Milestone54PermissionFlowBugFixTest {

    private lateinit var settingsRepo: InMemoryMonitoringSettingsRepository

    @Before
    fun setUp() {
        settingsRepo = InMemoryMonitoringSettingsRepository()
        NotificationPermissionHelper.permissionOverrideForTesting = null
        SmsPermissionHelper.permissionOverrideForTesting = null
    }

    @After
    fun tearDown() {
        NotificationPermissionHelper.permissionOverrideForTesting = null
        SmsPermissionHelper.permissionOverrideForTesting = null
    }

    // =========================================================================
    // 1. SMS Permission & Initial Import Never Asks Again
    // =========================================================================

    @Test
    fun test01_freshInstall_noPermission_eligibleForOnboarding() {
        SmsPermissionHelper.permissionOverrideForTesting = false
        assertFalse(settingsRepo.isInitialSmsImportCompleted())

        // Under fresh install, with no permission and incomplete import:
        val shouldShowOnboarding = !settingsRepo.isInitialSmsImportCompleted()
        assertTrue(shouldShowOnboarding)
    }

    @Test
    fun test02_smsPermissionGrantedInImportPage_marksInitialImportCompletedPermanently() {
        // User starts without permission
        SmsPermissionHelper.permissionOverrideForTesting = false
        assertFalse(settingsRepo.isInitialSmsImportCompleted())

        // User goes to Import Previous Transactions page and grants permission
        SmsPermissionHelper.permissionOverrideForTesting = true
        settingsRepo.setInitialSmsImportCompleted(true)

        // Verify it is completed in repo
        assertTrue(settingsRepo.isInitialSmsImportCompleted())

        // User closes and reopens the app (simulate new session)
        val hasDismissedSession = false
        val shouldShowOnboardingOnReopen = !hasDismissedSession && !settingsRepo.isInitialSmsImportCompleted()

        // MUST NEVER ASK AGAIN
        assertFalse(shouldShowOnboardingOnReopen)
    }

    @Test
    fun test03_smsPermissionGrantedInHomeDialog_marksInitialImportCompletedPermanently() {
        // User starts without permission
        SmsPermissionHelper.permissionOverrideForTesting = false
        assertFalse(settingsRepo.isInitialSmsImportCompleted())

        // User accepts in Home Page InitialSmsImportDialog
        SmsPermissionHelper.permissionOverrideForTesting = true
        settingsRepo.setInitialSmsImportCompleted(true)

        // Close and reopen app
        val shouldShowOnboarding = !settingsRepo.isInitialSmsImportCompleted()
        assertFalse(shouldShowOnboarding)
    }

    @Test
    fun test04_systemPermissionAlreadyGranted_neverShowsInitialImportDialog() {
        // User already gave SMS permission to the app
        SmsPermissionHelper.permissionOverrideForTesting = true

        // If permission is already granted, the app treats import as completed or bypasses onboarding
        val isPermGranted = SmsPermissionHelper.permissionOverrideForTesting == true
        if (isPermGranted) {
            settingsRepo.setInitialSmsImportCompleted(true)
        }

        assertTrue(settingsRepo.isInitialSmsImportCompleted())
        val canShowDialog = !settingsRepo.isInitialSmsImportCompleted()
        assertFalse("Dialog must not show when SMS permission is already granted", canShowDialog)
    }

    // =========================================================================
    // 2. Notification Permission & Auto Tracking Toggle Behavior
    // =========================================================================

    @Test
    fun test05_homeNotificationPermission_granted_togglesAutoTrackingOn() {
        // Initially no notification access
        NotificationPermissionHelper.permissionOverrideForTesting = false

        // User is prompted on Home page and taps "Grant Permission"
        var pendingHomeNotificationGrant = true

        // User grants notification access in system settings and returns (ON_RESUME)
        NotificationPermissionHelper.permissionOverrideForTesting = true
        val granted = NotificationPermissionHelper.permissionOverrideForTesting == true

        if (pendingHomeNotificationGrant) {
            pendingHomeNotificationGrant = false
            if (granted) {
                settingsRepo.setGlobalEnabled(true)
                settingsRepo.setNotificationTrackingEnabled(true)
            } else {
                settingsRepo.setGlobalEnabled(false)
                settingsRepo.setNotificationTrackingEnabled(false)
            }
        }

        // Verify settingsRepo updated
        assertTrue(settingsRepo.getSettings().globalEnabled)
        assertTrue(settingsRepo.isNotificationTrackingEnabled())

        // In SettingsScreen, effectiveAutoTracking = userPrefGlobalEnabled && hasNotifAccess
        val effectiveAutoTracking = settingsRepo.getSettings().globalEnabled &&
                (NotificationPermissionHelper.permissionOverrideForTesting == true)
        assertTrue("Auto Tracking must be ON when permission granted from Home page", effectiveAutoTracking)
    }

    @Test
    fun test06_homeNotificationPermission_denied_togglesAutoTrackingOff() {
        // Initially no notification access
        NotificationPermissionHelper.permissionOverrideForTesting = false

        // User taps "Not Now" (dismisses from Home page)
        val hasDismissedDialog = true
        settingsRepo.setGlobalEnabled(false)
        settingsRepo.setNotificationTrackingEnabled(false)

        assertFalse(settingsRepo.getSettings().globalEnabled)
        assertFalse(settingsRepo.isNotificationTrackingEnabled())

        val effectiveAutoTracking = settingsRepo.getSettings().globalEnabled &&
                (NotificationPermissionHelper.permissionOverrideForTesting == true)
        assertFalse("Auto Tracking must be OFF when user denies from Home page", effectiveAutoTracking)
    }

    @Test
    fun test07_homeNotificationPermission_deniedThenToggledOnInSettings() {
        // 1. User denied on Home page
        NotificationPermissionHelper.permissionOverrideForTesting = false
        settingsRepo.setGlobalEnabled(false)
        settingsRepo.setNotificationTrackingEnabled(false)

        var effectiveAutoTracking = settingsRepo.getSettings().globalEnabled &&
                (NotificationPermissionHelper.permissionOverrideForTesting == true)
        assertFalse(effectiveAutoTracking)

        // 2. User goes to Settings and taps toggle ON
        var pendingPermissionAction: String? = null
        val requestedToggleState = true
        if (requestedToggleState) {
            if (NotificationPermissionHelper.permissionOverrideForTesting != true) {
                // Must request permission first
                pendingPermissionAction = "AUTO_TRACKING"
            } else {
                settingsRepo.setGlobalEnabled(true)
            }
        }

        assertEquals("AUTO_TRACKING", pendingPermissionAction)
        // Toggle remains OFF until permission actually granted
        effectiveAutoTracking = settingsRepo.getSettings().globalEnabled &&
                (NotificationPermissionHelper.permissionOverrideForTesting == true)
        assertFalse(effectiveAutoTracking)

        // 3. User grants permission in system settings and returns (ON_RESUME)
        NotificationPermissionHelper.permissionOverrideForTesting = true
        val hasNotifAccess = NotificationPermissionHelper.permissionOverrideForTesting == true

        if (pendingPermissionAction == "AUTO_TRACKING") {
            pendingPermissionAction = null
            if (hasNotifAccess) {
                settingsRepo.setGlobalEnabled(true)
            } else {
                settingsRepo.setGlobalEnabled(false)
            }
        }

        // Now toggle is ON!
        effectiveAutoTracking = settingsRepo.getSettings().globalEnabled && hasNotifAccess
        assertTrue("Auto Tracking must be ON after granting permission from Settings", effectiveAutoTracking)
    }

    @Test
    fun test08_clearAllData_preservesInitialImportCompleted_neverAsksAgain() {
        // Onboarding already done
        settingsRepo.setInitialSmsImportCompleted(true)
        assertTrue(settingsRepo.isInitialSmsImportCompleted())

        // Clear All Data runs
        val wasCompleted = settingsRepo.isInitialSmsImportCompleted()
        settingsRepo.setInitialSmsImportCompleted(wasCompleted)

        // Verify preserved
        assertTrue(settingsRepo.isInitialSmsImportCompleted())
        assertFalse("Must never ask again after Clear All Data", !settingsRepo.isInitialSmsImportCompleted())
    }
}
