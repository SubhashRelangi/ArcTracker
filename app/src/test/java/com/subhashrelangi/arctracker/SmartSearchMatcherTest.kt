package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.ui.core.SmartSearchMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSearchMatcherTest {

    @Test
    fun testEmptyQueryMatchesAll() {
        assertTrue(SmartSearchMatcher.matches("", "Amazon India", "Shopping"))
        assertTrue(SmartSearchMatcher.matches("   ", "Swiggy", "Food & Dining"))
    }

    @Test
    fun testSingleLetterStartingMatch() {
        // "a" should match words starting with "a"
        assertTrue(SmartSearchMatcher.matches("a", "Amazon India"))
        assertTrue(SmartSearchMatcher.matches("a", "Axis Bank"))
        assertTrue(SmartSearchMatcher.matches("a", "Auto & Transport"))
        assertTrue(SmartSearchMatcher.matches("a", "Apple Store"))

        // "a" should NOT match words that merely contain "a" in the middle
        assertFalse(SmartSearchMatcher.matches("a", "Swiggy"))
        assertFalse(SmartSearchMatcher.matches("a", "Starbucks"))
        assertFalse(SmartSearchMatcher.matches("a", "Uber"))
        assertFalse(SmartSearchMatcher.matches("a", "Zomato"))
    }

    @Test
    fun testSingleLetterCaseInsensitive() {
        assertTrue(SmartSearchMatcher.matches("A", "amazon"))
        assertTrue(SmartSearchMatcher.matches("s", "Starbucks"))
        assertFalse(SmartSearchMatcher.matches("s", "Amazon"))
    }

    @Test
    fun testPieceToPieceMultiTokenMatching() {
        // Piece-to-piece: both "amazon" and "500" must match candidate fields
        assertTrue(SmartSearchMatcher.matches("amazon 500", "Amazon India", "Shopping", "500.00"))
        assertFalse(SmartSearchMatcher.matches("amazon 500", "Amazon India", "Shopping", "1200.00"))

        // Multi-word category match
        assertTrue(SmartSearchMatcher.matches("food dining", "Swiggy", "Food & Dining"))
        assertTrue(SmartSearchMatcher.matches("swiggy food", "Swiggy", "Food & Dining"))
    }

    @Test
    fun testRelevanceScoring() {
        val scorePrefix = SmartSearchMatcher.score("ama", "Amazon India")
        val scoreContains = SmartSearchMatcher.score("ama", "Panama Jack")
        val scoreNone = SmartSearchMatcher.score("ama", "Starbucks")

        assertTrue(scorePrefix > scoreContains)
        assertTrue(scoreContains > scoreNone)
        assertEquals(0, scoreNone)
    }
}
