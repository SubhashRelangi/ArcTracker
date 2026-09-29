package com.subhashrelangi.arctracker

import android.content.SharedPreferences
import com.subhashrelangi.arctracker.settings.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Step 9.8.1 Regression Tests:
 * Verifies that newly added user apps have their package added to enabledPackages by default,
 * while leaving globalEnabled completely untouched, preserving existing states,
 * and ensuring atomicity upon failure.
 */
class NewAppDefaultMonitoringStateTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var repository: MonitoringSettingsRepository

    private val gpay = "com.google.android.apps.nbu.paisa.user"
    private val phonepe = "com.phonepe.app"
    private val paytm = "net.one97.paytm"
    private val newUpiApp = "com.custom.upi.test"
    private val newBankApp = "com.jupiter.money"
    private val newSmsApp = "org.telegram.messenger"

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        repository = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
    }

    // 1. newly added app is enabled by default
    @Test
    fun test01_newlyAddedApp_isEnabledByDefault() {
        val app = SupportedApp(
            packageName = newUpiApp,
            displayName = "BHIM UPI",
            description = "UPI app",
            category = AppCategory.UPI_PAYMENT
        )

        val result = repository.addUserApp(app)
        assertTrue("addUserApp should succeed", result)
        assertTrue("Newly added app must be enabled by default", repository.isAppEnabled(newUpiApp))
    }

    // 2. newly added app package exists in enabledPackages
    @Test
    fun test02_newlyAddedApp_packageExistsInEnabledPackages() {
        val app = SupportedApp(
            packageName = newBankApp,
            displayName = "Jupiter",
            description = "Banking app",
            category = AppCategory.BANKING
        )

        repository.addUserApp(app)
        val enabledPackages = repository.getSettings().enabledPackages
        assertTrue(
            "enabledPackages set must contain newly added package",
            enabledPackages.contains(newBankApp)
        )
    }

    // 3. globalEnabled remains unchanged when adding (globalEnabled = true)
    @Test
    fun test03_globalEnabled_remainsUnchangedWhenAdding_withGlobalTrue() {
        repository.setGlobalEnabled(true)
        assertTrue(repository.getSettings().globalEnabled)

        val app = SupportedApp(
            packageName = newUpiApp,
            displayName = "BHIM UPI",
            description = "UPI app",
            category = AppCategory.UPI_PAYMENT
        )
        repository.addUserApp(app)

        assertTrue(
            "globalEnabled must remain true after adding an app",
            repository.getSettings().globalEnabled
        )
    }

    // 4. globalEnabled=false + newly added app remains global=false
    @Test
    fun test04_globalEnabledFalse_newlyAddedApp_remainsGlobalFalse() {
        repository.setGlobalEnabled(false)
        assertFalse("globalEnabled is initially false", repository.getSettings().globalEnabled)

        val app = SupportedApp(
            packageName = newSmsApp,
            displayName = "Telegram",
            description = "Messaging app",
            category = AppCategory.SMS_MESSENGER
        )
        repository.addUserApp(app)

        // Repository settings checks
        assertFalse(
            "globalEnabled must remain false after adding an app",
            repository.getSettings().globalEnabled
        )
        assertTrue(
            "Package switch is enabled individually",
            repository.isAppEnabled(newSmsApp)
        )
        assertTrue(
            "Package exists in enabledPackages",
            repository.getSettings().enabledPackages.contains(newSmsApp)
        )

        // Effective monitoring check: must be FALSE because global monitoring is disabled
        assertFalse(
            "Monitoring must NOT be active when globalEnabled is false",
            repository.isMonitoringActive(newSmsApp)
        )
        assertFalse(
            "Package must not be effectively monitored when globalEnabled is false",
            repository.isPackageEffectivelyMonitored(newSmsApp, isInstalled = true)
        )
    }

    // 5. existing enabledPackages entries remain unchanged
    @Test
    fun test05_existingEnabledPackages_remainUnchanged() {
        // AppCatalog defaults are enabled: GPay, PhonePe, Paytm
        assertTrue(repository.isAppEnabled(gpay))
        assertTrue(repository.isAppEnabled(phonepe))
        assertTrue(repository.isAppEnabled(paytm))

        val app = SupportedApp(
            packageName = newBankApp,
            displayName = "Jupiter",
            description = "Banking app",
            category = AppCategory.BANKING
        )
        repository.addUserApp(app)

        assertTrue("GPay remains enabled", repository.isAppEnabled(gpay))
        assertTrue("PhonePe remains enabled", repository.isAppEnabled(phonepe))
        assertTrue("Paytm remains enabled", repository.isAppEnabled(paytm))
        assertTrue("New app is enabled", repository.isAppEnabled(newBankApp))
    }

    // 6. existing disabled app states remain unchanged
    @Test
    fun test06_existingDisabledAppStates_remainUnchanged() {
        // Explicitly disable PhonePe
        repository.setAppEnabled(phonepe, false)
        assertFalse("PhonePe was disabled", repository.isAppEnabled(phonepe))
        assertTrue("GPay remains enabled", repository.isAppEnabled(gpay))

        val app = SupportedApp(
            packageName = newUpiApp,
            displayName = "BHIM UPI",
            description = "UPI app",
            category = AppCategory.UPI_PAYMENT
        )
        repository.addUserApp(app)

        assertFalse("PhonePe MUST REMAIN disabled after new app is added", repository.isAppEnabled(phonepe))
        assertTrue("GPay remains enabled", repository.isAppEnabled(gpay))
        assertTrue("New app is enabled", repository.isAppEnabled(newUpiApp))
    }

    // 7. persistence/repository recreation preserves newly-added enabled state
    @Test
    fun test07_persistence_recreationPreservesNewlyAddedEnabledState() {
        val app = SupportedApp(
            packageName = newUpiApp,
            displayName = "BHIM UPI",
            description = "UPI app",
            category = AppCategory.UPI_PAYMENT
        )
        repository.addUserApp(app)
        assertTrue(repository.isAppEnabled(newUpiApp))

        // Recreate repository from the same underlying SharedPreferences
        val reloadedRepository = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        val configuredApps = reloadedRepository.getAllConfiguredApps()
        assertTrue("Configured apps must contain newly added app", configuredApps.any { it.packageName == newUpiApp })
        assertTrue("Newly added app must be enabled after reload", reloadedRepository.isAppEnabled(newUpiApp))
        assertTrue(
            "enabledPackages set must contain newly added package after reload",
            reloadedRepository.getSettings().enabledPackages.contains(newUpiApp)
        )
    }

    // 8. failed addition does not leave an orphan enabledPackages entry
    @Test
    fun test08_failedAddition_doesNotLeaveOrphanEnabledPackagesEntry() {
        val initialEnabled = repository.getSettings().enabledPackages

        // Case A: Blank package name
        val blankApp = SupportedApp(
            packageName = "",
            displayName = "Blank",
            description = "Desc",
            category = AppCategory.UPI_PAYMENT
        )
        assertFalse("Blank package addition must fail", repository.addUserApp(blankApp))
        assertFalse(repository.getSettings().enabledPackages.contains(""))
        assertEquals("enabledPackages must remain unchanged on blank failure", initialEnabled, repository.getSettings().enabledPackages)

        // Case B: Built-in package cannot be re-added
        val builtInApp = SupportedApp(
            packageName = gpay,
            displayName = "Fake GPay",
            description = "Desc",
            category = AppCategory.BANKING
        )
        assertFalse("Built-in package addition must fail", repository.addUserApp(builtInApp))
        assertEquals("enabledPackages must remain unchanged on built-in collision", initialEnabled, repository.getSettings().enabledPackages)

        // Case C: Duplicate user-added app where user had previously disabled it
        val validApp = SupportedApp(
            packageName = newBankApp,
            displayName = "Jupiter",
            description = "Banking app",
            category = AppCategory.BANKING
        )
        assertTrue(repository.addUserApp(validApp))
        assertTrue(repository.isAppEnabled(newBankApp))

        // User turns off this app
        repository.setAppEnabled(newBankApp, false)
        assertFalse(repository.isAppEnabled(newBankApp))

        // Attempting to re-add the duplicate app must fail and NOT reset switch to enabled
        assertFalse("Duplicate add must fail", repository.addUserApp(validApp))
        assertFalse("Duplicate failed add must NOT reset disabled switch to true", repository.isAppEnabled(newBankApp))

        // Case D: Failing SharedPreferences commit simulation
        val failingPrefs = FailingCommitSharedPreferences()
        val failingRepo = SharedPreferencesMonitoringSettingsRepository(failingPrefs)
        val initialFailingEnabled = failingRepo.getSettings().enabledPackages

        val uncommitableApp = SupportedApp(
            packageName = "com.uncommitable.app",
            displayName = "Uncommitable",
            description = "Desc",
            category = AppCategory.UPI_PAYMENT
        )
        assertFalse("Commit failure must return false", failingRepo.addUserApp(uncommitableApp))
        assertFalse("Failed commit must not store user app", failingRepo.getUserAddedApps().any { it.packageName == uncommitableApp.packageName })
        assertFalse("Failed commit must not add to enabledPackages", failingRepo.getSettings().enabledPackages.contains(uncommitableApp.packageName))
        assertFalse("isAppEnabled must be false", failingRepo.isAppEnabled(uncommitableApp.packageName))
        assertEquals("enabledPackages must be identical to initial state", initialFailingEnabled, failingRepo.getSettings().enabledPackages)
    }

    // 9. InMemoryMonitoringSettingsRepository mirrors same behavior
    @Test
    fun test09_inMemoryRepository_defaultMonitoringState() {
        val inMemory = InMemoryMonitoringSettingsRepository(initialGlobalEnabled = false)
        assertFalse(inMemory.getSettings().globalEnabled)

        val app = SupportedApp(
            packageName = newUpiApp,
            displayName = "BHIM UPI",
            description = "Desc",
            category = AppCategory.UPI_PAYMENT
        )
        assertTrue(inMemory.addUserApp(app))
        assertFalse("globalEnabled remains false", inMemory.getSettings().globalEnabled)
        assertTrue("New app is enabled in enabledPackages", inMemory.isAppEnabled(newUpiApp))
        assertTrue("Package in enabledPackages", inMemory.getSettings().enabledPackages.contains(newUpiApp))
        assertFalse("Not actively monitored when global is off", inMemory.isMonitoringActive(newUpiApp))

        // Duplicate rejection
        assertFalse(inMemory.addUserApp(app))
        // Blank rejection
        assertFalse(inMemory.addUserApp(SupportedApp("", "Blank", "Desc", AppCategory.BANKING)))
    }
}

/**
 * SharedPreferences implementation where Editor.commit() always fails and does not write.
 */
private class FailingCommitSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any>()

    override fun getAll(): MutableMap<String, *> = HashMap(data)
    override fun getString(key: String?, defValue: String?): String? = (data[key] as? String) ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        val value = data[key]
        return if (value is Set<*>) (value as Set<String>).toMutableSet() else defValues
    }

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
        override fun commit(): Boolean = false // Always simulate commit failure
        override fun apply() {} // No-op
    }
}
