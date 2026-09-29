package com.subhashrelangi.arctracker.service

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log

/**
 * Result of reading SMS messages from the system inbox (Milestone 3A).
 */
sealed class SmsReadResult {
    /**
     * Successfully scanned SMS inbox.
     * @param totalRead Number of valid records read/dispatched.
     */
    data class Success(val totalRead: Int) : SmsReadResult()

    /**
     * Reading could not proceed because Android READ_SMS permission is not granted.
     */
    data object PermissionRequired : SmsReadResult()

    /**
     * An unexpected failure occurred while querying or processing the SMS provider.
     */
    data class Failure(val error: Throwable, val message: String) : SmsReadResult()
}

/**
 * Bounded, streaming reader interface for Android's SMS inbox (Milestone 3A).
 *
 * CRITICAL ARCHITECTURAL GUARANTEES:
 * 1. Bounded date-range queries only (startTimeMillis to endTimeMillis inclusive).
 * 2. Deterministic ordering: date ASC, _id ASC.
 * 3. Streaming/callback execution to prevent OOM when scanning large inboxes (10,000+ messages).
 * 4. Original Android SMS _id strictly preserved ("sms_<id>"); NEVER generates random UUIDs.
 * 5. Raw SMS body strictly preserved without truncation or whitespace modifications.
 * 6. NO bank sender whitelists, NO mobile-number rejections at this layer.
 * 7. NO financial parsing inside SmsReader (no debit/credit/OTP logic).
 * 8. Privacy: NO complete SMS bodies logged to logcat.
 * 9. Cursor safety: Always closed; individual corrupt rows skipped without aborting the entire scan.
 */
interface SmsReader {

    /**
     * Reads SMS records matching the bounded time range [startTimeMillis..endTimeMillis] inclusive.
     * Invokes [onRecord] sequentially for each valid [SmsRecord].
     * If [onRecord] returns false, stops iteration immediately and returns [SmsReadResult.Success].
     */
    fun readSms(
        startTimeMillis: Long,
        endTimeMillis: Long,
        onRecord: (SmsRecord) -> Boolean
    ): SmsReadResult

    /**
     * Convenience method to fetch matching records into an in-memory list up to [limit].
     */
    fun readSmsList(
        startTimeMillis: Long,
        endTimeMillis: Long,
        limit: Int = Int.MAX_VALUE
    ): Pair<SmsReadResult, List<SmsRecord>> {
        val list = mutableListOf<SmsRecord>()
        val result = readSms(startTimeMillis, endTimeMillis) { record ->
            list.add(record)
            list.size < limit
        }
        return Pair(result, list)
    }

    /**
     * Reads matching SMS records in batches of [batchSize] to support chunked streaming.
     */
    fun readSmsBatch(
        startTimeMillis: Long,
        endTimeMillis: Long,
        batchSize: Int = 100,
        onBatch: (List<SmsRecord>) -> Boolean
    ): SmsReadResult {
        if (batchSize <= 0) {
            return SmsReadResult.Failure(
                IllegalArgumentException("batchSize must be greater than 0"),
                "Invalid batch size"
            )
        }
        val currentBatch = ArrayList<SmsRecord>(batchSize)
        var shouldContinue = true

        val result = readSms(startTimeMillis, endTimeMillis) { record ->
            currentBatch.add(record)
            if (currentBatch.size >= batchSize) {
                shouldContinue = onBatch(currentBatch.toList())
                currentBatch.clear()
            }
            shouldContinue
        }

        if (result is SmsReadResult.Success && shouldContinue && currentBatch.isNotEmpty()) {
            onBatch(currentBatch.toList())
            currentBatch.clear()
        }

        return result
    }

    companion object {
        fun create(context: Context): SmsReader = AndroidSmsReader(context)

        fun readSms(
            context: Context,
            startTimeMillis: Long,
            endTimeMillis: Long,
            onRecord: (SmsRecord) -> Boolean
        ): SmsReadResult = create(context).readSms(startTimeMillis, endTimeMillis, onRecord)

        fun readSmsList(
            context: Context,
            startTimeMillis: Long,
            endTimeMillis: Long,
            limit: Int = Int.MAX_VALUE
        ): Pair<SmsReadResult, List<SmsRecord>> = create(context).readSmsList(startTimeMillis, endTimeMillis, limit)
    }
}

/**
 * Android Telephony SMS inbox ContentResolver adapter implementation.
 */
