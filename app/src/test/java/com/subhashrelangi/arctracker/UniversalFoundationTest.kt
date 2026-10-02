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
        // Backgrounds
        assertEquals(Color(0xFF090C10), ArcColors.Background)
        assertEquals(ArcColors.Background, LedgerColors.Background)
        assertEquals(com.subhashrelangi.arctracker.ui.review.ArcColors.Background, ArcColors.Background)

        // Financial indicator colors
        assertEquals(Color(0xFFF43F5E), ArcColors.Debit)
        assertEquals(ArcColors.Debit, LedgerColors.Debit)
        assertEquals(Color(0xFF00E676), ArcColors.Credit)
        assertEquals(ArcColors.Credit, LedgerColors.Credit)

        // Warning / Amber
        assertEquals(Color(0xFFFBBF24), ArcColors.WarningAmber)
        assertEquals(ArcColors.WarningAmber, LedgerColors.WarningAmber)

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
}
