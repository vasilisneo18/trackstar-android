package com.vasilisneo.trackstar.ui.screens.main.workout

// Wires the Workout tab to real backend data: fetches the selected day's plan (GET /api/plan)
// and completed sessions (GET /api/sessions) via WorkoutViewModel, replacing the previous
// always-empty placeholder. The day strip and rest-day empty state are unchanged from before
// (already pixel-matched MyWorkoutView.swift's rest-day state) — only the content below them
// now branches on viewModel.displaySessions. Session cards here are plain native Compose
// content (not a pixel copy of iOS), matching this project's "iOS visuals, Android mechanics"
// principle for new interaction surfaces.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.ui.res.painterResource
import com.vasilisneo.trackstar.R
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.data.api.ExerciseData
import com.vasilisneo.trackstar.data.api.PlannedSessionResponse
import com.vasilisneo.trackstar.data.api.WorkoutSessionResponse
import com.vasilisneo.trackstar.data.api.ExerciseComment
import com.vasilisneo.trackstar.data.workout.ExerciseDisplayUnit
import com.vasilisneo.trackstar.data.workout.groupedForDisplay
import com.vasilisneo.trackstar.ui.screens.main.plan.ExerciseCommentsSheet
import com.vasilisneo.trackstar.ui.screens.main.plan.ExerciseNoteButton
import com.vasilisneo.trackstar.ui.components.AuthWordmark
import com.vasilisneo.trackstar.ui.components.ProfileAvatarButton
import com.vasilisneo.trackstar.ui.theme.TrackstarBackground
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun WorkoutScreen(
    onProfileClick: () -> Unit = {},
    onScheduleWorkout: () -> Unit = {},
    onStartSession: (date: LocalDate, sessionId: String) -> Unit = { _, _ -> },
    onQuickLog: (date: LocalDate, sessionId: String) -> Unit = { _, _ -> },
    activeSession: ActiveSessionViewModel? = null,
    onResumeSession: () -> Unit = {},
    refreshKey: Int = 0,
    onUpgrade: () -> Unit = {},
) {
    val viewModel: WorkoutViewModel = viewModel()
    // Re-fetch completed sessions when a session finishes elsewhere (active session / quick log
    // both live above this screen in MainAppScreen and bump refreshKey on save).
    androidx.compose.runtime.LaunchedEffect(refreshKey) {
        if (refreshKey > 0) viewModel.fetch()
    }
    val selectedDate = viewModel.selectedDate
    val weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }
    val today = LocalDate.now()

    val displaySessions = viewModel.displaySessions
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Collapse progresses 0→1 over the first ~56dp of scroll (or instantly once past the first item).
    val thresholdPx = with(density) { 56.dp.toPx() }
    val collapse by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / thresholdPx).coerceIn(0f, 1f)
        }
    }
    // Full header height (measured at rest) → the scroll content's top padding, so content scrolls
    // UNDER the header rather than bleeding out below the day strip.
    var headerHeightPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    val headerHeightDp = with(density) { headerHeightPx.toDp() }
    // Backdrop-blur source (the list) + child (the header) — real frosted glass like iOS.
    val hazeState = androidx.compose.runtime.remember { HazeState() }
    var commentingExercise by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<ExerciseData?>(null) }
    // The missed session whose detail sheet is open (null = closed), mirroring MyWorkoutView's
    // `missedSession` @State on iOS.
    var missedSession by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<PlannedSessionResponse?>(null) }

    val monthFmt = java.time.format.DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    val dayLabel = if (selectedDate == today) "Today" else selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val dateLabel = "${selectedDate.dayOfMonth} ${selectedDate.format(monthFmt)}"

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        // Wordmark only on the empty rest-day state — with sessions present the cards are now
        // translucent, so a background wordmark would faintly bleed through the bottom card.
        // Lifted above the floating tab bar rather than hidden behind it.
        if (displaySessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 76.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                AuthWordmark()
            }
        }

        // Content — scrolls beneath the header (padded down by the header's full height).
        if (displaySessions.isEmpty()) {
            RestDayEmptyState(onScheduleWorkout = onScheduleWorkout)
        } else {
            LazyColumn(
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = (if (headerHeightDp > 0.dp) headerHeightDp else 150.dp) + 4.dp,
                    // Clear the floating tab bar with a comfortable gap above it (matches the
                    // space iOS leaves below the last card).
                    bottom = com.vasilisneo.trackstar.ui.components.tabBarContentBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().haze(hazeState)
            ) {
                    displaySessions.forEach { display ->
                        when (display) {
                            is SessionDisplay.Upcoming -> {
                                // If a session is already in progress for this planned session,
                                // show the in-progress card instead of the Start/plan cards
                                // (mirrors MyWorkoutView.inProgressCard on iOS).
                                if (activeSession != null && activeSession.planSessionId == display.planned.id) {
                                    item {
                                        InProgressCard(session = activeSession, onResume = onResumeSession)
                                    }
                                } else {
                                    item {
                                        StartSessionButton(
                                            session = display.planned,
                                            // Free tier: 3 logged sessions/week (FeatureGate). At the
                                            // cap, starting/quick-logging opens the paywall instead.
                                            onStart = {
                                                if (!viewModel.canStartSession) onUpgrade()
                                                else onStartSession(selectedDate, display.planned.id ?: return@StartSessionButton)
                                            },
                                            onQuickLog = {
                                                if (!viewModel.canStartSession) onUpgrade()
                                                else onQuickLog(selectedDate, display.planned.id ?: return@StartSessionButton)
                                            },
                                        )
                                    }
                                    items(display.planned.exercises.orEmpty().groupedForDisplay(), key = { it.id }) { unit ->
                                        when (unit) {
                                            is ExerciseDisplayUnit.Single -> ExercisePlanCard(
                                                exercise = unit.exercise,
                                                comments = viewModel.exerciseComments[unit.exercise.id] ?: emptyList(),
                                                onCommentsTap = { commentingExercise = unit.exercise },
                                            )
                                            is ExerciseDisplayUnit.Pair -> ExercisePlanPairCard(
                                                a = unit.a, b = unit.b,
                                                commentsA = viewModel.exerciseComments[unit.a.id] ?: emptyList(),
                                                commentsB = viewModel.exerciseComments[unit.b.id] ?: emptyList(),
                                                onCommentsTapA = { commentingExercise = unit.a },
                                                onCommentsTapB = { commentingExercise = unit.b },
                                            )
                                        }
                                    }
                                }
                            }
                            is SessionDisplay.Completed -> item {
                                CompletedSessionCard(session = display.planned, completed = display.completed)
                            }
                            is SessionDisplay.Missed -> item {
                                // If a session is already running for this planned day (e.g. started
                                // late from the missed sheet), show the in-progress card instead —
                                // matching the Upcoming branch and iOS's MyWorkoutView.
                                if (activeSession != null && activeSession.planSessionId == display.planned.id) {
                                    InProgressCard(session = activeSession, onResume = onResumeSession)
                                } else {
                                    MissedSessionCard(session = display.planned, onClick = { missedSession = display.planned })
                                }
                            }
                        }
                    }
                }
            }

        // Fixed header overlay: avatar row + collapsing day strip. Its background fades in on
        // scroll so content passing beneath it is hidden — but as a vertical gradient that stays
        // opaque behind the avatar/strip and fades out over the last stretch, so content slides
        // under it softly instead of meeting a flat near-black edge (Android can't blur like iOS).
        val headerAlpha = (collapse * 2.5f).coerceIn(0f, 1f)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .onSizeChanged { if (collapse < 0.01f && it.height > 0) headerHeightPx = it.height }
                // Frosted glass: blurs the list behind the header, tint ramping in on scroll so
                // the top stays clear (glow shows through) — matches iOS's ultraThinMaterial.
                .hazeChild(
                    state = hazeState,
                    style = HazeStyle(
                        // Transparent base so at rest the header shows the gradient (no dark bar).
                        // A LIGHT tint (not the dark base) so the frost LIGHTENS the blurred content
                        // like iOS's ultraThinMaterial — a lighter grey band, not a darker one.
                        backgroundColor = Color.Transparent,
                        // Semi-opaque frosted-grey band: on real devices the blur softens content;
                        // on emulators (no RenderEffect blur) this opacity alone obscures it so the
                        // header doesn't read as a see-through pane. Grey (not the dark base) keeps
                        // it the lighter frosted colour iOS shows.
                        tint = HazeTint(Color(0xFF3B3B46).copy(alpha = 0.82f * headerAlpha)),
                        blurRadius = 40.dp,
                    ),
                )
                .statusBarsPadding()
        ) {
            // Top row: profile avatar + schedule (calendar) button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                ProfileAvatarButton(initials = viewModel.userInitials, onClick = onProfileClick)
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable(onClick = onScheduleWorkout),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = "Schedule workout", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            CollapsingDayStrip(
                collapse = collapse,
                weekDays = weekDays,
                selectedDate = selectedDate,
                today = today,
                dayLabel = dayLabel,
                dateLabel = dateLabel,
                onSelect = { viewModel.goToDate(it) },
            )
        }
    }

    missedSession?.let { planned ->
        val exercises = planned.exercises.orEmpty()
        val sessionId = planned.id
        MissedSessionSheet(
            workoutTitle = planned.title?.takeIf { it.isNotBlank() } ?: "Workout",
            exercises = exercises,
            // Only offer the actions when the session can actually be started/logged. Both paths
            // enforce the free-tier weekly cap (paywall on overflow) like the Upcoming card.
            onStart = if (sessionId != null) {
                {
                    missedSession = null
                    if (!viewModel.canStartSession) onUpgrade() else onStartSession(selectedDate, sessionId)
                }
            } else null,
            onQuickLog = if (sessionId != null) {
                {
                    missedSession = null
                    if (!viewModel.canStartSession) onUpgrade() else onQuickLog(selectedDate, sessionId)
                }
            } else null,
            onDismiss = { missedSession = null },
        )
    }

    commentingExercise?.let { exercise ->
        val exId = exercise.id ?: ""
        ExerciseCommentsSheet(
            exerciseName = exercise.name ?: "Exercise",
            exerciseId = exId,
            weekIdentifier = viewModel.weekId,
            authorName = viewModel.authorName,
            authorRole = viewModel.authorRole,
            initialComments = viewModel.exerciseComments[exId] ?: emptyList(),
            onCommentsUpdated = { viewModel.setCommentsFor(exId, it) },
            onDismiss = { commentingExercise = null },
        )
    }
}

