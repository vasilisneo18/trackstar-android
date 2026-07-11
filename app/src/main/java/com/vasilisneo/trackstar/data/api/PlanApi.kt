package com.vasilisneo.trackstar.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Protected planned-session endpoint (/api/plan). Requires the Bearer token, added by
// NetworkClient's auth interceptor.
interface PlanApi {
    @GET("plan")
    suspend fun getPlan(@Query("weekIdentifier") weekIdentifier: String): Response<List<PlannedSessionResponse>>

    @POST("plan")
    suspend fun upsertSession(@Body request: PlannedSessionRequest): Response<PlannedSessionResponse>

    @DELETE("plan/{sessionId}")
    suspend fun deleteSession(@Path("sessionId") sessionId: String): Response<MessageResponse>

    @POST("plan/batch")
    suspend fun upsertBatch(@Body requests: List<PlannedSessionRequest>): Response<List<PlannedSessionResponse>>
}

// Matches com.fitnessbook.dto.PlannedSessionRequest exactly (id, weekIdentifier, day,
// orderIndex, title, exercises) — the write-side counterpart of PlannedSessionResponse, reusing
// the same ExerciseData/ExerciseSet shape since both read and write the same document shape.
data class PlannedSessionRequest(
    val id: String,
    val weekIdentifier: String,
    val day: String,
    val orderIndex: Int,
    val title: String,
    val exercises: List<ExerciseData>,
)

// Matches com.fitnessbook.model.PlannedSession / dto.PlannedSessionRequest. `exercises` is an
// opaque BSON blob on the backend (never typed server-side) — the shape below matches what the
// iOS app writes (Trackstar/Model/Workout/ExerciseData.swift), since both apps read/write the
// same documents.
data class PlannedSessionResponse(
    val id: String?,
    val weekIdentifier: String?,
    val day: String?,
    val orderIndex: Int?,
    val title: String?,
    val exercises: List<ExerciseData>?,
)

data class ExerciseData(
    val id: String?,
    val name: String?,
    val sets: List<ExerciseSet>?,
    val frequencyType: String?,
    val resistanceType: String?,
    val resistanceUnit: ResistanceUnit?,
    val compoundGroupId: String?,
)

data class ExerciseSet(
    val id: String?,
    val frequencyValue: FrequencyValue?,
    val resistanceValue: ResistanceValue?,
    val restSeconds: Int?,
    val setType: String?,
    // Upper bound of a rep range (e.g. "8-10 reps"); nil means a single fixed target.
    val repsMax: Int?,
)

// Swift enums with associated values are serialized as a flat object with exactly one key
// present — {"reps": 10} or {"duration": "30 sec"}. Gson maps this naturally onto a data class
// with all fields nullable, no custom adapter needed.
data class FrequencyValue(
    val reps: Int? = null,
    val duration: String? = null,
    val distance: String? = null,
)

data class ResistanceValue(
    val weight: String? = null,
    val bandLevel: String? = null,
)

data class ResistanceUnit(
    val weight: String? = null,   // "KG" / "LB"
    val bandLevel: Boolean? = null,
)
