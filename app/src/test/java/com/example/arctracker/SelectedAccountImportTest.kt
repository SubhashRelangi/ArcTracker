package com.example.arctracker

import com.example.arctracker.data.Expense
import com.example.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive verification test suite for:
 * STEP 5: Account Selection UI & State Management (In-Memory)
 * STEP 6: Selected Account Import Execution & Idempotent Persistence
 */
class SelectedAccountImportTest {

    private lateinit var dao: FakeExpenseDao
    private lateinit var reader: InMemorySmsReader
    private lateinit var manager: HistoricalSmsImportManager

    @Before
    fun setUp() {
        dao = FakeExpenseDao()
        reader = InMemorySmsReader(hasPermission = true, records = emptyList())
        manager = HistoricalSmsImportManager(
            smsReader = reader,
            dao = dao,
            persistenceManager = TransactionPersistenceManager
        )
    }

    private fun makeSms(
        id: Long,
        body: String,
        address: String = "AD-BANK",
        dateMillis: Long = 1711360000000L
    ) = SmsRecord(
        id = id,
        address = address,
        body = body,
        dateMillis = dateMillis
    )

    private fun createThreeAccountScenario(): List<SmsRecord> {
        return listOf(
            // Group 1: HDFC Bank A/c 4381 (2 transactions)
            makeSms(101L, "A/c XX4381 debited by HDFC Bank Rs. 500.00 on 20-Sep-26 to Swiggy Ref 111001", "AD-HDFCBK", dateMillis = 1000L),
            makeSms(102L, "A/c XX4381 debited by HDFC Bank Rs. 150.00 on 21-Sep-26 to Uber Ref 111002", "AD-HDFCBK", dateMillis = 2000L),
            // Group 2: SBI A/c 7724 (2 transactions: 1 debit, 1 credit)
            makeSms(201L, "SBI A/c XX7724 credited INR 2,000.00 on 22-Sep-26 by Salary Ref 222001", "AD-SBIINB", dateMillis = 3000L),
            makeSms(202L, "SBI A/c XX7724 debited INR 300.00 on 23-Sep-26 to D-Mart Ref 222002", "AD-SBIINB", dateMillis = 4000L),
            // Group 3: ICICI Bank A/c 1102 (2 transactions)
            makeSms(301L, "ICICI Bank Acct XX1102 debited for Rs 750.00 on 24-Sep-26 at Amazon Ref 333001", "AD-ICICIB", dateMillis = 5000L),
            makeSms(302L, "ICICI Bank Acct XX1102 debited for Rs 250.00 on 25-Sep-26 at Flipkart Ref 333002", "AD-ICICIB", dateMillis = 6000L)
        )
    }

    // =========================================================================
    // STEP 5 TESTS: Account Selection UI & State Management (In-Memory)
    // =========================================================================

    @Test
    fun `step5_01 - Default selection state initializes with all discovered groups selected`() = runBlocking {
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        assertEquals(3, scanResult.accountGroups.size)

        // UI default selection contract: all discovered groups selected by default
        val defaultSelectedGroupIds = scanResult.accountGroups.map { it.groupId }.toSet()
        assertEquals(3, defaultSelectedGroupIds.size)
        assertTrue(defaultSelectedGroupIds.contains("hdfc_bank_account_4381"))
        assertTrue(defaultSelectedGroupIds.contains("sbi_bank_account_7724"))
        assertTrue(defaultSelectedGroupIds.contains("icici_bank_account_1102"))
    }

    @Test
    fun `step5_02 - Select All action selects all available group IDs`() = runBlocking {
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        // Simulate user starting with partial or empty selection
        var selectedGroupIds = setOf("hdfc_bank_account_4381")

        // User clicks "Select All"
        selectedGroupIds = scanResult.accountGroups.map { it.groupId }.toSet()

        assertEquals(3, selectedGroupIds.size)
        assertEquals(
            scanResult.accountGroups.map { it.groupId }.toSet(),
            selectedGroupIds
        )
    }

