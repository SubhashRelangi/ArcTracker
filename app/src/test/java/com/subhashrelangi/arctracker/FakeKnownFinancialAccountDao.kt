package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.KnownFinancialAccountDao
import com.subhashrelangi.arctracker.service.InstrumentType

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeKnownFinancialAccountDao : KnownFinancialAccountDao {

    private val accounts = mutableMapOf<String, KnownFinancialAccount>()
    private val accountsFlow = MutableStateFlow<List<KnownFinancialAccount>>(emptyList())

    private fun notifyFlow() {
        accountsFlow.value = accounts.values.sortedByDescending { it.updatedAt }
    }

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

    override fun getAllFlow(): Flow<List<KnownFinancialAccount>> = accountsFlow.asStateFlow()

    override fun getCount(): Int = accounts.size

    override fun insert(account: KnownFinancialAccount): Long {
        if (accounts.containsKey(account.id)) {
            throw IllegalStateException("UNIQUE constraint failed: known_financial_accounts.id ${account.id} already exists.")
        }
        accounts[account.id] = account
        notifyFlow()
        return 1L
    }

    override fun insertAll(accounts: List<KnownFinancialAccount>): List<Long> {
        val result = mutableListOf<Long>()
        for (acc in accounts) {
            this.accounts[acc.id] = acc
            result.add(1L)
        }
        notifyFlow()
        return result
    }

    override fun update(account: KnownFinancialAccount): Int {
        return if (accounts.containsKey(account.id)) {
            accounts[account.id] = account
            notifyFlow()
            1
        } else {
            0
        }
    }

    override fun delete(account: KnownFinancialAccount): Int {
        val removed = accounts.remove(account.id) != null
        if (removed) notifyFlow()
        return if (removed) 1 else 0
    }

    override fun deleteById(id: String): Int {
        val removed = accounts.remove(id) != null
        if (removed) notifyFlow()
        return if (removed) 1 else 0
    }

    override fun deleteAll(): Int {
        val size = accounts.size
        accounts.clear()
        notifyFlow()
        return size
    }
}
