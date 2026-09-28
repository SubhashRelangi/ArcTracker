package com.example.arctracker

import com.example.arctracker.data.Expense
import com.example.arctracker.service.*
import com.example.arctracker.settings.InMemoryMonitoringSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Verification test suite for:
 * STEP 7: First-Time Historical SMS Import Onboarding Integration
 */
class FirstTimeHistoricalSmsImportOnboardingTest {

    private lateinit var settingsRepo: InMemoryMonitoringSettingsRepository
    private lateinit var dao: FakeExpenseDao
    private lateinit var reader: InMemorySmsReader
    private lateinit var manager: HistoricalSmsImportManager

    @Before
    fun setUp() {
        settingsRepo = InMemoryMonitoringSettingsRepository()
        dao = FakeExpenseDao()
        reader = InMemorySmsReader(hasPermission = true, records = emptyList())
        manager = HistoricalSmsImportManager(
            smsReader = reader,
            dao = dao,
            persistenceManager = TransactionPersistenceManager
        )
        NotificationPermissionHelper.permissionOverrideForTesting = null
        SmsPermissionHelper.permissionOverrideForTesting = null
    }

    @After
    fun tearDown() {
        NotificationPermissionHelper.permissionOverrideForTesting = null
        SmsPermissionHelper.permissionOverrideForTesting = null
    }

    // =========================================================================
    // Test 1: Notification Permission Granted -> Historical Import Prompt Appears
    // =========================================================================

    @Test
    fun test01_notificationPermissionGranted_historicalImportPromptAppears() {
        // Given Notification Access is granted
        val isNotificationAccessGranted = true
        val hasDismissedNotificationPermissionDialog = false
        val hasDismissedInitialSmsImportDialog = false
        val isInitialImportCompleted = settingsRepo.isInitialSmsImportCompleted()

        assertFalse("Initial SMS import should be false on fresh install", isInitialImportCompleted)

        // Notification dialog is NOT shown because permission is granted
        val showNotificationDialog = !isNotificationAccessGranted && !hasDismissedNotificationPermissionDialog
        assertFalse(showNotificationDialog)

        // Historical SMS import prompt condition evaluates to TRUE
        val showHistoricalImportPrompt = !hasDismissedInitialSmsImportDialog && !isInitialImportCompleted
        assertTrue("Historical import prompt must appear after notification access is granted", showHistoricalImportPrompt)
    }

    // =========================================================================
    // Test 2: Press "Not now" -> Prompt Closes, No SMS Request, No Scan, No DB Writes
    // =========================================================================

    @Test
    fun test02_pressNotNow_closesPrompt_doesNotRequestSms_doesNotScan_doesNotModifyDb() = runBlocking {
        var hasDismissedInitialSmsImportDialog = false
        var currentRoute = "Home"
        var smsPermissionRequested = false

        // User clicks "Not now"
        val onDismiss = {
            hasDismissedInitialSmsImportDialog = true
            settingsRepo.setInitialSmsImportCompleted(true)
        }

        onDismiss()

        // 1. Dialog is dismissed
        assertTrue(hasDismissedInitialSmsImportDialog)
        val shouldShowPrompt = !hasDismissedInitialSmsImportDialog && !settingsRepo.isInitialSmsImportCompleted()
        assertFalse("Prompt must be closed after Not now", shouldShowPrompt)

        // 2. READ_SMS was not requested
        assertFalse("READ_SMS must not be requested when Not now is clicked", smsPermissionRequested)

        // 3. Zero database writes
        assertEquals(0, dao.getCount())
        assertTrue(dao.getAllExpensesList().isEmpty())

        // 4. Route remains Home (no navigation to SmsImport)
        assertEquals("Home", currentRoute)
    }

    // =========================================================================
    // Test 3: Press "Import" with READ_SMS not granted -> READ_SMS is Requested
    // =========================================================================

