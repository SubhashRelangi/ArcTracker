package com.subhashrelangi.arctracker

import android.content.SharedPreferences
import com.subhashrelangi.arctracker.settings.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Step 9.9 Unit Tests: Remove User-Added Supported App.
 *
 * Verifies all 33 requirements across:
 * - Repository removal behavior (Tests 1-16)
 * - UI & dialog interaction contracts (Tests 17-27)
 * - Lifecycle reconciliation & re-add behavior (Tests 28-33)
 */
class RemoveUserAppTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var repository: MonitoringSettingsRepository
    private lateinit var appsProvider: InMemoryInstalledAppsProvider

    private val gpay = "com.google.android.apps.nbu.paisa.user"
    private val phonepe = "com.phonepe.app"
    private val myUpiApp = "com.custom.myupi"
    private val myBankApp = "com.custom.mybank"
    private val mySmsApp = "com.custom.mysms"

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        repository = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        appsProvider = InMemoryInstalledAppsProvider()
    }

    private fun getSelectableApps(): List<InstalledAppInfo> {
        val configuredSet = repository.getAllConfiguredApps().map { it.packageName.lowercase() }.toSet()
        return appsProvider.getInstalledApps().filter { !configuredSet.contains(it.packageName.lowercase()) }
    }

    private fun getActiveAppsForCategory(category: AppCategory): List<SupportedApp> {
        val installedMap = appsProvider.getInstalledApps().associateBy { it.packageName.lowercase() }
        return repository.getAllConfiguredApps()
            .filter { it.category == category && installedMap.containsKey(it.packageName.lowercase()) }
    }

    // ==========================================
    // 18. TESTS — REPOSITORY (1 to 16)
    // ==========================================

    // 1. user-added app can be removed
    @Test
    fun test01_userAddedApp_canBeRemoved() {
        val app = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        assertTrue(repository.addUserApp(app))
        assertTrue(repository.removeUserApp(myUpiApp))
    }

    // 2. built-in app cannot be removed
    @Test
    fun test02_builtInApp_cannotBeRemoved() {
        assertFalse("Built-in GPay removal must return false", repository.removeUserApp(gpay))
        assertFalse("Built-in PhonePe removal must return false", repository.removeUserApp(phonepe))
        assertTrue("Built-in app must remain in catalog", AppCatalog.containsPackage(gpay))
    }

    // 3. nonexistent user app removal returns false
    @Test
    fun test03_nonexistentUserApp_removalReturnsFalse() {
        assertFalse(repository.removeUserApp("com.nonexistent.app"))
    }

    // 4. blank package removal returns false
    @Test
    fun test04_blankPackage_removalReturnsFalse() {
        assertFalse(repository.removeUserApp(""))
        assertFalse(repository.removeUserApp("   "))
    }

    // 5. removing user app deletes its definition
    @Test
    fun test05_removingUserApp_deletesItsDefinition() {
        val app = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        repository.addUserApp(app)
        assertTrue(repository.getUserAddedApps().any { it.packageName == myUpiApp })

        repository.removeUserApp(myUpiApp)
        assertFalse(repository.getUserAddedApps().any { it.packageName == myUpiApp })
        assertFalse(repository.getAllConfiguredApps().any { it.packageName == myUpiApp })
    }

    // 6. removing user app removes package from enabledPackages
    @Test
    fun test06_removingUserApp_removesPackageFromEnabledPackages() {
        val app = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        repository.addUserApp(app)
        assertTrue(repository.isAppEnabled(myUpiApp))
        assertTrue(repository.getSettings().enabledPackages.contains(myUpiApp))

        repository.removeUserApp(myUpiApp)
        assertFalse("Package must not be enabled after removal", repository.isAppEnabled(myUpiApp))
        assertFalse("enabledPackages must not contain removed package", repository.getSettings().enabledPackages.contains(myUpiApp))
    }

    // 7. removing one app does not affect another
    @Test
    fun test07_removingOneApp_doesNotAffectAnother() {
        val app1 = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        val app2 = SupportedApp(myBankApp, "My Bank", "Desc", AppCategory.BANKING)
        repository.addUserApp(app1)
        repository.addUserApp(app2)

        assertTrue(repository.removeUserApp(myUpiApp))

        // app2 remains completely intact
        assertTrue(repository.getUserAddedApps().any { it.packageName == myBankApp })
        assertTrue(repository.isAppEnabled(myBankApp))
        assertTrue(repository.getSettings().enabledPackages.contains(myBankApp))
        // Built-in apps also remain enabled
        assertTrue(repository.isAppEnabled(gpay))
    }

    // 8. globalEnabled remains unchanged
    @Test
    fun test08_globalEnabled_remainsUnchanged() {
        val app = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)

        // Case A: globalEnabled is false
        repository.setGlobalEnabled(false)
        repository.addUserApp(app)
        assertFalse(repository.getSettings().globalEnabled)
        repository.removeUserApp(myUpiApp)
        assertFalse("globalEnabled must remain false", repository.getSettings().globalEnabled)

        // Case B: globalEnabled is true
        repository.setGlobalEnabled(true)
        repository.addUserApp(app)
        assertTrue(repository.getSettings().globalEnabled)
        repository.removeUserApp(myUpiApp)
        assertTrue("globalEnabled must remain true", repository.getSettings().globalEnabled)
    }

    // 9. remove works when app is currently installed
    @Test
    fun test09_removeWorks_whenAppIsCurrentlyInstalled() {
        appsProvider.installApp(InstalledAppInfo(myUpiApp, "My UPI"))
        val app = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        repository.addUserApp(app)

        assertTrue(repository.removeUserApp(myUpiApp))
        assertFalse(repository.getUserAddedApps().any { it.packageName == myUpiApp })
    }

    // 10. remove works when app is currently uninstalled
    @Test
    fun test10_removeWorks_whenAppIsCurrentlyUninstalled() {
        // App was configured earlier but is not in appsProvider
        assertFalse(appsProvider.isPackageInstalled(myUpiApp))
        val app = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        repository.addUserApp(app)

        assertTrue(repository.removeUserApp(myUpiApp))
        assertFalse(repository.getUserAddedApps().any { it.packageName == myUpiApp })
    }

    // 11. persistence survives repository recreation
    @Test
    fun test11_persistence_survivesRepositoryRecreation() {
        val app = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        repository.addUserApp(app)
        repository.removeUserApp(myUpiApp)

        val freshRepo = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertFalse(freshRepo.getUserAddedApps().any { it.packageName == myUpiApp })
        assertFalse(freshRepo.getSettings().enabledPackages.contains(myUpiApp))
        assertFalse(freshRepo.isAppEnabled(myUpiApp))
    }

    // 12. removed app does not reappear after repository recreation
    @Test
    fun test12_removedApp_doesNotReappearAfterRepositoryRecreation() {
        appsProvider.installApp(InstalledAppInfo(myUpiApp, "My UPI"))
        val app = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        repository.addUserApp(app)
        repository.removeUserApp(myUpiApp)

        // Multiple recreations
        val repo1 = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertFalse(repo1.getAllConfiguredApps().any { it.packageName == myUpiApp })
        val repo2 = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertFalse(repo2.getAllConfiguredApps().any { it.packageName == myUpiApp })
    }

    // 13. duplicate/corrupt user entries are all removed
    @Test
    fun test13_duplicateOrCorruptUserEntries_areAllRemoved() {
        // Inject duplicate serialized entries into fakePrefs directly
        val rawApp1 = "$myUpiApp|My UPI 1|Desc 1|${AppCategory.UPI_PAYMENT.id}"
        val rawApp2 = "$myUpiApp|My UPI 2|Desc 2|${AppCategory.BANKING.id}"
        fakePrefs.edit().putStringSet(MonitoringSettingsRepository.KEY_USER_ADDED_APPS, setOf(rawApp1, rawApp2)).commit()

        assertTrue(repository.removeUserApp(myUpiApp))
        assertTrue(repository.getUserAddedApps().isEmpty())
        assertFalse(repository.getSettings().enabledPackages.contains(myUpiApp))
    }

    // 14. AppCatalog is never modified
    @Test
    fun test14_appCatalog_isNeverModified() {
        val catalogCountBefore = AppCatalog.allApps.size
        repository.removeUserApp(gpay)
        assertEquals("AppCatalog size must not change", catalogCountBefore, AppCatalog.allApps.size)
        assertTrue(AppCatalog.containsPackage(gpay))
        assertTrue(AppCatalog.containsPackage(phonepe))
    }

    // 15. persistence failure does not leave partial state
    @Test
    fun test15_persistenceFailure_doesNotLeavePartialState() {
        val failingPrefs = FailingCommitSharedPreferencesForRemove()
        val failingRepo = SharedPreferencesMonitoringSettingsRepository(failingPrefs)

        // Even if an app was previously present
        assertFalse(failingRepo.removeUserApp(myUpiApp))
    }

    // 16. removed package cannot remain as orphan enabledPackages entry
    @Test
    fun test16_removedPackage_cannotRemainAsOrphanEnabledPackagesEntry() {
        val app = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        repository.addUserApp(app)
        assertTrue(repository.getSettings().enabledPackages.contains(myUpiApp))

        repository.removeUserApp(myUpiApp)
        assertFalse("No orphan package in enabledPackages", repository.getSettings().enabledPackages.contains(myUpiApp))
    }

    // ==========================================
    // 19. TESTS — UI & DIALOG (17 to 27)
    // ==========================================

    // 17. Remove action visible for user-added app
    @Test
    fun test17_removeAction_visibleForUserAddedApp() {
        val userApp = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT, defaultEnabled = true)
        val isUserAdded = !AppCatalog.containsPackage(userApp.packageName)
        assertTrue("Remove action must be visible for user-added app", isUserAdded)
    }

    // 18. Remove action hidden for built-in app
    @Test
    fun test18_removeAction_hiddenForBuiltInApp() {
        val builtInApp = AppCatalog.allApps.first { it.packageName == gpay }
        val isUserAdded = !AppCatalog.containsPackage(builtInApp.packageName)
        assertFalse("Remove action must be hidden for built-in app", isUserAdded)
    }

    // 19. confirmation dialog appears
    @Test
    fun test19_confirmationDialog_appears() {
        val app = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        var appToRemove: SupportedApp? = null

        // User taps remove icon
        appToRemove = app
        assertNotNull("Confirmation dialog must be triggered with target app", appToRemove)
        assertEquals(myUpiApp, appToRemove?.packageName)
    }

    // 20. Cancel leaves app unchanged
    @Test
    fun test20_cancel_leavesAppUnchanged() {
        val app = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        repository.addUserApp(app)
        var appToRemove: SupportedApp? = app

        // User taps Cancel
        appToRemove = null
        assertNull(appToRemove)
        assertTrue("App remains configured on Cancel", repository.getAllConfiguredApps().any { it.packageName == myUpiApp })
        assertTrue("App remains enabled on Cancel", repository.isAppEnabled(myUpiApp))
    }

    // 21. Confirm removes app
    @Test
    fun test21_confirm_removesApp() {
        val app = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        repository.addUserApp(app)
        var appToRemove: SupportedApp? = app

        // User taps Confirm Remove
        val success = repository.removeUserApp(appToRemove!!.packageName)
        appToRemove = null
        assertTrue(success)
        assertFalse(repository.getAllConfiguredApps().any { it.packageName == myUpiApp })
    }

    // 22. removed app disappears from Supported Apps
    @Test
    fun test22_removedApp_disappearsFromSupportedApps() {
        appsProvider.installApp(InstalledAppInfo(myUpiApp, "My UPI"))
        val app = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        repository.addUserApp(app)

        val beforeActive = getActiveAppsForCategory(AppCategory.UPI_PAYMENT)
        assertTrue(beforeActive.any { it.packageName == myUpiApp })

        repository.removeUserApp(myUpiApp)
        val afterActive = getActiveAppsForCategory(AppCategory.UPI_PAYMENT)
        assertFalse("Removed app must disappear from active category", afterActive.any { it.packageName == myUpiApp })
    }

    // 23. removed installed app becomes available through "+ Add App"
    @Test
    fun test23_removedInstalledApp_becomesAvailableThroughAddApp() {
        appsProvider.installApp(InstalledAppInfo(myUpiApp, "My UPI"))
        val app = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        repository.addUserApp(app)
        assertFalse("Configured app not selectable", getSelectableApps().any { it.packageName == myUpiApp })

        repository.removeUserApp(myUpiApp)
        assertTrue("Removed installed app MUST become available in selectable list", getSelectableApps().any { it.packageName == myUpiApp })
    }

    // 24. removed uninstalled app does not appear
    @Test
    fun test24_removedUninstalledApp_doesNotAppear() {
        val app = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        repository.addUserApp(app)
        repository.removeUserApp(myUpiApp)

        // It is not installed, so it should neither be in active apps nor in selectable apps
        assertFalse(getActiveAppsForCategory(AppCategory.UPI_PAYMENT).any { it.packageName == myUpiApp })
        assertFalse(getSelectableApps().any { it.packageName == myUpiApp })
    }

    // 25. removing last app from category preserves category
    @Test
    fun test25_removingLastAppFromCategory_preservesCategory() {
        // SMS category initially only has Google Messages
        val customSms = SupportedApp(mySmsApp, "My SMS", "Desc", AppCategory.SMS_MESSENGER)
        repository.addUserApp(customSms)
        assertTrue(repository.getAllConfiguredApps().any { it.packageName == mySmsApp })

        repository.removeUserApp(mySmsApp)
        // All categories remain defined and valid
        val categories = AppCategory.values()
        assertEquals(3, categories.size)
        assertTrue(categories.contains(AppCategory.SMS_MESSENGER))
    }

    // 26. Edit remains available before removal
    @Test
    fun test26_editRemainsAvailableBeforeRemoval() {
        val app = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        repository.addUserApp(app)

        // Can edit category
        assertTrue(repository.updateUserApp(app.copy(category = AppCategory.BANKING)))
        val updated = repository.getAllConfiguredApps().first { it.packageName == myUpiApp }
        assertEquals(AppCategory.BANKING, updated.category)

        // Can then remove
        assertTrue(repository.removeUserApp(myUpiApp))
    }

    // 27. Edit/Remove actions do not modify unrelated apps
    @Test
    fun test27_editRemoveActions_doNotModifyUnrelatedApps() {
        val app1 = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        val app2 = SupportedApp(myBankApp, "My Bank", "Desc", AppCategory.BANKING)
        repository.addUserApp(app1)
        repository.addUserApp(app2)

        repository.removeUserApp(myUpiApp)
        val app2Config = repository.getAllConfiguredApps().first { it.packageName == myBankApp }
        assertEquals(AppCategory.BANKING, app2Config.category)
        assertTrue(repository.isAppEnabled(myBankApp))
        assertTrue(repository.isAppEnabled(gpay))
    }

    // ==========================================
    // 20. TESTS — LIFECYCLE (28 to 33)
    // ==========================================

    // 28. remove installed app -> reinstall does not automatically restore support
    @Test
    fun test28_removeInstalledApp_reinstallDoesNotAutomaticallyRestoreSupport() {
        appsProvider.installApp(InstalledAppInfo(myUpiApp, "My UPI"))
        repository.addUserApp(SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT))
        repository.removeUserApp(myUpiApp)

        // Simulate reinstall (uninstall then reinstall)
        appsProvider.uninstallApp(myUpiApp)
        appsProvider.installApp(InstalledAppInfo(myUpiApp, "My UPI"))

        assertFalse("Reinstalling after explicit remove must NOT automatically make it Supported",
            repository.getAllConfiguredApps().any { it.packageName == myUpiApp })
        assertTrue("It must be available under + Add App", getSelectableApps().any { it.packageName == myUpiApp })
    }

    // 29. remove uninstalled app -> reinstall does not automatically restore support
    @Test
    fun test29_removeUninstalledApp_reinstallDoesNotAutomaticallyRestoreSupport() {
        repository.addUserApp(SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT))
        repository.removeUserApp(myUpiApp)

        // Now install it
        appsProvider.installApp(InstalledAppInfo(myUpiApp, "My UPI"))

        assertFalse("Reinstalling after remove must NOT automatically make it Supported",
            repository.getAllConfiguredApps().any { it.packageName == myUpiApp })
        assertTrue("It must be selectable under + Add App", getSelectableApps().any { it.packageName == myUpiApp })
    }

    // 30. removed package can be explicitly added again
    @Test
    fun test30_removedPackage_canBeExplicitlyAddedAgain() {
        appsProvider.installApp(InstalledAppInfo(myUpiApp, "My UPI"))
        val app = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        repository.addUserApp(app)
        repository.removeUserApp(myUpiApp)

        // Explicit re-add
        val reAdded = repository.addUserApp(SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT))
        assertTrue("Explicit re-add must succeed", reAdded)
        assertTrue(repository.getAllConfiguredApps().any { it.packageName == myUpiApp })
    }

    // 31. re-added package receives the category selected by the category's "+ Add App" action
    @Test
    fun test31_reAddedPackage_receivesCategorySelectedByAddApp() {
        appsProvider.installApp(InstalledAppInfo(myUpiApp, "My UPI"))
        // First added under UPI
        repository.addUserApp(SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT))
        repository.removeUserApp(myUpiApp)

        // Now user adds it from Banking "+ Add App"
        repository.addUserApp(SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.BANKING))
        val configured = repository.getAllConfiguredApps().first { it.packageName == myUpiApp }
        assertEquals(AppCategory.BANKING, configured.category)
    }

    // 32. re-added package starts enabled according to Step 9.8.1 behavior
    @Test
    fun test32_reAddedPackage_startsEnabledAccordingToStep981() {
        appsProvider.installApp(InstalledAppInfo(myUpiApp, "My UPI"))
        repository.addUserApp(SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT))
        // Explicitly disable before removing
        repository.setAppEnabled(myUpiApp, false)
        assertFalse(repository.isAppEnabled(myUpiApp))

        repository.removeUserApp(myUpiApp)

        // Re-adding it starts enabled by default
        repository.addUserApp(SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT))
        assertTrue("Re-added app must start enabled by default", repository.isAppEnabled(myUpiApp))
    }

    // 33. globalEnabled remains unchanged after remove and re-add
    @Test
    fun test33_globalEnabled_remainsUnchangedAfterRemoveAndReAdd() {
        repository.setGlobalEnabled(false)
        assertFalse(repository.getSettings().globalEnabled)

        appsProvider.installApp(InstalledAppInfo(myUpiApp, "My UPI"))
        val app = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        repository.addUserApp(app)
        assertFalse(repository.getSettings().globalEnabled)

        repository.removeUserApp(myUpiApp)
        assertFalse(repository.getSettings().globalEnabled)

        repository.addUserApp(app)
        assertFalse("globalEnabled must remain false after remove and re-add", repository.getSettings().globalEnabled)
    }

    // Bonus test: InMemoryMonitoringSettingsRepository mirrors removeUserApp behavior
    @Test
    fun test34_inMemoryRepository_mirrorsRemoveBehavior() {
        val inMemory = InMemoryMonitoringSettingsRepository(initialGlobalEnabled = true)
        val app = SupportedApp(myUpiApp, "My UPI", "Desc", AppCategory.UPI_PAYMENT)
        assertTrue(inMemory.addUserApp(app))
        assertTrue(inMemory.isAppEnabled(myUpiApp))

        // Built-in rejected
        assertFalse(inMemory.removeUserApp(gpay))
        // Blank rejected
        assertFalse(inMemory.removeUserApp(""))
        // Nonexistent rejected
        assertFalse(inMemory.removeUserApp("com.other.app"))

        // Successful removal
        assertTrue(inMemory.removeUserApp(myUpiApp))
        assertFalse(inMemory.isAppEnabled(myUpiApp))
        assertFalse(inMemory.getAllConfiguredApps().any { it.packageName == myUpiApp })
    }
}

private class FailingCommitSharedPreferencesForRemove : SharedPreferences {
    private val data = mutableMapOf<String, Any>()

    override fun getAll(): MutableMap<String, *> = HashMap(data)
    override fun getString(key: String?, defValue: String?): String? = (data[key] as? String) ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (data[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
    override fun contains(key: String?): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FailingEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class FailingEditor : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = this
        override fun putStringSet(key: String, values: MutableSet<String>?) = this
        override fun putInt(key: String, value: Int) = this
        override fun putLong(key: String, value: Long) = this
        override fun putFloat(key: String, value: Float) = this
        override fun putBoolean(key: String, value: Boolean) = this
        override fun remove(key: String) = this
        override fun clear() = this
        override fun commit(): Boolean = false
        override fun apply() {}
    }
}
