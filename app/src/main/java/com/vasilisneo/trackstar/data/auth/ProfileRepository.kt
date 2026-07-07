package com.vasilisneo.trackstar.data.auth

import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.ProfileResponse

// Fetches the signed-in user's full profile (GET /api/profile) — the body stats the Profile
// screen shows beyond the name/email cached in TokenStore.
class ProfileRepository {
    private val api = NetworkClient.profileApi

    suspend fun getProfile(): ApiResult<ProfileResponse> = apiCall { api.getProfile() }
}
