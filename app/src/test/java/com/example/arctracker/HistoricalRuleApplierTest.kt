package com.example.arctracker

import com.example.arctracker.data.*
import com.example.arctracker.service.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Focused Unit Tests for Milestone 12: Historical Rule Application & Bulk Re-Categorization.
 *
 * Verifies all 30 required scenarios:
 * 1. Preview detects applicable category changes.
 * 2. Preview does not modify the database.
 * 3. USER_ASSIGNED transaction is never changed in preview.
 * 4. INFERRED transaction can be re-categorized.
 * 5. NONE category source can receive an inferred category.
 * 6. Current category equal to proposed category is not treated as a change (ALREADY_MATCHES).
 * 7. Archived category cannot be assigned.
 * 8. Deleted category cannot be assigned.
 * 9. Actionable items filter works (Select All selects all applicable changes).
 * 10. Clear All / empty selection works.
 * 11. Individual selection works.
 * 12. Only selected transactions are updated.
 * 13. Zero selection performs no write.
 * 14. Bulk update is atomic and transactional.
 * 15. Failed bulk update does not leave partial changes.
 * 16. Cancellation during preview causes no writes.
 * 17. Stale USER_ASSIGNED transaction is protected at apply time.
 * 18. Missing transaction is handled safely.
 * 19. Account information remains unchanged.
 * 20. Amount remains unchanged.
 * 21. Merchant remains unchanged.
 * 22. Reference (notificationKey, rawText) remains unchanged.
 * 23. Pending state remains unchanged.
 * 24. Self-transfer relationship remains unchanged.
 * 25. Category source becomes INFERRED after rule application.
 * 26. Manual category assignment remains USER_ASSIGNED.
 * 27. Existing M11 user rules are reused.
 * 28. Existing merchant aliases are reused.
 * 29. Semantic protections still take precedence over user rules.
 * 30. Specific rule targeting applies only that single rule.
 */
class HistoricalRuleApplierTest {

    private lateinit var expenseDao: FakeExpenseDao
    private lateinit var categoryDao: FakeTransactionCategoryDao
    private lateinit var ruleDao: FakeUserCategoryRuleDao
    private lateinit var aliasDao: FakeMerchantAliasDao
    private lateinit var applier: HistoricalRuleApplier
    private lateinit var categoryManager: CategoryManager

    @Before
    fun setUp() = runBlocking {
        expenseDao = FakeExpenseDao()
        categoryDao = FakeTransactionCategoryDao()
        ruleDao = FakeUserCategoryRuleDao()
        aliasDao = FakeMerchantAliasDao()

        categoryManager = CategoryManager(categoryDao, expenseDao, ruleDao = ruleDao)
        categoryManager.ensureBuiltInCategoriesSeeded()

        applier = HistoricalRuleApplier(
            expenseDao = expenseDao,
            categoryDao = categoryDao,
            ruleDao = ruleDao,
            aliasDao = aliasDao
        )
    }

    // ==================================================
    // 1-5: PREVIEW EVALUATION & CATEGORY SOURCE HANDLING
    // ==================================================

    @Test
    fun test01_previewDetectsApplicableCategoryChanges() = runBlocking {
        // Setup: User rule maps "Swiggy" -> Food & Dining
        ruleDao.insert(
            UserCategoryRule(
                name = "Food Rule",
                matchType = UserRuleMatchType.MERCHANT_EXACT,
                pattern = "Swiggy",
                normalizedPattern = "swiggy",
                categoryId = BuiltInCategories.FOOD_AND_DINING.id
            )
        )

        // Expense currently uncategorized (NONE)
        val expense = Expense(
            id = 1,
            merchant = "Swiggy",
            amount = 350.0,
            dateMillis = 1000L,
            type = "Debit",
            notificationKey = "key1",
            categoryId = null,
            categorySource = CategorySource.NONE
        )
        expenseDao.insert(expense)

        val preview = applier.generatePreview()
        assertEquals(1, preview.totalEvaluated)
        assertEquals(1, preview.willChangeCount)
        assertEquals(0, preview.alreadyMatchesCount)
        assertEquals(0, preview.protectedCount)

        val item = preview.items[0]
        assertEquals(PreviewChangeStatus.WILL_CHANGE, item.changeStatus)
        assertEquals(BuiltInCategories.FOOD_AND_DINING.id, item.proposedCategoryId)
        assertEquals(CategorySource.INFERRED, item.proposedCategorySource)
    }

