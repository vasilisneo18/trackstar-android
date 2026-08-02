package com.vasilisneo.trackstar.data.workout

import com.vasilisneo.trackstar.data.api.AiUsageResponse
import com.vasilisneo.trackstar.data.api.DietPlanInput
import com.vasilisneo.trackstar.data.api.DietPlanResponse
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.WorkoutPlanInput
import com.vasilisneo.trackstar.data.api.WorkoutPlanResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.apiCall

// `open` so test fakes can override these when constructor-injected into the AI planner view models.
open class AiRepository {
    private val api = NetworkClient.aiApi

    open suspend fun generateWorkoutPlan(input: WorkoutPlanInput): ApiResult<WorkoutPlanResponse> =
        apiCall { api.generateWorkoutPlan(input) }

    open suspend fun generateDietPlan(input: DietPlanInput): ApiResult<DietPlanResponse> =
        apiCall { api.generateDietPlan(input) }

    open suspend fun getUsage(): ApiResult<AiUsageResponse> =
        apiCall { api.getUsage() }
}
