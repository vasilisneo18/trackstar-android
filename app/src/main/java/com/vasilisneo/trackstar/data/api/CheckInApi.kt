package com.vasilisneo.trackstar.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// Gym attendance / check-ins (/api/checkins). Mirrors iOS's Attendance models + CheckInViewModel.
// A visit is opened by scanning a gym QR (with a geofence check via lat/lng) or a coach's rotating
// session code, and closed on check-out (or auto-closed server-side after 4h).
interface CheckInApi {

    @POST("checkins")
    suspend fun checkIn(@Body body: CheckInRequest): Response<VisitResponse>

    @POST("checkins/{id}/checkout")
    suspend fun checkOut(@Path("id") id: String): Response<VisitResponse>

    // All of the athlete's visits, newest first (the open one, if any, plus history).
    @GET("checkins")
    suspend fun getVisits(): Response<List<VisitResponse>>
}

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
