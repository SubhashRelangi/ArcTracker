package com.subhashrelangi.arctracker.service

import com.subhashrelangi.arctracker.security.SecureLogger
import com.subhashrelangi.arctracker.security.SensitiveDataSanitizer
import java.util.Collections

/**
 * Structured diagnostic event categories for ArcTracker ingestion pipeline.
 *
 * Requirements:
 * Must never contain sensitive financial payloads, raw notification texts, SMS bodies,
 * account numbers, transaction amounts, or OTPs.
 */
enum class DiagnosticCategory {
    LISTENER_CONNECTED,
    LISTENER_DISCONNECTED,
    NOTIFICATION_RECEIVED,
    NOTIFICATION_IGNORED,
    CONTENT_UNAVAILABLE,
    SOURCE_NOT_SUPPORTED,
    PARSE_FAILED,
    AMOUNT_UNAVAILABLE,
    DIRECTION_AMBIGUOUS,
    VALIDATION_REJECTED,
    DUPLICATE_SKIPPED,
    PERSISTENCE_FAILED,
    TRANSACTION_PERSISTED
}

/**
 * Privacy-safe diagnostic event record.
 */
data class DiagnosticEvent(
    val category: DiagnosticCategory,
    val packageName: String? = null,
    val reason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * In-memory thread-safe diagnostic event manager.
 * Holds recent events in an LRU/ring buffer for testing, UI status verification,
 * and logs structured diagnostic events safely through [SecureLogger].
 */
object DiagnosticEventManager {
    private const val TAG = "DiagnosticEventManager"
    private const val MAX_EVENTS = 200

    private val eventList: MutableList<DiagnosticEvent> =
        Collections.synchronizedList(mutableListOf<DiagnosticEvent>())

    private val listeners: MutableList<(DiagnosticEvent) -> Unit> =
        Collections.synchronizedList(mutableListOf())

    /**
     * Records a privacy-safe diagnostic event.
     * Sanitizes reason and packageName to guarantee no PII/amounts are recorded.
     */
    fun logEvent(
        category: DiagnosticCategory,
        packageName: String? = null,
        reason: String? = null
    ): DiagnosticEvent {
        val safePkg = packageName?.take(100)
        val safeReason = reason?.let { SensitiveDataSanitizer.sanitize(it).take(200) }
        val event = DiagnosticEvent(
            category = category,
            packageName = safePkg,
            reason = safeReason,
            timestamp = System.currentTimeMillis()
        )

        synchronized(eventList) {
            if (eventList.size >= MAX_EVENTS) {
                eventList.removeAt(0)
            }
            eventList.add(event)
        }

        SecureLogger.d(TAG, "Diagnostic: category=${event.category}, pkg=${event.packageName}, reason=${event.reason}")

        synchronized(listeners) {
            for (listener in listeners) {
                try {
                    listener(event)
                } catch (e: Throwable) {
                    SecureLogger.e(TAG, "Listener exception in logEvent", e)
                }
            }
        }

        return event
    }

    /**
     * Retrieves all recorded diagnostic events.
     */
    fun getEvents(): List<DiagnosticEvent> {
        return synchronized(eventList) {
            eventList.toList()
        }
    }

    /**
     * Retrieves all recorded diagnostic events for a specific category.
     */
    fun getEventsForCategory(category: DiagnosticCategory): List<DiagnosticEvent> {
        return synchronized(eventList) {
            eventList.filter { it.category == category }
        }
    }

    /**
     * Adds an event listener.
     */
    fun addListener(listener: (DiagnosticEvent) -> Unit) {
        listeners.add(listener)
    }

    /**
     * Removes an event listener.
     */
    fun removeListener(listener: (DiagnosticEvent) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Clears all recorded events (useful for tests).
     */
    fun clearEvents() {
        synchronized(eventList) {
            eventList.clear()
        }
    }
}
