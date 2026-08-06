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

// Coach side of booking: manage the slots you offer. repo is constructor-injected (secondary
// Application constructor for the viewModel() factory) so it's testable.
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

    // Slots grouped by date (soonest first), for a sectioned list.
    val slotsByDate: List<Pair<String, List<SlotResponse>>>
        get() = slots.groupBy { it.date }.toList().sortedBy { it.first }

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

    fun addSlot(date: String, startTime: String, endTime: String, capacity: Int, title: String?, notes: String?, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            errorMessage = null
            val req = CreateSlotRequest(date, startTime, endTime, capacity.coerceAtLeast(1), title?.ifBlank { null }, notes?.ifBlank { null })
            when (val r = repo.createSlot(req)) {
                is ApiResult.Success -> { fetch(); onDone(true) }
                is ApiResult.Error -> { errorMessage = r.message; onDone(false) }
            }
        }
    }

    fun deleteSlot(id: String) {
        // Optimistic removal, then confirm on the server.
        slots = slots.filterNot { it.id == id }
        viewModelScope.launch { repo.deleteSlot(id) }
    }
}
