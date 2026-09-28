package com.example.arctracker

import com.example.arctracker.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests covering the complete Test Matrix (Cases A through Y) for the
 * Evidence-Based Actual Transaction Event Gate (Step 4.5).
 */
class ActualTransactionEventGateTest {

    private lateinit var fakeDao: FakeExpenseDao

    @Before
    fun setUp() {
        fakeDao = FakeExpenseDao()
    }

    private fun createNotification(
        text: String,
        packageName: String = "net.one97.paytm",
        title: String? = null
    ): NormalizedNotification {
        val captured = CapturedNotificationInfo(
            packageName = packageName,
            notificationKey = "key_${System.currentTimeMillis()}_${text.hashCode()}",
            postTime = 1711360000000L,
            title = title,
            text = text
        )
        return NotificationNormalizer.normalize(captured)!!
    }

    // =========================================================================
    // Case A: Completed Debit Action
    // =========================================================================
    @Test
    fun `Case A - Completed Debit action paid to merchant`() {
        val text = "Paid ₹500 to Swiggy"
        val assessment = ActualTransactionEventGate.assess(text)
        assertEquals(ActualEventStatus.TRUE, assessment.actualEvent)
        assertEquals(TransactionEventType.COMPLETED, assessment.eventType)
        assertEquals(TransactionDirection.DEBIT, assessment.directionHint)
    }

    // =========================================================================
    // Case B: Completed Debit with Account & Date
    // =========================================================================
    @Test
    fun `Case B - Account debited with currency and date`() {
        val text = "A/c *1234 debited by INR 1,200 on 25-Sep-26"
        val assessment = ActualTransactionEventGate.assess(text)
        assertEquals(ActualEventStatus.TRUE, assessment.actualEvent)
        assertEquals(TransactionEventType.COMPLETED, assessment.eventType)
        assertEquals(TransactionDirection.DEBIT, assessment.directionHint)
    }

    // =========================================================================
    // Case C: Completed Credit
    // =========================================================================
    @Test
    fun `Case C - Amount received from counterparty`() {
        val text = "Rs. 2,000 received from John"
        val assessment = ActualTransactionEventGate.assess(text)
        assertEquals(ActualEventStatus.TRUE, assessment.actualEvent)
        assertEquals(TransactionEventType.COMPLETED, assessment.eventType)
        assertEquals(TransactionDirection.CREDIT, assessment.directionHint)
    }

    // =========================================================================
    // Case D: Cashback Credited
    // =========================================================================
    @Test
    fun `Case D - Cashback credited to account`() {
        val text = "Cashback of ₹50 credited to your account"
        val assessment = ActualTransactionEventGate.assess(text)
        assertEquals(ActualEventStatus.TRUE, assessment.actualEvent)
        assertEquals(TransactionEventType.COMPLETED, assessment.eventType)
        assertEquals(TransactionDirection.CREDIT, assessment.directionHint)
    }

    // =========================================================================
    // Case E: Refund Credited
    // =========================================================================
    @Test
    fun `Case E - Refund processed`() {
        val text = "Refund of ₹399 processed"
        val assessment = ActualTransactionEventGate.assess(text)
        assertEquals(ActualEventStatus.TRUE, assessment.actualEvent)
        assertEquals(TransactionEventType.COMPLETED, assessment.eventType)
        assertEquals(TransactionDirection.CREDIT, assessment.directionHint)
    }

    // =========================================================================
    // Case F: Status Success
    // =========================================================================
    @Test
    fun `Case F - Payment successful`() {
        val text = "Payment of ₹150 to Uber successful"
        val assessment = ActualTransactionEventGate.assess(text)
        assertEquals(ActualEventStatus.TRUE, assessment.actualEvent)
        assertEquals(TransactionEventType.COMPLETED, assessment.eventType)
        assertEquals(TransactionDirection.DEBIT, assessment.directionHint)
    }

    // =========================================================================
    // Case G: Status Failed
    // =========================================================================
    @Test
    fun `Case G - Payment failed`() {
        val text = "Payment of ₹750 to Zomato failed"
        val assessment = ActualTransactionEventGate.assess(text)
        assertEquals(ActualEventStatus.TRUE, assessment.actualEvent)
        assertEquals(TransactionEventType.FAILED, assessment.eventType)
    }

    // =========================================================================
    // Case H: Status Pending
    // =========================================================================
    @Test
    fun `Case H - Payment pending`() {
        val text = "Payment of ₹1,000 to Amazon pending"
        val assessment = ActualTransactionEventGate.assess(text)
        assertEquals(ActualEventStatus.TRUE, assessment.actualEvent)
        assertEquals(TransactionEventType.PENDING, assessment.eventType)
    }

    // =========================================================================
    // Case I: Status Reversed
    // =========================================================================
    @Test
    fun `Case I - Payment reversed`() {
        val text = "Payment of ₹500 reversed"
        val assessment = ActualTransactionEventGate.assess(text)
        assertEquals(ActualEventStatus.TRUE, assessment.actualEvent)
        assertEquals(TransactionEventType.REVERSED, assessment.eventType)
        assertEquals(TransactionDirection.UNKNOWN, assessment.directionHint)
    }