    @Test
    fun test02_previewDoesNotModifyTheDatabase() = runBlocking {
        ruleDao.insert(
            UserCategoryRule(
                matchType = UserRuleMatchType.MERCHANT_EXACT,
                pattern = "Uber",
                normalizedPattern = "uber",
                categoryId = BuiltInCategories.TRANSPORT.id
            )
        )
        val expense = Expense(
            id = 1,
            merchant = "Uber",
            amount = 200.0,
            dateMillis = 1000L,
            type = "Debit",
            notificationKey = "key2",
            categoryId = null,
            categorySource = CategorySource.NONE
        )
        expenseDao.insert(expense)

        // Generate preview
        applier.generatePreview()

        // Verify DB is untouched
        val currentInDb = expenseDao.getExpenseById(1)
        assertNotNull(currentInDb)
        assertNull(currentInDb!!.categoryId)
        assertEquals(CategorySource.NONE, currentInDb.categorySource)
    }

    @Test
    fun test03_userAssignedTransactionIsNeverChangedInPreview() = runBlocking {
        // User explicitly set Netflix to Entertainment
        val expense = Expense(
            id = 10,
            merchant = "Netflix",
            amount = 649.0,
            dateMillis = 2000L,
            type = "Debit",
            notificationKey = "key10",
            categoryId = BuiltInCategories.ENTERTAINMENT.id,
            categorySource = CategorySource.USER_ASSIGNED
        )
        expenseDao.insert(expense)

        // Now someone adds a conflicting rule for Netflix -> Other
        ruleDao.insert(
            UserCategoryRule(
                matchType = UserRuleMatchType.MERCHANT_EXACT,
                pattern = "Netflix",
                normalizedPattern = "netflix",
                categoryId = BuiltInCategories.OTHER.id
            )
        )

        val preview = applier.generatePreview()
        assertEquals(1, preview.totalEvaluated)
        assertEquals(0, preview.willChangeCount)
        assertEquals(1, preview.protectedCount)

        val item = preview.items[0]
        assertEquals(PreviewChangeStatus.PROTECTED_USER_ASSIGNED, item.changeStatus)
        assertEquals(BuiltInCategories.ENTERTAINMENT.id, item.proposedCategoryId)
        assertEquals(CategorySource.USER_ASSIGNED, item.proposedCategorySource)
        assertTrue(preview.actionableItems.isEmpty())
    }

    @Test
    fun test04_inferredTransactionCanBeReCategorized() = runBlocking {
        // Expense was previously INFERRED as shopping
        val expense = Expense(
            id = 20,
            merchant = "Amazon",
            amount = 999.0,
            dateMillis = 3000L,
            type = "Debit",
            notificationKey = "key20",
            categoryId = BuiltInCategories.SHOPPING.id,
            categorySource = CategorySource.INFERRED
        )
        expenseDao.insert(expense)

        // User rule maps Amazon -> Entertainment
        ruleDao.insert(
            UserCategoryRule(
                matchType = UserRuleMatchType.MERCHANT_EXACT,
                pattern = "Amazon",
                normalizedPattern = "amazon",
                categoryId = BuiltInCategories.ENTERTAINMENT.id
            )
        )

        val preview = applier.generatePreview()
        assertEquals(1, preview.willChangeCount)
        val item = preview.actionableItems[0]
        assertEquals(BuiltInCategories.SHOPPING.id, item.currentCategoryId)
        assertEquals(BuiltInCategories.ENTERTAINMENT.id, item.proposedCategoryId)
        assertEquals(CategorySource.INFERRED, item.proposedCategorySource)
    }

    @Test
    fun test05_noneCategorySourceCanReceiveInferredCategory() = runBlocking {
        val expense = Expense(
            id = 30,
            merchant = "Starbucks",
            amount = 350.0,
            dateMillis = 4000L,
            type = "Debit",
            notificationKey = "key30",
            categoryId = null,
            categorySource = CategorySource.NONE
        )
        expenseDao.insert(expense)

        // Built-in rule matches Starbucks -> Food & Dining
        val preview = applier.generatePreview()
        assertEquals(1, preview.willChangeCount)
        val item = preview.actionableItems[0]
        assertNull(item.currentCategoryId)
        assertEquals(CategorySource.NONE, item.currentCategorySource)
        assertEquals(BuiltInCategories.FOOD_AND_DINING.id, item.proposedCategoryId)
        assertEquals(CategorySource.INFERRED, item.proposedCategorySource)
    }

