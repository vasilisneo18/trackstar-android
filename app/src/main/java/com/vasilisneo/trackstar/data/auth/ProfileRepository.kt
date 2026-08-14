package com.vasilisneo.trackstar.data.auth

import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.ProfileResponse
import com.vasilisneo.trackstar.data.api.UpdateProfileRequest
import com.vasilisneo.trackstar.data.local.cachePeek
import com.vasilisneo.trackstar.data.local.cachedRead

// Fetches the signed-in user's full profile (GET /api/profile) — the body stats the Profile
// screen shows beyond the name/email cached in TokenStore. Cache-then-network so the Profile
// screen renders from cache when offline.
// `open` so a test fake can override getProfile when constructor-injected into a view model.
open class ProfileRepository {
    private val api = NetworkClient.profileApi

    open suspend fun getProfile(): ApiResult<ProfileResponse> =
        cachedRead("profile") { apiCall { api.getProfile() } }

    // Cache-only peek (no network) for painting the Profile screen instantly on open.
    open suspend fun cachedProfile(): ProfileResponse? = cachePeek("profile")

    open suspend fun updateProfile(request: UpdateProfileRequest): ApiResult<ProfileResponse> =
        apiCall { api.updateProfile(request) }
}
