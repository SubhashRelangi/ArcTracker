package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.BuiltInCategories
import com.subhashrelangi.arctracker.service.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * JVM Unit Test Suite for Milestone 10: Automatic Category Inference.
 *
 * Covers all 60+ required verification scenarios:
 * 1. Merchant normalizer (lowercase, whitespace, punctuation, prefixes, token boundaries).
 * 2. Food & Dining inference.
 * 3. Shopping inference.
 * 4. Transport inference.
 * 5. Entertainment inference.
 * 6. Healthcare inference.
 * 7. Travel inference.
 * 8. Education inference.
 * 9. Bills & Utilities inference.
 * 10. Income protection & Credit constraint.
 * 11. Cash withdrawal protection.
 * 12. Transfer protection (precedence over merchant keywords).
 * 13. Conflict detection and deterministic resolution.
 * 14. Refunds, reversals, and cashback ambiguity protection.
 * 15. Word-boundary safety (no substring false positives).
 * 16. Category availability (archived/deleted categories excluded).
 * 17. Custom category rule support.
 * 18. User override protection (USER_ASSIGNED category preserved).
 * 19. Determinism and crash safety.
 */
class CategoryInferenceEngineTest {

    private lateinit var engine: CategoryInferenceEngine
    private val allActiveCategoryIds = BuiltInCategories.ALL.map { it.id }.toSet()

    @Before
    fun setUp() {
        engine = CategoryInferenceEngine()
    }

    // ==========================================
    // 1. Normalization & Word Boundaries
    // ==========================================

    @Test
    fun testNormalizationLowercaseAndWhitespace() {
        assertEquals("swiggy", MerchantNormalizer.normalize("  SWIGGY  "))
        assertEquals("uber india", MerchantNormalizer.normalize("UBER   INDIA"))
        assertEquals("cafe coffee day", MerchantNormalizer.normalize("Cafe\tCoffee\nDay"))
    }

    @Test
    fun testNormalizationPrefixStripping() {
        assertEquals("swiggy", MerchantNormalizer.normalize("UPI/SWIGGY"))
        assertEquals("amazon", MerchantNormalizer.normalize("POS AMAZON"))
        assertEquals("zomato", MerchantNormalizer.normalize("VPA/ZOMATO"))
        assertEquals("netflix", MerchantNormalizer.normalize("WWW.NETFLIX.COM"))
        assertEquals("flipkart", MerchantNormalizer.normalize("Paid to Flipkart"))
    }

    @Test
    fun testNormalizationPunctuationAndSeparators() {
        assertEquals("swiggy order 123", MerchantNormalizer.normalize("SWIGGY - ORDER #123"))
        assertEquals("uber ride", MerchantNormalizer.normalize("UBER_RIDE*"))
    }

    @Test
    fun testWordBoundaryContainment() {
        assertTrue(MerchantNormalizer.containsToken("ola cabs", "ola"))
        assertTrue(MerchantNormalizer.containsToken("ola", "ola"))
        assertFalse("ola must not match colab", MerchantNormalizer.containsToken("colab notebook", "ola"))
        assertFalse("ola must not match polar", MerchantNormalizer.containsToken("polar bear", "ola"))

        assertTrue(MerchantNormalizer.containsToken("gas bill", "gas"))
        assertFalse("gas must not match vegas", MerchantNormalizer.containsToken("las vegas hotel", "gas"))
        assertFalse("gas must not match gasket", MerchantNormalizer.containsToken("gasket repair", "gas"))
    }

    // ==========================================
    // 2. Food & Dining
    // ==========================================

