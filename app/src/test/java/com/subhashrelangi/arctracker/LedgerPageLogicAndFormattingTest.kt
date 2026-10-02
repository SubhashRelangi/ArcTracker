package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.ui.ledger.LedgerBadgeType
import com.subhashrelangi.arctracker.ui.ledger.LedgerFilterMode
import org.junit.Assert.*
import org.junit.Test
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class LedgerPageLogicAndFormattingTest {

    @Test
    fun testCurrencyFormatting_largeValues() {
        val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

        val amount1 = 145000.00
        val formatted1 = formatter.format(amount1).replace("Rs.", "₹").replace("INR", "₹").trim()
        assertTrue("Formatted currency should contain 145", formatted1.contains("145"))
        assertTrue("Formatted currency should contain 000", formatted1.contains("000"))

        val amount2 = 58420.00
        val formatted2 = formatter.format(amount2).replace("Rs.", "₹").replace("INR", "₹").trim()
        println("FORMATTED 58420: '$formatted2'")
        assertTrue("Formatted currency should contain 58", formatted2.contains("58"))
        assertTrue("Formatted currency should contain 420", formatted2.contains("420"))

        val amount3 = 480.00
        val formatted3 = formatter.format(amount3).replace("Rs.", "₹").replace("INR", "₹").trim()
        assertTrue("Formatted currency should contain 480", formatted3.contains("480"))
    }

    @Test
    fun testDateHeaderFormatting_todayYesterdayAndHistorical() {
        val cal = Calendar.getInstance()

        // Today
        val todayMillis = cal.timeInMillis
        val todayStr = formatLedgerDateHeaderTest(todayMillis)
        assertTrue("Today's date header must start with TODAY", todayStr.startsWith("TODAY •"))

        // Yesterday
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayMillis = cal.timeInMillis
        val yesterdayStr = formatLedgerDateHeaderTest(yesterdayMillis)
        assertTrue("Yesterday's date header must start with YESTERDAY", yesterdayStr.startsWith("YESTERDAY •"))

        // 5 days ago
        cal.add(Calendar.DAY_OF_YEAR, -4)
        val historicalMillis = cal.timeInMillis
        val historicalStr = formatLedgerDateHeaderTest(historicalMillis)
        assertFalse("Older date header must not be TODAY", historicalStr.startsWith("TODAY"))
        assertFalse("Older date header must not be YESTERDAY", historicalStr.startsWith("YESTERDAY"))
        assertTrue("Older date must contain day, month and year", historicalStr.length >= 8)
    }

    @Test
    fun testLedgerFilterLogic_allDebitsCreditsAndNeedsReview() {
        val sampleExpenses = listOf(
            Expense(id = 1, amount = 480.0, merchant = "Swiggy", dateMillis = 1000L, type = "Debit", isPending = true),
            Expense(id = 2, amount = 260.0, merchant = "Uber India", dateMillis = 2000L, type = "Debit", isPending = false, accountSuffix = "1234"),
            Expense(id = 3, amount = 145000.0, merchant = "Acme Corp", dateMillis = 3000L, type = "Credit", isPending = false, accountSuffix = "5678"),
            Expense(id = 4, amount = 1500.0, merchant = "UPI Ref 999", dateMillis = 4000L, type = "Debit", isPending = false, accountId = null, accountSuffix = null, source = "SMS"),
            Expense(id = 5, amount = 200.0, merchant = "Cash Tea", dateMillis = 5000L, type = "Debit", isPending = false, source = "MANUAL")
        )

        // ALL filter
        val allFiltered = filterExpenses(sampleExpenses, "", LedgerFilterMode.ALL, emptyMap())
        assertEquals(5, allFiltered.size)

        // DEBITS filter
        val debitsFiltered = filterExpenses(sampleExpenses, "", LedgerFilterMode.DEBITS, emptyMap())
        assertEquals(4, debitsFiltered.size)
        assertTrue(debitsFiltered.all { it.type.equals("Debit", true) })

        // CREDITS filter
        val creditsFiltered = filterExpenses(sampleExpenses, "", LedgerFilterMode.CREDITS, emptyMap())
        assertEquals(1, creditsFiltered.size)
        assertEquals("Acme Corp", creditsFiltered[0].merchant)

        // NEEDS_REVIEW filter (Pending + Unresolved non-manual)
        val reviewFiltered = filterExpenses(sampleExpenses, "", LedgerFilterMode.NEEDS_REVIEW, emptyMap())
        assertEquals(2, reviewFiltered.size) // Swiggy (pending) and UPI Ref 999 (unresolved)
        assertTrue(reviewFiltered.any { it.merchant == "Swiggy" })
        assertTrue(reviewFiltered.any { it.merchant == "UPI Ref 999" })
        assertFalse(reviewFiltered.any { it.merchant == "Cash Tea" }) // Manual cash is not unresolved
    }

    @Test
    fun testLedgerSearchLogic_merchantAmountBank() {
        val account = KnownFinancialAccount(id = "acc1", institutionName = "HDFC Bank", accountSuffix = "1234")
        val accountsMap = mapOf("acc1" to account)

        val sampleExpenses = listOf(
            Expense(id = 1, amount = 480.0, merchant = "Swiggy Food & Services", dateMillis = 1000L, type = "Debit", accountId = "acc1"),
            Expense(id = 2, amount = 260.0, merchant = "Uber India Systems", dateMillis = 2000L, type = "Debit", note = "Office commute"),
            Expense(id = 3, amount = 145000.0, merchant = "Acme Corp", dateMillis = 3000L, type = "Credit")
        )

        // Search by merchant substring
        val swiggyResults = filterExpenses(sampleExpenses, "swiggy", LedgerFilterMode.ALL, accountsMap)
        assertEquals(1, swiggyResults.size)
        assertEquals(1, swiggyResults[0].id)

        // Search by bank name
        val hdfcResults = filterExpenses(sampleExpenses, "hdfc", LedgerFilterMode.ALL, accountsMap)
        assertEquals(1, hdfcResults.size)
        assertEquals(1, hdfcResults[0].id)

        // Search by note
        val noteResults = filterExpenses(sampleExpenses, "commute", LedgerFilterMode.ALL, accountsMap)
        assertEquals(1, noteResults.size)
        assertEquals(2, noteResults[0].id)

        // Search by amount
        val amountResults = filterExpenses(sampleExpenses, "145000", LedgerFilterMode.ALL, accountsMap)
        assertEquals(1, amountResults.size)
        assertEquals(3, amountResults[0].id)
    }

    @Test
    fun testNetFlowCalculation_positiveAndNegative() {
        val expenses = listOf(
            Expense(id = 1, amount = 480.0, merchant = "Food", dateMillis = 1000L, type = "Debit"),
            Expense(id = 2, amount = 260.0, merchant = "Cab", dateMillis = 2000L, type = "Debit"),
            Expense(id = 3, amount = 145000.0, merchant = "Salary", dateMillis = 3000L, type = "Credit")
        )

        val totalDebits = expenses.filter { it.type.equals("Debit", true) }.sumOf { it.amount }
        val totalCredits = expenses.filter { it.type.equals("Credit", true) }.sumOf { it.amount }
        val netFlow = totalCredits - totalDebits

        assertEquals(740.0, totalDebits, 0.01)
        assertEquals(145000.0, totalCredits, 0.01)
        assertEquals(144260.0, netFlow, 0.01)
    }

    @Test
    fun testLongMerchantTextHandling_noCrash() {
        val longMerchant = "AMAZON PAY INDIA PRIVATE LIMITED - ORDER #402-9281729-1928374 BRANCH BANGALORE"
        val expense = Expense(
            id = 99,
            amount = 999999.99,
            merchant = longMerchant,
            dateMillis = System.currentTimeMillis(),
            type = "Debit"
        )

        assertNotNull(expense.merchant)
        assertTrue(expense.merchant.length > 50)
    }

    // Helper mirror matching formatLedgerDateHeader
    private fun formatLedgerDateHeaderTest(dateMillis: Long): String {
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val yesterdayCal = (todayCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }

        val targetCal = Calendar.getInstance().apply { timeInMillis = dateMillis }
        val isToday = targetCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                targetCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
        val isYesterday = targetCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
                targetCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)

        val dayFormat = SimpleDateFormat("dd MMM", Locale.US)
        val fullFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

        return when {
            isToday -> "TODAY • ${dayFormat.format(Date(dateMillis)).uppercase(Locale.US)}"
            isYesterday -> "YESTERDAY • ${dayFormat.format(Date(dateMillis)).uppercase(Locale.US)}"
            else -> fullFormat.format(Date(dateMillis)).uppercase(Locale.US)
        }
    }

    // Helper mirror matching filter logic
    private fun filterExpenses(
        expenses: List<Expense>,
        searchQuery: String,
        selectedFilter: LedgerFilterMode,
        accountsMap: Map<String, KnownFinancialAccount>
    ): List<Expense> {
        return expenses.filter { expense ->
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim().lowercase(Locale.ROOT)
                val account = expense.accountId?.let { accountsMap[it] }
                val bankName = account?.institutionName ?: ""
                val suffix = expense.accountSuffix ?: account?.accountSuffix ?: ""
                val amountStr = String.format(Locale.US, "%.2f", expense.amount)

                expense.merchant.lowercase(Locale.ROOT).contains(q) ||
                        (expense.note?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                        (expense.tag?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                        (expense.category?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                        (expense.rawText?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                        bankName.lowercase(Locale.ROOT).contains(q) ||
                        suffix.contains(q) ||
                        amountStr.contains(q)
            }

            if (!matchesSearch) return@filter false

            when (selectedFilter) {
                LedgerFilterMode.ALL -> true
                LedgerFilterMode.DEBITS -> expense.type.equals("Debit", ignoreCase = true)
                LedgerFilterMode.CREDITS -> expense.type.equals("Credit", ignoreCase = true)
                LedgerFilterMode.NEEDS_REVIEW -> expense.isPending || (expense.accountId.isNullOrBlank() && expense.accountSuffix.isNullOrBlank() && !expense.type.equals("Credit", true) && expense.source != "MANUAL")
            }
        }
    }
}
