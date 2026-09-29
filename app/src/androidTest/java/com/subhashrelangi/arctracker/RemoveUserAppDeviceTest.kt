package com.subhashrelangi.arctracker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subhashrelangi.arctracker.settings.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 9.9 Connected Android Device Tests: Remove User-Added Supported App.
 * Executes on physical Samsung Galaxy A34 5G (SM-A346E - 16).
 *
 * Verifies:
 * - User-added application addition, presence, and removal on physical device
 * - Built-in applications cannot be removed on physical device
 * - Removing deletes configuration and cleans up enabledPackages
 * - Re-adding removed app restores it and enables it by default
 * - Persistence survives real SharedPreferences reload
 */
@RunWith(AndroidJUnit4::class)
class RemoveUserAppDeviceTest {

    private lateinit var context: Context
    private lateinit var repository: MonitoringSettingsRepository
    private lateinit var provider: DefaultInstalledAppsProvider

    private val customPackage = "com.example.device.removableapp"
    private val gpay = "com.google.android.apps.nbu.paisa.user"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = MonitoringSettingsRepository.getInstance(context)
        provider = DefaultInstalledAppsProvider(context)
    }

    @After
    fun tearDown() {
        val prefs = context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun testRealDevice_userApp_canBeRemovedAndCleansUpState() {
        val app = SupportedApp(
            packageName = customPackage,
            displayName = "Device Removable App",
            description = "Removable app test",
            category = AppCategory.UPI_PAYMENT
        )
        // 1. Add app
        assertTrue(repository.addUserApp(app))
        assertTrue(repository.getUserAddedApps().any { it.packageName == customPackage })
        assertTrue(repository.isAppEnabled(customPackage))

        // 2. Remove app
        assertTrue("removeUserApp must return true for user-added app", repository.removeUserApp(customPackage))

        // 3. Verify removal
        assertFalse("User app definition must be gone", repository.getUserAddedApps().any { it.packageName == customPackage })
        assertFalse("Package must not be enabled", repository.isAppEnabled(customPackage))
        assertFalse("Package must not be in enabledPackages", repository.getSettings().enabledPackages.contains(customPackage))

        // 4. Verify persistence across fresh instance
        val reloaded = MonitoringSettingsRepository.getInstance(context)
        assertFalse(reloaded.getUserAddedApps().any { it.packageName == customPackage })
        assertFalse(reloaded.isAppEnabled(customPackage))
    }

    @Test
    fun testRealDevice_builtInApp_cannotBeRemoved() {
        assertFalse("Built-in Google Pay must NOT be removable", repository.removeUserApp(gpay))
        assertTrue("Google Pay must remain configured in catalog", AppCatalog.containsPackage(gpay))
        assertTrue("Google Pay must remain enabled", repository.isAppEnabled(gpay))
    }

    @Test
    fun testRealDevice_removeAndReAdd_startsEnabledByDefault() {
        val app = SupportedApp(
            packageName = customPackage,
            displayName = "Device Removable App",
            description = "Removable app test",
            category = AppCategory.UPI_PAYMENT
        )
        // Add
        assertTrue(repository.addUserApp(app))
        // Explicitly disable
        repository.setAppEnabled(customPackage, false)
        assertFalse(repository.isAppEnabled(customPackage))

        // Remove
        assertTrue(repository.removeUserApp(customPackage))

        // Re-add to different category (BANKING)
        val reAddedApp = app.copy(category = AppCategory.BANKING)
        assertTrue(repository.addUserApp(reAddedApp))

        // Must now be BANKING and enabled by default
        val configured = repository.getAllConfiguredApps().firstOrNull { it.packageName == customPackage }
        assertNotNull(configured)
        assertEquals(AppCategory.BANKING, configured?.category)
        assertTrue("Re-added app must start enabled by default", repository.isAppEnabled(customPackage))
    }

    @Test
    fun testRealDevice_globalEnabled_remainsUnchangedOnRemove() {
        repository.setGlobalEnabled(false)
        assertFalse(repository.getSettings().globalEnabled)

        val app = SupportedApp(
            packageName = customPackage,
            displayName = "Device Removable App",
            description = "Removable app test",
            category = AppCategory.UPI_PAYMENT
        )
        repository.addUserApp(app)
        assertFalse("globalEnabled must remain false after add", repository.getSettings().globalEnabled)

        repository.removeUserApp(customPackage)
        assertFalse("globalEnabled must remain false after remove", repository.getSettings().globalEnabled)
    }
}
