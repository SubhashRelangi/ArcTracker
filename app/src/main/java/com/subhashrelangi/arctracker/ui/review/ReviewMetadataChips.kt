package com.subhashrelangi.arctracker.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Metadata chips for financial transactions (Source App, Account, Mode).
 */
@Composable
fun ReviewMetadataChips(
    sourceText: String,
    accountLabel: String?,
    modeText: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Source App Chip
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = ArcColors.ChipBackground,
            border = BorderStroke(1.dp, ArcColors.ChipBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = if (sourceText.contains("SMS", ignoreCase = true)) {
                    Icons.Default.Sms
                } else {
                    Icons.Default.NotificationsNone
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ArcColors.TextSecondary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = sourceText,
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 2. Account Chip
        if (!accountLabel.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ArcColors.ChipBackground,
                border = BorderStroke(1.dp, ArcColors.ChipBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = ArcColors.TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = accountLabel,
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 3. Mode Chip (e.g. Mode: P2M)
        if (!modeText.isNullOrBlank()) {
            Text(
                text = "Mode: $modeText",
                color = ArcColors.TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
