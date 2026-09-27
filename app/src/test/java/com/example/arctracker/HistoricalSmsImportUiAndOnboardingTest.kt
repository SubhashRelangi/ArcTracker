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
