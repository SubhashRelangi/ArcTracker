package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Step 10: Notification Transaction Accuracy, Multi-Transaction Extraction,
 * Zero-Amount Prevention, and False Positive Rejection Test Suite.
 *
 * Verifies all 35 required scenarios from Step 10 specification.
 */
class NotificationAccuracyAndMultiTransactionTest {

    private lateinit var dao: FakeExpenseDao

    @Before
    fun setUp() {
        dao = FakeExpenseDao()
        TransactionPersistenceManager.persistenceListener = null
    }

    private fun createNotification(
        text: String,
        title: String = "Bank",
        packageName: String = "com.phonepe.app",
        key: String = "notif_key_${System.nanoTime()}",
        postTime: Long = 1700000000000L,
        textLines: List<String> = emptyList()
    ): CapturedNotificationInfo {
        return CapturedNotificationInfo(
            packageName = packageName,
            notificationKey = key,
            postTime = postTime,
            title = title,
            text = text,
            textLines = textLines
        )
    }

    // 1. Non-payment notification with ₹ amount (e.g. recharge offer / promo)
    @Test
    fun test01_nonPaymentNotification_withRupeeAmount_rejected() = runBlocking {
        val notif = createNotification("Recharge for ₹299 to get 1.5GB/day and unlimited calls")
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue("Non-payment recharge offer must be ignored", res is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, dao.getCount())
    }

    // 2. Balance-only notification
    @Test
    fun test02_balanceOnlyNotification_rejected() = runBlocking {
        val notif = createNotification("Available balance in A/c XX1234 is ₹12,345", packageName = "com.hdfc.bank")
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue("Balance-only inquiry must be ignored", res is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, dao.getCount())
    }

    // 3. Marketing notification containing payment words
    @Test
    fun test03_marketingNotification_containingPaymentWords_rejected() = runBlocking {
        val notif = createNotification("Send money to friends and win rewards! Cashback offer inside.")
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue("Promotional marketing must be ignored", res is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, dao.getCount())
    }

    // 4. Payment reminder
    @Test
    fun test04_paymentReminder_rejected() = runBlocking {
        val notif = createNotification("Reminder: Electricity bill of ₹1,200 is due on 30-Sep. Pay now to avoid late fee.")
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue("Payment reminder must be ignored", res is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, dao.getCount())
    }

    // 5. Loan promotion
    @Test
    fun test05_loanPromotion_rejected() = runBlocking {
        val notif = createNotification("Pre-approved personal loan up to ₹5,00,000 in your account. Apply now!", packageName = "com.csam.icici.bank.imobile")
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue("Loan promotion must be ignored", res is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, dao.getCount())
    }

    // 6. OTP/security notification
    @Test
    fun test06_otpNotification_rejected() = runBlocking {
        val notif = createNotification("Your OTP for payment of ₹500 at Amazon is 654321. Do not share it with anyone.")
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue("OTP message must be ignored", res is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, dao.getCount())
    }

