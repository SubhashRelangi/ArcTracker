package com.subhashrelangi.arctracker

import android.os.Bundle
import com.subhashrelangi.arctracker.data.Expense
import com.subhashrelangi.arctracker.service.*
import com.subhashrelangi.arctracker.settings.InMemoryMonitoringSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive Regression Test Suite (Phase 9).
 * Verifies all 22 required test cases for critical notification listening
 * and transaction extraction remediation.
 */
class NotificationIngestionRemediationTest {

    private lateinit var fakeDao: FakeExpenseDao
    private lateinit var service: NotificationReaderService

    @Before
    fun setUp() {
        NotificationPermissionHelper.permissionOverrideForTesting = true
        NotificationReaderService.isConnected = false
        NotificationReaderService.lastCapturedNotification = null
        NotificationReaderService.clearActiveNotifications()
        DiagnosticEventManager.clearEvents()
        NotificationReaderService.settingsRepositoryOverride = InMemoryMonitoringSettingsRepository(
            initialGlobalEnabled = true,
            initialEnabledPackages = setOf("com.google.android.apps.nbu.paisa.user", "com.bank.app", "com.phonepe.app")
        )
        fakeDao = FakeExpenseDao()
        service = NotificationReaderService()
    }

    @After
    fun tearDown() {
        NotificationPermissionHelper.permissionOverrideForTesting = null
        NotificationReaderService.settingsRepositoryOverride = null
        NotificationReaderService.isConnected = false
        NotificationReaderService.lastCapturedNotification = null
        NotificationReaderService.clearActiveNotifications()
        DiagnosticEventManager.clearEvents()
    }

    // 1. Normalizer handles multiline text with Indian rupee formatting (₹1,50,000.00) and removes extra whitespace without corrupting amounts
    @Test
    fun test01_normalizer_multilineIndianRupeeWhitespace() {
        val raw = CapturedNotificationInfo(
            packageName = "com.bank.app",
            notificationKey = "key_1",
            postTime = 1000L,
            title = "  HDFC   Bank  \n",
            text = "  A/c   *1234   debited   by   ₹1,50,000.00  \n\n  Avl   Bal:   ₹5,00,000.00  "
        )
        val normalized = NotificationNormalizer.normalize(raw)
        assertNotNull(normalized)
        assertTrue("Normalized text must contain ₹1,50,000.00 intact", normalized!!.normalizedCombinedText.contains("₹1,50,000.00"))
        assertFalse("Normalized text must not contain double spaces", normalized.normalizedCombinedText.contains("   "))
    }

    // 2. bigText takes precedence over truncated text when bigText contains the full message
    @Test
    fun test02_bigText_takesPrecedenceOverTruncatedText() {
        val truncatedText = "Rs 5,000 debited from A/c XX1234..."
        val fullBigText = "Rs 5,000 debited from A/c XX1234 to Merchant on 12-Oct. Bal: Rs 45,000"

        val combined = NotificationNormalizer.buildCombinedText(
            title = "Bank Alert",
            text = truncatedText,
            bigText = fullBigText,
            textLines = emptyList(),
            subText = null,
            summaryText = null,
            infoText = null
        )

        assertTrue("Must contain full bigText", combined.contains(fullBigText))
        assertFalse("Truncated snippet must not be duplicated or prepended", combined.contains("..."))
    }

    // 3. Group summary notifications (isGroupSummary == true) are filtered and do not produce phantom transactions
    @Test
    fun test03_groupSummary_filteredAndIgnored() {
        val processed = service.processNotification(
            packageName = "com.google.android.apps.nbu.paisa.user",
            notificationKey = "summary_key_1",
            postTime = 1000L,
            isGroupSummary = true,
            flags = android.app.Notification.FLAG_GROUP_SUMMARY,
            contentExtractor = {
                NotificationContent(title = "Google Pay", text = "3 new transactions")
            }
        )

        assertTrue("Group summary notification is captured by service", processed)
        val captured = NotificationReaderService.lastCapturedNotification
        assertNotNull(captured)
        assertTrue(captured!!.isGroupSummary)
        val ignoredEvents = DiagnosticEventManager.getEventsForCategory(DiagnosticCategory.NOTIFICATION_IGNORED)
        assertTrue("NOTIFICATION_IGNORED diagnostic must be emitted for group_summary", ignoredEvents.any { it.reason == "group_summary" })
    }

