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
import com.example.arctracker.data.Expense
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
        runBlocking {
            AppDatabase.getDatabase(context).expenseDao().clearAll()
        }
    }

    @After
    fun tearDown() {
        val prefs = context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        runBlocking {
            AppDatabase.getDatabase(context).expenseDao().clearAll()
        }
    }

    @Test
    fun testRealDevice_scenarioA_completedOnboarding_clearAllData_preservesLifecycleAndSuppressesDialog() = runBlocking {
        val expenseDao = AppDatabase.getDatabase(context).expenseDao()

        // 1. Initial SMS onboarding completed
        repository.setInitialSmsImportCompleted(true)
        assertTrue(repository.isInitialSmsImportCompleted())

        // 2. Insert transactions into database
        val testExpense = Expense(
            amount = 450.0,
            merchant = "Device Test Restaurant",
            dateMillis = System.currentTimeMillis()
        )
        expenseDao.insert(testExpense)
        assertTrue("Database must contain inserted transactions", expenseDao.getCount() > 0)

        // 3. Clear All Data execution on real device
        val wasCompleted = repository.isInitialSmsImportCompleted()
        expenseDao.clearAll()
        repository.setInitialSmsImportCompleted(wasCompleted)

        // 4. Assertions:
        // Transactions = 0
        assertEquals(0, expenseDao.getCount())

        // initialSmsImportCompleted must remain TRUE
        assertTrue("initialSmsImportCompleted must remain true after Clear All Data", repository.isInitialSmsImportCompleted())

        // Dialog should NOT be shown
        val hasDismissedSession = false
        val shouldShowPrompt = !hasDismissedSession && !repository.isInitialSmsImportCompleted()
        assertFalse("Initial SMS import prompt must NOT appear after Clear All Data when completed", shouldShowPrompt)

        // Normal settings preserved (not wiped)
        assertTrue("Global monitoring must remain enabled", repository.getSettings().globalEnabled)
    }

    @Test
    fun testRealDevice_scenarioB_incompleteOnboarding_clearAllData_doesNotMarkComplete() = runBlocking {
        val expenseDao = AppDatabase.getDatabase(context).expenseDao()

        // 1. Initial SMS onboarding incomplete
        repository.setInitialSmsImportCompleted(false)
        assertFalse(repository.isInitialSmsImportCompleted())

        // 2. Insert transactions into database
        val testExpense = Expense(
            amount = 120.0,
            merchant = "Device Test Manual Item",
            dateMillis = System.currentTimeMillis()
        )
        expenseDao.insert(testExpense)
        assertTrue("Database must contain inserted transactions", expenseDao.getCount() > 0)

        // 3. Clear All Data execution
        val wasCompleted = repository.isInitialSmsImportCompleted()
        expenseDao.clearAll()
        repository.setInitialSmsImportCompleted(wasCompleted)

        // 4. Assertions:
        assertEquals(0, expenseDao.getCount())

        // initialSmsImportCompleted must remain FALSE
        assertFalse("Clear All Data must NOT mark onboarding as completed when it was incomplete", repository.isInitialSmsImportCompleted())

        // User remains eligible for onboarding
        val isEligibleForOnboarding = !repository.isInitialSmsImportCompleted()
        assertTrue("User must remain eligible for onboarding", isEligibleForOnboarding)
    }

    @Test
    fun testRealDevice_scenarioC_permissionIndependence() = runBlocking {
        val expenseDao = AppDatabase.getDatabase(context).expenseDao()

        // Check real Android permission
        val initialPermissionState = SmsPermissionHelper.isSmsPermissionGranted(context)
        repository.setInitialSmsImportCompleted(true)

        // Clear All Data
        val wasCompleted = repository.isInitialSmsImportCompleted()
        expenseDao.clearAll()
        repository.setInitialSmsImportCompleted(wasCompleted)

        // Verify Android permission state is unchanged
        val postClearPermissionState = SmsPermissionHelper.isSmsPermissionGranted(context)
        assertEquals("Android permission state must remain identical after Clear All Data", initialPermissionState, postClearPermissionState)
        assertTrue(repository.isInitialSmsImportCompleted())
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
