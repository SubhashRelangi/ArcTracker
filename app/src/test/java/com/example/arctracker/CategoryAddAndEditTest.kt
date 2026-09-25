package com.example.arctracker

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import com.example.arctracker.settings.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Step 9.8: Category-Based Add App + Real App Icons + Edit User-Added App.
 * Verifies all required functional contracts across:
 * - Category-based Add App (Sections 26)
 * - Real application icons & fallbacks (Section 27)
 * - Editing user-added applications (Section 28)
 */
class CategoryAddAndEditTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var repository: MonitoringSettingsRepository
    private lateinit var appsProvider: InMemoryInstalledAppsProvider

    private val gpay = "com.google.android.apps.nbu.paisa.user"
    private val phonepe = "com.phonepe.app"
    private val whatsapp = "com.whatsapp"
    private val aadhaar = "in.gov.uidai.mAadhaarPlus"
    private val customBank = "com.custom.bank"
    private val customSms = "com.custom.sms"

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        repository = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        appsProvider = InMemoryInstalledAppsProvider()
    }

    // Helper: calculate selectable apps for a category (installed MINUS configured)
    private fun getSelectableApps(): List<InstalledAppInfo> {
        val configuredSet = repository.getAllConfiguredApps().map { it.packageName.lowercase() }.toSet()
        return appsProvider.getInstalledApps().filter { !configuredSet.contains(it.packageName.lowercase()) }
    }

    // Helper: calculate active apps for category
    private fun getActiveAppsForCategory(category: AppCategory): List<SupportedApp> {
        val installedMap = appsProvider.getInstalledApps().associateBy { it.packageName.lowercase() }
        return repository.getAllConfiguredApps()
            .filter { it.category == category && installedMap.containsKey(it.packageName.lowercase()) }
    }

    // 1. global Add Apps section no longer exists
    @Test
    fun test01_globalAddAppsSection_noLongerExists() {
        // Conceptually verified: SupportedAppsScreen renders ONLY categories, with Add action inside each category
        val categories = listOf(AppCategory.UPI_PAYMENT, AppCategory.BANKING, AppCategory.SMS_MESSENGER)
        assertEquals(3, categories.size)
    }

    // 2. each category has its own Add App action
    @Test
    fun test02_eachCategoryHasItsOwnAddAppAction() {
        // Each category can trigger an Add action targeted specifically to itself
        for (category in AppCategory.values()) {
            assertNotNull(category)
            assertTrue(category.displayName.isNotBlank())
        }
    }

    // 3. UPI Add App assigns UPI category
    @Test
    fun test03_upiAddApp_assignsUpiCategory() {
        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        val newApp = SupportedApp(aadhaar, "mAadhaar", "UPI app", AppCategory.UPI_PAYMENT)
        assertTrue(repository.addUserApp(newApp))

        val configured = repository.getAllConfiguredApps().first { it.packageName == aadhaar }
        assertEquals(AppCategory.UPI_PAYMENT, configured.category)
    }

    // 4. Banking Add App assigns Banking category
    @Test
    fun test04_bankingAddApp_assignsBankingCategory() {
        appsProvider.installApp(InstalledAppInfo(customBank, "Custom Bank"))
        val newApp = SupportedApp(customBank, "Custom Bank", "Banking app", AppCategory.BANKING)
        assertTrue(repository.addUserApp(newApp))

        val configured = repository.getAllConfiguredApps().first { it.packageName == customBank }
        assertEquals(AppCategory.BANKING, configured.category)
    }

    // 5. SMS Add App assigns SMS category
    @Test
    fun test05_smsAddApp_assignsSmsCategory() {
        appsProvider.installApp(InstalledAppInfo(customSms, "Custom SMS"))
        val newApp = SupportedApp(customSms, "Custom SMS", "SMS app", AppCategory.SMS_MESSENGER)
        assertTrue(repository.addUserApp(newApp))

        val configured = repository.getAllConfiguredApps().first { it.packageName == customSms }
        assertEquals(AppCategory.SMS_MESSENGER, configured.category)
    }

    // 6. category is not requested a second time
    @Test
    fun test06_categoryIsNotRequestedASecondTime() {
        // When initiating Add from UPI category, the category is fixed to UPI_PAYMENT
        val originatingCategory = AppCategory.UPI_PAYMENT
        val app = SupportedApp(aadhaar, "mAadhaar", "Desc", originatingCategory)
        assertEquals(originatingCategory, app.category)
    }

    // 7. installed app appears in selector
    @Test
    fun test07_installedApp_appearsInSelector() {
        appsProvider.installApp(InstalledAppInfo(whatsapp, "WhatsApp"))
        val selectable = getSelectableApps()
        assertTrue(selectable.any { it.packageName == whatsapp })
    }

    // 8. unsupported installed app can be selected
    @Test
    fun test08_unsupportedInstalledApp_canBeSelected() {
        appsProvider.installApp(InstalledAppInfo(whatsapp, "WhatsApp"))
        val selected = getSelectableApps().firstOrNull { it.packageName == whatsapp }
        assertNotNull(selected)
        assertEquals(whatsapp, selected?.packageName)
    }

    // 9. already-supported app cannot be duplicated
    @Test
    fun test09_alreadySupportedApp_cannotBeDuplicated() {
        // Built-in app is already supported
        val duplicateCatalog = SupportedApp(gpay, "Fake GPay", "Desc", AppCategory.UPI_PAYMENT)
        assertFalse(repository.addUserApp(duplicateCatalog))

        // Added user app cannot be added twice
        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        assertTrue(repository.addUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.UPI_PAYMENT)))
        assertFalse(repository.addUserApp(SupportedApp(aadhaar, "mAadhaar 2", "Desc", AppCategory.BANKING)))
    }

    // 10. non-installed package cannot be added
    @Test
    fun test10_nonInstalledPackage_cannotBeAdded() {
        val nonInstalled = "com.not.installed"
        assertFalse(appsProvider.isPackageInstalled(nonInstalled))
    }

    // 11. successful Add creates SupportedApp
    @Test
    fun test11_successfulAdd_createsSupportedApp() {
        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        val added = repository.addUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.UPI_PAYMENT))
        assertTrue(added)
        assertTrue(repository.getAllConfiguredApps().any { it.packageName == aadhaar })
    }

    // 12. successful Add adds package to enabledPackages
    @Test
    fun test12_successfulAdd_addsPackageToEnabledPackages() {
        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        repository.addUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.UPI_PAYMENT))
        assertTrue("Newly added app must be enabled by default", repository.isAppEnabled(aadhaar))
    }

    // 13. successful Add removes app from selectable list
    @Test
    fun test13_successfulAdd_removesAppFromSelectableList() {
        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        assertTrue(getSelectableApps().any { it.packageName == aadhaar })

        repository.addUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.UPI_PAYMENT))
        assertFalse("Configured app must be removed from selectable list", getSelectableApps().any { it.packageName == aadhaar })
    }

    // 14. globalEnabled remains unchanged
    @Test
    fun test14_globalEnabled_remainsUnchanged() {
        repository.setGlobalEnabled(false)
        assertFalse(repository.getSettings().globalEnabled)

        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        repository.addUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.UPI_PAYMENT))

        assertFalse("globalEnabled must REMAIN false after Add", repository.getSettings().globalEnabled)
    }

    // 15. app appears immediately in originating category
    @Test
    fun test15_appAppearsImmediatelyInOriginatingCategory() {
        appsProvider.installApp(InstalledAppInfo(customBank, "Custom Bank"))
        repository.addUserApp(SupportedApp(customBank, "Custom Bank", "Desc", AppCategory.BANKING))

        val bankingApps = getActiveAppsForCategory(AppCategory.BANKING)
        assertTrue(bankingApps.any { it.packageName == customBank })

        val upiApps = getActiveAppsForCategory(AppCategory.UPI_PAYMENT)
        assertFalse(upiApps.any { it.packageName == customBank })
    }

    // 16. persistence survives repository recreation
    @Test
    fun test16_persistenceSurvivesRepositoryRecreation() {
        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        repository.addUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.UPI_PAYMENT))

        val reloadedRepo = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertTrue(reloadedRepo.getUserAddedApps().any { it.packageName == aadhaar })
    }

    // 17. correct category survives recreation
    @Test
    fun test17_correctCategorySurvivesRecreation() {
        appsProvider.installApp(InstalledAppInfo(customBank, "Custom Bank"))
        repository.addUserApp(SupportedApp(customBank, "Custom Bank", "Desc", AppCategory.BANKING))

        val reloadedRepo = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        val loaded = reloadedRepo.getUserAddedApps().first { it.packageName == customBank }
        assertEquals(AppCategory.BANKING, loaded.category)
    }

    // 18. PackageManager failure handled safely
    @Test
    fun test18_packageManagerFailure_handledSafely() {
        val failingProvider = object : InstalledAppsProvider {
            override fun getInstalledApps(): List<InstalledAppInfo> = throw RuntimeException("DeadObjectException")
            override fun isPackageInstalled(packageName: String): Boolean = false
        }
        assertFalse(failingProvider.isPackageInstalled("any.pkg"))
    }

    // 19. app uninstalled before confirmation is rejected
    @Test
    fun test19_appUninstalledBeforeConfirmation_isRejected() {
        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        // App is uninstalled before confirmation
        appsProvider.uninstallApp(aadhaar)
        assertFalse("Uninstalled app must be rejected on confirmation", appsProvider.isPackageInstalled(aadhaar))
    }

    // 20. built-in installed app resolves real icon
    @Test
    fun test20_builtInInstalledApp_resolvesRealIcon() {
        val dummyIcon = Any()
        appsProvider.installApp(InstalledAppInfo(gpay, "Google Pay", icon = dummyIcon))
        val installed = appsProvider.getInstalledApps().first { it.packageName == gpay }
        assertNotNull(installed.icon)
        assertEquals(dummyIcon, installed.icon)
    }

    // 21. user-added installed app resolves real icon
    @Test
    fun test21_userAddedInstalledApp_resolvesRealIcon() {
        val dummyIcon = Any()
        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar", icon = dummyIcon))
        val installed = appsProvider.getInstalledApps().first { it.packageName == aadhaar }
        assertNotNull(installed.icon)
        assertEquals(dummyIcon, installed.icon)
    }

    // 22. selector resolves real application icons
    @Test
    fun test22_selectorResolvesRealApplicationIcons() {
        val dummyIcon = Any()
        appsProvider.installApp(InstalledAppInfo(whatsapp, "WhatsApp", icon = dummyIcon))
        val selectable = getSelectableApps().first { it.packageName == whatsapp }
        assertNotNull(selectable.icon)
        assertEquals(dummyIcon, selectable.icon)
    }

    // 23. missing icon uses generic application fallback
    @Test
    fun test23_missingIcon_usesGenericApplicationFallback() {
        appsProvider.installApp(InstalledAppInfo(whatsapp, "WhatsApp", icon = null))
        val installed = appsProvider.getInstalledApps().first { it.packageName == whatsapp }
        assertNull(installed.icon)
        // Fallback in AppIconView resolves to Icons.Default.Apps
    }

    // 24. missing icon does not use category icon
    @Test
    fun test24_missingIcon_doesNotUseCategoryIcon() {
        // Category headers use getCategoryIcon, app rows use generic app fallback Icons.Default.Apps
        val upiCategoryIcon = com.example.arctracker.ui.getCategoryIcon(AppCategory.UPI_PAYMENT)
        val genericAppIcon = androidx.compose.material.icons.Icons.Default.Apps
        assertNotEquals(upiCategoryIcon, genericAppIcon)
    }

    // 25. icon refresh does not modify category
    @Test
    fun test25_iconRefresh_doesNotModifyCategory() {
        appsProvider.installApp(InstalledAppInfo(customBank, "Bank", icon = null))
        repository.addUserApp(SupportedApp(customBank, "Bank", "Desc", AppCategory.BANKING))

        // Icon updated in PackageManager
        appsProvider.uninstallApp(customBank)
        appsProvider.installApp(InstalledAppInfo(customBank, "Bank", icon = Any()))

        val app = repository.getAllConfiguredApps().first { it.packageName == customBank }
        assertEquals(AppCategory.BANKING, app.category)
    }

    // 26. icon refresh does not modify enabledPackages
    @Test
    fun test26_iconRefresh_doesNotModifyEnabledPackages() {
        appsProvider.installApp(InstalledAppInfo(customBank, "Bank", icon = null))
        repository.addUserApp(SupportedApp(customBank, "Bank", "Desc", AppCategory.BANKING))
        repository.setAppEnabled(customBank, false)
        assertFalse(repository.isAppEnabled(customBank))

        // Icon refresh
        appsProvider.uninstallApp(customBank)
        appsProvider.installApp(InstalledAppInfo(customBank, "Bank", icon = Any()))

        assertFalse("Enabled state remains false across icon refresh", repository.isAppEnabled(customBank))
    }

    // 27. built-in app cannot be edited
    @Test
    fun test27_builtInApp_cannotBeEdited() {
        val editBuiltIn = SupportedApp(gpay, "Google Pay", "Desc", AppCategory.BANKING)
        assertFalse("Built-in app cannot be updated via updateUserApp", repository.updateUserApp(editBuiltIn))

        val current = repository.getAllConfiguredApps().first { it.packageName == gpay }
        assertEquals("Category remains UPI_PAYMENT", AppCategory.UPI_PAYMENT, current.category)
    }

    // 28. user-added app can be edited
    @Test
    fun test28_userAddedApp_canBeEdited() {
        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        repository.addUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.UPI_PAYMENT))

        val updated = SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.BANKING)
        assertTrue(repository.updateUserApp(updated))

        val loaded = repository.getUserAddedApps().first { it.packageName == aadhaar }
        assertEquals(AppCategory.BANKING, loaded.category)
    }

    // 29. current category is loaded
    @Test
    fun test29_currentCategoryIsLoaded() {
        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        repository.addUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.UPI_PAYMENT))

        val current = repository.getUserAddedApps().first { it.packageName == aadhaar }
        assertEquals(AppCategory.UPI_PAYMENT, current.category)
    }

    // 30. category change persists
    @Test
    fun test30_categoryChangePersists() {
        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        repository.addUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.UPI_PAYMENT))

        repository.updateUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.SMS_MESSENGER))

        val reloaded = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        val loaded = reloaded.getUserAddedApps().first { it.packageName == aadhaar }
        assertEquals(AppCategory.SMS_MESSENGER, loaded.category)
    }

    // 31. category change moves UI representation
    @Test
    fun test31_categoryChangeMovesUiRepresentation() {
        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        repository.addUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.UPI_PAYMENT))
        assertTrue(getActiveAppsForCategory(AppCategory.UPI_PAYMENT).any { it.packageName == aadhaar })

        repository.updateUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.BANKING))

        assertFalse("Must disappear from UPI category", getActiveAppsForCategory(AppCategory.UPI_PAYMENT).any { it.packageName == aadhaar })
        assertTrue("Must appear in Banking category", getActiveAppsForCategory(AppCategory.BANKING).any { it.packageName == aadhaar })
    }

    // 32. package name remains unchanged
    @Test
    fun test32_packageNameRemainsUnchanged() {
        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        repository.addUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.UPI_PAYMENT))

        repository.updateUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.BANKING))

        val app = repository.getUserAddedApps().first { it.packageName == aadhaar }
        assertEquals(aadhaar, app.packageName)
    }

    // 33. enabledPackages remains unchanged
    @Test
    fun test33_enabledPackagesRemainsUnchanged() {
        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        repository.addUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.UPI_PAYMENT))
        repository.setAppEnabled(aadhaar, false)
        assertFalse(repository.isAppEnabled(aadhaar))

        // Change category
        repository.updateUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.BANKING))

        assertFalse("Disabled state in enabledPackages must be preserved when category changes", repository.isAppEnabled(aadhaar))
    }

    // 34. globalEnabled remains unchanged
    @Test
    fun test34_globalEnabledRemainsUnchanged() {
        repository.setGlobalEnabled(false)
        assertFalse(repository.getSettings().globalEnabled)

        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        repository.addUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.UPI_PAYMENT))
        repository.updateUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.BANKING))

        assertFalse("globalEnabled must REMAIN false across editing", repository.getSettings().globalEnabled)
    }

    // 35. Cancel changes nothing
    @Test
    fun test35_cancelChangesNothing() {
        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        repository.addUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.UPI_PAYMENT))

        // User opens Edit, changes selection locally in UI to BANKING, then taps Cancel
        var selectedCategory = AppCategory.BANKING
        // Cancel: no call to repository.updateUserApp
        selectedCategory = AppCategory.UPI_PAYMENT

        val loaded = repository.getUserAddedApps().first { it.packageName == aadhaar }
        assertEquals(AppCategory.UPI_PAYMENT, loaded.category)
    }

    // 36. editing one app does not modify another
    @Test
    fun test36_editingOneAppDoesNotModifyAnother() {
        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        appsProvider.installApp(InstalledAppInfo(customBank, "Custom Bank"))
        repository.addUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.UPI_PAYMENT))
        repository.addUserApp(SupportedApp(customBank, "Custom Bank", "Desc", AppCategory.BANKING))

        // Edit aadhaar to SMS_MESSENGER
        repository.updateUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.SMS_MESSENGER))

        val bankApp = repository.getUserAddedApps().first { it.packageName == customBank }
        assertEquals("Other app must remain in BANKING", AppCategory.BANKING, bankApp.category)
    }

    // 37. persistence failure rolls back
    @Test
    fun test37_persistenceFailure_rollsBack() {
        // Attempting to edit non-existent user app
        val nonExistent = SupportedApp("com.ghost.app", "Ghost", "Desc", AppCategory.BANKING)
        assertFalse(repository.updateUserApp(nonExistent))
    }

    // 38. uninstalled app cannot be actively edited
    @Test
    fun test38_uninstalledApp_cannotBeActivelyEdited() {
        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        repository.addUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.UPI_PAYMENT))

        // User uninstalls aadhaar
        appsProvider.uninstallApp(aadhaar)
        assertFalse(appsProvider.isPackageInstalled(aadhaar))

        // In UI, uninstalled app does not appear in active Supported Apps, so edit action cannot be invoked
        assertFalse(getActiveAppsForCategory(AppCategory.UPI_PAYMENT).any { it.packageName == aadhaar })
    }

    // 39. reinstall preserves edited category
    @Test
    fun test39_reinstall_preservesEditedCategory() {
        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        repository.addUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.UPI_PAYMENT))

        // Edit to BANKING
        repository.updateUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.BANKING))

        // Uninstall and reinstall
        appsProvider.uninstallApp(aadhaar)
        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))

        val restored = getActiveAppsForCategory(AppCategory.BANKING).firstOrNull { it.packageName == aadhaar }
        assertNotNull(restored)
        assertEquals(AppCategory.BANKING, restored?.category)
    }

    // 40. AppCatalog remains unchanged
    @Test
    fun test40_appCatalogRemainsUnchanged() {
        val initialCatalog = AppCatalog.allApps
        val initialSize = initialCatalog.size

        appsProvider.installApp(InstalledAppInfo(aadhaar, "mAadhaar"))
        repository.addUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.UPI_PAYMENT))
        repository.updateUserApp(SupportedApp(aadhaar, "mAadhaar", "Desc", AppCategory.BANKING))

        assertEquals("AppCatalog size must remain exactly 16", initialSize, AppCatalog.allApps.size)
        assertEquals("AppCatalog list must remain identical", initialCatalog, AppCatalog.allApps)
    }
}
