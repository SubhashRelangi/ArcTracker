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

/**
 * Ledger Title & Actions Subheader section:
 * Row 1: [ Ledger • Local Vault               [⚙] [⤓] ]
 * Row 2: [ Encrypted on-device SQLite ledger • October 2026 ]
 * (The top brand "ArcTracker" header is managed universally by the root Scaffold topBar)
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
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 4.dp)
    ) {
        // 1. Ledger Title & Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
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

        // 2. Subtitle: "Encrypted on-device SQLite ledger • October 2026"
        Text(
            text = "Encrypted on-device SQLite ledger • $monthSubtitle",
            color = Color(0xFF64748B),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Normal
        )
    }
}