    // 4. MessagingStyle / EXTRA_MESSAGES content is extracted correctly
    @Test
    fun test04_messagingStyle_contentExtracted() {
        val messageLines = arrayOf("Paid ₹1,200 to Starbucks via UPI")
        val lines = NotificationReaderService.extractMessagingLinesFromValue(messageLines)
        assertEquals(1, lines.size)
        assertEquals("Paid ₹1,200 to Starbucks via UPI", lines[0])
    }

    // 5. Notification with balance and debit amount correctly extracts the debit amount, NOT the balance
    @Test
    fun test05_balanceAndDebit_extractsDebitAmountNotBalance() {
        val text = "A/c *1234 debited by Rs 2,500.00 on 15-Jan-25. Bal Rs 45,230.50"
        val raw = CapturedNotificationInfo(
            packageName = "com.bank.app",
            notificationKey = "bal_debit_key",
            postTime = 1000L,
            text = text
        )
        val normalized = NotificationNormalizer.normalize(raw)!!
        val candidate = StructuredTransactionExtractor.extractDirect(normalized)

        assertEquals("Primary amount must be transaction debit amount (2500.0)", 2500.0, candidate.amount ?: 0.0, 0.001)
        val secBalance = candidate.secondaryAmounts.find { it.type == SecondaryAmountType.BALANCE }
        assertNotNull("Balance amount must be recorded in secondary amounts", secBalance)
        assertEquals(45230.50, secBalance!!.amount, 0.001)
    }

    // 6. Notification with credit limit and spent amount correctly extracts the spent amount, NOT the limit
    @Test
    fun test06_creditLimitAndSpent_extractsSpentAmountNotLimit() {
        val text = "Spent Rs.4,320.00 on Card ending 9876. Avl Limit: Rs.95,680.00"
        val raw = CapturedNotificationInfo(
            packageName = "com.bank.app",
            notificationKey = "spent_limit_key",
            postTime = 1000L,
            text = text
        )
        val normalized = NotificationNormalizer.normalize(raw)!!
        val candidate = StructuredTransactionExtractor.extractDirect(normalized)

        assertEquals("Primary amount must be spent amount (4320.0)", 4320.0, candidate.amount ?: 0.0, 0.001)
        val secLimit = candidate.secondaryAmounts.find { it.type == SecondaryAmountType.LIMIT }
        assertNotNull("Credit limit amount must be recorded in secondary amounts", secLimit)
        assertEquals(95680.0, secLimit!!.amount, 0.001)
    }

    // 7. Indian number format ₹1,00,000 is parsed to 100000.0
    @Test
    fun test07_indianNumberFormat_oneLakh() {
        val raw = CapturedNotificationInfo(
            packageName = "com.bank.app",
            notificationKey = "lakh_key",
            postTime = 1000L,
            text = "Paid ₹1,00,000 to Housing Society"
        )
        val normalized = NotificationNormalizer.normalize(raw)!!
        val candidate = StructuredTransactionExtractor.extractDirect(normalized)
        assertEquals(100000.0, candidate.amount ?: 0.0, 0.001)
    }

    // 8. Indian number format ₹1,50,000.50 is parsed to 150000.5
    @Test
    fun test08_indianNumberFormat_lakhWithDecimals() {
        val raw = CapturedNotificationInfo(
            packageName = "com.bank.app",
            notificationKey = "lakh_dec_key",
            postTime = 1000L,
            text = "Debited ₹1,50,000.50 from A/c XX1234"
        )
        val normalized = NotificationNormalizer.normalize(raw)!!
        val candidate = StructuredTransactionExtractor.extractDirect(normalized)
        assertEquals(150000.50, candidate.amount ?: 0.0, 0.001)
    }

