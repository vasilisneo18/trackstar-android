package com.vasilisneo.trackstar.ui.screens.main.workout

// Full-screen Quick Log surface, mirroring iOS's QuickLogSheet.swift: log a whole planned
// session at once (no live timer). Each exercise shows its sets with a per-set Log button
// (opens LogSetSheet) or Logged/Skipped state with Undo, plus a "Log all as planned" bulk
// action. Superset pairs render as one combined round-based section (Log opens
// LogCompoundSetSheet for both halves at once). When every set is handled, a Finish button
// POSTs the session. Presented as a modal slide-up from MainAppScreen.

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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.data.api.ExerciseData
import com.vasilisneo.trackstar.data.api.ExerciseSet
import com.vasilisneo.trackstar.data.workout.ExerciseDisplayUnit
import com.vasilisneo.trackstar.data.workout.groupedForDisplay
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.TrackstarBackground
import com.vasilisneo.trackstar.ui.theme.trackstarBackground

private val CompletedGreen = Color(0xFF34C759)

@Composable
fun QuickLogSheet(viewModel: QuickLogViewModel, onClose: () -> Unit, onFinished: () -> Unit) {
    var loggingSet by remember { mutableStateOf<Pair<ExerciseData, ExerciseSet>?>(null) }
    var loggingRound by remember { mutableStateOf<Triple<ExerciseData, ExerciseData, Int>?>(null) }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Quick Log", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("${viewModel.handledSets} / ${viewModel.totalSets} sets logged", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
                }
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(44.dp))
            }

            when {
                viewModel.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TrackstarAccent)
                }
                viewModel.loadError -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Couldn't load this session.", color = Color.White.copy(alpha = 0.6f))
                }
                else -> {
                    val units = viewModel.exercises.groupedForDisplay()

                    // Progress bar (one segment per exercise/superset unit)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                        units.forEach { unit ->
                            val (handled, total) = unitHandledProgress(viewModel, unit)
                            val progress = if (total > 0) handled.toFloat() / total else 0f
                            val isDone = total > 0 && handled == total
                            Box(modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f))) {
                                Box(modifier = Modifier.fillMaxWidth(progress).fillMaxSize().background(if (isDone) CompletedGreen else TrackstarAccent, RoundedCornerShape(50)))
                            }
                        }
                    }

                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(units, key = { it.id }) { unit ->
                            when (unit) {
                                is ExerciseDisplayUnit.Single -> QuickLogExerciseSection(
                                    viewModel = viewModel,
                                    exercise = unit.exercise,
                                    onLogSet = { set -> loggingSet = unit.exercise to set },
                                )
                                is ExerciseDisplayUnit.Pair -> QuickLogCompoundSection(
                                    viewModel = viewModel,
                                    a = unit.a, b = unit.b,
                                    onLogRound = { round -> loggingRound = Triple(unit.a, unit.b, round) },
                                )
                            }
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    if (viewModel.isComplete) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .height(54.dp)
                                .clip(RoundedCornerShape(50))
                                .background(CompletedGreen.copy(alpha = 0.85f))
                                .clickable(enabled = !viewModel.isSaving) { viewModel.finish(onSaved = onFinished) }
                        ) {
                            if (viewModel.isSaving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.padding(start = 8.dp))
                                Text("Finish Session", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
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
                viewModel.logSet(set, reps, weight, durationText, distanceText)
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
                    viewModel.logSet(setA, repsA, weightA, null, null)
                    viewModel.logSet(setB, repsB, weightB, null, null)
                    loggingRound = null
                },
                onDismiss = { loggingRound = null },
            )
        }
    }
}

private fun unitHandledProgress(viewModel: QuickLogViewModel, unit: ExerciseDisplayUnit): Pair<Int, Int> = when (unit) {
    is ExerciseDisplayUnit.Single -> viewModel.exerciseProgress(unit.exercise)
    is ExerciseDisplayUnit.Pair -> {
        val (handledA, totalA) = viewModel.exerciseProgress(unit.a)
        val (handledB, totalB) = viewModel.exerciseProgress(unit.b)
        (handledA + handledB) to (totalA + totalB)
    }
}

