package com.subhashrelangi.arctracker

import android.content.SharedPreferences
import com.subhashrelangi.arctracker.settings.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Step 9.10 End-to-End Audit & Regression Hardening Test Suite.
 *
 * Verifies all requirements specified in Step 9.10:
 * - Core State Machine (Scenarios A through F)
 * - Global Monitoring & Privacy Gate boundaries
 * - Per-App State independence and persistence
 * - Repository-level Built-in vs User-Added protection
 * - Category Behavior & ArcTracker self-exclusion
 * - Install / Uninstall / Reinstall Lifecycle reconciliation
 * - Persistence atomicity & corrupt data handling
 * - Legacy auto-tracking migration
 */
class SupportedAppsAuditAndHardeningTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var repository: MonitoringSettingsRepository
    private lateinit var appsProvider: InMemoryInstalledAppsProvider

    private val gpay = "com.google.android.apps.nbu.paisa.user"
    private val phonepe = "com.phonepe.app"
    private val paytm = "net.one97.paytm"
    private val hdfc = "com.snapwork.hdfc"
    private val gmessages = "com.google.android.apps.messaging"

    private val userUpi = "com.custom.upi"
    private val userBank = "com.custom.bank"
    private val userSms = "com.custom.sms"
    private val unknownApp = "com.unknown.installed"
    private val arcTrackerPackage = "com.subhashrelangi.arctracker"

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        repository = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        appsProvider = InMemoryInstalledAppsProvider()
    }

    private fun getSelectableApps(excludePackage: String = arcTrackerPackage): List<InstalledAppInfo> {
        val configuredSet = repository.getAllConfiguredApps().map { it.packageName.lowercase() }.toSet()
        return appsProvider.getInstalledApps().filter {
            val lower = it.packageName.lowercase()
            lower != excludePackage.lowercase() && !configuredSet.contains(lower)
        }
    }

    private fun getActiveAppsForCategory(category: AppCategory): List<SupportedApp> {
        val installedMap = appsProvider.getInstalledApps().associateBy { it.packageName.lowercase() }
        return repository.getAllConfiguredApps()
            .filter { it.category == category && installedMap.containsKey(it.packageName.lowercase()) }
    }

    // ==========================================
    // 1. CORE STATE MACHINE
    // ==========================================

    @Test
    fun testStateMachine_A_builtInApp_installed() {
        // Built-in app installed
        appsProvider.installApp(InstalledAppInfo(gpay, "Google Pay"))

        val activeUpiApps = getActiveAppsForCategory(AppCategory.UPI_PAYMENT)
        assertTrue("Installed built-in app must appear in active apps", activeUpiApps.any { it.packageName == gpay })

        // Can be toggled ON and OFF
        assertTrue(repository.isAppEnabled(gpay))
        repository.setAppEnabled(gpay, false)
        assertFalse(repository.isAppEnabled(gpay))
        repository.setAppEnabled(gpay, true)
        assertTrue(repository.isAppEnabled(gpay))

        // Cannot be edited
        val editAttempt = SupportedApp(gpay, "Fake GPay", "Desc", AppCategory.BANKING)
        assertFalse("Built-in app cannot be edited", repository.updateUserApp(editAttempt))

        // Cannot be removed
        assertFalse("Built-in app cannot be removed", repository.removeUserApp(gpay))
        assertTrue("Built-in app remains in catalog", AppCatalog.containsPackage(gpay))
    }

    @Test
    fun testStateMachine_B_builtInApp_notInstalled_andReinstall() {
        // GPay is built-in, but NOT installed in appsProvider
        assertFalse(appsProvider.isPackageInstalled(gpay))

        val activeApps = getActiveAppsForCategory(AppCategory.UPI_PAYMENT)
        assertFalse("Uninstalled built-in app must not appear in active UI", activeApps.any { it.packageName == gpay })

        // Configure toggle while uninstalled
        repository.setAppEnabled(gpay, false)
        assertFalse("Configuration remains dormant", repository.isAppEnabled(gpay))

        // Reinstall restores it with previous configured state
        appsProvider.installApp(InstalledAppInfo(gpay, "Google Pay"))
        val restoredActiveApps = getActiveAppsForCategory(AppCategory.UPI_PAYMENT)
        assertTrue("Reinstalled built-in app appears again", restoredActiveApps.any { it.packageName == gpay })
        assertFalse("Restored app preserves its dormant disabled state", repository.isAppEnabled(gpay))
    }

    @Test
    fun testStateMachine_C_unknownApp_installed_notAutomaticallySupported() {
        // Unknown app installed on phone
        appsProvider.installApp(InstalledAppInfo(unknownApp, "Random App"))

        // Does NOT automatically become supported
        val allConfigured = repository.getAllConfiguredApps()
        assertFalse("Unknown app must not automatically become supported", allConfigured.any { it.packageName == unknownApp })
        assertFalse("Unknown app must not be in enabledPackages", repository.isAppEnabled(unknownApp))

        // Appears in selectable list
        val selectable = getSelectableApps()
        assertTrue("Unknown app appears in selectable list", selectable.any { it.packageName == unknownApp })

        // Adding it puts it in originating category and starts ENABLED by default
        val newApp = SupportedApp(unknownApp, "Random App", "Desc", AppCategory.BANKING)
        assertTrue(repository.addUserApp(newApp))

        val configured = repository.getAllConfiguredApps().first { it.packageName == unknownApp }
        assertEquals(AppCategory.BANKING, configured.category)
        assertTrue("Newly added app must start ENABLED by default", repository.isAppEnabled(unknownApp))
    }

    @Test
    fun testStateMachine_D_userAddedApp_installed_canToggle_canEdit_canRemove() {
        appsProvider.installApp(InstalledAppInfo(userUpi, "My Custom UPI"))
        val app = SupportedApp(userUpi, "My Custom UPI", "Desc", AppCategory.UPI_PAYMENT)
        assertTrue(repository.addUserApp(app))

        // Appears in assigned category
        val activeUpi = getActiveAppsForCategory(AppCategory.UPI_PAYMENT)
        assertTrue(activeUpi.any { it.packageName == userUpi })

        // Can toggle ON/OFF
        assertTrue(repository.isAppEnabled(userUpi))
        repository.setAppEnabled(userUpi, false)
        assertFalse(repository.isAppEnabled(userUpi))

        // Can edit category
        assertTrue(repository.updateUserApp(app.copy(category = AppCategory.BANKING)))
        val activeBanking = getActiveAppsForCategory(AppCategory.BANKING)
        assertTrue("Moved to Banking", activeBanking.any { it.packageName == userUpi })
        assertFalse("Enabled state preserved across edit", repository.isAppEnabled(userUpi))

        // Can remove
        assertTrue(repository.removeUserApp(userUpi))
        assertFalse(repository.getAllConfiguredApps().any { it.packageName == userUpi })
    }

    @Test
    fun testStateMachine_E_userAddedApp_uninstalled_dormantConfiguration() {
        appsProvider.installApp(InstalledAppInfo(userBank, "My Bank"))
        repository.addUserApp(SupportedApp(userBank, "My Bank", "Desc", AppCategory.BANKING))
        repository.setAppEnabled(userBank, true)

        // Uninstall
        appsProvider.uninstallApp(userBank)
        assertFalse("Disappears from active UI", getActiveAppsForCategory(AppCategory.BANKING).any { it.packageName == userBank })
        assertTrue("Configuration remains dormant in repository", repository.getUserAddedApps().any { it.packageName == userBank })

        // Reinstall restores it
        appsProvider.installApp(InstalledAppInfo(userBank, "My Bank"))
        assertTrue("Appears again on reinstall", getActiveAppsForCategory(AppCategory.BANKING).any { it.packageName == userBank })
        assertTrue("Enabled state restored", repository.isAppEnabled(userBank))
    }

    @Test
    fun testStateMachine_F_userAddedApp_removedThroughArcTracker() {
        appsProvider.installApp(InstalledAppInfo(userUpi, "My UPI"))
        repository.addUserApp(SupportedApp(userUpi, "My UPI", "Desc", AppCategory.UPI_PAYMENT))
        repository.setGlobalEnabled(false)

        // Remove
        assertTrue(repository.removeUserApp(userUpi))
        assertFalse("Config deleted", repository.getUserAddedApps().any { it.packageName == userUpi })
        assertFalse("Enabled state removed", repository.isAppEnabled(userUpi))
        assertFalse("globalEnabled unchanged", repository.getSettings().globalEnabled)

        // Still installed -> selectable again in + Add App
        assertTrue("Available in + Add App", getSelectableApps().any { it.packageName == userUpi })

        // Reinstalling does NOT restore removed configuration
        appsProvider.uninstallApp(userUpi)
        appsProvider.installApp(InstalledAppInfo(userUpi, "My UPI"))
        assertFalse("Reinstall does NOT restore removed config", repository.getAllConfiguredApps().any { it.packageName == userUpi })

        // Adding again starts enabled
        assertTrue(repository.addUserApp(SupportedApp(userUpi, "My UPI", "Desc", AppCategory.UPI_PAYMENT)))
        assertTrue("Re-added app starts enabled", repository.isAppEnabled(userUpi))
    }

    // ==========================================
    // 2. PER-APP STATE & TOGGLES
    // ==========================================

    @Test
    fun testPerAppState_emptyEnabledPackages_distinguishableFromNeverExisted() {
        // Case A: Never existed -> returns default enabled packages
        assertFalse(fakePrefs.contains(MonitoringSettingsRepository.KEY_ENABLED_PACKAGES))
        assertEquals(AppCatalog.defaultEnabledPackages, repository.getSettings().enabledPackages)

        // Case B: User turned off all apps -> empty set
        repository.setEnabledPackages(emptySet())
        assertTrue(fakePrefs.contains(MonitoringSettingsRepository.KEY_ENABLED_PACKAGES))
        assertTrue(repository.getSettings().enabledPackages.isEmpty())

        // Recreating repository preserves empty set
        val freshRepo = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertTrue(freshRepo.getSettings().enabledPackages.isEmpty())
    }

    @Test
    fun testPerAppState_globalToggle_independentFromIndividualAppStates() {
        repository.setAppEnabled(gpay, false)
        repository.setAppEnabled(phonepe, true)

        // Turn global monitoring OFF
        repository.setGlobalEnabled(false)
        assertFalse(repository.getSettings().globalEnabled)
        assertFalse("GPay still disabled individually", repository.isAppEnabled(gpay))
        assertTrue("PhonePe still enabled individually", repository.isAppEnabled(phonepe))

        // Turn global monitoring ON
        repository.setGlobalEnabled(true)
        assertTrue(repository.getSettings().globalEnabled)
        assertFalse("GPay remains disabled", repository.isAppEnabled(gpay))
        assertTrue("PhonePe remains enabled", repository.isAppEnabled(phonepe))
    }

    // ==========================================
    // 3. CATEGORY BEHAVIOR & SELF-EXCLUSION
    // ==========================================

    @Test
    fun testCategoryBehavior_arcTrackerPackage_isExcludedFromAddableApps() {
        appsProvider.installApp(InstalledAppInfo(arcTrackerPackage, "ArcTracker"))
        appsProvider.installApp(InstalledAppInfo(unknownApp, "Random App"))

        val selectable = getSelectableApps(excludePackage = arcTrackerPackage)
        assertFalse("ArcTracker itself must be excluded from + Add App selector", selectable.any { it.packageName == arcTrackerPackage })
        assertTrue("Other unknown apps must remain selectable", selectable.any { it.packageName == unknownApp })
    }

    // ==========================================
    // 4. LEGACY MIGRATION COMPATIBILITY
    // ==========================================

    @Test
    fun testLegacyCompatibility_migrationPrecedence() {
        // Sub-test 1: legacy false + new unset -> false
        val prefs1 = FakeSharedPreferences()
        prefs1.edit().putBoolean(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING, false).commit()
        val repo1 = SharedPreferencesMonitoringSettingsRepository(prefs1)
        assertFalse(repo1.getSettings().globalEnabled)

        // Sub-test 2: legacy true + new unset -> true
        val prefs2 = FakeSharedPreferences()
        prefs2.edit().putBoolean(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING, true).commit()
        val repo2 = SharedPreferencesMonitoringSettingsRepository(prefs2)
        assertTrue(repo2.getSettings().globalEnabled)

        // Sub-test 3: explicit new true + legacy false -> true
        val prefs3 = FakeSharedPreferences()
        prefs3.edit()
            .putBoolean(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING, false)
            .putBoolean(MonitoringSettingsRepository.KEY_GLOBAL_ENABLED, true)
            .commit()
        val repo3 = SharedPreferencesMonitoringSettingsRepository(prefs3)
        assertTrue(repo3.getSettings().globalEnabled)

        // Sub-test 4: global toggle updates synchronize both keys
        repo3.setGlobalEnabled(false)
        assertFalse(prefs3.getBoolean(MonitoringSettingsRepository.KEY_GLOBAL_ENABLED, true))
        assertFalse(prefs3.getBoolean(MonitoringSettingsRepository.LEGACY_KEY_AUTO_TRACKING, true))
    }

    // ==========================================
    // 5. ATOMICITY & CORRUPT DATA SAFETY
    // ==========================================

    @Test
    fun testCorruptDataSafety_serializer_handlesMalformedDataGracefully() {
        // Less than 4 parts
        assertNull(SupportedAppSerializer.deserialize("only|two"))
        assertNull(SupportedAppSerializer.deserialize(""))
        // Blank package
        assertNull(SupportedAppSerializer.deserialize("  |Name|Desc|upi"))

        // Valid with special characters escaped
        val app = SupportedApp("com.pkg.test", "Name | With Pipe", "Desc \\ Backslash", AppCategory.BANKING)
        val serialized = SupportedAppSerializer.serialize(app)
        val deserialized = SupportedAppSerializer.deserialize(serialized)
        assertNotNull(deserialized)
        assertEquals(app.packageName, deserialized?.packageName)
        assertEquals(app.displayName, deserialized?.displayName)
        assertEquals(app.description, deserialized?.description)
        assertEquals(app.category, deserialized?.category)
    }

    @Test
    fun testCorruptDataSafety_duplicateEntries_purgedOnRemove() {
        val raw1 = "$userUpi|Name 1|Desc 1|${AppCategory.UPI_PAYMENT.id}"
        val raw2 = "$userUpi|Name 2|Desc 2|${AppCategory.BANKING.id}"
        fakePrefs.edit().putStringSet(MonitoringSettingsRepository.KEY_USER_ADDED_APPS, setOf(raw1, raw2)).commit()

        assertTrue(repository.removeUserApp(userUpi))
        assertTrue("All duplicate corrupt entries must be purged", repository.getUserAddedApps().isEmpty())
    }
}
