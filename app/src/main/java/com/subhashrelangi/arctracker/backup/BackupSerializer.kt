package com.subhashrelangi.arctracker.backup

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Production-quality JSON Serializer and Parser for ArcTracker full backup files (Milestone 15).
 *
 * Guarantees:
 * - Uses streaming [JsonWriter] and [JsonReader] to handle large datasets efficiently.
 * - Deterministic serialization with SHA-256 integrity checksum calculation.
 * - Protects against malformed JSON and enforces strict character encoding (UTF-8).
 */
object BackupSerializer {

    val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    /**
     * Serializes the [ArcTrackerBackupPayload] into the given [OutputStream].
     * Automatically calculates the checksum and updates the metadata before writing.
     */
    fun writePayload(payload: ArcTrackerBackupPayload, outputStream: OutputStream): BackupMetadata {
        val calculatedChecksum = computePayloadChecksum(payload)
        val finalMetadata = payload.metadata.copy(
            checksum = calculatedChecksum,
            entityCounts = BackupEntityCounts(
                transactions = payload.transactions.size,
                categories = payload.categories.size,
                accounts = payload.accounts.size,
                rules = payload.rules.size,
                aliases = payload.aliases.size,
                budgets = payload.budgets.size,
                budgetAlertStates = payload.budgetAlertStates.size
            )
        )
        val finalPayload = payload.copy(metadata = finalMetadata)

        val writer = OutputStreamWriter(outputStream, StandardCharsets.UTF_8).buffered()
        val jsonWriter = JsonWriter(writer).apply {
            setIndent("  ")
        }

        gson.toJson(finalPayload, ArcTrackerBackupPayload::class.java, jsonWriter)
        jsonWriter.flush()
        writer.flush()

        return finalMetadata
    }

    /**
     * Deserializes an [ArcTrackerBackupPayload] from an [InputStream].
     * Throws [IllegalArgumentException] if JSON is malformed or invalid.
     */
    fun readPayload(inputStream: InputStream): ArcTrackerBackupPayload {
        val reader = InputStreamReader(inputStream, StandardCharsets.UTF_8).buffered()
        val jsonReader = JsonReader(reader)

        return try {
            val payload = gson.fromJson<ArcTrackerBackupPayload>(jsonReader, ArcTrackerBackupPayload::class.java)
                ?: throw IllegalArgumentException("Backup file is empty or does not contain a valid JSON object")
            payload
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed backup file: ${e.message}", e)
        }
    }

    /**
     * Serializes payload to JSON string (primarily for testing and small previews).
     */
    fun toJsonString(payload: ArcTrackerBackupPayload): String {
        val calculatedChecksum = computePayloadChecksum(payload)
        val finalMetadata = payload.metadata.copy(
            checksum = calculatedChecksum,
            entityCounts = BackupEntityCounts(
                transactions = payload.transactions.size,
                categories = payload.categories.size,
                accounts = payload.accounts.size,
                rules = payload.rules.size,
                aliases = payload.aliases.size,
                budgets = payload.budgets.size,
                budgetAlertStates = payload.budgetAlertStates.size
            )
        )
        val finalPayload = payload.copy(metadata = finalMetadata)
        return gson.toJson(finalPayload)
    }

    /**
     * Deserializes payload from JSON string.
     */
    fun fromJsonString(json: String): ArcTrackerBackupPayload {
        return try {
            gson.fromJson(json, ArcTrackerBackupPayload::class.java)
                ?: throw IllegalArgumentException("JSON string resulted in null payload")
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed JSON string: ${e.message}", e)
        }
    }

    /**
     * Computes a deterministic SHA-256 checksum over the payload entities.
     */
    fun computePayloadChecksum(payload: ArcTrackerBackupPayload): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val sb = StringBuilder()

        sb.append(payload.metadata.formatVersion).append("|")
        sb.append(payload.metadata.databaseSchemaVersion).append("|")

        // Include key entity signatures
        for (cat in payload.categories.sortedBy { it.id }) {
            sb.append("cat:${cat.id}:${cat.name}:${cat.isSystem}|")
        }
        for (acc in payload.accounts.sortedBy { it.id }) {
            sb.append("acc:${acc.id}:${acc.accountSuffix}:${acc.instrumentType}|")
        }
        for (tx in payload.transactions.sortedBy { it.id }) {
            sb.append("tx:${tx.id}:${tx.amount}:${tx.dateMillis}:${tx.merchant}|")
        }
        for (rule in payload.rules.sortedBy { it.id }) {
            sb.append("rule:${rule.id}:${rule.pattern}:${rule.categoryId}|")
        }
        for (alias in payload.aliases.sortedBy { it.id }) {
            sb.append("alias:${alias.id}:${alias.alias}:${alias.canonicalMerchant}|")
        }
        for (b in payload.budgets.sortedBy { it.id }) {
            sb.append("b:${b.id}:${b.name}:${b.amountLimit}|")
        }

        val hashBytes = digest.digest(sb.toString().toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
