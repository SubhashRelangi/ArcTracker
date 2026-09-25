package com.example.arctracker.service

/**
 * Direction candidate for a structured transaction (Step 5).
 */
enum class TransactionDirection {
    DEBIT,
    CREDIT,
    UNKNOWN
}

/**
 * Textual transaction status extracted from notification content (Step 5).
 */
enum class TransactionStatus {
    SUCCESS,
    FAILED,
    PENDING,
    REVERSED,
    REFUNDED,
    DECLINED
}

/**
 * Category for secondary monetary values identified in the notification (e.g. balance, fee, cashback).
 */
enum class SecondaryAmountType {
    BALANCE,
    FEE,
    CASHBACK,
    TAX_OR_GST,
    DISCOUNT,
    TOTAL,
    LIMIT,
    OTHER
}

/**
 * Represents a secondary monetary value detected in notification context.
 * Kept separate from the primary transaction amount.
 */
data class SecondaryAmount(
    val type: SecondaryAmountType,
    val amount: Double,
    val rawText: String,
    val evidence: String
)

/**
 * Explains where an extracted field came from and what evidence supports it.
 */
data class FieldEvidence(
    val fieldName: String,
    val extractedValue: String,
    val sourceSnippet: String,
    val ruleOrPattern: String? = null
)

/**
 * Structured transaction candidate produced from a single classified notification (Step 5).
 *
 * All financial fields are nullable when evidence is absent.
 * Missing values are never filled with placeholders like "Unknown" or 0.
 * Traceable back to the source notification via metadata.
 */
data class StructuredTransactionCandidate(
    val sourceNotificationKey: String,
    val packageName: String,
    val postTime: Long,
    val amount: Double? = null,
    val currency: String? = null,
    val merchant: String? = null,
    val counterparty: String? = null,
    val direction: TransactionDirection = TransactionDirection.UNKNOWN,
    val status: TransactionStatus? = null,
    val referenceId: String? = null,
    val utr: String? = null,
    val rrn: String? = null,
    val upiTransactionId: String? = null,
    val upiId: String? = null,
    val accountSuffix: String? = null,
    val cardSuffix: String? = null,
    val secondaryAmounts: List<SecondaryAmount> = emptyList(),
    val evidence: Map<String, FieldEvidence> = emptyMap(),
    val isUpdate: Boolean = false,
    val groupKey: String? = null,
    val isGroup: Boolean = false,
    val isGroupSummary: Boolean = false
) {
    /**
     * Retrieves the evidence object for a specific field if present.
     */
    fun getEvidence(fieldName: String): FieldEvidence? = evidence[fieldName]
}
