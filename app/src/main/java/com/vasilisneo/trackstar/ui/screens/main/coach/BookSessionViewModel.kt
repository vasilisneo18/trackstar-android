package com.vasilisneo.trackstar.ui.screens.main.coach

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.api.SlotResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.BookingRepository
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// Athlete side of booking: browse the linked coach's upcoming slots and book/cancel, laid out like
// the weekly plan — a week bar at the bottom and per-day tabs up top. repo is constructor-injected
// (secondary Application constructor for the viewModel() factory).
class BookSessionViewModel(
    app: Application,
    private val repo: BookingRepository,
) : AndroidViewModel(app) {

    constructor(app: Application) : this(app, BookingRepository())

    var available by mutableStateOf<List<SlotResponse>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var busySlotId by mutableStateOf<String?>(null) // slot currently being booked/cancelled
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Week + day selection, mirroring WeeklyPlanViewModel so the UI reuses DayTabPill/WeekNavigationBar.
    var weekStart by mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
        private set
    var selectedDay by mutableStateOf(LocalDate.now().dayOfWeek)
        private set

    val weekDays: List<LocalDate>
        get() = (0..6).map { weekStart.plusDays(it.toLong()) }

    val selectedDate: LocalDate
        get() = weekStart.plusDays((selectedDay.value - DayOfWeek.MONDAY.value).toLong())

    // Slots on the selected day, earliest start first.
    val slotsForSelectedDay: List<SlotResponse>
        get() = available.filter { it.date == selectedDate.toString() }.sortedBy { it.startTime }

    // Does the given weekday (in the current week) have any available slots? Drives the day-tab dot.
    fun hasSlots(day: DayOfWeek): Boolean {
        val date = weekStart.plusDays((day.value - DayOfWeek.MONDAY.value).toLong()).toString()
        return available.any { it.date == date }
    }

    fun goToDay(date: LocalDate) { selectedDay = date.dayOfWeek }
    fun goToPreviousWeek() { weekStart = weekStart.minusWeeks(1) }
    fun goToNextWeek() { weekStart = weekStart.plusWeeks(1) }
    fun clearError() { errorMessage = null }

    // Select an arbitrary date (from the calendar view); keeps week + day in sync.
    fun selectDate(date: LocalDate) {
        weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        selectedDay = date.dayOfWeek
    }

    fun hasSessionsOn(date: LocalDate): Boolean = available.any { it.date == date.toString() }

    val myBookings: List<SlotResponse>
        get() = available.filter { it.bookedByMe }

    init { fetch() }

    fun fetch() {
        viewModelScope.launch {
            isLoading = true
            when (val r = repo.availableSlots()) {
                is ApiResult.Success -> available = r.data
                is ApiResult.Error -> errorMessage = r.message
            }
            isLoading = false
        }
    }

    fun book(slotId: String) {
        if (busySlotId != null) return
        viewModelScope.launch {
            busySlotId = slotId
            errorMessage = null
            when (val r = repo.book(slotId)) {
                is ApiResult.Success -> replaceSlot(r.data)
                is ApiResult.Error -> errorMessage = r.message
            }
            busySlotId = null
        }
    }

    fun cancel(slotId: String) {
        if (busySlotId != null) return
        viewModelScope.launch {
            busySlotId = slotId
            errorMessage = null
            when (val r = repo.cancel(slotId)) {
                is ApiResult.Success -> replaceSlot(r.data)
                is ApiResult.Error -> errorMessage = r.message
            }
            busySlotId = null
        }
    }

    // Swap the updated slot back into the list so remaining/bookedByMe reflect immediately.
    private fun replaceSlot(updated: SlotResponse) {
        available = available.map { if (it.id == updated.id) updated else it }
    }
}
