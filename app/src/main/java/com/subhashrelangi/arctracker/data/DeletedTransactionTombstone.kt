package com.subhashrelangi.arctracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Minimal, privacy-safe tombstone record for transaction sources deleted by the user.
 *
 * Ensures that transactions intentionally deleted by the user are never silently
 * recreated by subsequent historical SMS scans or rescans.
 *
 * Privacy Invariants:
 * - Stores ZERO monetary amounts, account numbers, merchant names, or SMS message bodies.
 * - Retains only the stable source identifier ([sourceKey]), the source category ([sourceType]),
 *   and the deletion timestamp ([deletedAtMillis]).
 */
@Entity(
    tableName = "deleted_transaction_tombstones",
    indices = [
        Index(value = ["sourceType"])
    ]
)
data class DeletedTransactionTombstone(
    @PrimaryKey
    val sourceKey: String,
    val sourceType: String = "SMS_HISTORY",
    val deletedAtMillis: Long = System.currentTimeMillis()
)
