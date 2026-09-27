package com.example.arctracker

import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import com.example.arctracker.service.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/**
 * Milestone 3A: Unit tests for Android SMS Reader / ContentResolver Source Layer.
 *
 * Verifies:
 * 1. Bounded date-range queries (startTime to endTime).
 * 2. Inclusive boundary timestamp handling.
 * 3. Deterministic ordering: date ASC, _id ASC.
 * 4. SMS _id preservation ("sms_<id>"), no random UUID generation.
 * 5. Unknown/mobile sender support (no sender whitelisting).
 * 6. Null and malformed rows handled gracefully without aborting cursor.
 * 7. Missing READ_SMS permission handling (returns PermissionRequired, never crashes).
 * 8. SmsRecord.toSourceEvent() metadata preservation.
 * 9. Separation of sourceEventTime (arrival) from extracted transactionTime.
 * 10. Streaming / batching / early stop capabilities.
 * 11. Resource safety (cursor closed in all scenarios).
 */
class SmsReaderTest {

    private lateinit var dummyContext: Context

    @Before
    fun setUp() {
        SmsPermissionHelper.permissionOverrideForTesting = true
        dummyContext = object : ContextWrapper(null) {}
    }

    @After
    fun tearDown() {
        SmsPermissionHelper.permissionOverrideForTesting = null
    }

