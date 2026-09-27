package com.example.arctracker

import com.example.arctracker.service.*
import org.junit.Assert.*
import org.junit.Test

class TransactionDeduplicatorTest {

    private fun createValidatedCandidate(
        text: String,
        notificationKey: String = "notif_key_default",
        postTime: Long = 1000000L,
        isUpdate: Boolean = false,
        packageName: String = "com.google.android.apps.nbu.paisa.user"
    ): ValidatedTransactionCandidate {
        val captured = CapturedNotificationInfo(
            packageName = packageName,
            notificationKey = notificationKey,
            postTime = postTime,
            text = text,
            isUpdate = isUpdate
        )
        val normalized = NotificationNormalizer.normalize(captured)!!
        val classification = FinancialClassifier.classify(normalized)
        val candidate = StructuredTransactionExtractor.extractDirect(normalized, classification)
        return TransactionValidator.validate(candidate, classification)
    }

    // --- Source Identity (Level 1) ---

    @Test
    fun test01_sameNotificationKeySameEvent_isDuplicate() {
        val candidate = createValidatedCandidate("₹500 paid to Ravi", notificationKey = "key_100")
        val existing = listOf(
            TransactionRecord.fromValidated(candidate)
        )

        val result = TransactionDeduplicator.evaluate(candidate, existing)

        assertEquals(DedupDecision.DUPLICATE, result.decision)
        assertEquals(MatchStrategy.SOURCE_EVENT_ID, result.strategy)
        assertEquals("key_100", result.matchedRecordId)
        assertTrue(result.isDuplicate)
    }

    @Test
    fun test02_sameNotificationKeyNewerPayload_isUpdateExisting() {
        val initialCandidate = createValidatedCandidate("Payment pending for ₹500", notificationKey = "key_100", postTime = 1000L)
        val updatedCandidate = createValidatedCandidate("₹500 paid to Ravi. UPI Ref: 123456", notificationKey = "key_100", postTime = 1500L, isUpdate = true)

        val existing = listOf(
            TransactionRecord.fromValidated(initialCandidate)
        )

        val result = TransactionDeduplicator.evaluate(updatedCandidate, existing)

        assertEquals(DedupDecision.UPDATE_EXISTING, result.decision)
        assertEquals(MatchStrategy.NOTIFICATION_UPDATE, result.strategy)
        assertEquals("key_100", result.matchedRecordId)
        assertTrue(result.isUpdate)
    }

    @Test
    fun test03_differentNotificationKeySameReferenceId_isCorrelated() {
        val candidate1 = createValidatedCandidate("₹500 paid to Ravi. UPI Ref: 123456789012", notificationKey = "key_app_1", postTime = 1000L)
        val candidate2 = createValidatedCandidate("₹500 paid to Ravi. UPI Ref: 123456789012", notificationKey = "key_app_2", postTime = 2000L)

        val existing = listOf(
            TransactionRecord.fromValidated(candidate1)
        )

        val result = TransactionDeduplicator.evaluate(candidate2, existing)

        assertEquals(DedupDecision.CORRELATED, result.decision)
        assertEquals(MatchStrategy.EXPLICIT_REFERENCE_ID, result.strategy)
        assertEquals("key_app_1", result.matchedRecordId)
        assertTrue(result.isCorrelated)
    }

    // --- Strong Reference Matching (Level 2) ---

    @Test
    fun test04_sameUtrSameAmountSameDirection_isCorrelated() {
        val candidate1 = createValidatedCandidate("Debited ₹1,000. UTR: ABC123456789", notificationKey = "key_sms_1")
        val candidate2 = createValidatedCandidate("Paid ₹1,000 to Store. UTR: ABC123456789", notificationKey = "key_notif_1")

        val existing = listOf(
            TransactionRecord.fromValidated(candidate1, sourceType = TransactionSourceType.SMS_HISTORY)
        )

        val result = TransactionDeduplicator.evaluate(candidate2, existing)

        assertEquals(DedupDecision.CORRELATED, result.decision)
        assertEquals(MatchStrategy.EXPLICIT_REFERENCE_ID, result.strategy)
    }

    @Test
    fun test05_sameUpiReferenceCompatibleContext_isCorrelated() {
        val candidate1 = createValidatedCandidate("Payment of ₹250 successful. UPI Ref No: 998877665544", notificationKey = "key_gpay")
        val candidate2 = createValidatedCandidate("₹250 paid to Merchant. UPI Ref: 998877665544", notificationKey = "key_phonepe")

        val existing = listOf(TransactionRecord.fromValidated(candidate1))
        val result = TransactionDeduplicator.evaluate(candidate2, existing)

        assertEquals(DedupDecision.CORRELATED, result.decision)
    }

