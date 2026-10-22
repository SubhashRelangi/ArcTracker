package com.subhashrelangi.arctracker.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.subhashrelangi.arctracker.BuildConfig
import com.subhashrelangi.arctracker.R
import kotlinx.coroutines.launch

// ==========================================
// Light Theme Color Tokens
// ==========================================
private val InitialLightBackground = Color(0xFFFFFFFF)
private val BrandTextPrimary = Color(0xFF0F172A)
private val BrandTextSecondary = Color(0xFF64748B)
private val VersionBadgeBg = Color(0xFFF1F5F9)
private val VersionBadgeText = Color(0xFF64748B)
private val StatusIndicatorGreen = Color(0xFF10B981)
private val PagerIndicatorActive = Color(0xFF0F172A)
private val PagerIndicatorInactive = Color(0xFFE2E8F0)
private val PrimaryBtnBg = Color(0xFF0F172A)
private val PrimaryBtnText = Color(0xFFFFFFFF)
private val SecondaryBtnBg = Color(0xFFF8FAFC)
private val SecondaryBtnBorder = Color(0xFFE2E8F0)
private val SecondaryBtnText = Color(0xFF0F172A)
private val LockIconGreen = Color(0xFF10B981)

/**
 * Initial Page matching requested specifications:
 * 1. Top Bar: Title starts first ("ArcTracker" bold text + GitHub release version pill, green status dot on right)
 * 2. Borderless Logo: Dark theme logo for light theme, light theme logo for dark theme, completely borderless
 * 3. Hero group positioned right above the CTA buttons (no empty space between them)
 * 4. Static-sized swipeable pager: Prevents text shrinking/jitter on the 3rd swipe with consistent line heights
 * 5. 3-step page indicators synced to the swipe pager
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InitialPageScreen(
    onStartSetupClick: () -> Unit = {},
    onSkipToDashboardClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Adapt system status & navigation bars for light theme surface
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

    val isDark = isSystemInDarkTheme()
    // Dark theme logo used for light theme, light theme logo used for dark theme
    val logoResource = if (isDark) R.drawable.ic_logo_light else R.drawable.ic_logo_dark

    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(InitialLightBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // ----------------------------------------------------
            // 1. Header (Title starts first on left, GitHub version, green dot on right)
            // ----------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Title starts first, followed by GitHub version pill
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ArcTracker",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandTextPrimary,
                        letterSpacing = (-0.3).sp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Version Badge Pill (GitHub downloaded version)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(VersionBadgeBg)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = BuildConfig.GITHUB_VERSION.ifEmpty { "v${BuildConfig.VERSION_NAME}" },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = VersionBadgeText
                        )
                    }
                }

                // Right: Status Indicator Green Dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(StatusIndicatorGreen)
                )
            }

            // Pushes hero content & buttons down to the bottom area, removing gap before buttons
            Spacer(modifier = Modifier.weight(1f))

            // ----------------------------------------------------
            // 2. Hero Section (Left-aligned, positioned directly above buttons)
            // ----------------------------------------------------
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                // Borderless Brand Logo (Dark-theme logo in light theme, completely borderless)
                Image(
                    painter = painterResource(id = logoResource),
                    contentDescription = "ArcTracker Brand Logo",
                    modifier = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Static-sized Horizontal Pager: Fixed height and line limits to prevent any shrinking/jitter
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) { page ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 6.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        when (page) {
                            0 -> {
                                Text(
                                    text = "Your finances. Clear\nand under control.",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 32.sp,
                                    letterSpacing = (-0.4).sp,
                                    color = BrandTextPrimary,
                                    minLines = 2,
                                    maxLines = 2
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Track transactions, understand spending, and\nstay in control with private, on-device\nautomation.",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Normal,
                                    lineHeight = 19.sp,
                                    color = BrandTextSecondary,
                                    minLines = 3,
                                    maxLines = 3
                                )
                            }
                            1 -> {
                                Text(
                                    text = "100% Local-First.\nZero cloud sync.",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 32.sp,
                                    letterSpacing = (-0.4).sp,
                                    color = BrandTextPrimary,
                                    minLines = 2,
                                    maxLines = 2
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Real-time Indian banking SMS & notification\nledger stored strictly on your device in\nencrypted SQLite.",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Normal,
                                    lineHeight = 19.sp,
                                    color = BrandTextSecondary,
                                    minLines = 3,
                                    maxLines = 3
                                )
                            }
                            2 -> {
                                Text(
                                    text = "Intelligent Ingest.\nBuilt for Indian UPI.",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 32.sp,
                                    letterSpacing = (-0.4).sp,
                                    color = BrandTextPrimary,
                                    minLines = 2,
                                    maxLines = 2
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "• Instant UPI & bank SMS alert ingestion\n• Automated smart categorization rules\n• Multi-bank balance reconciliation & budgets",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Normal,
                                    lineHeight = 19.sp,
                                    color = BrandTextSecondary,
                                    minLines = 3,
                                    maxLines = 3
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3-Step Page Indicators synced to PagerState
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width = if (isSelected) 22.dp else 7.dp
                        val color = if (isSelected) PagerIndicatorActive else PagerIndicatorInactive

                        Box(
                            modifier = Modifier
                                .width(width)
                                .height(3.5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                        )
                    }
                }
            }

            // Tight spacing directly connecting hero group to bottom buttons
            Spacer(modifier = Modifier.height(24.dp))

            // ----------------------------------------------------
            // 3. Bottom CTA Actions
            // ----------------------------------------------------
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Primary Action Button: "Get Started ->"
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .shadow(
                            elevation = 2.dp,
                            shape = RoundedCornerShape(14.dp),
                            ambientColor = Color(0x10000000),
                            spotColor = Color(0x1A000000)
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(color = Color.White.copy(alpha = 0.2f)),
                            onClick = onStartSetupClick
                        ),
                    shape = RoundedCornerShape(14.dp),
                    color = PrimaryBtnBg
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Get Started",
                            color = PrimaryBtnText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = PrimaryBtnText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary Action Button: "Explore Demo"
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(color = Color.Black.copy(alpha = 0.06f)),
                            onClick = onSkipToDashboardClick
                        ),
                    shape = RoundedCornerShape(14.dp),
                    color = SecondaryBtnBg,
                    border = BorderStroke(1.dp, SecondaryBtnBorder)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Visibility,
                            contentDescription = null,
                            tint = SecondaryBtnText,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Explore Demo",
                            color = SecondaryBtnText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Local-First Security Note
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = LockIconGreen,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Your financial data stays on your device.",
                        color = BrandTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun InitialPageScreenPreview() {
    MaterialTheme {
        InitialPageScreen(
            onStartSetupClick = {},
            onSkipToDashboardClick = {}
        )
    }
}