@Composable
private fun QuickLogCompoundSection(viewModel: QuickLogViewModel, a: ExerciseData, b: ExerciseData, onLogRound: (Int) -> Unit) {
    val rounds = maxOf(a.sets.orEmpty().size, b.sets.orEmpty().size)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Link, contentDescription = null, tint = Color(0xFF64D2FF), modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.padding(start = 5.dp))
                Text("SUPERSET", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64D2FF))
            }
            Text("${a.name ?: "Exercise"} + ${b.name ?: "Exercise"}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("$rounds rounds planned", fontSize = 14.sp, color = Color.White.copy(alpha = 0.4f))
        }

        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f))) {
            for (round in 0 until rounds) {
                if (round > 0) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
                val setA = a.sets.orEmpty().getOrNull(round)
                val setB = b.sets.orEmpty().getOrNull(round)
                val loggedA = setA?.id?.let { viewModel.loggedSets.containsKey(it) } == true
                val loggedB = setB?.id?.let { viewModel.loggedSets.containsKey(it) } == true
                val skippedA = setA?.id?.let { viewModel.skippedSetIds.contains(it) } == true
                val skippedB = setB?.id?.let { viewModel.skippedSetIds.contains(it) } == true
                val isLogged = loggedA && loggedB
                val isSkipped = skippedA && skippedB
                QuickLogRoundRow(
                    round = round + 1, nameA = a.name ?: "Exercise", setA = setA, nameB = b.name ?: "Exercise", setB = setB,
                    isLogged = isLogged, isSkipped = isSkipped,
                    onLog = { onLogRound(round) },
                    onSkip = { setA?.let { viewModel.skipSet(it) }; setB?.let { viewModel.skipSet(it) } },
                    onUndo = { setA?.let { viewModel.undoSet(it) }; setB?.let { viewModel.undoSet(it) } },
                )
            }

            val isBulk = a.id?.let { viewModel.bulkLoggedExerciseIds.contains(it) } == true
            val hasUnhandled = viewModel.hasUnhandledSets(a) || viewModel.hasUnhandledSets(b)
            if (isBulk || hasUnhandled) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 16.dp))
                val label = if (isBulk) "Undo log all" else "Log all as planned"
                val icon = if (isBulk) Icons.AutoMirrored.Filled.Undo else Icons.Filled.CheckCircle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isBulk) { viewModel.undoBulkLog(a); viewModel.undoBulkLog(b) }
                            else { viewModel.logExerciseAsPlanned(a); viewModel.logExerciseAsPlanned(b) }
                        }
                        .padding(vertical = 14.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.45f))
                }
            }
        }
    }
}

@Composable
private fun QuickLogRoundRow(
    round: Int, nameA: String, setA: ExerciseSet?, nameB: String, setB: ExerciseSet?,
    isLogged: Boolean, isSkipped: Boolean, onLog: () -> Unit, onSkip: () -> Unit, onUndo: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(
                when {
                    isLogged -> CompletedGreen
                    isSkipped -> Color.White.copy(alpha = 0.08f)
                    else -> Color.White.copy(alpha = 0.1f)
                }
            ),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLogged -> Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                isSkipped -> Icon(Icons.Filled.FastForward, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(12.dp))
                else -> Text("$round", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.45f))
            }
        }
        Spacer(modifier = Modifier.padding(start = 12.dp))
        val handled = isLogged || isSkipped
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            QuickLogCompoundLine(name = nameA, set = setA, handled = handled)
            QuickLogCompoundLine(name = nameB, set = setB, handled = handled)
        }
        Spacer(modifier = Modifier.padding(start = 8.dp))
        when {
            isSkipped -> ActionChip(text = "Undo", filled = false, onClick = onUndo)
            isLogged -> ActionChip(text = "Undo", filled = false, onClick = onUndo)
            else -> ActionChip(text = "Log", filled = true, onClick = onLog)
        }
    }
}