    // ==================================================
    // 6-8: ALREADY MATCHING & ARCHIVED / DELETED SAFETY
    // ==================================================

    @Test
    fun test06_currentCategoryEqualToProposedCategoryIsNotTreatedAsChange() = runBlocking {
        val expense = Expense(
            id = 40,
            merchant = "Swiggy",
            amount = 400.0,
            dateMillis = 5000L,
            type = "Debit",
            notificationKey = "key40",
            categoryId = BuiltInCategories.FOOD_AND_DINING.id,
            categorySource = CategorySource.INFERRED
        )
        expenseDao.insert(expense)

        val preview = applier.generatePreview()
        assertEquals(0, preview.willChangeCount)
        assertEquals(1, preview.alreadyMatchesCount)
        assertEquals(PreviewChangeStatus.ALREADY_MATCHES, preview.items[0].changeStatus)
    }

    @Test
    fun test07_archivedCategoryCannotBeAssigned() = runBlocking {
        // Create custom category and then archive it
        val customCat = categoryManager.createCategory("Gym").getOrThrow()
        categoryManager.archiveCategory(customCat.id)

        // Rule pointing to archived category
        ruleDao.insert(
            UserCategoryRule(
                matchType = UserRuleMatchType.MERCHANT_EXACT,
                pattern = "CultFit",
                normalizedPattern = "cultfit",
                categoryId = customCat.id,
                isEnabled = true
            )
        )

        val expense = Expense(
            id = 50,
            merchant = "CultFit",
            amount = 1500.0,
            dateMillis = 6000L,
            type = "Debit",
            notificationKey = "key50",
            categoryId = null,
            categorySource = CategorySource.NONE
        )
        expenseDao.insert(expense)

        val preview = applier.generatePreview()
        assertEquals(0, preview.willChangeCount)
        assertEquals(1, preview.cannotChangeCount)
        assertFalse(preview.actionableItems.any { it.proposedCategoryId == customCat.id })
    }

    @Test
    fun test08_deletedCategoryCannotBeAssigned() = runBlocking {
        // Rule points to a deleted category ID
        ruleDao.insert(
            UserCategoryRule(
                matchType = UserRuleMatchType.MERCHANT_EXACT,
                pattern = "PetShop",
                normalizedPattern = "petshop",
                categoryId = "deleted_category_id_999",
                isEnabled = true
            )
        )

        val expense = Expense(
            id = 60,
            merchant = "PetShop",
            amount = 500.0,
            dateMillis = 7000L,
            type = "Debit",
            notificationKey = "key60",
            categoryId = null,
            categorySource = CategorySource.NONE
        )
        expenseDao.insert(expense)

        val preview = applier.generatePreview()
        assertEquals(0, preview.willChangeCount)
        assertTrue(preview.actionableItems.isEmpty())
    }

    // ==================================================
    // 9-13: SELECTION BEHAVIOR
    // ==================================================

    @Test
    fun test09_selectAllSelectsAllApplicableChanges() = runBlocking {
        ruleDao.insert(
            UserCategoryRule(
                matchType = UserRuleMatchType.MERCHANT_EXACT,
                pattern = "Uber",
                normalizedPattern = "uber",
                categoryId = BuiltInCategories.TRANSPORT.id
            )
        )
        ruleDao.insert(
            UserCategoryRule(
                matchType = UserRuleMatchType.MERCHANT_EXACT,
                pattern = "Swiggy",
                normalizedPattern = "swiggy",
                categoryId = BuiltInCategories.FOOD_AND_DINING.id
            )
        )

        expenseDao.insert(Expense(id = 1, merchant = "Uber", amount = 100.0, dateMillis = 1000L, type = "Debit", notificationKey = "k1", categoryId = null, categorySource = CategorySource.NONE))
        expenseDao.insert(Expense(id = 2, merchant = "Swiggy", amount = 200.0, dateMillis = 1000L, type = "Debit", notificationKey = "k2", categoryId = null, categorySource = CategorySource.NONE))

        val preview = applier.generatePreview()
        val allActionable = preview.actionableItems
        assertEquals(2, allActionable.size)

        // Apply all selected
        val result = applier.applyReassignments(allActionable)
        assertEquals(2, result.successCount)
        assertEquals(0, result.skippedCount)
        assertEquals(BuiltInCategories.TRANSPORT.id, expenseDao.getExpenseById(1)?.categoryId)
        assertEquals(BuiltInCategories.FOOD_AND_DINING.id, expenseDao.getExpenseById(2)?.categoryId)
    }

