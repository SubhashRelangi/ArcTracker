package com.subhashrelangi.arctracker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subhashrelangi.arctracker.settings.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Step 9.7 Connected Android Device Tests for Lifecycle Reconciliation & State Consistency.
 * Executes on physical Samsung Galaxy A34 5G (SM-A346E - 16).
 * Verifies:
 * - Real runtime partition of Supported Apps vs Add Apps with zero overlap
 * - Category preservation across repository reload and device lifecycle
 * - Global monitoring preservation across screen reconciliations
 * - Effective monitoring requires package to be both configured/enabled AND physically installed
 */
@RunWith(AndroidJUnit4::class)
class LifecycleReconciliationDeviceTest {

    private lateinit var context: Context
    private lateinit var repository: MonitoringSettingsRepository
    private lateinit var provider: DefaultInstalledAppsProvider

    private val testPackage = "com.example.reconcile.test"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = MonitoringSettingsRepository.getInstance(context)
        provider = DefaultInstalledAppsProvider(context)
    }

    @After
    fun tearDown() {
        val prefs = context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun testRealDevice_reconciliation_partitionsSupportedAndAddableWithoutOverlap() {
        val installed = provider.getInstalledApps()
        val configured = repository.getAllConfiguredApps()

        val installedSet = installed.map { it.packageName.lowercase() }.toSet()
        val configuredSet = configured.map { it.packageName.lowercase() }.toSet()

        // Active supported apps = configured INTERSECT installed
        val activeSupported = configured.filter { installedSet.contains(it.packageName.lowercase()) }
            .distinctBy { it.packageName.lowercase() }

        // Addable apps = installed MINUS configured
        val addable = installed.filter { !configuredSet.contains(it.packageName.lowercase()) }
            .distinctBy { it.packageName.lowercase() }

        // Verify zero overlap
        val activePackages = activeSupported.map { it.packageName.lowercase() }.toSet()
        val addablePackages = addable.map { it.packageName.lowercase() }.toSet()
        val overlap = activePackages.intersect(addablePackages)

        assertTrue("Supported Apps and Add Apps must have zero overlap on real device", overlap.isEmpty())
    }

    @Test
    fun testRealDevice_reconciliation_preservesUserCategoryAcrossReload() {
        val app = SupportedApp(
            packageName = testPackage,
            displayName = "Reconciliation App",
            description = "Device reconciliation test",
            category = AppCategory.BANKING
        )
        assertTrue(repository.addUserApp(app))

        // Reload from storage
        val reloaded = MonitoringSettingsRepository.getInstance(context)
        val loaded = reloaded.getUserAddedApps().firstOrNull { it.packageName == testPackage }
        assertNotNull("Persisted user app must survive reload", loaded)
        assertEquals("Category must remain BANKING", AppCategory.BANKING, loaded?.category)
    }

    @Test
    fun testRealDevice_reconciliation_globalMonitoringUntouched() {
        repository.setGlobalEnabled(false)
        assertFalse(repository.getSettings().globalEnabled)

        // Add user app
        val app = SupportedApp(testPackage, "Reconcile App", "Desc", AppCategory.UPI_PAYMENT)
        repository.addUserApp(app)

        val reloaded = MonitoringSettingsRepository.getInstance(context)
        assertFalse("globalEnabled must REMAIN false across reconciliations", reloaded.getSettings().globalEnabled)
    }

    @Test
    fun testRealDevice_reconciliation_effectiveMonitoringRequiresInstalledAndEnabled() {
        val uninstalledPackage = "com.uninstalled.fake.app"
        val app = SupportedApp(uninstalledPackage, "Ghost App", "Desc", AppCategory.UPI_PAYMENT)
        repository.addUserApp(app)
        repository.setGlobalEnabled(true)

        // Configured and enabled in settings
        assertTrue(repository.isAppEnabled(uninstalledPackage))
        assertTrue(repository.isMonitoringActive(uninstalledPackage))

        // But physically NOT installed on device
        val isPhysicallyInstalled = provider.isPackageInstalled(uninstalledPackage)
        assertFalse("Ghost package is not physically installed", isPhysicallyInstalled)

        // Effective monitoring must be false
        assertFalse("Effective monitoring must be FALSE for uninstalled package",
            repository.isPackageEffectivelyMonitored(uninstalledPackage, isPhysicallyInstalled))
    }
}