    @Test
    fun testFoodInferenceSwiggy() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Swiggy", availableCategoryIds = allActiveCategoryIds))
        assertEquals(CategoryInferenceStatus.HIGH_CONFIDENCE, result.status)
        assertEquals("food_dining", result.suggestedCategoryId)
        assertTrue(result.isAutoAssignable)
    }

    @Test
    fun testFoodInferenceZomato() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "UPI/ZOMATO-PAY", availableCategoryIds = allActiveCategoryIds))
        assertEquals(CategoryInferenceStatus.HIGH_CONFIDENCE, result.status)
        assertEquals("food_dining", result.suggestedCategoryId)
    }

    @Test
    fun testFoodInferenceDominos() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Dominos Pizza", availableCategoryIds = allActiveCategoryIds))
        assertEquals(CategoryInferenceStatus.HIGH_CONFIDENCE, result.status)
        assertEquals("food_dining", result.suggestedCategoryId)
    }

    @Test
    fun testFoodInferenceRestaurantKeyword() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Grand Hyatt Restaurant", availableCategoryIds = allActiveCategoryIds))
        assertEquals("food_dining", result.suggestedCategoryId)
    }

    @Test
    fun testFoodInferenceCafeKeyword() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Blue Tokai Cafe", availableCategoryIds = allActiveCategoryIds))
        assertEquals("food_dining", result.suggestedCategoryId)
    }

    // ==========================================
    // 3. Shopping
    // ==========================================

    @Test
    fun testShoppingInferenceAmazon() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Amazon India", availableCategoryIds = allActiveCategoryIds))
        assertEquals(CategoryInferenceStatus.HIGH_CONFIDENCE, result.status)
        assertEquals("shopping", result.suggestedCategoryId)
    }

    @Test
    fun testShoppingInferenceFlipkart() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Flipkart Internet", availableCategoryIds = allActiveCategoryIds))
        assertEquals(CategoryInferenceStatus.HIGH_CONFIDENCE, result.status)
        assertEquals("shopping", result.suggestedCategoryId)
    }

    @Test
    fun testShoppingInferenceMyntra() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Myntra Designs", availableCategoryIds = allActiveCategoryIds))
        assertEquals("shopping", result.suggestedCategoryId)
    }

    @Test
    fun testShoppingInferenceSupermarket() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Nature's Basket Supermarket", availableCategoryIds = allActiveCategoryIds))
        assertEquals("shopping", result.suggestedCategoryId)
    }

    // ==========================================
    // 4. Transport
    // ==========================================

    @Test
    fun testTransportInferenceUber() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Uber Trip", availableCategoryIds = allActiveCategoryIds))
        assertEquals(CategoryInferenceStatus.HIGH_CONFIDENCE, result.status)
        assertEquals("transport", result.suggestedCategoryId)
    }

    @Test
    fun testTransportInferenceOla() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Ola Cabs", availableCategoryIds = allActiveCategoryIds))
        assertEquals(CategoryInferenceStatus.HIGH_CONFIDENCE, result.status)
        assertEquals("transport", result.suggestedCategoryId)
    }

    @Test
    fun testTransportInferenceFuel() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "HPCL Petrol Pump", availableCategoryIds = allActiveCategoryIds))
        assertEquals("transport", result.suggestedCategoryId)
    }

    @Test
    fun testTransportInferenceMetro() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Namma Metro Bangalore", availableCategoryIds = allActiveCategoryIds))
        assertEquals("transport", result.suggestedCategoryId)
    }

    @Test
    fun testTransportInferenceTollFastag() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "NETC FASTag Toll Plaza", availableCategoryIds = allActiveCategoryIds))
        assertEquals("transport", result.suggestedCategoryId)
    }

    // ==========================================
    // 5. Entertainment
    // ==========================================

    @Test
    fun testEntertainmentInferenceNetflix() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Netflix.com", availableCategoryIds = allActiveCategoryIds))
        assertEquals(CategoryInferenceStatus.HIGH_CONFIDENCE, result.status)
        assertEquals("entertainment", result.suggestedCategoryId)
    }

    @Test
    fun testEntertainmentInferenceSpotify() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Spotify Subscription", availableCategoryIds = allActiveCategoryIds))
        assertEquals("entertainment", result.suggestedCategoryId)
    }

    @Test
    fun testEntertainmentInferenceBookMyShow() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "BookMyShow Movies", availableCategoryIds = allActiveCategoryIds))
        assertEquals("entertainment", result.suggestedCategoryId)
    }

    // ==========================================
    // 6. Healthcare
    // ==========================================

    @Test
    fun testHealthcareInferencePharmacy() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "MedPlus Pharmacy", availableCategoryIds = allActiveCategoryIds))
        assertEquals("healthcare", result.suggestedCategoryId)
    }

    @Test
    fun testHealthcareInferenceHospital() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Fortis Hospital", availableCategoryIds = allActiveCategoryIds))
        assertEquals("healthcare", result.suggestedCategoryId)
    }

    @Test
    fun testHealthcareInferenceApollo() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Apollo Pharmacy Ltd", availableCategoryIds = allActiveCategoryIds))
        assertEquals("healthcare", result.suggestedCategoryId)
    }

    // ==========================================
    // 7. Travel
    // ==========================================

    @Test
    fun testTravelInferenceMakeMyTrip() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "MakeMyTrip India", availableCategoryIds = allActiveCategoryIds))
        assertEquals("travel", result.suggestedCategoryId)
    }

    @Test
    fun testTravelInferenceHotel() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Marriott Hotel Stay", availableCategoryIds = allActiveCategoryIds))
        assertEquals("travel", result.suggestedCategoryId)
    }

    @Test
    fun testTravelInferenceFlight() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "IndiGo Flight Booking", availableCategoryIds = allActiveCategoryIds))
        assertEquals("travel", result.suggestedCategoryId)
    }

    // ==========================================
    // 8. Education
    // ==========================================

    @Test
    fun testEducationInferenceUdemy() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Udemy Courses", availableCategoryIds = allActiveCategoryIds))
        assertEquals("education", result.suggestedCategoryId)
    }

    @Test
    fun testEducationInferenceCoursera() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Coursera Subscription", availableCategoryIds = allActiveCategoryIds))
        assertEquals("education", result.suggestedCategoryId)
    }

    @Test
    fun testEducationInferenceUniversityFee() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Delhi University Tuition Fee", availableCategoryIds = allActiveCategoryIds))
        assertEquals("education", result.suggestedCategoryId)
    }

    // ==========================================
    // 9. Bills & Utilities
    // ==========================================

    @Test
    fun testBillsInferenceElectricity() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "BESCOM Electricity Bill", availableCategoryIds = allActiveCategoryIds))
        assertEquals("bills_utilities", result.suggestedCategoryId)
    }

    @Test
    fun testBillsInferenceBroadband() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Airtel Broadband Fiber", availableCategoryIds = allActiveCategoryIds))
        assertEquals("bills_utilities", result.suggestedCategoryId)
    }

    @Test
    fun testBillsInferenceMobileRecharge() {
        val result = engine.inferCategory(CategoryInferenceInput(merchant = "Jio Mobile Recharge", availableCategoryIds = allActiveCategoryIds))
        assertEquals("bills_utilities", result.suggestedCategoryId)
    }

    // ==========================================
    // 10. Income Protection
    // ==========================================

    @Test
    fun testIncomeInferenceExplicitSalaryCredit() {
        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Acme Corp Salary",
                transactionType = "Credit",
                availableCategoryIds = allActiveCategoryIds
            )
        )
        assertEquals(CategoryInferenceStatus.HIGH_CONFIDENCE, result.status)
        assertEquals("income", result.suggestedCategoryId)
    }

    @Test
    fun testIncomeInferenceExplicitPayrollCredit() {
        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Monthly Payroll Transfer",
                transactionType = "Credit",
                availableCategoryIds = allActiveCategoryIds
            )
        )
        assertEquals("income", result.suggestedCategoryId)
    }

    @Test
    fun testSalaryDebitDoesNotBecomeIncome() {
        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Salary Advance Payment",
                transactionType = "Debit", // Debit direction constraint violation
                availableCategoryIds = allActiveCategoryIds
            )
        )
        assertNotEquals("income", result.suggestedCategoryId)
    }

    @Test
    fun testGenericCreditDoesNotBlindlyBecomeIncome() {
        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Unknown Sender",
                transactionType = "Credit",
                availableCategoryIds = allActiveCategoryIds
            )
        )
        assertEquals(CategoryInferenceStatus.NO_MATCH, result.status)
        assertNull(result.suggestedCategoryId)
    }

    // ==========================================
    // 11. Cash Withdrawal Protection
    // ==========================================

    @Test
    fun testCashWithdrawalInferenceAtm() {
        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "HDFC Bank ATM Cash Withdrawal",
                transactionType = "Debit",
                availableCategoryIds = allActiveCategoryIds
            )
        )
        assertEquals(CategoryInferenceStatus.HIGH_CONFIDENCE, result.status)
        assertEquals("cash_withdrawal", result.suggestedCategoryId)
    }

    @Test
    fun testCashWithdrawalInferenceCashDispensed() {
        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Cash Dispensed at SBI ATM",
                availableCategoryIds = allActiveCategoryIds
            )
        )
        assertEquals("cash_withdrawal", result.suggestedCategoryId)
    }

    // ==========================================
    // 12. Transfer Protection & Keyword Precedence
    // ==========================================

    @Test
    fun testTransferProtectionOverMerchantCollision() {
        // "TRANSFER TO AMAZON" must NOT become Shopping merely because "amazon" appears!
        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Fund Transfer to Amazon Pay",
                rawText = "INR 1000 debited for Transfer to Amazon",
                availableCategoryIds = allActiveCategoryIds
            )
        )
        assertEquals("Transfer must take precedence over Shopping keyword collision", "transfer", result.suggestedCategoryId)
    }

    @Test
    fun testSelfTransferInference() {
        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Self Transfer to HDFC",
                availableCategoryIds = allActiveCategoryIds
            )
        )
        assertEquals("transfer", result.suggestedCategoryId)
    }

    @Test
    fun testAmazonSalaryCreditBecomesIncomeNotShopping() {
        // "AMAZON SALARY CREDIT" must become Income, not Shopping!
        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Amazon Development Center",
                rawText = "INR 85,000 credited towards Monthly Salary",
                transactionType = "Credit",
                availableCategoryIds = allActiveCategoryIds
            )
        )
        assertEquals("Income must take precedence over Shopping keyword collision on credit salary", "income", result.suggestedCategoryId)
    }

    @Test
    fun testUberEatsBecomesFoodDiningNotTransport() {
        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Uber Eats Order",
                availableCategoryIds = allActiveCategoryIds
            )
        )
        assertEquals("Uber Eats must resolve to food_dining, not transport", "food_dining", result.suggestedCategoryId)
    }

    // ==========================================
    // 13. Refund / Cashback / Reversal Safety
    // ==========================================

    @Test
    fun testRefundDoesNotBlindlyBecomeIncomeOrShopping() {
        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Amazon Refund Received",
                rawText = "INR 500 refund credited to your account",
                transactionType = "Credit",
                availableCategoryIds = allActiveCategoryIds
            )
        )
        assertNotEquals("Refund should not become Income", "income", result.suggestedCategoryId)
        assertNotEquals("Refund should not blindly become Shopping", "shopping", result.suggestedCategoryId)
    }

    @Test
    fun testCashbackDoesNotBlindlyBecomeShopping() {
        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Cred Cashback Received",
                rawText = "Cashback reward credited",
                transactionType = "Credit",
                availableCategoryIds = allActiveCategoryIds
            )
        )
        assertNotEquals("Cashback should not blindly become Shopping", "shopping", result.suggestedCategoryId)
    }

    // ==========================================
    // 14. Category Availability & Inactive Filtering
    // ==========================================

    @Test
    fun testArchivedCategoryIsNeverSuggested() {
        // Suppose "food_dining" is archived (omitted from availableCategoryIds)
        val activeWithoutFood = allActiveCategoryIds - "food_dining"

        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Swiggy",
                availableCategoryIds = activeWithoutFood
            )
        )
        assertNotEquals("food_dining", result.suggestedCategoryId)
        assertEquals(CategoryInferenceStatus.NO_MATCH, result.status)
    }

    // ==========================================
    // 15. User Override Protection
    // ==========================================

    @Test
    fun testUserAssignedCategoryIsProtected() {
        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Swiggy", // Normally matches food_dining
                existingCategoryId = "custom_gifts",
                existingCategorySource = CategorySource.USER_ASSIGNED,
                availableCategoryIds = allActiveCategoryIds + "custom_gifts"
            )
        )

        assertEquals(CategoryInferenceStatus.USER_ASSIGNED_PRESERVED, result.status)
        assertEquals("custom_gifts", result.suggestedCategoryId)
        assertNotEquals("food_dining", result.suggestedCategoryId)
    }

    // ==========================================
    // 16. Custom Categories
    // ==========================================

    @Test
    fun testCustomCategoryNotAutomaticallyInferredMerelyFromItsName() {
        // Without an explicit rule, user creating a category "Gaming" does not cause "Gaming Store" to auto-assign custom_gaming
        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Local Gaming Lounge",
                availableCategoryIds = allActiveCategoryIds + "custom_gaming"
            )
        )
        assertNotEquals("custom_gaming", result.suggestedCategoryId)
    }

    @Test
    fun testCustomRuleInEngine() {
        val customRule = CategoryInferenceRule(
            id = "rule_custom_gym",
            categoryId = "custom_fitness",
            matchingType = CategoryMatchingType.MERCHANT_TOKEN,
            pattern = "cult fit",
            priority = RulePriority.EXACT_MERCHANT
        )
        val customEngine = CategoryInferenceEngine(BuiltInInferenceRules.ALL_RULES + customRule)

        val result = customEngine.inferCategory(
            CategoryInferenceInput(
                merchant = "Cult Fit Gym",
                availableCategoryIds = allActiveCategoryIds + "custom_fitness"
            )
        )
        assertEquals("custom_fitness", result.suggestedCategoryId)
        assertEquals(CategoryInferenceStatus.HIGH_CONFIDENCE, result.status)
    }

    // ==========================================
    // 17. Determinism & Crash Safety
    // ==========================================

    @Test
    fun testDeterminismSameInputSameOutput() {
        val input = CategoryInferenceInput(merchant = "Zomato Bangalore", availableCategoryIds = allActiveCategoryIds)
        val res1 = engine.inferCategory(input)
        val res2 = engine.inferCategory(input)

        assertEquals(res1.status, res2.status)
        assertEquals(res1.suggestedCategoryId, res2.suggestedCategoryId)
        assertEquals(res1.confidenceScore, res2.confidenceScore, 0.001)
        assertEquals(res1.matchedRules, res2.matchedRules)
    }

    @Test
    fun testNullAndEmptyInputsDoNotCrash() {
        val res1 = engine.inferCategory(CategoryInferenceInput(merchant = null, rawText = null))
        assertEquals(CategoryInferenceStatus.NO_MATCH, res1.status)

        val res2 = engine.inferCategory(CategoryInferenceInput(merchant = "", rawText = "   "))
        assertEquals(CategoryInferenceStatus.NO_MATCH, res2.status)
    }

    @Test
    fun testPreviewTestInferenceHelper() {
        val res = engine.testInference(merchant = "Swiggy", transactionType = "Debit")
        assertEquals("food_dining", res.suggestedCategoryId)
        assertTrue(res.evidence.isNotEmpty())
        assertTrue(res.reason.isNotBlank())
    }
}