    // =========================================================================
    // Case J: Real Production False Positive (Paytm Credit Card Promo)
    // =========================================================================
    @Test
    fun `Case J - Production False Positive Paytm promo yields 0 candidates and 0 Room inserts`() = runBlocking {
        val promoText = "₹2,000 Is One Bill Away! 🚀\nPay your Credit Card Bill on Paytm with No Extra Fees & get a chance to win ₹2,000 cashback. Use CCBP2000.\nNo Fees"
        val notif = createNotification(promoText, packageName = "net.one97.paytm")

        // 1. Event Gate Assessment directly
        val assessment = ActualTransactionEventGate.assessNotification(notif)
        assertEquals(ActualEventStatus.FALSE, assessment.actualEvent)
        assertTrue(
            assessment.eventType == TransactionEventType.FUTURE_ACTION ||
                    assessment.eventType == TransactionEventType.PROMOTIONAL
        )

        // 2. Extraction Pipeline yields 0 candidates
        val candidates = StructuredTransactionExtractor.extractAll(notif)
        assertTrue("Promotional message must extract 0 candidates, but got: ${candidates.size}", candidates.isEmpty())

        // 3. Single extract helper returns null
        val singleCandidate = StructuredTransactionExtractor.extract(notif)
        assertNull("Extract must return null for promotional message", singleCandidate)

        // 4. Persistence Pipeline via Captured Notification: Ignored
        val captured = CapturedNotificationInfo(
            packageName = "net.one97.paytm",
            notificationKey = "test_promo_key",
            postTime = 1711360000000L,
            title = "Promotional",
            text = promoText
        )
        val pipelineResult = TransactionPersistenceManager.processCapturedNotification(captured, fakeDao)
        assertTrue(pipelineResult is ExpensePersistenceResult.IgnoredNonFinancial)

        // 5. Zero Room Database rows
        val allExpenses = fakeDao.getAllExpensesList()
        assertEquals(0, allExpenses.size)
        val pendingExpenses = fakeDao.getPendingExpensesList()
        assertEquals(0, pendingExpenses.size)
    }

    // =========================================================================
    // Case K: Future Action / Bill Payment Command
    // =========================================================================
    @Test
    fun `Case K - Future action pay bill before date`() {
        val text = "Pay your electricity bill of ₹1,200 before 30-Sep"
        val assessment = ActualTransactionEventGate.assess(text)
        assertEquals(ActualEventStatus.FALSE, assessment.actualEvent)
        assertEquals(TransactionEventType.FUTURE_ACTION, assessment.eventType)
    }

    // =========================================================================
    // Case L: Promotional Contest / Incentive
    // =========================================================================
    @Test
    fun `Case L - Promotional contest chance to win cashback`() {
        val text = "Pay on Paytm and get a chance to win ₹5,000 cashback"
        val assessment = ActualTransactionEventGate.assess(text)
        assertEquals(ActualEventStatus.FALSE, assessment.actualEvent)
        assertTrue(
            assessment.eventType == TransactionEventType.PROMOTIONAL ||
                    assessment.eventType == TransactionEventType.FUTURE_ACTION
        )
    }

    // =========================================================================
    // Case M: Promotional Reward / Scratch Card
    // =========================================================================
    @Test
    fun `Case M - Win scratch card on next recharge`() {
        val text = "Win up to ₹1,000 scratch card on your next recharge"
        val assessment = ActualTransactionEventGate.assess(text)
        assertEquals(ActualEventStatus.FALSE, assessment.actualEvent)
        assertEquals(TransactionEventType.PROMOTIONAL, assessment.eventType)
    }

    // =========================================================================
    // Case N: Balance Inquiry (Informational)
    // =========================================================================
    @Test
    fun `Case N - Available balance inquiry statement`() {
        val text = "Available balance in A/c *5678 is ₹25,430.50"
        val assessment = ActualTransactionEventGate.assess(text)
        assertEquals(ActualEventStatus.FALSE, assessment.actualEvent)
        assertEquals(TransactionEventType.INFORMATIONAL, assessment.eventType)
    }

    // =========================================================================
    // Case O: Credit Limit Statement (Informational)
    // =========================================================================
    @Test
    fun `Case O - Credit limit statement`() {
        val text = "Your credit card limit is ₹1,50,000"
        val assessment = ActualTransactionEventGate.assess(text)
        assertEquals(ActualEventStatus.FALSE, assessment.actualEvent)
        assertEquals(TransactionEventType.INFORMATIONAL, assessment.eventType)
    }

    // =========================================================================
    // Case P: Bill Reminder (Informational)
    // =========================================================================
    @Test
    fun `Case P - Bill due reminder`() {
        val text = "Reminder: Credit card bill of ₹8,500 due on 05-Oct"
        val assessment = ActualTransactionEventGate.assess(text)
        assertEquals(ActualEventStatus.FALSE, assessment.actualEvent)
        assertEquals(TransactionEventType.INFORMATIONAL, assessment.eventType)
    }

