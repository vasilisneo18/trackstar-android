package com.vasilisneo.trackstar.ui.screens.main.stats

// Ports iOS's WorkoutStatsView: a collapsing-header stats dashboard computed entirely from the
// completed-session list — summary counts, a completion-rate ring, week streak, a weekly-volume
// bar chart, personal records, and a link into the progress chart. Subscription gating (iOS's
// locked "Silver" cards) is omitted: there's no subscription system on Android yet, so every
// section is shown unlocked.

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.components.ProfileAvatarButton
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CardFill = Color.White.copy(alpha = 0.06f)

@Composable
fun StatsScreen(
    onProfileClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onOpenProgress: () -> Unit = {},
    viewModel: StatsViewModel = viewModel(),
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val thresholdPx = with(LocalDensity.current) { 60.dp.toPx() }
    val collapse by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / thresholdPx).coerceIn(0f, 1f)
        }
    }
    val hazeState = remember { HazeState() }
    val headerAlpha = (collapse * 2.5f).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        // Faint brand wordmark pinned to the bottom (iOS's Trackstar background text).
        Text(
            "Trackstar",
            fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.08f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)
        )

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 68.dp, bottom = com.vasilisneo.trackstar.ui.components.tabBarContentBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().statusBarsPadding().haze(hazeState),
        ) {
            item {
                Text(
                    "Stats", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .alpha((1f - collapse).coerceIn(0f, 1f))
                )
            }
            item { SummaryRow(viewModel.allTimeCount, viewModel.thisMonthCount, viewModel.thisWeekCount) }
            item { CompletionCard(viewModel.completionRate) }
            if (viewModel.streak > 0) item { StreakCard(viewModel.streak) }
            item { VolumeCard(viewModel.weeklyVolumes) }
            if (viewModel.personalRecords.isNotEmpty()) item { PrsCard(viewModel) }
            item { ProgressButton(onClick = onOpenProgress) }
        }

        // Collapsing nav bar overlay (profile · "Stats" fades in · history clock). Frosted-glass
        // tint ramping in on scroll — same haze + colour as the workout tab's header.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hazeChild(
                    state = hazeState,
                    style = HazeStyle(
                        backgroundColor = Color.Transparent,
                        tint = HazeTint(Color(0xFF3B3B46).copy(alpha = 0.82f * headerAlpha)),
                        blurRadius = 40.dp,
                    ),
                )
                .statusBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)
            ) {
                ProfileAvatarButton(initials = viewModel.userInitials, onClick = onProfileClick)
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    "Stats", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    modifier = Modifier.alpha(collapse.coerceIn(0f, 1f))
                )
                Spacer(modifier = Modifier.weight(1f))
                GlassCircleIconButton(onClick = onHistoryClick, contentDescription = "History", icon = Icons.Filled.Schedule)
            }
            // Breathing room below the buttons so scrolled content doesn't sit flush against them.
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

// MARK: - Summary row

@Composable
private fun SummaryRow(allTime: Int, thisMonth: Int, thisWeek: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "SESSIONS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.4f),
            letterSpacing = 1.5.sp, modifier = Modifier.padding(horizontal = 4.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("$allTime", "All time", Modifier.weight(1f))
            StatCard("$thisMonth", "This month", Modifier.weight(1f))
            StatCard("$thisWeek", "This week", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(CardFill).padding(vertical = 18.dp)
    ) {
        Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.5f))
    }
}

// MARK: - Completion rate ring

@Composable
private fun CompletionCard(rate: Double) {
    val accent = TrackstarAccent
    val animated by animateFloatAsState(targetValue = (rate / 100.0).toFloat().coerceIn(0f, 1f), animationSpec = tween(700), label = "ring")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardFill).padding(16.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(60.dp)) {
            Canvas(modifier = Modifier.size(60.dp)) {
                val stroke = 7.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = Color.White.copy(alpha = 0.08f), startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke)
                )
                drawArc(
                    color = accent, startAngle = -90f, sweepAngle = animated * 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Text("${rate.toInt()}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Completion Rate", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Sets completed vs planned", fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f))
        }
    }
}

// MARK: - Streak

@Composable
private fun StreakCard(streak: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardFill).padding(16.dp)
    ) {
        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = Color(0xFFFF9500), modifier = Modifier.size(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$streak week${if (streak == 1) "" else "s"} in a row", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Keep it up — consistency is everything", fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f))
        }
    }
}

// MARK: - Weekly volume bar chart

@Composable
private fun VolumeCard(weeks: List<StatsViewModel.WeeklyVolume>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardFill).padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
            Text("VOLUME", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.4f), letterSpacing = 1.5.sp)
            Text("Weekly total in tonnes", fontSize = 12.sp, color = Color.White.copy(alpha = 0.35f))
        }
        if (weeks.any { it.tonnes > 0 }) {
            VolumeBarChart(weeks)
        } else {
            Text(
                "Log sessions to see your volume trend", fontSize = 13.sp, color = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
            )
        }
    }
}