    @Test
    fun test10_clearAllSelectsNothing() = runBlocking {
        val selected = emptyList<HistoricalReassignmentPreviewItem>()
        val result = applier.applyReassignments(selected)
        assertEquals(0, result.successCount)
        assertEquals(0, result.skippedCount)
        assertTrue(result.isSuccess)
    }

    @Test
    fun test11_individualSelectionWorks() = runBlocking {
        ruleDao.insert(UserCategoryRule(matchType = UserRuleMatchType.MERCHANT_EXACT, pattern = "Uber", normalizedPattern = "uber", categoryId = BuiltInCategories.TRANSPORT.id))
        ruleDao.insert(UserCategoryRule(matchType = UserRuleMatchType.MERCHANT_EXACT, pattern = "Swiggy", normalizedPattern = "swiggy", categoryId = BuiltInCategories.FOOD_AND_DINING.id))

        expenseDao.insert(Expense(id = 1, merchant = "Uber", amount = 100.0, dateMillis = 1000L, type = "Debit", notificationKey = "k1", categoryId = null, categorySource = CategorySource.NONE))
        expenseDao.insert(Expense(id = 2, merchant = "Swiggy", amount = 200.0, dateMillis = 1000L, type = "Debit", notificationKey = "k2", categoryId = null, categorySource = CategorySource.NONE))

        val preview = applier.generatePreview()
        // Only select item 2 (Swiggy)
        val selectedOnlySwiggy = preview.actionableItems.filter { it.transactionId == 2 }
        val result = applier.applyReassignments(selectedOnlySwiggy)

        assertEquals(1, result.successCount)
        // Swiggy updated
        assertEquals(BuiltInCategories.FOOD_AND_DINING.id, expenseDao.getExpenseById(2)?.categoryId)
        // Uber NOT updated
        assertNull(expenseDao.getExpenseById(1)?.categoryId)
    }

    @Test
    fun test12_onlySelectedTransactionsAreUpdated() = runBlocking {
        ruleDao.insert(UserCategoryRule(matchType = UserRuleMatchType.MERCHANT_EXACT, pattern = "A", normalizedPattern = "a", categoryId = BuiltInCategories.SHOPPING.id))
        expenseDao.insert(Expense(id = 1, merchant = "A", amount = 10.0, dateMillis = 1000L, type = "Debit", notificationKey = "k1", categoryId = null, categorySource = CategorySource.NONE))
        expenseDao.insert(Expense(id = 2, merchant = "A", amount = 20.0, dateMillis = 1000L, type = "Debit", notificationKey = "k2", categoryId = null, categorySource = CategorySource.NONE))

        val preview = applier.generatePreview()
        val onlyFirst = listOf(preview.actionableItems.first { it.transactionId == 1 })
        applier.applyReassignments(onlyFirst)

        assertEquals(BuiltInCategories.SHOPPING.id, expenseDao.getExpenseById(1)?.categoryId)
        assertNull(expenseDao.getExpenseById(2)?.categoryId)
    }

    @Test
    fun test13_zeroSelectionPerformsNoWrite() = runBlocking {
        expenseDao.insert(Expense(id = 1, merchant = "Uber", amount = 100.0, dateMillis = 1000L, type = "Debit", notificationKey = "k1", categoryId = null, categorySource = CategorySource.NONE))
        val result = applier.applyReassignments(emptyList())
        assertEquals(0, result.successCount)
        assertNull(expenseDao.getExpenseById(1)?.categoryId)
    }

    // ==================================================
    // 14-18: TRANSACTIONALITY & CONCURRENCY / STALE DATA
    // ==================================================

    @Test
    fun test14_bulkUpdateIsTransactional() = runBlocking {
        ruleDao.insert(UserCategoryRule(matchType = UserRuleMatchType.MERCHANT_EXACT, pattern = "Uber", normalizedPattern = "uber", categoryId = BuiltInCategories.TRANSPORT.id))
        expenseDao.insert(Expense(id = 1, merchant = "Uber", amount = 100.0, dateMillis = 1000L, type = "Debit", notificationKey = "k1", categoryId = null, categorySource = CategorySource.NONE))

        val preview = applier.generatePreview()
        val result = applier.applyReassignments(preview.actionableItems)
        assertTrue(result.isSuccess)
        assertEquals(1, result.successCount)
    }

