package com.vasilisneo.trackstar.ui.screens.main.plan

// Weekly plan editor: day tabs + the selected day's sessions. Mirrors iOS's WeeklyPlanView,
// which renders a day's single session two different ways:
//   • Exactly one session  -> edited INLINE here (title field, exercise/superset rows, and
//     Add Exercise / Add Superset / Add Session buttons) via WeeklyPlanViewModel's immediate-
//     persist methods, matching WeeklyPlanView.singleSessionInlineView.
//   • Zero or 2+ sessions   -> compact tappable cards; tapping one (or "Add Session") pushes
//     SessionEditScreen, the full-screen session/exercise editor.
// Week/day navigation and session deletion live here in both modes.

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.data.api.ExerciseData
import com.vasilisneo.trackstar.data.api.PlannedSessionResponse
import com.vasilisneo.trackstar.data.workout.ExerciseDisplayUnit
import com.vasilisneo.trackstar.data.workout.groupedForDisplay
import com.vasilisneo.trackstar.ui.components.DragReorderState
import com.vasilisneo.trackstar.ui.components.SwipeActionSpec
import com.vasilisneo.trackstar.ui.components.SwipeActionsRow
import com.vasilisneo.trackstar.ui.components.SwipeDeleteTint
import com.vasilisneo.trackstar.ui.components.SwipeEditTint
import com.vasilisneo.trackstar.ui.components.dragHandle
import com.vasilisneo.trackstar.ui.components.dragReorderItem
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.TrackstarBackground
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WeeklyPlanScreen(
    onBackClick: () -> Unit,
    onUpgrade: () -> Unit = {},
    onOpenSession: (weekIdentifier: String, day: String, sessionId: String?) -> Unit,
) {
    val viewModel: WeeklyPlanViewModel = viewModel()
    // AI workout planner is Silver+ (FeatureGate.canUseAI). The ✨ button opens the paywall on a
    // lower tier instead of the planner.
    val plan by com.vasilisneo.trackstar.data.billing.BillingManager.currentPlan.collectAsState()
    val canUseAI = com.vasilisneo.trackstar.data.billing.FeatureGate.canUseAI(plan)
    var deletingSession by remember { mutableStateOf<PlannedSessionResponse?>(null) }
    var aiPlannerViewModel by remember { mutableStateOf<AIWorkoutPlannerViewModel?>(null) }

    // Sheets/dialogs for inline single-session editing (only reachable when the day holds
    // exactly one session — see the loaded `else` branch).
    var showPicker by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<ExerciseData?>(null) }
    var editingPair by remember { mutableStateOf<Pair<ExerciseData, ExerciseData>?>(null) }
    var showNewPairSheet by remember { mutableStateOf(false) }
    var deletingExerciseId by remember { mutableStateOf<String?>(null) }
    var deletingPairIds by remember { mutableStateOf<Pair<String, String>?>(null) }
    var commentingExercise by remember { mutableStateOf<ExerciseData?>(null) }

    // The single inline-edited session, if this day has exactly one — the sheet callbacks below
    // route their edits into it. Null in the empty/multi-session card modes.
    val singleSession = viewModel.sessionsForSelectedDay.singleOrNull()

    // Re-fetch when returning to this screen (e.g. after pushing SessionEditScreen to add or edit
    // a session), so a newly added session flips the inline view over to cards and edits show.
    // Skips the very first resume since init already fetched.
    var skipFirstResume by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        if (skipFirstResume) skipFirstResume = false else viewModel.fetch()
        onPauseOrDispose { }
    }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar — mirrors WeeklyPlanView's safeAreaInset(edge: .top): back (glassCircle) +
            // centered "Plan Your Week" title + AI sparkles (glassCircle). Week navigation lives
            // in the bottom bar, not here.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                CircleIconButton(icon = Icons.Filled.ChevronLeft, contentDescription = "Back", onClick = onBackClick)
                Spacer(modifier = Modifier.weight(1f))
                Text("Plan Your Week", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
                CircleIconButton(
                    icon = Icons.Filled.AutoAwesome, contentDescription = "AI Workout Planner",
                    onClick = {
                        if (canUseAI) aiPlannerViewModel = AIWorkoutPlannerViewModel(viewModel.weekIdentifier)
                        else onUpgrade()
                    },
                )
            }

            // Day tabs — mirrors WeeklyPlanTabBar.swift exactly: the active pill expands to fill
            // remaining width and shows the full day name on a solid per-day accent color;
            // inactive pills are a fixed 30dp-wide single-letter chip on translucent white, with
            // a small colored dot if that day has any exercises planned.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 8.dp)) {
                val gap = 6.dp
                // iOS letter chips are 30pt; a touch wider (34dp) reads closer at Android densities.
                val inactiveW = 34.dp
                // 7 pills + 6 gaps; the one active pill fills whatever's left. Each pill animates
                // its own width, so when the active day changes the closing pill contracts to a
                // letter chip while the opening one expands — the freed space transfers across
                // (both animate in lock-step), mirroring WeeklyPlanTabBar's expand/collapse.
                val activeW = (maxWidth - inactiveW * 6 - gap * 6).coerceAtLeast(72.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
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
                viewModel.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TrackstarAccent)
                }
                viewModel.loadError -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Couldn't load this week's plan.", color = Color.White.copy(alpha = 0.6f))
                }
                singleSession != null -> {
                    // Exactly one session — edit it inline (iOS's singleSessionInlineView).
                    SingleSessionInline(
                        session = singleSession,
                        viewModel = viewModel,
                        onAddExercise = { showPicker = true },
                        onAddSuperset = { showNewPairSheet = true },
                        onAddSession = { onOpenSession(viewModel.weekIdentifier, viewModel.selectedDayName, null) },
                        onEditExercise = { editingExercise = it },
                        onEditPair = { a, b -> editingPair = a to b },
                        onDeleteExercise = { deletingExerciseId = it },
                        onDeletePair = { a, b -> deletingPairIds = a to b },
                        onCommentsTap = { commentingExercise = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                else -> {
                    // Zero or 2+ sessions — compact cards; each pushes SessionEditScreen.
                    val sessions = viewModel.sessionsForSelectedDay
                    val dragState = remember(sessions) { DragReorderState(sessions) { it.id ?: it.hashCode().toString() } }
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (sessions.isEmpty()) {
                            item {
                                // Mirrors WeeklyPlanView's empty state: dumbbell icon + "Nothing
                                // planned" + "Add an exercise to get started."
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 60.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.FitnessCenter, contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(48.dp)
                                    )
                                    Text("Nothing planned", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                                    Text(
                                        "Add an exercise to get started.",
                                        fontSize = 14.sp, color = Color.White.copy(alpha = 0.3f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
                        }
                        items(dragState.order, key = { it.id ?: it.hashCode().toString() }) { session ->
                            val sessionKey = session.id ?: session.hashCode().toString()
                            Box(modifier = Modifier.dragReorderItem(dragState, sessionKey).then(if (dragState.draggingKey == sessionKey) Modifier else Modifier.animateItem())) {
                                PlanSessionCard(
                                    session = session,
                                    onClick = { onOpenSession(viewModel.weekIdentifier, viewModel.selectedDayName, session.id) },
                                    onDelete = { deletingSession = session },
                                    dragHandleModifier = Modifier.dragHandle(dragState, sessionKey, listState, viewModel::reorderSessions),
                                )
                            }
                        }
                        item {
                            AddSessionButton(onClick = { onOpenSession(viewModel.weekIdentifier, viewModel.selectedDayName, null) })
                        }
                    }
                }
            }

            // Bottom week-navigation bar — mirrors WeeklyPlanView's weekNavigationBar
            // (safeAreaInset(edge: .bottom)): a glassRect pill with prev/next chevrons and the
            // centered week range.
            WeekNavigationBar(
                weekRange = formattedWeekRange(viewModel.weekStart),
                onPrevious = viewModel::goToPreviousWeek,
                onNext = viewModel::goToNextWeek,
            )
        }
    }

    deletingSession?.let { session ->
        AlertDialog(
            onDismissRequest = { deletingSession = null },
            containerColor = Color(0xFF1A1A26),
            title = { Text("Delete Session?", color = Color.White) },
            text = { Text("\"${session.title ?: "Workout"}\" and its exercises will be removed.", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(session.id ?: "")
                    deletingSession = null
                }) { Text("Delete", color = Color(0xFFFF453A)) }
            },
            dismissButton = { TextButton(onClick = { deletingSession = null }) { Text("Cancel", color = Color.White) } },
        )
    }

    aiPlannerViewModel?.let { vm ->
        AIWorkoutPlannerScreen(
            viewModel = vm,
            onClose = { vm.dispose(); aiPlannerViewModel = null },
            onApplied = { vm.dispose(); aiPlannerViewModel = null; viewModel.fetch() },
        )
    }

    // Inline single-session editing sheets/dialogs — all route into `singleSession` (guarded, in
    // case the day's session set changed underneath an open sheet).
    if (showPicker) {
        ExercisePickerSheet(
            onAdd = { stubs -> singleSession?.let { viewModel.addExercisesTo(it, stubs) } },
            onDismiss = { showPicker = false },
        )
    }

    editingExercise?.let { exercise ->
        ExerciseEditorSheet(
            existing = exercise,
            onSave = { updated ->
                singleSession?.let { viewModel.updateExerciseIn(it, updated) }
                editingExercise = null
            },
            onDelete = {
                singleSession?.let { viewModel.deleteExerciseFrom(it, exercise.id ?: "") }
                editingExercise = null
            },
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
            onSave = { updatedA, updatedB -> singleSession?.let { viewModel.upsertPairIn(it, updatedA, updatedB) }; editingPair = null },
            onDismiss = { editingPair = null },
        )
    }

    deletingExerciseId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingExerciseId = null },
            containerColor = Color(0xFF1A1A26),
            title = { Text("Delete Exercise?", color = Color.White) },
            confirmButton = {
                TextButton(onClick = {
                    singleSession?.let { viewModel.deleteExerciseFrom(it, id) }
                    deletingExerciseId = null
                }) { Text("Delete", color = Color(0xFFFF453A)) }
            },
            dismissButton = { TextButton(onClick = { deletingExerciseId = null }) { Text("Cancel", color = Color.White) } },
        )
    }

    deletingPairIds?.let { (aId, bId) ->
        AlertDialog(
            onDismissRequest = { deletingPairIds = null },
            containerColor = Color(0xFF1A1A26),
            title = { Text("Delete Superset?", color = Color.White) },
            confirmButton = {
                TextButton(onClick = {
                    singleSession?.let { viewModel.deletePairFrom(it, aId, bId) }
                    deletingPairIds = null
                }) { Text("Delete", color = Color(0xFFFF453A)) }
            },
            dismissButton = { TextButton(onClick = { deletingPairIds = null }) { Text("Cancel", color = Color.White) } },
        )
    }

    commentingExercise?.let { exercise ->
        val exId = exercise.id ?: ""
        ExerciseCommentsSheet(
            exerciseName = exercise.name ?: "Exercise",
            exerciseId = exId,
            weekIdentifier = viewModel.weekIdentifier,
            authorName = viewModel.authorName,
            authorRole = viewModel.authorRole,
            initialComments = viewModel.exerciseComments[exId] ?: emptyList(),
            onCommentsUpdated = { viewModel.setCommentsFor(exId, it) },
            onDismiss = { commentingExercise = null },
        )
    }
}

