package com.vasilisneo.trackstar.ui.screens.main.workout

// Full-screen active-session flow, closely mirroring iOS's ActiveSessionView.swift: single-UNIT
// focus (one exercise, or one superset pair, worked at a time — not a flat list), a segmented
// per-unit progress bar, "Up next" / "Completed" sections, a rest-timer takeover card, and a
// bottom CTA that logs the current set (or, for a superset, the current round via
// LogCompoundSetSheet). Presented as a full-screen overlay from MainAppScreen, mirroring iOS's
// fullScreenCover.

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.vasilisneo.trackstar.data.api.ExerciseData
import com.vasilisneo.trackstar.data.api.ExerciseSet
import com.vasilisneo.trackstar.data.workout.ExerciseDisplayUnit
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.TrackstarBackground
import com.vasilisneo.trackstar.ui.theme.trackstarBackground

private val CompletedGreen = Color(0xFF34C759)

@Composable
fun ActiveSessionScreen(
    viewModel: ActiveSessionViewModel,
    onMinimize: () -> Unit,
    onDiscard: () -> Unit,
    onFinish: () -> Unit,
) {
    var loggingSet by remember { mutableStateOf<Pair<ExerciseData, ExerciseSet>?>(null) }
    var loggingRound by remember { mutableStateOf<Triple<ExerciseData, ExerciseData, Int>?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(
                elapsedSeconds = viewModel.elapsedSeconds,
                onMinimize = onMinimize,
                onCancel = { showExitDialog = true },
            )

            when {
                viewModel.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TrackstarAccent)
                }
                viewModel.loadError -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Couldn't load this session.", color = Color.White.copy(alpha = 0.6f))
                }
                else -> {
                    ProgressSection(viewModel = viewModel)

                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        when (val current = viewModel.currentUnit) {
                            is ExerciseDisplayUnit.Single -> item {
                                CurrentExerciseSection(
                                    exercise = current.exercise,
                                    loggedSetIds = viewModel.loggedSets.keys,
                                    onSetClick = { set ->
                                        // Timed sets (duration) go through the duration helper
                                        // card, not the numeric LogSetSheet.
                                        if (set.frequencyValue?.duration != null) viewModel.openDurationHelper(set)
                                        else loggingSet = current.exercise to set
                                    }
                                )
                            }
                            is ExerciseDisplayUnit.Pair -> item {
                                CurrentCompoundSection(
                                    a = current.a, b = current.b,
                                    loggedSetIds = viewModel.loggedSets.keys,
                                    currentRound = viewModel.currentCompoundRoundIndex(current.a, current.b),
                                    onRoundClick = { round -> loggingRound = Triple(current.a, current.b, round) },
                                )
                            }
                            null -> Unit
                        }
                        val upNext = viewModel.upNextUnits
                        if (upNext.isNotEmpty()) {
                            item { SectionLabel("Up next") }
                            items(upNext, key = { it.id }) { unit ->
                                val (done, total) = viewModel.unitProgress(unit)
                                UpNextRow(unit = unit, done = done, total = total)
                            }
                        }
                        val completed = viewModel.completedUnits
                        if (completed.isNotEmpty()) {
                            item { SectionLabel("Completed") }
                            items(completed, key = { it.id }) { unit ->
                                CompletedRow(unit = unit)
                            }
                        }
                    }

                    BottomArea(viewModel = viewModel, onLogCurrentSet = {
                        when (val current = viewModel.currentUnit) {
                            is ExerciseDisplayUnit.Single -> {
                                val set = viewModel.currentSet
                                if (set != null) {
                                    if (set.frequencyValue?.duration != null) viewModel.openDurationHelper(set)
                                    else loggingSet = current.exercise to set
                                }
                            }
                            is ExerciseDisplayUnit.Pair -> {
                                val round = viewModel.currentCompoundRoundIndex(current.a, current.b)
                                if (round != null) loggingRound = Triple(current.a, current.b, round)
                            }
                            null -> Unit
                        }
                    }, onFinish = { viewModel.finish(onSaved = onFinish) })
                }
            }
        }
    }

    val pending = loggingSet
    if (pending != null) {
        val (exercise, set) = pending
        LogSetSheet(
            exerciseName = exercise.name ?: "Exercise",
            set = set,
            onLog = { reps, weight, durationText, distanceText ->
                viewModel.completeSet(set, reps, weight, durationText, distanceText)
                loggingSet = null
            },
            onDismiss = { loggingSet = null },
        )
    }

    val pendingRound = loggingRound
    if (pendingRound != null) {
        val (exerciseA, exerciseB, round) = pendingRound
        val setA = exerciseA.sets.orEmpty().getOrNull(round)
        val setB = exerciseB.sets.orEmpty().getOrNull(round)
        if (setA != null && setB != null) {
            LogCompoundSetSheet(
                round = round + 1,
                nameA = exerciseA.name ?: "Exercise", setA = setA,
                nameB = exerciseB.name ?: "Exercise", setB = setB,
                onLog = { repsA, weightA, repsB, weightB ->
                    viewModel.completeCompoundRound(setA, repsA, weightA, setB, repsB, weightB)
                    loggingRound = null
                },
                onDismiss = { loggingRound = null },
            )
        }
    }

    // Cancel confirmation, mirroring iOS's "Leave Session?" alert — discarding loses progress,
    // so it's gated behind a destructive confirm rather than dismissing silently.
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = Color(0xFF1A1A26),
            title = { Text("Leave Session?", color = Color.White) },
            text = { Text("Your progress will be lost if you discard.", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false; onDiscard() }) {
                    Text("Discard Session", color = Color(0xFFFF453A))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Keep Running", color = Color.White)
                }
            },
        )
    }
}

