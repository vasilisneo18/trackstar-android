package com.vasilisneo.trackstar.ui.screens.main.coach

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vasilisneo.trackstar.data.api.ProfileResponse
import com.vasilisneo.trackstar.data.api.SlotResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.TokenStore
import com.vasilisneo.trackstar.data.workout.AthleteRepository
import com.vasilisneo.trackstar.data.workout.BookingRepository
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
// hasSessionToday: does this athlete have a session planned for today's weekday?
data class AthleteWeeklySummary(val plannedCount: Int, val completedCount: Int, val hasSessionToday: Boolean = false)

// Ports iOS's AthletesViewModel: loads the coach's roster and, per athlete, counts this week's
// planned sessions and completed sessions (API-first — no local cache yet).
// repo is constructor-injected so tests can supply a fake and exercise the roster + weekly-summary
// logic without the network. The secondary (Application) constructor is the one Compose's
// viewModel() factory resolves at runtime.
class AthletesViewModel(
    app: Application,
    private val repo: AthleteRepository,
) : AndroidViewModel(app) {

    constructor(app: Application) : this(app, AthleteRepository())

    private val tokenStore = TokenStore(app)
    private val bookingRepo = BookingRepository()

    var athletes by mutableStateOf<List<ProfileResponse>>(emptyList())
        private set
    var weeklySummaries by mutableStateOf<Map<String, AthleteWeeklySummary>>(emptyMap())
        private set
    var isLoading by mutableStateOf(false)
        private set

    // The coach's next booked session (has attendees) at/after now — the "coming up" quick-info.
    var nextUpcomingSession by mutableStateOf<SlotResponse?>(null)
        private set

    // This week's team totals — completed and planned across the roster.
    val finishedSessionsCount: Int
        get() = weeklySummaries.values.sumOf { it.completedCount }
    val plannedSessionsCount: Int
        get() = weeklySummaries.values.sumOf { it.plannedCount }
    // How many athletes have at least one session planned for today.
    val athletesWithSessionTodayCount: Int
        get() = weeklySummaries.values.count { it.hasSessionToday }

    val userInitials: String = initialsFrom(
        listOfNotNull(tokenStore.firstName?.ifBlank { null }, tokenStore.lastName?.ifBlank { null })
            .joinToString(" ").ifBlank { null }
    )

    init { fetch() }

    fun fetch() {
        viewModelScope.launch {
            // Paint from cache first (stale-while-revalidate) so the roster + pills are on screen
            // from the first frame — no empty state, no spinner, no reflow when the network lands.
            if (athletes.isEmpty()) {
                repo.cachedRoster()?.let { cached ->
                    athletes = cached
                    seedSummariesFromCache(cached)
                }
            }
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
        fetchUpcomingSession()
    }

    private fun fetchUpcomingSession() {
        viewModelScope.launch {
            // Cached slots first, then network — same instant-paint reasoning as the roster.
            if (nextUpcomingSession == null) {
                bookingRepo.cachedMySlots()?.let { nextUpcomingSession = pickUpcoming(it) }
            }
            val slots = (bookingRepo.mySlots() as? ApiResult.Success)?.data.orEmpty()
            nextUpcomingSession = pickUpcoming(slots)
        }
    }

    // The soonest slot at/after now that has at least one booking — the coach's "coming up".
    private fun pickUpcoming(slots: List<SlotResponse>): SlotResponse? {
        val now = java.time.LocalDateTime.now()
        val today = now.toLocalDate().toString()
        val nowHHmm = "%02d:%02d".format(now.hour, now.minute)
        return slots
            .filter { (it.capacity - it.remaining) > 0 }
            .filter { it.date > today || (it.date == today && it.startTime >= nowHHmm) }
            .minByOrNull { it.date + it.startTime }
    }

    // Computes this week's planned/completed pills straight from the cache (no network).
    private suspend fun seedSummariesFromCache(roster: List<ProfileResponse>) {
        val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusWeeks(1)
        val weekId = weekIdentifierFor(weekStart)
        val todayName = LocalDate.now().dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
        val cached = roster.mapNotNull { athlete ->
            val id = athlete.id ?: return@mapNotNull null
            val planned = repo.cachedAthletePlan(id, weekId) ?: return@mapNotNull null
            val completed = (repo.cachedAthleteSessions(id).orEmpty()).count { s ->
                val d = s.localDate
                d != null && !d.isBefore(weekStart) && d.isBefore(weekEnd)
            }
            id to AthleteWeeklySummary(planned.size, completed, planned.any { it.day == todayName })
        }.toMap()
        if (cached.isNotEmpty()) weeklySummaries = cached
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
        val todayName = LocalDate.now().dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
        viewModelScope.launch {
            val results = roster.mapNotNull { athlete ->
                val id = athlete.id ?: return@mapNotNull null
                async {
                    val planned = (repo.getAthletePlan(id, weekId) as? ApiResult.Success)?.data.orEmpty()
                    val completed = (repo.getAthleteSessions(id) as? ApiResult.Success)?.data.orEmpty().count { s ->
                        val d = s.localDate
                        d != null && !d.isBefore(weekStart) && d.isBefore(weekEnd)
                    }
                    id to AthleteWeeklySummary(planned.size, completed, planned.any { it.day == todayName })
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
