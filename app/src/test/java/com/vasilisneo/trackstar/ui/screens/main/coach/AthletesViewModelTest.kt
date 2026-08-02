package com.vasilisneo.trackstar.ui.screens.main.coach

import androidx.test.core.app.ApplicationProvider
import com.vasilisneo.trackstar.data.api.MessageResponse
import com.vasilisneo.trackstar.data.api.PlannedSessionResponse
import com.vasilisneo.trackstar.data.api.ProfileResponse
import com.vasilisneo.trackstar.data.api.WorkoutSessionData
import com.vasilisneo.trackstar.data.api.WorkoutSessionResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.AthleteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AthletesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeAthleteRepo(
        private val roster: List<ProfileResponse>,
        private val plans: Map<String, List<PlannedSessionResponse>> = emptyMap(),
        private val sessions: Map<String, List<WorkoutSessionResponse>> = emptyMap(),
    ) : AthleteRepository() {
        val removed = mutableListOf<String>()
        override suspend fun getAthletes() = ApiResult.Success(roster)
        override suspend fun getAthletePlan(athleteId: String, weekIdentifier: String) =
            ApiResult.Success(plans[athleteId].orEmpty())
        override suspend fun getAthleteSessions(athleteId: String) =
            ApiResult.Success(sessions[athleteId].orEmpty())
        override suspend fun removeAthlete(athleteId: String): ApiResult<MessageResponse> {
            removed.add(athleteId)
            return ApiResult.Success(MessageResponse("ok"))
        }
    }

    private val monday: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun profile(id: String, first: String? = "First", last: String? = "Last", email: String? = "$id@x.com") =
        ProfileResponse(
            id = id, email = email, firstName = first, lastName = last, age = null, role = "athlete",
            gender = null, height = null, weight = null, targetWeight = null, country = null,
            coachName = null, coachingSince = null,
        )

    private fun planned(id: String) =
        PlannedSessionResponse(id = id, weekIdentifier = "w", day = "Monday", orderIndex = 0, title = "S", exercises = emptyList())

    private fun sessionOn(date: LocalDate) = WorkoutSessionResponse(
        id = "c", clientId = null, date = date.toString(), durationSeconds = 0,
        sessionData = WorkoutSessionData(
            id = "sd", date = 0.0, completedAt = 0.0, durationSeconds = 0,
            exercises = emptyList(), planSessionId = null, title = "S",
        ),
    )

    private fun vm(repo: FakeAthleteRepo) = AthletesViewModel(ApplicationProvider.getApplicationContext(), repo)

    @Test fun `fetch loads the roster`() = runTest(dispatcher.scheduler) {
        val vm = vm(FakeAthleteRepo(listOf(profile("a1"), profile("a2"))))
        advanceUntilIdle()
        assertEquals(listOf("a1", "a2"), vm.athletes.map { it.id })
    }

    @Test fun `weekly summary counts planned sessions and this-week completions`() = runTest(dispatcher.scheduler) {
        val repo = FakeAthleteRepo(
            roster = listOf(profile("a1")),
            plans = mapOf("a1" to listOf(planned("p1"), planned("p2"))),          // planned = 2
            sessions = mapOf(
                "a1" to listOf(
                    sessionOn(monday),                 // this week
                    sessionOn(monday.plusDays(2)),     // this week
                    sessionOn(monday.minusDays(1)),    // last week -> excluded
                ),
            ),
        )
        val vm = vm(repo)
        advanceUntilIdle()
        val summary = vm.weeklySummaries["a1"]!!
        assertEquals(2, summary.plannedCount)
        assertEquals(2, summary.completedCount)
    }

    @Test fun `removeAthlete optimistically drops from the roster and calls the repo`() = runTest(dispatcher.scheduler) {
        val repo = FakeAthleteRepo(listOf(profile("a1"), profile("a2")))
        val vm = vm(repo)
        advanceUntilIdle()
        vm.removeAthlete("a1")
        assertEquals(listOf("a2"), vm.athletes.map { it.id })
        advanceUntilIdle()
        assertTrue(repo.removed.contains("a1"))
    }

    // --- pure profile extensions --------------------------------------------

    @Test fun `fullName joins first and last, falling back to email then Athlete`() {
        assertEquals("First Last", profile("a1", "First", "Last").fullName)
        assertEquals("a2@x.com", profile("a2", first = null, last = null, email = "a2@x.com").fullName)
        assertEquals("Athlete", profile("a3", first = null, last = null, email = null).fullName)
    }

    @Test fun `athleteInitials uses first and last, else a question mark`() {
        assertEquals("FL", profile("a1", "First", "Last").athleteInitials)
        assertEquals("?", profile("a2", first = null, last = null, email = "a2@x.com").athleteInitials)
    }
}
