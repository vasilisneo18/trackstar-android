package com.vasilisneo.trackstar.ui.screens.main.workout

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.api.ExerciseComment
import com.vasilisneo.trackstar.data.api.PlannedSessionResponse
import com.vasilisneo.trackstar.data.api.WorkoutSessionResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.CommentRepository
import com.vasilisneo.trackstar.data.workout.PlanRepository
import com.vasilisneo.trackstar.data.workout.SessionRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

// Mirrors MyWorkoutViewModel.SessionDisplay on iOS: a planned session shows as Completed (a logged
// session matches it), Missed (its day is already past and nothing was logged), or Upcoming (today
// or a future day). Missed and Upcoming carry the same planned data — they differ only in how the
// card presents (an orange "you missed this" prompt vs. the Start/plan cards).
sealed interface SessionDisplay {
    data class Completed(val planned: PlannedSessionResponse, val completed: WorkoutSessionResponse) : SessionDisplay
    data class Missed(val planned: PlannedSessionResponse) : SessionDisplay
    data class Upcoming(val planned: PlannedSessionResponse) : SessionDisplay
}

// Loads the selected week's plan (GET /api/plan?weekIdentifier=) and all completed sessions
// (GET /api/sessions), following the same AndroidViewModel(app)-owns-its-repos pattern as
// ProfileViewModel. Silently keeps stale data on ApiResult.Error, matching that precedent.
// Repositories are constructor-injected so tests can supply fakes and exercise the plan/session
// display logic without the network. The secondary (Application) constructor is the one Compose's
// viewModel() factory resolves at runtime.
class WorkoutViewModel(
    app: Application,
    private val planRepository: PlanRepository,
    private val sessionRepository: SessionRepository,
    private val commentRepository: CommentRepository,
    private val bookingRepository: com.vasilisneo.trackstar.data.workout.BookingRepository =
        com.vasilisneo.trackstar.data.workout.BookingRepository(),
) : AndroidViewModel(app) {

    constructor(app: Application) : this(app, PlanRepository(), SessionRepository(), CommentRepository())

    private val tokenStore = com.vasilisneo.trackstar.data.auth.TokenStore(app)

    // Exercise notes for the week, merged onto cards by exercise id (best-effort).
    var exerciseComments by mutableStateOf<Map<String, List<ExerciseComment>>>(emptyMap())
        private set
    val weekId: String get() = weekIdentifierFor(selectedDate)
    val authorName: String
        get() = listOfNotNull(tokenStore.firstName?.ifBlank { null }, tokenStore.lastName?.ifBlank { null }).joinToString(" ").ifBlank { "You" }
    val authorRole: String
        get() = tokenStore.role ?: "athlete"

    // Initials for the profile avatar, from the cached signed-in identity (same source iOS
    // uses via KeychainManager). Falls back to "?" only when there's genuinely no name.
    val userInitials: String = com.vasilisneo.trackstar.ui.components.initialsFrom(
        listOfNotNull(tokenStore.firstName?.ifBlank { null }, tokenStore.lastName?.ifBlank { null })
            .joinToString(" ").ifBlank { null }
    )

    var selectedDate by mutableStateOf(LocalDate.now())
        private set

    var isLoading by mutableStateOf(false)
        private set

    private var weekSessions by mutableStateOf<List<PlannedSessionResponse>>(emptyList())
    private var completedSessions by mutableStateOf<List<WorkoutSessionResponse>>(emptyList())

    // Backend's `day` field is the capitalized weekday name written by iOS's
    // DayTabModel.rawValue ("Monday" … "Sunday"), not the lowercase name suggested by the
    // backend model's comment — confirmed via WeeklyPlanSyncRequest.swift.
    private val selectedDayName: String
        get() = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    // Free accounts may log FeatureGate.WEEKLY_SESSION_LIMIT sessions per calendar week. Count the
    // current week's already-logged sessions (server data, so it survives reinstall unlike iOS's
    // local store) and expose whether another may be started under the caller's plan.
    private val sessionsThisWeek: Int
        get() {
            val currentWeek = weekIdentifierFor(LocalDate.now())
            return completedSessions.count { s ->
                val d = s.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                d != null && weekIdentifierFor(d) == currentWeek
            }
        }

    val canStartSession: Boolean
        get() = com.vasilisneo.trackstar.data.billing.FeatureGate.canStartSession(
            com.vasilisneo.trackstar.data.billing.BillingManager.currentPlan.value, sessionsThisWeek
        )

    // True when the selected day is strictly before today, so an unlogged session on it counts as
    // missed rather than upcoming (mirrors iOS's MyWorkoutViewModel.isPastDay).
    private val isPastDay: Boolean
        get() = selectedDate.isBefore(LocalDate.now())

    val displaySessions: List<SessionDisplay>
        get() {
            // A logged session only counts for the exact date it was logged (its `date` is the
            // planned day). Without this a past session would attach to a same-id planned session in
            // another week — e.g. a "completed" report showing up on a future date.
            val dayKey = selectedDate.toString()
            return weekSessions
                .filter { it.day == selectedDayName }
                .sortedBy { it.orderIndex ?: 0 }
                .map { planned ->
                    val match = completedSessions.firstOrNull {
                        it.date == dayKey && planned.id != null && it.sessionData?.planSessionId == planned.id
                    }
                    when {
                        match != null -> SessionDisplay.Completed(planned, match)
                        isPastDay -> SessionDisplay.Missed(planned)
                        else -> SessionDisplay.Upcoming(planned)
                    }
                }
        }

    // Same-weekday sessions from one week ago — powers the "copy last week's workout" prompt on an
    // empty upcoming day (mirrors iOS's lastWeekSessions).
    var lastWeekSessions by mutableStateOf<List<PlannedSessionResponse>>(emptyList())
        private set

    // Coaching booking (mirrors iOS MyWorkoutViewModel): the athlete's own booked slots and the
    // coach's upcoming bookable slots.
    var bookings by mutableStateOf<List<com.vasilisneo.trackstar.data.api.SlotResponse>>(emptyList())
        private set
    var availableSlots by mutableStateOf<List<com.vasilisneo.trackstar.data.api.SlotResponse>>(emptyList())
        private set

    /** The athlete's booked coaching sessions on the selected day. */
    val bookingsForCurrentDay: List<com.vasilisneo.trackstar.data.api.SlotResponse>
        get() {
            val key = selectedDate.toString()
            return bookings.filter { it.date == key && it.bookedByMe }.sortedBy { it.startTime }
        }

    /** The soonest slots the athlete can still book (upcoming, not full) — up to 3. */
    val nextAvailableSlots: List<com.vasilisneo.trackstar.data.api.SlotResponse>
        get() {
            val todayKey = LocalDate.now().toString()
            val nowTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            return availableSlots
                .filter { !it.full && (it.date > todayKey || (it.date == todayKey && it.startTime >= nowTime)) }
                .sortedBy { it.date + it.startTime }
                .take(3)
        }

    fun loadBookings() {
        viewModelScope.launch {
            bookingRepository.cachedMyBookings()?.let { if (bookings.isEmpty()) bookings = it }
            bookingRepository.cachedAvailableSlots()?.let { if (availableSlots.isEmpty()) availableSlots = it }
            (bookingRepository.myBookings() as? ApiResult.Success)?.let { bookings = it.data }
            (bookingRepository.availableSlots() as? ApiResult.Success)?.let { availableSlots = it.data }
        }
    }

    init { fetch(); loadLastWeek(); loadBookings() }

    fun goToDate(date: LocalDate) {
        val weekChanged = weekIdentifierFor(date) != weekIdentifierFor(selectedDate)
        selectedDate = date
        if (weekChanged) fetch()
        loadLastWeek()
    }

    private fun loadLastWeek() {
        viewModelScope.launch {
            val lwId = weekIdentifierFor(selectedDate.minusWeeks(1))
            val dayName = selectedDayName
            val plan = planRepository.cachedPlan(lwId) ?: when (val r = planRepository.getPlan(lwId)) {
                is ApiResult.Success -> r.data
                is ApiResult.Error -> emptyList()
            }
            lastWeekSessions = plan.filter { it.day == dayName }.sortedBy { it.orderIndex ?: 0 }
        }
    }

    /** Copy last week's same-weekday sessions onto the current day (fresh ids), then refresh. */
    fun copyLastWeekPlan(onDone: (Boolean) -> Unit = {}) {
        val source = lastWeekSessions
        if (source.isEmpty()) { onDone(false); return }
        viewModelScope.launch {
            val requests = source.mapIndexed { idx, s ->
                com.vasilisneo.trackstar.data.api.PlannedSessionRequest(
                    id = java.util.UUID.randomUUID().toString(),
                    weekIdentifier = weekId,
                    day = selectedDayName,
                    orderIndex = s.orderIndex ?: idx,
                    title = s.title?.takeIf { it.isNotBlank() } ?: "Workout",
                    exercises = s.exercises.orEmpty().map { it.copy(id = java.util.UUID.randomUUID().toString()) },
                )
            }
            when (planRepository.upsertBatch(requests)) {
                is ApiResult.Success -> { fetch(); onDone(true) }
                is ApiResult.Error -> onDone(false)
            }
        }
    }

    // The week weekSessions currently holds, so a day-swipe that crosses a week boundary can tell
    // it's showing the wrong week and repaint — otherwise last week's sessions (and their completed
    // matches) bleed onto the new week, e.g. a "report" appearing on a future date.
    private var loadedWeekId: String? = null

    fun fetch() {
        viewModelScope.launch {
            val weekId = weekIdentifierFor(selectedDate)
            // If we crossed into a different week, the sessions on screen belong to the old week.
            // Repaint from THIS week's cache immediately (or clear) so we never render another
            // week's data; only keep stale-while-revalidate within the same week.
            if (loadedWeekId != weekId) {
                weekSessions = planRepository.cachedPlan(weekId) ?: emptyList()
            } else if (weekSessions.isEmpty()) {
                planRepository.cachedPlan(weekId)?.let { weekSessions = it }
            }
            if (completedSessions.isEmpty()) sessionRepository.cachedSessions()?.let { completedSessions = it }
            isLoading = weekSessions.isEmpty()
            when (val result = planRepository.getPlan(weekId)) {
                is ApiResult.Success -> { weekSessions = result.data; loadedWeekId = weekId }
                is ApiResult.Error -> Unit // keep stale data on failure
            }
            when (val result = sessionRepository.getSessions()) {
                is ApiResult.Success -> completedSessions = result.data
                is ApiResult.Error -> Unit
            }
            isLoading = false
        }
        // Notes load independently — best-effort previews, never block the workout view.
        viewModelScope.launch {
            when (val c = commentRepository.getWeekComments(weekIdentifierFor(selectedDate))) {
                is ApiResult.Success -> exerciseComments = c.data.groupBy { it.exerciseId ?: "" }
                is ApiResult.Error -> Unit
            }
        }
    }

    fun setCommentsFor(exerciseId: String, comments: List<ExerciseComment>) {
        exerciseComments = exerciseComments.toMutableMap().apply { put(exerciseId, comments) }
    }
}

// ISO-8601 week-of-year (Monday-first). The workout day strip groups weeks Monday→Sunday
// (previousOrSame(MONDAY)), and the backend plan for a week is stored under one identifier for all
// seven of those days. A locale-based key (e.g. en_US, Sunday-first) would put Sunday in the NEXT
// week — so on a US device only Sunday's session went missing, since it was fetched from the wrong
// week bucket. ISO keeps all of Mon→Sun in the same week, matching the strip and iOS's grouping.
fun weekIdentifierFor(date: LocalDate): String {
    val weekFields = WeekFields.ISO
    val week = date.get(weekFields.weekOfWeekBasedYear())
    val year = date.get(weekFields.weekBasedYear())
    return "%d-W%02d".format(year, week)
}
