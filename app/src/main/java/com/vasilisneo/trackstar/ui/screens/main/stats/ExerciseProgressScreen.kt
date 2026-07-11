package com.vasilisneo.trackstar.ui.screens.main.stats

// Ports iOS's ExerciseProgressView: pick an exercise, then see either an interactive progress
// line chart (max weight / volume / reps / sets / duration over a time range, with drag-to-scrub
// and a trend badge) or a per-session list of every logged set. The chart is drawn with Compose
// Canvas (no chart library). Exercise switching is via the pill picker (iOS also swipes between
// exercises — omitted here; the picker covers selection).

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.data.api.ExerciseSummary
import com.vasilisneo.trackstar.data.api.WorkoutSessionResponse
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val CardFill = Color.White.copy(alpha = 0.06f)

private enum class Metric(val short: String, val unit: String) {
    MAX_WEIGHT("Weight", "kg"), VOLUME("Volume", "kg"), REPS("Reps", "reps"), SETS("Sets", "sets"), DURATION("Duration", "min")
}

private enum class Range(val label: String, val days: Long) {
    M1("1M", 30), M2("2M", 60), M3("3M", 90), M6("6M", 180), Y1("1Y", 365)
}

private data class Point(val date: LocalDate, val value: Double)

@Composable
fun ExerciseProgressScreen(onBack: () -> Unit = {}, viewModel: StatsViewModel = viewModel()) {
    val sessions = viewModel.sessions
    val names = remember(sessions) { exerciseNames(sessions) }
    var showList by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { names.size })
    val scope = rememberCoroutineScope()
    var selected by remember(names) { mutableStateOf(names.firstOrNull() ?: "") }

    // Swiping the chart pager updates the selected exercise so the picker follows the swipe.
    LaunchedEffect(pagerState.currentPage, names) {
        names.getOrNull(pagerState.currentPage)?.let { selected = it }
    }
    // Returning to chart mode: align the pager to whatever exercise is selected.
    LaunchedEffect(showList) {
        if (!showList) {
            val idx = names.indexOf(selected).coerceAtLeast(0)
            if (idx != pagerState.currentPage) pagerState.scrollToPage(idx)
        }
    }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        if (names.isEmpty()) {
            NoSessionsCard()
        } else {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Spacer(modifier = Modifier.height(60.dp))
                ExercisePicker(names, selected, onSelect = { name ->
                    selected = name
                    if (!showList) scope.launch { pagerState.animateScrollToPage(names.indexOf(name).coerceAtLeast(0)) }
                })
                Spacer(modifier = Modifier.height(10.dp))
                if (showList) {
                    ListPage(selected, sessions)
                } else {
                    // Swipe between exercise charts (iOS pages these too). The chart's own drag-scrub
                    // consumes its gestures, so paging happens on the surrounding (non-chart) areas.
                    HorizontalPager(
                        state = pagerState,
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) { page ->
                        ChartPage(names[page], sessions)
                    }
                }
            }
        }

        // Nav bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp).height(52.dp)
        ) {
            GlassCircleIconButton(onClick = onBack, contentDescription = "Back", icon = Icons.Filled.ChevronLeft)
            Spacer(modifier = Modifier.weight(1f))
            Text("Progress", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.weight(1f))
            GlassCircleIconButton(
                onClick = { showList = !showList }, contentDescription = "Toggle view",
                icon = if (showList) Icons.Filled.ShowChart else Icons.AutoMirrored.Filled.List,
            )
        }
    }
}

// MARK: - Exercise picker

@Composable
private fun ExercisePicker(names: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        names.forEach { name ->
            val on = name == selected
            Text(
                name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (on) Color.White else Color.White.copy(alpha = 0.5f),
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (on) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.07f))
                    .clickable { onSelect(name) }.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

// MARK: - Chart page

@Composable
private fun ChartPage(exercise: String, sessions: List<WorkoutSessionResponse>) {
    val accent = TrackstarAccent
    val available = remember(exercise, sessions) { availableMetrics(exercise, sessions) }
    var range by remember(exercise) { mutableStateOf(Range.M3) }
    var metric by remember(exercise) { mutableStateOf(available.firstOrNull() ?: Metric.SETS) }
    if (metric !in available) metric = available.firstOrNull() ?: Metric.SETS
    var selectedIdx by remember(exercise, metric, range) { mutableStateOf<Int?>(null) }

    val points = remember(exercise, metric, range, sessions) { dataPoints(exercise, metric, range, sessions) }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp)
            .clip(RoundedCornerShape(20.dp)).background(CardFill)
    ) {
        RangePicker(range, onSelect = { range = it; selectedIdx = null }, modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 10.dp))
        MetricPicker(available, metric, onSelect = { metric = it; selectedIdx = null }, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp))
        ValueCallout(points, metric, selectedIdx, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp))
        if (points.size >= 2) {
            LineChart(points, accent, selectedIdx, onScrub = { selectedIdx = it }, modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 16.dp))
        } else {
            InsufficientData(exercise, modifier = Modifier.padding(20.dp))
        }
    }
}

