package com.subhashrelangi.arctracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [TransactionCategory] entities.
 */
@Dao
interface TransactionCategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: TransactionCategory): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<TransactionCategory>): List<Long>

    @Update
    suspend fun update(category: TransactionCategory): Int

    @Delete
    suspend fun delete(category: TransactionCategory): Int

    @Query("DELETE FROM transaction_categories WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM transaction_categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TransactionCategory?

    @Query("SELECT * FROM transaction_categories ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllFlow(): Flow<List<TransactionCategory>>

    @Query("SELECT * FROM transaction_categories ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAll(): List<TransactionCategory>

    @Query("SELECT * FROM transaction_categories WHERE isArchived = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun getActiveFlow(): Flow<List<TransactionCategory>>

    @Query("SELECT * FROM transaction_categories WHERE isArchived = 0 ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getActive(): List<TransactionCategory>

    @Query("SELECT * FROM transaction_categories WHERE isArchived = 1 ORDER BY sortOrder ASC, createdAt ASC")
    fun getArchivedFlow(): Flow<List<TransactionCategory>>

    @Query("SELECT * FROM transaction_categories WHERE isArchived = 1 ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getArchived(): List<TransactionCategory>

    @Query("SELECT * FROM transaction_categories WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun findByName(name: String): TransactionCategory?

    @Query("SELECT COUNT(*) FROM transaction_categories")
    suspend fun count(): Int

    @Query("SELECT MAX(sortOrder) FROM transaction_categories")
    suspend fun getMaxSortOrder(): Int?
}
