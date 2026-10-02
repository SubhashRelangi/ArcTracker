package com.subhashrelangi.arctracker.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.subhashrelangi.arctracker.BuildConfig
import com.subhashrelangi.arctracker.service.NotificationPermissionHelper
import com.subhashrelangi.arctracker.service.SmsPermissionHelper
import com.subhashrelangi.arctracker.settings.MonitoringSettingsRepository
import com.subhashrelangi.arctracker.ui.theme.ArcColors

// ==========================================
// Color Tokens matching Ui-Designs/SetUpPage.png
// ==========================================
private val SetupBgColor = ArcColors.Background // #090C10
private val CardSurface = Color(0xFF12151D)
private val CardBorder = Color(0xFF222836)

// Shield & Security
private val ShieldPillBg = Color(0xFF0D251A)
private val ShieldPillBorder = Color(0xFF164E35)
private val EmeraldAccent = Color(0xFF00E676)
private val GreenActive = Color(0xFF10B981)

// Amber / Priority / Warning
private val AmberAccent = Color(0xFFF59E0B)
private val AmberIconBg = Color(0xFF261D0C)
private val AmberIconBorder = Color(0xFF453314)
private val AmberPillBg = Color(0xFF2B200C)
private val AmberPillBorder = Color(0xFF4A3614)
private val AmberWarnBg = Color(0xFF1A1409)
private val AmberWarnBorder = Color(0xFF382A0F)

// Indigo / SMS Backfill
private val BackfillPillBg = Color(0xFF1C2230)
private val BackfillPillBorder = Color(0xFF2D374D)
private val BackfillText = Color(0xFF818CF8)

// Typography colors
private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF64748B)

/**
 * ArcTracker Local-first Setup Screen matching Ui-Designs/SetUpPage.png exactly.
 *
 * Provides permission explanations and triggers for:
 * 1. Notification Listener Access (real-time UPI alerts)
 * 2. SMS Inbox Telephony Access (historical transaction backfill)
 *
 * Integrated with real system permission checkers and settings recovery across process death.
 */
