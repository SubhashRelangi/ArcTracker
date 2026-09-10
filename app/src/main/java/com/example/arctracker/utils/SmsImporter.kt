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
        val totalFound: Int,
        val imported: Int,
        val duplicatesSkipped: Int,
        val ignored: Int,
        val totalAmountImported: Double,
        val totalIncomeImported: Double
    )

    suspend fun importSms(context: Context, startTimeMillis: Long, updateProgress: (Int, Int) -> Unit): ImportResult = withContext(Dispatchers.IO) {
        var totalFound = 0
        var imported = 0
        var duplicatesSkipped = 0
        var ignored = 0
        var totalAmountImported = 0.0
        var totalIncomeImported = 0.0

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

                val lowerBody = body.lowercase()
                val isFinancial = lowerBody.contains("paid") || lowerBody.contains("debited") || 
                                  lowerBody.contains("sent") || lowerBody.contains("payment") || 
                                  lowerBody.contains("spent") || lowerBody.contains("deducted") ||
                                  lowerBody.contains("received") || lowerBody.contains("credited") ||
                                  lowerBody.contains("rs") || lowerBody.contains("inr") || lowerBody.contains("₹")
                                  
                val isIgnore = lowerBody.contains("otp") || lowerBody.contains("code is") || lowerBody.contains("cashback") || lowerBody.contains("available balance") || lowerBody.contains("loan") || lowerBody.contains("login")

                if (isFinancial && !isIgnore) {
                    val parsed = ExpenseParser.parseExpenseData(body, address)
                    if (parsed != null) {
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
                    } else {
                        ignored++
                    }
                } else {
                    ignored++
                }

                processed++
                if (processed % 20 == 0) {
                    updateProgress(processed, totalMessages)
                }
            }
            updateProgress(totalMessages, totalMessages)
        }

        ImportResult(totalFound, imported, duplicatesSkipped, ignored, totalAmountImported, totalIncomeImported)
    }
}
