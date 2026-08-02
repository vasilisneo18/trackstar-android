package com.vasilisneo.trackstar.ui.screens.main.diet

import androidx.test.core.app.ApplicationProvider
import com.vasilisneo.trackstar.data.api.DietMeal
import com.vasilisneo.trackstar.data.api.WeeklyDietPlanDto
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.DietRepository
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Same DI pattern: a fake DietRepository feeds a controlled plan (and records saves) so the diet
// mutations, active-day selection, and consume gating are verified without the network.
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DietViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeDietRepo(private val plan: WeeklyDietPlanDto) : DietRepository() {
        var saved: WeeklyDietPlanDto? = null
        override suspend fun getDiet(athleteId: String?) = ApiResult.Success(plan)
        override suspend fun saveDiet(plan: WeeklyDietPlanDto, athleteId: String?): ApiResult<Unit> {
            saved = plan
            return ApiResult.Success(Unit)
        }
    }

    private val today = todayName()
    private val otherDay = if (today == "Monday") "Tuesday" else "Monday"

    private fun meal(id: String, consumed: Boolean = false) =
        DietMeal(id = id, type = "breakfast", name = id, isConsumed = consumed)

    private fun vm(
        plan: WeeklyDietPlanDto = WeeklyDietPlanDto(),
        athleteId: String? = null,
        repo: FakeDietRepo = FakeDietRepo(plan),
    ) = DietViewModel(ApplicationProvider.getApplicationContext(), athleteId, repo)

    @Test fun `fetch loads the current day's meals`() = runTest(dispatcher.scheduler) {
        val vm = vm(WeeklyDietPlanDto(mapOf(today to listOf(meal("m1"), meal("m2")))))
        advanceUntilIdle()
        assertEquals(listOf("m1", "m2"), vm.activeMeals.map { it.id })
    }

    @Test fun `addMeal appends a new meal to the active day`() = runTest(dispatcher.scheduler) {
        val vm = vm()
        advanceUntilIdle()
        vm.addMeal(meal("m1"))
        assertEquals(listOf("m1"), vm.activeMeals.map { it.id })
    }

    @Test fun `addMeal with an existing id replaces rather than duplicates`() = runTest(dispatcher.scheduler) {
        val vm = vm(WeeklyDietPlanDto(mapOf(today to listOf(meal("m1", consumed = false)))))
        advanceUntilIdle()
        vm.addMeal(meal("m1", consumed = true))
        assertEquals(1, vm.activeMeals.size)
        assertTrue(vm.activeMeals.single().isConsumed)
    }

    @Test fun `removeMeal drops the meal by id`() = runTest(dispatcher.scheduler) {
        val vm = vm(WeeklyDietPlanDto(mapOf(today to listOf(meal("m1"), meal("m2")))))
        advanceUntilIdle()
        vm.removeMeal("m1")
        assertEquals(listOf("m2"), vm.activeMeals.map { it.id })
    }

    @Test fun `toggleConsumed flips the consumed flag`() = runTest(dispatcher.scheduler) {
        val vm = vm(WeeklyDietPlanDto(mapOf(today to listOf(meal("m1", consumed = false)))))
        advanceUntilIdle()
        vm.toggleConsumed("m1")
        assertTrue(vm.activeMeals.single().isConsumed)
    }

    @Test fun `hasMeals reflects which days have meals`() = runTest(dispatcher.scheduler) {
        val vm = vm(WeeklyDietPlanDto(mapOf(today to listOf(meal("m1")))))
        advanceUntilIdle()
        assertTrue(vm.hasMeals(today))
        assertFalse(vm.hasMeals(otherDay))
    }

    @Test fun `a mutation syncs the whole plan to the repository`() = runTest(dispatcher.scheduler) {
        val repo = FakeDietRepo(WeeklyDietPlanDto())
        val vm = vm(repo = repo)
        advanceUntilIdle()
        vm.addMeal(meal("m1"))
        advanceUntilIdle()
        assertNotNull(repo.saved)
        assertEquals(listOf("m1"), repo.saved!!.meals[today]!!.map { it.id })
    }

    @Test fun `canConsumeToday is true for the signed-in user on today`() = runTest(dispatcher.scheduler) {
        val vm = vm()
        advanceUntilIdle()
        assertTrue(vm.canConsumeToday)
    }

    @Test fun `canConsumeToday is false in coach mode`() = runTest(dispatcher.scheduler) {
        val vm = vm(athleteId = "athlete-1")
        advanceUntilIdle()
        assertFalse(vm.canConsumeToday)
    }

    @Test fun `canConsumeToday is false when viewing another day`() = runTest(dispatcher.scheduler) {
        val vm = vm()
        advanceUntilIdle()
        vm.currentDay = otherDay
        assertFalse(vm.canConsumeToday)
    }
}