// Trimmed text box (no font padding, centered line) so the day label/number sit tightly.
private val TightText = androidx.compose.ui.text.TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both),
)

// Day strip that collapses on scroll (ports MyWorkoutView.dayStrip): the 7-day M-T-W… row fades
// out and shrinks while a compact "Today 10 Jul" label fades in, so the header takes less room
// as you read down the exercise list.
@Composable
private fun CollapsingDayStrip(
    collapse: Float,
    weekDays: List<LocalDate>,
    selectedDate: LocalDate,
    today: LocalDate,
    dayLabel: String,
    dateLabel: String,
    onSelect: (LocalDate) -> Unit,
) {
    // No extra font padding + centered/trimmed line box, so the label/number sit tightly and
    // centre cleanly in the pill (Compose's default font padding otherwise loosens the spacing).
    // Declared inline via a val below.
    // iOS DayStripRow: 76→40 tall, 3-letter uppercase labels, a rounded selection pill that fills
    // the cell (corner 10, white@18), the number, and a today dot.
    val height = androidx.compose.ui.unit.lerp(76.dp, 40.dp, collapse)
    Box(
        modifier = Modifier.fillMaxWidth().height(height).padding(horizontal = 15.dp).clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(vertical = 6.dp)
                .graphicsLayer { alpha = (1f - collapse * 1.6f).coerceIn(0f, 1f) }
        ) {
            for (day in weekDays) {
                val selected = day == selectedDate
                val isToday = day == today
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { onSelect(day) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH),
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, style = TightText,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.4f),
                        )
                        Text(
                            day.dayOfMonth.toString(),
                            fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, style = TightText,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
                        )
                        Box(
                            modifier = Modifier.size(4.dp).clip(CircleShape)
                                .background(if (isToday) Color.White.copy(alpha = if (selected) 0.8f else 0.35f) else Color.Transparent)
                        )
                    }
                }
            }
        }
        val labelAlpha = ((collapse - 0.7f) / 0.3f).coerceIn(0f, 1f)
        if (labelAlpha > 0f) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.graphicsLayer { alpha = labelAlpha }) {
                Text(dayLabel, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.75f))
                Text(dateLabel, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.4f))
            }
        }
    }
}

