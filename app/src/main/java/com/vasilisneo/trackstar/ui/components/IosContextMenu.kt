package com.vasilisneo.trackstar.ui.components

// iOS-style context menu for Home cards (mirrors SwiftUI .contextMenu): long-press lifts the card
// above a dimmed, blurred backdrop and shows a rounded menu under it (or above it when there's no
// room), with icons on the right and destructive actions in red. Tap outside to dismiss.

import android.os.Build
import android.view.WindowManager
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.vasilisneo.trackstar.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var open by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val openMenu = {
        if (bounds != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            open = true
        }
    }
    Box(
        modifier = modifier
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = openMenu,
            )
    ) { content(openMenu) }

    val b = bounds
    if (open && b != null) {
        ContextMenuOverlay(bounds = b, actions = actions, onDismiss = { open = false }) { content {} }
    }
}

@Composable
private fun ContextMenuOverlay(
    bounds: Rect,
    actions: List<ContextMenuAction>,
    onDismiss: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 500f)) }
    fun close(then: () -> Unit = {}) {
        scope.launch {
            progress.animateTo(0f, tween(140))
            onDismiss()
            then()
        }
    }

    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Our own dim (the platform one is turned off) plus a real backdrop blur on Android 12+.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(window) {
            window?.setDimAmount(0f)
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = 40 }
            }
        }
        val density = LocalDensity.current
        val screenH = LocalView.current.rootView.height.toFloat()
        val menuHeightPx = with(density) { (actions.size * 50 + 8).dp.toPx() }
        val gapPx = with(density) { 10.dp.toPx() }
        val below = bounds.bottom + gapPx + menuHeightPx < screenH - with(density) { 24.dp.toPx() }
        val menuY = if (below) bounds.bottom + gapPx else bounds.top - gapPx - menuHeightPx
        val p = progress.value

        Box(
            modifier = Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f * p))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() }
        ) {
            // The lifted card.
            Box(
                modifier = Modifier
                    .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                    .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                    .graphicsLayer { val s = 1f + 0.03f * p; scaleX = s; scaleY = s }
            ) { preview() }

            // The menu.
            Column(
                modifier = Modifier
                    .offset { IntOffset(bounds.left.roundToInt(), menuY.roundToInt()) }
                    .width(250.dp)
                    .graphicsLayer {
                        alpha = p
                        val s = 0.85f + 0.15f * p; scaleX = s; scaleY = s
                        transformOrigin = TransformOrigin(0f, if (below) 0f else 1f)
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(MenuFill.copy(alpha = 0.97f))
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
