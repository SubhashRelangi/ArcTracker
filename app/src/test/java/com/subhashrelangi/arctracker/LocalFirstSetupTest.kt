package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.service.NotificationPermissionHelper
import com.subhashrelangi.arctracker.service.SmsPermissionHelper
import com.subhashrelangi.arctracker.settings.MonitoringSettingsRepository
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying Local-first Setup state management, recovery flags,
 * and permission helper interactions.
 */
class LocalFirstSetupTest {

    private lateinit var repository: MonitoringSettingsRepository

    @Before
    fun setUp() {
        repository = MonitoringSettingsRepository.createInMemory()
        NotificationPermissionHelper.permissionOverrideForTesting = null
        SmsPermissionHelper.permissionOverrideForTesting = null
    }

    @After
    fun tearDown() {
        NotificationPermissionHelper.permissionOverrideForTesting = null
        SmsPermissionHelper.permissionOverrideForTesting = null
    }

    @Test
    fun testSetupInProgressDefaultsToFalse() {
        assertFalse(repository.isSetupInProgress())
    }

    @Test
    fun testSetupInProgressStateCanBeToggled() {
        repository.setSetupInProgress(true)
        assertTrue(repository.isSetupInProgress())

        repository.setSetupInProgress(false)
        assertFalse(repository.isSetupInProgress())
    }

    @Test
    fun testAutoSmsScanArchiveDefaultsToTrue() {
        assertTrue(repository.isAutoSmsScanArchiveEnabled())
    }

    @Test
    fun testAutoSmsScanArchiveCanBeDisabled() {
        repository.setAutoSmsScanArchiveEnabled(false)
        assertFalse(repository.isAutoSmsScanArchiveEnabled())

        repository.setAutoSmsScanArchiveEnabled(true)
        assertTrue(repository.isAutoSmsScanArchiveEnabled())
    }

    @Test
    fun testPermissionStateOverrides() {
        NotificationPermissionHelper.permissionOverrideForTesting = true
        assertTrue(NotificationPermissionHelper.permissionOverrideForTesting == true)

        NotificationPermissionHelper.permissionOverrideForTesting = false
        assertFalse(NotificationPermissionHelper.permissionOverrideForTesting == true)

        SmsPermissionHelper.permissionOverrideForTesting = true
        assertTrue(SmsPermissionHelper.permissionOverrideForTesting == true)

        SmsPermissionHelper.permissionOverrideForTesting = false
        assertFalse(SmsPermissionHelper.permissionOverrideForTesting == true)
    }

    @Test
    fun testInitialOnboardingCompletionLifecycle() {
        assertFalse(repository.isInitialOnboardingCompleted())

        // User enters setup
        repository.setSetupInProgress(true)
        assertTrue(repository.isSetupInProgress())
        assertFalse(repository.isInitialOnboardingCompleted())

        // Setup finishes
        repository.setSetupInProgress(false)
        repository.setInitialOnboardingCompleted(true)

        assertFalse(repository.isSetupInProgress())
        assertTrue(repository.isInitialOnboardingCompleted())
    }
}
