package com.vasilisneo.trackstar.ui.screens.main.coach

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.api.CreateSlotRequest
import com.vasilisneo.trackstar.data.api.SlotResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.BookingRepository
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// Coach side of booking: manage the slots you offer, laid out like the weekly plan — a week bar at
// the bottom and per-day tabs up top. repo is constructor-injected (secondary Application
// constructor for the viewModel() factory) so it's testable.
class CoachAvailabilityViewModel(
    app: Application,
    private val repo: BookingRepository,
) : AndroidViewModel(app) {

    constructor(app: Application) : this(app, BookingRepository())

    var slots by mutableStateOf<List<SlotResponse>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Week + day selection, mirroring WeeklyPlanViewModel so the UI can reuse DayTabPill/WeekNavigationBar.
    var weekStart by mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
        private set
    var selectedDay by mutableStateOf(LocalDate.now().dayOfWeek)
        private set

    val weekDays: List<LocalDate>
        get() = (0..6).map { weekStart.plusDays(it.toLong()) }

    // The concrete date of the selected weekday within the current week.
    val selectedDate: LocalDate
        get() = weekStart.plusDays((selectedDay.value - DayOfWeek.MONDAY.value).toLong())

    // Slots on the selected day, earliest start first.
    val slotsForSelectedDay: List<SlotResponse>
        get() = slots.filter { it.date == selectedDate.toString() }.sortedBy { it.startTime }

    // Does the given weekday (in the current week) have any slots? Drives the day-tab dot.
    fun hasSlots(day: DayOfWeek): Boolean {
        val date = weekStart.plusDays((day.value - DayOfWeek.MONDAY.value).toLong()).toString()
        return slots.any { it.date == date }
    }

    fun goToDay(date: LocalDate) { selectedDay = date.dayOfWeek }
    fun goToPreviousWeek() { weekStart = weekStart.minusWeeks(1) }
    fun goToNextWeek() { weekStart = weekStart.plusWeeks(1) }
    fun clearError() { errorMessage = null }

    init { fetch() }

    fun fetch() {
        viewModelScope.launch {
            isLoading = true
            when (val r = repo.mySlots()) {
                is ApiResult.Success -> slots = r.data
                is ApiResult.Error -> errorMessage = r.message
            }
            isLoading = false
        }
    }

    // date defaults to the selected day; repeatWeeks > 1 repeats the slot weekly for that many weeks.
    fun addSlot(
        startTime: String,
        endTime: String,
        capacity: Int,
        title: String?,
        notes: String?,
        repeatWeeks: Int = 1,
        date: LocalDate = selectedDate,
        onDone: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            errorMessage = null
            val req = CreateSlotRequest(
                date.toString(), startTime, endTime, capacity.coerceAtLeast(1),
                title?.ifBlank { null }, notes?.ifBlank { null }, repeatWeeks.coerceAtLeast(1),
            )
            when (val r = repo.createSlot(req)) {
                is ApiResult.Success -> { fetch(); onDone(true) }
                is ApiResult.Error -> { errorMessage = r.message; onDone(false) }
            }
        }
    }

    // "Cancel this session" — remove just this dated occurrence (booked athletes are notified).
    fun deleteSlot(id: String) {
        // Optimistic removal, then confirm on the server.
        slots = slots.filterNot { it.id == id }
        viewModelScope.launch { repo.deleteSlot(id) }
    }

    // "Delete permanently" — remove this occurrence and all future weeks of its recurring series.
    fun deleteSlotSeries(id: String) {
        viewModelScope.launch {
            when (val r = repo.deleteSlotSeries(id)) {
                is ApiResult.Success -> fetch() // count of removed slots is server-side; refetch to reflect
                is ApiResult.Error -> errorMessage = r.message
            }
        }
    }

    // "Edit session" — change time/capacity/title of one slot (booked athletes are notified server-side).
    fun editSlot(id: String, date: String, startTime: String, endTime: String, capacity: Int, title: String?, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            errorMessage = null
            val req = CreateSlotRequest(date, startTime, endTime, capacity.coerceAtLeast(1), title?.ifBlank { null }, null, 1)
            when (val r = repo.updateSlot(id, req)) {
                is ApiResult.Success -> { fetch(); onDone(true) }
                is ApiResult.Error -> { errorMessage = r.message; onDone(false) }
            }
        }
    }
}
