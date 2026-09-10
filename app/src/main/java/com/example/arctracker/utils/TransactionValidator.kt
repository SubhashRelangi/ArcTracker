package com.example.arctracker.utils

/**
 * Multi-check validation pipeline for notification-based expense capture.
 *
 * A notification is only accepted as a real transaction when ALL of these pass:
 *  1. It has actual text content.
 *  2. It does NOT look like a promotional / marketing blast.
 *  3. It is NOT noise (OTP, balance, login, loan offers, etc.).
 *  4. It contains an explicit debit OR credit synonym.
 *  5. A monetary amount can be parsed and is within a sane range.
 *  6. Essential details (amount, merchant, type) can be extracted.
 *
 * If any check fails, the notification is dropped entirely.
 */
object TransactionValidator {

    /** Debit synonyms — any one of these must appear for a Debit transaction. */
    val debitSynonyms = listOf(
        "paid", "paid to", "debited", "debited by", "deducted", "spent",
        "sent", "sent to", "payment of", "purchased", "purchase at",
        "withdrawn", "withdrawal", "card transaction", "upi transaction",
        "transfer to", "autopay", "emi", "charged"
    )

    /** Credit synonyms — any one of these must appear for a Credit transaction. */
    val creditSynonyms = listOf(
        "received", "received from", "credited", "credited to", "refund",
        "deposited", "deposit of", "money added", "added to wallet",
        "upi /cr", "/cr/", "cr from", "cash in"
    )

    /** Noise keywords — instant reject regardless of anything else. */
    private val noiseKeywords = listOf(
        "otp", "one time password", "code is", "available balance",
        "balance is", "bal is", "login", "signin", "sign in",
        "welcome", "verify", "blocked", "unblocked", "kyc",
        "limit changed", "statement", "mini statement", "loan offer"
    )

    /** Maximum plausible single-transaction amount (sanity bound). */
    private const val MAX_AMOUNT = 10_000_000.0 // 1 crore

    data class Validation(
        val accepted: Boolean,
        val reason: String,
        val parsed: ExpenseParser.ParsedExpense? = null
    )

    /**
     * Runs the full validation pipeline. Returns [Validation.accepted] = true only
     * when the notification is a genuine transaction with extractable details.
     */
    fun validate(text: String, title: String): Validation {
        val combined = "$title. $text".trim()
        val lower = combined.lowercase()

        // 1. Must have content
        if (combined.isBlank() || combined == ".") {
            return Validation(false, "Empty notification")
        }

        // 2. Promotional blasts are never transactions
        if (BankSenderFilter.isPromotional(combined)) {
            return Validation(false, "Promotional message")
        }

        // 3. Noise: OTPs, balances, logins, service messages
        noiseKeywords.firstOrNull { lower.contains(it) }?.let {
            return Validation(false, "Noise keyword: $it")
        }

        // 4. Must contain an explicit debit or credit synonym
        val hasDebit = debitSynonyms.any { lower.contains(it) }
        val hasCredit = creditSynonyms.any { lower.contains(it) }
        if (!hasDebit && !hasCredit) {
            return Validation(false, "No debit/credit synonym found")
        }

        // 5 & 6. Parse amount + merchant; parser returns null if no valid amount
        val parsed = ExpenseParser.parseExpenseData(text, title)
            ?: return Validation(false, "No parseable amount")

        if (parsed.amount <= 0.0) {
            return Validation(false, "Zero or negative amount")
        }
        if (parsed.amount > MAX_AMOUNT) {
            return Validation(false, "Implausible amount")
        }

        val type = when {
            hasCredit && !hasDebit -> "Credit"
            hasDebit && !hasCredit -> "Debit"
            else -> parsed.type // both present — trust the parser
        }

        return Validation(
            true,
            "Accepted",
            ExpenseParser.ParsedExpense(parsed.amount, parsed.merchant.ifBlank { "Unknown Merchant" }, type)
        )
    }
}