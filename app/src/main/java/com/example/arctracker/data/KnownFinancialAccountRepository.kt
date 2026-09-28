package com.example.arctracker.data

import com.example.arctracker.service.AccountIdentityExtractor
import com.example.arctracker.service.IdentityConfidence
import com.example.arctracker.service.InstrumentType

/**
 * Repository providing persistent management and deterministic merging
 * for known financial accounts (Milestone 2).
 */
class KnownFinancialAccountRepository(
    private val dao: KnownFinancialAccountDao
) {

    /**
     * Upserts a known financial account.
     *
     * If an account with the same canonical ID exists:
     * - Preserves stable ID.
     * - Preserves createdAt timestamp.
     * - Updates updatedAt timestamp.
     * - Upgrades institution information if incoming provides it and existing was null.
     * - Upgrades confidence if incoming confidence is higher quality.
     * - Does NOT overwrite good metadata with null/unknown.
     * - Preserves instrument type (never converts BANK_ACCOUNT to CARD or vice versa).
     */
    fun upsert(incoming: KnownFinancialAccount): KnownFinancialAccount {
        val existing = dao.getById(incoming.id)
        if (existing == null) {
            dao.insert(incoming)
            return incoming
        }

        val merged = mergeAccounts(existing, incoming)
        dao.update(merged)
        return merged
    }

    private fun mergeAccounts(
        existing: KnownFinancialAccount,
        incoming: KnownFinancialAccount
    ): KnownFinancialAccount {
        val id = existing.id
        val createdAt = existing.createdAt
        val updatedAt = maxOf(incoming.updatedAt, System.currentTimeMillis())

        val institutionId = incoming.institutionId?.takeUnless { it.isBlank() } ?: existing.institutionId
        val institutionName = incoming.institutionName?.takeUnless { it.isBlank() } ?: existing.institutionName
        val instrumentType = existing.instrumentType
        val accountSuffix = existing.accountSuffix

        val confidence = if (incoming.confidence.qualityScore() >= existing.confidence.qualityScore()) {
            incoming.confidence
        } else {
            existing.confidence
        }

        val source = when {
            existing.source == AccountSource.USER_CONFIRMED && incoming.source != AccountSource.USER_CONFIRMED -> existing.source
            incoming.source == AccountSource.USER_CONFIRMED -> incoming.source
            incoming.confidence.qualityScore() >= existing.confidence.qualityScore() -> incoming.source
            else -> existing.source
        }

        return KnownFinancialAccount(
            id = id,
            institutionId = institutionId,
            institutionName = institutionName,
            accountSuffix = accountSuffix,
            instrumentType = instrumentType,
            confidence = confidence,
            source = source,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun getById(id: String): KnownFinancialAccount? = dao.getById(id)

    fun findByExact(
        institutionId: String?,
        instrumentType: InstrumentType,
        suffix: String
    ): KnownFinancialAccount? {
        val safe = AccountIdentityExtractor.safeSuffix(suffix) ?: suffix.filter { it.isDigit() }
        val id = KnownFinancialAccount.generateId(institutionId, instrumentType, safe)
        return dao.getById(id)
    }

    fun findBySuffix(suffix: String): List<KnownFinancialAccount> {
        val safe = AccountIdentityExtractor.safeSuffix(suffix) ?: suffix.filter { it.isDigit() }
        return dao.findBySuffix(safe)
    }

    fun findByInstitutionAndSuffix(
        institutionId: String,
        suffix: String
    ): List<KnownFinancialAccount> {
        val safe = AccountIdentityExtractor.safeSuffix(suffix) ?: suffix.filter { it.isDigit() }
        return dao.findByInstitutionAndSuffix(institutionId.trim().lowercase(), safe)
    }

    fun getAll(): List<KnownFinancialAccount> = dao.getAll()

    fun getCount(): Int = dao.getCount()

    fun delete(account: KnownFinancialAccount): Boolean = dao.delete(account) > 0

    fun deleteById(id: String): Boolean = dao.deleteById(id) > 0

    fun deleteAll(): Int = dao.deleteAll()
}
