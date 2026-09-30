package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.*
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Milestone 11: User-Defined Category Rules & Merchant Aliases JVM Unit Tests.
 *
 * Comprehensive coverage:
 * 1. User rule creation & validation (exact, contains, token, whitespace rejection, duplicate/conflict rejection)
 * 2. User rule inference & overriding built-in rules (e.g. Netflix -> Food & Dining)
 * 3. User rule conflict resolution (exact > contains, specificity, equal-strength CONFLICT)
 * 4. High-precedence semantic safety (transfer, cash withdrawal, income direction)
 * 5. User override preservation (USER_ASSIGNED remains untouchable)
 * 6. Category lifecycle safety (archived category excluded, deleted category disabled)
 * 7. Rule lifecycle (enable/disable, edit, delete, non-rewriting of history)
 * 8. Merchant alias creation, normalization, resolution, and chaining prevention
 * 9. Alias + Rule interactions & explainability evidence
 */
class CategoryRuleAndAliasTest {

    private lateinit var categoryDao: FakeTransactionCategoryDao
    private lateinit var ruleDao: FakeUserCategoryRuleDao
    private lateinit var aliasDao: FakeMerchantAliasDao
    private lateinit var expenseDao: FakeExpenseDao
    private lateinit var categoryManager: CategoryManager
    private lateinit var ruleManager: CategoryRuleManager
    private lateinit var aliasManager: MerchantAliasManager
    private lateinit var engine: CategoryInferenceEngine

    private val allActiveCategoryIds: Set<String>
        get() = runBlocking { categoryDao.getActive().map { it.id }.toSet() }

    @Before
    fun setUp() = runBlocking {
        categoryDao = FakeTransactionCategoryDao()
        ruleDao = FakeUserCategoryRuleDao()
        aliasDao = FakeMerchantAliasDao()
        expenseDao = FakeExpenseDao()

        categoryManager = CategoryManager(categoryDao, expenseDao, ruleDao = ruleDao)
        ruleManager = CategoryRuleManager(ruleDao, categoryDao)
        aliasManager = MerchantAliasManager(aliasDao)
        engine = CategoryInferenceEngine()

        // Seed 12 built-in categories
        categoryManager.ensureBuiltInCategoriesSeeded()
    }

    // ==================================================
    // 1. USER RULE CREATION & VALIDATION
    // ==================================================

    @Test
    fun testCreateExactMerchantRule() = runBlocking {
        val result = ruleManager.createRule(
            pattern = "ABC Coffee",
            categoryId = "food_dining",
            matchType = UserRuleMatchType.MERCHANT_EXACT
        )
        assertTrue(result.isSuccess)
        val rule = result.getOrThrow()
        assertEquals("ABC Coffee", rule.pattern)
        assertEquals("abc coffee", rule.normalizedPattern)
        assertEquals("food_dining", rule.categoryId)
        assertEquals(UserRuleMatchType.MERCHANT_EXACT, rule.matchType)
        assertTrue(rule.isEnabled)
    }

    @Test
    fun testCreateContainsRule() = runBlocking {
        val result = ruleManager.createRule(
            pattern = "Coffee",
            categoryId = "food_dining",
            matchType = UserRuleMatchType.MERCHANT_CONTAINS
        )
        assertTrue(result.isSuccess)
        assertEquals(UserRuleMatchType.MERCHANT_CONTAINS, result.getOrThrow().matchType)
    }

    @Test
    fun testCreateTokenRule() = runBlocking {
        val result = ruleManager.createRule(
            pattern = "Bakehouse",
            categoryId = "food_dining",
            matchType = UserRuleMatchType.MERCHANT_TOKEN
        )
        assertTrue(result.isSuccess)
        assertEquals(UserRuleMatchType.MERCHANT_TOKEN, result.getOrThrow().matchType)
    }

    @Test
    fun testRejectBlankPattern() = runBlocking {
        val res1 = ruleManager.createRule("   ", "food_dining")
        assertTrue(res1.isFailure)
        assertTrue(res1.exceptionOrNull()?.message?.contains("blank") == true)
    }

