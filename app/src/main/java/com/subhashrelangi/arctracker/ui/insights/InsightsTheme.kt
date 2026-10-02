package com.subhashrelangi.arctracker.ui.insights

import androidx.compose.ui.graphics.Color
import com.subhashrelangi.arctracker.data.CategoryVisuals

/**
 * Design tokens and semantic color mappings for the ArcTracker Insights dashboard.
 * Unified with universal theme tokens in com.subhashrelangi.arctracker.ui.theme.
 */
object InsightsTheme {

    // Surfaces & Containers
    val Background = Color(0xFF090C10)
    val CardBackground = Color(0xFF161B22)
    val CardBorder = Color(0xFF30363D)
    val SubCardBackground = Color(0xFF0D1117)
    val SubCardBorder = Color(0xFF21262D)

    // Financial & Status Indicators
    val PositiveGreen = Color(0xFF00E676)
    val PositiveGreenSubtle = Color(0xFF3FB950)
    val PositiveGreenContainer = Color(0xFF0D3320)
    val NegativeRed = Color(0xFFF85149)
    val NegativeRedSubtle = Color(0xFFFF7B72)
    val WarningAmber = Color(0xFFE3B341)
    val WarningAmberContainer = Color(0xFF2D2200)
    val PrimaryBlue = Color(0xFF58A6FF)
    val BlueContainer = Color(0xFF0C2D6B)

    // Typography Colors
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8B949E)
    val TextTertiary = Color(0xFF6E7681)

    // Category Semantic Colors (as per reference design)
    val CategoryFood = Color(0xFFE3B341)           // Amber / Yellow
    val CategoryShopping = Color(0xFF58A6FF)       // Blue
    val CategoryBills = Color(0xFFF85149)          // Coral / Red
    val CategoryTransport = Color(0xFF3FB950)      // Green
    val CategoryEntertainment = Color(0xFFA5D6FF)  // Light Blue / Lavender
    val CategoryHealth = Color(0xFFBC8CFF)         // Purple
    val CategoryMisc = Color(0xFF8B949E)           // Gray

    /**
     * Resolves category color based on category name or fallback color key.
     */
    fun resolveCategoryColor(categoryName: String, fallbackColorKey: String = ""): Color {
        val lower = categoryName.lowercase()
        return when {
            lower.contains("food") || lower.contains("dining") || lower.contains("restaurant") || lower.contains("cafe") -> CategoryFood
            lower.contains("shopping") || lower.contains("retail") || lower.contains("store") -> CategoryShopping
            lower.contains("bill") || lower.contains("utilit") || lower.contains("recharge") -> CategoryBills
            lower.contains("transport") || lower.contains("commute") || lower.contains("travel") || lower.contains("fuel") || lower.contains("cab") -> CategoryTransport
            lower.contains("entertainment") || lower.contains("subs") || lower.contains("movie") || lower.contains("stream") -> CategoryEntertainment
            lower.contains("health") || lower.contains("medical") || lower.contains("pharmacy") -> CategoryHealth
            lower.contains("other") || lower.contains("misc") || lower.contains("uncategorized") -> CategoryMisc
            fallbackColorKey.isNotBlank() -> CategoryVisuals.getColor(fallbackColorKey)
            else -> CategoryMisc
        }
    }
}