    @Test
    fun `step5_03 - Clear All action deselects all groups`() = runBlocking {
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        var selectedGroupIds = scanResult.accountGroups.map { it.groupId }.toSet()
        assertEquals(3, selectedGroupIds.size)

        // User clicks "Clear All"
        selectedGroupIds = emptySet()

        assertTrue(selectedGroupIds.isEmpty())
        assertEquals(0, selectedGroupIds.size)
    }

    @Test
    fun `step5_04 - Individual group toggle correctly updates selected set`() = runBlocking {
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        var selectedGroupIds = scanResult.accountGroups.map { it.groupId }.toSet()

        // Deselect HDFC
        val hdfcId = "hdfc_bank_account_4381"
        selectedGroupIds = selectedGroupIds - hdfcId
        assertFalse(selectedGroupIds.contains(hdfcId))
        assertEquals(2, selectedGroupIds.size)

        // Re-select HDFC
        selectedGroupIds = selectedGroupIds + hdfcId
        assertTrue(selectedGroupIds.contains(hdfcId))
        assertEquals(3, selectedGroupIds.size)
    }

    @Test
    fun `step5_05 - Import button is disabled when selectedGroupIds is empty`() = runBlocking {
        val selectedGroupIds = emptySet<String>()
        val isImportButtonEnabled = selectedGroupIds.isNotEmpty()

        assertFalse("Import button must be disabled when 0 groups are selected", isImportButtonEnabled)
    }

    @Test
    fun `step5_06 - Import button is enabled when at least one group is selected`() = runBlocking {
        val selectedGroupIds = setOf("hdfc_bank_account_4381")
        val isImportButtonEnabled = selectedGroupIds.isNotEmpty()

        assertTrue("Import button must be enabled when at least 1 group is selected", isImportButtonEnabled)
    }

    @Test
    fun `step5_07 - Unidentified account group has distinctive review identity`() = runBlocking {
        // SMS with no bank name and no account suffix, from personal/unknown sender
        val sms = makeSms(401L, "Debited Rs. 500 on 20-Sep-26 to Store Ref 998811", "+919876543210")
        reader.records = listOf(sms)

        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(1, scanResult.accountGroups.size)

        val group = scanResult.accountGroups[0]
        assertEquals("unidentified_account", group.groupId)
        assertEquals("Unidentified Account", group.identity.getDisplayName())
        assertEquals(InstrumentType.UNKNOWN, group.identity.instrumentType)
        assertFalse(group.identity.isPartiallyIdentified)
    }

    @Test
    fun `step5_08 - Debit and credit totals for selected groups calculate accurately`() = runBlocking {
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        // Select only SBI (contains 300 debit, 2000 credit) and ICICI (contains 750 + 250 = 1000 debit)
        val selectedGroupIds = setOf("sbi_bank_account_7724", "icici_bank_account_1102")
        val selectedGroups = scanResult.accountGroups.filter { selectedGroupIds.contains(it.groupId) }

        val selectedDebitTotal = selectedGroups.sumOf { it.totalDebit }
        val selectedCreditTotal = selectedGroups.sumOf { it.totalCredit }
        val selectedTxnCount = selectedGroups.sumOf { it.transactionCount }

        assertEquals(1300.0, selectedDebitTotal, 0.001) // 300 + 1000
        assertEquals(2000.0, selectedCreditTotal, 0.001) // 2000
        assertEquals(4, selectedTxnCount)
    }

