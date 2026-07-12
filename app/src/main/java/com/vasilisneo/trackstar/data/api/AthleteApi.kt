package com.vasilisneo.trackstar.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Coach-side endpoints (all under /api/coach/...), matching iOS's AthleteService / APIEndpoint.
// A coach manages a roster of athletes and reads/writes each athlete's plan, sessions and diet.
// Reuses the same document DTOs as the athlete's own endpoints (ProfileResponse, PlannedSession*,
// WorkoutSessionResponse, WeeklyDiet*) since the backend stores identical shapes per user.
interface AthleteApi {

    // Roster
    @GET("coach/athletes")
    suspend fun getAthletes(): Response<List<ProfileResponse>>

    @GET("coach/athletes/{id}")
    suspend fun getAthlete(@Path("id") athleteId: String): Response<ProfileResponse>

    @POST("coach/athletes")
    suspend fun addAthlete(@Body request: AddAthleteRequest): Response<ProfileResponse>

    @DELETE("coach/athletes/{id}")
    suspend fun removeAthlete(@Path("id") athleteId: String): Response<MessageResponse>

    // Athlete's weekly plan (coach can view + edit)
    @GET("coach/athletes/{id}/plan")
    suspend fun getAthletePlan(@Path("id") athleteId: String, @Query("weekIdentifier") weekIdentifier: String): Response<List<PlannedSessionResponse>>

    @POST("coach/athletes/{id}/plan")
    suspend fun upsertAthleteSession(@Path("id") athleteId: String, @Body request: PlannedSessionRequest): Response<PlannedSessionResponse>

    @POST("coach/athletes/{id}/plan/batch")
    suspend fun upsertAthleteBatch(@Path("id") athleteId: String, @Body requests: List<PlannedSessionRequest>): Response<List<PlannedSessionResponse>>

    @DELETE("coach/athletes/{id}/plan/{sessionId}")
    suspend fun deleteAthleteSession(@Path("id") athleteId: String, @Path("sessionId") sessionId: String): Response<MessageResponse>

    // Athlete's completed sessions + diet
    @GET("coach/athletes/{id}/sessions")
    suspend fun getAthleteSessions(@Path("id") athleteId: String): Response<List<WorkoutSessionResponse>>

    @GET("coach/athletes/{id}/diet")
    suspend fun getAthleteDiet(@Path("id") athleteId: String): Response<DietSyncResponse>

    @POST("coach/athletes/{id}/diet")
    suspend fun saveAthleteDiet(@Path("id") athleteId: String, @Body request: DietSyncRequest): Response<DietSyncResponse>

    // Coach notes about an athlete (training profile + free-text notes)
    @GET("coach/athletes/{id}/notes")
    suspend fun getAthleteNotes(@Path("id") athleteId: String): Response<AthleteNotesDto>

    @POST("coach/athletes/{id}/notes")
    suspend fun saveAthleteNotes(@Path("id") athleteId: String, @Body notes: AthleteNotesDto): Response<AthleteNotesDto>

    // Invites
    @POST("coach/invite")
    suspend fun createInvite(): Response<CoachInviteResponse>

    // Athlete side
    @GET("coach/my-coach")
    suspend fun getMyCoach(): Response<ProfileResponse>
}

data class AddAthleteRequest(val email: String, val useBronzeGrant: Boolean = false)

// Mirrors iOS AthleteNotes: the coach's training profile + free-text notes for an athlete.
// `startDate` is a "yyyy-MM-dd" string; `fitnessLevel` one of Beginner/Intermediate/Advanced/Elite.
data class AthleteNotesDto(
    val athleteId: String,
    val startDate: String = "",
    val fitnessLevel: String = "Beginner",
    val trainingDaysPerWeek: Int = 3,
    val goals: String = "",
    val injuries: String = "",
    val notes: String = "",
)

// iOS CoachInviteResponse — a deep link the athlete opens (or a QR encodes) to accept the invite.
data class CoachInviteResponse(val deepLink: String? = null, val token: String? = null)