@Composable
private fun Header(elapsedSeconds: Int, onMinimize: () -> Unit, onCancel: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        GlassCircleButton(icon = Icons.Filled.KeyboardArrowDown, contentDescription = "Minimize", onClick = onMinimize)
        Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(modifier = Modifier.size(7.dp).background(CompletedGreen, CircleShape))
                Text("RECORDING", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, color = Color.White.copy(alpha = 0.6f))
            }
            Text(
                formatElapsed(elapsedSeconds),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        GlassCircleButton(icon = Icons.Filled.Close, contentDescription = "Discard", onClick = onCancel)
    }
}

@Composable
private fun GlassCircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit) {
    // 44dp circle + 18dp glyph to match iOS's .glassCircle() (44pt frame, 16pt bold icon).
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ProgressSection(viewModel: ActiveSessionViewModel) {
    val units = viewModel.units
    val currentIndex = viewModel.currentUnitIndex
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            units.forEach { unit ->
                val (done, total) = viewModel.unitProgress(unit)
                val progress = if (total > 0) done.toFloat() / total else 0f
                val isDone = total > 0 && done == total
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.12f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxSize()
                            .background(if (isDone) CompletedGreen else TrackstarAccent, RoundedCornerShape(50))
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Exercise ${(currentIndex ?: (units.size - 1)) + 1} of ${units.size}",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text("${viewModel.completedSets} / ${viewModel.totalSets} sets logged", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun CurrentExerciseSection(exercise: ExerciseData, loggedSetIds: Set<String>, onSetClick: (ExerciseSet) -> Unit) {
    val isDuration = exercise.sets?.firstOrNull()?.frequencyValue?.duration != null
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(exercise.name ?: "Exercise", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("${exercise.sets.orEmpty().size} sets planned", fontSize = 14.sp, color = Color.White.copy(alpha = 0.4f))
        }

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f))
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("SET", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.3f), modifier = Modifier.padding(end = 24.dp))
                Text(if (isDuration) "DURATION" else "REPS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.3f), modifier = Modifier.weight(1f))
                Text("WEIGHT", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.3f))
            }
            exercise.sets.orEmpty().forEachIndexed { index, set ->
                SetRow(index = index + 1, set = set, isLogged = loggedSetIds.contains(set.id), onClick = { onSetClick(set) })
            }
        }
    }
}

