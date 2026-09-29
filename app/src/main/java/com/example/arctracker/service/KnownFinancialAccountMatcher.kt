package com.example.arctracker.service

import com.example.arctracker.data.KnownFinancialAccount
import com.example.arctracker.data.KnownFinancialAccountRepository

/**
 * Pure, deterministic account matcher connecting extracted live notification account metadata
 * to the persistent [KnownFinancialAccount] registry (Milestone 4).
 *
 * Guarantees:
 * 1. Safe enrichment only: Never alters transaction validity or existence.
 * 2. Deterministic: No guessing (never uses first(), confidence ranking, or arbitrary defaults).
 * 3. Ambiguity preservation: Returns [KnownFinancialAccountMatchResult.Ambiguous] when multiple accounts qualify.
 * 4. Read-only: Never creates or alters registry accounts from live notifications.
 * 5. No bank inference from UPI apps: Package names (Google Pay, PhonePe, Paytm) are NEVER mapped to banks.
 * 6. Instrument distinction: Cards vs bank accounts are strictly distinguished when evidence exists.
 */
class KnownFinancialAccountMatcher(
    private val repository: KnownFinancialAccountRepository?
) {

    /**
     * Matches raw suffix, instrument type, and optional institution against the known financial account registry.
     *
     * @param suffix Extracted account or card suffix (e.g. "9020", "XXXX9020").
     * @param instrumentType The instrument type if known (e.g. [InstrumentType.CARD], [InstrumentType.BANK_ACCOUNT], or [InstrumentType.UNKNOWN]).
     * @param institutionId Optional canonical institution ID if explicitly identified in notification text (e.g. "hdfc", "sbi").
     * @return [KnownFinancialAccountMatchResult] representing the exact matching state.
     */
    fun match(
        suffix: String?,
        instrumentType: InstrumentType = InstrumentType.UNKNOWN,
        institutionId: String? = null
    ): KnownFinancialAccountMatchResult {
        val repo = repository ?: return KnownFinancialAccountMatchResult.NoAccountData

        if (suffix.isNullOrBlank()) {
            return KnownFinancialAccountMatchResult.NoAccountData
        }

        val safeSuffix = AccountIdentityExtractor.safeSuffix(suffix)
            ?: suffix.filter { it.isDigit() }.takeIf { it.length >= 3 }
            ?: return KnownFinancialAccountMatchResult.NoAccountData

        // Query repository for all accounts matching this suffix
        val allWithSuffix = repo.findBySuffix(safeSuffix)
        if (allWithSuffix.isEmpty()) {
            return KnownFinancialAccountMatchResult.Unmatched
        }

        // 1. Filter by instrument type if explicitly specified (not UNKNOWN)
        val instrumentFiltered = if (instrumentType != InstrumentType.UNKNOWN) {
            allWithSuffix.filter { it.instrumentType == instrumentType }
        } else {
            allWithSuffix
        }

        if (instrumentFiltered.isEmpty()) {
            // e.g. input was CARD 9020, but registry only contained BANK_ACCOUNT 9020
            return KnownFinancialAccountMatchResult.Unmatched
        }

        // 2. Filter by institutionId if explicitly provided
        val finalCandidates = if (!institutionId.isNullOrBlank()) {
            val cleanInst = institutionId.trim().lowercase()
            instrumentFiltered.filter { account ->
                account.institutionId != null && account.institutionId.equals(cleanInst, ignoreCase = true)
            }
        } else {
            instrumentFiltered
        }

        if (finalCandidates.isEmpty()) {
            // Suffix exists, but conflicting institution was explicitly identified
            // Rule 10: Do not fall back to suffix-only matching when conflicting institution is specified
            return KnownFinancialAccountMatchResult.Unmatched
        }

        // 3. Evaluate candidate count
        return when (finalCandidates.size) {
            1 -> KnownFinancialAccountMatchResult.Matched(finalCandidates.first())
            else -> KnownFinancialAccountMatchResult.Ambiguous(finalCandidates)
        }
    }

    /**
     * Matches a structured transaction candidate against the registry.
     *
     * Extracts suffix, instrument type, and reliable institution from candidate content.
     * Strictly ignores package names to prevent inferring banks from UPI applications.
     */
    fun match(candidate: StructuredTransactionCandidate): KnownFinancialAccountMatchResult {
        // 1. Determine suffix and instrument type
        val (suffix, instrumentType) = when {
            !candidate.cardSuffix.isNullOrBlank() -> {
                candidate.cardSuffix to InstrumentType.CARD
            }
            !candidate.accountSuffix.isNullOrBlank() -> {
                val isExplicitAccount = candidate.getEvidence("accountSuffix")?.ruleOrPattern == "EXPLICIT_ACCOUNT_PATTERN" ||
                    candidate.rawContent?.contains(Regex("""(?i)\b(?:a/c|account|acct)\b""")) == true
                val type = if (isExplicitAccount) InstrumentType.BANK_ACCOUNT else InstrumentType.UNKNOWN
                candidate.accountSuffix to type
            }
            else -> null to InstrumentType.UNKNOWN
        }

        if (suffix.isNullOrBlank()) {
            return KnownFinancialAccountMatchResult.NoAccountData
        }

        // 2. Extract reliable explicit institution (text only, never from packageName)
        val institutionId = extractExplicitInstitutionId(candidate)

        return match(
            suffix = suffix,
            instrumentType = instrumentType,
            institutionId = institutionId
        )
    }

    /**
     * Extracts canonical institution ID only from explicit notification text or candidate bank.
     * NEVER uses package name (e.g. Google Pay, PhonePe, Paytm).
     */
    private fun extractExplicitInstitutionId(candidate: StructuredTransactionCandidate): String? {
        // Priority 1: Check candidate.bank if populated from explicit text patterns
        if (!candidate.bank.isNullOrBlank()) {
            val bankClean = candidate.bank.trim()
            val fromText = AccountIdentityExtractor.extractBankFromText(bankClean)
            if (fromText != null) {
                return normalizeInstitutionNameToId(fromText)
            }
            return bankClean.lowercase().filter { it.isLetterOrDigit() }
        }

        // Priority 2: Extract bank from raw notification content
        val textBank = AccountIdentityExtractor.extractBankFromText(candidate.rawContent)
        if (textBank != null) {
            return normalizeInstitutionNameToId(textBank)
        }

        return null
    }

    private fun normalizeInstitutionNameToId(bankName: String): String {
        val lower = bankName.lowercase()
        return when {
            lower.contains("hdfc") -> "hdfc"
            lower.contains("state bank") || lower.contains("sbi") -> "sbi"
            lower.contains("icici") -> "icici"
            lower.contains("axis") -> "axis"
            lower.contains("kotak") -> "kotak"
            lower.contains("punjab") || lower.contains("pnb") -> "pnb"
            lower.contains("baroda") || lower.contains("bob") -> "bob"
            lower.contains("canara") -> "canara"
            lower.contains("post") || lower.contains("ippb") -> "ippb"
            lower.contains("apg") || lower.contains("pragathi") -> "apgb"
            lower.contains("union") -> "union"
            lower.contains("indusind") -> "indusind"
            lower.contains("yes") -> "yesbank"
            lower.contains("idfc") -> "idfc"
            lower.contains("federal") -> "federal"
            lower.contains("bank of india") || lower == "boi" -> "boi"
            lower.contains("central bank") || lower.contains("cbin") -> "cbin"
            lower.contains("indian bank") -> "indianbank"
            lower.contains("rbl") -> "rbl"
            else -> lower.filter { it.isLetterOrDigit() }
        }
    }
}