// Mirrors iOS's startSessionButton in MyWorkoutView+Cards.swift: title above a translucent
// white "Start Session" pill + a square Quick Log (clipboard) button beside it.
@Composable
private fun StartSessionButton(session: PlannedSessionResponse, onStart: () -> Unit, onQuickLog: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        val title = session.title?.takeIf { it.isNotBlank() }
        if (title != null) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(onClick = onStart),
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text("Start Session", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
            }
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable(onClick = onQuickLog),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = "Quick log", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

private val MissedOrange = Color(0xFFFF9500)

// Ports iOS's missedSessionCard (MyWorkoutView+Cards.swift): an orange-outlined card for a
// planned session whose day has passed unlogged. Tapping it opens MissedSessionSheet.
@Composable
private fun MissedSessionCard(session: PlannedSessionResponse, onClick: () -> Unit) {
    val exercises = session.exercises.orEmpty()
    val setCount = exercises.sumOf { it.sets.orEmpty().size }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardFill)
            .border(1.dp, MissedOrange.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MissedOrange, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.padding(start = 14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text("Missed Session", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    session.title?.takeIf { it.isNotBlank() } ?: "Workout",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(18.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.padding(start = 6.dp))
            Text("${exercises.size} exercises", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.padding(start = 6.dp))
            Text("$setCount sets", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.5f))
        }

        Text(
            "Tap to view or start it now",
            fontSize = 12.sp,
            color = MissedOrange.copy(alpha = 0.8f)
        )
    }
}

