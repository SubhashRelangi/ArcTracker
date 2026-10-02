package com.subhashrelangi.arctracker.ui.ledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class LedgerBadgeType {
    REVIEW,
    UNRESOLVED,
    SALARY,
    SMS
}

/**
 * Compact status badge shown beside the merchant name in transaction cards.
 * E.g., [ ⚑ Review ], [ ⚠ Unresolved ], [ ✓ Salary ], [ ✨ SMS ]
 */
@Composable
fun LedgerStatusChip(
    type: LedgerBadgeType,
    modifier: Modifier = Modifier
) {
    val (bgColor, borderColor, contentColor, icon, label) = when (type) {
        LedgerBadgeType.REVIEW -> StatusChipConfig(
            bgColor = LedgerColors.WarningAmberBg,
            borderColor = LedgerColors.WarningAmberBorder,
            contentColor = LedgerColors.WarningAmber,
            icon = Icons.Default.Flag,
            label = "Review"
        )
        LedgerBadgeType.UNRESOLVED -> StatusChipConfig(
            bgColor = LedgerColors.UnresolvedBg,
            borderColor = LedgerColors.UnresolvedBorder,
            contentColor = LedgerColors.Unresolved,
            icon = Icons.Default.Warning,
            label = "Unresolved"
        )
        LedgerBadgeType.SALARY -> StatusChipConfig(
            bgColor = LedgerColors.CreditBg,
            borderColor = LedgerColors.CreditBorder,
            contentColor = LedgerColors.Credit,
            icon = Icons.Default.Check,
            label = "Salary"
        )
        LedgerBadgeType.SMS -> StatusChipConfig(
            bgColor = Color(0xFF1A1F2C),
            borderColor = Color(0xFF2B3344),
            contentColor = LedgerColors.TextSecondary,
            icon = Icons.Rounded.AutoAwesome,
            label = "SMS"
        )
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(10.dp)
            )

            Spacer(modifier = Modifier.width(3.dp))

            Text(
                text = label,
                color = contentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private data class StatusChipConfig(
    val bgColor: Color,
    val borderColor: Color,
    val contentColor: Color,
    val icon: ImageVector,
    val label: String
)
