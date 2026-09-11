package com.example.arctracker.utils

import android.content.Context
import android.net.Uri
import com.example.arctracker.data.AppDatabase
import com.example.arctracker.data.Expense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object SmsImporter {

    data class ImportResult(
        val totalFound: Int,            // every message scanned in the inbox
        val bankMessagesFound: Int,     // messages sent by a recognized bank sender
        val imported: Int,
        val duplicatesSkipped: Int,
        val promotionalSkipped: Int,    // bank messages that are marketing blasts
        val nonBankSkipped: Int,        // senders that are not recognized banks
        val ignored: Int,               // bank messages that were unparsable / non-financial
        val totalAmountImported: Double,
        val totalIncomeImported: Double,
        val banksDetected: List<String>
    )

    suspend fun importSms(context: Context, startTimeMillis: Long, updateProgress: (Int, Int) -> Unit): ImportResult = withContext(Dispatchers.IO) {
        var totalFound = 0
        var bankMessagesFound = 0
        var imported = 0
        var duplicatesSkipped = 0
        var promotionalSkipped = 0
        var nonBankSkipped = 0
        var ignored = 0
        var totalAmountImported = 0.0
        var totalIncomeImported = 0.0
        val banksDetected = mutableSetOf<String>()

        val database = AppDatabase.getDatabase(context)
        val dao = database.expenseDao()

        val cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("_id", "address", "body", "date"),
            "date >= ?",
            arrayOf(startTimeMillis.toString()),
            "date ASC"
        )

        cursor?.use {
            val totalMessages = it.count
            totalFound = totalMessages
            var processed = 0

            val addressIdx = it.getColumnIndex("address")
            val bodyIdx = it.getColumnIndex("body")
            val dateIdx = it.getColumnIndex("date")

            while (it.moveToNext()) {
                val address = if (addressIdx != -1) it.getString(addressIdx) else ""
                val body = if (bodyIdx != -1) it.getString(bodyIdx) else ""
                val dateMillis = if (dateIdx != -1) it.getLong(dateIdx) else System.currentTimeMillis()

                // ---- Bank-only gate: never touch messages from random senders ----
                if (!BankSenderFilter.isBankSender(address)) {
                    nonBankSkipped++
                } else {
                    bankMessagesFound++
                    banksDetected.add(BankSenderFilter.detectBankName(address) ?: "BANK")

                    // Unified validation: handles ignore rules, parsing, and bounds
                    val result = TransactionValidator.validate(context, body, address)
                    
                    if (!result.accepted) {
                        if (result.reason.startsWith("Ignored by")) {
                            promotionalSkipped++ // Reusing counter for any ignored rule
                        } else {
                            ignored++
                        }
                    } else {
                        val parsed = result.parsed!!
                        // Duplicate check: +/- 24 hours window.
                        val startTime = dateMillis - (24 * 60 * 60 * 1000L)
                        val endTime = dateMillis + (24 * 60 * 60 * 1000L)
                        val duplicates = dao.findPotentialDuplicates(parsed.amount, parsed.type, startTime, endTime)

                        val isDuplicate = duplicates.isNotEmpty()

                        if (isDuplicate) {
                            duplicatesSkipped++
                        } else {
                            dao.insertExpense(
                                Expense(
                                    parsed.amount,
                                    parsed.merchant,
                                    dateMillis,
                                    parsed.type,
                                    "sms_" + UUID.randomUUID().toString(),
                                    false,
                                    body,
                                    "Other",
                                    "",
                                    "SMS_HISTORY"
                                )
                            )
                            imported++
                            if (parsed.type == "Credit") {
                                totalIncomeImported += parsed.amount
                            } else {
                                totalAmountImported += parsed.amount
                            }
                        }
                    }
                }

                processed++
                if (processed % 20 == 0) {
                    updateProgress(processed, totalMessages)
                }
            }
            updateProgress(totalMessages, totalMessages)
        }

        ImportResult(
            totalFound,
            bankMessagesFound,
            imported,
            duplicatesSkipped,
            promotionalSkipped,
            nonBankSkipped,
            ignored,
            totalAmountImported,
            totalIncomeImported,
            banksDetected.sorted()
        )
    }
}
