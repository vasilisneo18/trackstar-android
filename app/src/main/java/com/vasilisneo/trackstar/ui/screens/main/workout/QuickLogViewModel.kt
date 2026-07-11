package com.vasilisneo.trackstar.ui.screens.main.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vasilisneo.trackstar.data.api.ActualPerformance
import com.vasilisneo.trackstar.data.api.ExerciseData
import com.vasilisneo.trackstar.data.api.ExerciseSet
import com.vasilisneo.trackstar.data.api.ExerciseSummary
import com.vasilisneo.trackstar.data.api.FrequencyValue
import com.vasilisneo.trackstar.data.api.ResistanceValue
import com.vasilisneo.trackstar.data.api.SetResult
import com.vasilisneo.trackstar.data.api.WorkoutSessionData
import com.vasilisneo.trackstar.data.api.WorkoutSessionRequest
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.PlanRepository
import com.vasilisneo.trackstar.data.workout.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// State holder for the Quick Log surface — log an entire planned session at once without a live
// timer, mirroring iOS's QuickLogSheet. Each set is Logged, Skipped, or pending; a per-exercise
// "Log all as planned" bulk-logs the remaining sets with their planned values. On Finish it POSTs
// a WorkoutSession with durationSeconds = 0 (no live timing, same as iOS). Plain class (no
// Application) so it can be held in MainAppScreen; owner calls dispose() when done.
class QuickLogViewModel(
    private val date: LocalDate,
    private val sessionId: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val planRepository = PlanRepository()
    private val sessionRepository = SessionRepository()

    var isLoading by mutableStateOf(true)
        private set
    var loadError by mutableStateOf(false)
        private set
    var title by mutableStateOf("")
        private set
    var exercises by mutableStateOf<List<ExerciseData>>(emptyList())
        private set
    var isSaving by mutableStateOf(false)
        private set

    var loggedSets by mutableStateOf<Map<String, LoggedSet>>(emptyMap())
        private set
    var skippedSetIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var bulkLoggedExerciseIds by mutableStateOf<Set<String>>(emptySet())
        private set

    val totalSets: Int get() = exercises.sumOf { it.sets?.size ?: 0 }
    val handledSets: Int get() = loggedSets.size + skippedSetIds.size
    val isComplete: Boolean get() = totalSets > 0 && handledSets >= totalSets

    fun exerciseProgress(exercise: ExerciseData): Pair<Int, Int> {
        val sets = exercise.sets.orEmpty()
        val handled = sets.count { loggedSets.containsKey(it.id) || skippedSetIds.contains(it.id) }
        return handled to sets.size
    }

    fun hasUnhandledSets(exercise: ExerciseData): Boolean =
        exercise.sets.orEmpty().any { !loggedSets.containsKey(it.id) && !skippedSetIds.contains(it.id) }

    init {
        scope.launch {
            val weekId = weekIdentifierFor(date)
            when (val result = planRepository.getPlan(weekId)) {
                is ApiResult.Success -> {
                    val session = result.data.firstOrNull { it.id == sessionId }
                    if (session != null) {
                        title = session.title ?: "Workout"
                        exercises = session.exercises.orEmpty()
                    } else loadError = true
                }
                is ApiResult.Error -> loadError = true
            }
            isLoading = false
        }
    }

    fun logSet(set: ExerciseSet, reps: Int?, weight: String?, durationText: String?, distanceText: String?) {
        val setId = set.id ?: return
        val freq = when {
            durationText != null -> FrequencyValue(duration = durationText)
            distanceText != null -> FrequencyValue(distance = distanceText)
            reps != null -> FrequencyValue(reps = reps)
            else -> FrequencyValue()
        }
        val resistance = if (!weight.isNullOrBlank()) ResistanceValue(weight = weight) else ResistanceValue()
        skippedSetIds = skippedSetIds - setId
        loggedSets = loggedSets + (setId to LoggedSet(freq, resistance))
    }

    fun skipSet(set: ExerciseSet) {
        val setId = set.id ?: return
        loggedSets = loggedSets - setId
        skippedSetIds = skippedSetIds + setId
    }

    fun undoSet(set: ExerciseSet) {
        val setId = set.id ?: return
        loggedSets = loggedSets - setId
        skippedSetIds = skippedSetIds - setId
    }

    // Bulk-log every still-unhandled set in the exercise with its planned values.
    fun logExerciseAsPlanned(exercise: ExerciseData) {
        val newLogged = loggedSets.toMutableMap()
        exercise.sets.orEmpty().forEach { set ->
            val id = set.id ?: return@forEach
            if (newLogged.containsKey(id) || skippedSetIds.contains(id)) return@forEach
            newLogged[id] = LoggedSet(
                frequencyValue = set.frequencyValue ?: FrequencyValue(),
                resistanceValue = set.resistanceValue ?: ResistanceValue(),
            )
        }
        loggedSets = newLogged
        exercise.id?.let { bulkLoggedExerciseIds = bulkLoggedExerciseIds + it }
    }

    fun undoBulkLog(exercise: ExerciseData) {
        val newLogged = loggedSets.toMutableMap()
        exercise.sets.orEmpty().forEach { set -> set.id?.let { newLogged.remove(it) } }
        loggedSets = newLogged
        exercise.id?.let { bulkLoggedExerciseIds = bulkLoggedExerciseIds - it }
    }

    fun finish(onSaved: () -> Unit) {
        if (isSaving) return
        isSaving = true
        scope.launch {
            when (sessionRepository.saveSession(buildRequest())) {
                is ApiResult.Success -> onSaved()
                is ApiResult.Error -> Unit
            }
            isSaving = false
        }
    }

    private fun buildRequest(): WorkoutSessionRequest {
        val nowEpoch = System.currentTimeMillis() / 1000.0
        val dateEpoch = date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond().toDouble()

        val summaries = exercises.map { exercise ->
            val results = exercise.sets.orEmpty().mapIndexed { index, set ->
                val logged = set.id?.let { loggedSets[it] }
                SetResult(
                    id = set.id ?: "",
                    index = index + 1,
                    label = set.quickLogLabel(),
                    actualPerformance = logged?.let { ActualPerformance(it.frequencyValue, it.resistanceValue) },
                    configuredRestSeconds = set.restSeconds ?: 0,
                    setType = set.setType ?: "Normal",
                )
            }
            ExerciseSummary(
                id = exercise.id ?: "",
                name = exercise.name ?: "",
                sets = results,
                compoundGroupId = exercise.compoundGroupId,
            )
        }

        val sessionData = WorkoutSessionData(
            id = UUID.randomUUID().toString(),
            date = dateEpoch,
            completedAt = nowEpoch,
            durationSeconds = 0,
            exercises = summaries,
            planSessionId = sessionId,
            title = title,
        )
        return WorkoutSessionRequest(
            clientId = UUID.randomUUID().toString(),
            date = date.toString(),
            durationSeconds = 0,
            sessionData = sessionData,
        )
    }

    fun dispose() {
        scope.cancel()
    }
}

private fun ExerciseSet.quickLogLabel(): String {
    val freq = frequencyValue
    return when {
        freq?.reps != null -> {
            val max = repsMax
            if (max != null && max != freq.reps) "${minOf(freq.reps, max)}-${maxOf(freq.reps, max)} reps" else "${freq.reps} reps"
        }
        freq?.duration != null -> freq.duration
        freq?.distance != null -> freq.distance
        else -> ""
    }
}