// Mirrors iOS's inProgressCard: green-bordered card shown in place of Start Session while a
// session for this planned day is running — live per-exercise progress + elapsed timer + Resume.
@Composable
private fun InProgressCard(session: ActiveSessionViewModel, onResume: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardFill)
            .border(1.dp, CompletedGreen.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .clickable(onClick = onResume)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.size(8.dp).background(CompletedGreen, CircleShape))
            Spacer(modifier = Modifier.padding(start = 7.dp))
            Text("In Progress", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CompletedGreen)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                formatDuration(session.elapsedSeconds),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        val title = session.title.takeIf { it.isNotBlank() }
        if (title != null) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            session.exercises.forEach { exercise ->
                val (done, total) = session.exerciseProgress(exercise)
                val allDone = total > 0 && done == total
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        if (allDone) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (allDone) CompletedGreen else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.padding(start = 10.dp))
                    Text(
                        exercise.name ?: "Exercise",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (allDone) Color.White.copy(alpha = 0.4f) else Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "$done/$total",
                        fontSize = 13.sp,
                        color = if (allDone) CompletedGreen.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.4f)
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("${session.completedSets}/${session.totalSets} sets completed", fontSize = 12.sp, color = Color.White.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = CompletedGreen.copy(alpha = 0.9f), modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.padding(start = 4.dp))
            Text("Resume", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CompletedGreen.copy(alpha = 0.9f))
        }
    }
}

