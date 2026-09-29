package com.subhashrelangi.arctracker.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Visual mappings for category icon and color keys.
 *
 * Uses stable string keys stored in Room instead of fragile Android resource IDs.
 * Works seamlessly in both Light and Dark themes.
 */
object CategoryVisuals {

    val AVAILABLE_ICON_KEYS = listOf(
        "restaurant",
        "shopping_bag",
        "directions_car",
        "receipt",
        "movie",
        "medical_services",
        "flight",
        "school",
        "swap_horiz",
        "account_balance_wallet",
        "payments",
        "category",
        "coffee",
        "fitness_center",
        "home",
        "pets",
        "work",
        "card_giftcard",
        "savings",
        "local_grocery_store"
    )

    val AVAILABLE_COLOR_KEYS = listOf(
        "orange",
        "purple",
        "blue",
        "teal",
        "pink",
        "red",
        "cyan",
        "amber",
        "indigo",
        "brown",
        "green",
        "default"
    )

    fun getIcon(key: String): ImageVector {
        return when (key.lowercase()) {
            "restaurant", "food", "dining" -> Icons.Default.Restaurant
            "shopping_bag", "shopping" -> Icons.Default.ShoppingBag
            "directions_car", "transport", "car" -> Icons.Default.DirectionsCar
            "receipt", "bills" -> Icons.Default.Receipt
            "movie", "entertainment" -> Icons.Default.Movie
            "medical_services", "health", "medical" -> Icons.Default.MedicalServices
            "flight", "travel" -> Icons.Default.Flight
            "school", "education" -> Icons.Default.School
            "swap_horiz", "transfer" -> Icons.Default.SwapHoriz
            "account_balance_wallet", "cash", "atm" -> Icons.Default.AccountBalanceWallet
            "payments", "income", "salary" -> Icons.Default.Payments
            "coffee", "cafe" -> Icons.Default.LocalCafe
            "fitness_center", "gym", "fitness" -> Icons.Default.FitnessCenter
            "home", "housing", "rent" -> Icons.Default.Home
            "pets", "pet" -> Icons.Default.Pets
            "work", "office" -> Icons.Default.Work
            "card_giftcard", "gift" -> Icons.Default.CardGiftcard
            "savings", "invest" -> Icons.Default.Savings
            "local_grocery_store", "grocery" -> Icons.Default.LocalGroceryStore
            else -> Icons.Default.Category
        }
    }

    fun getColor(key: String): Color {
        return when (key.lowercase()) {
            "orange" -> Color(0xFFF57C00)
            "purple" -> Color(0xFF7B1FA2)
            "blue" -> Color(0xFF1976D2)
            "teal" -> Color(0xFF00796B)
            "pink" -> Color(0xFFC2185B)
            "red" -> Color(0xFFD32F2F)
            "cyan" -> Color(0xFF0097A7)
            "amber" -> Color(0xFFFFA000)
            "indigo" -> Color(0xFF303F9F)
            "brown" -> Color(0xFF5D4037)
            "green" -> Color(0xFF388E3C)
            else -> Color(0xFF757575)
        }
    }

    fun getContainerColor(key: String): Color {
        return when (key.lowercase()) {
            "orange" -> Color(0xFFFFF3E0)
            "purple" -> Color(0xFFF3E5F5)
            "blue" -> Color(0xFFE3F2FD)
            "teal" -> Color(0xFFE0F2F1)
            "pink" -> Color(0xFFFCE4EC)
            "red" -> Color(0xFFFFEBEE)
            "cyan" -> Color(0xFFE0F7FA)
            "amber" -> Color(0xFFFFF8E1)
            "indigo" -> Color(0xFFE8EAF6)
            "brown" -> Color(0xFFEFEBE9)
            "green" -> Color(0xFFE8F5E9)
            else -> Color(0xFFF5F5F5)
        }
    }

    fun isValidIconKey(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        return AVAILABLE_ICON_KEYS.contains(key.trim().lowercase())
    }

    fun isValidColorKey(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        return AVAILABLE_COLOR_KEYS.contains(key.trim().lowercase())
    }
}
