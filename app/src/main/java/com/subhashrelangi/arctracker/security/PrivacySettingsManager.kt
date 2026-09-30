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

    fun isScreenSecurityEnabled(): Boolean {
        return prefs.getBoolean(KEY_SCREEN_SECURITY, false)
    }

    fun setScreenSecurityEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCREEN_SECURITY, enabled).apply()
        _screenSecurityFlow.value = enabled
    }

    fun isAccountMaskingEnabled(): Boolean {
        return prefs.getBoolean(KEY_ACCOUNT_MASKING, true)
    }

    fun setAccountMaskingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ACCOUNT_MASKING, enabled).apply()
        _accountMaskingFlow.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "arctracker_privacy_prefs"
        private const val KEY_SCREEN_SECURITY = "screen_security_enabled"
        private const val KEY_ACCOUNT_MASKING = "account_masking_enabled"

        @Volatile
        private var INSTANCE: PrivacySettingsManager? = null

        fun getInstance(context: Context): PrivacySettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrivacySettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
