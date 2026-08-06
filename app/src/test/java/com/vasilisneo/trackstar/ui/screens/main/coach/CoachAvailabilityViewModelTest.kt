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
            val slot = slot(id = "new", date = request.date, start = request.startTime, capacity = request.capacity)
            slots.add(slot)
            return ApiResult.Success(slot)
        }
        override suspend fun deleteSlot(slotId: String): ApiResult<MessageResponse> {
            deleted.add(slotId)
            slots.removeAll { it.id == slotId }
            return ApiResult.Success(MessageResponse("ok"))
        }
    }

    private fun vm(repo: FakeBookingRepo) = CoachAvailabilityViewModel(ApplicationProvider.getApplicationContext(), repo)

    @Test fun `fetch loads my slots`() = runTest(dispatcher.scheduler) {
        val vm = vm(FakeBookingRepo(listOf(slot("s1"), slot("s2"))))
        advanceUntilIdle()
        assertEquals(listOf("s1", "s2"), vm.slots.map { it.id })
    }

    @Test fun `slotsByDate groups and sorts by date soonest first`() = runTest(dispatcher.scheduler) {
        val vm = vm(FakeBookingRepo(listOf(
            slot("a", date = "2026-08-10"),
            slot("b", date = "2026-08-05"),
            slot("c", date = "2026-08-10"),
        )))
        advanceUntilIdle()
        val grouped = vm.slotsByDate
        assertEquals(listOf("2026-08-05", "2026-08-10"), grouped.map { it.first })
        assertEquals(listOf("b"), grouped[0].second.map { it.id })
        assertEquals(listOf("a", "c"), grouped[1].second.map { it.id })
    }

    @Test fun `addSlot posts the request, refetches, and reports success`() = runTest(dispatcher.scheduler) {
        val repo = FakeBookingRepo()
        val vm = vm(repo)
        advanceUntilIdle()
        var reported: Boolean? = null
        vm.addSlot("2026-08-12", "09:00", "10:00", capacity = 3, title = "Group", notes = null) { reported = it }
        advanceUntilIdle()
        assertEquals(1, repo.created.size)
        assertEquals(3, repo.created[0].capacity)
        assertEquals("Group", repo.created[0].title)
        assertTrue(reported == true)
        assertTrue(vm.slots.any { it.id == "new" })
    }

    @Test fun `addSlot coerces a zero capacity up to 1`() = runTest(dispatcher.scheduler) {
        val repo = FakeBookingRepo()
        val vm = vm(repo)
        advanceUntilIdle()
        vm.addSlot("2026-08-12", "09:00", "10:00", capacity = 0, title = null, notes = null)
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
            date: String = "2026-08-10",
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
