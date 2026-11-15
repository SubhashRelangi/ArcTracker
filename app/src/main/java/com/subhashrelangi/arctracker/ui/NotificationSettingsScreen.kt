package com.subhashrelangi.arctracker.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.subhashrelangi.arctracker.engine.notification.DailyNotificationScheduler
import com.subhashrelangi.arctracker.engine.notification.NotificationDeliveryHelper
import com.subhashrelangi.arctracker.engine.notification.NotificationSettingsRepository
import com.subhashrelangi.arctracker.ui.core.ArcModalBottomSheet
import com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader
import com.subhashrelangi.arctracker.ui.theme.ArcColors
import java.util.Locale

@Composable
fun NotificationSettingsScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val notifRepo = remember { NotificationSettingsRepository.getInstance(context) }
    val notifPrefs by notifRepo.preferencesFlow.collectAsState()

    var hasSystemPermission by remember {
        mutableStateOf(NotificationDeliveryHelper.hasNotificationPermission(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasSystemPermission = NotificationDeliveryHelper.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var timePickerTarget by remember { mutableStateOf<String?>(null) } // "DIGEST", "CASH", "WEEKLY"
    var showDailyLimitSheet by remember { mutableStateOf(false) }
    var limitInputText by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = ArcColors.Background,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ArcColors.Background)
            ) {
                ArcTrackerHeader(
                    title = "Notifications",
                    subtitle = "Preferences & Reminders",
                    onBackClick = onNavigateBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ArcColors.Background)
                        .statusBarsPadding(),
                    horizontalPadding = 16.dp,
                    actions = {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(ArcColors.CardBackground)
                                .border(BorderStroke(1.dp, ArcColors.Border), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = ArcColors.Primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }
        },
        containerColor = ArcColors.Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // System Notification Permission Banner
            if (!hasSystemPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ArcColors.CardBackground
                    ),
                    border = BorderStroke(1.dp, ArcColors.Border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ArcColors.SurfaceHighlight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsOff,
                                contentDescription = null,
                                tint = ArcColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "System Notifications Disabled",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ArcColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "System permissions are currently off. Tap Enable to allow alerts.",
                                fontSize = 11.5.sp,
                                color = ArcColors.TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val fallback = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(fallback)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArcColors.Primary
                            ),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Enable", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Quick Master Controls Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ArcColors.CardBackground),
                border = BorderStroke(1.dp, ArcColors.Border)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ArcColors.SurfaceHighlight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                tint = ArcColors.Primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "All Notifications Master Switch",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = ArcColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (notifPrefs.isNotificationsMasterEnabled) "Active • Notifications allowed" else "Silenced • All notifications muted",
                                fontSize = 11.5.sp,
                                color = ArcColors.TextMuted
                            )
                        }

                        Switch(
                            checked = notifPrefs.isNotificationsMasterEnabled,
                            onCheckedChange = { isEnabled ->
                                notifRepo.setMasterEnabled(isEnabled)
                                DailyNotificationScheduler.scheduleAll(context)
                                Toast.makeText(
                                    context,
                                    if (isEnabled) "Notifications enabled" else "All notifications muted",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.scale(0.85f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ArcColors.Primary,
                                checkedBorderColor = Color.Transparent,
                                uncheckedThumbColor = if (ArcColors.isDark) Color(0xFF71717A) else Color.White,
                                uncheckedTrackColor = if (ArcColors.isDark) ArcColors.SurfaceHighlight else Color(0xFFCBD5E1),
                                uncheckedBorderColor = Color.Transparent
                            )
                        )
                    }

                    HorizontalDivider(color = ArcColors.BorderSubtle, thickness = 1.dp)

                    // Quick Action Buttons: Enable All vs Disable All
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                notifRepo.disableAllNotifications()
                                DailyNotificationScheduler.scheduleAll(context)
                                Toast.makeText(context, "All notification types disabled", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ArcColors.Border),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsOff,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = ArcColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Disable All",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ArcColors.TextSecondary
                            )
                        }

                        Button(
                            onClick = {
                                notifRepo.enableAllNotifications()
                                DailyNotificationScheduler.scheduleAll(context)
                                Toast.makeText(context, "All notification types enabled", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ArcColors.Primary),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Enable All",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Notification Types Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NOTIFICATION CHANNELS & PROMPTS",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = ArcColors.TextMuted,
                    letterSpacing = 0.8.sp
                )
            }

            // Individual Notification Toggles Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ArcColors.CardBackground),
                border = BorderStroke(1.dp, ArcColors.Border)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val isMasterActive = notifPrefs.isNotificationsMasterEnabled

                    // 1. Weekly Spending Summary
                    val weeklyTimeStr = formatTime12Hour(notifPrefs.weeklySummaryHour, notifPrefs.weeklySummaryMinute)
                    NotificationSwitchRow(
                        icon = Icons.Outlined.DateRange,
                        title = "Weekly Spending Summary",
                        subtitle = "Weekly digest of total outflow, category trends & insights every Sunday at $weeklyTimeStr",
                        checked = notifPrefs.isWeeklySummaryEnabled,
                        enabled = isMasterActive,
                        onCheckedChange = { isEnabled ->
                            notifRepo.setWeeklySummaryEnabled(isEnabled)
                            DailyNotificationScheduler.scheduleAll(context)
                        }
                    )

                    if (notifPrefs.isWeeklySummaryEnabled && isMasterActive) {
                        NotificationTimeItemRow(
                            title = "Weekly Summary Time",
                            badgeText = "Sunday • $weeklyTimeStr",
                            onClick = { timePickerTarget = "WEEKLY" }
                        )
                    }

                    HorizontalDivider(color = ArcColors.BorderSubtle, thickness = 1.dp, modifier = Modifier.padding(start = 64.dp, end = 16.dp))

                    // 2. Nightly Expense Digest
                    val digestTimeStr = formatTime12Hour(notifPrefs.dailyDigestHour, notifPrefs.dailyDigestMinute)
                    NotificationSwitchRow(
                        icon = Icons.Outlined.NightsStay,
                        title = "Nightly Expense Digest",
                        subtitle = "Summary of today's spending, top categories & zero-spend celebrations at $digestTimeStr",
                        checked = notifPrefs.isDailyDigestEnabled,
                        enabled = isMasterActive,
                        onCheckedChange = { isEnabled ->
                            notifRepo.setDailyDigestEnabled(isEnabled)
                            DailyNotificationScheduler.scheduleAll(context)
                        }
                    )

                    if (notifPrefs.isDailyDigestEnabled && isMasterActive) {
                        NotificationTimeItemRow(
                            title = "Digest Delivery Time",
                            badgeText = digestTimeStr,
                            onClick = { timePickerTarget = "DIGEST" }
                        )
                    }

                    HorizontalDivider(color = ArcColors.BorderSubtle, thickness = 1.dp, modifier = Modifier.padding(start = 64.dp, end = 16.dp))

                    // 3. Evening Liquid Cash Check-in
                    val cashTimeStr = formatTime12Hour(notifPrefs.cashReminderHour, notifPrefs.cashReminderMinute)
                    NotificationSwitchRow(
                        icon = Icons.Outlined.Payments,
                        title = "Evening Cash Check-in",
                        subtitle = "Gentle prompt at $cashTimeStr to record tea, transit, or grocery cash expenses",
                        checked = notifPrefs.isCashReminderEnabled,
                        enabled = isMasterActive,
                        onCheckedChange = { isEnabled ->
                            notifRepo.setCashReminderEnabled(isEnabled)
                            DailyNotificationScheduler.scheduleAll(context)
                        }
                    )

                    if (notifPrefs.isCashReminderEnabled && isMasterActive) {
                        NotificationTimeItemRow(
                            title = "Cash Reminder Time",
                            badgeText = cashTimeStr,
                            onClick = { timePickerTarget = "CASH" }
                        )
                    }

                    HorizontalDivider(color = ArcColors.BorderSubtle, thickness = 1.dp, modifier = Modifier.padding(start = 64.dp, end = 16.dp))

                    // 4. Budget & Threshold Alerts
                    NotificationSwitchRow(
                        icon = Icons.Outlined.WarningAmber,
                        title = "Budget Threshold Alerts",
                        subtitle = "Instant alert when spending reaches 80% warning or exceeds 100% monthly budget",
                        checked = notifPrefs.isBudgetAlertsEnabled,
                        enabled = isMasterActive,
                        onCheckedChange = { isEnabled ->
                            notifRepo.setBudgetAlertsEnabled(isEnabled)
                        }
                    )

                    HorizontalDivider(color = ArcColors.BorderSubtle, thickness = 1.dp, modifier = Modifier.padding(start = 64.dp, end = 16.dp))

                    // 5. Daily Spending Target
                    val dailyLimitText = notifPrefs.dailySpendLimit?.let { "₹${String.format(Locale.US, "%,.0f", it)}" } ?: "Not Configured"
                    NotificationItemRowWithAction(
                        icon = Icons.Outlined.Speed,
                        title = "Daily Spend Limit",
                        subtitle = "Instant alert when single-day spending crosses target",
                        badgeText = dailyLimitText,
                        enabled = isMasterActive,
                        onClick = {
                            limitInputText = notifPrefs.dailySpendLimit?.toInt()?.toString() ?: ""
                            showDailyLimitSheet = true
                        }
                    )

                    HorizontalDivider(color = ArcColors.BorderSubtle, thickness = 1.dp, modifier = Modifier.padding(start = 64.dp, end = 16.dp))

                    // 6. Review Queue Digest
                    NotificationSwitchRow(
                        icon = Icons.Outlined.RateReview,
                        title = "Pending Review Reminders",
                        subtitle = "Reminder digest if unreviewed transactions need your confirmation",
                        checked = notifPrefs.isReviewReminderEnabled,
                        enabled = isMasterActive,
                        onCheckedChange = { isEnabled ->
                            notifRepo.setReviewReminderEnabled(isEnabled)
                        }
                    )

                    HorizontalDivider(color = ArcColors.BorderSubtle, thickness = 1.dp, modifier = Modifier.padding(start = 64.dp, end = 16.dp))

                    // 7. ATM Cash Withdrawal Follow-up
                    NotificationSwitchRow(
                        icon = Icons.Outlined.LocalAtm,
                        title = "ATM Cash Withdrawal Follow-up",
                        subtitle = "Contextual prompt asking if you'd like to log spending after withdrawing cash",
                        checked = notifPrefs.isAtmFollowUpEnabled,
                        enabled = isMasterActive,
                        onCheckedChange = { isEnabled ->
                            notifRepo.setAtmFollowUpEnabled(isEnabled)
                        }
                    )

                    HorizontalDivider(color = ArcColors.BorderSubtle, thickness = 1.dp, modifier = Modifier.padding(start = 64.dp, end = 16.dp))

                    // 8. Large Transaction Alert
                    val highValueText = "₹${String.format(Locale.US, "%,.0f", notifPrefs.highValueThreshold)}"
                    NotificationSwitchRow(
                        icon = Icons.Outlined.ReceiptLong,
                        title = "Large Transaction Alert",
                        subtitle = "Instant alert on individual debit purchases exceeding $highValueText",
                        checked = notifPrefs.isHighValueAlertEnabled,
                        enabled = isMasterActive,
                        onCheckedChange = { isEnabled ->
                            notifRepo.setHighValueAlertEnabled(isEnabled)
                        }
                    )
                }
            }

            // Privacy Assurance Note
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = Color(0xFF15803D),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "100% On-Device Local Notifications • Zero Internet Tracking",
                    fontSize = 11.5.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.navigationBarsPadding().height(32.dp))
        }
    }

    // Time Selection Sheet
    timePickerTarget?.let { target ->
        val isDigest = target == "DIGEST"
        val isWeekly = target == "WEEKLY"
        val currentTimeHour = when (target) {
            "DIGEST" -> notifPrefs.dailyDigestHour
            "WEEKLY" -> notifPrefs.weeklySummaryHour
            else -> notifPrefs.cashReminderHour
        }
        val currentTimeMin = when (target) {
            "DIGEST" -> notifPrefs.dailyDigestMinute
            "WEEKLY" -> notifPrefs.weeklySummaryMinute
            else -> notifPrefs.cashReminderMinute
        }

        val options = listOf(
            Triple(19, 0, "7:00 PM"),
            Triple(20, 0, "8:00 PM"),
            Triple(20, 30, "8:30 PM"),
            Triple(21, 0, "9:00 PM"),
            Triple(21, 30, "9:30 PM"),
            Triple(22, 0, "10:00 PM")
        )

        ArcModalBottomSheet(
            onDismissRequest = { timePickerTarget = null },
            title = when {
                isWeekly -> "Weekly Summary Delivery Time"
                isDigest -> "Digest Delivery Time"
                else -> "Cash Reminder Time"
            },
            icon = Icons.Outlined.Schedule,
            maxHeight = 360.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Select preferred delivery time for this reminder:",
                    fontSize = 13.sp,
                    color = ArcColors.TextSecondary
                )

                options.chunked(3).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowOptions.forEach { (hour, minute, label) ->
                            val isSelected = currentTimeHour == hour && currentTimeMin == minute
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        when (target) {
                                            "DIGEST" -> notifRepo.setDailyDigestTime(hour, minute)
                                            "WEEKLY" -> notifRepo.setWeeklySummaryTime(1, hour, minute)
                                            else -> notifRepo.setCashReminderTime(hour, minute)
                                        }
                                        DailyNotificationScheduler.scheduleAll(context)
                                        timePickerTarget = null
                                    },
                                color = if (isSelected) ArcColors.Primary else ArcColors.SurfaceHighlight,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (isSelected) ArcColors.Primary else ArcColors.Border)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (isSelected) Color.White else ArcColors.TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { timePickerTarget = null },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Done", color = ArcColors.TextSecondary)
                }
            }
        }
    }

    // Daily Spend Limit Target Sheet
    if (showDailyLimitSheet) {
        ArcModalBottomSheet(
            onDismissRequest = { showDailyLimitSheet = false },
            title = "Daily Spending Limit",
            icon = Icons.Outlined.Speed,
            maxHeight = 380.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Set a daily spend ceiling. ArcTracker will immediately alert you when your settled expenses cross this amount today.",
                    fontSize = 13.sp,
                    color = ArcColors.TextSecondary,
                    lineHeight = 18.sp
                )

                OutlinedTextField(
                    value = limitInputText,
                    onValueChange = { str -> limitInputText = str.filter { it.isDigit() || it == '.' } },
                    label = { Text("Daily Target Amount (₹)") },
                    prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ArcColors.Primary,
                        unfocusedBorderColor = ArcColors.Border
                    )
                )

                // Quick preset chips
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1000.0, 2000.0, 5000.0).forEach { preset ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    limitInputText = preset.toInt().toString()
                                },
                            color = ArcColors.SurfaceHighlight,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ArcColors.Border)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("₹${preset.toInt()}", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = ArcColors.TextPrimary)
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            notifRepo.setDailySpendLimit(null)
                            showDailyLimitSheet = false
                            limitInputText = ""
                            Toast.makeText(context, "Daily spend limit cleared", Toast.LENGTH_SHORT).show()
                        },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("Clear Limit", color = ArcColors.TextSecondary)
                    }
                    Button(
                        onClick = {
                            val parsed = limitInputText.toDoubleOrNull()
                            if (parsed != null && parsed > 0.0) {
                                notifRepo.setDailySpendLimit(parsed)
                                Toast.makeText(context, "Daily target set to ₹${parsed.toInt()}", Toast.LENGTH_SHORT).show()
                            } else {
                                notifRepo.setDailySpendLimit(null)
                            }
                            showDailyLimitSheet = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArcColors.Primary),
                        shape = CircleShape,
                        modifier = Modifier.weight(1.2f).height(46.dp)
                    ) {
                        Text("Save Target", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ArcColors.SurfaceHighlight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ArcColors.TextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                color = ArcColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = ArcColors.TextMuted,
                lineHeight = 15.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            modifier = Modifier.scale(0.85f),
            thumbContent = if (checked) {
                {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                        tint = Color(0xFF047857)
                    )
                }
            } else null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ArcColors.Primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = if (ArcColors.isDark) Color(0xFF71717A) else Color.White,
                uncheckedTrackColor = if (ArcColors.isDark) ArcColors.SurfaceHighlight else Color(0xFFCBD5E1),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun NotificationTimeItemRow(
    title: String,
    badgeText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 64.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            color = ArcColors.TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ArcColors.SurfaceHighlight)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = ArcColors.Primary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ArcColors.TextMutedLight,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun NotificationItemRowWithAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeText: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ArcColors.SurfaceHighlight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ArcColors.TextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                color = ArcColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = ArcColors.TextMuted
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ArcColors.SurfaceHighlight)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = ArcColors.Primary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ArcColors.TextMutedLight,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun formatTime12Hour(hour: Int, minute: Int): String {
    val h = hour.coerceIn(0, 23)
    val m = minute.coerceIn(0, 59)
    val amPm = if (h < 12) "AM" else "PM"
    val displayHour = when (h % 12) {
        0 -> 12
        else -> h % 12
    }
    return String.format(Locale.US, "%d:%02d %s", displayHour, m, amPm)
}
