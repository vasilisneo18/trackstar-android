package com.vasilisneo.trackstar.data.local

import com.vasilisneo.trackstar.data.api.PlannedSessionRequest
import com.vasilisneo.trackstar.data.api.WeeklyDietPlanDto
import com.vasilisneo.trackstar.data.api.WorkoutSessionRequest
import com.vasilisneo.trackstar.data.auth.AuthTokenHolder

// The offline-write outbox: repositories enqueue a write here when it fails because the device is
// offline, and PendingSyncService replays queued writes (FIFO) when connectivity returns. Payloads
// are stored as JSON keyed by a `type` string that the sync service dispatches on.
object Outbox {

    // Action type tags (stored in PendingActionEntity.type).
    const val SESSION_SAVE = "session_save"
    const val PLAN_UPSERT = "plan_upsert"
    const val PLAN_DELETE = "plan_delete"
    const val PLAN_BATCH = "plan_batch"
    const val DIET_SAVE = "diet_save"

    // Payload shapes (serialized to PendingActionEntity.payload). The athlete-side coach edits carry
    // an athleteId; null means the signed-in user's own plan/diet.
    data class PlanUpsertPayload(val request: PlannedSessionRequest, val athleteId: String?)
    data class PlanDeletePayload(val sessionId: String, val athleteId: String?)
    data class PlanBatchPayload(val requests: List<PlannedSessionRequest>, val athleteId: String?)
    data class DietSavePayload(val plan: WeeklyDietPlanDto, val athleteId: String?)

    // Enqueue helpers — each returns true if queued (a userId + ready DB), false otherwise.
    suspend fun enqueueSessionSave(request: WorkoutSessionRequest): Boolean =
        enqueue(SESSION_SAVE, request)

    suspend fun enqueuePlanUpsert(request: PlannedSessionRequest, athleteId: String?): Boolean =
        enqueue(PLAN_UPSERT, PlanUpsertPayload(request, athleteId))

    suspend fun enqueuePlanDelete(sessionId: String, athleteId: String?): Boolean =
        enqueue(PLAN_DELETE, PlanDeletePayload(sessionId, athleteId))

    suspend fun enqueuePlanBatch(requests: List<PlannedSessionRequest>, athleteId: String?): Boolean =
        enqueue(PLAN_BATCH, PlanBatchPayload(requests, athleteId))

    suspend fun enqueueDietSave(plan: WeeklyDietPlanDto, athleteId: String?): Boolean =
        enqueue(DIET_SAVE, DietSavePayload(plan, athleteId))

    private suspend fun enqueue(type: String, payload: Any): Boolean {
        val userId = AuthTokenHolder.userId ?: return false
        if (!LocalStore.isReady) return false
        return runCatching {
            LocalStore.db.pendingActionDao().insert(
                PendingActionEntity(userId = userId, type = type, payload = LocalStore.gson.toJson(payload)),
            )
        }.isSuccess
    }
}
