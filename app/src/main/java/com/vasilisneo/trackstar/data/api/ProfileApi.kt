package com.vasilisneo.trackstar.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

// Protected profile endpoint (/api/profile). Requires the Bearer token, added by
// NetworkClient's auth interceptor.
interface ProfileApi {
    @GET("profile")
    suspend fun getProfile(): Response<ProfileResponse>

    // Partial update — only non-null fields are applied server-side (UserService.updateProfile).
    @PUT("profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<ProfileResponse>
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
    val notifyOnOpenSlot: Boolean? = null,
    val bookingEnabled: Boolean? = null,        // this user's own coach setting
    val coachBookingEnabled: Boolean? = null,   // whether this user's linked coach offers booking
)

// Partial profile update — mirrors com.fitnessbook.dto.UpdateProfileRequest. Only non-null fields are
// serialized (Gson omits nulls) and applied server-side, so this doubles as a per-field patch.
data class UpdateProfileRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    val height: Double? = null,
    val weight: Double? = null,
    val targetWeight: Double? = null,
    val country: String? = null,
    val role: String? = null,          // "athlete" | "coach"
    val notifyOnOpenSlot: Boolean? = null,
    val bookingEnabled: Boolean? = null,
)
