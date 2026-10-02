package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.ui.theme.ArcColors

// ==========================================
// Color Tokens matching Ui-Designs/InitialPage.png
// ==========================================
private val InitialBgColor = ArcColors.Background // #090C10 nearly black
private val CardSurface = Color(0xFF141720)
private val CardBorder = Color(0xFF222836)

// 100% LOCAL-FIRST ENGINE badge
private val PillGreenBg = Color(0xFF0D251A)
private val PillGreenBorder = Color(0xFF164E35)
private val PillGreenText = Color(0xFF00E676)

// Card 1: Notification (Blue)
private val NotificationIconTint = Color(0xFF60A5FA)
private val NotificationBoxBg = Color(0xFF14243B)
private val NotificationBoxBorder = Color(0xFF1E3A5F)

// Card 2: Security (Green)
private val SecurityIconTint = Color(0xFF00E676)
private val SecurityBoxBg = Color(0xFF0F2B1D)
private val SecurityBoxBorder = Color(0xFF175337)

// Card 3: SMS (Purple/Indigo)
private val SmsIconTint = Color(0xFFA78BFA)
private val SmsBoxBg = Color(0xFF241C38)
private val SmsBoxBorder = Color(0xFF3B2D5A)

private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF64748B)

/**
 * First-launch welcome / setup screen matching Ui-Designs/InitialPage.png exactly.
 *
 * Structure:
 * 1. Centered ArcTracker logo inside dark squircle with outer white/slate border ring
 * 2. "100% LOCAL-FIRST ENGINE" green pill badge
 * 3. "ArcTracker" bold brand title
 * 4. Compact subtitle: "Zero cloud sync. Real-time Indian banking SMS & UPI notification ledger stored locally in encrypted SQLite."
 * 5. Three capability cards:
 *    - Notification Listener (blue icon)
 *    - AES-256-GCM Vault (green icon)
 *    - Telephony SMS Ingest (purple icon)
 * 6. Primary CTA: "Start Setup" (clean white pill button)
 * 7. Secondary action: "Skip straight to Dashboard (Demo Data)"
 */
@Composable
fun InitialPageScreen(
    onStartSetupClick: () -> Unit,
    onSkipToDashboardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(InitialBgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ----------------------------------------------------
            // 1. ArcTracker Logo at Top Center
            // ----------------------------------------------------
            InitialBrandLogo(
                modifier = Modifier.size(68.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ----------------------------------------------------
            // 2. 100% LOCAL-FIRST ENGINE Badge Pill
            // ----------------------------------------------------
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(PillGreenBg)
                    .border(BorderStroke(1.dp, PillGreenBorder), RoundedCornerShape(50))
                    .padding(horizontal = 13.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "100% LOCAL-FIRST ENGINE",
                    color = PillGreenText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ----------------------------------------------------
            // 3. Brand Title
            // ----------------------------------------------------
            Text(
                text = "ArcTracker",
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // ----------------------------------------------------
            // 4. Subtitle Description
            // ----------------------------------------------------
            Text(
                text = "Zero cloud sync. Real-time Indian banking SMS & UPI notification ledger stored locally in encrypted SQLite.",
                color = TextSecondary,
                fontSize = 12.5.sp,
                lineHeight = 17.5.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // ----------------------------------------------------
            // 5. Capability Cards (Exactly 3)
            // ----------------------------------------------------
            // Card 1: Notification Listener
            CapabilityCard(
                icon = Icons.Outlined.NotificationsNone,
                iconTint = NotificationIconTint,
                iconBoxBg = NotificationBoxBg,
                iconBoxBorder = NotificationBoxBorder,
                title = "Notification Listener",
                description = "Captures GPay, PhonePe, Paytm & Bank alerts sub-millisecond."
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Card 2: AES-256-GCM Vault
            CapabilityCard(
                icon = Icons.Outlined.Lock,
                iconTint = SecurityIconTint,
                iconBoxBg = SecurityBoxBg,
                iconBoxBorder = SecurityBoxBorder,
                title = "AES-256-GCM Vault",
                description = "Air-gapped on-device Room database. No server sockets."
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Card 3: Telephony SMS Ingest
            CapabilityCard(
                icon = Icons.Outlined.ChatBubbleOutline,
                iconTint = SmsIconTint,
                iconBoxBg = SmsBoxBg,
                iconBoxBorder = SmsBoxBorder,
                title = "Telephony SMS Ingest",
                description = "Batch scans historical bank statements (HDFC, SBI, Axis, ICICI)."
            )

            // Flexible dynamic spacer absorbing remaining viewport height
            Spacer(modifier = Modifier.weight(1f))

            // ----------------------------------------------------
            // 6. Primary Action: Start Setup
            // ----------------------------------------------------
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = Color(0x66000000),
                        ambientColor = Color(0x33000000)
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(color = Color.Black.copy(alpha = 0.2f)),
                        onClick = onStartSetupClick
                    ),
                shape = RoundedCornerShape(16.dp),
                color = Color.White
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Start Setup",
                        color = Color(0xFF0A0D14),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ----------------------------------------------------
            // 7. Secondary Action: Skip straight to Dashboard (Demo Data)
            // ----------------------------------------------------
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(color = Color.White.copy(alpha = 0.1f)),
                        onClick = onSkipToDashboardClick
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Skip straight to Dashboard (Demo Data)",
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * Backward compatibility overload for existing call sites.
 */
@Composable
fun InitialPageScreen(
    onGetStartedClick: () -> Unit = {},
    onExploreDemoClick: () -> Unit = {}
) {
    InitialPageScreen(
        onStartSetupClick = onGetStartedClick,
        onSkipToDashboardClick = onExploreDemoClick
    )
}

/**
 * Horizontal capability card matching the reference image.
 */
@Composable
private fun CapabilityCard(
    icon: ImageVector,
    iconTint: Color,
    iconBoxBg: Color,
    iconBoxBorder: Color,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Icon Container (Rounded square)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBoxBg)
                    .border(BorderStroke(1.dp, iconBoxBorder), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right: Title & Description
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.1.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.5.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Top brand squircle logo for InitialPage matching reference image:
 * Dark rounded squircle with white circular border container enclosing the project's real ArcTracker logo asset.
 */
@Composable
private fun InitialBrandLogo(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF141720))
            .border(BorderStroke(1.dp, Color(0xFF262C3D)), RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Inner white squircle matching reference mockup
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.subhashrelangi.arctracker.R.drawable.ic_launcher_foreground),
                contentDescription = "ArcTracker App Icon",
                modifier = Modifier.size(46.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090C10)
@Composable
fun InitialPageScreenPreview() {
    MaterialTheme {
        InitialPageScreen(
            onStartSetupClick = {},
            onSkipToDashboardClick = {}
        )
    }
}
