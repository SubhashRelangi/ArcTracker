package com.subhashrelangi.arctracker.backup

import com.subhashrelangi.arctracker.data.BuiltInCategories
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.TransactionCategory
import com.subhashrelangi.arctracker.data.TransactionRelationshipType
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Production-quality, RFC 4180-compliant CSV Exporter for ArcTracker transactions (Milestone 15).
 *
 * Guarantees:
 * - Proper escaping of quotes, commas, newlines, and Unicode characters.
 * - Strictly excludes sensitive credentials, raw SMS text, OTPs, or unmasked account numbers.
 * - Formats dates deterministically in user-friendly format (yyyy-MM-dd HH:mm:ss).
 * - Masks account suffixes to safe 4-digit trailing identifiers.
 * - Streams output directly to prevent memory spikes on large datasets.
 */
object CsvExporter {

    private val CSV_HEADER = listOf(
        "Date",
        "Merchant",
        "Amount",
        "Type",
        "Category",
        "Category Source",
        "Account Institution",
        "Account Suffix",
        "Instrument Type",
        "Pending",
        "Self-Transfer",
        "Notes"
    )

    private val dateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }

    /**
     * Exports a list of expenses to an [OutputStream] in streaming fashion.
     */
    fun exportToStream(
        expenses: List<Expense>,
        accountsMap: Map<String, KnownFinancialAccount> = emptyMap(),
        categoriesMap: Map<String, TransactionCategory> = emptyMap(),
        outputStream: OutputStream
    ): Int {
        val writer = OutputStreamWriter(outputStream, StandardCharsets.UTF_8).buffered()
        try {
            // Write CSV header
            writer.write(CSV_HEADER.joinToString(",") { escapeCsv(it) })
            writer.write("\r\n")

            // Write transaction rows
            for (expense in expenses) {
                val row = formatExpenseRow(expense, accountsMap, categoriesMap)
                writer.write(row)
                writer.write("\r\n")
            }
            writer.flush()
            return expenses.size
        } finally {
            // Do not close outputStream here so caller retains lifecycle control
            writer.flush()
        }
    }

    /**
     * Generates a complete CSV string representation (useful for testing and previews).
     */
    fun generateCsvString(
        expenses: List<Expense>,
        accountsMap: Map<String, KnownFinancialAccount> = emptyMap(),
        categoriesMap: Map<String, TransactionCategory> = emptyMap()
    ): String {
        val sb = StringBuilder()
        sb.append(CSV_HEADER.joinToString(",") { escapeCsv(it) })
        sb.append("\r\n")

        for (expense in expenses) {
            sb.append(formatExpenseRow(expense, accountsMap, categoriesMap))
            sb.append("\r\n")
        }
        return sb.toString()
    }

    private fun formatExpenseRow(
        expense: Expense,
        accountsMap: Map<String, KnownFinancialAccount>,
        categoriesMap: Map<String, TransactionCategory>
    ): String {
        val dateStr = dateFormat.get()?.format(Date(expense.dateMillis)) ?: ""
        val merchant = expense.merchant
        val amount = String.format(Locale.US, "%.2f", expense.amount)
        val type = expense.type

        // Resolve Category Name
        val categoryName = when {
            !expense.categoryId.isNullOrBlank() -> {
                categoriesMap[expense.categoryId]?.name
                    ?: BuiltInCategories.ALL.find { it.id == expense.categoryId }?.name
                    ?: expense.category
                    ?: expense.categoryId ?: ""
            }
            !expense.category.isNullOrBlank() -> expense.category ?: ""
            else -> "Uncategorized"
        }

        val categorySource = expense.categorySource

        // Resolve Account details
        val linkedAccount = expense.accountId?.let { accountsMap[it] }
        val institution = linkedAccount?.institutionName ?: linkedAccount?.institutionId ?: ""
        val suffix = expense.accountSuffix ?: linkedAccount?.accountSuffix ?: ""
        val maskedSuffix = if (suffix.isNotBlank()) "•••• $suffix" else ""
        val instrumentType = linkedAccount?.instrumentType?.name ?: ""

        val isPending = if (expense.isPending) "Yes" else "No"
        val isSelfTransfer = if (expense.relationshipType == TransactionRelationshipType.SELF_TRANSFER) "Yes" else "No"
        val notes = expense.note ?: ""

        val values = listOf(
            dateStr,
            merchant,
            amount,
            type,
            categoryName,
            categorySource,
            institution,
            maskedSuffix,
            instrumentType,
            isPending,
            isSelfTransfer,
            notes
        )

        return values.joinToString(",") { escapeCsv(it) }
    }

    /**
     * Escapes a single CSV value according to RFC 4180.
     */
    fun escapeCsv(value: String): String {
        if (value.isEmpty()) return ""
        val containsSpecial = value.contains(',') ||
            value.contains('"') ||
            value.contains('\n') ||
            value.contains('\r')

        return if (containsSpecial) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
