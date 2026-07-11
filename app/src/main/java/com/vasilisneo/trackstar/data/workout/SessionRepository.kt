package com.vasilisneo.trackstar.data.workout

import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.WorkoutSessionRequest
import com.vasilisneo.trackstar.data.api.WorkoutSessionResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.apiCall

// Fetches completed sessions (GET /api/sessions) and saves a finished session
// (POST /api/sessions, upserted by clientId on the backend).
class SessionRepository {
    private val api = NetworkClient.sessionApi

    suspend fun getSessions(): ApiResult<List<WorkoutSessionResponse>> =
        apiCall { api.getSessions() }

    suspend fun saveSession(request: WorkoutSessionRequest): ApiResult<WorkoutSessionResponse> =
        apiCall { api.saveSession(request) }
}
