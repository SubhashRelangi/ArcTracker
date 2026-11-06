package com.subhashrelangi.arctracker.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.subhashrelangi.arctracker.service.SmsPermissionHelper
import com.subhashrelangi.arctracker.ui.theme.ArcColors

/**
 * Dialog shown during onboarding or setup prompting the user to perform an initial
 * historical SMS scan and import previous financial transactions.
 * Styled in ArcTracker Emerald Light theme with circular buttons.
 */
@Composable
fun InitialSmsImportDialog(
    onNavigateToImport: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            SmsPermissionHelper.setInitialImportCompleted(context, true)
            onNavigateToImport()
        } else {
            Toast.makeText(
                context,
                "SMS permission is required to import previous transactions",
                Toast.LENGTH_LONG
            ).show()
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ArcColors.CardBackground,
            border = BorderStroke(1.dp, ArcColors.Border),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(if (ArcColors.isDark) Color(0xFF064E3B) else ArcColors.PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Sms,
                        contentDescription = "SMS Import",
                        tint = ArcColors.Primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Import previous transactions?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ArcColors.TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "ArcTracker can scan your messages for previous financial transactions and let you choose which accounts to import.",
                    fontSize = 13.sp,
                    color = ArcColors.TextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (SmsPermissionHelper.isSmsPermissionGranted(context)) {
                            SmsPermissionHelper.setInitialImportCompleted(context, true)
                            onNavigateToImport()
                        } else {
                            permissionLauncher.launch(Manifest.permission.READ_SMS)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArcColors.Primary,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Import",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, ArcColors.Border)
                ) {
                    Text(
                        text = "Not now",
                        color = ArcColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }
    }
}
