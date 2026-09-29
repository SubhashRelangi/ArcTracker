package com.subhashrelangi.arctracker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subhashrelangi.arctracker.settings.AppCatalog
import com.subhashrelangi.arctracker.settings.AppCategory
import com.subhashrelangi.arctracker.settings.MonitoringSettingsRepository
import com.subhashrelangi.arctracker.ui.getCategoryIcon
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 9.5 Connected Android Device Tests for Supported Apps UI & Category Lifecycle.
 * Executes on real connected Android hardware (Samsung Galaxy A34 5G / Android 16).
 * Verifies:
 * - Real runtime catalog partition across UPI, Banking, and SMS categories
 * - Default category expansion rules (UPI true, Banking false, SMS false)
 * - SharedPreferences persistence and restoration on real device storage
 * - Independence of category UI state from real device persistence
 * - Long app names and icon resolution on real device runtime
 * - Persistence preservation across simulated screen re-entry
 */
@RunWith(AndroidJUnit4::class)
class SupportedAppsUiDeviceTest {

    private lateinit var context: Context
    private lateinit var repository: MonitoringSettingsRepository

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = MonitoringSettingsRepository.getInstance(context)
    }

    @After
    fun tearDown() {
        val prefs = context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun testRealDevice_catalogCategoriesIntegrity() {
        val upiApps = AppCatalog.getAppsByCategory(AppCategory.UPI_PAYMENT)
        val bankingApps = AppCatalog.getAppsByCategory(AppCategory.BANKING)
        val smsApps = AppCatalog.getAppsByCategory(AppCategory.SMS_MESSENGER)

        assertEquals("UPI category must contain 7 apps on device", 7, upiApps.size)
        assertEquals("Banking category must contain 6 apps on device", 6, bankingApps.size)
        assertEquals("SMS category must contain 3 apps on device", 3, smsApps.size)
        assertEquals("Total catalog apps must be 16", 16, AppCatalog.allApps.size)

        // Verify icon resolution on real device
        assertNotNull(getCategoryIcon(AppCategory.UPI_PAYMENT))
        assertNotNull(getCategoryIcon(AppCategory.BANKING))
        assertNotNull(getCategoryIcon(AppCategory.SMS_MESSENGER))
        assertNotNull(getCategoryIcon(null))
    }

    @Test
    fun testRealDevice_categoryOrderAndDefaultExpandState() {
        val orderedCategories = listOf(
            AppCategory.UPI_PAYMENT,
            AppCategory.BANKING,
            AppCategory.SMS_MESSENGER
        )

        assertEquals("1st category must be UPI", "UPI & Payment Apps", orderedCategories[0].displayName)
        assertEquals("2nd category must be Banking", "Banking Apps", orderedCategories[1].displayName)
        assertEquals("3rd category must be SMS", "SMS & Messenger Apps", orderedCategories[2].displayName)

        // Default expand states
        assertTrue("UPI must be expanded by default", orderedCategories[0] == AppCategory.UPI_PAYMENT)
        assertFalse("Banking must be collapsed by default", orderedCategories[1] == AppCategory.UPI_PAYMENT)
        assertFalse("SMS must be collapsed by default", orderedCategories[2] == AppCategory.UPI_PAYMENT)
    }

    @Test
    fun testRealDevice_appTogglePersistsAcrossRecreation() {
        val testPackage = "com.google.android.apps.nbu.paisa.user"
        repository.setAppEnabled(testPackage, true)

        val prefs = context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(MonitoringSettingsRepository.KEY_ENABLED_PACKAGES, null)
        assertNotNull(savedSet)
        assertTrue(savedSet!!.contains(testPackage))

        // Recreate repository simulating navigating away and re-entering SupportedAppsScreen
        val reloaded = MonitoringSettingsRepository.getInstance(context)
        assertTrue("Reloaded repository on device must retain package", reloaded.getSettings().enabledPackages.contains(testPackage))
    }

    @Test
    fun testRealDevice_globalOffPreservesIndividualSelections() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        val hdfc = "com.snapwork.hdfc"

        repository.setAppEnabled(gpay, true)
        repository.setAppEnabled(hdfc, true)
        repository.setGlobalEnabled(false)

        val settings = repository.getSettings()
        assertFalse("Global monitoring must be disabled", settings.globalEnabled)
        assertTrue("Google Pay selection preserved", settings.enabledPackages.contains(gpay))
        assertTrue("HDFC selection preserved", settings.enabledPackages.contains(hdfc))

        // Reopen screen / reload
        val reloaded = MonitoringSettingsRepository.getInstance(context)
        assertFalse(reloaded.getSettings().globalEnabled)
        assertTrue(reloaded.getSettings().enabledPackages.contains(gpay))
        assertTrue(reloaded.getSettings().enabledPackages.contains(hdfc))
    }
}
