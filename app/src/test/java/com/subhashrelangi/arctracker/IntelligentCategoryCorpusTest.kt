package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.BuiltInCategories
import com.subhashrelangi.arctracker.data.UserCategoryRule
import com.subhashrelangi.arctracker.data.UserRuleMatchType
import com.subhashrelangi.arctracker.engine.category.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase 14: Comprehensive 50+ Vector Indian Merchant Intelligent Category Corpus Suite.
 *
 * Validates:
 * 1. High-accuracy offline categorization of real-world Indian regional merchants (Food, Transit, RTCs, Railways, Health, Bills, Shopping, Entertainment).
 * 2. High-confidence auto-assignment (`isAutoAssignable == true`).
 * 3. Negative and ambiguous merchant safety (unresolved without random guessing).
 * 4. Word-boundary substring collision prevention.
 * 5. User-defined rule priority overrides over built-in rules.
 * 6. User-assigned category preservation invariant.
 */
class IntelligentCategoryCorpusTest {

    private lateinit var engine: CategoryInferenceEngine
    private val allActiveCategoryIds = BuiltInCategories.ALL.map { it.id }.toSet()

    @Before
    fun setUp() {
        engine = CategoryInferenceEngine()
    }

    // =========================================================================
    // 1. Food & Dining Vectors (12 Real-World Indian Regional Merchants)
    // =========================================================================

    @Test
    fun testFoodAndDining_regionalCorpus() {
        val foodVectors = listOf(
            "Paid to RNRK Foods" to 100.0,
            "RNRKFOODS" to 150.0,
            "Sri Balaji Sweets & Bakery" to 350.0,
            "Bawarchi Biryani" to 580.0,
            "Annapurna Mess" to 90.0,
            "Vinayaka Tiffins" to 60.0,
            "Chai Point" to 120.0,
            "Hotel Shadab" to 250.0,
            "Sri Krishna Mithai Bhandar" to 420.0,
            "Royal Bakers" to 200.0,
            "Udupi Tiffin Center" to 85.0,
            "Cream Stone Ice Cream" to 280.0
        )

        for ((merchant, amount) in foodVectors) {
            val res = engine.inferCategory(
                CategoryInferenceInput(
                    merchant = merchant,
                    amount = amount,
                    availableCategoryIds = allActiveCategoryIds
                )
            )
            assertEquals("Merchant '$merchant' must infer food_dining", "food_dining", res.suggestedCategoryId)
            assertEquals("Food & Dining", res.suggestedCategoryName)
            assertEquals("Merchant '$merchant' must be HIGH_CONFIDENCE", CategoryInferenceStatus.HIGH_CONFIDENCE, res.status)
            assertTrue("Merchant '$merchant' must be auto-assignable", res.isAutoAssignable)
        }
    }

    // =========================================================================
    // 2. Travel, Transit & RTC Vectors (14 Indian RTC, Train, Airline, Cabs, Fuel)
    // =========================================================================

    @Test
    fun testTransitAndTravel_regionalCorpus() {
        val transitVectors = listOf(
            "APSRTC" to 450.0,
            "TGSRTC Bus Pass" to 1200.0,
            "TSRTC Online" to 380.0,
            "KSRTC Bangalore" to 650.0,
            "MSRTC Mumbai" to 210.0,
            "UPSRTC Bus" to 340.0,
            "BMTC Bus" to 50.0,
            "IRCTC Rail Connect" to 850.0,
            "Indian Railways UTS" to 45.0,
            "IndiGo Airlines" to 4500.0,
            "Orange Travels" to 1400.0,
            "VRL Travels" to 1100.0,
            "Uber India" to 220.0,
            "NHAI FASTag Toll" to 180.0,
            "HPCL Petrol Pump" to 1000.0,
            "Indian Oil Filling Station" to 2000.0
        )

        for ((merchant, amount) in transitVectors) {
            val res = engine.inferCategory(
                CategoryInferenceInput(
                    merchant = merchant,
                    amount = amount,
                    availableCategoryIds = allActiveCategoryIds
                )
            )
            assertEquals("Merchant '$merchant' must infer transport", "transport", res.suggestedCategoryId)
            assertEquals("Travel & Transport", res.suggestedCategoryName)
            assertEquals("Merchant '$merchant' must be HIGH_CONFIDENCE", CategoryInferenceStatus.HIGH_CONFIDENCE, res.status)
            assertTrue("Merchant '$merchant' must be auto-assignable", res.isAutoAssignable)
        }
    }

