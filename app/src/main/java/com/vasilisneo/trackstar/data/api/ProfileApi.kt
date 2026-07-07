package com.vasilisneo.trackstar.data.api

import retrofit2.Response
import retrofit2.http.GET

// Protected profile endpoint (/api/profile). Requires the Bearer token, added by
// NetworkClient's auth interceptor.
interface ProfileApi {
    @GET("profile")
    suspend fun getProfile(): Response<ProfileResponse>
}

// Matches com.fitnessbook.dto.UserProfileResponse (subset used by the Profile screen).
data class ProfileResponse(
    val id: String?,
    val email: String?,
    val firstName: String?,
    val lastName: String?,
    val age: Int?,
    val role: String?,
    val gender: String?,
    val height: Double?,
    val weight: Double?,
    val targetWeight: Double?,
    val country: String?,
    val coachName: String?,
    val coachingSince: String?,
)