// Superset focus — mirrors currentCompoundSection on iOS: a "SUPERSET" label, both names
// joined with " + ", and one row per round showing both exercises' rep/weight stacked with an
// arrow divider between them (instead of one line per set like the single-exercise table).
@Composable
private fun CurrentCompoundSection(a: ExerciseData, b: ExerciseData, loggedSetIds: Set<String>, currentRound: Int?, onRoundClick: (Int) -> Unit) {
    val rounds = maxOf(a.sets.orEmpty().size, b.sets.orEmpty().size)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Link, contentDescription = null, tint = Color(0xFF64D2FF), modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.padding(start = 5.dp))
                Text("SUPERSET", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = Color(0xFF64D2FF))
            }
            Text("${a.name ?: "Exercise"} + ${b.name ?: "Exercise"}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("$rounds rounds planned", fontSize = 14.sp, color = Color.White.copy(alpha = 0.4f))
        }

        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f))) {
            for (round in 0 until rounds) {
                if (round > 0) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
                CompoundRoundRow(
                    round = round + 1,
                    nameA = a.name ?: "Exercise", setA = a.sets.orEmpty().getOrNull(round),
                    nameB = b.name ?: "Exercise", setB = b.sets.orEmpty().getOrNull(round),
                    isLogged = a.sets.orEmpty().getOrNull(round)?.id?.let { loggedSetIds.contains(it) } == true &&
                        b.sets.orEmpty().getOrNull(round)?.id?.let { loggedSetIds.contains(it) } == true,
                    onClick = { onRoundClick(round) },
                )
            }
        }
    }
}

@Composable
private fun CompoundRoundRow(round: Int, nameA: String, setA: ExerciseSet?, nameB: String, setB: ExerciseSet?, isLogged: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isLogged, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(if (isLogged) CompletedGreen else Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (isLogged) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            else Text("$round", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.45f))
        }
        Spacer(modifier = Modifier.padding(start = 12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CompoundLine(name = nameA, set = setA, isLogged = isLogged)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(11.dp))
                Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.08f)))
            }
            CompoundLine(name = nameB, set = setB, isLogged = isLogged)
        }
    }
}

@Composable
private fun CompoundLine(name: String, set: ExerciseSet?, isLogged: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64D2FF).copy(alpha = if (isLogged) 0.35f else 0.85f),
            maxLines = 1, modifier = Modifier.width(80.dp)
        )
        if (set != null) {
            Text(set.repsOrDurationText(), fontSize = 15.sp, color = Color.White.copy(alpha = if (isLogged) 0.3f else 1f), modifier = Modifier.weight(1f))
            Text(set.weightText(), fontSize = 15.sp, color = Color.White.copy(alpha = if (isLogged) 0.3f else 1f))
        }
    }
}

@Composable
private fun SetRow(index: Int, set: ExerciseSet, isLogged: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLogged, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(if (isLogged) CompletedGreen else Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (isLogged) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            } else {
                Text("$index", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.45f))
            }
        }
        Spacer(modifier = Modifier.padding(start = 12.dp))
        Text(
            set.repsOrDurationText(),
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White.copy(alpha = if (isLogged) 0.3f else 1f),
            modifier = Modifier.weight(1f)
        )
        Text(
            set.weightText(),
            fontSize = 17.sp,
            color = Color.White.copy(alpha = if (isLogged) 0.3f else 1f)
        )
        if (!isLogged) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(16.dp).padding(start = 8.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.45f), modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun UpNextRow(unit: ExerciseDisplayUnit, done: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.07f)), contentAlignment = Alignment.Center) {
            Text("$done/$total", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.4f))
        }
        Spacer(modifier = Modifier.padding(start = 12.dp))
        Column {
            Text(unitName(unit), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.65f))
            Text(unitSummary(unit), fontSize = 12.sp, color = Color.White.copy(alpha = 0.3f))
        }
    }
}