    // =========================================================================
    // 3. Healthcare & Medical Vectors (8 Pharmacy, Hospital, Lab Merchants)
    // =========================================================================

    @Test
    fun testHealthcare_regionalCorpus() {
        val healthVectors = listOf(
            "Apollo Pharmacy",
            "MedPlus Mart",
            "Netmeds Marketplace",
            "Tata 1mg Healthcare",
            "Sri Sai Medicals & General Stores",
            "Vijaya Diagnostics",
            "City Hospital",
            "Clove Dental"
        )

        for (merchant in healthVectors) {
            val res = engine.inferCategory(
                CategoryInferenceInput(
                    merchant = merchant,
                    availableCategoryIds = allActiveCategoryIds
                )
            )
            assertEquals("Merchant '$merchant' must infer healthcare", "healthcare", res.suggestedCategoryId)
            assertEquals("Healthcare", res.suggestedCategoryName)
            assertEquals("Merchant '$merchant' must be HIGH_CONFIDENCE", CategoryInferenceStatus.HIGH_CONFIDENCE, res.status)
            assertTrue("Merchant '$merchant' must be auto-assignable", res.isAutoAssignable)
        }
    }

    // =========================================================================
    // 4. Bills & Utilities Vectors (8 Discoms, Gas, Broadband Merchants)
    // =========================================================================

    @Test
    fun testBillsAndUtilities_regionalCorpus() {
        val utilityVectors = listOf(
            "APEPDCL Electricity Bill",
            "TSSPDCL Power",
            "BESCOM Electricity",
            "ACT Fibernet",
            "JioFiber Broadband",
            "Airtel Xstream Fiber",
            "HP Gas Booking",
            "Bharat BillPay BBPS"
        )

        for (merchant in utilityVectors) {
            val res = engine.inferCategory(
                CategoryInferenceInput(
                    merchant = merchant,
                    availableCategoryIds = allActiveCategoryIds
                )
            )
            assertEquals("Merchant '$merchant' must infer bills_utilities", "bills_utilities", res.suggestedCategoryId)
            assertEquals("Bills & Utilities", res.suggestedCategoryName)
            assertEquals("Merchant '$merchant' must be HIGH_CONFIDENCE", CategoryInferenceStatus.HIGH_CONFIDENCE, res.status)
            assertTrue("Merchant '$merchant' must be auto-assignable", res.isAutoAssignable)
        }
    }

    // =========================================================================
    // 5. Shopping, Apparel & Retail Vectors (8 Electronics, Silks, Retail)
    // =========================================================================

    @Test
    fun testShoppingAndRetail_regionalCorpus() {
        val shoppingVectors = listOf(
            "Bajaj Electronics",
            "Croma Retail",
            "Reliance Digital",
            "Vijay Sales",
            "Chennai Silks",
            "RS Brothers Garments",
            "Zudio Fashion",
            "Decathlon Sports Store"
        )

        for (merchant in shoppingVectors) {
            val res = engine.inferCategory(
                CategoryInferenceInput(
                    merchant = merchant,
                    availableCategoryIds = allActiveCategoryIds
                )
            )
            assertEquals("Merchant '$merchant' must infer shopping", "shopping", res.suggestedCategoryId)
            assertEquals("Shopping", res.suggestedCategoryName)
            assertEquals("Merchant '$merchant' must be HIGH_CONFIDENCE", CategoryInferenceStatus.HIGH_CONFIDENCE, res.status)
            assertTrue("Merchant '$merchant' must be auto-assignable", res.isAutoAssignable)
        }
    }

    // =========================================================================
    // 6. Entertainment & Cinema Vectors (6 Cinema, Multiplex, Streaming)
    // =========================================================================

