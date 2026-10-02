package com.subhashrelangi.arctracker.ui.core

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.subhashrelangi.arctracker.ui.AppHeaderBrandLogo
import com.subhashrelangi.arctracker.ui.theme.ArcDimensions
import com.subhashrelangi.arctracker.ui.theme.ArcTypography

/**
 * Universal ArcTracker Brand identity row matching LedgerPage.
 * Displays:
 * [Logo with green indicator dot] ArcTracker [UPI badge]
 *                                 ● Automated • Local-first
 */
@Composable
fun ArcTrackerBrand(
    modifier: Modifier = Modifier,
    title: String = "ArcTracker",
    showBadge: Boolean = true,
    badgeText: String = "UPI",
    statusText: String = "Automated  •  Local-first"
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppHeaderBrandLogo(
            modifier = Modifier.size(ArcDimensions.BrandLogoSize)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = ArcTypography.BrandTitle
                )

                if (showBadge) {
                    Spacer(modifier = Modifier.width(8.dp))
                    ArcUpiBadge(text = badgeText)
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArcStatusDot(size = ArcDimensions.LiveDotSize)

                Spacer(modifier = Modifier.width(5.dp))

                Text(
                    text = statusText,
                    style = ArcTypography.BrandSubtitle
                )
            }
        }
    }
}