    @Test
    fun test06_sameRrnCompatibleContext_isCorrelated() {
        val candidate1 = createValidatedCandidate("Txn successful. RRN 123456789012 for ₹450", notificationKey = "key_bank_1")
        val candidate2 = createValidatedCandidate("Paid ₹450 at Shop. RRN 123456789012", notificationKey = "key_bank_2")

        val existing = listOf(TransactionRecord.fromValidated(candidate1))
        val result = TransactionDeduplicator.evaluate(candidate2, existing)

        assertEquals(DedupDecision.CORRELATED, result.decision)
    }

    // --- Reference Conflicts ---

    @Test
    fun test07_sameUtrDifferentAmount_needsReview() {
        val candidate1 = createValidatedCandidate("Debited ₹500. UTR: ABC123456789", notificationKey = "key_1")
        val candidate2 = createValidatedCandidate("Debited ₹5,000. UTR: ABC123456789", notificationKey = "key_2")

        val existing = listOf(TransactionRecord.fromValidated(candidate1))
        val result = TransactionDeduplicator.evaluate(candidate2, existing)

        assertEquals(DedupDecision.NEEDS_REVIEW, result.decision)
        assertEquals(MatchStrategy.IDENTITY_CONFLICT, result.strategy)
        assertTrue(result.conflictingSignals.any { it.contains("AMOUNT_MISMATCH") })
    }

    @Test
    fun test08_sameReferenceConflictingDirection_needsReview() {
        val candidate1 = createValidatedCandidate("Debited ₹500. UTR: ABC123456789", notificationKey = "key_1")
        val candidate2 = createValidatedCandidate("Credited ₹500. UTR: ABC123456789", notificationKey = "key_2")

        val existing = listOf(TransactionRecord.fromValidated(candidate1))
        val result = TransactionDeduplicator.evaluate(candidate2, existing)

        assertEquals(DedupDecision.NEEDS_REVIEW, result.decision)
        assertEquals(MatchStrategy.IDENTITY_CONFLICT, result.strategy)
        assertTrue(result.conflictingSignals.any { it.contains("DIRECTION_MISMATCH") })
    }

    // --- Strong Fingerprint (Level 3) ---

    @Test
    fun test09_sameAmountDirectionMerchantAccountTime_isCorrelated() {
        val candidate1 = createValidatedCandidate("₹500 paid to Ravi using card XX1234", notificationKey = "key_1", postTime = 1000000L)
        val candidate2 = createValidatedCandidate("₹500 paid to Ravi. A/c XX1234", notificationKey = "key_2", postTime = 1010000L)

        val existing = listOf(TransactionRecord.fromValidated(candidate1))
        val result = TransactionDeduplicator.evaluate(candidate2, existing)

        assertEquals(DedupDecision.CORRELATED, result.decision)
        assertEquals(MatchStrategy.STRONG_FINGERPRINT, result.strategy)
    }

    @Test
    fun test10_sameAmountDirectionOnly_isNotDuplicate() {
        val candidate1 = createValidatedCandidate("₹500 paid", notificationKey = "key_1", postTime = 1000000L)
        val candidate2 = createValidatedCandidate("₹500 paid", notificationKey = "key_2", postTime = 1005000L)

        val existing = listOf(TransactionRecord.fromValidated(candidate1))
        val result = TransactionDeduplicator.evaluate(candidate2, existing)

        // Amount + direction alone must NOT automatically duplicate
        assertEquals(DedupDecision.NEW_TRANSACTION, result.decision)
    }

    @Test
    fun test11_sameAmountTimestampOnly_isNotDuplicate() {
        val candidate1 = createValidatedCandidate("₹500 paid to Ravi", notificationKey = "key_1", postTime = 1000000L)
        val candidate2 = createValidatedCandidate("₹500 paid to Amazon", notificationKey = "key_2", postTime = 1000000L)

        val existing = listOf(TransactionRecord.fromValidated(candidate1))
        val result = TransactionDeduplicator.evaluate(candidate2, existing)

        assertEquals(DedupDecision.NEW_TRANSACTION, result.decision)
    }