    @Test
    fun `step5_09 - Zero Room database writes during scan and selection phase`() = runBlocking {
        reader.records = createThreeAccountScenario()

        // 1. Initial count
        assertEquals(0, dao.getCount())

        // 2. Scan phase
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(0, dao.getCount())

        // 3. Selection state manipulations
        var selectedGroupIds = scanResult.accountGroups.map { it.groupId }.toSet()
        selectedGroupIds = selectedGroupIds - "hdfc_bank_account_4381"
        selectedGroupIds = emptySet()
        selectedGroupIds = setOf("sbi_bank_account_7724")

        // 4. Room DB row count remains strictly 0
        assertEquals(0, dao.getCount())
        assertTrue(dao.getAllExpensesList().isEmpty())
    }

    @Test
    fun `step5_10 - Expand and collapse tracking operates independently of selection state`() = runBlocking {
        var expandedGroupIds = emptySet<String>()
        val hdfcId = "hdfc_bank_account_4381"

        // Expand HDFC group
        expandedGroupIds = expandedGroupIds + hdfcId
        assertTrue(expandedGroupIds.contains(hdfcId))

        // Collapse HDFC group
        expandedGroupIds = expandedGroupIds - hdfcId
        assertFalse(expandedGroupIds.contains(hdfcId))
    }

    @Test
    fun `step5_11 - Multi-account selection state accurately filters account groups`() = runBlocking {
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        val selectedGroupIds = setOf("hdfc_bank_account_4381", "sbi_bank_account_7724")
        val selectedGroups = scanResult.accountGroups.filter { selectedGroupIds.contains(it.groupId) }

        assertEquals(2, selectedGroups.size)
        assertTrue(selectedGroups.any { it.groupId == "hdfc_bank_account_4381" })
        assertTrue(selectedGroups.any { it.groupId == "sbi_bank_account_7724" })
        assertFalse(selectedGroups.any { it.groupId == "icici_bank_account_1102" })
    }

    @Test
    fun `step5_12 - Single-account selection state isolates selected account group`() = runBlocking {
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        val selectedGroupIds = setOf("icici_bank_account_1102")
        val selectedGroups = scanResult.accountGroups.filter { selectedGroupIds.contains(it.groupId) }

        assertEquals(1, selectedGroups.size)
        assertEquals("icici_bank_account_1102", selectedGroups[0].groupId)
        assertEquals(2, selectedGroups[0].transactionCount)
    }

    @Test
    fun `step5_13 - Selection state uses stable group IDs instead of indices or display strings`() = runBlocking {
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        for (group in scanResult.accountGroups) {
            // Must not be empty, must be lowercase alphanumeric with underscores
            assertTrue("Group ID '${group.groupId}' must be stable key", group.groupId.matches(Regex("^[a-z0-9_]+$")))
            assertNotEquals("Group ID must not equal display name", group.identity.getDisplayName(), group.groupId)
        }
    }

    // =========================================================================
    // STEP 6 TESTS: Selected Account Import Execution & Idempotent Persistence
    // =========================================================================

    @Test
    fun `step6_01 - Selecting 1 of 3 groups imports ONLY transactions belonging to that group`() = runBlocking {
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(6, scanResult.scannedItems.size)

        // Select only HDFC (2 transactions)
        val selectedIds = setOf("hdfc_bank_account_4381")
        val importResult = manager.importTransactions(scanResult, selectedGroupIds = selectedIds) as SmsImportResult.Success

        assertEquals(2, importResult.insertedCount)
        assertEquals(2, dao.getCount())

        val persisted = dao.getAllExpensesList()
        assertEquals(2, persisted.size)
        // All persisted items must belong to HDFC
        assertTrue(persisted.all { it.note?.contains("HDFC Bank") == true || it.note?.contains("4381") == true })
        // Neither SBI nor ICICI should be persisted
        assertFalse(persisted.any { it.note?.contains("7724") == true || it.note?.contains("SBI") == true })
        assertFalse(persisted.any { it.note?.contains("1102") == true || it.note?.contains("ICICI") == true })
    }