@Composable
private fun RangePicker(selected: Range, onSelect: (Range) -> Unit, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.07f)).padding(4.dp)) {
        Range.entries.forEach { r ->
            val on = r == selected
            Text(
                r.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                color = if (on) Color.White else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) Color.White.copy(alpha = 0.2f) else Color.Transparent).clickable { onSelect(r) }.padding(vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun MetricPicker(available: List<Metric>, selected: Metric, onSelect: (Metric) -> Unit, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.horizontalScroll(rememberScrollState())) {
        available.forEach { m ->
            val on = m == selected
            Text(
                m.short, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = if (on) Color.White else Color.White.copy(alpha = 0.45f),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) TrackstarAccent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.07f)).clickable { onSelect(m) }.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ValueCallout(points: List<Point>, metric: Metric, selectedIdx: Int?, modifier: Modifier = Modifier) {
    val pinned = selectedIdx?.let { points.getOrNull(it) }
    if (pinned != null) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(pinned.date.format(dateFmt), fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(formatValue(pinned.value), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(metric.unit, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
            }
        }
    } else if (points.size >= 2) {
        val minV = points.minOf { it.value }; val maxV = points.maxOf { it.value }
        Row(verticalAlignment = Alignment.Bottom, modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${formatValue(minV)} – ${formatValue(maxV)}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(metric.unit, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.weight(1f))
            trendArrow(points)?.let { (icon, color, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
                }
            }
        }
    }
}

@Composable
private fun LineChart(points: List<Point>, accent: Color, selectedIdx: Int?, onScrub: (Int?) -> Unit, modifier: Modifier = Modifier) {
    val minV = points.minOf { it.value }
    val maxV = points.maxOf { it.value }
    val pad = maxOf((maxV - minV) * 0.25, 1.0)
    val yLo = maxOf(0.0, minV - pad); val yHi = maxV + pad
    val minDay = points.first().date.toEpochDay().toDouble()
    val maxDay = points.last().date.toEpochDay().toDouble()
    val leftGutter = 34.dp; val bottomGutter = 20.dp

    Box(modifier = modifier.fillMaxWidth().height(220.dp)) {
        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(points) {
                val lg = leftGutter.toPx()
                val plotW = size.width - lg
                fun nearestFromX(x: Float): Int {
                    val frac = ((x - lg) / plotW).coerceIn(0f, 1f)
                    val targetDay = minDay + frac * (maxDay - minDay)
                    return points.indices.minByOrNull { abs(points[it].date.toEpochDay() - targetDay) } ?: 0
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onScrub(nearestFromX(down.position.x))
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        if (abs(change.positionChange().x) > 4f) moved = true
                        onScrub(nearestFromX(change.position.x))
                        change.consume()
                    }
                    if (!moved) onScrub(null) // tap without drag clears the selection
                }
            }
        ) {
            val lg = leftGutter.toPx(); val bg = bottomGutter.toPx()
            val plotW = size.width - lg; val plotH = size.height - bg
            fun xFor(d: LocalDate): Float = lg + (if (maxDay == minDay) 0.5f else ((d.toEpochDay() - minDay) / (maxDay - minDay)).toFloat()) * plotW
            fun yFor(v: Double): Float = (plotH - ((v - yLo) / (yHi - yLo)).toFloat() * plotH)

            // Grid + y labels (4 marks)
            val gridColor = Color.White.copy(alpha = 0.08f)
            for (i in 0..3) {
                val v = yLo + (yHi - yLo) * i / 3.0
                val y = yFor(v)
                drawLine(gridColor, Offset(lg, y), Offset(size.width, y), 1f)
                drawContext.canvas.nativeCanvas.apply {
                    val p = android.graphics.Paint().apply { color = android.graphics.Color.argb(102, 255, 255, 255); textSize = 10.sp.toPx(); isAntiAlias = true }
                    drawText(formatValue(v), 0f, y + 3.dp.toPx(), p)
                }
            }
            // X labels (4)
            drawContext.canvas.nativeCanvas.apply {
                val p = android.graphics.Paint().apply { color = android.graphics.Color.argb(102, 255, 255, 255); textSize = 10.sp.toPx(); isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER }
                for (i in 0..3) {
                    val day = (minDay + (maxDay - minDay) * i / 3.0).roundToInt().toLong()
                    val d = LocalDate.ofEpochDay(day)
                    val x = xFor(d).coerceIn(lg + 12.dp.toPx(), size.width - 12.dp.toPx())
                    drawText(d.format(shortDateFmt), x, size.height - 4.dp.toPx(), p)
                }
            }

            // Area fill
            val area = Path().apply {
                moveTo(xFor(points.first().date), plotH)
                points.forEach { lineTo(xFor(it.date), yFor(it.value)) }
                lineTo(xFor(points.last().date), plotH)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = 0.4f), accent.copy(alpha = 0f)), startY = 0f, endY = plotH))

            // Line
            val line = Path().apply {
                points.forEachIndexed { i, pt -> if (i == 0) moveTo(xFor(pt.date), yFor(pt.value)) else lineTo(xFor(pt.date), yFor(pt.value)) }
            }
            drawPath(line, accent, style = Stroke(width = 2.5.dp.toPx()))

            // Selected marker
            selectedIdx?.let { idx ->
                points.getOrNull(idx)?.let { pt ->
                    val x = xFor(pt.date); val y = yFor(pt.value)
                    drawLine(Color.White.copy(alpha = 0.25f), Offset(x, 0f), Offset(x, plotH), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)))
                    drawCircle(Color.White, 5.dp.toPx(), Offset(x, y))
                    drawCircle(accent, 3.dp.toPx(), Offset(x, y))
                }
            }
        }
    }
}