@Composable
fun LocalFirstSetupScreen(
    onSetupCompleted: (autoScanSms: Boolean) -> Unit,
    onSkipToDemoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val settingsRepo = remember {
        MonitoringSettingsRepository.getInstance(context)
    }

    var isNotificationGranted by remember {
        mutableStateOf(NotificationPermissionHelper.isNotificationAccessGranted(context))
    }

    var isSmsGranted by remember {
        mutableStateOf(SmsPermissionHelper.isSmsPermissionGranted(context))
    }

    var autoScanSmsArchive by remember {
        mutableStateOf(settingsRepo.isAutoSmsScanArchiveEnabled())
    }

    var isWhyRequiredExpanded by remember {
        mutableStateOf(false)
    }

    var showHelpDialog by remember {
        mutableStateOf(false)
    }

    // Dynamic permission launcher for Android SMS
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isSmsGranted = granted
        if (granted) {
            SmsPermissionHelper.setInitialImportCompleted(context, true)
        }
    }

    // Dynamic intent launcher for Notification Listener settings
    val notificationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = NotificationPermissionHelper.isNotificationAccessGranted(context)
        isNotificationGranted = granted
    }

    // Observe lifecycle ON_RESUME so when the user returns from Android Settings,
    // permission states update immediately and accurately.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isNotificationGranted = NotificationPermissionHelper.isNotificationAccessGranted(context)
                val smsNow = SmsPermissionHelper.isSmsPermissionGranted(context)
                isSmsGranted = smsNow
                if (smsNow) {
                    SmsPermissionHelper.setInitialImportCompleted(context, true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SetupBgColor)
            .statusBarsPadding()
    ) {
        // ----------------------------------------------------
        // 1. Static Header: Logo, Name, Version & Help Button
        // ----------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ArcTracker Squircle Logo
                SetupBrandLogo(modifier = Modifier.size(38.dp))

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ArcTracker",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Version Badge pill (e.g. "v2.4 LOCAL")
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E232E))
                                .border(BorderStroke(1.dp, Color(0xFF2C3240)), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} LOCAL",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Local-first Setup",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            // Help Button (?)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF141722))
                    .border(BorderStroke(1.dp, Color(0xFF222836)), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(bounded = true, color = Color.White.copy(alpha = 0.2f)),
                        onClick = { showHelpDialog = true }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.HelpOutline,
                    contentDescription = "Help & Architecture",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // ----------------------------------------------------
        // Scrollable Body Content
        // ----------------------------------------------------
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // ----------------------------------------------------
            // 2. Encrypted Telemetry Shield Badge Pill
            // ----------------------------------------------------
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ShieldPillBg)
                    .border(BorderStroke(1.dp, ShieldPillBorder), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = EmeraldAccent,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ENCRYPTED TELEMETRY SHIELD",
                        color = EmeraldAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ----------------------------------------------------
            // 3. Setup Headline
            // ----------------------------------------------------
            Text(
                text = "Zero-Cloud Ingestion.\n100% On-Device Privacy.",
                color = TextWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 30.sp,
                letterSpacing = (-0.4).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ----------------------------------------------------
            // 4. Explanatory Paragraph
            // ----------------------------------------------------
            Text(
                text = "ArcTracker extracts financial evidence directly from your device's banking SMS alerts and UPI push notifications without bank logins, external servers, or cloud credentials.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ----------------------------------------------------
            // 5. Card 1: Notification Listener Service
            // ----------------------------------------------------
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Header Row: Icon, Title & Priority Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Amber Icon Container with sub-badge
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AmberIconBg)
                                .border(BorderStroke(1.dp, AmberIconBorder), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isNotificationGranted) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsNone,
                                contentDescription = null,
                                tint = AmberAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Title & Status
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Notification Listener\nService",
                                color = TextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 19.sp
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = if (isNotificationGranted) "Status: Active" else "Status: Action Required",
                                color = if (isNotificationGranted) GreenActive else AmberAccent,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Priority Tag
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isNotificationGranted) ShieldPillBg else AmberPillBg)
                                .border(
                                    BorderStroke(1.dp, if (isNotificationGranted) ShieldPillBorder else AmberPillBorder),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isNotificationGranted) "ACTIVE" else "PRIORITY",
                                color = if (isNotificationGranted) GreenActive else AmberAccent,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Captures instant debit/credit alerts from Google Pay, PhonePe, Paytm, CRED, and official banking apps the moment you tap to pay.",
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Technical panel
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF161922),
                        border = BorderStroke(1.dp, Color(0xFF232836)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Memory,
                                contentDescription = null,
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Runs as Android NotificationListenerService • No Internet Permission Required",
                                color = TextSecondary,
                                fontSize = 10.5.sp,
                                lineHeight = 14.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Grant Notification Access Button
                    if (!isNotificationGranted) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(color = Color.Black.copy(alpha = 0.2f))
                                ) {
                                    val intent = NotificationPermissionHelper.createNotificationAccessSettingsIntent()
                                    try {
                                        notificationSettingsLauncher.launch(intent)
                                    } catch (_: Exception) {
                                        NotificationPermissionHelper.openNotificationAccessSettings(context)
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = null,
                                    tint = Color(0xFF0B0E14),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Grant Notification Access",
                                    color = Color(0xFF0B0E14),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0F261D),
                            border = BorderStroke(1.dp, Color(0xFF1B5539))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = GreenActive,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Notification Access Granted",
                                    color = GreenActive,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Expandable "Why is this required?"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { isWhyRequiredExpanded = !isWhyRequiredExpanded }
                            )
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Why is this required?",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isWhyRequiredExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = isWhyRequiredExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            text = "Android's NotificationListenerService allows ArcTracker to observe system push notifications emitted by UPI and banking apps the moment transactions occur. Raw notifications are parsed locally and discarded immediately.",
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ----------------------------------------------------
            // 6. Card 2: SMS Inbox Telephony Access
            // ----------------------------------------------------
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Header Row: Icon, Title & Backfill Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        // SMS Icon Container
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF191E2B))
                                .border(BorderStroke(1.dp, Color(0xFF283144)), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Title & Status
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "SMS Inbox Telephony\nAccess",
                                color = TextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 19.sp
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = if (isSmsGranted) "Status: Ready" else "Status: Pending Step 2",
                                color = if (isSmsGranted) GreenActive else TextMuted,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Backfill Tag
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSmsGranted) ShieldPillBg else BackfillPillBg)
                                .border(
                                    BorderStroke(1.dp, if (isSmsGranted) ShieldPillBorder else BackfillPillBorder),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isSmsGranted) "READY" else "BACKFILL",
                                color = if (isSmsGranted) GreenActive else BackfillText,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Scans past 3–12 months of transactional SMS from financial institutions (HDFC, SBI, ICICI, Axis) to backfill historical balances and build your initial ledger.",
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Green Security Panel
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0C1F17),
                        border = BorderStroke(1.dp, Color(0xFF164834)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = GreenActive,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Local Regex Parsing • OTPs & Personal Messages Suppressed",
                                color = GreenActive,
                                fontSize = 10.5.sp,
                                lineHeight = 14.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Auto-Scan SMS Archive Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Auto-Scan SMS Archive",
                                color = TextWhite,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Discovers last 90 days passes",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Switch(
                            checked = autoScanSmsArchive,
                            onCheckedChange = {
                                autoScanSmsArchive = it
                                settingsRepo.setAutoSmsScanArchiveEnabled(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = GreenActive,
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFF262C3D),
                                uncheckedBorderColor = Color(0xFF384156)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Request SMS Permission Button
                    if (!isSmsGranted) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(color = Color.White.copy(alpha = 0.1f))
                                ) {
                                    smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF181C26),
                            border = BorderStroke(1.dp, Color(0xFF2C3242))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = TextWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Request SMS Permission",
                                    color = TextWhite,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0F261D),
                            border = BorderStroke(1.dp, Color(0xFF1B5539))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = GreenActive,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SMS Telephony Granted",
                                    color = GreenActive,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ----------------------------------------------------
            // 7. Android OS Process Note
            // ----------------------------------------------------
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = AmberWarnBg,
                border = BorderStroke(1.dp, AmberWarnBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = AmberAccent,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "ANDROID OS PROCESS NOTE",
                            color = AmberAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Android terminates apps when toggling system settings; ArcTracker automatically recovers your setup state on return.",
                            color = Color(0xFFD1D5DB),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ----------------------------------------------------
            // 8. Primary Setup Action Button (Inline, visible when scrolled to end)
            // ----------------------------------------------------
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(14.dp),
                        spotColor = Color(0x66000000),
                        ambientColor = Color(0x33000000)
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(color = Color.Black.copy(alpha = 0.2f))
                    ) {
                        if (!isNotificationGranted) {
                            val intent = NotificationPermissionHelper.createNotificationAccessSettingsIntent()
                            try {
                                notificationSettingsLauncher.launch(intent)
                            } catch (_: Exception) {
                                NotificationPermissionHelper.openNotificationAccessSettings(context)
                            }
                        } else if (!isSmsGranted) {
                            smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                        } else {
                            // Both permissions granted: complete setup and continue!
                            settingsRepo.setAutoSmsScanArchiveEnabled(autoScanSmsArchive)
                            settingsRepo.setSetupInProgress(false)
                            settingsRepo.setInitialOnboardingCompleted(true)
                            settingsRepo.setGlobalEnabled(true)
                            settingsRepo.setNotificationTrackingEnabled(true)
                            settingsRepo.setSmsTrackingEnabled(true)
                            onSetupCompleted(autoScanSmsArchive)
                        }
                    },
                shape = RoundedCornerShape(14.dp),
                color = Color.White
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isNotificationGranted && isSmsGranted) {
                            if (autoScanSmsArchive) "Continue to SMS Import Wizard" else "Complete Setup & Continue"
                        } else {
                            "Enable Permissions & Start Setup"
                        },
                        color = Color(0xFF090C10),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF090C10),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Action: Skip setup and explore demo mode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(color = Color.White.copy(alpha = 0.1f)),
                        onClick = onSkipToDemoClick
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Skip setup and explore demo mode",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Help & Architecture Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            containerColor = Color(0xFF141722),
            title = {
                Text(
                    text = "Local-First Architecture",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "ArcTracker processes all financial transactions entirely on your phone.\n\n" +
                                "• Zero cloud servers: Database resides in your device's protected internal storage.\n" +
                                "• Telemetry Shield: No network connections or sockets opened for financial data.\n" +
                                "• OTP Filter: All one-time-passwords and non-financial messages are ignored instantly.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got it", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/**
 * Compact ArcTracker brand mark for setup header matching the reference visual design.
 */
@Composable
private fun SetupBrandLogo(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141720))
            .border(BorderStroke(1.dp, Color(0xFF262C3D)), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Inner white squircle
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            // Inner pure black circle
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // ArcTracker stylized 'A' lettermark
                Canvas(modifier = Modifier.size(14.dp)) {
                    val w = size.width
                    val h = size.height
                    val strokeW = 1.6.dp.toPx()

                    // Left curved stem
                    val leftStem = Path().apply {
                        moveTo(w * 0.28f, h * 0.82f)
                        quadraticBezierTo(w * 0.32f, h * 0.42f, w * 0.52f, h * 0.18f)
                    }
                    drawPath(
                        path = leftStem,
                        color = Color.White,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )

                    // Right stem
                    val rightStem = Path().apply {
                        moveTo(w * 0.52f, h * 0.18f)
                        lineTo(w * 0.66f, h * 0.82f)
                    }
                    drawPath(
                        path = rightStem,
                        color = Color.White,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )

                    // Arched crossbar extending past stems
                    val arcCrossbar = Path().apply {
                        moveTo(w * 0.10f, h * 0.52f)
                        quadraticBezierTo(w * 0.48f, h * 0.42f, w * 0.90f, h * 0.56f)
                    }
                    drawPath(
                        path = arcCrossbar,
                        color = Color.White,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}
