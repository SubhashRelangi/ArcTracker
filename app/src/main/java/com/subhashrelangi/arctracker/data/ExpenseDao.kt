package com.subhashrelangi.arctracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [Expense] entities in the ArcTracker Room database.
 */
@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(expense: Expense): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(expenses: List<Expense>): List<Long>

    @Update
    suspend fun update(expense: Expense): Int

    @Delete
    suspend fun delete(expense: Expense): Int

    @Query("SELECT * FROM expenses ORDER BY dateMillis DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY dateMillis DESC")
    suspend fun getAllExpensesList(): List<Expense>

    @Query("SELECT * FROM expenses WHERE isPending = 1 ORDER BY dateMillis DESC")
    fun getPendingExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE isPending = 1 ORDER BY dateMillis DESC")
    suspend fun getPendingExpensesList(): List<Expense>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: Int): Expense?

    @Query("SELECT * FROM expenses WHERE notificationKey = :key LIMIT 1")
    suspend fun getExpenseByNotificationKey(key: String): Expense?

    @Query("SELECT * FROM expenses WHERE notificationKey = :key AND notificationKey != ''")
    suspend fun findByNotificationKey(key: String): List<Expense>

    @Query("SELECT * FROM expenses WHERE dateMillis BETWEEN :startMillis AND :endMillis ORDER BY dateMillis DESC")
    suspend fun getExpensesBetween(startMillis: Long, endMillis: Long): List<Expense>

    @Query("SELECT * FROM expenses WHERE dateMillis BETWEEN :startMillis AND :endMillis ORDER BY dateMillis DESC")
    fun getExpensesBetweenFlow(startMillis: Long, endMillis: Long): Flow<List<Expense>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE dateMillis BETWEEN :startMillis AND :endMillis AND type = :type AND isPending = 0 AND (relationshipType IS NULL OR relationshipType != 'SELF_TRANSFER')")
    suspend fun getTotalAmountBetween(startMillis: Long, endMillis: Long, type: String): Double

    @Query("SELECT * FROM expenses WHERE relationshipId = :relationshipId")
    suspend fun getExpensesByRelationshipId(relationshipId: String): List<Expense>

    /**
     * Atomically persists a self-transfer candidate and updates its matched counterpart in a single Room transaction.
     *
     * Invariants:
     * - The counterpart record must exist (by notificationKey or integer id).
     * - The new transfer expense is inserted.
     * - The counterpart is linked with the provided [relationshipType] and [relationshipId].
     * - If counterpart retrieval, candidate insert, or counterpart update fails, the entire transaction rolls back.
     */
    @Transaction
    suspend fun insertSelfTransferPair(
        newExpense: Expense,
        counterpartRecordId: String,
        relationshipType: String,
        relationshipId: String
    ): Expense {
        val existing = getExpenseByNotificationKey(counterpartRecordId)
            ?: counterpartRecordId.toIntOrNull()?.let { getExpenseById(it) }
            ?: throw IllegalStateException("Self-transfer counterpart record not found: $counterpartRecordId")

        val newId = insert(newExpense)
        val saved = newExpense.copy(id = newId.toInt())

        if (existing.relationshipType == null || existing.relationshipId == null) {
            val updatedExisting = existing.copy(
                relationshipType = relationshipType,
                relationshipId = relationshipId
            )
            val updatedRows = update(updatedExisting)
            if (updatedRows <= 0) {
                throw IllegalStateException("Failed to update self-transfer counterpart record: $counterpartRecordId")
            }
        }

        return saved
    }

    /**
     * Atomically deletes an expense and disassociates its self-transfer counterpart if present.
     *
     * Invariants:
     * - If [expenseId] does not exist, returns 0.
     * - If the record has [relationshipType] == "SELF_TRANSFER" and a non-blank [relationshipId],
     *   locates the paired counterpart with opposite direction and matching amount, and clears
     *   its [relationshipType] and [relationshipId] so it is no longer excluded from spending totals.
     * - If counterpart is missing, deletes the requested record safely.
     * - Preserves all other transaction fields on the surviving record.
     * - Executes inside a single Room database transaction; rolls back completely on any failure.
     */
    @Transaction
    suspend fun deleteSelfTransferAware(expenseId: Int): Int {
        val existing = getExpenseById(expenseId) ?: return 0

        val relType = existing.relationshipType
        val relId = existing.relationshipId

        if (relType == "SELF_TRANSFER" && !relId.isNullOrBlank()) {
            val candidateCounterparts = getExpensesByRelationshipId(relId).filter { it.id != expenseId }
            val targetCounterpart = if (candidateCounterparts.size <= 1) {
                candidateCounterparts.firstOrNull()
            } else {
                candidateCounterparts.find {
                    !it.type.equals(existing.type, ignoreCase = true) &&
                    kotlin.math.abs(it.amount - existing.amount) < 0.01
                } ?: candidateCounterparts.firstOrNull()
            }

            if (targetCounterpart != null && targetCounterpart.relationshipType == "SELF_TRANSFER") {
                val cleared = targetCounterpart.copy(
                    relationshipType = null,
                    relationshipId = null
                )
                val updated = update(cleared)
                if (updated <= 0) {
                    throw IllegalStateException("Failed to disassociate self-transfer counterpart id=${targetCounterpart.id}")
                }
            }
        }

        val deletedRows = delete(existing)
        if (deletedRows <= 0) {
            throw IllegalStateException("Failed to delete expense id=$expenseId")
        }
        return deletedRows
    }

    @Query("UPDATE expenses SET accountId = :accountId WHERE id = :id")
    suspend fun updateAccountId(id: Int, accountId: String?): Int

    @Query("UPDATE expenses SET accountId = :accountId, accountSuffix = :accountSuffix WHERE id = :id")
    suspend fun updateAccountMetadata(id: Int, accountId: String?, accountSuffix: String?): Int

    @Query("UPDATE expenses SET accountId = :newAccountId WHERE accountId = :oldAccountId")
    suspend fun reassignAccountId(oldAccountId: String, newAccountId: String): Int

    @Query("UPDATE expenses SET accountId = NULL WHERE accountId = :accountId")
    suspend fun clearAccountIdForAccount(accountId: String): Int

    @Query("UPDATE expenses SET accountId = :accountId WHERE id IN (:expenseIds)")
    suspend fun bulkUpdateAccountId(expenseIds: List<Int>, accountId: String?): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE accountId = :accountId")
    suspend fun countByAccountId(accountId: String): Int

    @Query("SELECT * FROM expenses WHERE accountId IS NULL ORDER BY dateMillis DESC")
    suspend fun getExpensesWithoutAccount(): List<Expense>

    @Query("SELECT * FROM expenses WHERE accountId IS NULL ORDER BY dateMillis DESC")
    fun getExpensesWithoutAccountFlow(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE accountId = :accountId ORDER BY dateMillis DESC")
    suspend fun getExpensesByAccountId(accountId: String): List<Expense>

    @Query("SELECT * FROM expenses WHERE accountId = :accountId ORDER BY dateMillis DESC")
    fun getExpensesByAccountIdFlow(accountId: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE accountSuffix = :suffix ORDER BY dateMillis DESC")
    suspend fun getExpensesByAccountSuffix(suffix: String): List<Expense>

    @Query("SELECT * FROM expenses WHERE tag = :category ORDER BY dateMillis DESC")
    suspend fun getExpensesByCategory(category: String): List<Expense>

    @Query("SELECT * FROM expenses WHERE tag = :category ORDER BY dateMillis DESC")
    fun getExpensesByCategoryFlow(category: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE merchant LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%' ORDER BY dateMillis DESC")
    suspend fun searchExpensesList(query: String): List<Expense>

    @Query("SELECT * FROM expenses WHERE merchant LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%' ORDER BY dateMillis DESC")
    fun searchExpensesFlow(query: String): Flow<List<Expense>>

    @Query("UPDATE expenses SET categoryId = :categoryId, tag = :categoryName, categorySource = :categorySource WHERE id = :id")
    suspend fun updateCategoryMetadata(id: Int, categoryId: String?, categoryName: String?, categorySource: String = "USER_ASSIGNED"): Int

    @Query("UPDATE expenses SET categoryId = :categoryId, tag = :categoryName, categorySource = :categorySource WHERE id = :id AND categorySource != 'USER_ASSIGNED'")
    suspend fun updateCategoryMetadataIfNotUserAssigned(id: Int, categoryId: String?, categoryName: String?, categorySource: String = "INFERRED"): Int

    @Query("UPDATE expenses SET categoryId = :newCategoryId, tag = :newCategoryName, categorySource = :categorySource WHERE categoryId = :oldCategoryId")
    suspend fun reassignCategoryId(oldCategoryId: String, newCategoryId: String, newCategoryName: String, categorySource: String = "USER_ASSIGNED"): Int

    @Query("UPDATE expenses SET categoryId = NULL, tag = NULL, categorySource = 'NONE' WHERE categoryId = :categoryId")
    suspend fun clearCategoryId(categoryId: String): Int

    @Query("UPDATE expenses SET categoryId = :categoryId, tag = :categoryName, categorySource = :categorySource WHERE id IN (:expenseIds)")
    suspend fun bulkUpdateCategoryId(expenseIds: List<Int>, categoryId: String?, categoryName: String?, categorySource: String = "USER_ASSIGNED"): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE categoryId = :categoryId")
    suspend fun countByCategoryId(categoryId: String): Int

    @Query("SELECT * FROM expenses WHERE categoryId = :categoryId ORDER BY dateMillis DESC")
    suspend fun getExpensesByCategoryId(categoryId: String): List<Expense>

    @Query("SELECT * FROM expenses WHERE categoryId = :categoryId ORDER BY dateMillis DESC")
    fun getExpensesByCategoryIdFlow(categoryId: String): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE categoryId IS NULL OR categoryId = '' ORDER BY dateMillis DESC")
    suspend fun getUncategorizedExpenses(): List<Expense>

    @Query("SELECT * FROM expenses WHERE categoryId IS NULL OR categoryId = '' ORDER BY dateMillis DESC")
    fun getUncategorizedExpensesFlow(): Flow<List<Expense>>

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun getCount(): Int

    @Query("DELETE FROM expenses")
    suspend fun clearAll(): Int
}
