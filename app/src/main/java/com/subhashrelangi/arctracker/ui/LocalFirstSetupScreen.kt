package com.subhashrelangi.arctracker.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.subhashrelangi.arctracker.R
import com.subhashrelangi.arctracker.service.NotificationPermissionHelper
import com.subhashrelangi.arctracker.settings.MonitoringSettingsRepository

// ==========================================
// Light Theme Color Tokens
// ==========================================
private val SetupLightBackground = Color(0xFFFFFFFF)
private val BrandTextPrimary = Color(0xFF0F172A)
private val BrandTextSecondary = Color(0xFF64748B)

// Step badge
private val StepPillBg = Color(0xFFECFDF5)
private val StepPillBorder = Color(0xFFA7F3D0)
private val StepPillText = Color(0xFF059669)

// Cards
private val CardSurface = Color(0xFFF8FAFC)
private val CardBorder = Color(0xFFE2E8F0)
private val IconBoxBg = Color(0xFFF1F5F9)

// Status pills
private val StatusEnabledBg = Color(0xFFECFDF5)
private val StatusEnabledBorder = Color(0xFFA7F3D0)
private val StatusEnabledText = Color(0xFF059669)

private val StatusDisabledBg = Color(0xFFFEF2F2)
private val StatusDisabledBorder = Color(0xFFFECACA)
private val StatusDisabledText = Color(0xFFDC2626)

// Buttons
private val PrimaryBtnBg = Color(0xFF0F172A)
private val PrimaryBtnText = Color(0xFFFFFFFF)
private val BackBtnBg = Color(0xFFF8FAFC)
private val BackBtnBorder = Color(0xFFE2E8F0)

/**
 * Clean & essential Notification Permission Setup screen matching Light Theme:
 * 1. Top Bar: "ArcTracker" prominent title + "/ Onboarding" normal text on the left, Light theme brand logo on the right
 * 2. Status Row: "• STEP 1 OF 2" emerald pill badge
 * 3. Headline: "Capture transactions automatically"
 * 4. Subtitle: Clear on-device privacy explanation
 * 5. Essential Cards:
 *    - Notification Listener service status (Disabled / Enabled dynamic badge)
 *    - Android permission instructions info card
 * 6. Bottom Navigation Actions:
 *    - Left: Circular back button with left arrow
 *    - Right: Rounded pill continue/enable button with text & right arrow
 *    - Context-based secondary action: "Continue without access" if disabled, privacy assurance note if enabled
 */
