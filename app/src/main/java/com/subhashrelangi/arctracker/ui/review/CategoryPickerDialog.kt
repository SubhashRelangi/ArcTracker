package com.subhashrelangi.arctracker.ui.review

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.CategoryVisuals
import com.subhashrelangi.arctracker.data.TransactionCategory
import com.subhashrelangi.arctracker.ui.theme.ArcColors
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Selection display modes for category picker experimentation.
 */
enum class CategoryPickerMode {
    ROTARY_DIAL, // True Circular Rotary Wheel with Pointer Arrow
    TACTILE_GRID  // Modern 3-Column Ergonomic Tile Matrix
}

/**
 * Modern Industrial Category Picker (Experimental Architecture).
 *
 * Eliminates centered floating modal dialogs and nested scroll traps.
 * Anchors naturally at the bottom thumb zone with tactile haptics and
 * interactive circular rotary dial / grid view modes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerDialog(
    categories: List<TransactionCategory>,
    currentCategoryId: String?,
    onCategorySelected: (TransactionCategory) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickerMode by remember { mutableStateOf(CategoryPickerMode.ROTARY_DIAL) }

    // Filter active categories or fallback to all if empty
    val validCategories = remember(categories) {
        if (categories.isNotEmpty()) categories.filter { !it.isArchived } else categories
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = ArcColors.SurfaceElevated,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = ArcColors.Border,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Select Category",
                        color = ArcColors.TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (pickerMode == CategoryPickerMode.ROTARY_DIAL) "Turn the wheel to choose" else "Tap a tile below",
                        color = ArcColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }

                // Mode Switcher Pill (Dial vs Grid)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ArcColors.Surface)
                        .border(1.dp, ArcColors.Border, RoundedCornerShape(20.dp))
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { pickerMode = CategoryPickerMode.ROTARY_DIAL },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (pickerMode == CategoryPickerMode.ROTARY_DIAL) ArcColors.PrimaryLight else Color.Transparent
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Adjust,
                            contentDescription = "Rotary Dial Mode",
                            tint = if (pickerMode == CategoryPickerMode.ROTARY_DIAL) ArcColors.Primary else ArcColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { pickerMode = CategoryPickerMode.TACTILE_GRID },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (pickerMode == CategoryPickerMode.TACTILE_GRID) ArcColors.PrimaryLight else Color.Transparent
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Tactile Grid Mode",
                            tint = if (pickerMode == CategoryPickerMode.TACTILE_GRID) ArcColors.Primary else ArcColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body: Switch between True Circular Wheel and Tactile Grid
            AnimatedContent(
                targetState = pickerMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "PickerModeAnimation"
            ) { mode ->
                when (mode) {
                    CategoryPickerMode.ROTARY_DIAL -> {
                        ArcCircularWheelCategoryPicker(
                            categories = validCategories,
                            currentCategoryId = currentCategoryId,
                            onCategoryConfirmed = { cat ->
                                onCategorySelected(cat)
                                onDismissRequest()
                            }
                        )
                    }
                    CategoryPickerMode.TACTILE_GRID -> {
                        TactileGridCategoryPicker(
                            categories = validCategories,
                            currentCategoryId = currentCategoryId,
                            onCategorySelected = { cat ->
                                onCategorySelected(cat)
                                onDismissRequest()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Signature Arc Circular Rotary Wheel with Needle Arrow Indicator.
 *
 * User rotates the circular wheel directly with tactile touch.
 * The pointer needle at 12 o'clock points down to the active category.
 * Snapping and subtle haptics provide precision physical feedback.
 */
