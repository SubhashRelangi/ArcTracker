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
 * Step 9.6 Connected Android Device Tests for Interactive Add Apps + Category Assignment.
 * Executes on physical Samsung Galaxy A34 5G (SM-A346E - 16).
 * Verifies:
 * - Real PackageManager discovery through [DefaultInstalledAppsProvider]
 * - Real SharedPreferences persistence of user-added application definitions
 * - Duplicate package prevention on real device
 * - Category assignment and persistence across repository reload
 * - Independence of globalEnabled when user adds an application
 */
@RunWith(AndroidJUnit4::class)
class AddAppsDeviceTest {

    private lateinit var context: Context
    private lateinit var repository: MonitoringSettingsRepository
    private lateinit var provider: DefaultInstalledAppsProvider

    private val customPackage = "com.example.devicecustomupi"

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
    fun testRealDevice_installedAppDiscovery_returnsRealApps() {
        val installedApps = provider.getInstalledApps()
        assertNotNull("Installed apps list must not be null on real device", installedApps)
        assertTrue("Installed apps list on real Samsung device must not be empty", installedApps.isNotEmpty())

        // Verify labels and packages are valid non-blank strings
        for (app in installedApps) {
            assertTrue("Package name must be non-blank", app.packageName.isNotBlank())
            assertTrue("Display name must be non-blank", app.displayName.isNotBlank())
        }
    }

    @Test
    fun testRealDevice_addUserApp_persistsAndSurvivesReload() {
        val customApp = SupportedApp(
            packageName = customPackage,
            displayName = "Custom Device UPI",
            description = "Device test app",
            category = AppCategory.UPI_PAYMENT
        )

        val added = repository.addUserApp(customApp)
        assertTrue("addUserApp must return true", added)

        // Verify in SharedPreferences
        val prefs = context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE)
        val rawSet = prefs.getStringSet(MonitoringSettingsRepository.KEY_USER_ADDED_APPS, null)
        assertNotNull("User added apps set must exist in SharedPreferences", rawSet)
        assertTrue("Set must contain custom package", rawSet!!.any { it.contains(customPackage) })

        // Reload repository to simulate app restart
        val reloaded = MonitoringSettingsRepository.getInstance(context)
        val userApps = reloaded.getUserAddedApps()
        val found = userApps.firstOrNull { it.packageName == customPackage }
        assertNotNull("User app must survive process reload", found)
        assertEquals("Custom Device UPI", found?.displayName)
        assertEquals(AppCategory.UPI_PAYMENT, found?.category)
        assertTrue("Newly added app must be enabled by default", reloaded.isAppEnabled(customPackage))
    }

    @Test
    fun testRealDevice_duplicatePackage_isRejected() {
        // Built-in catalog package
        val gpay = "com.google.android.apps.nbu.paisa.user"
        val duplicateCatalog = SupportedApp(gpay, "Fake GPay", "Desc", AppCategory.UPI_PAYMENT)
        assertFalse("Duplicate of built-in catalog package must be rejected", repository.addUserApp(duplicateCatalog))

        // First user add succeeds
        val customApp = SupportedApp(customPackage, "First Name", "Desc", AppCategory.BANKING)
        assertTrue(repository.addUserApp(customApp))

        // Second user add of same package fails
        val secondCustom = SupportedApp(customPackage, "Second Name", "Desc", AppCategory.SMS_MESSENGER)
        assertFalse("Duplicate user added package must be rejected", repository.addUserApp(secondCustom))
    }

    @Test
    fun testRealDevice_globalEnabled_remainsUnchangedWhenAppAdded() {
        repository.setGlobalEnabled(false)
        assertFalse("Global monitoring is false", repository.getSettings().globalEnabled)

        val customApp = SupportedApp(customPackage, "Custom App", "Desc", AppCategory.SMS_MESSENGER)
        repository.addUserApp(customApp)

        val reloaded = MonitoringSettingsRepository.getInstance(context)
        assertFalse("globalEnabled must REMAIN false after adding an app", reloaded.getSettings().globalEnabled)
        assertTrue("App itself is in enabledPackages", reloaded.isAppEnabled(customPackage))
        assertFalse("Monitoring must NOT be active while globalEnabled is false", reloaded.isMonitoringActive(customPackage))
    }
}
