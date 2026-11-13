package com.subhashrelangi.arctracker.ui.review

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashrelangi.arctracker.data.CategoryVisuals
import com.subhashrelangi.arctracker.data.TransactionCategory
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Selection display modes for category picker experimentation.
 */
enum class CategoryPickerMode {
    ROTARY_DIAL, // Signature Arc Rotary Wheel with Pointer Arrow
    TACTILE_GRID  // Modern 3-Column Ergonomic Tile Matrix
}

/**
 * Modern Industrial Category Picker (Experimental Architecture).
 *
 * Eliminates centered floating modal dialogs and nested scroll traps.
 * Anchors naturally at the bottom thumb zone with tactile haptics and
 * interactive rotary dial / grid view modes.
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

    val initialIndex = remember(validCategories, currentCategoryId) {
        val idx = validCategories.indexOfFirst { it.id == currentCategoryId }
        if (idx >= 0) idx else 0
    }

    var selectedIndex by remember { mutableIntStateOf(initialIndex) }
    val activeCategory = validCategories.getOrNull(selectedIndex) ?: validCategories.firstOrNull()

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
                        text = if (pickerMode == CategoryPickerMode.ROTARY_DIAL) "Rotate dial to select" else "Tap a tile below",
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

            // Body: Switch between Rotary Dial and Tactile Grid
            AnimatedContent(
                targetState = pickerMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "PickerModeAnimation"
            ) { mode ->
                when (mode) {
                    CategoryPickerMode.ROTARY_DIAL -> {
                        RotaryDialCategoryPicker(
                            categories = validCategories,
                            selectedIndex = selectedIndex,
                            onIndexChanged = { selectedIndex = it },
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
 * Signature Arc Rotary Wheel with Needle Arrow Indicator.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RotaryDialCategoryPicker(
    categories: List<TransactionCategory>,
    selectedIndex: Int,
    onIndexChanged: (Int) -> Unit,
    onCategoryConfirmed: (TransactionCategory) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState)

    val activeCategory = categories.getOrNull(selectedIndex) ?: categories.firstOrNull()
    val activeColor = activeCategory?.let { CategoryVisuals.getColor(it.colorKey) } ?: ArcColors.Primary

    // Sync scroll position with selected index
    LaunchedEffect(lazyListState.firstVisibleItemIndex) {
        val centerIdx = lazyListState.firstVisibleItemIndex
        if (centerIdx in categories.indices && centerIdx != selectedIndex) {
            onIndexChanged(centerIdx)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Active Featured Showcase Card
        if (activeCategory != null) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = ArcColors.Surface,
                border = BorderStroke(1.5.dp, activeColor.copy(alpha = 0.6f)),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(activeColor.copy(alpha = 0.2f))
                                .border(1.5.dp, activeColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CategoryVisuals.getIcon(activeCategory.iconKey),
                                contentDescription = null,
                                tint = activeColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = activeCategory.name,
                                color = ArcColors.TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Category Target",
                                color = ArcColors.TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Confirm Pill Button
                    Button(
                        onClick = { onCategoryConfirmed(activeCategory) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = activeColor,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Apply",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Pointer Needle Indicator (Pinup Arrow)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Active Indicator",
                tint = activeColor,
                modifier = Modifier
                    .size(32.dp)
                    .scale(1.3f)
            )
        }

        // Horizontal Arc Rotary Carousel Track
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
        ) {
            val itemWidth = 72.dp
            val halfScreenWidth = maxWidth / 2
            val sidePadding = (halfScreenWidth - (itemWidth / 2)).coerceAtLeast(0.dp)

            // Background Subtle Arc Glow Line
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                activeColor.copy(alpha = 0.35f),
                                activeColor,
                                activeColor.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )
            )

            LazyRow(
                state = lazyListState,
                flingBehavior = snapFlingBehavior,
                contentPadding = PaddingValues(horizontal = sidePadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(categories, key = { _, cat -> cat.id }) { index, cat ->
                    val isSelected = index == selectedIndex
                    val catColor = CategoryVisuals.getColor(cat.colorKey)
                    val catIcon = CategoryVisuals.getIcon(cat.iconKey)

                    val scaleAnim by animateFloatAsState(
                        targetValue = if (isSelected) 1.25f else 0.85f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "ItemScale"
                    )

                    val alphaAnim by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.5f,
                        animationSpec = tween(150),
                        label = "ItemAlpha"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(itemWidth)
                            .scale(scaleAnim)
                            .alpha(alphaAnim)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onIndexChanged(index)
                                coroutineScope.launch {
                                    lazyListState.animateScrollToItem(index)
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) catColor.copy(alpha = 0.22f) else ArcColors.Surface)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) catColor else ArcColors.Border,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = catIcon,
                                contentDescription = cat.name,
                                tint = if (isSelected) catColor else ArcColors.TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = cat.name,
                            color = if (isSelected) ArcColors.TextPrimary else ArcColors.TextMuted,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
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
