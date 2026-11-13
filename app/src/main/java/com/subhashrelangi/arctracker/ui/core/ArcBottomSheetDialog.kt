package com.subhashrelangi.arctracker.ui.core

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.ui.theme.ArcColors

/**
 * Standard ArcTracker Bottom-to-Center Modal Bottom Sheet.
 * Anchors firmly to the bottom of the screen and animates smoothly upward,
 * replacing center pop-up dialogs with a modern, ergonomic bottom-sheet experience.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = ArcColors.Primary,
    showCloseButton: Boolean = true,
    minHeight: Dp = 200.dp,
    maxHeight: Dp = 560.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = ArcColors.SurfaceElevated,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = ArcColors.Border,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = minHeight, max = maxHeight)
                .padding(contentPadding)
                .padding(bottom = 16.dp)
        ) {
            if (title != null || icon != null || showCloseButton) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (icon != null) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(iconColor.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = iconColor,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Column {
                            if (title != null) {
                                Text(
                                    text = title,
                                    color = ArcColors.TextPrimary,
                                    fontSize = 17.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    color = ArcColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    if (showCloseButton) {
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = ArcColors.TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            content()
        }
    }
}

/**
 * Standard ArcTracker Bottom-to-Center Confirmation Sheet.
 * Replaces center confirmation AlertDialogs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcConfirmationBottomSheet(
    title: String,
    message: String = "",
    description: String = message,
    confirmText: String = "Confirm",
    cancelText: String = "Cancel",
    icon: ImageVector = Icons.Outlined.DeleteOutline,
    iconColor: Color = Color(0xFFDC2626),
    confirmColor: Color? = null,
    isDestructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit = {},
    onDismissRequest: () -> Unit = onDismiss
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val effectiveDismiss = if (onDismissRequest != onDismiss && onDismiss == {}) onDismissRequest else onDismiss
    val effectiveMessage = if (message.isNotBlank()) message else description

    ModalBottomSheet(
        onDismissRequest = effectiveDismiss,
        sheetState = sheetState,
        containerColor = ArcColors.SurfaceElevated,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = ArcColors.Border,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header with Icon & Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = title,
                    color = ArcColors.TextPrimary,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = effectiveMessage,
                color = ArcColors.TextSecondary,
                fontSize = 13.5.sp,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Action Buttons (Cancel / Confirm)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = effectiveDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ArcColors.Border)
                ) {
                    Text(
                        text = cancelText,
                        color = ArcColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        onConfirm()
                        effectiveDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = confirmColor ?: (if (isDestructive) Color(0xFFDC2626) else ArcColors.Primary)
                    )
                ) {
                    Text(
                        text = confirmText,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