// Mirrors WeeklyPlanView.singleSessionInlineView + its bottomActionButton (the 1-session case):
// a title field, the exercise/superset rows (reordered by drag, tap to edit, trailing delete),
// then Add Exercise / Add Superset / Add Session. Reuses SessionEditScreen's row composables so a
// session reads the same inline as it does in the full-screen editor.
@Composable
internal fun SingleSessionInline(
    session: PlannedSessionResponse,
    viewModel: SessionExerciseEditor,
    onAddExercise: () -> Unit,
    onAddSuperset: () -> Unit,
    onAddSession: () -> Unit,
    onEditExercise: (ExerciseData) -> Unit,
    onEditPair: (ExerciseData, ExerciseData) -> Unit,
    onDeleteExercise: (String) -> Unit,
    onDeletePair: (String, String) -> Unit,
    onCommentsTap: (ExerciseData) -> Unit,
    showNotes: Boolean = true,
    swipeActions: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val exercises = session.exercises.orEmpty()
    val units = exercises.groupedForDisplay()
    val dragState = remember(units) { DragReorderState(units) { it.id } }
    fun commitReorder(newOrder: List<ExerciseDisplayUnit>) {
        viewModel.reorderExercisesIn(
            session,
            newOrder.flatMap { u ->
                when (u) {
                    is ExerciseDisplayUnit.Single -> listOf(u.exercise)
                    is ExerciseDisplayUnit.Pair -> listOf(u.a, u.b)
                }
            }
        )
    }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        item {
            SessionTitleField(
                sessionId = session.id ?: "",
                initialTitle = session.title ?: "",
                onCommit = { viewModel.updateSessionTitle(session, it) },
            )
        }

        if (exercises.isEmpty()) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp)
                ) {
                    Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(36.dp))
                    Text("No exercises yet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.4f))
                }
            }
        } else {
            items(dragState.order, key = { it.id }) { unit ->
                Box(modifier = Modifier.dragReorderItem(dragState, unit.id).then(if (dragState.draggingKey == unit.id) Modifier else Modifier.animateItem())) {
                    val row: @Composable () -> Unit = {
                        when (unit) {
                            is ExerciseDisplayUnit.Single -> SessionExerciseRow(
                                exercise = unit.exercise,
                                onClick = { onEditExercise(unit.exercise) },
                                onDelete = { onDeleteExercise(unit.exercise.id ?: "") },
                                comments = viewModel.exerciseComments[unit.exercise.id] ?: emptyList(),
                                onCommentsTap = { onCommentsTap(unit.exercise) },
                                showNotes = showNotes,
                                dragHandleModifier = Modifier.dragHandle(dragState, unit.id, listState, ::commitReorder),
                            )
                            is ExerciseDisplayUnit.Pair -> SessionSupersetRow(
                                a = unit.a, b = unit.b,
                                onClick = { onEditPair(unit.a, unit.b) },
                                onDelete = { onDeletePair(unit.a.id ?: "", unit.b.id ?: "") },
                                commentsA = viewModel.exerciseComments[unit.a.id] ?: emptyList(),
                                commentsB = viewModel.exerciseComments[unit.b.id] ?: emptyList(),
                                onCommentsTapA = { onCommentsTap(unit.a) },
                                onCommentsTapB = { onCommentsTap(unit.b) },
                                showNotes = showNotes,
                                dragHandleModifier = Modifier.dragHandle(dragState, unit.id, listState, ::commitReorder),
                            )
                        }
                    }
                    if (swipeActions) {
                        val specs = when (unit) {
                            is ExerciseDisplayUnit.Single -> listOf(
                                SwipeActionSpec(Icons.Filled.Edit, "Edit", SwipeEditTint) { onEditExercise(unit.exercise) },
                                SwipeActionSpec(Icons.Filled.Delete, "Delete", SwipeDeleteTint) { onDeleteExercise(unit.exercise.id ?: "") },
                            )
                            is ExerciseDisplayUnit.Pair -> listOf(
                                SwipeActionSpec(Icons.Filled.Edit, "Edit", SwipeEditTint) { onEditPair(unit.a, unit.b) },
                                SwipeActionSpec(Icons.Filled.Delete, "Delete", SwipeDeleteTint) { onDeletePair(unit.a.id ?: "", unit.b.id ?: "") },
                            )
                        }
                        SwipeActionsRow(actions = specs) { row() }
                    } else {
                        row()
                    }
                }
            }
        }

        item {
            SingleSessionActions(onAddExercise = onAddExercise, onAddSuperset = onAddSuperset, onAddSession = onAddSession)
        }
    }
}