@Composable
private fun InsufficientData(exercise: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier.fillMaxWidth()) {
        Icon(Icons.Filled.ShowChart, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(36.dp))
        Text("Not enough data yet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text("Log at least 2 sessions with $exercise to see your progress chart.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f), textAlign = TextAlign.Center)
    }
}

// MARK: - List page

@Composable
private fun ListPage(exercise: String, sessions: List<WorkoutSessionResponse>) {
    var range by remember(exercise) { mutableStateOf(Range.M3) }
    val entries = remember(exercise, range, sessions) { listEntries(exercise, range, sessions) }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 40.dp)
    ) {
        RangePicker(range, onSelect = { range = it })
        if (entries.isEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(36.dp))
                Text("No logged sets for $exercise yet.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.4f), textAlign = TextAlign.Center)
            }
        } else {
            entries.forEach { entry -> SessionCard(entry) }
        }
    }
}

private data class ListEntry(val date: LocalDate, val rows: List<SetRow>)
private data class SetRow(val index: Int, val weight: Double, val reps: Int?, val duration: Double?)

@Composable
private fun SessionCard(entry: ListEntry) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardFill)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(entry.date.format(dateFmt), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
            Text("${entry.rows.size} set${if (entry.rows.size == 1) "" else "s"}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.4f))
        }
        entry.rows.forEachIndexed { i, row ->
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = if (i == 0) 0.08f else 0.05f)))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text("Set ${row.index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.35f), modifier = Modifier.width(44.dp))
                SetRowDetail(row)
            }
        }
    }
}

