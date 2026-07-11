package com.vasilisneo.trackstar.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// Protected completed-session endpoint (/api/sessions). Requires the Bearer token, added by
// NetworkClient's auth interceptor.
interface SessionApi {
    @POST("sessions")
    suspend fun saveSession(@Body request: WorkoutSessionRequest): Response<WorkoutSessionResponse>

    @GET("sessions")
    suspend fun getSessions(): Response<List<WorkoutSessionResponse>>
}

// Matches com.fitnessbook.dto.WorkoutSessionRequest. `clientId` is the idempotency key
// (upsert by userId+clientId on the backend) — generate a fresh UUID per finished session.
data class WorkoutSessionRequest(
    val clientId: String,
    val date: String,          // "yyyy-MM-dd"
    val durationSeconds: Int,
    val sessionData: WorkoutSessionData,
)

// Matches com.fitnessbook.model.WorkoutSession — same shape returned by GET, `sessionData` is
// the opaque BSON blob matching iOS's Trackstar/Model/WorkoutSession.swift `WorkoutSession` struct.
data class WorkoutSessionResponse(
    val id: String?,
    val clientId: String?,
    val date: String?,
    val durationSeconds: Int?,
    val sessionData: WorkoutSessionData?,
)

// iOS `WorkoutSession` struct (the full logged-session document, not to be confused with the
// backend model of the same name that wraps it in `sessionData`). iOS uses a plain
// `JSONEncoder()`/`JSONDecoder()` with no custom date strategy, so `Date` fields serialize as
// `.deferredToDate` — a raw Double of seconds since the Unix epoch, not an ISO string.
data class WorkoutSessionData(
    val id: String,
    val date: Double,           // epoch seconds
    val completedAt: Double,    // epoch seconds
    val durationSeconds: Int,
    val exercises: List<ExerciseSummary>,
    val planSessionId: String?,
    val title: String,
)

data class ExerciseSummary(
    val id: String,
    val name: String,
    val sets: List<SetResult>,
    val compoundGroupId: String? = null,
)

data class SetResult(
    val id: String,
    val index: Int,
    val label: String,
    val actualPerformance: ActualPerformance?,
    val configuredRestSeconds: Int,
    val actualRestSeconds: Int? = null,
    val completedAtSeconds: Int? = null,
    val durationSeconds: Int? = null,
    val note: String? = null,
    val setType: String,
)

data class ActualPerformance(
    val frequencyValue: FrequencyValue,
    val resistanceValue: ResistanceValue,
)
