package com.vasilisneo.trackstar.ui.screens.main.workout

import androidx.test.core.app.ApplicationProvider
import com.vasilisneo.trackstar.data.api.ExerciseComment
import com.vasilisneo.trackstar.data.api.PlannedSessionResponse
import com.vasilisneo.trackstar.data.api.WorkoutSessionData
import com.vasilisneo.trackstar.data.api.WorkoutSessionResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.CommentRepository
import com.vasilisneo.trackstar.data.workout.PlanRepository
import com.vasilisneo.trackstar.data.workout.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// Same DI pattern as StatsViewModelTest: fakes feed a controlled plan + completed sessions so we can
// assert the Completed/Missed/Upcoming mapping, day filtering, ordering, and the free-tier session
// cap — no network.
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WorkoutViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakePlanRepo(private val data: List<PlannedSessionResponse>) : PlanRepository() {
        override suspend fun getPlan(weekIdentifier: String, athleteId: String?) = ApiResult.Success(data)
    }

    private class FakeSessionRepo(private val data: List<WorkoutSessionResponse>) : SessionRepository() {
        override suspend fun getSessions() = ApiResult.Success(data)
    }

    private class FakeCommentRepo : CommentRepository() {
        override suspend fun getWeekComments(weekIdentifier: String) = ApiResult.Success(emptyList<ExerciseComment>())
    }

    private val todayName: String = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    private fun planned(id: String, day: String = todayName, order: Int = 0) = PlannedSessionResponse(
        id = id, weekIdentifier = "w", day = day, orderIndex = order, title = "Session", exercises = emptyList(),
    )

    private fun completedFor(planId: String, date: LocalDate = LocalDate.now()) = WorkoutSessionResponse(
        id = "c", clientId = null, date = date.toString(), durationSeconds = 0,
        sessionData = WorkoutSessionData(
            id = "sd", date = 0.0, completedAt = 0.0, durationSeconds = 0,
            exercises = emptyList(), planSessionId = planId, title = "S",
        ),
    )

    private fun vm(planned: List<PlannedSessionResponse>, completed: List<WorkoutSessionResponse>) =
        WorkoutViewModel(
            ApplicationProvider.getApplicationContext(),
            FakePlanRepo(planned), FakeSessionRepo(completed), FakeCommentRepo(),
        )

    private fun SessionDisplay.plannedId(): String = when (this) {
        is SessionDisplay.Completed -> planned.id ?: ""
        is SessionDisplay.Missed -> planned.id ?: ""
        is SessionDisplay.Upcoming -> planned.id ?: ""
    }

    @Test fun `a logged session shows as Completed`() = runTest(dispatcher.scheduler) {
        val vm = vm(listOf(planned("p1")), listOf(completedFor("p1")))
        advanceUntilIdle()
        val display = vm.displaySessions.single()
        assertTrue(display is SessionDisplay.Completed)
        assertEquals("p1", display.plannedId())
    }

    @Test fun `an unlogged session today shows as Upcoming`() = runTest(dispatcher.scheduler) {
        val vm = vm(listOf(planned("p1")), emptyList())
        advanceUntilIdle()
        assertTrue(vm.displaySessions.single() is SessionDisplay.Upcoming)
    }

    @Test fun `an unlogged session on a past day shows as Missed`() = runTest(dispatcher.scheduler) {
        val vm = vm(listOf(planned("p1")), emptyList())
        advanceUntilIdle()
        // A week ago is the same weekday (so the plan still matches) but strictly in the past.
        vm.goToDate(LocalDate.now().minusWeeks(1))
        advanceUntilIdle()
        assertTrue(vm.displaySessions.single() is SessionDisplay.Missed)
    }

    @Test fun `sessions on other weekdays are filtered out`() = runTest(dispatcher.scheduler) {
        val tomorrowName = LocalDate.now().plusDays(1).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val vm = vm(listOf(planned("p1", day = tomorrowName)), emptyList())
        advanceUntilIdle()
        assertTrue(vm.displaySessions.isEmpty())
    }

    @Test fun `sessions are ordered by orderIndex`() = runTest(dispatcher.scheduler) {
        val vm = vm(listOf(planned("p2", order = 1), planned("p1", order = 0)), emptyList())
        advanceUntilIdle()
        assertEquals(listOf("p1", "p2"), vm.displaySessions.map { it.plannedId() })
    }

    @Test fun `free user at the weekly cap cannot start a session`() = runTest(dispatcher.scheduler) {
        val threeThisWeek = (1..3).map { completedFor("x$it") } // dated today -> current week
        val vm = vm(emptyList(), threeThisWeek)
        advanceUntilIdle()
        assertFalse(vm.canStartSession)
    }

    @Test fun `free user under the cap can start a session`() = runTest(dispatcher.scheduler) {
        val vm = vm(emptyList(), emptyList())
        advanceUntilIdle()
        assertTrue(vm.canStartSession)
    }
}
