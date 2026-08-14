package com.vasilisneo.trackstar.data.workout

import com.google.gson.reflect.TypeToken
import com.vasilisneo.trackstar.data.api.MessageResponse
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.PlannedSessionRequest
import com.vasilisneo.trackstar.data.api.PlannedSessionResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.AuthTokenHolder
import com.vasilisneo.trackstar.data.auth.apiCall
import com.vasilisneo.trackstar.data.local.CacheEntry
import com.vasilisneo.trackstar.data.local.LocalStore
import com.vasilisneo.trackstar.data.local.Outbox
import com.vasilisneo.trackstar.data.local.cachePeek
import com.vasilisneo.trackstar.data.local.cachedRead

// Fetches, upserts, and deletes planned sessions (/api/plan). Pass an `athleteId` to operate on an
// athlete's plan via /api/coach/athletes/{id}/plan (coach editing their athlete's week).
// getPlan() is cache-then-network (per week, per athlete) so the weekly plan shows offline; writes
// queue in the outbox when offline (replayed on reconnect) and optimistically update the cached
// week so the edit shows immediately.
// `open` so a test fake can override getPlan when constructor-injected into a view model.
open class PlanRepository {
    private val api = NetworkClient.planApi
    private val coachApi = NetworkClient.athleteApi

    open suspend fun getPlan(weekIdentifier: String, athleteId: String? = null): ApiResult<List<PlannedSessionResponse>> {
        return cachedRead(planKey(weekIdentifier, athleteId)) {
            apiCall { if (athleteId == null) api.getPlan(weekIdentifier) else coachApi.getAthletePlan(athleteId, weekIdentifier) }
        }
    }

    // Cache-only peek (no network) for painting the week instantly on open.
    open suspend fun cachedPlan(weekIdentifier: String, athleteId: String? = null): List<PlannedSessionResponse>? =
        cachePeek(planKey(weekIdentifier, athleteId))

    suspend fun upsertSession(request: PlannedSessionRequest, athleteId: String? = null): ApiResult<PlannedSessionResponse> {
        val result = apiCall { if (athleteId == null) api.upsertSession(request) else coachApi.upsertAthleteSession(athleteId, request) }
        if (result is ApiResult.Error && result.offline) {
            Outbox.enqueuePlanUpsert(request, athleteId)
            cacheUpsert(request, athleteId)
            return ApiResult.Success(request.toResponse())
        }
        return result
    }

    suspend fun deleteSession(sessionId: String, athleteId: String? = null): ApiResult<MessageResponse> {
        val result = apiCall { if (athleteId == null) api.deleteSession(sessionId) else coachApi.deleteAthleteSession(athleteId, sessionId) }
        if (result is ApiResult.Error && result.offline) {
            Outbox.enqueuePlanDelete(sessionId, athleteId)
            // The delete API doesn't carry the week, so the optimistic cache removal is handled by the
            // in-memory view state here; a cold offline reload may still show the row until it syncs.
            return ApiResult.Success(MessageResponse("Queued"))
        }
        return result
    }

    open suspend fun upsertBatch(requests: List<PlannedSessionRequest>, athleteId: String? = null): ApiResult<List<PlannedSessionResponse>> {
        val result = apiCall { if (athleteId == null) api.upsertBatch(requests) else coachApi.upsertAthleteBatch(athleteId, requests) }
        if (result is ApiResult.Error && result.offline) {
            Outbox.enqueuePlanBatch(requests, athleteId)
            requests.forEach { cacheUpsert(it, athleteId) }
            return ApiResult.Success(requests.map { it.toResponse() })
        }
        return result
    }

    // --- helpers -------------------------------------------------------------

    private fun planKey(weekIdentifier: String, athleteId: String?) =
        if (athleteId == null) "plan:$weekIdentifier" else "athletePlan:$athleteId:$weekIdentifier"

    private fun PlannedSessionRequest.toResponse() =
        PlannedSessionResponse(id, weekIdentifier, day, orderIndex, title, exercises)

    // Merge a single upserted session into the cached week (replace by id, else append) so an offline
    // edit is visible on a cold reload before it syncs.
    private suspend fun cacheUpsert(request: PlannedSessionRequest, athleteId: String?) {
        val userId = AuthTokenHolder.userId ?: return
        if (!LocalStore.isReady) return
        runCatching {
            val dao = LocalStore.db.cacheDao()
            val key = planKey(request.weekIdentifier, athleteId)
            val existing: List<PlannedSessionResponse> = dao.get(userId, key)?.let { json ->
                LocalStore.gson.fromJson(json, object : TypeToken<List<PlannedSessionResponse>>() {}.type)
            } ?: emptyList()
            val updated = existing.filter { it.id != request.id } + request.toResponse()
            dao.put(CacheEntry(userId, key, LocalStore.gson.toJson(updated)))
        }
    }
}
