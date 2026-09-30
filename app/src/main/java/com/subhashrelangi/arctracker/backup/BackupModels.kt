package com.subhashrelangi.arctracker.backup

import com.google.gson.annotations.SerializedName
import com.subhashrelangi.arctracker.data.*

/**
 * Milestone 15: Backup & Export Data Transfer Objects (DTOs) and Domain Models.
 *
 * Guarantees:
 * - Versioned backup format independently of Room schema version.
 * - Explicit DTOs decoupled from Room internal structures.
 * - Strictly excludes sensitive credentials, unmasked account numbers, OTPs, and runtime state.
 * - Preserves full relational integrity across transactions, accounts, categories, rules, and budgets.
 */

const val CURRENT_BACKUP_FORMAT_VERSION = 1
const val APP_VERSION_NAME = "1.0.0"

/**
 * Metadata header for full ArcTracker backup files.
 */
data class BackupMetadata(
    @SerializedName("formatVersion")
    val formatVersion: Int = CURRENT_BACKUP_FORMAT_VERSION,
    @SerializedName("appVersion")
    val appVersion: String = APP_VERSION_NAME,
    @SerializedName("databaseSchemaVersion")
    val databaseSchemaVersion: Int = 8,
    @SerializedName("exportedAt")
    val exportedAt: Long = System.currentTimeMillis(),
    @SerializedName("entityCounts")
    val entityCounts: BackupEntityCounts = BackupEntityCounts(),
    @SerializedName("checksum")
    val checksum: String = ""
)

/**
 * Counts of entities contained in the backup for rapid preview and validation.
 */
data class BackupEntityCounts(
    @SerializedName("transactions")
    val transactions: Int = 0,
    @SerializedName("categories")
    val categories: Int = 0,
    @SerializedName("accounts")
    val accounts: Int = 0,
    @SerializedName("rules")
    val rules: Int = 0,
    @SerializedName("aliases")
    val aliases: Int = 0,
    @SerializedName("budgets")
    val budgets: Int = 0,
    @SerializedName("budgetAlertStates")
    val budgetAlertStates: Int = 0
)

/**
 * Portable DTO for a transaction record.
 */
data class ExpenseBackupDto(
    @SerializedName("id")
    val id: Int,
    @SerializedName("amount")
    val amount: Double,
    @SerializedName("merchant")
    val merchant: String,
    @SerializedName("dateMillis")
    val dateMillis: Long,
    @SerializedName("type")
    val type: String = "Debit",
    @SerializedName("notificationKey")
    val notificationKey: String = "",
    @SerializedName("isPending")
    val isPending: Boolean = false,
    @SerializedName("tag")
    val tag: String? = null,
    @SerializedName("note")
    val note: String? = null,
    @SerializedName("source")
    val source: String = "MANUAL",
    @SerializedName("relationshipType")
    val relationshipType: String? = null,
    @SerializedName("relationshipId")
    val relationshipId: String? = null,
    @SerializedName("accountId")
    val accountId: String? = null,
    @SerializedName("accountSuffix")
    val accountSuffix: String? = null,
    @SerializedName("categoryId")
    val categoryId: String? = null,
    @SerializedName("categorySource")
    val categorySource: String = "NONE"
) {
    fun toExpense(): Expense {
        return Expense(
            id = id,
            amount = amount,
            merchant = merchant,
            dateMillis = dateMillis,
            type = type,
            notificationKey = notificationKey,
            isPending = isPending,
            rawText = null, // Strictly sanitize raw text from backup
            tag = tag,
            note = note,
            source = source,
            relationshipType = relationshipType,
            relationshipId = relationshipId,
            accountId = accountId,
            accountSuffix = accountSuffix,
            categoryId = categoryId,
            categorySource = categorySource
        )
    }

    companion object {
        fun fromExpense(e: Expense): ExpenseBackupDto {
            return ExpenseBackupDto(
                id = e.id,
                amount = e.amount,
                merchant = e.merchant,
                dateMillis = e.dateMillis,
                type = e.type,
                notificationKey = e.notificationKey,
                isPending = e.isPending,
                tag = e.tag,
                note = e.note,
                source = e.source,
                relationshipType = e.relationshipType,
                relationshipId = e.relationshipId,
                accountId = e.accountId,
                accountSuffix = e.accountSuffix,
                categoryId = e.categoryId,
                categorySource = e.categorySource
            )
        }
    }
}

/**
 * Portable DTO for a transaction category.
 */
