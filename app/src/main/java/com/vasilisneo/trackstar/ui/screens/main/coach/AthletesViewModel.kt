package com.vasilisneo.trackstar.ui.screens.main.coach

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.api.ProfileResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.TokenStore
import com.vasilisneo.trackstar.data.workout.AthleteRepository
import com.vasilisneo.trackstar.ui.components.initialsFrom
import com.vasilisneo.trackstar.ui.screens.main.stats.localDate
import com.vasilisneo.trackstar.ui.screens.main.workout.weekIdentifierFor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// This week's plan-vs-done snapshot for one athlete, shown as pills on the roster card.
data class AthleteWeeklySummary(val plannedCount: Int, val completedCount: Int)

// Ports iOS's AthletesViewModel: loads the coach's roster and, per athlete, counts this week's
// planned sessions and completed sessions (API-first — no local cache yet).
class AthletesViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AthleteRepository()
    private val tokenStore = TokenStore(app)

    var athletes by mutableStateOf<List<ProfileResponse>>(emptyList())
        private set
    var weeklySummaries by mutableStateOf<Map<String, AthleteWeeklySummary>>(emptyMap())
        private set
    var isLoading by mutableStateOf(false)
        private set

    val userInitials: String = initialsFrom(
        listOfNotNull(tokenStore.firstName?.ifBlank { null }, tokenStore.lastName?.ifBlank { null })
            .joinToString(" ").ifBlank { null }
    )

    init { fetch() }

    fun fetch() {
        viewModelScope.launch {
            isLoading = athletes.isEmpty()
            when (val r = repo.getAthletes()) {
                is ApiResult.Success -> {
                    athletes = r.data
                    fetchSummaries(r.data)
                }
                is ApiResult.Error -> Unit // keep stale roster on failure
            }
            isLoading = false
        }
    }

    // Removes the athlete (optimistically off the roster, then DELETE on the backend). Called after
    // the swipe-revealed Remove button + confirmation dialog (iOS-style).
    fun removeAthlete(id: String) {
        athletes = athletes.filterNot { it.id == id }
        viewModelScope.launch { repo.removeAthlete(id) }
    }

    private fun fetchSummaries(roster: List<ProfileResponse>) {
        val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusWeeks(1)
        val weekId = weekIdentifierFor(weekStart)
        viewModelScope.launch {
            val results = roster.mapNotNull { athlete ->
                val id = athlete.id ?: return@mapNotNull null
                async {
                    val planned = (repo.getAthletePlan(id, weekId) as? ApiResult.Success)?.data.orEmpty().size
                    val completed = (repo.getAthleteSessions(id) as? ApiResult.Success)?.data.orEmpty().count { s ->
                        val d = s.localDate
                        d != null && !d.isBefore(weekStart) && d.isBefore(weekEnd)
                    }
                    id to AthleteWeeklySummary(planned, completed)
                }
            }.awaitAll().toMap()
            weeklySummaries = results
        }
    }
}

// iOS UserProfile.fullName / initials, computed from the profile DTO.
val ProfileResponse.fullName: String
    get() = listOfNotNull(firstName?.ifBlank { null }, lastName?.ifBlank { null }).joinToString(" ").ifBlank { email ?: "Athlete" }

val ProfileResponse.athleteInitials: String
    get() = initialsFrom(listOfNotNull(firstName?.ifBlank { null }, lastName?.ifBlank { null }).joinToString(" ").ifBlank { null })
