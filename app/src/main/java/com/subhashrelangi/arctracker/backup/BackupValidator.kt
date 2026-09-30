package com.subhashrelangi.arctracker.backup

import com.subhashrelangi.arctracker.data.BuiltInCategories

/**
 * Production-quality Pre-Import Validator for ArcTracker backups (Milestone 15).
 *
 * Guarantees:
 * - Strictly validates format version, structure, and constraints BEFORE touching the database.
 * - Enforces relational reference consistency across transactions, categories, accounts, rules, and budgets.
 * - Detects duplicate identifiers, impossible dates, negative/NaN amounts, and malformed enums.
 * - Produces detailed, human-readable error messages for safe user feedback.
 */
object BackupValidator {

    /**
     * Validates an entire [ArcTrackerBackupPayload].
     *
     * @param payload The parsed backup data.
     * @param existingCategoryIds Set of category IDs already present in the local database.
     * @param existingAccountIds Set of account IDs already present in the local database.
     * @param existingBudgetIds Set of budget IDs already present in the local database.
     */
    fun validate(
        payload: ArcTrackerBackupPayload,
        existingCategoryIds: Set<String> = emptySet(),
        existingAccountIds: Set<String> = emptySet(),
        existingBudgetIds: Set<String> = emptySet()
    ): ValidationResult {
        val errors = mutableListOf<String>()

        // 1. Format Version Validation
        val version = payload.metadata.formatVersion
        if (version <= 0) {
            errors.add("Invalid backup format version: $version")
        } else if (version > CURRENT_BACKUP_FORMAT_VERSION) {
            errors.add("Unsupported backup format version: $version (this ArcTracker build supports up to version $CURRENT_BACKUP_FORMAT_VERSION)")
            // Future versions cannot be validated safely
            return ValidationResult.Failure(errors)
        }

        // 2. Validate Categories
        val seenCategoryIds = mutableSetOf<String>()
        val builtInIds = BuiltInCategories.ALL.map { it.id }.toSet()
        val allValidCategoryIds = mutableSetOf<String>().apply {
            addAll(builtInIds)
            addAll(existingCategoryIds)
        }

        for ((index, cat) in payload.categories.withIndex()) {
            if (cat.id.isBlank()) {
                errors.add("Category at index $index has a blank id")
            } else if (!seenCategoryIds.add(cat.id)) {
                errors.add("Duplicate category id in backup: '${cat.id}'")
            }
            if (cat.name.isBlank()) {
                errors.add("Category '${cat.id}' has a blank name")
            }
            allValidCategoryIds.add(cat.id)
        }

        // 3. Validate Accounts
        val seenAccountIds = mutableSetOf<String>()
        val allValidAccountIds = mutableSetOf<String>().apply {
            addAll(existingAccountIds)
        }

        for ((index, acc) in payload.accounts.withIndex()) {
            if (acc.id.isBlank()) {
                errors.add("Account at index $index has a blank id")
            } else if (!seenAccountIds.add(acc.id)) {
                errors.add("Duplicate account id in backup: '${acc.id}'")
            }
            if (acc.accountSuffix.isBlank() || acc.accountSuffix.length > 4 || !acc.accountSuffix.all { it.isDigit() }) {
                errors.add("Account '${acc.id}' has invalid suffix '${acc.accountSuffix}' (must be 1-4 digits)")
            }
            allValidAccountIds.add(acc.id)
        }

        // 4. Validate Transactions
        val seenTxIds = mutableSetOf<Int>()
        for ((index, tx) in payload.transactions.withIndex()) {
            if (!seenTxIds.add(tx.id)) {
                // Duplicate IDs inside the file itself
                errors.add("Duplicate transaction id in backup: ${tx.id} at index $index")
            }
            if (tx.merchant.isBlank()) {
                errors.add("Transaction ${tx.id} has an empty merchant name")
            }
            if (tx.amount <= 0.0 || tx.amount.isNaN() || tx.amount.isInfinite()) {
                errors.add("Transaction ${tx.id} has invalid amount: ${tx.amount}")
            }
            if (tx.dateMillis <= 0) {
                errors.add("Transaction ${tx.id} has invalid timestamp: ${tx.dateMillis}")
            }
            if (!tx.type.equals("Debit", ignoreCase = true) && !tx.type.equals("Credit", ignoreCase = true)) {
                errors.add("Transaction ${tx.id} has invalid type: '${tx.type}' (expected 'Debit' or 'Credit')")
            }
            // Category reference check
            if (!tx.categoryId.isNullOrBlank() && !allValidCategoryIds.contains(tx.categoryId)) {
                // Non-fatal if legacy tag resolves, otherwise record warning/error
                val legacy = BuiltInCategories.findLegacyMapping(tx.tag)
                if (legacy == null) {
                    errors.add("Transaction ${tx.id} references non-existent category '${tx.categoryId}'")
                }
            }
            // Account reference check
            if (!tx.accountId.isNullOrBlank() && !allValidAccountIds.contains(tx.accountId)) {
                errors.add("Transaction ${tx.id} references non-existent account '${tx.accountId}'")
            }
        }

        // 5. Validate Rules
        val seenRuleIds = mutableSetOf<String>()
        for ((index, rule) in payload.rules.withIndex()) {
            if (rule.id.isBlank()) {
                errors.add("Rule at index $index has a blank id")
            } else if (!seenRuleIds.add(rule.id)) {
                errors.add("Duplicate rule id in backup: '${rule.id}'")
            }
            if (rule.pattern.isBlank()) {
                errors.add("Rule '${rule.id}' has an empty pattern")
            }
            if (rule.categoryId.isBlank() || !allValidCategoryIds.contains(rule.categoryId)) {
                errors.add("Rule '${rule.id}' references non-existent category '${rule.categoryId}'")
            }
        }

        // 6. Validate Aliases
        val seenAliasIds = mutableSetOf<String>()
        for ((index, alias) in payload.aliases.withIndex()) {
            if (alias.id.isBlank()) {
                errors.add("Alias at index $index has a blank id")
            } else if (!seenAliasIds.add(alias.id)) {
                errors.add("Duplicate alias id in backup: '${alias.id}'")
            }
            if (alias.alias.isBlank()) {
                errors.add("Alias '${alias.id}' has blank alias text")
            }
            if (alias.canonicalMerchant.isBlank()) {
                errors.add("Alias '${alias.id}' has blank canonical merchant")
            }
        }

        // 7. Validate Budgets
        val seenBudgetIds = mutableSetOf<String>()
        val allValidBudgetIds = mutableSetOf<String>().apply {
            addAll(existingBudgetIds)
        }

        for ((index, b) in payload.budgets.withIndex()) {
            if (b.id.isBlank()) {
                errors.add("Budget at index $index has a blank id")
            } else if (!seenBudgetIds.add(b.id)) {
                errors.add("Duplicate budget id in backup: '${b.id}'")
            }
            if (b.name.isBlank()) {
                errors.add("Budget '${b.id}' has an empty name")
            }
            if (b.amountLimit <= 0.0 || b.amountLimit.isNaN() || b.amountLimit.isInfinite()) {
                errors.add("Budget '${b.id}' has invalid amount limit: ${b.amountLimit}")
            }
            if (b.warningThreshold <= 0.0 || b.warningThreshold > b.exceededThreshold) {
                errors.add("Budget '${b.id}' has invalid warning threshold: ${b.warningThreshold}")
            }
            if (!b.categoryId.isNullOrBlank() && !allValidCategoryIds.contains(b.categoryId)) {
                errors.add("Budget '${b.id}' references non-existent category '${b.categoryId}'")
            }
            allValidBudgetIds.add(b.id)
        }

        // 8. Validate Budget Alert States
        val seenAlertStateIds = mutableSetOf<String>()
        for ((index, state) in payload.budgetAlertStates.withIndex()) {
            if (state.id.isBlank()) {
                errors.add("Budget alert state at index $index has a blank id")
            } else if (!seenAlertStateIds.add(state.id)) {
                errors.add("Duplicate alert state id in backup: '${state.id}'")
            }
            if (state.budgetId.isBlank() || !allValidBudgetIds.contains(state.budgetId)) {
                errors.add("Budget alert state '${state.id}' references non-existent budget '${state.budgetId}'")
            }
            if (state.periodStart <= 0) {
                errors.add("Budget alert state '${state.id}' has invalid periodStart: ${state.periodStart}")
            }
        }

        return if (errors.isEmpty()) {
            ValidationResult.Success(payload)
        } else {
            ValidationResult.Failure(errors)
        }
    }
}
