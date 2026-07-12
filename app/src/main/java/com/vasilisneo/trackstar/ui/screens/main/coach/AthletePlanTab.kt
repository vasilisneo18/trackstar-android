package com.vasilisneo.trackstar.ui.screens.main.coach

// The coach's athlete-detail Plan tab — purpose-built for the detail context (no standalone plan
// screen chrome / nav bar). Reuses the granular plan components (day-tab bar, single-session inline
// editor, session cards, week nav) + the exercise/superset editing sheets, backed by an
// athleteId-scoped WeeklyPlanViewModel so all edits write to /coach/athletes/{id}/plan. Matches
// iOS's WeeklyPlanEditorView(embeddedMode: true). Multi-session days are view/delete-only for now
// (single-session editing is the common case); "Add Session" creates an empty session inline.

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.data.api.ExerciseData
import com.vasilisneo.trackstar.data.api.PlannedSessionResponse
import com.vasilisneo.trackstar.ui.components.DragReorderState
import com.vasilisneo.trackstar.ui.components.dragHandle
import com.vasilisneo.trackstar.ui.components.dragReorderItem
import com.vasilisneo.trackstar.ui.screens.main.plan.AddSessionButton
import com.vasilisneo.trackstar.ui.screens.main.plan.CompoundExercisePairSheet
import com.vasilisneo.trackstar.ui.screens.main.plan.DayTabPill
import com.vasilisneo.trackstar.ui.screens.main.plan.ExerciseEditorSheet
import com.vasilisneo.trackstar.ui.screens.main.plan.ExercisePickerSheet
import com.vasilisneo.trackstar.ui.screens.main.plan.PlanSessionCard
import com.vasilisneo.trackstar.ui.screens.main.plan.SingleSessionInline
import com.vasilisneo.trackstar.ui.screens.main.plan.WeeklyPlanViewModel
import com.vasilisneo.trackstar.ui.screens.main.plan.WeekNavigationBar
import com.vasilisneo.trackstar.ui.screens.main.plan.formattedWeekRange
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AthletePlanTab(viewModel: WeeklyPlanViewModel, modifier: Modifier = Modifier) {
    var deletingSession by remember { mutableStateOf<PlannedSessionResponse?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<ExerciseData?>(null) }
    var editingPair by remember { mutableStateOf<Pair<ExerciseData, ExerciseData>?>(null) }
    var showNewPairSheet by remember { mutableStateOf(false) }
    var deletingExerciseId by remember { mutableStateOf<String?>(null) }
    var deletingPairIds by remember { mutableStateOf<Pair<String, String>?>(null) }

    val singleSession = viewModel.sessionsForSelectedDay.singleOrNull()

    Column(modifier = modifier.fillMaxSize()) {
        // Day-tab bar (same expand/contract pills as the weekly plan).
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 8.dp)) {
            val gap = 6.dp
            val inactiveW = 34.dp
            val activeW = (maxWidth - inactiveW * 6 - gap * 6).coerceAtLeast(72.dp)
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                viewModel.weekDays.forEach { day ->
                    DayTabPill(
                        day = day.dayOfWeek,
                        isActive = day.dayOfWeek == viewModel.selectedDay,
                        hasExercises = viewModel.hasExercises(day.dayOfWeek),
                        activeWidth = activeW,
                        inactiveWidth = inactiveW,
                        onClick = { viewModel.goToDay(day) },
                    )
                }
            }
        }

        when {
            viewModel.isLoading -> Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TrackstarAccent)
            }
            viewModel.loadError -> Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("Couldn't load this athlete's plan.", color = Color.White.copy(alpha = 0.6f))
            }
            singleSession != null -> SingleSessionInline(
                session = singleSession,
                viewModel = viewModel,
                onAddExercise = { showPicker = true },
                onAddSuperset = { showNewPairSheet = true },
                onAddSession = { viewModel.createSession() },
                onEditExercise = { editingExercise = it },
                onEditPair = { a, b -> editingPair = a to b },
                onDeleteExercise = { deletingExerciseId = it },
                onDeletePair = { a, b -> deletingPairIds = a to b },
                onCommentsTap = { /* coach comments on athlete exercises: later phase */ },
                modifier = Modifier.weight(1f),
            )
            else -> {
                val sessions = viewModel.sessionsForSelectedDay
                val dragState = remember(sessions) { DragReorderState(sessions) { it.id ?: it.hashCode().toString() } }
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (sessions.isEmpty()) {
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                            ) {
                                Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(48.dp))
                                Text("Nothing planned", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                                Text("Add a session to build this athlete's day.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.3f))
                            }
                        }
                    }
                    items(dragState.order, key = { it.id ?: it.hashCode().toString() }) { session ->
                        val key = session.id ?: session.hashCode().toString()
                        Box(modifier = Modifier.dragReorderItem(dragState, key).then(if (dragState.draggingKey == key) Modifier else Modifier.animateItem())) {
                            PlanSessionCard(
                                session = session,
                                onClick = { /* multi-session editing: later phase */ },
                                onDelete = { deletingSession = session },
                                dragHandleModifier = Modifier.dragHandle(dragState, key, listState, viewModel::reorderSessions),
                            )
                        }
                    }
                    item { AddSessionButton(onClick = { viewModel.createSession() }) }
                }
            }
        }

        WeekNavigationBar(
            weekRange = formattedWeekRange(viewModel.weekStart),
            onPrevious = viewModel::goToPreviousWeek,
            onNext = viewModel::goToNextWeek,
        )
    }

    // Editing sheets/dialogs — same wiring as WeeklyPlanScreen, routed into the single session.
    deletingSession?.let { session ->
        AlertDialog(
            onDismissRequest = { deletingSession = null },
            containerColor = Color(0xFF1A1A26),
            title = { Text("Delete Session?", color = Color.White) },
            text = { Text("\"${session.title ?: "Workout"}\" and its exercises will be removed.", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteSession(session.id ?: ""); deletingSession = null }) { Text("Delete", color = Color(0xFFFF453A)) } },
            dismissButton = { TextButton(onClick = { deletingSession = null }) { Text("Cancel", color = Color.White) } },
        )
    }

    if (showPicker) {
        ExercisePickerSheet(
            onAdd = { stubs -> singleSession?.let { viewModel.addExercisesTo(it, stubs) } },
            onDismiss = { showPicker = false },
        )
    }

    editingExercise?.let { exercise ->
        ExerciseEditorSheet(
            existing = exercise,
            onSave = { updated -> singleSession?.let { viewModel.updateExerciseIn(it, updated) }; editingExercise = null },
            onDelete = { singleSession?.let { viewModel.deleteExerciseFrom(it, exercise.id ?: "") }; editingExercise = null },
            onDismiss = { editingExercise = null },
        )
    }

    if (showNewPairSheet) {
        CompoundExercisePairSheet(
            initialExerciseA = null,
            initialExerciseB = null,
            onSave = { a, b -> singleSession?.let { viewModel.upsertPairIn(it, a, b) }; showNewPairSheet = false },
            onDismiss = { showNewPairSheet = false },
        )
    }

    editingPair?.let { (a, b) ->
        CompoundExercisePairSheet(
            initialExerciseA = a,
            initialExerciseB = b,
            onSave = { ua, ub -> singleSession?.let { viewModel.upsertPairIn(it, ua, ub) }; editingPair = null },
            onDismiss = { editingPair = null },
        )
    }

    deletingExerciseId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingExerciseId = null },
            containerColor = Color(0xFF1A1A26),
            title = { Text("Delete Exercise?", color = Color.White) },
            confirmButton = { TextButton(onClick = { singleSession?.let { viewModel.deleteExerciseFrom(it, id) }; deletingExerciseId = null }) { Text("Delete", color = Color(0xFFFF453A)) } },
            dismissButton = { TextButton(onClick = { deletingExerciseId = null }) { Text("Cancel", color = Color.White) } },
        )
    }

    deletingPairIds?.let { (aId, bId) ->
        AlertDialog(
            onDismissRequest = { deletingPairIds = null },
            containerColor = Color(0xFF1A1A26),
            title = { Text("Delete Superset?", color = Color.White) },
            confirmButton = { TextButton(onClick = { singleSession?.let { viewModel.deletePairFrom(it, aId, bId) }; deletingPairIds = null }) { Text("Delete", color = Color(0xFFFF453A)) } },
            dismissButton = { TextButton(onClick = { deletingPairIds = null }) { Text("Cancel", color = Color.White) } },
        )
    }
}
