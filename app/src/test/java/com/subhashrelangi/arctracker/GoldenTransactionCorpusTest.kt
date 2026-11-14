package com.subhashrelangi.arctracker

import com.subhashrelangi.arctracker.engine.pipeline.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 6: Golden Transaction Corpus Regression Suite.
 *
 * Verifies 40+ industrial-grade real-world Indian banking, UPI fintech, telecom fulfillment,
 * promotional spam, mandate/autopay, dual-verb, and split-bill transaction vectors.
 *
 * Enforces two core invariants:
 * 1. Zero Unanchored Database Writes: Non-financial notices, carrier fulfillment updates (e.g. AD-AIRINF-S),
 *    promotional spam (-P headers), and future mandates are dropped or rejected before database writes (zero writes).
 * 2. High-Fidelity Capture: Genuine Tier 1 bank SMS, credit card spend alerts, and fintech pushes
 *    are captured with exact amount, direction, merchant, and banking instrument suffixes.
 */
class GoldenTransactionCorpusTest {

    data class PipelineOutcome(
        val isDroppedOrRejected: Boolean,
        val validated: ValidatedTransactionCandidate? = null,
        val rejectionReason: String? = null
    )

    private fun runPipeline(
        text: String,
        senderOrPackage: String,
        timestamp: Long = 1731580000000L
    ): PipelineOutcome {
        val captured = CapturedNotificationInfo(
            packageName = senderOrPackage,
            notificationKey = "key_${senderOrPackage.hashCode()}_${timestamp}",
            postTime = timestamp,
            text = text
        )
        val normalized = NotificationNormalizer.normalize(captured)
            ?: return PipelineOutcome(isDroppedOrRejected = true, rejectionReason = "Normalization dropped")

        val classification = FinancialClassifier.classify(normalized)
        if (classification.isNoise || classification.financialRelevance == FinancialRelevance.NON_FINANCIAL) {
            return PipelineOutcome(
                isDroppedOrRejected = true,
                rejectionReason = "Classifier dropped: ${classification.noiseCategory}"
            )
        }

        val candidates = StructuredTransactionExtractor.extractAll(classification)
        if (candidates.isEmpty()) {
            return PipelineOutcome(isDroppedOrRejected = true, rejectionReason = "No structured candidate extracted")
        }

        val candidate = candidates.first()
        val validated = TransactionValidator.validate(candidate, classification)
        if (validated.isRejected) {
            return PipelineOutcome(
                isDroppedOrRejected = true,
                validated = validated,
                rejectionReason = "Validator rejected: ${validated.rejectionReasons.joinToString(", ")}"
            )
        }

        return PipelineOutcome(isDroppedOrRejected = false, validated = validated)
    }

    // =========================================================================
    // CATEGORY 1: Tier 1 Bank Debit SMS (8 Banks)
    // =========================================================================

