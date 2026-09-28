package com.example.arctracker.service

import com.example.arctracker.data.AccountSource
import com.example.arctracker.data.KnownFinancialAccount
import com.example.arctracker.data.KnownFinancialAccountRepository

/**
 * Synchronizes financial account identities discovered from historical SMS into the
 * persistent [KnownFinancialAccount] registry (Milestone 3).
 *
 * Guarantees:
 * 1. Only synchronizes identities with a valid safe account suffix (never creates records without a suffix).
 * 2. Never stores or logs full account numbers, full card numbers, or credentials.
 * 3. Evidence-based: Bank identity comes strictly from AccountIdentityExtractor, never inferred from package names.
 * 4. Deterministic upsert: multiple messages for the same account update the existing record without duplicates.
 * 5. Unknown -> Known reconciliation: when definitive institution evidence arrives for an account previously
 *    recorded as unknown, safely upgrades to the canonical institution record and removes the provisional unknown record,
 *    provided there is no ambiguity across multiple known institutions.
 * 6. Same suffix across different institutions (e.g. HDFC 9020 and APGB 9020) remain separate.
 * 7. Bank accounts vs cards with the same suffix remain separate.
 */
class HistoricalSmsAccountRegistrySynchronizer(
    private val repository: KnownFinancialAccountRepository?
) {

    /**
     * Synchronizes a single [FinancialAccountIdentity] into the account registry.
     *
     * @param identity The extracted financial account identity.
     * @return The persisted or updated [KnownFinancialAccount], or null if invalid or repository unavailable.
     */
    suspend fun synchronize(
        identity: FinancialAccountIdentity,
        suppressedReconciliationSuffixes: Set<String> = emptySet()
    ): KnownFinancialAccount? {
        val repo = repository ?: return null

        // 1. Validate account suffix
        val rawSuffix = identity.accountSuffix ?: identity.cardSuffix
        if (rawSuffix.isNullOrBlank()) {
            return null
        }

        val safeSuffix = AccountIdentityExtractor.safeSuffix(rawSuffix)
            ?: rawSuffix.filter { it.isDigit() }.takeIf { it.length >= 3 }
            ?: return null

        // 2. Validate instrument type
        val instrumentType = when {
            identity.instrumentType != InstrumentType.UNKNOWN -> identity.instrumentType
            !identity.cardSuffix.isNullOrBlank() -> InstrumentType.CARD
            else -> InstrumentType.BANK_ACCOUNT
        }

        // 3. Create the canonical KnownFinancialAccount
        val candidate = KnownFinancialAccount.create(
            institutionId = identity.institutionId,
            institutionName = identity.institutionName,
            accountSuffix = safeSuffix,
            instrumentType = instrumentType,
            confidence = identity.confidence,
            source = AccountSource.HISTORICAL_SMS
        )

        // 4. Perform Unknown -> Known Reconciliation if applicable
        if (candidate.institutionId != null) {
            reconcileUnknownIfApplicable(repo, candidate, suppressedReconciliationSuffixes)
        }

        // 5. Upsert the canonical account
        return repo.upsert(candidate)
    }

    /**
     * Synchronizes a collection of distinct [FinancialAccountIdentity] items.
     */
    suspend fun synchronizeAll(
        identities: Collection<FinancialAccountIdentity>
    ): List<KnownFinancialAccount> {
        // Pre-scan batch for suffixes that have multiple distinct known institutions in this batch
        val ambiguousBatchSuffixes = identities
            .filter { !it.institutionId.isNullOrBlank() }
            .groupBy { it.accountSuffix ?: it.cardSuffix }
            .filter { (_, group) ->
                group.mapNotNull { it.institutionId?.lowercase() }.distinct().size > 1
            }
            .keys
            .filterNotNull()
            .toSet()

        val results = mutableListOf<KnownFinancialAccount>()
        for (identity in identities) {
            val synced = synchronize(identity, ambiguousBatchSuffixes)
            if (synced != null) {
                results.add(synced)
            }
        }
        return results
    }

    /**
     * Reconciles a provisional "unknown" record (e.g. "unknown_bank_account_9020")
     * into a definitively identified known account (e.g. "hdfc_bank_account_9020").
     *
     * Safe Ambiguity Rule:
     * Reconciliation is ONLY performed if there are NO OTHER known institutions in the registry
     * sharing this account suffix. If multiple known institutions exist with this suffix (e.g. both HDFC and APGB),
     * merging the unknown record would be ambiguous and is intentionally NOT performed.
     */
    private fun reconcileUnknownIfApplicable(
        repo: KnownFinancialAccountRepository,
        incoming: KnownFinancialAccount,
        suppressedSuffixes: Set<String> = emptySet()
    ) {
        if (suppressedSuffixes.contains(incoming.accountSuffix)) {
            // Ambiguity detected across current synchronization batch: do NOT delete unknown record
            return
        }

        val unknownId = KnownFinancialAccount.generateId(null, incoming.instrumentType, incoming.accountSuffix)
        val provisionalUnknown = repo.getById(unknownId) ?: return

        // Check for ambiguity across known institutions with this suffix
        val existingWithSuffix = repo.findBySuffix(incoming.accountSuffix)
        val competingKnownInstitutions = existingWithSuffix.filter {
            it.institutionId != null && !it.institutionId.equals(incoming.institutionId, ignoreCase = true)
        }

        if (competingKnownInstitutions.isNotEmpty()) {
            // Ambiguity detected: Suffix is shared by multiple distinct institutions.
            // Do NOT delete the unknown record.
            return
        }

        // Unambiguous: Remove the provisional unknown record so the definitive institution record survives.
        repo.deleteById(unknownId)
    }
}
