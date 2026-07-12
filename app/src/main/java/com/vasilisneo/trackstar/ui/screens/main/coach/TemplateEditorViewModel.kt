package com.vasilisneo.trackstar.ui.screens.main.coach

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.api.ExerciseComment
import com.vasilisneo.trackstar.data.api.ExerciseData
import com.vasilisneo.trackstar.data.api.PlannedSessionResponse
import com.vasilisneo.trackstar.data.api.TemplateSessionSyncRequest
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.TemplateRepository
import com.vasilisneo.trackstar.ui.screens.main.plan.SessionExerciseEditor
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

// Edits one template's weekly plan (/coach/templates/{id}/sessions). Templates are week-agnostic, so
// there's no week navigation — just a day selector over Mon–Sun. Sessions are held as
// PlannedSessionResponse so the plan's inline-editing components can be reused unchanged; each edit
// upserts that one session. Implements SessionExerciseEditor for SingleSessionInline.
class TemplateEditorViewModel(private val templateId: String) : ViewModel(), SessionExerciseEditor {

    private val repo = TemplateRepository()

    // Templates carry no coach/athlete comments.
    override val exerciseComments: Map<String, List<ExerciseComment>> = emptyMap()

    var selectedDay by mutableStateOf(LocalDate.now().dayOfWeek)
        private set
    var isLoading by mutableStateOf(false)
        private set
    private var sessions by mutableStateOf<List<PlannedSessionResponse>>(emptyList())

    val selectedDayName: String get() = selectedDay.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val weekDays: List<DayOfWeek> get() = DayOfWeek.entries // Mon–Sun
    val sessionsForSelectedDay: List<PlannedSessionResponse>
        get() = sessions.filter { it.day == selectedDayName }.sortedBy { it.orderIndex ?: 0 }

    fun hasExercises(day: DayOfWeek): Boolean {
        val name = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return sessions.any { it.day == name && it.exercises.orEmpty().isNotEmpty() }
    }

    fun goToDay(day: DayOfWeek) { selectedDay = day }

    init { fetch() }

    fun fetch() {
        viewModelScope.launch {
            isLoading = sessions.isEmpty()
            when (val r = repo.getTemplateSessions(templateId)) {
                is ApiResult.Success -> sessions = r.data.map {
                    PlannedSessionResponse(id = it.id, weekIdentifier = null, day = it.day, orderIndex = it.orderIndex, title = it.title, exercises = it.exercises)
                }
                is ApiResult.Error -> Unit
            }
            isLoading = false
        }
    }

    // MARK: - Session mutations

    fun createSession() {
        val newSession = PlannedSessionResponse(
            id = UUID.randomUUID().toString(), weekIdentifier = null, day = selectedDayName,
            orderIndex = sessionsForSelectedDay.size, title = "", exercises = emptyList(),
        )
        sessions = sessions + newSession
        upsert(newSession)
    }

    fun deleteSession(sessionId: String) {
        sessions = sessions.filterNot { it.id == sessionId }
        viewModelScope.launch { repo.deleteTemplateSession(templateId, sessionId) }
    }

    fun reorderSessions(newOrder: List<PlannedSessionResponse>) {
        val reindexed = newOrder.mapIndexed { i, s -> s.copy(orderIndex = i) }
        sessions = sessions.filterNot { s -> reindexed.any { it.id == s.id } } + reindexed
        reindexed.forEach { upsert(it) }
    }

    // MARK: - Exercise mutations (each re-upserts the one session)

    private fun persist(session: PlannedSessionResponse) {
        sessions = sessions.map { if (it.id == session.id) session else it }
        upsert(session)
    }

    override fun reorderExercisesIn(session: PlannedSessionResponse, newOrder: List<ExerciseData>) =
        persist(session.copy(exercises = newOrder))

    override fun updateSessionTitle(session: PlannedSessionResponse, title: String) =
        persist(session.copy(title = title))

    fun addExercisesTo(session: PlannedSessionResponse, newOnes: List<ExerciseData>) =
        persist(session.copy(exercises = session.exercises.orEmpty() + newOnes))

    fun updateExerciseIn(session: PlannedSessionResponse, exercise: ExerciseData) =
        persist(session.copy(exercises = session.exercises.orEmpty().map { if (it.id == exercise.id) exercise else it }))

    fun deleteExerciseFrom(session: PlannedSessionResponse, exerciseId: String) =
        persist(session.copy(exercises = session.exercises.orEmpty().filterNot { it.id == exerciseId }))

    fun upsertPairIn(session: PlannedSessionResponse, a: ExerciseData, b: ExerciseData) {
        var list = session.exercises.orEmpty()
        list = if (list.any { it.id == a.id }) list.map { if (it.id == a.id) a else it } else list + a
        list = if (list.any { it.id == b.id }) list.map { if (it.id == b.id) b else it } else list + b
        persist(session.copy(exercises = list))
    }

    fun deletePairFrom(session: PlannedSessionResponse, aId: String, bId: String) =
        persist(session.copy(exercises = session.exercises.orEmpty().filterNot { it.id == aId || it.id == bId }))

    private fun upsert(session: PlannedSessionResponse) {
        viewModelScope.launch {
            repo.upsertTemplateSession(
                templateId,
                TemplateSessionSyncRequest(
                    id = session.id ?: UUID.randomUUID().toString(),
                    day = session.day ?: selectedDayName,
                    orderIndex = session.orderIndex ?: 0,
                    title = session.title ?: "",
                    exercises = session.exercises.orEmpty(),
                ),
            )
        }
    }
}