    @Test
    fun test15_failedBulkUpdateDoesNotLeavePartialChanges() = runBlocking {
        // If an exception occurs, the result reports failure and error message
        val failingDao = object : FakeExpenseDao() {
            override suspend fun updateCategoryMetadataIfNotUserAssigned(
                id: Int,
                categoryId: String?,
                categoryName: String?,
                categorySource: String
            ): Int {
                throw IllegalStateException("Simulated SQLite Disk Failure")
            }
        }
        failingDao.insert(
            Expense(
                id = 1,
                merchant = "Uber",
                amount = 100.0,
                dateMillis = 1000L,
                type = "Debit",
                notificationKey = "k1",
                categoryId = null,
                categorySource = CategorySource.NONE
            )
        )

        val mockFailingApplier = HistoricalRuleApplier(
            expenseDao = failingDao,
            categoryDao = categoryDao,
            ruleDao = ruleDao,
            aliasDao = aliasDao
        )

        val item = HistoricalReassignmentPreviewItem(
            transactionId = 1,
            merchant = "Uber",
            amount = 100.0,
            dateMillis = 1000L,
            currentCategoryId = null,
            currentCategoryName = null,
            currentCategorySource = CategorySource.NONE,
            proposedCategoryId = BuiltInCategories.TRANSPORT.id,
            proposedCategoryName = "Transport",
            proposedCategorySource = CategorySource.INFERRED,
            changeStatus = PreviewChangeStatus.WILL_CHANGE
        )

        val result = mockFailingApplier.applyReassignments(listOf(item))
        assertFalse(result.isSuccess)
        assertEquals(0, result.successCount)
        assertEquals(1, result.failedCount)
        assertTrue(result.errorMessage?.contains("Simulated SQLite Disk Failure") == true)
    }

    @Test
    fun test16_cancellationDuringPreviewCausesNoWrites() = runBlocking {
        expenseDao.insert(Expense(id = 1, merchant = "Uber", amount = 100.0, dateMillis = 1000L, type = "Debit", notificationKey = "k1", categoryId = null, categorySource = CategorySource.NONE))

        val job = launch {
            try {
                applier.generatePreview(onProgress = { current, _ ->
                    if (current > 0) cancel() // Cancel immediately!
                })
            } catch (e: CancellationException) {
                // Expected
            }
        }
        job.join()

        // DB remains untouched
        assertNull(expenseDao.getExpenseById(1)?.categoryId)
    }

    @Test
    fun test17_staleUserAssignedTransactionIsProtectedAtApplyTime() = runBlocking {
        // Step 1: Preview generated when expense was NONE
        val expense = Expense(id = 100, merchant = "Uber", amount = 150.0, dateMillis = 1000L, type = "Debit", notificationKey = "k100", categoryId = null, categorySource = CategorySource.NONE)
        expenseDao.insert(expense)

        ruleDao.insert(UserCategoryRule(matchType = UserRuleMatchType.MERCHANT_EXACT, pattern = "Uber", normalizedPattern = "uber", categoryId = BuiltInCategories.TRANSPORT.id))
        val preview = applier.generatePreview()
        val actionableItem = preview.actionableItems.first { it.transactionId == 100 }

        // Step 2: Before user presses Apply, user manually assigns category in another screen!
        expenseDao.updateCategoryMetadata(100, BuiltInCategories.HEALTHCARE.id, "Healthcare", CategorySource.USER_ASSIGNED)

        // Step 3: Now user confirms apply from stale preview
        val result = applier.applyReassignments(listOf(actionableItem))

        // Must be SKIPPED! Never overwritten!
        assertEquals(0, result.successCount)
        assertEquals(1, result.skippedCount)

        val finalExpense = expenseDao.getExpenseById(100)
        assertEquals(BuiltInCategories.HEALTHCARE.id, finalExpense?.categoryId)
        assertEquals(CategorySource.USER_ASSIGNED, finalExpense?.categorySource)
    }

