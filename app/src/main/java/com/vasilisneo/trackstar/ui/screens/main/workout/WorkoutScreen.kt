package com.vasilisneo.trackstar.ui.screens.main.workout

// Wires the Workout tab to real backend data: fetches the selected day's plan (GET /api/plan)
// and completed sessions (GET /api/sessions) via WorkoutViewModel, replacing the previous
// always-empty placeholder. The day strip and rest-day empty state are unchanged from before
// (already pixel-matched MyWorkoutView.swift's rest-day state) — only the content below them
// now branches on viewModel.displaySessions. Session cards here are plain native Compose
// content (not a pixel copy of iOS), matching this project's "iOS visuals, Android mechanics"
// principle for new interaction surfaces.

import com.vasilisneo.trackstar.ui.i18n.tr
import com.vasilisneo.trackstar.ui.i18n.trf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.composed
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Groups
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
import com.vasilisneo.trackstar.ui.i18n.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import com.vasilisneo.trackstar.ui.components.loadingShimmer
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveCircleOutline
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
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
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.PaddingValues
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    onProfileClick: () -> Unit = {},
    onScheduleWorkout: () -> Unit = {},
    onBookSession: () -> Unit = {},
    // Coach: open the schedule on the selected day (yyyy-MM-dd).
    onOpenCoachSchedule: (String) -> Unit = {},
    showBookSession: Boolean = false,
    onStartSession: (date: LocalDate, sessionId: String) -> Unit = { _, _ -> },
    onQuickLog: (date: LocalDate, sessionId: String) -> Unit = { _, _ -> },
    activeSession: ActiveSessionViewModel? = null,
    onResumeSession: () -> Unit = {},
    refreshKey: Int = 0,
    onUpgrade: () -> Unit = {},
    onOpenDiet: () -> Unit = {},
) {
    val viewModel: WorkoutViewModel = viewModel()
    // Re-fetch completed sessions when a session finishes elsewhere (active session / quick log
    // both live above this screen in MainAppScreen and bump refreshKey on save).
    // Coach: their own sessions for the "Your sessions" card. Re-runs on every return to Home.
    val plan by com.vasilisneo.trackstar.data.billing.BillingManager.currentPlan.collectAsState()
    val isCoach = com.vasilisneo.trackstar.data.billing.FeatureGate.canCoach(plan)
    androidx.compose.runtime.LaunchedEffect(isCoach) { if (isCoach) viewModel.loadCoachSlots() }
    androidx.compose.runtime.LaunchedEffect(refreshKey) {
        if (refreshKey > 0) viewModel.fetch()
    }
    // Refresh the plan whenever the app returns to the foreground, so a coach's plan change made
    // while the athlete was away shows up without a manual reopen (mirrors iOS's refresh-on-appear).
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.fetch()
        viewModel.loadBookings()
    }
    val selectedDate = viewModel.selectedDate
    val today = LocalDate.now()
    val isPast = selectedDate.isBefore(today)
    // Dashboard pinning — long-press a card to pin it above the workout (mirrors iOS @AppStorage).
    val stepsPinned = com.vasilisneo.trackstar.ui.util.rememberBooleanPref("dashPinSteps", false)
    val bookPinned = com.vasilisneo.trackstar.ui.util.rememberBooleanPref("dashPinBook", false)
    val dietPinned = com.vasilisneo.trackstar.ui.util.rememberBooleanPref("dashPinDiet", false)
    // Per-widget visibility — users add/remove dashboard widgets via the gallery + long-press menu.
    val showStepsWidget = com.vasilisneo.trackstar.ui.util.rememberBooleanPref("widgetShowSteps", true)
    val showDietWidget = com.vasilisneo.trackstar.ui.util.rememberBooleanPref("widgetShowDiet", true)
    val showBookWidget = com.vasilisneo.trackstar.ui.util.rememberBooleanPref("widgetShowBook", true)
    // The workout (planned sessions / Plan session prompt) is a widget too — booking-only users can
    // hide it; pinned, it moves above every other card (mirrors iOS).
    val showWorkoutWidget = com.vasilisneo.trackstar.ui.util.rememberBooleanPref("widgetShowWorkout", true)
    // GPS activities (run / walk / ride): the Activity card records; the day's activities follow.
    val showActivityWidget = com.vasilisneo.trackstar.ui.util.rememberBooleanPref("widgetShowActivity", true)
    var recordActivityType by remember { mutableStateOf<com.vasilisneo.trackstar.data.activity.ActivityType?>(null) }
    var openedActivity by remember { mutableStateOf<com.vasilisneo.trackstar.data.api.RecordedActivity?>(null) }
    val allActivities by com.vasilisneo.trackstar.data.activity.ActivityStore.activities.collectAsState()
    val activityPrefs = androidx.compose.ui.platform.LocalContext.current.let { ctx ->
        remember { ctx.getSharedPreferences("trackstar_prefs", android.content.Context.MODE_PRIVATE) }
    }
    // Ski / snowboard days: the Slopes card records; its map button opens the ski resorts browser.
    val showSlopesWidget = com.vasilisneo.trackstar.ui.util.rememberBooleanPref("widgetShowSlopes", true)
    var showResorts by remember { mutableStateOf(false) }
    var showGroups by remember { mutableStateOf(false) }
    // Hiking: the card records a hike; its map button opens the trail browser ("Hike this" → guide).
    val showHikingWidget = com.vasilisneo.trackstar.ui.util.rememberBooleanPref("widgetShowHiking", true)
    var showTrails by remember { mutableStateOf(false) }
    var hikeGuide by remember { mutableStateOf<com.vasilisneo.trackstar.data.activity.Trail?>(null) }
    val lastSlopeType = { com.vasilisneo.trackstar.data.activity.ActivityType.of(activityPrefs.getString("slopesLastType", "ski")) }
    val slopesCard: @Composable (Modifier) -> Unit = { m ->
        com.vasilisneo.trackstar.ui.components.IosContextMenu(
            actions = listOf(com.vasilisneo.trackstar.ui.components.ContextMenuAction(
                "Remove from Home", Icons.Filled.RemoveCircleOutline, destructive = true,
                onClick = { showSlopesWidget.value = false })),
            modifier = m,
            onClick = { recordActivityType = lastSlopeType() },
        ) {
            com.vasilisneo.trackstar.ui.screens.main.activity.SlopesHomeCard(
                date = selectedDate, lastType = lastSlopeType(),
                onRecord = { recordActivityType = lastSlopeType() },
                onResorts = { showResorts = true },
                onGroups = { showGroups = true },
            )
        }
    }
    val hikingCard: @Composable (Modifier) -> Unit = { m ->
        com.vasilisneo.trackstar.ui.components.IosContextMenu(
            actions = listOf(com.vasilisneo.trackstar.ui.components.ContextMenuAction(
                "Remove from Home", Icons.Filled.RemoveCircleOutline, destructive = true,
                onClick = { showHikingWidget.value = false })),
            modifier = m,
            onClick = { hikeGuide = null; recordActivityType = com.vasilisneo.trackstar.data.activity.ActivityType.Hike },
        ) {
            com.vasilisneo.trackstar.ui.screens.main.activity.HikingHomeCard(
                date = selectedDate,
                onRecord = { hikeGuide = null; recordActivityType = com.vasilisneo.trackstar.data.activity.ActivityType.Hike },
                onTrails = { showTrails = true },
            )
        }
    }
    // Steps and Activity sit side by side as half tiles (mirrors iOS's movementSection); Steps alone
    // keeps its full card, Activity alone stays a half tile.
    val recordActivity = {
        recordActivityType = com.vasilisneo.trackstar.data.activity.ActivityType.of(activityPrefs.getString("activityLastType", "run"))
    }
    val movementRow: @Composable (Modifier, Boolean) -> Unit = { m, withSteps ->
        val steps = withSteps && showStepsWidget.value
        val activity = showActivityWidget.value
        if (steps && !activity) {
            PinnableCard(pinned = stepsPinned.value, onTogglePin = { stepsPinned.value = !stepsPinned.value },
                onRemove = { showStepsWidget.value = false }, modifier = m) {
                com.vasilisneo.trackstar.ui.screens.main.DailyStepsCard(date = selectedDate)
            }
        } else if (activity) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = m.fillMaxWidth()) {
                if (steps) {
                    PinnableCard(pinned = stepsPinned.value, onTogglePin = { stepsPinned.value = !stepsPinned.value },
                        onRemove = { showStepsWidget.value = false }, modifier = Modifier.weight(1f)) {
                        com.vasilisneo.trackstar.ui.screens.main.StepsHalfTile(date = selectedDate)
                    }
                }
                com.vasilisneo.trackstar.ui.components.IosContextMenu(
                    actions = listOf(com.vasilisneo.trackstar.ui.components.ContextMenuAction(
                        "Remove from Home", Icons.Filled.RemoveCircleOutline, destructive = true,
                        onClick = { showActivityWidget.value = false })),
                    modifier = Modifier.weight(1f),
                    onClick = recordActivity,
                ) {
                    com.vasilisneo.trackstar.ui.screens.main.activity.ActivityHomeCard(date = selectedDate, onRecord = recordActivity, onGroups = { showGroups = true })
                }
                if (!steps) Spacer(Modifier.weight(1f))
            }
        }
    }
    // Month shown in the Today pill while the week wheel moves (null = show "Today").
    var monthPill by remember { mutableStateOf<String?>(null) }
    val workoutPinned = com.vasilisneo.trackstar.ui.util.rememberBooleanPref("dashPinWorkout", false)
    var showWidgetGallery by remember { mutableStateOf(false) }
    val hasHiddenWidgets = !showWorkoutWidget.value || !showStepsWidget.value || !showActivityWidget.value || !showSlopesWidget.value || !showHikingWidget.value || !showDietWidget.value || (showBookSession && !showBookWidget.value)

    val displaySessions = viewModel.displaySessions
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Collapse progresses 0→1 over the first ~56dp of scroll (or instantly once past the first item).
    val thresholdPx = with(density) { 56.dp.toPx() }
    // The empty-day layout is a scrolling Column, not the LazyColumn — the header follows whichever
    // is on screen (only one is composed at a time; the Column's offset resets on day change).
    val emptyDayScroll = androidx.compose.foundation.rememberScrollState()
    LaunchedEffect(selectedDate) { emptyDayScroll.scrollTo(0) }
    val collapse by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            val list = if (listState.firstVisibleItemIndex > 0) 1f
                else (listState.firstVisibleItemScrollOffset / thresholdPx).coerceIn(0f, 1f)
            maxOf(list, (emptyDayScroll.value / thresholdPx).coerceIn(0f, 1f))
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
    // Which upcoming session cards are expanded to show their exercises (mirrors iOS's
    // expandedSessionIds).
    val expandedSessionIds = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateListOf<String>() }

    val monthFmt = java.time.format.DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    val dayLabel = if (selectedDate == today) "Today" else selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val dateLabel = "${selectedDate.dayOfMonth} ${selectedDate.format(monthFmt)}"

    // Horizontal swipe changes the day (left = next, right = previous), matching iOS's MyWorkoutView.
    // detectHorizontalDragGestures waits for horizontal slop, so vertical list scrolling still works.
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // No day-swipe on the content any more (it fought with taps on the cards) — weeks are paged by
    // swiping the day strip instead (CollapsingDayStrip).
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
        if (displaySessions.isEmpty() && viewModel.isLoading) {
            // Loading a (usually just-swiped-to) week — don't flash the plan/no-data state before
            // the real sessions land.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = TrackstarAccent, strokeWidth = 2.dp)
            }
        } else if (displaySessions.isEmpty()) {
            // Scrolls once the widgets outgrow the screen (Activity / Slopes cards etc.).
            Column(
                modifier = Modifier.fillMaxSize()
                    .haze(hazeState)
                    .verticalScroll(emptyDayScroll)
                    .padding(
                        top = (if (headerHeightDp > 0.dp) headerHeightDp else 150.dp) + 4.dp,
                        bottom = com.vasilisneo.trackstar.ui.components.tabBarContentBottomPadding(),
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Coach: their next session on this day (same look as an athlete's booking).
                val coachNext = viewModel.coachNextSlot
                if (isCoach && coachNext != null) {
                    Text(if (selectedDate == today) "Your next session today" else "Your next session",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp))
                    Box(modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).clickable { onOpenCoachSchedule(selectedDate.toString()) }) {
                        BookedSessionCard(coachNext, detail = coachSessionDetail(coachNext), loading = !viewModel.bookingsLoaded)
                    }
                }
                // Booked coaching sessions still show on an otherwise-empty day.
                val booked = viewModel.bookingsForCurrentDay
                if (booked.isNotEmpty()) {
                    Text(
                        (if (selectedDate == today) "Today's booked session" else "Booked session") + if (booked.size == 1) "" else "s",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    booked.forEach {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) { BookedSessionCard(it, loading = !viewModel.bookingsLoaded) }
                    }
                }
                if (isPast) {
                    // Past day: show steps if there were any, otherwise a "no data" state. No planning.
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val health = androidx.compose.runtime.remember { com.vasilisneo.trackstar.data.health.HealthConnectManager(ctx) }
                    var steps by androidx.compose.runtime.remember(selectedDate) { androidx.compose.runtime.mutableStateOf<Int?>(null) }
                    androidx.compose.runtime.LaunchedEffect(selectedDate) {
                        steps = if (health.isAvailable && health.hasStepsPermission()) health.steps(selectedDate) else 0
                    }
                    when (val s = steps) {
                        null -> Unit // loading — avoid flashing "no data"
                        else -> if (s > 0) {
                            com.vasilisneo.trackstar.ui.screens.main.DailyStepsCard(
                                date = selectedDate, modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxWidth().height(360.dp)) { NoDataForDay() }
                        }
                    }
                } else {
                    // Today / future empty day: Plan is a half tile beside Copy-last-week when there's
                    // something to copy, otherwise full width.
                    if (showWorkoutWidget.value) {
                        com.vasilisneo.trackstar.ui.components.IosContextMenu(
                            actions = widgetMenuActions(workoutPinned.value, { workoutPinned.value = !workoutPinned.value }, { showWorkoutWidget.value = false }),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) { openMenu ->
                            PlanSessionCard(
                                lastWeek = viewModel.lastWeekSessions,
                                weekdayName = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                                onPlan = onScheduleWorkout,
                                onCopyLastWeek = { viewModel.copyLastWeekPlan() },
                                onLongPress = openMenu,
                            )
                        }
                    }
                    movementRow(Modifier.padding(horizontal = 16.dp), true)
                    if (showSlopesWidget.value) slopesCard(Modifier.padding(horizontal = 16.dp))
                    if (showHikingWidget.value) hikingCard(Modifier.padding(horizontal = 16.dp))
                    val dayActivities = com.vasilisneo.trackstar.data.activity.ActivityStore.activitiesOn(selectedDate, allActivities)
                    if ((showActivityWidget.value || showSlopesWidget.value || showHikingWidget.value) && dayActivities.isNotEmpty()) {
                        com.vasilisneo.trackstar.ui.screens.main.activity.ActivityCarouselCard(
                            dayActivities, onOpen = { openedActivity = it }, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    if (showBookSession && showBookWidget.value) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp).clickable(onClick = onBookSession)) {
                            BookSessionDashboardCard(viewModel.nextAvailableSlots, loading = !viewModel.bookingsLoaded)
                        }
                    }
                    if (showDietWidget.value) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp).clickable(onClick = onOpenDiet)) {
                            DietDashboardCard(date = selectedDate)
                        }
                    }
                    if (hasHiddenWidgets) {
                        AddWidgetButton(onClick = { showWidgetGallery = true }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
                    }
                }
            }
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
                    item { com.vasilisneo.trackstar.ui.components.OfflineBanner() }
                    // The day's sessions — called first when the workout widget is pinned, otherwise
                    // between the pinned and unpinned cards.
                    val workoutItems: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
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
                                        item(key = "session-${display.planned.id}") {
                                            val sid = display.planned.id ?: ""
                                            SessionDashboardCard(
                                                session = display.planned,
                                                expanded = expandedSessionIds.contains(sid),
                                                onToggleExpand = {
                                                    if (expandedSessionIds.contains(sid)) expandedSessionIds.remove(sid)
                                                    else expandedSessionIds.add(sid)
                                                },
                                                comments = viewModel.exerciseComments,
                                                onCommentsTap = { commentingExercise = it },
                                                // Free tier: 3 logged sessions/week (FeatureGate). At the
                                                // cap, starting/quick-logging opens the paywall instead.
                                                onStart = {
                                                    if (!viewModel.canStartSession) onUpgrade()
                                                    else onStartSession(selectedDate, sid.ifBlank { return@SessionDashboardCard })
                                                },
                                                onQuickLog = {
                                                    if (!viewModel.canStartSession) onUpgrade()
                                                    else onQuickLog(selectedDate, sid.ifBlank { return@SessionDashboardCard })
                                                },
                                            )
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
                    if (showWorkoutWidget.value && workoutPinned.value) workoutItems()
                    // Pinned cards sit above the workout (long-press to pin/unpin).
                    if (stepsPinned.value && showStepsWidget.value) item {
                        PinnableCard(pinned = true, onTogglePin = { stepsPinned.value = false }, onRemove = { showStepsWidget.value = false }) {
                            com.vasilisneo.trackstar.ui.screens.main.DailyStepsCard(date = selectedDate)
                        }
                    }
                    if (bookPinned.value && showBookSession && !isPast && showBookWidget.value) item {
                        PinnableCard(pinned = true, onTogglePin = { bookPinned.value = false }, onClick = onBookSession, onRemove = { showBookWidget.value = false }) {
                            BookSessionDashboardCard(viewModel.nextAvailableSlots, loading = !viewModel.bookingsLoaded)
                        }
                    }
                    if (dietPinned.value && showDietWidget.value) item {
                        PinnableCard(pinned = true, onTogglePin = { dietPinned.value = false }, onClick = onOpenDiet, onRemove = { showDietWidget.value = false }) {
                            DietDashboardCard(date = selectedDate)
                        }
                    }
                    // Coach: their next session on this day (same look as an athlete's booking).
                    val coachNext = viewModel.coachNextSlot
                    if (isCoach && coachNext != null) {
                        item(key = "coach-next-label") {
                            Text(if (selectedDate == today) "Your next session today" else "Your next session",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
                        }
                        item(key = "coach-next") {
                            Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onOpenCoachSchedule(selectedDate.toString()) }) {
                                BookedSessionCard(coachNext, detail = coachSessionDetail(coachNext), loading = !viewModel.bookingsLoaded)
                            }
                        }
                    }
                    // Booked coaching sessions for the selected day (above the workout, like iOS).
                    val booked = viewModel.bookingsForCurrentDay
                    if (booked.isNotEmpty()) {
                        item {
                            Text(
                                (if (selectedDate == today) "Today's booked session" else "Booked session") + if (booked.size == 1) "" else "s",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                        items(booked, key = { "booked-${it.id}" }) { BookedSessionCard(it, loading = !viewModel.bookingsLoaded) }
                    }
                    if (showWorkoutWidget.value && !workoutPinned.value) workoutItems()
                    // Apple Health steps for the selected day, below the workout (matches iOS).
                    // Unpinned cards sit below the workout (default).
                    if ((!stepsPinned.value && showStepsWidget.value) || showActivityWidget.value) item(key = "movement") {
                        movementRow(Modifier, !stepsPinned.value)
                    }
                    if (showSlopesWidget.value) item(key = "slopes-card") { slopesCard(Modifier) }
                    if (showHikingWidget.value) item(key = "hiking-card") { hikingCard(Modifier) }
                    val dayActivities = com.vasilisneo.trackstar.data.activity.ActivityStore.activitiesOn(selectedDate, allActivities)
                    if ((showActivityWidget.value || showSlopesWidget.value || showHikingWidget.value) && dayActivities.isNotEmpty()) item(key = "activity-carousel") {
                        com.vasilisneo.trackstar.ui.screens.main.activity.ActivityCarouselCard(dayActivities, onOpen = { openedActivity = it })
                    }
                    if (showBookSession && !isPast && !bookPinned.value && showBookWidget.value) item {
                        PinnableCard(pinned = false, onTogglePin = { bookPinned.value = true }, onClick = onBookSession, onRemove = { showBookWidget.value = false }) {
                            BookSessionDashboardCard(viewModel.nextAvailableSlots, loading = !viewModel.bookingsLoaded)
                        }
                    }
                    if (!dietPinned.value && showDietWidget.value) item {
                        PinnableCard(pinned = false, onTogglePin = { dietPinned.value = true }, onClick = onOpenDiet, onRemove = { showDietWidget.value = false }) {
                            DietDashboardCard(date = selectedDate)
                        }
                    }
                    // Add widget — after the last card, styled like one (mirrors iOS).
                    if (hasHiddenWidgets) item(key = "add-widget") {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                            AddWidgetButton(onClick = { showWidgetGallery = true })
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
                // Solid fallback under the frost: where blur isn't available (emulators, some
                // devices) the cards would otherwise show straight through the date strip.
                .background(Color(0xFF1C2236).copy(alpha = 0.92f * headerAlpha))
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
                // Booking now lives in the dashboard "Book a session" card (matches iOS, which
                // dropped the nav-bar book button).
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
                selectedDate = selectedDate,
                today = today,
                dayLabel = dayLabel,
                dateLabel = dateLabel,
                onSelect = { viewModel.goToDate(it) },
                onMonthPill = { monthPill = it },
                onWeekSettled = { monday ->
                    // Same weekday in the week the wheel came to rest on.
                    viewModel.goToDate(monday.plusDays((selectedDate.dayOfWeek.value - 1).toLong()))
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                },
            )
        }

        // "Today" pill — appears when viewing another day, jumps back to today (matches iOS). While
        // the week wheel moves it shows the month instead, then turns back into "Today".
        androidx.compose.animation.AnimatedVisibility(
            visible = selectedDate != today || monthPill != null,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(bottom = if (activeSession != null) 150.dp else 92.dp),
        ) {
            // No background — just the label; a soft shadow keeps it readable over cards (matches iOS).
            val labelShadow = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset(0f, 2f), 12f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .animateContentSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) {
                        viewModel.goToDate(today)
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                val month = monthPill
                Icon(
                    if (month != null) Icons.Filled.CalendarMonth else Icons.AutoMirrored.Filled.Undo,
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp),
                )
                androidx.compose.animation.AnimatedContent(targetState = month ?: "Today", label = "todayPill") { label ->
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, style = labelShadow)
                }
            }
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

    recordActivityType?.let { type ->
        com.vasilisneo.trackstar.ui.screens.main.activity.ActivityRecorderDialog(initialType = type,
            onClose = { recordActivityType = null; hikeGuide = null },
            guide = if (type == com.vasilisneo.trackstar.data.activity.ActivityType.Hike) hikeGuide else null)
    }
    if (showTrails) {
        com.vasilisneo.trackstar.ui.screens.main.activity.TrailsDialog(
            onClose = { showTrails = false },
            onHike = { route -> showTrails = false; hikeGuide = route; recordActivityType = com.vasilisneo.trackstar.data.activity.ActivityType.Hike },
        )
    }
    if (showGroups) {
        com.vasilisneo.trackstar.ui.screens.main.activity.GroupsDialog(onClose = { showGroups = false })
    }
    if (showResorts) {
        com.vasilisneo.trackstar.ui.screens.main.activity.ResortsDialog(
            onClose = { showResorts = false },
            onRecord = { showResorts = false; recordActivityType = lastSlopeType() },
        )
    }
    openedActivity?.let { a ->
        com.vasilisneo.trackstar.ui.screens.main.activity.ActivityDetailDialog(activity = a, onClose = { openedActivity = null })
    }

    if (showWidgetGallery) {
        val hidden = buildList {
            if (!showWorkoutWidget.value) add(AddableWidget("workout") { showWorkoutWidget.value = true })
            if (!showStepsWidget.value) add(AddableWidget("steps") { showStepsWidget.value = true })
            if (!showActivityWidget.value) add(AddableWidget("activity") { showActivityWidget.value = true })
            if (!showSlopesWidget.value) add(AddableWidget("slopes") { showSlopesWidget.value = true })
            if (!showHikingWidget.value) add(AddableWidget("hiking") { showHikingWidget.value = true })
            if (!showDietWidget.value) add(AddableWidget("diet") { showDietWidget.value = true })
            if (showBookSession && !showBookWidget.value) add(AddableWidget("book") { showBookWidget.value = true })
        }
        WidgetGallerySheet(
            hidden = hidden,
            onDismiss = { showWidgetGallery = false },
        ) { id ->
            when (id) {
                "workout" -> FullPromptCard(Icons.Filled.FitnessCenter, "Workout", "Your planned sessions, or plan one for the day.")
                "steps" -> com.vasilisneo.trackstar.ui.screens.main.DailyStepsCard(date = selectedDate)
                "activity" -> com.vasilisneo.trackstar.ui.screens.main.activity.ActivityHomeCard(date = selectedDate, onRecord = {})
                "hiking" -> com.vasilisneo.trackstar.ui.screens.main.activity.HikingHomeCard(date = selectedDate, onRecord = {}, onTrails = {})
                "slopes" -> com.vasilisneo.trackstar.ui.screens.main.activity.SlopesHomeCard(date = selectedDate, lastType = lastSlopeType(), onRecord = {}, onResorts = {})
                "diet" -> DietDashboardCard()
                "book" -> BookSessionDashboardCard(viewModel.nextAvailableSlots, loading = !viewModel.bookingsLoaded)
            }
        }
        LaunchedEffect(hidden.isEmpty()) { if (hidden.isEmpty()) showWidgetGallery = false }
    }
}