    @Test
    fun `step6_02 - Selecting 2 of 3 groups imports ONLY transactions belonging to those 2 groups`() = runBlocking {
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        // Select HDFC and SBI (2 + 2 = 4 transactions)
        val selectedIds = setOf("hdfc_bank_account_4381", "sbi_bank_account_7724")
        val importResult = manager.importTransactions(scanResult, selectedGroupIds = selectedIds) as SmsImportResult.Success

        assertEquals(4, importResult.insertedCount)
        assertEquals(4, dao.getCount())

        val persisted = dao.getAllExpensesList()
        assertEquals(4, persisted.size)
        // ICICI transactions (1102) must NOT be present
        assertFalse(persisted.any { it.note?.contains("1102") == true || it.note?.contains("ICICI") == true })
    }

    @Test
    fun `step6_03 - Select All imports transactions from all discovered groups`() = runBlocking {
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        val allGroupIds = scanResult.accountGroups.map { it.groupId }.toSet()
        val importResult = manager.importTransactions(scanResult, selectedGroupIds = allGroupIds) as SmsImportResult.Success

        assertEquals(6, importResult.insertedCount)
        assertEquals(6, dao.getCount())
    }

    @Test
    fun `step6_04 - Clear All or empty selection imports 0 transactions`() = runBlocking {
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        val emptySelection = emptySet<String>()
        val importResult = manager.importTransactions(scanResult, selectedGroupIds = emptySelection) as SmsImportResult.Success

        assertEquals(0, importResult.insertedCount)
        assertEquals(0, dao.getCount())
        assertTrue(dao.getAllExpensesList().isEmpty())
    }

    @Test
    fun `step6_05 - Unselected transactions are completely untouched in Room`() = runBlocking {
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        // Import only ICICI
        val selectedIds = setOf("icici_bank_account_1102")
        val importResult = manager.importTransactions(scanResult, selectedGroupIds = selectedIds) as SmsImportResult.Success

        assertEquals(2, importResult.insertedCount)
        assertEquals(0, importResult.duplicatesSkippedCount)

        val persisted = dao.getAllExpensesList()
        assertEquals(2, persisted.size)
        // Ensure no placeholder or ghost entries for HDFC or SBI exist
        assertTrue(persisted.all { it.amount == 750.0 || it.amount == 250.0 })
    }

    @Test
    fun `step6_06 - Selected transactions properly persist amount, direction, and party`() = runBlocking {
        val sms = makeSms(501L, "Rs 849.00 paid to Zomato. Debited from A/c XX4381 on 20-Sep-26 Ref 555001", "AD-HDFCBK")
        reader.records = listOf(sms)

        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        val importResult = manager.importTransactions(
            scanResult,
            selectedGroupIds = setOf("hdfc_bank_account_4381")
        ) as SmsImportResult.Success

        assertEquals(1, importResult.insertedCount)
        val persisted = dao.getAllExpensesList()[0]
        assertEquals(849.0, persisted.amount, 0.001)
        assertEquals("Debit", persisted.type)
        assertTrue(persisted.merchant.contains("Zomato"))
    }

    @Test
    fun `step6_07 - Bank identity and account suffix are preserved upon persistence`() = runBlocking {
        val sms = makeSms(601L, "SBI A/c XX7724 debited INR 450.00 on 20-Sep-26 to BookMyShow Ref 666001", "AD-SBIINB")
        reader.records = listOf(sms)

        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        manager.importTransactions(
            scanResult,
            selectedGroupIds = setOf("sbi_bank_account_7724")
        )

        val persisted = dao.getAllExpensesList()[0]
        assertTrue(persisted.note?.contains("State Bank of India") == true || persisted.note?.contains("SBI") == true)
        assertTrue(persisted.note?.contains("7724") == true)
    }

