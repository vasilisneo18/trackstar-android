package com.vasilisneo.trackstar.ui.screens.main.plan

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.api.ExerciseComment
import com.vasilisneo.trackstar.data.api.ExerciseData
import com.vasilisneo.trackstar.data.api.PlannedSessionRequest
import com.vasilisneo.trackstar.data.api.PlannedSessionResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.TokenStore
import com.vasilisneo.trackstar.data.workout.CommentRepository
import com.vasilisneo.trackstar.data.workout.PlanRepository
import com.vasilisneo.trackstar.ui.screens.main.workout.weekIdentifierFor
import kotlinx.coroutines.launch
import java.util.UUID
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// Drives the weekly plan editor's list view: week/day navigation, fetching, session deletion,
// and — when a day holds exactly one session — inline editing of that session's title and
// exercises (mirroring iOS's WeeklyPlanView.singleSessionInlineView). Inline edits apply
// optimistically to local state and immediately re-upsert the one session (same pattern as
// reorderSessions), since there's no editor screen to defer a save to. When a day holds two or
// more sessions each is a tappable card that pushes SessionEditScreen + SessionEditViewModel,
// which defers its save to a single upsert on the checkmark (see that file's header comment).
// `athleteId` non-null = coach mode: reads/writes the athlete's plan via /coach/athletes/{id}/plan.
// @JvmOverloads keeps the no-arg AndroidViewModel factory working for the signed-in user's own plan.
class WeeklyPlanViewModel @JvmOverloads constructor(app: Application, private val athleteId: String? = null) : AndroidViewModel(app) {

    private val planRepository = PlanRepository()
    private val commentRepository = CommentRepository()
    private val tokenStore = TokenStore(app)

    // Comments/notes are a separate collection server-side — fetched per week and merged onto
    // exercises by id here (mirrors iOS's fetchWeekComments/applyComments).
    var exerciseComments by mutableStateOf<Map<String, List<ExerciseComment>>>(emptyMap())
        private set

    // Author identity for posting comments on your own exercises.
    val authorName: String
        get() = listOfNotNull(tokenStore.firstName?.ifBlank { null }, tokenStore.lastName?.ifBlank { null }).joinToString(" ").ifBlank { "You" }
    val authorRole: String
        get() = tokenStore.role ?: "athlete"

    var weekStart by mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
        private set
    var selectedDay by mutableStateOf(LocalDate.now().dayOfWeek)
        private set

    var isLoading by mutableStateOf(false)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var loadError by mutableStateOf(false)
        private set

    private var weekSessions by mutableStateOf<List<PlannedSessionResponse>>(emptyList())

    val selectedDayName: String
        get() = selectedDay.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    val weekIdentifier: String
        get() = weekIdentifierFor(weekStart)

    val sessionsForSelectedDay: List<PlannedSessionResponse>
        get() = weekSessions.filter { it.day == selectedDayName }.sortedBy { it.orderIndex ?: 0 }

    val weekDays: List<LocalDate>
        get() = (0..6).map { weekStart.plusDays(it.toLong()) }

    // Drives the day tab bar's "has content" dot indicator.
    fun hasExercises(day: DayOfWeek): Boolean {
        val dayName = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return weekSessions.any { it.day == dayName && it.exercises.orEmpty().isNotEmpty() }
    }

    init { fetch() }

    fun goToDay(day: LocalDate) {
        val newWeekStart = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        selectedDay = day.dayOfWeek
        // The whole week is already loaded — switching days just re-filters in-memory state, so
        // only refetch when actually crossing into a different week (no spinner flash on tab taps).
        if (newWeekStart != weekStart) {
            weekStart = newWeekStart
            fetch()
        }
    }

    fun goToPreviousWeek() {
        weekStart = weekStart.minusWeeks(1)
        fetch()
    }

    fun goToNextWeek() {
        weekStart = weekStart.plusWeeks(1)
        fetch()
    }

    fun fetch() {
        val week = weekIdentifierFor(weekStart)
        viewModelScope.launch {
            isLoading = true
            loadError = false
            when (val result = planRepository.getPlan(week, athleteId)) {
                is ApiResult.Success -> weekSessions = result.data
                is ApiResult.Error -> loadError = true
            }
            isLoading = false
        }
        // Comments load in a SEPARATE coroutine so a slow/failed comment request can never block
        // the plan out of its loading state or affect loadError — comment previews are best-effort.
        // Only for the signed-in user's own plan (the coach-comment endpoint isn't wired here yet).
        if (athleteId == null) {
            viewModelScope.launch {
                when (val c = commentRepository.getWeekComments(week)) {
                    is ApiResult.Success -> exerciseComments = c.data.groupBy { it.exerciseId ?: "" }
                    is ApiResult.Error -> Unit
                }
            }
        }
    }

