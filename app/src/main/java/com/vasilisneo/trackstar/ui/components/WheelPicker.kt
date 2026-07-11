package com.vasilisneo.trackstar.ui.components

// Scroll-wheel number picker, porting the look/feel of iOS's UIPickerView-backed controls
// (TimerPicker / DistancePicker / RestTimePicker in CreateExerciseBottomSheet): a vertical
// snapping wheel with a highlighted center band, the centered value bold/opaque and neighbours
// fading out. Compose it as one or more `WheelColumn`s inside `WheelPickerRow` so several wheels
// (hour/min/sec, km/m, min/sec) share a single center band and read as one control.

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

// One wheel: a list of display strings, the current index, and a change callback. `unit` renders
// a small dim caption pinned beside the numbers (e.g. "min", "sec", "km", "m").
data class WheelColumn(
    val items: List<String>,
    val selectedIndex: Int,
    val onSelectedIndexChange: (Int) -> Unit,
    val unit: String? = null,
    val width: Dp = 56.dp,
)

// Row of wheels sharing one center highlight band — the whole control reads as a single picker.
@Composable
fun WheelPickerRow(
    columns: List<WheelColumn>,
    modifier: Modifier = Modifier,
    visibleCount: Int = 5,
    itemHeight: Dp = 32.dp,
) {
    Box(
        modifier = modifier.height(itemHeight * visibleCount),
        contentAlignment = Alignment.Center,
    ) {
        // Center band behind the selected row.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f))
        )
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            columns.forEach { column ->
                WheelColumnView(column = column, visibleCount = visibleCount, itemHeight = itemHeight)
            }
        }
    }
}

@Composable
private fun WheelColumnView(column: WheelColumn, visibleCount: Int, itemHeight: Dp) {
    val lastIndex = (column.items.size - 1).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = column.selectedIndex.coerceIn(0, lastIndex))
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    val centeredIndex by remember {
        derivedStateOf {
            val offsetRows = (listState.firstVisibleItemScrollOffset / itemHeightPx).roundToInt()
            (listState.firstVisibleItemIndex + offsetRows).coerceIn(0, lastIndex)
        }
    }

    // Report the settled value only once the wheel comes to rest on a snap position.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && centeredIndex != column.selectedIndex) column.onSelectedIndexChange(centeredIndex)
        }
    }
    // Follow programmatic changes (e.g. the field was reset when switching frequency type).
    LaunchedEffect(column.selectedIndex) {
        if (!listState.isScrollInProgress && centeredIndex != column.selectedIndex) {
            listState.scrollToItem(column.selectedIndex.coerceIn(0, lastIndex))
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = itemHeight * (visibleCount / 2)),
            modifier = Modifier.width(column.width).height(itemHeight * visibleCount),
        ) {
            itemsIndexed(column.items) { index, text ->
                val distance = abs(index - centeredIndex)
                Box(modifier = Modifier.height(itemHeight).fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Text(
                        text,
                        fontSize = if (distance == 0) 20.sp else 17.sp,
                        fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Normal,
                        color = Color.White.copy(
                            alpha = when (distance) { 0 -> 1f; 1 -> 0.45f; 2 -> 0.2f; else -> 0.1f }
                        ),
                    )
                }
            }
        }
        if (column.unit != null) {
            androidx.compose.material3.Text(
                column.unit,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.padding(start = 4.dp, end = 8.dp),
            )
        }
    }
}
