package com.example.arctracker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arctracker.data.AppDatabase
import com.example.arctracker.data.Expense
import com.example.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Historical SMS Scan Pipeline on-device verification running on connected physical device.
 *
 * Verifies on real Android runtime:
 * 1. Scan produces candidate transactions with strictly ZERO database writes.
 * 2. Scan identifies duplicates against existing Room records without mutating Room.
 * 3. Only the explicit importTransactions call persists transactions into Room.
 */
@RunWith(AndroidJUnit4::class)
class HistoricalSmsScanPipelineDeviceTest {

    private lateinit var context: Context
    private lateinit var testDb: AppDatabase
    private lateinit var reader: InMemorySmsReader
    private lateinit var manager: HistoricalSmsImportManager

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testDb = AppDatabase.createInMemoryDatabase(context)
        reader = InMemorySmsReader(hasPermission = true, records = emptyList())
        manager = HistoricalSmsImportManager(
            smsReader = reader,
            dao = testDb.expenseDao(),
            persistenceManager = TransactionPersistenceManager
        )
    }

    @After
    fun tearDown() {
        testDb.close()
    }

    @Test
    fun test01_device_scanProducesCandidates_withStrictlyZeroDatabaseWrites() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        reader.records = listOf(
            SmsRecord(
                id = 1001L,
                address = "AD-HDFCBK",
                body = "Rs 450.00 debited from A/C **1234 on 28-Sep-26 to Swiggy. UPI: 123456789012",
                dateMillis = 1774000000000L
            ),
            SmsRecord(
                id = 1002L,
                address = "AD-SBIINB",
                body = "Your a/c no. XXXXXX1234 is credited by Rs. 2,500.00 on 28-Sep-26 by transfer from John. Ref 987654321098",
                dateMillis = 1774000050000L
            ),
            SmsRecord(
                id = 1003L,
                address = "+919876543210",
                body = "Hey, are we having lunch today at 1pm?",
                dateMillis = 1774000100000L
            )
        )

        val result = manager.scan(0L, Long.MAX_VALUE)
        assertTrue("Scan must succeed", result is SmsScanResult.Success)

        val success = result as SmsScanResult.Success
        assertEquals(3, success.messagesScanned)
        assertEquals(2, success.financialMessages)
        assertEquals(1, success.nonFinancialMessages)
        assertEquals(2, success.transactionCandidatesCount)

        // STRICT INVARIANT: ZERO database writes during scan
        assertEquals(0, dao.getCount())
        assertTrue(dao.getAllExpensesList().isEmpty())
        assertTrue(dao.getPendingExpensesList().isEmpty())
    }

    @Test
    fun test02_device_preExistingRecordsRemainUnmodifiedDuringScan() = runBlocking {
        val dao = testDb.expenseDao()
        dao.insert(
            Expense(
                amount = 999.0,
                merchant = "Pre-existing Expense",
                dateMillis = 1773000000000L,
                notificationKey = "sms_999",
                source = "SMS_HISTORY"
            )
        )
        assertEquals(1, dao.getCount())

        reader.records = listOf(
            SmsRecord(
                id = 999L, // Duplicate of existing DB record
                address = "AD-HDFCBK",
                body = "Rs 999.00 debited from A/C **1234 on 27-Sep-26 to Store. Ref 111222333444",
                dateMillis = 1773000000000L
            ),
            SmsRecord(
                id = 1000L, // New transaction
                address = "AD-HDFCBK",
                body = "Rs 150.00 debited from A/C **1234 on 28-Sep-26 to Cafe. Ref 555666777888",
                dateMillis = 1774000000000L
            )
        )

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(2, result.messagesScanned)
        assertEquals(1, result.newTransactionsCount)
        assertEquals(1, result.duplicatesCount)

        // Database remains strictly untouched
        assertEquals(1, dao.getCount())
        val record = dao.getAllExpensesList().first()
        assertEquals("Pre-existing Expense", record.merchant)
        assertEquals(999.0, record.amount, 0.001)
    }

    @Test
    fun test03_device_onlyExplicitImportPersistsToRoomDatabase() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        reader.records = listOf(
            SmsRecord(
                id = 2001L,
                address = "AD-IPPB",
                body = "A/C X2959 Debit Rs.350.00 for UPI to Grocery Mart on 28-09-26 Ref 624571799987. Avl Bal Rs.500.00. -IPPB",
                dateMillis = 1774000000000L
            )
        )

        // Phase 1: Scan (produces candidates, 0 writes)
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(0, dao.getCount())

        // Phase 2: Import (persists candidates)
        val importResult = manager.importTransactions(scanResult)
        assertTrue(importResult is SmsImportResult.Success)

        val successImport = importResult as SmsImportResult.Success
        assertEquals(1, successImport.insertedCount)

        // Phase 3: Verify Room persistence
        assertEquals(1, dao.getCount())
        val saved = dao.getAllExpensesList().first()
        assertEquals(350.0, saved.amount, 0.001)
        assertEquals("Grocery Mart", saved.merchant)
        assertEquals("sms_2001", saved.notificationKey)
        assertEquals("SMS_HISTORY", saved.source)
    }

    @Test
    fun test04_device_accountIdentityAndStableGrouping_withZeroDatabaseWrites() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        reader.records = listOf(
            SmsRecord(
                id = 3001L,
                address = "AD-HDFCBK",
                body = "A/c XX4381 debited by HDFC Bank Rs. 450.00 on 28-Sep-26 to Store1",
                dateMillis = 1774000000000L
            ),
            SmsRecord(
                id = 3002L,
                address = "AD-HDFCBK",
                body = "A/c XX4381 credited by HDFC Bank INR 1,200.00 on 28-Sep-26 by transfer",
                dateMillis = 1774000050000L
            ),
            SmsRecord(
                id = 3003L,
                address = "AD-SBIINB",
                body = "SBI A/c XX7724 credited INR 2,500.00 on 28-Sep-26 by transfer Ref 998811",
                dateMillis = 1774000100000L
            ),
            SmsRecord(
                id = 3004L,
                address = "+919876543210",
                body = "Debited Rs. 300.00 on 28-Sep-26 to Cafe Ref 554433",
                dateMillis = 1774000150000L
            )
        )

        val result = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        // Verify account groups
        assertEquals(3, result.accountGroups.size)

        val hdfcGroup = result.accountGroups.first { it.groupId == "hdfc_bank_account_4381" }
        assertEquals(2, hdfcGroup.transactionCount)
        assertEquals(450.0, hdfcGroup.totalDebit, 0.001)
        assertEquals(1200.0, hdfcGroup.totalCredit, 0.001)
        assertEquals("HDFC Bank ••••4381", hdfcGroup.identity.getDisplayName())

        val sbiGroup = result.accountGroups.first { it.groupId == "sbi_bank_account_7724" }
        assertEquals(1, sbiGroup.transactionCount)
        assertEquals(0.0, sbiGroup.totalDebit, 0.001)
        assertEquals(2500.0, sbiGroup.totalCredit, 0.001)

        val unidentifiedGroup = result.accountGroups.first { it.groupId == "unidentified_account" }
        assertEquals(1, unidentifiedGroup.transactionCount)
        assertEquals(300.0, unidentifiedGroup.totalDebit, 0.001)

        // Strict invariant: ZERO Room database writes during scan & grouping
        assertEquals(0, dao.getCount())
        assertTrue(dao.getAllExpensesList().isEmpty())
    }

    @Test
    fun test05_device_selectedAccountImport_filtersAndPersistsOnlyChosenGroups() = runBlocking {
        val dao = testDb.expenseDao()
        assertEquals(0, dao.getCount())

        reader.records = listOf(
            SmsRecord(
                id = 4001L,
                address = "AD-HDFCBK",
                body = "A/c XX4381 debited by HDFC Bank Rs. 450.00 on 28-Sep-26 to Swiggy Ref 1111",
                dateMillis = 1774000000000L
            ),
            SmsRecord(
                id = 4002L,
                address = "AD-HDFCBK",
                body = "A/c XX4381 debited by HDFC Bank Rs. 150.00 on 28-Sep-26 to Uber Ref 2222",
                dateMillis = 1774000050000L
            ),
            SmsRecord(
                id = 4003L,
                address = "AD-SBIINB",
                body = "SBI A/c XX7724 credited INR 2,500.00 on 28-Sep-26 by transfer Ref 3333",
                dateMillis = 1774000100000L
            ),
            SmsRecord(
                id = 4004L,
                address = "+919876543210",
                body = "Debited Rs. 300.00 on 28-Sep-26 to Cafe Ref 4444",
                dateMillis = 1774000150000L
            )
        )

        // 1. Scan phase: 4 items, 3 groups, strictly 0 DB writes
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(3, scanResult.accountGroups.size)
        assertEquals(0, dao.getCount())

        // 2. Import phase: Select ONLY HDFC
        val hdfcGroupId = "hdfc_bank_account_4381"
        val importResult1 = manager.importTransactions(
            scanResult,
            selectedGroupIds = setOf(hdfcGroupId)
        ) as SmsImportResult.Success

        assertEquals(2, importResult1.insertedCount)
        assertEquals(2, dao.getCount())

        // Verify only HDFC items exist in Room
        val persisted1 = dao.getAllExpensesList()
        assertEquals(2, persisted1.size)
        assertTrue(persisted1.all { it.note?.contains("HDFC Bank") == true || it.note?.contains("4381") == true })
        assertFalse(persisted1.any { it.note?.contains("7724") == true || it.note?.contains("SBI") == true })

        // 3. Idempotency test: Re-importing HDFC inserts 0 new rows
        val reimportResult = manager.importTransactions(
            scanResult,
            selectedGroupIds = setOf(hdfcGroupId)
        ) as SmsImportResult.Success

        assertEquals(0, reimportResult.insertedCount)
        assertEquals(2, reimportResult.duplicatesSkippedCount)
        assertEquals(2, dao.getCount())

        // 4. Partial subsequent import: Now import SBI
        val sbiGroupId = "sbi_bank_account_7724"
        val importResult2 = manager.importTransactions(
            scanResult,
            selectedGroupIds = setOf(sbiGroupId)
        ) as SmsImportResult.Success

        assertEquals(1, importResult2.insertedCount)
        assertEquals(3, dao.getCount()) // 2 HDFC + 1 SBI
        assertEquals(2500.0, importResult2.totalIncomeImported, 0.001)
    }
}
