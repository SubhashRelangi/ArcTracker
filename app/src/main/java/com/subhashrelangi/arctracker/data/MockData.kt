package com.subhashrelangi.arctracker.data

import java.util.Calendar
import java.util.UUID

object MockData {
    fun getInitialExpenses(): List<Expense> {
        val now = Calendar.getInstance()
        val list = mutableListOf<Expense>()
        var currentId = 1

        fun timeOffset(daysAgo: Int, hour: Int, minute: Int): Long {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun monthOffset(monthsAgo: Int, day: Int, hour: Int, minute: Int): Long {
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -monthsAgo)
            cal.set(Calendar.DAY_OF_MONTH, day)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        // Current Month transactions
        list.add(
            Expense(
                id = currentId++,
                amount = 450.0,
                merchant = "Zomato",
                dateMillis = timeOffset(0, 13, 15),
                type = "Debit",
                tag = "Food",
                note = "Lunch with team",
                source = "MANUAL"
            )
        )
        list.add(
            Expense(
                id = currentId++,
                amount = 85.0,
                merchant = "Starbucks",
                dateMillis = timeOffset(0, 9, 30),
                type = "Debit",
                tag = "Food",
                note = "Morning latte",
                source = "MANUAL"
            )
        )
        list.add(
            Expense(
                id = currentId++,
                amount = 320.0,
                merchant = "Uber",
                dateMillis = timeOffset(1, 19, 45),
                type = "Debit",
                tag = "Travel",
                note = "Cab back home",
                source = "MANUAL"
            )
        )
        list.add(
            Expense(
                id = currentId++,
                amount = 1499.0,
                merchant = "Amazon",
                dateMillis = timeOffset(2, 16, 20),
                type = "Debit",
                tag = "Shopping",
                note = "Wireless headphones",
                source = "MANUAL"
            )
        )
        list.add(
            Expense(
                id = currentId++,
                amount = 75000.0,
                merchant = "Tech Corp Inc",
                dateMillis = timeOffset(3, 10, 0),
                type = "Credit",
                tag = "Salary",
                note = "Monthly salary credited",
                source = "MANUAL"
            )
        )
        list.add(
            Expense(
                id = currentId++,
                amount = 1250.0,
                merchant = "Electricity Bill",
                dateMillis = timeOffset(4, 11, 10),
                type = "Debit",
                tag = "Bills",
                note = "BESCOM power bill",
                source = "MANUAL"
            )
        )
        list.add(
            Expense(
                id = currentId++,
                amount = 650.0,
                merchant = "Swiggy Instamart",
                dateMillis = timeOffset(5, 20, 15),
                type = "Debit",
                tag = "Food",
                note = "Weekly groceries",
                source = "MANUAL"
            )
        )
        list.add(
            Expense(
                id = currentId++,
                amount = 12000.0,
                merchant = "Freelance Project",
                dateMillis = timeOffset(7, 14, 30),
                type = "Credit",
                tag = "Allowance",
                note = "UI redesign milestone",
                source = "MANUAL"
            )
        )
        list.add(
            Expense(
                id = currentId++,
                amount = 299.0,
                merchant = "Netflix",
                dateMillis = timeOffset(8, 8, 45),
                type = "Debit",
                tag = "Bills",
                note = "Monthly subscription",
                source = "MANUAL"
            )
        )
        list.add(
            Expense(
                id = currentId++,
                amount = 1850.0,
                merchant = "Decathlon",
                dateMillis = timeOffset(10, 17, 30),
                type = "Debit",
                tag = "Shopping",
                note = "Running shoes & gear",
                source = "MANUAL"
            )
        )
        list.add(
            Expense(
                id = currentId++,
                amount = 450.0,
                merchant = "BookMyShow",
                dateMillis = timeOffset(12, 21, 0),
                type = "Debit",
                tag = "Other",
                note = "Movie tickets",
                source = "MANUAL"
            )
        )

        // Previous Month transactions
        list.add(
            Expense(
                id = currentId++,
                amount = 75000.0,
                merchant = "Tech Corp Inc",
                dateMillis = monthOffset(1, 1, 10, 0),
                type = "Credit",
                tag = "Salary",
                note = "Previous month salary",
                source = "MANUAL"
            )
        )
        list.add(
            Expense(
                id = currentId++,
                amount = 3200.0,
                merchant = "DMart Supermarket",
                dateMillis = monthOffset(1, 5, 16, 0),
                type = "Debit",
                tag = "Food",
                note = "Monthly ration",
                source = "MANUAL"
            )
        )
        list.add(
            Expense(
                id = currentId++,
                amount = 2500.0,
                merchant = "Indian Oil Petrol",
                dateMillis = monthOffset(1, 12, 11, 0),
                type = "Debit",
                tag = "Travel",
                note = "Car fuel",
                source = "MANUAL"
            )
        )
        list.add(
            Expense(
                id = currentId++,
                amount = 4500.0,
                merchant = "Myntra",
                dateMillis = monthOffset(1, 18, 14, 0),
                type = "Debit",
                tag = "Shopping",
                note = "Festive clothes",
                source = "MANUAL"
            )
        )
        list.add(
            Expense(
                id = currentId++,
                amount = 1500.0,
                merchant = "Amazon Refund",
                dateMillis = monthOffset(1, 22, 12, 0),
                type = "Credit",
                tag = "Refund",
                note = "Returned item refund",
                source = "MANUAL"
            )
        )

        return list.sortedByDescending { it.dateMillis }
    }

    /**
     * Complete rich dataset for Demo Mode:
     * - Exactly 3 unverified pending transactions (Swiggy, Amazon Pay, Starbucks) for Review screen
     * - Rich debit & credit transactions across diverse categories (Food, Shopping, Travel, Bills, Salary)
     * - Linked account suffixes matching demo financial accounts
     */
    fun getFullDemoExpenses(): List<Expense> {
        fun timeOffset(daysAgo: Int, hour: Int, minute: Int): Long {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun monthOffset(monthsAgo: Int, day: Int, hour: Int, minute: Int): Long {
            val cal = Calendar.getInstance()
            cal.add(Calendar.MONTH, -monthsAgo)
            cal.set(Calendar.DAY_OF_MONTH, day)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        val list = mutableListOf<Expense>()

        // 1. Pending Transactions for Review Screen (Exactly 3 unverified items)
        list.add(
            Expense(
                amount = 480.0,
                merchant = "Swiggy",
                dateMillis = timeOffset(0, 13, 42),
                type = "Debit",
                tag = "Food",
                isPending = true,
                note = "Order #84920412",
                source = "DEMO",
                accountSuffix = "4012"
            )
        )
        list.add(
            Expense(
                amount = 1299.0,
                merchant = "Amazon Pay",
                dateMillis = timeOffset(0, 11, 15),
                type = "Debit",
                tag = "Shopping",
                isPending = true,
                note = "Order 402-91823-11",
                source = "DEMO",
                accountSuffix = "9821"
            )
        )
        list.add(
            Expense(
                amount = 350.0,
                merchant = "Starbucks Coffee",
                dateMillis = timeOffset(1, 16, 20),
                type = "Debit",
                tag = "Food",
                isPending = true,
                note = "Frappuccino & muffin",
                source = "DEMO",
                accountSuffix = "4012"
            )
        )

        // 2. Verified Active Ledger Transactions (Current Month)
        list.add(
            Expense(
                amount = 75000.0,
                merchant = "Tech Corp Inc",
                dateMillis = timeOffset(2, 10, 0),
                type = "Credit",
                tag = "Salary",
                isPending = false,
                note = "Monthly payroll direct deposit",
                source = "DEMO",
                accountSuffix = "4012"
            )
        )
        list.add(
            Expense(
                amount = 25000.0,
                merchant = "Freelance Consulting",
                dateMillis = timeOffset(5, 15, 30),
                type = "Credit",
                tag = "Allowance",
                isPending = false,
                note = "Mobile app contract milestone",
                source = "DEMO",
                accountSuffix = "1089"
            )
        )
        list.add(
            Expense(
                amount = 180000.0,
                merchant = "Opening Balance",
                dateMillis = timeOffset(20, 9, 0),
                type = "Credit",
                tag = "Salary",
                isPending = false,
                note = "Liquid savings balance",
                source = "DEMO",
                accountSuffix = "4012"
            )
        )
        list.add(
            Expense(
                amount = 450.0,
                merchant = "Zomato",
                dateMillis = timeOffset(1, 19, 30),
                type = "Debit",
                tag = "Food",
                isPending = false,
                note = "Dinner with friends",
                source = "DEMO",
                accountSuffix = "4012"
            )
        )
        list.add(
            Expense(
                amount = 320.0,
                merchant = "Uber",
                dateMillis = timeOffset(2, 20, 15),
                type = "Debit",
                tag = "Travel",
                isPending = false,
                note = "Ride home from office",
                source = "DEMO",
                accountSuffix = "4012"
            )
        )
        list.add(
            Expense(
                amount = 4850.0,
                merchant = "DMart Supermarket",
                dateMillis = timeOffset(3, 14, 20),
                type = "Debit",
                tag = "Food",
                isPending = false,
                note = "Monthly groceries and home essentials",
                source = "DEMO",
                accountSuffix = "4012"
            )
        )
        list.add(
            Expense(
                amount = 3200.0,
                merchant = "Indian Oil Petrol",
                dateMillis = timeOffset(4, 11, 45),
                type = "Debit",
                tag = "Travel",
                isPending = false,
                note = "Vehicle fuel refilling",
                source = "DEMO",
                accountSuffix = "9821"
            )
        )
        list.add(
            Expense(
                amount = 1450.0,
                merchant = "Electricity Bill",
                dateMillis = timeOffset(6, 12, 10),
                type = "Debit",
                tag = "Bills",
                isPending = false,
                note = "State power distribution board",
                source = "DEMO",
                accountSuffix = "4012"
            )
        )
        list.add(
            Expense(
                amount = 649.0,
                merchant = "Netflix Subscription",
                dateMillis = timeOffset(8, 8, 30),
                type = "Debit",
                tag = "Bills",
                isPending = false,
                note = "Monthly 4K plan",
                source = "DEMO",
                accountSuffix = "9821"
            )
        )
        list.add(
            Expense(
                amount = 2800.0,
                merchant = "Decathlon",
                dateMillis = timeOffset(9, 17, 40),
                type = "Debit",
                tag = "Shopping",
                isPending = false,
                note = "Running shoes & fitness wear",
                source = "DEMO",
                accountSuffix = "9821"
            )
        )
        list.add(
            Expense(
                amount = 3490.0,
                merchant = "Myntra",
                dateMillis = timeOffset(11, 16, 50),
                type = "Debit",
                tag = "Shopping",
                isPending = false,
                note = "Festive casual wear",
                source = "DEMO",
                accountSuffix = "9821"
            )
        )
        list.add(
            Expense(
                amount = 760.0,
                merchant = "BookMyShow",
                dateMillis = timeOffset(12, 21, 15),
                type = "Debit",
                tag = "Other",
                isPending = false,
                note = "Weekend cinema tickets",
                source = "DEMO",
                accountSuffix = "4012"
            )
        )

        // 3. Previous Month Historical Transactions
        list.add(
            Expense(
                amount = 75000.0,
                merchant = "Tech Corp Inc",
                dateMillis = monthOffset(1, 1, 10, 0),
                type = "Credit",
                tag = "Salary",
                isPending = false,
                note = "Previous month salary",
                source = "DEMO",
                accountSuffix = "4012"
            )
        )
        list.add(
            Expense(
                amount = 22000.0,
                merchant = "House Rent",
                dateMillis = monthOffset(1, 3, 11, 0),
                type = "Debit",
                tag = "Bills",
                isPending = false,
                note = "Monthly apartment rent",
                source = "DEMO",
                accountSuffix = "4012"
            )
        )
        list.add(
            Expense(
                amount = 5400.0,
                merchant = "DMart Supermarket",
                dateMillis = monthOffset(1, 6, 17, 30),
                type = "Debit",
                tag = "Food",
                isPending = false,
                note = "Groceries",
                source = "DEMO",
                accountSuffix = "4012"
            )
        )
        list.add(
            Expense(
                amount = 1500.0,
                merchant = "Amazon Refund",
                dateMillis = monthOffset(1, 15, 12, 0),
                type = "Credit",
                tag = "Refund",
                isPending = false,
                note = "Item return refund credited",
                source = "DEMO",
                accountSuffix = "9821"
            )
        )

        return list.sortedByDescending { it.dateMillis }
    }

    /**
     * Seeds complete demo data into Room database (Accounts, Budgets, Expenses)
     * so that ALL screens in the app have full functionality in Demo Mode.
     */
    suspend fun seedCompleteDemoData(database: AppDatabase) {
        val expenseDao = database.expenseDao()
        val accountDao = database.knownFinancialAccountDao()
        val budgetDao = database.budgetDao()

        // 1. Seed financial accounts if none exist
        if (accountDao.getAll().isEmpty()) {
            accountDao.insertAll(
                listOf(
                    KnownFinancialAccount(
                        id = "hdfc_bank_account_4012",
                        institutionId = "hdfc",
                        institutionName = "HDFC Bank",
                        accountSuffix = "4012",
                        instrumentType = com.subhashrelangi.arctracker.service.InstrumentType.BANK_ACCOUNT,
                        confidence = com.subhashrelangi.arctracker.service.IdentityConfidence.HIGH,
                        source = AccountSource.USER_CONFIRMED
                    ),
                    KnownFinancialAccount(
                        id = "sbi_card_9821",
                        institutionId = "sbi",
                        institutionName = "SBI Card",
                        accountSuffix = "9821",
                        instrumentType = com.subhashrelangi.arctracker.service.InstrumentType.CARD,
                        confidence = com.subhashrelangi.arctracker.service.IdentityConfidence.HIGH,
                        source = AccountSource.USER_CONFIRMED
                    ),
                    KnownFinancialAccount(
                        id = "icici_bank_account_1089",
                        institutionId = "icici",
                        institutionName = "ICICI Bank",
                        accountSuffix = "1089",
                        instrumentType = com.subhashrelangi.arctracker.service.InstrumentType.BANK_ACCOUNT,
                        confidence = com.subhashrelangi.arctracker.service.IdentityConfidence.HIGH,
                        source = AccountSource.USER_CONFIRMED
                    ),
                    KnownFinancialAccount(
                        id = "axis_card_3301",
                        institutionId = "axis",
                        institutionName = "Axis Bank Card",
                        accountSuffix = "3301",
                        instrumentType = com.subhashrelangi.arctracker.service.InstrumentType.CARD,
                        confidence = com.subhashrelangi.arctracker.service.IdentityConfidence.HIGH,
                        source = AccountSource.USER_CONFIRMED
                    )
                )
            )
        }

        // 2. Seed budget if none exist
        try {
            if (budgetDao.getAll().isEmpty()) {
                budgetDao.insert(
                    Budget(
                        id = UUID.randomUUID().toString(),
                        name = "Monthly Spending",
                        amountLimit = 80000.0,
                        periodType = "MONTHLY",
                        isEnabled = true
                    )
                )
            }
        } catch (_: Exception) {}

        // 3. Clear existing DEMO expenses and insert fresh full demo expenses
        val allExpenses = expenseDao.getAllExpensesList()
        if (allExpenses.isEmpty() || allExpenses.all { it.source == "DEMO" }) {
            expenseDao.clearAll()
            expenseDao.insertAll(getFullDemoExpenses())
        }
    }

    /**
     * Clears demo data from the Room database so real setup begins cleanly.
     */
    suspend fun clearDemoData(database: AppDatabase) {
        val expenseDao = database.expenseDao()
        val allExpenses = expenseDao.getAllExpensesList()
        val demoExpenses = allExpenses.filter { it.source == "DEMO" }
        for (item in demoExpenses) {
            try {
                expenseDao.delete(item)
            } catch (_: Exception) {}
        }
    }
}
