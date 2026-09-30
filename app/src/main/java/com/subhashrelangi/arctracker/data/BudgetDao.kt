package com.subhashrelangi.arctracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [Budget] and [BudgetAlertState] entities in Room.
 */
@Dao
interface BudgetDao {

    // ==========================================
    // Budget Operations
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: Budget): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<Budget>): List<Long>

    @Update
    suspend fun update(budget: Budget): Int

    @Delete
    suspend fun delete(budget: Budget): Int

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM budgets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Budget?

    @Query("SELECT * FROM budgets WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<Budget?>

    @Query("SELECT * FROM budgets ORDER BY createdAt DESC")
    suspend fun getAll(): List<Budget>

    @Query("SELECT * FROM budgets ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE isEnabled = 1 ORDER BY createdAt DESC")
    suspend fun getActive(): List<Budget>

    @Query("SELECT * FROM budgets WHERE isEnabled = 1 ORDER BY createdAt DESC")
    fun getActiveFlow(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId")
    suspend fun getByCategory(categoryId: String): List<Budget>

    @Query("UPDATE budgets SET isEnabled = 0, updatedAt = :timestamp WHERE categoryId = :categoryId")
    suspend fun disableBudgetsForCategory(categoryId: String, timestamp: Long = System.currentTimeMillis()): Int

    @Query("UPDATE budgets SET isEnabled = :isEnabled, updatedAt = :timestamp WHERE id = :id")
    suspend fun setBudgetEnabled(id: String, isEnabled: Boolean, timestamp: Long = System.currentTimeMillis()): Int

    @Query("SELECT COUNT(*) FROM budgets")
    suspend fun getCount(): Int

    @Query("DELETE FROM budgets")
    suspend fun clearAll(): Int

    // ==========================================
    // Alert State Operations
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlertState(state: BudgetAlertState): Long

    @Query("SELECT * FROM budget_alert_states WHERE budgetId = :budgetId AND periodStart = :periodStart LIMIT 1")
    suspend fun getAlertState(budgetId: String, periodStart: Long): BudgetAlertState?

    @Query("SELECT * FROM budget_alert_states WHERE id = :id LIMIT 1")
    suspend fun getAlertStateById(id: String): BudgetAlertState?

    @Query("DELETE FROM budget_alert_states WHERE budgetId = :budgetId")
    suspend fun deleteAlertStatesForBudget(budgetId: String): Int

    @Query("DELETE FROM budget_alert_states")
    suspend fun clearAllAlertStates(): Int
}
