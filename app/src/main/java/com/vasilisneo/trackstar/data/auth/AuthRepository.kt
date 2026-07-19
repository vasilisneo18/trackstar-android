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
        val result = apiCall { api.login(LoginRequest(cleanEmail, password)) }
        if (result is ApiResult.Success) {
            tokenStore.save(result.data)
            tokenStore.saveCredentials(cleanEmail, password)
            com.vasilisneo.trackstar.data.billing.BillingManager.logIn(result.data.userId)
        }
        return result
    }

    suspend fun register(request: RegisterRequest): ApiResult<AuthResponse> {
        val result = apiCall { api.register(request) }
        if (result is ApiResult.Success) {
            tokenStore.save(result.data)
            tokenStore.saveCredentials(request.email, request.password)
            com.vasilisneo.trackstar.data.billing.BillingManager.logIn(result.data.userId)
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
        val result = apiCall {
            api.social(SocialAuthRequest(provider, idToken, firstName, lastName, email))
        }
        if (result is ApiResult.Success) {
            tokenStore.save(result.data)
            com.vasilisneo.trackstar.data.billing.BillingManager.logIn(result.data.userId)
        }
        return result
    }

    suspend fun forgotPassword(email: String): ApiResult<MessageResponse> =
        apiCall { api.forgotPassword(ForgotPasswordRequest(email.trim().lowercase())) }
}