    @Test
    fun testRejectInvalidOrMissingCategory() = runBlocking {
        val res = ruleManager.createRule("Coffee Shop", "nonexistent_cat_999")
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.contains("does not exist") == true)
    }

    @Test
    fun testRejectArchivedCategoryTarget() = runBlocking {
        // Archive food_dining
        categoryManager.archiveCategory("food_dining")

        val res = ruleManager.createRule("Cafe Nero", "food_dining")
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.contains("archived") == true)
    }

    @Test
    fun testRejectDuplicateNormalizedRule() = runBlocking {
        ruleManager.createRule("Starbucks", "food_dining")

        // Equivalent whitespace/case variation
        val res = ruleManager.createRule("  starbucks  ", "food_dining")
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.contains("Duplicate") == true)
    }

    @Test
    fun testRejectConflictingRulePattern() = runBlocking {
        ruleManager.createRule("Starbucks", "food_dining")

        // Same pattern pointing to different category
        val res = ruleManager.createRule("starbucks", "shopping")
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.contains("Conflict") == true)
    }

    // ==================================================
    // 2. USER RULE INFERENCE & OVERRIDING BUILT-IN
    // ==================================================

    @Test
    fun testExactUserRuleOverridesBuiltInRule() = runBlocking {
        // Built-in rule maps Netflix -> Entertainment
        val builtInResult = engine.inferCategory(CategoryInferenceInput(merchant = "Netflix", availableCategoryIds = allActiveCategoryIds))
        assertEquals("entertainment", builtInResult.suggestedCategoryId)

        // User explicitly teaches: Netflix -> Food & Dining
        val rule = ruleManager.createRule("Netflix", "food_dining", UserRuleMatchType.MERCHANT_EXACT).getOrThrow()

        val userResult = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Netflix",
                availableCategoryIds = allActiveCategoryIds,
                userRules = listOf(rule)
            )
        )
        assertEquals(CategoryInferenceStatus.HIGH_CONFIDENCE, userResult.status)
        assertEquals("food_dining", userResult.suggestedCategoryId)
        assertEquals(rule.id, userResult.matchedUserRuleId)
        assertTrue(userResult.evidence.any { it.type == EvidenceType.USER_RULE })
    }

    @Test
    fun testContainsUserRuleMatches() = runBlocking {
        val rule = ruleManager.createRule("Bakery", "food_dining", UserRuleMatchType.MERCHANT_CONTAINS).getOrThrow()

        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Glen's Bakehouse & Bakery",
                availableCategoryIds = allActiveCategoryIds,
                userRules = listOf(rule)
            )
        )
        assertEquals("food_dining", result.suggestedCategoryId)
    }

    @Test
    fun testTokenUserRuleMatches() = runBlocking {
        val rule = ruleManager.createRule("Pharmacy", "healthcare", UserRuleMatchType.MERCHANT_TOKEN).getOrThrow()

        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "City Center Pharmacy Bangalore",
                availableCategoryIds = allActiveCategoryIds,
                userRules = listOf(rule)
            )
        )
        assertEquals("healthcare", result.suggestedCategoryId)
    }

    @Test
    fun testDisabledUserRuleDoesNotMatch() = runBlocking {
        val rule = ruleManager.createRule("Unknown Shop 123", "shopping").getOrThrow()
        ruleManager.setRuleEnabled(rule.id, false)
        val disabledRule = ruleDao.getById(rule.id)!!

        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Unknown Shop 123",
                availableCategoryIds = allActiveCategoryIds,
                userRules = listOf(disabledRule)
            )
        )
        assertEquals(CategoryInferenceStatus.NO_MATCH, result.status)
        assertNull(result.suggestedCategoryId)
    }

    @Test
    fun testCustomCategoryTargetRule() = runBlocking {
        // Create custom category
        val customCat = categoryManager.createCategory("Pet Care", "pets", "amber").getOrThrow()

        val rule = ruleManager.createRule("Heads Up For Tails", customCat.id).getOrThrow()

        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Heads Up For Tails",
                availableCategoryIds = allActiveCategoryIds,
                userRules = listOf(rule)
            )
        )
        assertEquals(customCat.id, result.suggestedCategoryId)
    }

    // ==================================================
    // 3. USER RULE CONFLICT RESOLUTION
    // ==================================================

    @Test
    fun testExactRuleBeatsContainsRule() = runBlocking {
        // Specific rule: "Starbucks Coffee" -> Food & Dining (Exact)
        val ruleExact = UserCategoryRule(
            id = "r_exact",
            pattern = "Starbucks Coffee",
            normalizedPattern = "starbucks coffee",
            categoryId = "food_dining",
            matchType = UserRuleMatchType.MERCHANT_EXACT
        )
        // Generic rule: "Coffee" -> Shopping (Contains)
        val ruleContains = UserCategoryRule(
            id = "r_contains",
            pattern = "Coffee",
            normalizedPattern = "coffee",
            categoryId = "shopping",
            matchType = UserRuleMatchType.MERCHANT_CONTAINS
        )

        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Starbucks Coffee",
                availableCategoryIds = allActiveCategoryIds,
                userRules = listOf(ruleExact, ruleContains)
            )
        )
        // Exact rule has higher base priority (1200 vs 1100), so Food & Dining wins cleanly
        assertEquals("food_dining", result.suggestedCategoryId)
    }

    @Test
    fun testEqualStrengthConflictingUserRulesYieldConflict() = runBlocking {
        // Two exact rules matching different categories
        val ruleA = UserCategoryRule(
            id = "r_a",
            pattern = "General Store",
            normalizedPattern = "general store",
            categoryId = "shopping",
            matchType = UserRuleMatchType.MERCHANT_EXACT,
            priority = 100
        )
        val ruleB = UserCategoryRule(
            id = "r_b",
            pattern = "General Store",
            normalizedPattern = "general store",
            categoryId = "food_dining",
            matchType = UserRuleMatchType.MERCHANT_EXACT,
            priority = 100
        )

        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "General Store",
                availableCategoryIds = allActiveCategoryIds,
                userRules = listOf(ruleA, ruleB)
            )
        )
        assertEquals(CategoryInferenceStatus.CONFLICT, result.status)
        assertNull(result.suggestedCategoryId)
    }

    // ==================================================
    // 4. HIGH-PRECEDENCE SEMANTIC SAFETY
    // ==================================================

    @Test
    fun testUserRuleCannotOverrideTransferProtection() = runBlocking {
        // User rule: Amazon -> Shopping
        val amazonRule = ruleManager.createRule("Amazon", "shopping", UserRuleMatchType.MERCHANT_TOKEN).getOrThrow()

        // Transaction is an explicit Transfer to Amazon
        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Transfer to Amazon",
                transactionType = "Debit",
                availableCategoryIds = allActiveCategoryIds,
                userRules = listOf(amazonRule)
            )
        )
        // Semantic transfer protection must win!
        assertEquals("transfer", result.suggestedCategoryId)
        assertNotEquals("shopping", result.suggestedCategoryId)
    }

    @Test
    fun testUserRuleCannotOverrideAtmCashWithdrawalProtection() = runBlocking {
        // User rule: ATM -> Shopping
        val atmRule = ruleManager.createRule("ATM", "shopping", UserRuleMatchType.MERCHANT_TOKEN).getOrThrow()

        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "ATM Cash Withdrawal HDFC",
                availableCategoryIds = allActiveCategoryIds,
                userRules = listOf(atmRule)
            )
        )
        // Cash withdrawal protection wins!
        assertEquals("cash_withdrawal", result.suggestedCategoryId)
    }

    @Test
    fun testSalaryCreditProtectionCannotBeOverridden() = runBlocking {
        val salaryRule = ruleManager.createRule("Salary", "shopping", UserRuleMatchType.MERCHANT_TOKEN).getOrThrow()

        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Company Salary Credit",
                transactionType = "Credit",
                availableCategoryIds = allActiveCategoryIds,
                userRules = listOf(salaryRule)
            )
        )
        assertEquals("income", result.suggestedCategoryId)
    }

    // ==================================================
    // 5. USER OVERRIDE PROTECTION
    // ==================================================

    @Test
    fun testUserAssignedCategoryRemainsUntouchedByUserRule() = runBlocking {
        val rule = ruleManager.createRule("Starbucks", "food_dining").getOrThrow()

        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Starbucks",
                existingCategoryId = "travel",
                existingCategorySource = CategorySource.USER_ASSIGNED,
                availableCategoryIds = allActiveCategoryIds,
                userRules = listOf(rule)
            )
        )
        assertEquals(CategoryInferenceStatus.USER_ASSIGNED_PRESERVED, result.status)
        assertEquals("travel", result.suggestedCategoryId)
        assertFalse(result.isAutoAssignable)
    }

    // ==================================================
    // 6. CATEGORY LIFECYCLE SAFETY
    // ==================================================

    @Test
    fun testArchivedTargetCategoryCannotBeInferred() = runBlocking {
        val customCat = categoryManager.createCategory("Subscriptions", "subscriptions", "purple").getOrThrow()
        val rule = ruleManager.createRule("SaaS Monthly", customCat.id).getOrThrow()

        // Archive the category
        categoryManager.archiveCategory(customCat.id)

        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "SaaS Monthly",
                availableCategoryIds = allActiveCategoryIds, // Excludes customCat
                userRules = listOf(rule)
            )
        )
        // Inactive category is filtered out
        assertNotEquals(customCat.id, result.suggestedCategoryId)
    }

    @Test
    fun testCategoryDeletionDisablesRules() = runBlocking {
        val customCat = categoryManager.createCategory("Clubs", "star", "blue").getOrThrow()
        val rule = ruleManager.createRule("Tennis Club", customCat.id).getOrThrow()
        assertTrue(ruleDao.getById(rule.id)!!.isEnabled)

        // Delete custom category unlinking transactions
        categoryManager.deleteCategory(customCat.id, reassignToCategoryId = null)

        val updatedRule = ruleDao.getById(rule.id)!!
        assertFalse("Rule referencing deleted category must be disabled", updatedRule.isEnabled)
    }

    // ==================================================
    // 7. RULE LIFECYCLE
    // ==================================================

    @Test
    fun testRuleEnableDisableLifecycle() = runBlocking {
        val rule = ruleManager.createRule("Local Grocer", "food_dining").getOrThrow()
        assertTrue(ruleDao.getById(rule.id)!!.isEnabled)

        ruleManager.setRuleEnabled(rule.id, false)
        assertFalse(ruleDao.getById(rule.id)!!.isEnabled)

        ruleManager.setRuleEnabled(rule.id, true)
        assertTrue(ruleDao.getById(rule.id)!!.isEnabled)
    }

    @Test
    fun testRuleEditLifecycle() = runBlocking {
        val rule = ruleManager.createRule("Old Name", "food_dining").getOrThrow()

        val updateRes = ruleManager.updateRule(
            id = rule.id,
            pattern = "New Name",
            categoryId = "shopping",
            matchType = UserRuleMatchType.MERCHANT_EXACT,
            priority = 150,
            isEnabled = true,
            name = "My Rule"
        )
        assertTrue(updateRes.isSuccess)
        val updated = ruleDao.getById(rule.id)!!
        assertEquals("New Name", updated.pattern)
        assertEquals("new name", updated.normalizedPattern)
        assertEquals("shopping", updated.categoryId)
        assertEquals(150, updated.priority)
    }

    @Test
    fun testRuleDeletionLifecycle() = runBlocking {
        val rule = ruleManager.createRule("Temporary Shop", "shopping").getOrThrow()
        assertNotNull(ruleDao.getById(rule.id))

        ruleManager.deleteRule(rule.id)
        assertNull(ruleDao.getById(rule.id))
    }

    // ==================================================
    // 8. MERCHANT ALIASES
    // ==================================================

    @Test
    fun testCreateAndResolveAlias() = runBlocking {
        val result = aliasManager.createAlias(
            alias = "AMZN Mktp",
            canonicalMerchant = "Amazon"
        )
        assertTrue(result.isSuccess)
        val alias = result.getOrThrow()
        assertEquals("AMZN Mktp", alias.alias)
        assertEquals("Amazon", alias.canonicalMerchant)
        assertEquals("amzn mktp", alias.normalizedAlias)

        val (resolved, matchedAlias) = aliasManager.resolveAlias("AMZN Mktp", listOf(alias))
        assertEquals("Amazon", resolved)
        assertNotNull(matchedAlias)
    }

    @Test
    fun testAliasPlusBuiltInRuleInference() = runBlocking {
        val alias = aliasManager.createAlias("AMZN Mktp", "Amazon").getOrThrow()

        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "AMZN Mktp",
                availableCategoryIds = allActiveCategoryIds,
                merchantAliases = listOf(alias)
            )
        )
        // Alias resolved to Amazon -> built-in rule inferred Shopping
        assertEquals("shopping", result.suggestedCategoryId)
        assertEquals("Amazon", result.resolvedMerchant)
        assertTrue(result.evidence.any { it.type == EvidenceType.MERCHANT_ALIAS })
    }

    @Test
    fun testAliasPlusUserRuleInference() = runBlocking {
        // User rule: Amazon -> Other
        val userRule = ruleManager.createRule("Amazon", "other").getOrThrow()
        val alias = aliasManager.createAlias("AMZN India Store", "Amazon").getOrThrow()

        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "AMZN India Store",
                availableCategoryIds = allActiveCategoryIds,
                userRules = listOf(userRule),
                merchantAliases = listOf(alias)
            )
        )
        assertEquals("other", result.suggestedCategoryId)
        assertEquals("Amazon", result.resolvedMerchant)
        assertTrue(result.evidence.any { it.type == EvidenceType.MERCHANT_ALIAS })
        assertTrue(result.evidence.any { it.type == EvidenceType.USER_RULE })
    }

    @Test
    fun testRejectAliasSelfReference() = runBlocking {
        val res = aliasManager.createAlias("Amazon", "Amazon")
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.contains("itself") == true)
    }

    @Test
    fun testRejectAliasChaining() = runBlocking {
        // A -> B
        aliasManager.createAlias("AliasOne", "CanonicalMid")

        // Try B -> C (CanonicalMid -> RealEnd)
        val res = aliasManager.createAlias("CanonicalMid", "RealEnd")
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.contains("already configured") == true ||
            res.exceptionOrNull()?.message?.contains("chain") == true)
    }

    @Test
    fun testRejectDuplicateAlias() = runBlocking {
        aliasManager.createAlias("AMZN", "Amazon")

        val res = aliasManager.createAlias("amzn", "Amazon")
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.contains("Duplicate") == true)
    }

    @Test
    fun testRejectConflictingAlias() = runBlocking {
        aliasManager.createAlias("ABC PAY", "Amazon")

        val res = aliasManager.createAlias("ABC PAY", "Flipkart")
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.contains("Conflict") == true)
    }

    @Test
    fun testDisabledAliasIsIgnored() = runBlocking {
        val alias = aliasManager.createAlias("UBR TRIP", "Uber").getOrThrow()
        aliasManager.setAliasEnabled(alias.id, false)
        val disabled = aliasDao.getById(alias.id)!!

        val (resolved, matched) = aliasManager.resolveAlias("UBR TRIP", listOf(disabled))
        assertEquals("UBR TRIP", resolved)
        assertNull(matched)
    }

    @Test
    fun testTransferSafetySurvivesAliasResolution() = runBlocking {
        val alias = aliasManager.createAlias("AMZN", "Amazon").getOrThrow()

        val result = engine.inferCategory(
            CategoryInferenceInput(
                merchant = "Transfer to AMZN",
                availableCategoryIds = allActiveCategoryIds,
                merchantAliases = listOf(alias)
            )
        )
        // Transfer protection wins!
        assertEquals("transfer", result.suggestedCategoryId)
    }

    // ==================================================
    // 9. PERSISTENCE IN INTEGRATION
    // ==================================================

    @Test
    fun testTransactionPersistenceManagerUsesUserRules() = runBlocking {
        val userRule = ruleManager.createRule("My Custom Coffee", "food_dining").getOrThrow()

        val candidate = StructuredTransactionCandidate(
            sourceNotificationKey = "notif_custom_1",
            packageName = "com.google.android.apps.nbu.paisa.user",
            postTime = System.currentTimeMillis(),
            merchant = "My Custom Coffee",
            amount = 120.0,
            direction = TransactionDirection.DEBIT
        )

        val expense = TransactionPersistenceManager.mapToExpense(
            candidate = candidate,
            isPending = false,
            userRules = listOf(userRule)
        )
        assertEquals("food_dining", expense.categoryId)
        assertEquals(CategorySource.INFERRED, expense.categorySource)
    }
}