@Composable
private fun SetRowDetail(row: SetRow) {
    if (row.reps != null) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (row.weight > 0) {
                Text(formatValue(row.weight), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("kg", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
                Text("×", fontSize = 13.sp, color = Color.White.copy(alpha = 0.3f))
                Text("${row.reps}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("reps", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
            } else {
                Text("${row.reps}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("reps · bodyweight", fontSize = 12.sp, color = Color.White.copy(alpha = 0.35f))
            }
        }
    } else if (row.duration != null) {
        val secs = row.duration.toInt(); val m = secs / 60; val s = secs % 60
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (m > 0) String.format(Locale.ENGLISH, "%d:%02d", m, s) else String.format(Locale.ENGLISH, "0:%02d", s), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text("min", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
            if (row.weight > 0) {
                Text("·", fontSize = 12.sp, color = Color.White.copy(alpha = 0.3f))
                Text("${formatValue(row.weight)} kg", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun NoSessionsCard() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize().padding(40.dp), ) {
        Spacer(modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ShowChart, contentDescription = null, tint = Color.White.copy(alpha = 0.15f), modifier = Modifier.size(52.dp))
        Text("No sessions yet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.55f))
        Text("Complete workouts to start\ntracking your exercise progress.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.3f), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.weight(1f))
    }
}

// MARK: - Data

private val dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
private val shortDateFmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

private fun exerciseNames(sessions: List<WorkoutSessionResponse>): List<String> =
    sessions.flatMap { it.sessionData?.exercises.orEmpty().map { e -> e.name } }.distinct().sorted()

private fun WorkoutSessionResponse.exerciseNamed(name: String): ExerciseSummary? =
    sessionData?.exercises?.firstOrNull { it.name == name }

private fun availableMetrics(exercise: String, sessions: List<WorkoutSessionResponse>): List<Metric> {
    var hasWeight = false; var hasReps = false; var hasDuration = false
    sessions.forEach { s ->
        s.exerciseNamed(exercise)?.sets?.forEach { set ->
            val p = set.actualPerformance ?: return@forEach
            if (set.setType == "Warm-up") return@forEach
            if ((p.resistanceValue.weight?.toDoubleOrNull() ?: 0.0) > 0) hasWeight = true
            if ((p.frequencyValue.reps ?: 0) > 0) hasReps = true
            if ((p.frequencyValue.duration?.leadingNumber() ?: 0.0) > 0) hasDuration = true
        }
    }
    return buildList {
        if (hasWeight) add(Metric.MAX_WEIGHT)
        if (hasWeight && hasReps) add(Metric.VOLUME)
        if (hasReps) add(Metric.REPS)
        add(Metric.SETS)
        if (hasDuration) add(Metric.DURATION)
    }
}

private fun dataPoints(exercise: String, metric: Metric, range: Range, sessions: List<WorkoutSessionResponse>): List<Point> {
    val cutoff = LocalDate.now().minusDays(range.days)
    return sessions.mapNotNull { s ->
        val d = s.localDate ?: return@mapNotNull null
        if (d.isBefore(cutoff)) return@mapNotNull null
        val ex = s.exerciseNamed(exercise) ?: return@mapNotNull null
        val v = compute(ex, metric)
        if (v > 0) Point(d, v) else null
    }.sortedBy { it.date }
}

private fun compute(ex: ExerciseSummary, metric: Metric): Double {
    val allCompleted = ex.sets.filter { it.completedAtSeconds != null || it.actualPerformance != null }
    if (metric == Metric.SETS) return allCompleted.size.toDouble()
    val completed = allCompleted.filter { it.setType != "Warm-up" }
    return when (metric) {
        Metric.MAX_WEIGHT -> completed.mapNotNull { it.actualPerformance?.resistanceValue?.weight?.toDoubleOrNull()?.takeIf { w -> w > 0 } }.maxOrNull() ?: 0.0
        Metric.VOLUME -> completed.sumOf { set ->
            val p = set.actualPerformance ?: return@sumOf 0.0
            val w = p.resistanceValue.weight?.toDoubleOrNull() ?: 0.0
            if (w <= 0) 0.0 else w * (p.frequencyValue.reps?.toDouble() ?: 1.0)
        }
        Metric.REPS -> completed.sumOf { (it.actualPerformance?.frequencyValue?.reps ?: 0).toDouble() }
        Metric.SETS -> completed.size.toDouble()
        Metric.DURATION -> completed.sumOf { (it.actualPerformance?.frequencyValue?.duration?.leadingNumber() ?: 0.0) / 60.0 }
    }
}

private fun listEntries(exercise: String, range: Range, sessions: List<WorkoutSessionResponse>): List<ListEntry> {
    val cutoff = LocalDate.now().minusDays(range.days)
    return sessions.mapNotNull { s ->
        val d = s.localDate ?: return@mapNotNull null
        if (d.isBefore(cutoff)) return@mapNotNull null
        val ex = s.exerciseNamed(exercise) ?: return@mapNotNull null
        val completed = ex.sets.filter { it.completedAtSeconds != null || it.actualPerformance != null }
        val rows = completed.mapIndexedNotNull { i, set ->
            val p = set.actualPerformance ?: return@mapIndexedNotNull null
            SetRow(
                index = i,
                weight = p.resistanceValue.weight?.toDoubleOrNull() ?: 0.0,
                reps = p.frequencyValue.reps,
                duration = p.frequencyValue.duration?.leadingNumber()?.takeIf { it > 0 },
            )
        }
        if (rows.isEmpty()) null else ListEntry(d, rows)
    }.sortedByDescending { it.date }
}

private fun trendArrow(points: List<Point>): Triple<ImageVector, Color, String>? {
    if (points.size < 2) return null
    val first = points.first().value
    if (first <= 0) return null
    val delta = (points.last().value - first) / first
    return when {
        delta > 0.03 -> Triple(Icons.AutoMirrored.Filled.TrendingUp, Color(0xFF34C759), "Improving")
        delta < -0.03 -> Triple(Icons.AutoMirrored.Filled.TrendingDown, Color(0xFFFF453A).copy(alpha = 0.8f), "Declining")
        else -> Triple(Icons.AutoMirrored.Filled.TrendingFlat, Color.White.copy(alpha = 0.5f), "Stable")
    }
}

private fun formatValue(v: Double): String =
    if (v % 1.0 == 0.0) String.format(Locale.ENGLISH, "%.0f", v) else String.format(Locale.ENGLISH, "%.1f", v)

private fun String.leadingNumber(): Double =
    Regex("""\d+(\.\d+)?""").find(this)?.value?.toDoubleOrNull() ?: 0.0
