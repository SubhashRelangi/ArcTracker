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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.TransactionCategory
import com.subhashrelangi.arctracker.engine.account.InstrumentType
import com.subhashrelangi.arctracker.ui.theme.ArcColors
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Visual styling choices for category pickers.
 */
enum class CategoryPickerStyle {
    CIRCULAR_WHEEL, // Signature Arc Rotary Wheel (Exclusively for Review Section)
    HORIZONTAL_SNAP // Universal Industrial Horizontal Snapping Cards (All other screens)
}

/**
 * Selection display modes for category picker in Review section.
 */
enum class CategoryPickerMode {
    ROTARY_DIAL, // True Circular Rotary Wheel with Pointer Arrow
    TACTILE_GRID  // Modern 3-Column Ergonomic Tile Matrix
}

/**
 * Universal Industrial Category Picker (BottomSheet Architecture).
 *
 * Supports both:
 * 1. CIRCULAR_WHEEL: Signature tactile rotary dial with needle arrow (Review section).
 * 2. HORIZONTAL_SNAP: Minimalist, industrial-standard horizontal snapping cards (All other screens).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerDialog(
    categories: List<TransactionCategory>,
    currentCategoryId: String?,
    onCategorySelected: (TransactionCategory) -> Unit,
    onDismissRequest: () -> Unit,
    pickerStyle: CategoryPickerStyle = CategoryPickerStyle.HORIZONTAL_SNAP
) {
    val validCategories = remember(categories) {
        if (categories.isNotEmpty()) categories.filter { !it.isArchived } else categories
    }

    if (pickerStyle == CategoryPickerStyle.CIRCULAR_WHEEL) {
        ArcCircularWheelCategorySheet(
            categories = validCategories,
            currentCategoryId = currentCategoryId,
            onCategorySelected = onCategorySelected,
            onDismissRequest = onDismissRequest
        )
    } else {
        ArcHorizontalCategorySheet(
            categories = validCategories,
            currentCategoryId = currentCategoryId,
            onCategorySelected = onCategorySelected,
            onDismissRequest = onDismissRequest
        )
    }
}

/**
 * Minimalist, industrial standard horizontal snapping category picker sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArcHorizontalCategorySheet(
    categories: List<TransactionCategory>,
    currentCategoryId: String?,
    onCategorySelected: (TransactionCategory) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

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
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header
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
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap a category card to apply",
                        color = ArcColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ArcColors.SurfaceHighlight,
                    border = BorderStroke(1.dp, ArcColors.Border.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "${categories.size} categories",
                        color = ArcColors.TextMuted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Horizontal Snapping Cards
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories, key = { it.id }) { cat ->
                    val isSelected = cat.id == currentCategoryId
                    val catColor = CategoryVisuals.getColor(cat.colorKey)
                    val catIcon = CategoryVisuals.getIcon(cat.iconKey)

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) catColor.copy(alpha = 0.18f) else ArcColors.SurfaceHighlight,
                        border = BorderStroke(
                            width = if (isSelected) 1.8.dp else 1.dp,
                            color = if (isSelected) catColor else ArcColors.Border.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .width(114.dp)
                            .height(92.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCategorySelected(cat)
                                onDismissRequest()
                            }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(7.dp)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(catColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(catColor.copy(alpha = if (isSelected) 0.35f else 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = catIcon,
                                        contentDescription = cat.name,
                                        tint = catColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(7.dp))

                                Text(
                                    text = cat.name,
                                    color = if (isSelected) ArcColors.TextPrimary else ArcColors.TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Signature Arc Circular Wheel Category Sheet (Review Section).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArcCircularWheelCategorySheet(
    categories: List<TransactionCategory>,
    currentCategoryId: String?,
    onCategorySelected: (TransactionCategory) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickerMode by remember { mutableStateOf(CategoryPickerMode.ROTARY_DIAL) }

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
                        text = if (pickerMode == CategoryPickerMode.ROTARY_DIAL) "Turn wheel & tap center to select" else "Tap a tile below",
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
                            categories = categories,
                            currentCategoryId = currentCategoryId,
                            onCategoryConfirmed = { cat ->
                                onCategorySelected(cat)
                                onDismissRequest()
                            }
                        )
                    }
                    CategoryPickerMode.TACTILE_GRID -> {
                        TactileGridCategoryPicker(
                            categories = categories,
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

    val dialSurfaceColor = ArcColors.Surface
    val dialBorderColor = ArcColors.Border
    val primaryThemeColor = ArcColors.Primary

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
        val itemRadiusPx = with(density) { 96.dp.toPx() }
        val itemSizeDp = 40.dp
        val itemSizePx = with(density) { itemSizeDp.toPx() }

        // Needle Arrow & Wheel Container (TopStart alignment so polar offsets are accurate)
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
            contentAlignment = Alignment.TopStart
        ) {
            // 1. Wheel Canvas: Spoke lines, Outer ring, Guide track, Needle Arrow
            Canvas(modifier = Modifier.fillMaxSize()) {
                val outerRadius = size.width / 2f - 4.dp.toPx()
                val innerRadius = 46.dp.toPx()

                // Dial background circle
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

                // Radial Spoke Divider Lines (clean, separating each category sector)
                val sliceCount = categories.size
                for (i in 0 until sliceCount) {
                    val spokeAngle = currentAngle + (i - 0.5f) * sliceAngle - 90f
                    val rad = Math.toRadians(spokeAngle.toDouble())
                    val startX = centerOffset.x + innerRadius * cos(rad).toFloat()
                    val startY = centerOffset.y + innerRadius * sin(rad).toFloat()
                    val endX = centerOffset.x + (outerRadius - 2.dp.toPx()) * cos(rad).toFloat()
                    val endY = centerOffset.y + (outerRadius - 2.dp.toPx()) * sin(rad).toFloat()

                    drawLine(
                        color = dialBorderColor.copy(alpha = 0.22f),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Clean accent arc on the outer rim under active category (no muddy slice bleed)
                drawArc(
                    color = activeColor.copy(alpha = 0.6f),
                    startAngle = currentAngle + (pointedIndex - 0.45f) * sliceAngle - 90f,
                    sweepAngle = sliceAngle * 0.9f,
                    useCenter = false,
                    topLeft = Offset(centerOffset.x - outerRadius, centerOffset.y - outerRadius),
                    size = Size(outerRadius * 2, outerRadius * 2),
                    style = Stroke(width = 3.dp.toPx())
                )

                // Top Arrow Pointer (Precision needle pointing directly down into top item)
                val needleWidth = 12.dp.toPx()
                val needleHeight = 15.dp.toPx()
                val needleTopY = 4.dp.toPx()
                val needlePath = Path().apply {
                    moveTo(centerOffset.x - (needleWidth / 2f), needleTopY)
                    lineTo(centerOffset.x + (needleWidth / 2f), needleTopY)
                    lineTo(centerOffset.x, needleTopY + needleHeight)
                    close()
                }
                drawPath(path = needlePath, color = activeColor)

                // Needle pivot cap
                drawCircle(
                    color = Color.White,
                    radius = 2.5.dp.toPx(),
                    center = Offset(centerOffset.x, needleTopY + 3.dp.toPx())
                )
            }

            // 2. Category Badges around the Wheel with vibrant theme colors
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
                        .scale(if (isPointed) 1.22f else 0.90f)
                        .alpha(if (isPointed) 1f else 0.85f)
                        .clip(CircleShape)
                        .background(catColor.copy(alpha = if (isPointed) 0.32f else 0.16f))
                        .border(
                            width = if (isPointed) 2.5.dp else 1.2.dp,
                            color = if (isPointed) catColor else catColor.copy(alpha = 0.55f),
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (isPointed) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCategoryConfirmed(cat)
                            } else {
                                animateToCategory(index)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = catIcon,
                        contentDescription = cat.name,
                        tint = catColor,
                        modifier = Modifier.size(if (isPointed) 22.dp else 19.dp)
                    )
                }
            }

            // 3. Center Hub (Centered in wheel, tap to confirm)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = dialSurfaceColor,
                    border = BorderStroke(2.dp, activeColor),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .clickable {
                            activeCategory?.let {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCategoryConfirmed(it)
                            }
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
                                modifier = Modifier.size(28.dp)
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = activeCategory.name,
                                color = ArcColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
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

/**
 * Minimalist, industrial-standard horizontal snapping account picker dialog.
 * Replaces old intrusive centered modal popups across the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HorizontalAccountPickerDialog(
    title: String = "Select Account",
    subtitle: String = "Choose linked bank account or card",
    detectedSuffix: String? = null,
    availableAccounts: List<KnownFinancialAccount>,
    currentAccountId: String? = null,
    currentAccountSuffix: String? = null,
    allowCashOption: Boolean = false,
    onCashSelected: (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
    onAccountSelected: (KnownFinancialAccount) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

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
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        color = ArcColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        color = ArcColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = ArcColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Suffix detection banner if available
            if (!detectedSuffix.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ArcColors.PrimaryLight,
                    border = BorderStroke(1.dp, ArcColors.Primary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ArcColors.Primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Detected SMS Account: Ending in ••••$detectedSuffix",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ArcColors.Primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (availableAccounts.isEmpty() && !allowCashOption) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No linked accounts found. Please link or import accounts.",
                        color = ArcColors.TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Optional Cash / Wallet Tile
                    if (allowCashOption) {
                        item(key = "cash_wallet_option") {
                            val isCashSelected = currentAccountId == null && currentAccountSuffix == null
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isCashSelected) ArcColors.PrimaryLight else ArcColors.SurfaceHighlight,
                                border = BorderStroke(
                                    width = if (isCashSelected) 1.8.dp else 1.dp,
                                    color = if (isCashSelected) ArcColors.Primary else ArcColors.Border.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .width(140.dp)
                                    .height(96.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (onCashSelected != null) {
                                            onCashSelected()
                                        } else {
                                            onAccountSelected(
                                                KnownFinancialAccount(
                                                    id = "cash_liquid",
                                                    institutionName = "Cash / Wallet",
                                                    accountSuffix = "",
                                                    instrumentType = InstrumentType.CASH
                                                )
                                            )
                                        }
                                        onDismissRequest()
                                    }
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (isCashSelected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(7.dp)
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(ArcColors.Primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isCashSelected) ArcColors.Primary.copy(alpha = 0.22f) else ArcColors.BorderSubtle
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.AccountBalanceWallet,
                                                contentDescription = null,
                                                tint = if (isCashSelected) ArcColors.Primary else ArcColors.TextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = "Cash / Wallet",
                                            color = ArcColors.TextPrimary,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Text(
                                            text = "Liquid Cash",
                                            color = ArcColors.TextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(availableAccounts, key = { it.id }) { acc ->
                        val isSelected = (currentAccountId != null && acc.id == currentAccountId) ||
                                (currentAccountSuffix != null && acc.accountSuffix.equals(currentAccountSuffix, ignoreCase = true)) ||
                                (detectedSuffix != null && acc.accountSuffix.equals(detectedSuffix, ignoreCase = true))

                        val bankIcon = when (acc.instrumentType) {
                            InstrumentType.CARD -> Icons.Outlined.CreditCard
                            InstrumentType.WALLET -> Icons.Outlined.AccountBalanceWallet
                            InstrumentType.CASH -> Icons.Outlined.Payments
                            else -> Icons.Outlined.AccountBalance
                        }

                        val institutionTitle = acc.institutionName ?: acc.institutionId?.uppercase() ?: "Bank"
                        val typeAndSuffix = "${acc.instrumentType.name.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() }} •• ${acc.accountSuffix}"

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) ArcColors.PrimaryLight else ArcColors.SurfaceHighlight,
                            border = BorderStroke(
                                width = if (isSelected) 1.8.dp else 1.dp,
                                color = if (isSelected) ArcColors.Primary else ArcColors.Border.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .width(146.dp)
                                .height(96.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAccountSelected(acc)
                                    onDismissRequest()
                                }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(7.dp)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(ArcColors.Primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) ArcColors.Primary.copy(alpha = 0.22f) else ArcColors.BorderSubtle
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = bankIcon,
                                            contentDescription = null,
                                            tint = if (isSelected) ArcColors.Primary else ArcColors.TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = institutionTitle,
                                        color = ArcColors.TextPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = typeAndSuffix,
                                        color = ArcColors.TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Generic horizontal item model.
 */
