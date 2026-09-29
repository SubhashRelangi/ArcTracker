package com.subhashrelangi.arctracker.service

import android.util.Log
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.ExpenseDao
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.KnownFinancialAccountRepository

/**
 * Result metrics returned by [AccountReconciliationBackfiller.backfill].
 */
data class BackfillResult(
    val processedCount: Int = 0,
    val reconciledCount: Int = 0,
    val ambiguousCount: Int = 0,
    val unmatchedCount: Int = 0,
    val noAccountDataCount: Int = 0,
    val skippedAlreadyResolvedCount: Int = 0
)

/**
 * Milestone 5: Safe, deterministic backfiller for linking unresolved [Expense] records
 * to [KnownFinancialAccount] records.
 *
 * Guarantees:
 * 1. ZERO GUESSING: Only deterministic suffix and instrument evidence can establish a link.
 * 2. PROHIBITED SIGNALS: Never links based solely on amount, merchant, date, timestamp, or proximity.
 * 3. STRICT AMBIGUITY HANDLING: If multiple known accounts share the suffix without disambiguating signal,
 *    leaves [Expense.accountId] as null.
 * 4. IDEMPOTENT: Never overwrites an already resolved [Expense.accountId].
 * 5. PRIVACY: Suffixes are restricted to safe 3–4 digits; full account numbers are never parsed or stored.
 */