@Composable
private fun ArcCircularWheelCategoryPicker(
    categories: List<TransactionCategory>,
    currentCategoryId: String?,
    onCategoryConfirmed: (TransactionCategory) -> Unit
) {
    if (categories.isEmpty()) return

    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val initialIndex = remember(categories, currentCategoryId) {
        val idx = categories.indexOfFirst { it.id == currentCategoryId }
        if (idx >= 0) idx else 0
    }

    val sliceAngle = remember(categories.size) { 360f / categories.size.coerceAtLeast(1) }
    val initialRotation = remember(initialIndex, sliceAngle) { -initialIndex * sliceAngle }

    var rotationAngle by remember { mutableFloatStateOf(initialRotation) }
    val rotationAnim = remember { Animatable(initialRotation) }
    var isDragging by remember { mutableStateOf(false) }

    val currentAngle = if (isDragging) rotationAngle else rotationAnim.value

    fun calculateIndex(angle: Float): Int {
        val normalized = ((-angle % 360f) + 360f) % 360f
        return (round(normalized / sliceAngle).toInt()) % categories.size
    }

    val pointedIndex = remember(currentAngle, categories.size) {
        calculateIndex(currentAngle)
    }

    var lastHapticIndex by remember { mutableIntStateOf(pointedIndex) }

    val activeCategory = categories.getOrNull(pointedIndex) ?: categories.firstOrNull()
    val activeColor = activeCategory?.let { CategoryVisuals.getColor(it.colorKey) } ?: ArcColors.Primary

    fun animateToCategory(index: Int) {
        val diff = (rotationAngle + index * sliceAngle)
        val diffNorm = ((diff % 360f) + 540f) % 360f - 180f
        val target = rotationAngle - diffNorm
        coroutineScope.launch {
            rotationAnim.snapTo(rotationAngle)
            rotationAnim.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) {
                rotationAngle = value
            }
        }
    }

    fun snapToNearest() {
        val target = round(rotationAngle / sliceAngle) * sliceAngle
        coroutineScope.launch {
            rotationAnim.snapTo(rotationAngle)
            rotationAnim.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) {
                rotationAngle = value
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val wheelSizeDp = 280.dp
        val centerPx = with(density) { (wheelSizeDp / 2f).toPx() }
        val centerOffset = Offset(centerPx, centerPx)
        val itemRadiusPx = with(density) { 98.dp.toPx() }
        val itemSizeDp = 42.dp
        val itemSizePx = with(density) { itemSizeDp.toPx() }
        val dialSurfaceColor = ArcColors.Surface
        val dialBorderColor = ArcColors.Border

        // Needle Arrow & Wheel Container
        Box(
            modifier = Modifier
                .size(wheelSizeDp)
                .pointerInput(categories.size) {
                    var lastTouchAngle = 0f
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            coroutineScope.launch { rotationAnim.snapTo(rotationAngle) }
                            val dx = offset.x - centerPx
                            val dy = offset.y - centerPx
                            lastTouchAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val dx = change.position.x - centerPx
                            val dy = change.position.y - centerPx
                            val currentTouchAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            var delta = currentTouchAngle - lastTouchAngle
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f

                            rotationAngle += delta
                            lastTouchAngle = currentTouchAngle

                            val idx = calculateIndex(rotationAngle)
                            if (idx != lastHapticIndex) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastHapticIndex = idx
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            snapToNearest()
                        },
                        onDragCancel = {
                            isDragging = false
                            snapToNearest()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // 1. Wheel Canvas: Spoke lines, Outer ring, Guide track, Needle Arrow
            Canvas(modifier = Modifier.fillMaxSize()) {
                val outerRadius = size.width / 2f - 4.dp.toPx()
                val innerRadius = 48.dp.toPx()

                // Subtle dial background circle
                drawCircle(
                    color = dialSurfaceColor,
                    radius = outerRadius,
                    center = centerOffset
                )

                // Outer border ring
                drawCircle(
                    color = dialBorderColor.copy(alpha = 0.5f),
                    radius = outerRadius,
                    center = centerOffset,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Subtle circular orbit guide track for badges
                drawCircle(
                    color = dialBorderColor.copy(alpha = 0.2f),
                    radius = itemRadiusPx,
                    center = centerOffset,
                    style = Stroke(width = 1.dp.toPx())
                )

                // Radial Spoke Divider Lines
                val sliceCount = categories.size
                for (i in 0 until sliceCount) {
                    val spokeAngle = currentAngle + (i - 0.5f) * sliceAngle - 90f
                    val rad = Math.toRadians(spokeAngle.toDouble())
                    val startX = centerOffset.x + innerRadius * cos(rad).toFloat()
                    val startY = centerOffset.y + innerRadius * sin(rad).toFloat()
                    val endX = centerOffset.x + (outerRadius - 2.dp.toPx()) * cos(rad).toFloat()
                    val endY = centerOffset.y + (outerRadius - 2.dp.toPx()) * sin(rad).toFloat()

                    drawLine(
                        color = dialBorderColor.copy(alpha = 0.35f),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Active Sector Accent Arc / Glow on rim
                drawArc(
                    color = activeColor.copy(alpha = 0.12f),
                    startAngle = currentAngle + (pointedIndex - 0.5f) * sliceAngle - 90f,
                    sweepAngle = sliceAngle,
                    useCenter = true,
                    topLeft = Offset(centerOffset.x - outerRadius, centerOffset.y - outerRadius),
                    size = Size(outerRadius * 2, outerRadius * 2)
                )

                // Top Arrow Pointer (Needle pointing directly down into top item)
                val needleWidth = 14.dp.toPx()
                val needleHeight = 16.dp.toPx()
                val needlePath = Path().apply {
                    moveTo(centerOffset.x - (needleWidth / 2f), 2.dp.toPx())
                    lineTo(centerOffset.x + (needleWidth / 2f), 2.dp.toPx())
                    lineTo(centerOffset.x, 2.dp.toPx() + needleHeight)
                    close()
                }
                drawPath(path = needlePath, color = activeColor)

                // Needle pivot cap
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = Offset(centerOffset.x, 4.dp.toPx())
                )
            }

            // 2. Category Badges around the Wheel
            categories.forEachIndexed { index, cat ->
                val itemAngle = currentAngle + index * sliceAngle - 90f
                val rad = Math.toRadians(itemAngle.toDouble())
                val xOffsetPx = centerOffset.x + (itemRadiusPx * cos(rad)).toFloat() - (itemSizePx / 2f)
                val yOffsetPx = centerOffset.y + (itemRadiusPx * sin(rad)).toFloat() - (itemSizePx / 2f)

                val isPointed = index == pointedIndex
                val catColor = CategoryVisuals.getColor(cat.colorKey)
                val catIcon = CategoryVisuals.getIcon(cat.iconKey)

                Box(
                    modifier = Modifier
                        .offset { IntOffset(xOffsetPx.roundToInt(), yOffsetPx.roundToInt()) }
                        .size(itemSizeDp)
                        .scale(if (isPointed) 1.2f else 0.88f)
                        .alpha(if (isPointed) 1f else 0.65f)
                        .clip(CircleShape)
                        .background(if (isPointed) catColor.copy(alpha = 0.25f) else ArcColors.SurfaceElevated)
                        .border(
                            width = if (isPointed) 2.5.dp else 1.dp,
                            color = if (isPointed) catColor else ArcColors.Border,
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            animateToCategory(index)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = catIcon,
                        contentDescription = cat.name,
                        tint = if (isPointed) catColor else ArcColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 3. Center Hub (Tap to confirm)
            Surface(
                shape = CircleShape,
                color = ArcColors.SurfaceElevated,
                border = BorderStroke(1.5.dp, activeColor.copy(alpha = 0.7f)),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .size(94.dp)
                    .clip(CircleShape)
                    .clickable {
                        activeCategory?.let { onCategoryConfirmed(it) }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (activeCategory != null) {
                        Icon(
                            imageVector = CategoryVisuals.getIcon(activeCategory.iconKey),
                            contentDescription = activeCategory.name,
                            tint = activeColor,
                            modifier = Modifier.size(26.dp)
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = activeCategory.name,
                            color = ArcColors.TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Tap to choose",
                            color = ArcColors.TextMuted,
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. Primary Bottom Confirm Button
        Button(
            onClick = {
                activeCategory?.let { onCategoryConfirmed(it) }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = activeColor,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Select ${activeCategory?.name ?: "Category"}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Modern 3-Column Ergonomic Tile Matrix (Alternative Industrial Mode).
 */
@Composable
private fun TactileGridCategoryPicker(
    categories: List<TransactionCategory>,
    currentCategoryId: String?,
    onCategorySelected: (TransactionCategory) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Chunk categories into rows of 3
        val rows = remember(categories) { categories.chunked(3) }

        rows.forEach { rowCategories ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowCategories.forEach { cat ->
                    val isSelected = cat.id == currentCategoryId
                    val catColor = CategoryVisuals.getColor(cat.colorKey)
                    val catIcon = CategoryVisuals.getIcon(cat.iconKey)

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) ArcColors.PrimaryLight else ArcColors.Surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) ArcColors.Primary else ArcColors.Border
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCategorySelected(cat)
                            }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(catColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = catIcon,
                                    contentDescription = null,
                                    tint = catColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = cat.name,
                                color = if (isSelected) ArcColors.Primary else ArcColors.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Fill blank slots if the last row has fewer than 3 items
                if (rowCategories.size < 3) {
                    repeat(3 - rowCategories.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
