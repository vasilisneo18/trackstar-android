package com.vasilisneo.trackstar.ui.components

// iOS-style context menu for Home cards: long-press lifts the card slightly in place and shows a
// rounded menu right under it (or above it when there's no room), with icons on the right and
// destructive actions in red. The menu stays attached to the card; touch anywhere else to dismiss.

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.vasilisneo.trackstar.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class ContextMenuAction(
    val title: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

private val MenuFill = Color(0xFF2C2C2E)
private val MenuDivider = Color.White.copy(alpha = 0.12f)
private val Destructive = Color(0xFFFF453A)

/**
 * Wraps a card with an iOS-style long-press menu. [content] receives an `openMenu` callback for
 * cards whose own tap targets swallow the long-press (e.g. the Plan / Copy tiles).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IosContextMenu(
    actions: List<ContextMenuAction>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable (openMenu: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val openMenu = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        open = true
        scope.launch { progress.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 500f)) }
        Unit
    }
    fun close(then: () -> Unit = {}) {
        scope.launch {
            progress.animateTo(0f, tween(140))
            open = false
            then()
        }
    }
    // The real card stays in the page (no copy in an overlay); it just lifts a little while open.
    Box(
        modifier = modifier
            .graphicsLayer { val s = 1f + 0.03f * progress.value; scaleX = s; scaleY = s }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = openMenu,
            )
    ) {
        content(openMenu)
        if (open) {
            val density = LocalDensity.current
            val placement = remember { mutableStateOf(MenuPlacement(below = true, alignRight = false)) }
            // Anchored to the card, so it moves with it when the page scrolls. Touching anywhere
            // else closes it (and still scrolls the page).
            Popup(
                popupPositionProvider = MenuPosition(density, placement),
                onDismissRequest = { close() },
                properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
            ) {
                val p = progress.value
                Column(
                    modifier = Modifier
                        .width(250.dp)
                        .graphicsLayer {
                            alpha = p
                            val s = 0.85f + 0.15f * p; scaleX = s; scaleY = s
                            transformOrigin = TransformOrigin(if (placement.value.alignRight) 1f else 0f,
                                if (placement.value.below) 0f else 1f)
                        }
                        .shadow(16.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(MenuFill)
                ) {
                    actions.forEachIndexed { i, action ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(MenuDivider))
                        val tint = if (action.destructive) Destructive else Color.White
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                                .clickable { close(action.onClick) }
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(action.title, fontSize = 16.sp, fontWeight = FontWeight.Normal, color = tint, modifier = Modifier.weight(1f))
                            Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
        }
    }
}

private data class MenuPlacement(val below: Boolean, val alignRight: Boolean)

/** Under the card (or above it when there's no room), lined up with its left edge — or its right
 *  edge when that would run off screen (a half tile on the right, like iOS). */
private class MenuPosition(
    private val density: androidx.compose.ui.unit.Density,
    private val placement: androidx.compose.runtime.MutableState<MenuPlacement>,
) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val gap = with(density) { 10.dp.roundToPx() }
        val edge = with(density) { 12.dp.roundToPx() }
        val bottomMargin = with(density) { 24.dp.roundToPx() }
        val below = anchorBounds.bottom + gap + popupContentSize.height < windowSize.height - bottomMargin
        val alignRight = anchorBounds.left + popupContentSize.width > windowSize.width - edge
        val y = if (below) anchorBounds.bottom + gap else anchorBounds.top - gap - popupContentSize.height
        val x = if (alignRight) (anchorBounds.right - popupContentSize.width).coerceAtLeast(edge) else anchorBounds.left
        val next = MenuPlacement(below, alignRight)
        if (placement.value != next) placement.value = next
        return IntOffset(x, y)
    }
}
