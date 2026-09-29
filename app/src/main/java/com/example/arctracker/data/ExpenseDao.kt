package com.example.arctracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("SELECT * FROM expenses WHERE relationshipId = :relationshipId")
    suspend fun getExpensesByRelationshipId(relationshipId: String): List<Expense>

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

    @Query("UPDATE expenses SET categoryId = :categoryId, tag = :categoryName WHERE id = :id")
    suspend fun updateCategoryMetadata(id: Int, categoryId: String?, categoryName: String?): Int

    @Query("UPDATE expenses SET categoryId = :newCategoryId, tag = :newCategoryName WHERE categoryId = :oldCategoryId")
    suspend fun reassignCategoryId(oldCategoryId: String, newCategoryId: String, newCategoryName: String): Int

    @Query("UPDATE expenses SET categoryId = NULL, tag = NULL WHERE categoryId = :categoryId")
    suspend fun clearCategoryId(categoryId: String): Int

    @Query("UPDATE expenses SET categoryId = :categoryId, tag = :categoryName WHERE id IN (:expenseIds)")
    suspend fun bulkUpdateCategoryId(expenseIds: List<Int>, categoryId: String?, categoryName: String?): Int

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
