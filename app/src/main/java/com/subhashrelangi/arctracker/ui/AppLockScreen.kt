package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.subhashrelangi.arctracker.security.BiometricAuthHelper

/**
 * Minimalist, blank App Lock screen.
 *
 * Provides a clean blank surface matching the app theme and immediately prompts
 * for Fingerprint, Face ID, or device PIN confirmation. Tapping anywhere re-prompts
 * if the system authentication dialog is dismissed.
 */
@Composable
fun AppLockScreen(
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    fun promptAuth() {
        if (activity != null) {
            BiometricAuthHelper.authenticate(
                activity = activity,
                title = "Unlock ArcTracker",
                subtitle = "Confirm Fingerprint, Face ID, or PIN",
                onSuccess = onUnlockSuccess
            )
        }
    }

    // Automatically trigger biometric / PIN confirmation on launch and whenever the screen resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                promptAuth()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Clean blank surface matching app background; tapping anywhere re-prompts
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                promptAuth()
            }
    )
}
