package com.example.arctracker.service

import com.example.arctracker.data.MerchantAlias
import com.example.arctracker.data.MerchantAliasDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Domain manager for merchant aliases (Milestone 11).
 *
 * Responsibilities:
 * - Normalizes merchant variations into canonical merchant identities.
 * - Enforces validation, uniqueness, and conflict safety.
 * - Prevents alias chaining (A -> B -> C) and self-references (A -> A).
 * - High-speed in-memory resolution for the category inference engine.
 * - Keeps merchant identity strictly separate from category assignment.
 */
class MerchantAliasManager(
    private val aliasDao: MerchantAliasDao
) {
    private val mutex = Mutex()
    private var activeCache: List<MerchantAlias>? = null

    /**
     * Creates a new merchant alias after rigorous validation.
     */
    suspend fun createAlias(
        alias: String,
        canonicalMerchant: String
    ): Result<MerchantAlias> = mutex.withLock {
        val trimmedAlias = alias.trim()
        val trimmedCanonical = canonicalMerchant.trim()

        if (trimmedAlias.isBlank()) {
            return Result.failure(IllegalArgumentException("Alias cannot be blank"))
        }
        if (trimmedCanonical.isBlank()) {
            return Result.failure(IllegalArgumentException("Canonical merchant name cannot be blank"))
        }

        val normAlias = MerchantNormalizer.normalize(trimmedAlias, stripPrefixes = true)
        val normCanonical = MerchantNormalizer.normalize(trimmedCanonical, stripPrefixes = true)

        if (normAlias.isBlank() || normCanonical.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid alias or canonical merchant name after normalization"))
        }

        // 1. Self-reference check
        if (normAlias == normCanonical) {
            return Result.failure(IllegalArgumentException("An alias cannot point to itself (alias and canonical merchant are identical)"))
        }

        val existingAliases = aliasDao.getAll()

        // 2. Duplicate and Conflict check
        val existingMatch = existingAliases.find { it.normalizedAlias == normAlias }
        if (existingMatch != null) {
            val existingCanonicalNorm = MerchantNormalizer.normalize(existingMatch.canonicalMerchant, stripPrefixes = true)
            return if (existingCanonicalNorm == normCanonical) {
                Result.failure(IllegalArgumentException("Duplicate: Alias '$trimmedAlias' for '$trimmedCanonical' already exists"))
            } else {
                Result.failure(IllegalArgumentException("Conflict: Alias '$trimmedAlias' already points to '${existingMatch.canonicalMerchant}'"))
            }
        }

        // 3. Chaining prevention (A -> B, B -> C)
        // Check if canonicalMerchant is already an alias
        val canonicalIsAlias = existingAliases.any { it.normalizedAlias == normCanonical }
        if (canonicalIsAlias) {
            return Result.failure(IllegalArgumentException("Cannot chain aliases: '$trimmedCanonical' is already an alias for another merchant"))
        }

        // Check if this alias is already used as a canonical merchant
        val aliasIsCanonical = existingAliases.any {
            MerchantNormalizer.normalize(it.canonicalMerchant, stripPrefixes = true) == normAlias
        }
        if (aliasIsCanonical) {
            return Result.failure(IllegalArgumentException("Cannot create alias: '$trimmedAlias' is already configured as a canonical merchant for existing aliases"))
        }

        val newAlias = MerchantAlias(
            id = UUID.randomUUID().toString(),
            alias = trimmedAlias,
            canonicalMerchant = trimmedCanonical,
            normalizedAlias = normAlias,
            isEnabled = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        aliasDao.insert(newAlias)
        invalidateCache()
        Result.success(newAlias)
    }

    /**
     * Updates an existing alias.
     */
    suspend fun updateAlias(
        id: String,
        alias: String,
        canonicalMerchant: String,
        isEnabled: Boolean
    ): Result<MerchantAlias> = mutex.withLock {
        val existing = aliasDao.getById(id)
            ?: return Result.failure(IllegalArgumentException("Alias not found with id: $id"))

        val trimmedAlias = alias.trim()
        val trimmedCanonical = canonicalMerchant.trim()

        if (trimmedAlias.isBlank()) {
            return Result.failure(IllegalArgumentException("Alias cannot be blank"))
        }
        if (trimmedCanonical.isBlank()) {
            return Result.failure(IllegalArgumentException("Canonical merchant name cannot be blank"))
        }

        val normAlias = MerchantNormalizer.normalize(trimmedAlias, stripPrefixes = true)
        val normCanonical = MerchantNormalizer.normalize(trimmedCanonical, stripPrefixes = true)

        if (normAlias == normCanonical) {
            return Result.failure(IllegalArgumentException("An alias cannot point to itself"))
        }

        val otherAliases = aliasDao.getAll().filter { it.id != id }

        // Conflict check with other aliases
        val conflict = otherAliases.find { it.normalizedAlias == normAlias }
        if (conflict != null) {
            return Result.failure(IllegalArgumentException("Conflict: Alias '$trimmedAlias' is already used for '${conflict.canonicalMerchant}'"))
        }

        // Chaining check
        if (otherAliases.any { it.normalizedAlias == normCanonical }) {
            return Result.failure(IllegalArgumentException("Cannot chain aliases: '$trimmedCanonical' is already an alias"))
        }
        if (otherAliases.any { MerchantNormalizer.normalize(it.canonicalMerchant, stripPrefixes = true) == normAlias }) {
            return Result.failure(IllegalArgumentException("Cannot create alias: '$trimmedAlias' is already used as a canonical merchant"))
        }

        val updated = existing.copy(
            alias = trimmedAlias,
            canonicalMerchant = trimmedCanonical,
            normalizedAlias = normAlias,
            isEnabled = isEnabled,
            updatedAt = System.currentTimeMillis()
        )

        aliasDao.update(updated)
        invalidateCache()
        Result.success(updated)
    }

    /**
     * Toggles an alias enabled/disabled.
     */
    suspend fun setAliasEnabled(id: String, enabled: Boolean): Result<Unit> = mutex.withLock {
        val existing = aliasDao.getById(id)
            ?: return Result.failure(IllegalArgumentException("Alias not found with id: $id"))

        val updated = existing.copy(isEnabled = enabled, updatedAt = System.currentTimeMillis())
        aliasDao.update(updated)
        invalidateCache()
        Result.success(Unit)
    }

    /**
     * Deletes an alias safely without altering historical transactions.
     */
    suspend fun deleteAlias(id: String): Result<Unit> = mutex.withLock {
        aliasDao.deleteById(id)
        invalidateCache()
        Result.success(Unit)
    }

    /**
     * Returns all aliases as a continuous reactive Flow.
     */
    fun getAllFlow(): Flow<List<MerchantAlias>> = aliasDao.getAllFlow()

    /**
     * Returns all active aliases for high-speed local inference.
     */
    suspend fun getActiveAliases(): List<MerchantAlias> = mutex.withLock {
        activeCache ?: run {
            val list = aliasDao.getActiveAliases()
            activeCache = list
            list
        }
    }

    /**
     * Resolves an incoming merchant name using the active alias registry.
     * Returns Pair(canonicalMerchantName, matchedAliasOrNull).
     */
    fun resolveAlias(
        rawMerchant: String?,
        activeAliases: List<MerchantAlias>
    ): Pair<String?, MerchantAlias?> {
        if (rawMerchant.isNullOrBlank() || activeAliases.isEmpty()) {
            return Pair(rawMerchant, null)
        }

        val norm = MerchantNormalizer.normalize(rawMerchant, stripPrefixes = true)
        val matched = activeAliases.find { it.isEnabled && it.normalizedAlias == norm }
            ?: return Pair(rawMerchant, null)

        return Pair(matched.canonicalMerchant, matched)
    }

    private fun invalidateCache() {
        activeCache = null
    }
}