class AndroidSmsReader(
    private val context: Context,
    private val permissionChecker: (Context) -> Boolean = { ctx ->
        SmsPermissionHelper.isSmsPermissionGranted(ctx)
    },
    private val queryExecutor: (Uri?, Array<String>?, String?, Array<String>?, String?) -> Cursor? = { uri, proj, sel, args, sort ->
        val targetUri = uri ?: getInboxUri()
        if (targetUri != null) {
            context.contentResolver.query(targetUri, proj, sel, args, sort)
        } else {
            null
        }
    }
) : SmsReader {

    override fun readSms(
        startTimeMillis: Long,
        endTimeMillis: Long,
        onRecord: (SmsRecord) -> Boolean
    ): SmsReadResult {
        // 1. Check runtime READ_SMS permission
        if (!permissionChecker(context)) {
            Log.w(TAG, "Cannot read SMS: READ_SMS permission not granted")
            return SmsReadResult.PermissionRequired
        }

        // 2. Validate date range bounds
        if (startTimeMillis > endTimeMillis) {
            Log.d(TAG, "Empty date range requested: startTime ($startTimeMillis) > endTime ($endTimeMillis)")
            return SmsReadResult.Success(0)
        }

        // 3. Prepare ContentResolver query
        val inboxUri = getInboxUri()

        val selection = "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?"
        val selectionArgs = arrayOf(startTimeMillis.toString(), endTimeMillis.toString())
        val sortOrder = "${Telephony.Sms.DATE} ASC, ${Telephony.Sms._ID} ASC"

        val cursor: Cursor? = try {
            queryExecutor(inboxUri, PROJECTION, selection, selectionArgs, sortOrder)
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException while querying SMS inbox: ${e.message}")
            return SmsReadResult.PermissionRequired
        } catch (e: Throwable) {
            Log.e(TAG, "Unexpected error executing SMS query", e)
            return SmsReadResult.Failure(e, "Error querying SMS inbox: ${e.message}")
        }

        if (cursor == null) {
            Log.w(TAG, "ContentResolver returned null cursor for SMS inbox")
            return SmsReadResult.Failure(
                IllegalStateException("ContentResolver returned null cursor for SMS inbox"),
                "Null cursor returned by SMS provider"
            )
        }

        var totalRead = 0

        // 4. Safely traverse cursor with row-level error isolation
        cursor.use { c ->
            val idIndex = c.getColumnIndex(Telephony.Sms._ID)
            val addressIndex = c.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIndex = c.getColumnIndex(Telephony.Sms.BODY)
            val dateIndex = c.getColumnIndex(Telephony.Sms.DATE)
            val typeIndex = c.getColumnIndex(Telephony.Sms.TYPE)
            val readIndex = c.getColumnIndex(Telephony.Sms.READ)
            val subIdIndex = c.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

            while (c.moveToNext()) {
                try {
                    if (idIndex < 0 || c.isNull(idIndex)) {
                        Log.w(TAG, "Skipping SMS row: missing _id column or null id")
                        continue
                    }
                    val id = c.getLong(idIndex)

                    if (dateIndex < 0 || c.isNull(dateIndex)) {
                        Log.w(TAG, "Skipping SMS row $id: missing date column or null date")
                        continue
                    }
                    val dateMillis = c.getLong(dateIndex)

                    val address = if (addressIndex >= 0 && !c.isNull(addressIndex)) {
                        c.getString(addressIndex) ?: "UNKNOWN"
                    } else {
                        "UNKNOWN"
                    }

                    // Raw body: MUST preserve raw SMS body without truncating or modifying whitespace
                    val body = if (bodyIndex >= 0 && !c.isNull(bodyIndex)) {
                        c.getString(bodyIndex) ?: ""
                    } else {
                        ""
                    }

                    val type = if (typeIndex >= 0 && !c.isNull(typeIndex)) {
                        c.getInt(typeIndex)
                    } else {
                        Telephony.Sms.MESSAGE_TYPE_INBOX
                    }

                    val isRead = if (readIndex >= 0 && !c.isNull(readIndex)) {
                        c.getInt(readIndex) == 1
                    } else {
                        true
                    }

                    val subscriptionId = if (subIdIndex >= 0 && !c.isNull(subIdIndex)) {
                        c.getInt(subIdIndex)
                    } else {
                        null
                    }

                    val record = SmsRecord(
                        id = id,
                        address = address,
                        body = body,
                        dateMillis = dateMillis,
                        type = type,
                        read = isRead,
                        subscriptionId = subscriptionId
                    )

                    totalRead++
                    val shouldContinue = onRecord(record)
                    if (!shouldContinue) {
                        Log.d(TAG, "SMS reading halted early by caller after $totalRead records")
                        break
                    }
                } catch (e: Exception) {
                    // Privacy: Never log raw SMS body!
                    Log.w(TAG, "Skipping malformed SMS row: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }

        Log.d(TAG, "Completed SMS scan. Total records processed: $totalRead")
        return SmsReadResult.Success(totalRead)
    }

    companion object {
        private const val TAG = "ArcTracker:SmsReader"
        const val INBOX_URI_STRING = "content://sms/inbox"

        fun getInboxUri(): Uri? {
            return try {
                Telephony.Sms.Inbox.CONTENT_URI ?: Uri.parse(INBOX_URI_STRING)
            } catch (e: Throwable) {
                null
            }
        }

        val PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.SUBSCRIPTION_ID
        )
    }
}

/**
 * In-memory implementation of [SmsReader] for unit tests, previews, and isolated simulations.
 */
class InMemorySmsReader(
    var hasPermission: Boolean = true,
    var records: List<SmsRecord> = emptyList(),
    var shouldThrowSecurityExceptionOnQuery: Boolean = false,
    var shouldFailWithNullCursor: Boolean = false
) : SmsReader {

    override fun readSms(
        startTimeMillis: Long,
        endTimeMillis: Long,
        onRecord: (SmsRecord) -> Boolean
    ): SmsReadResult {
        if (!hasPermission || shouldThrowSecurityExceptionOnQuery) {
            return SmsReadResult.PermissionRequired
        }
        if (shouldFailWithNullCursor) {
            return SmsReadResult.Failure(
                IllegalStateException("Simulated null cursor"),
                "Null cursor returned by SMS provider"
            )
        }
        if (startTimeMillis > endTimeMillis) {
            return SmsReadResult.Success(0)
        }

        val filtered = records
            .filter { it.dateMillis in startTimeMillis..endTimeMillis }
            .sortedWith(compareBy<SmsRecord> { it.dateMillis }.thenBy { it.id })

        var count = 0
        for (r in filtered) {
            count++
            if (!onRecord(r)) break
        }
        return SmsReadResult.Success(count)
    }
}
