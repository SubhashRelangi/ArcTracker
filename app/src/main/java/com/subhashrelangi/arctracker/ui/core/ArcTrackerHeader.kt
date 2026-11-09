package com.subhashrelangi.arctracker.ui.core

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.ui.theme.ArcColors

/**
 * Universal ArcTracker Header.
 * Dynamically styled with ArcColors tokens to adapt seamlessly across Light and Dark themes.
 *
 * Primary (Tab) screens:
 *   Left: Clean "ArcTracker" brand text (22.sp, bold, ArcColors.TextPrimary, -0.5.sp letter spacing)
 *   Right: Circular action buttons (38.dp, ArcColors.Surface, 1.dp border ArcColors.Border, ArcColors.TextPrimary)
 *          e.g. Search (optional), Add (optional), Profile (optional), or custom actions.
 *   Height: Fixed 52.dp for exact baseline consistency across all tabs.
 *
 * Child / Secondary screens:
 *   Left: Circular back button (38.dp) + Title (20.sp, bold, ArcColors.TextPrimary) + optional Subtitle (12.sp, ArcColors.TextMuted)
 *   Right: Custom actions or action buttons.
 */
@Composable
fun ArcTrackerHeader(
    modifier: Modifier = Modifier,
    title: String = "ArcTracker",
    subtitle: String? = null,
    showSearch: Boolean = false,
    isSearchActive: Boolean = false,
    showAdd: Boolean = false,
    showProfile: Boolean = false,
    profileInitials: String = "",
    onSearchClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 0.dp,
    // Backward compatibility for existing callers
    showBadge: Boolean = false,
    badgeText: String = "UPI",
    statusText: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column / Row
        if (onBackClick != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // Circular Back Button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ArcColors.Surface)
                        .border(BorderStroke(1.dp, ArcColors.Border), CircleShape)
                        .clickable(onClick = onBackClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = ArcColors.TextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ArcColors.TextPrimary,
                        letterSpacing = (-0.4).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = ArcColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            // Main Top Brand Title: Clean "ArcTracker"
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = ArcColors.TextPrimary,
                letterSpacing = (-0.5).sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right: Configurable Action Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (actions != null) {
                actions()
            } else {
                if (showSearch) {
                    val searchBg = if (isSearchActive) ArcColors.TextPrimary else ArcColors.Surface
                    val searchTint = if (isSearchActive) ArcColors.Background else ArcColors.TextPrimary
                    val searchBorder = if (isSearchActive) ArcColors.TextPrimary else ArcColors.Border
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(searchBg)
                            .border(BorderStroke(1.dp, searchBorder), CircleShape)
                            .clickable(onClick = onSearchClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = if (isSearchActive) "Close Search" else "Search",
                            tint = searchTint,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                if (showAdd) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ArcColors.Surface)
                            .border(BorderStroke(1.dp, ArcColors.Border), CircleShape)
                            .clickable(onClick = onAddClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            tint = ArcColors.TextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                if (showProfile) {
                    val hasInitials = profileInitials.isNotBlank()
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (hasInitials) ArcColors.PrimaryLight else ArcColors.Surface)
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (hasInitials) ArcColors.PrimaryBorder else ArcColors.Border
                                ),
                                CircleShape
                            )
                            .clickable(onClick = onProfileClick),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasInitials) {
                            Text(
                                text = profileInitials.take(2).uppercase(),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ArcColors.Primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = "Profile & Accounts",
                                tint = ArcColors.TextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