// Trimmed text box (no font padding, centered line) so the day label/number sit tightly.
private val TightText = androidx.compose.ui.text.TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both),
)

// Day strip that collapses on scroll (ports MyWorkoutView.dayStrip): a horizontally paging wheel of
// weeks (flick through weeks; when it settles the same weekday is selected) with a month pill under
// it while it moves. On scroll the row fades/shrinks into a compact "Today 10 Jul" label.
private const val WEEK_PAGES = 209          // two years back … two years ahead
private const val THIS_WEEK_PAGE = 104

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CollapsingDayStrip(
    collapse: Float,
    selectedDate: LocalDate,
    today: LocalDate,
    dayLabel: String,
    dateLabel: String,
    onSelect: (LocalDate) -> Unit,
    onMonthPill: (String?) -> Unit,
    onWeekSettled: (monday: LocalDate) -> Unit,
) {
    val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun mondayOf(page: Int) = thisMonday.plusWeeks((page - THIS_WEEK_PAGE).toLong())
    fun pageOf(date: LocalDate) = THIS_WEEK_PAGE + java.time.temporal.ChronoUnit.WEEKS.between(
        thisMonday, date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))).toInt()

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = pageOf(selectedDate)) { WEEK_PAGES }
    val selectedDateState = androidx.compose.runtime.rememberUpdatedState(selectedDate)

    // Today pill / date picked elsewhere → bring that week into view.
    LaunchedEffect(selectedDate) {
        val target = pageOf(selectedDate).coerceIn(0, WEEK_PAGES - 1)
        if (target != pagerState.currentPage && !pagerState.isScrollInProgress) pagerState.animateScrollToPage(target)
    }
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.currentPage }.collect {
            if (pagerState.isScrollInProgress) onMonthPill(monthTitle(mondayOf(it)))
        }
    }
    // While the wheel moves, show the month; when it settles on another week, select it.
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.isScrollInProgress }.collect { moving ->
            if (moving) {
                onMonthPill(monthTitle(mondayOf(pagerState.currentPage)))
            } else {
                val monday = mondayOf(pagerState.settledPage)
                onMonthPill(monthTitle(monday))
                if (monday != selectedDateState.value.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) onWeekSettled(monday)
                kotlinx.coroutines.delay(900)
                onMonthPill(null)
            }
        }
    }

    val height = androidx.compose.ui.unit.lerp(76.dp, 40.dp, collapse)
    Box(modifier = Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            userScrollEnabled = collapse < 0.5f,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize().clipToBounds()
                .graphicsLayer { alpha = (1f - collapse * 1.6f).coerceIn(0f, 1f) },
        ) { page ->
            val monday = mondayOf(page)
            WeekRow(
                days = (0..6).map { monday.plusDays(it.toLong()) },
                selectedDate = selectedDate,
                today = today,
                onSelect = onSelect,
            )
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

// "October 2026", or "Oct – Nov 2026" when the week spans two months.
private fun monthTitle(monday: LocalDate): String {
    val sunday = monday.plusDays(6)
    return if (monday.month == sunday.month) {
        "${monday.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${monday.year}"
    } else {
        "${monday.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} – ${sunday.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${sunday.year}"
    }
}

// One week of the strip: 7 day pills (iOS DayStripRow — 46dp-wide rounded pill, white@18 when
// selected, a today dot).
@Composable
private fun WeekRow(days: List<LocalDate>, selectedDate: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 15.dp, vertical = 4.dp)
    ) {
        for (day in days) {
            val selected = day == selectedDate
            val isToday = day == today
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
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
    }
}

