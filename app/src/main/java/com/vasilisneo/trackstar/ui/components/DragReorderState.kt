package com.vasilisneo.trackstar.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Port of iOS's drag-to-reorder (WeeklyPlanView/SessionEditView reorderGesture + checkAndSwap +
// checkAndScroll): long-press the leading handle to lift the row — it scales to 1.05 and floats
// above its neighbours (zIndex) while tracking the finger — with a haptic on lift and on each
// swap, live swapping as it crosses a neighbour's midpoint, and auto-scrolling when dragged near
// the list's top/bottom edge. The reordered result is persisted once on drag end.
//
// Unlike iOS this scales/translates the row IN PLACE (graphicsLayer) rather than rendering a
// separate ghost overlay in global coordinates — visually the same lift, without the global-
// coordinate math, and it stays inside the LazyColumn so neighbours reflow via animateItem().
class DragReorderState<T>(initialItems: List<T>, private val key: (T) -> String) {
    var order by mutableStateOf(initialItems)
        private set
    var draggingKey by mutableStateOf<String?>(null)
        private set
    var dragOffsetY by mutableStateOf(0f)
        private set

    private val itemHeightPx = mutableStateMapOf<String, Int>()

    fun syncOrder(items: List<T>) {
        if (draggingKey == null) order = items
    }

    fun reportHeight(k: String, heightPx: Int) {
        itemHeightPx[k] = heightPx
    }

    fun startDrag(k: String) {
        draggingKey = k
        dragOffsetY = 0f
    }

    // Advances the drag by the finger delta; returns true if it triggered a swap (so the caller
    // can fire a haptic).
    fun updateDrag(deltaY: Float): Boolean {
        if (draggingKey == null) return false
        dragOffsetY += deltaY
        return maybeSwap()
    }

    // Called by the auto-scroll loop after it scrolls the list: keeps the lifted row under the
    // finger by folding the scrolled distance into the drag offset, then re-checks for a swap.
    fun onAutoScroll(deltaY: Float): Boolean {
        if (draggingKey == null) return false
        dragOffsetY += deltaY
        return maybeSwap()
    }

    fun endDrag(onCommit: (List<T>) -> Unit) {
        val committed = order
        draggingKey = null
        dragOffsetY = 0f
        onCommit(committed)
    }

    private fun maybeSwap(): Boolean {
        val dk = draggingKey ?: return false
        val currentIndex = order.indexOfFirst { key(it) == dk }
        if (currentIndex < 0) return false

        if (dragOffsetY > 0) {
            val nextIndex = currentIndex + 1
            if (nextIndex >= order.size) return false
            val nextHeight = itemHeightPx[key(order[nextIndex])] ?: return false
            if (dragOffsetY > nextHeight / 2f) {
                order = order.toMutableList().apply {
                    val tmp = this[currentIndex]; this[currentIndex] = this[nextIndex]; this[nextIndex] = tmp
                }
                dragOffsetY -= nextHeight
                return true
            }
        } else {
            val prevIndex = currentIndex - 1
            if (prevIndex < 0) return false
            val prevHeight = itemHeightPx[key(order[prevIndex])] ?: return false
            if (-dragOffsetY > prevHeight / 2f) {
                order = order.toMutableList().apply {
                    val tmp = this[currentIndex]; this[currentIndex] = this[prevIndex]; this[prevIndex] = tmp
                }
                dragOffsetY += prevHeight
                return true
            }
        }
        return false
    }
}

// Applies the lift transform (translate + 1.05 scale, animated) and z-index to a row, and reports
// its measured height for swap-threshold math. Call on the row's outer Modifier.
fun <T> Modifier.dragReorderItem(state: DragReorderState<T>, itemKey: String): Modifier = composed {
    val dragging = state.draggingKey == itemKey
    val scale by animateFloatAsState(targetValue = if (dragging) 1.05f else 1f, label = "dragLiftScale")
    Modifier
        .onSizeChanged { state.reportHeight(itemKey, it.height) }
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer {
            translationY = if (dragging) state.dragOffsetY else 0f
            scaleX = scale
            scaleY = scale
        }
}

// Attaches the long-press-drag gesture to a narrow handle area (not the whole row) so it doesn't
// fight the list's own scroll gesture — mirrors iOS's 44pt leading drag-handle overlay. Fires
// haptics on lift and each swap, and runs an auto-scroll loop while the row is held near an edge.
fun <T> Modifier.dragHandle(
    state: DragReorderState<T>,
    itemKey: String,
    listState: LazyListState,
    onReorder: (List<T>) -> Unit,
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val autoScrollJob = remember { mutableStateOf<Job?>(null) }
    pointerInput(itemKey) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                state.startDrag(itemKey)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                autoScrollJob.value = scope.launch { autoScrollLoop(state, listState, haptic) }
            },
            onDrag = { change, dragAmount ->
                change.consume()
                if (state.updateDrag(dragAmount.y)) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onDragEnd = { autoScrollJob.value?.cancel(); state.endDrag(onReorder) },
            onDragCancel = { autoScrollJob.value?.cancel(); state.endDrag(onReorder) },
        )
    }
}

// Continuously scrolls the list while the lifted row sits within `edge` px of the top/bottom of
// the viewport — mirrors iOS's checkAndScroll 0.1s timer. Compensates the drag offset so the row
// stays glued to the finger as the content moves underneath it.
private suspend fun <T> autoScrollLoop(state: DragReorderState<T>, listState: LazyListState, haptic: HapticFeedback) {
    val edge = 120f
    val speed = 14f
    while (true) {
        val dk = state.draggingKey ?: break
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == dk }
        if (item != null) {
            val top = item.offset + state.dragOffsetY
            val bottom = top + item.size
            when {
                top < info.viewportStartOffset + edge && listState.canScrollBackward -> {
                    listState.scrollBy(-speed)
                    if (state.onAutoScroll(-speed)) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                bottom > info.viewportEndOffset - edge && listState.canScrollForward -> {
                    listState.scrollBy(speed)
                    if (state.onAutoScroll(speed)) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
        // delay is cancellable — the job is cancelled on drag end, ending the loop.
        delay(16)
    }
}
