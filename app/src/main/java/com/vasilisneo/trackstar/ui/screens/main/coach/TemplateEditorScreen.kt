package com.vasilisneo.trackstar.ui.screens.main.coach

// Edits a template's weekly plan — the day-tab bar + inline session/exercise editor (reused from the
// weekly plan), backed by TemplateEditorViewModel (/coach/templates/{id}/sessions). Week-agnostic, so
// there's no week-navigation bar; just Mon–Sun day tabs. Matches iOS's TemplateDetailView.

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.data.api.ExerciseData
import com.vasilisneo.trackstar.data.api.PlannedSessionResponse
import com.vasilisneo.trackstar.ui.components.DragReorderState
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.components.dragHandle
import com.vasilisneo.trackstar.ui.components.dragReorderItem
import com.vasilisneo.trackstar.ui.screens.main.plan.AddSessionButton
import com.vasilisneo.trackstar.ui.screens.main.plan.CompoundExercisePairSheet
import com.vasilisneo.trackstar.ui.screens.main.plan.DayTabPill
import com.vasilisneo.trackstar.ui.screens.main.plan.ExerciseEditorSheet
import com.vasilisneo.trackstar.ui.screens.main.plan.ExercisePickerSheet
import com.vasilisneo.trackstar.ui.screens.main.plan.PlanSessionCard
import com.vasilisneo.trackstar.ui.screens.main.plan.SingleSessionInline
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TemplateEditorScreen(templateId: String, templateName: String, onBack: () -> Unit) {
    val vm: TemplateEditorViewModel = viewModel(key = templateId) { TemplateEditorViewModel(templateId) }

    var deletingSession by remember { mutableStateOf<PlannedSessionResponse?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<ExerciseData?>(null) }
    var editingPair by remember { mutableStateOf<Pair<ExerciseData, ExerciseData>?>(null) }
    var showNewPairSheet by remember { mutableStateOf(false) }
    var deletingExerciseId by remember { mutableStateOf<String?>(null) }
    var deletingPairIds by remember { mutableStateOf<Pair<String, String>?>(null) }

    val singleSession = vm.sessionsForSelectedDay.singleOrNull()

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
                GlassCircleIconButton(onClick = onBack, contentDescription = "Back", icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft)
                Text(templateName.ifBlank { "Template" }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(44.dp))
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 8.dp)) {
                val gap = 6.dp
                val inactiveW = 34.dp
                val activeW = (maxWidth - inactiveW * 6 - gap * 6).coerceAtLeast(72.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                    vm.weekDays.forEach { day ->
                        DayTabPill(
                            day = day,
                            isActive = day == vm.selectedDay,
                            hasExercises = vm.hasExercises(day),
                            activeWidth = activeW,
                            inactiveWidth = inactiveW,
                            onClick = { vm.goToDay(day) },
                        )
                    }
                }
            }

            when {
                vm.isLoading -> Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TrackstarAccent)
                }
                singleSession != null -> SingleSessionInline(
                    session = singleSession,
                    viewModel = vm,
                    onAddExercise = { showPicker = true },
                    onAddSuperset = { showNewPairSheet = true },
                    onAddSession = { vm.createSession() },
                    onEditExercise = { editingExercise = it },
                    onEditPair = { a, b -> editingPair = a to b },
                    onDeleteExercise = { deletingExerciseId = it },
                    onDeletePair = { a, b -> deletingPairIds = a to b },
                    onCommentsTap = { },
                    showNotes = false,
                    modifier = Modifier.weight(1f),
                )
                else -> {
                    val sessions = vm.sessionsForSelectedDay
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
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 60.dp)) {
                                    Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(48.dp))
                                    Text("Nothing planned", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                                    Text("Add a session to build this day.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.3f))
                                }
                            }
                        }
                        items(dragState.order, key = { it.id ?: it.hashCode().toString() }) { session ->
                            val key = session.id ?: session.hashCode().toString()
                            Box(modifier = Modifier.dragReorderItem(dragState, key).then(if (dragState.draggingKey == key) Modifier else Modifier.animateItem())) {
                                PlanSessionCard(
                                    session = session,
                                    onClick = { },
                                    onDelete = { deletingSession = session },
                                    dragHandleModifier = Modifier.dragHandle(dragState, key, listState, vm::reorderSessions),
                                )
                            }
                        }
                        item { AddSessionButton(onClick = { vm.createSession() }) }
                    }
                }
            }
        }
    }

    deletingSession?.let { session ->
        AlertDialog(
            onDismissRequest = { deletingSession = null },
            containerColor = Color(0xFF1A1A26),
            title = { Text("Delete Session?", color = Color.White) },
            text = { Text("\"${session.title ?: "Workout"}\" and its exercises will be removed.", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = { TextButton(onClick = { vm.deleteSession(session.id ?: ""); deletingSession = null }) { Text("Delete", color = Color(0xFFFF453A)) } },
            dismissButton = { TextButton(onClick = { deletingSession = null }) { Text("Cancel", color = Color.White) } },
        )
    }

    if (showPicker) {
        ExercisePickerSheet(onAdd = { stubs -> singleSession?.let { vm.addExercisesTo(it, stubs) } }, onDismiss = { showPicker = false })
    }
    editingExercise?.let { exercise ->
        ExerciseEditorSheet(
            existing = exercise,
            onSave = { updated -> singleSession?.let { vm.updateExerciseIn(it, updated) }; editingExercise = null },
            onDelete = { singleSession?.let { vm.deleteExerciseFrom(it, exercise.id ?: "") }; editingExercise = null },
            onDismiss = { editingExercise = null },
        )
    }
    if (showNewPairSheet) {
        CompoundExercisePairSheet(
            initialExerciseA = null, initialExerciseB = null,
            onSave = { a, b -> singleSession?.let { vm.upsertPairIn(it, a, b) }; showNewPairSheet = false },
            onDismiss = { showNewPairSheet = false },
        )
    }
    editingPair?.let { (a, b) ->
        CompoundExercisePairSheet(
            initialExerciseA = a, initialExerciseB = b,
            onSave = { ua, ub -> singleSession?.let { vm.upsertPairIn(it, ua, ub) }; editingPair = null },
            onDismiss = { editingPair = null },
        )
    }
    deletingExerciseId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingExerciseId = null },
            containerColor = Color(0xFF1A1A26),
            title = { Text("Delete Exercise?", color = Color.White) },
            confirmButton = { TextButton(onClick = { singleSession?.let { vm.deleteExerciseFrom(it, id) }; deletingExerciseId = null }) { Text("Delete", color = Color(0xFFFF453A)) } },
            dismissButton = { TextButton(onClick = { deletingExerciseId = null }) { Text("Cancel", color = Color.White) } },
        )
    }
    deletingPairIds?.let { (aId, bId) ->
        AlertDialog(
            onDismissRequest = { deletingPairIds = null },
            containerColor = Color(0xFF1A1A26),
            title = { Text("Delete Superset?", color = Color.White) },
            confirmButton = { TextButton(onClick = { singleSession?.let { vm.deletePairFrom(it, aId, bId) }; deletingPairIds = null }) { Text("Delete", color = Color(0xFFFF453A)) } },
            dismissButton = { TextButton(onClick = { deletingPairIds = null }) { Text("Cancel", color = Color.White) } },
        )
    }
}