private data class SetGroup(
    val count: Int,
    val reps: Int?,
    val repsMax: Int?,
    val duration: String?,
    val distance: String?,
    val weight: String?,
    val bandLevel: String?,
    val restSeconds: Int,
) {
    val repsRangeDisplay: String
        get() = when {
            reps != null -> {
                val max = repsMax
                if (max != null && max != reps) "${minOf(reps, max)}-${maxOf(reps, max)}" else "$reps"
            }
            duration != null -> duration
            distance != null -> distance
            else -> ""
        }
    val typeLabel: String
        get() = when {
            reps != null -> "Reps"
            duration != null -> "Duration"
            distance != null -> "Distance"
            else -> ""
        }
}

// Groups consecutive identical sets so "3 × 10 @ 50 kg" shows as one row — mirrors
// ExerciseView.groupedSets on iOS.
private fun ExerciseData.groupedSets(): List<SetGroup> {
    val groups = mutableListOf<SetGroup>()
    sets.orEmpty().forEach { set ->
        val freq = set.frequencyValue
        val res = set.resistanceValue
        val last = groups.lastOrNull()
        if (last != null &&
            last.reps == freq?.reps && last.duration == freq?.duration && last.distance == freq?.distance &&
            last.repsMax == set.repsMax && last.weight == res?.weight && last.bandLevel == res?.bandLevel &&
            last.restSeconds == (set.restSeconds ?: 0)
        ) {
            groups[groups.size - 1] = last.copy(count = last.count + 1)
        } else {
            groups.add(
                SetGroup(
                    count = 1, reps = freq?.reps, repsMax = set.repsMax, duration = freq?.duration, distance = freq?.distance,
                    weight = res?.weight, bandLevel = res?.bandLevel, restSeconds = set.restSeconds ?: 0,
                )
            )
        }
    }
    return groups
}

private fun formatRest(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return when {
        m == 0 -> "${s}s"
        s == 0 -> "${m}m"
        else -> "${m}m ${s}s"
    }
}

// Mirrors ExerciseView.swift: exercise name header + one row of labeled stat columns
// (Sets / Reps / Weight / Rest) per group of identically-configured sets.
@Composable
private fun ExercisePlanCard(
    exercise: ExerciseData,
    comments: List<ExerciseComment> = emptyList(),
    onCommentsTap: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardFill)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(exercise.name ?: "Exercise", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)

        val groups = exercise.groupedSets()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEachIndexed { index, group ->
                if (index > 0) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    StatColumn(label = "Sets", value = "${group.count}")
                    StatColumn(label = group.typeLabel, value = group.repsRangeDisplay)
                    if (!group.weight.isNullOrBlank()) {
                        StatColumn(label = "Weight", value = "${group.weight}${(exercise.resistanceUnit?.weight ?: "KG").uppercase(Locale.ENGLISH)}")
                    } else if (!group.bandLevel.isNullOrBlank()) {
                        StatColumn(label = "Band", value = group.bandLevel)
                    }
                    if (group.restSeconds > 0) {
                        StatColumn(label = "Rest", value = formatRest(group.restSeconds))
                    }
                }
            }
        }
        ExerciseNoteButton(comments = comments, onTap = onCommentsTap)
    }
}

// Exercise-card fill — the translucent white the weekly-plan cards use, so the workout view's
// cards read identically to the planner's (Android-only tweak; iOS to follow). Lets the theme
// gradient tint the cards instead of a flat slate.
private val CardFill = Color.White.copy(alpha = 0.06f)

