package com.example.arctracker.data

data class Expense(
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
    var source: String = "MANUAL"
)
