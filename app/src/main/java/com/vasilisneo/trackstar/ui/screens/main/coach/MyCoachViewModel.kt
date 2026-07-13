package com.vasilisneo.trackstar.ui.screens.main.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vasilisneo.trackstar.data.api.ProfileResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.AthleteRepository
import kotlinx.coroutines.launch

// Athlete-side "My Coach" tab. Ports the coach section iOS shows in ProfileView (getMyCoach):
// when linked, the coach's name + "coaching since"; when unlinked, an empty state pointing the
// athlete at their QR / an invite link. The MyTeam tab renders this for athletes; coaches get
// AthletesScreen instead.
class MyCoachViewModel : ViewModel() {

    private val repo = AthleteRepository()

    var coach by mutableStateOf<ProfileResponse?>(null)
        private set
    // True once the first load settles, so the UI can tell "still loading" from "no coach".
    var loaded by mutableStateOf(false)
        private set

    init { fetch() }

    fun fetch() {
        viewModelScope.launch {
            when (val r = repo.getMyCoach()) {
                is ApiResult.Success -> coach = r.data
                // The backend 400s ("No coach linked") when unlinked — that's the empty state, not
                // an error to surface. Any other failure also falls through to the empty state.
                is ApiResult.Error -> coach = null
            }
            loaded = true
        }
    }
}
