package com.subhashrelangi.arctracker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subhashrelangi.arctracker.settings.AppCatalog
import com.subhashrelangi.arctracker.settings.MonitoringSettingsRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test executing on a real device/emulator to verify Step 9.2:
 * - Real SharedPreferences persistence of globalEnabled on Android device
 * - Synchronization of KEY_GLOBAL_ENABLED and LEGACY_KEY_AUTO_TRACKING
 * - Independence of globalEnabled from enabledPackages
 * - Screen reopen / process reload simulation with persistent device storage
 */
@RunWith(AndroidJUnit4::class)
class MonitoringSettingsDeviceTest {

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
    fun testRealDevice_defaultGlobalEnabledIsTrue() {
        val settings = repository.getSettings()
        assertTrue("Default globalEnabled must be true on real device", settings.globalEnabled)
    }

    @Test
    fun testRealDevice_toggleOff_persistsAcrossReload() {
        repository.setGlobalEnabled(false)

        val prefs = context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE)
        assertFalse(prefs.getBoolean(MonitoringSettingsRepository.KEY_GLOBAL_ENABLED, true))
        assertFalse(prefs.getBoolean(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING, true))

        // Create new instance pointing to real storage to simulate screen reopen / restart
        val reloaded = MonitoringSettingsRepository.getInstance(context)
        assertFalse("Reloaded repository on device must preserve OFF", reloaded.getSettings().globalEnabled)
    }

    @Test
    fun testRealDevice_toggleOn_persistsAcrossReload() {
        repository.setGlobalEnabled(false)
        assertFalse(repository.getSettings().globalEnabled)

        repository.setGlobalEnabled(true)
        val prefs = context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean(MonitoringSettingsRepository.KEY_GLOBAL_ENABLED, false))
        assertTrue(prefs.getBoolean(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING, false))

        val reloaded = MonitoringSettingsRepository.getInstance(context)
        assertTrue("Reloaded repository on device must preserve ON", reloaded.getSettings().globalEnabled)
    }

    @Test
    fun testRealDevice_toggleDoesNotMutateEnabledPackages() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        repository.setAppEnabled(gpay, true)
        val initialPackages = repository.getSettings().enabledPackages
        assertTrue(initialPackages.contains(gpay))

        repository.setGlobalEnabled(false)
        assertEquals("enabledPackages must remain unchanged on device when global OFF",
            initialPackages, repository.getSettings().enabledPackages)

        repository.setGlobalEnabled(true)
        assertEquals("enabledPackages must remain unchanged on device when global ON",
            initialPackages, repository.getSettings().enabledPackages)
    }

    @Test
    fun testRealDevice_individualAppToggle_persistsAcrossReload() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        repository.setAppEnabled(gpay, false)

        val reloaded = MonitoringSettingsRepository.getInstance(context)
        assertFalse("Disabled Google Pay must remain disabled across reload", reloaded.isAppEnabled(gpay))

        repository.setAppEnabled(gpay, true)
        val secondReload = MonitoringSettingsRepository.getInstance(context)
        assertTrue("Enabled Google Pay must remain enabled across reload", secondReload.isAppEnabled(gpay))
    }

    @Test
    fun testRealDevice_independentAppToggles_doNotAffectOtherApps() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        val phonepe = "com.phonepe.app"

        repository.setAppEnabled(gpay, true)
        repository.setAppEnabled(phonepe, false)

        val reloaded = MonitoringSettingsRepository.getInstance(context)
        assertTrue("Google Pay must be enabled", reloaded.isAppEnabled(gpay))
        assertFalse("PhonePe must be disabled", reloaded.isAppEnabled(phonepe))
    }

    @Test
    fun testRealDevice_appTogglesIndependentFromGlobalSwitch() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        val phonepe = "com.phonepe.app"

        repository.setAppEnabled(gpay, true)
        repository.setAppEnabled(phonepe, false)
        repository.setGlobalEnabled(false)

        val reloaded = MonitoringSettingsRepository.getInstance(context)
        assertFalse("Global switch is OFF", reloaded.getSettings().globalEnabled)
        assertTrue("Google Pay switch retains ON while global is OFF", reloaded.getSettings().isAppEnabled(gpay))
        assertFalse("PhonePe switch retains OFF while global is OFF", reloaded.getSettings().isAppEnabled(phonepe))

        repository.setGlobalEnabled(true)
        val reloaded2 = MonitoringSettingsRepository.getInstance(context)
        assertTrue("Global switch is ON", reloaded2.getSettings().globalEnabled)
        assertTrue("Google Pay switch retains ON after global is ON", reloaded2.getSettings().isAppEnabled(gpay))
        assertFalse("PhonePe switch retains OFF after global is ON", reloaded2.getSettings().isAppEnabled(phonepe))
    }

    @Test
    fun testRealDevice_allAppTogglesDisabled_persistsEmptySetWithoutReapplyingDefaults() {
        repository.setEnabledPackages(emptySet())

        val reloaded = MonitoringSettingsRepository.getInstance(context)
        assertTrue("Empty enabledPackages must not revert to defaultEnabled", reloaded.getSettings().enabledPackages.isEmpty())
    }
}
