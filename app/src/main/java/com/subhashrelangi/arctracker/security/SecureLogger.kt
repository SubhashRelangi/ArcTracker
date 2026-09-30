package com.subhashrelangi.arctracker.security

import android.util.Log
import com.subhashrelangi.arctracker.BuildConfig

/**
 * Production-quality, privacy-hardened Logger (Milestone 16).
 *
 * Guarantees:
 * - Automatically redacts cards, account numbers, and OTPs via [SensitiveDataSanitizer].
 * - In release builds, suppresses verbose debug and info logs to prevent logcat sniffing.
 * - Retains high-level, sanitized warning and error logs for crash diagnosis.
 */
object SecureLogger {

    var isDebugEnabled: Boolean = BuildConfig.DEBUG

    fun d(tag: String, message: String) {
        if (isDebugEnabled) {
            val sanitized = SensitiveDataSanitizer.sanitize(message)
            Log.d(tag, sanitized)
        }
    }

    fun i(tag: String, message: String) {
        if (isDebugEnabled) {
            val sanitized = SensitiveDataSanitizer.sanitize(message)
            Log.i(tag, sanitized)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val sanitized = SensitiveDataSanitizer.sanitize(message)
        if (throwable != null) {
            Log.w(tag, sanitized, throwable)
        } else {
            Log.w(tag, sanitized)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val sanitized = SensitiveDataSanitizer.sanitize(message)
        if (throwable != null) {
            Log.e(tag, sanitized, throwable)
        } else {
            Log.e(tag, sanitized)
        }
    }
}
