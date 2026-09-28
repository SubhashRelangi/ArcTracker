package com.example.arctracker

import com.example.arctracker.data.KnownFinancialAccount
import com.example.arctracker.data.KnownFinancialAccountDao
import com.example.arctracker.service.InstrumentType

class FakeKnownFinancialAccountDao : KnownFinancialAccountDao {

    private val accounts = mutableMapOf<String, KnownFinancialAccount>()

    override fun getById(id: String): KnownFinancialAccount? = accounts[id]

    override fun findByCanonicalIdentity(
        institutionId: String?,
        instrumentType: InstrumentType,
        accountSuffix: String
    ): KnownFinancialAccount? {
        return accounts.values.firstOrNull {
            val instMatches = if (institutionId == null) it.institutionId == null else it.institutionId.equals(institutionId, ignoreCase = true)
            instMatches && it.instrumentType == instrumentType && it.accountSuffix == accountSuffix
        }
    }

    override fun findByInstitutionAndSuffix(
        institutionId: String,
        accountSuffix: String
    ): List<KnownFinancialAccount> {
        return accounts.values.filter {
            it.institutionId.equals(institutionId, ignoreCase = true) &&
                    it.accountSuffix == accountSuffix
        }.sortedByDescending { it.updatedAt }
    }

    override fun findBySuffix(accountSuffix: String): List<KnownFinancialAccount> {
        return accounts.values.filter {
            it.accountSuffix == accountSuffix
        }.sortedByDescending { it.updatedAt }
    }

    override fun getAll(): List<KnownFinancialAccount> = accounts.values.sortedByDescending { it.updatedAt }

    override fun getCount(): Int = accounts.size

    override fun insert(account: KnownFinancialAccount): Long {
        if (accounts.containsKey(account.id)) {
            throw IllegalStateException("UNIQUE constraint failed: known_financial_accounts.id ${account.id} already exists.")
        }
        accounts[account.id] = account
        return 1L
    }

    override fun update(account: KnownFinancialAccount): Int {
        return if (accounts.containsKey(account.id)) {
            accounts[account.id] = account
            1
        } else {
            0
        }
    }

    override fun delete(account: KnownFinancialAccount): Int {
        return if (accounts.remove(account.id) != null) 1 else 0
    }

    override fun deleteById(id: String): Int {
        return if (accounts.remove(id) != null) 1 else 0
    }

    override fun deleteAll(): Int {
        val size = accounts.size
        accounts.clear()
        return size
    }
}
