package com.example.arctracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["notificationKey"]),
        Index(value = ["dateMillis"]),
        Index(value = ["isPending"]),
        Index(value = ["relationshipId"]),
        Index(value = ["accountId"]),
        Index(value = ["accountSuffix"]),
        Index(value = ["categoryId"])
    ]
)
data class Expense(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,
    var amount: Double,
    var merchant: String,
    var dateMillis: Long,
    var type: String = "Debit", // "Debit" or "Credit"
    var notificationKey: String = "",
    var isPending: Boolean = false,
    var rawText: String? = null,
    var tag: String? = null,
    var note: String? = null,
    var source: String = "MANUAL",
    var relationshipType: String? = null,
    var relationshipId: String? = null,
    var accountId: String? = null,
    var accountSuffix: String? = null,
    var categoryId: String? = null,
    var categorySource: String = "NONE"
) {
    /**
     * Backward-compatible property accessing the category identifier/name.
     * In M9, [categoryId] is the canonical relational source of truth.
     */
    var category: String?
        @androidx.room.Ignore get() = tag ?: categoryId
        set(value) {
            tag = value
            val mapping = BuiltInCategories.findLegacyMapping(value)
            if (mapping != null) {
                categoryId = mapping.id
                tag = mapping.name
            } else if (value.isNullOrBlank()) {
                categoryId = null
                tag = null
            } else {
                categoryId = value
            }
        }
}

object TransactionRelationshipType {
    const val NONE = "NONE"
    const val SELF_TRANSFER = "SELF_TRANSFER"
}
