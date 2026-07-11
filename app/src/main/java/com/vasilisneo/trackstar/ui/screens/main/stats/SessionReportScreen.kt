package com.vasilisneo.trackstar.ui.screens.main.stats

// Ports iOS's SessionReportView: the full breakdown of one completed session — a "Session
// Complete" hero, duration/sets/exercises stat cards, then a card per exercise (or superset)
// listing every set's actual result vs the plan. Ad/delete paths from iOS are omitted (no ad or
// local-store infra on Android yet).

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.data.api.ActualPerformance
import com.vasilisneo.trackstar.data.api.ExerciseSummary
import com.vasilisneo.trackstar.data.api.SetResult
import com.vasilisneo.trackstar.data.api.WorkoutSessionResponse
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CardFill = Color.White.copy(alpha = 0.06f)
private val Green = Color(0xFF34C759)
private val SupersetCyan = Color(0xFF64D2FF)

@Composable
fun SessionReportScreen(session: WorkoutSessionResponse, onClose: () -> Unit) {
    val exercises = session.sessionData?.exercises.orEmpty()
    val units = remember(session) { exercises.groupedSummaries() }
    val completedAt = remember(session) {
        val epoch = session.sessionData?.completedAt?.toLong() ?: 0L
        // Guard against bad/legacy epochs (< 2020): fall back to the session's calendar date.
        if (epoch > 1_577_836_800L) {
            Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a", Locale.ENGLISH))
        } else {
            session.localDate?.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)) ?: ""
        }
    }
    val totalSets = exercises.sumOf { it.sets.size }
    val completedSets = exercises.sumOf { ex -> ex.sets.count { it.actualPerformance != null } }
    val duration = session.durationSeconds ?: session.sessionData?.durationSeconds ?: 0

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
                .padding(horizontal = 20.dp).padding(top = 52.dp, bottom = 40.dp)
        ) {
            // Hero
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Green, modifier = Modifier.size(72.dp))
                Text("Session Complete", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(completedAt, fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f))
            }

            // Stat cards
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ReportStatCard("Duration", if (duration > 0) formatTime(duration) else "—", Modifier.weight(1f))
                ReportStatCard("Sets", "$completedSets/$totalSets", Modifier.weight(1f))
                ReportStatCard("Exercises", "${exercises.size}", Modifier.weight(1f))
            }

            // Exercise breakdown
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                units.forEach { unit ->
                    when (unit) {
                        is SummaryUnit.Single -> ExerciseDetailCard(unit.ex)
                        is SummaryUnit.Pair -> CompoundDetailCard(unit.a, unit.b)
                    }
                }
            }
        }

        // Close button
        Box(modifier = Modifier.statusBarsPadding().padding(start = 20.dp, top = 16.dp)) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// MARK: - Single exercise

@Composable
private fun ExerciseDetailCard(ex: ExerciseSummary) {
    val done = ex.sets.count { it.actualPerformance != null }
    val total = ex.sets.size
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardFill)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(ex.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            Text("$done/$total sets", fontSize = 13.sp, color = if (done == total && total > 0) Green else Color.White.copy(alpha = 0.45f))
        }
        ex.sets.forEach { result ->
            Divider()
            SetRow(result)
        }
    }
}

@Composable
private fun SetRow(result: SetResult) {
    val logged = result.actualPerformance != null
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        StatusIcon(logged, Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Set ${result.index}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.4f))
            if (!logged) {
                Text("Skipped", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.25f))
            } else {
                val actual = result.actualPerformance!!.formatLabel().ifBlank { result.label }
                Text(actual, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                if (result.label.isNotBlank() && result.label != actual) {
                    Text("Planned: ${result.label}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.3f))
                }
                result.note?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f)) }
            }
        }
        if (logged) TrailingMetrics(result)
    }
}

// MARK: - Compound (superset)