// ==================================================
// IN-MEMORY FAKES FOR FOCUSED TESTING
// ==================================================

class FakeUserCategoryRuleDao : UserCategoryRuleDao {
    val rules = mutableListOf<UserCategoryRule>()

    override suspend fun insert(rule: UserCategoryRule): Long {
        rules.removeAll { it.id == rule.id }
        rules.add(rule)
        return rules.size.toLong()
    }

    override suspend fun update(rule: UserCategoryRule) {
        val idx = rules.indexOfFirst { it.id == rule.id }
        if (idx >= 0) rules[idx] = rule
    }

    override suspend fun delete(rule: UserCategoryRule) {
        rules.removeAll { it.id == rule.id }
    }

    override suspend fun deleteById(id: String) {
        rules.removeAll { it.id == id }
    }

    override suspend fun getById(id: String): UserCategoryRule? = rules.find { it.id == id }

    override suspend fun getAll(): List<UserCategoryRule> = rules.toList()

    override fun getAllFlow(): Flow<List<UserCategoryRule>> = flowOf(rules.toList())

    override suspend fun getActiveRules(): List<UserCategoryRule> = rules.filter { it.isEnabled }

    override fun getActiveRulesFlow(): Flow<List<UserCategoryRule>> = flowOf(rules.filter { it.isEnabled })

