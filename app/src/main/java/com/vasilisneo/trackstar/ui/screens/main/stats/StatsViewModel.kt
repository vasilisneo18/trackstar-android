package com.vasilisneo.trackstar.ui.screens.main.stats

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.api.WorkoutSessionResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.TokenStore
import com.vasilisneo.trackstar.data.workout.SessionRepository
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Ports iOS's WorkoutStatsView computed properties: every figure is derived on-device from the
// completed-session list (GET /api/sessions) — no dedicated stats endpoint, matching iOS's
// SessionHistoryStore-fed view. Kept as an AndroidViewModel(app) that owns its repo, like the
// other tab view models.
class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val sessionRepository = SessionRepository()
    private val tokenStore = TokenStore(app)

    var isLoading by mutableStateOf(false)
        private set
    var sessions by mutableStateOf<List<WorkoutSessionResponse>>(emptyList())
        private set

    val userInitials: String = com.vasilisneo.trackstar.ui.components.initialsFrom(
        listOfNotNull(tokenStore.firstName?.ifBlank { null }, tokenStore.lastName?.ifBlank { null })
            .joinToString(" ").ifBlank { null }
    )

    init { fetch() }

    fun fetch() {
        viewModelScope.launch {
            isLoading = true
            when (val r = sessionRepository.getSessions()) {
                is ApiResult.Success -> sessions = r.data
                is ApiResult.Error -> Unit // keep stale data on failure
            }
            isLoading = false
        }
    }

    // MARK: - Summary counts

    val allTimeCount: Int get() = sessions.size

    val thisWeekCount: Int
        get() {
            val start = startOfWeek(LocalDate.now())
            val end = start.plusDays(6)
            return sessions.count { val d = it.localDate; d != null && !d.isBefore(start) && !d.isAfter(end) }
        }

    val thisMonthCount: Int
        get() {
            val now = LocalDate.now()
            return sessions.count { it.localDate?.let { d -> d.year == now.year && d.month == now.month } ?: false }
        }

    // MARK: - Completion rate (completed sets / planned sets)

    val completionRate: Double
        get() {
            val total = sessions.sumOf { it.totalSets }
            val completed = sessions.sumOf { it.completedSets }
            return if (total > 0) completed.toDouble() / total * 100 else 0.0
        }

    // MARK: - Streak: consecutive weeks with >=1 session (current week skippable if still empty)

    val streak: Int
        get() {
            var weekStart = startOfWeek(LocalDate.now())
            var count = 0
            var maySkipCurrentWeek = true
            while (true) {
                val weekEnd = weekStart.plusDays(6)
                val hasSession = sessions.any { val d = it.localDate; d != null && !d.isBefore(weekStart) && !d.isAfter(weekEnd) }
                if (!hasSession) {
                    if (maySkipCurrentWeek && count == 0) {
                        maySkipCurrentWeek = false
                        weekStart = weekStart.minusWeeks(1)
                        continue
                    }
                    break
                }
                maySkipCurrentWeek = false
                count++
                weekStart = weekStart.minusWeeks(1)
            }
            return count
        }

    // MARK: - Weekly volume (last 8 weeks, in tonnes)

    data class WeeklyVolume(val weekStart: LocalDate, val tonnes: Double)

    val weeklyVolumes: List<WeeklyVolume>
        get() {
            val thisWeek = startOfWeek(LocalDate.now())
            val start = thisWeek.minusWeeks(7)
            val result = mutableListOf<WeeklyVolume>()
            var week = start
            while (!week.isAfter(thisWeek)) {
                val end = week.plusDays(6)
                val vol = sessions
                    .filter { val d = it.localDate; d != null && !d.isBefore(week) && !d.isAfter(end) }
                    .sumOf { it.totalVolume }
                result.add(WeeklyVolume(week, vol / 1000.0))
                week = week.plusWeeks(1)
            }
            return result
        }

    // MARK: - Personal records (per exercise: best weight / reps / duration)

    enum class PRMetric(val label: String, val unit: String) {
        WEIGHT("Weight", "kg"), REPS("Reps", "reps"), DURATION("Duration", "min")
    }

    data class PREntry(
        val name: String,
        val maxWeight: Double, val repsAtMaxWeight: Int, val weightDate: LocalDate?,
        val maxReps: Int, val weightAtMaxReps: Double, val repsDate: LocalDate?,
        val maxDuration: Double, val weightAtMaxDuration: Double, val durationDate: LocalDate?,
        val sessionCount: Int,
    )

    val personalRecords: List<PREntry>
        get() {
            data class Best(var primary: Double = 0.0, var secondary: Double = 0.0, var secondaryInt: Int = 0, var date: LocalDate? = null)
            val weightBest = HashMap<String, Best>()
            val repsBest = HashMap<String, Best>()
            val durationBest = HashMap<String, Best>()
            val counts = HashMap<String, Int>()

            for (session in sessions) {
                val date = session.localDate
                for (ex in session.sessionData?.exercises.orEmpty()) {
                    counts[ex.name] = (counts[ex.name] ?: 0) + 1
                    for (set in ex.sets) {
                        val p = set.actualPerformance ?: continue
                        val weight = p.resistanceValue.weight?.toDoubleOrNull() ?: 0.0
                        val reps = p.frequencyValue.reps ?: 0
                        val duration = (p.frequencyValue.duration?.leadingNumber() ?: 0.0) / 60.0 // minutes
                        if (weight > 0 && weight > (weightBest[ex.name]?.primary ?: 0.0)) {
                            weightBest[ex.name] = Best(primary = weight, secondaryInt = reps, date = date)
                        }
                        if (reps > 0 && reps > (repsBest[ex.name]?.secondaryInt ?: 0)) {
                            repsBest[ex.name] = Best(primary = reps.toDouble(), secondary = weight, secondaryInt = reps, date = date)
                        }
                        if (duration > 0 && duration > (durationBest[ex.name]?.primary ?: 0.0)) {
                            durationBest[ex.name] = Best(primary = duration, secondary = weight, date = date)
                        }
                    }
                }
            }

            return counts.keys.mapNotNull { name ->
                val wb = weightBest[name]; val rb = repsBest[name]; val db = durationBest[name]
                if (wb == null && rb == null && db == null) return@mapNotNull null
                PREntry(
                    name = name,
                    maxWeight = wb?.primary ?: 0.0, repsAtMaxWeight = wb?.secondaryInt ?: 0, weightDate = wb?.date,
                    maxReps = rb?.secondaryInt ?: 0, weightAtMaxReps = rb?.secondary ?: 0.0, repsDate = rb?.date,
                    maxDuration = db?.primary ?: 0.0, weightAtMaxDuration = db?.secondary ?: 0.0, durationDate = db?.date,
                    sessionCount = counts[name] ?: 0,
                )
            }.sortedWith(compareByDescending<PREntry> { it.sessionCount }.thenBy { it.name })
        }

    val availablePRMetrics: List<PRMetric>
        get() = buildList {
            if (personalRecords.any { it.maxWeight > 0 }) add(PRMetric.WEIGHT)
            if (personalRecords.any { it.maxReps > 0 }) add(PRMetric.REPS)
            if (personalRecords.any { it.maxDuration > 0 }) add(PRMetric.DURATION)
        }

    fun filteredPRs(metric: PRMetric): List<PREntry> = when (metric) {
        PRMetric.WEIGHT -> personalRecords.filter { it.maxWeight > 0 }
        PRMetric.REPS -> personalRecords.filter { it.maxReps > 0 }
        PRMetric.DURATION -> personalRecords.filter { it.maxDuration > 0 }
    }
}