@Composable
private fun CompletedRow(unit: ExerciseDisplayUnit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(CompletedGreen.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = CompletedGreen, modifier = Modifier.size(14.dp))
        }
        Spacer(modifier = Modifier.padding(start = 12.dp))
        Column {
            Text(unitName(unit), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.35f))
            Text(unitSummary(unit), fontSize = 12.sp, color = Color.White.copy(alpha = 0.2f))
        }
    }
}

private fun unitName(unit: ExerciseDisplayUnit): String = when (unit) {
    is ExerciseDisplayUnit.Single -> unit.exercise.name ?: "Exercise"
    is ExerciseDisplayUnit.Pair -> "${unit.a.name ?: "Exercise"} + ${unit.b.name ?: "Exercise"}"
}

private fun unitSummary(unit: ExerciseDisplayUnit): String = when (unit) {
    is ExerciseDisplayUnit.Single -> exerciseSummary(unit.exercise)
    is ExerciseDisplayUnit.Pair -> "${unit.a.sets.orEmpty().size} rounds · Superset"
}

private fun exerciseSummary(exercise: ExerciseData): String {
    val first = exercise.sets.orEmpty().firstOrNull() ?: return "${exercise.sets.orEmpty().size} sets"
    val r = first.repsOrDurationText()
    val w = first.weightText()
    return if (w != "—" && w.isNotBlank()) "${exercise.sets.orEmpty().size} × $r · $w" else "${exercise.sets.orEmpty().size} × $r"
}

@Composable
private fun BottomArea(viewModel: ActiveSessionViewModel, onLogCurrentSet: () -> Unit, onFinish: () -> Unit) {
    when {
        viewModel.durationSet != null -> DurationHelperCard(viewModel = viewModel)
        viewModel.isResting -> RestTimerCard(viewModel = viewModel)
        viewModel.isSessionComplete -> FinishButton(isSaving = viewModel.isSaving, onFinish = onFinish)
        else -> BottomCTA(viewModel = viewModel, onLogCurrentSet = onLogCurrentSet)
    }
}

// Duration timed-set takeover card, mirroring iOS's durationHelperCard four phases:
// Ready (target + delay picker + Start) → Get-ready countdown → Running (elapsed + Stop & Log)
// → Time's up (Log Set).
@Composable
private fun DurationHelperCard(viewModel: ActiveSessionViewModel) {
    val set = viewModel.durationSet ?: return
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 15.dp)
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 32.dp)
    ) {
        when {
            viewModel.durationDone -> {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = CompletedGreen, modifier = Modifier.size(44.dp))
                Text("Time's up!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    formatElapsed(viewModel.durationElapsedSeconds),
                    fontSize = 36.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.7f)
                )
                DurationPillButton("Log Set", CompletedGreen.copy(alpha = 0.85f)) { viewModel.logDuration() }
            }
            viewModel.durationCountdown != null -> {
                Text("Get ready…", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
                Text(
                    "${viewModel.durationCountdown}",
                    fontSize = 64.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFF9F0A)
                )
                CancelText { viewModel.cancelDurationHelper() }
            }
            viewModel.durationRunning -> {
                Text("RUNNING", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.45f))
                Text(
                    formatElapsed(viewModel.durationElapsedSeconds),
                    fontSize = 52.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White
                )
                DurationPillButton("Stop & Log", Color(0xFFFF453A).copy(alpha = 0.85f)) { viewModel.stopDurationAndLog() }
            }
            else -> {
                Text("DURATION", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.45f))
                Text(
                    set.repsOrDurationText(),
                    fontSize = 52.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White
                )
                // Start-delay picker (3 / 5 / 10 s)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.07f)).padding(3.dp)
                ) {
                    listOf(3, 5, 10).forEach { seconds ->
                        val selected = viewModel.startDelay == seconds
                        Text(
                            "${seconds}s",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                                .clickable { viewModel.chooseStartDelay(seconds) }
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }
                DurationPillButton("Start in ${viewModel.startDelay}s", TrackstarAccent) { viewModel.beginDurationCountdown() }
                CancelText { viewModel.cancelDurationHelper() }
            }
        }
    }
}

