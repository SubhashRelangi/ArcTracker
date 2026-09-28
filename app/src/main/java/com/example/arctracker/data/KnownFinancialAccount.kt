package com.example.arctracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.arctracker.service.AccountIdentityExtractor
import com.example.arctracker.service.IdentityConfidence
import com.example.arctracker.service.InstrumentType

/**
 * Source describing how a financial account was discovered or registered (Milestone 2).
 */
enum class AccountSource {
    HISTORICAL_SMS,
    LIVE_NOTIFICATION,
    USER_CONFIRMED,
    OTHER
}

/**
 * Quality ranking for identity confidence to support safe upsert metadata upgrades.
 */
fun IdentityConfidence.qualityScore(): Int = when (this) {
    IdentityConfidence.HIGH -> 3
    IdentityConfidence.MEDIUM -> 2
    IdentityConfidence.LOW -> 1
    IdentityConfidence.UNKNOWN -> 0
}

/**
 * Room entity representing a persistent known financial account (Milestone 2).
 *
 * Guarantees:
 * 1. Stable, deterministic canonical identity: `${institutionId ?: "unknown"}_${instrumentType}_${accountSuffix}`.
 * 2. Uniqueness distinguishes institution, instrument type, and account suffix.
 * 3. Never stores full account numbers, cards, PAN, OTP, or credentials (strictly safe trailing suffix).
 * 4. Separate accounts for different institutions sharing the same suffix (e.g. HDFC 9020 vs APGB 9020).
 * 5. Separate accounts for bank accounts vs cards (e.g. HDFC BANK_ACCOUNT 9020 vs HDFC CARD 9020).
 * 6. Unknown institutions are fully supported without colliding with known institutions.
 */
@Entity(
    tableName = "known_financial_accounts",
    indices = [
        Index(value = ["accountSuffix"]),
        Index(value = ["institutionId"]),
        Index(value = ["instrumentType"])
    ]
)
data class KnownFinancialAccount(
    @PrimaryKey
    val id: String,
    val institutionId: String? = null,
    val institutionName: String? = null,
    val accountSuffix: String,
    val instrumentType: InstrumentType = InstrumentType.BANK_ACCOUNT,
    val confidence: IdentityConfidence = IdentityConfidence.UNKNOWN,
    val source: AccountSource = AccountSource.OTHER,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Generates a stable, deterministic canonical identifier for a financial account.
         */
        fun generateId(
            institutionId: String?,
            instrumentType: InstrumentType,
            accountSuffix: String
        ): String {
            val inst = institutionId?.trim()?.lowercase().takeUnless { it.isNullOrBlank() } ?: "unknown"
            val instrument = instrumentType.name.lowercase()
            val clean = AccountIdentityExtractor.safeSuffix(accountSuffix) ?: accountSuffix.filter { it.isDigit() }
            val safe = when {
                clean.length >= 4 -> clean.takeLast(4)
                clean.isNotEmpty() -> clean
                else -> "unknown"
            }
            return "${inst}_${instrument}_${safe}"
        }

        /**
         * Factory creating a [KnownFinancialAccount] with sanitized safe suffix and canonical ID.
         */
        fun create(
            institutionId: String? = null,
            institutionName: String? = null,
            accountSuffix: String,
            instrumentType: InstrumentType = InstrumentType.BANK_ACCOUNT,
            confidence: IdentityConfidence = IdentityConfidence.UNKNOWN,
            source: AccountSource = AccountSource.OTHER,
            createdAt: Long = System.currentTimeMillis(),
            updatedAt: Long = createdAt
        ): KnownFinancialAccount {
            val clean = AccountIdentityExtractor.safeSuffix(accountSuffix) ?: accountSuffix.filter { it.isDigit() }
            val safe = when {
                clean.length >= 4 -> clean.takeLast(4)
                clean.isNotEmpty() -> clean
                else -> "unknown"
            }
            val id = generateId(institutionId, instrumentType, safe)
            return KnownFinancialAccount(
                id = id,
                institutionId = institutionId?.trim()?.lowercase().takeUnless { it.isNullOrBlank() },
                institutionName = institutionName?.trim().takeUnless { it.isNullOrBlank() },
                accountSuffix = safe,
                instrumentType = instrumentType,
                confidence = confidence,
                source = source,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }
}

/**
 * Room TypeConverters for [KnownFinancialAccount] enum columns.
 */
class KnownFinancialAccountConverters {

    @TypeConverter
    fun fromInstrumentType(type: InstrumentType?): String? = type?.name

    @TypeConverter
    fun toInstrumentType(name: String?): InstrumentType? = name?.let {
        try {
            InstrumentType.valueOf(it)
        } catch (_: Exception) {
            InstrumentType.UNKNOWN
        }
    }

    @TypeConverter
    fun fromIdentityConfidence(confidence: IdentityConfidence?): String? = confidence?.name

    @TypeConverter
    fun toIdentityConfidence(name: String?): IdentityConfidence? = name?.let {
        try {
            IdentityConfidence.valueOf(it)
        } catch (_: Exception) {
            IdentityConfidence.UNKNOWN
        }
    }

    @TypeConverter
    fun fromAccountSource(source: AccountSource?): String? = source?.name

    @TypeConverter
    fun toAccountSource(name: String?): AccountSource? = name?.let {
        try {
            AccountSource.valueOf(it)
        } catch (_: Exception) {
            AccountSource.OTHER
        }
    }
}