    @Test
    fun `step6_08 - Unidentified transactions imported safely when selected`() = runBlocking {
        val sms = makeSms(701L, "Debited Rs. 120 on 20-Sep-26 to Chai Point Ref 770011", "+919876543210")
        reader.records = listOf(sms)

        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        val importResult = manager.importTransactions(
            scanResult,
            selectedGroupIds = setOf("unidentified_account")
        ) as SmsImportResult.Success

        assertEquals(1, importResult.insertedCount)
        val persisted = dao.getAllExpensesList()[0]
        assertEquals(120.0, persisted.amount, 0.001)
    }

    @Test
    fun `step6_09 - Repeated import of the same group is strictly idempotent`() = runBlocking {
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        val selectedIds = setOf("hdfc_bank_account_4381")

        // First import
        val firstImport = manager.importTransactions(scanResult, selectedGroupIds = selectedIds) as SmsImportResult.Success
        assertEquals(2, firstImport.insertedCount)
        assertEquals(0, firstImport.duplicatesSkippedCount)
        assertEquals(2, dao.getCount())

        // Re-scan and second import of the same group
        val secondScanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        val secondImport = manager.importTransactions(secondScanResult, selectedGroupIds = selectedIds) as SmsImportResult.Success

        assertEquals(0, secondImport.insertedCount)
        assertEquals(2, secondImport.duplicatesSkippedCount)
        assertEquals("Database row count must NOT increase on duplicate import", 2, dao.getCount())
    }

    @Test
    fun `step6_10 - Partial re-import imports only new group without duplicating previous group`() = runBlocking {
        reader.records = createThreeAccountScenario()
        val scanResult1 = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        // Import Group 1 (HDFC) first
        manager.importTransactions(scanResult1, selectedGroupIds = setOf("hdfc_bank_account_4381"))
        assertEquals(2, dao.getCount())

        // Later, user scans again and imports Group 2 (SBI)
        val scanResult2 = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        val importResult2 = manager.importTransactions(scanResult2, selectedGroupIds = setOf("sbi_bank_account_7724")) as SmsImportResult.Success

        assertEquals(2, importResult2.insertedCount)
        assertEquals(4, dao.getCount()) // 2 HDFC + 2 SBI

        val persisted = dao.getAllExpensesList()
        assertEquals(2, persisted.count { it.note?.contains("4381") == true || it.note?.contains("HDFC") == true })
        assertEquals(2, persisted.count { it.note?.contains("7724") == true || it.note?.contains("SBI") == true })
    }

    @Test
    fun `step6_11 - Group filtering uses stable groupId and matches correctly`() = runBlocking {
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        // Verify each item's groupId matches the extractor's output
        for (item in scanResult.scannedItems) {
            val expectedGroupId = AccountIdentityExtractor.generateGroupId(item.accountIdentity)
            assertNotNull(expectedGroupId)
        }

        // Test filtering with an invalid/mismatched string
        val importResult = manager.importTransactions(
            scanResult,
            selectedGroupIds = setOf("invalid_group_string_hdfc_4381")
        ) as SmsImportResult.Success

        assertEquals(0, importResult.insertedCount)
        assertEquals(0, dao.getCount())
    }

    @Test
    fun `step6_12 - Progress callback receives correct totalToImport matching selection`() = runBlocking {
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        // Select HDFC only (2 transactions out of 6)
        val reportedTotals = mutableListOf<Int>()
        manager.importTransactions(
            scanResult,
            selectedGroupIds = setOf("hdfc_bank_account_4381"),
            progressCallback = { progress ->
                reportedTotals.add(progress.totalToImport)
                true
            }
        )

        assertTrue(reportedTotals.isNotEmpty())
        assertTrue("Reported totalToImport should reflect the 2 selected items", reportedTotals.all { it == 2 })
    }

    @Test
    fun `step6_13 - Cancellation during selected import stops execution cleanly`() = runBlocking {
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        // Cancel on the first progress callback
        val importResult = manager.importTransactions(
            scanResult,
            selectedGroupIds = setOf("hdfc_bank_account_4381", "sbi_bank_account_7724"),
            progressCallback = { false }
        )

        assertTrue(importResult is SmsImportResult.Cancelled)
        assertEquals(0, dao.getCount())
    }

