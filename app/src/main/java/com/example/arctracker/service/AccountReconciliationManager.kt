package com.example.arctracker.service

import androidx.room.withTransaction
import com.example.arctracker.data.AccountSource
import com.example.arctracker.data.AppDatabase
import com.example.arctracker.data.Expense
import com.example.arctracker.data.ExpenseDao
import com.example.arctracker.data.KnownFinancialAccount
import com.example.arctracker.data.KnownFinancialAccountRepository
import kotlinx.coroutines.flow.Flow

data class MergeValidationResult(
    val isCompatible: Boolean,
    val reason: String? = null
)

/**
 * Manager facilitating user-facing account management, manual transaction reconciliation,
 * reassignment, unlinking, account lifecycle management (delete/merge), and bulk reconciliation (Milestones 6 & 7).
 */
class AccountReconciliationManager(
    private val expenseDao: ExpenseDao,
    private val knownAccountRepo: KnownFinancialAccountRepository,
    private val database: AppDatabase? = null
) {

    private suspend fun <T> runTransaction(block: suspend () -> T): T {
        val db = database
        return if (db != null) {
            db.withTransaction { block() }
        } else {
            block()
        }
    }

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
        return expenseDao.countByAccountId(accountId)
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

    fun getCompatibleAccounts(expense: Expense): List<KnownFinancialAccount> {
        val allAccounts = knownAccountRepo.getAll()
        val suffix = expense.accountSuffix?.trim()

        if (suffix.isNullOrBlank()) {
            return allAccounts
        }

        val matching = allAccounts.filter { it.accountSuffix == suffix }
        val others = allAccounts.filter { it.accountSuffix != suffix }
        return matching + others
    }

    // --- Single Transaction Manual Assignment, Reassignment, and Unlink ---

    suspend fun assignAccount(expenseId: Int, accountId: String): Result<Expense> = runTransaction {
        val expense = expenseDao.getExpenseById(expenseId)
            ?: return@runTransaction Result.failure(IllegalArgumentException("Expense with id $expenseId not found"))
        val account = knownAccountRepo.getById(accountId)
            ?: return@runTransaction Result.failure(IllegalArgumentException("Account with id $accountId not found"))

        val rowsUpdated = expenseDao.updateAccountId(expenseId, account.id)
        if (rowsUpdated <= 0) {
            return@runTransaction Result.failure(IllegalStateException("Failed to update expense accountId in database"))
        }

        try {
            knownAccountRepo.upsert(
                account.copy(
                    source = AccountSource.USER_CONFIRMED,
                    confidence = IdentityConfidence.HIGH,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {}

        Result.success(expense.copy(accountId = account.id))
    }

    suspend fun reassignAccount(expenseId: Int, newAccountId: String): Result<Expense> = runTransaction {
        val expense = expenseDao.getExpenseById(expenseId)
            ?: return@runTransaction Result.failure(IllegalArgumentException("Expense with id $expenseId not found"))
        val newAccount = knownAccountRepo.getById(newAccountId)
            ?: return@runTransaction Result.failure(IllegalArgumentException("Account with id $newAccountId not found"))

        val rowsUpdated = expenseDao.updateAccountId(expenseId, newAccount.id)
        if (rowsUpdated <= 0) {
            return@runTransaction Result.failure(IllegalStateException("Failed to update expense accountId in database"))
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

        Result.success(expense.copy(accountId = newAccount.id))
    }

    suspend fun unlinkAccount(expenseId: Int): Result<Expense> = runTransaction {
        val expense = expenseDao.getExpenseById(expenseId)
            ?: return@runTransaction Result.failure(IllegalArgumentException("Expense with id $expenseId not found"))

        val rowsUpdated = expenseDao.updateAccountId(expenseId, null)
        if (rowsUpdated <= 0) {
            return@runTransaction Result.failure(IllegalStateException("Failed to unlink expense accountId in database"))
        }

        Result.success(expense.copy(accountId = null))
    }

    // --- Bulk Manual Transaction Reconciliation (Milestone 7) ---

    suspend fun bulkAssign(expenseIds: List<Int>, targetAccountId: String): Result<Int> = runTransaction {
        if (expenseIds.isEmpty()) return@runTransaction Result.success(0)
        val targetAccount = knownAccountRepo.getById(targetAccountId)
            ?: return@runTransaction Result.failure(IllegalArgumentException("Target account with id $targetAccountId not found"))

        val count = expenseDao.bulkUpdateAccountId(expenseIds, targetAccountId)

        try {
            knownAccountRepo.upsert(
                targetAccount.copy(
                    source = AccountSource.USER_CONFIRMED,
                    confidence = IdentityConfidence.HIGH,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {}

        Result.success(count)
    }

    suspend fun bulkReassign(expenseIds: List<Int>, newAccountId: String): Result<Int> = runTransaction {
        if (expenseIds.isEmpty()) return@runTransaction Result.success(0)
        val newAccount = knownAccountRepo.getById(newAccountId)
            ?: return@runTransaction Result.failure(IllegalArgumentException("Target account with id $newAccountId not found"))

        val count = expenseDao.bulkUpdateAccountId(expenseIds, newAccountId)

        try {
            knownAccountRepo.upsert(
                newAccount.copy(
                    source = AccountSource.USER_CONFIRMED,
                    confidence = IdentityConfidence.HIGH,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {}

        Result.success(count)
    }

    suspend fun bulkUnlink(expenseIds: List<Int>): Result<Int> = runTransaction {
        if (expenseIds.isEmpty()) return@runTransaction Result.success(0)
        val count = expenseDao.bulkUpdateAccountId(expenseIds, null)
        Result.success(count)
    }

    // --- Safe Account Lifecycle Management: Delete & Merge (Milestone 7) ---

    /**
     * Safely deletes an account from the registry.
     *
     * Invariants:
     * - Disallows dangling references: Sets [Expense.accountId] to null on all referencing expenses first.
     * - Preserves [Expense.accountSuffix], amount, merchant, timestamp, type, notes, and relationship fields.
     * - Atomic execution inside transaction: expenses cleared -> account deleted.
     */
    suspend fun deleteAccount(accountId: String): Result<Int> = runTransaction {
        val account = knownAccountRepo.getById(accountId)
            ?: return@runTransaction Result.failure(IllegalArgumentException("Account with id $accountId not found"))

        // 1. Clear accountId on referencing expenses
        val unlinkedCount = expenseDao.clearAccountIdForAccount(accountId)

        // 2. Delete account from registry
        val deleted = knownAccountRepo.deleteById(accountId)
        if (!deleted) {
            throw IllegalStateException("Failed to delete account $accountId from repository")
        }

        Result.success(unlinkedCount)
    }

    /**
     * Validates whether source account can be merged into target account.
     *
     * Preconditions (Parts 6 & 11):
     * - source.id != target.id
     * - source.instrumentType == target.instrumentType (or UNKNOWN) -> BANK_ACCOUNT != CARD
     * - source.accountSuffix == target.accountSuffix
     * - Conflicting known institution identities are blocked.
     */
    fun validateMergeCompatibility(source: KnownFinancialAccount, target: KnownFinancialAccount): MergeValidationResult {
        if (source.id == target.id) {
            return MergeValidationResult(false, "Cannot merge an account into itself.")
        }
        if (source.instrumentType != target.instrumentType &&
            source.instrumentType != InstrumentType.UNKNOWN &&
            target.instrumentType != InstrumentType.UNKNOWN
        ) {
            return MergeValidationResult(
                false,
                "Cannot merge different instrument types (${source.instrumentType} vs ${target.instrumentType})."
            )
        }
        if (source.accountSuffix != target.accountSuffix) {
            return MergeValidationResult(
                false,
                "Cannot merge accounts with different account suffixes (••••${source.accountSuffix} vs ••••${target.accountSuffix})."
            )
        }
        val sourceInst = source.institutionId?.trim()?.lowercase()
        val targetInst = target.institutionId?.trim()?.lowercase()
        if (!sourceInst.isNullOrBlank() && !targetInst.isNullOrBlank() &&
            sourceInst != targetInst &&
            sourceInst != "unknown" && targetInst != "unknown"
        ) {
            return MergeValidationResult(
                false,
                "These accounts have conflicting institution identities (${source.institutionName ?: sourceInst} vs ${target.institutionName ?: targetInst}). They should remain separate unless verified."
            )
        }
        return MergeValidationResult(true, null)
    }

    /**
     * Retrieves valid merge candidate targets for a given source account.
     */
    fun getMergeCandidates(sourceAccount: KnownFinancialAccount): List<KnownFinancialAccount> {
        val allAccounts = knownAccountRepo.getAll()
        return allAccounts.filter { candidate ->
            validateMergeCompatibility(sourceAccount, candidate).isCompatible
        }
    }

    /**
     * Merges a source account into a target account upon explicit user decision.
     *
     * Invariants (Parts 9 & 10):
     * - Validates merge compatibility.
     * - Reassigns all [Expense.accountId] from sourceAccountId to targetAccountId.
     * - Deletes source account.
     * - Preserves target account canonical identity; upgrades target source to [AccountSource.USER_CONFIRMED].
     * - Never deletes expenses or alters amount, merchant, timestamp, suffix, or relationships.
     * - Fully transactional: reassign -> delete source account.
     */
    suspend fun mergeAccounts(sourceAccountId: String, targetAccountId: String): Result<Int> = runTransaction {
        if (sourceAccountId == targetAccountId) {
            return@runTransaction Result.failure(IllegalArgumentException("Cannot merge an account into itself"))
        }
        val sourceAccount = knownAccountRepo.getById(sourceAccountId)
            ?: return@runTransaction Result.failure(IllegalArgumentException("Source account not found"))
        val targetAccount = knownAccountRepo.getById(targetAccountId)
            ?: return@runTransaction Result.failure(IllegalArgumentException("Target account not found"))

        val validation = validateMergeCompatibility(sourceAccount, targetAccount)
        if (!validation.isCompatible) {
            return@runTransaction Result.failure(IllegalStateException(validation.reason ?: "Incompatible accounts"))
        }

        // 1. Reassign all expenses from source to target
        val reassignedCount = expenseDao.reassignAccountId(sourceAccountId, targetAccountId)

        // 2. Remove source account from repository
        val deleted = knownAccountRepo.deleteById(sourceAccountId)
        if (!deleted) {
            throw IllegalStateException("Failed to delete source account $sourceAccountId during merge")
        }

        // 3. Upgrade target account to USER_CONFIRMED
        try {
            knownAccountRepo.upsert(
                targetAccount.copy(
                    source = AccountSource.USER_CONFIRMED,
                    confidence = IdentityConfidence.HIGH,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } catch (_: Exception) {}

        Result.success(reassignedCount)
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
