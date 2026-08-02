package com.vasilisneo.trackstar.ui.screens.main.plan

import com.vasilisneo.trackstar.data.api.AiUsageResponse
import com.vasilisneo.trackstar.data.api.PlannedSessionRequest
import com.vasilisneo.trackstar.data.api.PlannedSessionResponse
import com.vasilisneo.trackstar.data.api.WorkoutPlanDay
import com.vasilisneo.trackstar.data.api.WorkoutPlanExercise
import com.vasilisneo.trackstar.data.api.WorkoutPlanInput
import com.vasilisneo.trackstar.data.api.WorkoutPlanResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.AiRepository
import com.vasilisneo.trackstar.data.workout.PlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// The AI workout planner is a plain VM with its own Main-dispatched scope; a test dispatcher drives
// it and fake repos feed usage/generation/apply. Covers the wizard state machine (no Robolectric
// needed — no Android framework in this VM).
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AIWorkoutPlannerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeAiRepo(
        private val usage: ApiResult<AiUsageResponse> = ApiResult.Success(AiUsageResponse(0, 0, 6)),
        private val workout: ApiResult<WorkoutPlanResponse> = ApiResult.Success(WorkoutPlanResponse(emptyMap())),
    ) : AiRepository() {
        override suspend fun getUsage() = usage
        override suspend fun generateWorkoutPlan(input: WorkoutPlanInput) = workout
    }

    private class FakePlanRepo(
        private val batch: ApiResult<List<PlannedSessionResponse>> = ApiResult.Success(emptyList()),
    ) : PlanRepository() {
        var lastRequests: List<PlannedSessionRequest>? = null
        override suspend fun upsertBatch(requests: List<PlannedSessionRequest>, athleteId: String?): ApiResult<List<PlannedSessionResponse>> {
            lastRequests = requests
            return batch
        }
    }

    private fun exercise(name: String) =
        WorkoutPlanExercise(name = name, sets = 3, reps = 8, durationSeconds = null, distanceMeters = null, restSeconds = 90)

    private fun vm(ai: FakeAiRepo = FakeAiRepo(), plan: FakePlanRepo = FakePlanRepo()) =
        AIWorkoutPlannerViewModel("2026-W31", ai, plan)

    // --- wizard navigation ---------------------------------------------------

    @Test fun `goNext advances but stops at the last step`() = runTest(dispatcher.scheduler) {
        val vm = vm()
        assertEquals(0, vm.step)
        repeat(10) { vm.goNext() }
        assertEquals(vm.totalSteps - 1, vm.step)
    }

    @Test fun `goBack never goes below zero`() = runTest(dispatcher.scheduler) {
        val vm = vm()
        vm.goBack()
        assertEquals(0, vm.step)
    }

    // --- category selection --------------------------------------------------

    @Test fun `selectPrimary sets then deselects on re-tap`() = runTest(dispatcher.scheduler) {
        val vm = vm()
        vm.selectPrimary("Gym")
        assertEquals("Gym", vm.selectedPrimaryCategory)
        vm.selectPrimary("Gym")
        assertEquals(null, vm.selectedPrimaryCategory)
    }

    @Test fun `promoting a secondary to primary removes it from secondaries`() = runTest(dispatcher.scheduler) {
        val vm = vm()
        vm.toggleSecondary("Gym")
        vm.selectPrimary("Gym")
        assertEquals("Gym", vm.selectedPrimaryCategory)
        assertFalse(vm.selectedSecondaryCategories.contains("Gym"))
    }

    @Test fun `toggleSecondary caps at two selections`() = runTest(dispatcher.scheduler) {
        val vm = vm()
        vm.toggleSecondary("Gym")
        vm.toggleSecondary("Running")
        vm.toggleSecondary("Cycling") // ignored — already at 2
        assertEquals(2, vm.selectedSecondaryCategories.size)
        assertFalse(vm.selectedSecondaryCategories.contains("Cycling"))
    }

    @Test fun `selectedCategories is primary followed by secondaries`() = runTest(dispatcher.scheduler) {
        val vm = vm()
        vm.selectPrimary("Gym")
        vm.toggleSecondary("Running")
        assertEquals(listOf("Gym", "Running"), vm.selectedCategories)
    }

    @Test fun `toggleFocus adds then removes an option`() = runTest(dispatcher.scheduler) {
        val vm = vm()
        vm.toggleFocus("Gym", "Chest")
        assertTrue(vm.focusByCategory["Gym"]!!.contains("Chest"))
        vm.toggleFocus("Gym", "Chest")
        assertFalse(vm.focusByCategory["Gym"].orEmpty().contains("Chest"))
    }

    // --- usage check ---------------------------------------------------------

    @Test fun `checkUsage reads usage and clears the checking flag`() = runTest(dispatcher.scheduler) {
        val vm = vm(FakeAiRepo(usage = ApiResult.Success(AiUsageResponse(2, 0, 6))))
        advanceUntilIdle()
        assertEquals(2, vm.usageUsed)
        assertEquals(6, vm.usageLimit)
        assertFalse(vm.limitReached)
        assertFalse(vm.isCheckingUsage)
    }

    @Test fun `checkUsage flags the limit when used equals limit`() = runTest(dispatcher.scheduler) {
        val vm = vm(FakeAiRepo(usage = ApiResult.Success(AiUsageResponse(6, 0, 6))))
        advanceUntilIdle()
        assertTrue(vm.limitReached)
    }

    // --- generation ----------------------------------------------------------

    @Test fun `generate stores the plan and checks every exercise by default`() = runTest(dispatcher.scheduler) {
        val days = mapOf("monday" to WorkoutPlanDay(title = "Push", exercises = listOf(exercise("Bench"), exercise("Fly"))))
        val vm = vm(FakeAiRepo(workout = ApiResult.Success(WorkoutPlanResponse(days))))
        advanceUntilIdle()
        vm.generate()
        advanceUntilIdle()
        assertTrue(vm.hasGeneratedPlan)
        assertEquals(2, vm.checkedCount("monday"))
        assertFalse(vm.isGenerating)
    }

    @Test fun `a limit error during generate flips limitReached`() = runTest(dispatcher.scheduler) {
        val vm = vm(FakeAiRepo(workout = ApiResult.Error("Monthly limit reached")))
        advanceUntilIdle()
        vm.generate()
        advanceUntilIdle()
        assertNotNull(vm.errorMessage)
        assertTrue(vm.limitReached)
    }

    @Test fun `toggleExercise unchecks a generated exercise`() = runTest(dispatcher.scheduler) {
        val days = mapOf("monday" to WorkoutPlanDay(title = "Push", exercises = listOf(exercise("Bench"), exercise("Fly"))))
        val vm = vm(FakeAiRepo(workout = ApiResult.Success(WorkoutPlanResponse(days))))
        advanceUntilIdle()
        vm.generate()
        advanceUntilIdle()
        vm.toggleExercise("monday::0")
        assertEquals(1, vm.checkedCount("monday"))
    }

    // --- apply ---------------------------------------------------------------

    @Test fun `apply sends only the checked exercises and reports success`() = runTest(dispatcher.scheduler) {
        val days = mapOf("monday" to WorkoutPlanDay(title = "Push", exercises = listOf(exercise("Bench"), exercise("Fly"))))
        val plan = FakePlanRepo(batch = ApiResult.Success(emptyList()))
        val vm = vm(FakeAiRepo(workout = ApiResult.Success(WorkoutPlanResponse(days))), plan)
        advanceUntilIdle()
        vm.generate()
        advanceUntilIdle()
        vm.toggleExercise("monday::1") // drop the second exercise

        var done: Boolean? = null
        vm.apply { done = it }
        advanceUntilIdle()

        assertEquals(true, done)
        assertEquals(1, plan.lastRequests!!.size)
        assertEquals(1, plan.lastRequests!!.single().exercises.size) // only the checked one
        assertEquals("Monday", plan.lastRequests!!.single().day)
    }
}