// Superset pair — ports iOS's CompoundExerciseView: link + "Superset" header, each exercise as a
// name + per-set label row, an arrow-down divider between them, then Rounds / Rest stat columns.
// Read-only (no drag/delete) — the editable twin is SessionSupersetRow in the plan package.
@Composable
private fun ExercisePlanPairCard(
    a: ExerciseData,
    b: ExerciseData,
    commentsA: List<ExerciseComment> = emptyList(),
    commentsB: List<ExerciseComment> = emptyList(),
    onCommentsTapA: () -> Unit = {},
    onCommentsTapB: () -> Unit = {},
) {
    val rounds = a.sets.orEmpty().size
    val restSeconds = a.sets.orEmpty().firstOrNull()?.restSeconds ?: 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardFill)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Link, contentDescription = null, tint = Color(0xFF64D2FF), modifier = Modifier.size(11.dp))
            Spacer(modifier = Modifier.padding(start = 6.dp))
            Text("Superset", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64D2FF))
        }
        SupersetPreviewRow(a, commentsA, onCommentsTapA)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.1f)))
            Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(10.dp))
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.1f)))
        }
        SupersetPreviewRow(b, commentsB, onCommentsTapB)
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            StatColumn(label = "Rounds", value = "$rounds")
            if (restSeconds > 0) StatColumn(label = "Rest", value = formatRest(restSeconds))
        }
    }
}

@Composable
private fun SupersetPreviewRow(exercise: ExerciseData, comments: List<ExerciseComment> = emptyList(), onCommentsTap: () -> Unit = {}) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(exercise.name ?: "Exercise", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(modifier = Modifier.weight(1f))
        val group = exercise.groupedSets().firstOrNull()
        if (group != null) {
            val label = buildString {
                append(group.repsRangeDisplay)
                if (group.typeLabel.isNotBlank()) append(" ${group.typeLabel.lowercase()}")
                if (!group.weight.isNullOrBlank()) append(" @ ${group.weight} kg")
                else if (!group.bandLevel.isNullOrBlank()) append(" · ${group.bandLevel}")
            }
            Text(label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
    ExerciseNoteButton(comments = comments, onTap = onCommentsTap, showDivider = false, compact = true)
  }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
    }
}

// Mirrors SessionCardView.swift: header (checkmark + "Session Complete" + duration), per-
// exercise summary rows, footer divider + counts.
@Composable
private fun CompletedSessionCard(session: PlannedSessionResponse, completed: WorkoutSessionResponse) {
    val exercises = completed.sessionData?.exercises.orEmpty()
    val totalSets = exercises.sumOf { it.sets.size }
    val completedSets = exercises.sumOf { ex -> ex.sets.count { it.actualPerformance != null } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardFill)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = CompletedGreen, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.padding(start = 4.dp))
            Text("Session Complete", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                formatDuration(completed.durationSeconds ?: 0),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.55f)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            exercises.forEach { exercise ->
                val done = exercise.sets.count { it.actualPerformance != null }
                val total = exercise.sets.size
                val allDone = total > 0 && done == total
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        if (allDone) Icons.Filled.Check else Icons.Filled.MoreHoriz,
                        contentDescription = null,
                        tint = if (allDone) CompletedGreen else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    Text(exercise.name, fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                    Text(
                        "$done/$total",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (allDone) CompletedGreen.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.4f)
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "$completedSets/$totalSets sets · ${exercises.size} exercises",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private val CompletedGreen = Color(0xFF34C759)

@Composable
private fun RestDayEmptyState(onScheduleWorkout: () -> Unit) {
    // Center in the *visible* area, not the full space behind the floating tab bar: reserve the
    // tab bar (~76dp) + nav bar at the bottom so the cluster sits at the optical middle rather
    // than being dragged low (mirrors iOS reserving the tab bar via a bottom safeAreaInset).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(bottom = 76.dp)
            .padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(120.dp).background(Color.White.copy(alpha = 0.05f), CircleShape))
                Box(modifier = Modifier.size(86.dp).background(Color.White.copy(alpha = 0.07f), CircleShape))
                Box(modifier = Modifier.size(58.dp).background(Color.White.copy(alpha = 0.10f), CircleShape))
                Icon(
                    painter = painterResource(R.drawable.ic_workout_figure),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(30.dp)
                )
            }

            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    "REST DAY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.5.sp,
                    color = Color.White.copy(alpha = 0.35f)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nothing planned.", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "Enjoy the recovery, or\nschedule something.",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onScheduleWorkout)
                    .padding(vertical = 14.dp),
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text("Schedule Workout", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