    // 7. Zero amount
    @Test
    fun test07_zeroAmount_strictlyRejected_neverReachesRoom() = runBlocking {
        val notif = createNotification("Paid ₹0.00 to Merchant")
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue("Zero amount must be rejected", res is ExpensePersistenceResult.Rejected || res is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals("Zero amount must NEVER reach Room database", 0, dao.getCount())
    }

    // 8. Missing amount
    @Test
    fun test08_missingAmount_rejected_neverReachesRoom() = runBlocking {
        val notif = createNotification("Payment successful to Amazon")
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue("Missing amount must be rejected from persistence", res is ExpensePersistenceResult.Rejected || res is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals("Missing amount must NEVER reach Room database", 0, dao.getCount())
    }

    // 9. Negative/invalid amount
    @Test
    fun test09_negativeOrInvalidAmount_rejected_neverReachesRoom() = runBlocking {
        val candidate = StructuredTransactionCandidate(
            packageName = "com.phonepe.app",
            sourceNotificationKey = "neg_1",
            postTime = 1000L,
            amount = -50.0,
            merchant = "Store"
        )
        val validated = TransactionValidator.validate(candidate)
        assertTrue("Negative amount must fail validation", validated.isRejected)

        val res = TransactionPersistenceManager.processValidatedCandidate(validated, dao)
        assertTrue("Negative amount must be rejected by persistence manager", res is ExpensePersistenceResult.Rejected)
        assertEquals(0, dao.getCount())
    }

    // 10. Missing merchant
    @Test
    fun test10_missingMerchant_validTransaction_notFabricatedUnknownMerchant() = runBlocking {
        val notif = createNotification("Paid ₹500. UPI Ref 123456789012")
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue(res is ExpensePersistenceResult.Inserted)
        assertEquals(1, dao.getCount())
        val saved = dao.getAllExpensesList().first()
        assertEquals(500.0, saved.amount, 0.001)
        // Must NOT store synthetic "Unknown Merchant" as extracted data
        assertNotEquals("Unknown Merchant", saved.merchant)
        assertEquals("", saved.merchant)
    }

    // 11. Valid debit
    @Test
    fun test11_validDebit_correctDetailsExtracted() = runBlocking {
        val notif = createNotification("Paid ₹500 to Amazon using UPI Ref 123456789012")
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue(res is ExpensePersistenceResult.Inserted)
        assertEquals(1, dao.getCount())
        val saved = dao.getAllExpensesList().first()
        assertEquals(500.0, saved.amount, 0.001)
        assertEquals("Amazon", saved.merchant)
        assertEquals("Debit", saved.type)
        assertFalse(saved.isPending)
    }

    // 12. Valid credit
    @Test
    fun test12_validCredit_correctDetailsExtracted() = runBlocking {
        val notif = createNotification("₹1,000 received from Ravi. UPI Ref 987654321098")
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue(res is ExpensePersistenceResult.Inserted)
        assertEquals(1, dao.getCount())
        val saved = dao.getAllExpensesList().first()
        assertEquals(1000.0, saved.amount, 0.001)
        assertEquals("Ravi", saved.merchant)
        assertEquals("Credit", saved.type)
    }

    // 13. Failed transaction
    @Test
    fun test13_failedTransaction_doesNotBecomeNormalExpense() = runBlocking {
        val notif = createNotification("Payment of ₹500 to Swiggy failed. Amount not debited.")
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        // Failed status requires review or is not a confirmed completed expense
        if (res is ExpensePersistenceResult.Inserted) {
            assertTrue("Failed transaction must have isPending=true", res.isPending)
        } else {
            assertTrue(res is ExpensePersistenceResult.ReviewPending || res is ExpensePersistenceResult.Rejected || res is ExpensePersistenceResult.IgnoredNonFinancial)
        }
    }

    // 14. Pending transaction
    @Test
    fun test14_pendingTransaction_persistedWithIsPendingTrue() = runBlocking {
        val notif = createNotification("Payment of ₹500 to Swiggy is pending")
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue("Pending transaction must result in ReviewPending or Inserted(isPending=true)",
            res is ExpensePersistenceResult.ReviewPending || (res is ExpensePersistenceResult.Inserted && res.isPending))
        val saved = dao.getAllExpensesList().first()
        assertTrue("Pending transaction must be flagged isPending in database", saved.isPending)
        assertEquals(500.0, saved.amount, 0.001)
    }

    // 15. Reversed transaction
    @Test
    fun test15_reversedTransaction_requiresReview() = runBlocking {
        val notif = createNotification("Payment of ₹500 reversed by bank. Ref: 123456")
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        if (res is ExpensePersistenceResult.Inserted) {
            assertTrue(res.isPending)
        } else {
            assertTrue(res is ExpensePersistenceResult.ReviewPending || res is ExpensePersistenceResult.Rejected || res is ExpensePersistenceResult.IgnoredNonFinancial)
        }
    }

    // 16. Refunded transaction
    @Test
    fun test16_refundedTransaction_processedAsCredit() = runBlocking {
        val notif = createNotification("Refund of ₹450 from Zomato processed successfully")
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue(res is ExpensePersistenceResult.Inserted)
        val saved = dao.getAllExpensesList().first()
        assertEquals(450.0, saved.amount, 0.001)
        assertEquals("Credit", saved.type)
    }

    // 17. One notification with one transaction
    @Test
    fun test17_oneNotification_oneTransaction_singleCandidate() = runBlocking {
        val notif = createNotification("Paid ₹350 to Starbucks")
        val candidates = StructuredTransactionExtractor.extractAll(NotificationNormalizer.normalize(notif)!!)
        assertEquals(1, candidates.size)
        assertEquals(350.0, candidates[0].amount)
        assertEquals("Starbucks", candidates[0].merchant)
    }

    // 18. One notification with two transactions (PhonePe style)
    @Test
    fun test18_oneNotification_twoTransactions_extractsBothCandidates() = runBlocking {
        val notif = createNotification("Paid ₹500 to Amazon. Paid ₹250 to Flipkart.")
        val candidates = StructuredTransactionExtractor.extractAll(NotificationNormalizer.normalize(notif)!!)
        assertEquals(2, candidates.size)

        assertEquals(500.0, candidates[0].amount)
        assertEquals("Amazon", candidates[0].merchant)

        assertEquals(250.0, candidates[1].amount)
        assertEquals("Flipkart", candidates[1].merchant)

        // Process through persistence
        val results = TransactionPersistenceManager.processCapturedNotificationAll(notif, dao)
        assertEquals(2, results.size)
        assertEquals(2, dao.getCount())
    }

    // 19. One notification with three transactions
    @Test
    fun test19_oneNotification_threeTransactions_extractsAllThree() = runBlocking {
        val notif = createNotification("1. Paid ₹100 to Tea Stall. 2. Paid ₹200 to Bakery. 3. Paid ₹300 to Bookstore.")
        val candidates = StructuredTransactionExtractor.extractAll(NotificationNormalizer.normalize(notif)!!)
        assertEquals(3, candidates.size)

        assertEquals(100.0, candidates[0].amount)
        assertEquals(200.0, candidates[1].amount)
        assertEquals(300.0, candidates[2].amount)

        val results = TransactionPersistenceManager.processCapturedNotificationAll(notif, dao)
        assertEquals(3, results.size)
        assertEquals(3, dao.getCount())
    }

    // 20. Transaction + GST
    @Test
    fun test20_transactionWithGst_extractsTransactionAmountOnly() = runBlocking {
        val notif = createNotification("Paid ₹500 to Restaurant. GST ₹90. Total ₹590.")
        val candidates = StructuredTransactionExtractor.extractAll(NotificationNormalizer.normalize(notif)!!)
        assertEquals(1, candidates.size)
        assertEquals(500.0, candidates[0].amount)

        val taxSec = candidates[0].secondaryAmounts.find { it.type == SecondaryAmountType.TAX_OR_GST }
        assertNotNull("GST must be captured as secondary amount", taxSec)
        assertEquals(90.0, taxSec?.amount)
    }

    // 21. Transaction + cashback
    @Test
    fun test21_transactionWithCashback_extractsTransactionAmountOnly() = runBlocking {
        val notif = createNotification("Paid ₹500 at Starbucks. Cashback received: ₹50. Bal: ₹4,000")
        val candidates = StructuredTransactionExtractor.extractAll(NotificationNormalizer.normalize(notif)!!)
        assertEquals(1, candidates.size)
        assertEquals(500.0, candidates[0].amount)

        val cashback = candidates[0].secondaryAmounts.find { it.type == SecondaryAmountType.CASHBACK }
        assertNotNull(cashback)
        assertEquals(50.0, cashback?.amount)
    }

    // 22. Transaction + balance
    @Test
    fun test22_transactionWithBalance_extractsTransactionAmountOnly() = runBlocking {
        val notif = createNotification("₹500 debited for payment to Swiggy. Avl Bal: ₹8,500")
        val candidates = StructuredTransactionExtractor.extractAll(NotificationNormalizer.normalize(notif)!!)
        assertEquals(1, candidates.size)
        assertEquals(500.0, candidates[0].amount)

        val balance = candidates[0].secondaryAmounts.find { it.type == SecondaryAmountType.BALANCE }
        assertNotNull(balance)
        assertEquals(8500.0, balance?.amount)
    }

    // 23. Two transactions + total
    @Test
    fun test23_twoTransactionsWithTotal_extractsBothAndIgnoresTotalAsCandidate() = runBlocking {
        val notif = createNotification("Transaction 1: Paid ₹500 to Merchant A. Transaction 2: Paid ₹250 to Merchant B. Total ₹750.")
        val candidates = StructuredTransactionExtractor.extractAll(NotificationNormalizer.normalize(notif)!!)
        assertEquals(2, candidates.size)
        assertEquals(500.0, candidates[0].amount)
        assertEquals(250.0, candidates[1].amount)

        // Neither candidate should take 750
        assertTrue(candidates.none { it.amount == 750.0 })
    }

    // 24. Two transactions with different merchants
    @Test
    fun test24_twoTransactions_differentMerchants() = runBlocking {
        val notif = createNotification("Paid ₹500 to Amazon. Paid ₹250 to Flipkart.")
        val candidates = StructuredTransactionExtractor.extractAll(NotificationNormalizer.normalize(notif)!!)
        assertEquals("Amazon", candidates[0].merchant)
        assertEquals("Flipkart", candidates[1].merchant)
    }

    // 25. Two transactions with different references
    @Test
    fun test25_twoTransactions_differentReferences() = runBlocking {
        val notif = createNotification("Paid ₹500 to A Ref: UTR123456789012. Paid ₹250 to B Ref: UTR987654321098.")
        val candidates = StructuredTransactionExtractor.extractAll(NotificationNormalizer.normalize(notif)!!)
        assertEquals(2, candidates.size)
        assertEquals("UTR123456789012", candidates[0].referenceId)
        assertEquals("UTR987654321098", candidates[1].referenceId)
    }

    // 26. Notification update processing → successful
    @Test
    fun test26_notificationUpdate_pendingToSuccess() = runBlocking {
        val pendingNotif = createNotification("Payment of ₹600 to Swiggy is pending", key = "update_flow_1")
        val res1 = TransactionPersistenceManager.processCapturedNotification(pendingNotif, dao)
        assertEquals(1, dao.getCount())
        assertTrue(dao.getAllExpensesList().first().isPending)

        val successNotif = createNotification("Payment of ₹600 to Swiggy successful", key = "update_flow_1")
        val res2 = TransactionPersistenceManager.processCapturedNotification(successNotif, dao)
        assertTrue(res2 is ExpensePersistenceResult.Updated)
        assertEquals("Must update existing row, not insert a duplicate", 1, dao.getCount())
        assertFalse("Updated expense must no longer be pending", dao.getAllExpensesList().first().isPending)
    }

    // 27. Notification + SMS duplicate (correlation)
    @Test
    fun test27_notificationPlusSmsDuplicate_correlatesToOneRecord() = runBlocking {
        val notif = createNotification("₹800 paid to Zomato. UPI Ref: 112233445566", key = "notif_corr_1")
        TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertEquals(1, dao.getCount())

        val smsRes = TransactionPersistenceManager.processSms(
            smsId = "sms_1001",
            sender = "HDFCBK",
            body = "Rs 800 debited from A/c XX1234 to Zomato. UPI Ref 112233445566",
            timestamp = 1700000010000L,
            dao = dao
        )
        assertTrue(smsRes is ExpensePersistenceResult.Enriched || smsRes is ExpensePersistenceResult.Updated || smsRes is ExpensePersistenceResult.SkippedDuplicate)
        assertEquals("Notification + SMS with matching UTR must correlate to 1 row", 1, dao.getCount())
    }

    // 28. Same amount in two different transactions
    @Test
    fun test28_sameAmount_twoDifferentTransactions_bothPersisted() = runBlocking {
        val notif = createNotification("Paid ₹500 to Merchant A. Paid ₹500 to Merchant B.")
        val results = TransactionPersistenceManager.processCapturedNotificationAll(notif, dao)
        assertEquals(2, results.size)
        assertEquals(2, dao.getCount())
        val expenses = dao.getAllExpensesList()
        assertEquals(500.0, expenses[0].amount, 0.001)
        assertEquals(500.0, expenses[1].amount, 0.001)
        assertNotEquals(expenses[0].merchant, expenses[1].merchant)
    }

    // 29. Same merchant with two different transactions
    @Test
    fun test29_sameMerchant_twoDifferentTransactions_bothPersisted() = runBlocking {
        val notif = createNotification("Paid ₹500 to Amazon. Paid ₹300 to Amazon.")
        val results = TransactionPersistenceManager.processCapturedNotificationAll(notif, dao)
        assertEquals(2, results.size)
        assertEquals(2, dao.getCount())
        val expenses = dao.getAllExpensesList()
        assertTrue(expenses.any { it.amount == 500.0 })
        assertTrue(expenses.any { it.amount == 300.0 })
    }

    // 30. Unknown sender but valid transaction
    @Test
    fun test30_unknownSender_validTransaction_processedSuccessfully() = runBlocking {
        val notif = createNotification("Paid ₹750 to Myntra. Txn ID: 998877", packageName = "com.custom.finapp")
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue(res is ExpensePersistenceResult.Inserted)
        assertEquals(1, dao.getCount())
        assertEquals(750.0, dao.getAllExpensesList().first().amount, 0.001)
    }

    // 31. Known payment app but promotional notification
    @Test
    fun test31_knownPaymentApp_promotionalNotification_rejected() = runBlocking {
        val notif = createNotification("Invite friends and earn up to ₹500 cashback on PhonePe!", packageName = "com.phonepe.app")
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue(res is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, dao.getCount())
    }

    // 32. App notification with no financial transaction
    @Test
    fun test32_appNotification_noFinancialTransaction_ignored() = runBlocking {
        val notif = createNotification("Hey, are you free for lunch tomorrow?", packageName = "com.whatsapp")
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue(res is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, dao.getCount())
    }

    // 33. Malformed amount text
    @Test
    fun test33_malformedAmountText_rejected() = runBlocking {
        val notif = createNotification("Paid ₹abc to Store")
        val res = TransactionPersistenceManager.processCapturedNotification(notif, dao)
        assertTrue(res is ExpensePersistenceResult.Rejected || res is ExpensePersistenceResult.IgnoredNonFinancial)
        assertEquals(0, dao.getCount())
    }

    // 34. Currency formatting variants
    @Test
    fun test34_currencyFormattingVariants_parsedCorrectly() = runBlocking {
        val variants = listOf(
            "Paid Rs 1,500.50 to Store A" to 1500.50,
            "Paid INR 500 to Store B" to 500.0,
            "Paid ₹ 200.00 to Store C" to 200.0,
            "500 Rupees paid to Store D" to 500.0,
            "300 INR paid to Store E" to 300.0
        )

        for ((text, expectedAmount) in variants) {
            val notif = createNotification(text)
            val candidate = StructuredTransactionExtractor.extract(NotificationNormalizer.normalize(notif)!!)
            assertNotNull("Failed parsing variant: $text", candidate)
            assertEquals("Incorrect amount for variant: $text", expectedAmount, candidate?.amount ?: 0.0, 0.001)
        }
    }

    // 35. UTR/RRN/reference extraction
    @Test
    fun test35_referenceExtraction_utrRrnUpiRef() = runBlocking {
        val utrNotif = createNotification("Paid ₹500 to Ravi. UTR: 123456789012")
        val utrCand = StructuredTransactionExtractor.extract(NotificationNormalizer.normalize(utrNotif)!!)
        assertEquals("123456789012", utrCand?.utr)

        val rrnNotif = createNotification("Paid ₹500 to Ravi. RRN: 987654321012")
        val rrnCand = StructuredTransactionExtractor.extract(NotificationNormalizer.normalize(rrnNotif)!!)
        assertEquals("987654321012", rrnCand?.rrn)

        val upiRefNotif = createNotification("Paid ₹500 to Ravi. UPI Ref No: 554433221100")
        val upiCand = StructuredTransactionExtractor.extract(NotificationNormalizer.normalize(upiRefNotif)!!)
        assertEquals("554433221100", upiCand?.upiTransactionId)
    }
}
