package com.vasilisneo.trackstar.ui.components

// Port of iOS's SwipeAction UIComponent: swipe a row left (trailing) to reveal one or more square
// action buttons. Each action reserves 80dp of slide and shows a 68×68 rounded button (corner 20)
// with an 18-ish icon over an 11sp label — matching SwipeAction.swift. Used by the template editor's
// exercise rows (Edit + Delete). Synchronous float offset via draggable (not per-delta snapTo) so a
// fast fling doesn't leave the row stuck; settles open past 40% of the reveal width, else closed.

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class SwipeActionSpec(val icon: ImageVector, val label: String, val tint: Color, val onClick: () -> Unit)

// iOS SwipeAction tints: Edit is white @ 0.15, Delete is system red @ 0.75.
val SwipeEditTint = Color.White.copy(alpha = 0.15f)
val SwipeDeleteTint = Color(0xFFFF3B30).copy(alpha = 0.75f)

@Composable
fun SwipeActionsRow(
    actions: List<SwipeActionSpec>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val slot = 80.dp
    val totalPx = with(density) { (slot * actions.size).toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val draggableState = rememberDraggableState { delta -> offsetX = (offsetX + delta).coerceIn(-totalPx, 0f) }
    fun settle(target: Float) {
        scope.launch { animate(offsetX, target, animationSpec = spring(dampingRatio = 0.85f)) { v, _ -> offsetX = v } }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (offsetX < 0f) {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.matchParentSize(),
            ) {
                actions.forEach { action ->
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.width(slot).fillMaxHeight()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(68.dp).clip(RoundedCornerShape(20.dp)).background(action.tint).clickable { settle(0f); action.onClick() },
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(action.icon, contentDescription = action.label, tint = Color.White, modifier = Modifier.size(20.dp))
                                Text(action.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { settle(if (offsetX < -totalPx * 0.4f) -totalPx else 0f) },
                )
        ) {
            content()
            // When open, a tap anywhere on the row closes it instead of hitting the card underneath.
            if (offsetX != 0f) {
                Box(
                    modifier = Modifier.matchParentSize().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { settle(0f) }
                )
            }
        }
    }
}
