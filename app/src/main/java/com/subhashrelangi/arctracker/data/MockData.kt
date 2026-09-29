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
}