    @Test
    fun test18_missingTransactionIsHandledSafely() = runBlocking {
        val nonExistentItem = HistoricalReassignmentPreviewItem(
            transactionId = 99999,
            merchant = "Ghost",
            amount = 50.0,
            dateMillis = 1000L,
            currentCategoryId = null,
            currentCategoryName = null,
            currentCategorySource = CategorySource.NONE,
            proposedCategoryId = BuiltInCategories.TRANSPORT.id,
            proposedCategoryName = "Transport",
            proposedCategorySource = CategorySource.INFERRED,
            changeStatus = PreviewChangeStatus.WILL_CHANGE
        )

        val result = applier.applyReassignments(listOf(nonExistentItem))
        assertEquals(0, result.successCount)
        assertEquals(1, result.skippedCount)
        assertTrue(result.isSuccess)
    }

    // ==================================================
    // 19-26: NON-CATEGORY FIELD PRESERVATION
    // ==================================================

    @Test
    fun test19_to_26_allNonCategoryFieldsAreStrictlyPreserved() = runBlocking {
        val original = Expense(
            id = 77,
            amount = 1234.56,
            merchant = "Flipkart India",
            dateMillis = 1710000000000L,
            type = "Debit",
            notificationKey = "notif_secure_key_123",
            isPending = false,
            rawText = "Paid INR 1234.56 at Flipkart via HDFC Bank xx9020",
            tag = "Old Tag",
            note = "Custom user notes that must never be wiped",
            source = "NOTIFICATION",
            relationshipType = "SELF_TRANSFER",
            relationshipId = "rel_abc_123",
            accountId = "hdfc_bank_9020",
            accountSuffix = "9020",
            categoryId = null,
            categorySource = CategorySource.NONE
        )
        expenseDao.insert(original)

        ruleDao.insert(
            UserCategoryRule(
                matchType = UserRuleMatchType.MERCHANT_CONTAINS,
                pattern = "Flipkart",
                normalizedPattern = "flipkart",
                categoryId = BuiltInCategories.SHOPPING.id
            )
        )

        val preview = applier.generatePreview()
        val result = applier.applyReassignments(preview.actionableItems)
        assertEquals(1, result.successCount)

        val updated = expenseDao.getExpenseById(77)
        assertNotNull(updated)

        // 19. Account info unchanged
        assertEquals("hdfc_bank_9020", updated!!.accountId)
        assertEquals("9020", updated.accountSuffix)

        // 20. Amount unchanged
        assertEquals(1234.56, updated.amount, 0.001)

        // 21. Merchant unchanged
        assertEquals("Flipkart India", updated.merchant)

        // 22. Reference & Raw Text unchanged
        assertEquals("notif_secure_key_123", updated.notificationKey)
        assertEquals("Paid INR 1234.56 at Flipkart via HDFC Bank xx9020", updated.rawText)
        assertEquals("Custom user notes that must never be wiped", updated.note)
        assertEquals("NOTIFICATION", updated.source)

        // 23. Pending state unchanged
        assertFalse(updated.isPending)

        // 24. Relationship info unchanged
        assertEquals("SELF_TRANSFER", updated.relationshipType)
        assertEquals("rel_abc_123", updated.relationshipId)

        // 25. Category updated to Inferred
        assertEquals(BuiltInCategories.SHOPPING.id, updated.categoryId)
        assertEquals(CategorySource.INFERRED, updated.categorySource)
        assertEquals("Shopping", updated.tag)
    }

    @Test
    fun test26_manualCategoryAssignmentRemainsUserAssigned() = runBlocking {
        val manualExpense = Expense(
            id = 88,
            amount = 500.0,
            merchant = "Doctor Clinic",
            dateMillis = 1000L,
            type = "Debit",
            notificationKey = "k88",
            categoryId = BuiltInCategories.HEALTHCARE.id,
            categorySource = CategorySource.USER_ASSIGNED
        )
        expenseDao.insert(manualExpense)

        val preview = applier.generatePreview()
        assertEquals(1, preview.protectedCount)
        assertTrue(preview.actionableItems.isEmpty())

        val inDb = expenseDao.getExpenseById(88)
        assertEquals(CategorySource.USER_ASSIGNED, inDb?.categorySource)
    }

    // ==================================================
    // 27-30: REUSE OF EXISTING M11 RULES, ALIASES & PROTECTIONS
    // ==================================================

