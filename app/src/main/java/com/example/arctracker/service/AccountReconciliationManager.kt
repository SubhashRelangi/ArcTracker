package com.example.arctracker.service

import com.example.arctracker.data.AccountSource
import com.example.arctracker.data.Expense
import com.example.arctracker.data.ExpenseDao
import com.example.arctracker.data.KnownFinancialAccount
import com.example.arctracker.data.KnownFinancialAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Manager facilitating user-facing account management, manual transaction reconciliation,
 * reassignment, and unlinking (Milestone 6).
 */
class AccountReconciliationManager(
    private val expenseDao: ExpenseDao,
    private val knownAccountRepo: KnownFinancialAccountRepository
) {

    // --- Querying Accounts ---

    fun getAllKnownAccounts(): List<KnownFinancialAccount> {
        return knownAccountRepo.getAll()
    }

    fun getAllKnownAccountsFlow(): Flow<List<KnownFinancialAccount>> {
        return knownAccountRepo.getAllFlow()
    }

    fun getKnownAccountCount(): Int {
        return knownAccountRepo.getCount()
    }

    fun getAccountById(id: String): KnownFinancialAccount? {
        return knownAccountRepo.getById(id)
    }

    // --- Account Linked Transactions ---

    suspend fun getLinkedTransactionCount(accountId: String): Int {
        return expenseDao.getExpensesByAccountId(accountId).size
    }

    suspend fun getLinkedTransactions(accountId: String): List<Expense> {
        return expenseDao.getExpensesByAccountId(accountId)
    }

    fun getLinkedTransactionsFlow(accountId: String): Flow<List<Expense>> {
        return expenseDao.getExpensesByAccountIdFlow(accountId)
    }

    // --- Unresolved Transactions ---

    suspend fun getUnresolvedTransactions(): List<Expense> {
        return expenseDao.getExpensesWithoutAccount()
    }

    fun getUnresolvedTransactionsFlow(): Flow<List<Expense>> {
        return expenseDao.getExpensesWithoutAccountFlow()
    }

    suspend fun getUnresolvedWithSuffix(): List<Expense> {
        return expenseDao.getExpensesWithoutAccount().filter { !it.accountSuffix.isNullOrBlank() }
    }

    suspend fun getUnresolvedWithoutSuffix(): List<Expense> {
        return expenseDao.getExpensesWithoutAccount().filter { it.accountSuffix.isNullOrBlank() }
    }

    // --- Compatibility / Picker Candidate Resolution ---

    /**
     * Resolves compatible candidate accounts for an unresolved (or reassignable) transaction.
     *
     * Invariants:
     * - Returns distinct known accounts; never collapses different accounts sharing the same suffix.
     * - If the transaction has an accountSuffix, accounts matching that suffix are prioritized first.
     * - Default candidate selection in UI MUST remain unselected (none).
     */
    fun getCompatibleAccounts(expense: Expense): List<KnownFinancialAccount> {
        val allAccounts = knownAccountRepo.getAll()
        val suffix = expense.accountSuffix?.trim()

        if (suffix.isNullOrBlank()) {
            return allAccounts
        }

        // Prioritize accounts matching suffix, followed by the rest
        val matching = allAccounts.filter { it.accountSuffix == suffix }
        val others = allAccounts.filter { it.accountSuffix != suffix }
        return matching + others
    }

    // --- Manual Assignment, Reassignment, and Unlink ---

    /**
     * Explicitly links a transaction to a known account.
     *
     * Invariants:
     * - Updates ONLY [Expense.accountId].
     * - Preserves [Expense.accountSuffix] and all financial metadata (amount, merchant, date, etc.).
     * - Upgrades account source to [AccountSource.USER_CONFIRMED] without damaging metadata.
     * - NEVER creates an automatic global rule.
     */
    suspend fun assignAccount(expenseId: Int, accountId: String): Result<Expense> {
        val expense = expenseDao.getExpenseById(expenseId)
            ?: return Result.failure(IllegalArgumentException("Expense with id $expenseId not found"))
        val account = knownAccountRepo.getById(accountId)
            ?: return Result.failure(IllegalArgumentException("Account with id $accountId not found"))

        val rowsUpdated = expenseDao.updateAccountId(expenseId, account.id)
        if (rowsUpdated <= 0) {
            return Result.failure(IllegalStateException("Failed to update expense accountId in database"))
        }

        // Upgrade account confidence & source to USER_CONFIRMED
        try {
            knownAccountRepo.upsert(
                account.copy(
                    source = AccountSource.USER_CONFIRMED,
                    confidence = IdentityConfidence.HIGH,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {}

        return Result.success(expense.copy(accountId = account.id))
    }

    /**
     * Reassigns an already-linked transaction to a different known account upon explicit user confirmation.
     *
     * Invariants:
     * - Updates ONLY [Expense.accountId] to [newAccountId].
     * - Preserves all other transaction fields.
     */
    suspend fun reassignAccount(expenseId: Int, newAccountId: String): Result<Expense> {
        val expense = expenseDao.getExpenseById(expenseId)
            ?: return Result.failure(IllegalArgumentException("Expense with id $expenseId not found"))
        val newAccount = knownAccountRepo.getById(newAccountId)
            ?: return Result.failure(IllegalArgumentException("Account with id $newAccountId not found"))

        val rowsUpdated = expenseDao.updateAccountId(expenseId, newAccount.id)
        if (rowsUpdated <= 0) {
            return Result.failure(IllegalStateException("Failed to update expense accountId in database"))
        }

        try {
            knownAccountRepo.upsert(
                newAccount.copy(
                    source = AccountSource.USER_CONFIRMED,
                    confidence = IdentityConfidence.HIGH,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {}

        return Result.success(expense.copy(accountId = newAccount.id))
    }

    /**
     * Unlinks a transaction from its account upon explicit user confirmation.
     *
     * Invariants:
     * - Sets [Expense.accountId] to null.
     * - Preserves [Expense.accountSuffix] unchanged so the transaction can be resolved again.
     * - Does NOT delete the [KnownFinancialAccount].
     */
    suspend fun unlinkAccount(expenseId: Int): Result<Expense> {
        val expense = expenseDao.getExpenseById(expenseId)
            ?: return Result.failure(IllegalArgumentException("Expense with id $expenseId not found"))

        val rowsUpdated = expenseDao.updateAccountId(expenseId, null)
        if (rowsUpdated <= 0) {
            return Result.failure(IllegalStateException("Failed to unlink expense accountId in database"))
        }

        return Result.success(expense.copy(accountId = null))
    }

    companion object {
        fun formatMaskedSuffix(suffix: String): String = "••••$suffix"

        fun formatAccountType(instrumentType: InstrumentType, suffix: String): String {
            return when (instrumentType) {
                InstrumentType.BANK_ACCOUNT -> "Bank account ••••$suffix"
                InstrumentType.CARD -> "Card ••••$suffix"
                InstrumentType.UNKNOWN -> "Account ••••$suffix"
            }
        }

        fun formatAccountSource(source: AccountSource): String {
            return when (source) {
                AccountSource.HISTORICAL_SMS -> "Detected from SMS"
                AccountSource.LIVE_NOTIFICATION -> "Detected from notification"
                AccountSource.USER_CONFIRMED -> "Confirmed by you"
                AccountSource.OTHER -> "Other"
            }
        }
    }
}
