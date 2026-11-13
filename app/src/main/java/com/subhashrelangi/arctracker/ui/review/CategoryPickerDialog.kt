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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
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
import com.subhashrelangi.arctracker.data.BuiltInCategories
import com.subhashrelangi.arctracker.data.CategoryVisuals
import com.subhashrelangi.arctracker.data.KnownFinancialAccount
import com.subhashrelangi.arctracker.data.TransactionCategory
import com.subhashrelangi.arctracker.engine.account.InstrumentType
import com.subhashrelangi.arctracker.ui.theme.ArcColors
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Extension modifier that renders a clean, native vertical scrollbar on a LazyColumn.
 */
fun Modifier.verticalScrollbar(
    state: LazyListState,
    color: Color = Color.Gray.copy(alpha = 0.5f),
    width: androidx.compose.ui.unit.Dp = 4.dp
): Modifier = this.drawWithContent {
    drawContent()
    val totalItems = state.layoutInfo.totalItemsCount
    val visibleItems = state.layoutInfo.visibleItemsInfo.size
    if (totalItems > 0 && visibleItems < totalItems) {
        val firstVisible = state.firstVisibleItemIndex
        val scrollbarHeightRatio = (visibleItems.toFloat() / totalItems.toFloat()).coerceIn(0.12f, 1f)
        val scrollbarHeight = size.height * scrollbarHeightRatio
        val maxScrollOffset = size.height - scrollbarHeight
        val scrollOffsetRatio = (firstVisible.toFloat() / (totalItems - visibleItems).coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        val scrollbarOffset = scrollOffsetRatio * maxScrollOffset

        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - width.toPx() - 3.dp.toPx(), scrollbarOffset),
            size = Size(width.toPx(), scrollbarHeight),
            cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2)
        )
    }
}

/**
 * Visual styling choices for category pickers.
 */
enum class CategoryPickerStyle {
    CIRCULAR_WHEEL, // Review Section: bottom-to-center bottom sheet with list/grid and wheel options
    HORIZONTAL_SNAP // Universal Industrial Bottom Sheet with Vertical Scrollbar
}

/**
 * Selection display modes for category picker.
 */
enum class CategoryPickerMode {
    VERTICAL_LIST, // Clean vertical scrollable list with scrollbar
    ROTARY_DIAL    // Interactive circular wheel
}

