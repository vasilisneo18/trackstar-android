package com.vasilisneo.trackstar.ui.screens.main.coach

import androidx.test.core.app.ApplicationProvider
import com.vasilisneo.trackstar.data.api.CreateSlotRequest
import com.vasilisneo.trackstar.data.api.MessageResponse
import com.vasilisneo.trackstar.data.api.SlotResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.BookingRepository
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
class CoachAvailabilityViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeBookingRepo(initial: List<SlotResponse> = emptyList()) : BookingRepository() {
        val slots = initial.toMutableList()
        val created = mutableListOf<CreateSlotRequest>()
        val deleted = mutableListOf<String>()
        override suspend fun mySlots() = ApiResult.Success(slots.toList())
        override suspend fun createSlot(request: CreateSlotRequest): ApiResult<SlotResponse> {
            created.add(request)
            // Mirror the backend: repeatWeeks creates one slot per week from the base date.
            val base = LocalDate.parse(request.date)
            var first: SlotResponse? = null
            repeat(request.repeatWeeks.coerceAtLeast(1)) { i ->
                val slot = slot(id = "new-$i", date = base.plusWeeks(i.toLong()).toString(), start = request.startTime, capacity = request.capacity)
                slots.add(slot)
                if (first == null) first = slot
            }
            return ApiResult.Success(first!!)
        }
        override suspend fun deleteSlot(slotId: String): ApiResult<MessageResponse> {
            deleted.add(slotId)
            slots.removeAll { it.id == slotId }
            return ApiResult.Success(MessageResponse("ok"))
        }
    }

    private val monday: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun vm(repo: FakeBookingRepo) = CoachAvailabilityViewModel(ApplicationProvider.getApplicationContext(), repo)

    @Test fun `fetch loads my slots`() = runTest(dispatcher.scheduler) {
        val vm = vm(FakeBookingRepo(listOf(slot("s1"), slot("s2"))))
        advanceUntilIdle()
        assertEquals(listOf("s1", "s2"), vm.slots.map { it.id })
    }

    @Test fun `slotsForSelectedDay filters to the selected weekday`() = runTest(dispatcher.scheduler) {
        val tue = monday.plusDays(1)
        val repo = FakeBookingRepo(listOf(
            slot("mon", date = monday.toString()),
            slot("tueA", date = tue.toString(), start = "10:00"),
            slot("tueB", date = tue.toString(), start = "08:00"),
        ))
        val vm = vm(repo)
        advanceUntilIdle()
        vm.goToDay(tue)
        // Sorted by start time, only Tuesday's slots.
        assertEquals(listOf("tueB", "tueA"), vm.slotsForSelectedDay.map { it.id })
        assertTrue(vm.hasSlots(DayOfWeek.TUESDAY))
        assertTrue(vm.hasSlots(DayOfWeek.MONDAY))
    }

    @Test fun `addSlot posts for the selected day and reports success`() = runTest(dispatcher.scheduler) {
        val repo = FakeBookingRepo()
        val vm = vm(repo)
        advanceUntilIdle()
        val wed = monday.plusDays(2)
        vm.goToDay(wed)
        var reported: Boolean? = null
        vm.addSlot("09:00", "10:00", capacity = 3, title = "Group", notes = null) { reported = it }
        advanceUntilIdle()
        assertEquals(1, repo.created.size)
        assertEquals(wed.toString(), repo.created[0].date)
        assertEquals(3, repo.created[0].capacity)
        assertEquals(1, repo.created[0].repeatWeeks)
        assertTrue(reported == true)
    }

    @Test fun `addSlot with repeatWeeks creates one slot per week`() = runTest(dispatcher.scheduler) {
        val repo = FakeBookingRepo()
        val vm = vm(repo)
        advanceUntilIdle()
        vm.addSlot("09:00", "10:00", capacity = 1, title = null, notes = null, repeatWeeks = 8)
        advanceUntilIdle()
        assertEquals(8, repo.created[0].repeatWeeks)
        assertEquals(8, repo.slots.size)
    }

    @Test fun `addSlot coerces a zero capacity up to 1`() = runTest(dispatcher.scheduler) {
        val repo = FakeBookingRepo()
        val vm = vm(repo)
        advanceUntilIdle()
        vm.addSlot("09:00", "10:00", capacity = 0, title = null, notes = null)
        advanceUntilIdle()
        assertEquals(1, repo.created[0].capacity)
    }

    @Test fun `deleteSlot optimistically removes and calls the repo`() = runTest(dispatcher.scheduler) {
        val repo = FakeBookingRepo(listOf(slot("s1"), slot("s2")))
        val vm = vm(repo)
        advanceUntilIdle()
        vm.deleteSlot("s1")
        assertEquals(listOf("s2"), vm.slots.map { it.id })
        advanceUntilIdle()
        assertTrue(repo.deleted.contains("s1"))
    }

    companion object {
        fun slot(
            id: String,
            date: String = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString(),
            start: String = "09:00",
            end: String = "10:00",
            capacity: Int = 1,
        ) = SlotResponse(
            id = id, coachId = "coach", coachName = "Coach", date = date, startTime = start, endTime = end,
            capacity = capacity, remaining = capacity, full = false, bookedByMe = false,
            title = null, notes = null, attendees = null,
        )
    }
}
