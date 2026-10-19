package com.subhashrelangi.arctracker.security

import android.util.Log
import com.subhashrelangi.arctracker.BuildConfig

/**
 * Production-quality, privacy-hardened Logger (Milestone 16).
 *
 * Guarantees:
 * - Automatically redacts cards, account numbers, OTPs, UPI handles, and reference numbers via [SensitiveDataSanitizer].
 * - In release builds, suppresses verbose debug and info logs to prevent logcat sniffing.
 * - Retains high-level, sanitized warning and error logs for crash diagnosis.
 * - Sanitizes exception messages so internal database queries, table structures, and storage paths are never leaked.
 */
object SecureLogger {

    var isDebugEnabled: Boolean = BuildConfig.DEBUG

    /**
     * Optional handler for unit testing or custom telemetry sinks.
     */
    var logHandler: ((priority: Int, tag: String, message: String) -> Unit)? = null

    fun d(tag: String, message: String) {
        if (isDebugEnabled) {
            val sanitized = SensitiveDataSanitizer.sanitize(message)
            logHandler?.invoke(Log.DEBUG, tag, sanitized)
            try {
                Log.d(tag, sanitized)
            } catch (_: Throwable) {}
        }
    }

    fun i(tag: String, message: String) {
        if (isDebugEnabled) {
            val sanitized = SensitiveDataSanitizer.sanitize(message)
            logHandler?.invoke(Log.INFO, tag, sanitized)
            try {
                Log.i(tag, sanitized)
            } catch (_: Throwable) {}
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val sanitized = SensitiveDataSanitizer.sanitize(message)
        val finalMessage = if (throwable != null) {
            val sanitizedError = SensitiveDataSanitizer.sanitizeErrorMessage(throwable)
            "$sanitized [${throwable.javaClass.simpleName}]: $sanitizedError"
        } else {
            sanitized
        }
        logHandler?.invoke(Log.WARN, tag, finalMessage)
        try {
            Log.w(tag, finalMessage)
        } catch (_: Throwable) {}
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val sanitized = SensitiveDataSanitizer.sanitize(message)
        val finalMessage = if (throwable != null) {
            val sanitizedError = SensitiveDataSanitizer.sanitizeErrorMessage(throwable)
            "$sanitized [${throwable.javaClass.simpleName}]: $sanitizedError"
        } else {
            sanitized
        }
        logHandler?.invoke(Log.ERROR, tag, finalMessage)
        try {
            Log.e(tag, finalMessage)
        } catch (_: Throwable) {}
    }
}
