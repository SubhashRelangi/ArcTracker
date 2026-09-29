package com.subhashrelangi.arctracker.data

import androidx.room.*
import com.subhashrelangi.arctracker.service.InstrumentType

/**
 * Data Access Object for [KnownFinancialAccount] (Milestone 2).
 */
@Dao
interface KnownFinancialAccountDao {

    @Query("SELECT * FROM known_financial_accounts WHERE id = :id LIMIT 1")
    fun getById(id: String): KnownFinancialAccount?

    @Query("""
        SELECT * FROM known_financial_accounts 
        WHERE ((:institutionId IS NULL AND institutionId IS NULL) OR institutionId = :institutionId) 
          AND instrumentType = :instrumentType 
          AND accountSuffix = :accountSuffix 
        LIMIT 1
    """)
    fun findByCanonicalIdentity(
        institutionId: String?,
        instrumentType: InstrumentType,
        accountSuffix: String
    ): KnownFinancialAccount?

    @Query("""
        SELECT * FROM known_financial_accounts 
        WHERE institutionId = :institutionId AND accountSuffix = :accountSuffix
        ORDER BY updatedAt DESC
    """)
    fun findByInstitutionAndSuffix(
        institutionId: String,
        accountSuffix: String
    ): List<KnownFinancialAccount>

    @Query("""
        SELECT * FROM known_financial_accounts 
        WHERE accountSuffix = :accountSuffix 
        ORDER BY updatedAt DESC
    """)
    fun findBySuffix(accountSuffix: String): List<KnownFinancialAccount>

    @Query("SELECT * FROM known_financial_accounts ORDER BY updatedAt DESC")
    fun getAll(): List<KnownFinancialAccount>

    @Query("SELECT * FROM known_financial_accounts ORDER BY updatedAt DESC")
    fun getAllFlow(): kotlinx.coroutines.flow.Flow<List<KnownFinancialAccount>>

    @Query("SELECT COUNT(*) FROM known_financial_accounts")
    fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(account: KnownFinancialAccount): Long

    @Update
    fun update(account: KnownFinancialAccount): Int

    @Delete
    fun delete(account: KnownFinancialAccount): Int

    @Query("DELETE FROM known_financial_accounts WHERE id = :id")
    fun deleteById(id: String): Int

    @Query("DELETE FROM known_financial_accounts")
    fun deleteAll(): Int
}
