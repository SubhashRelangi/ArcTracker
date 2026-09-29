package com.subhashrelangi.arctracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantAliasDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alias: MerchantAlias): Long

    @Update
    suspend fun update(alias: MerchantAlias)

    @Delete
    suspend fun delete(alias: MerchantAlias)

    @Query("DELETE FROM merchant_aliases WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM merchant_aliases WHERE id = :id")
    suspend fun getById(id: String): MerchantAlias?

    @Query("SELECT * FROM merchant_aliases ORDER BY canonicalMerchant ASC, alias ASC")
    suspend fun getAll(): List<MerchantAlias>

    @Query("SELECT * FROM merchant_aliases ORDER BY canonicalMerchant ASC, alias ASC")
    fun getAllFlow(): Flow<List<MerchantAlias>>

    @Query("SELECT * FROM merchant_aliases WHERE isEnabled = 1 ORDER BY canonicalMerchant ASC, alias ASC")
    suspend fun getActiveAliases(): List<MerchantAlias>

    @Query("SELECT * FROM merchant_aliases WHERE isEnabled = 1 ORDER BY canonicalMerchant ASC, alias ASC")
    fun getActiveAliasesFlow(): Flow<List<MerchantAlias>>

    @Query("SELECT * FROM merchant_aliases WHERE normalizedAlias = :normalizedAlias LIMIT 1")
    suspend fun getByNormalizedAlias(normalizedAlias: String): MerchantAlias?

    @Query("SELECT COUNT(*) FROM merchant_aliases")
    suspend fun count(): Int
}
