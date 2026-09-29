package com.example.arctracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Supported match types for user-defined category rules (Milestone 11).
 * Safe, deterministic matching types only — no arbitrary regex or executable code.
 */
object UserRuleMatchType {
    const val MERCHANT_EXACT = "MERCHANT_EXACT"
    const val MERCHANT_CONTAINS = "MERCHANT_CONTAINS"
    const val MERCHANT_TOKEN = "MERCHANT_TOKEN"
    const val TRANSACTION_TEXT_CONTAINS = "TRANSACTION_TEXT_CONTAINS"

    val ALL = listOf(
        MERCHANT_EXACT,
        MERCHANT_CONTAINS,
        MERCHANT_TOKEN,
        TRANSACTION_TEXT_CONTAINS
    )
}

/**
 * Persistent Room Entity for user-defined category rules (Milestone 11).
 *
 * Guarantees:
 * - Stable, persistent UUID primary key (never the pattern itself).
 * - Normalized pattern stored and indexed for deterministic, high-speed matching.
 * - Explicit enable/disable toggle.
 * - Explicit deterministic priority.
 * - Tied to persistent category ID.
 */
@Entity(
    tableName = "user_category_rules",
    indices = [
        Index(value = ["normalizedPattern"]),
        Index(value = ["categoryId"]),
        Index(value = ["isEnabled"])
    ]
)
data class UserCategoryRule(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String? = null,
    val matchType: String = UserRuleMatchType.MERCHANT_EXACT,
    val pattern: String,
    val normalizedPattern: String,
    val categoryId: String,
    val priority: Int = 100,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
