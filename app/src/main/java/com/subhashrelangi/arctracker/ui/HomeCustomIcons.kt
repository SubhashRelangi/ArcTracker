package com.subhashrelangi.arctracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Custom vector icon implementations for pixel-perfect match with Ui-Designs/HomePage.png.
 */

// ----------------------------------------------------
// 1. App Header Logo with Green Automation Dot
// ----------------------------------------------------
@Composable
fun AppHeaderBrandLogo(
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Outer Circular Container with white stroke
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black, CircleShape)
                .border(1.8.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize(0.65f)) {
                val strokeW = 1.8.dp.toPx()
                val w = size.width
                val h = size.height

                // Stylized ArcTracker 'A' lettermark
                val leftBase = Offset(w * 0.22f, h * 0.80f)
                val apex = Offset(w * 0.50f, h * 0.20f)
                val rightBase = Offset(w * 0.78f, h * 0.80f)
                val crossStart = Offset(w * 0.32f, h * 0.60f)
                val crossEnd = Offset(w * 0.68f, h * 0.60f)

                drawLine(
                    color = Color.White,
                    start = leftBase,
                    end = apex,
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = apex,
                    end = rightBase,
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = crossStart,
                    end = crossEnd,
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }
        }

        // Green Indicator Dot on bottom-right
        Box(
            modifier = Modifier
                .size(10.dp)
                .align(Alignment.BottomEnd)
                .background(Color(0xFF00E676), CircleShape)
                .border(2.dp, Color(0xFF090C10), CircleShape)
        )
    }
}

// ----------------------------------------------------
// 2. Total Liquid Position Card Wallet Icon
// ----------------------------------------------------
@Composable
fun WalletPositionIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF94A3B8)
) {
    Canvas(modifier = modifier) {
        val strokeW = 1.4.dp.toPx()
        val w = size.width
        val h = size.height

        // Main card outline
        val cardRect = RoundRect(
            left = w * 0.10f,
            top = h * 0.20f,
            right = w * 0.88f,
            bottom = h * 0.80f,
            cornerRadius = CornerRadius(2.dp.toPx())
        )
        val cardPath = Path().apply { addRoundRect(cardRect) }
        drawPath(cardPath, color = tint, style = Stroke(width = strokeW))

        // Flap / tab on the right side
        val flapPath = Path().apply {
            moveTo(w * 0.58f, h * 0.38f)
            lineTo(w * 0.88f, h * 0.38f)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    left = w * 0.74f,
                    top = h * 0.38f,
                    right = w * 0.96f,
                    bottom = h * 0.62f
                ),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            lineTo(w * 0.58f, h * 0.62f)
            close()
        }
        drawPath(flapPath, color = tint, style = Stroke(width = strokeW))

        // Small circle inside tab
        drawCircle(
            color = tint,
            radius = 1.2.dp.toPx(),
            center = Offset(w * 0.78f, h * 0.50f)
        )
    }
}

// ----------------------------------------------------
// 3. Quick Action: Scan SMS Icon (Chat bubble with 3 dots)
// ----------------------------------------------------
@Composable
fun ChatBubbleDotsIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFCBD5E1)
) {
    Canvas(modifier = modifier) {
        val strokeW = 1.6.dp.toPx()
        val w = size.width
        val h = size.height

        val bubblePath = Path().apply {
            moveTo(w * 0.20f, h * 0.20f)
            lineTo(w * 0.80f, h * 0.20f)
            quadraticBezierTo(w * 0.88f, h * 0.20f, w * 0.88f, h * 0.28f)
            lineTo(w * 0.88f, h * 0.62f)
            quadraticBezierTo(w * 0.88f, h * 0.70f, w * 0.80f, h * 0.70f)
            lineTo(w * 0.38f, h * 0.70f)
            lineTo(w * 0.22f, h * 0.84f)
            lineTo(w * 0.22f, h * 0.70f)
            lineTo(w * 0.20f, h * 0.70f)
            quadraticBezierTo(w * 0.12f, h * 0.70f, w * 0.12f, h * 0.62f)
            lineTo(w * 0.12f, h * 0.28f)
            quadraticBezierTo(w * 0.12f, h * 0.20f, w * 0.20f, h * 0.20f)
            close()
        }
        drawPath(
            path = bubblePath,
            color = tint,
            style = Stroke(width = strokeW, join = StrokeJoin.Round, cap = StrokeCap.Round)
        )

        // Three dots inside
        val dotRadius = 1.2.dp.toPx()
        val dotY = h * 0.45f
        drawCircle(color = tint, radius = dotRadius, center = Offset(w * 0.35f, dotY))
        drawCircle(color = tint, radius = dotRadius, center = Offset(w * 0.50f, dotY))
        drawCircle(color = tint, radius = dotRadius, center = Offset(w * 0.65f, dotY))
    }
}

