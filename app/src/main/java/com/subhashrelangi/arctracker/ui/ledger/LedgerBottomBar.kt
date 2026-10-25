package com.subhashrelangi.arctracker.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
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
 * Bottom Privacy & Local-first SQLite store footer matching reference mockup:
 * [ (🔒) ]
 * All 32 records synced to private on-device store
 * No cloud telemetry • Zero analytics trackers
 */
@Composable
fun LedgerDatabaseFooter(
    recordCount: Int = 32,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFFF1F5F9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "Encrypted On-device",
                tint = Color(0xFF0F172A),
                modifier = Modifier.size(15.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "All $recordCount records synced to private on-device store",
            color = Color(0xFF0F172A),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "No cloud telemetry • Zero analytics trackers",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

/**
 * Backward-compatible helper for action bar if referenced.
 */
@Composable
fun LedgerSummaryActionBar(
    totalDebits: Double,
    totalCredits: Double,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Replaced by top Monthly Overview Card and header export icon in new redesign.
}
