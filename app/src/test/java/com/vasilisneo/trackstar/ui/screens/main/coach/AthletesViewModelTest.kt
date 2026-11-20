package com.vasilisneo.trackstar.ui.screens.main.coach

import androidx.test.core.app.ApplicationProvider
import com.vasilisneo.trackstar.data.api.AthleteSummaryResponse
import com.vasilisneo.trackstar.data.api.MessageResponse
import com.vasilisneo.trackstar.data.api.ProfileResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.AthleteRepository
import com.vasilisneo.trackstar.ui.screens.main.workout.weekIdentifierFor
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
        private val summaries: List<AthleteSummaryResponse> = emptyList(),
    ) : AthleteRepository() {
        val removed = mutableListOf<String>()
        /** The week the summaries were asked for (week identifier, Monday). */
        var summaryWeek: Pair<String, String>? = null
        override suspend fun getAthletes() = ApiResult.Success(roster)
        override suspend fun getAthleteSummaries(weekIdentifier: String, weekStart: String): ApiResult<List<AthleteSummaryResponse>> {
            summaryWeek = weekIdentifier to weekStart
            return ApiResult.Success(summaries)
        }
        override suspend fun cachedAthleteSummaries(weekIdentifier: String): List<AthleteSummaryResponse>? = null
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

    private fun vm(repo: FakeAthleteRepo) = AthletesViewModel(ApplicationProvider.getApplicationContext(), repo)

    @Test fun `fetch loads the roster`() = runTest(dispatcher.scheduler) {
        val vm = vm(FakeAthleteRepo(listOf(profile("a1"), profile("a2"))))
        advanceUntilIdle()
        assertEquals(listOf("a1", "a2"), vm.athletes.map { it.id })
    }

    // The week's totals come from one server call for the whole roster (the server counts them).
    @Test fun `weekly summaries come from this week's batch call`() = runTest(dispatcher.scheduler) {
        val repo = FakeAthleteRepo(
            roster = listOf(profile("a1"), profile("a2")),
            summaries = listOf(
                AthleteSummaryResponse(athleteId = "a1", plannedCount = 2, completedCount = 1, hasSessionToday = true),
                AthleteSummaryResponse(athleteId = "a2", plannedCount = 3, completedCount = 3),
            ),
        )
        val vm = vm(repo)
        advanceUntilIdle()
        assertEquals(weekIdentifierFor(monday) to monday.toString(), repo.summaryWeek)
        val a1 = vm.weeklySummaries["a1"]!!
        assertEquals(2, a1.plannedCount)
        assertEquals(1, a1.completedCount)
        assertTrue(a1.hasSessionToday)
        assertEquals(4, vm.finishedSessionsCount)
        assertEquals(5, vm.plannedSessionsCount)
        assertEquals(1, vm.athletesWithSessionTodayCount)
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