    // Called when the comments sheet posts/deletes, so the card preview updates immediately.
    fun setCommentsFor(exerciseId: String, comments: List<ExerciseComment>) {
        exerciseComments = exerciseComments.toMutableMap().apply { put(exerciseId, comments) }
    }

    fun deleteSession(sessionId: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            isSaving = true
            when (planRepository.deleteSession(sessionId, athleteId)) {
                is ApiResult.Success -> {
                    weekSessions = weekSessions.filterNot { it.id == sessionId }
                    onDone(true)
                }
                is ApiResult.Error -> onDone(false)
            }
            isSaving = false
        }
    }

    // Reorders sessions within the selected day. Applied optimistically to local state
    // immediately (so the drag settles without waiting on the network), then persisted by
    // re-upserting each session whose orderIndex actually changed.
    fun reorderSessions(newOrder: List<PlannedSessionResponse>) {
        val reindexed = newOrder.mapIndexed { index, session -> session.copy(orderIndex = index) }
        val otherDays = weekSessions.filterNot { s -> reindexed.any { it.id == s.id } }
        weekSessions = otherDays + reindexed

        viewModelScope.launch {
            isSaving = true
            reindexed.forEach { session ->
                planRepository.upsertSession(session.toRequest(), athleteId)
            }
            isSaving = false
        }
    }

    // Creates an empty session on the selected day and upserts it, so a 0-session day flips into the
    // single-session inline editor. Used by the coach's athlete Plan tab (no SessionEditScreen push).
    fun createSession() {
        val newSession = PlannedSessionResponse(
            id = UUID.randomUUID().toString(),
            weekIdentifier = weekIdentifier,
            day = selectedDayName,
            orderIndex = sessionsForSelectedDay.size,
            title = "",
            exercises = emptyList(),
        )
        weekSessions = weekSessions + newSession
        viewModelScope.launch {
            isSaving = true
            planRepository.upsertSession(newSession.toRequest(), athleteId)
            isSaving = false
        }
    }

    // MARK: - Single-session inline editing
    //
    // Each of these replaces the session in local state (so the UI updates instantly) and
    // re-upserts just that session. Discrete edits (add/update/delete/reorder) persist
    // immediately; the title field commits on focus loss to avoid an upsert per keystroke.

    private fun persist(session: PlannedSessionResponse) {
        weekSessions = weekSessions.map { if (it.id == session.id) session else it }
        viewModelScope.launch {
            isSaving = true
            planRepository.upsertSession(session.toRequest(), athleteId)
            isSaving = false
        }
    }

    fun updateSessionTitle(session: PlannedSessionResponse, title: String) =
        persist(session.copy(title = title))

    fun addExercisesTo(session: PlannedSessionResponse, newOnes: List<ExerciseData>) =
        persist(session.copy(exercises = session.exercises.orEmpty() + newOnes))

    fun updateExerciseIn(session: PlannedSessionResponse, exercise: ExerciseData) =
        persist(session.copy(exercises = session.exercises.orEmpty().map { if (it.id == exercise.id) exercise else it }))

    fun deleteExerciseFrom(session: PlannedSessionResponse, exerciseId: String) =
        persist(session.copy(exercises = session.exercises.orEmpty().filterNot { it.id == exerciseId }))

    // Upserts both halves of a superset by id (mirrors CompoundExercisePairSheet's onSave) —
    // replaces each if already present, else appends.
    fun upsertPairIn(session: PlannedSessionResponse, a: ExerciseData, b: ExerciseData) {
        var list = session.exercises.orEmpty()
        list = if (list.any { it.id == a.id }) list.map { if (it.id == a.id) a else it } else list + a
        list = if (list.any { it.id == b.id }) list.map { if (it.id == b.id) b else it } else list + b
        persist(session.copy(exercises = list))
    }

    fun deletePairFrom(session: PlannedSessionResponse, aId: String, bId: String) =
        persist(session.copy(exercises = session.exercises.orEmpty().filterNot { it.id == aId || it.id == bId }))

    fun reorderExercisesIn(session: PlannedSessionResponse, newOrder: List<ExerciseData>) =
        persist(session.copy(exercises = newOrder))
}

private fun PlannedSessionResponse.toRequest(): PlannedSessionRequest = PlannedSessionRequest(
    id = id ?: UUID.randomUUID().toString(),
    weekIdentifier = weekIdentifier ?: "",
    day = day ?: "",
    orderIndex = orderIndex ?: 0,
    title = title ?: "",
    exercises = exercises.orEmpty(),
)
