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
import com.vasilisneo.trackstar.data.workout.ExerciseDisplayUnit
import com.vasilisneo.trackstar.data.workout.PlanRepository
import com.vasilisneo.trackstar.data.workout.SessionRepository
import com.vasilisneo.trackstar.data.workout.groupedForDisplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// A logged result for one set — mirrors iOS's ActualPerformance, plus a completion flag so the
// UI can distinguish "logged with zero reps" from "not logged yet".
data class LoggedSet(
    val frequencyValue: FrequencyValue,
    val resistanceValue: ResistanceValue,
)

// Drives the full-screen active-session flow: loads the planned session's exercises, tracks
// per-set logged performance plus a foreground-only rest countdown (kotlinx.coroutines delay —
// no background robustness this pass, matching the plan's scope cut), and POSTs the finished
// session on Finish.
//
// A plain state holder (not a ViewModel) so it can be hoisted to MainAppScreen and *survive
// being minimized* — the session must keep running (timer, rest countdown) while the full-screen
// UI is dismissed to the mini-bar, exactly like iOS's ActiveSessionViewModel outliving its
// fullScreenCover. The repos use the NetworkClient singleton, so no Application/Context is needed.
// The owner must call dispose() when the session ends or is discarded to cancel the scope.
class ActiveSessionViewModel(
    private val date: LocalDate,
    private val sessionId: String,
) {

    // The planned session this active session was started from — used by the Workout tab to
    // swap the Start Session button for an in-progress card on the matching day.
    val planSessionId: String get() = sessionId

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

    var elapsedSeconds by mutableStateOf(0)
        private set
    var activeSetId by mutableStateOf<String?>(null)
        private set
    var loggedSets by mutableStateOf<Map<String, LoggedSet>>(emptyMap())
        private set
    var isResting by mutableStateOf(false)
        private set
    var restSecondsRemaining by mutableStateOf(0)
        private set
    var isSaving by mutableStateOf(false)
        private set

    val totalSets: Int get() = exercises.sumOf { it.sets?.size ?: 0 }
    val completedSets: Int get() = loggedSets.size
    val isSessionComplete: Boolean get() = totalSets > 0 && completedSets >= totalSets

    // Single-UNIT focus, mirroring ActiveSessionView on iOS: work through one exercise (or
    // superset pair) at a time rather than a flat scrollable list. A "unit" is either a single
    // exercise or two exercises sharing a compoundGroupId (see ExerciseGrouping.kt) — supersets
    // are worked through round-by-round via completeCompoundRound, everything else per-set via
    // completeSet.
    fun isExerciseComplete(exercise: ExerciseData): Boolean {
        val sets = exercise.sets.orEmpty()
        return sets.isNotEmpty() && sets.all { loggedSets.containsKey(it.id) }
    }

    fun exerciseProgress(exercise: ExerciseData): Pair<Int, Int> {
        val sets = exercise.sets.orEmpty()
        return sets.count { loggedSets.containsKey(it.id) } to sets.size
    }

    val units: List<ExerciseDisplayUnit>
        get() = exercises.groupedForDisplay()

    fun isUnitComplete(unit: ExerciseDisplayUnit): Boolean = when (unit) {
        is ExerciseDisplayUnit.Single -> isExerciseComplete(unit.exercise)
        is ExerciseDisplayUnit.Pair -> isExerciseComplete(unit.a) && isExerciseComplete(unit.b)
    }

    fun unitProgress(unit: ExerciseDisplayUnit): Pair<Int, Int> = when (unit) {
        is ExerciseDisplayUnit.Single -> exerciseProgress(unit.exercise)
        is ExerciseDisplayUnit.Pair -> {
            val (doneA, totalA) = exerciseProgress(unit.a)
            val (doneB, totalB) = exerciseProgress(unit.b)
            (doneA + doneB) to (totalA + totalB)
        }
    }

    val currentUnitIndex: Int?
        get() = units.indexOfFirst { !isUnitComplete(it) }.takeIf { it >= 0 }

    val currentUnit: ExerciseDisplayUnit?
        get() = currentUnitIndex?.let { units[it] }

    // Only meaningful when currentUnit is a Single — kept for the single-exercise UI path.
    val currentExercise: ExerciseData?
        get() = (currentUnit as? ExerciseDisplayUnit.Single)?.exercise

    val currentSet: ExerciseSet?
        get() = currentExercise?.sets.orEmpty().firstOrNull { !loggedSets.containsKey(it.id) }

    // Index of the first not-yet-fully-logged round in a superset pair — mirrors
    // currentCompoundRoundIndex on iOS. Null once every round is logged.
    fun currentCompoundRoundIndex(a: ExerciseData, b: ExerciseData): Int? {
        val rounds = maxOf(a.sets.orEmpty().size, b.sets.orEmpty().size)
        for (i in 0 until rounds) {
            val doneA = a.sets.orEmpty().getOrNull(i)?.let { loggedSets.containsKey(it.id) } ?: true
            val doneB = b.sets.orEmpty().getOrNull(i)?.let { loggedSets.containsKey(it.id) } ?: true
            if (!(doneA && doneB)) return i
        }
        return null
    }

    val upNextUnits: List<ExerciseDisplayUnit>
        get() = currentUnitIndex?.let { units.drop(it + 1) } ?: emptyList()

    val completedUnits: List<ExerciseDisplayUnit>
        get() = units.filter { isUnitComplete(it) }

    private var restJob: Job? = null

    init {
        scope.launch {
            val weekId = weekIdentifierFor(date)
            when (val result = planRepository.getPlan(weekId)) {
                is ApiResult.Success -> {
                    val session = result.data.firstOrNull { it.id == sessionId }
                    if (session != null) {
                        title = session.title ?: "Workout"
                        exercises = session.exercises.orEmpty()
                    } else {
                        loadError = true
                    }
                }
                is ApiResult.Error -> loadError = true
            }
            isLoading = false
        }
        startSessionTimer()
    }

    private fun startSessionTimer() {
        scope.launch {
            while (true) {
                delay(1000)
                elapsedSeconds += 1
            }
        }
    }

    fun startSet(set: ExerciseSet) {
        if (activeSetId != null) return
        activeSetId = set.id
    }

    fun cancelActiveSet() {
        activeSetId = null
    }

    fun completeSet(set: ExerciseSet, reps: Int?, weight: String?, durationText: String?, distanceText: String? = null) {
        val setId = set.id ?: return
        if (loggedSets.containsKey(setId)) return
        activeSetId = null

        val freq = when {
            durationText != null -> FrequencyValue(duration = durationText)
            distanceText != null -> FrequencyValue(distance = distanceText)
            reps != null -> FrequencyValue(reps = reps)
            else -> FrequencyValue()
        }
        val resistance = if (!weight.isNullOrBlank()) ResistanceValue(weight = weight) else ResistanceValue()

        loggedSets = loggedSets + (setId to LoggedSet(freq, resistance))

        val restSeconds = set.restSeconds ?: 0
        if (restSeconds > 0 && !isSessionComplete) {
            startRestTimer(restSeconds)
        }
    }

    // Logs both halves of a superset round at once — mirrors completeCompoundRound on iOS.
    // Rest is shared: a single timer for max(setA.restSeconds, setB.restSeconds), not two.
    fun completeCompoundRound(setA: ExerciseSet, repsA: Int?, weightA: String?, setB: ExerciseSet, repsB: Int?, weightB: String?) {
        val idA = setA.id ?: return
        val idB = setB.id ?: return
        if (loggedSets.containsKey(idA) || loggedSets.containsKey(idB)) return
        activeSetId = null

        loggedSets = loggedSets +
            (idA to LoggedSet(FrequencyValue(reps = repsA), if (!weightA.isNullOrBlank()) ResistanceValue(weight = weightA) else ResistanceValue())) +
            (idB to LoggedSet(FrequencyValue(reps = repsB), if (!weightB.isNullOrBlank()) ResistanceValue(weight = weightB) else ResistanceValue()))

        val restSeconds = maxOf(setA.restSeconds ?: 0, setB.restSeconds ?: 0)
        if (restSeconds > 0 && !isSessionComplete) {
            startRestTimer(restSeconds)
        }
    }

    // --- Duration timed-set flow (Plank etc.), mirroring iOS's durationHelperCard phases ---
    // Ready (durationSet set) -> Countdown (durationCountdown != null) -> Running (durationRunning)
    // -> Done (durationDone). A duration set is logged from this flow, not the LogSetSheet.
    var durationSet by mutableStateOf<ExerciseSet?>(null)
        private set
    var durationCountdown by mutableStateOf<Int?>(null)
        private set
    var durationRunning by mutableStateOf(false)
        private set
    var durationElapsedSeconds by mutableStateOf(0)
        private set
    var durationDone by mutableStateOf(false)
        private set
    var startDelay by mutableStateOf(5)
        private set

    private var durationJob: Job? = null

    fun openDurationHelper(set: ExerciseSet) {
        if (durationSet != null) return
        durationJob?.cancel()
        durationSet = set
        durationCountdown = null
        durationRunning = false
        durationElapsedSeconds = 0
        durationDone = false
        activeSetId = set.id
    }

    fun chooseStartDelay(seconds: Int) { startDelay = seconds }

    fun beginDurationCountdown() {
        val set = durationSet ?: return
        durationJob?.cancel()
        durationJob = scope.launch {
            var c = startDelay
            durationCountdown = c
            while (c > 0) {
                delay(1000)
                c -= 1
                durationCountdown = c
            }
            durationCountdown = null
            startDurationRunning(set)
        }
    }

    private fun startDurationRunning(set: ExerciseSet) {
        val target = parseDurationSeconds(set.frequencyValue?.duration ?: "")
        durationRunning = true
        durationElapsedSeconds = 0
        durationJob?.cancel()
        durationJob = scope.launch {
            while (true) {
                delay(1000)
                durationElapsedSeconds += 1
                if (target > 0 && durationElapsedSeconds >= target) {
                    durationRunning = false
                    durationDone = true
                    break
                }
            }
        }
    }

    // Manual "Stop & Log" — freeze at the current elapsed and move to the Done phase.
    fun stopDurationAndLog() {
        durationJob?.cancel()
        durationRunning = false
        durationDone = true
    }

    fun logDuration() {
        val set = durationSet ?: return
        val target = parseDurationSeconds(set.frequencyValue?.duration ?: "")
        val seconds = if (durationElapsedSeconds > 0) durationElapsedSeconds else target
        val text = formatDurationSecs(seconds)
        resetDurationState()
        completeSet(set, reps = null, weight = null, durationText = text, distanceText = null)
    }

    fun cancelDurationHelper() {
        resetDurationState()
        activeSetId = null
    }

    private fun resetDurationState() {
        durationJob?.cancel()
        durationJob = null
        durationSet = null
        durationCountdown = null
        durationRunning = false
        durationDone = false
        durationElapsedSeconds = 0
    }

    fun skipRest() {
        restJob?.cancel()
        restJob = null
        isResting = false
        restSecondsRemaining = 0
    }

    fun addMinuteToRest() {
        restSecondsRemaining = minOf(restSecondsRemaining + 60, 3599)
    }

    private fun startRestTimer(seconds: Int) {
        restJob?.cancel()
        isResting = true
        restSecondsRemaining = seconds
        restJob = scope.launch {
            while (restSecondsRemaining > 0) {
                delay(1000)
                restSecondsRemaining -= 1
            }
            isResting = false
        }
    }

    fun finish(onSaved: () -> Unit) {
        if (isSaving) return
        isSaving = true
        scope.launch {
            val request = buildRequest()
            when (sessionRepository.saveSession(request)) {
                is ApiResult.Success -> onSaved()
                is ApiResult.Error -> Unit // stay on screen so the user can retry Finish
            }
            isSaving = false
        }
    }

    // Cancels the session/rest/timer coroutines. The owner (MainAppScreen) calls this when the
    // session is finished or discarded so nothing leaks after the holder is dropped.
    fun dispose() {
        scope.cancel()
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
                    label = set.sessionLabel(),
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
            durationSeconds = elapsedSeconds,
            exercises = summaries,
            planSessionId = sessionId,
            title = title,
        )

        return WorkoutSessionRequest(
            clientId = UUID.randomUUID().toString(),
            date = date.toString(),
            durationSeconds = elapsedSeconds,
            sessionData = sessionData,
        )
    }
}

