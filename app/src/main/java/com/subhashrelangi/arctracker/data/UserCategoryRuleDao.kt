package com.subhashrelangi.arctracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserCategoryRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: UserCategoryRule): Long

    @Update
    suspend fun update(rule: UserCategoryRule)

    @Delete
    suspend fun delete(rule: UserCategoryRule)

    @Query("DELETE FROM user_category_rules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM user_category_rules WHERE id = :id")
    suspend fun getById(id: String): UserCategoryRule?

    @Query("SELECT * FROM user_category_rules ORDER BY priority DESC, createdAt ASC")
    suspend fun getAll(): List<UserCategoryRule>

    @Query("SELECT * FROM user_category_rules ORDER BY priority DESC, createdAt ASC")
    fun getAllFlow(): Flow<List<UserCategoryRule>>

    @Query("SELECT * FROM user_category_rules WHERE isEnabled = 1 ORDER BY priority DESC, createdAt ASC")
    suspend fun getActiveRules(): List<UserCategoryRule>

    @Query("SELECT * FROM user_category_rules WHERE isEnabled = 1 ORDER BY priority DESC, createdAt ASC")
    fun getActiveRulesFlow(): Flow<List<UserCategoryRule>>

    @Query("SELECT * FROM user_category_rules WHERE categoryId = :categoryId")
    suspend fun getRulesForCategory(categoryId: String): List<UserCategoryRule>

    @Query("UPDATE user_category_rules SET isEnabled = 0, updatedAt = :updatedAt WHERE categoryId = :categoryId")
    suspend fun disableRulesForCategory(categoryId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM user_category_rules")
    suspend fun count(): Int
}
