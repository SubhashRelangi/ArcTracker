package com.subhashrelangi.arctracker.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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

/**
 * Empty review state when no transactions require human triage.
 */
@Composable
fun ReviewEmptyState(
    onViewLedger: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Green Checkmark Circular Badge
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F291E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Completed",
                tint = Color(0xFF10B981),
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "All caught up",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "There are no transactions waiting for review.\n\nArcTracker is monitoring your financial notifications and SMS locally.",
            color = Color(0xFF94A3B8),
            fontSize = 13.5.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        OutlinedButton(
            onClick = onViewLedger,
            shape = ArcShapes.Button,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFF181C26),
                contentColor = Color.White
            ),
            border = BorderStroke(1.dp, Color(0xFF262C3D)),
            modifier = Modifier.height(44.dp),
            contentPadding = PaddingValues(horizontal = 24.dp)
        ) {
            Text(
                text = "View Ledger",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color.White
            )
        }
    }
}