    @Test
    fun test12_sameAmountMerchantOnly_isNotDuplicateWithoutTimeOrAccount() {
        val candidate1 = createValidatedCandidate("₹500 paid to Ravi", notificationKey = "key_1", postTime = 1000000L)
        // 5 hours later
        val candidate2 = createValidatedCandidate("₹500 paid to Ravi", notificationKey = "key_2", postTime = 1000000L + 5 * 3600 * 1000L)

        val existing = listOf(TransactionRecord.fromValidated(candidate1))
        val result = TransactionDeduplicator.evaluate(candidate2, existing)

        assertEquals(DedupDecision.NEW_TRANSACTION, result.decision)
    }

    // --- Cross-Source (Notification <-> SMS) ---

    @Test
    fun test13_smsAndNotificationWithSameUpiRef_isCorrelated() {
        val notifCandidate = createValidatedCandidate("Paid ₹350 to Swiggy. UPI Ref: 123456789012", notificationKey = "notif_swiggy", postTime = 1000000L)
        val smsRecord = TransactionRecord(
            id = "sms_101",
            sourceType = TransactionSourceType.SMS_HISTORY,
            amount = 350.0,
            direction = TransactionDirection.DEBIT,
            upiTransactionId = "123456789012",
            timestamp = 1005000L
        )

        val result = TransactionDeduplicator.evaluate(notifCandidate, listOf(smsRecord))

        assertEquals(DedupDecision.CORRELATED, result.decision)
        assertEquals(MatchStrategy.EXPLICIT_REFERENCE_ID, result.strategy)
    }

    @Test
    fun test14_smsAndNotificationWithFingerprintAgreement_isCorrelated() {
        val notifCandidate = createValidatedCandidate("₹500 paid to Ravi. Card XX1234", notificationKey = "notif_1", postTime = 1000000L)
        val smsRecord = TransactionRecord(
            id = "sms_102",
            sourceType = TransactionSourceType.SMS_HISTORY,
            amount = 500.0,
            direction = TransactionDirection.DEBIT,
            merchant = "Ravi",
            cardSuffix = "1234",
            timestamp = 1010000L
        )

        val result = TransactionDeduplicator.evaluate(notifCandidate, listOf(smsRecord))

        assertEquals(DedupDecision.CORRELATED, result.decision)
        assertEquals(MatchStrategy.STRONG_FINGERPRINT, result.strategy)
    }

    @Test
    fun test15_smsAndNotificationWithOnlyAmountTimeAgreement_isNewTransaction() {
        val notifCandidate = createValidatedCandidate("₹500 paid", notificationKey = "notif_plain", postTime = 1000000L)
        val smsRecord = TransactionRecord(
            id = "sms_103",
            sourceType = TransactionSourceType.SMS_HISTORY,
            amount = 500.0,
            direction = TransactionDirection.DEBIT,
            timestamp = 1002000L
        )

        val result = TransactionDeduplicator.evaluate(notifCandidate, listOf(smsRecord))

        // Never automatically merge on amount + time alone
        assertEquals(DedupDecision.NEW_TRANSACTION, result.decision)
    }

    // --- Different Transactions ---

    @Test
    fun test16_differentMerchantsSameAmount_isNewTransaction() {
        val candidateRavi = createValidatedCandidate("₹500 paid to Ravi", notificationKey = "key_ravi", postTime = 1000000L)
        val candidateAmazon = createValidatedCandidate("₹500 paid to Amazon", notificationKey = "key_amazon", postTime = 1001000L)

        val existing = listOf(TransactionRecord.fromValidated(candidateRavi))
        val result = TransactionDeduplicator.evaluate(candidateAmazon, existing)

        assertEquals(DedupDecision.NEW_TRANSACTION, result.decision)
    }

    @Test
    fun test17_sameMerchantIncompatibleTime_isNewTransaction() {
        val morningCandidate = createValidatedCandidate("₹500 paid to Ravi", notificationKey = "key_morning", postTime = 1000000L)
        // 6 hours later (21,600,000 ms)
        val eveningCandidate = createValidatedCandidate("₹500 paid to Ravi", notificationKey = "key_evening", postTime = 1000000L + 21600000L)

        val existing = listOf(TransactionRecord.fromValidated(morningCandidate))
        val result = TransactionDeduplicator.evaluate(eveningCandidate, existing)

        assertEquals(DedupDecision.NEW_TRANSACTION, result.decision)
    }

    // --- Notification Updates ---