@Composable
private fun QuickLogCompoundLine(name: String, set: ExerciseSet?, handled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64D2FF).copy(alpha = if (handled) 0.35f else 0.85f), maxLines = 1, modifier = Modifier.width(72.dp))
        if (set != null) {
            Text(set.repsOrDurationText(), fontSize = 15.sp, color = Color.White.copy(alpha = if (handled) 0.3f else 1f), modifier = Modifier.weight(1f))
            Text(set.weightText(), fontSize = 13.sp, color = Color.White.copy(alpha = if (handled) 0.3f else 0.7f))
        }
    }
}

@Composable
private fun QuickLogExerciseSection(
    viewModel: QuickLogViewModel,
    exercise: ExerciseData,
    onLogSet: (ExerciseSet) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(exercise.name ?: "Exercise", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("${exercise.sets.orEmpty().size} sets planned", fontSize = 14.sp, color = Color.White.copy(alpha = 0.4f))
        }

        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f))) {
            exercise.sets.orEmpty().forEachIndexed { index, set ->
                QuickLogSetRow(viewModel = viewModel, index = index + 1, set = set, onLog = { onLogSet(set) })
            }

            val isBulk = exercise.id?.let { viewModel.bulkLoggedExerciseIds.contains(it) } == true
            if (isBulk || viewModel.hasUnhandledSets(exercise)) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 16.dp))
                val label = if (isBulk) "Undo log all" else "Log all as planned"
                val icon = if (isBulk) Icons.AutoMirrored.Filled.Undo else Icons.Filled.CheckCircle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (isBulk) viewModel.undoBulkLog(exercise) else viewModel.logExerciseAsPlanned(exercise) }
                        .padding(vertical = 14.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.45f))
                }
            }
        }
    }
}

@Composable
private fun QuickLogSetRow(viewModel: QuickLogViewModel, index: Int, set: ExerciseSet, onLog: () -> Unit) {
    val setId = set.id
    val logged = setId?.let { viewModel.loggedSets[it] }
    val isLogged = logged != null
    val isSkipped = setId != null && viewModel.skippedSetIds.contains(setId)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        // Indicator
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(
                when {
                    isLogged -> CompletedGreen
                    isSkipped -> Color.White.copy(alpha = 0.08f)
                    else -> Color.White.copy(alpha = 0.1f)
                }
            ),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLogged -> Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                isSkipped -> Icon(Icons.Filled.FastForward, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(12.dp))
                else -> Text("$index", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.45f))
            }
        }
        Spacer(modifier = Modifier.padding(start = 12.dp))

        val handled = isLogged || isSkipped
        val repsText = logged?.let { loggedRepsText(it) } ?: set.repsOrDurationText()
        val weightText = logged?.let { loggedWeightText(it) } ?: set.weightText()
        Text(repsText, fontSize = 17.sp, color = Color.White.copy(alpha = if (handled) 0.3f else 1f), modifier = Modifier.weight(1f))
        Text(weightText, fontSize = 15.sp, color = Color.White.copy(alpha = if (handled) 0.3f else 0.7f), modifier = Modifier.padding(end = 12.dp))

        // Action
        when {
            isSkipped -> ActionChip(text = "Undo", filled = false) { viewModel.undoSet(set) }
            !isLogged -> ActionChip(text = "Log", filled = true, onClick = onLog)
            else -> ActionChip(text = "Undo", filled = false) { viewModel.undoSet(set) }
        }
    }
}

@Composable
private fun ActionChip(text: String, filled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = if (filled) FontWeight.Bold else FontWeight.SemiBold,
        color = if (filled) Color.White else Color.White.copy(alpha = 0.45f),
        modifier = Modifier
            .clip(CircleShape)
            .background(if (filled) TrackstarAccent else Color.White.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    )
}

private fun loggedRepsText(logged: LoggedSet): String {
    val f = logged.frequencyValue
    return when {
        f.reps != null -> "${f.reps}"
        f.duration != null -> f.duration
        f.distance != null -> f.distance
        else -> "—"
    }
}

private fun loggedWeightText(logged: LoggedSet): String {
    val w = logged.resistanceValue.weight
    return if (!w.isNullOrBlank()) "$w kg" else "—"
}
