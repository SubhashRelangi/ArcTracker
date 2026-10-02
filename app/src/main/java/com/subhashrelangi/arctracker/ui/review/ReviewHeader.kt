package com.subhashrelangi.arctracker.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.ui.AppHeaderBrandLogo

/**
 * Top branding header, sandbox telemetry bar, and Review Queue title banner
 * matching Ui-Designs/ReviewPage.png.
 */
@Composable
fun ReviewHeader(
    pendingCount: Int,
    onSearchClick: () -> Unit = {},
    onAddClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // 1. Top Branding Row (ArcTracker, UPI badge, status, Search, Add)
        com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader(
            modifier = Modifier.fillMaxWidth(),
            onSearchClick = onSearchClick,
            onAddClick = onAddClick
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Micro-telemetry Banner (AUTOMATED INGEST SANDBOX | Local AES-256)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(ArcColors.Warning, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "AUTOMATED INGEST SANDBOX",
                    color = ArcColors.TextSecondary,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = ArcColors.SuccessContainer,
                border = BorderStroke(1.dp, ArcColors.SuccessBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = ArcColors.Success,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Local AES-256",
                        color = ArcColors.Success,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Section Title & Pending Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Review Queue",
                color = ArcColors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )

            if (pendingCount > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ArcColors.WarningContainer,
                    border = BorderStroke(1.dp, ArcColors.WarningBorder)
                ) {
                    Text(
                        text = "$pendingCount Pending",
                        color = ArcColors.Warning,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. Compact Description
        Text(
            text = "Auto-captured from UPI notifications & device SMS.\nVerify or resolve hints to sync with your private ledger.",
            color = ArcColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}
