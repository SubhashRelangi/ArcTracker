package com.example.arctracker.service

import com.example.arctracker.data.KnownFinancialAccount

/**
 * Structured match result from querying the [KnownFinancialAccountRepository] for a live notification (Milestone 4).
 *
 * Distinguishes four explicit states:
 * 1. [Matched]: Exactly one compatible known account was identified.
 * 2. [Ambiguous]: Multiple compatible known accounts exist for the given suffix/criteria; no guessing is permitted.
 * 3. [Unmatched]: A valid suffix was provided, but no matching known account exists in the registry.
 * 4. [NoAccountData]: No account suffix or instrument identity was present in the notification.
 */
sealed class KnownFinancialAccountMatchResult {

    /**
     * Exactly one compatible known account matched the notification.
     */
    data class Matched(
        val account: KnownFinancialAccount
    ) : KnownFinancialAccountMatchResult() {
        val enrichment: AccountEnrichment = AccountEnrichment(
            accountId = account.id,
            institutionId = account.institutionId,
            institutionName = account.institutionName,
            instrumentType = account.instrumentType
        )
    }

    /**
     * Multiple accounts match the suffix/criteria; guessing is strictly forbidden.
     */
    data class Ambiguous(
        val candidates: List<KnownFinancialAccount>,
        val reason: String = "Multiple accounts match criteria"
    ) : KnownFinancialAccountMatchResult()

    /**
     * A valid account/card suffix was present, but no matching record exists in the known accounts registry.
     */
    data object Unmatched : KnownFinancialAccountMatchResult()

    /**
     * No account suffix, card suffix, or masked number was found in the notification.
     */
    data object NoAccountData : KnownFinancialAccountMatchResult()

    val matchedAccount: KnownFinancialAccount?
        get() = (this as? Matched)?.account

    val accountEnrichment: AccountEnrichment?
        get() = (this as? Matched)?.enrichment

    val isMatched: Boolean
        get() = this is Matched

    val isAmbiguous: Boolean
        get() = this is Ambiguous
}

/**
 * Structured metadata representing a matched financial account for transaction enrichment (Milestone 4).
 */
data class AccountEnrichment(
    val accountId: String,
    val institutionId: String?,
    val institutionName: String?,
    val instrumentType: InstrumentType
)