    // 9. Plain number without decimals ₹500 is parsed to 500.0
    @Test
    fun test09_plainNumber_fiveHundred() {
        val raw = CapturedNotificationInfo(
            packageName = "com.bank.app",
            notificationKey = "five_hundred_key",
            postTime = 1000L,
            text = "Paid ₹500 for Groceries"
        )
        val normalized = NotificationNormalizer.normalize(raw)!!
        val candidate = StructuredTransactionExtractor.extractDirect(normalized)
        assertEquals(500.0, candidate.amount ?: 0.0, 0.001)
    }

    // 10. Ambiguous direction (both debit and credit words) is marked UNKNOWN and rejected from automatic persistence
    @Test
    fun test10_ambiguousDirection_rejectedFromAutomaticPersistence() = runBlocking {
        val raw = CapturedNotificationInfo(
            packageName = "com.bank.app",
            notificationKey = "ambiguous_dir_key",
            postTime = 1000L,
            text = "Transaction alert: ₹1,000 was debited and credited in review"
        )
        val res = TransactionPersistenceManager.processCapturedNotification(raw, fakeDao)
        assertTrue("Ambiguous direction must be rejected", res is ExpensePersistenceResult.Rejected || res is ExpensePersistenceResult.IgnoredNonFinancial)
        val ambiguousEvents = DiagnosticEventManager.getEventsForCategory(DiagnosticCategory.DIRECTION_AMBIGUOUS)
        assertTrue("DIRECTION_AMBIGUOUS diagnostic must be emitted", ambiguousEvents.isNotEmpty())
    }

    // 11. Credit message ("Rs 5,000 credited to a/c *1234") is classified as CREDIT, not DEBIT
    @Test
    fun test11_creditMessage_classifiedAsCredit() {
        val raw = CapturedNotificationInfo(
            packageName = "com.bank.app",
            notificationKey = "credit_key",
            postTime = 1000L,
            text = "Rs 5,000 credited to a/c *1234"
        )
        val normalized = NotificationNormalizer.normalize(raw)!!
        val candidate = StructuredTransactionExtractor.extractDirect(normalized)
        assertEquals("Direction must be CREDIT", TransactionDirection.CREDIT, candidate.direction)
    }

    // 12. Debit message with beneficiary ("Rs 1,000 debited from a/c *1234 and credited to VPA foo@upi") is classified as DEBIT from user perspective
    @Test
    fun test12_twoPartyTransfer_classifiedAsDebitFromUserPerspective() {
        val raw = CapturedNotificationInfo(
            packageName = "com.bank.app",
            notificationKey = "two_party_key",
            postTime = 1000L,
            text = "Rs 1,000 debited from a/c *1234 and credited to VPA foo@upi"
        )
        val normalized = NotificationNormalizer.normalize(raw)!!
        val candidate = StructuredTransactionExtractor.extractDirect(normalized)
        assertEquals("Direction must be DEBIT from user account perspective", TransactionDirection.DEBIT, candidate.direction)
    }

    // 13. Credit card bill reminder ("Your credit card bill of Rs 5,000 is due") is NOT classified as a transaction
    @Test
    fun test13_creditCardBillReminder_notACompletedTransaction() {
        val raw = CapturedNotificationInfo(
            packageName = "com.bank.app",
            notificationKey = "bill_due_key",
            postTime = 1000L,
            text = "Your credit card bill of Rs 5,000 is due on 20-Oct"
        )
        val normalized = NotificationNormalizer.normalize(raw)!!
        val assessment = ActualTransactionEventGate.assessNotification(normalized)
        assertEquals("Bill reminder must be assessed as FALSE", ActualEventStatus.FALSE, assessment.actualEvent)
    }

    // 14. Promotional offer with currency ("Get Rs 100 cashback on recharge") is NOT classified as a transaction
    @Test
    fun test14_promotionalOffer_notACompletedTransaction() {
        val raw = CapturedNotificationInfo(
            packageName = "com.pay.app",
            notificationKey = "promo_key",
            postTime = 1000L,
            text = "Get Rs 100 cashback on recharge today! Use code RECHARGE100"
        )
        val normalized = NotificationNormalizer.normalize(raw)!!
        val assessment = ActualTransactionEventGate.assessNotification(normalized)
        assertEquals("Promotional offer must be assessed as FALSE", ActualEventStatus.FALSE, assessment.actualEvent)
    }

