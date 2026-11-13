package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.ui.core.ArcModalBottomSheet
import com.subhashrelangi.arctracker.ui.theme.ArcColors

/**
 * Bottom Sheet prompting the user to grant Android Notification Access permission for ArcTracker.
 * Slides up from bottom to center with standard ArcTracker styling.
 */
@Composable
fun NotificationPermissionDialog(
    onGrantClick: () -> Unit,
    onDismiss: () -> Unit
) {
    ArcModalBottomSheet(
        onDismissRequest = onDismiss,
        title = "Notification Access Needed",
        icon = Icons.Filled.NotificationsActive,
        iconColor = ArcColors.Primary,
        maxHeight = 360.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ArcTracker requires Notification Access to automatically read and record transaction notifications from your UPI and banking apps.\n\nAll notification data stays 100% private and offline on your device.",
                fontSize = 13.sp,
                color = ArcColors.TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onGrantClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ArcColors.Primary,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Grant Permission",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = CircleShape,
                border = BorderStroke(1.dp, ArcColors.Border)
            ) {
                Text(
                    text = "Not Now",
                    color = ArcColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                )
            }
        }
    }
}