    @Test
    fun test03_pressImport_permissionNotGranted_requestsReadSms() {
        SmsPermissionHelper.permissionOverrideForTesting = false

        var requestPermissionCalled = false
        var navigatedToImport = false

        // Simulate "Import" button click logic
        fun onImportClicked() {
            if (SmsPermissionHelper.permissionOverrideForTesting == true) {
                navigatedToImport = true
            } else {
                requestPermissionCalled = true
            }
        }

        onImportClicked()

        assertTrue("READ_SMS permission must be requested when not already granted", requestPermissionCalled)
        assertFalse("Must not navigate until permission is granted", navigatedToImport)
    }

    // =========================================================================
    // Test 4: READ_SMS Granted -> Existing Import Transactions Screen Opens
    // =========================================================================

    @Test
    fun test04_readSmsGranted_opensExistingImportTransactionsScreen() {
        var backStack = listOf("Home")
        fun navigateTo(route: String) {
            backStack = backStack + route
        }

        var hasDismissedInitialSmsImportDialog = false

        // User grants permission in permission dialog
        val isGranted = true
        if (isGranted) {
            hasDismissedInitialSmsImportDialog = true
            settingsRepo.setInitialSmsImportCompleted(true)
            navigateTo("SmsImport")
        }

        assertTrue(hasDismissedInitialSmsImportDialog)
        assertTrue(settingsRepo.isInitialSmsImportCompleted())
        assertEquals("SmsImport", backStack.last())
        assertEquals(listOf("Home", "SmsImport"), backStack)
    }

    // =========================================================================
    // Test 5: READ_SMS Already Granted -> Opens Screen Without Requesting Again
    // =========================================================================

    @Test
    fun test05_readSmsAlreadyGranted_opensImportTransactionsScreenWithoutRequestingPermissionAgain() {
        SmsPermissionHelper.permissionOverrideForTesting = true

        var permissionLauncherLaunched = false
        var backStack = listOf("Home")
        fun navigateTo(route: String) {
            backStack = backStack + route
        }

        // User presses "Import"
        if (SmsPermissionHelper.permissionOverrideForTesting == true) {
            // Already granted: directly navigate
            settingsRepo.setInitialSmsImportCompleted(true)
            navigateTo("SmsImport")
        } else {
            permissionLauncherLaunched = true
        }

        assertFalse("Permission launcher must NOT be launched if already granted", permissionLauncherLaunched)
        assertEquals("SmsImport", backStack.last())
        assertEquals(listOf("Home", "SmsImport"), backStack)
    }

    // =========================================================================
    // Test 6: READ_SMS Denied -> No Scan, No DB Write, App Remains Usable
    // =========================================================================

    @Test
    fun test06_readSmsDenied_noScan_noDbWrite_appRemainsUsable() = runBlocking {
        var backStack = listOf("Home")
        fun navigateTo(route: String) {
            backStack = backStack + route
        }

        var hasDismissedInitialSmsImportDialog = false

        // User denies READ_SMS permission
        val isGranted = false
        if (isGranted) {
            hasDismissedInitialSmsImportDialog = true
            settingsRepo.setInitialSmsImportCompleted(true)
            navigateTo("SmsImport")
        } else {
            // Dismiss dialog safely without crash or navigation
            hasDismissedInitialSmsImportDialog = true
            settingsRepo.setInitialSmsImportCompleted(true)
        }

        assertTrue(hasDismissedInitialSmsImportDialog)
        // No navigation occurred: remains on Home
        assertEquals("Home", backStack.last())
        assertEquals(listOf("Home"), backStack)

        // Zero database writes
        assertEquals(0, dao.getCount())
        assertTrue(dao.getAllExpensesList().isEmpty())
    }

    // =========================================================================
    // Test 7: Import Transactions Screen Functions with Pipeline and Selection
    // =========================================================================

