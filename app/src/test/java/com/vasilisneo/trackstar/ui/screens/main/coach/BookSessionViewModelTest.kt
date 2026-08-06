package com.vasilisneo.trackstar.ui.screens.main.coach

import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BookSessionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeBookingRepo(initial: List<SlotResponse>) : BookingRepository() {
        var available = initial.toMutableList()
        val booked = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        var bookError: String? = null
        override suspend fun availableSlots() = ApiResult.Success(available.toList())
        override suspend fun book(slotId: String): ApiResult<SlotResponse> {
            bookError?.let { return ApiResult.Error(it) }
            booked.add(slotId)
            val updated = available.first { it.id == slotId }.let {
                it.copy(bookedByMe = true, remaining = (it.remaining - 1).coerceAtLeast(0), full = it.remaining - 1 <= 0)
            }
            available = available.map { if (it.id == slotId) updated else it }.toMutableList()
            return ApiResult.Success(updated)
        }
        override suspend fun cancel(slotId: String): ApiResult<SlotResponse> {
            cancelled.add(slotId)
            val updated = available.first { it.id == slotId }.let {
                it.copy(bookedByMe = false, remaining = it.remaining + 1, full = false)
            }
            available = available.map { if (it.id == slotId) updated else it }.toMutableList()
            return ApiResult.Success(updated)
        }
    }

    private fun slot(
        id: String,
        date: String = "2026-08-10",
        capacity: Int = 2,
        remaining: Int = 2,
        bookedByMe: Boolean = false,
        full: Boolean = false,
    ) = SlotResponse(
        id = id, coachId = "coach", coachName = "Coach", date = date, startTime = "09:00", endTime = "10:00",
        capacity = capacity, remaining = remaining, full = full, bookedByMe = bookedByMe,
        title = null, notes = null, attendees = null,
    )

    private fun vm(repo: FakeBookingRepo) = BookSessionViewModel(ApplicationProvider.getApplicationContext(), repo)

    @Test fun `fetch loads available slots`() = runTest(dispatcher.scheduler) {
        val vm = vm(FakeBookingRepo(listOf(slot("s1"), slot("s2"))))
        advanceUntilIdle()
        assertEquals(listOf("s1", "s2"), vm.available.map { it.id })
    }

    @Test fun `book marks the slot booked and updates remaining in place`() = runTest(dispatcher.scheduler) {
        val repo = FakeBookingRepo(listOf(slot("s1", remaining = 2)))
        val vm = vm(repo)
        advanceUntilIdle()
        vm.book("s1")
        advanceUntilIdle()
        assertTrue(repo.booked.contains("s1"))
        val updated = vm.available.first { it.id == "s1" }
        assertTrue(updated.bookedByMe)
        assertEquals(1, updated.remaining)
        assertTrue("s1" in vm.myBookings.map { it.id })
    }

    @Test fun `cancel clears bookedByMe and restores remaining`() = runTest(dispatcher.scheduler) {
        val repo = FakeBookingRepo(listOf(slot("s1", remaining = 0, bookedByMe = true, full = true)))
        val vm = vm(repo)
        advanceUntilIdle()
        vm.cancel("s1")
        advanceUntilIdle()
        assertTrue(repo.cancelled.contains("s1"))
        val updated = vm.available.first { it.id == "s1" }
        assertFalse(updated.bookedByMe)
        assertFalse(updated.full)
        assertEquals(1, updated.remaining)
    }

    @Test fun `book surfaces an error message and does not update the slot`() = runTest(dispatcher.scheduler) {
        val repo = FakeBookingRepo(listOf(slot("s1"))).apply { bookError = "Session is full" }
        val vm = vm(repo)
        advanceUntilIdle()
        vm.book("s1")
        advanceUntilIdle()
        assertEquals("Session is full", vm.errorMessage)
        assertFalse(vm.available.first { it.id == "s1" }.bookedByMe)
    }

    @Test fun `slotsForSelectedDay filters to the selected weekday, sorted by start`() = runTest(dispatcher.scheduler) {
        val monday = java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val wed = monday.plusDays(2)
        val vm = vm(FakeBookingRepo(listOf(
            slot("monA", date = monday.toString()),
            slot("wedLate", date = wed.toString()),
            slot("wedEarly", date = wed.toString()),
        ).mapIndexed { i, s -> if (s.id == "wedEarly") s.copy(startTime = "08:00") else if (s.id == "wedLate") s.copy(startTime = "11:00") else s }))
        advanceUntilIdle()
        vm.goToDay(wed)
        assertEquals(listOf("wedEarly", "wedLate"), vm.slotsForSelectedDay.map { it.id })
        assertTrue(vm.hasSlots(java.time.DayOfWeek.WEDNESDAY))
        assertTrue(vm.hasSlots(java.time.DayOfWeek.MONDAY))
    }
}