@Composable
private fun CompoundDetailCard(a: ExerciseSummary, b: ExerciseSummary) {
    val rounds = maxOf(a.sets.size, b.sets.size)
    val done = a.sets.count { it.actualPerformance != null } + b.sets.count { it.actualPerformance != null }
    val total = a.sets.size + b.sets.size
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardFill).padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Link, contentDescription = null, tint = SupersetCyan, modifier = Modifier.size(11.dp))
                Text("SUPERSET", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SupersetCyan, letterSpacing = 1.sp)
            }
            Text("$done/$total sets", fontSize = 13.sp, color = if (done == total && total > 0) Green else Color.White.copy(alpha = 0.45f))
        }
        Text("${a.name} + ${b.name}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp))
        for (i in 0 until rounds) {
            Divider()
            CompoundRoundRow(i + 1, a.name, a.sets.getOrNull(i), b.name, b.sets.getOrNull(i))
        }
    }
}

@Composable
private fun CompoundRoundRow(round: Int, nameA: String, resultA: SetResult?, nameB: String, resultB: SetResult?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Set $round", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.4f))
        if (resultA != null) CompoundResultLine(nameA, resultA)
        if (resultA != null && resultB != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(9.dp))
                Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.08f)))
            }
        }
        if (resultB != null) CompoundResultLine(nameB, resultB)
    }
}

@Composable
private fun CompoundResultLine(name: String, result: SetResult) {
    val logged = result.actualPerformance != null
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatusIcon(logged, Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SupersetCyan.copy(alpha = if (logged) 0.85f else 0.4f))
            if (!logged) {
                Text("Skipped", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.25f))
            } else {
                Text(result.actualPerformance!!.formatLabel().ifBlank { result.label }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                result.note?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f)) }
            }
        }
        if (logged) TrailingMetrics(result)
    }
}

// MARK: - Shared bits

@Composable
private fun StatusIcon(logged: Boolean, modifier: Modifier = Modifier) {
    if (logged) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Green, modifier = modifier.size(16.dp))
    } else {
        Icon(Icons.Filled.SkipNext, contentDescription = null, tint = Color.White.copy(alpha = 0.25f), modifier = modifier.size(14.dp))
    }
}

@Composable
private fun TrailingMetrics(result: SetResult) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        result.durationSeconds?.let { dur ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.Filled.Timer, contentDescription = null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(10.dp))
                Text(formatTime(dur), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.55f))
            }
        }
        result.actualRestSeconds?.let { rest ->
            Text("${formatRest(rest)} rest", fontSize = 11.sp, color = Color.White.copy(alpha = 0.35f))
        }
    }
}

@Composable
private fun ReportStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(CardFill).padding(vertical = 18.dp)
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.45f))
    }
}

@Composable
private fun Divider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
}

// MARK: - Grouping + helpers

private sealed interface SummaryUnit {
    data class Single(val ex: ExerciseSummary) : SummaryUnit
    data class Pair(val a: ExerciseSummary, val b: ExerciseSummary) : SummaryUnit
}

private fun List<ExerciseSummary>.groupedSummaries(): List<SummaryUnit> {
    val out = ArrayList<SummaryUnit>()
    var i = 0
    while (i < size) {
        val cur = this[i]
        val next = getOrNull(i + 1)
        if (cur.compoundGroupId != null && next?.compoundGroupId != null && cur.compoundGroupId == next.compoundGroupId) {
            out.add(SummaryUnit.Pair(cur, next)); i += 2
        } else {
            out.add(SummaryUnit.Single(cur)); i += 1
        }
    }
    return out
}

private fun ActualPerformance.formatLabel(): String {
    val freq = frequencyValue
    val freqPart = when {
        freq.reps != null -> "${freq.reps} reps"
        !freq.duration.isNullOrBlank() -> freq.duration
        !freq.distance.isNullOrBlank() -> freq.distance
        else -> ""
    }
    val resPart = when {
        !resistanceValue.weight.isNullOrBlank() -> "@ ${resistanceValue.weight} kg"
        !resistanceValue.bandLevel.isNullOrBlank() -> "· ${resistanceValue.bandLevel}"
        else -> ""
    }
    return listOf(freqPart, resPart).filter { it.isNotBlank() }.joinToString(" ")
}

private fun formatTime(seconds: Int): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return if (h > 0) String.format(Locale.ENGLISH, "%d:%02d:%02d", h, m, s) else String.format(Locale.ENGLISH, "%d:%02d", m, s)
}

private fun formatRest(seconds: Int): String {
    val m = seconds / 60; val s = seconds % 60
    return when {
        m == 0 -> "${s}s"
        s == 0 -> "${m}m"
        else -> "${m}m ${s}s"
    }
}
