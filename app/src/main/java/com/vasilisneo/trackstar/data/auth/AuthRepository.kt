package com.vasilisneo.trackstar.data.auth

import com.vasilisneo.trackstar.data.api.AuthResponse
import com.vasilisneo.trackstar.data.api.CheckEmailRequest
import com.vasilisneo.trackstar.data.api.ForgotPasswordRequest
import com.vasilisneo.trackstar.data.api.LoginRequest
import com.vasilisneo.trackstar.data.api.MessageResponse
import com.vasilisneo.trackstar.data.api.NetworkClient
import com.vasilisneo.trackstar.data.api.RegisterRequest
import com.vasilisneo.trackstar.data.api.SocialAuthRequest

// Thin repository over AuthApi: runs each call through the shared apiCall() helper and
// persists the auth response to TokenStore on success.
class AuthRepository(private val tokenStore: TokenStore) {

    private val api = NetworkClient.authApi

    suspend fun checkEmail(email: String): ApiResult<Boolean> =
        when (val r = apiCall { api.checkEmail(CheckEmailRequest(email.trim().lowercase())) }) {
            is ApiResult.Success -> ApiResult.Success(r.data.exists)
            is ApiResult.Error -> r
        }

    suspend fun login(email: String, password: String): ApiResult<AuthResponse> {
        val cleanEmail = email.trim().lowercase()
        val result = withDeviceLimitPrompt { replace -> apiCall { api.login(LoginRequest(cleanEmail, password, replaceOldest = replace)) } }
        if (result is ApiResult.Success) {
            tokenStore.save(result.data)
            com.vasilisneo.trackstar.data.billing.BillingManager.logIn(result.data.userId)
            com.vasilisneo.trackstar.data.push.PushTokenRegistrar.register()
        }
        return result
    }

    suspend fun register(request: RegisterRequest): ApiResult<AuthResponse> {
        val result = apiCall { api.register(request) }
        if (result is ApiResult.Success) {
            tokenStore.save(result.data)
            com.vasilisneo.trackstar.data.billing.BillingManager.logIn(result.data.userId)
            com.vasilisneo.trackstar.data.push.PushTokenRegistrar.register()
        }
        return result
    }

    // One-tap "Continue as": exchange the kept refresh token for a fresh session (no stored
    // password). Fails when the refresh token is missing/expired — caller then routes to login.
    suspend fun refreshSession(): ApiResult<AuthResponse> {
        val refreshToken = tokenStore.refreshToken ?: return ApiResult.Error("No saved session", offline = false)
        val result = apiCall { api.refresh(com.vasilisneo.trackstar.data.api.RefreshRequest(refreshToken)) }
        if (result is ApiResult.Success) {
            tokenStore.save(result.data)
            com.vasilisneo.trackstar.data.billing.BillingManager.logIn(result.data.userId)
            com.vasilisneo.trackstar.data.push.PushTokenRegistrar.register()
        }
        return result
    }

    // Google/Apple sign-in: exchange the provider's ID token for our JWT. No password to cache (so no
    // "Continue as" quick-login for social accounts — they re-tap Google), but the refresh token in
    // TokenStore keeps the session alive across launches just like email login.
    suspend fun socialLogin(
        provider: String,
        idToken: String,
        firstName: String?,
        lastName: String?,
        email: String?,
    ): ApiResult<AuthResponse> {
        val result = withDeviceLimitPrompt { replace ->
            apiCall { api.social(SocialAuthRequest(provider, idToken, firstName, lastName, email, replaceOldest = replace)) }
        }
        if (result is ApiResult.Success) {
            tokenStore.save(result.data)
            com.vasilisneo.trackstar.data.billing.BillingManager.logIn(result.data.userId)
            com.vasilisneo.trackstar.data.push.PushTokenRegistrar.register()
        }
        return result
    }

    /** All three device slots taken (409): ask first, then log in again replacing the oldest. */
    private suspend fun withDeviceLimitPrompt(call: suspend (replaceOldest: Boolean?) -> ApiResult<AuthResponse>): ApiResult<AuthResponse> {
        val first = call(null)
        if (first !is ApiResult.Error || first.code != 409) return first
        if (!DeviceLimitPrompt.confirm()) return ApiResult.Error(com.vasilisneo.trackstar.ui.i18n.tr("Not logged in."))
        return call(true)
    }

    suspend fun forgotPassword(email: String): ApiResult<MessageResponse> =
        apiCall { api.forgotPassword(ForgotPasswordRequest(email.trim().lowercase())) }
}