@Composable
private fun DurationPillButton(label: String, color: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(color)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun CancelText(onClick: () -> Unit) {
    Text(
        "Cancel",
        fontSize = 15.sp, color = Color.White.copy(alpha = 0.45f),
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp)
    )
}

@Composable
private fun BottomCTA(viewModel: ActiveSessionViewModel, onLogCurrentSet: () -> Unit) {
    val currentUnit = viewModel.currentUnit
    val set = viewModel.currentSet
    val isDuration = set?.frequencyValue?.duration != null
    val label = when (currentUnit) {
        is ExerciseDisplayUnit.Single -> {
            if (set != null) {
                val r = set.repsOrDurationText()
                val w = set.weightText()
                if (w == "—" || w.isBlank()) "Log set · $r" else "Log set · $w × $r"
            } else "Log set"
        }
        is ExerciseDisplayUnit.Pair -> {
            val round = viewModel.currentCompoundRoundIndex(currentUnit.a, currentUnit.b)
            if (round != null) "Log Round ${round + 1}" else "Log set"
        }
        null -> "Log set"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 54.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(if (isDuration && set != null) Modifier.clickable { viewModel.openDurationHelper(set) } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Timer, contentDescription = null, tint = if (isDuration) Color.White else Color.White.copy(alpha = 0.2f), modifier = Modifier.size(20.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .clip(RoundedCornerShape(50))
                .background(TrackstarAccent)
                .clickable(onClick = onLogCurrentSet)
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.padding(start = 6.dp))
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

@Composable
private fun FinishButton(isSaving: Boolean, onFinish: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(50))
            .background(CompletedGreen.copy(alpha = 0.85f))
            .clickable(enabled = !isSaving, onClick = onFinish)
    ) {
        if (isSaving) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
        } else {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.padding(start = 8.dp))
            Text("Finish Session", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

@Composable
private fun RestTimerCard(viewModel: ActiveSessionViewModel) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 15.dp)
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(top = 24.dp, bottom = 32.dp)
    ) {
        Text("Rest", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
        Text(
            formatElapsed(viewModel.restSecondsRemaining),
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Skip", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable(onClick = viewModel::skipRest)
                    .padding(horizontal = 28.dp, vertical = 12.dp)
            )
            Text(
                "+1 min", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.2f))
                    .clickable(onClick = viewModel::addMinuteToRest)
                    .padding(horizontal = 28.dp, vertical = 12.dp)
            )
        }
    }
}

private fun formatElapsed(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

// Spotify-style persistent bar shown just above the tab bar while a session is active but
// minimized — mirrors iOS's activeSessionMiniBar: edge-to-edge, flat (no rounded corners), with
// a hairline top divider (approximating .ultraThinMaterial). Transparent itself — the continuous
// surface behind it (and behind the tab bar, down to the bottom edge) is drawn by the caller so
// the whole bottom region reads as one material, like iOS.
@Composable
fun ActiveSessionMiniBar(viewModel: ActiveSessionViewModel, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand)
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp)
        ) {
            Box(modifier = Modifier.size(9.dp).background(CompletedGreen, CircleShape))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    viewModel.title.ifBlank { "Active Session" },
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1
                )
                Text("${viewModel.completedSets}/${viewModel.totalSets} sets", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
            }
            Text(
                formatElapsed(viewModel.elapsedSeconds),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.6f)
            )
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Expand", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
        }
    }
}
