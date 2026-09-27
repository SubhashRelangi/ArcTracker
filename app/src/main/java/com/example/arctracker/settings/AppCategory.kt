package com.example.arctracker.settings

/**
 * Centralized, stable definition of supported application categories.
 */
enum class AppCategory(val id: String, val displayName: String) {
    UPI_PAYMENT("upi_payment", "UPI & Payment Apps"),
    BANKING("banking", "Banking Apps"),
    SMS_MESSENGER("sms_messenger", "SMS & Messenger Apps");

    companion object {
        fun fromId(id: String): AppCategory? =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) }

        fun fromDisplayName(name: String): AppCategory? =
            values().firstOrNull { it.displayName.equals(name, ignoreCase = true) }
    }
}