// ----------------------------------------------------
// 4. Quick Action: Review Icon (Document with Checkmark)
// ----------------------------------------------------
@Composable
fun ReviewDocumentIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFFBBF24)
) {
    Canvas(modifier = modifier) {
        val strokeW = 1.6.dp.toPx()
        val w = size.width
        val h = size.height

        // Outer sheet
        val sheetRect = RoundRect(
            left = w * 0.20f,
            top = h * 0.15f,
            right = w * 0.80f,
            bottom = h * 0.85f,
            cornerRadius = CornerRadius(2.dp.toPx())
        )
        val sheetPath = Path().apply { addRoundRect(sheetRect) }
        drawPath(sheetPath, color = tint, style = Stroke(width = strokeW))

        // Horizontal lines on left
        drawLine(
            color = tint,
            start = Offset(w * 0.32f, h * 0.34f),
            end = Offset(w * 0.50f, h * 0.34f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.32f, h * 0.50f),
            end = Offset(w * 0.46f, h * 0.50f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.32f, h * 0.66f),
            end = Offset(w * 0.50f, h * 0.66f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )

        // Checkmark on right
        val checkPath = Path().apply {
            moveTo(w * 0.56f, h * 0.50f)
            lineTo(w * 0.64f, h * 0.60f)
            lineTo(w * 0.74f, h * 0.40f)
        }
        drawPath(
            path = checkPath,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

// ----------------------------------------------------
// 5. Quick Action: Add Cash Icon (Square with angled Pen)
// ----------------------------------------------------
@Composable
fun AddCashEditIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFCBD5E1)
) {
    Canvas(modifier = modifier) {
        val strokeW = 1.6.dp.toPx()
        val w = size.width
        val h = size.height

        // Square frame with break for pen at top-right
        val framePath = Path().apply {
            moveTo(w * 0.55f, h * 0.20f)
            lineTo(w * 0.26f, h * 0.20f)
            quadraticBezierTo(w * 0.18f, h * 0.20f, w * 0.18f, h * 0.28f)
            lineTo(w * 0.18f, h * 0.76f)
            quadraticBezierTo(w * 0.18f, h * 0.84f, w * 0.26f, h * 0.84f)
            lineTo(w * 0.74f, h * 0.84f)
            quadraticBezierTo(w * 0.82f, h * 0.84f, w * 0.82f, h * 0.76f)
            lineTo(w * 0.82f, h * 0.48f)
        }
        drawPath(
            path = framePath,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Pen writing diagonally
        val penPath = Path().apply {
            moveTo(w * 0.44f, h * 0.58f)
            lineTo(w * 0.72f, h * 0.30f)
            lineTo(w * 0.80f, h * 0.38f)
            lineTo(w * 0.52f, h * 0.66f)
            lineTo(w * 0.42f, h * 0.68f)
            close()
        }
        drawPath(
            path = penPath,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

// ----------------------------------------------------
// 6. Quick Action: Export Icon (Tray with Arrow Up)
// ----------------------------------------------------
@Composable
fun ExportTrayIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFCBD5E1)
) {
    Canvas(modifier = modifier) {
        val strokeW = 1.6.dp.toPx()
        val w = size.width
        val h = size.height

        // Open tray box
        val trayPath = Path().apply {
            moveTo(w * 0.24f, h * 0.45f)
            lineTo(w * 0.24f, h * 0.78f)
            quadraticBezierTo(w * 0.24f, h * 0.84f, w * 0.30f, h * 0.84f)
            lineTo(w * 0.70f, h * 0.84f)
            quadraticBezierTo(w * 0.76f, h * 0.84f, w * 0.76f, h * 0.78f)
            lineTo(w * 0.76f, h * 0.45f)
        }
        drawPath(
            path = trayPath,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Arrow pointing straight up out of tray
        val arrowStem = Path().apply {
            moveTo(w * 0.50f, h * 0.65f)
            lineTo(w * 0.50f, h * 0.20f)
        }
        drawPath(
            path = arrowStem,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        val arrowHead = Path().apply {
            moveTo(w * 0.36f, h * 0.34f)
            lineTo(w * 0.50f, h * 0.20f)
            lineTo(w * 0.64f, h * 0.34f)
        }
        drawPath(
            path = arrowHead,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

// ----------------------------------------------------
// 7. Bottom Nav: Home 4-Square Grid Icon
// ----------------------------------------------------
@Composable
fun GridFourSquaresIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val strokeW = 1.5.dp.toPx()
        val w = size.width
        val h = size.height

        val sqSize = w * 0.32f
        val gap = w * 0.12f
        val startX = w * 0.12f
        val startY = h * 0.12f
        val corner = CornerRadius(1.5.dp.toPx())

        // Top-left
        drawRoundRect(
            color = tint,
            topLeft = Offset(startX, startY),
            size = Size(sqSize, sqSize),
            cornerRadius = corner,
            style = Stroke(width = strokeW)
        )
        // Top-right
        drawRoundRect(
            color = tint,
            topLeft = Offset(startX + sqSize + gap, startY),
            size = Size(sqSize, sqSize),
            cornerRadius = corner,
            style = Stroke(width = strokeW)
        )
        // Bottom-left
        drawRoundRect(
            color = tint,
            topLeft = Offset(startX, startY + sqSize + gap),
            size = Size(sqSize, sqSize),
            cornerRadius = corner,
            style = Stroke(width = strokeW)
        )
        // Bottom-right
        drawRoundRect(
            color = tint,
            topLeft = Offset(startX + sqSize + gap, startY + sqSize + gap),
            size = Size(sqSize, sqSize),
            cornerRadius = corner,
            style = Stroke(width = strokeW)
        )
    }
}

// ----------------------------------------------------
// 8. Bottom Nav: Ledger Receipt Icon with Jagged Edge
// ----------------------------------------------------
@Composable
fun ReceiptJaggedIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF94A3B8)
) {
    Canvas(modifier = modifier) {
        val strokeW = 1.5.dp.toPx()
        val w = size.width
        val h = size.height

        val l = w * 0.22f
        val r = w * 0.78f
        val t = h * 0.15f
        val b = h * 0.85f

        // Receipt body with jagged top edge
        val receiptPath = Path().apply {
            moveTo(l, b)
            lineTo(l, t + 4.dp.toPx())
            // Jagged top peaks
            val step = (r - l) / 4f
            lineTo(l + step * 0.5f, t)
            lineTo(l + step * 1f, t + 4.dp.toPx())
            lineTo(l + step * 1.5f, t)
            lineTo(l + step * 2f, t + 4.dp.toPx())
            lineTo(l + step * 2.5f, t)
            lineTo(l + step * 3f, t + 4.dp.toPx())
            lineTo(l + step * 3.5f, t)
            lineTo(r, t + 4.dp.toPx())
            lineTo(r, b)
            close()
        }
        drawPath(
            path = receiptPath,
            color = tint,
            style = Stroke(width = strokeW, join = StrokeJoin.Round)
        )

        // Middle text lines
        drawLine(
            color = tint,
            start = Offset(w * 0.34f, h * 0.40f),
            end = Offset(w * 0.66f, h * 0.40f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.34f, h * 0.55f),
            end = Offset(w * 0.66f, h * 0.55f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.34f, h * 0.70f),
            end = Offset(w * 0.52f, h * 0.70f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

// ----------------------------------------------------
// 9. Bottom Nav: Review Tray/Inbox Icon
// ----------------------------------------------------
@Composable
fun ReviewInboxTrayIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF94A3B8)
) {
    Canvas(modifier = modifier) {
        val strokeW = 1.5.dp.toPx()
        val w = size.width
        val h = size.height

        val trayPath = Path().apply {
            moveTo(w * 0.18f, h * 0.28f)
            lineTo(w * 0.18f, h * 0.74f)
            quadraticBezierTo(w * 0.18f, h * 0.82f, w * 0.26f, h * 0.82f)
            // Bottom front with curved dip in center
            lineTo(w * 0.38f, h * 0.82f)
            quadraticBezierTo(w * 0.42f, h * 0.68f, w * 0.50f, h * 0.68f)
            quadraticBezierTo(w * 0.58f, h * 0.68f, w * 0.62f, h * 0.82f)
            lineTo(w * 0.74f, h * 0.82f)
            quadraticBezierTo(w * 0.82f, h * 0.82f, w * 0.82f, h * 0.74f)
            lineTo(w * 0.82f, h * 0.28f)
            quadraticBezierTo(w * 0.82f, h * 0.20f, w * 0.74f, h * 0.20f)
            lineTo(w * 0.26f, h * 0.20f)
            quadraticBezierTo(w * 0.18f, h * 0.20f, w * 0.18f, h * 0.28f)
            close()
        }
        drawPath(
            path = trayPath,
            color = tint,
            style = Stroke(width = strokeW, join = StrokeJoin.Round)
        )
    }
}

// ----------------------------------------------------
// 10. Bottom Nav: Insights Line Chart Icon
// ----------------------------------------------------
@Composable
fun ZigzagChartIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF94A3B8)
) {
    Canvas(modifier = modifier) {
        val strokeW = 1.8.dp.toPx()
        val w = size.width
        val h = size.height

        val chartPath = Path().apply {
            moveTo(w * 0.18f, h * 0.72f)
            lineTo(w * 0.42f, h * 0.48f)
            lineTo(w * 0.60f, h * 0.62f)
            lineTo(w * 0.82f, h * 0.26f)
        }
        drawPath(
            path = chartPath,
            color = tint,
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

// ----------------------------------------------------
// 11. Bottom Nav: Settings Sliders / Tune Icon
// ----------------------------------------------------
@Composable
fun SlidersTuneIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF94A3B8)
) {
    Canvas(modifier = modifier) {
        val strokeW = 1.5.dp.toPx()
        val w = size.width
        val h = size.height

        val y1 = h * 0.30f
        val y2 = h * 0.50f
        val y3 = h * 0.70f
        val barL = w * 0.20f
        val barR = w * 0.80f

        // Line 1: slider knob near right (at 65%)
        drawLine(color = tint, start = Offset(barL, y1), end = Offset(barR, y1), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.65f, y1 - 2.5.dp.toPx()), end = Offset(w * 0.65f, y1 + 2.5.dp.toPx()), strokeWidth = strokeW * 1.5f, cap = StrokeCap.Round)

        // Line 2: slider knob near left (at 35%)
        drawLine(color = tint, start = Offset(barL, y2), end = Offset(barR, y2), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.35f, y2 - 2.5.dp.toPx()), end = Offset(w * 0.35f, y2 + 2.5.dp.toPx()), strokeWidth = strokeW * 1.5f, cap = StrokeCap.Round)

        // Line 3: slider knob near right (at 65%)
        drawLine(color = tint, start = Offset(barL, y3), end = Offset(barR, y3), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.65f, y3 - 2.5.dp.toPx()), end = Offset(w * 0.65f, y3 + 2.5.dp.toPx()), strokeWidth = strokeW * 1.5f, cap = StrokeCap.Round)
    }
}
