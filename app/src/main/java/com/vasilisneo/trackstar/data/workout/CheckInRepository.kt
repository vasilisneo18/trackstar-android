package com.vasilisneo.trackstar.data.workout

import com.vasilisneo.trackstar.data.api.CheckInRequest
import com.vasilisneo.trackstar.data.api.CreateGymRequest
import com.vasilisneo.trackstar.data.api.GymResponse
import com.vasilisneo.trackstar.data.api.MessageResponse
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.SessionCodeResponse
import com.vasilisneo.trackstar.data.api.VisitResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.apiCall

// Gym attendance / check-ins. API-first, like the other repos. `open` so a view-model test can
// substitute a fake.
open class CheckInRepository {
    private val api = NetworkClient.checkInApi

    // Athlete
    open suspend fun checkIn(request: CheckInRequest): ApiResult<VisitResponse> =
        apiCall { api.checkIn(request) }

    open suspend fun checkOut(visitId: String): ApiResult<VisitResponse> =
        apiCall { api.checkOut(visitId) }

    open suspend fun getVisits(): ApiResult<List<VisitResponse>> =
        apiCall { api.getVisits() }

    // Coach
    open suspend fun createSessionCode(): ApiResult<SessionCodeResponse> =
        apiCall { api.createSessionCode() }

    open suspend fun endSession(): ApiResult<Map<String, Int>> =
        apiCall { api.endSession() }

    open suspend fun getGyms(): ApiResult<List<GymResponse>> =
        apiCall { api.getGyms() }

    open suspend fun createGym(request: CreateGymRequest): ApiResult<GymResponse> =
        apiCall { api.createGym(request) }

    open suspend fun deleteGym(id: String): ApiResult<MessageResponse> =
        apiCall { api.deleteGym(id) }

    open suspend fun getCoachAttendance(): ApiResult<List<VisitResponse>> =
        apiCall { api.getCoachAttendance() }
}
