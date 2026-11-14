package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.engine.category.MerchantNormalizer
import org.junit.Assert.*
import org.junit.Test

/**
 * JVM Unit Test Suite for Phase 11: Compound UPI Merchant Tokenizer & Morphological Decomposer.
 */
class MerchantNormalizerTest {

    @Test
    fun testDecomposeMerchantTokens_spacedWords() {
        val tokens = MerchantNormalizer.decomposeMerchantTokens("RNRK Foods")
        assertTrue(tokens.contains("rnrk"))
        assertTrue(tokens.contains("foods"))
        assertTrue(MerchantNormalizer.containsToken("RNRK Foods", "foods"))
    }

    @Test
    fun testDecomposeMerchantTokens_concatenatedSingleCase() {
        val tokens = MerchantNormalizer.decomposeMerchantTokens("RNRKFOODS")
        assertTrue(tokens.contains("rnrk"))
        assertTrue(tokens.contains("foods"))
        assertTrue(MerchantNormalizer.containsToken("RNRKFOODS", "foods"))
    }

    @Test
    fun testDecomposeMerchantTokens_withPrefixAndNumericNoise() {
        val tokens = MerchantNormalizer.decomposeMerchantTokens("UPI-RNRKFOODS-123")
        assertTrue(tokens.contains("rnrk"))
        assertTrue(tokens.contains("foods"))
        assertTrue(MerchantNormalizer.containsToken("UPI-RNRKFOODS-123", "foods"))
    }

    @Test
    fun testDecomposeMerchantTokens_sriBalajiSweets() {
        val tokens = MerchantNormalizer.decomposeMerchantTokens("SRIBALAJISWEETS")
        assertTrue(tokens.contains("sweets"))
        assertTrue(MerchantNormalizer.containsToken("SRIBALAJISWEETS", "sweets"))
    }

    @Test
    fun testDecomposeMerchantTokens_apsrtcOnline() {
        val tokens = MerchantNormalizer.decomposeMerchantTokens("APSRTCONLINE")
        assertTrue(tokens.contains("apsrtc"))
        assertTrue(tokens.contains("online"))
        assertTrue(MerchantNormalizer.containsToken("APSRTCONLINE", "apsrtc"))
    }

    @Test
    fun testDecomposeMerchantTokens_orangeTravelsWithSeparators() {
        val tokens = MerchantNormalizer.decomposeMerchantTokens("Orange_Travels")
        assertTrue(tokens.contains("orange"))
        assertTrue(tokens.contains("travels"))
        assertTrue(MerchantNormalizer.containsToken("Orange_Travels", "travels"))
    }

    @Test
    fun testDecomposeMerchantTokens_vpaHandle() {
        val tokens = MerchantNormalizer.decomposeMerchantTokens("rnrkfoods@okaxis")
        assertTrue(tokens.contains("rnrk"))
        assertTrue(tokens.contains("foods"))
        assertTrue(MerchantNormalizer.containsToken("rnrkfoods@okaxis", "foods"))
    }

    @Test
    fun testDecomposeMerchantTokens_camelCase() {
        val tokens = MerchantNormalizer.decomposeMerchantTokens("SriBalajiSweets")
        assertTrue(tokens.contains("sri"))
        assertTrue(tokens.contains("balaji"))
        assertTrue(tokens.contains("sweets"))
    }

    @Test
    fun testDecomposeMerchantTokens_gatewayTransportPrefix() {
        val tokens = MerchantNormalizer.decomposeMerchantTokens("RAZORPAY*SWIGGY")
        assertTrue(tokens.contains("swiggy"))
        assertFalse(tokens.contains("razorpay"))
    }

    @Test
    fun testWordBoundarySafety_negativeSubstringCollisions() {
        assertFalse("ola must not match colab", MerchantNormalizer.containsToken("colab notebook", "ola"))
        assertFalse("ola must not match polar", MerchantNormalizer.containsToken("polar bear", "ola"))
        assertFalse("gas must not match vegas", MerchantNormalizer.containsToken("las vegas hotel", "gas"))
        assertFalse("gas must not match gasket", MerchantNormalizer.containsToken("gasket repair", "gas"))
        assertFalse("bus must not match business", MerchantNormalizer.containsToken("business suite", "bus"))
    }

    @Test
    fun testDomainSuffix_bookingComPreservedAsDistinctBrand() {
        assertEquals("netflix", MerchantNormalizer.normalize("WWW.NETFLIX.COM"))
        assertEquals("booking com", MerchantNormalizer.normalize("Booking.com"))
        assertTrue(MerchantNormalizer.containsToken("Booking.com B.V.", "booking com"))
        assertFalse(MerchantNormalizer.containsToken("HP Gas Booking", "booking com"))
    }
}
