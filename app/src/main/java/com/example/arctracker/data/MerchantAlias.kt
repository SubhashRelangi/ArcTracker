package com.example.arctracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Persistent Room Entity for user merchant aliases (Milestone 11).
 *
 * Normalizes variations of a merchant's name into a canonical merchant identity.
 * Does NOT directly assign a category — keeps merchant identity separate from categorization.
 *
 * Example:
 * Alias: "AMZN Mktp" -> Canonical: "Amazon"
 * Alias: "AMAZON PAY INDIA" -> Canonical: "Amazon"
 */
@Entity(
    tableName = "merchant_aliases",
    indices = [
        Index(value = ["normalizedAlias"]),
        Index(value = ["isEnabled"])
    ]
)
data class MerchantAlias(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val alias: String,
    val canonicalMerchant: String,
    val normalizedAlias: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
