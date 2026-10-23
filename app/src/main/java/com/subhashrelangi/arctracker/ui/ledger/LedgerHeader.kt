package com.subhashrelangi.arctracker.ui.ledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.ui.core.ArcTrackerHeader

/**
 * Top brand header & Ledger title section:
 * Row 1: Brand Header matching Home page: [ ArcTracker               (👤) ]
 *        (Search icon removed on Ledger per user request)
 * Row 2: [ Ledger • Local Vault               [⚙] [⤓] ]
 * Row 3: [ Encrypted on-device SQLite ledger • October 2026 ]
 */
@Composable
fun LedgerHeader(
    monthSubtitle: String = "October 2026",
    onProfileClick: () -> Unit = {},
    onFilterClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp)
    ) {
        // 1. Top Brand Header: Exact Home Page Header (ArcTracker + Profile, NO search icon)
        ArcTrackerHeader(
            title = "ArcTracker",
            showSearch = false, // User requested: "dont need search icon so remove that in the ledger page"
            showProfile = true,
            onProfileClick = onProfileClick,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Ledger Title & Actions Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: "Ledger" + "• Local Vault"
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ledger",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFECFDF5))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Local Vault",
                        color = Color(0xFF059669),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Right: Filter (Tune) and Export (FileUpload) Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Filter / Tune button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), CircleShape)
                        .clickable(onClick = onFilterClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = "Filter / Month",
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Export button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(BorderStroke(1.dp, Color(0xFFE2E8F0)), CircleShape)
                        .clickable(onClick = onExportClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileUpload,
                        contentDescription = "Export CSV",
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 3. Subtitle: "Encrypted on-device SQLite ledger • October 2026"
        Text(
            text = "Encrypted on-device SQLite ledger • $monthSubtitle",
            color = Color(0xFF64748B),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