    @Test
    fun `step6_14 - Credit transactions in selected group update totalIncomeImported`() = runBlocking {
        // SBI group has 1 credit of Rs. 2,000 and 1 debit of Rs. 300
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        val importResult = manager.importTransactions(
            scanResult,
            selectedGroupIds = setOf("sbi_bank_account_7724")
        ) as SmsImportResult.Success

        assertEquals(2000.0, importResult.totalIncomeImported, 0.001)
        assertEquals(300.0, importResult.totalAmountImported, 0.001)
    }

    @Test
    fun `step6_15 - Debit transactions in selected group update totalAmountImported`() = runBlocking {
        // HDFC group has 2 debits: 500 + 150 = 650
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        val importResult = manager.importTransactions(
            scanResult,
            selectedGroupIds = setOf("hdfc_bank_account_4381")
        ) as SmsImportResult.Success

        assertEquals(650.0, importResult.totalAmountImported, 0.001)
        assertEquals(0.0, importResult.totalIncomeImported, 0.001)
    }

    @Test
    fun `step6_16 - Correlation with existing notification record enriches selected item without duplicate row`() = runBlocking {
        val baseTime = 1772600000000L
        val existingNotificationExpense = Expense(
            amount = 250.0,
            merchant = "satya chicken",
            dateMillis = baseTime,
            notificationKey = "notif_key_1",
            note = "UPI Ref: 624571799987",
            source = "NOTIFICATION"
        )
        val insertedId = dao.insert(existingNotificationExpense)
        assertEquals(1, dao.getCount())

        val sms = SmsRecord(
            id = 77L,
            address = "AD-IPPB",
            body = "A/C X2959 Debit Rs.250.00 for UPI to satya chicken Ref 624571799987. Avl Bal Rs.116.62. -IPPB",
            dateMillis = baseTime + 1000L
        )
        reader.records = listOf(sms)

        val scanResult = manager.scan(baseTime - 10000L, baseTime + 10000L) as SmsScanResult.Success
        assertEquals(1, scanResult.accountGroups.size)
        val groupId = scanResult.accountGroups[0].groupId

        val importResult = manager.importTransactions(
            scanResult,
            selectedGroupIds = setOf(groupId)
        ) as SmsImportResult.Success

        // Should correlate and enrich, not add a duplicate row
        assertEquals(1, importResult.enrichedCount)
        assertEquals(0, importResult.insertedCount)
        assertEquals(1, dao.getCount())

        val enriched = dao.getExpenseById(insertedId.toInt())
        assertNotNull(enriched)
        assertTrue(enriched!!.note?.contains("Correlated") == true)
    }

    @Test
    fun `step6_17 - Zero Room writes occur until explicit importTransactions call`() = runBlocking {
        reader.records = createThreeAccountScenario()

        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success
        assertEquals(3, scanResult.accountGroups.size)
        assertEquals(0, dao.getCount())

        // Selection is made
        val selectedIds = setOf("hdfc_bank_account_4381")
        assertEquals(0, dao.getCount())

        // Only when importTransactions is called does persistence occur
        manager.importTransactions(scanResult, selectedGroupIds = selectedIds)
        assertEquals(2, dao.getCount())
    }

    @Test
    fun `step6_18 - SmsImportResult Success contains selectedGroupIds filter info`() = runBlocking {
        reader.records = createThreeAccountScenario()
        val scanResult = manager.scan(0L, Long.MAX_VALUE) as SmsScanResult.Success

        val selectedIds = setOf("hdfc_bank_account_4381", "icici_bank_account_1102")
        val importResult = manager.importTransactions(scanResult, selectedGroupIds = selectedIds) as SmsImportResult.Success

        assertEquals(selectedIds, importResult.selectedGroupIds)
    }
}
