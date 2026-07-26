package com.vasilisneo.trackstar.data.workout

import com.vasilisneo.trackstar.data.api.DietSyncRequest
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.WeeklyDietPlanDto
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.apiCall
import com.vasilisneo.trackstar.data.local.cachedRead

// Weekly diet plan (GET/POST /api/diet). API-first like the plan/session repos — the ViewModel
// holds the plan in memory and calls save on every change. Pass an `athleteId` to operate on an
// athlete's diet via /api/coach/athletes/{id}/diet (coach editing their athlete's plan).
// getDiet() is cache-then-network so the Diet tab works offline.
class DietRepository {
    private val api = NetworkClient.dietApi
    private val coachApi = NetworkClient.athleteApi

    suspend fun getDiet(athleteId: String? = null): ApiResult<WeeklyDietPlanDto> {
        val key = athleteId?.let { "diet:athlete:$it" } ?: "diet"
        return cachedRead(key) {
            when (val r = apiCall { if (athleteId == null) api.getDiet() else coachApi.getAthleteDiet(athleteId) }) {
                is ApiResult.Success -> ApiResult.Success(r.data.planData)
                is ApiResult.Error -> r // preserve the offline flag so cache fallback works
            }
        }
    }

    suspend fun saveDiet(plan: WeeklyDietPlanDto, athleteId: String? = null): ApiResult<Unit> =
        when (val r = apiCall {
            if (athleteId == null) api.saveDiet(DietSyncRequest(plan)) else coachApi.saveAthleteDiet(athleteId, DietSyncRequest(plan))
        }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> ApiResult.Error(r.message)
        }
}