data class HorizontalOptionItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null
)

/**
 * Minimalist, industrial-standard horizontal snapping arbitrary option picker dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HorizontalOptionPickerDialog(
    title: String,
    subtitle: String? = null,
    options: List<HorizontalOptionItem>,
    selectedId: String?,
    onDismissRequest: () -> Unit,
    onOptionSelected: (HorizontalOptionItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

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
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        color = ArcColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = ArcColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = ArcColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(options, key = { it.id }) { item ->
                    val isSelected = item.id == selectedId

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) ArcColors.PrimaryLight else ArcColors.SurfaceHighlight,
                        border = BorderStroke(
                            width = if (isSelected) 1.8.dp else 1.dp,
                            color = if (isSelected) ArcColors.Primary else ArcColors.Border.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .width(136.dp)
                            .height(92.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOptionSelected(item)
                                onDismissRequest()
                            }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(7.dp)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(ArcColors.Primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (item.icon != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) ArcColors.Primary.copy(alpha = 0.22f) else ArcColors.BorderSubtle
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) ArcColors.Primary else ArcColors.TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                }

                                Text(
                                    text = item.title,
                                    color = ArcColors.TextPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (item.subtitle != null) {
                                    Text(
                                        text = item.subtitle,
                                        color = ArcColors.TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
