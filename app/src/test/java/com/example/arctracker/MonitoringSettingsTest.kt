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