// Session title field — keeps its text local while focused so typing doesn't churn the whole
// screen, and commits (a single upsert) on focus loss, matching iOS syncing title on disappear.
// Reuses SessionEditScreen's compact 50dp field so it reads the same in both places.
@Composable
private fun SessionTitleField(sessionId: String, initialTitle: String, onCommit: (String) -> Unit) {
    var text by remember(sessionId) { mutableStateOf(initialTitle) }
    var wasFocused by remember(sessionId) { mutableStateOf(false) }
    SessionTitleTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.onFocusChanged { state ->
            if (state.isFocused) wasFocused = true
            else if (wasFocused) { wasFocused = false; if (text != initialTitle) onCommit(text) }
        },
    )
}

// Mirrors WeeklyPlanView.bottomActionButton for the 1-session case: prominent outlined "Add
// Exercise" + two subtle text buttons ("Add Superset", "Add Session").
@Composable
private fun SingleSessionActions(onAddExercise: () -> Unit, onAddSuperset: () -> Unit, onAddSession: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                .clickable(onClick = onAddExercise)
                .padding(vertical = 14.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.padding(start = 8.dp))
            Text("Add Exercise", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onAddSuperset).padding(vertical = 10.dp)
        ) {
            Icon(Icons.Filled.Link, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.padding(start = 6.dp))
            Text("Add Superset", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.4f))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onAddSession).padding(vertical = 10.dp)
        ) {
            Icon(Icons.Filled.LibraryAdd, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.padding(start = 6.dp))
            Text("Add Session", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.4f))
        }
    }
}

