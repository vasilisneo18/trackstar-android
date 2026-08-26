package com.vasilisneo.trackstar.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
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

    // Athletes this coach spent a Bronze credit on.
    @GET("coach/bronze-grants")
    suspend fun getBronzeGrants(): Response<List<BronzeGrantResponse>>

    // Revoke a Bronze grant: athlete back to free, credit refunded to the coach.
    @DELETE("coach/bronze-grants/{athleteId}")
    suspend fun revokeBronzeGrant(@Path("athleteId") athleteId: String): Response<MessageResponse>

    // The whole roster's week totals in one call (vs. plan+sessions per athlete).
    @GET("coach/roster-summaries")
    suspend fun getAthleteSummaries(
        @Query("weekIdentifier") weekIdentifier: String,
        @Query("weekStart") weekStart: String,
    ): Response<List<AthleteSummaryResponse>>

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

    // Set whether accepting this invite (QR "My QR" / Share Link) spends a Bronze credit.
    @PUT("coach/invite/{token}")
    suspend fun updateInviteGrantBronze(@Path("token") token: String, @Body body: Map<String, Boolean>): Response<Map<String, Boolean>>

    // Athlete side
    @GET("coach/my-coach")
    suspend fun getMyCoach(): Response<ProfileResponse>

    // Athlete checks an invite token before accepting (does not consume it). Always returns 200
    // with valid/reason — the backend never errors here.
    @GET("coach/invite/validate/{token}")
    suspend fun validateInvite(@Path("token") token: String): Response<InviteValidationResponse>

    // Athlete accepts an invite and links to the coach. 200 on success; 400 with a message if the
    // token is invalid/expired/already-used or the athlete already has a coach.
    @POST("coach/invite/accept/{token}")
    suspend fun acceptInvite(@Path("token") token: String): Response<MessageResponse>
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

// iOS InviteValidationResponse. `reason` is one of not_found/used/expired/own_invite/already_linked
// when invalid, empty when valid. AcceptInviteSheet maps each reason to a friendly title/message.
data class InviteValidationResponse(val valid: Boolean = false, val reason: String = "")

// One athlete's week totals from GET /coach/athletes/summaries.
data class AthleteSummaryResponse(
    val athleteId: String? = null,
    val plannedCount: Int = 0,
    val completedCount: Int = 0,
    val hasSessionToday: Boolean = false,
)

// One athlete a coach granted Bronze to (GET /coach/bronze-grants).
data class BronzeGrantResponse(
    val athleteId: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val plan: String? = null,
    val grantExpiresAt: Long? = null,
)
