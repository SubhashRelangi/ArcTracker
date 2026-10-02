package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==========================================
// Initial Page Color Tokens (100% On-Device Dark Theme)
// ==========================================
private val InitialBgColor = Color(0xFF090C10)
private val CardSurfaceColor = Color(0xFF11141C)
private val CardBorderColor = Color(0xFF1D222E)

private val EmeraldAccent = Color(0xFF00E676)
private val EmeraldBadgeBg = Color(0xFF092318)
private val EmeraldBadgeBorder = Color(0xFF124D35)

private val MutedBadgeBg = Color(0xFF1F2432)
private val MutedBadgeBorder = Color(0xFF2C3446)

private val IconBoxBg = Color(0xFF181C26)
private val IconBoxBorder = Color(0xFF252C3D)

private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF94A3B8)
private val TextTertiary = Color(0xFF64748B)
private val AmberAccent = Color(0xFFF59E0B)

/**
 * Initial landing screen for ArcTracker when installed or launched in welcome mode.
 * Matches Ui-Designs/InitialPage.png exactly with full responsiveness and smooth scrolling.
 */
@Composable
fun InitialPageScreen(
    onGetStartedClick: () -> Unit = {},
    onExploreDemoClick: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(InitialBgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ----------------------------------------------------
            // 1. App Header Row: Icon, App Name, Badges
            // ----------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Logo Box
                ArcTrackerBrandLogo(
                    modifier = Modifier.size(44.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // App Name + Version Badge
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ArcTracker",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // "V2.4 LOCAL-FIRST" Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(EmeraldBadgeBg)
                                .border(BorderStroke(1.dp, EmeraldBadgeBorder), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Column {
                                Text(
                                    text = "V2.4 LOCAL-",
                                    color = EmeraldAccent,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black,
                                    lineHeight = 10.sp,
                                    letterSpacing = 0.4.sp
                                )
                                Text(
                                    text = "FIRST",
                                    color = EmeraldAccent,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black,
                                    lineHeight = 10.sp,
                                    letterSpacing = 0.4.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Autonomous Financial Ledger",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // "AIR-GAPPED" Pill Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(EmeraldBadgeBg)
                        .border(BorderStroke(1.dp, EmeraldBadgeBorder), RoundedCornerShape(18.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = "Air-Gapped Security",
                            tint = EmeraldAccent,
                            modifier = Modifier.size(15.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Column {
                            Text(
                                text = "AIR-",
                                color = EmeraldAccent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 10.sp,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "GAPPED",
                                color = EmeraldAccent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 10.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // ----------------------------------------------------
            // 2. Hero Section: Eyebrow, Title, Description
            // ----------------------------------------------------
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(EmeraldAccent, CircleShape)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "SOVEREIGN FINANCIAL PIPELINE",
                    color = EmeraldAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your Financial Ledger.\n100% On-Device.",
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 38.sp,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "ArcTracker transforms incoming Indian banking SMS alerts and UPI notifications into an automated, double-entry expense ledger in real time. Never leaves your phone.",
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ----------------------------------------------------
            // 3. Section Header: Architecture and Pipeline
            // ----------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INTERNAL ENGINE\nARCHITECTURE",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 14.sp,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "M3 Compose\nPipeline",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    lineHeight = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ----------------------------------------------------
            // 4. Feature Card 1: Notification Listener Daemon
            // ----------------------------------------------------
            ArchitectureFeatureCard(
                icon = Icons.Outlined.Notifications,
                iconTint = Color(0xFFE2E8F0),
                iconBoxBg = IconBoxBg,
                iconBoxBorder = IconBoxBorder,
                title = "Notification\nListener Daemon",
                badgeText = "No Internet\nPermission",
                badgeTextColor = TextSecondary,
                badgeBg = MutedBadgeBg,
                badgeBorder = MutedBadgeBorder,
                description = "Intercepts real-time payment alerts from Google Pay, PhonePe, Paytm, CRED, and banking apps sub-millisecond.",
                bottomContent = {
                    Text(
                        text = "GPay  •  PhonePe  •  HDFC  •  SBI UPI",
                        color = TextTertiary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ----------------------------------------------------
            // 5. Feature Card 2: Encrypted SQLite Vault
            // ----------------------------------------------------
            ArchitectureFeatureCard(
                icon = Icons.Rounded.Sync,
                iconTint = EmeraldAccent,
                iconBoxBg = EmeraldBadgeBg,
                iconBoxBorder = EmeraldBadgeBorder,
                title = "Encrypted SQLite\nVault",
                badgeText = "Isolated\nSandbox",
                badgeTextColor = EmeraldAccent,
                badgeBg = EmeraldBadgeBg,
                badgeBorder = EmeraldBadgeBorder,
                description = "All transactions, merchant mappings, and account balances stored in local AES-256 Room database. Zero external servers.",
                bottomContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VpnKey,
                            contentDescription = "Keystore Key",
                            tint = EmeraldAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Master Key derived via Android Keystore TPM",
                            color = TextTertiary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ----------------------------------------------------
            // 6. Feature Card 3: Telephony SMS Backfill
            // ----------------------------------------------------
            ArchitectureFeatureCard(
                icon = Icons.Outlined.Mail,
                iconTint = Color(0xFFE2E8F0),
                iconBoxBg = IconBoxBg,
                iconBoxBorder = IconBoxBorder,
                title = "Telephony SMS\nBackfill",
                badgeText = "Read-Only Local\nQuery",
                badgeTextColor = TextSecondary,
                badgeBg = MutedBadgeBg,
                badgeBorder = MutedBadgeBorder,
                description = "One-time scan of historical transactional messages to reconstruct past account statements and initial balances.",
                bottomContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = "Zero Cellular Data",
                            tint = AmberAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Optional  •  Zero cellular data transmission",
                            color = AmberAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ----------------------------------------------------
            // 7. Privacy & Security Guarantees Section
            // ----------------------------------------------------
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.GppGood,
                    contentDescription = "Guarantees",
                    tint = EmeraldAccent,
                    modifier = Modifier.size(19.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Privacy & Security Guarantees",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            PrivacyGuaranteeItem(text = "No Account Credentials or Bank Logins Required")
            Spacer(modifier = Modifier.height(10.dp))
            PrivacyGuaranteeItem(text = "Automatic Noise & OTP Suppression Filter")
            Spacer(modifier = Modifier.height(10.dp))
            PrivacyGuaranteeItem(text = "Hardware-Backed Key Derivation (Android Keystore)")

            Spacer(modifier = Modifier.height(28.dp))

            // ----------------------------------------------------
            // 8. Action Buttons
            // ----------------------------------------------------
            // Primary CTA: Get Started & Setup Ingestion
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(color = Color.Black.copy(alpha = 0.2f)),
                        onClick = onGetStartedClick
                    ),
                shape = RoundedCornerShape(14.dp),
                color = Color.White
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Get Started & Setup Ingestion",
                        color = Color(0xFF0F172A),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Arrow Forward",
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary CTA: Explore Demo Mode with Sample Ledger
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(BorderStroke(1.dp, CardBorderColor), RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(color = Color.White.copy(alpha = 0.1f)),
                        onClick = onExploreDemoClick
                    ),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF131722)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = "Demo Mode",
                        tint = Color(0xFFE2E8F0),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Explore Demo Mode with Sample Ledger",
                        color = Color(0xFFE2E8F0),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ----------------------------------------------------
            // 9. Footer Info
            // ----------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Storage,
                    contentDescription = "Open Source Local DB",
                    tint = TextTertiary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Encrypted Room DB v8  •  Apache 2.0 Open Source",
                    color = TextTertiary,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = "ISO/IEC 27001 Local Architecture  •  Zero Network Sockets Opened",
                color = TextTertiary,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Reusable architecture feature card matching the Initial Page design.
 */
@Composable
private fun ArchitectureFeatureCard(
    icon: ImageVector,
    iconTint: Color,
    iconBoxBg: Color,
    iconBoxBorder: Color,
    title: String,
    badgeText: String,
    badgeTextColor: Color,
    badgeBg: Color,
    badgeBorder: Color,
    description: String,
    bottomContent: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardSurfaceColor,
        border = BorderStroke(1.dp, CardBorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Icon + Title + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon Box
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBoxBg)
                        .border(BorderStroke(1.dp, iconBoxBorder), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 19.sp,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Badge Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeBg)
                        .border(BorderStroke(1.dp, badgeBorder), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeTextColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body Description
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Info Row
            bottomContent()
        }
    }
}

/**
 * Bullet item for Privacy & Security Guarantees with emerald checkmark circle.
 */
@Composable
private fun PrivacyGuaranteeItem(
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(EmeraldBadgeBg)
                .border(BorderStroke(1.dp, EmeraldBadgeBorder), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = EmeraldAccent,
                modifier = Modifier.size(12.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = text,
            color = Color(0xFFE2E8F0),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Vector rendering of the ArcTracker stylized brand mark squircle.
 */
@Composable
fun ArcTrackerBrandLogo(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF141720))
            .border(BorderStroke(1.dp, Color(0xFF262C3D)), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val strokeW = 1.6.dp.toPx()
            // Outer thin subtle circle
            drawCircle(
                color = Color(0xFF94A3B8),
                radius = size.minDimension / 2.2f,
                style = Stroke(width = strokeW * 0.8f)
            )

            // Stylized 'A' lettermark
            val w = size.width
            val h = size.height

            val leftBase = Offset(w * 0.30f, h * 0.72f)
            val apex = Offset(w * 0.50f, h * 0.28f)
            val rightBase = Offset(w * 0.70f, h * 0.72f)
            val crossStart = Offset(w * 0.38f, h * 0.55f)
            val crossEnd = Offset(w * 0.62f, h * 0.55f)

            drawLine(
                color = Color.White,
                start = leftBase,
                end = apex,
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = apex,
                end = rightBase,
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = crossStart,
                end = crossEnd,
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090C10)
@Composable
fun InitialPageScreenPreview() {
    MaterialTheme {
        InitialPageScreen()
    }
}