/**
 * Universal Industrial Category Picker (BottomSheet Architecture).
 * Attached to the bottom of the screen, appears from bottom to center with a vertical scrollbar.
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
        val nonArchived = if (categories.isNotEmpty()) categories.filter { !it.isArchived } else BuiltInCategories.ALL
        // Deduplicate and canonicalize categories so that duplicates like "Transport" and "Travel"
        // or same-meaning categories are merged cleanly into canonical categories.
        val seenCanonicalKeys = mutableSetOf<String>()
        val result = mutableListOf<TransactionCategory>()
        for (cat in nonArchived) {
            val canonicalName = when (cat.name.trim().lowercase()) {
                "travel", "transport", "commute", "transit", "travel & transport", "travel and transport" -> "Travel & Transport"
                "shopping & tech" -> "Shopping"
                "bills and utilities", "housing & utilities", "housing & rent" -> "Bills & Utilities"
                "healthcare & medical" -> "Healthcare"
                else -> cat.name.trim()
            }
            val key = canonicalName.lowercase()
            if (key !in seenCanonicalKeys) {
                seenCanonicalKeys.add(key)
                result.add(if (cat.name != canonicalName) cat.copy(name = canonicalName) else cat)
            }
        }
        if (result.isNotEmpty()) result else BuiltInCategories.ALL
    }

    if (pickerStyle == CategoryPickerStyle.CIRCULAR_WHEEL) {
        ArcReviewCategorySheet(
            categories = validCategories,
            currentCategoryId = currentCategoryId,
            onCategorySelected = onCategorySelected,
            onDismissRequest = onDismissRequest
        )
    } else {
        ArcVerticalCategorySheet(
            categories = validCategories,
            currentCategoryId = currentCategoryId,
            onCategorySelected = onCategorySelected,
            onDismissRequest = onDismissRequest
        )
    }
}

/**
 * Universal bottom-to-center vertical category picker sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArcVerticalCategorySheet(
    categories: List<TransactionCategory>,
    currentCategoryId: String?,
    onCategorySelected: (TransactionCategory) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

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
                .heightIn(min = 340.dp, max = 460.dp)
                .padding(bottom = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Select Category",
                        color = ArcColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose category from list below",
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

            Spacer(modifier = Modifier.height(8.dp))

            // Body: Clean Vertical List with Scrollbar
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScrollbar(listState, color = ArcColors.Border.copy(alpha = 0.7f)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(categories, key = { it.id }) { cat ->
                    val isSelected = cat.id == currentCategoryId
                    val catColor = CategoryVisuals.getColor(cat.colorKey)
                    val catIcon = CategoryVisuals.getIcon(cat.iconKey)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) catColor.copy(alpha = 0.16f) else ArcColors.SurfaceHighlight,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) catColor else ArcColors.Border.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCategorySelected(cat)
                                onDismissRequest()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(catColor.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = catIcon,
                                    contentDescription = cat.name,
                                    tint = catColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = cat.name,
                                color = ArcColors.TextPrimary,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = catColor,
                                    modifier = Modifier.size(18.dp)
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
 * Category sheet for Review section:
 * Bottom sheet from bottom to center with vertical list, grid, and rotary wheel options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArcReviewCategorySheet(
    categories: List<TransactionCategory>,
    currentCategoryId: String?,
    onCategorySelected: (TransactionCategory) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
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
                .heightIn(min = 340.dp, max = 480.dp)
                .padding(bottom = 20.dp),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Select Category",
                        color = ArcColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (pickerMode) {
                            CategoryPickerMode.VERTICAL_LIST -> "Choose category from list below"
                            CategoryPickerMode.ROTARY_DIAL -> "Turn wheel & tap center to select"
                        },
                        color = ArcColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }

                // Mode Switcher Pill (Wheel vs List)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ArcColors.Surface)
                        .border(1.dp, ArcColors.Border, RoundedCornerShape(20.dp))
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { pickerMode = CategoryPickerMode.ROTARY_DIAL },
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (pickerMode == CategoryPickerMode.ROTARY_DIAL) ArcColors.PrimaryLight else Color.Transparent
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Adjust,
                            contentDescription = "Rotary Dial Mode",
                            tint = if (pickerMode == CategoryPickerMode.ROTARY_DIAL) ArcColors.Primary else ArcColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { pickerMode = CategoryPickerMode.VERTICAL_LIST },
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (pickerMode == CategoryPickerMode.VERTICAL_LIST) ArcColors.PrimaryLight else Color.Transparent
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatListBulleted,
                            contentDescription = "Vertical List Mode",
                            tint = if (pickerMode == CategoryPickerMode.VERTICAL_LIST) ArcColors.Primary else ArcColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

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

            Spacer(modifier = Modifier.height(8.dp))

            // Body: Switch between Vertical List and Rotary Dial
            when (pickerMode) {
                CategoryPickerMode.VERTICAL_LIST -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScrollbar(listState, color = ArcColors.Border.copy(alpha = 0.7f)),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(categories, key = { it.id }) { cat ->
                            val isSelected = cat.id == currentCategoryId
                            val catColor = CategoryVisuals.getColor(cat.colorKey)
                            val catIcon = CategoryVisuals.getIcon(cat.iconKey)

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) catColor.copy(alpha = 0.16f) else ArcColors.SurfaceHighlight,
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) catColor else ArcColors.Border.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onCategorySelected(cat)
                                        onDismissRequest()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(catColor.copy(alpha = 0.22f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = catIcon,
                                            contentDescription = cat.name,
                                            tint = catColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Text(
                                        text = cat.name,
                                        color = ArcColors.TextPrimary,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = catColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
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
            }
        }
    }
}

/**
 * Signature Arc Circular Rotary Wheel with Needle Arrow Indicator.
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
                        color = dialBorderColor.copy(alpha = 0.22f),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Accent arc on outer rim under active category
                drawArc(
                    color = activeColor.copy(alpha = 0.6f),
                    startAngle = currentAngle + (pointedIndex - 0.45f) * sliceAngle - 90f,
                    sweepAngle = sliceAngle * 0.9f,
                    useCenter = false,
                    topLeft = Offset(centerOffset.x - outerRadius, centerOffset.y - outerRadius),
                    size = Size(outerRadius * 2, outerRadius * 2),
                    style = Stroke(width = 3.dp.toPx())
                )

                // Top Arrow Pointer Needle
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
 * Minimalist, industrial-standard vertical scrolling account picker dialog.
 * Attached to the bottom of the screen, appearing from bottom to center with a vertical scrollbar.
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
    val listState = rememberLazyListState()

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
                .heightIn(min = 320.dp, max = 460.dp)
                .padding(bottom = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                        .padding(horizontal = 20.dp, vertical = 24.dp),
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
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScrollbar(listState, color = ArcColors.Border.copy(alpha = 0.7f)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Optional Cash / Wallet Tile
                    if (allowCashOption) {
                        item(key = "cash_wallet_option") {
                            val isCashSelected = currentAccountId == null && currentAccountSuffix == null
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCashSelected) ArcColors.PrimaryLight else ArcColors.SurfaceHighlight,
                                border = BorderStroke(
                                    width = if (isCashSelected) 1.5.dp else 1.dp,
                                    color = if (isCashSelected) ArcColors.Primary else ArcColors.Border.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(12.dp))
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
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
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

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Cash / Wallet",
                                            color = ArcColors.TextPrimary,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Liquid Cash or untracked wallet",
                                            color = ArcColors.TextSecondary,
                                            fontSize = 11.5.sp
                                        )
                                    }

                                    if (isCashSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = ArcColors.Primary,
                                            modifier = Modifier.size(18.dp)
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
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) ArcColors.PrimaryLight else ArcColors.SurfaceHighlight,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) ArcColors.Primary else ArcColors.Border.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAccountSelected(acc)
                                    onDismissRequest()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
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

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = institutionTitle,
                                        color = ArcColors.TextPrimary,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = typeAndSuffix,
                                        color = ArcColors.TextSecondary,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = ArcColors.Primary,
                                        modifier = Modifier.size(18.dp)
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
 * Generic horizontal/vertical option item model.
 */
data class HorizontalOptionItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null
)

/**
 * Minimalist, industrial-standard vertical scrolling option picker dialog.
 * Attached to the bottom of the screen, appearing from bottom to center with a vertical scrollbar.
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
    val listState = rememberLazyListState()

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
                .heightIn(min = 280.dp, max = 440.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScrollbar(listState, color = ArcColors.Border.copy(alpha = 0.7f)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(options, key = { it.id }) { item ->
                    val isSelected = item.id == selectedId

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) ArcColors.PrimaryLight else ArcColors.SurfaceHighlight,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) ArcColors.Primary else ArcColors.Border.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOptionSelected(item)
                                onDismissRequest()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.icon != null) {
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
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) ArcColors.Primary else ArcColors.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = ArcColors.TextPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (item.subtitle != null) {
                                    Text(
                                        text = item.subtitle,
                                        color = ArcColors.TextSecondary,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = ArcColors.Primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
