package com.vasilisneo.trackstar.ui.screens.main.stats

import androidx.test.core.app.ApplicationProvider
import com.vasilisneo.trackstar.data.api.ActualPerformance
import com.vasilisneo.trackstar.data.api.ExerciseSummary
import com.vasilisneo.trackstar.data.api.FrequencyValue
import com.vasilisneo.trackstar.data.api.ResistanceValue
import com.vasilisneo.trackstar.data.api.SetResult
import com.vasilisneo.trackstar.data.api.WorkoutSessionData
import com.vasilisneo.trackstar.data.api.WorkoutSessionResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

// Proof-of-pattern: StatsViewModel now takes its SessionRepository via the constructor, so a fake
// feeds a controlled session list and we assert the derived stats (counts, completion rate, streak,
// weekly volume) with no network. viewModelScope runs on an injected test dispatcher.
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class StatsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeSessionRepository(private val data: List<WorkoutSessionResponse>) : SessionRepository() {
        override suspend fun getSessions(): ApiResult<List<WorkoutSessionResponse>> = ApiResult.Success(data)
    }

    private fun set(weight: String?, reps: Int?, completed: Boolean) = SetResult(
        id = "s", index = 0, label = "1", configuredRestSeconds = 60, setType = "normal",
        actualPerformance = if (completed) {
            ActualPerformance(FrequencyValue(reps = reps), ResistanceValue(weight = weight))
        } else null,
    )

    private fun session(date: LocalDate, sets: List<SetResult>) = WorkoutSessionResponse(
        id = "i", clientId = null, date = date.toString(), durationSeconds = 0,
        sessionData = WorkoutSessionData(
            id = "sd", date = 0.0, completedAt = 0.0, durationSeconds = 0,
            exercises = listOf(ExerciseSummary(id = "e", name = "Squat", sets = sets)),
            planSessionId = null, title = "S",
        ),
    )

    private fun vmWith(sessions: List<WorkoutSessionResponse>) =
        StatsViewModel(ApplicationProvider.getApplicationContext(), FakeSessionRepository(sessions))

    @Test fun `allTimeCount is the number of sessions`() = runTest(dispatcher.scheduler) {
        val vm = vmWith(listOf(session(LocalDate.now(), emptyList()), session(LocalDate.now(), emptyList())))
        advanceUntilIdle()
        assertEquals(2, vm.allTimeCount)
    }

    @Test fun `thisWeekCount counts only sessions in the current week`() = runTest(dispatcher.scheduler) {
        val vm = vmWith(
            listOf(
                session(LocalDate.now(), emptyList()),                 // this week
                session(LocalDate.now().minusWeeks(2), emptyList()),   // two weeks ago
            ),
        )
        advanceUntilIdle()
        assertEquals(1, vm.thisWeekCount)
    }

    @Test fun `thisMonthCount counts only sessions in the current month`() = runTest(dispatcher.scheduler) {
        val vm = vmWith(
            listOf(
                session(LocalDate.now(), emptyList()),
                session(LocalDate.now().minusMonths(2), emptyList()),
            ),
        )
        advanceUntilIdle()
        assertEquals(1, vm.thisMonthCount)
    }

    @Test fun `completionRate is completed over total sets as a percentage`() = runTest(dispatcher.scheduler) {
        val vm = vmWith(
            listOf(
                session(LocalDate.now(), listOf(set("50", 5, true), set("50", 5, true), set("50", 5, false), set("50", 5, false))),
            ),
        )
        advanceUntilIdle()
        assertEquals(50.0, vm.completionRate, 0.0001)
    }

    @Test fun `completionRate is zero when there are no sets`() = runTest(dispatcher.scheduler) {
        val vm = vmWith(listOf(session(LocalDate.now(), emptyList())))
        advanceUntilIdle()
        assertEquals(0.0, vm.completionRate, 0.0001)
    }

    @Test fun `streak counts consecutive weeks with a session`() = runTest(dispatcher.scheduler) {
        val vm = vmWith(
            listOf(
                session(LocalDate.now(), emptyList()),               // this week
                session(LocalDate.now().minusWeeks(1), emptyList()), // last week
                // gap at 2 weeks ago -> streak stops at 2
                session(LocalDate.now().minusWeeks(4), emptyList()),
            ),
        )
        advanceUntilIdle()
        assertEquals(2, vm.streak)
    }

    @Test fun `streak skips an empty current week`() = runTest(dispatcher.scheduler) {
        // Nothing this week, but last week has a session — the current week is skippable.
        val vm = vmWith(listOf(session(LocalDate.now().minusWeeks(1), emptyList())))
        advanceUntilIdle()
        assertEquals(1, vm.streak)
    }

    @Test fun `weeklyVolumes returns eight weeks ending with the current week`() = runTest(dispatcher.scheduler) {
        val vm = vmWith(listOf(session(LocalDate.now(), listOf(set("100", 5, true))))) // 500 kg -> 0.5 t
        advanceUntilIdle()
        val volumes = vm.weeklyVolumes
        assertEquals(8, volumes.size)
        assertEquals(0.5, volumes.last().tonnes, 0.0001)
    }
}
