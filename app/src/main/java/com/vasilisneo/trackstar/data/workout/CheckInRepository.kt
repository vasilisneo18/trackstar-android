package com.vasilisneo.trackstar.data.workout

import com.vasilisneo.trackstar.data.api.CheckInRequest
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.VisitResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.apiCall

// Gym attendance / check-ins. API-first, like the other repos. `open` so a view-model test can
// substitute a fake.
open class CheckInRepository {
    private val api = NetworkClient.checkInApi

    open suspend fun checkIn(request: CheckInRequest): ApiResult<VisitResponse> =
        apiCall { api.checkIn(request) }

    open suspend fun checkOut(visitId: String): ApiResult<VisitResponse> =
        apiCall { api.checkOut(visitId) }

    open suspend fun getVisits(): ApiResult<List<VisitResponse>> =
        apiCall { api.getVisits() }
}
