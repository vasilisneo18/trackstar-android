package com.vasilisneo.trackstar.ui.screens.main.attendance

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.api.CreateGymRequest
import com.vasilisneo.trackstar.data.api.GymResponse
import com.vasilisneo.trackstar.data.api.VisitResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.location.LocationProvider
import com.vasilisneo.trackstar.data.workout.CheckInRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Coach side of attendance: a rotating session code, gym management, and the attendance roster.
// Mirrors iOS's SessionCodeView / GymsView / CoachAttendanceView view models.
class CoachCheckInViewModel(
    app: Application,
    private val repo: CheckInRepository,
) : AndroidViewModel(app) {

    constructor(app: Application) : this(app, CheckInRepository())

    private val location = LocationProvider(app)

    var error by mutableStateOf<String?>(null)
    fun clearError() { error = null }

    // --- Session code -------------------------------------------------------
    var sessionQrPayload by mutableStateOf<String?>(null)
        private set
    var isEnding by mutableStateOf(false)
        private set
    var endedMessage by mutableStateOf<String?>(null)
    fun clearEnded() { endedMessage = null }

    /** Refresh the rotating code roughly every minute while the screen is open. */
    suspend fun runSessionCodeLoop() {
        while (true) {
            when (val r = repo.createSessionCode()) {
                is ApiResult.Success -> sessionQrPayload = "trackstar://checkin/coach/${r.data.token}"
                is ApiResult.Error -> if (sessionQrPayload == null) error = r.message
            }
            delay(60_000)
        }
    }

    fun endSession() {
        viewModelScope.launch {
            isEnding = true
            when (val r = repo.endSession()) {
                is ApiResult.Success -> {
                    val n = r.data["closed"] ?: 0
                    endedMessage = if (n == 0) "No one was checked in." else "Checked out $n athlete${if (n == 1) "" else "s"}."
                }
                is ApiResult.Error -> error = r.message
            }
            isEnding = false
        }
    }

    // --- Gyms ---------------------------------------------------------------
    var gyms by mutableStateOf<List<GymResponse>>(emptyList())
        private set
    var isLoadingGyms by mutableStateOf(false)
        private set

    fun loadGyms() {
        viewModelScope.launch {
            isLoadingGyms = true
            when (val r = repo.getGyms()) {
                is ApiResult.Success -> gyms = r.data
                is ApiResult.Error -> Unit
            }
            isLoadingGyms = false
        }
    }

    /** Adds a gym at the coach's current location. onDone(true) on success. */
    fun addGym(name: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            when (val loc = location.currentLocation()) {
                is LocationProvider.Result.Error -> { error = loc.message; onDone(false) }
                is LocationProvider.Result.Success -> {
                    when (val r = repo.createGym(CreateGymRequest(name, loc.lat, loc.lng))) {
                        is ApiResult.Success -> { loadGyms(); onDone(true) }
                        is ApiResult.Error -> { error = r.message; onDone(false) }
                    }
                }
            }
        }
    }

    fun deleteGym(id: String) {
        viewModelScope.launch {
            when (repo.deleteGym(id)) {
                is ApiResult.Success -> gyms = gyms.filterNot { it.id == id }
                is ApiResult.Error -> loadGyms()
            }
        }
    }

    // --- Attendance roster --------------------------------------------------
    var attendance by mutableStateOf<List<VisitResponse>>(emptyList())
        private set
    var isLoadingAttendance by mutableStateOf(false)
        private set

    fun loadAttendance() {
        viewModelScope.launch {
            isLoadingAttendance = true
            when (val r = repo.getCoachAttendance()) {
                is ApiResult.Success -> attendance = r.data
                is ApiResult.Error -> Unit
            }
            isLoadingAttendance = false
        }
    }
}
