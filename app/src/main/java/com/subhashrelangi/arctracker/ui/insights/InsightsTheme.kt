package com.subhashrelangi.arctracker.ui.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.subhashrelangi.arctracker.data.CategoryVisuals
import com.subhashrelangi.arctracker.ui.theme.LocalArcColors

/**
 * Design tokens and semantic color mappings for the ArcTracker Insights dashboard.
 * Unified dynamically with universal theme tokens in com.subhashrelangi.arctracker.ui.theme.
 */
object InsightsTheme {

    // Dynamic Surfaces & Containers
    val LightBackground: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.background
    val LightCardBackground: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.cardBackground
    val LightCardBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.border
    val LightSubCardBackground: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.surfaceHighlight
    val LightSubCardBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.borderSubtle

    // Dynamic Typography
    val LightTextPrimary: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.textPrimary
    val LightTextSecondary: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.textSecondary
    val LightTextMuted: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.textMuted

    // Accent Colors
    val EmeraldGreen: Color @Composable @ReadOnlyComposable get() = if (LocalArcColors.current.isDark) Color(0xFF34D399) else Color(0xFF15803D)
    val EmeraldGreenDark: Color @Composable @ReadOnlyComposable get() = if (LocalArcColors.current.isDark) Color(0xFF10B981) else Color(0xFF047857)
    val EmeraldGreenBadge: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.primaryLight
    val EmeraldGreenBadgeBorder: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.primaryBorder
    val AmberBrown: Color @Composable @ReadOnlyComposable get() = if (LocalArcColors.current.isDark) Color(0xFFFCD34D) else Color(0xFF92400E)
    val AmberOrange: Color @Composable @ReadOnlyComposable get() = if (LocalArcColors.current.isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
    val AmberBadge: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.warningAmberBg
    val CharcoalDark: Color @Composable @ReadOnlyComposable get() = LocalArcColors.current.textPrimary

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
