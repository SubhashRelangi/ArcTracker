package com.subhashrelangi.arctracker.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.subhashrelangi.arctracker.security.BiometricAuthHelper

@Composable
fun AppLockScreen(
    onUnlockSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // Micro-pulse animation for the biometric badge
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Automatically trigger biometric authentication when the lock screen appears
    LaunchedEffect(Unit) {
        if (activity != null) {
            BiometricAuthHelper.authenticate(
                activity = activity,
                title = "Unlock ArcTracker",
                subtitle = "Confirm your Fingerprint, Face ID, or PIN",
                onSuccess = onUnlockSuccess
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Glowing Biometric / Shield Badge
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFD1FAE5), Color(0xFFECFDF5))
                        )
                    )
                    .border(BorderStroke(2.dp, Color(0xFFA7F3D0)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF047857)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Fingerprint,
                        contentDescription = "Biometric Lock",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Title & Status
            Text(
                text = "ArcTracker Secured",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your offline financial vault is locked after 5 minutes of inactivity.",
                fontSize = 13.5.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Security Badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFECFDF5),
                border = BorderStroke(1.dp, Color(0xFFA7F3D0))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = Color(0xFF15803D),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "100% Air-Gapped Local Guard",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF15803D)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Unlock Button
            Button(
                onClick = {
                    if (activity != null) {
                        BiometricAuthHelper.authenticate(
                            activity = activity,
                            title = "Unlock ArcTracker",
                            subtitle = "Confirm your Fingerprint, Face ID, or PIN",
                            onSuccess = onUnlockSuccess
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF047857),
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Unlock with Biometrics or PIN",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
