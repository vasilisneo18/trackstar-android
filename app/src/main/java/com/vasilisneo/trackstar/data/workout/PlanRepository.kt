package com.vasilisneo.trackstar.data.workout

import com.vasilisneo.trackstar.data.api.MessageResponse
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.PlannedSessionRequest
import com.vasilisneo.trackstar.data.api.PlannedSessionResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.apiCall

// Fetches, upserts, and deletes planned sessions (/api/plan). Pass an `athleteId` to operate on an
// athlete's plan via /api/coach/athletes/{id}/plan (coach editing their athlete's week).
class PlanRepository {
    private val api = NetworkClient.planApi
    private val coachApi = NetworkClient.athleteApi

    suspend fun getPlan(weekIdentifier: String, athleteId: String? = null): ApiResult<List<PlannedSessionResponse>> =
        apiCall { if (athleteId == null) api.getPlan(weekIdentifier) else coachApi.getAthletePlan(athleteId, weekIdentifier) }

    suspend fun upsertSession(request: PlannedSessionRequest, athleteId: String? = null): ApiResult<PlannedSessionResponse> =
        apiCall { if (athleteId == null) api.upsertSession(request) else coachApi.upsertAthleteSession(athleteId, request) }

    suspend fun deleteSession(sessionId: String, athleteId: String? = null): ApiResult<MessageResponse> =
        apiCall { if (athleteId == null) api.deleteSession(sessionId) else coachApi.deleteAthleteSession(athleteId, sessionId) }

    suspend fun upsertBatch(requests: List<PlannedSessionRequest>, athleteId: String? = null): ApiResult<List<PlannedSessionResponse>> =
        apiCall { if (athleteId == null) api.upsertBatch(requests) else coachApi.upsertAthleteBatch(athleteId, requests) }
}
