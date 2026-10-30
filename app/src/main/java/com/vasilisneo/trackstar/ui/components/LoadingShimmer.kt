package com.vasilisneo.trackstar.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * While [active], dims the content and sweeps a shimmer across it — the Home widgets render straight
 * away from their last known state and shimmer until fresh data lands (mirrors iOS loadingShimmer).
 */
fun Modifier.loadingShimmer(active: Boolean, cornerRadius: Dp = 20.dp): Modifier =
    if (!active) this else composed {
        val transition = rememberInfiniteTransition(label = "shimmer")
        val phase by transition.animateFloat(
            initialValue = -1f, targetValue = 2f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
            label = "shimmerPhase",
        )
        drawWithContent {
            drawContent()
            // Veil the stale data, then the moving highlight.
            val r = CornerRadius(cornerRadius.toPx())
            drawRoundRect(Color(0xFF1A1F35).copy(alpha = 0.55f), cornerRadius = r)
            val w = size.width
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                    start = Offset(w * phase - w * 0.5f, 0f),
                    end = Offset(w * phase + w * 0.5f, 0f),
                ),
                cornerRadius = r,
            )
        }
    }
