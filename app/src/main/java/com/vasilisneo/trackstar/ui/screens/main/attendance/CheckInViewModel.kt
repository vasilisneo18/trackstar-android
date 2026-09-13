package com.vasilisneo.trackstar.ui.screens.main.attendance

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.api.CheckInRequest
import com.vasilisneo.trackstar.data.api.VisitResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.location.LocationProvider
import com.vasilisneo.trackstar.data.workout.CheckInRepository
import kotlinx.coroutines.launch

// The two things a check-in QR can encode (deep-link style, matching iOS's CheckInScan):
//   trackstar://checkin/gym/<gymId>   ·   trackstar://checkin/coach/<sessionToken>
sealed interface CheckInScan {
    data class Gym(val gymId: String) : CheckInScan
    data class CoachSession(val token: String) : CheckInScan

    companion object {
        fun parse(code: String): CheckInScan? {
            val uri = runCatching { Uri.parse(code) }.getOrNull() ?: return null
            if (uri.scheme != "trackstar" || uri.host != "checkin") return null
            val parts = uri.pathSegments
            if (parts.size != 2) return null
            return when (parts[0]) {
                "gym" -> Gym(parts[1])
                "coach" -> CoachSession(parts[1])
                else -> null
            }
        }
    }
}

// Drives the athlete's attendance screen: scan to check in (gym or coach session), see the active
// visit, check out, and browse history. Mirrors iOS's CheckInViewModel.
class CheckInViewModel(
    app: Application,
    private val repo: CheckInRepository = CheckInRepository(),
) : AndroidViewModel(app) {

    private val location = LocationProvider(app)

    var activeVisit by mutableStateOf<VisitResponse?>(null)
        private set
    var history by mutableStateOf<List<VisitResponse>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isBusy by mutableStateOf(false)   // during a check-in / check-out round trip
        private set
    var error by mutableStateOf<String?>(null)

    fun clearError() { error = null }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            when (val r = repo.getVisits()) {
                is ApiResult.Success -> {
                    activeVisit = r.data.firstOrNull { it.isOpen }
                    history = r.data.filter { !it.isOpen }
                }
                is ApiResult.Error -> Unit // keep whatever's on screen
            }
            isLoading = false
        }
    }

    fun handleScan(code: String) {
        val scan = CheckInScan.parse(code) ?: run {
            error = "That QR code isn't a Trackstar check-in code."
            return
        }
        viewModelScope.launch {
            isBusy = true
            error = null
            val result = when (scan) {
                is CheckInScan.CoachSession ->
                    repo.checkIn(CheckInRequest(method = "coach_session", sessionToken = scan.token))
                is CheckInScan.Gym -> when (val loc = location.currentLocation()) {
                    is LocationProvider.Result.Error -> {
                        error = loc.message
                        isBusy = false
                        return@launch
                    }
                    is LocationProvider.Result.Success ->
                        repo.checkIn(CheckInRequest(method = "gym", gymId = scan.gymId, lat = loc.lat, lng = loc.lng))
                }
            }
            when (result) {
                is ApiResult.Success -> { activeVisit = result.data; load() }
                is ApiResult.Error -> error = result.message
            }
            isBusy = false
        }
    }

    fun checkOut() {
        val id = activeVisit?.id ?: return
        viewModelScope.launch {
            isBusy = true
            when (val r = repo.checkOut(id)) {
                is ApiResult.Success -> { activeVisit = null; load() }
                is ApiResult.Error -> error = r.message
            }
            isBusy = false
        }
    }
}