// e.g. "8-10 reps" or "30 sec" — mirrors ExerciseSet.sessionLabel on iOS (label shown on the
// logged SetResult).
private fun ExerciseSet.sessionLabel(): String {
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

// e.g. "8-10" or "30 sec" — the REPS/DURATION column value, without the "reps" suffix (mirrors
// ExerciseSet.repsRangeDisplay/repsString(for:actual:) on iOS).
fun ExerciseSet.repsOrDurationText(): String {
    val freq = frequencyValue
    return when {
        freq?.reps != null -> {
            val max = repsMax
            if (max != null && max != freq.reps) "${minOf(freq.reps, max)}-${maxOf(freq.reps, max)}" else "${freq.reps}"
        }
        freq?.duration != null -> freq.duration
        freq?.distance != null -> freq.distance
        else -> ""
    }
}

// Parses a planned duration string into seconds. Handles the TimerPicker formats iOS writes
// ("45 sec", "1 minute 30 sec", "1 hour 5 minute"), short suffixes ("30s", "2m"), colon forms
// ("M:SS", "H:MM:SS"), and a bare integer (seconds). Mirrors iOS's parseDurationSeconds.
fun parseDurationSeconds(raw: String): Int {
    val s = raw.trim().lowercase()
    if (s.isEmpty()) return 0
    if (s.contains("sec") || s.contains("min") || s.contains("hour")) {
        var total = 0
        val tokens = s.split(" ")
        var i = 0
        while (i < tokens.size) {
            val n = tokens[i].toIntOrNull()
            if (n != null) {
                val unit = tokens.getOrNull(i + 1) ?: ""
                when {
                    unit.startsWith("hour") -> total += n * 3600
                    unit.startsWith("min") -> total += n * 60
                    unit.startsWith("sec") -> total += n
                }
                i += 2
            } else i += 1
        }
        return total
    }
    if (s.endsWith("s")) s.dropLast(1).toIntOrNull()?.let { return it }
    if (s.endsWith("m")) s.dropLast(1).toIntOrNull()?.let { return it * 60 }
    val parts = s.split(":")
    if (parts.size == 2) {
        val m = parts[0].toIntOrNull(); val sec = parts[1].toIntOrNull()
        if (m != null && sec != null) return m * 60 + sec
    }
    if (parts.size == 3) {
        val h = parts[0].toIntOrNull(); val m = parts[1].toIntOrNull(); val sec = parts[2].toIntOrNull()
        if (h != null && m != null && sec != null) return h * 3600 + m * 60 + sec
    }
    return s.toIntOrNull() ?: 0
}

fun formatDurationSecs(seconds: Int): String =
    if (seconds >= 60) "%d:%02d".format(seconds / 60, seconds % 60) else "${seconds}s"

// "60 kg" or "—" — mirrors ExerciseSet.weightString(for:actual:) on iOS.
fun ExerciseSet.weightText(): String {
    val weight = resistanceValue?.weight
    val band = resistanceValue?.bandLevel
    return when {
        !weight.isNullOrBlank() -> "$weight kg"
        !band.isNullOrBlank() -> band
        else -> "—"
    }
}
