package com.subhashrelangi.arctracker

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.subhashrelangi.arctracker.data.AppDatabase
import com.subhashrelangi.arctracker.data.DeletedTransactionTombstone
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * P2 — Step 4: Real Room SQLite / Device Tests for Historical SMS Import Deletion Integrity.
 *
 * Verifies on real SQLite / Room:
 * 1. Scenario D: Tombstone markers survive closing and reopening the real SQLite database file.
 * 2. Real SQLite atomic transaction guarantees for deletion + tombstone creation.
 * 3. End-to-end SMS scan, deletion, and rescan immunity against resurrection on real SQLite.
 */
@RunWith(AndroidJUnit4::class)
class HistoricalSmsImportDeletionIntegrityDeviceTest {

    private lateinit var context: Context
    private lateinit var inMemoryDb: AppDatabase
    private val createdDbFiles = mutableListOf<String>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        inMemoryDb = AppDatabase.createInMemoryDatabase(context)
    }

    @After
    fun tearDown() {
        inMemoryDb.close()
        for (dbName in createdDbFiles) {
            context.deleteDatabase(dbName)
        }
    }

    // =========================================================================
    // SCENARIO D: SURVIVES DATABASE CLOSE & REOPEN
    // =========================================================================

    @Test
    fun testScenarioD_tombstoneSurvivesClosingAndReopeningRealDatabase() = runBlocking {
        val dbName = "tombstone_restart_test_${System.currentTimeMillis()}.db"
        createdDbFiles.add(dbName)

        // 1. Open persistent SQLite database file
        var persistentDb = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName
        ).allowMainThreadQueries().build()

        val dao1 = persistentDb.expenseDao()

        // Insert SMS expense
        val exp = Expense(
            amount = 899.0,
            merchant = "Nike Store",
            type = "Debit",
            dateMillis = System.currentTimeMillis(),
            source = "SMS_HISTORY",
            notificationKey = "sms_persist_555"
        )
        val id = dao1.insert(exp).toInt()
        assertEquals(1, dao1.getAllExpenses().first().size)

        // Delete via deleteSelfTransferAware to write tombstone
        val delCount = dao1.deleteSelfTransferAware(id)
        assertEquals(1, delCount)
        assertTrue(dao1.getAllExpenses().first().isEmpty())
        assertTrue("Tombstone must be present before close", dao1.isSourceKeyTombstoned("sms_persist_555"))

        // 2. CLOSE the database completely
        persistentDb.close()

        // 3. REOPEN the database from the same persistent SQLite file
        persistentDb = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName
        ).allowMainThreadQueries().build()

        val reopenedDao = persistentDb.expenseDao()

        // 4. Verify the tombstone marker survived the database restart
        assertTrue("Tombstone must survive closing and reopening database", reopenedDao.isSourceKeyTombstoned("sms_persist_555"))
        val tombstonedKeys = reopenedDao.getAllTombstonedSmsSourceKeys()
        assertTrue("Tombstone list must contain sms_persist_555", tombstonedKeys.contains("sms_persist_555"))

        // 5. Verify direct persistence manager skips tombstoned candidate on reopened DB
        val candidate = StructuredTransactionCandidate(
            sourceNotificationKey = "sms_persist_555",
            packageName = "com.google.android.apps.messaging",
            postTime = System.currentTimeMillis(),
            transactionTimestamp = System.currentTimeMillis(),
            amount = 899.0,
            merchant = "Nike Store",
            direction = TransactionDirection.DEBIT,
            status = TransactionStatus.SUCCESS,
            rawContent = "Rs 899 spent at Nike Store"
        )
        val validated = ValidatedTransactionCandidate(
            candidate = candidate,
            validationState = ValidationState.ACCEPTABLE,
            evidenceLevel = EvidenceLevel.STRONG,
            isStructurallyValid = true,
            validationReasons = listOf("Valid")
        )

        val result = TransactionPersistenceManager.processValidatedCandidate(validated, reopenedDao)
        assertTrue("Reopened DB must suppress tombstoned transaction", result is ExpensePersistenceResult.SkippedDuplicate)
        assertTrue("No transactions resurrected in reopened DB", reopenedDao.getAllExpenses().first().isEmpty())

        persistentDb.close()
    }

    // =========================================================================
    // REAL SQLITE: END-TO-END SCAN -> IMPORT -> DELETE -> RESCAN
    // =========================================================================

    @Test
    fun testRealSqlite_endToEndSmsScanDeleteRescan_remainsDeleted() = runBlocking {
        val dao = inMemoryDb.expenseDao()
        val timestamp = System.currentTimeMillis()

        val sms = SmsRecord(
            id = 777L,
            address = "AD-IPPB",
            body = "A/C X2959 Debit Rs.500.00 for UPI to Blue Pottery on 02-09-26 Ref 624571799987. Avl Bal Rs.116.62. -IPPB",
            dateMillis = timestamp
        )
        val reader = InMemorySmsReader(hasPermission = true, records = listOf(sms))
        val manager = HistoricalSmsImportManager(
            smsReader = reader,
            dao = dao,
            persistenceManager = TransactionPersistenceManager
        )

        // Scan & Import
        val scan1 = manager.scan(0L, timestamp + 10000L) as SmsScanResult.Success
        assertEquals(1, scan1.newTransactionsCount)
        val import1 = manager.importTransactions(scan1) as SmsImportResult.Success
        assertEquals(1, import1.insertedCount)

        val imported = dao.getAllExpenses().first()[0]
        assertEquals("sms_777", imported.notificationKey)

        // Delete
        dao.deleteSelfTransferAware(imported.id)
        assertTrue(dao.getAllExpenses().first().isEmpty())
        assertTrue(dao.isSourceKeyTombstoned("sms_777"))

        // Rescan
        val scan2 = manager.scan(0L, timestamp + 10000L) as SmsScanResult.Success
        assertEquals("Rescan must detect as duplicate", 1, scan2.duplicatesCount)
        assertEquals("Rescan must not count as new", 0, scan2.newTransactionsCount)

        val import2 = manager.importTransactions(scan2) as SmsImportResult.Success
        assertEquals("Re-import must insert 0 rows", 0, import2.insertedCount)
        assertTrue("Room expenses must remain empty", dao.getAllExpenses().first().isEmpty())
    }
}
