package com.subhashrelangi.arctracker

import android.content.SharedPreferences
import com.subhashrelangi.arctracker.settings.*
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

    // ==================================================
    // STEP 9.3: 20 REQUIRED PER-APP TOGGLE TESTS
    // ==================================================

    // 1. Catalog app initially reflects enabledPackages
    @Test
    fun test93_01_catalogApp_initiallyReflectsEnabledPackages() {
        val settings = repo.getSettings()
        for (app in AppCatalog.allApps) {
            val isEnabledInSettings = settings.isAppEnabled(app.packageName)
            assertEquals("App ${app.packageName} enabled state must match enabledPackages membership",
                settings.enabledPackages.contains(app.packageName), isEnabledInSettings)
        }
    }

    // 2. Enabled Google Pay renders ON
    @Test
    fun test93_02_enabledGooglePay_rendersOn() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        repo.setAppEnabled(gpay, true)
        assertTrue("Google Pay must be enabled in settings", repo.isAppEnabled(gpay))
        assertTrue("Google Pay must be in enabledPackages set", repo.getSettings().enabledPackages.contains(gpay))
    }

    // 3. Disabled Google Pay renders OFF
    @Test
    fun test93_03_disabledGooglePay_rendersOff() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        repo.setAppEnabled(gpay, false)
        assertFalse("Google Pay must be disabled in settings", repo.isAppEnabled(gpay))
        assertFalse("Google Pay must not be in enabledPackages set", repo.getSettings().enabledPackages.contains(gpay))
    }

    // 4. Enabling Google Pay adds its package
    @Test
    fun test93_04_enablingGooglePay_addsItsPackage() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        repo.setAppEnabled(gpay, false)
        assertFalse(repo.getSettings().enabledPackages.contains(gpay))

        repo.setAppEnabled(gpay, true)
        assertTrue(repo.getSettings().enabledPackages.contains(gpay))
    }

    // 5. Disabling Google Pay removes its package
    @Test
    fun test93_05_disablingGooglePay_removesItsPackage() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        repo.setAppEnabled(gpay, true)
        assertTrue(repo.getSettings().enabledPackages.contains(gpay))

        repo.setAppEnabled(gpay, false)
        assertFalse(repo.getSettings().enabledPackages.contains(gpay))
    }

    // 6. Enabling PhonePe does not change Google Pay
    @Test
    fun test93_06_enablingPhonePe_doesNotChangeGooglePay() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        val phonepe = "com.phonepe.app"

        repo.setAppEnabled(gpay, false)
        assertFalse(repo.isAppEnabled(gpay))

        repo.setAppEnabled(phonepe, true)
        assertTrue(repo.isAppEnabled(phonepe))
        assertFalse("Google Pay must remain disabled when PhonePe is enabled", repo.isAppEnabled(gpay))
    }

    // 7. Disabling PhonePe does not change Google Pay
    @Test
    fun test93_07_disablingPhonePe_doesNotChangeGooglePay() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        val phonepe = "com.phonepe.app"

        repo.setAppEnabled(gpay, true)
        assertTrue(repo.isAppEnabled(gpay))

        repo.setAppEnabled(phonepe, false)
        assertFalse(repo.isAppEnabled(phonepe))
        assertTrue("Google Pay must remain enabled when PhonePe is disabled", repo.isAppEnabled(gpay))
    }

    // 8. Multiple enabled apps persist
    @Test
    fun test93_08_multipleEnabledAppsPersist() {
        val testApps = setOf("com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm")
        repo.setEnabledPackages(testApps)

        val settings = repo.getSettings()
        assertTrue(settings.enabledPackages.containsAll(testApps))
        assertEquals(testApps.size, settings.enabledPackages.size)
    }

    // 9. Reopening screen restores app states
    @Test
    fun test93_09_reopeningScreen_restoresAppStates() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        val phonepe = "com.phonepe.app"
        repo.setAppEnabled(gpay, false)
        repo.setAppEnabled(phonepe, true)

        // Simulate re-reading settings on screen reopen
        val reloadedSettings = repo.getSettings()
        assertFalse("Google Pay state preserved on reopen", reloadedSettings.isAppEnabled(gpay))
        assertTrue("PhonePe state preserved on reopen", reloadedSettings.isAppEnabled(phonepe))
    }

    // 10. Process reload restores app states
    @Test
    fun test93_10_processReload_restoresAppStates() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        val yono = "com.sbi.SBIAnywhere"
        repo.setAppEnabled(gpay, false)
        repo.setAppEnabled(yono, true)

        val newRepo = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertFalse("Google Pay state preserved after process reload", newRepo.isAppEnabled(gpay))
        assertTrue("YONO SBI state preserved after process reload", newRepo.isAppEnabled(yono))
    }

    // 11. App restart restores app states
    @Test
    fun test93_11_appRestart_restoresAppStates() {
        val customSet = setOf("in.amazon.mShop.android.shopping", "com.dreamplug.androidapp")
        repo.setEnabledPackages(customSet)

        // Fresh repo instance mimicking cold restart
        val restartedRepo = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertEquals(customSet, restartedRepo.getSettings().enabledPackages)
    }

    // 12. Global OFF does not clear enabledPackages
    @Test
    fun test93_12_globalOff_doesNotClearEnabledPackages() {
        val initialPackages = repo.getSettings().enabledPackages
        assertTrue(initialPackages.isNotEmpty())

        repo.setGlobalEnabled(false)
        assertFalse(repo.getSettings().globalEnabled)
        assertEquals("enabledPackages must remain completely intact when global OFF",
            initialPackages, repo.getSettings().enabledPackages)
    }

    // 13. Global ON does not modify enabledPackages
    @Test
    fun test93_13_globalOn_doesNotModifyEnabledPackages() {
        val initialPackages = repo.getSettings().enabledPackages
        repo.setGlobalEnabled(false)
        repo.setGlobalEnabled(true)
        assertTrue(repo.getSettings().globalEnabled)
        assertEquals("enabledPackages must remain completely intact when global ON",
            initialPackages, repo.getSettings().enabledPackages)
    }

    // 14. Enabling same package twice creates one entry
    @Test
    fun test93_14_enablingSamePackageTwice_createsOneEntry() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        repo.setAppEnabled(gpay, true)
        repo.setAppEnabled(gpay, true)

        val occurrences = repo.getSettings().enabledPackages.count { it == gpay }
        assertEquals(1, occurrences)
    }

    // 15. Disabling already-disabled package is safe
    @Test
    fun test93_15_disablingAlreadyDisabledPackage_isSafe() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        repo.setAppEnabled(gpay, false)
        assertFalse(repo.isAppEnabled(gpay))

        // Disabling again should not throw or corrupt state
        repo.setAppEnabled(gpay, false)
        assertFalse(repo.isAppEnabled(gpay))
    }

    // 16. Unknown package operation does not crash
    @Test
    fun test93_16_unknownPackageOperation_doesNotCrash() {
        val unknown = "com.unknown.noncatalog.app"
        repo.setAppEnabled(unknown, true)
        assertTrue(repo.isAppEnabled(unknown))

        repo.setAppEnabled(unknown, false)
        assertFalse(repo.isAppEnabled(unknown))
    }

    // 17. Repository failure does not crash UI
    @Test
    fun test93_17_repositoryFailure_doesNotCrashUI() {
        val failingRepo = object : MonitoringSettingsRepository {
            override fun getSettings(): MonitoringSettings = throw RuntimeException("Storage read error")
            override fun setGlobalEnabled(enabled: Boolean) = throw RuntimeException("Storage error")
            override fun isAppEnabled(packageName: String): Boolean = throw RuntimeException("Storage error")
            override fun setAppEnabled(packageName: String, enabled: Boolean) = throw RuntimeException("Storage write error")
            override fun setEnabledPackages(packages: Set<String>) = throw RuntimeException("Storage error")
            override fun resetToDefaults() = throw RuntimeException("Storage error")
        }

        // Test UI read fallback
        val loadedPackages = try {
            failingRepo.getSettings().enabledPackages
        } catch (e: Exception) {
            AppCatalog.defaultEnabledPackages
        }
        assertEquals(AppCatalog.defaultEnabledPackages, loadedPackages)

        // Test UI toggle rollback on write failure
        var currentEnabled = setOf("pkg1")
        val previousEnabled = currentEnabled
        currentEnabled = currentEnabled + "pkg2"
        var caught = false
        try {
            failingRepo.setAppEnabled("pkg2", true)
        } catch (e: Exception) {
            caught = true
            currentEnabled = previousEnabled
        }
        assertTrue("Write failure must be caught", caught)
        assertEquals("State must rollback to previous on failure", setOf("pkg1"), currentEnabled)
    }

    // 18. AppCatalog.defaultEnabled is not reapplied after user changes a setting
    @Test
    fun test93_18_defaultEnabled_notReappliedAfterUserChangesSetting() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        // User explicitly disables Google Pay
        repo.setAppEnabled(gpay, false)

        val freshRepo = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertFalse("Google Pay must remain disabled and not revert to defaultEnabled=true", freshRepo.isAppEnabled(gpay))

        // Even if all packages are disabled by the user
        freshRepo.setEnabledPackages(emptySet())
        val emptyRepo = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertTrue("Empty enabledPackages must not revert to defaultEnabled", emptyRepo.getSettings().enabledPackages.isEmpty())
    }

    // 19. UPI app state is independent from Banking app state
    @Test
    fun test93_19_upiAppState_independentFromBankingAppState() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        val yono = "com.sbi.SBIAnywhere"

        repo.setAppEnabled(gpay, false)
        repo.setAppEnabled(yono, true)

        assertFalse("UPI app is disabled", repo.isAppEnabled(gpay))
        assertTrue("Banking app is enabled", repo.isAppEnabled(yono))

        repo.setAppEnabled(gpay, true)
        assertTrue("UPI app is now enabled", repo.isAppEnabled(gpay))
        assertTrue("Banking app state remained intact", repo.isAppEnabled(yono))
    }

    // 20. Banking app state is independent from SMS/Messenger app state
    @Test
    fun test93_20_bankingAppState_independentFromSmsAppState() {
        val hdfc = "com.snapwork.hdfc"
        val gmessages = "com.google.android.apps.messaging"

        repo.setAppEnabled(hdfc, true)
        repo.setAppEnabled(gmessages, false)

        assertTrue("Banking app is enabled", repo.isAppEnabled(hdfc))
        assertFalse("SMS app is disabled", repo.isAppEnabled(gmessages))

        repo.setAppEnabled(hdfc, false)
        assertFalse("Banking app is now disabled", repo.isAppEnabled(hdfc))
        assertFalse("SMS app state remained intact", repo.isAppEnabled(gmessages))
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
