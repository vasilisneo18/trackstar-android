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

// Athlete side of booking: browse the linked coach's upcoming slots and book/cancel. repo is
// constructor-injected (secondary Application constructor for the viewModel() factory).
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

    val availableByDate: List<Pair<String, List<SlotResponse>>>
        get() = available.groupBy { it.date }.toList().sortedBy { it.first }

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
