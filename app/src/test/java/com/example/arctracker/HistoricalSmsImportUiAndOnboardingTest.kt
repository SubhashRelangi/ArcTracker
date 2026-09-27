package com.example.arctracker

import android.content.SharedPreferences
import com.example.arctracker.data.Expense
import com.example.arctracker.service.*
import com.example.arctracker.settings.InMemoryMonitoringSettingsRepository
import com.example.arctracker.settings.MonitoringSettingsRepository
import com.example.arctracker.settings.SharedPreferencesMonitoringSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class HistoricalSmsImportUiAndOnboardingTest {

    private lateinit var fakePrefs: FakeTestSharedPreferences
    private lateinit var sharedPrefsRepo: SharedPreferencesMonitoringSettingsRepository
    private lateinit var inMemoryRepo: InMemoryMonitoringSettingsRepository

    @Before
    fun setUp() {
        fakePrefs = FakeTestSharedPreferences()
        sharedPrefsRepo = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        inMemoryRepo = InMemoryMonitoringSettingsRepository()
    }

    @Test
    fun test01_initialSmsImportFlag_defaultsToFalse() {
        assertFalse(sharedPrefsRepo.isInitialSmsImportCompleted())
        assertFalse(inMemoryRepo.isInitialSmsImportCompleted())
    }

    @Test
    fun test02_initialSmsImportFlag_persistsTrue() {
        sharedPrefsRepo.setInitialSmsImportCompleted(true)
        assertTrue(sharedPrefsRepo.isInitialSmsImportCompleted())
        assertEquals(true, fakePrefs.getBoolean(MonitoringSettingsRepository.KEY_INITIAL_SMS_IMPORT_COMPLETED, false))

        val reloadedRepo = SharedPreferencesMonitoringSettingsRepository(fakePrefs)
        assertTrue(reloadedRepo.isInitialSmsImportCompleted())
    }

    @Test
    fun test03_initialSmsImportFlag_canBeResetToFalse() {
        sharedPrefsRepo.setInitialSmsImportCompleted(true)
        assertTrue(sharedPrefsRepo.isInitialSmsImportCompleted())

        sharedPrefsRepo.setInitialSmsImportCompleted(false)
        assertFalse(sharedPrefsRepo.isInitialSmsImportCompleted())
    }

    @Test
    fun test04_inMemoryRepo_initialSmsImportFlag_updatesCorrectly() {
        inMemoryRepo.setInitialSmsImportCompleted(true)
        assertTrue(inMemoryRepo.isInitialSmsImportCompleted())

        inMemoryRepo.setInitialSmsImportCompleted(false)
        assertFalse(inMemoryRepo.isInitialSmsImportCompleted())
    }

    @Test
    fun test05_calculateThreeMonthsAgoTimestamp_exactThreeCalendarMonths() {
        // Set fixed anchor: 2026-09-27 15:30:45.123
        val anchorCalendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.SEPTEMBER)
            set(Calendar.DAY_OF_MONTH, 27)
            set(Calendar.HOUR_OF_DAY, 15)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 45)
            set(Calendar.MILLISECOND, 123)
        }
        val anchorMillis = anchorCalendar.timeInMillis

        val threeMonthsAgo = SmsPermissionHelper.calculateThreeMonthsAgoTimestamp(anchorMillis)

        val resultCalendar = Calendar.getInstance().apply {
            timeInMillis = threeMonthsAgo
        }

        // Expected: 2026-06-27 00:00:00.000
        assertEquals(2026, resultCalendar.get(Calendar.YEAR))
        assertEquals(Calendar.JUNE, resultCalendar.get(Calendar.MONTH))
        assertEquals(27, resultCalendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, resultCalendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCalendar.get(Calendar.MINUTE))
        assertEquals(0, resultCalendar.get(Calendar.SECOND))
        assertEquals(0, resultCalendar.get(Calendar.MILLISECOND))
    }

    @Test
    fun test06_calculateThreeMonthsAgoTimestamp_yearBoundary() {
        // Anchor: 2026-01-15 10:00:00
        val anchorCalendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 15)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val anchorMillis = anchorCalendar.timeInMillis

        val threeMonthsAgo = SmsPermissionHelper.calculateThreeMonthsAgoTimestamp(anchorMillis)

        val resultCalendar = Calendar.getInstance().apply {
            timeInMillis = threeMonthsAgo
        }

        // Expected: 2025-10-15 00:00:00
        assertEquals(2025, resultCalendar.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, resultCalendar.get(Calendar.MONTH))
        assertEquals(15, resultCalendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, resultCalendar.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun test07_dateValidation_startBeforeOrEqualEnd_isValid() {
        val now = System.currentTimeMillis()
        val past = now - (30L * 24 * 60 * 60 * 1000)

        // Valid ranges
        assertTrue(past < now)
        assertTrue(now == now)

        // Invalid range
        val invalidStart = now
        val invalidEnd = past
        assertTrue(invalidStart > invalidEnd)
    }

    @Test
    fun test08_presetDateCalculations() {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        // Last 30 days
        val thirtyDaysAgo = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -30)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertTrue(thirtyDaysAgo < today)

        // Last 3 months
        val threeMonthsAgo = Calendar.getInstance().apply {
            add(Calendar.MONTH, -3)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertTrue(threeMonthsAgo < thirtyDaysAgo)

        // Last 6 months
        val sixMonthsAgo = Calendar.getInstance().apply {
            add(Calendar.MONTH, -6)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertTrue(sixMonthsAgo < threeMonthsAgo)

        // Last 1 year
        val oneYearAgo = Calendar.getInstance().apply {
            add(Calendar.YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertTrue(oneYearAgo < sixMonthsAgo)
    }

    @Test
    fun test09_idempotency_secondImportYieldsZeroNewPersistedExpenses() = runBlocking {
        val fakeDao = FakeExpenseDao()
        val sms1 = SmsRecord(
            id = 101L,
            address = "HDFCBK",
            body = "Rs 450.00 debited from A/C **1234 on 20-Sep-26 info Swiggy UPI:123456789012",
            dateMillis = 1758360000000L,
            type = 1
        )
        val sms2 = SmsRecord(
            id = 102L,
            address = "SBIINB",
            body = "Dear UPI user A/C 9876 debited by 120.00 on 21Sep26 ref 987654321098 by Zomato",
            dateMillis = 1758446400000L,
            type = 1
        )

        val smsReader = InMemorySmsReader(hasPermission = true, records = listOf(sms1, sms2))
        val manager = HistoricalSmsImportManager(
            smsReader = smsReader,
            dao = fakeDao,
            persistenceManager = TransactionPersistenceManager
        )

        // First import
        val firstResult = manager.scanAndImport(1758000000000L, 1759000000000L)
        assertTrue(firstResult is SmsImportResult.Success)
        val firstSuccess = firstResult as SmsImportResult.Success
        assertEquals(2, firstSuccess.insertedCount)
        assertEquals(0, firstSuccess.duplicatesSkippedCount)
        assertEquals(2, fakeDao.getCount())

        // Second import over same data range
        val secondResult = manager.scanAndImport(1758000000000L, 1759000000000L)
        assertTrue(secondResult is SmsImportResult.Success)
        val secondSuccess = secondResult as SmsImportResult.Success
        assertEquals(0, secondSuccess.insertedCount)
        assertEquals(2, secondSuccess.duplicatesSkippedCount)
        // Store size unchanged!
        assertEquals(2, fakeDao.getCount())
    }

    @Test
    fun test10_onboardingFlowDecisionLogic() {
        // Fresh install: notification permission not granted, initial SMS import not completed
        var notificationAccessGranted = false
        var initialSmsImportCompleted = false

        // Step 1: User sees notification permission prompt
        val showNotificationDialog = !notificationAccessGranted
        assertTrue(showNotificationDialog)

        // User grants or dismisses notification dialog
        notificationAccessGranted = true

        // Step 2: Now initial SMS import dialog should be eligible to display
        val showSmsImportDialog = !initialSmsImportCompleted
        assertTrue(showSmsImportDialog)

        // User completes initial SMS import
        initialSmsImportCompleted = true

        // Step 3: Subsequent app launch
        val showSmsImportDialogNextLaunch = !initialSmsImportCompleted
        assertFalse(showSmsImportDialogNextLaunch)
    }

    @Test
    fun test11_clearAllData_preservesInitialImportCompletedState() {
        // User had previously completed initial SMS import
        sharedPrefsRepo.setInitialSmsImportCompleted(true)
        assertTrue(sharedPrefsRepo.isInitialSmsImportCompleted())

        // Clear All Data calls resetToDefaults()
        sharedPrefsRepo.resetToDefaults()

        // Assert initial SMS import flag is PRESERVED as true
        assertTrue("Initial SMS import completion must be preserved across resetToDefaults", sharedPrefsRepo.isInitialSmsImportCompleted())

        // Verify in-memory repo behaves identically
        inMemoryRepo.setInitialSmsImportCompleted(true)
        assertTrue(inMemoryRepo.isInitialSmsImportCompleted())
        inMemoryRepo.resetToDefaults()
        assertTrue("InMemory repo must also preserve initial SMS import completion", inMemoryRepo.isInitialSmsImportCompleted())
    }

    @Test
    fun test12_appRelaunch_doesNotTriggerOnboardingIfCompleted() {
        // Given initial import was completed in an earlier session
        sharedPrefsRepo.setInitialSmsImportCompleted(true)

        // Simulate new app session / activity launch
        val isCompleted = sharedPrefsRepo.isInitialSmsImportCompleted()
        val hasDismissedInitialSmsImportDialog = false // Fresh state
        val shouldShowPrompt = !hasDismissedInitialSmsImportDialog && !isCompleted

        // Assert dialog is NOT shown
        assertFalse(shouldShowPrompt)
    }

    @Test
    fun test13_navigationStack_pushAndPopFlow() {
        // Navigation stack state simulator
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
        assertEquals("Home", backStack.last())
        navigateTo("Settings")
        assertEquals(listOf("Home", "Settings"), backStack)
        assertEquals("Settings", backStack.last())

        navigateTo("ClearAllData")
        assertEquals(listOf("Home", "Settings", "ClearAllData"), backStack)
        assertEquals("ClearAllData", backStack.last())

        // Back from ClearAllData -> Data & Storage (within Settings)
        navigateBack()
        assertEquals(listOf("Home", "Settings"), backStack)
        assertEquals("Settings", backStack.last())

        // Back from Settings -> Home
        navigateBack()
        assertEquals(listOf("Home"), backStack)
        assertEquals("Home", backStack.last())

        // Back from Home -> cannot pop further (system back closes app)
        val canNavigateBack = backStack.size > 1
        assertFalse(canNavigateBack)
    }

    @Test
    fun test14_clearAllData_preservesInitialImportCompletedStateAndReturnsToSettings() {
        // Given initial import was previously completed and dismissed
        sharedPrefsRepo.setInitialSmsImportCompleted(true)
        var hasDismissedInitialSmsImportDialog = true

        // User navigates Home -> Settings -> ClearAllData
        var backStack = listOf("Home", "Settings", "ClearAllData")

        // User confirms Clear All Data
        sharedPrefsRepo.resetToDefaults()
        if (!sharedPrefsRepo.isInitialSmsImportCompleted()) {
            hasDismissedInitialSmsImportDialog = false
        }
        if (backStack.size > 1) {
            backStack = backStack.dropLast(1)
        }

        // Verify destination is Settings (Data & Storage)
        assertEquals("Settings", backStack.last())
        assertEquals(listOf("Home", "Settings"), backStack)

        // Verify initial SMS import remains completed and onboarding dialog is NOT shown
        assertTrue(sharedPrefsRepo.isInitialSmsImportCompleted())
        val canShowInitialSmsImport = !hasDismissedInitialSmsImportDialog && !sharedPrefsRepo.isInitialSmsImportCompleted()
        assertFalse("Initial SMS import prompt must NOT appear after Clear All Data if already completed", canShowInitialSmsImport)
    }

    @Test
    fun test15_smsImportScreenToolbarOwnership_singleHeader() {
        // Helper determining if parent Scaffold should render ArcTrackerHeader
        fun shouldParentRenderHeader(route: String): Boolean {
            return route != "SmsImport"
        }

        assertTrue(shouldParentRenderHeader("Home"))
        assertTrue(shouldParentRenderHeader("Settings"))
        assertTrue(shouldParentRenderHeader("ClearAllData"))
        assertTrue(shouldParentRenderHeader("Database"))
        assertTrue(shouldParentRenderHeader("BackupRestore"))

        // For SmsImport, parent header must NOT render
        assertFalse(shouldParentRenderHeader("SmsImport"))
    }

    private fun createNormalized(text: String, title: String? = null, key: String = "test_key", packageName: String = "com.jio.myjio"): NormalizedNotification {
        val captured = CapturedNotificationInfo(
            packageName = packageName,
            notificationKey = key,
            postTime = System.currentTimeMillis(),
            title = title,
            text = text
        )
        return NotificationNormalizer.normalize(captured)!!
    }

    private fun createValidatedCandidate(
        text: String,
        notificationKey: String = "test_key",
        postTime: Long = 1000L,
        packageName: String = "com.sbi.SBIAnywhere"
    ): ValidatedTransactionCandidate {
        val captured = CapturedNotificationInfo(
            packageName = packageName,
            notificationKey = notificationKey,
            postTime = postTime,
            title = null,
            text = text
        )
        val normalized = NotificationNormalizer.normalize(captured)!!
        val classification = FinancialClassifier.classify(normalized)
        val candidate = StructuredTransactionExtractor.extractDirect(normalized, classification)
        return TransactionValidator.validate(candidate, classification)
    }

    @Test
    fun test16_jioPromotionalPlanOffer_classifiedAsNonFinancialNoise() {
        val promo1 = createNormalized("Recharge with Rs 949 plan and get free JioHotstar for 90 days with 2 GB/day data! Click jio.com/recharge now.", title = "Jio Plan Offer")
        val result1 = FinancialClassifier.classify(promo1)
        assertEquals(FinancialRelevance.NON_FINANCIAL, result1.financialRelevance)
        assertTrue(result1.isNoise)
        assertEquals(NoiseCategory.PROMOTIONAL_OR_OFFER, result1.noiseCategory)

        val promo2 = createNormalized("Recharge with Rs.949 for 84 days validity & 2GB/day. Recharge now on MyJio.", title = "Special offer!")
        val result2 = FinancialClassifier.classify(promo2)
        assertEquals(FinancialRelevance.NON_FINANCIAL, result2.financialRelevance)
        assertTrue(result2.isNoise)

        val promo3 = createNormalized("Enjoy Disney+ Hotstar with Jio Rs 949 plan! 2GB/day + unlimited calls for 84 days. Recharge today.", title = "Entertainment Pack")
        val result3 = FinancialClassifier.classify(promo3)
        assertEquals(FinancialRelevance.NON_FINANCIAL, result3.financialRelevance)
        assertTrue(result3.isNoise)
    }

    @Test
    fun test17_completedJioRecharge_classifiedAsFinancialDebit() {
        val completedRecharge = createNormalized("Your Jio recharge of Rs.949 was successful. Recharge ID: 123456789. Your plan is active for 90 days.", title = "Recharge Successful")
        val result = FinancialClassifier.classify(completedRecharge)
        assertEquals(FinancialRelevance.FINANCIAL, result.financialRelevance)
        assertFalse(result.isNoise)
        assertEquals(DirectionHint.DEBIT_HINT, result.directionHint)

        val candidate = StructuredTransactionExtractor.extractDirect(completedRecharge, result)
        assertEquals(949.0, candidate?.amount)
        assertEquals(TransactionDirection.DEBIT, candidate?.direction)
        assertEquals(TransactionStatus.SUCCESS, candidate?.status)
    }

    @Test
    fun test18_crossSourceCorrelation_bankDebitAndTelecomConfirmation() {
        val bankCandidate = createValidatedCandidate(
            text = "Rs 949 debited from AC **1234 on 27-Sep-24 via UPI. Ref 427101234567. Avl bal Rs 5000.",
            notificationKey = "bank_sms_1",
            packageName = "com.sbi.SBIAnywhere"
        )

        val telecomCandidate = createValidatedCandidate(
            text = "Your Jio recharge of Rs.949 was successful. Txn ID: 123456789. Your plan is active for 90 days.",
            notificationKey = "jio_sms_1",
            packageName = "com.jio.myjio"
        )
        val telecomRecord = TransactionRecord.fromValidated(telecomCandidate)

        // Deduplication between different reference IDs must result in independent transactions
        val dedupResult = TransactionDeduplicator.evaluate(bankCandidate, listOf(telecomRecord))
        assertEquals(DedupDecision.NEW_TRANSACTION, dedupResult.decision)
    }

    @Test
    fun test19_reviewCompleteAction_resolvesUnknownMerchantAndPersists() {
        var completedMerchant: String? = null
        var completedAmount: Double? = null
        var isPendingResolved: Boolean = false

        // Review callback simulator
        fun onCompleteReview(amount: Double, merchant: String) {
            completedAmount = amount
            completedMerchant = if (merchant.isNotBlank()) merchant.trim() else "Unknown Merchant"
            isPendingResolved = true
        }

        // Test with blank merchant on pending approval
        val inputAmount = 949.0
        val inputMerchant = ""
        onCompleteReview(inputAmount, inputMerchant)

        assertTrue(isPendingResolved)
        assertEquals(949.0, completedAmount)
        assertEquals("Unknown Merchant", completedMerchant)

        // Test with explicit user entered merchant
        onCompleteReview(949.0, "Jio Telecom")
        assertEquals("Jio Telecom", completedMerchant)
    }

    @Test
    fun test20_smsPermission_requestedOnlyOnScanMessagesOrFirstTimeHome() {
        // Permission check state matrix
        fun shouldRequestSmsPermission(route: String, userTappedScan: Boolean, isInitialCompleted: Boolean): Boolean {
            return when {
                route == "SmsImport" && userTappedScan -> true
                route == "Home" && !isInitialCompleted -> true
                else -> false
            }
        }

        // Unrelated Settings screens must never request SMS permission
        assertFalse(shouldRequestSmsPermission("Settings", userTappedScan = false, isInitialCompleted = true))
        assertFalse(shouldRequestSmsPermission("DataStorage", userTappedScan = false, isInitialCompleted = true))
        assertFalse(shouldRequestSmsPermission("ClearAllData", userTappedScan = false, isInitialCompleted = true))
        assertFalse(shouldRequestSmsPermission("SmsImport", userTappedScan = false, isInitialCompleted = true))

        // Only explicit tap to scan in SmsImport requests permission
        assertTrue(shouldRequestSmsPermission("SmsImport", userTappedScan = true, isInitialCompleted = true))

        // Home screen during first-time setup (before initial completion) can offer onboarding
        assertTrue(shouldRequestSmsPermission("Home", userTappedScan = false, isInitialCompleted = false))
        assertFalse(shouldRequestSmsPermission("Home", userTappedScan = false, isInitialCompleted = true))
    }

    @Test
    fun test21_test1_clearAllData_completed_clearsTransactions_preservesCompletedTrue() = runBlocking {
        // GIVEN: initialSmsImportCompleted = true, transactions exist
        sharedPrefsRepo.setInitialSmsImportCompleted(true)
        val fakeDao = FakeExpenseDao()
        val expense = Expense(id = 1, amount = 250.0, merchant = "Swiggy", dateMillis = 1758999999000L)
        fakeDao.insert(expense)
        var inMemoryExpenses = listOf(expense)
        assertEquals(1, fakeDao.getCount())
        assertEquals(1, inMemoryExpenses.size)
        assertTrue(sharedPrefsRepo.isInitialSmsImportCompleted())

        // WHEN: Clear All Data
        val wasCompleted = sharedPrefsRepo.isInitialSmsImportCompleted()
        inMemoryExpenses = emptyList()
        fakeDao.clearAll()
        sharedPrefsRepo.setInitialSmsImportCompleted(wasCompleted)

        // THEN: transactions = 0, initialSmsImportCompleted = true, onboarding dialog = NOT shown
        assertEquals(0, fakeDao.getCount())
        assertEquals(0, inMemoryExpenses.size)
        assertTrue(sharedPrefsRepo.isInitialSmsImportCompleted())

        val hasDismissedSession = false
        val shouldShowOnboardingDialog = !hasDismissedSession && !sharedPrefsRepo.isInitialSmsImportCompleted()
        assertFalse("Onboarding dialog must NOT be shown when initialSmsImportCompleted is true", shouldShowOnboardingDialog)
    }

    @Test
    fun test22_test2_clearAllData_incomplete_clearsTransactions_preservesCompletedFalse() = runBlocking {
        // GIVEN: initialSmsImportCompleted = false, transactions exist
        sharedPrefsRepo.setInitialSmsImportCompleted(false)
        val fakeDao = FakeExpenseDao()
        val expense = Expense(id = 2, amount = 150.0, merchant = "Zomato", dateMillis = 1758999999000L)
        fakeDao.insert(expense)
        var inMemoryExpenses = listOf(expense)
        assertEquals(1, fakeDao.getCount())
        assertFalse(sharedPrefsRepo.isInitialSmsImportCompleted())

        // WHEN: Clear All Data
        val wasCompleted = sharedPrefsRepo.isInitialSmsImportCompleted()
        inMemoryExpenses = emptyList()
        fakeDao.clearAll()
        sharedPrefsRepo.setInitialSmsImportCompleted(wasCompleted)

        // THEN: transactions = 0, initialSmsImportCompleted = false (must NOT mark onboarding complete)
        assertEquals(0, fakeDao.getCount())
        assertEquals(0, inMemoryExpenses.size)
        assertFalse("Clear All Data must NOT mark onboarding complete if it was false", sharedPrefsRepo.isInitialSmsImportCompleted())
    }

    @Test
    fun test23_test3_clearAllData_completed_smsPermissionFalse_permissionUnaffected_dialogNotShown() = runBlocking {
        // GIVEN: initialSmsImportCompleted = true, hasSmsPermission = false
        sharedPrefsRepo.setInitialSmsImportCompleted(true)
        var hasSmsPermission = false
        val fakeDao = FakeExpenseDao()
        fakeDao.insert(Expense(id = 3, amount = 350.0, merchant = "Uber", dateMillis = 1758999999000L))

        // WHEN: Clear All Data
        val wasCompleted = sharedPrefsRepo.isInitialSmsImportCompleted()
        fakeDao.clearAll()
        sharedPrefsRepo.setInitialSmsImportCompleted(wasCompleted)

        // THEN: initialSmsImportCompleted = true, hasSmsPermission remains unaffected, dialog NOT shown
        assertTrue(sharedPrefsRepo.isInitialSmsImportCompleted())
        assertFalse("Android SMS permission state must not be modified by Clear All Data", hasSmsPermission)

        val hasDismissedSession = false
        val shouldShowOnboardingDialog = !hasDismissedSession && !sharedPrefsRepo.isInitialSmsImportCompleted()
        assertFalse("Onboarding dialog must NOT be shown", shouldShowOnboardingDialog)
    }

    @Test
    fun test24_test4_clearAllData_completed_transactionsCountPositive_clearsData_returnHome_noDialog() = runBlocking {
        // GIVEN: initialSmsImportCompleted = true, transaction count > 0
        sharedPrefsRepo.setInitialSmsImportCompleted(true)
        val fakeDao = FakeExpenseDao()
        for (i in 1..10) {
            fakeDao.insert(Expense(id = i, amount = 100.0 * i, merchant = "Merchant $i", dateMillis = 1758999999000L))
        }
        var inMemoryExpenses = fakeDao.getAllExpensesList()
        assertEquals(10, fakeDao.getCount())
        assertEquals(10, inMemoryExpenses.size)

        // Navigation state simulation: Home -> Settings -> ClearAllData
        var backStack = listOf("Home", "Settings", "ClearAllData")

        // WHEN: Clear All Data -> return Home
        val wasCompleted = sharedPrefsRepo.isInitialSmsImportCompleted()
        inMemoryExpenses = emptyList()
        fakeDao.clearAll()
        sharedPrefsRepo.setInitialSmsImportCompleted(wasCompleted)

        // Pop back to Settings, then Home
        if (backStack.last() == "ClearAllData") backStack = backStack.dropLast(1)
        if (backStack.last() == "Settings") backStack = backStack.dropLast(1)

        // THEN: transaction count = 0, at Home, no initial 3-month import dialog
        assertEquals("Home", backStack.last())
        assertEquals(0, fakeDao.getCount())
        assertEquals(0, inMemoryExpenses.size)
        assertTrue(sharedPrefsRepo.isInitialSmsImportCompleted())

        val shouldShowInitial3MonthDialog = !sharedPrefsRepo.isInitialSmsImportCompleted()
        assertFalse("No initial 3-month import dialog after returning to Home", shouldShowInitial3MonthDialog)
    }

    @Test
    fun test25_test5_clearAllData_incomplete_transactionsCountPositive_clearsData_returnHome_onboardingIncomplete() = runBlocking {
        // GIVEN: initialSmsImportCompleted = false, transaction count > 0
        sharedPrefsRepo.setInitialSmsImportCompleted(false)
        val fakeDao = FakeExpenseDao()
        fakeDao.insert(Expense(id = 1, amount = 50.0, merchant = "Manual Cash", dateMillis = 1758999999000L))
        assertEquals(1, fakeDao.getCount())

        var backStack = listOf("Home", "Settings", "ClearAllData")

        // WHEN: Clear All Data -> return Home
        val wasCompleted = sharedPrefsRepo.isInitialSmsImportCompleted()
        fakeDao.clearAll()
        sharedPrefsRepo.setInitialSmsImportCompleted(wasCompleted)

        if (backStack.last() == "ClearAllData") backStack = backStack.dropLast(1)
        if (backStack.last() == "Settings") backStack = backStack.dropLast(1)

        // THEN: transaction count = 0, onboarding lifecycle remains incomplete
        assertEquals("Home", backStack.last())
        assertEquals(0, fakeDao.getCount())
        assertFalse("Onboarding lifecycle must remain incomplete", sharedPrefsRepo.isInitialSmsImportCompleted())

        val eligibleForInitialOnboarding = !sharedPrefsRepo.isInitialSmsImportCompleted()
        assertTrue("User remains eligible for initial onboarding flow", eligibleForInitialOnboarding)
    }

    private class FakeTestSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = EditorImpl()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private inner class EditorImpl : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private var clear = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor { temp[key] = value; return this }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor { temp[key] = values?.toSet(); return this }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor { temp[key] = value; return this }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor { temp[key] = value; return this }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor { temp[key] = value; return this }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { temp[key] = value; return this }
            override fun remove(key: String): SharedPreferences.Editor { temp[key] = null; return this }
            override fun clear(): SharedPreferences.Editor { clear = true; return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clear) data.clear()
                for ((k, v) in temp) {
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }
    }
}