    @Test
    fun test18_processingToSuccessSameKey_isUpdateExisting() {
        val processingCandidate = createValidatedCandidate("Payment processing ₹500 to Ravi", notificationKey = "gpay_txn_55", postTime = 1000L)
        val successCandidate = createValidatedCandidate("Payment successful ₹500 to Ravi", notificationKey = "gpay_txn_55", postTime = 2000L, isUpdate = true)

        val existing = listOf(TransactionRecord.fromValidated(processingCandidate))
        val result = TransactionDeduplicator.evaluate(successCandidate, existing)

        assertEquals(DedupDecision.UPDATE_EXISTING, result.decision)
    }

    @Test
    fun test19_processingToSuccessDifferentKeySameUpiRef_isUpdateExisting() {
        val processingCandidate = createValidatedCandidate("Payment pending for ₹500. UPI Ref: 123456789012", notificationKey = "key_pending_1")
        val successCandidate = createValidatedCandidate("Payment successful ₹500 to Ravi. UPI Ref: 123456789012", notificationKey = "key_success_2")

        val existing = listOf(TransactionRecord.fromValidated(processingCandidate))
        val result = TransactionDeduplicator.evaluate(successCandidate, existing)

        assertEquals(DedupDecision.UPDATE_EXISTING, result.decision)
        assertEquals(MatchStrategy.EXPLICIT_REFERENCE_ID, result.strategy)
    }

    // --- Account / Card ---

    @Test
    fun test20_sameAmountDirectionSameAccountSuffixCompatible_isCorrelated() {
        val candidate1 = createValidatedCandidate("₹500 debited from A/c XX1234", notificationKey = "key_acc_1", postTime = 1000000L)
        val candidate2 = createValidatedCandidate("Paid ₹500 to Ravi using card XX1234", notificationKey = "key_acc_2", postTime = 1005000L)

        val existing = listOf(TransactionRecord.fromValidated(candidate1))
        val result = TransactionDeduplicator.evaluate(candidate2, existing)

        assertEquals(DedupDecision.CORRELATED, result.decision)
    }

    @Test
    fun test21_differentExplicitAccountSuffixes_isNeedsReviewOrNew() {
        val candidate1 = createValidatedCandidate("₹500 debited from A/c XX1234", notificationKey = "key_acc_1", postTime = 1000000L)
        val candidate2 = createValidatedCandidate("₹500 debited from A/c XX5678", notificationKey = "key_acc_2", postTime = 1001000L)

        val existing = listOf(TransactionRecord.fromValidated(candidate1))
        val result = TransactionDeduplicator.evaluate(candidate2, existing)

        assertNotEquals(DedupDecision.DUPLICATE, result.decision)
        assertNotEquals(DedupDecision.CORRELATED, result.decision)
        assertTrue(result.decision == DedupDecision.NEEDS_REVIEW || result.decision == DedupDecision.NEW_TRANSACTION)
    }

    // --- UPI ID ---

    @Test
    fun test22_sameAmountDirectionSameUpiId_isCorrelated() {
        val candidate1 = createValidatedCandidate("₹500 sent to ravi@upi", notificationKey = "key_upi_1", postTime = 1000000L)
        val candidate2 = createValidatedCandidate("Payment of ₹500 to ravi@upi", notificationKey = "key_upi_2", postTime = 1005000L)

        val existing = listOf(TransactionRecord.fromValidated(candidate1))
        val result = TransactionDeduplicator.evaluate(candidate2, existing)

        assertEquals(DedupDecision.CORRELATED, result.decision)
        assertEquals(MatchStrategy.STRONG_FINGERPRINT, result.strategy)
    }

    @Test
    fun test23_sameAmountDirectionDifferentUpiIds_isNewTransaction() {
        val candidate1 = createValidatedCandidate("₹500 sent to ravi@upi", notificationKey = "key_upi_1", postTime = 1000000L)
        val candidate2 = createValidatedCandidate("₹500 sent to rahul@upi", notificationKey = "key_upi_2", postTime = 1002000L)

        val existing = listOf(TransactionRecord.fromValidated(candidate1))
        val result = TransactionDeduplicator.evaluate(candidate2, existing)

        assertEquals(DedupDecision.NEW_TRANSACTION, result.decision)
    }

    // --- Merchant Normalization ---

    @Test
    fun test24_merchantCasingNormalizedAsCompatible() {
        val candidate1 = createValidatedCandidate("Paid ₹500 to Amazon India", notificationKey = "key_1", postTime = 1000000L)
        val candidate2 = createValidatedCandidate("Paid ₹500 to AMAZON INDIA", notificationKey = "key_2", postTime = 1005000L)

        val existing = listOf(TransactionRecord.fromValidated(candidate1))
        val result = TransactionDeduplicator.evaluate(candidate2, existing)

        assertEquals(DedupDecision.CORRELATED, result.decision)
    }

