package com.vasilisneo.trackstar.ui.screens.main.stats

import com.vasilisneo.trackstar.data.api.ActualPerformance
import com.vasilisneo.trackstar.data.api.ExerciseSummary
import com.vasilisneo.trackstar.data.api.FrequencyValue
import com.vasilisneo.trackstar.data.api.ResistanceValue
import com.vasilisneo.trackstar.data.api.SetResult
import com.vasilisneo.trackstar.data.api.WorkoutSessionData
import com.vasilisneo.trackstar.data.api.WorkoutSessionResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Covers the pure WorkoutSessionResponse extension logic that drives the stats screen:
// localDate / totalSets / completedSets / totalVolume.
class SessionStatsTest {

    // --- fixture helpers -----------------------------------------------------

    private fun set(weight: String?, reps: Int?, completed: Boolean): SetResult = SetResult(
        id = "s", index = 0, label = "1", configuredRestSeconds = 60, setType = "normal",
        actualPerformance = if (completed) {
            ActualPerformance(
                frequencyValue = FrequencyValue(reps = reps),
                resistanceValue = ResistanceValue(weight = weight),
            )
        } else null,
    )

    private fun session(
        date: String? = null,
        epochSeconds: Double? = null,
        exercises: List<ExerciseSummary> = emptyList(),
    ) = WorkoutSessionResponse(
        id = "id", clientId = "c", date = date, durationSeconds = 0,
        sessionData = WorkoutSessionData(
            id = "sd", date = epochSeconds ?: 0.0, completedAt = 0.0, durationSeconds = 0,
            exercises = exercises, planSessionId = null, title = "Session",
        ),
    )

    private fun exercise(vararg sets: SetResult) = ExerciseSummary(id = "e", name = "Squat", sets = sets.toList())

    // --- localDate -----------------------------------------------------------

    @Test
    fun `localDate parses the top-level yyyy-MM-dd date`() {
        assertEquals(LocalDate.of(2026, 5, 20), session(date = "2026-05-20").localDate)
    }

    @Test
    fun `localDate falls back to epoch seconds when the string date is absent`() {
        val epoch = 1_770_000_000.0 // some instant
        val expected = Instant.ofEpochSecond(epoch.toLong()).atZone(ZoneId.systemDefault()).toLocalDate()
        assertEquals(expected, session(date = null, epochSeconds = epoch).localDate)
    }

    @Test
    fun `localDate prefers the string date over the epoch fallback`() {
        val s = session(date = "2026-01-02", epochSeconds = 1_770_000_000.0)
        assertEquals(LocalDate.of(2026, 1, 2), s.localDate)
    }

    @Test
    fun `localDate is null for an unparseable string and no epoch`() {
        // date = "not-a-date" and epoch 0 -> epoch 0 is a valid instant (1970), so null only when
        // there's genuinely nothing usable. A garbage string alone still falls back to epoch 0.
        val s = WorkoutSessionResponse(id = "i", clientId = "c", date = "garbage", durationSeconds = 0, sessionData = null)
        assertNull(s.localDate)
    }

    // --- totalSets / completedSets ------------------------------------------

    @Test
    fun `totalSets sums sets across all exercises`() {
        val s = session(
            exercises = listOf(
                exercise(set("100", 5, true), set("100", 5, true)),
                exercise(set("50", 8, false)),
            ),
        )
        assertEquals(3, s.totalSets)
    }

    @Test
    fun `completedSets counts only sets with actual performance`() {
        val s = session(
            exercises = listOf(
                exercise(set("100", 5, true), set("100", 5, false), set("100", 5, true)),
            ),
        )
        assertEquals(2, s.completedSets)
    }

    @Test
    fun `empty session has zero sets`() {
        val s = session(exercises = emptyList())
        assertEquals(0, s.totalSets)
        assertEquals(0, s.completedSets)
    }

    // --- totalVolume ---------------------------------------------------------

    @Test
    fun `totalVolume sums weight times reps over completed sets`() {
        val s = session(
            exercises = listOf(
                exercise(set("100", 5, true), set("80", 10, true)), // 500 + 800
            ),
        )
        assertEquals(1300.0, s.totalVolume, 0.0001)
    }

    @Test
    fun `totalVolume ignores incomplete sets`() {
        val s = session(
            exercises = listOf(
                exercise(set("100", 5, true), set("100", 5, false)), // only 500 counts
            ),
        )
        assertEquals(500.0, s.totalVolume, 0.0001)
    }

    @Test
    fun `totalVolume ignores sets without a numeric weight`() {
        val s = session(
            exercises = listOf(
                exercise(set(weight = null, reps = 10, completed = true)), // bodyweight -> 0
            ),
        )
        assertEquals(0.0, s.totalVolume, 0.0001)
    }
}
