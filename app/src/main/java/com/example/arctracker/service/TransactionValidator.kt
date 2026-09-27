package com.example.arctracker.service

import com.example.arctracker.utils.IgnoreRule
import kotlin.math.max
import kotlin.math.min

/**
 * Pure, deterministic validator and confidence assessor for [StructuredTransactionCandidate] (Step 6).
 *
 * Answers:
 * 1. Is the candidate structurally valid?
 * 2. What evidence supports each important field?
 * 3. How reliable is the candidate (EvidenceLevel: VERY_STRONG to NONE)?
 * 4. What is the decision state (ACCEPTABLE, NEEDS_REVIEW, REJECTED)?
 *
 * Does NOT:
 * - Insert into Room database
 * - Create or confirm expenses
 * - Perform deduplication or correlation (Step 7)
 */
object TransactionValidator {

    /**
     * Validates a candidate against its evidence and classification.
     *
     * @param candidate The extracted structured transaction candidate.
     * @param classification Optional classification result from Step 4.
     * @return [ValidatedTransactionCandidate] containing decision, evidence level, reasons, and signals.
     */
    fun validate(
        candidate: StructuredTransactionCandidate,
        classification: NotificationClassificationResult? = null,
        userAccounts: List<UserAccountContext> = emptyList()
    ): ValidatedTransactionCandidate {
        val validationReasons = mutableListOf<String>()
        val rejectionReasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val supportingSignals = mutableListOf<String>()
        val contradictingSignals = mutableListOf<String>()
        val fieldQualities = mutableMapOf<String, FieldEvidenceQuality>()

        var isStructurallyValid = true

        // ----------------------------------------------------
        // 1. Basic Structural Validation
        // ----------------------------------------------------

        // Amount validation
        val amount = candidate.amount
        if (amount != null) {
            when {
                amount.isNaN() -> {
                    isStructurallyValid = false
                    rejectionReasons.add("Amount is NaN")
                    contradictingSignals.add("AMOUNT_IS_NAN")
                }
                amount.isInfinite() -> {
                    isStructurallyValid = false
                    rejectionReasons.add("Amount is Infinite")
                    contradictingSignals.add("AMOUNT_IS_INFINITE")
                }
                amount <= 0.0 -> {
                    isStructurallyValid = false
                    rejectionReasons.add("Amount must be greater than 0 (was: $amount)")
                    contradictingSignals.add("AMOUNT_NON_POSITIVE")
                }
                else -> {
                    supportingSignals.add("VALID_POSITIVE_AMOUNT")
                    validationReasons.add("Valid positive transaction amount: $amount")
                }
            }
        } else {
            warnings.add("Transaction amount is absent")
            contradictingSignals.add("AMOUNT_IS_ABSENT")
        }

        // Currency validation
        val currency = candidate.currency
        if (currency != null) {
            supportingSignals.add("VALID_CURRENCY")
        }

        // Merchant / Counterparty validation
        val merchant = candidate.merchant ?: candidate.counterparty ?: candidate.upiId
        if (merchant != null) {
            when {
                merchant.isBlank() -> {
                    isStructurallyValid = false
                    rejectionReasons.add("Merchant string is blank")
                }
                !merchant.any { it.isLetterOrDigit() } -> {
                    isStructurallyValid = false
                    rejectionReasons.add("Merchant contains no letters or digits: '$merchant'")
                }
                merchant.startsWith("A/c", ignoreCase = true) || merchant.startsWith("Account", ignoreCase = true) -> {
                    isStructurallyValid = false
                    rejectionReasons.add("Merchant was erroneously extracted as account: '$merchant'")
                }
                else -> {
                    supportingSignals.add("VALID_MERCHANT_NAME")
                    validationReasons.add("Counterparty/merchant identified: $merchant")
                }
            }
        }

        // Reference ID validation
        val refId = candidate.referenceId ?: candidate.upiTransactionId ?: candidate.utr ?: candidate.rrn
        if (refId != null) {
            when {
                refId.isBlank() -> {
                    isStructurallyValid = false
                    rejectionReasons.add("Reference identifier is blank")
                }
                refId.equals("otp", ignoreCase = true) -> {
                    isStructurallyValid = false
                    rejectionReasons.add("Reference identifier is identified as OTP marker")
                }
                amount != null && refId == amount.toLong().toString() -> {
                    isStructurallyValid = false
                    rejectionReasons.add("Reference identifier duplicates the transaction amount")
                }
                else -> {
                    supportingSignals.add("VALID_REFERENCE_ID")
                    validationReasons.add("Explicit reference identifier detected: $refId")
                }
            }
        }

        // Account / Card suffix validation
        val cardSuffix = candidate.cardSuffix
        if (cardSuffix != null) {
            if (cardSuffix.length > 6) {
                isStructurallyValid = false
                rejectionReasons.add("Card suffix exceeds maximum length: $cardSuffix")
            } else {
                supportingSignals.add("VALID_CARD_SUFFIX")
            }
        }

        val accountSuffix = candidate.accountSuffix
        if (accountSuffix != null) {
            if (accountSuffix.length > 6) {
                isStructurallyValid = false
                rejectionReasons.add("Account suffix exceeds maximum length: $accountSuffix")
            } else {
                supportingSignals.add("VALID_ACCOUNT_SUFFIX")
                if (userAccounts.isNotEmpty()) {
                    val matchedAccount = userAccounts.firstOrNull { it.matchesAccountSuffix(accountSuffix) }
                    if (matchedAccount != null) {
                        supportingSignals.add("KNOWN_USER_ACCOUNT")
                        validationReasons.add("Account matches configured user account: ${matchedAccount.bankName ?: matchedAccount.accountSuffix}")
                    }
                }
            }
        }

        // ----------------------------------------------------
        // 2. Classification & Noise Consistency
        // ----------------------------------------------------
        if (classification != null) {
            if (classification.isNoise || classification.noiseCategory != NoiseCategory.NONE) {
                rejectionReasons.add("Notification classified as noise: ${classification.noiseCategory}")
                contradictingSignals.add("NOISE_CLASSIFICATION_${classification.noiseCategory}")
            }

            when (classification.financialRelevance) {
                FinancialRelevance.NON_FINANCIAL -> {
                    rejectionReasons.add("Classification is NON_FINANCIAL")
                    contradictingSignals.add("NON_FINANCIAL_CLASSIFICATION")
                }
                FinancialRelevance.UNCERTAIN -> {
                    warnings.add("Classification is UNCERTAIN: isolated financial keyword without completed action")
                    contradictingSignals.add("UNCERTAIN_CLASSIFICATION")
                }
                FinancialRelevance.FINANCIAL -> {
                    supportingSignals.add("FINANCIAL_CLASSIFICATION")
                    validationReasons.add("Classified as FINANCIAL with verified transaction action")
                }
            }
        }

        // ----------------------------------------------------
        // 3. Amount vs Evidence & Secondary Amounts
        // ----------------------------------------------------
        if (amount != null) {
            val amountEvidence = candidate.getEvidence("amount")
            if (amountEvidence != null) {
                val snippetLower = amountEvidence.sourceSnippet.lowercase()
                val isBalanceSnippet = snippetLower.contains("available balance") || snippetLower.contains("avl bal")
                val isOtpSnippet = snippetLower.contains("otp") || snippetLower.contains("one time password")
                val isLimitSnippet = snippetLower.contains("limit")

                if (isBalanceSnippet && !snippetLower.contains("debited") && !snippetLower.contains("paid") && !snippetLower.contains("credited")) {
                    rejectionReasons.add("Amount evidence corresponds to balance inquiry rather than transaction event")
                    contradictingSignals.add("AMOUNT_FROM_BALANCE")
                }
                if (isOtpSnippet) {
                    rejectionReasons.add("Amount evidence is embedded within an OTP message")
                    contradictingSignals.add("AMOUNT_FROM_OTP")
                }
                if (isLimitSnippet) {
                    rejectionReasons.add("Amount evidence corresponds to a transaction or credit limit")
                    contradictingSignals.add("AMOUNT_FROM_LIMIT")
                }
            } else if (candidate.evidence.isNotEmpty()) {
                warnings.add("Amount lacks explicit field evidence mapping")
            }

            // Check against secondary amounts
            for (sec in candidate.secondaryAmounts) {
                if (sec.type == SecondaryAmountType.BALANCE && sec.amount == amount && candidate.secondaryAmounts.none { it.amount != amount }) {
                    // Amount is identical to balance and no distinct action amount exists
                    warnings.add("Extracted amount matches recorded secondary balance")
                }
            }
        }

        // ----------------------------------------------------
        // 4. Direction & Status Consistency
        // ----------------------------------------------------
        when (candidate.direction) {
            TransactionDirection.DEBIT -> {
                supportingSignals.add("DIRECTION_DEBIT")
                validationReasons.add("Transaction direction is DEBIT")
            }
            TransactionDirection.CREDIT -> {
                supportingSignals.add("DIRECTION_CREDIT")
                validationReasons.add("Transaction direction is CREDIT")
            }
            TransactionDirection.UNKNOWN -> {
                warnings.add("Transaction direction is UNKNOWN or conflicting")
                contradictingSignals.add("DIRECTION_UNKNOWN")
            }
        }

        val status = candidate.status
        if (status != null) {
            when (status) {
                TransactionStatus.SUCCESS -> {
                    supportingSignals.add("STATUS_SUCCESS")
                    validationReasons.add("Transaction status is SUCCESS")
                }
                TransactionStatus.REFUNDED -> {
                    supportingSignals.add("STATUS_REFUNDED")
                    validationReasons.add("Transaction status is REFUNDED")
                }
                TransactionStatus.FAILED -> {
                    warnings.add("Transaction status is FAILED; not a completed transaction")
                    contradictingSignals.add("STATUS_FAILED")
                }
                TransactionStatus.PENDING -> {
                    warnings.add("Transaction status is PENDING; processing has not completed")
                    contradictingSignals.add("STATUS_PENDING")
                }
                TransactionStatus.REVERSED -> {
                    warnings.add("Transaction status is REVERSED")
                    contradictingSignals.add("STATUS_REVERSED")
                }
                TransactionStatus.DECLINED -> {
                    warnings.add("Transaction status is DECLINED")
                    contradictingSignals.add("STATUS_DECLINED")
                }
            }
        }

        // ----------------------------------------------------
        // 5. Field Evidence Quality Assessment
        // ----------------------------------------------------
        fieldQualities["amount"] = when {
            amount == null -> FieldEvidenceQuality.NONE
            candidate.getEvidence("amount")?.ruleOrPattern == "ACTION_VERB_ASSOCIATION" -> FieldEvidenceQuality.DIRECT_CONTEXT
            candidate.getEvidence("amount")?.ruleOrPattern == "CURRENCY_MARKER" -> FieldEvidenceQuality.INDIRECT_CONTEXT
            else -> FieldEvidenceQuality.WEAK
        }

        fieldQualities["merchant"] = when {
            merchant == null -> FieldEvidenceQuality.NONE
            candidate.getEvidence("merchant") != null || candidate.getEvidence("counterparty") != null -> FieldEvidenceQuality.DIRECT_CONTEXT
            else -> FieldEvidenceQuality.WEAK
        }

        fieldQualities["referenceId"] = when {
            candidate.referenceId == null && candidate.upiTransactionId == null && candidate.utr == null && candidate.rrn == null -> FieldEvidenceQuality.NONE
            else -> FieldEvidenceQuality.EXPLICIT
        }

        fieldQualities["direction"] = when (candidate.direction) {
            TransactionDirection.DEBIT, TransactionDirection.CREDIT -> FieldEvidenceQuality.DIRECT_CONTEXT
            TransactionDirection.UNKNOWN -> FieldEvidenceQuality.NONE
        }

        // ----------------------------------------------------
        // 6. Evidence Level & Heuristic Score
        // ----------------------------------------------------
        val hasFinancialClassification = classification?.financialRelevance == FinancialRelevance.FINANCIAL
        val isUncertain = classification?.financialRelevance == FinancialRelevance.UNCERTAIN
        val isNoiseOrNonFinancial = (classification?.isNoise == true) ||
                (classification?.financialRelevance == FinancialRelevance.NON_FINANCIAL)
        val hasValidPositiveAmount = amount != null && amount > 0.0 && isStructurallyValid
        val hasKnownDirection = candidate.direction == TransactionDirection.DEBIT || candidate.direction == TransactionDirection.CREDIT
        val hasSuccessfulStatus = status == null || status == TransactionStatus.SUCCESS || status == TransactionStatus.REFUNDED

        val evidenceLevel = when {
            !isStructurallyValid || isNoiseOrNonFinancial || rejectionReasons.isNotEmpty() -> {
                EvidenceLevel.NONE
            }
            hasFinancialClassification && hasValidPositiveAmount && hasKnownDirection && hasSuccessfulStatus && (merchant != null || refId != null || accountSuffix != null || cardSuffix != null) -> {
                EvidenceLevel.VERY_STRONG
            }
            hasFinancialClassification && hasValidPositiveAmount && hasKnownDirection && hasSuccessfulStatus -> {
                EvidenceLevel.STRONG
            }
            hasFinancialClassification && (hasValidPositiveAmount || status != null) -> {
                EvidenceLevel.MODERATE
            }
            isUncertain || hasValidPositiveAmount -> {
                EvidenceLevel.WEAK
            }
            else -> {
                EvidenceLevel.NONE
            }
        }

        // Heuristic points calculation (0 - 100), purely for explainable diagnostic rating (NOT a probability)
        var score = 0
        if (!isNoiseOrNonFinancial && isStructurallyValid && rejectionReasons.isEmpty()) {
            if (hasFinancialClassification) score += 30
            if (hasValidPositiveAmount) {
                score += if (fieldQualities["amount"] == FieldEvidenceQuality.DIRECT_CONTEXT) 30 else 15
            }
            if (hasKnownDirection) score += 15
            if (merchant != null) score += 10
            if (refId != null) score += 10
            if (status == TransactionStatus.SUCCESS || status == TransactionStatus.REFUNDED) score += 5

            if (isUncertain) score -= 30
            if (amount == null) score -= 25
            if (candidate.direction == TransactionDirection.UNKNOWN) score -= 15
            if (status in listOf(TransactionStatus.FAILED, TransactionStatus.PENDING, TransactionStatus.REVERSED)) score -= 20
        }
        val clampedScore = max(0, min(100, score))

        // ----------------------------------------------------
        // 7. Decision State (ACCEPTABLE / NEEDS_REVIEW / REJECTED)
        // ----------------------------------------------------
        val validationState = when {
            // REJECTED:
            !isStructurallyValid ||
                    isNoiseOrNonFinancial ||
                    rejectionReasons.isNotEmpty() ||
                    evidenceLevel == EvidenceLevel.NONE -> {
                ValidationState.REJECTED
            }

            // ACCEPTABLE:
            // Must have FINANCIAL classification, valid positive amount, known direction, strong/very-strong evidence,
            // no non-success terminal statuses (failed/pending/reversed require review).
            hasFinancialClassification &&
                    hasValidPositiveAmount &&
                    hasKnownDirection &&
                    hasSuccessfulStatus &&
                    (evidenceLevel == EvidenceLevel.VERY_STRONG || evidenceLevel == EvidenceLevel.STRONG) -> {
                ValidationState.ACCEPTABLE
            }

            // NEEDS_REVIEW:
            // Partial, missing amount, ambiguous direction, uncertain classification, or failed/pending status.
            else -> {
                ValidationState.NEEDS_REVIEW
            }
        }

        return ValidatedTransactionCandidate(
            candidate = candidate,
            validationState = validationState,
            evidenceLevel = evidenceLevel,
            isStructurallyValid = isStructurallyValid,
            validationReasons = validationReasons,
            rejectionReasons = rejectionReasons,
            warnings = warnings,
            supportingSignals = supportingSignals,
            contradictingSignals = contradictingSignals,
            fieldQualities = fieldQualities,
            heuristicScore = clampedScore
        )
    }

    /**
     * Convenience method to classify, extract, and validate directly from a [NormalizedNotification].
     */
    fun validate(
        notification: NormalizedNotification,
        ignoreRules: List<IgnoreRule> = emptyList()
    ): ValidatedTransactionCandidate {
        val classification = FinancialClassifier.classify(notification, ignoreRules)
        val candidate = StructuredTransactionExtractor.extractDirect(notification, classification)
        return validate(candidate, classification)
    }
}