// MARK: - Session extensions (iOS's WorkoutSession computed props over the Android DTO)

// Monday-based week start (the app's calendars render Mon–Sun), mirroring iOS's startOfWeek.
private fun startOfWeek(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE

// Prefer the "yyyy-MM-dd" top-level date; fall back to the epoch-seconds sessionData.date.
val WorkoutSessionResponse.localDate: LocalDate?
    get() = date?.let { runCatching { LocalDate.parse(it, isoDate) }.getOrNull() }
        ?: sessionData?.date?.let { Instant.ofEpochSecond(it.toLong()).atZone(ZoneId.systemDefault()).toLocalDate() }

val WorkoutSessionResponse.totalSets: Int
    get() = sessionData?.exercises.orEmpty().sumOf { it.sets.size }

val WorkoutSessionResponse.completedSets: Int
    get() = sessionData?.exercises.orEmpty().sumOf { ex -> ex.sets.count { it.actualPerformance != null } }

// Σ over completed sets of weight × reps (kg). Only weight×reps sets contribute, matching iOS.
val WorkoutSessionResponse.totalVolume: Double
    get() = sessionData?.exercises.orEmpty().sumOf { ex ->
        ex.sets.sumOf { set ->
            val p = set.actualPerformance ?: return@sumOf 0.0
            val weight = p.resistanceValue.weight?.toDoubleOrNull() ?: 0.0
            val reps = p.frequencyValue.reps ?: 0
            weight * reps
        }
    }

// Duration strings may be "60", "60 sec", "2 minute 30 sec" — grab the leading number (iOS stores
// a bare numeric string and does Double(d)); lenient here for the human-formatted case.
private fun String.leadingNumber(): Double =
    Regex("""\d+(\.\d+)?""").find(this)?.value?.toDoubleOrNull() ?: 0.0