@Composable
// Ports iOS's startSessionButton + expandable session group (MyWorkoutView): one big card per
// planned session (title + Start + quick-log + an expand toggle). When expanded, the exercises
// render below inside the same accent-bordered container so the session reads as one unit.
private fun SessionDashboardCard(
    session: PlannedSessionResponse,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onStart: () -> Unit,
    onQuickLog: () -> Unit,
    comments: Map<String, List<ExerciseComment>>,
    onCommentsTap: (ExerciseData) -> Unit,
) {
    val accent = TrackstarAccent
    val units = session.exercises.orEmpty().groupedForDisplay()
    val exerciseCount = session.exercises.orEmpty().size
    val chevronAngle by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    // Animate the container (border/tint/inset) alongside the exercises' expand/collapse so nothing
    // snaps — border and padding fade/grow in step with the list rather than toggling instantly.
    val pad by androidx.compose.animation.core.animateDpAsState(if (expanded) 8.dp else 0.dp, label = "pad")
    val borderAlpha by androidx.compose.animation.core.animateFloatAsState(if (expanded) 0.35f else 0f, label = "border")
    val bgAlpha by androidx.compose.animation.core.animateFloatAsState(if (expanded) 0.06f else 0f, label = "bg")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(accent.copy(alpha = bgAlpha))
            .border(1.dp, accent.copy(alpha = borderAlpha), RoundedCornerShape(26.dp))
            .padding(pad)
    ) {
        // The session "start" card.
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardFill).padding(16.dp)
        ) {
            session.title?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color.White).clickable(onClick = onStart),
                ) {
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Text("Start Session", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                    Spacer(Modifier.weight(1f))
                }
                Box(
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onQuickLog),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ListAlt, "Quick log", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                }
            }
            // Expand/collapse toggle — whole row tappable, no ripple flash (matches iOS).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(
                    interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleExpand,
                )
            ) {
                Text(
                    if (exerciseCount == 1) tr("1 exercise") else trf("%s exercises", exerciseCount),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.KeyboardArrowDown, null, tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp).rotate(chevronAngle)
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                units.forEach { unit ->
                    when (unit) {
                        is ExerciseDisplayUnit.Single -> ExercisePlanCard(
                            exercise = unit.exercise,
                            comments = comments[unit.exercise.id] ?: emptyList(),
                            onCommentsTap = { onCommentsTap(unit.exercise) },
                        )
                        is ExerciseDisplayUnit.Pair -> ExercisePlanPairCard(
                            a = unit.a, b = unit.b,
                            commentsA = comments[unit.a.id] ?: emptyList(),
                            commentsB = comments[unit.b.id] ?: emptyList(),
                            onCommentsTapA = { onCommentsTap(unit.a) },
                            onCommentsTapB = { onCommentsTap(unit.b) },
                        )
                    }
                }
            }
        }
    }
}

