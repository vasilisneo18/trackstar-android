package com.vasilisneo.trackstar.data.workout

import com.vasilisneo.trackstar.data.api.AddAthleteRequest
import com.vasilisneo.trackstar.data.api.AthleteNotesDto
import com.vasilisneo.trackstar.data.api.CoachInviteResponse
import com.vasilisneo.trackstar.data.api.InviteValidationResponse
import com.vasilisneo.trackstar.data.api.MessageResponse
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.PlannedSessionResponse
import com.vasilisneo.trackstar.data.api.ProfileResponse
import com.vasilisneo.trackstar.data.api.WorkoutSessionResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.apiCall
import com.vasilisneo.trackstar.data.local.cachedRead

// Coach-side reads over /api/coach/... — the roster plus each athlete's plan and sessions. Reads are
// cache-then-network so the roster and athlete detail render offline. Write/invite operations stay
// online-only until the Phase 3 outbox.
class AthleteRepository {
    private val api = NetworkClient.athleteApi

    suspend fun getAthletes(): ApiResult<List<ProfileResponse>> =
        cachedRead("roster") { apiCall { api.getAthletes() } }

    suspend fun getAthlete(athleteId: String): ApiResult<ProfileResponse> =
        cachedRead("athlete:$athleteId") { apiCall { api.getAthlete(athleteId) } }

    suspend fun getAthletePlan(athleteId: String, weekIdentifier: String): ApiResult<List<PlannedSessionResponse>> =
        cachedRead("athletePlan:$athleteId:$weekIdentifier") { apiCall { api.getAthletePlan(athleteId, weekIdentifier) } }

    suspend fun getAthleteSessions(athleteId: String): ApiResult<List<WorkoutSessionResponse>> =
        cachedRead("athleteSessions:$athleteId") { apiCall { api.getAthleteSessions(athleteId) } }

    suspend fun getAthleteNotes(athleteId: String): ApiResult<AthleteNotesDto> =
        cachedRead("athleteNotes:$athleteId") { apiCall { api.getAthleteNotes(athleteId) } }

    suspend fun saveAthleteNotes(athleteId: String, notes: AthleteNotesDto): ApiResult<AthleteNotesDto> =
        apiCall { api.saveAthleteNotes(athleteId, notes) }

    suspend fun removeAthlete(athleteId: String): ApiResult<MessageResponse> =
        apiCall { api.removeAthlete(athleteId) }

    suspend fun addAthlete(email: String): ApiResult<ProfileResponse> =
        apiCall { api.addAthlete(AddAthleteRequest(email = email)) }

    suspend fun createInvite(): ApiResult<CoachInviteResponse> =
        apiCall { api.createInvite() }

    // Athlete side: the linked coach's profile. Errors (400 "No coach linked") when unlinked —
    // callers treat that as the empty state rather than a failure. Cache-then-network, but the 400
    // is not an offline error so it won't be masked by a stale cached coach.
    suspend fun getMyCoach(): ApiResult<ProfileResponse> =
        cachedRead("myCoach") { apiCall { api.getMyCoach() } }

    suspend fun validateInvite(token: String): ApiResult<InviteValidationResponse> =
        apiCall { api.validateInvite(token) }

    suspend fun acceptInvite(token: String): ApiResult<MessageResponse> =
        apiCall { api.acceptInvite(token) }
}