    @Test
    fun test01_hdfcBankDebitSms() {
        val outcome = runPipeline(
            text = "A/c **1234 debited by Rs 500.00 on 14-Nov-26 to SWIGGY. UPI Ref 431908123456. Bal Rs 12,450.00",
            senderOrPackage = "VK-HDFCBK"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(500.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("1234", candidate.accountSuffix)
        assertEquals("431908123456", candidate.referenceId)
    }

    @Test
    fun test02_sbiDebitSms() {
        val outcome = runPipeline(
            text = "Dear SBI User, your A/c ending 5678 debited by 1250.0 on 14Nov26 by transfer to ZOMATO UPI/431908654321. Bal: Rs 8,300.00",
            senderOrPackage = "BZ-SBIINB"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(1250.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("5678", candidate.accountSuffix)
    }

    @Test
    fun test03_iciciBankDebitSms() {
        val outcome = runPipeline(
            text = "Acct XX9012 debited with INR 2,499.00 on 14-Nov-26. Info: BIL*NETFLIX. Available balance INR 45,210.50",
            senderOrPackage = "VM-ICICIB"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(2499.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("9012", candidate.accountSuffix)
    }

    @Test
    fun test04_axisBankDebitSms() {
        val outcome = runPipeline(
            text = "INR 350.00 debited from A/c no. XX3456 on 14-Nov-26 at CHAIPOINT. UPI Ref: 431900112233",
            senderOrPackage = "AX-AXISBK"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(350.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("3456", candidate.accountSuffix)
        assertEquals("431900112233", candidate.referenceId)
    }

    @Test
    fun test05_kotakBankDebitSms() {
        val outcome = runPipeline(
            text = "Rs. 720.00 debited from Kotak Bank A/c ...7890 on 14-NOV-26 towards UBER INDIA. Ref no 431900998877",
            senderOrPackage = "BP-KOTAKB"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(720.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("7890", candidate.accountSuffix)
    }

    @Test
    fun test06_pnbDebitSms() {
        val outcome = runPipeline(
            text = "A/c *1357 debited by Rs. 1500.00 on 14-11-2026 by UPI to AMAZON. Ref 431900445566",
            senderOrPackage = "VD-PNBSMS"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(1500.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("1357", candidate.accountSuffix)
    }

    @Test
    fun test07_bobDebitSms() {
        val outcome = runPipeline(
            text = "BOB A/c ending 2468 debited by Rs 850.00 on 14-Nov-26. Trans Info: UPI/431900334455/SWIGGY",
            senderOrPackage = "VM-BOBTXN"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(850.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("2468", candidate.accountSuffix)
    }

    @Test
    fun test08_canaraBankDebitSms() {
        val outcome = runPipeline(
            text = "Your Canara Bank A/c ...9753 debited with Rs 300.00 on 14-11-2026. Ref no: 431900223311",
            senderOrPackage = "VM-CANBNK"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(300.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("9753", candidate.accountSuffix)
    }

    // =========================================================================
    // CATEGORY 2: Tier 1 Bank Credit SMS (4 Banks)
    // =========================================================================

    @Test
    fun test09_hdfcBankCreditSms() {
        val outcome = runPipeline(
            text = "A/c **1234 credited with Rs 25,000.00 on 14-Nov-26 by transfer from EMPLOYER CORP. UPI Ref 431900889900. Bal Rs 37,450.00",
            senderOrPackage = "VK-HDFCBK"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(25000.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.CREDIT, candidate.direction)
        assertEquals("1234", candidate.accountSuffix)
        assertEquals("431900889900", candidate.referenceId)
    }

    @Test
    fun test10_sbiCreditSms() {
        val outcome = runPipeline(
            text = "Dear SBI User, your A/c ending 5678 credited by Rs 5,000.00 on 14Nov26 by transfer from JOHN DOE UPI/431900778899. Bal: Rs 13,300.00",
            senderOrPackage = "BZ-SBIINB"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(5000.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.CREDIT, candidate.direction)
        assertEquals("5678", candidate.accountSuffix)
    }

    @Test
    fun test11_iciciBankCreditSms() {
        val outcome = runPipeline(
            text = "Acct XX9012 credited with INR 10,000.00 on 14-Nov-26. Info: NEFT-SALARY. Available balance INR 55,210.50",
            senderOrPackage = "VM-ICICIB"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(10000.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.CREDIT, candidate.direction)
        assertEquals("9012", candidate.accountSuffix)
    }

    @Test
    fun test12_axisBankCreditSms() {
        val outcome = runPipeline(
            text = "INR 1,200.00 credited to A/c no. XX3456 on 14-Nov-26 by UPI. Ref: 431900667788",
            senderOrPackage = "AX-AXISBK"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(1200.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.CREDIT, candidate.direction)
        assertEquals("3456", candidate.accountSuffix)
    }

    // =========================================================================
    // CATEGORY 3: Credit Card Spend SMS (4 Cards)
    // =========================================================================

    @Test
    fun test13_hdfcCreditCardSpend() {
        val outcome = runPipeline(
            text = "Alert: Rs. 4,500.00 spent on HDFC Bank Credit Card ending 8899 at CROMA on 14-Nov-26. Avail Lmt: Rs 1,45,500.00",
            senderOrPackage = "VK-HDFCBK"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(4500.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("8899", candidate.cardSuffix)
    }

    @Test
    fun test14_iciciCreditCardSpend() {
        val outcome = runPipeline(
            text = "Transaction of INR 1,899.00 made on ICICI Bank Credit Card XX4433 at MYNTRA on 14-Nov-26. Avail Limit: INR 85,000.00",
            senderOrPackage = "VM-ICICIB"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(1899.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("4433", candidate.cardSuffix)
    }

    @Test
    fun test15_sbiCardSpend() {
        val outcome = runPipeline(
            text = "Rs. 3,250.00 spent on your SBI Card ending 1122 at RELIANCE DIGITAL on 14-Nov-26. Avl Lmt: Rs 96,750",
            senderOrPackage = "BZ-SBICRD"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(3250.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("1122", candidate.cardSuffix)
    }

    @Test
    fun test16_axisCreditCardSpend() {
        val outcome = runPipeline(
            text = "INR 620.00 spent on Axis Bank Card no. ending 5566 at MCDONALDS on 14-Nov-26",
            senderOrPackage = "AX-AXISBK"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(620.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("5566", candidate.cardSuffix)
    }

    // =========================================================================
    // CATEGORY 4: Fintech App Push Notifications (OS-verified packages) (10 Apps)
    // =========================================================================

    @Test
    fun test17_gpayDebitMinimalist() {
        val outcome = runPipeline(
            text = "Paid ₹45 to Tea Post",
            senderOrPackage = "com.google.android.apps.nbu.paisa.user"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(45.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("Tea Post", candidate.merchant)
    }

    @Test
    fun test18_gpayCreditMinimalist() {
        val outcome = runPipeline(
            text = "₹1,200 received from Sarah Connor",
            senderOrPackage = "com.google.android.apps.nbu.paisa.user"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(1200.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.CREDIT, candidate.direction)
        assertEquals("Sarah Connor", candidate.counterparty)
    }

    @Test
    fun test19_gpayWithUpiRef() {
        val outcome = runPipeline(
            text = "Paid ₹350 to Starbucks. UPI Ref: 431900112244",
            senderOrPackage = "com.google.android.apps.nbu.paisa.user"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(350.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("Starbucks", candidate.merchant)
        assertEquals("431900112244", candidate.referenceId)
    }

    @Test
    fun test20_phonePeDebitMinimalist() {
        val outcome = runPipeline(
            text = "Paid ₹150 to Fresh Fruits",
            senderOrPackage = "com.phonepe.app"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(150.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("Fresh Fruits", candidate.merchant)
    }

    @Test
    fun test21_phonePePaymentSuccessful() {
        val outcome = runPipeline(
            text = "Payment of ₹850 to BigBasket successful",
            senderOrPackage = "com.phonepe.app"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(850.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("BigBasket", candidate.merchant)
    }

    @Test
    fun test22_phonePeCredit() {
        val outcome = runPipeline(
            text = "Received ₹2,000 from Alex",
            senderOrPackage = "com.phonepe.app"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(2000.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.CREDIT, candidate.direction)
        assertEquals("Alex", candidate.counterparty)
    }

    @Test
    fun test23_paytmDebit() {
        val outcome = runPipeline(
            text = "Paid ₹99 to Uber. Paytm Txn ID: 202611141234",
            senderOrPackage = "net.one97.paytm"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(99.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("Uber", candidate.merchant)
        assertEquals("202611141234", candidate.referenceId)
    }

    @Test
    fun test24_paytmCredit() {
        val outcome = runPipeline(
            text = "Received ₹500 from Rahul",
            senderOrPackage = "net.one97.paytm"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(500.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.CREDIT, candidate.direction)
        assertEquals("Rahul", candidate.counterparty)
    }

    @Test
    fun test25_credCreditCardPayment() {
        val outcome = runPipeline(
            text = "Paid ₹15,400 to HDFC Bank Credit Card",
            senderOrPackage = "com.dreamplug.androidapp"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(15400.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
    }

    @Test
    fun test26_bhimUpiDebit() {
        val outcome = runPipeline(
            text = "₹600 paid to Apollo Pharmacy. UPI Ref: 431900554433",
            senderOrPackage = "in.org.npci.upiapp"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(600.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("Apollo Pharmacy", candidate.merchant)
        assertEquals("431900554433", candidate.referenceId)
    }

    // =========================================================================
    // CATEGORY 5: Telecom Fulfillment & Carrier Status Notices (Guaranteed 0 Writes)
    // =========================================================================

    @Test
    fun test27_airtelFulfillmentNotice_droppedWithZeroWrites() {
        val outcome = runPipeline(
            text = "Hi, we have processed Rs. 199.0 for your Airtel Mobile 9502568713. The payment will be updated within 15 minutes. Please keep your order ID 7517823928865849344 for future reference. Track your payment status via https://i.airtel.in/Recharge-Bills",
            senderOrPackage = "AD-AIRINF-S"
        )
        assertTrue("Airtel recharge status notice must be dropped with zero writes", outcome.isDroppedOrRejected)
    }

    @Test
    fun test28_airtelPromoChannel_droppedWithZeroWrites() {
        val outcome = runPipeline(
            text = "Exclusive offer! Recharge with Rs. 299 and get 2GB/day + Disney+ Hotstar free. Click https://airtel.in/recharge",
            senderOrPackage = "AD-AIRINF-P"
        )
        assertTrue("Promotional -P channel must be hard-dropped with zero writes", outcome.isDroppedOrRejected)
    }

    @Test
    fun test29_jioFulfillmentNotice_droppedWithZeroWrites() {
        val outcome = runPipeline(
            text = "Recharge of Rs 239 successful for Jio Number 9876543210. Benefits: 1.5 GB/Day, Validity: 28 days. Ref: JIO87654321",
            senderOrPackage = "JM-JIOINF"
        )
        assertTrue("Jio recharge status update must be dropped with zero writes", outcome.isDroppedOrRejected)
    }

    @Test
    fun test30_viPostpaidPaymentNotice_droppedWithZeroWrites() {
        val outcome = runPipeline(
            text = "Payment of Rs 479 received for your Vi Postpaid number 9123456780. Thank you! Click https://myvi.in/pay",
            senderOrPackage = "VD-ViCARE"
        )
        assertTrue("Vi postpaid status receipt without bank account anchor must be dropped with zero writes", outcome.isDroppedOrRejected)
    }

    @Test
    fun test31_bsnlPromoChannel_droppedWithZeroWrites() {
        val outcome = runPipeline(
            text = "Special Plan Voucher! Recharge Rs. 197 get unlimited calls for 70 days. Visit bsnl.co.in",
            senderOrPackage = "AD-BSNLPR-P"
        )
        assertTrue("BSNL promo must be dropped with zero writes", outcome.isDroppedOrRejected)
    }

    // =========================================================================
    // CATEGORY 6: Commercial / Merchant Promo Spam (-P Headers & Discounts) (Zero Writes)
    // =========================================================================

    @Test
    fun test32_swiggyPromo_droppedWithZeroWrites() {
        val outcome = runPipeline(
            text = "Hungry? Get 50% OFF up to Rs 100 on your next order! Use code SWIGGY50",
            senderOrPackage = "AX-SWIGGY-P"
        )
        assertTrue("Swiggy promo -P channel must be dropped with zero writes", outcome.isDroppedOrRejected)
    }

    @Test
    fun test33_zomatoPromo_droppedWithZeroWrites() {
        val outcome = runPipeline(
            text = "Craving Biryani? Flat Rs 120 discount on orders above Rs 299 at Behrouz. Order now!",
            senderOrPackage = "AD-ZOMATO-P"
        )
        assertTrue("Zomato promo must be dropped with zero writes", outcome.isDroppedOrRejected)
    }

    @Test
    fun test34_flipkartPromo_droppedWithZeroWrites() {
        val outcome = runPipeline(
            text = "Big Billion Days! iPhone 15 at Rs 49,999. Grab before stock ends. https://fkrt.it/sale",
            senderOrPackage = "VM-FLPKRT-P"
        )
        assertTrue("Flipkart marketing notice must be dropped with zero writes", outcome.isDroppedOrRejected)
    }

    @Test
    fun test35_myntraPromo_droppedWithZeroWrites() {
        val outcome = runPipeline(
            text = "Extra Rs. 300 off on footwear. Use code SHOE300. Valid today only!",
            senderOrPackage = "AX-MYNTRA-P"
        )
        assertTrue("Myntra discount notice must be dropped with zero writes", outcome.isDroppedOrRejected)
    }

    @Test
    fun test36_loanSpam_droppedWithZeroWrites() {
        val outcome = runPipeline(
            text = "Pre-approved personal loan of Rs 5,00,000 disbursed to your account within 2 minutes. Apply now!",
            senderOrPackage = "JM-KREDIT-P"
        )
        assertTrue("Loan offer spam must be dropped with zero writes", outcome.isDroppedOrRejected)
    }

    // =========================================================================
    // CATEGORY 7: Mandate / Autopay / Upcoming Bill Reminders (Guaranteed 0 Writes)
    // =========================================================================

    @Test
    fun test37_hdfcAutopayMandate_droppedWithZeroWrites() {
        val outcome = runPipeline(
            text = "Dear Customer, an autopay debit mandate for Rs. 499.00 to NETFLIX is scheduled for 18-Nov-26 from your A/c **1234",
            senderOrPackage = "VK-HDFCBK"
        )
        assertTrue("Upcoming mandate schedule alert must not trigger ledger write", outcome.isDroppedOrRejected)
    }

    @Test
    fun test38_sbiAutopayReminder_droppedWithZeroWrites() {
        val outcome = runPipeline(
            text = "UPI autopay reminder: Rs 999.00 will be debited towards HOTSTAR on 20-Nov-26 from A/c ending 5678",
            senderOrPackage = "BZ-SBIINB"
        )
        assertTrue("Future autopay reminder must not trigger ledger write", outcome.isDroppedOrRejected)
    }

    @Test
    fun test39_electricityBillReminder_droppedWithZeroWrites() {
        val outcome = runPipeline(
            text = "Dear Consumer, electricity bill of Rs. 1,450.00 for CA 102938475 is due on 22-Nov-26. Pay before due date to avoid disconnection.",
            senderOrPackage = "AD-APEPDCL-S"
        )
        assertTrue("Electricity bill reminder must be dropped with zero writes", outcome.isDroppedOrRejected)
    }

    @Test
    fun test40_creditCardBillStatement_droppedWithZeroWrites() {
        val outcome = runPipeline(
            text = "Dear Customer, total amount due on your Credit Card ending 8899 is Rs 18,500.00 payable by 05-Dec-26. Min due: Rs 925.00",
            senderOrPackage = "VK-HDFCBK"
        )
        assertTrue("Bill statement notification must not trigger an expense write", outcome.isDroppedOrRejected)
    }

    // =========================================================================
    // CATEGORY 8: Dual-Verb Transfers & SBI Currency-less
    // =========================================================================

    @Test
    fun test41_dualVerbOutgoingTransfer_resolvesDebit() {
        val outcome = runPipeline(
            text = "Your A/c ending 1234 debited by INR 5,000.00 on 14-Nov-26 towards transfer to beneficiary A/c ending 5678 credited. UPI Ref: 431900123456",
            senderOrPackage = "VK-HDFCBK"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(5000.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("1234", candidate.accountSuffix)
        assertEquals("431900123456", candidate.referenceId)
    }

    @Test
    fun test42_dualVerbIncomingTransfer_resolvesCredit() {
        val outcome = runPipeline(
            text = "Beneficiary A/c ending 1234 credited by Rs 3,000.00 from A/c ending 9999 debited. Ref: 431900654321",
            senderOrPackage = "VK-HDFCBK"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(3000.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.CREDIT, candidate.direction)
        assertEquals("1234", candidate.accountSuffix)
        assertEquals("431900654321", candidate.referenceId)
    }

    @Test
    fun test43_sbiCurrencylessDebitAmount() {
        val outcome = runPipeline(
            text = "Dear SBI User, your A/c ending 5678 debited by 350.0 on 14Nov26. Ref: 431900111222. Bal: 14,000.00",
            senderOrPackage = "BZ-SBIINB"
        )
        assertFalse(outcome.rejectionReason ?: "", outcome.isDroppedOrRejected)
        val candidate = outcome.validated!!.candidate
        assertEquals(350.0, candidate.amount!!, 0.001)
        assertEquals(TransactionDirection.DEBIT, candidate.direction)
        assertEquals("5678", candidate.accountSuffix)
        assertEquals("431900111222", candidate.referenceId)
    }

    // =========================================================================
    // CATEGORY 9: Deduplication & Split Transactions with Distinct Identifiers
    // =========================================================================

    @Test
    fun test44_splitTransactionWithDistinctUtrs_neverDeduplicates() {
        val payment1 = runPipeline(
            text = "Paid ₹500 to Starbucks. UPI Ref: 431900881111",
            senderOrPackage = "com.google.android.apps.nbu.paisa.user",
            timestamp = 10000L
        ).validated!!

        val payment2 = runPipeline(
            text = "Paid ₹500 to Starbucks. UPI Ref: 431900882222",
            senderOrPackage = "com.google.android.apps.nbu.paisa.user",
            timestamp = 10005L
        ).validated!!

        val existingRecord = TransactionRecord.fromValidated(payment1)
        val dedupResult = TransactionDeduplicator.evaluate(payment2, listOf(existingRecord))

        assertEquals(DedupDecision.NEW_TRANSACTION, dedupResult.decision)
        assertNotEquals(DedupDecision.DUPLICATE, dedupResult.decision)
        assertNotEquals(DedupDecision.CORRELATED, dedupResult.decision)
    }

    @Test
    fun test45_crossChannelCorrelation_pushAndSms() {
        val pushCandidate = runPipeline(
            text = "Paid ₹250 to Chai Point",
            senderOrPackage = "com.google.android.apps.nbu.paisa.user",
            timestamp = 10000L
        ).validated!!

        val existingPushRecord = TransactionRecord.fromValidated(pushCandidate)

        val smsCandidate = runPipeline(
            text = "A/c **1234 debited by Rs. 250 at Chai Point. Ref 998811223344",
            senderOrPackage = "VK-HDFCBK",
            timestamp = 10030L
        ).validated!!

        val dedupResult = TransactionDeduplicator.evaluate(smsCandidate, listOf(existingPushRecord))

        assertEquals(DedupDecision.CORRELATED, dedupResult.decision)
        assertEquals(existingPushRecord.id, dedupResult.matchedRecordId)
    }
}