    /**
     * Helper to create a dynamic proxy [Cursor] for testing without Android framework mocks.
     */
    private fun createFakeCursor(
        columns: Array<String>,
        rows: List<Array<Any?>>,
        onClose: () -> Unit = {}
    ): Cursor {
        var currentIndex = -1
        var isClosed = false

        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "moveToNext" -> {
                    currentIndex++
                    currentIndex < rows.size
                }
                "getColumnIndex" -> {
                    val colName = args[0] as String
                    columns.indexOf(colName)
                }
                "getLong" -> {
                    val colIndex = args[0] as Int
                    val value = rows[currentIndex][colIndex]
                    if (value == null) {
                        0L
                    } else if (value is Number) {
                        value.toLong()
                    } else {
                        throw IllegalArgumentException("Expected Number at col $colIndex, got $value")
                    }
                }
                "getString" -> {
                    val colIndex = args[0] as Int
                    rows[currentIndex][colIndex] as? String
                }
                "getInt" -> {
                    val colIndex = args[0] as Int
                    val value = rows[currentIndex][colIndex]
                    if (value == null) {
                        0
                    } else if (value is Number) {
                        value.toInt()
                    } else {
                        throw IllegalArgumentException("Expected Number at col $colIndex, got $value")
                    }
                }
                "isNull" -> {
                    val colIndex = args[0] as Int
                    rows[currentIndex][colIndex] == null
                }
                "close" -> {
                    isClosed = true
                    onClose()
                    null
                }
                "isClosed" -> isClosed
                "hashCode" -> 42
                "equals" -> proxy === args[0]
                "toString" -> "FakeCursor(rows=${rows.size}, pos=$currentIndex)"
                else -> null
            }
        }

        return Proxy.newProxyInstance(
            Cursor::class.java.classLoader,
            arrayOf(Cursor::class.java),
            handler
        ) as Cursor
    }

    // =========================================================================
    // 1. DATE RANGE BOUNDED FILTERING
    // =========================================================================

    @Test
    fun test01_dateRangeBoundedFiltering_excludesOutliers() {
        val start = 1700000000000L
        val end = 1700086400000L

        var queriedSelection: String? = null
        var queriedArgs: Array<String>? = null

        val columns = AndroidSmsReader.PROJECTION
        // Row 1 is within range
        val rows = listOf<Array<Any?>>(
            arrayOf(101L, "AD-IPPB", "Debited INR 500", 1700050000000L, 1, 1, null)
        )
        val cursor = createFakeCursor(columns, rows)

        val reader = AndroidSmsReader(
            context = dummyContext,
            permissionChecker = { true },
            queryExecutor = { _, _, sel, args, _ ->
                queriedSelection = sel
                queriedArgs = args
                cursor
            }
        )

        val (result, records) = reader.readSmsList(start, end)

        assertTrue(result is SmsReadResult.Success)
        assertEquals(1, (result as SmsReadResult.Success).totalRead)
        assertEquals(1, records.size)
        assertEquals(101L, records[0].id)

        // Verify bounds were passed to selection
        assertEquals("${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?", queriedSelection)
        assertArrayEquals(arrayOf(start.toString(), end.toString()), queriedArgs)
    }

    // =========================================================================
    // 2. BOUNDARY TIMESTAMP HANDLING (INCLUSIVE & REVERSED)
    // =========================================================================

    @Test
    fun test02_boundaryTimestampHandling_inclusiveEndpoints() {
        val start = 1700000000000L
        val end = 1700086400000L

        val columns = AndroidSmsReader.PROJECTION
        val rows = listOf<Array<Any?>>(
            arrayOf(1L, "SENDER1", "Exact start bound", start, 1, 1, null),
            arrayOf(2L, "SENDER2", "Exact end bound", end, 1, 1, null)
        )
        val cursor = createFakeCursor(columns, rows)

        val reader = AndroidSmsReader(
            context = dummyContext,
            permissionChecker = { true },
            queryExecutor = { _, _, _, _, _ -> cursor }
        )

        val (result, records) = reader.readSmsList(start, end)

        assertTrue(result is SmsReadResult.Success)
        assertEquals(2, records.size)
        assertEquals(start, records[0].dateMillis)
        assertEquals(end, records[1].dateMillis)
    }

    @Test
    fun test03_boundaryTimestampHandling_reversedBoundsReturnsEmpty() {
        var queryCalled = false
        val reader = AndroidSmsReader(
            context = dummyContext,
            permissionChecker = { true },
            queryExecutor = { _, _, _, _, _ ->
                queryCalled = true
                null
            }
        )

        // Start timestamp is greater than end timestamp
        val (result, records) = reader.readSmsList(startTimeMillis = 2000L, endTimeMillis = 1000L)

        assertTrue(result is SmsReadResult.Success)
        assertEquals(0, (result as SmsReadResult.Success).totalRead)
        assertTrue(records.isEmpty())
        assertFalse("Query should not be executed when bounds are inverted", queryCalled)
    }

    // =========================================================================
    // 3. DETERMINISTIC ORDERING: date ASC, _id ASC
    // =========================================================================

    @Test
    fun test04_deterministicOrdering_dateAscAndIdAsc() {
        var querySortOrder: String? = null

        val columns = AndroidSmsReader.PROJECTION
        val rows = listOf<Array<Any?>>(
            arrayOf(10L, "BANK", "Msg 1", 1000L, 1, 1, null),
            arrayOf(5L, "BANK", "Msg 2 same time lower id", 2000L, 1, 1, null),
            arrayOf(8L, "BANK", "Msg 3 same time higher id", 2000L, 1, 1, null),
            arrayOf(1L, "BANK", "Msg 4 later time", 3000L, 1, 1, null)
        )
        val cursor = createFakeCursor(columns, rows)

        val reader = AndroidSmsReader(
            context = dummyContext,
            permissionChecker = { true },
            queryExecutor = { _, _, _, _, sort ->
                querySortOrder = sort
                cursor
            }
        )

        val (result, records) = reader.readSmsList(0L, 5000L)

        assertTrue(result is SmsReadResult.Success)
        assertEquals("${Telephony.Sms.DATE} ASC, ${Telephony.Sms._ID} ASC", querySortOrder)
        assertEquals(4, records.size)
        assertEquals(10L, records[0].id)
        assertEquals(5L, records[1].id)
        assertEquals(8L, records[2].id)
        assertEquals(1L, records[3].id)
    }

    // =========================================================================
    // 4. SMS _ID PRESERVATION (NEVER RANDOM UUID)
    // =========================================================================

    @Test
    fun test05_originalSmsIdPreserved_neverGeneratesUuid() {
        val columns = AndroidSmsReader.PROJECTION
        val expectedSmsId = 9876543210L
        val rows = listOf<Array<Any?>>(
            arrayOf(expectedSmsId, "AD-IPPB", "Debited INR 100", 1700000000000L, 1, 1, 2)
        )
        val cursor = createFakeCursor(columns, rows)

        val reader = AndroidSmsReader(
            context = dummyContext,
            permissionChecker = { true },
            queryExecutor = { _, _, _, _, _ -> cursor }
        )

        val (_, records) = reader.readSmsList(0L, Long.MAX_VALUE)
        val record = records[0]

        // Must retain exact Long id
        assertEquals(expectedSmsId, record.id)
        assertEquals(2, record.subscriptionId)

        // When converted to TransactionSourceEvent, format is sms_<id>
        val sourceEvent = record.toSourceEvent()
        assertEquals("sms_9876543210", sourceEvent.sourceId)
        assertFalse("Source ID must not be a random UUID", sourceEvent.sourceId.contains("-"))
    }

    // =========================================================================
    // 5. UNKNOWN & MOBILE SENDER SUPPORT (NO WHITELISTING)
    // =========================================================================

    @Test
    fun test06_senderSupport_mobileNumbersAndAlphanumericHeadersAllowed() {
        val columns = AndroidSmsReader.PROJECTION
        val rows = listOf<Array<Any?>>(
            arrayOf(1L, "+919876543210", "Paid 200 via UPI", 1000L, 1, 1, null),
            arrayOf(2L, "9876543210", "Sent money 500", 2000L, 1, 1, null),
            arrayOf(3L, "AD-IPPB", "Debited 100", 3000L, 1, 1, null),
            arrayOf(4L, "VK-SBIUPI", "Credited 50", 4000L, 1, 1, null),
            arrayOf(5L, "UNKNOWN_ENTITY", "Random SMS", 5000L, 1, 1, null),
            arrayOf(6L, null, "Null sender message", 6000L, 1, 1, null)
        )
        val cursor = createFakeCursor(columns, rows)

        val reader = AndroidSmsReader(
            context = dummyContext,
            permissionChecker = { true },
            queryExecutor = { _, _, _, _, _ -> cursor }
        )

        val (result, records) = reader.readSmsList(0L, 10000L)

        assertTrue(result is SmsReadResult.Success)
        assertEquals(6, records.size)

        // Verify none of the senders were rejected or dropped
        assertEquals("+919876543210", records[0].address)
        assertTrue(records[0].toSourceEvent().isMobileSender)

        assertEquals("9876543210", records[1].address)
        assertTrue(records[1].toSourceEvent().isMobileSender)

        assertEquals("AD-IPPB", records[2].address)
        assertFalse(records[2].toSourceEvent().isMobileSender)

        assertEquals("VK-SBIUPI", records[3].address)
        assertFalse(records[3].toSourceEvent().isMobileSender)

        assertEquals("UNKNOWN_ENTITY", records[4].address)
        assertEquals("UNKNOWN", records[5].address)
    }

    // =========================================================================
    // 6. RAW BODY PRESERVATION (NO TRUNCATION / NO WHITESPACE ALTERATIONS)
    // =========================================================================

    @Test
    fun test07_rawBodyPreserved_whitespaceAndSpecialCharsIntact() {
        val rawMessage = "  Dear Customer,\n\n₹1,500.00 debited from A/C *1234 on 25-Nov-2023 14:30:15 IST.\r\nRef: UPI/123456789012.  \t"

        val columns = AndroidSmsReader.PROJECTION
        val rows = listOf<Array<Any?>>(
            arrayOf(1L, "AD-IPPB", rawMessage, 1000L, 1, 1, null)
        )
        val cursor = createFakeCursor(columns, rows)

        val reader = AndroidSmsReader(
            context = dummyContext,
            permissionChecker = { true },
            queryExecutor = { _, _, _, _, _ -> cursor }
        )

        val (_, records) = reader.readSmsList(0L, 2000L)
        val record = records[0]

        // Exact match with no trimming or whitespace alterations
        assertEquals(rawMessage, record.body)
        assertEquals(rawMessage, record.toSourceEvent().rawText)
    }

    // =========================================================================
    // 7. NULL & MALFORMED ROWS HANDLED GRACEFULLY (ERROR ISOLATION)
    // =========================================================================

    @Test
    fun test08_nullOrMalformedRows_skippedGracefullyWithoutAborting() {
        val columns = AndroidSmsReader.PROJECTION
        val rows = listOf<Array<Any?>>(
            // Valid row 1
            arrayOf(1L, "SENDER1", "Valid message 1", 1000L, 1, 1, null),
            // Corrupt row: null ID
            arrayOf(null, "SENDER2", "Missing ID message", 2000L, 1, 1, null),
            // Corrupt row: null Date
            arrayOf(3L, "SENDER3", "Missing Date message", null, 1, 1, null),
            // Valid row 4
            arrayOf(4L, "SENDER4", "Valid message 4", 4000L, 1, 1, null)
        )
        val cursor = createFakeCursor(columns, rows)

        val reader = AndroidSmsReader(
            context = dummyContext,
            permissionChecker = { true },
            queryExecutor = { _, _, _, _, _ -> cursor }
        )

        val (result, records) = reader.readSmsList(0L, 5000L)

        assertTrue(result is SmsReadResult.Success)
        // Corrupt rows 2 & 3 skipped; valid rows 1 & 4 processed
        assertEquals(2, records.size)
        assertEquals(1L, records[0].id)
        assertEquals(4L, records[1].id)
    }

    // =========================================================================
    // 8. MISSING PERMISSION & SECURITY EXCEPTION HANDLING
    // =========================================================================

    @Test
    fun test09_permissionDenied_returnsPermissionRequiredWithoutException() {
        var queryInvoked = false
        val reader = AndroidSmsReader(
            context = dummyContext,
            permissionChecker = { false }, // Permission denied
            queryExecutor = { _, _, _, _, _ ->
                queryInvoked = true
                null
            }
        )

        val (result, records) = reader.readSmsList(0L, 1000L)

        assertEquals(SmsReadResult.PermissionRequired, result)
        assertTrue(records.isEmpty())
        assertFalse("Content query must not run if permission is missing", queryInvoked)
    }

    @Test
    fun test10_querySecurityException_returnsPermissionRequired() {
        val reader = AndroidSmsReader(
            context = dummyContext,
            permissionChecker = { true },
            queryExecutor = { _, _, _, _, _ ->
                throw SecurityException("Permission denial during content query")
            }
        )

        val (result, records) = reader.readSmsList(0L, 1000L)

        assertEquals(SmsReadResult.PermissionRequired, result)
        assertTrue(records.isEmpty())
    }

    // =========================================================================
    // 9. NULL CURSOR & FAILURE HANDLING
    // =========================================================================

    @Test
    fun test11_nullCursor_returnsFailureGracefully() {
        val reader = AndroidSmsReader(
            context = dummyContext,
            permissionChecker = { true },
            queryExecutor = { _, _, _, _, _ -> null }
        )

        val (result, records) = reader.readSmsList(0L, 1000L)

        assertTrue("Null cursor should yield Failure", result is SmsReadResult.Failure)
        assertTrue(records.isEmpty())
    }

    // =========================================================================
    // 10. RESOURCE SAFETY: CURSOR ALWAYS CLOSED
    // =========================================================================

    @Test
    fun test12_cursorAlwaysClosed_afterReading() {
        var cursorClosed = false
        val columns = AndroidSmsReader.PROJECTION
        val rows = listOf<Array<Any?>>(
            arrayOf(1L, "BANK", "Test body", 1000L, 1, 1, null)
        )
        val cursor = createFakeCursor(columns, rows, onClose = { cursorClosed = true })

        val reader = AndroidSmsReader(
            context = dummyContext,
            permissionChecker = { true },
            queryExecutor = { _, _, _, _, _ -> cursor }
        )

        reader.readSmsList(0L, 2000L)

        assertTrue("Cursor must be closed after readSms execution", cursorClosed)
    }

    // =========================================================================
    // 11. STREAMING & EARLY STOP
    // =========================================================================

    @Test
    fun test13_streamingEarlyStop_stopsImmediatelyWhenCallbackReturnsFalse() {
        var cursorClosed = false
        val columns = AndroidSmsReader.PROJECTION
        val rows = listOf<Array<Any?>>(
            arrayOf(1L, "BANK", "Msg 1", 1000L, 1, 1, null),
            arrayOf(2L, "BANK", "Msg 2", 2000L, 1, 1, null),
            arrayOf(3L, "BANK", "Msg 3", 3000L, 1, 1, null)
        )
        val cursor = createFakeCursor(columns, rows, onClose = { cursorClosed = true })

        val reader = AndroidSmsReader(
            context = dummyContext,
            permissionChecker = { true },
            queryExecutor = { _, _, _, _, _ -> cursor }
        )

        val consumed = mutableListOf<SmsRecord>()
        val result = reader.readSms(0L, 5000L) { record ->
            consumed.add(record)
            // Stop after consuming 2 records
            consumed.size < 2
        }

        assertTrue(result is SmsReadResult.Success)
        assertEquals(2, (result as SmsReadResult.Success).totalRead)
        assertEquals(2, consumed.size)
        assertEquals(1L, consumed[0].id)
        assertEquals(2L, consumed[1].id)
        assertTrue("Cursor must be closed even when halting early", cursorClosed)
    }

    // =========================================================================
    // 12. BATCH STREAMING API (readSmsBatch)
    // =========================================================================

    @Test
    fun test14_batchStreaming_emitsConfiguredBatchSizes() {
        val columns = AndroidSmsReader.PROJECTION
        val rows = (1..5).map { id ->
            arrayOf<Any?>(id.toLong(), "BANK", "Message $id", id * 1000L, 1, 1, null)
        }
        val cursor = createFakeCursor(columns, rows)

        val reader = AndroidSmsReader(
            context = dummyContext,
            permissionChecker = { true },
            queryExecutor = { _, _, _, _, _ -> cursor }
        )

        val batches = mutableListOf<List<SmsRecord>>()
        val result = reader.readSmsBatch(0L, 10000L, batchSize = 2) { batch ->
            batches.add(batch)
            true
        }

        assertTrue(result is SmsReadResult.Success)
        assertEquals(5, (result as SmsReadResult.Success).totalRead)
        // 5 items in batches of 2 -> [2, 2, 1]
        assertEquals(3, batches.size)
        assertEquals(2, batches[0].size)
        assertEquals(2, batches[1].size)
        assertEquals(1, batches[2].size)
        assertEquals(1L, batches[0][0].id)
        assertEquals(5L, batches[2][0].id)
    }

    // =========================================================================
    // 13. METADATA PRESERVATION IN toSourceEvent()
    // =========================================================================

    @Test
    fun test15_toSourceEvent_preservesAllMetadata() {
        val record = SmsRecord(
            id = 42L,
            address = "AD-IPPB",
            body = "Acct *1234 debited INR 500",
            dateMillis = 1700000000000L,
            type = 1,
            read = true,
            subscriptionId = 1
        )

        val event = record.toSourceEvent()

        assertEquals(TransactionSourceType.SMS_HISTORY, event.sourceType)
        assertEquals("sms_42", event.sourceId)
        assertEquals("AD-IPPB", event.sender)
        assertEquals("Acct *1234 debited INR 500", event.rawText)
        assertEquals(1700000000000L, event.eventTimestamp)
        assertEquals("AD-IPPB", event.title)
        assertEquals("sms", event.category)
    }

    // =========================================================================
    // 14. SEPARATION OF sourceEventTime AND transactionTime
    // =========================================================================

    @Test
    fun test16_temporalSeparation_sourceEventTimeDistinguishedFromExtractedTransactionTime() {
        val smsArrivalTimestamp = 1700050000000L // e.g. SMS arrived later
        val rawSmsText = "Dear Customer, INR 750.00 debited from A/C *5678 on 25-Nov-23 10:15:30 IST to Swiggy. UPI Ref: 332910485923."

        val record = SmsRecord(
            id = 100L,
            address = "AD-IPPB",
            body = rawSmsText,
            dateMillis = smsArrivalTimestamp
        )

        val sourceEvent = record.toSourceEvent()

        // 1. Source event arrival timestamp is preserved
        assertEquals(smsArrivalTimestamp, sourceEvent.eventTimestamp)

        // 2. Pass into the shared transaction pipeline
        val captured = sourceEvent.toCapturedNotificationInfo()
        val normalized = NotificationNormalizer.normalize(captured)
        assertNotNull(normalized)

        val candidates = StructuredTransactionExtractor.extractAll(normalized!!)

        assertEquals(1, candidates.size)
        val candidate = candidates[0]

        // Candidate postTime is the arrival timestamp
        assertEquals(smsArrivalTimestamp, candidate.postTime)
        // Candidate temporal evidence contains the separation between source arrival and transaction time
        assertNotNull(candidate.temporalEvidence)
        assertEquals(smsArrivalTimestamp, candidate.temporalEvidence!!.sourceEventTimeMillis)
    }

    // =========================================================================
    // 15. IN-MEMORY SMS READER BEHAVIOR
    // =========================================================================

    @Test
    fun test17_inMemorySmsReader_behavesConsistentlyWithRealReader() {
        val testRecords = listOf(
            SmsRecord(id = 3L, address = "BANK1", body = "Late msg", dateMillis = 3000L),
            SmsRecord(id = 1L, address = "BANK2", body = "Early msg", dateMillis = 1000L),
            SmsRecord(id = 2L, address = "BANK3", body = "Mid msg", dateMillis = 2000L)
        )

        val reader = InMemorySmsReader(hasPermission = true, records = testRecords)

        val (result, list) = reader.readSmsList(1000L, 2500L)

        assertTrue(result is SmsReadResult.Success)
        assertEquals(2, list.size)
        assertEquals(1L, list[0].id)
        assertEquals(2L, list[1].id)

        // Test permission denial in InMemorySmsReader
        reader.hasPermission = false
        val deniedResult = reader.readSms(1000L, 2500L) { true }
        assertEquals(SmsReadResult.PermissionRequired, deniedResult)
    }

    // =========================================================================
    // 16. QUERY PARAMETERS VERIFICATION
    // =========================================================================

    @Test
    fun test18_queryParameters_correctProjectionSelectionAndSortOrder() {
        var capturedProjection: Array<String>? = null
        var capturedSelection: String? = null
        var capturedSortOrder: String? = null

        val columns = AndroidSmsReader.PROJECTION
        val rows = emptyList<Array<Any?>>()
        val cursor = createFakeCursor(columns, rows)

        val reader = AndroidSmsReader(
            context = dummyContext,
            permissionChecker = { true },
            queryExecutor = { _, proj, sel, _, sort ->
                capturedProjection = proj
                capturedSelection = sel
                capturedSortOrder = sort
                cursor
            }
        )

        reader.readSms(100L, 200L) { true }

        assertArrayEquals(AndroidSmsReader.PROJECTION, capturedProjection)
        assertEquals("${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?", capturedSelection)
        assertEquals("${Telephony.Sms.DATE} ASC, ${Telephony.Sms._ID} ASC", capturedSortOrder)
    }
}
