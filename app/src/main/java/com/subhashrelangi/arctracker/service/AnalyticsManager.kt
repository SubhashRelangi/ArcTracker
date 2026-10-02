package com.subhashrelangi.arctracker.service

import com.subhashrelangi.arctracker.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Read-Only Analytics Engine for ArcTracker (Milestone 13).
 *
 * Guarantees:
 * - 100% READ-ONLY: Never creates, updates, deletes, or mutates transactions, accounts, rules, or categories.
 * - Deterministic: Pure mathematical aggregation over persisted records.
 * - Self-Transfer Safe: Never inflates spending, income, net, or merchant metrics with self-transfers.
 * - Category Inference Decoupled: Never re-runs inference or modifies categorySource.
 * - Reactive: Exposes Flow streams that automatically refresh when persisted transactions change.
 */
class AnalyticsManager(
    private val expenseDao: ExpenseDao,
    private val categoryDao: TransactionCategoryDao? = null,
    private val accountDao: KnownFinancialAccountDao? = null
) {

    /**
     * Queries and computes an [AnalyticsReport] for the specified date range.
     */
    suspend fun getAnalyticsReport(
        dateRange: AnalyticsDateRange = AnalyticsDateRange.THIS_MONTH,
        customStartMillis: Long? = null,
        customEndMillis: Long? = null,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): AnalyticsReport {
        val (startMillis, endMillis) = AnalyticsDateRange.calculateBounds(
            range = dateRange,
            referenceTimeMillis = referenceTimeMillis,
            customStartMillis = customStartMillis,
            customEndMillis = customEndMillis
        )

        val expenses = if (dateRange == AnalyticsDateRange.ALL_TIME) {
            expenseDao.getAllExpensesList()
        } else {
            expenseDao.getExpensesBetween(startMillis, endMillis)
        }

        val categories = categoryDao?.getAll() ?: emptyList()
        val accounts = accountDao?.getAll() ?: emptyList()

        return calculateAnalytics(
            expenses = expenses,
            categories = categories,
            accounts = accounts,
            dateRange = dateRange,
            startMillis = startMillis,
            endMillis = endMillis
        )
    }

    /**
     * Observes reactive [AnalyticsReport] updates for the specified date range.
     */
    fun getAnalyticsReportFlow(
        dateRange: AnalyticsDateRange = AnalyticsDateRange.THIS_MONTH,
        customStartMillis: Long? = null,
        customEndMillis: Long? = null,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): Flow<AnalyticsReport> {
        val (startMillis, endMillis) = AnalyticsDateRange.calculateBounds(
            range = dateRange,
            referenceTimeMillis = referenceTimeMillis,
            customStartMillis = customStartMillis,
            customEndMillis = customEndMillis
        )

        val flow = if (dateRange == AnalyticsDateRange.ALL_TIME) {
            expenseDao.getAllExpenses()
        } else {
            expenseDao.getExpensesBetweenFlow(startMillis, endMillis)
        }

        return flow.map { expenses ->
            val categories = categoryDao?.getAll() ?: emptyList()
            val accounts = accountDao?.getAll() ?: emptyList()
            calculateAnalytics(
                expenses = expenses,
                categories = categories,
                accounts = accounts,
                dateRange = dateRange,
                startMillis = startMillis,
                endMillis = endMillis
            )
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Pure, deterministic in-memory calculation of [AnalyticsReport].
     * Can be tested completely independently of Room or SQLite.
     */
    fun calculateAnalytics(
        expenses: List<Expense>,
        categories: List<TransactionCategory>,
        accounts: List<KnownFinancialAccount>,
        dateRange: AnalyticsDateRange,
        startMillis: Long,
        endMillis: Long
    ): AnalyticsReport {
        // Safe bounds filter (guarantees strict adherence to start..end)
        val inRangeExpenses = if (dateRange == AnalyticsDateRange.ALL_TIME) {
            expenses
        } else {
            expenses.filter { it.dateMillis in startMillis..endMillis }
        }

        if (inRangeExpenses.isEmpty()) {
            return AnalyticsReport.empty(dateRange, startMillis, endMillis)
        }

        val categoriesMap = (categories + BuiltInCategories.ALL).associateBy { it.id }
        val accountsMap = accounts.associateBy { it.id }

        var totalExpenses = 0.0
        var totalIncome = 0.0
        var expenseCount = 0
        var incomeCount = 0
        var pendingCount = 0
        var pendingAmount = 0.0
        var selfTransferCount = 0
        var selfTransferAmount = 0.0

        // Filter collections
        val validExpenses = mutableListOf<Expense>()
        val validIncomes = mutableListOf<Expense>()

        for (exp in inRangeExpenses) {
            val isSelfTransfer = exp.relationshipType == TransactionRelationshipType.SELF_TRANSFER
            if (isSelfTransfer) {
                selfTransferCount++
                if (exp.type.equals("Debit", ignoreCase = true)) {
                    selfTransferAmount += exp.amount
                }
                continue // Exclude self-transfers from general spending, income, and category metrics
            }

            if (exp.isPending) {
                pendingCount++
                pendingAmount += exp.amount
                continue // Exclude pending from settled spending/income totals
            }

            if (exp.type.equals("Debit", ignoreCase = true)) {
                totalExpenses += exp.amount
                expenseCount++
                validExpenses.add(exp)
            } else if (exp.type.equals("Credit", ignoreCase = true)) {
                totalIncome += exp.amount
                incomeCount++
                validIncomes.add(exp)
            }
        }

        val netAmount = totalIncome - totalExpenses

        val summary = AnalyticsSummary(
            totalExpenses = totalExpenses,
            totalIncome = totalIncome,
            netAmount = netAmount,
            totalTransactionCount = inRangeExpenses.size,
            expenseCount = expenseCount,
            incomeCount = incomeCount,
            pendingCount = pendingCount,
            pendingAmount = pendingAmount,
            selfTransferCount = selfTransferCount,
            selfTransferAmount = selfTransferAmount
        )

        // ==========================================
        // 1. Category Analytics (Debits only)
        // ==========================================
        val categorizedGroups = mutableMapOf<String, MutableList<Expense>>()
        val uncategorizedExpenses = mutableListOf<Expense>()

        for (exp in validExpenses) {
            val resolvedCatId = exp.categoryId?.takeIf { it.isNotBlank() }
                ?: BuiltInCategories.findLegacyMapping(exp.tag)?.id

            if (resolvedCatId != null &&
                !resolvedCatId.equals("uncategorized", ignoreCase = true) &&
                !resolvedCatId.equals("none", ignoreCase = true)
            ) {
                categorizedGroups.getOrPut(resolvedCatId) { mutableListOf() }.add(exp)
            } else {
                uncategorizedExpenses.add(exp)
            }
        }

        val categoryBreakdown = categorizedGroups.map { (catId, exps) ->
            val cat = categoriesMap[catId]
            val catName = cat?.name
                ?: exps.firstOrNull()?.tag?.takeIf { it.isNotBlank() }
                ?: catId
            val iconKey = cat?.iconKey ?: "category"
            val colorKey = cat?.colorKey ?: "default"
            val catTotal = exps.sumOf { it.amount }
            val percentage = if (totalExpenses > 0.0) (catTotal / totalExpenses) * 100.0 else 0.0

            CategoryAnalytics(
                categoryId = catId,
                categoryName = catName,
                iconKey = iconKey,
                colorKey = colorKey,
                totalAmount = catTotal,
                transactionCount = exps.size,
                percentage = percentage,
                isSystem = cat?.isSystem ?: false,
                isArchived = cat?.isArchived ?: false
            )
        }.sortedByDescending { it.totalAmount }

        val uncategorizedTotal = uncategorizedExpenses.sumOf { it.amount }
        val uncategorizedPercentage = if (totalExpenses > 0.0) (uncategorizedTotal / totalExpenses) * 100.0 else 0.0
        val uncategorized = UncategorizedAnalytics(
            transactionCount = uncategorizedExpenses.size,
            totalAmount = uncategorizedTotal,
            percentage = uncategorizedPercentage
        )

        // ==========================================
        // 2. Monthly Trend Analytics
        // ==========================================
        val ymFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        val monthLabelFormat = SimpleDateFormat("MMM yyyy", Locale.US)

        val monthlyGroups = inRangeExpenses
            .filter { it.relationshipType != TransactionRelationshipType.SELF_TRANSFER && !it.isPending }
            .groupBy { ymFormat.format(Date(it.dateMillis)) }

        val monthlyTrends = monthlyGroups.map { (ym, exps) ->
            val monthStartMillis = exps.minOfOrNull { it.dateMillis } ?: 0L
            val label = try {
                val date = ymFormat.parse(ym)
                if (date != null) monthLabelFormat.format(date) else ym
            } catch (e: Exception) {
                ym
            }

            val mExpenses = exps.filter { it.type.equals("Debit", ignoreCase = true) }.sumOf { it.amount }
            val mIncome = exps.filter { it.type.equals("Credit", ignoreCase = true) }.sumOf { it.amount }

            // Monthly category breakdown (Debits only)
            val monthCatGroups = exps.filter { it.type.equals("Debit", ignoreCase = true) }
                .groupBy { exp ->
                    exp.categoryId?.takeIf { it.isNotBlank() }
                        ?: BuiltInCategories.findLegacyMapping(exp.tag)?.id
                        ?: "uncategorized"
                }
            val monthCategoryTrends = monthCatGroups.map { (catId, cExps) ->
                val catName = categoriesMap[catId]?.name
                    ?: cExps.firstOrNull()?.tag?.takeIf { it.isNotBlank() }
                    ?: if (catId == "uncategorized") "Uncategorized" else catId
                MonthlyCategoryTrend(
                    categoryId = catId,
                    categoryName = catName,
                    amount = cExps.sumOf { it.amount },
                    transactionCount = cExps.size
                )
            }.sortedByDescending { it.amount }

            MonthlyTrendAnalytics(
                yearMonth = ym,
                label = label,
                startMillis = monthStartMillis,
                totalExpenses = mExpenses,
                totalIncome = mIncome,
                netAmount = mIncome - mExpenses,
                transactionCount = exps.size,
                categoryTrends = monthCategoryTrends
            )
        }.sortedBy { it.yearMonth }

        // ==========================================
        // 3. Merchant Analytics (Top Merchants by Expense)
        // ==========================================
        val merchantGroups = validExpenses.groupBy { it.merchant.trim().ifEmpty { "Unknown Merchant" } }
        val topMerchants = merchantGroups.map { (merchant, exps) ->
            val mTotal = exps.sumOf { it.amount }
            val percentage = if (totalExpenses > 0.0) (mTotal / totalExpenses) * 100.0 else 0.0
            MerchantAnalytics(
                merchant = merchant,
                totalAmount = mTotal,
                transactionCount = exps.size,
                percentage = percentage
            )
        }.sortedByDescending { it.totalAmount }

        // ==========================================
        // 4. Category Source Analytics
        // ==========================================
        val sourceGroups = inRangeExpenses.groupBy {
            val src = it.categorySource.trim().uppercase()
            when (src) {
                CategorySource.USER_ASSIGNED -> CategorySource.USER_ASSIGNED
                CategorySource.INFERRED -> CategorySource.INFERRED
                CategorySource.SYSTEM_ASSIGNED -> CategorySource.SYSTEM_ASSIGNED
                else -> CategorySource.NONE
            }
        }

        val totalTxnCount = inRangeExpenses.size.toDouble()
        val categorySources = listOf(
            CategorySource.USER_ASSIGNED to "User Assigned",
            CategorySource.INFERRED to "Automatically Inferred",
            CategorySource.SYSTEM_ASSIGNED to "System Assigned",
            CategorySource.NONE to "Unassigned / None"
        ).mapNotNull { (sourceKey, displayName) ->
            val exps = sourceGroups[sourceKey]
            if (exps != null && exps.isNotEmpty()) {
                val count = exps.size
                val sTotal = exps.sumOf { it.amount }
                val percentage = if (totalTxnCount > 0.0) (count / totalTxnCount) * 100.0 else 0.0
                CategorySourceAnalytics(
                    source = sourceKey,
                    displayName = displayName,
                    transactionCount = count,
                    totalAmount = sTotal,
                    percentage = percentage
                )
            } else null
        }

        // ==========================================
        // 5. Automated Categorization / Rule Effectiveness
        // ==========================================
        val inferredExps = sourceGroups[CategorySource.INFERRED] ?: emptyList()
        val userAssignedExps = sourceGroups[CategorySource.USER_ASSIGNED] ?: emptyList()
        val uncategorizedSourceExps = sourceGroups[CategorySource.NONE] ?: emptyList()

        val categorizedCount = inferredExps.size + userAssignedExps.size
        val autoPercentage = if (categorizedCount > 0) {
            (inferredExps.size.toDouble() / categorizedCount) * 100.0
        } else 0.0

        val ruleEffectiveness = RuleAnalytics(
            inferredCount = inferredExps.size,
            inferredAmount = inferredExps.sumOf { it.amount },
            userAssignedCount = userAssignedExps.size,
            userAssignedAmount = userAssignedExps.sumOf { it.amount },
            uncategorizedCount = uncategorizedSourceExps.size,
            uncategorizedAmount = uncategorizedSourceExps.sumOf { it.amount },
            autoCategorizedPercentage = autoPercentage
        )

        // ==========================================
        // 6. Account Analytics
        // ==========================================
        val accountGroups = inRangeExpenses
            .filter { it.relationshipType != TransactionRelationshipType.SELF_TRANSFER && !it.isPending }
            .groupBy { it.accountId }

        val accountBreakdown = accountGroups.map { (accId, exps) ->
            val acc = accId?.let { accountsMap[it] }
            val institutionName = acc?.institutionName?.takeIf { it.isNotBlank() }
                ?: if (accId == null) "Unresolved Account" else "Account $accId"

            val accountSuffix = acc?.accountSuffix
                ?: exps.firstOrNull { !it.accountSuffix.isNullOrBlank() }?.accountSuffix

            val instrumentType = acc?.instrumentType?.name

            val accExpenses = exps.filter { it.type.equals("Debit", ignoreCase = true) }.sumOf { it.amount }
            val accIncome = exps.filter { it.type.equals("Credit", ignoreCase = true) }.sumOf { it.amount }

            AccountAnalytics(
                accountId = accId,
                institutionName = institutionName,
                accountSuffix = accountSuffix,
                instrumentType = instrumentType,
                totalExpenses = accExpenses,
                totalIncome = accIncome,
                netAmount = accIncome - accExpenses,
                transactionCount = exps.size
            )
        }.sortedByDescending { it.totalExpenses }

        return AnalyticsReport(
            dateRange = dateRange,
            startDateMillis = startMillis,
            endDateMillis = endMillis,
            summary = summary,
            categoryBreakdown = categoryBreakdown,
            uncategorized = uncategorized,
            monthlyTrends = monthlyTrends,
            topMerchants = topMerchants,
            categorySources = categorySources,
            ruleEffectiveness = ruleEffectiveness,
            accountBreakdown = accountBreakdown
        )
    }
}
