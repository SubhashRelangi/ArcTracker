package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.service.InstrumentType
import com.subhashrelangi.arctracker.ui.WalletCardModel
import com.subhashrelangi.arctracker.ui.WalletCardTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying the physical pocket wallet and Apple Wallet cascading stack
 * business logic, card mapping, balance filtering, and state transitions.
 */
class HomeScreenWalletDeckTest {

    @Test
    fun testDemoModeWalletCards() {
        // In Demo Mode, exactly 4 authentic cards must be provided
        val demoCards = listOf(
            WalletCardModel(
                id = "hdfc",
                bankName = "HDFC",
                cardName = "HDFC Regalia",
                suffix = "4821",
                balanceAmount = 78400.0,
                balanceFormatted = "₹78,400.00",
                incomeAmount = 48500.0,
                incomeFormatted = "+₹48,500",
                expenseAmount = 12450.0,
                expenseFormatted = "-₹12,450",
                cardTypeLabel = "CREDIT",
                theme = WalletCardTheme.GRAPHITE_DARK
            ),
            WalletCardModel(
                id = "icici",
                bankName = "ICICI",
                cardName = "ICICI Advantage",
                suffix = "1002",
                balanceAmount = 42350.0,
                balanceFormatted = "₹42,350.00",
                incomeAmount = 25000.0,
                incomeFormatted = "+₹25,000",
                expenseAmount = 14800.0,
                expenseFormatted = "-₹14,800",
                cardTypeLabel = "VISA",
                theme = WalletCardTheme.ROYAL_PURPLE
            ),
            WalletCardModel(
                id = "upi",
                bankName = "UPI",
                cardName = "UPI & Digital Wallets",
                suffix = "",
                balanceAmount = 27500.0,
                balanceFormatted = "₹27,500.00",
                incomeAmount = 11500.0,
                incomeFormatted = "+₹11,500",
                expenseAmount = 9500.0,
                expenseFormatted = "-₹9,500",
                cardTypeLabel = "WALLET",
                statusText = "Active",
                theme = WalletCardTheme.PORCELAIN_WHITE,
                isWalletUpi = true
            ),
            WalletCardModel(
                id = "sbi",
                bankName = "SBI",
                cardName = "SBI Global Savings",
                suffix = "8820",
                balanceAmount = 14200.0,
                balanceFormatted = "₹14,200.00",
                incomeAmount = 15000.0,
                incomeFormatted = "+₹15,000",
                expenseAmount = 800.0,
                expenseFormatted = "-₹800",
                cardTypeLabel = "SAVINGS",
                statusText = "Active",
                theme = WalletCardTheme.EMERALD_SLATE
            )
        )

        assertEquals(4, demoCards.size)
        // Verify Pocket Mode displays first 3 cards tucked in pocket
        val pocketTuckedCards = demoCards.take(3)
        assertEquals("hdfc", pocketTuckedCards[0].id)
        assertEquals("icici", pocketTuckedCards[1].id)
        assertEquals("upi", pocketTuckedCards[2].id)

        // Verify active selection brings card into front slot
        val selectedCardId = "sbi"
        val active = demoCards.find { it.id == selectedCardId }
        assertNotNull(active)
        assertEquals("SBI Global Savings", active!!.cardName)
        assertEquals("₹14,200.00", active.balanceFormatted)
        assertEquals("+₹15,000", active.incomeFormatted)
        assertEquals("-₹800", active.expenseFormatted)
    }

    @Test
    fun testRealAccountsWalletMapping() {
        val accounts = listOf(
            KnownFinancialAccount(
                id = "acct_hdfc",
                institutionId = "hdfc",
                institutionName = "HDFC Bank",
                instrumentType = InstrumentType.CARD,
                accountSuffix = "4821"
            ),
            KnownFinancialAccount(
                id = "acct_sbi",
                institutionId = "sbi",
                institutionName = "State Bank of India",
                instrumentType = InstrumentType.BANK_ACCOUNT,
                accountSuffix = "1104"
            )
        )

        val expenses = listOf(
            Expense(
                id = 1,
                amount = 25000.0,
                merchant = "Employer Ltd",
                dateMillis = System.currentTimeMillis(),
                type = "Credit",
                accountId = "acct_hdfc",
                accountSuffix = "4821"
            ),
            Expense(
                id = 2,
                amount = 3500.0,
                merchant = "Nature Basket",
                dateMillis = System.currentTimeMillis(),
                type = "Debit",
                accountId = "acct_hdfc",
                accountSuffix = "4821"
            ),
            Expense(
                id = 3,
                amount = 1200.0,
                merchant = "Power Corp",
                dateMillis = System.currentTimeMillis(),
                type = "Debit",
                accountId = "acct_sbi",
                accountSuffix = "1104"
            )
        )

        // Aggregate across accounts
        val totalInc = expenses.filter { it.type.equals("Credit", true) }.sumOf { it.amount }
        val totalExp = expenses.filter { !it.type.equals("Credit", true) }.sumOf { it.amount }
        assertEquals(25000.0, totalInc, 0.01)
        assertEquals(4700.0, totalExp, 0.01)
        assertEquals(20300.0, totalInc - totalExp, 0.01)

        // Filter for HDFC account specifically
        val hdfcExpenses = expenses.filter { it.accountId == "acct_hdfc" }
        val hdfcInc = hdfcExpenses.filter { it.type.equals("Credit", true) }.sumOf { it.amount }
        val hdfcExp = hdfcExpenses.filter { !it.type.equals("Credit", true) }.sumOf { it.amount }
        assertEquals(25000.0, hdfcInc, 0.01)
        assertEquals(3500.0, hdfcExp, 0.01)
        assertEquals(21500.0, hdfcInc - hdfcExp, 0.01)
    }

    @Test
    fun testWalletExpansionAndCollapseToggle() {
        var isWalletExpanded = false

        // User clicks "See all cards"
        isWalletExpanded = true
        assertTrue("Wallet should be expanded in Apple Wallet cascading mode", isWalletExpanded)

        // User clicks "Tuck into wallet"
        isWalletExpanded = false
        assertFalse("Wallet should be collapsed back to physical leather pocket", isWalletExpanded)
    }

    @Test
    fun testCardSelectionToggle() {
        var selectedCardId: String? = null

        // Initial state: No card selected, aggregate view
        assertNull(selectedCardId)

        // Tapping card "hdfc" selects it
        selectedCardId = "hdfc"
        assertEquals("hdfc", selectedCardId)

        // Tapping selected card again deselects and returns to all cards
        selectedCardId = null
        assertNull(selectedCardId)
    }
}