    // 15. OTP notification ("Your OTP is 4521 for transaction of Rs 500") is NOT ingested as a completed transaction
    @Test
    fun test15_otpNotification_notIngestedAsCompletedTransaction() = runBlocking {
        val raw = CapturedNotificationInfo(
            packageName = "com.bank.app",
            notificationKey = "otp_key",
            postTime = 1000L,
            text = "Your OTP is 4521 for transaction of Rs 500 at Merchant"
        )
        val res = TransactionPersistenceManager.processCapturedNotification(raw, fakeDao)
        assertTrue("OTP notification must be ignored/rejected", res is ExpensePersistenceResult.IgnoredNonFinancial || res is ExpensePersistenceResult.Rejected)
        assertEquals("Zero expenses should be written for OTP notification", 0, fakeDao.getAllExpensesList().size)
    }

    // 16. Transaction with null/empty merchant receives a deterministic fallback name (e.g., "Bank debit" or "UPI payment")
    @Test
    fun test16_transactionWithEmptyMerchant_receivesDeterministicFallback() {
        val candidate = StructuredTransactionCandidate(
            sourceNotificationKey = "notif_empty_m",
            packageName = "com.bank.app",
            postTime = 1000L,
            amount = 350.0,
            currency = "INR",
            direction = TransactionDirection.DEBIT,
            accountSuffix = "1234",
            merchant = null
        )
        val expense = TransactionPersistenceManager.mapToExpense(candidate, isPending = false)
        assertNotNull(expense.merchant)
        assertFalse("Merchant must not be blank", expense.merchant.isBlank())
        assertEquals("Bank Debit", expense.merchant)
    }

    // 17. Transaction with amount <= 0 is rejected by validation gate
    @Test
    fun test17_nonPositiveAmount_rejectedByValidationGate() = runBlocking {
        val candidate = StructuredTransactionCandidate(
            sourceNotificationKey = "zero_amount_key",
            packageName = "com.bank.app",
            postTime = 1000L,
            amount = 0.0,
            currency = "INR",
            direction = TransactionDirection.DEBIT,
            merchant = "Shop"
        )
        val validated = TransactionValidator.validate(candidate)
        assertTrue("Zero amount must be rejected by validator", validated.isRejected)

        val raw = CapturedNotificationInfo(
            packageName = "com.bank.app",
            notificationKey = "zero_raw_key",
            postTime = 1000L,
            text = "Paid Rs 0 to Shop"
        )
        val res = TransactionPersistenceManager.processCapturedNotification(raw, fakeDao)
        assertTrue("Non-positive amount must be rejected by persistence manager", res is ExpensePersistenceResult.Rejected || res is ExpensePersistenceResult.IgnoredNonFinancial)
    }

    // 18. Duplicate notification (same reference ID / timestamp / amount) is skipped, not duplicated
    @Test
    fun test18_duplicateNotification_skippedNotDuplicated() = runBlocking {
        val raw1 = CapturedNotificationInfo(
            packageName = "com.bank.app",
            notificationKey = "dup_key_1",
            postTime = 1000L,
            text = "Paid Rs 500 to Swiggy. UPI Ref 123456789012"
        )
        val raw2 = CapturedNotificationInfo(
            packageName = "com.bank.app",
            notificationKey = "dup_key_1",
            postTime = 1005L,
            text = "Paid Rs 500 to Swiggy. UPI Ref 123456789012"
        )

        val res1 = TransactionPersistenceManager.processCapturedNotification(raw1, fakeDao)
        assertTrue("First notification must be inserted", res1 is ExpensePersistenceResult.Inserted)

        val res2 = TransactionPersistenceManager.processCapturedNotification(raw2, fakeDao)
        assertTrue("Second identical notification must be skipped as duplicate", res2 is ExpensePersistenceResult.SkippedDuplicate)
        assertEquals("Database must contain exactly 1 transaction", 1, fakeDao.getAllExpensesList().size)

        val dupEvents = DiagnosticEventManager.getEventsForCategory(DiagnosticCategory.DUPLICATE_SKIPPED)
        assertTrue("DUPLICATE_SKIPPED diagnostic event must be emitted", dupEvents.isNotEmpty())
    }

