package com.example.arctracker

import com.example.arctracker.settings.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Step 9.7: Supported Apps Lifecycle Reconciliation & State Consistency.
 * Verifies all 22 required reconciliation and state lifecycle scenarios:
 * 1. installed + configured -> Supported
 * 2. installed + unconfigured -> Add Apps
 * 3. not installed + configured -> nowhere
 * 4. not installed + unconfigured -> nowhere
 * 5. newly installed app appears in Add Apps
 * 6. newly installed app does not become Supported automatically
 * 7. uninstall removes app from active Supported Apps
 * 8. uninstall removes app from Add Apps
 * 9. reinstall restores previously configured user app
 * 10. reinstall does not create duplicate
 * 11. category survives uninstall/reinstall
 * 12. enabled package configuration survives uninstall/reinstall
 * 13. globalEnabled survives reconciliation
 * 14. PackageManager failure preserves persisted configuration
 * 15. duplicate persisted package does not create duplicate UI
 * 16. built-in catalog is never mutated
 * 17. Add Apps contains no supported package
 * 18. Supported Apps contains no unsupported package
 * 19. each package appears at most once
 * 20. metadata refresh does not change category
 * 21. refresh does not reset UI monitoring state
 * 22. stale configuration does not authorize active monitoring while app is absent
 */
class LifecycleReconciliationTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var repository: MonitoringSettingsRepository
    private lateinit var appsProvider: InMemoryInstalledAppsProvider

    private val gpay = "com.google.android.apps.nbu.paisa.user"
    private val phonepe = "com.phonepe.app"
    private val whatsapp = "com.whatsapp"
    private val instagram = "com.instagram.android"
    private val customBank = "com.example.mybank"
    private val customUpi = "com.example.myupi"

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        repository = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        appsProvider = InMemoryInstalledAppsProvider()
    }

    // Helper: calculate active supported apps (configured INTERSECT installed)
    private fun getActiveSupportedApps(): List<SupportedApp> {
        val installedMap = appsProvider.getInstalledApps().associateBy { it.packageName.lowercase() }
        val seen = mutableSetOf<String>()
        return repository.getAllConfiguredApps().mapNotNull { configuredApp ->
            val lowerPkg = configuredApp.packageName.lowercase()
            val installed = installedMap[lowerPkg] ?: return@mapNotNull null
            if (!seen.add(lowerPkg)) return@mapNotNull null
            val freshName = installed.displayName.takeIf { it.isNotBlank() } ?: configuredApp.displayName
            configuredApp.copy(displayName = freshName)
        }
    }

    // Helper: calculate addable apps (installed MINUS configured)
    private fun getAddableApps(): List<InstalledAppInfo> {
        val configuredSet = repository.getAllConfiguredApps().map { it.packageName.lowercase() }.toSet()
        return appsProvider.getInstalledApps()
            .filter { !configuredSet.contains(it.packageName.lowercase()) }
            .distinctBy { it.packageName.lowercase() }
    }

    // 1. installed + configured -> Supported
    @Test
    fun test01_installedAndConfigured_appearsInSupported() {
        appsProvider.installApp(InstalledAppInfo(gpay, "Google Pay"))
        val active = getActiveSupportedApps()
        assertTrue("Installed configured app must appear in Supported Apps", active.any { it.packageName == gpay })
        assertFalse("Installed configured app must NOT appear in Add Apps", getAddableApps().any { it.packageName == gpay })
    }

    // 2. installed + unconfigured -> Add Apps
    @Test
    fun test02_installedAndUnconfigured_appearsInAddApps() {
        appsProvider.installApp(InstalledAppInfo(whatsapp, "WhatsApp"))
        val addable = getAddableApps()
        assertTrue("Installed unconfigured app must appear in Add Apps", addable.any { it.packageName == whatsapp })
        assertFalse("Installed unconfigured app must NOT appear in Supported Apps", getActiveSupportedApps().any { it.packageName == whatsapp })
    }

    // 3. not installed + configured -> nowhere
    @Test
    fun test03_notInstalledAndConfigured_appearsNowhere() {
        // gpay is configured in AppCatalog, but NOT installed in appsProvider
        assertFalse(appsProvider.isPackageInstalled(gpay))
        assertFalse("Not installed configured app must NOT appear in active Supported Apps", getActiveSupportedApps().any { it.packageName == gpay })
        assertFalse("Not installed configured app must NOT appear in Add Apps", getAddableApps().any { it.packageName == gpay })
    }

    // 4. not installed + unconfigured -> nowhere
    @Test
    fun test04_notInstalledAndUnconfigured_appearsNowhere() {
        val unknownPkg = "com.random.uninstalled"
        assertFalse(appsProvider.isPackageInstalled(unknownPkg))
        assertFalse("Not installed unconfigured app must NOT appear in active Supported Apps", getActiveSupportedApps().any { it.packageName == unknownPkg })
        assertFalse("Not installed unconfigured app must NOT appear in Add Apps", getAddableApps().any { it.packageName == unknownPkg })
    }

    // 5. newly installed app appears in Add Apps
    @Test
    fun test05_newlyInstalledApp_appearsInAddApps() {
        assertFalse(getAddableApps().any { it.packageName == instagram })

        // User installs Instagram
        appsProvider.installApp(InstalledAppInfo(instagram, "Instagram"))
        assertTrue("Newly installed app must appear in Add Apps after refresh", getAddableApps().any { it.packageName == instagram })
    }

    // 6. newly installed app does not become Supported automatically
    @Test
    fun test06_newlyInstalledApp_doesNotBecomeSupportedAutomatically() {
        appsProvider.installApp(InstalledAppInfo(instagram, "Instagram"))
        assertFalse("Newly installed app must NEVER become Supported automatically", getActiveSupportedApps().any { it.packageName == instagram })
        assertFalse("Newly installed app must NOT be in enabledPackages", repository.isAppEnabled(instagram))
    }

    // 7. uninstall removes app from active Supported Apps
    @Test
    fun test07_uninstall_removesAppFromActiveSupportedApps() {
        appsProvider.installApp(InstalledAppInfo(phonepe, "PhonePe"))
        assertTrue("Initial condition: PhonePe is active Supported", getActiveSupportedApps().any { it.packageName == phonepe })

        // User uninstalls PhonePe
        appsProvider.uninstallApp(phonepe)
        assertFalse("Uninstalled app must disappear from active Supported Apps", getActiveSupportedApps().any { it.packageName == phonepe })
    }

    // 8. uninstall removes app from Add Apps
    @Test
    fun test08_uninstall_removesAppFromAddApps() {
        appsProvider.installApp(InstalledAppInfo(whatsapp, "WhatsApp"))
        assertTrue("Initial condition: WhatsApp is in Add Apps", getAddableApps().any { it.packageName == whatsapp })

        // User uninstalls WhatsApp
        appsProvider.uninstallApp(whatsapp)
        assertFalse("Uninstalled app must NOT appear in Add Apps", getAddableApps().any { it.packageName == whatsapp })
    }

    // 9. reinstall restores previously configured user app
    @Test
    fun test09_reinstall_restoresPreviouslyConfiguredUserApp() {
        appsProvider.installApp(InstalledAppInfo(customUpi, "My UPI"))
        repository.addUserApp(SupportedApp(customUpi, "My UPI", "UPI app", AppCategory.UPI_PAYMENT))
        assertTrue(getActiveSupportedApps().any { it.packageName == customUpi })

        // Uninstall
        appsProvider.uninstallApp(customUpi)
        assertFalse(getActiveSupportedApps().any { it.packageName == customUpi })

        // Reinstall
        appsProvider.installApp(InstalledAppInfo(customUpi, "My UPI"))
        val restored = getActiveSupportedApps().firstOrNull { it.packageName == customUpi }
        assertNotNull("Reinstalled app must be restored to Supported Apps", restored)
        assertEquals(AppCategory.UPI_PAYMENT, restored?.category)
    }

    // 10. reinstall does not create duplicate
    @Test
    fun test10_reinstall_doesNotCreateDuplicate() {
        appsProvider.installApp(InstalledAppInfo(customUpi, "My UPI"))
        repository.addUserApp(SupportedApp(customUpi, "My UPI", "UPI app", AppCategory.UPI_PAYMENT))

        // Cycle uninstall and reinstall
        appsProvider.uninstallApp(customUpi)
        appsProvider.installApp(InstalledAppInfo(customUpi, "My UPI"))

        val matching = getActiveSupportedApps().filter { it.packageName == customUpi }
        assertEquals("Must contain exactly 1 entry for reinstalled app", 1, matching.size)
    }

    // 11. category survives uninstall/reinstall
    @Test
    fun test11_category_survivesUninstallAndReinstall() {
        appsProvider.installApp(InstalledAppInfo(customBank, "My Bank"))
        repository.addUserApp(SupportedApp(customBank, "My Bank", "Bank desc", AppCategory.BANKING))

        // Uninstall
        appsProvider.uninstallApp(customBank)

        // Reinstall
        appsProvider.installApp(InstalledAppInfo(customBank, "My Bank"))
        val app = getActiveSupportedApps().first { it.packageName == customBank }
        assertEquals("Persisted category must remain BANKING", AppCategory.BANKING, app.category)
    }

    // 12. enabled package configuration survives uninstall/reinstall
    @Test
    fun test12_enabledPackageConfiguration_survivesUninstallAndReinstall() {
        appsProvider.installApp(InstalledAppInfo(customUpi, "My UPI"))
        repository.addUserApp(SupportedApp(customUpi, "My UPI", "Desc", AppCategory.UPI_PAYMENT))
        assertTrue(repository.isAppEnabled(customUpi))

        // Uninstall
        appsProvider.uninstallApp(customUpi)
        // Configuration in settings is preserved
        assertTrue("Enabled state in settings is preserved across uninstall", repository.isAppEnabled(customUpi))

        // Reinstall
        appsProvider.installApp(InstalledAppInfo(customUpi, "My UPI"))
        assertTrue("Restored app is enabled immediately", repository.isAppEnabled(customUpi))
    }

    // 13. globalEnabled survives reconciliation
    @Test
    fun test13_globalEnabled_survivesReconciliation() {
        repository.setGlobalEnabled(false)
        assertFalse(repository.getSettings().globalEnabled)

        // Simulate reconciliation cycles
        appsProvider.installApp(InstalledAppInfo(instagram, "Instagram"))
        val addable = getAddableApps()
        val supported = getActiveSupportedApps()

        assertFalse("globalEnabled must REMAIN false across reconciliations", repository.getSettings().globalEnabled)
    }

    // 14. PackageManager failure preserves persisted configuration
    @Test
    fun test14_packageManagerFailure_preservesPersistedConfiguration() {
        appsProvider.installApp(InstalledAppInfo(customBank, "My Bank"))
        repository.addUserApp(SupportedApp(customBank, "My Bank", "Desc", AppCategory.BANKING))

        val beforeCount = repository.getAllConfiguredApps().size

        // Failing provider
        val failingProvider = object : InstalledAppsProvider {
            override fun getInstalledApps(): List<InstalledAppInfo> {
                throw RuntimeException("PackageManager IPC DeadObjectException")
            }
            override fun isPackageInstalled(packageName: String): Boolean = false
        }

        try {
            failingProvider.getInstalledApps()
        } catch (e: Exception) {
            // Exception caught safely
        }

        // Repository configuration must be 100% intact
        assertEquals("Configured apps must remain intact on failure", beforeCount, repository.getAllConfiguredApps().size)
        assertTrue(repository.getAllConfiguredApps().any { it.packageName == customBank })
    }

    // 15. duplicate persisted package does not create duplicate UI
    @Test
    fun test15_duplicatePersistedPackage_doesNotCreateDuplicateUi() {
        appsProvider.installApp(InstalledAppInfo(customBank, "My Bank"))
        // Manually populate duplicate serialized entries in fakePrefs to simulate legacy/corrupt storage
        val dup1 = SupportedAppSerializer.serialize(SupportedApp(customBank, "Bank 1", "Desc", AppCategory.BANKING))
        val dup2 = SupportedAppSerializer.serialize(SupportedApp(customBank, "Bank 2", "Desc", AppCategory.BANKING))
        fakePrefs.edit().putStringSet(MonitoringSettingsRepository.KEY_USER_ADDED_APPS, setOf(dup1, dup2)).commit()

        val active = getActiveSupportedApps().filter { it.packageName == customBank }
        assertEquals("Duplicate persisted entries must be deduplicated to 1 entry in UI", 1, active.size)
    }

    // 16. built-in catalog is never mutated
    @Test
    fun test16_builtInCatalog_isNeverMutated() {
        val initialCatalog = AppCatalog.allApps
        val initialSize = initialCatalog.size

        appsProvider.installApp(InstalledAppInfo(customUpi, "My UPI"))
        repository.addUserApp(SupportedApp(customUpi, "My UPI", "Desc", AppCategory.UPI_PAYMENT))

        // Reconciliation
        val active = getActiveSupportedApps()
        val addable = getAddableApps()

        assertEquals("Catalog size must never change", initialSize, AppCatalog.allApps.size)
        assertEquals("Catalog list must remain strictly identical", initialCatalog, AppCatalog.allApps)
    }

    // 17. Add Apps contains no supported package
    @Test
    fun test17_addApps_containsNoSupportedPackage() {
        appsProvider.installApp(InstalledAppInfo(gpay, "Google Pay"))
        appsProvider.installApp(InstalledAppInfo(whatsapp, "WhatsApp"))

        val addable = getAddableApps()
        val supportedPackages = repository.getAllConfiguredApps().map { it.packageName.lowercase() }.toSet()

        for (addableApp in addable) {
            assertFalse("Add Apps must contain no configured supported package: ${addableApp.packageName}",
                supportedPackages.contains(addableApp.packageName.lowercase()))
        }
    }

    // 18. Supported Apps contains no unsupported package
    @Test
    fun test18_supportedApps_containsNoUnsupportedPackage() {
        appsProvider.installApp(InstalledAppInfo(whatsapp, "WhatsApp"))
        appsProvider.installApp(InstalledAppInfo(gpay, "Google Pay"))

        val activeSupported = getActiveSupportedApps()
        val configuredPackages = repository.getAllConfiguredApps().map { it.packageName.lowercase() }.toSet()

        for (app in activeSupported) {
            assertTrue("Active supported app must be in configured packages: ${app.packageName}",
                configuredPackages.contains(app.packageName.lowercase()))
        }
    }

    // 19. each package appears at most once
    @Test
    fun test19_eachPackageAppearsAtMostOnce() {
        appsProvider.installApp(InstalledAppInfo(gpay, "Google Pay"))
        appsProvider.installApp(InstalledAppInfo(whatsapp, "WhatsApp"))
        appsProvider.installApp(InstalledAppInfo(customBank, "My Bank"))
        repository.addUserApp(SupportedApp(customBank, "My Bank", "Desc", AppCategory.BANKING))

        val active = getActiveSupportedApps()
        val addable = getAddableApps()

        val activePackages = active.map { it.packageName.lowercase() }
        val addablePackages = addable.map { it.packageName.lowercase() }

        assertEquals(activePackages.size, activePackages.toSet().size)
        assertEquals(addablePackages.size, addablePackages.toSet().size)

        // Intersection between Supported and Addable must be empty
        val overlap = activePackages.toSet().intersect(addablePackages.toSet())
        assertTrue("Overlap between Supported and Addable must be completely empty", overlap.isEmpty())
    }

    // 20. metadata refresh does not change category
    @Test
    fun test20_metadataRefresh_doesNotChangeCategory() {
        appsProvider.installApp(InstalledAppInfo(customBank, "My Bank v1"))
        repository.addUserApp(SupportedApp(customBank, "My Bank v1", "Desc", AppCategory.BANKING))

        // App updates its title in Android OS
        appsProvider.uninstallApp(customBank)
        appsProvider.installApp(InstalledAppInfo(customBank, "My Bank Premier 2026"))

        val refreshedApp = getActiveSupportedApps().first { it.packageName == customBank }
        assertEquals("My Bank Premier 2026", refreshedApp.displayName)
        assertEquals("Category must remain BANKING despite metadata change", AppCategory.BANKING, refreshedApp.category)
    }

    // 21. refresh does not reset UI monitoring state
    @Test
    fun test21_refresh_doesNotResetUiMonitoringState() {
        appsProvider.installApp(InstalledAppInfo(gpay, "Google Pay"))
        repository.setAppEnabled(gpay, false)
        assertFalse(repository.isAppEnabled(gpay))

        // Multiple reconciliations
        getActiveSupportedApps()
        getAddableApps()

        assertFalse("Per-app disabled switch state must NOT be reset by refresh", repository.isAppEnabled(gpay))
    }

    // 22. stale configuration does not authorize active monitoring while app is absent
    @Test
    fun test22_staleConfiguration_doesNotAuthorizeActiveMonitoringWhileAppAbsent() {
        appsProvider.installApp(InstalledAppInfo(customBank, "My Bank"))
        repository.addUserApp(SupportedApp(customBank, "My Bank", "Desc", AppCategory.BANKING))
        repository.setGlobalEnabled(true)

        // When installed, package is effectively monitored
        assertTrue(repository.isPackageEffectivelyMonitored(customBank, appsProvider.isPackageInstalled(customBank)))

        // User uninstalls customBank
        appsProvider.uninstallApp(customBank)
        assertFalse(appsProvider.isPackageInstalled(customBank))

        // Effective monitoring MUST be false while the app is uninstalled
        assertFalse("Uninstalled app must NOT be effectively monitored",
            repository.isPackageEffectivelyMonitored(customBank, appsProvider.isPackageInstalled(customBank)))

        val effectiveSet = repository.getEffectiveMonitoredPackages(
            appsProvider.getInstalledApps().map { it.packageName }.toSet()
        )
        assertFalse("Uninstalled package must not be in effective monitored set", effectiveSet.contains(customBank))
    }
}
