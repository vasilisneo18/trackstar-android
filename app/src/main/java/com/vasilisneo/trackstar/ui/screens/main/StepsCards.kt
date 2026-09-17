package com.vasilisneo.trackstar.ui.screens.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.activity.compose.rememberLauncherForActivityResult
import com.vasilisneo.trackstar.data.health.HealthConnectManager
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val DailyGoal = 10_000
private val CardFill = Color.White.copy(alpha = 0.06f)

// MARK: - Daily card (Workout home) — cumulative steps through the selected day

@Composable
fun DailyStepsCard(date: LocalDate, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val health = remember { HealthConnectManager(context) }
    var granted by remember { mutableStateOf(false) }
    var steps by remember { mutableStateOf(0) }
    var hourly by remember { mutableStateOf(List(24) { 0 }) }

    val launcher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { result -> granted = result.containsAll(health.permissions) }

    LaunchedEffect(Unit) {
        if (!health.isAvailable) return@LaunchedEffect
        if (health.hasStepsPermission()) granted = true else launcher.launch(health.permissions)
    }
    LaunchedEffect(date, granted) {
        if (granted) {
            steps = health.steps(date)
            hourly = health.hourlySteps(date)
        }
    }
    // Re-query when the screen resumes (returning from another tab/app) so steps aren't stale.
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (granted) scope.launch {
            steps = health.steps(date)
            hourly = health.hourlySteps(date)
        }
    }

    if (!health.isAvailable) return

    val accent = TrackstarAccent
    val axisMax = stepsAxisMax(steps)
    val ticks = stepsTicks(steps)
    // Progress is "real" up to this hour: past days are complete, today stops at the current hour.
    val nowHour = if (date == LocalDate.now()) java.time.LocalTime.now().hour else 23
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardFill).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DirectionsWalk, null, tint = accent, modifier = Modifier.width(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("%,d".format(steps), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.width(6.dp))
            Text("steps", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
            Spacer(Modifier.weight(1f))
            Text("Goal %,d".format(DailyGoal), fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
        }

        Row {
            // Leading y-axis labels drawn at each round-thousand tick position.
            Canvas(modifier = Modifier.height(60.dp).width(28.dp)) {
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.RIGHT
                    textSize = 10.sp.toPx()
                    color = android.graphics.Color.argb((0.35f * 255).toInt(), 255, 255, 255)
                }
                ticks.forEach { t ->
                    val y = size.height * (1f - t.toFloat() / axisMax.toFloat())
                    drawContext.canvas.nativeCanvas.drawText(kLabel(t), size.width, y + paint.textSize * 0.35f, paint)
                }
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                CumulativeStepsChart(hourly = hourly, axisMax = axisMax, ticks = ticks, nowHour = nowHour, accent = accent)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    listOf("00", "06", "12", "18").forEach {
                        Text(it, fontSize = 10.sp, color = Color.White.copy(alpha = 0.35f),
                            textAlign = TextAlign.Start, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CumulativeStepsChart(hourly: List<Int>, axisMax: Int, ticks: List<Int>, nowHour: Int, accent: Color) {
    Canvas(modifier = Modifier.fillMaxWidth().height(60.dp)) {
        val n = 24
        val maxF = axisMax.toFloat().coerceAtLeast(1f)
        fun px(i: Int) = size.width * i / (n - 1)
        fun py(v: Int) = size.height * (1f - (v / maxF).coerceIn(0f, 1f))

        // Baseline + a gridline at each tick.
        drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, size.height), Offset(size.width, size.height), 1f)
        ticks.forEach { t ->
            val y = size.height * (1f - t / maxF)
            drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y), 1f)
        }

        // Cumulative running total per hour.
        var running = 0
        val pts = (0 until n).map { running += hourly[it]; Offset(px(it), py(running)) }
        val cut = nowHour.coerceIn(0, n - 1)

        // Filled area + solid line up to the current hour.
        val area = Path().apply {
            moveTo(0f, size.height)
            (0..cut).forEach { lineTo(pts[it].x, pts[it].y) }
            lineTo(pts[cut].x, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.02f))))

        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            (1..cut).forEach { lineTo(pts[it].x, pts[it].y) }
        }
        drawPath(line, accent, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        // Faded dashed line flat across the rest of the day.
        if (cut < n - 1) {
            val faded = Path().apply {
                moveTo(pts[cut].x, pts[cut].y)
                (cut + 1 until n).forEach { lineTo(pts[it].x, pts[it].y) }
            }
            drawPath(faded, accent.copy(alpha = 0.22f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
        }
    }
}

// Y-axis grows with the day's steps in round-thousand ticks: pick a round step (1k, then 2k, 5k,
// 10k… as the count climbs, keeping ≤5 ticks) and round the top up to the next multiple of it — so
// 2,300 → top 3,000 (1k/2k/3k) and 5,000 → top 6,000 (not a half-empty 10k).
private fun stepsAxisStep(steps: Int): Int =
    listOf(1000, 2000, 5000, 10000, 20000, 50000, 100000).firstOrNull { steps / it + 1 <= 5 } ?: 100000

private fun stepsAxisMax(steps: Int): Int {
    val step = stepsAxisStep(steps)
    val m = (steps / step + 1) * step
    return if (m / step < 3) 3 * step else m   // always show at least 3 ticks
}

private fun stepsTicks(steps: Int): List<Int> {
    val step = stepsAxisStep(steps)
    val max = stepsAxisMax(steps)
    return generateSequence(step) { it + step }.takeWhile { it <= max }.toList()
}

private fun kLabel(v: Int): String {
    if (v == 0) return "0"
    val k = v / 1000.0
    return if (k == kotlin.math.floor(k)) "${k.toInt()}k" else "%.1fk".format(k)
}

// MARK: - Weekly chart (Stats) — steps per day, tap a bar to inspect it

@Composable
fun WeeklyStepsCard(weekStart: LocalDate, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val health = remember { HealthConnectManager(context) }
    var granted by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf(List(7) { 0 }) }
    var selected by remember { mutableStateOf<Int?>(null) }

    val launcher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { result -> granted = result.containsAll(health.permissions) }

    LaunchedEffect(Unit) {
        if (!health.isAvailable) return@LaunchedEffect
        if (health.hasStepsPermission()) granted = true else launcher.launch(health.permissions)
    }
    LaunchedEffect(weekStart, granted) {
        if (granted) days = health.weeklySteps(weekStart)
    }
    // Re-query when the screen resumes so the week's steps aren't stale.
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (granted) scope.launch { days = health.weeklySteps(weekStart) }
    }

    if (!health.isAvailable) return

    val accent = TrackstarAccent
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH) }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardFill).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Steps this week", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.weight(1f))
            val sel = selected
            if (sel != null) {
                Text(
                    "%,d · %s".format(days[sel], weekStart.plusDays(sel.toLong()).format(dateFmt)),
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f)
                )
            } else {
                Text("%,d total".format(days.sum()), fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
            }
        }

        val maxSteps = (days.maxOrNull() ?: 0).coerceAtLeast(1)
        Canvas(
            modifier = Modifier.fillMaxWidth().height(130.dp).pointerInput(Unit) {
                detectTapGestures { offset ->
                    val idx = (offset.x / (size.width / 7f)).toInt().coerceIn(0, 6)
                    selected = if (selected == idx) null else idx
                }
            }
        ) {
            drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, 0f), Offset(size.width, 0f), 1f)
            drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, size.height), Offset(size.width, size.height), 1f)
            val slot = size.width / 7f
            val barW = 18.dp.toPx()
            days.forEachIndexed { i, count ->
                val h = (count.toFloat() / maxSteps) * size.height
                val left = i * slot + (slot - barW) / 2
                val dim = selected != null && selected != i
                drawRoundRect(
                    color = if (dim) accent.copy(alpha = 0.3f) else accent,
                    topLeft = Offset(left, size.height - h),
                    size = Size(barW, h.coerceAtLeast(0f)),
                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                )
            }
        }

        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { i ->
                Text(
                    weekStart.plusDays(i.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