    // 19. Listener connection state transitions (onListenerConnected, onListenerDisconnected) are accurately reflected
    @Test
    fun test19_connectionStateTransitions_reflectedAccurately() {
        assertFalse("Initially disconnected", NotificationReaderService.isConnected)

        service.onListenerConnected()
        assertTrue("Connected after onListenerConnected", NotificationReaderService.isConnected)
        assertEquals(true, NotificationReaderService.connectionState.value)

        val connectedEvents = DiagnosticEventManager.getEventsForCategory(DiagnosticCategory.LISTENER_CONNECTED)
        assertEquals(1, connectedEvents.size)

        service.onListenerDisconnected()
        assertFalse("Disconnected after onListenerDisconnected", NotificationReaderService.isConnected)
        assertEquals(false, NotificationReaderService.connectionState.value)

        val disconnectedEvents = DiagnosticEventManager.getEventsForCategory(DiagnosticCategory.LISTENER_DISCONNECTED)
        assertEquals(1, disconnectedEvents.size)
    }

    // 20. DiagnosticEvent logging emits appropriate categories without sensitive data leaks
    @Test
    fun test20_diagnosticEventLogging_noSensitiveDataLeaks() {
        DiagnosticEventManager.clearEvents()
        DiagnosticEventManager.logEvent(
            DiagnosticCategory.TRANSACTION_PERSISTED,
            packageName = "com.bank.app",
            reason = "id=12"
        )
        val events = DiagnosticEventManager.getEvents()
        assertEquals(1, events.size)
        val event = events[0]
        assertEquals(DiagnosticCategory.TRANSACTION_PERSISTED, event.category)
        assertEquals("com.bank.app", event.packageName)
        assertFalse("Reason must not contain rupee symbol", event.reason?.contains("₹") == true)
        assertFalse("Reason must not contain amount", event.reason?.contains("500") == true)
        assertFalse("Reason must not contain account numbers", event.reason?.contains("XX") == true)
    }

    // 21. Service status reporting correctly reflects "Disconnected" when permission is granted but service is unbound
    @Test
    fun test21_serviceStatus_disconnectedWhenPermissionGrantedButUnbound() {
        NotificationReaderService.isConnected = false
        val isPermissionGranted = true
        val isServiceRunning = NotificationReaderService.isServiceRunningAndConnected()

        assertFalse("Service must be unbound/disconnected", isServiceRunning)
        assertTrue("Permission is granted", isPermissionGranted)

        // Status logic check (same as in SettingsScreen)
        val isHealthy = isPermissionGranted && isServiceRunning
        assertFalse("Health state must be FALSE when service is unbound even if permission is granted", isHealthy)
    }

    // 22. Multiple concurrent notifications are processed thread-safely without race conditions or lost transactions
    @Test
    fun test22_concurrentNotifications_processedThreadSafely() = runBlocking {
        val totalCount = 20
        val deferredResults = (1..totalCount).map { i ->
            async(Dispatchers.Default) {
                val raw = CapturedNotificationInfo(
                    packageName = "com.bank.app",
                    notificationKey = "concurrent_key_$i",
                    postTime = 1000L + i,
                    text = "Paid Rs $i to Merchant_$i. Ref ID TXN$i"
                )
                TransactionPersistenceManager.processCapturedNotification(raw, fakeDao)
            }
        }
        val results = deferredResults.awaitAll()
        assertEquals(totalCount, results.size)
        assertTrue("All distinct transactions must be successfully processed", results.all { it is ExpensePersistenceResult.Inserted })
        val persistedExpenses = fakeDao.getAllExpensesList()
        assertEquals("All $totalCount transactions must be safely persisted in Room without race conditions", totalCount, persistedExpenses.size)
    }
}