@Composable
fun LocalFirstSetupScreen(
    onSetupCompleted: (autoScanSms: Boolean) -> Unit = {},
    onSkipToDemoClick: () -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    val settingsRepo = remember {
        MonitoringSettingsRepository.getInstance(context)
    }

    var isNotificationGranted by remember {
        mutableStateOf(NotificationPermissionHelper.isNotificationAccessGranted(context))
    }

    // Refresh permission status on resume (when returning from Android settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = NotificationPermissionHelper.isNotificationAccessGranted(context)
                if (isNotificationGranted != granted) {
                    isNotificationGranted = granted
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isNotificationGranted = NotificationPermissionHelper.isNotificationAccessGranted(context)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SetupLightBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // ----------------------------------------------------
            // 1. Top Bar Header
            // ----------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ArcTracker",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandTextPrimary,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "/ Onboarding",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Normal,
                        color = BrandTextSecondary
                    )
                }

                // Top Right: Light Theme Logo
                Image(
                    painter = painterResource(id = R.drawable.ic_logo_light),
                    contentDescription = "ArcTracker Light Theme Logo",
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ----------------------------------------------------
            // 2. Stepper Status Row
            // ----------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "• STEP 1 OF 2" pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(StepPillBg)
                        .border(BorderStroke(1.dp, StepPillBorder), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(StepPillText)
                        )
                        Text(
                            text = "STEP 1 OF 2",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = StepPillText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // ----------------------------------------------------
            // 3. Headline & Description
            // ----------------------------------------------------
            Text(
                text = "Capture transactions\nautomatically",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 35.sp,
                letterSpacing = (-0.4).sp,
                color = BrandTextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "ArcTracker listens for supported banking and payment notifications locally, recording logs instantly without cloud routing.",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 21.sp,
                color = BrandTextSecondary
            )

            Spacer(modifier = Modifier.height(26.dp))

            // ----------------------------------------------------
            // 4. Essential Permission Cards
            // ----------------------------------------------------

            // Card 1: Notification Listener Service Status Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bell Icon in rounded box
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(IconBoxBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsNone,
                                contentDescription = null,
                                tint = if (isNotificationGranted) StepPillText else BrandTextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Notification Listener",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandTextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Android System Service",
                                fontSize = 12.sp,
                                color = BrandTextSecondary
                            )
                        }
                    }

                    // Status Pill (Disabled / Enabled)
                    val statusBg = if (isNotificationGranted) StatusEnabledBg else StatusDisabledBg
                    val statusBorder = if (isNotificationGranted) StatusEnabledBorder else StatusDisabledBorder
                    val statusText = if (isNotificationGranted) StatusEnabledText else StatusDisabledText
                    val statusLabel = if (isNotificationGranted) "Enabled" else "Disabled"

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(statusBg)
                            .border(BorderStroke(1.dp, statusBorder), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusText)
                            )
                            Text(
                                text = statusLabel,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = statusText
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Card 2: Essential Explanation / Info Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = BrandTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "Android isolates financial alert listeners. Tapping enable will direct you directly to Android's Device & App Notifications manager where ArcTracker can be authorized.",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = BrandTextSecondary
                    )
                }
            }

            // Flexible dynamic spacer absorbing viewport height
            Spacer(modifier = Modifier.weight(1f))

            // ----------------------------------------------------
            // 5. Bottom Navigation Actions
            // ----------------------------------------------------
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Navigation Action Row: Circular Back Button on Left + Compact Action Button on Right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Circular Back Button with Left Arrow
                    Surface(
                        modifier = Modifier
                            .size(52.dp)
                            .shadow(
                                elevation = 1.dp,
                                shape = CircleShape,
                                ambientColor = Color(0x08000000),
                                spotColor = Color(0x10000000)
                            )
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(color = Color.Black.copy(alpha = 0.1f)),
                                onClick = {
                                    onBackClick?.invoke()
                                }
                            ),
                        shape = CircleShape,
                        color = BackBtnBg,
                        border = BorderStroke(1.dp, BackBtnBorder)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Previous Screen",
                                tint = BrandTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Right: Primary Next/Enable Action Button
                    Surface(
                        modifier = Modifier
                            .height(52.dp)
                            .shadow(
                                elevation = 2.dp,
                                shape = RoundedCornerShape(26.dp),
                                ambientColor = Color(0x10000000),
                                spotColor = Color(0x1A000000)
                            )
                            .clip(RoundedCornerShape(26.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(color = Color.White.copy(alpha = 0.2f)),
                                onClick = {
                                    if (isNotificationGranted) {
                                        settingsRepo.setNotificationTrackingEnabled(true)
                                        settingsRepo.setGlobalEnabled(true)
                                        onSetupCompleted(true)
                                    } else {
                                        val intent = NotificationPermissionHelper.createNotificationAccessSettingsIntent()
                                        try {
                                            settingsLauncher.launch(intent)
                                        } catch (_: Exception) {
                                            context.startActivity(intent)
                                        }
                                    }
                                }
                            ),
                        shape = RoundedCornerShape(26.dp),
                        color = PrimaryBtnBg
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .fillMaxHeight(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isNotificationGranted) "Continue" else "Enable Access",
                                color = PrimaryBtnText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = PrimaryBtnText,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Context-based Secondary Action / Reassurance
                if (!isNotificationGranted) {
                    // When disabled: offer skip without feeling trapped
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(color = Color.Black.copy(alpha = 0.06f)),
                                onClick = {
                                    onSetupCompleted(false)
                                }
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Continue without access",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = BrandTextSecondary
                        )
                    }
                } else {
                    // When enabled: show positive privacy note
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = StepPillText,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Access active • 100% private on-device",
                            color = BrandTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun LocalFirstSetupScreenPreview() {
    MaterialTheme {
        LocalFirstSetupScreen()
    }
}