// Mirrors WeeklyPlanView.weekNavigationBar: a full-width glassRect pill (translucent fill,
// rounded 20) with prev/next chevrons and the centered week range, pinned to the bottom.
@Composable
internal fun WeekNavigationBar(weekRange: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 15.dp)
            .padding(bottom = 16.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        Box(modifier = Modifier.fillMaxHeight().clickable(onClick = onPrevious).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous week", tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(weekRange, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier.fillMaxHeight().clickable(onClick = onNext).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next week", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

// Mirrors DayTabModel.color on iOS — one distinct accent per day (iOS system-color dark-mode
// values, matching the exact hex shades already used elsewhere in this app for iOS system colors).
internal fun dayColor(day: java.time.DayOfWeek): Color = when (day) {
    java.time.DayOfWeek.MONDAY -> Color(0xFF0A84FF)     // blue
    java.time.DayOfWeek.TUESDAY -> Color(0xFFFF9F0A)    // orange
    java.time.DayOfWeek.WEDNESDAY -> Color(0xFFFFD60A)  // yellow
    java.time.DayOfWeek.THURSDAY -> Color(0xFF34C759)   // green
    java.time.DayOfWeek.FRIDAY -> Color(0xFF64D2FF)     // cyan
    java.time.DayOfWeek.SATURDAY -> Color(0xFFAF52DE)   // purple
    java.time.DayOfWeek.SUNDAY -> Color(0xFFFF375F)     // pink
}

// Mirrors DayTabModel.shortLabel exactly (M/T/W/T/F/S/S — Tuesday/Thursday and Saturday/Sunday
// deliberately collide on one letter, disambiguated by only ever showing the full name when active).
internal fun dayShortLabel(day: java.time.DayOfWeek): String = when (day) {
    java.time.DayOfWeek.MONDAY -> "M"
    java.time.DayOfWeek.TUESDAY -> "T"
    java.time.DayOfWeek.WEDNESDAY -> "W"
    java.time.DayOfWeek.THURSDAY -> "T"
    java.time.DayOfWeek.FRIDAY -> "F"
    java.time.DayOfWeek.SATURDAY -> "S"
    java.time.DayOfWeek.SUNDAY -> "S"
}

@Composable
internal fun DayTabPill(
    day: java.time.DayOfWeek,
    isActive: Boolean,
    hasExercises: Boolean,
    activeWidth: Dp,
    inactiveWidth: Dp,
    onClick: () -> Unit,
) {
    // Width + fill animate together on a day change: the pill expands to `activeWidth` (showing
    // the full name, revealed from the centre as the frame grows) or contracts to the letter chip.
    val spec = tween<Dp>(durationMillis = 280, easing = FastOutSlowInEasing)
    val width by animateDpAsState(targetValue = if (isActive) activeWidth else inactiveWidth, animationSpec = spec, label = "dayTabWidth")
    val bg by animateColorAsState(
        targetValue = if (isActive) dayColor(day) else Color.White.copy(alpha = 0.1f),
        animationSpec = tween(280, easing = FastOutSlowInEasing), label = "dayTabBg"
    )
    Box {
        Box(
            modifier = Modifier
                .width(width)
                // iOS is 36pt; a touch taller here (40dp) reads better at Android densities.
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isActive) day.getDisplayName(TextStyle.FULL, Locale.ENGLISH) else dayShortLabel(day),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                color = if (isActive) Color.White else Color.White.copy(alpha = 0.45f),
            )
        }
        if (hasExercises && !isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dayColor(day))
            )
        }
    }
}

