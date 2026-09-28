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
        Index(value = ["relationshipId"])
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
    var relationshipId: String? = null
)

object TransactionRelationshipType {
    const val NONE = "NONE"
    const val SELF_TRANSFER = "SELF_TRANSFER"
}
