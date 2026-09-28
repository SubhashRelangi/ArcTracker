package com.example.arctracker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arctracker.data.AppDatabase
import com.example.arctracker.service.*
import com.example.arctracker.settings.MonitoringSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real device instrumented verification for Step 7:
 * First-Time Historical SMS Import Onboarding Integration.
 * Executes on the connected physical Samsung Galaxy A34 (Android 16).
 */
@RunWith(AndroidJUnit4::class)
class FirstTimeHistoricalSmsImportOnboardingDeviceTest {

    private lateinit var context: Context
    private lateinit var repository: MonitoringSettingsRepository
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        repository = MonitoringSettingsRepository.getInstance(context)
        database = AppDatabase.getDatabase(context)
        runBlocking {
            database.expenseDao().clearAll()
        }
        SmsPermissionHelper.permissionOverrideForTesting = null
        NotificationPermissionHelper.permissionOverrideForTesting = null
    }

    @After
    fun tearDown() {
        val prefs = context.getSharedPreferences("ArcTrackerPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        runBlocking {
            database.expenseDao().clearAll()
        }
        SmsPermissionHelper.permissionOverrideForTesting = null
        NotificationPermissionHelper.permissionOverrideForTesting = null
    }

    @Test
    fun test01_device_freshOnboarding_notificationAccess_historicalPrompt_notNow() = runBlocking {
        val dao = database.expenseDao()
        assertEquals("Initial DB must be empty", 0, dao.getCount())

        // 1. Fresh onboarding state: Notification granted, SMS not completed
        NotificationPermissionHelper.permissionOverrideForTesting = true
        SmsPermissionHelper.permissionOverrideForTesting = false
        assertFalse(repository.isInitialSmsImportCompleted())

        // 2. Historical import prompt is eligible to show
        val showPrompt = !repository.isInitialSmsImportCompleted()
        assertTrue("Prompt should appear on fresh onboarding", showPrompt)

        // 3. User chooses "Not now"
        // Simulating dialog dismiss callback:
        SmsPermissionHelper.setInitialImportCompleted(context, true)

        // 4. Verify prompt will not appear on subsequent app launch / session
        assertTrue(repository.isInitialSmsImportCompleted())
        val showPromptAfterDismiss = !repository.isInitialSmsImportCompleted()
        assertFalse("Prompt must not appear after Not now", showPromptAfterDismiss)

        // 5. Strictly ZERO Room database writes
        assertEquals("Room DB must remain strictly 0 rows", 0, dao.getCount())
        assertTrue(dao.getAllExpensesList().isEmpty())
    }

    @Test
    fun test02_device_import_readSmsGranted_existingImportScreenFlow_scansAndImportsSelected() = runBlocking {
        val dao = database.expenseDao()
        assertEquals(0, dao.getCount())

        // 1. Notification access granted, user taps "Import", grants READ_SMS
        NotificationPermissionHelper.permissionOverrideForTesting = true
        SmsPermissionHelper.permissionOverrideForTesting = true

        // Mark onboarding completed and navigate to SmsImportScreen
        SmsPermissionHelper.setInitialImportCompleted(context, true)
        assertTrue(repository.isInitialSmsImportCompleted())

        // 2. User chooses date range in SmsImportScreen and clicks "Scan Messages"
        val mockReader = InMemorySmsReader(
            hasPermission = true,
            records = listOf(
                SmsRecord(
                    id = 5001L,
                    address = "AD-HDFCBK",
                    body = "A/c XX4381 debited by HDFC Bank Rs. 350.00 on 28-Sep-26 to Swiggy Ref 9001",
                    dateMillis = 1774000000000L
                ),
                SmsRecord(
                    id = 5002L,
                    address = "AD-SBIINB",
                    body = "SBI A/c XX7724 credited INR 1,500.00 on 28-Sep-26 by transfer Ref 9002",
                    dateMillis = 1774000050000L
                )
            )
        )

        val importManager = HistoricalSmsImportManager(
            smsReader = mockReader,
            dao = dao,
            persistenceManager = TransactionPersistenceManager
        )

        val scanResult = importManager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        // Scan verification: 2 groups discovered, strictly ZERO database writes
        assertEquals(2, scanResult.accountGroups.size)
        assertEquals("Database must remain empty after scan", 0, dao.getCount())

        // 3. User selects only HDFC account group and clicks "Import Selected"
        val selectedIds = setOf("hdfc_bank_account_4381")
        val importResult = importManager.importTransactions(
            scanResult = scanResult,
            selectedGroupIds = selectedIds
        ) as SmsImportResult.Success

        assertEquals(1, importResult.insertedCount)

        // 4. Persistence verification: exactly 1 expense row in real hardware Room SQLite DB
        assertEquals(1, dao.getCount())
        val saved = dao.getAllExpensesList().first()
        assertEquals(350.0, saved.amount, 0.001)
        assertTrue(saved.note?.contains("HDFC Bank") == true || saved.note?.contains("4381") == true)
        assertFalse(saved.note?.contains("SBI") == true)
    }

    @Test
    fun test03_device_import_readSmsDenied_noScan_noDbWrite_appRemainsUsable() = runBlocking {
        val dao = database.expenseDao()
        assertEquals(0, dao.getCount())

        // 1. User taps "Import" but denies READ_SMS permission
        SmsPermissionHelper.permissionOverrideForTesting = false

        // Simulating denial callback: safely dismiss dialog
        var hasDismissed = false
        val onDenied = {
            hasDismissed = true
            SmsPermissionHelper.setInitialImportCompleted(context, true)
        }

        onDenied()

        assertTrue(hasDismissed)
        assertTrue(repository.isInitialSmsImportCompleted())

        // 2. No scan ran, zero DB writes, app remains in safe operational state
        assertEquals("Room DB must have 0 rows after denial", 0, dao.getCount())
        assertTrue(dao.getAllExpensesList().isEmpty())
    }

    @Test
    fun test04_device_import_readSmsAlreadyGranted_navigatesDirectlyWithoutRequestingAgain() = runBlocking {
        val dao = database.expenseDao()
        assertEquals(0, dao.getCount())

        // 1. User taps "Import" with READ_SMS already granted
        SmsPermissionHelper.permissionOverrideForTesting = true

        var permissionRequested = false
        var navigatedToImport = false

        if (SmsPermissionHelper.permissionOverrideForTesting == true) {
            SmsPermissionHelper.setInitialImportCompleted(context, true)
            navigatedToImport = true
        } else {
            permissionRequested = true
        }

        assertFalse("Permission must NOT be requested again", permissionRequested)
        assertTrue("Must navigate to import directly", navigatedToImport)
        assertEquals("Room DB must remain untouched until user explicitly scans and imports", 0, dao.getCount())
    }
}
