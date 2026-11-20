package com.vasilisneo.trackstar.data.local

import com.vasilisneo.trackstar.data.api.DietSyncRequest
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.PlannedSessionRequest
import com.vasilisneo.trackstar.data.api.WorkoutSessionRequest
import com.vasilisneo.trackstar.data.auth.AuthTokenHolder
import com.vasilisneo.trackstar.data.network.ConnectivityMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

// Replays the offline-write outbox (Outbox / pending_actions) when connectivity returns — the
// Android analogue of iOS's PendingSyncService. Started once from Application.onCreate; it collects
// ConnectivityMonitor and drains the current user's queued writes FIFO whenever the device is
// online (including at launch, to flush anything left from a previous session).
object PendingSyncService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    @Volatile private var started = false

    private enum class Outcome { SUCCESS, RETRY, DROP }

    /** Sends what's waiting now — after signing in again (a write can wait on a dead login, not
     *  only on being offline). */
    fun flush() {
        scope.launch { drain() }
    }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            // StateFlow replays its current value on collect, so this also drains once at launch if
            // we're already online.
            ConnectivityMonitor.isOnline.collect { online -> if (online) drain() }
        }
    }

    private suspend fun drain() {
        val userId = AuthTokenHolder.userId ?: return
        if (!LocalStore.isReady || !ConnectivityMonitor.isOnline.value) return
        mutex.withLock {
            val dao = LocalStore.db.pendingActionDao()
            val actions = runCatching { dao.forUser(userId) }.getOrDefault(emptyList())
            for (action in actions) {
                when (execute(action)) {
                    Outcome.SUCCESS, Outcome.DROP -> runCatching { dao.delete(action) }
                    // Went offline again mid-drain — keep this and the rest, retry on next reconnect.
                    Outcome.RETRY -> return@withLock
                }
            }
        }
    }

    // Runs one queued write against the live API. SUCCESS -> remove; RETRY -> still offline, keep;
    // DROP -> server rejected it (replaying won't help), remove so it can't block the queue forever.
    private suspend fun execute(action: PendingActionEntity): Outcome {
        val gson = LocalStore.gson
        return try {
            val response: retrofit2.Response<*> = when (action.type) {
                Outbox.SESSION_SAVE -> {
                    val req = gson.fromJson(action.payload, WorkoutSessionRequest::class.java)
                    NetworkClient.sessionApi.saveSession(req)
                }
                Outbox.PLAN_UPSERT -> {
                    val p = gson.fromJson(action.payload, Outbox.PlanUpsertPayload::class.java)
                    if (p.athleteId == null) NetworkClient.planApi.upsertSession(p.request)
                    else NetworkClient.athleteApi.upsertAthleteSession(p.athleteId, p.request)
                }
                Outbox.PLAN_DELETE -> {
                    val p = gson.fromJson(action.payload, Outbox.PlanDeletePayload::class.java)
                    if (p.athleteId == null) NetworkClient.planApi.deleteSession(p.sessionId)
                    else NetworkClient.athleteApi.deleteAthleteSession(p.athleteId, p.sessionId)
                }
                Outbox.PLAN_BATCH -> {
                    val p = gson.fromJson(action.payload, Outbox.PlanBatchPayload::class.java)
                    if (p.athleteId == null) NetworkClient.planApi.upsertBatch(p.requests)
                    else NetworkClient.athleteApi.upsertAthleteBatch(p.athleteId, p.requests)
                }
                Outbox.DIET_SAVE -> {
                    val p = gson.fromJson(action.payload, Outbox.DietSavePayload::class.java)
                    if (p.athleteId == null) NetworkClient.dietApi.saveDiet(DietSyncRequest(p.plan))
                    else NetworkClient.athleteApi.saveAthleteDiet(p.athleteId, DietSyncRequest(p.plan))
                }
                else -> return Outcome.DROP // unknown type
            }
            when {
                response.isSuccessful -> Outcome.SUCCESS
                // Signed out: keep it for after the next sign-in.
                AuthTokenHolder.isSignedOutStatus(response.code()) -> Outcome.RETRY
                else -> Outcome.DROP
            }
        } catch (e: IOException) {
            Outcome.RETRY
        } catch (e: Exception) {
            Outcome.DROP
        }
    }
}
