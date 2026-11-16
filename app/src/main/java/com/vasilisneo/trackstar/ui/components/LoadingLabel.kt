package com.vasilisneo.trackstar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.ui.i18n.Text

/** What a button shows while its action runs: a loading circle beside "Saving…"-style text. Every
 *  loading button uses this so they all look the same (LoadingLabel on iOS). When it appears the
 *  circle grows in from nothing, so the content widens out to both sides instead of jumping. */
@Composable
fun LoadingLabel(text: String, color: Color = Color.Black, fontSize: TextUnit = 17.sp) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) }
    val circle = if (fontSize.value < 15f) 13.dp else 18.dp
    val p = shown.value.coerceAtLeast(0f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        // The slot widens from 0; the circle keeps its size inside it and scales/fades in.
        Box(Modifier.width(circle * p).height(circle), contentAlignment = Alignment.Center) {
            LoadingCircle(color = color, size = circle,
                modifier = Modifier.requiredSize(circle).scale(0.3f + 0.7f * p).alpha(p.coerceAtMost(1f)))
        }
        Spacer(Modifier.width(10.dp * p))
        Text(text, color = color, fontSize = fontSize, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

/** The app's loading circle: a three-quarter ring that spins (LoadingCircle on iOS). */
@Composable
fun LoadingCircle(color: Color, size: Dp = 20.dp, strokeWidth: Dp = 2.dp, modifier: Modifier = Modifier) {
    val turn by rememberInfiniteTransition(label = "loading").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "turn")
    Canvas(modifier.size(size).rotate(turn)) {
        val w = strokeWidth.toPx()
        drawArc(color, startAngle = 0f, sweepAngle = 270f, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
            size = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w),
            style = Stroke(width = w, cap = StrokeCap.Round))
    }
}