class AccountReconciliationBackfiller(
    private val expenseDao: ExpenseDao,
    private val accountRepository: KnownFinancialAccountRepository,
    private val accountMatcher: KnownFinancialAccountMatcher? = null
) {

    companion object {
        private const val TAG = "AccountReconciliationBackfiller"
        private val NOTE_ACCOUNT_SUFFIX_REGEX = Regex("""(?i)(?:A/c:\s*XX|Card:\s*XX)(\d{3,4})\b""")
        private val MASKED_PATTERN = Regex("""(?i)(?:[Xx*]{3,}|(?:A/c|Account|Acct|Card)[\s.:#]*[Xx*]*)(\d{3,4})\b""")
    }

    /**
     * Executes safe, idempotent backfill across all expenses currently missing an account link.
     */
    suspend fun backfill(): BackfillResult {
        val unresolvedExpenses = expenseDao.getExpensesWithoutAccount()
        Log.i(TAG, "Starting safe backfill for ${unresolvedExpenses.size} unresolved expenses")

        var reconciledCount = 0
        var ambiguousCount = 0
        var unmatchedCount = 0
        var noAccountDataCount = 0

        for (expense in unresolvedExpenses) {
            // Invariant guard: strictly skip if accountId is already set
            if (expense.accountId != null) {
                continue
            }

            val outcome = backfillSingleExpense(expense)
            when (outcome) {
                Outcome.RECONCILED -> reconciledCount++
                Outcome.AMBIGUOUS -> ambiguousCount++
                Outcome.UNMATCHED -> unmatchedCount++
                Outcome.NO_ACCOUNT_DATA -> noAccountDataCount++
                Outcome.SKIPPED -> { /* Already resolved */ }
            }
        }

        val result = BackfillResult(
            processedCount = unresolvedExpenses.size,
            reconciledCount = reconciledCount,
            ambiguousCount = ambiguousCount,
            unmatchedCount = unmatchedCount,
            noAccountDataCount = noAccountDataCount,
            skippedAlreadyResolvedCount = 0
        )
        Log.i(TAG, "Backfill complete: $result")
        return result
    }

    /**
     * Reconciles a single expense deterministically.
     */
    suspend fun backfillSingleExpense(expense: Expense): Outcome {
        if (expense.accountId != null) {
            return Outcome.SKIPPED
        }

        // 1. Extract safe suffix
        val safeSuffix = resolveSafeSuffix(expense)
        if (safeSuffix == null) {
            // No deterministic suffix evidence found.
            // Explicit rule: Amount, merchant, timestamp alone are strictly insufficient.
            return Outcome.NO_ACCOUNT_DATA
        }

        // 2. Determine instrument type signal if present
        val detectedInstrument = detectInstrumentType(expense)

        // 3. Query known accounts with this suffix
        val candidateAccounts = accountRepository.findBySuffix(safeSuffix)
        if (candidateAccounts.isEmpty()) {
            // Suffix is present, but registry contains no compatible account
            if (expense.accountSuffix == null) {
                expenseDao.updateAccountMetadata(expense.id, null, safeSuffix)
            }
            return Outcome.UNMATCHED
        }

        // Filter candidates by instrument type if detected
        val compatibleAccounts = if (detectedInstrument != InstrumentType.UNKNOWN) {
            candidateAccounts.filter { it.instrumentType == detectedInstrument }
        } else {
            candidateAccounts
        }

        // Disambiguate by bank name if explicit in note or raw text
        val bankName = extractBankEvidence(expense)
        val finalCandidates = if (!bankName.isNullOrBlank() && compatibleAccounts.size > 1) {
            val matchingBank = compatibleAccounts.filter {
                it.institutionName.equals(bankName, ignoreCase = true) ||
                    it.institutionId.equals(bankName, ignoreCase = true)
            }
            if (matchingBank.isNotEmpty()) matchingBank else compatibleAccounts
        } else {
            compatibleAccounts
        }

        return when {
            finalCandidates.size == 1 -> {
                val matched = finalCandidates.first()
                expenseDao.updateAccountMetadata(expense.id, matched.id, safeSuffix)
                Log.d(TAG, "Reconciled expense id=${expense.id} to accountId=${matched.id}, suffix=$safeSuffix")
                Outcome.RECONCILED
            }
            finalCandidates.size > 1 -> {
                // Ambiguity: Multiple compatible accounts exist; never guess!
                if (expense.accountSuffix == null) {
                    expenseDao.updateAccountMetadata(expense.id, null, safeSuffix)
                }
                Log.d(TAG, "Ambiguous account for expense id=${expense.id} with suffix=$safeSuffix (${finalCandidates.size} candidates). Left null.")
                Outcome.AMBIGUOUS
            }
            else -> {
                if (expense.accountSuffix == null) {
                    expenseDao.updateAccountMetadata(expense.id, null, safeSuffix)
                }
                Outcome.UNMATCHED
            }
        }
    }

    private fun resolveSafeSuffix(expense: Expense): String? {
        // Source 1: Already populated safe suffix
        if (!expense.accountSuffix.isNullOrBlank()) {
            return AccountIdentityExtractor.safeSuffix(expense.accountSuffix)
        }

        // Source 2: Structured Note tokens (e.g. "A/c: XX9020" or "Card: XX9020")
        val note = expense.note
        if (!note.isNullOrBlank()) {
            val match = NOTE_ACCOUNT_SUFFIX_REGEX.find(note)
            if (match != null) {
                val safe = AccountIdentityExtractor.safeSuffix(match.groupValues[1])
                if (safe != null) return safe
            }
        }

        // Source 3: Raw notification / SMS text
        val rawText = expense.rawText
        if (!rawText.isNullOrBlank()) {
            val match = MASKED_PATTERN.find(rawText)
            if (match != null) {
                val safe = AccountIdentityExtractor.safeSuffix(match.groupValues[1])
                if (safe != null) return safe
            }
        }

        return null
    }

    private fun detectInstrumentType(expense: Expense): InstrumentType {
        val note = expense.note?.lowercase() ?: ""
        val raw = expense.rawText?.lowercase() ?: ""
        return when {
            note.contains("card:") || raw.contains("card ending") || raw.contains("debit card") || raw.contains("credit card") -> InstrumentType.CARD
            note.contains("a/c:") || raw.contains("a/c") || raw.contains("account") -> InstrumentType.BANK_ACCOUNT
            else -> InstrumentType.UNKNOWN
        }
    }

    private fun extractBankEvidence(expense: Expense): String? {
        val note = expense.note ?: ""
        if (note.contains("Bank: ")) {
            val after = note.substringAfter("Bank: ")
            val bank = after.substringBefore(" |").substringBefore(";").trim()
            if (bank.isNotBlank()) return bank
        }
        return null
    }

    enum class Outcome {
        RECONCILED,
        AMBIGUOUS,
        UNMATCHED,
        NO_ACCOUNT_DATA,
        SKIPPED
    }
}
