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
 * Step 9.8 Connected Android Device Tests for Category-Based Add App + Real Icons + Edit User App.
 * Executes on physical Samsung Galaxy A34 5G (SM-A346E - 16).
 * Verifies:
 * - Real PackageManager resolution of actual application icons
 * - User-added app addition with category persistence
 * - Editing category of user-added app and persistence across process reload
 * - Built-in catalog app immutability (cannot be edited)
 * - Preservation of globalEnabled and enabledPackages across add and edit
 */
@RunWith(AndroidJUnit4::class)
class CategoryAddAndEditDeviceTest {

    private lateinit var context: Context
    private lateinit var repository: MonitoringSettingsRepository
    private lateinit var provider: DefaultInstalledAppsProvider

    private val customPackage = "com.example.device.editableapp"

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
    fun testRealDevice_installedApps_resolveRealIcons() {
        val installed = provider.getInstalledApps()
        assertTrue("Installed apps must not be empty on real device", installed.isNotEmpty())

        val withIcons = installed.filter { it.icon != null }
        assertTrue("Installed apps on real device must have loaded application icons", withIcons.isNotEmpty())
    }

    @Test
    fun testRealDevice_userApp_canBeAddedWithCategory() {
        val app = SupportedApp(
            packageName = customPackage,
            displayName = "Device Editable App",
            description = "Test description",
            category = AppCategory.UPI_PAYMENT
        )
        assertTrue(repository.addUserApp(app))

        val loaded = repository.getUserAddedApps().firstOrNull { it.packageName == customPackage }
        assertNotNull(loaded)
        assertEquals(AppCategory.UPI_PAYMENT, loaded?.category)
        assertTrue(repository.isAppEnabled(customPackage))
    }

    @Test
    fun testRealDevice_userApp_canBeEditedToNewCategory() {
        val initialApp = SupportedApp(
            packageName = customPackage,
            displayName = "Device Editable App",
            description = "Test description",
            category = AppCategory.UPI_PAYMENT
        )
        assertTrue(repository.addUserApp(initialApp))

        // Change category to BANKING
        val updatedApp = initialApp.copy(category = AppCategory.BANKING)
        assertTrue(repository.updateUserApp(updatedApp))

        // Reload from real SharedPreferences
        val reloaded = MonitoringSettingsRepository.getInstance(context)
        val loaded = reloaded.getUserAddedApps().firstOrNull { it.packageName == customPackage }
        assertNotNull(loaded)
        assertEquals("Persisted category must be BANKING after edit", AppCategory.BANKING, loaded?.category)
        assertTrue("Enabled state must be preserved across category edit", reloaded.isAppEnabled(customPackage))
    }

    @Test
    fun testRealDevice_builtInApp_cannotBeEdited() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        val editAttempt = SupportedApp(gpay, "Fake GPay", "Desc", AppCategory.BANKING)

        assertFalse("updateUserApp must reject built-in catalog app", repository.updateUserApp(editAttempt))

        val builtIn = repository.getAllConfiguredApps().first { it.packageName == gpay }
        assertEquals("Built-in app category must remain UPI_PAYMENT", AppCategory.UPI_PAYMENT, builtIn.category)
    }

    @Test
    fun testRealDevice_globalEnabled_remainsUnchangedAcrossAddAndEdit() {
        repository.setGlobalEnabled(false)
        assertFalse(repository.getSettings().globalEnabled)

        // Add
        val app = SupportedApp(customPackage, "App", "Desc", AppCategory.UPI_PAYMENT)
        repository.addUserApp(app)
        assertFalse("globalEnabled must remain false after Add", repository.getSettings().globalEnabled)

        // Edit
        val updated = app.copy(category = AppCategory.SMS_MESSENGER)
        repository.updateUserApp(updated)
        assertFalse("globalEnabled must remain false after Edit", repository.getSettings().globalEnabled)
    }
}