// 44dp glass circle matching iOS's .glassCircle() (44pt frame, 18dp glyph).
@Composable
private fun CircleIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun PlanSessionCard(session: PlannedSessionResponse, onClick: () -> Unit, onDelete: () -> Unit, dragHandleModifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Icon(
            Icons.Filled.Menu, contentDescription = "Drag to reorder", tint = Color.White.copy(alpha = 0.35f),
            modifier = dragHandleModifier.size(20.dp).padding(end = 8.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(session.title?.takeIf { it.isNotBlank() } ?: "Workout", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            val count = session.exercises.orEmpty().size
            Text(
                if (count == 0) "No exercises" else "$count exercise${if (count == 1) "" else "s"}",
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f)
            )
        }
        Icon(
            Icons.Filled.Delete, contentDescription = "Delete session", tint = Color(0xFFFF453A).copy(alpha = 0.75f),
            modifier = Modifier.size(17.dp).clickable(onClick = onDelete)
        )
        Spacer(modifier = Modifier.padding(start = 12.dp))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(16.dp))
    }
}

@Composable
internal fun AddSessionButton(onClick: () -> Unit) {
    // Mirrors WeeklyPlanView.bottomActionButton: 8% fill + 1px white-15% border, corner 18,
    // content at 70% white.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.padding(start = 8.dp))
        Text("Add Session", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))
    }
}

internal fun formattedWeekRange(weekStart: LocalDate): String {
    val end = weekStart.plusDays(6)
    val monthFmt = java.time.format.DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    // Includes the year to match iOS ("6-12 Jul 2026").
    return if (weekStart.month == end.month) {
        "${weekStart.dayOfMonth}-${end.dayOfMonth} ${weekStart.format(monthFmt)} ${end.year}"
    } else {
        "${weekStart.dayOfMonth} ${weekStart.format(monthFmt)} - ${end.dayOfMonth} ${end.format(monthFmt)} ${end.year}"
    }
}