// Ports iOS's planSessionCard. Sizing rules: Plan is a half tile beside "Copy last <day>" when the
// same weekday last week had sessions (progressive overload), otherwise a full-width prompt.
@Composable
private fun PlanSessionCard(
    lastWeek: List<PlannedSessionResponse>,
    weekdayName: String,
    onPlan: () -> Unit,
    onCopyLastWeek: () -> Unit,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (lastWeek.isEmpty()) {
        Box(modifier = modifier.clip(RoundedCornerShape(20.dp)).androidx_combinedClickable(onClick = onPlan, onLongClick = onLongPress)) {
            FullPromptCard(Icons.Filled.FitnessCenter, "Plan session", "Nothing planned — add a workout for this day.")
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).androidx_combinedClickable(onClick = onPlan, onLongClick = onLongPress)) {
            HalfTile(Icons.Filled.FitnessCenter, "Plan session", "Add a workout for this day.")
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp)).androidx_combinedClickable(onClick = onCopyLastWeek, onLongClick = onLongPress)) {
            val exercises = lastWeek.sumOf { it.exercises?.size ?: 0 }
            val name = lastWeek.first().title?.takeIf { it.isNotBlank() } ?: "Workout"
            val more = if (lastWeek.size > 1) " +${lastWeek.size - 1}" else ""
            HalfTile(
                Icons.Filled.Refresh, trf("Copy last %s", weekdayName),
                "$name$more · " + (if (exercises == 1) tr("1 exercise") else trf("%s exercises", exercises))
            )
        }
    }
}

