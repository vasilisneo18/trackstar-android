package com.vasilisneo.trackstar.data.workout

import com.vasilisneo.trackstar.data.api.AddAthleteRequest
import com.vasilisneo.trackstar.data.api.AthleteNotesDto
import com.vasilisneo.trackstar.data.api.CoachInviteResponse
import com.vasilisneo.trackstar.data.api.MessageResponse
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.PlannedSessionResponse
import com.vasilisneo.trackstar.data.api.ProfileResponse
import com.vasilisneo.trackstar.data.api.WorkoutSessionResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.apiCall

// Coach-side reads over /api/coach/... — the roster plus each athlete's plan and sessions. API-first
// like the other repos. Write/invite/diet operations get added as the later coach phases land.
class AthleteRepository {
    private val api = NetworkClient.athleteApi

    suspend fun getAthletes(): ApiResult<List<ProfileResponse>> = apiCall { api.getAthletes() }

    suspend fun getAthlete(athleteId: String): ApiResult<ProfileResponse> = apiCall { api.getAthlete(athleteId) }

    suspend fun getAthletePlan(athleteId: String, weekIdentifier: String): ApiResult<List<PlannedSessionResponse>> =
        apiCall { api.getAthletePlan(athleteId, weekIdentifier) }

    suspend fun getAthleteSessions(athleteId: String): ApiResult<List<WorkoutSessionResponse>> =
        apiCall { api.getAthleteSessions(athleteId) }

    suspend fun getAthleteNotes(athleteId: String): ApiResult<AthleteNotesDto> =
        apiCall { api.getAthleteNotes(athleteId) }

    suspend fun saveAthleteNotes(athleteId: String, notes: AthleteNotesDto): ApiResult<AthleteNotesDto> =
        apiCall { api.saveAthleteNotes(athleteId, notes) }

    suspend fun removeAthlete(athleteId: String): ApiResult<MessageResponse> =
        apiCall { api.removeAthlete(athleteId) }

    suspend fun addAthlete(email: String): ApiResult<ProfileResponse> =
        apiCall { api.addAthlete(AddAthleteRequest(email = email)) }

    suspend fun createInvite(): ApiResult<CoachInviteResponse> =
        apiCall { api.createInvite() }
}
