package com.example.arctracker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arctracker.data.*
import com.example.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Milestone 3: Historical SMS -> Known Financial Account Synchronization Real Device Tests
 * Executing on connected physical Samsung Galaxy A34 5G.
 */
@RunWith(AndroidJUnit4::class)
class HistoricalSmsAccountRegistryDeviceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var accountDao: KnownFinancialAccountDao
    private lateinit var repository: KnownFinancialAccountRepository
    private lateinit var synchronizer: HistoricalSmsAccountRegistrySynchronizer

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = AppDatabase.createInMemoryDatabase(context)
        accountDao = database.knownFinancialAccountDao()
        repository = KnownFinancialAccountRepository(accountDao)
        synchronizer = HistoricalSmsAccountRegistrySynchronizer(repository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================================================
    // 1. Historical SMS identity -> registry persistence
    // ==================================================
    @Test
    fun test01_device_historicalSmsIdentityToRegistryPersistence() = runBlocking {
        val identity = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH,
            evidenceSource = "bank=BODY_EXPLICIT;suffix=BODY_ACCOUNT"
        )

        val synced = synchronizer.synchronize(identity)
        assertNotNull(synced)
        assertEquals("hdfc_bank_account_9020", synced?.id)

        // Read directly from Room database
        val inDb = accountDao.getById("hdfc_bank_account_9020")
        assertNotNull("Record must be persisted in real Room DB", inDb)
        assertEquals("hdfc", inDb?.institutionId)
        assertEquals("HDFC Bank", inDb?.institutionName)
        assertEquals("9020", inDb?.accountSuffix)
        assertEquals(AccountSource.HISTORICAL_SMS, inDb?.source)
    }

    // ==================================================
    // 2. Same suffix / different bank separation (hdfc_9020 vs apgb_9020)
    // ==================================================
    @Test
    fun test02_device_sameSuffixDifferentBankSeparation() = runBlocking {
        val hdfc = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val apgb = FinancialAccountIdentity(
            institutionId = "apgb",
            institutionName = "Andhra Pragathi Grameena Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )

        synchronizer.synchronize(hdfc)
        synchronizer.synchronize(apgb)

        assertEquals(2, accountDao.getCount())
        val hdfcRecord = accountDao.getById("hdfc_bank_account_9020")
        val apgbRecord = accountDao.getById("apgb_bank_account_9020")

        assertNotNull(hdfcRecord)
        assertNotNull(apgbRecord)
        assertEquals("hdfc", hdfcRecord?.institutionId)
        assertEquals("apgb", apgbRecord?.institutionId)

        val suffix9020Accounts = accountDao.findBySuffix("9020")
        assertEquals(2, suffix9020Accounts.size)
    }

    // ==================================================
    // 3. Same bank / different suffix separation (hdfc_9020 vs hdfc_4381)
    // ==================================================
    @Test
    fun test03_device_sameBankDifferentSuffixSeparation() = runBlocking {
        val hdfc1 = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "9020",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )
        val hdfc2 = FinancialAccountIdentity(
            institutionId = "hdfc",
            institutionName = "HDFC Bank",
            accountSuffix = "4381",
            instrumentType = InstrumentType.BANK_ACCOUNT
        )

        synchronizer.synchronize(hdfc1)
        synchronizer.synchronize(hdfc2)

        assertEquals(2, accountDao.getCount())
        assertNotNull(accountDao.getById("hdfc_bank_account_9020"))
        assertNotNull(accountDao.getById("hdfc_bank_account_4381"))

        val hdfcAccounts = accountDao.getAll().filter { it.institutionId == "hdfc" }
        assertEquals(2, hdfcAccounts.size)
    }

    // ==================================================
    // 4. Registry survives Room persistence
    // ==================================================
    @Test
    fun test04_device_registrySurvivesRoomPersistence() = runBlocking {
        val identity = FinancialAccountIdentity(
            institutionId = "sbi",
            institutionName = "State Bank of India",
            accountSuffix = "1234",
            instrumentType = InstrumentType.BANK_ACCOUNT,
            confidence = IdentityConfidence.HIGH
        )
        synchronizer.synchronize(identity)

        // Verify across independent DAO queries
        val record = accountDao.getById("sbi_bank_account_1234")
        assertNotNull(record)
        assertEquals("sbi", record?.institutionId)
        assertEquals("1234", record?.accountSuffix)
        assertEquals(1, accountDao.getCount())
    }

    // ==================================================
    // 5. Selected account import creates expected registry record
    // ==================================================
    @Test
    fun test05_device_selectedAccountImportCreatesExpectedRegistryRecord() = runBlocking {
        val mockSms = InMemorySmsReader(
            records = listOf(
                SmsRecord(
                    id = 101L,
                    address = "AD-HDFCBK",
                    body = "Rs.1,200.00 debited from A/c XX9020 to Swiggy. UPI Ref: 987654321",
                    dateMillis = 1711360000000L
                ),
                SmsRecord(
                    id = 102L,
                    address = "VK-APGBANK",
                    body = "Rs.500.00 credited to A/c XX1065 from Friend. UPI Ref: 123456789",
                    dateMillis = 1711360000000L
                )
            )
        )

        val expenseDao = database.expenseDao()
        val manager = HistoricalSmsImportManager(
            smsReader = mockSms,
            dao = expenseDao,
            synchronizer = synchronizer
        )

        // Scan returns groups
        val scanResult = manager.scan(0L, System.currentTimeMillis())
        assertTrue(scanResult is SmsScanResult.Success)
        val success = scanResult as SmsScanResult.Success
        assertEquals(2, success.accountGroups.size)

        // Before import, registry is untouched
        assertEquals(0, accountDao.getCount())

        // User selects ONLY HDFC XX9020
        val selected = setOf("hdfc_bank_account_9020")
        val importResult = manager.importTransactions(success, selected)
        assertTrue(importResult is SmsImportResult.Success)
        val importSuccess = importResult as SmsImportResult.Success
        assertEquals(1, importSuccess.insertedCount)

        // Verify registry has ONLY selected account
        assertEquals(1, accountDao.getCount())
        assertNotNull(accountDao.getById("hdfc_bank_account_9020"))
        assertNull(accountDao.getById("apgb_bank_account_1065"))
    }

    // ==================================================
    // 6. Unselected account does NOT become an imported registry account
    // ==================================================
    @Test
    fun test06_device_unselectedAccountDoesNotBecomeImportedRegistryAccount() = runBlocking {
        val mockSms = InMemorySmsReader(
            records = listOf(
                SmsRecord(
                    id = 201L,
                    address = "AD-HDFCBK",
                    body = "Rs.500 debited from A/c XX9020 to Store. UPI Ref: 1111",
                    dateMillis = 1711360000000L
                ),
                SmsRecord(
                    id = 202L,
                    address = "AD-HDFCBK",
                    body = "Rs.250 debited from A/c XX4381 to Store. UPI Ref: 2222",
                    dateMillis = 1711360000000L
                )
            )
        )

        val expenseDao = database.expenseDao()
        val manager = HistoricalSmsImportManager(
            smsReader = mockSms,
            dao = expenseDao,
            synchronizer = synchronizer
        )

        val scanResult = manager.scan(0L, System.currentTimeMillis()) as SmsScanResult.Success

        // User selects ONLY XX4381, leaving XX9020 UNSELECTED
        val selected = setOf("hdfc_bank_account_4381")
        manager.importTransactions(scanResult, selected)

        // XX4381 exists, XX9020 must NOT exist in the registry
        assertNotNull("Selected account must be in registry", accountDao.getById("hdfc_bank_account_4381"))
        assertNull("Unselected account must NOT be in registry", accountDao.getById("hdfc_bank_account_9020"))
        assertEquals(1, accountDao.getCount())
    }

    // ==================================================
    // 7. Existing historical transaction import and notification tracking unaffected
    // ==================================================
    @Test
    fun test07_device_existingHistoricalImportAndTrackingUnaffected() = runBlocking {
        val mockSms = InMemorySmsReader(
            records = listOf(
                SmsRecord(
                    id = 301L,
                    address = "AD-HDFCBK",
                    body = "Rs.999.00 debited from A/c XX9020 to Netflix. UPI Ref: 999888777",
                    dateMillis = 1711360000000L
                )
            )
        )

        val expenseDao = database.expenseDao()
        val manager = HistoricalSmsImportManager(
            smsReader = mockSms,
            dao = expenseDao,
            synchronizer = synchronizer
        )

        val scanResult = manager.scan(0L, System.currentTimeMillis()) as SmsScanResult.Success
        val importResult = manager.importTransactions(scanResult)
        assertTrue(importResult is SmsImportResult.Success)
        val importSuccess = importResult as SmsImportResult.Success
        assertEquals(1, importSuccess.insertedCount)
        val allExpenses = expenseDao.getAllExpensesList()
        assertEquals(1, allExpenses.size)
        assertEquals(999.0, allExpenses[0].amount, 0.001)
        assertEquals("sms_301", allExpenses[0].notificationKey)

        // Account registry synchronized cleanly in KnownFinancialAccountDao
        assertEquals(1, accountDao.getCount())
        val account = accountDao.getById("hdfc_bank_account_9020")
        assertNotNull(account)
        assertEquals("hdfc", account?.institutionId)
        assertEquals("9020", account?.accountSuffix)
    }
}