// Full-width prompt: icon on the left, title + subtitle beside it (mirrors iOS dashboardCard).
@Composable
private fun FullPromptCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardFill).padding(16.dp)
    ) {
        Box(
            modifier = Modifier.size(46.dp).background(Color.White.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = TrackstarAccent, modifier = Modifier.size(18.dp)) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

// Half-width tile: icon on top, title + short subtitle below (mirrors iOS halfTile).
@Composable
private fun HalfTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight()
            .clip(RoundedCornerShape(20.dp)).background(CardFill).padding(14.dp)
    ) {
        TileContent(icon, title, subtitle, Modifier.fillMaxHeight())
    }
}

@Composable
private fun TileContent(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = TrackstarAccent, modifier = Modifier.size(16.dp)) }
        // Icon pinned top, text pinned bottom when the card is taller (e.g. Book with slots).
        Spacer(Modifier.weight(1f).heightIn(min = 12.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 2)
    }
}

// Ports iOS's noDataView: a centered "nothing here" state for a past day with no session and no steps.
@Composable
private fun NoDataForDay() {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Inbox, null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(10.dp))
        Text("No data for this day", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))
        Text("Nothing was logged on this day.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f))
    }
}

// Coach's next-session line: "18:00–19:00 · 3/5 booked".
private fun coachSessionDetail(slot: com.vasilisneo.trackstar.data.api.SlotResponse): String =
    trf("%1\$s–%2\$s · %3\$s/%4\$s booked", slot.startTime, slot.endTime, (slot.capacity - slot.remaining).coerceAtLeast(0), slot.capacity)

