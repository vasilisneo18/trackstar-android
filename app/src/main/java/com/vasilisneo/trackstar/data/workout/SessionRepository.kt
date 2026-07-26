package com.vasilisneo.trackstar.data.workout

import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.WorkoutSessionRequest
import com.vasilisneo.trackstar.data.api.WorkoutSessionResponse
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.auth.AuthTokenHolder
import com.vasilisneo.trackstar.data.auth.apiCall
import com.vasilisneo.trackstar.data.local.LocalStore
import com.vasilisneo.trackstar.data.local.Outbox
import com.vasilisneo.trackstar.data.local.WorkoutSessionEntity
import java.util.UUID

// Fetches completed sessions (GET /api/sessions) and saves a finished session
// (POST /api/sessions, upserted by clientId on the backend).
//
// getSessions() is cache-then-network: a successful fetch refreshes the Room cache, and a network
// failure falls back to the last cached set so history/stats keep working offline (mirrors iOS's
// SessionHistoryStore Realm fallback). saveSession() stays online-only in this phase — offline
// writes come with the pending-sync outbox (Phase 3).
class SessionRepository {
    private val api = NetworkClient.sessionApi

    suspend fun getSessions(): ApiResult<List<WorkoutSessionResponse>> {
        val userId = AuthTokenHolder.userId
        return when (val result = apiCall { api.getSessions() }) {
            is ApiResult.Success -> {
                if (userId != null) cacheSessions(userId, result.data)
                result
            }
            is ApiResult.Error -> {
                // Only serve stale sessions on a genuine connectivity failure, not a server error.
                val cached = if (userId != null && result.offline) loadCached(userId) else emptyList()
                if (cached.isNotEmpty()) ApiResult.Success(cached) else result
            }
        }
    }

    // Saves a finished session; if offline, queues it in the outbox (replayed on reconnect) and
    // optimistically adds it to the local cache + returns Success echoing the request, so history/
    // stats reflect the workout immediately and it's never lost.
    suspend fun saveSession(request: WorkoutSessionRequest): ApiResult<WorkoutSessionResponse> {
        val result = apiCall { api.saveSession(request) }
        if (result is ApiResult.Error && result.offline) {
            Outbox.enqueueSessionSave(request)
            AuthTokenHolder.userId?.let { cacheOne(it, request.toEcho()) }
            return ApiResult.Success(request.toEcho())
        }
        return result
    }

    private fun WorkoutSessionRequest.toEcho() = WorkoutSessionResponse(
        id = null,
        clientId = clientId,
        date = date,
        durationSeconds = durationSeconds,
        sessionData = sessionData,
    )

    private suspend fun cacheOne(userId: String, session: WorkoutSessionResponse) {
        if (!LocalStore.isReady) return
        runCatching {
            LocalStore.db.workoutSessionDao().insertAll(
                listOf(
                    WorkoutSessionEntity(
                        userId = userId,
                        cacheKey = session.clientId ?: session.id ?: UUID.randomUUID().toString(),
                        date = session.date,
                        json = LocalStore.gson.toJson(session),
                    ),
                ),
            )
        }
    }

    // --- cache helpers -------------------------------------------------------

    private suspend fun cacheSessions(userId: String, sessions: List<WorkoutSessionResponse>) {
        if (!LocalStore.isReady) return
        runCatching {
            val gson = LocalStore.gson
            val rows = sessions.map { s ->
                WorkoutSessionEntity(
                    userId = userId,
                    cacheKey = s.clientId ?: s.id ?: UUID.randomUUID().toString(),
                    date = s.date,
                    json = gson.toJson(s),
                )
            }
            LocalStore.db.workoutSessionDao().replaceUser(userId, rows)
        }
    }

    private suspend fun loadCached(userId: String): List<WorkoutSessionResponse> {
        if (!LocalStore.isReady) return emptyList()
        return runCatching {
            val gson = LocalStore.gson
            LocalStore.db.workoutSessionDao().loadJson(userId)
                .mapNotNull { runCatching { gson.fromJson(it, WorkoutSessionResponse::class.java) }.getOrNull() }
        }.getOrDefault(emptyList())
    }
}