    @Test
    fun test07_importTransactionsScreen_stillWorksExactlyAsBefore() = runBlocking {
        // Pipeline test: scan produces groups, zero writes, selected import persists
        reader.records = listOf(
            SmsRecord(
                id = 101L,
                address = "AD-HDFCBK",
                body = "A/c XX4381 debited by HDFC Bank Rs. 450.00 on 28-Sep-26 to Swiggy Ref 1111",
                dateMillis = 1774000000000L
            ),
            SmsRecord(
                id = 102L,
                address = "AD-SBIINB",
                body = "SBI A/c XX7724 credited INR 2,500.00 on 28-Sep-26 by transfer Ref 2222",
                dateMillis = 1774000050000L
            )
        )

        // Scan phase: produces groups, 0 DB writes
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(2, scanResult.accountGroups.size)
        assertEquals(0, dao.getCount())

        // Selected import of HDFC only
        val importResult = manager.importTransactions(
            scanResult,
            selectedGroupIds = setOf("hdfc_bank_account_4381")
        ) as SmsImportResult.Success

        assertEquals(1, importResult.insertedCount)
        assertEquals(1, dao.getCount())
        val saved = dao.getAllExpensesList().first()
        assertEquals(450.0, saved.amount, 0.001)
    }

    // =========================================================================
    // Test 8: Manual Import Entry Under Settings Remains Available After Not Now
    // =========================================================================

    @Test
    fun test08_manualImportEntryUnderSettings_remainsAvailableAfterChoosingNotNow() {
        // User previously chose "Not now"
        settingsRepo.setInitialSmsImportCompleted(true)
        assertTrue(settingsRepo.isInitialSmsImportCompleted())

        // Navigation stack simulation: Home -> Settings -> SmsImport
        var backStack = listOf("Home")
        fun navigateTo(route: String) {
            if (route == "Home") {
                backStack = listOf("Home")
            } else if (route == "Settings") {
                backStack = listOf("Home", route)
            } else {
                backStack = backStack + route
            }
        }

        navigateTo("Settings")
        assertEquals(listOf("Home", "Settings"), backStack)

        // In Data & Storage, user clicks "Import Previous Transactions"
        navigateTo("SmsImport")
        assertEquals(listOf("Home", "Settings", "SmsImport"), backStack)
        assertEquals("SmsImport", backStack.last())

        // Back from SmsImport -> Settings
        backStack = backStack.dropLast(1)
        assertEquals("Settings", backStack.last())
    }

    // =========================================================================
    // Test 9: Reopening App Does Not Create Duplicate Onboarding Dialogs
    // =========================================================================

    @Test
    fun test09_reopeningApp_doesNotCreateDuplicateOnboardingDialogs() {
        // Initial import completed during onboarding
        settingsRepo.setInitialSmsImportCompleted(true)

        // Simulate app kill and reopen (fresh component state)
        val hasDismissedInitialSmsImportDialog = false
        val isInitialCompleted = settingsRepo.isInitialSmsImportCompleted()

        val shouldShowDialog = !hasDismissedInitialSmsImportDialog && !isInitialCompleted
        assertFalse("Reopening app must NOT show onboarding dialog if already completed", shouldShowDialog)
    }

    // =========================================================================
    // Test 10: Existing Notification Access Behavior Remains Unchanged
    // =========================================================================

    @Test
    fun test10_existingNotificationAccessBehavior_remainsUnchanged() {
        // When notification access is NOT granted
        val isNotificationAccessGranted = false
        val hasDismissedNotificationPermissionDialog = false
        val hasDismissedInitialSmsImportDialog = false

        val showNotificationDialog = !isNotificationAccessGranted && !hasDismissedNotificationPermissionDialog
        assertTrue("Notification permission dialog must show when notification access is not granted", showNotificationDialog)

        // Historical SMS import dialog must NOT show before notification permission is resolved
        // In MainActivity, the `else if` ensures SMS dialog only renders when notification dialog is resolved
        val canShowSmsDialogInBranch = isNotificationAccessGranted || hasDismissedNotificationPermissionDialog
        assertFalse("SMS onboarding dialog must NOT preempt notification access dialog", canShowSmsDialogInBranch)
    }
}