// Ports iOS's bookedSessionCard: a coaching session the athlete has already booked on this day —
// accent-tinted with a border so it stands out from the workout cards.
@Composable
private fun BookedSessionCard(slot: com.vasilisneo.trackstar.data.api.SlotResponse, detail: String? = null, loading: Boolean = false) {
    val accent = TrackstarAccent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .loadingShimmer(loading)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier.size(46.dp).background(accent.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.EventAvailable, null, tint = accent, modifier = Modifier.size(18.dp)) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                slot.title?.takeIf { it.isNotBlank() } ?: "Coaching session",
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1
            )
            Text(
                detail ?: "${slot.startTime}–${slot.endTime}${slot.coachName?.let { " · $it" } ?: ""}",
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 1
            )
        }
    }
}

// Ports iOS's bookWithCoachCard: prompt to book, listing up to 3 of the coach's next available
// slots with their open-spot counts. Tapping opens the booking flow.
@Composable
private fun BookSessionDashboardCard(
    slots: List<com.vasilisneo.trackstar.data.api.SlotResponse>,
    loading: Boolean = false,
) {
    // No open slots → a plain full-width prompt (a lone half tile looks empty).
    if (slots.isEmpty()) {
        Box(Modifier.loadingShimmer(loading)) { FullPromptCard(Icons.Filled.EventAvailable, "Book session", "Reserve a slot with your coach.") }
        return
    }
    val accent = TrackstarAccent
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .clip(RoundedCornerShape(20.dp)).background(CardFill).loadingShimmer(loading).padding(14.dp)
    ) {
        // Narrow, so the slots column gets the room for session names.
        Box(modifier = Modifier.width(112.dp).fillMaxHeight()) {
            TileContent(Icons.Filled.EventAvailable, "Book session", "Reserve a slot with your coach.", Modifier.fillMaxHeight())
        }
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.08f)))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            Text("AVAILABLE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.35f), letterSpacing = 1.sp)
            slots.forEach { slot ->
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(slotDayTime(slot), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, softWrap = false)
                        slot.title?.takeIf { it.isNotBlank() }?.let {
                            Text(it, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                    Text(if (slot.remaining == 1) tr("1 spot left") else trf("%s spots left", slot.remaining), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent)
                }
            }
        }
    }
}