@Composable
private fun VolumeBarChart(weeks: List<StatsViewModel.WeeklyVolume>) {
    val accent = TrackstarAccent
    val maxVol = (weeks.maxOfOrNull { it.tonnes } ?: 0.0).coerceAtLeast(0.001)
    val labelFmt = remember { DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Max-value gridline label (top-left), like iOS's leading y-axis marks.
        Text(String.format(Locale.ENGLISH, "%.1f t", maxVol), fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
        Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
            val n = weeks.size
            if (n == 0) return@Canvas
            // Faint baseline + top gridline.
            drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, 0f), Offset(size.width, 0f), 1f)
            drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, size.height), Offset(size.width, size.height), 1f)
            val slot = size.width / n
            val barW = slot * 0.5f
            weeks.forEachIndexed { i, w ->
                val h = ((w.tonnes / maxVol).toFloat()) * size.height
                if (h <= 0f) return@forEachIndexed
                val left = i * slot + (slot - barW) / 2
                drawRoundRect(
                    color = accent.copy(alpha = 0.8f),
                    topLeft = Offset(left, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                )
            }
        }
        // X-axis labels — every other week to avoid crowding.
        Row(modifier = Modifier.fillMaxWidth()) {
            weeks.forEachIndexed { i, w ->
                Text(
                    if (i % 2 == 0) w.weekStart.format(labelFmt) else "",
                    fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f), textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// MARK: - Personal records

@Composable
private fun PrsCard(viewModel: StatsViewModel) {
    val accent = TrackstarAccent
    val metrics = viewModel.availablePRMetrics
    var selected by remember(metrics) { mutableStateOf(metrics.firstOrNull() ?: StatsViewModel.PRMetric.WEIGHT) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    val rows = viewModel.filteredPRs(selected).take(8)

    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardFill)) {
        Text(
            "Personal Records", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            metrics.forEach { m ->
                val on = m == selected
                Text(
                    m.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = if (on) Color.White else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (on) accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.07f))
                        .clickable { selected = m }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
        rows.forEachIndexed { index, pr ->
            if (index > 0) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 16.dp))
            PrRow(pr, selected, expanded = expandedId == pr.name, onToggle = { expandedId = if (expandedId == pr.name) null else pr.name })
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun PrRow(pr: StatsViewModel.PREntry, metric: StatsViewModel.PRMetric, expanded: Boolean, onToggle: () -> Unit) {
    val date = when (metric) {
        StatsViewModel.PRMetric.WEIGHT -> pr.weightDate
        StatsViewModel.PRMetric.REPS -> pr.repsDate
        StatsViewModel.PRMetric.DURATION -> pr.durationDate
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onToggle
        ).padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(pr.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1)
            if (expanded && date != null) {
                Text("Set on ${date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            when (metric) {
                StatsViewModel.PRMetric.WEIGHT -> {
                    PrBadge(formatWeight(pr.maxWeight), "kg", primary = true)
                    if (pr.repsAtMaxWeight > 0) PrBadge("${pr.repsAtMaxWeight}", "reps", primary = false)
                }
                StatsViewModel.PRMetric.REPS -> {
                    PrBadge("${pr.maxReps}", "reps", primary = true)
                    if (pr.weightAtMaxReps > 0) PrBadge(formatWeight(pr.weightAtMaxReps), "kg", primary = false)
                }
                StatsViewModel.PRMetric.DURATION -> {
                    PrBadge(formatDurationMin(pr.maxDuration), "min", primary = true)
                    if (pr.weightAtMaxDuration > 0) PrBadge(formatWeight(pr.weightAtMaxDuration), "kg", primary = false)
                }
            }
        }
    }
}

@Composable
private fun PrBadge(value: String, unit: String, primary: Boolean) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (primary) TrackstarAccent else Color.White)
        Text(unit, fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
    }
}

// MARK: - Progress chart button

@Composable
private fun ProgressButton(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardFill).clickable(onClick = onClick).padding(16.dp)
    ) {
        Icon(Icons.Filled.ShowChart, contentDescription = null, tint = TrackstarAccent, modifier = Modifier.size(18.dp))
        Text("View Progress Chart", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
    }
}

// MARK: - Helpers

private fun formatWeight(v: Double): String =
    if (v % 1.0 == 0.0) String.format(Locale.ENGLISH, "%.0f", v) else String.format(Locale.ENGLISH, "%.1f", v)

private fun formatDurationMin(minutes: Double): String {
    val total = (minutes * 60).toInt()
    val m = total / 60; val s = total % 60
    return if (m > 0) String.format(Locale.ENGLISH, "%d:%02d", m, s) else String.format(Locale.ENGLISH, "0:%02d", s)
}
