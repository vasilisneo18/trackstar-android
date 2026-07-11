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
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.TokenStore
import com.vasilisneo.trackstar.data.workout.CommentRepository
import com.vasilisneo.trackstar.data.workout.PlanRepository
import kotlinx.coroutines.launch
import java.util.UUID

// Drives one session's editor (new or existing). Loads its own copy of the week's plan by id
// rather than receiving the session as a nav argument (nav routes can't carry a full object
// graph) — same self-contained-fetch pattern as ActiveSessionViewModel. Edits accumulate purely
// locally (title/exercises); nothing hits the backend until save() — a single upsert — matching
// iOS's SessionEditView snapshot-on-open / commit-on-checkmark / discard-on-X behavior, unlike
// WeeklyPlanViewModel's other mutations which save immediately.
class SessionEditViewModel(
    app: Application,
    private val weekIdentifier: String,
    private val day: String,
    private val sessionId: String?,
) : AndroidViewModel(app) {

    private val planRepository = PlanRepository()
    private val commentRepository = CommentRepository()
    private val tokenStore = TokenStore(app)

    val weekId: String get() = weekIdentifier
    val authorName: String
        get() = listOfNotNull(tokenStore.firstName?.ifBlank { null }, tokenStore.lastName?.ifBlank { null }).joinToString(" ").ifBlank { "You" }
    val authorRole: String
        get() = tokenStore.role ?: "athlete"

    var exerciseComments by mutableStateOf<Map<String, List<ExerciseComment>>>(emptyMap())
        private set

    var isLoading by mutableStateOf(sessionId != null)
        private set
    var isSaving by mutableStateOf(false)
        private set

    var title by mutableStateOf("")
        private set
    var exercises by mutableStateOf<List<ExerciseData>>(emptyList())
        private set

    private var orderIndex = 0

    val isNew: Boolean get() = sessionId == null

    init {
        viewModelScope.launch {
            when (val result = planRepository.getPlan(weekIdentifier)) {
                is ApiResult.Success -> {
                    if (sessionId != null) {
                        val session = result.data.firstOrNull { it.id == sessionId }
                        if (session != null) {
                            title = session.title ?: ""
                            exercises = session.exercises.orEmpty()
                            orderIndex = session.orderIndex ?: 0
                        }
                    } else {
                        orderIndex = result.data.count { it.day == day }
                    }
                }
                is ApiResult.Error -> Unit
            }
            isLoading = false
        }
        // Comments load independently — best-effort previews, never block the editor.
        viewModelScope.launch {
            when (val c = commentRepository.getWeekComments(weekIdentifier)) {
                is ApiResult.Success -> exerciseComments = c.data.groupBy { it.exerciseId ?: "" }
                is ApiResult.Error -> Unit
            }
        }
    }

    fun setCommentsFor(exerciseId: String, comments: List<ExerciseComment>) {
        exerciseComments = exerciseComments.toMutableMap().apply { put(exerciseId, comments) }
    }

    fun updateTitle(newTitle: String) { title = newTitle }

    fun addExercises(newOnes: List<ExerciseData>) { exercises = exercises + newOnes }

    fun updateExercise(exercise: ExerciseData) {
        exercises = exercises.map { if (it.id == exercise.id) exercise else it }
    }

    fun deleteExercise(exerciseId: String) {
        exercises = exercises.filterNot { it.id == exerciseId }
    }

    // Upserts both halves of a superset by id (matching CompoundExercisePairSheet's onSave on
    // iOS) — replaces each if already present, else appends.
    fun upsertExercisePair(a: ExerciseData, b: ExerciseData) {
        var updated = exercises
        updated = if (updated.any { it.id == a.id }) updated.map { if (it.id == a.id) a else it } else updated + a
        updated = if (updated.any { it.id == b.id }) updated.map { if (it.id == b.id) b else it } else updated + b
        exercises = updated
    }

    fun deleteExercisePair(aId: String, bId: String) {
        exercises = exercises.filterNot { it.id == aId || it.id == bId }
    }

    // Applied purely locally, like every other edit here — persisted on save().
    fun reorderExercises(newOrder: List<ExerciseData>) {
        exercises = newOrder
    }

    // No-op (empty title + no exercises) is treated as "nothing to save" rather than pushing an
    // empty session, matching iOS's `hasContent` guard in SessionEditView's checkmark handler.
    fun save(onDone: (Boolean) -> Unit) {
        val hasContent = title.isNotBlank() || exercises.isNotEmpty()
        if (!hasContent) { onDone(true); return }
        if (isSaving) return
        isSaving = true
        viewModelScope.launch {
            val request = PlannedSessionRequest(
                id = sessionId ?: UUID.randomUUID().toString(),
                weekIdentifier = weekIdentifier,
                day = day,
                orderIndex = orderIndex,
                title = title,
                exercises = exercises,
            )
            when (planRepository.upsertSession(request)) {
                is ApiResult.Success -> onDone(true)
                is ApiResult.Error -> onDone(false)
            }
            isSaving = false
        }
    }
}

// Mirrors ExerciseData.isConfigured on iOS: has sets, and at least one set has a meaningful
// value — either resistance is None (nothing further needed) or the frequency/resistance value
// is actually filled in. A freshly-picked-from-library stub (reps=0, weight="") is NOT
// configured, so it renders as a tap-to-configure row instead of a normal exercise card.
fun ExerciseData.isConfigured(): Boolean {
    val sets = sets.orEmpty()
    if (sets.isEmpty()) return false
    return sets.any { set ->
        val resistance = set.resistanceValue
        val resistanceIsNone = resistance?.weight.isNullOrBlank() && resistance?.bandLevel.isNullOrBlank()
        if (resistanceIsNone && resistanceType == "None") return@any true
        val freq = set.frequencyValue
        when {
            freq?.reps != null -> freq.reps > 0 || !resistance?.weight.isNullOrBlank()
            freq?.duration != null -> freq.duration.isNotBlank()
            freq?.distance != null -> freq.distance.isNotBlank()
            else -> false
        }
    }
}
