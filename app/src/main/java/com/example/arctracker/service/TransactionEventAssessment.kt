package com.example.arctracker.service

/**
 * High-level determination of whether an actual financial transaction event occurred.
 */
enum class ActualEventStatus {
    /** Clear evidence of an actual completed or attempted financial transaction event. */
    TRUE,

    /** Clear evidence that no actual transaction event occurred (future, promotional, hypothetical, informational). */
    FALSE,

    /** Ambiguous or isolated context; cannot confirm an actual transaction event. */
    UNCERTAIN
}

/**
 * Semantic type of the evaluated notification or clause event.
 */
enum class TransactionEventType {
    COMPLETED,
    PENDING,
    FAILED,
    REVERSED,
    REFUNDED,
    FUTURE_ACTION,
    PROMOTIONAL,
    INFORMATIONAL,
    UNKNOWN
}

/**
 * Explainable assessment produced by the Actual Transaction Event Gate (Step 4.5).
 *
 * Distinguishes genuine money movement from future actions, promotional offers,
 * and informational statements across unknown applications.
 */
data class TransactionEventAssessment(
    val actualEvent: ActualEventStatus,
    val eventType: TransactionEventType,
    val directionHint: TransactionDirection = TransactionDirection.UNKNOWN,
    val isActionAssociatedWithAmount: Boolean = false,
    val positiveEvidence: List<String> = emptyList(),
    val negativeEvidence: List<String> = emptyList(),
    val diagnosticReasons: List<String> = emptyList()
)