data class CategoryBackupDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("iconKey")
    val iconKey: String = "category",
    @SerializedName("colorKey")
    val colorKey: String = "default",
    @SerializedName("isSystem")
    val isSystem: Boolean = false,
    @SerializedName("isArchived")
    val isArchived: Boolean = false,
    @SerializedName("sortOrder")
    val sortOrder: Int = 0,
    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toCategory(): TransactionCategory {
        return TransactionCategory(
            id = id,
            name = name,
            iconKey = iconKey,
            colorKey = colorKey,
            isSystem = isSystem,
            isArchived = isArchived,
            sortOrder = sortOrder,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromCategory(c: TransactionCategory): CategoryBackupDto {
            return CategoryBackupDto(
                id = c.id,
                name = c.name,
                iconKey = c.iconKey,
                colorKey = c.colorKey,
                isSystem = c.isSystem,
                isArchived = c.isArchived,
                sortOrder = c.sortOrder,
                createdAt = c.createdAt,
                updatedAt = c.updatedAt
            )
        }
    }
}

/**
 * Portable DTO for a known financial account.
 */
data class AccountBackupDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("institutionId")
    val institutionId: String? = null,
    @SerializedName("institutionName")
    val institutionName: String? = null,
    @SerializedName("accountSuffix")
    val accountSuffix: String,
    @SerializedName("instrumentType")
    val instrumentType: String,
    @SerializedName("confidence")
    val confidence: String,
    @SerializedName("source")
    val source: String,
    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toAccount(): KnownFinancialAccount {
        val instType = try {
            com.subhashrelangi.arctracker.service.InstrumentType.valueOf(instrumentType)
        } catch (e: Exception) {
            com.subhashrelangi.arctracker.service.InstrumentType.BANK_ACCOUNT
        }
        val conf = try {
            com.subhashrelangi.arctracker.service.IdentityConfidence.valueOf(confidence)
        } catch (e: Exception) {
            com.subhashrelangi.arctracker.service.IdentityConfidence.UNKNOWN
        }
        val src = try {
            AccountSource.valueOf(source)
        } catch (e: Exception) {
            AccountSource.OTHER
        }

        return KnownFinancialAccount(
            id = id,
            institutionId = institutionId,
            institutionName = institutionName,
            accountSuffix = accountSuffix,
            instrumentType = instType,
            confidence = conf,
            source = src,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromAccount(a: KnownFinancialAccount): AccountBackupDto {
            return AccountBackupDto(
                id = a.id,
                institutionId = a.institutionId,
                institutionName = a.institutionName,
                accountSuffix = a.accountSuffix,
                instrumentType = a.instrumentType.name,
                confidence = a.confidence.name,
                source = a.source.name,
                createdAt = a.createdAt,
                updatedAt = a.updatedAt
            )
        }
    }
}

/**
 * Portable DTO for user-defined category rules.
 */
data class RuleBackupDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("matchType")
    val matchType: String = UserRuleMatchType.MERCHANT_EXACT,
    @SerializedName("pattern")
    val pattern: String,
    @SerializedName("normalizedPattern")
    val normalizedPattern: String,
    @SerializedName("categoryId")
    val categoryId: String,
    @SerializedName("priority")
    val priority: Int = 100,
    @SerializedName("isEnabled")
    val isEnabled: Boolean = true,
    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toRule(): UserCategoryRule {
        return UserCategoryRule(
            id = id,
            name = name,
            matchType = matchType,
            pattern = pattern,
            normalizedPattern = normalizedPattern,
            categoryId = categoryId,
            priority = priority,
            isEnabled = isEnabled,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromRule(r: UserCategoryRule): RuleBackupDto {
            return RuleBackupDto(
                id = r.id,
                name = r.name,
                matchType = r.matchType,
                pattern = r.pattern,
                normalizedPattern = r.normalizedPattern,
                categoryId = r.categoryId,
                priority = r.priority,
                isEnabled = r.isEnabled,
                createdAt = r.createdAt,
                updatedAt = r.updatedAt
            )
        }
    }
}

/**
 * Portable DTO for merchant aliases.
 */
data class AliasBackupDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("alias")
    val alias: String,
    @SerializedName("canonicalMerchant")
    val canonicalMerchant: String,
    @SerializedName("normalizedAlias")
    val normalizedAlias: String,
    @SerializedName("isEnabled")
    val isEnabled: Boolean = true,
    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toAlias(): MerchantAlias {
        return MerchantAlias(
            id = id,
            alias = alias,
            canonicalMerchant = canonicalMerchant,
            normalizedAlias = normalizedAlias,
            isEnabled = isEnabled,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromAlias(a: MerchantAlias): AliasBackupDto {
            return AliasBackupDto(
                id = a.id,
                alias = a.alias,
                canonicalMerchant = a.canonicalMerchant,
                normalizedAlias = a.normalizedAlias,
                isEnabled = a.isEnabled,
                createdAt = a.createdAt,
                updatedAt = a.updatedAt
            )
        }
    }
}

/**
 * Portable DTO for budgets.
 */
