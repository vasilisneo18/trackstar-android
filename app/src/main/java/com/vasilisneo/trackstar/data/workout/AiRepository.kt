package com.vasilisneo.trackstar.data.workout

import com.vasilisneo.trackstar.data.api.AiUsageResponse
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.WorkoutPlanInput
import com.vasilisneo.trackstar.data.api.WorkoutPlanResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.apiCall

class AiRepository {
    private val api = NetworkClient.aiApi

    suspend fun generateWorkoutPlan(input: WorkoutPlanInput): ApiResult<WorkoutPlanResponse> =
        apiCall { api.generateWorkoutPlan(input) }

    suspend fun getUsage(): ApiResult<AiUsageResponse> =
        apiCall { api.getUsage() }
}
