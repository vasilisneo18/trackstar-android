package com.vasilisneo.trackstar.data.workout

import com.vasilisneo.trackstar.data.api.MessageResponse
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.PlannedSessionRequest
import com.vasilisneo.trackstar.data.api.PlannedSessionResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.apiCall

// Fetches, upserts, and deletes planned sessions (/api/plan).
class PlanRepository {
    private val api = NetworkClient.planApi

    suspend fun getPlan(weekIdentifier: String): ApiResult<List<PlannedSessionResponse>> =
        apiCall { api.getPlan(weekIdentifier) }

    suspend fun upsertSession(request: PlannedSessionRequest): ApiResult<PlannedSessionResponse> =
        apiCall { api.upsertSession(request) }

    suspend fun deleteSession(sessionId: String): ApiResult<MessageResponse> =
        apiCall { api.deleteSession(sessionId) }

    suspend fun upsertBatch(requests: List<PlannedSessionRequest>): ApiResult<List<PlannedSessionResponse>> =
        apiCall { api.upsertBatch(requests) }
}
