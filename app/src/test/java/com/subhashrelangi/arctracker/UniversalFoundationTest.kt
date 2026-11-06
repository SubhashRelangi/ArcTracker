package com.subhashrelangi.arctracker

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.subhashrelangi.arctracker.ui.core.ArcDestination
import com.subhashrelangi.arctracker.ui.ledger.LedgerColors
import com.subhashrelangi.arctracker.ui.ledger.LedgerShapes
import com.subhashrelangi.arctracker.ui.ledger.LedgerSpacing
import com.subhashrelangi.arctracker.ui.theme.ArcColors
import com.subhashrelangi.arctracker.ui.theme.ArcDimensions
import com.subhashrelangi.arctracker.ui.theme.ArcShapes
import com.subhashrelangi.arctracker.ui.theme.ArcSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class UniversalFoundationTest {

    @Test
    fun testArcDestinationRoutingMapping() {
        assertEquals(ArcDestination.HOME, ArcDestination.fromRoute("Home"))
        assertEquals(ArcDestination.LEDGER, ArcDestination.fromRoute("Transactions"))
        assertEquals(ArcDestination.LEDGER, ArcDestination.fromRoute("Ledger"))
        assertEquals(ArcDestination.REVIEW, ArcDestination.fromRoute("Pending"))
        assertEquals(ArcDestination.REVIEW, ArcDestination.fromRoute("Review"))
        assertEquals(ArcDestination.INSIGHTS, ArcDestination.fromRoute("Insights"))
        assertEquals(ArcDestination.INSIGHTS, ArcDestination.fromRoute("Analytics"))
        assertEquals(ArcDestination.SETTINGS, ArcDestination.fromRoute("Settings"))
        // Fallback default
        assertEquals(ArcDestination.HOME, ArcDestination.fromRoute("UnknownScreen"))
    }

    @Test
    fun testArcDestinationTitlesAndRoutes() {
        assertEquals("Home", ArcDestination.HOME.title)
        assertEquals("Home", ArcDestination.HOME.route)

        assertEquals("Ledger", ArcDestination.LEDGER.title)
        assertEquals("Transactions", ArcDestination.LEDGER.route)

        assertEquals("Review", ArcDestination.REVIEW.title)
        assertEquals("Pending", ArcDestination.REVIEW.route)

        assertEquals("Insights", ArcDestination.INSIGHTS.title)
        assertEquals("Insights", ArcDestination.INSIGHTS.route)

        assertEquals("Settings", ArcDestination.SETTINGS.title)
        assertEquals("Settings", ArcDestination.SETTINGS.route)
    }

    @Test
    fun testTokenCentralizationAndDelegation() {
        // Light Theme Tokens
        assertEquals(Color(0xFFF8FAFC), ArcColors.Light.background)
        assertEquals(Color(0xFFF8FAFC), LedgerColors.Light.background)
        assertEquals(Color(0xFF0F172A), ArcColors.Light.debit)
        assertEquals(Color(0xFF047857), ArcColors.Light.credit)
        assertEquals(Color(0xFFD97706), ArcColors.Light.warningAmber)
        assertEquals(Color(0xFFD97706), LedgerColors.Light.warningAmber)

        // Dark Theme Tokens
        assertEquals(Color(0xFF090D16), ArcColors.Dark.background)
        assertEquals(Color(0xFF090D16), LedgerColors.Dark.background)
        assertEquals(Color(0xFFF8FAFC), ArcColors.Dark.debit)
        assertEquals(Color(0xFF10B981), ArcColors.Dark.credit)
        assertEquals(Color(0xFFF59E0B), ArcColors.Dark.warningAmber)
        assertEquals(Color(0xFFF59E0B), LedgerColors.Dark.warningAmber)

        // Spacing & Shapes
        assertEquals(ArcShapes.Card, LedgerShapes.Card)
        assertEquals(ArcSpacing.sm, LedgerSpacing.sm)
        assertEquals(ArcSpacing.md, LedgerSpacing.md)
        assertEquals(ArcSpacing.lg, LedgerSpacing.lg)
    }

    @Test
    fun testArcDimensions() {
        assertEquals(40.dp, ArcDimensions.IconButtonSize)
        assertEquals(36.dp, ArcDimensions.BrandLogoSize)
        assertEquals(6.dp, ArcDimensions.LiveDotSize)
    }

    @Test
    fun testAnalyticsDateRangeLabels() {
        val ranges = com.subhashrelangi.arctracker.service.AnalyticsDateRange.values()
        assertEquals(7, ranges.size)
        assertEquals("This Month", ranges[0].label)
        assertEquals("Last Month", ranges[1].label)
        assertEquals("Last 3 Months", ranges[2].label)
        assertEquals("Today", ranges[3].label)
        assertEquals("This Week", ranges[4].label)
        assertEquals("All Time", ranges[5].label)
        assertEquals("Custom Range", ranges[6].label)
    }

    @Test
    fun testPrimaryDestinationsSet() {
        val primaryRoutes = setOf("Home", "Transactions", "Pending", "Review", "Insights", "Settings")
        assertEquals(true, primaryRoutes.contains(ArcDestination.HOME.route))
        assertEquals(true, primaryRoutes.contains(ArcDestination.LEDGER.route))
        assertEquals(true, primaryRoutes.contains(ArcDestination.REVIEW.route))
        assertEquals(true, primaryRoutes.contains(ArcDestination.INSIGHTS.route))
        assertEquals(true, primaryRoutes.contains(ArcDestination.SETTINGS.route))
    }
}
