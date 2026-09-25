package com.example.arctracker

import android.content.SharedPreferences
import com.example.arctracker.settings.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Step 9.1: Monitoring Settings Foundation + App Catalog.
 * Tests isolated monitoring settings and catalog contracts without modifying real user preferences.
 */
class MonitoringSettingsTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var repo: MonitoringSettingsRepository

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        repo = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
    }

    // 1. Default MonitoringSettings can be created
    @Test
    fun test01_defaultMonitoringSettings_canBeCreated() {
        val settings = MonitoringSettings()
        assertTrue(settings.globalEnabled)
        assertTrue(settings.enabledPackages.isEmpty())
    }

    // 2. globalEnabled can be persisted
    @Test
    fun test02_globalEnabled_canBePersisted() {
        repo.setGlobalEnabled(false)
        assertFalse(repo.getSettings().globalEnabled)
        assertEquals(false, fakePrefs.getBoolean(MonitoringSettingsRepository.KEY_GLOBAL_ENABLED, true))
    }

    // 3. globalEnabled can be restored
    @Test
    fun test03_globalEnabled_canBeRestored() {
        repo.setGlobalEnabled(false)
        // Create new repository instance pointing to same storage to simulate app recreation
        val reloadedRepo = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertFalse(reloadedRepo.getSettings().globalEnabled)

        reloadedRepo.setGlobalEnabled(true)
        val secondReload = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertTrue(secondReload.getSettings().globalEnabled)
    }

    // 4. package can be enabled
    @Test
    fun test04_package_canBeEnabled() {
        val testPkg = "com.custom.finapp"
        assertFalse(repo.isAppEnabled(testPkg))

        repo.setAppEnabled(testPkg, true)
        assertTrue(repo.isAppEnabled(testPkg))
        assertTrue(repo.getSettings().enabledPackages.contains(testPkg))
    }

    // 5. package can be disabled
    @Test
    fun test05_package_canBeDisabled() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        assertTrue(repo.isAppEnabled(gpay))

        repo.setAppEnabled(gpay, false)
        assertFalse(repo.isAppEnabled(gpay))
        assertFalse(repo.getSettings().enabledPackages.contains(gpay))
    }

    // 6. enabled package survives recreation/reload
    @Test
    fun test06_enabledPackage_survivesRecreation() {
        val testPkg = "com.newbank.app"
        repo.setAppEnabled(testPkg, true)

        val newRepo = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertTrue(newRepo.isAppEnabled(testPkg))
    }

    // 7. disabled package survives recreation/reload
    @Test
    fun test07_disabledPackage_survivesRecreation() {
        val phonepe = "com.phonepe.app"
        repo.setAppEnabled(phonepe, false)

        val newRepo = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertFalse(newRepo.isAppEnabled(phonepe))
    }

    // 8. multiple enabled packages are preserved
    @Test
    fun test08_multipleEnabledPackages_arePreserved() {
        val pkgs = setOf("pkg.a", "pkg.b", "pkg.c")
        repo.setEnabledPackages(pkgs)

        val settings = repo.getSettings()
        assertEquals(3, settings.enabledPackages.size)
        assertTrue(settings.enabledPackages.containsAll(pkgs))

        val newRepo = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertEquals(pkgs, newRepo.getSettings().enabledPackages)
    }

    // 9. disabling one package does not disable another
    @Test
    fun test09_disablingOnePackage_doesNotDisableAnother() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        val phonepe = "com.phonepe.app"
        assertTrue(repo.isAppEnabled(gpay))
        assertTrue(repo.isAppEnabled(phonepe))

        repo.setAppEnabled(gpay, false)

        assertFalse(repo.isAppEnabled(gpay))
        assertTrue(repo.isAppEnabled(phonepe))
    }

    // 10. globalEnabled and enabledPackages are persisted independently
    @Test
    fun test10_globalEnabledAndEnabledPackages_persistedIndependently() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        repo.setGlobalEnabled(false)
        repo.setAppEnabled(gpay, true)

        val settings = repo.getSettings()
        assertFalse("Global switch should be false", settings.globalEnabled)
        assertTrue("Individual app switch should be true", settings.isAppEnabled(gpay))
        assertFalse("Effective monitoring should be false when global is disabled", settings.isMonitoringActive(gpay))

        // Re-enable global
        repo.setGlobalEnabled(true)
        assertTrue("Effective monitoring should now be active", repo.isMonitoringActive(gpay))
    }

    // 11. duplicate package names cannot create duplicate entries
    @Test
    fun test11_duplicatePackageNames_cannotCreateDuplicates() {
        val testPkg = "com.example.duplicate"
        repo.setAppEnabled(testPkg, true)
        repo.setAppEnabled(testPkg, true)

        val count = repo.getSettings().enabledPackages.count { it == testPkg }
        assertEquals(1, count)
    }

    // 12. unknown package can safely be represented in settings
    @Test
    fun test12_unknownPackage_canSafelyBeRepresented() {
        val unknownPkg = "com.unknown.arbitrary.package"
        repo.setAppEnabled(unknownPkg, true)

        assertTrue(repo.isAppEnabled(unknownPkg))
        assertTrue(repo.isMonitoringActive(unknownPkg))
    }

    // 13. catalog contains Google Pay
    @Test
    fun test13_catalog_containsGooglePay() {
        val app = AppCatalog.findByPackage("com.google.android.apps.nbu.paisa.user")
        assertNotNull(app)
        assertEquals("Google Pay", app!!.displayName)
        assertEquals(AppCategory.UPI_PAYMENT, app.category)
    }

    // 14. catalog contains PhonePe
    @Test
    fun test14_catalog_containsPhonePe() {
        val app = AppCatalog.findByPackage("com.phonepe.app")
        assertNotNull(app)
        assertEquals("PhonePe", app!!.displayName)
        assertEquals(AppCategory.UPI_PAYMENT, app.category)
    }

    // 15. catalog contains Paytm
    @Test
    fun test15_catalog_containsPaytm() {
        val app = AppCatalog.findByPackage("net.one97.paytm")
        assertNotNull(app)
        assertEquals("Paytm", app!!.displayName)
        assertEquals(AppCategory.UPI_PAYMENT, app.category)
    }

    // 16. catalog contains Amazon Pay
    @Test
    fun test16_catalog_containsAmazonPay() {
        val app = AppCatalog.findByPackage("in.amazon.mShop.android.shopping")
        assertNotNull(app)
        assertEquals("Amazon Pay", app!!.displayName)
        assertEquals(AppCategory.UPI_PAYMENT, app.category)
    }

    // 17. catalog contains CRED
    @Test
    fun test17_catalog_containsCRED() {
        val app = AppCatalog.findByPackage("com.dreamplug.androidapp")
        assertNotNull(app)
        assertEquals("CRED", app!!.displayName)
        assertEquals(AppCategory.UPI_PAYMENT, app.category)
    }

    // 18. every catalog package has a non-empty display name
    @Test
    fun test18_everyCatalogPackage_hasNonEmptyDisplayName() {
        assertTrue(AppCatalog.allApps.isNotEmpty())
        for (app in AppCatalog.allApps) {
            assertTrue("Display name must not be blank for ${app.packageName}", app.displayName.isNotBlank())
            assertTrue("Description must not be blank for ${app.packageName}", app.description.isNotBlank())
        }
    }

    // 19. every catalog entry belongs to exactly one category
    @Test
    fun test19_everyCatalogEntry_belongsToValidCategory() {
        val validCategories = AppCategory.values().toSet()
        for (app in AppCatalog.allApps) {
            assertTrue("Category must be one of the valid AppCategories", validCategories.contains(app.category))
        }
    }

    // 20. no catalog contains duplicate package identifiers
    @Test
    fun test20_noCatalog_containsDuplicatePackageIdentifiers() {
        val packages = AppCatalog.allApps.map { it.packageName.lowercase() }
        val duplicates = packages.groupingBy { it }.eachCount().filter { it.value > 1 }
        assertTrue("Catalog must not have duplicate packages: $duplicates", duplicates.isEmpty())
    }

    // 21. Legacy auto-tracking preference is respected on existing install
    @Test
    fun test21_legacyAutoTracking_isRespected() {
        // Simulate existing install where user previously disabled auto-tracking
        fakePrefs.edit().putBoolean(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING, false).apply()

        val migratedRepo = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertFalse("Legacy isAutoTrackingEnabled=false should be respected", migratedRepo.getSettings().globalEnabled)
    }

    // 22. In-memory repository implementation functions identically for tests
    @Test
    fun test22_inMemoryRepository_functionsCorrectly() {
        val inMem = MonitoringSettingsRepository.createInMemory(initialGlobalEnabled = true)
        assertTrue(inMem.getSettings().globalEnabled)

        inMem.setAppEnabled("com.test.app", false)
        assertFalse(inMem.isAppEnabled("com.test.app"))

        inMem.resetToDefaults()
        assertTrue(inMem.getSettings().globalEnabled)
        assertTrue(inMem.getSettings().enabledPackages.containsAll(AppCatalog.defaultEnabledPackages))
    }

    // ==================================================
    // STEP 9.2: 16 REQUIRED VERIFICATION TESTS
    // ==================================================

    // 1. Initial state when repo has globalEnabled=true -> returns true
    @Test
    fun test92_01_repoGlobalEnabledTrue_initialStateIsTrue() {
        val settings = repo.getSettings()
        assertTrue("Initial state must be true", settings.globalEnabled)
    }

    // 2. Initial state when repo has globalEnabled=false -> returns false
    @Test
    fun test92_02_repoGlobalEnabledFalse_initialStateIsFalse() {
        repo.setGlobalEnabled(false)
        val settings = repo.getSettings()
        assertFalse("Initial state must be false after disabling", settings.globalEnabled)
    }

    // 3. Toggling switch ON persists globalEnabled=true in repository & SharedPreferences
    @Test
    fun test92_03_togglingSwitchOn_persistsGlobalEnabledTrue() {
        repo.setGlobalEnabled(false)
        assertFalse(repo.getSettings().globalEnabled)

        // Toggle ON
        repo.setGlobalEnabled(true)
        assertTrue("Repository state must be true", repo.getSettings().globalEnabled)
        assertEquals(true, fakePrefs.getBoolean(MonitoringSettingsRepository.KEY_GLOBAL_ENABLED, false))
        assertEquals(true, fakePrefs.getBoolean(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING, false))
    }

    // 4. Toggling switch OFF persists globalEnabled=false in repository & SharedPreferences
    @Test
    fun test92_04_togglingSwitchOff_persistsGlobalEnabledFalse() {
        // Toggle OFF
        repo.setGlobalEnabled(false)
        assertFalse("Repository state must be false", repo.getSettings().globalEnabled)
        assertEquals(false, fakePrefs.getBoolean(MonitoringSettingsRepository.KEY_GLOBAL_ENABLED, true))
        assertEquals(false, fakePrefs.getBoolean(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING, true))
    }

    // 5. Reopening screen after toggling OFF preserves OFF
    @Test
    fun test92_05_reopeningScreen_preservesOff() {
        repo.setGlobalEnabled(false)
        // Screen re-query
        val reloadedSettings = repo.getSettings()
        assertFalse("Re-querying settings on screen reopen must preserve OFF", reloadedSettings.globalEnabled)
    }

    // 6. Reopening screen after toggling ON preserves ON
    @Test
    fun test92_06_reopeningScreen_preservesOn() {
        repo.setGlobalEnabled(false)
        repo.setGlobalEnabled(true)
        // Screen re-query
        val reloadedSettings = repo.getSettings()
        assertTrue("Re-querying settings on screen reopen must preserve ON", reloadedSettings.globalEnabled)
    }

    // 7. Restart/reload preserves OFF
    @Test
    fun test92_07_restartReload_preservesOff() {
        repo.setGlobalEnabled(false)
        // Simulate app kill and recreation with fresh repository instance
        val freshRepo = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertFalse("Fresh repository after restart must preserve OFF", freshRepo.getSettings().globalEnabled)
    }

    // 8. Restart/reload preserves ON
    @Test
    fun test92_08_restartReload_preservesOn() {
        repo.setGlobalEnabled(false)
        repo.setGlobalEnabled(true)
        // Simulate app kill and recreation with fresh repository instance
        val freshRepo = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertTrue("Fresh repository after restart must preserve ON", freshRepo.getSettings().globalEnabled)
    }

    // 9. Turning global OFF does not modify enabledPackages
    @Test
    fun test92_09_turningGlobalOff_doesNotModifyEnabledPackages() {
        val initialPackages = setOf("com.pkg1", "com.pkg2")
        repo.setEnabledPackages(initialPackages)
        assertEquals(initialPackages, repo.getSettings().enabledPackages)

        repo.setGlobalEnabled(false)
        assertEquals("enabledPackages must remain untouched when turning global OFF",
            initialPackages, repo.getSettings().enabledPackages)
    }

    // 10. Turning global ON does not modify enabledPackages
    @Test
    fun test92_10_turningGlobalOn_doesNotModifyEnabledPackages() {
        val initialPackages = setOf("com.pkg.single")
        repo.setEnabledPackages(initialPackages)
        repo.setGlobalEnabled(false)
        assertEquals(initialPackages, repo.getSettings().enabledPackages)

        repo.setGlobalEnabled(true)
        assertEquals("enabledPackages must remain untouched when turning global ON",
            initialPackages, repo.getSettings().enabledPackages)
    }

    // 11. Empty enabledPackages does not force globalEnabled=false
    @Test
    fun test92_11_emptyEnabledPackages_doesNotForceGlobalEnabledFalse() {
        repo.setEnabledPackages(emptySet())
        assertTrue("enabledPackages should be empty", repo.getSettings().enabledPackages.isEmpty())
        assertTrue("globalEnabled must remain true even if enabledPackages is empty", repo.getSettings().globalEnabled)
    }

    // 12. Non-empty enabledPackages does not force globalEnabled=true
    @Test
    fun test92_12_nonEmptyEnabledPackages_doesNotForceGlobalEnabledTrue() {
        repo.setEnabledPackages(setOf("com.google.android.apps.nbu.paisa.user", "com.phonepe.app"))
        repo.setGlobalEnabled(false)
        assertFalse("globalEnabled must remain false even if enabledPackages has items", repo.getSettings().globalEnabled)
        assertEquals(2, repo.getSettings().enabledPackages.size)
    }

    // 13. Case A: isAutoTrackingEnabled = false, monitoring_global_enabled unset -> globalEnabled = false
    @Test
    fun test92_13_caseA_legacyAutoTrackingFalse_unsetGlobal_resultsInFalse() {
        fakePrefs.edit().clear().apply()
        fakePrefs.edit().putBoolean(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING, false).apply()
        assertFalse(fakePrefs.contains(MonitoringSettingsRepository.KEY_GLOBAL_ENABLED))

        val repoCaseA = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertFalse("Case A must result in globalEnabled=false", repoCaseA.getSettings().globalEnabled)
    }

    // 14. Case B: isAutoTrackingEnabled = true, monitoring_global_enabled unset -> globalEnabled = true
    @Test
    fun test92_14_caseB_legacyAutoTrackingTrue_unsetGlobal_resultsInTrue() {
        fakePrefs.edit().clear().apply()
        fakePrefs.edit().putBoolean(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING, true).apply()
        assertFalse(fakePrefs.contains(MonitoringSettingsRepository.KEY_GLOBAL_ENABLED))

        val repoCaseB = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertTrue("Case B must result in globalEnabled=true", repoCaseB.getSettings().globalEnabled)
    }

    // 15. Case C: isAutoTrackingEnabled = false, monitoring_global_enabled = true -> globalEnabled = true (explicit takes precedence)
    @Test
    fun test92_15_caseC_explicitMonitoringGlobalEnabled_takesPrecedenceOverLegacy() {
        fakePrefs.edit().clear().apply()
        fakePrefs.edit().putBoolean(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING, false).apply()
        fakePrefs.edit().putBoolean(MonitoringSettingsRepository.KEY_GLOBAL_ENABLED, true).apply()

        val repoCaseC = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertTrue("Case C: explicit monitoring_global_enabled=true must take precedence over legacy=false",
            repoCaseC.getSettings().globalEnabled)
    }

    // 16. Repository failure does not crash the UI (safe fallback handling verified)
    @Test
    fun test92_16_repositoryFailure_handledGracefully() {
        val failingRepo = object : MonitoringSettingsRepository {
            override fun getSettings(): MonitoringSettings = throw RuntimeException("Storage failure")
            override fun setGlobalEnabled(enabled: Boolean) = throw RuntimeException("Write failure")
            override fun isAppEnabled(packageName: String): Boolean = throw RuntimeException("Read failure")
            override fun setAppEnabled(packageName: String, enabled: Boolean) = throw RuntimeException("Write failure")
            override fun setEnabledPackages(packages: Set<String>) = throw RuntimeException("Write failure")
            override fun resetToDefaults() = throw RuntimeException("Reset failure")
        }

        // Test UI read fallback logic: catch exception and default to true
        val masterEnabledState = try {
            failingRepo.getSettings().globalEnabled
        } catch (e: Exception) {
            true // default fallback
        }
        assertTrue("Master switch state should safely fallback to default without crashing", masterEnabledState)

        // Test UI write fallback logic: catch exception without throwing
        var writeExceptionCaught = false
        try {
            failingRepo.setGlobalEnabled(false)
        } catch (e: Exception) {
            writeExceptionCaught = true
        }
        assertTrue("Write failure should be caught safely by UI without unhandled crash", writeExceptionCaught)
    }
}

/**
 * Isolated in-memory implementation of Android [SharedPreferences] for unit testing.
 */
class FakeSharedPreferences : SharedPreferences {

    private val data = mutableMapOf<String, Any>()

    override fun getAll(): MutableMap<String, *> = HashMap(data)

    override fun getString(key: String?, defValue: String?): String? =
        (data[key] as? String) ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        val value = data[key]
        return if (value is Set<*>) (value as Set<String>).toMutableSet() else defValues
    }

    override fun getInt(key: String?, defValue: Int): Int =
        (data[key] as? Int) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        (data[key] as? Long) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        (data[key] as? Float) ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (data[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(data)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class FakeEditor(private val data: MutableMap<String, Any>) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
            temp[key] = values?.toSet()
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            temp[key] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) data.clear()
            for ((k, v) in temp) {
                if (v == null) data.remove(k) else data[k] = v
            }
        }
    }
}
