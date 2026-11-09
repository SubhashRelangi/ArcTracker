package com.subhashrelangi.arctracker.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Privacy and Security Preferences Manager (Milestone 16).
 *
 * Manages user-configurable security controls:
 * - Screen security (FLAG_SECURE to prevent screenshots and hide previews in recent apps).
 * - Masking financial accounts across UI.
 */
class PrivacySettingsManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _screenSecurityFlow = MutableStateFlow(isScreenSecurityEnabled())
    val screenSecurityFlow: StateFlow<Boolean> = _screenSecurityFlow.asStateFlow()

    private val _accountMaskingFlow = MutableStateFlow(isAccountMaskingEnabled())
    val accountMaskingFlow: StateFlow<Boolean> = _accountMaskingFlow.asStateFlow()

    private val _appLockFlow = MutableStateFlow(isAppLockEnabled())
    val appLockFlow: StateFlow<Boolean> = _appLockFlow.asStateFlow()

    fun isScreenSecurityEnabled(): Boolean {
        return prefs.getBoolean(KEY_SCREEN_SECURITY, true)
    }

    fun setScreenSecurityEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCREEN_SECURITY, enabled).commit()
        _screenSecurityFlow.value = enabled
    }

    fun isAccountMaskingEnabled(): Boolean {
        return prefs.getBoolean(KEY_ACCOUNT_MASKING, true)
    }

    fun setAccountMaskingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ACCOUNT_MASKING, enabled).apply()
        _accountMaskingFlow.value = enabled
    }

    fun isAppLockEnabled(): Boolean {
        return prefs.getBoolean(KEY_APP_LOCK, false)
    }

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK, enabled).apply()
        _appLockFlow.value = enabled
    }

    fun getLastActiveTimestamp(): Long {
        return prefs.getLong(KEY_LAST_ACTIVE_TIME, 0L)
    }

    fun setLastActiveTimestamp(time: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_ACTIVE_TIME, time).apply()
    }

    fun isSessionExpired(timeoutMillis: Long = APP_LOCK_TIMEOUT_MILLIS): Boolean {
        val lastActive = getLastActiveTimestamp()
        if (lastActive == 0L) return true
        return (System.currentTimeMillis() - lastActive) >= timeoutMillis
    }

    companion object {
        private const val PREFS_NAME = "arctracker_privacy_prefs"
        private const val KEY_SCREEN_SECURITY = "screen_security_enabled"
        private const val KEY_ACCOUNT_MASKING = "account_masking_enabled"
        private const val KEY_APP_LOCK = "app_lock_enabled"
        private const val KEY_LAST_ACTIVE_TIME = "last_app_active_time"
        const val APP_LOCK_TIMEOUT_MILLIS = 5 * 60 * 1000L // 5 minutes range lock

        @Volatile
        private var INSTANCE: PrivacySettingsManager? = null

        fun getInstance(context: Context): PrivacySettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrivacySettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