    // =========================================================================
    // Case Q: Isolated Currency
    // =========================================================================
    @Test
    fun `Case Q - Isolated currency amount without completed action`() {
        val text = "Special update: ₹500"
        val assessment = ActualTransactionEventGate.assess(text)
        assertTrue(assessment.actualEvent == ActualEventStatus.UNCERTAIN || assessment.actualEvent == ActualEventStatus.FALSE)
    }

    // =========================================================================
    // Case R: Multi-Clause Mixing (Completed action + Future promo)
    // =========================================================================
    @Test
    fun `Case R - Multi-clause notification mixes completed debit and future promo`() {
        val clause1 = "₹500 paid to Swiggy"
        val clause2 = "Pay another ₹500 to win ₹100 cashback"

        val assessment1 = ActualTransactionEventGate.assessClause(clause1)
        val assessment2 = ActualTransactionEventGate.assessClause(clause2)

        assertEquals(ActualEventStatus.TRUE, assessment1.actualEvent)
        assertEquals(TransactionDirection.DEBIT, assessment1.directionHint)

        assertEquals(ActualEventStatus.FALSE, assessment2.actualEvent)
    }

    // =========================================================================
    // Case S: Unknown Application Debit
    // =========================================================================
    @Test
    fun `Case S - Unknown application package with completed debit evidence`() {
        val text = "₹350 spent at Starbucks"
        val notif = createNotification(text, packageName = "com.unknown.coffeeapp")
        val assessment = ActualTransactionEventGate.assessNotification(notif)

        assertEquals(ActualEventStatus.TRUE, assessment.actualEvent)
        assertEquals(TransactionDirection.DEBIT, assessment.directionHint)
    }

    // =========================================================================
    // Case T: Unknown Application Future Action
    // =========================================================================
    @Test
    fun `Case T - Unknown application package with future discount offer`() {
        val text = "Pay ₹350 at Starbucks to get 10% off"
        val notif = createNotification(text, packageName = "com.unknown.coffeeapp")
        val assessment = ActualTransactionEventGate.assessNotification(notif)

        assertEquals(ActualEventStatus.FALSE, assessment.actualEvent)
    }

    // =========================================================================
    // Case U: Amount Near Imperative Action
    // =========================================================================
    @Test
    fun `Case U - Click to pay imperative command`() {
        val text = "Click to pay ₹999 now"
        val assessment = ActualTransactionEventGate.assess(text)
        assertEquals(ActualEventStatus.FALSE, assessment.actualEvent)
    }

    // =========================================================================
    // Case V: Amount in Promotional Header
    // =========================================================================
    @Test
    fun `Case V - Promotional cashback header with recharge now`() {
        val text = "₹500 Cashback Waiting For You! Recharge now"
        val assessment = ActualTransactionEventGate.assess(text)
        assertEquals(ActualEventStatus.FALSE, assessment.actualEvent)
    }

    // =========================================================================
    // Case W: Beneficiary Credit in Account Debit SMS
    // =========================================================================
    @Test
    fun `Case W - Account debited and beneficiary credited correctly resolves to DEBIT`() {
        val text = "Your a/c is debited for Rs. 500 and credited to VPA ravi@okaxis"
        val notif = createNotification(text, packageName = "AD-HDFCBK")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(500.0, candidate?.amount)
        assertEquals(TransactionDirection.DEBIT, candidate?.direction)
    }

    // =========================================================================
    // Case X: Reversal Ambiguity
    // =========================================================================
    @Test
    fun `Case X - Reversal maintains reversal event type and unknown direction`() {
        val text = "Reversal of Rs. 500"
        val assessment = ActualTransactionEventGate.assess(text)

        assertEquals(ActualEventStatus.TRUE, assessment.actualEvent)
        assertEquals(TransactionEventType.REVERSED, assessment.eventType)
        assertEquals(TransactionDirection.UNKNOWN, assessment.directionHint)
    }

    // =========================================================================
    // Case Y: Recharge Offer vs Confirmation
    // =========================================================================
    @Test
    fun `Case Y1 - Recharge plan offer is rejected as non-event`() {
        val offerText = "Recharge with ₹299 plan for 28 days validity"
        val notif = createNotification(offerText, packageName = "com.jio.myjio")
        val candidates = StructuredTransactionExtractor.extractAll(notif)
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `Case Y2 - Recharge confirmation is accepted as actual debit event`() {
        val confirmText = "Recharge of ₹299 was successful"
        val notif = createNotification(confirmText, packageName = "com.jio.myjio")
        val candidate = StructuredTransactionExtractor.extract(notif)

        assertNotNull(candidate)
        assertEquals(299.0, candidate?.amount)
        assertEquals(TransactionDirection.DEBIT, candidate?.direction)
    }
}
