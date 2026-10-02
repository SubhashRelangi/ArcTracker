package com.subhashrelangi.arctracker.ui.review

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Skeleton loading placeholders for review cards during initial fetch.
 */
@Composable
fun ReviewLoadingState(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(2) {
            Surface(
                shape = ArcShapes.Card,
                color = ArcColors.SurfaceCard,
                border = BorderStroke(1.dp, ArcColors.SurfaceCardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(21.dp))
                                    .background(Color.White.copy(alpha = alpha))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Box(
                                    modifier = Modifier
                                        .size(width = 120.dp, height = 18.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = alpha))
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(width = 160.dp, height = 12.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = alpha * 0.7f))
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(width = 80.dp, height = 22.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = alpha))
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(width = 100.dp, height = 26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = alpha * 0.6f))
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 90.dp, height = 26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = alpha * 0.6f))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White.copy(alpha = alpha * 0.8f))
                    )
                }
            }
        }
    }
}
