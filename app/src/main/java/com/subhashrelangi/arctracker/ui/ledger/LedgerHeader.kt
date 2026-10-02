package com.subhashrelangi.arctracker.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
 * Top branding header matching Ui-Designs/Ledgerpage.png.
 * Features ArcTracker branding, UPI pill badge, Automated • Local-first status,
 * circular Search action and circular Add transaction action.
 */
@Composable
fun LedgerHeader(
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onAddClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Branding Logo + Title + Subtitle
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppHeaderBrandLogo(
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ArcTracker",
                        color = LedgerColors.TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = LedgerShapes.SmallPill,
                        color = LedgerColors.UpiBadgeBg
                    ) {
                        Text(
                            text = "UPI",
                            color = LedgerColors.UpiBadgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF22C55E), CircleShape)
                    )

                    Spacer(modifier = Modifier.width(5.dp))

                    Text(
                        text = "Automated  •  Local-first",
                        color = LedgerColors.TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.3.sp
                    )
                }
            }
        }

        // Right: Circular Search and Add Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF161B22))
                    .border(1.dp, LedgerColors.BorderLight, CircleShape)
                    .clickable(onClick = onSearchClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = LedgerColors.TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Add Transaction Button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onAddClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Transaction",
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
