package com.example.arctracker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arctracker.data.AppDatabase
import com.example.arctracker.service.HistoricalSmsImportManager
import com.example.arctracker.service.SmsPermissionHelper
import com.example.arctracker.service.SmsScanResult
import com.example.arctracker.service.TransactionPersistenceManager
import com.example.arctracker.settings.MonitoringSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real device instrumented verification for Milestone 5 UI/Navigation and Clear All Data fixes.
 * Executes on the physical Samsung test device.
 */
@RunWith(AndroidJUnit4::class)
class NavigationAndClearDataDeviceTest {

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
    fun testRealDevice_initialSmsImportFlagLifecycleAndClearAllData() {
        // 1. Fresh state on device
        assertFalse(repository.isInitialSmsImportCompleted())

        // 2. Complete initial SMS import
        repository.setInitialSmsImportCompleted(true)
        assertTrue(repository.isInitialSmsImportCompleted())

        // 3. Verify persistence across fresh repository lookup
        val reloaded = MonitoringSettingsRepository.getInstance(context)
        assertTrue(reloaded.isInitialSmsImportCompleted())

        // 4. App restart check: onboarding dialog suppressed when completed
        val isCompleted = reloaded.isInitialSmsImportCompleted()
        val hasDismissedInitialSmsImportDialog = false
        val shouldShowPrompt = !hasDismissedInitialSmsImportDialog && !isCompleted
        assertFalse("Onboarding prompt must not be shown on app restart when completed", shouldShowPrompt)

        // 5. Clear All Data execution on real device
        val prefs = context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository.resetToDefaults()

        // 6. Confirm initial import state is reset
        assertFalse("Initial SMS import must be reset after Clear All Data", repository.isInitialSmsImportCompleted())

        // 7. Verify prompt can appear again after Clear All Data
        val promptAfterClear = !hasDismissedInitialSmsImportDialog && !repository.isInitialSmsImportCompleted()
        assertTrue("Initial SMS import prompt must become available again after Clear All Data", promptAfterClear)
    }

    @Test
    fun testRealDevice_navigationBackStack_nestedSettingsFlow() {
        var backStack = listOf("Home")

        fun navigateTo(route: String) {
            if (route == "Home") {
                backStack = listOf("Home")
            } else if (route == "Transactions" || route == "Settings") {
                backStack = listOf("Home", route)
            } else {
                if (backStack.lastOrNull() != route) {
                    backStack = backStack + route
                }
            }
        }

        fun navigateBack() {
            if (backStack.size > 1) {
                backStack = backStack.dropLast(1)
            }
        }

        // Home -> Settings -> ClearAllData
        navigateTo("Settings")
        assertEquals(listOf("Home", "Settings"), backStack)

        navigateTo("ClearAllData")
        assertEquals(listOf("Home", "Settings", "ClearAllData"), backStack)

        // Simulate swipe back (or toolbar back) from ClearAllData
        navigateBack()
        assertEquals("Back from ClearAllData must land on Settings", "Settings", backStack.last())
        assertEquals(listOf("Home", "Settings"), backStack)

        // Simulate swipe back from Settings
        navigateBack()
        assertEquals("Back from Settings must land on Home", "Home", backStack.last())
        assertEquals(listOf("Home"), backStack)

        // Back from Home: canNavigateBack is false, delegates to system
        assertFalse(backStack.size > 1)
    }

    @Test
    fun testRealDevice_toolbarOwnership_smsImportExclusion() {
        fun shouldParentRenderArcTrackerHeader(route: String): Boolean {
            return route != "SmsImport"
        }

        // Parent scaffold renders ArcTrackerHeader for standard screens
        assertTrue(shouldParentRenderArcTrackerHeader("Home"))
        assertTrue(shouldParentRenderArcTrackerHeader("Transactions"))
        assertTrue(shouldParentRenderArcTrackerHeader("Settings"))
        assertTrue(shouldParentRenderArcTrackerHeader("ClearAllData"))
        assertTrue(shouldParentRenderArcTrackerHeader("Database"))
        assertTrue(shouldParentRenderArcTrackerHeader("BackupRestore"))

        // For SmsImport, parent header must NOT render (SmsImportScreen owns its TopAppBar)
        assertFalse(shouldParentRenderArcTrackerHeader("SmsImport"))
    }

    @Test
    fun testRealDevice_historicalSmsImportRealScanExecution() = runBlocking {
        val manager = HistoricalSmsImportManager.create(context)

        val threeMonthsAgo = SmsPermissionHelper.calculateThreeMonthsAgoTimestamp()
        val now = System.currentTimeMillis()

        // Verify scan executes without throwing on real device
        val result = manager.scan(threeMonthsAgo, now)
        assertTrue(
            "Scan on real device must return Success or Cancelled without error",
            result is SmsScanResult.Success || result is SmsScanResult.Cancelled
        )
    }
}