data class BudgetBackupDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("amountLimit")
    val amountLimit: Double,
    @SerializedName("categoryId")
    val categoryId: String? = null,
    @SerializedName("periodType")
    val periodType: String = BudgetPeriodType.MONTHLY.name,
    @SerializedName("periodAnchor")
    val periodAnchor: Int = 1,
    @SerializedName("warningThreshold")
    val warningThreshold: Double = 80.0,
    @SerializedName("exceededThreshold")
    val exceededThreshold: Double = 100.0,
    @SerializedName("isEnabled")
    val isEnabled: Boolean = true,
    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toBudget(): Budget {
        return Budget(
            id = id,
            name = name,
            amountLimit = amountLimit,
            categoryId = categoryId,
            periodType = periodType,
            periodAnchor = periodAnchor,
            warningThreshold = warningThreshold,
            exceededThreshold = exceededThreshold,
            isEnabled = isEnabled,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromBudget(b: Budget): BudgetBackupDto {
            return BudgetBackupDto(
                id = b.id,
                name = b.name,
                amountLimit = b.amountLimit,
                categoryId = b.categoryId,
                periodType = b.periodType,
                periodAnchor = b.periodAnchor,
                warningThreshold = b.warningThreshold,
                exceededThreshold = b.exceededThreshold,
                isEnabled = b.isEnabled,
                createdAt = b.createdAt,
                updatedAt = b.updatedAt
            )
        }
    }
}

/**
 * Portable DTO for budget alert states.
 */
data class BudgetAlertStateBackupDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("budgetId")
    val budgetId: String,
    @SerializedName("periodStart")
    val periodStart: Long,
    @SerializedName("warningSent")
    val warningSent: Boolean = false,
    @SerializedName("exceededSent")
    val exceededSent: Boolean = false,
    @SerializedName("updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toAlertState(): BudgetAlertState {
        return BudgetAlertState(
            id = id,
            budgetId = budgetId,
            periodStart = periodStart,
            warningSent = warningSent,
            exceededSent = exceededSent,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromAlertState(s: BudgetAlertState): BudgetAlertStateBackupDto {
            return BudgetAlertStateBackupDto(
                id = s.id,
                budgetId = s.budgetId,
                periodStart = s.periodStart,
                warningSent = s.warningSent,
                exceededSent = s.exceededSent,
                updatedAt = s.updatedAt
            )
        }
    }
}

/**
 * Full top-level backup payload structure.
 */
data class ArcTrackerBackupPayload(
    @SerializedName("metadata")
    val metadata: BackupMetadata,
    @SerializedName("categories")
    val categories: List<CategoryBackupDto> = emptyList(),
    @SerializedName("accounts")
    val accounts: List<AccountBackupDto> = emptyList(),
    @SerializedName("transactions")
    val transactions: List<ExpenseBackupDto> = emptyList(),
    @SerializedName("rules")
    val rules: List<RuleBackupDto> = emptyList(),
    @SerializedName("aliases")
    val aliases: List<AliasBackupDto> = emptyList(),
    @SerializedName("budgets")
    val budgets: List<BudgetBackupDto> = emptyList(),
    @SerializedName("budgetAlertStates")
    val budgetAlertStates: List<BudgetAlertStateBackupDto> = emptyList()
)

/**
 * Supported import modes.
 */
enum class ImportMode {
    /**
     * Safely merges backup data into existing database.
     * New records are added. Existing records are protected.
     * USER_ASSIGNED categories and user notes are NEVER overwritten.
     */
    MERGE,

    /**
     * Completely restores the application state from the backup file.
     * Creates a safety backup of existing data prior to replacement.
     */
    FULL_RESTORE
}

/**
 * Comprehensive preview summary shown to the user before committing an import.
 */
data class ImportPreview(
    val metadata: BackupMetadata,
    val mode: ImportMode,
    val totalTransactions: Int,
    val newTransactions: Int,
    val existingTransactions: Int,
    val totalCategories: Int,
    val newCategories: Int,
    val totalAccounts: Int,
    val newAccounts: Int,
    val totalRules: Int,
    val totalAliases: Int,
    val totalBudgets: Int,
    val totalAlertStates: Int,
    val validationWarnings: List<String> = emptyList()
)

/**
 * Summary of completed import operations.
 */
data class ImportSummary(
    val mode: ImportMode,
    val importedTransactions: Int,
    val preservedTransactions: Int,
    val importedCategories: Int,
    val importedAccounts: Int,
    val importedRules: Int,
    val importedAliases: Int,
    val importedBudgets: Int,
    val durationMs: Long
)

/**
 * Sealed result for validation operations.
 */
sealed class ValidationResult {
    data class Success(val payload: ArcTrackerBackupPayload) : ValidationResult()
    data class Failure(val errors: List<String>) : ValidationResult()
}
