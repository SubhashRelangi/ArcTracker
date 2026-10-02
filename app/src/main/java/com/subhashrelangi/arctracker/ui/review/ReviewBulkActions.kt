package com.subhashrelangi.arctracker.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bulk Action Row matching Ui-Designs/ReviewPage.png:
 * [ ✓ Approve Verified (N) ]  [ Dismiss All ]
 */
@Composable
fun ReviewBulkActions(
    verifiedCount: Int,
    onApproveVerified: () -> Unit,
    onDismissAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Approve Verified button (Dominant white pill)
        Button(
            onClick = onApproveVerified,
            enabled = verifiedCount > 0,
            shape = ArcShapes.Button,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
                disabledContainerColor = Color.White.copy(alpha = 0.4f),
                disabledContentColor = Color.Black.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .weight(1.3f)
                .height(44.dp),
            contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Approve Verified ($verifiedCount)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.Black
                )
            }
        }

        // Dismiss All button (Dark pill)
        OutlinedButton(
            onClick = onDismissAll,
            shape = ArcShapes.Button,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFF181C26),
                contentColor = Color(0xFF94A3B8)
            ),
            border = BorderStroke(1.dp, Color(0xFF262C3D)),
            modifier = Modifier
                .weight(0.9f)
                .height(44.dp),
            contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            Text(
                text = "Dismiss All",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color.White
            )
        }
    }
}
