package com.vasilisneo.trackstar.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// Gym attendance / check-ins (/api/checkins). Mirrors iOS's Attendance models + CheckInViewModel.
// A visit is opened by scanning a gym QR (with a geofence check via lat/lng) or a coach's rotating
// session code, and closed on check-out (or auto-closed server-side after 4h).
interface CheckInApi {

    // Athlete
    @POST("checkins")
    suspend fun checkIn(@Body body: CheckInRequest): Response<VisitResponse>

    @POST("checkins/{id}/checkout")
    suspend fun checkOut(@Path("id") id: String): Response<VisitResponse>

    // All of the athlete's visits, newest first (the open one, if any, plus history).
    @GET("checkins")
    suspend fun getVisits(): Response<List<VisitResponse>>

    // Coach — a short-lived rotating session code athletes scan to check in.
    @POST("coach/session-code")
    suspend fun createSessionCode(): Response<SessionCodeResponse>

    // End the running session: closes everyone still checked in. Returns {"closed": n}.
    @POST("coach/session/end")
    suspend fun endSession(): Response<Map<String, Int>>

    // Coach — gyms (QR posters with a geofence).
    @GET("coach/gyms")
    suspend fun getGyms(): Response<List<GymResponse>>

    @POST("coach/gyms")
    suspend fun createGym(@Body body: CreateGymRequest): Response<GymResponse>

    @DELETE("coach/gyms/{id}")
    suspend fun deleteGym(@Path("id") id: String): Response<MessageResponse>

    // Coach — the team's visits (roster / attendance report).
    @GET("coach/attendance")
    suspend fun getCoachAttendance(): Response<List<VisitResponse>>
}

data class SessionCodeResponse(val token: String, val expiresAt: Double)

data class GymResponse(
    val id: String?,
    val ownerCoachId: String?,
    val name: String?,
    val lat: Double?,
    val lng: Double?,
)

data class CreateGymRequest(val name: String, val lat: Double, val lng: Double)

data class CheckInRequest(
    val method: String,          // "gym" | "coach_session"
    val gymId: String? = null,
    val sessionToken: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

data class VisitResponse(
    val id: String?,
    val athleteId: String?,
    val athleteName: String?,
    val method: String?,         // "gym" | "coach_session"
    val gymId: String?,
    val gymName: String?,
    val coachId: String?,
    val coachName: String?,
    val bookingId: String?,
    val checkInAt: Double?,       // epoch ms
    val checkOutAt: Double?,      // epoch ms, null while open
    val durationMin: Int?,
    val status: String?,          // open | closed | auto_closed
    val lat: Double?,
    val lng: Double?,
) {
    val isOpen: Boolean get() = status == "open"

    /** Where the visit happened — the gym name, or the coach's name for a coached session. */
    val locationLabel: String
        get() = when {
            !gymName.isNullOrEmpty() -> gymName
            !coachName.isNullOrEmpty() -> "Session with $coachName"
            method == "coach_session" -> "Coached session"
            else -> "Gym visit"
        }
}
