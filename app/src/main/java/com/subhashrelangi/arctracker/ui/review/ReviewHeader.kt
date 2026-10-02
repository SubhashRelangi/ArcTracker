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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppHeaderBrandLogo(
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ArcTracker",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E2330)
                        ) {
                            Text(
                                text = "UPI",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Automated • Local-first",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Right Action Buttons (Search & Add)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search circular button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF141721))
                        .clickable { onSearchClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Add '+' white circular button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { onAddClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Transaction",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

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
