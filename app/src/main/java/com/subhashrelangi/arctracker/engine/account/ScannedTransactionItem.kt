package com.subhashrelangi.arctracker.engine.account

import com.subhashrelangi.arctracker.engine.pipeline.DedupDecision
import com.subhashrelangi.arctracker.engine.pipeline.MatchStrategy
import com.subhashrelangi.arctracker.engine.pipeline.TransactionSourceEvent
import com.subhashrelangi.arctracker.engine.pipeline.ValidatedTransactionCandidate

/**
 * Individual transaction item analyzed during the SCAN phase.
 * Holds all structured candidate intelligence needed for the subsequent IMPORT phase.
 */
data class ScannedTransactionItem(
    val candidate: ValidatedTransactionCandidate,
    val sourceEvent: TransactionSourceEvent,
    val plannedDecision: DedupDecision,
    val matchStrategy: MatchStrategy,
    val matchedRecordId: String? = null,
    val reason: String,
    val accountIdentity: FinancialAccountIdentity = FinancialAccountIdentity()
)
