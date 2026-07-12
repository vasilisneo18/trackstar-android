package com.vasilisneo.trackstar.ui.screens.main.coach

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.api.AthleteNotesDto
import com.vasilisneo.trackstar.data.api.ProfileResponse
import com.vasilisneo.trackstar.data.api.WorkoutSessionResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.AthleteRepository
import kotlinx.coroutines.launch

// Backs the coach's per-athlete detail screen. Loads the athlete's profile (for the nav bar) and
// their completed sessions (Sessions tab). Plan/Diet tabs get their own athlete-scoped state as
// those tabs are built. `athleteId` is the coach's `/coach/athletes/{id}` scope.
class AthleteDetailViewModel(private val athleteId: String) : ViewModel() {

    private val repo = AthleteRepository()

    var athlete by mutableStateOf<ProfileResponse?>(null)
        private set
    var sessions by mutableStateOf<List<WorkoutSessionResponse>>(emptyList())
        private set
    var isLoadingSessions by mutableStateOf(false)
        private set
    var notes by mutableStateOf(AthleteNotesDto(athleteId = athleteId))
        private set

    init {
        fetchProfile()
        fetchSessions()
        fetchNotes()
    }

    fun fetchProfile() {
        viewModelScope.launch {
            (repo.getAthlete(athleteId) as? ApiResult.Success)?.let { athlete = it.data }
        }
    }

    fun fetchSessions() {
        viewModelScope.launch {
            isLoadingSessions = sessions.isEmpty()
            (repo.getAthleteSessions(athleteId) as? ApiResult.Success)?.let { sessions = it.data }
            isLoadingSessions = false
        }
    }

    fun fetchNotes() {
        viewModelScope.launch {
            (repo.getAthleteNotes(athleteId) as? ApiResult.Success)?.let { notes = it.data }
        }
    }

    // Updates local state immediately and persists (mirrors the diet/plan optimistic-sync pattern).
    fun updateNotes(updated: AthleteNotesDto) {
        notes = updated
        viewModelScope.launch { repo.saveAthleteNotes(athleteId, updated) }
    }

    fun removeAthlete(onRemoved: () -> Unit) {
        viewModelScope.launch {
            repo.removeAthlete(athleteId)
            onRemoved()
        }
    }
}
