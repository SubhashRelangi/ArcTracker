package com.subhashrelangi.arctracker.ui.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.subhashrelangi.arctracker.ui.theme.ArcColors
import com.subhashrelangi.arctracker.ui.theme.ArcTypography

/**
 * Universal security footer indicator matching LedgerPage.
 * Displays shield icon and "Encrypted Local-First SQLite Database".
 */
@Composable
fun ArcTrackerSecurityFooter(
    modifier: Modifier = Modifier,
    text: String = "Encrypted Local-First SQLite Database"
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Shield,
            contentDescription = null,
            tint = ArcColors.TextMuted,
            modifier = Modifier.size(13.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = text,
            style = ArcTypography.SecurityFooter
        )
    }
}