// Diet tool card for the Home dashboard — calorie ring (consumed / planned) + macros for the day
// selected on Home, opening the full Diet screen on tap. Mirrors iOS's DietToolCard.
@Composable
private fun DietDashboardCard(
    date: LocalDate = LocalDate.now(),
    viewModel: com.vasilisneo.trackstar.ui.screens.main.diet.DietViewModel = viewModel(),
) {
    val accent = TrackstarAccent
    val isToday = date == LocalDate.now()
    val dayLabel = if (isToday) "Today" else date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    // Prefer the plan published by whichever DietViewModel edited it last (the Diet screen has its
    // own instance); read the day straight from the plan — viewModel.currentDay belongs to Diet.
    val plan = com.vasilisneo.trackstar.ui.screens.main.diet.DietRefreshSignal.latestPlan ?: viewModel.weeklyPlan
    val meals = plan[date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)].orEmpty()
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardFill).loadingShimmer(!viewModel.hasFetched).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Restaurant, null, tint = accent, modifier = Modifier.size(15.dp))
            Text("Diet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
            Text(dayLabel.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.35f), letterSpacing = 1.sp)
        }
        if (meals.isEmpty()) {
            Text(trf("No meals planned for %s. Tap to plan your meals.", if (isToday) tr("today") else dayLabel), fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
        } else {
            com.vasilisneo.trackstar.ui.screens.main.diet.NutritionSummary(meals)
        }
    }
}

