package com.subhashrelangi.arctracker.ui.core

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.ui.review.ArcColors

/**
 * Standardized Floating "+ Add Expense" Pill Button for ArcTracker.
 * Guarantees exact fixed size, shape, typography, padding, and screen position
 * across all main pages (Home, Ledger, Review, Insights, Settings) to prevent
 * any shifting, jumping, or sizing discrepancies during tab navigation.
 */
@Composable
fun ArcFloatingAddExpenseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .navigationBarsPadding()
            .padding(end = 16.dp, bottom = 72.dp)
            .shadow(elevation = 6.dp, shape = CircleShape),
        shape = CircleShape,
        color = if (ArcColors.isDark) ArcColors.SurfaceElevated else Color(0xFF0F172A),
        border = BorderStroke(
            1.dp,
            if (ArcColors.isDark) ArcColors.Primary.copy(alpha = 0.35f) else Color(0xFF334155)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Expense",
                tint = ArcColors.Primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Add Expense",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
