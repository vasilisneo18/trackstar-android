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

// Mirrors MyWorkoutViewModel.SessionDisplay on iOS, minus the `.missed` case — an upcoming
// session on a past day just renders as upcoming for this pass (out of scope per the plan).
sealed interface SessionDisplay {
    data class Completed(val planned: PlannedSessionResponse, val completed: WorkoutSessionResponse) : SessionDisplay
    data class Upcoming(val planned: PlannedSessionResponse) : SessionDisplay
}

// Loads the selected week's plan (GET /api/plan?weekIdentifier=) and all completed sessions
// (GET /api/sessions), following the same AndroidViewModel(app)-owns-its-repos pattern as
// ProfileViewModel. Silently keeps stale data on ApiResult.Error, matching that precedent.
class WorkoutViewModel(app: Application) : AndroidViewModel(app) {

    private val planRepository = PlanRepository()
    private val sessionRepository = SessionRepository()
    private val commentRepository = CommentRepository()
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

    val displaySessions: List<SessionDisplay>
        get() = weekSessions
            .filter { it.day == selectedDayName }
            .sortedBy { it.orderIndex ?: 0 }
            .map { planned ->
                val match = completedSessions.firstOrNull { it.sessionData?.planSessionId == planned.id }
                if (match != null) SessionDisplay.Completed(planned, match) else SessionDisplay.Upcoming(planned)
            }

    init { fetch() }

    fun goToDate(date: LocalDate) {
        val weekChanged = weekIdentifierFor(date) != weekIdentifierFor(selectedDate)
        selectedDate = date
        if (weekChanged) fetch()
    }

    fun fetch() {
        viewModelScope.launch {
            isLoading = true
            val weekId = weekIdentifierFor(selectedDate)
            when (val result = planRepository.getPlan(weekId)) {
                is ApiResult.Success -> weekSessions = result.data
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

// Locale-based week-of-year, mirroring iOS's Calendar.current.weekIdentifier() (also
// locale-dependent, not forced ISO-8601 — see Extension+Date.swift). Both platforms derive
// week fields from the device's CLDR locale data, so this matches for the common case.
fun weekIdentifierFor(date: LocalDate): String {
    val weekFields = WeekFields.of(Locale.getDefault())
    val week = date.get(weekFields.weekOfWeekBasedYear())
    val year = date.get(weekFields.weekBasedYear())
    return "%d-W%02d".format(year, week)
}