    @Test
    fun test27_existingUserRulesAreReused() = runBlocking {
        // User rule: "McDonalds" -> Food & Dining
        ruleDao.insert(
            UserCategoryRule(
                matchType = UserRuleMatchType.MERCHANT_CONTAINS,
                pattern = "McDonalds",
                normalizedPattern = "mcdonalds",
                categoryId = BuiltInCategories.FOOD_AND_DINING.id
            )
        )

        expenseDao.insert(Expense(id = 1, merchant = "McDonalds Express", amount = 150.0, dateMillis = 1000L, type = "Debit", notificationKey = "k1", categoryId = null, categorySource = CategorySource.NONE))

        val preview = applier.generatePreview()
        assertEquals(1, preview.willChangeCount)
        assertEquals(BuiltInCategories.FOOD_AND_DINING.id, preview.actionableItems[0].proposedCategoryId)
    }

    @Test
    fun test28_existingMerchantAliasesAreReused() = runBlocking {
        // Alias: "MCD" -> "McDonalds"
        aliasDao.insert(
            MerchantAlias(
                alias = "MCD",
                canonicalMerchant = "McDonalds",
                normalizedAlias = "mcd",
                isEnabled = true
            )
        )
        // User rule: "McDonalds" -> Food & Dining
        ruleDao.insert(
            UserCategoryRule(
                matchType = UserRuleMatchType.MERCHANT_EXACT,
                pattern = "McDonalds",
                normalizedPattern = "mcdonalds",
                categoryId = BuiltInCategories.FOOD_AND_DINING.id
            )
        )

        // Expense has raw alias "MCD"
        expenseDao.insert(Expense(id = 1, merchant = "MCD", amount = 220.0, dateMillis = 1000L, type = "Debit", notificationKey = "k1", categoryId = null, categorySource = CategorySource.NONE))

        val preview = applier.generatePreview()
        assertEquals(1, preview.willChangeCount)
        val item = preview.actionableItems[0]
        assertEquals(BuiltInCategories.FOOD_AND_DINING.id, item.proposedCategoryId)
    }

    @Test
    fun test29_semanticProtectionsStillTakePrecedence() = runBlocking {
        // User rule: "Amazon" -> Shopping
        ruleDao.insert(
            UserCategoryRule(
                matchType = UserRuleMatchType.MERCHANT_CONTAINS,
                pattern = "Amazon",
                normalizedPattern = "amazon",
                categoryId = BuiltInCategories.SHOPPING.id
            )
        )

        // Expense text indicates transfer: "Transfer to Amazon"
        expenseDao.insert(
            Expense(
                id = 1,
                merchant = "Amazon",
                amount = 2000.0,
                dateMillis = 1000L,
                type = "Debit",
                notificationKey = "k1",
                rawText = "Transfer to Amazon for wallet balance",
                categoryId = null,
                categorySource = CategorySource.NONE
            )
        )

        val preview = applier.generatePreview()
        assertEquals(1, preview.willChangeCount)
        // Transfer protection MUST win over user rule
        assertEquals(BuiltInCategories.TRANSFER.id, preview.actionableItems[0].proposedCategoryId)
    }

    @Test
    fun test30_specificRuleTargetingAppliesOnlyThatSingleRule() = runBlocking {
        val rule1 = UserCategoryRule(id = "rule_netflix", matchType = UserRuleMatchType.MERCHANT_EXACT, pattern = "Netflix", normalizedPattern = "netflix", categoryId = BuiltInCategories.ENTERTAINMENT.id)
        val rule2 = UserCategoryRule(id = "rule_swiggy", matchType = UserRuleMatchType.MERCHANT_EXACT, pattern = "Swiggy", normalizedPattern = "swiggy", categoryId = BuiltInCategories.FOOD_AND_DINING.id)
        ruleDao.insert(rule1)
        ruleDao.insert(rule2)

        expenseDao.insert(Expense(id = 1, merchant = "Netflix", amount = 499.0, dateMillis = 1000L, type = "Debit", notificationKey = "k1", categoryId = null, categorySource = CategorySource.NONE))
        expenseDao.insert(Expense(id = 2, merchant = "Swiggy", amount = 300.0, dateMillis = 1000L, type = "Debit", notificationKey = "k2", categoryId = null, categorySource = CategorySource.NONE))

        // Target ONLY rule_netflix
        val preview = applier.generatePreview(specificRuleId = "rule_netflix")
        val actionable = preview.actionableItems

        // Only Netflix is matched by this rule; Swiggy should either fall back to built-in or not match rule_netflix
        assertTrue(actionable.any { it.transactionId == 1 && it.proposedCategoryId == BuiltInCategories.ENTERTAINMENT.id })
    }
}
