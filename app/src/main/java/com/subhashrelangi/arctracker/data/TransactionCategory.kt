package com.subhashrelangi.arctracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent Category Entity for ArcTracker (Milestone 9).
 *
 * Guarantees:
 * - Stable, deterministic string IDs (not display names, not resource IDs).
 * - Distinguishes system built-in categories from user custom categories.
 * - Supports icon and color key mappings that work across light and dark themes.
 * - Supports safe archiving and deterministic sorting.
 */
@Entity(
    tableName = "transaction_categories",
    indices = [
        Index(value = ["isArchived"]),
        Index(value = ["sortOrder"])
    ]
)
data class TransactionCategory(
    @PrimaryKey
    val id: String,
    val name: String,
    val iconKey: String = "category",
    val colorKey: String = "default",
    val isSystem: Boolean = false,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