    override suspend fun getRulesForCategory(categoryId: String): List<UserCategoryRule> =
        rules.filter { it.categoryId == categoryId }

    override suspend fun disableRulesForCategory(categoryId: String, updatedAt: Long) {
        for (i in rules.indices) {
            if (rules[i].categoryId == categoryId) {
                rules[i] = rules[i].copy(isEnabled = false, updatedAt = updatedAt)
            }
        }
    }

    override suspend fun count(): Int = rules.size

    override suspend fun insertAll(rules: List<UserCategoryRule>): List<Long> {
        return rules.map { insert(it) }
    }

    override suspend fun deleteAll(): Int {
        val count = rules.size
        rules.clear()
        return count
    }
}

class FakeMerchantAliasDao : MerchantAliasDao {
    val aliases = mutableListOf<MerchantAlias>()

    override suspend fun insert(alias: MerchantAlias): Long {
        aliases.removeAll { it.id == alias.id }
        aliases.add(alias)
        return aliases.size.toLong()
    }

    override suspend fun update(alias: MerchantAlias) {
        val idx = aliases.indexOfFirst { it.id == alias.id }
        if (idx >= 0) aliases[idx] = alias
    }

    override suspend fun delete(alias: MerchantAlias) {
        aliases.removeAll { it.id == alias.id }
    }

    override suspend fun deleteById(id: String) {
        aliases.removeAll { it.id == id }
    }

    override suspend fun getById(id: String): MerchantAlias? = aliases.find { it.id == id }

    override suspend fun getAll(): List<MerchantAlias> = aliases.toList()

    override fun getAllFlow(): Flow<List<MerchantAlias>> = flowOf(aliases.toList())

    override suspend fun getActiveAliases(): List<MerchantAlias> = aliases.filter { it.isEnabled }

    override fun getActiveAliasesFlow(): Flow<List<MerchantAlias>> = flowOf(aliases.filter { it.isEnabled })

    override suspend fun getByNormalizedAlias(normalizedAlias: String): MerchantAlias? =
        aliases.find { it.normalizedAlias == normalizedAlias }

    override suspend fun count(): Int = aliases.size

    override suspend fun insertAll(aliases: List<MerchantAlias>): List<Long> {
        return aliases.map { insert(it) }
    }

    override suspend fun deleteAll(): Int {
        val count = aliases.size
        aliases.clear()
        return count
    }
}
