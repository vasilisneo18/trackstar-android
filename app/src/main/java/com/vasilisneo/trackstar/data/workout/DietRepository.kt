package com.vasilisneo.trackstar.data.workout

import com.vasilisneo.trackstar.data.api.DietSyncRequest
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.WeeklyDietPlanDto
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.AuthTokenHolder
import com.vasilisneo.trackstar.data.auth.apiCall
import com.vasilisneo.trackstar.data.local.CacheEntry
import com.vasilisneo.trackstar.data.local.LocalStore
import com.vasilisneo.trackstar.data.local.Outbox
import com.vasilisneo.trackstar.data.local.cachePeek
import com.vasilisneo.trackstar.data.local.cachedRead

// Weekly diet plan (GET/POST /api/diet). API-first like the plan/session repos — the ViewModel
// holds the plan in memory and calls save on every change. Pass an `athleteId` to operate on an
// athlete's diet via /api/coach/athletes/{id}/diet (coach editing their athlete's plan).
// getDiet() is cache-then-network so the Diet tab works offline.
// `open` so a test fake can override getDiet/saveDiet when constructor-injected into a view model.
open class DietRepository {
    private val api = NetworkClient.dietApi
    private val coachApi = NetworkClient.athleteApi

    open suspend fun getDiet(athleteId: String? = null): ApiResult<WeeklyDietPlanDto> {
        return cachedRead(dietKey(athleteId)) {
            when (val r = apiCall { if (athleteId == null) api.getDiet() else coachApi.getAthleteDiet(athleteId) }) {
                is ApiResult.Success -> ApiResult.Success(r.data.planData)
                is ApiResult.Error -> r // preserve the offline flag so cache fallback works
            }
        }
    }

    // Cache-only peek (no network) for painting the Diet tab instantly on open.
    open suspend fun cachedDiet(athleteId: String? = null): WeeklyDietPlanDto? = cachePeek(dietKey(athleteId))

    private fun dietKey(athleteId: String?) = athleteId?.let { "diet:athlete:$it" } ?: "diet"

    // Saves the diet plan; if offline, queues it (replayed on reconnect) and optimistically
    // overwrites the cached plan so the change shows on a cold reload, returning Success.
    open suspend fun saveDiet(plan: WeeklyDietPlanDto, athleteId: String? = null): ApiResult<Unit> =
        when (val r = apiCall {
            if (athleteId == null) api.saveDiet(DietSyncRequest(plan)) else coachApi.saveAthleteDiet(athleteId, DietSyncRequest(plan))
        }) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> {
                if (r.offline) {
                    Outbox.enqueueDietSave(plan, athleteId)
                    cacheDiet(plan, athleteId)
                    ApiResult.Success(Unit)
                } else {
                    ApiResult.Error(r.message)
                }
            }
        }

    private suspend fun cacheDiet(plan: WeeklyDietPlanDto, athleteId: String?) {
        val userId = AuthTokenHolder.userId ?: return
        if (!LocalStore.isReady) return
        val key = athleteId?.let { "diet:athlete:$it" } ?: "diet"
        runCatching {
            LocalStore.db.cacheDao().put(CacheEntry(userId, key, LocalStore.gson.toJson(plan)))
        }
    }
}