// Wraps a dashboard card so a long-press opens a Pin/Unpin menu (mirrors iOS's contextMenu on the
// steps and book cards). Tapping runs onClick; the whole thing is ripple-free.
@Composable
private fun PinnableCard(
    pinned: Boolean,
    onTogglePin: () -> Unit,
    onClick: () -> Unit = {},
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    com.vasilisneo.trackstar.ui.components.IosContextMenu(
        actions = widgetMenuActions(pinned, onTogglePin, onRemove),
        modifier = modifier,
        onClick = onClick,
    ) { content() }
}

// Pin/Unpin + Remove from Home, as an iOS-style context menu.
private fun widgetMenuActions(pinned: Boolean, onTogglePin: () -> Unit, onRemove: (() -> Unit)?) = buildList {
    add(com.vasilisneo.trackstar.ui.components.ContextMenuAction(if (pinned) "Unpin" else "Pin to top", Icons.Filled.PushPin, onClick = onTogglePin))
    if (onRemove != null) add(com.vasilisneo.trackstar.ui.components.ContextMenuAction("Remove from Home", Icons.Filled.RemoveCircleOutline, destructive = true, onClick = onRemove))
}

private data class AddableWidget(val id: String, val onAdd: () -> Unit)

// Add widget sheet — mirrors iOS's widgetGallerySheet: full height, X on the left, a large title
// that scrolls up and hands over to a centered inline title, and the real Home cards — tap a card
// to add it (no + badge).
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun WidgetGallerySheet(
    hidden: List<AddableWidget>,
    onDismiss: () -> Unit,
    card: @Composable (String) -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val collapse by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf {
            val info = listState.layoutInfo.visibleItemsInfo
            val title = info.firstOrNull { it.index == 0 } ?: return@derivedStateOf if (info.isEmpty()) 0f else 1f
            val h = title.size.toFloat()
            if (h <= 0f) 0f else ((-title.offset).toFloat() / h).coerceIn(0f, 1f)
        }
    }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
        contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.93f)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .trackstarBackground()
        ) {
            // Nav bar: X, centered inline title (fades in as the large title scrolls away), 44dp balance.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp)
            ) {
                GlassCircleIconButton(onClick = onDismiss, contentDescription = "Close", icon = Icons.Filled.Close)
                Text(
                    "Add widget", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).alpha(collapse)
                )
                Spacer(Modifier.size(44.dp))
            }
            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                item {
                    Text("Add widget", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).alpha(1f - collapse))
                }
                items(hidden, key = { it.id }) { w ->
                    Box(modifier = Modifier.fillMaxWidth().androidx_combinedClickable(onClick = { w.onAdd() }, onLongClick = {})) {
                        card(w.id)
                    }
                }
                if (hidden.isEmpty()) item {
                    Text("All widgets added. Long-press a widget on Home to remove it.",
                        fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f))
                }
            }
        }
    }
}

// Add widget pill — card-coloured, after the last card on Home (mirrors iOS addWidgetButton).
@Composable
private fun AddWidgetButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.clip(CircleShape).background(CardFill).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text("Add widget", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.androidx_combinedClickable(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.composed {
        combinedClickable(
            interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }

// "Sat 24 · 18:00" — compact line for the Book card's slot column (matches iOS).
private fun slotDayTime(slot: com.vasilisneo.trackstar.data.api.SlotResponse): String {
    val day = runCatching {
        LocalDate.parse(slot.date).format(java.time.format.DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH))
    }.getOrDefault(slot.date)
    return "$day · ${slot.startTime}"
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
            Text(trf("%1\$s/%2\$s sets completed", session.completedSets, session.totalSets), fontSize = 12.sp, color = Color.White.copy(alpha = 0.35f))
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
                trf("%1\$s/%2\$s sets · %3\$s exercises", completedSets, totalSets, exercises.size),
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