    @Test
    fun testEntertainment_regionalCorpus() {
        val entVectors = listOf(
            "BookMyShow Cinema",
            "PVR Multiplex",
            "INOX Cinemas",
            "Asian Cinemas Talkies",
            "Netflix India",
            "Spotify Premium"
        )

        for (merchant in entVectors) {
            val res = engine.inferCategory(
                CategoryInferenceInput(
                    merchant = merchant,
                    availableCategoryIds = allActiveCategoryIds
                )
            )
            assertEquals("Merchant '$merchant' must infer entertainment", "entertainment", res.suggestedCategoryId)
            assertEquals("Entertainment", res.suggestedCategoryName)
            assertEquals("Merchant '$merchant' must be HIGH_CONFIDENCE", CategoryInferenceStatus.HIGH_CONFIDENCE, res.status)
            assertTrue("Merchant '$merchant' must be auto-assignable", res.isAutoAssignable)
        }
    }

    // =========================================================================
    // 7. Negative & Ambiguous Vectors (Safeguard 7: No Random Guessing)
    // =========================================================================

    @Test
    fun testNegativeAndAmbiguousMerchants_remainUnassignedWithoutGuessing() {
        val ambiguousMerchants = listOf(
            "Ramesh Enterprises",
            "ABC Traders",
            "Sharma & Co",
            "Gupta Associates",
            "XYZ Global Solutions"
        )

        for (merchant in ambiguousMerchants) {
            val res = engine.inferCategory(
                CategoryInferenceInput(
                    merchant = merchant,
                    availableCategoryIds = allActiveCategoryIds
                )
            )
            assertEquals("Ambiguous merchant '$merchant' must produce NO_MATCH", CategoryInferenceStatus.NO_MATCH, res.status)
            assertNull("Suggested category ID must be null", res.suggestedCategoryId)
            assertFalse("Must not be auto-assignable", res.isAutoAssignable)
        }
    }

    @Test
    fun testSubstringBoundarySafety_preventsCollisions() {
        // "colab" must NOT match "ola" (transport)
        val colabRes = engine.inferCategory(
            CategoryInferenceInput(merchant = "Google Colab Pro", availableCategoryIds = allActiveCategoryIds)
        )
        assertNotEquals("Colab must not infer transport", "transport", colabRes.suggestedCategoryId)

        // "business" must NOT match "bus" (transport)
        val busRes = engine.inferCategory(
            CategoryInferenceInput(merchant = "Business Solutions Ltd", availableCategoryIds = allActiveCategoryIds)
        )
        assertNotEquals("Business must not infer transport", "transport", busRes.suggestedCategoryId)

        // "vegas" must NOT match "gas" (bills_utilities)
        val vegasRes = engine.inferCategory(
            CategoryInferenceInput(merchant = "Las Vegas Boutique", availableCategoryIds = allActiveCategoryIds)
        )
        assertNotEquals("Vegas must not infer bills_utilities", "bills_utilities", vegasRes.suggestedCategoryId)
    }

    // =========================================================================
    // 8. User Self-Learning Rule Priority Overrides
    // =========================================================================

    @Test
    fun testUserDefinedRule_overridesBuiltInMerchantRule() {
        // Built-in rule maps Starbucks to food_dining.
        // User creates rule mapping Starbucks to "other" (e.g. Work Meeting).
        val userRule = UserCategoryRule(
            id = "user_rule_starbucks",
            pattern = "Starbucks",
            normalizedPattern = "starbucks",
            categoryId = "other",
            matchType = UserRuleMatchType.MERCHANT_TOKEN,
            priority = 100,
            isEnabled = true
        )

        val res = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Starbucks Coffee",
                userRules = listOf(userRule),
                availableCategoryIds = allActiveCategoryIds
            )
        )

        assertEquals("User rule must override built-in rule", "other", res.suggestedCategoryId)
        assertEquals(CategoryInferenceStatus.HIGH_CONFIDENCE, res.status)
        assertEquals("user_rule_starbucks", res.matchedUserRuleId)
    }

    // =========================================================================
    // 9. Manual Category Preservation Invariant
    // =========================================================================

    @Test
    fun testManualCategoryPreservation_neverOverwritten() {
        val res = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Swiggy",
                existingCategoryId = "shopping",
                existingCategorySource = CategorySource.USER_ASSIGNED,
                availableCategoryIds = allActiveCategoryIds
            )
        )

        assertEquals(CategoryInferenceStatus.USER_ASSIGNED_PRESERVED, res.status)
        assertEquals("shopping", res.suggestedCategoryId)
        assertEquals("Shopping", res.suggestedCategoryName)
    }
}