    @Test
    fun test25_differentEntitiesNotIdenticalBasedOnlyOnSimilarity() {
        val candidate1 = createValidatedCandidate("Paid ₹500 to Amazon", notificationKey = "key_1", postTime = 1000000L)
        val candidate2 = createValidatedCandidate("Paid ₹500 to Amazon Pay", notificationKey = "key_2", postTime = 1005000L)

        val existing = listOf(TransactionRecord.fromValidated(candidate1))
        val result = TransactionDeduplicator.evaluate(candidate2, existing)

        assertEquals(DedupDecision.NEW_TRANSACTION, result.decision)
    }

    // --- Manual Transactions ---

    @Test
    fun test26_manualExpenseAndNotification_isNotDuplicateWithoutReference() {
        val notifCandidate = createValidatedCandidate("₹500 paid to Ravi", notificationKey = "notif_10", postTime = 1000000L)
        val manualRecord = TransactionRecord(
            id = "manual_1",
            sourceType = TransactionSourceType.MANUAL,
            amount = 500.0,
            merchant = "Ravi",
            direction = TransactionDirection.DEBIT,
            timestamp = 1002000L
        )

        val result = TransactionDeduplicator.evaluate(notifCandidate, listOf(manualRecord))

        // Never automatically merge manual expense without explicit transaction reference
        assertEquals(DedupDecision.NEW_TRANSACTION, result.decision)
    }

    // --- Missing Fields ---

    @Test
    fun test27_bothMissingMerchantAndReference_isNotDuplicate() {
        val candidate1 = createValidatedCandidate("Your account debited by Rs 250", notificationKey = "key_1", postTime = 1000000L)
        val candidate2 = createValidatedCandidate("Your account debited by Rs 250", notificationKey = "key_2", postTime = 1002000L)

        val existing = listOf(TransactionRecord.fromValidated(candidate1))
        val result = TransactionDeduplicator.evaluate(candidate2, existing)

        assertEquals(DedupDecision.NEW_TRANSACTION, result.decision)
    }

    @Test
    fun test28_smsHasUtrNotificationHasFingerprintAgreement_isCorrelated() {
        val notifCandidate = createValidatedCandidate("₹500 paid to Ravi. A/c XX1234", notificationKey = "notif_1", postTime = 1000000L)
        val smsRecord = TransactionRecord(
            id = "sms_utr",
            sourceType = TransactionSourceType.SMS_HISTORY,
            amount = 500.0,
            direction = TransactionDirection.DEBIT,
            merchant = "Ravi",
            accountSuffix = "1234",
            utr = "ABC123456789",
            timestamp = 1005000L
        )

        val result = TransactionDeduplicator.evaluate(notifCandidate, listOf(smsRecord))

        assertEquals(DedupDecision.CORRELATED, result.decision)
    }

    // --- Noisy Candidates ---

    @Test
    fun test29_rejectedStep6Candidate_excludedFromDeduplication() {
        val notif = NotificationNormalizer.normalize(
            CapturedNotificationInfo(
                packageName = "com.google.android.apps.nbu.paisa.user",
                notificationKey = "key_otp",
                postTime = 1000L,
                text = "Your OTP for the ₹500 transaction is 123456"
            )
        )!!
        val classification = FinancialClassifier.classify(notif)
        val candidate = StructuredTransactionExtractor.extractDirect(notif, classification)
        val validated = TransactionValidator.validate(candidate, classification)
        assertTrue(validated.isRejected)

        val existing = listOf(
            TransactionRecord(id = "rec1", amount = 500.0, timestamp = 1000L)
        )

        val result = TransactionDeduplicator.evaluate(validated, existing)
        assertEquals(MatchStrategy.REJECTED_INPUT, result.strategy)
        assertNotEquals(DedupDecision.DUPLICATE, result.decision)
        assertNotEquals(DedupDecision.CORRELATED, result.decision)
    }

    @Test
    fun test30_needsReviewStep6Candidate_handledConservatively() {
        val candidate = createValidatedCandidate("Payment successful", notificationKey = "key_partial")
        assertTrue(candidate.needsReview)

        val existing = listOf(
            TransactionRecord(id = "rec1", amount = 500.0, timestamp = 1000L)
        )

        val result = TransactionDeduplicator.evaluate(candidate, existing)
        assertNotEquals(DedupDecision.DUPLICATE, result.decision)
        assertNotEquals(DedupDecision.CORRELATED, result.decision)
    }
}
